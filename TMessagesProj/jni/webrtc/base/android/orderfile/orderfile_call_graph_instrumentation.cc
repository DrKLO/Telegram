// Copyright (c) 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/orderfile/orderfile_instrumentation.h"

#include <time.h>
#include <unistd.h>

#include <atomic>
#include <cstdio>
#include <cstring>
#include <string>
#include <thread>
#include <vector>

#include "base/android/library_loader/anchor_functions.h"
#include "base/android/orderfile/orderfile_buildflags.h"
#include "base/files/file.h"
#include "base/format_macros.h"
#include "base/json/json_writer.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/strings/stringprintf.h"
#include "base/values.h"
#include "build/build_config.h"

#if BUILDFLAG(DEVTOOLS_INSTRUMENTATION_DUMPING)
#include <sstream>

#include "base/command_line.h"
#include "base/time/time.h"
#include "base/trace_event/memory_dump_manager.h"
#include "base/trace_event/memory_dump_provider.h"
#endif  // BUILDFLAG(DEVTOOLS_INSTRUMENTATION_DUMPING)

#if !BUILDFLAG(SUPPORTS_CODE_ORDERING)
#error Only supported on architectures supporting code ordering (arm/arm64).
#endif  // !BUILDFLAG(SUPPORTS_CODE_ORDERING)

// Must be applied to all functions within this file.
#define NO_INSTRUMENT_FUNCTION __attribute__((no_instrument_function))
#define INLINE_AND_NO_INSTRUMENT_FUNCTION \
  __attribute__((always_inline, no_instrument_function))

namespace base {
namespace android {
namespace orderfile {

namespace {

#if BUILDFLAG(DEVTOOLS_INSTRUMENTATION_DUMPING)
// This is defined in content/public/common/content_switches.h, which is not
// accessible in ::base.
constexpr const char kProcessTypeSwitch[] = "type";
#else
// Constant used for StartDelayedDump().
constexpr int kDelayInSeconds = 30;
#endif  // BUILDFLAG(DEVTOOLS_INSTRUMENTATION_DUMPING)

constexpr size_t kMaxTextSizeInBytes = 1 << 27;
constexpr size_t kMaxElements = kMaxTextSizeInBytes / 4;
// Native code currently have ~850k symbols, hence recording up to 1M symbols
// can cover all possible callee symbols.
constexpr size_t kMaxReachedSymbols = 1 << 20;
// 3 callers are recorded per callee.
constexpr size_t kCallerBuckets = 3;
// The last bucket is to count for misses and callers from outside the
// native code bounds.
constexpr size_t kMissesBucketIndex = 3;
constexpr size_t kTotalBuckets = 4;

std::atomic<uint32_t> callee_map[kMaxElements];
static_assert(sizeof(callee_map) == 128 * (1 << 20), "");
// Contain caller offsets. 4 buckets of callers per callee where the
// last bucket is for misses.
std::atomic<uint32_t> g_caller_offset[kMaxReachedSymbols * kTotalBuckets];
static_assert(sizeof(g_caller_offset) == 16 * (1 << 20), "");
// Corresponding count of |g_caller_offset|.
std::atomic<uint32_t> g_caller_count[kMaxReachedSymbols * kTotalBuckets];
static_assert(sizeof(g_caller_count) == 16 * (1 << 20), "");
// Index for |g_caller_offset| and |g_caller_count|.
std::atomic<uint32_t> g_callers_index;
std::atomic<uint32_t> g_calls_count;
std::atomic<bool> g_disabled;

#if BUILDFLAG(DEVTOOLS_INSTRUMENTATION_DUMPING)
// Dump offsets when a memory dump is requested. Used only if
// switches::kDevtoolsInstrumentationDumping is set.
class OrderfileMemoryDumpHook : public base::trace_event::MemoryDumpProvider {
  NO_INSTRUMENT_FUNCTION bool OnMemoryDump(
      const base::trace_event::MemoryDumpArgs& args,
      base::trace_event::ProcessMemoryDump* pmd) override {
    if (!Disable())
      return true;  // A dump has already been started.

    std::string process_type =
        base::CommandLine::ForCurrentProcess()->GetSwitchValueASCII(
            kProcessTypeSwitch);
    if (process_type.empty())
      process_type = "browser";

    Dump(process_type);
    return true;  // If something goes awry, a fatal error will be created
                  // internally.
  }
};
#endif  // BUILDFLAG(DEVTOOLS_INSTRUMENTATION_DUMPING)

// This is not racy. It is guaranteed that any number of threads concurrently
// calling this function in any order, will always end up with the same count
// at the end. It returns |element|'s value before the increment.
INLINE_AND_NO_INSTRUMENT_FUNCTION uint32_t
AtomicIncrement(std::atomic<uint32_t>* element) {
  return element->fetch_add(1, std::memory_order_relaxed);
}

// Increment the miss bucket for a callee. |index| is the first bucket of
// callers for this callee.
INLINE_AND_NO_INSTRUMENT_FUNCTION void RecordMiss(size_t index) {
  AtomicIncrement(g_caller_count + index + kMissesBucketIndex);
}

// Increment the caller count if it has previously been registered.
// If it hasn't, search for an empty bucket and register the caller.
// Otherwise, return false.
// |index| is the first bucket to register callers for a certain callee.
INLINE_AND_NO_INSTRUMENT_FUNCTION bool RecordCaller(size_t index,
                                                    size_t caller_offset) {
  for (size_t i = index; i < index + kCallerBuckets; i++) {
    auto offset = g_caller_offset[i].load(std::memory_order_relaxed);
    // This check is racy, a write could have happened between the load and the
    // check.
    if (offset == caller_offset) {
      // Caller already recorded, increment the count.
      AtomicIncrement(g_caller_count + i);
      return true;
    }
  }

  for (size_t i = index; i < index + kCallerBuckets; i++) {
    auto offset = g_caller_offset[i].load(std::memory_order_relaxed);
    size_t expected = 0;
    if (!offset) {
      // This is not racy as the compare and exchange is done atomically.
      // It is impossible to reset a bucket if it has already been set. It
      // exchanges the value in |g_caller_offset[i]| with |caller_offset| if
      // the value in |g_caller_offset[i] == expected|.
      // Otherwise, returns false and set |expected = g_caller_offset[i]|.
      if (g_caller_offset[i].compare_exchange_strong(
              expected, caller_offset, std::memory_order_relaxed,
              std::memory_order_relaxed)) {
        AtomicIncrement(g_caller_count + i);
        return true;
      }
    }
    // This will decrease the chances that we miss something due to unseen
    // changes made by another thread.
    if (offset == caller_offset || expected == caller_offset) {
      AtomicIncrement(g_caller_count + i);
      return true;
    }
  }
  return false;
}

template <bool for_testing>
__attribute__((always_inline, no_instrument_function)) void RecordAddress(
    size_t callee_address,
    size_t caller_address) {
  bool disabled = g_disabled.load(std::memory_order_relaxed);
  if (disabled)
    return;

  const size_t start =
      for_testing ? kStartOfTextForTesting : base::android::kStartOfText;
  const size_t end =
      for_testing ? kEndOfTextForTesting : base::android::kEndOfText;

  if (UNLIKELY(callee_address < start || callee_address > end)) {
    // Only the code in the native library is instrumented. Callees are expected
    // to be within the native library bounds.
    Disable();
    IMMEDIATE_CRASH();
  }

  size_t offset = callee_address - start;
  static_assert(sizeof(int) == 4,
                "Collection and processing code assumes that sizeof(int) == 4");

  size_t offset_index = offset / 4;

  if (UNLIKELY(offset_index >= kMaxElements))
    return;

  std::atomic<uint32_t>* element = callee_map + offset_index;
  uint32_t callers_index = element->load(std::memory_order_relaxed);

  // Racy check.
  if (callers_index == 0) {
    // Fragmentation is possible as we increment the |insertion_index| based on
    // a racy check.
    uint32_t insertion_index = AtomicIncrement(&g_callers_index) + 1;
    if (UNLIKELY(insertion_index >= kMaxReachedSymbols))
      return;

    uint32_t expected = 0;
    // Exchanges the value in |element| with |insertion_index| if the value in
    // |element == expected|. Otherwise, set |expected = element|.
    element->compare_exchange_strong(expected, insertion_index,
                                     std::memory_order_relaxed,
                                     std::memory_order_relaxed);
    // If expected is set, then this callee has previously been seen and already
    // has a corresponding index in the callers array.
    callers_index = expected == 0 ? insertion_index : expected;
  }

  AtomicIncrement(&g_calls_count);
  callers_index *= kTotalBuckets;
  if (caller_address <= start || caller_address > end ||
      !RecordCaller(callers_index, caller_address - start)) {
    // Record as a Miss, if the caller is not within the bounds of the native
    // code or there are no empty buckets to record one more caller for this
    // callee.
    RecordMiss(callers_index);
  }
}

NO_INSTRUMENT_FUNCTION bool DumpToFile(const base::FilePath& path) {
  auto file =
      base::File(path, base::File::FLAG_CREATE_ALWAYS | base::File::FLAG_WRITE);
  if (!file.IsValid()) {
    PLOG(ERROR) << "Could not open " << path;
    return false;
  }

  if (g_callers_index == 0) {
    LOG(ERROR) << "No entries to dump";
    return false;
  }

  // This can get very large as it  constructs the whole data structure in
  // memory before dumping it to the file.
  DictionaryValue root;
  uint32_t total_calls_count = g_calls_count.load(std::memory_order_relaxed);
  root.SetStringKey("total_calls_count",
                    base::StringPrintf("%" PRIu32, total_calls_count));
  ListValue call_graph;
  for (size_t i = 0; i < kMaxElements; i++) {
    auto caller_index =
        callee_map[i].load(std::memory_order_relaxed) * kTotalBuckets;
    if (!caller_index)
      // This callee was never called.
      continue;

    DictionaryValue callee_element;
    uint32_t callee_offset = i * 4;
    callee_element.SetStringKey("index",
                                base::StringPrintf("%" PRIuS, caller_index));
    callee_element.SetStringKey("callee_offset",
                                base::StringPrintf("%" PRIu32, callee_offset));
    std::string offset_str = "";
    ListValue callers_list;
    for (size_t j = 0; j < kTotalBuckets; j++) {
      uint32_t caller_offset =
          g_caller_offset[caller_index + j].load(std::memory_order_relaxed);

      // The last bucket is for misses or callers outside the native library,
      // the caller_offset for this bucket is 0.
      if (j != kMissesBucketIndex && !caller_offset)
        continue;

      uint32_t count =
          g_caller_count[caller_index + j].load(std::memory_order_relaxed);
      // The count can only be 0 for the misses bucket. Otherwise,
      // if |caller_offset| is set then the count must be >= 1.
      CHECK_EQ(count || j == kMissesBucketIndex, true);
      if (!count)
        // No misses.
        continue;

      DictionaryValue caller_count;
      caller_count.SetStringKey("caller_offset",
                                base::StringPrintf("%" PRIu32, caller_offset));
      caller_count.SetStringKey("count", base::StringPrintf("%" PRIu32, count));
      callers_list.Append(std::move(caller_count));
    }
    callee_element.SetKey("caller_and_count", std::move(callers_list));
    call_graph.Append(std::move(callee_element));
  }

  root.SetKey("call_graph", std::move(call_graph));
  std::string output_js;
  if (!JSONWriter::Write(root, &output_js)) {
    LOG(FATAL) << "Error getting JSON string";
  }
  if (file.WriteAtCurrentPos(output_js.c_str(),
                             static_cast<int>(output_js.size())) < 0) {
    // If the file could be opened, but writing has failed, it's likely that
    // data was partially written. Producing incomplete profiling data would
    // lead to a poorly performing orderfile, but might not be otherwised
    // noticed. So we crash instead.
    LOG(FATAL) << "Error writing profile data";
  }
  return true;
}

// Stops recording, and outputs the data to |path|.
NO_INSTRUMENT_FUNCTION void StopAndDumpToFile(int pid,
                                              uint64_t start_ns_since_epoch,
                                              const std::string& tag) {
  std::string tag_str;
  if (!tag.empty())
    tag_str = base::StringPrintf("%s-", tag.c_str());
  auto path = base::StringPrintf(
      "/data/local/tmp/chrome/orderfile/profile-hitmap-%s%d-%" PRIu64 ".txt",
      tag_str.c_str(), pid, start_ns_since_epoch);

  if (!DumpToFile(base::FilePath(path))) {
    LOG(ERROR) << "Problem with dump (" << tag << ")";
  }
}

}  // namespace

// It is safe to call any function after |Disable()| has been called. No risk of
// infinite recursion.
NO_INSTRUMENT_FUNCTION bool Disable() {
  bool disabled = g_disabled.exchange(true, std::memory_order_relaxed);
  std::atomic_thread_fence(std::memory_order_seq_cst);
  return !disabled;
}

NO_INSTRUMENT_FUNCTION void StartDelayedDump() {
#if BUILDFLAG(DEVTOOLS_INSTRUMENTATION_DUMPING)
  static auto* g_orderfile_memory_dump_hook = new OrderfileMemoryDumpHook();
  base::trace_event::MemoryDumpManager::GetInstance()->RegisterDumpProvider(
      g_orderfile_memory_dump_hook, "Orderfile", nullptr);
// Return, letting devtools tracing handle any dumping.
#else
  // Using std::thread and not using base::TimeTicks() in order to to not call
  // too many base:: symbols that would pollute the reached symbol dumps.
  struct timespec ts;
  if (clock_gettime(CLOCK_MONOTONIC, &ts))
    PLOG(FATAL) << "clock_gettime.";
  uint64_t start_ns_since_epoch =
      static_cast<uint64_t>(ts.tv_sec) * 1000 * 1000 * 1000 + ts.tv_nsec;
  int pid = getpid();
  std::thread([pid, start_ns_since_epoch]() {
    sleep(kDelayInSeconds);
    if (Disable())
      StopAndDumpToFile(pid, start_ns_since_epoch, "");
  }).detach();
#endif  // BUILDFLAG(DEVTOOLS_INSTRUMENTATION_DUMPING)
}

NO_INSTRUMENT_FUNCTION void Dump(const std::string& tag) {
  // As profiling has been disabled, none of the uses of ::base symbols below
  // will enter the symbol dump.
  StopAndDumpToFile(
      getpid(), (base::Time::Now() - base::Time::UnixEpoch()).InNanoseconds(),
      tag);
}

NO_INSTRUMENT_FUNCTION void ResetForTesting() {
  Disable();
  memset(reinterpret_cast<uint32_t*>(callee_map), 0,
         sizeof(uint32_t) * kMaxElements);
  memset(reinterpret_cast<uint32_t*>(g_caller_offset), 0,
         sizeof(uint32_t) * kMaxReachedSymbols * kTotalBuckets);
  memset(reinterpret_cast<uint32_t*>(g_caller_count), 0,
         sizeof(uint32_t) * kMaxReachedSymbols * kTotalBuckets);
  g_callers_index = 0;
  g_disabled = false;
}

NO_INSTRUMENT_FUNCTION void RecordAddressForTesting(size_t callee_address,
                                                    size_t caller_address) {
  return RecordAddress<true>(callee_address, caller_address);
}

// Returns a flattened vector where each callee is allocated 9 buckets.
// First bucket -> callee offset
// 8 buckets -> [caller offset, count, ...]
NO_INSTRUMENT_FUNCTION std::vector<size_t> GetOrderedOffsetsForTesting() {
  std::vector<size_t> result;
  for (size_t i = 0; i < kMaxElements; i++) {
    auto caller_index =
        callee_map[i].load(std::memory_order_relaxed) * kTotalBuckets;
    if (!caller_index)
      continue;

    result.push_back(i * 4);
    for (size_t j = 0; j < kTotalBuckets; j++) {
      uint32_t count =
          g_caller_count[caller_index + j].load(std::memory_order_relaxed);

      uint32_t caller_offset =
          g_caller_offset[caller_index + j].load(std::memory_order_relaxed);
      result.push_back(caller_offset);
      result.push_back(count);
    }
  }
  return result;
}

}  // namespace orderfile
}  // namespace android
}  // namespace base

extern "C" {

NO_INSTRUMENT_FUNCTION void __cyg_profile_func_enter_bare() {
  base::android::orderfile::RecordAddress<false>(
      reinterpret_cast<size_t>(__builtin_return_address(0)),
      reinterpret_cast<size_t>(__builtin_return_address(1)));
}

}  // extern "C"

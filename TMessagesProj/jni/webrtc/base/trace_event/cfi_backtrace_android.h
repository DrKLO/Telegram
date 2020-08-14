// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TRACE_EVENT_CFI_BACKTRACE_ANDROID_H_
#define BASE_TRACE_EVENT_CFI_BACKTRACE_ANDROID_H_

#include <stddef.h>
#include <stdint.h>

#include <memory>

#include "base/base_export.h"
#include "base/debug/debugging_buildflags.h"
#include "base/files/memory_mapped_file.h"
#include "base/gtest_prod_util.h"
#include "base/threading/thread_local_storage.h"

namespace base {
namespace trace_event {

// This class is used to unwind stack frames in the current thread. The unwind
// information (dwarf debug info) is stripped from the chrome binary and we do
// not build with exception tables (ARM EHABI) in release builds. So, we use a
// custom unwind table which is generated and added to specific android builds,
// when add_unwind_tables_in_apk build option is specified. This unwind table
// contains information for unwinding stack frames when the functions calls are
// from lib[mono]chrome.so. The file is added as an asset to the apk and the
// table is used to unwind stack frames for profiling. This class implements
// methods to read and parse the unwind table and unwind stack frames using this
// data.
class BASE_EXPORT CFIBacktraceAndroid {
 public:
  // Creates and initializes by memory mapping the unwind tables from apk assets
  // on first call.
  static CFIBacktraceAndroid* GetInitializedInstance();

  // Returns true if the given program counter |pc| is mapped in chrome library.
  static bool is_chrome_address(uintptr_t pc);

  // Returns the start and end address of the current library.
  static uintptr_t executable_start_addr();
  static uintptr_t executable_end_addr();

  // Returns true if stack unwinding is possible using CFI unwind tables in apk.
  // There is no need to check this before each unwind call. Will always return
  // the same value based on CFI tables being present in the binary.
  bool can_unwind_stack_frames() const { return can_unwind_stack_frames_; }

  // Returns the program counters by unwinding stack in the current thread in
  // order of latest call frame first. Unwinding works only if
  // can_unwind_stack_frames() returns true. This function allocates memory from
  // heap for cache on the first call of the calling thread, unless
  // AllocateCacheForCurrentThread() is called from the thread. For each stack
  // frame, this method searches through the unwind table mapped in memory to
  // find the unwind information for function and walks the stack to find all
  // the return address. This only works until the last function call from the
  // chrome.so. We do not have unwind information to unwind beyond any frame
  // outside of chrome.so. Calls to Unwind() are thread safe and lock free, once
  // Initialize() returns success.
  size_t Unwind(const void** out_trace, size_t max_depth);

  // Same as above function, but starts from a given program counter |pc|,
  // stack pointer |sp| and link register |lr|. This can be from current thread
  // or any other thread. But the caller must make sure that the thread's stack
  // segment is not racy to read.
  size_t Unwind(uintptr_t pc,
                uintptr_t sp,
                uintptr_t lr,
                const void** out_trace,
                size_t max_depth);

  // Allocates memory for CFI cache for the current thread so that Unwind()
  // calls are safe for signal handlers.
  void AllocateCacheForCurrentThread();

  // The CFI information that correspond to an instruction.
  struct CFIRow {
    bool operator==(const CFIBacktraceAndroid::CFIRow& o) const {
      return cfa_offset == o.cfa_offset && ra_offset == o.ra_offset;
    }

    // The offset of the call frame address of previous function from the
    // current stack pointer. Rule for unwinding SP: SP_prev = SP_cur +
    // cfa_offset.
    uint16_t cfa_offset = 0;
    // The offset of location of return address from the previous call frame
    // address. Rule for unwinding PC: PC_prev = * (SP_prev - ra_offset).
    uint16_t ra_offset = 0;
  };

  // Finds the CFI row for the given |func_addr| in terms of offset from
  // the start of the current binary. Concurrent calls are thread safe.
  bool FindCFIRowForPC(uintptr_t func_addr, CFIRow* out);

 private:
  FRIEND_TEST_ALL_PREFIXES(CFIBacktraceAndroidTest, TestCFICache);
  FRIEND_TEST_ALL_PREFIXES(CFIBacktraceAndroidTest, TestFindCFIRow);
  FRIEND_TEST_ALL_PREFIXES(CFIBacktraceAndroidTest, TestUnwinding);

  // A simple cache that stores entries in table using prime modulo hashing.
  // This cache with 500 entries already gives us 95% hit rate, and fits in a
  // single system page (usually 4KiB). Using a thread local cache for each
  // thread gives us 30% improvements on performance of heap profiling.
  class CFICache {
   public:
    // Add new item to the cache. It replaces an existing item with same hash.
    // Constant time operation.
    void Add(uintptr_t address, CFIRow cfi);

    // Finds the given address and fills |cfi| with the info for the address.
    // returns true if found, otherwise false. Assumes |address| is never 0.
    bool Find(uintptr_t address, CFIRow* cfi);

   private:
    FRIEND_TEST_ALL_PREFIXES(CFIBacktraceAndroidTest, TestCFICache);

    // Size is the highest prime which fits the cache in a single system page,
    // usually 4KiB. A prime is chosen to make sure addresses are hashed evenly.
    static const int kLimit = 509;

    struct AddrAndCFI {
      uintptr_t address;
      CFIRow cfi;
    };
    AddrAndCFI cache_[kLimit] = {};
  };

  static_assert(sizeof(CFIBacktraceAndroid::CFICache) < 4096,
                "The cache does not fit in a single page.");

  CFIBacktraceAndroid();
  ~CFIBacktraceAndroid();

  // Initializes unwind tables using the CFI asset file in the apk if present.
  // Also stores the limits of mapped region of the lib[mono]chrome.so binary,
  // since the unwind is only feasible for addresses within the .so file. Once
  // initialized, the memory map of the unwind table is never cleared since we
  // cannot guarantee that all the threads are done using the memory map when
  // heap profiling is turned off. But since we keep the memory map is clean,
  // the system can choose to evict the unused pages when needed. This would
  // still reduce the total amount of address space available in process.
  void Initialize();

  // Finds the UNW_INDEX and UNW_DATA tables in from the CFI file memory map.
  void ParseCFITables();

  CFICache* GetThreadLocalCFICache();

  // The start address of the memory mapped unwind table asset file. Unique ptr
  // because it is replaced in tests.
  std::unique_ptr<MemoryMappedFile> cfi_mmap_;

  // The UNW_INDEX table: Start address of the function address column. The
  // memory segment corresponding to this column is treated as an array of
  // uintptr_t.
  const uintptr_t* unw_index_function_col_ = nullptr;
  // The UNW_INDEX table: Start address of the index column. The memory segment
  // corresponding to this column is treated as an array of uint16_t.
  const uint16_t* unw_index_indices_col_ = nullptr;
  // The number of rows in UNW_INDEX table.
  size_t unw_index_row_count_ = 0;

  // The start address of UNW_DATA table.
  const uint16_t* unw_data_start_addr_ = nullptr;

  bool can_unwind_stack_frames_ = false;

  ThreadLocalStorage::Slot thread_local_cfi_cache_;
};

}  // namespace trace_event
}  // namespace base

#endif  // BASE_TRACE_EVENT_CFI_BACKTRACE_ANDROID_H_

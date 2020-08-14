// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/process_memory_dump.h"

#include <errno.h>

#include <vector>

#include "base/memory/ptr_util.h"
#include "base/memory/shared_memory_tracker.h"
#include "base/process/process_metrics.h"
#include "base/strings/stringprintf.h"
#include "base/trace_event/memory_infra_background_allowlist.h"
#include "base/trace_event/trace_event_impl.h"
#include "base/trace_event/traced_value.h"
#include "base/unguessable_token.h"
#include "build/build_config.h"

#if defined(OS_IOS)
#include <mach/vm_page_size.h>
#endif

#if defined(OS_POSIX) || defined(OS_FUCHSIA)
#include <sys/mman.h>
#endif

#if defined(OS_WIN)
#include <windows.h>  // Must be in front of other Windows header files

#include <Psapi.h>
#endif

namespace base {
namespace trace_event {

namespace {

const char kEdgeTypeOwnership[] = "ownership";

std::string GetSharedGlobalAllocatorDumpName(
    const MemoryAllocatorDumpGuid& guid) {
  return "global/" + guid.ToString();
}

#if defined(COUNT_RESIDENT_BYTES_SUPPORTED)
size_t GetSystemPageCount(size_t mapped_size, size_t page_size) {
  return (mapped_size + page_size - 1) / page_size;
}
#endif

UnguessableToken GetTokenForCurrentProcess() {
  static UnguessableToken instance = UnguessableToken::Create();
  return instance;
}

}  // namespace

// static
bool ProcessMemoryDump::is_black_hole_non_fatal_for_testing_ = false;

#if defined(COUNT_RESIDENT_BYTES_SUPPORTED)
// static
size_t ProcessMemoryDump::GetSystemPageSize() {
#if defined(OS_IOS)
  // On iOS, getpagesize() returns the user page sizes, but for allocating
  // arrays for mincore(), kernel page sizes is needed. Use vm_kernel_page_size
  // as recommended by Apple, https://forums.developer.apple.com/thread/47532/.
  // Refer to http://crbug.com/542671 and Apple rdar://23651782
  return vm_kernel_page_size;
#else
  return base::GetPageSize();
#endif  // defined(OS_IOS)
}

// static
size_t ProcessMemoryDump::CountResidentBytes(void* start_address,
                                             size_t mapped_size) {
  const size_t page_size = GetSystemPageSize();
  const uintptr_t start_pointer = reinterpret_cast<uintptr_t>(start_address);
  DCHECK_EQ(0u, start_pointer % page_size);

  size_t offset = 0;
  size_t total_resident_pages = 0;
  bool failure = false;

  // An array as large as number of pages in memory segment needs to be passed
  // to the query function. To avoid allocating a large array, the given block
  // of memory is split into chunks of size |kMaxChunkSize|.
  const size_t kMaxChunkSize = 8 * 1024 * 1024;
  size_t max_vec_size =
      GetSystemPageCount(std::min(mapped_size, kMaxChunkSize), page_size);
#if defined(OS_WIN)
  std::unique_ptr<PSAPI_WORKING_SET_EX_INFORMATION[]> vec(
      new PSAPI_WORKING_SET_EX_INFORMATION[max_vec_size]);
#elif defined(OS_MACOSX)
  std::unique_ptr<char[]> vec(new char[max_vec_size]);
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  std::unique_ptr<unsigned char[]> vec(new unsigned char[max_vec_size]);
#endif

  while (offset < mapped_size) {
    uintptr_t chunk_start = (start_pointer + offset);
    const size_t chunk_size = std::min(mapped_size - offset, kMaxChunkSize);
    const size_t page_count = GetSystemPageCount(chunk_size, page_size);
    size_t resident_page_count = 0;
#if defined(OS_WIN)
    for (size_t i = 0; i < page_count; i++) {
      vec[i].VirtualAddress =
          reinterpret_cast<void*>(chunk_start + i * page_size);
    }
    DWORD vec_size = static_cast<DWORD>(
        page_count * sizeof(PSAPI_WORKING_SET_EX_INFORMATION));
    failure = !QueryWorkingSetEx(GetCurrentProcess(), vec.get(), vec_size);

    for (size_t i = 0; i < page_count; i++)
      resident_page_count += vec[i].VirtualAttributes.Valid;
#elif defined(OS_FUCHSIA)
    // TODO(fuchsia): Port, see https://crbug.com/706592.
    ALLOW_UNUSED_LOCAL(chunk_start);
    ALLOW_UNUSED_LOCAL(page_count);
#elif defined(OS_MACOSX)
    // mincore in MAC does not fail with EAGAIN.
    failure =
        !!mincore(reinterpret_cast<void*>(chunk_start), chunk_size, vec.get());
    for (size_t i = 0; i < page_count; i++)
      resident_page_count += vec[i] & MINCORE_INCORE ? 1 : 0;
#elif defined(OS_POSIX)
    int error_counter = 0;
    int result = 0;
    // HANDLE_EINTR tries for 100 times. So following the same pattern.
    do {
      result =
#if defined(OS_AIX)
          mincore(reinterpret_cast<char*>(chunk_start), chunk_size,
                  reinterpret_cast<char*>(vec.get()));
#else
          mincore(reinterpret_cast<void*>(chunk_start), chunk_size, vec.get());
#endif
    } while (result == -1 && errno == EAGAIN && error_counter++ < 100);
    failure = !!result;

    for (size_t i = 0; i < page_count; i++)
      resident_page_count += vec[i] & 1;
#endif

    if (failure)
      break;

    total_resident_pages += resident_page_count * page_size;
    offset += kMaxChunkSize;
  }

  DCHECK(!failure);
  if (failure) {
    total_resident_pages = 0;
    LOG(ERROR) << "CountResidentBytes failed. The resident size is invalid";
  }
  return total_resident_pages;
}

// static
base::Optional<size_t> ProcessMemoryDump::CountResidentBytesInSharedMemory(
    void* start_address,
    size_t mapped_size) {
#if defined(OS_MACOSX) && !defined(OS_IOS)
  // On macOS, use mach_vm_region instead of mincore for performance
  // (crbug.com/742042).
  mach_vm_size_t dummy_size = 0;
  mach_vm_address_t address =
      reinterpret_cast<mach_vm_address_t>(start_address);
  vm_region_top_info_data_t info;
  MachVMRegionResult result =
      GetTopInfo(mach_task_self(), &dummy_size, &address, &info);
  if (result == MachVMRegionResult::Error) {
    LOG(ERROR) << "CountResidentBytesInSharedMemory failed. The resident size "
                  "is invalid";
    return base::Optional<size_t>();
  }

  size_t resident_pages =
      info.private_pages_resident + info.shared_pages_resident;

  // On macOS, measurements for private memory footprint overcount by
  // faulted pages in anonymous shared memory. To discount for this, we touch
  // all the resident pages in anonymous shared memory here, thus making them
  // faulted as well. This relies on two assumptions:
  //
  // 1) Consumers use shared memory from front to back. Thus, if there are
  // (N) resident pages, those pages represent the first N * PAGE_SIZE bytes in
  // the shared memory region.
  //
  // 2) This logic is run shortly before the logic that calculates
  // phys_footprint, thus ensuring that the discrepancy between faulted and
  // resident pages is minimal.
  //
  // The performance penalty is expected to be small.
  //
  // * Most of the time, we expect the pages to already be resident and faulted,
  // thus incurring a cache penalty read hit [since we read from each resident
  // page].
  //
  // * Rarely, we expect the pages to be resident but not faulted, resulting in
  // soft faults + cache penalty.
  //
  // * If assumption (1) is invalid, this will potentially fault some
  // previously non-resident pages, thus increasing memory usage, without fixing
  // the accounting.
  //
  // Sanity check in case the mapped size is less than the total size of the
  // region.
  size_t pages_to_fault =
      std::min(resident_pages, (mapped_size + PAGE_SIZE - 1) / PAGE_SIZE);

  volatile char* base_address = static_cast<char*>(start_address);
  for (size_t i = 0; i < pages_to_fault; ++i) {
    // Reading from a volatile is a visible side-effect for the purposes of
    // optimization. This guarantees that the optimizer will not kill this line.
    base_address[i * PAGE_SIZE];
  }

  return resident_pages * PAGE_SIZE;
#else
  return CountResidentBytes(start_address, mapped_size);
#endif  // defined(OS_MACOSX) && !defined(OS_IOS)
}

#endif  // defined(COUNT_RESIDENT_BYTES_SUPPORTED)

ProcessMemoryDump::ProcessMemoryDump(
    const MemoryDumpArgs& dump_args)
    : process_token_(GetTokenForCurrentProcess()),
      dump_args_(dump_args) {}

ProcessMemoryDump::~ProcessMemoryDump() = default;
ProcessMemoryDump::ProcessMemoryDump(ProcessMemoryDump&& other) = default;
ProcessMemoryDump& ProcessMemoryDump::operator=(ProcessMemoryDump&& other) =
    default;

MemoryAllocatorDump* ProcessMemoryDump::CreateAllocatorDump(
    const std::string& absolute_name) {
  return AddAllocatorDumpInternal(std::make_unique<MemoryAllocatorDump>(
      absolute_name, dump_args_.level_of_detail, GetDumpId(absolute_name)));
}

MemoryAllocatorDump* ProcessMemoryDump::CreateAllocatorDump(
    const std::string& absolute_name,
    const MemoryAllocatorDumpGuid& guid) {
  return AddAllocatorDumpInternal(std::make_unique<MemoryAllocatorDump>(
      absolute_name, dump_args_.level_of_detail, guid));
}

MemoryAllocatorDump* ProcessMemoryDump::AddAllocatorDumpInternal(
    std::unique_ptr<MemoryAllocatorDump> mad) {
  // In background mode return the black hole dump, if invalid dump name is
  // given.
  if (dump_args_.level_of_detail == MemoryDumpLevelOfDetail::BACKGROUND &&
      !IsMemoryAllocatorDumpNameInAllowlist(mad->absolute_name())) {
    return GetBlackHoleMad();
  }

  auto insertion_result = allocator_dumps_.insert(
      std::make_pair(mad->absolute_name(), std::move(mad)));
  MemoryAllocatorDump* inserted_mad = insertion_result.first->second.get();
  DCHECK(insertion_result.second) << "Duplicate name: "
                                  << inserted_mad->absolute_name();
  return inserted_mad;
}

MemoryAllocatorDump* ProcessMemoryDump::GetAllocatorDump(
    const std::string& absolute_name) const {
  auto it = allocator_dumps_.find(absolute_name);
  if (it != allocator_dumps_.end())
    return it->second.get();
  return nullptr;
}

MemoryAllocatorDump* ProcessMemoryDump::GetOrCreateAllocatorDump(
    const std::string& absolute_name) {
  MemoryAllocatorDump* mad = GetAllocatorDump(absolute_name);
  return mad ? mad : CreateAllocatorDump(absolute_name);
}

MemoryAllocatorDump* ProcessMemoryDump::CreateSharedGlobalAllocatorDump(
    const MemoryAllocatorDumpGuid& guid) {
  // A shared allocator dump can be shared within a process and the guid could
  // have been created already.
  MemoryAllocatorDump* mad = GetSharedGlobalAllocatorDump(guid);
  if (mad && mad != black_hole_mad_.get()) {
    // The weak flag is cleared because this method should create a non-weak
    // dump.
    mad->clear_flags(MemoryAllocatorDump::Flags::WEAK);
    return mad;
  }
  return CreateAllocatorDump(GetSharedGlobalAllocatorDumpName(guid), guid);
}

MemoryAllocatorDump* ProcessMemoryDump::CreateWeakSharedGlobalAllocatorDump(
    const MemoryAllocatorDumpGuid& guid) {
  MemoryAllocatorDump* mad = GetSharedGlobalAllocatorDump(guid);
  if (mad && mad != black_hole_mad_.get())
    return mad;
  mad = CreateAllocatorDump(GetSharedGlobalAllocatorDumpName(guid), guid);
  mad->set_flags(MemoryAllocatorDump::Flags::WEAK);
  return mad;
}

MemoryAllocatorDump* ProcessMemoryDump::GetSharedGlobalAllocatorDump(
    const MemoryAllocatorDumpGuid& guid) const {
  return GetAllocatorDump(GetSharedGlobalAllocatorDumpName(guid));
}

void ProcessMemoryDump::DumpHeapUsage(
    const std::unordered_map<base::trace_event::AllocationContext,
                             base::trace_event::AllocationMetrics>&
        metrics_by_context,
    base::trace_event::TraceEventMemoryOverhead& overhead,
    const char* allocator_name) {
  std::string base_name = base::StringPrintf("tracing/heap_profiler_%s",
                                             allocator_name);
  overhead.DumpInto(base_name.c_str(), this);
}

void ProcessMemoryDump::SetAllocatorDumpsForSerialization(
    std::vector<std::unique_ptr<MemoryAllocatorDump>> dumps) {
  DCHECK(allocator_dumps_.empty());
  for (std::unique_ptr<MemoryAllocatorDump>& dump : dumps)
    AddAllocatorDumpInternal(std::move(dump));
}

std::vector<ProcessMemoryDump::MemoryAllocatorDumpEdge>
ProcessMemoryDump::GetAllEdgesForSerialization() const {
  std::vector<MemoryAllocatorDumpEdge> edges;
  edges.reserve(allocator_dumps_edges_.size());
  for (const auto& it : allocator_dumps_edges_)
    edges.push_back(it.second);
  return edges;
}

void ProcessMemoryDump::SetAllEdgesForSerialization(
    const std::vector<ProcessMemoryDump::MemoryAllocatorDumpEdge>& edges) {
  DCHECK(allocator_dumps_edges_.empty());
  for (const MemoryAllocatorDumpEdge& edge : edges) {
    auto it_and_inserted = allocator_dumps_edges_.emplace(edge.source, edge);
    DCHECK(it_and_inserted.second);
  }
}

void ProcessMemoryDump::Clear() {
  allocator_dumps_.clear();
  allocator_dumps_edges_.clear();
}

void ProcessMemoryDump::TakeAllDumpsFrom(ProcessMemoryDump* other) {
  // Moves the ownership of all MemoryAllocatorDump(s) contained in |other|
  // into this ProcessMemoryDump, checking for duplicates.
  for (auto& it : other->allocator_dumps_)
    AddAllocatorDumpInternal(std::move(it.second));
  other->allocator_dumps_.clear();

  // Move all the edges.
  allocator_dumps_edges_.insert(other->allocator_dumps_edges_.begin(),
                                other->allocator_dumps_edges_.end());
  other->allocator_dumps_edges_.clear();
}

void ProcessMemoryDump::SerializeAllocatorDumpsInto(TracedValue* value) const {
  if (allocator_dumps_.size() > 0) {
    value->BeginDictionary("allocators");
    for (const auto& allocator_dump_it : allocator_dumps_)
      allocator_dump_it.second->AsValueInto(value);
    value->EndDictionary();
  }

  value->BeginArray("allocators_graph");
  for (const auto& it : allocator_dumps_edges_) {
    const MemoryAllocatorDumpEdge& edge = it.second;
    value->BeginDictionary();
    value->SetString("source", edge.source.ToString());
    value->SetString("target", edge.target.ToString());
    value->SetInteger("importance", edge.importance);
    value->SetString("type", kEdgeTypeOwnership);
    value->EndDictionary();
  }
  value->EndArray();
}

void ProcessMemoryDump::AddOwnershipEdge(const MemoryAllocatorDumpGuid& source,
                                         const MemoryAllocatorDumpGuid& target,
                                         int importance) {
  // This will either override an existing edge or create a new one.
  auto it = allocator_dumps_edges_.find(source);
  int max_importance = importance;
  if (it != allocator_dumps_edges_.end()) {
    DCHECK_EQ(target.ToUint64(), it->second.target.ToUint64());
    max_importance = std::max(importance, it->second.importance);
  }
  allocator_dumps_edges_[source] = {source, target, max_importance,
                                    false /* overridable */};
}

void ProcessMemoryDump::AddOwnershipEdge(
    const MemoryAllocatorDumpGuid& source,
    const MemoryAllocatorDumpGuid& target) {
  AddOwnershipEdge(source, target, 0 /* importance */);
}

void ProcessMemoryDump::AddOverridableOwnershipEdge(
    const MemoryAllocatorDumpGuid& source,
    const MemoryAllocatorDumpGuid& target,
    int importance) {
  if (allocator_dumps_edges_.count(source) == 0) {
    allocator_dumps_edges_[source] = {source, target, importance,
                                      true /* overridable */};
  } else {
    // An edge between the source and target already exits. So, do nothing here
    // since the new overridable edge is implicitly overridden by a strong edge
    // which was created earlier.
    DCHECK(!allocator_dumps_edges_[source].overridable);
  }
}

void ProcessMemoryDump::CreateSharedMemoryOwnershipEdge(
    const MemoryAllocatorDumpGuid& client_local_dump_guid,
    const UnguessableToken& shared_memory_guid,
    int importance) {
  CreateSharedMemoryOwnershipEdgeInternal(client_local_dump_guid,
                                          shared_memory_guid, importance,
                                          false /*is_weak*/);
}

void ProcessMemoryDump::CreateWeakSharedMemoryOwnershipEdge(
    const MemoryAllocatorDumpGuid& client_local_dump_guid,
    const UnguessableToken& shared_memory_guid,
    int importance) {
  CreateSharedMemoryOwnershipEdgeInternal(
      client_local_dump_guid, shared_memory_guid, importance, true /*is_weak*/);
}

void ProcessMemoryDump::CreateSharedMemoryOwnershipEdgeInternal(
    const MemoryAllocatorDumpGuid& client_local_dump_guid,
    const UnguessableToken& shared_memory_guid,
    int importance,
    bool is_weak) {
  DCHECK(!shared_memory_guid.is_empty());
  // New model where the global dumps created by SharedMemoryTracker are used
  // for the clients.

  // The guid of the local dump created by SharedMemoryTracker for the memory
  // segment.
  auto local_shm_guid =
      GetDumpId(SharedMemoryTracker::GetDumpNameForTracing(shared_memory_guid));

  // The dump guid of the global dump created by the tracker for the memory
  // segment.
  auto global_shm_guid =
      SharedMemoryTracker::GetGlobalDumpIdForTracing(shared_memory_guid);

  // Create an edge between local dump of the client and the local dump of the
  // SharedMemoryTracker. Do not need to create the dumps here since the tracker
  // would create them. The importance is also required here for the case of
  // single process mode.
  AddOwnershipEdge(client_local_dump_guid, local_shm_guid, importance);

  // TODO(ssid): Handle the case of weak dumps here. This needs a new function
  // GetOrCreaetGlobalDump() in PMD since we need to change the behavior of the
  // created global dump.
  // Create an edge that overrides the edge created by SharedMemoryTracker.
  AddOwnershipEdge(local_shm_guid, global_shm_guid, importance);
}

void ProcessMemoryDump::AddSuballocation(const MemoryAllocatorDumpGuid& source,
                                         const std::string& target_node_name) {
  // Do not create new dumps for suballocations in background mode.
  if (dump_args_.level_of_detail == MemoryDumpLevelOfDetail::BACKGROUND)
    return;

  std::string child_mad_name = target_node_name + "/__" + source.ToString();
  MemoryAllocatorDump* target_child_mad = CreateAllocatorDump(child_mad_name);
  AddOwnershipEdge(source, target_child_mad->guid());
}

MemoryAllocatorDump* ProcessMemoryDump::GetBlackHoleMad() {
  DCHECK(is_black_hole_non_fatal_for_testing_);
  if (!black_hole_mad_) {
    std::string name = "discarded";
    black_hole_mad_.reset(new MemoryAllocatorDump(
        name, dump_args_.level_of_detail, GetDumpId(name)));
  }
  return black_hole_mad_.get();
}

MemoryAllocatorDumpGuid ProcessMemoryDump::GetDumpId(
    const std::string& absolute_name) {
  return MemoryAllocatorDumpGuid(StringPrintf(
      "%s:%s", process_token().ToString().c_str(), absolute_name.c_str()));
}

bool ProcessMemoryDump::MemoryAllocatorDumpEdge::operator==(
    const MemoryAllocatorDumpEdge& other) const {
  return source == other.source && target == other.target &&
         importance == other.importance && overridable == other.overridable;
}

bool ProcessMemoryDump::MemoryAllocatorDumpEdge::operator!=(
    const MemoryAllocatorDumpEdge& other) const {
  return !(*this == other);
}

}  // namespace trace_event
}  // namespace base

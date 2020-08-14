// Copyright (c) 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/metrics/persistent_memory_allocator.h"

#include <assert.h>
#include <algorithm>

#if defined(OS_WIN)
#include <windows.h>
#include "winbase.h"
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
#include <sys/mman.h>
#endif

#include "base/debug/alias.h"
#include "base/files/memory_mapped_file.h"
#include "base/logging.h"
#include "base/metrics/histogram_functions.h"
#include "base/metrics/sparse_histogram.h"
#include "base/numerics/safe_conversions.h"
#include "base/optional.h"
#include "base/system/sys_info.h"
#include "base/threading/scoped_blocking_call.h"
#include "build/build_config.h"

namespace {

// Limit of memory segment size. It has to fit in an unsigned 32-bit number
// and should be a power of 2 in order to accommodate almost any page size.
const uint32_t kSegmentMaxSize = 1 << 30;  // 1 GiB

// A constant (random) value placed in the shared metadata to identify
// an already initialized memory segment.
const uint32_t kGlobalCookie = 0x408305DC;

// The current version of the metadata. If updates are made that change
// the metadata, the version number can be queried to operate in a backward-
// compatible manner until the memory segment is completely re-initalized.
const uint32_t kGlobalVersion = 2;

// Constant values placed in the block headers to indicate its state.
const uint32_t kBlockCookieFree = 0;
const uint32_t kBlockCookieQueue = 1;
const uint32_t kBlockCookieWasted = (uint32_t)-1;
const uint32_t kBlockCookieAllocated = 0xC8799269;

// TODO(bcwhite): When acceptable, consider moving flags to std::atomic<char>
// types rather than combined bitfield.

// Flags stored in the flags_ field of the SharedMetadata structure below.
enum : int {
  kFlagCorrupt = 1 << 0,
  kFlagFull    = 1 << 1
};

// Errors that are logged in "errors" histogram.
enum AllocatorError : int {
  kMemoryIsCorrupt = 1,
};

bool CheckFlag(const volatile std::atomic<uint32_t>* flags, int flag) {
  uint32_t loaded_flags = flags->load(std::memory_order_relaxed);
  return (loaded_flags & flag) != 0;
}

void SetFlag(volatile std::atomic<uint32_t>* flags, int flag) {
  uint32_t loaded_flags = flags->load(std::memory_order_relaxed);
  for (;;) {
    uint32_t new_flags = (loaded_flags & ~flag) | flag;
    // In the failue case, actual "flags" value stored in loaded_flags.
    // These access are "relaxed" because they are completely independent
    // of all other values.
    if (flags->compare_exchange_weak(loaded_flags, new_flags,
                                     std::memory_order_relaxed,
                                     std::memory_order_relaxed)) {
      break;
    }
  }
}

}  // namespace

namespace base {

// All allocations and data-structures must be aligned to this byte boundary.
// Alignment as large as the physical bus between CPU and RAM is _required_
// for some architectures, is simply more efficient on other CPUs, and
// generally a Good Idea(tm) for all platforms as it reduces/eliminates the
// chance that a type will span cache lines. Alignment mustn't be less
// than 8 to ensure proper alignment for all types. The rest is a balance
// between reducing spans across multiple cache lines and wasted space spent
// padding out allocations. An alignment of 16 would ensure that the block
// header structure always sits in a single cache line. An average of about
// 1/2 this value will be wasted with every allocation.
const uint32_t PersistentMemoryAllocator::kAllocAlignment = 8;

// The block-header is placed at the top of every allocation within the
// segment to describe the data that follows it.
struct PersistentMemoryAllocator::BlockHeader {
  uint32_t size;       // Number of bytes in this block, including header.
  uint32_t cookie;     // Constant value indicating completed allocation.
  std::atomic<uint32_t> type_id;  // Arbitrary number indicating data type.
  std::atomic<uint32_t> next;     // Pointer to the next block when iterating.
};

// The shared metadata exists once at the top of the memory segment to
// describe the state of the allocator to all processes. The size of this
// structure must be a multiple of 64-bits to ensure compatibility between
// architectures.
struct PersistentMemoryAllocator::SharedMetadata {
  uint32_t cookie;     // Some value that indicates complete initialization.
  uint32_t size;       // Total size of memory segment.
  uint32_t page_size;  // Paging size within memory segment.
  uint32_t version;    // Version code so upgrades don't break.
  uint64_t id;         // Arbitrary ID number given by creator.
  uint32_t name;       // Reference to stored name string.
  uint32_t padding1;   // Pad-out read-only data to 64-bit alignment.

  // Above is read-only after first construction. Below may be changed and
  // so must be marked "volatile" to provide correct inter-process behavior.

  // State of the memory, plus some padding to keep alignment.
  volatile std::atomic<uint8_t> memory_state;  // MemoryState enum values.
  uint8_t padding2[3];

  // Bitfield of information flags. Access to this should be done through
  // the CheckFlag() and SetFlag() methods defined above.
  volatile std::atomic<uint32_t> flags;

  // Offset/reference to first free space in segment.
  volatile std::atomic<uint32_t> freeptr;

  // The "iterable" queue is an M&S Queue as described here, append-only:
  // https://www.research.ibm.com/people/m/michael/podc-1996.pdf
  // |queue| needs to be 64-bit aligned and is itself a multiple of 64 bits.
  volatile std::atomic<uint32_t> tailptr;  // Last block of iteration queue.
  volatile BlockHeader queue;   // Empty block for linked-list head/tail.
};

// The "queue" block header is used to detect "last node" so that zero/null
// can be used to indicate that it hasn't been added at all. It is part of
// the SharedMetadata structure which itself is always located at offset zero.
const PersistentMemoryAllocator::Reference
    PersistentMemoryAllocator::kReferenceQueue =
        offsetof(SharedMetadata, queue);

const base::FilePath::CharType PersistentMemoryAllocator::kFileExtension[] =
    FILE_PATH_LITERAL(".pma");


PersistentMemoryAllocator::Iterator::Iterator(
    const PersistentMemoryAllocator* allocator)
    : allocator_(allocator), last_record_(kReferenceQueue), record_count_(0) {}

PersistentMemoryAllocator::Iterator::Iterator(
    const PersistentMemoryAllocator* allocator,
    Reference starting_after)
    : allocator_(allocator), last_record_(0), record_count_(0) {
  Reset(starting_after);
}

void PersistentMemoryAllocator::Iterator::Reset() {
  last_record_.store(kReferenceQueue, std::memory_order_relaxed);
  record_count_.store(0, std::memory_order_relaxed);
}

void PersistentMemoryAllocator::Iterator::Reset(Reference starting_after) {
  if (starting_after == 0) {
    Reset();
    return;
  }

  last_record_.store(starting_after, std::memory_order_relaxed);
  record_count_.store(0, std::memory_order_relaxed);

  // Ensure that the starting point is a valid, iterable block (meaning it can
  // be read and has a non-zero "next" pointer).
  const volatile BlockHeader* block =
      allocator_->GetBlock(starting_after, 0, 0, false, false);
  if (!block || block->next.load(std::memory_order_relaxed) == 0) {
    NOTREACHED();
    last_record_.store(kReferenceQueue, std::memory_order_release);
  }
}

PersistentMemoryAllocator::Reference
PersistentMemoryAllocator::Iterator::GetLast() {
  Reference last = last_record_.load(std::memory_order_relaxed);
  if (last == kReferenceQueue)
    return kReferenceNull;
  return last;
}

PersistentMemoryAllocator::Reference
PersistentMemoryAllocator::Iterator::GetNext(uint32_t* type_return) {
  // Make a copy of the existing count of found-records, acquiring all changes
  // made to the allocator, notably "freeptr" (see comment in loop for why
  // the load of that value cannot be moved above here) that occurred during
  // any previous runs of this method, including those by parallel threads
  // that interrupted it. It pairs with the Release at the end of this method.
  //
  // Otherwise, if the compiler were to arrange the two loads such that
  // "count" was fetched _after_ "freeptr" then it would be possible for
  // this thread to be interrupted between them and other threads perform
  // multiple allocations, make-iterables, and iterations (with the included
  // increment of |record_count_|) culminating in the check at the bottom
  // mistakenly determining that a loop exists. Isn't this stuff fun?
  uint32_t count = record_count_.load(std::memory_order_acquire);

  Reference last = last_record_.load(std::memory_order_acquire);
  Reference next;
  while (true) {
    const volatile BlockHeader* block =
        allocator_->GetBlock(last, 0, 0, true, false);
    if (!block)  // Invalid iterator state.
      return kReferenceNull;

    // The compiler and CPU can freely reorder all memory accesses on which
    // there are no dependencies. It could, for example, move the load of
    // "freeptr" to above this point because there are no explicit dependencies
    // between it and "next". If it did, however, then another block could
    // be queued after that but before the following load meaning there is
    // one more queued block than the future "detect loop by having more
    // blocks that could fit before freeptr" will allow.
    //
    // By "acquiring" the "next" value here, it's synchronized to the enqueue
    // of the node which in turn is synchronized to the allocation (which sets
    // freeptr). Thus, the scenario above cannot happen.
    next = block->next.load(std::memory_order_acquire);
    if (next == kReferenceQueue)  // No next allocation in queue.
      return kReferenceNull;
    block = allocator_->GetBlock(next, 0, 0, false, false);
    if (!block) {  // Memory is corrupt.
      allocator_->SetCorrupt();
      return kReferenceNull;
    }

    // Update the "last_record" pointer to be the reference being returned.
    // If it fails then another thread has already iterated past it so loop
    // again. Failing will also load the existing value into "last" so there
    // is no need to do another such load when the while-loop restarts. A
    // "strong" compare-exchange is used because failing unnecessarily would
    // mean repeating some fairly costly validations above.
    if (last_record_.compare_exchange_strong(
            last, next, std::memory_order_acq_rel, std::memory_order_acquire)) {
      *type_return = block->type_id.load(std::memory_order_relaxed);
      break;
    }
  }

  // Memory corruption could cause a loop in the list. Such must be detected
  // so as to not cause an infinite loop in the caller. This is done by simply
  // making sure it doesn't iterate more times than the absolute maximum
  // number of allocations that could have been made. Callers are likely
  // to loop multiple times before it is detected but at least it stops.
  const uint32_t freeptr = std::min(
      allocator_->shared_meta()->freeptr.load(std::memory_order_relaxed),
      allocator_->mem_size_);
  const uint32_t max_records =
      freeptr / (sizeof(BlockHeader) + kAllocAlignment);
  if (count > max_records) {
    allocator_->SetCorrupt();
    return kReferenceNull;
  }

  // Increment the count and release the changes made above. It pairs with
  // the Acquire at the top of this method. Note that this operation is not
  // strictly synchonized with fetching of the object to return, which would
  // have to be done inside the loop and is somewhat complicated to achieve.
  // It does not matter if it falls behind temporarily so long as it never
  // gets ahead.
  record_count_.fetch_add(1, std::memory_order_release);
  return next;
}

PersistentMemoryAllocator::Reference
PersistentMemoryAllocator::Iterator::GetNextOfType(uint32_t type_match) {
  Reference ref;
  uint32_t type_found;
  while ((ref = GetNext(&type_found)) != 0) {
    if (type_found == type_match)
      return ref;
  }
  return kReferenceNull;
}


// static
bool PersistentMemoryAllocator::IsMemoryAcceptable(const void* base,
                                                   size_t size,
                                                   size_t page_size,
                                                   bool readonly) {
  return ((base && reinterpret_cast<uintptr_t>(base) % kAllocAlignment == 0) &&
          (size >= sizeof(SharedMetadata) && size <= kSegmentMaxSize) &&
          (size % kAllocAlignment == 0 || readonly) &&
          (page_size == 0 || size % page_size == 0 || readonly));
}

PersistentMemoryAllocator::PersistentMemoryAllocator(void* base,
                                                     size_t size,
                                                     size_t page_size,
                                                     uint64_t id,
                                                     base::StringPiece name,
                                                     bool readonly)
    : PersistentMemoryAllocator(Memory(base, MEM_EXTERNAL),
                                size,
                                page_size,
                                id,
                                name,
                                readonly) {}

PersistentMemoryAllocator::PersistentMemoryAllocator(Memory memory,
                                                     size_t size,
                                                     size_t page_size,
                                                     uint64_t id,
                                                     base::StringPiece name,
                                                     bool readonly)
    : mem_base_(static_cast<char*>(memory.base)),
      mem_type_(memory.type),
      mem_size_(static_cast<uint32_t>(size)),
      mem_page_(static_cast<uint32_t>((page_size ? page_size : size))),
#if defined(OS_NACL)
      vm_page_size_(4096U),  // SysInfo is not built for NACL.
#else
      vm_page_size_(SysInfo::VMAllocationGranularity()),
#endif
      readonly_(readonly),
      corrupt_(0),
      allocs_histogram_(nullptr),
      used_histogram_(nullptr),
      errors_histogram_(nullptr) {
  // These asserts ensure that the structures are 32/64-bit agnostic and meet
  // all the requirements of use within the allocator. They access private
  // definitions and so cannot be moved to the global scope.
  static_assert(sizeof(PersistentMemoryAllocator::BlockHeader) == 16,
                "struct is not portable across different natural word widths");
  static_assert(sizeof(PersistentMemoryAllocator::SharedMetadata) == 64,
                "struct is not portable across different natural word widths");

  static_assert(sizeof(BlockHeader) % kAllocAlignment == 0,
                "BlockHeader is not a multiple of kAllocAlignment");
  static_assert(sizeof(SharedMetadata) % kAllocAlignment == 0,
                "SharedMetadata is not a multiple of kAllocAlignment");
  static_assert(kReferenceQueue % kAllocAlignment == 0,
                "\"queue\" is not aligned properly; must be at end of struct");

  // Ensure that memory segment is of acceptable size.
  CHECK(IsMemoryAcceptable(memory.base, size, page_size, readonly));

  // These atomics operate inter-process and so must be lock-free.
  DCHECK(SharedMetadata().freeptr.is_lock_free());
  DCHECK(SharedMetadata().flags.is_lock_free());
  DCHECK(BlockHeader().next.is_lock_free());
  CHECK(corrupt_.is_lock_free());

  if (shared_meta()->cookie != kGlobalCookie) {
    if (readonly) {
      SetCorrupt();
      return;
    }

    // This block is only executed when a completely new memory segment is
    // being initialized. It's unshared and single-threaded...
    volatile BlockHeader* const first_block =
        reinterpret_cast<volatile BlockHeader*>(mem_base_ +
                                                sizeof(SharedMetadata));
    if (shared_meta()->cookie != 0 ||
        shared_meta()->size != 0 ||
        shared_meta()->version != 0 ||
        shared_meta()->freeptr.load(std::memory_order_relaxed) != 0 ||
        shared_meta()->flags.load(std::memory_order_relaxed) != 0 ||
        shared_meta()->id != 0 ||
        shared_meta()->name != 0 ||
        shared_meta()->tailptr != 0 ||
        shared_meta()->queue.cookie != 0 ||
        shared_meta()->queue.next.load(std::memory_order_relaxed) != 0 ||
        first_block->size != 0 ||
        first_block->cookie != 0 ||
        first_block->type_id.load(std::memory_order_relaxed) != 0 ||
        first_block->next != 0) {
      // ...or something malicious has been playing with the metadata.
      SetCorrupt();
    }

    // This is still safe to do even if corruption has been detected.
    shared_meta()->cookie = kGlobalCookie;
    shared_meta()->size = mem_size_;
    shared_meta()->page_size = mem_page_;
    shared_meta()->version = kGlobalVersion;
    shared_meta()->id = id;
    shared_meta()->freeptr.store(sizeof(SharedMetadata),
                                 std::memory_order_release);

    // Set up the queue of iterable allocations.
    shared_meta()->queue.size = sizeof(BlockHeader);
    shared_meta()->queue.cookie = kBlockCookieQueue;
    shared_meta()->queue.next.store(kReferenceQueue, std::memory_order_release);
    shared_meta()->tailptr.store(kReferenceQueue, std::memory_order_release);

    // Allocate space for the name so other processes can learn it.
    if (!name.empty()) {
      const size_t name_length = name.length() + 1;
      shared_meta()->name = Allocate(name_length, 0);
      char* name_cstr = GetAsArray<char>(shared_meta()->name, 0, name_length);
      if (name_cstr)
        memcpy(name_cstr, name.data(), name.length());
    }

    shared_meta()->memory_state.store(MEMORY_INITIALIZED,
                                      std::memory_order_release);
  } else {
    if (shared_meta()->size == 0 || shared_meta()->version != kGlobalVersion ||
        shared_meta()->freeptr.load(std::memory_order_relaxed) == 0 ||
        shared_meta()->tailptr == 0 || shared_meta()->queue.cookie == 0 ||
        shared_meta()->queue.next.load(std::memory_order_relaxed) == 0) {
      SetCorrupt();
    }
    if (!readonly) {
      // The allocator is attaching to a previously initialized segment of
      // memory. If the initialization parameters differ, make the best of it
      // by reducing the local construction parameters to match those of
      // the actual memory area. This ensures that the local object never
      // tries to write outside of the original bounds.
      // Because the fields are const to ensure that no code other than the
      // constructor makes changes to them as well as to give optimization
      // hints to the compiler, it's necessary to const-cast them for changes
      // here.
      if (shared_meta()->size < mem_size_)
        *const_cast<uint32_t*>(&mem_size_) = shared_meta()->size;
      if (shared_meta()->page_size < mem_page_)
        *const_cast<uint32_t*>(&mem_page_) = shared_meta()->page_size;

      // Ensure that settings are still valid after the above adjustments.
      if (!IsMemoryAcceptable(memory.base, mem_size_, mem_page_, readonly))
        SetCorrupt();
    }
  }
}

PersistentMemoryAllocator::~PersistentMemoryAllocator() {
  // It's strictly forbidden to do any memory access here in case there is
  // some issue with the underlying memory segment. The "Local" allocator
  // makes use of this to allow deletion of the segment on the heap from
  // within its destructor.
}

uint64_t PersistentMemoryAllocator::Id() const {
  return shared_meta()->id;
}

const char* PersistentMemoryAllocator::Name() const {
  Reference name_ref = shared_meta()->name;
  const char* name_cstr =
      GetAsArray<char>(name_ref, 0, PersistentMemoryAllocator::kSizeAny);
  if (!name_cstr)
    return "";

  size_t name_length = GetAllocSize(name_ref);
  if (name_cstr[name_length - 1] != '\0') {
    NOTREACHED();
    SetCorrupt();
    return "";
  }

  return name_cstr;
}

void PersistentMemoryAllocator::CreateTrackingHistograms(
    base::StringPiece name) {
  if (name.empty() || readonly_)
    return;
  std::string name_string = name.as_string();

#if 0
  // This histogram wasn't being used so has been disabled. It is left here
  // in case development of a new use of the allocator could benefit from
  // recording (temporarily and locally) the allocation sizes.
  DCHECK(!allocs_histogram_);
  allocs_histogram_ = Histogram::FactoryGet(
      "UMA.PersistentAllocator." + name_string + ".Allocs", 1, 10000, 50,
      HistogramBase::kUmaTargetedHistogramFlag);
#endif

  DCHECK(!used_histogram_);
  used_histogram_ = LinearHistogram::FactoryGet(
      "UMA.PersistentAllocator." + name_string + ".UsedPct", 1, 101, 21,
      HistogramBase::kUmaTargetedHistogramFlag);

  DCHECK(!errors_histogram_);
  errors_histogram_ = SparseHistogram::FactoryGet(
      "UMA.PersistentAllocator." + name_string + ".Errors",
      HistogramBase::kUmaTargetedHistogramFlag);
}

void PersistentMemoryAllocator::Flush(bool sync) {
  FlushPartial(used(), sync);
}

void PersistentMemoryAllocator::SetMemoryState(uint8_t memory_state) {
  shared_meta()->memory_state.store(memory_state, std::memory_order_relaxed);
  FlushPartial(sizeof(SharedMetadata), false);
}

uint8_t PersistentMemoryAllocator::GetMemoryState() const {
  return shared_meta()->memory_state.load(std::memory_order_relaxed);
}

size_t PersistentMemoryAllocator::used() const {
  return std::min(shared_meta()->freeptr.load(std::memory_order_relaxed),
                  mem_size_);
}

PersistentMemoryAllocator::Reference PersistentMemoryAllocator::GetAsReference(
    const void* memory,
    uint32_t type_id) const {
  uintptr_t address = reinterpret_cast<uintptr_t>(memory);
  if (address < reinterpret_cast<uintptr_t>(mem_base_))
    return kReferenceNull;

  uintptr_t offset = address - reinterpret_cast<uintptr_t>(mem_base_);
  if (offset >= mem_size_ || offset < sizeof(BlockHeader))
    return kReferenceNull;

  Reference ref = static_cast<Reference>(offset) - sizeof(BlockHeader);
  if (!GetBlockData(ref, type_id, kSizeAny))
    return kReferenceNull;

  return ref;
}

size_t PersistentMemoryAllocator::GetAllocSize(Reference ref) const {
  const volatile BlockHeader* const block = GetBlock(ref, 0, 0, false, false);
  if (!block)
    return 0;
  uint32_t size = block->size;
  // Header was verified by GetBlock() but a malicious actor could change
  // the value between there and here. Check it again.
  if (size <= sizeof(BlockHeader) || ref + size > mem_size_) {
    SetCorrupt();
    return 0;
  }
  return size - sizeof(BlockHeader);
}

uint32_t PersistentMemoryAllocator::GetType(Reference ref) const {
  const volatile BlockHeader* const block = GetBlock(ref, 0, 0, false, false);
  if (!block)
    return 0;
  return block->type_id.load(std::memory_order_relaxed);
}

bool PersistentMemoryAllocator::ChangeType(Reference ref,
                                           uint32_t to_type_id,
                                           uint32_t from_type_id,
                                           bool clear) {
  DCHECK(!readonly_);
  volatile BlockHeader* const block = GetBlock(ref, 0, 0, false, false);
  if (!block)
    return false;

  // "Strong" exchanges are used below because there is no loop that can retry
  // in the wake of spurious failures possible with "weak" exchanges. It is,
  // in aggregate, an "acquire-release" operation so no memory accesses can be
  // reordered either before or after this method (since changes based on type
  // could happen on either side).

  if (clear) {
    // If clearing the memory, first change it to the "transitioning" type so
    // there can be no confusion by other threads. After the memory is cleared,
    // it can be changed to its final type.
    if (!block->type_id.compare_exchange_strong(
            from_type_id, kTypeIdTransitioning, std::memory_order_acquire,
            std::memory_order_acquire)) {
      // Existing type wasn't what was expected: fail (with no changes)
      return false;
    }

    // Clear the memory in an atomic manner. Using "release" stores force
    // every write to be done after the ones before it. This is better than
    // using memset because (a) it supports "volatile" and (b) it creates a
    // reliable pattern upon which other threads may rely.
    volatile std::atomic<int>* data =
        reinterpret_cast<volatile std::atomic<int>*>(
            reinterpret_cast<volatile char*>(block) + sizeof(BlockHeader));
    const uint32_t words = (block->size - sizeof(BlockHeader)) / sizeof(int);
    DCHECK_EQ(0U, (block->size - sizeof(BlockHeader)) % sizeof(int));
    for (uint32_t i = 0; i < words; ++i) {
      data->store(0, std::memory_order_release);
      ++data;
    }

    // If the destination type is "transitioning" then skip the final exchange.
    if (to_type_id == kTypeIdTransitioning)
      return true;

    // Finish the change to the desired type.
    from_type_id = kTypeIdTransitioning;  // Exchange needs modifiable original.
    bool success = block->type_id.compare_exchange_strong(
        from_type_id, to_type_id, std::memory_order_release,
        std::memory_order_relaxed);
    DCHECK(success);  // Should never fail.
    return success;
  }

  // One step change to the new type. Will return false if the existing value
  // doesn't match what is expected.
  return block->type_id.compare_exchange_strong(from_type_id, to_type_id,
                                                std::memory_order_acq_rel,
                                                std::memory_order_acquire);
}

PersistentMemoryAllocator::Reference PersistentMemoryAllocator::Allocate(
    size_t req_size,
    uint32_t type_id) {
  Reference ref = AllocateImpl(req_size, type_id);
  if (ref) {
    // Success: Record this allocation in usage stats (if active).
    if (allocs_histogram_)
      allocs_histogram_->Add(static_cast<HistogramBase::Sample>(req_size));
  } else {
    // Failure: Record an allocation of zero for tracking.
    if (allocs_histogram_)
      allocs_histogram_->Add(0);
  }
  return ref;
}

PersistentMemoryAllocator::Reference PersistentMemoryAllocator::AllocateImpl(
    size_t req_size,
    uint32_t type_id) {
  DCHECK(!readonly_);

  // Validate req_size to ensure it won't overflow when used as 32-bit value.
  if (req_size > kSegmentMaxSize - sizeof(BlockHeader)) {
    NOTREACHED();
    return kReferenceNull;
  }

  // Round up the requested size, plus header, to the next allocation alignment.
  uint32_t size = static_cast<uint32_t>(req_size + sizeof(BlockHeader));
  size = (size + (kAllocAlignment - 1)) & ~(kAllocAlignment - 1);
  if (size <= sizeof(BlockHeader) || size > mem_page_) {
    NOTREACHED();
    return kReferenceNull;
  }

  // Get the current start of unallocated memory. Other threads may
  // update this at any time and cause us to retry these operations.
  // This value should be treated as "const" to avoid confusion through
  // the code below but recognize that any failed compare-exchange operation
  // involving it will cause it to be loaded with a more recent value. The
  // code should either exit or restart the loop in that case.
  /* const */ uint32_t freeptr =
      shared_meta()->freeptr.load(std::memory_order_acquire);

  // Allocation is lockless so we do all our caculation and then, if saving
  // indicates a change has occurred since we started, scrap everything and
  // start over.
  for (;;) {
    if (IsCorrupt())
      return kReferenceNull;

    if (freeptr + size > mem_size_) {
      SetFlag(&shared_meta()->flags, kFlagFull);
      return kReferenceNull;
    }

    // Get pointer to the "free" block. If something has been allocated since
    // the load of freeptr above, it is still safe as nothing will be written
    // to that location until after the compare-exchange below.
    volatile BlockHeader* const block = GetBlock(freeptr, 0, 0, false, true);
    if (!block) {
      SetCorrupt();
      return kReferenceNull;
    }

    // An allocation cannot cross page boundaries. If it would, create a
    // "wasted" block and begin again at the top of the next page. This
    // area could just be left empty but we fill in the block header just
    // for completeness sake.
    const uint32_t page_free = mem_page_ - freeptr % mem_page_;
    if (size > page_free) {
      if (page_free <= sizeof(BlockHeader)) {
        SetCorrupt();
        return kReferenceNull;
      }
      const uint32_t new_freeptr = freeptr + page_free;
      if (shared_meta()->freeptr.compare_exchange_strong(
              freeptr, new_freeptr, std::memory_order_acq_rel,
              std::memory_order_acquire)) {
        block->size = page_free;
        block->cookie = kBlockCookieWasted;
      }
      continue;
    }

    // Don't leave a slice at the end of a page too small for anything. This
    // can result in an allocation up to two alignment-sizes greater than the
    // minimum required by requested-size + header + alignment.
    if (page_free - size < sizeof(BlockHeader) + kAllocAlignment)
      size = page_free;

    const uint32_t new_freeptr = freeptr + size;
    if (new_freeptr > mem_size_) {
      SetCorrupt();
      return kReferenceNull;
    }

    // Save our work. Try again if another thread has completed an allocation
    // while we were processing. A "weak" exchange would be permissable here
    // because the code will just loop and try again but the above processing
    // is significant so make the extra effort of a "strong" exchange.
    if (!shared_meta()->freeptr.compare_exchange_strong(
            freeptr, new_freeptr, std::memory_order_acq_rel,
            std::memory_order_acquire)) {
      continue;
    }

    // Given that all memory was zeroed before ever being given to an instance
    // of this class and given that we only allocate in a monotomic fashion
    // going forward, it must be that the newly allocated block is completely
    // full of zeros. If we find anything in the block header that is NOT a
    // zero then something must have previously run amuck through memory,
    // writing beyond the allocated space and into unallocated space.
    if (block->size != 0 ||
        block->cookie != kBlockCookieFree ||
        block->type_id.load(std::memory_order_relaxed) != 0 ||
        block->next.load(std::memory_order_relaxed) != 0) {
      SetCorrupt();
      return kReferenceNull;
    }

    // Make sure the memory exists by writing to the first byte of every memory
    // page it touches beyond the one containing the block header itself.
    // As the underlying storage is often memory mapped from disk or shared
    // space, sometimes things go wrong and those address don't actually exist
    // leading to a SIGBUS (or Windows equivalent) at some arbitrary location
    // in the code. This should concentrate all those failures into this
    // location for easy tracking and, eventually, proper handling.
    volatile char* mem_end = reinterpret_cast<volatile char*>(block) + size;
    volatile char* mem_begin = reinterpret_cast<volatile char*>(
        (reinterpret_cast<uintptr_t>(block) + sizeof(BlockHeader) +
         (vm_page_size_ - 1)) &
        ~static_cast<uintptr_t>(vm_page_size_ - 1));
    for (volatile char* memory = mem_begin; memory < mem_end;
         memory += vm_page_size_) {
      // It's required that a memory segment start as all zeros and thus the
      // newly allocated block is all zeros at this point. Thus, writing a
      // zero to it allows testing that the memory exists without actually
      // changing its contents. The compiler doesn't know about the requirement
      // and so cannot optimize-away these writes.
      *memory = 0;
    }

    // Load information into the block header. There is no "release" of the
    // data here because this memory can, currently, be seen only by the thread
    // performing the allocation. When it comes time to share this, the thread
    // will call MakeIterable() which does the release operation.
    block->size = size;
    block->cookie = kBlockCookieAllocated;
    block->type_id.store(type_id, std::memory_order_relaxed);
    return freeptr;
  }
}

void PersistentMemoryAllocator::GetMemoryInfo(MemoryInfo* meminfo) const {
  uint32_t remaining = std::max(
      mem_size_ - shared_meta()->freeptr.load(std::memory_order_relaxed),
      (uint32_t)sizeof(BlockHeader));
  meminfo->total = mem_size_;
  meminfo->free = remaining - sizeof(BlockHeader);
}

void PersistentMemoryAllocator::MakeIterable(Reference ref) {
  DCHECK(!readonly_);
  if (IsCorrupt())
    return;
  volatile BlockHeader* block = GetBlock(ref, 0, 0, false, false);
  if (!block)  // invalid reference
    return;
  if (block->next.load(std::memory_order_acquire) != 0)  // Already iterable.
    return;
  block->next.store(kReferenceQueue, std::memory_order_release);  // New tail.

  // Try to add this block to the tail of the queue. May take multiple tries.
  // If so, tail will be automatically updated with a more recent value during
  // compare-exchange operations.
  uint32_t tail = shared_meta()->tailptr.load(std::memory_order_acquire);
  for (;;) {
    // Acquire the current tail-pointer released by previous call to this
    // method and validate it.
    block = GetBlock(tail, 0, 0, true, false);
    if (!block) {
      SetCorrupt();
      return;
    }

    // Try to insert the block at the tail of the queue. The tail node always
    // has an existing value of kReferenceQueue; if that is somehow not the
    // existing value then another thread has acted in the meantime. A "strong"
    // exchange is necessary so the "else" block does not get executed when
    // that is not actually the case (which can happen with a "weak" exchange).
    uint32_t next = kReferenceQueue;  // Will get replaced with existing value.
    if (block->next.compare_exchange_strong(next, ref,
                                            std::memory_order_acq_rel,
                                            std::memory_order_acquire)) {
      // Update the tail pointer to the new offset. If the "else" clause did
      // not exist, then this could be a simple Release_Store to set the new
      // value but because it does, it's possible that other threads could add
      // one or more nodes at the tail before reaching this point. We don't
      // have to check the return value because it either operates correctly
      // or the exact same operation has already been done (by the "else"
      // clause) on some other thread.
      shared_meta()->tailptr.compare_exchange_strong(tail, ref,
                                                     std::memory_order_release,
                                                     std::memory_order_relaxed);
      return;
    }
    // In the unlikely case that a thread crashed or was killed between the
    // update of "next" and the update of "tailptr", it is necessary to
    // perform the operation that would have been done. There's no explicit
    // check for crash/kill which means that this operation may also happen
    // even when the other thread is in perfect working order which is what
    // necessitates the CompareAndSwap above.
    shared_meta()->tailptr.compare_exchange_strong(
        tail, next, std::memory_order_acq_rel, std::memory_order_acquire);
  }
}

// The "corrupted" state is held both locally and globally (shared). The
// shared flag can't be trusted since a malicious actor could overwrite it.
// Because corruption can be detected during read-only operations such as
// iteration, this method may be called by other "const" methods. In this
// case, it's safe to discard the constness and modify the local flag and
// maybe even the shared flag if the underlying data isn't actually read-only.
void PersistentMemoryAllocator::SetCorrupt() const {
  if (!corrupt_.load(std::memory_order_relaxed) &&
      !CheckFlag(
          const_cast<volatile std::atomic<uint32_t>*>(&shared_meta()->flags),
          kFlagCorrupt)) {
    LOG(ERROR) << "Corruption detected in shared-memory segment.";
    RecordError(kMemoryIsCorrupt);
  }

  corrupt_.store(true, std::memory_order_relaxed);
  if (!readonly_) {
    SetFlag(const_cast<volatile std::atomic<uint32_t>*>(&shared_meta()->flags),
            kFlagCorrupt);
  }
}

bool PersistentMemoryAllocator::IsCorrupt() const {
  if (corrupt_.load(std::memory_order_relaxed) ||
      CheckFlag(&shared_meta()->flags, kFlagCorrupt)) {
    SetCorrupt();  // Make sure all indicators are set.
    return true;
  }
  return false;
}

bool PersistentMemoryAllocator::IsFull() const {
  return CheckFlag(&shared_meta()->flags, kFlagFull);
}

// Dereference a block |ref| and ensure that it's valid for the desired
// |type_id| and |size|. |special| indicates that we may try to access block
// headers not available to callers but still accessed by this module. By
// having internal dereferences go through this same function, the allocator
// is hardened against corruption.
const volatile PersistentMemoryAllocator::BlockHeader*
PersistentMemoryAllocator::GetBlock(Reference ref, uint32_t type_id,
                                    uint32_t size, bool queue_ok,
                                    bool free_ok) const {
  // Handle special cases.
  if (ref == kReferenceQueue && queue_ok)
    return reinterpret_cast<const volatile BlockHeader*>(mem_base_ + ref);

  // Validation of parameters.
  if (ref < sizeof(SharedMetadata))
    return nullptr;
  if (ref % kAllocAlignment != 0)
    return nullptr;
  size += sizeof(BlockHeader);
  if (ref + size > mem_size_)
    return nullptr;

  // Validation of referenced block-header.
  if (!free_ok) {
    const volatile BlockHeader* const block =
        reinterpret_cast<volatile BlockHeader*>(mem_base_ + ref);
    if (block->cookie != kBlockCookieAllocated)
      return nullptr;
    if (block->size < size)
      return nullptr;
    if (ref + block->size > mem_size_)
      return nullptr;
    if (type_id != 0 &&
        block->type_id.load(std::memory_order_relaxed) != type_id) {
      return nullptr;
    }
  }

  // Return pointer to block data.
  return reinterpret_cast<const volatile BlockHeader*>(mem_base_ + ref);
}

void PersistentMemoryAllocator::FlushPartial(size_t length, bool sync) {
  // Generally there is nothing to do as every write is done through volatile
  // memory with atomic instructions to guarantee consistency. This (virtual)
  // method exists so that derivced classes can do special things, such as
  // tell the OS to write changes to disk now rather than when convenient.
}

void PersistentMemoryAllocator::RecordError(int error) const {
  if (errors_histogram_)
    errors_histogram_->Add(error);
}

const volatile void* PersistentMemoryAllocator::GetBlockData(
    Reference ref,
    uint32_t type_id,
    uint32_t size) const {
  DCHECK(size > 0);
  const volatile BlockHeader* block =
      GetBlock(ref, type_id, size, false, false);
  if (!block)
    return nullptr;
  return reinterpret_cast<const volatile char*>(block) + sizeof(BlockHeader);
}

void PersistentMemoryAllocator::UpdateTrackingHistograms() {
  DCHECK(!readonly_);
  if (used_histogram_) {
    MemoryInfo meminfo;
    GetMemoryInfo(&meminfo);
    HistogramBase::Sample used_percent = static_cast<HistogramBase::Sample>(
        ((meminfo.total - meminfo.free) * 100ULL / meminfo.total));
    used_histogram_->Add(used_percent);
  }
}


//----- LocalPersistentMemoryAllocator -----------------------------------------

LocalPersistentMemoryAllocator::LocalPersistentMemoryAllocator(
    size_t size,
    uint64_t id,
    base::StringPiece name)
    : PersistentMemoryAllocator(AllocateLocalMemory(size),
                                size, 0, id, name, false) {}

LocalPersistentMemoryAllocator::~LocalPersistentMemoryAllocator() {
  DeallocateLocalMemory(const_cast<char*>(mem_base_), mem_size_, mem_type_);
}

// static
PersistentMemoryAllocator::Memory
LocalPersistentMemoryAllocator::AllocateLocalMemory(size_t size) {
  void* address;

#if defined(OS_WIN)
  address =
      ::VirtualAlloc(nullptr, size, MEM_RESERVE | MEM_COMMIT, PAGE_READWRITE);
  if (address)
    return Memory(address, MEM_VIRTUAL);
  UmaHistogramSparse("UMA.LocalPersistentMemoryAllocator.Failures.Win",
                     ::GetLastError());
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  // MAP_ANON is deprecated on Linux but MAP_ANONYMOUS is not universal on Mac.
  // MAP_SHARED is not available on Linux <2.4 but required on Mac.
  address = ::mmap(nullptr, size, PROT_READ | PROT_WRITE,
                   MAP_ANON | MAP_SHARED, -1, 0);
  if (address != MAP_FAILED)
    return Memory(address, MEM_VIRTUAL);
  UmaHistogramSparse("UMA.LocalPersistentMemoryAllocator.Failures.Posix",
                     errno);
#else
#error This architecture is not (yet) supported.
#endif

  // As a last resort, just allocate the memory from the heap. This will
  // achieve the same basic result but the acquired memory has to be
  // explicitly zeroed and thus realized immediately (i.e. all pages are
  // added to the process now istead of only when first accessed).
  address = malloc(size);
  DPCHECK(address);
  memset(address, 0, size);
  return Memory(address, MEM_MALLOC);
}

// static
void LocalPersistentMemoryAllocator::DeallocateLocalMemory(void* memory,
                                                           size_t size,
                                                           MemoryType type) {
  if (type == MEM_MALLOC) {
    free(memory);
    return;
  }

  DCHECK_EQ(MEM_VIRTUAL, type);
#if defined(OS_WIN)
  BOOL success = ::VirtualFree(memory, 0, MEM_DECOMMIT);
  DCHECK(success);
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  int result = ::munmap(memory, size);
  DCHECK_EQ(0, result);
#else
#error This architecture is not (yet) supported.
#endif
}

//----- WritableSharedPersistentMemoryAllocator --------------------------------

WritableSharedPersistentMemoryAllocator::
    WritableSharedPersistentMemoryAllocator(
        base::WritableSharedMemoryMapping memory,
        uint64_t id,
        base::StringPiece name)
    : PersistentMemoryAllocator(Memory(memory.memory(), MEM_SHARED),
                                memory.size(),
                                0,
                                id,
                                name,
                                false),
      shared_memory_(std::move(memory)) {}

WritableSharedPersistentMemoryAllocator::
    ~WritableSharedPersistentMemoryAllocator() = default;

// static
bool WritableSharedPersistentMemoryAllocator::IsSharedMemoryAcceptable(
    const base::WritableSharedMemoryMapping& memory) {
  return IsMemoryAcceptable(memory.memory(), memory.size(), 0, false);
}

//----- ReadOnlySharedPersistentMemoryAllocator --------------------------------

ReadOnlySharedPersistentMemoryAllocator::
    ReadOnlySharedPersistentMemoryAllocator(
        base::ReadOnlySharedMemoryMapping memory,
        uint64_t id,
        base::StringPiece name)
    : PersistentMemoryAllocator(
          Memory(const_cast<void*>(memory.memory()), MEM_SHARED),
          memory.size(),
          0,
          id,
          name,
          true),
      shared_memory_(std::move(memory)) {}

ReadOnlySharedPersistentMemoryAllocator::
    ~ReadOnlySharedPersistentMemoryAllocator() = default;

// static
bool ReadOnlySharedPersistentMemoryAllocator::IsSharedMemoryAcceptable(
    const base::ReadOnlySharedMemoryMapping& memory) {
  return IsMemoryAcceptable(memory.memory(), memory.size(), 0, true);
}

#if !defined(OS_NACL)
//----- FilePersistentMemoryAllocator ------------------------------------------

FilePersistentMemoryAllocator::FilePersistentMemoryAllocator(
    std::unique_ptr<MemoryMappedFile> file,
    size_t max_size,
    uint64_t id,
    base::StringPiece name,
    bool read_only)
    : PersistentMemoryAllocator(
          Memory(const_cast<uint8_t*>(file->data()), MEM_FILE),
          max_size != 0 ? max_size : file->length(),
          0,
          id,
          name,
          read_only),
      mapped_file_(std::move(file)) {}

FilePersistentMemoryAllocator::~FilePersistentMemoryAllocator() = default;

// static
bool FilePersistentMemoryAllocator::IsFileAcceptable(
    const MemoryMappedFile& file,
    bool read_only) {
  return IsMemoryAcceptable(file.data(), file.length(), 0, read_only);
}

void FilePersistentMemoryAllocator::Cache() {
  // Since this method is expected to load data from permanent storage
  // into memory, blocking I/O may occur.
  base::ScopedBlockingCall scoped_blocking_call(FROM_HERE,
                                                base::BlockingType::MAY_BLOCK);

  // Calculate begin/end addresses so that the first byte of every page
  // in that range can be read. Keep within the used space. The |volatile|
  // keyword makes it so the compiler can't make assumptions about what is
  // in a given memory location and thus possibly avoid the read.
  const volatile char* mem_end = mem_base_ + used();
  const volatile char* mem_begin = mem_base_;

  // Iterate over the memory a page at a time, reading the first byte of
  // every page. The values are added to a |total| so that the compiler
  // can't omit the read.
  int total = 0;
  for (const volatile char* memory = mem_begin; memory < mem_end;
       memory += vm_page_size_) {
    total += *memory;
  }

  // Tell the compiler that |total| is used so that it can't optimize away
  // the memory accesses above.
  debug::Alias(&total);
}

void FilePersistentMemoryAllocator::FlushPartial(size_t length, bool sync) {
  if (IsReadonly())
    return;

  base::Optional<base::ScopedBlockingCall> scoped_blocking_call;
  if (sync)
    scoped_blocking_call.emplace(FROM_HERE, base::BlockingType::MAY_BLOCK);

#if defined(OS_WIN)
  // Windows doesn't support asynchronous flush.
  scoped_blocking_call.emplace(FROM_HERE, base::BlockingType::MAY_BLOCK);
  BOOL success = ::FlushViewOfFile(data(), length);
  DPCHECK(success);
#elif defined(OS_MACOSX)
  // On OSX, "invalidate" removes all cached pages, forcing a re-read from
  // disk. That's not applicable to "flush" so omit it.
  int result =
      ::msync(const_cast<void*>(data()), length, sync ? MS_SYNC : MS_ASYNC);
  DCHECK_NE(EINVAL, result);
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  // On POSIX, "invalidate" forces _other_ processes to recognize what has
  // been written to disk and so is applicable to "flush".
  int result = ::msync(const_cast<void*>(data()), length,
                       MS_INVALIDATE | (sync ? MS_SYNC : MS_ASYNC));
  DCHECK_NE(EINVAL, result);
#else
#error Unsupported OS.
#endif
}
#endif  // !defined(OS_NACL)

//----- DelayedPersistentAllocation --------------------------------------------

// Forwarding constructors.
DelayedPersistentAllocation::DelayedPersistentAllocation(
    PersistentMemoryAllocator* allocator,
    subtle::Atomic32* ref,
    uint32_t type,
    size_t size,
    bool make_iterable)
    : DelayedPersistentAllocation(
          allocator,
          reinterpret_cast<std::atomic<Reference>*>(ref),
          type,
          size,
          0,
          make_iterable) {}

DelayedPersistentAllocation::DelayedPersistentAllocation(
    PersistentMemoryAllocator* allocator,
    subtle::Atomic32* ref,
    uint32_t type,
    size_t size,
    size_t offset,
    bool make_iterable)
    : DelayedPersistentAllocation(
          allocator,
          reinterpret_cast<std::atomic<Reference>*>(ref),
          type,
          size,
          offset,
          make_iterable) {}

DelayedPersistentAllocation::DelayedPersistentAllocation(
    PersistentMemoryAllocator* allocator,
    std::atomic<Reference>* ref,
    uint32_t type,
    size_t size,
    bool make_iterable)
    : DelayedPersistentAllocation(allocator,
                                  ref,
                                  type,
                                  size,
                                  0,
                                  make_iterable) {}

// Real constructor.
DelayedPersistentAllocation::DelayedPersistentAllocation(
    PersistentMemoryAllocator* allocator,
    std::atomic<Reference>* ref,
    uint32_t type,
    size_t size,
    size_t offset,
    bool make_iterable)
    : allocator_(allocator),
      type_(type),
      size_(checked_cast<uint32_t>(size)),
      offset_(checked_cast<uint32_t>(offset)),
      make_iterable_(make_iterable),
      reference_(ref) {
  DCHECK(allocator_);
  DCHECK_NE(0U, type_);
  DCHECK_LT(0U, size_);
  DCHECK(reference_);
}

DelayedPersistentAllocation::~DelayedPersistentAllocation() = default;

void* DelayedPersistentAllocation::Get() const {
  // Relaxed operations are acceptable here because it's not protecting the
  // contents of the allocation in any way.
  Reference ref = reference_->load(std::memory_order_acquire);
  if (!ref) {
    ref = allocator_->Allocate(size_, type_);
    if (!ref)
      return nullptr;

    // Store the new reference in its proper location using compare-and-swap.
    // Use a "strong" exchange to ensure no false-negatives since the operation
    // cannot be retried.
    Reference existing = 0;  // Must be mutable; receives actual value.
    if (reference_->compare_exchange_strong(existing, ref,
                                            std::memory_order_release,
                                            std::memory_order_relaxed)) {
      if (make_iterable_)
        allocator_->MakeIterable(ref);
    } else {
      // Failure indicates that something else has raced ahead, performed the
      // allocation, and stored its reference. Purge the allocation that was
      // just done and use the other one instead.
      DCHECK_EQ(type_, allocator_->GetType(existing));
      DCHECK_LE(size_, allocator_->GetAllocSize(existing));
      allocator_->ChangeType(ref, 0, type_, /*clear=*/false);
      ref = existing;
    }
  }

  char* mem = allocator_->GetAsArray<char>(ref, type_, size_);
  if (!mem) {
    // This should never happen but be tolerant if it does as corruption from
    // the outside is something to guard against.
    NOTREACHED();
    return nullptr;
  }
  return mem + offset_;
}

}  // namespace base

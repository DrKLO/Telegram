// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/threading/thread_local_storage.h"

#include "base/atomicops.h"
#include "base/compiler_specific.h"
#include "base/logging.h"
#include "base/no_destructor.h"
#include "base/synchronization/lock.h"
#include "build/build_config.h"

using base::internal::PlatformThreadLocalStorage;

// Chrome Thread Local Storage (TLS)
//
// This TLS system allows Chrome to use a single OS level TLS slot process-wide,
// and allows us to control the slot limits instead of being at the mercy of the
// platform. To do this, Chrome TLS replicates an array commonly found in the OS
// thread metadata.
//
// Overview:
//
// OS TLS Slots       Per-Thread                 Per-Process Global
//     ...
//     []             Chrome TLS Array           Chrome TLS Metadata
//     [] ----------> [][][][][ ][][][][]        [][][][][ ][][][][]
//     []                      |                          |
//     ...                     V                          V
//                      Metadata Version           Slot Information
//                         Your Data!
//
// Using a single OS TLS slot, Chrome TLS allocates an array on demand for the
// lifetime of each thread that requests Chrome TLS data. Each per-thread TLS
// array matches the length of the per-process global metadata array.
//
// A per-process global TLS metadata array tracks information about each item in
// the per-thread array:
//   * Status: Tracks if the slot is allocated or free to assign.
//   * Destructor: An optional destructor to call on thread destruction for that
//                 specific slot.
//   * Version: Tracks the current version of the TLS slot. Each TLS slot
//              allocation is associated with a unique version number.
//
//              Most OS TLS APIs guarantee that a newly allocated TLS slot is
//              initialized to 0 for all threads. The Chrome TLS system provides
//              this guarantee by tracking the version for each TLS slot here
//              on each per-thread Chrome TLS array entry. Threads that access
//              a slot with a mismatched version will receive 0 as their value.
//              The metadata version is incremented when the client frees a
//              slot. The per-thread metadata version is updated when a client
//              writes to the slot. This scheme allows for constant time
//              invalidation and avoids the need to iterate through each Chrome
//              TLS array to mark the slot as zero.
//
// Just like an OS TLS API, clients of the Chrome TLS are responsible for
// managing any necessary lifetime of the data in their slots. The only
// convenience provided is automatic destruction when a thread ends. If a client
// frees a slot, that client is responsible for destroying the data in the slot.

namespace {
// In order to make TLS destructors work, we need to keep around a function
// pointer to the destructor for each slot. We keep this array of pointers in a
// global (static) array.
// We use the single OS-level TLS slot (giving us one pointer per thread) to
// hold a pointer to a per-thread array (table) of slots that we allocate to
// Chromium consumers.

// g_native_tls_key is the one native TLS that we use. It stores our table.
base::subtle::Atomic32 g_native_tls_key =
    PlatformThreadLocalStorage::TLS_KEY_OUT_OF_INDEXES;

// The OS TLS slot has the following states. The TLS slot's lower 2 bits contain
// the state, the upper bits the TlsVectorEntry*.
//   * kUninitialized: Any call to Slot::Get()/Set() will create the base
//     per-thread TLS state. kUninitialized must be null.
//   * kInUse: value has been created and is in use.
//   * kDestroying: Set when the thread is exiting prior to deleting any of the
//     values stored in the TlsVectorEntry*. This state is necessary so that
//     sequence/task checks won't be done while in the process of deleting the
//     tls entries (see comments in SequenceCheckerImpl for more details).
//   * kDestroyed: All of the values in the vector have been deallocated and
//     the TlsVectorEntry has been deleted.
//
// Final States:
//   * Windows: kDestroyed. Windows does not iterate through the OS TLS to clean
//     up the values.
//   * POSIX: kUninitialized. POSIX iterates through TLS until all slots contain
//     nullptr.
//
// More details on this design:
//   We need some type of thread-local state to indicate that the TLS system has
//   been destroyed. To do so, we leverage the multi-pass nature of destruction
//   of pthread_key.
//
//    a) After destruction of TLS system, we set the pthread_key to a sentinel
//       kDestroyed.
//    b) All calls to Slot::Get() DCHECK that the state is not kDestroyed, and
//       any system which might potentially invoke Slot::Get() after destruction
//       of TLS must check ThreadLocalStorage::ThreadIsBeingDestroyed().
//    c) After a full pass of the pthread_keys, on the next invocation of
//       ConstructTlsVector(), we'll then set the key to nullptr.
//    d) At this stage, the TLS system is back in its uninitialized state.
//    e) If in the second pass of destruction of pthread_keys something were to
//       re-initialize TLS [this should never happen! Since the only code which
//       uses Chrome TLS is Chrome controlled, we should really be striving for
//       single-pass destruction], then TLS will be re-initialized and then go
//       through the 2-pass destruction system again. Everything should just
//       work (TM).

// The state of the tls-entry.
enum class TlsVectorState {
  kUninitialized = 0,

  // In the process of destroying the entries in the vector.
  kDestroying,

  // All of the entries and the vector has been destroyed.
  kDestroyed,

  // The vector has been initialized and is in use.
  kInUse,

  kMaxValue = kInUse
};

// Bit-mask used to store TlsVectorState.
constexpr uintptr_t kVectorStateBitMask = 3;
static_assert(static_cast<int>(TlsVectorState::kMaxValue) <=
                  kVectorStateBitMask,
              "number of states must fit in header");
static_assert(static_cast<int>(TlsVectorState::kUninitialized) == 0,
              "kUninitialized must be null");

// The maximum number of slots in our thread local storage stack.
constexpr int kThreadLocalStorageSize = 256;

enum TlsStatus {
  FREE,
  IN_USE,
};

struct TlsMetadata {
  TlsStatus status;
  base::ThreadLocalStorage::TLSDestructorFunc destructor;
  // Incremented every time a slot is reused. Used to detect reuse of slots.
  uint32_t version;
};

struct TlsVectorEntry {
  void* data;
  uint32_t version;
};

// This lock isn't needed until after we've constructed the per-thread TLS
// vector, so it's safe to use.
base::Lock* GetTLSMetadataLock() {
  static auto* lock = new base::Lock();
  return lock;
}
TlsMetadata g_tls_metadata[kThreadLocalStorageSize];
size_t g_last_assigned_slot = 0;

// The maximum number of times to try to clear slots by calling destructors.
// Use pthread naming convention for clarity.
constexpr int kMaxDestructorIterations = kThreadLocalStorageSize;

// Sets the value and state of the vector.
void SetTlsVectorValue(PlatformThreadLocalStorage::TLSKey key,
                       TlsVectorEntry* tls_data,
                       TlsVectorState state) {
  DCHECK(tls_data || (state == TlsVectorState::kUninitialized) ||
         (state == TlsVectorState::kDestroyed));
  PlatformThreadLocalStorage::SetTLSValue(
      key, reinterpret_cast<void*>(reinterpret_cast<uintptr_t>(tls_data) |
                                   static_cast<uintptr_t>(state)));
}

// Returns the tls vector and current state from the raw tls value.
TlsVectorState GetTlsVectorStateAndValue(void* tls_value,
                                         TlsVectorEntry** entry = nullptr) {
  if (entry) {
    *entry = reinterpret_cast<TlsVectorEntry*>(
        reinterpret_cast<uintptr_t>(tls_value) & ~kVectorStateBitMask);
  }
  return static_cast<TlsVectorState>(reinterpret_cast<uintptr_t>(tls_value) &
                                     kVectorStateBitMask);
}

// Returns the tls vector and state using the tls key.
TlsVectorState GetTlsVectorStateAndValue(PlatformThreadLocalStorage::TLSKey key,
                                         TlsVectorEntry** entry = nullptr) {
  return GetTlsVectorStateAndValue(PlatformThreadLocalStorage::GetTLSValue(key),
                                   entry);
}

// This function is called to initialize our entire Chromium TLS system.
// It may be called very early, and we need to complete most all of the setup
// (initialization) before calling *any* memory allocator functions, which may
// recursively depend on this initialization.
// As a result, we use Atomics, and avoid anything (like a singleton) that might
// require memory allocations.
TlsVectorEntry* ConstructTlsVector() {
  PlatformThreadLocalStorage::TLSKey key =
      base::subtle::NoBarrier_Load(&g_native_tls_key);
  if (key == PlatformThreadLocalStorage::TLS_KEY_OUT_OF_INDEXES) {
    CHECK(PlatformThreadLocalStorage::AllocTLS(&key));

    // The TLS_KEY_OUT_OF_INDEXES is used to find out whether the key is set or
    // not in NoBarrier_CompareAndSwap, but Posix doesn't have invalid key, we
    // define an almost impossible value be it.
    // If we really get TLS_KEY_OUT_OF_INDEXES as value of key, just alloc
    // another TLS slot.
    if (key == PlatformThreadLocalStorage::TLS_KEY_OUT_OF_INDEXES) {
      PlatformThreadLocalStorage::TLSKey tmp = key;
      CHECK(PlatformThreadLocalStorage::AllocTLS(&key) &&
            key != PlatformThreadLocalStorage::TLS_KEY_OUT_OF_INDEXES);
      PlatformThreadLocalStorage::FreeTLS(tmp);
    }
    // Atomically test-and-set the tls_key. If the key is
    // TLS_KEY_OUT_OF_INDEXES, go ahead and set it. Otherwise, do nothing, as
    // another thread already did our dirty work.
    if (PlatformThreadLocalStorage::TLS_KEY_OUT_OF_INDEXES !=
        static_cast<PlatformThreadLocalStorage::TLSKey>(
            base::subtle::NoBarrier_CompareAndSwap(
                &g_native_tls_key,
                PlatformThreadLocalStorage::TLS_KEY_OUT_OF_INDEXES, key))) {
      // We've been shortcut. Another thread replaced g_native_tls_key first so
      // we need to destroy our index and use the one the other thread got
      // first.
      PlatformThreadLocalStorage::FreeTLS(key);
      key = base::subtle::NoBarrier_Load(&g_native_tls_key);
    }
  }
  CHECK_EQ(GetTlsVectorStateAndValue(key), TlsVectorState::kUninitialized);

  // Some allocators, such as TCMalloc, make use of thread local storage. As a
  // result, any attempt to call new (or malloc) will lazily cause such a system
  // to initialize, which will include registering for a TLS key. If we are not
  // careful here, then that request to create a key will call new back, and
  // we'll have an infinite loop. We avoid that as follows: Use a stack
  // allocated vector, so that we don't have dependence on our allocator until
  // our service is in place. (i.e., don't even call new until after we're
  // setup)
  TlsVectorEntry stack_allocated_tls_data[kThreadLocalStorageSize];
  memset(stack_allocated_tls_data, 0, sizeof(stack_allocated_tls_data));
  // Ensure that any rentrant calls change the temp version.
  SetTlsVectorValue(key, stack_allocated_tls_data, TlsVectorState::kInUse);

  // Allocate an array to store our data.
  TlsVectorEntry* tls_data = new TlsVectorEntry[kThreadLocalStorageSize];
  memcpy(tls_data, stack_allocated_tls_data, sizeof(stack_allocated_tls_data));
  SetTlsVectorValue(key, tls_data, TlsVectorState::kInUse);
  return tls_data;
}

void OnThreadExitInternal(TlsVectorEntry* tls_data) {
  DCHECK(tls_data);
  // Some allocators, such as TCMalloc, use TLS. As a result, when a thread
  // terminates, one of the destructor calls we make may be to shut down an
  // allocator. We have to be careful that after we've shutdown all of the known
  // destructors (perchance including an allocator), that we don't call the
  // allocator and cause it to resurrect itself (with no possibly destructor
  // call to follow). We handle this problem as follows: Switch to using a stack
  // allocated vector, so that we don't have dependence on our allocator after
  // we have called all g_tls_metadata destructors. (i.e., don't even call
  // delete[] after we're done with destructors.)
  TlsVectorEntry stack_allocated_tls_data[kThreadLocalStorageSize];
  memcpy(stack_allocated_tls_data, tls_data, sizeof(stack_allocated_tls_data));
  // Ensure that any re-entrant calls change the temp version.
  PlatformThreadLocalStorage::TLSKey key =
      base::subtle::NoBarrier_Load(&g_native_tls_key);
  SetTlsVectorValue(key, stack_allocated_tls_data, TlsVectorState::kDestroying);
  delete[] tls_data;  // Our last dependence on an allocator.

  // Snapshot the TLS Metadata so we don't have to lock on every access.
  TlsMetadata tls_metadata[kThreadLocalStorageSize];
  {
    base::AutoLock auto_lock(*GetTLSMetadataLock());
    memcpy(tls_metadata, g_tls_metadata, sizeof(g_tls_metadata));
  }

  int remaining_attempts = kMaxDestructorIterations;
  bool need_to_scan_destructors = true;
  while (need_to_scan_destructors) {
    need_to_scan_destructors = false;
    // Try to destroy the first-created-slot (which is slot 1) in our last
    // destructor call. That user was able to function, and define a slot with
    // no other services running, so perhaps it is a basic service (like an
    // allocator) and should also be destroyed last. If we get the order wrong,
    // then we'll iterate several more times, so it is really not that critical
    // (but it might help).
    for (int slot = 0; slot < kThreadLocalStorageSize; ++slot) {
      void* tls_value = stack_allocated_tls_data[slot].data;
      if (!tls_value || tls_metadata[slot].status == TlsStatus::FREE ||
          stack_allocated_tls_data[slot].version != tls_metadata[slot].version)
        continue;

      base::ThreadLocalStorage::TLSDestructorFunc destructor =
          tls_metadata[slot].destructor;
      if (!destructor)
        continue;
      stack_allocated_tls_data[slot].data = nullptr;  // pre-clear the slot.
      destructor(tls_value);
      // Any destructor might have called a different service, which then set a
      // different slot to a non-null value. Hence we need to check the whole
      // vector again. This is a pthread standard.
      need_to_scan_destructors = true;
    }
    if (--remaining_attempts <= 0) {
      NOTREACHED();  // Destructors might not have been called.
      break;
    }
  }

  // Remove our stack allocated vector.
  SetTlsVectorValue(key, nullptr, TlsVectorState::kDestroyed);
}

}  // namespace

namespace base {

namespace internal {

#if defined(OS_WIN)
void PlatformThreadLocalStorage::OnThreadExit() {
  PlatformThreadLocalStorage::TLSKey key =
      base::subtle::NoBarrier_Load(&g_native_tls_key);
  if (key == PlatformThreadLocalStorage::TLS_KEY_OUT_OF_INDEXES)
    return;
  TlsVectorEntry* tls_vector = nullptr;
  const TlsVectorState state = GetTlsVectorStateAndValue(key, &tls_vector);

  // On Windows, thread destruction callbacks are only invoked once per module,
  // so there should be no way that this could be invoked twice.
  DCHECK_NE(state, TlsVectorState::kDestroyed);

  // Maybe we have never initialized TLS for this thread.
  if (state == TlsVectorState::kUninitialized)
    return;
  OnThreadExitInternal(tls_vector);
}
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
void PlatformThreadLocalStorage::OnThreadExit(void* value) {
  // On posix this function may be called twice. The first pass calls dtors and
  // sets state to kDestroyed. The second pass sets kDestroyed to
  // kUninitialized.
  TlsVectorEntry* tls_vector = nullptr;
  const TlsVectorState state = GetTlsVectorStateAndValue(value, &tls_vector);
  if (state == TlsVectorState::kDestroyed) {
    PlatformThreadLocalStorage::TLSKey key =
        base::subtle::NoBarrier_Load(&g_native_tls_key);
    SetTlsVectorValue(key, nullptr, TlsVectorState::kUninitialized);
    return;
  }

  OnThreadExitInternal(tls_vector);
}
#endif  // defined(OS_WIN)

}  // namespace internal

// static
bool ThreadLocalStorage::HasBeenDestroyed() {
  PlatformThreadLocalStorage::TLSKey key =
      base::subtle::NoBarrier_Load(&g_native_tls_key);
  if (key == PlatformThreadLocalStorage::TLS_KEY_OUT_OF_INDEXES)
    return false;
  const TlsVectorState state = GetTlsVectorStateAndValue(key);
  return state == TlsVectorState::kDestroying ||
         state == TlsVectorState::kDestroyed;
}

void ThreadLocalStorage::Slot::Initialize(TLSDestructorFunc destructor) {
  PlatformThreadLocalStorage::TLSKey key =
      base::subtle::NoBarrier_Load(&g_native_tls_key);
  if (key == PlatformThreadLocalStorage::TLS_KEY_OUT_OF_INDEXES ||
      GetTlsVectorStateAndValue(key) == TlsVectorState::kUninitialized) {
    ConstructTlsVector();
  }

  // Grab a new slot.
  {
    base::AutoLock auto_lock(*GetTLSMetadataLock());
    for (int i = 0; i < kThreadLocalStorageSize; ++i) {
      // Tracking the last assigned slot is an attempt to find the next
      // available slot within one iteration. Under normal usage, slots remain
      // in use for the lifetime of the process (otherwise before we reclaimed
      // slots, we would have run out of slots). This makes it highly likely the
      // next slot is going to be a free slot.
      size_t slot_candidate =
          (g_last_assigned_slot + 1 + i) % kThreadLocalStorageSize;
      if (g_tls_metadata[slot_candidate].status == TlsStatus::FREE) {
        g_tls_metadata[slot_candidate].status = TlsStatus::IN_USE;
        g_tls_metadata[slot_candidate].destructor = destructor;
        g_last_assigned_slot = slot_candidate;
        DCHECK_EQ(kInvalidSlotValue, slot_);
        slot_ = slot_candidate;
        version_ = g_tls_metadata[slot_candidate].version;
        break;
      }
    }
  }
  CHECK_NE(slot_, kInvalidSlotValue);
  CHECK_LT(slot_, kThreadLocalStorageSize);
}

void ThreadLocalStorage::Slot::Free() {
  DCHECK_NE(slot_, kInvalidSlotValue);
  DCHECK_LT(slot_, kThreadLocalStorageSize);
  {
    base::AutoLock auto_lock(*GetTLSMetadataLock());
    g_tls_metadata[slot_].status = TlsStatus::FREE;
    g_tls_metadata[slot_].destructor = nullptr;
    ++(g_tls_metadata[slot_].version);
  }
  slot_ = kInvalidSlotValue;
}

void* ThreadLocalStorage::Slot::Get() const {
  TlsVectorEntry* tls_data = nullptr;
  const TlsVectorState state = GetTlsVectorStateAndValue(
      base::subtle::NoBarrier_Load(&g_native_tls_key), &tls_data);
  DCHECK_NE(state, TlsVectorState::kDestroyed);
  if (!tls_data)
    return nullptr;
  DCHECK_NE(slot_, kInvalidSlotValue);
  DCHECK_LT(slot_, kThreadLocalStorageSize);
  // Version mismatches means this slot was previously freed.
  if (tls_data[slot_].version != version_)
    return nullptr;
  return tls_data[slot_].data;
}

void ThreadLocalStorage::Slot::Set(void* value) {
  TlsVectorEntry* tls_data = nullptr;
  const TlsVectorState state = GetTlsVectorStateAndValue(
      base::subtle::NoBarrier_Load(&g_native_tls_key), &tls_data);
  DCHECK_NE(state, TlsVectorState::kDestroyed);
  if (!tls_data) {
    if (!value)
      return;
    tls_data = ConstructTlsVector();
  }
  DCHECK_NE(slot_, kInvalidSlotValue);
  DCHECK_LT(slot_, kThreadLocalStorageSize);
  tls_data[slot_].data = value;
  tls_data[slot_].version = version_;
}

ThreadLocalStorage::Slot::Slot(TLSDestructorFunc destructor) {
  Initialize(destructor);
}

ThreadLocalStorage::Slot::~Slot() {
  Free();
}

}  // namespace base

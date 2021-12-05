// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_SEQUENCE_MANAGER_ATOMIC_FLAG_SET_H_
#define BASE_TASK_SEQUENCE_MANAGER_ATOMIC_FLAG_SET_H_

#include <atomic>
#include <memory>

#include "base/base_export.h"
#include "base/callback.h"
#include "base/task/sequence_manager/associated_thread_id.h"

namespace base {
namespace sequence_manager {
namespace internal {

// This class maintains a set of AtomicFlags which can be activated or
// deactivated at any time by any thread. When a flag is created a callback is
// specified and the RunActiveCallbacks method can be invoked to fire callbacks
// for all active flags. Creating releasing or destroying an AtomicFlag must be
// done on the associated thread, as must calling RunActiveCallbacks. This
// class is thread-affine.
class BASE_EXPORT AtomicFlagSet {
 protected:
  struct Group;

 public:
  explicit AtomicFlagSet(scoped_refptr<AssociatedThreadId> associated_thread);

  // AtomicFlags need to be released (or deleted) before this can be deleted.
  ~AtomicFlagSet();

  // This class is thread-affine in addition SetActive can be called
  // concurrently from any thread.
  class BASE_EXPORT AtomicFlag {
   public:
    AtomicFlag();

    // Automatically releases the AtomicFlag.
    ~AtomicFlag();

    AtomicFlag(const AtomicFlag&) = delete;
    AtomicFlag(AtomicFlag&& other);

    // Can be called on any thread. Marks whether the flag is active or not,
    // which controls whether RunActiveCallbacks() will fire the associated
    // callback or not. In the absence of external synchronization, the value
    // set by this call might not immediately be visible to a thread calling
    // RunActiveCallbacks(); the only guarantee is that a value set by this will
    // eventually be visible to other threads due to cache coherency. Release /
    // acquire semantics are used on the underlying atomic operations so if
    // RunActiveCallbacks sees the value set by a call to SetActive(), it will
    // also see the memory changes that happened prior to that SetActive() call.
    void SetActive(bool active);

    // Releases the flag. Must be called on the associated thread. SetActive
    // can't be called after this.
    void ReleaseAtomicFlag();

   private:
    friend AtomicFlagSet;

    AtomicFlag(AtomicFlagSet* outer, Group* element, size_t flag_bit);

    AtomicFlagSet* outer_ = nullptr;
    Group* group_ = nullptr;  // Null when AtomicFlag is invalid.
    size_t flag_bit_ = 0;  // This is 1 << index of this flag within the group.
  };

  // Adds a new flag to the set. The |callback| will be fired by
  // RunActiveCallbacks if the flag is active. Must be called on the associated
  // thread.
  AtomicFlag AddFlag(RepeatingClosure callback);

  // Runs the registered callback for all flags marked as active and atomically
  // resets all flags to inactive. Must be called on the associated thread.
  void RunActiveCallbacks() const;

 protected:
  Group* GetAllocListForTesting() const { return alloc_list_head_.get(); }

  Group* GetPartiallyFreeListForTesting() const {
    return partially_free_list_head_;
  }

  // Wraps a single std::atomic<size_t> which is shared by a number of
  // AtomicFlag's with one bit per flag.
  struct BASE_EXPORT Group {
    Group();
    ~Group();

    static constexpr int kNumFlags = sizeof(size_t) * 8;

    std::atomic<size_t> flags = {0};
    size_t allocated_flags = 0;
    RepeatingClosure flag_callbacks[kNumFlags];
    Group* prev = nullptr;
    std::unique_ptr<Group> next;
    Group* partially_free_list_prev = nullptr;
    Group* partially_free_list_next = nullptr;

    bool IsFull() const;

    bool IsEmpty() const;

    // Returns the index of the first unallocated flag. Must not be called when
    // all flags are set.
    int FindFirstUnallocatedFlag() const;

    // Computes the index of the |flag_callbacks| based on the number of leading
    // zero bits in |flag|.
    static int IndexOfFirstFlagSet(size_t flag);

   private:
    DISALLOW_COPY_AND_ASSIGN(Group);
  };

 private:
  void AddToAllocList(std::unique_ptr<Group> element);

  // This deletes |element|.
  void RemoveFromAllocList(Group* element);

  void AddToPartiallyFreeList(Group* element);

  // This does not delete |element|.
  void RemoveFromPartiallyFreeList(Group* element);

  scoped_refptr<AssociatedThreadId> associated_thread_;
  std::unique_ptr<Group> alloc_list_head_;
  Group* partially_free_list_head_ = nullptr;

  DISALLOW_COPY_AND_ASSIGN(AtomicFlagSet);
};

}  // namespace internal
}  // namespace sequence_manager
}  // namespace base

#endif  // BASE_TASK_SEQUENCE_MANAGER_ATOMIC_FLAG_SET_H_

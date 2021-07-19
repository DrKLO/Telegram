// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_THREADING_SEQUENCE_LOCAL_STORAGE_SLOT_H_
#define BASE_THREADING_SEQUENCE_LOCAL_STORAGE_SLOT_H_

#include <memory>
#include <utility>

#include "base/base_export.h"
#include "base/template_util.h"
#include "base/threading/sequence_local_storage_map.h"

namespace base {

namespace internal {
BASE_EXPORT int GetNextSequenceLocalStorageSlotNumber();
}

// SequenceLocalStorageSlot allows arbitrary values to be stored and retrieved
// from a sequence. Values are deleted when the sequence is deleted.
//
// Example usage:
//
// int& GetSequenceLocalStorage()
//     static base::NoDestructor<SequenceLocalStorageSlot<int>> sls_value;
//     return sls_value->GetOrCreateValue();
// }
//
// void Read() {
//   int value = GetSequenceLocalStorage();
//   ...
// }
//
// void Write() {
//   GetSequenceLocalStorage() = 42;
// }
//
// void PostTasks() {
//   // Since Read() runs on the same sequence as Write(), it
//   // will read the value "42". A Read() running on a different
//   // sequence would not see that value.
//   scoped_refptr<base::SequencedTaskRunner> task_runner = ...;
//   task_runner->PostTask(FROM_HERE, base::BindOnce(&Write));
//   task_runner->PostTask(FROM_HERE, base::BindOnce(&Read));
// }
//
// SequenceLocalStorageSlot must be used within the scope of a
// ScopedSetSequenceLocalStorageMapForCurrentThread object.
// Note: this is true on all ThreadPool workers and on threads bound to a
// MessageLoop.
template <typename T, typename Deleter = std::default_delete<T>>
class SequenceLocalStorageSlot {
 public:
  SequenceLocalStorageSlot()
      : slot_id_(internal::GetNextSequenceLocalStorageSlotNumber()) {}
  ~SequenceLocalStorageSlot() = default;

  operator bool() const { return GetValuePointer() != nullptr; }

  // Default-constructs the value for the current sequence if not
  // already constructed. Then, returns the value.
  T& GetOrCreateValue() {
    T* ptr = GetValuePointer();
    if (!ptr)
      ptr = emplace();
    return *ptr;
  }

  // Returns a pointer to the value for the current sequence. May be
  // nullptr if the value was not constructed on the current sequence.
  T* GetValuePointer() {
    void* ptr =
        internal::SequenceLocalStorageMap::GetForCurrentThread().Get(slot_id_);
    return static_cast<T*>(ptr);
  }
  const T* GetValuePointer() const {
    return const_cast<SequenceLocalStorageSlot*>(this)->GetValuePointer();
  }

  T* operator->() { return GetValuePointer(); }
  const T* operator->() const { return GetValuePointer(); }

  T& operator*() { return *GetValuePointer(); }
  const T& operator*() const { return *GetValuePointer(); }

  void reset() { Adopt(nullptr); }

  // Constructs this slot's sequence-local value with |args...| and returns a
  // pointer to the created object.
  template <class... Args>
  T* emplace(Args&&... args) {
    T* value_ptr = new T(std::forward<Args>(args)...);
    Adopt(value_ptr);
    return value_ptr;
  }

 private:
  // Takes ownership of |value_ptr|.
  void Adopt(T* value_ptr) {
    // Since SequenceLocalStorageMap needs to store values of various types
    // within the same map, the type of value_destructor_pair.value is void*
    // (std::unique_ptr<void> is invalid). Memory is freed by calling
    // |value_destructor_pair.destructor| in the destructor of
    // ValueDestructorPair which is invoked when the value is overwritten by
    // another call to SequenceLocalStorageMap::Set or when the
    // SequenceLocalStorageMap is deleted.
    internal::SequenceLocalStorageMap::ValueDestructorPair::DestructorFunc*
        destructor = [](void* ptr) { Deleter()(static_cast<T*>(ptr)); };

    internal::SequenceLocalStorageMap::ValueDestructorPair
        value_destructor_pair(value_ptr, destructor);

    internal::SequenceLocalStorageMap::GetForCurrentThread().Set(
        slot_id_, std::move(value_destructor_pair));
  }

  // |slot_id_| is used as a key in SequenceLocalStorageMap
  const int slot_id_;
  DISALLOW_COPY_AND_ASSIGN(SequenceLocalStorageSlot);
};

}  // namespace base
#endif  // BASE_THREADING_SEQUENCE_LOCAL_STORAGE_SLOT_H_

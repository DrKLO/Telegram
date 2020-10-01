// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Weak pointers are pointers to an object that do not affect its lifetime,
// and which may be invalidated (i.e. reset to nullptr) by the object, or its
// owner, at any time, most commonly when the object is about to be deleted.

// Weak pointers are useful when an object needs to be accessed safely by one
// or more objects other than its owner, and those callers can cope with the
// object vanishing and e.g. tasks posted to it being silently dropped.
// Reference-counting such an object would complicate the ownership graph and
// make it harder to reason about the object's lifetime.

// EXAMPLE:
//
//  class Controller {
//   public:
//    void SpawnWorker() { Worker::StartNew(weak_factory_.GetWeakPtr()); }
//    void WorkComplete(const Result& result) { ... }
//   private:
//    // Member variables should appear before the WeakPtrFactory, to ensure
//    // that any WeakPtrs to Controller are invalidated before its members
//    // variable's destructors are executed, rendering them invalid.
//    WeakPtrFactory<Controller> weak_factory_{this};
//  };
//
//  class Worker {
//   public:
//    static void StartNew(const WeakPtr<Controller>& controller) {
//      Worker* worker = new Worker(controller);
//      // Kick off asynchronous processing...
//    }
//   private:
//    Worker(const WeakPtr<Controller>& controller)
//        : controller_(controller) {}
//    void DidCompleteAsynchronousProcessing(const Result& result) {
//      if (controller_)
//        controller_->WorkComplete(result);
//    }
//    WeakPtr<Controller> controller_;
//  };
//
// With this implementation a caller may use SpawnWorker() to dispatch multiple
// Workers and subsequently delete the Controller, without waiting for all
// Workers to have completed.

// ------------------------- IMPORTANT: Thread-safety -------------------------

// Weak pointers may be passed safely between sequences, but must always be
// dereferenced and invalidated on the same SequencedTaskRunner otherwise
// checking the pointer would be racey.
//
// To ensure correct use, the first time a WeakPtr issued by a WeakPtrFactory
// is dereferenced, the factory and its WeakPtrs become bound to the calling
// sequence or current SequencedWorkerPool token, and cannot be dereferenced or
// invalidated on any other task runner. Bound WeakPtrs can still be handed
// off to other task runners, e.g. to use to post tasks back to object on the
// bound sequence.
//
// If all WeakPtr objects are destroyed or invalidated then the factory is
// unbound from the SequencedTaskRunner/Thread. The WeakPtrFactory may then be
// destroyed, or new WeakPtr objects may be used, from a different sequence.
//
// Thus, at least one WeakPtr object must exist and have been dereferenced on
// the correct sequence to enforce that other WeakPtr objects will enforce they
// are used on the desired sequence.

#ifndef BASE_MEMORY_WEAK_PTR_H_
#define BASE_MEMORY_WEAK_PTR_H_

#include <cstddef>
#include <type_traits>

#include "base/base_export.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/memory/ref_counted.h"
#include "base/sequence_checker.h"
#include "base/synchronization/atomic_flag.h"

namespace base {

template <typename T> class SupportsWeakPtr;
template <typename T> class WeakPtr;

namespace internal {
// These classes are part of the WeakPtr implementation.
// DO NOT USE THESE CLASSES DIRECTLY YOURSELF.

class BASE_EXPORT WeakReference {
 public:
  // Although Flag is bound to a specific SequencedTaskRunner, it may be
  // deleted from another via base::WeakPtr::~WeakPtr().
  class BASE_EXPORT Flag : public RefCountedThreadSafe<Flag> {
   public:
    Flag();

    void Invalidate();
    bool IsValid() const;

    bool MaybeValid() const;

    void DetachFromSequence();

   private:
    friend class base::RefCountedThreadSafe<Flag>;

    ~Flag();

    SEQUENCE_CHECKER(sequence_checker_);
    AtomicFlag invalidated_;
  };

  WeakReference();
  explicit WeakReference(const scoped_refptr<Flag>& flag);
  ~WeakReference();

  WeakReference(WeakReference&& other) noexcept;
  WeakReference(const WeakReference& other);
  WeakReference& operator=(WeakReference&& other) noexcept = default;
  WeakReference& operator=(const WeakReference& other) = default;

  bool IsValid() const;
  bool MaybeValid() const;

 private:
  scoped_refptr<const Flag> flag_;
};

class BASE_EXPORT WeakReferenceOwner {
 public:
  WeakReferenceOwner();
  ~WeakReferenceOwner();

  WeakReference GetRef() const;

  bool HasRefs() const { return !flag_->HasOneRef(); }

  void Invalidate();

 private:
  scoped_refptr<WeakReference::Flag> flag_;
};

// This class simplifies the implementation of WeakPtr's type conversion
// constructor by avoiding the need for a public accessor for ref_.  A
// WeakPtr<T> cannot access the private members of WeakPtr<U>, so this
// base class gives us a way to access ref_ in a protected fashion.
class BASE_EXPORT WeakPtrBase {
 public:
  WeakPtrBase();
  ~WeakPtrBase();

  WeakPtrBase(const WeakPtrBase& other) = default;
  WeakPtrBase(WeakPtrBase&& other) noexcept = default;
  WeakPtrBase& operator=(const WeakPtrBase& other) = default;
  WeakPtrBase& operator=(WeakPtrBase&& other) noexcept = default;

  void reset() {
    ref_ = internal::WeakReference();
    ptr_ = 0;
  }

 protected:
  WeakPtrBase(const WeakReference& ref, uintptr_t ptr);

  WeakReference ref_;

  // This pointer is only valid when ref_.is_valid() is true.  Otherwise, its
  // value is undefined (as opposed to nullptr).
  uintptr_t ptr_;
};

// This class provides a common implementation of common functions that would
// otherwise get instantiated separately for each distinct instantiation of
// SupportsWeakPtr<>.
class SupportsWeakPtrBase {
 public:
  // A safe static downcast of a WeakPtr<Base> to WeakPtr<Derived>. This
  // conversion will only compile if there is exists a Base which inherits
  // from SupportsWeakPtr<Base>. See base::AsWeakPtr() below for a helper
  // function that makes calling this easier.
  //
  // Precondition: t != nullptr
  template<typename Derived>
  static WeakPtr<Derived> StaticAsWeakPtr(Derived* t) {
    static_assert(
        std::is_base_of<internal::SupportsWeakPtrBase, Derived>::value,
        "AsWeakPtr argument must inherit from SupportsWeakPtr");
    return AsWeakPtrImpl<Derived>(t);
  }

 private:
  // This template function uses type inference to find a Base of Derived
  // which is an instance of SupportsWeakPtr<Base>. We can then safely
  // static_cast the Base* to a Derived*.
  template <typename Derived, typename Base>
  static WeakPtr<Derived> AsWeakPtrImpl(SupportsWeakPtr<Base>* t) {
    WeakPtr<Base> ptr = t->AsWeakPtr();
    return WeakPtr<Derived>(
        ptr.ref_, static_cast<Derived*>(reinterpret_cast<Base*>(ptr.ptr_)));
  }
};

}  // namespace internal

template <typename T> class WeakPtrFactory;

// The WeakPtr class holds a weak reference to |T*|.
//
// This class is designed to be used like a normal pointer.  You should always
// null-test an object of this class before using it or invoking a method that
// may result in the underlying object being destroyed.
//
// EXAMPLE:
//
//   class Foo { ... };
//   WeakPtr<Foo> foo;
//   if (foo)
//     foo->method();
//
template <typename T>
class WeakPtr : public internal::WeakPtrBase {
 public:
  WeakPtr() = default;
  WeakPtr(std::nullptr_t) {}

  // Allow conversion from U to T provided U "is a" T. Note that this
  // is separate from the (implicit) copy and move constructors.
  template <typename U>
  WeakPtr(const WeakPtr<U>& other) : WeakPtrBase(other) {
    // Need to cast from U* to T* to do pointer adjustment in case of multiple
    // inheritance. This also enforces the "U is a T" rule.
    T* t = reinterpret_cast<U*>(other.ptr_);
    ptr_ = reinterpret_cast<uintptr_t>(t);
  }
  template <typename U>
  WeakPtr(WeakPtr<U>&& other) noexcept : WeakPtrBase(std::move(other)) {
    // Need to cast from U* to T* to do pointer adjustment in case of multiple
    // inheritance. This also enforces the "U is a T" rule.
    T* t = reinterpret_cast<U*>(other.ptr_);
    ptr_ = reinterpret_cast<uintptr_t>(t);
  }

  T* get() const {
    return ref_.IsValid() ? reinterpret_cast<T*>(ptr_) : nullptr;
  }

  T& operator*() const {
    DCHECK(get() != nullptr);
    return *get();
  }
  T* operator->() const {
    DCHECK(get() != nullptr);
    return get();
  }

  // Allow conditionals to test validity, e.g. if (weak_ptr) {...};
  explicit operator bool() const { return get() != nullptr; }

  // Returns false if the WeakPtr is confirmed to be invalid. This call is safe
  // to make from any thread, e.g. to optimize away unnecessary work, but
  // operator bool() must always be called, on the correct sequence, before
  // actually using the pointer.
  //
  // Warning: as with any object, this call is only thread-safe if the WeakPtr
  // instance isn't being re-assigned or reset() racily with this call.
  bool MaybeValid() const { return ref_.MaybeValid(); }

  // Returns whether the object |this| points to has been invalidated. This can
  // be used to distinguish a WeakPtr to a destroyed object from one that has
  // been explicitly set to null.
  bool WasInvalidated() const { return ptr_ && !ref_.IsValid(); }

 private:
  friend class internal::SupportsWeakPtrBase;
  template <typename U> friend class WeakPtr;
  friend class SupportsWeakPtr<T>;
  friend class WeakPtrFactory<T>;

  WeakPtr(const internal::WeakReference& ref, T* ptr)
      : WeakPtrBase(ref, reinterpret_cast<uintptr_t>(ptr)) {}
};

// Allow callers to compare WeakPtrs against nullptr to test validity.
template <class T>
bool operator!=(const WeakPtr<T>& weak_ptr, std::nullptr_t) {
  return !(weak_ptr == nullptr);
}
template <class T>
bool operator!=(std::nullptr_t, const WeakPtr<T>& weak_ptr) {
  return weak_ptr != nullptr;
}
template <class T>
bool operator==(const WeakPtr<T>& weak_ptr, std::nullptr_t) {
  return weak_ptr.get() == nullptr;
}
template <class T>
bool operator==(std::nullptr_t, const WeakPtr<T>& weak_ptr) {
  return weak_ptr == nullptr;
}

namespace internal {
class BASE_EXPORT WeakPtrFactoryBase {
 protected:
  WeakPtrFactoryBase(uintptr_t ptr);
  ~WeakPtrFactoryBase();
  internal::WeakReferenceOwner weak_reference_owner_;
  uintptr_t ptr_;
};
}  // namespace internal

// A class may be composed of a WeakPtrFactory and thereby
// control how it exposes weak pointers to itself.  This is helpful if you only
// need weak pointers within the implementation of a class.  This class is also
// useful when working with primitive types.  For example, you could have a
// WeakPtrFactory<bool> that is used to pass around a weak reference to a bool.
template <class T>
class WeakPtrFactory : public internal::WeakPtrFactoryBase {
 public:
  explicit WeakPtrFactory(T* ptr)
      : WeakPtrFactoryBase(reinterpret_cast<uintptr_t>(ptr)) {}

  ~WeakPtrFactory() = default;

  WeakPtr<T> GetWeakPtr() {
    return WeakPtr<T>(weak_reference_owner_.GetRef(),
                      reinterpret_cast<T*>(ptr_));
  }

  // Call this method to invalidate all existing weak pointers.
  void InvalidateWeakPtrs() {
    DCHECK(ptr_);
    weak_reference_owner_.Invalidate();
  }

  // Call this method to determine if any weak pointers exist.
  bool HasWeakPtrs() const {
    DCHECK(ptr_);
    return weak_reference_owner_.HasRefs();
  }

 private:
  DISALLOW_IMPLICIT_CONSTRUCTORS(WeakPtrFactory);
};

// A class may extend from SupportsWeakPtr to let others take weak pointers to
// it. This avoids the class itself implementing boilerplate to dispense weak
// pointers.  However, since SupportsWeakPtr's destructor won't invalidate
// weak pointers to the class until after the derived class' members have been
// destroyed, its use can lead to subtle use-after-destroy issues.
template <class T>
class SupportsWeakPtr : public internal::SupportsWeakPtrBase {
 public:
  SupportsWeakPtr() = default;

  WeakPtr<T> AsWeakPtr() {
    return WeakPtr<T>(weak_reference_owner_.GetRef(), static_cast<T*>(this));
  }

 protected:
  ~SupportsWeakPtr() = default;

 private:
  internal::WeakReferenceOwner weak_reference_owner_;
  DISALLOW_COPY_AND_ASSIGN(SupportsWeakPtr);
};

// Helper function that uses type deduction to safely return a WeakPtr<Derived>
// when Derived doesn't directly extend SupportsWeakPtr<Derived>, instead it
// extends a Base that extends SupportsWeakPtr<Base>.
//
// EXAMPLE:
//   class Base : public base::SupportsWeakPtr<Producer> {};
//   class Derived : public Base {};
//
//   Derived derived;
//   base::WeakPtr<Derived> ptr = base::AsWeakPtr(&derived);
//
// Note that the following doesn't work (invalid type conversion) since
// Derived::AsWeakPtr() is WeakPtr<Base> SupportsWeakPtr<Base>::AsWeakPtr(),
// and there's no way to safely cast WeakPtr<Base> to WeakPtr<Derived> at
// the caller.
//
//   base::WeakPtr<Derived> ptr = derived.AsWeakPtr();  // Fails.

template <typename Derived>
WeakPtr<Derived> AsWeakPtr(Derived* t) {
  return internal::SupportsWeakPtrBase::StaticAsWeakPtr<Derived>(t);
}

}  // namespace base

#endif  // BASE_MEMORY_WEAK_PTR_H_

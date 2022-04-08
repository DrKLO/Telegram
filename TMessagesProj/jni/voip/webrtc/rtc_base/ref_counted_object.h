/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef RTC_BASE_REF_COUNTED_OBJECT_H_
#define RTC_BASE_REF_COUNTED_OBJECT_H_

#include <type_traits>
#include <utility>

#include "api/scoped_refptr.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/ref_count.h"
#include "rtc_base/ref_counter.h"

namespace rtc {

template <class T>
class RefCountedObject : public T {
 public:
  RefCountedObject() {}

  template <class P0>
  explicit RefCountedObject(P0&& p0) : T(std::forward<P0>(p0)) {}

  template <class P0, class P1, class... Args>
  RefCountedObject(P0&& p0, P1&& p1, Args&&... args)
      : T(std::forward<P0>(p0),
          std::forward<P1>(p1),
          std::forward<Args>(args)...) {}

  void AddRef() const override { ref_count_.IncRef(); }

  RefCountReleaseStatus Release() const override {
    const auto status = ref_count_.DecRef();
    if (status == RefCountReleaseStatus::kDroppedLastRef) {
      delete this;
    }
    return status;
  }

  // Return whether the reference count is one. If the reference count is used
  // in the conventional way, a reference count of 1 implies that the current
  // thread owns the reference and no other thread shares it. This call
  // performs the test for a reference count of one, and performs the memory
  // barrier needed for the owning thread to act on the object, knowing that it
  // has exclusive access to the object.
  virtual bool HasOneRef() const { return ref_count_.HasOneRef(); }

 protected:
  ~RefCountedObject() override {}

  mutable webrtc::webrtc_impl::RefCounter ref_count_{0};

  RTC_DISALLOW_COPY_AND_ASSIGN(RefCountedObject);
};

template <class T>
class FinalRefCountedObject final : public T {
 public:
  using T::T;
  // Until c++17 compilers are allowed not to inherit the default constructors.
  // Thus the default constructors are forwarded explicitly.
  FinalRefCountedObject() = default;
  explicit FinalRefCountedObject(const T& other) : T(other) {}
  explicit FinalRefCountedObject(T&& other) : T(std::move(other)) {}
  FinalRefCountedObject(const FinalRefCountedObject&) = delete;
  FinalRefCountedObject& operator=(const FinalRefCountedObject&) = delete;

  void AddRef() const { ref_count_.IncRef(); }
  RefCountReleaseStatus Release() const {
    const auto status = ref_count_.DecRef();
    if (status == RefCountReleaseStatus::kDroppedLastRef) {
      delete this;
    }
    return status;
  }
  bool HasOneRef() const { return ref_count_.HasOneRef(); }

 private:
  ~FinalRefCountedObject() = default;

  mutable webrtc::webrtc_impl::RefCounter ref_count_{0};
};

// General utilities for constructing a reference counted class and the
// appropriate reference count implementation for that class.
//
// These utilities select either the `RefCountedObject` implementation or
// `FinalRefCountedObject` depending on whether the to-be-shared class is
// derived from the RefCountInterface interface or not (respectively).

// `make_ref_counted`:
//
// Use this when you want to construct a reference counted object of type T and
// get a `scoped_refptr<>` back. Example:
//
//   auto p = make_ref_counted<Foo>("bar", 123);
//
// For a class that inherits from RefCountInterface, this is equivalent to:
//
//   auto p = scoped_refptr<Foo>(new RefCountedObject<Foo>("bar", 123));
//
// If the class does not inherit from RefCountInterface, the example is
// equivalent to:
//
//   auto p = scoped_refptr<FinalRefCountedObject<Foo>>(
//       new FinalRefCountedObject<Foo>("bar", 123));
//
// In these cases, `make_ref_counted` reduces the amount of boilerplate code but
// also helps with the most commonly intended usage of RefCountedObject whereby
// methods for reference counting, are virtual and designed to satisfy the need
// of an interface. When such a need does not exist, it is more efficient to use
// the `FinalRefCountedObject` template, which does not add the vtable overhead.
//
// Note that in some cases, using RefCountedObject directly may still be what's
// needed.

// `make_ref_counted` for classes that are convertible to RefCountInterface.
template <
    typename T,
    typename... Args,
    typename std::enable_if<std::is_convertible<T*, RefCountInterface*>::value,
                            T>::type* = nullptr>
scoped_refptr<T> make_ref_counted(Args&&... args) {
  return new RefCountedObject<T>(std::forward<Args>(args)...);
}

// `make_ref_counted` for complete classes that are not convertible to
// RefCountInterface.
template <
    typename T,
    typename... Args,
    typename std::enable_if<!std::is_convertible<T*, RefCountInterface*>::value,
                            T>::type* = nullptr>
scoped_refptr<FinalRefCountedObject<T>> make_ref_counted(Args&&... args) {
  return new FinalRefCountedObject<T>(std::forward<Args>(args)...);
}

// `Ref<>`, `Ref<>::Type` and `Ref<>::Ptr`:
//
// `Ref` is a type declaring utility that is compatible with `make_ref_counted`
// and can be used in classes and methods where it's more convenient (or
// readable) to have the compiler figure out the fully fleshed out type for a
// class rather than spell it out verbatim in all places the type occurs (which
// can mean maintenance work if the class layout changes).
//
// Usage examples:
//
// If you want to declare the parameter type that's always compatible with
// this code:
//
//   Bar(make_ref_counted<Foo>());
//
// You can use `Ref<>::Ptr` to declare a compatible scoped_refptr type:
//
//   void Bar(Ref<Foo>::Ptr p);
//
// This might be more practically useful in templates though.
//
// In rare cases you might need to be able to declare a parameter that's fully
// compatible with the reference counted T type - and just using T* is not
// enough. To give a code example, we can declare a function, `Foo` that is
// compatible with this code:
//   auto p = make_ref_counted<Foo>();
//   Foo(p.get());
//
//   void Foo(Ref<Foo>::Type* foo_ptr);
//
// Alternatively this would be:
//   void Foo(Foo* foo_ptr);
// or
//   void Foo(FinalRefCountedObject<Foo>* foo_ptr);

// Declares the approprate reference counted type for T depending on whether
// T is convertible to RefCountInterface or not.
// For classes that are convertible, the type will simply be T.
// For classes that cannot be converted to RefCountInterface, the type will be
// FinalRefCountedObject<T>.
// This is most useful for declaring a scoped_refptr<T> instance for a class
// that may or may not implement a virtual reference counted interface:
// * scoped_refptr<Ref<Foo>::Type> my_ptr;
template <typename T>
struct Ref {
  typedef typename std::conditional<
      std::is_convertible<T*, RefCountInterface*>::value,
      T,
      FinalRefCountedObject<T>>::type Type;

  typedef scoped_refptr<Type> Ptr;
};

}  // namespace rtc

#endif  // RTC_BASE_REF_COUNTED_OBJECT_H_

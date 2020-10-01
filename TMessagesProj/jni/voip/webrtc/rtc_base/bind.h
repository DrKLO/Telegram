/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Bind() is an overloaded function that converts method calls into function
// objects (aka functors). The method object is captured as a scoped_refptr<> if
// possible, and as a raw pointer otherwise. Any arguments to the method are
// captured by value. The return value of Bind is a stateful, nullary function
// object. Care should be taken about the lifetime of objects captured by
// Bind(); the returned functor knows nothing about the lifetime of a non
// ref-counted method object or any arguments passed by pointer, and calling the
// functor with a destroyed object will surely do bad things.
//
// To prevent the method object from being captured as a scoped_refptr<>, you
// can use Unretained. But this should only be done when absolutely necessary,
// and when the caller knows the extra reference isn't needed.
//
// Example usage:
//   struct Foo {
//     int Test1() { return 42; }
//     int Test2() const { return 52; }
//     int Test3(int x) { return x*x; }
//     float Test4(int x, float y) { return x + y; }
//   };
//
//   int main() {
//     Foo foo;
//     cout << rtc::Bind(&Foo::Test1, &foo)() << endl;
//     cout << rtc::Bind(&Foo::Test2, &foo)() << endl;
//     cout << rtc::Bind(&Foo::Test3, &foo, 3)() << endl;
//     cout << rtc::Bind(&Foo::Test4, &foo, 7, 8.5f)() << endl;
//   }
//
// Example usage of ref counted objects:
//   struct Bar {
//     int AddRef();
//     int Release();
//
//     void Test() {}
//     void BindThis() {
//       // The functor passed to AsyncInvoke() will keep this object alive.
//       invoker.AsyncInvoke(RTC_FROM_HERE,rtc::Bind(&Bar::Test, this));
//     }
//   };
//
//   int main() {
//     rtc::scoped_refptr<Bar> bar = new rtc::RefCountedObject<Bar>();
//     auto functor = rtc::Bind(&Bar::Test, bar);
//     bar = nullptr;
//     // The functor stores an internal scoped_refptr<Bar>, so this is safe.
//     functor();
//   }
//

#ifndef RTC_BASE_BIND_H_
#define RTC_BASE_BIND_H_

#include <tuple>
#include <type_traits>

#include "api/scoped_refptr.h"

#define NONAME

namespace rtc {
namespace detail {
// This is needed because the template parameters in Bind can't be resolved
// if they're used both as parameters of the function pointer type and as
// parameters to Bind itself: the function pointer parameters are exact
// matches to the function prototype, but the parameters to bind have
// references stripped. This trick allows the compiler to dictate the Bind
// parameter types rather than deduce them.
template <class T>
struct identity {
  typedef T type;
};

// IsRefCounted<T>::value will be true for types that can be used in
// rtc::scoped_refptr<T>, i.e. types that implements nullary functions AddRef()
// and Release(), regardless of their return types. AddRef() and Release() can
// be defined in T or any superclass of T.
template <typename T>
class IsRefCounted {
  // This is a complex implementation detail done with SFINAE.

  // Define types such that sizeof(Yes) != sizeof(No).
  struct Yes {
    char dummy[1];
  };
  struct No {
    char dummy[2];
  };
  // Define two overloaded template functions with return types of different
  // size. This way, we can use sizeof() on the return type to determine which
  // function the compiler would have chosen. One function will be preferred
  // over the other if it is possible to create it without compiler errors,
  // otherwise the compiler will simply remove it, and default to the less
  // preferred function.
  template <typename R>
  static Yes test(R* r, decltype(r->AddRef(), r->Release(), 42));
  template <typename C>
  static No test(...);

 public:
  // Trick the compiler to tell if it's possible to call AddRef() and Release().
  static const bool value = sizeof(test<T>((T*)nullptr, 42)) == sizeof(Yes);
};

// TernaryTypeOperator is a helper class to select a type based on a static bool
// value.
template <bool condition, typename IfTrueT, typename IfFalseT>
struct TernaryTypeOperator {};

template <typename IfTrueT, typename IfFalseT>
struct TernaryTypeOperator<true, IfTrueT, IfFalseT> {
  typedef IfTrueT type;
};

template <typename IfTrueT, typename IfFalseT>
struct TernaryTypeOperator<false, IfTrueT, IfFalseT> {
  typedef IfFalseT type;
};

// PointerType<T>::type will be scoped_refptr<T> for ref counted types, and T*
// otherwise.
template <class T>
struct PointerType {
  typedef typename TernaryTypeOperator<IsRefCounted<T>::value,
                                       scoped_refptr<T>,
                                       T*>::type type;
};

template <typename T>
class UnretainedWrapper {
 public:
  explicit UnretainedWrapper(T* o) : ptr_(o) {}
  T* get() const { return ptr_; }

 private:
  T* ptr_;
};

}  // namespace detail

template <typename T>
static inline detail::UnretainedWrapper<T> Unretained(T* o) {
  return detail::UnretainedWrapper<T>(o);
}

template <class ObjectT, class MethodT, class R, typename... Args>
class MethodFunctor {
 public:
  MethodFunctor(MethodT method, ObjectT* object, Args... args)
      : method_(method), object_(object), args_(args...) {}
  R operator()() const {
    return CallMethod(std::index_sequence_for<Args...>());
  }

 private:
  template <size_t... S>
  R CallMethod(std::index_sequence<S...>) const {
    return (object_->*method_)(std::get<S>(args_)...);
  }

  MethodT method_;
  typename detail::PointerType<ObjectT>::type object_;
  typename std::tuple<typename std::remove_reference<Args>::type...> args_;
};

template <class ObjectT, class MethodT, class R, typename... Args>
class UnretainedMethodFunctor {
 public:
  UnretainedMethodFunctor(MethodT method,
                          detail::UnretainedWrapper<ObjectT> object,
                          Args... args)
      : method_(method), object_(object.get()), args_(args...) {}
  R operator()() const {
    return CallMethod(std::index_sequence_for<Args...>());
  }

 private:
  template <size_t... S>
  R CallMethod(std::index_sequence<S...>) const {
    return (object_->*method_)(std::get<S>(args_)...);
  }

  MethodT method_;
  ObjectT* object_;
  typename std::tuple<typename std::remove_reference<Args>::type...> args_;
};

template <class FunctorT, class R, typename... Args>
class Functor {
 public:
  Functor(const FunctorT& functor, Args... args)
      : functor_(functor), args_(args...) {}
  R operator()() const {
    return CallFunction(std::index_sequence_for<Args...>());
  }

 private:
  template <size_t... S>
  R CallFunction(std::index_sequence<S...>) const {
    return functor_(std::get<S>(args_)...);
  }

  FunctorT functor_;
  typename std::tuple<typename std::remove_reference<Args>::type...> args_;
};

#define FP_T(x) R (ObjectT::*x)(Args...)

template <class ObjectT, class R, typename... Args>
MethodFunctor<ObjectT, FP_T(NONAME), R, Args...> Bind(
    FP_T(method),
    ObjectT* object,
    typename detail::identity<Args>::type... args) {
  return MethodFunctor<ObjectT, FP_T(NONAME), R, Args...>(method, object,
                                                          args...);
}

template <class ObjectT, class R, typename... Args>
MethodFunctor<ObjectT, FP_T(NONAME), R, Args...> Bind(
    FP_T(method),
    const scoped_refptr<ObjectT>& object,
    typename detail::identity<Args>::type... args) {
  return MethodFunctor<ObjectT, FP_T(NONAME), R, Args...>(method, object.get(),
                                                          args...);
}

template <class ObjectT, class R, typename... Args>
UnretainedMethodFunctor<ObjectT, FP_T(NONAME), R, Args...> Bind(
    FP_T(method),
    detail::UnretainedWrapper<ObjectT> object,
    typename detail::identity<Args>::type... args) {
  return UnretainedMethodFunctor<ObjectT, FP_T(NONAME), R, Args...>(
      method, object, args...);
}

#undef FP_T
#define FP_T(x) R (ObjectT::*x)(Args...) const

template <class ObjectT, class R, typename... Args>
MethodFunctor<const ObjectT, FP_T(NONAME), R, Args...> Bind(
    FP_T(method),
    const ObjectT* object,
    typename detail::identity<Args>::type... args) {
  return MethodFunctor<const ObjectT, FP_T(NONAME), R, Args...>(method, object,
                                                                args...);
}
template <class ObjectT, class R, typename... Args>
UnretainedMethodFunctor<const ObjectT, FP_T(NONAME), R, Args...> Bind(
    FP_T(method),
    detail::UnretainedWrapper<const ObjectT> object,
    typename detail::identity<Args>::type... args) {
  return UnretainedMethodFunctor<const ObjectT, FP_T(NONAME), R, Args...>(
      method, object, args...);
}

#undef FP_T
#define FP_T(x) R (*x)(Args...)

template <class R, typename... Args>
Functor<FP_T(NONAME), R, Args...> Bind(
    FP_T(function),
    typename detail::identity<Args>::type... args) {
  return Functor<FP_T(NONAME), R, Args...>(function, args...);
}

#undef FP_T

}  // namespace rtc

#undef NONAME

#endif  // RTC_BASE_BIND_H_

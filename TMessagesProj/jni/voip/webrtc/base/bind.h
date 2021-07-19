// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_BIND_H_
#define BASE_BIND_H_

#include <functional>
#include <memory>
#include <type_traits>
#include <utility>

#include "base/bind_internal.h"
#include "base/compiler_specific.h"
#include "base/template_util.h"
#include "build/build_config.h"

#if defined(OS_MACOSX) && !HAS_FEATURE(objc_arc)
#include "base/mac/scoped_block.h"
#endif

// -----------------------------------------------------------------------------
// Usage documentation
// -----------------------------------------------------------------------------
//
// Overview:
// base::BindOnce() and base::BindRepeating() are helpers for creating
// base::OnceCallback and base::RepeatingCallback objects respectively.
//
// For a runnable object of n-arity, the base::Bind*() family allows partial
// application of the first m arguments. The remaining n - m arguments must be
// passed when invoking the callback with Run().
//
//   // The first argument is bound at callback creation; the remaining
//   // two must be passed when calling Run() on the callback object.
//   base::OnceCallback<long(int, long)> cb = base::BindOnce(
//       [](short x, int y, long z) { return x * y * z; }, 42);
//
// When binding to a method, the receiver object must also be specified at
// callback creation time. When Run() is invoked, the method will be invoked on
// the specified receiver object.
//
//   class C : public base::RefCounted<C> { void F(); };
//   auto instance = base::MakeRefCounted<C>();
//   auto cb = base::BindOnce(&C::F, instance);
//   std::move(cb).Run();  // Identical to instance->F()
//
// base::Bind is currently a type alias for base::BindRepeating(). In the
// future, we expect to flip this to default to base::BindOnce().
//
// See //docs/callback.md for the full documentation.
//
// -----------------------------------------------------------------------------
// Implementation notes
// -----------------------------------------------------------------------------
//
// If you're reading the implementation, before proceeding further, you should
// read the top comment of base/bind_internal.h for a definition of common
// terms and concepts.

namespace base {

namespace internal {

// IsOnceCallback<T> is a std::true_type if |T| is a OnceCallback.
template <typename T>
struct IsOnceCallback : std::false_type {};

template <typename Signature>
struct IsOnceCallback<OnceCallback<Signature>> : std::true_type {};

// Helper to assert that parameter |i| of type |Arg| can be bound, which means:
// - |Arg| can be retained internally as |Storage|.
// - |Arg| can be forwarded as |Unwrapped| to |Param|.
template <size_t i,
          typename Arg,
          typename Storage,
          typename Unwrapped,
          typename Param>
struct AssertConstructible {
 private:
  static constexpr bool param_is_forwardable =
      std::is_constructible<Param, Unwrapped>::value;
  // Unlike the check for binding into storage below, the check for
  // forwardability drops the const qualifier for repeating callbacks. This is
  // to try to catch instances where std::move()--which forwards as a const
  // reference with repeating callbacks--is used instead of base::Passed().
  static_assert(
      param_is_forwardable ||
          !std::is_constructible<Param, std::decay_t<Unwrapped>&&>::value,
      "Bound argument |i| is move-only but will be forwarded by copy. "
      "Ensure |Arg| is bound using base::Passed(), not std::move().");
  static_assert(
      param_is_forwardable,
      "Bound argument |i| of type |Arg| cannot be forwarded as "
      "|Unwrapped| to the bound functor, which declares it as |Param|.");

  static constexpr bool arg_is_storable =
      std::is_constructible<Storage, Arg>::value;
  static_assert(arg_is_storable ||
                    !std::is_constructible<Storage, std::decay_t<Arg>&&>::value,
                "Bound argument |i| is move-only but will be bound by copy. "
                "Ensure |Arg| is mutable and bound using std::move().");
  static_assert(arg_is_storable,
                "Bound argument |i| of type |Arg| cannot be converted and "
                "bound as |Storage|.");
};

// Takes three same-length TypeLists, and applies AssertConstructible for each
// triples.
template <typename Index,
          typename Args,
          typename UnwrappedTypeList,
          typename ParamsList>
struct AssertBindArgsValidity;

template <size_t... Ns,
          typename... Args,
          typename... Unwrapped,
          typename... Params>
struct AssertBindArgsValidity<std::index_sequence<Ns...>,
                              TypeList<Args...>,
                              TypeList<Unwrapped...>,
                              TypeList<Params...>>
    : AssertConstructible<Ns, Args, std::decay_t<Args>, Unwrapped, Params>... {
  static constexpr bool ok = true;
};

// The implementation of TransformToUnwrappedType below.
template <bool is_once, typename T>
struct TransformToUnwrappedTypeImpl;

template <typename T>
struct TransformToUnwrappedTypeImpl<true, T> {
  using StoredType = std::decay_t<T>;
  using ForwardType = StoredType&&;
  using Unwrapped = decltype(Unwrap(std::declval<ForwardType>()));
};

template <typename T>
struct TransformToUnwrappedTypeImpl<false, T> {
  using StoredType = std::decay_t<T>;
  using ForwardType = const StoredType&;
  using Unwrapped = decltype(Unwrap(std::declval<ForwardType>()));
};

// Transform |T| into `Unwrapped` type, which is passed to the target function.
// Example:
//   In is_once == true case,
//     `int&&` -> `int&&`,
//     `const int&` -> `int&&`,
//     `OwnedWrapper<int>&` -> `int*&&`.
//   In is_once == false case,
//     `int&&` -> `const int&`,
//     `const int&` -> `const int&`,
//     `OwnedWrapper<int>&` -> `int* const &`.
template <bool is_once, typename T>
using TransformToUnwrappedType =
    typename TransformToUnwrappedTypeImpl<is_once, T>::Unwrapped;

// Transforms |Args| into `Unwrapped` types, and packs them into a TypeList.
// If |is_method| is true, tries to dereference the first argument to support
// smart pointers.
template <bool is_once, bool is_method, typename... Args>
struct MakeUnwrappedTypeListImpl {
  using Type = TypeList<TransformToUnwrappedType<is_once, Args>...>;
};

// Performs special handling for this pointers.
// Example:
//   int* -> int*,
//   std::unique_ptr<int> -> int*.
template <bool is_once, typename Receiver, typename... Args>
struct MakeUnwrappedTypeListImpl<is_once, true, Receiver, Args...> {
  using UnwrappedReceiver = TransformToUnwrappedType<is_once, Receiver>;
  using Type = TypeList<decltype(&*std::declval<UnwrappedReceiver>()),
                        TransformToUnwrappedType<is_once, Args>...>;
};

template <bool is_once, bool is_method, typename... Args>
using MakeUnwrappedTypeList =
    typename MakeUnwrappedTypeListImpl<is_once, is_method, Args...>::Type;

// Used below in BindImpl to determine whether to use Invoker::Run or
// Invoker::RunOnce.
// Note: Simply using `kIsOnce ? &Invoker::RunOnce : &Invoker::Run` does not
// work, since the compiler needs to check whether both expressions are
// well-formed. Using `Invoker::Run` with a OnceCallback triggers a
// static_assert, which is why the ternary expression does not compile.
// TODO(crbug.com/752720): Remove this indirection once we have `if constexpr`.
template <typename Invoker>
constexpr auto GetInvokeFunc(std::true_type) {
  return Invoker::RunOnce;
}

template <typename Invoker>
constexpr auto GetInvokeFunc(std::false_type) {
  return Invoker::Run;
}

template <template <typename> class CallbackT,
          typename Functor,
          typename... Args>
decltype(auto) BindImpl(Functor&& functor, Args&&... args) {
  // This block checks if each |args| matches to the corresponding params of the
  // target function. This check does not affect the behavior of Bind, but its
  // error message should be more readable.
  static constexpr bool kIsOnce = IsOnceCallback<CallbackT<void()>>::value;
  using Helper = internal::BindTypeHelper<Functor, Args...>;
  using FunctorTraits = typename Helper::FunctorTraits;
  using BoundArgsList = typename Helper::BoundArgsList;
  using UnwrappedArgsList =
      internal::MakeUnwrappedTypeList<kIsOnce, FunctorTraits::is_method,
                                      Args&&...>;
  using BoundParamsList = typename Helper::BoundParamsList;
  static_assert(internal::AssertBindArgsValidity<
                    std::make_index_sequence<Helper::num_bounds>, BoundArgsList,
                    UnwrappedArgsList, BoundParamsList>::ok,
                "The bound args need to be convertible to the target params.");

  using BindState = internal::MakeBindStateType<Functor, Args...>;
  using UnboundRunType = MakeUnboundRunType<Functor, Args...>;
  using Invoker = internal::Invoker<BindState, UnboundRunType>;
  using CallbackType = CallbackT<UnboundRunType>;

  // Store the invoke func into PolymorphicInvoke before casting it to
  // InvokeFuncStorage, so that we can ensure its type matches to
  // PolymorphicInvoke, to which CallbackType will cast back.
  using PolymorphicInvoke = typename CallbackType::PolymorphicInvoke;
  PolymorphicInvoke invoke_func =
      GetInvokeFunc<Invoker>(bool_constant<kIsOnce>());

  using InvokeFuncStorage = internal::BindStateBase::InvokeFuncStorage;
  return CallbackType(BindState::Create(
      reinterpret_cast<InvokeFuncStorage>(invoke_func),
      std::forward<Functor>(functor), std::forward<Args>(args)...));
}

}  // namespace internal

// Bind as OnceCallback.
template <typename Functor, typename... Args>
inline OnceCallback<MakeUnboundRunType<Functor, Args...>> BindOnce(
    Functor&& functor,
    Args&&... args) {
  static_assert(!internal::IsOnceCallback<std::decay_t<Functor>>() ||
                    (std::is_rvalue_reference<Functor&&>() &&
                     !std::is_const<std::remove_reference_t<Functor>>()),
                "BindOnce requires non-const rvalue for OnceCallback binding."
                " I.e.: base::BindOnce(std::move(callback)).");

  return internal::BindImpl<OnceCallback>(std::forward<Functor>(functor),
                                          std::forward<Args>(args)...);
}

// Bind as RepeatingCallback.
template <typename Functor, typename... Args>
inline RepeatingCallback<MakeUnboundRunType<Functor, Args...>>
BindRepeating(Functor&& functor, Args&&... args) {
  static_assert(
      !internal::IsOnceCallback<std::decay_t<Functor>>(),
      "BindRepeating cannot bind OnceCallback. Use BindOnce with std::move().");

  return internal::BindImpl<RepeatingCallback>(std::forward<Functor>(functor),
                                               std::forward<Args>(args)...);
}

// Unannotated Bind.
// TODO(tzik): Deprecate this and migrate to OnceCallback and
// RepeatingCallback, once they get ready.
template <typename Functor, typename... Args>
inline Callback<MakeUnboundRunType<Functor, Args...>>
Bind(Functor&& functor, Args&&... args) {
  return base::BindRepeating(std::forward<Functor>(functor),
                             std::forward<Args>(args)...);
}

// Special cases for binding to a base::Callback without extra bound arguments.
template <typename Signature>
OnceCallback<Signature> BindOnce(OnceCallback<Signature> callback) {
  return callback;
}

template <typename Signature>
OnceCallback<Signature> BindOnce(RepeatingCallback<Signature> callback) {
  return callback;
}

template <typename Signature>
RepeatingCallback<Signature> BindRepeating(
    RepeatingCallback<Signature> callback) {
  return callback;
}

template <typename Signature>
Callback<Signature> Bind(Callback<Signature> callback) {
  return callback;
}

// Unretained() allows binding a non-refcounted class, and to disable
// refcounting on arguments that are refcounted objects.
//
// EXAMPLE OF Unretained():
//
//   class Foo {
//    public:
//     void func() { cout << "Foo:f" << endl; }
//   };
//
//   // In some function somewhere.
//   Foo foo;
//   OnceClosure foo_callback =
//       BindOnce(&Foo::func, Unretained(&foo));
//   std::move(foo_callback).Run();  // Prints "Foo:f".
//
// Without the Unretained() wrapper on |&foo|, the above call would fail
// to compile because Foo does not support the AddRef() and Release() methods.
template <typename T>
static inline internal::UnretainedWrapper<T> Unretained(T* o) {
  return internal::UnretainedWrapper<T>(o);
}

// RetainedRef() accepts a ref counted object and retains a reference to it.
// When the callback is called, the object is passed as a raw pointer.
//
// EXAMPLE OF RetainedRef():
//
//    void foo(RefCountedBytes* bytes) {}
//
//    scoped_refptr<RefCountedBytes> bytes = ...;
//    OnceClosure callback = BindOnce(&foo, base::RetainedRef(bytes));
//    std::move(callback).Run();
//
// Without RetainedRef, the scoped_refptr would try to implicitly convert to
// a raw pointer and fail compilation:
//
//    OnceClosure callback = BindOnce(&foo, bytes); // ERROR!
template <typename T>
static inline internal::RetainedRefWrapper<T> RetainedRef(T* o) {
  return internal::RetainedRefWrapper<T>(o);
}
template <typename T>
static inline internal::RetainedRefWrapper<T> RetainedRef(scoped_refptr<T> o) {
  return internal::RetainedRefWrapper<T>(std::move(o));
}

// Owned() transfers ownership of an object to the callback resulting from
// bind; the object will be deleted when the callback is deleted.
//
// EXAMPLE OF Owned():
//
//   void foo(int* arg) { cout << *arg << endl }
//
//   int* pn = new int(1);
//   RepeatingClosure foo_callback = BindRepeating(&foo, Owned(pn));
//
//   foo_callback.Run();  // Prints "1"
//   foo_callback.Run();  // Prints "1"
//   *pn = 2;
//   foo_callback.Run();  // Prints "2"
//
//   foo_callback.Reset();  // |pn| is deleted.  Also will happen when
//                          // |foo_callback| goes out of scope.
//
// Without Owned(), someone would have to know to delete |pn| when the last
// reference to the callback is deleted.
template <typename T>
static inline internal::OwnedWrapper<T> Owned(T* o) {
  return internal::OwnedWrapper<T>(o);
}

template <typename T, typename Deleter>
static inline internal::OwnedWrapper<T, Deleter> Owned(
    std::unique_ptr<T, Deleter>&& ptr) {
  return internal::OwnedWrapper<T, Deleter>(std::move(ptr));
}

// Passed() is for transferring movable-but-not-copyable types (eg. unique_ptr)
// through a RepeatingCallback. Logically, this signifies a destructive transfer
// of the state of the argument into the target function. Invoking
// RepeatingCallback::Run() twice on a callback that was created with a Passed()
// argument will CHECK() because the first invocation would have already
// transferred ownership to the target function.
//
// Note that Passed() is not necessary with BindOnce(), as std::move() does the
// same thing. Avoid Passed() in favor of std::move() with BindOnce().
//
// EXAMPLE OF Passed():
//
//   void TakesOwnership(std::unique_ptr<Foo> arg) { }
//   std::unique_ptr<Foo> CreateFoo() { return std::make_unique<Foo>();
//   }
//
//   auto f = std::make_unique<Foo>();
//
//   // |cb| is given ownership of Foo(). |f| is now NULL.
//   // You can use std::move(f) in place of &f, but it's more verbose.
//   RepeatingClosure cb = BindRepeating(&TakesOwnership, Passed(&f));
//
//   // Run was never called so |cb| still owns Foo() and deletes
//   // it on Reset().
//   cb.Reset();
//
//   // |cb| is given a new Foo created by CreateFoo().
//   cb = BindRepeating(&TakesOwnership, Passed(CreateFoo()));
//
//   // |arg| in TakesOwnership() is given ownership of Foo(). |cb|
//   // no longer owns Foo() and, if reset, would not delete Foo().
//   cb.Run();  // Foo() is now transferred to |arg| and deleted.
//   cb.Run();  // This CHECK()s since Foo() already been used once.
//
// We offer 2 syntaxes for calling Passed(). The first takes an rvalue and is
// best suited for use with the return value of a function or other temporary
// rvalues. The second takes a pointer to the scoper and is just syntactic sugar
// to avoid having to write Passed(std::move(scoper)).
//
// Both versions of Passed() prevent T from being an lvalue reference. The first
// via use of enable_if, and the second takes a T* which will not bind to T&.
template <typename T,
          std::enable_if_t<!std::is_lvalue_reference<T>::value>* = nullptr>
static inline internal::PassedWrapper<T> Passed(T&& scoper) {
  return internal::PassedWrapper<T>(std::move(scoper));
}
template <typename T>
static inline internal::PassedWrapper<T> Passed(T* scoper) {
  return internal::PassedWrapper<T>(std::move(*scoper));
}

// IgnoreResult() is used to adapt a function or callback with a return type to
// one with a void return. This is most useful if you have a function with,
// say, a pesky ignorable bool return that you want to use with PostTask or
// something else that expect a callback with a void return.
//
// EXAMPLE OF IgnoreResult():
//
//   int DoSomething(int arg) { cout << arg << endl; }
//
//   // Assign to a callback with a void return type.
//   OnceCallback<void(int)> cb = BindOnce(IgnoreResult(&DoSomething));
//   std::move(cb).Run(1);  // Prints "1".
//
//   // Prints "2" on |ml|.
//   ml->PostTask(FROM_HERE, BindOnce(IgnoreResult(&DoSomething), 2);
template <typename T>
static inline internal::IgnoreResultHelper<T> IgnoreResult(T data) {
  return internal::IgnoreResultHelper<T>(std::move(data));
}

#if defined(OS_MACOSX) && !HAS_FEATURE(objc_arc)

// RetainBlock() is used to adapt an Objective-C block when Automated Reference
// Counting (ARC) is disabled. This is unnecessary when ARC is enabled, as the
// BindOnce and BindRepeating already support blocks then.
//
// EXAMPLE OF RetainBlock():
//
//   // Wrap the block and bind it to a callback.
//   OnceCallback<void(int)> cb =
//       BindOnce(RetainBlock(^(int n) { NSLog(@"%d", n); }));
//   std::move(cb).Run(1);  // Logs "1".
template <typename R, typename... Args>
base::mac::ScopedBlock<R (^)(Args...)> RetainBlock(R (^block)(Args...)) {
  return base::mac::ScopedBlock<R (^)(Args...)>(block,
                                                base::scoped_policy::RETAIN);
}

#endif  // defined(OS_MACOSX) && !HAS_FEATURE(objc_arc)

}  // namespace base

#endif  // BASE_BIND_H_

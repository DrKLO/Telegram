// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// This defines helpful methods for dealing with Callbacks.  Because Callbacks
// are implemented using templates, with a class per callback signature, adding
// methods to Callback<> itself is unattractive (lots of extra code gets
// generated).  Instead, consider adding methods here.

#ifndef BASE_CALLBACK_HELPERS_H_
#define BASE_CALLBACK_HELPERS_H_

#include <type_traits>
#include <utility>

#include "base/atomicops.h"
#include "base/bind.h"
#include "base/callback.h"
#include "base/compiler_specific.h"
#include "base/macros.h"
#include "base/memory/ptr_util.h"

namespace base {

namespace internal {

template <typename T>
struct IsBaseCallbackImpl : std::false_type {};

template <typename R, typename... Args>
struct IsBaseCallbackImpl<OnceCallback<R(Args...)>> : std::true_type {};

template <typename R, typename... Args>
struct IsBaseCallbackImpl<RepeatingCallback<R(Args...)>> : std::true_type {};

}  // namespace internal

template <typename T>
using IsBaseCallback = internal::IsBaseCallbackImpl<std::decay_t<T>>;

// SFINAE friendly enabler allowing to overload methods for both Repeating and
// OnceCallbacks.
//
// Usage:
// template <template <typename> class CallbackType,
//           ... other template args ...,
//           typename = EnableIfIsBaseCallback<CallbackType>>
// void DoStuff(CallbackType<...> cb, ...);
template <template <typename> class CallbackType>
using EnableIfIsBaseCallback =
    std::enable_if_t<IsBaseCallback<CallbackType<void()>>::value>;

namespace internal {

template <typename... Args>
class AdaptCallbackForRepeatingHelper final {
 public:
  explicit AdaptCallbackForRepeatingHelper(OnceCallback<void(Args...)> callback)
      : callback_(std::move(callback)) {
    DCHECK(callback_);
  }

  void Run(Args... args) {
    if (subtle::NoBarrier_AtomicExchange(&has_run_, 1))
      return;
    DCHECK(callback_);
    std::move(callback_).Run(std::forward<Args>(args)...);
  }

 private:
  volatile subtle::Atomic32 has_run_ = 0;
  base::OnceCallback<void(Args...)> callback_;

  DISALLOW_COPY_AND_ASSIGN(AdaptCallbackForRepeatingHelper);
};

}  // namespace internal

// Wraps the given OnceCallback into a RepeatingCallback that relays its
// invocation to the original OnceCallback on the first invocation. The
// following invocations are just ignored.
//
// Note that this deliberately subverts the Once/Repeating paradigm of Callbacks
// but helps ease the migration from old-style Callbacks. Avoid if possible; use
// if necessary for migration. TODO(tzik): Remove it. https://crbug.com/730593
template <typename... Args>
RepeatingCallback<void(Args...)> AdaptCallbackForRepeating(
    OnceCallback<void(Args...)> callback) {
  using Helper = internal::AdaptCallbackForRepeatingHelper<Args...>;
  return base::BindRepeating(&Helper::Run,
                             std::make_unique<Helper>(std::move(callback)));
}

// ScopedClosureRunner is akin to std::unique_ptr<> for Closures. It ensures
// that the Closure is executed no matter how the current scope exits.
// If you are looking for "ScopedCallback", "CallbackRunner", or
// "CallbackScoper" this is the class you want.
class BASE_EXPORT ScopedClosureRunner {
 public:
  ScopedClosureRunner();
  explicit ScopedClosureRunner(OnceClosure closure);
  ~ScopedClosureRunner();

  ScopedClosureRunner(ScopedClosureRunner&& other);

  // Releases the current closure if it's set and replaces it with the closure
  // from |other|.
  ScopedClosureRunner& operator=(ScopedClosureRunner&& other);

  // Calls the current closure and resets it, so it wont be called again.
  void RunAndReset();

  // Replaces closure with the new one releasing the old one without calling it.
  void ReplaceClosure(OnceClosure closure);

  // Releases the Closure without calling.
  OnceClosure Release() WARN_UNUSED_RESULT;

 private:
  OnceClosure closure_;

  DISALLOW_COPY_AND_ASSIGN(ScopedClosureRunner);
};

}  // namespace base

#endif  // BASE_CALLBACK_HELPERS_H_

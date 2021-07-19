// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// This file contains utility functions and classes that help the
// implementation, and management of the Callback objects.

#ifndef BASE_CALLBACK_INTERNAL_H_
#define BASE_CALLBACK_INTERNAL_H_

#include "base/base_export.h"
#include "base/callback_forward.h"
#include "base/macros.h"
#include "base/memory/ref_counted.h"

namespace base {

struct FakeBindState;

namespace internal {

class BindStateBase;
class FinallyExecutorCommon;
class ThenAndCatchExecutorCommon;

template <typename ReturnType>
class PostTaskExecutor;

template <typename Functor, typename... BoundArgs>
struct BindState;

class CallbackBase;
class CallbackBaseCopyable;

struct BindStateBaseRefCountTraits {
  static void Destruct(const BindStateBase*);
};

template <typename T>
using PassingType = std::conditional_t<std::is_scalar<T>::value, T, T&&>;

// BindStateBase is used to provide an opaque handle that the Callback
// class can use to represent a function object with bound arguments.  It
// behaves as an existential type that is used by a corresponding
// DoInvoke function to perform the function execution.  This allows
// us to shield the Callback class from the types of the bound argument via
// "type erasure."
// At the base level, the only task is to add reference counting data. Avoid
// using or inheriting any virtual functions. Creating a vtable for every
// BindState template instantiation results in a lot of bloat. Its only task is
// to call the destructor which can be done with a function pointer.
class BASE_EXPORT BindStateBase
    : public RefCountedThreadSafe<BindStateBase, BindStateBaseRefCountTraits> {
 public:
  REQUIRE_ADOPTION_FOR_REFCOUNTED_TYPE();

  enum CancellationQueryMode {
    IS_CANCELLED,
    MAYBE_VALID,
  };

  using InvokeFuncStorage = void(*)();

 private:
  BindStateBase(InvokeFuncStorage polymorphic_invoke,
                void (*destructor)(const BindStateBase*));
  BindStateBase(InvokeFuncStorage polymorphic_invoke,
                void (*destructor)(const BindStateBase*),
                bool (*query_cancellation_traits)(const BindStateBase*,
                                                  CancellationQueryMode mode));

  ~BindStateBase() = default;

  friend struct BindStateBaseRefCountTraits;
  friend class RefCountedThreadSafe<BindStateBase, BindStateBaseRefCountTraits>;

  friend class CallbackBase;
  friend class CallbackBaseCopyable;

  // Whitelist subclasses that access the destructor of BindStateBase.
  template <typename Functor, typename... BoundArgs>
  friend struct BindState;
  friend struct ::base::FakeBindState;

  bool IsCancelled() const {
    return query_cancellation_traits_(this, IS_CANCELLED);
  }

  bool MaybeValid() const {
    return query_cancellation_traits_(this, MAYBE_VALID);
  }

  // In C++, it is safe to cast function pointers to function pointers of
  // another type. It is not okay to use void*. We create a InvokeFuncStorage
  // that that can store our function pointer, and then cast it back to
  // the original type on usage.
  InvokeFuncStorage polymorphic_invoke_;

  // Pointer to a function that will properly destroy |this|.
  void (*destructor_)(const BindStateBase*);
  bool (*query_cancellation_traits_)(const BindStateBase*,
                                     CancellationQueryMode mode);

  DISALLOW_COPY_AND_ASSIGN(BindStateBase);
};

// Holds the Callback methods that don't require specialization to reduce
// template bloat.
// CallbackBase<MoveOnly> is a direct base class of MoveOnly callbacks, and
// CallbackBase<Copyable> uses CallbackBase<MoveOnly> for its implementation.
class BASE_EXPORT CallbackBase {
 public:
  inline CallbackBase(CallbackBase&& c) noexcept;
  CallbackBase& operator=(CallbackBase&& c) noexcept;

  explicit CallbackBase(const CallbackBaseCopyable& c);
  CallbackBase& operator=(const CallbackBaseCopyable& c);

  explicit CallbackBase(CallbackBaseCopyable&& c) noexcept;
  CallbackBase& operator=(CallbackBaseCopyable&& c) noexcept;

  // Returns true if Callback is null (doesn't refer to anything).
  bool is_null() const { return !bind_state_; }
  explicit operator bool() const { return !is_null(); }

  // Returns true if the callback invocation will be nop due to an cancellation.
  // It's invalid to call this on uninitialized callback.
  //
  // Must be called on the Callback's destination sequence.
  bool IsCancelled() const;

  // If this returns false, the callback invocation will be a nop due to a
  // cancellation. This may(!) still return true, even on a cancelled callback.
  //
  // This function is thread-safe.
  bool MaybeValid() const;

  // Returns the Callback into an uninitialized state.
  void Reset();

 protected:
  friend class FinallyExecutorCommon;
  friend class ThenAndCatchExecutorCommon;

  template <typename ReturnType>
  friend class PostTaskExecutor;

  using InvokeFuncStorage = BindStateBase::InvokeFuncStorage;

  // Returns true if this callback equals |other|. |other| may be null.
  bool EqualsInternal(const CallbackBase& other) const;

  constexpr inline CallbackBase();

  // Allow initializing of |bind_state_| via the constructor to avoid default
  // initialization of the scoped_refptr.
  explicit inline CallbackBase(BindStateBase* bind_state);

  InvokeFuncStorage polymorphic_invoke() const {
    return bind_state_->polymorphic_invoke_;
  }

  // Force the destructor to be instantiated inside this translation unit so
  // that our subclasses will not get inlined versions.  Avoids more template
  // bloat.
  ~CallbackBase();

  scoped_refptr<BindStateBase> bind_state_;
};

constexpr CallbackBase::CallbackBase() = default;
CallbackBase::CallbackBase(CallbackBase&&) noexcept = default;
CallbackBase::CallbackBase(BindStateBase* bind_state)
    : bind_state_(AdoptRef(bind_state)) {}

// CallbackBase<Copyable> is a direct base class of Copyable Callbacks.
class BASE_EXPORT CallbackBaseCopyable : public CallbackBase {
 public:
  CallbackBaseCopyable(const CallbackBaseCopyable& c);
  CallbackBaseCopyable(CallbackBaseCopyable&& c) noexcept = default;
  CallbackBaseCopyable& operator=(const CallbackBaseCopyable& c);
  CallbackBaseCopyable& operator=(CallbackBaseCopyable&& c) noexcept;

 protected:
  constexpr CallbackBaseCopyable() = default;
  explicit CallbackBaseCopyable(BindStateBase* bind_state)
      : CallbackBase(bind_state) {}
  ~CallbackBaseCopyable() = default;
};

}  // namespace internal
}  // namespace base

#endif  // BASE_CALLBACK_INTERNAL_H_

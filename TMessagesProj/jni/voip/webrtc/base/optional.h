// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_OPTIONAL_H_
#define BASE_OPTIONAL_H_

#include <functional>
#include <type_traits>
#include <utility>

#include "base/logging.h"
#include "base/template_util.h"

namespace base {

// Specification:
// http://en.cppreference.com/w/cpp/utility/optional/nullopt_t
struct nullopt_t {
  constexpr explicit nullopt_t(int) {}
};

// Specification:
// http://en.cppreference.com/w/cpp/utility/optional/nullopt
constexpr nullopt_t nullopt(0);

// Forward declaration, which is refered by following helpers.
template <typename T>
class Optional;

namespace internal {

struct DummyUnionMember {};

template <typename T, bool = std::is_trivially_destructible<T>::value>
struct OptionalStorageBase {
  // Provide non-defaulted default ctor to make sure it's not deleted by
  // non-trivial T::T() in the union.
  constexpr OptionalStorageBase() : dummy_() {}

  template <class... Args>
  constexpr explicit OptionalStorageBase(in_place_t, Args&&... args)
      : is_populated_(true), value_(std::forward<Args>(args)...) {}

  // When T is not trivially destructible we must call its
  // destructor before deallocating its memory.
  // Note that this hides the (implicitly declared) move constructor, which
  // would be used for constexpr move constructor in OptionalStorage<T>.
  // It is needed iff T is trivially move constructible. However, the current
  // is_trivially_{copy,move}_constructible implementation requires
  // is_trivially_destructible (which looks a bug, cf:
  // https://gcc.gnu.org/bugzilla/show_bug.cgi?id=51452 and
  // http://cplusplus.github.io/LWG/lwg-active.html#2116), so it is not
  // necessary for this case at the moment. Please see also the destructor
  // comment in "is_trivially_destructible = true" specialization below.
  ~OptionalStorageBase() {
    if (is_populated_)
      value_.~T();
  }

  template <class... Args>
  void Init(Args&&... args) {
    DCHECK(!is_populated_);
    ::new (&value_) T(std::forward<Args>(args)...);
    is_populated_ = true;
  }

  bool is_populated_ = false;
  union {
    // |dummy_| exists so that the union will always be initialized, even when
    // it doesn't contain a value. Union members must be initialized for the
    // constructor to be 'constexpr'. Having a special trivial class for it is
    // better than e.g. using char, because the latter will have to be
    // zero-initialized, and the compiler can't optimize this write away, since
    // it assumes this might be a programmer's invariant. This can also cause
    // problems for conservative GC in Oilpan. Compiler is free to split shared
    // and non-shared parts of the union in separate memory locations (or
    // registers). If conservative GC is triggered at this moment, the stack
    // scanning routine won't find the correct object pointed from
    // Optional<HeapObject*>. This dummy valueless struct lets the compiler know
    // that we don't care about the value of this union member.
    DummyUnionMember dummy_;
    T value_;
  };
};

template <typename T>
struct OptionalStorageBase<T, true /* trivially destructible */> {
  // Provide non-defaulted default ctor to make sure it's not deleted by
  // non-trivial T::T() in the union.
  constexpr OptionalStorageBase() : dummy_() {}

  template <class... Args>
  constexpr explicit OptionalStorageBase(in_place_t, Args&&... args)
      : is_populated_(true), value_(std::forward<Args>(args)...) {}

  // When T is trivially destructible (i.e. its destructor does nothing) there
  // is no need to call it. Implicitly defined destructor is trivial, because
  // both members (bool and union containing only variants which are trivially
  // destructible) are trivially destructible.
  // Explicitly-defaulted destructor is also trivial, but do not use it here,
  // because it hides the implicit move constructor. It is needed to implement
  // constexpr move constructor in OptionalStorage iff T is trivially move
  // constructible. Note that, if T is trivially move constructible, the move
  // constructor of OptionalStorageBase<T> is also implicitly defined and it is
  // trivially move constructor. If T is not trivially move constructible,
  // "not declaring move constructor without destructor declaration" here means
  // "delete move constructor", which works because any move constructor of
  // OptionalStorage will not refer to it in that case.

  template <class... Args>
  void Init(Args&&... args) {
    DCHECK(!is_populated_);
    ::new (&value_) T(std::forward<Args>(args)...);
    is_populated_ = true;
  }

  bool is_populated_ = false;
  union {
    // |dummy_| exists so that the union will always be initialized, even when
    // it doesn't contain a value. Union members must be initialized for the
    // constructor to be 'constexpr'. Having a special trivial class for it is
    // better than e.g. using char, because the latter will have to be
    // zero-initialized, and the compiler can't optimize this write away, since
    // it assumes this might be a programmer's invariant. This can also cause
    // problems for conservative GC in Oilpan. Compiler is free to split shared
    // and non-shared parts of the union in separate memory locations (or
    // registers). If conservative GC is triggered at this moment, the stack
    // scanning routine won't find the correct object pointed from
    // Optional<HeapObject*>. This dummy valueless struct lets the compiler know
    // that we don't care about the value of this union member.
    DummyUnionMember dummy_;
    T value_;
  };
};

// Implement conditional constexpr copy and move constructors. These are
// constexpr if is_trivially_{copy,move}_constructible<T>::value is true
// respectively. If each is true, the corresponding constructor is defined as
// "= default;", which generates a constexpr constructor (In this case,
// the condition of constexpr-ness is satisfied because the base class also has
// compiler generated constexpr {copy,move} constructors). Note that
// placement-new is prohibited in constexpr.
template <typename T,
          bool = is_trivially_copy_constructible<T>::value,
          bool = std::is_trivially_move_constructible<T>::value>
struct OptionalStorage : OptionalStorageBase<T> {
  // This is no trivially {copy,move} constructible case. Other cases are
  // defined below as specializations.

  // Accessing the members of template base class requires explicit
  // declaration.
  using OptionalStorageBase<T>::is_populated_;
  using OptionalStorageBase<T>::value_;
  using OptionalStorageBase<T>::Init;

  // Inherit constructors (specifically, the in_place constructor).
  using OptionalStorageBase<T>::OptionalStorageBase;

  // User defined constructor deletes the default constructor.
  // Define it explicitly.
  OptionalStorage() = default;

  OptionalStorage(const OptionalStorage& other) {
    if (other.is_populated_)
      Init(other.value_);
  }

  OptionalStorage(OptionalStorage&& other) noexcept(
      std::is_nothrow_move_constructible<T>::value) {
    if (other.is_populated_)
      Init(std::move(other.value_));
  }
};

template <typename T>
struct OptionalStorage<T,
                       true /* trivially copy constructible */,
                       false /* trivially move constructible */>
    : OptionalStorageBase<T> {
  using OptionalStorageBase<T>::is_populated_;
  using OptionalStorageBase<T>::value_;
  using OptionalStorageBase<T>::Init;
  using OptionalStorageBase<T>::OptionalStorageBase;

  OptionalStorage() = default;
  OptionalStorage(const OptionalStorage& other) = default;

  OptionalStorage(OptionalStorage&& other) noexcept(
      std::is_nothrow_move_constructible<T>::value) {
    if (other.is_populated_)
      Init(std::move(other.value_));
  }
};

template <typename T>
struct OptionalStorage<T,
                       false /* trivially copy constructible */,
                       true /* trivially move constructible */>
    : OptionalStorageBase<T> {
  using OptionalStorageBase<T>::is_populated_;
  using OptionalStorageBase<T>::value_;
  using OptionalStorageBase<T>::Init;
  using OptionalStorageBase<T>::OptionalStorageBase;

  OptionalStorage() = default;
  OptionalStorage(OptionalStorage&& other) = default;

  OptionalStorage(const OptionalStorage& other) {
    if (other.is_populated_)
      Init(other.value_);
  }
};

template <typename T>
struct OptionalStorage<T,
                       true /* trivially copy constructible */,
                       true /* trivially move constructible */>
    : OptionalStorageBase<T> {
  // If both trivially {copy,move} constructible are true, it is not necessary
  // to use user-defined constructors. So, just inheriting constructors
  // from the base class works.
  using OptionalStorageBase<T>::OptionalStorageBase;
};

// Base class to support conditionally usable copy-/move- constructors
// and assign operators.
template <typename T>
class OptionalBase {
  // This class provides implementation rather than public API, so everything
  // should be hidden. Often we use composition, but we cannot in this case
  // because of C++ language restriction.
 protected:
  constexpr OptionalBase() = default;
  constexpr OptionalBase(const OptionalBase& other) = default;
  constexpr OptionalBase(OptionalBase&& other) = default;

  template <class... Args>
  constexpr explicit OptionalBase(in_place_t, Args&&... args)
      : storage_(in_place, std::forward<Args>(args)...) {}

  // Implementation of converting constructors.
  template <typename U>
  explicit OptionalBase(const OptionalBase<U>& other) {
    if (other.storage_.is_populated_)
      storage_.Init(other.storage_.value_);
  }

  template <typename U>
  explicit OptionalBase(OptionalBase<U>&& other) {
    if (other.storage_.is_populated_)
      storage_.Init(std::move(other.storage_.value_));
  }

  ~OptionalBase() = default;

  OptionalBase& operator=(const OptionalBase& other) {
    CopyAssign(other);
    return *this;
  }

  OptionalBase& operator=(OptionalBase&& other) noexcept(
      std::is_nothrow_move_assignable<T>::value&&
          std::is_nothrow_move_constructible<T>::value) {
    MoveAssign(std::move(other));
    return *this;
  }

  template <typename U>
  void CopyAssign(const OptionalBase<U>& other) {
    if (other.storage_.is_populated_)
      InitOrAssign(other.storage_.value_);
    else
      FreeIfNeeded();
  }

  template <typename U>
  void MoveAssign(OptionalBase<U>&& other) {
    if (other.storage_.is_populated_)
      InitOrAssign(std::move(other.storage_.value_));
    else
      FreeIfNeeded();
  }

  template <typename U>
  void InitOrAssign(U&& value) {
    if (storage_.is_populated_)
      storage_.value_ = std::forward<U>(value);
    else
      storage_.Init(std::forward<U>(value));
  }

  void FreeIfNeeded() {
    if (!storage_.is_populated_)
      return;
    storage_.value_.~T();
    storage_.is_populated_ = false;
  }

  // For implementing conversion, allow access to other typed OptionalBase
  // class.
  template <typename U>
  friend class OptionalBase;

  OptionalStorage<T> storage_;
};

// The following {Copy,Move}{Constructible,Assignable} structs are helpers to
// implement constructor/assign-operator overloading. Specifically, if T is
// is not movable but copyable, Optional<T>'s move constructor should not
// participate in overload resolution. This inheritance trick implements that.
template <bool is_copy_constructible>
struct CopyConstructible {};

template <>
struct CopyConstructible<false> {
  constexpr CopyConstructible() = default;
  constexpr CopyConstructible(const CopyConstructible&) = delete;
  constexpr CopyConstructible(CopyConstructible&&) = default;
  CopyConstructible& operator=(const CopyConstructible&) = default;
  CopyConstructible& operator=(CopyConstructible&&) = default;
};

template <bool is_move_constructible>
struct MoveConstructible {};

template <>
struct MoveConstructible<false> {
  constexpr MoveConstructible() = default;
  constexpr MoveConstructible(const MoveConstructible&) = default;
  constexpr MoveConstructible(MoveConstructible&&) = delete;
  MoveConstructible& operator=(const MoveConstructible&) = default;
  MoveConstructible& operator=(MoveConstructible&&) = default;
};

template <bool is_copy_assignable>
struct CopyAssignable {};

template <>
struct CopyAssignable<false> {
  constexpr CopyAssignable() = default;
  constexpr CopyAssignable(const CopyAssignable&) = default;
  constexpr CopyAssignable(CopyAssignable&&) = default;
  CopyAssignable& operator=(const CopyAssignable&) = delete;
  CopyAssignable& operator=(CopyAssignable&&) = default;
};

template <bool is_move_assignable>
struct MoveAssignable {};

template <>
struct MoveAssignable<false> {
  constexpr MoveAssignable() = default;
  constexpr MoveAssignable(const MoveAssignable&) = default;
  constexpr MoveAssignable(MoveAssignable&&) = default;
  MoveAssignable& operator=(const MoveAssignable&) = default;
  MoveAssignable& operator=(MoveAssignable&&) = delete;
};

// Helper to conditionally enable converting constructors and assign operators.
template <typename T, typename U>
using IsConvertibleFromOptional =
    disjunction<std::is_constructible<T, Optional<U>&>,
                std::is_constructible<T, const Optional<U>&>,
                std::is_constructible<T, Optional<U>&&>,
                std::is_constructible<T, const Optional<U>&&>,
                std::is_convertible<Optional<U>&, T>,
                std::is_convertible<const Optional<U>&, T>,
                std::is_convertible<Optional<U>&&, T>,
                std::is_convertible<const Optional<U>&&, T>>;

template <typename T, typename U>
using IsAssignableFromOptional =
    disjunction<IsConvertibleFromOptional<T, U>,
                std::is_assignable<T&, Optional<U>&>,
                std::is_assignable<T&, const Optional<U>&>,
                std::is_assignable<T&, Optional<U>&&>,
                std::is_assignable<T&, const Optional<U>&&>>;

// Forward compatibility for C++17.
// Introduce one more deeper nested namespace to avoid leaking using std::swap.
namespace swappable_impl {
using std::swap;

struct IsSwappableImpl {
  // Tests if swap can be called. Check<T&>(0) returns true_type iff swap
  // is available for T. Otherwise, Check's overload resolution falls back
  // to Check(...) declared below thanks to SFINAE, so returns false_type.
  template <typename T>
  static auto Check(int)
      -> decltype(swap(std::declval<T>(), std::declval<T>()), std::true_type());

  template <typename T>
  static std::false_type Check(...);
};
}  // namespace swappable_impl

template <typename T>
struct IsSwappable : decltype(swappable_impl::IsSwappableImpl::Check<T&>(0)) {};

// Forward compatibility for C++20.
template <typename T>
using RemoveCvRefT = std::remove_cv_t<std::remove_reference_t<T>>;

}  // namespace internal

// On Windows, by default, empty-base class optimization does not work,
// which means even if the base class is empty struct, it still consumes one
// byte for its body. __declspec(empty_bases) enables the optimization.
// cf)
// https://blogs.msdn.microsoft.com/vcblog/2016/03/30/optimizing-the-layout-of-empty-base-classes-in-vs2015-update-2-3/
#ifdef OS_WIN
#define OPTIONAL_DECLSPEC_EMPTY_BASES __declspec(empty_bases)
#else
#define OPTIONAL_DECLSPEC_EMPTY_BASES
#endif

// base::Optional is a Chromium version of the C++17 optional class:
// std::optional documentation:
// http://en.cppreference.com/w/cpp/utility/optional
// Chromium documentation:
// https://chromium.googlesource.com/chromium/src/+/master/docs/optional.md
//
// These are the differences between the specification and the implementation:
// - Constructors do not use 'constexpr' as it is a C++14 extension.
// - 'constexpr' might be missing in some places for reasons specified locally.
// - No exceptions are thrown, because they are banned from Chromium.
//   Marked noexcept for only move constructor and move assign operators.
// - All the non-members are in the 'base' namespace instead of 'std'.
//
// Note that T cannot have a constructor T(Optional<T>) etc. Optional<T> checks
// T's constructor (specifically via IsConvertibleFromOptional), and in the
// check whether T can be constructible from Optional<T>, which is recursive
// so it does not work. As of Feb 2018, std::optional C++17 implementation in
// both clang and gcc has same limitation. MSVC SFINAE looks to have different
// behavior, but anyway it reports an error, too.
template <typename T>
class OPTIONAL_DECLSPEC_EMPTY_BASES Optional
    : public internal::OptionalBase<T>,
      public internal::CopyConstructible<std::is_copy_constructible<T>::value>,
      public internal::MoveConstructible<std::is_move_constructible<T>::value>,
      public internal::CopyAssignable<std::is_copy_constructible<T>::value &&
                                      std::is_copy_assignable<T>::value>,
      public internal::MoveAssignable<std::is_move_constructible<T>::value &&
                                      std::is_move_assignable<T>::value> {
 private:
  // Disable some versions of T that are ill-formed.
  // See: https://timsong-cpp.github.io/cppwp/n4659/optional#syn-1
  static_assert(
      !std::is_same<internal::RemoveCvRefT<T>, in_place_t>::value,
      "instantiation of base::Optional with in_place_t is ill-formed");
  static_assert(!std::is_same<internal::RemoveCvRefT<T>, nullopt_t>::value,
                "instantiation of base::Optional with nullopt_t is ill-formed");
  static_assert(
      !std::is_reference<T>::value,
      "instantiation of base::Optional with a reference type is ill-formed");
  // See: https://timsong-cpp.github.io/cppwp/n4659/optional#optional-3
  static_assert(std::is_destructible<T>::value,
                "instantiation of base::Optional with a non-destructible type "
                "is ill-formed");
  // Arrays are explicitly disallowed because for arrays of known bound
  // is_destructible is of undefined value.
  // See: https://en.cppreference.com/w/cpp/types/is_destructible
  static_assert(
      !std::is_array<T>::value,
      "instantiation of base::Optional with an array type is ill-formed");

 public:
#undef OPTIONAL_DECLSPEC_EMPTY_BASES
  using value_type = T;

  // Defer default/copy/move constructor implementation to OptionalBase.
  constexpr Optional() = default;
  constexpr Optional(const Optional& other) = default;
  constexpr Optional(Optional&& other) noexcept(
      std::is_nothrow_move_constructible<T>::value) = default;

  constexpr Optional(nullopt_t) {}  // NOLINT(runtime/explicit)

  // Converting copy constructor. "explicit" only if
  // std::is_convertible<const U&, T>::value is false. It is implemented by
  // declaring two almost same constructors, but that condition in enable_if_t
  // is different, so that either one is chosen, thanks to SFINAE.
  template <
      typename U,
      std::enable_if_t<std::is_constructible<T, const U&>::value &&
                           !internal::IsConvertibleFromOptional<T, U>::value &&
                           std::is_convertible<const U&, T>::value,
                       bool> = false>
  Optional(const Optional<U>& other) : internal::OptionalBase<T>(other) {}

  template <
      typename U,
      std::enable_if_t<std::is_constructible<T, const U&>::value &&
                           !internal::IsConvertibleFromOptional<T, U>::value &&
                           !std::is_convertible<const U&, T>::value,
                       bool> = false>
  explicit Optional(const Optional<U>& other)
      : internal::OptionalBase<T>(other) {}

  // Converting move constructor. Similar to converting copy constructor,
  // declaring two (explicit and non-explicit) constructors.
  template <
      typename U,
      std::enable_if_t<std::is_constructible<T, U&&>::value &&
                           !internal::IsConvertibleFromOptional<T, U>::value &&
                           std::is_convertible<U&&, T>::value,
                       bool> = false>
  Optional(Optional<U>&& other) : internal::OptionalBase<T>(std::move(other)) {}

  template <
      typename U,
      std::enable_if_t<std::is_constructible<T, U&&>::value &&
                           !internal::IsConvertibleFromOptional<T, U>::value &&
                           !std::is_convertible<U&&, T>::value,
                       bool> = false>
  explicit Optional(Optional<U>&& other)
      : internal::OptionalBase<T>(std::move(other)) {}

  template <class... Args>
  constexpr explicit Optional(in_place_t, Args&&... args)
      : internal::OptionalBase<T>(in_place, std::forward<Args>(args)...) {}

  template <
      class U,
      class... Args,
      class = std::enable_if_t<std::is_constructible<value_type,
                                                     std::initializer_list<U>&,
                                                     Args...>::value>>
  constexpr explicit Optional(in_place_t,
                              std::initializer_list<U> il,
                              Args&&... args)
      : internal::OptionalBase<T>(in_place, il, std::forward<Args>(args)...) {}

  // Forward value constructor. Similar to converting constructors,
  // conditionally explicit.
  template <
      typename U = value_type,
      std::enable_if_t<
          std::is_constructible<T, U&&>::value &&
              !std::is_same<internal::RemoveCvRefT<U>, in_place_t>::value &&
              !std::is_same<internal::RemoveCvRefT<U>, Optional<T>>::value &&
              std::is_convertible<U&&, T>::value,
          bool> = false>
  constexpr Optional(U&& value)
      : internal::OptionalBase<T>(in_place, std::forward<U>(value)) {}

  template <
      typename U = value_type,
      std::enable_if_t<
          std::is_constructible<T, U&&>::value &&
              !std::is_same<internal::RemoveCvRefT<U>, in_place_t>::value &&
              !std::is_same<internal::RemoveCvRefT<U>, Optional<T>>::value &&
              !std::is_convertible<U&&, T>::value,
          bool> = false>
  constexpr explicit Optional(U&& value)
      : internal::OptionalBase<T>(in_place, std::forward<U>(value)) {}

  ~Optional() = default;

  // Defer copy-/move- assign operator implementation to OptionalBase.
  Optional& operator=(const Optional& other) = default;
  Optional& operator=(Optional&& other) noexcept(
      std::is_nothrow_move_assignable<T>::value&&
          std::is_nothrow_move_constructible<T>::value) = default;

  Optional& operator=(nullopt_t) {
    FreeIfNeeded();
    return *this;
  }

  // Perfect-forwarded assignment.
  template <typename U>
  std::enable_if_t<
      !std::is_same<internal::RemoveCvRefT<U>, Optional<T>>::value &&
          std::is_constructible<T, U>::value &&
          std::is_assignable<T&, U>::value &&
          (!std::is_scalar<T>::value ||
           !std::is_same<std::decay_t<U>, T>::value),
      Optional&>
  operator=(U&& value) {
    InitOrAssign(std::forward<U>(value));
    return *this;
  }

  // Copy assign the state of other.
  template <typename U>
  std::enable_if_t<!internal::IsAssignableFromOptional<T, U>::value &&
                       std::is_constructible<T, const U&>::value &&
                       std::is_assignable<T&, const U&>::value,
                   Optional&>
  operator=(const Optional<U>& other) {
    CopyAssign(other);
    return *this;
  }

  // Move assign the state of other.
  template <typename U>
  std::enable_if_t<!internal::IsAssignableFromOptional<T, U>::value &&
                       std::is_constructible<T, U>::value &&
                       std::is_assignable<T&, U>::value,
                   Optional&>
  operator=(Optional<U>&& other) {
    MoveAssign(std::move(other));
    return *this;
  }

  constexpr const T* operator->() const {
    CHECK(storage_.is_populated_);
    return &storage_.value_;
  }

  constexpr T* operator->() {
    CHECK(storage_.is_populated_);
    return &storage_.value_;
  }

  constexpr const T& operator*() const & {
    CHECK(storage_.is_populated_);
    return storage_.value_;
  }

  constexpr T& operator*() & {
    CHECK(storage_.is_populated_);
    return storage_.value_;
  }

  constexpr const T&& operator*() const && {
    CHECK(storage_.is_populated_);
    return std::move(storage_.value_);
  }

  constexpr T&& operator*() && {
    CHECK(storage_.is_populated_);
    return std::move(storage_.value_);
  }

  constexpr explicit operator bool() const { return storage_.is_populated_; }

  constexpr bool has_value() const { return storage_.is_populated_; }

  constexpr T& value() & {
    CHECK(storage_.is_populated_);
    return storage_.value_;
  }

  constexpr const T& value() const & {
    CHECK(storage_.is_populated_);
    return storage_.value_;
  }

  constexpr T&& value() && {
    CHECK(storage_.is_populated_);
    return std::move(storage_.value_);
  }

  constexpr const T&& value() const && {
    CHECK(storage_.is_populated_);
    return std::move(storage_.value_);
  }

  template <class U>
  constexpr T value_or(U&& default_value) const& {
    // TODO(mlamouri): add the following assert when possible:
    // static_assert(std::is_copy_constructible<T>::value,
    //               "T must be copy constructible");
    static_assert(std::is_convertible<U, T>::value,
                  "U must be convertible to T");
    return storage_.is_populated_
               ? storage_.value_
               : static_cast<T>(std::forward<U>(default_value));
  }

  template <class U>
  constexpr T value_or(U&& default_value) && {
    // TODO(mlamouri): add the following assert when possible:
    // static_assert(std::is_move_constructible<T>::value,
    //               "T must be move constructible");
    static_assert(std::is_convertible<U, T>::value,
                  "U must be convertible to T");
    return storage_.is_populated_
               ? std::move(storage_.value_)
               : static_cast<T>(std::forward<U>(default_value));
  }

  void swap(Optional& other) {
    if (!storage_.is_populated_ && !other.storage_.is_populated_)
      return;

    if (storage_.is_populated_ != other.storage_.is_populated_) {
      if (storage_.is_populated_) {
        other.storage_.Init(std::move(storage_.value_));
        FreeIfNeeded();
      } else {
        storage_.Init(std::move(other.storage_.value_));
        other.FreeIfNeeded();
      }
      return;
    }

    DCHECK(storage_.is_populated_ && other.storage_.is_populated_);
    using std::swap;
    swap(**this, *other);
  }

  void reset() { FreeIfNeeded(); }

  template <class... Args>
  T& emplace(Args&&... args) {
    FreeIfNeeded();
    storage_.Init(std::forward<Args>(args)...);
    return storage_.value_;
  }

  template <class U, class... Args>
  std::enable_if_t<
      std::is_constructible<T, std::initializer_list<U>&, Args&&...>::value,
      T&>
  emplace(std::initializer_list<U> il, Args&&... args) {
    FreeIfNeeded();
    storage_.Init(il, std::forward<Args>(args)...);
    return storage_.value_;
  }

 private:
  // Accessing template base class's protected member needs explicit
  // declaration to do so.
  using internal::OptionalBase<T>::CopyAssign;
  using internal::OptionalBase<T>::FreeIfNeeded;
  using internal::OptionalBase<T>::InitOrAssign;
  using internal::OptionalBase<T>::MoveAssign;
  using internal::OptionalBase<T>::storage_;
};

// Here after defines comparation operators. The definition follows
// http://en.cppreference.com/w/cpp/utility/optional/operator_cmp
// while bool() casting is replaced by has_value() to meet the chromium
// style guide.
template <class T, class U>
constexpr bool operator==(const Optional<T>& lhs, const Optional<U>& rhs) {
  if (lhs.has_value() != rhs.has_value())
    return false;
  if (!lhs.has_value())
    return true;
  return *lhs == *rhs;
}

template <class T, class U>
constexpr bool operator!=(const Optional<T>& lhs, const Optional<U>& rhs) {
  if (lhs.has_value() != rhs.has_value())
    return true;
  if (!lhs.has_value())
    return false;
  return *lhs != *rhs;
}

template <class T, class U>
constexpr bool operator<(const Optional<T>& lhs, const Optional<U>& rhs) {
  if (!rhs.has_value())
    return false;
  if (!lhs.has_value())
    return true;
  return *lhs < *rhs;
}

template <class T, class U>
constexpr bool operator<=(const Optional<T>& lhs, const Optional<U>& rhs) {
  if (!lhs.has_value())
    return true;
  if (!rhs.has_value())
    return false;
  return *lhs <= *rhs;
}

template <class T, class U>
constexpr bool operator>(const Optional<T>& lhs, const Optional<U>& rhs) {
  if (!lhs.has_value())
    return false;
  if (!rhs.has_value())
    return true;
  return *lhs > *rhs;
}

template <class T, class U>
constexpr bool operator>=(const Optional<T>& lhs, const Optional<U>& rhs) {
  if (!rhs.has_value())
    return true;
  if (!lhs.has_value())
    return false;
  return *lhs >= *rhs;
}

template <class T>
constexpr bool operator==(const Optional<T>& opt, nullopt_t) {
  return !opt;
}

template <class T>
constexpr bool operator==(nullopt_t, const Optional<T>& opt) {
  return !opt;
}

template <class T>
constexpr bool operator!=(const Optional<T>& opt, nullopt_t) {
  return opt.has_value();
}

template <class T>
constexpr bool operator!=(nullopt_t, const Optional<T>& opt) {
  return opt.has_value();
}

template <class T>
constexpr bool operator<(const Optional<T>& opt, nullopt_t) {
  return false;
}

template <class T>
constexpr bool operator<(nullopt_t, const Optional<T>& opt) {
  return opt.has_value();
}

template <class T>
constexpr bool operator<=(const Optional<T>& opt, nullopt_t) {
  return !opt;
}

template <class T>
constexpr bool operator<=(nullopt_t, const Optional<T>& opt) {
  return true;
}

template <class T>
constexpr bool operator>(const Optional<T>& opt, nullopt_t) {
  return opt.has_value();
}

template <class T>
constexpr bool operator>(nullopt_t, const Optional<T>& opt) {
  return false;
}

template <class T>
constexpr bool operator>=(const Optional<T>& opt, nullopt_t) {
  return true;
}

template <class T>
constexpr bool operator>=(nullopt_t, const Optional<T>& opt) {
  return !opt;
}

template <class T, class U>
constexpr bool operator==(const Optional<T>& opt, const U& value) {
  return opt.has_value() ? *opt == value : false;
}

template <class T, class U>
constexpr bool operator==(const U& value, const Optional<T>& opt) {
  return opt.has_value() ? value == *opt : false;
}

template <class T, class U>
constexpr bool operator!=(const Optional<T>& opt, const U& value) {
  return opt.has_value() ? *opt != value : true;
}

template <class T, class U>
constexpr bool operator!=(const U& value, const Optional<T>& opt) {
  return opt.has_value() ? value != *opt : true;
}

template <class T, class U>
constexpr bool operator<(const Optional<T>& opt, const U& value) {
  return opt.has_value() ? *opt < value : true;
}

template <class T, class U>
constexpr bool operator<(const U& value, const Optional<T>& opt) {
  return opt.has_value() ? value < *opt : false;
}

template <class T, class U>
constexpr bool operator<=(const Optional<T>& opt, const U& value) {
  return opt.has_value() ? *opt <= value : true;
}

template <class T, class U>
constexpr bool operator<=(const U& value, const Optional<T>& opt) {
  return opt.has_value() ? value <= *opt : false;
}

template <class T, class U>
constexpr bool operator>(const Optional<T>& opt, const U& value) {
  return opt.has_value() ? *opt > value : false;
}

template <class T, class U>
constexpr bool operator>(const U& value, const Optional<T>& opt) {
  return opt.has_value() ? value > *opt : true;
}

template <class T, class U>
constexpr bool operator>=(const Optional<T>& opt, const U& value) {
  return opt.has_value() ? *opt >= value : false;
}

template <class T, class U>
constexpr bool operator>=(const U& value, const Optional<T>& opt) {
  return opt.has_value() ? value >= *opt : true;
}

template <class T>
constexpr Optional<std::decay_t<T>> make_optional(T&& value) {
  return Optional<std::decay_t<T>>(std::forward<T>(value));
}

template <class T, class... Args>
constexpr Optional<T> make_optional(Args&&... args) {
  return Optional<T>(in_place, std::forward<Args>(args)...);
}

template <class T, class U, class... Args>
constexpr Optional<T> make_optional(std::initializer_list<U> il,
                                    Args&&... args) {
  return Optional<T>(in_place, il, std::forward<Args>(args)...);
}

// Partial specialization for a function template is not allowed. Also, it is
// not allowed to add overload function to std namespace, while it is allowed
// to specialize the template in std. Thus, swap() (kind of) overloading is
// defined in base namespace, instead.
template <class T>
std::enable_if_t<std::is_move_constructible<T>::value &&
                 internal::IsSwappable<T>::value>
swap(Optional<T>& lhs, Optional<T>& rhs) {
  lhs.swap(rhs);
}

}  // namespace base

namespace std {

template <class T>
struct hash<base::Optional<T>> {
  size_t operator()(const base::Optional<T>& opt) const {
    return opt == base::nullopt ? 0 : std::hash<T>()(*opt);
  }
};

}  // namespace std

#endif  // BASE_OPTIONAL_H_

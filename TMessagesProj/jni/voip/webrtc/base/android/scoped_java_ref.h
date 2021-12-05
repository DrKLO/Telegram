// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ANDROID_SCOPED_JAVA_REF_H_
#define BASE_ANDROID_SCOPED_JAVA_REF_H_

#include <jni.h>
#include <stddef.h>

#include <type_traits>
#include <utility>

#include "base/base_export.h"
#include "base/logging.h"
#include "base/macros.h"

namespace base {
namespace android {

// Creates a new local reference frame, in which at least a given number of
// local references can be created. Note that local references already created
// in previous local frames are still valid in the current local frame.
class BASE_EXPORT ScopedJavaLocalFrame {
 public:
  explicit ScopedJavaLocalFrame(JNIEnv* env);
  ScopedJavaLocalFrame(JNIEnv* env, int capacity);
  ~ScopedJavaLocalFrame();

 private:
  // This class is only good for use on the thread it was created on so
  // it's safe to cache the non-threadsafe JNIEnv* inside this object.
  JNIEnv* env_;

  DISALLOW_COPY_AND_ASSIGN(ScopedJavaLocalFrame);
};

// Forward declare the generic java reference template class.
template <typename T>
class JavaRef;

// Template specialization of JavaRef, which acts as the base class for all
// other JavaRef<> template types. This allows you to e.g. pass
// ScopedJavaLocalRef<jstring> into a function taking const JavaRef<jobject>&
template <>
class BASE_EXPORT JavaRef<jobject> {
 public:
  // Initializes a null reference.
  constexpr JavaRef() {}

  // Allow nullptr to be converted to JavaRef. This avoids having to declare an
  // empty JavaRef just to pass null to a function, and makes C++ "nullptr" and
  // Java "null" equivalent.
  constexpr JavaRef(std::nullptr_t) {}

  // Public to allow destruction of null JavaRef objects.
  ~JavaRef() {}

  // TODO(torne): maybe rename this to get() for consistency with unique_ptr
  // once there's fewer unnecessary uses of it in the codebase.
  jobject obj() const { return obj_; }

  explicit operator bool() const { return obj_ != nullptr; }

  // Deprecated. Just use bool conversion.
  // TODO(torne): replace usage and remove this.
  bool is_null() const { return obj_ == nullptr; }

 protected:
// Takes ownership of the |obj| reference passed; requires it to be a local
// reference type.
#if DCHECK_IS_ON()
  // Implementation contains a DCHECK; implement out-of-line when DCHECK_IS_ON.
  JavaRef(JNIEnv* env, jobject obj);
#else
  JavaRef(JNIEnv* env, jobject obj) : obj_(obj) {}
#endif

  // Used for move semantics. obj_ must have been released first if non-null.
  void steal(JavaRef&& other) {
    obj_ = other.obj_;
    other.obj_ = nullptr;
  }

  // The following are implementation detail convenience methods, for
  // use by the sub-classes.
  JNIEnv* SetNewLocalRef(JNIEnv* env, jobject obj);
  void SetNewGlobalRef(JNIEnv* env, jobject obj);
  void ResetLocalRef(JNIEnv* env);
  void ResetGlobalRef();
  jobject ReleaseInternal();

 private:
  jobject obj_ = nullptr;

  DISALLOW_COPY_AND_ASSIGN(JavaRef);
};

// Forward declare the object array reader for the convenience function.
template <typename T>
class JavaObjectArrayReader;

// Generic base class for ScopedJavaLocalRef and ScopedJavaGlobalRef. Useful
// for allowing functions to accept a reference without having to mandate
// whether it is a local or global type.
template <typename T>
class JavaRef : public JavaRef<jobject> {
 public:
  constexpr JavaRef() {}
  constexpr JavaRef(std::nullptr_t) {}
  ~JavaRef() {}

  T obj() const { return static_cast<T>(JavaRef<jobject>::obj()); }

  // Get a JavaObjectArrayReader for the array pointed to by this reference.
  // Only defined for JavaRef<jobjectArray>.
  // You must pass the type of the array elements (usually jobject) as the
  // template parameter.
  template <typename ElementType,
            typename T_ = T,
            typename = std::enable_if_t<std::is_same<T_, jobjectArray>::value>>
  JavaObjectArrayReader<ElementType> ReadElements() const {
    return JavaObjectArrayReader<ElementType>(*this);
  }

 protected:
  JavaRef(JNIEnv* env, T obj) : JavaRef<jobject>(env, obj) {}

 private:
  DISALLOW_COPY_AND_ASSIGN(JavaRef);
};

// Holds a local reference to a JNI method parameter.
// Method parameters should not be deleted, and so this class exists purely to
// wrap them as a JavaRef<T> in the JNI binding generator. Do not create
// instances manually.
template <typename T>
class JavaParamRef : public JavaRef<T> {
 public:
  // Assumes that |obj| is a parameter passed to a JNI method from Java.
  // Does not assume ownership as parameters should not be deleted.
  JavaParamRef(JNIEnv* env, T obj) : JavaRef<T>(env, obj) {}

  // Allow nullptr to be converted to JavaParamRef. Some unit tests call JNI
  // methods directly from C++ and pass null for objects which are not actually
  // used by the implementation (e.g. the caller object); allow this to keep
  // working.
  JavaParamRef(std::nullptr_t) {}

  ~JavaParamRef() {}

  // TODO(torne): remove this cast once we're using JavaRef consistently.
  // http://crbug.com/506850
  operator T() const { return JavaRef<T>::obj(); }

 private:
  DISALLOW_COPY_AND_ASSIGN(JavaParamRef);
};

// Holds a local reference to a Java object. The local reference is scoped
// to the lifetime of this object.
// Instances of this class may hold onto any JNIEnv passed into it until
// destroyed. Therefore, since a JNIEnv is only suitable for use on a single
// thread, objects of this class must be created, used, and destroyed, on a
// single thread.
// Therefore, this class should only be used as a stack-based object and from a
// single thread. If you wish to have the reference outlive the current
// callstack (e.g. as a class member) or you wish to pass it across threads,
// use a ScopedJavaGlobalRef instead.
template <typename T>
class ScopedJavaLocalRef : public JavaRef<T> {
 public:
  // Take ownership of a bare jobject. This does not create a new reference.
  // This should only be used by JNI helper functions, or in cases where code
  // must call JNIEnv methods directly.
  static ScopedJavaLocalRef Adopt(JNIEnv* env, T obj) {
    return ScopedJavaLocalRef(env, obj);
  }

  constexpr ScopedJavaLocalRef() {}
  constexpr ScopedJavaLocalRef(std::nullptr_t) {}

  // Copy constructor. This is required in addition to the copy conversion
  // constructor below.
  ScopedJavaLocalRef(const ScopedJavaLocalRef& other) : env_(other.env_) {
    JavaRef<T>::SetNewLocalRef(env_, other.obj());
  }

  // Copy conversion constructor.
  template <typename U,
            typename = std::enable_if_t<std::is_convertible<U, T>::value>>
  ScopedJavaLocalRef(const ScopedJavaLocalRef<U>& other) : env_(other.env_) {
    JavaRef<T>::SetNewLocalRef(env_, other.obj());
  }

  // Move constructor. This is required in addition to the move conversion
  // constructor below.
  ScopedJavaLocalRef(ScopedJavaLocalRef&& other) : env_(other.env_) {
    JavaRef<T>::steal(std::move(other));
  }

  // Move conversion constructor.
  template <typename U,
            typename = std::enable_if_t<std::is_convertible<U, T>::value>>
  ScopedJavaLocalRef(ScopedJavaLocalRef<U>&& other) : env_(other.env_) {
    JavaRef<T>::steal(std::move(other));
  }

  // Constructor for other JavaRef types.
  explicit ScopedJavaLocalRef(const JavaRef<T>& other) { Reset(other); }

  // Assumes that |obj| is a local reference to a Java object and takes
  // ownership of this local reference.
  // TODO(torne): make legitimate uses call Adopt() instead, and make this
  // private.
  ScopedJavaLocalRef(JNIEnv* env, T obj) : JavaRef<T>(env, obj), env_(env) {}

  ~ScopedJavaLocalRef() { Reset(); }

  // Null assignment, for disambiguation.
  ScopedJavaLocalRef& operator=(std::nullptr_t) {
    Reset();
    return *this;
  }

  // Copy assignment.
  ScopedJavaLocalRef& operator=(const ScopedJavaLocalRef& other) {
    Reset(other);
    return *this;
  }

  // Copy conversion assignment.
  template <typename U,
            typename = std::enable_if_t<std::is_convertible<U, T>::value>>
  ScopedJavaLocalRef& operator=(const ScopedJavaLocalRef<U>& other) {
    Reset(other);
    return *this;
  }

  // Move assignment.
  template <typename U,
            typename = std::enable_if_t<std::is_convertible<U, T>::value>>
  ScopedJavaLocalRef& operator=(ScopedJavaLocalRef<U>&& other) {
    env_ = other.env_;
    Reset();
    JavaRef<T>::steal(std::move(other));
    return *this;
  }

  // Assignment for other JavaRef types.
  ScopedJavaLocalRef& operator=(const JavaRef<T>& other) {
    Reset(other);
    return *this;
  }

  void Reset() { JavaRef<T>::ResetLocalRef(env_); }

  template <typename U,
            typename = std::enable_if_t<std::is_convertible<U, T>::value>>
  void Reset(const ScopedJavaLocalRef<U>& other) {
    // We can copy over env_ here as |other| instance must be from the same
    // thread as |this| local ref. (See class comment for multi-threading
    // limitations, and alternatives).
    Reset(other.env_, other.obj());
  }

  void Reset(const JavaRef<T>& other) {
    // If |env_| was not yet set (is still null) it will be attached to the
    // current thread in SetNewLocalRef().
    Reset(env_, other.obj());
  }

  // Creates a new local reference to the Java object, unlike the constructor
  // with the same parameters that takes ownership of the existing reference.
  // Deprecated. Don't use bare jobjects; use a JavaRef as the input.
  // TODO(torne): fix existing usage and remove this.
  void Reset(JNIEnv* env, T obj) {
    env_ = JavaRef<T>::SetNewLocalRef(env, obj);
  }

  // Releases the local reference to the caller. The caller *must* delete the
  // local reference when it is done with it. Note that calling a Java method
  // is *not* a transfer of ownership and Release() should not be used.
  T Release() { return static_cast<T>(JavaRef<T>::ReleaseInternal()); }

 private:
  // This class is only good for use on the thread it was created on so
  // it's safe to cache the non-threadsafe JNIEnv* inside this object.
  JNIEnv* env_ = nullptr;

  // Prevent ScopedJavaLocalRef(JNIEnv*, T obj) from being used to take
  // ownership of a JavaParamRef's underlying object - parameters are not
  // allowed to be deleted and so should not be owned by ScopedJavaLocalRef.
  // TODO(torne): this can be removed once JavaParamRef no longer has an
  // implicit conversion back to T.
  ScopedJavaLocalRef(JNIEnv* env, const JavaParamRef<T>& other);

  // Friend required to get env_ from conversions.
  template <typename U>
  friend class ScopedJavaLocalRef;

  // Avoids JavaObjectArrayReader having to accept and store its own env.
  template <typename U>
  friend class JavaObjectArrayReader;
};

// Holds a global reference to a Java object. The global reference is scoped
// to the lifetime of this object. This class does not hold onto any JNIEnv*
// passed to it, hence it is safe to use across threads (within the constraints
// imposed by the underlying Java object that it references).
template <typename T>
class ScopedJavaGlobalRef : public JavaRef<T> {
 public:
  constexpr ScopedJavaGlobalRef() {}
  constexpr ScopedJavaGlobalRef(std::nullptr_t) {}

  // Copy constructor. This is required in addition to the copy conversion
  // constructor below.
  ScopedJavaGlobalRef(const ScopedJavaGlobalRef& other) { Reset(other); }

  // Copy conversion constructor.
  template <typename U,
            typename = std::enable_if_t<std::is_convertible<U, T>::value>>
  ScopedJavaGlobalRef(const ScopedJavaGlobalRef<U>& other) {
    Reset(other);
  }

  // Move constructor. This is required in addition to the move conversion
  // constructor below.
  ScopedJavaGlobalRef(ScopedJavaGlobalRef&& other) {
    JavaRef<T>::steal(std::move(other));
  }

  // Move conversion constructor.
  template <typename U,
            typename = std::enable_if_t<std::is_convertible<U, T>::value>>
  ScopedJavaGlobalRef(ScopedJavaGlobalRef<U>&& other) {
    JavaRef<T>::steal(std::move(other));
  }

  // Conversion constructor for other JavaRef types.
  explicit ScopedJavaGlobalRef(const JavaRef<T>& other) { Reset(other); }

  // Create a new global reference to the object.
  // Deprecated. Don't use bare jobjects; use a JavaRef as the input.
  ScopedJavaGlobalRef(JNIEnv* env, T obj) { Reset(env, obj); }

  ~ScopedJavaGlobalRef() { Reset(); }

  // Null assignment, for disambiguation.
  ScopedJavaGlobalRef& operator=(std::nullptr_t) {
    Reset();
    return *this;
  }

  // Copy assignment.
  ScopedJavaGlobalRef& operator=(const ScopedJavaGlobalRef& other) {
    Reset(other);
    return *this;
  }

  // Copy conversion assignment.
  template <typename U,
            typename = std::enable_if_t<std::is_convertible<U, T>::value>>
  ScopedJavaGlobalRef& operator=(const ScopedJavaGlobalRef<U>& other) {
    Reset(other);
    return *this;
  }

  // Move assignment.
  template <typename U,
            typename = std::enable_if_t<std::is_convertible<U, T>::value>>
  ScopedJavaGlobalRef& operator=(ScopedJavaGlobalRef<U>&& other) {
    Reset();
    JavaRef<T>::steal(std::move(other));
    return *this;
  }

  // Assignment for other JavaRef types.
  ScopedJavaGlobalRef& operator=(const JavaRef<T>& other) {
    Reset(other);
    return *this;
  }

  void Reset() { JavaRef<T>::ResetGlobalRef(); }

  template <typename U,
            typename = std::enable_if_t<std::is_convertible<U, T>::value>>
  void Reset(const ScopedJavaGlobalRef<U>& other) {
    Reset(nullptr, other.obj());
  }

  void Reset(const JavaRef<T>& other) { Reset(nullptr, other.obj()); }

  // Deprecated. You can just use Reset(const JavaRef&).
  void Reset(JNIEnv* env, const JavaParamRef<T>& other) {
    Reset(env, other.obj());
  }

  // Deprecated. Don't use bare jobjects; use a JavaRef as the input.
  void Reset(JNIEnv* env, T obj) { JavaRef<T>::SetNewGlobalRef(env, obj); }

  // Releases the global reference to the caller. The caller *must* delete the
  // global reference when it is done with it. Note that calling a Java method
  // is *not* a transfer of ownership and Release() should not be used.
  T Release() { return static_cast<T>(JavaRef<T>::ReleaseInternal()); }
};

// Wrapper for a jobjectArray which supports input iteration, allowing Java
// arrays to be iterated over with a range-based for loop, or used with
// <algorithm> functions that accept input iterators.
//
// The iterator returns each object in the array in turn, wrapped in a
// ScopedJavaLocalRef<T>. T will usually be jobject, but if you know that the
// array contains a more specific type (such as jstring) you can use that
// instead. This does not check the type at runtime!
//
// The wrapper holds a local reference to the array and only queries the size of
// the array once, so must only be used as a stack-based object from the current
// thread.
//
// Note that this does *not* update the contents of the array if you mutate the
// returned ScopedJavaLocalRef.
template <typename T>
class JavaObjectArrayReader {
 public:
  class iterator {
   public:
    // We can only be an input iterator, as all richer iterator types must
    // implement the multipass guarantee (always returning the same object for
    // the same iterator position), which is not practical when returning
    // temporary objects.
    using iterator_category = std::input_iterator_tag;

    using difference_type = ptrdiff_t;
    using value_type = ScopedJavaLocalRef<T>;

    // It doesn't make sense to return a reference type as the iterator creates
    // temporary wrapper objects when dereferenced. Fortunately, it's not
    // required that input iterators actually use references, and defining it
    // as value_type is valid.
    using reference = value_type;

    // This exists to make operator-> work as expected: its return value must
    // resolve to an actual pointer (otherwise the compiler just keeps calling
    // operator-> on the return value until it does), so we need an extra level
    // of indirection. This is sometimes called an "arrow proxy" or similar, and
    // this version is adapted from base/value_iterators.h.
    class pointer {
     public:
      explicit pointer(const reference& ref) : ref_(ref) {}
      pointer(const pointer& ptr) = default;
      pointer& operator=(const pointer& ptr) = delete;
      reference* operator->() { return &ref_; }

     private:
      reference ref_;
    };

    iterator(const iterator&) = default;
    ~iterator() = default;

    iterator& operator=(const iterator&) = default;

    bool operator==(const iterator& other) const {
      DCHECK(reader_ == other.reader_);
      return i_ == other.i_;
    }

    bool operator!=(const iterator& other) const {
      DCHECK(reader_ == other.reader_);
      return i_ != other.i_;
    }

    reference operator*() const {
      DCHECK(i_ < reader_->size_);
      // JNIEnv functions return unowned local references; take ownership with
      // Adopt so that ~ScopedJavaLocalRef will release it automatically later.
      return value_type::Adopt(
          reader_->array_.env_,
          static_cast<T>(reader_->array_.env_->GetObjectArrayElement(
              reader_->array_.obj(), i_)));
    }

    pointer operator->() const { return pointer(operator*()); }

    iterator& operator++() {
      DCHECK(i_ < reader_->size_);
      ++i_;
      return *this;
    }

    iterator operator++(int) {
      iterator old = *this;
      ++*this;
      return old;
    }

   private:
    iterator(const JavaObjectArrayReader* reader, jsize i)
        : reader_(reader), i_(i) {}
    const JavaObjectArrayReader* reader_;
    jsize i_;

    friend JavaObjectArrayReader;
  };

  JavaObjectArrayReader(const JavaRef<jobjectArray>& array) : array_(array) {
    size_ = array_.env_->GetArrayLength(array_.obj());
  }

  // Copy constructor to allow returning it from JavaRef::ReadElements().
  JavaObjectArrayReader(const JavaObjectArrayReader& other) = default;

  // Assignment operator for consistency with copy constructor.
  JavaObjectArrayReader& operator=(const JavaObjectArrayReader& other) =
      default;

  // Allow move constructor and assignment since this owns a local ref.
  JavaObjectArrayReader(JavaObjectArrayReader&& other) = default;
  JavaObjectArrayReader& operator=(JavaObjectArrayReader&& other) = default;

  bool empty() const { return size_ == 0; }

  jsize size() const { return size_; }

  iterator begin() const { return iterator(this, 0); }

  iterator end() const { return iterator(this, size_); }

 private:
  ScopedJavaLocalRef<jobjectArray> array_;
  jsize size_;

  friend iterator;
};

}  // namespace android
}  // namespace base

#endif  // BASE_ANDROID_SCOPED_JAVA_REF_H_

// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_CONTAINERS_BUFFER_ITERATOR_H_
#define BASE_CONTAINERS_BUFFER_ITERATOR_H_

#include <type_traits>

#include "base/bit_cast.h"
#include "base/containers/span.h"
#include "base/numerics/checked_math.h"

namespace base {

// BufferIterator is a bounds-checked container utility to access variable-
// length, heterogeneous structures contained within a buffer. If the data are
// homogeneous, use base::span<> instead.
//
// After being created with a weakly-owned buffer, BufferIterator returns
// pointers to structured data within the buffer. After each method call that
// returns data in the buffer, the iterator position is advanced by the byte
// size of the object (or span of objects) returned. If there are not enough
// bytes remaining in the buffer to return the requested object(s), a nullptr
// or empty span is returned.
//
// This class is similar to base::Pickle, which should be preferred for
// serializing to disk. Pickle versions its header and does not support writing
// structures, which are problematic for serialization due to struct padding and
// version shear concerns.
//
// Example usage:
//
//    std::vector<uint8_t> buffer(4096);
//    if (!ReadSomeData(&buffer, buffer.size())) {
//      LOG(ERROR) << "Failed to read data.";
//      return false;
//    }
//
//    BufferIterator<uint8_t> iterator(buffer);
//    uint32_t* num_items = iterator.Object<uint32_t>();
//    if (!num_items) {
//      LOG(ERROR) << "No num_items field."
//      return false;
//    }
//
//    base::span<const item_struct> items =
//        iterator.Span<item_struct>(*num_items);
//    if (items.size() != *num_items) {
//      LOG(ERROR) << "Not enough items.";
//      return false;
//    }
//
//    // ... validate the objects in |items|.
template <typename B>
class BufferIterator {
 public:
  static_assert(std::is_same<std::remove_const_t<B>, char>::value ||
                    std::is_same<std::remove_const_t<B>, unsigned char>::value,
                "Underlying buffer type must be char-type.");

  BufferIterator() {}
  BufferIterator(B* data, size_t size)
      : BufferIterator(make_span(data, size)) {}
  explicit BufferIterator(span<B> buffer)
      : buffer_(buffer), remaining_(buffer) {}
  ~BufferIterator() {}

  // Returns a pointer to a mutable structure T in the buffer at the current
  // position. On success, the iterator position is advanced by sizeof(T). If
  // there are not sizeof(T) bytes remaining in the buffer, returns nullptr.
  template <typename T,
            typename =
                typename std::enable_if_t<std::is_trivially_copyable<T>::value>>
  T* MutableObject() {
    size_t size = sizeof(T);
    size_t next_position;
    if (!CheckAdd(position(), size).AssignIfValid(&next_position))
      return nullptr;
    if (next_position > total_size())
      return nullptr;
    T* t = bit_cast<T*>(remaining_.data());
    remaining_ = remaining_.subspan(size);
    return t;
  }

  // Returns a const pointer to an object of type T in the buffer at the current
  // position.
  template <typename T,
            typename =
                typename std::enable_if_t<std::is_trivially_copyable<T>::value>>
  const T* Object() {
    return MutableObject<const T>();
  }

  // Returns a span of |count| T objects in the buffer at the current position.
  // On success, the iterator position is advanced by |sizeof(T) * count|. If
  // there are not enough bytes remaining in the buffer to fulfill the request,
  // returns an empty span.
  template <typename T,
            typename =
                typename std::enable_if_t<std::is_trivially_copyable<T>::value>>
  span<T> MutableSpan(size_t count) {
    size_t size;
    if (!CheckMul(sizeof(T), count).AssignIfValid(&size))
      return span<T>();
    size_t next_position;
    if (!CheckAdd(position(), size).AssignIfValid(&next_position))
      return span<T>();
    if (next_position > total_size())
      return span<T>();
    auto result = span<T>(bit_cast<T*>(remaining_.data()), count);
    remaining_ = remaining_.subspan(size);
    return result;
  }

  // Returns a span to |count| const objects of type T in the buffer at the
  // current position.
  template <typename T,
            typename =
                typename std::enable_if_t<std::is_trivially_copyable<T>::value>>
  span<const T> Span(size_t count) {
    return MutableSpan<const T>(count);
  }

  // Resets the iterator position to the absolute offset |to|.
  void Seek(size_t to) { remaining_ = buffer_.subspan(to); }

  // Returns the total size of the underlying buffer.
  size_t total_size() { return buffer_.size(); }

  // Returns the current position in the buffer.
  size_t position() { return buffer_.size_bytes() - remaining_.size_bytes(); }

 private:
  // The original buffer that the iterator was constructed with.
  const span<B> buffer_;
  // A subspan of |buffer_| containing the remaining bytes to iterate over.
  span<B> remaining_;
  // Copy and assign allowed.
};

}  // namespace base

#endif  // BASE_CONTAINERS_BUFFER_ITERATOR_H_

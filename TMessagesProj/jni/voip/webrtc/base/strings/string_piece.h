// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
// Copied from strings/stringpiece.h with modifications
//
// A string-like object that points to a sized piece of memory.
//
// You can use StringPiece as a function or method parameter.  A StringPiece
// parameter can receive a double-quoted string literal argument, a "const
// char*" argument, a string argument, or a StringPiece argument with no data
// copying.  Systematic use of StringPiece for arguments reduces data
// copies and strlen() calls.
//
// Prefer passing StringPieces by value:
//   void MyFunction(StringPiece arg);
// If circumstances require, you may also pass by const reference:
//   void MyFunction(const StringPiece& arg);  // not preferred
// Both of these have the same lifetime semantics.  Passing by value
// generates slightly smaller code.  For more discussion, Googlers can see
// the thread go/stringpiecebyvalue on c-users.

#ifndef BASE_STRINGS_STRING_PIECE_H_
#define BASE_STRINGS_STRING_PIECE_H_

#include <stddef.h>

#include <iosfwd>
#include <string>
#include <type_traits>

#include "base/base_export.h"
#include "base/logging.h"
#include "base/strings/char_traits.h"
#include "base/strings/string16.h"
#include "base/strings/string_piece_forward.h"

namespace base {

// internal --------------------------------------------------------------------

// Many of the StringPiece functions use different implementations for the
// 8-bit and 16-bit versions, and we don't want lots of template expansions in
// this (very common) header that will slow down compilation.
//
// So here we define overloaded functions called by the StringPiece template.
// For those that share an implementation, the two versions will expand to a
// template internal to the .cc file.
namespace internal {

BASE_EXPORT size_t copy(const StringPiece& self,
                        char* buf,
                        size_t n,
                        size_t pos);
BASE_EXPORT size_t copy(const StringPiece16& self,
                        char16* buf,
                        size_t n,
                        size_t pos);

BASE_EXPORT size_t find(const StringPiece& self,
                        const StringPiece& s,
                        size_t pos);
BASE_EXPORT size_t find(const StringPiece16& self,
                        const StringPiece16& s,
                        size_t pos);
BASE_EXPORT size_t find(const StringPiece& self,
                        char c,
                        size_t pos);
BASE_EXPORT size_t find(const StringPiece16& self,
                        char16 c,
                        size_t pos);

BASE_EXPORT size_t rfind(const StringPiece& self,
                         const StringPiece& s,
                         size_t pos);
BASE_EXPORT size_t rfind(const StringPiece16& self,
                         const StringPiece16& s,
                         size_t pos);
BASE_EXPORT size_t rfind(const StringPiece& self,
                         char c,
                         size_t pos);
BASE_EXPORT size_t rfind(const StringPiece16& self,
                         char16 c,
                         size_t pos);

BASE_EXPORT size_t find_first_of(const StringPiece& self,
                                 const StringPiece& s,
                                 size_t pos);
BASE_EXPORT size_t find_first_of(const StringPiece16& self,
                                 const StringPiece16& s,
                                 size_t pos);

BASE_EXPORT size_t find_first_not_of(const StringPiece& self,
                                     const StringPiece& s,
                                     size_t pos);
BASE_EXPORT size_t find_first_not_of(const StringPiece16& self,
                                     const StringPiece16& s,
                                     size_t pos);
BASE_EXPORT size_t find_first_not_of(const StringPiece& self,
                                     char c,
                                     size_t pos);
BASE_EXPORT size_t find_first_not_of(const StringPiece16& self,
                                     char16 c,
                                     size_t pos);

BASE_EXPORT size_t find_last_of(const StringPiece& self,
                                const StringPiece& s,
                                size_t pos);
BASE_EXPORT size_t find_last_of(const StringPiece16& self,
                                const StringPiece16& s,
                                size_t pos);
BASE_EXPORT size_t find_last_of(const StringPiece& self,
                                char c,
                                size_t pos);
BASE_EXPORT size_t find_last_of(const StringPiece16& self,
                                char16 c,
                                size_t pos);

BASE_EXPORT size_t find_last_not_of(const StringPiece& self,
                                    const StringPiece& s,
                                    size_t pos);
BASE_EXPORT size_t find_last_not_of(const StringPiece16& self,
                                    const StringPiece16& s,
                                    size_t pos);
BASE_EXPORT size_t find_last_not_of(const StringPiece16& self,
                                    char16 c,
                                    size_t pos);
BASE_EXPORT size_t find_last_not_of(const StringPiece& self,
                                    char c,
                                    size_t pos);

BASE_EXPORT StringPiece substr(const StringPiece& self,
                               size_t pos,
                               size_t n);
BASE_EXPORT StringPiece16 substr(const StringPiece16& self,
                                 size_t pos,
                                 size_t n);

}  // namespace internal

// BasicStringPiece ------------------------------------------------------------

// Defines the types, methods, operators, and data members common to both
// StringPiece and StringPiece16.
//
// This is templatized by string class type rather than character type, so
// BasicStringPiece<std::string> or BasicStringPiece<base::string16>.
template <typename STRING_TYPE> class BasicStringPiece {
 public:
  // Standard STL container boilerplate.
  typedef size_t size_type;
  typedef typename STRING_TYPE::value_type value_type;
  typedef const value_type* pointer;
  typedef const value_type& reference;
  typedef const value_type& const_reference;
  typedef ptrdiff_t difference_type;
  typedef const value_type* const_iterator;
  typedef std::reverse_iterator<const_iterator> const_reverse_iterator;

  static const size_type npos;

 public:
  // We provide non-explicit singleton constructors so users can pass
  // in a "const char*" or a "string" wherever a "StringPiece" is
  // expected (likewise for char16, string16, StringPiece16).
  constexpr BasicStringPiece() : ptr_(NULL), length_(0) {}
  // TODO(crbug.com/1049498): Construction from nullptr is not allowed for
  // std::basic_string_view, so remove the special handling for it.
  // Note: This doesn't just use STRING_TYPE::traits_type::length(), since that
  // isn't constexpr until C++17.
  constexpr BasicStringPiece(const value_type* str)
      : ptr_(str), length_(!str ? 0 : CharTraits<value_type>::length(str)) {}
  // Explicitly disallow construction from nullptr. Note that this does not
  // catch construction from runtime strings that might be null.
  // Note: The following is just a more elaborate way of spelling
  // `BasicStringPiece(nullptr_t) = delete`, but unfortunately the terse form is
  // not supported by the PNaCl toolchain.
  // TODO(crbug.com/1049498): Remove once we CHECK(str) in the constructor
  // above.
  template <class T, class = std::enable_if_t<std::is_null_pointer<T>::value>>
  BasicStringPiece(T) {
    static_assert(sizeof(T) == 0,  // Always false.
                  "StringPiece does not support construction from nullptr, use "
                  "the default constructor instead.");
  }
  BasicStringPiece(const STRING_TYPE& str)
      : ptr_(str.data()), length_(str.size()) {}
  constexpr BasicStringPiece(const value_type* offset, size_type len)
      : ptr_(offset), length_(len) {}
  BasicStringPiece(const typename STRING_TYPE::const_iterator& begin,
                   const typename STRING_TYPE::const_iterator& end) {
    DCHECK(begin <= end) << "StringPiece iterators swapped or invalid.";
    length_ = static_cast<size_t>(std::distance(begin, end));

    // The length test before assignment is to avoid dereferencing an iterator
    // that may point to the end() of a string.
    ptr_ = length_ > 0 ? &*begin : nullptr;
  }

  // data() may return a pointer to a buffer with embedded NULs, and the
  // returned buffer may or may not be null terminated.  Therefore it is
  // typically a mistake to pass data() to a routine that expects a NUL
  // terminated string.
  constexpr const value_type* data() const { return ptr_; }
  constexpr size_type size() const noexcept { return length_; }
  constexpr size_type length() const noexcept { return length_; }
  bool empty() const { return length_ == 0; }

  constexpr value_type operator[](size_type i) const {
    CHECK(i < length_);
    return ptr_[i];
  }

  value_type front() const {
    CHECK_NE(0UL, length_);
    return ptr_[0];
  }

  value_type back() const {
    CHECK_NE(0UL, length_);
    return ptr_[length_ - 1];
  }

  constexpr void remove_prefix(size_type n) {
    CHECK(n <= length_);
    ptr_ += n;
    length_ -= n;
  }

  constexpr void remove_suffix(size_type n) {
    CHECK(n <= length_);
    length_ -= n;
  }

  constexpr int compare(BasicStringPiece x) const noexcept {
    int r = CharTraits<value_type>::compare(
        ptr_, x.ptr_, (length_ < x.length_ ? length_ : x.length_));
    if (r == 0) {
      if (length_ < x.length_) r = -1;
      else if (length_ > x.length_) r = +1;
    }
    return r;
  }

  // This is the style of conversion preferred by std::string_view in C++17.
  explicit operator STRING_TYPE() const { return as_string(); }

  STRING_TYPE as_string() const {
    // std::string doesn't like to take a NULL pointer even with a 0 size.
    return empty() ? STRING_TYPE() : STRING_TYPE(data(), size());
  }

  const_iterator begin() const { return ptr_; }
  const_iterator end() const { return ptr_ + length_; }
  const_reverse_iterator rbegin() const {
    return const_reverse_iterator(ptr_ + length_);
  }
  const_reverse_iterator rend() const {
    return const_reverse_iterator(ptr_);
  }

  size_type max_size() const { return length_; }
  size_type capacity() const { return length_; }

  size_type copy(value_type* buf, size_type n, size_type pos = 0) const {
    return internal::copy(*this, buf, n, pos);
  }

  // Does "this" start with "x"
  constexpr bool starts_with(BasicStringPiece x) const noexcept {
    return (
        (this->length_ >= x.length_) &&
        (CharTraits<value_type>::compare(this->ptr_, x.ptr_, x.length_) == 0));
  }

  // Does "this" end with "x"
  constexpr bool ends_with(BasicStringPiece x) const noexcept {
    return ((this->length_ >= x.length_) &&
            (CharTraits<value_type>::compare(
                 this->ptr_ + (this->length_ - x.length_), x.ptr_, x.length_) ==
             0));
  }

  // find: Search for a character or substring at a given offset.
  size_type find(const BasicStringPiece<STRING_TYPE>& s,
                 size_type pos = 0) const {
    return internal::find(*this, s, pos);
  }
  size_type find(value_type c, size_type pos = 0) const {
    return internal::find(*this, c, pos);
  }

  // rfind: Reverse find.
  size_type rfind(const BasicStringPiece& s,
                  size_type pos = BasicStringPiece::npos) const {
    return internal::rfind(*this, s, pos);
  }
  size_type rfind(value_type c, size_type pos = BasicStringPiece::npos) const {
    return internal::rfind(*this, c, pos);
  }

  // find_first_of: Find the first occurence of one of a set of characters.
  size_type find_first_of(const BasicStringPiece& s,
                          size_type pos = 0) const {
    return internal::find_first_of(*this, s, pos);
  }
  size_type find_first_of(value_type c, size_type pos = 0) const {
    return find(c, pos);
  }

  // find_first_not_of: Find the first occurence not of a set of characters.
  size_type find_first_not_of(const BasicStringPiece& s,
                              size_type pos = 0) const {
    return internal::find_first_not_of(*this, s, pos);
  }
  size_type find_first_not_of(value_type c, size_type pos = 0) const {
    return internal::find_first_not_of(*this, c, pos);
  }

  // find_last_of: Find the last occurence of one of a set of characters.
  size_type find_last_of(const BasicStringPiece& s,
                         size_type pos = BasicStringPiece::npos) const {
    return internal::find_last_of(*this, s, pos);
  }
  size_type find_last_of(value_type c,
                         size_type pos = BasicStringPiece::npos) const {
    return rfind(c, pos);
  }

  // find_last_not_of: Find the last occurence not of a set of characters.
  size_type find_last_not_of(const BasicStringPiece& s,
                             size_type pos = BasicStringPiece::npos) const {
    return internal::find_last_not_of(*this, s, pos);
  }
  size_type find_last_not_of(value_type c,
                             size_type pos = BasicStringPiece::npos) const {
    return internal::find_last_not_of(*this, c, pos);
  }

  // substr.
  BasicStringPiece substr(size_type pos,
                          size_type n = BasicStringPiece::npos) const {
    return internal::substr(*this, pos, n);
  }

 protected:
  const value_type* ptr_;
  size_type length_;
};

template <typename STRING_TYPE>
const typename BasicStringPiece<STRING_TYPE>::size_type
BasicStringPiece<STRING_TYPE>::npos =
    typename BasicStringPiece<STRING_TYPE>::size_type(-1);

// MSVC doesn't like complex extern templates and DLLs.
#if !defined(COMPILER_MSVC)
extern template class BASE_EXPORT BasicStringPiece<std::string>;
extern template class BASE_EXPORT BasicStringPiece<string16>;
#endif

// Comparison operators --------------------------------------------------------
// operator ==
template <typename StringT>
constexpr bool operator==(BasicStringPiece<StringT> lhs,
                          BasicStringPiece<StringT> rhs) noexcept {
  return lhs.size() == rhs.size() && lhs.compare(rhs) == 0;
}

// Here and below we make use of std::common_type_t to emulate an identity type
// transformation. This creates a non-deduced context, so that we can compare
// StringPieces with types that implicitly convert to StringPieces. See
// https://wg21.link/n3766 for details.
// Furthermore, we require dummy template parameters for these overloads to work
// around a name mangling issue on Windows.
template <typename StringT, int = 1>
constexpr bool operator==(
    BasicStringPiece<StringT> lhs,
    std::common_type_t<BasicStringPiece<StringT>> rhs) noexcept {
  return lhs.size() == rhs.size() && lhs.compare(rhs) == 0;
}

template <typename StringT, int = 2>
constexpr bool operator==(std::common_type_t<BasicStringPiece<StringT>> lhs,
                          BasicStringPiece<StringT> rhs) noexcept {
  return lhs.size() == rhs.size() && lhs.compare(rhs) == 0;
}

// operator !=
template <typename StringT>
constexpr bool operator!=(BasicStringPiece<StringT> lhs,
                          BasicStringPiece<StringT> rhs) noexcept {
  return !(lhs == rhs);
}

template <typename StringT, int = 1>
constexpr bool operator!=(
    BasicStringPiece<StringT> lhs,
    std::common_type_t<BasicStringPiece<StringT>> rhs) noexcept {
  return !(lhs == rhs);
}

template <typename StringT, int = 2>
constexpr bool operator!=(std::common_type_t<BasicStringPiece<StringT>> lhs,
                          BasicStringPiece<StringT> rhs) noexcept {
  return !(lhs == rhs);
}

// operator <
template <typename StringT>
constexpr bool operator<(BasicStringPiece<StringT> lhs,
                         BasicStringPiece<StringT> rhs) noexcept {
  return lhs.compare(rhs) < 0;
}

template <typename StringT, int = 1>
constexpr bool operator<(
    BasicStringPiece<StringT> lhs,
    std::common_type_t<BasicStringPiece<StringT>> rhs) noexcept {
  return lhs.compare(rhs) < 0;
}

template <typename StringT, int = 2>
constexpr bool operator<(std::common_type_t<BasicStringPiece<StringT>> lhs,
                         BasicStringPiece<StringT> rhs) noexcept {
  return lhs.compare(rhs) < 0;
}

// operator >
template <typename StringT>
constexpr bool operator>(BasicStringPiece<StringT> lhs,
                         BasicStringPiece<StringT> rhs) noexcept {
  return rhs < lhs;
}

template <typename StringT, int = 1>
constexpr bool operator>(
    BasicStringPiece<StringT> lhs,
    std::common_type_t<BasicStringPiece<StringT>> rhs) noexcept {
  return rhs < lhs;
}

template <typename StringT, int = 2>
constexpr bool operator>(std::common_type_t<BasicStringPiece<StringT>> lhs,
                         BasicStringPiece<StringT> rhs) noexcept {
  return rhs < lhs;
}

// operator <=
template <typename StringT>
constexpr bool operator<=(BasicStringPiece<StringT> lhs,
                          BasicStringPiece<StringT> rhs) noexcept {
  return !(rhs < lhs);
}

template <typename StringT, int = 1>
constexpr bool operator<=(
    BasicStringPiece<StringT> lhs,
    std::common_type_t<BasicStringPiece<StringT>> rhs) noexcept {
  return !(rhs < lhs);
}

template <typename StringT, int = 2>
constexpr bool operator<=(std::common_type_t<BasicStringPiece<StringT>> lhs,
                          BasicStringPiece<StringT> rhs) noexcept {
  return !(rhs < lhs);
}

// operator >=
template <typename StringT>
constexpr bool operator>=(BasicStringPiece<StringT> lhs,
                          BasicStringPiece<StringT> rhs) noexcept {
  return !(lhs < rhs);
}

template <typename StringT, int = 1>
constexpr bool operator>=(
    BasicStringPiece<StringT> lhs,
    std::common_type_t<BasicStringPiece<StringT>> rhs) noexcept {
  return !(lhs < rhs);
}

template <typename StringT, int = 2>
constexpr bool operator>=(std::common_type_t<BasicStringPiece<StringT>> lhs,
                          BasicStringPiece<StringT> rhs) noexcept {
  return !(lhs < rhs);
}

BASE_EXPORT std::ostream& operator<<(std::ostream& o,
                                     const StringPiece& piece);

BASE_EXPORT std::ostream& operator<<(std::ostream& o,
                                     const StringPiece16& piece);

// Hashing ---------------------------------------------------------------------

// We provide appropriate hash functions so StringPiece and StringPiece16 can
// be used as keys in hash sets and maps.

// This hash function is copied from base/strings/string16.h. We don't use the
// ones already defined for string and string16 directly because it would
// require the string constructors to be called, which we don't want.

template <typename StringPieceType>
struct StringPieceHashImpl {
  std::size_t operator()(StringPieceType sp) const {
    std::size_t result = 0;
    for (auto c : sp)
      result = (result * 131) + c;
    return result;
  }
};

using StringPieceHash = StringPieceHashImpl<StringPiece>;
using StringPiece16Hash = StringPieceHashImpl<StringPiece16>;
using WStringPieceHash = StringPieceHashImpl<WStringPiece>;

}  // namespace base

#endif  // BASE_STRINGS_STRING_PIECE_H_

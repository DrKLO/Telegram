// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/i18n/char_iterator.h"

#include "base/logging.h"
#include "third_party/icu/source/common/unicode/utf16.h"
#include "third_party/icu/source/common/unicode/utf8.h"

namespace base {
namespace i18n {

// UTF8CharIterator ------------------------------------------------------------

UTF8CharIterator::UTF8CharIterator(const std::string* str)
    : str_(reinterpret_cast<const uint8_t*>(str->data())),
      len_(str->size()),
      array_pos_(0),
      next_pos_(0),
      char_pos_(0),
      char_(0) {
  if (len_)
    U8_NEXT(str_, next_pos_, len_, char_);
}

UTF8CharIterator::~UTF8CharIterator() = default;

bool UTF8CharIterator::Advance() {
  if (array_pos_ >= len_)
    return false;

  array_pos_ = next_pos_;
  char_pos_++;
  if (next_pos_ < len_)
    U8_NEXT(str_, next_pos_, len_, char_);

  return true;
}

// UTF16CharIterator -----------------------------------------------------------

UTF16CharIterator::UTF16CharIterator(const string16* str)
    : UTF16CharIterator(str, 0) {}

UTF16CharIterator::UTF16CharIterator(const char16* str, size_t str_len)
    : UTF16CharIterator(str, str_len, 0) {}

UTF16CharIterator::UTF16CharIterator(UTF16CharIterator&& to_move) = default;

UTF16CharIterator::~UTF16CharIterator() = default;

UTF16CharIterator& UTF16CharIterator::operator=(UTF16CharIterator&& to_move) =
    default;

// static
UTF16CharIterator UTF16CharIterator::LowerBound(const string16* str,
                                                size_t array_index) {
  return LowerBound(reinterpret_cast<const char16*>(str->data()), str->length(),
                    array_index);
}

// static
UTF16CharIterator UTF16CharIterator::LowerBound(const char16* str,
                                                size_t length,
                                                size_t array_index) {
  DCHECK_LE(array_index, length);
  U16_SET_CP_START(str, 0, array_index);
  return UTF16CharIterator(str, length, array_index);
}

// static
UTF16CharIterator UTF16CharIterator::UpperBound(const string16* str,
                                                size_t array_index) {
  return UpperBound(reinterpret_cast<const char16*>(str->data()), str->length(),
                    array_index);
}

// static
UTF16CharIterator UTF16CharIterator::UpperBound(const char16* str,
                                                size_t length,
                                                size_t array_index) {
  DCHECK_LE(array_index, length);
  U16_SET_CP_LIMIT(str, 0, array_index, length);
  return UTF16CharIterator(str, length, array_index);
}

int32_t UTF16CharIterator::NextCodePoint() const {
  if (next_pos_ >= len_)
    return 0;

  UChar32 c;
  U16_GET(str_, 0, next_pos_, len_, c);
  return c;
}

int32_t UTF16CharIterator::PreviousCodePoint() const {
  if (array_pos_ <= 0)
    return 0;

  uint32_t pos = array_pos_;
  UChar32 c;
  U16_PREV(str_, 0, pos, c);
  return c;
}

bool UTF16CharIterator::Advance() {
  if (array_pos_ >= len_)
    return false;

  array_pos_ = next_pos_;
  char_offset_++;
  if (next_pos_ < len_)
    ReadChar();

  return true;
}

bool UTF16CharIterator::Rewind() {
  if (array_pos_ <= 0)
    return false;

  next_pos_ = array_pos_;
  char_offset_--;
  U16_PREV(str_, 0, array_pos_, char_);
  return true;
}

UTF16CharIterator::UTF16CharIterator(const string16* str, int32_t initial_pos)
    : UTF16CharIterator(str->data(), str->length(), initial_pos) {}

UTF16CharIterator::UTF16CharIterator(const char16* str,
                                     size_t str_len,
                                     int32_t initial_pos)
    : str_(str),
      len_(str_len),
      array_pos_(initial_pos),
      next_pos_(initial_pos),
      char_offset_(0),
      char_(0) {
  // This has the side-effect of advancing |next_pos_|.
  if (array_pos_ < len_)
    ReadChar();
}

void UTF16CharIterator::ReadChar() {
  // This is actually a huge macro, so is worth having in a separate function.
  U16_NEXT(str_, next_pos_, len_, char_);
}

}  // namespace i18n
}  // namespace base

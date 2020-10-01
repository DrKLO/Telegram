// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_I18N_CHAR_ITERATOR_H_
#define BASE_I18N_CHAR_ITERATOR_H_

#include <stddef.h>
#include <stdint.h>

#include <string>

#include "base/gtest_prod_util.h"
#include "base/i18n/base_i18n_export.h"
#include "base/macros.h"
#include "base/strings/string16.h"
#include "build/build_config.h"

// The CharIterator classes iterate through the characters in UTF8 and
// UTF16 strings.  Example usage:
//
//   UTF8CharIterator iter(&str);
//   while (!iter.end()) {
//     VLOG(1) << iter.get();
//     iter.Advance();
//   }

#if defined(OS_WIN)
typedef unsigned char uint8_t;
#endif

namespace base {
namespace i18n {

class BASE_I18N_EXPORT UTF8CharIterator {
 public:
  // Requires |str| to live as long as the UTF8CharIterator does.
  explicit UTF8CharIterator(const std::string* str);
  ~UTF8CharIterator();

  // Return the starting array index of the current character within the
  // string.
  int32_t array_pos() const { return array_pos_; }

  // Return the logical index of the current character, independent of the
  // number of bytes each character takes.
  int32_t char_pos() const { return char_pos_; }

  // Return the current char.
  int32_t get() const { return char_; }

  // Returns true if we're at the end of the string.
  bool end() const { return array_pos_ == len_; }

  // Advance to the next actual character.  Returns false if we're at the
  // end of the string.
  bool Advance();

 private:
  // The string we're iterating over.
  const uint8_t* str_;

  // The length of the encoded string.
  int32_t len_;

  // Array index.
  int32_t array_pos_;

  // The next array index.
  int32_t next_pos_;

  // Character index.
  int32_t char_pos_;

  // The current character.
  int32_t char_;

  DISALLOW_COPY_AND_ASSIGN(UTF8CharIterator);
};

class BASE_I18N_EXPORT UTF16CharIterator {
 public:
  // Requires |str| to live as long as the UTF16CharIterator does.
  explicit UTF16CharIterator(const string16* str);
  UTF16CharIterator(const char16* str, size_t str_len);
  UTF16CharIterator(UTF16CharIterator&& to_move);
  ~UTF16CharIterator();
  UTF16CharIterator& operator=(UTF16CharIterator&& to_move);

  // Returns an iterator starting on the unicode character at offset
  // |array_index| into the string, or the previous array offset if
  // |array_index| is the second half of a surrogate pair.
  static UTF16CharIterator LowerBound(const string16* str, size_t array_index);
  static UTF16CharIterator LowerBound(const char16* str,
                                      size_t str_len,
                                      size_t array_index);

  // Returns an iterator starting on the unicode character at offset
  // |array_index| into the string, or the next offset if |array_index| is the
  // second half of a surrogate pair.
  static UTF16CharIterator UpperBound(const string16* str, size_t array_index);
  static UTF16CharIterator UpperBound(const char16* str,
                                      size_t str_len,
                                      size_t array_index);

  // Return the starting array index of the current character within the
  // string.
  int32_t array_pos() const { return array_pos_; }

  // Returns the offset in code points from the initial iterator position, which
  // could be negative if Rewind() is called. The initial value is always zero,
  // regardless of how the iterator is constructed.
  int32_t char_offset() const { return char_offset_; }

  // Returns the code point at the current position.
  int32_t get() const { return char_; }

  // Returns the code point (i.e. the full Unicode character, not half of a
  // surrogate pair) following the current one. Should not be called if end() is
  // true. If the current code point is the last one in the string, returns
  // zero.
  int32_t NextCodePoint() const;

  // Returns the code point (i.e. the full Unicode character, not half of a
  // surrogate pair) preceding the current one. Should not be called if start()
  // is true.
  int32_t PreviousCodePoint() const;

  // Returns true if we're at the start of the string.
  bool start() const { return array_pos_ == 0; }

  // Returns true if we're at the end of the string.
  bool end() const { return array_pos_ == len_; }

  // Advances to the next actual character.  Returns false if we're at the
  // end of the string.
  bool Advance();

  // Moves to the previous actual character. Returns false if we're at the start
  // of the string.
  bool Rewind();

 private:
  UTF16CharIterator(const string16* str, int32_t initial_pos);
  UTF16CharIterator(const char16* str, size_t str_len, int32_t initial_pos);

  // Fills in the current character we found and advances to the next
  // character, updating all flags as necessary.
  void ReadChar();

  // The string we're iterating over.
  const char16* str_;

  // The length of the encoded string.
  int32_t len_;

  // Array index.
  int32_t array_pos_;

  // The next array index.
  int32_t next_pos_;

  // Character offset from the initial position of the iterator.
  int32_t char_offset_;

  // The current character.
  int32_t char_;

  DISALLOW_COPY_AND_ASSIGN(UTF16CharIterator);
};

}  // namespace i18n
}  // namespace base

#endif  // BASE_I18N_CHAR_ITERATOR_H_

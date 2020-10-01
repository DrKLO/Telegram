// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_STRINGS_STRING_TOKENIZER_H_
#define BASE_STRINGS_STRING_TOKENIZER_H_

#include <algorithm>
#include <string>

#include "base/strings/string_piece.h"

namespace base {

// StringTokenizerT is a simple string tokenizer class.  It works like an
// iterator that with each step (see the Advance method) updates members that
// refer to the next token in the input string.  The user may optionally
// configure the tokenizer to return delimiters.
//
// EXAMPLE 1:
//
//   char input[] = "this is a test";
//   CStringTokenizer t(input, input + strlen(input), " ");
//   while (t.GetNext()) {
//     printf("%s\n", t.token().c_str());
//   }
//
// Output:
//
//   this
//   is
//   a
//   test
//
//
// EXAMPLE 2:
//
//   std::string input = "no-cache=\"foo, bar\", private";
//   StringTokenizer t(input, ", ");
//   t.set_quote_chars("\"");
//   while (t.GetNext()) {
//     printf("%s\n", t.token().c_str());
//   }
//
// Output:
//
//   no-cache="foo, bar"
//   private
//
//
// EXAMPLE 3:
//
//   bool next_is_option = false, next_is_value = false;
//   std::string input = "text/html; charset=UTF-8; foo=bar";
//   StringTokenizer t(input, "; =");
//   t.set_options(StringTokenizer::RETURN_DELIMS);
//   while (t.GetNext()) {
//     if (t.token_is_delim()) {
//       switch (*t.token_begin()) {
//         case ';':
//           next_is_option = true;
//           break;
//         case '=':
//           next_is_value = true;
//           break;
//       }
//     } else {
//       const char* label;
//       if (next_is_option) {
//         label = "option-name";
//         next_is_option = false;
//       } else if (next_is_value) {
//         label = "option-value";
//         next_is_value = false;
//       } else {
//         label = "mime-type";
//       }
//       printf("%s: %s\n", label, t.token().c_str());
//     }
//   }
//
//
template <class str, class const_iterator>
class StringTokenizerT {
 public:
  typedef typename str::value_type char_type;

  // Options that may be pass to set_options()
  enum {
    // Specifies the delimiters should be returned as tokens
    RETURN_DELIMS = 1 << 0,

    // Specifies that empty tokens should be returned. Treats the beginning and
    // ending of the string as implicit delimiters, though doesn't return them
    // as tokens if RETURN_DELIMS is also used.
    RETURN_EMPTY_TOKENS = 1 << 1,
  };

  // The string object must live longer than the tokenizer. In particular, this
  // should not be constructed with a temporary. The deleted rvalue constructor
  // blocks the most obvious instances of this (e.g. passing a string literal to
  // the constructor), but caution must still be exercised.
  StringTokenizerT(const str& string,
                   const str& delims) {
    Init(string.begin(), string.end(), delims);
  }

  // Don't allow temporary strings to be used with string tokenizer, since
  // Init() would otherwise save iterators to a temporary string.
  StringTokenizerT(str&&, const str& delims) = delete;

  StringTokenizerT(const_iterator string_begin,
                   const_iterator string_end,
                   const str& delims) {
    Init(string_begin, string_end, delims);
  }

  // Set the options for this tokenizer.  By default, this is 0.
  void set_options(int options) { options_ = options; }

  // Set the characters to regard as quotes.  By default, this is empty.  When
  // a quote char is encountered, the tokenizer will switch into a mode where
  // it ignores delimiters that it finds.  It switches out of this mode once it
  // finds another instance of the quote char.  If a backslash is encountered
  // within a quoted string, then the next character is skipped.
  void set_quote_chars(const str& quotes) { quotes_ = quotes; }

  // Call this method to advance the tokenizer to the next delimiter.  This
  // returns false if the tokenizer is complete.  This method must be called
  // before calling any of the token* methods.
  bool GetNext() {
    if (quotes_.empty() && options_ == 0)
      return QuickGetNext();
    else
      return FullGetNext();
  }

  // Start iterating through tokens from the beginning of the string.
  void Reset() {
    token_end_ = start_pos_;
  }

  // Returns true if token is a delimiter.  When the tokenizer is constructed
  // with the RETURN_DELIMS option, this method can be used to check if the
  // returned token is actually a delimiter. Returns true before the first
  // time GetNext() has been called, and after GetNext() returns false.
  bool token_is_delim() const { return token_is_delim_; }

  // If GetNext() returned true, then these methods may be used to read the
  // value of the token.
  const_iterator token_begin() const { return token_begin_; }
  const_iterator token_end() const { return token_end_; }
  str token() const { return str(token_begin_, token_end_); }
  BasicStringPiece<str> token_piece() const {
    return BasicStringPiece<str>(&*token_begin_,
                                 std::distance(token_begin_, token_end_));
  }

 private:
  void Init(const_iterator string_begin,
            const_iterator string_end,
            const str& delims) {
    start_pos_ = string_begin;
    token_begin_ = string_begin;
    token_end_ = string_begin;
    end_ = string_end;
    delims_ = delims;
    options_ = 0;
    token_is_delim_ = true;
  }

  // Implementation of GetNext() for when we have no quote characters. We have
  // two separate implementations because AdvanceOne() is a hot spot in large
  // text files with large tokens.
  bool QuickGetNext() {
    token_is_delim_ = false;
    for (;;) {
      token_begin_ = token_end_;
      if (token_end_ == end_) {
        token_is_delim_ = true;
        return false;
      }
      ++token_end_;
      if (delims_.find(*token_begin_) == str::npos)
        break;
      // else skip over delimiter.
    }
    while (token_end_ != end_ && delims_.find(*token_end_) == str::npos)
      ++token_end_;
    return true;
  }

  // Implementation of GetNext() for when we have to take quotes into account.
  bool FullGetNext() {
    AdvanceState state;

    for (;;) {
      if (token_is_delim_) {
        // Last token was a delimiter. Note: This is also the case at the start.
        //
        //    ... D T T T T D ...
        //        ^ ^
        //        | |
        //        | |token_end_| : The next character to look at or |end_|.
        //        |
        //        |token_begin_| : Points to delimiter or |token_end_|.
        //
        // The next token is always a non-delimiting token. It could be empty,
        // however.
        token_is_delim_ = false;
        token_begin_ = token_end_;

        // Slurp all non-delimiter characters into the token.
        while (token_end_ != end_ && AdvanceOne(&state, *token_end_)) {
          ++token_end_;
        }

        // If it's non-empty, or empty tokens were requested, return the token.
        if (token_begin_ != token_end_ || (options_ & RETURN_EMPTY_TOKENS))
          return true;
      }

      DCHECK(!token_is_delim_);
      // Last token was a regular token.
      //
      //    ... T T T D T T ...
      //        ^     ^
      //        |     |
      //        |     token_end_ : The next character to look at. Always one
      //        |                  char beyond the token boundary.
      //        |
      //        token_begin_ : Points to beginning of token. Note: token could
      //                       be empty, in which case
      //                       token_begin_ == token_end_.
      //
      // The next token is always a delimiter. It could be |end_| however, but
      // |end_| is also an implicit delimiter.
      token_is_delim_ = true;
      token_begin_ = token_end_;

      if (token_end_ == end_)
        return false;

      // Look at the delimiter.
      ++token_end_;
      if (options_ & RETURN_DELIMS)
        return true;
    }

    return false;
  }

  bool IsDelim(char_type c) const {
    return delims_.find(c) != str::npos;
  }

  bool IsQuote(char_type c) const {
    return quotes_.find(c) != str::npos;
  }

  struct AdvanceState {
    bool in_quote;
    bool in_escape;
    char_type quote_char;
    AdvanceState() : in_quote(false), in_escape(false), quote_char('\0') {}
  };

  // Returns true if a delimiter was not hit.
  bool AdvanceOne(AdvanceState* state, char_type c) {
    if (state->in_quote) {
      if (state->in_escape) {
        state->in_escape = false;
      } else if (c == '\\') {
        state->in_escape = true;
      } else if (c == state->quote_char) {
        state->in_quote = false;
      }
    } else {
      if (IsDelim(c))
        return false;
      state->in_quote = IsQuote(state->quote_char = c);
    }
    return true;
  }

  const_iterator start_pos_;
  const_iterator token_begin_;
  const_iterator token_end_;
  const_iterator end_;
  str delims_;
  str quotes_;
  int options_;
  bool token_is_delim_;
};

typedef StringTokenizerT<std::string, std::string::const_iterator>
    StringTokenizer;
typedef StringTokenizerT<string16, string16::const_iterator> String16Tokenizer;
typedef StringTokenizerT<std::string, const char*> CStringTokenizer;

}  // namespace base

#endif  // BASE_STRINGS_STRING_TOKENIZER_H_

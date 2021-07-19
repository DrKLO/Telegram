// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_I18N_MESSAGE_FORMATTER_H_
#define BASE_I18N_MESSAGE_FORMATTER_H_

#include <stdint.h>

#include <memory>
#include <string>

#include "base/i18n/base_i18n_export.h"
#include "base/macros.h"
#include "base/strings/string16.h"
#include "base/strings/string_piece.h"
#include "third_party/icu/source/common/unicode/uversion.h"

U_NAMESPACE_BEGIN
class Formattable;
U_NAMESPACE_END

namespace base {

class Time;

namespace i18n {

class MessageFormatter;

namespace internal {

class BASE_I18N_EXPORT MessageArg {
 public:
  MessageArg(const char* s);
  MessageArg(StringPiece s);
  MessageArg(const std::string& s);
  MessageArg(const string16& s);
  MessageArg(int i);
  MessageArg(int64_t i);
  MessageArg(double d);
  MessageArg(const Time& t);
  ~MessageArg();

 private:
  friend class base::i18n::MessageFormatter;
  MessageArg();
  // Tests if this argument has a value, and if so increments *count.
  bool has_value(int* count) const;
  std::unique_ptr<icu::Formattable> formattable;
  DISALLOW_COPY_AND_ASSIGN(MessageArg);
};

}  // namespace internal

// Message Formatter with the ICU message format syntax support.
// It can format strings (UTF-8 and UTF-16), numbers and base::Time with
// plural, gender and other 'selectors' support. This is handy if you
// have multiple parameters of differnt types and some of them require
// plural or gender/selector support.
//
// To use this API for locale-sensitive formatting, retrieve a 'message
// template' in the ICU message format from a message bundle (e.g. with
// l10n_util::GetStringUTF16()) and pass it to FormatWith{Named,Numbered}Args.
//
// MessageFormat specs:
//   http://icu-project.org/apiref/icu4j/com/ibm/icu/text/MessageFormat.html
//   http://icu-project.org/apiref/icu4c/classicu_1_1DecimalFormat.html#details
// Examples:
//   http://userguide.icu-project.org/formatparse/messages
//   message_formatter_unittest.cc
//   go/plurals inside Google.
//   TODO(jshin): Document this API in md format docs.
// Caveat:
//   When plural/select/gender is used along with other format specifiers such
//   as date or number, plural/select/gender should be at the top level. It's
//   not an ICU restriction but a constraint imposed by Google's translation
//   infrastructure. Message A does not work. It must be revised to Message B.
//
//     A.
//       Rated <ph name="RATING">{0, number,0.0}<ex>3.2</ex></ph>
//       by {1, plural, =1{a user} other{# users}}
//
//     B.
//       {1, plural,
//         =1{Rated <ph name="RATING">{0, number,0.0}<ex>3.2</ex></ph>
//             by a user.}
//         other{Rated <ph name="RATING">{0, number,0.0}<ex>3.2</ex></ph>
//               by # users.}}

class BASE_I18N_EXPORT MessageFormatter {
 public:
  static string16 FormatWithNamedArgs(
      StringPiece16 msg,
      StringPiece name0 = StringPiece(),
      const internal::MessageArg& arg0 = internal::MessageArg(),
      StringPiece name1 = StringPiece(),
      const internal::MessageArg& arg1 = internal::MessageArg(),
      StringPiece name2 = StringPiece(),
      const internal::MessageArg& arg2 = internal::MessageArg(),
      StringPiece name3 = StringPiece(),
      const internal::MessageArg& arg3 = internal::MessageArg(),
      StringPiece name4 = StringPiece(),
      const internal::MessageArg& arg4 = internal::MessageArg(),
      StringPiece name5 = StringPiece(),
      const internal::MessageArg& arg5 = internal::MessageArg(),
      StringPiece name6 = StringPiece(),
      const internal::MessageArg& arg6 = internal::MessageArg());

  static string16 FormatWithNumberedArgs(
      StringPiece16 msg,
      const internal::MessageArg& arg0 = internal::MessageArg(),
      const internal::MessageArg& arg1 = internal::MessageArg(),
      const internal::MessageArg& arg2 = internal::MessageArg(),
      const internal::MessageArg& arg3 = internal::MessageArg(),
      const internal::MessageArg& arg4 = internal::MessageArg(),
      const internal::MessageArg& arg5 = internal::MessageArg(),
      const internal::MessageArg& arg6 = internal::MessageArg());

 private:
  MessageFormatter() = delete;
  DISALLOW_COPY_AND_ASSIGN(MessageFormatter);
};

}  // namespace i18n
}  // namespace base

#endif  // BASE_I18N_MESSAGE_FORMATTER_H_

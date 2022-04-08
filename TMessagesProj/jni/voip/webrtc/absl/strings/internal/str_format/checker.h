// Copyright 2020 The Abseil Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef ABSL_STRINGS_INTERNAL_STR_FORMAT_CHECKER_H_
#define ABSL_STRINGS_INTERNAL_STR_FORMAT_CHECKER_H_

#include "absl/base/attributes.h"
#include "absl/strings/internal/str_format/arg.h"
#include "absl/strings/internal/str_format/extension.h"

// Compile time check support for entry points.

#ifndef ABSL_INTERNAL_ENABLE_FORMAT_CHECKER
// We disable format checker under vscode intellisense compilation.
// See https://github.com/microsoft/vscode-cpptools/issues/3683 for
// more details.
#if ABSL_HAVE_ATTRIBUTE(enable_if) && !defined(__native_client__) && \
    !defined(__INTELLISENSE__)
#define ABSL_INTERNAL_ENABLE_FORMAT_CHECKER 1
#endif  // ABSL_HAVE_ATTRIBUTE(enable_if) && !defined(__native_client__) &&
        // !defined(__INTELLISENSE__)
#endif  // ABSL_INTERNAL_ENABLE_FORMAT_CHECKER

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace str_format_internal {

constexpr bool AllOf() { return true; }

template <typename... T>
constexpr bool AllOf(bool b, T... t) {
  return b && AllOf(t...);
}

#ifdef ABSL_INTERNAL_ENABLE_FORMAT_CHECKER

constexpr bool ContainsChar(const char* chars, char c) {
  return *chars == c || (*chars && ContainsChar(chars + 1, c));
}

// A constexpr compatible list of Convs.
struct ConvList {
  const FormatConversionCharSet* array;
  int count;

  // We do the bound check here to avoid having to do it on the callers.
  // Returning an empty FormatConversionCharSet has the same effect as
  // short circuiting because it will never match any conversion.
  constexpr FormatConversionCharSet operator[](int i) const {
    return i < count ? array[i] : FormatConversionCharSet{};
  }

  constexpr ConvList without_front() const {
    return count != 0 ? ConvList{array + 1, count - 1} : *this;
  }
};

template <size_t count>
struct ConvListT {
  // Make sure the array has size > 0.
  FormatConversionCharSet list[count ? count : 1];
};

constexpr char GetChar(string_view str, size_t index) {
  return index < str.size() ? str[index] : char{};
}

constexpr string_view ConsumeFront(string_view str, size_t len = 1) {
  return len <= str.size() ? string_view(str.data() + len, str.size() - len)
                           : string_view();
}

constexpr string_view ConsumeAnyOf(string_view format, const char* chars) {
  return ContainsChar(chars, GetChar(format, 0))
             ? ConsumeAnyOf(ConsumeFront(format), chars)
             : format;
}

constexpr bool IsDigit(char c) { return c >= '0' && c <= '9'; }

// Helper class for the ParseDigits function.
// It encapsulates the two return values we need there.
struct Integer {
  string_view format;
  int value;

  // If the next character is a '$', consume it.
  // Otherwise, make `this` an invalid positional argument.
  constexpr Integer ConsumePositionalDollar() const {
    return GetChar(format, 0) == '$' ? Integer{ConsumeFront(format), value}
                                     : Integer{format, 0};
  }
};

constexpr Integer ParseDigits(string_view format, int value = 0) {
  return IsDigit(GetChar(format, 0))
             ? ParseDigits(ConsumeFront(format),
                           10 * value + GetChar(format, 0) - '0')
             : Integer{format, value};
}

// Parse digits for a positional argument.
// The parsing also consumes the '$'.
constexpr Integer ParsePositional(string_view format) {
  return ParseDigits(format).ConsumePositionalDollar();
}

// Parses a single conversion specifier.
// See ConvParser::Run() for post conditions.
class ConvParser {
  constexpr ConvParser SetFormat(string_view format) const {
    return ConvParser(format, args_, error_, arg_position_, is_positional_);
  }

  constexpr ConvParser SetArgs(ConvList args) const {
    return ConvParser(format_, args, error_, arg_position_, is_positional_);
  }

  constexpr ConvParser SetError(bool error) const {
    return ConvParser(format_, args_, error_ || error, arg_position_,
                      is_positional_);
  }

  constexpr ConvParser SetArgPosition(int arg_position) const {
    return ConvParser(format_, args_, error_, arg_position, is_positional_);
  }

  // Consumes the next arg and verifies that it matches `conv`.
  // `error_` is set if there is no next arg or if it doesn't match `conv`.
  constexpr ConvParser ConsumeNextArg(char conv) const {
    return SetArgs(args_.without_front()).SetError(!Contains(args_[0], conv));
  }

  // Verify that positional argument `i.value` matches `conv`.
  // `error_` is set if `i.value` is not a valid argument or if it doesn't
  // match.
  constexpr ConvParser VerifyPositional(Integer i, char conv) const {
    return SetFormat(i.format).SetError(!Contains(args_[i.value - 1], conv));
  }

  // Parse the position of the arg and store it in `arg_position_`.
  constexpr ConvParser ParseArgPosition(Integer arg) const {
    return SetFormat(arg.format).SetArgPosition(arg.value);
  }

  // Consume the flags.
  constexpr ConvParser ParseFlags() const {
    return SetFormat(ConsumeAnyOf(format_, "-+ #0"));
  }

  // Consume the width.
  // If it is '*', we verify that it matches `args_`. `error_` is set if it
  // doesn't match.
  constexpr ConvParser ParseWidth() const {
    return IsDigit(GetChar(format_, 0))
               ? SetFormat(ParseDigits(format_).format)
               : GetChar(format_, 0) == '*'
                     ? is_positional_
                           ? VerifyPositional(
                                 ParsePositional(ConsumeFront(format_)), '*')
                           : SetFormat(ConsumeFront(format_))
                                 .ConsumeNextArg('*')
                     : *this;
  }

  // Consume the precision.
  // If it is '*', we verify that it matches `args_`. `error_` is set if it
  // doesn't match.
  constexpr ConvParser ParsePrecision() const {
    return GetChar(format_, 0) != '.'
               ? *this
               : GetChar(format_, 1) == '*'
                     ? is_positional_
                           ? VerifyPositional(
                                 ParsePositional(ConsumeFront(format_, 2)), '*')
                           : SetFormat(ConsumeFront(format_, 2))
                                 .ConsumeNextArg('*')
                     : SetFormat(ParseDigits(ConsumeFront(format_)).format);
  }

  // Consume the length characters.
  constexpr ConvParser ParseLength() const {
    return SetFormat(ConsumeAnyOf(format_, "lLhjztq"));
  }

  // Consume the conversion character and verify that it matches `args_`.
  // `error_` is set if it doesn't match.
  constexpr ConvParser ParseConversion() const {
    return is_positional_
               ? VerifyPositional({ConsumeFront(format_), arg_position_},
                                  GetChar(format_, 0))
               : ConsumeNextArg(GetChar(format_, 0))
                     .SetFormat(ConsumeFront(format_));
  }

  constexpr ConvParser(string_view format, ConvList args, bool error,
                       int arg_position, bool is_positional)
      : format_(format),
        args_(args),
        error_(error),
        arg_position_(arg_position),
        is_positional_(is_positional) {}

 public:
  constexpr ConvParser(string_view format, ConvList args, bool is_positional)
      : format_(format),
        args_(args),
        error_(false),
        arg_position_(0),
        is_positional_(is_positional) {}

  // Consume the whole conversion specifier.
  // `format()` will be set to the character after the conversion character.
  // `error()` will be set if any of the arguments do not match.
  constexpr ConvParser Run() const {
    return (is_positional_ ? ParseArgPosition(ParsePositional(format_)) : *this)
        .ParseFlags()
        .ParseWidth()
        .ParsePrecision()
        .ParseLength()
        .ParseConversion();
  }

  constexpr string_view format() const { return format_; }
  constexpr ConvList args() const { return args_; }
  constexpr bool error() const { return error_; }
  constexpr bool is_positional() const { return is_positional_; }

 private:
  string_view format_;
  // Current list of arguments. If we are not in positional mode we will consume
  // from the front.
  ConvList args_;
  bool error_;
  // Holds the argument position of the conversion character, if we are in
  // positional mode. Otherwise, it is unspecified.
  int arg_position_;
  // Whether we are in positional mode.
  // It changes the behavior of '*' and where to find the converted argument.
  bool is_positional_;
};

// Parses a whole format expression.
// See FormatParser::Run().
class FormatParser {
  static constexpr bool FoundPercent(string_view format) {
    return format.empty() ||
           (GetChar(format, 0) == '%' && GetChar(format, 1) != '%');
  }

  // We use an inner function to increase the recursion limit.
  // The inner function consumes up to `limit` characters on every run.
  // This increases the limit from 512 to ~512*limit.
  static constexpr string_view ConsumeNonPercentInner(string_view format,
                                                      int limit = 20) {
    return FoundPercent(format) || !limit
               ? format
               : ConsumeNonPercentInner(
                     ConsumeFront(format, GetChar(format, 0) == '%' &&
                                                  GetChar(format, 1) == '%'
                                              ? 2
                                              : 1),
                     limit - 1);
  }

  // Consume characters until the next conversion spec %.
  // It skips %%.
  static constexpr string_view ConsumeNonPercent(string_view format) {
    return FoundPercent(format)
               ? format
               : ConsumeNonPercent(ConsumeNonPercentInner(format));
  }

  static constexpr bool IsPositional(string_view format) {
    return IsDigit(GetChar(format, 0)) ? IsPositional(ConsumeFront(format))
                                       : GetChar(format, 0) == '$';
  }

  constexpr bool RunImpl(bool is_positional) const {
    // In non-positional mode we require all arguments to be consumed.
    // In positional mode just reaching the end of the format without errors is
    // enough.
    return (format_.empty() && (is_positional || args_.count == 0)) ||
           (!format_.empty() &&
            ValidateArg(
                ConvParser(ConsumeFront(format_), args_, is_positional).Run()));
  }

  constexpr bool ValidateArg(ConvParser conv) const {
    return !conv.error() && FormatParser(conv.format(), conv.args())
                                .RunImpl(conv.is_positional());
  }

 public:
  constexpr FormatParser(string_view format, ConvList args)
      : format_(ConsumeNonPercent(format)), args_(args) {}

  // Runs the parser for `format` and `args`.
  // It verifies that the format is valid and that all conversion specifiers
  // match the arguments passed.
  // In non-positional mode it also verfies that all arguments are consumed.
  constexpr bool Run() const {
    return RunImpl(!format_.empty() && IsPositional(ConsumeFront(format_)));
  }

 private:
  string_view format_;
  // Current list of arguments.
  // If we are not in positional mode we will consume from the front and will
  // have to be empty in the end.
  ConvList args_;
};

template <FormatConversionCharSet... C>
constexpr bool ValidFormatImpl(string_view format) {
  return FormatParser(format,
                      {ConvListT<sizeof...(C)>{{C...}}.list, sizeof...(C)})
      .Run();
}

#endif  // ABSL_INTERNAL_ENABLE_FORMAT_CHECKER

}  // namespace str_format_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_STRINGS_INTERNAL_STR_FORMAT_CHECKER_H_

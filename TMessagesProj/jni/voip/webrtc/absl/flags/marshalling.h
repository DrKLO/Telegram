//
//  Copyright 2019 The Abseil Authors.
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
//
// -----------------------------------------------------------------------------
// File: marshalling.h
// -----------------------------------------------------------------------------
//
// This header file defines the API for extending Abseil flag support to
// custom types, and defines the set of overloads for fundamental types.
//
// Out of the box, the Abseil flags library supports the following types:
//
// * `bool`
// * `int16_t`
// * `uint16_t`
// * `int32_t`
// * `uint32_t`
// * `int64_t`
// * `uint64_t`
// * `float`
// * `double`
// * `std::string`
// * `std::vector<std::string>`
// * `absl::LogSeverity` (provided natively for layering reasons)
//
// Note that support for integral types is implemented using overloads for
// variable-width fundamental types (`short`, `int`, `long`, etc.). However,
// you should prefer the fixed-width integral types (`int32_t`, `uint64_t`,
// etc.) we've noted above within flag definitions.
//
// In addition, several Abseil libraries provide their own custom support for
// Abseil flags. Documentation for these formats is provided in the type's
// `AbslParseFlag()` definition.
//
// The Abseil time library provides the following support for civil time values:
//
// * `absl::CivilSecond`
// * `absl::CivilMinute`
// * `absl::CivilHour`
// * `absl::CivilDay`
// * `absl::CivilMonth`
// * `absl::CivilYear`
//
// and also provides support for the following absolute time values:
//
// * `absl::Duration`
// * `absl::Time`
//
// Additional support for Abseil types will be noted here as it is added.
//
// You can also provide your own custom flags by adding overloads for
// `AbslParseFlag()` and `AbslUnparseFlag()` to your type definitions. (See
// below.)
//
// -----------------------------------------------------------------------------
// Adding Type Support for Abseil Flags
// -----------------------------------------------------------------------------
//
// To add support for your user-defined type, add overloads of `AbslParseFlag()`
// and `AbslUnparseFlag()` as free (non-member) functions to your type. If `T`
// is a class type, these functions can be friend function definitions. These
// overloads must be added to the same namespace where the type is defined, so
// that they can be discovered by Argument-Dependent Lookup (ADL).
//
// Example:
//
//   namespace foo {
//
//   enum OutputMode { kPlainText, kHtml };
//
//   // AbslParseFlag converts from a string to OutputMode.
//   // Must be in same namespace as OutputMode.
//
//   // Parses an OutputMode from the command line flag value `text`. Returns
//   // `true` and sets `*mode` on success; returns `false` and sets `*error`
//   // on failure.
//   bool AbslParseFlag(absl::string_view text,
//                      OutputMode* mode,
//                      std::string* error) {
//     if (text == "plaintext") {
//       *mode = kPlainText;
//       return true;
//     }
//     if (text == "html") {
//       *mode = kHtml;
//      return true;
//     }
//     *error = "unknown value for enumeration";
//     return false;
//  }
//
//  // AbslUnparseFlag converts from an OutputMode to a string.
//  // Must be in same namespace as OutputMode.
//
//  // Returns a textual flag value corresponding to the OutputMode `mode`.
//  std::string AbslUnparseFlag(OutputMode mode) {
//    switch (mode) {
//      case kPlainText: return "plaintext";
//      case kHtml: return "html";
//    }
//    return absl::StrCat(mode);
//  }
//
// Notice that neither `AbslParseFlag()` nor `AbslUnparseFlag()` are class
// members, but free functions. `AbslParseFlag/AbslUnparseFlag()` overloads
// for a type should only be declared in the same file and namespace as said
// type. The proper `AbslParseFlag/AbslUnparseFlag()` implementations for a
// given type will be discovered via Argument-Dependent Lookup (ADL).
//
// `AbslParseFlag()` may need, in turn, to parse simpler constituent types
// using `absl::ParseFlag()`. For example, a custom struct `MyFlagType`
// consisting of a `std::pair<int, std::string>` would add an `AbslParseFlag()`
// overload for its `MyFlagType` like so:
//
// Example:
//
//   namespace my_flag_type {
//
//   struct MyFlagType {
//     std::pair<int, std::string> my_flag_data;
//   };
//
//   bool AbslParseFlag(absl::string_view text, MyFlagType* flag,
//                      std::string* err);
//
//   std::string AbslUnparseFlag(const MyFlagType&);
//
//   // Within the implementation, `AbslParseFlag()` will, in turn invoke
//   // `absl::ParseFlag()` on its constituent `int` and `std::string` types
//   // (which have built-in Abseil flag support).
//
//   bool AbslParseFlag(absl::string_view text, MyFlagType* flag,
//                      std::string* err) {
//     std::pair<absl::string_view, absl::string_view> tokens =
//         absl::StrSplit(text, ',');
//     if (!absl::ParseFlag(tokens.first, &flag->my_flag_data.first, err))
//         return false;
//     if (!absl::ParseFlag(tokens.second, &flag->my_flag_data.second, err))
//         return false;
//     return true;
//   }
//
//   // Similarly, for unparsing, we can simply invoke `absl::UnparseFlag()` on
//   // the constituent types.
//   std::string AbslUnparseFlag(const MyFlagType& flag) {
//     return absl::StrCat(absl::UnparseFlag(flag.my_flag_data.first),
//                         ",",
//                         absl::UnparseFlag(flag.my_flag_data.second));
//   }
#ifndef ABSL_FLAGS_MARSHALLING_H_
#define ABSL_FLAGS_MARSHALLING_H_

#include <string>
#include <vector>

#include "absl/base/config.h"
#include "absl/strings/string_view.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace flags_internal {

// Overloads of `AbslParseFlag()` and `AbslUnparseFlag()` for fundamental types.
bool AbslParseFlag(absl::string_view, bool*, std::string*);
bool AbslParseFlag(absl::string_view, short*, std::string*);           // NOLINT
bool AbslParseFlag(absl::string_view, unsigned short*, std::string*);  // NOLINT
bool AbslParseFlag(absl::string_view, int*, std::string*);             // NOLINT
bool AbslParseFlag(absl::string_view, unsigned int*, std::string*);    // NOLINT
bool AbslParseFlag(absl::string_view, long*, std::string*);            // NOLINT
bool AbslParseFlag(absl::string_view, unsigned long*, std::string*);   // NOLINT
bool AbslParseFlag(absl::string_view, long long*, std::string*);       // NOLINT
bool AbslParseFlag(absl::string_view, unsigned long long*,             // NOLINT
                   std::string*);
bool AbslParseFlag(absl::string_view, float*, std::string*);
bool AbslParseFlag(absl::string_view, double*, std::string*);
bool AbslParseFlag(absl::string_view, std::string*, std::string*);
bool AbslParseFlag(absl::string_view, std::vector<std::string>*, std::string*);

template <typename T>
bool InvokeParseFlag(absl::string_view input, T* dst, std::string* err) {
  // Comment on next line provides a good compiler error message if T
  // does not have AbslParseFlag(absl::string_view, T*, std::string*).
  return AbslParseFlag(input, dst, err);  // Is T missing AbslParseFlag?
}

// Strings and std:: containers do not have the same overload resolution
// considerations as fundamental types. Naming these 'AbslUnparseFlag' means we
// can avoid the need for additional specializations of Unparse (below).
std::string AbslUnparseFlag(absl::string_view v);
std::string AbslUnparseFlag(const std::vector<std::string>&);

template <typename T>
std::string Unparse(const T& v) {
  // Comment on next line provides a good compiler error message if T does not
  // have UnparseFlag.
  return AbslUnparseFlag(v);  // Is T missing AbslUnparseFlag?
}

// Overloads for builtin types.
std::string Unparse(bool v);
std::string Unparse(short v);               // NOLINT
std::string Unparse(unsigned short v);      // NOLINT
std::string Unparse(int v);                 // NOLINT
std::string Unparse(unsigned int v);        // NOLINT
std::string Unparse(long v);                // NOLINT
std::string Unparse(unsigned long v);       // NOLINT
std::string Unparse(long long v);           // NOLINT
std::string Unparse(unsigned long long v);  // NOLINT
std::string Unparse(float v);
std::string Unparse(double v);

}  // namespace flags_internal

// ParseFlag()
//
// Parses a string value into a flag value of type `T`. Do not add overloads of
// this function for your type directly; instead, add an `AbslParseFlag()`
// free function as documented above.
//
// Some implementations of `AbslParseFlag()` for types which consist of other,
// constituent types which already have Abseil flag support, may need to call
// `absl::ParseFlag()` on those consituent string values. (See above.)
template <typename T>
inline bool ParseFlag(absl::string_view input, T* dst, std::string* error) {
  return flags_internal::InvokeParseFlag(input, dst, error);
}

// UnparseFlag()
//
// Unparses a flag value of type `T` into a string value. Do not add overloads
// of this function for your type directly; instead, add an `AbslUnparseFlag()`
// free function as documented above.
//
// Some implementations of `AbslUnparseFlag()` for types which consist of other,
// constituent types which already have Abseil flag support, may want to call
// `absl::UnparseFlag()` on those constituent types. (See above.)
template <typename T>
inline std::string UnparseFlag(const T& v) {
  return flags_internal::Unparse(v);
}

// Overloads for `absl::LogSeverity` can't (easily) appear alongside that type's
// definition because it is layered below flags.  See proper documentation in
// base/log_severity.h.
enum class LogSeverity : int;
bool AbslParseFlag(absl::string_view, absl::LogSeverity*, std::string*);
std::string AbslUnparseFlag(absl::LogSeverity);

ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_FLAGS_MARSHALLING_H_

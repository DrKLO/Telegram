//
// Copyright 2017 The Abseil Authors.
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
#ifndef ABSL_STRINGS_INTERNAL_STR_FORMAT_EXTENSION_H_
#define ABSL_STRINGS_INTERNAL_STR_FORMAT_EXTENSION_H_

#include <limits.h>

#include <cstddef>
#include <cstring>
#include <ostream>

#include "absl/base/config.h"
#include "absl/base/port.h"
#include "absl/meta/type_traits.h"
#include "absl/strings/internal/str_format/output.h"
#include "absl/strings/string_view.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace str_format_internal {

class FormatRawSinkImpl {
 public:
  // Implicitly convert from any type that provides the hook function as
  // described above.
  template <typename T, decltype(str_format_internal::InvokeFlush(
                            std::declval<T*>(), string_view()))* = nullptr>
  FormatRawSinkImpl(T* raw)  // NOLINT
      : sink_(raw), write_(&FormatRawSinkImpl::Flush<T>) {}

  void Write(string_view s) { write_(sink_, s); }

  template <typename T>
  static FormatRawSinkImpl Extract(T s) {
    return s.sink_;
  }

 private:
  template <typename T>
  static void Flush(void* r, string_view s) {
    str_format_internal::InvokeFlush(static_cast<T*>(r), s);
  }

  void* sink_;
  void (*write_)(void*, string_view);
};

// An abstraction to which conversions write their string data.
class FormatSinkImpl {
 public:
  explicit FormatSinkImpl(FormatRawSinkImpl raw) : raw_(raw) {}

  ~FormatSinkImpl() { Flush(); }

  void Flush() {
    raw_.Write(string_view(buf_, pos_ - buf_));
    pos_ = buf_;
  }

  void Append(size_t n, char c) {
    if (n == 0) return;
    size_ += n;
    auto raw_append = [&](size_t count) {
      memset(pos_, c, count);
      pos_ += count;
    };
    while (n > Avail()) {
      n -= Avail();
      if (Avail() > 0) {
        raw_append(Avail());
      }
      Flush();
    }
    raw_append(n);
  }

  void Append(string_view v) {
    size_t n = v.size();
    if (n == 0) return;
    size_ += n;
    if (n >= Avail()) {
      Flush();
      raw_.Write(v);
      return;
    }
    memcpy(pos_, v.data(), n);
    pos_ += n;
  }

  size_t size() const { return size_; }

  // Put 'v' to 'sink' with specified width, precision, and left flag.
  bool PutPaddedString(string_view v, int w, int p, bool l);

  template <typename T>
  T Wrap() {
    return T(this);
  }

  template <typename T>
  static FormatSinkImpl* Extract(T* s) {
    return s->sink_;
  }

 private:
  size_t Avail() const { return buf_ + sizeof(buf_) - pos_; }

  FormatRawSinkImpl raw_;
  size_t size_ = 0;
  char* pos_ = buf_;
  char buf_[1024];
};

struct Flags {
  bool basic : 1;     // fastest conversion: no flags, width, or precision
  bool left : 1;      // "-"
  bool show_pos : 1;  // "+"
  bool sign_col : 1;  // " "
  bool alt : 1;       // "#"
  bool zero : 1;      // "0"
  std::string ToString() const;
  friend std::ostream& operator<<(std::ostream& os, const Flags& v) {
    return os << v.ToString();
  }
};

// clang-format off
#define ABSL_INTERNAL_CONVERSION_CHARS_EXPAND_(X_VAL, X_SEP) \
  /* text */ \
  X_VAL(c) X_SEP X_VAL(C) X_SEP X_VAL(s) X_SEP X_VAL(S) X_SEP \
  /* ints */ \
  X_VAL(d) X_SEP X_VAL(i) X_SEP X_VAL(o) X_SEP \
  X_VAL(u) X_SEP X_VAL(x) X_SEP X_VAL(X) X_SEP \
  /* floats */ \
  X_VAL(f) X_SEP X_VAL(F) X_SEP X_VAL(e) X_SEP X_VAL(E) X_SEP \
  X_VAL(g) X_SEP X_VAL(G) X_SEP X_VAL(a) X_SEP X_VAL(A) X_SEP \
  /* misc */ \
  X_VAL(n) X_SEP X_VAL(p)

enum class FormatConversionChar : uint8_t {
    c, C, s, S,              // text
    d, i, o, u, x, X,        // int
    f, F, e, E, g, G, a, A,  // float
    n, p,                    // misc
    kNone
};
// clang-format on

inline FormatConversionChar FormatConversionCharFromChar(char c) {
  switch (c) {
#define ABSL_INTERNAL_X_VAL(id) \
  case #id[0]:                  \
    return FormatConversionChar::id;
    ABSL_INTERNAL_CONVERSION_CHARS_EXPAND_(ABSL_INTERNAL_X_VAL, )
#undef ABSL_INTERNAL_X_VAL
  }
  return FormatConversionChar::kNone;
}

inline int FormatConversionCharRadix(FormatConversionChar c) {
  switch (c) {
    case FormatConversionChar::x:
    case FormatConversionChar::X:
    case FormatConversionChar::a:
    case FormatConversionChar::A:
    case FormatConversionChar::p:
      return 16;
    case FormatConversionChar::o:
      return 8;
    default:
      return 10;
  }
}

inline bool FormatConversionCharIsUpper(FormatConversionChar c) {
  switch (c) {
    case FormatConversionChar::X:
    case FormatConversionChar::F:
    case FormatConversionChar::E:
    case FormatConversionChar::G:
    case FormatConversionChar::A:
      return true;
    default:
      return false;
  }
}

inline bool FormatConversionCharIsSigned(FormatConversionChar c) {
  switch (c) {
    case FormatConversionChar::d:
    case FormatConversionChar::i:
      return true;
    default:
      return false;
  }
}

inline bool FormatConversionCharIsIntegral(FormatConversionChar c) {
  switch (c) {
    case FormatConversionChar::d:
    case FormatConversionChar::i:
    case FormatConversionChar::u:
    case FormatConversionChar::o:
    case FormatConversionChar::x:
    case FormatConversionChar::X:
      return true;
    default:
      return false;
  }
}

inline bool FormatConversionCharIsFloat(FormatConversionChar c) {
  switch (c) {
    case FormatConversionChar::a:
    case FormatConversionChar::e:
    case FormatConversionChar::f:
    case FormatConversionChar::g:
    case FormatConversionChar::A:
    case FormatConversionChar::E:
    case FormatConversionChar::F:
    case FormatConversionChar::G:
      return true;
    default:
      return false;
  }
}

inline char FormatConversionCharToChar(FormatConversionChar c) {
  switch (c) {
#define ABSL_INTERNAL_X_VAL(e)  \
  case FormatConversionChar::e: \
    return #e[0];
#define ABSL_INTERNAL_X_SEP
    ABSL_INTERNAL_CONVERSION_CHARS_EXPAND_(ABSL_INTERNAL_X_VAL,
                                           ABSL_INTERNAL_X_SEP)
    case FormatConversionChar::kNone:
      return '\0';
#undef ABSL_INTERNAL_X_VAL
#undef ABSL_INTERNAL_X_SEP
  }
  return '\0';
}

// The associated char.
inline std::ostream& operator<<(std::ostream& os, FormatConversionChar v) {
  char c = FormatConversionCharToChar(v);
  if (!c) c = '?';
  return os << c;
}

struct FormatConversionSpecImplFriend;

class FormatConversionSpec {
 public:
  // Width and precison are not specified, no flags are set.
  bool is_basic() const { return flags_.basic; }
  bool has_left_flag() const { return flags_.left; }
  bool has_show_pos_flag() const { return flags_.show_pos; }
  bool has_sign_col_flag() const { return flags_.sign_col; }
  bool has_alt_flag() const { return flags_.alt; }
  bool has_zero_flag() const { return flags_.zero; }

  FormatConversionChar conversion_char() const {
    // Keep this field first in the struct . It generates better code when
    // accessing it when ConversionSpec is passed by value in registers.
    static_assert(offsetof(FormatConversionSpec, conv_) == 0, "");
    return conv_;
  }

  // Returns the specified width. If width is unspecfied, it returns a negative
  // value.
  int width() const { return width_; }
  // Returns the specified precision. If precision is unspecfied, it returns a
  // negative value.
  int precision() const { return precision_; }

 private:
  friend struct str_format_internal::FormatConversionSpecImplFriend;
  FormatConversionChar conv_ = FormatConversionChar::kNone;
  Flags flags_;
  int width_;
  int precision_;
};

struct FormatConversionSpecImplFriend final {
  static void SetFlags(Flags f, FormatConversionSpec* conv) {
    conv->flags_ = f;
  }
  static void SetConversionChar(FormatConversionChar c,
                                FormatConversionSpec* conv) {
    conv->conv_ = c;
  }
  static void SetWidth(int w, FormatConversionSpec* conv) { conv->width_ = w; }
  static void SetPrecision(int p, FormatConversionSpec* conv) {
    conv->precision_ = p;
  }
  static std::string FlagsToString(const FormatConversionSpec& spec) {
    return spec.flags_.ToString();
  }
};

constexpr uint64_t FormatConversionCharToConvValue(char conv) {
  return
#define ABSL_INTERNAL_CHAR_SET_CASE(c)                                       \
  conv == #c[0]                                                              \
      ? (uint64_t{1} << (1 + static_cast<uint8_t>(FormatConversionChar::c))) \
      :
      ABSL_INTERNAL_CONVERSION_CHARS_EXPAND_(ABSL_INTERNAL_CHAR_SET_CASE, )
#undef ABSL_INTERNAL_CHAR_SET_CASE
                  conv == '*'
          ? 1
          : 0;
}

enum class FormatConversionCharSet : uint64_t {
#define ABSL_INTERNAL_CHAR_SET_CASE(c) \
  c = FormatConversionCharToConvValue(#c[0]),
  ABSL_INTERNAL_CONVERSION_CHARS_EXPAND_(ABSL_INTERNAL_CHAR_SET_CASE, )
#undef ABSL_INTERNAL_CHAR_SET_CASE

  // Used for width/precision '*' specification.
  kStar = FormatConversionCharToConvValue('*'),
  // Some predefined values:
  kIntegral = d | i | u | o | x | X,
  kFloating = a | e | f | g | A | E | F | G,
  kNumeric = kIntegral | kFloating,
  kString = s,
  kPointer = p
};

// Type safe OR operator.
// We need this for two reasons:
//  1. operator| on enums makes them decay to integers and the result is an
//     integer. We need the result to stay as an enum.
//  2. We use "enum class" which would not work even if we accepted the decay.
constexpr FormatConversionCharSet operator|(FormatConversionCharSet a,
                                            FormatConversionCharSet b) {
  return FormatConversionCharSet(static_cast<uint64_t>(a) |
                                 static_cast<uint64_t>(b));
}

// Overloaded conversion functions to support absl::ParsedFormat.
// Get a conversion with a single character in it.
constexpr FormatConversionCharSet ToFormatConversionCharSet(char c) {
  return static_cast<FormatConversionCharSet>(
      FormatConversionCharToConvValue(c));
}

// Get a conversion with a single character in it.
constexpr FormatConversionCharSet ToFormatConversionCharSet(
    FormatConversionCharSet c) {
  return c;
}

template <typename T>
void ToFormatConversionCharSet(T) = delete;

// Checks whether `c` exists in `set`.
constexpr bool Contains(FormatConversionCharSet set, char c) {
  return (static_cast<uint64_t>(set) & FormatConversionCharToConvValue(c)) != 0;
}

// Checks whether all the characters in `c` are contained in `set`
constexpr bool Contains(FormatConversionCharSet set,
                        FormatConversionCharSet c) {
  return (static_cast<uint64_t>(set) & static_cast<uint64_t>(c)) ==
         static_cast<uint64_t>(c);
}

// Return type of the AbslFormatConvert() functions.
// The FormatConversionCharSet template parameter is used to inform the
// framework of what conversion characters are supported by that
// AbslFormatConvert routine.
template <FormatConversionCharSet C>
struct FormatConvertResult {
  static constexpr FormatConversionCharSet kConv = C;
  bool value;
};

template <FormatConversionCharSet C>
constexpr FormatConversionCharSet FormatConvertResult<C>::kConv;

// Return capacity - used, clipped to a minimum of 0.
inline size_t Excess(size_t used, size_t capacity) {
  return used < capacity ? capacity - used : 0;
}

// Type alias for use during migration.
using ConversionChar = FormatConversionChar;
using ConversionSpec = FormatConversionSpec;
using Conv = FormatConversionCharSet;
template <FormatConversionCharSet C>
using ConvertResult = FormatConvertResult<C>;

}  // namespace str_format_internal

ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_STRINGS_INTERNAL_STR_FORMAT_EXTENSION_H_

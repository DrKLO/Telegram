// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_LOCATION_H_
#define BASE_LOCATION_H_

#include <stddef.h>

#include <cassert>
#include <functional>
#include <string>

#include "base/base_export.h"
#include "base/debug/debugging_buildflags.h"
#include "base/hash/hash.h"
#include "build/build_config.h"

namespace base {

#if defined(__has_builtin)
// Clang allows detection of these builtins.
#define SUPPORTS_LOCATION_BUILTINS                                       \
  (__has_builtin(__builtin_FUNCTION) && __has_builtin(__builtin_FILE) && \
   __has_builtin(__builtin_LINE))
#elif defined(COMPILER_GCC) && __GNUC__ >= 7
// GCC has supported these for a long time, but they point at the function
// declaration in the case of default arguments, rather than at the call site.
#define SUPPORTS_LOCATION_BUILTINS 1
#else
#define SUPPORTS_LOCATION_BUILTINS 0
#endif

// Location provides basic info where of an object was constructed, or was
// significantly brought to life.
class BASE_EXPORT Location {
 public:
  Location();
  Location(const Location& other);

  // Only initializes the file name and program counter, the source information
  // will be null for the strings, and -1 for the line number.
  // TODO(http://crbug.com/760702) remove file name from this constructor.
  Location(const char* file_name, const void* program_counter);

  // Constructor should be called with a long-lived char*, such as __FILE__.
  // It assumes the provided value will persist as a global constant, and it
  // will not make a copy of it.
  Location(const char* function_name,
           const char* file_name,
           int line_number,
           const void* program_counter);

  // Comparator for hash map insertion. The program counter should uniquely
  // identify a location.
  bool operator==(const Location& other) const {
    return program_counter_ == other.program_counter_;
  }

  // Returns true if there is source code location info. If this is false,
  // the Location object only contains a program counter or is
  // default-initialized (the program counter is also null).
  bool has_source_info() const { return function_name_ && file_name_; }

  // Will be nullptr for default initialized Location objects and when source
  // names are disabled.
  const char* function_name() const { return function_name_; }

  // Will be nullptr for default initialized Location objects and when source
  // names are disabled.
  const char* file_name() const { return file_name_; }

  // Will be -1 for default initialized Location objects and when source names
  // are disabled.
  int line_number() const { return line_number_; }

  // The address of the code generating this Location object. Should always be
  // valid except for default initialized Location objects, which will be
  // nullptr.
  const void* program_counter() const { return program_counter_; }

  // Converts to the most user-readable form possible. If function and filename
  // are not available, this will return "pc:<hex address>".
  std::string ToString() const;

#if !BUILDFLAG(FROM_HERE_USES_LOCATION_BUILTINS)
#if !BUILDFLAG(ENABLE_LOCATION_SOURCE)
  static Location CreateFromHere(const char* file_name);
#else
  static Location CreateFromHere(const char* function_name,
                                 const char* file_name,
                                 int line_number);
#endif
#endif

#if SUPPORTS_LOCATION_BUILTINS && BUILDFLAG(ENABLE_LOCATION_SOURCE)
  static Location Current(const char* function_name = __builtin_FUNCTION(),
                          const char* file_name = __builtin_FILE(),
                          int line_number = __builtin_LINE());
#elif SUPPORTS_LOCATION_BUILTINS
  static Location Current(const char* file_name = __builtin_FILE());
#else
  static Location Current();
#endif

 private:
  const char* function_name_ = nullptr;
  const char* file_name_ = nullptr;
  int line_number_ = -1;
  const void* program_counter_ = nullptr;
};

BASE_EXPORT const void* GetProgramCounter();

#if BUILDFLAG(FROM_HERE_USES_LOCATION_BUILTINS)

#define FROM_HERE ::base::Location::Current()

// The macros defined here will expand to the current function.
#elif BUILDFLAG(ENABLE_LOCATION_SOURCE)

// Full source information should be included.
#define FROM_HERE ::base::Location::CreateFromHere(__func__, __FILE__, __LINE__)

#else

// TODO(http://crbug.com/760702) remove the __FILE__ argument from these calls.
#define FROM_HERE ::base::Location::CreateFromHere(__FILE__)

#endif

}  // namespace base

namespace std {

// Specialization for using Location in hash tables.
template <>
struct hash<::base::Location> {
  std::size_t operator()(const ::base::Location& loc) const {
    const void* program_counter = loc.program_counter();
    return base::FastHash(base::as_bytes(base::make_span(&program_counter, 1)));
  }
};

}  // namespace std

#endif  // BASE_LOCATION_H_

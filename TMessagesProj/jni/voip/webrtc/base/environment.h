// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ENVIRONMENT_H_
#define BASE_ENVIRONMENT_H_

#include <map>
#include <memory>
#include <string>

#include "base/base_export.h"
#include "base/strings/string16.h"
#include "base/strings/string_piece.h"
#include "build/build_config.h"

namespace base {

namespace env_vars {

#if defined(OS_POSIX) || defined(OS_FUCHSIA)
BASE_EXPORT extern const char kHome[];
#endif

}  // namespace env_vars

class BASE_EXPORT Environment {
 public:
  virtual ~Environment();

  // Returns the appropriate platform-specific instance.
  static std::unique_ptr<Environment> Create();

  // Gets an environment variable's value and stores it in |result|.
  // Returns false if the key is unset.
  virtual bool GetVar(StringPiece variable_name, std::string* result) = 0;

  // Syntactic sugar for GetVar(variable_name, nullptr);
  virtual bool HasVar(StringPiece variable_name);

  // Returns true on success, otherwise returns false. This method should not
  // be called in a multi-threaded process.
  virtual bool SetVar(StringPiece variable_name,
                      const std::string& new_value) = 0;

  // Returns true on success, otherwise returns false. This method should not
  // be called in a multi-threaded process.
  virtual bool UnSetVar(StringPiece variable_name) = 0;
};

#if defined(OS_WIN)
using NativeEnvironmentString = std::wstring;
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
using NativeEnvironmentString = std::string;
#endif
using EnvironmentMap =
    std::map<NativeEnvironmentString, NativeEnvironmentString>;

}  // namespace base

#endif  // BASE_ENVIRONMENT_H_

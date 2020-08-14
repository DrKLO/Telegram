// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_VALUE_CONVERSIONS_H_
#define BASE_VALUE_CONVERSIONS_H_

// This file contains methods to convert things to a |Value| and back.

#include <memory>

#include "base/base_export.h"
#include "base/compiler_specific.h"

namespace base {

class FilePath;
class Time;
class TimeDelta;
class UnguessableToken;
class Value;

// In GetValueAs*() functions, the caller owns the object pointed by the output
// parameter, e.g. |file_path|. If false is returned, the value of the object
// pointed by the output parameter is not changed. It is okay to pass nullptr to
// the output parameter.

// Warning: The Values involved could be stored on persistent storage like files
// on disks. Therefore, changes in implementation could lead to data corruption
// and must be done with caution.

BASE_EXPORT Value CreateFilePathValue(const FilePath& in_value);
BASE_EXPORT bool GetValueAsFilePath(const Value& value,
                                    FilePath* file_path) WARN_UNUSED_RESULT;

BASE_EXPORT Value CreateTimeValue(const Time& time);
BASE_EXPORT bool GetValueAsTime(const Value& value,
                                Time* time) WARN_UNUSED_RESULT;

BASE_EXPORT Value CreateTimeDeltaValue(const TimeDelta& time_delta);
BASE_EXPORT bool GetValueAsTimeDelta(const Value& value,
                                     TimeDelta* time_delta) WARN_UNUSED_RESULT;

BASE_EXPORT Value CreateUnguessableTokenValue(const UnguessableToken& token);
BASE_EXPORT bool GetValueAsUnguessableToken(const Value& value,
                                            UnguessableToken* token)
    WARN_UNUSED_RESULT;

}  // namespace base

#endif  // BASE_VALUE_CONVERSIONS_H_

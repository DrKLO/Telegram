// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_BUILD_TIME_H_
#define BASE_BUILD_TIME_H_

#include "base/base_export.h"
#include "base/time/time.h"

namespace base {

// GetBuildTime returns the time at which the current binary was built,
// rounded down to 5:00:00am at the start of the day in UTC.
//
// This uses a generated file, which doesn't trigger a rebuild when the time
// changes. It will, however, be updated whenever //build/util/LASTCHANGE
// changes.
//
// This value should only be considered accurate to within a day.
// It will always be in the past.
//
// Note: If the build is not official (i.e. is_official_build = false)
// this time will be set to 5:00:00am on the most recent first Sunday
// of a month.
Time BASE_EXPORT GetBuildTime();

}  // namespace base

#endif  // BASE_BUILD_TIME_H_

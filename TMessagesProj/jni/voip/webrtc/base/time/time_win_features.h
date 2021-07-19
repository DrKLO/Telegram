// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TIME_TIME_WIN_FEATURES_H_
#define BASE_TIME_TIME_WIN_FEATURES_H_

#include "base/base_export.h"

namespace base {

struct Feature;

// Slow the maximum interrupt timer on battery power to 8 ms, instead of the
// default 4 ms.
extern const BASE_EXPORT Feature kSlowDCTimerInterruptsWin;

}  // namespace base

#endif  // BASE_TIME_TIME_WIN_FEATURES_H_

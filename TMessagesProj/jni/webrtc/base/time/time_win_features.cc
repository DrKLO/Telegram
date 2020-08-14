// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/time/time_win_features.h"

#include "base/feature_list.h"

namespace base {

const Feature kSlowDCTimerInterruptsWin{"SlowDCTimerInterruptsWin",
                                        FEATURE_DISABLED_BY_DEFAULT};

}  // namespace base

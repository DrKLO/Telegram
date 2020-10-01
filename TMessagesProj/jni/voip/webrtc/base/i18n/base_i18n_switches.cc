// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/i18n/base_i18n_switches.h"

namespace switches {

// Force the UI to a specific direction. Valid values are "ltr" (left-to-right)
// and "rtl" (right-to-left).
const char kForceUIDirection[]   = "force-ui-direction";

// Force the text rendering to a specific direction. Valid values are "ltr"
// (left-to-right) and "rtl" (right-to-left). Only tested meaningfully with
// RTL.
const char kForceTextDirection[] = "force-text-direction";

const char kForceDirectionLTR[]  = "ltr";
const char kForceDirectionRTL[]  = "rtl";

}  // namespace switches

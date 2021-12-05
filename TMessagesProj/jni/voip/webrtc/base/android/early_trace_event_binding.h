// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ANDROID_EARLY_TRACE_EVENT_BINDING_H_
#define BASE_ANDROID_EARLY_TRACE_EVENT_BINDING_H_

#include "base/base_export.h"

namespace base {
namespace android {

// Returns true if background startup tracing flag was set on the previous
// startup.
BASE_EXPORT bool GetBackgroundStartupTracingFlag();

// Sets a flag to chrome application preferences to enable startup tracing next
// time the app is started.
BASE_EXPORT void SetBackgroundStartupTracingFlag(bool enabled);

}  // namespace android
}  // namespace base

#endif  // BASE_ANDROID_EARLY_TRACE_EVENT_BINDING_H_

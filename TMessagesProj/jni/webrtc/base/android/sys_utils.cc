// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/sys_utils.h"

#include <memory>

#include "base/android/build_info.h"
#include "base/base_jni_headers/SysUtils_jni.h"
#include "base/process/process_metrics.h"
#include "base/system/sys_info.h"
#include "base/trace_event/trace_event.h"

namespace base {
namespace android {

bool SysUtils::IsLowEndDeviceFromJni() {
  JNIEnv* env = AttachCurrentThread();
  return Java_SysUtils_isLowEndDevice(env);
}

bool SysUtils::IsCurrentlyLowMemory() {
  JNIEnv* env = AttachCurrentThread();
  return Java_SysUtils_isCurrentlyLowMemory(env);
}

// Logs the number of minor / major page faults to tracing (and also the time to
// collect) the metrics. Does nothing if tracing is not enabled.
static void JNI_SysUtils_LogPageFaultCountToTracing(JNIEnv* env) {
  // This is racy, but we are OK losing data, and collecting it is potentially
  // expensive (reading and parsing a file).
  bool enabled;
  TRACE_EVENT_CATEGORY_GROUP_ENABLED("startup", &enabled);
  if (!enabled)
    return;
  TRACE_EVENT_BEGIN2("memory", "CollectPageFaultCount", "minor", 0, "major", 0);
  std::unique_ptr<base::ProcessMetrics> process_metrics(
      base::ProcessMetrics::CreateProcessMetrics(
          base::GetCurrentProcessHandle()));
  base::PageFaultCounts counts;
  process_metrics->GetPageFaultCounts(&counts);
  TRACE_EVENT_END2("memory", "CollectPageFaults", "minor", counts.minor,
                   "major", counts.major);
}

}  // namespace android

}  // namespace base

// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// This is a separate file so that users of process metrics don't need to
// include windows.h unless they need IoCounters.

#ifndef BASE_PROCESS_PROCESS_METRICS_IOCOUNTERS_H_
#define BASE_PROCESS_PROCESS_METRICS_IOCOUNTERS_H_

#include <stdint.h>

#include "base/process/process_metrics.h"
#include "build/build_config.h"

#if defined(OS_WIN)
#include <windows.h>
#endif

namespace base {

#if defined(OS_WIN)
struct IoCounters : public IO_COUNTERS {};
#elif defined(OS_POSIX)
struct IoCounters {
  uint64_t ReadOperationCount;
  uint64_t WriteOperationCount;
  uint64_t OtherOperationCount;
  uint64_t ReadTransferCount;
  uint64_t WriteTransferCount;
  uint64_t OtherTransferCount;
};
#endif

}  // namespace base

#endif  // BASE_PROCESS_PROCESS_METRICS_IOCOUNTERS_H_

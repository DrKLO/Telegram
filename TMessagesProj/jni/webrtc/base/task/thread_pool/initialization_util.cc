// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/initialization_util.h"

#include <algorithm>

#include "base/numerics/ranges.h"
#include "base/system/sys_info.h"

namespace base {

int RecommendedMaxNumberOfThreadsInThreadGroup(int min,
                                               int max,
                                               double cores_multiplier,
                                               int offset) {
  const int num_of_cores = SysInfo::NumberOfProcessors();
  const int threads = std::ceil<int>(num_of_cores * cores_multiplier) + offset;
  return ClampToRange(threads, min, max);
}

}  // namespace base

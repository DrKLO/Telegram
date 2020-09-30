// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/posix/can_lower_nice_to.h"

#include <limits.h>
#include <sys/resource.h>
#include <sys/types.h>
#include <unistd.h>

#include "build/build_config.h"

// Not defined on AIX by default.
#if defined(OS_AIX)
#if defined(RLIMIT_NICE)
#error Assumption about OS_AIX is incorrect
#endif
#define RLIMIT_NICE 20
#endif

namespace base {
namespace internal {

bool CanLowerNiceTo(int nice_value) {
  // On a POSIX system, the nice value of a thread can be lowered 1. by the root
  // user, 2. by a user with the CAP_SYS_NICE permission or 3. by any user if
  // the target value is within the range allowed by RLIMIT_NICE.

  // 1. Check for root user.
  if (geteuid() == 0)
    return true;

  // 2. Skip checking the CAP_SYS_NICE permission because it would require
  // libcap.so.

  // 3. Check whether the target value is within the range allowed by
  // RLIMIT_NICE.
  //
  // NZERO should be defined in <limits.h> per POSIX, and should be at least 20.
  // (NZERO-1) is the highest possible niceness value (i.e. lowest priority).
  // Most platforms use NZERO=20.
  //
  // RLIMIT_NICE tells us how much we can reduce niceness (increase priority) if
  // we start at NZERO. For example, if NZERO is 20 and the rlimit is 30, we can
  // lower niceness anywhere within the [-10, 19] range (20 - 30 = -10).
  //
  // So, we are allowed to reduce niceness to a minimum of NZERO - rlimit:
  struct rlimit rlim;
  if (getrlimit(RLIMIT_NICE, &rlim) != 0)
    return false;
  const int lowest_nice_allowed = NZERO - static_cast<int>(rlim.rlim_cur);

  // And lowering niceness to |nice_value| is allowed if it is greater than or
  // equal to the limit:
  return nice_value >= lowest_nice_allowed;
}

}  // namespace internal
}  // namespace base

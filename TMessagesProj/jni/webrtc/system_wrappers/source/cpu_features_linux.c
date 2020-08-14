/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdlib.h>
#include <string.h>
#include <features.h>
#ifndef __GLIBC_PREREQ
#define __GLIBC_PREREQ(a, b) 0
#endif
#if __GLIBC_PREREQ(2, 16)
#include <sys/auxv.h>
#else
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <link.h>
#endif
#include "rtc_base/system/arch.h"
#include "system_wrappers/include/cpu_features_wrapper.h"

#if defined(WEBRTC_ARCH_ARM_FAMILY)
#include <asm/hwcap.h>

uint64_t WebRtc_GetCPUFeaturesARM(void) {
  uint64_t result = 0;
  int architecture = 0;
  unsigned long hwcap = 0;
  const char* platform = NULL;
#if __GLIBC_PREREQ(2, 16)
  hwcap = getauxval(AT_HWCAP);
  platform = (const char*)getauxval(AT_PLATFORM);
#else
  ElfW(auxv_t) auxv;
  int fd = open("/proc/self/auxv", O_RDONLY);
  if (fd >= 0) {
    while (hwcap == 0 || platform == NULL) {
      if (read(fd, &auxv, sizeof(auxv)) < (ssize_t)sizeof(auxv)) {
        if (errno == EINTR)
          continue;
        break;
      }
      switch (auxv.a_type) {
        case AT_HWCAP:
          hwcap = auxv.a_un.a_val;
          break;
        case AT_PLATFORM:
          platform = (const char*)auxv.a_un.a_val;
          break;
      }
    }
    close(fd);
  }
#endif  // __GLIBC_PREREQ(2,16)
#if defined(__aarch64__)
  architecture = 8;
  if ((hwcap & HWCAP_FP) != 0)
    result |= kCPUFeatureVFPv3;
  if ((hwcap & HWCAP_ASIMD) != 0)
    result |= kCPUFeatureNEON;
#else
  if (platform != NULL) {
    /* expect a string in the form "v6l" or "v7l", etc.
     */
    if (platform[0] == 'v' && '0' <= platform[1] && platform[1] <= '9' &&
        (platform[2] == 'l' || platform[2] == 'b')) {
      architecture = platform[1] - '0';
    }
  }
  if ((hwcap & HWCAP_VFPv3) != 0)
    result |= kCPUFeatureVFPv3;
  if ((hwcap & HWCAP_NEON) != 0)
    result |= kCPUFeatureNEON;
#endif
  if (architecture >= 7)
    result |= kCPUFeatureARMv7;
  if (architecture >= 6)
    result |= kCPUFeatureLDREXSTREX;
  return result;
}
#endif  // WEBRTC_ARCH_ARM_FAMILY

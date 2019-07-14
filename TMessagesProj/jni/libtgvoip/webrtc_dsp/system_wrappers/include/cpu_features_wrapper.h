/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_INCLUDE_CPU_FEATURES_WRAPPER_H_
#define WEBRTC_SYSTEM_WRAPPERS_INCLUDE_CPU_FEATURES_WRAPPER_H_

#if defined(__cplusplus) || defined(c_plusplus)
extern "C" {
#endif

#include "typedefs.h"

// List of features in x86.
typedef enum {
  kSSE2,
  kSSE3
} CPUFeature;

// List of features in ARM.
enum {
  kCPUFeatureARMv7       = (1 << 0),
  kCPUFeatureVFPv3       = (1 << 1),
  kCPUFeatureNEON        = (1 << 2),
  kCPUFeatureLDREXSTREX  = (1 << 3)
};

typedef int (*WebRtc_CPUInfo)(CPUFeature feature);

// Returns true if the CPU supports the feature.
extern WebRtc_CPUInfo WebRtc_GetCPUInfo;

// No CPU feature is available => straight C path.
extern WebRtc_CPUInfo WebRtc_GetCPUInfoNoASM;

// Return the features in an ARM device.
// It detects the features in the hardware platform, and returns supported
// values in the above enum definition as a bitmask.
extern uint64_t WebRtc_GetCPUFeaturesARM(void);

#if defined(__cplusplus) || defined(c_plusplus)
}  // extern "C"
#endif

#endif // WEBRTC_SYSTEM_WRAPPERS_INCLUDE_CPU_FEATURES_WRAPPER_H_

/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC_AEC_COMMON_H_
#define MODULES_AUDIO_PROCESSING_AEC_AEC_COMMON_H_

#ifdef _MSC_VER /* visual c++ */
#define ALIGN16_BEG __declspec(align(16))
#define ALIGN16_END
#else /* gcc or icc */
#define ALIGN16_BEG
#define ALIGN16_END __attribute__((aligned(16)))
#endif

#ifdef __cplusplus
namespace webrtc {
#endif

extern ALIGN16_BEG const float ALIGN16_END WebRtcAec_sqrtHanning[65];
extern ALIGN16_BEG const float ALIGN16_END WebRtcAec_weightCurve[65];
extern ALIGN16_BEG const float ALIGN16_END WebRtcAec_overDriveCurve[65];
extern const float WebRtcAec_kExtendedSmoothingCoefficients[2][2];
extern const float WebRtcAec_kNormalSmoothingCoefficients[2][2];
extern const float WebRtcAec_kMinFarendPSD;

#ifdef __cplusplus
}  // namespace webrtc
#endif

#endif  // MODULES_AUDIO_PROCESSING_AEC_AEC_COMMON_H_

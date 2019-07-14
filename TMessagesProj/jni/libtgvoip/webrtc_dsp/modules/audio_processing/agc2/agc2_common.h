/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_AGC2_COMMON_H_
#define MODULES_AUDIO_PROCESSING_AGC2_AGC2_COMMON_H_

#include <stddef.h>

namespace webrtc {

constexpr float kMinFloatS16Value = -32768.f;
constexpr float kMaxFloatS16Value = 32767.f;
constexpr float kMaxAbsFloatS16Value = 32768.0f;

constexpr size_t kFrameDurationMs = 10;
constexpr size_t kSubFramesInFrame = 20;
constexpr size_t kMaximalNumberOfSamplesPerChannel = 480;

constexpr float kAttackFilterConstant = 0.f;

// Adaptive digital gain applier settings below.
constexpr float kMaxGainChangePerSecondDb = 3.f;
constexpr float kMaxGainChangePerFrameDb =
    kMaxGainChangePerSecondDb * kFrameDurationMs / 1000.f;
constexpr float kHeadroomDbfs = 1.f;
constexpr float kMaxGainDb = 30.f;
constexpr float kInitialAdaptiveDigitalGainDb = 8.f;
// At what limiter levels should we start decreasing the adaptive digital gain.
constexpr float kLimiterThresholdForAgcGainDbfs = -kHeadroomDbfs;

// This parameter must be tuned together with the noise estimator.
constexpr float kMaxNoiseLevelDbfs = -50.f;

// This is the threshold for speech. Speech frames are used for updating the
// speech level, measuring the amount of speech, and decide when to allow target
// gain reduction.
constexpr float kVadConfidenceThreshold = 0.4f;

// The amount of 'memory' of the Level Estimator. Decides leak factors.
constexpr size_t kFullBufferSizeMs = 1600;
constexpr float kFullBufferLeakFactor = 1.f - 1.f / kFullBufferSizeMs;

constexpr float kInitialSpeechLevelEstimateDbfs = -30.f;

// Saturation Protector settings.
float GetInitialSaturationMarginDb();
float GetExtraSaturationMarginOffsetDb();

constexpr size_t kPeakEnveloperSuperFrameLengthMs = 400;
static_assert(kFullBufferSizeMs % kPeakEnveloperSuperFrameLengthMs == 0,
              "Full buffer size should be a multiple of super frame length for "
              "optimal Saturation Protector performance.");

constexpr size_t kPeakEnveloperBufferSize =
    kFullBufferSizeMs / kPeakEnveloperSuperFrameLengthMs + 1;

// This value is 10 ** (-1/20 * frame_size_ms / satproc_attack_ms),
// where satproc_attack_ms is 5000.
constexpr float kSaturationProtectorAttackConstant = 0.9988493699365052f;

// This value is 10 ** (-1/20 * frame_size_ms / satproc_decay_ms),
// where satproc_decay_ms is 1000.
constexpr float kSaturationProtectorDecayConstant = 0.9997697679981565f;

// This is computed from kDecayMs by
// 10 ** (-1/20 * subframe_duration / kDecayMs).
// |subframe_duration| is |kFrameDurationMs / kSubFramesInFrame|.
// kDecayMs is defined in agc2_testing_common.h
constexpr float kDecayFilterConstant = 0.9998848773724686f;

// Number of interpolation points for each region of the limiter.
// These values have been tuned to limit the interpolated gain curve error given
// the limiter parameters and allowing a maximum error of +/- 32768^-1.
constexpr size_t kInterpolatedGainCurveKneePoints = 22;
constexpr size_t kInterpolatedGainCurveBeyondKneePoints = 10;
constexpr size_t kInterpolatedGainCurveTotalPoints =
    kInterpolatedGainCurveKneePoints + kInterpolatedGainCurveBeyondKneePoints;

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_AGC2_COMMON_H_

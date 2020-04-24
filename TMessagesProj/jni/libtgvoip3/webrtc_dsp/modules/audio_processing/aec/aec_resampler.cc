/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/* Resamples a signal to an arbitrary rate. Used by the AEC to compensate for
 * clock skew by resampling the farend signal.
 */

#include "modules/audio_processing/aec/aec_resampler.h"

#include <stdlib.h>
#include <string.h>

#include "modules/audio_processing/aec/aec_core.h"
#include "rtc_base/checks.h"

namespace webrtc {

enum { kEstimateLengthFrames = 400 };

typedef struct {
  float buffer[kResamplerBufferSize];
  float position;

  int deviceSampleRateHz;
  int skewData[kEstimateLengthFrames];
  int skewDataIndex;
  float skewEstimate;
} AecResampler;

static int EstimateSkew(const int* rawSkew,
                        int size,
                        int deviceSampleRateHz,
                        float* skewEst);

void* WebRtcAec_CreateResampler() {
  return malloc(sizeof(AecResampler));
}

int WebRtcAec_InitResampler(void* resampInst, int deviceSampleRateHz) {
  AecResampler* obj = static_cast<AecResampler*>(resampInst);
  memset(obj->buffer, 0, sizeof(obj->buffer));
  obj->position = 0.0;

  obj->deviceSampleRateHz = deviceSampleRateHz;
  memset(obj->skewData, 0, sizeof(obj->skewData));
  obj->skewDataIndex = 0;
  obj->skewEstimate = 0.0;

  return 0;
}

void WebRtcAec_FreeResampler(void* resampInst) {
  AecResampler* obj = static_cast<AecResampler*>(resampInst);
  free(obj);
}

void WebRtcAec_ResampleLinear(void* resampInst,
                              const float* inspeech,
                              size_t size,
                              float skew,
                              float* outspeech,
                              size_t* size_out) {
  AecResampler* obj = static_cast<AecResampler*>(resampInst);

  float* y;
  float be, tnew;
  size_t tn, mm;

  RTC_DCHECK_LE(size, 2 * FRAME_LEN);
  RTC_DCHECK(resampInst);
  RTC_DCHECK(inspeech);
  RTC_DCHECK(outspeech);
  RTC_DCHECK(size_out);

  // Add new frame data in lookahead
  memcpy(&obj->buffer[FRAME_LEN + kResamplingDelay], inspeech,
         size * sizeof(inspeech[0]));

  // Sample rate ratio
  be = 1 + skew;

  // Loop over input frame
  mm = 0;
  y = &obj->buffer[FRAME_LEN];  // Point at current frame

  tnew = be * mm + obj->position;
  tn = (size_t)tnew;

  while (tn < size) {
    // Interpolation
    outspeech[mm] = y[tn] + (tnew - tn) * (y[tn + 1] - y[tn]);
    mm++;

    tnew = be * mm + obj->position;
    tn = static_cast<int>(tnew);
  }

  *size_out = mm;
  obj->position += (*size_out) * be - size;

  // Shift buffer
  memmove(obj->buffer, &obj->buffer[size],
          (kResamplerBufferSize - size) * sizeof(obj->buffer[0]));
}

int WebRtcAec_GetSkew(void* resampInst, int rawSkew, float* skewEst) {
  AecResampler* obj = static_cast<AecResampler*>(resampInst);
  int err = 0;

  if (obj->skewDataIndex < kEstimateLengthFrames) {
    obj->skewData[obj->skewDataIndex] = rawSkew;
    obj->skewDataIndex++;
  } else if (obj->skewDataIndex == kEstimateLengthFrames) {
    err = EstimateSkew(obj->skewData, kEstimateLengthFrames,
                       obj->deviceSampleRateHz, skewEst);
    obj->skewEstimate = *skewEst;
    obj->skewDataIndex++;
  } else {
    *skewEst = obj->skewEstimate;
  }

  return err;
}

int EstimateSkew(const int* rawSkew,
                 int size,
                 int deviceSampleRateHz,
                 float* skewEst) {
  const int absLimitOuter = static_cast<int>(0.04f * deviceSampleRateHz);
  const int absLimitInner = static_cast<int>(0.0025f * deviceSampleRateHz);
  int i = 0;
  int n = 0;
  float rawAvg = 0;
  float err = 0;
  float rawAbsDev = 0;
  int upperLimit = 0;
  int lowerLimit = 0;
  float cumSum = 0;
  float x = 0;
  float x2 = 0;
  float y = 0;
  float xy = 0;
  float xAvg = 0;
  float denom = 0;
  float skew = 0;

  *skewEst = 0;  // Set in case of error below.
  for (i = 0; i < size; i++) {
    if ((rawSkew[i] < absLimitOuter && rawSkew[i] > -absLimitOuter)) {
      n++;
      rawAvg += rawSkew[i];
    }
  }

  if (n == 0) {
    return -1;
  }
  RTC_DCHECK_GT(n, 0);
  rawAvg /= n;

  for (i = 0; i < size; i++) {
    if ((rawSkew[i] < absLimitOuter && rawSkew[i] > -absLimitOuter)) {
      err = rawSkew[i] - rawAvg;
      rawAbsDev += err >= 0 ? err : -err;
    }
  }
  RTC_DCHECK_GT(n, 0);
  rawAbsDev /= n;
  upperLimit = static_cast<int>(rawAvg + 5 * rawAbsDev + 1);  // +1 for ceiling.
  lowerLimit = static_cast<int>(rawAvg - 5 * rawAbsDev - 1);  // -1 for floor.

  n = 0;
  for (i = 0; i < size; i++) {
    if ((rawSkew[i] < absLimitInner && rawSkew[i] > -absLimitInner) ||
        (rawSkew[i] < upperLimit && rawSkew[i] > lowerLimit)) {
      n++;
      cumSum += rawSkew[i];
      x += n;
      x2 += n * n;
      y += cumSum;
      xy += n * cumSum;
    }
  }

  if (n == 0) {
    return -1;
  }
  RTC_DCHECK_GT(n, 0);
  xAvg = x / n;
  denom = x2 - xAvg * x;

  if (denom != 0) {
    skew = (xy - xAvg * y) / denom;
  }

  *skewEst = skew;
  return 0;
}
}  // namespace webrtc

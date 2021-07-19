/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef COMMON_AUDIO_INCLUDE_AUDIO_UTIL_H_
#define COMMON_AUDIO_INCLUDE_AUDIO_UTIL_H_

#include <stdint.h>

#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>

#include "rtc_base/checks.h"

namespace webrtc {

typedef std::numeric_limits<int16_t> limits_int16;

// The conversion functions use the following naming convention:
// S16:      int16_t [-32768, 32767]
// Float:    float   [-1.0, 1.0]
// FloatS16: float   [-32768.0, 32768.0]
// Dbfs: float [-20.0*log(10, 32768), 0] = [-90.3, 0]
// The ratio conversion functions use this naming convention:
// Ratio: float (0, +inf)
// Db: float (-inf, +inf)
static inline float S16ToFloat(int16_t v) {
  constexpr float kScaling = 1.f / 32768.f;
  return v * kScaling;
}

static inline int16_t FloatS16ToS16(float v) {
  v = std::min(v, 32767.f);
  v = std::max(v, -32768.f);
  return static_cast<int16_t>(v + std::copysign(0.5f, v));
}

static inline int16_t FloatToS16(float v) {
  v *= 32768.f;
  v = std::min(v, 32767.f);
  v = std::max(v, -32768.f);
  return static_cast<int16_t>(v + std::copysign(0.5f, v));
}

static inline float FloatToFloatS16(float v) {
  v = std::min(v, 1.f);
  v = std::max(v, -1.f);
  return v * 32768.f;
}

static inline float FloatS16ToFloat(float v) {
  v = std::min(v, 32768.f);
  v = std::max(v, -32768.f);
  constexpr float kScaling = 1.f / 32768.f;
  return v * kScaling;
}

void FloatToS16(const float* src, size_t size, int16_t* dest);
void S16ToFloat(const int16_t* src, size_t size, float* dest);
void S16ToFloatS16(const int16_t* src, size_t size, float* dest);
void FloatS16ToS16(const float* src, size_t size, int16_t* dest);
void FloatToFloatS16(const float* src, size_t size, float* dest);
void FloatS16ToFloat(const float* src, size_t size, float* dest);

inline float DbToRatio(float v) {
  return std::pow(10.0f, v / 20.0f);
}

inline float DbfsToFloatS16(float v) {
  static constexpr float kMaximumAbsFloatS16 = -limits_int16::min();
  return DbToRatio(v) * kMaximumAbsFloatS16;
}

inline float FloatS16ToDbfs(float v) {
  RTC_DCHECK_GE(v, 0);

  // kMinDbfs is equal to -20.0 * log10(-limits_int16::min())
  static constexpr float kMinDbfs = -90.30899869919436f;
  if (v <= 1.0f) {
    return kMinDbfs;
  }
  // Equal to 20 * log10(v / (-limits_int16::min()))
  return 20.0f * std::log10(v) + kMinDbfs;
}

// Copy audio from |src| channels to |dest| channels unless |src| and |dest|
// point to the same address. |src| and |dest| must have the same number of
// channels, and there must be sufficient space allocated in |dest|.
template <typename T>
void CopyAudioIfNeeded(const T* const* src,
                       int num_frames,
                       int num_channels,
                       T* const* dest) {
  for (int i = 0; i < num_channels; ++i) {
    if (src[i] != dest[i]) {
      std::copy(src[i], src[i] + num_frames, dest[i]);
    }
  }
}

// Deinterleave audio from |interleaved| to the channel buffers pointed to
// by |deinterleaved|. There must be sufficient space allocated in the
// |deinterleaved| buffers (|num_channel| buffers with |samples_per_channel|
// per buffer).
template <typename T>
void Deinterleave(const T* interleaved,
                  size_t samples_per_channel,
                  size_t num_channels,
                  T* const* deinterleaved) {
  for (size_t i = 0; i < num_channels; ++i) {
    T* channel = deinterleaved[i];
    size_t interleaved_idx = i;
    for (size_t j = 0; j < samples_per_channel; ++j) {
      channel[j] = interleaved[interleaved_idx];
      interleaved_idx += num_channels;
    }
  }
}

// Interleave audio from the channel buffers pointed to by |deinterleaved| to
// |interleaved|. There must be sufficient space allocated in |interleaved|
// (|samples_per_channel| * |num_channels|).
template <typename T>
void Interleave(const T* const* deinterleaved,
                size_t samples_per_channel,
                size_t num_channels,
                T* interleaved) {
  for (size_t i = 0; i < num_channels; ++i) {
    const T* channel = deinterleaved[i];
    size_t interleaved_idx = i;
    for (size_t j = 0; j < samples_per_channel; ++j) {
      interleaved[interleaved_idx] = channel[j];
      interleaved_idx += num_channels;
    }
  }
}

// Copies audio from a single channel buffer pointed to by |mono| to each
// channel of |interleaved|. There must be sufficient space allocated in
// |interleaved| (|samples_per_channel| * |num_channels|).
template <typename T>
void UpmixMonoToInterleaved(const T* mono,
                            int num_frames,
                            int num_channels,
                            T* interleaved) {
  int interleaved_idx = 0;
  for (int i = 0; i < num_frames; ++i) {
    for (int j = 0; j < num_channels; ++j) {
      interleaved[interleaved_idx++] = mono[i];
    }
  }
}

template <typename T, typename Intermediate>
void DownmixToMono(const T* const* input_channels,
                   size_t num_frames,
                   int num_channels,
                   T* out) {
  for (size_t i = 0; i < num_frames; ++i) {
    Intermediate value = input_channels[0][i];
    for (int j = 1; j < num_channels; ++j) {
      value += input_channels[j][i];
    }
    out[i] = value / num_channels;
  }
}

// Downmixes an interleaved multichannel signal to a single channel by averaging
// all channels.
template <typename T, typename Intermediate>
void DownmixInterleavedToMonoImpl(const T* interleaved,
                                  size_t num_frames,
                                  int num_channels,
                                  T* deinterleaved) {
  RTC_DCHECK_GT(num_channels, 0);
  RTC_DCHECK_GT(num_frames, 0);

  const T* const end = interleaved + num_frames * num_channels;

  while (interleaved < end) {
    const T* const frame_end = interleaved + num_channels;

    Intermediate value = *interleaved++;
    while (interleaved < frame_end) {
      value += *interleaved++;
    }

    *deinterleaved++ = value / num_channels;
  }
}

template <typename T>
void DownmixInterleavedToMono(const T* interleaved,
                              size_t num_frames,
                              int num_channels,
                              T* deinterleaved);

template <>
void DownmixInterleavedToMono<int16_t>(const int16_t* interleaved,
                                       size_t num_frames,
                                       int num_channels,
                                       int16_t* deinterleaved);

}  // namespace webrtc

#endif  // COMMON_AUDIO_INCLUDE_AUDIO_UTIL_H_

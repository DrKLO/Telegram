/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio/audio_frame.h"

#include <string.h>

#include "rtc_base/checks.h"
#include "rtc_base/timeutils.h"

namespace webrtc {

AudioFrame::AudioFrame() {
  // Visual Studio doesn't like this in the class definition.
  static_assert(sizeof(data_) == kMaxDataSizeBytes, "kMaxDataSizeBytes");
}

void AudioFrame::Reset() {
  ResetWithoutMuting();
  muted_ = true;
}

void AudioFrame::ResetWithoutMuting() {
  // TODO(wu): Zero is a valid value for |timestamp_|. We should initialize
  // to an invalid value, or add a new member to indicate invalidity.
  timestamp_ = 0;
  elapsed_time_ms_ = -1;
  ntp_time_ms_ = -1;
  samples_per_channel_ = 0;
  sample_rate_hz_ = 0;
  num_channels_ = 0;
  speech_type_ = kUndefined;
  vad_activity_ = kVadUnknown;
  profile_timestamp_ms_ = 0;
}

void AudioFrame::UpdateFrame(uint32_t timestamp,
                             const int16_t* data,
                             size_t samples_per_channel,
                             int sample_rate_hz,
                             SpeechType speech_type,
                             VADActivity vad_activity,
                             size_t num_channels) {
  timestamp_ = timestamp;
  samples_per_channel_ = samples_per_channel;
  sample_rate_hz_ = sample_rate_hz;
  speech_type_ = speech_type;
  vad_activity_ = vad_activity;
  num_channels_ = num_channels;

  const size_t length = samples_per_channel * num_channels;
  RTC_CHECK_LE(length, kMaxDataSizeSamples);
  if (data != nullptr) {
    memcpy(data_, data, sizeof(int16_t) * length);
    muted_ = false;
  } else {
    muted_ = true;
  }
}

void AudioFrame::CopyFrom(const AudioFrame& src) {
  if (this == &src)
    return;

  timestamp_ = src.timestamp_;
  elapsed_time_ms_ = src.elapsed_time_ms_;
  ntp_time_ms_ = src.ntp_time_ms_;
  muted_ = src.muted();
  samples_per_channel_ = src.samples_per_channel_;
  sample_rate_hz_ = src.sample_rate_hz_;
  speech_type_ = src.speech_type_;
  vad_activity_ = src.vad_activity_;
  num_channels_ = src.num_channels_;

  const size_t length = samples_per_channel_ * num_channels_;
  RTC_CHECK_LE(length, kMaxDataSizeSamples);
  if (!src.muted()) {
    memcpy(data_, src.data(), sizeof(int16_t) * length);
    muted_ = false;
  }
}

void AudioFrame::UpdateProfileTimeStamp() {
  profile_timestamp_ms_ = rtc::TimeMillis();
}

int64_t AudioFrame::ElapsedProfileTimeMs() const {
  if (profile_timestamp_ms_ == 0) {
    // Profiling has not been activated.
    return -1;
  }
  return rtc::TimeSince(profile_timestamp_ms_);
}

const int16_t* AudioFrame::data() const {
  return muted_ ? empty_data() : data_;
}

// TODO(henrik.lundin) Can we skip zeroing the buffer?
// See https://bugs.chromium.org/p/webrtc/issues/detail?id=5647.
int16_t* AudioFrame::mutable_data() {
  if (muted_) {
    memset(data_, 0, kMaxDataSizeBytes);
    muted_ = false;
  }
  return data_;
}

void AudioFrame::Mute() {
  muted_ = true;
}

bool AudioFrame::muted() const {
  return muted_;
}

// static
const int16_t* AudioFrame::empty_data() {
  static int16_t* null_data = new int16_t[kMaxDataSizeSamples]();
  return &null_data[0];
}

}  // namespace webrtc

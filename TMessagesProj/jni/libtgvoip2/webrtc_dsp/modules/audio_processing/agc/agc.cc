/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc/agc.h"

#include <cmath>
#include <cstdlib>
#include <vector>

#include "modules/audio_processing/agc/loudness_histogram.h"
#include "modules/audio_processing/agc/utility.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

const int kDefaultLevelDbfs = -18;
const int kNumAnalysisFrames = 100;
const double kActivityThreshold = 0.3;

}  // namespace

Agc::Agc()
    : target_level_loudness_(Dbfs2Loudness(kDefaultLevelDbfs)),
      target_level_dbfs_(kDefaultLevelDbfs),
      histogram_(LoudnessHistogram::Create(kNumAnalysisFrames)),
      inactive_histogram_(LoudnessHistogram::Create()) {}

Agc::~Agc() {}

float Agc::AnalyzePreproc(const int16_t* audio, size_t length) {
  RTC_DCHECK_GT(length, 0);
  size_t num_clipped = 0;
  for (size_t i = 0; i < length; ++i) {
    if (audio[i] == 32767 || audio[i] == -32768)
      ++num_clipped;
  }
  return 1.0f * num_clipped / length;
}

void Agc::Process(const int16_t* audio, size_t length, int sample_rate_hz) {
  vad_.ProcessChunk(audio, length, sample_rate_hz);
  const std::vector<double>& rms = vad_.chunkwise_rms();
  const std::vector<double>& probabilities =
      vad_.chunkwise_voice_probabilities();
  RTC_DCHECK_EQ(rms.size(), probabilities.size());
  for (size_t i = 0; i < rms.size(); ++i) {
    histogram_->Update(rms[i], probabilities[i]);
  }
}

bool Agc::GetRmsErrorDb(int* error) {
  if (!error) {
    RTC_NOTREACHED();
    return false;
  }

  if (histogram_->num_updates() < kNumAnalysisFrames) {
    // We haven't yet received enough frames.
    return false;
  }

  if (histogram_->AudioContent() < kNumAnalysisFrames * kActivityThreshold) {
    // We are likely in an inactive segment.
    return false;
  }

  double loudness = Linear2Loudness(histogram_->CurrentRms());
  *error = std::floor(Loudness2Db(target_level_loudness_ - loudness) + 0.5);
  histogram_->Reset();
  return true;
}

void Agc::Reset() {
  histogram_->Reset();
}

int Agc::set_target_level_dbfs(int level) {
  // TODO(turajs): just some arbitrary sanity check. We can come up with better
  // limits. The upper limit should be chosen such that the risk of clipping is
  // low. The lower limit should not result in a too quiet signal.
  if (level >= 0 || level <= -100)
    return -1;
  target_level_dbfs_ = level;
  target_level_loudness_ = Dbfs2Loudness(level);
  return 0;
}

int Agc::target_level_dbfs() const {
  return target_level_dbfs_;
}

float Agc::voice_probability() const {
  return vad_.last_voice_probability();
}

}  // namespace webrtc

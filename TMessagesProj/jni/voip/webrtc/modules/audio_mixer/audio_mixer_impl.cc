/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_mixer/audio_mixer_impl.h"

#include <stdint.h>

#include <algorithm>
#include <iterator>
#include <type_traits>
#include <utility>

#include "modules/audio_mixer/audio_frame_manipulator.h"
#include "modules/audio_mixer/default_output_rate_calculator.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

struct AudioMixerImpl::SourceStatus {
  explicit SourceStatus(Source* audio_source) : audio_source(audio_source) {}
  Source* audio_source = nullptr;

  // A frame that will be passed to audio_source->GetAudioFrameWithInfo.
  AudioFrame audio_frame;
};

namespace {

std::vector<std::unique_ptr<AudioMixerImpl::SourceStatus>>::const_iterator
FindSourceInList(
    AudioMixerImpl::Source const* audio_source,
    std::vector<std::unique_ptr<AudioMixerImpl::SourceStatus>> const*
        audio_source_list) {
  return std::find_if(
      audio_source_list->begin(), audio_source_list->end(),
      [audio_source](const std::unique_ptr<AudioMixerImpl::SourceStatus>& p) {
        return p->audio_source == audio_source;
      });
}
}  // namespace

struct AudioMixerImpl::HelperContainers {
  void resize(size_t size) {
    audio_to_mix.resize(size);
    preferred_rates.resize(size);
  }

  std::vector<AudioFrame*> audio_to_mix;
  std::vector<int> preferred_rates;
};

AudioMixerImpl::AudioMixerImpl(
    std::unique_ptr<OutputRateCalculator> output_rate_calculator,
    bool use_limiter)
    : output_rate_calculator_(std::move(output_rate_calculator)),
      audio_source_list_(),
      helper_containers_(std::make_unique<HelperContainers>()),
      frame_combiner_(use_limiter) {}

AudioMixerImpl::~AudioMixerImpl() {}

rtc::scoped_refptr<AudioMixerImpl> AudioMixerImpl::Create() {
  return Create(std::unique_ptr<DefaultOutputRateCalculator>(
                    new DefaultOutputRateCalculator()),
                /*use_limiter=*/true);
}

rtc::scoped_refptr<AudioMixerImpl> AudioMixerImpl::Create(
    std::unique_ptr<OutputRateCalculator> output_rate_calculator,
    bool use_limiter) {
  return rtc::make_ref_counted<AudioMixerImpl>(
      std::move(output_rate_calculator), use_limiter);
}

void AudioMixerImpl::Mix(size_t number_of_channels,
                         AudioFrame* audio_frame_for_mixing) {
  TRACE_EVENT0("webrtc", "AudioMixerImpl::Mix");
  RTC_DCHECK(number_of_channels >= 1);
  MutexLock lock(&mutex_);

  size_t number_of_streams = audio_source_list_.size();

  std::transform(audio_source_list_.begin(), audio_source_list_.end(),
                 helper_containers_->preferred_rates.begin(),
                 [&](std::unique_ptr<SourceStatus>& a) {
                   return a->audio_source->PreferredSampleRate();
                 });

  int output_frequency = output_rate_calculator_->CalculateOutputRateFromRange(
      rtc::ArrayView<const int>(helper_containers_->preferred_rates.data(),
                                number_of_streams));

  frame_combiner_.Combine(GetAudioFromSources(output_frequency),
                          number_of_channels, output_frequency,
                          number_of_streams, audio_frame_for_mixing);
}

bool AudioMixerImpl::AddSource(Source* audio_source) {
  RTC_DCHECK(audio_source);
  MutexLock lock(&mutex_);
  RTC_DCHECK(FindSourceInList(audio_source, &audio_source_list_) ==
             audio_source_list_.end())
      << "Source already added to mixer";
  audio_source_list_.emplace_back(new SourceStatus(audio_source));
  helper_containers_->resize(audio_source_list_.size());
  UpdateSourceCountStats();
  return true;
}

void AudioMixerImpl::RemoveSource(Source* audio_source) {
  RTC_DCHECK(audio_source);
  MutexLock lock(&mutex_);
  const auto iter = FindSourceInList(audio_source, &audio_source_list_);
  RTC_DCHECK(iter != audio_source_list_.end()) << "Source not present in mixer";
  audio_source_list_.erase(iter);
}

rtc::ArrayView<AudioFrame* const> AudioMixerImpl::GetAudioFromSources(
    int output_frequency) {
  int audio_to_mix_count = 0;
  for (auto& source_and_status : audio_source_list_) {
    const auto audio_frame_info =
        source_and_status->audio_source->GetAudioFrameWithInfo(
            output_frequency, &source_and_status->audio_frame);
    switch (audio_frame_info) {
      case Source::AudioFrameInfo::kError:
        RTC_LOG_F(LS_WARNING)
            << "failed to GetAudioFrameWithInfo() from source";
        break;
      case Source::AudioFrameInfo::kMuted:
        break;
      case Source::AudioFrameInfo::kNormal:
        helper_containers_->audio_to_mix[audio_to_mix_count++] =
            &source_and_status->audio_frame;
    }
  }
  return rtc::ArrayView<AudioFrame* const>(
      helper_containers_->audio_to_mix.data(), audio_to_mix_count);
}

void AudioMixerImpl::UpdateSourceCountStats() {
  size_t current_source_count = audio_source_list_.size();
  // Log to the histogram whenever the maximum number of sources increases.
  if (current_source_count > max_source_count_ever_) {
    RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.Audio.AudioMixer.NewHighestSourceCount",
                                current_source_count, 1, 20, 20);
    max_source_count_ever_ = current_source_count;
  }
}
}  // namespace webrtc

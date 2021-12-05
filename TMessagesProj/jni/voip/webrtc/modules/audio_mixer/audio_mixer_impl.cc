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
#include "rtc_base/ref_counted_object.h"

namespace webrtc {

struct AudioMixerImpl::SourceStatus {
  SourceStatus(Source* audio_source, bool is_mixed, float gain)
      : audio_source(audio_source), is_mixed(is_mixed), gain(gain) {}
  Source* audio_source = nullptr;
  bool is_mixed = false;
  float gain = 0.0f;

  // A frame that will be passed to audio_source->GetAudioFrameWithInfo.
  AudioFrame audio_frame;
};

namespace {

struct SourceFrame {
  SourceFrame() = default;

  SourceFrame(AudioMixerImpl::SourceStatus* source_status,
              AudioFrame* audio_frame,
              bool muted)
      : source_status(source_status), audio_frame(audio_frame), muted(muted) {
    RTC_DCHECK(source_status);
    RTC_DCHECK(audio_frame);
    if (!muted) {
      energy = AudioMixerCalculateEnergy(*audio_frame);
    }
  }

  SourceFrame(AudioMixerImpl::SourceStatus* source_status,
              AudioFrame* audio_frame,
              bool muted,
              uint32_t energy)
      : source_status(source_status),
        audio_frame(audio_frame),
        muted(muted),
        energy(energy) {
    RTC_DCHECK(source_status);
    RTC_DCHECK(audio_frame);
  }

  AudioMixerImpl::SourceStatus* source_status = nullptr;
  AudioFrame* audio_frame = nullptr;
  bool muted = true;
  uint32_t energy = 0;
};

// ShouldMixBefore(a, b) is used to select mixer sources.
// Returns true if `a` is preferred over `b` as a source to be mixed.
bool ShouldMixBefore(const SourceFrame& a, const SourceFrame& b) {
  if (a.muted != b.muted) {
    return b.muted;
  }

  const auto a_activity = a.audio_frame->vad_activity_;
  const auto b_activity = b.audio_frame->vad_activity_;

  if (a_activity != b_activity) {
    return a_activity == AudioFrame::kVadActive;
  }

  return a.energy > b.energy;
}

void RampAndUpdateGain(
    rtc::ArrayView<const SourceFrame> mixed_sources_and_frames) {
  for (const auto& source_frame : mixed_sources_and_frames) {
    float target_gain = source_frame.source_status->is_mixed ? 1.0f : 0.0f;
    Ramp(source_frame.source_status->gain, target_gain,
         source_frame.audio_frame);
    source_frame.source_status->gain = target_gain;
  }
}

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
    audio_source_mixing_data_list.resize(size);
    ramp_list.resize(size);
    preferred_rates.resize(size);
  }

  std::vector<AudioFrame*> audio_to_mix;
  std::vector<SourceFrame> audio_source_mixing_data_list;
  std::vector<SourceFrame> ramp_list;
  std::vector<int> preferred_rates;
};

AudioMixerImpl::AudioMixerImpl(
    std::unique_ptr<OutputRateCalculator> output_rate_calculator,
    bool use_limiter,
    int max_sources_to_mix)
    : max_sources_to_mix_(max_sources_to_mix),
      output_rate_calculator_(std::move(output_rate_calculator)),
      audio_source_list_(),
      helper_containers_(std::make_unique<HelperContainers>()),
      frame_combiner_(use_limiter) {
  RTC_CHECK_GE(max_sources_to_mix, 1) << "At least one source must be mixed";
  audio_source_list_.reserve(max_sources_to_mix);
  helper_containers_->resize(max_sources_to_mix);
}

AudioMixerImpl::~AudioMixerImpl() {}

rtc::scoped_refptr<AudioMixerImpl> AudioMixerImpl::Create(
    int max_sources_to_mix) {
  return Create(std::unique_ptr<DefaultOutputRateCalculator>(
                    new DefaultOutputRateCalculator()),
                /*use_limiter=*/true, max_sources_to_mix);
}

rtc::scoped_refptr<AudioMixerImpl> AudioMixerImpl::Create(
    std::unique_ptr<OutputRateCalculator> output_rate_calculator,
    bool use_limiter,
    int max_sources_to_mix) {
  return rtc::make_ref_counted<AudioMixerImpl>(
      std::move(output_rate_calculator), use_limiter, max_sources_to_mix);
}

void AudioMixerImpl::Mix(size_t number_of_channels,
                         AudioFrame* audio_frame_for_mixing) {
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
  audio_source_list_.emplace_back(new SourceStatus(audio_source, false, 0));
  helper_containers_->resize(audio_source_list_.size());
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
  // Get audio from the audio sources and put it in the SourceFrame vector.
  int audio_source_mixing_data_count = 0;
  for (auto& source_and_status : audio_source_list_) {
    const auto audio_frame_info =
        source_and_status->audio_source->GetAudioFrameWithInfo(
            output_frequency, &source_and_status->audio_frame);

    if (audio_frame_info == Source::AudioFrameInfo::kError) {
      RTC_LOG_F(LS_WARNING) << "failed to GetAudioFrameWithInfo() from source";
      continue;
    }
    helper_containers_
        ->audio_source_mixing_data_list[audio_source_mixing_data_count++] =
        SourceFrame(source_and_status.get(), &source_and_status->audio_frame,
                    audio_frame_info == Source::AudioFrameInfo::kMuted);
  }
  rtc::ArrayView<SourceFrame> audio_source_mixing_data_view(
      helper_containers_->audio_source_mixing_data_list.data(),
      audio_source_mixing_data_count);

  // Sort frames by sorting function.
  std::sort(audio_source_mixing_data_view.begin(),
            audio_source_mixing_data_view.end(), ShouldMixBefore);

  int max_audio_frame_counter = max_sources_to_mix_;
  int ramp_list_lengh = 0;
  int audio_to_mix_count = 0;
  // Go through list in order and put unmuted frames in result list.
  for (const auto& p : audio_source_mixing_data_view) {
    // Filter muted.
    if (p.muted) {
      p.source_status->is_mixed = false;
      continue;
    }

    // Add frame to result vector for mixing.
    bool is_mixed = false;
    if (max_audio_frame_counter > 0) {
      --max_audio_frame_counter;
      helper_containers_->audio_to_mix[audio_to_mix_count++] = p.audio_frame;
      helper_containers_->ramp_list[ramp_list_lengh++] =
          SourceFrame(p.source_status, p.audio_frame, false, -1);
      is_mixed = true;
    }
    p.source_status->is_mixed = is_mixed;
  }
  RampAndUpdateGain(rtc::ArrayView<SourceFrame>(
      helper_containers_->ramp_list.data(), ramp_list_lengh));
  return rtc::ArrayView<AudioFrame* const>(
      helper_containers_->audio_to_mix.data(), audio_to_mix_count);
}

bool AudioMixerImpl::GetAudioSourceMixabilityStatusForTest(
    AudioMixerImpl::Source* audio_source) const {
  MutexLock lock(&mutex_);

  const auto iter = FindSourceInList(audio_source, &audio_source_list_);
  if (iter != audio_source_list_.end()) {
    return (*iter)->is_mixed;
  }

  RTC_LOG(LS_ERROR) << "Audio source unknown";
  return false;
}
}  // namespace webrtc

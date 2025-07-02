/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_mixer/frame_combiner.h"

#include <algorithm>
#include <array>
#include <cstdint>
#include <iterator>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "api/array_view.h"
#include "api/rtp_packet_info.h"
#include "api/rtp_packet_infos.h"
#include "common_audio/include/audio_util.h"
#include "modules/audio_mixer/audio_frame_manipulator.h"
#include "modules/audio_mixer/audio_mixer_impl.h"
#include "modules/audio_processing/include/audio_frame_view.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {
namespace {

using MixingBuffer =
    std::array<std::array<float, FrameCombiner::kMaximumChannelSize>,
               FrameCombiner::kMaximumNumberOfChannels>;

void SetAudioFrameFields(rtc::ArrayView<const AudioFrame* const> mix_list,
                         size_t number_of_channels,
                         int sample_rate,
                         size_t number_of_streams,
                         AudioFrame* audio_frame_for_mixing) {
  const size_t samples_per_channel = static_cast<size_t>(
      (sample_rate * webrtc::AudioMixerImpl::kFrameDurationInMs) / 1000);

  // TODO(minyue): Issue bugs.webrtc.org/3390.
  // Audio frame timestamp. The 'timestamp_' field is set to dummy
  // value '0', because it is only supported in the one channel case and
  // is then updated in the helper functions.
  audio_frame_for_mixing->UpdateFrame(
      0, nullptr, samples_per_channel, sample_rate, AudioFrame::kUndefined,
      AudioFrame::kVadUnknown, number_of_channels);

  if (mix_list.empty()) {
    audio_frame_for_mixing->elapsed_time_ms_ = -1;
  } else {
    audio_frame_for_mixing->timestamp_ = mix_list[0]->timestamp_;
    audio_frame_for_mixing->elapsed_time_ms_ = mix_list[0]->elapsed_time_ms_;
    audio_frame_for_mixing->ntp_time_ms_ = mix_list[0]->ntp_time_ms_;
    std::vector<RtpPacketInfo> packet_infos;
    for (const auto& frame : mix_list) {
      audio_frame_for_mixing->timestamp_ =
          std::min(audio_frame_for_mixing->timestamp_, frame->timestamp_);
      audio_frame_for_mixing->ntp_time_ms_ =
          std::min(audio_frame_for_mixing->ntp_time_ms_, frame->ntp_time_ms_);
      audio_frame_for_mixing->elapsed_time_ms_ = std::max(
          audio_frame_for_mixing->elapsed_time_ms_, frame->elapsed_time_ms_);
      packet_infos.insert(packet_infos.end(), frame->packet_infos_.begin(),
                          frame->packet_infos_.end());
    }
    audio_frame_for_mixing->packet_infos_ =
        RtpPacketInfos(std::move(packet_infos));
  }
}

void MixFewFramesWithNoLimiter(rtc::ArrayView<const AudioFrame* const> mix_list,
                               AudioFrame* audio_frame_for_mixing) {
  if (mix_list.empty()) {
    audio_frame_for_mixing->Mute();
    return;
  }
  RTC_DCHECK_LE(mix_list.size(), 1);
  std::copy(mix_list[0]->data(),
            mix_list[0]->data() +
                mix_list[0]->num_channels_ * mix_list[0]->samples_per_channel_,
            audio_frame_for_mixing->mutable_data());
}

void MixToFloatFrame(rtc::ArrayView<const AudioFrame* const> mix_list,
                     size_t samples_per_channel,
                     size_t number_of_channels,
                     MixingBuffer* mixing_buffer) {
  RTC_DCHECK_LE(samples_per_channel, FrameCombiner::kMaximumChannelSize);
  RTC_DCHECK_LE(number_of_channels, FrameCombiner::kMaximumNumberOfChannels);
  // Clear the mixing buffer.
  *mixing_buffer = {};

  // Convert to FloatS16 and mix.
  for (size_t i = 0; i < mix_list.size(); ++i) {
    const AudioFrame* const frame = mix_list[i];
    const int16_t* const frame_data = frame->data();
    for (size_t j = 0; j < std::min(number_of_channels,
                                    FrameCombiner::kMaximumNumberOfChannels);
         ++j) {
      for (size_t k = 0; k < std::min(samples_per_channel,
                                      FrameCombiner::kMaximumChannelSize);
           ++k) {
        (*mixing_buffer)[j][k] += frame_data[number_of_channels * k + j];
      }
    }
  }
}

void RunLimiter(AudioFrameView<float> mixing_buffer_view, Limiter* limiter) {
  const size_t sample_rate = mixing_buffer_view.samples_per_channel() * 1000 /
                             AudioMixerImpl::kFrameDurationInMs;
  // TODO(alessiob): Avoid calling SetSampleRate every time.
  limiter->SetSampleRate(sample_rate);
  limiter->Process(mixing_buffer_view);
}

// Both interleaves and rounds.
void InterleaveToAudioFrame(AudioFrameView<const float> mixing_buffer_view,
                            AudioFrame* audio_frame_for_mixing) {
  const size_t number_of_channels = mixing_buffer_view.num_channels();
  const size_t samples_per_channel = mixing_buffer_view.samples_per_channel();
  int16_t* const mixing_data = audio_frame_for_mixing->mutable_data();
  // Put data in the result frame.
  for (size_t i = 0; i < number_of_channels; ++i) {
    for (size_t j = 0; j < samples_per_channel; ++j) {
      mixing_data[number_of_channels * j + i] =
          FloatS16ToS16(mixing_buffer_view.channel(i)[j]);
    }
  }
}
}  // namespace

constexpr size_t FrameCombiner::kMaximumNumberOfChannels;
constexpr size_t FrameCombiner::kMaximumChannelSize;

FrameCombiner::FrameCombiner(bool use_limiter)
    : data_dumper_(new ApmDataDumper(0)),
      mixing_buffer_(
          std::make_unique<std::array<std::array<float, kMaximumChannelSize>,
                                      kMaximumNumberOfChannels>>()),
      limiter_(static_cast<size_t>(48000), data_dumper_.get(), "AudioMixer"),
      use_limiter_(use_limiter) {
  static_assert(kMaximumChannelSize * kMaximumNumberOfChannels <=
                    AudioFrame::kMaxDataSizeSamples,
                "");
}

FrameCombiner::~FrameCombiner() = default;

void FrameCombiner::Combine(rtc::ArrayView<AudioFrame* const> mix_list,
                            size_t number_of_channels,
                            int sample_rate,
                            size_t number_of_streams,
                            AudioFrame* audio_frame_for_mixing) {
  RTC_DCHECK(audio_frame_for_mixing);

  SetAudioFrameFields(mix_list, number_of_channels, sample_rate,
                      number_of_streams, audio_frame_for_mixing);

  const size_t samples_per_channel = static_cast<size_t>(
      (sample_rate * webrtc::AudioMixerImpl::kFrameDurationInMs) / 1000);

  for (const auto* frame : mix_list) {
    RTC_DCHECK_EQ(samples_per_channel, frame->samples_per_channel_);
    RTC_DCHECK_EQ(sample_rate, frame->sample_rate_hz_);
  }

  // The 'num_channels_' field of frames in 'mix_list' could be
  // different from 'number_of_channels'.
  for (auto* frame : mix_list) {
    RemixFrame(number_of_channels, frame);
  }

  if (number_of_streams <= 1) {
    MixFewFramesWithNoLimiter(mix_list, audio_frame_for_mixing);
    return;
  }

  MixToFloatFrame(mix_list, samples_per_channel, number_of_channels,
                  mixing_buffer_.get());

  const size_t output_number_of_channels =
      std::min(number_of_channels, kMaximumNumberOfChannels);
  const size_t output_samples_per_channel =
      std::min(samples_per_channel, kMaximumChannelSize);

  // Put float data in an AudioFrameView.
  std::array<float*, kMaximumNumberOfChannels> channel_pointers{};
  for (size_t i = 0; i < output_number_of_channels; ++i) {
    channel_pointers[i] = &(*mixing_buffer_.get())[i][0];
  }
  AudioFrameView<float> mixing_buffer_view(&channel_pointers[0],
                                           output_number_of_channels,
                                           output_samples_per_channel);

  if (use_limiter_) {
    RunLimiter(mixing_buffer_view, &limiter_);
  }

  InterleaveToAudioFrame(mixing_buffer_view, audio_frame_for_mixing);
}

}  // namespace webrtc

/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "audio/audio_transport_impl.h"

#include <algorithm>
#include <memory>
#include <utility>

#include "audio/remix_resample.h"
#include "audio/utility/audio_frame_operations.h"
#include "call/audio_sender.h"
#include "modules/async_audio_processing/async_audio_processing.h"
#include "modules/audio_processing/include/audio_frame_proxies.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace {

// We want to process at the lowest sample rate and channel count possible
// without losing information. Choose the lowest native rate at least equal to
// the minimum of input and codec rates, choose lowest channel count, and
// configure the audio frame.
void InitializeCaptureFrame(int input_sample_rate,
                            int send_sample_rate_hz,
                            size_t input_num_channels,
                            size_t send_num_channels,
                            AudioFrame* audio_frame) {
  RTC_DCHECK(audio_frame);
  int min_processing_rate_hz = std::min(input_sample_rate, send_sample_rate_hz);
  for (int native_rate_hz : AudioProcessing::kNativeSampleRatesHz) {
    audio_frame->sample_rate_hz_ = native_rate_hz;
    if (audio_frame->sample_rate_hz_ >= min_processing_rate_hz) {
      break;
    }
  }
  audio_frame->num_channels_ = std::min(input_num_channels, send_num_channels);
}

void ProcessCaptureFrame(uint32_t delay_ms,
                         bool key_pressed,
                         bool swap_stereo_channels,
                         AudioProcessing* audio_processing,
                         AudioFrame* audio_frame) {
  RTC_DCHECK(audio_frame);
  if (audio_processing) {
    audio_processing->set_stream_delay_ms(delay_ms);
    audio_processing->set_stream_key_pressed(key_pressed);
    int error = ProcessAudioFrame(audio_processing, audio_frame);

    RTC_DCHECK_EQ(0, error) << "ProcessStream() error: " << error;
  }

  if (swap_stereo_channels) {
    AudioFrameOperations::SwapStereoChannels(audio_frame);
  }
}

// Resample audio in |frame| to given sample rate preserving the
// channel count and place the result in |destination|.
int Resample(const AudioFrame& frame,
             const int destination_sample_rate,
             PushResampler<int16_t>* resampler,
             int16_t* destination) {
  const int number_of_channels = static_cast<int>(frame.num_channels_);
  const int target_number_of_samples_per_channel =
      destination_sample_rate / 100;
  resampler->InitializeIfNeeded(frame.sample_rate_hz_, destination_sample_rate,
                                number_of_channels);

  // TODO(yujo): make resampler take an AudioFrame, and add special case
  // handling of muted frames.
  return resampler->Resample(
      frame.data(), frame.samples_per_channel_ * number_of_channels,
      destination, number_of_channels * target_number_of_samples_per_channel);
}
}  // namespace

AudioTransportImpl::AudioTransportImpl(
    AudioMixer* mixer,
    AudioProcessing* audio_processing,
    AsyncAudioProcessing::Factory* async_audio_processing_factory)
    : audio_processing_(audio_processing),
      async_audio_processing_(
          async_audio_processing_factory
              ? async_audio_processing_factory->CreateAsyncAudioProcessing(
                    [this](std::unique_ptr<AudioFrame> frame) {
                      this->SendProcessedData(std::move(frame));
                    })
              : nullptr),
      mixer_(mixer) {
  RTC_DCHECK(mixer);
}

AudioTransportImpl::~AudioTransportImpl() {}

// Not used in Chromium. Process captured audio and distribute to all sending
// streams, and try to do this at the lowest possible sample rate.
int32_t AudioTransportImpl::RecordedDataIsAvailable(
    const void* audio_data,
    const size_t number_of_frames,
    const size_t bytes_per_sample,
    const size_t number_of_channels,
    const uint32_t sample_rate,
    const uint32_t audio_delay_milliseconds,
    const int32_t /*clock_drift*/,
    const uint32_t /*volume*/,
    const bool key_pressed,
    uint32_t& /*new_mic_volume*/) {  // NOLINT: to avoid changing APIs
  RTC_DCHECK(audio_data);
  RTC_DCHECK_GE(number_of_channels, 1);
  RTC_DCHECK_LE(number_of_channels, 2);
  RTC_DCHECK_EQ(2 * number_of_channels, bytes_per_sample);
  RTC_DCHECK_GE(sample_rate, AudioProcessing::NativeRate::kSampleRate8kHz);
  // 100 = 1 second / data duration (10 ms).
  RTC_DCHECK_EQ(number_of_frames * 100, sample_rate);
  RTC_DCHECK_LE(bytes_per_sample * number_of_frames * number_of_channels,
                AudioFrame::kMaxDataSizeBytes);

  int send_sample_rate_hz = 0;
  size_t send_num_channels = 0;
  bool swap_stereo_channels = false;
  {
    MutexLock lock(&capture_lock_);
    send_sample_rate_hz = send_sample_rate_hz_;
    send_num_channels = send_num_channels_;
    swap_stereo_channels = swap_stereo_channels_;
  }

  std::unique_ptr<AudioFrame> audio_frame(new AudioFrame());
  InitializeCaptureFrame(sample_rate, send_sample_rate_hz, number_of_channels,
                         send_num_channels, audio_frame.get());
  voe::RemixAndResample(static_cast<const int16_t*>(audio_data),
                        number_of_frames, number_of_channels, sample_rate,
                        &capture_resampler_, audio_frame.get());
  ProcessCaptureFrame(audio_delay_milliseconds, key_pressed,
                      swap_stereo_channels, audio_processing_,
                      audio_frame.get());

  // Typing detection (utilizes the APM/VAD decision). We let the VAD determine
  // if we're using this feature or not.
  // TODO(solenberg): GetConfig() takes a lock. Work around that.
  bool typing_detected = false;
  if (audio_processing_ &&
      audio_processing_->GetConfig().voice_detection.enabled) {
    if (audio_frame->vad_activity_ != AudioFrame::kVadUnknown) {
      bool vad_active = audio_frame->vad_activity_ == AudioFrame::kVadActive;
      typing_detected = typing_detection_.Process(key_pressed, vad_active);
    }
  }

  // Copy frame and push to each sending stream. The copy is required since an
  // encoding task will be posted internally to each stream.
  {
    MutexLock lock(&capture_lock_);
    typing_noise_detected_ = typing_detected;
  }

  RTC_DCHECK_GT(audio_frame->samples_per_channel_, 0);
  if (async_audio_processing_)
    async_audio_processing_->Process(std::move(audio_frame));
  else
    SendProcessedData(std::move(audio_frame));

  return 0;
}

void AudioTransportImpl::SendProcessedData(
    std::unique_ptr<AudioFrame> audio_frame) {
  RTC_DCHECK_GT(audio_frame->samples_per_channel_, 0);
  MutexLock lock(&capture_lock_);
  if (audio_senders_.empty())
    return;

  auto it = audio_senders_.begin();
  while (++it != audio_senders_.end()) {
    auto audio_frame_copy = std::make_unique<AudioFrame>();
    audio_frame_copy->CopyFrom(*audio_frame);
    (*it)->SendAudioData(std::move(audio_frame_copy));
  }
  // Send the original frame to the first stream w/o copying.
  (*audio_senders_.begin())->SendAudioData(std::move(audio_frame));
}

// Mix all received streams, feed the result to the AudioProcessing module, then
// resample the result to the requested output rate.
int32_t AudioTransportImpl::NeedMorePlayData(const size_t nSamples,
                                             const size_t nBytesPerSample,
                                             const size_t nChannels,
                                             const uint32_t samplesPerSec,
                                             void* audioSamples,
                                             size_t& nSamplesOut,
                                             int64_t* elapsed_time_ms,
                                             int64_t* ntp_time_ms) {
  RTC_DCHECK_EQ(sizeof(int16_t) * nChannels, nBytesPerSample);
  RTC_DCHECK_GE(nChannels, 1);
  RTC_DCHECK_LE(nChannels, 2);
  RTC_DCHECK_GE(
      samplesPerSec,
      static_cast<uint32_t>(AudioProcessing::NativeRate::kSampleRate8kHz));

  // 100 = 1 second / data duration (10 ms).
  RTC_DCHECK_EQ(nSamples * 100, samplesPerSec);
  RTC_DCHECK_LE(nBytesPerSample * nSamples * nChannels,
                AudioFrame::kMaxDataSizeBytes);

  mixer_->Mix(nChannels, &mixed_frame_);
  *elapsed_time_ms = mixed_frame_.elapsed_time_ms_;
  *ntp_time_ms = mixed_frame_.ntp_time_ms_;

  if (audio_processing_) {
    const auto error =
        ProcessReverseAudioFrame(audio_processing_, &mixed_frame_);
    RTC_DCHECK_EQ(error, AudioProcessing::kNoError);
  }

  nSamplesOut = Resample(mixed_frame_, samplesPerSec, &render_resampler_,
                         static_cast<int16_t*>(audioSamples));
  RTC_DCHECK_EQ(nSamplesOut, nChannels * nSamples);
  return 0;
}

// Used by Chromium - same as NeedMorePlayData() but because Chrome has its
// own APM instance, does not call audio_processing_->ProcessReverseStream().
void AudioTransportImpl::PullRenderData(int bits_per_sample,
                                        int sample_rate,
                                        size_t number_of_channels,
                                        size_t number_of_frames,
                                        void* audio_data,
                                        int64_t* elapsed_time_ms,
                                        int64_t* ntp_time_ms) {
  RTC_DCHECK_EQ(bits_per_sample, 16);
  RTC_DCHECK_GE(number_of_channels, 1);
  RTC_DCHECK_GE(sample_rate, AudioProcessing::NativeRate::kSampleRate8kHz);

  // 100 = 1 second / data duration (10 ms).
  RTC_DCHECK_EQ(number_of_frames * 100, sample_rate);

  // 8 = bits per byte.
  RTC_DCHECK_LE(bits_per_sample / 8 * number_of_frames * number_of_channels,
                AudioFrame::kMaxDataSizeBytes);
  mixer_->Mix(number_of_channels, &mixed_frame_);
  *elapsed_time_ms = mixed_frame_.elapsed_time_ms_;
  *ntp_time_ms = mixed_frame_.ntp_time_ms_;

  auto output_samples = Resample(mixed_frame_, sample_rate, &render_resampler_,
                                 static_cast<int16_t*>(audio_data));
  RTC_DCHECK_EQ(output_samples, number_of_channels * number_of_frames);
}

void AudioTransportImpl::UpdateAudioSenders(std::vector<AudioSender*> senders,
                                            int send_sample_rate_hz,
                                            size_t send_num_channels) {
  MutexLock lock(&capture_lock_);
  audio_senders_ = std::move(senders);
  send_sample_rate_hz_ = send_sample_rate_hz;
  send_num_channels_ = send_num_channels;
}

void AudioTransportImpl::SetStereoChannelSwapping(bool enable) {
  MutexLock lock(&capture_lock_);
  swap_stereo_channels_ = enable;
}

bool AudioTransportImpl::typing_noise_detected() const {
  MutexLock lock(&capture_lock_);
  return typing_noise_detected_;
}
}  // namespace webrtc

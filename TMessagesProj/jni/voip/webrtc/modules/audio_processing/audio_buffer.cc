/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/audio_buffer.h"

#include <string.h>

#include <cstdint>

#include "common_audio/channel_buffer.h"
#include "common_audio/include/audio_util.h"
#include "common_audio/resampler/push_sinc_resampler.h"
#include "modules/audio_processing/splitting_filter.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

constexpr size_t kSamplesPer32kHzChannel = 320;
constexpr size_t kSamplesPer48kHzChannel = 480;
constexpr size_t kMaxSamplesPerChannel = AudioBuffer::kMaxSampleRate / 100;

size_t NumBandsFromFramesPerChannel(size_t num_frames) {
  if (num_frames == kSamplesPer32kHzChannel) {
    return 2;
  }
  if (num_frames == kSamplesPer48kHzChannel) {
    return 3;
  }
  return 1;
}

}  // namespace

AudioBuffer::AudioBuffer(size_t input_rate,
                         size_t input_num_channels,
                         size_t buffer_rate,
                         size_t buffer_num_channels,
                         size_t output_rate,
                         size_t output_num_channels)
    : input_num_frames_(static_cast<int>(input_rate) / 100),
      input_num_channels_(input_num_channels),
      buffer_num_frames_(static_cast<int>(buffer_rate) / 100),
      buffer_num_channels_(buffer_num_channels),
      output_num_frames_(static_cast<int>(output_rate) / 100),
      output_num_channels_(0),
      num_channels_(buffer_num_channels),
      num_bands_(NumBandsFromFramesPerChannel(buffer_num_frames_)),
      num_split_frames_(rtc::CheckedDivExact(buffer_num_frames_, num_bands_)),
      data_(
          new ChannelBuffer<float>(buffer_num_frames_, buffer_num_channels_)) {
  RTC_DCHECK_GT(input_num_frames_, 0);
  RTC_DCHECK_GT(buffer_num_frames_, 0);
  RTC_DCHECK_GT(output_num_frames_, 0);
  RTC_DCHECK_GT(input_num_channels_, 0);
  RTC_DCHECK_GT(buffer_num_channels_, 0);
  RTC_DCHECK_LE(buffer_num_channels_, input_num_channels_);

  const bool input_resampling_needed = input_num_frames_ != buffer_num_frames_;
  const bool output_resampling_needed =
      output_num_frames_ != buffer_num_frames_;
  if (input_resampling_needed) {
    for (size_t i = 0; i < buffer_num_channels_; ++i) {
      input_resamplers_.push_back(std::unique_ptr<PushSincResampler>(
          new PushSincResampler(input_num_frames_, buffer_num_frames_)));
    }
  }

  if (output_resampling_needed) {
    for (size_t i = 0; i < buffer_num_channels_; ++i) {
      output_resamplers_.push_back(std::unique_ptr<PushSincResampler>(
          new PushSincResampler(buffer_num_frames_, output_num_frames_)));
    }
  }

  if (num_bands_ > 1) {
    split_data_.reset(new ChannelBuffer<float>(
        buffer_num_frames_, buffer_num_channels_, num_bands_));
    splitting_filter_.reset(new SplittingFilter(
        buffer_num_channels_, num_bands_, buffer_num_frames_));
  }
}

AudioBuffer::~AudioBuffer() {}

void AudioBuffer::set_downmixing_to_specific_channel(size_t channel) {
  downmix_by_averaging_ = false;
  RTC_DCHECK_GT(input_num_channels_, channel);
  channel_for_downmixing_ = std::min(channel, input_num_channels_ - 1);
}

void AudioBuffer::set_downmixing_by_averaging() {
  downmix_by_averaging_ = true;
}

void AudioBuffer::CopyFrom(const float* const* stacked_data,
                           const StreamConfig& stream_config) {
  RTC_DCHECK_EQ(stream_config.num_frames(), input_num_frames_);
  RTC_DCHECK_EQ(stream_config.num_channels(), input_num_channels_);
  RestoreNumChannels();
  const bool downmix_needed = input_num_channels_ > 1 && num_channels_ == 1;

  const bool resampling_needed = input_num_frames_ != buffer_num_frames_;

  if (downmix_needed) {
    RTC_DCHECK_GE(kMaxSamplesPerChannel, input_num_frames_);

    std::array<float, kMaxSamplesPerChannel> downmix;
    if (downmix_by_averaging_) {
      const float kOneByNumChannels = 1.f / input_num_channels_;
      for (size_t i = 0; i < input_num_frames_; ++i) {
        float value = stacked_data[0][i];
        for (size_t j = 1; j < input_num_channels_; ++j) {
          value += stacked_data[j][i];
        }
        downmix[i] = value * kOneByNumChannels;
      }
    }
    const float* downmixed_data = downmix_by_averaging_
                                      ? downmix.data()
                                      : stacked_data[channel_for_downmixing_];

    if (resampling_needed) {
      input_resamplers_[0]->Resample(downmixed_data, input_num_frames_,
                                     data_->channels()[0], buffer_num_frames_);
    }
    const float* data_to_convert =
        resampling_needed ? data_->channels()[0] : downmixed_data;
    FloatToFloatS16(data_to_convert, buffer_num_frames_, data_->channels()[0]);
  } else {
    if (resampling_needed) {
      for (size_t i = 0; i < num_channels_; ++i) {
        input_resamplers_[i]->Resample(stacked_data[i], input_num_frames_,
                                       data_->channels()[i],
                                       buffer_num_frames_);
        FloatToFloatS16(data_->channels()[i], buffer_num_frames_,
                        data_->channels()[i]);
      }
    } else {
      for (size_t i = 0; i < num_channels_; ++i) {
        FloatToFloatS16(stacked_data[i], buffer_num_frames_,
                        data_->channels()[i]);
      }
    }
  }
}

void AudioBuffer::CopyTo(const StreamConfig& stream_config,
                         float* const* stacked_data) {
  RTC_DCHECK_EQ(stream_config.num_frames(), output_num_frames_);

  const bool resampling_needed = output_num_frames_ != buffer_num_frames_;
  if (resampling_needed) {
    for (size_t i = 0; i < num_channels_; ++i) {
      FloatS16ToFloat(data_->channels()[i], buffer_num_frames_,
                      data_->channels()[i]);
      output_resamplers_[i]->Resample(data_->channels()[i], buffer_num_frames_,
                                      stacked_data[i], output_num_frames_);
    }
  } else {
    for (size_t i = 0; i < num_channels_; ++i) {
      FloatS16ToFloat(data_->channels()[i], buffer_num_frames_,
                      stacked_data[i]);
    }
  }

  for (size_t i = num_channels_; i < stream_config.num_channels(); ++i) {
    memcpy(stacked_data[i], stacked_data[0],
           output_num_frames_ * sizeof(**stacked_data));
  }
}

void AudioBuffer::CopyTo(AudioBuffer* buffer) const {
  RTC_DCHECK_EQ(buffer->num_frames(), output_num_frames_);

  const bool resampling_needed = output_num_frames_ != buffer_num_frames_;
  if (resampling_needed) {
    for (size_t i = 0; i < num_channels_; ++i) {
      output_resamplers_[i]->Resample(data_->channels()[i], buffer_num_frames_,
                                      buffer->channels()[i],
                                      buffer->num_frames());
    }
  } else {
    for (size_t i = 0; i < num_channels_; ++i) {
      memcpy(buffer->channels()[i], data_->channels()[i],
             buffer_num_frames_ * sizeof(**buffer->channels()));
    }
  }

  for (size_t i = num_channels_; i < buffer->num_channels(); ++i) {
    memcpy(buffer->channels()[i], buffer->channels()[0],
           output_num_frames_ * sizeof(**buffer->channels()));
  }
}

void AudioBuffer::RestoreNumChannels() {
  num_channels_ = buffer_num_channels_;
  data_->set_num_channels(buffer_num_channels_);
  if (split_data_.get()) {
    split_data_->set_num_channels(buffer_num_channels_);
  }
}

void AudioBuffer::set_num_channels(size_t num_channels) {
  RTC_DCHECK_GE(buffer_num_channels_, num_channels);
  num_channels_ = num_channels;
  data_->set_num_channels(num_channels);
  if (split_data_.get()) {
    split_data_->set_num_channels(num_channels);
  }
}

// The resampler is only for supporting 48kHz to 16kHz in the reverse stream.
void AudioBuffer::CopyFrom(const int16_t* const interleaved_data,
                           const StreamConfig& stream_config) {
  RTC_DCHECK_EQ(stream_config.num_channels(), input_num_channels_);
  RTC_DCHECK_EQ(stream_config.num_frames(), input_num_frames_);
  RestoreNumChannels();

  const bool resampling_required = input_num_frames_ != buffer_num_frames_;

  const int16_t* interleaved = interleaved_data;
  if (num_channels_ == 1) {
    if (input_num_channels_ == 1) {
      if (resampling_required) {
        std::array<float, kMaxSamplesPerChannel> float_buffer;
        S16ToFloatS16(interleaved, input_num_frames_, float_buffer.data());
        input_resamplers_[0]->Resample(float_buffer.data(), input_num_frames_,
                                       data_->channels()[0],
                                       buffer_num_frames_);
      } else {
        S16ToFloatS16(interleaved, input_num_frames_, data_->channels()[0]);
      }
    } else {
      std::array<float, kMaxSamplesPerChannel> float_buffer;
      float* downmixed_data =
          resampling_required ? float_buffer.data() : data_->channels()[0];
      if (downmix_by_averaging_) {
        for (size_t j = 0, k = 0; j < input_num_frames_; ++j) {
          int32_t sum = 0;
          for (size_t i = 0; i < input_num_channels_; ++i, ++k) {
            sum += interleaved[k];
          }
          downmixed_data[j] = sum / static_cast<int16_t>(input_num_channels_);
        }
      } else {
        for (size_t j = 0, k = channel_for_downmixing_; j < input_num_frames_;
             ++j, k += input_num_channels_) {
          downmixed_data[j] = interleaved[k];
        }
      }

      if (resampling_required) {
        input_resamplers_[0]->Resample(downmixed_data, input_num_frames_,
                                       data_->channels()[0],
                                       buffer_num_frames_);
      }
    }
  } else {
    auto deinterleave_channel = [](size_t channel, size_t num_channels,
                                   size_t samples_per_channel, const int16_t* x,
                                   float* y) {
      for (size_t j = 0, k = channel; j < samples_per_channel;
           ++j, k += num_channels) {
        y[j] = x[k];
      }
    };

    if (resampling_required) {
      std::array<float, kMaxSamplesPerChannel> float_buffer;
      for (size_t i = 0; i < num_channels_; ++i) {
        deinterleave_channel(i, num_channels_, input_num_frames_, interleaved,
                             float_buffer.data());
        input_resamplers_[i]->Resample(float_buffer.data(), input_num_frames_,
                                       data_->channels()[i],
                                       buffer_num_frames_);
      }
    } else {
      for (size_t i = 0; i < num_channels_; ++i) {
        deinterleave_channel(i, num_channels_, input_num_frames_, interleaved,
                             data_->channels()[i]);
      }
    }
  }
}

void AudioBuffer::CopyTo(const StreamConfig& stream_config,
                         int16_t* const interleaved_data) {
  const size_t config_num_channels = stream_config.num_channels();

  RTC_DCHECK(config_num_channels == num_channels_ || num_channels_ == 1);
  RTC_DCHECK_EQ(stream_config.num_frames(), output_num_frames_);

  const bool resampling_required = buffer_num_frames_ != output_num_frames_;

  int16_t* interleaved = interleaved_data;
  if (num_channels_ == 1) {
    std::array<float, kMaxSamplesPerChannel> float_buffer;

    if (resampling_required) {
      output_resamplers_[0]->Resample(data_->channels()[0], buffer_num_frames_,
                                      float_buffer.data(), output_num_frames_);
    }
    const float* deinterleaved =
        resampling_required ? float_buffer.data() : data_->channels()[0];

    if (config_num_channels == 1) {
      for (size_t j = 0; j < output_num_frames_; ++j) {
        interleaved[j] = FloatS16ToS16(deinterleaved[j]);
      }
    } else {
      for (size_t i = 0, k = 0; i < output_num_frames_; ++i) {
        float tmp = FloatS16ToS16(deinterleaved[i]);
        for (size_t j = 0; j < config_num_channels; ++j, ++k) {
          interleaved[k] = tmp;
        }
      }
    }
  } else {
    auto interleave_channel = [](size_t channel, size_t num_channels,
                                 size_t samples_per_channel, const float* x,
                                 int16_t* y) {
      for (size_t k = 0, j = channel; k < samples_per_channel;
           ++k, j += num_channels) {
        y[j] = FloatS16ToS16(x[k]);
      }
    };

    if (resampling_required) {
      for (size_t i = 0; i < num_channels_; ++i) {
        std::array<float, kMaxSamplesPerChannel> float_buffer;
        output_resamplers_[i]->Resample(data_->channels()[i],
                                        buffer_num_frames_, float_buffer.data(),
                                        output_num_frames_);
        interleave_channel(i, config_num_channels, output_num_frames_,
                           float_buffer.data(), interleaved);
      }
    } else {
      for (size_t i = 0; i < num_channels_; ++i) {
        interleave_channel(i, config_num_channels, output_num_frames_,
                           data_->channels()[i], interleaved);
      }
    }

    for (size_t i = num_channels_; i < config_num_channels; ++i) {
      for (size_t j = 0, k = i, n = num_channels_; j < output_num_frames_;
           ++j, k += config_num_channels, n += config_num_channels) {
        interleaved[k] = interleaved[n];
      }
    }
  }
}

void AudioBuffer::SplitIntoFrequencyBands() {
  splitting_filter_->Analysis(data_.get(), split_data_.get());
}

void AudioBuffer::MergeFrequencyBands() {
  splitting_filter_->Synthesis(split_data_.get(), data_.get());
}

void AudioBuffer::ExportSplitChannelData(
    size_t channel,
    int16_t* const* split_band_data) const {
  for (size_t k = 0; k < num_bands(); ++k) {
    const float* band_data = split_bands_const(channel)[k];

    RTC_DCHECK(split_band_data[k]);
    RTC_DCHECK(band_data);
    for (size_t i = 0; i < num_frames_per_band(); ++i) {
      split_band_data[k][i] = FloatS16ToS16(band_data[i]);
    }
  }
}

void AudioBuffer::ImportSplitChannelData(
    size_t channel,
    const int16_t* const* split_band_data) {
  for (size_t k = 0; k < num_bands(); ++k) {
    float* band_data = split_bands(channel)[k];
    RTC_DCHECK(split_band_data[k]);
    RTC_DCHECK(band_data);
    for (size_t i = 0; i < num_frames_per_band(); ++i) {
      band_data[i] = split_band_data[k][i];
    }
  }
}

}  // namespace webrtc

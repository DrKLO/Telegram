/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/audio_converter.h"

#include <cstring>
#include <memory>
#include <utility>
#include <vector>

#include "common_audio/channel_buffer.h"
#include "common_audio/resampler/push_sinc_resampler.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"

using rtc::checked_cast;

namespace webrtc {

class CopyConverter : public AudioConverter {
 public:
  CopyConverter(size_t src_channels,
                size_t src_frames,
                size_t dst_channels,
                size_t dst_frames)
      : AudioConverter(src_channels, src_frames, dst_channels, dst_frames) {}
  ~CopyConverter() override{};

  void Convert(const float* const* src,
               size_t src_size,
               float* const* dst,
               size_t dst_capacity) override {
    CheckSizes(src_size, dst_capacity);
    if (src != dst) {
      for (size_t i = 0; i < src_channels(); ++i)
        std::memcpy(dst[i], src[i], dst_frames() * sizeof(*dst[i]));
    }
  }
};

class UpmixConverter : public AudioConverter {
 public:
  UpmixConverter(size_t src_channels,
                 size_t src_frames,
                 size_t dst_channels,
                 size_t dst_frames)
      : AudioConverter(src_channels, src_frames, dst_channels, dst_frames) {}
  ~UpmixConverter() override{};

  void Convert(const float* const* src,
               size_t src_size,
               float* const* dst,
               size_t dst_capacity) override {
    CheckSizes(src_size, dst_capacity);
    for (size_t i = 0; i < dst_frames(); ++i) {
      const float value = src[0][i];
      for (size_t j = 0; j < dst_channels(); ++j)
        dst[j][i] = value;
    }
  }
};

class DownmixConverter : public AudioConverter {
 public:
  DownmixConverter(size_t src_channels,
                   size_t src_frames,
                   size_t dst_channels,
                   size_t dst_frames)
      : AudioConverter(src_channels, src_frames, dst_channels, dst_frames) {}
  ~DownmixConverter() override{};

  void Convert(const float* const* src,
               size_t src_size,
               float* const* dst,
               size_t dst_capacity) override {
    CheckSizes(src_size, dst_capacity);
    float* dst_mono = dst[0];
    for (size_t i = 0; i < src_frames(); ++i) {
      float sum = 0;
      for (size_t j = 0; j < src_channels(); ++j)
        sum += src[j][i];
      dst_mono[i] = sum / src_channels();
    }
  }
};

class ResampleConverter : public AudioConverter {
 public:
  ResampleConverter(size_t src_channels,
                    size_t src_frames,
                    size_t dst_channels,
                    size_t dst_frames)
      : AudioConverter(src_channels, src_frames, dst_channels, dst_frames) {
    resamplers_.reserve(src_channels);
    for (size_t i = 0; i < src_channels; ++i)
      resamplers_.push_back(std::unique_ptr<PushSincResampler>(
          new PushSincResampler(src_frames, dst_frames)));
  }
  ~ResampleConverter() override{};

  void Convert(const float* const* src,
               size_t src_size,
               float* const* dst,
               size_t dst_capacity) override {
    CheckSizes(src_size, dst_capacity);
    for (size_t i = 0; i < resamplers_.size(); ++i)
      resamplers_[i]->Resample(src[i], src_frames(), dst[i], dst_frames());
  }

 private:
  std::vector<std::unique_ptr<PushSincResampler>> resamplers_;
};

// Apply a vector of converters in serial, in the order given. At least two
// converters must be provided.
class CompositionConverter : public AudioConverter {
 public:
  explicit CompositionConverter(
      std::vector<std::unique_ptr<AudioConverter>> converters)
      : converters_(std::move(converters)) {
    RTC_CHECK_GE(converters_.size(), 2);
    // We need an intermediate buffer after every converter.
    for (auto it = converters_.begin(); it != converters_.end() - 1; ++it)
      buffers_.push_back(
          std::unique_ptr<ChannelBuffer<float>>(new ChannelBuffer<float>(
              (*it)->dst_frames(), (*it)->dst_channels())));
  }
  ~CompositionConverter() override{};

  void Convert(const float* const* src,
               size_t src_size,
               float* const* dst,
               size_t dst_capacity) override {
    converters_.front()->Convert(src, src_size, buffers_.front()->channels(),
                                 buffers_.front()->size());
    for (size_t i = 2; i < converters_.size(); ++i) {
      auto& src_buffer = buffers_[i - 2];
      auto& dst_buffer = buffers_[i - 1];
      converters_[i]->Convert(src_buffer->channels(), src_buffer->size(),
                              dst_buffer->channels(), dst_buffer->size());
    }
    converters_.back()->Convert(buffers_.back()->channels(),
                                buffers_.back()->size(), dst, dst_capacity);
  }

 private:
  std::vector<std::unique_ptr<AudioConverter>> converters_;
  std::vector<std::unique_ptr<ChannelBuffer<float>>> buffers_;
};

std::unique_ptr<AudioConverter> AudioConverter::Create(size_t src_channels,
                                                       size_t src_frames,
                                                       size_t dst_channels,
                                                       size_t dst_frames) {
  std::unique_ptr<AudioConverter> sp;
  if (src_channels > dst_channels) {
    if (src_frames != dst_frames) {
      std::vector<std::unique_ptr<AudioConverter>> converters;
      converters.push_back(std::unique_ptr<AudioConverter>(new DownmixConverter(
          src_channels, src_frames, dst_channels, src_frames)));
      converters.push_back(
          std::unique_ptr<AudioConverter>(new ResampleConverter(
              dst_channels, src_frames, dst_channels, dst_frames)));
      sp.reset(new CompositionConverter(std::move(converters)));
    } else {
      sp.reset(new DownmixConverter(src_channels, src_frames, dst_channels,
                                    dst_frames));
    }
  } else if (src_channels < dst_channels) {
    if (src_frames != dst_frames) {
      std::vector<std::unique_ptr<AudioConverter>> converters;
      converters.push_back(
          std::unique_ptr<AudioConverter>(new ResampleConverter(
              src_channels, src_frames, src_channels, dst_frames)));
      converters.push_back(std::unique_ptr<AudioConverter>(new UpmixConverter(
          src_channels, dst_frames, dst_channels, dst_frames)));
      sp.reset(new CompositionConverter(std::move(converters)));
    } else {
      sp.reset(new UpmixConverter(src_channels, src_frames, dst_channels,
                                  dst_frames));
    }
  } else if (src_frames != dst_frames) {
    sp.reset(new ResampleConverter(src_channels, src_frames, dst_channels,
                                   dst_frames));
  } else {
    sp.reset(
        new CopyConverter(src_channels, src_frames, dst_channels, dst_frames));
  }

  return sp;
}

// For CompositionConverter.
AudioConverter::AudioConverter()
    : src_channels_(0), src_frames_(0), dst_channels_(0), dst_frames_(0) {}

AudioConverter::AudioConverter(size_t src_channels,
                               size_t src_frames,
                               size_t dst_channels,
                               size_t dst_frames)
    : src_channels_(src_channels),
      src_frames_(src_frames),
      dst_channels_(dst_channels),
      dst_frames_(dst_frames) {
  RTC_CHECK(dst_channels == src_channels || dst_channels == 1 ||
            src_channels == 1);
}

void AudioConverter::CheckSizes(size_t src_size, size_t dst_capacity) const {
  RTC_CHECK_EQ(src_size, src_channels() * src_frames());
  RTC_CHECK_GE(dst_capacity, dst_channels() * dst_frames());
}

}  // namespace webrtc

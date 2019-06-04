/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AUDIO_BUFFER_H_
#define MODULES_AUDIO_PROCESSING_AUDIO_BUFFER_H_

#include <stddef.h>
#include <stdint.h>
#include <memory>
#include <vector>

#include "api/audio/audio_frame.h"
#include "common_audio/channel_buffer.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "rtc_base/gtest_prod_util.h"

namespace webrtc {

class IFChannelBuffer;
class PushSincResampler;
class SplittingFilter;

enum Band { kBand0To8kHz = 0, kBand8To16kHz = 1, kBand16To24kHz = 2 };

class AudioBuffer {
 public:
  // TODO(ajm): Switch to take ChannelLayouts.
  AudioBuffer(size_t input_num_frames,
              size_t num_input_channels,
              size_t process_num_frames,
              size_t num_process_channels,
              size_t output_num_frames);
  virtual ~AudioBuffer();

  size_t num_channels() const;
  void set_num_channels(size_t num_channels);
  size_t num_frames() const;
  size_t num_frames_per_band() const;
  size_t num_keyboard_frames() const;
  size_t num_bands() const;

  // Returns a pointer array to the full-band channels.
  // Usage:
  // channels()[channel][sample].
  // Where:
  // 0 <= channel < |num_proc_channels_|
  // 0 <= sample < |proc_num_frames_|
  int16_t* const* channels();
  const int16_t* const* channels_const() const;
  float* const* channels_f();
  const float* const* channels_const_f() const;

  // Returns a pointer array to the bands for a specific channel.
  // Usage:
  // split_bands(channel)[band][sample].
  // Where:
  // 0 <= channel < |num_proc_channels_|
  // 0 <= band < |num_bands_|
  // 0 <= sample < |num_split_frames_|
  int16_t* const* split_bands(size_t channel);
  const int16_t* const* split_bands_const(size_t channel) const;
  float* const* split_bands_f(size_t channel);
  const float* const* split_bands_const_f(size_t channel) const;

  // Returns a pointer array to the channels for a specific band.
  // Usage:
  // split_channels(band)[channel][sample].
  // Where:
  // 0 <= band < |num_bands_|
  // 0 <= channel < |num_proc_channels_|
  // 0 <= sample < |num_split_frames_|
  int16_t* const* split_channels(Band band);
  const int16_t* const* split_channels_const(Band band) const;
  float* const* split_channels_f(Band band);
  const float* const* split_channels_const_f(Band band) const;

  // Returns a pointer to the ChannelBuffer that encapsulates the full-band
  // data.
  ChannelBuffer<int16_t>* data();
  const ChannelBuffer<int16_t>* data() const;
  ChannelBuffer<float>* data_f();
  const ChannelBuffer<float>* data_f() const;

  // Returns a pointer to the ChannelBuffer that encapsulates the split data.
  ChannelBuffer<int16_t>* split_data();
  const ChannelBuffer<int16_t>* split_data() const;
  ChannelBuffer<float>* split_data_f();
  const ChannelBuffer<float>* split_data_f() const;

  // Returns a pointer to the low-pass data downmixed to mono. If this data
  // isn't already available it re-calculates it.
  const int16_t* mixed_low_pass_data();
  const int16_t* low_pass_reference(int channel) const;

  const float* keyboard_data() const;

  void set_activity(AudioFrame::VADActivity activity);
  AudioFrame::VADActivity activity() const;

  // Use for int16 interleaved data.
  void DeinterleaveFrom(AudioFrame* audioFrame);
  // If |data_changed| is false, only the non-audio data members will be copied
  // to |frame|.
  void InterleaveTo(AudioFrame* frame, bool data_changed) const;

  // Use for float deinterleaved data.
  void CopyFrom(const float* const* data, const StreamConfig& stream_config);
  void CopyTo(const StreamConfig& stream_config, float* const* data);
  void CopyLowPassToReference();

  // Splits the signal into different bands.
  void SplitIntoFrequencyBands();
  // Recombine the different bands into one signal.
  void MergeFrequencyBands();

 private:
  FRIEND_TEST_ALL_PREFIXES(AudioBufferTest,
                           SetNumChannelsSetsChannelBuffersNumChannels);
  // Called from DeinterleaveFrom() and CopyFrom().
  void InitForNewData();

  // The audio is passed into DeinterleaveFrom() or CopyFrom() with input
  // format (samples per channel and number of channels).
  const size_t input_num_frames_;
  const size_t num_input_channels_;
  // The audio is stored by DeinterleaveFrom() or CopyFrom() with processing
  // format.
  const size_t proc_num_frames_;
  const size_t num_proc_channels_;
  // The audio is returned by InterleaveTo() and CopyTo() with output samples
  // per channels and the current number of channels. This last one can be
  // changed at any time using set_num_channels().
  const size_t output_num_frames_;
  size_t num_channels_;

  size_t num_bands_;
  size_t num_split_frames_;
  bool mixed_low_pass_valid_;
  bool reference_copied_;
  AudioFrame::VADActivity activity_;

  const float* keyboard_data_;
  std::unique_ptr<IFChannelBuffer> data_;
  std::unique_ptr<IFChannelBuffer> split_data_;
  std::unique_ptr<SplittingFilter> splitting_filter_;
  std::unique_ptr<ChannelBuffer<int16_t>> mixed_low_pass_channels_;
  std::unique_ptr<ChannelBuffer<int16_t>> low_pass_reference_channels_;
  std::unique_ptr<IFChannelBuffer> input_buffer_;
  std::unique_ptr<IFChannelBuffer> output_buffer_;
  std::unique_ptr<ChannelBuffer<float>> process_buffer_;
  std::vector<std::unique_ptr<PushSincResampler>> input_resamplers_;
  std::vector<std::unique_ptr<PushSincResampler>> output_resamplers_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AUDIO_BUFFER_H_

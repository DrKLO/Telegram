/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/merge.h"

#include <string.h>  // memmove, memcpy, memset, size_t

#include <algorithm>  // min, max
#include <memory>

#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "modules/audio_coding/neteq/audio_multi_vector.h"
#include "modules/audio_coding/neteq/cross_correlation.h"
#include "modules/audio_coding/neteq/dsp_helper.h"
#include "modules/audio_coding/neteq/expand.h"
#include "modules/audio_coding/neteq/sync_buffer.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {

Merge::Merge(int fs_hz,
             size_t num_channels,
             Expand* expand,
             SyncBuffer* sync_buffer)
    : fs_hz_(fs_hz),
      num_channels_(num_channels),
      fs_mult_(fs_hz_ / 8000),
      timestamps_per_call_(static_cast<size_t>(fs_hz_ / 100)),
      expand_(expand),
      sync_buffer_(sync_buffer),
      expanded_(num_channels_) {
  RTC_DCHECK_GT(num_channels_, 0);
}

Merge::~Merge() = default;

size_t Merge::Process(int16_t* input,
                      size_t input_length,
                      AudioMultiVector* output) {
  // TODO(hlundin): Change to an enumerator and skip assert.
  RTC_DCHECK(fs_hz_ == 8000 || fs_hz_ == 16000 || fs_hz_ == 32000 ||
             fs_hz_ == 48000);
  RTC_DCHECK_LE(fs_hz_, kMaxSampleRate);  // Should not be possible.
  if (input_length == 0) {
    return 0;
  }

  size_t old_length;
  size_t expand_period;
  // Get expansion data to overlap and mix with.
  size_t expanded_length = GetExpandedSignal(&old_length, &expand_period);

  // Transfer input signal to an AudioMultiVector.
  AudioMultiVector input_vector(num_channels_);
  input_vector.PushBackInterleaved(
      rtc::ArrayView<const int16_t>(input, input_length));
  size_t input_length_per_channel = input_vector.Size();
  RTC_DCHECK_EQ(input_length_per_channel, input_length / num_channels_);

  size_t best_correlation_index = 0;
  size_t output_length = 0;

  std::unique_ptr<int16_t[]> input_channel(
      new int16_t[input_length_per_channel]);
  std::unique_ptr<int16_t[]> expanded_channel(new int16_t[expanded_length]);
  for (size_t channel = 0; channel < num_channels_; ++channel) {
    input_vector[channel].CopyTo(input_length_per_channel, 0,
                                 input_channel.get());
    expanded_[channel].CopyTo(expanded_length, 0, expanded_channel.get());

    const int16_t new_mute_factor = std::min<int16_t>(
        16384, SignalScaling(input_channel.get(), input_length_per_channel,
                             expanded_channel.get()));

    if (channel == 0) {
      // Downsample, correlate, and find strongest correlation period for the
      // reference (i.e., first) channel only.
      // Downsample to 4kHz sample rate.
      Downsample(input_channel.get(), input_length_per_channel,
                 expanded_channel.get(), expanded_length);

      // Calculate the lag of the strongest correlation period.
      best_correlation_index = CorrelateAndPeakSearch(
          old_length, input_length_per_channel, expand_period);
    }

    temp_data_.resize(input_length_per_channel + best_correlation_index);
    int16_t* decoded_output = temp_data_.data() + best_correlation_index;

    // Mute the new decoded data if needed (and unmute it linearly).
    // This is the overlapping part of expanded_signal.
    size_t interpolation_length =
        std::min(kMaxCorrelationLength * fs_mult_,
                 expanded_length - best_correlation_index);
    interpolation_length =
        std::min(interpolation_length, input_length_per_channel);

    RTC_DCHECK_LE(new_mute_factor, 16384);
    int16_t mute_factor =
        std::max(expand_->MuteFactor(channel), new_mute_factor);
    RTC_DCHECK_GE(mute_factor, 0);

    if (mute_factor < 16384) {
      // Set a suitable muting slope (Q20). 0.004 for NB, 0.002 for WB,
      // and so on, or as fast as it takes to come back to full gain within the
      // frame length.
      const int back_to_fullscale_inc = static_cast<int>(
          ((16384 - mute_factor) << 6) / input_length_per_channel);
      const int increment = std::max(4194 / fs_mult_, back_to_fullscale_inc);
      mute_factor = static_cast<int16_t>(DspHelper::RampSignal(
          input_channel.get(), interpolation_length, mute_factor, increment));
      DspHelper::UnmuteSignal(&input_channel[interpolation_length],
                              input_length_per_channel - interpolation_length,
                              &mute_factor, increment,
                              &decoded_output[interpolation_length]);
    } else {
      // No muting needed.
      memmove(
          &decoded_output[interpolation_length],
          &input_channel[interpolation_length],
          sizeof(int16_t) * (input_length_per_channel - interpolation_length));
    }

    // Do overlap and mix linearly.
    int16_t increment =
        static_cast<int16_t>(16384 / (interpolation_length + 1));  // In Q14.
    int16_t local_mute_factor = 16384 - increment;
    memmove(temp_data_.data(), expanded_channel.get(),
            sizeof(int16_t) * best_correlation_index);
    DspHelper::CrossFade(&expanded_channel[best_correlation_index],
                         input_channel.get(), interpolation_length,
                         &local_mute_factor, increment, decoded_output);

    output_length = best_correlation_index + input_length_per_channel;
    if (channel == 0) {
      RTC_DCHECK(output->Empty());  // Output should be empty at this point.
      output->AssertSize(output_length);
    } else {
      RTC_DCHECK_EQ(output->Size(), output_length);
    }
    (*output)[channel].OverwriteAt(temp_data_.data(), output_length, 0);
  }

  // Copy back the first part of the data to `sync_buffer_` and remove it from
  // `output`.
  sync_buffer_->ReplaceAtIndex(*output, old_length, sync_buffer_->next_index());
  output->PopFront(old_length);

  // Return new added length. `old_length` samples were borrowed from
  // `sync_buffer_`.
  RTC_DCHECK_GE(output_length, old_length);
  return output_length - old_length;
}

size_t Merge::GetExpandedSignal(size_t* old_length, size_t* expand_period) {
  // Check how much data that is left since earlier.
  *old_length = sync_buffer_->FutureLength();
  // Should never be less than overlap_length.
  RTC_DCHECK_GE(*old_length, expand_->overlap_length());
  // Generate data to merge the overlap with using expand.
  expand_->SetParametersForMergeAfterExpand();

  if (*old_length >= 210 * kMaxSampleRate / 8000) {
    // TODO(hlundin): Write test case for this.
    // The number of samples available in the sync buffer is more than what fits
    // in expanded_signal. Keep the first 210 * kMaxSampleRate / 8000 samples,
    // but shift them towards the end of the buffer. This is ok, since all of
    // the buffer will be expand data anyway, so as long as the beginning is
    // left untouched, we're fine.
    size_t length_diff = *old_length - 210 * kMaxSampleRate / 8000;
    sync_buffer_->InsertZerosAtIndex(length_diff, sync_buffer_->next_index());
    *old_length = 210 * kMaxSampleRate / 8000;
    // This is the truncated length.
  }
  // This assert should always be true thanks to the if statement above.
  RTC_DCHECK_GE(210 * kMaxSampleRate / 8000, *old_length);

  AudioMultiVector expanded_temp(num_channels_);
  expand_->Process(&expanded_temp);
  *expand_period = expanded_temp.Size();  // Samples per channel.

  expanded_.Clear();
  // Copy what is left since earlier into the expanded vector.
  expanded_.PushBackFromIndex(*sync_buffer_, sync_buffer_->next_index());
  RTC_DCHECK_EQ(expanded_.Size(), *old_length);
  RTC_DCHECK_GT(expanded_temp.Size(), 0);
  // Do "ugly" copy and paste from the expanded in order to generate more data
  // to correlate (but not interpolate) with.
  const size_t required_length = static_cast<size_t>((120 + 80 + 2) * fs_mult_);
  if (expanded_.Size() < required_length) {
    while (expanded_.Size() < required_length) {
      // Append one more pitch period each time.
      expanded_.PushBack(expanded_temp);
    }
    // Trim the length to exactly `required_length`.
    expanded_.PopBack(expanded_.Size() - required_length);
  }
  RTC_DCHECK_GE(expanded_.Size(), required_length);
  return required_length;
}

int16_t Merge::SignalScaling(const int16_t* input,
                             size_t input_length,
                             const int16_t* expanded_signal) const {
  // Adjust muting factor if new vector is more or less of the BGN energy.
  const auto mod_input_length = rtc::SafeMin<size_t>(
      64 * rtc::dchecked_cast<size_t>(fs_mult_), input_length);
  const int16_t expanded_max =
      WebRtcSpl_MaxAbsValueW16(expanded_signal, mod_input_length);
  int32_t factor =
      (expanded_max * expanded_max) / (std::numeric_limits<int32_t>::max() /
                                       static_cast<int32_t>(mod_input_length));
  const int expanded_shift = factor == 0 ? 0 : 31 - WebRtcSpl_NormW32(factor);
  int32_t energy_expanded = WebRtcSpl_DotProductWithScale(
      expanded_signal, expanded_signal, mod_input_length, expanded_shift);

  // Calculate energy of input signal.
  const int16_t input_max = WebRtcSpl_MaxAbsValueW16(input, mod_input_length);
  factor = (input_max * input_max) / (std::numeric_limits<int32_t>::max() /
                                      static_cast<int32_t>(mod_input_length));
  const int input_shift = factor == 0 ? 0 : 31 - WebRtcSpl_NormW32(factor);
  int32_t energy_input = WebRtcSpl_DotProductWithScale(
      input, input, mod_input_length, input_shift);

  // Align to the same Q-domain.
  if (input_shift > expanded_shift) {
    energy_expanded = energy_expanded >> (input_shift - expanded_shift);
  } else {
    energy_input = energy_input >> (expanded_shift - input_shift);
  }

  // Calculate muting factor to use for new frame.
  int16_t mute_factor;
  if (energy_input > energy_expanded) {
    // Normalize `energy_input` to 14 bits.
    int16_t temp_shift = WebRtcSpl_NormW32(energy_input) - 17;
    energy_input = WEBRTC_SPL_SHIFT_W32(energy_input, temp_shift);
    // Put `energy_expanded` in a domain 14 higher, so that
    // energy_expanded / energy_input is in Q14.
    energy_expanded = WEBRTC_SPL_SHIFT_W32(energy_expanded, temp_shift + 14);
    // Calculate sqrt(energy_expanded / energy_input) in Q14.
    mute_factor = static_cast<int16_t>(
        WebRtcSpl_SqrtFloor((energy_expanded / energy_input) << 14));
  } else {
    // Set to 1 (in Q14) when `expanded` has higher energy than `input`.
    mute_factor = 16384;
  }

  return mute_factor;
}

// TODO(hlundin): There are some parameter values in this method that seem
// strange. Compare with Expand::Correlation.
void Merge::Downsample(const int16_t* input,
                       size_t input_length,
                       const int16_t* expanded_signal,
                       size_t expanded_length) {
  const int16_t* filter_coefficients;
  size_t num_coefficients;
  int decimation_factor = fs_hz_ / 4000;
  static const size_t kCompensateDelay = 0;
  size_t length_limit = static_cast<size_t>(fs_hz_ / 100);  // 10 ms in samples.
  if (fs_hz_ == 8000) {
    filter_coefficients = DspHelper::kDownsample8kHzTbl;
    num_coefficients = 3;
  } else if (fs_hz_ == 16000) {
    filter_coefficients = DspHelper::kDownsample16kHzTbl;
    num_coefficients = 5;
  } else if (fs_hz_ == 32000) {
    filter_coefficients = DspHelper::kDownsample32kHzTbl;
    num_coefficients = 7;
  } else {  // fs_hz_ == 48000
    filter_coefficients = DspHelper::kDownsample48kHzTbl;
    num_coefficients = 7;
  }
  size_t signal_offset = num_coefficients - 1;
  WebRtcSpl_DownsampleFast(
      &expanded_signal[signal_offset], expanded_length - signal_offset,
      expanded_downsampled_, kExpandDownsampLength, filter_coefficients,
      num_coefficients, decimation_factor, kCompensateDelay);
  if (input_length <= length_limit) {
    // Not quite long enough, so we have to cheat a bit.
    // If the input is shorter than the offset, we consider the input to be 0
    // length. This will cause us to skip the downsampling since it makes no
    // sense anyway, and input_downsampled_ will be filled with zeros. This is
    // clearly a pathological case, and the signal quality will suffer, but
    // there is not much we can do.
    const size_t temp_len =
        input_length > signal_offset ? input_length - signal_offset : 0;
    // TODO(hlundin): Should `downsamp_temp_len` be corrected for round-off
    // errors? I.e., (temp_len + decimation_factor - 1) / decimation_factor?
    size_t downsamp_temp_len = temp_len / decimation_factor;
    if (downsamp_temp_len > 0) {
      WebRtcSpl_DownsampleFast(&input[signal_offset], temp_len,
                               input_downsampled_, downsamp_temp_len,
                               filter_coefficients, num_coefficients,
                               decimation_factor, kCompensateDelay);
    }
    memset(&input_downsampled_[downsamp_temp_len], 0,
           sizeof(int16_t) * (kInputDownsampLength - downsamp_temp_len));
  } else {
    WebRtcSpl_DownsampleFast(
        &input[signal_offset], input_length - signal_offset, input_downsampled_,
        kInputDownsampLength, filter_coefficients, num_coefficients,
        decimation_factor, kCompensateDelay);
  }
}

size_t Merge::CorrelateAndPeakSearch(size_t start_position,
                                     size_t input_length,
                                     size_t expand_period) const {
  // Calculate correlation without any normalization.
  const size_t max_corr_length = kMaxCorrelationLength;
  size_t stop_position_downsamp =
      std::min(max_corr_length, expand_->max_lag() / (fs_mult_ * 2) + 1);

  int32_t correlation[kMaxCorrelationLength];
  CrossCorrelationWithAutoShift(input_downsampled_, expanded_downsampled_,
                                kInputDownsampLength, stop_position_downsamp, 1,
                                correlation);

  // Normalize correlation to 14 bits and copy to a 16-bit array.
  const size_t pad_length = expand_->overlap_length() - 1;
  const size_t correlation_buffer_size = 2 * pad_length + kMaxCorrelationLength;
  std::unique_ptr<int16_t[]> correlation16(
      new int16_t[correlation_buffer_size]);
  memset(correlation16.get(), 0, correlation_buffer_size * sizeof(int16_t));
  int16_t* correlation_ptr = &correlation16[pad_length];
  int32_t max_correlation =
      WebRtcSpl_MaxAbsValueW32(correlation, stop_position_downsamp);
  int norm_shift = std::max(0, 17 - WebRtcSpl_NormW32(max_correlation));
  WebRtcSpl_VectorBitShiftW32ToW16(correlation_ptr, stop_position_downsamp,
                                   correlation, norm_shift);

  // Calculate allowed starting point for peak finding.
  // The peak location bestIndex must fulfill two criteria:
  // (1) w16_bestIndex + input_length <
  //     timestamps_per_call_ + expand_->overlap_length();
  // (2) w16_bestIndex + input_length < start_position.
  size_t start_index = timestamps_per_call_ + expand_->overlap_length();
  start_index = std::max(start_position, start_index);
  start_index = (input_length > start_index) ? 0 : (start_index - input_length);
  // Downscale starting index to 4kHz domain. (fs_mult_ * 2 = fs_hz_ / 4000.)
  size_t start_index_downsamp = start_index / (fs_mult_ * 2);

  // Calculate a modified `stop_position_downsamp` to account for the increased
  // start index `start_index_downsamp` and the effective array length.
  size_t modified_stop_pos =
      std::min(stop_position_downsamp,
               kMaxCorrelationLength + pad_length - start_index_downsamp);
  size_t best_correlation_index;
  int16_t best_correlation;
  static const size_t kNumCorrelationCandidates = 1;
  DspHelper::PeakDetection(&correlation_ptr[start_index_downsamp],
                           modified_stop_pos, kNumCorrelationCandidates,
                           fs_mult_, &best_correlation_index,
                           &best_correlation);
  // Compensate for modified start index.
  best_correlation_index += start_index;

  // Ensure that underrun does not occur for 10ms case => we have to get at
  // least 10ms + overlap . (This should never happen thanks to the above
  // modification of peak-finding starting point.)
  while (((best_correlation_index + input_length) <
          (timestamps_per_call_ + expand_->overlap_length())) ||
         ((best_correlation_index + input_length) < start_position)) {
    RTC_DCHECK_NOTREACHED();                  // Should never happen.
    best_correlation_index += expand_period;  // Jump one lag ahead.
  }
  return best_correlation_index;
}

size_t Merge::RequiredFutureSamples() {
  return fs_hz_ / 100 * num_channels_;  // 10 ms.
}

}  // namespace webrtc

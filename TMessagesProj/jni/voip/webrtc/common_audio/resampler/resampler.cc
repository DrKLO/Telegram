/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * A wrapper for resampling a numerous amount of sampling combinations.
 */

#include "common_audio/resampler/include/resampler.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "rtc_base/logging.h"

namespace webrtc {

Resampler::Resampler()
    : state1_(nullptr),
      state2_(nullptr),
      state3_(nullptr),
      in_buffer_(nullptr),
      out_buffer_(nullptr),
      in_buffer_size_(0),
      out_buffer_size_(0),
      in_buffer_size_max_(0),
      out_buffer_size_max_(0),
      my_in_frequency_khz_(0),
      my_out_frequency_khz_(0),
      my_mode_(kResamplerMode1To1),
      num_channels_(0),
      helper_left_(nullptr),
      helper_right_(nullptr) {}

Resampler::Resampler(int inFreq, int outFreq, size_t num_channels)
    : Resampler() {
  Reset(inFreq, outFreq, num_channels);
}

Resampler::~Resampler() {
  if (state1_) {
    free(state1_);
  }
  if (state2_) {
    free(state2_);
  }
  if (state3_) {
    free(state3_);
  }
  if (in_buffer_) {
    free(in_buffer_);
  }
  if (out_buffer_) {
    free(out_buffer_);
  }
  if (helper_left_) {
    delete helper_left_;
  }
  if (helper_right_) {
    delete helper_right_;
  }
}

int Resampler::ResetIfNeeded(int inFreq, int outFreq, size_t num_channels) {
  int tmpInFreq_kHz = inFreq / 1000;
  int tmpOutFreq_kHz = outFreq / 1000;

  if ((tmpInFreq_kHz != my_in_frequency_khz_) ||
      (tmpOutFreq_kHz != my_out_frequency_khz_) ||
      (num_channels != num_channels_)) {
    return Reset(inFreq, outFreq, num_channels);
  } else {
    return 0;
  }
}

int Resampler::Reset(int inFreq, int outFreq, size_t num_channels) {
  if (num_channels != 1 && num_channels != 2) {
    RTC_LOG(LS_WARNING)
        << "Reset() called with unsupported channel count, num_channels = "
        << num_channels;
    return -1;
  }
  ResamplerMode mode;
  if (ComputeResamplerMode(inFreq, outFreq, &mode) != 0) {
    RTC_LOG(LS_WARNING)
        << "Reset() called with unsupported sample rates, inFreq = " << inFreq
        << ", outFreq = " << outFreq;
    return -1;
  }
  // Reinitialize internal state for the frequencies and sample rates.
  num_channels_ = num_channels;
  my_mode_ = mode;

  if (state1_) {
    free(state1_);
    state1_ = nullptr;
  }
  if (state2_) {
    free(state2_);
    state2_ = nullptr;
  }
  if (state3_) {
    free(state3_);
    state3_ = nullptr;
  }
  if (in_buffer_) {
    free(in_buffer_);
    in_buffer_ = nullptr;
  }
  if (out_buffer_) {
    free(out_buffer_);
    out_buffer_ = nullptr;
  }
  if (helper_left_) {
    delete helper_left_;
    helper_left_ = nullptr;
  }
  if (helper_right_) {
    delete helper_right_;
    helper_right_ = nullptr;
  }

  in_buffer_size_ = 0;
  out_buffer_size_ = 0;
  in_buffer_size_max_ = 0;
  out_buffer_size_max_ = 0;

  // We need to track what domain we're in.
  my_in_frequency_khz_ = inFreq / 1000;
  my_out_frequency_khz_ = outFreq / 1000;

  if (num_channels_ == 2) {
    // Create two mono resamplers.
    helper_left_ = new Resampler(inFreq, outFreq, 1);
    helper_right_ = new Resampler(inFreq, outFreq, 1);
  }

  // Now create the states we need.
  switch (my_mode_) {
    case kResamplerMode1To1:
      // No state needed;
      break;
    case kResamplerMode1To2:
      state1_ = malloc(8 * sizeof(int32_t));
      memset(state1_, 0, 8 * sizeof(int32_t));
      break;
    case kResamplerMode1To3:
      state1_ = malloc(sizeof(WebRtcSpl_State16khzTo48khz));
      WebRtcSpl_ResetResample16khzTo48khz(
          static_cast<WebRtcSpl_State16khzTo48khz*>(state1_));
      break;
    case kResamplerMode1To4:
      // 1:2
      state1_ = malloc(8 * sizeof(int32_t));
      memset(state1_, 0, 8 * sizeof(int32_t));
      // 2:4
      state2_ = malloc(8 * sizeof(int32_t));
      memset(state2_, 0, 8 * sizeof(int32_t));
      break;
    case kResamplerMode1To6:
      // 1:2
      state1_ = malloc(8 * sizeof(int32_t));
      memset(state1_, 0, 8 * sizeof(int32_t));
      // 2:6
      state2_ = malloc(sizeof(WebRtcSpl_State16khzTo48khz));
      WebRtcSpl_ResetResample16khzTo48khz(
          static_cast<WebRtcSpl_State16khzTo48khz*>(state2_));
      break;
    case kResamplerMode1To12:
      // 1:2
      state1_ = malloc(8 * sizeof(int32_t));
      memset(state1_, 0, 8 * sizeof(int32_t));
      // 2:4
      state2_ = malloc(8 * sizeof(int32_t));
      memset(state2_, 0, 8 * sizeof(int32_t));
      // 4:12
      state3_ = malloc(sizeof(WebRtcSpl_State16khzTo48khz));
      WebRtcSpl_ResetResample16khzTo48khz(
          static_cast<WebRtcSpl_State16khzTo48khz*>(state3_));
      break;
    case kResamplerMode2To3:
      // 2:6
      state1_ = malloc(sizeof(WebRtcSpl_State16khzTo48khz));
      WebRtcSpl_ResetResample16khzTo48khz(
          static_cast<WebRtcSpl_State16khzTo48khz*>(state1_));
      // 6:3
      state2_ = malloc(8 * sizeof(int32_t));
      memset(state2_, 0, 8 * sizeof(int32_t));
      break;
    case kResamplerMode2To11:
      state1_ = malloc(8 * sizeof(int32_t));
      memset(state1_, 0, 8 * sizeof(int32_t));

      state2_ = malloc(sizeof(WebRtcSpl_State8khzTo22khz));
      WebRtcSpl_ResetResample8khzTo22khz(
          static_cast<WebRtcSpl_State8khzTo22khz*>(state2_));
      break;
    case kResamplerMode4To11:
      state1_ = malloc(sizeof(WebRtcSpl_State8khzTo22khz));
      WebRtcSpl_ResetResample8khzTo22khz(
          static_cast<WebRtcSpl_State8khzTo22khz*>(state1_));
      break;
    case kResamplerMode8To11:
      state1_ = malloc(sizeof(WebRtcSpl_State16khzTo22khz));
      WebRtcSpl_ResetResample16khzTo22khz(
          static_cast<WebRtcSpl_State16khzTo22khz*>(state1_));
      break;
    case kResamplerMode11To16:
      state1_ = malloc(8 * sizeof(int32_t));
      memset(state1_, 0, 8 * sizeof(int32_t));

      state2_ = malloc(sizeof(WebRtcSpl_State22khzTo16khz));
      WebRtcSpl_ResetResample22khzTo16khz(
          static_cast<WebRtcSpl_State22khzTo16khz*>(state2_));
      break;
    case kResamplerMode11To32:
      // 11 -> 22
      state1_ = malloc(8 * sizeof(int32_t));
      memset(state1_, 0, 8 * sizeof(int32_t));

      // 22 -> 16
      state2_ = malloc(sizeof(WebRtcSpl_State22khzTo16khz));
      WebRtcSpl_ResetResample22khzTo16khz(
          static_cast<WebRtcSpl_State22khzTo16khz*>(state2_));

      // 16 -> 32
      state3_ = malloc(8 * sizeof(int32_t));
      memset(state3_, 0, 8 * sizeof(int32_t));

      break;
    case kResamplerMode2To1:
      state1_ = malloc(8 * sizeof(int32_t));
      memset(state1_, 0, 8 * sizeof(int32_t));
      break;
    case kResamplerMode3To1:
      state1_ = malloc(sizeof(WebRtcSpl_State48khzTo16khz));
      WebRtcSpl_ResetResample48khzTo16khz(
          static_cast<WebRtcSpl_State48khzTo16khz*>(state1_));
      break;
    case kResamplerMode4To1:
      // 4:2
      state1_ = malloc(8 * sizeof(int32_t));
      memset(state1_, 0, 8 * sizeof(int32_t));
      // 2:1
      state2_ = malloc(8 * sizeof(int32_t));
      memset(state2_, 0, 8 * sizeof(int32_t));
      break;
    case kResamplerMode6To1:
      // 6:2
      state1_ = malloc(sizeof(WebRtcSpl_State48khzTo16khz));
      WebRtcSpl_ResetResample48khzTo16khz(
          static_cast<WebRtcSpl_State48khzTo16khz*>(state1_));
      // 2:1
      state2_ = malloc(8 * sizeof(int32_t));
      memset(state2_, 0, 8 * sizeof(int32_t));
      break;
    case kResamplerMode12To1:
      // 12:4
      state1_ = malloc(sizeof(WebRtcSpl_State48khzTo16khz));
      WebRtcSpl_ResetResample48khzTo16khz(
          static_cast<WebRtcSpl_State48khzTo16khz*>(state1_));
      // 4:2
      state2_ = malloc(8 * sizeof(int32_t));
      memset(state2_, 0, 8 * sizeof(int32_t));
      // 2:1
      state3_ = malloc(8 * sizeof(int32_t));
      memset(state3_, 0, 8 * sizeof(int32_t));
      break;
    case kResamplerMode3To2:
      // 3:6
      state1_ = malloc(8 * sizeof(int32_t));
      memset(state1_, 0, 8 * sizeof(int32_t));
      // 6:2
      state2_ = malloc(sizeof(WebRtcSpl_State48khzTo16khz));
      WebRtcSpl_ResetResample48khzTo16khz(
          static_cast<WebRtcSpl_State48khzTo16khz*>(state2_));
      break;
    case kResamplerMode11To2:
      state1_ = malloc(sizeof(WebRtcSpl_State22khzTo8khz));
      WebRtcSpl_ResetResample22khzTo8khz(
          static_cast<WebRtcSpl_State22khzTo8khz*>(state1_));

      state2_ = malloc(8 * sizeof(int32_t));
      memset(state2_, 0, 8 * sizeof(int32_t));

      break;
    case kResamplerMode11To4:
      state1_ = malloc(sizeof(WebRtcSpl_State22khzTo8khz));
      WebRtcSpl_ResetResample22khzTo8khz(
          static_cast<WebRtcSpl_State22khzTo8khz*>(state1_));
      break;
    case kResamplerMode11To8:
      state1_ = malloc(sizeof(WebRtcSpl_State22khzTo16khz));
      WebRtcSpl_ResetResample22khzTo16khz(
          static_cast<WebRtcSpl_State22khzTo16khz*>(state1_));
      break;
  }

  return 0;
}

int Resampler::ComputeResamplerMode(int in_freq_hz,
                                    int out_freq_hz,
                                    ResamplerMode* mode) {
  // Start with a math exercise, Euclid's algorithm to find the gcd:
  int a = in_freq_hz;
  int b = out_freq_hz;
  int c = a % b;
  while (c != 0) {
    a = b;
    b = c;
    c = a % b;
  }
  // b is now the gcd;

  // Scale with GCD
  const int reduced_in_freq = in_freq_hz / b;
  const int reduced_out_freq = out_freq_hz / b;

  if (reduced_in_freq == reduced_out_freq) {
    *mode = kResamplerMode1To1;
  } else if (reduced_in_freq == 1) {
    switch (reduced_out_freq) {
      case 2:
        *mode = kResamplerMode1To2;
        break;
      case 3:
        *mode = kResamplerMode1To3;
        break;
      case 4:
        *mode = kResamplerMode1To4;
        break;
      case 6:
        *mode = kResamplerMode1To6;
        break;
      case 12:
        *mode = kResamplerMode1To12;
        break;
      default:
        return -1;
    }
  } else if (reduced_out_freq == 1) {
    switch (reduced_in_freq) {
      case 2:
        *mode = kResamplerMode2To1;
        break;
      case 3:
        *mode = kResamplerMode3To1;
        break;
      case 4:
        *mode = kResamplerMode4To1;
        break;
      case 6:
        *mode = kResamplerMode6To1;
        break;
      case 12:
        *mode = kResamplerMode12To1;
        break;
      default:
        return -1;
    }
  } else if ((reduced_in_freq == 2) && (reduced_out_freq == 3)) {
    *mode = kResamplerMode2To3;
  } else if ((reduced_in_freq == 2) && (reduced_out_freq == 11)) {
    *mode = kResamplerMode2To11;
  } else if ((reduced_in_freq == 4) && (reduced_out_freq == 11)) {
    *mode = kResamplerMode4To11;
  } else if ((reduced_in_freq == 8) && (reduced_out_freq == 11)) {
    *mode = kResamplerMode8To11;
  } else if ((reduced_in_freq == 3) && (reduced_out_freq == 2)) {
    *mode = kResamplerMode3To2;
  } else if ((reduced_in_freq == 11) && (reduced_out_freq == 2)) {
    *mode = kResamplerMode11To2;
  } else if ((reduced_in_freq == 11) && (reduced_out_freq == 4)) {
    *mode = kResamplerMode11To4;
  } else if ((reduced_in_freq == 11) && (reduced_out_freq == 16)) {
    *mode = kResamplerMode11To16;
  } else if ((reduced_in_freq == 11) && (reduced_out_freq == 32)) {
    *mode = kResamplerMode11To32;
  } else if ((reduced_in_freq == 11) && (reduced_out_freq == 8)) {
    *mode = kResamplerMode11To8;
  } else {
    return -1;
  }
  return 0;
}

// Synchronous resampling, all output samples are written to samplesOut
int Resampler::Push(const int16_t* samplesIn,
                    size_t lengthIn,
                    int16_t* samplesOut,
                    size_t maxLen,
                    size_t& outLen) {
  if (num_channels_ == 2) {
    // Split up the signal and call the helper object for each channel
    int16_t* left =
        static_cast<int16_t*>(malloc(lengthIn * sizeof(int16_t) / 2));
    int16_t* right =
        static_cast<int16_t*>(malloc(lengthIn * sizeof(int16_t) / 2));
    int16_t* out_left =
        static_cast<int16_t*>(malloc(maxLen / 2 * sizeof(int16_t)));
    int16_t* out_right =
        static_cast<int16_t*>(malloc(maxLen / 2 * sizeof(int16_t)));
    int res = 0;
    for (size_t i = 0; i < lengthIn; i += 2) {
      left[i >> 1] = samplesIn[i];
      right[i >> 1] = samplesIn[i + 1];
    }

    // It's OK to overwrite the local parameter, since it's just a copy
    lengthIn = lengthIn / 2;

    size_t actualOutLen_left = 0;
    size_t actualOutLen_right = 0;
    // Do resampling for right channel
    res |= helper_left_->Push(left, lengthIn, out_left, maxLen / 2,
                              actualOutLen_left);
    res |= helper_right_->Push(right, lengthIn, out_right, maxLen / 2,
                               actualOutLen_right);
    if (res || (actualOutLen_left != actualOutLen_right)) {
      free(left);
      free(right);
      free(out_left);
      free(out_right);
      return -1;
    }

    // Reassemble the signal
    for (size_t i = 0; i < actualOutLen_left; i++) {
      samplesOut[i * 2] = out_left[i];
      samplesOut[i * 2 + 1] = out_right[i];
    }
    outLen = 2 * actualOutLen_left;

    free(left);
    free(right);
    free(out_left);
    free(out_right);

    return 0;
  }

  // Containers for temp samples
  int16_t* tmp;
  int16_t* tmp_2;
  // tmp data for resampling routines
  int32_t* tmp_mem;

  switch (my_mode_) {
    case kResamplerMode1To1:
      memcpy(samplesOut, samplesIn, lengthIn * sizeof(int16_t));
      outLen = lengthIn;
      break;
    case kResamplerMode1To2:
      if (maxLen < (lengthIn * 2)) {
        return -1;
      }
      WebRtcSpl_UpsampleBy2(samplesIn, lengthIn, samplesOut,
                            static_cast<int32_t*>(state1_));
      outLen = lengthIn * 2;
      return 0;
    case kResamplerMode1To3:

      // We can only handle blocks of 160 samples
      // Can be fixed, but I don't think it's needed
      if ((lengthIn % 160) != 0) {
        return -1;
      }
      if (maxLen < (lengthIn * 3)) {
        return -1;
      }
      tmp_mem = static_cast<int32_t*>(malloc(336 * sizeof(int32_t)));

      for (size_t i = 0; i < lengthIn; i += 160) {
        WebRtcSpl_Resample16khzTo48khz(
            samplesIn + i, samplesOut + i * 3,
            static_cast<WebRtcSpl_State16khzTo48khz*>(state1_), tmp_mem);
      }
      outLen = lengthIn * 3;
      free(tmp_mem);
      return 0;
    case kResamplerMode1To4:
      if (maxLen < (lengthIn * 4)) {
        return -1;
      }

      tmp = static_cast<int16_t*>(malloc(sizeof(int16_t) * 2 * lengthIn));
      // 1:2
      WebRtcSpl_UpsampleBy2(samplesIn, lengthIn, tmp,
                            static_cast<int32_t*>(state1_));
      // 2:4
      WebRtcSpl_UpsampleBy2(tmp, lengthIn * 2, samplesOut,
                            static_cast<int32_t*>(state2_));
      outLen = lengthIn * 4;
      free(tmp);
      return 0;
    case kResamplerMode1To6:
      // We can only handle blocks of 80 samples
      // Can be fixed, but I don't think it's needed
      if ((lengthIn % 80) != 0) {
        return -1;
      }
      if (maxLen < (lengthIn * 6)) {
        return -1;
      }

      // 1:2

      tmp_mem = static_cast<int32_t*>(malloc(336 * sizeof(int32_t)));
      tmp = static_cast<int16_t*>(malloc(sizeof(int16_t) * 2 * lengthIn));

      WebRtcSpl_UpsampleBy2(samplesIn, lengthIn, tmp,
                            static_cast<int32_t*>(state1_));
      outLen = lengthIn * 2;

      for (size_t i = 0; i < outLen; i += 160) {
        WebRtcSpl_Resample16khzTo48khz(
            tmp + i, samplesOut + i * 3,
            static_cast<WebRtcSpl_State16khzTo48khz*>(state2_), tmp_mem);
      }
      outLen = outLen * 3;
      free(tmp_mem);
      free(tmp);

      return 0;
    case kResamplerMode1To12:
      // We can only handle blocks of 40 samples
      // Can be fixed, but I don't think it's needed
      if ((lengthIn % 40) != 0) {
        return -1;
      }
      if (maxLen < (lengthIn * 12)) {
        return -1;
      }

      tmp_mem = static_cast<int32_t*>(malloc(336 * sizeof(int32_t)));
      tmp = static_cast<int16_t*>(malloc(sizeof(int16_t) * 4 * lengthIn));
      // 1:2
      WebRtcSpl_UpsampleBy2(samplesIn, lengthIn, samplesOut,
                            static_cast<int32_t*>(state1_));
      outLen = lengthIn * 2;
      // 2:4
      WebRtcSpl_UpsampleBy2(samplesOut, outLen, tmp,
                            static_cast<int32_t*>(state2_));
      outLen = outLen * 2;
      // 4:12
      for (size_t i = 0; i < outLen; i += 160) {
        // WebRtcSpl_Resample16khzTo48khz() takes a block of 160 samples
        // as input and outputs a resampled block of 480 samples. The
        // data is now actually in 32 kHz sampling rate, despite the
        // function name, and with a resampling factor of three becomes
        // 96 kHz.
        WebRtcSpl_Resample16khzTo48khz(
            tmp + i, samplesOut + i * 3,
            static_cast<WebRtcSpl_State16khzTo48khz*>(state3_), tmp_mem);
      }
      outLen = outLen * 3;
      free(tmp_mem);
      free(tmp);

      return 0;
    case kResamplerMode2To3:
      if (maxLen < (lengthIn * 3 / 2)) {
        return -1;
      }
      // 2:6
      // We can only handle blocks of 160 samples
      // Can be fixed, but I don't think it's needed
      if ((lengthIn % 160) != 0) {
        return -1;
      }
      tmp = static_cast<int16_t*>(malloc(sizeof(int16_t) * lengthIn * 3));
      tmp_mem = static_cast<int32_t*>(malloc(336 * sizeof(int32_t)));
      for (size_t i = 0; i < lengthIn; i += 160) {
        WebRtcSpl_Resample16khzTo48khz(
            samplesIn + i, tmp + i * 3,
            static_cast<WebRtcSpl_State16khzTo48khz*>(state1_), tmp_mem);
      }
      lengthIn = lengthIn * 3;
      // 6:3
      WebRtcSpl_DownsampleBy2(tmp, lengthIn, samplesOut,
                              static_cast<int32_t*>(state2_));
      outLen = lengthIn / 2;
      free(tmp);
      free(tmp_mem);
      return 0;
    case kResamplerMode2To11:

      // We can only handle blocks of 80 samples
      // Can be fixed, but I don't think it's needed
      if ((lengthIn % 80) != 0) {
        return -1;
      }
      if (maxLen < ((lengthIn * 11) / 2)) {
        return -1;
      }
      tmp = static_cast<int16_t*>(malloc(sizeof(int16_t) * 2 * lengthIn));
      // 1:2
      WebRtcSpl_UpsampleBy2(samplesIn, lengthIn, tmp,
                            static_cast<int32_t*>(state1_));
      lengthIn *= 2;

      tmp_mem = static_cast<int32_t*>(malloc(98 * sizeof(int32_t)));

      for (size_t i = 0; i < lengthIn; i += 80) {
        WebRtcSpl_Resample8khzTo22khz(
            tmp + i, samplesOut + (i * 11) / 4,
            static_cast<WebRtcSpl_State8khzTo22khz*>(state2_), tmp_mem);
      }
      outLen = (lengthIn * 11) / 4;
      free(tmp_mem);
      free(tmp);
      return 0;
    case kResamplerMode4To11:

      // We can only handle blocks of 80 samples
      // Can be fixed, but I don't think it's needed
      if ((lengthIn % 80) != 0) {
        return -1;
      }
      if (maxLen < ((lengthIn * 11) / 4)) {
        return -1;
      }
      tmp_mem = static_cast<int32_t*>(malloc(98 * sizeof(int32_t)));

      for (size_t i = 0; i < lengthIn; i += 80) {
        WebRtcSpl_Resample8khzTo22khz(
            samplesIn + i, samplesOut + (i * 11) / 4,
            static_cast<WebRtcSpl_State8khzTo22khz*>(state1_), tmp_mem);
      }
      outLen = (lengthIn * 11) / 4;
      free(tmp_mem);
      return 0;
    case kResamplerMode8To11:
      // We can only handle blocks of 160 samples
      // Can be fixed, but I don't think it's needed
      if ((lengthIn % 160) != 0) {
        return -1;
      }
      if (maxLen < ((lengthIn * 11) / 8)) {
        return -1;
      }
      tmp_mem = static_cast<int32_t*>(malloc(88 * sizeof(int32_t)));

      for (size_t i = 0; i < lengthIn; i += 160) {
        WebRtcSpl_Resample16khzTo22khz(
            samplesIn + i, samplesOut + (i * 11) / 8,
            static_cast<WebRtcSpl_State16khzTo22khz*>(state1_), tmp_mem);
      }
      outLen = (lengthIn * 11) / 8;
      free(tmp_mem);
      return 0;

    case kResamplerMode11To16:
      // We can only handle blocks of 110 samples
      if ((lengthIn % 110) != 0) {
        return -1;
      }
      if (maxLen < ((lengthIn * 16) / 11)) {
        return -1;
      }

      tmp_mem = static_cast<int32_t*>(malloc(104 * sizeof(int32_t)));
      tmp = static_cast<int16_t*>(malloc((sizeof(int16_t) * lengthIn * 2)));

      WebRtcSpl_UpsampleBy2(samplesIn, lengthIn, tmp,
                            static_cast<int32_t*>(state1_));

      for (size_t i = 0; i < (lengthIn * 2); i += 220) {
        WebRtcSpl_Resample22khzTo16khz(
            tmp + i, samplesOut + (i / 220) * 160,
            static_cast<WebRtcSpl_State22khzTo16khz*>(state2_), tmp_mem);
      }

      outLen = (lengthIn * 16) / 11;

      free(tmp_mem);
      free(tmp);
      return 0;

    case kResamplerMode11To32:

      // We can only handle blocks of 110 samples
      if ((lengthIn % 110) != 0) {
        return -1;
      }
      if (maxLen < ((lengthIn * 32) / 11)) {
        return -1;
      }

      tmp_mem = static_cast<int32_t*>(malloc(104 * sizeof(int32_t)));
      tmp = static_cast<int16_t*>(malloc((sizeof(int16_t) * lengthIn * 2)));

      // 11 -> 22 kHz in samplesOut
      WebRtcSpl_UpsampleBy2(samplesIn, lengthIn, samplesOut,
                            static_cast<int32_t*>(state1_));

      // 22 -> 16 in tmp
      for (size_t i = 0; i < (lengthIn * 2); i += 220) {
        WebRtcSpl_Resample22khzTo16khz(
            samplesOut + i, tmp + (i / 220) * 160,
            static_cast<WebRtcSpl_State22khzTo16khz*>(state2_), tmp_mem);
      }

      // 16 -> 32 in samplesOut
      WebRtcSpl_UpsampleBy2(tmp, (lengthIn * 16) / 11, samplesOut,
                            static_cast<int32_t*>(state3_));

      outLen = (lengthIn * 32) / 11;

      free(tmp_mem);
      free(tmp);
      return 0;

    case kResamplerMode2To1:
      if (maxLen < (lengthIn / 2)) {
        return -1;
      }
      WebRtcSpl_DownsampleBy2(samplesIn, lengthIn, samplesOut,
                              static_cast<int32_t*>(state1_));
      outLen = lengthIn / 2;
      return 0;
    case kResamplerMode3To1:
      // We can only handle blocks of 480 samples
      // Can be fixed, but I don't think it's needed
      if ((lengthIn % 480) != 0) {
        return -1;
      }
      if (maxLen < (lengthIn / 3)) {
        return -1;
      }
      tmp_mem = static_cast<int32_t*>(malloc(496 * sizeof(int32_t)));

      for (size_t i = 0; i < lengthIn; i += 480) {
        WebRtcSpl_Resample48khzTo16khz(
            samplesIn + i, samplesOut + i / 3,
            static_cast<WebRtcSpl_State48khzTo16khz*>(state1_), tmp_mem);
      }
      outLen = lengthIn / 3;
      free(tmp_mem);
      return 0;
    case kResamplerMode4To1:
      if (maxLen < (lengthIn / 4)) {
        return -1;
      }
      tmp = static_cast<int16_t*>(malloc(sizeof(int16_t) * lengthIn / 2));
      // 4:2
      WebRtcSpl_DownsampleBy2(samplesIn, lengthIn, tmp,
                              static_cast<int32_t*>(state1_));
      // 2:1
      WebRtcSpl_DownsampleBy2(tmp, lengthIn / 2, samplesOut,
                              static_cast<int32_t*>(state2_));
      outLen = lengthIn / 4;
      free(tmp);
      return 0;

    case kResamplerMode6To1:
      // We can only handle blocks of 480 samples
      // Can be fixed, but I don't think it's needed
      if ((lengthIn % 480) != 0) {
        return -1;
      }
      if (maxLen < (lengthIn / 6)) {
        return -1;
      }

      tmp_mem = static_cast<int32_t*>(malloc(496 * sizeof(int32_t)));
      tmp = static_cast<int16_t*>(malloc((sizeof(int16_t) * lengthIn) / 3));

      for (size_t i = 0; i < lengthIn; i += 480) {
        WebRtcSpl_Resample48khzTo16khz(
            samplesIn + i, tmp + i / 3,
            static_cast<WebRtcSpl_State48khzTo16khz*>(state1_), tmp_mem);
      }
      outLen = lengthIn / 3;
      free(tmp_mem);
      WebRtcSpl_DownsampleBy2(tmp, outLen, samplesOut,
                              static_cast<int32_t*>(state2_));
      free(tmp);
      outLen = outLen / 2;
      return 0;
    case kResamplerMode12To1:
      // We can only handle blocks of 480 samples
      // Can be fixed, but I don't think it's needed
      if ((lengthIn % 480) != 0) {
        return -1;
      }
      if (maxLen < (lengthIn / 12)) {
        return -1;
      }

      tmp_mem = static_cast<int32_t*>(malloc(496 * sizeof(int32_t)));
      tmp = static_cast<int16_t*>(malloc((sizeof(int16_t) * lengthIn) / 3));
      tmp_2 = static_cast<int16_t*>(malloc((sizeof(int16_t) * lengthIn) / 6));
      // 12:4
      for (size_t i = 0; i < lengthIn; i += 480) {
        // WebRtcSpl_Resample48khzTo16khz() takes a block of 480 samples
        // as input and outputs a resampled block of 160 samples. The
        // data is now actually in 96 kHz sampling rate, despite the
        // function name, and with a resampling factor of 1/3 becomes
        // 32 kHz.
        WebRtcSpl_Resample48khzTo16khz(
            samplesIn + i, tmp + i / 3,
            static_cast<WebRtcSpl_State48khzTo16khz*>(state1_), tmp_mem);
      }
      outLen = lengthIn / 3;
      free(tmp_mem);
      // 4:2
      WebRtcSpl_DownsampleBy2(tmp, outLen, tmp_2,
                              static_cast<int32_t*>(state2_));
      outLen = outLen / 2;
      free(tmp);
      // 2:1
      WebRtcSpl_DownsampleBy2(tmp_2, outLen, samplesOut,
                              static_cast<int32_t*>(state3_));
      free(tmp_2);
      outLen = outLen / 2;
      return 0;
    case kResamplerMode3To2:
      if (maxLen < (lengthIn * 2 / 3)) {
        return -1;
      }
      // 3:6
      tmp = static_cast<int16_t*>(malloc(sizeof(int16_t) * lengthIn * 2));
      WebRtcSpl_UpsampleBy2(samplesIn, lengthIn, tmp,
                            static_cast<int32_t*>(state1_));
      lengthIn *= 2;
      // 6:2
      // We can only handle blocks of 480 samples
      // Can be fixed, but I don't think it's needed
      if ((lengthIn % 480) != 0) {
        free(tmp);
        return -1;
      }
      tmp_mem = static_cast<int32_t*>(malloc(496 * sizeof(int32_t)));
      for (size_t i = 0; i < lengthIn; i += 480) {
        WebRtcSpl_Resample48khzTo16khz(
            tmp + i, samplesOut + i / 3,
            static_cast<WebRtcSpl_State48khzTo16khz*>(state2_), tmp_mem);
      }
      outLen = lengthIn / 3;
      free(tmp);
      free(tmp_mem);
      return 0;
    case kResamplerMode11To2:
      // We can only handle blocks of 220 samples
      // Can be fixed, but I don't think it's needed
      if ((lengthIn % 220) != 0) {
        return -1;
      }
      if (maxLen < ((lengthIn * 2) / 11)) {
        return -1;
      }
      tmp_mem = static_cast<int32_t*>(malloc(126 * sizeof(int32_t)));
      tmp =
          static_cast<int16_t*>(malloc((lengthIn * 4) / 11 * sizeof(int16_t)));

      for (size_t i = 0; i < lengthIn; i += 220) {
        WebRtcSpl_Resample22khzTo8khz(
            samplesIn + i, tmp + (i * 4) / 11,
            static_cast<WebRtcSpl_State22khzTo8khz*>(state1_), tmp_mem);
      }
      lengthIn = (lengthIn * 4) / 11;

      WebRtcSpl_DownsampleBy2(tmp, lengthIn, samplesOut,
                              static_cast<int32_t*>(state2_));
      outLen = lengthIn / 2;

      free(tmp_mem);
      free(tmp);
      return 0;
    case kResamplerMode11To4:
      // We can only handle blocks of 220 samples
      // Can be fixed, but I don't think it's needed
      if ((lengthIn % 220) != 0) {
        return -1;
      }
      if (maxLen < ((lengthIn * 4) / 11)) {
        return -1;
      }
      tmp_mem = static_cast<int32_t*>(malloc(126 * sizeof(int32_t)));

      for (size_t i = 0; i < lengthIn; i += 220) {
        WebRtcSpl_Resample22khzTo8khz(
            samplesIn + i, samplesOut + (i * 4) / 11,
            static_cast<WebRtcSpl_State22khzTo8khz*>(state1_), tmp_mem);
      }
      outLen = (lengthIn * 4) / 11;
      free(tmp_mem);
      return 0;
    case kResamplerMode11To8:
      // We can only handle blocks of 160 samples
      // Can be fixed, but I don't think it's needed
      if ((lengthIn % 220) != 0) {
        return -1;
      }
      if (maxLen < ((lengthIn * 8) / 11)) {
        return -1;
      }
      tmp_mem = static_cast<int32_t*>(malloc(104 * sizeof(int32_t)));

      for (size_t i = 0; i < lengthIn; i += 220) {
        WebRtcSpl_Resample22khzTo16khz(
            samplesIn + i, samplesOut + (i * 8) / 11,
            static_cast<WebRtcSpl_State22khzTo16khz*>(state1_), tmp_mem);
      }
      outLen = (lengthIn * 8) / 11;
      free(tmp_mem);
      return 0;
  }
  return 0;
}

}  // namespace webrtc

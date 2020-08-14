/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/vad/vad_sp.h"

#include "rtc_base/checks.h"
#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "common_audio/vad/vad_core.h"

// Allpass filter coefficients, upper and lower, in Q13.
// Upper: 0.64, Lower: 0.17.
static const int16_t kAllPassCoefsQ13[2] = { 5243, 1392 };  // Q13.
static const int16_t kSmoothingDown = 6553;  // 0.2 in Q15.
static const int16_t kSmoothingUp = 32439;  // 0.99 in Q15.

// TODO(bjornv): Move this function to vad_filterbank.c.
// Downsampling filter based on splitting filter and allpass functions.
void WebRtcVad_Downsampling(const int16_t* signal_in,
                            int16_t* signal_out,
                            int32_t* filter_state,
                            size_t in_length) {
  int16_t tmp16_1 = 0, tmp16_2 = 0;
  int32_t tmp32_1 = filter_state[0];
  int32_t tmp32_2 = filter_state[1];
  size_t n = 0;
  // Downsampling by 2 gives half length.
  size_t half_length = (in_length >> 1);

  // Filter coefficients in Q13, filter state in Q0.
  for (n = 0; n < half_length; n++) {
    // All-pass filtering upper branch.
    tmp16_1 = (int16_t) ((tmp32_1 >> 1) +
        ((kAllPassCoefsQ13[0] * *signal_in) >> 14));
    *signal_out = tmp16_1;
    tmp32_1 = (int32_t)(*signal_in++) - ((kAllPassCoefsQ13[0] * tmp16_1) >> 12);

    // All-pass filtering lower branch.
    tmp16_2 = (int16_t) ((tmp32_2 >> 1) +
        ((kAllPassCoefsQ13[1] * *signal_in) >> 14));
    *signal_out++ += tmp16_2;
    tmp32_2 = (int32_t)(*signal_in++) - ((kAllPassCoefsQ13[1] * tmp16_2) >> 12);
  }
  // Store the filter states.
  filter_state[0] = tmp32_1;
  filter_state[1] = tmp32_2;
}

// Inserts |feature_value| into |low_value_vector|, if it is one of the 16
// smallest values the last 100 frames. Then calculates and returns the median
// of the five smallest values.
int16_t WebRtcVad_FindMinimum(VadInstT* self,
                              int16_t feature_value,
                              int channel) {
  int i = 0, j = 0;
  int position = -1;
  // Offset to beginning of the 16 minimum values in memory.
  const int offset = (channel << 4);
  int16_t current_median = 1600;
  int16_t alpha = 0;
  int32_t tmp32 = 0;
  // Pointer to memory for the 16 minimum values and the age of each value of
  // the |channel|.
  int16_t* age = &self->index_vector[offset];
  int16_t* smallest_values = &self->low_value_vector[offset];

  RTC_DCHECK_LT(channel, kNumChannels);

  // Each value in |smallest_values| is getting 1 loop older. Update |age|, and
  // remove old values.
  for (i = 0; i < 16; i++) {
    if (age[i] != 100) {
      age[i]++;
    } else {
      // Too old value. Remove from memory and shift larger values downwards.
      for (j = i; j < 15; j++) {
        smallest_values[j] = smallest_values[j + 1];
        age[j] = age[j + 1];
      }
      age[15] = 101;
      smallest_values[15] = 10000;
    }
  }

  // Check if |feature_value| is smaller than any of the values in
  // |smallest_values|. If so, find the |position| where to insert the new value
  // (|feature_value|).
  if (feature_value < smallest_values[7]) {
    if (feature_value < smallest_values[3]) {
      if (feature_value < smallest_values[1]) {
        if (feature_value < smallest_values[0]) {
          position = 0;
        } else {
          position = 1;
        }
      } else if (feature_value < smallest_values[2]) {
        position = 2;
      } else {
        position = 3;
      }
    } else if (feature_value < smallest_values[5]) {
      if (feature_value < smallest_values[4]) {
        position = 4;
      } else {
        position = 5;
      }
    } else if (feature_value < smallest_values[6]) {
      position = 6;
    } else {
      position = 7;
    }
  } else if (feature_value < smallest_values[15]) {
    if (feature_value < smallest_values[11]) {
      if (feature_value < smallest_values[9]) {
        if (feature_value < smallest_values[8]) {
          position = 8;
        } else {
          position = 9;
        }
      } else if (feature_value < smallest_values[10]) {
        position = 10;
      } else {
        position = 11;
      }
    } else if (feature_value < smallest_values[13]) {
      if (feature_value < smallest_values[12]) {
        position = 12;
      } else {
        position = 13;
      }
    } else if (feature_value < smallest_values[14]) {
      position = 14;
    } else {
      position = 15;
    }
  }

  // If we have detected a new small value, insert it at the correct position
  // and shift larger values up.
  if (position > -1) {
    for (i = 15; i > position; i--) {
      smallest_values[i] = smallest_values[i - 1];
      age[i] = age[i - 1];
    }
    smallest_values[position] = feature_value;
    age[position] = 1;
  }

  // Get |current_median|.
  if (self->frame_counter > 2) {
    current_median = smallest_values[2];
  } else if (self->frame_counter > 0) {
    current_median = smallest_values[0];
  }

  // Smooth the median value.
  if (self->frame_counter > 0) {
    if (current_median < self->mean_value[channel]) {
      alpha = kSmoothingDown;  // 0.2 in Q15.
    } else {
      alpha = kSmoothingUp;  // 0.99 in Q15.
    }
  }
  tmp32 = (alpha + 1) * self->mean_value[channel];
  tmp32 += (WEBRTC_SPL_WORD16_MAX - alpha) * current_median;
  tmp32 += 16384;
  self->mean_value[channel] = (int16_t) (tmp32 >> 15);

  return self->mean_value[channel];
}

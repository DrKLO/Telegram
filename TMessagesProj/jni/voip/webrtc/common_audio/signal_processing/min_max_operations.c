/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * This file contains the implementation of functions
 * WebRtcSpl_MaxAbsValueW16C()
 * WebRtcSpl_MaxAbsValueW32C()
 * WebRtcSpl_MaxValueW16C()
 * WebRtcSpl_MaxValueW32C()
 * WebRtcSpl_MinValueW16C()
 * WebRtcSpl_MinValueW32C()
 * WebRtcSpl_MaxAbsIndexW16()
 * WebRtcSpl_MaxIndexW16()
 * WebRtcSpl_MaxIndexW32()
 * WebRtcSpl_MinIndexW16()
 * WebRtcSpl_MinIndexW32()
 *
 */

#include <stdlib.h>

#include "rtc_base/checks.h"
#include "common_audio/signal_processing/include/signal_processing_library.h"

// TODO(bjorn/kma): Consolidate function pairs (e.g. combine
//   WebRtcSpl_MaxAbsValueW16C and WebRtcSpl_MaxAbsIndexW16 into a single one.)
// TODO(kma): Move the next six functions into min_max_operations_c.c.

// Maximum absolute value of word16 vector. C version for generic platforms.
int16_t WebRtcSpl_MaxAbsValueW16C(const int16_t* vector, size_t length) {
  size_t i = 0;
  int absolute = 0, maximum = 0;

  RTC_DCHECK_GT(length, 0);

  for (i = 0; i < length; i++) {
    absolute = abs((int)vector[i]);

    if (absolute > maximum) {
      maximum = absolute;
    }
  }

  // Guard the case for abs(-32768).
  if (maximum > WEBRTC_SPL_WORD16_MAX) {
    maximum = WEBRTC_SPL_WORD16_MAX;
  }

  return (int16_t)maximum;
}

// Maximum absolute value of word32 vector. C version for generic platforms.
int32_t WebRtcSpl_MaxAbsValueW32C(const int32_t* vector, size_t length) {
  // Use uint32_t for the local variables, to accommodate the return value
  // of abs(0x80000000), which is 0x80000000.

  uint32_t absolute = 0, maximum = 0;
  size_t i = 0;

  RTC_DCHECK_GT(length, 0);

  for (i = 0; i < length; i++) {
    absolute = abs((int)vector[i]);
    if (absolute > maximum) {
      maximum = absolute;
    }
  }

  maximum = WEBRTC_SPL_MIN(maximum, WEBRTC_SPL_WORD32_MAX);

  return (int32_t)maximum;
}

// Maximum value of word16 vector. C version for generic platforms.
int16_t WebRtcSpl_MaxValueW16C(const int16_t* vector, size_t length) {
  int16_t maximum = WEBRTC_SPL_WORD16_MIN;
  size_t i = 0;

  RTC_DCHECK_GT(length, 0);

  for (i = 0; i < length; i++) {
    if (vector[i] > maximum)
      maximum = vector[i];
  }
  return maximum;
}

// Maximum value of word32 vector. C version for generic platforms.
int32_t WebRtcSpl_MaxValueW32C(const int32_t* vector, size_t length) {
  int32_t maximum = WEBRTC_SPL_WORD32_MIN;
  size_t i = 0;

  RTC_DCHECK_GT(length, 0);

  for (i = 0; i < length; i++) {
    if (vector[i] > maximum)
      maximum = vector[i];
  }
  return maximum;
}

// Minimum value of word16 vector. C version for generic platforms.
int16_t WebRtcSpl_MinValueW16C(const int16_t* vector, size_t length) {
  int16_t minimum = WEBRTC_SPL_WORD16_MAX;
  size_t i = 0;

  RTC_DCHECK_GT(length, 0);

  for (i = 0; i < length; i++) {
    if (vector[i] < minimum)
      minimum = vector[i];
  }
  return minimum;
}

// Minimum value of word32 vector. C version for generic platforms.
int32_t WebRtcSpl_MinValueW32C(const int32_t* vector, size_t length) {
  int32_t minimum = WEBRTC_SPL_WORD32_MAX;
  size_t i = 0;

  RTC_DCHECK_GT(length, 0);

  for (i = 0; i < length; i++) {
    if (vector[i] < minimum)
      minimum = vector[i];
  }
  return minimum;
}

// Index of maximum absolute value in a word16 vector.
size_t WebRtcSpl_MaxAbsIndexW16(const int16_t* vector, size_t length) {
  // Use type int for local variables, to accomodate the value of abs(-32768).

  size_t i = 0, index = 0;
  int absolute = 0, maximum = 0;

  RTC_DCHECK_GT(length, 0);

  for (i = 0; i < length; i++) {
    absolute = abs((int)vector[i]);

    if (absolute > maximum) {
      maximum = absolute;
      index = i;
    }
  }

  return index;
}

// Index of maximum value in a word16 vector.
size_t WebRtcSpl_MaxIndexW16(const int16_t* vector, size_t length) {
  size_t i = 0, index = 0;
  int16_t maximum = WEBRTC_SPL_WORD16_MIN;

  RTC_DCHECK_GT(length, 0);

  for (i = 0; i < length; i++) {
    if (vector[i] > maximum) {
      maximum = vector[i];
      index = i;
    }
  }

  return index;
}

// Index of maximum value in a word32 vector.
size_t WebRtcSpl_MaxIndexW32(const int32_t* vector, size_t length) {
  size_t i = 0, index = 0;
  int32_t maximum = WEBRTC_SPL_WORD32_MIN;

  RTC_DCHECK_GT(length, 0);

  for (i = 0; i < length; i++) {
    if (vector[i] > maximum) {
      maximum = vector[i];
      index = i;
    }
  }

  return index;
}

// Index of minimum value in a word16 vector.
size_t WebRtcSpl_MinIndexW16(const int16_t* vector, size_t length) {
  size_t i = 0, index = 0;
  int16_t minimum = WEBRTC_SPL_WORD16_MAX;

  RTC_DCHECK_GT(length, 0);

  for (i = 0; i < length; i++) {
    if (vector[i] < minimum) {
      minimum = vector[i];
      index = i;
    }
  }

  return index;
}

// Index of minimum value in a word32 vector.
size_t WebRtcSpl_MinIndexW32(const int32_t* vector, size_t length) {
  size_t i = 0, index = 0;
  int32_t minimum = WEBRTC_SPL_WORD32_MAX;

  RTC_DCHECK_GT(length, 0);

  for (i = 0; i < length; i++) {
    if (vector[i] < minimum) {
      minimum = vector[i];
      index = i;
    }
  }

  return index;
}

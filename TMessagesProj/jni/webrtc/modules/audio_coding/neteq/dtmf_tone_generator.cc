/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This class provides a generator for DTMF tones. The tone generation is based
// on a sinusoid recursion. Each sinusoid is generated using a recursion
// formula; x[n] = a * x[n-1] - x[n-2], where the coefficient
// a = 2*cos(2*pi*f/fs). The recursion is started with x[-1] = 0 and
// x[-2] = sin(2*pi*f/fs). (Note that with this initialization, the resulting
// sinusoid gets a "negative" rotation; x[n] = sin(-2*pi*f/fs * n + phi), but
// kept this way due to historical reasons.)
// TODO(hlundin): Change to positive rotation?
//
// Each key on the telephone keypad corresponds to an "event", 0-15. Each event
// is mapped to a tone pair, with a low and a high frequency. There are four
// low and four high frequencies, each corresponding to a row and column,
// respectively, on the keypad as illustrated below.
//
//          1209 Hz  1336 Hz  1477 Hz  1633 Hz
// 697 Hz      1        2        3       12
// 770 Hz      4        5        6       13
// 852 Hz      7        8        9       14
// 941 Hz     10        0       11       15

#include "modules/audio_coding/neteq/dtmf_tone_generator.h"

#include "modules/audio_coding/neteq/audio_vector.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"

namespace webrtc {

// The filter coefficient a = 2*cos(2*pi*f/fs) for the low frequency tone, for
// sample rates fs = {8000, 16000, 32000, 48000} Hz, and events 0 through 15.
// Values are in Q14.
const int DtmfToneGenerator::kCoeff1[4][16] = {
    {24219, 27980, 27980, 27980, 26956, 26956, 26956, 25701, 25701, 25701,
     24219, 24219, 27980, 26956, 25701, 24219},
    {30556, 31548, 31548, 31548, 31281, 31281, 31281, 30951, 30951, 30951,
     30556, 30556, 31548, 31281, 30951, 30556},
    {32210, 32462, 32462, 32462, 32394, 32394, 32394, 32311, 32311, 32311,
     32210, 32210, 32462, 32394, 32311, 32210},
    {32520, 32632, 32632, 32632, 32602, 32602, 32602, 32564, 32564, 32564,
     32520, 32520, 32632, 32602, 32564, 32520}};

// The filter coefficient a = 2*cos(2*pi*f/fs) for the high frequency tone, for
// sample rates fs = {8000, 16000, 32000, 48000} Hz, and events 0 through 15.
// Values are in Q14.
const int DtmfToneGenerator::kCoeff2[4][16] = {
    {16325, 19073, 16325, 13085, 19073, 16325, 13085, 19073, 16325, 13085,
     19073, 13085, 9315, 9315, 9315, 9315},
    {28361, 29144, 28361, 27409, 29144, 28361, 27409, 29144, 28361, 27409,
     29144, 27409, 26258, 26258, 26258, 26258},
    {31647, 31849, 31647, 31400, 31849, 31647, 31400, 31849, 31647, 31400,
     31849, 31400, 31098, 31098, 31098, 31098},
    {32268, 32359, 32268, 32157, 32359, 32268, 32157, 32359, 32268, 32157,
     32359, 32157, 32022, 32022, 32022, 32022}};

// The initialization value x[-2] = sin(2*pi*f/fs) for the low frequency tone,
// for sample rates fs = {8000, 16000, 32000, 48000} Hz, and events 0-15.
// Values are in Q14.
const int DtmfToneGenerator::kInitValue1[4][16] = {
    {11036, 8528, 8528, 8528, 9315, 9315, 9315, 10163, 10163, 10163, 11036,
     11036, 8528, 9315, 10163, 11036},
    {5918, 4429, 4429, 4429, 4879, 4879, 4879, 5380, 5380, 5380, 5918, 5918,
     4429, 4879, 5380, 5918},
    {3010, 2235, 2235, 2235, 2468, 2468, 2468, 2728, 2728, 2728, 3010, 3010,
     2235, 2468, 2728, 3010},
    {2013, 1493, 1493, 1493, 1649, 1649, 1649, 1823, 1823, 1823, 2013, 2013,
     1493, 1649, 1823, 2013}};

// The initialization value x[-2] = sin(2*pi*f/fs) for the high frequency tone,
// for sample rates fs = {8000, 16000, 32000, 48000} Hz, and events 0-15.
// Values are in Q14.
const int DtmfToneGenerator::kInitValue2[4][16] = {
    {14206, 13323, 14206, 15021, 13323, 14206, 15021, 13323, 14206, 15021,
     13323, 15021, 15708, 15708, 15708, 15708},
    {8207, 7490, 8207, 8979, 7490, 8207, 8979, 7490, 8207, 8979, 7490, 8979,
     9801, 9801, 9801, 9801},
    {4249, 3853, 4249, 4685, 3853, 4249, 4685, 3853, 4249, 4685, 3853, 4685,
     5164, 5164, 5164, 5164},
    {2851, 2582, 2851, 3148, 2582, 2851, 3148, 2582, 2851, 3148, 2582, 3148,
     3476, 3476, 3476, 3476}};

// Amplitude multipliers for volume values 0 through 63, corresponding to
// 0 dBm0 through -63 dBm0. Values are in Q14.
// for a in range(0, 64):
//   print round(16141.0 * 10**(-float(a)/20))
const int DtmfToneGenerator::kAmplitude[64] = {
    16141, 14386, 12821, 11427, 10184, 9077, 8090, 7210, 6426, 5727, 5104,
    4549,  4054,  3614,  3221,  2870,  2558, 2280, 2032, 1811, 1614, 1439,
    1282,  1143,  1018,  908,   809,   721,  643,  573,  510,  455,  405,
    361,   322,   287,   256,   228,   203,  181,  161,  144,  128,  114,
    102,   91,    81,    72,    64,    57,   51,   45,   41,   36,   32,
    29,    26,    23,    20,    18,    16,   14,   13,   11};

// Constructor.
DtmfToneGenerator::DtmfToneGenerator()
    : initialized_(false), coeff1_(0), coeff2_(0), amplitude_(0) {}

// Initialize the DTMF generator with sample rate fs Hz (8000, 16000, 32000,
// 48000), event (0-15) and attenuation (0-36 dB).
// Returns 0 on success, otherwise an error code.
int DtmfToneGenerator::Init(int fs, int event, int attenuation) {
  initialized_ = false;
  size_t fs_index;
  if (fs == 8000) {
    fs_index = 0;
  } else if (fs == 16000) {
    fs_index = 1;
  } else if (fs == 32000) {
    fs_index = 2;
  } else if (fs == 48000) {
    fs_index = 3;
  } else {
    RTC_NOTREACHED();
    fs_index = 1;  // Default to 8000 Hz.
  }

  if (event < 0 || event > 15) {
    return kParameterError;  // Invalid event number.
  }

  if (attenuation < 0 || attenuation > 63) {
    return kParameterError;  // Invalid attenuation.
  }

  // Look up oscillator coefficient for low and high frequencies.
  RTC_DCHECK_LE(0, fs_index);
  RTC_DCHECK_GT(arraysize(kCoeff1), fs_index);
  RTC_DCHECK_GT(arraysize(kCoeff2), fs_index);
  RTC_DCHECK_LE(0, event);
  RTC_DCHECK_GT(arraysize(kCoeff1[fs_index]), event);
  RTC_DCHECK_GT(arraysize(kCoeff2[fs_index]), event);
  coeff1_ = kCoeff1[fs_index][event];
  coeff2_ = kCoeff2[fs_index][event];

  // Look up amplitude multiplier.
  RTC_DCHECK_LE(0, attenuation);
  RTC_DCHECK_GT(arraysize(kAmplitude), attenuation);
  amplitude_ = kAmplitude[attenuation];

  // Initialize sample history.
  RTC_DCHECK_LE(0, fs_index);
  RTC_DCHECK_GT(arraysize(kInitValue1), fs_index);
  RTC_DCHECK_GT(arraysize(kInitValue2), fs_index);
  RTC_DCHECK_LE(0, event);
  RTC_DCHECK_GT(arraysize(kInitValue1[fs_index]), event);
  RTC_DCHECK_GT(arraysize(kInitValue2[fs_index]), event);
  sample_history1_[0] = kInitValue1[fs_index][event];
  sample_history1_[1] = 0;
  sample_history2_[0] = kInitValue2[fs_index][event];
  sample_history2_[1] = 0;

  initialized_ = true;
  return 0;
}

// Reset tone generator to uninitialized state.
void DtmfToneGenerator::Reset() {
  initialized_ = false;
}

// Generate num_samples of DTMF signal and write to |output|.
int DtmfToneGenerator::Generate(size_t num_samples, AudioMultiVector* output) {
  if (!initialized_) {
    return kNotInitialized;
  }

  if (!output) {
    return kParameterError;
  }

  output->AssertSize(num_samples);
  for (size_t i = 0; i < num_samples; ++i) {
    // Use recursion formula y[n] = a * y[n - 1] - y[n - 2].
    int16_t temp_val_low =
        ((coeff1_ * sample_history1_[1] + 8192) >> 14) - sample_history1_[0];
    int16_t temp_val_high =
        ((coeff2_ * sample_history2_[1] + 8192) >> 14) - sample_history2_[0];

    // Update recursion memory.
    sample_history1_[0] = sample_history1_[1];
    sample_history1_[1] = temp_val_low;
    sample_history2_[0] = sample_history2_[1];
    sample_history2_[1] = temp_val_high;

    // Attenuate the low frequency tone 3 dB.
    int32_t temp_val =
        kAmpMultiplier * temp_val_low + temp_val_high * (1 << 15);
    // Normalize the signal to Q14 with proper rounding.
    temp_val = (temp_val + 16384) >> 15;
    // Scale the signal to correct volume.
    (*output)[0][i] =
        static_cast<int16_t>((temp_val * amplitude_ + 8192) >> 14);
  }
  // Copy first channel to all other channels.
  for (size_t channel = 1; channel < output->Channels(); ++channel) {
    output->CopyChannel(0, channel);
  }

  return static_cast<int>(num_samples);
}

bool DtmfToneGenerator::initialized() const {
  return initialized_;
}

}  // namespace webrtc

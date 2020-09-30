/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/cng/webrtc_cng.h"

#include <algorithm>

#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {

namespace {

const size_t kCngMaxOutsizeOrder = 640;

// TODO(ossu): Rename the left-over WebRtcCng according to style guide.
void WebRtcCng_K2a16(int16_t* k, int useOrder, int16_t* a);

const int32_t WebRtcCng_kDbov[94] = {
    1081109975, 858756178, 682134279, 541838517, 430397633, 341876992,
    271562548,  215709799, 171344384, 136103682, 108110997, 85875618,
    68213428,   54183852,  43039763,  34187699,  27156255,  21570980,
    17134438,   13610368,  10811100,  8587562,   6821343,   5418385,
    4303976,    3418770,   2715625,   2157098,   1713444,   1361037,
    1081110,    858756,    682134,    541839,    430398,    341877,
    271563,     215710,    171344,    136104,    108111,    85876,
    68213,      54184,     43040,     34188,     27156,     21571,
    17134,      13610,     10811,     8588,      6821,      5418,
    4304,       3419,      2716,      2157,      1713,      1361,
    1081,       859,       682,       542,       430,       342,
    272,        216,       171,       136,       108,       86,
    68,         54,        43,        34,        27,        22,
    17,         14,        11,        9,         7,         5,
    4,          3,         3,         2,         2,         1,
    1,          1,         1,         1};

const int16_t WebRtcCng_kCorrWindow[WEBRTC_CNG_MAX_LPC_ORDER] = {
    32702, 32636, 32570, 32505, 32439, 32374,
    32309, 32244, 32179, 32114, 32049, 31985};

}  // namespace

ComfortNoiseDecoder::ComfortNoiseDecoder() {
  /* Needed to get the right function pointers in SPLIB. */
  Reset();
}

void ComfortNoiseDecoder::Reset() {
  dec_seed_ = 7777; /* For debugging only. */
  dec_target_energy_ = 0;
  dec_used_energy_ = 0;
  for (auto& c : dec_target_reflCoefs_)
    c = 0;
  for (auto& c : dec_used_reflCoefs_)
    c = 0;
  for (auto& c : dec_filtstate_)
    c = 0;
  for (auto& c : dec_filtstateLow_)
    c = 0;
  dec_order_ = 5;
  dec_target_scale_factor_ = 0;
  dec_used_scale_factor_ = 0;
}

void ComfortNoiseDecoder::UpdateSid(rtc::ArrayView<const uint8_t> sid) {
  int16_t refCs[WEBRTC_CNG_MAX_LPC_ORDER];
  int32_t targetEnergy;
  size_t length = sid.size();
  /* Throw away reflection coefficients of higher order than we can handle. */
  if (length > (WEBRTC_CNG_MAX_LPC_ORDER + 1))
    length = WEBRTC_CNG_MAX_LPC_ORDER + 1;

  dec_order_ = static_cast<uint16_t>(length - 1);

  uint8_t sid0 = std::min<uint8_t>(sid[0], 93);
  targetEnergy = WebRtcCng_kDbov[sid0];
  /* Take down target energy to 75%. */
  targetEnergy = targetEnergy >> 1;
  targetEnergy += targetEnergy >> 2;

  dec_target_energy_ = targetEnergy;

  /* Reconstruct coeffs with tweak for WebRtc implementation of RFC3389. */
  if (dec_order_ == WEBRTC_CNG_MAX_LPC_ORDER) {
    for (size_t i = 0; i < (dec_order_); i++) {
      refCs[i] = sid[i + 1] << 8; /* Q7 to Q15*/
      dec_target_reflCoefs_[i] = refCs[i];
    }
  } else {
    for (size_t i = 0; i < (dec_order_); i++) {
      refCs[i] = (sid[i + 1] - 127) * (1 << 8); /* Q7 to Q15. */
      dec_target_reflCoefs_[i] = refCs[i];
    }
  }

  for (size_t i = (dec_order_); i < WEBRTC_CNG_MAX_LPC_ORDER; i++) {
    refCs[i] = 0;
    dec_target_reflCoefs_[i] = refCs[i];
  }
}

bool ComfortNoiseDecoder::Generate(rtc::ArrayView<int16_t> out_data,
                                   bool new_period) {
  int16_t excitation[kCngMaxOutsizeOrder];
  int16_t low[kCngMaxOutsizeOrder];
  int16_t lpPoly[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t ReflBetaStd = 26214;      /* 0.8 in q15. */
  int16_t ReflBetaCompStd = 6553;   /* 0.2 in q15. */
  int16_t ReflBetaNewP = 19661;     /* 0.6 in q15. */
  int16_t ReflBetaCompNewP = 13107; /* 0.4 in q15. */
  int16_t Beta, BetaC;              /* These are in Q15. */
  int32_t targetEnergy;
  int16_t En;
  int16_t temp16;
  const size_t num_samples = out_data.size();

  if (num_samples > kCngMaxOutsizeOrder) {
    return false;
  }

  if (new_period) {
    dec_used_scale_factor_ = dec_target_scale_factor_;
    Beta = ReflBetaNewP;
    BetaC = ReflBetaCompNewP;
  } else {
    Beta = ReflBetaStd;
    BetaC = ReflBetaCompStd;
  }

  /* Calculate new scale factor in Q13 */
  dec_used_scale_factor_ = rtc::checked_cast<int16_t>(
      WEBRTC_SPL_MUL_16_16_RSFT(dec_used_scale_factor_, Beta >> 2, 13) +
      WEBRTC_SPL_MUL_16_16_RSFT(dec_target_scale_factor_, BetaC >> 2, 13));

  dec_used_energy_ = dec_used_energy_ >> 1;
  dec_used_energy_ += dec_target_energy_ >> 1;

  /* Do the same for the reflection coeffs, albeit in Q15. */
  for (size_t i = 0; i < WEBRTC_CNG_MAX_LPC_ORDER; i++) {
    dec_used_reflCoefs_[i] =
        (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(dec_used_reflCoefs_[i], Beta, 15);
    dec_used_reflCoefs_[i] +=
        (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(dec_target_reflCoefs_[i], BetaC, 15);
  }

  /* Compute the polynomial coefficients. */
  WebRtcCng_K2a16(dec_used_reflCoefs_, WEBRTC_CNG_MAX_LPC_ORDER, lpPoly);

  targetEnergy = dec_used_energy_;

  /* Calculate scaling factor based on filter energy. */
  En = 8192; /* 1.0 in Q13. */
  for (size_t i = 0; i < (WEBRTC_CNG_MAX_LPC_ORDER); i++) {
    /* Floating point value for reference.
       E *= 1.0 - (dec_used_reflCoefs_[i] / 32768.0) *
       (dec_used_reflCoefs_[i] / 32768.0);
     */

    /* Same in fixed point. */
    /* K(i).^2 in Q15. */
    temp16 = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(dec_used_reflCoefs_[i],
                                                dec_used_reflCoefs_[i], 15);
    /* 1 - K(i).^2 in Q15. */
    temp16 = 0x7fff - temp16;
    En = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(En, temp16, 15);
  }

  /* float scaling= sqrt(E * dec_target_energy_ / (1 << 24)); */

  /* Calculate sqrt(En * target_energy / excitation energy) */
  targetEnergy = WebRtcSpl_Sqrt(dec_used_energy_);

  En = (int16_t)WebRtcSpl_Sqrt(En) << 6;
  En = (En * 3) >> 1; /* 1.5 estimates sqrt(2). */
  dec_used_scale_factor_ = (int16_t)((En * targetEnergy) >> 12);

  /* Generate excitation. */
  /* Excitation energy per sample is 2.^24 - Q13 N(0,1). */
  for (size_t i = 0; i < num_samples; i++) {
    excitation[i] = WebRtcSpl_RandN(&dec_seed_) >> 1;
  }

  /* Scale to correct energy. */
  WebRtcSpl_ScaleVector(excitation, excitation, dec_used_scale_factor_,
                        num_samples, 13);

  /* |lpPoly| - Coefficients in Q12.
   * |excitation| - Speech samples.
   * |nst->dec_filtstate| - State preservation.
   * |out_data| - Filtered speech samples. */
  WebRtcSpl_FilterAR(lpPoly, WEBRTC_CNG_MAX_LPC_ORDER + 1, excitation,
                     num_samples, dec_filtstate_, WEBRTC_CNG_MAX_LPC_ORDER,
                     dec_filtstateLow_, WEBRTC_CNG_MAX_LPC_ORDER,
                     out_data.data(), low, num_samples);

  return true;
}

ComfortNoiseEncoder::ComfortNoiseEncoder(int fs, int interval, int quality)
    : enc_nrOfCoefs_(quality),
      enc_sampfreq_(fs),
      enc_interval_(interval),
      enc_msSinceSid_(0),
      enc_Energy_(0),
      enc_reflCoefs_{0},
      enc_corrVector_{0},
      enc_seed_(7777) /* For debugging only. */ {
  RTC_CHECK_GT(quality, 0);
  RTC_CHECK_LE(quality, WEBRTC_CNG_MAX_LPC_ORDER);
}

void ComfortNoiseEncoder::Reset(int fs, int interval, int quality) {
  RTC_CHECK_GT(quality, 0);
  RTC_CHECK_LE(quality, WEBRTC_CNG_MAX_LPC_ORDER);
  enc_nrOfCoefs_ = quality;
  enc_sampfreq_ = fs;
  enc_interval_ = interval;
  enc_msSinceSid_ = 0;
  enc_Energy_ = 0;
  for (auto& c : enc_reflCoefs_)
    c = 0;
  for (auto& c : enc_corrVector_)
    c = 0;
  enc_seed_ = 7777; /* For debugging only. */
}

size_t ComfortNoiseEncoder::Encode(rtc::ArrayView<const int16_t> speech,
                                   bool force_sid,
                                   rtc::Buffer* output) {
  int16_t arCoefs[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int32_t corrVector[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t refCs[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t hanningW[kCngMaxOutsizeOrder];
  int16_t ReflBeta = 19661;     /* 0.6 in q15. */
  int16_t ReflBetaComp = 13107; /* 0.4 in q15. */
  int32_t outEnergy;
  int outShifts;
  size_t i;
  int stab;
  int acorrScale;
  size_t index;
  size_t ind, factor;
  int32_t* bptr;
  int32_t blo, bhi;
  int16_t negate;
  const int16_t* aptr;
  int16_t speechBuf[kCngMaxOutsizeOrder];

  const size_t num_samples = speech.size();
  RTC_CHECK_LE(num_samples, kCngMaxOutsizeOrder);

  for (i = 0; i < num_samples; i++) {
    speechBuf[i] = speech[i];
  }

  factor = num_samples;

  /* Calculate energy and a coefficients. */
  outEnergy = WebRtcSpl_Energy(speechBuf, num_samples, &outShifts);
  while (outShifts > 0) {
    /* We can only do 5 shifts without destroying accuracy in
     * division factor. */
    if (outShifts > 5) {
      outEnergy <<= (outShifts - 5);
      outShifts = 5;
    } else {
      factor /= 2;
      outShifts--;
    }
  }
  outEnergy = WebRtcSpl_DivW32W16(outEnergy, (int16_t)factor);

  if (outEnergy > 1) {
    /* Create Hanning Window. */
    WebRtcSpl_GetHanningWindow(hanningW, num_samples / 2);
    for (i = 0; i < (num_samples / 2); i++)
      hanningW[num_samples - i - 1] = hanningW[i];

    WebRtcSpl_ElementwiseVectorMult(speechBuf, hanningW, speechBuf, num_samples,
                                    14);

    WebRtcSpl_AutoCorrelation(speechBuf, num_samples, enc_nrOfCoefs_,
                              corrVector, &acorrScale);

    if (*corrVector == 0)
      *corrVector = WEBRTC_SPL_WORD16_MAX;

    /* Adds the bandwidth expansion. */
    aptr = WebRtcCng_kCorrWindow;
    bptr = corrVector;

    /* (zzz) lpc16_1 = 17+1+820+2+2 = 842 (ordo2=700). */
    for (ind = 0; ind < enc_nrOfCoefs_; ind++) {
      /* The below code multiplies the 16 b corrWindow values (Q15) with
       * the 32 b corrvector (Q0) and shifts the result down 15 steps. */
      negate = *bptr < 0;
      if (negate)
        *bptr = -*bptr;

      blo = (int32_t)*aptr * (*bptr & 0xffff);
      bhi = ((blo >> 16) & 0xffff) +
            ((int32_t)(*aptr++) * ((*bptr >> 16) & 0xffff));
      blo = (blo & 0xffff) | ((bhi & 0xffff) << 16);

      *bptr = (((bhi >> 16) & 0x7fff) << 17) | ((uint32_t)blo >> 15);
      if (negate)
        *bptr = -*bptr;
      bptr++;
    }
    /* End of bandwidth expansion. */

    stab = WebRtcSpl_LevinsonDurbin(corrVector, arCoefs, refCs, enc_nrOfCoefs_);

    if (!stab) {
      /* Disregard from this frame */
      return 0;
    }

  } else {
    for (i = 0; i < enc_nrOfCoefs_; i++)
      refCs[i] = 0;
  }

  if (force_sid) {
    /* Read instantaneous values instead of averaged. */
    for (i = 0; i < enc_nrOfCoefs_; i++)
      enc_reflCoefs_[i] = refCs[i];
    enc_Energy_ = outEnergy;
  } else {
    /* Average history with new values. */
    for (i = 0; i < enc_nrOfCoefs_; i++) {
      enc_reflCoefs_[i] =
          (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(enc_reflCoefs_[i], ReflBeta, 15);
      enc_reflCoefs_[i] +=
          (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(refCs[i], ReflBetaComp, 15);
    }
    enc_Energy_ = (outEnergy >> 2) + (enc_Energy_ >> 1) + (enc_Energy_ >> 2);
  }

  if (enc_Energy_ < 1) {
    enc_Energy_ = 1;
  }

  if ((enc_msSinceSid_ > (enc_interval_ - 1)) || force_sid) {
    /* Search for best dbov value. */
    index = 0;
    for (i = 1; i < 93; i++) {
      /* Always round downwards. */
      if ((enc_Energy_ - WebRtcCng_kDbov[i]) > 0) {
        index = i;
        break;
      }
    }
    if ((i == 93) && (index == 0))
      index = 94;

    const size_t output_coefs = enc_nrOfCoefs_ + 1;
    output->AppendData(output_coefs, [&](rtc::ArrayView<uint8_t> output) {
      output[0] = (uint8_t)index;

      /* Quantize coefficients with tweak for WebRtc implementation of
       * RFC3389. */
      if (enc_nrOfCoefs_ == WEBRTC_CNG_MAX_LPC_ORDER) {
        for (i = 0; i < enc_nrOfCoefs_; i++) {
          /* Q15 to Q7 with rounding. */
          output[i + 1] = ((enc_reflCoefs_[i] + 128) >> 8);
        }
      } else {
        for (i = 0; i < enc_nrOfCoefs_; i++) {
          /* Q15 to Q7 with rounding. */
          output[i + 1] = (127 + ((enc_reflCoefs_[i] + 128) >> 8));
        }
      }

      return output_coefs;
    });

    enc_msSinceSid_ =
        static_cast<int16_t>((1000 * num_samples) / enc_sampfreq_);
    return output_coefs;
  } else {
    enc_msSinceSid_ +=
        static_cast<int16_t>((1000 * num_samples) / enc_sampfreq_);
    return 0;
  }
}

namespace {
/* Values in |k| are Q15, and |a| Q12. */
void WebRtcCng_K2a16(int16_t* k, int useOrder, int16_t* a) {
  int16_t any[WEBRTC_SPL_MAX_LPC_ORDER + 1];
  int16_t* aptr;
  int16_t* aptr2;
  int16_t* anyptr;
  const int16_t* kptr;
  int m, i;

  kptr = k;
  *a = 4096; /* i.e., (Word16_MAX >> 3) + 1 */
  *any = *a;
  a[1] = (*k + 4) >> 3;
  for (m = 1; m < useOrder; m++) {
    kptr++;
    aptr = a;
    aptr++;
    aptr2 = &a[m];
    anyptr = any;
    anyptr++;

    any[m + 1] = (*kptr + 4) >> 3;
    for (i = 0; i < m; i++) {
      *anyptr++ =
          (*aptr++) +
          (int16_t)((((int32_t)(*aptr2--) * (int32_t)*kptr) + 16384) >> 15);
    }

    aptr = a;
    anyptr = any;
    for (i = 0; i < (m + 2); i++) {
      *aptr++ = *anyptr++;
    }
  }
}

}  // namespace

}  // namespace webrtc

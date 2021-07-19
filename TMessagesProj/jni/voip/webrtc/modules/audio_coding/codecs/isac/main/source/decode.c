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
 * decode_B.c
 *
 * This file contains definition of funtions for decoding.
 * Decoding of lower-band, including normal-decoding and RCU decoding.
 * Decoding of upper-band, including 8-12 kHz, when the bandwidth is
 * 0-12 kHz, and 8-16 kHz, when the bandwidth is 0-16 kHz.
 *
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "modules/audio_coding/codecs/isac/main/source/codec.h"
#include "modules/audio_coding/codecs/isac/main/source/entropy_coding.h"
#include "modules/audio_coding/codecs/isac/main/source/pitch_estimator.h"
#include "modules/audio_coding/codecs/isac/main/source/bandwidth_estimator.h"
#include "modules/audio_coding/codecs/isac/main/source/structs.h"
#include "modules/audio_coding/codecs/isac/main/source/settings.h"
#include "modules/audio_coding/codecs/isac/main/source/pitch_filter.h"

/*
 * function to decode the bitstream
 * returns the total number of bytes in the stream
 */
int WebRtcIsac_DecodeLb(const TransformTables* transform_tables,
                        float* signal_out, ISACLBDecStruct* ISACdecLB_obj,
                        int16_t* current_framesamples,
                        int16_t isRCUPayload) {
  int k;
  int len, err;
  int16_t bandwidthInd;

  float LP_dec_float[FRAMESAMPLES_HALF];
  float HP_dec_float[FRAMESAMPLES_HALF];

  double LPw[FRAMESAMPLES_HALF];
  double HPw[FRAMESAMPLES_HALF];
  double LPw_pf[FRAMESAMPLES_HALF];

  double lo_filt_coef[(ORDERLO + 1)*SUBFRAMES];
  double hi_filt_coef[(ORDERHI + 1)*SUBFRAMES];

  double real_f[FRAMESAMPLES_HALF];
  double imag_f[FRAMESAMPLES_HALF];

  double PitchLags[4];
  double PitchGains[4];
  double AvgPitchGain;
  int16_t PitchGains_Q12[4];
  int16_t AvgPitchGain_Q12;

  float gain;

  int frame_nb; /* counter */
  int frame_mode; /* 0 30ms, 1 for 60ms */
  /* Processed_samples: 480 (30, 60 ms). Cannot take other values. */

  WebRtcIsac_ResetBitstream(&(ISACdecLB_obj->bitstr_obj));

  len = 0;

  /* Decode framelength and BW estimation - not used,
     only for stream pointer*/
  err = WebRtcIsac_DecodeFrameLen(&ISACdecLB_obj->bitstr_obj,
                                  current_framesamples);
  if (err < 0) {
    return err;
  }

  /* Frame_mode:
   * 0: indicates 30 ms frame (480 samples)
   * 1: indicates 60 ms frame (960 samples) */
  frame_mode = *current_framesamples / MAX_FRAMESAMPLES;

  err = WebRtcIsac_DecodeSendBW(&ISACdecLB_obj->bitstr_obj, &bandwidthInd);
  if (err < 0) {
    return err;
  }

  /* One loop if it's one frame (20 or 30ms), 2 loops if 2 frames
     bundled together (60ms). */
  for (frame_nb = 0; frame_nb <= frame_mode; frame_nb++) {
    /* Decode & de-quantize pitch parameters */
    err = WebRtcIsac_DecodePitchGain(&ISACdecLB_obj->bitstr_obj,
                                     PitchGains_Q12);
    if (err < 0) {
      return err;
    }

    err = WebRtcIsac_DecodePitchLag(&ISACdecLB_obj->bitstr_obj, PitchGains_Q12,
                                    PitchLags);
    if (err < 0) {
      return err;
    }

    AvgPitchGain_Q12 = (PitchGains_Q12[0] + PitchGains_Q12[1] +
        PitchGains_Q12[2] + PitchGains_Q12[3]) >> 2;

    /* Decode & de-quantize filter coefficients. */
    err = WebRtcIsac_DecodeLpc(&ISACdecLB_obj->bitstr_obj, lo_filt_coef,
                               hi_filt_coef);
    if (err < 0) {
      return err;
    }
    /* Decode & de-quantize spectrum. */
    len = WebRtcIsac_DecodeSpec(&ISACdecLB_obj->bitstr_obj, AvgPitchGain_Q12,
                                kIsacLowerBand, real_f, imag_f);
    if (len < 0) {
      return len;
    }

    /* Inverse transform. */
    WebRtcIsac_Spec2time(transform_tables, real_f, imag_f, LPw, HPw,
                         &ISACdecLB_obj->fftstr_obj);

    /* Convert PitchGains back to float for pitchfilter_post */
    for (k = 0; k < 4; k++) {
      PitchGains[k] = ((float)PitchGains_Q12[k]) / 4096;
    }
    if (isRCUPayload) {
      for (k = 0; k < 240; k++) {
        LPw[k] *= RCU_TRANSCODING_SCALE_INVERSE;
        HPw[k] *= RCU_TRANSCODING_SCALE_INVERSE;
      }
    }

    /* Inverse pitch filter. */
    WebRtcIsac_PitchfilterPost(LPw, LPw_pf, &ISACdecLB_obj->pitchfiltstr_obj,
                               PitchLags, PitchGains);
    /* Convert AvgPitchGain back to float for computation of gain. */
    AvgPitchGain = ((float)AvgPitchGain_Q12) / 4096;
    gain = 1.0f - 0.45f * (float)AvgPitchGain;

    for (k = 0; k < FRAMESAMPLES_HALF; k++) {
      /* Reduce gain to compensate for pitch enhancer. */
      LPw_pf[k] *= gain;
    }

    if (isRCUPayload) {
      for (k = 0; k < FRAMESAMPLES_HALF; k++) {
        /* Compensation for transcoding gain changes. */
        LPw_pf[k] *= RCU_TRANSCODING_SCALE;
        HPw[k] *= RCU_TRANSCODING_SCALE;
      }
    }
    /* Perceptual post-filtering (using normalized lattice filter). */
    WebRtcIsac_NormLatticeFilterAr(
        ORDERLO, ISACdecLB_obj->maskfiltstr_obj.PostStateLoF,
        (ISACdecLB_obj->maskfiltstr_obj).PostStateLoG, LPw_pf, lo_filt_coef,
        LP_dec_float);
    WebRtcIsac_NormLatticeFilterAr(
        ORDERHI, ISACdecLB_obj->maskfiltstr_obj.PostStateHiF,
        (ISACdecLB_obj->maskfiltstr_obj).PostStateHiG, HPw, hi_filt_coef,
        HP_dec_float);

    /* Recombine the 2 bands. */
    WebRtcIsac_FilterAndCombineFloat(LP_dec_float, HP_dec_float,
                                     signal_out + frame_nb * FRAMESAMPLES,
                                     &ISACdecLB_obj->postfiltbankstr_obj);
  }
  return len;
}


/*
 * This decode function is called when the codec is operating in 16 kHz
 * bandwidth to decode the upperband, i.e. 8-16 kHz.
 *
 * Contrary to lower-band, the upper-band (8-16 kHz) is not split in
 * frequency, but split to 12 sub-frames, i.e. twice as lower-band.
 */
int WebRtcIsac_DecodeUb16(const TransformTables* transform_tables,
                          float* signal_out, ISACUBDecStruct* ISACdecUB_obj,
                          int16_t isRCUPayload) {
  int len, err;

  double halfFrameFirst[FRAMESAMPLES_HALF];
  double halfFrameSecond[FRAMESAMPLES_HALF];

  double percepFilterParam[(UB_LPC_ORDER + 1) * (SUBFRAMES << 1) +
                           (UB_LPC_ORDER + 1)];

  double real_f[FRAMESAMPLES_HALF];
  double imag_f[FRAMESAMPLES_HALF];
  const int16_t kAveragePitchGain = 0; /* No pitch-gain for upper-band. */
  len = 0;

  /* Decode & de-quantize filter coefficients. */
  memset(percepFilterParam, 0, sizeof(percepFilterParam));
  err = WebRtcIsac_DecodeInterpolLpcUb(&ISACdecUB_obj->bitstr_obj,
                                       percepFilterParam, isac16kHz);
  if (err < 0) {
    return err;
  }

  /* Decode & de-quantize spectrum. */
  len = WebRtcIsac_DecodeSpec(&ISACdecUB_obj->bitstr_obj, kAveragePitchGain,
                              kIsacUpperBand16, real_f, imag_f);
  if (len < 0) {
    return len;
  }
  if (isRCUPayload) {
    int n;
    for (n = 0; n < 240; n++) {
      real_f[n] *= RCU_TRANSCODING_SCALE_UB_INVERSE;
      imag_f[n] *= RCU_TRANSCODING_SCALE_UB_INVERSE;
    }
  }
  /* Inverse transform. */
  WebRtcIsac_Spec2time(transform_tables,
                       real_f, imag_f, halfFrameFirst, halfFrameSecond,
                       &ISACdecUB_obj->fftstr_obj);

  /* Perceptual post-filtering (using normalized lattice filter). */
  WebRtcIsac_NormLatticeFilterAr(
      UB_LPC_ORDER, ISACdecUB_obj->maskfiltstr_obj.PostStateLoF,
      (ISACdecUB_obj->maskfiltstr_obj).PostStateLoG, halfFrameFirst,
      &percepFilterParam[(UB_LPC_ORDER + 1)], signal_out);

  WebRtcIsac_NormLatticeFilterAr(
      UB_LPC_ORDER, ISACdecUB_obj->maskfiltstr_obj.PostStateLoF,
      (ISACdecUB_obj->maskfiltstr_obj).PostStateLoG, halfFrameSecond,
      &percepFilterParam[(UB_LPC_ORDER + 1) * SUBFRAMES + (UB_LPC_ORDER + 1)],
      &signal_out[FRAMESAMPLES_HALF]);

  return len;
}

/*
 * This decode function is called when the codec operates at 0-12 kHz
 * bandwidth to decode the upperband, i.e. 8-12 kHz.
 *
 * At the encoder the upper-band is split into two band, 8-12 kHz & 12-16
 * kHz, and only 8-12 kHz is encoded. At the decoder, 8-12 kHz band is
 * reconstructed and 12-16 kHz replaced with zeros. Then two bands
 * are combined, to reconstruct the upperband 8-16 kHz.
 */
int WebRtcIsac_DecodeUb12(const TransformTables* transform_tables,
                          float* signal_out, ISACUBDecStruct* ISACdecUB_obj,
                          int16_t isRCUPayload) {
  int len, err;

  float LP_dec_float[FRAMESAMPLES_HALF];
  float HP_dec_float[FRAMESAMPLES_HALF];

  double LPw[FRAMESAMPLES_HALF];
  double HPw[FRAMESAMPLES_HALF];

  double percepFilterParam[(UB_LPC_ORDER + 1)*SUBFRAMES];

  double real_f[FRAMESAMPLES_HALF];
  double imag_f[FRAMESAMPLES_HALF];
  const int16_t kAveragePitchGain = 0; /* No pitch-gain for upper-band. */
  len = 0;

  /* Decode & dequantize filter coefficients. */
  err = WebRtcIsac_DecodeInterpolLpcUb(&ISACdecUB_obj->bitstr_obj,
                                       percepFilterParam, isac12kHz);
  if (err < 0) {
    return err;
  }

  /* Decode & de-quantize spectrum. */
  len = WebRtcIsac_DecodeSpec(&ISACdecUB_obj->bitstr_obj, kAveragePitchGain,
                              kIsacUpperBand12, real_f, imag_f);
  if (len < 0) {
    return len;
  }

  if (isRCUPayload) {
    int n;
    for (n = 0; n < 240; n++) {
      real_f[n] *= RCU_TRANSCODING_SCALE_UB_INVERSE;
      imag_f[n] *= RCU_TRANSCODING_SCALE_UB_INVERSE;
    }
  }
  /* Inverse transform. */
  WebRtcIsac_Spec2time(transform_tables,
                       real_f, imag_f, LPw, HPw, &ISACdecUB_obj->fftstr_obj);
  /* perceptual post-filtering (using normalized lattice filter) */
  WebRtcIsac_NormLatticeFilterAr(UB_LPC_ORDER,
                                 ISACdecUB_obj->maskfiltstr_obj.PostStateLoF,
                                 (ISACdecUB_obj->maskfiltstr_obj).PostStateLoG,
                                 LPw, percepFilterParam, LP_dec_float);
  /* Zero for 12-16 kHz. */
  memset(HP_dec_float, 0, sizeof(float) * (FRAMESAMPLES_HALF));
  /* Recombine the 2 bands. */
  WebRtcIsac_FilterAndCombineFloat(HP_dec_float, LP_dec_float, signal_out,
                                   &ISACdecUB_obj->postfiltbankstr_obj);
  return len;
}

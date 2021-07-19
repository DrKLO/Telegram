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
 * filterbanks.c
 *
 * This file contains function WebRtcIsac_AllPassFilter2Float,
 * WebRtcIsac_SplitAndFilter, and WebRtcIsac_FilterAndCombine
 * which implement filterbanks that produce decimated lowpass and
 * highpass versions of a signal, and performs reconstruction.
 *
 */

#include "modules/audio_coding/codecs/isac/main/source/settings.h"
#include "modules/audio_coding/codecs/isac/main/source/codec.h"
#include "modules/audio_coding/codecs/isac/main/source/isac_vad.h"

/* Combining */

/* HPstcoeff_out_1 = {a1, a2, b1 - b0 * a1, b2 - b0 * a2}; */
static const float kHpStCoefOut1Float[4] =
{-1.99701049409000f, 0.99714204490000f, 0.01701049409000f, -0.01704204490000f};

/* HPstcoeff_out_2 = {a1, a2, b1 - b0 * a1, b2 - b0 * a2}; */
static const float kHpStCoefOut2Float[4] =
{-1.98645294509837f, 0.98672435560000f, 0.00645294509837f, -0.00662435560000f};


/* Function WebRtcIsac_FilterAndCombine */
/* This is a decoder function that takes the decimated
   length FRAMESAMPLES_HALF input low-pass and
   high-pass signals and creates a reconstructed fullband
   output signal of length FRAMESAMPLES. WebRtcIsac_FilterAndCombine
   is the sibling function of WebRtcIsac_SplitAndFilter */
/* INPUTS:
   inLP: a length FRAMESAMPLES_HALF array of input low-pass
   samples.
   inHP: a length FRAMESAMPLES_HALF array of input high-pass
   samples.
   postfiltdata: input data structure containing the filterbank
   states from the previous decoding iteration.
   OUTPUTS:
   Out: a length FRAMESAMPLES array of output reconstructed
   samples (fullband) based on the input low-pass and
   high-pass signals.
   postfiltdata: the input data structure containing the filterbank
   states is updated for the next decoding iteration */
void WebRtcIsac_FilterAndCombineFloat(float *InLP,
                                      float *InHP,
                                      float *Out,
                                      PostFiltBankstr *postfiltdata)
{
  int k;
  float tempin_ch1[FRAMESAMPLES+MAX_AR_MODEL_ORDER];
  float tempin_ch2[FRAMESAMPLES+MAX_AR_MODEL_ORDER];
  float ftmp, ftmp2;

  /* Form the polyphase signals*/
  for (k=0;k<FRAMESAMPLES_HALF;k++) {
    tempin_ch1[k]=InLP[k]+InHP[k]; /* Construct a new upper channel signal*/
    tempin_ch2[k]=InLP[k]-InHP[k]; /* Construct a new lower channel signal*/
  }


  /* all-pass filter the new upper channel signal. HOWEVER, use the all-pass filter factors
     that were used as a lower channel at the encoding side.  So at the decoder, the
     corresponding all-pass filter factors for each channel are swapped.*/
  WebRtcIsac_AllPassFilter2Float(tempin_ch1, WebRtcIsac_kLowerApFactorsFloat,
                                 FRAMESAMPLES_HALF, NUMBEROFCHANNELAPSECTIONS,postfiltdata->STATE_0_UPPER_float);

  /* Now, all-pass filter the new lower channel signal. But since all-pass filter factors
     at the decoder are swapped from the ones at the encoder, the 'upper' channel
     all-pass filter factors (WebRtcIsac_kUpperApFactorsFloat) are used to filter this new
     lower channel signal */
  WebRtcIsac_AllPassFilter2Float(tempin_ch2, WebRtcIsac_kUpperApFactorsFloat,
                                 FRAMESAMPLES_HALF, NUMBEROFCHANNELAPSECTIONS,postfiltdata->STATE_0_LOWER_float);


  /* Merge outputs to form the full length output signal.*/
  for (k=0;k<FRAMESAMPLES_HALF;k++) {
    Out[2*k]=tempin_ch2[k];
    Out[2*k+1]=tempin_ch1[k];
  }


  /* High pass filter */

  for (k=0;k<FRAMESAMPLES;k++) {
    ftmp2 = Out[k] + kHpStCoefOut1Float[2] * postfiltdata->HPstates1_float[0] +
        kHpStCoefOut1Float[3] * postfiltdata->HPstates1_float[1];
    ftmp = Out[k] - kHpStCoefOut1Float[0] * postfiltdata->HPstates1_float[0] -
        kHpStCoefOut1Float[1] * postfiltdata->HPstates1_float[1];
    postfiltdata->HPstates1_float[1] = postfiltdata->HPstates1_float[0];
    postfiltdata->HPstates1_float[0] = ftmp;
    Out[k] = ftmp2;
  }

  for (k=0;k<FRAMESAMPLES;k++) {
    ftmp2 = Out[k] + kHpStCoefOut2Float[2] * postfiltdata->HPstates2_float[0] +
        kHpStCoefOut2Float[3] * postfiltdata->HPstates2_float[1];
    ftmp = Out[k] - kHpStCoefOut2Float[0] * postfiltdata->HPstates2_float[0] -
        kHpStCoefOut2Float[1] * postfiltdata->HPstates2_float[1];
    postfiltdata->HPstates2_float[1] = postfiltdata->HPstates2_float[0];
    postfiltdata->HPstates2_float[0] = ftmp;
    Out[k] = ftmp2;
  }
}

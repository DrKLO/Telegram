/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory.h>
#include <string.h>
#ifdef WEBRTC_ANDROID
#include <stdlib.h>
#endif

#include "modules/audio_coding/codecs/isac/main/source/pitch_estimator.h"
#include "modules/audio_coding/codecs/isac/main/source/isac_vad.h"

static void WebRtcIsac_AllPoleFilter(double* InOut,
                                     double* Coef,
                                     size_t lengthInOut,
                                     int orderCoef) {
  /* the state of filter is assumed to be in InOut[-1] to InOut[-orderCoef] */
  double scal;
  double sum;
  size_t n;
  int k;

  //if (fabs(Coef[0]-1.0)<0.001) {
  if ( (Coef[0] > 0.9999) && (Coef[0] < 1.0001) )
  {
    for(n = 0; n < lengthInOut; n++)
    {
      sum = Coef[1] * InOut[-1];
      for(k = 2; k <= orderCoef; k++){
        sum += Coef[k] * InOut[-k];
      }
      *InOut++ -= sum;
    }
  }
  else
  {
    scal = 1.0 / Coef[0];
    for(n=0;n<lengthInOut;n++)
    {
      *InOut *= scal;
      for(k=1;k<=orderCoef;k++){
        *InOut -= scal*Coef[k]*InOut[-k];
      }
      InOut++;
    }
  }
}

static void WebRtcIsac_AllZeroFilter(double* In,
                                     double* Coef,
                                     size_t lengthInOut,
                                     int orderCoef,
                                     double* Out) {
  /* the state of filter is assumed to be in In[-1] to In[-orderCoef] */

  size_t n;
  int k;
  double tmp;

  for(n = 0; n < lengthInOut; n++)
  {
    tmp = In[0] * Coef[0];

    for(k = 1; k <= orderCoef; k++){
      tmp += Coef[k] * In[-k];
    }

    *Out++ = tmp;
    In++;
  }
}

static void WebRtcIsac_ZeroPoleFilter(double* In,
                                      double* ZeroCoef,
                                      double* PoleCoef,
                                      size_t lengthInOut,
                                      int orderCoef,
                                      double* Out) {
  /* the state of the zero section is assumed to be in In[-1] to In[-orderCoef] */
  /* the state of the pole section is assumed to be in Out[-1] to Out[-orderCoef] */

  WebRtcIsac_AllZeroFilter(In,ZeroCoef,lengthInOut,orderCoef,Out);
  WebRtcIsac_AllPoleFilter(Out,PoleCoef,lengthInOut,orderCoef);
}


void WebRtcIsac_AutoCorr(double* r, const double* x, size_t N, size_t order) {
  size_t  lag, n;
  double sum, prod;
  const double *x_lag;

  for (lag = 0; lag <= order; lag++)
  {
    sum = 0.0f;
    x_lag = &x[lag];
    prod = x[0] * x_lag[0];
    for (n = 1; n < N - lag; n++) {
      sum += prod;
      prod = x[n] * x_lag[n];
    }
    sum += prod;
    r[lag] = sum;
  }

}

static void WebRtcIsac_BwExpand(double* out,
                                double* in,
                                double coef,
                                size_t length) {
  size_t i;
  double  chirp;

  chirp = coef;

  out[0] = in[0];
  for (i = 1; i < length; i++) {
    out[i] = chirp * in[i];
    chirp *= coef;
  }
}

void WebRtcIsac_WeightingFilter(const double* in,
                                double* weiout,
                                double* whiout,
                                WeightFiltstr* wfdata) {
  double  tmpbuffer[PITCH_FRAME_LEN + PITCH_WLPCBUFLEN];
  double  corr[PITCH_WLPCORDER+1], rc[PITCH_WLPCORDER+1];
  double apol[PITCH_WLPCORDER+1], apolr[PITCH_WLPCORDER+1];
  double  rho=0.9, *inp, *dp, *dp2;
  double  whoutbuf[PITCH_WLPCBUFLEN + PITCH_WLPCORDER];
  double  weoutbuf[PITCH_WLPCBUFLEN + PITCH_WLPCORDER];
  double  *weo, *who, opol[PITCH_WLPCORDER+1], ext[PITCH_WLPCWINLEN];
  int     k, n, endpos, start;

  /* Set up buffer and states */
  memcpy(tmpbuffer, wfdata->buffer, sizeof(double) * PITCH_WLPCBUFLEN);
  memcpy(tmpbuffer+PITCH_WLPCBUFLEN, in, sizeof(double) * PITCH_FRAME_LEN);
  memcpy(wfdata->buffer, tmpbuffer+PITCH_FRAME_LEN, sizeof(double) * PITCH_WLPCBUFLEN);

  dp=weoutbuf;
  dp2=whoutbuf;
  for (k=0;k<PITCH_WLPCORDER;k++) {
    *dp++ = wfdata->weostate[k];
    *dp2++ = wfdata->whostate[k];
    opol[k]=0.0;
  }
  opol[0]=1.0;
  opol[PITCH_WLPCORDER]=0.0;
  weo=dp;
  who=dp2;

  endpos=PITCH_WLPCBUFLEN + PITCH_SUBFRAME_LEN;
  inp=tmpbuffer + PITCH_WLPCBUFLEN;

  for (n=0; n<PITCH_SUBFRAMES; n++) {
    /* Windowing */
    start=endpos-PITCH_WLPCWINLEN;
    for (k=0; k<PITCH_WLPCWINLEN; k++) {
      ext[k]=wfdata->window[k]*tmpbuffer[start+k];
    }

    /* Get LPC polynomial */
    WebRtcIsac_AutoCorr(corr, ext, PITCH_WLPCWINLEN, PITCH_WLPCORDER);
    corr[0]=1.01*corr[0]+1.0; /* White noise correction */
    WebRtcIsac_LevDurb(apol, rc, corr, PITCH_WLPCORDER);
    WebRtcIsac_BwExpand(apolr, apol, rho, PITCH_WLPCORDER+1);

    /* Filtering */
    WebRtcIsac_ZeroPoleFilter(inp, apol, apolr, PITCH_SUBFRAME_LEN, PITCH_WLPCORDER, weo);
    WebRtcIsac_ZeroPoleFilter(inp, apolr, opol, PITCH_SUBFRAME_LEN, PITCH_WLPCORDER, who);

    inp+=PITCH_SUBFRAME_LEN;
    endpos+=PITCH_SUBFRAME_LEN;
    weo+=PITCH_SUBFRAME_LEN;
    who+=PITCH_SUBFRAME_LEN;
  }

  /* Export filter states */
  for (k=0;k<PITCH_WLPCORDER;k++) {
    wfdata->weostate[k]=weoutbuf[PITCH_FRAME_LEN+k];
    wfdata->whostate[k]=whoutbuf[PITCH_FRAME_LEN+k];
  }

  /* Export output data */
  memcpy(weiout, weoutbuf+PITCH_WLPCORDER, sizeof(double) * PITCH_FRAME_LEN);
  memcpy(whiout, whoutbuf+PITCH_WLPCORDER, sizeof(double) * PITCH_FRAME_LEN);
}

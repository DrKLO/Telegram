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
 * lattice.c
 *
 * contains the normalized lattice filter routines (MA and AR) for iSAC codec
 *
 */

#include <math.h>
#include <memory.h>
#include <string.h>
#ifdef WEBRTC_ANDROID
#include <stdlib.h>
#endif

#include "modules/audio_coding/codecs/isac/main/source/settings.h"
#include "modules/audio_coding/codecs/isac/main/source/codec.h"

/* filter the signal using normalized lattice filter */
/* MA filter */
void WebRtcIsac_NormLatticeFilterMa(int orderCoef,
                                     float *stateF,
                                     float *stateG,
                                     float *lat_in,
                                     double *filtcoeflo,
                                     double *lat_out)
{
  int n,k,i,u,temp1;
  int ord_1 = orderCoef+1;
  float sth[MAX_AR_MODEL_ORDER];
  float cth[MAX_AR_MODEL_ORDER];
  float inv_cth[MAX_AR_MODEL_ORDER];
  double a[MAX_AR_MODEL_ORDER+1];
  float f[MAX_AR_MODEL_ORDER+1][HALF_SUBFRAMELEN], g[MAX_AR_MODEL_ORDER+1][HALF_SUBFRAMELEN];
  float gain1;

  for (u=0;u<SUBFRAMES;u++)
  {
    /* set the Direct Form coefficients */
    temp1 = u*ord_1;
    a[0] = 1;
    memcpy(a+1, filtcoeflo+temp1+1, sizeof(double) * (ord_1-1));

    /* compute lattice filter coefficients */
    WebRtcIsac_Dir2Lat(a,orderCoef,sth,cth);

    /* compute the gain */
    gain1 = (float)filtcoeflo[temp1];
    for (k=0;k<orderCoef;k++)
    {
      gain1 *= cth[k];
      inv_cth[k] = 1/cth[k];
    }

    /* normalized lattice filter */
    /*****************************/

    /* initial conditions */
    for (i=0;i<HALF_SUBFRAMELEN;i++)
    {
      f[0][i] = lat_in[i + u * HALF_SUBFRAMELEN];
      g[0][i] = lat_in[i + u * HALF_SUBFRAMELEN];
    }

    /* get the state of f&g for the first input, for all orders */
    for (i=1;i<ord_1;i++)
    {
      f[i][0] = inv_cth[i-1]*(f[i-1][0] + sth[i-1]*stateG[i-1]);
      g[i][0] = cth[i-1]*stateG[i-1] + sth[i-1]* f[i][0];
    }

    /* filtering */
    for(k=0;k<orderCoef;k++)
    {
      for(n=0;n<(HALF_SUBFRAMELEN-1);n++)
      {
        f[k+1][n+1] = inv_cth[k]*(f[k][n+1] + sth[k]*g[k][n]);
        g[k+1][n+1] = cth[k]*g[k][n] + sth[k]* f[k+1][n+1];
      }
    }

    for(n=0;n<HALF_SUBFRAMELEN;n++)
    {
      lat_out[n + u * HALF_SUBFRAMELEN] = gain1 * f[orderCoef][n];
    }

    /* save the states */
    for (i=0;i<ord_1;i++)
    {
      stateF[i] = f[i][HALF_SUBFRAMELEN-1];
      stateG[i] = g[i][HALF_SUBFRAMELEN-1];
    }
    /* process next frame */
  }

  return;
}


/*///////////////////AR filter ///////////////////////////////*/
/* filter the signal using normalized lattice filter */
void WebRtcIsac_NormLatticeFilterAr(int orderCoef,
                                     float *stateF,
                                     float *stateG,
                                     double *lat_in,
                                     double *lo_filt_coef,
                                     float *lat_out)
{
  int n,k,i,u,temp1;
  int ord_1 = orderCoef+1;
  float sth[MAX_AR_MODEL_ORDER];
  float cth[MAX_AR_MODEL_ORDER];
  double a[MAX_AR_MODEL_ORDER+1];
  float ARf[MAX_AR_MODEL_ORDER+1][HALF_SUBFRAMELEN], ARg[MAX_AR_MODEL_ORDER+1][HALF_SUBFRAMELEN];
  float gain1,inv_gain1;

  for (u=0;u<SUBFRAMES;u++)
  {
    /* set the denominator and numerator of the Direct Form */
    temp1 = u*ord_1;
    a[0] = 1;

    memcpy(a+1, lo_filt_coef+temp1+1, sizeof(double) * (ord_1-1));

    WebRtcIsac_Dir2Lat(a,orderCoef,sth,cth);

    gain1 = (float)lo_filt_coef[temp1];
    for (k=0;k<orderCoef;k++)
    {
      gain1 = cth[k]*gain1;
    }

    /* initial conditions */
    inv_gain1 = 1/gain1;
    for (i=0;i<HALF_SUBFRAMELEN;i++)
    {
      ARf[orderCoef][i] = (float)lat_in[i + u * HALF_SUBFRAMELEN]*inv_gain1;
    }


    for (i=orderCoef-1;i>=0;i--) //get the state of f&g for the first input, for all orders
    {
      ARf[i][0] = cth[i]*ARf[i+1][0] - sth[i]*stateG[i];
      ARg[i+1][0] = sth[i]*ARf[i+1][0] + cth[i]* stateG[i];
    }
    ARg[0][0] = ARf[0][0];

    for(n=0;n<(HALF_SUBFRAMELEN-1);n++)
    {
      for(k=orderCoef-1;k>=0;k--)
      {
        ARf[k][n+1] = cth[k]*ARf[k+1][n+1] - sth[k]*ARg[k][n];
        ARg[k+1][n+1] = sth[k]*ARf[k+1][n+1] + cth[k]* ARg[k][n];
      }
      ARg[0][n+1] = ARf[0][n+1];
    }

    memcpy(lat_out+u * HALF_SUBFRAMELEN, &(ARf[0][0]), sizeof(float) * HALF_SUBFRAMELEN);

    /* cannot use memcpy in the following */
    for (i=0;i<ord_1;i++)
    {
      stateF[i] = ARf[i][HALF_SUBFRAMELEN-1];
      stateG[i] = ARg[i][HALF_SUBFRAMELEN-1];
    }

  }

  return;
}


/* compute the reflection coefficients using the step-down procedure*/
/* converts the direct form parameters to lattice form.*/
/* a and b are vectors which contain the direct form coefficients,
   according to
   A(z) = a(1) + a(2)*z + a(3)*z^2 + ... + a(M+1)*z^M
   B(z) = b(1) + b(2)*z + b(3)*z^2 + ... + b(M+1)*z^M
*/

void WebRtcIsac_Dir2Lat(double *a,
                        int orderCoef,
                        float *sth,
                        float *cth)
{
  int m, k;
  float tmp[MAX_AR_MODEL_ORDER];
  float tmp_inv, cth2;

  sth[orderCoef-1] = (float)a[orderCoef];
  cth2 = 1.0f - sth[orderCoef-1] * sth[orderCoef-1];
  cth[orderCoef-1] = (float)sqrt(cth2);
  for (m=orderCoef-1; m>0; m--)
  {
    tmp_inv = 1.0f / cth2;
    for (k=1; k<=m; k++)
    {
      tmp[k] = ((float)a[k] - sth[m] * (float)a[m-k+1]) * tmp_inv;
    }

    for (k=1; k<m; k++)
    {
      a[k] = tmp[k];
    }

    sth[m-1] = tmp[m];
    cth2 = 1 - sth[m-1] * sth[m-1];
    cth[m-1] = (float)sqrt(cth2);
  }
}

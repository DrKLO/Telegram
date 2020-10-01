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
 * code_LPC_UB.c
 *
 * This file contains definition of functions used to
 * encode LPC parameters (Shape & gain) of the upper band.
 *
 */

#include <math.h>
#include <stdio.h>
#include <string.h>

#include "modules/audio_coding/codecs/isac/main/source/encode_lpc_swb.h"
#include "modules/audio_coding/codecs/isac/main/source/lpc_gain_swb_tables.h"
#include "modules/audio_coding/codecs/isac/main/source/lpc_shape_swb12_tables.h"
#include "modules/audio_coding/codecs/isac/main/source/lpc_shape_swb16_tables.h"
#include "modules/audio_coding/codecs/isac/main/source/settings.h"

/******************************************************************************
 * WebRtcIsac_RemoveLarMean()
 *
 * Remove the means from LAR coefficients.
 *
 * Input:
 *      -lar                : pointer to lar vectors. LAR vectors are
 *                            concatenated.
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -lar                : pointer to mean-removed LAR:s.
 *
 *
 */
int16_t
WebRtcIsac_RemoveLarMean(
    double* lar,
    int16_t bandwidth)
{
  int16_t coeffCntr;
  int16_t vecCntr;
  int16_t numVec;
  const double* meanLAR;
  switch(bandwidth)
  {
    case isac12kHz:
      {
        numVec = UB_LPC_VEC_PER_FRAME;
        meanLAR = WebRtcIsac_kMeanLarUb12;
        break;
      }
    case isac16kHz:
      {
        numVec = UB16_LPC_VEC_PER_FRAME;
        meanLAR = WebRtcIsac_kMeanLarUb16;
        break;
      }
    default:
      return -1;
  }

  for(vecCntr = 0; vecCntr < numVec; vecCntr++)
  {
    for(coeffCntr = 0; coeffCntr < UB_LPC_ORDER; coeffCntr++)
    {
      // REMOVE MEAN
      *lar++ -= meanLAR[coeffCntr];
    }
  }
  return 0;
}

/******************************************************************************
 * WebRtcIsac_DecorrelateIntraVec()
 *
 * Remove the correlation amonge the components of LAR vectors. If LAR vectors
 * of one frame are put in a matrix where each column is a LAR vector of a
 * sub-frame, then this is equivalent to multiplying the LAR matrix with
 * a decorrelting mtrix from left.
 *
 * Input:
 *      -inLar              : pointer to mean-removed LAR vecrtors.
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -out                : decorrelated LAR vectors.
 */
int16_t
WebRtcIsac_DecorrelateIntraVec(
    const double* data,
    double*       out,
    int16_t bandwidth)
{
  const double* ptrData;
  const double* ptrRow;
  int16_t rowCntr;
  int16_t colCntr;
  int16_t larVecCntr;
  int16_t numVec;
  const double* decorrMat;
  switch(bandwidth)
  {
    case isac12kHz:
      {
        decorrMat = &WebRtcIsac_kIntraVecDecorrMatUb12[0][0];
        numVec = UB_LPC_VEC_PER_FRAME;
        break;
      }
    case isac16kHz:
      {
        decorrMat = &WebRtcIsac_kIintraVecDecorrMatUb16[0][0];
        numVec = UB16_LPC_VEC_PER_FRAME;
        break;
      }
    default:
      return -1;
  }

  //
  // decorrMat * data
  //
  // data is assumed to contain 'numVec' of LAR
  // vectors (mean removed) each of dimension 'UB_LPC_ORDER'
  // concatenated one after the other.
  //

  ptrData = data;
  for(larVecCntr = 0; larVecCntr < numVec; larVecCntr++)
  {
    for(rowCntr = 0; rowCntr < UB_LPC_ORDER; rowCntr++)
    {
      ptrRow = &decorrMat[rowCntr * UB_LPC_ORDER];
      *out = 0;
      for(colCntr = 0; colCntr < UB_LPC_ORDER; colCntr++)
      {
        *out += ptrData[colCntr] * ptrRow[colCntr];
      }
      out++;
    }
    ptrData += UB_LPC_ORDER;
  }
  return 0;
}

/******************************************************************************
 * WebRtcIsac_DecorrelateInterVec()
 *
 * Remover the correlation among mean-removed LAR vectors. If LAR vectors
 * of one frame are put in a matrix where each column is a LAR vector of a
 * sub-frame, then this is equivalent to multiplying the LAR matrix with
 * a decorrelting mtrix from right.
 *
 * Input:
 *      -data               : pointer to matrix of LAR vectors. The matrix
 *                            is stored column-wise.
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -out                : decorrelated LAR vectors.
 */
int16_t
WebRtcIsac_DecorrelateInterVec(
    const double* data,
    double* out,
    int16_t bandwidth)
{
  int16_t coeffCntr;
  int16_t rowCntr;
  int16_t colCntr;
  const double* decorrMat;
  int16_t interVecDim;

  switch(bandwidth)
  {
    case isac12kHz:
      {
        decorrMat = &WebRtcIsac_kInterVecDecorrMatUb12[0][0];
        interVecDim = UB_LPC_VEC_PER_FRAME;
        break;
      }
    case isac16kHz:
      {
        decorrMat = &WebRtcIsac_kInterVecDecorrMatUb16[0][0];
        interVecDim = UB16_LPC_VEC_PER_FRAME;
        break;
      }
    default:
      return -1;
  }

  //
  // data * decorrMat
  //
  // data is of size 'interVecDim' * 'UB_LPC_ORDER'
  // That is 'interVecDim' of LAR vectors (mean removed)
  // in columns each of dimension 'UB_LPC_ORDER'.
  // matrix is stored column-wise.
  //

  for(coeffCntr = 0; coeffCntr < UB_LPC_ORDER; coeffCntr++)
  {
    for(colCntr = 0; colCntr < interVecDim; colCntr++)
    {
      out[coeffCntr + colCntr * UB_LPC_ORDER] = 0;
      for(rowCntr = 0; rowCntr < interVecDim; rowCntr++)
      {
        out[coeffCntr + colCntr * UB_LPC_ORDER] +=
            data[coeffCntr + rowCntr * UB_LPC_ORDER] *
            decorrMat[rowCntr * interVecDim + colCntr];
      }
    }
  }
  return 0;
}

/******************************************************************************
 * WebRtcIsac_QuantizeUncorrLar()
 *
 * Quantize the uncorrelated parameters.
 *
 * Input:
 *      -data               : uncorrelated LAR vectors.
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -data               : quantized version of the input.
 *      -idx                : pointer to quantization indices.
 */
double
WebRtcIsac_QuantizeUncorrLar(
    double* data,
    int* recIdx,
    int16_t bandwidth)
{
  int16_t cntr;
  int32_t idx;
  int16_t interVecDim;
  const double* leftRecPoint;
  double quantizationStepSize;
  const int16_t* numQuantCell;
  switch(bandwidth)
  {
    case isac12kHz:
      {
        leftRecPoint         = WebRtcIsac_kLpcShapeLeftRecPointUb12;
        quantizationStepSize = WebRtcIsac_kLpcShapeQStepSizeUb12;
        numQuantCell         = WebRtcIsac_kLpcShapeNumRecPointUb12;
        interVecDim          = UB_LPC_VEC_PER_FRAME;
        break;
      }
    case isac16kHz:
      {
        leftRecPoint         = WebRtcIsac_kLpcShapeLeftRecPointUb16;
        quantizationStepSize = WebRtcIsac_kLpcShapeQStepSizeUb16;
        numQuantCell         = WebRtcIsac_kLpcShapeNumRecPointUb16;
        interVecDim          = UB16_LPC_VEC_PER_FRAME;
        break;
      }
    default:
      return -1;
  }

  //
  // Quantize the parametrs.
  //
  for(cntr = 0; cntr < UB_LPC_ORDER * interVecDim; cntr++)
  {
    idx = (int32_t)floor((*data - leftRecPoint[cntr]) /
                               quantizationStepSize + 0.5);
    if(idx < 0)
    {
      idx = 0;
    }
    else if(idx >= numQuantCell[cntr])
    {
      idx = numQuantCell[cntr] - 1;
    }

    *data++ = leftRecPoint[cntr] + idx * quantizationStepSize;
    *recIdx++ = idx;
  }
  return 0;
}


/******************************************************************************
 * WebRtcIsac_DequantizeLpcParam()
 *
 * Get the quantized value of uncorrelated LARs given the quantization indices.
 *
 * Input:
 *      -idx                : pointer to quantiztion indices.
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -out                : pointer to quantized values.
 */
int16_t
WebRtcIsac_DequantizeLpcParam(
    const int* idx,
    double*    out,
    int16_t bandwidth)
{
  int16_t cntr;
  int16_t interVecDim;
  const double* leftRecPoint;
  double quantizationStepSize;

  switch(bandwidth)
  {
    case isac12kHz:
      {
        leftRecPoint =         WebRtcIsac_kLpcShapeLeftRecPointUb12;
        quantizationStepSize = WebRtcIsac_kLpcShapeQStepSizeUb12;
        interVecDim =          UB_LPC_VEC_PER_FRAME;
        break;
      }
    case isac16kHz:
      {
        leftRecPoint =         WebRtcIsac_kLpcShapeLeftRecPointUb16;
        quantizationStepSize = WebRtcIsac_kLpcShapeQStepSizeUb16;
        interVecDim =          UB16_LPC_VEC_PER_FRAME;
        break;
      }
    default:
      return -1;
  }

  //
  // Dequantize given the quantization indices
  //

  for(cntr = 0; cntr < UB_LPC_ORDER * interVecDim; cntr++)
  {
    *out++ = leftRecPoint[cntr] + *idx++ * quantizationStepSize;
  }
  return 0;
}


/******************************************************************************
 * WebRtcIsac_CorrelateIntraVec()
 *
 * This is the inverse of WebRtcIsac_DecorrelateIntraVec().
 *
 * Input:
 *      -data               : uncorrelated parameters.
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -out                : correlated parametrs.
 */
int16_t
WebRtcIsac_CorrelateIntraVec(
    const double* data,
    double*       out,
    int16_t bandwidth)
{
  int16_t vecCntr;
  int16_t rowCntr;
  int16_t colCntr;
  int16_t numVec;
  const double* ptrData;
  const double* intraVecDecorrMat;

  switch(bandwidth)
  {
    case isac12kHz:
      {
        numVec            = UB_LPC_VEC_PER_FRAME;
        intraVecDecorrMat = &WebRtcIsac_kIntraVecDecorrMatUb12[0][0];
        break;
      }
    case isac16kHz:
      {
        numVec            = UB16_LPC_VEC_PER_FRAME;
        intraVecDecorrMat = &WebRtcIsac_kIintraVecDecorrMatUb16[0][0];
        break;
      }
    default:
      return -1;
  }


  ptrData = data;
  for(vecCntr = 0; vecCntr < numVec; vecCntr++)
  {
    for(colCntr = 0; colCntr < UB_LPC_ORDER; colCntr++)
    {
      *out = 0;
      for(rowCntr = 0; rowCntr < UB_LPC_ORDER; rowCntr++)
      {
        *out += ptrData[rowCntr] *
            intraVecDecorrMat[rowCntr * UB_LPC_ORDER + colCntr];
      }
      out++;
    }
    ptrData += UB_LPC_ORDER;
  }
  return 0;
}

/******************************************************************************
 * WebRtcIsac_CorrelateInterVec()
 *
 * This is the inverse of WebRtcIsac_DecorrelateInterVec().
 *
 * Input:
 *      -data
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -out                : correlated parametrs.
 */
int16_t
WebRtcIsac_CorrelateInterVec(
    const double* data,
    double*       out,
    int16_t bandwidth)
{
  int16_t coeffCntr;
  int16_t rowCntr;
  int16_t colCntr;
  int16_t interVecDim;
  double myVec[UB16_LPC_VEC_PER_FRAME] = {0.0};
  const double* interVecDecorrMat;

  switch(bandwidth)
  {
    case isac12kHz:
      {
        interVecDim       = UB_LPC_VEC_PER_FRAME;
        interVecDecorrMat = &WebRtcIsac_kInterVecDecorrMatUb12[0][0];
        break;
      }
    case isac16kHz:
      {
        interVecDim       = UB16_LPC_VEC_PER_FRAME;
        interVecDecorrMat = &WebRtcIsac_kInterVecDecorrMatUb16[0][0];
        break;
      }
    default:
      return -1;
  }

  for(coeffCntr = 0; coeffCntr < UB_LPC_ORDER; coeffCntr++)
  {
    for(rowCntr = 0; rowCntr < interVecDim; rowCntr++)
    {
      myVec[rowCntr] = 0;
      for(colCntr = 0; colCntr < interVecDim; colCntr++)
      {
        myVec[rowCntr] += data[coeffCntr + colCntr * UB_LPC_ORDER] * //*ptrData *
            interVecDecorrMat[rowCntr * interVecDim + colCntr];
        //ptrData += UB_LPC_ORDER;
      }
    }

    for(rowCntr = 0; rowCntr < interVecDim; rowCntr++)
    {
      out[coeffCntr + rowCntr * UB_LPC_ORDER] = myVec[rowCntr];
    }
  }
  return 0;
}

/******************************************************************************
 * WebRtcIsac_AddLarMean()
 *
 * This is the inverse of WebRtcIsac_RemoveLarMean()
 *
 * Input:
 *      -data               : pointer to mean-removed LAR:s.
 *      -bandwidth          : indicates if the given LAR vectors belong
 *                            to SWB-12kHz or SWB-16kHz.
 *
 * Output:
 *      -data               : pointer to LARs.
 */
int16_t
WebRtcIsac_AddLarMean(
    double* data,
    int16_t bandwidth)
{
  int16_t coeffCntr;
  int16_t vecCntr;
  int16_t numVec;
  const double* meanLAR;

  switch(bandwidth)
  {
    case isac12kHz:
      {
        numVec = UB_LPC_VEC_PER_FRAME;
        meanLAR = WebRtcIsac_kMeanLarUb12;
        break;
      }
    case isac16kHz:
      {
        numVec = UB16_LPC_VEC_PER_FRAME;
        meanLAR = WebRtcIsac_kMeanLarUb16;
        break;
      }
    default:
      return -1;
  }

  for(vecCntr = 0; vecCntr < numVec; vecCntr++)
  {
    for(coeffCntr = 0; coeffCntr < UB_LPC_ORDER; coeffCntr++)
    {
      *data++ += meanLAR[coeffCntr];
    }
  }
  return 0;
}

/******************************************************************************
 * WebRtcIsac_ToLogDomainRemoveMean()
 *
 * Transform the LPC gain to log domain then remove the mean value.
 *
 * Input:
 *      -lpcGain            : pointer to LPC Gain, expecting 6 LPC gains
 *
 * Output:
 *      -lpcGain            : mean-removed in log domain.
 */
int16_t
WebRtcIsac_ToLogDomainRemoveMean(
    double* data)
{
  int16_t coeffCntr;
  for(coeffCntr = 0; coeffCntr < UB_LPC_GAIN_DIM; coeffCntr++)
  {
    data[coeffCntr] = log(data[coeffCntr]) - WebRtcIsac_kMeanLpcGain;
  }
  return 0;
}


/******************************************************************************
 * WebRtcIsac_DecorrelateLPGain()
 *
 * Decorrelate LPC gains. There are 6 LPC Gains per frame. This is like
 * multiplying gain vector with decorrelating matrix.
 *
 * Input:
 *      -data               : LPC gain in log-domain with mean removed.
 *
 * Output:
 *      -out                : decorrelated parameters.
 */
int16_t WebRtcIsac_DecorrelateLPGain(
    const double* data,
    double* out)
{
  int16_t rowCntr;
  int16_t colCntr;

  for(colCntr = 0; colCntr < UB_LPC_GAIN_DIM; colCntr++)
  {
    *out = 0;
    for(rowCntr = 0; rowCntr < UB_LPC_GAIN_DIM; rowCntr++)
    {
      *out += data[rowCntr] * WebRtcIsac_kLpcGainDecorrMat[rowCntr][colCntr];
    }
    out++;
  }
  return 0;
}

/******************************************************************************
 * WebRtcIsac_QuantizeLpcGain()
 *
 * Quantize the decorrelated log-domain gains.
 *
 * Input:
 *      -lpcGain            : uncorrelated LPC gains.
 *
 * Output:
 *      -idx                : quantization indices
 *      -lpcGain            : quantized value of the inpt.
 */
double WebRtcIsac_QuantizeLpcGain(
    double* data,
    int*    idx)
{
  int16_t coeffCntr;
  for(coeffCntr = 0; coeffCntr < UB_LPC_GAIN_DIM; coeffCntr++)
  {
    *idx = (int)floor((*data - WebRtcIsac_kLeftRecPointLpcGain[coeffCntr]) /
                                WebRtcIsac_kQSizeLpcGain + 0.5);

    if(*idx < 0)
    {
      *idx = 0;
    }
    else if(*idx >= WebRtcIsac_kNumQCellLpcGain[coeffCntr])
    {
      *idx = WebRtcIsac_kNumQCellLpcGain[coeffCntr] - 1;
    }
    *data = WebRtcIsac_kLeftRecPointLpcGain[coeffCntr] + *idx *
        WebRtcIsac_kQSizeLpcGain;

    data++;
    idx++;
  }
  return 0;
}

/******************************************************************************
 * WebRtcIsac_DequantizeLpcGain()
 *
 * Get the quantized values given the quantization indices.
 *
 * Input:
 *      -idx                : pointer to quantization indices.
 *
 * Output:
 *      -lpcGains           : quantized values of the given parametes.
 */
int16_t WebRtcIsac_DequantizeLpcGain(
    const int* idx,
    double*    out)
{
  int16_t coeffCntr;
  for(coeffCntr = 0; coeffCntr < UB_LPC_GAIN_DIM; coeffCntr++)
  {
    *out = WebRtcIsac_kLeftRecPointLpcGain[coeffCntr] + *idx *
        WebRtcIsac_kQSizeLpcGain;
    out++;
    idx++;
  }
  return 0;
}

/******************************************************************************
 * WebRtcIsac_CorrelateLpcGain()
 *
 * This is the inverse of WebRtcIsac_DecorrelateLPGain().
 *
 * Input:
 *      -data               : decorrelated parameters.
 *
 * Output:
 *      -out                : correlated parameters.
 */
int16_t WebRtcIsac_CorrelateLpcGain(
    const double* data,
    double* out)
{
  int16_t rowCntr;
  int16_t colCntr;

  for(rowCntr = 0; rowCntr < UB_LPC_GAIN_DIM; rowCntr++)
  {
    *out = 0;
    for(colCntr = 0; colCntr < UB_LPC_GAIN_DIM; colCntr++)
    {
      *out += WebRtcIsac_kLpcGainDecorrMat[rowCntr][colCntr] * data[colCntr];
    }
    out++;
  }

  return 0;
}


/******************************************************************************
 * WebRtcIsac_AddMeanToLinearDomain()
 *
 * This is the inverse of WebRtcIsac_ToLogDomainRemoveMean().
 *
 * Input:
 *      -lpcGain            : LPC gain in log-domain & mean removed
 *
 * Output:
 *      -lpcGain            : LPC gain in normal domain.
 */
int16_t WebRtcIsac_AddMeanToLinearDomain(
    double* lpcGains)
{
  int16_t coeffCntr;
  for(coeffCntr = 0; coeffCntr < UB_LPC_GAIN_DIM; coeffCntr++)
  {
    lpcGains[coeffCntr] = exp(lpcGains[coeffCntr] + WebRtcIsac_kMeanLpcGain);
  }
  return 0;
}

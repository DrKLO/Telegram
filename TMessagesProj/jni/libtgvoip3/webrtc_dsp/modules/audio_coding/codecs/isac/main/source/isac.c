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
 * isac.c
 *
 * This C file contains the functions for the ISAC API
 *
 */

#include "modules/audio_coding/codecs/isac/main/include/isac.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "rtc_base/checks.h"
#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "modules/audio_coding/codecs/isac/main/source/bandwidth_estimator.h"
#include "modules/audio_coding/codecs/isac/main/source/codec.h"
#include "modules/audio_coding/codecs/isac/main/source/crc.h"
#include "modules/audio_coding/codecs/isac/main/source/entropy_coding.h"
#include "modules/audio_coding/codecs/isac/main/source/lpc_shape_swb16_tables.h"
#include "modules/audio_coding/codecs/isac/main/source/os_specific_inline.h"
#include "modules/audio_coding/codecs/isac/main/source/structs.h"
#include "modules/audio_coding/codecs/isac/main/source/isac_vad.h"
#include "rtc_base/system/arch.h"

#define BIT_MASK_DEC_INIT 0x0001
#define BIT_MASK_ENC_INIT 0x0002

#define LEN_CHECK_SUM_WORD8     4
#define MAX_NUM_LAYERS         10


/****************************************************************************
 * UpdatePayloadSizeLimit(...)
 *
 * Call this function to update the limit on the payload size. The limit on
 * payload size might change i) if a user ''directly changes the limit by
 * calling xxx_setMaxPayloadSize() or xxx_setMaxRate(), or ii) indirectly
 * when bandwidth is changing. The latter might be the result of bandwidth
 * adaptation, or direct change of the bottleneck in instantaneous mode.
 *
 * This function takes the current overall limit on payload, and translates it
 * to the limits on lower and upper-band. If the codec is in wideband mode,
 * then the overall limit and the limit on the lower-band is the same.
 * Otherwise, a fraction of the limit should be allocated to lower-band
 * leaving some room for the upper-band bit-stream. That is why an update
 * of limit is required every time that the bandwidth is changing.
 *
 */
static void UpdatePayloadSizeLimit(ISACMainStruct* instISAC) {
  int16_t lim30MsPayloadBytes = WEBRTC_SPL_MIN(
                          (instISAC->maxPayloadSizeBytes),
                          (instISAC->maxRateBytesPer30Ms));
  int16_t lim60MsPayloadBytes = WEBRTC_SPL_MIN(
                          (instISAC->maxPayloadSizeBytes),
                          (instISAC->maxRateBytesPer30Ms << 1));

  /* The only time that iSAC will have 60 ms
   * frame-size is when operating in wideband, so
   * there is no upper-band bit-stream. */

  if (instISAC->bandwidthKHz == isac8kHz) {
    /* At 8 kHz there is no upper-band bit-stream,
     * therefore, the lower-band limit is the overall limit. */
    instISAC->instLB.ISACencLB_obj.payloadLimitBytes60 =
      lim60MsPayloadBytes;
    instISAC->instLB.ISACencLB_obj.payloadLimitBytes30 =
      lim30MsPayloadBytes;
  } else {
    /* When in super-wideband, we only have 30 ms frames.
     * Do a rate allocation for the given limit. */
    if (lim30MsPayloadBytes > 250) {
      /* 4/5 to lower-band the rest for upper-band. */
      instISAC->instLB.ISACencLB_obj.payloadLimitBytes30 =
        (lim30MsPayloadBytes << 2) / 5;
    } else if (lim30MsPayloadBytes > 200) {
      /* For the interval of 200 to 250 the share of
       * upper-band linearly grows from 20 to 50. */
      instISAC->instLB.ISACencLB_obj.payloadLimitBytes30 =
        (lim30MsPayloadBytes << 1) / 5 + 100;
    } else {
      /* Allocate only 20 for upper-band. */
      instISAC->instLB.ISACencLB_obj.payloadLimitBytes30 =
        lim30MsPayloadBytes - 20;
    }
    instISAC->instUB.ISACencUB_obj.maxPayloadSizeBytes =
      lim30MsPayloadBytes;
  }
}


/****************************************************************************
 * UpdateBottleneck(...)
 *
 * This function updates the bottleneck only if the codec is operating in
 * channel-adaptive mode. Furthermore, as the update of bottleneck might
 * result in an update of bandwidth, therefore, the bottlenech should be
 * updated just right before the first 10ms of a frame is pushed into encoder.
 *
 */
static void UpdateBottleneck(ISACMainStruct* instISAC) {
  /* Read the bottleneck from bandwidth estimator for the
   * first 10 ms audio. This way, if there is a change
   * in bandwidth, upper and lower-band will be in sync. */
  if ((instISAC->codingMode == 0) &&
      (instISAC->instLB.ISACencLB_obj.buffer_index == 0) &&
      (instISAC->instLB.ISACencLB_obj.frame_nb == 0)) {
    int32_t bottleneck =
        WebRtcIsac_GetUplinkBandwidth(&instISAC->bwestimator_obj);

    /* Adding hysteresis when increasing signal bandwidth. */
    if ((instISAC->bandwidthKHz == isac8kHz)
        && (bottleneck > 37000)
        && (bottleneck < 41000)) {
      bottleneck = 37000;
    }

    /* Switching from 12 kHz to 16 kHz is not allowed at this revision.
     * If we let this happen, we have to take care of buffer_index and
     * the last LPC vector. */
    if ((instISAC->bandwidthKHz != isac16kHz) &&
        (bottleneck > 46000)) {
      bottleneck = 46000;
    }

    /* We might need a rate allocation. */
    if (instISAC->encoderSamplingRateKHz == kIsacWideband) {
      /* Wideband is the only choice we have here. */
      instISAC->instLB.ISACencLB_obj.bottleneck =
        (bottleneck > 32000) ? 32000 : bottleneck;
      instISAC->bandwidthKHz = isac8kHz;
    } else {
      /* Do the rate-allocation and get the new bandwidth. */
      enum ISACBandwidth bandwidth;
      WebRtcIsac_RateAllocation(bottleneck,
                                &(instISAC->instLB.ISACencLB_obj.bottleneck),
                                &(instISAC->instUB.ISACencUB_obj.bottleneck),
                                &bandwidth);
      if (bandwidth != isac8kHz) {
        instISAC->instLB.ISACencLB_obj.new_framelength = 480;
      }
      if (bandwidth != instISAC->bandwidthKHz) {
        /* Bandwidth is changing. */
        instISAC->bandwidthKHz = bandwidth;
        UpdatePayloadSizeLimit(instISAC);
        if (bandwidth == isac12kHz) {
          instISAC->instLB.ISACencLB_obj.buffer_index = 0;
        }
        /* Currently we don't let the bandwidth to switch to 16 kHz
         * if in adaptive mode. If we let this happen, we have to take
         * care of buffer_index and the last LPC vector. */
      }
    }
  }
}


/****************************************************************************
 * GetSendBandwidthInfo(...)
 *
 * This is called to get the bandwidth info. This info is the bandwidth and
 * the jitter of 'there-to-here' channel, estimated 'here.' These info
 * is signaled in an in-band fashion to the other side.
 *
 * The call to the bandwidth estimator triggers a recursive averaging which
 * has to be synchronized between encoder & decoder, therefore, the call to
 * BWE should be once per packet. As the BWE info is inserted into bit-stream
 * We need a valid info right before the encodeLB function is going to
 * generate a bit-stream. That is when lower-band buffer has already 20ms
 * of audio, and the 3rd block of 10ms is going to be injected into encoder.
 *
 * Inputs:
 *         - instISAC          : iSAC instance.
 *
 * Outputs:
 *         - bandwidthIndex    : an index which has to be encoded in
 *                               lower-band bit-stream, indicating the
 *                               bandwidth of there-to-here channel.
 *         - jitterInfo        : this indicates if the jitter is high
 *                               or low and it is encoded in upper-band
 *                               bit-stream.
 *
 */
static void GetSendBandwidthInfo(ISACMainStruct* instISAC,
                                 int16_t* bandwidthIndex,
                                 int16_t* jitterInfo) {
  if ((instISAC->instLB.ISACencLB_obj.buffer_index ==
      (FRAMESAMPLES_10ms << 1)) &&
      (instISAC->instLB.ISACencLB_obj.frame_nb == 0)) {
    /* Bandwidth estimation and coding. */
    WebRtcIsac_GetDownlinkBwJitIndexImpl(&(instISAC->bwestimator_obj),
                                         bandwidthIndex, jitterInfo,
                                         instISAC->decoderSamplingRateKHz);
  }
}


/****************************************************************************
 * WebRtcIsac_AssignSize(...)
 *
 * This function returns the size of the ISAC instance, so that the instance
 * can be created out side iSAC.
 *
 * Output:
 *        - sizeinbytes       : number of bytes needed to allocate for the
 *                              instance.
 *
 * Return value               : 0 - Ok
 *                             -1 - Error
 */
int16_t WebRtcIsac_AssignSize(int* sizeInBytes) {
  *sizeInBytes = sizeof(ISACMainStruct) * 2 / sizeof(int16_t);
  return 0;
}


/****************************************************************************
 * WebRtcIsac_Assign(...)
 *
 * This function assigns the memory already created to the ISAC instance.
 *
 * Input:
 *        - ISAC_main_inst    : address of the pointer to the coder instance.
 *        - instISAC_Addr     : the already allocated memory, where we put the
 *                              iSAC structure.
 *
 * Return value               : 0 - Ok
 *                             -1 - Error
 */
int16_t WebRtcIsac_Assign(ISACStruct** ISAC_main_inst,
                          void* instISAC_Addr) {
  if (instISAC_Addr != NULL) {
    ISACMainStruct* instISAC = (ISACMainStruct*)instISAC_Addr;
    instISAC->errorCode = 0;
    instISAC->initFlag = 0;

    /* Assign the address. */
    *ISAC_main_inst = (ISACStruct*)instISAC_Addr;

    /* Default is wideband. */
    instISAC->encoderSamplingRateKHz = kIsacWideband;
    instISAC->decoderSamplingRateKHz = kIsacWideband;
    instISAC->bandwidthKHz           = isac8kHz;
    instISAC->in_sample_rate_hz = 16000;

    WebRtcIsac_InitTransform(&instISAC->transform_tables);
    return 0;
  } else {
    return -1;
  }
}


/****************************************************************************
 * WebRtcIsac_Create(...)
 *
 * This function creates an ISAC instance, which will contain the state
 * information for one coding/decoding channel.
 *
 * Input:
 *        - ISAC_main_inst    : address of the pointer to the coder instance.
 *
 * Return value               : 0 - Ok
 *                             -1 - Error
 */
int16_t WebRtcIsac_Create(ISACStruct** ISAC_main_inst) {
  ISACMainStruct* instISAC;

  if (ISAC_main_inst != NULL) {
    instISAC = (ISACMainStruct*)malloc(sizeof(ISACMainStruct));
    *ISAC_main_inst = (ISACStruct*)instISAC;
    if (*ISAC_main_inst != NULL) {
      instISAC->errorCode = 0;
      instISAC->initFlag = 0;
      /* Default is wideband. */
      instISAC->bandwidthKHz = isac8kHz;
      instISAC->encoderSamplingRateKHz = kIsacWideband;
      instISAC->decoderSamplingRateKHz = kIsacWideband;
      instISAC->in_sample_rate_hz = 16000;

      WebRtcIsac_InitTransform(&instISAC->transform_tables);
      return 0;
    } else {
      return -1;
    }
  } else {
    return -1;
  }
}


/****************************************************************************
 * WebRtcIsac_Free(...)
 *
 * This function frees the ISAC instance created at the beginning.
 *
 * Input:
 *        - ISAC_main_inst    : a ISAC instance.
 *
 * Return value               : 0 - Ok
 *                             -1 - Error
 */
int16_t WebRtcIsac_Free(ISACStruct* ISAC_main_inst) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
  free(instISAC);
  return 0;
}


/****************************************************************************
 * EncoderInitLb(...) - internal function for initialization of
 *                                Lower Band
 * EncoderInitUb(...) - internal function for initialization of
 *                                Upper Band
 * WebRtcIsac_EncoderInit(...) - API function
 *
 * This function initializes a ISAC instance prior to the encoder calls.
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 *        - CodingMode        : 0 -> Bit rate and frame length are automatically
 *                                 adjusted to available bandwidth on
 *                                 transmission channel, applicable just to
 *                                 wideband mode.
 *                              1 -> User sets a frame length and a target bit
 *                                 rate which is taken as the maximum
 *                                 short-term average bit rate.
 *
 * Return value               :  0 - Ok
 *                              -1 - Error
 */
static int16_t EncoderInitLb(ISACLBStruct* instLB,
                             int16_t codingMode,
                             enum IsacSamplingRate sampRate) {
  int16_t statusInit = 0;
  int k;

  /* Init stream vector to zero */
  for (k = 0; k < STREAM_SIZE_MAX_60; k++) {
    instLB->ISACencLB_obj.bitstr_obj.stream[k] = 0;
  }

  if ((codingMode == 1) || (sampRate == kIsacSuperWideband)) {
    /* 30 ms frame-size if either in super-wideband or
     * instantaneous mode (I-mode). */
    instLB->ISACencLB_obj.new_framelength = 480;
  } else {
    instLB->ISACencLB_obj.new_framelength = INITIAL_FRAMESAMPLES;
  }

  WebRtcIsac_InitMasking(&instLB->ISACencLB_obj.maskfiltstr_obj);
  WebRtcIsac_InitPreFilterbank(&instLB->ISACencLB_obj.prefiltbankstr_obj);
  WebRtcIsac_InitPitchFilter(&instLB->ISACencLB_obj.pitchfiltstr_obj);
  WebRtcIsac_InitPitchAnalysis(
    &instLB->ISACencLB_obj.pitchanalysisstr_obj);

  instLB->ISACencLB_obj.buffer_index = 0;
  instLB->ISACencLB_obj.frame_nb = 0;
  /* Default for I-mode. */
  instLB->ISACencLB_obj.bottleneck = 32000;
  instLB->ISACencLB_obj.current_framesamples = 0;
  instLB->ISACencLB_obj.s2nr = 0;
  instLB->ISACencLB_obj.payloadLimitBytes30 = STREAM_SIZE_MAX_30;
  instLB->ISACencLB_obj.payloadLimitBytes60 = STREAM_SIZE_MAX_60;
  instLB->ISACencLB_obj.maxPayloadBytes = STREAM_SIZE_MAX_60;
  instLB->ISACencLB_obj.maxRateInBytes = STREAM_SIZE_MAX_30;
  instLB->ISACencLB_obj.enforceFrameSize = 0;
  /* Invalid value prevents getRedPayload to
     run before encoder is called. */
  instLB->ISACencLB_obj.lastBWIdx            = -1;
  return statusInit;
}

static int16_t EncoderInitUb(ISACUBStruct* instUB,
                             int16_t bandwidth) {
  int16_t statusInit = 0;
  int k;

  /* Init stream vector to zero. */
  for (k = 0; k < STREAM_SIZE_MAX_60; k++) {
    instUB->ISACencUB_obj.bitstr_obj.stream[k] = 0;
  }

  WebRtcIsac_InitMasking(&instUB->ISACencUB_obj.maskfiltstr_obj);
  WebRtcIsac_InitPreFilterbank(&instUB->ISACencUB_obj.prefiltbankstr_obj);

  if (bandwidth == isac16kHz) {
    instUB->ISACencUB_obj.buffer_index = LB_TOTAL_DELAY_SAMPLES;
  } else {
    instUB->ISACencUB_obj.buffer_index = 0;
  }
  /* Default for I-mode. */
  instUB->ISACencUB_obj.bottleneck = 32000;
  /* These store the limits for the wideband + super-wideband bit-stream. */
  instUB->ISACencUB_obj.maxPayloadSizeBytes = STREAM_SIZE_MAX_30 << 1;
  /* This has to be updated after each lower-band encoding to guarantee
   * a correct payload-limitation. */
  instUB->ISACencUB_obj.numBytesUsed = 0;
  memset(instUB->ISACencUB_obj.data_buffer_float, 0,
         (MAX_FRAMESAMPLES + LB_TOTAL_DELAY_SAMPLES) * sizeof(float));

  memcpy(&(instUB->ISACencUB_obj.lastLPCVec),
         WebRtcIsac_kMeanLarUb16, sizeof(double) * UB_LPC_ORDER);

  return statusInit;
}


int16_t WebRtcIsac_EncoderInit(ISACStruct* ISAC_main_inst,
                               int16_t codingMode) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
  int16_t status;

  if ((codingMode != 0) && (codingMode != 1)) {
    instISAC->errorCode = ISAC_DISALLOWED_CODING_MODE;
    return -1;
  }
  /* Default bottleneck. */
  instISAC->bottleneck = MAX_ISAC_BW;

  if (instISAC->encoderSamplingRateKHz == kIsacWideband) {
    instISAC->bandwidthKHz = isac8kHz;
    instISAC->maxPayloadSizeBytes = STREAM_SIZE_MAX_60;
    instISAC->maxRateBytesPer30Ms = STREAM_SIZE_MAX_30;
  } else {
    instISAC->bandwidthKHz = isac16kHz;
    instISAC->maxPayloadSizeBytes = STREAM_SIZE_MAX;
    instISAC->maxRateBytesPer30Ms = STREAM_SIZE_MAX;
  }

  /* Channel-adaptive = 0; Instantaneous (Channel-independent) = 1. */
  instISAC->codingMode = codingMode;

  WebRtcIsac_InitBandwidthEstimator(&instISAC->bwestimator_obj,
                                    instISAC->encoderSamplingRateKHz,
                                    instISAC->decoderSamplingRateKHz);

  WebRtcIsac_InitRateModel(&instISAC->rate_data_obj);
  /* Default for I-mode. */
  instISAC->MaxDelay = 10.0;

  status = EncoderInitLb(&instISAC->instLB, codingMode,
                         instISAC->encoderSamplingRateKHz);
  if (status < 0) {
    instISAC->errorCode = -status;
    return -1;
  }

  if (instISAC->encoderSamplingRateKHz == kIsacSuperWideband) {
    /* Initialize encoder filter-bank. */
    memset(instISAC->analysisFBState1, 0,
           FB_STATE_SIZE_WORD32 * sizeof(int32_t));
    memset(instISAC->analysisFBState2, 0,
           FB_STATE_SIZE_WORD32 * sizeof(int32_t));

    status = EncoderInitUb(&(instISAC->instUB),
                           instISAC->bandwidthKHz);
    if (status < 0) {
      instISAC->errorCode = -status;
      return -1;
    }
  }
  /* Initialization is successful, set the flag. */
  instISAC->initFlag |= BIT_MASK_ENC_INIT;
  return 0;
}


/****************************************************************************
 * WebRtcIsac_Encode(...)
 *
 * This function encodes 10ms frame(s) and inserts it into a package.
 * Input speech length has to be 160 samples (10ms). The encoder buffers those
 * 10ms frames until it reaches the chosen Framesize (480 or 960 samples
 * corresponding to 30 or 60 ms frames), and then proceeds to the encoding.
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 *        - speechIn          : input speech vector.
 *
 * Output:
 *        - encoded           : the encoded data vector
 *
 * Return value:
 *                            : >0 - Length (in bytes) of coded data
 *                            :  0 - The buffer didn't reach the chosen
 *                                  frameSize so it keeps buffering speech
 *                                 samples.
 *                            : -1 - Error
 */
int WebRtcIsac_Encode(ISACStruct* ISAC_main_inst,
                      const int16_t* speechIn,
                      uint8_t* encoded) {
  float inFrame[FRAMESAMPLES_10ms];
  int16_t speechInLB[FRAMESAMPLES_10ms];
  int16_t speechInUB[FRAMESAMPLES_10ms];
  int streamLenLB = 0;
  int streamLenUB = 0;
  int streamLen = 0;
  size_t k = 0;
  uint8_t garbageLen = 0;
  int32_t bottleneck = 0;
  int16_t bottleneckIdx = 0;
  int16_t jitterInfo = 0;

  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
  ISACLBStruct* instLB = &(instISAC->instLB);
  ISACUBStruct* instUB = &(instISAC->instUB);

  /* Check if encoder initiated. */
  if ((instISAC->initFlag & BIT_MASK_ENC_INIT) !=
      BIT_MASK_ENC_INIT) {
    instISAC->errorCode = ISAC_ENCODER_NOT_INITIATED;
    return -1;
  }

  if (instISAC->encoderSamplingRateKHz == kIsacSuperWideband) {
    WebRtcSpl_AnalysisQMF(speechIn, SWBFRAMESAMPLES_10ms, speechInLB,
                          speechInUB, instISAC->analysisFBState1,
                          instISAC->analysisFBState2);

    /* Convert from fixed to floating point. */
    for (k = 0; k < FRAMESAMPLES_10ms; k++) {
      inFrame[k] = (float)speechInLB[k];
    }
  } else {
    for (k = 0; k < FRAMESAMPLES_10ms; k++) {
      inFrame[k] = (float) speechIn[k];
    }
  }

  /* Add some noise to avoid denormal numbers. */
  inFrame[0] += (float)1.23455334e-3;
  inFrame[1] -= (float)2.04324239e-3;
  inFrame[2] += (float)1.90854954e-3;
  inFrame[9] += (float)1.84854878e-3;

  /* This function will update the bottleneck if required. */
  UpdateBottleneck(instISAC);

  /* Get the bandwith information which has to be sent to the other side. */
  GetSendBandwidthInfo(instISAC, &bottleneckIdx, &jitterInfo);

  /* Encode lower-band. */
  streamLenLB = WebRtcIsac_EncodeLb(&instISAC->transform_tables,
                                    inFrame, &instLB->ISACencLB_obj,
                                    instISAC->codingMode, bottleneckIdx);
  if (streamLenLB < 0) {
    return -1;
  }

  if (instISAC->encoderSamplingRateKHz == kIsacSuperWideband) {
    instUB = &(instISAC->instUB);

    /* Convert to float. */
    for (k = 0; k < FRAMESAMPLES_10ms; k++) {
      inFrame[k] = (float) speechInUB[k];
    }

    /* Add some noise to avoid denormal numbers. */
    inFrame[0] += (float)1.23455334e-3;
    inFrame[1] -= (float)2.04324239e-3;
    inFrame[2] += (float)1.90854954e-3;
    inFrame[9] += (float)1.84854878e-3;

    /* Tell to upper-band the number of bytes used so far.
     * This is for payload limitation. */
    instUB->ISACencUB_obj.numBytesUsed =
        (int16_t)(streamLenLB + 1 + LEN_CHECK_SUM_WORD8);
    /* Encode upper-band. */
    switch (instISAC->bandwidthKHz) {
      case isac12kHz: {
        streamLenUB = WebRtcIsac_EncodeUb12(&instISAC->transform_tables,
                                            inFrame, &instUB->ISACencUB_obj,
                                            jitterInfo);
        break;
      }
      case isac16kHz: {
        streamLenUB = WebRtcIsac_EncodeUb16(&instISAC->transform_tables,
                                            inFrame, &instUB->ISACencUB_obj,
                                            jitterInfo);
        break;
      }
      case isac8kHz: {
        streamLenUB = 0;
        break;
      }
    }

    if ((streamLenUB < 0) && (streamLenUB != -ISAC_PAYLOAD_LARGER_THAN_LIMIT)) {
      /* An error has happened but this is not the error due to a
       * bit-stream larger than the limit. */
      return -1;
    }

    if (streamLenLB == 0) {
      return 0;
    }

    /* One byte is allocated for the length. According to older decoders
       so the length bit-stream plus one byte for size and
       LEN_CHECK_SUM_WORD8 for the checksum should be less than or equal
       to 255. */
    if ((streamLenUB > (255 - (LEN_CHECK_SUM_WORD8 + 1))) ||
        (streamLenUB == -ISAC_PAYLOAD_LARGER_THAN_LIMIT)) {
      /* We have got a too long bit-stream we skip the upper-band
       * bit-stream for this frame. */
      streamLenUB = 0;
    }

    memcpy(encoded, instLB->ISACencLB_obj.bitstr_obj.stream, streamLenLB);
    streamLen = streamLenLB;
    if (streamLenUB > 0) {
      encoded[streamLenLB] = (uint8_t)(streamLenUB + 1 + LEN_CHECK_SUM_WORD8);
      memcpy(&encoded[streamLenLB + 1],
             instUB->ISACencUB_obj.bitstr_obj.stream,
             streamLenUB);
      streamLen += encoded[streamLenLB];
    } else {
      encoded[streamLenLB] = 0;
    }
  } else {
    if (streamLenLB == 0) {
      return 0;
    }
    memcpy(encoded, instLB->ISACencLB_obj.bitstr_obj.stream, streamLenLB);
    streamLenUB = 0;
    streamLen = streamLenLB;
  }

  /* Add Garbage if required. */
  bottleneck = WebRtcIsac_GetUplinkBandwidth(&instISAC->bwestimator_obj);
  if (instISAC->codingMode == 0) {
    int minBytes;
    int limit;
    uint8_t* ptrGarbage;

    instISAC->MaxDelay = (double)WebRtcIsac_GetUplinkMaxDelay(
                           &instISAC->bwestimator_obj);

    /* Update rate model and get minimum number of bytes in this packet. */
    minBytes = WebRtcIsac_GetMinBytes(
        &(instISAC->rate_data_obj), streamLen,
        instISAC->instLB.ISACencLB_obj.current_framesamples, bottleneck,
        instISAC->MaxDelay, instISAC->bandwidthKHz);

    /* Make sure MinBytes does not exceed packet size limit. */
    if (instISAC->bandwidthKHz == isac8kHz) {
      if (instLB->ISACencLB_obj.current_framesamples == FRAMESAMPLES) {
        limit = instLB->ISACencLB_obj.payloadLimitBytes30;
      } else {
        limit = instLB->ISACencLB_obj.payloadLimitBytes60;
      }
    } else {
      limit = instUB->ISACencUB_obj.maxPayloadSizeBytes;
    }
    minBytes = (minBytes > limit) ? limit : minBytes;

    /* Make sure we don't allow more than 255 bytes of garbage data.
     * We store the length of the garbage data in 8 bits in the bitstream,
     * 255 is the max garbage length we can signal using 8 bits. */
    if ((instISAC->bandwidthKHz == isac8kHz) ||
        (streamLenUB == 0)) {
      ptrGarbage = &encoded[streamLenLB];
      limit = streamLen + 255;
    } else {
      ptrGarbage = &encoded[streamLenLB + 1 + streamLenUB];
      limit = streamLen + (255 - encoded[streamLenLB]);
    }
    minBytes = (minBytes > limit) ? limit : minBytes;

    garbageLen = (minBytes > streamLen) ? (uint8_t)(minBytes - streamLen) : 0;

    /* Save data for creation of multiple bit-streams. */
    /* If bit-stream too short then add garbage at the end. */
    if (garbageLen > 0) {
      /* Overwrite the garbage area to avoid leaking possibly sensitive data
         over the network. This also makes the output deterministic. */
      memset(ptrGarbage, 0, garbageLen);

      /* For a correct length of the upper-band bit-stream together
       * with the garbage. Garbage is embeded in upper-band bit-stream.
       * That is the only way to preserve backward compatibility. */
      if ((instISAC->bandwidthKHz == isac8kHz) ||
          (streamLenUB == 0)) {
        encoded[streamLenLB] = garbageLen;
      } else {
        encoded[streamLenLB] += garbageLen;
        /* Write the length of the garbage at the end of the upper-band
         *  bit-stream, if exists. This helps for sanity check. */
        encoded[streamLenLB + 1 + streamLenUB] = garbageLen;

      }
      streamLen += garbageLen;
    }
  } else {
    /* update rate model */
    WebRtcIsac_UpdateRateModel(
        &instISAC->rate_data_obj, streamLen,
        instISAC->instLB.ISACencLB_obj.current_framesamples, bottleneck);
    garbageLen = 0;
  }

  /* Generate CRC if required. */
  if ((instISAC->bandwidthKHz != isac8kHz) && (streamLenUB > 0)) {
    uint32_t crc;

    WebRtcIsac_GetCrc((int16_t*)(&(encoded[streamLenLB + 1])),
                      streamLenUB + garbageLen, &crc);
#ifndef WEBRTC_ARCH_BIG_ENDIAN
    for (k = 0; k < LEN_CHECK_SUM_WORD8; k++) {
      encoded[streamLen - LEN_CHECK_SUM_WORD8 + k] =
          (uint8_t)(crc >> (24 - k * 8));
    }
#else
    memcpy(&encoded[streamLenLB + streamLenUB + 1], &crc, LEN_CHECK_SUM_WORD8);
#endif
  }
  return streamLen;
}


/******************************************************************************
 * WebRtcIsac_GetNewBitStream(...)
 *
 * This function returns encoded data, with the recieved bwe-index in the
 * stream. If the rate is set to a value less than bottleneck of codec
 * the new bistream will be re-encoded with the given target rate.
 * It should always return a complete packet, i.e. only called once
 * even for 60 msec frames.
 *
 * NOTE 1! This function does not write in the ISACStruct, it is not allowed.
 * NOTE 2! Rates larger than the bottleneck of the codec will be limited
 *         to the current bottleneck.
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 *        - bweIndex          : Index of bandwidth estimate to put in new
 *                              bitstream
 *        - rate              : target rate of the transcoder is bits/sec.
 *                              Valid values are the accepted rate in iSAC,
 *                              i.e. 10000 to 56000.
 *
 * Output:
 *        - encoded           : The encoded data vector
 *
 * Return value               : >0 - Length (in bytes) of coded data
 *                              -1 - Error  or called in SWB mode
 *                                 NOTE! No error code is written to
 *                                 the struct since it is only allowed to read
 *                                 the struct.
 */
int16_t WebRtcIsac_GetNewBitStream(ISACStruct*  ISAC_main_inst,
                                   int16_t  bweIndex,
                                   int16_t  jitterInfo,
                                   int32_t  rate,
                                   uint8_t* encoded,
                                   int16_t  isRCU) {
  Bitstr iSACBitStreamInst;   /* Local struct for bitstream handling */
  int16_t streamLenLB;
  int16_t streamLenUB;
  int16_t totalStreamLen;
  double gain2;
  double gain1;
  float scale;
  enum ISACBandwidth bandwidthKHz;
  double rateLB;
  double rateUB;
  int32_t currentBN;
  uint32_t crc;
#ifndef WEBRTC_ARCH_BIG_ENDIAN
  int16_t  k;
#endif
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;

  if ((instISAC->initFlag & BIT_MASK_ENC_INIT) !=
      BIT_MASK_ENC_INIT) {
    return -1;
  }

  /* Get the bottleneck of this iSAC and limit the
   * given rate to the current bottleneck. */
  WebRtcIsac_GetUplinkBw(ISAC_main_inst, &currentBN);
  if (rate > currentBN) {
    rate = currentBN;
  }

  if (WebRtcIsac_RateAllocation(rate, &rateLB, &rateUB, &bandwidthKHz) < 0) {
    return -1;
  }

  /* Cannot transcode from 16 kHz to 12 kHz. */
  if ((bandwidthKHz == isac12kHz) &&
      (instISAC->bandwidthKHz == isac16kHz)) {
    return -1;
  }

  /* A gain [dB] for the given rate. */
  gain1 = WebRtcIsac_GetSnr(
      rateLB, instISAC->instLB.ISACencLB_obj.current_framesamples);
  /* The gain [dB] of this iSAC. */
  gain2 = WebRtcIsac_GetSnr(
      instISAC->instLB.ISACencLB_obj.bottleneck,
      instISAC->instLB.ISACencLB_obj.current_framesamples);

  /* Scale is the ratio of two gains in normal domain. */
  scale = (float)pow(10, (gain1 - gain2) / 20.0);
  /* Change the scale if this is a RCU bit-stream. */
  scale = (isRCU) ? (scale * RCU_TRANSCODING_SCALE) : scale;

  streamLenLB = WebRtcIsac_EncodeStoredDataLb(
                  &instISAC->instLB.ISACencLB_obj.SaveEnc_obj,
                  &iSACBitStreamInst, bweIndex, scale);

  if (streamLenLB < 0) {
    return -1;
  }

  /* Convert from bytes to int16_t. */
  memcpy(encoded, iSACBitStreamInst.stream, streamLenLB);

  if (bandwidthKHz == isac8kHz) {
    return streamLenLB;
  }

  totalStreamLen = streamLenLB;
  /* super-wideband is always at 30ms.
   * These gains are in dB.
   * Gain for the given rate. */
  gain1 = WebRtcIsac_GetSnr(rateUB, FRAMESAMPLES);
  /* Gain of this iSAC */
  gain2 = WebRtcIsac_GetSnr(instISAC->instUB.ISACencUB_obj.bottleneck,
                            FRAMESAMPLES);

  /* Scale is the ratio of two gains in normal domain. */
  scale = (float)pow(10, (gain1 - gain2) / 20.0);

  /* Change the scale if this is a RCU bit-stream. */
  scale = (isRCU)? (scale * RCU_TRANSCODING_SCALE_UB) : scale;

  streamLenUB = WebRtcIsac_EncodeStoredDataUb(
                  &(instISAC->instUB.ISACencUB_obj.SaveEnc_obj),
                  &iSACBitStreamInst, jitterInfo, scale,
                  instISAC->bandwidthKHz);

  if (streamLenUB < 0) {
    return -1;
  }

  if (streamLenUB + 1 + LEN_CHECK_SUM_WORD8 > 255) {
    return streamLenLB;
  }

  totalStreamLen = streamLenLB + streamLenUB + 1 + LEN_CHECK_SUM_WORD8;
  encoded[streamLenLB] = streamLenUB + 1 + LEN_CHECK_SUM_WORD8;

  memcpy(&encoded[streamLenLB + 1], iSACBitStreamInst.stream,
         streamLenUB);

  WebRtcIsac_GetCrc((int16_t*)(&(encoded[streamLenLB + 1])),
                    streamLenUB, &crc);
#ifndef WEBRTC_ARCH_BIG_ENDIAN
  for (k = 0; k < LEN_CHECK_SUM_WORD8; k++) {
    encoded[totalStreamLen - LEN_CHECK_SUM_WORD8 + k] =
      (uint8_t)((crc >> (24 - k * 8)) & 0xFF);
  }
#else
  memcpy(&encoded[streamLenLB + streamLenUB + 1], &crc,
         LEN_CHECK_SUM_WORD8);
#endif
  return totalStreamLen;
}


/****************************************************************************
 * DecoderInitLb(...) - internal function for initialization of
 *                                Lower Band
 * DecoderInitUb(...) - internal function for initialization of
 *                                Upper Band
 * WebRtcIsac_DecoderInit(...) - API function
 *
 * This function initializes a ISAC instance prior to the decoder calls.
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 */
static void DecoderInitLb(ISACLBStruct* instISAC) {
  int i;
  /* Initialize stream vector to zero. */
  for (i = 0; i < STREAM_SIZE_MAX_60; i++) {
    instISAC->ISACdecLB_obj.bitstr_obj.stream[i] = 0;
  }

  WebRtcIsac_InitMasking(&instISAC->ISACdecLB_obj.maskfiltstr_obj);
  WebRtcIsac_InitPostFilterbank(
    &instISAC->ISACdecLB_obj.postfiltbankstr_obj);
  WebRtcIsac_InitPitchFilter(&instISAC->ISACdecLB_obj.pitchfiltstr_obj);
}

static void DecoderInitUb(ISACUBStruct* instISAC) {
  int i;
  /* Init stream vector to zero */
  for (i = 0; i < STREAM_SIZE_MAX_60; i++) {
    instISAC->ISACdecUB_obj.bitstr_obj.stream[i] = 0;
  }

  WebRtcIsac_InitMasking(&instISAC->ISACdecUB_obj.maskfiltstr_obj);
  WebRtcIsac_InitPostFilterbank(
    &instISAC->ISACdecUB_obj.postfiltbankstr_obj);
}

void WebRtcIsac_DecoderInit(ISACStruct* ISAC_main_inst) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;

  DecoderInitLb(&instISAC->instLB);
  if (instISAC->decoderSamplingRateKHz == kIsacSuperWideband) {
    memset(instISAC->synthesisFBState1, 0,
           FB_STATE_SIZE_WORD32 * sizeof(int32_t));
    memset(instISAC->synthesisFBState2, 0,
           FB_STATE_SIZE_WORD32 * sizeof(int32_t));
    DecoderInitUb(&(instISAC->instUB));
  }
  if ((instISAC->initFlag & BIT_MASK_ENC_INIT) != BIT_MASK_ENC_INIT) {
    WebRtcIsac_InitBandwidthEstimator(&instISAC->bwestimator_obj,
                                      instISAC->encoderSamplingRateKHz,
                                      instISAC->decoderSamplingRateKHz);
  }
  instISAC->initFlag |= BIT_MASK_DEC_INIT;
  instISAC->resetFlag_8kHz = 0;
}


/****************************************************************************
 * WebRtcIsac_UpdateBwEstimate(...)
 *
 * This function updates the estimate of the bandwidth.
 *
 * NOTE:
 * The estimates of bandwidth is not valid if the sample rate of the far-end
 * encoder is set to 48 kHz and send timestamps are increamented according to
 * 48 kHz sampling rate.
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 *        - encoded           : encoded ISAC frame(s).
 *        - packet_size       : size of the packet.
 *        - rtp_seq_number    : the RTP number of the packet.
 *        - arr_ts            : the arrival time of the packet (from NetEq)
 *                              in samples.
 *
 * Return value               :  0 - Ok
 *                              -1 - Error
 */
int16_t WebRtcIsac_UpdateBwEstimate(ISACStruct* ISAC_main_inst,
                                    const uint8_t* encoded,
                                    size_t packet_size,
                                    uint16_t rtp_seq_number,
                                    uint32_t send_ts,
                                    uint32_t arr_ts) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
  Bitstr streamdata;
#ifndef WEBRTC_ARCH_BIG_ENDIAN
  int k;
#endif
  int16_t err;

  /* Check if decoder initiated. */
  if ((instISAC->initFlag & BIT_MASK_DEC_INIT) != BIT_MASK_DEC_INIT) {
    instISAC->errorCode = ISAC_DECODER_NOT_INITIATED;
    return -1;
  }

  /* Check that the size of the packet is valid, and if not return without
   * updating the bandwidth estimate. A valid size is at least 10 bytes. */
  if (packet_size < 10) {
    /* Return error code if the packet length is null. */
    instISAC->errorCode = ISAC_EMPTY_PACKET;
    return -1;
  }

  WebRtcIsac_ResetBitstream(&(streamdata));

#ifndef WEBRTC_ARCH_BIG_ENDIAN
  for (k = 0; k < 10; k++) {
    uint16_t ek = ((const uint16_t*)encoded)[k >> 1];
    streamdata.stream[k] = (uint8_t)((ek >> ((k & 1) << 3)) & 0xff);
  }
#else
  memcpy(streamdata.stream, encoded, 10);
#endif

  err = WebRtcIsac_EstimateBandwidth(&instISAC->bwestimator_obj, &streamdata,
                                     packet_size, rtp_seq_number, send_ts,
                                     arr_ts, instISAC->encoderSamplingRateKHz,
                                     instISAC->decoderSamplingRateKHz);
  if (err < 0) {
    /* Return error code if something went wrong. */
    instISAC->errorCode = -err;
    return -1;
  }
  return 0;
}

static int Decode(ISACStruct* ISAC_main_inst,
                  const uint8_t* encoded,
                  size_t lenEncodedBytes,
                  int16_t* decoded,
                  int16_t* speechType,
                  int16_t isRCUPayload) {
  /* Number of samples (480 or 960), output from decoder
     that were actually used in the encoder/decoder
     (determined on the fly). */
  int16_t numSamplesLB;
  int16_t numSamplesUB;
  int16_t speechIdx;
  float outFrame[MAX_FRAMESAMPLES];
  int16_t outFrameLB[MAX_FRAMESAMPLES];
  int16_t outFrameUB[MAX_FRAMESAMPLES];
  int numDecodedBytesLBint;
  size_t numDecodedBytesLB;
  int numDecodedBytesUB;
  size_t lenEncodedLBBytes;
  int16_t validChecksum = 1;
  int16_t k;
  uint16_t numLayer;
  size_t totSizeBytes;
  int16_t err;

  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
  ISACUBDecStruct* decInstUB = &(instISAC->instUB.ISACdecUB_obj);
  ISACLBDecStruct* decInstLB = &(instISAC->instLB.ISACdecLB_obj);

  /* Check if decoder initiated. */
  if ((instISAC->initFlag & BIT_MASK_DEC_INIT) !=
      BIT_MASK_DEC_INIT) {
    instISAC->errorCode = ISAC_DECODER_NOT_INITIATED;
    return -1;
  }

  if (lenEncodedBytes == 0) {
    /* return error code if the packet length is null. */
    instISAC->errorCode = ISAC_EMPTY_PACKET;
    return -1;
  }

  /* The size of the encoded lower-band is bounded by
   * STREAM_SIZE_MAX. If a payload with the size larger than STREAM_SIZE_MAX
   * is received, it is not considered erroneous. */
  lenEncodedLBBytes = (lenEncodedBytes > STREAM_SIZE_MAX) ?
      STREAM_SIZE_MAX : lenEncodedBytes;

  /* Copy to lower-band bit-stream structure. */
  memcpy(instISAC->instLB.ISACdecLB_obj.bitstr_obj.stream, encoded,
         lenEncodedLBBytes);

  /* We need to initialize numSamplesLB to something; otherwise, in the test
     for whether we should return -1 below, the compiler might generate code
     that fools Memcheck (Valgrind) into thinking that the control flow depends
     on the uninitialized value in numSamplesLB (since WebRtcIsac_DecodeLb will
     not fill it in if it fails and returns -1). */
  numSamplesLB = 0;

  /* Regardless of that the current codec is setup to work in
   * wideband or super-wideband, the decoding of the lower-band
   * has to be performed. */
  numDecodedBytesLBint = WebRtcIsac_DecodeLb(&instISAC->transform_tables,
                                             outFrame, decInstLB,
                                             &numSamplesLB, isRCUPayload);
  numDecodedBytesLB = (size_t)numDecodedBytesLBint;
  if ((numDecodedBytesLBint < 0) ||
      (numDecodedBytesLB > lenEncodedLBBytes) ||
      (numSamplesLB > MAX_FRAMESAMPLES)) {
    instISAC->errorCode = ISAC_LENGTH_MISMATCH;
    return -1;
  }

  /* Error Check, we accept multi-layer bit-stream This will limit number
   * of iterations of the while loop. Even without this the number
   * of iterations is limited. */
  numLayer = 1;
  totSizeBytes = numDecodedBytesLB;
  while (totSizeBytes != lenEncodedBytes) {
    if ((totSizeBytes > lenEncodedBytes) ||
        (encoded[totSizeBytes] == 0) ||
        (numLayer > MAX_NUM_LAYERS)) {
      instISAC->errorCode = ISAC_LENGTH_MISMATCH;
      return -1;
    }
    totSizeBytes += encoded[totSizeBytes];
    numLayer++;
  }

  if (instISAC->decoderSamplingRateKHz == kIsacWideband) {
    for (k = 0; k < numSamplesLB; k++) {
      if (outFrame[k] > 32767) {
        decoded[k] = 32767;
      } else if (outFrame[k] < -32768) {
        decoded[k] = -32768;
      } else {
        decoded[k] = (int16_t)WebRtcIsac_lrint(outFrame[k]);
      }
    }
    numSamplesUB = 0;
  } else {
    uint32_t crc;
    /* We don't accept larger than 30ms (480 samples at lower-band)
     * frame-size. */
    for (k = 0; k < numSamplesLB; k++) {
      if (outFrame[k] > 32767) {
        outFrameLB[k] = 32767;
      } else if (outFrame[k] < -32768) {
        outFrameLB[k] = -32768;
      } else {
        outFrameLB[k] = (int16_t)WebRtcIsac_lrint(outFrame[k]);
      }
    }

    /* Check for possible error, and if upper-band stream exists. */
    if (numDecodedBytesLB == lenEncodedBytes) {
      /* Decoding was successful. No super-wideband bit-stream exists. */
      numSamplesUB = numSamplesLB;
      memset(outFrameUB, 0, sizeof(int16_t) *  numSamplesUB);

      /* Prepare for the potential increase of signal bandwidth. */
      instISAC->resetFlag_8kHz = 2;
    } else {
      /* This includes the checksum and the bytes that stores the length. */
      int16_t lenNextStream = encoded[numDecodedBytesLB];

      /* Is this garbage or valid super-wideband bit-stream?
       * Check if checksum is valid. */
      if (lenNextStream <= (LEN_CHECK_SUM_WORD8 + 1)) {
        /* Such a small second layer cannot be super-wideband layer.
         * It must be a short garbage. */
        validChecksum = 0;
      } else {
        /* Run CRC to see if the checksum match. */
        WebRtcIsac_GetCrc((int16_t*)(&encoded[numDecodedBytesLB + 1]),
                          lenNextStream - LEN_CHECK_SUM_WORD8 - 1, &crc);

        validChecksum = 1;
        for (k = 0; k < LEN_CHECK_SUM_WORD8; k++) {
          validChecksum &= (((crc >> (24 - k * 8)) & 0xFF) ==
                            encoded[numDecodedBytesLB + lenNextStream -
                                          LEN_CHECK_SUM_WORD8 + k]);
        }
      }

      if (!validChecksum) {
        /* This is a garbage, we have received a wideband
         * bit-stream with garbage. */
        numSamplesUB = numSamplesLB;
        memset(outFrameUB, 0, sizeof(int16_t) * numSamplesUB);
      } else {
        /* A valid super-wideband biststream exists. */
        enum ISACBandwidth bandwidthKHz;
        int32_t maxDelayBit;

        /* If we have super-wideband bit-stream, we cannot
         * have 60 ms frame-size. */
        if (numSamplesLB > FRAMESAMPLES) {
          instISAC->errorCode = ISAC_LENGTH_MISMATCH;
          return -1;
        }

        /* The rest of the bit-stream contains the upper-band
         * bit-stream curently this is the only thing there,
         * however, we might add more layers. */

        /* Have to exclude one byte where the length is stored
         * and last 'LEN_CHECK_SUM_WORD8' bytes where the
         * checksum is stored. */
        lenNextStream -= (LEN_CHECK_SUM_WORD8 + 1);

        memcpy(decInstUB->bitstr_obj.stream,
               &encoded[numDecodedBytesLB + 1], lenNextStream);

        /* Reset bit-stream object, this is the first decoding. */
        WebRtcIsac_ResetBitstream(&(decInstUB->bitstr_obj));

        /* Decode jitter information. */
        err = WebRtcIsac_DecodeJitterInfo(&decInstUB->bitstr_obj, &maxDelayBit);
        if (err < 0) {
          instISAC->errorCode = -err;
          return -1;
        }

        /* Update jitter info which is in the upper-band bit-stream
         * only if the encoder is in super-wideband. Otherwise,
         * the jitter info is already embedded in bandwidth index
         * and has been updated. */
        if (instISAC->encoderSamplingRateKHz == kIsacSuperWideband) {
          err = WebRtcIsac_UpdateUplinkJitter(
                  &(instISAC->bwestimator_obj), maxDelayBit);
          if (err < 0) {
            instISAC->errorCode = -err;
            return -1;
          }
        }

        /* Decode bandwidth information. */
        err = WebRtcIsac_DecodeBandwidth(&decInstUB->bitstr_obj,
                                         &bandwidthKHz);
        if (err < 0) {
          instISAC->errorCode = -err;
          return -1;
        }

        switch (bandwidthKHz) {
          case isac12kHz: {
            numDecodedBytesUB = WebRtcIsac_DecodeUb12(
                &instISAC->transform_tables, outFrame, decInstUB, isRCUPayload);

            /* Hang-over for transient alleviation -
             * wait two frames to add the upper band going up from 8 kHz. */
            if (instISAC->resetFlag_8kHz > 0) {
              if (instISAC->resetFlag_8kHz == 2) {
                /* Silence first and a half frame. */
                memset(outFrame, 0, MAX_FRAMESAMPLES *
                       sizeof(float));
              } else {
                const float rampStep = 2.0f / MAX_FRAMESAMPLES;
                float rampVal = 0;
                memset(outFrame, 0, (MAX_FRAMESAMPLES >> 1) *
                       sizeof(float));

                /* Ramp up second half of second frame. */
                for (k = MAX_FRAMESAMPLES / 2; k < MAX_FRAMESAMPLES; k++) {
                  outFrame[k] *= rampVal;
                  rampVal += rampStep;
                }
              }
              instISAC->resetFlag_8kHz -= 1;
            }

            break;
          }
          case isac16kHz: {
            numDecodedBytesUB = WebRtcIsac_DecodeUb16(
                &instISAC->transform_tables, outFrame, decInstUB, isRCUPayload);
            break;
          }
          default:
            return -1;
        }

        if (numDecodedBytesUB < 0) {
          instISAC->errorCode = numDecodedBytesUB;
          return -1;
        }
        if (numDecodedBytesLB + numDecodedBytesUB > lenEncodedBytes) {
          // We have supposedly decoded more bytes than we were given. Likely
          // caused by bad input data.
          instISAC->errorCode = ISAC_LENGTH_MISMATCH;
          return -1;
        }

        /* It might be less due to garbage. */
        if ((numDecodedBytesUB != lenNextStream) &&
            (numDecodedBytesLB + 1 + numDecodedBytesUB >= lenEncodedBytes ||
             numDecodedBytesUB !=
                 (lenNextStream -
                  encoded[numDecodedBytesLB + 1 + numDecodedBytesUB]))) {
          instISAC->errorCode = ISAC_LENGTH_MISMATCH;
          return -1;
        }

        /* If there is no error Upper-band always decodes
         * 30 ms (480 samples). */
        numSamplesUB = FRAMESAMPLES;

        /* Convert to W16. */
        for (k = 0; k < numSamplesUB; k++) {
          if (outFrame[k] > 32767) {
            outFrameUB[k] = 32767;
          } else if (outFrame[k] < -32768) {
            outFrameUB[k] = -32768;
          } else {
            outFrameUB[k] = (int16_t)WebRtcIsac_lrint(
                              outFrame[k]);
          }
        }
      }
    }

    speechIdx = 0;
    while (speechIdx < numSamplesLB) {
      WebRtcSpl_SynthesisQMF(&outFrameLB[speechIdx], &outFrameUB[speechIdx],
                             FRAMESAMPLES_10ms, &decoded[(speechIdx << 1)],
                             instISAC->synthesisFBState1,
                             instISAC->synthesisFBState2);

      speechIdx += FRAMESAMPLES_10ms;
    }
  }
  *speechType = 0;
  return (numSamplesLB + numSamplesUB);
}







/****************************************************************************
 * WebRtcIsac_Decode(...)
 *
 * This function decodes a ISAC frame. Output speech length
 * will be a multiple of 480 samples: 480 or 960 samples,
 * depending on the  frameSize (30 or 60 ms).
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 *        - encoded           : encoded ISAC frame(s)
 *        - len               : bytes in encoded vector
 *
 * Output:
 *        - decoded           : The decoded vector
 *
 * Return value               : >0 - number of samples in decoded vector
 *                              -1 - Error
 */

int WebRtcIsac_Decode(ISACStruct* ISAC_main_inst,
                      const uint8_t* encoded,
                      size_t lenEncodedBytes,
                      int16_t* decoded,
                      int16_t* speechType) {
  int16_t isRCUPayload = 0;
  return Decode(ISAC_main_inst, encoded, lenEncodedBytes, decoded,
                speechType, isRCUPayload);
}

/****************************************************************************
 * WebRtcIsac_DecodeRcu(...)
 *
 * This function decodes a redundant (RCU) iSAC frame. Function is called in
 * NetEq with a stored RCU payload in case of packet loss. Output speech length
 * will be a multiple of 480 samples: 480 or 960 samples,
 * depending on the framesize (30 or 60 ms).
 *
 * Input:
 *      - ISAC_main_inst     : ISAC instance.
 *      - encoded            : encoded ISAC RCU frame(s)
 *      - len                : bytes in encoded vector
 *
 * Output:
 *      - decoded            : The decoded vector
 *
 * Return value              : >0 - number of samples in decoded vector
 *                             -1 - Error
 */



int WebRtcIsac_DecodeRcu(ISACStruct* ISAC_main_inst,
                         const uint8_t* encoded,
                         size_t lenEncodedBytes,
                         int16_t* decoded,
                         int16_t* speechType) {
  int16_t isRCUPayload = 1;
  return Decode(ISAC_main_inst, encoded, lenEncodedBytes, decoded,
                speechType, isRCUPayload);
}


/****************************************************************************
 * WebRtcIsac_DecodePlc(...)
 *
 * This function conducts PLC for ISAC frame(s). Output speech length
 * will be a multiple of 480 samples: 480 or 960 samples,
 * depending on the  frameSize (30 or 60 ms).
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 *        - noOfLostFrames    : Number of PLC frames to produce
 *
 * Output:
 *        - decoded           : The decoded vector
 *
 * Return value               : Number of samples in decoded PLC vector
 */
size_t WebRtcIsac_DecodePlc(ISACStruct* ISAC_main_inst,
                            int16_t* decoded,
                            size_t noOfLostFrames) {
  size_t numSamples = 0;
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;

  /* Limit number of frames to two = 60 millisecond.
   * Otherwise we exceed data vectors. */
  if (noOfLostFrames > 2) {
    noOfLostFrames = 2;
  }

  /* Get the number of samples per frame */
  switch (instISAC->decoderSamplingRateKHz) {
    case kIsacWideband: {
      numSamples = 480 * noOfLostFrames;
      break;
    }
    case kIsacSuperWideband: {
      numSamples = 960 * noOfLostFrames;
      break;
    }
  }

  /* Set output samples to zero. */
  memset(decoded, 0, numSamples * sizeof(int16_t));
  return numSamples;
}


/****************************************************************************
 * ControlLb(...) - Internal function for controlling Lower Band
 * ControlUb(...) - Internal function for controlling Upper Band
 * WebRtcIsac_Control(...) - API function
 *
 * This function sets the limit on the short-term average bit rate and the
 * frame length. Should be used only in Instantaneous mode.
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 *        - rate              : limit on the short-term average bit rate,
 *                              in bits/second (between 10000 and 32000)
 *        - frameSize         : number of milliseconds per frame (30 or 60)
 *
 * Return value               : 0 - ok
 *                             -1 - Error
 */
static int16_t ControlLb(ISACLBStruct* instISAC, double rate,
                         int16_t frameSize) {
  if ((rate >= 10000) && (rate <= 32000)) {
    instISAC->ISACencLB_obj.bottleneck = rate;
  } else {
    return -ISAC_DISALLOWED_BOTTLENECK;
  }

  if ((frameSize == 30) || (frameSize == 60)) {
    instISAC->ISACencLB_obj.new_framelength = (FS / 1000) *  frameSize;
  } else {
    return -ISAC_DISALLOWED_FRAME_LENGTH;
  }

  return 0;
}

static int16_t ControlUb(ISACUBStruct* instISAC, double rate) {
  if ((rate >= 10000) && (rate <= 32000)) {
    instISAC->ISACencUB_obj.bottleneck = rate;
  } else {
    return -ISAC_DISALLOWED_BOTTLENECK;
  }
  return 0;
}

int16_t WebRtcIsac_Control(ISACStruct* ISAC_main_inst,
                           int32_t bottleneckBPS,
                           int frameSize) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
  int16_t status;
  double rateLB;
  double rateUB;
  enum ISACBandwidth bandwidthKHz;

  if (instISAC->codingMode == 0) {
    /* In adaptive mode. */
    instISAC->errorCode = ISAC_MODE_MISMATCH;
    return -1;
  }

  /* Check if encoder initiated */
  if ((instISAC->initFlag & BIT_MASK_ENC_INIT) !=
      BIT_MASK_ENC_INIT) {
    instISAC->errorCode = ISAC_ENCODER_NOT_INITIATED;
    return -1;
  }

  if (instISAC->encoderSamplingRateKHz == kIsacWideband) {
    /* If the sampling rate is 16kHz then bandwith should be 8kHz,
     * regardless of bottleneck. */
    bandwidthKHz = isac8kHz;
    rateLB = (bottleneckBPS > 32000) ? 32000 : bottleneckBPS;
    rateUB = 0;
  } else {
    if (WebRtcIsac_RateAllocation(bottleneckBPS, &rateLB, &rateUB,
                                  &bandwidthKHz) < 0) {
      return -1;
    }
  }

  if ((instISAC->encoderSamplingRateKHz == kIsacSuperWideband) &&
      (frameSize != 30) &&
      (bandwidthKHz != isac8kHz)) {
    /* Cannot have 60 ms in super-wideband. */
    instISAC->errorCode = ISAC_DISALLOWED_FRAME_LENGTH;
    return -1;
  }

  status = ControlLb(&instISAC->instLB, rateLB, (int16_t)frameSize);
  if (status < 0) {
    instISAC->errorCode = -status;
    return -1;
  }
  if (bandwidthKHz != isac8kHz) {
    status = ControlUb(&(instISAC->instUB), rateUB);
    if (status < 0) {
      instISAC->errorCode = -status;
      return -1;
    }
  }


  /* Check if bandwidth is changing from wideband to super-wideband
   * then we have to synch data buffer of lower & upper-band. Also
   * clean up the upper-band data buffer. */

  if ((instISAC->bandwidthKHz == isac8kHz) && (bandwidthKHz != isac8kHz)) {
    memset(instISAC->instUB.ISACencUB_obj.data_buffer_float, 0,
           sizeof(float) * (MAX_FRAMESAMPLES + LB_TOTAL_DELAY_SAMPLES));

    if (bandwidthKHz == isac12kHz) {
      instISAC->instUB.ISACencUB_obj.buffer_index =
        instISAC->instLB.ISACencLB_obj.buffer_index;
    } else {
      instISAC->instUB.ISACencUB_obj.buffer_index =
          LB_TOTAL_DELAY_SAMPLES + instISAC->instLB.ISACencLB_obj.buffer_index;

      memcpy(&(instISAC->instUB.ISACencUB_obj.lastLPCVec),
             WebRtcIsac_kMeanLarUb16, sizeof(double) * UB_LPC_ORDER);
    }
  }

  /* Update the payload limit if the bandwidth is changing. */
  if (instISAC->bandwidthKHz != bandwidthKHz) {
    instISAC->bandwidthKHz = bandwidthKHz;
    UpdatePayloadSizeLimit(instISAC);
  }
  instISAC->bottleneck = bottleneckBPS;
  return 0;
}

void WebRtcIsac_SetInitialBweBottleneck(ISACStruct* ISAC_main_inst,
                                        int bottleneck_bits_per_second) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
  RTC_DCHECK_GE(bottleneck_bits_per_second, 10000);
  RTC_DCHECK_LE(bottleneck_bits_per_second, 32000);
  instISAC->bwestimator_obj.send_bw_avg = (float)bottleneck_bits_per_second;
}

/****************************************************************************
 * WebRtcIsac_ControlBwe(...)
 *
 * This function sets the initial values of bottleneck and frame-size if
 * iSAC is used in channel-adaptive mode. Through this API, users can
 * enforce a frame-size for all values of bottleneck. Then iSAC will not
 * automatically change the frame-size.
 *
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 *        - rateBPS           : initial value of bottleneck in bits/second
 *                              10000 <= rateBPS <= 32000 is accepted
 *                              For default bottleneck set rateBPS = 0
 *        - frameSizeMs       : number of milliseconds per frame (30 or 60)
 *        - enforceFrameSize  : 1 to enforce the given frame-size through out
 *                              the adaptation process, 0 to let iSAC change
 *                              the frame-size if required.
 *
 * Return value               : 0 - ok
 *                             -1 - Error
 */
int16_t WebRtcIsac_ControlBwe(ISACStruct* ISAC_main_inst,
                              int32_t bottleneckBPS,
                              int frameSizeMs,
                              int16_t enforceFrameSize) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
  enum ISACBandwidth bandwidth;

   /* Check if encoder initiated */
  if ((instISAC->initFlag & BIT_MASK_ENC_INIT) !=
      BIT_MASK_ENC_INIT) {
    instISAC->errorCode = ISAC_ENCODER_NOT_INITIATED;
    return -1;
  }

  /* Check that we are in channel-adaptive mode, otherwise, return (-1) */
  if (instISAC->codingMode != 0) {
    instISAC->errorCode = ISAC_MODE_MISMATCH;
    return -1;
  }
  if ((frameSizeMs != 30) &&
      (instISAC->encoderSamplingRateKHz == kIsacSuperWideband)) {
    return -1;
  }

  /* Set structure variable if enforceFrameSize is set. ISAC will then
   * keep the chosen frame size. */
  if (enforceFrameSize != 0) {
    instISAC->instLB.ISACencLB_obj.enforceFrameSize = 1;
  } else {
    instISAC->instLB.ISACencLB_obj.enforceFrameSize = 0;
  }

  /* Set the initial rate. If the input value is zero then the default intial
   * rate is used. Otehrwise, values between 10 to 32 kbps are accepted. */
  if (bottleneckBPS != 0) {
    double rateLB;
    double rateUB;
    if (WebRtcIsac_RateAllocation(bottleneckBPS, &rateLB, &rateUB,
                                  &bandwidth) < 0) {
      return -1;
    }
    instISAC->bwestimator_obj.send_bw_avg = (float)bottleneckBPS;
    instISAC->bandwidthKHz = bandwidth;
  }

  /* Set the initial frame-size. If 'enforceFrameSize' is set, the frame-size
   *  will not change */
  if (frameSizeMs != 0) {
    if ((frameSizeMs  == 30) || (frameSizeMs == 60)) {
      instISAC->instLB.ISACencLB_obj.new_framelength =
          (int16_t)((FS / 1000) * frameSizeMs);
    } else {
      instISAC->errorCode = ISAC_DISALLOWED_FRAME_LENGTH;
      return -1;
    }
  }
  return 0;
}


/****************************************************************************
 * WebRtcIsac_GetDownLinkBwIndex(...)
 *
 * This function returns index representing the Bandwidth estimate from
 * the other side to this side.
 *
 * Input:
 *        - ISAC_main_inst    : iSAC structure
 *
 * Output:
 *        - bweIndex         : Bandwidth estimate to transmit to other side.
 *
 */
int16_t WebRtcIsac_GetDownLinkBwIndex(ISACStruct* ISAC_main_inst,
                                      int16_t* bweIndex,
                                      int16_t* jitterInfo) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;

  /* Check if encoder initialized. */
  if ((instISAC->initFlag & BIT_MASK_DEC_INIT) !=
      BIT_MASK_DEC_INIT) {
    instISAC->errorCode = ISAC_ENCODER_NOT_INITIATED;
    return -1;
  }

  /* Call function to get Bandwidth Estimate. */
  WebRtcIsac_GetDownlinkBwJitIndexImpl(&(instISAC->bwestimator_obj), bweIndex,
                                       jitterInfo,
                                       instISAC->decoderSamplingRateKHz);
  return 0;
}


/****************************************************************************
 * WebRtcIsac_UpdateUplinkBw(...)
 *
 * This function takes an index representing the Bandwidth estimate from
 * this side to other side and updates BWE.
 *
 * Input:
 *        - ISAC_main_inst    : iSAC structure
 *        - rateIndex         : Bandwidth estimate from other side.
 *
 * Return value               : 0 - ok
 *                             -1 - index out of range
 */
int16_t WebRtcIsac_UpdateUplinkBw(ISACStruct* ISAC_main_inst,
                                  int16_t bweIndex) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
  int16_t returnVal;

  /* Check if encoder initiated. */
  if ((instISAC->initFlag & BIT_MASK_ENC_INIT) !=
      BIT_MASK_ENC_INIT) {
    instISAC->errorCode = ISAC_ENCODER_NOT_INITIATED;
    return -1;
  }

  /* Call function to get Bandwidth Estimate. */
  returnVal = WebRtcIsac_UpdateUplinkBwImpl(
                &(instISAC->bwestimator_obj), bweIndex,
                instISAC->encoderSamplingRateKHz);

  if (returnVal < 0) {
    instISAC->errorCode = -returnVal;
    return -1;
  } else {
    return 0;
  }
}


/****************************************************************************
 * WebRtcIsac_ReadBwIndex(...)
 *
 * This function returns the index of the Bandwidth estimate from the
 * bit-stream.
 *
 * Input:
 *        - encoded           : Encoded bit-stream
 *
 * Output:
 *        - frameLength       : Length of frame in packet (in samples)
 *        - bweIndex          : Bandwidth estimate in bit-stream
 *
 */
int16_t WebRtcIsac_ReadBwIndex(const uint8_t* encoded,
                               int16_t* bweIndex) {
  Bitstr streamdata;
#ifndef WEBRTC_ARCH_BIG_ENDIAN
  int k;
#endif
  int16_t err;

  WebRtcIsac_ResetBitstream(&(streamdata));

#ifndef WEBRTC_ARCH_BIG_ENDIAN
  for (k = 0; k < 10; k++) {
    int16_t ek2 = ((const int16_t*)encoded)[k >> 1];
    streamdata.stream[k] = (uint8_t)((ek2 >> ((k & 1) << 3)) & 0xff);
  }
#else
  memcpy(streamdata.stream, encoded, 10);
#endif

  /* Decode frame length. */
  err = WebRtcIsac_DecodeFrameLen(&streamdata, bweIndex);
  if (err < 0) {
    return err;
  }

  /* Decode BW estimation. */
  err = WebRtcIsac_DecodeSendBW(&streamdata, bweIndex);
  if (err < 0) {
    return err;
  }

  return 0;
}


/****************************************************************************
 * WebRtcIsac_ReadFrameLen(...)
 *
 * This function returns the number of samples the decoder will generate if
 * the given payload is decoded.
 *
 * Input:
 *        - encoded           : Encoded bitstream
 *
 * Output:
 *        - frameLength       : Length of frame in packet (in samples)
 *
 */
int16_t WebRtcIsac_ReadFrameLen(ISACStruct* ISAC_main_inst,
                                const uint8_t* encoded,
                                int16_t* frameLength) {
  Bitstr streamdata;
#ifndef WEBRTC_ARCH_BIG_ENDIAN
  int k;
#endif
  int16_t err;
  ISACMainStruct* instISAC;

  WebRtcIsac_ResetBitstream(&(streamdata));

#ifndef WEBRTC_ARCH_BIG_ENDIAN
  for (k = 0; k < 10; k++) {
    int16_t ek2 = ((const int16_t*)encoded)[k >> 1];
    streamdata.stream[k] = (uint8_t)((ek2 >> ((k & 1) << 3)) & 0xff);
  }
#else
  memcpy(streamdata.stream, encoded, 10);
#endif

  /* Decode frame length. */
  err = WebRtcIsac_DecodeFrameLen(&streamdata, frameLength);
  if (err < 0) {
    return -1;
  }
  instISAC = (ISACMainStruct*)ISAC_main_inst;

  if (instISAC->decoderSamplingRateKHz == kIsacSuperWideband) {
    /* The decoded frame length indicates the number of samples in
     * lower-band in this case, multiply by 2 to get the total number
     * of samples. */
    *frameLength <<= 1;
  }
  return 0;
}


/*******************************************************************************
 * WebRtcIsac_GetNewFrameLen(...)
 *
 * This function returns the frame length (in samples) of the next packet.
 * In the case of channel-adaptive mode, iSAC decides on its frame length based
 * on the estimated bottleneck, this AOI allows a user to prepare for the next
 * packet (at the encoder).
 *
 * The primary usage is in CE to make the iSAC works in channel-adaptive mode
 *
 * Input:
 *        - ISAC_main_inst     : iSAC struct
 *
 * Return Value                : frame lenght in samples
 *
 */
int16_t WebRtcIsac_GetNewFrameLen(ISACStruct* ISAC_main_inst) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;

  /* Return new frame length. */
  if (instISAC->in_sample_rate_hz == 16000)
    return (instISAC->instLB.ISACencLB_obj.new_framelength);
  else  /* 32000 Hz */
    return ((instISAC->instLB.ISACencLB_obj.new_framelength) * 2);
}


/****************************************************************************
 * WebRtcIsac_GetErrorCode(...)
 *
 * This function can be used to check the error code of an iSAC instance.
 * When a function returns -1 an error code will be set for that instance.
 * The function below extracts the code of the last error that occurred in
 * the specified instance.
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance
 *
 * Return value               : Error code
 */
int16_t WebRtcIsac_GetErrorCode(ISACStruct* ISAC_main_inst) {
 return ((ISACMainStruct*)ISAC_main_inst)->errorCode;
}


/****************************************************************************
 * WebRtcIsac_GetUplinkBw(...)
 *
 * This function outputs the target bottleneck of the codec. In
 * channel-adaptive mode, the target bottleneck is specified through an in-band
 * signalling retrieved by bandwidth estimator.
 * In channel-independent, also called instantaneous mode, the target
 * bottleneck is provided to the encoder by calling xxx_control(...) (if
 * xxx_control is never called, the default values are used.).
 * Note that the output is the iSAC internal operating bottleneck which might
 * differ slightly from the one provided through xxx_control().
 *
 * Input:
 *        - ISAC_main_inst    : iSAC instance
 *
 * Output:
 *        - *bottleneck       : bottleneck in bits/sec
 *
 * Return value               : -1 if error happens
 *                               0 bit-rates computed correctly.
 */
int16_t WebRtcIsac_GetUplinkBw(ISACStruct*  ISAC_main_inst,
                               int32_t* bottleneck) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;

  if (instISAC->codingMode == 0) {
    /* We are in adaptive mode then get the bottleneck from BWE. */
    *bottleneck = (int32_t)instISAC->bwestimator_obj.send_bw_avg;
  } else {
    *bottleneck = instISAC->bottleneck;
  }

  if ((*bottleneck > 32000) && (*bottleneck < 38000)) {
    *bottleneck = 32000;
  } else if ((*bottleneck > 45000) && (*bottleneck < 50000)) {
    *bottleneck = 45000;
  } else if (*bottleneck > 56000) {
    *bottleneck = 56000;
  }
  return 0;
}


/******************************************************************************
 * WebRtcIsac_SetMaxPayloadSize(...)
 *
 * This function sets a limit for the maximum payload size of iSAC. The same
 * value is used both for 30 and 60 ms packets. If the encoder sampling rate
 * is 16 kHz the maximum payload size is between 120 and 400 bytes. If the
 * encoder sampling rate is 32 kHz the maximum payload size is between 120
 * and 600 bytes.
 *
 * ---------------
 * IMPORTANT NOTES
 * ---------------
 * The size of a packet is limited to the minimum of 'max-payload-size' and
 * 'max-rate.' For instance, let's assume the max-payload-size is set to
 * 170 bytes, and max-rate is set to 40 kbps. Note that a limit of 40 kbps
 * translates to 150 bytes for 30ms frame-size & 300 bytes for 60ms
 * frame-size. Then a packet with a frame-size of 30 ms is limited to 150,
 * i.e. min(170, 150), and a packet with 60 ms frame-size is limited to
 * 170 bytes, i.e. min(170, 300).
 *
 * Input:
 *        - ISAC_main_inst    : iSAC instance
 *        - maxPayloadBytes   : maximum size of the payload in bytes
 *                              valid values are between 100 and 400 bytes
 *                              if encoder sampling rate is 16 kHz. For
 *                              32 kHz encoder sampling rate valid values
 *                              are between 100 and 600 bytes.
 *
 * Return value               : 0 if successful
 *                             -1 if error happens
 */
int16_t WebRtcIsac_SetMaxPayloadSize(ISACStruct* ISAC_main_inst,
                                     int16_t maxPayloadBytes) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
  int16_t status = 0;

  /* Check if encoder initiated */
  if ((instISAC->initFlag & BIT_MASK_ENC_INIT) !=
      BIT_MASK_ENC_INIT) {
    instISAC->errorCode = ISAC_ENCODER_NOT_INITIATED;
    return -1;
  }

  if (instISAC->encoderSamplingRateKHz == kIsacSuperWideband) {
    /* Sanity check. */
    if (maxPayloadBytes < 120) {
      /* 'maxRate' is out of valid range
       * set to the acceptable value and return -1. */
      maxPayloadBytes = 120;
      status = -1;
    }

    /* sanity check */
    if (maxPayloadBytes > STREAM_SIZE_MAX) {
      /* maxRate is out of valid range,
       * set to the acceptable value and return -1. */
      maxPayloadBytes = STREAM_SIZE_MAX;
      status = -1;
    }
  } else {
    if (maxPayloadBytes < 120) {
      /* Max payload-size is out of valid range
       * set to the acceptable value and return -1. */
      maxPayloadBytes = 120;
      status = -1;
    }
    if (maxPayloadBytes > STREAM_SIZE_MAX_60) {
      /* Max payload-size is out of valid range
       * set to the acceptable value and return -1. */
      maxPayloadBytes = STREAM_SIZE_MAX_60;
      status = -1;
    }
  }
  instISAC->maxPayloadSizeBytes = maxPayloadBytes;
  UpdatePayloadSizeLimit(instISAC);
  return status;
}


/******************************************************************************
 * WebRtcIsac_SetMaxRate(...)
 *
 * This function sets the maximum rate which the codec may not exceed for
 * any signal packet. The maximum rate is defined and payload-size per
 * frame-size in bits per second.
 *
 * The codec has a maximum rate of 53400 bits per second (200 bytes per 30
 * ms) if the encoder sampling rate is 16kHz, and 160 kbps (600 bytes/30 ms)
 * if the encoder sampling rate is 32 kHz.
 *
 * It is possible to set a maximum rate between 32000 and 53400 bits/sec
 * in wideband mode, and 32000 to 160000 bits/sec in super-wideband mode.
 *
 * ---------------
 * IMPORTANT NOTES
 * ---------------
 * The size of a packet is limited to the minimum of 'max-payload-size' and
 * 'max-rate.' For instance, let's assume the max-payload-size is set to
 * 170 bytes, and max-rate is set to 40 kbps. Note that a limit of 40 kbps
 * translates to 150 bytes for 30ms frame-size & 300 bytes for 60ms
 * frame-size. Then a packet with a frame-size of 30 ms is limited to 150,
 * i.e. min(170, 150), and a packet with 60 ms frame-size is limited to
 * 170 bytes, min(170, 300).
 *
 * Input:
 *        - ISAC_main_inst    : iSAC instance
 *        - maxRate           : maximum rate in bits per second,
 *                              valid values are 32000 to 53400 bits/sec in
 *                              wideband mode, and 32000 to 160000 bits/sec in
 *                              super-wideband mode.
 *
 * Return value               : 0 if successful
 *                             -1 if error happens
 */
int16_t WebRtcIsac_SetMaxRate(ISACStruct* ISAC_main_inst,
                              int32_t maxRate) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
  int16_t maxRateInBytesPer30Ms;
  int16_t status = 0;

  /* check if encoder initiated */
  if ((instISAC->initFlag & BIT_MASK_ENC_INIT) != BIT_MASK_ENC_INIT) {
    instISAC->errorCode = ISAC_ENCODER_NOT_INITIATED;
    return -1;
  }
  /* Calculate maximum number of bytes per 30 msec packets for the
     given maximum rate. Multiply with 30/1000 to get number of
     bits per 30 ms, divide by 8 to get number of bytes per 30 ms:
     maxRateInBytes = floor((maxRate * 30/1000) / 8); */
  maxRateInBytesPer30Ms = (int16_t)(maxRate * 3 / 800);

  if (instISAC->encoderSamplingRateKHz == kIsacWideband) {
    if (maxRate < 32000) {
      /* 'maxRate' is out of valid range.
       * Set to the acceptable value and return -1. */
      maxRateInBytesPer30Ms = 120;
      status = -1;
    }

    if (maxRate > 53400) {
      /* 'maxRate' is out of valid range.
       * Set to the acceptable value and return -1. */
      maxRateInBytesPer30Ms = 200;
      status = -1;
    }
  } else {
    if (maxRateInBytesPer30Ms < 120) {
      /* 'maxRate' is out of valid range
       * Set to the acceptable value and return -1. */
      maxRateInBytesPer30Ms = 120;
      status = -1;
    }

    if (maxRateInBytesPer30Ms > STREAM_SIZE_MAX) {
      /* 'maxRate' is out of valid range.
       * Set to the acceptable value and return -1. */
      maxRateInBytesPer30Ms = STREAM_SIZE_MAX;
      status = -1;
    }
  }
  instISAC->maxRateBytesPer30Ms = maxRateInBytesPer30Ms;
  UpdatePayloadSizeLimit(instISAC);
  return status;
}


/****************************************************************************
 * WebRtcIsac_GetRedPayload(...)
 *
 * This function populates "encoded" with the redundant payload of the recently
 * encodedframe. This function has to be called once that WebRtcIsac_Encode(...)
 * returns a positive value. Regardless of the frame-size this function will
 * be called only once after encoding is completed. The bit-stream is
 * targeted for 16000 bit/sec.
 *
 * Input:
 *        - ISAC_main_inst    : iSAC struct
 *
 * Output:
 *        - encoded           : the encoded data vector
 *
 *
 * Return value               : >0 - Length (in bytes) of coded data
 *                            : -1 - Error
 */
int16_t WebRtcIsac_GetRedPayload(ISACStruct* ISAC_main_inst,
                                 uint8_t* encoded) {
  Bitstr iSACBitStreamInst;
  int16_t streamLenLB;
  int16_t streamLenUB;
  int16_t streamLen;
  int16_t totalLenUB;
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
#ifndef WEBRTC_ARCH_BIG_ENDIAN
  int k;
#endif

  if ((instISAC->initFlag & BIT_MASK_ENC_INIT) !=
      BIT_MASK_ENC_INIT) {
    instISAC->errorCode = ISAC_ENCODER_NOT_INITIATED;
  }

  WebRtcIsac_ResetBitstream(&(iSACBitStreamInst));

  streamLenLB = WebRtcIsac_EncodeStoredDataLb(
                  &instISAC->instLB.ISACencLB_obj.SaveEnc_obj,
                  &iSACBitStreamInst,
                  instISAC->instLB.ISACencLB_obj.lastBWIdx,
                  RCU_TRANSCODING_SCALE);
  if (streamLenLB < 0) {
    return -1;
  }

  /* convert from bytes to int16_t. */
  memcpy(encoded, iSACBitStreamInst.stream, streamLenLB);
  streamLen = streamLenLB;
  if (instISAC->bandwidthKHz == isac8kHz) {
    return streamLenLB;
  }

  streamLenUB = WebRtcIsac_GetRedPayloadUb(
                  &instISAC->instUB.ISACencUB_obj.SaveEnc_obj,
                  &iSACBitStreamInst, instISAC->bandwidthKHz);
  if (streamLenUB < 0) {
    /* An error has happened but this is not the error due to a
     * bit-stream larger than the limit. */
    return -1;
  }

  /* We have one byte to write the total length of the upper-band.
   * The length includes the bit-stream length, check-sum and the
   * single byte where the length is written to. This is according to
   * iSAC wideband and how the "garbage" is dealt. */
  totalLenUB = streamLenUB + 1 + LEN_CHECK_SUM_WORD8;
  if (totalLenUB > 255) {
    streamLenUB = 0;
  }

  /* Generate CRC if required. */
  if ((instISAC->bandwidthKHz != isac8kHz) &&
      (streamLenUB > 0)) {
    uint32_t crc;
    streamLen += totalLenUB;
    encoded[streamLenLB] = (uint8_t)totalLenUB;
    memcpy(&encoded[streamLenLB + 1], iSACBitStreamInst.stream,
           streamLenUB);

    WebRtcIsac_GetCrc((int16_t*)(&(encoded[streamLenLB + 1])),
                      streamLenUB, &crc);
#ifndef WEBRTC_ARCH_BIG_ENDIAN
    for (k = 0; k < LEN_CHECK_SUM_WORD8; k++) {
      encoded[streamLen - LEN_CHECK_SUM_WORD8 + k] =
        (uint8_t)((crc >> (24 - k * 8)) & 0xFF);
    }
#else
    memcpy(&encoded[streamLenLB + streamLenUB + 1], &crc,
           LEN_CHECK_SUM_WORD8);
#endif
  }
  return streamLen;
}


/****************************************************************************
 * WebRtcIsac_version(...)
 *
 * This function returns the version number.
 *
 * Output:
 *        - version      : Pointer to character string
 *
 */
void WebRtcIsac_version(char* version) {
  strcpy(version, "4.3.0");
}


/******************************************************************************
 * WebRtcIsac_SetEncSampRate()
 * This function sets the sampling rate of the encoder. Initialization of the
 * encoder WILL NOT overwrite the sampling rate of the encoder. The default
 * value is 16 kHz which is set when the instance is created. The encoding-mode
 * and the bottleneck remain unchanged by this call, however, the maximum rate
 * and maximum payload-size will be reset to their default values.
 *
 * Input:
 *        - ISAC_main_inst    : iSAC instance
 *        - sample_rate_hz    : sampling rate in Hertz, valid values are 16000
 *                              and 32000.
 *
 * Return value               : 0 if successful
 *                             -1 if failed.
 */
int16_t WebRtcIsac_SetEncSampRate(ISACStruct* ISAC_main_inst,
                                  uint16_t sample_rate_hz) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
  enum IsacSamplingRate encoder_operational_rate;

  if ((sample_rate_hz != 16000) && (sample_rate_hz != 32000)) {
    /* Sampling Frequency is not supported. */
    instISAC->errorCode = ISAC_UNSUPPORTED_SAMPLING_FREQUENCY;
    return -1;
  }
  if (sample_rate_hz == 16000) {
    encoder_operational_rate = kIsacWideband;
  } else {
    encoder_operational_rate = kIsacSuperWideband;
  }

  if ((instISAC->initFlag & BIT_MASK_ENC_INIT) !=
      BIT_MASK_ENC_INIT) {
    if (encoder_operational_rate == kIsacWideband) {
      instISAC->bandwidthKHz = isac8kHz;
    } else {
      instISAC->bandwidthKHz = isac16kHz;
    }
  } else {
    ISACUBStruct* instUB = &(instISAC->instUB);
    ISACLBStruct* instLB = &(instISAC->instLB);
    int32_t bottleneck = instISAC->bottleneck;
    int16_t codingMode = instISAC->codingMode;
    int16_t frameSizeMs = instLB->ISACencLB_obj.new_framelength /
        (FS / 1000);

    if ((encoder_operational_rate == kIsacWideband) &&
        (instISAC->encoderSamplingRateKHz == kIsacSuperWideband)) {
      /* Changing from super-wideband to wideband.
       * we don't need to re-initialize the encoder of the lower-band. */
      instISAC->bandwidthKHz = isac8kHz;
      if (codingMode == 1) {
        ControlLb(instLB,
                  (bottleneck > 32000) ? 32000 : bottleneck, FRAMESIZE);
      }
      instISAC->maxPayloadSizeBytes = STREAM_SIZE_MAX_60;
      instISAC->maxRateBytesPer30Ms = STREAM_SIZE_MAX_30;
    } else if ((encoder_operational_rate == kIsacSuperWideband) &&
               (instISAC->encoderSamplingRateKHz == kIsacWideband)) {
      double bottleneckLB = 0;
      double bottleneckUB = 0;
      if (codingMode == 1) {
        WebRtcIsac_RateAllocation(bottleneck, &bottleneckLB, &bottleneckUB,
                                  &(instISAC->bandwidthKHz));
      }

      instISAC->bandwidthKHz = isac16kHz;
      instISAC->maxPayloadSizeBytes = STREAM_SIZE_MAX;
      instISAC->maxRateBytesPer30Ms = STREAM_SIZE_MAX;

      EncoderInitLb(instLB, codingMode, encoder_operational_rate);
      EncoderInitUb(instUB, instISAC->bandwidthKHz);

      memset(instISAC->analysisFBState1, 0,
             FB_STATE_SIZE_WORD32 * sizeof(int32_t));
      memset(instISAC->analysisFBState2, 0,
             FB_STATE_SIZE_WORD32 * sizeof(int32_t));

      if (codingMode == 1) {
        instISAC->bottleneck = bottleneck;
        ControlLb(instLB, bottleneckLB,
                  (instISAC->bandwidthKHz == isac8kHz) ? frameSizeMs:FRAMESIZE);
        if (instISAC->bandwidthKHz > isac8kHz) {
          ControlUb(instUB, bottleneckUB);
        }
      } else {
        instLB->ISACencLB_obj.enforceFrameSize = 0;
        instLB->ISACencLB_obj.new_framelength = FRAMESAMPLES;
      }
    }
  }
  instISAC->encoderSamplingRateKHz = encoder_operational_rate;
  instISAC->in_sample_rate_hz = sample_rate_hz;
  return 0;
}


/******************************************************************************
 * WebRtcIsac_SetDecSampRate()
 * This function sets the sampling rate of the decoder. Initialization of the
 * decoder WILL NOT overwrite the sampling rate of the encoder. The default
 * value is 16 kHz which is set when the instance is created.
 *
 * Input:
 *        - ISAC_main_inst    : iSAC instance
 *        - sample_rate_hz    : sampling rate in Hertz, valid values are 16000
 *                              and 32000.
 *
 * Return value               : 0 if successful
 *                             -1 if failed.
 */
int16_t WebRtcIsac_SetDecSampRate(ISACStruct* ISAC_main_inst,
                                  uint16_t sample_rate_hz) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
  enum IsacSamplingRate decoder_operational_rate;

  if (sample_rate_hz == 16000) {
    decoder_operational_rate = kIsacWideband;
  } else if (sample_rate_hz == 32000) {
    decoder_operational_rate = kIsacSuperWideband;
  } else {
    /* Sampling Frequency is not supported. */
    instISAC->errorCode = ISAC_UNSUPPORTED_SAMPLING_FREQUENCY;
    return -1;
  }

  if ((instISAC->decoderSamplingRateKHz == kIsacWideband) &&
        (decoder_operational_rate == kIsacSuperWideband)) {
      /* Switching from wideband to super-wideband at the decoder
       * we need to reset the filter-bank and initialize upper-band decoder. */
      memset(instISAC->synthesisFBState1, 0,
             FB_STATE_SIZE_WORD32 * sizeof(int32_t));
      memset(instISAC->synthesisFBState2, 0,
             FB_STATE_SIZE_WORD32 * sizeof(int32_t));

      DecoderInitUb(&instISAC->instUB);
  }
  instISAC->decoderSamplingRateKHz = decoder_operational_rate;
  return 0;
}


/******************************************************************************
 * WebRtcIsac_EncSampRate()
 *
 * Input:
 *        - ISAC_main_inst    : iSAC instance
 *
 * Return value               : sampling rate in Hertz. The input to encoder
 *                              is expected to be sampled in this rate.
 *
 */
uint16_t WebRtcIsac_EncSampRate(ISACStruct* ISAC_main_inst) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
  return instISAC->in_sample_rate_hz;
}


/******************************************************************************
 * WebRtcIsac_DecSampRate()
 * Return the sampling rate of the decoded audio.
 *
 * Input:
 *        - ISAC_main_inst    : iSAC instance
 *
 * Return value               : sampling rate in Hertz. Decoder output is
 *                              sampled at this rate.
 *
 */
uint16_t WebRtcIsac_DecSampRate(ISACStruct* ISAC_main_inst) {
  ISACMainStruct* instISAC = (ISACMainStruct*)ISAC_main_inst;
  return instISAC->decoderSamplingRateKHz == kIsacWideband ? 16000 : 32000;
}

void WebRtcIsac_GetBandwidthInfo(ISACStruct* inst,
                                 IsacBandwidthInfo* bwinfo) {
  ISACMainStruct* instISAC = (ISACMainStruct*)inst;
  RTC_DCHECK_NE(0, instISAC->initFlag & BIT_MASK_DEC_INIT);
  WebRtcIsacBw_GetBandwidthInfo(&instISAC->bwestimator_obj,
                                instISAC->decoderSamplingRateKHz, bwinfo);
}

void WebRtcIsac_SetBandwidthInfo(ISACStruct* inst,
                                 const IsacBandwidthInfo* bwinfo) {
  ISACMainStruct* instISAC = (ISACMainStruct*)inst;
  RTC_DCHECK_NE(0, instISAC->initFlag & BIT_MASK_ENC_INIT);
  WebRtcIsacBw_SetBandwidthInfo(&instISAC->bwestimator_obj, bwinfo);
}

void WebRtcIsac_SetEncSampRateInDecoder(ISACStruct* inst,
                                        int sample_rate_hz) {
  ISACMainStruct* instISAC = (ISACMainStruct*)inst;
  RTC_DCHECK_NE(0, instISAC->initFlag & BIT_MASK_DEC_INIT);
  RTC_DCHECK(!(instISAC->initFlag & BIT_MASK_ENC_INIT));
  RTC_DCHECK(sample_rate_hz == 16000 || sample_rate_hz == 32000);
  instISAC->encoderSamplingRateKHz = sample_rate_hz / 1000;
}

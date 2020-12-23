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
 * isacfix.c
 *
 * This C file contains the functions for the ISAC API
 *
 */

#include "modules/audio_coding/codecs/isac/fix/include/isacfix.h"

#include <stdlib.h>

#include "rtc_base/checks.h"
#include "modules/audio_coding/codecs/isac/fix/source/bandwidth_estimator.h"
#include "modules/audio_coding/codecs/isac/fix/source/codec.h"
#include "modules/audio_coding/codecs/isac/fix/source/entropy_coding.h"
#include "modules/audio_coding/codecs/isac/fix/source/filterbank_internal.h"
#include "modules/audio_coding/codecs/isac/fix/source/lpc_masking_model.h"
#include "modules/audio_coding/codecs/isac/fix/source/structs.h"

// Declare function pointers.
FilterMaLoopFix WebRtcIsacfix_FilterMaLoopFix;
Spec2Time WebRtcIsacfix_Spec2Time;
Time2Spec WebRtcIsacfix_Time2Spec;
MatrixProduct1 WebRtcIsacfix_MatrixProduct1;
MatrixProduct2 WebRtcIsacfix_MatrixProduct2;

/* This method assumes that |stream_size_bytes| is in valid range,
 * i.e. >= 0 && <=  STREAM_MAXW16_60MS
 */
static void InitializeDecoderBitstream(size_t stream_size_bytes,
                                       Bitstr_dec* bitstream) {
  bitstream->W_upper = 0xFFFFFFFF;
  bitstream->streamval = 0;
  bitstream->stream_index = 0;
  bitstream->full = 1;
  bitstream->stream_size = (stream_size_bytes + 1) >> 1;
  memset(bitstream->stream, 0, sizeof(bitstream->stream));
}

/****************************************************************************
 * WebRtcIsacfix_Create(...)
 *
 * This function creates a ISAC instance, which will contain the state
 * information for one coding/decoding channel.
 *
 * Input:
 *      - *ISAC_main_inst   : a pointer to the coder instance.
 *
 * Return value             :  0 - Ok
 *                            -1 - Error
 */

int16_t WebRtcIsacfix_Create(ISACFIX_MainStruct **ISAC_main_inst)
{
  ISACFIX_SubStruct *tempo;
  tempo = malloc(1 * sizeof(ISACFIX_SubStruct));
  *ISAC_main_inst = (ISACFIX_MainStruct *)tempo;
  if (*ISAC_main_inst!=NULL) {
    (*(ISACFIX_SubStruct**)ISAC_main_inst)->errorcode = 0;
    (*(ISACFIX_SubStruct**)ISAC_main_inst)->initflag = 0;
    (*(ISACFIX_SubStruct**)ISAC_main_inst)->ISACenc_obj.SaveEnc_ptr = NULL;
    WebRtcIsacfix_InitBandwidthEstimator(&tempo->bwestimator_obj);
    return(0);
  } else {
    return(-1);
  }
}


/****************************************************************************
 * WebRtcIsacfix_CreateInternal(...)
 *
 * This function creates the memory that is used to store data in the encoder
 *
 * Input:
 *      - *ISAC_main_inst   : a pointer to the coder instance.
 *
 * Return value             :  0 - Ok
 *                            -1 - Error
 */

int16_t WebRtcIsacfix_CreateInternal(ISACFIX_MainStruct *ISAC_main_inst)
{
  ISACFIX_SubStruct *ISAC_inst;

  /* typecast pointer to real structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;

  /* Allocate memory for storing encoder data */
  ISAC_inst->ISACenc_obj.SaveEnc_ptr = malloc(1 * sizeof(IsacSaveEncoderData));

  if (ISAC_inst->ISACenc_obj.SaveEnc_ptr!=NULL) {
    return(0);
  } else {
    return(-1);
  }
}


/****************************************************************************
 * WebRtcIsacfix_Free(...)
 *
 * This function frees the ISAC instance created at the beginning.
 *
 * Input:
 *      - ISAC_main_inst    : a ISAC instance.
 *
 * Return value             :  0 - Ok
 *                            -1 - Error
 */

int16_t WebRtcIsacfix_Free(ISACFIX_MainStruct *ISAC_main_inst)
{
  free(ISAC_main_inst);
  return(0);
}

/****************************************************************************
 * WebRtcIsacfix_FreeInternal(...)
 *
 * This function frees the internal memory for storing encoder data.
 *
 * Input:
 *       - ISAC_main_inst    : a ISAC instance.
 *
 * Return value              :  0 - Ok
 *                             -1 - Error
 */

int16_t WebRtcIsacfix_FreeInternal(ISACFIX_MainStruct *ISAC_main_inst)
{
  ISACFIX_SubStruct *ISAC_inst;

  /* typecast pointer to real structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;

  /* Release memory */
  free(ISAC_inst->ISACenc_obj.SaveEnc_ptr);

  return(0);
}

/****************************************************************************
 * WebRtcIsacfix_InitNeon(...)
 *
 * This function initializes function pointers for ARM Neon platform.
 */

#if defined(WEBRTC_HAS_NEON)
static void WebRtcIsacfix_InitNeon(void) {
  WebRtcIsacfix_AutocorrFix = WebRtcIsacfix_AutocorrNeon;
  WebRtcIsacfix_FilterMaLoopFix = WebRtcIsacfix_FilterMaLoopNeon;
  WebRtcIsacfix_Spec2Time = WebRtcIsacfix_Spec2TimeNeon;
  WebRtcIsacfix_Time2Spec = WebRtcIsacfix_Time2SpecNeon;
  WebRtcIsacfix_AllpassFilter2FixDec16 =
      WebRtcIsacfix_AllpassFilter2FixDec16Neon;
  WebRtcIsacfix_MatrixProduct1 = WebRtcIsacfix_MatrixProduct1Neon;
  WebRtcIsacfix_MatrixProduct2 = WebRtcIsacfix_MatrixProduct2Neon;
}
#endif

/****************************************************************************
 * WebRtcIsacfix_InitMIPS(...)
 *
 * This function initializes function pointers for MIPS platform.
 */

#if defined(MIPS32_LE)
static void WebRtcIsacfix_InitMIPS(void) {
  WebRtcIsacfix_AutocorrFix = WebRtcIsacfix_AutocorrMIPS;
  WebRtcIsacfix_FilterMaLoopFix = WebRtcIsacfix_FilterMaLoopMIPS;
  WebRtcIsacfix_Spec2Time = WebRtcIsacfix_Spec2TimeMIPS;
  WebRtcIsacfix_Time2Spec = WebRtcIsacfix_Time2SpecMIPS;
  WebRtcIsacfix_MatrixProduct1 = WebRtcIsacfix_MatrixProduct1MIPS;
  WebRtcIsacfix_MatrixProduct2 = WebRtcIsacfix_MatrixProduct2MIPS;
#if defined(MIPS_DSP_R1_LE)
  WebRtcIsacfix_AllpassFilter2FixDec16 =
      WebRtcIsacfix_AllpassFilter2FixDec16MIPS;
  WebRtcIsacfix_HighpassFilterFixDec32 =
      WebRtcIsacfix_HighpassFilterFixDec32MIPS;
#endif
#if defined(MIPS_DSP_R2_LE)
  WebRtcIsacfix_CalculateResidualEnergy =
      WebRtcIsacfix_CalculateResidualEnergyMIPS;
#endif
}
#endif

static void InitFunctionPointers(void) {
  WebRtcIsacfix_AutocorrFix = WebRtcIsacfix_AutocorrC;
  WebRtcIsacfix_FilterMaLoopFix = WebRtcIsacfix_FilterMaLoopC;
  WebRtcIsacfix_CalculateResidualEnergy =
      WebRtcIsacfix_CalculateResidualEnergyC;
  WebRtcIsacfix_AllpassFilter2FixDec16 = WebRtcIsacfix_AllpassFilter2FixDec16C;
  WebRtcIsacfix_HighpassFilterFixDec32 = WebRtcIsacfix_HighpassFilterFixDec32C;
  WebRtcIsacfix_Time2Spec = WebRtcIsacfix_Time2SpecC;
  WebRtcIsacfix_Spec2Time = WebRtcIsacfix_Spec2TimeC;
  WebRtcIsacfix_MatrixProduct1 = WebRtcIsacfix_MatrixProduct1C;
  WebRtcIsacfix_MatrixProduct2 = WebRtcIsacfix_MatrixProduct2C;

#if defined(WEBRTC_HAS_NEON)
  WebRtcIsacfix_InitNeon();
#endif

#if defined(MIPS32_LE)
  WebRtcIsacfix_InitMIPS();
#endif
}

/****************************************************************************
 * WebRtcIsacfix_EncoderInit(...)
 *
 * This function initializes a ISAC instance prior to the encoder calls.
 *
 * Input:
 *      - ISAC_main_inst    : ISAC instance.
 *      - CodingMode        : 0 -> Bit rate and frame length are automatically
 *                                 adjusted to available bandwidth on
 *                                 transmission channel.
 *                            1 -> User sets a frame length and a target bit
 *                                 rate which is taken as the maximum short-term
 *                                 average bit rate.
 *
 * Return value             :  0 - Ok
 *                            -1 - Error
 */

int16_t WebRtcIsacfix_EncoderInit(ISACFIX_MainStruct *ISAC_main_inst,
                                  int16_t  CodingMode)
{
  int k;
  int16_t statusInit;
  ISACFIX_SubStruct *ISAC_inst;

  statusInit = 0;
  /* typecast pointer to rela structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;

  /* flag encoder init */
  ISAC_inst->initflag |= 2;

  if (CodingMode == 0)
    /* Adaptive mode */
    ISAC_inst->ISACenc_obj.new_framelength  = INITIAL_FRAMESAMPLES;
  else if (CodingMode == 1)
    /* Instantaneous mode */
    ISAC_inst->ISACenc_obj.new_framelength = 480;    /* default for I-mode */
  else {
    ISAC_inst->errorcode = ISAC_DISALLOWED_CODING_MODE;
    statusInit = -1;
  }

  ISAC_inst->CodingMode = CodingMode;

  WebRtcIsacfix_InitMaskingEnc(&ISAC_inst->ISACenc_obj.maskfiltstr_obj);
  WebRtcIsacfix_InitPreFilterbank(&ISAC_inst->ISACenc_obj.prefiltbankstr_obj);
  WebRtcIsacfix_InitPitchFilter(&ISAC_inst->ISACenc_obj.pitchfiltstr_obj);
  WebRtcIsacfix_InitPitchAnalysis(&ISAC_inst->ISACenc_obj.pitchanalysisstr_obj);

  WebRtcIsacfix_InitRateModel(&ISAC_inst->ISACenc_obj.rate_data_obj);


  ISAC_inst->ISACenc_obj.buffer_index   = 0;
  ISAC_inst->ISACenc_obj.frame_nb    = 0;
  ISAC_inst->ISACenc_obj.BottleNeck      = 32000; /* default for I-mode */
  ISAC_inst->ISACenc_obj.MaxDelay    = 10;    /* default for I-mode */
  ISAC_inst->ISACenc_obj.current_framesamples = 0;
  ISAC_inst->ISACenc_obj.s2nr     = 0;
  ISAC_inst->ISACenc_obj.MaxBits    = 0;
  ISAC_inst->ISACenc_obj.bitstr_seed   = 4447;
  ISAC_inst->ISACenc_obj.payloadLimitBytes30  = STREAM_MAXW16_30MS << 1;
  ISAC_inst->ISACenc_obj.payloadLimitBytes60  = STREAM_MAXW16_60MS << 1;
  ISAC_inst->ISACenc_obj.maxPayloadBytes      = STREAM_MAXW16_60MS << 1;
  ISAC_inst->ISACenc_obj.maxRateInBytes       = STREAM_MAXW16_30MS << 1;
  ISAC_inst->ISACenc_obj.enforceFrameSize     = 0;

  /* Init the bistream data area to zero */
  for (k=0; k<STREAM_MAXW16_60MS; k++){
    ISAC_inst->ISACenc_obj.bitstr_obj.stream[k] = 0;
  }

  InitFunctionPointers();

  return statusInit;
}

/* Read the given number of bytes of big-endian 16-bit integers from |src| and
   write them to |dest| in host endian. If |nbytes| is odd, the number of
   output elements is rounded up, and the least significant byte of the last
   element is set to 0. */
static void read_be16(const uint8_t* src, size_t nbytes, uint16_t* dest) {
  size_t i;
  for (i = 0; i < nbytes / 2; ++i)
    dest[i] = src[2 * i] << 8 | src[2 * i + 1];
  if (nbytes % 2 == 1)
    dest[nbytes / 2] = src[nbytes - 1] << 8;
}

/* Read the given number of bytes of host-endian 16-bit integers from |src| and
   write them to |dest| in big endian. If |nbytes| is odd, the number of source
   elements is rounded up (but only the most significant byte of the last
   element is used), and the number of output bytes written will be
   nbytes + 1. */
static void write_be16(const uint16_t* src, size_t nbytes, uint8_t* dest) {
  size_t i;
  for (i = 0; i < nbytes / 2; ++i) {
    dest[2 * i] = src[i] >> 8;
    dest[2 * i + 1] = src[i];
  }
  if (nbytes % 2 == 1) {
    dest[nbytes - 1] = src[nbytes / 2] >> 8;
    dest[nbytes] = 0;
  }
}

/****************************************************************************
 * WebRtcIsacfix_Encode(...)
 *
 * This function encodes 10ms frame(s) and inserts it into a package.
 * Input speech length has to be 160 samples (10ms). The encoder buffers those
 * 10ms frames until it reaches the chosen Framesize (480 or 960 samples
 * corresponding to 30 or 60 ms frames), and then proceeds to the encoding.
 *
 * Input:
 *      - ISAC_main_inst    : ISAC instance.
 *      - speechIn          : input speech vector.
 *
 * Output:
 *      - encoded           : the encoded data vector
 *
 * Return value:
 *                          : >0 - Length (in bytes) of coded data
 *                          :  0 - The buffer didn't reach the chosen framesize
 *                            so it keeps buffering speech samples.
 *                          : -1 - Error
 */

int WebRtcIsacfix_Encode(ISACFIX_MainStruct *ISAC_main_inst,
                         const int16_t    *speechIn,
                         uint8_t* encoded)
{
  ISACFIX_SubStruct *ISAC_inst;
  int stream_len;

  /* typecast pointer to rela structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;


  /* check if encoder initiated */
  if ((ISAC_inst->initflag & 2) != 2) {
    ISAC_inst->errorcode = ISAC_ENCODER_NOT_INITIATED;
    return (-1);
  }

  stream_len = WebRtcIsacfix_EncodeImpl((int16_t*)speechIn,
                                        &ISAC_inst->ISACenc_obj,
                                        &ISAC_inst->bwestimator_obj,
                                        ISAC_inst->CodingMode);
  if (stream_len<0) {
    ISAC_inst->errorcode = -(int16_t)stream_len;
    return -1;
  }

  write_be16(ISAC_inst->ISACenc_obj.bitstr_obj.stream, (size_t)stream_len,
             encoded);
  return stream_len;

}


/****************************************************************************
 * WebRtcIsacfix_GetNewBitStream(...)
 *
 * This function returns encoded data, with the recieved bwe-index in the
 * stream. It should always return a complete packet, i.e. only called once
 * even for 60 msec frames
 *
 * Input:
 *      - ISAC_main_inst    : ISAC instance.
 *      - bweIndex          : index of bandwidth estimate to put in new bitstream
 *
 * Output:
 *      - encoded           : the encoded data vector
 *
 * Return value:
 *                          : >0 - Length (in bytes) of coded data
 *                          : -1 - Error
 */

int16_t WebRtcIsacfix_GetNewBitStream(ISACFIX_MainStruct *ISAC_main_inst,
                                      int16_t      bweIndex,
                                      float              scale,
                                      uint8_t* encoded)
{
  ISACFIX_SubStruct *ISAC_inst;
  int16_t stream_len;

  /* typecast pointer to rela structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;


  /* check if encoder initiated */
  if ((ISAC_inst->initflag & 2) != 2) {
    ISAC_inst->errorcode = ISAC_ENCODER_NOT_INITIATED;
    return (-1);
  }

  stream_len = WebRtcIsacfix_EncodeStoredData(&ISAC_inst->ISACenc_obj,
                                              bweIndex,
                                              scale);
  if (stream_len<0) {
    ISAC_inst->errorcode = - stream_len;
    return -1;
  }

  write_be16(ISAC_inst->ISACenc_obj.bitstr_obj.stream, stream_len, encoded);
  return stream_len;
}



/****************************************************************************
 * WebRtcIsacfix_DecoderInit(...)
 *
 * This function initializes a ISAC instance prior to the decoder calls.
 *
 * Input:
 *      - ISAC_main_inst    : ISAC instance.
 */

void WebRtcIsacfix_DecoderInit(ISACFIX_MainStruct *ISAC_main_inst)
{
  ISACFIX_SubStruct *ISAC_inst;

  InitFunctionPointers();

  /* typecast pointer to real structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;

  /* flag decoder init */
  ISAC_inst->initflag |= 1;

  WebRtcIsacfix_InitMaskingDec(&ISAC_inst->ISACdec_obj.maskfiltstr_obj);
  WebRtcIsacfix_InitPostFilterbank(&ISAC_inst->ISACdec_obj.postfiltbankstr_obj);
  WebRtcIsacfix_InitPitchFilter(&ISAC_inst->ISACdec_obj.pitchfiltstr_obj);

  /* TS */
  WebRtcIsacfix_InitPlc( &ISAC_inst->ISACdec_obj.plcstr_obj );
}


/****************************************************************************
 * WebRtcIsacfix_UpdateBwEstimate1(...)
 *
 * This function updates the estimate of the bandwidth.
 *
 * Input:
 *      - ISAC_main_inst    : ISAC instance.
 *      - encoded           : encoded ISAC frame(s).
 *      - packet_size       : size of the packet.
 *      - rtp_seq_number    : the RTP number of the packet.
 *      - arr_ts            : the arrival time of the packet (from NetEq)
 *                            in samples.
 *
 * Return value             :  0 - Ok
 *                            -1 - Error
 */

int16_t WebRtcIsacfix_UpdateBwEstimate1(ISACFIX_MainStruct *ISAC_main_inst,
                                        const uint8_t* encoded,
                                        size_t packet_size,
                                        uint16_t rtp_seq_number,
                                        uint32_t arr_ts)
{
  ISACFIX_SubStruct *ISAC_inst;
  Bitstr_dec streamdata;
  int16_t err;
  const size_t kRequiredEncodedLenBytes = 10;

  /* typecast pointer to real structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;

  /* Sanity check of packet length */
  if (packet_size == 0) {
    /* return error code if the packet length is null or less */
    ISAC_inst->errorcode = ISAC_EMPTY_PACKET;
    return -1;
  } else if (packet_size > (STREAM_MAXW16<<1)) {
    /* return error code if length of stream is too long */
    ISAC_inst->errorcode = ISAC_LENGTH_MISMATCH;
    return -1;
  }

  /* check if decoder initiated */
  if ((ISAC_inst->initflag & 1) != 1) {
    ISAC_inst->errorcode = ISAC_DECODER_NOT_INITIATED;
    return (-1);
  }

  InitializeDecoderBitstream(packet_size, &streamdata);

  read_be16(encoded, kRequiredEncodedLenBytes, streamdata.stream);

  err = WebRtcIsacfix_EstimateBandwidth(&ISAC_inst->bwestimator_obj,
                                        &streamdata,
                                        packet_size,
                                        rtp_seq_number,
                                        0,
                                        arr_ts);


  if (err < 0)
  {
    /* return error code if something went wrong */
    ISAC_inst->errorcode = -err;
    return -1;
  }


  return 0;
}

/****************************************************************************
 * WebRtcIsacfix_UpdateBwEstimate(...)
 *
 * This function updates the estimate of the bandwidth.
 *
 * Input:
 *      - ISAC_main_inst    : ISAC instance.
 *      - encoded           : encoded ISAC frame(s).
 *      - packet_size       : size of the packet.
 *      - rtp_seq_number    : the RTP number of the packet.
 *      - send_ts           : Send Time Stamp from RTP header
 *      - arr_ts            : the arrival time of the packet (from NetEq)
 *                            in samples.
 *
 * Return value             :  0 - Ok
 *                            -1 - Error
 */

int16_t WebRtcIsacfix_UpdateBwEstimate(ISACFIX_MainStruct *ISAC_main_inst,
                                       const uint8_t* encoded,
                                       size_t packet_size,
                                       uint16_t rtp_seq_number,
                                       uint32_t send_ts,
                                       uint32_t arr_ts)
{
  ISACFIX_SubStruct *ISAC_inst;
  Bitstr_dec streamdata;
  int16_t err;
  const size_t kRequiredEncodedLenBytes = 10;

  /* typecast pointer to real structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;

  /* Sanity check of packet length */
  if (packet_size == 0) {
    /* return error code if the packet length is null  or less */
    ISAC_inst->errorcode = ISAC_EMPTY_PACKET;
    return -1;
  } else if (packet_size < kRequiredEncodedLenBytes) {
    ISAC_inst->errorcode = ISAC_PACKET_TOO_SHORT;
    return -1;
  } else if (packet_size > (STREAM_MAXW16<<1)) {
    /* return error code if length of stream is too long */
    ISAC_inst->errorcode = ISAC_LENGTH_MISMATCH;
    return -1;
  }

  /* check if decoder initiated */
  if ((ISAC_inst->initflag & 1) != 1) {
    ISAC_inst->errorcode = ISAC_DECODER_NOT_INITIATED;
    return (-1);
  }

  InitializeDecoderBitstream(packet_size, &streamdata);

  read_be16(encoded, kRequiredEncodedLenBytes, streamdata.stream);

  err = WebRtcIsacfix_EstimateBandwidth(&ISAC_inst->bwestimator_obj,
                                        &streamdata,
                                        packet_size,
                                        rtp_seq_number,
                                        send_ts,
                                        arr_ts);

  if (err < 0)
  {
    /* return error code if something went wrong */
    ISAC_inst->errorcode = -err;
    return -1;
  }


  return 0;
}

/****************************************************************************
 * WebRtcIsacfix_Decode(...)
 *
 * This function decodes a ISAC frame. Output speech length
 * will be a multiple of 480 samples: 480 or 960 samples,
 * depending on the framesize (30 or 60 ms).
 *
 * Input:
 *      - ISAC_main_inst    : ISAC instance.
 *      - encoded           : encoded ISAC frame(s)
 *      - len               : bytes in encoded vector
 *
 * Output:
 *      - decoded           : The decoded vector
 *
 * Return value             : >0 - number of samples in decoded vector
 *                            -1 - Error
 */


int WebRtcIsacfix_Decode(ISACFIX_MainStruct* ISAC_main_inst,
                         const uint8_t* encoded,
                         size_t len,
                         int16_t* decoded,
                         int16_t* speechType)
{
  ISACFIX_SubStruct *ISAC_inst;
  /* number of samples (480 or 960), output from decoder */
  /* that were actually used in the encoder/decoder (determined on the fly) */
  size_t number_of_samples;
  int declen_int = 0;
  size_t declen;

  /* typecast pointer to real structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;

  /* check if decoder initiated */
  if ((ISAC_inst->initflag & 1) != 1) {
    ISAC_inst->errorcode = ISAC_DECODER_NOT_INITIATED;
    return (-1);
  }

  /* Sanity check of packet length */
  if (len == 0) {
    /* return error code if the packet length is null  or less */
    ISAC_inst->errorcode = ISAC_EMPTY_PACKET;
    return -1;
  } else if (len > (STREAM_MAXW16<<1)) {
    /* return error code if length of stream is too long */
    ISAC_inst->errorcode = ISAC_LENGTH_MISMATCH;
    return -1;
  }

  InitializeDecoderBitstream(len, &ISAC_inst->ISACdec_obj.bitstr_obj);

  read_be16(encoded, len, ISAC_inst->ISACdec_obj.bitstr_obj.stream);

  /* added for NetEq purposes (VAD/DTX related) */
  *speechType=1;

  declen_int = WebRtcIsacfix_DecodeImpl(decoded, &ISAC_inst->ISACdec_obj,
                                        &number_of_samples);
  if (declen_int < 0) {
    /* Some error inside the decoder */
    ISAC_inst->errorcode = -(int16_t)declen_int;
    memset(decoded, 0, sizeof(int16_t) * MAX_FRAMESAMPLES);
    return -1;
  }
  declen = (size_t)declen_int;

  /* error check */

  if (declen & 1) {
    if (len != declen &&
        len != declen +
            ((ISAC_inst->ISACdec_obj.bitstr_obj.stream[declen >> 1]) & 0xFF)) {
      ISAC_inst->errorcode = ISAC_LENGTH_MISMATCH;
      memset(decoded, 0, sizeof(int16_t) * number_of_samples);
      return -1;
    }
  } else {
    if (len != declen &&
        len != declen +
            ((ISAC_inst->ISACdec_obj.bitstr_obj.stream[declen >> 1]) >> 8)) {
      ISAC_inst->errorcode = ISAC_LENGTH_MISMATCH;
      memset(decoded, 0, sizeof(int16_t) * number_of_samples);
      return -1;
    }
  }

  return (int)number_of_samples;
}


/****************************************************************************
 * WebRtcIsacfix_DecodePlc(...)
 *
 * This function conducts PLC for ISAC frame(s) in wide-band (16kHz sampling).
 * Output speech length  will be "480*noOfLostFrames" samples
 * that is equevalent of "30*noOfLostFrames" millisecond.
 *
 * Input:
 *      - ISAC_main_inst    : ISAC instance.
 *      - noOfLostFrames    : Number of PLC frames (480sample = 30ms)
 *                                to produce
 *
 * Output:
 *      - decoded           : The decoded vector
 *
 * Return value             : Number of samples in decoded PLC vector
 */

size_t WebRtcIsacfix_DecodePlc(ISACFIX_MainStruct* ISAC_main_inst,
                               int16_t* decoded,
                               size_t noOfLostFrames)
{

  size_t no_of_samples, declen, k;
  int16_t outframe16[MAX_FRAMESAMPLES];

  ISACFIX_SubStruct *ISAC_inst;
  /* typecast pointer to real structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;

  /* Limit number of frames to two = 60 msec. Otherwise we exceed data vectors */
  if (noOfLostFrames > 2) {
    noOfLostFrames = 2;
  }
  k = 0;
  declen = 0;
  while( noOfLostFrames > 0 )
  {
    WebRtcIsacfix_DecodePlcImpl(&(outframe16[k*480]), &ISAC_inst->ISACdec_obj,
                                &no_of_samples);
    declen += no_of_samples;
    noOfLostFrames--;
    k++;
  }

  for (k=0;k<declen;k++) {
    decoded[k] = outframe16[k];
  }

  return declen;
}


/****************************************************************************
 * WebRtcIsacfix_Control(...)
 *
 * This function sets the limit on the short-term average bit rate and the
 * frame length. Should be used only in Instantaneous mode.
 *
 * Input:
 *      - ISAC_main_inst    : ISAC instance.
 *      - rate              : limit on the short-term average bit rate,
 *                            in bits/second (between 10000 and 32000)
 *      - framesize         : number of milliseconds per frame (30 or 60)
 *
 * Return value             : 0  - ok
 *                            -1 - Error
 */

int16_t WebRtcIsacfix_Control(ISACFIX_MainStruct *ISAC_main_inst,
                              int16_t rate,
                              int framesize)
{
  ISACFIX_SubStruct *ISAC_inst;
  /* typecast pointer to real structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;

  if (ISAC_inst->CodingMode == 0)
  {
    /* in adaptive mode */
    ISAC_inst->errorcode = ISAC_MODE_MISMATCH;
    return -1;
  }


  if (rate >= 10000 && rate <= 32000)
    ISAC_inst->ISACenc_obj.BottleNeck = rate;
  else {
    ISAC_inst->errorcode = ISAC_DISALLOWED_BOTTLENECK;
    return -1;
  }



  if (framesize  == 30 || framesize == 60)
    ISAC_inst->ISACenc_obj.new_framelength = (int16_t)((FS/1000) * framesize);
  else {
    ISAC_inst->errorcode = ISAC_DISALLOWED_FRAME_LENGTH;
    return -1;
  }

  return 0;
}

void WebRtcIsacfix_SetInitialBweBottleneck(ISACFIX_MainStruct* ISAC_main_inst,
                                           int bottleneck_bits_per_second) {
  ISACFIX_SubStruct* inst = (ISACFIX_SubStruct*)ISAC_main_inst;
  RTC_DCHECK_GE(bottleneck_bits_per_second, 10000);
  RTC_DCHECK_LE(bottleneck_bits_per_second, 32000);
  inst->bwestimator_obj.sendBwAvg = ((uint32_t)bottleneck_bits_per_second) << 7;
}

/****************************************************************************
 * WebRtcIsacfix_ControlBwe(...)
 *
 * This function sets the initial values of bottleneck and frame-size if
 * iSAC is used in channel-adaptive mode. Through this API, users can
 * enforce a frame-size for all values of bottleneck. Then iSAC will not
 * automatically change the frame-size.
 *
 *
 * Input:
 *  - ISAC_main_inst : ISAC instance.
 *      - rateBPS           : initial value of bottleneck in bits/second
 *                            10000 <= rateBPS <= 32000 is accepted
 *                            For default bottleneck set rateBPS = 0
 *      - frameSizeMs       : number of milliseconds per frame (30 or 60)
 *      - enforceFrameSize  : 1 to enforce the given frame-size through out
 *                            the adaptation process, 0 to let iSAC change
 *                            the frame-size if required.
 *
 * Return value    : 0  - ok
 *         -1 - Error
 */

int16_t WebRtcIsacfix_ControlBwe(ISACFIX_MainStruct *ISAC_main_inst,
                                 int16_t rateBPS,
                                 int frameSizeMs,
                                 int16_t enforceFrameSize)
{
  ISACFIX_SubStruct *ISAC_inst;
  /* Typecast pointer to real structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;

  /* check if encoder initiated */
  if ((ISAC_inst->initflag & 2) != 2) {
    ISAC_inst->errorcode = ISAC_ENCODER_NOT_INITIATED;
    return (-1);
  }

  /* Check that we are in channel-adaptive mode, otherwise, return -1 */
  if (ISAC_inst->CodingMode != 0) {
    ISAC_inst->errorcode = ISAC_MODE_MISMATCH;
    return (-1);
  }

  /* Set struct variable if enforceFrameSize is set. ISAC will then keep the */
  /* chosen frame size.                                                      */
  ISAC_inst->ISACenc_obj.enforceFrameSize = (enforceFrameSize != 0)? 1:0;

  /* Set initial rate, if value between 10000 and 32000,                */
  /* if rateBPS is 0, keep the default initial bottleneck value (15000) */
  if ((rateBPS >= 10000) && (rateBPS <= 32000)) {
    ISAC_inst->bwestimator_obj.sendBwAvg = (((uint32_t)rateBPS) << 7);
  } else if (rateBPS != 0) {
    ISAC_inst->errorcode = ISAC_DISALLOWED_BOTTLENECK;
    return -1;
  }

  /* Set initial framesize. If enforceFrameSize is set the frame size will not change */
  if ((frameSizeMs  == 30) || (frameSizeMs == 60)) {
    ISAC_inst->ISACenc_obj.new_framelength = (int16_t)((FS/1000) * frameSizeMs);
  } else {
    ISAC_inst->errorcode = ISAC_DISALLOWED_FRAME_LENGTH;
    return -1;
  }

  return 0;
}





/****************************************************************************
 * WebRtcIsacfix_GetDownLinkBwIndex(...)
 *
 * This function returns index representing the Bandwidth estimate from
 * other side to this side.
 *
 * Input:
 *      - ISAC_main_inst: iSAC struct
 *
 * Output:
 *      - rateIndex     : Bandwidth estimate to transmit to other side.
 *
 */

int16_t WebRtcIsacfix_GetDownLinkBwIndex(ISACFIX_MainStruct* ISAC_main_inst,
                                         int16_t*     rateIndex)
{
  ISACFIX_SubStruct *ISAC_inst;

  /* typecast pointer to real structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;

  /* Call function to get Bandwidth Estimate */
  *rateIndex = WebRtcIsacfix_GetDownlinkBwIndexImpl(&ISAC_inst->bwestimator_obj);

  return 0;
}


/****************************************************************************
 * WebRtcIsacfix_UpdateUplinkBw(...)
 *
 * This function takes an index representing the Bandwidth estimate from
 * this side to other side and updates BWE.
 *
 * Input:
 *      - ISAC_main_inst: iSAC struct
 *      - rateIndex     : Bandwidth estimate from other side.
 *
 */

int16_t WebRtcIsacfix_UpdateUplinkBw(ISACFIX_MainStruct* ISAC_main_inst,
                                     int16_t     rateIndex)
{
  int16_t err = 0;
  ISACFIX_SubStruct *ISAC_inst;

  /* typecast pointer to real structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;

  /* Call function to update BWE with received Bandwidth Estimate */
  err = WebRtcIsacfix_UpdateUplinkBwRec(&ISAC_inst->bwestimator_obj, rateIndex);
  if (err < 0) {
    ISAC_inst->errorcode = -err;
    return (-1);
  }

  return 0;
}

/****************************************************************************
 * WebRtcIsacfix_ReadFrameLen(...)
 *
 * This function returns the length of the frame represented in the packet.
 *
 * Input:
 *      - encoded       : Encoded bitstream
 *
 * Output:
 *      - frameLength   : Length of frame in packet (in samples)
 *
 */

int16_t WebRtcIsacfix_ReadFrameLen(const uint8_t* encoded,
                                   size_t encoded_len_bytes,
                                   size_t* frameLength)
{
  Bitstr_dec streamdata;
  int16_t err;
  const size_t kRequiredEncodedLenBytes = 10;

  if (encoded_len_bytes < kRequiredEncodedLenBytes) {
    return -1;
  }

  InitializeDecoderBitstream(encoded_len_bytes, &streamdata);

  read_be16(encoded, kRequiredEncodedLenBytes, streamdata.stream);

  /* decode frame length */
  err = WebRtcIsacfix_DecodeFrameLen(&streamdata, frameLength);
  if (err<0)  // error check
    return err;

  return 0;
}


/****************************************************************************
 * WebRtcIsacfix_ReadBwIndex(...)
 *
 * This function returns the index of the Bandwidth estimate from the bitstream.
 *
 * Input:
 *      - encoded       : Encoded bitstream
 *
 * Output:
 *      - frameLength   : Length of frame in packet (in samples)
 *      - rateIndex     : Bandwidth estimate in bitstream
 *
 */

int16_t WebRtcIsacfix_ReadBwIndex(const uint8_t* encoded,
                                  size_t encoded_len_bytes,
                                  int16_t* rateIndex)
{
  Bitstr_dec streamdata;
  int16_t err;
  const size_t kRequiredEncodedLenBytes = 10;

  if (encoded_len_bytes < kRequiredEncodedLenBytes) {
    return -1;
  }

  InitializeDecoderBitstream(encoded_len_bytes, &streamdata);

  read_be16(encoded, kRequiredEncodedLenBytes, streamdata.stream);

  /* decode frame length, needed to get to the rateIndex in the bitstream */
  size_t frameLength;
  err = WebRtcIsacfix_DecodeFrameLen(&streamdata, &frameLength);
  if (err<0)  // error check
    return err;

  /* decode BW estimation */
  err = WebRtcIsacfix_DecodeSendBandwidth(&streamdata, rateIndex);
  if (err<0)  // error check
    return err;

  return 0;
}




/****************************************************************************
 * WebRtcIsacfix_GetErrorCode(...)
 *
 * This function can be used to check the error code of an iSAC instance. When
 * a function returns -1 a error code will be set for that instance. The
 * function below extract the code of the last error that occured in the
 * specified instance.
 *
 * Input:
 *      - ISAC_main_inst    : ISAC instance
 *
 * Return value             : Error code
 */

int16_t WebRtcIsacfix_GetErrorCode(ISACFIX_MainStruct *ISAC_main_inst)
{
  ISACFIX_SubStruct *ISAC_inst;
  /* typecast pointer to real structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;

  return ISAC_inst->errorcode;
}



/****************************************************************************
 * WebRtcIsacfix_GetUplinkBw(...)
 *
 * This function returns the inst quantized iSAC send bitrate
 *
 * Input:
 *      - ISAC_main_inst    : iSAC instance
 *
 * Return value             : bitrate
 */

int32_t WebRtcIsacfix_GetUplinkBw(ISACFIX_MainStruct *ISAC_main_inst)
{
  ISACFIX_SubStruct *ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;
  BwEstimatorstr * bw = (BwEstimatorstr*)&(ISAC_inst->bwestimator_obj);

  return (int32_t) WebRtcIsacfix_GetUplinkBandwidth(bw);
}

/****************************************************************************
 * WebRtcIsacfix_GetNewFrameLen(...)
 *
 * This function return the next frame length (in samples) of iSAC.
 *
 * Input:
 *      - ISAC_main_inst    : iSAC instance
 *
 * Return value             :  frame lenght in samples
 */

int16_t WebRtcIsacfix_GetNewFrameLen(ISACFIX_MainStruct *ISAC_main_inst)
{
  ISACFIX_SubStruct *ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;
  return ISAC_inst->ISACenc_obj.new_framelength;
}


/****************************************************************************
 * WebRtcIsacfix_SetMaxPayloadSize(...)
 *
 * This function sets a limit for the maximum payload size of iSAC. The same
 * value is used both for 30 and 60 msec packets.
 * The absolute max will be valid until next time the function is called.
 * NOTE! This function may override the function WebRtcIsacfix_SetMaxRate()
 *
 * Input:
 *      - ISAC_main_inst    : iSAC instance
 *      - maxPayloadBytes   : maximum size of the payload in bytes
 *                            valid values are between 100 and 400 bytes
 *
 *
 * Return value             : 0 if sucessful
 *                           -1 if error happens
 */

int16_t WebRtcIsacfix_SetMaxPayloadSize(ISACFIX_MainStruct *ISAC_main_inst,
                                        int16_t maxPayloadBytes)
{
  ISACFIX_SubStruct *ISAC_inst;

  /* typecast pointer to real structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;

  if((maxPayloadBytes < 100) || (maxPayloadBytes > 400))
  {
    /* maxPayloadBytes is out of valid range */
    return -1;
  }
  else
  {
    /* Set new absolute max, which will not change unless this function
       is called again with a new value */
    ISAC_inst->ISACenc_obj.maxPayloadBytes = maxPayloadBytes;

    /* Set new maximum values for 30 and 60 msec packets */
    if (maxPayloadBytes < ISAC_inst->ISACenc_obj.maxRateInBytes) {
      ISAC_inst->ISACenc_obj.payloadLimitBytes30 = maxPayloadBytes;
    } else {
      ISAC_inst->ISACenc_obj.payloadLimitBytes30 = ISAC_inst->ISACenc_obj.maxRateInBytes;
    }

    if ( maxPayloadBytes < (ISAC_inst->ISACenc_obj.maxRateInBytes << 1)) {
      ISAC_inst->ISACenc_obj.payloadLimitBytes60 = maxPayloadBytes;
    } else {
      ISAC_inst->ISACenc_obj.payloadLimitBytes60 = (ISAC_inst->ISACenc_obj.maxRateInBytes << 1);
    }
  }
  return 0;
}


/****************************************************************************
 * WebRtcIsacfix_SetMaxRate(...)
 *
 * This function sets the maximum rate which the codec may not exceed for a
 * singel packet. The maximum rate is set in bits per second.
 * The codec has an absolute maximum rate of 53400 bits per second (200 bytes
 * per 30 msec).
 * It is possible to set a maximum rate between 32000 and 53400 bits per second.
 *
 * The rate limit is valid until next time the function is called.
 *
 * NOTE! Packet size will never go above the value set if calling
 * WebRtcIsacfix_SetMaxPayloadSize() (default max packet size is 400 bytes).
 *
 * Input:
 *      - ISAC_main_inst    : iSAC instance
 *      - maxRateInBytes    : maximum rate in bits per second,
 *                            valid values are 32000 to 53400 bits
 *
 * Return value             : 0 if sucessful
 *                           -1 if error happens
 */

int16_t WebRtcIsacfix_SetMaxRate(ISACFIX_MainStruct *ISAC_main_inst,
                                 int32_t maxRate)
{
  ISACFIX_SubStruct *ISAC_inst;
  int16_t maxRateInBytes;

  /* typecast pointer to real structure */
  ISAC_inst = (ISACFIX_SubStruct *)ISAC_main_inst;

  if((maxRate < 32000) || (maxRate > 53400))
  {
    /* maxRate is out of valid range */
    return -1;
  }
  else
  {
    /* Calculate maximum number of bytes per 30 msec packets for the given
       maximum rate. Multiply with 30/1000 to get number of bits per 30 msec,
       divide by 8 to get number of bytes per 30 msec:
       maxRateInBytes = floor((maxRate * 30/1000) / 8); */
    maxRateInBytes = (int16_t)( WebRtcSpl_DivW32W16ResW16(WEBRTC_SPL_MUL(maxRate, 3), 800) );

    /* Store the value for usage in the WebRtcIsacfix_SetMaxPayloadSize-function */
    ISAC_inst->ISACenc_obj.maxRateInBytes = maxRateInBytes;

    /* For 30 msec packets: if the new limit is below the maximum
       payload size, set a new limit */
    if (maxRateInBytes < ISAC_inst->ISACenc_obj.maxPayloadBytes) {
      ISAC_inst->ISACenc_obj.payloadLimitBytes30 = maxRateInBytes;
    } else {
      ISAC_inst->ISACenc_obj.payloadLimitBytes30 = ISAC_inst->ISACenc_obj.maxPayloadBytes;
    }

    /* For 60 msec packets: if the new limit (times 2) is below the
       maximum payload size, set a new limit */
    if ( (maxRateInBytes << 1) < ISAC_inst->ISACenc_obj.maxPayloadBytes) {
      ISAC_inst->ISACenc_obj.payloadLimitBytes60 = (maxRateInBytes << 1);
    } else {
      ISAC_inst->ISACenc_obj.payloadLimitBytes60 = ISAC_inst->ISACenc_obj.maxPayloadBytes;
    }
  }

  return 0;
}



/****************************************************************************
 * WebRtcIsacfix_version(...)
 *
 * This function returns the version number.
 *
 * Output:
 *      - version  : Pointer to character string
 *
 */

void WebRtcIsacfix_version(char *version)
{
  strcpy(version, "3.6.0");
}

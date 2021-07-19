/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_FIX_INCLUDE_ISACFIX_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_FIX_INCLUDE_ISACFIX_H_

#include <stddef.h>

#include "modules/audio_coding/codecs/isac/bandwidth_info.h"

typedef struct {
  void* dummy;
} ISACFIX_MainStruct;

#if defined(__cplusplus)
extern "C" {
#endif

/****************************************************************************
 * WebRtcIsacfix_Create(...)
 *
 * This function creates an ISAC instance, which will contain the state
 * information for one coding/decoding channel.
 *
 * Input:
 *      - *ISAC_main_inst   : a pointer to the coder instance.
 *
 * Return value             : 0 - Ok
 *                           -1 - Error
 */

int16_t WebRtcIsacfix_Create(ISACFIX_MainStruct** ISAC_main_inst);

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

int16_t WebRtcIsacfix_Free(ISACFIX_MainStruct* ISAC_main_inst);

/****************************************************************************
 * WebRtcIsacfix_EncoderInit(...)
 *
 * This function initializes an ISAC instance prior to the encoder calls.
 *
 * Input:
 *     - ISAC_main_inst     : ISAC instance.
 *     - CodingMode         : 0 - Bit rate and frame length are automatically
 *                                adjusted to available bandwidth on
 *                                transmission channel.
 *                            1 - User sets a frame length and a target bit
 *                                rate which is taken as the maximum short-term
 *                                average bit rate.
 *
 * Return value             :  0 - Ok
 *                            -1 - Error
 */

int16_t WebRtcIsacfix_EncoderInit(ISACFIX_MainStruct* ISAC_main_inst,
                                  int16_t CodingMode);

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
 * Return value             : >0 - Length (in bytes) of coded data
 *                             0 - The buffer didn't reach the chosen framesize
 *                                 so it keeps buffering speech samples.
 *                            -1 - Error
 */

int WebRtcIsacfix_Encode(ISACFIX_MainStruct* ISAC_main_inst,
                         const int16_t* speechIn,
                         uint8_t* encoded);

/****************************************************************************
 * WebRtcIsacfix_DecoderInit(...)
 *
 * This function initializes an ISAC instance prior to the decoder calls.
 *
 * Input:
 *  - ISAC_main_inst : ISAC instance.
 */

void WebRtcIsacfix_DecoderInit(ISACFIX_MainStruct* ISAC_main_inst);

/****************************************************************************
 * WebRtcIsacfix_UpdateBwEstimate1(...)
 *
 * This function updates the estimate of the bandwidth.
 *
 * Input:
 *      - ISAC_main_inst    : ISAC instance.
 *      - encoded           : encoded ISAC frame(s).
 *      - packet_size       : size of the packet in bytes.
 *      - rtp_seq_number    : the RTP number of the packet.
 *      - arr_ts            : the arrival time of the packet (from NetEq)
 *                            in samples.
 *
 * Return value             : 0 - Ok
 *                           -1 - Error
 */

int16_t WebRtcIsacfix_UpdateBwEstimate1(ISACFIX_MainStruct* ISAC_main_inst,
                                        const uint8_t* encoded,
                                        size_t packet_size,
                                        uint16_t rtp_seq_number,
                                        uint32_t arr_ts);

/****************************************************************************
 * WebRtcIsacfix_UpdateBwEstimate(...)
 *
 * This function updates the estimate of the bandwidth.
 *
 * Input:
 *      - ISAC_main_inst    : ISAC instance.
 *      - encoded           : encoded ISAC frame(s).
 *      - packet_size       : size of the packet in bytes.
 *      - rtp_seq_number    : the RTP number of the packet.
 *      - send_ts           : the send time of the packet from RTP header,
 *                            in samples.
 *      - arr_ts            : the arrival time of the packet (from NetEq)
 *                            in samples.
 *
 * Return value             :  0 - Ok
 *                            -1 - Error
 */

int16_t WebRtcIsacfix_UpdateBwEstimate(ISACFIX_MainStruct* ISAC_main_inst,
                                       const uint8_t* encoded,
                                       size_t packet_size,
                                       uint16_t rtp_seq_number,
                                       uint32_t send_ts,
                                       uint32_t arr_ts);

/****************************************************************************
 * WebRtcIsacfix_Decode(...)
 *
 * This function decodes an ISAC frame. Output speech length
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
                         int16_t* speechType);

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
 *                            to produce
 *                            NOTE! Maximum number is 2 (960 samples = 60ms)
 *
 * Output:
 *      - decoded           : The decoded vector
 *
 * Return value             : Number of samples in decoded PLC vector
 */

size_t WebRtcIsacfix_DecodePlc(ISACFIX_MainStruct* ISAC_main_inst,
                               int16_t* decoded,
                               size_t noOfLostFrames);

/****************************************************************************
 * WebRtcIsacfix_ReadFrameLen(...)
 *
 * This function returns the length of the frame represented in the packet.
 *
 * Input:
 *      - encoded           : Encoded bitstream
 *      - encoded_len_bytes : Length of the bitstream in bytes.
 *
 * Output:
 *      - frameLength       : Length of frame in packet (in samples)
 *
 */

int16_t WebRtcIsacfix_ReadFrameLen(const uint8_t* encoded,
                                   size_t encoded_len_bytes,
                                   size_t* frameLength);

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
 *                           -1 - Error
 */

int16_t WebRtcIsacfix_Control(ISACFIX_MainStruct* ISAC_main_inst,
                              int16_t rate,
                              int framesize);

void WebRtcIsacfix_SetInitialBweBottleneck(ISACFIX_MainStruct* ISAC_main_inst,
                                           int bottleneck_bits_per_second);

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
 *      - ISAC_main_inst    : ISAC instance.
 *      - rateBPS           : initial value of bottleneck in bits/second
 *                            10000 <= rateBPS <= 32000 is accepted
 *      - frameSizeMs       : number of milliseconds per frame (30 or 60)
 *      - enforceFrameSize  : 1 to enforce the given frame-size through out
 *                            the adaptation process, 0 to let iSAC change
 *                            the frame-size if required.
 *
 * Return value             : 0  - ok
 *                           -1 - Error
 */

int16_t WebRtcIsacfix_ControlBwe(ISACFIX_MainStruct* ISAC_main_inst,
                                 int16_t rateBPS,
                                 int frameSizeMs,
                                 int16_t enforceFrameSize);

/****************************************************************************
 * WebRtcIsacfix_version(...)
 *
 * This function returns the version number.
 *
 * Output:
 *      - version      : Pointer to character string
 *
 */

void WebRtcIsacfix_version(char* version);

/****************************************************************************
 * WebRtcIsacfix_GetErrorCode(...)
 *
 * This function can be used to check the error code of an iSAC instance. When
 * a function returns -1 a error code will be set for that instance. The
 * function below extract the code of the last error that occured in the
 * specified instance.
 *
 * Input:
 *  - ISAC_main_inst        : ISAC instance
 *
 * Return value             : Error code
 */

int16_t WebRtcIsacfix_GetErrorCode(ISACFIX_MainStruct* ISAC_main_inst);

/****************************************************************************
 * WebRtcIsacfix_GetUplinkBw(...)
 *
 * This function return iSAC send bitrate
 *
 * Input:
 *      - ISAC_main_inst    : iSAC instance
 *
 * Return value             : <0 Error code
 *                            else bitrate
 */

int32_t WebRtcIsacfix_GetUplinkBw(ISACFIX_MainStruct* ISAC_main_inst);

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

int16_t WebRtcIsacfix_SetMaxPayloadSize(ISACFIX_MainStruct* ISAC_main_inst,
                                        int16_t maxPayloadBytes);

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

int16_t WebRtcIsacfix_SetMaxRate(ISACFIX_MainStruct* ISAC_main_inst,
                                 int32_t maxRate);

/****************************************************************************
 * WebRtcIsacfix_CreateInternal(...)
 *
 * This function creates the memory that is used to store data in the encoder
 *
 * Input:
 *      - *ISAC_main_inst   : a pointer to the coder instance.
 *
 * Return value             : 0 - Ok
 *                           -1 - Error
 */

int16_t WebRtcIsacfix_CreateInternal(ISACFIX_MainStruct* ISAC_main_inst);

/****************************************************************************
 * WebRtcIsacfix_FreeInternal(...)
 *
 * This function frees the internal memory for storing encoder data.
 *
 * Input:
 *      - ISAC_main_inst        : an ISAC instance.
 *
 * Return value                 :  0 - Ok
 *                                -1 - Error
 */

int16_t WebRtcIsacfix_FreeInternal(ISACFIX_MainStruct* ISAC_main_inst);

/****************************************************************************
 * WebRtcIsacfix_GetNewBitStream(...)
 *
 * This function returns encoded data, with the recieved bwe-index in the
 * stream. It should always return a complete packet, i.e. only called once
 * even for 60 msec frames
 *
 * Input:
 *      - ISAC_main_inst    : ISAC instance.
 *      - bweIndex          : index of bandwidth estimate to put in new
 * bitstream - scale             : factor for rate change (0.4 ~=> half the
 * rate, 1 no change).
 *
 * Output:
 *      - encoded           : the encoded data vector
 *
 * Return value             : >0 - Length (in bytes) of coded data
 *                            -1 - Error
 */

int16_t WebRtcIsacfix_GetNewBitStream(ISACFIX_MainStruct* ISAC_main_inst,
                                      int16_t bweIndex,
                                      float scale,
                                      uint8_t* encoded);

/****************************************************************************
 * WebRtcIsacfix_GetDownLinkBwIndex(...)
 *
 * This function returns index representing the Bandwidth estimate from
 * other side to this side.
 *
 * Input:
 *      - ISAC_main_inst    : iSAC struct
 *
 * Output:
 *      - rateIndex         : Bandwidth estimate to transmit to other side.
 *
 */

int16_t WebRtcIsacfix_GetDownLinkBwIndex(ISACFIX_MainStruct* ISAC_main_inst,
                                         int16_t* rateIndex);

/****************************************************************************
 * WebRtcIsacfix_UpdateUplinkBw(...)
 *
 * This function takes an index representing the Bandwidth estimate from
 * this side to other side and updates BWE.
 *
 * Input:
 *      - ISAC_main_inst    : iSAC struct
 *      - rateIndex         : Bandwidth estimate from other side.
 *
 */

int16_t WebRtcIsacfix_UpdateUplinkBw(ISACFIX_MainStruct* ISAC_main_inst,
                                     int16_t rateIndex);

/****************************************************************************
 * WebRtcIsacfix_ReadBwIndex(...)
 *
 * This function returns the index of the Bandwidth estimate from the bitstream.
 *
 * Input:
 *      - encoded           : Encoded bitstream
 *      - encoded_len_bytes : Length of the bitstream in bytes.
 *
 * Output:
 *      - rateIndex         : Bandwidth estimate in bitstream
 *
 */

int16_t WebRtcIsacfix_ReadBwIndex(const uint8_t* encoded,
                                  size_t encoded_len_bytes,
                                  int16_t* rateIndex);

/****************************************************************************
 * WebRtcIsacfix_GetNewFrameLen(...)
 *
 * This function return the next frame length (in samples) of iSAC.
 *
 * Input:
 *      -ISAC_main_inst     : iSAC instance
 *
 * Return value             : frame lenght in samples
 */

int16_t WebRtcIsacfix_GetNewFrameLen(ISACFIX_MainStruct* ISAC_main_inst);

#if defined(__cplusplus)
}
#endif

#endif /* MODULES_AUDIO_CODING_CODECS_ISAC_FIX_INCLUDE_ISACFIX_H_ */

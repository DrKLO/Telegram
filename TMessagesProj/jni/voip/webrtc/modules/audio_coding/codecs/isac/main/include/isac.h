/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_INCLUDE_ISAC_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_INCLUDE_ISAC_H_

#include <stddef.h>

#include "modules/audio_coding/codecs/isac/bandwidth_info.h"

typedef struct WebRtcISACStruct ISACStruct;

#if defined(__cplusplus)
extern "C" {
#endif

/******************************************************************************
 * WebRtcIsac_Create(...)
 *
 * This function creates an ISAC instance, which will contain the state
 * information for one coding/decoding channel.
 *
 * Input:
 *        - *ISAC_main_inst   : a pointer to the coder instance.
 *
 * Return value               : 0 - Ok
 *                             -1 - Error
 */

int16_t WebRtcIsac_Create(ISACStruct** ISAC_main_inst);

/******************************************************************************
 * WebRtcIsac_Free(...)
 *
 * This function frees the ISAC instance created at the beginning.
 *
 * Input:
 *        - ISAC_main_inst    : an ISAC instance.
 *
 * Return value               : 0 - Ok
 *                             -1 - Error
 */

int16_t WebRtcIsac_Free(ISACStruct* ISAC_main_inst);

/******************************************************************************
 * WebRtcIsac_EncoderInit(...)
 *
 * This function initializes an ISAC instance prior to the encoder calls.
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 *        - CodingMode        : 0 -> Bit rate and frame length are
 *                                automatically adjusted to available bandwidth
 *                                on transmission channel, just valid if codec
 *                                is created to work in wideband mode.
 *                              1 -> User sets a frame length and a target bit
 *                                rate which is taken as the maximum
 *                                short-term average bit rate.
 *
 * Return value               : 0 - Ok
 *                             -1 - Error
 */

int16_t WebRtcIsac_EncoderInit(ISACStruct* ISAC_main_inst, int16_t CodingMode);

/******************************************************************************
 * WebRtcIsac_Encode(...)
 *
 * This function encodes 10ms audio blocks and inserts it into a package.
 * Input speech length has 160 samples if operating at 16 kHz sampling
 * rate, or 320 if operating at 32 kHz sampling rate. The encoder buffers the
 * input audio until the whole frame is buffered then proceeds with encoding.
 *
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
 *                               frame-size so it keeps buffering speech
 *                               samples.
 *                            : -1 - Error
 */

int WebRtcIsac_Encode(ISACStruct* ISAC_main_inst,
                      const int16_t* speechIn,
                      uint8_t* encoded);

/******************************************************************************
 * WebRtcIsac_DecoderInit(...)
 *
 * This function initializes an ISAC instance prior to the decoder calls.
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 */

void WebRtcIsac_DecoderInit(ISACStruct* ISAC_main_inst);

/******************************************************************************
 * WebRtcIsac_UpdateBwEstimate(...)
 *
 * This function updates the estimate of the bandwidth.
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 *        - encoded           : encoded ISAC frame(s).
 *        - packet_size       : size of the packet.
 *        - rtp_seq_number    : the RTP number of the packet.
 *        - send_ts           : the RTP send timestamp, given in samples
 *        - arr_ts            : the arrival time of the packet (from NetEq)
 *                              in samples.
 *
 * Return value               : 0 - Ok
 *                             -1 - Error
 */

int16_t WebRtcIsac_UpdateBwEstimate(ISACStruct* ISAC_main_inst,
                                    const uint8_t* encoded,
                                    size_t packet_size,
                                    uint16_t rtp_seq_number,
                                    uint32_t send_ts,
                                    uint32_t arr_ts);

/******************************************************************************
 * WebRtcIsac_Decode(...)
 *
 * This function decodes an ISAC frame. At 16 kHz sampling rate, the length
 * of the output audio could be either 480 or 960 samples, equivalent to
 * 30 or 60 ms respectively. At 32 kHz sampling rate, the length of the
 * output audio is 960 samples, which is 30 ms.
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 *        - encoded           : encoded ISAC frame(s).
 *        - len               : bytes in encoded vector.
 *
 * Output:
 *        - decoded           : The decoded vector.
 *
 * Return value               : >0 - number of samples in decoded vector.
 *                              -1 - Error.
 */

int WebRtcIsac_Decode(ISACStruct* ISAC_main_inst,
                      const uint8_t* encoded,
                      size_t len,
                      int16_t* decoded,
                      int16_t* speechType);

/******************************************************************************
 * WebRtcIsac_DecodePlc(...)
 *
 * This function conducts PLC for ISAC frame(s). Output speech length
 * will be a multiple of frames, i.e. multiples of 30 ms audio. Therefore,
 * the output is multiple of 480 samples if operating at 16 kHz and multiple
 * of 960 if operating at 32 kHz.
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 *        - noOfLostFrames    : Number of PLC frames to produce.
 *
 * Output:
 *        - decoded           : The decoded vector.
 *
 * Return value               : Number of samples in decoded PLC vector
 */

size_t WebRtcIsac_DecodePlc(ISACStruct* ISAC_main_inst,
                            int16_t* decoded,
                            size_t noOfLostFrames);

/******************************************************************************
 * WebRtcIsac_Control(...)
 *
 * This function sets the limit on the short-term average bit-rate and the
 * frame length. Should be used only in Instantaneous mode. At 16 kHz sampling
 * rate, an average bit-rate between 10000 to 32000 bps is valid and a
 * frame-size of 30 or 60 ms is acceptable. At 32 kHz, an average bit-rate
 * between 10000 to 56000 is acceptable, and the valid frame-size is 30 ms.
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 *        - rate              : limit on the short-term average bit rate,
 *                              in bits/second.
 *        - framesize         : frame-size in millisecond.
 *
 * Return value               : 0  - ok
 *                             -1 - Error
 */

int16_t WebRtcIsac_Control(ISACStruct* ISAC_main_inst,
                           int32_t rate,
                           int framesize);

void WebRtcIsac_SetInitialBweBottleneck(ISACStruct* ISAC_main_inst,
                                        int bottleneck_bits_per_second);

/******************************************************************************
 * WebRtcIsac_ControlBwe(...)
 *
 * This function sets the initial values of bottleneck and frame-size if
 * iSAC is used in channel-adaptive mode. Therefore, this API is not
 * applicable if the codec is created to operate in super-wideband mode.
 *
 * Through this API, users can enforce a frame-size for all values of
 * bottleneck. Then iSAC will not automatically change the frame-size.
 *
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 *        - rateBPS           : initial value of bottleneck in bits/second
 *                              10000 <= rateBPS <= 56000 is accepted
 *                              For default bottleneck set rateBPS = 0
 *        - frameSizeMs       : number of milliseconds per frame (30 or 60)
 *        - enforceFrameSize  : 1 to enforce the given frame-size through
 *                              out the adaptation process, 0 to let iSAC
 *                              change the frame-size if required.
 *
 * Return value               : 0  - ok
 *                             -1 - Error
 */

int16_t WebRtcIsac_ControlBwe(ISACStruct* ISAC_main_inst,
                              int32_t rateBPS,
                              int frameSizeMs,
                              int16_t enforceFrameSize);

/******************************************************************************
 * WebRtcIsac_ReadFrameLen(...)
 *
 * This function returns the length of the frame represented in the packet.
 *
 * Input:
 *        - encoded           : Encoded bit-stream
 *
 * Output:
 *        - frameLength       : Length of frame in packet (in samples)
 *
 */

int16_t WebRtcIsac_ReadFrameLen(const ISACStruct* ISAC_main_inst,
                                const uint8_t* encoded,
                                int16_t* frameLength);

/******************************************************************************
 * WebRtcIsac_version(...)
 *
 * This function returns the version number.
 *
 * Output:
 *        - version      : Pointer to character string
 *
 */

void WebRtcIsac_version(char* version);

/******************************************************************************
 * WebRtcIsac_GetErrorCode(...)
 *
 * This function can be used to check the error code of an iSAC instance. When
 * a function returns -1 a error code will be set for that instance. The
 * function below extract the code of the last error that occurred in the
 * specified instance.
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance
 *
 * Return value               : Error code
 */

int16_t WebRtcIsac_GetErrorCode(ISACStruct* ISAC_main_inst);

/****************************************************************************
 * WebRtcIsac_GetUplinkBw(...)
 *
 * This function outputs the target bottleneck of the codec. In
 * channel-adaptive mode, the target bottleneck is specified through in-band
 * signalling retreived by bandwidth estimator.
 * In channel-independent, also called instantaneous mode, the target
 * bottleneck is provided to the encoder by calling xxx_control(...). If
 * xxx_control is never called the default values is returned. The default
 * value for bottleneck at 16 kHz encoder sampling rate is 32000 bits/sec,
 * and it is 56000 bits/sec for 32 kHz sampling rate.
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

int16_t WebRtcIsac_GetUplinkBw(ISACStruct* ISAC_main_inst, int32_t* bottleneck);

/******************************************************************************
 * WebRtcIsac_SetMaxPayloadSize(...)
 *
 * This function sets a limit for the maximum payload size of iSAC. The same
 * value is used both for 30 and 60 ms packets. If the encoder sampling rate
 * is 16 kHz the maximum payload size is between 120 and 400 bytes. If the
 * encoder sampling rate is 32 kHz the maximum payload size is between 120
 * and 600 bytes.
 *
 * If an out of range limit is used, the function returns -1, but the closest
 * valid value will be applied.
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
 *                              valid values are between 120 and 400 bytes
 *                              if encoder sampling rate is 16 kHz. For
 *                              32 kHz encoder sampling rate valid values
 *                              are between 120 and 600 bytes.
 *
 * Return value               : 0 if successful
 *                             -1 if error happens
 */

int16_t WebRtcIsac_SetMaxPayloadSize(ISACStruct* ISAC_main_inst,
                                     int16_t maxPayloadBytes);

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
 * If an out of range limit is used, the function returns -1, but the closest
 * valid value will be applied.
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

int16_t WebRtcIsac_SetMaxRate(ISACStruct* ISAC_main_inst, int32_t maxRate);

/******************************************************************************
 * WebRtcIsac_DecSampRate()
 * Return the sampling rate of the decoded audio.
 *
 * Input:
 *        - ISAC_main_inst    : iSAC instance
 *
 * Return value               : sampling frequency in Hertz.
 *
 */

uint16_t WebRtcIsac_DecSampRate(ISACStruct* ISAC_main_inst);

/******************************************************************************
 * WebRtcIsac_EncSampRate()
 *
 * Input:
 *        - ISAC_main_inst    : iSAC instance
 *
 * Return value               : sampling rate in Hertz.
 *
 */

uint16_t WebRtcIsac_EncSampRate(ISACStruct* ISAC_main_inst);

/******************************************************************************
 * WebRtcIsac_SetDecSampRate()
 * Set the sampling rate of the decoder.  Initialization of the decoder WILL
 * NOT overwrite the sampling rate of the encoder. The default value is 16 kHz
 * which is set when the instance is created.
 *
 * Input:
 *        - ISAC_main_inst    : iSAC instance
 *        - sampRate          : sampling rate in Hertz.
 *
 * Return value               : 0 if successful
 *                             -1 if failed.
 */

int16_t WebRtcIsac_SetDecSampRate(ISACStruct* ISAC_main_inst,
                                  uint16_t samp_rate_hz);

/******************************************************************************
 * WebRtcIsac_SetEncSampRate()
 * Set the sampling rate of the encoder. Initialization of the encoder WILL
 * NOT overwrite the sampling rate of the encoder. The default value is 16 kHz
 * which is set when the instance is created. The encoding-mode and the
 * bottleneck remain unchanged by this call, however, the maximum rate and
 * maximum payload-size will reset to their default value.
 *
 * Input:
 *        - ISAC_main_inst    : iSAC instance
 *        - sampRate          : sampling rate in Hertz.
 *
 * Return value               : 0 if successful
 *                             -1 if failed.
 */

int16_t WebRtcIsac_SetEncSampRate(ISACStruct* ISAC_main_inst,
                                  uint16_t sample_rate_hz);

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
 * NOTE 2! Currently not implemented for SWB mode.
 * NOTE 3! Rates larger than the bottleneck of the codec will be limited
 *         to the current bottleneck.
 *
 * Input:
 *        - ISAC_main_inst    : ISAC instance.
 *        - bweIndex          : Index of bandwidth estimate to put in new
 *                              bitstream
 *        - rate              : target rate of the transcoder is bits/sec.
 *                              Valid values are the accepted rate in iSAC,
 *                              i.e. 10000 to 56000.
 *        - isRCU                       : if the new bit-stream is an RCU
 * stream. Note that the rate parameter always indicates the target rate of the
 * main payload, regardless of 'isRCU' value.
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
int16_t WebRtcIsac_GetNewBitStream(ISACStruct* ISAC_main_inst,
                                   int16_t bweIndex,
                                   int16_t jitterInfo,
                                   int32_t rate,
                                   uint8_t* encoded,
                                   int16_t isRCU);

/****************************************************************************
 * WebRtcIsac_GetDownLinkBwIndex(...)
 *
 * This function returns index representing the Bandwidth estimate from
 * other side to this side.
 *
 * Input:
 *        - ISAC_main_inst    : iSAC struct
 *
 * Output:
 *        - bweIndex          : Bandwidth estimate to transmit to other side.
 *
 */

int16_t WebRtcIsac_GetDownLinkBwIndex(ISACStruct* ISAC_main_inst,
                                      int16_t* bweIndex,
                                      int16_t* jitterInfo);

/****************************************************************************
 * WebRtcIsac_UpdateUplinkBw(...)
 *
 * This function takes an index representing the Bandwidth estimate from
 * this side to other side and updates BWE.
 *
 * Input:
 *        - ISAC_main_inst    : iSAC struct
 *        - bweIndex          : Bandwidth estimate from other side.
 *
 */

int16_t WebRtcIsac_UpdateUplinkBw(ISACStruct* ISAC_main_inst, int16_t bweIndex);

/****************************************************************************
 * WebRtcIsac_ReadBwIndex(...)
 *
 * This function returns the index of the Bandwidth estimate from the bitstream.
 *
 * Input:
 *        - encoded           : Encoded bitstream
 *
 * Output:
 *        - frameLength       : Length of frame in packet (in samples)
 *        - bweIndex         : Bandwidth estimate in bitstream
 *
 */

int16_t WebRtcIsac_ReadBwIndex(const uint8_t* encoded, int16_t* bweIndex);

/*******************************************************************************
 * WebRtcIsac_GetNewFrameLen(...)
 *
 * returns the frame lenght (in samples) of the next packet. In the case of
 * channel-adaptive mode, iSAC decides on its frame lenght based on the
 * estimated bottleneck this allows a user to prepare for the next packet (at
 * the encoder)
 *
 * The primary usage is in CE to make the iSAC works in channel-adaptive mode
 *
 * Input:
 *        - ISAC_main_inst     : iSAC struct
 *
 * Return Value                : frame lenght in samples
 *
 */

int16_t WebRtcIsac_GetNewFrameLen(ISACStruct* ISAC_main_inst);

/****************************************************************************
 *  WebRtcIsac_GetRedPayload(...)
 *
 *  Populates "encoded" with the redundant payload of the recently encoded
 *  frame. This function has to be called once that WebRtcIsac_Encode(...)
 *  returns a positive value. Regardless of the frame-size this function will
 *  be called only once after encoding is completed.
 *
 * Input:
 *      - ISAC_main_inst    : iSAC struct
 *
 * Output:
 *        - encoded            : the encoded data vector
 *
 *
 * Return value:
 *                              : >0 - Length (in bytes) of coded data
 *                              : -1 - Error
 *
 *
 */
int16_t WebRtcIsac_GetRedPayload(ISACStruct* ISAC_main_inst, uint8_t* encoded);

/****************************************************************************
 * WebRtcIsac_DecodeRcu(...)
 *
 * This function decodes a redundant (RCU) iSAC frame. Function is called in
 * NetEq with a stored RCU payload i case of packet loss. Output speech length
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
                         size_t len,
                         int16_t* decoded,
                         int16_t* speechType);

/* If `inst` is a decoder but not an encoder: tell it what sample rate the
   encoder is using, for bandwidth estimation purposes. */
void WebRtcIsac_SetEncSampRateInDecoder(ISACStruct* inst, int sample_rate_hz);

#if defined(__cplusplus)
}
#endif

#endif /* MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_INCLUDE_ISAC_H_ */

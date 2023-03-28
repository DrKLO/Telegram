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
 * bandwidth_estimator.h
 *
 * This header file contains the API for the Bandwidth Estimator
 * designed for iSAC.
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_BANDWIDTH_ESTIMATOR_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_BANDWIDTH_ESTIMATOR_H_

#include "modules/audio_coding/codecs/isac/fix/source/structs.h"

/****************************************************************************
 * WebRtcIsacfix_InitBandwidthEstimator(...)
 *
 * This function initializes the struct for the bandwidth estimator
 *
 * Input/Output:
 *      - bwest_str        : Struct containing bandwidth information.
 *
 * Return value            : 0
 */

int32_t WebRtcIsacfix_InitBandwidthEstimator(BwEstimatorstr* bwest_str);

/****************************************************************************
 * WebRtcIsacfix_UpdateUplinkBwImpl(...)
 *
 * This function updates bottle neck rate received from other side in payload
 * and calculates a new bottle neck to send to the other side.
 *
 * Input/Output:
 *      - bweStr           : struct containing bandwidth information.
 *      - rtpNumber        : value from RTP packet, from NetEq
 *      - frameSize        : length of signal frame in ms, from iSAC decoder
 *      - sendTime         : value in RTP header giving send time in samples
 *      - arrivalTime      : value given by timeGetTime() time of arrival in
 *                           samples of packet from NetEq
 *      - pksize           : size of packet in bytes, from NetEq
 *      - Index            : integer (range 0...23) indicating bottle neck &
 *                           jitter as estimated by other side
 *
 * Return value            : 0 if everything went fine,
 *                           -1 otherwise
 */

int32_t WebRtcIsacfix_UpdateUplinkBwImpl(BwEstimatorstr* bwest_str,
                                         uint16_t rtp_number,
                                         int16_t frameSize,
                                         uint32_t send_ts,
                                         uint32_t arr_ts,
                                         size_t pksize,
                                         uint16_t Index);

/* Update receiving estimates. Used when we only receive BWE index, no iSAC data
 * packet. */
int16_t WebRtcIsacfix_UpdateUplinkBwRec(BwEstimatorstr* bwest_str,
                                        int16_t Index);

/****************************************************************************
 * WebRtcIsacfix_GetDownlinkBwIndexImpl(...)
 *
 * This function calculates and returns the bandwidth/jitter estimation code
 * (integer 0...23) to put in the sending iSAC payload.
 *
 * Input:
 *      - bweStr       : BWE struct
 *
 * Return:
 *      bandwith and jitter index (0..23)
 */
uint16_t WebRtcIsacfix_GetDownlinkBwIndexImpl(BwEstimatorstr* bwest_str);

/* Returns the bandwidth estimation (in bps) */
uint16_t WebRtcIsacfix_GetDownlinkBandwidth(const BwEstimatorstr* bwest_str);

/* Returns the bandwidth that iSAC should send with in bps */
int16_t WebRtcIsacfix_GetUplinkBandwidth(const BwEstimatorstr* bwest_str);

/* Returns the max delay (in ms) */
int16_t WebRtcIsacfix_GetDownlinkMaxDelay(const BwEstimatorstr* bwest_str);

/* Returns the max delay value from the other side in ms */
int16_t WebRtcIsacfix_GetUplinkMaxDelay(const BwEstimatorstr* bwest_str);

/*
 * update amount of data in bottle neck buffer and burst handling
 * returns minimum payload size (bytes)
 */
uint16_t WebRtcIsacfix_GetMinBytes(
    RateModel* State,
    int16_t StreamSize,    /* bytes in bitstream */
    int16_t FrameLen,      /* ms per frame */
    int16_t BottleNeck,    /* bottle neck rate; excl headers (bps) */
    int16_t DelayBuildUp); /* max delay from bottle neck buffering (ms) */

/*
 * update long-term average bitrate and amount of data in buffer
 */
void WebRtcIsacfix_UpdateRateModel(
    RateModel* State,
    int16_t StreamSize,   /* bytes in bitstream */
    int16_t FrameSamples, /* samples per frame */
    int16_t BottleNeck);  /* bottle neck rate; excl headers (bps) */

void WebRtcIsacfix_InitRateModel(RateModel* State);

/* Returns the new framelength value (input argument: bottle_neck) */
int16_t WebRtcIsacfix_GetNewFrameLength(int16_t bottle_neck,
                                        int16_t current_framelength);

/* Returns the new SNR value (input argument: bottle_neck) */
// returns snr in Q10
int16_t WebRtcIsacfix_GetSnr(int16_t bottle_neck, int16_t framesamples);

#endif /*  MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_BANDWIDTH_ESTIMATOR_H_ \
        */

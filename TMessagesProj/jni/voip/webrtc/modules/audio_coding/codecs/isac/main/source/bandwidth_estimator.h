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

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_BANDWIDTH_ESTIMATOR_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_BANDWIDTH_ESTIMATOR_H_

#include <stddef.h>

#include "modules/audio_coding/codecs/isac/main/source/settings.h"
#include "modules/audio_coding/codecs/isac/main/source/structs.h"

#define MIN_ISAC_BW 10000
#define MIN_ISAC_BW_LB 10000
#define MIN_ISAC_BW_UB 25000

#define MAX_ISAC_BW 56000
#define MAX_ISAC_BW_UB 32000
#define MAX_ISAC_BW_LB 32000

#define MIN_ISAC_MD 5
#define MAX_ISAC_MD 25

// assumed header size, in bytes; we don't know the exact number
// (header compression may be used)
#define HEADER_SIZE 35

// Initial Frame-Size, in ms, for Wideband & Super-Wideband Mode
#define INIT_FRAME_LEN_WB 60
#define INIT_FRAME_LEN_SWB 30

// Initial Bottleneck Estimate, in bits/sec, for
// Wideband & Super-wideband mode
#define INIT_BN_EST_WB 20e3f
#define INIT_BN_EST_SWB 56e3f

// Initial Header rate (header rate depends on frame-size),
// in bits/sec, for Wideband & Super-Wideband mode.
#define INIT_HDR_RATE_WB \
  ((float)HEADER_SIZE * 8.0f * 1000.0f / (float)INIT_FRAME_LEN_WB)
#define INIT_HDR_RATE_SWB \
  ((float)HEADER_SIZE * 8.0f * 1000.0f / (float)INIT_FRAME_LEN_SWB)

// number of packets in a row for a high rate burst
#define BURST_LEN 3

// ms, max time between two full bursts
#define BURST_INTERVAL 500

// number of packets in a row for initial high rate burst
#define INIT_BURST_LEN 5

// bits/s, rate for the first BURST_LEN packets
#define INIT_RATE_WB INIT_BN_EST_WB
#define INIT_RATE_SWB INIT_BN_EST_SWB

#if defined(__cplusplus)
extern "C" {
#endif

/* This function initializes the struct                    */
/* to be called before using the struct for anything else  */
/* returns 0 if everything went fine, -1 otherwise         */
int32_t WebRtcIsac_InitBandwidthEstimator(
    BwEstimatorstr* bwest_str,
    enum IsacSamplingRate encoderSampRate,
    enum IsacSamplingRate decoderSampRate);

/* This function updates the receiving estimate */
/* Parameters: */
/* rtp_number    - value from RTP packet, from NetEq */
/* frame length  - length of signal frame in ms, from iSAC decoder */
/* send_ts       - value in RTP header giving send time in samples */
/* arr_ts        - value given by timeGetTime() time of arrival in samples of
 * packet from NetEq      */
/* pksize        - size of packet in bytes, from NetEq */
/* Index         - integer (range 0...23) indicating bottle neck & jitter as
 * estimated by other side */
/* returns 0 if everything went fine, -1 otherwise */
int16_t WebRtcIsac_UpdateBandwidthEstimator(BwEstimatorstr* bwest_str,
                                            uint16_t rtp_number,
                                            int32_t frame_length,
                                            uint32_t send_ts,
                                            uint32_t arr_ts,
                                            size_t pksize);

/* Update receiving estimates. Used when we only receive BWE index, no iSAC data
 * packet. */
int16_t WebRtcIsac_UpdateUplinkBwImpl(
    BwEstimatorstr* bwest_str,
    int16_t Index,
    enum IsacSamplingRate encoderSamplingFreq);

/* Returns the bandwidth/jitter estimation code (integer 0...23) to put in the
 * sending iSAC payload */
void WebRtcIsac_GetDownlinkBwJitIndexImpl(
    BwEstimatorstr* bwest_str,
    int16_t* bottleneckIndex,
    int16_t* jitterInfo,
    enum IsacSamplingRate decoderSamplingFreq);

/* Returns the bandwidth estimation (in bps) */
int32_t WebRtcIsac_GetDownlinkBandwidth(const BwEstimatorstr* bwest_str);

/* Returns the max delay (in ms) */
int32_t WebRtcIsac_GetDownlinkMaxDelay(const BwEstimatorstr* bwest_str);

/* Returns the bandwidth that iSAC should send with in bps */
int32_t WebRtcIsac_GetUplinkBandwidth(const BwEstimatorstr* bwest_str);

/* Returns the max delay value from the other side in ms */
int32_t WebRtcIsac_GetUplinkMaxDelay(const BwEstimatorstr* bwest_str);

/*
 * update amount of data in bottle neck buffer and burst handling
 * returns minimum payload size (bytes)
 */
int WebRtcIsac_GetMinBytes(
    RateModel* State,
    int StreamSize,      /* bytes in bitstream */
    int FrameLen,        /* ms per frame */
    double BottleNeck,   /* bottle neck rate; excl headers (bps) */
    double DelayBuildUp, /* max delay from bottleneck buffering (ms) */
    enum ISACBandwidth bandwidth
    /*,int16_t        frequentLargePackets*/);

/*
 * update long-term average bitrate and amount of data in buffer
 */
void WebRtcIsac_UpdateRateModel(
    RateModel* State,
    int StreamSize,     /* bytes in bitstream */
    int FrameSamples,   /* samples per frame */
    double BottleNeck); /* bottle neck rate; excl headers (bps) */

void WebRtcIsac_InitRateModel(RateModel* State);

/* Returns the new framelength value (input argument: bottle_neck) */
int WebRtcIsac_GetNewFrameLength(double bottle_neck, int current_framelength);

/* Returns the new SNR value (input argument: bottle_neck) */
double WebRtcIsac_GetSnr(double bottle_neck, int new_framelength);

int16_t WebRtcIsac_UpdateUplinkJitter(BwEstimatorstr* bwest_str, int32_t index);

#if defined(__cplusplus)
}
#endif

#endif /* MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_BANDWIDTH_ESTIMATOR_H_ \
        */

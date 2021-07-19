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
 * structs.h
 *
 * This header file contains all the structs used in the ISAC codec
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_STRUCTS_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_STRUCTS_H_

#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "modules/audio_coding/codecs/isac/bandwidth_info.h"
#include "modules/audio_coding/codecs/isac/fix/source/settings.h"

/* Bitstream struct for decoder */
typedef struct Bitstreamstruct_dec {
  uint16_t stream[INTERNAL_STREAM_SIZE_W16]; /* Array bytestream to decode */
  uint32_t W_upper;                          /* Upper boundary of interval W */
  uint32_t streamval;
  uint16_t stream_index; /* Index to the current position in bytestream */
  int16_t full;          /* 0 - first byte in memory filled, second empty*/
  /* 1 - both bytes are empty (we just filled the previous memory */

  size_t stream_size; /* The size of stream in bytes. */
} Bitstr_dec;

/* Bitstream struct for encoder */
typedef struct Bitstreamstruct_enc {
  uint16_t
      stream[STREAM_MAXW16_60MS]; /* Vector for adding encoded bytestream */
  uint32_t W_upper;               /* Upper boundary of interval W */
  uint32_t streamval;
  uint16_t stream_index; /* Index to the current position in bytestream */
  int16_t full;          /* 0 - first byte in memory filled, second empty*/
  /* 1 - both bytes are empty (we just filled the previous memory */

} Bitstr_enc;

typedef struct {
  int16_t DataBufferLoQ0[WINLEN];
  int16_t DataBufferHiQ0[WINLEN];

  int32_t CorrBufLoQQ[ORDERLO + 1];
  int32_t CorrBufHiQQ[ORDERHI + 1];

  int16_t CorrBufLoQdom[ORDERLO + 1];
  int16_t CorrBufHiQdom[ORDERHI + 1];

  int32_t PreStateLoGQ15[ORDERLO + 1];
  int32_t PreStateHiGQ15[ORDERHI + 1];

  uint32_t OldEnergy;

} MaskFiltstr_enc;

typedef struct {
  int16_t PostStateLoGQ0[ORDERLO + 1];
  int16_t PostStateHiGQ0[ORDERHI + 1];

  uint32_t OldEnergy;

} MaskFiltstr_dec;

typedef struct {
  // state vectors for each of the two analysis filters

  int32_t INSTAT1_fix[2 * (QORDER - 1)];
  int32_t INSTAT2_fix[2 * (QORDER - 1)];
  int16_t INLABUF1_fix[QLOOKAHEAD];
  int16_t INLABUF2_fix[QLOOKAHEAD];

  /* High pass filter */
  int32_t HPstates_fix[HPORDER];

} PreFiltBankstr;

typedef struct {
  // state vectors for each of the two analysis filters
  int32_t STATE_0_LOWER_fix[2 * POSTQORDER];
  int32_t STATE_0_UPPER_fix[2 * POSTQORDER];

  /* High pass filter */

  int32_t HPstates1_fix[HPORDER];
  int32_t HPstates2_fix[HPORDER];

} PostFiltBankstr;

typedef struct {
  /* data buffer for pitch filter */
  int16_t ubufQQ[PITCH_BUFFSIZE];

  /* low pass state vector */
  int16_t ystateQQ[PITCH_DAMPORDER];

  /* old lag and gain */
  int16_t oldlagQ7;
  int16_t oldgainQ12;

} PitchFiltstr;

typedef struct {
  // for inital estimator
  int16_t dec_buffer16[PITCH_CORR_LEN2 + PITCH_CORR_STEP2 + PITCH_MAX_LAG / 2 -
                       PITCH_FRAME_LEN / 2 + 2];
  int32_t decimator_state32[2 * ALLPASSSECTIONS + 1];
  int16_t inbuf[QLOOKAHEAD];

  PitchFiltstr PFstr_wght;
  PitchFiltstr PFstr;

} PitchAnalysisStruct;

typedef struct {
  /* Parameters used in PLC to avoid re-computation       */

  /* --- residual signals --- */
  int16_t prevPitchInvIn[FRAMESAMPLES / 2];
  int16_t prevPitchInvOut[PITCH_MAX_LAG + 10];  // [FRAMESAMPLES/2]; save 90
  int32_t prevHP[PITCH_MAX_LAG + 10];           // [FRAMESAMPLES/2]; save 90

  int16_t decayCoeffPriodic; /* how much to supress a sample */
  int16_t decayCoeffNoise;
  int16_t used; /* if PLC is used */

  int16_t* lastPitchLP;  // [FRAMESAMPLES/2]; saved 240;

  /* --- LPC side info --- */
  int16_t lofilt_coefQ15[ORDERLO];
  int16_t hifilt_coefQ15[ORDERHI];
  int32_t gain_lo_hiQ17[2];

  /* --- LTP side info --- */
  int16_t AvgPitchGain_Q12;
  int16_t lastPitchGain_Q12;
  int16_t lastPitchLag_Q7;

  /* --- Add-overlap in recovery packet --- */
  int16_t overlapLP[RECOVERY_OVERLAP];  // [FRAMESAMPLES/2]; saved 160

  int16_t pitchCycles;
  int16_t A;
  int16_t B;
  size_t pitchIndex;
  size_t stretchLag;
  int16_t* prevPitchLP;  // [ FRAMESAMPLES/2 ]; saved 240
  int16_t seed;

  int16_t std;
} PLCstr;

/* Have instance of struct together with other iSAC structs */
typedef struct {
  int16_t prevFrameSizeMs; /* Previous frame size (in ms) */
  uint16_t prevRtpNumber;  /* Previous RTP timestamp from received packet */
  /* (in samples relative beginning)  */
  uint32_t prevSendTime;    /* Send time for previous packet, from RTP header */
  uint32_t prevArrivalTime; /* Arrival time for previous packet (in ms using
                               timeGetTime()) */
  uint16_t prevRtpRate; /* rate of previous packet, derived from RTP timestamps
                           (in bits/s) */
  uint32_t lastUpdate; /* Time since the last update of the Bottle Neck estimate
                          (in samples) */
  uint32_t lastReduction; /* Time sinse the last reduction (in samples) */
  int32_t countUpdates;   /* How many times the estimate was update in the
                             beginning */

  /* The estimated bottle neck rate from there to here (in bits/s) */
  uint32_t recBw;
  uint32_t recBwInv;
  uint32_t recBwAvg;
  uint32_t recBwAvgQ;

  uint32_t minBwInv;
  uint32_t maxBwInv;

  /* The estimated mean absolute jitter value, as seen on this side (in ms) */
  int32_t recJitter;
  int32_t recJitterShortTerm;
  int32_t recJitterShortTermAbs;
  int32_t recMaxDelay;
  int32_t recMaxDelayAvgQ;

  int16_t recHeaderRate; /* (assumed) bitrate for headers (bps) */

  uint32_t sendBwAvg; /* The estimated bottle neck rate from here to there (in
                         bits/s) */
  int32_t sendMaxDelayAvg; /* The estimated mean absolute jitter value, as seen
                              on the other siee (in ms)  */

  int16_t countRecPkts; /* number of packets received since last update */
  int16_t highSpeedRec; /* flag for marking that a high speed network has been
                           detected downstream */

  /* number of consecutive pkts sent during which the bwe estimate has
     remained at a value greater than the downstream threshold for determining
     highspeed network */
  int16_t countHighSpeedRec;

  /* flag indicating bwe should not adjust down immediately for very late pckts
   */
  int16_t inWaitPeriod;

  /* variable holding the time of the start of a window of time when
     bwe should not adjust down immediately for very late pckts */
  uint32_t startWaitPeriod;

  /* number of consecutive pkts sent during which the bwe estimate has
     remained at a value greater than the upstream threshold for determining
     highspeed network */
  int16_t countHighSpeedSent;

  /* flag indicated the desired number of packets over threshold rate have been
     sent and bwe will assume the connection is over broadband network */
  int16_t highSpeedSend;

  IsacBandwidthInfo external_bw_info;
} BwEstimatorstr;

typedef struct {
  /* boolean, flags if previous packet exceeded B.N. */
  int16_t PrevExceed;
  /* ms */
  int16_t ExceedAgo;
  /* packets left to send in current burst */
  int16_t BurstCounter;
  /* packets */
  int16_t InitCounter;
  /* ms remaining in buffer when next packet will be sent */
  int16_t StillBuffered;

} RateModel;

/* The following strutc is used to store data from encoding, to make it
   fast and easy to construct a new bitstream with a different Bandwidth
   estimate. All values (except framelength and minBytes) is double size to
   handle 60 ms of data.
*/
typedef struct {
  /* Used to keep track of if it is first or second part of 60 msec packet */
  int startIdx;

  /* Frame length in samples */
  int16_t framelength;

  /* Pitch Gain */
  int16_t pitchGain_index[2];

  /* Pitch Lag */
  int32_t meanGain[2];
  int16_t pitchIndex[PITCH_SUBFRAMES * 2];

  /* LPC */
  int32_t LPCcoeffs_g[12 * 2]; /* KLT_ORDER_GAIN = 12 */
  int16_t LPCindex_s[108 * 2]; /* KLT_ORDER_SHAPE = 108 */
  int16_t LPCindex_g[12 * 2];  /* KLT_ORDER_GAIN = 12 */

  /* Encode Spec */
  int16_t fre[FRAMESAMPLES];
  int16_t fim[FRAMESAMPLES];
  int16_t AvgPitchGain[2];

  /* Used in adaptive mode only */
  int minBytes;

} IsacSaveEncoderData;

typedef struct {
  Bitstr_enc bitstr_obj;
  MaskFiltstr_enc maskfiltstr_obj;
  PreFiltBankstr prefiltbankstr_obj;
  PitchFiltstr pitchfiltstr_obj;
  PitchAnalysisStruct pitchanalysisstr_obj;
  RateModel rate_data_obj;

  int16_t buffer_index;
  int16_t current_framesamples;

  int16_t data_buffer_fix[FRAMESAMPLES];  // the size was MAX_FRAMESAMPLES

  int16_t frame_nb;
  int16_t BottleNeck;
  int16_t MaxDelay;
  int16_t new_framelength;
  int16_t s2nr;
  uint16_t MaxBits;

  int16_t bitstr_seed;

  IsacSaveEncoderData* SaveEnc_ptr;
  int16_t payloadLimitBytes30; /* Maximum allowed number of bits for a 30 msec
                                  packet */
  int16_t payloadLimitBytes60; /* Maximum allowed number of bits for a 30 msec
                                  packet */
  int16_t maxPayloadBytes; /* Maximum allowed number of bits for both 30 and 60
                              msec packet */
  int16_t maxRateInBytes; /* Maximum allowed rate in bytes per 30 msec packet */
  int16_t enforceFrameSize; /* If set iSAC will never change packet size */

} IsacFixEncoderInstance;

typedef struct {
  Bitstr_dec bitstr_obj;
  MaskFiltstr_dec maskfiltstr_obj;
  PostFiltBankstr postfiltbankstr_obj;
  PitchFiltstr pitchfiltstr_obj;
  PLCstr plcstr_obj; /* TS; for packet loss concealment */
} IsacFixDecoderInstance;

typedef struct {
  IsacFixEncoderInstance ISACenc_obj;
  IsacFixDecoderInstance ISACdec_obj;
  BwEstimatorstr bwestimator_obj;
  int16_t CodingMode; /* 0 = adaptive; 1 = instantaneous */
  int16_t errorcode;
  int16_t initflag; /* 0 = nothing initiated; 1 = encoder or decoder */
  /* not initiated; 2 = all initiated */
} ISACFIX_SubStruct;

typedef struct {
  int32_t lpcGains[12]; /* 6 lower-band & 6 upper-band we may need to double it
                           for 60*/
  /* */
  uint32_t W_upper; /* Upper boundary of interval W */
  uint32_t streamval;
  uint16_t stream_index; /* Index to the current position in bytestream */
  int16_t full;          /* 0 - first byte in memory filled, second empty*/
  /* 1 - both bytes are empty (we just filled the previous memory */
  uint16_t beforeLastWord;
  uint16_t lastWord;
} transcode_obj;

// Bitstr_enc myBitStr;

#endif /* MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_STRUCTS_H_ */

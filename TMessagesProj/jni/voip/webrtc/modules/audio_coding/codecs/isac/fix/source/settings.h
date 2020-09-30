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
 * settings.h
 *
 * Declaration of #defines used in the iSAC codec
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_SETTINGS_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_SETTINGS_H_

/* sampling frequency (Hz) */
#define FS 16000
/* 1.5 times Sampling frequency */
#define FS_1_HALF (uint32_t)24000
/* Three times Sampling frequency */
#define FS3 (uint32_t)48000
/* Eight times Sampling frequency */
#define FS8 (uint32_t)128000

/* number of samples per frame (either 480 (30ms) or 960 (60ms)) */
#define INITIAL_FRAMESAMPLES 960

/* miliseconds */
#define FRAMESIZE 30
/* number of samples per frame processed in the encoder (30ms) */
#define FRAMESAMPLES 480 /* ((FRAMESIZE*FS)/1000) */
#define FRAMESAMPLES_HALF 240
/* max number of samples per frame (= 60 ms frame) */
#define MAX_FRAMESAMPLES 960
/* number of samples per 10ms frame */
#define FRAMESAMPLES_10ms 160 /* ((10*FS)/1000) */
/* Number of samples per 1 ms */
#define SAMPLES_PER_MSEC 16
/* number of subframes */
#define SUBFRAMES 6
/* length of a subframe */
#define UPDATE 80
/* length of half a subframe (low/high band) */
#define HALF_SUBFRAMELEN 40 /* (UPDATE/2) */
/* samples of look ahead (in a half-band, so actually half the samples of look
 * ahead @ FS) */
#define QLOOKAHEAD 24 /* 3 ms */

/* order of AR model in spectral entropy coder */
#define AR_ORDER 6
#define MAX_ORDER 13
#define LEVINSON_MAX_ORDER 12

/* window length (masking analysis) */
#define WINLEN 256
/* order of low-band pole filter used to approximate masking curve */
#define ORDERLO 12
/* order of hi-band pole filter used to approximate masking curve */
#define ORDERHI 6

#define KLT_NUM_AVG_GAIN 0
#define KLT_NUM_AVG_SHAPE 0
#define KLT_NUM_MODELS 3
#define LPC_SHAPE_ORDER 18 /* (ORDERLO + ORDERHI) */

#define KLT_ORDER_GAIN 12   /* (2 * SUBFRAMES) */
#define KLT_ORDER_SHAPE 108 /*  (LPC_SHAPE_ORDER * SUBFRAMES) */

/* order for post_filter_bank */
#define POSTQORDER 3
/* order for pre-filterbank */
#define QORDER 3
/* for decimator */
#define ALLPASSSECTIONS 2
/* The number of composite all-pass filter factors */
#define NUMBEROFCOMPOSITEAPSECTIONS 4

/* The number of all-pass filter factors in an upper or lower channel*/
#define NUMBEROFCHANNELAPSECTIONS 2

#define DPMIN_Q10 -10240  /* -10.00 in Q10 */
#define DPMAX_Q10 10240   /* 10.00 in Q10 */
#define MINBITS_Q10 10240 /* 10.0 in Q10 */

/* array size for byte stream in number of Word16. */
#define STREAM_MAXW16 \
  300 /* The old maximum size still needed for the decoding */
#define STREAM_MAXW16_30MS \
  100 /* 100 Word16 = 200 bytes = 53.4 kbit/s @ 30 ms.framelength */
#define STREAM_MAXW16_60MS \
  200 /* 200 Word16 = 400 bytes = 53.4 kbit/s @ 60 ms.framelength */
/* This is used only at the decoder bit-stream struct.
 * - The encoder and decoder bitstream containers are of different size because
 *   old iSAC limited the encoded bitstream to 600 bytes. But newer versions
 *   restrict to shorter bitstream.
 * - We add 10 bytes of guards to the internal bitstream container. The reason
 *   is that entropy decoder might read few bytes (3 according to our
 *   observations) more than the actual size of the bitstream. To avoid reading
 *   outside memory, in rare occasion of full-size bitstream we add 10 bytes
 *   of guard. */
#define INTERNAL_STREAM_SIZE_W16 (STREAM_MAXW16 + 5)

/* storage size for bit counts */
//#define BIT_COUNTER_SIZE                        30
/* maximum order of any AR model or filter */
#define MAX_AR_MODEL_ORDER 12

/* Maximum number of iterations allowed to limit payload size */
#define MAX_PAYLOAD_LIMIT_ITERATION 1

/* Bandwidth estimator */

#define MIN_ISAC_BW 10000 /* Minimum bandwidth in bits per sec */
#define MAX_ISAC_BW 32000 /* Maxmum bandwidth in bits per sec */
#define MIN_ISAC_MD 5     /* Minimum Max Delay in ?? */
#define MAX_ISAC_MD 25    /* Maxmum Max Delay in ?? */
#define DELAY_CORRECTION_MAX 717
#define DELAY_CORRECTION_MED 819
#define Thld_30_60 18000
#define Thld_60_30 27000

/* assumed header size; we don't know the exact number (header compression may
 * be used) */
#define HEADER_SIZE 35 /* bytes */
#define INIT_FRAME_LEN 60
#define INIT_BN_EST 20000
#define INIT_BN_EST_Q7 2560000    /* 20 kbps in Q7 */
#define INIT_REC_BN_EST_Q5 789312 /* INIT_BN_EST + INIT_HDR_RATE in Q5 */

/* 8738 in Q18 is ~ 1/30 */
/* #define INIT_HDR_RATE (((HEADER_SIZE * 8 * 1000) * 8738) >> NUM_BITS_TO_SHIFT
 * (INIT_FRAME_LEN)) */
#define INIT_HDR_RATE 4666
/* number of packets in a row for a high rate burst */
#define BURST_LEN 3
/* ms, max time between two full bursts */
#define BURST_INTERVAL 800
/* number of packets in a row for initial high rate burst */
#define INIT_BURST_LEN 5
/* bits/s, rate for the first BURST_LEN packets */
#define INIT_RATE 10240000 /* INIT_BN_EST in Q9 */

/* For pitch analysis */
#define PITCH_FRAME_LEN 240   /* (FRAMESAMPLES/2) 30 ms  */
#define PITCH_MAX_LAG 140     /* 57 Hz  */
#define PITCH_MIN_LAG 20      /* 400 Hz */
#define PITCH_MIN_LAG_Q8 5120 /* 256 * PITCH_MIN_LAG */
#define OFFSET_Q8 768         /* 256 * 3 */

#define PITCH_MAX_GAIN_Q12 1843 /* 0.45 */
#define PITCH_LAG_SPAN2 65      /* (PITCH_MAX_LAG/2-PITCH_MIN_LAG/2+5) */
#define PITCH_CORR_LEN2 60      /* 15 ms  */
#define PITCH_CORR_STEP2 60     /* (PITCH_FRAME_LEN/4) */
#define PITCH_SUBFRAMES 4
#define PITCH_SUBFRAME_LEN 60 /* (PITCH_FRAME_LEN/PITCH_SUBFRAMES) */

/* For pitch filter */
#define PITCH_BUFFSIZE \
  190 /* (PITCH_MAX_LAG + 50) Extra 50 for fraction and LP filters */
#define PITCH_INTBUFFSIZE 430 /* (PITCH_FRAME_LEN+PITCH_BUFFSIZE) */
#define PITCH_FRACS 8
#define PITCH_FRACORDER 9
#define PITCH_DAMPORDER 5

/* Order of high pass filter */
#define HPORDER 2

/* PLC */
#define DECAY_RATE \
  10 /* Q15, 20% of decay every lost frame apllied linearly sample by sample*/
#define PLC_WAS_USED 1
#define PLC_NOT_USED 3
#define RECOVERY_OVERLAP 80
#define RESAMP_RES 256
#define RESAMP_RES_BIT 8

/* Define Error codes */
/* 6000 General */
#define ISAC_MEMORY_ALLOCATION_FAILED 6010
#define ISAC_MODE_MISMATCH 6020
#define ISAC_DISALLOWED_BOTTLENECK 6030
#define ISAC_DISALLOWED_FRAME_LENGTH 6040
/* 6200 Bandwidth estimator */
#define ISAC_RANGE_ERROR_BW_ESTIMATOR 6240
/* 6400 Encoder */
#define ISAC_ENCODER_NOT_INITIATED 6410
#define ISAC_DISALLOWED_CODING_MODE 6420
#define ISAC_DISALLOWED_FRAME_MODE_ENCODER 6430
#define ISAC_DISALLOWED_BITSTREAM_LENGTH 6440
#define ISAC_PAYLOAD_LARGER_THAN_LIMIT 6450
/* 6600 Decoder */
#define ISAC_DECODER_NOT_INITIATED 6610
#define ISAC_EMPTY_PACKET 6620
#define ISAC_PACKET_TOO_SHORT 6625
#define ISAC_DISALLOWED_FRAME_MODE_DECODER 6630
#define ISAC_RANGE_ERROR_DECODE_FRAME_LENGTH 6640
#define ISAC_RANGE_ERROR_DECODE_BANDWIDTH 6650
#define ISAC_RANGE_ERROR_DECODE_PITCH_GAIN 6660
#define ISAC_RANGE_ERROR_DECODE_PITCH_LAG 6670
#define ISAC_RANGE_ERROR_DECODE_LPC 6680
#define ISAC_RANGE_ERROR_DECODE_SPECTRUM 6690
#define ISAC_LENGTH_MISMATCH 6730
/* 6800 Call setup formats */
#define ISAC_INCOMPATIBLE_FORMATS 6810

#endif /* MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_SETTINGS_H_ */

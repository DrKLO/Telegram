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
 * BwEstimator.c
 *
 * This file contains the code for the Bandwidth Estimator designed
 * for iSAC.
 *
 */

#include <math.h>
#include <string.h>

#include "modules/audio_coding/codecs/isac/main/source/bandwidth_estimator.h"
#include "modules/audio_coding/codecs/isac/main/source/settings.h"
#include "modules/audio_coding/codecs/isac/main/include/isac.h"
#include "rtc_base/checks.h"

/* array of quantization levels for bottle neck info; Matlab code: */
/* sprintf('%4.1ff, ', logspace(log10(5000), log10(40000), 12)) */
static const float kQRateTableWb[12] =
{
  10000.0f, 11115.3f, 12355.1f, 13733.1f, 15264.8f, 16967.3f,
  18859.8f, 20963.3f, 23301.4f, 25900.3f, 28789.0f, 32000.0f};


static const float kQRateTableSwb[24] =
{
  10000.0f, 11115.3f, 12355.1f, 13733.1f, 15264.8f, 16967.3f,
  18859.8f, 20963.3f, 23153.1f, 25342.9f, 27532.7f, 29722.5f,
  31912.3f, 34102.1f, 36291.9f, 38481.7f, 40671.4f, 42861.2f,
  45051.0f, 47240.8f, 49430.6f, 51620.4f, 53810.2f, 56000.0f,
};




int32_t WebRtcIsac_InitBandwidthEstimator(
    BwEstimatorstr*              bwest_str,
    enum IsacSamplingRate encoderSampRate,
    enum IsacSamplingRate decoderSampRate)
{
  switch(encoderSampRate)
  {
    case kIsacWideband:
      {
        bwest_str->send_bw_avg       = INIT_BN_EST_WB;
        break;
      }
    case kIsacSuperWideband:
      {
        bwest_str->send_bw_avg       = INIT_BN_EST_SWB;
        break;
      }
  }

  switch(decoderSampRate)
  {
    case kIsacWideband:
      {
        bwest_str->prev_frame_length = INIT_FRAME_LEN_WB;
        bwest_str->rec_bw_inv        = 1.0f /
            (INIT_BN_EST_WB + INIT_HDR_RATE_WB);
        bwest_str->rec_bw            = (int32_t)INIT_BN_EST_WB;
        bwest_str->rec_bw_avg_Q      = INIT_BN_EST_WB;
        bwest_str->rec_bw_avg        = INIT_BN_EST_WB + INIT_HDR_RATE_WB;
        bwest_str->rec_header_rate   = INIT_HDR_RATE_WB;
        break;
      }
    case kIsacSuperWideband:
      {
        bwest_str->prev_frame_length = INIT_FRAME_LEN_SWB;
        bwest_str->rec_bw_inv        = 1.0f /
            (INIT_BN_EST_SWB + INIT_HDR_RATE_SWB);
        bwest_str->rec_bw            = (int32_t)INIT_BN_EST_SWB;
        bwest_str->rec_bw_avg_Q      = INIT_BN_EST_SWB;
        bwest_str->rec_bw_avg        = INIT_BN_EST_SWB + INIT_HDR_RATE_SWB;
        bwest_str->rec_header_rate   = INIT_HDR_RATE_SWB;
        break;
      }
  }

  bwest_str->prev_rec_rtp_number       = 0;
  bwest_str->prev_rec_arr_ts           = 0;
  bwest_str->prev_rec_send_ts          = 0;
  bwest_str->prev_rec_rtp_rate         = 1.0f;
  bwest_str->last_update_ts            = 0;
  bwest_str->last_reduction_ts         = 0;
  bwest_str->count_tot_updates_rec     = -9;
  bwest_str->rec_jitter                = 10.0f;
  bwest_str->rec_jitter_short_term     = 0.0f;
  bwest_str->rec_jitter_short_term_abs = 5.0f;
  bwest_str->rec_max_delay             = 10.0f;
  bwest_str->rec_max_delay_avg_Q       = 10.0f;
  bwest_str->num_pkts_rec              = 0;

  bwest_str->send_max_delay_avg        = 10.0f;

  bwest_str->hsn_detect_rec = 0;

  bwest_str->num_consec_rec_pkts_over_30k = 0;

  bwest_str->hsn_detect_snd = 0;

  bwest_str->num_consec_snt_pkts_over_30k = 0;

  bwest_str->in_wait_period = 0;

  bwest_str->change_to_WB = 0;

  bwest_str->numConsecLatePkts = 0;
  bwest_str->consecLatency = 0;
  bwest_str->inWaitLatePkts = 0;
  bwest_str->senderTimestamp = 0;
  bwest_str->receiverTimestamp = 0;

  bwest_str->external_bw_info.in_use = 0;

  return 0;
}

/* This function updates both bottle neck rates                                                      */
/* Parameters:                                                                                       */
/* rtp_number    - value from RTP packet, from NetEq                                                 */
/* frame length  - length of signal frame in ms, from iSAC decoder                                   */
/* send_ts       - value in RTP header giving send time in samples                                     */
/* arr_ts        - value given by timeGetTime() time of arrival in samples of packet from NetEq      */
/* pksize        - size of packet in bytes, from NetEq                                               */
/* Index         - integer (range 0...23) indicating bottle neck & jitter as estimated by other side */
/* returns 0 if everything went fine, -1 otherwise                                                   */
int16_t WebRtcIsac_UpdateBandwidthEstimator(
    BwEstimatorstr* bwest_str,
    const uint16_t rtp_number,
    const int32_t frame_length,
    const uint32_t send_ts,
    const uint32_t arr_ts,
    const size_t pksize
    /*,    const uint16_t Index*/)
{
  float weight = 0.0f;
  float curr_bw_inv = 0.0f;
  float rec_rtp_rate;
  float t_diff_proj;
  float arr_ts_diff;
  float send_ts_diff;
  float arr_time_noise;
  float arr_time_noise_abs;

  float delay_correction_factor = 1;
  float late_diff = 0.0f;
  int immediate_set = 0;
  int num_pkts_expected;

  RTC_DCHECK(!bwest_str->external_bw_info.in_use);

  // We have to adjust the header-rate if the first packet has a
  // frame-size different than the initialized value.
  if ( frame_length != bwest_str->prev_frame_length )
  {
    bwest_str->rec_header_rate = (float)HEADER_SIZE * 8.0f *
        1000.0f / (float)frame_length;     /* bits/s */
  }

  /* UPDATE ESTIMATES ON THIS SIDE */
  /* compute far-side transmission rate */
  rec_rtp_rate = ((float)pksize * 8.0f * 1000.0f / (float)frame_length) +
      bwest_str->rec_header_rate;
  // rec_rtp_rate packet bits/s + header bits/s

  /* check for timer wrap-around */
  if (arr_ts < bwest_str->prev_rec_arr_ts)
  {
    bwest_str->prev_rec_arr_ts   = arr_ts;
    bwest_str->last_update_ts    = arr_ts;
    bwest_str->last_reduction_ts = arr_ts + 3*FS;
    bwest_str->num_pkts_rec      = 0;

    /* store frame length */
    bwest_str->prev_frame_length = frame_length;

    /* store far-side transmission rate */
    bwest_str->prev_rec_rtp_rate = rec_rtp_rate;

    /* store far-side RTP time stamp */
    bwest_str->prev_rec_rtp_number = rtp_number;

    return 0;
  }

  bwest_str->num_pkts_rec++;

  /* check that it's not one of the first 9 packets */
  if ( bwest_str->count_tot_updates_rec > 0 )
  {
    if(bwest_str->in_wait_period > 0 )
    {
      bwest_str->in_wait_period--;
    }

    bwest_str->inWaitLatePkts -= ((bwest_str->inWaitLatePkts > 0)? 1:0);
    send_ts_diff = (float)(send_ts - bwest_str->prev_rec_send_ts);

    if (send_ts_diff <= (16 * frame_length)*2)
      //doesn't allow for a dropped packet, not sure necessary to be
      // that strict -DH
    {
      /* if not been updated for a long time, reduce the BN estimate */
      if((uint32_t)(arr_ts - bwest_str->last_update_ts) *
         1000.0f / FS > 3000)
      {
        //how many frames should have been received since the last
        // update if too many have been dropped or there have been
        // big delays won't allow this reduction may no longer need
        // the send_ts_diff here
        num_pkts_expected = (int)(((float)(arr_ts -
                                           bwest_str->last_update_ts) * 1000.0f /(float) FS) /
                                  (float)frame_length);

        if(((float)bwest_str->num_pkts_rec/(float)num_pkts_expected) >
           0.9)
        {
          float inv_bitrate = (float) pow( 0.99995,
                                           (double)((uint32_t)(arr_ts -
                                                                     bwest_str->last_reduction_ts)*1000.0f/FS) );

          if ( inv_bitrate )
          {
            bwest_str->rec_bw_inv /= inv_bitrate;

            //precautionary, likely never necessary
            if (bwest_str->hsn_detect_snd &&
                bwest_str->hsn_detect_rec)
            {
              if (bwest_str->rec_bw_inv > 0.000066f)
              {
                bwest_str->rec_bw_inv = 0.000066f;
              }
            }
          }
          else
          {
            bwest_str->rec_bw_inv = 1.0f /
                (INIT_BN_EST_WB + INIT_HDR_RATE_WB);
          }
          /* reset time-since-update counter */
          bwest_str->last_reduction_ts = arr_ts;
        }
        else
          //reset here?
        {
          bwest_str->last_reduction_ts = arr_ts + 3*FS;
          bwest_str->last_update_ts = arr_ts;
          bwest_str->num_pkts_rec = 0;
        }
      }
    }
    else
    {
      bwest_str->last_reduction_ts = arr_ts + 3*FS;
      bwest_str->last_update_ts = arr_ts;
      bwest_str->num_pkts_rec = 0;
    }


    /* temporarily speed up adaptation if frame length has changed */
    if ( frame_length != bwest_str->prev_frame_length )
    {
      bwest_str->count_tot_updates_rec = 10;
      bwest_str->rec_header_rate = (float)HEADER_SIZE * 8.0f *
          1000.0f / (float)frame_length;     /* bits/s */

      bwest_str->rec_bw_inv = 1.0f /((float)bwest_str->rec_bw +
                                     bwest_str->rec_header_rate);
    }

    ////////////////////////
    arr_ts_diff = (float)(arr_ts - bwest_str->prev_rec_arr_ts);

    if (send_ts_diff > 0 )
    {
      late_diff = arr_ts_diff - send_ts_diff;
    }
    else
    {
      late_diff = arr_ts_diff - (float)(16 * frame_length);
    }

    if((late_diff > 0) && !bwest_str->inWaitLatePkts)
    {
      bwest_str->numConsecLatePkts++;
      bwest_str->consecLatency += late_diff;
    }
    else
    {
      bwest_str->numConsecLatePkts = 0;
      bwest_str->consecLatency = 0;
    }
    if(bwest_str->numConsecLatePkts > 50)
    {
      float latencyMs = bwest_str->consecLatency/(FS/1000);
      float averageLatencyMs = latencyMs / bwest_str->numConsecLatePkts;
      delay_correction_factor = frame_length / (frame_length + averageLatencyMs);
      immediate_set = 1;
      bwest_str->inWaitLatePkts = (int16_t)((bwest_str->consecLatency/(FS/1000)) / 30);// + 150;
      bwest_str->start_wait_period = arr_ts;
    }
    ///////////////////////////////////////////////



    /*   update only if previous packet was not lost */
    if ( rtp_number == bwest_str->prev_rec_rtp_number + 1 )
    {


      if (!(bwest_str->hsn_detect_snd && bwest_str->hsn_detect_rec))
      {
        if ((arr_ts_diff > (float)(16 * frame_length)))
        {
          //1/2 second
          if ((late_diff > 8000.0f) && !bwest_str->in_wait_period)
          {
            delay_correction_factor = 0.7f;
            bwest_str->in_wait_period = 55;
            bwest_str->start_wait_period = arr_ts;
            immediate_set = 1;
          }
          //320 ms
          else if (late_diff > 5120.0f && !bwest_str->in_wait_period)
          {
            delay_correction_factor = 0.8f;
            immediate_set = 1;
            bwest_str->in_wait_period = 44;
            bwest_str->start_wait_period = arr_ts;
          }
        }
      }


      if ((bwest_str->prev_rec_rtp_rate > bwest_str->rec_bw_avg) &&
          (rec_rtp_rate > bwest_str->rec_bw_avg)                 &&
          !bwest_str->in_wait_period)
      {
        /* test if still in initiation period and increment counter */
        if (bwest_str->count_tot_updates_rec++ > 99)
        {
          /* constant weight after initiation part */
          weight = 0.01f;
        }
        else
        {
          /* weight decreases with number of updates */
          weight = 1.0f / (float) bwest_str->count_tot_updates_rec;
        }
        /* Bottle Neck Estimation */

        /* limit outliers */
        /* if more than 25 ms too much */
        if (arr_ts_diff > frame_length * FS/1000 + 400.0f)
        {
          // in samples,  why 25ms??
          arr_ts_diff = frame_length * FS/1000 + 400.0f;
        }
        if(arr_ts_diff < (frame_length * FS/1000) - 160.0f)
        {
          /* don't allow it to be less than frame rate - 10 ms */
          arr_ts_diff = (float)frame_length * FS/1000 - 160.0f;
        }

        /* compute inverse receiving rate for last packet */
        curr_bw_inv = arr_ts_diff / ((float)(pksize + HEADER_SIZE) *
                                     8.0f * FS); // (180+35)*8*16000 = 27.5 Mbit....


        if(curr_bw_inv <
           (1.0f / (MAX_ISAC_BW + bwest_str->rec_header_rate)))
        {
          // don't allow inv rate to be larger than MAX
          curr_bw_inv = (1.0f /
                         (MAX_ISAC_BW + bwest_str->rec_header_rate));
        }

        /* update bottle neck rate estimate */
        bwest_str->rec_bw_inv = weight * curr_bw_inv +
            (1.0f - weight) * bwest_str->rec_bw_inv;

        /* reset time-since-update counter */
        bwest_str->last_update_ts    = arr_ts;
        bwest_str->last_reduction_ts = arr_ts + 3 * FS;
        bwest_str->num_pkts_rec = 0;

        /* Jitter Estimation */
        /* projected difference between arrival times */
        t_diff_proj = ((float)(pksize + HEADER_SIZE) * 8.0f *
                       1000.0f) / bwest_str->rec_bw_avg;


        // difference between projected and actual
        //   arrival time differences
        arr_time_noise = (float)(arr_ts_diff*1000.0f/FS) -
            t_diff_proj;
        arr_time_noise_abs = (float) fabs( arr_time_noise );

        /* long term averaged absolute jitter */
        bwest_str->rec_jitter = weight * arr_time_noise_abs +
            (1.0f - weight) * bwest_str->rec_jitter;
        if (bwest_str->rec_jitter > 10.0f)
        {
          bwest_str->rec_jitter = 10.0f;
        }
        /* short term averaged absolute jitter */
        bwest_str->rec_jitter_short_term_abs = 0.05f *
            arr_time_noise_abs + 0.95f *
            bwest_str->rec_jitter_short_term_abs;

        /* short term averaged jitter */
        bwest_str->rec_jitter_short_term = 0.05f * arr_time_noise +
            0.95f * bwest_str->rec_jitter_short_term;
      }
    }
  }
  else
  {
    // reset time-since-update counter when
    // receiving the first 9 packets
    bwest_str->last_update_ts    = arr_ts;
    bwest_str->last_reduction_ts = arr_ts + 3*FS;
    bwest_str->num_pkts_rec = 0;

    bwest_str->count_tot_updates_rec++;
  }

  /* limit minimum bottle neck rate */
  if (bwest_str->rec_bw_inv > 1.0f / ((float)MIN_ISAC_BW +
                                      bwest_str->rec_header_rate))
  {
    bwest_str->rec_bw_inv = 1.0f / ((float)MIN_ISAC_BW +
                                    bwest_str->rec_header_rate);
  }

  // limit maximum bitrate
  if (bwest_str->rec_bw_inv < 1.0f / ((float)MAX_ISAC_BW +
                                      bwest_str->rec_header_rate))
  {
    bwest_str->rec_bw_inv = 1.0f / ((float)MAX_ISAC_BW +
                                    bwest_str->rec_header_rate);
  }

  /* store frame length */
  bwest_str->prev_frame_length = frame_length;

  /* store far-side transmission rate */
  bwest_str->prev_rec_rtp_rate = rec_rtp_rate;

  /* store far-side RTP time stamp */
  bwest_str->prev_rec_rtp_number = rtp_number;

  // Replace bwest_str->rec_max_delay by the new
  // value (atomic operation)
  bwest_str->rec_max_delay = 3.0f * bwest_str->rec_jitter;

  /* store send and arrival time stamp */
  bwest_str->prev_rec_arr_ts = arr_ts ;
  bwest_str->prev_rec_send_ts = send_ts;

  /* Replace bwest_str->rec_bw by the new value (atomic operation) */
  bwest_str->rec_bw = (int32_t)(1.0f / bwest_str->rec_bw_inv -
                                      bwest_str->rec_header_rate);

  if (immediate_set)
  {
    bwest_str->rec_bw = (int32_t) (delay_correction_factor *
                                         (float) bwest_str->rec_bw);

    if (bwest_str->rec_bw < (int32_t) MIN_ISAC_BW)
    {
      bwest_str->rec_bw = (int32_t) MIN_ISAC_BW;
    }

    bwest_str->rec_bw_avg = bwest_str->rec_bw +
        bwest_str->rec_header_rate;

    bwest_str->rec_bw_avg_Q = (float) bwest_str->rec_bw;

    bwest_str->rec_jitter_short_term = 0.0f;

    bwest_str->rec_bw_inv = 1.0f / (bwest_str->rec_bw +
                                    bwest_str->rec_header_rate);

    bwest_str->count_tot_updates_rec = 1;

    immediate_set = 0;
    bwest_str->consecLatency = 0;
    bwest_str->numConsecLatePkts = 0;
  }

  return 0;
}


/* This function updates the send bottle neck rate                                                   */
/* Index         - integer (range 0...23) indicating bottle neck & jitter as estimated by other side */
/* returns 0 if everything went fine, -1 otherwise                                                   */
int16_t WebRtcIsac_UpdateUplinkBwImpl(
    BwEstimatorstr*           bwest_str,
    int16_t               index,
    enum IsacSamplingRate encoderSamplingFreq)
{
  RTC_DCHECK(!bwest_str->external_bw_info.in_use);

  if((index < 0) || (index > 23))
  {
    return -ISAC_RANGE_ERROR_BW_ESTIMATOR;
  }

  /* UPDATE ESTIMATES FROM OTHER SIDE */
  if(encoderSamplingFreq == kIsacWideband)
  {
    if(index > 11)
    {
      index -= 12;
      /* compute the jitter estimate as decoded on the other side */
      bwest_str->send_max_delay_avg = 0.9f * bwest_str->send_max_delay_avg +
          0.1f * (float)MAX_ISAC_MD;
    }
    else
    {
      /* compute the jitter estimate as decoded on the other side */
      bwest_str->send_max_delay_avg = 0.9f * bwest_str->send_max_delay_avg +
          0.1f * (float)MIN_ISAC_MD;
    }

    /* compute the BN estimate as decoded on the other side */
    bwest_str->send_bw_avg = 0.9f * bwest_str->send_bw_avg +
        0.1f * kQRateTableWb[index];
  }
  else
  {
    /* compute the BN estimate as decoded on the other side */
    bwest_str->send_bw_avg = 0.9f * bwest_str->send_bw_avg +
        0.1f * kQRateTableSwb[index];
  }

  if (bwest_str->send_bw_avg > (float) 28000 && !bwest_str->hsn_detect_snd)
  {
    bwest_str->num_consec_snt_pkts_over_30k++;

    if (bwest_str->num_consec_snt_pkts_over_30k >= 66)
    {
      //approx 2 seconds with 30ms frames
      bwest_str->hsn_detect_snd = 1;
    }
  }
  else if (!bwest_str->hsn_detect_snd)
  {
    bwest_str->num_consec_snt_pkts_over_30k = 0;
  }
  return 0;
}

// called when there is upper-band bit-stream to update jitter
// statistics.
int16_t WebRtcIsac_UpdateUplinkJitter(
    BwEstimatorstr*              bwest_str,
    int32_t                  index)
{
  RTC_DCHECK(!bwest_str->external_bw_info.in_use);

  if((index < 0) || (index > 23))
  {
    return -ISAC_RANGE_ERROR_BW_ESTIMATOR;
  }

  if(index > 0)
  {
    /* compute the jitter estimate as decoded on the other side */
    bwest_str->send_max_delay_avg = 0.9f * bwest_str->send_max_delay_avg +
        0.1f * (float)MAX_ISAC_MD;
  }
  else
  {
    /* compute the jitter estimate as decoded on the other side */
    bwest_str->send_max_delay_avg = 0.9f * bwest_str->send_max_delay_avg +
        0.1f * (float)MIN_ISAC_MD;
  }

  return 0;
}



// Returns the bandwidth/jitter estimation code (integer 0...23)
// to put in the sending iSAC payload
void
WebRtcIsac_GetDownlinkBwJitIndexImpl(
    BwEstimatorstr*           bwest_str,
    int16_t*              bottleneckIndex,
    int16_t*              jitterInfo,
    enum IsacSamplingRate decoderSamplingFreq)
{
  float MaxDelay;
  //uint16_t MaxDelayBit;

  float rate;
  float r;
  float e1, e2;
  const float weight = 0.1f;
  const float* ptrQuantizationTable;
  int16_t addJitterInfo;
  int16_t minInd;
  int16_t maxInd;
  int16_t midInd;

  if (bwest_str->external_bw_info.in_use) {
    *bottleneckIndex = bwest_str->external_bw_info.bottleneck_idx;
    *jitterInfo = bwest_str->external_bw_info.jitter_info;
    return;
  }

  /* Get Max Delay Bit */
  /* get unquantized max delay */
  MaxDelay = (float)WebRtcIsac_GetDownlinkMaxDelay(bwest_str);

  if ( ((1.f - weight) * bwest_str->rec_max_delay_avg_Q + weight *
        MAX_ISAC_MD - MaxDelay) > (MaxDelay - (1.f-weight) *
                                   bwest_str->rec_max_delay_avg_Q - weight * MIN_ISAC_MD) )
  {
    jitterInfo[0] = 0;
    /* update quantized average */
    bwest_str->rec_max_delay_avg_Q =
        (1.f - weight) * bwest_str->rec_max_delay_avg_Q + weight *
        (float)MIN_ISAC_MD;
  }
  else
  {
    jitterInfo[0] = 1;
    /* update quantized average */
    bwest_str->rec_max_delay_avg_Q =
        (1.f-weight) * bwest_str->rec_max_delay_avg_Q + weight *
        (float)MAX_ISAC_MD;
  }

  // Get unquantized rate.
  rate = (float)WebRtcIsac_GetDownlinkBandwidth(bwest_str);

  /* Get Rate Index */
  if(decoderSamplingFreq == kIsacWideband)
  {
    ptrQuantizationTable = kQRateTableWb;
    addJitterInfo = 1;
    maxInd = 11;
  }
  else
  {
    ptrQuantizationTable = kQRateTableSwb;
    addJitterInfo = 0;
    maxInd = 23;
  }

  minInd = 0;
  while(maxInd > minInd + 1)
  {
    midInd = (maxInd + minInd) >> 1;
    if(rate > ptrQuantizationTable[midInd])
    {
      minInd = midInd;
    }
    else
    {
      maxInd = midInd;
    }
  }
  // Chose the index which gives results an average which is closest
  // to rate
  r = (1 - weight) * bwest_str->rec_bw_avg_Q - rate;
  e1 = weight * ptrQuantizationTable[minInd] + r;
  e2 = weight * ptrQuantizationTable[maxInd] + r;
  e1 = (e1 > 0)? e1:-e1;
  e2 = (e2 > 0)? e2:-e2;
  if(e1 < e2)
  {
    bottleneckIndex[0] = minInd;
  }
  else
  {
    bottleneckIndex[0] = maxInd;
  }

  bwest_str->rec_bw_avg_Q = (1 - weight) * bwest_str->rec_bw_avg_Q +
      weight * ptrQuantizationTable[bottleneckIndex[0]];
  bottleneckIndex[0] += jitterInfo[0] * 12 * addJitterInfo;

  bwest_str->rec_bw_avg = (1 - weight) * bwest_str->rec_bw_avg + weight *
      (rate + bwest_str->rec_header_rate);
}



/* get the bottle neck rate from far side to here, as estimated on this side */
int32_t WebRtcIsac_GetDownlinkBandwidth( const BwEstimatorstr *bwest_str)
{
  int32_t  rec_bw;
  float   jitter_sign;
  float   bw_adjust;

  RTC_DCHECK(!bwest_str->external_bw_info.in_use);

  /* create a value between -1.0 and 1.0 indicating "average sign" of jitter */
  jitter_sign = bwest_str->rec_jitter_short_term /
      bwest_str->rec_jitter_short_term_abs;

  /* adjust bw proportionally to negative average jitter sign */
  bw_adjust = 1.0f - jitter_sign * (0.15f + 0.15f * jitter_sign * jitter_sign);

  /* adjust Rate if jitter sign is mostly constant */
  rec_bw = (int32_t)(bwest_str->rec_bw * bw_adjust);

  /* limit range of bottle neck rate */
  if (rec_bw < MIN_ISAC_BW)
  {
    rec_bw = MIN_ISAC_BW;
  }
  else if (rec_bw > MAX_ISAC_BW)
  {
    rec_bw = MAX_ISAC_BW;
  }
  return rec_bw;
}

/* Returns the max delay (in ms) */
int32_t
WebRtcIsac_GetDownlinkMaxDelay(const BwEstimatorstr *bwest_str)
{
  int32_t rec_max_delay;

  RTC_DCHECK(!bwest_str->external_bw_info.in_use);

  rec_max_delay = (int32_t)(bwest_str->rec_max_delay);

  /* limit range of jitter estimate */
  if (rec_max_delay < MIN_ISAC_MD)
  {
    rec_max_delay = MIN_ISAC_MD;
  }
  else if (rec_max_delay > MAX_ISAC_MD)
  {
    rec_max_delay = MAX_ISAC_MD;
  }
  return rec_max_delay;
}

/* Clamp val to the closed interval [min,max]. */
static int32_t clamp(int32_t val, int32_t min, int32_t max) {
  RTC_DCHECK_LE(min, max);
  return val < min ? min : (val > max ? max : val);
}

int32_t WebRtcIsac_GetUplinkBandwidth(const BwEstimatorstr* bwest_str) {
  return bwest_str->external_bw_info.in_use
             ? bwest_str->external_bw_info.send_bw_avg
             : clamp(bwest_str->send_bw_avg, MIN_ISAC_BW, MAX_ISAC_BW);
}

int32_t WebRtcIsac_GetUplinkMaxDelay(const BwEstimatorstr* bwest_str) {
  return bwest_str->external_bw_info.in_use
             ? bwest_str->external_bw_info.send_max_delay_avg
             : clamp(bwest_str->send_max_delay_avg, MIN_ISAC_MD, MAX_ISAC_MD);
}

void WebRtcIsacBw_GetBandwidthInfo(BwEstimatorstr* bwest_str,
                                   enum IsacSamplingRate decoder_sample_rate_hz,
                                   IsacBandwidthInfo* bwinfo) {
  RTC_DCHECK(!bwest_str->external_bw_info.in_use);
  bwinfo->in_use = 1;
  bwinfo->send_bw_avg = WebRtcIsac_GetUplinkBandwidth(bwest_str);
  bwinfo->send_max_delay_avg = WebRtcIsac_GetUplinkMaxDelay(bwest_str);
  WebRtcIsac_GetDownlinkBwJitIndexImpl(bwest_str, &bwinfo->bottleneck_idx,
                                       &bwinfo->jitter_info,
                                       decoder_sample_rate_hz);
}

void WebRtcIsacBw_SetBandwidthInfo(BwEstimatorstr* bwest_str,
                                   const IsacBandwidthInfo* bwinfo) {
  memcpy(&bwest_str->external_bw_info, bwinfo,
         sizeof bwest_str->external_bw_info);
}

/*
 * update long-term average bitrate and amount of data in buffer
 * returns minimum payload size (bytes)
 */
int WebRtcIsac_GetMinBytes(
    RateModel*         State,
    int                StreamSize,    /* bytes in bitstream */
    const int          FrameSamples,  /* samples per frame */
    const double       BottleNeck,    /* bottle neck rate; excl headers (bps) */
    const double       DelayBuildUp,  /* max delay from bottleneck buffering (ms) */
    enum ISACBandwidth bandwidth
    /*,int16_t        frequentLargePackets*/)
{
  double MinRate = 0.0;
  int    MinBytes;
  double TransmissionTime;
  int    burstInterval = BURST_INTERVAL;

  // first 10 packets @ low rate, then INIT_BURST_LEN packets @
  // fixed rate of INIT_RATE bps
  if (State->InitCounter > 0)
  {
    if (State->InitCounter-- <= INIT_BURST_LEN)
    {
      if(bandwidth == isac8kHz)
      {
        MinRate = INIT_RATE_WB;
      }
      else
      {
        MinRate = INIT_RATE_SWB;
      }
    }
    else
    {
      MinRate = 0;
    }
  }
  else
  {
    /* handle burst */
    if (State->BurstCounter)
    {
      if (State->StillBuffered < (1.0 - 1.0/BURST_LEN) * DelayBuildUp)
      {
        /* max bps derived from BottleNeck and DelayBuildUp values */
        MinRate = (1.0 + (FS/1000) * DelayBuildUp /
                   (double)(BURST_LEN * FrameSamples)) * BottleNeck;
      }
      else
      {
        // max bps derived from StillBuffered and DelayBuildUp
        // values
        MinRate = (1.0 + (FS/1000) * (DelayBuildUp -
                                      State->StillBuffered) / (double)FrameSamples) * BottleNeck;
        if (MinRate < 1.04 * BottleNeck)
        {
          MinRate = 1.04 * BottleNeck;
        }
      }
      State->BurstCounter--;
    }
  }


  /* convert rate from bits/second to bytes/packet */
  MinBytes = (int) (MinRate * FrameSamples / (8.0 * FS));

  /* StreamSize will be adjusted if less than MinBytes */
  if (StreamSize < MinBytes)
  {
    StreamSize = MinBytes;
  }

  /* keep track of when bottle neck was last exceeded by at least 1% */
  if (StreamSize * 8.0 * FS / FrameSamples > 1.01 * BottleNeck) {
    if (State->PrevExceed) {
      /* bottle_neck exceded twice in a row, decrease ExceedAgo */
      State->ExceedAgo -= /*BURST_INTERVAL*/ burstInterval / (BURST_LEN - 1);
      if (State->ExceedAgo < 0)
        State->ExceedAgo = 0;
    }
    else
    {
      State->ExceedAgo += (FrameSamples * 1000) / FS; /* ms */
      State->PrevExceed = 1;
    }
  }
  else
  {
    State->PrevExceed = 0;
    State->ExceedAgo += (FrameSamples * 1000) / FS;     /* ms */
  }

  /* set burst flag if bottle neck not exceeded for long time */
  if ((State->ExceedAgo > burstInterval) &&
      (State->BurstCounter == 0))
  {
    if (State->PrevExceed)
    {
      State->BurstCounter = BURST_LEN - 1;
    }
    else
    {
      State->BurstCounter = BURST_LEN;
    }
  }


  /* Update buffer delay */
  TransmissionTime = StreamSize * 8.0 * 1000.0 / BottleNeck;  /* ms */
  State->StillBuffered += TransmissionTime;
  State->StillBuffered -= (FrameSamples * 1000) / FS;     /* ms */
  if (State->StillBuffered < 0.0)
  {
    State->StillBuffered = 0.0;
  }

  return MinBytes;
}


/*
 * update long-term average bitrate and amount of data in buffer
 */
void WebRtcIsac_UpdateRateModel(
    RateModel *State,
    int StreamSize,                    /* bytes in bitstream */
    const int FrameSamples,            /* samples per frame */
    const double BottleNeck)        /* bottle neck rate; excl headers (bps) */
{
  double TransmissionTime;

  /* avoid the initial "high-rate" burst */
  State->InitCounter = 0;

  /* Update buffer delay */
  TransmissionTime = StreamSize * 8.0 * 1000.0 / BottleNeck;  /* ms */
  State->StillBuffered += TransmissionTime;
  State->StillBuffered -= (FrameSamples * 1000) / FS;     /* ms */
  if (State->StillBuffered < 0.0)
    State->StillBuffered = 0.0;

}


void WebRtcIsac_InitRateModel(
    RateModel *State)
{
  State->PrevExceed      = 0;                        /* boolean */
  State->ExceedAgo       = 0;                        /* ms */
  State->BurstCounter    = 0;                        /* packets */
  State->InitCounter     = INIT_BURST_LEN + 10;    /* packets */
  State->StillBuffered   = 1.0;                    /* ms */
}

int WebRtcIsac_GetNewFrameLength(
    double bottle_neck,
    int    current_framesamples)
{
  int new_framesamples;

  const int Thld_20_30 = 20000;

  //const int Thld_30_20 = 30000;
  const int Thld_30_20 = 1000000;   // disable 20 ms frames

  const int Thld_30_60 = 18000;
  //const int Thld_30_60 = 0;      // disable 60 ms frames

  const int Thld_60_30 = 27000;


  new_framesamples = current_framesamples;

  /* find new framelength */
  switch(current_framesamples) {
    case 320:
      if (bottle_neck < Thld_20_30)
        new_framesamples = 480;
      break;
    case 480:
      if (bottle_neck < Thld_30_60)
        new_framesamples = 960;
      else if (bottle_neck > Thld_30_20)
        new_framesamples = 320;
      break;
    case 960:
      if (bottle_neck >= Thld_60_30)
        new_framesamples = 480;
      break;
  }

  return new_framesamples;
}

double WebRtcIsac_GetSnr(
    double bottle_neck,
    int    framesamples)
{
  double s2nr;

  const double a_20 = -30.0;
  const double b_20 = 0.8;
  const double c_20 = 0.0;

  const double a_30 = -23.0;
  const double b_30 = 0.48;
  const double c_30 = 0.0;

  const double a_60 = -23.0;
  const double b_60 = 0.53;
  const double c_60 = 0.0;


  /* find new SNR value */
  switch(framesamples) {
    case 320:
      s2nr = a_20 + b_20 * bottle_neck * 0.001 + c_20 * bottle_neck *
          bottle_neck * 0.000001;
      break;
    case 480:
      s2nr = a_30 + b_30 * bottle_neck * 0.001 + c_30 * bottle_neck *
          bottle_neck * 0.000001;
      break;
    case 960:
      s2nr = a_60 + b_60 * bottle_neck * 0.001 + c_60 * bottle_neck *
          bottle_neck * 0.000001;
      break;
    default:
      s2nr = 0;
  }

  return s2nr;

}

/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/isac/main/source/structs.h"
#include "modules/audio_coding/codecs/isac/main/source/bandwidth_estimator.h"
#include "modules/audio_coding/codecs/isac/main/source/entropy_coding.h"
#include "modules/audio_coding/codecs/isac/main/source/codec.h"


int
WebRtcIsac_EstimateBandwidth(
    BwEstimatorstr*           bwest_str,
    Bitstr*                   streamdata,
    size_t                packet_size,
    uint16_t              rtp_seq_number,
    uint32_t              send_ts,
    uint32_t              arr_ts,
    enum IsacSamplingRate encoderSampRate,
    enum IsacSamplingRate decoderSampRate)
{
  int16_t  index;
  int16_t  frame_samples;
  uint32_t sendTimestampIn16kHz;
  uint32_t arrivalTimestampIn16kHz;
  uint32_t diffSendTime;
  uint32_t diffArrivalTime;
  int err;

  /* decode framelength and BW estimation */
  err = WebRtcIsac_DecodeFrameLen(streamdata, &frame_samples);
  if(err < 0)  // error check
  {
    return err;
  }
  err = WebRtcIsac_DecodeSendBW(streamdata, &index);
  if(err < 0)  // error check
  {
    return err;
  }

  /* UPDATE ESTIMATES FROM OTHER SIDE */
  err = WebRtcIsac_UpdateUplinkBwImpl(bwest_str, index, encoderSampRate);
  if(err < 0)
  {
    return err;
  }

  // We like BWE to work at 16 kHz sampling rate,
  // therefore, we have to change the timestamps accordingly.
  // translate the send timestamp if required
  diffSendTime = (uint32_t)((uint32_t)send_ts -
                                  (uint32_t)bwest_str->senderTimestamp);
  bwest_str->senderTimestamp = send_ts;

  diffArrivalTime = (uint32_t)((uint32_t)arr_ts -
                                     (uint32_t)bwest_str->receiverTimestamp);
  bwest_str->receiverTimestamp = arr_ts;

  if(decoderSampRate == kIsacSuperWideband)
  {
    diffArrivalTime = (uint32_t)diffArrivalTime >> 1;
    diffSendTime = (uint32_t)diffSendTime >> 1;
  }

  // arrival timestamp in 16 kHz
  arrivalTimestampIn16kHz = (uint32_t)((uint32_t)
                                             bwest_str->prev_rec_arr_ts + (uint32_t)diffArrivalTime);
  // send timestamp in 16 kHz
  sendTimestampIn16kHz = (uint32_t)((uint32_t)
                                          bwest_str->prev_rec_send_ts + (uint32_t)diffSendTime);

  err = WebRtcIsac_UpdateBandwidthEstimator(bwest_str, rtp_seq_number,
                                            (frame_samples * 1000) / FS, sendTimestampIn16kHz,
                                            arrivalTimestampIn16kHz, packet_size);
  // error check
  if(err < 0)
  {
    return err;
  }

  return 0;
}

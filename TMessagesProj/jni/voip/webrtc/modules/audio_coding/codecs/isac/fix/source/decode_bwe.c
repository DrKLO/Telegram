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
 * decode_bwe.c
 *
 * This C file contains the internal decode bandwidth estimate function.
 *
 */


#include "modules/audio_coding/codecs/isac/fix/source/bandwidth_estimator.h"
#include "modules/audio_coding/codecs/isac/fix/source/codec.h"
#include "modules/audio_coding/codecs/isac/fix/source/entropy_coding.h"
#include "modules/audio_coding/codecs/isac/fix/source/structs.h"




int WebRtcIsacfix_EstimateBandwidth(BwEstimatorstr *bwest_str,
                                    Bitstr_dec  *streamdata,
                                    size_t packet_size,
                                    uint16_t rtp_seq_number,
                                    uint32_t send_ts,
                                    uint32_t arr_ts)
{
  int16_t index;
  size_t frame_samples;
  int err;

  /* decode framelength */
  err = WebRtcIsacfix_DecodeFrameLen(streamdata, &frame_samples);
  /* error check */
  if (err<0) {
    return err;
  }

  /* decode BW estimation */
  err = WebRtcIsacfix_DecodeSendBandwidth(streamdata, &index);
  /* error check */
  if (err<0) {
    return err;
  }

  /* Update BWE with received data */
  err = WebRtcIsacfix_UpdateUplinkBwImpl(
      bwest_str,
      rtp_seq_number,
      (int16_t)(frame_samples * 1000 / FS),
      send_ts,
      arr_ts,
      packet_size,  /* in bytes */
      index);

  /* error check */
  if (err<0) {
    return err;
  }

  /* Succesful */
  return 0;
}

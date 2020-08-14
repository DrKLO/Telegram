/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/acm2/call_statistics.h"

#include "rtc_base/checks.h"

namespace webrtc {

namespace acm2 {

void CallStatistics::DecodedByNetEq(AudioFrame::SpeechType speech_type,
                                    bool muted) {
  ++decoding_stat_.calls_to_neteq;
  if (muted) {
    ++decoding_stat_.decoded_muted_output;
  }
  switch (speech_type) {
    case AudioFrame::kNormalSpeech: {
      ++decoding_stat_.decoded_normal;
      break;
    }
    case AudioFrame::kPLC: {
      ++decoding_stat_.decoded_neteq_plc;
      break;
    }
    case AudioFrame::kCodecPLC: {
      ++decoding_stat_.decoded_codec_plc;
      break;
    }
    case AudioFrame::kCNG: {
      ++decoding_stat_.decoded_cng;
      break;
    }
    case AudioFrame::kPLCCNG: {
      ++decoding_stat_.decoded_plc_cng;
      break;
    }
    case AudioFrame::kUndefined: {
      // If the audio is decoded by NetEq, |kUndefined| is not an option.
      RTC_NOTREACHED();
    }
  }
}

void CallStatistics::DecodedBySilenceGenerator() {
  ++decoding_stat_.calls_to_silence_generator;
}

const AudioDecodingCallStats& CallStatistics::GetDecodingStatistics() const {
  return decoding_stat_;
}

}  // namespace acm2

}  // namespace webrtc

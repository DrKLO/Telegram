/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/opus/audio_encoder_multi_channel_opus.h"

#include <utility>

#include "modules/audio_coding/codecs/opus/audio_encoder_multi_channel_opus_impl.h"

namespace webrtc {

absl::optional<AudioEncoderMultiChannelOpusConfig>
AudioEncoderMultiChannelOpus::SdpToConfig(const SdpAudioFormat& format) {
  return AudioEncoderMultiChannelOpusImpl::SdpToConfig(format);
}

void AudioEncoderMultiChannelOpus::AppendSupportedEncoders(
    std::vector<AudioCodecSpec>* specs) {
  // To get full utilization of the surround support of the Opus lib, we can
  // mark which channel is the low frequency effects (LFE). But that is not done
  // ATM.
  {
    AudioCodecInfo surround_5_1_opus_info{48000, 6,
                                          /* default_bitrate_bps= */ 128000};
    surround_5_1_opus_info.allow_comfort_noise = false;
    surround_5_1_opus_info.supports_network_adaption = false;
    SdpAudioFormat opus_format({"multiopus",
                                48000,
                                6,
                                {{"minptime", "10"},
                                 {"useinbandfec", "1"},
                                 {"channel_mapping", "0,4,1,2,3,5"},
                                 {"num_streams", "4"},
                                 {"coupled_streams", "2"}}});
    specs->push_back({std::move(opus_format), surround_5_1_opus_info});
  }
  {
    AudioCodecInfo surround_7_1_opus_info{48000, 8,
                                          /* default_bitrate_bps= */ 200000};
    surround_7_1_opus_info.allow_comfort_noise = false;
    surround_7_1_opus_info.supports_network_adaption = false;
    SdpAudioFormat opus_format({"multiopus",
                                48000,
                                8,
                                {{"minptime", "10"},
                                 {"useinbandfec", "1"},
                                 {"channel_mapping", "0,6,1,2,3,4,5,7"},
                                 {"num_streams", "5"},
                                 {"coupled_streams", "3"}}});
    specs->push_back({std::move(opus_format), surround_7_1_opus_info});
  }
}

AudioCodecInfo AudioEncoderMultiChannelOpus::QueryAudioEncoder(
    const AudioEncoderMultiChannelOpusConfig& config) {
  return AudioEncoderMultiChannelOpusImpl::QueryAudioEncoder(config);
}

std::unique_ptr<AudioEncoder> AudioEncoderMultiChannelOpus::MakeAudioEncoder(
    const AudioEncoderMultiChannelOpusConfig& config,
    int payload_type,
    absl::optional<AudioCodecPairId> /*codec_pair_id*/) {
  return AudioEncoderMultiChannelOpusImpl::MakeAudioEncoder(config,
                                                            payload_type);
}

}  // namespace webrtc

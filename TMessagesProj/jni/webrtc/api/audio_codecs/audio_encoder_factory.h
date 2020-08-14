/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_AUDIO_CODECS_AUDIO_ENCODER_FACTORY_H_
#define API_AUDIO_CODECS_AUDIO_ENCODER_FACTORY_H_

#include <memory>
#include <vector>

#include "absl/types/optional.h"
#include "api/audio_codecs/audio_codec_pair_id.h"
#include "api/audio_codecs/audio_encoder.h"
#include "api/audio_codecs/audio_format.h"
#include "rtc_base/ref_count.h"

namespace webrtc {

// A factory that creates AudioEncoders.
class AudioEncoderFactory : public rtc::RefCountInterface {
 public:
  // Returns a prioritized list of audio codecs, to use for signaling etc.
  virtual std::vector<AudioCodecSpec> GetSupportedEncoders() = 0;

  // Returns information about how this format would be encoded, provided it's
  // supported. More format and format variations may be supported than those
  // returned by GetSupportedEncoders().
  virtual absl::optional<AudioCodecInfo> QueryAudioEncoder(
      const SdpAudioFormat& format) = 0;

  // Creates an AudioEncoder for the specified format. The encoder will tags its
  // payloads with the specified payload type. The `codec_pair_id` argument is
  // used to link encoders and decoders that talk to the same remote entity: if
  // a AudioEncoderFactory::MakeAudioEncoder() and a
  // AudioDecoderFactory::MakeAudioDecoder() call receive non-null IDs that
  // compare equal, the factory implementations may assume that the encoder and
  // decoder form a pair. (The intended use case for this is to set up
  // communication between the AudioEncoder and AudioDecoder instances, which is
  // needed for some codecs with built-in bandwidth adaptation.)
  //
  // Note: Implementations need to be robust against combinations other than
  // one encoder, one decoder getting the same ID; such encoders must still
  // work.
  //
  // TODO(ossu): Try to avoid audio encoders having to know their payload type.
  virtual std::unique_ptr<AudioEncoder> MakeAudioEncoder(
      int payload_type,
      const SdpAudioFormat& format,
      absl::optional<AudioCodecPairId> codec_pair_id) = 0;
};

}  // namespace webrtc

#endif  // API_AUDIO_CODECS_AUDIO_ENCODER_FACTORY_H_

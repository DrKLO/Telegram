/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_AUDIO_CODECS_AUDIO_DECODER_FACTORY_H_
#define API_AUDIO_CODECS_AUDIO_DECODER_FACTORY_H_

#include <memory>
#include <vector>

#include "absl/types/optional.h"
#include "api/audio_codecs/audio_codec_pair_id.h"
#include "api/audio_codecs/audio_decoder.h"
#include "api/audio_codecs/audio_format.h"
#include "api/ref_count.h"

namespace webrtc {

// A factory that creates AudioDecoders.
class AudioDecoderFactory : public RefCountInterface {
 public:
  virtual std::vector<AudioCodecSpec> GetSupportedDecoders() = 0;

  virtual bool IsSupportedDecoder(const SdpAudioFormat& format) = 0;

  // Create a new decoder instance. The `codec_pair_id` argument is used to link
  // encoders and decoders that talk to the same remote entity: if a
  // AudioEncoderFactory::MakeAudioEncoder() and a
  // AudioDecoderFactory::MakeAudioDecoder() call receive non-null IDs that
  // compare equal, the factory implementations may assume that the encoder and
  // decoder form a pair. (The intended use case for this is to set up
  // communication between the AudioEncoder and AudioDecoder instances, which is
  // needed for some codecs with built-in bandwidth adaptation.)
  //
  // Returns null if the format isn't supported.
  //
  // Note: Implementations need to be robust against combinations other than
  // one encoder, one decoder getting the same ID; such decoders must still
  // work.
  virtual std::unique_ptr<AudioDecoder> MakeAudioDecoder(
      const SdpAudioFormat& format,
      absl::optional<AudioCodecPairId> codec_pair_id) = 0;
};

}  // namespace webrtc

#endif  // API_AUDIO_CODECS_AUDIO_DECODER_FACTORY_H_

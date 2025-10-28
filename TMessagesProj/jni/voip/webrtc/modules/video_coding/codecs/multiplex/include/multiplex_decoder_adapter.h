/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_CODECS_MULTIPLEX_INCLUDE_MULTIPLEX_DECODER_ADAPTER_H_
#define MODULES_VIDEO_CODING_CODECS_MULTIPLEX_INCLUDE_MULTIPLEX_DECODER_ADAPTER_H_

#include <map>
#include <memory>
#include <vector>

#include "api/environment/environment.h"
#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_decoder.h"
#include "api/video_codecs/video_decoder_factory.h"
#include "modules/video_coding/codecs/multiplex/include/multiplex_encoder_adapter.h"

namespace webrtc {

class MultiplexDecoderAdapter : public VideoDecoder {
 public:
  // `factory` is not owned and expected to outlive this class.
  MultiplexDecoderAdapter(const Environment& env,
                          VideoDecoderFactory* factory,
                          const SdpVideoFormat& associated_format,
                          bool supports_augmenting_data = false);
  virtual ~MultiplexDecoderAdapter();

  // Implements VideoDecoder
  bool Configure(const Settings& settings) override;
  int32_t Decode(const EncodedImage& input_image,
                 int64_t render_time_ms) override;
  int32_t RegisterDecodeCompleteCallback(
      DecodedImageCallback* callback) override;
  int32_t Release() override;

  void Decoded(AlphaCodecStream stream_idx,
               VideoFrame* decoded_image,
               absl::optional<int32_t> decode_time_ms,
               absl::optional<uint8_t> qp);

 private:
  // Wrapper class that redirects Decoded() calls.
  class AdapterDecodedImageCallback;

  // Holds the decoded image output of a frame.
  struct DecodedImageData;

  // Holds the augmenting data of an image
  struct AugmentingData;

  void MergeAlphaImages(VideoFrame* decoded_image,
                        const absl::optional<int32_t>& decode_time_ms,
                        const absl::optional<uint8_t>& qp,
                        VideoFrame* multiplex_decoded_image,
                        const absl::optional<int32_t>& multiplex_decode_time_ms,
                        const absl::optional<uint8_t>& multiplex_qp,
                        std::unique_ptr<uint8_t[]> augmenting_data,
                        uint16_t augmenting_data_length);

  const Environment env_;
  VideoDecoderFactory* const factory_;
  const SdpVideoFormat associated_format_;
  std::vector<std::unique_ptr<VideoDecoder>> decoders_;
  std::vector<std::unique_ptr<AdapterDecodedImageCallback>> adapter_callbacks_;
  DecodedImageCallback* decoded_complete_callback_;

  // Holds YUV or AXX decode output of a frame that is identified by timestamp.
  std::map<uint32_t /* timestamp */, DecodedImageData> decoded_data_;
  std::map<uint32_t /* timestamp */, AugmentingData> decoded_augmenting_data_;
  const bool supports_augmenting_data_;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_CODECS_MULTIPLEX_INCLUDE_MULTIPLEX_DECODER_ADAPTER_H_

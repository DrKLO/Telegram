/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_CODECS_VP8_LIBVPX_VP8_DECODER_H_
#define MODULES_VIDEO_CODING_CODECS_VP8_LIBVPX_VP8_DECODER_H_

#include <memory>

#include "absl/types/optional.h"
#include "api/video/encoded_image.h"
#include "api/video_codecs/video_decoder.h"
#include "common_video/include/video_frame_buffer_pool.h"
#include "modules/video_coding/codecs/vp8/include/vp8.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "vpx/vp8dx.h"
#include "vpx/vpx_decoder.h"

namespace webrtc {

class LibvpxVp8Decoder : public VideoDecoder {
 public:
  LibvpxVp8Decoder();
  ~LibvpxVp8Decoder() override;

  int InitDecode(const VideoCodec* inst, int number_of_cores) override;

  int Decode(const EncodedImage& input_image,
             bool missing_frames,
             int64_t /*render_time_ms*/) override;

  int RegisterDecodeCompleteCallback(DecodedImageCallback* callback) override;
  int Release() override;

  const char* ImplementationName() const override;

  struct DeblockParams {
    int max_level = 6;   // Deblocking strength: [0, 16].
    int degrade_qp = 1;  // If QP value is below, start lowering |max_level|.
    int min_qp = 0;      // If QP value is below, turn off deblocking.
  };

 private:
  class QpSmoother;
  int ReturnFrame(const vpx_image_t* img,
                  uint32_t timeStamp,
                  int qp,
                  const webrtc::ColorSpace* explicit_color_space);
  const bool use_postproc_;

  VideoFrameBufferPool buffer_pool_;
  DecodedImageCallback* decode_complete_callback_;
  bool inited_;
  vpx_codec_ctx_t* decoder_;
  int propagation_cnt_;
  int last_frame_width_;
  int last_frame_height_;
  bool key_frame_required_;
  const absl::optional<DeblockParams> deblock_params_;
  const std::unique_ptr<QpSmoother> qp_smoother_;

  // Decoder should produce this format if possible.
  const VideoFrameBuffer::Type preferred_output_format_;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_CODECS_VP8_LIBVPX_VP8_DECODER_H_

/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 *
 */

#ifndef MODULES_VIDEO_CODING_CODECS_H264_H264_ENCODER_IMPL_H_
#define MODULES_VIDEO_CODING_CODECS_H264_H264_ENCODER_IMPL_H_

// Everything declared in this header is only required when WebRTC is
// build with H264 support, please do not move anything out of the
// #ifdef unless needed and tested.
#ifdef WEBRTC_USE_H264

#include <memory>
#include <vector>

#include "api/video/i420_buffer.h"
#include "api/video_codecs/video_encoder.h"
#include "common_video/h264/h264_bitstream_parser.h"
#include "modules/video_coding/codecs/h264/include/h264.h"
#include "modules/video_coding/utility/quality_scaler.h"
#include "third_party/openh264/src/codec/api/svc/codec_app_def.h"

class ISVCEncoder;

namespace webrtc {

class H264EncoderImpl : public H264Encoder {
 public:
  struct LayerConfig {
    int simulcast_idx = 0;
    int width = -1;
    int height = -1;
    bool sending = true;
    bool key_frame_request = false;
    float max_frame_rate = 0;
    uint32_t target_bps = 0;
    uint32_t max_bps = 0;
    bool frame_dropping_on = false;
    int key_frame_interval = 0;
    int num_temporal_layers = 1;

    void SetStreamState(bool send_stream);
  };

 public:
  explicit H264EncoderImpl(const cricket::VideoCodec& codec);
  ~H264EncoderImpl() override;

  // `settings.max_payload_size` is ignored.
  // The following members of `codec_settings` are used. The rest are ignored.
  // - codecType (must be kVideoCodecH264)
  // - targetBitrate
  // - maxFramerate
  // - width
  // - height
  int32_t InitEncode(const VideoCodec* codec_settings,
                     const VideoEncoder::Settings& settings) override;
  int32_t Release() override;

  int32_t RegisterEncodeCompleteCallback(
      EncodedImageCallback* callback) override;
  void SetRates(const RateControlParameters& parameters) override;

  // The result of encoding - an EncodedImage and CodecSpecificInfo - are
  // passed to the encode complete callback.
  int32_t Encode(const VideoFrame& frame,
                 const std::vector<VideoFrameType>* frame_types) override;

  EncoderInfo GetEncoderInfo() const override;

  // Exposed for testing.
  H264PacketizationMode PacketizationModeForTesting() const {
    return packetization_mode_;
  }

 private:
  SEncParamExt CreateEncoderParams(size_t i) const;

  webrtc::H264BitstreamParser h264_bitstream_parser_;
  // Reports statistics with histograms.
  void ReportInit();
  void ReportError();

  std::vector<ISVCEncoder*> encoders_;
  std::vector<SSourcePicture> pictures_;
  std::vector<rtc::scoped_refptr<I420Buffer>> downscaled_buffers_;
  std::vector<LayerConfig> configurations_;
  std::vector<EncodedImage> encoded_images_;

  VideoCodec codec_;
  H264PacketizationMode packetization_mode_;
  size_t max_payload_size_;
  int32_t number_of_cores_;
  EncodedImageCallback* encoded_image_callback_;

  bool has_reported_init_;
  bool has_reported_error_;

  std::vector<uint8_t> tl0sync_limit_;
};

}  // namespace webrtc

#endif  // WEBRTC_USE_H264

#endif  // MODULES_VIDEO_CODING_CODECS_H264_H264_ENCODER_IMPL_H_

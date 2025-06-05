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

#ifdef TGCALLS_ENABLE_x264

#ifndef WEBRTC_MODULES_VIDEO_CODING_CODECS_H264_H264_ENCODER_IMPL_H_
#define WEBRTC_MODULES_VIDEO_CODING_CODECS_H264_H264_ENCODER_IMPL_H_

#include "modules/video_coding/codecs/h264/include/h264.h"

#include <vector>

#include "api/scoped_refptr.h"

#include <libx264/x264.h>

class ISVCEncoder;

namespace webrtc {

class H264EncoderX264Impl : public H264Encoder {
 public:
  H264EncoderX264Impl(cricket::VideoCodec const &videoCodec);
  ~H264EncoderX264Impl() override;

  // |max_payload_size| is ignored.
  // The following members of |codec_settings| are used. The rest are ignored.;
  // - codecType (must be kVideoCodecH264)
  // - targetBitrate
  // - maxFramerate
  // - width
  // - height
  int32_t InitEncode(const VideoCodec* codec_settings,
                     int32_t number_of_cores,
                     size_t /*max_payload_size*/) override;
  int32_t Release() override;

  int32_t RegisterEncodeCompleteCallback(
      EncodedImageCallback* callback) override;
  void SetRates(const RateControlParameters& parameters) override;

  // The result of encoding - an EncodedImage and RTPFragmentationHeader - are
  // passed to the encode complete callback.
  int32_t Encode(const VideoFrame& frame,
                 const std::vector<VideoFrameType>* frame_types) override;

 private:
  bool IsInitialized() const;

//  ISVCEncoder* openh264_encoder_;
  VideoCodec codec_settings_;
    
  H264PacketizationMode packetization_mode_;

  EncodedImage encoded_image_;
  EncodedImageCallback* encoded_image_callback_;
    
  bool inited_;
  x264_picture_t pic_;
  x264_picture_t pic_out_;
  x264_t *encoder_;
  int i_frame = 0;//frame index
  x264_nal_t *nal_t_;
  int sps_id_ = 1;
//  x264_param_t x264_parameter;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_VIDEO_CODING_CODECS_H264_H264_ENCODER_IMPL_H_

#endif

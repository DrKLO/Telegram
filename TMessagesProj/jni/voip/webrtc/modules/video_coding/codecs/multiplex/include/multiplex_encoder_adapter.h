/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_CODECS_MULTIPLEX_INCLUDE_MULTIPLEX_ENCODER_ADAPTER_H_
#define MODULES_VIDEO_CODING_CODECS_MULTIPLEX_INCLUDE_MULTIPLEX_ENCODER_ADAPTER_H_

#include <map>
#include <memory>
#include <vector>

#include "api/fec_controller_override.h"
#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_encoder.h"
#include "api/video_codecs/video_encoder_factory.h"
#include "modules/video_coding/codecs/multiplex/multiplex_encoded_image_packer.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "rtc_base/synchronization/mutex.h"

namespace webrtc {

enum AlphaCodecStream {
  kYUVStream = 0,
  kAXXStream = 1,
  kAlphaCodecStreams = 2,
};

class MultiplexEncoderAdapter : public VideoEncoder {
 public:
  // `factory` is not owned and expected to outlive this class.
  MultiplexEncoderAdapter(VideoEncoderFactory* factory,
                          const SdpVideoFormat& associated_format,
                          bool supports_augmenting_data = false);
  virtual ~MultiplexEncoderAdapter();

  // Implements VideoEncoder
  void SetFecControllerOverride(
      FecControllerOverride* fec_controller_override) override;
  int InitEncode(const VideoCodec* inst,
                 const VideoEncoder::Settings& settings) override;
  int Encode(const VideoFrame& input_image,
             const std::vector<VideoFrameType>* frame_types) override;
  int RegisterEncodeCompleteCallback(EncodedImageCallback* callback) override;
  void SetRates(const RateControlParameters& parameters) override;
  void OnPacketLossRateUpdate(float packet_loss_rate) override;
  void OnRttUpdate(int64_t rtt_ms) override;
  void OnLossNotification(const LossNotification& loss_notification) override;
  int Release() override;
  EncoderInfo GetEncoderInfo() const override;

  EncodedImageCallback::Result OnEncodedImage(
      AlphaCodecStream stream_idx,
      const EncodedImage& encodedImage,
      const CodecSpecificInfo* codecSpecificInfo);

 private:
  // Wrapper class that redirects OnEncodedImage() calls.
  class AdapterEncodedImageCallback;

  VideoEncoderFactory* const factory_;
  const SdpVideoFormat associated_format_;
  std::vector<std::unique_ptr<VideoEncoder>> encoders_;
  std::vector<std::unique_ptr<AdapterEncodedImageCallback>> adapter_callbacks_;
  EncodedImageCallback* encoded_complete_callback_;

  std::map<uint32_t /* timestamp */, MultiplexImage> stashed_images_
      RTC_GUARDED_BY(mutex_);

  uint16_t picture_index_ = 0;
  std::vector<uint8_t> multiplex_dummy_planes_;

  int key_frame_interval_;
  EncodedImage combined_image_;

  Mutex mutex_;

  const bool supports_augmented_data_;
  int augmenting_data_size_ = 0;

  EncoderInfo encoder_info_;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_CODECS_MULTIPLEX_INCLUDE_MULTIPLEX_ENCODER_ADAPTER_H_

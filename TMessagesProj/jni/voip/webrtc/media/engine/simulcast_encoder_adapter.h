/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 *
 */

#ifndef MEDIA_ENGINE_SIMULCAST_ENCODER_ADAPTER_H_
#define MEDIA_ENGINE_SIMULCAST_ENCODER_ADAPTER_H_

#include <memory>
#include <stack>
#include <string>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/fec_controller_override.h"
#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_encoder.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/utility/framerate_controller.h"
#include "rtc_base/atomic_ops.h"
#include "rtc_base/synchronization/sequence_checker.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

class SimulcastRateAllocator;
class VideoEncoderFactory;

// SimulcastEncoderAdapter implements simulcast support by creating multiple
// webrtc::VideoEncoder instances with the given VideoEncoderFactory.
// The object is created and destroyed on the worker thread, but all public
// interfaces should be called from the encoder task queue.
class RTC_EXPORT SimulcastEncoderAdapter : public VideoEncoder {
 public:
  // TODO(bugs.webrtc.org/11000): Remove when downstream usage is gone.
  SimulcastEncoderAdapter(VideoEncoderFactory* primarty_factory,
                          const SdpVideoFormat& format);
  // |primary_factory| produces the first-choice encoders to use.
  // |fallback_factory|, if non-null, is used to create fallback encoder that
  // will be used if InitEncode() fails for the primary encoder.
  SimulcastEncoderAdapter(VideoEncoderFactory* primary_factory,
                          VideoEncoderFactory* fallback_factory,
                          const SdpVideoFormat& format);
  ~SimulcastEncoderAdapter() override;

  // Implements VideoEncoder.
  void SetFecControllerOverride(
      FecControllerOverride* fec_controller_override) override;
  int Release() override;
  int InitEncode(const VideoCodec* codec_settings,
                 const VideoEncoder::Settings& settings) override;
  int Encode(const VideoFrame& input_image,
             const std::vector<VideoFrameType>* frame_types) override;
  int RegisterEncodeCompleteCallback(EncodedImageCallback* callback) override;
  void SetRates(const RateControlParameters& parameters) override;
  void OnPacketLossRateUpdate(float packet_loss_rate) override;
  void OnRttUpdate(int64_t rtt_ms) override;
  void OnLossNotification(const LossNotification& loss_notification) override;

  // Eventual handler for the contained encoders' EncodedImageCallbacks, but
  // called from an internal helper that also knows the correct stream
  // index.
  EncodedImageCallback::Result OnEncodedImage(
      size_t stream_idx,
      const EncodedImage& encoded_image,
      const CodecSpecificInfo* codec_specific_info);

  EncoderInfo GetEncoderInfo() const override;

 private:
  struct StreamInfo {
    StreamInfo(std::unique_ptr<VideoEncoder> encoder,
               std::unique_ptr<EncodedImageCallback> callback,
               std::unique_ptr<FramerateController> framerate_controller,
               uint16_t width,
               uint16_t height,
               bool send_stream)
        : encoder(std::move(encoder)),
          callback(std::move(callback)),
          framerate_controller(std::move(framerate_controller)),
          width(width),
          height(height),
          key_frame_request(false),
          send_stream(send_stream) {}
    std::unique_ptr<VideoEncoder> encoder;
    std::unique_ptr<EncodedImageCallback> callback;
    std::unique_ptr<FramerateController> framerate_controller;
    uint16_t width;
    uint16_t height;
    bool key_frame_request;
    bool send_stream;
  };

  enum class StreamResolution {
    OTHER,
    HIGHEST,
    LOWEST,
  };

  // Populate the codec settings for each simulcast stream.
  void PopulateStreamCodec(const webrtc::VideoCodec& inst,
                           int stream_index,
                           uint32_t start_bitrate_kbps,
                           StreamResolution stream_resolution,
                           webrtc::VideoCodec* stream_codec);

  bool Initialized() const;

  void DestroyStoredEncoders();

  volatile int inited_;  // Accessed atomically.
  VideoEncoderFactory* const primary_encoder_factory_;
  VideoEncoderFactory* const fallback_encoder_factory_;
  const SdpVideoFormat video_format_;
  VideoCodec codec_;
  std::vector<StreamInfo> streaminfos_;
  EncodedImageCallback* encoded_complete_callback_;

  // Used for checking the single-threaded access of the encoder interface.
  SequenceChecker encoder_queue_;

  // Store encoders in between calls to Release and InitEncode, so they don't
  // have to be recreated. Remaining encoders are destroyed by the destructor.
  std::stack<std::unique_ptr<VideoEncoder>> stored_encoders_;

  const absl::optional<unsigned int> experimental_boosted_screenshare_qp_;
  const bool boost_base_layer_quality_;
  const bool prefer_temporal_support_on_base_layer_;
};

}  // namespace webrtc

#endif  // MEDIA_ENGINE_SIMULCAST_ENCODER_ADAPTER_H_

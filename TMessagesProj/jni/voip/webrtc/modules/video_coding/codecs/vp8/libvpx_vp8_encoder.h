/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_CODECS_VP8_LIBVPX_VP8_ENCODER_H_
#define MODULES_VIDEO_CODING_CODECS_VP8_LIBVPX_VP8_ENCODER_H_

#include <memory>
#include <string>
#include <vector>

#include "api/fec_controller_override.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "api/video/encoded_image.h"
#include "api/video/video_frame.h"
#include "api/video_codecs/video_encoder.h"
#include "api/video_codecs/vp8_frame_buffer_controller.h"
#include "api/video_codecs/vp8_frame_config.h"
#include "modules/video_coding/codecs/interface/libvpx_interface.h"
#include "modules/video_coding/codecs/vp8/include/vp8.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/utility/framerate_controller_deprecated.h"
#include "modules/video_coding/utility/vp8_constants.h"
#include "rtc_base/experiments/cpu_speed_experiment.h"
#include "rtc_base/experiments/encoder_info_settings.h"
#include "rtc_base/experiments/rate_control_settings.h"
#include <libvpx/vp8cx.h>
#include <libvpx/vpx_encoder.h>

namespace webrtc {

class LibvpxVp8Encoder : public VideoEncoder {
 public:
  LibvpxVp8Encoder(std::unique_ptr<LibvpxInterface> interface,
                   VP8Encoder::Settings settings);
  ~LibvpxVp8Encoder() override;

  int Release() override;

  void SetFecControllerOverride(
      FecControllerOverride* fec_controller_override) override;

  int InitEncode(const VideoCodec* codec_settings,
                 const VideoEncoder::Settings& settings) override;

  int Encode(const VideoFrame& input_image,
             const std::vector<VideoFrameType>* frame_types) override;

  int RegisterEncodeCompleteCallback(EncodedImageCallback* callback) override;

  void SetRates(const RateControlParameters& parameters) override;

  void OnPacketLossRateUpdate(float packet_loss_rate) override;

  void OnRttUpdate(int64_t rtt_ms) override;

  void OnLossNotification(const LossNotification& loss_notification) override;

  EncoderInfo GetEncoderInfo() const override;

  static vpx_enc_frame_flags_t EncodeFlags(const Vp8FrameConfig& references);

 private:
  // Get the cpu_speed setting for encoder based on resolution and/or platform.
  int GetCpuSpeed(int width, int height);

  // Determine number of encoder threads to use.
  int NumberOfThreads(int width, int height, int number_of_cores);

  // Call encoder initialize function and set control settings.
  int InitAndSetControlSettings();

  void PopulateCodecSpecific(CodecSpecificInfo* codec_specific,
                             const vpx_codec_cx_pkt& pkt,
                             int stream_idx,
                             int encoder_idx,
                             uint32_t timestamp);

  int GetEncodedPartitions(const VideoFrame& input_image,
                           bool retransmission_allowed);

  // Set the stream state for stream `stream_idx`.
  void SetStreamState(bool send_stream, int stream_idx);

  uint32_t MaxIntraTarget(uint32_t optimal_buffer_size);

  uint32_t FrameDropThreshold(size_t spatial_idx) const;

  size_t SteadyStateSize(int sid, int tid);

  bool UpdateVpxConfiguration(size_t stream_index);

  void MaybeUpdatePixelFormat(vpx_img_fmt fmt);
  // Prepares `raw_image_` to reference image data of `buffer`, or of mapped or
  // scaled versions of `buffer`. Returns a list of buffers that got referenced
  // as a result, allowing the caller to keep references to them until after
  // encoding has finished. On failure to convert the buffer, an empty list is
  // returned.
  std::vector<rtc::scoped_refptr<VideoFrameBuffer>> PrepareBuffers(
      rtc::scoped_refptr<VideoFrameBuffer> buffer);

  const std::unique_ptr<LibvpxInterface> libvpx_;

  const CpuSpeedExperiment experimental_cpu_speed_config_arm_;
  const RateControlSettings rate_control_settings_;

  EncodedImageCallback* encoded_complete_callback_ = nullptr;
  VideoCodec codec_;
  bool inited_ = false;
  int64_t timestamp_ = 0;
  int qp_max_ = 56;
  int cpu_speed_default_ = -6;
  int number_of_cores_ = 0;
  uint32_t rc_max_intra_target_ = 0;
  int num_active_streams_ = 0;
  const std::unique_ptr<Vp8FrameBufferControllerFactory>
      frame_buffer_controller_factory_;
  std::unique_ptr<Vp8FrameBufferController> frame_buffer_controller_;
  const std::vector<VideoEncoder::ResolutionBitrateLimits>
      resolution_bitrate_limits_;
  std::vector<bool> key_frame_request_;
  std::vector<bool> send_stream_;
  std::vector<int> cpu_speed_;
  std::vector<vpx_image_t> raw_images_;
  std::vector<EncodedImage> encoded_images_;
  std::vector<vpx_codec_ctx_t> encoders_;
  std::vector<vpx_codec_enc_cfg_t> vpx_configs_;
  std::vector<Vp8EncoderConfig> config_overrides_;
  std::vector<vpx_rational_t> downsampling_factors_;
  std::vector<Timestamp> last_encoder_output_time_;

  // Variable frame-rate screencast related fields and methods.
  const struct VariableFramerateExperiment {
    bool enabled = false;
    // Framerate is limited to this value in steady state.
    float framerate_limit = 5.0;
    // This qp or below is considered a steady state.
    int steady_state_qp = kVp8SteadyStateQpThreshold;
    // Frames of at least this percentage below ideal for configured bitrate are
    // considered in a steady state.
    int steady_state_undershoot_percentage = 30;
  } variable_framerate_experiment_;
  static VariableFramerateExperiment ParseVariableFramerateConfig(
      std::string group_name);
  FramerateControllerDeprecated framerate_controller_;
  int num_steady_state_frames_ = 0;

  FecControllerOverride* fec_controller_override_ = nullptr;

  const LibvpxVp8EncoderInfoSettings encoder_info_override_;

  absl::optional<TimeDelta> max_frame_drop_interval_;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_CODECS_VP8_LIBVPX_VP8_ENCODER_H_

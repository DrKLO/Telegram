/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 *
 */

#ifndef MODULES_VIDEO_CODING_CODECS_VP9_LIBVPX_VP9_ENCODER_H_
#define MODULES_VIDEO_CODING_CODECS_VP9_LIBVPX_VP9_ENCODER_H_

#ifdef RTC_ENABLE_VP9

#include <map>
#include <memory>
#include <vector>

#include "api/fec_controller_override.h"
#include "api/transport/webrtc_key_value_config.h"
#include "api/video_codecs/video_encoder.h"
#include "api/video_codecs/vp9_profile.h"
#include "common_video/include/video_frame_buffer_pool.h"
#include "modules/video_coding/codecs/interface/libvpx_interface.h"
#include "modules/video_coding/codecs/vp9/include/vp9.h"
#include "modules/video_coding/codecs/vp9/vp9_frame_buffer_pool.h"
#include "modules/video_coding/svc/scalable_video_controller.h"
#include "modules/video_coding/utility/framerate_controller.h"
#include "rtc_base/experiments/encoder_info_settings.h"
#include <libvpx/vp8cx.h>

namespace webrtc {

class LibvpxVp9Encoder : public VP9Encoder {
 public:
  LibvpxVp9Encoder(const cricket::VideoCodec& codec,
                   std::unique_ptr<LibvpxInterface> interface,
                   const WebRtcKeyValueConfig& trials);

  ~LibvpxVp9Encoder() override;

  void SetFecControllerOverride(
      FecControllerOverride* fec_controller_override) override;

  int Release() override;

  int InitEncode(const VideoCodec* codec_settings,
                 const Settings& settings) override;

  int Encode(const VideoFrame& input_image,
             const std::vector<VideoFrameType>* frame_types) override;

  int RegisterEncodeCompleteCallback(EncodedImageCallback* callback) override;

  void SetRates(const RateControlParameters& parameters) override;

  EncoderInfo GetEncoderInfo() const override;

 private:
  // Determine number of encoder threads to use.
  int NumberOfThreads(int width, int height, int number_of_cores);

  // Call encoder initialize function and set control settings.
  int InitAndSetControlSettings(const VideoCodec* inst);

  bool PopulateCodecSpecific(CodecSpecificInfo* codec_specific,
                             absl::optional<int>* spatial_idx,
                             const vpx_codec_cx_pkt& pkt,
                             uint32_t timestamp);
  void FillReferenceIndices(const vpx_codec_cx_pkt& pkt,
                            const size_t pic_num,
                            const bool inter_layer_predicted,
                            CodecSpecificInfoVP9* vp9_info);
  void UpdateReferenceBuffers(const vpx_codec_cx_pkt& pkt,
                              const size_t pic_num);
  vpx_svc_ref_frame_config_t SetReferences(
      bool is_key_pic,
      size_t first_active_spatial_layer_id);

  bool ExplicitlyConfiguredSpatialLayers() const;
  bool SetSvcRates(const VideoBitrateAllocation& bitrate_allocation);

  void GetEncodedLayerFrame(const vpx_codec_cx_pkt* pkt);

  // Callback function for outputting packets per spatial layer.
  static void EncoderOutputCodedPacketCallback(vpx_codec_cx_pkt* pkt,
                                               void* user_data);

  void DeliverBufferedFrame(bool end_of_picture);

  bool DropFrame(uint8_t spatial_idx, uint32_t rtp_timestamp);

  // Determine maximum target for Intra frames
  //
  // Input:
  //    - optimal_buffer_size : Optimal buffer size
  // Return Value             : Max target size for Intra frames represented as
  //                            percentage of the per frame bandwidth
  uint32_t MaxIntraTarget(uint32_t optimal_buffer_size);

  size_t SteadyStateSize(int sid, int tid);

  void MaybeRewrapRawWithFormat(const vpx_img_fmt fmt);
  // Prepares |raw_| to reference image data of |buffer|, or of mapped or scaled
  // versions of |buffer|. Returns the buffer that got referenced as a result,
  // allowing the caller to keep a reference to it until after encoding has
  // finished. On failure to convert the buffer, null is returned.
  rtc::scoped_refptr<VideoFrameBuffer> PrepareBufferForProfile0(
      rtc::scoped_refptr<VideoFrameBuffer> buffer);

  const std::unique_ptr<LibvpxInterface> libvpx_;
  EncodedImage encoded_image_;
  CodecSpecificInfo codec_specific_;
  EncodedImageCallback* encoded_complete_callback_;
  VideoCodec codec_;
  const VP9Profile profile_;
  bool inited_;
  int64_t timestamp_;
  uint32_t rc_max_intra_target_;
  vpx_codec_ctx_t* encoder_;
  vpx_codec_enc_cfg_t* config_;
  vpx_image_t* raw_;
  vpx_svc_extra_cfg_t svc_params_;
  const VideoFrame* input_image_;
  GofInfoVP9 gof_;  // Contains each frame's temporal information for
                    // non-flexible mode.
  bool force_key_frame_;
  size_t pics_since_key_;
  uint8_t num_temporal_layers_;
  uint8_t num_spatial_layers_;         // Number of configured SLs
  uint8_t num_active_spatial_layers_;  // Number of actively encoded SLs
  uint8_t first_active_layer_;
  bool layer_deactivation_requires_key_frame_;
  bool is_svc_;
  InterLayerPredMode inter_layer_pred_;
  bool external_ref_control_;
  const bool trusted_rate_controller_;
  bool layer_buffering_;
  const bool full_superframe_drop_;
  vpx_svc_frame_drop_t svc_drop_frame_;
  bool first_frame_in_picture_;
  VideoBitrateAllocation current_bitrate_allocation_;
  bool ss_info_needed_;
  bool force_all_active_layers_;
  const bool use_svc_controller_;

  std::unique_ptr<ScalableVideoController> svc_controller_;
  std::vector<FramerateController> framerate_controller_;

  // Used for flexible mode.
  bool is_flexible_mode_;
  struct RefFrameBuffer {
    RefFrameBuffer(size_t pic_num,
                   size_t spatial_layer_id,
                   size_t temporal_layer_id)
        : pic_num(pic_num),
          spatial_layer_id(spatial_layer_id),
          temporal_layer_id(temporal_layer_id) {}
    RefFrameBuffer() {}

    bool operator==(const RefFrameBuffer& o) {
      return pic_num == o.pic_num && spatial_layer_id == o.spatial_layer_id &&
             temporal_layer_id == o.temporal_layer_id;
    }

    size_t pic_num = 0;
    size_t spatial_layer_id = 0;
    size_t temporal_layer_id = 0;
  };
  std::map<size_t, RefFrameBuffer> ref_buf_;
  std::vector<ScalableVideoController::LayerFrameConfig> layer_frames_;

  // Variable frame-rate related fields and methods.
  const struct VariableFramerateExperiment {
    bool enabled;
    // Framerate is limited to this value in steady state.
    float framerate_limit;
    // This qp or below is considered a steady state.
    int steady_state_qp;
    // Frames of at least this percentage below ideal for configured bitrate are
    // considered in a steady state.
    int steady_state_undershoot_percentage;
    // Number of consecutive frames with good QP and size required to detect
    // the steady state.
    int frames_before_steady_state;
  } variable_framerate_experiment_;
  static VariableFramerateExperiment ParseVariableFramerateConfig(
      const WebRtcKeyValueConfig& trials);
  FramerateController variable_framerate_controller_;

  const struct QualityScalerExperiment {
    int low_qp;
    int high_qp;
    bool enabled;
  } quality_scaler_experiment_;
  static QualityScalerExperiment ParseQualityScalerConfig(
      const WebRtcKeyValueConfig& trials);
  const bool external_ref_ctrl_;

  // Flags that can affect speed vs quality tradeoff, and are configureable per
  // resolution ranges.
  struct PerformanceFlags {
    // If false, a lookup will be made in |settings_by_resolution| base on the
    // highest currently active resolution, and the overall speed then set to
    // to the |base_layer_speed| matching that entry.
    // If true, each active resolution will have it's speed and deblock_mode set
    // based on it resolution, and the high layer speed configured for non
    // base temporal layer frames.
    bool use_per_layer_speed = false;

    struct ParameterSet {
      int base_layer_speed = -1;  // Speed setting for TL0.
      int high_layer_speed = -1;  // Speed setting for TL1-TL3.
      //  0 = deblock all temporal layers (TL)
      //  1 = disable deblock for top-most TL
      //  2 = disable deblock for all TLs
      int deblock_mode = 0;
    };
    // Map from min pixel count to settings for that resolution and above.
    // E.g. if you want some settings A if below wvga (640x360) and some other
    // setting B at wvga and above, you'd use map {{0, A}, {230400, B}}.
    std::map<int, ParameterSet> settings_by_resolution;
  };
  // Performance flags, ordered by |min_pixel_count|.
  const PerformanceFlags performance_flags_;
  // Caching of of |speed_configs_|, where index i maps to the resolution as
  // specified in |codec_.spatialLayer[i]|.
  std::vector<PerformanceFlags::ParameterSet>
      performance_flags_by_spatial_index_;
  void UpdatePerformanceFlags();
  static PerformanceFlags ParsePerformanceFlagsFromTrials(
      const WebRtcKeyValueConfig& trials);
  static PerformanceFlags GetDefaultPerformanceFlags();

  int num_steady_state_frames_;
  // Only set config when this flag is set.
  bool config_changed_;

  const LibvpxVp9EncoderInfoSettings encoder_info_override_;
};

}  // namespace webrtc

#endif  // RTC_ENABLE_VP9

#endif  // MODULES_VIDEO_CODING_CODECS_VP9_LIBVPX_VP9_ENCODER_H_

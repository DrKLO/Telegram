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

#ifdef RTC_ENABLE_VP9

#include "modules/video_coding/codecs/vp9/vp9_impl.h"

#include <algorithm>
#include <limits>
#include <utility>
#include <vector>

#include "absl/memory/memory.h"
#include "absl/strings/match.h"
#include "api/transport/field_trial_based_config.h"
#include "api/video/color_space.h"
#include "api/video/i010_buffer.h"
#include "common_video/include/video_frame_buffer.h"
#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/video_coding/codecs/vp9/svc_rate_allocator.h"
#include "modules/video_coding/svc/create_scalability_structure.h"
#include "modules/video_coding/svc/scalable_video_controller.h"
#include "modules/video_coding/svc/scalable_video_controller_no_layering.h"
#include "modules/video_coding/utility/vp9_uncompressed_header_parser.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/experiments/rate_control_settings.h"
#include "rtc_base/keep_ref_until_done.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"
#include "third_party/libyuv/include/libyuv/convert.h"
#include "vpx/vp8cx.h"
#include "vpx/vp8dx.h"
#include "vpx/vpx_decoder.h"
#include "vpx/vpx_encoder.h"

namespace webrtc {

namespace {
// Maps from gof_idx to encoder internal reference frame buffer index. These
// maps work for 1,2 and 3 temporal layers with GOF length of 1,2 and 4 frames.
uint8_t kRefBufIdx[4] = {0, 0, 0, 1};
uint8_t kUpdBufIdx[4] = {0, 0, 1, 0};

// Maximum allowed PID difference for differnet per-layer frame-rate case.
const int kMaxAllowedPidDiff = 30;

// TODO(ilink): Tune these thresholds further.
// Selected using ConverenceMotion_1280_720_50.yuv clip.
// No toggling observed on any link capacity from 100-2000kbps.
// HD was reached consistently when link capacity was 1500kbps.
// Set resolutions are a bit more conservative than svc_config.cc sets, e.g.
// for 300kbps resolution converged to 270p instead of 360p.
constexpr int kLowVp9QpThreshold = 149;
constexpr int kHighVp9QpThreshold = 205;

// Only positive speeds, range for real-time coding currently is: 5 - 8.
// Lower means slower/better quality, higher means fastest/lower quality.
int GetCpuSpeed(int width, int height) {
#if defined(WEBRTC_ARCH_ARM) || defined(WEBRTC_ARCH_ARM64) || defined(ANDROID)
  return 8;
#else
  // For smaller resolutions, use lower speed setting (get some coding gain at
  // the cost of increased encoding complexity).
  if (width * height <= 352 * 288)
    return 5;
  else
    return 7;
#endif
}
// Helper class for extracting VP9 colorspace.
ColorSpace ExtractVP9ColorSpace(vpx_color_space_t space_t,
                                vpx_color_range_t range_t,
                                unsigned int bit_depth) {
  ColorSpace::PrimaryID primaries = ColorSpace::PrimaryID::kUnspecified;
  ColorSpace::TransferID transfer = ColorSpace::TransferID::kUnspecified;
  ColorSpace::MatrixID matrix = ColorSpace::MatrixID::kUnspecified;
  switch (space_t) {
    case VPX_CS_BT_601:
    case VPX_CS_SMPTE_170:
      primaries = ColorSpace::PrimaryID::kSMPTE170M;
      transfer = ColorSpace::TransferID::kSMPTE170M;
      matrix = ColorSpace::MatrixID::kSMPTE170M;
      break;
    case VPX_CS_SMPTE_240:
      primaries = ColorSpace::PrimaryID::kSMPTE240M;
      transfer = ColorSpace::TransferID::kSMPTE240M;
      matrix = ColorSpace::MatrixID::kSMPTE240M;
      break;
    case VPX_CS_BT_709:
      primaries = ColorSpace::PrimaryID::kBT709;
      transfer = ColorSpace::TransferID::kBT709;
      matrix = ColorSpace::MatrixID::kBT709;
      break;
    case VPX_CS_BT_2020:
      primaries = ColorSpace::PrimaryID::kBT2020;
      switch (bit_depth) {
        case 8:
          transfer = ColorSpace::TransferID::kBT709;
          break;
        case 10:
          transfer = ColorSpace::TransferID::kBT2020_10;
          break;
        default:
          RTC_NOTREACHED();
          break;
      }
      matrix = ColorSpace::MatrixID::kBT2020_NCL;
      break;
    case VPX_CS_SRGB:
      primaries = ColorSpace::PrimaryID::kBT709;
      transfer = ColorSpace::TransferID::kIEC61966_2_1;
      matrix = ColorSpace::MatrixID::kBT709;
      break;
    default:
      break;
  }

  ColorSpace::RangeID range = ColorSpace::RangeID::kInvalid;
  switch (range_t) {
    case VPX_CR_STUDIO_RANGE:
      range = ColorSpace::RangeID::kLimited;
      break;
    case VPX_CR_FULL_RANGE:
      range = ColorSpace::RangeID::kFull;
      break;
    default:
      break;
  }
  return ColorSpace(primaries, transfer, matrix, range);
}

std::pair<size_t, size_t> GetActiveLayers(
    const VideoBitrateAllocation& allocation) {
  for (size_t sl_idx = 0; sl_idx < kMaxSpatialLayers; ++sl_idx) {
    if (allocation.GetSpatialLayerSum(sl_idx) > 0) {
      size_t last_layer = sl_idx + 1;
      while (last_layer < kMaxSpatialLayers &&
             allocation.GetSpatialLayerSum(last_layer) > 0) {
        ++last_layer;
      }
      return std::make_pair(sl_idx, last_layer);
    }
  }
  return {0, 0};
}

std::unique_ptr<ScalableVideoController> CreateVp9ScalabilityStructure(
    const VideoCodec& codec) {
  int num_spatial_layers = codec.VP9().numberOfSpatialLayers;
  int num_temporal_layers =
      std::max(1, int{codec.VP9().numberOfTemporalLayers});
  if (num_spatial_layers == 1 && num_temporal_layers == 1) {
    return std::make_unique<ScalableVideoControllerNoLayering>();
  }

  char name[20];
  rtc::SimpleStringBuilder ss(name);
  if (codec.mode == VideoCodecMode::kScreensharing) {
    // TODO(bugs.webrtc.org/11999): Compose names of the structures when they
    // are implemented.
    return nullptr;
  } else if (codec.VP9().interLayerPred == InterLayerPredMode::kOn ||
             num_spatial_layers == 1) {
    ss << "L" << num_spatial_layers << "T" << num_temporal_layers;
  } else if (codec.VP9().interLayerPred == InterLayerPredMode::kOnKeyPic) {
    ss << "L" << num_spatial_layers << "T" << num_temporal_layers << "_KEY";
  } else {
    RTC_DCHECK_EQ(codec.VP9().interLayerPred, InterLayerPredMode::kOff);
    ss << "S" << num_spatial_layers << "T" << num_temporal_layers;
  }

  // Check spatial ratio.
  if (num_spatial_layers > 1 && codec.spatialLayers[0].targetBitrate > 0) {
    if (codec.width != codec.spatialLayers[num_spatial_layers - 1].width ||
        codec.height != codec.spatialLayers[num_spatial_layers - 1].height) {
      RTC_LOG(LS_WARNING)
          << "Top layer resolution expected to match overall resolution";
      return nullptr;
    }
    // Check if the ratio is one of the supported.
    int numerator;
    int denominator;
    if (codec.spatialLayers[1].width == 2 * codec.spatialLayers[0].width) {
      numerator = 1;
      denominator = 2;
      // no suffix for 1:2 ratio.
    } else if (2 * codec.spatialLayers[1].width ==
               3 * codec.spatialLayers[0].width) {
      numerator = 2;
      denominator = 3;
      ss << "h";
    } else {
      RTC_LOG(LS_WARNING) << "Unsupported scalability ratio "
                          << codec.spatialLayers[0].width << ":"
                          << codec.spatialLayers[1].width;
      return nullptr;
    }
    // Validate ratio is consistent for all spatial layer transitions.
    for (int sid = 1; sid < num_spatial_layers; ++sid) {
      if (codec.spatialLayers[sid].width * numerator !=
              codec.spatialLayers[sid - 1].width * denominator ||
          codec.spatialLayers[sid].height * numerator !=
              codec.spatialLayers[sid - 1].height * denominator) {
        RTC_LOG(LS_WARNING) << "Inconsistent scalability ratio " << numerator
                            << ":" << denominator;
        return nullptr;
      }
    }
  }

  auto scalability_structure_controller = CreateScalabilityStructure(name);
  if (scalability_structure_controller == nullptr) {
    RTC_LOG(LS_WARNING) << "Unsupported scalability structure " << name;
  } else {
    RTC_LOG(LS_INFO) << "Created scalability structure " << name;
  }
  return scalability_structure_controller;
}

vpx_svc_ref_frame_config_t Vp9References(
    rtc::ArrayView<const ScalableVideoController::LayerFrameConfig> layers) {
  vpx_svc_ref_frame_config_t ref_config = {};
  for (const ScalableVideoController::LayerFrameConfig& layer_frame : layers) {
    const auto& buffers = layer_frame.Buffers();
    RTC_DCHECK_LE(buffers.size(), 3);
    int sid = layer_frame.SpatialId();
    if (!buffers.empty()) {
      ref_config.lst_fb_idx[sid] = buffers[0].id;
      ref_config.reference_last[sid] = buffers[0].referenced;
      if (buffers[0].updated) {
        ref_config.update_buffer_slot[sid] |= (1 << buffers[0].id);
      }
    }
    if (buffers.size() > 1) {
      ref_config.gld_fb_idx[sid] = buffers[1].id;
      ref_config.reference_golden[sid] = buffers[1].referenced;
      if (buffers[1].updated) {
        ref_config.update_buffer_slot[sid] |= (1 << buffers[1].id);
      }
    }
    if (buffers.size() > 2) {
      ref_config.alt_fb_idx[sid] = buffers[2].id;
      ref_config.reference_alt_ref[sid] = buffers[2].referenced;
      if (buffers[2].updated) {
        ref_config.update_buffer_slot[sid] |= (1 << buffers[2].id);
      }
    }
  }
  // TODO(bugs.webrtc.org/11999): Fill ref_config.duration
  return ref_config;
}

}  // namespace

void VP9EncoderImpl::EncoderOutputCodedPacketCallback(vpx_codec_cx_pkt* pkt,
                                                      void* user_data) {
  VP9EncoderImpl* enc = static_cast<VP9EncoderImpl*>(user_data);
  enc->GetEncodedLayerFrame(pkt);
}

VP9EncoderImpl::VP9EncoderImpl(const cricket::VideoCodec& codec)
    : VP9EncoderImpl(codec, FieldTrialBasedConfig()) {}

VP9EncoderImpl::VP9EncoderImpl(const cricket::VideoCodec& codec,
                               const WebRtcKeyValueConfig& trials)
    : encoded_image_(),
      encoded_complete_callback_(nullptr),
      profile_(
          ParseSdpForVP9Profile(codec.params).value_or(VP9Profile::kProfile0)),
      inited_(false),
      timestamp_(0),
      cpu_speed_(3),
      rc_max_intra_target_(0),
      encoder_(nullptr),
      config_(nullptr),
      raw_(nullptr),
      input_image_(nullptr),
      force_key_frame_(true),
      pics_since_key_(0),
      num_temporal_layers_(0),
      num_spatial_layers_(0),
      num_active_spatial_layers_(0),
      first_active_layer_(0),
      layer_deactivation_requires_key_frame_(absl::StartsWith(
          trials.Lookup("WebRTC-Vp9IssueKeyFrameOnLayerDeactivation"),
          "Enabled")),
      is_svc_(false),
      inter_layer_pred_(InterLayerPredMode::kOn),
      external_ref_control_(false),  // Set in InitEncode because of tests.
      trusted_rate_controller_(
          RateControlSettings::ParseFromKeyValueConfig(&trials)
              .LibvpxVp9TrustedRateController()),
      layer_buffering_(false),
      full_superframe_drop_(true),
      first_frame_in_picture_(true),
      ss_info_needed_(false),
      force_all_active_layers_(false),
      use_svc_controller_(
          absl::StartsWith(trials.Lookup("WebRTC-Vp9DependencyDescriptor"),
                           "Enabled")),
      is_flexible_mode_(false),
      variable_framerate_experiment_(ParseVariableFramerateConfig(trials)),
      variable_framerate_controller_(
          variable_framerate_experiment_.framerate_limit),
      quality_scaler_experiment_(ParseQualityScalerConfig(trials)),
      external_ref_ctrl_(
          !absl::StartsWith(trials.Lookup("WebRTC-Vp9ExternalRefCtrl"),
                            "Disabled")),
      per_layer_speed_(ParsePerLayerSpeed(trials)),
      num_steady_state_frames_(0),
      config_changed_(true) {
  codec_ = {};
  memset(&svc_params_, 0, sizeof(vpx_svc_extra_cfg_t));
}

VP9EncoderImpl::~VP9EncoderImpl() {
  Release();
}

void VP9EncoderImpl::SetFecControllerOverride(FecControllerOverride*) {
  // Ignored.
}

int VP9EncoderImpl::Release() {
  int ret_val = WEBRTC_VIDEO_CODEC_OK;

  if (encoder_ != nullptr) {
    if (inited_) {
      if (vpx_codec_destroy(encoder_)) {
        ret_val = WEBRTC_VIDEO_CODEC_MEMORY;
      }
    }
    delete encoder_;
    encoder_ = nullptr;
  }
  if (config_ != nullptr) {
    delete config_;
    config_ = nullptr;
  }
  if (raw_ != nullptr) {
    vpx_img_free(raw_);
    raw_ = nullptr;
  }
  inited_ = false;
  return ret_val;
}

bool VP9EncoderImpl::ExplicitlyConfiguredSpatialLayers() const {
  // We check target_bitrate_bps of the 0th layer to see if the spatial layers
  // (i.e. bitrates) were explicitly configured.
  return codec_.spatialLayers[0].targetBitrate > 0;
}

bool VP9EncoderImpl::SetSvcRates(
    const VideoBitrateAllocation& bitrate_allocation) {
  std::pair<size_t, size_t> current_layers =
      GetActiveLayers(current_bitrate_allocation_);
  std::pair<size_t, size_t> new_layers = GetActiveLayers(bitrate_allocation);

  const bool layer_activation_requires_key_frame =
      inter_layer_pred_ == InterLayerPredMode::kOff ||
      inter_layer_pred_ == InterLayerPredMode::kOnKeyPic;
  const bool lower_layers_enabled = new_layers.first < current_layers.first;
  const bool higher_layers_enabled = new_layers.second > current_layers.second;
  const bool disabled_layers = new_layers.first > current_layers.first ||
                               new_layers.second < current_layers.second;

  if (lower_layers_enabled ||
      (higher_layers_enabled && layer_activation_requires_key_frame) ||
      (disabled_layers && layer_deactivation_requires_key_frame_)) {
    force_key_frame_ = true;
  }

  if (current_layers != new_layers) {
    ss_info_needed_ = true;
  }

  config_->rc_target_bitrate = bitrate_allocation.get_sum_kbps();

  if (ExplicitlyConfiguredSpatialLayers()) {
    for (size_t sl_idx = 0; sl_idx < num_spatial_layers_; ++sl_idx) {
      const bool was_layer_active = (config_->ss_target_bitrate[sl_idx] > 0);
      config_->ss_target_bitrate[sl_idx] =
          bitrate_allocation.GetSpatialLayerSum(sl_idx) / 1000;

      for (size_t tl_idx = 0; tl_idx < num_temporal_layers_; ++tl_idx) {
        config_->layer_target_bitrate[sl_idx * num_temporal_layers_ + tl_idx] =
            bitrate_allocation.GetTemporalLayerSum(sl_idx, tl_idx) / 1000;
      }

      if (!was_layer_active) {
        // Reset frame rate controller if layer is resumed after pause.
        framerate_controller_[sl_idx].Reset();
      }

      framerate_controller_[sl_idx].SetTargetRate(
          codec_.spatialLayers[sl_idx].maxFramerate);
    }
  } else {
    float rate_ratio[VPX_MAX_LAYERS] = {0};
    float total = 0;
    for (int i = 0; i < num_spatial_layers_; ++i) {
      if (svc_params_.scaling_factor_num[i] <= 0 ||
          svc_params_.scaling_factor_den[i] <= 0) {
        RTC_LOG(LS_ERROR) << "Scaling factors not specified!";
        return false;
      }
      rate_ratio[i] = static_cast<float>(svc_params_.scaling_factor_num[i]) /
                      svc_params_.scaling_factor_den[i];
      total += rate_ratio[i];
    }

    for (int i = 0; i < num_spatial_layers_; ++i) {
      RTC_CHECK_GT(total, 0);
      config_->ss_target_bitrate[i] = static_cast<unsigned int>(
          config_->rc_target_bitrate * rate_ratio[i] / total);
      if (num_temporal_layers_ == 1) {
        config_->layer_target_bitrate[i] = config_->ss_target_bitrate[i];
      } else if (num_temporal_layers_ == 2) {
        config_->layer_target_bitrate[i * num_temporal_layers_] =
            config_->ss_target_bitrate[i] * 2 / 3;
        config_->layer_target_bitrate[i * num_temporal_layers_ + 1] =
            config_->ss_target_bitrate[i];
      } else if (num_temporal_layers_ == 3) {
        config_->layer_target_bitrate[i * num_temporal_layers_] =
            config_->ss_target_bitrate[i] / 2;
        config_->layer_target_bitrate[i * num_temporal_layers_ + 1] =
            config_->layer_target_bitrate[i * num_temporal_layers_] +
            (config_->ss_target_bitrate[i] / 4);
        config_->layer_target_bitrate[i * num_temporal_layers_ + 2] =
            config_->ss_target_bitrate[i];
      } else {
        RTC_LOG(LS_ERROR) << "Unsupported number of temporal layers: "
                          << num_temporal_layers_;
        return false;
      }

      framerate_controller_[i].SetTargetRate(codec_.maxFramerate);
    }
  }

  num_active_spatial_layers_ = 0;
  first_active_layer_ = 0;
  bool seen_active_layer = false;
  bool expect_no_more_active_layers = false;
  int highest_active_width = 0;
  int highest_active_height = 0;
  for (int i = 0; i < num_spatial_layers_; ++i) {
    if (config_->ss_target_bitrate[i] > 0) {
      RTC_DCHECK(!expect_no_more_active_layers) << "Only middle layer is "
                                                   "deactivated.";
      if (!seen_active_layer) {
        first_active_layer_ = i;
      }
      num_active_spatial_layers_ = i + 1;
      seen_active_layer = true;
      highest_active_width =
          (svc_params_.scaling_factor_num[i] * config_->g_w) /
          svc_params_.scaling_factor_den[i];
      highest_active_height =
          (svc_params_.scaling_factor_num[i] * config_->g_h) /
          svc_params_.scaling_factor_den[i];
    } else {
      expect_no_more_active_layers = seen_active_layer;
    }
  }

  if (higher_layers_enabled && !force_key_frame_) {
    // Prohibit drop of all layers for the next frame, so newly enabled
    // layer would have a valid spatial reference.
    for (size_t i = 0; i < num_spatial_layers_; ++i) {
      svc_drop_frame_.framedrop_thresh[i] = 0;
    }
    force_all_active_layers_ = true;
  }

  if (svc_controller_) {
    VideoBitrateAllocation allocation;
    for (int sid = 0; sid < num_spatial_layers_; ++sid) {
      for (int tid = 0; tid < num_temporal_layers_; ++tid) {
        allocation.SetBitrate(
            sid, tid,
            config_->layer_target_bitrate[sid * num_temporal_layers_ + tid] *
                1000);
      }
    }
    svc_controller_->OnRatesUpdated(allocation);
  }
  current_bitrate_allocation_ = bitrate_allocation;
  cpu_speed_ = GetCpuSpeed(highest_active_width, highest_active_height);
  config_changed_ = true;
  return true;
}

void VP9EncoderImpl::SetRates(const RateControlParameters& parameters) {
  if (!inited_) {
    RTC_LOG(LS_WARNING) << "SetRates() calll while uninitialzied.";
    return;
  }
  if (encoder_->err) {
    RTC_LOG(LS_WARNING) << "Encoder in error state: " << encoder_->err;
    return;
  }
  if (parameters.framerate_fps < 1.0) {
    RTC_LOG(LS_WARNING) << "Unsupported framerate: "
                        << parameters.framerate_fps;
    return;
  }

  codec_.maxFramerate = static_cast<uint32_t>(parameters.framerate_fps + 0.5);

  bool res = SetSvcRates(parameters.bitrate);
  RTC_DCHECK(res) << "Failed to set new bitrate allocation";
  config_changed_ = true;
}

// TODO(eladalon): s/inst/codec_settings/g.
int VP9EncoderImpl::InitEncode(const VideoCodec* inst,
                               const Settings& settings) {
  if (inst == nullptr) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  if (inst->maxFramerate < 1) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  // Allow zero to represent an unspecified maxBitRate
  if (inst->maxBitrate > 0 && inst->startBitrate > inst->maxBitrate) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  if (inst->width < 1 || inst->height < 1) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  if (settings.number_of_cores < 1) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  if (inst->VP9().numberOfTemporalLayers > 3) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  // libvpx probably does not support more than 3 spatial layers.
  if (inst->VP9().numberOfSpatialLayers > 3) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }

  absl::optional<vpx_img_fmt_t> previous_img_fmt =
      raw_ ? absl::make_optional<vpx_img_fmt_t>(raw_->fmt) : absl::nullopt;

  int ret_val = Release();
  if (ret_val < 0) {
    return ret_val;
  }
  if (encoder_ == nullptr) {
    encoder_ = new vpx_codec_ctx_t;
  }
  if (config_ == nullptr) {
    config_ = new vpx_codec_enc_cfg_t;
  }
  timestamp_ = 0;
  if (&codec_ != inst) {
    codec_ = *inst;
  }

  force_key_frame_ = true;
  pics_since_key_ = 0;

  num_spatial_layers_ = inst->VP9().numberOfSpatialLayers;
  RTC_DCHECK_GT(num_spatial_layers_, 0);
  num_temporal_layers_ = inst->VP9().numberOfTemporalLayers;
  if (num_temporal_layers_ == 0) {
    num_temporal_layers_ = 1;
  }

  if (use_svc_controller_) {
    svc_controller_ = CreateVp9ScalabilityStructure(*inst);
  }
  framerate_controller_ = std::vector<FramerateController>(
      num_spatial_layers_, FramerateController(codec_.maxFramerate));

  is_svc_ = (num_spatial_layers_ > 1 || num_temporal_layers_ > 1);

  // Populate encoder configuration with default values.
  if (vpx_codec_enc_config_default(vpx_codec_vp9_cx(), config_, 0)) {
    return WEBRTC_VIDEO_CODEC_ERROR;
  }

  vpx_img_fmt img_fmt = VPX_IMG_FMT_NONE;
  unsigned int bits_for_storage = 8;
  switch (profile_) {
    case VP9Profile::kProfile0:
      img_fmt = previous_img_fmt.value_or(VPX_IMG_FMT_I420);
      bits_for_storage = 8;
      config_->g_bit_depth = VPX_BITS_8;
      config_->g_profile = 0;
      config_->g_input_bit_depth = 8;
      break;
    case VP9Profile::kProfile1:
      // Encoding of profile 1 is not implemented. It would require extended
      // support for I444, I422, and I440 buffers.
      RTC_NOTREACHED();
      break;
    case VP9Profile::kProfile2:
      img_fmt = VPX_IMG_FMT_I42016;
      bits_for_storage = 16;
      config_->g_bit_depth = VPX_BITS_10;
      config_->g_profile = 2;
      config_->g_input_bit_depth = 10;
      break;
  }

  // Creating a wrapper to the image - setting image data to nullptr. Actual
  // pointer will be set in encode. Setting align to 1, as it is meaningless
  // (actual memory is not allocated).
  raw_ =
      vpx_img_wrap(nullptr, img_fmt, codec_.width, codec_.height, 1, nullptr);
  raw_->bit_depth = bits_for_storage;

  config_->g_w = codec_.width;
  config_->g_h = codec_.height;
  config_->rc_target_bitrate = inst->startBitrate;  // in kbit/s
  config_->g_error_resilient = is_svc_ ? VPX_ERROR_RESILIENT_DEFAULT : 0;
  // Setting the time base of the codec.
  config_->g_timebase.num = 1;
  config_->g_timebase.den = 90000;
  config_->g_lag_in_frames = 0;  // 0- no frame lagging
  config_->g_threads = 1;
  // Rate control settings.
  config_->rc_dropframe_thresh = inst->VP9().frameDroppingOn ? 30 : 0;
  config_->rc_end_usage = VPX_CBR;
  config_->g_pass = VPX_RC_ONE_PASS;
  config_->rc_min_quantizer =
      codec_.mode == VideoCodecMode::kScreensharing ? 8 : 2;
  config_->rc_max_quantizer = 52;
  config_->rc_undershoot_pct = 50;
  config_->rc_overshoot_pct = 50;
  config_->rc_buf_initial_sz = 500;
  config_->rc_buf_optimal_sz = 600;
  config_->rc_buf_sz = 1000;
  // Set the maximum target size of any key-frame.
  rc_max_intra_target_ = MaxIntraTarget(config_->rc_buf_optimal_sz);
  // Key-frame interval is enforced manually by this wrapper.
  config_->kf_mode = VPX_KF_DISABLED;
  // TODO(webm:1592): work-around for libvpx issue, as it can still
  // put some key-frames at will even in VPX_KF_DISABLED kf_mode.
  config_->kf_max_dist = inst->VP9().keyFrameInterval;
  config_->kf_min_dist = config_->kf_max_dist;
  if (quality_scaler_experiment_.enabled) {
    // In that experiment webrtc wide quality scaler is used instead of libvpx
    // internal scaler.
    config_->rc_resize_allowed = 0;
  } else {
    config_->rc_resize_allowed = inst->VP9().automaticResizeOn ? 1 : 0;
  }
  // Determine number of threads based on the image size and #cores.
  config_->g_threads =
      NumberOfThreads(config_->g_w, config_->g_h, settings.number_of_cores);

  cpu_speed_ = GetCpuSpeed(config_->g_w, config_->g_h);

  is_flexible_mode_ = inst->VP9().flexibleMode;

  inter_layer_pred_ = inst->VP9().interLayerPred;

  if (num_spatial_layers_ > 1 &&
      codec_.mode == VideoCodecMode::kScreensharing && !is_flexible_mode_) {
    RTC_LOG(LS_ERROR) << "Flexible mode is required for screenshare with "
                         "several spatial layers";
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }

  // External reference control is required for different frame rate on spatial
  // layers because libvpx generates rtp incompatible references in this case.
  external_ref_control_ = external_ref_ctrl_ ||
                          (num_spatial_layers_ > 1 &&
                           codec_.mode == VideoCodecMode::kScreensharing) ||
                          inter_layer_pred_ == InterLayerPredMode::kOn;

  if (num_temporal_layers_ == 1) {
    gof_.SetGofInfoVP9(kTemporalStructureMode1);
    config_->temporal_layering_mode = VP9E_TEMPORAL_LAYERING_MODE_NOLAYERING;
    config_->ts_number_layers = 1;
    config_->ts_rate_decimator[0] = 1;
    config_->ts_periodicity = 1;
    config_->ts_layer_id[0] = 0;
  } else if (num_temporal_layers_ == 2) {
    gof_.SetGofInfoVP9(kTemporalStructureMode2);
    config_->temporal_layering_mode = VP9E_TEMPORAL_LAYERING_MODE_0101;
    config_->ts_number_layers = 2;
    config_->ts_rate_decimator[0] = 2;
    config_->ts_rate_decimator[1] = 1;
    config_->ts_periodicity = 2;
    config_->ts_layer_id[0] = 0;
    config_->ts_layer_id[1] = 1;
  } else if (num_temporal_layers_ == 3) {
    gof_.SetGofInfoVP9(kTemporalStructureMode3);
    config_->temporal_layering_mode = VP9E_TEMPORAL_LAYERING_MODE_0212;
    config_->ts_number_layers = 3;
    config_->ts_rate_decimator[0] = 4;
    config_->ts_rate_decimator[1] = 2;
    config_->ts_rate_decimator[2] = 1;
    config_->ts_periodicity = 4;
    config_->ts_layer_id[0] = 0;
    config_->ts_layer_id[1] = 2;
    config_->ts_layer_id[2] = 1;
    config_->ts_layer_id[3] = 2;
  } else {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }

  if (external_ref_control_) {
    config_->temporal_layering_mode = VP9E_TEMPORAL_LAYERING_MODE_BYPASS;
    if (num_temporal_layers_ > 1 && num_spatial_layers_ > 1 &&
        codec_.mode == VideoCodecMode::kScreensharing) {
      // External reference control for several temporal layers with different
      // frame rates on spatial layers is not implemented yet.
      return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
    }
  }
  ref_buf_.clear();

  return InitAndSetControlSettings(inst);
}

int VP9EncoderImpl::NumberOfThreads(int width,
                                    int height,
                                    int number_of_cores) {
  // Keep the number of encoder threads equal to the possible number of column
  // tiles, which is (1, 2, 4, 8). See comments below for VP9E_SET_TILE_COLUMNS.
  if (width * height >= 1280 * 720 && number_of_cores > 4) {
    return 4;
  } else if (width * height >= 640 * 360 && number_of_cores > 2) {
    return 2;
  } else {
// Use 2 threads for low res on ARM.
#if defined(WEBRTC_ARCH_ARM) || defined(WEBRTC_ARCH_ARM64) || \
    defined(WEBRTC_ANDROID)
    if (width * height >= 320 * 180 && number_of_cores > 2) {
      return 2;
    }
#endif
    // 1 thread less than VGA.
    return 1;
  }
}

int VP9EncoderImpl::InitAndSetControlSettings(const VideoCodec* inst) {
  // Set QP-min/max per spatial and temporal layer.
  int tot_num_layers = num_spatial_layers_ * num_temporal_layers_;
  for (int i = 0; i < tot_num_layers; ++i) {
    svc_params_.max_quantizers[i] = config_->rc_max_quantizer;
    svc_params_.min_quantizers[i] = config_->rc_min_quantizer;
  }
  config_->ss_number_layers = num_spatial_layers_;
  if (svc_controller_) {
    auto stream_config = svc_controller_->StreamConfig();
    for (int i = 0; i < stream_config.num_spatial_layers; ++i) {
      svc_params_.scaling_factor_num[i] = stream_config.scaling_factor_num[i];
      svc_params_.scaling_factor_den[i] = stream_config.scaling_factor_den[i];
    }
  } else if (ExplicitlyConfiguredSpatialLayers()) {
    for (int i = 0; i < num_spatial_layers_; ++i) {
      const auto& layer = codec_.spatialLayers[i];
      RTC_CHECK_GT(layer.width, 0);
      const int scale_factor = codec_.width / layer.width;
      RTC_DCHECK_GT(scale_factor, 0);

      // Ensure scaler factor is integer.
      if (scale_factor * layer.width != codec_.width) {
        return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
      }

      // Ensure scale factor is the same in both dimensions.
      if (scale_factor * layer.height != codec_.height) {
        return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
      }

      // Ensure scale factor is power of two.
      const bool is_pow_of_two = (scale_factor & (scale_factor - 1)) == 0;
      if (!is_pow_of_two) {
        return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
      }

      svc_params_.scaling_factor_num[i] = 1;
      svc_params_.scaling_factor_den[i] = scale_factor;

      RTC_DCHECK_GT(codec_.spatialLayers[i].maxFramerate, 0);
      RTC_DCHECK_LE(codec_.spatialLayers[i].maxFramerate, codec_.maxFramerate);
      if (i > 0) {
        // Frame rate of high spatial layer is supposed to be equal or higher
        // than frame rate of low spatial layer.
        RTC_DCHECK_GE(codec_.spatialLayers[i].maxFramerate,
                      codec_.spatialLayers[i - 1].maxFramerate);
      }
    }
  } else {
    int scaling_factor_num = 256;
    for (int i = num_spatial_layers_ - 1; i >= 0; --i) {
      // 1:2 scaling in each dimension.
      svc_params_.scaling_factor_num[i] = scaling_factor_num;
      svc_params_.scaling_factor_den[i] = 256;
    }
  }

  SvcRateAllocator init_allocator(codec_);
  current_bitrate_allocation_ =
      init_allocator.Allocate(VideoBitrateAllocationParameters(
          inst->startBitrate * 1000, inst->maxFramerate));
  if (!SetSvcRates(current_bitrate_allocation_)) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }

  const vpx_codec_err_t rv = vpx_codec_enc_init(
      encoder_, vpx_codec_vp9_cx(), config_,
      config_->g_bit_depth == VPX_BITS_8 ? 0 : VPX_CODEC_USE_HIGHBITDEPTH);
  if (rv != VPX_CODEC_OK) {
    RTC_LOG(LS_ERROR) << "Init error: " << vpx_codec_err_to_string(rv);
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }

  if (per_layer_speed_.enabled) {
    for (int i = 0; i < num_spatial_layers_; ++i) {
      if (codec_.spatialLayers[i].active) {
        continue;
      }

      if (per_layer_speed_.layers[i] != -1) {
        svc_params_.speed_per_layer[i] = per_layer_speed_.layers[i];
      } else {
        svc_params_.speed_per_layer[i] = GetCpuSpeed(
            codec_.spatialLayers[i].width, codec_.spatialLayers[i].height);
      }
    }
  }

  vpx_codec_control(encoder_, VP8E_SET_CPUUSED, cpu_speed_);
  vpx_codec_control(encoder_, VP8E_SET_MAX_INTRA_BITRATE_PCT,
                    rc_max_intra_target_);
  vpx_codec_control(encoder_, VP9E_SET_AQ_MODE,
                    inst->VP9().adaptiveQpMode ? 3 : 0);

  vpx_codec_control(encoder_, VP9E_SET_FRAME_PARALLEL_DECODING, 0);
  vpx_codec_control(encoder_, VP9E_SET_SVC_GF_TEMPORAL_REF, 0);

  if (is_svc_) {
    vpx_codec_control(encoder_, VP9E_SET_SVC, 1);
    vpx_codec_control(encoder_, VP9E_SET_SVC_PARAMETERS, &svc_params_);
  }

  if (num_spatial_layers_ > 1) {
    switch (inter_layer_pred_) {
      case InterLayerPredMode::kOn:
        vpx_codec_control(encoder_, VP9E_SET_SVC_INTER_LAYER_PRED, 0);
        break;
      case InterLayerPredMode::kOff:
        vpx_codec_control(encoder_, VP9E_SET_SVC_INTER_LAYER_PRED, 1);
        break;
      case InterLayerPredMode::kOnKeyPic:
        vpx_codec_control(encoder_, VP9E_SET_SVC_INTER_LAYER_PRED, 2);
        break;
      default:
        RTC_NOTREACHED();
    }

    memset(&svc_drop_frame_, 0, sizeof(svc_drop_frame_));
    const bool reverse_constrained_drop_mode =
        inter_layer_pred_ == InterLayerPredMode::kOn &&
        codec_.mode == VideoCodecMode::kScreensharing &&
        num_spatial_layers_ > 1;
    if (reverse_constrained_drop_mode) {
      // Screenshare dropping mode: drop a layer only together with all lower
      // layers. This ensures that drops on lower layers won't reduce frame-rate
      // for higher layers and reference structure is RTP-compatible.
      svc_drop_frame_.framedrop_mode = CONSTRAINED_FROM_ABOVE_DROP;
      svc_drop_frame_.max_consec_drop = 5;
      for (size_t i = 0; i < num_spatial_layers_; ++i) {
        svc_drop_frame_.framedrop_thresh[i] = config_->rc_dropframe_thresh;
      }
      // No buffering is needed because the highest layer is always present in
      // all frames in CONSTRAINED_FROM_ABOVE drop mode.
      layer_buffering_ = false;
    } else {
      // Configure encoder to drop entire superframe whenever it needs to drop
      // a layer. This mode is preferred over per-layer dropping which causes
      // quality flickering and is not compatible with RTP non-flexible mode.
      svc_drop_frame_.framedrop_mode =
          full_superframe_drop_ ? FULL_SUPERFRAME_DROP : CONSTRAINED_LAYER_DROP;
      // Buffering is needed only for constrained layer drop, as it's not clear
      // which frame is the last.
      layer_buffering_ = !full_superframe_drop_;
      svc_drop_frame_.max_consec_drop = std::numeric_limits<int>::max();
      for (size_t i = 0; i < num_spatial_layers_; ++i) {
        svc_drop_frame_.framedrop_thresh[i] = config_->rc_dropframe_thresh;
      }
    }
    vpx_codec_control(encoder_, VP9E_SET_SVC_FRAME_DROP_LAYER,
                      &svc_drop_frame_);
  }

  // Register callback for getting each spatial layer.
  vpx_codec_priv_output_cx_pkt_cb_pair_t cbp = {
      VP9EncoderImpl::EncoderOutputCodedPacketCallback,
      reinterpret_cast<void*>(this)};
  vpx_codec_control(encoder_, VP9E_REGISTER_CX_CALLBACK,
                    reinterpret_cast<void*>(&cbp));

  // Control function to set the number of column tiles in encoding a frame, in
  // log2 unit: e.g., 0 = 1 tile column, 1 = 2 tile columns, 2 = 4 tile columns.
  // The number tile columns will be capped by the encoder based on image size
  // (minimum width of tile column is 256 pixels, maximum is 4096).
  vpx_codec_control(encoder_, VP9E_SET_TILE_COLUMNS, (config_->g_threads >> 1));

  // Turn on row-based multithreading.
  vpx_codec_control(encoder_, VP9E_SET_ROW_MT, 1);

#if !defined(WEBRTC_ARCH_ARM) && !defined(WEBRTC_ARCH_ARM64) && \
    !defined(ANDROID)
  // Do not enable the denoiser on ARM since optimization is pending.
  // Denoiser is on by default on other platforms.
  vpx_codec_control(encoder_, VP9E_SET_NOISE_SENSITIVITY,
                    inst->VP9().denoisingOn ? 1 : 0);
#endif

  if (codec_.mode == VideoCodecMode::kScreensharing) {
    // Adjust internal parameters to screen content.
    vpx_codec_control(encoder_, VP9E_SET_TUNE_CONTENT, 1);
  }
  // Enable encoder skip of static/low content blocks.
  vpx_codec_control(encoder_, VP8E_SET_STATIC_THRESHOLD, 1);
  inited_ = true;
  config_changed_ = true;
  return WEBRTC_VIDEO_CODEC_OK;
}

uint32_t VP9EncoderImpl::MaxIntraTarget(uint32_t optimal_buffer_size) {
  // Set max to the optimal buffer level (normalized by target BR),
  // and scaled by a scale_par.
  // Max target size = scale_par * optimal_buffer_size * targetBR[Kbps].
  // This value is presented in percentage of perFrameBw:
  // perFrameBw = targetBR[Kbps] * 1000 / framerate.
  // The target in % is as follows:
  float scale_par = 0.5;
  uint32_t target_pct =
      optimal_buffer_size * scale_par * codec_.maxFramerate / 10;
  // Don't go below 3 times the per frame bandwidth.
  const uint32_t min_intra_size = 300;
  return (target_pct < min_intra_size) ? min_intra_size : target_pct;
}

int VP9EncoderImpl::Encode(const VideoFrame& input_image,
                           const std::vector<VideoFrameType>* frame_types) {
  if (!inited_) {
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }
  if (encoded_complete_callback_ == nullptr) {
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }
  if (num_active_spatial_layers_ == 0) {
    // All spatial layers are disabled, return without encoding anything.
    return WEBRTC_VIDEO_CODEC_OK;
  }

  // We only support one stream at the moment.
  if (frame_types && !frame_types->empty()) {
    if ((*frame_types)[0] == VideoFrameType::kVideoFrameKey) {
      force_key_frame_ = true;
    }
  }

  if (pics_since_key_ + 1 ==
      static_cast<size_t>(codec_.VP9()->keyFrameInterval)) {
    force_key_frame_ = true;
  }

  if (svc_controller_) {
    layer_frames_ = svc_controller_->NextFrameConfig(force_key_frame_);
    if (layer_frames_.empty()) {
      return WEBRTC_VIDEO_CODEC_ERROR;
    }
  }

  vpx_svc_layer_id_t layer_id = {0};
  if (!force_key_frame_) {
    const size_t gof_idx = (pics_since_key_ + 1) % gof_.num_frames_in_gof;
    layer_id.temporal_layer_id = gof_.temporal_idx[gof_idx];

    if (VideoCodecMode::kScreensharing == codec_.mode) {
      const uint32_t frame_timestamp_ms =
          1000 * input_image.timestamp() / kVideoPayloadTypeFrequency;

      // To ensure that several rate-limiters with different limits don't
      // interfere, they must be queried in order of increasing limit.

      bool use_steady_state_limiter =
          variable_framerate_experiment_.enabled &&
          input_image.update_rect().IsEmpty() &&
          num_steady_state_frames_ >=
              variable_framerate_experiment_.frames_before_steady_state;

      // Need to check all frame limiters, even if lower layers are disabled,
      // because variable frame-rate limiter should be checked after the first
      // layer. It's easier to overwrite active layers after, then check all
      // cases.
      for (uint8_t sl_idx = 0; sl_idx < num_active_spatial_layers_; ++sl_idx) {
        const float layer_fps =
            framerate_controller_[layer_id.spatial_layer_id].GetTargetRate();
        // Use steady state rate-limiter at the correct place.
        if (use_steady_state_limiter &&
            layer_fps > variable_framerate_experiment_.framerate_limit - 1e-9) {
          if (variable_framerate_controller_.DropFrame(frame_timestamp_ms)) {
            layer_id.spatial_layer_id = num_active_spatial_layers_;
          }
          // Break always: if rate limiter triggered frame drop, no need to
          // continue; otherwise, the rate is less than the next limiters.
          break;
        }
        if (framerate_controller_[sl_idx].DropFrame(frame_timestamp_ms)) {
          ++layer_id.spatial_layer_id;
        } else {
          break;
        }
      }

      if (use_steady_state_limiter &&
          layer_id.spatial_layer_id < num_active_spatial_layers_) {
        variable_framerate_controller_.AddFrame(frame_timestamp_ms);
      }
    }

    if (force_all_active_layers_) {
      layer_id.spatial_layer_id = first_active_layer_;
      force_all_active_layers_ = false;
    }

    RTC_DCHECK_LE(layer_id.spatial_layer_id, num_active_spatial_layers_);
    if (layer_id.spatial_layer_id >= num_active_spatial_layers_) {
      // Drop entire picture.
      return WEBRTC_VIDEO_CODEC_OK;
    }
  }

  // Need to set temporal layer id on ALL layers, even disabled ones.
  // Otherwise libvpx might produce frames on a disabled layer:
  // http://crbug.com/1051476
  for (int sl_idx = 0; sl_idx < num_spatial_layers_; ++sl_idx) {
    layer_id.temporal_layer_id_per_spatial[sl_idx] = layer_id.temporal_layer_id;
  }

  if (layer_id.spatial_layer_id < first_active_layer_) {
    layer_id.spatial_layer_id = first_active_layer_;
  }

  if (svc_controller_) {
    layer_id.spatial_layer_id = layer_frames_.front().SpatialId();
    layer_id.temporal_layer_id = layer_frames_.front().TemporalId();
    for (const auto& layer : layer_frames_) {
      layer_id.temporal_layer_id_per_spatial[layer.SpatialId()] =
          layer.TemporalId();
    }
  }

  vpx_codec_control(encoder_, VP9E_SET_SVC_LAYER_ID, &layer_id);

  if (num_spatial_layers_ > 1) {
    // Update frame dropping settings as they may change on per-frame basis.
    vpx_codec_control(encoder_, VP9E_SET_SVC_FRAME_DROP_LAYER,
                      &svc_drop_frame_);
  }

  if (config_changed_) {
    if (vpx_codec_enc_config_set(encoder_, config_)) {
      return WEBRTC_VIDEO_CODEC_ERROR;
    }
    vpx_codec_control(encoder_, VP8E_SET_CPUUSED, cpu_speed_);
    config_changed_ = false;
  }

  RTC_DCHECK_EQ(input_image.width(), raw_->d_w);
  RTC_DCHECK_EQ(input_image.height(), raw_->d_h);

  // Set input image for use in the callback.
  // This was necessary since you need some information from input_image.
  // You can save only the necessary information (such as timestamp) instead of
  // doing this.
  input_image_ = &input_image;

  // Keep reference to buffer until encode completes.
  rtc::scoped_refptr<const VideoFrameBuffer> video_frame_buffer;
  const I010BufferInterface* i010_buffer;
  rtc::scoped_refptr<const I010BufferInterface> i010_copy;
  switch (profile_) {
    case VP9Profile::kProfile0: {
      if (input_image.video_frame_buffer()->type() ==
          VideoFrameBuffer::Type::kNV12) {
        const NV12BufferInterface* nv12_buffer =
            input_image.video_frame_buffer()->GetNV12();
        video_frame_buffer = nv12_buffer;
        MaybeRewrapRawWithFormat(VPX_IMG_FMT_NV12);
        raw_->planes[VPX_PLANE_Y] = const_cast<uint8_t*>(nv12_buffer->DataY());
        raw_->planes[VPX_PLANE_U] = const_cast<uint8_t*>(nv12_buffer->DataUV());
        raw_->planes[VPX_PLANE_V] = raw_->planes[VPX_PLANE_U] + 1;
        raw_->stride[VPX_PLANE_Y] = nv12_buffer->StrideY();
        raw_->stride[VPX_PLANE_U] = nv12_buffer->StrideUV();
        raw_->stride[VPX_PLANE_V] = nv12_buffer->StrideUV();
      } else {
        rtc::scoped_refptr<I420BufferInterface> i420_buffer =
            input_image.video_frame_buffer()->ToI420();
        video_frame_buffer = i420_buffer;
        MaybeRewrapRawWithFormat(VPX_IMG_FMT_I420);
        // Image in vpx_image_t format.
        // Input image is const. VPX's raw image is not defined as const.
        raw_->planes[VPX_PLANE_Y] = const_cast<uint8_t*>(i420_buffer->DataY());
        raw_->planes[VPX_PLANE_U] = const_cast<uint8_t*>(i420_buffer->DataU());
        raw_->planes[VPX_PLANE_V] = const_cast<uint8_t*>(i420_buffer->DataV());
        raw_->stride[VPX_PLANE_Y] = i420_buffer->StrideY();
        raw_->stride[VPX_PLANE_U] = i420_buffer->StrideU();
        raw_->stride[VPX_PLANE_V] = i420_buffer->StrideV();
      }
      break;
    }
    case VP9Profile::kProfile1: {
      RTC_NOTREACHED();
      break;
    }
    case VP9Profile::kProfile2: {
      // We can inject kI010 frames directly for encode. All other formats
      // should be converted to it.
      switch (input_image.video_frame_buffer()->type()) {
        case VideoFrameBuffer::Type::kI010: {
          i010_buffer = input_image.video_frame_buffer()->GetI010();
          break;
        }
        default: {
          i010_copy =
              I010Buffer::Copy(*input_image.video_frame_buffer()->ToI420());
          i010_buffer = i010_copy.get();
        }
      }
      raw_->planes[VPX_PLANE_Y] = const_cast<uint8_t*>(
          reinterpret_cast<const uint8_t*>(i010_buffer->DataY()));
      raw_->planes[VPX_PLANE_U] = const_cast<uint8_t*>(
          reinterpret_cast<const uint8_t*>(i010_buffer->DataU()));
      raw_->planes[VPX_PLANE_V] = const_cast<uint8_t*>(
          reinterpret_cast<const uint8_t*>(i010_buffer->DataV()));
      raw_->stride[VPX_PLANE_Y] = i010_buffer->StrideY() * 2;
      raw_->stride[VPX_PLANE_U] = i010_buffer->StrideU() * 2;
      raw_->stride[VPX_PLANE_V] = i010_buffer->StrideV() * 2;
      break;
    }
  }

  vpx_enc_frame_flags_t flags = 0;
  if (force_key_frame_) {
    flags = VPX_EFLAG_FORCE_KF;
  }

  if (svc_controller_) {
    vpx_svc_ref_frame_config_t ref_config = Vp9References(layer_frames_);
    vpx_codec_control(encoder_, VP9E_SET_SVC_REF_FRAME_CONFIG, &ref_config);
  } else if (external_ref_control_) {
    vpx_svc_ref_frame_config_t ref_config =
        SetReferences(force_key_frame_, layer_id.spatial_layer_id);

    if (VideoCodecMode::kScreensharing == codec_.mode) {
      for (uint8_t sl_idx = 0; sl_idx < num_active_spatial_layers_; ++sl_idx) {
        ref_config.duration[sl_idx] = static_cast<int64_t>(
            90000 / (std::min(static_cast<float>(codec_.maxFramerate),
                              framerate_controller_[sl_idx].GetTargetRate())));
      }
    }

    vpx_codec_control(encoder_, VP9E_SET_SVC_REF_FRAME_CONFIG, &ref_config);
  }

  first_frame_in_picture_ = true;

  // TODO(ssilkin): Frame duration should be specified per spatial layer
  // since their frame rate can be different. For now calculate frame duration
  // based on target frame rate of the highest spatial layer, which frame rate
  // is supposed to be equal or higher than frame rate of low spatial layers.
  // Also, timestamp should represent actual time passed since previous frame
  // (not 'expected' time). Then rate controller can drain buffer more
  // accurately.
  RTC_DCHECK_GE(framerate_controller_.size(), num_active_spatial_layers_);
  float target_framerate_fps =
      (codec_.mode == VideoCodecMode::kScreensharing)
          ? std::min(static_cast<float>(codec_.maxFramerate),
                     framerate_controller_[num_active_spatial_layers_ - 1]
                         .GetTargetRate())
          : codec_.maxFramerate;
  uint32_t duration = static_cast<uint32_t>(90000 / target_framerate_fps);
  const vpx_codec_err_t rv = vpx_codec_encode(encoder_, raw_, timestamp_,
                                              duration, flags, VPX_DL_REALTIME);
  if (rv != VPX_CODEC_OK) {
    RTC_LOG(LS_ERROR) << "Encoding error: " << vpx_codec_err_to_string(rv)
                      << "\n"
                         "Details: "
                      << vpx_codec_error(encoder_) << "\n"
                      << vpx_codec_error_detail(encoder_);
    return WEBRTC_VIDEO_CODEC_ERROR;
  }
  timestamp_ += duration;

  if (layer_buffering_) {
    const bool end_of_picture = true;
    DeliverBufferedFrame(end_of_picture);
  }

  return WEBRTC_VIDEO_CODEC_OK;
}

void VP9EncoderImpl::PopulateCodecSpecific(CodecSpecificInfo* codec_specific,
                                           absl::optional<int>* spatial_idx,
                                           const vpx_codec_cx_pkt& pkt,
                                           uint32_t timestamp) {
  RTC_CHECK(codec_specific != nullptr);
  codec_specific->codecType = kVideoCodecVP9;
  CodecSpecificInfoVP9* vp9_info = &(codec_specific->codecSpecific.VP9);

  vp9_info->first_frame_in_picture = first_frame_in_picture_;
  vp9_info->flexible_mode = is_flexible_mode_;

  if (pkt.data.frame.flags & VPX_FRAME_IS_KEY) {
    pics_since_key_ = 0;
  } else if (first_frame_in_picture_) {
    ++pics_since_key_;
  }

  vpx_svc_layer_id_t layer_id = {0};
  vpx_codec_control(encoder_, VP9E_GET_SVC_LAYER_ID, &layer_id);

  // Can't have keyframe with non-zero temporal layer.
  RTC_DCHECK(pics_since_key_ != 0 || layer_id.temporal_layer_id == 0);

  RTC_CHECK_GT(num_temporal_layers_, 0);
  RTC_CHECK_GT(num_active_spatial_layers_, 0);
  if (num_temporal_layers_ == 1) {
    RTC_CHECK_EQ(layer_id.temporal_layer_id, 0);
    vp9_info->temporal_idx = kNoTemporalIdx;
  } else {
    vp9_info->temporal_idx = layer_id.temporal_layer_id;
  }
  if (num_active_spatial_layers_ == 1) {
    RTC_CHECK_EQ(layer_id.spatial_layer_id, 0);
    *spatial_idx = absl::nullopt;
  } else {
    *spatial_idx = layer_id.spatial_layer_id;
  }

  // TODO(asapersson): this info has to be obtained from the encoder.
  vp9_info->temporal_up_switch = false;

  const bool is_key_pic = (pics_since_key_ == 0);
  const bool is_inter_layer_pred_allowed =
      (inter_layer_pred_ == InterLayerPredMode::kOn ||
       (inter_layer_pred_ == InterLayerPredMode::kOnKeyPic && is_key_pic));

  // Always set inter_layer_predicted to true on high layer frame if inter-layer
  // prediction (ILP) is allowed even if encoder didn't actually use it.
  // Setting inter_layer_predicted to false would allow receiver to decode high
  // layer frame without decoding low layer frame. If that would happen (e.g.
  // if low layer frame is lost) then receiver won't be able to decode next high
  // layer frame which uses ILP.
  vp9_info->inter_layer_predicted =
      first_frame_in_picture_ ? false : is_inter_layer_pred_allowed;

  // Mark all low spatial layer frames as references (not just frames of
  // active low spatial layers) if inter-layer prediction is enabled since
  // these frames are indirect references of high spatial layer, which can
  // later be enabled without key frame.
  vp9_info->non_ref_for_inter_layer_pred =
      !is_inter_layer_pred_allowed ||
      layer_id.spatial_layer_id + 1 == num_spatial_layers_;

  // Always populate this, so that the packetizer can properly set the marker
  // bit.
  vp9_info->num_spatial_layers = num_active_spatial_layers_;
  vp9_info->first_active_layer = first_active_layer_;

  vp9_info->num_ref_pics = 0;
  FillReferenceIndices(pkt, pics_since_key_, vp9_info->inter_layer_predicted,
                       vp9_info);
  if (vp9_info->flexible_mode) {
    vp9_info->gof_idx = kNoGofIdx;
  } else {
    vp9_info->gof_idx =
        static_cast<uint8_t>(pics_since_key_ % gof_.num_frames_in_gof);
    vp9_info->temporal_up_switch = gof_.temporal_up_switch[vp9_info->gof_idx];
    RTC_DCHECK(vp9_info->num_ref_pics == gof_.num_ref_pics[vp9_info->gof_idx] ||
               vp9_info->num_ref_pics == 0);
  }

  vp9_info->inter_pic_predicted = (!is_key_pic && vp9_info->num_ref_pics > 0);

  // Write SS on key frame of independently coded spatial layers and on base
  // temporal/spatial layer frame if number of layers changed without issuing
  // of key picture (inter-layer prediction is enabled).
  const bool is_key_frame = is_key_pic && !vp9_info->inter_layer_predicted;
  if (is_key_frame || (ss_info_needed_ && layer_id.temporal_layer_id == 0 &&
                       layer_id.spatial_layer_id == first_active_layer_)) {
    vp9_info->ss_data_available = true;
    vp9_info->spatial_layer_resolution_present = true;
    // Signal disabled layers.
    for (size_t i = 0; i < first_active_layer_; ++i) {
      vp9_info->width[i] = 0;
      vp9_info->height[i] = 0;
    }
    for (size_t i = first_active_layer_; i < num_active_spatial_layers_; ++i) {
      vp9_info->width[i] = codec_.width * svc_params_.scaling_factor_num[i] /
                           svc_params_.scaling_factor_den[i];
      vp9_info->height[i] = codec_.height * svc_params_.scaling_factor_num[i] /
                            svc_params_.scaling_factor_den[i];
    }
    if (vp9_info->flexible_mode) {
      vp9_info->gof.num_frames_in_gof = 0;
    } else {
      vp9_info->gof.CopyGofInfoVP9(gof_);
    }

    ss_info_needed_ = false;
  } else {
    vp9_info->ss_data_available = false;
  }

  first_frame_in_picture_ = false;

  // Populate codec-agnostic section in the codec specific structure.
  if (svc_controller_) {
    auto it = absl::c_find_if(
        layer_frames_,
        [&](const ScalableVideoController::LayerFrameConfig& config) {
          return config.SpatialId() == spatial_idx->value_or(0);
        });
    RTC_CHECK(it != layer_frames_.end())
        << "Failed to find spatial id " << spatial_idx->value_or(0);
    codec_specific->generic_frame_info = svc_controller_->OnEncodeDone(*it);
    if (is_key_frame) {
      codec_specific->template_structure =
          svc_controller_->DependencyStructure();
      auto& resolutions = codec_specific->template_structure->resolutions;
      resolutions.resize(num_spatial_layers_);
      for (int sid = 0; sid < num_spatial_layers_; ++sid) {
        resolutions[sid] = RenderResolution(
            /*width=*/codec_.width * svc_params_.scaling_factor_num[sid] /
                svc_params_.scaling_factor_den[sid],
            /*height=*/codec_.height * svc_params_.scaling_factor_num[sid] /
                svc_params_.scaling_factor_den[sid]);
      }
    }
  }
}

void VP9EncoderImpl::FillReferenceIndices(const vpx_codec_cx_pkt& pkt,
                                          const size_t pic_num,
                                          const bool inter_layer_predicted,
                                          CodecSpecificInfoVP9* vp9_info) {
  vpx_svc_layer_id_t layer_id = {0};
  vpx_codec_control(encoder_, VP9E_GET_SVC_LAYER_ID, &layer_id);

  const bool is_key_frame =
      (pkt.data.frame.flags & VPX_FRAME_IS_KEY) ? true : false;

  std::vector<RefFrameBuffer> ref_buf_list;

  if (is_svc_) {
    vpx_svc_ref_frame_config_t enc_layer_conf = {{0}};
    vpx_codec_control(encoder_, VP9E_GET_SVC_REF_FRAME_CONFIG, &enc_layer_conf);
    int ref_buf_flags = 0;

    if (enc_layer_conf.reference_last[layer_id.spatial_layer_id]) {
      const size_t fb_idx =
          enc_layer_conf.lst_fb_idx[layer_id.spatial_layer_id];
      RTC_DCHECK(ref_buf_.find(fb_idx) != ref_buf_.end());
      if (std::find(ref_buf_list.begin(), ref_buf_list.end(),
                    ref_buf_.at(fb_idx)) == ref_buf_list.end()) {
        ref_buf_list.push_back(ref_buf_.at(fb_idx));
        ref_buf_flags |= 1 << fb_idx;
      }
    }

    if (enc_layer_conf.reference_alt_ref[layer_id.spatial_layer_id]) {
      const size_t fb_idx =
          enc_layer_conf.alt_fb_idx[layer_id.spatial_layer_id];
      RTC_DCHECK(ref_buf_.find(fb_idx) != ref_buf_.end());
      if (std::find(ref_buf_list.begin(), ref_buf_list.end(),
                    ref_buf_.at(fb_idx)) == ref_buf_list.end()) {
        ref_buf_list.push_back(ref_buf_.at(fb_idx));
        ref_buf_flags |= 1 << fb_idx;
      }
    }

    if (enc_layer_conf.reference_golden[layer_id.spatial_layer_id]) {
      const size_t fb_idx =
          enc_layer_conf.gld_fb_idx[layer_id.spatial_layer_id];
      RTC_DCHECK(ref_buf_.find(fb_idx) != ref_buf_.end());
      if (std::find(ref_buf_list.begin(), ref_buf_list.end(),
                    ref_buf_.at(fb_idx)) == ref_buf_list.end()) {
        ref_buf_list.push_back(ref_buf_.at(fb_idx));
        ref_buf_flags |= 1 << fb_idx;
      }
    }

    RTC_LOG(LS_VERBOSE) << "Frame " << pic_num << " sl "
                        << layer_id.spatial_layer_id << " tl "
                        << layer_id.temporal_layer_id << " refered buffers "
                        << (ref_buf_flags & (1 << 0) ? 1 : 0)
                        << (ref_buf_flags & (1 << 1) ? 1 : 0)
                        << (ref_buf_flags & (1 << 2) ? 1 : 0)
                        << (ref_buf_flags & (1 << 3) ? 1 : 0)
                        << (ref_buf_flags & (1 << 4) ? 1 : 0)
                        << (ref_buf_flags & (1 << 5) ? 1 : 0)
                        << (ref_buf_flags & (1 << 6) ? 1 : 0)
                        << (ref_buf_flags & (1 << 7) ? 1 : 0);

  } else if (!is_key_frame) {
    RTC_DCHECK_EQ(num_spatial_layers_, 1);
    RTC_DCHECK_EQ(num_temporal_layers_, 1);
    // In non-SVC mode encoder doesn't provide reference list. Assume each frame
    // refers previous one, which is stored in buffer 0.
    ref_buf_list.push_back(ref_buf_.at(0));
  }

  size_t max_ref_temporal_layer_id = 0;

  std::vector<size_t> ref_pid_list;

  vp9_info->num_ref_pics = 0;
  for (const RefFrameBuffer& ref_buf : ref_buf_list) {
    RTC_DCHECK_LE(ref_buf.pic_num, pic_num);
    if (ref_buf.pic_num < pic_num) {
      if (inter_layer_pred_ != InterLayerPredMode::kOn) {
        // RTP spec limits temporal prediction to the same spatial layer.
        // It is safe to ignore this requirement if inter-layer prediction is
        // enabled for all frames when all base frames are relayed to receiver.
        RTC_DCHECK_EQ(ref_buf.spatial_layer_id, layer_id.spatial_layer_id);
      } else {
        RTC_DCHECK_LE(ref_buf.spatial_layer_id, layer_id.spatial_layer_id);
      }
      RTC_DCHECK_LE(ref_buf.temporal_layer_id, layer_id.temporal_layer_id);

      // Encoder may reference several spatial layers on the same previous
      // frame in case if some spatial layers are skipped on the current frame.
      // We shouldn't put duplicate references as it may break some old
      // clients and isn't RTP compatible.
      if (std::find(ref_pid_list.begin(), ref_pid_list.end(),
                    ref_buf.pic_num) != ref_pid_list.end()) {
        continue;
      }
      ref_pid_list.push_back(ref_buf.pic_num);

      const size_t p_diff = pic_num - ref_buf.pic_num;
      RTC_DCHECK_LE(p_diff, 127UL);

      vp9_info->p_diff[vp9_info->num_ref_pics] = static_cast<uint8_t>(p_diff);
      ++vp9_info->num_ref_pics;

      max_ref_temporal_layer_id =
          std::max(max_ref_temporal_layer_id, ref_buf.temporal_layer_id);
    } else {
      RTC_DCHECK(inter_layer_predicted);
      // RTP spec only allows to use previous spatial layer for inter-layer
      // prediction.
      RTC_DCHECK_EQ(ref_buf.spatial_layer_id + 1, layer_id.spatial_layer_id);
    }
  }

  vp9_info->temporal_up_switch =
      (max_ref_temporal_layer_id <
       static_cast<size_t>(layer_id.temporal_layer_id));
}

void VP9EncoderImpl::UpdateReferenceBuffers(const vpx_codec_cx_pkt& pkt,
                                            const size_t pic_num) {
  vpx_svc_layer_id_t layer_id = {0};
  vpx_codec_control(encoder_, VP9E_GET_SVC_LAYER_ID, &layer_id);

  RefFrameBuffer frame_buf(pic_num, layer_id.spatial_layer_id,
                           layer_id.temporal_layer_id);

  if (is_svc_) {
    vpx_svc_ref_frame_config_t enc_layer_conf = {{0}};
    vpx_codec_control(encoder_, VP9E_GET_SVC_REF_FRAME_CONFIG, &enc_layer_conf);
    const int update_buffer_slot =
        enc_layer_conf.update_buffer_slot[layer_id.spatial_layer_id];

    for (size_t i = 0; i < kNumVp9Buffers; ++i) {
      if (update_buffer_slot & (1 << i)) {
        ref_buf_[i] = frame_buf;
      }
    }

    RTC_LOG(LS_VERBOSE) << "Frame " << pic_num << " sl "
                        << layer_id.spatial_layer_id << " tl "
                        << layer_id.temporal_layer_id << " updated buffers "
                        << (update_buffer_slot & (1 << 0) ? 1 : 0)
                        << (update_buffer_slot & (1 << 1) ? 1 : 0)
                        << (update_buffer_slot & (1 << 2) ? 1 : 0)
                        << (update_buffer_slot & (1 << 3) ? 1 : 0)
                        << (update_buffer_slot & (1 << 4) ? 1 : 0)
                        << (update_buffer_slot & (1 << 5) ? 1 : 0)
                        << (update_buffer_slot & (1 << 6) ? 1 : 0)
                        << (update_buffer_slot & (1 << 7) ? 1 : 0);
  } else {
    RTC_DCHECK_EQ(num_spatial_layers_, 1);
    RTC_DCHECK_EQ(num_temporal_layers_, 1);
    // In non-svc mode encoder doesn't provide reference list. Assume each frame
    // is reference and stored in buffer 0.
    ref_buf_[0] = frame_buf;
  }
}

vpx_svc_ref_frame_config_t VP9EncoderImpl::SetReferences(
    bool is_key_pic,
    size_t first_active_spatial_layer_id) {
  // kRefBufIdx, kUpdBufIdx need to be updated to support longer GOFs.
  RTC_DCHECK_LE(gof_.num_frames_in_gof, 4);

  vpx_svc_ref_frame_config_t ref_config;
  memset(&ref_config, 0, sizeof(ref_config));

  const size_t num_temporal_refs = std::max(1, num_temporal_layers_ - 1);
  const bool is_inter_layer_pred_allowed =
      inter_layer_pred_ == InterLayerPredMode::kOn ||
      (inter_layer_pred_ == InterLayerPredMode::kOnKeyPic && is_key_pic);
  absl::optional<int> last_updated_buf_idx;

  // Put temporal reference to LAST and spatial reference to GOLDEN. Update
  // frame buffer (i.e. store encoded frame) if current frame is a temporal
  // reference (i.e. it belongs to a low temporal layer) or it is a spatial
  // reference. In later case, always store spatial reference in the last
  // reference frame buffer.
  // For the case of 3 temporal and 3 spatial layers we need 6 frame buffers
  // for temporal references plus 1 buffer for spatial reference. 7 buffers
  // in total.

  for (size_t sl_idx = first_active_spatial_layer_id;
       sl_idx < num_active_spatial_layers_; ++sl_idx) {
    const size_t curr_pic_num = is_key_pic ? 0 : pics_since_key_ + 1;
    const size_t gof_idx = curr_pic_num % gof_.num_frames_in_gof;

    if (!is_key_pic) {
      // Set up temporal reference.
      const int buf_idx = sl_idx * num_temporal_refs + kRefBufIdx[gof_idx];

      // Last reference frame buffer is reserved for spatial reference. It is
      // not supposed to be used for temporal prediction.
      RTC_DCHECK_LT(buf_idx, kNumVp9Buffers - 1);

      const int pid_diff = curr_pic_num - ref_buf_[buf_idx].pic_num;
      // Incorrect spatial layer may be in the buffer due to a key-frame.
      const bool same_spatial_layer =
          ref_buf_[buf_idx].spatial_layer_id == sl_idx;
      bool correct_pid = false;
      if (is_flexible_mode_) {
        correct_pid = pid_diff > 0 && pid_diff < kMaxAllowedPidDiff;
      } else {
        // Below code assumes single temporal referecence.
        RTC_DCHECK_EQ(gof_.num_ref_pics[gof_idx], 1);
        correct_pid = pid_diff == gof_.pid_diff[gof_idx][0];
      }

      if (same_spatial_layer && correct_pid) {
        ref_config.lst_fb_idx[sl_idx] = buf_idx;
        ref_config.reference_last[sl_idx] = 1;
      } else {
        // This reference doesn't match with one specified by GOF. This can
        // only happen if spatial layer is enabled dynamically without key
        // frame. Spatial prediction is supposed to be enabled in this case.
        RTC_DCHECK(is_inter_layer_pred_allowed &&
                   sl_idx > first_active_spatial_layer_id);
      }
    }

    if (is_inter_layer_pred_allowed && sl_idx > first_active_spatial_layer_id) {
      // Set up spatial reference.
      RTC_DCHECK(last_updated_buf_idx);
      ref_config.gld_fb_idx[sl_idx] = *last_updated_buf_idx;
      ref_config.reference_golden[sl_idx] = 1;
    } else {
      RTC_DCHECK(ref_config.reference_last[sl_idx] != 0 ||
                 sl_idx == first_active_spatial_layer_id ||
                 inter_layer_pred_ == InterLayerPredMode::kOff);
    }

    last_updated_buf_idx.reset();

    if (gof_.temporal_idx[gof_idx] < num_temporal_layers_ - 1 ||
        num_temporal_layers_ == 1) {
      last_updated_buf_idx = sl_idx * num_temporal_refs + kUpdBufIdx[gof_idx];

      // Ensure last frame buffer is not used for temporal prediction (it is
      // reserved for spatial reference).
      RTC_DCHECK_LT(*last_updated_buf_idx, kNumVp9Buffers - 1);
    } else if (is_inter_layer_pred_allowed) {
      last_updated_buf_idx = kNumVp9Buffers - 1;
    }

    if (last_updated_buf_idx) {
      ref_config.update_buffer_slot[sl_idx] = 1 << *last_updated_buf_idx;
    }
  }

  return ref_config;
}

int VP9EncoderImpl::GetEncodedLayerFrame(const vpx_codec_cx_pkt* pkt) {
  RTC_DCHECK_EQ(pkt->kind, VPX_CODEC_CX_FRAME_PKT);

  if (pkt->data.frame.sz == 0) {
    // Ignore dropped frame.
    return WEBRTC_VIDEO_CODEC_OK;
  }

  vpx_svc_layer_id_t layer_id = {0};
  vpx_codec_control(encoder_, VP9E_GET_SVC_LAYER_ID, &layer_id);

  if (layer_buffering_) {
    // Deliver buffered low spatial layer frame.
    const bool end_of_picture = false;
    DeliverBufferedFrame(end_of_picture);
  }

  // TODO(nisse): Introduce some buffer cache or buffer pool, to reduce
  // allocations and/or copy operations.
  encoded_image_.SetEncodedData(EncodedImageBuffer::Create(
      static_cast<const uint8_t*>(pkt->data.frame.buf), pkt->data.frame.sz));

  const bool is_key_frame =
      (pkt->data.frame.flags & VPX_FRAME_IS_KEY) ? true : false;
  // Ensure encoder issued key frame on request.
  RTC_DCHECK(is_key_frame || !force_key_frame_);

  // Check if encoded frame is a key frame.
  encoded_image_._frameType = VideoFrameType::kVideoFrameDelta;
  if (is_key_frame) {
    encoded_image_._frameType = VideoFrameType::kVideoFrameKey;
    force_key_frame_ = false;
  }

  codec_specific_ = {};
  absl::optional<int> spatial_index;
  PopulateCodecSpecific(&codec_specific_, &spatial_index, *pkt,
                        input_image_->timestamp());
  encoded_image_.SetSpatialIndex(spatial_index);

  UpdateReferenceBuffers(*pkt, pics_since_key_);

  TRACE_COUNTER1("webrtc", "EncodedFrameSize", encoded_image_.size());
  encoded_image_.SetTimestamp(input_image_->timestamp());
  encoded_image_._encodedHeight =
      pkt->data.frame.height[layer_id.spatial_layer_id];
  encoded_image_._encodedWidth =
      pkt->data.frame.width[layer_id.spatial_layer_id];
  int qp = -1;
  vpx_codec_control(encoder_, VP8E_GET_LAST_QUANTIZER, &qp);
  encoded_image_.qp_ = qp;

  if (!layer_buffering_) {
    const bool end_of_picture = encoded_image_.SpatialIndex().value_or(0) + 1 ==
                                num_active_spatial_layers_;
    DeliverBufferedFrame(end_of_picture);
  }

  return WEBRTC_VIDEO_CODEC_OK;
}

void VP9EncoderImpl::DeliverBufferedFrame(bool end_of_picture) {
  if (encoded_image_.size() > 0) {
    if (num_spatial_layers_ > 1) {
      // Restore frame dropping settings, as dropping may be temporary forbidden
      // due to dynamically enabled layers.
      for (size_t i = 0; i < num_spatial_layers_; ++i) {
        svc_drop_frame_.framedrop_thresh[i] = config_->rc_dropframe_thresh;
      }
    }

    codec_specific_.end_of_picture = end_of_picture;

    encoded_complete_callback_->OnEncodedImage(encoded_image_,
                                               &codec_specific_);

    if (codec_.mode == VideoCodecMode::kScreensharing) {
      const uint8_t spatial_idx = encoded_image_.SpatialIndex().value_or(0);
      const uint32_t frame_timestamp_ms =
          1000 * encoded_image_.Timestamp() / kVideoPayloadTypeFrequency;
      framerate_controller_[spatial_idx].AddFrame(frame_timestamp_ms);

      const size_t steady_state_size = SteadyStateSize(
          spatial_idx, codec_specific_.codecSpecific.VP9.temporal_idx);

      // Only frames on spatial layers, which may be limited in a steady state
      // are considered for steady state detection.
      if (framerate_controller_[spatial_idx].GetTargetRate() >
          variable_framerate_experiment_.framerate_limit + 1e-9) {
        if (encoded_image_.qp_ <=
                variable_framerate_experiment_.steady_state_qp &&
            encoded_image_.size() <= steady_state_size) {
          ++num_steady_state_frames_;
        } else {
          num_steady_state_frames_ = 0;
        }
      }
    }
    encoded_image_.set_size(0);
  }
}

int VP9EncoderImpl::RegisterEncodeCompleteCallback(
    EncodedImageCallback* callback) {
  encoded_complete_callback_ = callback;
  return WEBRTC_VIDEO_CODEC_OK;
}

VideoEncoder::EncoderInfo VP9EncoderImpl::GetEncoderInfo() const {
  EncoderInfo info;
  info.supports_native_handle = false;
  info.implementation_name = "libvpx";
  if (quality_scaler_experiment_.enabled && inited_ &&
      codec_.VP9().automaticResizeOn) {
    info.scaling_settings = VideoEncoder::ScalingSettings(
        quality_scaler_experiment_.low_qp, quality_scaler_experiment_.high_qp);
  } else {
    info.scaling_settings = VideoEncoder::ScalingSettings::kOff;
  }
  info.has_trusted_rate_controller = trusted_rate_controller_;
  info.is_hardware_accelerated = false;
  info.has_internal_source = false;
  if (inited_) {
    // Find the max configured fps of any active spatial layer.
    float max_fps = 0.0;
    for (size_t si = 0; si < num_spatial_layers_; ++si) {
      if (codec_.spatialLayers[si].active &&
          codec_.spatialLayers[si].maxFramerate > max_fps) {
        max_fps = codec_.spatialLayers[si].maxFramerate;
      }
    }

    for (size_t si = 0; si < num_spatial_layers_; ++si) {
      info.fps_allocation[si].clear();
      if (!codec_.spatialLayers[si].active) {
        continue;
      }

      // This spatial layer may already use a fraction of the total frame rate.
      const float sl_fps_fraction =
          codec_.spatialLayers[si].maxFramerate / max_fps;
      for (size_t ti = 0; ti < num_temporal_layers_; ++ti) {
        const uint32_t decimator =
            num_temporal_layers_ <= 1 ? 1 : config_->ts_rate_decimator[ti];
        RTC_DCHECK_GT(decimator, 0);
        info.fps_allocation[si].push_back(
            rtc::saturated_cast<uint8_t>(EncoderInfo::kMaxFramerateFraction *
                                         (sl_fps_fraction / decimator)));
      }
    }
    if (profile_ == VP9Profile::kProfile0) {
      info.preferred_pixel_formats = {VideoFrameBuffer::Type::kI420,
                                      VideoFrameBuffer::Type::kNV12};
    }
  }
  return info;
}

size_t VP9EncoderImpl::SteadyStateSize(int sid, int tid) {
  const size_t bitrate_bps = current_bitrate_allocation_.GetBitrate(
      sid, tid == kNoTemporalIdx ? 0 : tid);
  const float fps = (codec_.mode == VideoCodecMode::kScreensharing)
                        ? std::min(static_cast<float>(codec_.maxFramerate),
                                   framerate_controller_[sid].GetTargetRate())
                        : codec_.maxFramerate;
  return static_cast<size_t>(
      bitrate_bps / (8 * fps) *
          (100 -
           variable_framerate_experiment_.steady_state_undershoot_percentage) /
          100 +
      0.5);
}

// static
VP9EncoderImpl::VariableFramerateExperiment
VP9EncoderImpl::ParseVariableFramerateConfig(
    const WebRtcKeyValueConfig& trials) {
  FieldTrialFlag enabled = FieldTrialFlag("Enabled");
  FieldTrialParameter<double> framerate_limit("min_fps", 5.0);
  FieldTrialParameter<int> qp("min_qp", 32);
  FieldTrialParameter<int> undershoot_percentage("undershoot", 30);
  FieldTrialParameter<int> frames_before_steady_state(
      "frames_before_steady_state", 5);
  ParseFieldTrial({&enabled, &framerate_limit, &qp, &undershoot_percentage,
                   &frames_before_steady_state},
                  trials.Lookup("WebRTC-VP9VariableFramerateScreenshare"));
  VariableFramerateExperiment config;
  config.enabled = enabled.Get();
  config.framerate_limit = framerate_limit.Get();
  config.steady_state_qp = qp.Get();
  config.steady_state_undershoot_percentage = undershoot_percentage.Get();
  config.frames_before_steady_state = frames_before_steady_state.Get();

  return config;
}

// static
VP9EncoderImpl::QualityScalerExperiment
VP9EncoderImpl::ParseQualityScalerConfig(const WebRtcKeyValueConfig& trials) {
  FieldTrialFlag disabled = FieldTrialFlag("Disabled");
  FieldTrialParameter<int> low_qp("low_qp", kLowVp9QpThreshold);
  FieldTrialParameter<int> high_qp("hihg_qp", kHighVp9QpThreshold);
  ParseFieldTrial({&disabled, &low_qp, &high_qp},
                  trials.Lookup("WebRTC-VP9QualityScaler"));
  QualityScalerExperiment config;
  config.enabled = !disabled.Get();
  RTC_LOG(LS_INFO) << "Webrtc quality scaler for vp9 is "
                   << (config.enabled ? "enabled." : "disabled");
  config.low_qp = low_qp.Get();
  config.high_qp = high_qp.Get();

  return config;
}

// static
VP9EncoderImpl::SpeedSettings VP9EncoderImpl::ParsePerLayerSpeed(
    const WebRtcKeyValueConfig& trials) {
  FieldTrialFlag enabled("enabled");
  FieldTrialParameter<int> speeds[kMaxSpatialLayers]{
      {"s0", -1}, {"s1", -1}, {"s2", -1}, {"s3", -1}, {"s4", -1}};
  ParseFieldTrial(
      {&enabled, &speeds[0], &speeds[1], &speeds[2], &speeds[3], &speeds[4]},
      trials.Lookup("WebRTC-VP9-PerLayerSpeed"));
  return SpeedSettings{enabled.Get(),
                       {speeds[0].Get(), speeds[1].Get(), speeds[2].Get(),
                        speeds[3].Get(), speeds[4].Get()}};
}

void VP9EncoderImpl::MaybeRewrapRawWithFormat(const vpx_img_fmt fmt) {
  if (!raw_) {
    raw_ = vpx_img_wrap(nullptr, fmt, codec_.width, codec_.height, 1, nullptr);
  } else if (raw_->fmt != fmt) {
    RTC_LOG(INFO) << "Switching VP9 encoder pixel format to "
                  << (fmt == VPX_IMG_FMT_NV12 ? "NV12" : "I420");
    vpx_img_free(raw_);
    raw_ = vpx_img_wrap(nullptr, fmt, codec_.width, codec_.height, 1, nullptr);
  }
  // else no-op since the image is already in the right format.
}

VP9DecoderImpl::VP9DecoderImpl() : VP9DecoderImpl(FieldTrialBasedConfig()) {}
VP9DecoderImpl::VP9DecoderImpl(const WebRtcKeyValueConfig& trials)
    : decode_complete_callback_(nullptr),
      inited_(false),
      decoder_(nullptr),
      key_frame_required_(true),
      preferred_output_format_(
          absl::StartsWith(trials.Lookup("WebRTC-NV12Decode"), "Enabled")
              ? VideoFrameBuffer::Type::kNV12
              : VideoFrameBuffer::Type::kI420) {}

VP9DecoderImpl::~VP9DecoderImpl() {
  inited_ = true;  // in order to do the actual release
  Release();
  int num_buffers_in_use = libvpx_buffer_pool_.GetNumBuffersInUse();
  if (num_buffers_in_use > 0) {
    // The frame buffers are reference counted and frames are exposed after
    // decoding. There may be valid usage cases where previous frames are still
    // referenced after ~VP9DecoderImpl that is not a leak.
    RTC_LOG(LS_INFO) << num_buffers_in_use
                     << " Vp9FrameBuffers are still "
                        "referenced during ~VP9DecoderImpl.";
  }
}

int VP9DecoderImpl::InitDecode(const VideoCodec* inst, int number_of_cores) {
  int ret_val = Release();
  if (ret_val < 0) {
    return ret_val;
  }

  if (decoder_ == nullptr) {
    decoder_ = new vpx_codec_ctx_t;
  }
  vpx_codec_dec_cfg_t cfg;
  memset(&cfg, 0, sizeof(cfg));

#ifdef FUZZING_BUILD_MODE_UNSAFE_FOR_PRODUCTION
  // We focus on webrtc fuzzing here, not libvpx itself. Use single thread for
  // fuzzing, because:
  //  - libvpx's VP9 single thread decoder is more fuzzer friendly. It detects
  //    errors earlier than the multi-threads version.
  //  - Make peak CPU usage under control (not depending on input)
  cfg.threads = 1;
#else
  if (!inst) {
    // No config provided - don't know resolution to decode yet.
    // Set thread count to one in the meantime.
    cfg.threads = 1;
  } else {
    // We want to use multithreading when decoding high resolution videos. But
    // not too many in order to avoid overhead when many stream are decoded
    // concurrently.
    // Set 2 thread as target for 1280x720 pixel count, and then scale up
    // linearly from there - but cap at physical core count.
    // For common resolutions this results in:
    // 1 for 360p
    // 2 for 720p
    // 4 for 1080p
    // 8 for 1440p
    // 18 for 4K
    int num_threads =
        std::max(1, 2 * (inst->width * inst->height) / (1280 * 720));
    cfg.threads = std::min(number_of_cores, num_threads);
    current_codec_ = *inst;
  }
#endif

  num_cores_ = number_of_cores;

  vpx_codec_flags_t flags = 0;
  if (vpx_codec_dec_init(decoder_, vpx_codec_vp9_dx(), &cfg, flags)) {
    return WEBRTC_VIDEO_CODEC_MEMORY;
  }

  if (!libvpx_buffer_pool_.InitializeVpxUsePool(decoder_)) {
    return WEBRTC_VIDEO_CODEC_MEMORY;
  }

  inited_ = true;
  // Always start with a complete key frame.
  key_frame_required_ = true;
  if (inst && inst->buffer_pool_size) {
    if (!libvpx_buffer_pool_.Resize(*inst->buffer_pool_size) ||
        !output_buffer_pool_.Resize(*inst->buffer_pool_size)) {
      return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
    }
  }

  vpx_codec_err_t status =
      vpx_codec_control(decoder_, VP9D_SET_LOOP_FILTER_OPT, 1);
  if (status != VPX_CODEC_OK) {
    RTC_LOG(LS_ERROR) << "Failed to enable VP9D_SET_LOOP_FILTER_OPT. "
                      << vpx_codec_error(decoder_);
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }

  return WEBRTC_VIDEO_CODEC_OK;
}

int VP9DecoderImpl::Decode(const EncodedImage& input_image,
                           bool missing_frames,
                           int64_t /*render_time_ms*/) {
  if (!inited_) {
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }
  if (decode_complete_callback_ == nullptr) {
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }

  if (input_image._frameType == VideoFrameType::kVideoFrameKey) {
    absl::optional<vp9::FrameInfo> frame_info =
        vp9::ParseIntraFrameInfo(input_image.data(), input_image.size());
    if (frame_info) {
      if (frame_info->frame_width != current_codec_.width ||
          frame_info->frame_height != current_codec_.height) {
        // Resolution has changed, tear down and re-init a new decoder in
        // order to get correct sizing.
        Release();
        current_codec_.width = frame_info->frame_width;
        current_codec_.height = frame_info->frame_height;
        int reinit_status = InitDecode(&current_codec_, num_cores_);
        if (reinit_status != WEBRTC_VIDEO_CODEC_OK) {
          RTC_LOG(LS_WARNING) << "Failed to re-init decoder.";
          return reinit_status;
        }
      }
    } else {
      RTC_LOG(LS_WARNING) << "Failed to parse VP9 header from key-frame.";
    }
  }

  // Always start with a complete key frame.
  if (key_frame_required_) {
    if (input_image._frameType != VideoFrameType::kVideoFrameKey)
      return WEBRTC_VIDEO_CODEC_ERROR;
    key_frame_required_ = false;
  }
  vpx_codec_iter_t iter = nullptr;
  vpx_image_t* img;
  const uint8_t* buffer = input_image.data();
  if (input_image.size() == 0) {
    buffer = nullptr;  // Triggers full frame concealment.
  }
  // During decode libvpx may get and release buffers from
  // |libvpx_buffer_pool_|. In practice libvpx keeps a few (~3-4) buffers alive
  // at a time.
  if (vpx_codec_decode(decoder_, buffer,
                       static_cast<unsigned int>(input_image.size()), 0,
                       VPX_DL_REALTIME)) {
    return WEBRTC_VIDEO_CODEC_ERROR;
  }
  // |img->fb_priv| contains the image data, a reference counted Vp9FrameBuffer.
  // It may be released by libvpx during future vpx_codec_decode or
  // vpx_codec_destroy calls.
  img = vpx_codec_get_frame(decoder_, &iter);
  int qp;
  vpx_codec_err_t vpx_ret =
      vpx_codec_control(decoder_, VPXD_GET_LAST_QUANTIZER, &qp);
  RTC_DCHECK_EQ(vpx_ret, VPX_CODEC_OK);
  int ret =
      ReturnFrame(img, input_image.Timestamp(), qp, input_image.ColorSpace());
  if (ret != 0) {
    return ret;
  }
  return WEBRTC_VIDEO_CODEC_OK;
}

int VP9DecoderImpl::ReturnFrame(
    const vpx_image_t* img,
    uint32_t timestamp,
    int qp,
    const webrtc::ColorSpace* explicit_color_space) {
  if (img == nullptr) {
    // Decoder OK and nullptr image => No show frame.
    return WEBRTC_VIDEO_CODEC_NO_OUTPUT;
  }

  // This buffer contains all of |img|'s image data, a reference counted
  // Vp9FrameBuffer. (libvpx is done with the buffers after a few
  // vpx_codec_decode calls or vpx_codec_destroy).
  Vp9FrameBufferPool::Vp9FrameBuffer* img_buffer =
      static_cast<Vp9FrameBufferPool::Vp9FrameBuffer*>(img->fb_priv);

  // The buffer can be used directly by the VideoFrame (without copy) by
  // using a Wrapped*Buffer.
  rtc::scoped_refptr<VideoFrameBuffer> img_wrapped_buffer;
  switch (img->bit_depth) {
    case 8:
      if (img->fmt == VPX_IMG_FMT_I420) {
        if (preferred_output_format_ == VideoFrameBuffer::Type::kNV12) {
          rtc::scoped_refptr<NV12Buffer> nv12_buffer =
              output_buffer_pool_.CreateNV12Buffer(img->d_w, img->d_h);
          if (!nv12_buffer.get()) {
            // Buffer pool is full.
            return WEBRTC_VIDEO_CODEC_NO_OUTPUT;
          }
          img_wrapped_buffer = nv12_buffer;
          libyuv::I420ToNV12(img->planes[VPX_PLANE_Y], img->stride[VPX_PLANE_Y],
                             img->planes[VPX_PLANE_U], img->stride[VPX_PLANE_U],
                             img->planes[VPX_PLANE_V], img->stride[VPX_PLANE_V],
                             nv12_buffer->MutableDataY(),
                             nv12_buffer->StrideY(),
                             nv12_buffer->MutableDataUV(),
                             nv12_buffer->StrideUV(), img->d_w, img->d_h);
          // No holding onto img_buffer as it's no longer needed and can be
          // reused.
        } else {
          img_wrapped_buffer = WrapI420Buffer(
              img->d_w, img->d_h, img->planes[VPX_PLANE_Y],
              img->stride[VPX_PLANE_Y], img->planes[VPX_PLANE_U],
              img->stride[VPX_PLANE_U], img->planes[VPX_PLANE_V],
              img->stride[VPX_PLANE_V],
              // WrappedI420Buffer's mechanism for allowing the release of its
              // frame buffer is through a callback function. This is where we
              // should release |img_buffer|.
              rtc::KeepRefUntilDone(img_buffer));
        }
      } else if (img->fmt == VPX_IMG_FMT_I444) {
        img_wrapped_buffer = WrapI444Buffer(
            img->d_w, img->d_h, img->planes[VPX_PLANE_Y],
            img->stride[VPX_PLANE_Y], img->planes[VPX_PLANE_U],
            img->stride[VPX_PLANE_U], img->planes[VPX_PLANE_V],
            img->stride[VPX_PLANE_V],
            // WrappedI444Buffer's mechanism for allowing the release of its
            // frame buffer is through a callback function. This is where we
            // should release |img_buffer|.
            rtc::KeepRefUntilDone(img_buffer));
      } else {
        RTC_LOG(LS_ERROR)
            << "Unsupported pixel format produced by the decoder: "
            << static_cast<int>(img->fmt);
        return WEBRTC_VIDEO_CODEC_NO_OUTPUT;
      }
      break;
    case 10:
      img_wrapped_buffer = WrapI010Buffer(
          img->d_w, img->d_h,
          reinterpret_cast<const uint16_t*>(img->planes[VPX_PLANE_Y]),
          img->stride[VPX_PLANE_Y] / 2,
          reinterpret_cast<const uint16_t*>(img->planes[VPX_PLANE_U]),
          img->stride[VPX_PLANE_U] / 2,
          reinterpret_cast<const uint16_t*>(img->planes[VPX_PLANE_V]),
          img->stride[VPX_PLANE_V] / 2, rtc::KeepRefUntilDone(img_buffer));
      break;
    default:
      RTC_LOG(LS_ERROR) << "Unsupported bit depth produced by the decoder: "
                        << img->bit_depth;
      return WEBRTC_VIDEO_CODEC_NO_OUTPUT;
  }

  auto builder = VideoFrame::Builder()
                     .set_video_frame_buffer(img_wrapped_buffer)
                     .set_timestamp_rtp(timestamp);
  if (explicit_color_space) {
    builder.set_color_space(*explicit_color_space);
  } else {
    builder.set_color_space(
        ExtractVP9ColorSpace(img->cs, img->range, img->bit_depth));
  }
  VideoFrame decoded_image = builder.build();

  decode_complete_callback_->Decoded(decoded_image, absl::nullopt, qp);
  return WEBRTC_VIDEO_CODEC_OK;
}

int VP9DecoderImpl::RegisterDecodeCompleteCallback(
    DecodedImageCallback* callback) {
  decode_complete_callback_ = callback;
  return WEBRTC_VIDEO_CODEC_OK;
}

int VP9DecoderImpl::Release() {
  int ret_val = WEBRTC_VIDEO_CODEC_OK;

  if (decoder_ != nullptr) {
    if (inited_) {
      // When a codec is destroyed libvpx will release any buffers of
      // |libvpx_buffer_pool_| it is currently using.
      if (vpx_codec_destroy(decoder_)) {
        ret_val = WEBRTC_VIDEO_CODEC_MEMORY;
      }
    }
    delete decoder_;
    decoder_ = nullptr;
  }
  // Releases buffers from the pool. Any buffers not in use are deleted. Buffers
  // still referenced externally are deleted once fully released, not returning
  // to the pool.
  libvpx_buffer_pool_.ClearPool();
  output_buffer_pool_.Release();
  inited_ = false;
  return ret_val;
}

const char* VP9DecoderImpl::ImplementationName() const {
  return "libvpx";
}

}  // namespace webrtc

#endif  // RTC_ENABLE_VP9

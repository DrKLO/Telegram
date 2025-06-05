/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/vp8/libvpx_vp8_encoder.h"

#include <string.h>

#include <algorithm>
#include <cstdint>
#include <iterator>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "api/scoped_refptr.h"
#include "api/video/video_content_type.h"
#include "api/video/video_frame_buffer.h"
#include "api/video/video_timing.h"
#include "api/video_codecs/scalability_mode.h"
#include "api/video_codecs/vp8_temporal_layers.h"
#include "api/video_codecs/vp8_temporal_layers_factory.h"
#include "modules/video_coding/codecs/interface/common_constants.h"
#include "modules/video_coding/codecs/vp8/include/vp8.h"
#include "modules/video_coding/codecs/vp8/vp8_scalability.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "modules/video_coding/svc/scalability_mode_util.h"
#include "modules/video_coding/utility/simulcast_rate_allocator.h"
#include "modules/video_coding/utility/simulcast_utility.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/experiments/field_trial_units.h"
#include "rtc_base/logging.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/field_trial.h"
#include "third_party/libyuv/include/libyuv/scale.h"
#include <libvpx/vp8cx.h>

#if (defined(WEBRTC_ARCH_ARM) || defined(WEBRTC_ARCH_ARM64)) && \
    (defined(WEBRTC_ANDROID) || defined(WEBRTC_IOS))
#define MOBILE_ARM
#endif

namespace webrtc {
namespace {
#if defined(WEBRTC_IOS)
constexpr char kVP8IosMaxNumberOfThreadFieldTrial[] =
    "WebRTC-VP8IosMaxNumberOfThread";
constexpr char kVP8IosMaxNumberOfThreadFieldTrialParameter[] = "max_thread";
#endif

constexpr char kVp8ForcePartitionResilience[] =
    "WebRTC-VP8-ForcePartitionResilience";

// QP is obtained from VP8-bitstream for HW, so the QP corresponds to the
// bitstream range of [0, 127] and not the user-level range of [0,63].
constexpr int kLowVp8QpThreshold = 29;
constexpr int kHighVp8QpThreshold = 95;

constexpr int kTokenPartitions = VP8_ONE_TOKENPARTITION;
constexpr uint32_t kVp832ByteAlign = 32u;

constexpr int kRtpTicksPerSecond = 90000;
constexpr int kRtpTicksPerMs = kRtpTicksPerSecond / 1000;

// If internal frame dropping is enabled, force the encoder to output a frame
// on an encode request after this timeout even if this causes some
// bitrate overshoot compared to the nominal target. Otherwise we risk the
// receivers incorrectly identifying the gap as a fault and they may needlessly
// send keyframe requests to recover.
constexpr TimeDelta kDefaultMaxFrameDropInterval = TimeDelta::Seconds(2);

// VP8 denoiser states.
enum denoiserState : uint32_t {
  kDenoiserOff,
  kDenoiserOnYOnly,
  kDenoiserOnYUV,
  kDenoiserOnYUVAggressive,
  // Adaptive mode defaults to kDenoiserOnYUV on key frame, but may switch
  // to kDenoiserOnYUVAggressive based on a computed noise metric.
  kDenoiserOnAdaptive
};

// Greatest common divisior
int GCD(int a, int b) {
  int c = a % b;
  while (c != 0) {
    a = b;
    b = c;
    c = a % b;
  }
  return b;
}

static_assert(Vp8EncoderConfig::TemporalLayerConfig::kMaxPeriodicity ==
                  VPX_TS_MAX_PERIODICITY,
              "Vp8EncoderConfig::kMaxPeriodicity must be kept in sync with the "
              "constant in libvpx.");
static_assert(Vp8EncoderConfig::TemporalLayerConfig::kMaxLayers ==
                  VPX_TS_MAX_LAYERS,
              "Vp8EncoderConfig::kMaxLayers must be kept in sync with the "
              "constant in libvpx.");

// Allow a newer value to override a current value only if the new value
// is set.
template <typename T>
bool MaybeSetNewValue(const absl::optional<T>& new_value,
                      absl::optional<T>* base_value) {
  if (new_value.has_value() && new_value != *base_value) {
    *base_value = new_value;
    return true;
  } else {
    return false;
  }
}

// Adds configuration from `new_config` to `base_config`. Both configs consist
// of optionals, and only optionals which are set in `new_config` can have
// an effect. (That is, set values in `base_config` cannot be unset.)
// Returns `true` iff any changes were made to `base_config`.
bool MaybeExtendVp8EncoderConfig(const Vp8EncoderConfig& new_config,
                                 Vp8EncoderConfig* base_config) {
  bool changes_made = false;
  changes_made |= MaybeSetNewValue(new_config.temporal_layer_config,
                                   &base_config->temporal_layer_config);
  changes_made |= MaybeSetNewValue(new_config.rc_target_bitrate,
                                   &base_config->rc_target_bitrate);
  changes_made |= MaybeSetNewValue(new_config.rc_max_quantizer,
                                   &base_config->rc_max_quantizer);
  changes_made |= MaybeSetNewValue(new_config.g_error_resilient,
                                   &base_config->g_error_resilient);
  return changes_made;
}

void ApplyVp8EncoderConfigToVpxConfig(const Vp8EncoderConfig& encoder_config,
                                      vpx_codec_enc_cfg_t* vpx_config) {
  if (encoder_config.temporal_layer_config.has_value()) {
    const Vp8EncoderConfig::TemporalLayerConfig& ts_config =
        encoder_config.temporal_layer_config.value();
    vpx_config->ts_number_layers = ts_config.ts_number_layers;
    std::copy(ts_config.ts_target_bitrate.begin(),
              ts_config.ts_target_bitrate.end(),
              std::begin(vpx_config->ts_target_bitrate));
    std::copy(ts_config.ts_rate_decimator.begin(),
              ts_config.ts_rate_decimator.end(),
              std::begin(vpx_config->ts_rate_decimator));
    vpx_config->ts_periodicity = ts_config.ts_periodicity;
    std::copy(ts_config.ts_layer_id.begin(), ts_config.ts_layer_id.end(),
              std::begin(vpx_config->ts_layer_id));
  } else {
    vpx_config->ts_number_layers = 1;
    vpx_config->ts_rate_decimator[0] = 1;
    vpx_config->ts_periodicity = 1;
    vpx_config->ts_layer_id[0] = 0;
  }

  if (encoder_config.rc_target_bitrate.has_value()) {
    vpx_config->rc_target_bitrate = encoder_config.rc_target_bitrate.value();
  }

  if (encoder_config.rc_max_quantizer.has_value()) {
    vpx_config->rc_max_quantizer = encoder_config.rc_max_quantizer.value();
  }

  if (encoder_config.g_error_resilient.has_value()) {
    vpx_config->g_error_resilient = encoder_config.g_error_resilient.value();
  }
}

bool IsCompatibleVideoFrameBufferType(VideoFrameBuffer::Type left,
                                      VideoFrameBuffer::Type right) {
  if (left == VideoFrameBuffer::Type::kI420 ||
      left == VideoFrameBuffer::Type::kI420A) {
    // LibvpxVp8Encoder does not care about the alpha channel, I420A and I420
    // are considered compatible.
    return right == VideoFrameBuffer::Type::kI420 ||
           right == VideoFrameBuffer::Type::kI420A;
  }
  return left == right;
}

void SetRawImagePlanes(vpx_image_t* raw_image, VideoFrameBuffer* buffer) {
  switch (buffer->type()) {
    case VideoFrameBuffer::Type::kI420:
    case VideoFrameBuffer::Type::kI420A: {
      const I420BufferInterface* i420_buffer = buffer->GetI420();
      RTC_DCHECK(i420_buffer);
      raw_image->planes[VPX_PLANE_Y] =
          const_cast<uint8_t*>(i420_buffer->DataY());
      raw_image->planes[VPX_PLANE_U] =
          const_cast<uint8_t*>(i420_buffer->DataU());
      raw_image->planes[VPX_PLANE_V] =
          const_cast<uint8_t*>(i420_buffer->DataV());
      raw_image->stride[VPX_PLANE_Y] = i420_buffer->StrideY();
      raw_image->stride[VPX_PLANE_U] = i420_buffer->StrideU();
      raw_image->stride[VPX_PLANE_V] = i420_buffer->StrideV();
      break;
    }
    case VideoFrameBuffer::Type::kNV12: {
      const NV12BufferInterface* nv12_buffer = buffer->GetNV12();
      RTC_DCHECK(nv12_buffer);
      raw_image->planes[VPX_PLANE_Y] =
          const_cast<uint8_t*>(nv12_buffer->DataY());
      raw_image->planes[VPX_PLANE_U] =
          const_cast<uint8_t*>(nv12_buffer->DataUV());
      raw_image->planes[VPX_PLANE_V] = raw_image->planes[VPX_PLANE_U] + 1;
      raw_image->stride[VPX_PLANE_Y] = nv12_buffer->StrideY();
      raw_image->stride[VPX_PLANE_U] = nv12_buffer->StrideUV();
      raw_image->stride[VPX_PLANE_V] = nv12_buffer->StrideUV();
      break;
    }
    default:
      RTC_DCHECK_NOTREACHED();
  }
}

// Helper class used to temporarily change the frame drop threshold for an
// encoder. Returns the setting to the previous value when upon destruction.
class FrameDropConfigOverride {
 public:
  FrameDropConfigOverride(LibvpxInterface* libvpx,
                          vpx_codec_ctx_t* encoder,
                          vpx_codec_enc_cfg_t* config,
                          uint32_t temporary_frame_drop_threshold)
      : libvpx_(libvpx),
        encoder_(encoder),
        config_(config),
        original_frame_drop_threshold_(config->rc_dropframe_thresh) {
    config_->rc_dropframe_thresh = temporary_frame_drop_threshold;
    libvpx_->codec_enc_config_set(encoder_, config_);
  }
  ~FrameDropConfigOverride() {
    config_->rc_dropframe_thresh = original_frame_drop_threshold_;
    libvpx_->codec_enc_config_set(encoder_, config_);
  }

 private:
  LibvpxInterface* const libvpx_;
  vpx_codec_ctx_t* const encoder_;
  vpx_codec_enc_cfg_t* const config_;
  const uint32_t original_frame_drop_threshold_;
};

absl::optional<TimeDelta> ParseFrameDropInterval() {
  FieldTrialFlag disabled = FieldTrialFlag("Disabled");
  FieldTrialParameter<TimeDelta> interval("interval",
                                          kDefaultMaxFrameDropInterval);
  ParseFieldTrial({&disabled, &interval},
                  field_trial::FindFullName("WebRTC-VP8-MaxFrameInterval"));
  if (disabled.Get()) {
    // Kill switch set, don't use any max frame interval.
    return absl::nullopt;
  }
  return interval.Get();
}

}  // namespace

std::unique_ptr<VideoEncoder> VP8Encoder::Create() {
  return std::make_unique<LibvpxVp8Encoder>(LibvpxInterface::Create(),
                                            VP8Encoder::Settings());
}

std::unique_ptr<VideoEncoder> VP8Encoder::Create(
    VP8Encoder::Settings settings) {
  return std::make_unique<LibvpxVp8Encoder>(LibvpxInterface::Create(),
                                            std::move(settings));
}

vpx_enc_frame_flags_t LibvpxVp8Encoder::EncodeFlags(
    const Vp8FrameConfig& references) {
  RTC_DCHECK(!references.drop_frame);

  vpx_enc_frame_flags_t flags = 0;

  if ((references.last_buffer_flags &
       Vp8FrameConfig::BufferFlags::kReference) == 0)
    flags |= VP8_EFLAG_NO_REF_LAST;
  if ((references.last_buffer_flags & Vp8FrameConfig::BufferFlags::kUpdate) ==
      0)
    flags |= VP8_EFLAG_NO_UPD_LAST;
  if ((references.golden_buffer_flags &
       Vp8FrameConfig::BufferFlags::kReference) == 0)
    flags |= VP8_EFLAG_NO_REF_GF;
  if ((references.golden_buffer_flags & Vp8FrameConfig::BufferFlags::kUpdate) ==
      0)
    flags |= VP8_EFLAG_NO_UPD_GF;
  if ((references.arf_buffer_flags & Vp8FrameConfig::BufferFlags::kReference) ==
      0)
    flags |= VP8_EFLAG_NO_REF_ARF;
  if ((references.arf_buffer_flags & Vp8FrameConfig::BufferFlags::kUpdate) == 0)
    flags |= VP8_EFLAG_NO_UPD_ARF;
  if (references.freeze_entropy)
    flags |= VP8_EFLAG_NO_UPD_ENTROPY;

  return flags;
}

LibvpxVp8Encoder::LibvpxVp8Encoder(std::unique_ptr<LibvpxInterface> interface,
                                   VP8Encoder::Settings settings)
    : libvpx_(std::move(interface)),
      rate_control_settings_(RateControlSettings::ParseFromFieldTrials()),
      frame_buffer_controller_factory_(
          std::move(settings.frame_buffer_controller_factory)),
      resolution_bitrate_limits_(std::move(settings.resolution_bitrate_limits)),
      key_frame_request_(kMaxSimulcastStreams, false),
      last_encoder_output_time_(kMaxSimulcastStreams,
                                Timestamp::MinusInfinity()),
      variable_framerate_experiment_(ParseVariableFramerateConfig(
          "WebRTC-VP8VariableFramerateScreenshare")),
      framerate_controller_(variable_framerate_experiment_.framerate_limit),
      max_frame_drop_interval_(ParseFrameDropInterval()) {
  // TODO(eladalon/ilnik): These reservations might be wasting memory.
  // InitEncode() is resizing to the actual size, which might be smaller.
  raw_images_.reserve(kMaxSimulcastStreams);
  encoded_images_.reserve(kMaxSimulcastStreams);
  send_stream_.reserve(kMaxSimulcastStreams);
  cpu_speed_.assign(kMaxSimulcastStreams, cpu_speed_default_);
  encoders_.reserve(kMaxSimulcastStreams);
  vpx_configs_.reserve(kMaxSimulcastStreams);
  config_overrides_.reserve(kMaxSimulcastStreams);
  downsampling_factors_.reserve(kMaxSimulcastStreams);
}

LibvpxVp8Encoder::~LibvpxVp8Encoder() {
  Release();
}

int LibvpxVp8Encoder::Release() {
  int ret_val = WEBRTC_VIDEO_CODEC_OK;

  encoded_images_.clear();

  if (inited_) {
    for (auto it = encoders_.rbegin(); it != encoders_.rend(); ++it) {
      if (libvpx_->codec_destroy(&*it)) {
        ret_val = WEBRTC_VIDEO_CODEC_MEMORY;
      }
    }
  }
  encoders_.clear();

  vpx_configs_.clear();
  config_overrides_.clear();
  send_stream_.clear();
  cpu_speed_.clear();

  for (auto it = raw_images_.rbegin(); it != raw_images_.rend(); ++it) {
    libvpx_->img_free(&*it);
  }
  raw_images_.clear();

  frame_buffer_controller_.reset();
  inited_ = false;
  return ret_val;
}

void LibvpxVp8Encoder::SetRates(const RateControlParameters& parameters) {
  if (!inited_) {
    RTC_LOG(LS_WARNING) << "SetRates() while not initialize";
    return;
  }

  if (encoders_[0].err) {
    RTC_LOG(LS_WARNING) << "Encoder in error state.";
    return;
  }

  if (parameters.framerate_fps < 1.0) {
    RTC_LOG(LS_WARNING) << "Unsupported framerate (must be >= 1.0): "
                        << parameters.framerate_fps;
    return;
  }

  if (parameters.bitrate.get_sum_bps() == 0) {
    // Encoder paused, turn off all encoding.
    const int num_streams = static_cast<size_t>(encoders_.size());
    for (int i = 0; i < num_streams; ++i)
      SetStreamState(false, i);
    return;
  }

  codec_.maxFramerate = static_cast<uint32_t>(parameters.framerate_fps + 0.5);

  if (encoders_.size() > 1) {
    // If we have more than 1 stream, reduce the qp_max for the low resolution
    // stream if frame rate is not too low. The trade-off with lower qp_max is
    // possibly more dropped frames, so we only do this if the frame rate is
    // above some threshold (base temporal layer is down to 1/4 for 3 layers).
    // We may want to condition this on bitrate later.
    if (rate_control_settings_.Vp8BoostBaseLayerQuality() &&
        parameters.framerate_fps > 20.0) {
      vpx_configs_[encoders_.size() - 1].rc_max_quantizer = 45;
    } else {
      // Go back to default value set in InitEncode.
      vpx_configs_[encoders_.size() - 1].rc_max_quantizer = qp_max_;
    }
  }

  for (size_t i = 0; i < encoders_.size(); ++i) {
    const size_t stream_idx = encoders_.size() - 1 - i;

    unsigned int target_bitrate_kbps =
        parameters.bitrate.GetSpatialLayerSum(stream_idx) / 1000;

    bool send_stream = target_bitrate_kbps > 0;
    if (send_stream || encoders_.size() > 1)
      SetStreamState(send_stream, stream_idx);

    vpx_configs_[i].rc_target_bitrate = target_bitrate_kbps;
    if (send_stream) {
      frame_buffer_controller_->OnRatesUpdated(
          stream_idx, parameters.bitrate.GetTemporalLayerAllocation(stream_idx),
          static_cast<int>(parameters.framerate_fps + 0.5));
    }

    UpdateVpxConfiguration(stream_idx);

    vpx_codec_err_t err =
        libvpx_->codec_enc_config_set(&encoders_[i], &vpx_configs_[i]);
    if (err != VPX_CODEC_OK) {
      RTC_LOG(LS_WARNING) << "Error configuring codec, error code: " << err
                          << ", details: "
                          << libvpx_->codec_error_detail(&encoders_[i]);
    }
  }
}

void LibvpxVp8Encoder::OnPacketLossRateUpdate(float packet_loss_rate) {
  // TODO(bugs.webrtc.org/10431): Replace condition by DCHECK.
  if (frame_buffer_controller_) {
    frame_buffer_controller_->OnPacketLossRateUpdate(packet_loss_rate);
  }
}

void LibvpxVp8Encoder::OnRttUpdate(int64_t rtt_ms) {
  // TODO(bugs.webrtc.org/10431): Replace condition by DCHECK.
  if (frame_buffer_controller_) {
    frame_buffer_controller_->OnRttUpdate(rtt_ms);
  }
}

void LibvpxVp8Encoder::OnLossNotification(
    const LossNotification& loss_notification) {
  if (frame_buffer_controller_) {
    frame_buffer_controller_->OnLossNotification(loss_notification);
  }
}

void LibvpxVp8Encoder::SetStreamState(bool send_stream, int stream_idx) {
  if (send_stream && !send_stream_[stream_idx]) {
    // Need a key frame if we have not sent this stream before.
    key_frame_request_[stream_idx] = true;
  }
  send_stream_[stream_idx] = send_stream;
}

void LibvpxVp8Encoder::SetFecControllerOverride(
    FecControllerOverride* fec_controller_override) {
  // TODO(bugs.webrtc.org/10769): Update downstream and remove ability to
  // pass nullptr.
  // RTC_DCHECK(fec_controller_override);
  RTC_DCHECK(!fec_controller_override_);
  fec_controller_override_ = fec_controller_override;
}

// TODO(eladalon): s/inst/codec_settings/g.
int LibvpxVp8Encoder::InitEncode(const VideoCodec* inst,
                                 const VideoEncoder::Settings& settings) {
  if (inst == NULL) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  if (inst->maxFramerate < 1) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  // allow zero to represent an unspecified maxBitRate
  if (inst->maxBitrate > 0 && inst->startBitrate > inst->maxBitrate) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  if (inst->width < 1 || inst->height < 1) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  if (settings.number_of_cores < 1) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }

  if (absl::optional<ScalabilityMode> scalability_mode =
          inst->GetScalabilityMode();
      scalability_mode.has_value() &&
      !VP8SupportsScalabilityMode(*scalability_mode)) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }

  num_active_streams_ = 0;
  for (int i = 0; i < inst->numberOfSimulcastStreams; ++i) {
    if (inst->simulcastStream[i].active) {
      ++num_active_streams_;
    }
  }
  if (inst->numberOfSimulcastStreams == 0 && inst->active) {
    num_active_streams_ = 1;
  }

  if (inst->VP8().automaticResizeOn && num_active_streams_ > 1) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }

  // Use the previous pixel format to avoid extra image allocations.
  vpx_img_fmt_t pixel_format =
      raw_images_.empty() ? VPX_IMG_FMT_I420 : raw_images_[0].fmt;

  int retVal = Release();
  if (retVal < 0) {
    return retVal;
  }

  int number_of_streams = SimulcastUtility::NumberOfSimulcastStreams(*inst);
  if (number_of_streams > 1 &&
      !SimulcastUtility::ValidSimulcastParameters(*inst, number_of_streams)) {
    return WEBRTC_VIDEO_CODEC_ERR_SIMULCAST_PARAMETERS_NOT_SUPPORTED;
  }

  RTC_DCHECK(!frame_buffer_controller_);
  if (frame_buffer_controller_factory_) {
    frame_buffer_controller_ = frame_buffer_controller_factory_->Create(
        *inst, settings, fec_controller_override_);
  } else {
    Vp8TemporalLayersFactory factory;
    frame_buffer_controller_ =
        factory.Create(*inst, settings, fec_controller_override_);
  }
  RTC_DCHECK(frame_buffer_controller_);

  number_of_cores_ = settings.number_of_cores;
  timestamp_ = 0;
  codec_ = *inst;

  // Code expects simulcastStream resolutions to be correct, make sure they are
  // filled even when there are no simulcast layers.
  if (codec_.numberOfSimulcastStreams == 0) {
    codec_.simulcastStream[0].width = codec_.width;
    codec_.simulcastStream[0].height = codec_.height;
  }

  encoded_images_.resize(number_of_streams);
  encoders_.resize(number_of_streams);
  vpx_configs_.resize(number_of_streams);
  config_overrides_.resize(number_of_streams);
  downsampling_factors_.resize(number_of_streams);
  raw_images_.resize(number_of_streams);
  send_stream_.resize(number_of_streams);
  send_stream_[0] = true;  // For non-simulcast case.
  cpu_speed_.resize(number_of_streams);
  std::fill(key_frame_request_.begin(), key_frame_request_.end(), false);
  std::fill(last_encoder_output_time_.begin(), last_encoder_output_time_.end(),
            Timestamp::MinusInfinity());

  int idx = number_of_streams - 1;
  for (int i = 0; i < (number_of_streams - 1); ++i, --idx) {
    int gcd = GCD(inst->simulcastStream[idx].width,
                  inst->simulcastStream[idx - 1].width);
    downsampling_factors_[i].num = inst->simulcastStream[idx].width / gcd;
    downsampling_factors_[i].den = inst->simulcastStream[idx - 1].width / gcd;
    send_stream_[i] = false;
  }
  if (number_of_streams > 1) {
    send_stream_[number_of_streams - 1] = false;
    downsampling_factors_[number_of_streams - 1].num = 1;
    downsampling_factors_[number_of_streams - 1].den = 1;
  }

  // populate encoder configuration with default values
  if (libvpx_->codec_enc_config_default(vpx_codec_vp8_cx(), &vpx_configs_[0],
                                        0)) {
    return WEBRTC_VIDEO_CODEC_ERROR;
  }
  // setting the time base of the codec
  vpx_configs_[0].g_timebase.num = 1;
  vpx_configs_[0].g_timebase.den = kRtpTicksPerSecond;
  vpx_configs_[0].g_lag_in_frames = 0;  // 0- no frame lagging

  // Set the error resilience mode for temporal layers (but not simulcast).
  vpx_configs_[0].g_error_resilient =
      (SimulcastUtility::NumberOfTemporalLayers(*inst, 0) > 1)
          ? VPX_ERROR_RESILIENT_DEFAULT
          : 0;

  // Override the error resilience mode if this is not simulcast, but we are
  // using temporal layers.
  if (field_trial::IsEnabled(kVp8ForcePartitionResilience) &&
      (number_of_streams == 1) &&
      (SimulcastUtility::NumberOfTemporalLayers(*inst, 0) > 1)) {
    RTC_LOG(LS_INFO) << "Overriding g_error_resilient from "
                     << vpx_configs_[0].g_error_resilient << " to "
                     << VPX_ERROR_RESILIENT_PARTITIONS;
    vpx_configs_[0].g_error_resilient = VPX_ERROR_RESILIENT_PARTITIONS;
  }

  // rate control settings
  vpx_configs_[0].rc_dropframe_thresh = FrameDropThreshold(0);
  vpx_configs_[0].rc_end_usage = VPX_CBR;
  vpx_configs_[0].g_pass = VPX_RC_ONE_PASS;
  // Handle resizing outside of libvpx.
  vpx_configs_[0].rc_resize_allowed = 0;
  vpx_configs_[0].rc_min_quantizer =
      codec_.mode == VideoCodecMode::kScreensharing ? 12 : 2;
  if (inst->qpMax >= vpx_configs_[0].rc_min_quantizer) {
    qp_max_ = inst->qpMax;
  }
  if (rate_control_settings_.LibvpxVp8QpMax()) {
    qp_max_ = std::max(rate_control_settings_.LibvpxVp8QpMax().value(),
                       static_cast<int>(vpx_configs_[0].rc_min_quantizer));
  }
  vpx_configs_[0].rc_max_quantizer = qp_max_;
  vpx_configs_[0].rc_undershoot_pct = 100;
  vpx_configs_[0].rc_overshoot_pct = 15;
  vpx_configs_[0].rc_buf_initial_sz = 500;
  vpx_configs_[0].rc_buf_optimal_sz = 600;
  vpx_configs_[0].rc_buf_sz = 1000;

  // Set the maximum target size of any key-frame.
  rc_max_intra_target_ = MaxIntraTarget(vpx_configs_[0].rc_buf_optimal_sz);

  if (inst->VP8().keyFrameInterval > 0) {
    vpx_configs_[0].kf_mode = VPX_KF_AUTO;
    vpx_configs_[0].kf_max_dist = inst->VP8().keyFrameInterval;
  } else {
    vpx_configs_[0].kf_mode = VPX_KF_DISABLED;
  }

  // Allow the user to set the complexity for the base stream.
  switch (inst->GetVideoEncoderComplexity()) {
    case VideoCodecComplexity::kComplexityHigh:
      cpu_speed_[0] = -5;
      break;
    case VideoCodecComplexity::kComplexityHigher:
      cpu_speed_[0] = -4;
      break;
    case VideoCodecComplexity::kComplexityMax:
      cpu_speed_[0] = -3;
      break;
    default:
      cpu_speed_[0] = -6;
      break;
  }
  cpu_speed_default_ = cpu_speed_[0];
  // Set encoding complexity (cpu_speed) based on resolution and/or platform.
  cpu_speed_[0] = GetCpuSpeed(inst->width, inst->height);
  for (int i = 1; i < number_of_streams; ++i) {
    cpu_speed_[i] =
        GetCpuSpeed(inst->simulcastStream[number_of_streams - 1 - i].width,
                    inst->simulcastStream[number_of_streams - 1 - i].height);
  }
  vpx_configs_[0].g_w = inst->width;
  vpx_configs_[0].g_h = inst->height;

  // Determine number of threads based on the image size and #cores.
  // TODO(fbarchard): Consider number of Simulcast layers.
  vpx_configs_[0].g_threads = NumberOfThreads(
      vpx_configs_[0].g_w, vpx_configs_[0].g_h, settings.number_of_cores);
  if (settings.encoder_thread_limit.has_value()) {
    RTC_DCHECK_GE(settings.encoder_thread_limit.value(), 1);
    vpx_configs_[0].g_threads = std::min(
        vpx_configs_[0].g_threads,
        static_cast<unsigned int>(settings.encoder_thread_limit.value()));
  }

  // Creating a wrapper to the image - setting image data to NULL.
  // Actual pointer will be set in encode. Setting align to 1, as it
  // is meaningless (no memory allocation is done here).
  libvpx_->img_wrap(&raw_images_[0], pixel_format, inst->width, inst->height, 1,
                    NULL);

  // Note the order we use is different from webm, we have lowest resolution
  // at position 0 and they have highest resolution at position 0.
  const size_t stream_idx_cfg_0 = encoders_.size() - 1;
  SimulcastRateAllocator init_allocator(codec_);
  VideoBitrateAllocation allocation =
      init_allocator.Allocate(VideoBitrateAllocationParameters(
          inst->startBitrate * 1000, inst->maxFramerate));
  std::vector<uint32_t> stream_bitrates;
  for (int i = 0; i == 0 || i < inst->numberOfSimulcastStreams; ++i) {
    uint32_t bitrate = allocation.GetSpatialLayerSum(i) / 1000;
    stream_bitrates.push_back(bitrate);
  }

  vpx_configs_[0].rc_target_bitrate = stream_bitrates[stream_idx_cfg_0];
  if (stream_bitrates[stream_idx_cfg_0] > 0) {
    uint32_t maxFramerate =
        inst->simulcastStream[stream_idx_cfg_0].maxFramerate;
    if (!maxFramerate) {
      maxFramerate = inst->maxFramerate;
    }

    frame_buffer_controller_->OnRatesUpdated(
        stream_idx_cfg_0,
        allocation.GetTemporalLayerAllocation(stream_idx_cfg_0), maxFramerate);
  }
  frame_buffer_controller_->SetQpLimits(stream_idx_cfg_0,
                                        vpx_configs_[0].rc_min_quantizer,
                                        vpx_configs_[0].rc_max_quantizer);
  UpdateVpxConfiguration(stream_idx_cfg_0);
  vpx_configs_[0].rc_dropframe_thresh = FrameDropThreshold(stream_idx_cfg_0);

  for (size_t i = 1; i < encoders_.size(); ++i) {
    const size_t stream_idx = encoders_.size() - 1 - i;
    memcpy(&vpx_configs_[i], &vpx_configs_[0], sizeof(vpx_configs_[0]));

    vpx_configs_[i].g_w = inst->simulcastStream[stream_idx].width;
    vpx_configs_[i].g_h = inst->simulcastStream[stream_idx].height;

    // Use 1 thread for lower resolutions.
    vpx_configs_[i].g_threads = 1;

    vpx_configs_[i].rc_dropframe_thresh = FrameDropThreshold(stream_idx);

    // Setting alignment to 32 - as that ensures at least 16 for all
    // planes (32 for Y, 16 for U,V). Libvpx sets the requested stride for
    // the y plane, but only half of it to the u and v planes.
    libvpx_->img_alloc(
        &raw_images_[i], pixel_format, inst->simulcastStream[stream_idx].width,
        inst->simulcastStream[stream_idx].height, kVp832ByteAlign);
    SetStreamState(stream_bitrates[stream_idx] > 0, stream_idx);
    vpx_configs_[i].rc_target_bitrate = stream_bitrates[stream_idx];
    if (stream_bitrates[stream_idx] > 0) {
      uint32_t maxFramerate = inst->simulcastStream[stream_idx].maxFramerate;
      if (!maxFramerate) {
        maxFramerate = inst->maxFramerate;
      }
      frame_buffer_controller_->OnRatesUpdated(
          stream_idx, allocation.GetTemporalLayerAllocation(stream_idx),
          maxFramerate);
    }
    frame_buffer_controller_->SetQpLimits(stream_idx,
                                          vpx_configs_[i].rc_min_quantizer,
                                          vpx_configs_[i].rc_max_quantizer);
    UpdateVpxConfiguration(stream_idx);
  }

  return InitAndSetControlSettings();
}

int LibvpxVp8Encoder::GetCpuSpeed(int width, int height) {
#ifdef MOBILE_ARM
  // On mobile platform, use a lower speed setting for lower resolutions for
  // CPUs with 4 or more cores.
  RTC_DCHECK_GT(number_of_cores_, 0);
  if (experimental_cpu_speed_config_arm_
          .GetValue(width * height, number_of_cores_)
          .has_value()) {
    return experimental_cpu_speed_config_arm_
        .GetValue(width * height, number_of_cores_)
        .value();
  }

  if (number_of_cores_ <= 3)
    return -12;

  if (width * height <= 352 * 288)
    return -8;
  else if (width * height <= 640 * 480)
    return -10;
  else
    return -12;
#else
  // For non-ARM, increase encoding complexity (i.e., use lower speed setting)
  // if resolution is below CIF. Otherwise, keep the default/user setting
  // (`cpu_speed_default_`) set on InitEncode via VP8().complexity.
  if (width * height < 352 * 288)
    return (cpu_speed_default_ < -4) ? -4 : cpu_speed_default_;
  else
    return cpu_speed_default_;
#endif
}

int LibvpxVp8Encoder::NumberOfThreads(int width, int height, int cpus) {
#if defined(WEBRTC_ANDROID)
  if (width * height >= 320 * 180) {
    if (cpus >= 4) {
      // 3 threads for CPUs with 4 and more cores since most of times only 4
      // cores will be active.
      return 3;
    } else if (cpus == 3 || cpus == 2) {
      return 2;
    } else {
      return 1;
    }
  }
  return 1;
#else
#if defined(WEBRTC_IOS)
  std::string trial_string =
      field_trial::FindFullName(kVP8IosMaxNumberOfThreadFieldTrial);
  FieldTrialParameter<int> max_thread_number(
      kVP8IosMaxNumberOfThreadFieldTrialParameter, 0);
  ParseFieldTrial({&max_thread_number}, trial_string);
  if (max_thread_number.Get() > 0) {
    if (width * height < 320 * 180) {
      return 1;  // Use single thread for small screens
    }
    // thread number must be less than or equal to the number of CPUs.
    return std::min(cpus, max_thread_number.Get());
  }
#endif  // defined(WEBRTC_IOS)
  if (width * height >= 1920 * 1080 && cpus > 8) {
    return 8;  // 8 threads for 1080p on high perf machines.
  } else if (width * height > 1280 * 960 && cpus >= 6) {
    // 3 threads for 1080p.
    return 3;
  } else if (width * height > 640 * 480 && cpus >= 3) {
    // Default 2 threads for qHD/HD, but allow 3 if core count is high enough,
    // as this will allow more margin for high-core/low clock machines or if
    // not built with highest optimization.
    if (cpus >= 6) {
      return 3;
    }
    return 2;
  } else {
    // 1 thread for VGA or less.
    return 1;
  }
#endif
}

int LibvpxVp8Encoder::InitAndSetControlSettings() {
  vpx_codec_flags_t flags = 0;
  flags |= VPX_CODEC_USE_OUTPUT_PARTITION;

  if (encoders_.size() > 1) {
    int error = libvpx_->codec_enc_init_multi(
        &encoders_[0], vpx_codec_vp8_cx(), &vpx_configs_[0], encoders_.size(),
        flags, &downsampling_factors_[0]);
    if (error) {
      return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
    }
  } else {
    if (libvpx_->codec_enc_init(&encoders_[0], vpx_codec_vp8_cx(),
                                &vpx_configs_[0], flags)) {
      return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
    }
  }
  // Enable denoising for the highest resolution stream, and for
  // the second highest resolution if we are doing more than 2
  // spatial layers/streams.
  // TODO(holmer): Investigate possibility of adding a libvpx API
  // for getting the denoised frame from the encoder and using that
  // when encoding lower resolution streams. Would it work with the
  // multi-res encoding feature?
#ifdef MOBILE_ARM
  denoiserState denoiser_state = kDenoiserOnYOnly;
#else
  denoiserState denoiser_state = kDenoiserOnAdaptive;
#endif
  libvpx_->codec_control(
      &encoders_[0], VP8E_SET_NOISE_SENSITIVITY,
      codec_.VP8()->denoisingOn ? denoiser_state : kDenoiserOff);
  if (encoders_.size() > 2) {
    libvpx_->codec_control(
        &encoders_[1], VP8E_SET_NOISE_SENSITIVITY,
        codec_.VP8()->denoisingOn ? denoiser_state : kDenoiserOff);
  }
  for (size_t i = 0; i < encoders_.size(); ++i) {
    // Allow more screen content to be detected as static.
    libvpx_->codec_control(
        &(encoders_[i]), VP8E_SET_STATIC_THRESHOLD,
        codec_.mode == VideoCodecMode::kScreensharing ? 100u : 1u);
    libvpx_->codec_control(&(encoders_[i]), VP8E_SET_CPUUSED, cpu_speed_[i]);
    libvpx_->codec_control(
        &(encoders_[i]), VP8E_SET_TOKEN_PARTITIONS,
        static_cast<vp8e_token_partitions>(kTokenPartitions));
    libvpx_->codec_control(&(encoders_[i]), VP8E_SET_MAX_INTRA_BITRATE_PCT,
                           rc_max_intra_target_);
    // VP8E_SET_SCREEN_CONTENT_MODE 2 = screen content with more aggressive
    // rate control (drop frames on large target bitrate overshoot)
    libvpx_->codec_control(
        &(encoders_[i]), VP8E_SET_SCREEN_CONTENT_MODE,
        codec_.mode == VideoCodecMode::kScreensharing ? 2u : 0u);
  }
  inited_ = true;
  return WEBRTC_VIDEO_CODEC_OK;
}

uint32_t LibvpxVp8Encoder::MaxIntraTarget(uint32_t optimalBuffersize) {
  // Set max to the optimal buffer level (normalized by target BR),
  // and scaled by a scalePar.
  // Max target size = scalePar * optimalBufferSize * targetBR[Kbps].
  // This values is presented in percentage of perFrameBw:
  // perFrameBw = targetBR[Kbps] * 1000 / frameRate.
  // The target in % is as follows:

  float scalePar = 0.5;
  uint32_t targetPct = optimalBuffersize * scalePar * codec_.maxFramerate / 10;

  // Don't go below 3 times the per frame bandwidth.
  const uint32_t minIntraTh = 300;
  return (targetPct < minIntraTh) ? minIntraTh : targetPct;
}

uint32_t LibvpxVp8Encoder::FrameDropThreshold(size_t spatial_idx) const {
  if (!codec_.GetFrameDropEnabled()) {
    return 0;
  }

  // If temporal layers are used, they get to override the frame dropping
  // setting, as eg. ScreenshareLayers does not work as intended with frame
  // dropping on and DefaultTemporalLayers will have performance issues with
  // frame dropping off.
  RTC_DCHECK(frame_buffer_controller_);
  RTC_DCHECK_LT(spatial_idx, frame_buffer_controller_->StreamCount());
  return frame_buffer_controller_->SupportsEncoderFrameDropping(spatial_idx)
             ? 30
             : 0;
}

size_t LibvpxVp8Encoder::SteadyStateSize(int sid, int tid) {
  const int encoder_id = encoders_.size() - 1 - sid;
  size_t bitrate_bps;
  float fps;
  if ((SimulcastUtility::IsConferenceModeScreenshare(codec_) && sid == 0) ||
      vpx_configs_[encoder_id].ts_number_layers <= 1) {
    // In conference screenshare there's no defined per temporal layer bitrate
    // and framerate.
    bitrate_bps = vpx_configs_[encoder_id].rc_target_bitrate * 1000;
    fps = codec_.maxFramerate;
  } else {
    bitrate_bps = vpx_configs_[encoder_id].ts_target_bitrate[tid] * 1000;
    fps = codec_.maxFramerate /
          fmax(vpx_configs_[encoder_id].ts_rate_decimator[tid], 1.0);
    if (tid > 0) {
      // Layer bitrate and fps are counted as a partial sums.
      bitrate_bps -= vpx_configs_[encoder_id].ts_target_bitrate[tid - 1] * 1000;
      fps = codec_.maxFramerate /
            fmax(vpx_configs_[encoder_id].ts_rate_decimator[tid - 1], 1.0);
    }
  }

  if (fps < 1e-9)
    return 0;
  return static_cast<size_t>(
      bitrate_bps / (8 * fps) *
          (100 -
           variable_framerate_experiment_.steady_state_undershoot_percentage) /
          100 +
      0.5);
}

bool LibvpxVp8Encoder::UpdateVpxConfiguration(size_t stream_index) {
  RTC_DCHECK(frame_buffer_controller_);

  const size_t config_index = vpx_configs_.size() - 1 - stream_index;

  RTC_DCHECK_LT(config_index, config_overrides_.size());
  Vp8EncoderConfig* config = &config_overrides_[config_index];

  const Vp8EncoderConfig new_config =
      frame_buffer_controller_->UpdateConfiguration(stream_index);

  if (new_config.reset_previous_configuration_overrides) {
    *config = new_config;
    return true;
  }

  const bool changes_made = MaybeExtendVp8EncoderConfig(new_config, config);

  // Note that overrides must be applied even if they haven't changed.
  RTC_DCHECK_LT(config_index, vpx_configs_.size());
  vpx_codec_enc_cfg_t* vpx_config = &vpx_configs_[config_index];
  ApplyVp8EncoderConfigToVpxConfig(*config, vpx_config);

  return changes_made;
}

int LibvpxVp8Encoder::Encode(const VideoFrame& frame,
                             const std::vector<VideoFrameType>* frame_types) {
  RTC_DCHECK_EQ(frame.width(), codec_.width);
  RTC_DCHECK_EQ(frame.height(), codec_.height);

  if (!inited_)
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  if (encoded_complete_callback_ == NULL)
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;

  bool key_frame_requested = false;
  for (size_t i = 0; i < key_frame_request_.size() && i < send_stream_.size();
       ++i) {
    if (key_frame_request_[i] && send_stream_[i]) {
      key_frame_requested = true;
      break;
    }
  }
  if (!key_frame_requested && frame_types) {
    for (size_t i = 0; i < frame_types->size() && i < send_stream_.size();
         ++i) {
      if ((*frame_types)[i] == VideoFrameType::kVideoFrameKey &&
          send_stream_[i]) {
        key_frame_requested = true;
        break;
      }
    }
  }

  // Check if any encoder risks timing out and force a frame in that case.
  std::vector<FrameDropConfigOverride> frame_drop_overrides_;
  if (max_frame_drop_interval_.has_value()) {
    Timestamp now = Timestamp::Micros(frame.timestamp_us());
    for (size_t i = 0; i < send_stream_.size(); ++i) {
      if (send_stream_[i] && FrameDropThreshold(i) > 0 &&
          last_encoder_output_time_[i].IsFinite() &&
          (now - last_encoder_output_time_[i]) >= *max_frame_drop_interval_) {
        RTC_LOG(LS_INFO) << "Forcing frame to avoid timeout for stream " << i;
        size_t encoder_idx = encoders_.size() - 1 - i;
        frame_drop_overrides_.emplace_back(libvpx_.get(),
                                           &encoders_[encoder_idx],
                                           &vpx_configs_[encoder_idx], 0);
      }
    }
  }

  if (frame.update_rect().IsEmpty() && num_steady_state_frames_ >= 3 &&
      !key_frame_requested) {
    if (variable_framerate_experiment_.enabled &&
        framerate_controller_.DropFrame(frame.timestamp() / kRtpTicksPerMs) &&
        frame_drop_overrides_.empty()) {
      return WEBRTC_VIDEO_CODEC_OK;
    }
    framerate_controller_.AddFrame(frame.timestamp() / kRtpTicksPerMs);
  }

  bool send_key_frame = key_frame_requested;
  bool drop_frame = false;
  bool retransmission_allowed = true;
  Vp8FrameConfig tl_configs[kMaxSimulcastStreams];
  for (size_t i = 0; i < encoders_.size(); ++i) {
    tl_configs[i] =
        frame_buffer_controller_->NextFrameConfig(i, frame.timestamp());
    send_key_frame |= tl_configs[i].IntraFrame();
    drop_frame |= tl_configs[i].drop_frame;
    RTC_DCHECK(i == 0 ||
               retransmission_allowed == tl_configs[i].retransmission_allowed);
    retransmission_allowed = tl_configs[i].retransmission_allowed;
  }

  if (drop_frame && !send_key_frame) {
    return WEBRTC_VIDEO_CODEC_OK;
  }

  vpx_enc_frame_flags_t flags[kMaxSimulcastStreams];
  for (size_t i = 0; i < encoders_.size(); ++i) {
    flags[i] = send_key_frame ? VPX_EFLAG_FORCE_KF : EncodeFlags(tl_configs[i]);
  }

  // Scale and map buffers and set `raw_images_` to hold pointers to the result.
  // Because `raw_images_` are set to hold pointers to the prepared buffers, we
  // need to keep these buffers alive through reference counting until after
  // encoding is complete.
  std::vector<rtc::scoped_refptr<VideoFrameBuffer>> prepared_buffers =
      PrepareBuffers(frame.video_frame_buffer());
  if (prepared_buffers.empty()) {
    return WEBRTC_VIDEO_CODEC_ERROR;
  }
  struct CleanUpOnExit {
    explicit CleanUpOnExit(
        vpx_image_t* raw_image,
        std::vector<rtc::scoped_refptr<VideoFrameBuffer>> prepared_buffers)
        : raw_image_(raw_image),
          prepared_buffers_(std::move(prepared_buffers)) {}
    ~CleanUpOnExit() {
      raw_image_->planes[VPX_PLANE_Y] = nullptr;
      raw_image_->planes[VPX_PLANE_U] = nullptr;
      raw_image_->planes[VPX_PLANE_V] = nullptr;
    }
    vpx_image_t* raw_image_;
    std::vector<rtc::scoped_refptr<VideoFrameBuffer>> prepared_buffers_;
  } clean_up_on_exit(&raw_images_[0], std::move(prepared_buffers));

  if (send_key_frame) {
    // Adapt the size of the key frame when in screenshare with 1 temporal
    // layer.
    if (encoders_.size() == 1 &&
        codec_.mode == VideoCodecMode::kScreensharing &&
        codec_.VP8()->numberOfTemporalLayers <= 1) {
      const uint32_t forceKeyFrameIntraTh = 100;
      libvpx_->codec_control(&(encoders_[0]), VP8E_SET_MAX_INTRA_BITRATE_PCT,
                             forceKeyFrameIntraTh);
    }

    std::fill(key_frame_request_.begin(), key_frame_request_.end(), false);
  }

  // Set the encoder frame flags and temporal layer_id for each spatial stream.
  // Note that streams are defined starting from lowest resolution at
  // position 0 to highest resolution at position |encoders_.size() - 1|,
  // whereas `encoder_` is from highest to lowest resolution.
  for (size_t i = 0; i < encoders_.size(); ++i) {
    const size_t stream_idx = encoders_.size() - 1 - i;

    if (UpdateVpxConfiguration(stream_idx)) {
      if (libvpx_->codec_enc_config_set(&encoders_[i], &vpx_configs_[i]))
        return WEBRTC_VIDEO_CODEC_ERROR;
    }

    libvpx_->codec_control(&encoders_[i], VP8E_SET_FRAME_FLAGS,
                           static_cast<int>(flags[stream_idx]));
    libvpx_->codec_control(&encoders_[i], VP8E_SET_TEMPORAL_LAYER_ID,
                           tl_configs[i].encoder_layer_id);
  }
  // TODO(holmer): Ideally the duration should be the timestamp diff of this
  // frame and the next frame to be encoded, which we don't have. Instead we
  // would like to use the duration of the previous frame. Unfortunately the
  // rate control seems to be off with that setup. Using the average input
  // frame rate to calculate an average duration for now.
  RTC_DCHECK_GT(codec_.maxFramerate, 0);
  uint32_t duration = kRtpTicksPerSecond / codec_.maxFramerate;

  int error = WEBRTC_VIDEO_CODEC_OK;
  int num_tries = 0;
  // If the first try returns WEBRTC_VIDEO_CODEC_TARGET_BITRATE_OVERSHOOT
  // the frame must be reencoded with the same parameters again because
  // target bitrate is exceeded and encoder state has been reset.
  while (num_tries == 0 ||
         (num_tries == 1 &&
          error == WEBRTC_VIDEO_CODEC_TARGET_BITRATE_OVERSHOOT)) {
    ++num_tries;
    // Note we must pass 0 for `flags` field in encode call below since they are
    // set above in `libvpx_interface_->vpx_codec_control_` function for each
    // encoder/spatial layer.
    error = libvpx_->codec_encode(&encoders_[0], &raw_images_[0], timestamp_,
                                  duration, 0, VPX_DL_REALTIME);
    // Reset specific intra frame thresholds, following the key frame.
    if (send_key_frame) {
      libvpx_->codec_control(&(encoders_[0]), VP8E_SET_MAX_INTRA_BITRATE_PCT,
                             rc_max_intra_target_);
    }
    if (error)
      return WEBRTC_VIDEO_CODEC_ERROR;
    // Examines frame timestamps only.
    error = GetEncodedPartitions(frame, retransmission_allowed);
  }
  // TODO(sprang): Shouldn't we use the frame timestamp instead?
  timestamp_ += duration;
  return error;
}

void LibvpxVp8Encoder::PopulateCodecSpecific(CodecSpecificInfo* codec_specific,
                                             const vpx_codec_cx_pkt_t& pkt,
                                             int stream_idx,
                                             int encoder_idx,
                                             uint32_t timestamp) {
  RTC_DCHECK(codec_specific);
  codec_specific->codecType = kVideoCodecVP8;
  codec_specific->codecSpecific.VP8.keyIdx =
      kNoKeyIdx;  // TODO(hlundin) populate this
  codec_specific->codecSpecific.VP8.nonReference =
      (pkt.data.frame.flags & VPX_FRAME_IS_DROPPABLE) != 0;

  int qp = 0;
  vpx_codec_control(&encoders_[encoder_idx], VP8E_GET_LAST_QUANTIZER_64, &qp);
  bool is_keyframe = (pkt.data.frame.flags & VPX_FRAME_IS_KEY) != 0;
  frame_buffer_controller_->OnEncodeDone(stream_idx, timestamp,
                                         encoded_images_[encoder_idx].size(),
                                         is_keyframe, qp, codec_specific);
  if (is_keyframe && codec_specific->template_structure != absl::nullopt) {
    // Number of resolutions must match number of spatial layers, VP8 structures
    // expected to use single spatial layer. Templates must be ordered by
    // spatial_id, so assumption there is exactly one spatial layer is same as
    // assumption last template uses spatial_id = 0.
    // This check catches potential scenario where template_structure is shared
    // across multiple vp8 streams and they are distinguished using spatial_id.
    // Assigning single resolution doesn't support such scenario, i.e. assumes
    // vp8 simulcast is sent using multiple ssrcs.
    RTC_DCHECK(!codec_specific->template_structure->templates.empty());
    RTC_DCHECK_EQ(
        codec_specific->template_structure->templates.back().spatial_id, 0);
    codec_specific->template_structure->resolutions = {
        RenderResolution(pkt.data.frame.width[0], pkt.data.frame.height[0])};
  }
  switch (vpx_configs_[encoder_idx].ts_number_layers) {
    case 1:
      codec_specific->scalability_mode = ScalabilityMode::kL1T1;
      break;
    case 2:
      codec_specific->scalability_mode = ScalabilityMode::kL1T2;
      break;
    case 3:
      codec_specific->scalability_mode = ScalabilityMode::kL1T3;
      break;
  }
}

int LibvpxVp8Encoder::GetEncodedPartitions(const VideoFrame& input_image,
                                           bool retransmission_allowed) {
  int stream_idx = static_cast<int>(encoders_.size()) - 1;
  int result = WEBRTC_VIDEO_CODEC_OK;
  for (size_t encoder_idx = 0; encoder_idx < encoders_.size();
       ++encoder_idx, --stream_idx) {
    vpx_codec_iter_t iter = NULL;
    encoded_images_[encoder_idx].set_size(0);
    encoded_images_[encoder_idx]._frameType = VideoFrameType::kVideoFrameDelta;
    CodecSpecificInfo codec_specific;
    const vpx_codec_cx_pkt_t* pkt = NULL;

    size_t encoded_size = 0;
    while ((pkt = libvpx_->codec_get_cx_data(&encoders_[encoder_idx], &iter)) !=
           NULL) {
      if (pkt->kind == VPX_CODEC_CX_FRAME_PKT) {
        encoded_size += pkt->data.frame.sz;
      }
    }

    auto buffer = EncodedImageBuffer::Create(encoded_size);

    iter = NULL;
    size_t encoded_pos = 0;
    while ((pkt = libvpx_->codec_get_cx_data(&encoders_[encoder_idx], &iter)) !=
           NULL) {
      switch (pkt->kind) {
        case VPX_CODEC_CX_FRAME_PKT: {
          RTC_CHECK_LE(encoded_pos + pkt->data.frame.sz, buffer->size());
          memcpy(&buffer->data()[encoded_pos], pkt->data.frame.buf,
                 pkt->data.frame.sz);
          encoded_pos += pkt->data.frame.sz;
          break;
        }
        default:
          break;
      }
      // End of frame
      if ((pkt->data.frame.flags & VPX_FRAME_IS_FRAGMENT) == 0) {
        // check if encoded frame is a key frame
        if (pkt->data.frame.flags & VPX_FRAME_IS_KEY) {
          encoded_images_[encoder_idx]._frameType =
              VideoFrameType::kVideoFrameKey;
        }
        encoded_images_[encoder_idx].SetEncodedData(buffer);
        encoded_images_[encoder_idx].set_size(encoded_pos);
        encoded_images_[encoder_idx].SetSimulcastIndex(stream_idx);
        PopulateCodecSpecific(&codec_specific, *pkt, stream_idx, encoder_idx,
                              input_image.timestamp());
        if (codec_specific.codecSpecific.VP8.temporalIdx != kNoTemporalIdx) {
          encoded_images_[encoder_idx].SetTemporalIndex(
              codec_specific.codecSpecific.VP8.temporalIdx);
        }
        break;
      }
    }
    encoded_images_[encoder_idx].SetRtpTimestamp(input_image.timestamp());
    encoded_images_[encoder_idx].SetCaptureTimeIdentifier(
        input_image.capture_time_identifier());
    encoded_images_[encoder_idx].SetColorSpace(input_image.color_space());
    encoded_images_[encoder_idx].SetRetransmissionAllowed(
        retransmission_allowed);

    if (send_stream_[stream_idx]) {
      if (encoded_images_[encoder_idx].size() > 0) {
        TRACE_COUNTER_ID1("webrtc", "EncodedFrameSize", encoder_idx,
                          encoded_images_[encoder_idx].size());
        encoded_images_[encoder_idx]._encodedHeight =
            codec_.simulcastStream[stream_idx].height;
        encoded_images_[encoder_idx]._encodedWidth =
            codec_.simulcastStream[stream_idx].width;
        int qp_128 = -1;
        libvpx_->codec_control(&encoders_[encoder_idx], VP8E_GET_LAST_QUANTIZER,
                               &qp_128);
        encoded_images_[encoder_idx].qp_ = qp_128;
        last_encoder_output_time_[stream_idx] =
            Timestamp::Micros(input_image.timestamp_us());

        encoded_complete_callback_->OnEncodedImage(encoded_images_[encoder_idx],
                                                   &codec_specific);
        const size_t steady_state_size = SteadyStateSize(
            stream_idx, codec_specific.codecSpecific.VP8.temporalIdx);
        if (qp_128 > variable_framerate_experiment_.steady_state_qp ||
            encoded_images_[encoder_idx].size() > steady_state_size) {
          num_steady_state_frames_ = 0;
        } else {
          ++num_steady_state_frames_;
        }
      } else if (!frame_buffer_controller_->SupportsEncoderFrameDropping(
                     stream_idx)) {
        result = WEBRTC_VIDEO_CODEC_TARGET_BITRATE_OVERSHOOT;
        if (encoded_images_[encoder_idx].size() == 0) {
          // Dropped frame that will be re-encoded.
          frame_buffer_controller_->OnFrameDropped(stream_idx,
                                                   input_image.timestamp());
        }
      }
    }
  }
  return result;
}

VideoEncoder::EncoderInfo LibvpxVp8Encoder::GetEncoderInfo() const {
  EncoderInfo info;
  info.supports_native_handle = false;
  info.implementation_name = "libvpx";
  info.has_trusted_rate_controller =
      rate_control_settings_.LibvpxVp8TrustedRateController();
  info.is_hardware_accelerated = false;
  info.supports_simulcast = true;
  if (!resolution_bitrate_limits_.empty()) {
    info.resolution_bitrate_limits = resolution_bitrate_limits_;
  }
  if (encoder_info_override_.requested_resolution_alignment()) {
    info.requested_resolution_alignment =
        *encoder_info_override_.requested_resolution_alignment();
    info.apply_alignment_to_all_simulcast_layers =
        encoder_info_override_.apply_alignment_to_all_simulcast_layers();
  }
  if (!encoder_info_override_.resolution_bitrate_limits().empty()) {
    info.resolution_bitrate_limits =
        encoder_info_override_.resolution_bitrate_limits();
  }

  const bool enable_scaling =
      num_active_streams_ == 1 &&
      (vpx_configs_.empty() || vpx_configs_[0].rc_dropframe_thresh > 0) &&
      codec_.VP8().automaticResizeOn;

  info.scaling_settings = enable_scaling
                              ? VideoEncoder::ScalingSettings(
                                    kLowVp8QpThreshold, kHighVp8QpThreshold)
                              : VideoEncoder::ScalingSettings::kOff;
  if (rate_control_settings_.LibvpxVp8MinPixels()) {
    info.scaling_settings.min_pixels_per_frame =
        rate_control_settings_.LibvpxVp8MinPixels().value();
  }
  info.preferred_pixel_formats = {VideoFrameBuffer::Type::kI420,
                                  VideoFrameBuffer::Type::kNV12};

  if (inited_) {
    // `encoder_idx` is libvpx index where 0 is highest resolution.
    // `si` is simulcast index, where 0 is lowest resolution.
    for (size_t si = 0, encoder_idx = encoders_.size() - 1;
         si < encoders_.size(); ++si, --encoder_idx) {
      info.fps_allocation[si].clear();
      if ((codec_.numberOfSimulcastStreams > si &&
           !codec_.simulcastStream[si].active) ||
          (si == 0 && SimulcastUtility::IsConferenceModeScreenshare(codec_))) {
        // No defined frame rate fractions if not active or if using
        // ScreenshareLayers, leave vector empty and continue;
        continue;
      }
      if (vpx_configs_[encoder_idx].ts_number_layers <= 1) {
        info.fps_allocation[si].push_back(EncoderInfo::kMaxFramerateFraction);
      } else {
        for (size_t ti = 0; ti < vpx_configs_[encoder_idx].ts_number_layers;
             ++ti) {
          RTC_DCHECK_GT(vpx_configs_[encoder_idx].ts_rate_decimator[ti], 0);
          info.fps_allocation[si].push_back(rtc::saturated_cast<uint8_t>(
              EncoderInfo::kMaxFramerateFraction /
                  vpx_configs_[encoder_idx].ts_rate_decimator[ti] +
              0.5));
        }
      }
    }
  }

  return info;
}

int LibvpxVp8Encoder::RegisterEncodeCompleteCallback(
    EncodedImageCallback* callback) {
  encoded_complete_callback_ = callback;
  return WEBRTC_VIDEO_CODEC_OK;
}

void LibvpxVp8Encoder::MaybeUpdatePixelFormat(vpx_img_fmt fmt) {
  RTC_DCHECK(!raw_images_.empty());
  if (raw_images_[0].fmt == fmt) {
    RTC_DCHECK(std::all_of(
        std::next(raw_images_.begin()), raw_images_.end(),
        [fmt](const vpx_image_t& raw_img) { return raw_img.fmt == fmt; }))
        << "Not all raw images had the right format!";
    return;
  }
  RTC_LOG(LS_INFO) << "Updating vp8 encoder pixel format to "
                   << (fmt == VPX_IMG_FMT_NV12 ? "NV12" : "I420");
  for (size_t i = 0; i < raw_images_.size(); ++i) {
    vpx_image_t& img = raw_images_[i];
    auto d_w = img.d_w;
    auto d_h = img.d_h;
    libvpx_->img_free(&img);
    // First image is wrapping the input frame, the rest are allocated.
    if (i == 0) {
      libvpx_->img_wrap(&img, fmt, d_w, d_h, 1, NULL);
    } else {
      libvpx_->img_alloc(&img, fmt, d_w, d_h, kVp832ByteAlign);
    }
  }
}

std::vector<rtc::scoped_refptr<VideoFrameBuffer>>
LibvpxVp8Encoder::PrepareBuffers(rtc::scoped_refptr<VideoFrameBuffer> buffer) {
  RTC_DCHECK_EQ(buffer->width(), raw_images_[0].d_w);
  RTC_DCHECK_EQ(buffer->height(), raw_images_[0].d_h);
  absl::InlinedVector<VideoFrameBuffer::Type, kMaxPreferredPixelFormats>
      supported_formats = {VideoFrameBuffer::Type::kI420,
                           VideoFrameBuffer::Type::kNV12};

  rtc::scoped_refptr<VideoFrameBuffer> mapped_buffer;
  if (buffer->type() != VideoFrameBuffer::Type::kNative) {
    // `buffer` is already mapped.
    mapped_buffer = buffer;
  } else {
    // Attempt to map to one of the supported formats.
    mapped_buffer = buffer->GetMappedFrameBuffer(supported_formats);
  }
  if (!mapped_buffer ||
      (absl::c_find(supported_formats, mapped_buffer->type()) ==
           supported_formats.end() &&
       mapped_buffer->type() != VideoFrameBuffer::Type::kI420A)) {
    // Unknown pixel format or unable to map, convert to I420 and prepare that
    // buffer instead to ensure Scale() is safe to use.
    auto converted_buffer = buffer->ToI420();
    if (!converted_buffer) {
      RTC_LOG(LS_ERROR) << "Failed to convert "
                        << VideoFrameBufferTypeToString(buffer->type())
                        << " image to I420. Can't encode frame.";
      return {};
    }
    RTC_CHECK(converted_buffer->type() == VideoFrameBuffer::Type::kI420 ||
              converted_buffer->type() == VideoFrameBuffer::Type::kI420A);

    // Because `buffer` had to be converted, use `converted_buffer` instead...
    buffer = mapped_buffer = converted_buffer;
  }

  // Maybe update pixel format.
  absl::InlinedVector<VideoFrameBuffer::Type, kMaxPreferredPixelFormats>
      mapped_type = {mapped_buffer->type()};
  switch (mapped_buffer->type()) {
    case VideoFrameBuffer::Type::kI420:
    case VideoFrameBuffer::Type::kI420A:
      MaybeUpdatePixelFormat(VPX_IMG_FMT_I420);
      break;
    case VideoFrameBuffer::Type::kNV12:
      MaybeUpdatePixelFormat(VPX_IMG_FMT_NV12);
      break;
    default:
      RTC_DCHECK_NOTREACHED();
  }

  // Prepare `raw_images_` from `mapped_buffer` and, if simulcast, scaled
  // versions of `buffer`.
  std::vector<rtc::scoped_refptr<VideoFrameBuffer>> prepared_buffers;
  SetRawImagePlanes(&raw_images_[0], mapped_buffer.get());
  prepared_buffers.push_back(mapped_buffer);
  for (size_t i = 1; i < encoders_.size(); ++i) {
    // Native buffers should implement optimized scaling and is the preferred
    // buffer to scale. But if the buffer isn't native, it should be cheaper to
    // scale from the previously prepared buffer which is smaller than `buffer`.
    VideoFrameBuffer* buffer_to_scale =
        buffer->type() == VideoFrameBuffer::Type::kNative
            ? buffer.get()
            : prepared_buffers.back().get();

    auto scaled_buffer =
        buffer_to_scale->Scale(raw_images_[i].d_w, raw_images_[i].d_h);
    if (scaled_buffer->type() == VideoFrameBuffer::Type::kNative) {
      auto mapped_scaled_buffer =
          scaled_buffer->GetMappedFrameBuffer(mapped_type);
      RTC_DCHECK(mapped_scaled_buffer) << "Unable to map the scaled buffer.";
      if (!mapped_scaled_buffer) {
        RTC_LOG(LS_ERROR) << "Failed to map scaled "
                          << VideoFrameBufferTypeToString(scaled_buffer->type())
                          << " image to "
                          << VideoFrameBufferTypeToString(mapped_buffer->type())
                          << ". Can't encode frame.";
        return {};
      }
      scaled_buffer = mapped_scaled_buffer;
    }
    if (!IsCompatibleVideoFrameBufferType(scaled_buffer->type(),
                                          mapped_buffer->type())) {
      RTC_LOG(LS_ERROR) << "When scaling "
                        << VideoFrameBufferTypeToString(buffer_to_scale->type())
                        << ", the image was unexpectedly converted to "
                        << VideoFrameBufferTypeToString(scaled_buffer->type())
                        << " instead of "
                        << VideoFrameBufferTypeToString(mapped_buffer->type())
                        << ". Can't encode frame.";
      RTC_DCHECK_NOTREACHED()
          << "Scaled buffer type "
          << VideoFrameBufferTypeToString(scaled_buffer->type())
          << " is not compatible with mapped buffer type "
          << VideoFrameBufferTypeToString(mapped_buffer->type());
      return {};
    }
    SetRawImagePlanes(&raw_images_[i], scaled_buffer.get());
    prepared_buffers.push_back(scaled_buffer);
  }
  return prepared_buffers;
}

// static
LibvpxVp8Encoder::VariableFramerateExperiment
LibvpxVp8Encoder::ParseVariableFramerateConfig(std::string group_name) {
  FieldTrialFlag disabled = FieldTrialFlag("Disabled");
  FieldTrialParameter<double> framerate_limit("min_fps", 5.0);
  FieldTrialParameter<int> qp("min_qp", 15);
  FieldTrialParameter<int> undershoot_percentage("undershoot", 30);
  ParseFieldTrial({&disabled, &framerate_limit, &qp, &undershoot_percentage},
                  field_trial::FindFullName(group_name));
  VariableFramerateExperiment config;
  config.enabled = !disabled.Get();
  config.framerate_limit = framerate_limit.Get();
  config.steady_state_qp = qp.Get();
  config.steady_state_undershoot_percentage = undershoot_percentage.Get();

  return config;
}

}  // namespace webrtc

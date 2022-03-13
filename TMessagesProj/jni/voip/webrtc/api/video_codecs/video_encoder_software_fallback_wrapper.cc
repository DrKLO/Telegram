/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/video_encoder_software_fallback_wrapper.h"

#include <stdint.h>

#include <cstdio>
#include <memory>
#include <string>
#include <vector>

#include "absl/strings/match.h"
#include "absl/types/optional.h"
#include "api/fec_controller_override.h"
#include "api/video/i420_buffer.h"
#include "api/video/video_bitrate_allocation.h"
#include "api/video/video_frame.h"
#include "api/video_codecs/video_codec.h"
#include "api/video_codecs/video_encoder.h"
#include "media/base/video_common.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "modules/video_coding/utility/simulcast_utility.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

namespace {

// If forced fallback is allowed, either:
//
// 1) The forced fallback is requested if the resolution is less than or equal
//    to `max_pixels_`. The resolution is allowed to be scaled down to
//    `min_pixels_`.
//
// 2) The forced fallback is requested if temporal support is preferred and the
//    SW fallback supports temporal layers while the HW encoder does not.

struct ForcedFallbackParams {
 public:
  bool SupportsResolutionBasedSwitch(const VideoCodec& codec) const {
    return enable_resolution_based_switch &&
           codec.codecType == kVideoCodecVP8 &&
           codec.numberOfSimulcastStreams <= 1 &&
           codec.width * codec.height <= max_pixels;
  }

  bool SupportsTemporalBasedSwitch(const VideoCodec& codec) const {
    return enable_temporal_based_switch &&
           SimulcastUtility::NumberOfTemporalLayers(codec, 0) != 1;
  }

  bool enable_temporal_based_switch = false;
  bool enable_resolution_based_switch = false;
  int min_pixels = 320 * 180;
  int max_pixels = 320 * 240;
};

const char kVp8ForceFallbackEncoderFieldTrial[] =
    "WebRTC-VP8-Forced-Fallback-Encoder-v2";

absl::optional<ForcedFallbackParams> ParseFallbackParamsFromFieldTrials(
    const VideoEncoder& main_encoder) {
  const std::string field_trial =
      webrtc::field_trial::FindFullName(kVp8ForceFallbackEncoderFieldTrial);
  if (!absl::StartsWith(field_trial, "Enabled")) {
    return absl::nullopt;
  }

  int max_pixels_lower_bound =
      main_encoder.GetEncoderInfo().scaling_settings.min_pixels_per_frame - 1;

  ForcedFallbackParams params;
  params.enable_resolution_based_switch = true;

  int min_bps = 0;
  if (sscanf(field_trial.c_str(), "Enabled-%d,%d,%d", &params.min_pixels,
             &params.max_pixels, &min_bps) != 3) {
    RTC_LOG(LS_WARNING)
        << "Invalid number of forced fallback parameters provided.";
    return absl::nullopt;
  } else if (params.min_pixels <= 0 ||
             params.max_pixels < max_pixels_lower_bound ||
             params.max_pixels < params.min_pixels || min_bps <= 0) {
    RTC_LOG(LS_WARNING) << "Invalid forced fallback parameter value provided.";
    return absl::nullopt;
  }

  return params;
}

absl::optional<ForcedFallbackParams> GetForcedFallbackParams(
    bool prefer_temporal_support,
    const VideoEncoder& main_encoder) {
  absl::optional<ForcedFallbackParams> params =
      ParseFallbackParamsFromFieldTrials(main_encoder);
  if (prefer_temporal_support) {
    if (!params.has_value()) {
      params.emplace();
    }
    params->enable_temporal_based_switch = prefer_temporal_support;
  }
  return params;
}

class VideoEncoderSoftwareFallbackWrapper final : public VideoEncoder {
 public:
  VideoEncoderSoftwareFallbackWrapper(
      std::unique_ptr<webrtc::VideoEncoder> sw_encoder,
      std::unique_ptr<webrtc::VideoEncoder> hw_encoder,
      bool prefer_temporal_support);
  ~VideoEncoderSoftwareFallbackWrapper() override;

  void SetFecControllerOverride(
      FecControllerOverride* fec_controller_override) override;

  int32_t InitEncode(const VideoCodec* codec_settings,
                     const VideoEncoder::Settings& settings) override;

  int32_t RegisterEncodeCompleteCallback(
      EncodedImageCallback* callback) override;

  int32_t Release() override;

  int32_t Encode(const VideoFrame& frame,
                 const std::vector<VideoFrameType>* frame_types) override;

  void OnPacketLossRateUpdate(float packet_loss_rate) override;

  void OnRttUpdate(int64_t rtt_ms) override;

  void OnLossNotification(const LossNotification& loss_notification) override;

  void SetRates(const RateControlParameters& parameters) override;

  EncoderInfo GetEncoderInfo() const override;

 private:
  bool InitFallbackEncoder(bool is_forced);
  bool TryInitForcedFallbackEncoder();
  bool IsFallbackActive() const;

  VideoEncoder* current_encoder() {
    switch (encoder_state_) {
      case EncoderState::kUninitialized:
        RTC_LOG(LS_WARNING)
            << "Trying to access encoder in uninitialized fallback wrapper.";
        // Return main encoder to preserve previous behavior.
        ABSL_FALLTHROUGH_INTENDED;
      case EncoderState::kMainEncoderUsed:
        return encoder_.get();
      case EncoderState::kFallbackDueToFailure:
      case EncoderState::kForcedFallback:
        return fallback_encoder_.get();
    }
    RTC_CHECK_NOTREACHED();
  }

  // Updates encoder with last observed parameters, such as callbacks, rates,
  // etc.
  void PrimeEncoder(VideoEncoder* encoder) const;

  // Settings used in the last InitEncode call and used if a dynamic fallback to
  // software is required.
  VideoCodec codec_settings_;
  absl::optional<VideoEncoder::Settings> encoder_settings_;

  // The last rate control settings, if set.
  absl::optional<RateControlParameters> rate_control_parameters_;

  // The last channel parameters set.
  absl::optional<float> packet_loss_;
  absl::optional<int64_t> rtt_;
  absl::optional<LossNotification> loss_notification_;

  enum class EncoderState {
    kUninitialized,
    kMainEncoderUsed,
    kFallbackDueToFailure,
    kForcedFallback
  };

  EncoderState encoder_state_;
  const std::unique_ptr<webrtc::VideoEncoder> encoder_;
  const std::unique_ptr<webrtc::VideoEncoder> fallback_encoder_;

  EncodedImageCallback* callback_;

  const absl::optional<ForcedFallbackParams> fallback_params_;
  int32_t EncodeWithMainEncoder(const VideoFrame& frame,
                                const std::vector<VideoFrameType>* frame_types);
};

VideoEncoderSoftwareFallbackWrapper::VideoEncoderSoftwareFallbackWrapper(
    std::unique_ptr<webrtc::VideoEncoder> sw_encoder,
    std::unique_ptr<webrtc::VideoEncoder> hw_encoder,
    bool prefer_temporal_support)
    : encoder_state_(EncoderState::kUninitialized),
      encoder_(std::move(hw_encoder)),
      fallback_encoder_(std::move(sw_encoder)),
      callback_(nullptr),
      fallback_params_(
          GetForcedFallbackParams(prefer_temporal_support, *encoder_)) {
  RTC_DCHECK(fallback_encoder_);
}

VideoEncoderSoftwareFallbackWrapper::~VideoEncoderSoftwareFallbackWrapper() =
    default;

void VideoEncoderSoftwareFallbackWrapper::PrimeEncoder(
    VideoEncoder* encoder) const {
  RTC_DCHECK(encoder);
  // Replay callback, rates, and channel parameters.
  if (callback_) {
    encoder->RegisterEncodeCompleteCallback(callback_);
  }
  if (rate_control_parameters_) {
    encoder->SetRates(*rate_control_parameters_);
  }
  if (rtt_.has_value()) {
    encoder->OnRttUpdate(rtt_.value());
  }
  if (packet_loss_.has_value()) {
    encoder->OnPacketLossRateUpdate(packet_loss_.value());
  }

  if (loss_notification_.has_value()) {
    encoder->OnLossNotification(loss_notification_.value());
  }
}

bool VideoEncoderSoftwareFallbackWrapper::InitFallbackEncoder(bool is_forced) {
  RTC_LOG(LS_WARNING) << "Encoder falling back to software encoding.";

  RTC_DCHECK(encoder_settings_.has_value());
  const int ret = fallback_encoder_->InitEncode(&codec_settings_,
                                                encoder_settings_.value());

  if (ret != WEBRTC_VIDEO_CODEC_OK) {
    RTC_LOG(LS_ERROR) << "Failed to initialize software-encoder fallback.";
    fallback_encoder_->Release();
    return false;
  }

  if (encoder_state_ == EncoderState::kMainEncoderUsed) {
    // Since we're switching to the fallback encoder, Release the real encoder.
    // It may be re-initialized via InitEncode later, and it will continue to
    // get Set calls for rates and channel parameters in the meantime.
    encoder_->Release();
  }

  if (is_forced) {
    encoder_state_ = EncoderState::kForcedFallback;
  } else {
    encoder_state_ = EncoderState::kFallbackDueToFailure;
  }

  return true;
}

void VideoEncoderSoftwareFallbackWrapper::SetFecControllerOverride(
    FecControllerOverride* fec_controller_override) {
  // It is important that only one of those would ever interact with the
  // `fec_controller_override` at a given time. This is the responsibility
  // of `this` to maintain.

  encoder_->SetFecControllerOverride(fec_controller_override);
  fallback_encoder_->SetFecControllerOverride(fec_controller_override);
}

int32_t VideoEncoderSoftwareFallbackWrapper::InitEncode(
    const VideoCodec* codec_settings,
    const VideoEncoder::Settings& settings) {
  // Store settings, in case we need to dynamically switch to the fallback
  // encoder after a failed Encode call.
  codec_settings_ = *codec_settings;
  encoder_settings_ = settings;
  // Clear stored rate/channel parameters.
  rate_control_parameters_ = absl::nullopt;

  RTC_DCHECK_EQ(encoder_state_, EncoderState::kUninitialized)
      << "InitEncode() should never be called on an active instance!";

  // Try to init forced software codec if it should be used.
  if (TryInitForcedFallbackEncoder()) {
    PrimeEncoder(current_encoder());
    return WEBRTC_VIDEO_CODEC_OK;
  }

  int32_t ret = encoder_->InitEncode(codec_settings, settings);
  if (ret == WEBRTC_VIDEO_CODEC_OK) {
    encoder_state_ = EncoderState::kMainEncoderUsed;
    PrimeEncoder(current_encoder());
    return ret;
  }

  // Try to instantiate software codec.
  if (InitFallbackEncoder(/*is_forced=*/false)) {
    PrimeEncoder(current_encoder());
    return WEBRTC_VIDEO_CODEC_OK;
  }

  // Software encoder failed too, use original return code.
  encoder_state_ = EncoderState::kUninitialized;
  return ret;
}

int32_t VideoEncoderSoftwareFallbackWrapper::RegisterEncodeCompleteCallback(
    EncodedImageCallback* callback) {
  callback_ = callback;
  return current_encoder()->RegisterEncodeCompleteCallback(callback);
}

int32_t VideoEncoderSoftwareFallbackWrapper::Release() {
  if (encoder_state_ == EncoderState::kUninitialized) {
    return WEBRTC_VIDEO_CODEC_OK;
  }
  int32_t ret = current_encoder()->Release();
  encoder_state_ = EncoderState::kUninitialized;
  return ret;
}

int32_t VideoEncoderSoftwareFallbackWrapper::Encode(
    const VideoFrame& frame,
    const std::vector<VideoFrameType>* frame_types) {
  switch (encoder_state_) {
    case EncoderState::kUninitialized:
      return WEBRTC_VIDEO_CODEC_ERROR;
    case EncoderState::kMainEncoderUsed: {
      return EncodeWithMainEncoder(frame, frame_types);
    }
    case EncoderState::kFallbackDueToFailure:
    case EncoderState::kForcedFallback:
      return fallback_encoder_->Encode(frame, frame_types);
  }
  RTC_CHECK_NOTREACHED();
}

int32_t VideoEncoderSoftwareFallbackWrapper::EncodeWithMainEncoder(
    const VideoFrame& frame,
    const std::vector<VideoFrameType>* frame_types) {
  int32_t ret = encoder_->Encode(frame, frame_types);
  // If requested, try a software fallback.
  bool fallback_requested = (ret == WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE);
  if (fallback_requested && InitFallbackEncoder(/*is_forced=*/false)) {
    // Start using the fallback with this frame.
    PrimeEncoder(current_encoder());
    if (frame.video_frame_buffer()->type() == VideoFrameBuffer::Type::kNative &&
        fallback_encoder_->GetEncoderInfo().supports_native_handle) {
      return fallback_encoder_->Encode(frame, frame_types);
    } else {
      RTC_LOG(LS_INFO) << "Fallback encoder does not support native handle - "
                          "converting frame to I420";
      rtc::scoped_refptr<I420BufferInterface> src_buffer =
          frame.video_frame_buffer()->ToI420();
      if (!src_buffer) {
        RTC_LOG(LS_ERROR) << "Failed to convert from to I420";
        return WEBRTC_VIDEO_CODEC_ENCODER_FAILURE;
      }
      rtc::scoped_refptr<VideoFrameBuffer> dst_buffer =
          src_buffer->Scale(codec_settings_.width, codec_settings_.height);
      if (!dst_buffer) {
        RTC_LOG(LS_ERROR) << "Failed to scale video frame.";
        return WEBRTC_VIDEO_CODEC_ENCODER_FAILURE;
      }
      VideoFrame scaled_frame = frame;
      scaled_frame.set_video_frame_buffer(dst_buffer);
      scaled_frame.set_update_rect(VideoFrame::UpdateRect{
          0, 0, scaled_frame.width(), scaled_frame.height()});
      return fallback_encoder_->Encode(scaled_frame, frame_types);
    }
  }
  // Fallback encoder failed too, return original error code.
  return ret;
}

void VideoEncoderSoftwareFallbackWrapper::SetRates(
    const RateControlParameters& parameters) {
  rate_control_parameters_ = parameters;
  return current_encoder()->SetRates(parameters);
}

void VideoEncoderSoftwareFallbackWrapper::OnPacketLossRateUpdate(
    float packet_loss_rate) {
  packet_loss_ = packet_loss_rate;
  current_encoder()->OnPacketLossRateUpdate(packet_loss_rate);
}

void VideoEncoderSoftwareFallbackWrapper::OnRttUpdate(int64_t rtt_ms) {
  rtt_ = rtt_ms;
  current_encoder()->OnRttUpdate(rtt_ms);
}

void VideoEncoderSoftwareFallbackWrapper::OnLossNotification(
    const LossNotification& loss_notification) {
  loss_notification_ = loss_notification;
  current_encoder()->OnLossNotification(loss_notification);
}

VideoEncoder::EncoderInfo VideoEncoderSoftwareFallbackWrapper::GetEncoderInfo()
    const {
  EncoderInfo fallback_encoder_info = fallback_encoder_->GetEncoderInfo();
  EncoderInfo default_encoder_info = encoder_->GetEncoderInfo();

  EncoderInfo info =
      IsFallbackActive() ? fallback_encoder_info : default_encoder_info;

  info.requested_resolution_alignment = cricket::LeastCommonMultiple(
      fallback_encoder_info.requested_resolution_alignment,
      default_encoder_info.requested_resolution_alignment);
  info.apply_alignment_to_all_simulcast_layers =
      fallback_encoder_info.apply_alignment_to_all_simulcast_layers ||
      default_encoder_info.apply_alignment_to_all_simulcast_layers;

  if (fallback_params_.has_value()) {
    const auto settings = (encoder_state_ == EncoderState::kForcedFallback)
                              ? fallback_encoder_info.scaling_settings
                              : default_encoder_info.scaling_settings;
    info.scaling_settings =
        settings.thresholds
            ? VideoEncoder::ScalingSettings(settings.thresholds->low,
                                            settings.thresholds->high,
                                            fallback_params_->min_pixels)
            : VideoEncoder::ScalingSettings::kOff;
  } else {
    info.scaling_settings = default_encoder_info.scaling_settings;
  }

  return info;
}

bool VideoEncoderSoftwareFallbackWrapper::IsFallbackActive() const {
  return encoder_state_ == EncoderState::kForcedFallback ||
         encoder_state_ == EncoderState::kFallbackDueToFailure;
}

bool VideoEncoderSoftwareFallbackWrapper::TryInitForcedFallbackEncoder() {
  if (!fallback_params_) {
    return false;
  }

  RTC_DCHECK_EQ(encoder_state_, EncoderState::kUninitialized);

  if (fallback_params_->SupportsResolutionBasedSwitch(codec_settings_)) {
    // Settings valid, try to instantiate software codec.
    RTC_LOG(LS_INFO) << "Request forced SW encoder fallback: "
                     << codec_settings_.width << "x" << codec_settings_.height;
    return InitFallbackEncoder(/*is_forced=*/true);
  }

  if (fallback_params_->SupportsTemporalBasedSwitch(codec_settings_)) {
    // First init main encoder to see if that supports temporal layers.
    if (encoder_->InitEncode(&codec_settings_, encoder_settings_.value()) ==
        WEBRTC_VIDEO_CODEC_OK) {
      encoder_state_ = EncoderState::kMainEncoderUsed;
    }

    if (encoder_state_ == EncoderState::kMainEncoderUsed &&
        encoder_->GetEncoderInfo().fps_allocation[0].size() != 1) {
      // Primary encoder already supports temporal layers, use that instead.
      return true;
    }

    // Try to initialize fallback and check if it supports temporal layers.
    if (fallback_encoder_->InitEncode(&codec_settings_,
                                      encoder_settings_.value()) ==
        WEBRTC_VIDEO_CODEC_OK) {
      if (fallback_encoder_->GetEncoderInfo().fps_allocation[0].size() != 1) {
        // Fallback encoder available and supports temporal layers, use it!
        if (encoder_state_ == EncoderState::kMainEncoderUsed) {
          // Main encoder initialized but does not support temporal layers,
          // release it again.
          encoder_->Release();
        }
        encoder_state_ = EncoderState::kForcedFallback;
        RTC_LOG(LS_INFO)
            << "Forced switch to SW encoder due to temporal support.";
        return true;
      } else {
        // Fallback encoder intialization succeeded, but it does not support
        // temporal layers either - release it.
        fallback_encoder_->Release();
      }
    }

    if (encoder_state_ == EncoderState::kMainEncoderUsed) {
      // Main encoder already initialized - make use of it.
      RTC_LOG(LS_INFO)
          << "Cannot fall back for temporal support since fallback that "
             "supports is not available. Using main encoder instead.";
      return true;
    }
  }

  // Neither forced fallback mode supported.
  return false;
}

}  // namespace

std::unique_ptr<VideoEncoder> CreateVideoEncoderSoftwareFallbackWrapper(
    std::unique_ptr<VideoEncoder> sw_fallback_encoder,
    std::unique_ptr<VideoEncoder> hw_encoder,
    bool prefer_temporal_support) {
  return std::make_unique<VideoEncoderSoftwareFallbackWrapper>(
      std::move(sw_fallback_encoder), std::move(hw_encoder),
      prefer_temporal_support);
}

}  // namespace webrtc

/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/adaptation/video_stream_encoder_resource_manager.h"

#include <stdio.h>

#include <algorithm>
#include <cmath>
#include <limits>
#include <memory>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/base/macros.h"
#include "api/adaptation/resource.h"
#include "api/field_trials_view.h"
#include "api/sequence_checker.h"
#include "api/task_queue/task_queue_base.h"
#include "api/video/video_adaptation_reason.h"
#include "api/video/video_source_interface.h"
#include "call/adaptation/video_source_restrictions.h"
#include "modules/video_coding/svc/scalability_mode_util.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"
#include "video/adaptation/quality_scaler_resource.h"

namespace webrtc {

const int kDefaultInputPixelsWidth = 176;
const int kDefaultInputPixelsHeight = 144;

namespace {

constexpr const char* kPixelLimitResourceFieldTrialName =
    "WebRTC-PixelLimitResource";

bool IsResolutionScalingEnabled(DegradationPreference degradation_preference) {
  return degradation_preference == DegradationPreference::MAINTAIN_FRAMERATE ||
         degradation_preference == DegradationPreference::BALANCED;
}

bool IsFramerateScalingEnabled(DegradationPreference degradation_preference) {
  return degradation_preference == DegradationPreference::MAINTAIN_RESOLUTION ||
         degradation_preference == DegradationPreference::BALANCED;
}

std::string ToString(VideoAdaptationReason reason) {
  switch (reason) {
    case VideoAdaptationReason::kQuality:
      return "quality";
    case VideoAdaptationReason::kCpu:
      return "cpu";
  }
  RTC_CHECK_NOTREACHED();
}

std::vector<bool> GetActiveLayersFlags(const VideoCodec& codec) {
  std::vector<bool> flags;
  if (codec.codecType == VideoCodecType::kVideoCodecVP9) {
    flags.resize(codec.VP9().numberOfSpatialLayers);
    for (size_t i = 0; i < flags.size(); ++i) {
      flags[i] = codec.spatialLayers[i].active;
    }
  } else {
    flags.resize(codec.numberOfSimulcastStreams);
    for (size_t i = 0; i < flags.size(); ++i) {
      flags[i] = codec.simulcastStream[i].active;
    }
  }
  return flags;
}

bool EqualFlags(const std::vector<bool>& a, const std::vector<bool>& b) {
  if (a.size() != b.size())
    return false;
  return std::equal(a.begin(), a.end(), b.begin());
}

absl::optional<DataRate> GetSingleActiveLayerMaxBitrate(
    const VideoCodec& codec) {
  int num_active = 0;
  absl::optional<DataRate> max_bitrate;
  if (codec.codecType == VideoCodecType::kVideoCodecVP9) {
    for (int i = 0; i < codec.VP9().numberOfSpatialLayers; ++i) {
      if (codec.spatialLayers[i].active) {
        ++num_active;
        max_bitrate =
            DataRate::KilobitsPerSec(codec.spatialLayers[i].maxBitrate);
      }
    }
  } else {
    for (int i = 0; i < codec.numberOfSimulcastStreams; ++i) {
      if (codec.simulcastStream[i].active) {
        ++num_active;
        max_bitrate =
            DataRate::KilobitsPerSec(codec.simulcastStream[i].maxBitrate);
      }
    }
  }
  return (num_active > 1) ? absl::nullopt : max_bitrate;
}

}  // namespace

class VideoStreamEncoderResourceManager::InitialFrameDropper {
 public:
  explicit InitialFrameDropper(
      rtc::scoped_refptr<QualityScalerResource> quality_scaler_resource,
      const FieldTrialsView& field_trials)
      : quality_scaler_resource_(quality_scaler_resource),
        quality_scaler_settings_(field_trials),
        has_seen_first_bwe_drop_(false),
        set_start_bitrate_(DataRate::Zero()),
        set_start_bitrate_time_ms_(0),
        initial_framedrop_(0),
        use_bandwidth_allocation_(false),
        bandwidth_allocation_(DataRate::Zero()),
        last_input_width_(0),
        last_input_height_(0),
        last_stream_configuration_changed_(false) {
    RTC_DCHECK(quality_scaler_resource_);
  }

  // Output signal.
  bool DropInitialFrames() const {
    return initial_framedrop_ < kMaxInitialFramedrop;
  }

  absl::optional<uint32_t> single_active_stream_pixels() const {
    return single_active_stream_pixels_;
  }

  absl::optional<uint32_t> UseBandwidthAllocationBps() const {
    return (use_bandwidth_allocation_ &&
            bandwidth_allocation_ > DataRate::Zero())
               ? absl::optional<uint32_t>(bandwidth_allocation_.bps())
               : absl::nullopt;
  }

  bool last_stream_configuration_changed() const {
    return last_stream_configuration_changed_;
  }

  // Input signals.
  void SetStartBitrate(DataRate start_bitrate, int64_t now_ms) {
    set_start_bitrate_ = start_bitrate;
    set_start_bitrate_time_ms_ = now_ms;
  }

  void SetBandwidthAllocation(DataRate bandwidth_allocation) {
    bandwidth_allocation_ = bandwidth_allocation;
  }

  void SetTargetBitrate(DataRate target_bitrate, int64_t now_ms) {
    if (set_start_bitrate_ > DataRate::Zero() && !has_seen_first_bwe_drop_ &&
        quality_scaler_resource_->is_started() &&
        quality_scaler_settings_.InitialBitrateIntervalMs() &&
        quality_scaler_settings_.InitialBitrateFactor()) {
      int64_t diff_ms = now_ms - set_start_bitrate_time_ms_;
      if (diff_ms <
              quality_scaler_settings_.InitialBitrateIntervalMs().value() &&
          (target_bitrate <
           (set_start_bitrate_ *
            quality_scaler_settings_.InitialBitrateFactor().value()))) {
        RTC_LOG(LS_INFO) << "Reset initial_framedrop_. Start bitrate: "
                         << set_start_bitrate_.bps()
                         << ", target bitrate: " << target_bitrate.bps();
        initial_framedrop_ = 0;
        has_seen_first_bwe_drop_ = true;
      }
    }
  }

  void OnEncoderSettingsUpdated(
      const VideoCodec& codec,
      const VideoAdaptationCounters& adaptation_counters) {
    last_stream_configuration_changed_ = false;
    std::vector<bool> active_flags = GetActiveLayersFlags(codec);
    // Check if the source resolution has changed for the external reasons,
    // i.e. without any adaptation from WebRTC.
    const bool source_resolution_changed =
        (last_input_width_ != codec.width ||
         last_input_height_ != codec.height) &&
        adaptation_counters.resolution_adaptations ==
            last_adaptation_counters_.resolution_adaptations;
    if (!EqualFlags(active_flags, last_active_flags_) ||
        source_resolution_changed) {
      // Streams configuration has changed.
      last_stream_configuration_changed_ = true;
      // Initial frame drop must be enabled because BWE might be way too low
      // for the selected resolution.
      if (quality_scaler_resource_->is_started()) {
        RTC_LOG(LS_INFO) << "Resetting initial_framedrop_ due to changed "
                            "stream parameters";
        initial_framedrop_ = 0;
        if (single_active_stream_pixels_ &&
            VideoStreamAdapter::GetSingleActiveLayerPixels(codec) >
                *single_active_stream_pixels_) {
          // Resolution increased.
          use_bandwidth_allocation_ = true;
        }
      }
    }
    last_adaptation_counters_ = adaptation_counters;
    last_active_flags_ = active_flags;
    last_input_width_ = codec.width;
    last_input_height_ = codec.height;
    single_active_stream_pixels_ =
        VideoStreamAdapter::GetSingleActiveLayerPixels(codec);
  }

  void OnFrameDroppedDueToSize() { ++initial_framedrop_; }

  void Disable() {
    initial_framedrop_ = kMaxInitialFramedrop;
    use_bandwidth_allocation_ = false;
  }

  void OnQualityScalerSettingsUpdated() {
    if (quality_scaler_resource_->is_started()) {
      // Restart frame drops due to size.
      initial_framedrop_ = 0;
    } else {
      // Quality scaling disabled so we shouldn't drop initial frames.
      Disable();
    }
  }

 private:
  // The maximum number of frames to drop at beginning of stream to try and
  // achieve desired bitrate.
  static const int kMaxInitialFramedrop = 4;

  const rtc::scoped_refptr<QualityScalerResource> quality_scaler_resource_;
  const QualityScalerSettings quality_scaler_settings_;
  bool has_seen_first_bwe_drop_;
  DataRate set_start_bitrate_;
  int64_t set_start_bitrate_time_ms_;
  // Counts how many frames we've dropped in the initial framedrop phase.
  int initial_framedrop_;
  absl::optional<uint32_t> single_active_stream_pixels_;
  bool use_bandwidth_allocation_;
  DataRate bandwidth_allocation_;

  std::vector<bool> last_active_flags_;
  VideoAdaptationCounters last_adaptation_counters_;
  int last_input_width_;
  int last_input_height_;
  bool last_stream_configuration_changed_;
};

VideoStreamEncoderResourceManager::VideoStreamEncoderResourceManager(
    VideoStreamInputStateProvider* input_state_provider,
    VideoStreamEncoderObserver* encoder_stats_observer,
    Clock* clock,
    bool experiment_cpu_load_estimator,
    std::unique_ptr<OveruseFrameDetector> overuse_detector,
    DegradationPreferenceProvider* degradation_preference_provider,
    const FieldTrialsView& field_trials)
    : field_trials_(field_trials),
      degradation_preference_provider_(degradation_preference_provider),
      bitrate_constraint_(std::make_unique<BitrateConstraint>()),
      balanced_constraint_(
          std::make_unique<BalancedConstraint>(degradation_preference_provider_,
                                               field_trials)),
      encode_usage_resource_(
          EncodeUsageResource::Create(std::move(overuse_detector))),
      quality_scaler_resource_(QualityScalerResource::Create()),
      pixel_limit_resource_(nullptr),
      bandwidth_quality_scaler_resource_(
          BandwidthQualityScalerResource::Create()),
      encoder_queue_(nullptr),
      input_state_provider_(input_state_provider),
      adaptation_processor_(nullptr),
      encoder_stats_observer_(encoder_stats_observer),
      degradation_preference_(DegradationPreference::DISABLED),
      video_source_restrictions_(),
      balanced_settings_(field_trials),
      clock_(clock),
      experiment_cpu_load_estimator_(experiment_cpu_load_estimator),
      initial_frame_dropper_(
          std::make_unique<InitialFrameDropper>(quality_scaler_resource_,
                                                field_trials)),
      quality_scaling_experiment_enabled_(
          QualityScalingExperiment::Enabled(field_trials_)),
      pixel_limit_resource_experiment_enabled_(
          field_trials.IsEnabled(kPixelLimitResourceFieldTrialName)),
      encoder_target_bitrate_bps_(absl::nullopt),
      quality_rampup_experiment_(
          QualityRampUpExperimentHelper::CreateIfEnabled(this, clock_)),
      encoder_settings_(absl::nullopt) {
  TRACE_EVENT0(
      "webrtc",
      "VideoStreamEncoderResourceManager::VideoStreamEncoderResourceManager");
  RTC_CHECK(degradation_preference_provider_);
  RTC_CHECK(encoder_stats_observer_);
}

VideoStreamEncoderResourceManager::~VideoStreamEncoderResourceManager() =
    default;

void VideoStreamEncoderResourceManager::Initialize(
    TaskQueueBase* encoder_queue) {
  RTC_DCHECK(!encoder_queue_);
  RTC_DCHECK(encoder_queue);
  encoder_queue_ = encoder_queue;
  encode_usage_resource_->RegisterEncoderTaskQueue(encoder_queue_);
  quality_scaler_resource_->RegisterEncoderTaskQueue(encoder_queue_);
  bandwidth_quality_scaler_resource_->RegisterEncoderTaskQueue(encoder_queue_);
}

void VideoStreamEncoderResourceManager::SetAdaptationProcessor(
    ResourceAdaptationProcessorInterface* adaptation_processor,
    VideoStreamAdapter* stream_adapter) {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  adaptation_processor_ = adaptation_processor;
  stream_adapter_ = stream_adapter;
}

void VideoStreamEncoderResourceManager::SetDegradationPreferences(
    DegradationPreference degradation_preference) {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  degradation_preference_ = degradation_preference;
  UpdateStatsAdaptationSettings();
}

DegradationPreference
VideoStreamEncoderResourceManager::degradation_preference() const {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  return degradation_preference_;
}

void VideoStreamEncoderResourceManager::ConfigureEncodeUsageResource() {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  RTC_DCHECK(encoder_settings_.has_value());
  if (encode_usage_resource_->is_started()) {
    encode_usage_resource_->StopCheckForOveruse();
  } else {
    // If the resource has not yet started then it needs to be added.
    AddResource(encode_usage_resource_, VideoAdaptationReason::kCpu);
  }
  encode_usage_resource_->StartCheckForOveruse(GetCpuOveruseOptions());
}

void VideoStreamEncoderResourceManager::MaybeInitializePixelLimitResource() {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  RTC_DCHECK(adaptation_processor_);
  RTC_DCHECK(!pixel_limit_resource_);
  if (!pixel_limit_resource_experiment_enabled_) {
    // The field trial is not running.
    return;
  }
  int max_pixels = 0;
  std::string pixel_limit_field_trial =
      field_trials_.Lookup(kPixelLimitResourceFieldTrialName);
  if (sscanf(pixel_limit_field_trial.c_str(), "Enabled-%d", &max_pixels) != 1) {
    RTC_LOG(LS_ERROR) << "Couldn't parse " << kPixelLimitResourceFieldTrialName
                      << " trial config: " << pixel_limit_field_trial;
    return;
  }
  RTC_LOG(LS_INFO) << "Running field trial "
                   << kPixelLimitResourceFieldTrialName << " configured to "
                   << max_pixels << " max pixels";
  // Configure the specified max pixels from the field trial. The pixel limit
  // resource is active for the lifetme of the stream (until
  // StopManagedResources() is called).
  pixel_limit_resource_ =
      PixelLimitResource::Create(encoder_queue_, input_state_provider_);
  pixel_limit_resource_->SetMaxPixels(max_pixels);
  AddResource(pixel_limit_resource_, VideoAdaptationReason::kCpu);
}

void VideoStreamEncoderResourceManager::StopManagedResources() {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  RTC_DCHECK(adaptation_processor_);
  if (encode_usage_resource_->is_started()) {
    encode_usage_resource_->StopCheckForOveruse();
    RemoveResource(encode_usage_resource_);
  }
  if (quality_scaler_resource_->is_started()) {
    quality_scaler_resource_->StopCheckForOveruse();
    RemoveResource(quality_scaler_resource_);
  }
  if (pixel_limit_resource_) {
    RemoveResource(pixel_limit_resource_);
    pixel_limit_resource_ = nullptr;
  }
  if (bandwidth_quality_scaler_resource_->is_started()) {
    bandwidth_quality_scaler_resource_->StopCheckForOveruse();
    RemoveResource(bandwidth_quality_scaler_resource_);
  }
}

void VideoStreamEncoderResourceManager::AddResource(
    rtc::scoped_refptr<Resource> resource,
    VideoAdaptationReason reason) {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  RTC_DCHECK(resource);
  bool inserted;
  std::tie(std::ignore, inserted) = resources_.emplace(resource, reason);
  RTC_DCHECK(inserted) << "Resource " << resource->Name()
                       << " already was inserted";
  adaptation_processor_->AddResource(resource);
}

void VideoStreamEncoderResourceManager::RemoveResource(
    rtc::scoped_refptr<Resource> resource) {
  {
    RTC_DCHECK_RUN_ON(encoder_queue_);
    RTC_DCHECK(resource);
    const auto& it = resources_.find(resource);
    RTC_DCHECK(it != resources_.end())
        << "Resource \"" << resource->Name() << "\" not found.";
    resources_.erase(it);
  }
  adaptation_processor_->RemoveResource(resource);
}

std::vector<AdaptationConstraint*>
VideoStreamEncoderResourceManager::AdaptationConstraints() const {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  return {bitrate_constraint_.get(), balanced_constraint_.get()};
}

void VideoStreamEncoderResourceManager::SetEncoderSettings(
    EncoderSettings encoder_settings) {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  encoder_settings_ = std::move(encoder_settings);
  bitrate_constraint_->OnEncoderSettingsUpdated(encoder_settings_);
  initial_frame_dropper_->OnEncoderSettingsUpdated(
      encoder_settings_->video_codec(), current_adaptation_counters_);
  MaybeUpdateTargetFrameRate();
  if (quality_rampup_experiment_) {
    quality_rampup_experiment_->ConfigureQualityRampupExperiment(
        initial_frame_dropper_->last_stream_configuration_changed(),
        initial_frame_dropper_->single_active_stream_pixels(),
        GetSingleActiveLayerMaxBitrate(encoder_settings_->video_codec()));
  }
}

void VideoStreamEncoderResourceManager::SetStartBitrate(
    DataRate start_bitrate) {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  if (!start_bitrate.IsZero()) {
    encoder_target_bitrate_bps_ = start_bitrate.bps();
    bitrate_constraint_->OnEncoderTargetBitrateUpdated(
        encoder_target_bitrate_bps_);
    balanced_constraint_->OnEncoderTargetBitrateUpdated(
        encoder_target_bitrate_bps_);
  }
  initial_frame_dropper_->SetStartBitrate(start_bitrate,
                                          clock_->TimeInMicroseconds());
}

void VideoStreamEncoderResourceManager::SetTargetBitrate(
    DataRate target_bitrate) {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  if (!target_bitrate.IsZero()) {
    encoder_target_bitrate_bps_ = target_bitrate.bps();
    bitrate_constraint_->OnEncoderTargetBitrateUpdated(
        encoder_target_bitrate_bps_);
    balanced_constraint_->OnEncoderTargetBitrateUpdated(
        encoder_target_bitrate_bps_);
  }
  initial_frame_dropper_->SetTargetBitrate(target_bitrate,
                                           clock_->TimeInMilliseconds());
}

void VideoStreamEncoderResourceManager::SetEncoderRates(
    const VideoEncoder::RateControlParameters& encoder_rates) {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  encoder_rates_ = encoder_rates;
  initial_frame_dropper_->SetBandwidthAllocation(
      encoder_rates.bandwidth_allocation);
}

void VideoStreamEncoderResourceManager::OnFrameDroppedDueToSize() {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  initial_frame_dropper_->OnFrameDroppedDueToSize();
  Adaptation reduce_resolution = stream_adapter_->GetAdaptDownResolution();
  if (reduce_resolution.status() == Adaptation::Status::kValid) {
    stream_adapter_->ApplyAdaptation(reduce_resolution,
                                     quality_scaler_resource_);
  }
}

void VideoStreamEncoderResourceManager::OnEncodeStarted(
    const VideoFrame& cropped_frame,
    int64_t time_when_first_seen_us) {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  encode_usage_resource_->OnEncodeStarted(cropped_frame,
                                          time_when_first_seen_us);
}

void VideoStreamEncoderResourceManager::OnEncodeCompleted(
    const EncodedImage& encoded_image,
    int64_t time_sent_in_us,
    absl::optional<int> encode_duration_us,
    DataSize frame_size) {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  // Inform `encode_usage_resource_` of the encode completed event.
  uint32_t timestamp = encoded_image.RtpTimestamp();
  int64_t capture_time_us =
      encoded_image.capture_time_ms_ * rtc::kNumMicrosecsPerMillisec;
  encode_usage_resource_->OnEncodeCompleted(
      timestamp, time_sent_in_us, capture_time_us, encode_duration_us);
  quality_scaler_resource_->OnEncodeCompleted(encoded_image, time_sent_in_us);
  bandwidth_quality_scaler_resource_->OnEncodeCompleted(
      encoded_image, time_sent_in_us, frame_size.bytes());
}

void VideoStreamEncoderResourceManager::OnFrameDropped(
    EncodedImageCallback::DropReason reason) {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  quality_scaler_resource_->OnFrameDropped(reason);
}

bool VideoStreamEncoderResourceManager::DropInitialFrames() const {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  return initial_frame_dropper_->DropInitialFrames();
}

absl::optional<uint32_t>
VideoStreamEncoderResourceManager::SingleActiveStreamPixels() const {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  return initial_frame_dropper_->single_active_stream_pixels();
}

absl::optional<uint32_t>
VideoStreamEncoderResourceManager::UseBandwidthAllocationBps() const {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  return initial_frame_dropper_->UseBandwidthAllocationBps();
}

void VideoStreamEncoderResourceManager::OnMaybeEncodeFrame() {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  initial_frame_dropper_->Disable();
  if (quality_rampup_experiment_ && quality_scaler_resource_->is_started()) {
    DataRate bandwidth = encoder_rates_.has_value()
                             ? encoder_rates_->bandwidth_allocation
                             : DataRate::Zero();
    quality_rampup_experiment_->PerformQualityRampupExperiment(
        quality_scaler_resource_, bandwidth,
        DataRate::BitsPerSec(encoder_target_bitrate_bps_.value_or(0)),
        GetSingleActiveLayerMaxBitrate(encoder_settings_->video_codec()));
  }
}

void VideoStreamEncoderResourceManager::UpdateQualityScalerSettings(
    absl::optional<VideoEncoder::QpThresholds> qp_thresholds) {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  if (qp_thresholds.has_value()) {
    if (quality_scaler_resource_->is_started()) {
      quality_scaler_resource_->SetQpThresholds(qp_thresholds.value());
    } else {
      quality_scaler_resource_->StartCheckForOveruse(qp_thresholds.value(),
                                                     field_trials_);
      AddResource(quality_scaler_resource_, VideoAdaptationReason::kQuality);
    }
  } else if (quality_scaler_resource_->is_started()) {
    quality_scaler_resource_->StopCheckForOveruse();
    RemoveResource(quality_scaler_resource_);
  }
  initial_frame_dropper_->OnQualityScalerSettingsUpdated();
}

void VideoStreamEncoderResourceManager::UpdateBandwidthQualityScalerSettings(
    bool bandwidth_quality_scaling_allowed,
    const std::vector<VideoEncoder::ResolutionBitrateLimits>&
        resolution_bitrate_limits) {
  RTC_DCHECK_RUN_ON(encoder_queue_);

  if (!bandwidth_quality_scaling_allowed) {
    if (bandwidth_quality_scaler_resource_->is_started()) {
      bandwidth_quality_scaler_resource_->StopCheckForOveruse();
      RemoveResource(bandwidth_quality_scaler_resource_);
    }
  } else {
    if (!bandwidth_quality_scaler_resource_->is_started()) {
      // Before executing "StartCheckForOveruse",we must execute "AddResource"
      // firstly,because it can make the listener valid.
      AddResource(bandwidth_quality_scaler_resource_,
                  webrtc::VideoAdaptationReason::kQuality);
      bandwidth_quality_scaler_resource_->StartCheckForOveruse(
          resolution_bitrate_limits);
    }
  }
}

void VideoStreamEncoderResourceManager::ConfigureQualityScaler(
    const VideoEncoder::EncoderInfo& encoder_info) {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  const auto scaling_settings = encoder_info.scaling_settings;
  const bool quality_scaling_allowed =
      IsResolutionScalingEnabled(degradation_preference_) &&
      (scaling_settings.thresholds.has_value() ||
       (encoder_settings_.has_value() &&
        encoder_settings_->encoder_config().is_quality_scaling_allowed)) &&
      encoder_info.is_qp_trusted.value_or(true);

  // TODO(https://crbug.com/webrtc/11222): Should this move to
  // QualityScalerResource?
  if (quality_scaling_allowed) {
    if (!quality_scaler_resource_->is_started()) {
      // Quality scaler has not already been configured.

      // Use experimental thresholds if available.
      absl::optional<VideoEncoder::QpThresholds> experimental_thresholds;
      if (quality_scaling_experiment_enabled_) {
        experimental_thresholds = QualityScalingExperiment::GetQpThresholds(
            GetVideoCodecTypeOrGeneric(encoder_settings_), field_trials_);
      }
      UpdateQualityScalerSettings(experimental_thresholds.has_value()
                                      ? experimental_thresholds
                                      : scaling_settings.thresholds);
    }
  } else {
    UpdateQualityScalerSettings(absl::nullopt);
  }

  // Set the qp-thresholds to the balanced settings if balanced mode.
  if (degradation_preference_ == DegradationPreference::BALANCED &&
      quality_scaler_resource_->is_started()) {
    absl::optional<VideoEncoder::QpThresholds> thresholds =
        balanced_settings_.GetQpThresholds(
            GetVideoCodecTypeOrGeneric(encoder_settings_),
            LastFrameSizeOrDefault());
    if (thresholds) {
      quality_scaler_resource_->SetQpThresholds(*thresholds);
    }
  }
  UpdateStatsAdaptationSettings();
}

void VideoStreamEncoderResourceManager::ConfigureBandwidthQualityScaler(
    const VideoEncoder::EncoderInfo& encoder_info) {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  const bool bandwidth_quality_scaling_allowed =
      IsResolutionScalingEnabled(degradation_preference_) &&
      (encoder_settings_.has_value() &&
       encoder_settings_->encoder_config().is_quality_scaling_allowed) &&
      !encoder_info.is_qp_trusted.value_or(true);

  UpdateBandwidthQualityScalerSettings(bandwidth_quality_scaling_allowed,
                                       encoder_info.resolution_bitrate_limits);
  UpdateStatsAdaptationSettings();
}

VideoAdaptationReason VideoStreamEncoderResourceManager::GetReasonFromResource(
    rtc::scoped_refptr<Resource> resource) const {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  const auto& registered_resource = resources_.find(resource);
  RTC_DCHECK(registered_resource != resources_.end())
      << resource->Name() << " not found.";
  return registered_resource->second;
}

// TODO(pbos): Lower these thresholds (to closer to 100%) when we handle
// pipelining encoders better (multiple input frames before something comes
// out). This should effectively turn off CPU adaptations for systems that
// remotely cope with the load right now.
CpuOveruseOptions VideoStreamEncoderResourceManager::GetCpuOveruseOptions()
    const {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  // This is already ensured by the only caller of this method:
  // StartResourceAdaptation().
  RTC_DCHECK(encoder_settings_.has_value());
  CpuOveruseOptions options;
  // Hardware accelerated encoders are assumed to be pipelined; give them
  // additional overuse time.
  if (encoder_settings_->encoder_info().is_hardware_accelerated) {
    options.low_encode_usage_threshold_percent = 150;
    options.high_encode_usage_threshold_percent = 200;
  }
  if (experiment_cpu_load_estimator_) {
    options.filter_time_ms = 5 * rtc::kNumMillisecsPerSec;
  }
  return options;
}

int VideoStreamEncoderResourceManager::LastFrameSizeOrDefault() const {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  return input_state_provider_->InputState()
      .single_active_stream_pixels()
      .value_or(
          input_state_provider_->InputState().frame_size_pixels().value_or(
              kDefaultInputPixelsWidth * kDefaultInputPixelsHeight));
}

void VideoStreamEncoderResourceManager::OnVideoSourceRestrictionsUpdated(
    VideoSourceRestrictions restrictions,
    const VideoAdaptationCounters& adaptation_counters,
    rtc::scoped_refptr<Resource> reason,
    const VideoSourceRestrictions& unfiltered_restrictions) {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  current_adaptation_counters_ = adaptation_counters;

  // TODO(bugs.webrtc.org/11553) Remove reason parameter and add reset callback.
  if (!reason && adaptation_counters.Total() == 0) {
    // Adaptation was manually reset - clear the per-reason counters too.
    encoder_stats_observer_->ClearAdaptationStats();
  }

  video_source_restrictions_ = FilterRestrictionsByDegradationPreference(
      restrictions, degradation_preference_);
  MaybeUpdateTargetFrameRate();
}

void VideoStreamEncoderResourceManager::OnResourceLimitationChanged(
    rtc::scoped_refptr<Resource> resource,
    const std::map<rtc::scoped_refptr<Resource>, VideoAdaptationCounters>&
        resource_limitations) {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  if (!resource) {
    encoder_stats_observer_->ClearAdaptationStats();
    return;
  }

  std::map<VideoAdaptationReason, VideoAdaptationCounters> limitations;
  for (auto& resource_counter : resource_limitations) {
    std::map<VideoAdaptationReason, VideoAdaptationCounters>::iterator it;
    bool inserted;
    std::tie(it, inserted) = limitations.emplace(
        GetReasonFromResource(resource_counter.first), resource_counter.second);
    if (!inserted && it->second.Total() < resource_counter.second.Total()) {
      it->second = resource_counter.second;
    }
  }

  VideoAdaptationReason adaptation_reason = GetReasonFromResource(resource);
  encoder_stats_observer_->OnAdaptationChanged(
      adaptation_reason, limitations[VideoAdaptationReason::kCpu],
      limitations[VideoAdaptationReason::kQuality]);

  if (quality_rampup_experiment_) {
    bool cpu_limited = limitations.at(VideoAdaptationReason::kCpu).Total() > 0;
    auto qp_resolution_adaptations =
        limitations.at(VideoAdaptationReason::kQuality).resolution_adaptations;
    quality_rampup_experiment_->cpu_adapted(cpu_limited);
    quality_rampup_experiment_->qp_resolution_adaptations(
        qp_resolution_adaptations);
  }

  RTC_LOG(LS_INFO) << ActiveCountsToString(limitations);
}

void VideoStreamEncoderResourceManager::MaybeUpdateTargetFrameRate() {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  absl::optional<double> codec_max_frame_rate =
      encoder_settings_.has_value()
          ? absl::optional<double>(
                encoder_settings_->video_codec().maxFramerate)
          : absl::nullopt;
  // The current target framerate is the maximum frame rate as specified by
  // the current codec configuration or any limit imposed by the adaptation
  // module. This is used to make sure overuse detection doesn't needlessly
  // trigger in low and/or variable framerate scenarios.
  absl::optional<double> target_frame_rate =
      video_source_restrictions_.max_frame_rate();
  if (!target_frame_rate.has_value() ||
      (codec_max_frame_rate.has_value() &&
       codec_max_frame_rate.value() < target_frame_rate.value())) {
    target_frame_rate = codec_max_frame_rate;
  }
  encode_usage_resource_->SetTargetFrameRate(target_frame_rate);
}

void VideoStreamEncoderResourceManager::UpdateStatsAdaptationSettings() const {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  VideoStreamEncoderObserver::AdaptationSettings cpu_settings(
      IsResolutionScalingEnabled(degradation_preference_),
      IsFramerateScalingEnabled(degradation_preference_));

  VideoStreamEncoderObserver::AdaptationSettings quality_settings =
      (quality_scaler_resource_->is_started() ||
       bandwidth_quality_scaler_resource_->is_started())
          ? cpu_settings
          : VideoStreamEncoderObserver::AdaptationSettings();
  encoder_stats_observer_->UpdateAdaptationSettings(cpu_settings,
                                                    quality_settings);
}

// static
std::string VideoStreamEncoderResourceManager::ActiveCountsToString(
    const std::map<VideoAdaptationReason, VideoAdaptationCounters>&
        active_counts) {
  rtc::StringBuilder ss;

  ss << "Downgrade counts: fps: {";
  for (auto& reason_count : active_counts) {
    ss << ToString(reason_count.first) << ":";
    ss << reason_count.second.fps_adaptations;
  }
  ss << "}, resolution {";
  for (auto& reason_count : active_counts) {
    ss << ToString(reason_count.first) << ":";
    ss << reason_count.second.resolution_adaptations;
  }
  ss << "}";

  return ss.Release();
}

void VideoStreamEncoderResourceManager::OnQualityRampUp() {
  RTC_DCHECK_RUN_ON(encoder_queue_);
  stream_adapter_->ClearRestrictions();
  quality_rampup_experiment_.reset();
}

bool VideoStreamEncoderResourceManager::IsSimulcastOrMultipleSpatialLayers(
    const VideoEncoderConfig& encoder_config,
    const VideoCodec& video_codec) {
  const std::vector<VideoStream>& simulcast_layers =
      encoder_config.simulcast_layers;
  if (simulcast_layers.empty()) {
    return false;
  }

  absl::optional<int> num_spatial_layers;
  if (simulcast_layers[0].scalability_mode.has_value() &&
      video_codec.numberOfSimulcastStreams == 1) {
    num_spatial_layers = ScalabilityModeToNumSpatialLayers(
        *simulcast_layers[0].scalability_mode);
  }

  if (simulcast_layers.size() == 1) {
    // Check if multiple spatial layers are used.
    return num_spatial_layers && *num_spatial_layers > 1;
  }

  bool svc_with_one_spatial_layer =
      num_spatial_layers && *num_spatial_layers == 1;
  if (simulcast_layers[0].active && !svc_with_one_spatial_layer) {
    // We can't distinguish between simulcast and singlecast when only the
    // lowest spatial layer is active. Treat this case as simulcast.
    return true;
  }

  int num_active_layers =
      std::count_if(simulcast_layers.begin(), simulcast_layers.end(),
                    [](const VideoStream& layer) { return layer.active; });
  return num_active_layers > 1;
}

}  // namespace webrtc

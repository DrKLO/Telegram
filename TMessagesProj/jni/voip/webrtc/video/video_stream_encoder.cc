/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/video_stream_encoder.h"

#include <algorithm>
#include <array>
#include <limits>
#include <memory>
#include <numeric>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/cleanup/cleanup.h"
#include "absl/types/optional.h"
#include "api/field_trials_view.h"
#include "api/sequence_checker.h"
#include "api/task_queue/task_queue_base.h"
#include "api/video/encoded_image.h"
#include "api/video/i420_buffer.h"
#include "api/video/render_resolution.h"
#include "api/video/video_adaptation_reason.h"
#include "api/video/video_bitrate_allocator_factory.h"
#include "api/video/video_codec_constants.h"
#include "api/video/video_layers_allocation.h"
#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_encoder.h"
#include "call/adaptation/resource_adaptation_processor.h"
#include "call/adaptation/video_source_restrictions.h"
#include "call/adaptation/video_stream_adapter.h"
#include "modules/video_coding/include/video_codec_initializer.h"
#include "modules/video_coding/svc/svc_rate_allocator.h"
#include "modules/video_coding/utility/vp8_constants.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/event.h"
#include "rtc_base/experiments/alr_experiment.h"
#include "rtc_base/experiments/encoder_info_settings.h"
#include "rtc_base/experiments/rate_control_settings.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/system/no_unique_address.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/metrics.h"
#include "video/adaptation/video_stream_encoder_resource_manager.h"
#include "video/alignment_adjuster.h"
#include "video/config/encoder_stream_factory.h"
#include "video/frame_cadence_adapter.h"

namespace webrtc {

namespace {

// Time interval for logging frame counts.
const int64_t kFrameLogIntervalMs = 60000;

// Time to keep a single cached pending frame in paused state.
const int64_t kPendingFrameTimeoutMs = 1000;

constexpr char kFrameDropperFieldTrial[] = "WebRTC-FrameDropper";

// TODO(bugs.webrtc.org/13572): Remove this kill switch after deploying the
// feature.
constexpr char kSwitchEncoderOnInitializationFailuresFieldTrial[] =
    "WebRTC-SwitchEncoderOnInitializationFailures";

const size_t kDefaultPayloadSize = 1440;

const int64_t kParameterUpdateIntervalMs = 1000;

// Animation is capped to 720p.
constexpr int kMaxAnimationPixels = 1280 * 720;

constexpr int kDefaultMinScreenSharebps = 1200000;

bool RequiresEncoderReset(const VideoCodec& prev_send_codec,
                          const VideoCodec& new_send_codec,
                          bool was_encode_called_since_last_initialization) {
  // Does not check max/minBitrate or maxFramerate.
  if (new_send_codec.codecType != prev_send_codec.codecType ||
      new_send_codec.width != prev_send_codec.width ||
      new_send_codec.height != prev_send_codec.height ||
      new_send_codec.qpMax != prev_send_codec.qpMax ||
      new_send_codec.numberOfSimulcastStreams !=
          prev_send_codec.numberOfSimulcastStreams ||
      new_send_codec.mode != prev_send_codec.mode ||
      new_send_codec.GetFrameDropEnabled() !=
          prev_send_codec.GetFrameDropEnabled()) {
    return true;
  }

  if (!was_encode_called_since_last_initialization &&
      (new_send_codec.startBitrate != prev_send_codec.startBitrate)) {
    // If start bitrate has changed reconfigure encoder only if encoding had not
    // yet started.
    return true;
  }

  switch (new_send_codec.codecType) {
    case kVideoCodecVP8:
      if (new_send_codec.VP8() != prev_send_codec.VP8()) {
        return true;
      }
      break;

    case kVideoCodecVP9:
      if (new_send_codec.VP9() != prev_send_codec.VP9()) {
        return true;
      }
      break;

    case kVideoCodecH264:
      if (new_send_codec.H264() != prev_send_codec.H264()) {
        return true;
      }
      break;
#ifndef DISABLE_H265
    case kVideoCodecH265:
      if (new_send_codec.H265() != prev_send_codec.H265()) {
        return true;
      }
      break;
#endif

    default:
      break;
  }

  for (unsigned char i = 0; i < new_send_codec.numberOfSimulcastStreams; ++i) {
    if (!new_send_codec.simulcastStream[i].active) {
      // No need to reset when stream is inactive.
      continue;
    }

    if (!prev_send_codec.simulcastStream[i].active ||
        new_send_codec.simulcastStream[i].width !=
            prev_send_codec.simulcastStream[i].width ||
        new_send_codec.simulcastStream[i].height !=
            prev_send_codec.simulcastStream[i].height ||
        new_send_codec.simulcastStream[i].numberOfTemporalLayers !=
            prev_send_codec.simulcastStream[i].numberOfTemporalLayers ||
        new_send_codec.simulcastStream[i].qpMax !=
            prev_send_codec.simulcastStream[i].qpMax) {
      return true;
    }
  }

  if (new_send_codec.codecType == kVideoCodecVP9) {
    size_t num_spatial_layers = new_send_codec.VP9().numberOfSpatialLayers;
    for (unsigned char i = 0; i < num_spatial_layers; ++i) {
      if (new_send_codec.spatialLayers[i].width !=
              prev_send_codec.spatialLayers[i].width ||
          new_send_codec.spatialLayers[i].height !=
              prev_send_codec.spatialLayers[i].height ||
          new_send_codec.spatialLayers[i].numberOfTemporalLayers !=
              prev_send_codec.spatialLayers[i].numberOfTemporalLayers ||
          new_send_codec.spatialLayers[i].qpMax !=
              prev_send_codec.spatialLayers[i].qpMax) {
        return true;
      }
    }
  }

  if (new_send_codec.GetScalabilityMode() !=
      prev_send_codec.GetScalabilityMode()) {
    return true;
  }

  return false;
}

std::array<uint8_t, 2> GetExperimentGroups() {
  std::array<uint8_t, 2> experiment_groups;
  absl::optional<AlrExperimentSettings> experiment_settings =
      AlrExperimentSettings::CreateFromFieldTrial(
          AlrExperimentSettings::kStrictPacingAndProbingExperimentName);
  if (experiment_settings) {
    experiment_groups[0] = experiment_settings->group_id + 1;
  } else {
    experiment_groups[0] = 0;
  }
  experiment_settings = AlrExperimentSettings::CreateFromFieldTrial(
      AlrExperimentSettings::kScreenshareProbingBweExperimentName);
  if (experiment_settings) {
    experiment_groups[1] = experiment_settings->group_id + 1;
  } else {
    experiment_groups[1] = 0;
  }
  return experiment_groups;
}

// Limit allocation across TLs in bitrate allocation according to number of TLs
// in EncoderInfo.
VideoBitrateAllocation UpdateAllocationFromEncoderInfo(
    const VideoBitrateAllocation& allocation,
    const VideoEncoder::EncoderInfo& encoder_info) {
  if (allocation.get_sum_bps() == 0) {
    return allocation;
  }
  VideoBitrateAllocation new_allocation;
  for (int si = 0; si < kMaxSpatialLayers; ++si) {
    if (encoder_info.fps_allocation[si].size() == 1 &&
        allocation.IsSpatialLayerUsed(si)) {
      // One TL is signalled to be used by the encoder. Do not distribute
      // bitrate allocation across TLs (use sum at ti:0).
      new_allocation.SetBitrate(si, 0, allocation.GetSpatialLayerSum(si));
    } else {
      for (int ti = 0; ti < kMaxTemporalStreams; ++ti) {
        if (allocation.HasBitrate(si, ti))
          new_allocation.SetBitrate(si, ti, allocation.GetBitrate(si, ti));
      }
    }
  }
  new_allocation.set_bw_limited(allocation.is_bw_limited());
  return new_allocation;
}

// Converts a VideoBitrateAllocation that contains allocated bitrate per layer,
// and an EncoderInfo that contains information about the actual encoder
// structure used by a codec. Stream structures can be Ksvc, Full SVC, Simulcast
// etc.
VideoLayersAllocation CreateVideoLayersAllocation(
    const VideoCodec& encoder_config,
    const VideoEncoder::RateControlParameters& current_rate,
    const VideoEncoder::EncoderInfo& encoder_info) {
  const VideoBitrateAllocation& target_bitrate = current_rate.target_bitrate;
  VideoLayersAllocation layers_allocation;
  if (target_bitrate.get_sum_bps() == 0) {
    return layers_allocation;
  }

  if (encoder_config.numberOfSimulcastStreams > 1) {
    layers_allocation.resolution_and_frame_rate_is_valid = true;
    for (int si = 0; si < encoder_config.numberOfSimulcastStreams; ++si) {
      if (!target_bitrate.IsSpatialLayerUsed(si) ||
          target_bitrate.GetSpatialLayerSum(si) == 0) {
        continue;
      }
      layers_allocation.active_spatial_layers.emplace_back();
      VideoLayersAllocation::SpatialLayer& spatial_layer =
          layers_allocation.active_spatial_layers.back();
      spatial_layer.width = encoder_config.simulcastStream[si].width;
      spatial_layer.height = encoder_config.simulcastStream[si].height;
      spatial_layer.rtp_stream_index = si;
      spatial_layer.spatial_id = 0;
      auto frame_rate_fraction =
          VideoEncoder::EncoderInfo::kMaxFramerateFraction;
      if (encoder_info.fps_allocation[si].size() == 1) {
        // One TL is signalled to be used by the encoder. Do not distribute
        // bitrate allocation across TLs (use sum at tl:0).
        spatial_layer.target_bitrate_per_temporal_layer.push_back(
            DataRate::BitsPerSec(target_bitrate.GetSpatialLayerSum(si)));
        frame_rate_fraction = encoder_info.fps_allocation[si][0];
      } else {  // Temporal layers are supported.
        uint32_t temporal_layer_bitrate_bps = 0;
        for (size_t ti = 0;
             ti < encoder_config.simulcastStream[si].numberOfTemporalLayers;
             ++ti) {
          if (!target_bitrate.HasBitrate(si, ti)) {
            break;
          }
          if (ti < encoder_info.fps_allocation[si].size()) {
            // Use frame rate of the top used temporal layer.
            frame_rate_fraction = encoder_info.fps_allocation[si][ti];
          }
          temporal_layer_bitrate_bps += target_bitrate.GetBitrate(si, ti);
          spatial_layer.target_bitrate_per_temporal_layer.push_back(
              DataRate::BitsPerSec(temporal_layer_bitrate_bps));
        }
      }
      // Encoder may drop frames internally if `maxFramerate` is set.
      spatial_layer.frame_rate_fps = std::min<uint8_t>(
          encoder_config.simulcastStream[si].maxFramerate,
          rtc::saturated_cast<uint8_t>(
              (current_rate.framerate_fps * frame_rate_fraction) /
              VideoEncoder::EncoderInfo::kMaxFramerateFraction));
    }
  } else if (encoder_config.numberOfSimulcastStreams == 1) {
    // TODO(bugs.webrtc.org/12000): Implement support for AV1 with
    // scalability.
    const bool higher_spatial_depend_on_lower =
        encoder_config.codecType == kVideoCodecVP9 &&
        encoder_config.VP9().interLayerPred == InterLayerPredMode::kOn;
    layers_allocation.resolution_and_frame_rate_is_valid = true;

    std::vector<DataRate> aggregated_spatial_bitrate(
        webrtc::kMaxTemporalStreams, DataRate::Zero());
    for (int si = 0; si < webrtc::kMaxSpatialLayers; ++si) {
      layers_allocation.resolution_and_frame_rate_is_valid = true;
      if (!target_bitrate.IsSpatialLayerUsed(si) ||
          target_bitrate.GetSpatialLayerSum(si) == 0) {
        break;
      }
      layers_allocation.active_spatial_layers.emplace_back();
      VideoLayersAllocation::SpatialLayer& spatial_layer =
          layers_allocation.active_spatial_layers.back();
      spatial_layer.width = encoder_config.spatialLayers[si].width;
      spatial_layer.height = encoder_config.spatialLayers[si].height;
      spatial_layer.rtp_stream_index = 0;
      spatial_layer.spatial_id = si;
      auto frame_rate_fraction =
          VideoEncoder::EncoderInfo::kMaxFramerateFraction;
      if (encoder_info.fps_allocation[si].size() == 1) {
        // One TL is signalled to be used by the encoder. Do not distribute
        // bitrate allocation across TLs (use sum at tl:0).
        DataRate aggregated_temporal_bitrate =
            DataRate::BitsPerSec(target_bitrate.GetSpatialLayerSum(si));
        aggregated_spatial_bitrate[0] += aggregated_temporal_bitrate;
        if (higher_spatial_depend_on_lower) {
          spatial_layer.target_bitrate_per_temporal_layer.push_back(
              aggregated_spatial_bitrate[0]);
        } else {
          spatial_layer.target_bitrate_per_temporal_layer.push_back(
              aggregated_temporal_bitrate);
        }
        frame_rate_fraction = encoder_info.fps_allocation[si][0];
      } else {  // Temporal layers are supported.
        DataRate aggregated_temporal_bitrate = DataRate::Zero();
        for (size_t ti = 0;
             ti < encoder_config.spatialLayers[si].numberOfTemporalLayers;
             ++ti) {
          if (!target_bitrate.HasBitrate(si, ti)) {
            break;
          }
          if (ti < encoder_info.fps_allocation[si].size()) {
            // Use frame rate of the top used temporal layer.
            frame_rate_fraction = encoder_info.fps_allocation[si][ti];
          }
          aggregated_temporal_bitrate +=
              DataRate::BitsPerSec(target_bitrate.GetBitrate(si, ti));
          if (higher_spatial_depend_on_lower) {
            spatial_layer.target_bitrate_per_temporal_layer.push_back(
                aggregated_temporal_bitrate + aggregated_spatial_bitrate[ti]);
            aggregated_spatial_bitrate[ti] += aggregated_temporal_bitrate;
          } else {
            spatial_layer.target_bitrate_per_temporal_layer.push_back(
                aggregated_temporal_bitrate);
          }
        }
      }
      // Encoder may drop frames internally if `maxFramerate` is set.
      spatial_layer.frame_rate_fps = std::min<uint8_t>(
          encoder_config.spatialLayers[si].maxFramerate,
          rtc::saturated_cast<uint8_t>(
              (current_rate.framerate_fps * frame_rate_fraction) /
              VideoEncoder::EncoderInfo::kMaxFramerateFraction));
    }
  }

  return layers_allocation;
}

VideoEncoder::EncoderInfo GetEncoderInfoWithBitrateLimitUpdate(
    const VideoEncoder::EncoderInfo& info,
    const VideoEncoderConfig& encoder_config,
    bool default_limits_allowed) {
  if (!default_limits_allowed || !info.resolution_bitrate_limits.empty() ||
      encoder_config.simulcast_layers.size() <= 1) {
    return info;
  }
  // Bitrate limits are not configured and more than one layer is used, use
  // the default limits (bitrate limits are not used for simulcast).
  VideoEncoder::EncoderInfo new_info = info;
  new_info.resolution_bitrate_limits =
      EncoderInfoSettings::GetDefaultSinglecastBitrateLimits(
          encoder_config.codec_type);
  return new_info;
}

int NumActiveStreams(const std::vector<VideoStream>& streams) {
  int num_active = 0;
  for (const auto& stream : streams) {
    if (stream.active)
      ++num_active;
  }
  return num_active;
}

void ApplyVp9BitrateLimits(const VideoEncoder::EncoderInfo& encoder_info,
                           const VideoEncoderConfig& encoder_config,
                           VideoCodec* codec) {
  if (codec->codecType != VideoCodecType::kVideoCodecVP9 ||
      encoder_config.simulcast_layers.size() <= 1 ||
      VideoStreamEncoderResourceManager::IsSimulcastOrMultipleSpatialLayers(
          encoder_config)) {
    // Resolution bitrate limits usage is restricted to singlecast.
    return;
  }

  // Get bitrate limits for active stream.
  absl::optional<uint32_t> pixels =
      VideoStreamAdapter::GetSingleActiveLayerPixels(*codec);
  if (!pixels.has_value()) {
    return;
  }
  absl::optional<VideoEncoder::ResolutionBitrateLimits> bitrate_limits =
      encoder_info.GetEncoderBitrateLimitsForResolution(*pixels);
  if (!bitrate_limits.has_value()) {
    return;
  }

  // Index for the active stream.
  absl::optional<size_t> index;
  for (size_t i = 0; i < encoder_config.simulcast_layers.size(); ++i) {
    if (encoder_config.simulcast_layers[i].active)
      index = i;
  }
  if (!index.has_value()) {
    return;
  }

  int min_bitrate_bps;
  if (encoder_config.simulcast_layers[*index].min_bitrate_bps <= 0) {
    min_bitrate_bps = bitrate_limits->min_bitrate_bps;
  } else {
    min_bitrate_bps =
        std::max(bitrate_limits->min_bitrate_bps,
                 encoder_config.simulcast_layers[*index].min_bitrate_bps);
  }
  int max_bitrate_bps;
  if (encoder_config.simulcast_layers[*index].max_bitrate_bps <= 0) {
    max_bitrate_bps = bitrate_limits->max_bitrate_bps;
  } else {
    max_bitrate_bps =
        std::min(bitrate_limits->max_bitrate_bps,
                 encoder_config.simulcast_layers[*index].max_bitrate_bps);
  }
  if (min_bitrate_bps >= max_bitrate_bps) {
    RTC_LOG(LS_WARNING) << "Bitrate limits not used, min_bitrate_bps "
                        << min_bitrate_bps << " >= max_bitrate_bps "
                        << max_bitrate_bps;
    return;
  }

  for (int i = 0; i < codec->VP9()->numberOfSpatialLayers; ++i) {
    if (codec->spatialLayers[i].active) {
      codec->spatialLayers[i].minBitrate = min_bitrate_bps / 1000;
      codec->spatialLayers[i].maxBitrate = max_bitrate_bps / 1000;
      codec->spatialLayers[i].targetBitrate =
          std::min(codec->spatialLayers[i].targetBitrate,
                   codec->spatialLayers[i].maxBitrate);
      break;
    }
  }
}

void ApplyEncoderBitrateLimitsIfSingleActiveStream(
    const VideoEncoder::EncoderInfo& encoder_info,
    const std::vector<VideoStream>& encoder_config_layers,
    std::vector<VideoStream>* streams) {
  // Apply limits if simulcast with one active stream (expect lowest).
  bool single_active_stream =
      streams->size() > 1 && NumActiveStreams(*streams) == 1 &&
      !streams->front().active && NumActiveStreams(encoder_config_layers) == 1;
  if (!single_active_stream) {
    return;
  }

  // Index for the active stream.
  size_t index = 0;
  for (size_t i = 0; i < encoder_config_layers.size(); ++i) {
    if (encoder_config_layers[i].active)
      index = i;
  }
  if (streams->size() < (index + 1) || !(*streams)[index].active) {
    return;
  }

  // Get bitrate limits for active stream.
  absl::optional<VideoEncoder::ResolutionBitrateLimits> encoder_bitrate_limits =
      encoder_info.GetEncoderBitrateLimitsForResolution(
          (*streams)[index].width * (*streams)[index].height);
  if (!encoder_bitrate_limits) {
    return;
  }

  // If bitrate limits are set by RtpEncodingParameters, use intersection.
  int min_bitrate_bps;
  if (encoder_config_layers[index].min_bitrate_bps <= 0) {
    min_bitrate_bps = encoder_bitrate_limits->min_bitrate_bps;
  } else {
    min_bitrate_bps = std::max(encoder_bitrate_limits->min_bitrate_bps,
                               (*streams)[index].min_bitrate_bps);
  }
  int max_bitrate_bps;
  if (encoder_config_layers[index].max_bitrate_bps <= 0) {
    max_bitrate_bps = encoder_bitrate_limits->max_bitrate_bps;
  } else {
    max_bitrate_bps = std::min(encoder_bitrate_limits->max_bitrate_bps,
                               (*streams)[index].max_bitrate_bps);
  }
  if (min_bitrate_bps >= max_bitrate_bps) {
    RTC_LOG(LS_WARNING) << "Encoder bitrate limits"
                        << " (min=" << encoder_bitrate_limits->min_bitrate_bps
                        << ", max=" << encoder_bitrate_limits->max_bitrate_bps
                        << ") do not intersect with stream limits"
                        << " (min=" << (*streams)[index].min_bitrate_bps
                        << ", max=" << (*streams)[index].max_bitrate_bps
                        << "). Encoder bitrate limits not used.";
    return;
  }

  (*streams)[index].min_bitrate_bps = min_bitrate_bps;
  (*streams)[index].max_bitrate_bps = max_bitrate_bps;
  (*streams)[index].target_bitrate_bps =
      std::min((*streams)[index].target_bitrate_bps,
               encoder_bitrate_limits->max_bitrate_bps);
}

absl::optional<int> ParseVp9LowTierCoreCountThreshold(
    const FieldTrialsView& trials) {
  FieldTrialFlag disable_low_tier("Disabled");
  FieldTrialParameter<int> max_core_count("max_core_count", 2);
  ParseFieldTrial({&disable_low_tier, &max_core_count},
                  trials.Lookup("WebRTC-VP9-LowTierOptimizations"));
  if (disable_low_tier.Get()) {
    return absl::nullopt;
  }
  return max_core_count.Get();
}

absl::optional<VideoSourceRestrictions> MergeRestrictions(
    const std::vector<absl::optional<VideoSourceRestrictions>>& list) {
  absl::optional<VideoSourceRestrictions> return_value;
  for (const auto& res : list) {
    if (!res) {
      continue;
    }
    if (!return_value) {
      return_value = *res;
      continue;
    }
    return_value->UpdateMin(*res);
  }
  return return_value;
}

}  //  namespace

VideoStreamEncoder::EncoderRateSettings::EncoderRateSettings()
    : rate_control(),
      encoder_target(DataRate::Zero()),
      stable_encoder_target(DataRate::Zero()) {}

VideoStreamEncoder::EncoderRateSettings::EncoderRateSettings(
    const VideoBitrateAllocation& bitrate,
    double framerate_fps,
    DataRate bandwidth_allocation,
    DataRate encoder_target,
    DataRate stable_encoder_target)
    : rate_control(bitrate, framerate_fps, bandwidth_allocation),
      encoder_target(encoder_target),
      stable_encoder_target(stable_encoder_target) {}

bool VideoStreamEncoder::EncoderRateSettings::operator==(
    const EncoderRateSettings& rhs) const {
  return rate_control == rhs.rate_control &&
         encoder_target == rhs.encoder_target &&
         stable_encoder_target == rhs.stable_encoder_target;
}

bool VideoStreamEncoder::EncoderRateSettings::operator!=(
    const EncoderRateSettings& rhs) const {
  return !(*this == rhs);
}

class VideoStreamEncoder::DegradationPreferenceManager
    : public DegradationPreferenceProvider {
 public:
  explicit DegradationPreferenceManager(
      VideoStreamAdapter* video_stream_adapter)
      : degradation_preference_(DegradationPreference::DISABLED),
        is_screenshare_(false),
        effective_degradation_preference_(DegradationPreference::DISABLED),
        video_stream_adapter_(video_stream_adapter) {
    RTC_DCHECK(video_stream_adapter_);
    sequence_checker_.Detach();
  }

  ~DegradationPreferenceManager() override = default;

  DegradationPreference degradation_preference() const override {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    return effective_degradation_preference_;
  }

  void SetDegradationPreference(DegradationPreference degradation_preference) {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    degradation_preference_ = degradation_preference;
    MaybeUpdateEffectiveDegradationPreference();
  }

  void SetIsScreenshare(bool is_screenshare) {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    is_screenshare_ = is_screenshare;
    MaybeUpdateEffectiveDegradationPreference();
  }

 private:
  void MaybeUpdateEffectiveDegradationPreference()
      RTC_RUN_ON(&sequence_checker_) {
    DegradationPreference effective_degradation_preference =
        (is_screenshare_ &&
         degradation_preference_ == DegradationPreference::BALANCED)
            ? DegradationPreference::MAINTAIN_RESOLUTION
            : degradation_preference_;

    if (effective_degradation_preference != effective_degradation_preference_) {
      effective_degradation_preference_ = effective_degradation_preference;
      video_stream_adapter_->SetDegradationPreference(
          effective_degradation_preference);
    }
  }

  RTC_NO_UNIQUE_ADDRESS SequenceChecker sequence_checker_;
  DegradationPreference degradation_preference_
      RTC_GUARDED_BY(&sequence_checker_);
  bool is_screenshare_ RTC_GUARDED_BY(&sequence_checker_);
  DegradationPreference effective_degradation_preference_
      RTC_GUARDED_BY(&sequence_checker_);
  VideoStreamAdapter* video_stream_adapter_ RTC_GUARDED_BY(&sequence_checker_);
};

VideoStreamEncoder::VideoStreamEncoder(
    Clock* clock,
    uint32_t number_of_cores,
    VideoStreamEncoderObserver* encoder_stats_observer,
    const VideoStreamEncoderSettings& settings,
    std::unique_ptr<OveruseFrameDetector> overuse_detector,
    std::unique_ptr<FrameCadenceAdapterInterface> frame_cadence_adapter,
    std::unique_ptr<webrtc::TaskQueueBase, webrtc::TaskQueueDeleter>
        encoder_queue,
    BitrateAllocationCallbackType allocation_cb_type,
    const FieldTrialsView& field_trials,
    webrtc::VideoEncoderFactory::EncoderSelectorInterface* encoder_selector)
    : field_trials_(field_trials),
      worker_queue_(TaskQueueBase::Current()),
      number_of_cores_(number_of_cores),
      sink_(nullptr),
      settings_(settings),
      allocation_cb_type_(allocation_cb_type),
      rate_control_settings_(RateControlSettings::ParseFromFieldTrials()),
      encoder_selector_from_constructor_(encoder_selector),
      encoder_selector_from_factory_(
          encoder_selector_from_constructor_
              ? nullptr
              : settings.encoder_factory->GetEncoderSelector()),
      encoder_selector_(encoder_selector_from_constructor_
                            ? encoder_selector_from_constructor_
                            : encoder_selector_from_factory_.get()),
      encoder_stats_observer_(encoder_stats_observer),
      cadence_callback_(*this),
      frame_cadence_adapter_(std::move(frame_cadence_adapter)),
      encoder_initialized_(false),
      max_framerate_(-1),
      pending_encoder_reconfiguration_(false),
      pending_encoder_creation_(false),
      crop_width_(0),
      crop_height_(0),
      encoder_target_bitrate_bps_(absl::nullopt),
      max_data_payload_length_(0),
      encoder_paused_and_dropped_frame_(false),
      was_encode_called_since_last_initialization_(false),
      encoder_failed_(false),
      clock_(clock),
      last_captured_timestamp_(0),
      delta_ntp_internal_ms_(clock_->CurrentNtpInMilliseconds() -
                             clock_->TimeInMilliseconds()),
      last_frame_log_ms_(clock_->TimeInMilliseconds()),
      captured_frame_count_(0),
      dropped_frame_cwnd_pushback_count_(0),
      dropped_frame_encoder_block_count_(0),
      pending_frame_post_time_us_(0),
      accumulated_update_rect_{0, 0, 0, 0},
      accumulated_update_rect_is_valid_(true),
      animation_start_time_(Timestamp::PlusInfinity()),
      cap_resolution_due_to_video_content_(false),
      expect_resize_state_(ExpectResizeState::kNoResize),
      fec_controller_override_(nullptr),
      force_disable_frame_dropper_(false),
      pending_frame_drops_(0),
      cwnd_frame_counter_(0),
      next_frame_types_(1, VideoFrameType::kVideoFrameDelta),
      frame_encode_metadata_writer_(this),
      experiment_groups_(GetExperimentGroups()),
      automatic_animation_detection_experiment_(
          ParseAutomatincAnimationDetectionFieldTrial()),
      input_state_provider_(encoder_stats_observer),
      video_stream_adapter_(
          std::make_unique<VideoStreamAdapter>(&input_state_provider_,
                                               encoder_stats_observer,
                                               field_trials)),
      degradation_preference_manager_(
          std::make_unique<DegradationPreferenceManager>(
              video_stream_adapter_.get())),
      adaptation_constraints_(),
      stream_resource_manager_(&input_state_provider_,
                               encoder_stats_observer,
                               clock_,
                               settings_.experiment_cpu_load_estimator,
                               std::move(overuse_detector),
                               degradation_preference_manager_.get(),
                               field_trials),
      video_source_sink_controller_(/*sink=*/frame_cadence_adapter_.get(),
                                    /*source=*/nullptr),
      default_limits_allowed_(
          !field_trials.IsEnabled("WebRTC-DefaultBitrateLimitsKillSwitch")),
      qp_parsing_allowed_(
          !field_trials.IsEnabled("WebRTC-QpParsingKillSwitch")),
      switch_encoder_on_init_failures_(!field_trials.IsDisabled(
          kSwitchEncoderOnInitializationFailuresFieldTrial)),
      vp9_low_tier_core_threshold_(
          ParseVp9LowTierCoreCountThreshold(field_trials)),
      encoder_queue_(std::move(encoder_queue)) {
  TRACE_EVENT0("webrtc", "VideoStreamEncoder::VideoStreamEncoder");
  RTC_DCHECK_RUN_ON(worker_queue_);
  RTC_DCHECK(encoder_stats_observer);
  RTC_DCHECK_GE(number_of_cores, 1);

  frame_cadence_adapter_->Initialize(&cadence_callback_);
  stream_resource_manager_.Initialize(encoder_queue_.Get());

  encoder_queue_.PostTask([this] {
    RTC_DCHECK_RUN_ON(&encoder_queue_);

    resource_adaptation_processor_ =
        std::make_unique<ResourceAdaptationProcessor>(
            video_stream_adapter_.get());

    stream_resource_manager_.SetAdaptationProcessor(
        resource_adaptation_processor_.get(), video_stream_adapter_.get());
    resource_adaptation_processor_->AddResourceLimitationsListener(
        &stream_resource_manager_);
    video_stream_adapter_->AddRestrictionsListener(&stream_resource_manager_);
    video_stream_adapter_->AddRestrictionsListener(this);
    stream_resource_manager_.MaybeInitializePixelLimitResource();

    // Add the stream resource manager's resources to the processor.
    adaptation_constraints_ = stream_resource_manager_.AdaptationConstraints();
    for (auto* constraint : adaptation_constraints_) {
      video_stream_adapter_->AddAdaptationConstraint(constraint);
    }
  });
}

VideoStreamEncoder::~VideoStreamEncoder() {
  RTC_DCHECK_RUN_ON(worker_queue_);
  RTC_DCHECK(!video_source_sink_controller_.HasSource())
      << "Must call ::Stop() before destruction.";
}

void VideoStreamEncoder::Stop() {
  RTC_DCHECK_RUN_ON(worker_queue_);
  video_source_sink_controller_.SetSource(nullptr);

  rtc::Event shutdown_event;
  absl::Cleanup shutdown = [&shutdown_event] { shutdown_event.Set(); };
  encoder_queue_.PostTask(
      [this, shutdown = std::move(shutdown)] {
        RTC_DCHECK_RUN_ON(&encoder_queue_);
        if (resource_adaptation_processor_) {
          stream_resource_manager_.StopManagedResources();
          for (auto* constraint : adaptation_constraints_) {
            video_stream_adapter_->RemoveAdaptationConstraint(constraint);
          }
          for (auto& resource : additional_resources_) {
            stream_resource_manager_.RemoveResource(resource);
          }
          additional_resources_.clear();
          video_stream_adapter_->RemoveRestrictionsListener(this);
          video_stream_adapter_->RemoveRestrictionsListener(
              &stream_resource_manager_);
          resource_adaptation_processor_->RemoveResourceLimitationsListener(
              &stream_resource_manager_);
          stream_resource_manager_.SetAdaptationProcessor(nullptr, nullptr);
          resource_adaptation_processor_.reset();
        }
        rate_allocator_ = nullptr;
        ReleaseEncoder();
        encoder_ = nullptr;
        frame_cadence_adapter_ = nullptr;
      });
  shutdown_event.Wait(rtc::Event::kForever);
}

void VideoStreamEncoder::SetFecControllerOverride(
    FecControllerOverride* fec_controller_override) {
  encoder_queue_.PostTask([this, fec_controller_override] {
    RTC_DCHECK_RUN_ON(&encoder_queue_);
    RTC_DCHECK(!fec_controller_override_);
    fec_controller_override_ = fec_controller_override;
    if (encoder_) {
      encoder_->SetFecControllerOverride(fec_controller_override_);
    }
  });
}

void VideoStreamEncoder::AddAdaptationResource(
    rtc::scoped_refptr<Resource> resource) {
  RTC_DCHECK_RUN_ON(worker_queue_);
  TRACE_EVENT0("webrtc", "VideoStreamEncoder::AddAdaptationResource");
  // Map any externally added resources as kCpu for the sake of stats reporting.
  // TODO(hbos): Make the manager map any unknown resources to kCpu and get rid
  // of this MapResourceToReason() call.
  TRACE_EVENT_ASYNC_BEGIN0(
      "webrtc", "VideoStreamEncoder::AddAdaptationResource(latency)", this);
  encoder_queue_.PostTask([this, resource = std::move(resource)] {
    TRACE_EVENT_ASYNC_END0(
        "webrtc", "VideoStreamEncoder::AddAdaptationResource(latency)", this);
    RTC_DCHECK_RUN_ON(&encoder_queue_);
    additional_resources_.push_back(resource);
    stream_resource_manager_.AddResource(resource, VideoAdaptationReason::kCpu);
  });
}

std::vector<rtc::scoped_refptr<Resource>>
VideoStreamEncoder::GetAdaptationResources() {
  RTC_DCHECK_RUN_ON(worker_queue_);
  // In practice, this method is only called by tests to verify operations that
  // run on the encoder queue. So rather than force PostTask() operations to
  // be accompanied by an event and a `Wait()`, we'll use PostTask + Wait()
  // here.
  rtc::Event event;
  std::vector<rtc::scoped_refptr<Resource>> resources;
  encoder_queue_.PostTask([&] {
    RTC_DCHECK_RUN_ON(&encoder_queue_);
    resources = resource_adaptation_processor_->GetResources();
    event.Set();
  });
  event.Wait(rtc::Event::kForever);
  return resources;
}

void VideoStreamEncoder::SetSource(
    rtc::VideoSourceInterface<VideoFrame>* source,
    const DegradationPreference& degradation_preference) {
  RTC_DCHECK_RUN_ON(worker_queue_);
  video_source_sink_controller_.SetSource(source);
  input_state_provider_.OnHasInputChanged(source);

  // This may trigger reconfiguring the QualityScaler on the encoder queue.
  encoder_queue_.PostTask([this, degradation_preference] {
    RTC_DCHECK_RUN_ON(&encoder_queue_);
    degradation_preference_manager_->SetDegradationPreference(
        degradation_preference);
    stream_resource_manager_.SetDegradationPreferences(degradation_preference);
    if (encoder_) {
      stream_resource_manager_.ConfigureQualityScaler(
          encoder_->GetEncoderInfo());
      stream_resource_manager_.ConfigureBandwidthQualityScaler(
          encoder_->GetEncoderInfo());
    }
  });
}

void VideoStreamEncoder::SetSink(EncoderSink* sink, bool rotation_applied) {
  RTC_DCHECK_RUN_ON(worker_queue_);
  video_source_sink_controller_.SetRotationApplied(rotation_applied);
  video_source_sink_controller_.PushSourceSinkSettings();

  encoder_queue_.PostTask([this, sink] {
    RTC_DCHECK_RUN_ON(&encoder_queue_);
    sink_ = sink;
  });
}

void VideoStreamEncoder::SetStartBitrate(int start_bitrate_bps) {
  encoder_queue_.PostTask([this, start_bitrate_bps] {
    RTC_DCHECK_RUN_ON(&encoder_queue_);
    RTC_LOG(LS_INFO) << "SetStartBitrate " << start_bitrate_bps;
    encoder_target_bitrate_bps_ =
        start_bitrate_bps != 0 ? absl::optional<uint32_t>(start_bitrate_bps)
                               : absl::nullopt;
    stream_resource_manager_.SetStartBitrate(
        DataRate::BitsPerSec(start_bitrate_bps));
  });
}

void VideoStreamEncoder::ConfigureEncoder(VideoEncoderConfig config,
                                          size_t max_data_payload_length) {
  RTC_DCHECK_RUN_ON(worker_queue_);
  encoder_queue_.PostTask(
      [this, config = std::move(config), max_data_payload_length]() mutable {
        RTC_DCHECK_RUN_ON(&encoder_queue_);
        RTC_DCHECK(sink_);
        RTC_LOG(LS_INFO) << "ConfigureEncoder requested.";

        // Set up the frame cadence adapter according to if we're going to do
        // screencast. The final number of spatial layers is based on info
        // in `send_codec_`, which is computed based on incoming frame
        // dimensions which can only be determined later.
        //
        // Note: zero-hertz mode isn't enabled by this alone. Constraints also
        // have to be set up with min_fps = 0 and max_fps > 0.
        if (config.content_type == VideoEncoderConfig::ContentType::kScreen) {
          frame_cadence_adapter_->SetZeroHertzModeEnabled(
              FrameCadenceAdapterInterface::ZeroHertzModeParams{});
        } else {
          frame_cadence_adapter_->SetZeroHertzModeEnabled(absl::nullopt);
        }

        pending_encoder_creation_ =
            (!encoder_ || encoder_config_.video_format != config.video_format ||
             max_data_payload_length_ != max_data_payload_length);
        encoder_config_ = std::move(config);
        max_data_payload_length_ = max_data_payload_length;
        pending_encoder_reconfiguration_ = true;

        // Reconfigure the encoder now if the frame resolution is known.
        // Otherwise, the reconfiguration is deferred until the next frame to
        // minimize the number of reconfigurations. The codec configuration
        // depends on incoming video frame size.
        if (last_frame_info_) {
          ReconfigureEncoder();
        }
      });
}

// We should reduce the number of 'full' ReconfigureEncoder(). If only need
// subset of it at runtime, consider handle it in
// VideoStreamEncoder::EncodeVideoFrame() when encoder_info_ != info.
void VideoStreamEncoder::ReconfigureEncoder() {
  // Running on the encoder queue.
  RTC_DCHECK(pending_encoder_reconfiguration_);

  bool encoder_reset_required = false;
  if (pending_encoder_creation_) {
    // Destroy existing encoder instance before creating a new one. Otherwise
    // attempt to create another instance will fail if encoder factory
    // supports only single instance of encoder of given type.
    encoder_.reset();

    encoder_ = settings_.encoder_factory->CreateVideoEncoder(
        encoder_config_.video_format);
    if (!encoder_) {
      RTC_LOG(LS_ERROR) << "CreateVideoEncoder failed, failing encoder format: "
                        << encoder_config_.video_format.ToString();
      RequestEncoderSwitch();
      return;
    }

    if (encoder_selector_) {
      encoder_selector_->OnCurrentEncoder(encoder_config_.video_format);
    }

    encoder_->SetFecControllerOverride(fec_controller_override_);

    encoder_reset_required = true;
  }

  // TODO(webrtc:14451) : Move AlignmentAdjuster into EncoderStreamFactory
  // Possibly adjusts scale_resolution_down_by in `encoder_config_` to limit the
  // alignment value.
  AlignmentAdjuster::GetAlignmentAndMaybeAdjustScaleFactors(
      encoder_->GetEncoderInfo(), &encoder_config_, absl::nullopt);

  std::vector<VideoStream> streams;
  if (encoder_config_.video_stream_factory) {
    // Note: only tests set their own EncoderStreamFactory...
    streams = encoder_config_.video_stream_factory->CreateEncoderStreams(
        last_frame_info_->width, last_frame_info_->height, encoder_config_);
  } else {
    rtc::scoped_refptr<VideoEncoderConfig::VideoStreamFactoryInterface>
        factory = rtc::make_ref_counted<cricket::EncoderStreamFactory>(
            encoder_config_.video_format.name, encoder_config_.max_qp,
            encoder_config_.content_type ==
                webrtc::VideoEncoderConfig::ContentType::kScreen,
            encoder_config_.legacy_conference_mode, encoder_->GetEncoderInfo(),
            MergeRestrictions({latest_restrictions_, animate_restrictions_}),
            &field_trials_);

    streams = factory->CreateEncoderStreams(
        last_frame_info_->width, last_frame_info_->height, encoder_config_);
  }

  // TODO(webrtc:14451) : Move AlignmentAdjuster into EncoderStreamFactory
  // Get alignment when actual number of layers are known.
  int alignment = AlignmentAdjuster::GetAlignmentAndMaybeAdjustScaleFactors(
      encoder_->GetEncoderInfo(), &encoder_config_, streams.size());

  // Check that the higher layers do not try to set number of temporal layers
  // to less than 1.
  // TODO(brandtr): Get rid of the wrapping optional as it serves no purpose
  // at this layer.
#if RTC_DCHECK_IS_ON
  for (const auto& stream : streams) {
    RTC_DCHECK_GE(stream.num_temporal_layers.value_or(1), 1);
  }
#endif

  // TODO(ilnik): If configured resolution is significantly less than provided,
  // e.g. because there are not enough SSRCs for all simulcast streams,
  // signal new resolutions via SinkWants to video source.

  // Stream dimensions may be not equal to given because of a simulcast
  // restrictions.
  auto highest_stream = absl::c_max_element(
      streams, [](const webrtc::VideoStream& a, const webrtc::VideoStream& b) {
        return std::tie(a.width, a.height) < std::tie(b.width, b.height);
      });
  int highest_stream_width = static_cast<int>(highest_stream->width);
  int highest_stream_height = static_cast<int>(highest_stream->height);
  // Dimension may be reduced to be, e.g. divisible by 4.
  RTC_CHECK_GE(last_frame_info_->width, highest_stream_width);
  RTC_CHECK_GE(last_frame_info_->height, highest_stream_height);
  crop_width_ = last_frame_info_->width - highest_stream_width;
  crop_height_ = last_frame_info_->height - highest_stream_height;

  if (!encoder_->GetEncoderInfo().is_qp_trusted.value_or(true)) {
    // when qp is not trusted, we priorities to using the
    // |resolution_bitrate_limits| provided by the decoder.
    const std::vector<VideoEncoder::ResolutionBitrateLimits>& bitrate_limits =
        encoder_->GetEncoderInfo().resolution_bitrate_limits.empty()
            ? EncoderInfoSettings::
                  GetDefaultSinglecastBitrateLimitsWhenQpIsUntrusted()
            : encoder_->GetEncoderInfo().resolution_bitrate_limits;

    // For BandwidthQualityScaler, its implement based on a certain pixel_count
    // correspond a certain bps interval. In fact, WebRTC default max_bps is
    // 2500Kbps when width * height > 960 * 540. For example, we assume:
    // 1.the camera support 1080p.
    // 2.ResolutionBitrateLimits set 720p bps interval is [1500Kbps,2000Kbps].
    // 3.ResolutionBitrateLimits set 1080p bps interval is [2000Kbps,2500Kbps].
    // We will never be stable at 720p due to actual encoding bps of 720p and
    // 1080p are both 2500Kbps. So it is necessary to do a linear interpolation
    // to get a certain bitrate for certain pixel_count. It also doesn't work
    // for 960*540 and 640*520, we will nerver be stable at 640*520 due to their
    // |target_bitrate_bps| are both 2000Kbps.
    absl::optional<VideoEncoder::ResolutionBitrateLimits>
        qp_untrusted_bitrate_limit = EncoderInfoSettings::
            GetSinglecastBitrateLimitForResolutionWhenQpIsUntrusted(
                last_frame_info_->width * last_frame_info_->height,
                bitrate_limits);

    if (qp_untrusted_bitrate_limit) {
      // bandwidth_quality_scaler is only used for singlecast.
      if (streams.size() == 1 && encoder_config_.simulcast_layers.size() == 1) {
        streams.back().min_bitrate_bps =
            qp_untrusted_bitrate_limit->min_bitrate_bps;
        streams.back().max_bitrate_bps =
            qp_untrusted_bitrate_limit->max_bitrate_bps;
        // If it is screen share mode, the minimum value of max_bitrate should
        // be greater than/equal to 1200kbps.
        if (encoder_config_.content_type ==
            VideoEncoderConfig::ContentType::kScreen) {
          streams.back().max_bitrate_bps = std::max(
              streams.back().max_bitrate_bps, kDefaultMinScreenSharebps);
        }
        streams.back().target_bitrate_bps =
            qp_untrusted_bitrate_limit->max_bitrate_bps;
      }
    }
  } else {
    absl::optional<VideoEncoder::ResolutionBitrateLimits>
        encoder_bitrate_limits =
            encoder_->GetEncoderInfo().GetEncoderBitrateLimitsForResolution(
                last_frame_info_->width * last_frame_info_->height);

    if (encoder_bitrate_limits) {
      if (streams.size() == 1 && encoder_config_.simulcast_layers.size() == 1) {
        // Bitrate limits can be set by app (in SDP or RtpEncodingParameters)
        // or/and can be provided by encoder. In presence of both set of
        // limits, the final set is derived as their intersection.
        int min_bitrate_bps;
        if (encoder_config_.simulcast_layers.empty() ||
            encoder_config_.simulcast_layers[0].min_bitrate_bps <= 0) {
          min_bitrate_bps = encoder_bitrate_limits->min_bitrate_bps;
        } else {
          min_bitrate_bps = std::max(encoder_bitrate_limits->min_bitrate_bps,
                                     streams.back().min_bitrate_bps);
        }

        int max_bitrate_bps;
        // We don't check encoder_config_.simulcast_layers[0].max_bitrate_bps
        // here since encoder_config_.max_bitrate_bps is derived from it (as
        // well as from other inputs).
        if (encoder_config_.max_bitrate_bps <= 0) {
          max_bitrate_bps = encoder_bitrate_limits->max_bitrate_bps;
        } else {
          max_bitrate_bps = std::min(encoder_bitrate_limits->max_bitrate_bps,
                                     streams.back().max_bitrate_bps);
        }

        if (min_bitrate_bps < max_bitrate_bps) {
          streams.back().min_bitrate_bps = min_bitrate_bps;
          streams.back().max_bitrate_bps = max_bitrate_bps;
          streams.back().target_bitrate_bps =
              std::min(streams.back().target_bitrate_bps,
                       encoder_bitrate_limits->max_bitrate_bps);
        } else {
          RTC_LOG(LS_WARNING)
              << "Bitrate limits provided by encoder"
              << " (min=" << encoder_bitrate_limits->min_bitrate_bps
              << ", max=" << encoder_bitrate_limits->max_bitrate_bps
              << ") do not intersect with limits set by app"
              << " (min=" << streams.back().min_bitrate_bps
              << ", max=" << encoder_config_.max_bitrate_bps
              << "). The app bitrate limits will be used.";
        }
      }
    }
  }

  ApplyEncoderBitrateLimitsIfSingleActiveStream(
      GetEncoderInfoWithBitrateLimitUpdate(
          encoder_->GetEncoderInfo(), encoder_config_, default_limits_allowed_),
      encoder_config_.simulcast_layers, &streams);

  VideoCodec codec;
  if (!VideoCodecInitializer::SetupCodec(encoder_config_, streams, &codec)) {
    RTC_LOG(LS_ERROR) << "Failed to create encoder configuration.";
  }

  if (encoder_config_.codec_type == kVideoCodecVP9) {
    // Spatial layers configuration might impose some parity restrictions,
    // thus some cropping might be needed.
    crop_width_ = last_frame_info_->width - codec.width;
    crop_height_ = last_frame_info_->height - codec.height;
    ApplyVp9BitrateLimits(GetEncoderInfoWithBitrateLimitUpdate(
                              encoder_->GetEncoderInfo(), encoder_config_,
                              default_limits_allowed_),
                          encoder_config_, &codec);
  }

  char log_stream_buf[4 * 1024];
  rtc::SimpleStringBuilder log_stream(log_stream_buf);
  log_stream << "ReconfigureEncoder:\n";
  log_stream << "Simulcast streams:\n";
  for (size_t i = 0; i < codec.numberOfSimulcastStreams; ++i) {
    log_stream << i << ": " << codec.simulcastStream[i].width << "x"
               << codec.simulcastStream[i].height
               << " min_kbps: " << codec.simulcastStream[i].minBitrate
               << " target_kbps: " << codec.simulcastStream[i].targetBitrate
               << " max_kbps: " << codec.simulcastStream[i].maxBitrate
               << " max_fps: " << codec.simulcastStream[i].maxFramerate
               << " max_qp: " << codec.simulcastStream[i].qpMax
               << " num_tl: " << codec.simulcastStream[i].numberOfTemporalLayers
               << " active: "
               << (codec.simulcastStream[i].active ? "true" : "false") << "\n";
  }
  if (encoder_config_.codec_type == kVideoCodecVP9) {
    size_t num_spatial_layers = codec.VP9()->numberOfSpatialLayers;
    log_stream << "Spatial layers:\n";
    for (size_t i = 0; i < num_spatial_layers; ++i) {
      log_stream << i << ": " << codec.spatialLayers[i].width << "x"
                 << codec.spatialLayers[i].height
                 << " min_kbps: " << codec.spatialLayers[i].minBitrate
                 << " target_kbps: " << codec.spatialLayers[i].targetBitrate
                 << " max_kbps: " << codec.spatialLayers[i].maxBitrate
                 << " max_fps: " << codec.spatialLayers[i].maxFramerate
                 << " max_qp: " << codec.spatialLayers[i].qpMax
                 << " num_tl: " << codec.spatialLayers[i].numberOfTemporalLayers
                 << " active: "
                 << (codec.spatialLayers[i].active ? "true" : "false") << "\n";
    }
  }
  RTC_LOG(LS_INFO) << log_stream.str();

  codec.startBitrate = std::max(encoder_target_bitrate_bps_.value_or(0) / 1000,
                                codec.minBitrate);
  codec.startBitrate = std::min(codec.startBitrate, codec.maxBitrate);
  codec.expect_encode_from_texture = last_frame_info_->is_texture;
  // Make sure the start bit rate is sane...
  RTC_DCHECK_LE(codec.startBitrate, 1000000);
  max_framerate_ = codec.maxFramerate;

  // Inform source about max configured framerate,
  // requested_resolution and which layers are active.
  int max_framerate = 0;
  // Is any layer active.
  bool active = false;
  // The max requested_resolution.
  absl::optional<rtc::VideoSinkWants::FrameSize> requested_resolution;
  for (const auto& stream : streams) {
    max_framerate = std::max(stream.max_framerate, max_framerate);
    active |= stream.active;
    // Note: we propagate the highest requested_resolution regardless
    // if layer is active or not.
    if (stream.requested_resolution) {
      if (!requested_resolution) {
        requested_resolution.emplace(stream.requested_resolution->width,
                                     stream.requested_resolution->height);
      } else {
        requested_resolution.emplace(
            std::max(stream.requested_resolution->width,
                     requested_resolution->width),
            std::max(stream.requested_resolution->height,
                     requested_resolution->height));
      }
    }
  }

  // The resolutions that we're actually encoding with.
  std::vector<rtc::VideoSinkWants::FrameSize> encoder_resolutions;
  // TODO(hbos): For the case of SVC, also make use of `codec.spatialLayers`.
  // For now, SVC layers are handled by the VP9 encoder.
  for (const auto& simulcastStream : codec.simulcastStream) {
    if (!simulcastStream.active)
      continue;
    encoder_resolutions.emplace_back(simulcastStream.width,
                                     simulcastStream.height);
  }

  worker_queue_->PostTask(SafeTask(
      task_safety_.flag(),
      [this, max_framerate, alignment,
       encoder_resolutions = std::move(encoder_resolutions),
       requested_resolution = std::move(requested_resolution), active]() {
        RTC_DCHECK_RUN_ON(worker_queue_);
        if (max_framerate !=
                video_source_sink_controller_.frame_rate_upper_limit() ||
            alignment != video_source_sink_controller_.resolution_alignment() ||
            encoder_resolutions !=
                video_source_sink_controller_.resolutions() ||
            (video_source_sink_controller_.requested_resolution() !=
             requested_resolution) ||
            (video_source_sink_controller_.active() != active)) {
          video_source_sink_controller_.SetFrameRateUpperLimit(max_framerate);
          video_source_sink_controller_.SetResolutionAlignment(alignment);
          video_source_sink_controller_.SetResolutions(
              std::move(encoder_resolutions));
          video_source_sink_controller_.SetRequestedResolution(
              requested_resolution);
          video_source_sink_controller_.SetActive(active);
          video_source_sink_controller_.PushSourceSinkSettings();
        }
      }));

  rate_allocator_ =
      settings_.bitrate_allocator_factory->CreateVideoBitrateAllocator(codec);
  rate_allocator_->SetLegacyConferenceMode(
      encoder_config_.legacy_conference_mode);

  // Reset (release existing encoder) if one exists and anything except
  // start bitrate or max framerate has changed.
  if (!encoder_reset_required) {
    encoder_reset_required = RequiresEncoderReset(
        send_codec_, codec, was_encode_called_since_last_initialization_);
  }

  if (codec.codecType == VideoCodecType::kVideoCodecVP9 &&
      number_of_cores_ <= vp9_low_tier_core_threshold_.value_or(0)) {
    codec.SetVideoEncoderComplexity(VideoCodecComplexity::kComplexityLow);
  }

  send_codec_ = codec;

  // Keep the same encoder, as long as the video_format is unchanged.
  // Encoder creation block is split in two since EncoderInfo needed to start
  // CPU adaptation with the correct settings should be polled after
  // encoder_->InitEncode().
  if (encoder_reset_required) {
    ReleaseEncoder();
    const size_t max_data_payload_length = max_data_payload_length_ > 0
                                               ? max_data_payload_length_
                                               : kDefaultPayloadSize;
    if (encoder_->InitEncode(
            &send_codec_,
            VideoEncoder::Settings(settings_.capabilities, number_of_cores_,
                                   max_data_payload_length)) != 0) {
      RTC_LOG(LS_ERROR) << "Failed to initialize the encoder associated with "
                           "codec type: "
                        << CodecTypeToPayloadString(send_codec_.codecType)
                        << " (" << send_codec_.codecType << ")";
      ReleaseEncoder();
    } else {
      encoder_initialized_ = true;
      encoder_->RegisterEncodeCompleteCallback(this);
      frame_encode_metadata_writer_.OnEncoderInit(send_codec_);
      next_frame_types_.clear();
      next_frame_types_.resize(
          std::max(static_cast<int>(codec.numberOfSimulcastStreams), 1),
          VideoFrameType::kVideoFrameKey);
    }

    frame_encode_metadata_writer_.Reset();
    last_encode_info_ms_ = absl::nullopt;
    was_encode_called_since_last_initialization_ = false;
  }

  // Inform dependents of updated encoder settings.
  OnEncoderSettingsChanged();

  if (encoder_initialized_) {
    RTC_LOG(LS_VERBOSE) << " max bitrate " << codec.maxBitrate
                        << " start bitrate " << codec.startBitrate
                        << " max frame rate " << codec.maxFramerate
                        << " max payload size " << max_data_payload_length_;
  } else {
    RTC_LOG(LS_ERROR) << "Failed to configure encoder.";
    rate_allocator_ = nullptr;
  }

  if (pending_encoder_creation_) {
    stream_resource_manager_.ConfigureEncodeUsageResource();
    pending_encoder_creation_ = false;
  }

  int num_layers;
  if (codec.codecType == kVideoCodecVP8) {
    num_layers = codec.VP8()->numberOfTemporalLayers;
  } else if (codec.codecType == kVideoCodecVP9) {
    num_layers = codec.VP9()->numberOfTemporalLayers;
  } else if (codec.codecType == kVideoCodecH264) {
    num_layers = codec.H264()->numberOfTemporalLayers;
  } else if (codec.codecType == kVideoCodecGeneric &&
             codec.numberOfSimulcastStreams > 0) {
    // This is mainly for unit testing, disabling frame dropping.
    // TODO(sprang): Add a better way to disable frame dropping.
    num_layers = codec.simulcastStream[0].numberOfTemporalLayers;
  } else {
    num_layers = 1;
  }

  frame_dropper_.Reset();
  frame_dropper_.SetRates(codec.startBitrate, max_framerate_);
  // Force-disable frame dropper if either:
  //  * We have screensharing with layers.
  //  * "WebRTC-FrameDropper" field trial is "Disabled".
  force_disable_frame_dropper_ =
      field_trials_.IsDisabled(kFrameDropperFieldTrial) ||
      (num_layers > 1 && codec.mode == VideoCodecMode::kScreensharing);

  VideoEncoder::EncoderInfo info = encoder_->GetEncoderInfo();
  if (rate_control_settings_.UseEncoderBitrateAdjuster()) {
    bitrate_adjuster_ = std::make_unique<EncoderBitrateAdjuster>(codec);
    bitrate_adjuster_->OnEncoderInfo(info);
  }

  if (rate_allocator_ && last_encoder_rate_settings_) {
    // We have a new rate allocator instance and already configured target
    // bitrate. Update the rate allocation and notify observers.
    // We must invalidate the last_encoder_rate_settings_ to ensure
    // the changes get propagated to all listeners.
    EncoderRateSettings rate_settings = *last_encoder_rate_settings_;
    last_encoder_rate_settings_.reset();
    rate_settings.rate_control.framerate_fps = GetInputFramerateFps();

    SetEncoderRates(UpdateBitrateAllocation(rate_settings));
  }

  encoder_stats_observer_->OnEncoderReconfigured(encoder_config_, streams);

  pending_encoder_reconfiguration_ = false;

  bool is_svc = false;
  // Set min_bitrate_bps, max_bitrate_bps, and max padding bit rate for VP9
  // and leave only one stream containing all necessary information.
  if (encoder_config_.codec_type == kVideoCodecVP9) {
    // Lower max bitrate to the level codec actually can produce.
    streams[0].max_bitrate_bps =
        std::min(streams[0].max_bitrate_bps,
                 SvcRateAllocator::GetMaxBitrate(codec).bps<int>());
    streams[0].min_bitrate_bps = codec.spatialLayers[0].minBitrate * 1000;
    // target_bitrate_bps specifies the maximum padding bitrate.
    streams[0].target_bitrate_bps =
        SvcRateAllocator::GetPaddingBitrate(codec).bps<int>();
    streams[0].width = streams.back().width;
    streams[0].height = streams.back().height;
    is_svc = codec.VP9()->numberOfSpatialLayers > 1;
    streams.resize(1);
  }

  sink_->OnEncoderConfigurationChanged(
      std::move(streams), is_svc, encoder_config_.content_type,
      encoder_config_.min_transmit_bitrate_bps);

  stream_resource_manager_.ConfigureQualityScaler(info);
  stream_resource_manager_.ConfigureBandwidthQualityScaler(info);

  if (!encoder_initialized_) {
    RTC_LOG(LS_WARNING) << "Failed to initialize "
                        << CodecTypeToPayloadString(codec.codecType)
                        << " encoder."
                        << "switch_encoder_on_init_failures: "
                        << switch_encoder_on_init_failures_;

    if (switch_encoder_on_init_failures_) {
      RequestEncoderSwitch();
    }
  }
}

void VideoStreamEncoder::RequestEncoderSwitch() {
  bool is_encoder_switching_supported =
      settings_.encoder_switch_request_callback != nullptr;
  bool is_encoder_selector_available = encoder_selector_ != nullptr;

  RTC_LOG(LS_INFO) << "RequestEncoderSwitch."
                   << " is_encoder_selector_available: "
                   << is_encoder_selector_available
                   << " is_encoder_switching_supported: "
                   << is_encoder_switching_supported;

  if (!is_encoder_switching_supported) {
    return;
  }

  // If encoder selector is available, switch to the encoder it prefers.
  // Otherwise try switching to VP8 (default WebRTC codec).
  absl::optional<SdpVideoFormat> preferred_fallback_encoder;
  if (is_encoder_selector_available) {
    preferred_fallback_encoder = encoder_selector_->OnEncoderBroken();
  }

  if (!preferred_fallback_encoder) {
    preferred_fallback_encoder =
        SdpVideoFormat(CodecTypeToPayloadString(kVideoCodecVP8));
  }

  settings_.encoder_switch_request_callback->RequestEncoderSwitch(
      *preferred_fallback_encoder, /*allow_default_fallback=*/true);
}

void VideoStreamEncoder::OnEncoderSettingsChanged() {
  EncoderSettings encoder_settings(
      GetEncoderInfoWithBitrateLimitUpdate(
          encoder_->GetEncoderInfo(), encoder_config_, default_limits_allowed_),
      encoder_config_.Copy(), send_codec_);
  stream_resource_manager_.SetEncoderSettings(encoder_settings);
  input_state_provider_.OnEncoderSettingsChanged(encoder_settings);
  bool is_screenshare = encoder_settings.encoder_config().content_type ==
                        VideoEncoderConfig::ContentType::kScreen;
  degradation_preference_manager_->SetIsScreenshare(is_screenshare);
  if (is_screenshare) {
    frame_cadence_adapter_->SetZeroHertzModeEnabled(
        FrameCadenceAdapterInterface::ZeroHertzModeParams{
            send_codec_.numberOfSimulcastStreams});
  }
}

void VideoStreamEncoder::OnFrame(Timestamp post_time,
                                 int frames_scheduled_for_processing,
                                 const VideoFrame& video_frame) {
  RTC_DCHECK_RUN_ON(&encoder_queue_);
  VideoFrame incoming_frame = video_frame;

  // In some cases, e.g., when the frame from decoder is fed to encoder,
  // the timestamp may be set to the future. As the encoding pipeline assumes
  // capture time to be less than present time, we should reset the capture
  // timestamps here. Otherwise there may be issues with RTP send stream.
  if (incoming_frame.timestamp_us() > post_time.us())
    incoming_frame.set_timestamp_us(post_time.us());

  // Capture time may come from clock with an offset and drift from clock_.
  int64_t capture_ntp_time_ms;
  if (video_frame.ntp_time_ms() > 0) {
    capture_ntp_time_ms = video_frame.ntp_time_ms();
  } else if (video_frame.render_time_ms() != 0) {
    capture_ntp_time_ms = video_frame.render_time_ms() + delta_ntp_internal_ms_;
  } else {
    capture_ntp_time_ms = post_time.ms() + delta_ntp_internal_ms_;
  }
  incoming_frame.set_ntp_time_ms(capture_ntp_time_ms);

  // Convert NTP time, in ms, to RTP timestamp.
  const int kMsToRtpTimestamp = 90;
  incoming_frame.set_timestamp(
      kMsToRtpTimestamp * static_cast<uint32_t>(incoming_frame.ntp_time_ms()));

  if (incoming_frame.ntp_time_ms() <= last_captured_timestamp_) {
    // We don't allow the same capture time for two frames, drop this one.
    RTC_LOG(LS_WARNING) << "Same/old NTP timestamp ("
                        << incoming_frame.ntp_time_ms()
                        << " <= " << last_captured_timestamp_
                        << ") for incoming frame. Dropping.";
    encoder_queue_.PostTask([this, incoming_frame]() {
      RTC_DCHECK_RUN_ON(&encoder_queue_);
      accumulated_update_rect_.Union(incoming_frame.update_rect());
      accumulated_update_rect_is_valid_ &= incoming_frame.has_update_rect();
    });
    return;
  }

  bool log_stats = false;
  if (post_time.ms() - last_frame_log_ms_ > kFrameLogIntervalMs) {
    last_frame_log_ms_ = post_time.ms();
    log_stats = true;
  }

  last_captured_timestamp_ = incoming_frame.ntp_time_ms();

  encoder_stats_observer_->OnIncomingFrame(incoming_frame.width(),
                                           incoming_frame.height());
  ++captured_frame_count_;
  CheckForAnimatedContent(incoming_frame, post_time.us());
  bool cwnd_frame_drop =
      cwnd_frame_drop_interval_ &&
      (cwnd_frame_counter_++ % cwnd_frame_drop_interval_.value() == 0);
  if (frames_scheduled_for_processing == 1 && !cwnd_frame_drop) {
    MaybeEncodeVideoFrame(incoming_frame, post_time.us());
  } else {
    if (cwnd_frame_drop) {
      // Frame drop by congestion window pushback. Do not encode this
      // frame.
      ++dropped_frame_cwnd_pushback_count_;
      encoder_stats_observer_->OnFrameDropped(
          VideoStreamEncoderObserver::DropReason::kCongestionWindow);
    } else {
      // There is a newer frame in flight. Do not encode this frame.
      RTC_LOG(LS_VERBOSE)
          << "Incoming frame dropped due to that the encoder is blocked.";
      ++dropped_frame_encoder_block_count_;
      encoder_stats_observer_->OnFrameDropped(
          VideoStreamEncoderObserver::DropReason::kEncoderQueue);
    }
    accumulated_update_rect_.Union(incoming_frame.update_rect());
    accumulated_update_rect_is_valid_ &= incoming_frame.has_update_rect();
  }
  if (log_stats) {
    RTC_LOG(LS_INFO) << "Number of frames: captured " << captured_frame_count_
                     << ", dropped (due to congestion window pushback) "
                     << dropped_frame_cwnd_pushback_count_
                     << ", dropped (due to encoder blocked) "
                     << dropped_frame_encoder_block_count_ << ", interval_ms "
                     << kFrameLogIntervalMs;
    captured_frame_count_ = 0;
    dropped_frame_cwnd_pushback_count_ = 0;
    dropped_frame_encoder_block_count_ = 0;
  }
}

void VideoStreamEncoder::OnDiscardedFrame() {
  encoder_stats_observer_->OnFrameDropped(
      VideoStreamEncoderObserver::DropReason::kSource);
}

bool VideoStreamEncoder::EncoderPaused() const {
  RTC_DCHECK_RUN_ON(&encoder_queue_);
  // Pause video if paused by caller or as long as the network is down or the
  // pacer queue has grown too large in buffered mode.
  // If the pacer queue has grown too large or the network is down,
  // `last_encoder_rate_settings_->encoder_target` will be 0.
  return !last_encoder_rate_settings_ ||
         last_encoder_rate_settings_->encoder_target == DataRate::Zero();
}

void VideoStreamEncoder::TraceFrameDropStart() {
  RTC_DCHECK_RUN_ON(&encoder_queue_);
  // Start trace event only on the first frame after encoder is paused.
  if (!encoder_paused_and_dropped_frame_) {
    TRACE_EVENT_ASYNC_BEGIN0("webrtc", "EncoderPaused", this);
  }
  encoder_paused_and_dropped_frame_ = true;
}

void VideoStreamEncoder::TraceFrameDropEnd() {
  RTC_DCHECK_RUN_ON(&encoder_queue_);
  // End trace event on first frame after encoder resumes, if frame was dropped.
  if (encoder_paused_and_dropped_frame_) {
    TRACE_EVENT_ASYNC_END0("webrtc", "EncoderPaused", this);
  }
  encoder_paused_and_dropped_frame_ = false;
}

VideoStreamEncoder::EncoderRateSettings
VideoStreamEncoder::UpdateBitrateAllocation(
    const EncoderRateSettings& rate_settings) {
  VideoBitrateAllocation new_allocation;
  // Only call allocators if bitrate > 0 (ie, not suspended), otherwise they
  // might cap the bitrate to the min bitrate configured.
  if (rate_allocator_ && rate_settings.encoder_target > DataRate::Zero()) {
    new_allocation = rate_allocator_->Allocate(VideoBitrateAllocationParameters(
        rate_settings.encoder_target, rate_settings.stable_encoder_target,
        rate_settings.rate_control.framerate_fps));
  }

  EncoderRateSettings new_rate_settings = rate_settings;
  new_rate_settings.rate_control.target_bitrate = new_allocation;
  new_rate_settings.rate_control.bitrate = new_allocation;
  // VideoBitrateAllocator subclasses may allocate a bitrate higher than the
  // target in order to sustain the min bitrate of the video codec. In this
  // case, make sure the bandwidth allocation is at least equal the allocation
  // as that is part of the document contract for that field.
  new_rate_settings.rate_control.bandwidth_allocation =
      std::max(new_rate_settings.rate_control.bandwidth_allocation,
               DataRate::BitsPerSec(
                   new_rate_settings.rate_control.bitrate.get_sum_bps()));

  if (bitrate_adjuster_) {
    VideoBitrateAllocation adjusted_allocation =
        bitrate_adjuster_->AdjustRateAllocation(new_rate_settings.rate_control);
    RTC_LOG(LS_VERBOSE) << "Adjusting allocation, fps = "
                        << rate_settings.rate_control.framerate_fps << ", from "
                        << new_allocation.ToString() << ", to "
                        << adjusted_allocation.ToString();
    new_rate_settings.rate_control.bitrate = adjusted_allocation;
  }

  return new_rate_settings;
}

uint32_t VideoStreamEncoder::GetInputFramerateFps() {
  const uint32_t default_fps = max_framerate_ != -1 ? max_framerate_ : 30;

  // This method may be called after we cleared out the frame_cadence_adapter_
  // reference in Stop(). In such a situation it's probably not important with a
  // decent estimate.
  absl::optional<uint32_t> input_fps =
      frame_cadence_adapter_ ? frame_cadence_adapter_->GetInputFrameRateFps()
                             : absl::nullopt;
  if (!input_fps || *input_fps == 0) {
    return default_fps;
  }
  return *input_fps;
}

void VideoStreamEncoder::SetEncoderRates(
    const EncoderRateSettings& rate_settings) {
  RTC_DCHECK_GT(rate_settings.rate_control.framerate_fps, 0.0);
  bool rate_control_changed =
      (!last_encoder_rate_settings_.has_value() ||
       last_encoder_rate_settings_->rate_control != rate_settings.rate_control);
  // For layer allocation signal we care only about the target bitrate (not the
  // adjusted one) and the target fps.
  bool layer_allocation_changed =
      !last_encoder_rate_settings_.has_value() ||
      last_encoder_rate_settings_->rate_control.target_bitrate !=
          rate_settings.rate_control.target_bitrate ||
      last_encoder_rate_settings_->rate_control.framerate_fps !=
          rate_settings.rate_control.framerate_fps;

  if (last_encoder_rate_settings_ != rate_settings) {
    last_encoder_rate_settings_ = rate_settings;
  }

  if (!encoder_)
    return;

  // Make the cadence adapter know if streams were disabled.
  for (int spatial_index = 0;
       spatial_index != send_codec_.numberOfSimulcastStreams; ++spatial_index) {
    frame_cadence_adapter_->UpdateLayerStatus(
        spatial_index,
        /*enabled=*/rate_settings.rate_control.target_bitrate
                .GetSpatialLayerSum(spatial_index) > 0);
  }

  // `bitrate_allocation` is 0 it means that the network is down or the send
  // pacer is full. We currently don't pass this on to the encoder since it is
  // unclear how current encoder implementations behave when given a zero target
  // bitrate.
  // TODO(perkj): Make sure all known encoder implementations handle zero
  // target bitrate and remove this check.
  if (rate_settings.rate_control.bitrate.get_sum_bps() == 0)
    return;

  if (rate_control_changed) {
    encoder_->SetRates(rate_settings.rate_control);

    encoder_stats_observer_->OnBitrateAllocationUpdated(
        send_codec_, rate_settings.rate_control.bitrate);
    frame_encode_metadata_writer_.OnSetRates(
        rate_settings.rate_control.bitrate,
        static_cast<uint32_t>(rate_settings.rate_control.framerate_fps + 0.5));
    stream_resource_manager_.SetEncoderRates(rate_settings.rate_control);
    if (layer_allocation_changed &&
        allocation_cb_type_ ==
            BitrateAllocationCallbackType::kVideoLayersAllocation) {
      sink_->OnVideoLayersAllocationUpdated(CreateVideoLayersAllocation(
          send_codec_, rate_settings.rate_control, encoder_->GetEncoderInfo()));
    }
  }
  if ((allocation_cb_type_ ==
       BitrateAllocationCallbackType::kVideoBitrateAllocation) ||
      (encoder_config_.content_type ==
           VideoEncoderConfig::ContentType::kScreen &&
       allocation_cb_type_ == BitrateAllocationCallbackType::
                                  kVideoBitrateAllocationWhenScreenSharing)) {
    sink_->OnBitrateAllocationUpdated(
        // Update allocation according to info from encoder. An encoder may
        // choose to not use all layers due to for example HW.
        UpdateAllocationFromEncoderInfo(
            rate_settings.rate_control.target_bitrate,
            encoder_->GetEncoderInfo()));
  }
}

void VideoStreamEncoder::MaybeEncodeVideoFrame(const VideoFrame& video_frame,
                                               int64_t time_when_posted_us) {
  RTC_DCHECK_RUN_ON(&encoder_queue_);
  input_state_provider_.OnFrameSizeObserved(video_frame.size());

  if (!last_frame_info_ || video_frame.width() != last_frame_info_->width ||
      video_frame.height() != last_frame_info_->height ||
      video_frame.is_texture() != last_frame_info_->is_texture) {
    if ((!last_frame_info_ || video_frame.width() != last_frame_info_->width ||
         video_frame.height() != last_frame_info_->height) &&
        settings_.encoder_switch_request_callback && encoder_selector_) {
      if (auto encoder = encoder_selector_->OnResolutionChange(
              {video_frame.width(), video_frame.height()})) {
        settings_.encoder_switch_request_callback->RequestEncoderSwitch(
            *encoder, /*allow_default_fallback=*/false);
      }
    }

    pending_encoder_reconfiguration_ = true;
    last_frame_info_ = VideoFrameInfo(video_frame.width(), video_frame.height(),
                                      video_frame.is_texture());
    RTC_LOG(LS_INFO) << "Video frame parameters changed: dimensions="
                     << last_frame_info_->width << "x"
                     << last_frame_info_->height
                     << ", texture=" << last_frame_info_->is_texture << ".";
    // Force full frame update, since resolution has changed.
    accumulated_update_rect_ =
        VideoFrame::UpdateRect{0, 0, video_frame.width(), video_frame.height()};
  }

  // We have to create the encoder before the frame drop logic,
  // because the latter depends on encoder_->GetScalingSettings.
  // According to the testcase
  // InitialFrameDropOffWhenEncoderDisabledScaling, the return value
  // from GetScalingSettings should enable or disable the frame drop.

  // Update input frame rate before we start using it. If we update it after
  // any potential frame drop we are going to artificially increase frame sizes.
  // Poll the rate before updating, otherwise we risk the rate being estimated
  // a little too high at the start of the call when then window is small.
  uint32_t framerate_fps = GetInputFramerateFps();
  frame_cadence_adapter_->UpdateFrameRate();

  int64_t now_ms = clock_->TimeInMilliseconds();
  if (pending_encoder_reconfiguration_) {
    ReconfigureEncoder();
    last_parameters_update_ms_.emplace(now_ms);
  } else if (!last_parameters_update_ms_ ||
             now_ms - *last_parameters_update_ms_ >=
                 kParameterUpdateIntervalMs) {
    if (last_encoder_rate_settings_) {
      // Clone rate settings before update, so that SetEncoderRates() will
      // actually detect the change between the input and
      // `last_encoder_rate_setings_`, triggering the call to SetRate() on the
      // encoder.
      EncoderRateSettings new_rate_settings = *last_encoder_rate_settings_;
      new_rate_settings.rate_control.framerate_fps =
          static_cast<double>(framerate_fps);
      SetEncoderRates(UpdateBitrateAllocation(new_rate_settings));
    }
    last_parameters_update_ms_.emplace(now_ms);
  }

  // Because pending frame will be dropped in any case, we need to
  // remember its updated region.
  if (pending_frame_) {
    encoder_stats_observer_->OnFrameDropped(
        VideoStreamEncoderObserver::DropReason::kEncoderQueue);
    accumulated_update_rect_.Union(pending_frame_->update_rect());
    accumulated_update_rect_is_valid_ &= pending_frame_->has_update_rect();
  }

  if (DropDueToSize(video_frame.size())) {
    RTC_LOG(LS_INFO) << "Dropping frame. Too large for target bitrate.";
    stream_resource_manager_.OnFrameDroppedDueToSize();
    // Storing references to a native buffer risks blocking frame capture.
    if (video_frame.video_frame_buffer()->type() !=
        VideoFrameBuffer::Type::kNative) {
      pending_frame_ = video_frame;
      pending_frame_post_time_us_ = time_when_posted_us;
    } else {
      // Ensure that any previously stored frame is dropped.
      pending_frame_.reset();
      accumulated_update_rect_.Union(video_frame.update_rect());
      accumulated_update_rect_is_valid_ &= video_frame.has_update_rect();
      encoder_stats_observer_->OnFrameDropped(
          VideoStreamEncoderObserver::DropReason::kEncoderQueue);
    }
    return;
  }
  stream_resource_manager_.OnMaybeEncodeFrame();

  if (EncoderPaused()) {
    // Storing references to a native buffer risks blocking frame capture.
    if (video_frame.video_frame_buffer()->type() !=
        VideoFrameBuffer::Type::kNative) {
      if (pending_frame_)
        TraceFrameDropStart();
      pending_frame_ = video_frame;
      pending_frame_post_time_us_ = time_when_posted_us;
    } else {
      // Ensure that any previously stored frame is dropped.
      pending_frame_.reset();
      TraceFrameDropStart();
      accumulated_update_rect_.Union(video_frame.update_rect());
      accumulated_update_rect_is_valid_ &= video_frame.has_update_rect();
      encoder_stats_observer_->OnFrameDropped(
          VideoStreamEncoderObserver::DropReason::kEncoderQueue);
    }
    return;
  }

  pending_frame_.reset();

  frame_dropper_.Leak(framerate_fps);
  // Frame dropping is enabled iff frame dropping is not force-disabled, and
  // rate controller is not trusted.
  const bool frame_dropping_enabled =
      !force_disable_frame_dropper_ &&
      !encoder_info_.has_trusted_rate_controller;
  frame_dropper_.Enable(frame_dropping_enabled);
  if (frame_dropping_enabled && frame_dropper_.DropFrame()) {
    RTC_LOG(LS_VERBOSE)
        << "Drop Frame: "
           "target bitrate "
        << (last_encoder_rate_settings_
                ? last_encoder_rate_settings_->encoder_target.bps()
                : 0)
        << ", input frame rate " << framerate_fps;
    OnDroppedFrame(
        EncodedImageCallback::DropReason::kDroppedByMediaOptimizations);
    accumulated_update_rect_.Union(video_frame.update_rect());
    accumulated_update_rect_is_valid_ &= video_frame.has_update_rect();
    return;
  }

  EncodeVideoFrame(video_frame, time_when_posted_us);
}

void VideoStreamEncoder::EncodeVideoFrame(const VideoFrame& video_frame,
                                          int64_t time_when_posted_us) {
  RTC_DCHECK_RUN_ON(&encoder_queue_);
  RTC_LOG(LS_VERBOSE) << __func__ << " posted " << time_when_posted_us
                      << " ntp time " << video_frame.ntp_time_ms();

  // If the encoder fail we can't continue to encode frames. When this happens
  // the WebrtcVideoSender is notified and the whole VideoSendStream is
  // recreated.
  if (encoder_failed_ || !encoder_initialized_)
    return;

  // It's possible that EncodeVideoFrame can be called after we've completed
  // a Stop() operation. Check if the encoder_ is set before continuing.
  // See: bugs.webrtc.org/12857
  if (!encoder_)
    return;

  TraceFrameDropEnd();

  // Encoder metadata needs to be updated before encode complete callback.
  VideoEncoder::EncoderInfo info = encoder_->GetEncoderInfo();
  if (info.implementation_name != encoder_info_.implementation_name ||
      info.is_hardware_accelerated != encoder_info_.is_hardware_accelerated) {
    encoder_stats_observer_->OnEncoderImplementationChanged({
        .name = info.implementation_name,
        .is_hardware_accelerated = info.is_hardware_accelerated,
    });
    if (bitrate_adjuster_) {
      // Encoder implementation changed, reset overshoot detector states.
      bitrate_adjuster_->Reset();
    }
  }

  if (encoder_info_ != info) {
    OnEncoderSettingsChanged();
    stream_resource_manager_.ConfigureEncodeUsageResource();
    // Re-configure scalers when encoder info changed. Consider two cases:
    // 1. When the status of the scaler changes from enabled to disabled, if we
    // don't do this CL, scaler will adapt up/down to trigger an unnecessary
    // full ReconfigureEncoder() when the scaler should be banned.
    // 2. When the status of the scaler changes from disabled to enabled, if we
    // don't do this CL, scaler will not work until some code trigger
    // ReconfigureEncoder(). In extreme cases, the scaler doesn't even work for
    // a long time when we expect that the scaler should work.
    stream_resource_manager_.ConfigureQualityScaler(info);
    stream_resource_manager_.ConfigureBandwidthQualityScaler(info);

    RTC_LOG(LS_INFO) << "Encoder info changed to " << info.ToString();
  }

  if (bitrate_adjuster_) {
    for (size_t si = 0; si < kMaxSpatialLayers; ++si) {
      if (info.fps_allocation[si] != encoder_info_.fps_allocation[si]) {
        bitrate_adjuster_->OnEncoderInfo(info);
        break;
      }
    }
  }
  encoder_info_ = info;
  last_encode_info_ms_ = clock_->TimeInMilliseconds();

  VideoFrame out_frame(video_frame);
  // Crop or scale the frame if needed. Dimension may be reduced to fit encoder
  // requirements, e.g. some encoders may require them to be divisible by 4.
  if ((crop_width_ > 0 || crop_height_ > 0) &&
      (out_frame.video_frame_buffer()->type() !=
           VideoFrameBuffer::Type::kNative ||
       !info.supports_native_handle)) {
    int cropped_width = video_frame.width() - crop_width_;
    int cropped_height = video_frame.height() - crop_height_;
    rtc::scoped_refptr<VideoFrameBuffer> cropped_buffer;
    // TODO(ilnik): Remove scaling if cropping is too big, as it should never
    // happen after SinkWants signaled correctly from ReconfigureEncoder.
    VideoFrame::UpdateRect update_rect = video_frame.update_rect();
    if (crop_width_ < 4 && crop_height_ < 4) {
      // The difference is small, crop without scaling.
      cropped_buffer = video_frame.video_frame_buffer()->CropAndScale(
          crop_width_ / 2, crop_height_ / 2, cropped_width, cropped_height,
          cropped_width, cropped_height);
      update_rect.offset_x -= crop_width_ / 2;
      update_rect.offset_y -= crop_height_ / 2;
      update_rect.Intersect(
          VideoFrame::UpdateRect{0, 0, cropped_width, cropped_height});

    } else {
      // The difference is large, scale it.
      cropped_buffer = video_frame.video_frame_buffer()->Scale(cropped_width,
                                                               cropped_height);
      if (!update_rect.IsEmpty()) {
        // Since we can't reason about pixels after scaling, we invalidate whole
        // picture, if anything changed.
        update_rect =
            VideoFrame::UpdateRect{0, 0, cropped_width, cropped_height};
      }
    }
    if (!cropped_buffer) {
      RTC_LOG(LS_ERROR) << "Cropping and scaling frame failed, dropping frame.";
      return;
    }

    out_frame.set_video_frame_buffer(cropped_buffer);
    out_frame.set_update_rect(update_rect);
    out_frame.set_ntp_time_ms(video_frame.ntp_time_ms());
    // Since accumulated_update_rect_ is constructed before cropping,
    // we can't trust it. If any changes were pending, we invalidate whole
    // frame here.
    if (!accumulated_update_rect_.IsEmpty()) {
      accumulated_update_rect_ =
          VideoFrame::UpdateRect{0, 0, out_frame.width(), out_frame.height()};
      accumulated_update_rect_is_valid_ = false;
    }
  }

  if (!accumulated_update_rect_is_valid_) {
    out_frame.clear_update_rect();
  } else if (!accumulated_update_rect_.IsEmpty() &&
             out_frame.has_update_rect()) {
    accumulated_update_rect_.Union(out_frame.update_rect());
    accumulated_update_rect_.Intersect(
        VideoFrame::UpdateRect{0, 0, out_frame.width(), out_frame.height()});
    out_frame.set_update_rect(accumulated_update_rect_);
    accumulated_update_rect_.MakeEmptyUpdate();
  }
  accumulated_update_rect_is_valid_ = true;

  TRACE_EVENT_ASYNC_STEP0("webrtc", "Video", video_frame.render_time_ms(),
                          "Encode");

  stream_resource_manager_.OnEncodeStarted(out_frame, time_when_posted_us);

  // The encoder should get the size that it expects.
  RTC_DCHECK(send_codec_.width <= out_frame.width() &&
             send_codec_.height <= out_frame.height())
      << "Encoder configured to " << send_codec_.width << "x"
      << send_codec_.height << " received a too small frame "
      << out_frame.width() << "x" << out_frame.height();

  TRACE_EVENT1("webrtc", "VCMGenericEncoder::Encode", "timestamp",
               out_frame.timestamp());

  frame_encode_metadata_writer_.OnEncodeStarted(out_frame);

  const int32_t encode_status = encoder_->Encode(out_frame, &next_frame_types_);
  was_encode_called_since_last_initialization_ = true;

  if (encode_status < 0) {
    RTC_LOG(LS_ERROR) << "Encoder failed, failing encoder format: "
                      << encoder_config_.video_format.ToString();
    RequestEncoderSwitch();
    return;
  }

  for (auto& it : next_frame_types_) {
    it = VideoFrameType::kVideoFrameDelta;
  }
}

void VideoStreamEncoder::RequestRefreshFrame() {
  worker_queue_->PostTask(SafeTask(task_safety_.flag(), [this] {
    RTC_DCHECK_RUN_ON(worker_queue_);
    video_source_sink_controller_.RequestRefreshFrame();
  }));
}

void VideoStreamEncoder::SendKeyFrame() {
  if (!encoder_queue_.IsCurrent()) {
    encoder_queue_.PostTask([this] { SendKeyFrame(); });
    return;
  }
  RTC_DCHECK_RUN_ON(&encoder_queue_);
  TRACE_EVENT0("webrtc", "OnKeyFrameRequest");
  RTC_DCHECK(!next_frame_types_.empty());

  if (frame_cadence_adapter_)
    frame_cadence_adapter_->ProcessKeyFrameRequest();

  if (!encoder_) {
    RTC_DLOG(LS_INFO) << __func__ << " no encoder.";
    return;  // Shutting down, or not configured yet.
  }

  // TODO(webrtc:10615): Map keyframe request to spatial layer.
  std::fill(next_frame_types_.begin(), next_frame_types_.end(),
            VideoFrameType::kVideoFrameKey);
}

void VideoStreamEncoder::OnLossNotification(
    const VideoEncoder::LossNotification& loss_notification) {
  if (!encoder_queue_.IsCurrent()) {
    encoder_queue_.PostTask(
        [this, loss_notification] { OnLossNotification(loss_notification); });
    return;
  }

  RTC_DCHECK_RUN_ON(&encoder_queue_);
  if (encoder_) {
    encoder_->OnLossNotification(loss_notification);
  }
}

EncodedImage VideoStreamEncoder::AugmentEncodedImage(
    const EncodedImage& encoded_image,
    const CodecSpecificInfo* codec_specific_info) {
  EncodedImage image_copy(encoded_image);
  const size_t spatial_idx = encoded_image.SpatialIndex().value_or(0);
  frame_encode_metadata_writer_.FillTimingInfo(spatial_idx, &image_copy);
  frame_encode_metadata_writer_.UpdateBitstream(codec_specific_info,
                                                &image_copy);
  VideoCodecType codec_type = codec_specific_info
                                  ? codec_specific_info->codecType
                                  : VideoCodecType::kVideoCodecGeneric;
  if (image_copy.qp_ < 0 && qp_parsing_allowed_) {
    // Parse encoded frame QP if that was not provided by encoder.
    image_copy.qp_ = qp_parser_
                         .Parse(codec_type, spatial_idx, image_copy.data(),
                                image_copy.size())
                         .value_or(-1);
  }
  RTC_LOG(LS_VERBOSE) << __func__ << " spatial_idx " << spatial_idx << " qp "
                      << image_copy.qp_;
  image_copy.SetAtTargetQuality(codec_type == kVideoCodecVP8 &&
                                image_copy.qp_ <= kVp8SteadyStateQpThreshold);

  // Piggyback ALR experiment group id and simulcast id into the content type.
  const uint8_t experiment_id =
      experiment_groups_[videocontenttypehelpers::IsScreenshare(
          image_copy.content_type_)];

  // TODO(ilnik): This will force content type extension to be present even
  // for realtime video. At the expense of miniscule overhead we will get
  // sliced receive statistics.
  RTC_CHECK(videocontenttypehelpers::SetExperimentId(&image_copy.content_type_,
                                                     experiment_id));
  // We count simulcast streams from 1 on the wire. That's why we set simulcast
  // id in content type to +1 of that is actual simulcast index. This is because
  // value 0 on the wire is reserved for 'no simulcast stream specified'.
  RTC_CHECK(videocontenttypehelpers::SetSimulcastId(
      &image_copy.content_type_, static_cast<uint8_t>(spatial_idx + 1)));

  return image_copy;
}

EncodedImageCallback::Result VideoStreamEncoder::OnEncodedImage(
    const EncodedImage& encoded_image,
    const CodecSpecificInfo* codec_specific_info) {
  TRACE_EVENT_INSTANT1("webrtc", "VCMEncodedFrameCallback::Encoded",
                       "timestamp", encoded_image.Timestamp());

  // TODO(bugs.webrtc.org/10520): Signal the simulcast id explicitly.

  const size_t spatial_idx = encoded_image.SpatialIndex().value_or(0);
  const VideoCodecType codec_type = codec_specific_info
                                        ? codec_specific_info->codecType
                                        : VideoCodecType::kVideoCodecGeneric;
  EncodedImage image_copy =
      AugmentEncodedImage(encoded_image, codec_specific_info);

  // Post a task because `send_codec_` requires `encoder_queue_` lock and we
  // need to update on quality convergence.
  unsigned int image_width = image_copy._encodedWidth;
  unsigned int image_height = image_copy._encodedHeight;
  encoder_queue_.PostTask([this, codec_type, image_width, image_height,
                           spatial_idx,
                           at_target_quality = image_copy.IsAtTargetQuality()] {
    RTC_DCHECK_RUN_ON(&encoder_queue_);

    // Let the frame cadence adapter know about quality convergence.
    if (frame_cadence_adapter_)
      frame_cadence_adapter_->UpdateLayerQualityConvergence(spatial_idx,
                                                            at_target_quality);

    // Currently, the internal quality scaler is used for VP9 instead of the
    // webrtc qp scaler (in the no-svc case or if only a single spatial layer is
    // encoded). It has to be explicitly detected and reported to adaptation
    // metrics.
    if (codec_type == VideoCodecType::kVideoCodecVP9 &&
        send_codec_.VP9()->automaticResizeOn) {
      unsigned int expected_width = send_codec_.width;
      unsigned int expected_height = send_codec_.height;
      int num_active_layers = 0;
      for (int i = 0; i < send_codec_.VP9()->numberOfSpatialLayers; ++i) {
        if (send_codec_.spatialLayers[i].active) {
          ++num_active_layers;
          expected_width = send_codec_.spatialLayers[i].width;
          expected_height = send_codec_.spatialLayers[i].height;
        }
      }
      RTC_DCHECK_LE(num_active_layers, 1)
          << "VP9 quality scaling is enabled for "
             "SVC with several active layers.";
      encoder_stats_observer_->OnEncoderInternalScalerUpdate(
          image_width < expected_width || image_height < expected_height);
    }
  });

  // Encoded is called on whatever thread the real encoder implementation run
  // on. In the case of hardware encoders, there might be several encoders
  // running in parallel on different threads.
  encoder_stats_observer_->OnSendEncodedImage(image_copy, codec_specific_info);

  EncodedImageCallback::Result result =
      sink_->OnEncodedImage(image_copy, codec_specific_info);

  // We are only interested in propagating the meta-data about the image, not
  // encoded data itself, to the post encode function. Since we cannot be sure
  // the pointer will still be valid when run on the task queue, set it to null.
  DataSize frame_size = DataSize::Bytes(image_copy.size());
  image_copy.ClearEncodedData();

  int temporal_index = 0;
  if (codec_specific_info) {
    if (codec_specific_info->codecType == kVideoCodecVP9) {
      temporal_index = codec_specific_info->codecSpecific.VP9.temporal_idx;
    } else if (codec_specific_info->codecType == kVideoCodecVP8) {
      temporal_index = codec_specific_info->codecSpecific.VP8.temporalIdx;
    }
  }
  if (temporal_index == kNoTemporalIdx) {
    temporal_index = 0;
  }

  RunPostEncode(image_copy, clock_->CurrentTime().us(), temporal_index,
                frame_size);

  if (result.error == Result::OK) {
    // In case of an internal encoder running on a separate thread, the
    // decision to drop a frame might be a frame late and signaled via
    // atomic flag. This is because we can't easily wait for the worker thread
    // without risking deadlocks, eg during shutdown when the worker thread
    // might be waiting for the internal encoder threads to stop.
    if (pending_frame_drops_.load() > 0) {
      int pending_drops = pending_frame_drops_.fetch_sub(1);
      RTC_DCHECK_GT(pending_drops, 0);
      result.drop_next_frame = true;
    }
  }

  return result;
}

void VideoStreamEncoder::OnDroppedFrame(DropReason reason) {
  switch (reason) {
    case DropReason::kDroppedByMediaOptimizations:
      encoder_stats_observer_->OnFrameDropped(
          VideoStreamEncoderObserver::DropReason::kMediaOptimization);
      break;
    case DropReason::kDroppedByEncoder:
      encoder_stats_observer_->OnFrameDropped(
          VideoStreamEncoderObserver::DropReason::kEncoder);
      break;
  }
  sink_->OnDroppedFrame(reason);
  encoder_queue_.PostTask([this, reason] {
    RTC_DCHECK_RUN_ON(&encoder_queue_);
    stream_resource_manager_.OnFrameDropped(reason);
  });
}

DataRate VideoStreamEncoder::UpdateTargetBitrate(DataRate target_bitrate,
                                                 double cwnd_reduce_ratio) {
  RTC_DCHECK_RUN_ON(&encoder_queue_);
  DataRate updated_target_bitrate = target_bitrate;

  // Drop frames when congestion window pushback ratio is larger than 1
  // percent and target bitrate is larger than codec min bitrate.
  // When target_bitrate is 0 means codec is paused, skip frame dropping.
  if (cwnd_reduce_ratio > 0.01 && target_bitrate.bps() > 0 &&
      target_bitrate.bps() > send_codec_.minBitrate * 1000) {
    int reduce_bitrate_bps = std::min(
        static_cast<int>(target_bitrate.bps() * cwnd_reduce_ratio),
        static_cast<int>(target_bitrate.bps() - send_codec_.minBitrate * 1000));
    if (reduce_bitrate_bps > 0) {
      // At maximum the congestion window can drop 1/2 frames.
      cwnd_frame_drop_interval_ = std::max(
          2, static_cast<int>(target_bitrate.bps() / reduce_bitrate_bps));
      // Reduce target bitrate accordingly.
      updated_target_bitrate =
          target_bitrate - (target_bitrate / cwnd_frame_drop_interval_.value());
      return updated_target_bitrate;
    }
  }
  cwnd_frame_drop_interval_.reset();
  return updated_target_bitrate;
}

void VideoStreamEncoder::OnBitrateUpdated(DataRate target_bitrate,
                                          DataRate stable_target_bitrate,
                                          DataRate link_allocation,
                                          uint8_t fraction_lost,
                                          int64_t round_trip_time_ms,
                                          double cwnd_reduce_ratio) {
  RTC_DCHECK_GE(link_allocation, target_bitrate);
  if (!encoder_queue_.IsCurrent()) {
    encoder_queue_.PostTask([this, target_bitrate, stable_target_bitrate,
                             link_allocation, fraction_lost, round_trip_time_ms,
                             cwnd_reduce_ratio] {
      DataRate updated_target_bitrate =
          UpdateTargetBitrate(target_bitrate, cwnd_reduce_ratio);
      OnBitrateUpdated(updated_target_bitrate, stable_target_bitrate,
                       link_allocation, fraction_lost, round_trip_time_ms,
                       cwnd_reduce_ratio);
    });
    return;
  }
  RTC_DCHECK_RUN_ON(&encoder_queue_);

  const bool video_is_suspended = target_bitrate == DataRate::Zero();
  const bool video_suspension_changed = video_is_suspended != EncoderPaused();

  if (!video_is_suspended && settings_.encoder_switch_request_callback &&
      encoder_selector_) {
    if (auto encoder = encoder_selector_->OnAvailableBitrate(link_allocation)) {
      settings_.encoder_switch_request_callback->RequestEncoderSwitch(
          *encoder, /*allow_default_fallback=*/false);
    }
  }

  RTC_DCHECK(sink_) << "sink_ must be set before the encoder is active.";

  RTC_LOG(LS_VERBOSE) << "OnBitrateUpdated, bitrate " << target_bitrate.bps()
                      << " stable bitrate = " << stable_target_bitrate.bps()
                      << " link allocation bitrate = " << link_allocation.bps()
                      << " packet loss " << static_cast<int>(fraction_lost)
                      << " rtt " << round_trip_time_ms;

  if (encoder_) {
    encoder_->OnPacketLossRateUpdate(static_cast<float>(fraction_lost) / 256.f);
    encoder_->OnRttUpdate(round_trip_time_ms);
  }

  uint32_t framerate_fps = GetInputFramerateFps();
  frame_dropper_.SetRates((target_bitrate.bps() + 500) / 1000, framerate_fps);

  EncoderRateSettings new_rate_settings{
      VideoBitrateAllocation(), static_cast<double>(framerate_fps),
      link_allocation, target_bitrate, stable_target_bitrate};
  SetEncoderRates(UpdateBitrateAllocation(new_rate_settings));

  if (target_bitrate.bps() != 0)
    encoder_target_bitrate_bps_ = target_bitrate.bps();

  stream_resource_manager_.SetTargetBitrate(target_bitrate);

  if (video_suspension_changed) {
    RTC_LOG(LS_INFO) << "Video suspend state changed to: "
                     << (video_is_suspended ? "suspended" : "not suspended");
    encoder_stats_observer_->OnSuspendChange(video_is_suspended);

    if (!video_is_suspended && pending_frame_ &&
        !DropDueToSize(pending_frame_->size())) {
      // A pending stored frame can be processed.
      int64_t pending_time_us =
          clock_->CurrentTime().us() - pending_frame_post_time_us_;
      if (pending_time_us < kPendingFrameTimeoutMs * 1000)
        EncodeVideoFrame(*pending_frame_, pending_frame_post_time_us_);
      pending_frame_.reset();
    } else if (!video_is_suspended && !pending_frame_ &&
               encoder_paused_and_dropped_frame_) {
      // A frame was enqueued during pause-state, but since it was a native
      // frame we could not store it in `pending_frame_` so request a
      // refresh-frame instead.
      RequestRefreshFrame();
    }
  }
}

bool VideoStreamEncoder::DropDueToSize(uint32_t pixel_count) const {
  if (!encoder_ || !stream_resource_manager_.DropInitialFrames() ||
      !encoder_target_bitrate_bps_.has_value()) {
    return false;
  }

  bool simulcast_or_svc =
      (send_codec_.codecType == VideoCodecType::kVideoCodecVP9 &&
       send_codec_.VP9().numberOfSpatialLayers > 1) ||
      (send_codec_.numberOfSimulcastStreams > 1 ||
       encoder_config_.simulcast_layers.size() > 1);

  if (simulcast_or_svc) {
    if (stream_resource_manager_.SingleActiveStreamPixels()) {
      pixel_count = stream_resource_manager_.SingleActiveStreamPixels().value();
    } else {
      return false;
    }
  }

  uint32_t bitrate_bps =
      stream_resource_manager_.UseBandwidthAllocationBps().value_or(
          encoder_target_bitrate_bps_.value());

  absl::optional<VideoEncoder::ResolutionBitrateLimits> encoder_bitrate_limits =
      GetEncoderInfoWithBitrateLimitUpdate(
          encoder_->GetEncoderInfo(), encoder_config_, default_limits_allowed_)
          .GetEncoderBitrateLimitsForResolution(pixel_count);

  if (encoder_bitrate_limits.has_value()) {
    // Use bitrate limits provided by encoder.
    return bitrate_bps <
           static_cast<uint32_t>(encoder_bitrate_limits->min_start_bitrate_bps);
  }

  if (bitrate_bps < 300000 /* qvga */) {
    return pixel_count > 320 * 240;
  } else if (bitrate_bps < 500000 /* vga */) {
    return pixel_count > 640 * 480;
  }
  return false;
}

void VideoStreamEncoder::OnVideoSourceRestrictionsUpdated(
    VideoSourceRestrictions restrictions,
    const VideoAdaptationCounters& adaptation_counters,
    rtc::scoped_refptr<Resource> reason,
    const VideoSourceRestrictions& unfiltered_restrictions) {
  RTC_DCHECK_RUN_ON(&encoder_queue_);
  RTC_LOG(LS_INFO) << "Updating sink restrictions from "
                   << (reason ? reason->Name() : std::string("<null>"))
                   << " to " << restrictions.ToString();

  // TODO(webrtc:14451) Split video_source_sink_controller_
  // so that ownership on restrictions/wants is kept on &encoder_queue_
  latest_restrictions_ = restrictions;

  worker_queue_->PostTask(SafeTask(
      task_safety_.flag(), [this, restrictions = std::move(restrictions)]() {
        RTC_DCHECK_RUN_ON(worker_queue_);
        video_source_sink_controller_.SetRestrictions(std::move(restrictions));
        video_source_sink_controller_.PushSourceSinkSettings();
      }));
}

void VideoStreamEncoder::RunPostEncode(const EncodedImage& encoded_image,
                                       int64_t time_sent_us,
                                       int temporal_index,
                                       DataSize frame_size) {
  if (!encoder_queue_.IsCurrent()) {
    encoder_queue_.PostTask([this, encoded_image, time_sent_us, temporal_index,
                             frame_size] {
      RunPostEncode(encoded_image, time_sent_us, temporal_index, frame_size);
    });
    return;
  }

  RTC_DCHECK_RUN_ON(&encoder_queue_);

  absl::optional<int> encode_duration_us;
  if (encoded_image.timing_.flags != VideoSendTiming::kInvalid) {
    encode_duration_us =
        TimeDelta::Millis(encoded_image.timing_.encode_finish_ms -
                          encoded_image.timing_.encode_start_ms)
            .us();
  }

  // Run post encode tasks, such as overuse detection and frame rate/drop
  // stats for internal encoders.
  const bool keyframe =
      encoded_image._frameType == VideoFrameType::kVideoFrameKey;

  if (!frame_size.IsZero()) {
    frame_dropper_.Fill(frame_size.bytes(), !keyframe);
  }

  stream_resource_manager_.OnEncodeCompleted(encoded_image, time_sent_us,
                                             encode_duration_us, frame_size);
  if (bitrate_adjuster_) {
    bitrate_adjuster_->OnEncodedFrame(
        frame_size, encoded_image.SpatialIndex().value_or(0), temporal_index);
  }
}

void VideoStreamEncoder::ReleaseEncoder() {
  if (!encoder_ || !encoder_initialized_) {
    return;
  }
  encoder_->Release();
  encoder_initialized_ = false;
  TRACE_EVENT0("webrtc", "VCMGenericEncoder::Release");
}

VideoStreamEncoder::AutomaticAnimationDetectionExperiment
VideoStreamEncoder::ParseAutomatincAnimationDetectionFieldTrial() const {
  AutomaticAnimationDetectionExperiment result;

  result.Parser()->Parse(
      field_trials_.Lookup("WebRTC-AutomaticAnimationDetectionScreenshare"));

  if (!result.enabled) {
    RTC_LOG(LS_INFO) << "Automatic animation detection experiment is disabled.";
    return result;
  }

  RTC_LOG(LS_INFO) << "Automatic animation detection experiment settings:"
                      " min_duration_ms="
                   << result.min_duration_ms
                   << " min_area_ration=" << result.min_area_ratio
                   << " min_fps=" << result.min_fps;

  return result;
}

void VideoStreamEncoder::CheckForAnimatedContent(
    const VideoFrame& frame,
    int64_t time_when_posted_in_us) {
  if (!automatic_animation_detection_experiment_.enabled ||
      encoder_config_.content_type !=
          VideoEncoderConfig::ContentType::kScreen ||
      stream_resource_manager_.degradation_preference() !=
          DegradationPreference::BALANCED) {
    return;
  }

  if (expect_resize_state_ == ExpectResizeState::kResize && last_frame_info_ &&
      last_frame_info_->width != frame.width() &&
      last_frame_info_->height != frame.height()) {
    // On applying resolution cap there will be one frame with no/different
    // update, which should be skipped.
    // It can be delayed by several frames.
    expect_resize_state_ = ExpectResizeState::kFirstFrameAfterResize;
    return;
  }

  if (expect_resize_state_ == ExpectResizeState::kFirstFrameAfterResize) {
    // The first frame after resize should have new, scaled update_rect.
    if (frame.has_update_rect()) {
      last_update_rect_ = frame.update_rect();
    } else {
      last_update_rect_ = absl::nullopt;
    }
    expect_resize_state_ = ExpectResizeState::kNoResize;
  }

  bool should_cap_resolution = false;
  if (!frame.has_update_rect()) {
    last_update_rect_ = absl::nullopt;
    animation_start_time_ = Timestamp::PlusInfinity();
  } else if ((!last_update_rect_ ||
              frame.update_rect() != *last_update_rect_)) {
    last_update_rect_ = frame.update_rect();
    animation_start_time_ = Timestamp::Micros(time_when_posted_in_us);
  } else {
    TimeDelta animation_duration =
        Timestamp::Micros(time_when_posted_in_us) - animation_start_time_;
    float area_ratio = static_cast<float>(last_update_rect_->width *
                                          last_update_rect_->height) /
                       (frame.width() * frame.height());
    if (animation_duration.ms() >=
            automatic_animation_detection_experiment_.min_duration_ms &&
        area_ratio >=
            automatic_animation_detection_experiment_.min_area_ratio &&
        encoder_stats_observer_->GetInputFrameRate() >=
            automatic_animation_detection_experiment_.min_fps) {
      should_cap_resolution = true;
    }
  }
  if (cap_resolution_due_to_video_content_ != should_cap_resolution) {
    expect_resize_state_ = should_cap_resolution ? ExpectResizeState::kResize
                                                 : ExpectResizeState::kNoResize;
    cap_resolution_due_to_video_content_ = should_cap_resolution;
    if (should_cap_resolution) {
      RTC_LOG(LS_INFO) << "Applying resolution cap due to animation detection.";
    } else {
      RTC_LOG(LS_INFO) << "Removing resolution cap due to no consistent "
                          "animation detection.";
    }
    // TODO(webrtc:14451) Split video_source_sink_controller_
    // so that ownership on restrictions/wants is kept on &encoder_queue_
    if (should_cap_resolution) {
      animate_restrictions_ =
          VideoSourceRestrictions(kMaxAnimationPixels,
                                  /* target_pixels_per_frame= */ absl::nullopt,
                                  /* max_frame_rate= */ absl::nullopt);
    } else {
      animate_restrictions_.reset();
    }

    worker_queue_->PostTask(
        SafeTask(task_safety_.flag(), [this, should_cap_resolution]() {
          RTC_DCHECK_RUN_ON(worker_queue_);
          video_source_sink_controller_.SetPixelsPerFrameUpperLimit(
              should_cap_resolution
                  ? absl::optional<size_t>(kMaxAnimationPixels)
                  : absl::nullopt);
          video_source_sink_controller_.PushSourceSinkSettings();
        }));
  }
}

void VideoStreamEncoder::InjectAdaptationResource(
    rtc::scoped_refptr<Resource> resource,
    VideoAdaptationReason reason) {
  encoder_queue_.PostTask([this, resource = std::move(resource), reason] {
    RTC_DCHECK_RUN_ON(&encoder_queue_);
    additional_resources_.push_back(resource);
    stream_resource_manager_.AddResource(resource, reason);
  });
}

void VideoStreamEncoder::InjectAdaptationConstraint(
    AdaptationConstraint* adaptation_constraint) {
  rtc::Event event;
  encoder_queue_.PostTask([this, adaptation_constraint, &event] {
    RTC_DCHECK_RUN_ON(&encoder_queue_);
    if (!resource_adaptation_processor_) {
      // The VideoStreamEncoder was stopped and the processor destroyed before
      // this task had a chance to execute. No action needed.
      return;
    }
    adaptation_constraints_.push_back(adaptation_constraint);
    video_stream_adapter_->AddAdaptationConstraint(adaptation_constraint);
    event.Set();
  });
  event.Wait(rtc::Event::kForever);
}

void VideoStreamEncoder::AddRestrictionsListenerForTesting(
    VideoSourceRestrictionsListener* restrictions_listener) {
  rtc::Event event;
  encoder_queue_.PostTask([this, restrictions_listener, &event] {
    RTC_DCHECK_RUN_ON(&encoder_queue_);
    RTC_DCHECK(resource_adaptation_processor_);
    video_stream_adapter_->AddRestrictionsListener(restrictions_listener);
    event.Set();
  });
  event.Wait(rtc::Event::kForever);
}

void VideoStreamEncoder::RemoveRestrictionsListenerForTesting(
    VideoSourceRestrictionsListener* restrictions_listener) {
  rtc::Event event;
  encoder_queue_.PostTask([this, restrictions_listener, &event] {
    RTC_DCHECK_RUN_ON(&encoder_queue_);
    RTC_DCHECK(resource_adaptation_processor_);
    video_stream_adapter_->RemoveRestrictionsListener(restrictions_listener);
    event.Set();
  });
  event.Wait(rtc::Event::kForever);
}

}  // namespace webrtc

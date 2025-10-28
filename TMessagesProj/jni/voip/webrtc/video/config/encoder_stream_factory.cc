/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "video/config/encoder_stream_factory.h"

#include <algorithm>
#include <limits>
#include <set>
#include <string>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/strings/match.h"
#include "api/video/video_codec_constants.h"
#include "media/base/media_constants.h"
#include "media/base/video_adapter.h"
#include "modules/video_coding/codecs/vp9/svc_config.h"
#include "rtc_base/experiments/min_video_bitrate_experiment.h"
#include "rtc_base/experiments/normalize_simulcast_size_experiment.h"
#include "rtc_base/logging.h"
#include "video/config/simulcast.h"

namespace cricket {
namespace {

const int kMinLayerSize = 16;

int ScaleDownResolution(int resolution,
                        double scale_down_by,
                        int min_resolution) {
  // Resolution is never scalied down to smaller than min_resolution.
  // If the input resolution is already smaller than min_resolution,
  // no scaling should be done at all.
  if (resolution <= min_resolution)
    return resolution;
  return std::max(static_cast<int>(resolution / scale_down_by + 0.5),
                  min_resolution);
}

bool PowerOfTwo(int value) {
  return (value > 0) && ((value & (value - 1)) == 0);
}

bool IsScaleFactorsPowerOfTwo(const webrtc::VideoEncoderConfig& config) {
  for (const auto& layer : config.simulcast_layers) {
    double scale = std::max(layer.scale_resolution_down_by, 1.0);
    if (std::round(scale) != scale || !PowerOfTwo(scale)) {
      return false;
    }
  }
  return true;
}

bool IsTemporalLayersSupported(const std::string& codec_name) {
  return absl::EqualsIgnoreCase(codec_name, kVp8CodecName) ||
         absl::EqualsIgnoreCase(codec_name, kVp9CodecName) ||
         absl::EqualsIgnoreCase(codec_name, kAv1CodecName);
}

size_t FindRequiredActiveLayers(
    const webrtc::VideoEncoderConfig& encoder_config) {
  // Need enough layers so that at least the first active one is present.
  for (size_t i = 0; i < encoder_config.number_of_streams; ++i) {
    if (encoder_config.simulcast_layers[i].active) {
      return i + 1;
    }
  }
  return 0;
}

// The selected thresholds for QVGA and VGA corresponded to a QP around 10.
// The change in QP declined above the selected bitrates.
static int GetMaxDefaultVideoBitrateKbps(int width,
                                         int height,
                                         bool is_screenshare) {
  int max_bitrate;
  if (width * height <= 320 * 240) {
    max_bitrate = 600;
  } else if (width * height <= 640 * 480) {
    max_bitrate = 1700;
  } else if (width * height <= 960 * 540) {
    max_bitrate = 2000;
  } else {
    max_bitrate = 2500;
  }
  if (is_screenshare)
    max_bitrate = std::max(max_bitrate, 1200);
  return max_bitrate;
}

}  // namespace

// TODO(bugs.webrtc.org/8785): Consider removing max_qp as member of
// EncoderStreamFactory and instead set this value individually for each stream
// in the VideoEncoderConfig.simulcast_layers.
EncoderStreamFactory::EncoderStreamFactory(std::string codec_name,
                                           int max_qp,
                                           bool is_screenshare,
                                           bool conference_mode)
    : codec_name_(codec_name),
      max_qp_(max_qp),
      is_screenshare_(is_screenshare),
      conference_mode_(conference_mode),
      trials_(fallback_trials_),
      encoder_info_requested_resolution_alignment_(1) {}

EncoderStreamFactory::EncoderStreamFactory(
    std::string codec_name,
    int max_qp,
    bool is_screenshare,
    bool conference_mode,
    const webrtc::VideoEncoder::EncoderInfo& encoder_info,
    absl::optional<webrtc::VideoSourceRestrictions> restrictions,
    const webrtc::FieldTrialsView* trials)
    : codec_name_(codec_name),
      max_qp_(max_qp),
      is_screenshare_(is_screenshare),
      conference_mode_(conference_mode),
      trials_(trials ? *trials : fallback_trials_),
      encoder_info_requested_resolution_alignment_(
          encoder_info.requested_resolution_alignment),
      restrictions_(restrictions) {}

std::vector<webrtc::VideoStream> EncoderStreamFactory::CreateEncoderStreams(
    int frame_width,
    int frame_height,
    const webrtc::VideoEncoderConfig& encoder_config) {
  RTC_DCHECK_GT(encoder_config.number_of_streams, 0);
  RTC_DCHECK_GE(encoder_config.simulcast_layers.size(),
                encoder_config.number_of_streams);

  const absl::optional<webrtc::DataRate> experimental_min_bitrate =
      GetExperimentalMinVideoBitrate(encoder_config.codec_type);

  bool is_simulcast = (encoder_config.number_of_streams > 1);
  // If scalability mode was specified, don't treat {active,inactive,inactive}
  // as simulcast since the simulcast configuration assumes very low bitrates
  // on the first layer. This would prevent rampup of multiple spatial layers.
  // See https://crbug.com/webrtc/15041.
  if (is_simulcast &&
      encoder_config.simulcast_layers[0].scalability_mode.has_value()) {
    // Require at least one non-first layer to be active for is_simulcast=true.
    is_simulcast = false;
    for (size_t i = 1; i < encoder_config.simulcast_layers.size(); ++i) {
      if (encoder_config.simulcast_layers[i].active) {
        is_simulcast = true;
        break;
      }
    }
  }

  if (is_simulcast || ((absl::EqualsIgnoreCase(codec_name_, kVp8CodecName) ||
                        absl::EqualsIgnoreCase(codec_name_, kH264CodecName)) &&
                       is_screenshare_ && conference_mode_)) {
    return CreateSimulcastOrConferenceModeScreenshareStreams(
        frame_width, frame_height, encoder_config, experimental_min_bitrate);
  }

  return CreateDefaultVideoStreams(frame_width, frame_height, encoder_config,
                                   experimental_min_bitrate);
}

std::vector<webrtc::VideoStream>
EncoderStreamFactory::CreateDefaultVideoStreams(
    int width,
    int height,
    const webrtc::VideoEncoderConfig& encoder_config,
    const absl::optional<webrtc::DataRate>& experimental_min_bitrate) const {
  std::vector<webrtc::VideoStream> layers;

  // The max bitrate specified by the API.
  // - `encoder_config.simulcast_layers[0].max_bitrate_bps` comes from the first
  //   RtpEncodingParamters, which is the encoding of this stream.
  // - `encoder_config.max_bitrate_bps` comes from SDP; "b=AS" or conditionally
  //   "x-google-max-bitrate".
  // If `api_max_bitrate_bps` has a value then it is positive.
  absl::optional<int> api_max_bitrate_bps;
  if (encoder_config.simulcast_layers[0].max_bitrate_bps > 0) {
    api_max_bitrate_bps = encoder_config.simulcast_layers[0].max_bitrate_bps;
  }
  if (encoder_config.max_bitrate_bps > 0) {
    api_max_bitrate_bps =
        api_max_bitrate_bps.has_value()
            ? std::min(encoder_config.max_bitrate_bps, *api_max_bitrate_bps)
            : encoder_config.max_bitrate_bps;
  }

  // For unset max bitrates set default bitrate for non-simulcast.
  int max_bitrate_bps =
      api_max_bitrate_bps.has_value()
          ? *api_max_bitrate_bps
          : GetMaxDefaultVideoBitrateKbps(width, height, is_screenshare_) *
                1000;

  int min_bitrate_bps =
      experimental_min_bitrate
          ? rtc::saturated_cast<int>(experimental_min_bitrate->bps())
          : webrtc::kDefaultMinVideoBitrateBps;
  if (encoder_config.simulcast_layers[0].min_bitrate_bps > 0) {
    // Use set min bitrate.
    min_bitrate_bps = encoder_config.simulcast_layers[0].min_bitrate_bps;
    // If only min bitrate is configured, make sure max is above min.
    if (!api_max_bitrate_bps.has_value())
      max_bitrate_bps = std::max(min_bitrate_bps, max_bitrate_bps);
  }
  int max_framerate = (encoder_config.simulcast_layers[0].max_framerate > 0)
                          ? encoder_config.simulcast_layers[0].max_framerate
                          : kDefaultVideoMaxFramerate;

  webrtc::VideoStream layer;
  layer.width = width;
  layer.height = height;
  layer.max_framerate = max_framerate;
  layer.requested_resolution =
      encoder_config.simulcast_layers[0].requested_resolution;
  // Note: VP9 seems to have be sending if any layer is active,
  // (see `UpdateSendState`) and still use parameters only from
  // encoder_config.simulcast_layers[0].
  layer.active = absl::c_any_of(encoder_config.simulcast_layers,
                                [](const auto& layer) { return layer.active; });

  if (encoder_config.simulcast_layers[0].requested_resolution) {
    auto res = GetLayerResolutionFromRequestedResolution(
        width, height,
        *encoder_config.simulcast_layers[0].requested_resolution);
    layer.width = res.width;
    layer.height = res.height;
  } else if (encoder_config.simulcast_layers[0].scale_resolution_down_by > 1.) {
    layer.width = ScaleDownResolution(
        layer.width,
        encoder_config.simulcast_layers[0].scale_resolution_down_by,
        kMinLayerSize);
    layer.height = ScaleDownResolution(
        layer.height,
        encoder_config.simulcast_layers[0].scale_resolution_down_by,
        kMinLayerSize);
  }

  if (absl::EqualsIgnoreCase(codec_name_, kVp9CodecName)) {
    RTC_DCHECK(encoder_config.encoder_specific_settings);
    // Use VP9 SVC layering from codec settings which might be initialized
    // though field trial in ConfigureVideoEncoderSettings.
    webrtc::VideoCodecVP9 vp9_settings;
    encoder_config.encoder_specific_settings->FillVideoCodecVp9(&vp9_settings);
    layer.num_temporal_layers = vp9_settings.numberOfTemporalLayers;

    // Number of spatial layers is signalled differently from different call
    // sites (sigh), pick the max as we are interested in the upper bound.
    int num_spatial_layers =
        std::max({encoder_config.simulcast_layers.size(),
                  encoder_config.spatial_layers.size(),
                  size_t{vp9_settings.numberOfSpatialLayers}});

    if (width * height > 0 &&
        (layer.num_temporal_layers > 1u || num_spatial_layers > 1)) {
      // In SVC mode, the VP9 max bitrate is determined by SvcConfig, instead of
      // GetMaxDefaultVideoBitrateKbps().
      std::vector<webrtc::SpatialLayer> svc_layers =
          webrtc::GetSvcConfig(width, height, max_framerate,
                               /*first_active_layer=*/0, num_spatial_layers,
                               *layer.num_temporal_layers, is_screenshare_);
      int sum_max_bitrates_kbps = 0;
      for (const webrtc::SpatialLayer& spatial_layer : svc_layers) {
        sum_max_bitrates_kbps += spatial_layer.maxBitrate;
      }
      RTC_DCHECK_GE(sum_max_bitrates_kbps, 0);
      if (!api_max_bitrate_bps.has_value()) {
        max_bitrate_bps = sum_max_bitrates_kbps * 1000;
      } else {
        max_bitrate_bps =
            std::min(max_bitrate_bps, sum_max_bitrates_kbps * 1000);
      }
      max_bitrate_bps = std::max(min_bitrate_bps, max_bitrate_bps);
    }
  }

  // In the case that the application sets a max bitrate that's lower than the
  // min bitrate, we adjust it down (see bugs.webrtc.org/9141).
  layer.min_bitrate_bps = std::min(min_bitrate_bps, max_bitrate_bps);
  if (encoder_config.simulcast_layers[0].target_bitrate_bps <= 0) {
    layer.target_bitrate_bps = max_bitrate_bps;
  } else {
    layer.target_bitrate_bps = std::min(
        encoder_config.simulcast_layers[0].target_bitrate_bps, max_bitrate_bps);
  }
  layer.max_bitrate_bps = max_bitrate_bps;
  layer.max_qp = max_qp_;
  layer.bitrate_priority = encoder_config.bitrate_priority;

  if (IsTemporalLayersSupported(codec_name_)) {
    // Use configured number of temporal layers if set.
    if (encoder_config.simulcast_layers[0].num_temporal_layers) {
      layer.num_temporal_layers =
          *encoder_config.simulcast_layers[0].num_temporal_layers;
    }
  }
  layer.scalability_mode = encoder_config.simulcast_layers[0].scalability_mode;
  layers.push_back(layer);
  return layers;
}

std::vector<webrtc::VideoStream>
EncoderStreamFactory::CreateSimulcastOrConferenceModeScreenshareStreams(
    int width,
    int height,
    const webrtc::VideoEncoderConfig& encoder_config,
    const absl::optional<webrtc::DataRate>& experimental_min_bitrate) const {
  std::vector<webrtc::VideoStream> layers;

  const bool temporal_layers_supported = IsTemporalLayersSupported(codec_name_);
  // Use legacy simulcast screenshare if conference mode is explicitly enabled
  // or use the regular simulcast configuration path which is generic.
  layers = GetSimulcastConfig(FindRequiredActiveLayers(encoder_config),
                              encoder_config.number_of_streams, width, height,
                              encoder_config.bitrate_priority, max_qp_,
                              is_screenshare_ && conference_mode_,
                              temporal_layers_supported, trials_);
  // Allow an experiment to override the minimum bitrate for the lowest
  // spatial layer. The experiment's configuration has the lowest priority.
  if (experimental_min_bitrate) {
    layers[0].min_bitrate_bps =
        rtc::saturated_cast<int>(experimental_min_bitrate->bps());
  }
  // Update the active simulcast layers and configured bitrates.
  bool is_highest_layer_max_bitrate_configured = false;
  const bool has_scale_resolution_down_by = absl::c_any_of(
      encoder_config.simulcast_layers, [](const webrtc::VideoStream& layer) {
        return layer.scale_resolution_down_by != -1.;
      });

  bool default_scale_factors_used = true;
  if (has_scale_resolution_down_by) {
    default_scale_factors_used = IsScaleFactorsPowerOfTwo(encoder_config);
  }
  const bool norm_size_configured =
      webrtc::NormalizeSimulcastSizeExperiment::GetBase2Exponent().has_value();
  const int normalized_width =
      (default_scale_factors_used || norm_size_configured) &&
              (width >= kMinLayerSize)
          ? NormalizeSimulcastSize(width, encoder_config.number_of_streams)
          : width;
  const int normalized_height =
      (default_scale_factors_used || norm_size_configured) &&
              (height >= kMinLayerSize)
          ? NormalizeSimulcastSize(height, encoder_config.number_of_streams)
          : height;
  for (size_t i = 0; i < layers.size(); ++i) {
    layers[i].active = encoder_config.simulcast_layers[i].active;
    layers[i].scalability_mode =
        encoder_config.simulcast_layers[i].scalability_mode;
    layers[i].requested_resolution =
        encoder_config.simulcast_layers[i].requested_resolution;
    // Update with configured num temporal layers if supported by codec.
    if (encoder_config.simulcast_layers[i].num_temporal_layers &&
        IsTemporalLayersSupported(codec_name_)) {
      layers[i].num_temporal_layers =
          *encoder_config.simulcast_layers[i].num_temporal_layers;
    }
    if (encoder_config.simulcast_layers[i].max_framerate > 0) {
      layers[i].max_framerate =
          encoder_config.simulcast_layers[i].max_framerate;
    }
    if (encoder_config.simulcast_layers[i].requested_resolution.has_value()) {
      auto res = GetLayerResolutionFromRequestedResolution(
          normalized_width, normalized_height,
          *encoder_config.simulcast_layers[i].requested_resolution);
      layers[i].width = res.width;
      layers[i].height = res.height;
    } else if (has_scale_resolution_down_by) {
      const double scale_resolution_down_by = std::max(
          encoder_config.simulcast_layers[i].scale_resolution_down_by, 1.0);
      layers[i].width = ScaleDownResolution(
          normalized_width, scale_resolution_down_by, kMinLayerSize);
      layers[i].height = ScaleDownResolution(
          normalized_height, scale_resolution_down_by, kMinLayerSize);
    }
    // Update simulcast bitrates with configured min and max bitrate.
    if (encoder_config.simulcast_layers[i].min_bitrate_bps > 0) {
      layers[i].min_bitrate_bps =
          encoder_config.simulcast_layers[i].min_bitrate_bps;
    }
    if (encoder_config.simulcast_layers[i].max_bitrate_bps > 0) {
      layers[i].max_bitrate_bps =
          encoder_config.simulcast_layers[i].max_bitrate_bps;
    }
    if (encoder_config.simulcast_layers[i].target_bitrate_bps > 0) {
      layers[i].target_bitrate_bps =
          encoder_config.simulcast_layers[i].target_bitrate_bps;
    }
    if (encoder_config.simulcast_layers[i].min_bitrate_bps > 0 &&
        encoder_config.simulcast_layers[i].max_bitrate_bps > 0) {
      // Min and max bitrate are configured.
      // Set target to 3/4 of the max bitrate (or to max if below min).
      if (encoder_config.simulcast_layers[i].target_bitrate_bps <= 0)
        layers[i].target_bitrate_bps = layers[i].max_bitrate_bps * 3 / 4;
      if (layers[i].target_bitrate_bps < layers[i].min_bitrate_bps)
        layers[i].target_bitrate_bps = layers[i].max_bitrate_bps;
    } else if (encoder_config.simulcast_layers[i].min_bitrate_bps > 0) {
      // Only min bitrate is configured, make sure target/max are above min.
      layers[i].target_bitrate_bps =
          std::max(layers[i].target_bitrate_bps, layers[i].min_bitrate_bps);
      layers[i].max_bitrate_bps =
          std::max(layers[i].max_bitrate_bps, layers[i].min_bitrate_bps);
    } else if (encoder_config.simulcast_layers[i].max_bitrate_bps > 0) {
      // Only max bitrate is configured, make sure min/target are below max.
      // Keep target bitrate if it is set explicitly in encoding config.
      // Otherwise set target bitrate to 3/4 of the max bitrate
      // or the one calculated from GetSimulcastConfig() which is larger.
      layers[i].min_bitrate_bps =
          std::min(layers[i].min_bitrate_bps, layers[i].max_bitrate_bps);
      if (encoder_config.simulcast_layers[i].target_bitrate_bps <= 0) {
        layers[i].target_bitrate_bps = std::max(
            layers[i].target_bitrate_bps, layers[i].max_bitrate_bps * 3 / 4);
      }
      layers[i].target_bitrate_bps = std::max(
          std::min(layers[i].target_bitrate_bps, layers[i].max_bitrate_bps),
          layers[i].min_bitrate_bps);
    }
    if (i == layers.size() - 1) {
      is_highest_layer_max_bitrate_configured =
          encoder_config.simulcast_layers[i].max_bitrate_bps > 0;
    }
  }
  if (!is_screenshare_ && !is_highest_layer_max_bitrate_configured &&
      encoder_config.max_bitrate_bps > 0) {
    // No application-configured maximum for the largest layer.
    // If there is bitrate leftover, give it to the largest layer.
    BoostMaxSimulcastLayer(
        webrtc::DataRate::BitsPerSec(encoder_config.max_bitrate_bps), &layers);
  }

  // Sort the layers by max_bitrate_bps, they might not always be from
  // smallest to biggest
  std::vector<size_t> index(layers.size());
  std::iota(index.begin(), index.end(), 0);
  std::stable_sort(index.begin(), index.end(), [&layers](size_t a, size_t b) {
    return layers[a].max_bitrate_bps < layers[b].max_bitrate_bps;
  });

  if (!layers[index[0]].active) {
    // Adjust min bitrate of the first active layer to allow it to go as low as
    // the lowest (now inactive) layer could.
    // Otherwise, if e.g. a single HD stream is active, it would have 600kbps
    // min bitrate, which would always be allocated to the stream.
    // This would lead to congested network, dropped frames and overall bad
    // experience.

    const int min_configured_bitrate = layers[index[0]].min_bitrate_bps;
    for (size_t i = 0; i < layers.size(); ++i) {
      if (layers[index[i]].active) {
        layers[index[i]].min_bitrate_bps = min_configured_bitrate;
        break;
      }
    }
  }

  return layers;
}

webrtc::Resolution
EncoderStreamFactory::GetLayerResolutionFromRequestedResolution(
    int frame_width,
    int frame_height,
    webrtc::Resolution requested_resolution) const {
  VideoAdapter adapter(encoder_info_requested_resolution_alignment_);
  adapter.OnOutputFormatRequest(requested_resolution.ToPair(),
                                requested_resolution.PixelCount(),
                                absl::nullopt);
  if (restrictions_) {
    rtc::VideoSinkWants wants;
    wants.is_active = true;
    wants.target_pixel_count = restrictions_->target_pixels_per_frame();
    wants.max_pixel_count =
        rtc::dchecked_cast<int>(restrictions_->max_pixels_per_frame().value_or(
            std::numeric_limits<int>::max()));
    wants.aggregates.emplace(rtc::VideoSinkWants::Aggregates());
    wants.resolution_alignment = encoder_info_requested_resolution_alignment_;
    adapter.OnSinkWants(wants);
  }
  int cropped_width, cropped_height;
  int out_width = 0, out_height = 0;
  if (!adapter.AdaptFrameResolution(frame_width, frame_height, 0,
                                    &cropped_width, &cropped_height, &out_width,
                                    &out_height)) {
    RTC_LOG(LS_ERROR) << "AdaptFrameResolution returned false!";
  }
  return {.width = out_width, .height = out_height};
}

}  // namespace cricket

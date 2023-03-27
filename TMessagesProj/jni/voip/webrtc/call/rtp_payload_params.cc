/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/rtp_payload_params.h"

#include <stddef.h>

#include <algorithm>

#include "absl/container/inlined_vector.h"
#include "absl/strings/match.h"
#include "absl/types/variant.h"
#include "api/video/video_timing.h"
#include "modules/video_coding/codecs/h264/include/h264_globals.h"
#include "modules/video_coding/codecs/interface/common_constants.h"
#include "modules/video_coding/codecs/vp8/include/vp8_globals.h"
#include "modules/video_coding/codecs/vp9/include/vp9_globals.h"
#include "modules/video_coding/frame_dependencies_calculator.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/random.h"
#include "rtc_base/time_utils.h"

namespace webrtc {
namespace {

constexpr int kMaxSimulatedSpatialLayers = 3;

void PopulateRtpWithCodecSpecifics(const CodecSpecificInfo& info,
                                   absl::optional<int> spatial_index,
                                   RTPVideoHeader* rtp) {
  rtp->codec = info.codecType;
  rtp->is_last_frame_in_picture = info.end_of_picture;
  switch (info.codecType) {
    case kVideoCodecVP8: {
      auto& vp8_header = rtp->video_type_header.emplace<RTPVideoHeaderVP8>();
      vp8_header.InitRTPVideoHeaderVP8();
      vp8_header.nonReference = info.codecSpecific.VP8.nonReference;
      vp8_header.temporalIdx = info.codecSpecific.VP8.temporalIdx;
      vp8_header.layerSync = info.codecSpecific.VP8.layerSync;
      vp8_header.keyIdx = info.codecSpecific.VP8.keyIdx;
      rtp->simulcastIdx = spatial_index.value_or(0);
      return;
    }
    case kVideoCodecVP9: {
      auto& vp9_header = rtp->video_type_header.emplace<RTPVideoHeaderVP9>();
      vp9_header.InitRTPVideoHeaderVP9();
      vp9_header.inter_pic_predicted =
          info.codecSpecific.VP9.inter_pic_predicted;
      vp9_header.flexible_mode = info.codecSpecific.VP9.flexible_mode;
      vp9_header.ss_data_available = info.codecSpecific.VP9.ss_data_available;
      vp9_header.non_ref_for_inter_layer_pred =
          info.codecSpecific.VP9.non_ref_for_inter_layer_pred;
      vp9_header.temporal_idx = info.codecSpecific.VP9.temporal_idx;
      vp9_header.temporal_up_switch = info.codecSpecific.VP9.temporal_up_switch;
      vp9_header.inter_layer_predicted =
          info.codecSpecific.VP9.inter_layer_predicted;
      vp9_header.gof_idx = info.codecSpecific.VP9.gof_idx;
      vp9_header.num_spatial_layers = info.codecSpecific.VP9.num_spatial_layers;
      vp9_header.first_active_layer = info.codecSpecific.VP9.first_active_layer;
      if (vp9_header.num_spatial_layers > 1) {
        vp9_header.spatial_idx = spatial_index.value_or(kNoSpatialIdx);
      } else {
        vp9_header.spatial_idx = kNoSpatialIdx;
      }
      if (info.codecSpecific.VP9.ss_data_available) {
        vp9_header.spatial_layer_resolution_present =
            info.codecSpecific.VP9.spatial_layer_resolution_present;
        if (info.codecSpecific.VP9.spatial_layer_resolution_present) {
          for (size_t i = 0; i < info.codecSpecific.VP9.num_spatial_layers;
               ++i) {
            vp9_header.width[i] = info.codecSpecific.VP9.width[i];
            vp9_header.height[i] = info.codecSpecific.VP9.height[i];
          }
        }
        vp9_header.gof.CopyGofInfoVP9(info.codecSpecific.VP9.gof);
      }

      vp9_header.num_ref_pics = info.codecSpecific.VP9.num_ref_pics;
      for (int i = 0; i < info.codecSpecific.VP9.num_ref_pics; ++i) {
        vp9_header.pid_diff[i] = info.codecSpecific.VP9.p_diff[i];
      }
      vp9_header.end_of_picture = info.end_of_picture;
      return;
    }
    case kVideoCodecH264: {
      auto& h264_header = rtp->video_type_header.emplace<RTPVideoHeaderH264>();
      h264_header.packetization_mode =
          info.codecSpecific.H264.packetization_mode;
      rtp->simulcastIdx = spatial_index.value_or(0);
      return;
    }
#ifndef DISABLE_H265
    case kVideoCodecH265: {
      auto& h265_header = rtp->video_type_header.emplace<RTPVideoHeaderH265>();
      h265_header.packetization_mode =
          info.codecSpecific.H265.packetization_mode;
    }
    return;
#endif
    case kVideoCodecMultiplex:
    case kVideoCodecGeneric:
      rtp->codec = kVideoCodecGeneric;
      rtp->simulcastIdx = spatial_index.value_or(0);
      return;
    default:
      return;
  }
}

void SetVideoTiming(const EncodedImage& image, VideoSendTiming* timing) {
  if (image.timing_.flags == VideoSendTiming::TimingFrameFlags::kInvalid ||
      image.timing_.flags == VideoSendTiming::TimingFrameFlags::kNotTriggered) {
    timing->flags = VideoSendTiming::TimingFrameFlags::kInvalid;
    return;
  }

  timing->encode_start_delta_ms = VideoSendTiming::GetDeltaCappedMs(
      image.capture_time_ms_, image.timing_.encode_start_ms);
  timing->encode_finish_delta_ms = VideoSendTiming::GetDeltaCappedMs(
      image.capture_time_ms_, image.timing_.encode_finish_ms);
  timing->packetization_finish_delta_ms = 0;
  timing->pacer_exit_delta_ms = 0;
  timing->network_timestamp_delta_ms = 0;
  timing->network2_timestamp_delta_ms = 0;
  timing->flags = image.timing_.flags;
}

// Returns structure that aligns with simulated generic info. The templates
// allow to produce valid dependency descriptor for any stream where
// `num_spatial_layers` * `num_temporal_layers` <= 32 (limited by
// https://aomediacodec.github.io/av1-rtp-spec/#a82-syntax, see
// template_fdiffs()). The set of the templates is not tuned for any paricular
// structure thus dependency descriptor would use more bytes on the wire than
// with tuned templates.
FrameDependencyStructure MinimalisticStructure(int num_spatial_layers,
                                               int num_temporal_layers) {
  RTC_DCHECK_LE(num_spatial_layers, DependencyDescriptor::kMaxSpatialIds);
  RTC_DCHECK_LE(num_temporal_layers, DependencyDescriptor::kMaxTemporalIds);
  RTC_DCHECK_LE(num_spatial_layers * num_temporal_layers, 32);
  FrameDependencyStructure structure;
  structure.num_decode_targets = num_spatial_layers * num_temporal_layers;
  structure.num_chains = num_spatial_layers;
  structure.templates.reserve(num_spatial_layers * num_temporal_layers);
  for (int sid = 0; sid < num_spatial_layers; ++sid) {
    for (int tid = 0; tid < num_temporal_layers; ++tid) {
      FrameDependencyTemplate a_template;
      a_template.spatial_id = sid;
      a_template.temporal_id = tid;
      for (int s = 0; s < num_spatial_layers; ++s) {
        for (int t = 0; t < num_temporal_layers; ++t) {
          // Prefer kSwitch indication for frames that is part of the decode
          // target because dependency descriptor information generated in this
          // class use kSwitch indications more often that kRequired, increasing
          // the chance of a good (or complete) template match.
          a_template.decode_target_indications.push_back(
              sid <= s && tid <= t ? DecodeTargetIndication::kSwitch
                                   : DecodeTargetIndication::kNotPresent);
        }
      }
      a_template.frame_diffs.push_back(tid == 0 ? num_spatial_layers *
                                                      num_temporal_layers
                                                : num_spatial_layers);
      a_template.chain_diffs.assign(structure.num_chains, 1);
      structure.templates.push_back(a_template);

      structure.decode_target_protected_by_chain.push_back(sid);
    }
  }
  return structure;
}
}  // namespace

RtpPayloadParams::RtpPayloadParams(const uint32_t ssrc,
                                   const RtpPayloadState* state,
                                   const FieldTrialsView& trials)
    : ssrc_(ssrc),
      generic_picture_id_experiment_(
          absl::StartsWith(trials.Lookup("WebRTC-GenericPictureId"),
                           "Enabled")),
      simulate_generic_structure_(absl::StartsWith(
          trials.Lookup("WebRTC-GenericCodecDependencyDescriptor"),
          "Enabled")) {
  for (auto& spatial_layer : last_shared_frame_id_)
    spatial_layer.fill(-1);

  chain_last_frame_id_.fill(-1);
  buffer_id_to_frame_id_.fill(-1);

  Random random(rtc::TimeMicros());
  state_.picture_id =
      state ? state->picture_id : (random.Rand<int16_t>() & 0x7FFF);
  state_.tl0_pic_idx = state ? state->tl0_pic_idx : (random.Rand<uint8_t>());
}

RtpPayloadParams::RtpPayloadParams(const RtpPayloadParams& other) = default;

RtpPayloadParams::~RtpPayloadParams() {}

RTPVideoHeader RtpPayloadParams::GetRtpVideoHeader(
    const EncodedImage& image,
    const CodecSpecificInfo* codec_specific_info,
    int64_t shared_frame_id) {
  RTPVideoHeader rtp_video_header;
  if (codec_specific_info) {
    PopulateRtpWithCodecSpecifics(*codec_specific_info, image.SpatialIndex(),
                                  &rtp_video_header);
  }
  rtp_video_header.frame_type = image._frameType;
  rtp_video_header.rotation = image.rotation_;
  rtp_video_header.content_type = image.content_type_;
  rtp_video_header.playout_delay = image.playout_delay_;
  rtp_video_header.width = image._encodedWidth;
  rtp_video_header.height = image._encodedHeight;
  rtp_video_header.color_space = image.ColorSpace()
                                     ? absl::make_optional(*image.ColorSpace())
                                     : absl::nullopt;
  rtp_video_header.video_frame_tracking_id = image.VideoFrameTrackingId();
  SetVideoTiming(image, &rtp_video_header.video_timing);

  const bool is_keyframe = image._frameType == VideoFrameType::kVideoFrameKey;
  const bool first_frame_in_picture =
      (codec_specific_info && codec_specific_info->codecType == kVideoCodecVP9)
          ? codec_specific_info->codecSpecific.VP9.first_frame_in_picture
          : true;

  SetCodecSpecific(&rtp_video_header, first_frame_in_picture);

  SetGeneric(codec_specific_info, shared_frame_id, is_keyframe,
             &rtp_video_header);

  return rtp_video_header;
}

uint32_t RtpPayloadParams::ssrc() const {
  return ssrc_;
}

RtpPayloadState RtpPayloadParams::state() const {
  return state_;
}

void RtpPayloadParams::SetCodecSpecific(RTPVideoHeader* rtp_video_header,
                                        bool first_frame_in_picture) {
  // Always set picture id. Set tl0_pic_idx iff temporal index is set.
  if (first_frame_in_picture) {
    state_.picture_id = (static_cast<uint16_t>(state_.picture_id) + 1) & 0x7FFF;
  }
  if (rtp_video_header->codec == kVideoCodecVP8) {
    auto& vp8_header =
        absl::get<RTPVideoHeaderVP8>(rtp_video_header->video_type_header);
    vp8_header.pictureId = state_.picture_id;

    if (vp8_header.temporalIdx != kNoTemporalIdx) {
      if (vp8_header.temporalIdx == 0) {
        ++state_.tl0_pic_idx;
      }
      vp8_header.tl0PicIdx = state_.tl0_pic_idx;
    }
  }
  if (rtp_video_header->codec == kVideoCodecVP9) {
    auto& vp9_header =
        absl::get<RTPVideoHeaderVP9>(rtp_video_header->video_type_header);
    vp9_header.picture_id = state_.picture_id;

    // Note that in the case that we have no temporal layers but we do have
    // spatial layers, packets will carry layering info with a temporal_idx of
    // zero, and we then have to set and increment tl0_pic_idx.
    if (vp9_header.temporal_idx != kNoTemporalIdx ||
        vp9_header.spatial_idx != kNoSpatialIdx) {
      if (first_frame_in_picture &&
          (vp9_header.temporal_idx == 0 ||
           vp9_header.temporal_idx == kNoTemporalIdx)) {
        ++state_.tl0_pic_idx;
      }
      vp9_header.tl0_pic_idx = state_.tl0_pic_idx;
    }
  }
  if (generic_picture_id_experiment_ &&
      rtp_video_header->codec == kVideoCodecGeneric) {
    rtp_video_header->video_type_header.emplace<RTPVideoHeaderLegacyGeneric>()
        .picture_id = state_.picture_id;
  }
}

RTPVideoHeader::GenericDescriptorInfo
RtpPayloadParams::GenericDescriptorFromFrameInfo(
    const GenericFrameInfo& frame_info,
    int64_t frame_id) {
  RTPVideoHeader::GenericDescriptorInfo generic;
  generic.frame_id = frame_id;
  generic.dependencies = dependencies_calculator_.FromBuffersUsage(
      frame_id, frame_info.encoder_buffers);
  generic.chain_diffs =
      chains_calculator_.From(frame_id, frame_info.part_of_chain);
  generic.spatial_index = frame_info.spatial_id;
  generic.temporal_index = frame_info.temporal_id;
  generic.decode_target_indications = frame_info.decode_target_indications;
  generic.active_decode_targets = frame_info.active_decode_targets;
  return generic;
}

void RtpPayloadParams::SetGeneric(const CodecSpecificInfo* codec_specific_info,
                                  int64_t frame_id,
                                  bool is_keyframe,
                                  RTPVideoHeader* rtp_video_header) {
  if (codec_specific_info && codec_specific_info->generic_frame_info &&
      !codec_specific_info->generic_frame_info->encoder_buffers.empty()) {
    if (is_keyframe) {
      // Key frame resets all chains it is in.
      chains_calculator_.Reset(
          codec_specific_info->generic_frame_info->part_of_chain);
    }
    rtp_video_header->generic = GenericDescriptorFromFrameInfo(
        *codec_specific_info->generic_frame_info, frame_id);
    return;
  }

  switch (rtp_video_header->codec) {
    case VideoCodecType::kVideoCodecGeneric:
      GenericToGeneric(frame_id, is_keyframe, rtp_video_header);
      return;
    case VideoCodecType::kVideoCodecVP8:
      if (codec_specific_info) {
        Vp8ToGeneric(codec_specific_info->codecSpecific.VP8, frame_id,
                     is_keyframe, rtp_video_header);
      }
      return;
    case VideoCodecType::kVideoCodecVP9:
      if (codec_specific_info != nullptr) {
        Vp9ToGeneric(codec_specific_info->codecSpecific.VP9, frame_id,
                     *rtp_video_header);
      }
      return;
    case VideoCodecType::kVideoCodecAV1:
      // TODO(philipel): Implement AV1 to generic descriptor.
      return;
    case VideoCodecType::kVideoCodecH264:
      if (codec_specific_info) {
        H264ToGeneric(codec_specific_info->codecSpecific.H264, frame_id,
                      is_keyframe, rtp_video_header);
      }
      return;
#ifndef DISABLE_H265
    case VideoCodecType::kVideoCodecH265:
#endif
    case VideoCodecType::kVideoCodecMultiplex:
      return;
  }
  RTC_DCHECK_NOTREACHED() << "Unsupported codec.";
}

absl::optional<FrameDependencyStructure> RtpPayloadParams::GenericStructure(
    const CodecSpecificInfo* codec_specific_info) {
  if (codec_specific_info == nullptr) {
    return absl::nullopt;
  }
  // This helper shouldn't be used when template structure is specified
  // explicetly.
  RTC_DCHECK(!codec_specific_info->template_structure.has_value());
  switch (codec_specific_info->codecType) {
    case VideoCodecType::kVideoCodecGeneric:
      if (simulate_generic_structure_) {
        return MinimalisticStructure(/*num_spatial_layers=*/1,
                                     /*num_temporal_layer=*/1);
      }
      return absl::nullopt;
    case VideoCodecType::kVideoCodecVP8:
      return MinimalisticStructure(/*num_spatial_layers=*/1,
                                   /*num_temporal_layer=*/kMaxTemporalStreams);
    case VideoCodecType::kVideoCodecVP9: {
      absl::optional<FrameDependencyStructure> structure =
          MinimalisticStructure(
              /*num_spatial_layers=*/kMaxSimulatedSpatialLayers,
              /*num_temporal_layer=*/kMaxTemporalStreams);
      const CodecSpecificInfoVP9& vp9 = codec_specific_info->codecSpecific.VP9;
      if (vp9.ss_data_available && vp9.spatial_layer_resolution_present) {
        RenderResolution first_valid;
        RenderResolution last_valid;
        for (size_t i = 0; i < vp9.num_spatial_layers; ++i) {
          RenderResolution r(vp9.width[i], vp9.height[i]);
          if (r.Valid()) {
            if (!first_valid.Valid()) {
              first_valid = r;
            }
            last_valid = r;
          }
          structure->resolutions.push_back(r);
        }
        if (!last_valid.Valid()) {
          // No valid resolution found. Do not send resolutions.
          structure->resolutions.clear();
        } else {
          structure->resolutions.resize(kMaxSimulatedSpatialLayers, last_valid);
          // VP9 encoder wrapper may disable first few spatial layers by
          // setting invalid resolution (0,0). `structure->resolutions`
          // doesn't support invalid resolution, so reset them to something
          // valid.
          for (RenderResolution& r : structure->resolutions) {
            if (!r.Valid()) {
              r = first_valid;
            }
          }
        }
      }
      return structure;
    }
    case VideoCodecType::kVideoCodecAV1:
    case VideoCodecType::kVideoCodecH264:
    case VideoCodecType::kVideoCodecH265:
    case VideoCodecType::kVideoCodecMultiplex:
      return absl::nullopt;
  }
  RTC_DCHECK_NOTREACHED() << "Unsupported codec.";
}

void RtpPayloadParams::GenericToGeneric(int64_t shared_frame_id,
                                        bool is_keyframe,
                                        RTPVideoHeader* rtp_video_header) {
  RTPVideoHeader::GenericDescriptorInfo& generic =
      rtp_video_header->generic.emplace();

  generic.frame_id = shared_frame_id;
  generic.decode_target_indications.push_back(DecodeTargetIndication::kSwitch);

  if (is_keyframe) {
    generic.chain_diffs.push_back(0);
    last_shared_frame_id_[0].fill(-1);
  } else {
    int64_t frame_id = last_shared_frame_id_[0][0];
    RTC_DCHECK_NE(frame_id, -1);
    RTC_DCHECK_LT(frame_id, shared_frame_id);
    generic.chain_diffs.push_back(shared_frame_id - frame_id);
    generic.dependencies.push_back(frame_id);
  }

  last_shared_frame_id_[0][0] = shared_frame_id;
}

void RtpPayloadParams::H264ToGeneric(const CodecSpecificInfoH264& h264_info,
                                     int64_t shared_frame_id,
                                     bool is_keyframe,
                                     RTPVideoHeader* rtp_video_header) {
  const int temporal_index =
      h264_info.temporal_idx != kNoTemporalIdx ? h264_info.temporal_idx : 0;

  if (temporal_index >= RtpGenericFrameDescriptor::kMaxTemporalLayers) {
    RTC_LOG(LS_WARNING) << "Temporal and/or spatial index is too high to be "
                           "used with generic frame descriptor.";
    return;
  }

  RTPVideoHeader::GenericDescriptorInfo& generic =
      rtp_video_header->generic.emplace();

  generic.frame_id = shared_frame_id;
  generic.temporal_index = temporal_index;

  if (is_keyframe) {
    RTC_DCHECK_EQ(temporal_index, 0);
    last_shared_frame_id_[/*spatial index*/ 0].fill(-1);
    last_shared_frame_id_[/*spatial index*/ 0][temporal_index] =
        shared_frame_id;
    return;
  }

  if (h264_info.base_layer_sync) {
    int64_t tl0_frame_id = last_shared_frame_id_[/*spatial index*/ 0][0];

    for (int i = 1; i < RtpGenericFrameDescriptor::kMaxTemporalLayers; ++i) {
      if (last_shared_frame_id_[/*spatial index*/ 0][i] < tl0_frame_id) {
        last_shared_frame_id_[/*spatial index*/ 0][i] = -1;
      }
    }

    RTC_DCHECK_GE(tl0_frame_id, 0);
    RTC_DCHECK_LT(tl0_frame_id, shared_frame_id);
    generic.dependencies.push_back(tl0_frame_id);
  } else {
    for (int i = 0; i <= temporal_index; ++i) {
      int64_t frame_id = last_shared_frame_id_[/*spatial index*/ 0][i];

      if (frame_id != -1) {
        RTC_DCHECK_LT(frame_id, shared_frame_id);
        generic.dependencies.push_back(frame_id);
      }
    }
  }

  last_shared_frame_id_[/*spatial_index*/ 0][temporal_index] = shared_frame_id;
}

void RtpPayloadParams::Vp8ToGeneric(const CodecSpecificInfoVP8& vp8_info,
                                    int64_t shared_frame_id,
                                    bool is_keyframe,
                                    RTPVideoHeader* rtp_video_header) {
  const auto& vp8_header =
      absl::get<RTPVideoHeaderVP8>(rtp_video_header->video_type_header);
  const int spatial_index = 0;
  const int temporal_index =
      vp8_header.temporalIdx != kNoTemporalIdx ? vp8_header.temporalIdx : 0;

  if (temporal_index >= RtpGenericFrameDescriptor::kMaxTemporalLayers ||
      spatial_index >= RtpGenericFrameDescriptor::kMaxSpatialLayers) {
    RTC_LOG(LS_WARNING) << "Temporal and/or spatial index is too high to be "
                           "used with generic frame descriptor.";
    return;
  }

  RTPVideoHeader::GenericDescriptorInfo& generic =
      rtp_video_header->generic.emplace();

  generic.frame_id = shared_frame_id;
  generic.spatial_index = spatial_index;
  generic.temporal_index = temporal_index;

  // Generate decode target indications.
  RTC_DCHECK_LT(temporal_index, kMaxTemporalStreams);
  generic.decode_target_indications.resize(kMaxTemporalStreams);
  auto it = std::fill_n(generic.decode_target_indications.begin(),
                        temporal_index, DecodeTargetIndication::kNotPresent);
  std::fill(it, generic.decode_target_indications.end(),
            DecodeTargetIndication::kSwitch);

  // Frame dependencies.
  if (vp8_info.useExplicitDependencies) {
    SetDependenciesVp8New(vp8_info, shared_frame_id, is_keyframe,
                          vp8_header.layerSync, &generic);
  } else {
    SetDependenciesVp8Deprecated(vp8_info, shared_frame_id, is_keyframe,
                                 spatial_index, temporal_index,
                                 vp8_header.layerSync, &generic);
  }

  // Calculate chains.
  generic.chain_diffs = {
      (is_keyframe || chain_last_frame_id_[0] < 0)
          ? 0
          : static_cast<int>(shared_frame_id - chain_last_frame_id_[0])};
  if (temporal_index == 0) {
    chain_last_frame_id_[0] = shared_frame_id;
  }
}

void RtpPayloadParams::Vp9ToGeneric(const CodecSpecificInfoVP9& vp9_info,
                                    int64_t shared_frame_id,
                                    RTPVideoHeader& rtp_video_header) {
  const auto& vp9_header =
      absl::get<RTPVideoHeaderVP9>(rtp_video_header.video_type_header);
  const int num_spatial_layers = kMaxSimulatedSpatialLayers;
  const int num_active_spatial_layers = vp9_header.num_spatial_layers;
  const int num_temporal_layers = kMaxTemporalStreams;
  static_assert(num_spatial_layers <=
                RtpGenericFrameDescriptor::kMaxSpatialLayers);
  static_assert(num_temporal_layers <=
                RtpGenericFrameDescriptor::kMaxTemporalLayers);
  static_assert(num_spatial_layers <= DependencyDescriptor::kMaxSpatialIds);
  static_assert(num_temporal_layers <= DependencyDescriptor::kMaxTemporalIds);

  int spatial_index =
      vp9_header.spatial_idx != kNoSpatialIdx ? vp9_header.spatial_idx : 0;
  int temporal_index =
      vp9_header.temporal_idx != kNoTemporalIdx ? vp9_header.temporal_idx : 0;

  if (spatial_index >= num_spatial_layers ||
      temporal_index >= num_temporal_layers ||
      num_active_spatial_layers > num_spatial_layers) {
    // Prefer to generate no generic layering than an inconsistent one.
    return;
  }

  RTPVideoHeader::GenericDescriptorInfo& result =
      rtp_video_header.generic.emplace();

  result.frame_id = shared_frame_id;
  result.spatial_index = spatial_index;
  result.temporal_index = temporal_index;

  result.decode_target_indications.reserve(num_spatial_layers *
                                           num_temporal_layers);
  for (int sid = 0; sid < num_spatial_layers; ++sid) {
    for (int tid = 0; tid < num_temporal_layers; ++tid) {
      DecodeTargetIndication dti;
      if (sid < spatial_index || tid < temporal_index) {
        dti = DecodeTargetIndication::kNotPresent;
      } else if (spatial_index != sid &&
                 vp9_header.non_ref_for_inter_layer_pred) {
        dti = DecodeTargetIndication::kNotPresent;
      } else if (sid == spatial_index && tid == temporal_index) {
        // Assume that if frame is decodable, all of its own layer is decodable.
        dti = DecodeTargetIndication::kSwitch;
      } else if (sid == spatial_index && vp9_header.temporal_up_switch) {
        dti = DecodeTargetIndication::kSwitch;
      } else if (!vp9_header.inter_pic_predicted) {
        // Key frame or spatial upswitch
        dti = DecodeTargetIndication::kSwitch;
      } else {
        // Make no other assumptions. That should be safe, though suboptimal.
        // To provide more accurate dti, encoder wrapper should fill in
        // CodecSpecificInfo::generic_frame_info
        dti = DecodeTargetIndication::kRequired;
      }
      result.decode_target_indications.push_back(dti);
    }
  }

  // Calculate frame dependencies.
  static constexpr int kPictureDiffLimit = 128;
  if (last_vp9_frame_id_.empty()) {
    // Create the array only if it is ever used.
    last_vp9_frame_id_.resize(kPictureDiffLimit);
  }
  if (vp9_header.inter_layer_predicted && spatial_index > 0) {
    result.dependencies.push_back(
        last_vp9_frame_id_[vp9_header.picture_id % kPictureDiffLimit]
                          [spatial_index - 1]);
  }
  if (vp9_header.inter_pic_predicted) {
    for (size_t i = 0; i < vp9_header.num_ref_pics; ++i) {
      // picture_id is 15 bit number that wraps around. Though undeflow may
      // produce picture that exceeds 2^15, it is ok because in this
      // code block only last 7 bits of the picture_id are used.
      uint16_t depend_on = vp9_header.picture_id - vp9_header.pid_diff[i];
      result.dependencies.push_back(
          last_vp9_frame_id_[depend_on % kPictureDiffLimit][spatial_index]);
    }
  }
  last_vp9_frame_id_[vp9_header.picture_id % kPictureDiffLimit][spatial_index] =
      shared_frame_id;

  result.active_decode_targets =
      ((uint32_t{1} << num_temporal_layers * num_active_spatial_layers) - 1);

  // Calculate chains, asuming chain includes all frames with temporal_id = 0
  if (!vp9_header.inter_pic_predicted && !vp9_header.inter_layer_predicted) {
    // Assume frames without dependencies also reset chains.
    for (int sid = spatial_index; sid < num_spatial_layers; ++sid) {
      chain_last_frame_id_[sid] = -1;
    }
  }
  result.chain_diffs.resize(num_spatial_layers, 0);
  for (int sid = 0; sid < num_active_spatial_layers; ++sid) {
    if (chain_last_frame_id_[sid] == -1) {
      result.chain_diffs[sid] = 0;
      continue;
    }
    result.chain_diffs[sid] = shared_frame_id - chain_last_frame_id_[sid];
  }

  if (temporal_index == 0) {
    chain_last_frame_id_[spatial_index] = shared_frame_id;
    if (!vp9_header.non_ref_for_inter_layer_pred) {
      for (int sid = spatial_index + 1; sid < num_spatial_layers; ++sid) {
        chain_last_frame_id_[sid] = shared_frame_id;
      }
    }
  }
}

void RtpPayloadParams::SetDependenciesVp8Deprecated(
    const CodecSpecificInfoVP8& vp8_info,
    int64_t shared_frame_id,
    bool is_keyframe,
    int spatial_index,
    int temporal_index,
    bool layer_sync,
    RTPVideoHeader::GenericDescriptorInfo* generic) {
  RTC_DCHECK(!vp8_info.useExplicitDependencies);
  RTC_DCHECK(!new_version_used_.has_value() || !new_version_used_.value());
  new_version_used_ = false;

  if (is_keyframe) {
    RTC_DCHECK_EQ(temporal_index, 0);
    last_shared_frame_id_[spatial_index].fill(-1);
    last_shared_frame_id_[spatial_index][temporal_index] = shared_frame_id;
    return;
  }

  if (layer_sync) {
    int64_t tl0_frame_id = last_shared_frame_id_[spatial_index][0];

    for (int i = 1; i < RtpGenericFrameDescriptor::kMaxTemporalLayers; ++i) {
      if (last_shared_frame_id_[spatial_index][i] < tl0_frame_id) {
        last_shared_frame_id_[spatial_index][i] = -1;
      }
    }

    RTC_DCHECK_GE(tl0_frame_id, 0);
    RTC_DCHECK_LT(tl0_frame_id, shared_frame_id);
    generic->dependencies.push_back(tl0_frame_id);
  } else {
    for (int i = 0; i <= temporal_index; ++i) {
      int64_t frame_id = last_shared_frame_id_[spatial_index][i];

      if (frame_id != -1) {
        RTC_DCHECK_LT(frame_id, shared_frame_id);
        generic->dependencies.push_back(frame_id);
      }
    }
  }

  last_shared_frame_id_[spatial_index][temporal_index] = shared_frame_id;
}

void RtpPayloadParams::SetDependenciesVp8New(
    const CodecSpecificInfoVP8& vp8_info,
    int64_t shared_frame_id,
    bool is_keyframe,
    bool layer_sync,
    RTPVideoHeader::GenericDescriptorInfo* generic) {
  RTC_DCHECK(vp8_info.useExplicitDependencies);
  RTC_DCHECK(!new_version_used_.has_value() || new_version_used_.value());
  new_version_used_ = true;

  if (is_keyframe) {
    RTC_DCHECK_EQ(vp8_info.referencedBuffersCount, 0u);
    buffer_id_to_frame_id_.fill(shared_frame_id);
    return;
  }

  constexpr size_t kBuffersCountVp8 = CodecSpecificInfoVP8::kBuffersCount;

  RTC_DCHECK_GT(vp8_info.referencedBuffersCount, 0u);
  RTC_DCHECK_LE(vp8_info.referencedBuffersCount,
                arraysize(vp8_info.referencedBuffers));

  for (size_t i = 0; i < vp8_info.referencedBuffersCount; ++i) {
    const size_t referenced_buffer = vp8_info.referencedBuffers[i];
    RTC_DCHECK_LT(referenced_buffer, kBuffersCountVp8);
    RTC_DCHECK_LT(referenced_buffer, buffer_id_to_frame_id_.size());

    const int64_t dependency_frame_id =
        buffer_id_to_frame_id_[referenced_buffer];
    RTC_DCHECK_GE(dependency_frame_id, 0);
    RTC_DCHECK_LT(dependency_frame_id, shared_frame_id);

    const bool is_new_dependency =
        std::find(generic->dependencies.begin(), generic->dependencies.end(),
                  dependency_frame_id) == generic->dependencies.end();
    if (is_new_dependency) {
      generic->dependencies.push_back(dependency_frame_id);
    }
  }

  RTC_DCHECK_LE(vp8_info.updatedBuffersCount, kBuffersCountVp8);
  for (size_t i = 0; i < vp8_info.updatedBuffersCount; ++i) {
    const size_t updated_id = vp8_info.updatedBuffers[i];
    buffer_id_to_frame_id_[updated_id] = shared_frame_id;
  }

  RTC_DCHECK_LE(buffer_id_to_frame_id_.size(), kBuffersCountVp8);
}

}  // namespace webrtc

/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_video_header.h"

namespace webrtc {

RTPVideoHeader::GenericDescriptorInfo::GenericDescriptorInfo() = default;
RTPVideoHeader::GenericDescriptorInfo::GenericDescriptorInfo(
    const GenericDescriptorInfo& other) = default;
RTPVideoHeader::GenericDescriptorInfo::~GenericDescriptorInfo() = default;

// static
RTPVideoHeader RTPVideoHeader::FromMetadata(
    const VideoFrameMetadata& metadata) {
  RTPVideoHeader rtp_video_header;
  rtp_video_header.SetFromMetadata(metadata);
  return rtp_video_header;
}

RTPVideoHeader::RTPVideoHeader() : video_timing() {}
RTPVideoHeader::RTPVideoHeader(const RTPVideoHeader& other) = default;
RTPVideoHeader::~RTPVideoHeader() = default;

VideoFrameMetadata RTPVideoHeader::GetAsMetadata() const {
  VideoFrameMetadata metadata;
  metadata.SetFrameType(frame_type);
  metadata.SetWidth(width);
  metadata.SetHeight(height);
  metadata.SetRotation(rotation);
  metadata.SetContentType(content_type);
  if (generic) {
    metadata.SetFrameId(generic->frame_id);
    metadata.SetSpatialIndex(generic->spatial_index);
    metadata.SetTemporalIndex(generic->temporal_index);
    metadata.SetFrameDependencies(generic->dependencies);
    metadata.SetDecodeTargetIndications(generic->decode_target_indications);
  }
  metadata.SetIsLastFrameInPicture(is_last_frame_in_picture);
  metadata.SetSimulcastIdx(simulcastIdx);
  metadata.SetCodec(codec);
  switch (codec) {
    case VideoCodecType::kVideoCodecVP8:
      metadata.SetRTPVideoHeaderCodecSpecifics(
          absl::get<RTPVideoHeaderVP8>(video_type_header));
      break;
    case VideoCodecType::kVideoCodecVP9:
      metadata.SetRTPVideoHeaderCodecSpecifics(
          absl::get<RTPVideoHeaderVP9>(video_type_header));
      break;
    case VideoCodecType::kVideoCodecH264:
      metadata.SetRTPVideoHeaderCodecSpecifics(
          absl::get<RTPVideoHeaderH264>(video_type_header));
      break;
    case VideoCodecType::kVideoCodecH265:
      // TODO(bugs.webrtc.org/13485)
      break;
    default:
      // Codec-specifics are not supported for this codec.
      break;
  }
  return metadata;
}

void RTPVideoHeader::SetFromMetadata(const VideoFrameMetadata& metadata) {
  frame_type = metadata.GetFrameType();
  width = metadata.GetWidth();
  height = metadata.GetHeight();
  rotation = metadata.GetRotation();
  content_type = metadata.GetContentType();
  if (!metadata.GetFrameId().has_value()) {
    generic = absl::nullopt;
  } else {
    generic.emplace();
    generic->frame_id = metadata.GetFrameId().value();
    generic->spatial_index = metadata.GetSpatialIndex();
    generic->temporal_index = metadata.GetTemporalIndex();
    generic->dependencies.assign(metadata.GetFrameDependencies().begin(),
                                 metadata.GetFrameDependencies().end());
    generic->decode_target_indications.assign(
        metadata.GetDecodeTargetIndications().begin(),
        metadata.GetDecodeTargetIndications().end());
  }
  is_last_frame_in_picture = metadata.GetIsLastFrameInPicture();
  simulcastIdx = metadata.GetSimulcastIdx();
  codec = metadata.GetCodec();
  switch (codec) {
    case VideoCodecType::kVideoCodecVP8:
      video_type_header = absl::get<RTPVideoHeaderVP8>(
          metadata.GetRTPVideoHeaderCodecSpecifics());
      break;
    case VideoCodecType::kVideoCodecVP9:
      video_type_header = absl::get<RTPVideoHeaderVP9>(
          metadata.GetRTPVideoHeaderCodecSpecifics());
      break;
    case VideoCodecType::kVideoCodecH264:
      video_type_header = absl::get<RTPVideoHeaderH264>(
          metadata.GetRTPVideoHeaderCodecSpecifics());
      break;
    default:
      // Codec-specifics are not supported for this codec.
      break;
  }
}

}  // namespace webrtc

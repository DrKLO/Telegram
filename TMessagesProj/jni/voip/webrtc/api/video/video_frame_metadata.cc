/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video/video_frame_metadata.h"

#include <utility>

namespace webrtc {

VideoFrameMetadata::VideoFrameMetadata() = default;

VideoFrameType VideoFrameMetadata::GetFrameType() const {
  return frame_type_;
}

void VideoFrameMetadata::SetFrameType(VideoFrameType frame_type) {
  frame_type_ = frame_type;
}

uint16_t VideoFrameMetadata::GetWidth() const {
  return width_;
}

void VideoFrameMetadata::SetWidth(uint16_t width) {
  width_ = width;
}

uint16_t VideoFrameMetadata::GetHeight() const {
  return height_;
}

void VideoFrameMetadata::SetHeight(uint16_t height) {
  height_ = height;
}

VideoRotation VideoFrameMetadata::GetRotation() const {
  return rotation_;
}

void VideoFrameMetadata::SetRotation(VideoRotation rotation) {
  rotation_ = rotation;
}

VideoContentType VideoFrameMetadata::GetContentType() const {
  return content_type_;
}

void VideoFrameMetadata::SetContentType(VideoContentType content_type) {
  content_type_ = content_type;
}

absl::optional<int64_t> VideoFrameMetadata::GetFrameId() const {
  return frame_id_;
}

void VideoFrameMetadata::SetFrameId(absl::optional<int64_t> frame_id) {
  frame_id_ = frame_id;
}

int VideoFrameMetadata::GetSpatialIndex() const {
  return spatial_index_;
}

void VideoFrameMetadata::SetSpatialIndex(int spatial_index) {
  spatial_index_ = spatial_index;
}

int VideoFrameMetadata::GetTemporalIndex() const {
  return temporal_index_;
}

void VideoFrameMetadata::SetTemporalIndex(int temporal_index) {
  temporal_index_ = temporal_index;
}

rtc::ArrayView<const int64_t> VideoFrameMetadata::GetFrameDependencies() const {
  return frame_dependencies_;
}

void VideoFrameMetadata::SetFrameDependencies(
    rtc::ArrayView<const int64_t> frame_dependencies) {
  frame_dependencies_.assign(frame_dependencies.begin(),
                             frame_dependencies.end());
}

rtc::ArrayView<const DecodeTargetIndication>
VideoFrameMetadata::GetDecodeTargetIndications() const {
  return decode_target_indications_;
}

void VideoFrameMetadata::SetDecodeTargetIndications(
    rtc::ArrayView<const DecodeTargetIndication> decode_target_indications) {
  decode_target_indications_.assign(decode_target_indications.begin(),
                                    decode_target_indications.end());
}

bool VideoFrameMetadata::GetIsLastFrameInPicture() const {
  return is_last_frame_in_picture_;
}

void VideoFrameMetadata::SetIsLastFrameInPicture(
    bool is_last_frame_in_picture) {
  is_last_frame_in_picture_ = is_last_frame_in_picture;
}

uint8_t VideoFrameMetadata::GetSimulcastIdx() const {
  return simulcast_idx_;
}

void VideoFrameMetadata::SetSimulcastIdx(uint8_t simulcast_idx) {
  simulcast_idx_ = simulcast_idx;
}

VideoCodecType VideoFrameMetadata::GetCodec() const {
  return codec_;
}

void VideoFrameMetadata::SetCodec(VideoCodecType codec) {
  codec_ = codec;
}

const RTPVideoHeaderCodecSpecifics&
VideoFrameMetadata::GetRTPVideoHeaderCodecSpecifics() const {
  return codec_specifics_;
}

void VideoFrameMetadata::SetRTPVideoHeaderCodecSpecifics(
    RTPVideoHeaderCodecSpecifics codec_specifics) {
  codec_specifics_ = std::move(codec_specifics);
}

uint32_t VideoFrameMetadata::GetSsrc() const {
  return ssrc_;
}

void VideoFrameMetadata::SetSsrc(uint32_t ssrc) {
  ssrc_ = ssrc;
}

std::vector<uint32_t> VideoFrameMetadata::GetCsrcs() const {
  return csrcs_;
}

void VideoFrameMetadata::SetCsrcs(std::vector<uint32_t> csrcs) {
  csrcs_ = std::move(csrcs);
}

bool operator==(const VideoFrameMetadata& lhs, const VideoFrameMetadata& rhs) {
  return lhs.frame_type_ == rhs.frame_type_ && lhs.width_ == rhs.width_ &&
         lhs.height_ == rhs.height_ && lhs.rotation_ == rhs.rotation_ &&
         lhs.content_type_ == rhs.content_type_ &&
         lhs.frame_id_ == rhs.frame_id_ &&
         lhs.spatial_index_ == rhs.spatial_index_ &&
         lhs.temporal_index_ == rhs.temporal_index_ &&
         lhs.frame_dependencies_ == rhs.frame_dependencies_ &&
         lhs.decode_target_indications_ == rhs.decode_target_indications_ &&
         lhs.is_last_frame_in_picture_ == rhs.is_last_frame_in_picture_ &&
         lhs.simulcast_idx_ == rhs.simulcast_idx_ && lhs.codec_ == rhs.codec_ &&
         lhs.codec_specifics_ == rhs.codec_specifics_ &&
         lhs.ssrc_ == rhs.ssrc_ && lhs.csrcs_ == rhs.csrcs_;
}

bool operator!=(const VideoFrameMetadata& lhs, const VideoFrameMetadata& rhs) {
  return !(lhs == rhs);
}

}  // namespace webrtc

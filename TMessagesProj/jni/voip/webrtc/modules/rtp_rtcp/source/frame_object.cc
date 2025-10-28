/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/frame_object.h"

#include <string.h>

#include <utility>

#include "api/video/encoded_image.h"
#include "api/video/video_timing.h"
#include "rtc_base/checks.h"

namespace webrtc {
RtpFrameObject::RtpFrameObject(
    uint16_t first_seq_num,
    uint16_t last_seq_num,
    bool markerBit,
    int times_nacked,
    int64_t first_packet_received_time,
    int64_t last_packet_received_time,
    uint32_t rtp_timestamp,
    int64_t ntp_time_ms,
    const VideoSendTiming& timing,
    uint8_t payload_type,
    VideoCodecType codec,
    VideoRotation rotation,
    VideoContentType content_type,
    const RTPVideoHeader& video_header,
    const absl::optional<webrtc::ColorSpace>& color_space,
    RtpPacketInfos packet_infos,
    rtc::scoped_refptr<EncodedImageBuffer> image_buffer)
    : image_buffer_(image_buffer),
      first_seq_num_(first_seq_num),
      last_seq_num_(last_seq_num),
      last_packet_received_time_(last_packet_received_time),
      times_nacked_(times_nacked) {
  rtp_video_header_ = video_header;

  // EncodedFrame members
  codec_type_ = codec;

  // TODO(philipel): Remove when encoded image is replaced by EncodedFrame.
  // VCMEncodedFrame members
  CopyCodecSpecific(&rtp_video_header_);
  _payloadType = payload_type;
  SetRtpTimestamp(rtp_timestamp);
  ntp_time_ms_ = ntp_time_ms;
  _frameType = rtp_video_header_.frame_type;

  // Setting frame's playout delays to the same values
  // as of the first packet's.
  SetPlayoutDelay(rtp_video_header_.playout_delay);

  SetEncodedData(image_buffer_);
  _encodedWidth = rtp_video_header_.width;
  _encodedHeight = rtp_video_header_.height;

  if (packet_infos.begin() != packet_infos.end()) {
    csrcs_ = packet_infos.begin()->csrcs();
  }

  // EncodedFrame members
  SetPacketInfos(std::move(packet_infos));

  rotation_ = rotation;
  SetColorSpace(color_space);
  SetVideoFrameTrackingId(rtp_video_header_.video_frame_tracking_id);
  content_type_ = content_type;
  if (timing.flags != VideoSendTiming::kInvalid) {
    // ntp_time_ms_ may be -1 if not estimated yet. This is not a problem,
    // as this will be dealt with at the time of reporting.
    timing_.encode_start_ms = ntp_time_ms_ + timing.encode_start_delta_ms;
    timing_.encode_finish_ms = ntp_time_ms_ + timing.encode_finish_delta_ms;
    timing_.packetization_finish_ms =
        ntp_time_ms_ + timing.packetization_finish_delta_ms;
    timing_.pacer_exit_ms = ntp_time_ms_ + timing.pacer_exit_delta_ms;
    timing_.network_timestamp_ms =
        ntp_time_ms_ + timing.network_timestamp_delta_ms;
    timing_.network2_timestamp_ms =
        ntp_time_ms_ + timing.network2_timestamp_delta_ms;
  }
  timing_.receive_start_ms = first_packet_received_time;
  timing_.receive_finish_ms = last_packet_received_time;
  timing_.flags = timing.flags;
  is_last_spatial_layer = markerBit;
}

RtpFrameObject::~RtpFrameObject() {}

uint16_t RtpFrameObject::first_seq_num() const {
  return first_seq_num_;
}

uint16_t RtpFrameObject::last_seq_num() const {
  return last_seq_num_;
}

int RtpFrameObject::times_nacked() const {
  return times_nacked_;
}

VideoFrameType RtpFrameObject::frame_type() const {
  return rtp_video_header_.frame_type;
}

VideoCodecType RtpFrameObject::codec_type() const {
  return codec_type_;
}

int64_t RtpFrameObject::ReceivedTime() const {
  return last_packet_received_time_;
}

int64_t RtpFrameObject::RenderTime() const {
  return _renderTimeMs;
}

bool RtpFrameObject::delayed_by_retransmission() const {
  return times_nacked() > 0;
}

const RTPVideoHeader& RtpFrameObject::GetRtpVideoHeader() const {
  return rtp_video_header_;
}

void RtpFrameObject::SetHeaderFromMetadata(const VideoFrameMetadata& metadata) {
  rtp_video_header_.SetFromMetadata(metadata);
}
}  // namespace webrtc

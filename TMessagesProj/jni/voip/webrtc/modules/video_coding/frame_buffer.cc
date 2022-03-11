/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/frame_buffer.h"

#include <string.h>

#include "api/video/encoded_image.h"
#include "api/video/video_timing.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/packet.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/trace_event.h"

namespace webrtc {

VCMFrameBuffer::VCMFrameBuffer()
    : _state(kStateEmpty), _nackCount(0), _latestPacketTimeMs(-1) {}

VCMFrameBuffer::~VCMFrameBuffer() {}

webrtc::VideoFrameType VCMFrameBuffer::FrameType() const {
  return _sessionInfo.FrameType();
}

int32_t VCMFrameBuffer::GetLowSeqNum() const {
  return _sessionInfo.LowSequenceNumber();
}

int32_t VCMFrameBuffer::GetHighSeqNum() const {
  return _sessionInfo.HighSequenceNumber();
}

int VCMFrameBuffer::PictureId() const {
  return _sessionInfo.PictureId();
}

int VCMFrameBuffer::TemporalId() const {
  return _sessionInfo.TemporalId();
}

bool VCMFrameBuffer::LayerSync() const {
  return _sessionInfo.LayerSync();
}

int VCMFrameBuffer::Tl0PicId() const {
  return _sessionInfo.Tl0PicId();
}

std::vector<NaluInfo> VCMFrameBuffer::GetNaluInfos() const {
  return _sessionInfo.GetNaluInfos();
}

void VCMFrameBuffer::SetGofInfo(const GofInfoVP9& gof_info, size_t idx) {
  TRACE_EVENT0("webrtc", "VCMFrameBuffer::SetGofInfo");
  _sessionInfo.SetGofInfo(gof_info, idx);
  // TODO(asapersson): Consider adding hdr->VP9.ref_picture_id for testing.
  _codecSpecificInfo.codecSpecific.VP9.temporal_idx =
      gof_info.temporal_idx[idx];
  _codecSpecificInfo.codecSpecific.VP9.temporal_up_switch =
      gof_info.temporal_up_switch[idx];
}

// Insert packet
VCMFrameBufferEnum VCMFrameBuffer::InsertPacket(const VCMPacket& packet,
                                                int64_t timeInMs,
                                                const FrameData& frame_data) {
  TRACE_EVENT0("webrtc", "VCMFrameBuffer::InsertPacket");
  RTC_DCHECK(!(NULL == packet.dataPtr && packet.sizeBytes > 0));
  if (packet.dataPtr != NULL) {
    _payloadType = packet.payloadType;
  }

  if (kStateEmpty == _state) {
    // First packet (empty and/or media) inserted into this frame.
    // store some info and set some initial values.
    SetTimestamp(packet.timestamp);
    // We only take the ntp timestamp of the first packet of a frame.
    ntp_time_ms_ = packet.ntp_time_ms_;
    _codec = packet.codec();
    if (packet.video_header.frame_type != VideoFrameType::kEmptyFrame) {
      // first media packet
      SetState(kStateIncomplete);
    }
  }

  size_t oldSize = encoded_image_buffer_ ? encoded_image_buffer_->size() : 0;
  uint32_t requiredSizeBytes =
      size() + packet.sizeBytes +
      (packet.insertStartCode ? kH264StartCodeLengthBytes : 0);
  if (requiredSizeBytes > oldSize) {
    const uint8_t* prevBuffer = data();
    const uint32_t increments =
        requiredSizeBytes / kBufferIncStepSizeBytes +
        (requiredSizeBytes % kBufferIncStepSizeBytes > 0);
    const uint32_t newSize = oldSize + increments * kBufferIncStepSizeBytes;
    if (newSize > kMaxJBFrameSizeBytes) {
      RTC_LOG(LS_ERROR) << "Failed to insert packet due to frame being too "
                           "big.";
      return kSizeError;
    }
    if (data() == nullptr) {
      encoded_image_buffer_ = EncodedImageBuffer::Create(newSize);
      SetEncodedData(encoded_image_buffer_);
      set_size(0);
    } else {
      RTC_CHECK(encoded_image_buffer_ != nullptr);
      RTC_DCHECK_EQ(encoded_image_buffer_->data(), data());
      encoded_image_buffer_->Realloc(newSize);
    }
    _sessionInfo.UpdateDataPointers(prevBuffer, data());
  }

  if (packet.width() > 0 && packet.height() > 0) {
    _encodedWidth = packet.width();
    _encodedHeight = packet.height();
  }

  // Don't copy payload specific data for empty packets (e.g padding packets).
  if (packet.sizeBytes > 0)
    CopyCodecSpecific(&packet.video_header);

  int retVal = _sessionInfo.InsertPacket(
      packet, encoded_image_buffer_ ? encoded_image_buffer_->data() : nullptr,
      frame_data);
  if (retVal == -1) {
    return kSizeError;
  } else if (retVal == -2) {
    return kDuplicatePacket;
  } else if (retVal == -3) {
    return kOutOfBoundsPacket;
  }
  // update size
  set_size(size() + static_cast<uint32_t>(retVal));

  _latestPacketTimeMs = timeInMs;

  // http://www.etsi.org/deliver/etsi_ts/126100_126199/126114/12.07.00_60/
  // ts_126114v120700p.pdf Section 7.4.5.
  // The MTSI client shall add the payload bytes as defined in this clause
  // onto the last RTP packet in each group of packets which make up a key
  // frame (I-frame or IDR frame in H.264 (AVC), or an IRAP picture in H.265
  // (HEVC)).
  if (packet.markerBit) {
    rotation_ = packet.video_header.rotation;
    content_type_ = packet.video_header.content_type;
    if (packet.video_header.video_timing.flags != VideoSendTiming::kInvalid) {
      timing_.encode_start_ms =
          ntp_time_ms_ + packet.video_header.video_timing.encode_start_delta_ms;
      timing_.encode_finish_ms =
          ntp_time_ms_ +
          packet.video_header.video_timing.encode_finish_delta_ms;
      timing_.packetization_finish_ms =
          ntp_time_ms_ +
          packet.video_header.video_timing.packetization_finish_delta_ms;
      timing_.pacer_exit_ms =
          ntp_time_ms_ + packet.video_header.video_timing.pacer_exit_delta_ms;
      timing_.network_timestamp_ms =
          ntp_time_ms_ +
          packet.video_header.video_timing.network_timestamp_delta_ms;
      timing_.network2_timestamp_ms =
          ntp_time_ms_ +
          packet.video_header.video_timing.network2_timestamp_delta_ms;
    }
    timing_.flags = packet.video_header.video_timing.flags;
  }

  if (packet.is_first_packet_in_frame()) {
    playout_delay_ = packet.video_header.playout_delay;
  }

  if (_sessionInfo.complete()) {
    SetState(kStateComplete);
    return kCompleteSession;
  }
  return kIncomplete;
}

int64_t VCMFrameBuffer::LatestPacketTimeMs() const {
  TRACE_EVENT0("webrtc", "VCMFrameBuffer::LatestPacketTimeMs");
  return _latestPacketTimeMs;
}

void VCMFrameBuffer::IncrementNackCount() {
  TRACE_EVENT0("webrtc", "VCMFrameBuffer::IncrementNackCount");
  _nackCount++;
}

int16_t VCMFrameBuffer::GetNackCount() const {
  TRACE_EVENT0("webrtc", "VCMFrameBuffer::GetNackCount");
  return _nackCount;
}

bool VCMFrameBuffer::HaveFirstPacket() const {
  TRACE_EVENT0("webrtc", "VCMFrameBuffer::HaveFirstPacket");
  return _sessionInfo.HaveFirstPacket();
}

int VCMFrameBuffer::NumPackets() const {
  TRACE_EVENT0("webrtc", "VCMFrameBuffer::NumPackets");
  return _sessionInfo.NumPackets();
}

void VCMFrameBuffer::Reset() {
  TRACE_EVENT0("webrtc", "VCMFrameBuffer::Reset");
  set_size(0);
  _sessionInfo.Reset();
  _payloadType = 0;
  _nackCount = 0;
  _latestPacketTimeMs = -1;
  _state = kStateEmpty;
  VCMEncodedFrame::Reset();
}

// Set state of frame
void VCMFrameBuffer::SetState(VCMFrameBufferStateEnum state) {
  TRACE_EVENT0("webrtc", "VCMFrameBuffer::SetState");
  if (_state == state) {
    return;
  }
  switch (state) {
    case kStateIncomplete:
      // we can go to this state from state kStateEmpty
      RTC_DCHECK_EQ(_state, kStateEmpty);

      // Do nothing, we received a packet
      break;

    case kStateComplete:
      RTC_DCHECK(_state == kStateEmpty || _state == kStateIncomplete);

      break;

    case kStateEmpty:
      // Should only be set to empty through Reset().
      RTC_DCHECK_NOTREACHED();
      break;
  }
  _state = state;
}

// Get current state of frame
VCMFrameBufferStateEnum VCMFrameBuffer::GetState() const {
  return _state;
}

void VCMFrameBuffer::PrepareForDecode(bool continuous) {
  TRACE_EVENT0("webrtc", "VCMFrameBuffer::PrepareForDecode");
  size_t bytes_removed = _sessionInfo.MakeDecodable();
  set_size(size() - bytes_removed);
  // Transfer frame information to EncodedFrame and create any codec
  // specific information.
  _frameType = _sessionInfo.FrameType();
  _missingFrame = !continuous;
}

}  // namespace webrtc

/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/h264_packet_buffer.h"

#include <algorithm>
#include <cstdint>
#include <utility>
#include <vector>

#include "api/array_view.h"
#include "api/rtp_packet_info.h"
#include "api/video/video_frame_type.h"
#include "common_video/h264/h264_common.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "modules/rtp_rtcp/source/rtp_video_header.h"
#include "modules/video_coding/codecs/h264/include/h264_globals.h"
#include "rtc_base/checks.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/sequence_number_util.h"

namespace webrtc {
namespace {
int64_t EuclideanMod(int64_t n, int64_t div) {
  RTC_DCHECK_GT(div, 0);
  return (n %= div) < 0 ? n + div : n;
}

rtc::ArrayView<const NaluInfo> GetNaluInfos(
    const RTPVideoHeaderH264& h264_header) {
  if (h264_header.nalus_length > kMaxNalusPerPacket) {
    return {};
  }

  return rtc::MakeArrayView(h264_header.nalus, h264_header.nalus_length);
}

bool IsFirstPacketOfFragment(const RTPVideoHeaderH264& h264_header) {
  return h264_header.nalus_length > 0;
}

bool BeginningOfIdr(const H264PacketBuffer::Packet& packet) {
  const auto& h264_header =
      absl::get<RTPVideoHeaderH264>(packet.video_header.video_type_header);
  const bool contains_idr_nalu =
      absl::c_any_of(GetNaluInfos(h264_header), [](const auto& nalu_info) {
        return nalu_info.type == H264::NaluType::kIdr;
      });
  switch (h264_header.packetization_type) {
    case kH264StapA:
    case kH264SingleNalu: {
      return contains_idr_nalu;
    }
    case kH264FuA: {
      return contains_idr_nalu && IsFirstPacketOfFragment(h264_header);
    }
  }
}

bool HasSps(const H264PacketBuffer::Packet& packet) {
  auto& h264_header =
      absl::get<RTPVideoHeaderH264>(packet.video_header.video_type_header);
  return absl::c_any_of(GetNaluInfos(h264_header), [](const auto& nalu_info) {
    return nalu_info.type == H264::NaluType::kSps;
  });
}

// TODO(bugs.webrtc.org/13157): Update the H264 depacketizer so we don't have to
//                              fiddle with the payload at this point.
rtc::CopyOnWriteBuffer FixVideoPayload(rtc::ArrayView<const uint8_t> payload,
                                       const RTPVideoHeader& video_header) {
  constexpr uint8_t kStartCode[] = {0, 0, 0, 1};

  const auto& h264_header =
      absl::get<RTPVideoHeaderH264>(video_header.video_type_header);

  rtc::CopyOnWriteBuffer result;
  switch (h264_header.packetization_type) {
    case kH264StapA: {
      const uint8_t* payload_end = payload.data() + payload.size();
      const uint8_t* nalu_ptr = payload.data() + 1;
      while (nalu_ptr < payload_end - 1) {
        // The first two bytes describe the length of the segment, where a
        // segment is the nalu type plus nalu payload.
        uint16_t segment_length = nalu_ptr[0] << 8 | nalu_ptr[1];
        nalu_ptr += 2;

        if (nalu_ptr + segment_length <= payload_end) {
          result.AppendData(kStartCode);
          result.AppendData(nalu_ptr, segment_length);
        }
        nalu_ptr += segment_length;
      }
      return result;
    }

    case kH264FuA: {
      if (IsFirstPacketOfFragment(h264_header)) {
        result.AppendData(kStartCode);
      }
      result.AppendData(payload);
      return result;
    }

    case kH264SingleNalu: {
      result.AppendData(kStartCode);
      result.AppendData(payload);
      return result;
    }
  }

  RTC_DCHECK_NOTREACHED();
  return result;
}

}  // namespace

H264PacketBuffer::H264PacketBuffer(bool idr_only_keyframes_allowed)
    : idr_only_keyframes_allowed_(idr_only_keyframes_allowed) {}

H264PacketBuffer::InsertResult H264PacketBuffer::InsertPacket(
    std::unique_ptr<Packet> packet) {
  RTC_DCHECK(packet->video_header.codec == kVideoCodecH264);

  InsertResult result;
  if (!absl::holds_alternative<RTPVideoHeaderH264>(
          packet->video_header.video_type_header)) {
    return result;
  }

  int64_t unwrapped_seq_num = seq_num_unwrapper_.Unwrap(packet->seq_num);
  auto& packet_slot = GetPacket(unwrapped_seq_num);
  if (packet_slot != nullptr &&
      AheadOrAt(packet_slot->timestamp, packet->timestamp)) {
    // The incoming `packet` is old or a duplicate.
    return result;
  } else {
    packet_slot = std::move(packet);
  }

  result.packets = FindFrames(unwrapped_seq_num);
  return result;
}

std::unique_ptr<H264PacketBuffer::Packet>& H264PacketBuffer::GetPacket(
    int64_t unwrapped_seq_num) {
  return buffer_[EuclideanMod(unwrapped_seq_num, kBufferSize)];
}

bool H264PacketBuffer::BeginningOfStream(
    const H264PacketBuffer::Packet& packet) const {
  return HasSps(packet) ||
         (idr_only_keyframes_allowed_ && BeginningOfIdr(packet));
}

std::vector<std::unique_ptr<H264PacketBuffer::Packet>>
H264PacketBuffer::FindFrames(int64_t unwrapped_seq_num) {
  std::vector<std::unique_ptr<Packet>> found_frames;

  Packet* packet = GetPacket(unwrapped_seq_num).get();
  RTC_CHECK(packet != nullptr);

  // Check if the packet is continuous or the beginning of a new coded video
  // sequence.
  if (unwrapped_seq_num - 1 != last_continuous_unwrapped_seq_num_) {
    if (unwrapped_seq_num <= last_continuous_unwrapped_seq_num_ ||
        !BeginningOfStream(*packet)) {
      return found_frames;
    }

    last_continuous_unwrapped_seq_num_ = unwrapped_seq_num;
  }

  for (int64_t seq_num = unwrapped_seq_num;
       seq_num < unwrapped_seq_num + kBufferSize;) {
    RTC_DCHECK_GE(seq_num, *last_continuous_unwrapped_seq_num_);

    // Packets that were never assembled into a completed frame will stay in
    // the 'buffer_'. Check that the `packet` sequence number match the expected
    // unwrapped sequence number.
    if (static_cast<uint16_t>(seq_num) != packet->seq_num) {
      return found_frames;
    }

    last_continuous_unwrapped_seq_num_ = seq_num;
    // Last packet of the frame, try to assemble the frame.
    if (packet->marker_bit) {
      uint32_t rtp_timestamp = packet->timestamp;

      // Iterate backwards to find where the frame starts.
      for (int64_t seq_num_start = seq_num;
           seq_num_start > seq_num - kBufferSize; --seq_num_start) {
        auto& prev_packet = GetPacket(seq_num_start - 1);

        if (prev_packet == nullptr || prev_packet->timestamp != rtp_timestamp) {
          if (MaybeAssembleFrame(seq_num_start, seq_num, found_frames)) {
            // Frame was assembled, continue to look for more frames.
            break;
          } else {
            // Frame was not assembled, no subsequent frame will be continuous.
            return found_frames;
          }
        }
      }
    }

    seq_num++;
    packet = GetPacket(seq_num).get();
    if (packet == nullptr) {
      return found_frames;
    }
  }

  return found_frames;
}

bool H264PacketBuffer::MaybeAssembleFrame(
    int64_t start_seq_num_unwrapped,
    int64_t end_sequence_number_unwrapped,
    std::vector<std::unique_ptr<Packet>>& frames) {
  bool has_sps = false;
  bool has_pps = false;
  bool has_idr = false;

  int width = -1;
  int height = -1;

  for (int64_t seq_num = start_seq_num_unwrapped;
       seq_num <= end_sequence_number_unwrapped; ++seq_num) {
    const auto& packet = GetPacket(seq_num);
    const auto& h264_header =
        absl::get<RTPVideoHeaderH264>(packet->video_header.video_type_header);
    for (const auto& nalu : GetNaluInfos(h264_header)) {
      has_idr |= nalu.type == H264::NaluType::kIdr;
      has_sps |= nalu.type == H264::NaluType::kSps;
      has_pps |= nalu.type == H264::NaluType::kPps;
    }

    width = std::max<int>(packet->video_header.width, width);
    height = std::max<int>(packet->video_header.height, height);
  }

  if (has_idr) {
    if (!idr_only_keyframes_allowed_ && (!has_sps || !has_pps)) {
      return false;
    }
  }

  for (int64_t seq_num = start_seq_num_unwrapped;
       seq_num <= end_sequence_number_unwrapped; ++seq_num) {
    auto& packet = GetPacket(seq_num);

    packet->video_header.is_first_packet_in_frame =
        (seq_num == start_seq_num_unwrapped);
    packet->video_header.is_last_packet_in_frame =
        (seq_num == end_sequence_number_unwrapped);

    if (packet->video_header.is_first_packet_in_frame) {
      if (width > 0 && height > 0) {
        packet->video_header.width = width;
        packet->video_header.height = height;
      }

      packet->video_header.frame_type = has_idr
                                            ? VideoFrameType::kVideoFrameKey
                                            : VideoFrameType::kVideoFrameDelta;
    }

    packet->video_payload =
        FixVideoPayload(packet->video_payload, packet->video_header);

    frames.push_back(std::move(packet));
  }

  return true;
}

}  // namespace webrtc

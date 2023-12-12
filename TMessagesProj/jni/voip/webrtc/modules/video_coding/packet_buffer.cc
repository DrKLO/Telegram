/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/packet_buffer.h"

#include <string.h>

#include <algorithm>
#include <cstdint>
#include <limits>
#include <utility>
#include <vector>

#include "absl/types/variant.h"
#include "api/array_view.h"
#include "api/rtp_packet_info.h"
#include "api/video/video_frame_type.h"
#include "common_video/h264/h264_common.h"
#ifndef DISABLE_H265
#include "common_video/h265/h265_common.h"
#endif
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "modules/rtp_rtcp/source/rtp_video_header.h"
#include "modules/video_coding/codecs/h264/include/h264_globals.h"
#ifndef DISABLE_H265
#include "modules/video_coding/codecs/h265/include/h265_globals.h"
#endif
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/mod_ops.h"

namespace webrtc {
namespace video_coding {

PacketBuffer::Packet::Packet(const RtpPacketReceived& rtp_packet,
                             const RTPVideoHeader& video_header)
    : marker_bit(rtp_packet.Marker()),
      payload_type(rtp_packet.PayloadType()),
      seq_num(rtp_packet.SequenceNumber()),
      timestamp(rtp_packet.Timestamp()),
      times_nacked(-1),
      video_header(video_header) {}

PacketBuffer::PacketBuffer(size_t start_buffer_size, size_t max_buffer_size)
    : max_size_(max_buffer_size),
      first_seq_num_(0),
      first_packet_received_(false),
      is_cleared_to_first_seq_num_(false),
      buffer_(start_buffer_size),
      sps_pps_idr_is_h264_keyframe_(false) {
  RTC_DCHECK_LE(start_buffer_size, max_buffer_size);
  // Buffer size must always be a power of 2.
  RTC_DCHECK((start_buffer_size & (start_buffer_size - 1)) == 0);
  RTC_DCHECK((max_buffer_size & (max_buffer_size - 1)) == 0);
}

PacketBuffer::~PacketBuffer() {
  Clear();
}

PacketBuffer::InsertResult PacketBuffer::InsertPacket(
    std::unique_ptr<PacketBuffer::Packet> packet) {
  PacketBuffer::InsertResult result;

  uint16_t seq_num = packet->seq_num;
  size_t index = seq_num % buffer_.size();

  if (!first_packet_received_) {
    first_seq_num_ = seq_num;
    first_packet_received_ = true;
  } else if (AheadOf(first_seq_num_, seq_num)) {
    // If we have explicitly cleared past this packet then it's old,
    // don't insert it, just silently ignore it.
    if (is_cleared_to_first_seq_num_) {
      return result;
    }

    first_seq_num_ = seq_num;
  }

  if (buffer_[index] != nullptr) {
    // Duplicate packet, just delete the payload.
    if (buffer_[index]->seq_num == packet->seq_num) {
      return result;
    }

    // The packet buffer is full, try to expand the buffer.
    while (ExpandBufferSize() && buffer_[seq_num % buffer_.size()] != nullptr) {
    }
    index = seq_num % buffer_.size();

    // Packet buffer is still full since we were unable to expand the buffer.
    if (buffer_[index] != nullptr) {
      // Clear the buffer, delete payload, and return false to signal that a
      // new keyframe is needed.
      RTC_LOG(LS_WARNING) << "Clear PacketBuffer and request key frame.";
      ClearInternal();
      result.buffer_cleared = true;
      return result;
    }
  }

  packet->continuous = false;
  buffer_[index] = std::move(packet);

  UpdateMissingPackets(seq_num);

  received_padding_.erase(
      received_padding_.begin(),
      received_padding_.lower_bound(seq_num - (buffer_.size() / 4)));

  result.packets = FindFrames(seq_num);
  return result;
}

void PacketBuffer::ClearTo(uint16_t seq_num) {
  // We have already cleared past this sequence number, no need to do anything.
  if (is_cleared_to_first_seq_num_ &&
      AheadOf<uint16_t>(first_seq_num_, seq_num)) {
    return;
  }

  // If the packet buffer was cleared between a frame was created and returned.
  if (!first_packet_received_)
    return;

  // Avoid iterating over the buffer more than once by capping the number of
  // iterations to the `size_` of the buffer.
  ++seq_num;
  size_t diff = ForwardDiff<uint16_t>(first_seq_num_, seq_num);
  size_t iterations = std::min(diff, buffer_.size());
  for (size_t i = 0; i < iterations; ++i) {
    auto& stored = buffer_[first_seq_num_ % buffer_.size()];
    if (stored != nullptr && AheadOf<uint16_t>(seq_num, stored->seq_num)) {
      stored = nullptr;
    }
    ++first_seq_num_;
  }

  // If `diff` is larger than `iterations` it means that we don't increment
  // `first_seq_num_` until we reach `seq_num`, so we set it here.
  first_seq_num_ = seq_num;

  is_cleared_to_first_seq_num_ = true;
  missing_packets_.erase(missing_packets_.begin(),
                         missing_packets_.lower_bound(seq_num));

  received_padding_.erase(received_padding_.begin(),
                          received_padding_.lower_bound(seq_num));
}

void PacketBuffer::Clear() {
  ClearInternal();
}

PacketBuffer::InsertResult PacketBuffer::InsertPadding(uint16_t seq_num) {
  PacketBuffer::InsertResult result;
  UpdateMissingPackets(seq_num);
  received_padding_.insert(seq_num);
  result.packets = FindFrames(static_cast<uint16_t>(seq_num + 1));
  return result;
}

void PacketBuffer::ForceSpsPpsIdrIsH264Keyframe() {
  sps_pps_idr_is_h264_keyframe_ = true;
}

void PacketBuffer::ResetSpsPpsIdrIsH264Keyframe() {
  sps_pps_idr_is_h264_keyframe_ = false;
}

void PacketBuffer::ClearInternal() {
  for (auto& entry : buffer_) {
    entry = nullptr;
  }

  first_packet_received_ = false;
  is_cleared_to_first_seq_num_ = false;
  newest_inserted_seq_num_.reset();
  missing_packets_.clear();
  received_padding_.clear();
}

bool PacketBuffer::ExpandBufferSize() {
  if (buffer_.size() == max_size_) {
    RTC_LOG(LS_WARNING) << "PacketBuffer is already at max size (" << max_size_
                        << "), failed to increase size.";
    return false;
  }

  size_t new_size = std::min(max_size_, 2 * buffer_.size());
  std::vector<std::unique_ptr<Packet>> new_buffer(new_size);
  for (std::unique_ptr<Packet>& entry : buffer_) {
    if (entry != nullptr) {
      new_buffer[entry->seq_num % new_size] = std::move(entry);
    }
  }
  buffer_ = std::move(new_buffer);
  RTC_LOG(LS_INFO) << "PacketBuffer size expanded to " << new_size;
  return true;
}

bool PacketBuffer::PotentialNewFrame(uint16_t seq_num) const {
  size_t index = seq_num % buffer_.size();
  int prev_index = index > 0 ? index - 1 : buffer_.size() - 1;
  const auto& entry = buffer_[index];
  const auto& prev_entry = buffer_[prev_index];

  if (entry == nullptr)
    return false;
  if (entry->seq_num != seq_num)
    return false;
  if (entry->is_first_packet_in_frame())
    return true;
  if (prev_entry == nullptr)
    return false;
  if (prev_entry->seq_num != static_cast<uint16_t>(entry->seq_num - 1))
    return false;
  if (prev_entry->timestamp != entry->timestamp)
    return false;
  if (prev_entry->continuous)
    return true;

  return false;
}

std::vector<std::unique_ptr<PacketBuffer::Packet>> PacketBuffer::FindFrames(
    uint16_t seq_num) {
  std::vector<std::unique_ptr<PacketBuffer::Packet>> found_frames;
  auto start = seq_num;

  for (size_t i = 0; i < buffer_.size(); ++i) {
    if (received_padding_.find(seq_num) != received_padding_.end()) {
      seq_num += 1;
      continue;
    }

    if (!PotentialNewFrame(seq_num)) {
      break;
    }

    size_t index = seq_num % buffer_.size();
    buffer_[index]->continuous = true;

    // If all packets of the frame is continuous, find the first packet of the
    // frame and add all packets of the frame to the returned packets.
    if (buffer_[index]->is_last_packet_in_frame()) {
      uint16_t start_seq_num = seq_num;

      // Find the start index by searching backward until the packet with
      // the `frame_begin` flag is set.
      int start_index = index;
      size_t tested_packets = 0;
      int64_t frame_timestamp = buffer_[start_index]->timestamp;

      // Identify H.264 keyframes by means of SPS, PPS, and IDR.
      bool is_h264 = buffer_[start_index]->codec() == kVideoCodecH264;
      bool has_h264_sps = false;
      bool has_h264_pps = false;
      bool has_h264_idr = false;
      bool is_h264_keyframe = false;

	  bool is_h265 = false;
#ifndef DISABLE_H265
      is_h265 = buffer_[start_index]->codec() == kVideoCodecH265;
      bool has_h265_sps = false;
      bool has_h265_pps = false;
      bool has_h265_idr = false;
      bool is_h265_keyframe = false;
#endif

      int idr_width = -1;
      int idr_height = -1;
      bool full_frame_found = false;
      while (true) {
        ++tested_packets;

        if (!is_h264 && !is_h265) {
          if (buffer_[start_index] == nullptr ||
              buffer_[start_index]->is_first_packet_in_frame()) {
            full_frame_found = buffer_[start_index] != nullptr;
            break;
          }
        }

        if (is_h264) {
          const auto* h264_header = absl::get_if<RTPVideoHeaderH264>(
              &buffer_[start_index]->video_header.video_type_header);
          if (!h264_header || h264_header->nalus_length >= kMaxNalusPerPacket)
            return found_frames;

          for (size_t j = 0; j < h264_header->nalus_length; ++j) {
            if (h264_header->nalus[j].type == H264::NaluType::kSps) {
              has_h264_sps = true;
            } else if (h264_header->nalus[j].type == H264::NaluType::kPps) {
              has_h264_pps = true;
            } else if (h264_header->nalus[j].type == H264::NaluType::kIdr) {
              has_h264_idr = true;
            }
          }
          if ((sps_pps_idr_is_h264_keyframe_ && has_h264_idr && has_h264_sps &&
               has_h264_pps) ||
              (!sps_pps_idr_is_h264_keyframe_ && has_h264_idr)) {
            is_h264_keyframe = true;
            // Store the resolution of key frame which is the packet with
            // smallest index and valid resolution; typically its IDR or SPS
            // packet; there may be packet preceeding this packet, IDR's
            // resolution will be applied to them.
            if (buffer_[start_index]->width() > 0 &&
                buffer_[start_index]->height() > 0) {
              idr_width = buffer_[start_index]->width();
              idr_height = buffer_[start_index]->height();
            }
          }
        }
#ifndef DISABLE_H265
        if (is_h265 && !is_h265_keyframe) {
          const auto* h265_header = absl::get_if<RTPVideoHeaderH265>(
              &buffer_[start_index]->video_header.video_type_header);
          if (!h265_header || h265_header->nalus_length >= kMaxNalusPerPacket)
            return found_frames;
          for (size_t j = 0; j < h265_header->nalus_length; ++j) {
            if (h265_header->nalus[j].type == H265::NaluType::kSps) {
              has_h265_sps = true;
            } else if (h265_header->nalus[j].type == H265::NaluType::kPps) {
              has_h265_pps = true;
            } else if (h265_header->nalus[j].type == H265::NaluType::kIdrWRadl
                       || h265_header->nalus[j].type == H265::NaluType::kIdrNLp
                       || h265_header->nalus[j].type == H265::NaluType::kCra) {
              has_h265_idr = true;
            }
          }
          if ((has_h265_sps && has_h265_pps) || has_h265_idr) {
            is_h265_keyframe = true;
			if (buffer_[start_index]->width() > 0 &&
                buffer_[start_index]->height() > 0) {
              idr_width = buffer_[start_index]->width();
              idr_height = buffer_[start_index]->height();
            }
          }
        }
#endif

        if (tested_packets == buffer_.size())
          break;

        start_index = start_index > 0 ? start_index - 1 : buffer_.size() - 1;

        // In the case of H264 we don't have a frame_begin bit (yes,
        // `frame_begin` might be set to true but that is a lie). So instead
        // we traverese backwards as long as we have a previous packet and
        // the timestamp of that packet is the same as this one. This may cause
        // the PacketBuffer to hand out incomplete frames.
        // See: https://bugs.chromium.org/p/webrtc/issues/detail?id=7106
        if ((is_h264 || is_h265) && (buffer_[start_index] == nullptr ||
                        buffer_[start_index]->timestamp != frame_timestamp)) {
          break;
        }

        --start_seq_num;
      }

      if (is_h264) {
        // Warn if this is an unsafe frame.
        if (has_h264_idr && (!has_h264_sps || !has_h264_pps)) {
          RTC_LOG(LS_WARNING)
              << "Received H.264-IDR frame "
                 "(SPS: "
              << has_h264_sps << ", PPS: " << has_h264_pps << "). Treating as "
              << (sps_pps_idr_is_h264_keyframe_ ? "delta" : "key")
              << " frame since WebRTC-SpsPpsIdrIsH264Keyframe is "
              << (sps_pps_idr_is_h264_keyframe_ ? "enabled." : "disabled");
        }

        // Now that we have decided whether to treat this frame as a key frame
        // or delta frame in the frame buffer, we update the field that
        // determines if the RtpFrameObject is a key frame or delta frame.
        const size_t first_packet_index = start_seq_num % buffer_.size();
        if (is_h264_keyframe) {
          buffer_[first_packet_index]->video_header.frame_type =
              VideoFrameType::kVideoFrameKey;
          if (idr_width > 0 && idr_height > 0) {
            // IDR frame was finalized and we have the correct resolution for
            // IDR; update first packet to have same resolution as IDR.
            buffer_[first_packet_index]->video_header.width = idr_width;
            buffer_[first_packet_index]->video_header.height = idr_height;
          }
        } else {
          buffer_[first_packet_index]->video_header.frame_type =
              VideoFrameType::kVideoFrameDelta;
        }

        // If this is not a keyframe, make sure there are no gaps in the packet
        // sequence numbers up until this point.
        if (!is_h264_keyframe && missing_packets_.upper_bound(start_seq_num) !=
                                     missing_packets_.begin()) {
          return found_frames;
        }
      }

#ifndef DISABLE_H265
      if (is_h265) {
        // Warn if this is an unsafe frame.
        if (has_h265_idr && (!has_h265_sps || !has_h265_pps)) {
          RTC_LOG(LS_WARNING)
              << "Received H.265-IDR frame "
              << "(SPS: " << has_h265_sps << ", PPS: " << has_h265_pps << "). "
              << "Treating as delta frame since "
              << "WebRTC-SpsPpsIdrIsH265Keyframe is always enabled.";
        }

        // Now that we have decided whether to treat this frame as a key frame
        // or delta frame in the frame buffer, we update the field that
        // determines if the RtpFrameObject is a key frame or delta frame.
        const size_t first_packet_index = start_seq_num % buffer_.size();
        if (is_h265_keyframe) {
          buffer_[first_packet_index]->video_header.frame_type =
		      VideoFrameType::kVideoFrameKey;
		  if (idr_width > 0 && idr_height > 0) {
            // IDR frame was finalized and we have the correct resolution for
            // IDR; update first packet to have same resolution as IDR.
            buffer_[first_packet_index]->video_header.width = idr_width;
            buffer_[first_packet_index]->video_header.height = idr_height;
          }
        } else {
          buffer_[first_packet_index]->video_header.frame_type =
		      VideoFrameType::kVideoFrameDelta;
        }

        // If this is not a key frame, make sure there are no gaps in the
        // packet sequence numbers up until this point.
        if (!is_h265_keyframe && missing_packets_.upper_bound(start_seq_num) !=
                missing_packets_.begin()) {
          return found_frames;
        }
      }
#endif
      if (is_h264 || is_h265 || full_frame_found) {
        const uint16_t end_seq_num = seq_num + 1;
        // Use uint16_t type to handle sequence number wrap around case.
        uint16_t num_packets = end_seq_num - start_seq_num;
        found_frames.reserve(found_frames.size() + num_packets);
        for (uint16_t i = start_seq_num; i != end_seq_num; ++i) {
          std::unique_ptr<Packet>& packet = buffer_[i % buffer_.size()];
          RTC_DCHECK(packet);
          RTC_DCHECK_EQ(i, packet->seq_num);
          // Ensure frame boundary flags are properly set.
          packet->video_header.is_first_packet_in_frame = (i == start_seq_num);
          packet->video_header.is_last_packet_in_frame = (i == seq_num);
          found_frames.push_back(std::move(packet));
        }

        missing_packets_.erase(missing_packets_.begin(),
                               missing_packets_.upper_bound(seq_num));
        received_padding_.erase(received_padding_.lower_bound(start),
                                received_padding_.upper_bound(seq_num));
      }
    }
    ++seq_num;
  }
  return found_frames;
}

void PacketBuffer::UpdateMissingPackets(uint16_t seq_num) {
  if (!newest_inserted_seq_num_)
    newest_inserted_seq_num_ = seq_num;

  const int kMaxPaddingAge = 1000;
  if (AheadOf(seq_num, *newest_inserted_seq_num_)) {
    uint16_t old_seq_num = seq_num - kMaxPaddingAge;
    auto erase_to = missing_packets_.lower_bound(old_seq_num);
    missing_packets_.erase(missing_packets_.begin(), erase_to);

    // Guard against inserting a large amount of missing packets if there is a
    // jump in the sequence number.
    if (AheadOf(old_seq_num, *newest_inserted_seq_num_))
      *newest_inserted_seq_num_ = old_seq_num;

    ++*newest_inserted_seq_num_;
    while (AheadOf(seq_num, *newest_inserted_seq_num_)) {
      missing_packets_.insert(*newest_inserted_seq_num_);
      ++*newest_inserted_seq_num_;
    }
  } else {
    missing_packets_.erase(seq_num);
  }
}

}  // namespace video_coding
}  // namespace webrtc

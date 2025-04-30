/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video/rtp_video_frame_assembler.h"

#include <algorithm>
#include <cstdint>
#include <map>
#include <memory>
#include <utility>
#include <vector>

#include "absl/container/inlined_vector.h"
#include "absl/types/optional.h"
#include "modules/rtp_rtcp/source/frame_object.h"
#include "modules/rtp_rtcp/source/rtp_dependency_descriptor_extension.h"
#include "modules/rtp_rtcp/source/rtp_generic_frame_descriptor_extension.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer_av1.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer_generic.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer_h264.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer_raw.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer_vp8.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer_vp9.h"
#include "modules/video_coding/packet_buffer.h"
#include "modules/video_coding/rtp_frame_reference_finder.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/sequence_number_unwrapper.h"

namespace webrtc {
namespace {
std::unique_ptr<VideoRtpDepacketizer> CreateDepacketizer(
    RtpVideoFrameAssembler::PayloadFormat payload_format) {
  switch (payload_format) {
    case RtpVideoFrameAssembler::kRaw:
      return std::make_unique<VideoRtpDepacketizerRaw>();
    case RtpVideoFrameAssembler::kH264:
      return std::make_unique<VideoRtpDepacketizerH264>();
    case RtpVideoFrameAssembler::kVp8:
      return std::make_unique<VideoRtpDepacketizerVp8>();
    case RtpVideoFrameAssembler::kVp9:
      return std::make_unique<VideoRtpDepacketizerVp9>();
    case RtpVideoFrameAssembler::kAv1:
      return std::make_unique<VideoRtpDepacketizerAv1>();
    case RtpVideoFrameAssembler::kGeneric:
      return std::make_unique<VideoRtpDepacketizerGeneric>();
    case RtpVideoFrameAssembler::kH265:
      // TODO(bugs.webrtc.org/13485): Implement VideoRtpDepacketizerH265
      RTC_DCHECK_NOTREACHED();
      return nullptr;
  }
  RTC_DCHECK_NOTREACHED();
  return nullptr;
}
}  // namespace

class RtpVideoFrameAssembler::Impl {
 public:
  explicit Impl(std::unique_ptr<VideoRtpDepacketizer> depacketizer);
  ~Impl() = default;

  FrameVector InsertPacket(const RtpPacketReceived& packet);

 private:
  using RtpFrameVector =
      absl::InlinedVector<std::unique_ptr<RtpFrameObject>, 3>;

  RtpFrameVector AssembleFrames(
      video_coding::PacketBuffer::InsertResult insert_result);
  FrameVector FindReferences(RtpFrameVector frames);
  FrameVector UpdateWithPadding(uint16_t seq_num);
  bool ParseDependenciesDescriptorExtension(const RtpPacketReceived& rtp_packet,
                                            RTPVideoHeader& video_header);
  bool ParseGenericDescriptorExtension(const RtpPacketReceived& rtp_packet,
                                       RTPVideoHeader& video_header);
  void ClearOldData(uint16_t incoming_seq_num);

  std::unique_ptr<FrameDependencyStructure> video_structure_;
  SeqNumUnwrapper<uint16_t> frame_id_unwrapper_;
  absl::optional<int64_t> video_structure_frame_id_;
  std::unique_ptr<VideoRtpDepacketizer> depacketizer_;
  video_coding::PacketBuffer packet_buffer_;
  RtpFrameReferenceFinder reference_finder_;
};

RtpVideoFrameAssembler::Impl::Impl(
    std::unique_ptr<VideoRtpDepacketizer> depacketizer)
    : depacketizer_(std::move(depacketizer)),
      packet_buffer_(/*start_buffer_size=*/2048, /*max_buffer_size=*/2048) {}

RtpVideoFrameAssembler::FrameVector RtpVideoFrameAssembler::Impl::InsertPacket(
    const RtpPacketReceived& rtp_packet) {
  if (rtp_packet.payload_size() == 0) {
    ClearOldData(rtp_packet.SequenceNumber());
    return UpdateWithPadding(rtp_packet.SequenceNumber());
  }

  absl::optional<VideoRtpDepacketizer::ParsedRtpPayload> parsed_payload =
      depacketizer_->Parse(rtp_packet.PayloadBuffer());

  if (parsed_payload == absl::nullopt) {
    return {};
  }

  if (rtp_packet.HasExtension<RtpDependencyDescriptorExtension>()) {
    if (!ParseDependenciesDescriptorExtension(rtp_packet,
                                              parsed_payload->video_header)) {
      return {};
    }
  } else if (rtp_packet.HasExtension<RtpGenericFrameDescriptorExtension00>()) {
    if (!ParseGenericDescriptorExtension(rtp_packet,
                                         parsed_payload->video_header)) {
      return {};
    }
  }

  parsed_payload->video_header.is_last_packet_in_frame |= rtp_packet.Marker();

  auto packet = std::make_unique<video_coding::PacketBuffer::Packet>(
      rtp_packet, parsed_payload->video_header);
  packet->video_payload = std::move(parsed_payload->video_payload);

  ClearOldData(rtp_packet.SequenceNumber());
  return FindReferences(
      AssembleFrames(packet_buffer_.InsertPacket(std::move(packet))));
}

void RtpVideoFrameAssembler::Impl::ClearOldData(uint16_t incoming_seq_num) {
  constexpr uint16_t kOldSeqNumThreshold = 2000;
  uint16_t old_seq_num = incoming_seq_num - kOldSeqNumThreshold;
  packet_buffer_.ClearTo(old_seq_num);
  reference_finder_.ClearTo(old_seq_num);
}

RtpVideoFrameAssembler::Impl::RtpFrameVector
RtpVideoFrameAssembler::Impl::AssembleFrames(
    video_coding::PacketBuffer::InsertResult insert_result) {
  video_coding::PacketBuffer::Packet* first_packet = nullptr;
  std::vector<rtc::ArrayView<const uint8_t>> payloads;
  RtpFrameVector result;

  for (auto& packet : insert_result.packets) {
    if (packet->is_first_packet_in_frame()) {
      first_packet = packet.get();
      payloads.clear();
    }
    payloads.emplace_back(packet->video_payload);

    if (packet->is_last_packet_in_frame()) {
      rtc::scoped_refptr<EncodedImageBuffer> bitstream =
          depacketizer_->AssembleFrame(payloads);

      if (!bitstream) {
        continue;
      }

      const video_coding::PacketBuffer::Packet& last_packet = *packet;
      result.push_back(std::make_unique<RtpFrameObject>(
          first_packet->seq_num,                  //
          last_packet.seq_num,                    //
          last_packet.marker_bit,                 //
          /*times_nacked=*/0,                     //
          /*first_packet_received_time=*/0,       //
          /*last_packet_received_time=*/0,        //
          first_packet->timestamp,                //
          /*ntp_time_ms=*/0,                      //
          /*timing=*/VideoSendTiming(),           //
          first_packet->payload_type,             //
          first_packet->codec(),                  //
          last_packet.video_header.rotation,      //
          last_packet.video_header.content_type,  //
          first_packet->video_header,             //
          last_packet.video_header.color_space,   //
          /*packet_infos=*/RtpPacketInfos(),      //
          std::move(bitstream)));
    }
  }

  return result;
}

RtpVideoFrameAssembler::FrameVector
RtpVideoFrameAssembler::Impl::FindReferences(RtpFrameVector frames) {
  FrameVector res;
  for (auto& frame : frames) {
    auto complete_frames = reference_finder_.ManageFrame(std::move(frame));
    for (std::unique_ptr<RtpFrameObject>& complete_frame : complete_frames) {
      uint16_t rtp_seq_num_start = complete_frame->first_seq_num();
      uint16_t rtp_seq_num_end = complete_frame->last_seq_num();
      res.emplace_back(rtp_seq_num_start, rtp_seq_num_end,
                       std::move(complete_frame));
    }
  }
  return res;
}

RtpVideoFrameAssembler::FrameVector
RtpVideoFrameAssembler::Impl::UpdateWithPadding(uint16_t seq_num) {
  auto res =
      FindReferences(AssembleFrames(packet_buffer_.InsertPadding(seq_num)));
  auto ref_finder_update = reference_finder_.PaddingReceived(seq_num);

  for (std::unique_ptr<RtpFrameObject>& complete_frame : ref_finder_update) {
    uint16_t rtp_seq_num_start = complete_frame->first_seq_num();
    uint16_t rtp_seq_num_end = complete_frame->last_seq_num();
    res.emplace_back(rtp_seq_num_start, rtp_seq_num_end,
                     std::move(complete_frame));
  }

  return res;
}

bool RtpVideoFrameAssembler::Impl::ParseDependenciesDescriptorExtension(
    const RtpPacketReceived& rtp_packet,
    RTPVideoHeader& video_header) {
  webrtc::DependencyDescriptor dependency_descriptor;

  if (!rtp_packet.GetExtension<RtpDependencyDescriptorExtension>(
          video_structure_.get(), &dependency_descriptor)) {
    // Descriptor is either malformed, or the template referenced is not in
    // the `video_structure_` currently being held.
    // TODO(bugs.webrtc.org/10342): Improve packet reordering behavior.
    RTC_LOG(LS_WARNING) << "ssrc: " << rtp_packet.Ssrc()
                        << " Failed to parse dependency descriptor.";
    return false;
  }

  if (dependency_descriptor.attached_structure != nullptr &&
      !dependency_descriptor.first_packet_in_frame) {
    RTC_LOG(LS_WARNING) << "ssrc: " << rtp_packet.Ssrc()
                        << "Invalid dependency descriptor: structure "
                           "attached to non first packet of a frame.";
    return false;
  }

  video_header.is_first_packet_in_frame =
      dependency_descriptor.first_packet_in_frame;
  video_header.is_last_packet_in_frame =
      dependency_descriptor.last_packet_in_frame;

  int64_t frame_id =
      frame_id_unwrapper_.Unwrap(dependency_descriptor.frame_number);
  auto& generic_descriptor_info = video_header.generic.emplace();
  generic_descriptor_info.frame_id = frame_id;
  generic_descriptor_info.spatial_index =
      dependency_descriptor.frame_dependencies.spatial_id;
  generic_descriptor_info.temporal_index =
      dependency_descriptor.frame_dependencies.temporal_id;

  for (int fdiff : dependency_descriptor.frame_dependencies.frame_diffs) {
    generic_descriptor_info.dependencies.push_back(frame_id - fdiff);
  }
  for (int cdiff : dependency_descriptor.frame_dependencies.chain_diffs) {
    generic_descriptor_info.chain_diffs.push_back(frame_id - cdiff);
  }
  generic_descriptor_info.decode_target_indications =
      dependency_descriptor.frame_dependencies.decode_target_indications;
  if (dependency_descriptor.resolution) {
    video_header.width = dependency_descriptor.resolution->Width();
    video_header.height = dependency_descriptor.resolution->Height();
  }
  if (dependency_descriptor.active_decode_targets_bitmask.has_value()) {
    generic_descriptor_info.active_decode_targets =
        *dependency_descriptor.active_decode_targets_bitmask;
  }

  // FrameDependencyStructure is sent in the dependency descriptor of the first
  // packet of a key frame and is required to parse all subsequent packets until
  // the next key frame.
  if (dependency_descriptor.attached_structure) {
    RTC_DCHECK(dependency_descriptor.first_packet_in_frame);
    if (video_structure_frame_id_ > frame_id) {
      RTC_LOG(LS_WARNING)
          << "Arrived key frame with id " << frame_id << " and structure id "
          << dependency_descriptor.attached_structure->structure_id
          << " is older than the latest received key frame with id "
          << *video_structure_frame_id_ << " and structure id "
          << video_structure_->structure_id;
      return false;
    }
    video_structure_ = std::move(dependency_descriptor.attached_structure);
    video_structure_frame_id_ = frame_id;
    video_header.frame_type = VideoFrameType::kVideoFrameKey;
  } else {
    video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  }
  return true;
}

bool RtpVideoFrameAssembler::Impl::ParseGenericDescriptorExtension(
    const RtpPacketReceived& rtp_packet,
    RTPVideoHeader& video_header) {
  RtpGenericFrameDescriptor generic_frame_descriptor;
  if (!rtp_packet.GetExtension<RtpGenericFrameDescriptorExtension00>(
          &generic_frame_descriptor)) {
    return false;
  }

  video_header.is_first_packet_in_frame =
      generic_frame_descriptor.FirstPacketInSubFrame();
  video_header.is_last_packet_in_frame =
      generic_frame_descriptor.LastPacketInSubFrame();

  if (generic_frame_descriptor.FirstPacketInSubFrame()) {
    video_header.frame_type =
        generic_frame_descriptor.FrameDependenciesDiffs().empty()
            ? VideoFrameType::kVideoFrameKey
            : VideoFrameType::kVideoFrameDelta;

    auto& generic_descriptor_info = video_header.generic.emplace();
    int64_t frame_id =
        frame_id_unwrapper_.Unwrap(generic_frame_descriptor.FrameId());
    generic_descriptor_info.frame_id = frame_id;
    generic_descriptor_info.spatial_index =
        generic_frame_descriptor.SpatialLayer();
    generic_descriptor_info.temporal_index =
        generic_frame_descriptor.TemporalLayer();
    for (uint16_t fdiff : generic_frame_descriptor.FrameDependenciesDiffs()) {
      generic_descriptor_info.dependencies.push_back(frame_id - fdiff);
    }
  }
  video_header.width = generic_frame_descriptor.Width();
  video_header.height = generic_frame_descriptor.Height();
  return true;
}

RtpVideoFrameAssembler::RtpVideoFrameAssembler(PayloadFormat payload_format)
    : impl_(std::make_unique<Impl>(CreateDepacketizer(payload_format))) {}

RtpVideoFrameAssembler::~RtpVideoFrameAssembler() = default;

RtpVideoFrameAssembler::FrameVector RtpVideoFrameAssembler::InsertPacket(
    const RtpPacketReceived& packet) {
  return impl_->InsertPacket(packet);
}

}  // namespace webrtc

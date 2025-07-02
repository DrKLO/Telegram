/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <vector>

#include "api/array_view.h"
#include "api/video/rtp_video_frame_assembler.h"
#include "modules/rtp_rtcp/source/rtp_dependency_descriptor_extension.h"
#include "modules/rtp_rtcp/source/rtp_format.h"
#include "modules/rtp_rtcp/source/rtp_generic_frame_descriptor_extension.h"
#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "modules/rtp_rtcp/source/rtp_packetizer_av1_test_helper.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {
namespace {

using ::testing::ElementsAreArray;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::Matches;
using ::testing::SizeIs;
using ::testing::UnorderedElementsAre;
using ::testing::UnorderedElementsAreArray;
using PayloadFormat = RtpVideoFrameAssembler::PayloadFormat;

class PacketBuilder {
 public:
  explicit PacketBuilder(PayloadFormat format)
      : format_(format), packet_to_send_(&extension_manager_) {}

  PacketBuilder& WithSeqNum(uint16_t seq_num) {
    seq_num_ = seq_num;
    return *this;
  }

  PacketBuilder& WithPayload(rtc::ArrayView<const uint8_t> payload) {
    payload_.assign(payload.begin(), payload.end());
    return *this;
  }

  PacketBuilder& WithVideoHeader(const RTPVideoHeader& video_header) {
    video_header_ = video_header;
    return *this;
  }

  template <typename T, typename... Args>
  PacketBuilder& WithExtension(int id, const Args&... args) {
    extension_manager_.Register<T>(id);
    packet_to_send_.IdentifyExtensions(extension_manager_);
    packet_to_send_.SetExtension<T>(std::forward<const Args>(args)...);
    return *this;
  }

  RtpPacketReceived Build() {
    auto packetizer =
        RtpPacketizer::Create(GetVideoCodecType(), payload_, {}, video_header_);
    packetizer->NextPacket(&packet_to_send_);
    packet_to_send_.SetSequenceNumber(seq_num_);

    RtpPacketReceived received(&extension_manager_);
    received.Parse(packet_to_send_.Buffer());
    return received;
  }

 private:
  absl::optional<VideoCodecType> GetVideoCodecType() {
    switch (format_) {
      case PayloadFormat::kRaw: {
        return absl::nullopt;
      }
      case PayloadFormat::kH264: {
        return kVideoCodecH264;
      }
      case PayloadFormat::kVp8: {
        return kVideoCodecVP8;
      }
      case PayloadFormat::kVp9: {
        return kVideoCodecVP9;
      }
      case PayloadFormat::kAv1: {
        return kVideoCodecAV1;
      }
      case PayloadFormat::kH265: {
        return kVideoCodecH265;
      }
      case PayloadFormat::kGeneric: {
        return kVideoCodecGeneric;
      }
    }
    RTC_DCHECK_NOTREACHED();
    return absl::nullopt;
  }

  const RtpVideoFrameAssembler::PayloadFormat format_;
  uint16_t seq_num_ = 0;
  std::vector<uint8_t> payload_;
  RTPVideoHeader video_header_;
  RtpPacketReceived::ExtensionManager extension_manager_;
  RtpPacketToSend packet_to_send_;
};

RtpPacketReceived PaddingPacket(uint16_t seq_num) {
  RtpPacketReceived padding_packet;
  padding_packet.SetSequenceNumber(seq_num);
  padding_packet.SetPadding(224);
  return padding_packet;
}

void AppendFrames(RtpVideoFrameAssembler::FrameVector from,
                  RtpVideoFrameAssembler::FrameVector& to) {
  to.insert(to.end(), std::make_move_iterator(from.begin()),
            std::make_move_iterator(from.end()));
}

rtc::ArrayView<int64_t> References(const std::unique_ptr<EncodedFrame>& frame) {
  return rtc::MakeArrayView(frame->references, frame->num_references);
}

rtc::ArrayView<uint8_t> Payload(const std::unique_ptr<EncodedFrame>& frame) {
  return rtc::ArrayView<uint8_t>(*frame->GetEncodedData());
}

TEST(RtpVideoFrameAssembler, Vp8Packetization) {
  RtpVideoFrameAssembler assembler(RtpVideoFrameAssembler::kVp8);

  // When sending VP8 over RTP parts of the payload is actually inspected at the
  // RTP level. It just so happen that the initial 'V' sets the keyframe bit
  // (0x01) to the correct value.
  uint8_t kKeyframePayload[] = "Vp8Keyframe";
  ASSERT_EQ(kKeyframePayload[0] & 0x01, 0);

  uint8_t kDeltaframePayload[] = "SomeFrame";
  ASSERT_EQ(kDeltaframePayload[0] & 0x01, 1);

  RtpVideoFrameAssembler::FrameVector frames;

  RTPVideoHeader video_header;
  auto& vp8_header =
      video_header.video_type_header.emplace<RTPVideoHeaderVP8>();

  vp8_header.pictureId = 10;
  vp8_header.tl0PicIdx = 0;
  AppendFrames(assembler.InsertPacket(PacketBuilder(PayloadFormat::kVp8)
                                          .WithPayload(kKeyframePayload)
                                          .WithVideoHeader(video_header)
                                          .Build()),
               frames);

  vp8_header.pictureId = 11;
  vp8_header.tl0PicIdx = 1;
  AppendFrames(assembler.InsertPacket(PacketBuilder(PayloadFormat::kVp8)
                                          .WithPayload(kDeltaframePayload)
                                          .WithVideoHeader(video_header)
                                          .Build()),
               frames);

  ASSERT_THAT(frames, SizeIs(2));

  auto first_frame = frames[0].ExtractFrame();
  EXPECT_THAT(first_frame->Id(), Eq(10));
  EXPECT_THAT(References(first_frame), IsEmpty());
  EXPECT_THAT(Payload(first_frame), ElementsAreArray(kKeyframePayload));

  auto second_frame = frames[1].ExtractFrame();
  EXPECT_THAT(second_frame->Id(), Eq(11));
  EXPECT_THAT(References(second_frame), UnorderedElementsAre(10));
  EXPECT_THAT(Payload(second_frame), ElementsAreArray(kDeltaframePayload));
}

TEST(RtpVideoFrameAssembler, Vp9Packetization) {
  RtpVideoFrameAssembler assembler(RtpVideoFrameAssembler::kVp9);
  RtpVideoFrameAssembler::FrameVector frames;

  uint8_t kPayload[] = "SomePayload";

  RTPVideoHeader video_header;
  auto& vp9_header =
      video_header.video_type_header.emplace<RTPVideoHeaderVP9>();
  vp9_header.InitRTPVideoHeaderVP9();

  vp9_header.picture_id = 10;
  vp9_header.tl0_pic_idx = 0;
  AppendFrames(assembler.InsertPacket(PacketBuilder(PayloadFormat::kVp9)
                                          .WithPayload(kPayload)
                                          .WithVideoHeader(video_header)
                                          .Build()),
               frames);

  vp9_header.picture_id = 11;
  vp9_header.tl0_pic_idx = 1;
  vp9_header.inter_pic_predicted = true;
  AppendFrames(assembler.InsertPacket(PacketBuilder(PayloadFormat::kVp9)
                                          .WithPayload(kPayload)
                                          .WithVideoHeader(video_header)
                                          .Build()),
               frames);

  ASSERT_THAT(frames, SizeIs(2));

  auto first_frame = frames[0].ExtractFrame();
  EXPECT_THAT(first_frame->Id(), Eq(10));
  EXPECT_THAT(Payload(first_frame), ElementsAreArray(kPayload));
  EXPECT_THAT(References(first_frame), IsEmpty());

  auto second_frame = frames[1].ExtractFrame();
  EXPECT_THAT(second_frame->Id(), Eq(11));
  EXPECT_THAT(Payload(second_frame), ElementsAreArray(kPayload));
  EXPECT_THAT(References(second_frame), UnorderedElementsAre(10));
}

TEST(RtpVideoFrameAssembler, Av1Packetization) {
  RtpVideoFrameAssembler assembler(RtpVideoFrameAssembler::kAv1);
  RtpVideoFrameAssembler::FrameVector frames;

  auto kKeyframePayload =
      BuildAv1Frame({Av1Obu(kAv1ObuTypeSequenceHeader).WithPayload({1, 2, 3}),
                     Av1Obu(kAv1ObuTypeFrame).WithPayload({4, 5, 6})});

  auto kDeltaframePayload =
      BuildAv1Frame({Av1Obu(kAv1ObuTypeFrame).WithPayload({7, 8, 9})});

  RTPVideoHeader video_header;

  video_header.frame_type = VideoFrameType::kVideoFrameKey;
  AppendFrames(assembler.InsertPacket(PacketBuilder(PayloadFormat::kAv1)
                                          .WithPayload(kKeyframePayload)
                                          .WithVideoHeader(video_header)
                                          .WithSeqNum(20)
                                          .Build()),
               frames);

  AppendFrames(assembler.InsertPacket(PacketBuilder(PayloadFormat::kAv1)
                                          .WithPayload(kDeltaframePayload)
                                          .WithSeqNum(21)
                                          .Build()),
               frames);

  ASSERT_THAT(frames, SizeIs(2));

  auto first_frame = frames[0].ExtractFrame();
  EXPECT_THAT(first_frame->Id(), Eq(20));
  EXPECT_THAT(Payload(first_frame), ElementsAreArray(kKeyframePayload));
  EXPECT_THAT(References(first_frame), IsEmpty());

  auto second_frame = frames[1].ExtractFrame();
  EXPECT_THAT(second_frame->Id(), Eq(21));
  EXPECT_THAT(Payload(second_frame), ElementsAreArray(kDeltaframePayload));
  EXPECT_THAT(References(second_frame), UnorderedElementsAre(20));
}

TEST(RtpVideoFrameAssembler, RawPacketizationDependencyDescriptorExtension) {
  RtpVideoFrameAssembler assembler(RtpVideoFrameAssembler::kRaw);
  RtpVideoFrameAssembler::FrameVector frames;
  uint8_t kPayload[] = "SomePayload";

  FrameDependencyStructure dependency_structure;
  dependency_structure.num_decode_targets = 1;
  dependency_structure.num_chains = 1;
  dependency_structure.decode_target_protected_by_chain.push_back(0);
  dependency_structure.templates.push_back(
      FrameDependencyTemplate().S(0).T(0).Dtis("S").ChainDiffs({0}));
  dependency_structure.templates.push_back(
      FrameDependencyTemplate().S(0).T(0).Dtis("S").ChainDiffs({10}).FrameDiffs(
          {10}));

  DependencyDescriptor dependency_descriptor;

  dependency_descriptor.frame_number = 10;
  dependency_descriptor.frame_dependencies = dependency_structure.templates[0];
  dependency_descriptor.attached_structure =
      std::make_unique<FrameDependencyStructure>(dependency_structure);
  AppendFrames(assembler.InsertPacket(
                   PacketBuilder(PayloadFormat::kRaw)
                       .WithPayload(kPayload)
                       .WithExtension<RtpDependencyDescriptorExtension>(
                           1, dependency_structure, dependency_descriptor)
                       .Build()),
               frames);

  dependency_descriptor.frame_number = 20;
  dependency_descriptor.frame_dependencies = dependency_structure.templates[1];
  dependency_descriptor.attached_structure.reset();
  AppendFrames(assembler.InsertPacket(
                   PacketBuilder(PayloadFormat::kRaw)
                       .WithPayload(kPayload)
                       .WithExtension<RtpDependencyDescriptorExtension>(
                           1, dependency_structure, dependency_descriptor)
                       .Build()),
               frames);

  ASSERT_THAT(frames, SizeIs(2));

  auto first_frame = frames[0].ExtractFrame();
  EXPECT_THAT(first_frame->Id(), Eq(10));
  EXPECT_THAT(Payload(first_frame), ElementsAreArray(kPayload));
  EXPECT_THAT(References(first_frame), IsEmpty());

  auto second_frame = frames[1].ExtractFrame();
  EXPECT_THAT(second_frame->Id(), Eq(20));
  EXPECT_THAT(Payload(second_frame), ElementsAreArray(kPayload));
  EXPECT_THAT(References(second_frame), UnorderedElementsAre(10));
}

TEST(RtpVideoFrameAssembler, RawPacketizationGenericDescriptor00Extension) {
  RtpVideoFrameAssembler assembler(RtpVideoFrameAssembler::kRaw);
  RtpVideoFrameAssembler::FrameVector frames;
  uint8_t kPayload[] = "SomePayload";

  RtpGenericFrameDescriptor generic;

  generic.SetFirstPacketInSubFrame(true);
  generic.SetLastPacketInSubFrame(true);
  generic.SetFrameId(100);
  AppendFrames(
      assembler.InsertPacket(
          PacketBuilder(PayloadFormat::kRaw)
              .WithPayload(kPayload)
              .WithExtension<RtpGenericFrameDescriptorExtension00>(1, generic)
              .Build()),
      frames);

  generic.SetFrameId(102);
  generic.AddFrameDependencyDiff(2);
  AppendFrames(
      assembler.InsertPacket(
          PacketBuilder(PayloadFormat::kRaw)
              .WithPayload(kPayload)
              .WithExtension<RtpGenericFrameDescriptorExtension00>(1, generic)
              .Build()),
      frames);

  ASSERT_THAT(frames, SizeIs(2));

  auto first_frame = frames[0].ExtractFrame();
  EXPECT_THAT(first_frame->Id(), Eq(100));
  EXPECT_THAT(Payload(first_frame), ElementsAreArray(kPayload));
  EXPECT_THAT(References(first_frame), IsEmpty());

  auto second_frame = frames[1].ExtractFrame();
  EXPECT_THAT(second_frame->Id(), Eq(102));
  EXPECT_THAT(Payload(second_frame), ElementsAreArray(kPayload));
  EXPECT_THAT(References(second_frame), UnorderedElementsAre(100));
}

TEST(RtpVideoFrameAssembler, RawPacketizationGenericPayloadDescriptor) {
  RtpVideoFrameAssembler assembler(RtpVideoFrameAssembler::kGeneric);
  RtpVideoFrameAssembler::FrameVector frames;
  uint8_t kPayload[] = "SomePayload";

  RTPVideoHeader video_header;

  video_header.frame_type = VideoFrameType::kVideoFrameKey;
  AppendFrames(assembler.InsertPacket(PacketBuilder(PayloadFormat::kGeneric)
                                          .WithPayload(kPayload)
                                          .WithVideoHeader(video_header)
                                          .WithSeqNum(123)
                                          .Build()),
               frames);

  video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  AppendFrames(assembler.InsertPacket(PacketBuilder(PayloadFormat::kGeneric)
                                          .WithPayload(kPayload)
                                          .WithVideoHeader(video_header)
                                          .WithSeqNum(124)
                                          .Build()),
               frames);

  ASSERT_THAT(frames, SizeIs(2));

  auto first_frame = frames[0].ExtractFrame();
  EXPECT_THAT(first_frame->Id(), Eq(123));
  EXPECT_THAT(Payload(first_frame), ElementsAreArray(kPayload));
  EXPECT_THAT(References(first_frame), IsEmpty());

  auto second_frame = frames[1].ExtractFrame();
  EXPECT_THAT(second_frame->Id(), Eq(124));
  EXPECT_THAT(Payload(second_frame), ElementsAreArray(kPayload));
  EXPECT_THAT(References(second_frame), UnorderedElementsAre(123));
}

TEST(RtpVideoFrameAssembler, Padding) {
  RtpVideoFrameAssembler assembler(RtpVideoFrameAssembler::kGeneric);
  RtpVideoFrameAssembler::FrameVector frames;
  uint8_t kPayload[] = "SomePayload";

  RTPVideoHeader video_header;

  video_header.frame_type = VideoFrameType::kVideoFrameKey;
  AppendFrames(assembler.InsertPacket(PacketBuilder(PayloadFormat::kGeneric)
                                          .WithPayload(kPayload)
                                          .WithVideoHeader(video_header)
                                          .WithSeqNum(123)
                                          .Build()),
               frames);

  video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  AppendFrames(assembler.InsertPacket(PacketBuilder(PayloadFormat::kGeneric)
                                          .WithPayload(kPayload)
                                          .WithVideoHeader(video_header)
                                          .WithSeqNum(125)
                                          .Build()),
               frames);

  ASSERT_THAT(frames, SizeIs(1));
  auto first_frame = frames[0].ExtractFrame();
  EXPECT_THAT(first_frame->Id(), Eq(123));
  EXPECT_THAT(Payload(first_frame), ElementsAreArray(kPayload));
  EXPECT_THAT(References(first_frame), IsEmpty());

  AppendFrames(assembler.InsertPacket(PaddingPacket(/*seq_num=*/124)), frames);

  ASSERT_THAT(frames, SizeIs(2));
  auto second_frame = frames[1].ExtractFrame();
  EXPECT_THAT(second_frame->Id(), Eq(125));
  EXPECT_THAT(Payload(second_frame), ElementsAreArray(kPayload));
  EXPECT_THAT(References(second_frame), UnorderedElementsAre(123));
}

TEST(RtpVideoFrameAssembler, ClearOldPackets) {
  RtpVideoFrameAssembler assembler(RtpVideoFrameAssembler::kGeneric);

  // If we don't have a payload the packet will be counted as a padding packet.
  uint8_t kPayload[] = "DontCare";

  RTPVideoHeader video_header;
  video_header.frame_type = VideoFrameType::kVideoFrameKey;
  EXPECT_THAT(assembler.InsertPacket(PacketBuilder(PayloadFormat::kGeneric)
                                         .WithPayload(kPayload)
                                         .WithVideoHeader(video_header)
                                         .WithSeqNum(0)
                                         .Build()),
              SizeIs(1));

  EXPECT_THAT(assembler.InsertPacket(PacketBuilder(PayloadFormat::kGeneric)
                                         .WithPayload(kPayload)
                                         .WithVideoHeader(video_header)
                                         .WithSeqNum(2000)
                                         .Build()),
              SizeIs(1));

  EXPECT_THAT(assembler.InsertPacket(PacketBuilder(PayloadFormat::kGeneric)
                                         .WithPayload(kPayload)
                                         .WithVideoHeader(video_header)
                                         .WithSeqNum(0)
                                         .Build()),
              SizeIs(0));

  EXPECT_THAT(assembler.InsertPacket(PacketBuilder(PayloadFormat::kGeneric)
                                         .WithPayload(kPayload)
                                         .WithVideoHeader(video_header)
                                         .WithSeqNum(1)
                                         .Build()),
              SizeIs(1));
}

TEST(RtpVideoFrameAssembler, ClearOldPacketsWithPadding) {
  RtpVideoFrameAssembler assembler(RtpVideoFrameAssembler::kGeneric);
  uint8_t kPayload[] = "DontCare";

  RTPVideoHeader video_header;
  video_header.frame_type = VideoFrameType::kVideoFrameKey;
  EXPECT_THAT(assembler.InsertPacket(PacketBuilder(PayloadFormat::kGeneric)
                                         .WithPayload(kPayload)
                                         .WithVideoHeader(video_header)
                                         .WithSeqNum(0)
                                         .Build()),
              SizeIs(1));

  EXPECT_THAT(assembler.InsertPacket(PaddingPacket(/*seq_num=*/2000)),
              SizeIs(0));

  EXPECT_THAT(assembler.InsertPacket(PacketBuilder(PayloadFormat::kGeneric)
                                         .WithPayload(kPayload)
                                         .WithVideoHeader(video_header)
                                         .WithSeqNum(0)
                                         .Build()),
              SizeIs(0));

  EXPECT_THAT(assembler.InsertPacket(PacketBuilder(PayloadFormat::kGeneric)
                                         .WithPayload(kPayload)
                                         .WithVideoHeader(video_header)
                                         .WithSeqNum(1)
                                         .Build()),
              SizeIs(1));
}

TEST(RtpVideoFrameAssembler, SeqNumStartAndSeqNumEndSet) {
  RtpVideoFrameAssembler assembler(RtpVideoFrameAssembler::kGeneric);
  RtpVideoFrameAssembler::FrameVector frames;
  uint8_t kPayload[] =
      "Some payload that will get split into two when packetized.";

  RTPVideoHeader video_header;
  video_header.frame_type = VideoFrameType::kVideoFrameKey;
  RtpPacketizer::PayloadSizeLimits limits;
  limits.max_payload_len = sizeof(kPayload) - 1;

  auto packetizer =
      RtpPacketizer::Create(kVideoCodecGeneric, kPayload, limits, video_header);
  ASSERT_THAT(packetizer->NumPackets(), Eq(2U));

  RtpPacketReceived::ExtensionManager extension_manager;
  {
    RtpPacketToSend send_packet(&extension_manager);
    packetizer->NextPacket(&send_packet);
    send_packet.SetSequenceNumber(123);
    RtpPacketReceived received_packet(&extension_manager);
    received_packet.Parse(send_packet.Buffer());
    assembler.InsertPacket(received_packet);
  }

  {
    RtpPacketToSend send_packet(&extension_manager);
    packetizer->NextPacket(&send_packet);
    send_packet.SetSequenceNumber(124);
    RtpPacketReceived received_packet(&extension_manager);
    received_packet.Parse(send_packet.Buffer());
    AppendFrames(assembler.InsertPacket(received_packet), frames);
  }

  ASSERT_THAT(frames, SizeIs(1));
  EXPECT_THAT(frames[0].RtpSeqNumStart(), Eq(123));
  EXPECT_THAT(frames[0].RtpSeqNumEnd(), Eq(124));
}

TEST(RtpVideoFrameAssembler, SeqNumStartAndSeqNumEndSetWhenPaddingReceived) {
  RtpVideoFrameAssembler assembler(RtpVideoFrameAssembler::kGeneric);
  RtpVideoFrameAssembler::FrameVector frames;
  uint8_t kPayload[] =
      "Some payload that will get split into two when packetized.";

  RTPVideoHeader video_header;
  video_header.frame_type = VideoFrameType::kVideoFrameKey;

  EXPECT_THAT(assembler.InsertPacket(PacketBuilder(PayloadFormat::kGeneric)
                                         .WithPayload(kPayload)
                                         .WithVideoHeader(video_header)
                                         .WithSeqNum(121)
                                         .Build()),
              SizeIs(1));

  video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  RtpPacketReceived::ExtensionManager extension_manager;
  RtpPacketizer::PayloadSizeLimits limits;
  limits.max_payload_len = sizeof(kPayload) - 1;

  auto packetizer =
      RtpPacketizer::Create(kVideoCodecGeneric, kPayload, limits, video_header);
  ASSERT_THAT(packetizer->NumPackets(), Eq(2U));

  {
    RtpPacketToSend send_packet(&extension_manager);
    packetizer->NextPacket(&send_packet);
    send_packet.SetSequenceNumber(123);
    RtpPacketReceived received_packet(&extension_manager);
    received_packet.Parse(send_packet.Buffer());
    assembler.InsertPacket(received_packet);
  }

  {
    RtpPacketToSend send_packet(&extension_manager);
    packetizer->NextPacket(&send_packet);
    send_packet.SetSequenceNumber(124);
    RtpPacketReceived received_packet(&extension_manager);
    received_packet.Parse(send_packet.Buffer());
    assembler.InsertPacket(received_packet);
  }

  AppendFrames(assembler.InsertPacket(PaddingPacket(/*seq_num=*/122)), frames);

  ASSERT_THAT(frames, SizeIs(1));
  EXPECT_THAT(frames[0].RtpSeqNumStart(), Eq(123));
  EXPECT_THAT(frames[0].RtpSeqNumEnd(), Eq(124));
}

}  // namespace
}  // namespace webrtc

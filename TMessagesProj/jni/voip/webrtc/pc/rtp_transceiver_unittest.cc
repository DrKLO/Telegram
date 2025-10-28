/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file contains tests for `RtpTransceiver`.

#include "pc/rtp_transceiver.h"

#include <memory>
#include <utility>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/environment/environment_factory.h"
#include "api/peer_connection_interface.h"
#include "api/rtp_parameters.h"
#include "media/base/media_engine.h"
#include "pc/test/enable_fake_media.h"
#include "pc/test/mock_channel_interface.h"
#include "pc/test/mock_rtp_receiver_internal.h"
#include "pc/test/mock_rtp_sender_internal.h"
#include "rtc_base/thread.h"
#include "test/gmock.h"
#include "test/gtest.h"

using ::testing::_;
using ::testing::ElementsAre;
using ::testing::Field;
using ::testing::Optional;
using ::testing::Property;
using ::testing::Return;
using ::testing::ReturnRef;

namespace webrtc {

namespace {

class RtpTransceiverTest : public testing::Test {
 public:
  RtpTransceiverTest()
      : dependencies_(MakeDependencies()),
        context_(
            ConnectionContext::Create(CreateEnvironment(), &dependencies_)) {}

 protected:
  cricket::MediaEngineInterface* media_engine() {
    return context_->media_engine();
  }
  ConnectionContext* context() { return context_.get(); }

 private:
  rtc::AutoThread main_thread_;

  static PeerConnectionFactoryDependencies MakeDependencies() {
    PeerConnectionFactoryDependencies d;
    d.network_thread = rtc::Thread::Current();
    d.worker_thread = rtc::Thread::Current();
    d.signaling_thread = rtc::Thread::Current();
    EnableFakeMedia(d);
    return d;
  }

  PeerConnectionFactoryDependencies dependencies_;
  rtc::scoped_refptr<ConnectionContext> context_;
};

// Checks that a channel cannot be set on a stopped `RtpTransceiver`.
TEST_F(RtpTransceiverTest, CannotSetChannelOnStoppedTransceiver) {
  const std::string content_name("my_mid");
  auto transceiver = rtc::make_ref_counted<RtpTransceiver>(
      cricket::MediaType::MEDIA_TYPE_AUDIO, context());
  auto channel1 = std::make_unique<cricket::MockChannelInterface>();
  EXPECT_CALL(*channel1, media_type())
      .WillRepeatedly(Return(cricket::MediaType::MEDIA_TYPE_AUDIO));
  EXPECT_CALL(*channel1, mid()).WillRepeatedly(ReturnRef(content_name));
  EXPECT_CALL(*channel1, SetFirstPacketReceivedCallback(_));
  EXPECT_CALL(*channel1, SetRtpTransport(_)).WillRepeatedly(Return(true));
  auto channel1_ptr = channel1.get();
  transceiver->SetChannel(std::move(channel1), [&](const std::string& mid) {
    EXPECT_EQ(mid, content_name);
    return nullptr;
  });
  EXPECT_EQ(channel1_ptr, transceiver->channel());

  // Stop the transceiver.
  transceiver->StopInternal();
  EXPECT_EQ(channel1_ptr, transceiver->channel());

  auto channel2 = std::make_unique<cricket::MockChannelInterface>();
  EXPECT_CALL(*channel2, media_type())
      .WillRepeatedly(Return(cricket::MediaType::MEDIA_TYPE_AUDIO));

  // Clear the current channel - required to allow SetChannel()
  EXPECT_CALL(*channel1_ptr, SetFirstPacketReceivedCallback(_));
  transceiver->ClearChannel();
  // Channel can no longer be set, so this call should be a no-op.
  transceiver->SetChannel(std::move(channel2),
                          [](const std::string&) { return nullptr; });
  EXPECT_EQ(nullptr, transceiver->channel());
}

// Checks that a channel can be unset on a stopped `RtpTransceiver`
TEST_F(RtpTransceiverTest, CanUnsetChannelOnStoppedTransceiver) {
  const std::string content_name("my_mid");
  auto transceiver = rtc::make_ref_counted<RtpTransceiver>(
      cricket::MediaType::MEDIA_TYPE_VIDEO, context());
  auto channel = std::make_unique<cricket::MockChannelInterface>();
  EXPECT_CALL(*channel, media_type())
      .WillRepeatedly(Return(cricket::MediaType::MEDIA_TYPE_VIDEO));
  EXPECT_CALL(*channel, mid()).WillRepeatedly(ReturnRef(content_name));
  EXPECT_CALL(*channel, SetFirstPacketReceivedCallback(_))
      .WillRepeatedly(testing::Return());
  EXPECT_CALL(*channel, SetRtpTransport(_)).WillRepeatedly(Return(true));

  auto channel_ptr = channel.get();
  transceiver->SetChannel(std::move(channel), [&](const std::string& mid) {
    EXPECT_EQ(mid, content_name);
    return nullptr;
  });
  EXPECT_EQ(channel_ptr, transceiver->channel());

  // Stop the transceiver.
  transceiver->StopInternal();
  EXPECT_EQ(channel_ptr, transceiver->channel());

  // Set the channel to `nullptr`.
  transceiver->ClearChannel();
  EXPECT_EQ(nullptr, transceiver->channel());
}

class RtpTransceiverUnifiedPlanTest : public RtpTransceiverTest {
 public:
  RtpTransceiverUnifiedPlanTest()
      : transceiver_(rtc::make_ref_counted<RtpTransceiver>(
            RtpSenderProxyWithInternal<RtpSenderInternal>::Create(
                rtc::Thread::Current(),
                sender_),
            RtpReceiverProxyWithInternal<RtpReceiverInternal>::Create(
                rtc::Thread::Current(),
                rtc::Thread::Current(),
                receiver_),
            context(),
            media_engine()->voice().GetRtpHeaderExtensions(),
            /* on_negotiation_needed= */ [] {})) {}

  static rtc::scoped_refptr<MockRtpReceiverInternal> MockReceiver() {
    auto receiver = rtc::make_ref_counted<MockRtpReceiverInternal>();
    EXPECT_CALL(*receiver.get(), media_type())
        .WillRepeatedly(Return(cricket::MediaType::MEDIA_TYPE_AUDIO));
    return receiver;
  }

  static rtc::scoped_refptr<MockRtpSenderInternal> MockSender() {
    auto sender = rtc::make_ref_counted<MockRtpSenderInternal>();
    EXPECT_CALL(*sender.get(), media_type())
        .WillRepeatedly(Return(cricket::MediaType::MEDIA_TYPE_AUDIO));
    return sender;
  }

  rtc::AutoThread main_thread_;
  rtc::scoped_refptr<MockRtpReceiverInternal> receiver_ = MockReceiver();
  rtc::scoped_refptr<MockRtpSenderInternal> sender_ = MockSender();
  rtc::scoped_refptr<RtpTransceiver> transceiver_;
};

// Basic tests for Stop()
TEST_F(RtpTransceiverUnifiedPlanTest, StopSetsDirection) {
  EXPECT_CALL(*receiver_.get(), Stop());
  EXPECT_CALL(*receiver_.get(), SetMediaChannel(_));
  EXPECT_CALL(*sender_.get(), SetTransceiverAsStopped());
  EXPECT_CALL(*sender_.get(), Stop());

  EXPECT_EQ(RtpTransceiverDirection::kInactive, transceiver_->direction());
  EXPECT_FALSE(transceiver_->current_direction());
  transceiver_->StopStandard();
  EXPECT_EQ(RtpTransceiverDirection::kStopped, transceiver_->direction());
  EXPECT_FALSE(transceiver_->current_direction());
  transceiver_->StopTransceiverProcedure();
  EXPECT_TRUE(transceiver_->current_direction());
  EXPECT_EQ(RtpTransceiverDirection::kStopped, transceiver_->direction());
  EXPECT_EQ(RtpTransceiverDirection::kStopped,
            *transceiver_->current_direction());
}

class RtpTransceiverTestForHeaderExtensions : public RtpTransceiverTest {
 public:
  RtpTransceiverTestForHeaderExtensions()
      : extensions_(
            {RtpHeaderExtensionCapability("uri1",
                                          1,
                                          RtpTransceiverDirection::kSendOnly),
             RtpHeaderExtensionCapability("uri2",
                                          2,
                                          RtpTransceiverDirection::kRecvOnly),
             RtpHeaderExtensionCapability(RtpExtension::kMidUri,
                                          3,
                                          RtpTransceiverDirection::kSendRecv),
             RtpHeaderExtensionCapability(RtpExtension::kVideoRotationUri,
                                          4,
                                          RtpTransceiverDirection::kSendRecv)}),
        transceiver_(rtc::make_ref_counted<RtpTransceiver>(
            RtpSenderProxyWithInternal<RtpSenderInternal>::Create(
                rtc::Thread::Current(),
                sender_),
            RtpReceiverProxyWithInternal<RtpReceiverInternal>::Create(
                rtc::Thread::Current(),
                rtc::Thread::Current(),
                receiver_),
            context(),
            extensions_,
            /* on_negotiation_needed= */ [] {})) {}

  static rtc::scoped_refptr<MockRtpReceiverInternal> MockReceiver() {
    auto receiver = rtc::make_ref_counted<MockRtpReceiverInternal>();
    EXPECT_CALL(*receiver.get(), media_type())
        .WillRepeatedly(Return(cricket::MediaType::MEDIA_TYPE_AUDIO));
    return receiver;
  }

  static rtc::scoped_refptr<MockRtpSenderInternal> MockSender() {
    auto sender = rtc::make_ref_counted<MockRtpSenderInternal>();
    EXPECT_CALL(*sender.get(), media_type())
        .WillRepeatedly(Return(cricket::MediaType::MEDIA_TYPE_AUDIO));
    return sender;
  }

  void ClearChannel() {
    EXPECT_CALL(*sender_.get(), SetMediaChannel(_));
    transceiver_->ClearChannel();
  }

  rtc::AutoThread main_thread_;
  rtc::scoped_refptr<MockRtpReceiverInternal> receiver_ = MockReceiver();
  rtc::scoped_refptr<MockRtpSenderInternal> sender_ = MockSender();

  std::vector<RtpHeaderExtensionCapability> extensions_;
  rtc::scoped_refptr<RtpTransceiver> transceiver_;
};

TEST_F(RtpTransceiverTestForHeaderExtensions, OffersChannelManagerList) {
  EXPECT_CALL(*receiver_.get(), Stop());
  EXPECT_CALL(*receiver_.get(), SetMediaChannel(_));
  EXPECT_CALL(*sender_.get(), SetTransceiverAsStopped());
  EXPECT_CALL(*sender_.get(), Stop());

  EXPECT_EQ(transceiver_->GetHeaderExtensionsToNegotiate(), extensions_);
}

TEST_F(RtpTransceiverTestForHeaderExtensions, ModifiesDirection) {
  EXPECT_CALL(*receiver_.get(), Stop());
  EXPECT_CALL(*receiver_.get(), SetMediaChannel(_));
  EXPECT_CALL(*sender_.get(), SetTransceiverAsStopped());
  EXPECT_CALL(*sender_.get(), Stop());

  auto modified_extensions = extensions_;
  modified_extensions[0].direction = RtpTransceiverDirection::kSendOnly;
  EXPECT_TRUE(
      transceiver_->SetHeaderExtensionsToNegotiate(modified_extensions).ok());
  EXPECT_EQ(transceiver_->GetHeaderExtensionsToNegotiate(),
            modified_extensions);
  modified_extensions[0].direction = RtpTransceiverDirection::kRecvOnly;
  EXPECT_TRUE(
      transceiver_->SetHeaderExtensionsToNegotiate(modified_extensions).ok());
  EXPECT_EQ(transceiver_->GetHeaderExtensionsToNegotiate(),
            modified_extensions);
  modified_extensions[0].direction = RtpTransceiverDirection::kSendRecv;
  EXPECT_TRUE(
      transceiver_->SetHeaderExtensionsToNegotiate(modified_extensions).ok());
  EXPECT_EQ(transceiver_->GetHeaderExtensionsToNegotiate(),
            modified_extensions);
  modified_extensions[0].direction = RtpTransceiverDirection::kInactive;
  EXPECT_TRUE(
      transceiver_->SetHeaderExtensionsToNegotiate(modified_extensions).ok());
  EXPECT_EQ(transceiver_->GetHeaderExtensionsToNegotiate(),
            modified_extensions);
}

TEST_F(RtpTransceiverTestForHeaderExtensions, AcceptsStoppedExtension) {
  EXPECT_CALL(*receiver_.get(), Stop());
  EXPECT_CALL(*receiver_.get(), SetMediaChannel(_));
  EXPECT_CALL(*sender_.get(), SetTransceiverAsStopped());
  EXPECT_CALL(*sender_.get(), Stop());

  auto modified_extensions = extensions_;
  modified_extensions[0].direction = RtpTransceiverDirection::kStopped;
  EXPECT_TRUE(
      transceiver_->SetHeaderExtensionsToNegotiate(modified_extensions).ok());
  EXPECT_EQ(transceiver_->GetHeaderExtensionsToNegotiate(),
            modified_extensions);
}

TEST_F(RtpTransceiverTestForHeaderExtensions, RejectsDifferentSize) {
  EXPECT_CALL(*receiver_.get(), Stop());
  EXPECT_CALL(*receiver_.get(), SetMediaChannel(_));
  EXPECT_CALL(*sender_.get(), SetTransceiverAsStopped());
  EXPECT_CALL(*sender_.get(), Stop());

  auto modified_extensions = extensions_;
  modified_extensions.pop_back();

  EXPECT_THAT(transceiver_->SetHeaderExtensionsToNegotiate(modified_extensions),
              Property(&RTCError::type, RTCErrorType::INVALID_MODIFICATION));
  EXPECT_EQ(transceiver_->GetHeaderExtensionsToNegotiate(), extensions_);
}

TEST_F(RtpTransceiverTestForHeaderExtensions, RejectsChangedUri) {
  EXPECT_CALL(*receiver_.get(), Stop());
  EXPECT_CALL(*receiver_.get(), SetMediaChannel(_));
  EXPECT_CALL(*sender_.get(), SetTransceiverAsStopped());
  EXPECT_CALL(*sender_.get(), Stop());

  auto modified_extensions = extensions_;
  ASSERT_TRUE(!modified_extensions.empty());
  modified_extensions[0].uri = "http://webrtc.org";

  EXPECT_THAT(transceiver_->SetHeaderExtensionsToNegotiate(modified_extensions),
              Property(&RTCError::type, RTCErrorType::INVALID_MODIFICATION));
  EXPECT_EQ(transceiver_->GetHeaderExtensionsToNegotiate(), extensions_);
}

TEST_F(RtpTransceiverTestForHeaderExtensions, RejectsReorder) {
  EXPECT_CALL(*receiver_.get(), Stop());
  EXPECT_CALL(*receiver_.get(), SetMediaChannel(_));
  EXPECT_CALL(*sender_.get(), SetTransceiverAsStopped());
  EXPECT_CALL(*sender_.get(), Stop());

  auto modified_extensions = extensions_;
  ASSERT_GE(modified_extensions.size(), 2u);
  std::swap(modified_extensions[0], modified_extensions[1]);

  EXPECT_THAT(transceiver_->SetHeaderExtensionsToNegotiate(modified_extensions),
              Property(&RTCError::type, RTCErrorType::INVALID_MODIFICATION));
  EXPECT_EQ(transceiver_->GetHeaderExtensionsToNegotiate(), extensions_);
}

TEST_F(RtpTransceiverTestForHeaderExtensions,
       RejectsStoppedMandatoryExtensions) {
  EXPECT_CALL(*receiver_.get(), Stop());
  EXPECT_CALL(*receiver_.get(), SetMediaChannel(_));
  EXPECT_CALL(*sender_.get(), SetTransceiverAsStopped());
  EXPECT_CALL(*sender_.get(), Stop());

  std::vector<RtpHeaderExtensionCapability> modified_extensions = extensions_;
  // Attempting to stop the mandatory MID extension.
  modified_extensions[2].direction = RtpTransceiverDirection::kStopped;
  EXPECT_THAT(transceiver_->SetHeaderExtensionsToNegotiate(modified_extensions),
              Property(&RTCError::type, RTCErrorType::INVALID_MODIFICATION));
  EXPECT_EQ(transceiver_->GetHeaderExtensionsToNegotiate(), extensions_);
}

TEST_F(RtpTransceiverTestForHeaderExtensions,
       NoNegotiatedHdrExtsWithoutChannel) {
  EXPECT_CALL(*receiver_.get(), Stop());
  EXPECT_CALL(*receiver_.get(), SetMediaChannel(_));
  EXPECT_CALL(*sender_.get(), SetTransceiverAsStopped());
  EXPECT_CALL(*sender_.get(), Stop());
  EXPECT_THAT(transceiver_->GetNegotiatedHeaderExtensions(),
              ElementsAre(Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped)));
}

TEST_F(RtpTransceiverTestForHeaderExtensions,
       NoNegotiatedHdrExtsWithChannelWithoutNegotiation) {
  const std::string content_name("my_mid");
  EXPECT_CALL(*receiver_.get(), SetMediaChannel(_)).WillRepeatedly(Return());
  EXPECT_CALL(*receiver_.get(), Stop()).WillRepeatedly(Return());
  EXPECT_CALL(*sender_.get(), SetMediaChannel(_));
  EXPECT_CALL(*sender_.get(), SetTransceiverAsStopped());
  EXPECT_CALL(*sender_.get(), Stop());
  auto mock_channel = std::make_unique<cricket::MockChannelInterface>();
  auto mock_channel_ptr = mock_channel.get();
  EXPECT_CALL(*mock_channel, SetFirstPacketReceivedCallback(_));
  EXPECT_CALL(*mock_channel, media_type())
      .WillRepeatedly(Return(cricket::MediaType::MEDIA_TYPE_AUDIO));
  EXPECT_CALL(*mock_channel, voice_media_send_channel())
      .WillRepeatedly(Return(nullptr));
  EXPECT_CALL(*mock_channel, mid()).WillRepeatedly(ReturnRef(content_name));
  EXPECT_CALL(*mock_channel, SetRtpTransport(_)).WillRepeatedly(Return(true));
  transceiver_->SetChannel(std::move(mock_channel),
                           [](const std::string&) { return nullptr; });
  EXPECT_THAT(transceiver_->GetNegotiatedHeaderExtensions(),
              ElementsAre(Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped)));

  EXPECT_CALL(*mock_channel_ptr, SetFirstPacketReceivedCallback(_));
  ClearChannel();
}

TEST_F(RtpTransceiverTestForHeaderExtensions, ReturnsNegotiatedHdrExts) {
  const std::string content_name("my_mid");
  EXPECT_CALL(*receiver_.get(), SetMediaChannel(_)).WillRepeatedly(Return());
  EXPECT_CALL(*receiver_.get(), Stop()).WillRepeatedly(Return());
  EXPECT_CALL(*sender_.get(), SetMediaChannel(_));
  EXPECT_CALL(*sender_.get(), SetTransceiverAsStopped());
  EXPECT_CALL(*sender_.get(), Stop());

  auto mock_channel = std::make_unique<cricket::MockChannelInterface>();
  auto mock_channel_ptr = mock_channel.get();
  EXPECT_CALL(*mock_channel, SetFirstPacketReceivedCallback(_));
  EXPECT_CALL(*mock_channel, media_type())
      .WillRepeatedly(Return(cricket::MediaType::MEDIA_TYPE_AUDIO));
  EXPECT_CALL(*mock_channel, voice_media_send_channel())
      .WillRepeatedly(Return(nullptr));
  EXPECT_CALL(*mock_channel, mid()).WillRepeatedly(ReturnRef(content_name));
  EXPECT_CALL(*mock_channel, SetRtpTransport(_)).WillRepeatedly(Return(true));

  cricket::RtpHeaderExtensions extensions = {RtpExtension("uri1", 1),
                                             RtpExtension("uri2", 2)};
  cricket::AudioContentDescription description;
  description.set_rtp_header_extensions(extensions);
  transceiver_->OnNegotiationUpdate(SdpType::kAnswer, &description);

  transceiver_->SetChannel(std::move(mock_channel),
                           [](const std::string&) { return nullptr; });

  EXPECT_THAT(transceiver_->GetNegotiatedHeaderExtensions(),
              ElementsAre(Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kSendRecv),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kSendRecv),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped)));
  EXPECT_CALL(*mock_channel_ptr, SetFirstPacketReceivedCallback(_));
  ClearChannel();
}

TEST_F(RtpTransceiverTestForHeaderExtensions,
       ReturnsNegotiatedHdrExtsSecondTime) {
  EXPECT_CALL(*receiver_.get(), Stop());
  EXPECT_CALL(*receiver_.get(), SetMediaChannel(_));
  EXPECT_CALL(*sender_.get(), SetTransceiverAsStopped());
  EXPECT_CALL(*sender_.get(), Stop());

  cricket::RtpHeaderExtensions extensions = {RtpExtension("uri1", 1),
                                             RtpExtension("uri2", 2)};
  cricket::AudioContentDescription description;
  description.set_rtp_header_extensions(extensions);
  transceiver_->OnNegotiationUpdate(SdpType::kAnswer, &description);

  EXPECT_THAT(transceiver_->GetNegotiatedHeaderExtensions(),
              ElementsAre(Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kSendRecv),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kSendRecv),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped)));
  extensions = {RtpExtension("uri3", 4), RtpExtension("uri5", 6)};
  description.set_rtp_header_extensions(extensions);
  transceiver_->OnNegotiationUpdate(SdpType::kAnswer, &description);

  EXPECT_THAT(transceiver_->GetNegotiatedHeaderExtensions(),
              ElementsAre(Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped),
                          Field(&RtpHeaderExtensionCapability::direction,
                                RtpTransceiverDirection::kStopped)));
}

TEST_F(RtpTransceiverTestForHeaderExtensions,
       SimulcastOrSvcEnablesExtensionsByDefault) {
  std::vector<RtpHeaderExtensionCapability> extensions = {
      {RtpExtension::kDependencyDescriptorUri, 1,
       RtpTransceiverDirection::kStopped},
      {RtpExtension::kVideoLayersAllocationUri, 2,
       RtpTransceiverDirection::kStopped},
  };

  // Default is stopped.
  auto sender = rtc::make_ref_counted<MockRtpSenderInternal>();
  auto transceiver = rtc::make_ref_counted<RtpTransceiver>(
      RtpSenderProxyWithInternal<RtpSenderInternal>::Create(
          rtc::Thread::Current(), sender),
      RtpReceiverProxyWithInternal<RtpReceiverInternal>::Create(
          rtc::Thread::Current(), rtc::Thread::Current(), receiver_),
      context(), extensions,
      /* on_negotiation_needed= */ [] {});
  std::vector<webrtc::RtpHeaderExtensionCapability> header_extensions =
      transceiver->GetHeaderExtensionsToNegotiate();
  ASSERT_EQ(header_extensions.size(), 2u);
  EXPECT_EQ(header_extensions[0].uri, RtpExtension::kDependencyDescriptorUri);
  EXPECT_EQ(header_extensions[0].direction, RtpTransceiverDirection::kStopped);
  EXPECT_EQ(header_extensions[1].uri, RtpExtension::kVideoLayersAllocationUri);
  EXPECT_EQ(header_extensions[1].direction, RtpTransceiverDirection::kStopped);

  // Simulcast, i.e. more than one encoding.
  RtpParameters simulcast_parameters;
  simulcast_parameters.encodings.resize(2);
  auto simulcast_sender = rtc::make_ref_counted<MockRtpSenderInternal>();
  EXPECT_CALL(*simulcast_sender, GetParametersInternal())
      .WillRepeatedly(Return(simulcast_parameters));
  auto simulcast_transceiver = rtc::make_ref_counted<RtpTransceiver>(
      RtpSenderProxyWithInternal<RtpSenderInternal>::Create(
          rtc::Thread::Current(), simulcast_sender),
      RtpReceiverProxyWithInternal<RtpReceiverInternal>::Create(
          rtc::Thread::Current(), rtc::Thread::Current(), receiver_),
      context(), extensions,
      /* on_negotiation_needed= */ [] {});
  auto simulcast_extensions =
      simulcast_transceiver->GetHeaderExtensionsToNegotiate();
  ASSERT_EQ(simulcast_extensions.size(), 2u);
  EXPECT_EQ(simulcast_extensions[0].uri,
            RtpExtension::kDependencyDescriptorUri);
  EXPECT_EQ(simulcast_extensions[0].direction,
            RtpTransceiverDirection::kSendRecv);
  EXPECT_EQ(simulcast_extensions[1].uri,
            RtpExtension::kVideoLayersAllocationUri);
  EXPECT_EQ(simulcast_extensions[1].direction,
            RtpTransceiverDirection::kSendRecv);

  // SVC, a single encoding with a scalabilityMode other than L1T1.
  webrtc::RtpParameters svc_parameters;
  svc_parameters.encodings.resize(1);
  svc_parameters.encodings[0].scalability_mode = "L3T3";

  auto svc_sender = rtc::make_ref_counted<MockRtpSenderInternal>();
  EXPECT_CALL(*svc_sender, GetParametersInternal())
      .WillRepeatedly(Return(svc_parameters));
  auto svc_transceiver = rtc::make_ref_counted<RtpTransceiver>(
      RtpSenderProxyWithInternal<RtpSenderInternal>::Create(
          rtc::Thread::Current(), svc_sender),
      RtpReceiverProxyWithInternal<RtpReceiverInternal>::Create(
          rtc::Thread::Current(), rtc::Thread::Current(), receiver_),
      context(), extensions,
      /* on_negotiation_needed= */ [] {});
  std::vector<webrtc::RtpHeaderExtensionCapability> svc_extensions =
      svc_transceiver->GetHeaderExtensionsToNegotiate();
  ASSERT_EQ(svc_extensions.size(), 2u);
  EXPECT_EQ(svc_extensions[0].uri, RtpExtension::kDependencyDescriptorUri);
  EXPECT_EQ(svc_extensions[0].direction, RtpTransceiverDirection::kSendRecv);
  EXPECT_EQ(svc_extensions[1].uri, RtpExtension::kVideoLayersAllocationUri);
  EXPECT_EQ(svc_extensions[1].direction, RtpTransceiverDirection::kSendRecv);
}

}  // namespace

}  // namespace webrtc

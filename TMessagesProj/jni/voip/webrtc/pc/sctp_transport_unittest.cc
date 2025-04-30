/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/sctp_transport.h"

#include <utility>
#include <vector>

#include "absl/memory/memory.h"
#include "absl/types/optional.h"
#include "api/dtls_transport_interface.h"
#include "api/transport/data_channel_transport_interface.h"
#include "media/base/media_channel.h"
#include "p2p/base/fake_dtls_transport.h"
#include "p2p/base/p2p_constants.h"
#include "p2p/base/packet_transport_internal.h"
#include "pc/dtls_transport.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/gunit.h"
#include "test/gmock.h"
#include "test/gtest.h"

constexpr int kDefaultTimeout = 1000;  // milliseconds
constexpr int kTestMaxSctpStreams = 1234;

using cricket::FakeDtlsTransport;
using ::testing::ElementsAre;

namespace webrtc {

namespace {

class FakeCricketSctpTransport : public cricket::SctpTransportInternal {
 public:
  void SetOnConnectedCallback(std::function<void()> callback) override {
    on_connected_callback_ = std::move(callback);
  }
  void SetDataChannelSink(DataChannelSink* sink) override {}
  void SetDtlsTransport(rtc::PacketTransportInternal* transport) override {}
  bool Start(int local_port, int remote_port, int max_message_size) override {
    return true;
  }
  bool OpenStream(int sid) override { return true; }
  bool ResetStream(int sid) override { return true; }
  RTCError SendData(int sid,
                    const SendDataParams& params,
                    const rtc::CopyOnWriteBuffer& payload) override {
    return RTCError::OK();
  }
  bool ReadyToSendData() override { return true; }
  void set_debug_name_for_testing(const char* debug_name) override {}
  int max_message_size() const override { return 0; }
  absl::optional<int> max_outbound_streams() const override {
    return max_outbound_streams_;
  }
  absl::optional<int> max_inbound_streams() const override {
    return max_inbound_streams_;
  }

  void SendSignalAssociationChangeCommunicationUp() {
    ASSERT_TRUE(on_connected_callback_);
    on_connected_callback_();
  }

  void set_max_outbound_streams(int streams) {
    max_outbound_streams_ = streams;
  }
  void set_max_inbound_streams(int streams) { max_inbound_streams_ = streams; }

 private:
  absl::optional<int> max_outbound_streams_;
  absl::optional<int> max_inbound_streams_;
  std::function<void()> on_connected_callback_;
};

}  // namespace

class TestSctpTransportObserver : public SctpTransportObserverInterface {
 public:
  TestSctpTransportObserver() : info_(SctpTransportState::kNew) {}

  void OnStateChange(SctpTransportInformation info) override {
    info_ = info;
    states_.push_back(info.state());
  }

  SctpTransportState State() {
    if (states_.size() > 0) {
      return states_[states_.size() - 1];
    } else {
      return SctpTransportState::kNew;
    }
  }

  const std::vector<SctpTransportState>& States() { return states_; }

  const SctpTransportInformation LastReceivedInformation() { return info_; }

 private:
  std::vector<SctpTransportState> states_;
  SctpTransportInformation info_;
};

class SctpTransportTest : public ::testing::Test {
 public:
  SctpTransport* transport() { return transport_.get(); }
  SctpTransportObserverInterface* observer() { return &observer_; }

  void CreateTransport() {
    auto cricket_sctp_transport =
        absl::WrapUnique(new FakeCricketSctpTransport());
    transport_ =
        rtc::make_ref_counted<SctpTransport>(std::move(cricket_sctp_transport));
  }

  void AddDtlsTransport() {
    std::unique_ptr<cricket::DtlsTransportInternal> cricket_transport =
        std::make_unique<FakeDtlsTransport>(
            "audio", cricket::ICE_CANDIDATE_COMPONENT_RTP);
    dtls_transport_ =
        rtc::make_ref_counted<DtlsTransport>(std::move(cricket_transport));
    transport_->SetDtlsTransport(dtls_transport_);
  }

  void CompleteSctpHandshake() {
    // The computed MaxChannels shall be the minimum of the outgoing
    // and incoming # of streams.
    CricketSctpTransport()->set_max_outbound_streams(kTestMaxSctpStreams);
    CricketSctpTransport()->set_max_inbound_streams(kTestMaxSctpStreams + 1);
    CricketSctpTransport()->SendSignalAssociationChangeCommunicationUp();
  }

  FakeCricketSctpTransport* CricketSctpTransport() {
    return static_cast<FakeCricketSctpTransport*>(transport_->internal());
  }

  rtc::AutoThread main_thread_;
  rtc::scoped_refptr<SctpTransport> transport_;
  rtc::scoped_refptr<DtlsTransport> dtls_transport_;
  TestSctpTransportObserver observer_;
};

TEST(SctpTransportSimpleTest, CreateClearDelete) {
  rtc::AutoThread main_thread;
  std::unique_ptr<cricket::SctpTransportInternal> fake_cricket_sctp_transport =
      absl::WrapUnique(new FakeCricketSctpTransport());
  rtc::scoped_refptr<SctpTransport> sctp_transport =
      rtc::make_ref_counted<SctpTransport>(
          std::move(fake_cricket_sctp_transport));
  ASSERT_TRUE(sctp_transport->internal());
  ASSERT_EQ(SctpTransportState::kNew, sctp_transport->Information().state());
  sctp_transport->Clear();
  ASSERT_FALSE(sctp_transport->internal());
  ASSERT_EQ(SctpTransportState::kClosed, sctp_transport->Information().state());
}

TEST_F(SctpTransportTest, EventsObservedWhenConnecting) {
  CreateTransport();
  transport()->RegisterObserver(observer());
  AddDtlsTransport();
  CompleteSctpHandshake();
  ASSERT_EQ_WAIT(SctpTransportState::kConnected, observer_.State(),
                 kDefaultTimeout);
  EXPECT_THAT(observer_.States(), ElementsAre(SctpTransportState::kConnecting,
                                              SctpTransportState::kConnected));
}

TEST_F(SctpTransportTest, CloseWhenClearing) {
  CreateTransport();
  transport()->RegisterObserver(observer());
  AddDtlsTransport();
  CompleteSctpHandshake();
  ASSERT_EQ_WAIT(SctpTransportState::kConnected, observer_.State(),
                 kDefaultTimeout);
  transport()->Clear();
  ASSERT_EQ_WAIT(SctpTransportState::kClosed, observer_.State(),
                 kDefaultTimeout);
}

TEST_F(SctpTransportTest, MaxChannelsSignalled) {
  CreateTransport();
  transport()->RegisterObserver(observer());
  AddDtlsTransport();
  EXPECT_FALSE(transport()->Information().MaxChannels());
  EXPECT_FALSE(observer_.LastReceivedInformation().MaxChannels());
  CompleteSctpHandshake();
  ASSERT_EQ_WAIT(SctpTransportState::kConnected, observer_.State(),
                 kDefaultTimeout);
  EXPECT_TRUE(transport()->Information().MaxChannels());
  EXPECT_EQ(kTestMaxSctpStreams, *(transport()->Information().MaxChannels()));
  EXPECT_TRUE(observer_.LastReceivedInformation().MaxChannels());
  EXPECT_EQ(kTestMaxSctpStreams,
            *(observer_.LastReceivedInformation().MaxChannels()));
}

TEST_F(SctpTransportTest, CloseWhenTransportCloses) {
  CreateTransport();
  transport()->RegisterObserver(observer());
  AddDtlsTransport();
  CompleteSctpHandshake();
  ASSERT_EQ_WAIT(SctpTransportState::kConnected, observer_.State(),
                 kDefaultTimeout);
  static_cast<cricket::FakeDtlsTransport*>(dtls_transport_->internal())
      ->SetDtlsState(DtlsTransportState::kClosed);
  ASSERT_EQ_WAIT(SctpTransportState::kClosed, observer_.State(),
                 kDefaultTimeout);
}

}  // namespace webrtc

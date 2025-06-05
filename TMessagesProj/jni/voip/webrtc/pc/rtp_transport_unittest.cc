/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/rtp_transport.h"

#include <utility>

#include "p2p/base/fake_packet_transport.h"
#include "pc/test/rtp_transport_test_util.h"
#include "rtc_base/buffer.h"
#include "rtc_base/containers/flat_set.h"
#include "rtc_base/gunit.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "test/gtest.h"
#include "test/run_loop.h"

namespace webrtc {

constexpr bool kMuxDisabled = false;
constexpr bool kMuxEnabled = true;
constexpr uint16_t kLocalNetId = 1;
constexpr uint16_t kRemoteNetId = 2;
constexpr int kLastPacketId = 100;
constexpr int kTransportOverheadPerPacket = 28;  // Ipv4(20) + UDP(8).

class SignalObserver : public sigslot::has_slots<> {
 public:
  explicit SignalObserver(RtpTransport* transport) {
    transport_ = transport;
    transport->SubscribeReadyToSend(
        this, [this](bool ready) { OnReadyToSend(ready); });
    transport->SubscribeNetworkRouteChanged(
        this, [this](absl::optional<rtc::NetworkRoute> route) {
          OnNetworkRouteChanged(route);
        });
    if (transport->rtp_packet_transport()) {
      transport->rtp_packet_transport()->SignalSentPacket.connect(
          this, &SignalObserver::OnSentPacket);
    }

    if (transport->rtcp_packet_transport()) {
      transport->rtcp_packet_transport()->SignalSentPacket.connect(
          this, &SignalObserver::OnSentPacket);
    }
  }

  bool ready() const { return ready_; }
  void OnReadyToSend(bool ready) { ready_ = ready; }

  absl::optional<rtc::NetworkRoute> network_route() { return network_route_; }
  void OnNetworkRouteChanged(absl::optional<rtc::NetworkRoute> network_route) {
    network_route_ = network_route;
  }

  void OnSentPacket(rtc::PacketTransportInternal* packet_transport,
                    const rtc::SentPacket& sent_packet) {
    if (packet_transport == transport_->rtp_packet_transport()) {
      rtp_transport_sent_count_++;
    } else {
      ASSERT_EQ(transport_->rtcp_packet_transport(), packet_transport);
      rtcp_transport_sent_count_++;
    }
  }

  int rtp_transport_sent_count() { return rtp_transport_sent_count_; }

  int rtcp_transport_sent_count() { return rtcp_transport_sent_count_; }

 private:
  int rtp_transport_sent_count_ = 0;
  int rtcp_transport_sent_count_ = 0;
  RtpTransport* transport_ = nullptr;
  bool ready_ = false;
  absl::optional<rtc::NetworkRoute> network_route_;
};

TEST(RtpTransportTest, SettingRtcpAndRtpSignalsReady) {
  RtpTransport transport(kMuxDisabled);
  SignalObserver observer(&transport);
  rtc::FakePacketTransport fake_rtcp("fake_rtcp");
  fake_rtcp.SetWritable(true);
  rtc::FakePacketTransport fake_rtp("fake_rtp");
  fake_rtp.SetWritable(true);

  transport.SetRtcpPacketTransport(&fake_rtcp);  // rtcp ready
  EXPECT_FALSE(observer.ready());
  transport.SetRtpPacketTransport(&fake_rtp);  // rtp ready
  EXPECT_TRUE(observer.ready());
}

TEST(RtpTransportTest, SettingRtpAndRtcpSignalsReady) {
  RtpTransport transport(kMuxDisabled);
  SignalObserver observer(&transport);
  rtc::FakePacketTransport fake_rtcp("fake_rtcp");
  fake_rtcp.SetWritable(true);
  rtc::FakePacketTransport fake_rtp("fake_rtp");
  fake_rtp.SetWritable(true);

  transport.SetRtpPacketTransport(&fake_rtp);  // rtp ready
  EXPECT_FALSE(observer.ready());
  transport.SetRtcpPacketTransport(&fake_rtcp);  // rtcp ready
  EXPECT_TRUE(observer.ready());
}

TEST(RtpTransportTest, SettingRtpWithRtcpMuxEnabledSignalsReady) {
  RtpTransport transport(kMuxEnabled);
  SignalObserver observer(&transport);
  rtc::FakePacketTransport fake_rtp("fake_rtp");
  fake_rtp.SetWritable(true);

  transport.SetRtpPacketTransport(&fake_rtp);  // rtp ready
  EXPECT_TRUE(observer.ready());
}

TEST(RtpTransportTest, DisablingRtcpMuxSignalsNotReady) {
  RtpTransport transport(kMuxEnabled);
  SignalObserver observer(&transport);
  rtc::FakePacketTransport fake_rtp("fake_rtp");
  fake_rtp.SetWritable(true);

  transport.SetRtpPacketTransport(&fake_rtp);  // rtp ready
  EXPECT_TRUE(observer.ready());

  transport.SetRtcpMuxEnabled(false);
  EXPECT_FALSE(observer.ready());
}

TEST(RtpTransportTest, EnablingRtcpMuxSignalsReady) {
  RtpTransport transport(kMuxDisabled);
  SignalObserver observer(&transport);
  rtc::FakePacketTransport fake_rtp("fake_rtp");
  fake_rtp.SetWritable(true);

  transport.SetRtpPacketTransport(&fake_rtp);  // rtp ready
  EXPECT_FALSE(observer.ready());

  transport.SetRtcpMuxEnabled(true);
  EXPECT_TRUE(observer.ready());
}

// Tests the SignalNetworkRoute is fired when setting a packet transport.
TEST(RtpTransportTest, SetRtpTransportWithNetworkRouteChanged) {
  RtpTransport transport(kMuxDisabled);
  SignalObserver observer(&transport);
  rtc::FakePacketTransport fake_rtp("fake_rtp");

  EXPECT_FALSE(observer.network_route());

  rtc::NetworkRoute network_route;
  // Set a non-null RTP transport with a new network route.
  network_route.connected = true;
  network_route.local = rtc::RouteEndpoint::CreateWithNetworkId(kLocalNetId);
  network_route.remote = rtc::RouteEndpoint::CreateWithNetworkId(kRemoteNetId);
  network_route.last_sent_packet_id = kLastPacketId;
  network_route.packet_overhead = kTransportOverheadPerPacket;
  fake_rtp.SetNetworkRoute(absl::optional<rtc::NetworkRoute>(network_route));
  transport.SetRtpPacketTransport(&fake_rtp);
  ASSERT_TRUE(observer.network_route());
  EXPECT_TRUE(observer.network_route()->connected);
  EXPECT_EQ(kLocalNetId, observer.network_route()->local.network_id());
  EXPECT_EQ(kRemoteNetId, observer.network_route()->remote.network_id());
  EXPECT_EQ(kTransportOverheadPerPacket,
            observer.network_route()->packet_overhead);
  EXPECT_EQ(kLastPacketId, observer.network_route()->last_sent_packet_id);

  // Set a null RTP transport.
  transport.SetRtpPacketTransport(nullptr);
  EXPECT_FALSE(observer.network_route());
}

TEST(RtpTransportTest, SetRtcpTransportWithNetworkRouteChanged) {
  RtpTransport transport(kMuxDisabled);
  SignalObserver observer(&transport);
  rtc::FakePacketTransport fake_rtcp("fake_rtcp");

  EXPECT_FALSE(observer.network_route());

  rtc::NetworkRoute network_route;
  // Set a non-null RTCP transport with a new network route.
  network_route.connected = true;
  network_route.local = rtc::RouteEndpoint::CreateWithNetworkId(kLocalNetId);
  network_route.remote = rtc::RouteEndpoint::CreateWithNetworkId(kRemoteNetId);
  network_route.last_sent_packet_id = kLastPacketId;
  network_route.packet_overhead = kTransportOverheadPerPacket;
  fake_rtcp.SetNetworkRoute(absl::optional<rtc::NetworkRoute>(network_route));
  transport.SetRtcpPacketTransport(&fake_rtcp);
  ASSERT_TRUE(observer.network_route());
  EXPECT_TRUE(observer.network_route()->connected);
  EXPECT_EQ(kLocalNetId, observer.network_route()->local.network_id());
  EXPECT_EQ(kRemoteNetId, observer.network_route()->remote.network_id());
  EXPECT_EQ(kTransportOverheadPerPacket,
            observer.network_route()->packet_overhead);
  EXPECT_EQ(kLastPacketId, observer.network_route()->last_sent_packet_id);

  // Set a null RTCP transport.
  transport.SetRtcpPacketTransport(nullptr);
  EXPECT_FALSE(observer.network_route());
}

// Test that RTCP packets are sent over correct transport based on the RTCP-mux
// status.
TEST(RtpTransportTest, RtcpPacketSentOverCorrectTransport) {
  // If the RTCP-mux is not enabled, RTCP packets are expected to be sent over
  // the RtcpPacketTransport.
  RtpTransport transport(kMuxDisabled);
  rtc::FakePacketTransport fake_rtcp("fake_rtcp");
  rtc::FakePacketTransport fake_rtp("fake_rtp");
  transport.SetRtcpPacketTransport(&fake_rtcp);  // rtcp ready
  transport.SetRtpPacketTransport(&fake_rtp);    // rtp ready
  SignalObserver observer(&transport);

  fake_rtp.SetDestination(&fake_rtp, true);
  fake_rtcp.SetDestination(&fake_rtcp, true);

  rtc::CopyOnWriteBuffer packet;
  EXPECT_TRUE(transport.SendRtcpPacket(&packet, rtc::PacketOptions(), 0));
  EXPECT_EQ(1, observer.rtcp_transport_sent_count());

  // The RTCP packets are expected to be sent over RtpPacketTransport if
  // RTCP-mux is enabled.
  transport.SetRtcpMuxEnabled(true);
  EXPECT_TRUE(transport.SendRtcpPacket(&packet, rtc::PacketOptions(), 0));
  EXPECT_EQ(1, observer.rtp_transport_sent_count());
}

TEST(RtpTransportTest, ChangingReadyToSendStateOnlySignalsWhenChanged) {
  RtpTransport transport(kMuxEnabled);
  TransportObserver observer(&transport);
  rtc::FakePacketTransport fake_rtp("fake_rtp");
  fake_rtp.SetWritable(true);

  // State changes, so we should signal.
  transport.SetRtpPacketTransport(&fake_rtp);
  EXPECT_EQ(observer.ready_to_send_signal_count(), 1);

  // State does not change, so we should not signal.
  transport.SetRtpPacketTransport(&fake_rtp);
  EXPECT_EQ(observer.ready_to_send_signal_count(), 1);

  // State does not change, so we should not signal.
  transport.SetRtcpMuxEnabled(true);
  EXPECT_EQ(observer.ready_to_send_signal_count(), 1);

  // State changes, so we should signal.
  transport.SetRtcpMuxEnabled(false);
  EXPECT_EQ(observer.ready_to_send_signal_count(), 2);
}

// Test that SignalPacketReceived fires with rtcp=true when a RTCP packet is
// received.
TEST(RtpTransportTest, SignalDemuxedRtcp) {
  RtpTransport transport(kMuxDisabled);
  rtc::FakePacketTransport fake_rtp("fake_rtp");
  fake_rtp.SetDestination(&fake_rtp, true);
  transport.SetRtpPacketTransport(&fake_rtp);
  TransportObserver observer(&transport);

  // An rtcp packet.
  const unsigned char data[] = {0x80, 73, 0, 0};
  const int len = 4;
  const rtc::PacketOptions options;
  const int flags = 0;
  fake_rtp.SendPacket(reinterpret_cast<const char*>(data), len, options, flags);
  EXPECT_EQ(0, observer.rtp_count());
  EXPECT_EQ(1, observer.rtcp_count());
}

static const unsigned char kRtpData[] = {0x80, 0x11, 0, 0, 0, 0,
                                         0,    0,    0, 0, 0, 0};
static const int kRtpLen = 12;

// Test that SignalPacketReceived fires with rtcp=false when a RTP packet with a
// handled payload type is received.
TEST(RtpTransportTest, SignalHandledRtpPayloadType) {
  RtpTransport transport(kMuxDisabled);
  rtc::FakePacketTransport fake_rtp("fake_rtp");
  fake_rtp.SetDestination(&fake_rtp, true);
  transport.SetRtpPacketTransport(&fake_rtp);
  TransportObserver observer(&transport);
  RtpDemuxerCriteria demuxer_criteria;
  // Add a handled payload type.
  demuxer_criteria.payload_types().insert(0x11);
  transport.RegisterRtpDemuxerSink(demuxer_criteria, &observer);

  // An rtp packet.
  const rtc::PacketOptions options;
  const int flags = 0;
  rtc::Buffer rtp_data(kRtpData, kRtpLen);
  fake_rtp.SendPacket(rtp_data.data<char>(), kRtpLen, options, flags);
  EXPECT_EQ(1, observer.rtp_count());
  EXPECT_EQ(0, observer.un_demuxable_rtp_count());
  EXPECT_EQ(0, observer.rtcp_count());
  // Remove the sink before destroying the transport.
  transport.UnregisterRtpDemuxerSink(&observer);
}

// Test that SignalPacketReceived does not fire when a RTP packet with an
// unhandled payload type is received.
TEST(RtpTransportTest, DontSignalUnhandledRtpPayloadType) {
  RtpTransport transport(kMuxDisabled);
  rtc::FakePacketTransport fake_rtp("fake_rtp");
  fake_rtp.SetDestination(&fake_rtp, true);
  transport.SetRtpPacketTransport(&fake_rtp);
  TransportObserver observer(&transport);
  RtpDemuxerCriteria demuxer_criteria;
  // Add an unhandled payload type.
  demuxer_criteria.payload_types().insert(0x12);
  transport.RegisterRtpDemuxerSink(demuxer_criteria, &observer);

  const rtc::PacketOptions options;
  const int flags = 0;
  rtc::Buffer rtp_data(kRtpData, kRtpLen);
  fake_rtp.SendPacket(rtp_data.data<char>(), kRtpLen, options, flags);
  EXPECT_EQ(0, observer.rtp_count());
  EXPECT_EQ(1, observer.un_demuxable_rtp_count());
  EXPECT_EQ(0, observer.rtcp_count());
  // Remove the sink before destroying the transport.
  transport.UnregisterRtpDemuxerSink(&observer);
}

TEST(RtpTransportTest, RecursiveSetSendDoesNotCrash) {
  const int kShortTimeout = 100;
  test::RunLoop loop;
  RtpTransport transport(kMuxEnabled);
  rtc::FakePacketTransport fake_rtp("fake_rtp");
  transport.SetRtpPacketTransport(&fake_rtp);
  TransportObserver observer(&transport);
  observer.SetActionOnReadyToSend([&](bool ready) {
    const rtc::PacketOptions options;
    const int flags = 0;
    rtc::CopyOnWriteBuffer rtp_data(kRtpData, kRtpLen);
    transport.SendRtpPacket(&rtp_data, options, flags);
  });
  // The fake RTP will have no destination, so will return -1.
  fake_rtp.SetError(ENOTCONN);
  fake_rtp.SetWritable(true);
  // At this point, only the initial ready-to-send is observed.
  EXPECT_TRUE(observer.ready_to_send());
  EXPECT_EQ(observer.ready_to_send_signal_count(), 1);
  // After the wait, the ready-to-send false is observed.
  EXPECT_EQ_WAIT(observer.ready_to_send_signal_count(), 2, kShortTimeout);
  EXPECT_FALSE(observer.ready_to_send());
}

TEST(RtpTransportTest, RecursiveOnSentPacketDoesNotCrash) {
  const int kShortTimeout = 100;
  test::RunLoop loop;
  RtpTransport transport(kMuxEnabled);
  rtc::FakePacketTransport fake_rtp("fake_rtp");
  transport.SetRtpPacketTransport(&fake_rtp);
  fake_rtp.SetDestination(&fake_rtp, true);
  TransportObserver observer(&transport);
  const rtc::PacketOptions options;
  const int flags = 0;

  fake_rtp.SetWritable(true);
  observer.SetActionOnSentPacket([&]() {
    rtc::CopyOnWriteBuffer rtp_data(kRtpData, kRtpLen);
    if (observer.sent_packet_count() < 2) {
      transport.SendRtpPacket(&rtp_data, options, flags);
    }
  });
  rtc::CopyOnWriteBuffer rtp_data(kRtpData, kRtpLen);
  transport.SendRtpPacket(&rtp_data, options, flags);
  EXPECT_EQ(observer.sent_packet_count(), 1);
  EXPECT_EQ_WAIT(observer.sent_packet_count(), 2, kShortTimeout);
}

}  // namespace webrtc

/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/socket/dcsctp_socket.h"

#include <algorithm>
#include <cstdint>
#include <deque>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/flags/flag.h"
#include "absl/memory/memory.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/common/handover_testing.h"
#include "net/dcsctp/common/math.h"
#include "net/dcsctp/packet/chunk/abort_chunk.h"
#include "net/dcsctp/packet/chunk/chunk.h"
#include "net/dcsctp/packet/chunk/cookie_ack_chunk.h"
#include "net/dcsctp/packet/chunk/cookie_echo_chunk.h"
#include "net/dcsctp/packet/chunk/data_chunk.h"
#include "net/dcsctp/packet/chunk/data_common.h"
#include "net/dcsctp/packet/chunk/error_chunk.h"
#include "net/dcsctp/packet/chunk/forward_tsn_chunk.h"
#include "net/dcsctp/packet/chunk/heartbeat_ack_chunk.h"
#include "net/dcsctp/packet/chunk/heartbeat_request_chunk.h"
#include "net/dcsctp/packet/chunk/idata_chunk.h"
#include "net/dcsctp/packet/chunk/init_ack_chunk.h"
#include "net/dcsctp/packet/chunk/init_chunk.h"
#include "net/dcsctp/packet/chunk/reconfig_chunk.h"
#include "net/dcsctp/packet/chunk/sack_chunk.h"
#include "net/dcsctp/packet/chunk/shutdown_chunk.h"
#include "net/dcsctp/packet/error_cause/error_cause.h"
#include "net/dcsctp/packet/error_cause/unrecognized_chunk_type_cause.h"
#include "net/dcsctp/packet/parameter/heartbeat_info_parameter.h"
#include "net/dcsctp/packet/parameter/outgoing_ssn_reset_request_parameter.h"
#include "net/dcsctp/packet/parameter/parameter.h"
#include "net/dcsctp/packet/parameter/reconfiguration_response_parameter.h"
#include "net/dcsctp/packet/sctp_packet.h"
#include "net/dcsctp/packet/tlv_trait.h"
#include "net/dcsctp/public/dcsctp_message.h"
#include "net/dcsctp/public/dcsctp_options.h"
#include "net/dcsctp/public/dcsctp_socket.h"
#include "net/dcsctp/public/text_pcap_packet_observer.h"
#include "net/dcsctp/public/types.h"
#include "net/dcsctp/rx/reassembly_queue.h"
#include "net/dcsctp/socket/mock_dcsctp_socket_callbacks.h"
#include "net/dcsctp/testing/testing_macros.h"
#include "rtc_base/gunit.h"
#include "test/gmock.h"

ABSL_FLAG(bool, dcsctp_capture_packets, false, "Print packet capture.");

namespace dcsctp {
namespace {
using ::testing::_;
using ::testing::AllOf;
using ::testing::ElementsAre;
using ::testing::ElementsAreArray;
using ::testing::Eq;
using ::testing::HasSubstr;
using ::testing::IsEmpty;
using ::testing::Not;
using ::testing::Property;
using ::testing::SizeIs;
using ::testing::UnorderedElementsAre;
using ::webrtc::TimeDelta;
using ::webrtc::Timestamp;

constexpr SendOptions kSendOptions;
constexpr size_t kLargeMessageSize = DcSctpOptions::kMaxSafeMTUSize * 20;
constexpr size_t kSmallMessageSize = 10;
constexpr int kMaxBurstPackets = 4;
constexpr DcSctpOptions kDefaultOptions;

MATCHER_P(HasChunks, chunks, "") {
  absl::optional<SctpPacket> packet = SctpPacket::Parse(arg, kDefaultOptions);
  if (!packet.has_value()) {
    *result_listener << "data didn't parse as an SctpPacket";
    return false;
  }

  return ExplainMatchResult(chunks, packet->descriptors(), result_listener);
}

MATCHER_P(IsChunkType, chunk_type, "") {
  return ExplainMatchResult(chunk_type, arg.type, result_listener);
}

MATCHER_P(IsDataChunk, properties, "") {
  if (arg.type != DataChunk::kType) {
    *result_listener << "the chunk is not a data chunk";
    return false;
  }

  absl::optional<DataChunk> chunk = DataChunk::Parse(arg.data);
  if (!chunk.has_value()) {
    *result_listener << "The chunk didn't parse as a data chunk";
    return false;
  }

  return ExplainMatchResult(properties, *chunk, result_listener);
}

MATCHER_P(IsSack, properties, "") {
  if (arg.type != SackChunk::kType) {
    *result_listener << "the chunk is not a sack chunk";
    return false;
  }

  absl::optional<SackChunk> chunk = SackChunk::Parse(arg.data);
  if (!chunk.has_value()) {
    *result_listener << "The chunk didn't parse as a sack chunk";
    return false;
  }

  return ExplainMatchResult(properties, *chunk, result_listener);
}

MATCHER_P(IsReConfig, properties, "") {
  if (arg.type != ReConfigChunk::kType) {
    *result_listener << "the chunk is not a re-config chunk";
    return false;
  }

  absl::optional<ReConfigChunk> chunk = ReConfigChunk::Parse(arg.data);
  if (!chunk.has_value()) {
    *result_listener << "The chunk didn't parse as a re-config chunk";
    return false;
  }

  return ExplainMatchResult(properties, *chunk, result_listener);
}

MATCHER_P(IsHeartbeatAck, properties, "") {
  if (arg.type != HeartbeatAckChunk::kType) {
    *result_listener << "the chunk is not a HeartbeatAckChunk";
    return false;
  }

  absl::optional<HeartbeatAckChunk> chunk = HeartbeatAckChunk::Parse(arg.data);
  if (!chunk.has_value()) {
    *result_listener << "The chunk didn't parse as a HeartbeatAckChunk";
    return false;
  }

  return ExplainMatchResult(properties, *chunk, result_listener);
}

MATCHER_P(IsHeartbeatRequest, properties, "") {
  if (arg.type != HeartbeatRequestChunk::kType) {
    *result_listener << "the chunk is not a HeartbeatRequestChunk";
    return false;
  }

  absl::optional<HeartbeatRequestChunk> chunk =
      HeartbeatRequestChunk::Parse(arg.data);
  if (!chunk.has_value()) {
    *result_listener << "The chunk didn't parse as a HeartbeatRequestChunk";
    return false;
  }

  return ExplainMatchResult(properties, *chunk, result_listener);
}

MATCHER_P(HasParameters, parameters, "") {
  return ExplainMatchResult(parameters, arg.parameters().descriptors(),
                            result_listener);
}

MATCHER_P(IsOutgoingResetRequest, properties, "") {
  if (arg.type != OutgoingSSNResetRequestParameter::kType) {
    *result_listener
        << "the parameter is not an OutgoingSSNResetRequestParameter";
    return false;
  }

  absl::optional<OutgoingSSNResetRequestParameter> parameter =
      OutgoingSSNResetRequestParameter::Parse(arg.data);
  if (!parameter.has_value()) {
    *result_listener
        << "The parameter didn't parse as an OutgoingSSNResetRequestParameter";
    return false;
  }

  return ExplainMatchResult(properties, *parameter, result_listener);
}

MATCHER_P(IsReconfigurationResponse, properties, "") {
  if (arg.type != ReconfigurationResponseParameter::kType) {
    *result_listener
        << "the parameter is not an ReconfigurationResponseParameter";
    return false;
  }

  absl::optional<ReconfigurationResponseParameter> parameter =
      ReconfigurationResponseParameter::Parse(arg.data);
  if (!parameter.has_value()) {
    *result_listener
        << "The parameter didn't parse as an ReconfigurationResponseParameter";
    return false;
  }

  return ExplainMatchResult(properties, *parameter, result_listener);
}

TSN AddTo(TSN tsn, int delta) {
  return TSN(*tsn + delta);
}

DcSctpOptions FixupOptions(DcSctpOptions options = {}) {
  DcSctpOptions fixup = options;
  // To make the interval more predictable in tests.
  fixup.heartbeat_interval_include_rtt = false;
  fixup.max_burst = kMaxBurstPackets;
  return fixup;
}

std::unique_ptr<PacketObserver> GetPacketObserver(absl::string_view name) {
  if (absl::GetFlag(FLAGS_dcsctp_capture_packets)) {
    return std::make_unique<TextPcapPacketObserver>(name);
  }
  return nullptr;
}

struct SocketUnderTest {
  explicit SocketUnderTest(absl::string_view name,
                           const DcSctpOptions& opts = kDefaultOptions)
      : options(FixupOptions(opts)),
        cb(name),
        socket(name, cb, GetPacketObserver(name), options) {}

  const DcSctpOptions options;
  testing::NiceMock<MockDcSctpSocketCallbacks> cb;
  DcSctpSocket socket;
};

void ExchangeMessages(SocketUnderTest& a, SocketUnderTest& z) {
  bool delivered_packet = false;
  do {
    delivered_packet = false;
    std::vector<uint8_t> packet_from_a = a.cb.ConsumeSentPacket();
    if (!packet_from_a.empty()) {
      delivered_packet = true;
      z.socket.ReceivePacket(std::move(packet_from_a));
    }
    std::vector<uint8_t> packet_from_z = z.cb.ConsumeSentPacket();
    if (!packet_from_z.empty()) {
      delivered_packet = true;
      a.socket.ReceivePacket(std::move(packet_from_z));
    }
  } while (delivered_packet);
}

void RunTimers(SocketUnderTest& s) {
  for (;;) {
    absl::optional<TimeoutID> timeout_id = s.cb.GetNextExpiredTimeout();
    if (!timeout_id.has_value()) {
      break;
    }
    s.socket.HandleTimeout(*timeout_id);
  }
}

void AdvanceTime(SocketUnderTest& a, SocketUnderTest& z, TimeDelta duration) {
  a.cb.AdvanceTime(duration);
  z.cb.AdvanceTime(duration);

  RunTimers(a);
  RunTimers(z);
}

// Exchanges messages between `a` and `z`, advancing time until there are no
// more pending timers, or until `max_timeout` is reached.
void ExchangeMessagesAndAdvanceTime(
    SocketUnderTest& a,
    SocketUnderTest& z,
    TimeDelta max_timeout = TimeDelta::Seconds(10)) {
  Timestamp time_started = a.cb.Now();
  while (a.cb.Now() - time_started < max_timeout) {
    ExchangeMessages(a, z);

    TimeDelta time_to_next_timeout =
        std::min(a.cb.GetTimeToNextTimeout(), z.cb.GetTimeToNextTimeout());
    if (time_to_next_timeout.IsPlusInfinity()) {
      // No more pending timer.
      return;
    }
    AdvanceTime(a, z, time_to_next_timeout);
  }
}

// Calls Connect() on `sock_a_` and make the connection established.
void ConnectSockets(SocketUnderTest& a, SocketUnderTest& z) {
  EXPECT_CALL(a.cb, OnConnected).Times(1);
  EXPECT_CALL(z.cb, OnConnected).Times(1);

  a.socket.Connect();
  // Z reads INIT, INIT_ACK, COOKIE_ECHO, COOKIE_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());

  EXPECT_EQ(a.socket.state(), SocketState::kConnected);
  EXPECT_EQ(z.socket.state(), SocketState::kConnected);
}

std::unique_ptr<SocketUnderTest> HandoverSocket(
    std::unique_ptr<SocketUnderTest> sut) {
  EXPECT_EQ(sut->socket.GetHandoverReadiness(), HandoverReadinessStatus());

  bool is_closed = sut->socket.state() == SocketState::kClosed;
  if (!is_closed) {
    EXPECT_CALL(sut->cb, OnClosed).Times(1);
  }
  absl::optional<DcSctpSocketHandoverState> handover_state =
      sut->socket.GetHandoverStateAndClose();
  EXPECT_TRUE(handover_state.has_value());
  g_handover_state_transformer_for_test(&*handover_state);

  auto handover_socket = std::make_unique<SocketUnderTest>("H", sut->options);
  if (!is_closed) {
    EXPECT_CALL(handover_socket->cb, OnConnected).Times(1);
  }
  handover_socket->socket.RestoreFromState(*handover_state);
  return handover_socket;
}

std::vector<uint32_t> GetReceivedMessagePpids(SocketUnderTest& z) {
  std::vector<uint32_t> ppids;
  for (;;) {
    absl::optional<DcSctpMessage> msg = z.cb.ConsumeReceivedMessage();
    if (!msg.has_value()) {
      break;
    }
    ppids.push_back(*msg->ppid());
  }
  return ppids;
}

// Test parameter that controls whether to perform handovers during the test. A
// test can have multiple points where it conditionally hands over socket Z.
// Either socket Z will be handed over at all those points or handed over never.
enum class HandoverMode {
  kNoHandover,
  kPerformHandovers,
};

class DcSctpSocketParametrizedTest
    : public ::testing::Test,
      public ::testing::WithParamInterface<HandoverMode> {
 protected:
  // Trigger handover for `sut` depending on the current test param.
  std::unique_ptr<SocketUnderTest> MaybeHandoverSocket(
      std::unique_ptr<SocketUnderTest> sut) {
    if (GetParam() == HandoverMode::kPerformHandovers) {
      return HandoverSocket(std::move(sut));
    }
    return sut;
  }

  // Trigger handover for socket Z depending on the current test param.
  // Then checks message passing to verify the handed over socket is functional.
  void MaybeHandoverSocketAndSendMessage(SocketUnderTest& a,
                                         std::unique_ptr<SocketUnderTest> z) {
    if (GetParam() == HandoverMode::kPerformHandovers) {
      z = HandoverSocket(std::move(z));
    }

    ExchangeMessages(a, *z);
    a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {1, 2}), kSendOptions);
    ExchangeMessages(a, *z);

    absl::optional<DcSctpMessage> msg = z->cb.ConsumeReceivedMessage();
    ASSERT_TRUE(msg.has_value());
    EXPECT_EQ(msg->stream_id(), StreamID(1));
  }
};

INSTANTIATE_TEST_SUITE_P(Handovers,
                         DcSctpSocketParametrizedTest,
                         testing::Values(HandoverMode::kNoHandover,
                                         HandoverMode::kPerformHandovers),
                         [](const auto& test_info) {
                           return test_info.param ==
                                          HandoverMode::kPerformHandovers
                                      ? "WithHandovers"
                                      : "NoHandover";
                         });

TEST(DcSctpSocketTest, EstablishConnection) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  EXPECT_CALL(a.cb, OnConnected).Times(1);
  EXPECT_CALL(z.cb, OnConnected).Times(1);
  EXPECT_CALL(a.cb, OnConnectionRestarted).Times(0);
  EXPECT_CALL(z.cb, OnConnectionRestarted).Times(0);

  a.socket.Connect();
  // Z reads INIT, produces INIT_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // A reads INIT_ACK, produces COOKIE_ECHO
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());
  // Z reads COOKIE_ECHO, produces COOKIE_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // A reads COOKIE_ACK.
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());

  EXPECT_EQ(a.socket.state(), SocketState::kConnected);
  EXPECT_EQ(z.socket.state(), SocketState::kConnected);
}

TEST(DcSctpSocketTest, EstablishConnectionWithSetupCollision) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  EXPECT_CALL(a.cb, OnConnected).Times(1);
  EXPECT_CALL(z.cb, OnConnected).Times(1);
  EXPECT_CALL(a.cb, OnConnectionRestarted).Times(0);
  EXPECT_CALL(z.cb, OnConnectionRestarted).Times(0);
  a.socket.Connect();
  z.socket.Connect();

  ExchangeMessages(a, z);

  EXPECT_EQ(a.socket.state(), SocketState::kConnected);
  EXPECT_EQ(z.socket.state(), SocketState::kConnected);
}

TEST(DcSctpSocketTest, ShuttingDownWhileEstablishingConnection) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  EXPECT_CALL(a.cb, OnConnected).Times(0);
  EXPECT_CALL(z.cb, OnConnected).Times(1);
  a.socket.Connect();

  // Z reads INIT, produces INIT_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // A reads INIT_ACK, produces COOKIE_ECHO
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());
  // Z reads COOKIE_ECHO, produces COOKIE_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // Drop COOKIE_ACK, just to more easily verify shutdown protocol.
  z.cb.ConsumeSentPacket();

  // As Socket A has received INIT_ACK, it has a TCB and is connected, while
  // Socket Z needs to receive COOKIE_ECHO to get there. Socket A still has
  // timers running at this point.
  EXPECT_EQ(a.socket.state(), SocketState::kConnecting);
  EXPECT_EQ(z.socket.state(), SocketState::kConnected);

  // Socket A is now shut down, which should make it stop those timers.
  a.socket.Shutdown();

  EXPECT_CALL(a.cb, OnClosed).Times(1);
  EXPECT_CALL(z.cb, OnClosed).Times(1);

  // Z reads SHUTDOWN, produces SHUTDOWN_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // A reads SHUTDOWN_ACK, produces SHUTDOWN_COMPLETE
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());
  // Z reads SHUTDOWN_COMPLETE.
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());

  EXPECT_TRUE(a.cb.ConsumeSentPacket().empty());
  EXPECT_TRUE(z.cb.ConsumeSentPacket().empty());

  EXPECT_EQ(a.socket.state(), SocketState::kClosed);
  EXPECT_EQ(z.socket.state(), SocketState::kClosed);
}

TEST(DcSctpSocketTest, EstablishSimultaneousConnection) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  EXPECT_CALL(a.cb, OnConnected).Times(1);
  EXPECT_CALL(z.cb, OnConnected).Times(1);
  EXPECT_CALL(a.cb, OnConnectionRestarted).Times(0);
  EXPECT_CALL(z.cb, OnConnectionRestarted).Times(0);
  a.socket.Connect();

  // INIT isn't received by Z, as it wasn't ready yet.
  a.cb.ConsumeSentPacket();

  z.socket.Connect();

  // A reads INIT, produces INIT_ACK
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());

  // Z reads INIT_ACK, sends COOKIE_ECHO
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());

  // A reads COOKIE_ECHO - establishes connection.
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());

  EXPECT_EQ(a.socket.state(), SocketState::kConnected);

  // Proceed with the remaining packets.
  ExchangeMessages(a, z);

  EXPECT_EQ(a.socket.state(), SocketState::kConnected);
  EXPECT_EQ(z.socket.state(), SocketState::kConnected);
}

TEST(DcSctpSocketTest, EstablishConnectionLostCookieAck) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  EXPECT_CALL(a.cb, OnConnected).Times(1);
  EXPECT_CALL(z.cb, OnConnected).Times(1);
  EXPECT_CALL(a.cb, OnConnectionRestarted).Times(0);
  EXPECT_CALL(z.cb, OnConnectionRestarted).Times(0);

  a.socket.Connect();
  // Z reads INIT, produces INIT_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // A reads INIT_ACK, produces COOKIE_ECHO
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());
  // Z reads COOKIE_ECHO, produces COOKIE_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // COOKIE_ACK is lost.
  z.cb.ConsumeSentPacket();

  EXPECT_EQ(a.socket.state(), SocketState::kConnecting);
  EXPECT_EQ(z.socket.state(), SocketState::kConnected);

  // This will make A re-send the COOKIE_ECHO
  AdvanceTime(a, z, a.options.t1_cookie_timeout.ToTimeDelta());

  // Z reads COOKIE_ECHO, produces COOKIE_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // A reads COOKIE_ACK.
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());

  EXPECT_EQ(a.socket.state(), SocketState::kConnected);
  EXPECT_EQ(z.socket.state(), SocketState::kConnected);
}

TEST(DcSctpSocketTest, ResendInitAndEstablishConnection) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  a.socket.Connect();
  // INIT is never received by Z.
  EXPECT_THAT(a.cb.ConsumeSentPacket(),
              HasChunks(ElementsAre(IsChunkType(InitChunk::kType))));

  AdvanceTime(a, z, a.options.t1_init_timeout.ToTimeDelta());

  // Z reads INIT, produces INIT_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // A reads INIT_ACK, produces COOKIE_ECHO
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());
  // Z reads COOKIE_ECHO, produces COOKIE_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // A reads COOKIE_ACK.
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());

  EXPECT_EQ(a.socket.state(), SocketState::kConnected);
  EXPECT_EQ(z.socket.state(), SocketState::kConnected);
}

TEST(DcSctpSocketTest, ResendingInitTooManyTimesAborts) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  a.socket.Connect();

  // INIT is never received by Z.
  EXPECT_THAT(a.cb.ConsumeSentPacket(),
              HasChunks(ElementsAre(IsChunkType(InitChunk::kType))));

  for (int i = 0; i < *a.options.max_init_retransmits; ++i) {
    AdvanceTime(a, z, a.options.t1_init_timeout.ToTimeDelta() * (1 << i));

    // INIT is resent
    EXPECT_THAT(a.cb.ConsumeSentPacket(),
                HasChunks(ElementsAre(IsChunkType(InitChunk::kType))));
  }

  // Another timeout, after the max init retransmits.
  EXPECT_CALL(a.cb, OnAborted).Times(1);
  AdvanceTime(a, z,
              a.options.t1_init_timeout.ToTimeDelta() *
                  (1 << *a.options.max_init_retransmits));

  EXPECT_EQ(a.socket.state(), SocketState::kClosed);
}

TEST(DcSctpSocketTest, ResendCookieEchoAndEstablishConnection) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  a.socket.Connect();

  // Z reads INIT, produces INIT_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // A reads INIT_ACK, produces COOKIE_ECHO
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());

  // COOKIE_ECHO is never received by Z.
  EXPECT_THAT(a.cb.ConsumeSentPacket(),
              HasChunks(ElementsAre(IsChunkType(CookieEchoChunk::kType))));

  AdvanceTime(a, z, a.options.t1_init_timeout.ToTimeDelta());

  // Z reads COOKIE_ECHO, produces COOKIE_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // A reads COOKIE_ACK.
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());

  EXPECT_EQ(a.socket.state(), SocketState::kConnected);
  EXPECT_EQ(z.socket.state(), SocketState::kConnected);
}

TEST(DcSctpSocketTest, ResendingCookieEchoTooManyTimesAborts) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  a.socket.Connect();

  // Z reads INIT, produces INIT_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // A reads INIT_ACK, produces COOKIE_ECHO
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());

  // COOKIE_ECHO is never received by Z.
  EXPECT_THAT(a.cb.ConsumeSentPacket(),
              HasChunks(ElementsAre(IsChunkType(CookieEchoChunk::kType))));

  for (int i = 0; i < *a.options.max_init_retransmits; ++i) {
    AdvanceTime(a, z, a.options.t1_cookie_timeout.ToTimeDelta() * (1 << i));

    // COOKIE_ECHO is resent
    EXPECT_THAT(a.cb.ConsumeSentPacket(),
                HasChunks(ElementsAre(IsChunkType(CookieEchoChunk::kType))));
  }

  // Another timeout, after the max init retransmits.
  EXPECT_CALL(a.cb, OnAborted).Times(1);
  AdvanceTime(a, z,
              a.options.t1_cookie_timeout.ToTimeDelta() *
                  (1 << *a.options.max_init_retransmits));

  EXPECT_EQ(a.socket.state(), SocketState::kClosed);
}

TEST(DcSctpSocketTest, DoesntSendMorePacketsUntilCookieAckHasBeenReceived) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53),
                              std::vector<uint8_t>(kLargeMessageSize)),
                kSendOptions);
  a.socket.Connect();

  // Z reads INIT, produces INIT_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // A reads INIT_ACK, produces COOKIE_ECHO
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());

  // COOKIE_ECHO is never received by Z.
  EXPECT_THAT(a.cb.ConsumeSentPacket(),
              HasChunks(ElementsAre(IsChunkType(CookieEchoChunk::kType),
                                    IsDataChunk(_))));

  EXPECT_THAT(a.cb.ConsumeSentPacket(), IsEmpty());

  // There are DATA chunks in the sent packet (that was lost), which means that
  // the T3-RTX timer is running, but as the socket is in kCookieEcho state, it
  // will be T1-COOKIE that drives retransmissions, so when the T3-RTX expires,
  // nothing should be retransmitted.
  ASSERT_TRUE(a.options.rto_initial < a.options.t1_cookie_timeout);
  AdvanceTime(a, z, a.options.rto_initial.ToTimeDelta());
  EXPECT_THAT(a.cb.ConsumeSentPacket(), IsEmpty());

  // When T1-COOKIE expires, both the COOKIE-ECHO and DATA should be present.
  AdvanceTime(a, z,
              a.options.t1_cookie_timeout.ToTimeDelta() -
                  a.options.rto_initial.ToTimeDelta());

  // And this COOKIE-ECHO and DATA is also lost - never received by Z.
  EXPECT_THAT(a.cb.ConsumeSentPacket(),
              HasChunks(ElementsAre(IsChunkType(CookieEchoChunk::kType),
                                    IsDataChunk(_))));

  EXPECT_THAT(a.cb.ConsumeSentPacket(), IsEmpty());

  // COOKIE_ECHO has exponential backoff.
  AdvanceTime(a, z, a.options.t1_cookie_timeout.ToTimeDelta() * 2);

  // Z reads COOKIE_ECHO, produces COOKIE_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // A reads COOKIE_ACK.
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());

  EXPECT_EQ(a.socket.state(), SocketState::kConnected);
  EXPECT_EQ(z.socket.state(), SocketState::kConnected);

  ExchangeMessages(a, z);
  EXPECT_THAT(z.cb.ConsumeReceivedMessage()->payload(),
              SizeIs(kLargeMessageSize));
}

TEST_P(DcSctpSocketParametrizedTest, ShutdownConnection) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  RTC_LOG(LS_INFO) << "Shutting down";

  EXPECT_CALL(z->cb, OnClosed).Times(1);
  a.socket.Shutdown();
  // Z reads SHUTDOWN, produces SHUTDOWN_ACK
  z->socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // A reads SHUTDOWN_ACK, produces SHUTDOWN_COMPLETE
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());
  // Z reads SHUTDOWN_COMPLETE.
  z->socket.ReceivePacket(a.cb.ConsumeSentPacket());

  EXPECT_EQ(a.socket.state(), SocketState::kClosed);
  EXPECT_EQ(z->socket.state(), SocketState::kClosed);

  z = MaybeHandoverSocket(std::move(z));
  EXPECT_EQ(z->socket.state(), SocketState::kClosed);
}

TEST(DcSctpSocketTest, ShutdownTimerExpiresTooManyTimeClosesConnection) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  ConnectSockets(a, z);

  a.socket.Shutdown();
  // Drop first SHUTDOWN packet.
  a.cb.ConsumeSentPacket();

  EXPECT_EQ(a.socket.state(), SocketState::kShuttingDown);

  for (int i = 0; i < *a.options.max_retransmissions; ++i) {
    AdvanceTime(a, z, a.options.rto_initial.ToTimeDelta() * (1 << i));

    // Dropping every shutdown chunk.
    EXPECT_THAT(a.cb.ConsumeSentPacket(),
                HasChunks(ElementsAre(IsChunkType(ShutdownChunk::kType))));
    EXPECT_TRUE(a.cb.ConsumeSentPacket().empty());
  }
  // The last expiry, makes it abort the connection.
  EXPECT_CALL(a.cb, OnAborted).Times(1);
  AdvanceTime(a, z,
              a.options.rto_initial.ToTimeDelta() *
                  (1 << *a.options.max_retransmissions));

  EXPECT_EQ(a.socket.state(), SocketState::kClosed);
  EXPECT_THAT(a.cb.ConsumeSentPacket(),
              HasChunks(ElementsAre(IsChunkType(AbortChunk::kType))));
  EXPECT_TRUE(a.cb.ConsumeSentPacket().empty());
}

TEST(DcSctpSocketTest, EstablishConnectionWhileSendingData) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  a.socket.Connect();

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {1, 2}), kSendOptions);

  // Z reads INIT, produces INIT_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // // A reads INIT_ACK, produces COOKIE_ECHO
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());
  // // Z reads COOKIE_ECHO, produces COOKIE_ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // // A reads COOKIE_ACK.
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());

  EXPECT_EQ(a.socket.state(), SocketState::kConnected);
  EXPECT_EQ(z.socket.state(), SocketState::kConnected);

  absl::optional<DcSctpMessage> msg = z.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg.has_value());
  EXPECT_EQ(msg->stream_id(), StreamID(1));
}

TEST(DcSctpSocketTest, SendMessageAfterEstablished) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  ConnectSockets(a, z);

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {1, 2}), kSendOptions);
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());

  absl::optional<DcSctpMessage> msg = z.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg.has_value());
  EXPECT_EQ(msg->stream_id(), StreamID(1));
}

TEST_P(DcSctpSocketParametrizedTest, TimeoutResendsPacket) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {1, 2}), kSendOptions);
  a.cb.ConsumeSentPacket();

  RTC_LOG(LS_INFO) << "Advancing time";
  AdvanceTime(a, *z, a.options.rto_initial.ToTimeDelta());

  z->socket.ReceivePacket(a.cb.ConsumeSentPacket());

  absl::optional<DcSctpMessage> msg = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg.has_value());
  EXPECT_EQ(msg->stream_id(), StreamID(1));

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest, SendALotOfBytesMissedSecondPacket) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  std::vector<uint8_t> payload(kLargeMessageSize);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), kSendOptions);

  // First DATA
  z->socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // Second DATA (lost)
  a.cb.ConsumeSentPacket();

  // Retransmit and handle the rest
  ExchangeMessages(a, *z);

  absl::optional<DcSctpMessage> msg = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg.has_value());
  EXPECT_EQ(msg->stream_id(), StreamID(1));
  EXPECT_THAT(msg->payload(), testing::ElementsAreArray(payload));

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest, SendingHeartbeatAnswersWithAck) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  // Inject a HEARTBEAT chunk
  SctpPacket::Builder b(a.socket.verification_tag(), DcSctpOptions());
  uint8_t info[] = {1, 2, 3, 4};
  Parameters::Builder params_builder;
  params_builder.Add(HeartbeatInfoParameter(info));
  b.Add(HeartbeatRequestChunk(params_builder.Build()));
  a.socket.ReceivePacket(b.Build());

  // HEARTBEAT_ACK is sent as a reply. Capture it.
  EXPECT_THAT(a.cb.ConsumeSentPacket(),
              HasChunks(ElementsAre(IsHeartbeatAck(
                  Property(&HeartbeatAckChunk::info,
                           Optional(Property(&HeartbeatInfoParameter::info,
                                             ElementsAre(1, 2, 3, 4))))))));

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest, ExpectHeartbeatToBeSent) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  EXPECT_THAT(a.cb.ConsumeSentPacket(), IsEmpty());

  AdvanceTime(a, *z, a.options.heartbeat_interval.ToTimeDelta());

  std::vector<uint8_t> packet = a.cb.ConsumeSentPacket();
  // The info is a single 64-bit number.
  EXPECT_THAT(
      packet,
      HasChunks(ElementsAre(IsHeartbeatRequest(Property(
          &HeartbeatRequestChunk::info,
          Optional(Property(&HeartbeatInfoParameter::info, SizeIs(8))))))));

  // Feed it to Sock-z and expect a HEARTBEAT_ACK that will be propagated back.
  z->socket.ReceivePacket(packet);
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest,
       CloseConnectionAfterTooManyLostHeartbeats) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  EXPECT_CALL(z->cb, OnClosed).Times(1);
  EXPECT_THAT(a.cb.ConsumeSentPacket(), testing::IsEmpty());
  // Force-close socket Z so that it doesn't interfere from now on.
  z->socket.Close();

  DurationMs time_to_next_hearbeat = a.options.heartbeat_interval;

  for (int i = 0; i < *a.options.max_retransmissions; ++i) {
    RTC_LOG(LS_INFO) << "Letting HEARTBEAT interval timer expire - sending...";
    AdvanceTime(a, *z, time_to_next_hearbeat.ToTimeDelta());

    // Dropping every heartbeat.
    ASSERT_HAS_VALUE_AND_ASSIGN(
        SctpPacket hb_packet,
        SctpPacket::Parse(a.cb.ConsumeSentPacket(), z->options));
    EXPECT_EQ(hb_packet.descriptors()[0].type, HeartbeatRequestChunk::kType);

    RTC_LOG(LS_INFO) << "Letting the heartbeat expire.";
    AdvanceTime(a, *z, TimeDelta::Millis(1000));

    time_to_next_hearbeat = a.options.heartbeat_interval - DurationMs(1000);
  }

  RTC_LOG(LS_INFO) << "Letting HEARTBEAT interval timer expire - sending...";
  AdvanceTime(a, *z, time_to_next_hearbeat.ToTimeDelta());

  // Last heartbeat
  EXPECT_THAT(a.cb.ConsumeSentPacket(), Not(IsEmpty()));

  EXPECT_CALL(a.cb, OnAborted).Times(1);
  // Should suffice as exceeding RTO
  AdvanceTime(a, *z, TimeDelta::Millis(1000));

  z = MaybeHandoverSocket(std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest, RecoversAfterASuccessfulAck) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  EXPECT_THAT(a.cb.ConsumeSentPacket(), testing::IsEmpty());
  EXPECT_CALL(z->cb, OnClosed).Times(1);
  // Force-close socket Z so that it doesn't interfere from now on.
  z->socket.Close();

  TimeDelta time_to_next_hearbeat = a.options.heartbeat_interval.ToTimeDelta();

  for (int i = 0; i < *a.options.max_retransmissions; ++i) {
    AdvanceTime(a, *z, time_to_next_hearbeat);

    // Dropping every heartbeat.
    a.cb.ConsumeSentPacket();

    RTC_LOG(LS_INFO) << "Letting the heartbeat expire.";
    AdvanceTime(a, *z, TimeDelta::Seconds(1));

    time_to_next_hearbeat =
        a.options.heartbeat_interval.ToTimeDelta() - TimeDelta::Seconds(1);
  }

  RTC_LOG(LS_INFO) << "Getting the last heartbeat - and acking it";
  AdvanceTime(a, *z, time_to_next_hearbeat);

  std::vector<uint8_t> hb_packet_raw = a.cb.ConsumeSentPacket();
  ASSERT_HAS_VALUE_AND_ASSIGN(SctpPacket hb_packet,
                              SctpPacket::Parse(hb_packet_raw, z->options));
  ASSERT_THAT(hb_packet.descriptors(), SizeIs(1));
  ASSERT_HAS_VALUE_AND_ASSIGN(
      HeartbeatRequestChunk hb,
      HeartbeatRequestChunk::Parse(hb_packet.descriptors()[0].data));

  SctpPacket::Builder b(a.socket.verification_tag(), a.options);
  b.Add(HeartbeatAckChunk(std::move(hb).extract_parameters()));
  a.socket.ReceivePacket(b.Build());

  // Should suffice as exceeding RTO - which will not fire.
  EXPECT_CALL(a.cb, OnAborted).Times(0);
  AdvanceTime(a, *z, TimeDelta::Seconds(1));

  EXPECT_THAT(a.cb.ConsumeSentPacket(), IsEmpty());

  // Verify that we get new heartbeats again.
  RTC_LOG(LS_INFO) << "Expecting a new heartbeat";
  AdvanceTime(a, *z, time_to_next_hearbeat);

  EXPECT_THAT(a.cb.ConsumeSentPacket(),
              HasChunks(ElementsAre(IsHeartbeatRequest(_))));
}

TEST_P(DcSctpSocketParametrizedTest, ResetStream) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {1, 2}), {});
  z->socket.ReceivePacket(a.cb.ConsumeSentPacket());

  absl::optional<DcSctpMessage> msg = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg.has_value());
  EXPECT_EQ(msg->stream_id(), StreamID(1));

  // Handle SACK
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());

  // Reset the outgoing stream. This will directly send a RE-CONFIG.
  a.socket.ResetStreams(std::vector<StreamID>({StreamID(1)}));

  // Receiving the packet will trigger a callback, indicating that A has
  // reset its stream. It will also send a RE-CONFIG with a response.
  EXPECT_CALL(z->cb, OnIncomingStreamsReset).Times(1);
  z->socket.ReceivePacket(a.cb.ConsumeSentPacket());

  // Receiving a response will trigger a callback. Streams are now reset.
  EXPECT_CALL(a.cb, OnStreamsResetPerformed).Times(1);
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest, ResetStreamWillMakeChunksStartAtZeroSsn) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  std::vector<uint8_t> payload(a.options.mtu - 100);

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), {});
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), {});

  auto packet1 = a.cb.ConsumeSentPacket();
  EXPECT_THAT(
      packet1,
      HasChunks(ElementsAre(IsDataChunk(Property(&DataChunk::ssn, SSN(0))))));
  z->socket.ReceivePacket(packet1);

  auto packet2 = a.cb.ConsumeSentPacket();
  EXPECT_THAT(
      packet2,
      HasChunks(ElementsAre(IsDataChunk(Property(&DataChunk::ssn, SSN(1))))));
  z->socket.ReceivePacket(packet2);

  // Handle SACK
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());

  absl::optional<DcSctpMessage> msg1 = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg1.has_value());
  EXPECT_EQ(msg1->stream_id(), StreamID(1));

  absl::optional<DcSctpMessage> msg2 = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg2.has_value());
  EXPECT_EQ(msg2->stream_id(), StreamID(1));

  // Reset the outgoing stream. This will directly send a RE-CONFIG.
  a.socket.ResetStreams(std::vector<StreamID>({StreamID(1)}));
  // RE-CONFIG, req
  z->socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // RE-CONFIG, resp
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), {});

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), {});

  auto packet3 = a.cb.ConsumeSentPacket();
  EXPECT_THAT(
      packet3,
      HasChunks(ElementsAre(IsDataChunk(Property(&DataChunk::ssn, SSN(0))))));
  z->socket.ReceivePacket(packet3);

  auto packet4 = a.cb.ConsumeSentPacket();
  EXPECT_THAT(
      packet4,
      HasChunks(ElementsAre(IsDataChunk(Property(&DataChunk::ssn, SSN(1))))));
  z->socket.ReceivePacket(packet4);

  // Handle SACK
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest,
       ResetStreamWillOnlyResetTheRequestedStreams) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  std::vector<uint8_t> payload(a.options.mtu - 100);

  // Send two ordered messages on SID 1
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), {});
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), {});

  auto packet1 = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet1, HasChunks(ElementsAre(IsDataChunk(
                           AllOf(Property(&DataChunk::stream_id, StreamID(1)),
                                 Property(&DataChunk::ssn, SSN(0)))))));
  z->socket.ReceivePacket(packet1);

  auto packet2 = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet2, HasChunks(ElementsAre(IsDataChunk(
                           AllOf(Property(&DataChunk::stream_id, StreamID(1)),
                                 Property(&DataChunk::ssn, SSN(1)))))));
  z->socket.ReceivePacket(packet2);

  // Handle SACK
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());

  // Do the same, for SID 3
  a.socket.Send(DcSctpMessage(StreamID(3), PPID(53), payload), {});
  a.socket.Send(DcSctpMessage(StreamID(3), PPID(53), payload), {});
  auto packet3 = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet3, HasChunks(ElementsAre(IsDataChunk(
                           AllOf(Property(&DataChunk::stream_id, StreamID(3)),
                                 Property(&DataChunk::ssn, SSN(0)))))));
  z->socket.ReceivePacket(packet3);
  auto packet4 = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet4, HasChunks(ElementsAre(IsDataChunk(
                           AllOf(Property(&DataChunk::stream_id, StreamID(3)),
                                 Property(&DataChunk::ssn, SSN(1)))))));
  z->socket.ReceivePacket(packet4);
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());

  // Receive all messages.
  absl::optional<DcSctpMessage> msg1 = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg1.has_value());
  EXPECT_EQ(msg1->stream_id(), StreamID(1));

  absl::optional<DcSctpMessage> msg2 = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg2.has_value());
  EXPECT_EQ(msg2->stream_id(), StreamID(1));

  absl::optional<DcSctpMessage> msg3 = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg3.has_value());
  EXPECT_EQ(msg3->stream_id(), StreamID(3));

  absl::optional<DcSctpMessage> msg4 = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg4.has_value());
  EXPECT_EQ(msg4->stream_id(), StreamID(3));

  // Reset SID 1. This will directly send a RE-CONFIG.
  a.socket.ResetStreams(std::vector<StreamID>({StreamID(3)}));
  // RE-CONFIG, req
  z->socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // RE-CONFIG, resp
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());

  // Send a message on SID 1 and 3 - SID 1 should not be reset, but 3 should.
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), {});

  a.socket.Send(DcSctpMessage(StreamID(3), PPID(53), payload), {});

  auto packet5 = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet5,
              HasChunks(ElementsAre(IsDataChunk(
                  AllOf(Property(&DataChunk::stream_id, StreamID(1)),
                        Property(&DataChunk::ssn, SSN(2)))))));  // Unchanged.
  z->socket.ReceivePacket(packet5);

  auto packet6 = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet6, HasChunks(ElementsAre(IsDataChunk(AllOf(
                           Property(&DataChunk::stream_id, StreamID(3)),
                           Property(&DataChunk::ssn, SSN(0)))))));  // Reset
  z->socket.ReceivePacket(packet6);

  // Handle SACK
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest, OnePeerReconnects) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  EXPECT_CALL(a.cb, OnConnectionRestarted).Times(1);
  // Let's be evil here - reconnect while a fragmented packet was about to be
  // sent. The receiving side should get it in full.
  std::vector<uint8_t> payload(kLargeMessageSize);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), kSendOptions);

  // First DATA
  z->socket.ReceivePacket(a.cb.ConsumeSentPacket());

  // Create a new association, z2 - and don't use z anymore.
  SocketUnderTest z2("Z2");
  z2.socket.Connect();

  // Retransmit and handle the rest. As there will be some chunks in-flight that
  // have the wrong verification tag, those will yield errors.
  ExchangeMessages(a, z2);

  absl::optional<DcSctpMessage> msg = z2.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg.has_value());
  EXPECT_EQ(msg->stream_id(), StreamID(1));
  EXPECT_THAT(msg->payload(), testing::ElementsAreArray(payload));
}

TEST_P(DcSctpSocketParametrizedTest, SendMessageWithLimitedRtx) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  SendOptions send_options;
  send_options.max_retransmissions = 0;
  std::vector<uint8_t> payload(a.options.mtu - 100);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(51), payload), send_options);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(52), payload), send_options);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), send_options);

  // First DATA
  z->socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // Second DATA (lost)
  a.cb.ConsumeSentPacket();
  // Third DATA
  z->socket.ReceivePacket(a.cb.ConsumeSentPacket());

  // Handle SACK for first DATA
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());

  // Handle delayed SACK for third DATA
  AdvanceTime(a, *z, a.options.delayed_ack_max_timeout.ToTimeDelta());

  // Handle SACK for second DATA
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());

  // Now the missing data chunk will be marked as nacked, but it might still be
  // in-flight and the reported gap could be due to out-of-order delivery. So
  // the RetransmissionQueue will not mark it as "to be retransmitted" until
  // after the t3-rtx timer has expired.
  AdvanceTime(a, *z, a.options.rto_initial.ToTimeDelta());

  // The chunk will be marked as retransmitted, and then as abandoned, which
  // will trigger a FORWARD-TSN to be sent.

  // FORWARD-TSN (third)
  z->socket.ReceivePacket(a.cb.ConsumeSentPacket());

  // Which will trigger a SACK
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());

  absl::optional<DcSctpMessage> msg1 = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg1.has_value());
  EXPECT_EQ(msg1->ppid(), PPID(51));

  absl::optional<DcSctpMessage> msg2 = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg2.has_value());
  EXPECT_EQ(msg2->ppid(), PPID(53));

  absl::optional<DcSctpMessage> msg3 = z->cb.ConsumeReceivedMessage();
  EXPECT_FALSE(msg3.has_value());

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest, SendManyFragmentedMessagesWithLimitedRtx) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  SendOptions send_options;
  send_options.unordered = IsUnordered(true);
  send_options.max_retransmissions = 0;
  std::vector<uint8_t> payload(a.options.mtu * 2 - 100 /* margin */);
  // Sending first message
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(51), payload), send_options);
  // Sending second message
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(52), payload), send_options);
  // Sending third message
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), send_options);
  // Sending fourth message
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(54), payload), send_options);

  // First DATA, first fragment
  std::vector<uint8_t> packet = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet, HasChunks(ElementsAre(
                          IsDataChunk(Property(&DataChunk::ppid, PPID(51))))));
  z->socket.ReceivePacket(std::move(packet));

  // First DATA, second fragment (lost)
  packet = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet, HasChunks(ElementsAre(
                          IsDataChunk(Property(&DataChunk::ppid, PPID(51))))));

  // Second DATA, first fragment
  packet = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet, HasChunks(ElementsAre(
                          IsDataChunk(Property(&DataChunk::ppid, PPID(52))))));
  z->socket.ReceivePacket(std::move(packet));

  // Second DATA, second fragment (lost)
  packet = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet, HasChunks(ElementsAre(IsDataChunk(
                          AllOf(Property(&DataChunk::ppid, PPID(52)),
                                Property(&DataChunk::ssn, SSN(0)))))));

  // Third DATA, first fragment
  packet = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet, HasChunks(ElementsAre(IsDataChunk(
                          AllOf(Property(&DataChunk::ppid, PPID(53)),
                                Property(&DataChunk::ssn, SSN(0)))))));
  z->socket.ReceivePacket(std::move(packet));

  // Third DATA, second fragment (lost)
  packet = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet, HasChunks(ElementsAre(IsDataChunk(
                          AllOf(Property(&DataChunk::ppid, PPID(53)),
                                Property(&DataChunk::ssn, SSN(0)))))));

  // Fourth DATA, first fragment
  packet = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet, HasChunks(ElementsAre(IsDataChunk(
                          AllOf(Property(&DataChunk::ppid, PPID(54)),
                                Property(&DataChunk::ssn, SSN(0)))))));
  z->socket.ReceivePacket(std::move(packet));

  // Fourth DATA, second fragment
  packet = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet, HasChunks(ElementsAre(IsDataChunk(
                          AllOf(Property(&DataChunk::ppid, PPID(54)),
                                Property(&DataChunk::ssn, SSN(0)))))));
  z->socket.ReceivePacket(std::move(packet));

  ExchangeMessages(a, *z);

  // Let the RTX timer expire, and exchange FORWARD-TSN/SACKs
  AdvanceTime(a, *z, a.options.rto_initial.ToTimeDelta());

  ExchangeMessages(a, *z);

  absl::optional<DcSctpMessage> msg1 = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg1.has_value());
  EXPECT_EQ(msg1->ppid(), PPID(54));

  ASSERT_FALSE(z->cb.ConsumeReceivedMessage().has_value());

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

struct FakeChunkConfig : ChunkConfig {
  static constexpr int kType = 0x49;
  static constexpr size_t kHeaderSize = 4;
  static constexpr int kVariableLengthAlignment = 0;
};

class FakeChunk : public Chunk, public TLVTrait<FakeChunkConfig> {
 public:
  FakeChunk() {}

  FakeChunk(FakeChunk&& other) = default;
  FakeChunk& operator=(FakeChunk&& other) = default;

  void SerializeTo(std::vector<uint8_t>& out) const override {
    AllocateTLV(out);
  }
  std::string ToString() const override { return "FAKE"; }
};

TEST_P(DcSctpSocketParametrizedTest, ReceivingUnknownChunkRespondsWithError) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  // Inject a FAKE chunk
  SctpPacket::Builder b(a.socket.verification_tag(), DcSctpOptions());
  b.Add(FakeChunk());
  a.socket.ReceivePacket(b.Build());

  // ERROR is sent as a reply. Capture it.
  ASSERT_HAS_VALUE_AND_ASSIGN(
      SctpPacket reply_packet,
      SctpPacket::Parse(a.cb.ConsumeSentPacket(), z->options));
  ASSERT_THAT(reply_packet.descriptors(), SizeIs(1));
  ASSERT_HAS_VALUE_AND_ASSIGN(
      ErrorChunk error, ErrorChunk::Parse(reply_packet.descriptors()[0].data));
  ASSERT_HAS_VALUE_AND_ASSIGN(
      UnrecognizedChunkTypeCause cause,
      error.error_causes().get<UnrecognizedChunkTypeCause>());
  EXPECT_THAT(cause.unrecognized_chunk(), ElementsAre(0x49, 0x00, 0x00, 0x04));

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest, ReceivingErrorChunkReportsAsCallback) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  // Inject a ERROR chunk
  SctpPacket::Builder b(a.socket.verification_tag(), DcSctpOptions());
  b.Add(
      ErrorChunk(Parameters::Builder()
                     .Add(UnrecognizedChunkTypeCause({0x49, 0x00, 0x00, 0x04}))
                     .Build()));

  EXPECT_CALL(a.cb, OnError(ErrorKind::kPeerReported,
                            HasSubstr("Unrecognized Chunk Type")));
  a.socket.ReceivePacket(b.Build());

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST(DcSctpSocketTest, PassingHighWatermarkWillOnlyAcceptCumAckTsn) {
  SocketUnderTest a("A");

  constexpr size_t kReceiveWindowBufferSize = 2000;
  SocketUnderTest z(
      "Z", {.mtu = 3000,
            .max_receiver_window_buffer_size = kReceiveWindowBufferSize});

  EXPECT_CALL(z.cb, OnClosed).Times(0);
  EXPECT_CALL(z.cb, OnAborted).Times(0);

  a.socket.Connect();
  std::vector<uint8_t> init_data = a.cb.ConsumeSentPacket();
  ASSERT_HAS_VALUE_AND_ASSIGN(SctpPacket init_packet,
                              SctpPacket::Parse(init_data, z.options));
  ASSERT_HAS_VALUE_AND_ASSIGN(
      InitChunk init_chunk,
      InitChunk::Parse(init_packet.descriptors()[0].data));
  z.socket.ReceivePacket(init_data);
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());

  // Fill up Z2 to the high watermark limit.
  constexpr size_t kWatermarkLimit =
      kReceiveWindowBufferSize * ReassemblyQueue::kHighWatermarkLimit;
  constexpr size_t kRemainingSize = kReceiveWindowBufferSize - kWatermarkLimit;

  TSN tsn = init_chunk.initial_tsn();
  AnyDataChunk::Options opts;
  opts.is_beginning = Data::IsBeginning(true);
  z.socket.ReceivePacket(
      SctpPacket::Builder(z.socket.verification_tag(), z.options)
          .Add(DataChunk(tsn, StreamID(1), SSN(0), PPID(53),
                         std::vector<uint8_t>(kWatermarkLimit + 1), opts))
          .Build());

  // First DATA will always trigger a SACK. It's not interesting.
  EXPECT_THAT(z.cb.ConsumeSentPacket(),
              HasChunks(ElementsAre(IsSack(
                  AllOf(Property(&SackChunk::cumulative_tsn_ack, tsn),
                        Property(&SackChunk::gap_ack_blocks, IsEmpty()))))));

  // This DATA should be accepted - it's advancing cum ack tsn.
  z.socket.ReceivePacket(
      SctpPacket::Builder(z.socket.verification_tag(), z.options)
          .Add(DataChunk(AddTo(tsn, 1), StreamID(1), SSN(0), PPID(53),
                         std::vector<uint8_t>(1),
                         /*options=*/{}))
          .Build());

  // The receiver might have moved into delayed ack mode.
  AdvanceTime(a, z, z.options.rto_initial.ToTimeDelta());

  EXPECT_THAT(z.cb.ConsumeSentPacket(),
              HasChunks(ElementsAre(IsSack(
                  AllOf(Property(&SackChunk::cumulative_tsn_ack, AddTo(tsn, 1)),
                        Property(&SackChunk::gap_ack_blocks, IsEmpty()))))));

  // This DATA will not be accepted - it's not advancing cum ack tsn.
  z.socket.ReceivePacket(
      SctpPacket::Builder(z.socket.verification_tag(), z.options)
          .Add(DataChunk(AddTo(tsn, 3), StreamID(1), SSN(0), PPID(53),
                         std::vector<uint8_t>(1),
                         /*options=*/{}))
          .Build());

  // Sack will be sent in IMMEDIATE mode when this is happening.
  EXPECT_THAT(z.cb.ConsumeSentPacket(),
              HasChunks(ElementsAre(IsSack(
                  AllOf(Property(&SackChunk::cumulative_tsn_ack, AddTo(tsn, 1)),
                        Property(&SackChunk::gap_ack_blocks, IsEmpty()))))));

  // This DATA will not be accepted either.
  z.socket.ReceivePacket(
      SctpPacket::Builder(z.socket.verification_tag(), z.options)
          .Add(DataChunk(AddTo(tsn, 4), StreamID(1), SSN(0), PPID(53),
                         std::vector<uint8_t>(1),
                         /*options=*/{}))
          .Build());

  // Sack will be sent in IMMEDIATE mode when this is happening.
  EXPECT_THAT(z.cb.ConsumeSentPacket(),
              HasChunks(ElementsAre(IsSack(
                  AllOf(Property(&SackChunk::cumulative_tsn_ack, AddTo(tsn, 1)),
                        Property(&SackChunk::gap_ack_blocks, IsEmpty()))))));

  // This DATA should be accepted, and it fills the reassembly queue.
  z.socket.ReceivePacket(
      SctpPacket::Builder(z.socket.verification_tag(), z.options)
          .Add(DataChunk(AddTo(tsn, 2), StreamID(1), SSN(0), PPID(53),
                         std::vector<uint8_t>(kRemainingSize),
                         /*options=*/{}))
          .Build());

  // The receiver might have moved into delayed ack mode.
  AdvanceTime(a, z, z.options.rto_initial.ToTimeDelta());

  EXPECT_THAT(z.cb.ConsumeSentPacket(),
              HasChunks(ElementsAre(IsSack(
                  AllOf(Property(&SackChunk::cumulative_tsn_ack, AddTo(tsn, 2)),
                        Property(&SackChunk::gap_ack_blocks, IsEmpty()))))));

  EXPECT_CALL(z.cb, OnAborted(ErrorKind::kResourceExhaustion, _));
  EXPECT_CALL(z.cb, OnClosed).Times(0);

  // This DATA will make the connection close. It's too full now.
  z.socket.ReceivePacket(
      SctpPacket::Builder(z.socket.verification_tag(), z.options)
          .Add(DataChunk(AddTo(tsn, 3), StreamID(1), SSN(0), PPID(53),
                         std::vector<uint8_t>(kSmallMessageSize),
                         /*options=*/{}))
          .Build());
}

TEST(DcSctpSocketTest, SetMaxMessageSize) {
  SocketUnderTest a("A");

  a.socket.SetMaxMessageSize(42u);
  EXPECT_EQ(a.socket.options().max_message_size, 42u);
}

TEST_P(DcSctpSocketParametrizedTest, SendManyMessages) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  static constexpr int kIterations = 100;
  std::vector<DcSctpMessage> messages;
  std::vector<SendStatus> statuses;
  for (int i = 0; i < kIterations; ++i) {
    messages.push_back(DcSctpMessage(StreamID(1), PPID(53), {1, 2}));
    statuses.push_back(SendStatus::kSuccess);
  }
  EXPECT_THAT(a.socket.SendMany(messages, {}), ElementsAreArray(statuses));

  ExchangeMessages(a, *z);

  for (int i = 0; i < kIterations; ++i) {
    EXPECT_TRUE(z->cb.ConsumeReceivedMessage().has_value());
  }

  EXPECT_FALSE(z->cb.ConsumeReceivedMessage().has_value());

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest, SendsMessagesWithLowLifetime) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  // Mock that the time always goes forward.
  Timestamp now = Timestamp::Zero();
  EXPECT_CALL(a.cb, Now).WillRepeatedly([&]() {
    now += TimeDelta::Millis(3);
    return now;
  });
  EXPECT_CALL(z->cb, Now).WillRepeatedly([&]() {
    now += TimeDelta::Millis(3);
    return now;
  });

  // Queue a few small messages with low lifetime, both ordered and unordered,
  // and validate that all are delivered.
  static constexpr int kIterations = 100;
  for (int i = 0; i < kIterations; ++i) {
    SendOptions send_options;
    send_options.unordered = IsUnordered((i % 2) == 0);
    send_options.lifetime = DurationMs(i % 3);  // 0, 1, 2 ms

    a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {1, 2}), send_options);
  }

  ExchangeMessages(a, *z);

  for (int i = 0; i < kIterations; ++i) {
    EXPECT_TRUE(z->cb.ConsumeReceivedMessage().has_value());
  }

  EXPECT_FALSE(z->cb.ConsumeReceivedMessage().has_value());

  // Validate that the sockets really make the time move forward.
  EXPECT_GE(now.ms(), kIterations * 2);

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest,
       DiscardsMessagesWithLowLifetimeIfMustBuffer) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  SendOptions lifetime_0;
  lifetime_0.unordered = IsUnordered(true);
  lifetime_0.lifetime = DurationMs(0);

  SendOptions lifetime_1;
  lifetime_1.unordered = IsUnordered(true);
  lifetime_1.lifetime = DurationMs(1);

  // Mock that the time always goes forward.
  Timestamp now = Timestamp::Zero();
  EXPECT_CALL(a.cb, Now).WillRepeatedly([&]() {
    now += TimeDelta::Millis(3);
    return now;
  });
  EXPECT_CALL(z->cb, Now).WillRepeatedly([&]() {
    now += TimeDelta::Millis(3);
    return now;
  });

  // Fill up the send buffer with a large message.
  std::vector<uint8_t> payload(kLargeMessageSize);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), kSendOptions);

  // And queue a few small messages with lifetime=0 or 1 ms - can't be sent.
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {1, 2, 3}), lifetime_0);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {4, 5, 6}), lifetime_1);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {7, 8, 9}), lifetime_0);

  // Handle all that was sent until congestion window got full.
  for (;;) {
    std::vector<uint8_t> packet_from_a = a.cb.ConsumeSentPacket();
    if (packet_from_a.empty()) {
      break;
    }
    z->socket.ReceivePacket(std::move(packet_from_a));
  }

  // Shouldn't be enough to send that large message.
  EXPECT_FALSE(z->cb.ConsumeReceivedMessage().has_value());

  // Exchange the rest of the messages, with the time ever increasing.
  ExchangeMessages(a, *z);

  // The large message should be delivered. It was sent reliably.
  ASSERT_HAS_VALUE_AND_ASSIGN(DcSctpMessage m1, z->cb.ConsumeReceivedMessage());
  EXPECT_EQ(m1.stream_id(), StreamID(1));
  EXPECT_THAT(m1.payload(), SizeIs(kLargeMessageSize));

  // But none of the smaller messages.
  EXPECT_FALSE(z->cb.ConsumeReceivedMessage().has_value());

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest, HasReasonableBufferedAmountValues) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  EXPECT_EQ(a.socket.buffered_amount(StreamID(1)), 0u);

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53),
                              std::vector<uint8_t>(kSmallMessageSize)),
                kSendOptions);
  // Sending a small message will directly send it as a single packet, so
  // nothing is left in the queue.
  EXPECT_EQ(a.socket.buffered_amount(StreamID(1)), 0u);

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53),
                              std::vector<uint8_t>(kLargeMessageSize)),
                kSendOptions);

  // Sending a message will directly start sending a few packets, so the
  // buffered amount is not the full message size.
  EXPECT_GT(a.socket.buffered_amount(StreamID(1)), 0u);
  EXPECT_LT(a.socket.buffered_amount(StreamID(1)), kLargeMessageSize);

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST(DcSctpSocketTest, HasDefaultOnBufferedAmountLowValueZero) {
  SocketUnderTest a("A");
  EXPECT_EQ(a.socket.buffered_amount_low_threshold(StreamID(1)), 0u);
}

TEST_P(DcSctpSocketParametrizedTest,
       TriggersOnBufferedAmountLowWithDefaultValueZero) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  EXPECT_CALL(a.cb, OnBufferedAmountLow).Times(0);
  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  EXPECT_CALL(a.cb, OnBufferedAmountLow(StreamID(1)));
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53),
                              std::vector<uint8_t>(kSmallMessageSize)),
                kSendOptions);
  ExchangeMessages(a, *z);

  EXPECT_CALL(a.cb, OnBufferedAmountLow).WillRepeatedly(testing::Return());
  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest,
       DoesntTriggerOnBufferedAmountLowIfBelowThreshold) {
  static constexpr size_t kMessageSize = 1000;
  static constexpr size_t kBufferedAmountLowThreshold = kMessageSize * 10;

  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  a.socket.SetBufferedAmountLowThreshold(StreamID(1),
                                         kBufferedAmountLowThreshold);
  EXPECT_CALL(a.cb, OnBufferedAmountLow).Times(0);
  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  EXPECT_CALL(a.cb, OnBufferedAmountLow(StreamID(1))).Times(0);
  a.socket.Send(
      DcSctpMessage(StreamID(1), PPID(53), std::vector<uint8_t>(kMessageSize)),
      kSendOptions);
  ExchangeMessages(a, *z);

  a.socket.Send(
      DcSctpMessage(StreamID(1), PPID(53), std::vector<uint8_t>(kMessageSize)),
      kSendOptions);
  ExchangeMessages(a, *z);

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest, TriggersOnBufferedAmountMultipleTimes) {
  static constexpr size_t kMessageSize = 1000;
  static constexpr size_t kBufferedAmountLowThreshold = kMessageSize / 2;

  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  a.socket.SetBufferedAmountLowThreshold(StreamID(1),
                                         kBufferedAmountLowThreshold);
  EXPECT_CALL(a.cb, OnBufferedAmountLow).Times(0);
  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  EXPECT_CALL(a.cb, OnBufferedAmountLow(StreamID(1))).Times(3);
  EXPECT_CALL(a.cb, OnBufferedAmountLow(StreamID(2))).Times(2);
  a.socket.Send(
      DcSctpMessage(StreamID(1), PPID(53), std::vector<uint8_t>(kMessageSize)),
      kSendOptions);
  ExchangeMessages(a, *z);

  a.socket.Send(
      DcSctpMessage(StreamID(2), PPID(53), std::vector<uint8_t>(kMessageSize)),
      kSendOptions);
  ExchangeMessages(a, *z);

  a.socket.Send(
      DcSctpMessage(StreamID(1), PPID(53), std::vector<uint8_t>(kMessageSize)),
      kSendOptions);
  ExchangeMessages(a, *z);

  a.socket.Send(
      DcSctpMessage(StreamID(2), PPID(53), std::vector<uint8_t>(kMessageSize)),
      kSendOptions);
  ExchangeMessages(a, *z);

  a.socket.Send(
      DcSctpMessage(StreamID(1), PPID(53), std::vector<uint8_t>(kMessageSize)),
      kSendOptions);
  ExchangeMessages(a, *z);

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest,
       TriggersOnBufferedAmountLowOnlyWhenCrossingThreshold) {
  static constexpr size_t kMessageSize = 1000;
  static constexpr size_t kBufferedAmountLowThreshold = kMessageSize * 1.5;

  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  a.socket.SetBufferedAmountLowThreshold(StreamID(1),
                                         kBufferedAmountLowThreshold);
  EXPECT_CALL(a.cb, OnBufferedAmountLow).Times(0);
  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  EXPECT_CALL(a.cb, OnBufferedAmountLow).Times(0);

  // Add a few messages to fill up the congestion window. When that is full,
  // messages will start to be fully buffered.
  while (a.socket.buffered_amount(StreamID(1)) <= kBufferedAmountLowThreshold) {
    a.socket.Send(DcSctpMessage(StreamID(1), PPID(53),
                                std::vector<uint8_t>(kMessageSize)),
                  kSendOptions);
  }
  size_t initial_buffered = a.socket.buffered_amount(StreamID(1));
  ASSERT_GT(initial_buffered, kBufferedAmountLowThreshold);

  // Start ACKing packets, which will empty the send queue, and trigger the
  // callback.
  EXPECT_CALL(a.cb, OnBufferedAmountLow(StreamID(1))).Times(1);
  ExchangeMessages(a, *z);

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest,
       DoesntTriggerOnTotalBufferAmountLowWhenBelow) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  EXPECT_CALL(a.cb, OnTotalBufferedAmountLow).Times(0);

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53),
                              std::vector<uint8_t>(kLargeMessageSize)),
                kSendOptions);

  ExchangeMessages(a, *z);

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest,
       TriggersOnTotalBufferAmountLowWhenCrossingThreshold) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  EXPECT_CALL(a.cb, OnTotalBufferedAmountLow).Times(0);

  // Fill up the send queue completely.
  for (;;) {
    if (a.socket.Send(DcSctpMessage(StreamID(1), PPID(53),
                                    std::vector<uint8_t>(kLargeMessageSize)),
                      kSendOptions) == SendStatus::kErrorResourceExhaustion) {
      break;
    }
  }

  EXPECT_CALL(a.cb, OnTotalBufferedAmountLow).Times(1);
  ExchangeMessages(a, *z);

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST(DcSctpSocketTest, InitialMetricsAreUnset) {
  SocketUnderTest a("A");

  EXPECT_FALSE(a.socket.GetMetrics().has_value());
}

TEST(DcSctpSocketTest, MessageInterleavingMetricsAreSet) {
  std::vector<std::pair<bool, bool>> combinations = {
      {false, false}, {false, true}, {true, false}, {true, true}};
  for (const auto& [a_enable, z_enable] : combinations) {
    DcSctpOptions a_options = {.enable_message_interleaving = a_enable};
    DcSctpOptions z_options = {.enable_message_interleaving = z_enable};

    SocketUnderTest a("A", a_options);
    SocketUnderTest z("Z", z_options);
    ConnectSockets(a, z);

    EXPECT_EQ(a.socket.GetMetrics()->uses_message_interleaving,
              a_enable && z_enable);
  }
}

TEST(DcSctpSocketTest, RxAndTxPacketMetricsIncrease) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  ConnectSockets(a, z);

  const size_t initial_a_rwnd = a.options.max_receiver_window_buffer_size *
                                ReassemblyQueue::kHighWatermarkLimit;

  EXPECT_EQ(a.socket.GetMetrics()->tx_packets_count, 2u);
  EXPECT_EQ(a.socket.GetMetrics()->rx_packets_count, 2u);
  EXPECT_EQ(a.socket.GetMetrics()->tx_messages_count, 0u);
  EXPECT_EQ(a.socket.GetMetrics()->cwnd_bytes,
            a.options.cwnd_mtus_initial * a.options.mtu);
  EXPECT_EQ(a.socket.GetMetrics()->unack_data_count, 0u);

  EXPECT_EQ(z.socket.GetMetrics()->rx_packets_count, 2u);
  EXPECT_EQ(z.socket.GetMetrics()->rx_messages_count, 0u);

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {1, 2}), kSendOptions);
  EXPECT_EQ(a.socket.GetMetrics()->unack_data_count, 1u);

  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());  // DATA
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());  // SACK
  EXPECT_EQ(a.socket.GetMetrics()->peer_rwnd_bytes, initial_a_rwnd);
  EXPECT_EQ(a.socket.GetMetrics()->unack_data_count, 0u);

  EXPECT_TRUE(z.cb.ConsumeReceivedMessage().has_value());

  EXPECT_EQ(a.socket.GetMetrics()->tx_packets_count, 3u);
  EXPECT_EQ(a.socket.GetMetrics()->rx_packets_count, 3u);
  EXPECT_EQ(a.socket.GetMetrics()->tx_messages_count, 1u);

  EXPECT_EQ(z.socket.GetMetrics()->rx_packets_count, 3u);
  EXPECT_EQ(z.socket.GetMetrics()->rx_messages_count, 1u);

  // Send one more (large - fragmented), and receive the delayed SACK.
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53),
                              std::vector<uint8_t>(a.options.mtu * 2 + 1)),
                kSendOptions);
  EXPECT_EQ(a.socket.GetMetrics()->unack_data_count, 3u);

  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());  // DATA
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());  // DATA

  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());  // SACK
  EXPECT_EQ(a.socket.GetMetrics()->unack_data_count, 1u);
  EXPECT_GT(a.socket.GetMetrics()->peer_rwnd_bytes, 0u);
  EXPECT_LT(a.socket.GetMetrics()->peer_rwnd_bytes, initial_a_rwnd);

  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());  // DATA

  EXPECT_TRUE(z.cb.ConsumeReceivedMessage().has_value());

  EXPECT_EQ(a.socket.GetMetrics()->tx_packets_count, 6u);
  EXPECT_EQ(a.socket.GetMetrics()->rx_packets_count, 4u);
  EXPECT_EQ(a.socket.GetMetrics()->tx_messages_count, 2u);

  EXPECT_EQ(z.socket.GetMetrics()->rx_packets_count, 6u);
  EXPECT_EQ(z.socket.GetMetrics()->rx_messages_count, 2u);

  // Delayed sack
  AdvanceTime(a, z, a.options.delayed_ack_max_timeout.ToTimeDelta());

  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());  // SACK
  EXPECT_EQ(a.socket.GetMetrics()->unack_data_count, 0u);
  EXPECT_EQ(a.socket.GetMetrics()->rx_packets_count, 5u);
  EXPECT_EQ(a.socket.GetMetrics()->peer_rwnd_bytes, initial_a_rwnd);
}

TEST(DcSctpSocketTest, RetransmissionMetricsAreSetForFastRetransmit) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");
  ConnectSockets(a, z);

  // Enough to trigger fast retransmit of the missing second packet.
  std::vector<uint8_t> payload(DcSctpOptions::kMaxSafeMTUSize * 5);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), kSendOptions);

  // Receive first packet, drop second, receive and retransmit the remaining.
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  a.cb.ConsumeSentPacket();
  ExchangeMessages(a, z);

  EXPECT_EQ(a.socket.GetMetrics()->rtx_packets_count, 1u);
  size_t expected_data_size =
      RoundDownTo4(DcSctpOptions::kMaxSafeMTUSize - SctpPacket::kHeaderSize);
  EXPECT_EQ(a.socket.GetMetrics()->rtx_bytes_count, expected_data_size);
}

TEST(DcSctpSocketTest, RetransmissionMetricsAreSetForNormalRetransmit) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");
  ConnectSockets(a, z);

  std::vector<uint8_t> payload(kSmallMessageSize);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), kSendOptions);

  a.cb.ConsumeSentPacket();
  AdvanceTime(a, z, a.options.rto_initial.ToTimeDelta());
  ExchangeMessages(a, z);

  EXPECT_EQ(a.socket.GetMetrics()->rtx_packets_count, 1u);
  size_t expected_data_size =
      RoundUpTo4(kSmallMessageSize + DataChunk::kHeaderSize);
  EXPECT_EQ(a.socket.GetMetrics()->rtx_bytes_count, expected_data_size);
}

TEST_P(DcSctpSocketParametrizedTest, UnackDataAlsoIncludesSendQueue) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53),
                              std::vector<uint8_t>(kLargeMessageSize)),
                kSendOptions);
  size_t payload_bytes =
      a.options.mtu - SctpPacket::kHeaderSize - DataChunk::kHeaderSize;

  size_t expected_sent_packets = a.options.cwnd_mtus_initial;

  size_t expected_queued_bytes =
      kLargeMessageSize - expected_sent_packets * payload_bytes;

  size_t expected_queued_packets = expected_queued_bytes / payload_bytes;

  // Due to alignment, padding etc, it's hard to calculate the exact number, but
  // it should be in this range.
  EXPECT_GE(a.socket.GetMetrics()->unack_data_count,
            expected_sent_packets + expected_queued_packets);

  EXPECT_LE(a.socket.GetMetrics()->unack_data_count,
            expected_sent_packets + expected_queued_packets + 2);

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest, DoesntSendMoreThanMaxBurstPackets) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53),
                              std::vector<uint8_t>(kLargeMessageSize)),
                kSendOptions);

  for (int i = 0; i < kMaxBurstPackets; ++i) {
    std::vector<uint8_t> packet = a.cb.ConsumeSentPacket();
    EXPECT_THAT(packet, Not(IsEmpty()));
    z->socket.ReceivePacket(std::move(packet));  // DATA
  }

  EXPECT_THAT(a.cb.ConsumeSentPacket(), IsEmpty());

  ExchangeMessages(a, *z);
  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest, SendsOnlyLargePackets) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  // A really large message, to ensure that the congestion window is often full.
  constexpr size_t kMessageSize = 100000;
  a.socket.Send(
      DcSctpMessage(StreamID(1), PPID(53), std::vector<uint8_t>(kMessageSize)),
      kSendOptions);

  bool delivered_packet = false;
  std::vector<size_t> data_packet_sizes;
  do {
    delivered_packet = false;
    std::vector<uint8_t> packet_from_a = a.cb.ConsumeSentPacket();
    if (!packet_from_a.empty()) {
      data_packet_sizes.push_back(packet_from_a.size());
      delivered_packet = true;
      z->socket.ReceivePacket(std::move(packet_from_a));
    }
    std::vector<uint8_t> packet_from_z = z->cb.ConsumeSentPacket();
    if (!packet_from_z.empty()) {
      delivered_packet = true;
      a.socket.ReceivePacket(std::move(packet_from_z));
    }
  } while (delivered_packet);

  size_t packet_payload_bytes =
      a.options.mtu - SctpPacket::kHeaderSize - DataChunk::kHeaderSize;
  // +1 accounts for padding, and rounding up.
  size_t expected_packets =
      (kMessageSize + packet_payload_bytes - 1) / packet_payload_bytes + 1;
  EXPECT_THAT(data_packet_sizes, SizeIs(expected_packets));

  // Remove the last size - it will be the remainder. But all other sizes should
  // be large.
  data_packet_sizes.pop_back();

  for (size_t size : data_packet_sizes) {
    // The 4 is for padding/alignment.
    EXPECT_GE(size, a.options.mtu - 4);
  }

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST(DcSctpSocketTest, SendMessagesAfterHandover) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);

  // Send message before handover to move socket to a not initial state
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {1, 2}), kSendOptions);
  z->socket.ReceivePacket(a.cb.ConsumeSentPacket());
  z->cb.ConsumeReceivedMessage();

  z = HandoverSocket(std::move(z));

  absl::optional<DcSctpMessage> msg;

  RTC_LOG(LS_INFO) << "Sending A #1";

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {3, 4}), kSendOptions);
  z->socket.ReceivePacket(a.cb.ConsumeSentPacket());

  msg = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg.has_value());
  EXPECT_EQ(msg->stream_id(), StreamID(1));
  EXPECT_THAT(msg->payload(), testing::ElementsAre(3, 4));

  RTC_LOG(LS_INFO) << "Sending A #2";

  a.socket.Send(DcSctpMessage(StreamID(2), PPID(53), {5, 6}), kSendOptions);
  z->socket.ReceivePacket(a.cb.ConsumeSentPacket());

  msg = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg.has_value());
  EXPECT_EQ(msg->stream_id(), StreamID(2));
  EXPECT_THAT(msg->payload(), testing::ElementsAre(5, 6));

  RTC_LOG(LS_INFO) << "Sending Z #1";

  z->socket.Send(DcSctpMessage(StreamID(1), PPID(53), {1, 2, 3}), kSendOptions);
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());  // ack
  a.socket.ReceivePacket(z->cb.ConsumeSentPacket());  // data

  msg = a.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg.has_value());
  EXPECT_EQ(msg->stream_id(), StreamID(1));
  EXPECT_THAT(msg->payload(), testing::ElementsAre(1, 2, 3));
}

TEST(DcSctpSocketTest, CanDetectDcsctpImplementation) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  ConnectSockets(a, z);

  EXPECT_EQ(a.socket.peer_implementation(), SctpImplementation::kDcsctp);

  // As A initiated the connection establishment, Z will not receive enough
  // information to know about A's implementation
  EXPECT_EQ(z.socket.peer_implementation(), SctpImplementation::kUnknown);
}

TEST(DcSctpSocketTest, BothCanDetectDcsctpImplementation) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  EXPECT_CALL(a.cb, OnConnected).Times(1);
  EXPECT_CALL(z.cb, OnConnected).Times(1);
  a.socket.Connect();
  z.socket.Connect();

  ExchangeMessages(a, z);

  EXPECT_EQ(a.socket.peer_implementation(), SctpImplementation::kDcsctp);
  EXPECT_EQ(z.socket.peer_implementation(), SctpImplementation::kDcsctp);
}

TEST_P(DcSctpSocketParametrizedTest, CanLoseFirstOrderedMessage) {
  SocketUnderTest a("A");
  auto z = std::make_unique<SocketUnderTest>("Z");

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  SendOptions send_options;
  send_options.unordered = IsUnordered(false);
  send_options.max_retransmissions = 0;
  std::vector<uint8_t> payload(a.options.mtu - 100);

  // Send a first message (SID=1, SSN=0)
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(51), payload), send_options);

  // First DATA is lost, and retransmission timer will delete it.
  a.cb.ConsumeSentPacket();
  AdvanceTime(a, *z, a.options.rto_initial.ToTimeDelta());
  ExchangeMessages(a, *z);

  // Send a second message (SID=0, SSN=1).
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(52), payload), send_options);
  ExchangeMessages(a, *z);

  // The Z socket should receive the second message, but not the first.
  absl::optional<DcSctpMessage> msg = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg.has_value());
  EXPECT_EQ(msg->ppid(), PPID(52));

  EXPECT_FALSE(z->cb.ConsumeReceivedMessage().has_value());

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST(DcSctpSocketTest, ReceiveBothUnorderedAndOrderedWithSameTSN) {
  /* This issue was found by fuzzing. */
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  a.socket.Connect();
  std::vector<uint8_t> init_data = a.cb.ConsumeSentPacket();
  ASSERT_HAS_VALUE_AND_ASSIGN(SctpPacket init_packet,
                              SctpPacket::Parse(init_data, z.options));
  ASSERT_HAS_VALUE_AND_ASSIGN(
      InitChunk init_chunk,
      InitChunk::Parse(init_packet.descriptors()[0].data));
  z.socket.ReceivePacket(init_data);
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());

  // Receive a short unordered message with tsn=INITIAL_TSN+1
  TSN tsn = init_chunk.initial_tsn();
  AnyDataChunk::Options opts;
  opts.is_beginning = Data::IsBeginning(true);
  opts.is_end = Data::IsEnd(true);
  opts.is_unordered = IsUnordered(true);
  z.socket.ReceivePacket(
      SctpPacket::Builder(z.socket.verification_tag(), z.options)
          .Add(DataChunk(TSN(*tsn + 1), StreamID(1), SSN(0), PPID(53),
                         std::vector<uint8_t>(10), opts))
          .Build());

  // Now receive a longer _ordered_ message with [INITIAL_TSN, INITIAL_TSN+1].
  // This isn't allowed as it reuses TSN=53 with different properties, but it
  // shouldn't cause any issues.
  opts.is_unordered = IsUnordered(false);
  opts.is_end = Data::IsEnd(false);
  z.socket.ReceivePacket(
      SctpPacket::Builder(z.socket.verification_tag(), z.options)
          .Add(DataChunk(tsn, StreamID(1), SSN(0), PPID(53),
                         std::vector<uint8_t>(10), opts))
          .Build());

  opts.is_beginning = Data::IsBeginning(false);
  opts.is_end = Data::IsEnd(true);
  z.socket.ReceivePacket(
      SctpPacket::Builder(z.socket.verification_tag(), z.options)
          .Add(DataChunk(TSN(*tsn + 1), StreamID(1), SSN(0), PPID(53),
                         std::vector<uint8_t>(10), opts))
          .Build());
}

TEST(DcSctpSocketTest, CloseTwoStreamsAtTheSameTime) {
  // Reported as https://crbug.com/1312009.
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  EXPECT_CALL(z.cb, OnIncomingStreamsReset(ElementsAre(StreamID(1)))).Times(1);
  EXPECT_CALL(z.cb, OnIncomingStreamsReset(ElementsAre(StreamID(2)))).Times(1);
  EXPECT_CALL(a.cb, OnStreamsResetPerformed(ElementsAre(StreamID(1)))).Times(1);
  EXPECT_CALL(a.cb, OnStreamsResetPerformed(ElementsAre(StreamID(2)))).Times(1);

  ConnectSockets(a, z);

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {1, 2}), kSendOptions);
  a.socket.Send(DcSctpMessage(StreamID(2), PPID(53), {1, 2}), kSendOptions);

  ExchangeMessages(a, z);

  a.socket.ResetStreams(std::vector<StreamID>({StreamID(1)}));
  a.socket.ResetStreams(std::vector<StreamID>({StreamID(2)}));

  ExchangeMessages(a, z);
}

TEST(DcSctpSocketTest, CloseThreeStreamsAtTheSameTime) {
  // Similar to CloseTwoStreamsAtTheSameTime, but ensuring that the two
  // remaining streams are reset at the same time in the second request.
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  EXPECT_CALL(z.cb, OnIncomingStreamsReset(ElementsAre(StreamID(1)))).Times(1);
  EXPECT_CALL(z.cb, OnIncomingStreamsReset(
                        UnorderedElementsAre(StreamID(2), StreamID(3))))
      .Times(1);
  EXPECT_CALL(a.cb, OnStreamsResetPerformed(ElementsAre(StreamID(1)))).Times(1);
  EXPECT_CALL(a.cb, OnStreamsResetPerformed(
                        UnorderedElementsAre(StreamID(2), StreamID(3))))
      .Times(1);

  ConnectSockets(a, z);

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {1, 2}), kSendOptions);
  a.socket.Send(DcSctpMessage(StreamID(2), PPID(53), {1, 2}), kSendOptions);
  a.socket.Send(DcSctpMessage(StreamID(3), PPID(53), {1, 2}), kSendOptions);

  ExchangeMessages(a, z);

  a.socket.ResetStreams(std::vector<StreamID>({StreamID(1)}));
  a.socket.ResetStreams(std::vector<StreamID>({StreamID(2)}));
  a.socket.ResetStreams(std::vector<StreamID>({StreamID(3)}));

  ExchangeMessages(a, z);
}

TEST(DcSctpSocketTest, CloseStreamsWithPendingRequest) {
  // Checks that stream reset requests are properly paused when they can't be
  // immediately reset - i.e. when there is already an ongoing stream reset
  // request (and there can only be a single one in-flight).
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  EXPECT_CALL(z.cb, OnIncomingStreamsReset(ElementsAre(StreamID(1)))).Times(1);
  EXPECT_CALL(z.cb, OnIncomingStreamsReset(
                        UnorderedElementsAre(StreamID(2), StreamID(3))))
      .Times(1);
  EXPECT_CALL(a.cb, OnStreamsResetPerformed(ElementsAre(StreamID(1)))).Times(1);
  EXPECT_CALL(a.cb, OnStreamsResetPerformed(
                        UnorderedElementsAre(StreamID(2), StreamID(3))))
      .Times(1);

  ConnectSockets(a, z);

  SendOptions send_options = {.unordered = IsUnordered(false)};

  // Send a few ordered messages
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {1, 2}), send_options);
  a.socket.Send(DcSctpMessage(StreamID(2), PPID(53), {1, 2}), send_options);
  a.socket.Send(DcSctpMessage(StreamID(3), PPID(53), {1, 2}), send_options);

  ExchangeMessages(a, z);

  // Receive these messages
  absl::optional<DcSctpMessage> msg1 = z.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg1.has_value());
  EXPECT_EQ(msg1->stream_id(), StreamID(1));
  absl::optional<DcSctpMessage> msg2 = z.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg2.has_value());
  EXPECT_EQ(msg2->stream_id(), StreamID(2));
  absl::optional<DcSctpMessage> msg3 = z.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg3.has_value());
  EXPECT_EQ(msg3->stream_id(), StreamID(3));

  // Reset the streams - not all at once.
  a.socket.ResetStreams(std::vector<StreamID>({StreamID(1)}));

  std::vector<uint8_t> packet = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet, HasChunks(ElementsAre(IsReConfig(HasParameters(
                          ElementsAre(IsOutgoingResetRequest(Property(
                              &OutgoingSSNResetRequestParameter::stream_ids,
                              ElementsAre(StreamID(1))))))))));
  z.socket.ReceivePacket(std::move(packet));

  // Sending more reset requests while this one is ongoing.

  a.socket.ResetStreams(std::vector<StreamID>({StreamID(2)}));
  a.socket.ResetStreams(std::vector<StreamID>({StreamID(3)}));

  ExchangeMessages(a, z);

  // Send a few more ordered messages
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), {1, 2}), send_options);
  a.socket.Send(DcSctpMessage(StreamID(2), PPID(53), {1, 2}), send_options);
  a.socket.Send(DcSctpMessage(StreamID(3), PPID(53), {1, 2}), send_options);

  ExchangeMessages(a, z);

  // Receive these messages
  absl::optional<DcSctpMessage> msg4 = z.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg4.has_value());
  EXPECT_EQ(msg4->stream_id(), StreamID(1));
  absl::optional<DcSctpMessage> msg5 = z.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg5.has_value());
  EXPECT_EQ(msg5->stream_id(), StreamID(2));
  absl::optional<DcSctpMessage> msg6 = z.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg6.has_value());
  EXPECT_EQ(msg6->stream_id(), StreamID(3));
}

TEST(DcSctpSocketTest, StreamsHaveInitialPriority) {
  DcSctpOptions options = {.default_stream_priority = StreamPriority(42)};
  SocketUnderTest a("A", options);

  EXPECT_EQ(a.socket.GetStreamPriority(StreamID(1)),
            options.default_stream_priority);

  a.socket.Send(DcSctpMessage(StreamID(2), PPID(53), {1, 2}), kSendOptions);

  EXPECT_EQ(a.socket.GetStreamPriority(StreamID(2)),
            options.default_stream_priority);
}

TEST(DcSctpSocketTest, CanChangeStreamPriority) {
  DcSctpOptions options = {.default_stream_priority = StreamPriority(42)};
  SocketUnderTest a("A", options);

  a.socket.SetStreamPriority(StreamID(1), StreamPriority(43));
  EXPECT_EQ(a.socket.GetStreamPriority(StreamID(1)), StreamPriority(43));

  a.socket.Send(DcSctpMessage(StreamID(2), PPID(53), {1, 2}), kSendOptions);

  a.socket.SetStreamPriority(StreamID(2), StreamPriority(43));
  EXPECT_EQ(a.socket.GetStreamPriority(StreamID(2)), StreamPriority(43));
}

TEST_P(DcSctpSocketParametrizedTest, WillHandoverPriority) {
  DcSctpOptions options = {.default_stream_priority = StreamPriority(42)};
  auto a = std::make_unique<SocketUnderTest>("A", options);
  SocketUnderTest z("Z");

  ConnectSockets(*a, z);

  a->socket.SetStreamPriority(StreamID(1), StreamPriority(43));
  a->socket.Send(DcSctpMessage(StreamID(2), PPID(53), {1, 2}), kSendOptions);
  a->socket.SetStreamPriority(StreamID(2), StreamPriority(43));

  ExchangeMessages(*a, z);

  a = MaybeHandoverSocket(std::move(a));

  EXPECT_EQ(a->socket.GetStreamPriority(StreamID(1)), StreamPriority(43));
  EXPECT_EQ(a->socket.GetStreamPriority(StreamID(2)), StreamPriority(43));
}

TEST(DcSctpSocketTest, ReconnectSocketWithPendingStreamReset) {
  // This is an issue found by fuzzing, and doesn't really make sense in WebRTC
  // data channels as a SCTP connection is never ever closed and then
  // reconnected. SCTP connections are closed when the peer connection is
  // deleted, and then it doesn't do more with SCTP.
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  ConnectSockets(a, z);

  a.socket.ResetStreams(std::vector<StreamID>({StreamID(1)}));

  EXPECT_CALL(z.cb, OnAborted).Times(1);
  a.socket.Close();

  EXPECT_EQ(a.socket.state(), SocketState::kClosed);

  EXPECT_CALL(a.cb, OnConnected).Times(1);
  EXPECT_CALL(z.cb, OnConnected).Times(1);
  a.socket.Connect();
  ExchangeMessages(a, z);
  a.socket.ResetStreams(std::vector<StreamID>({StreamID(2)}));
}

TEST(DcSctpSocketTest, SmallSentMessagesWithPrioWillArriveInSpecificOrder) {
  DcSctpOptions options = {.enable_message_interleaving = true};
  SocketUnderTest a("A", options);
  SocketUnderTest z("A", options);

  a.socket.SetStreamPriority(StreamID(1), StreamPriority(700));
  a.socket.SetStreamPriority(StreamID(2), StreamPriority(200));
  a.socket.SetStreamPriority(StreamID(3), StreamPriority(100));

  // Enqueue messages before connecting the socket, to ensure they aren't send
  // as soon as Send() is called.
  a.socket.Send(DcSctpMessage(StreamID(3), PPID(301),
                              std::vector<uint8_t>(kSmallMessageSize)),
                kSendOptions);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(101),
                              std::vector<uint8_t>(kSmallMessageSize)),
                kSendOptions);
  a.socket.Send(DcSctpMessage(StreamID(2), PPID(201),
                              std::vector<uint8_t>(kSmallMessageSize)),
                kSendOptions);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(102),
                              std::vector<uint8_t>(kSmallMessageSize)),
                kSendOptions);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(103),
                              std::vector<uint8_t>(kSmallMessageSize)),
                kSendOptions);

  ConnectSockets(a, z);
  ExchangeMessages(a, z);

  std::vector<uint32_t> received_ppids;
  for (;;) {
    absl::optional<DcSctpMessage> msg = z.cb.ConsumeReceivedMessage();
    if (!msg.has_value()) {
      break;
    }
    received_ppids.push_back(*msg->ppid());
  }

  EXPECT_THAT(received_ppids, ElementsAre(101, 102, 103, 201, 301));
}

TEST(DcSctpSocketTest, LargeSentMessagesWithPrioWillArriveInSpecificOrder) {
  DcSctpOptions options = {.enable_message_interleaving = true};
  SocketUnderTest a("A", options);
  SocketUnderTest z("A", options);

  a.socket.SetStreamPriority(StreamID(1), StreamPriority(700));
  a.socket.SetStreamPriority(StreamID(2), StreamPriority(200));
  a.socket.SetStreamPriority(StreamID(3), StreamPriority(100));

  // Enqueue messages before connecting the socket, to ensure they aren't send
  // as soon as Send() is called.
  a.socket.Send(DcSctpMessage(StreamID(3), PPID(301),
                              std::vector<uint8_t>(kLargeMessageSize)),
                kSendOptions);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(101),
                              std::vector<uint8_t>(kLargeMessageSize)),
                kSendOptions);
  a.socket.Send(DcSctpMessage(StreamID(2), PPID(201),
                              std::vector<uint8_t>(kLargeMessageSize)),
                kSendOptions);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(102),
                              std::vector<uint8_t>(kLargeMessageSize)),
                kSendOptions);

  ConnectSockets(a, z);
  ExchangeMessages(a, z);

  EXPECT_THAT(GetReceivedMessagePpids(z), ElementsAre(101, 102, 201, 301));
}

TEST(DcSctpSocketTest, MessageWithHigherPrioWillInterruptLowerPrioMessage) {
  DcSctpOptions options = {.enable_message_interleaving = true};
  SocketUnderTest a("A", options);
  SocketUnderTest z("Z", options);

  ConnectSockets(a, z);

  a.socket.SetStreamPriority(StreamID(2), StreamPriority(128));
  a.socket.Send(DcSctpMessage(StreamID(2), PPID(201),
                              std::vector<uint8_t>(kLargeMessageSize)),
                kSendOptions);

  // Due to a non-zero initial congestion window, the message will already start
  // to send, but will not succeed to be sent completely before filling the
  // congestion window or stopping due to reaching how many packets that can be
  // sent at once (max burst). The important thing is that the entire message
  // doesn't get sent in full.

  // Now enqueue two messages; one small and one large higher priority message.
  a.socket.SetStreamPriority(StreamID(1), StreamPriority(512));
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(101),
                              std::vector<uint8_t>(kSmallMessageSize)),
                kSendOptions);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(102),
                              std::vector<uint8_t>(kLargeMessageSize)),
                kSendOptions);

  ExchangeMessages(a, z);

  EXPECT_THAT(GetReceivedMessagePpids(z), ElementsAre(101, 102, 201));
}

TEST(DcSctpSocketTest, LifecycleEventsAreGeneratedForAckedMessages) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");
  ConnectSockets(a, z);

  a.socket.Send(DcSctpMessage(StreamID(2), PPID(101),
                              std::vector<uint8_t>(kLargeMessageSize)),
                {.lifecycle_id = LifecycleId(41)});

  a.socket.Send(DcSctpMessage(StreamID(2), PPID(102),
                              std::vector<uint8_t>(kLargeMessageSize)),
                kSendOptions);

  a.socket.Send(DcSctpMessage(StreamID(2), PPID(103),
                              std::vector<uint8_t>(kLargeMessageSize)),
                {.lifecycle_id = LifecycleId(42)});

  EXPECT_CALL(a.cb, OnLifecycleMessageDelivered(LifecycleId(41)));
  EXPECT_CALL(a.cb, OnLifecycleEnd(LifecycleId(41)));
  EXPECT_CALL(a.cb, OnLifecycleMessageDelivered(LifecycleId(42)));
  EXPECT_CALL(a.cb, OnLifecycleEnd(LifecycleId(42)));
  ExchangeMessages(a, z);
  // In case of delayed ack.
  AdvanceTime(a, z, a.options.delayed_ack_max_timeout.ToTimeDelta());
  ExchangeMessages(a, z);

  EXPECT_THAT(GetReceivedMessagePpids(z), ElementsAre(101, 102, 103));
}

TEST(DcSctpSocketTest, LifecycleEventsForFailMaxRetransmissions) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");
  ConnectSockets(a, z);

  std::vector<uint8_t> payload(a.options.mtu - 100);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(51), payload),
                {
                    .max_retransmissions = 0,
                    .lifecycle_id = LifecycleId(1),
                });
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(52), payload),
                {
                    .max_retransmissions = 0,
                    .lifecycle_id = LifecycleId(2),
                });
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload),
                {
                    .max_retransmissions = 0,
                    .lifecycle_id = LifecycleId(3),
                });

  // First DATA
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // Second DATA (lost)
  a.cb.ConsumeSentPacket();

  EXPECT_CALL(a.cb, OnLifecycleMessageDelivered(LifecycleId(1)));
  EXPECT_CALL(a.cb, OnLifecycleEnd(LifecycleId(1)));
  EXPECT_CALL(a.cb, OnLifecycleMessageExpired(LifecycleId(2),
                                              /*maybe_delivered=*/true));
  EXPECT_CALL(a.cb, OnLifecycleEnd(LifecycleId(2)));
  EXPECT_CALL(a.cb, OnLifecycleMessageDelivered(LifecycleId(3)));
  EXPECT_CALL(a.cb, OnLifecycleEnd(LifecycleId(3)));
  ExchangeMessages(a, z);

  // Handle delayed SACK.
  AdvanceTime(a, z, a.options.delayed_ack_max_timeout.ToTimeDelta());
  ExchangeMessages(a, z);

  // The chunk is now NACKed. Let the RTO expire, to discard the message.
  AdvanceTime(a, z, a.options.rto_initial.ToTimeDelta());
  ExchangeMessages(a, z);

  // Handle delayed SACK.
  AdvanceTime(a, z, a.options.delayed_ack_max_timeout.ToTimeDelta());
  ExchangeMessages(a, z);

  EXPECT_THAT(GetReceivedMessagePpids(z), ElementsAre(51, 53));
}

TEST(DcSctpSocketTest, LifecycleEventsForExpiredMessageWithRetransmitLimit) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");
  ConnectSockets(a, z);

  // Will not be able to send it in full within the congestion window, but will
  // need to wait for SACKs to be received for more fragments to be sent.
  std::vector<uint8_t> payload(kLargeMessageSize);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(51), payload),
                {
                    .max_retransmissions = 0,
                    .lifecycle_id = LifecycleId(1),
                });

  // First DATA
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());
  // Second DATA (lost)
  a.cb.ConsumeSentPacket();

  EXPECT_CALL(a.cb, OnLifecycleMessageExpired(LifecycleId(1),
                                              /*maybe_delivered=*/false));
  EXPECT_CALL(a.cb, OnLifecycleEnd(LifecycleId(1)));
  ExchangeMessages(a, z);

  EXPECT_THAT(GetReceivedMessagePpids(z), IsEmpty());
}

TEST(DcSctpSocketTest, LifecycleEventsForExpiredMessageWithLifetimeLimit) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  // Send it before the socket is connected, to prevent it from being sent too
  // quickly. The idea is that it should be expired before even attempting to
  // send it in full.
  std::vector<uint8_t> payload(kSmallMessageSize);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(51), payload),
                {
                    .lifetime = DurationMs(100),
                    .lifecycle_id = LifecycleId(1),
                });

  AdvanceTime(a, z, TimeDelta::Millis(200));

  EXPECT_CALL(a.cb, OnLifecycleMessageExpired(LifecycleId(1),
                                              /*maybe_delivered=*/false));
  EXPECT_CALL(a.cb, OnLifecycleEnd(LifecycleId(1)));
  ConnectSockets(a, z);
  ExchangeMessages(a, z);

  EXPECT_THAT(GetReceivedMessagePpids(z), IsEmpty());
}

TEST_P(DcSctpSocketParametrizedTest, ExposesTheNumberOfNegotiatedStreams) {
  DcSctpOptions options_a = {
      .announced_maximum_incoming_streams = 12,
      .announced_maximum_outgoing_streams = 45,
  };
  SocketUnderTest a("A", options_a);

  DcSctpOptions options_z = {
      .announced_maximum_incoming_streams = 23,
      .announced_maximum_outgoing_streams = 34,
  };
  auto z = std::make_unique<SocketUnderTest>("Z", options_z);

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  ASSERT_HAS_VALUE_AND_ASSIGN(Metrics metrics_a, a.socket.GetMetrics());
  EXPECT_EQ(metrics_a.negotiated_maximum_incoming_streams, 12);
  EXPECT_EQ(metrics_a.negotiated_maximum_outgoing_streams, 23);

  ASSERT_HAS_VALUE_AND_ASSIGN(Metrics metrics_z, z->socket.GetMetrics());
  EXPECT_EQ(metrics_z.negotiated_maximum_incoming_streams, 23);
  EXPECT_EQ(metrics_z.negotiated_maximum_outgoing_streams, 12);
}

TEST(DcSctpSocketTest, ResetStreamsDeferred) {
  // Guaranteed to be fragmented into two fragments.
  constexpr size_t kTwoFragmentsSize = DcSctpOptions::kMaxSafeMTUSize + 100;

  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  ConnectSockets(a, z);

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53),
                              std::vector<uint8_t>(kTwoFragmentsSize)),
                {});
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(54),
                              std::vector<uint8_t>(kSmallMessageSize)),
                {});

  a.socket.ResetStreams(std::vector<StreamID>({StreamID(1)}));

  auto data1 = a.cb.ConsumeSentPacket();
  auto data2 = a.cb.ConsumeSentPacket();
  auto data3 = a.cb.ConsumeSentPacket();
  auto reconfig = a.cb.ConsumeSentPacket();

  EXPECT_THAT(
      data1,
      HasChunks(ElementsAre(IsDataChunk(Property(&DataChunk::ssn, SSN(0))))));
  EXPECT_THAT(
      data2,
      HasChunks(ElementsAre(IsDataChunk(Property(&DataChunk::ssn, SSN(0))))));
  EXPECT_THAT(
      data3,
      HasChunks(ElementsAre(IsDataChunk(Property(&DataChunk::ssn, SSN(1))))));
  EXPECT_THAT(reconfig, HasChunks(ElementsAre(IsReConfig(HasParameters(
                            ElementsAre(IsOutgoingResetRequest(Property(
                                &OutgoingSSNResetRequestParameter::stream_ids,
                                ElementsAre(StreamID(1))))))))));

  // Receive them slightly out of order to make stream resetting deferred.
  z.socket.ReceivePacket(reconfig);

  z.socket.ReceivePacket(data1);
  z.socket.ReceivePacket(data2);
  z.socket.ReceivePacket(data3);

  absl::optional<DcSctpMessage> msg1 = z.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg1.has_value());
  EXPECT_EQ(msg1->stream_id(), StreamID(1));
  EXPECT_EQ(msg1->ppid(), PPID(53));
  EXPECT_EQ(msg1->payload().size(), kTwoFragmentsSize);

  absl::optional<DcSctpMessage> msg2 = z.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg2.has_value());
  EXPECT_EQ(msg2->stream_id(), StreamID(1));
  EXPECT_EQ(msg2->ppid(), PPID(54));
  EXPECT_EQ(msg2->payload().size(), kSmallMessageSize);

  EXPECT_CALL(a.cb, OnStreamsResetPerformed(ElementsAre(StreamID(1))));
  ExchangeMessages(a, z);

  // Z sent "in progress", which will make A buffer packets until it's sure
  // that the reconfiguration has been applied. A will retry - wait for that.
  AdvanceTime(a, z, a.options.rto_initial.ToTimeDelta());

  auto reconfig2 = a.cb.ConsumeSentPacket();
  EXPECT_THAT(reconfig2, HasChunks(ElementsAre(IsReConfig(HasParameters(
                             ElementsAre(IsOutgoingResetRequest(Property(
                                 &OutgoingSSNResetRequestParameter::stream_ids,
                                 ElementsAre(StreamID(1))))))))));
  EXPECT_CALL(z.cb, OnIncomingStreamsReset(ElementsAre(StreamID(1))));
  z.socket.ReceivePacket(reconfig2);

  auto reconfig3 = z.cb.ConsumeSentPacket();
  EXPECT_THAT(reconfig3, HasChunks(ElementsAre(IsReConfig(HasParameters(
                             ElementsAre(IsReconfigurationResponse(Property(
                                 &ReconfigurationResponseParameter::result,
                                 ReconfigurationResponseParameter::Result::
                                     kSuccessPerformed))))))));
  a.socket.ReceivePacket(reconfig3);

  EXPECT_THAT(
      data1,
      HasChunks(ElementsAre(IsDataChunk(Property(&DataChunk::ssn, SSN(0))))));
  EXPECT_THAT(
      data2,
      HasChunks(ElementsAre(IsDataChunk(Property(&DataChunk::ssn, SSN(0))))));
  EXPECT_THAT(
      data3,
      HasChunks(ElementsAre(IsDataChunk(Property(&DataChunk::ssn, SSN(1))))));
  EXPECT_THAT(reconfig, HasChunks(ElementsAre(IsReConfig(HasParameters(
                            ElementsAre(IsOutgoingResetRequest(Property(
                                &OutgoingSSNResetRequestParameter::stream_ids,
                                ElementsAre(StreamID(1))))))))));

  // Send a new message after the stream has been reset.
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(55),
                              std::vector<uint8_t>(kSmallMessageSize)),
                {});
  ExchangeMessages(a, z);

  absl::optional<DcSctpMessage> msg3 = z.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg3.has_value());
  EXPECT_EQ(msg3->stream_id(), StreamID(1));
  EXPECT_EQ(msg3->ppid(), PPID(55));
  EXPECT_EQ(msg3->payload().size(), kSmallMessageSize);
}

TEST(DcSctpSocketTest, ResetStreamsWithPausedSenderResumesWhenPerformed) {
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  ConnectSockets(a, z);

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(51),
                              std::vector<uint8_t>(kSmallMessageSize)),
                {});

  a.socket.ResetStreams(std::vector<StreamID>({StreamID(1)}));

  // Will be queued, as the stream has an outstanding reset operation.
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(52),
                              std::vector<uint8_t>(kSmallMessageSize)),
                {});

  EXPECT_CALL(a.cb, OnStreamsResetPerformed(ElementsAre(StreamID(1))));
  EXPECT_CALL(z.cb, OnIncomingStreamsReset(ElementsAre(StreamID(1))));
  ExchangeMessages(a, z);

  absl::optional<DcSctpMessage> msg1 = z.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg1.has_value());
  EXPECT_EQ(msg1->stream_id(), StreamID(1));
  EXPECT_EQ(msg1->ppid(), PPID(51));
  EXPECT_EQ(msg1->payload().size(), kSmallMessageSize);

  absl::optional<DcSctpMessage> msg2 = z.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg2.has_value());
  EXPECT_EQ(msg2->stream_id(), StreamID(1));
  EXPECT_EQ(msg2->ppid(), PPID(52));
  EXPECT_EQ(msg2->payload().size(), kSmallMessageSize);
}

TEST_P(DcSctpSocketParametrizedTest, ZeroChecksumMetricsAreSet) {
  std::vector<std::pair<bool, bool>> combinations = {
      {false, false}, {false, true}, {true, false}, {true, true}};
  for (const auto& [a_enable, z_enable] : combinations) {
    DcSctpOptions a_options = {
        .zero_checksum_alternate_error_detection_method =
            a_enable
                ? ZeroChecksumAlternateErrorDetectionMethod::LowerLayerDtls()
                : ZeroChecksumAlternateErrorDetectionMethod::None()};
    DcSctpOptions z_options = {
        .zero_checksum_alternate_error_detection_method =
            z_enable
                ? ZeroChecksumAlternateErrorDetectionMethod::LowerLayerDtls()
                : ZeroChecksumAlternateErrorDetectionMethod::None()};

    SocketUnderTest a("A", a_options);
    auto z = std::make_unique<SocketUnderTest>("Z", z_options);

    ConnectSockets(a, *z);
    z = MaybeHandoverSocket(std::move(z));

    EXPECT_EQ(a.socket.GetMetrics()->uses_zero_checksum, a_enable && z_enable);
    EXPECT_EQ(z->socket.GetMetrics()->uses_zero_checksum, a_enable && z_enable);
  }
}

TEST(DcSctpSocketTest, AlwaysSendsInitWithNonZeroChecksum) {
  DcSctpOptions options = {
      .zero_checksum_alternate_error_detection_method =
          ZeroChecksumAlternateErrorDetectionMethod::LowerLayerDtls()};
  SocketUnderTest a("A", options);

  a.socket.Connect();
  std::vector<uint8_t> data = a.cb.ConsumeSentPacket();
  ASSERT_HAS_VALUE_AND_ASSIGN(SctpPacket packet,
                              SctpPacket::Parse(data, options));
  EXPECT_THAT(packet.descriptors(),
              ElementsAre(testing::Field(&SctpPacket::ChunkDescriptor::type,
                                         InitChunk::kType)));
  EXPECT_THAT(packet.common_header().checksum, Not(Eq(0u)));
}

TEST(DcSctpSocketTest, MaySendInitAckWithZeroChecksum) {
  DcSctpOptions options = {
      .zero_checksum_alternate_error_detection_method =
          ZeroChecksumAlternateErrorDetectionMethod::LowerLayerDtls()};
  SocketUnderTest a("A", options);
  SocketUnderTest z("Z", options);

  a.socket.Connect();
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());  // INIT

  std::vector<uint8_t> data = z.cb.ConsumeSentPacket();
  ASSERT_HAS_VALUE_AND_ASSIGN(SctpPacket packet,
                              SctpPacket::Parse(data, options));
  EXPECT_THAT(packet.descriptors(),
              ElementsAre(testing::Field(&SctpPacket::ChunkDescriptor::type,
                                         InitAckChunk::kType)));
  EXPECT_THAT(packet.common_header().checksum, 0u);
}

TEST(DcSctpSocketTest, AlwaysSendsCookieEchoWithNonZeroChecksum) {
  DcSctpOptions options = {
      .zero_checksum_alternate_error_detection_method =
          ZeroChecksumAlternateErrorDetectionMethod::LowerLayerDtls()};
  SocketUnderTest a("A", options);
  SocketUnderTest z("Z", options);

  a.socket.Connect();
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());  // INIT
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());  // INIT-ACK

  std::vector<uint8_t> data = a.cb.ConsumeSentPacket();
  ASSERT_HAS_VALUE_AND_ASSIGN(SctpPacket packet,
                              SctpPacket::Parse(data, options));
  EXPECT_THAT(packet.descriptors(),
              ElementsAre(testing::Field(&SctpPacket::ChunkDescriptor::type,
                                         CookieEchoChunk::kType)));
  EXPECT_THAT(packet.common_header().checksum, Not(Eq(0u)));
}

TEST(DcSctpSocketTest, SendsCookieAckWithZeroChecksum) {
  DcSctpOptions options = {
      .zero_checksum_alternate_error_detection_method =
          ZeroChecksumAlternateErrorDetectionMethod::LowerLayerDtls()};
  SocketUnderTest a("A", options);
  SocketUnderTest z("Z", options);

  a.socket.Connect();
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());  // INIT
  a.socket.ReceivePacket(z.cb.ConsumeSentPacket());  // INIT-ACK
  z.socket.ReceivePacket(a.cb.ConsumeSentPacket());  // COOKIE-ECHO

  std::vector<uint8_t> data = z.cb.ConsumeSentPacket();
  ASSERT_HAS_VALUE_AND_ASSIGN(SctpPacket packet,
                              SctpPacket::Parse(data, options));
  EXPECT_THAT(packet.descriptors(),
              ElementsAre(testing::Field(&SctpPacket::ChunkDescriptor::type,
                                         CookieAckChunk::kType)));
  EXPECT_THAT(packet.common_header().checksum, 0u);
}

TEST_P(DcSctpSocketParametrizedTest, SendsDataWithZeroChecksum) {
  DcSctpOptions options = {
      .zero_checksum_alternate_error_detection_method =
          ZeroChecksumAlternateErrorDetectionMethod::LowerLayerDtls()};
  SocketUnderTest a("A", options);
  auto z = std::make_unique<SocketUnderTest>("Z", options);

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  std::vector<uint8_t> payload(a.options.mtu - 100);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), {});

  std::vector<uint8_t> data = a.cb.ConsumeSentPacket();
  z->socket.ReceivePacket(data);
  ASSERT_HAS_VALUE_AND_ASSIGN(SctpPacket packet,
                              SctpPacket::Parse(data, options));
  EXPECT_THAT(packet.descriptors(),
              ElementsAre(testing::Field(&SctpPacket::ChunkDescriptor::type,
                                         DataChunk::kType)));
  EXPECT_THAT(packet.common_header().checksum, 0u);

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST_P(DcSctpSocketParametrizedTest, AllPacketsAfterConnectHaveZeroChecksum) {
  DcSctpOptions options = {
      .zero_checksum_alternate_error_detection_method =
          ZeroChecksumAlternateErrorDetectionMethod::LowerLayerDtls()};
  SocketUnderTest a("A", options);
  auto z = std::make_unique<SocketUnderTest>("Z", options);

  ConnectSockets(a, *z);
  z = MaybeHandoverSocket(std::move(z));

  // Send large messages in both directions, and verify that they arrive and
  // that every packet has zero checksum.
  std::vector<uint8_t> payload(kLargeMessageSize);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), kSendOptions);
  z->socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), kSendOptions);

  for (;;) {
    if (auto data = a.cb.ConsumeSentPacket(); !data.empty()) {
      ASSERT_HAS_VALUE_AND_ASSIGN(SctpPacket packet,
                                  SctpPacket::Parse(data, options));
      EXPECT_THAT(packet.common_header().checksum, 0u);
      z->socket.ReceivePacket(std::move(data));

    } else if (auto data = z->cb.ConsumeSentPacket(); !data.empty()) {
      ASSERT_HAS_VALUE_AND_ASSIGN(SctpPacket packet,
                                  SctpPacket::Parse(data, options));
      EXPECT_THAT(packet.common_header().checksum, 0u);
      a.socket.ReceivePacket(std::move(data));

    } else {
      break;
    }
  }

  absl::optional<DcSctpMessage> msg1 = z->cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg1.has_value());
  EXPECT_THAT(msg1->payload(), SizeIs(kLargeMessageSize));

  absl::optional<DcSctpMessage> msg2 = a.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg2.has_value());
  EXPECT_THAT(msg2->payload(), SizeIs(kLargeMessageSize));

  MaybeHandoverSocketAndSendMessage(a, std::move(z));
}

TEST(DcSctpSocketTest, HandlesForwardTsnOutOfOrderWithStreamResetting) {
  // This test ensures that receiving FORWARD-TSN and RECONFIG out of order is
  // handled correctly.
  SocketUnderTest a("A", {.heartbeat_interval = DurationMs(0)});
  SocketUnderTest z("Z", {.heartbeat_interval = DurationMs(0)});

  ConnectSockets(a, z);
  std::vector<uint8_t> payload(kSmallMessageSize);
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(51), payload),
                {
                    .max_retransmissions = 0,
                });

  // Packet is lost.
  EXPECT_THAT(a.cb.ConsumeSentPacket(),
              HasChunks(ElementsAre(
                  IsDataChunk(AllOf(Property(&DataChunk::ssn, SSN(0)),
                                    Property(&DataChunk::ppid, PPID(51)))))));
  AdvanceTime(a, z, a.options.rto_initial.ToTimeDelta());

  auto fwd_tsn_packet = a.cb.ConsumeSentPacket();
  EXPECT_THAT(fwd_tsn_packet,
              HasChunks(ElementsAre(IsChunkType(ForwardTsnChunk::kType))));
  // Reset stream 1
  a.socket.ResetStreams(std::vector<StreamID>({StreamID(1)}));
  auto reconfig_packet = a.cb.ConsumeSentPacket();
  EXPECT_THAT(reconfig_packet,
              HasChunks(ElementsAre(IsChunkType(ReConfigChunk::kType))));

  // These two packets are received in the wrong order.
  z.socket.ReceivePacket(reconfig_packet);
  z.socket.ReceivePacket(fwd_tsn_packet);
  ExchangeMessagesAndAdvanceTime(a, z);

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(52), payload), {});
  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53), payload), {});

  auto data_packet_2 = a.cb.ConsumeSentPacket();
  auto data_packet_3 = a.cb.ConsumeSentPacket();
  EXPECT_THAT(data_packet_2, HasChunks(ElementsAre(IsDataChunk(AllOf(
                                 Property(&DataChunk::ssn, SSN(0)),
                                 Property(&DataChunk::ppid, PPID(52)))))));
  EXPECT_THAT(data_packet_3, HasChunks(ElementsAre(IsDataChunk(AllOf(
                                 Property(&DataChunk::ssn, SSN(1)),
                                 Property(&DataChunk::ppid, PPID(53)))))));

  z.socket.ReceivePacket(data_packet_2);
  z.socket.ReceivePacket(data_packet_3);
  ASSERT_THAT(z.cb.ConsumeReceivedMessage(),
              testing::Optional(Property(&DcSctpMessage::ppid, PPID(52))));
  ASSERT_THAT(z.cb.ConsumeReceivedMessage(),
              testing::Optional(Property(&DcSctpMessage::ppid, PPID(53))));
}

TEST(DcSctpSocketTest, ResentInitHasSameParameters) {
  // If an INIT chunk has to be resent (due to INIT_ACK not received in time),
  // the resent INIT must have the same properties as the original one.
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  a.socket.Connect();
  auto packet_1 = a.cb.ConsumeSentPacket();

  // Times out, INIT is re-sent.
  AdvanceTime(a, z, a.options.t1_init_timeout.ToTimeDelta());
  auto packet_2 = a.cb.ConsumeSentPacket();

  ASSERT_HAS_VALUE_AND_ASSIGN(SctpPacket init_packet_1,
                              SctpPacket::Parse(packet_1, z.options));
  ASSERT_HAS_VALUE_AND_ASSIGN(
      InitChunk init_chunk_1,
      InitChunk::Parse(init_packet_1.descriptors()[0].data));

  ASSERT_HAS_VALUE_AND_ASSIGN(SctpPacket init_packet_2,
                              SctpPacket::Parse(packet_2, z.options));
  ASSERT_HAS_VALUE_AND_ASSIGN(
      InitChunk init_chunk_2,
      InitChunk::Parse(init_packet_2.descriptors()[0].data));

  EXPECT_EQ(init_chunk_1.initial_tsn(), init_chunk_2.initial_tsn());
  EXPECT_EQ(init_chunk_1.initiate_tag(), init_chunk_2.initiate_tag());
}

TEST(DcSctpSocketTest, ResentInitAckHasDifferentParameters) {
  // For every INIT, an INIT_ACK is produced. Verify that the socket doesn't
  // maintain any state by ensuring that two created INIT_ACKs for the same
  // received INIT are different.
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  a.socket.Connect();
  auto packet_1 = a.cb.ConsumeSentPacket();
  EXPECT_THAT(packet_1, HasChunks(ElementsAre(IsChunkType(InitChunk::kType))));

  z.socket.ReceivePacket(packet_1);
  auto packet_2 = z.cb.ConsumeSentPacket();
  z.socket.ReceivePacket(packet_1);
  auto packet_3 = z.cb.ConsumeSentPacket();

  EXPECT_THAT(packet_2,
              HasChunks(ElementsAre(IsChunkType(InitAckChunk::kType))));
  EXPECT_THAT(packet_3,
              HasChunks(ElementsAre(IsChunkType(InitAckChunk::kType))));

  ASSERT_HAS_VALUE_AND_ASSIGN(SctpPacket init_ack_packet_1,
                              SctpPacket::Parse(packet_2, z.options));
  ASSERT_HAS_VALUE_AND_ASSIGN(
      InitAckChunk init_ack_chunk_1,
      InitAckChunk::Parse(init_ack_packet_1.descriptors()[0].data));

  ASSERT_HAS_VALUE_AND_ASSIGN(SctpPacket init_ack_packet_2,
                              SctpPacket::Parse(packet_3, z.options));
  ASSERT_HAS_VALUE_AND_ASSIGN(
      InitAckChunk init_ack_chunk_2,
      InitAckChunk::Parse(init_ack_packet_2.descriptors()[0].data));

  EXPECT_NE(init_ack_chunk_1.initiate_tag(), init_ack_chunk_2.initiate_tag());
  EXPECT_NE(init_ack_chunk_1.initial_tsn(), init_ack_chunk_2.initial_tsn());
}

TEST(DcSctpSocketResendInitTest, ConnectionCanContinueFromFirstInitAck) {
  // If an INIT chunk has to be resent (due to INIT_ACK not received in time),
  // another INIT will be sent, and if both INITs were actually received, both
  // will be responded to by an INIT_ACK. While these two INIT_ACKs may have
  // different parameters, the connection must be able to finish with the cookie
  // (as replied to using COOKIE_ECHO) from either INIT_ACK.
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53),
                              std::vector<uint8_t>(kLargeMessageSize)),
                kSendOptions);
  a.socket.Connect();
  auto init_1 = a.cb.ConsumeSentPacket();

  // Times out, INIT is re-sent.
  AdvanceTime(a, z, a.options.t1_init_timeout.ToTimeDelta());
  auto init_2 = a.cb.ConsumeSentPacket();

  EXPECT_THAT(init_1, HasChunks(ElementsAre(IsChunkType(InitChunk::kType))));
  EXPECT_THAT(init_2, HasChunks(ElementsAre(IsChunkType(InitChunk::kType))));

  z.socket.ReceivePacket(init_1);
  z.socket.ReceivePacket(init_2);
  auto init_ack_1 = z.cb.ConsumeSentPacket();
  auto init_ack_2 = z.cb.ConsumeSentPacket();
  EXPECT_THAT(init_ack_1,
              HasChunks(ElementsAre(IsChunkType(InitAckChunk::kType))));
  EXPECT_THAT(init_ack_2,
              HasChunks(ElementsAre(IsChunkType(InitAckChunk::kType))));

  a.socket.ReceivePacket(init_ack_1);
  // Then let the rest continue.
  ExchangeMessages(a, z);

  absl::optional<DcSctpMessage> msg = z.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg.has_value());
  EXPECT_EQ(msg->stream_id(), StreamID(1));
  EXPECT_THAT(msg->payload(), SizeIs(kLargeMessageSize));
}

TEST(DcSctpSocketResendInitTest, ConnectionCanContinueFromSecondInitAck) {
  // Just as above, but discarding the first INIT_ACK.
  SocketUnderTest a("A");
  SocketUnderTest z("Z");

  a.socket.Send(DcSctpMessage(StreamID(1), PPID(53),
                              std::vector<uint8_t>(kLargeMessageSize)),
                kSendOptions);
  a.socket.Connect();
  auto init_1 = a.cb.ConsumeSentPacket();

  // Times out, INIT is re-sent.
  AdvanceTime(a, z, a.options.t1_init_timeout.ToTimeDelta());
  auto init_2 = a.cb.ConsumeSentPacket();

  EXPECT_THAT(init_1, HasChunks(ElementsAre(IsChunkType(InitChunk::kType))));
  EXPECT_THAT(init_2, HasChunks(ElementsAre(IsChunkType(InitChunk::kType))));

  z.socket.ReceivePacket(init_1);
  z.socket.ReceivePacket(init_2);
  auto init_ack_1 = z.cb.ConsumeSentPacket();
  auto init_ack_2 = z.cb.ConsumeSentPacket();
  EXPECT_THAT(init_ack_1,
              HasChunks(ElementsAre(IsChunkType(InitAckChunk::kType))));
  EXPECT_THAT(init_ack_2,
              HasChunks(ElementsAre(IsChunkType(InitAckChunk::kType))));

  a.socket.ReceivePacket(init_ack_2);
  // Then let the rest continue.
  ExchangeMessages(a, z);

  absl::optional<DcSctpMessage> msg = z.cb.ConsumeReceivedMessage();
  ASSERT_TRUE(msg.has_value());
  EXPECT_EQ(msg->stream_id(), StreamID(1));
  EXPECT_THAT(msg->payload(), SizeIs(kLargeMessageSize));
}

}  // namespace
}  // namespace dcsctp

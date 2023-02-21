/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/fuzzers/dcsctp_fuzzers.h"

#include <string>
#include <utility>
#include <vector>

#include "net/dcsctp/common/math.h"
#include "net/dcsctp/packet/chunk/cookie_ack_chunk.h"
#include "net/dcsctp/packet/chunk/cookie_echo_chunk.h"
#include "net/dcsctp/packet/chunk/data_chunk.h"
#include "net/dcsctp/packet/chunk/forward_tsn_chunk.h"
#include "net/dcsctp/packet/chunk/forward_tsn_common.h"
#include "net/dcsctp/packet/chunk/shutdown_chunk.h"
#include "net/dcsctp/packet/error_cause/protocol_violation_cause.h"
#include "net/dcsctp/packet/error_cause/user_initiated_abort_cause.h"
#include "net/dcsctp/packet/parameter/forward_tsn_supported_parameter.h"
#include "net/dcsctp/packet/parameter/outgoing_ssn_reset_request_parameter.h"
#include "net/dcsctp/packet/parameter/state_cookie_parameter.h"
#include "net/dcsctp/public/dcsctp_message.h"
#include "net/dcsctp/public/types.h"
#include "net/dcsctp/socket/dcsctp_socket.h"
#include "net/dcsctp/socket/state_cookie.h"
#include "rtc_base/logging.h"

namespace dcsctp {
namespace dcsctp_fuzzers {
namespace {
static constexpr int kRandomValue = FuzzerCallbacks::kRandomValue;
static constexpr size_t kMinInputLength = 5;
static constexpr size_t kMaxInputLength = 1024;

// A starting state for the socket, when fuzzing.
enum class StartingState : int {
  kConnectNotCalled,
  // When socket initiating Connect
  kConnectCalled,
  kReceivedInitAck,
  kReceivedCookieAck,
  // When socket initiating Shutdown
  kShutdownCalled,
  kReceivedShutdownAck,
  // When peer socket initiated Connect
  kReceivedInit,
  kReceivedCookieEcho,
  // When peer initiated Shutdown
  kReceivedShutdown,
  kReceivedShutdownComplete,
  kNumberOfStates,
};

// State about the current fuzzing iteration
class FuzzState {
 public:
  explicit FuzzState(rtc::ArrayView<const uint8_t> data) : data_(data) {}

  uint8_t GetByte() {
    uint8_t value = 0;
    if (offset_ < data_.size()) {
      value = data_[offset_];
      ++offset_;
    }
    return value;
  }

  TSN GetNextTSN() { return TSN(tsn_++); }
  MID GetNextMID() { return MID(mid_++); }

  bool empty() const { return offset_ >= data_.size(); }

 private:
  uint32_t tsn_ = kRandomValue;
  uint32_t mid_ = 0;
  rtc::ArrayView<const uint8_t> data_;
  size_t offset_ = 0;
};

void SetSocketState(DcSctpSocketInterface& socket,
                    FuzzerCallbacks& socket_cb,
                    StartingState state) {
  // We'll use another temporary peer socket for the establishment.
  FuzzerCallbacks peer_cb;
  DcSctpSocket peer("peer", peer_cb, nullptr, {});

  switch (state) {
    case StartingState::kConnectNotCalled:
      return;
    case StartingState::kConnectCalled:
      socket.Connect();
      return;
    case StartingState::kReceivedInitAck:
      socket.Connect();
      peer.ReceivePacket(socket_cb.ConsumeSentPacket());  // INIT
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // INIT_ACK
      return;
    case StartingState::kReceivedCookieAck:
      socket.Connect();
      peer.ReceivePacket(socket_cb.ConsumeSentPacket());  // INIT
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // INIT_ACK
      peer.ReceivePacket(socket_cb.ConsumeSentPacket());  // COOKIE_ECHO
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // COOKIE_ACK
      return;
    case StartingState::kShutdownCalled:
      socket.Connect();
      peer.ReceivePacket(socket_cb.ConsumeSentPacket());  // INIT
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // INIT_ACK
      peer.ReceivePacket(socket_cb.ConsumeSentPacket());  // COOKIE_ECHO
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // COOKIE_ACK
      socket.Shutdown();
      return;
    case StartingState::kReceivedShutdownAck:
      socket.Connect();
      peer.ReceivePacket(socket_cb.ConsumeSentPacket());  // INIT
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // INIT_ACK
      peer.ReceivePacket(socket_cb.ConsumeSentPacket());  // COOKIE_ECHO
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // COOKIE_ACK
      socket.Shutdown();
      peer.ReceivePacket(socket_cb.ConsumeSentPacket());  // SHUTDOWN
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // SHUTDOWN_ACK
      return;
    case StartingState::kReceivedInit:
      peer.Connect();
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // INIT
      return;
    case StartingState::kReceivedCookieEcho:
      peer.Connect();
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // INIT
      peer.ReceivePacket(socket_cb.ConsumeSentPacket());  // INIT_ACK
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // COOKIE_ECHO
      return;
    case StartingState::kReceivedShutdown:
      socket.Connect();
      peer.ReceivePacket(socket_cb.ConsumeSentPacket());  // INIT
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // INIT_ACK
      peer.ReceivePacket(socket_cb.ConsumeSentPacket());  // COOKIE_ECHO
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // COOKIE_ACK
      peer.Shutdown();
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // SHUTDOWN
      return;
    case StartingState::kReceivedShutdownComplete:
      socket.Connect();
      peer.ReceivePacket(socket_cb.ConsumeSentPacket());  // INIT
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // INIT_ACK
      peer.ReceivePacket(socket_cb.ConsumeSentPacket());  // COOKIE_ECHO
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // COOKIE_ACK
      peer.Shutdown();
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // SHUTDOWN
      peer.ReceivePacket(socket_cb.ConsumeSentPacket());  // SHUTDOWN_ACK
      socket.ReceivePacket(peer_cb.ConsumeSentPacket());  // SHUTDOWN_COMPLETE
      return;
    case StartingState::kNumberOfStates:
      RTC_CHECK(false);
      return;
  }
}

void MakeDataChunk(FuzzState& state, SctpPacket::Builder& b) {
  DataChunk::Options options;
  options.is_unordered = IsUnordered(state.GetByte() != 0);
  options.is_beginning = Data::IsBeginning(state.GetByte() != 0);
  options.is_end = Data::IsEnd(state.GetByte() != 0);
  b.Add(DataChunk(state.GetNextTSN(), StreamID(state.GetByte()),
                  SSN(state.GetByte()), PPID(53), std::vector<uint8_t>(10),
                  options));
}

void MakeInitChunk(FuzzState& state, SctpPacket::Builder& b) {
  Parameters::Builder builder;
  builder.Add(ForwardTsnSupportedParameter());

  b.Add(InitChunk(VerificationTag(kRandomValue), 10000, 1000, 1000,
                  TSN(kRandomValue), builder.Build()));
}

void MakeInitAckChunk(FuzzState& state, SctpPacket::Builder& b) {
  Parameters::Builder builder;
  builder.Add(ForwardTsnSupportedParameter());

  uint8_t state_cookie[] = {1, 2, 3, 4, 5};
  Parameters::Builder params_builder =
      Parameters::Builder().Add(StateCookieParameter(state_cookie));

  b.Add(InitAckChunk(VerificationTag(kRandomValue), 10000, 1000, 1000,
                     TSN(kRandomValue), builder.Build()));
}

void MakeSackChunk(FuzzState& state, SctpPacket::Builder& b) {
  std::vector<SackChunk::GapAckBlock> gap_ack_blocks;
  uint16_t last_end = 0;
  while (gap_ack_blocks.size() < 20) {
    uint8_t delta_start = state.GetByte();
    if (delta_start < 0x80) {
      break;
    }
    uint8_t delta_end = state.GetByte();

    uint16_t start = last_end + delta_start;
    uint16_t end = start + delta_end;
    last_end = end;
    gap_ack_blocks.emplace_back(start, end);
  }

  TSN cum_ack_tsn(kRandomValue + state.GetByte());
  b.Add(SackChunk(cum_ack_tsn, 10000, std::move(gap_ack_blocks), {}));
}

void MakeHeartbeatRequestChunk(FuzzState& state, SctpPacket::Builder& b) {
  uint8_t info[] = {1, 2, 3, 4, 5};
  b.Add(HeartbeatRequestChunk(
      Parameters::Builder().Add(HeartbeatInfoParameter(info)).Build()));
}

void MakeHeartbeatAckChunk(FuzzState& state, SctpPacket::Builder& b) {
  std::vector<uint8_t> info(8);
  b.Add(HeartbeatRequestChunk(
      Parameters::Builder().Add(HeartbeatInfoParameter(info)).Build()));
}

void MakeAbortChunk(FuzzState& state, SctpPacket::Builder& b) {
  b.Add(AbortChunk(
      /*filled_in_verification_tag=*/true,
      Parameters::Builder().Add(UserInitiatedAbortCause("Fuzzing")).Build()));
}

void MakeErrorChunk(FuzzState& state, SctpPacket::Builder& b) {
  b.Add(ErrorChunk(
      Parameters::Builder().Add(ProtocolViolationCause("Fuzzing")).Build()));
}

void MakeCookieEchoChunk(FuzzState& state, SctpPacket::Builder& b) {
  std::vector<uint8_t> cookie(StateCookie::kCookieSize);
  b.Add(CookieEchoChunk(cookie));
}

void MakeCookieAckChunk(FuzzState& state, SctpPacket::Builder& b) {
  b.Add(CookieAckChunk());
}

void MakeShutdownChunk(FuzzState& state, SctpPacket::Builder& b) {
  b.Add(ShutdownChunk(state.GetNextTSN()));
}

void MakeShutdownAckChunk(FuzzState& state, SctpPacket::Builder& b) {
  b.Add(ShutdownAckChunk());
}

void MakeShutdownCompleteChunk(FuzzState& state, SctpPacket::Builder& b) {
  b.Add(ShutdownCompleteChunk(false));
}

void MakeReConfigChunk(FuzzState& state, SctpPacket::Builder& b) {
  std::vector<StreamID> streams = {StreamID(state.GetByte())};
  Parameters::Builder params_builder =
      Parameters::Builder().Add(OutgoingSSNResetRequestParameter(
          ReconfigRequestSN(kRandomValue), ReconfigRequestSN(kRandomValue),
          state.GetNextTSN(), streams));
  b.Add(ReConfigChunk(params_builder.Build()));
}

void MakeForwardTsnChunk(FuzzState& state, SctpPacket::Builder& b) {
  std::vector<ForwardTsnChunk::SkippedStream> skipped_streams;
  for (;;) {
    uint8_t stream = state.GetByte();
    if (skipped_streams.size() > 20 || stream < 0x80) {
      break;
    }
    skipped_streams.emplace_back(StreamID(stream), SSN(state.GetByte()));
  }
  b.Add(ForwardTsnChunk(state.GetNextTSN(), std::move(skipped_streams)));
}

void MakeIDataChunk(FuzzState& state, SctpPacket::Builder& b) {
  DataChunk::Options options;
  options.is_unordered = IsUnordered(state.GetByte() != 0);
  options.is_beginning = Data::IsBeginning(state.GetByte() != 0);
  options.is_end = Data::IsEnd(state.GetByte() != 0);
  b.Add(IDataChunk(state.GetNextTSN(), StreamID(state.GetByte()),
                   state.GetNextMID(), PPID(53), FSN(0),
                   std::vector<uint8_t>(10), options));
}

void MakeIForwardTsnChunk(FuzzState& state, SctpPacket::Builder& b) {
  std::vector<ForwardTsnChunk::SkippedStream> skipped_streams;
  for (;;) {
    uint8_t stream = state.GetByte();
    if (skipped_streams.size() > 20 || stream < 0x80) {
      break;
    }
    skipped_streams.emplace_back(StreamID(stream), SSN(state.GetByte()));
  }
  b.Add(IForwardTsnChunk(state.GetNextTSN(), std::move(skipped_streams)));
}

class RandomFuzzedChunk : public Chunk {
 public:
  explicit RandomFuzzedChunk(FuzzState& state) : state_(state) {}

  void SerializeTo(std::vector<uint8_t>& out) const override {
    size_t bytes = state_.GetByte();
    for (size_t i = 0; i < bytes; ++i) {
      out.push_back(state_.GetByte());
    }
  }

  std::string ToString() const override { return std::string("RANDOM_FUZZED"); }

 private:
  FuzzState& state_;
};

void MakeChunkWithRandomContent(FuzzState& state, SctpPacket::Builder& b) {
  b.Add(RandomFuzzedChunk(state));
}

std::vector<uint8_t> GeneratePacket(FuzzState& state) {
  DcSctpOptions options;
  // Setting a fixed limit to not be dependent on the defaults, which may
  // change.
  options.mtu = 2048;
  SctpPacket::Builder builder(VerificationTag(kRandomValue), options);

  // The largest expected serialized chunk, as created by fuzzers.
  static constexpr size_t kMaxChunkSize = 256;

  for (int i = 0; i < 5 && builder.bytes_remaining() > kMaxChunkSize; ++i) {
    switch (state.GetByte()) {
      case 1:
        MakeDataChunk(state, builder);
        break;
      case 2:
        MakeInitChunk(state, builder);
        break;
      case 3:
        MakeInitAckChunk(state, builder);
        break;
      case 4:
        MakeSackChunk(state, builder);
        break;
      case 5:
        MakeHeartbeatRequestChunk(state, builder);
        break;
      case 6:
        MakeHeartbeatAckChunk(state, builder);
        break;
      case 7:
        MakeAbortChunk(state, builder);
        break;
      case 8:
        MakeErrorChunk(state, builder);
        break;
      case 9:
        MakeCookieEchoChunk(state, builder);
        break;
      case 10:
        MakeCookieAckChunk(state, builder);
        break;
      case 11:
        MakeShutdownChunk(state, builder);
        break;
      case 12:
        MakeShutdownAckChunk(state, builder);
        break;
      case 13:
        MakeShutdownCompleteChunk(state, builder);
        break;
      case 14:
        MakeReConfigChunk(state, builder);
        break;
      case 15:
        MakeForwardTsnChunk(state, builder);
        break;
      case 16:
        MakeIDataChunk(state, builder);
        break;
      case 17:
        MakeIForwardTsnChunk(state, builder);
        break;
      case 18:
        MakeChunkWithRandomContent(state, builder);
        break;
      default:
        break;
    }
  }
  std::vector<uint8_t> packet = builder.Build();
  return packet;
}
}  // namespace

void FuzzSocket(DcSctpSocketInterface& socket,
                FuzzerCallbacks& cb,
                rtc::ArrayView<const uint8_t> data) {
  if (data.size() < kMinInputLength || data.size() > kMaxInputLength) {
    return;
  }
  if (data[0] >= static_cast<int>(StartingState::kNumberOfStates)) {
    return;
  }

  // Set the socket in a specified valid starting state
  SetSocketState(socket, cb, static_cast<StartingState>(data[0]));

  FuzzState state(data.subview(1));

  while (!state.empty()) {
    switch (state.GetByte()) {
      case 1:
        // Generate a valid SCTP packet (based on fuzz data) and "receive it".
        socket.ReceivePacket(GeneratePacket(state));
        break;
      case 2:
        socket.Connect();
        break;
      case 3:
        socket.Shutdown();
        break;
      case 4:
        socket.Close();
        break;
      case 5: {
        StreamID streams[] = {StreamID(state.GetByte())};
        socket.ResetStreams(streams);
      } break;
      case 6: {
        uint8_t flags = state.GetByte();
        SendOptions options;
        options.unordered = IsUnordered(flags & 0x01);
        options.max_retransmissions =
            (flags & 0x02) != 0 ? absl::make_optional(0) : absl::nullopt;
        options.lifecycle_id = LifecycleId(42);
        size_t payload_exponent = (flags >> 2) % 16;
        size_t payload_size = static_cast<size_t>(1) << payload_exponent;
        socket.Send(DcSctpMessage(StreamID(state.GetByte()), PPID(53),
                                  std::vector<uint8_t>(payload_size)),
                    options);
        break;
      }
      case 7: {
        // Expire an active timeout/timer.
        uint8_t timeout_idx = state.GetByte();
        absl::optional<TimeoutID> timeout_id = cb.ExpireTimeout(timeout_idx);
        if (timeout_id.has_value()) {
          socket.HandleTimeout(*timeout_id);
        }
        break;
      }
      default:
        break;
    }
  }
}
}  // namespace dcsctp_fuzzers
}  // namespace dcsctp

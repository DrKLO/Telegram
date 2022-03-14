/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_SOCKET_MOCK_DCSCTP_SOCKET_CALLBACKS_H_
#define NET_DCSCTP_SOCKET_MOCK_DCSCTP_SOCKET_CALLBACKS_H_

#include <cstdint>
#include <deque>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/public/dcsctp_message.h"
#include "net/dcsctp/public/dcsctp_socket.h"
#include "net/dcsctp/public/timeout.h"
#include "net/dcsctp/public/types.h"
#include "net/dcsctp/timer/fake_timeout.h"
#include "rtc_base/logging.h"
#include "rtc_base/random.h"
#include "test/gmock.h"

namespace dcsctp {

namespace internal {
// It can be argued if a mocked random number generator should be deterministic
// or if it should be have as a "real" random number generator. In this
// implementation, each instantiation of `MockDcSctpSocketCallbacks` will have
// their `GetRandomInt` return different sequences, but each instantiation will
// always generate the same sequence of random numbers. This to make it easier
// to compare logs from tests, but still to let e.g. two different sockets (used
// in the same test) get different random numbers, so that they don't start e.g.
// on the same sequence number. While that isn't an issue in the protocol, it
// just makes debugging harder as the two sockets would look exactly the same.
//
// In a real implementation of `DcSctpSocketCallbacks` the random number
// generator backing `GetRandomInt` should be seeded externally and correctly.
inline int GetUniqueSeed() {
  static int seed = 0;
  return ++seed;
}
}  // namespace internal

class MockDcSctpSocketCallbacks : public DcSctpSocketCallbacks {
 public:
  explicit MockDcSctpSocketCallbacks(absl::string_view name = "")
      : log_prefix_(name.empty() ? "" : std::string(name) + ": "),
        random_(internal::GetUniqueSeed()),
        timeout_manager_([this]() { return now_; }) {
    ON_CALL(*this, SendPacketWithStatus)
        .WillByDefault([this](rtc::ArrayView<const uint8_t> data) {
          sent_packets_.emplace_back(
              std::vector<uint8_t>(data.begin(), data.end()));
          return SendPacketStatus::kSuccess;
        });
    ON_CALL(*this, OnMessageReceived)
        .WillByDefault([this](DcSctpMessage message) {
          received_messages_.emplace_back(std::move(message));
        });

    ON_CALL(*this, OnError)
        .WillByDefault([this](ErrorKind error, absl::string_view message) {
          RTC_LOG(LS_WARNING)
              << log_prefix_ << "Socket error: " << ToString(error) << "; "
              << message;
        });
    ON_CALL(*this, OnAborted)
        .WillByDefault([this](ErrorKind error, absl::string_view message) {
          RTC_LOG(LS_WARNING)
              << log_prefix_ << "Socket abort: " << ToString(error) << "; "
              << message;
        });
    ON_CALL(*this, TimeMillis).WillByDefault([this]() { return now_; });
  }

  MOCK_METHOD(SendPacketStatus,
              SendPacketWithStatus,
              (rtc::ArrayView<const uint8_t> data),
              (override));

  std::unique_ptr<Timeout> CreateTimeout() override {
    return timeout_manager_.CreateTimeout();
  }

  MOCK_METHOD(TimeMs, TimeMillis, (), (override));
  uint32_t GetRandomInt(uint32_t low, uint32_t high) override {
    return random_.Rand(low, high);
  }

  MOCK_METHOD(void, OnMessageReceived, (DcSctpMessage message), (override));
  MOCK_METHOD(void,
              OnError,
              (ErrorKind error, absl::string_view message),
              (override));
  MOCK_METHOD(void,
              OnAborted,
              (ErrorKind error, absl::string_view message),
              (override));
  MOCK_METHOD(void, OnConnected, (), (override));
  MOCK_METHOD(void, OnClosed, (), (override));
  MOCK_METHOD(void, OnConnectionRestarted, (), (override));
  MOCK_METHOD(void,
              OnStreamsResetFailed,
              (rtc::ArrayView<const StreamID> outgoing_streams,
               absl::string_view reason),
              (override));
  MOCK_METHOD(void,
              OnStreamsResetPerformed,
              (rtc::ArrayView<const StreamID> outgoing_streams),
              (override));
  MOCK_METHOD(void,
              OnIncomingStreamsReset,
              (rtc::ArrayView<const StreamID> incoming_streams),
              (override));
  MOCK_METHOD(void, OnBufferedAmountLow, (StreamID stream_id), (override));
  MOCK_METHOD(void, OnTotalBufferedAmountLow, (), (override));

  bool HasPacket() const { return !sent_packets_.empty(); }

  std::vector<uint8_t> ConsumeSentPacket() {
    if (sent_packets_.empty()) {
      return {};
    }
    std::vector<uint8_t> ret = std::move(sent_packets_.front());
    sent_packets_.pop_front();
    return ret;
  }
  absl::optional<DcSctpMessage> ConsumeReceivedMessage() {
    if (received_messages_.empty()) {
      return absl::nullopt;
    }
    DcSctpMessage ret = std::move(received_messages_.front());
    received_messages_.pop_front();
    return ret;
  }

  void AdvanceTime(DurationMs duration_ms) { now_ = now_ + duration_ms; }
  void SetTime(TimeMs now) { now_ = now; }

  absl::optional<TimeoutID> GetNextExpiredTimeout() {
    return timeout_manager_.GetNextExpiredTimeout();
  }

  void Reset() {
    sent_packets_.clear();
    received_messages_.clear();
    timeout_manager_.Reset();
  }

 private:
  const std::string log_prefix_;
  TimeMs now_ = TimeMs(0);
  webrtc::Random random_;
  FakeTimeoutManager timeout_manager_;
  std::deque<std::vector<uint8_t>> sent_packets_;
  std::deque<DcSctpMessage> received_messages_;
};
}  // namespace dcsctp

#endif  // NET_DCSCTP_SOCKET_MOCK_DCSCTP_SOCKET_CALLBACKS_H_

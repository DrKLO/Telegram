/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_TEST_CLIENT_H_
#define RTC_BASE_TEST_CLIENT_H_

#include <memory>
#include <vector>

#include "rtc_base/async_udp_socket.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/fake_clock.h"
#include "rtc_base/synchronization/mutex.h"

namespace rtc {

// A simple client that can send TCP or UDP data and check that it receives
// what it expects to receive. Useful for testing server functionality.
class TestClient : public sigslot::has_slots<> {
 public:
  // Records the contents of a packet that was received.
  struct Packet {
    Packet(const SocketAddress& a,
           const char* b,
           size_t s,
           int64_t packet_time_us);
    Packet(const Packet& p);
    virtual ~Packet();

    SocketAddress addr;
    char* buf;
    size_t size;
    int64_t packet_time_us;
  };

  // Default timeout for NextPacket reads.
  static const int kTimeoutMs = 5000;

  // Creates a client that will send and receive with the given socket and
  // will post itself messages with the given thread.
  explicit TestClient(std::unique_ptr<AsyncPacketSocket> socket);
  // Create a test client that will use a fake clock. NextPacket needs to wait
  // for a packet to be received, and thus it needs to advance the fake clock
  // if the test is using one, rather than just sleeping.
  TestClient(std::unique_ptr<AsyncPacketSocket> socket,
             ThreadProcessingFakeClock* fake_clock);
  ~TestClient() override;

  SocketAddress address() const { return socket_->GetLocalAddress(); }
  SocketAddress remote_address() const { return socket_->GetRemoteAddress(); }

  // Checks that the socket moves to the specified connect state.
  bool CheckConnState(AsyncPacketSocket::State state);

  // Checks that the socket is connected to the remote side.
  bool CheckConnected() {
    return CheckConnState(AsyncPacketSocket::STATE_CONNECTED);
  }

  // Sends using the clients socket.
  int Send(const char* buf, size_t size);

  // Sends using the clients socket to the given destination.
  int SendTo(const char* buf, size_t size, const SocketAddress& dest);

  // Returns the next packet received by the client or null if none is received
  // within the specified timeout.
  std::unique_ptr<Packet> NextPacket(int timeout_ms);

  // Checks that the next packet has the given contents. Returns the remote
  // address that the packet was sent from.
  bool CheckNextPacket(const char* buf, size_t len, SocketAddress* addr);

  // Checks that no packets have arrived or will arrive in the next second.
  bool CheckNoPacket();

  int GetError();
  int SetOption(Socket::Option opt, int value);

  bool ready_to_send() const { return ready_to_send_count() > 0; }

  // How many times SignalReadyToSend has been fired.
  int ready_to_send_count() const { return ready_to_send_count_; }

 private:
  // Timeout for reads when no packet is expected.
  static const int kNoPacketTimeoutMs = 1000;
  // Workaround for the fact that AsyncPacketSocket::GetConnState doesn't exist.
  Socket::ConnState GetState();
  // Slot for packets read on the socket.
  void OnPacket(AsyncPacketSocket* socket,
                const char* buf,
                size_t len,
                const SocketAddress& remote_addr,
                const int64_t& packet_time_us);
  void OnReadyToSend(AsyncPacketSocket* socket);
  bool CheckTimestamp(int64_t packet_timestamp);
  void AdvanceTime(int ms);

  ThreadProcessingFakeClock* fake_clock_ = nullptr;
  webrtc::Mutex mutex_;
  std::unique_ptr<AsyncPacketSocket> socket_;
  std::vector<std::unique_ptr<Packet>> packets_;
  int ready_to_send_count_ = 0;
  int64_t prev_packet_timestamp_;
  RTC_DISALLOW_COPY_AND_ASSIGN(TestClient);
};

}  // namespace rtc

#endif  // RTC_BASE_TEST_CLIENT_H_

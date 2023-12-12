/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_STUNPROBER_STUN_PROBER_H_
#define P2P_STUNPROBER_STUN_PROBER_H_

#include <set>
#include <string>
#include <vector>

#include "api/sequence_checker.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "rtc_base/byte_buffer.h"
#include "rtc_base/ip_address.h"
#include "rtc_base/network.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/system/rtc_export.h"
#include "rtc_base/thread.h"

namespace rtc {
class AsyncPacketSocket;
class PacketSocketFactory;
class Thread;
class NetworkManager;
class AsyncResolverInterface;
}  // namespace rtc

namespace stunprober {

class StunProber;

static const int kMaxUdpBufferSize = 1200;

typedef std::function<void(StunProber*, int)> AsyncCallback;

enum NatType {
  NATTYPE_INVALID,
  NATTYPE_NONE,          // Not behind a NAT.
  NATTYPE_UNKNOWN,       // Behind a NAT but type can't be determine.
  NATTYPE_SYMMETRIC,     // Behind a symmetric NAT.
  NATTYPE_NON_SYMMETRIC  // Behind a non-symmetric NAT.
};

class RTC_EXPORT StunProber : public sigslot::has_slots<> {
 public:
  enum Status {       // Used in UMA_HISTOGRAM_ENUMERATION.
    SUCCESS,          // Successfully received bytes from the server.
    GENERIC_FAILURE,  // Generic failure.
    RESOLVE_FAILED,   // Host resolution failed.
    WRITE_FAILED,     // Sending a message to the server failed.
    READ_FAILED,      // Reading the reply from the server failed.
  };

  class Observer {
   public:
    virtual ~Observer() = default;
    virtual void OnPrepared(StunProber* prober, StunProber::Status status) = 0;
    virtual void OnFinished(StunProber* prober, StunProber::Status status) = 0;
  };

  struct RTC_EXPORT Stats {
    Stats();
    ~Stats();

    // `raw_num_request_sent` is the total number of requests
    // sent. `num_request_sent` is the count of requests against a server where
    // we see at least one response. `num_request_sent` is designed to protect
    // against DNS resolution failure or the STUN server is not responsive
    // which could skew the result.
    int raw_num_request_sent = 0;
    int num_request_sent = 0;

    int num_response_received = 0;
    NatType nat_type = NATTYPE_INVALID;
    int average_rtt_ms = -1;
    int success_percent = 0;
    int target_request_interval_ns = 0;
    int actual_request_interval_ns = 0;

    // Also report whether this trial can't be considered truly as shared
    // mode. Share mode only makes sense when we have multiple IP resolved and
    // successfully probed.
    bool shared_socket_mode = false;

    std::string host_ip;

    // If the srflx_addrs has more than 1 element, the NAT is symmetric.
    std::set<std::string> srflx_addrs;
  };

  StunProber(rtc::PacketSocketFactory* socket_factory,
             rtc::Thread* thread,
             std::vector<const rtc::Network*> networks);
  ~StunProber() override;

  StunProber(const StunProber&) = delete;
  StunProber& operator=(const StunProber&) = delete;

  // Begin performing the probe test against the `servers`. If
  // `shared_socket_mode` is false, each request will be done with a new socket.
  // Otherwise, a unique socket will be used for a single round of requests
  // against all resolved IPs. No single socket will be used against a given IP
  // more than once.  The interval of requests will be as close to the requested
  // inter-probe interval `stun_ta_interval_ms` as possible. After sending out
  // the last scheduled request, the probe will wait `timeout_ms` for request
  // responses and then call `finish_callback`.  `requests_per_ip` indicates how
  // many requests should be tried for each resolved IP address. In shared mode,
  // (the number of sockets to be created) equals to `requests_per_ip`. In
  // non-shared mode, (the number of sockets) equals to requests_per_ip * (the
  // number of resolved IP addresses). TODO(guoweis): Remove this once
  // everything moved to Prepare() and Run().
  bool Start(const std::vector<rtc::SocketAddress>& servers,
             bool shared_socket_mode,
             int stun_ta_interval_ms,
             int requests_per_ip,
             int timeout_ms,
             AsyncCallback finish_callback);

  // TODO(guoweis): The combination of Prepare() and Run() are equivalent to the
  // Start() above. Remove Start() once everything is migrated.
  bool Prepare(const std::vector<rtc::SocketAddress>& servers,
               bool shared_socket_mode,
               int stun_ta_interval_ms,
               int requests_per_ip,
               int timeout_ms,
               StunProber::Observer* observer);

  // Start to send out the STUN probes.
  bool Start(StunProber::Observer* observer);

  // Method to retrieve the Stats once `finish_callback` is invoked. Returning
  // false when the result is inconclusive, for example, whether it's behind a
  // NAT or not.
  bool GetStats(Stats* stats) const;

  int estimated_execution_time() {
    return static_cast<int>(requests_per_ip_ * all_servers_addrs_.size() *
                            interval_ms_);
  }

 private:
  // A requester tracks the requests and responses from a single socket to many
  // STUN servers.
  class Requester;

  // TODO(guoweis): Remove this once all dependencies move away from
  // AsyncCallback.
  class ObserverAdapter : public Observer {
   public:
    ObserverAdapter();
    ~ObserverAdapter() override;

    void set_callback(AsyncCallback callback) { callback_ = callback; }
    void OnPrepared(StunProber* stunprober, Status status) override;
    void OnFinished(StunProber* stunprober, Status status) override;

   private:
    AsyncCallback callback_;
  };

  bool ResolveServerName(const rtc::SocketAddress& addr);
  void OnServerResolved(rtc::AsyncResolverInterface* resolver);

  void OnSocketReady(rtc::AsyncPacketSocket* socket,
                     const rtc::SocketAddress& addr);

  void CreateSockets();

  bool Done() {
    return num_request_sent_ >= requests_per_ip_ * all_servers_addrs_.size();
  }

  size_t total_socket_required() {
    return (shared_socket_mode_ ? 1 : all_servers_addrs_.size()) *
           requests_per_ip_;
  }

  bool should_send_next_request(int64_t now);
  int get_wake_up_interval_ms();

  bool SendNextRequest();

  // Will be invoked in 1ms intervals and schedule the next request from the
  // `current_requester_` if the time has passed for another request.
  void MaybeScheduleStunRequests();

  void ReportOnPrepared(StunProber::Status status);
  void ReportOnFinished(StunProber::Status status);

  Requester* CreateRequester();

  Requester* current_requester_ = nullptr;

  // The time when the next request should go out.
  int64_t next_request_time_ms_ = 0;

  // Total requests sent so far.
  uint32_t num_request_sent_ = 0;

  bool shared_socket_mode_ = false;

  // How many requests should be done against each resolved IP.
  uint32_t requests_per_ip_ = 0;

  // Milliseconds to pause between each STUN request.
  int interval_ms_;

  // Timeout period after the last request is sent.
  int timeout_ms_;

  // STUN server name to be resolved.
  std::vector<rtc::SocketAddress> servers_;

  // Weak references.
  rtc::PacketSocketFactory* socket_factory_;
  rtc::Thread* thread_;

  // Accumulate all resolved addresses.
  std::vector<rtc::SocketAddress> all_servers_addrs_;

  // The set of STUN probe sockets and their state.
  std::vector<Requester*> requesters_;

  webrtc::SequenceChecker thread_checker_;

  // Temporary storage for created sockets.
  std::vector<rtc::AsyncPacketSocket*> sockets_;
  // This tracks how many of the sockets are ready.
  size_t total_ready_sockets_ = 0;

  Observer* observer_ = nullptr;
  // TODO(guoweis): Remove this once all dependencies move away from
  // AsyncCallback.
  ObserverAdapter observer_adapter_;

  const std::vector<const rtc::Network*> networks_;

  webrtc::ScopedTaskSafety task_safety_;
};

}  // namespace stunprober

#endif  // P2P_STUNPROBER_STUN_PROBER_H_

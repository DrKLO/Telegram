/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_VIRTUAL_SOCKET_SERVER_H_
#define RTC_BASE_VIRTUAL_SOCKET_SERVER_H_

#include <deque>
#include <map>
#include <vector>

#include "rtc_base/checks.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/deprecated/recursive_critical_section.h"
#include "rtc_base/event.h"
#include "rtc_base/fake_clock.h"
#include "rtc_base/message_handler.h"
#include "rtc_base/socket_server.h"

namespace rtc {

class Packet;
class VirtualSocket;
class SocketAddressPair;

// Simulates a network in the same manner as a loopback interface.  The
// interface can create as many addresses as you want.  All of the sockets
// created by this network will be able to communicate with one another, unless
// they are bound to addresses from incompatible families.
class VirtualSocketServer : public SocketServer, public sigslot::has_slots<> {
 public:
  VirtualSocketServer();
  // This constructor needs to be used if the test uses a fake clock and
  // ProcessMessagesUntilIdle, since ProcessMessagesUntilIdle needs a way of
  // advancing time.
  explicit VirtualSocketServer(ThreadProcessingFakeClock* fake_clock);
  ~VirtualSocketServer() override;

  // The default route indicates which local address to use when a socket is
  // bound to the 'any' address, e.g. 0.0.0.0.
  IPAddress GetDefaultRoute(int family);
  void SetDefaultRoute(const IPAddress& from_addr);

  // Limits the network bandwidth (maximum bytes per second).  Zero means that
  // all sends occur instantly.  Defaults to 0.
  uint32_t bandwidth() const { return bandwidth_; }
  void set_bandwidth(uint32_t bandwidth) { bandwidth_ = bandwidth; }

  // Limits the amount of data which can be in flight on the network without
  // packet loss (on a per sender basis).  Defaults to 64 KB.
  uint32_t network_capacity() const { return network_capacity_; }
  void set_network_capacity(uint32_t capacity) { network_capacity_ = capacity; }

  // The amount of data which can be buffered by tcp on the sender's side
  uint32_t send_buffer_capacity() const { return send_buffer_capacity_; }
  void set_send_buffer_capacity(uint32_t capacity) {
    send_buffer_capacity_ = capacity;
  }

  // The amount of data which can be buffered by tcp on the receiver's side
  uint32_t recv_buffer_capacity() const { return recv_buffer_capacity_; }
  void set_recv_buffer_capacity(uint32_t capacity) {
    recv_buffer_capacity_ = capacity;
  }

  // Controls the (transit) delay for packets sent in the network.  This does
  // not inclue the time required to sit in the send queue.  Both of these
  // values are measured in milliseconds.  Defaults to no delay.
  uint32_t delay_mean() const { return delay_mean_; }
  uint32_t delay_stddev() const { return delay_stddev_; }
  uint32_t delay_samples() const { return delay_samples_; }
  void set_delay_mean(uint32_t delay_mean) { delay_mean_ = delay_mean; }
  void set_delay_stddev(uint32_t delay_stddev) { delay_stddev_ = delay_stddev; }
  void set_delay_samples(uint32_t delay_samples) {
    delay_samples_ = delay_samples;
  }

  // If the (transit) delay parameters are modified, this method should be
  // called to recompute the new distribution.
  void UpdateDelayDistribution();

  // Controls the (uniform) probability that any sent packet is dropped.  This
  // is separate from calculations to drop based on queue size.
  double drop_probability() { return drop_prob_; }
  void set_drop_probability(double drop_prob) {
    RTC_DCHECK_GE(drop_prob, 0.0);
    RTC_DCHECK_LE(drop_prob, 1.0);
    drop_prob_ = drop_prob;
  }

  // If |blocked| is true, subsequent attempts to send will result in -1 being
  // returned, with the socket error set to EWOULDBLOCK.
  //
  // If this method is later called with |blocked| set to false, any sockets
  // that previously failed to send with EWOULDBLOCK will emit SignalWriteEvent.
  //
  // This can be used to simulate the send buffer on a network interface being
  // full, and test functionality related to EWOULDBLOCK/SignalWriteEvent.
  void SetSendingBlocked(bool blocked);

  // SocketFactory:
  Socket* CreateSocket(int family, int type) override;
  AsyncSocket* CreateAsyncSocket(int family, int type) override;

  // SocketServer:
  void SetMessageQueue(Thread* queue) override;
  bool Wait(int cms, bool process_io) override;
  void WakeUp() override;

  void SetDelayOnAddress(const rtc::SocketAddress& address, int delay_ms) {
    delay_by_ip_[address.ipaddr()] = delay_ms;
  }

  // Used by TurnPortTest and TcpPortTest (for example), to mimic a case where
  // a proxy returns the local host address instead of the original one the
  // port was bound against. Please see WebRTC issue 3927 for more detail.
  //
  // If SetAlternativeLocalAddress(A, B) is called, then when something
  // attempts to bind a socket to address A, it will get a socket bound to
  // address B instead.
  void SetAlternativeLocalAddress(const rtc::IPAddress& address,
                                  const rtc::IPAddress& alternative);

  typedef std::pair<double, double> Point;
  typedef std::vector<Point> Function;

  static Function* CreateDistribution(uint32_t mean,
                                      uint32_t stddev,
                                      uint32_t samples);

  // Similar to Thread::ProcessMessages, but it only processes messages until
  // there are no immediate messages or pending network traffic.  Returns false
  // if Thread::Stop() was called.
  bool ProcessMessagesUntilIdle();

  // Sets the next port number to use for testing.
  void SetNextPortForTesting(uint16_t port);

  // Close a pair of Tcp connections by addresses. Both connections will have
  // its own OnClose invoked.
  bool CloseTcpConnections(const SocketAddress& addr_local,
                           const SocketAddress& addr_remote);

  // Number of packets that clients have attempted to send through this virtual
  // socket server. Intended to be used for test assertions.
  uint32_t sent_packets() const { return sent_packets_; }

  // For testing purpose only. Fired when a client socket is created.
  sigslot::signal1<VirtualSocket*> SignalSocketCreated;

 protected:
  // Returns a new IP not used before in this network.
  IPAddress GetNextIP(int family);
  uint16_t GetNextPort();

  VirtualSocket* CreateSocketInternal(int family, int type);

  // Binds the given socket to addr, assigning and IP and Port if necessary
  int Bind(VirtualSocket* socket, SocketAddress* addr);

  // Binds the given socket to the given (fully-defined) address.
  int Bind(VirtualSocket* socket, const SocketAddress& addr);

  // Find the socket bound to the given address
  VirtualSocket* LookupBinding(const SocketAddress& addr);

  int Unbind(const SocketAddress& addr, VirtualSocket* socket);

  // Adds a mapping between this socket pair and the socket.
  void AddConnection(const SocketAddress& client,
                     const SocketAddress& server,
                     VirtualSocket* socket);

  // Find the socket pair corresponding to this server address.
  VirtualSocket* LookupConnection(const SocketAddress& client,
                                  const SocketAddress& server);

  void RemoveConnection(const SocketAddress& client,
                        const SocketAddress& server);

  // Connects the given socket to the socket at the given address
  int Connect(VirtualSocket* socket,
              const SocketAddress& remote_addr,
              bool use_delay);

  // Sends a disconnect message to the socket at the given address
  bool Disconnect(VirtualSocket* socket);

  // Sends the given packet to the socket at the given address (if one exists).
  int SendUdp(VirtualSocket* socket,
              const char* data,
              size_t data_size,
              const SocketAddress& remote_addr);

  // Moves as much data as possible from the sender's buffer to the network
  void SendTcp(VirtualSocket* socket);

  // Places a packet on the network.
  void AddPacketToNetwork(VirtualSocket* socket,
                          VirtualSocket* recipient,
                          int64_t cur_time,
                          const char* data,
                          size_t data_size,
                          size_t header_size,
                          bool ordered);

  // Removes stale packets from the network
  void PurgeNetworkPackets(VirtualSocket* socket, int64_t cur_time);

  // Computes the number of milliseconds required to send a packet of this size.
  uint32_t SendDelay(uint32_t size);

  // If the delay has been set for the address of the socket, returns the set
  // delay. Otherwise, returns a random transit delay chosen from the
  // appropriate distribution.
  uint32_t GetTransitDelay(Socket* socket);

  // Basic operations on functions.  Those that return a function also take
  // ownership of the function given (and hence, may modify or delete it).
  static Function* Accumulate(Function* f);
  static Function* Invert(Function* f);
  static Function* Resample(Function* f,
                            double x1,
                            double x2,
                            uint32_t samples);
  static double Evaluate(Function* f, double x);

  // Null out our message queue if it goes away. Necessary in the case where
  // our lifetime is greater than that of the thread we are using, since we
  // try to send Close messages for all connected sockets when we shutdown.
  void OnMessageQueueDestroyed() { msg_queue_ = nullptr; }

  // Determine if two sockets should be able to communicate.
  // We don't (currently) specify an address family for sockets; instead,
  // the currently bound address is used to infer the address family.
  // Any socket that is not explicitly bound to an IPv4 address is assumed to be
  // dual-stack capable.
  // This function tests if two addresses can communicate, as well as the
  // sockets to which they may be bound (the addresses may or may not yet be
  // bound to the sockets).
  // First the addresses are tested (after normalization):
  // If both have the same family, then communication is OK.
  // If only one is IPv4 then false, unless the other is bound to ::.
  // This applies even if the IPv4 address is 0.0.0.0.
  // The socket arguments are optional; the sockets are checked to see if they
  // were explicitly bound to IPv6-any ('::'), and if so communication is
  // permitted.
  // NB: This scheme doesn't permit non-dualstack IPv6 sockets.
  static bool CanInteractWith(VirtualSocket* local, VirtualSocket* remote);

 private:
  friend class VirtualSocket;

  // Sending was previously blocked, but now isn't.
  sigslot::signal0<> SignalReadyToSend;

  typedef std::map<SocketAddress, VirtualSocket*> AddressMap;
  typedef std::map<SocketAddressPair, VirtualSocket*> ConnectionMap;

  // May be null if the test doesn't use a fake clock, or it does but doesn't
  // use ProcessMessagesUntilIdle.
  ThreadProcessingFakeClock* fake_clock_ = nullptr;

  // Used to implement Wait/WakeUp.
  Event wakeup_;
  Thread* msg_queue_;
  bool stop_on_idle_;
  in_addr next_ipv4_;
  in6_addr next_ipv6_;
  uint16_t next_port_;
  AddressMap* bindings_;
  ConnectionMap* connections_;

  IPAddress default_route_v4_;
  IPAddress default_route_v6_;

  uint32_t bandwidth_;
  uint32_t network_capacity_;
  uint32_t send_buffer_capacity_;
  uint32_t recv_buffer_capacity_;
  uint32_t delay_mean_;
  uint32_t delay_stddev_;
  uint32_t delay_samples_;

  // Used for testing.
  uint32_t sent_packets_ = 0;

  std::map<rtc::IPAddress, int> delay_by_ip_;
  std::map<rtc::IPAddress, rtc::IPAddress> alternative_address_mapping_;
  std::unique_ptr<Function> delay_dist_;

  RecursiveCriticalSection delay_crit_;

  double drop_prob_;
  bool sending_blocked_ = false;
  RTC_DISALLOW_COPY_AND_ASSIGN(VirtualSocketServer);
};

// Implements the socket interface using the virtual network.  Packets are
// passed as messages using the message queue of the socket server.
class VirtualSocket : public AsyncSocket,
                      public MessageHandlerAutoCleanup,
                      public sigslot::has_slots<> {
 public:
  VirtualSocket(VirtualSocketServer* server, int family, int type, bool async);
  ~VirtualSocket() override;

  SocketAddress GetLocalAddress() const override;
  SocketAddress GetRemoteAddress() const override;

  int Bind(const SocketAddress& addr) override;
  int Connect(const SocketAddress& addr) override;
  int Close() override;
  int Send(const void* pv, size_t cb) override;
  int SendTo(const void* pv, size_t cb, const SocketAddress& addr) override;
  int Recv(void* pv, size_t cb, int64_t* timestamp) override;
  int RecvFrom(void* pv,
               size_t cb,
               SocketAddress* paddr,
               int64_t* timestamp) override;
  int Listen(int backlog) override;
  VirtualSocket* Accept(SocketAddress* paddr) override;

  int GetError() const override;
  void SetError(int error) override;
  ConnState GetState() const override;
  int GetOption(Option opt, int* value) override;
  int SetOption(Option opt, int value) override;
  void OnMessage(Message* pmsg) override;

  bool was_any() { return was_any_; }
  void set_was_any(bool was_any) { was_any_ = was_any; }

  // For testing purpose only. Fired when client socket is bound to an address.
  sigslot::signal2<VirtualSocket*, const SocketAddress&> SignalAddressReady;

 private:
  struct NetworkEntry {
    size_t size;
    int64_t done_time;
  };

  typedef std::deque<SocketAddress> ListenQueue;
  typedef std::deque<NetworkEntry> NetworkQueue;
  typedef std::vector<char> SendBuffer;
  typedef std::list<Packet*> RecvBuffer;
  typedef std::map<Option, int> OptionsMap;

  int InitiateConnect(const SocketAddress& addr, bool use_delay);
  void CompleteConnect(const SocketAddress& addr, bool notify);
  int SendUdp(const void* pv, size_t cb, const SocketAddress& addr);
  int SendTcp(const void* pv, size_t cb);

  // Used by server sockets to set the local address without binding.
  void SetLocalAddress(const SocketAddress& addr);

  void OnSocketServerReadyToSend();

  VirtualSocketServer* server_;
  int type_;
  bool async_;
  ConnState state_;
  int error_;
  SocketAddress local_addr_;
  SocketAddress remote_addr_;

  // Pending sockets which can be Accepted
  ListenQueue* listen_queue_;

  // Data which tcp has buffered for sending
  SendBuffer send_buffer_;
  // Set to false if the last attempt to send resulted in EWOULDBLOCK.
  // Set back to true when the socket can send again.
  bool ready_to_send_ = true;

  // Critical section to protect the recv_buffer and queue_
  RecursiveCriticalSection crit_;

  // Network model that enforces bandwidth and capacity constraints
  NetworkQueue network_;
  size_t network_size_;
  // The scheduled delivery time of the last packet sent on this socket.
  // It is used to ensure ordered delivery of packets sent on this socket.
  int64_t last_delivery_time_ = 0;

  // Data which has been received from the network
  RecvBuffer recv_buffer_;
  // The amount of data which is in flight or in recv_buffer_
  size_t recv_buffer_size_;

  // Is this socket bound?
  bool bound_;

  // When we bind a socket to Any, VSS's Bind gives it another address. For
  // dual-stack sockets, we want to distinguish between sockets that were
  // explicitly given a particular address and sockets that had one picked
  // for them by VSS.
  bool was_any_;

  // Store the options that are set
  OptionsMap options_map_;

  friend class VirtualSocketServer;
};

}  // namespace rtc

#endif  // RTC_BASE_VIRTUAL_SOCKET_SERVER_H_

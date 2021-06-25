/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_PHYSICAL_SOCKET_SERVER_H_
#define RTC_BASE_PHYSICAL_SOCKET_SERVER_H_

#if defined(WEBRTC_POSIX) && defined(WEBRTC_LINUX)
#include <sys/epoll.h>
#define WEBRTC_USE_EPOLL 1
#endif

#include <array>
#include <memory>
#include <unordered_map>
#include <vector>

#include "rtc_base/async_resolver.h"
#include "rtc_base/async_resolver_interface.h"
#include "rtc_base/deprecated/recursive_critical_section.h"
#include "rtc_base/socket_server.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/system/rtc_export.h"
#include "rtc_base/thread_annotations.h"

#if defined(WEBRTC_POSIX)
typedef int SOCKET;
#endif  // WEBRTC_POSIX

namespace rtc {

// Event constants for the Dispatcher class.
enum DispatcherEvent {
  DE_READ = 0x0001,
  DE_WRITE = 0x0002,
  DE_CONNECT = 0x0004,
  DE_CLOSE = 0x0008,
  DE_ACCEPT = 0x0010,
};

class Signaler;

class Dispatcher {
 public:
  virtual ~Dispatcher() {}
  virtual uint32_t GetRequestedEvents() = 0;
  virtual void OnEvent(uint32_t ff, int err) = 0;
#if defined(WEBRTC_WIN)
  virtual WSAEVENT GetWSAEvent() = 0;
  virtual SOCKET GetSocket() = 0;
  virtual bool CheckSignalClose() = 0;
#elif defined(WEBRTC_POSIX)
  virtual int GetDescriptor() = 0;
  virtual bool IsDescriptorClosed() = 0;
#endif
};

// A socket server that provides the real sockets of the underlying OS.
class RTC_EXPORT PhysicalSocketServer : public SocketServer {
 public:
  PhysicalSocketServer();
  ~PhysicalSocketServer() override;

  // SocketFactory:
  Socket* CreateSocket(int family, int type) override;
  AsyncSocket* CreateAsyncSocket(int family, int type) override;

  // Internal Factory for Accept (virtual so it can be overwritten in tests).
  virtual AsyncSocket* WrapSocket(SOCKET s);

  // SocketServer:
  bool Wait(int cms, bool process_io) override;
  void WakeUp() override;

  void Add(Dispatcher* dispatcher);
  void Remove(Dispatcher* dispatcher);
  void Update(Dispatcher* dispatcher);

 private:
  // The number of events to process with one call to "epoll_wait".
  static constexpr size_t kNumEpollEvents = 128;

#if defined(WEBRTC_POSIX)
  bool WaitSelect(int cms, bool process_io);
#endif  // WEBRTC_POSIX
#if defined(WEBRTC_USE_EPOLL)
  void AddEpoll(Dispatcher* dispatcher, uint64_t key);
  void RemoveEpoll(Dispatcher* dispatcher);
  void UpdateEpoll(Dispatcher* dispatcher, uint64_t key);
  bool WaitEpoll(int cms);
  bool WaitPoll(int cms, Dispatcher* dispatcher);

  // This array is accessed in isolation by a thread calling into Wait().
  // It's useless to use a SequenceChecker to guard it because a socket
  // server can outlive the thread it's bound to, forcing the Wait call
  // to have to reset the sequence checker on Wait calls.
  std::array<epoll_event, kNumEpollEvents> epoll_events_;
  const int epoll_fd_ = INVALID_SOCKET;
#endif  // WEBRTC_USE_EPOLL
  // uint64_t keys are used to uniquely identify a dispatcher in order to avoid
  // the ABA problem during the epoll loop (a dispatcher being destroyed and
  // replaced by one with the same address).
  uint64_t next_dispatcher_key_ RTC_GUARDED_BY(crit_) = 0;
  std::unordered_map<uint64_t, Dispatcher*> dispatcher_by_key_
      RTC_GUARDED_BY(crit_);
  // Reverse lookup necessary for removals/updates.
  std::unordered_map<Dispatcher*, uint64_t> key_by_dispatcher_
      RTC_GUARDED_BY(crit_);
  // A list of dispatcher keys that we're interested in for the current
  // select() or WSAWaitForMultipleEvents() loop. Again, used to avoid the ABA
  // problem (a socket being destroyed and a new one created with the same
  // handle, erroneously receiving the events from the destroyed socket).
  //
  // Kept as a member variable just for efficiency.
  std::vector<uint64_t> current_dispatcher_keys_;
  Signaler* signal_wakeup_;  // Assigned in constructor only
  RecursiveCriticalSection crit_;
#if defined(WEBRTC_WIN)
  const WSAEVENT socket_ev_;
#endif
  bool fWait_;
  // Are we currently in a select()/epoll()/WSAWaitForMultipleEvents loop?
  // Used for a DCHECK, because we don't support reentrant waiting.
  bool waiting_ = false;
};

class PhysicalSocket : public AsyncSocket, public sigslot::has_slots<> {
 public:
  PhysicalSocket(PhysicalSocketServer* ss, SOCKET s = INVALID_SOCKET);
  ~PhysicalSocket() override;

  // Creates the underlying OS socket (same as the "socket" function).
  virtual bool Create(int family, int type);

  SocketAddress GetLocalAddress() const override;
  SocketAddress GetRemoteAddress() const override;

  int Bind(const SocketAddress& bind_addr) override;
  int Connect(const SocketAddress& addr) override;

  int GetError() const override;
  void SetError(int error) override;

  ConnState GetState() const override;

  int GetOption(Option opt, int* value) override;
  int SetOption(Option opt, int value) override;

  int Send(const void* pv, size_t cb) override;
  int SendTo(const void* buffer,
             size_t length,
             const SocketAddress& addr) override;

  int Recv(void* buffer, size_t length, int64_t* timestamp) override;
  int RecvFrom(void* buffer,
               size_t length,
               SocketAddress* out_addr,
               int64_t* timestamp) override;

  int Listen(int backlog) override;
  AsyncSocket* Accept(SocketAddress* out_addr) override;

  int Close() override;

  SocketServer* socketserver() { return ss_; }

 protected:
  int DoConnect(const SocketAddress& connect_addr);

  // Make virtual so ::accept can be overwritten in tests.
  virtual SOCKET DoAccept(SOCKET socket, sockaddr* addr, socklen_t* addrlen);

  // Make virtual so ::send can be overwritten in tests.
  virtual int DoSend(SOCKET socket, const char* buf, int len, int flags);

  // Make virtual so ::sendto can be overwritten in tests.
  virtual int DoSendTo(SOCKET socket,
                       const char* buf,
                       int len,
                       int flags,
                       const struct sockaddr* dest_addr,
                       socklen_t addrlen);

  void OnResolveResult(AsyncResolverInterface* resolver);

  void UpdateLastError();
  void MaybeRemapSendError();

  uint8_t enabled_events() const { return enabled_events_; }
  virtual void SetEnabledEvents(uint8_t events);
  virtual void EnableEvents(uint8_t events);
  virtual void DisableEvents(uint8_t events);

  int TranslateOption(Option opt, int* slevel, int* sopt);

  PhysicalSocketServer* ss_;
  SOCKET s_;
  bool udp_;
  int family_ = 0;
  mutable webrtc::Mutex mutex_;
  int error_ RTC_GUARDED_BY(mutex_);
  ConnState state_;
  AsyncResolver* resolver_;

#if !defined(NDEBUG)
  std::string dbg_addr_;
#endif

 private:
  uint8_t enabled_events_ = 0;
};

class SocketDispatcher : public Dispatcher, public PhysicalSocket {
 public:
  explicit SocketDispatcher(PhysicalSocketServer* ss);
  SocketDispatcher(SOCKET s, PhysicalSocketServer* ss);
  ~SocketDispatcher() override;

  bool Initialize();

  virtual bool Create(int type);
  bool Create(int family, int type) override;

#if defined(WEBRTC_WIN)
  WSAEVENT GetWSAEvent() override;
  SOCKET GetSocket() override;
  bool CheckSignalClose() override;
#elif defined(WEBRTC_POSIX)
  int GetDescriptor() override;
  bool IsDescriptorClosed() override;
#endif

  uint32_t GetRequestedEvents() override;
  void OnEvent(uint32_t ff, int err) override;

  int Close() override;

#if defined(WEBRTC_USE_EPOLL)
 protected:
  void StartBatchedEventUpdates();
  void FinishBatchedEventUpdates();

  void SetEnabledEvents(uint8_t events) override;
  void EnableEvents(uint8_t events) override;
  void DisableEvents(uint8_t events) override;
#endif

 private:
#if defined(WEBRTC_WIN)
  static int next_id_;
  int id_;
  bool signal_close_;
  int signal_err_;
#endif  // WEBRTC_WIN
#if defined(WEBRTC_USE_EPOLL)
  void MaybeUpdateDispatcher(uint8_t old_events);

  int saved_enabled_events_ = -1;
#endif
};

}  // namespace rtc

#endif  // RTC_BASE_PHYSICAL_SOCKET_SERVER_H_

/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "rtc_base/physical_socket_server.h"

#if defined(_MSC_VER) && _MSC_VER < 1300
#pragma warning(disable : 4786)
#endif

#ifdef MEMORY_SANITIZER
#include <sanitizer/msan_interface.h>
#endif

#if defined(WEBRTC_POSIX)
#include <fcntl.h>
#include <string.h>
#if defined(WEBRTC_USE_EPOLL)
// "poll" will be used to wait for the signal dispatcher.
#include <poll.h>
#endif
#include <sys/ioctl.h>
#include <sys/select.h>
#include <sys/time.h>
#include <unistd.h>
#endif

#if defined(WEBRTC_WIN)
#include <windows.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#undef SetPort
#endif

#include <errno.h>

#include <algorithm>
#include <map>

#include "rtc_base/arraysize.h"
#include "rtc_base/byte_order.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/network_monitor.h"
#include "rtc_base/null_socket_server.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/time_utils.h"

#if defined(WEBRTC_LINUX)
#include <linux/sockios.h>
#endif

#if defined(WEBRTC_WIN)
#define LAST_SYSTEM_ERROR (::GetLastError())
#elif defined(__native_client__) && __native_client__
#define LAST_SYSTEM_ERROR (0)
#elif defined(WEBRTC_POSIX)
#define LAST_SYSTEM_ERROR (errno)
#endif  // WEBRTC_WIN

#if defined(WEBRTC_POSIX)
#include <netinet/tcp.h>  // for TCP_NODELAY
#define IP_MTU 14  // Until this is integrated from linux/in.h to netinet/in.h
typedef void* SockOptArg;

#endif  // WEBRTC_POSIX

#if defined(WEBRTC_POSIX) && !defined(WEBRTC_MAC) && !defined(__native_client__)

int64_t GetSocketRecvTimestamp(int socket) {
  struct timeval tv_ioctl;
  int ret = ioctl(socket, SIOCGSTAMP, &tv_ioctl);
  if (ret != 0)
    return -1;
  int64_t timestamp =
      rtc::kNumMicrosecsPerSec * static_cast<int64_t>(tv_ioctl.tv_sec) +
      static_cast<int64_t>(tv_ioctl.tv_usec);
  return timestamp;
}

#else

int64_t GetSocketRecvTimestamp(int socket) {
  return -1;
}
#endif

#if defined(WEBRTC_WIN)
typedef char* SockOptArg;
#endif

#if defined(WEBRTC_USE_EPOLL)
// POLLRDHUP / EPOLLRDHUP are only defined starting with Linux 2.6.17.
#if !defined(POLLRDHUP)
#define POLLRDHUP 0x2000
#endif
#if !defined(EPOLLRDHUP)
#define EPOLLRDHUP 0x2000
#endif
#endif

namespace {
class ScopedSetTrue {
 public:
  ScopedSetTrue(bool* value) : value_(value) {
    RTC_DCHECK(!*value_);
    *value_ = true;
  }
  ~ScopedSetTrue() { *value_ = false; }

 private:
  bool* value_;
};
}  // namespace

namespace rtc {

PhysicalSocket::PhysicalSocket(PhysicalSocketServer* ss, SOCKET s)
    : ss_(ss),
      s_(s),
      error_(0),
      state_((s == INVALID_SOCKET) ? CS_CLOSED : CS_CONNECTED),
      resolver_(nullptr) {
  if (s_ != INVALID_SOCKET) {
    SetEnabledEvents(DE_READ | DE_WRITE);

    int type = SOCK_STREAM;
    socklen_t len = sizeof(type);
    const int res =
        getsockopt(s_, SOL_SOCKET, SO_TYPE, (SockOptArg)&type, &len);
    RTC_DCHECK_EQ(0, res);
    udp_ = (SOCK_DGRAM == type);
  }
}

PhysicalSocket::~PhysicalSocket() {
  Close();
}

bool PhysicalSocket::Create(int family, int type) {
  Close();
  s_ = ::socket(family, type, 0);
  udp_ = (SOCK_DGRAM == type);
  family_ = family;
  UpdateLastError();
  if (udp_) {
    SetEnabledEvents(DE_READ | DE_WRITE);
  }
  return s_ != INVALID_SOCKET;
}

SocketAddress PhysicalSocket::GetLocalAddress() const {
  sockaddr_storage addr_storage = {};
  socklen_t addrlen = sizeof(addr_storage);
  sockaddr* addr = reinterpret_cast<sockaddr*>(&addr_storage);
  int result = ::getsockname(s_, addr, &addrlen);
  SocketAddress address;
  if (result >= 0) {
    SocketAddressFromSockAddrStorage(addr_storage, &address);
  } else {
    RTC_LOG(LS_WARNING) << "GetLocalAddress: unable to get local addr, socket="
                        << s_;
  }
  return address;
}

SocketAddress PhysicalSocket::GetRemoteAddress() const {
  sockaddr_storage addr_storage = {};
  socklen_t addrlen = sizeof(addr_storage);
  sockaddr* addr = reinterpret_cast<sockaddr*>(&addr_storage);
  int result = ::getpeername(s_, addr, &addrlen);
  SocketAddress address;
  if (result >= 0) {
    SocketAddressFromSockAddrStorage(addr_storage, &address);
  } else {
    RTC_LOG(LS_WARNING)
        << "GetRemoteAddress: unable to get remote addr, socket=" << s_;
  }
  return address;
}

int PhysicalSocket::Bind(const SocketAddress& bind_addr) {
  SocketAddress copied_bind_addr = bind_addr;
  // If a network binder is available, use it to bind a socket to an interface
  // instead of bind(), since this is more reliable on an OS with a weak host
  // model.
  if (ss_->network_binder() && !bind_addr.IsAnyIP()) {
    NetworkBindingResult result =
        ss_->network_binder()->BindSocketToNetwork(s_, bind_addr.ipaddr());
    if (result == NetworkBindingResult::SUCCESS) {
      // Since the network binder handled binding the socket to the desired
      // network interface, we don't need to (and shouldn't) include an IP in
      // the bind() call; bind() just needs to assign a port.
      copied_bind_addr.SetIP(GetAnyIP(copied_bind_addr.ipaddr().family()));
    } else if (result == NetworkBindingResult::NOT_IMPLEMENTED) {
      RTC_LOG(LS_INFO) << "Can't bind socket to network because "
                          "network binding is not implemented for this OS.";
    } else {
      if (bind_addr.IsLoopbackIP()) {
        // If we couldn't bind to a loopback IP (which should only happen in
        // test scenarios), continue on. This may be expected behavior.
        RTC_LOG(LS_VERBOSE) << "Binding socket to loopback address"
                            << " failed; result: " << static_cast<int>(result);
      } else {
        RTC_LOG(LS_WARNING) << "Binding socket to network address"
                            << " failed; result: " << static_cast<int>(result);
        // If a network binding was attempted and failed, we should stop here
        // and not try to use the socket. Otherwise, we may end up sending
        // packets with an invalid source address.
        // See: https://bugs.chromium.org/p/webrtc/issues/detail?id=7026
        return -1;
      }
    }
  }
  sockaddr_storage addr_storage;
  size_t len = copied_bind_addr.ToSockAddrStorage(&addr_storage);
  sockaddr* addr = reinterpret_cast<sockaddr*>(&addr_storage);
  int err = ::bind(s_, addr, static_cast<int>(len));
  UpdateLastError();
#if !defined(NDEBUG)
  if (0 == err) {
    dbg_addr_ = "Bound @ ";
    dbg_addr_.append(GetLocalAddress().ToString());
  }
#endif
  return err;
}

int PhysicalSocket::Connect(const SocketAddress& addr) {
  // TODO(pthatcher): Implicit creation is required to reconnect...
  // ...but should we make it more explicit?
  if (state_ != CS_CLOSED) {
    SetError(EALREADY);
    return SOCKET_ERROR;
  }
  if (addr.IsUnresolvedIP()) {
    RTC_LOG(LS_VERBOSE) << "Resolving addr in PhysicalSocket::Connect";
    resolver_ = new AsyncResolver();
    resolver_->SignalDone.connect(this, &PhysicalSocket::OnResolveResult);
    resolver_->Start(addr);
    state_ = CS_CONNECTING;
    return 0;
  }

  return DoConnect(addr);
}

int PhysicalSocket::DoConnect(const SocketAddress& connect_addr) {
  if ((s_ == INVALID_SOCKET) && !Create(connect_addr.family(), SOCK_STREAM)) {
    return SOCKET_ERROR;
  }
  sockaddr_storage addr_storage;
  size_t len = connect_addr.ToSockAddrStorage(&addr_storage);
  sockaddr* addr = reinterpret_cast<sockaddr*>(&addr_storage);
  int err = ::connect(s_, addr, static_cast<int>(len));
  UpdateLastError();
  uint8_t events = DE_READ | DE_WRITE;
  if (err == 0) {
    state_ = CS_CONNECTED;
  } else if (IsBlockingError(GetError())) {
    state_ = CS_CONNECTING;
    events |= DE_CONNECT;
  } else {
    return SOCKET_ERROR;
  }

  EnableEvents(events);
  return 0;
}

int PhysicalSocket::GetError() const {
  webrtc::MutexLock lock(&mutex_);
  return error_;
}

void PhysicalSocket::SetError(int error) {
  webrtc::MutexLock lock(&mutex_);
  error_ = error;
}

AsyncSocket::ConnState PhysicalSocket::GetState() const {
  return state_;
}

int PhysicalSocket::GetOption(Option opt, int* value) {
  int slevel;
  int sopt;
  if (TranslateOption(opt, &slevel, &sopt) == -1)
    return -1;
  socklen_t optlen = sizeof(*value);
  int ret = ::getsockopt(s_, slevel, sopt, (SockOptArg)value, &optlen);
  if (ret == -1) {
    return -1;
  }
  if (opt == OPT_DONTFRAGMENT) {
#if defined(WEBRTC_LINUX) && !defined(WEBRTC_ANDROID)
    *value = (*value != IP_PMTUDISC_DONT) ? 1 : 0;
#endif
  } else if (opt == OPT_DSCP) {
#if defined(WEBRTC_POSIX)
    // unshift DSCP value to get six most significant bits of IP DiffServ field
    *value >>= 2;
#endif
  }
  return ret;
}

int PhysicalSocket::SetOption(Option opt, int value) {
  int slevel;
  int sopt;
  if (TranslateOption(opt, &slevel, &sopt) == -1)
    return -1;
  if (opt == OPT_DONTFRAGMENT) {
#if defined(WEBRTC_LINUX) && !defined(WEBRTC_ANDROID)
    value = (value) ? IP_PMTUDISC_DO : IP_PMTUDISC_DONT;
#endif
  } else if (opt == OPT_DSCP) {
#if defined(WEBRTC_POSIX)
    // shift DSCP value to fit six most significant bits of IP DiffServ field
    value <<= 2;
#endif
  }
#if defined(WEBRTC_POSIX)
  if (sopt == IPV6_TCLASS) {
    // Set the IPv4 option in all cases to support dual-stack sockets.
    // Don't bother checking the return code, as this is expected to fail if
    // it's not actually dual-stack.
    ::setsockopt(s_, IPPROTO_IP, IP_TOS, (SockOptArg)&value, sizeof(value));
  }
#endif
  int result =
      ::setsockopt(s_, slevel, sopt, (SockOptArg)&value, sizeof(value));
  if (result != 0) {
    UpdateLastError();
  }
  return result;
}

int PhysicalSocket::Send(const void* pv, size_t cb) {
  int sent = DoSend(
      s_, reinterpret_cast<const char*>(pv), static_cast<int>(cb),
#if defined(WEBRTC_LINUX) && !defined(WEBRTC_ANDROID)
      // Suppress SIGPIPE. Without this, attempting to send on a socket whose
      // other end is closed will result in a SIGPIPE signal being raised to
      // our process, which by default will terminate the process, which we
      // don't want. By specifying this flag, we'll just get the error EPIPE
      // instead and can handle the error gracefully.
      MSG_NOSIGNAL
#else
      0
#endif
  );
  UpdateLastError();
  MaybeRemapSendError();
  // We have seen minidumps where this may be false.
  RTC_DCHECK(sent <= static_cast<int>(cb));
  if ((sent > 0 && sent < static_cast<int>(cb)) ||
      (sent < 0 && IsBlockingError(GetError()))) {
    EnableEvents(DE_WRITE);
  }
  return sent;
}

int PhysicalSocket::SendTo(const void* buffer,
                           size_t length,
                           const SocketAddress& addr) {
  sockaddr_storage saddr;
  size_t len = addr.ToSockAddrStorage(&saddr);
  int sent =
      DoSendTo(s_, static_cast<const char*>(buffer), static_cast<int>(length),
#if defined(WEBRTC_LINUX) && !defined(WEBRTC_ANDROID)
               // Suppress SIGPIPE. See above for explanation.
               MSG_NOSIGNAL,
#else
               0,
#endif
               reinterpret_cast<sockaddr*>(&saddr), static_cast<int>(len));
  UpdateLastError();
  MaybeRemapSendError();
  // We have seen minidumps where this may be false.
  RTC_DCHECK(sent <= static_cast<int>(length));
  if ((sent > 0 && sent < static_cast<int>(length)) ||
      (sent < 0 && IsBlockingError(GetError()))) {
    EnableEvents(DE_WRITE);
  }
  return sent;
}

int PhysicalSocket::Recv(void* buffer, size_t length, int64_t* timestamp) {
  int received =
      ::recv(s_, static_cast<char*>(buffer), static_cast<int>(length), 0);
  if ((received == 0) && (length != 0)) {
    // Note: on graceful shutdown, recv can return 0.  In this case, we
    // pretend it is blocking, and then signal close, so that simplifying
    // assumptions can be made about Recv.
    RTC_LOG(LS_WARNING) << "EOF from socket; deferring close event";
    // Must turn this back on so that the select() loop will notice the close
    // event.
    EnableEvents(DE_READ);
    SetError(EWOULDBLOCK);
    return SOCKET_ERROR;
  }
  if (timestamp) {
    *timestamp = GetSocketRecvTimestamp(s_);
  }
  UpdateLastError();
  int error = GetError();
  bool success = (received >= 0) || IsBlockingError(error);
  if (udp_ || success) {
    EnableEvents(DE_READ);
  }
  if (!success) {
    RTC_LOG_F(LS_VERBOSE) << "Error = " << error;
  }
  return received;
}

int PhysicalSocket::RecvFrom(void* buffer,
                             size_t length,
                             SocketAddress* out_addr,
                             int64_t* timestamp) {
  sockaddr_storage addr_storage;
  socklen_t addr_len = sizeof(addr_storage);
  sockaddr* addr = reinterpret_cast<sockaddr*>(&addr_storage);
  int received = ::recvfrom(s_, static_cast<char*>(buffer),
                            static_cast<int>(length), 0, addr, &addr_len);
  if (timestamp) {
    *timestamp = GetSocketRecvTimestamp(s_);
  }
  UpdateLastError();
  if ((received >= 0) && (out_addr != nullptr))
    SocketAddressFromSockAddrStorage(addr_storage, out_addr);
  int error = GetError();
  bool success = (received >= 0) || IsBlockingError(error);
  if (udp_ || success) {
    EnableEvents(DE_READ);
  }
  if (!success) {
    RTC_LOG_F(LS_VERBOSE) << "Error = " << error;
  }
  return received;
}

int PhysicalSocket::Listen(int backlog) {
  int err = ::listen(s_, backlog);
  UpdateLastError();
  if (err == 0) {
    state_ = CS_CONNECTING;
    EnableEvents(DE_ACCEPT);
#if !defined(NDEBUG)
    dbg_addr_ = "Listening @ ";
    dbg_addr_.append(GetLocalAddress().ToString());
#endif
  }
  return err;
}

AsyncSocket* PhysicalSocket::Accept(SocketAddress* out_addr) {
  // Always re-subscribe DE_ACCEPT to make sure new incoming connections will
  // trigger an event even if DoAccept returns an error here.
  EnableEvents(DE_ACCEPT);
  sockaddr_storage addr_storage;
  socklen_t addr_len = sizeof(addr_storage);
  sockaddr* addr = reinterpret_cast<sockaddr*>(&addr_storage);
  SOCKET s = DoAccept(s_, addr, &addr_len);
  UpdateLastError();
  if (s == INVALID_SOCKET)
    return nullptr;
  if (out_addr != nullptr)
    SocketAddressFromSockAddrStorage(addr_storage, out_addr);
  return ss_->WrapSocket(s);
}

int PhysicalSocket::Close() {
  if (s_ == INVALID_SOCKET)
    return 0;
  int err = ::closesocket(s_);
  UpdateLastError();
  s_ = INVALID_SOCKET;
  state_ = CS_CLOSED;
  SetEnabledEvents(0);
  if (resolver_) {
    resolver_->Destroy(false);
    resolver_ = nullptr;
  }
  return err;
}

SOCKET PhysicalSocket::DoAccept(SOCKET socket,
                                sockaddr* addr,
                                socklen_t* addrlen) {
  return ::accept(socket, addr, addrlen);
}

int PhysicalSocket::DoSend(SOCKET socket, const char* buf, int len, int flags) {
  return ::send(socket, buf, len, flags);
}

int PhysicalSocket::DoSendTo(SOCKET socket,
                             const char* buf,
                             int len,
                             int flags,
                             const struct sockaddr* dest_addr,
                             socklen_t addrlen) {
  return ::sendto(socket, buf, len, flags, dest_addr, addrlen);
}

void PhysicalSocket::OnResolveResult(AsyncResolverInterface* resolver) {
  if (resolver != resolver_) {
    return;
  }

  int error = resolver_->GetError();
  if (error == 0) {
    error = DoConnect(resolver_->address());
  } else {
    Close();
  }

  if (error) {
    SetError(error);
    SignalCloseEvent(this, error);
  }
}

void PhysicalSocket::UpdateLastError() {
  SetError(LAST_SYSTEM_ERROR);
}

void PhysicalSocket::MaybeRemapSendError() {
#if defined(WEBRTC_MAC)
  // https://developer.apple.com/library/mac/documentation/Darwin/
  // Reference/ManPages/man2/sendto.2.html
  // ENOBUFS - The output queue for a network interface is full.
  // This generally indicates that the interface has stopped sending,
  // but may be caused by transient congestion.
  if (GetError() == ENOBUFS) {
    SetError(EWOULDBLOCK);
  }
#endif
}

void PhysicalSocket::SetEnabledEvents(uint8_t events) {
  enabled_events_ = events;
}

void PhysicalSocket::EnableEvents(uint8_t events) {
  enabled_events_ |= events;
}

void PhysicalSocket::DisableEvents(uint8_t events) {
  enabled_events_ &= ~events;
}

int PhysicalSocket::TranslateOption(Option opt, int* slevel, int* sopt) {
  switch (opt) {
    case OPT_DONTFRAGMENT:
#if defined(WEBRTC_WIN)
      *slevel = IPPROTO_IP;
      *sopt = IP_DONTFRAGMENT;
      break;
#elif defined(WEBRTC_MAC) || defined(BSD) || defined(__native_client__)
      RTC_LOG(LS_WARNING) << "Socket::OPT_DONTFRAGMENT not supported.";
      return -1;
#elif defined(WEBRTC_POSIX)
      *slevel = IPPROTO_IP;
      *sopt = IP_MTU_DISCOVER;
      break;
#endif
    case OPT_RCVBUF:
      *slevel = SOL_SOCKET;
      *sopt = SO_RCVBUF;
      break;
    case OPT_SNDBUF:
      *slevel = SOL_SOCKET;
      *sopt = SO_SNDBUF;
      break;
    case OPT_NODELAY:
      *slevel = IPPROTO_TCP;
      *sopt = TCP_NODELAY;
      break;
    case OPT_DSCP:
#if defined(WEBRTC_POSIX)
      if (family_ == AF_INET6) {
        *slevel = IPPROTO_IPV6;
        *sopt = IPV6_TCLASS;
      } else {
        *slevel = IPPROTO_IP;
        *sopt = IP_TOS;
      }
      break;
#else
      RTC_LOG(LS_WARNING) << "Socket::OPT_DSCP not supported.";
      return -1;
#endif
    case OPT_RTP_SENDTIME_EXTN_ID:
      return -1;  // No logging is necessary as this not a OS socket option.
    default:
      RTC_NOTREACHED();
      return -1;
  }
  return 0;
}

SocketDispatcher::SocketDispatcher(PhysicalSocketServer* ss)
#if defined(WEBRTC_WIN)
    : PhysicalSocket(ss),
      id_(0),
      signal_close_(false)
#else
    : PhysicalSocket(ss)
#endif
{
}

SocketDispatcher::SocketDispatcher(SOCKET s, PhysicalSocketServer* ss)
#if defined(WEBRTC_WIN)
    : PhysicalSocket(ss, s),
      id_(0),
      signal_close_(false)
#else
    : PhysicalSocket(ss, s)
#endif
{
}

SocketDispatcher::~SocketDispatcher() {
  Close();
}

bool SocketDispatcher::Initialize() {
  RTC_DCHECK(s_ != INVALID_SOCKET);
// Must be a non-blocking
#if defined(WEBRTC_WIN)
  u_long argp = 1;
  ioctlsocket(s_, FIONBIO, &argp);
#elif defined(WEBRTC_POSIX)
  fcntl(s_, F_SETFL, fcntl(s_, F_GETFL, 0) | O_NONBLOCK);
#endif
#if defined(WEBRTC_IOS)
  // iOS may kill sockets when the app is moved to the background
  // (specifically, if the app doesn't use the "voip" UIBackgroundMode). When
  // we attempt to write to such a socket, SIGPIPE will be raised, which by
  // default will terminate the process, which we don't want. By specifying
  // this socket option, SIGPIPE will be disabled for the socket.
  int value = 1;
  ::setsockopt(s_, SOL_SOCKET, SO_NOSIGPIPE, &value, sizeof(value));
#endif
  ss_->Add(this);
  return true;
}

bool SocketDispatcher::Create(int type) {
  return Create(AF_INET, type);
}

bool SocketDispatcher::Create(int family, int type) {
  // Change the socket to be non-blocking.
  if (!PhysicalSocket::Create(family, type))
    return false;

  if (!Initialize())
    return false;

#if defined(WEBRTC_WIN)
  do {
    id_ = ++next_id_;
  } while (id_ == 0);
#endif
  return true;
}

#if defined(WEBRTC_WIN)

WSAEVENT SocketDispatcher::GetWSAEvent() {
  return WSA_INVALID_EVENT;
}

SOCKET SocketDispatcher::GetSocket() {
  return s_;
}

bool SocketDispatcher::CheckSignalClose() {
  if (!signal_close_)
    return false;

  char ch;
  if (recv(s_, &ch, 1, MSG_PEEK) > 0)
    return false;

  state_ = CS_CLOSED;
  signal_close_ = false;
  SignalCloseEvent(this, signal_err_);
  return true;
}

int SocketDispatcher::next_id_ = 0;

#elif defined(WEBRTC_POSIX)

int SocketDispatcher::GetDescriptor() {
  return s_;
}

bool SocketDispatcher::IsDescriptorClosed() {
  if (udp_) {
    // The MSG_PEEK trick doesn't work for UDP, since (at least in some
    // circumstances) it requires reading an entire UDP packet, which would be
    // bad for performance here. So, just check whether |s_| has been closed,
    // which should be sufficient.
    return s_ == INVALID_SOCKET;
  }
  // We don't have a reliable way of distinguishing end-of-stream
  // from readability.  So test on each readable call.  Is this
  // inefficient?  Probably.
  char ch;
  ssize_t res = ::recv(s_, &ch, 1, MSG_PEEK);
  if (res > 0) {
    // Data available, so not closed.
    return false;
  } else if (res == 0) {
    // EOF, so closed.
    return true;
  } else {  // error
    switch (errno) {
      // Returned if we've already closed s_.
      case EBADF:
      // Returned during ungraceful peer shutdown.
      case ECONNRESET:
        return true;
      // The normal blocking error; don't log anything.
      case EWOULDBLOCK:
      // Interrupted system call.
      case EINTR:
        return false;
      default:
        // Assume that all other errors are just blocking errors, meaning the
        // connection is still good but we just can't read from it right now.
        // This should only happen when connecting (and at most once), because
        // in all other cases this function is only called if the file
        // descriptor is already known to be in the readable state. However,
        // it's not necessary a problem if we spuriously interpret a
        // "connection lost"-type error as a blocking error, because typically
        // the next recv() will get EOF, so we'll still eventually notice that
        // the socket is closed.
        RTC_LOG_ERR(LS_WARNING) << "Assuming benign blocking error";
        return false;
    }
  }
}

#endif  // WEBRTC_POSIX

uint32_t SocketDispatcher::GetRequestedEvents() {
  return enabled_events();
}

#if defined(WEBRTC_WIN)

void SocketDispatcher::OnEvent(uint32_t ff, int err) {
  if ((ff & DE_CONNECT) != 0)
    state_ = CS_CONNECTED;

  // We set CS_CLOSED from CheckSignalClose.

  int cache_id = id_;
  // Make sure we deliver connect/accept first. Otherwise, consumers may see
  // something like a READ followed by a CONNECT, which would be odd.
  if (((ff & DE_CONNECT) != 0) && (id_ == cache_id)) {
    if (ff != DE_CONNECT)
      RTC_LOG(LS_VERBOSE) << "Signalled with DE_CONNECT: " << ff;
    DisableEvents(DE_CONNECT);
#if !defined(NDEBUG)
    dbg_addr_ = "Connected @ ";
    dbg_addr_.append(GetRemoteAddress().ToString());
#endif
    SignalConnectEvent(this);
  }
  if (((ff & DE_ACCEPT) != 0) && (id_ == cache_id)) {
    DisableEvents(DE_ACCEPT);
    SignalReadEvent(this);
  }
  if ((ff & DE_READ) != 0) {
    DisableEvents(DE_READ);
    SignalReadEvent(this);
  }
  if (((ff & DE_WRITE) != 0) && (id_ == cache_id)) {
    DisableEvents(DE_WRITE);
    SignalWriteEvent(this);
  }
  if (((ff & DE_CLOSE) != 0) && (id_ == cache_id)) {
    signal_close_ = true;
    signal_err_ = err;
  }
}

#elif defined(WEBRTC_POSIX)

void SocketDispatcher::OnEvent(uint32_t ff, int err) {
  if ((ff & DE_CONNECT) != 0)
    state_ = CS_CONNECTED;

  if ((ff & DE_CLOSE) != 0)
    state_ = CS_CLOSED;

#if defined(WEBRTC_USE_EPOLL)
  // Remember currently enabled events so we can combine multiple changes
  // into one update call later.
  // The signal handlers might re-enable events disabled here, so we can't
  // keep a list of events to disable at the end of the method. This list
  // would not be updated with the events enabled by the signal handlers.
  StartBatchedEventUpdates();
#endif
  // Make sure we deliver connect/accept first. Otherwise, consumers may see
  // something like a READ followed by a CONNECT, which would be odd.
  if ((ff & DE_CONNECT) != 0) {
    DisableEvents(DE_CONNECT);
    SignalConnectEvent(this);
  }
  if ((ff & DE_ACCEPT) != 0) {
    DisableEvents(DE_ACCEPT);
    SignalReadEvent(this);
  }
  if ((ff & DE_READ) != 0) {
    DisableEvents(DE_READ);
    SignalReadEvent(this);
  }
  if ((ff & DE_WRITE) != 0) {
    DisableEvents(DE_WRITE);
    SignalWriteEvent(this);
  }
  if ((ff & DE_CLOSE) != 0) {
    // The socket is now dead to us, so stop checking it.
    SetEnabledEvents(0);
    SignalCloseEvent(this, err);
  }
#if defined(WEBRTC_USE_EPOLL)
  FinishBatchedEventUpdates();
#endif
}

#endif  // WEBRTC_POSIX

#if defined(WEBRTC_USE_EPOLL)

inline static int GetEpollEvents(uint32_t ff) {
  int events = 0;
  if (ff & (DE_READ | DE_ACCEPT)) {
    events |= EPOLLIN;
  }
  if (ff & (DE_WRITE | DE_CONNECT)) {
    events |= EPOLLOUT;
  }
  return events;
}

void SocketDispatcher::StartBatchedEventUpdates() {
  RTC_DCHECK_EQ(saved_enabled_events_, -1);
  saved_enabled_events_ = enabled_events();
}

void SocketDispatcher::FinishBatchedEventUpdates() {
  RTC_DCHECK_NE(saved_enabled_events_, -1);
  uint8_t old_events = static_cast<uint8_t>(saved_enabled_events_);
  saved_enabled_events_ = -1;
  MaybeUpdateDispatcher(old_events);
}

void SocketDispatcher::MaybeUpdateDispatcher(uint8_t old_events) {
  if (GetEpollEvents(enabled_events()) != GetEpollEvents(old_events) &&
      saved_enabled_events_ == -1) {
    ss_->Update(this);
  }
}

void SocketDispatcher::SetEnabledEvents(uint8_t events) {
  uint8_t old_events = enabled_events();
  PhysicalSocket::SetEnabledEvents(events);
  MaybeUpdateDispatcher(old_events);
}

void SocketDispatcher::EnableEvents(uint8_t events) {
  uint8_t old_events = enabled_events();
  PhysicalSocket::EnableEvents(events);
  MaybeUpdateDispatcher(old_events);
}

void SocketDispatcher::DisableEvents(uint8_t events) {
  uint8_t old_events = enabled_events();
  PhysicalSocket::DisableEvents(events);
  MaybeUpdateDispatcher(old_events);
}

#endif  // WEBRTC_USE_EPOLL

int SocketDispatcher::Close() {
  if (s_ == INVALID_SOCKET)
    return 0;

#if defined(WEBRTC_WIN)
  id_ = 0;
  signal_close_ = false;
#endif
#if defined(WEBRTC_USE_EPOLL)
  // If we're batching events, the socket can be closed and reopened
  // during the batch. Set saved_enabled_events_ to 0 here so the new
  // socket, if any, has the correct old events bitfield
  if (saved_enabled_events_ != -1) {
    saved_enabled_events_ = 0;
  }
#endif
  ss_->Remove(this);
  return PhysicalSocket::Close();
}

#if defined(WEBRTC_POSIX)
// Sets the value of a boolean value to false when signaled.
class Signaler : public Dispatcher {
 public:
  Signaler(PhysicalSocketServer* ss, bool& flag_to_clear)
      : ss_(ss),
        afd_([] {
          std::array<int, 2> afd = {-1, -1};

          if (pipe(afd.data()) < 0) {
            RTC_LOG(LERROR) << "pipe failed";
          }
          return afd;
        }()),
        fSignaled_(false),
        flag_to_clear_(flag_to_clear) {
    ss_->Add(this);
  }

  ~Signaler() override {
    ss_->Remove(this);
    close(afd_[0]);
    close(afd_[1]);
  }

  virtual void Signal() {
    webrtc::MutexLock lock(&mutex_);
    if (!fSignaled_) {
      const uint8_t b[1] = {0};
      const ssize_t res = write(afd_[1], b, sizeof(b));
      RTC_DCHECK_EQ(1, res);
      fSignaled_ = true;
    }
  }

  uint32_t GetRequestedEvents() override { return DE_READ; }

  void OnEvent(uint32_t ff, int err) override {
    // It is not possible to perfectly emulate an auto-resetting event with
    // pipes.  This simulates it by resetting before the event is handled.

    webrtc::MutexLock lock(&mutex_);
    if (fSignaled_) {
      uint8_t b[4];  // Allow for reading more than 1 byte, but expect 1.
      const ssize_t res = read(afd_[0], b, sizeof(b));
      RTC_DCHECK_EQ(1, res);
      fSignaled_ = false;
    }
    flag_to_clear_ = false;
  }

  int GetDescriptor() override { return afd_[0]; }

  bool IsDescriptorClosed() override { return false; }

 private:
  PhysicalSocketServer* const ss_;
  const std::array<int, 2> afd_;
  bool fSignaled_ RTC_GUARDED_BY(mutex_);
  webrtc::Mutex mutex_;
  bool& flag_to_clear_;
};

#endif  // WEBRTC_POSIX

#if defined(WEBRTC_WIN)
static uint32_t FlagsToEvents(uint32_t events) {
  uint32_t ffFD = FD_CLOSE;
  if (events & DE_READ)
    ffFD |= FD_READ;
  if (events & DE_WRITE)
    ffFD |= FD_WRITE;
  if (events & DE_CONNECT)
    ffFD |= FD_CONNECT;
  if (events & DE_ACCEPT)
    ffFD |= FD_ACCEPT;
  return ffFD;
}

// Sets the value of a boolean value to false when signaled.
class Signaler : public Dispatcher {
 public:
  Signaler(PhysicalSocketServer* ss, bool& flag_to_clear)
      : ss_(ss), flag_to_clear_(flag_to_clear) {
    hev_ = WSACreateEvent();
    if (hev_) {
      ss_->Add(this);
    }
  }

  ~Signaler() override {
    if (hev_ != nullptr) {
      ss_->Remove(this);
      WSACloseEvent(hev_);
      hev_ = nullptr;
    }
  }

  virtual void Signal() {
    if (hev_ != nullptr)
      WSASetEvent(hev_);
  }

  uint32_t GetRequestedEvents() override { return 0; }

  void OnEvent(uint32_t ff, int err) override {
    WSAResetEvent(hev_);
    flag_to_clear_ = false;
  }

  WSAEVENT GetWSAEvent() override { return hev_; }

  SOCKET GetSocket() override { return INVALID_SOCKET; }

  bool CheckSignalClose() override { return false; }

 private:
  PhysicalSocketServer* ss_;
  WSAEVENT hev_;
  bool& flag_to_clear_;
};
#endif  // WEBRTC_WIN

PhysicalSocketServer::PhysicalSocketServer()
    :
#if defined(WEBRTC_USE_EPOLL)
      // Since Linux 2.6.8, the size argument is ignored, but must be greater
      // than zero. Before that the size served as hint to the kernel for the
      // amount of space to initially allocate in internal data structures.
      epoll_fd_(epoll_create(FD_SETSIZE)),
#endif
#if defined(WEBRTC_WIN)
      socket_ev_(WSACreateEvent()),
#endif
      fWait_(false) {
#if defined(WEBRTC_USE_EPOLL)
  if (epoll_fd_ == -1) {
    // Not an error, will fall back to "select" below.
    RTC_LOG_E(LS_WARNING, EN, errno) << "epoll_create";
    // Note that -1 == INVALID_SOCKET, the alias used by later checks.
  }
#endif
  // The `fWait_` flag to be cleared by the Signaler.
  signal_wakeup_ = new Signaler(this, fWait_);
}

PhysicalSocketServer::~PhysicalSocketServer() {
#if defined(WEBRTC_WIN)
  WSACloseEvent(socket_ev_);
#endif
  delete signal_wakeup_;
#if defined(WEBRTC_USE_EPOLL)
  if (epoll_fd_ != INVALID_SOCKET) {
    close(epoll_fd_);
  }
#endif
  RTC_DCHECK(dispatcher_by_key_.empty());
  RTC_DCHECK(key_by_dispatcher_.empty());
}

void PhysicalSocketServer::WakeUp() {
  signal_wakeup_->Signal();
}

Socket* PhysicalSocketServer::CreateSocket(int family, int type) {
  PhysicalSocket* socket = new PhysicalSocket(this);
  if (socket->Create(family, type)) {
    return socket;
  } else {
    delete socket;
    return nullptr;
  }
}

AsyncSocket* PhysicalSocketServer::CreateAsyncSocket(int family, int type) {
  SocketDispatcher* dispatcher = new SocketDispatcher(this);
  if (dispatcher->Create(family, type)) {
    return dispatcher;
  } else {
    delete dispatcher;
    return nullptr;
  }
}

AsyncSocket* PhysicalSocketServer::WrapSocket(SOCKET s) {
  SocketDispatcher* dispatcher = new SocketDispatcher(s, this);
  if (dispatcher->Initialize()) {
    return dispatcher;
  } else {
    delete dispatcher;
    return nullptr;
  }
}

void PhysicalSocketServer::Add(Dispatcher* pdispatcher) {
  CritScope cs(&crit_);
  if (key_by_dispatcher_.count(pdispatcher)) {
    RTC_LOG(LS_WARNING)
        << "PhysicalSocketServer asked to add a duplicate dispatcher.";
    return;
  }
  uint64_t key = next_dispatcher_key_++;
  dispatcher_by_key_.emplace(key, pdispatcher);
  key_by_dispatcher_.emplace(pdispatcher, key);
#if defined(WEBRTC_USE_EPOLL)
  if (epoll_fd_ != INVALID_SOCKET) {
    AddEpoll(pdispatcher, key);
  }
#endif  // WEBRTC_USE_EPOLL
}

void PhysicalSocketServer::Remove(Dispatcher* pdispatcher) {
  CritScope cs(&crit_);
  if (!key_by_dispatcher_.count(pdispatcher)) {
    RTC_LOG(LS_WARNING)
        << "PhysicalSocketServer asked to remove a unknown "
           "dispatcher, potentially from a duplicate call to Add.";
    return;
  }
  uint64_t key = key_by_dispatcher_.at(pdispatcher);
  key_by_dispatcher_.erase(pdispatcher);
  dispatcher_by_key_.erase(key);
#if defined(WEBRTC_USE_EPOLL)
  if (epoll_fd_ != INVALID_SOCKET) {
    RemoveEpoll(pdispatcher);
  }
#endif  // WEBRTC_USE_EPOLL
}

void PhysicalSocketServer::Update(Dispatcher* pdispatcher) {
#if defined(WEBRTC_USE_EPOLL)
  if (epoll_fd_ == INVALID_SOCKET) {
    return;
  }

  // Don't update dispatchers that haven't yet been added.
  CritScope cs(&crit_);
  if (!key_by_dispatcher_.count(pdispatcher)) {
    return;
  }

  UpdateEpoll(pdispatcher, key_by_dispatcher_.at(pdispatcher));
#endif
}

#if defined(WEBRTC_POSIX)

bool PhysicalSocketServer::Wait(int cmsWait, bool process_io) {
  // We don't support reentrant waiting.
  RTC_DCHECK(!waiting_);
  ScopedSetTrue s(&waiting_);
#if defined(WEBRTC_USE_EPOLL)
  // We don't keep a dedicated "epoll" descriptor containing only the non-IO
  // (i.e. signaling) dispatcher, so "poll" will be used instead of the default
  // "select" to support sockets larger than FD_SETSIZE.
  if (!process_io) {
    return WaitPoll(cmsWait, signal_wakeup_);
  } else if (epoll_fd_ != INVALID_SOCKET) {
    return WaitEpoll(cmsWait);
  }
#endif
  return WaitSelect(cmsWait, process_io);
}

static void ProcessEvents(Dispatcher* dispatcher,
                          bool readable,
                          bool writable,
                          bool check_error) {
  int errcode = 0;
  // TODO(pthatcher): Should we set errcode if getsockopt fails?
  if (check_error) {
    socklen_t len = sizeof(errcode);
    ::getsockopt(dispatcher->GetDescriptor(), SOL_SOCKET, SO_ERROR, &errcode,
                 &len);
  }

  // Most often the socket is writable or readable or both, so make a single
  // virtual call to get requested events
  const uint32_t requested_events = dispatcher->GetRequestedEvents();
  uint32_t ff = 0;

  // Check readable descriptors. If we're waiting on an accept, signal
  // that. Otherwise we're waiting for data, check to see if we're
  // readable or really closed.
  // TODO(pthatcher): Only peek at TCP descriptors.
  if (readable) {
    if (requested_events & DE_ACCEPT) {
      ff |= DE_ACCEPT;
    } else if (errcode || dispatcher->IsDescriptorClosed()) {
      ff |= DE_CLOSE;
    } else {
      ff |= DE_READ;
    }
  }

  // Check writable descriptors. If we're waiting on a connect, detect
  // success versus failure by the reaped error code.
  if (writable) {
    if (requested_events & DE_CONNECT) {
      if (!errcode) {
        ff |= DE_CONNECT;
      } else {
        ff |= DE_CLOSE;
      }
    } else {
      ff |= DE_WRITE;
    }
  }

  // Tell the descriptor about the event.
  if (ff != 0) {
    dispatcher->OnEvent(ff, errcode);
  }
}

bool PhysicalSocketServer::WaitSelect(int cmsWait, bool process_io) {
  // Calculate timing information

  struct timeval* ptvWait = nullptr;
  struct timeval tvWait;
  int64_t stop_us;
  if (cmsWait != kForever) {
    // Calculate wait timeval
    tvWait.tv_sec = cmsWait / 1000;
    tvWait.tv_usec = (cmsWait % 1000) * 1000;
    ptvWait = &tvWait;

    // Calculate when to return
    stop_us = rtc::TimeMicros() + cmsWait * 1000;
  }


  fd_set fdsRead;
  fd_set fdsWrite;
// Explicitly unpoison these FDs on MemorySanitizer which doesn't handle the
// inline assembly in FD_ZERO.
// http://crbug.com/344505
#ifdef MEMORY_SANITIZER
  __msan_unpoison(&fdsRead, sizeof(fdsRead));
  __msan_unpoison(&fdsWrite, sizeof(fdsWrite));
#endif

  fWait_ = true;

  while (fWait_) {
    // Zero all fd_sets. Although select() zeros the descriptors not signaled,
    // we may need to do this for dispatchers that were deleted while
    // iterating.
    FD_ZERO(&fdsRead);
    FD_ZERO(&fdsWrite);
    int fdmax = -1;
    {
      CritScope cr(&crit_);
      current_dispatcher_keys_.clear();
      for (auto const& kv : dispatcher_by_key_) {
        uint64_t key = kv.first;
        Dispatcher* pdispatcher = kv.second;
        // Query dispatchers for read and write wait state
        if (!process_io && (pdispatcher != signal_wakeup_))
          continue;
        current_dispatcher_keys_.push_back(key);
        int fd = pdispatcher->GetDescriptor();
        // "select"ing a file descriptor that is equal to or larger than
        // FD_SETSIZE will result in undefined behavior.
        RTC_DCHECK_LT(fd, FD_SETSIZE);
        if (fd > fdmax)
          fdmax = fd;

        uint32_t ff = pdispatcher->GetRequestedEvents();
        if (ff & (DE_READ | DE_ACCEPT))
          FD_SET(fd, &fdsRead);
        if (ff & (DE_WRITE | DE_CONNECT))
          FD_SET(fd, &fdsWrite);
      }
    }

    // Wait then call handlers as appropriate
    // < 0 means error
    // 0 means timeout
    // > 0 means count of descriptors ready
    int n = select(fdmax + 1, &fdsRead, &fdsWrite, nullptr, ptvWait);

    // If error, return error.
    if (n < 0) {
      if (errno != EINTR) {
        RTC_LOG_E(LS_ERROR, EN, errno) << "select";
        return false;
      }
      // Else ignore the error and keep going. If this EINTR was for one of the
      // signals managed by this PhysicalSocketServer, the
      // PosixSignalDeliveryDispatcher will be in the signaled state in the next
      // iteration.
    } else if (n == 0) {
      // If timeout, return success
      return true;
    } else {
      // We have signaled descriptors
      CritScope cr(&crit_);
      // Iterate only on the dispatchers whose sockets were passed into
      // WSAEventSelect; this avoids the ABA problem (a socket being
      // destroyed and a new one created with the same file descriptor).
      for (uint64_t key : current_dispatcher_keys_) {
        if (!dispatcher_by_key_.count(key))
          continue;
        Dispatcher* pdispatcher = dispatcher_by_key_.at(key);

        int fd = pdispatcher->GetDescriptor();

        bool readable = FD_ISSET(fd, &fdsRead);
        if (readable) {
          FD_CLR(fd, &fdsRead);
        }

        bool writable = FD_ISSET(fd, &fdsWrite);
        if (writable) {
          FD_CLR(fd, &fdsWrite);
        }

        // The error code can be signaled through reads or writes.
        ProcessEvents(pdispatcher, readable, writable, readable || writable);
      }
    }

    // Recalc the time remaining to wait. Doing it here means it doesn't get
    // calced twice the first time through the loop
    if (ptvWait) {
      ptvWait->tv_sec = 0;
      ptvWait->tv_usec = 0;
      int64_t time_left_us = stop_us - rtc::TimeMicros();
      if (time_left_us > 0) {
        ptvWait->tv_sec = time_left_us / rtc::kNumMicrosecsPerSec;
        ptvWait->tv_usec = time_left_us % rtc::kNumMicrosecsPerSec;
      }
    }
  }

  return true;
}

#if defined(WEBRTC_USE_EPOLL)

void PhysicalSocketServer::AddEpoll(Dispatcher* pdispatcher, uint64_t key) {
  RTC_DCHECK(epoll_fd_ != INVALID_SOCKET);
  int fd = pdispatcher->GetDescriptor();
  RTC_DCHECK(fd != INVALID_SOCKET);
  if (fd == INVALID_SOCKET) {
    return;
  }

  struct epoll_event event = {0};
  event.events = GetEpollEvents(pdispatcher->GetRequestedEvents());
  event.data.u64 = key;
  int err = epoll_ctl(epoll_fd_, EPOLL_CTL_ADD, fd, &event);
  RTC_DCHECK_EQ(err, 0);
  if (err == -1) {
    RTC_LOG_E(LS_ERROR, EN, errno) << "epoll_ctl EPOLL_CTL_ADD";
  }
}

void PhysicalSocketServer::RemoveEpoll(Dispatcher* pdispatcher) {
  RTC_DCHECK(epoll_fd_ != INVALID_SOCKET);
  int fd = pdispatcher->GetDescriptor();
  RTC_DCHECK(fd != INVALID_SOCKET);
  if (fd == INVALID_SOCKET) {
    return;
  }

  struct epoll_event event = {0};
  int err = epoll_ctl(epoll_fd_, EPOLL_CTL_DEL, fd, &event);
  RTC_DCHECK(err == 0 || errno == ENOENT);
  if (err == -1) {
    if (errno == ENOENT) {
      // Socket has already been closed.
      RTC_LOG_E(LS_VERBOSE, EN, errno) << "epoll_ctl EPOLL_CTL_DEL";
    } else {
      RTC_LOG_E(LS_ERROR, EN, errno) << "epoll_ctl EPOLL_CTL_DEL";
    }
  }
}

void PhysicalSocketServer::UpdateEpoll(Dispatcher* pdispatcher, uint64_t key) {
  RTC_DCHECK(epoll_fd_ != INVALID_SOCKET);
  int fd = pdispatcher->GetDescriptor();
  RTC_DCHECK(fd != INVALID_SOCKET);
  if (fd == INVALID_SOCKET) {
    return;
  }

  struct epoll_event event = {0};
  event.events = GetEpollEvents(pdispatcher->GetRequestedEvents());
  event.data.u64 = key;
  int err = epoll_ctl(epoll_fd_, EPOLL_CTL_MOD, fd, &event);
  RTC_DCHECK_EQ(err, 0);
  if (err == -1) {
    RTC_LOG_E(LS_ERROR, EN, errno) << "epoll_ctl EPOLL_CTL_MOD";
  }
}

bool PhysicalSocketServer::WaitEpoll(int cmsWait) {
  RTC_DCHECK(epoll_fd_ != INVALID_SOCKET);
  int64_t tvWait = -1;
  int64_t tvStop = -1;
  if (cmsWait != kForever) {
    tvWait = cmsWait;
    tvStop = TimeAfter(cmsWait);
  }

  fWait_ = true;
  while (fWait_) {
    // Wait then call handlers as appropriate
    // < 0 means error
    // 0 means timeout
    // > 0 means count of descriptors ready
    int n = epoll_wait(epoll_fd_, epoll_events_.data(), epoll_events_.size(),
                       static_cast<int>(tvWait));
    if (n < 0) {
      if (errno != EINTR) {
        RTC_LOG_E(LS_ERROR, EN, errno) << "epoll";
        return false;
      }
      // Else ignore the error and keep going. If this EINTR was for one of the
      // signals managed by this PhysicalSocketServer, the
      // PosixSignalDeliveryDispatcher will be in the signaled state in the next
      // iteration.
    } else if (n == 0) {
      // If timeout, return success
      return true;
    } else {
      // We have signaled descriptors
      CritScope cr(&crit_);
      for (int i = 0; i < n; ++i) {
        const epoll_event& event = epoll_events_[i];
        uint64_t key = event.data.u64;
        if (!dispatcher_by_key_.count(key)) {
          // The dispatcher for this socket no longer exists.
          continue;
        }
        Dispatcher* pdispatcher = dispatcher_by_key_.at(key);

        bool readable = (event.events & (EPOLLIN | EPOLLPRI));
        bool writable = (event.events & EPOLLOUT);
        bool check_error = (event.events & (EPOLLRDHUP | EPOLLERR | EPOLLHUP));

        ProcessEvents(pdispatcher, readable, writable, check_error);
      }
    }

    if (cmsWait != kForever) {
      tvWait = TimeDiff(tvStop, TimeMillis());
      if (tvWait <= 0) {
        // Return success on timeout.
        return true;
      }
    }
  }

  return true;
}

bool PhysicalSocketServer::WaitPoll(int cmsWait, Dispatcher* dispatcher) {
  RTC_DCHECK(dispatcher);
  int64_t tvWait = -1;
  int64_t tvStop = -1;
  if (cmsWait != kForever) {
    tvWait = cmsWait;
    tvStop = TimeAfter(cmsWait);
  }

  fWait_ = true;

  struct pollfd fds = {0};
  int fd = dispatcher->GetDescriptor();
  fds.fd = fd;

  while (fWait_) {
    uint32_t ff = dispatcher->GetRequestedEvents();
    fds.events = 0;
    if (ff & (DE_READ | DE_ACCEPT)) {
      fds.events |= POLLIN;
    }
    if (ff & (DE_WRITE | DE_CONNECT)) {
      fds.events |= POLLOUT;
    }
    fds.revents = 0;

    // Wait then call handlers as appropriate
    // < 0 means error
    // 0 means timeout
    // > 0 means count of descriptors ready
    int n = poll(&fds, 1, static_cast<int>(tvWait));
    if (n < 0) {
      if (errno != EINTR) {
        RTC_LOG_E(LS_ERROR, EN, errno) << "poll";
        return false;
      }
      // Else ignore the error and keep going. If this EINTR was for one of the
      // signals managed by this PhysicalSocketServer, the
      // PosixSignalDeliveryDispatcher will be in the signaled state in the next
      // iteration.
    } else if (n == 0) {
      // If timeout, return success
      return true;
    } else {
      // We have signaled descriptors (should only be the passed dispatcher).
      RTC_DCHECK_EQ(n, 1);
      RTC_DCHECK_EQ(fds.fd, fd);

      bool readable = (fds.revents & (POLLIN | POLLPRI));
      bool writable = (fds.revents & POLLOUT);
      bool check_error = (fds.revents & (POLLRDHUP | POLLERR | POLLHUP));

      ProcessEvents(dispatcher, readable, writable, check_error);
    }

    if (cmsWait != kForever) {
      tvWait = TimeDiff(tvStop, TimeMillis());
      if (tvWait < 0) {
        // Return success on timeout.
        return true;
      }
    }
  }

  return true;
}

#endif  // WEBRTC_USE_EPOLL

#endif  // WEBRTC_POSIX

#if defined(WEBRTC_WIN)
bool PhysicalSocketServer::Wait(int cmsWait, bool process_io) {
  // We don't support reentrant waiting.
  RTC_DCHECK(!waiting_);
  ScopedSetTrue s(&waiting_);

  int64_t cmsTotal = cmsWait;
  int64_t cmsElapsed = 0;
  int64_t msStart = Time();

  fWait_ = true;
  while (fWait_) {
    std::vector<WSAEVENT> events;
    std::vector<uint64_t> event_owners;

    events.push_back(socket_ev_);

    {
      CritScope cr(&crit_);
      // Get a snapshot of all current dispatchers; this is used to avoid the
      // ABA problem (see later comment) and avoids the dispatcher_by_key_
      // iterator being invalidated by calling CheckSignalClose, which may
      // remove the dispatcher from the list.
      current_dispatcher_keys_.clear();
      for (auto const& kv : dispatcher_by_key_) {
        current_dispatcher_keys_.push_back(kv.first);
      }
      for (uint64_t key : current_dispatcher_keys_) {
        if (!dispatcher_by_key_.count(key)) {
          continue;
        }
        Dispatcher* disp = dispatcher_by_key_.at(key);
        if (!disp)
          continue;
        if (!process_io && (disp != signal_wakeup_))
          continue;
        SOCKET s = disp->GetSocket();
        if (disp->CheckSignalClose()) {
          // We just signalled close, don't poll this socket.
        } else if (s != INVALID_SOCKET) {
          WSAEventSelect(s, events[0],
                         FlagsToEvents(disp->GetRequestedEvents()));
        } else {
          events.push_back(disp->GetWSAEvent());
          event_owners.push_back(key);
        }
      }
    }

    // Which is shorter, the delay wait or the asked wait?

    int64_t cmsNext;
    if (cmsWait == kForever) {
      cmsNext = cmsWait;
    } else {
      cmsNext = std::max<int64_t>(0, cmsTotal - cmsElapsed);
    }

    // Wait for one of the events to signal
    DWORD dw =
        WSAWaitForMultipleEvents(static_cast<DWORD>(events.size()), &events[0],
                                 false, static_cast<DWORD>(cmsNext), false);

    if (dw == WSA_WAIT_FAILED) {
      // Failed?
      // TODO(pthatcher): need a better strategy than this!
      WSAGetLastError();
      RTC_NOTREACHED();
      return false;
    } else if (dw == WSA_WAIT_TIMEOUT) {
      // Timeout?
      return true;
    } else {
      // Figure out which one it is and call it
      CritScope cr(&crit_);
      int index = dw - WSA_WAIT_EVENT_0;
      if (index > 0) {
        --index;  // The first event is the socket event
        uint64_t key = event_owners[index];
        if (!dispatcher_by_key_.count(key)) {
          // The dispatcher could have been removed while waiting for events.
          continue;
        }
        Dispatcher* disp = dispatcher_by_key_.at(key);
        disp->OnEvent(0, 0);
      } else if (process_io) {
        // Iterate only on the dispatchers whose sockets were passed into
        // WSAEventSelect; this avoids the ABA problem (a socket being
        // destroyed and a new one created with the same SOCKET handle).
        for (uint64_t key : current_dispatcher_keys_) {
          if (!dispatcher_by_key_.count(key)) {
            continue;
          }
          Dispatcher* disp = dispatcher_by_key_.at(key);
          SOCKET s = disp->GetSocket();
          if (s == INVALID_SOCKET)
            continue;

          WSANETWORKEVENTS wsaEvents;
          int err = WSAEnumNetworkEvents(s, events[0], &wsaEvents);
          if (err == 0) {
            {
              if ((wsaEvents.lNetworkEvents & FD_READ) &&
                  wsaEvents.iErrorCode[FD_READ_BIT] != 0) {
                RTC_LOG(WARNING)
                    << "PhysicalSocketServer got FD_READ_BIT error "
                    << wsaEvents.iErrorCode[FD_READ_BIT];
              }
              if ((wsaEvents.lNetworkEvents & FD_WRITE) &&
                  wsaEvents.iErrorCode[FD_WRITE_BIT] != 0) {
                RTC_LOG(WARNING)
                    << "PhysicalSocketServer got FD_WRITE_BIT error "
                    << wsaEvents.iErrorCode[FD_WRITE_BIT];
              }
              if ((wsaEvents.lNetworkEvents & FD_CONNECT) &&
                  wsaEvents.iErrorCode[FD_CONNECT_BIT] != 0) {
                RTC_LOG(WARNING)
                    << "PhysicalSocketServer got FD_CONNECT_BIT error "
                    << wsaEvents.iErrorCode[FD_CONNECT_BIT];
              }
              if ((wsaEvents.lNetworkEvents & FD_ACCEPT) &&
                  wsaEvents.iErrorCode[FD_ACCEPT_BIT] != 0) {
                RTC_LOG(WARNING)
                    << "PhysicalSocketServer got FD_ACCEPT_BIT error "
                    << wsaEvents.iErrorCode[FD_ACCEPT_BIT];
              }
              if ((wsaEvents.lNetworkEvents & FD_CLOSE) &&
                  wsaEvents.iErrorCode[FD_CLOSE_BIT] != 0) {
                RTC_LOG(WARNING)
                    << "PhysicalSocketServer got FD_CLOSE_BIT error "
                    << wsaEvents.iErrorCode[FD_CLOSE_BIT];
              }
            }
            uint32_t ff = 0;
            int errcode = 0;
            if (wsaEvents.lNetworkEvents & FD_READ)
              ff |= DE_READ;
            if (wsaEvents.lNetworkEvents & FD_WRITE)
              ff |= DE_WRITE;
            if (wsaEvents.lNetworkEvents & FD_CONNECT) {
              if (wsaEvents.iErrorCode[FD_CONNECT_BIT] == 0) {
                ff |= DE_CONNECT;
              } else {
                ff |= DE_CLOSE;
                errcode = wsaEvents.iErrorCode[FD_CONNECT_BIT];
              }
            }
            if (wsaEvents.lNetworkEvents & FD_ACCEPT)
              ff |= DE_ACCEPT;
            if (wsaEvents.lNetworkEvents & FD_CLOSE) {
              ff |= DE_CLOSE;
              errcode = wsaEvents.iErrorCode[FD_CLOSE_BIT];
            }
            if (ff != 0) {
              disp->OnEvent(ff, errcode);
            }
          }
        }
      }

      // Reset the network event until new activity occurs
      WSAResetEvent(socket_ev_);
    }

    // Break?
    if (!fWait_)
      break;
    cmsElapsed = TimeSince(msStart);
    if ((cmsWait != kForever) && (cmsElapsed >= cmsWait)) {
      break;
    }
  }

  // Done
  return true;
}
#endif  // WEBRTC_WIN

}  // namespace rtc

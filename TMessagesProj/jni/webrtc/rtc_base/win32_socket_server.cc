/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/win32_socket_server.h"

#include <ws2tcpip.h>  // NOLINT

#include <algorithm>

#include "rtc_base/byte_order.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"  // For Time, TimeSince
#include "rtc_base/win32_window.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////
// Win32Socket
///////////////////////////////////////////////////////////////////////////////

// TODO: Enable for production builds also? Use FormatMessage?
#if !defined(NDEBUG)
LPCSTR WSAErrorToString(int error, LPCSTR* description_result) {
  LPCSTR string = "Unspecified";
  LPCSTR description = "Unspecified description";
  switch (error) {
    case ERROR_SUCCESS:
      string = "SUCCESS";
      description = "Operation succeeded";
      break;
    case WSAEWOULDBLOCK:
      string = "WSAEWOULDBLOCK";
      description = "Using a non-blocking socket, will notify later";
      break;
    case WSAEACCES:
      string = "WSAEACCES";
      description = "Access denied, or sharing violation";
      break;
    case WSAEADDRNOTAVAIL:
      string = "WSAEADDRNOTAVAIL";
      description = "Address is not valid in this context";
      break;
    case WSAENETDOWN:
      string = "WSAENETDOWN";
      description = "Network is down";
      break;
    case WSAENETUNREACH:
      string = "WSAENETUNREACH";
      description = "Network is up, but unreachable";
      break;
    case WSAENETRESET:
      string = "WSANETRESET";
      description = "Connection has been reset due to keep-alive activity";
      break;
    case WSAECONNABORTED:
      string = "WSAECONNABORTED";
      description = "Aborted by host";
      break;
    case WSAECONNRESET:
      string = "WSAECONNRESET";
      description = "Connection reset by host";
      break;
    case WSAETIMEDOUT:
      string = "WSAETIMEDOUT";
      description = "Timed out, host failed to respond";
      break;
    case WSAECONNREFUSED:
      string = "WSAECONNREFUSED";
      description = "Host actively refused connection";
      break;
    case WSAEHOSTDOWN:
      string = "WSAEHOSTDOWN";
      description = "Host is down";
      break;
    case WSAEHOSTUNREACH:
      string = "WSAEHOSTUNREACH";
      description = "Host is unreachable";
      break;
    case WSAHOST_NOT_FOUND:
      string = "WSAHOST_NOT_FOUND";
      description = "No such host is known";
      break;
  }
  if (description_result) {
    *description_result = description;
  }
  return string;
}

void ReportWSAError(LPCSTR context, int error, const SocketAddress& address) {
  LPCSTR description_string;
  LPCSTR error_string = WSAErrorToString(error, &description_string);
  RTC_LOG(LS_INFO) << context << " = " << error << " (" << error_string << ":"
                   << description_string << ") [" << address.ToString() << "]";
}
#else
void ReportWSAError(LPCSTR context, int error, const SocketAddress& address) {}
#endif

/////////////////////////////////////////////////////////////////////////////
// Win32Socket::EventSink
/////////////////////////////////////////////////////////////////////////////

#define WM_SOCKETNOTIFY (WM_USER + 50)
#define WM_DNSNOTIFY (WM_USER + 51)

struct Win32Socket::DnsLookup {
  HANDLE handle;
  uint16_t port;
  char buffer[MAXGETHOSTSTRUCT];
};

class Win32Socket::EventSink : public Win32Window {
 public:
  explicit EventSink(Win32Socket* parent) : parent_(parent) {}

  void Dispose();

  bool OnMessage(UINT uMsg,
                 WPARAM wParam,
                 LPARAM lParam,
                 LRESULT& result) override;
  void OnNcDestroy() override;

 private:
  bool OnSocketNotify(UINT uMsg, WPARAM wParam, LPARAM lParam, LRESULT& result);
  bool OnDnsNotify(WPARAM wParam, LPARAM lParam, LRESULT& result);

  Win32Socket* parent_;
};

void Win32Socket::EventSink::Dispose() {
  parent_ = nullptr;
  if (::IsWindow(handle())) {
    ::DestroyWindow(handle());
  } else {
    delete this;
  }
}

bool Win32Socket::EventSink::OnMessage(UINT uMsg,
                                       WPARAM wParam,
                                       LPARAM lParam,
                                       LRESULT& result) {
  switch (uMsg) {
    case WM_SOCKETNOTIFY:
    case WM_TIMER:
      return OnSocketNotify(uMsg, wParam, lParam, result);
    case WM_DNSNOTIFY:
      return OnDnsNotify(wParam, lParam, result);
  }
  return false;
}

bool Win32Socket::EventSink::OnSocketNotify(UINT uMsg,
                                            WPARAM wParam,
                                            LPARAM lParam,
                                            LRESULT& result) {
  result = 0;

  int wsa_event = WSAGETSELECTEVENT(lParam);
  int wsa_error = WSAGETSELECTERROR(lParam);

  // Treat connect timeouts as close notifications
  if (uMsg == WM_TIMER) {
    wsa_event = FD_CLOSE;
    wsa_error = WSAETIMEDOUT;
  }

  if (parent_)
    parent_->OnSocketNotify(static_cast<SOCKET>(wParam), wsa_event, wsa_error);
  return true;
}

bool Win32Socket::EventSink::OnDnsNotify(WPARAM wParam,
                                         LPARAM lParam,
                                         LRESULT& result) {
  result = 0;

  int error = WSAGETASYNCERROR(lParam);
  if (parent_)
    parent_->OnDnsNotify(reinterpret_cast<HANDLE>(wParam), error);
  return true;
}

void Win32Socket::EventSink::OnNcDestroy() {
  if (parent_) {
    RTC_LOG(LS_ERROR) << "EventSink hwnd is being destroyed, but the event sink"
                         " hasn't yet been disposed.";
  } else {
    delete this;
  }
}

/////////////////////////////////////////////////////////////////////////////
// Win32Socket
/////////////////////////////////////////////////////////////////////////////

Win32Socket::Win32Socket()
    : socket_(INVALID_SOCKET),
      error_(0),
      state_(CS_CLOSED),
      connect_time_(0),
      closing_(false),
      close_error_(0),
      sink_(nullptr),
      dns_(nullptr) {}

Win32Socket::~Win32Socket() {
  Close();
}

bool Win32Socket::CreateT(int family, int type) {
  Close();
  int proto = (SOCK_DGRAM == type) ? IPPROTO_UDP : IPPROTO_TCP;
  socket_ = ::WSASocket(family, type, proto, nullptr, 0, 0);
  if (socket_ == INVALID_SOCKET) {
    UpdateLastError();
    return false;
  }
  if ((SOCK_DGRAM == type) && !SetAsync(FD_READ | FD_WRITE)) {
    return false;
  }
  return true;
}

int Win32Socket::Attach(SOCKET s) {
  RTC_DCHECK(socket_ == INVALID_SOCKET);
  if (socket_ != INVALID_SOCKET)
    return SOCKET_ERROR;

  RTC_DCHECK(s != INVALID_SOCKET);
  if (s == INVALID_SOCKET)
    return SOCKET_ERROR;

  socket_ = s;
  state_ = CS_CONNECTED;

  if (!SetAsync(FD_READ | FD_WRITE | FD_CLOSE))
    return SOCKET_ERROR;

  return 0;
}

void Win32Socket::SetTimeout(int ms) {
  if (sink_)
    ::SetTimer(sink_->handle(), 1, ms, 0);
}

SocketAddress Win32Socket::GetLocalAddress() const {
  sockaddr_storage addr = {0};
  socklen_t addrlen = sizeof(addr);
  int result =
      ::getsockname(socket_, reinterpret_cast<sockaddr*>(&addr), &addrlen);
  SocketAddress address;
  if (result >= 0) {
    SocketAddressFromSockAddrStorage(addr, &address);
  } else {
    RTC_LOG(LS_WARNING) << "GetLocalAddress: unable to get local addr, socket="
                        << socket_;
  }
  return address;
}

SocketAddress Win32Socket::GetRemoteAddress() const {
  sockaddr_storage addr = {0};
  socklen_t addrlen = sizeof(addr);
  int result =
      ::getpeername(socket_, reinterpret_cast<sockaddr*>(&addr), &addrlen);
  SocketAddress address;
  if (result >= 0) {
    SocketAddressFromSockAddrStorage(addr, &address);
  } else {
    RTC_LOG(LS_WARNING)
        << "GetRemoteAddress: unable to get remote addr, socket=" << socket_;
  }
  return address;
}

int Win32Socket::Bind(const SocketAddress& addr) {
  RTC_DCHECK(socket_ != INVALID_SOCKET);
  if (socket_ == INVALID_SOCKET)
    return SOCKET_ERROR;

  sockaddr_storage saddr;
  size_t len = addr.ToSockAddrStorage(&saddr);
  int err = ::bind(socket_, reinterpret_cast<sockaddr*>(&saddr),
                   static_cast<int>(len));
  UpdateLastError();
  return err;
}

int Win32Socket::Connect(const SocketAddress& addr) {
  if (state_ != CS_CLOSED) {
    SetError(EALREADY);
    return SOCKET_ERROR;
  }

  if (!addr.IsUnresolvedIP()) {
    return DoConnect(addr);
  }

  RTC_LOG_F(LS_INFO) << "async dns lookup (" << addr.hostname() << ")";
  DnsLookup* dns = new DnsLookup;
  if (!sink_) {
    // Explicitly create the sink ourselves here; we can't rely on SetAsync
    // because we don't have a socket_ yet.
    CreateSink();
  }
  // TODO: Replace with IPv6 compatible lookup.
  dns->handle = WSAAsyncGetHostByName(sink_->handle(), WM_DNSNOTIFY,
                                      addr.hostname().c_str(), dns->buffer,
                                      sizeof(dns->buffer));

  if (!dns->handle) {
    RTC_LOG_F(LS_ERROR) << "WSAAsyncGetHostByName error: " << WSAGetLastError();
    delete dns;
    UpdateLastError();
    Close();
    return SOCKET_ERROR;
  }

  dns->port = addr.port();
  dns_ = dns;
  state_ = CS_CONNECTING;
  return 0;
}

int Win32Socket::DoConnect(const SocketAddress& addr) {
  if ((socket_ == INVALID_SOCKET) && !CreateT(addr.family(), SOCK_STREAM)) {
    return SOCKET_ERROR;
  }
  if (!SetAsync(FD_READ | FD_WRITE | FD_CONNECT | FD_CLOSE)) {
    return SOCKET_ERROR;
  }

  sockaddr_storage saddr = {0};
  size_t len = addr.ToSockAddrStorage(&saddr);
  connect_time_ = Time();
  int result = connect(socket_, reinterpret_cast<SOCKADDR*>(&saddr),
                       static_cast<int>(len));
  if (result != SOCKET_ERROR) {
    state_ = CS_CONNECTED;
  } else {
    int code = WSAGetLastError();
    if (code == WSAEWOULDBLOCK) {
      state_ = CS_CONNECTING;
    } else {
      ReportWSAError("WSAAsync:connect", code, addr);
      error_ = code;
      Close();
      return SOCKET_ERROR;
    }
  }
  addr_ = addr;

  return 0;
}

int Win32Socket::GetError() const {
  return error_;
}

void Win32Socket::SetError(int error) {
  error_ = error;
}

Socket::ConnState Win32Socket::GetState() const {
  return state_;
}

int Win32Socket::GetOption(Option opt, int* value) {
  int slevel;
  int sopt;
  if (TranslateOption(opt, &slevel, &sopt) == -1)
    return -1;

  char* p = reinterpret_cast<char*>(value);
  int optlen = sizeof(value);
  return ::getsockopt(socket_, slevel, sopt, p, &optlen);
}

int Win32Socket::SetOption(Option opt, int value) {
  int slevel;
  int sopt;
  if (TranslateOption(opt, &slevel, &sopt) == -1)
    return -1;

  const char* p = reinterpret_cast<const char*>(&value);
  return ::setsockopt(socket_, slevel, sopt, p, sizeof(value));
}

int Win32Socket::Send(const void* buffer, size_t length) {
  int sent = ::send(socket_, reinterpret_cast<const char*>(buffer),
                    static_cast<int>(length), 0);
  UpdateLastError();
  return sent;
}

int Win32Socket::SendTo(const void* buffer,
                        size_t length,
                        const SocketAddress& addr) {
  sockaddr_storage saddr;
  size_t addr_len = addr.ToSockAddrStorage(&saddr);
  int sent = ::sendto(
      socket_, reinterpret_cast<const char*>(buffer), static_cast<int>(length),
      0, reinterpret_cast<sockaddr*>(&saddr), static_cast<int>(addr_len));
  UpdateLastError();
  return sent;
}

int Win32Socket::Recv(void* buffer, size_t length, int64_t* timestamp) {
  if (timestamp) {
    *timestamp = -1;
  }
  int received =
      ::recv(socket_, static_cast<char*>(buffer), static_cast<int>(length), 0);
  UpdateLastError();
  if (closing_ && received <= static_cast<int>(length))
    PostClosed();
  return received;
}

int Win32Socket::RecvFrom(void* buffer,
                          size_t length,
                          SocketAddress* out_addr,
                          int64_t* timestamp) {
  if (timestamp) {
    *timestamp = -1;
  }
  sockaddr_storage saddr;
  socklen_t addr_len = sizeof(saddr);
  int received =
      ::recvfrom(socket_, static_cast<char*>(buffer), static_cast<int>(length),
                 0, reinterpret_cast<sockaddr*>(&saddr), &addr_len);
  UpdateLastError();
  if (received != SOCKET_ERROR)
    SocketAddressFromSockAddrStorage(saddr, out_addr);
  if (closing_ && received <= static_cast<int>(length))
    PostClosed();
  return received;
}

int Win32Socket::Listen(int backlog) {
  int err = ::listen(socket_, backlog);
  if (!SetAsync(FD_ACCEPT))
    return SOCKET_ERROR;

  UpdateLastError();
  if (err == 0)
    state_ = CS_CONNECTING;
  return err;
}

Win32Socket* Win32Socket::Accept(SocketAddress* out_addr) {
  sockaddr_storage saddr;
  socklen_t addr_len = sizeof(saddr);
  SOCKET s = ::accept(socket_, reinterpret_cast<sockaddr*>(&saddr), &addr_len);
  UpdateLastError();
  if (s == INVALID_SOCKET)
    return nullptr;
  if (out_addr)
    SocketAddressFromSockAddrStorage(saddr, out_addr);
  Win32Socket* socket = new Win32Socket;
  if (0 == socket->Attach(s))
    return socket;
  delete socket;
  return nullptr;
}

int Win32Socket::Close() {
  int err = 0;
  if (socket_ != INVALID_SOCKET) {
    err = ::closesocket(socket_);
    socket_ = INVALID_SOCKET;
    closing_ = false;
    close_error_ = 0;
    UpdateLastError();
  }
  if (dns_) {
    WSACancelAsyncRequest(dns_->handle);
    delete dns_;
    dns_ = nullptr;
  }
  if (sink_) {
    sink_->Dispose();
    sink_ = nullptr;
  }
  addr_.Clear();
  state_ = CS_CLOSED;
  return err;
}

void Win32Socket::CreateSink() {
  RTC_DCHECK(nullptr == sink_);

  // Create window
  sink_ = new EventSink(this);
  sink_->Create(nullptr, L"EventSink", 0, 0, 0, 0, 10, 10);
}

bool Win32Socket::SetAsync(int events) {
  if (nullptr == sink_) {
    CreateSink();
    RTC_DCHECK(nullptr != sink_);
  }

  // start the async select
  if (WSAAsyncSelect(socket_, sink_->handle(), WM_SOCKETNOTIFY, events) ==
      SOCKET_ERROR) {
    UpdateLastError();
    Close();
    return false;
  }

  return true;
}

bool Win32Socket::HandleClosed(int close_error) {
  // WM_CLOSE will be received before all data has been read, so we need to
  // hold on to it until the read buffer has been drained.
  char ch;
  closing_ = true;
  close_error_ = close_error;
  return (::recv(socket_, &ch, 1, MSG_PEEK) <= 0);
}

void Win32Socket::PostClosed() {
  // If we see that the buffer is indeed drained, then send the close.
  closing_ = false;
  ::PostMessage(sink_->handle(), WM_SOCKETNOTIFY, socket_,
                WSAMAKESELECTREPLY(FD_CLOSE, close_error_));
}

void Win32Socket::UpdateLastError() {
  error_ = WSAGetLastError();
}

int Win32Socket::TranslateOption(Option opt, int* slevel, int* sopt) {
  switch (opt) {
    case OPT_DONTFRAGMENT:
      *slevel = IPPROTO_IP;
      *sopt = IP_DONTFRAGMENT;
      break;
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
      RTC_LOG(LS_WARNING) << "Socket::OPT_DSCP not supported.";
      return -1;
    default:
      RTC_NOTREACHED();
      return -1;
  }
  return 0;
}

void Win32Socket::OnSocketNotify(SOCKET socket, int event, int error) {
  // Ignore events if we're already closed.
  if (socket != socket_)
    return;

  error_ = error;
  switch (event) {
    case FD_CONNECT:
      if (error != ERROR_SUCCESS) {
        ReportWSAError("WSAAsync:connect notify", error, addr_);
#if !defined(NDEBUG)
        int64_t duration = TimeSince(connect_time_);
        RTC_LOG(LS_INFO) << "WSAAsync:connect error (" << duration
                         << " ms), faking close";
#endif
        state_ = CS_CLOSED;
        // If you get an error connecting, close doesn't really do anything
        // and it certainly doesn't send back any close notification, but
        // we really only maintain a few states, so it is easiest to get
        // back into a known state by pretending that a close happened, even
        // though the connect event never did occur.
        SignalCloseEvent(this, error);
      } else {
#if !defined(NDEBUG)
        int64_t duration = TimeSince(connect_time_);
        RTC_LOG(LS_INFO) << "WSAAsync:connect (" << duration << " ms)";
#endif
        state_ = CS_CONNECTED;
        SignalConnectEvent(this);
      }
      break;

    case FD_ACCEPT:
    case FD_READ:
      if (error != ERROR_SUCCESS) {
        ReportWSAError("WSAAsync:read notify", error, addr_);
      } else {
        SignalReadEvent(this);
      }
      break;

    case FD_WRITE:
      if (error != ERROR_SUCCESS) {
        ReportWSAError("WSAAsync:write notify", error, addr_);
      } else {
        SignalWriteEvent(this);
      }
      break;

    case FD_CLOSE:
      if (HandleClosed(error)) {
        ReportWSAError("WSAAsync:close notify", error, addr_);
        state_ = CS_CLOSED;
        SignalCloseEvent(this, error);
      }
      break;
  }
}

void Win32Socket::OnDnsNotify(HANDLE task, int error) {
  if (!dns_ || dns_->handle != task)
    return;

  uint32_t ip = 0;
  if (error == 0) {
    hostent* pHost = reinterpret_cast<hostent*>(dns_->buffer);
    uint32_t net_ip = *reinterpret_cast<uint32_t*>(pHost->h_addr_list[0]);
    ip = NetworkToHost32(net_ip);
  }

  RTC_LOG_F(LS_INFO) << "(" << IPAddress(ip).ToSensitiveString() << ", "
                     << error << ")";

  if (error == 0) {
    SocketAddress address(ip, dns_->port);
    error = DoConnect(address);
  } else {
    Close();
  }

  if (error) {
    error_ = error;
    SignalCloseEvent(this, error_);
  } else {
    delete dns_;
    dns_ = nullptr;
  }
}

///////////////////////////////////////////////////////////////////////////////
// Win32SocketServer
// Provides cricket base services on top of a win32 gui thread
///////////////////////////////////////////////////////////////////////////////

static UINT s_wm_wakeup_id = 0;
const wchar_t Win32SocketServer::kWindowName[] = L"libjingle Message Window";

Win32SocketServer::Win32SocketServer()
    : wnd_(this), posted_(false), hdlg_(nullptr) {
  if (s_wm_wakeup_id == 0)
    s_wm_wakeup_id = RegisterWindowMessageW(L"WM_WAKEUP");
  if (!wnd_.Create(nullptr, kWindowName, 0, 0, 0, 0, 0, 0)) {
    RTC_LOG_GLE(LS_ERROR) << "Failed to create message window.";
  }
}

Win32SocketServer::~Win32SocketServer() {
  if (wnd_.handle() != nullptr) {
    KillTimer(wnd_.handle(), 1);
    wnd_.Destroy();
  }
}

Socket* Win32SocketServer::CreateSocket(int family, int type) {
  return CreateAsyncSocket(family, type);
}

AsyncSocket* Win32SocketServer::CreateAsyncSocket(int family, int type) {
  Win32Socket* socket = new Win32Socket;
  if (socket->CreateT(family, type)) {
    return socket;
  }
  delete socket;
  return nullptr;
}

void Win32SocketServer::SetMessageQueue(Thread* queue) {
  message_queue_ = queue;
}

bool Win32SocketServer::Wait(int cms, bool process_io) {
  BOOL b;
  if (process_io) {
    // Spin the Win32 message pump at least once, and as long as requested.
    // This is the Thread::ProcessMessages case.
    uint32_t start = Time();
    do {
      MSG msg;
      SetTimer(wnd_.handle(), 0, cms, nullptr);
      // Get the next available message. If we have a modeless dialog, give
      // give the message to IsDialogMessage, which will return true if it
      // was a message for the dialog that it handled internally.
      // Otherwise, dispatch as usual via Translate/DispatchMessage.
      b = GetMessage(&msg, nullptr, 0, 0);
      if (b == -1) {
        RTC_LOG_GLE(LS_ERROR) << "GetMessage failed.";
        return false;
      } else if (b) {
        if (!hdlg_ || !IsDialogMessage(hdlg_, &msg)) {
          TranslateMessage(&msg);
          DispatchMessage(&msg);
        }
      }
      KillTimer(wnd_.handle(), 0);
    } while (b && TimeSince(start) < cms);
  } else if (cms != 0) {
    // Sit and wait forever for a WakeUp. This is the Thread::Send case.
    RTC_DCHECK(cms == -1);
    MSG msg;
    b = GetMessage(&msg, nullptr, s_wm_wakeup_id, s_wm_wakeup_id);
    {
      webrtc::MutexLock lock(&mutex_);
      posted_ = false;
    }
  } else {
    // No-op (cms == 0 && !process_io). This is the Pump case.
    b = TRUE;
  }
  return (b != FALSE);
}

void Win32SocketServer::WakeUp() {
  if (wnd_.handle()) {
    // Set the "message pending" flag, if not already set.
    {
      webrtc::MutexLock lock(&mutex_);
      if (posted_)
        return;
      posted_ = true;
    }

    PostMessage(wnd_.handle(), s_wm_wakeup_id, 0, 0);
  }
}

void Win32SocketServer::Pump() {
  // Clear the "message pending" flag.
  {
    webrtc::MutexLock lock(&mutex_);
    posted_ = false;
  }

  // Dispatch all the messages that are currently in our queue. If new messages
  // are posted during the dispatch, they will be handled in the next Pump.
  // We use max(1, ...) to make sure we try to dispatch at least once, since
  // this allow us to process "sent" messages, not included in the size() count.
  Message msg;
  for (size_t max_messages_to_process =
           std::max<size_t>(1, message_queue_->size());
       max_messages_to_process > 0 && message_queue_->Get(&msg, 0, false);
       --max_messages_to_process) {
    message_queue_->Dispatch(&msg);
  }

  // Anything remaining?
  int delay = message_queue_->GetDelay();
  if (delay == -1) {
    KillTimer(wnd_.handle(), 1);
  } else {
    SetTimer(wnd_.handle(), 1, delay, nullptr);
  }
}

bool Win32SocketServer::MessageWindow::OnMessage(UINT wm,
                                                 WPARAM wp,
                                                 LPARAM lp,
                                                 LRESULT& lr) {
  bool handled = false;
  if (wm == s_wm_wakeup_id || (wm == WM_TIMER && wp == 1)) {
    ss_->Pump();
    lr = 0;
    handled = true;
  }
  return handled;
}

Win32Thread::Win32Thread(SocketServer* ss) : Thread(ss), id_(0) {}

Win32Thread::~Win32Thread() {
  Stop();
}

void Win32Thread::Run() {
  id_ = GetCurrentThreadId();
  Thread::Run();
  id_ = 0;
}

void Win32Thread::Quit() {
  PostThreadMessage(id_, WM_QUIT, 0, 0);
}

}  // namespace rtc

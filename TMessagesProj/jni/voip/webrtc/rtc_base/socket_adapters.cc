/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#if defined(_MSC_VER) && _MSC_VER < 1300
#pragma warning(disable : 4786)
#endif

#include <algorithm>

#include "absl/strings/match.h"
#include "rtc_base/buffer.h"
#include "rtc_base/byte_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/http_common.h"
#include "rtc_base/logging.h"
#include "rtc_base/socket_adapters.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/zero_memory.h"

namespace rtc {

BufferedReadAdapter::BufferedReadAdapter(Socket* socket, size_t size)
    : AsyncSocketAdapter(socket),
      buffer_size_(size),
      data_len_(0),
      buffering_(false) {
  buffer_ = new char[buffer_size_];
}

BufferedReadAdapter::~BufferedReadAdapter() {
  delete[] buffer_;
}

int BufferedReadAdapter::Send(const void* pv, size_t cb) {
  if (buffering_) {
    // TODO: Spoof error better; Signal Writeable
    SetError(EWOULDBLOCK);
    return -1;
  }
  return AsyncSocketAdapter::Send(pv, cb);
}

int BufferedReadAdapter::Recv(void* pv, size_t cb, int64_t* timestamp) {
  if (buffering_) {
    SetError(EWOULDBLOCK);
    return -1;
  }

  size_t read = 0;

  if (data_len_) {
    read = std::min(cb, data_len_);
    memcpy(pv, buffer_, read);
    data_len_ -= read;
    if (data_len_ > 0) {
      memmove(buffer_, buffer_ + read, data_len_);
    }
    pv = static_cast<char*>(pv) + read;
    cb -= read;
  }

  // FIX: If cb == 0, we won't generate another read event

  int res = AsyncSocketAdapter::Recv(pv, cb, timestamp);
  if (res >= 0) {
    // Read from socket and possibly buffer; return combined length
    return res + static_cast<int>(read);
  }

  if (read > 0) {
    // Failed to read from socket, but still read something from buffer
    return static_cast<int>(read);
  }

  // Didn't read anything; return error from socket
  return res;
}

void BufferedReadAdapter::BufferInput(bool on) {
  buffering_ = on;
}

void BufferedReadAdapter::OnReadEvent(Socket* socket) {
  RTC_DCHECK(socket == GetSocket());

  if (!buffering_) {
    AsyncSocketAdapter::OnReadEvent(socket);
    return;
  }

  if (data_len_ >= buffer_size_) {
    RTC_LOG(LS_ERROR) << "Input buffer overflow";
    RTC_DCHECK_NOTREACHED();
    data_len_ = 0;
  }

  int len = AsyncSocketAdapter::Recv(buffer_ + data_len_,
                                     buffer_size_ - data_len_, nullptr);
  if (len < 0) {
    // TODO: Do something better like forwarding the error to the user.
    RTC_LOG_ERR(LS_INFO) << "Recv";
    return;
  }

  data_len_ += len;

  ProcessInput(buffer_, &data_len_);
}

///////////////////////////////////////////////////////////////////////////////

// This is a SSL v2 CLIENT_HELLO message.
// TODO: Should this have a session id? The response doesn't have a
// certificate, so the hello should have a session id.
static const uint8_t kSslClientHello[] = {
    0x80, 0x46,                                            // msg len
    0x01,                                                  // CLIENT_HELLO
    0x03, 0x01,                                            // SSL 3.1
    0x00, 0x2d,                                            // ciphersuite len
    0x00, 0x00,                                            // session id len
    0x00, 0x10,                                            // challenge len
    0x01, 0x00, 0x80, 0x03, 0x00, 0x80, 0x07, 0x00, 0xc0,  // ciphersuites
    0x06, 0x00, 0x40, 0x02, 0x00, 0x80, 0x04, 0x00, 0x80,  //
    0x00, 0x00, 0x04, 0x00, 0xfe, 0xff, 0x00, 0x00, 0x0a,  //
    0x00, 0xfe, 0xfe, 0x00, 0x00, 0x09, 0x00, 0x00, 0x64,  //
    0x00, 0x00, 0x62, 0x00, 0x00, 0x03, 0x00, 0x00, 0x06,  //
    0x1f, 0x17, 0x0c, 0xa6, 0x2f, 0x00, 0x78, 0xfc,        // challenge
    0x46, 0x55, 0x2e, 0xb1, 0x83, 0x39, 0xf1, 0xea         //
};

// static
ArrayView<const uint8_t> AsyncSSLSocket::SslClientHello() {
  // Implicit conversion directly from kSslClientHello to ArrayView fails when
  // built with gcc.
  return {kSslClientHello, sizeof(kSslClientHello)};
}

// This is a TLSv1 SERVER_HELLO message.
static const uint8_t kSslServerHello[] = {
    0x16,                                            // handshake message
    0x03, 0x01,                                      // SSL 3.1
    0x00, 0x4a,                                      // message len
    0x02,                                            // SERVER_HELLO
    0x00, 0x00, 0x46,                                // handshake len
    0x03, 0x01,                                      // SSL 3.1
    0x42, 0x85, 0x45, 0xa7, 0x27, 0xa9, 0x5d, 0xa0,  // server random
    0xb3, 0xc5, 0xe7, 0x53, 0xda, 0x48, 0x2b, 0x3f,  //
    0xc6, 0x5a, 0xca, 0x89, 0xc1, 0x58, 0x52, 0xa1,  //
    0x78, 0x3c, 0x5b, 0x17, 0x46, 0x00, 0x85, 0x3f,  //
    0x20,                                            // session id len
    0x0e, 0xd3, 0x06, 0x72, 0x5b, 0x5b, 0x1b, 0x5f,  // session id
    0x15, 0xac, 0x13, 0xf9, 0x88, 0x53, 0x9d, 0x9b,  //
    0xe8, 0x3d, 0x7b, 0x0c, 0x30, 0x32, 0x6e, 0x38,  //
    0x4d, 0xa2, 0x75, 0x57, 0x41, 0x6c, 0x34, 0x5c,  //
    0x00, 0x04,                                      // RSA/RC4-128/MD5
    0x00                                             // null compression
};

// static
ArrayView<const uint8_t> AsyncSSLSocket::SslServerHello() {
  return {kSslServerHello, sizeof(kSslServerHello)};
}

AsyncSSLSocket::AsyncSSLSocket(Socket* socket)
    : BufferedReadAdapter(socket, 1024) {}

int AsyncSSLSocket::Connect(const SocketAddress& addr) {
  // Begin buffering before we connect, so that there isn't a race condition
  // between potential senders and receiving the OnConnectEvent signal
  BufferInput(true);
  return BufferedReadAdapter::Connect(addr);
}

void AsyncSSLSocket::OnConnectEvent(Socket* socket) {
  RTC_DCHECK(socket == GetSocket());
  // TODO: we could buffer output too...
  const int res = DirectSend(kSslClientHello, sizeof(kSslClientHello));
  if (res != sizeof(kSslClientHello)) {
    RTC_LOG(LS_ERROR) << "Sending fake SSL ClientHello message failed.";
    Close();
    SignalCloseEvent(this, 0);
  }
}

void AsyncSSLSocket::ProcessInput(char* data, size_t* len) {
  if (*len < sizeof(kSslServerHello))
    return;

  if (memcmp(kSslServerHello, data, sizeof(kSslServerHello)) != 0) {
    RTC_LOG(LS_ERROR) << "Received non-matching fake SSL ServerHello message.";
    Close();
    SignalCloseEvent(this, 0);  // TODO: error code?
    return;
  }

  *len -= sizeof(kSslServerHello);
  if (*len > 0) {
    memmove(data, data + sizeof(kSslServerHello), *len);
  }

  bool remainder = (*len > 0);
  BufferInput(false);
  SignalConnectEvent(this);

  // FIX: if SignalConnect causes the socket to be destroyed, we are in trouble
  if (remainder)
    SignalReadEvent(this);
}

///////////////////////////////////////////////////////////////////////////////

AsyncHttpsProxySocket::AsyncHttpsProxySocket(Socket* socket,
                                             const std::string& user_agent,
                                             const SocketAddress& proxy,
                                             const std::string& username,
                                             const CryptString& password)
    : BufferedReadAdapter(socket, 1024),
      proxy_(proxy),
      agent_(user_agent),
      user_(username),
      pass_(password),
      force_connect_(false),
      state_(PS_ERROR),
      context_(0) {}

AsyncHttpsProxySocket::~AsyncHttpsProxySocket() {
  delete context_;
}

int AsyncHttpsProxySocket::Connect(const SocketAddress& addr) {
  int ret;
  RTC_LOG(LS_VERBOSE) << "AsyncHttpsProxySocket::Connect("
                      << proxy_.ToSensitiveString() << ")";
  dest_ = addr;
  state_ = PS_INIT;
  if (ShouldIssueConnect()) {
    BufferInput(true);
  }
  ret = BufferedReadAdapter::Connect(proxy_);
  // TODO: Set state_ appropriately if Connect fails.
  return ret;
}

SocketAddress AsyncHttpsProxySocket::GetRemoteAddress() const {
  return dest_;
}

int AsyncHttpsProxySocket::Close() {
  headers_.clear();
  state_ = PS_ERROR;
  dest_.Clear();
  delete context_;
  context_ = nullptr;
  return BufferedReadAdapter::Close();
}

Socket::ConnState AsyncHttpsProxySocket::GetState() const {
  if (state_ < PS_TUNNEL) {
    return CS_CONNECTING;
  } else if (state_ == PS_TUNNEL) {
    return CS_CONNECTED;
  } else {
    return CS_CLOSED;
  }
}

void AsyncHttpsProxySocket::OnConnectEvent(Socket* socket) {
  RTC_LOG(LS_VERBOSE) << "AsyncHttpsProxySocket::OnConnectEvent";
  if (!ShouldIssueConnect()) {
    state_ = PS_TUNNEL;
    BufferedReadAdapter::OnConnectEvent(socket);
    return;
  }
  SendRequest();
}

void AsyncHttpsProxySocket::OnCloseEvent(Socket* socket, int err) {
  RTC_LOG(LS_VERBOSE) << "AsyncHttpsProxySocket::OnCloseEvent(" << err << ")";
  if ((state_ == PS_WAIT_CLOSE) && (err == 0)) {
    state_ = PS_ERROR;
    Connect(dest_);
  } else {
    BufferedReadAdapter::OnCloseEvent(socket, err);
  }
}

void AsyncHttpsProxySocket::ProcessInput(char* data, size_t* len) {
  size_t start = 0;
  for (size_t pos = start; state_ < PS_TUNNEL && pos < *len;) {
    if (state_ == PS_SKIP_BODY) {
      size_t consume = std::min(*len - pos, content_length_);
      pos += consume;
      start = pos;
      content_length_ -= consume;
      if (content_length_ == 0) {
        EndResponse();
      }
      continue;
    }

    if (data[pos++] != '\n')
      continue;

    size_t length = pos - start - 1;
    if ((length > 0) && (data[start + length - 1] == '\r'))
      --length;

    data[start + length] = 0;
    ProcessLine(data + start, length);
    start = pos;
  }

  *len -= start;
  if (*len > 0) {
    memmove(data, data + start, *len);
  }

  if (state_ != PS_TUNNEL)
    return;

  bool remainder = (*len > 0);
  BufferInput(false);
  SignalConnectEvent(this);

  // FIX: if SignalConnect causes the socket to be destroyed, we are in trouble
  if (remainder)
    SignalReadEvent(this);  // TODO: signal this??
}

bool AsyncHttpsProxySocket::ShouldIssueConnect() const {
  // TODO: Think about whether a more sophisticated test
  // than dest port == 80 is needed.
  return force_connect_ || (dest_.port() != 80);
}

void AsyncHttpsProxySocket::SendRequest() {
  rtc::StringBuilder ss;
  ss << "CONNECT " << dest_.ToString() << " HTTP/1.0\r\n";
  ss << "User-Agent: " << agent_ << "\r\n";
  ss << "Host: " << dest_.HostAsURIString() << "\r\n";
  ss << "Content-Length: 0\r\n";
  ss << "Proxy-Connection: Keep-Alive\r\n";
  ss << headers_;
  ss << "\r\n";
  std::string str = ss.str();
  DirectSend(str.c_str(), str.size());
  state_ = PS_LEADER;
  expect_close_ = true;
  content_length_ = 0;
  headers_.clear();

  RTC_LOG(LS_VERBOSE) << "AsyncHttpsProxySocket >> " << str;
}

void AsyncHttpsProxySocket::ProcessLine(char* data, size_t len) {
  RTC_LOG(LS_VERBOSE) << "AsyncHttpsProxySocket << " << data;

  if (len == 0) {
    if (state_ == PS_TUNNEL_HEADERS) {
      state_ = PS_TUNNEL;
    } else if (state_ == PS_ERROR_HEADERS) {
      Error(defer_error_);
      return;
    } else if (state_ == PS_SKIP_HEADERS) {
      if (content_length_) {
        state_ = PS_SKIP_BODY;
      } else {
        EndResponse();
        return;
      }
    } else {
      if (!unknown_mechanisms_.empty()) {
        RTC_LOG(LS_ERROR) << "Unsupported authentication methods: "
                          << unknown_mechanisms_;
      }
      // Unexpected end of headers
      Error(0);
      return;
    }
  } else if (state_ == PS_LEADER) {
    unsigned int code;
    if (sscanf(data, "HTTP/%*u.%*u %u", &code) != 1) {
      Error(0);
      return;
    }
    switch (code) {
      case 200:
        // connection good!
        state_ = PS_TUNNEL_HEADERS;
        return;
#if defined(HTTP_STATUS_PROXY_AUTH_REQ) && (HTTP_STATUS_PROXY_AUTH_REQ != 407)
#error Wrong code for HTTP_STATUS_PROXY_AUTH_REQ
#endif
      case 407:  // HTTP_STATUS_PROXY_AUTH_REQ
        state_ = PS_AUTHENTICATE;
        return;
      default:
        defer_error_ = 0;
        state_ = PS_ERROR_HEADERS;
        return;
    }
  } else if ((state_ == PS_AUTHENTICATE) &&
             absl::StartsWithIgnoreCase(data, "Proxy-Authenticate:")) {
    std::string response, auth_method;
    switch (HttpAuthenticate(data + 19, len - 19, proxy_, "CONNECT", "/", user_,
                             pass_, context_, response, auth_method)) {
      case HAR_IGNORE:
        RTC_LOG(LS_VERBOSE) << "Ignoring Proxy-Authenticate: " << auth_method;
        if (!unknown_mechanisms_.empty())
          unknown_mechanisms_.append(", ");
        unknown_mechanisms_.append(auth_method);
        break;
      case HAR_RESPONSE:
        headers_ = "Proxy-Authorization: ";
        headers_.append(response);
        headers_.append("\r\n");
        state_ = PS_SKIP_HEADERS;
        unknown_mechanisms_.clear();
        break;
      case HAR_CREDENTIALS:
        defer_error_ = SOCKET_EACCES;
        state_ = PS_ERROR_HEADERS;
        unknown_mechanisms_.clear();
        break;
      case HAR_ERROR:
        defer_error_ = 0;
        state_ = PS_ERROR_HEADERS;
        unknown_mechanisms_.clear();
        break;
    }
  } else if (absl::StartsWithIgnoreCase(data, "Content-Length:")) {
    content_length_ = strtoul(data + 15, 0, 0);
  } else if (absl::StartsWithIgnoreCase(data, "Proxy-Connection: Keep-Alive")) {
    expect_close_ = false;
    /*
  } else if (absl::StartsWithIgnoreCase(data, "Connection: close") {
    expect_close_ = true;
    */
  }
}

void AsyncHttpsProxySocket::EndResponse() {
  if (!expect_close_) {
    SendRequest();
    return;
  }

  // No point in waiting for the server to close... let's close now
  // TODO: Refactor out PS_WAIT_CLOSE
  state_ = PS_WAIT_CLOSE;
  BufferedReadAdapter::Close();
  OnCloseEvent(this, 0);
}

void AsyncHttpsProxySocket::Error(int error) {
  BufferInput(false);
  Close();
  SetError(error);
  SignalCloseEvent(this, error);
}

///////////////////////////////////////////////////////////////////////////////

AsyncSocksProxySocket::AsyncSocksProxySocket(Socket* socket,
                                             const SocketAddress& proxy,
                                             const std::string& username,
                                             const CryptString& password)
    : BufferedReadAdapter(socket, 1024),
      state_(SS_ERROR),
      proxy_(proxy),
      user_(username),
      pass_(password) {}

AsyncSocksProxySocket::~AsyncSocksProxySocket() = default;

int AsyncSocksProxySocket::Connect(const SocketAddress& addr) {
  int ret;
  dest_ = addr;
  state_ = SS_INIT;
  BufferInput(true);
  ret = BufferedReadAdapter::Connect(proxy_);
  // TODO: Set state_ appropriately if Connect fails.
  return ret;
}

SocketAddress AsyncSocksProxySocket::GetRemoteAddress() const {
  return dest_;
}

int AsyncSocksProxySocket::Close() {
  state_ = SS_ERROR;
  dest_.Clear();
  return BufferedReadAdapter::Close();
}

Socket::ConnState AsyncSocksProxySocket::GetState() const {
  if (state_ < SS_TUNNEL) {
    return CS_CONNECTING;
  } else if (state_ == SS_TUNNEL) {
    return CS_CONNECTED;
  } else {
    return CS_CLOSED;
  }
}

void AsyncSocksProxySocket::OnConnectEvent(Socket* socket) {
  SendHello();
}

void AsyncSocksProxySocket::ProcessInput(char* data, size_t* len) {
  RTC_DCHECK(state_ < SS_TUNNEL);

  ByteBufferReader response(data, *len);

  if (state_ == SS_HELLO) {
    uint8_t ver, method;
    if (!response.ReadUInt8(&ver) || !response.ReadUInt8(&method))
      return;

    if (ver != 5) {
      Error(0);
      return;
    }

    if (method == 0) {
      SendConnect();
    } else if (method == 2) {
      SendAuth();
    } else {
      Error(0);
      return;
    }
  } else if (state_ == SS_AUTH) {
    uint8_t ver, status;
    if (!response.ReadUInt8(&ver) || !response.ReadUInt8(&status))
      return;

    if ((ver != 1) || (status != 0)) {
      Error(SOCKET_EACCES);
      return;
    }

    SendConnect();
  } else if (state_ == SS_CONNECT) {
    uint8_t ver, rep, rsv, atyp;
    if (!response.ReadUInt8(&ver) || !response.ReadUInt8(&rep) ||
        !response.ReadUInt8(&rsv) || !response.ReadUInt8(&atyp))
      return;

    if ((ver != 5) || (rep != 0)) {
      Error(0);
      return;
    }

    uint16_t port;
    if (atyp == 1) {
      uint32_t addr;
      if (!response.ReadUInt32(&addr) || !response.ReadUInt16(&port))
        return;
      RTC_LOG(LS_VERBOSE) << "Bound on " << addr << ":" << port;
    } else if (atyp == 3) {
      uint8_t length;
      std::string addr;
      if (!response.ReadUInt8(&length) || !response.ReadString(&addr, length) ||
          !response.ReadUInt16(&port))
        return;
      RTC_LOG(LS_VERBOSE) << "Bound on " << addr << ":" << port;
    } else if (atyp == 4) {
      std::string addr;
      if (!response.ReadString(&addr, 16) || !response.ReadUInt16(&port))
        return;
      RTC_LOG(LS_VERBOSE) << "Bound on <IPV6>:" << port;
    } else {
      Error(0);
      return;
    }

    state_ = SS_TUNNEL;
  }

  // Consume parsed data
  *len = response.Length();
  memmove(data, response.Data(), *len);

  if (state_ != SS_TUNNEL)
    return;

  bool remainder = (*len > 0);
  BufferInput(false);
  SignalConnectEvent(this);

  // FIX: if SignalConnect causes the socket to be destroyed, we are in trouble
  if (remainder)
    SignalReadEvent(this);  // TODO: signal this??
}

void AsyncSocksProxySocket::SendHello() {
  ByteBufferWriter request;
  request.WriteUInt8(5);  // Socks Version
  if (user_.empty()) {
    request.WriteUInt8(1);  // Authentication Mechanisms
    request.WriteUInt8(0);  // No authentication
  } else {
    request.WriteUInt8(2);  // Authentication Mechanisms
    request.WriteUInt8(0);  // No authentication
    request.WriteUInt8(2);  // Username/Password
  }
  DirectSend(request.Data(), request.Length());
  state_ = SS_HELLO;
}

void AsyncSocksProxySocket::SendAuth() {
  ByteBufferWriterT<ZeroOnFreeBuffer<char>> request;
  request.WriteUInt8(1);  // Negotiation Version
  request.WriteUInt8(static_cast<uint8_t>(user_.size()));
  request.WriteString(user_);  // Username
  request.WriteUInt8(static_cast<uint8_t>(pass_.GetLength()));
  size_t len = pass_.GetLength() + 1;
  char* sensitive = new char[len];
  pass_.CopyTo(sensitive, true);
  request.WriteBytes(sensitive, pass_.GetLength());  // Password
  ExplicitZeroMemory(sensitive, len);
  delete[] sensitive;
  DirectSend(request.Data(), request.Length());
  state_ = SS_AUTH;
}

void AsyncSocksProxySocket::SendConnect() {
  ByteBufferWriter request;
  request.WriteUInt8(5);  // Socks Version
  request.WriteUInt8(1);  // CONNECT
  request.WriteUInt8(0);  // Reserved
  if (dest_.IsUnresolvedIP()) {
    std::string hostname = dest_.hostname();
    request.WriteUInt8(3);  // DOMAINNAME
    request.WriteUInt8(static_cast<uint8_t>(hostname.size()));
    request.WriteString(hostname);  // Destination Hostname
  } else {
    request.WriteUInt8(1);            // IPV4
    request.WriteUInt32(dest_.ip());  // Destination IP
  }
  request.WriteUInt16(dest_.port());  // Destination Port
  DirectSend(request.Data(), request.Length());
  state_ = SS_CONNECT;
}

void AsyncSocksProxySocket::Error(int error) {
  state_ = SS_ERROR;
  BufferInput(false);
  Close();
  SetError(SOCKET_EACCES);
  SignalCloseEvent(this, error);
}

}  // namespace rtc

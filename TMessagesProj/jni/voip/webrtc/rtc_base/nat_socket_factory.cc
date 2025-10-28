/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/nat_socket_factory.h"

#include "api/units/timestamp.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/nat_server.h"
#include "rtc_base/virtual_socket_server.h"

namespace rtc {

// Packs the given socketaddress into the buffer in buf, in the quasi-STUN
// format that the natserver uses.
// Returns 0 if an invalid address is passed.
size_t PackAddressForNAT(char* buf,
                         size_t buf_size,
                         const SocketAddress& remote_addr) {
  const IPAddress& ip = remote_addr.ipaddr();
  int family = ip.family();
  buf[0] = 0;
  buf[1] = family;
  // Writes the port.
  *(reinterpret_cast<uint16_t*>(&buf[2])) = HostToNetwork16(remote_addr.port());
  if (family == AF_INET) {
    RTC_DCHECK(buf_size >= kNATEncodedIPv4AddressSize);
    in_addr v4addr = ip.ipv4_address();
    memcpy(&buf[4], &v4addr, kNATEncodedIPv4AddressSize - 4);
    return kNATEncodedIPv4AddressSize;
  } else if (family == AF_INET6) {
    RTC_DCHECK(buf_size >= kNATEncodedIPv6AddressSize);
    in6_addr v6addr = ip.ipv6_address();
    memcpy(&buf[4], &v6addr, kNATEncodedIPv6AddressSize - 4);
    return kNATEncodedIPv6AddressSize;
  }
  return 0U;
}

// Decodes the remote address from a packet that has been encoded with the nat's
// quasi-STUN format. Returns the length of the address (i.e., the offset into
// data where the original packet starts).
size_t UnpackAddressFromNAT(rtc::ArrayView<const uint8_t> buf,
                            SocketAddress* remote_addr) {
  RTC_CHECK(buf.size() >= 8);
  RTC_DCHECK(buf.data()[0] == 0);
  int family = buf[1];
  uint16_t port =
      NetworkToHost16(*(reinterpret_cast<const uint16_t*>(&buf.data()[2])));
  if (family == AF_INET) {
    const in_addr* v4addr = reinterpret_cast<const in_addr*>(&buf.data()[4]);
    *remote_addr = SocketAddress(IPAddress(*v4addr), port);
    return kNATEncodedIPv4AddressSize;
  } else if (family == AF_INET6) {
    RTC_DCHECK(buf.size() >= 20);
    const in6_addr* v6addr = reinterpret_cast<const in6_addr*>(&buf.data()[4]);
    *remote_addr = SocketAddress(IPAddress(*v6addr), port);
    return kNATEncodedIPv6AddressSize;
  }
  return 0U;
}

// NATSocket
class NATSocket : public Socket, public sigslot::has_slots<> {
 public:
  explicit NATSocket(NATInternalSocketFactory* sf, int family, int type)
      : sf_(sf),
        family_(family),
        type_(type),
        connected_(false),
        socket_(nullptr) {}

  ~NATSocket() override { delete socket_; }

  SocketAddress GetLocalAddress() const override {
    return (socket_) ? socket_->GetLocalAddress() : SocketAddress();
  }

  SocketAddress GetRemoteAddress() const override {
    return remote_addr_;  // will be NIL if not connected
  }

  int Bind(const SocketAddress& addr) override {
    if (socket_) {  // already bound, bubble up error
      return -1;
    }

    return BindInternal(addr);
  }

  int Connect(const SocketAddress& addr) override {
    int result = 0;
    // If we're not already bound (meaning `socket_` is null), bind to ANY
    // address.
    if (!socket_) {
      result = BindInternal(SocketAddress(GetAnyIP(family_), 0));
      if (result < 0) {
        return result;
      }
    }

    if (type_ == SOCK_STREAM) {
      result = socket_->Connect(server_addr_.IsNil() ? addr : server_addr_);
    } else {
      connected_ = true;
    }

    if (result >= 0) {
      remote_addr_ = addr;
    }

    return result;
  }

  int Send(const void* data, size_t size) override {
    RTC_DCHECK(connected_);
    return SendTo(data, size, remote_addr_);
  }

  int SendTo(const void* data,
             size_t size,
             const SocketAddress& addr) override {
    RTC_DCHECK(!connected_ || addr == remote_addr_);
    if (server_addr_.IsNil() || type_ == SOCK_STREAM) {
      return socket_->SendTo(data, size, addr);
    }
    // This array will be too large for IPv4 packets, but only by 12 bytes.
    std::unique_ptr<char[]> buf(new char[size + kNATEncodedIPv6AddressSize]);
    size_t addrlength =
        PackAddressForNAT(buf.get(), size + kNATEncodedIPv6AddressSize, addr);
    size_t encoded_size = size + addrlength;
    memcpy(buf.get() + addrlength, data, size);
    int result = socket_->SendTo(buf.get(), encoded_size, server_addr_);
    if (result >= 0) {
      RTC_DCHECK(result == static_cast<int>(encoded_size));
      result = result - static_cast<int>(addrlength);
    }
    return result;
  }

  int Recv(void* data, size_t size, int64_t* timestamp) override {
    SocketAddress addr;
    return RecvFrom(data, size, &addr, timestamp);
  }

  int RecvFrom(void* data,
               size_t size,
               SocketAddress* out_addr,
               int64_t* timestamp) override {
    if (server_addr_.IsNil() || type_ == SOCK_STREAM) {
      return socket_->RecvFrom(data, size, out_addr, timestamp);
    }
    // Make sure we have enough room to read the requested amount plus the
    // largest possible header address.
    buf_.EnsureCapacity(size + kNATEncodedIPv6AddressSize);

    // Read the packet from the socket.
    Socket::ReceiveBuffer receive_buffer(buf_);
    int result = socket_->RecvFrom(receive_buffer);
    if (result >= 0) {
      RTC_DCHECK(receive_buffer.source_address == server_addr_);
      *timestamp =
          receive_buffer.arrival_time.value_or(webrtc::Timestamp::Micros(0))
              .us();

      // Decode the wire packet into the actual results.
      SocketAddress real_remote_addr;
      size_t addrlength = UnpackAddressFromNAT(buf_, &real_remote_addr);
      memcpy(data, buf_.data() + addrlength, result - addrlength);

      // Make sure this packet should be delivered before returning it.
      if (!connected_ || (real_remote_addr == remote_addr_)) {
        if (out_addr)
          *out_addr = real_remote_addr;
        result = result - static_cast<int>(addrlength);
      } else {
        RTC_LOG(LS_ERROR) << "Dropping packet from unknown remote address: "
                          << real_remote_addr.ToString();
        result = 0;  // Tell the caller we didn't read anything
      }
    }

    return result;
  }

  int Close() override {
    int result = 0;
    if (socket_) {
      result = socket_->Close();
      if (result >= 0) {
        connected_ = false;
        remote_addr_ = SocketAddress();
        delete socket_;
        socket_ = nullptr;
      }
    }
    return result;
  }

  int Listen(int backlog) override { return socket_->Listen(backlog); }
  Socket* Accept(SocketAddress* paddr) override {
    return socket_->Accept(paddr);
  }
  int GetError() const override {
    return socket_ ? socket_->GetError() : error_;
  }
  void SetError(int error) override {
    if (socket_) {
      socket_->SetError(error);
    } else {
      error_ = error;
    }
  }
  ConnState GetState() const override {
    return connected_ ? CS_CONNECTED : CS_CLOSED;
  }
  int GetOption(Option opt, int* value) override {
    return socket_ ? socket_->GetOption(opt, value) : -1;
  }
  int SetOption(Option opt, int value) override {
    return socket_ ? socket_->SetOption(opt, value) : -1;
  }

  void OnConnectEvent(Socket* socket) {
    // If we're NATed, we need to send a message with the real addr to use.
    RTC_DCHECK(socket == socket_);
    if (server_addr_.IsNil()) {
      connected_ = true;
      SignalConnectEvent(this);
    } else {
      SendConnectRequest();
    }
  }
  void OnReadEvent(Socket* socket) {
    // If we're NATed, we need to process the connect reply.
    RTC_DCHECK(socket == socket_);
    if (type_ == SOCK_STREAM && !server_addr_.IsNil() && !connected_) {
      HandleConnectReply();
    } else {
      SignalReadEvent(this);
    }
  }
  void OnWriteEvent(Socket* socket) {
    RTC_DCHECK(socket == socket_);
    SignalWriteEvent(this);
  }
  void OnCloseEvent(Socket* socket, int error) {
    RTC_DCHECK(socket == socket_);
    SignalCloseEvent(this, error);
  }

 private:
  int BindInternal(const SocketAddress& addr) {
    RTC_DCHECK(!socket_);

    int result;
    socket_ = sf_->CreateInternalSocket(family_, type_, addr, &server_addr_);
    result = (socket_) ? socket_->Bind(addr) : -1;
    if (result >= 0) {
      socket_->SignalConnectEvent.connect(this, &NATSocket::OnConnectEvent);
      socket_->SignalReadEvent.connect(this, &NATSocket::OnReadEvent);
      socket_->SignalWriteEvent.connect(this, &NATSocket::OnWriteEvent);
      socket_->SignalCloseEvent.connect(this, &NATSocket::OnCloseEvent);
    } else {
      server_addr_.Clear();
      delete socket_;
      socket_ = nullptr;
    }

    return result;
  }

  // Sends the destination address to the server to tell it to connect.
  void SendConnectRequest() {
    char buf[kNATEncodedIPv6AddressSize];
    size_t length = PackAddressForNAT(buf, arraysize(buf), remote_addr_);
    socket_->Send(buf, length);
  }

  // Handles the byte sent back from the server and fires the appropriate event.
  void HandleConnectReply() {
    char code;
    socket_->Recv(&code, sizeof(code), nullptr);
    if (code == 0) {
      connected_ = true;
      SignalConnectEvent(this);
    } else {
      Close();
      SignalCloseEvent(this, code);
    }
  }

  NATInternalSocketFactory* sf_;
  int family_;
  int type_;
  bool connected_;
  SocketAddress remote_addr_;
  SocketAddress server_addr_;  // address of the NAT server
  Socket* socket_;
  // Need to hold error in case it occurs before the socket is created.
  int error_ = 0;
  Buffer buf_;
};

// NATSocketFactory
NATSocketFactory::NATSocketFactory(SocketFactory* factory,
                                   const SocketAddress& nat_udp_addr,
                                   const SocketAddress& nat_tcp_addr)
    : factory_(factory),
      nat_udp_addr_(nat_udp_addr),
      nat_tcp_addr_(nat_tcp_addr) {}

Socket* NATSocketFactory::CreateSocket(int family, int type) {
  return new NATSocket(this, family, type);
}

Socket* NATSocketFactory::CreateInternalSocket(int family,
                                               int type,
                                               const SocketAddress& local_addr,
                                               SocketAddress* nat_addr) {
  if (type == SOCK_STREAM) {
    *nat_addr = nat_tcp_addr_;
  } else {
    *nat_addr = nat_udp_addr_;
  }
  return factory_->CreateSocket(family, type);
}

// NATSocketServer
NATSocketServer::NATSocketServer(SocketServer* server)
    : server_(server), msg_queue_(nullptr) {}

NATSocketServer::Translator* NATSocketServer::GetTranslator(
    const SocketAddress& ext_ip) {
  return nats_.Get(ext_ip);
}

NATSocketServer::Translator* NATSocketServer::AddTranslator(
    const SocketAddress& ext_ip,
    const SocketAddress& int_ip,
    NATType type) {
  // Fail if a translator already exists with this extternal address.
  if (nats_.Get(ext_ip))
    return nullptr;

  return nats_.Add(
      ext_ip, new Translator(this, type, int_ip, *msg_queue_, server_, ext_ip));
}

void NATSocketServer::RemoveTranslator(const SocketAddress& ext_ip) {
  nats_.Remove(ext_ip);
}

Socket* NATSocketServer::CreateSocket(int family, int type) {
  return new NATSocket(this, family, type);
}

void NATSocketServer::SetMessageQueue(Thread* queue) {
  msg_queue_ = queue;
  server_->SetMessageQueue(queue);
}

bool NATSocketServer::Wait(webrtc::TimeDelta max_wait_duration,
                           bool process_io) {
  return server_->Wait(max_wait_duration, process_io);
}

void NATSocketServer::WakeUp() {
  server_->WakeUp();
}

Socket* NATSocketServer::CreateInternalSocket(int family,
                                              int type,
                                              const SocketAddress& local_addr,
                                              SocketAddress* nat_addr) {
  Socket* socket = nullptr;
  Translator* nat = nats_.FindClient(local_addr);
  if (nat) {
    socket = nat->internal_factory()->CreateSocket(family, type);
    *nat_addr = (type == SOCK_STREAM) ? nat->internal_tcp_address()
                                      : nat->internal_udp_address();
  } else {
    socket = server_->CreateSocket(family, type);
  }
  return socket;
}

// NATSocketServer::Translator
NATSocketServer::Translator::Translator(NATSocketServer* server,
                                        NATType type,
                                        const SocketAddress& int_ip,
                                        Thread& external_socket_thread,
                                        SocketFactory* ext_factory,
                                        const SocketAddress& ext_ip)
    : server_(server) {
  // Create a new private network, and a NATServer running on the private
  // network that bridges to the external network. Also tell the private
  // network to use the same message queue as us.
  internal_server_ = std::make_unique<VirtualSocketServer>();
  internal_server_->SetMessageQueue(server_->queue());
  nat_server_ = std::make_unique<NATServer>(
      type, *server->queue(), internal_server_.get(), int_ip, int_ip,
      external_socket_thread, ext_factory, ext_ip);
}

NATSocketServer::Translator::~Translator() {
  internal_server_->SetMessageQueue(nullptr);
}

NATSocketServer::Translator* NATSocketServer::Translator::GetTranslator(
    const SocketAddress& ext_ip) {
  return nats_.Get(ext_ip);
}

NATSocketServer::Translator* NATSocketServer::Translator::AddTranslator(
    const SocketAddress& ext_ip,
    const SocketAddress& int_ip,
    NATType type) {
  // Fail if a translator already exists with this extternal address.
  if (nats_.Get(ext_ip))
    return nullptr;

  AddClient(ext_ip);
  return nats_.Add(ext_ip, new Translator(server_, type, int_ip,
                                          *server_->queue(), server_, ext_ip));
}
void NATSocketServer::Translator::RemoveTranslator(
    const SocketAddress& ext_ip) {
  nats_.Remove(ext_ip);
  RemoveClient(ext_ip);
}

bool NATSocketServer::Translator::AddClient(const SocketAddress& int_ip) {
  // Fail if a client already exists with this internal address.
  if (clients_.find(int_ip) != clients_.end())
    return false;

  clients_.insert(int_ip);
  return true;
}

void NATSocketServer::Translator::RemoveClient(const SocketAddress& int_ip) {
  std::set<SocketAddress>::iterator it = clients_.find(int_ip);
  if (it != clients_.end()) {
    clients_.erase(it);
  }
}

NATSocketServer::Translator* NATSocketServer::Translator::FindClient(
    const SocketAddress& int_ip) {
  // See if we have the requested IP, or any of our children do.
  return (clients_.find(int_ip) != clients_.end()) ? this
                                                   : nats_.FindClient(int_ip);
}

// NATSocketServer::TranslatorMap
NATSocketServer::TranslatorMap::~TranslatorMap() {
  for (TranslatorMap::iterator it = begin(); it != end(); ++it) {
    delete it->second;
  }
}

NATSocketServer::Translator* NATSocketServer::TranslatorMap::Get(
    const SocketAddress& ext_ip) {
  TranslatorMap::iterator it = find(ext_ip);
  return (it != end()) ? it->second : nullptr;
}

NATSocketServer::Translator* NATSocketServer::TranslatorMap::Add(
    const SocketAddress& ext_ip,
    Translator* nat) {
  (*this)[ext_ip] = nat;
  return nat;
}

void NATSocketServer::TranslatorMap::Remove(const SocketAddress& ext_ip) {
  TranslatorMap::iterator it = find(ext_ip);
  if (it != end()) {
    delete it->second;
    erase(it);
  }
}

NATSocketServer::Translator* NATSocketServer::TranslatorMap::FindClient(
    const SocketAddress& int_ip) {
  Translator* nat = nullptr;
  for (TranslatorMap::iterator it = begin(); it != end() && !nat; ++it) {
    nat = it->second->FindClient(int_ip);
  }
  return nat;
}

}  // namespace rtc

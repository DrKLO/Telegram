/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_FAKE_DTLS_TRANSPORT_H_
#define P2P_BASE_FAKE_DTLS_TRANSPORT_H_

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "api/crypto/crypto_options.h"
#include "p2p/base/dtls_transport_internal.h"
#include "p2p/base/fake_ice_transport.h"
#include "rtc_base/fake_ssl_identity.h"
#include "rtc_base/rtc_certificate.h"

namespace cricket {

// Fake DTLS transport which is implemented by wrapping a fake ICE transport.
// Doesn't interact directly with fake ICE transport for anything other than
// sending packets.
class FakeDtlsTransport : public DtlsTransportInternal {
 public:
  explicit FakeDtlsTransport(FakeIceTransport* ice_transport)
      : ice_transport_(ice_transport),
        transport_name_(ice_transport->transport_name()),
        component_(ice_transport->component()),
        dtls_fingerprint_("", nullptr) {
    RTC_DCHECK(ice_transport_);
    ice_transport_->SignalReadPacket.connect(
        this, &FakeDtlsTransport::OnIceTransportReadPacket);
    ice_transport_->SignalNetworkRouteChanged.connect(
        this, &FakeDtlsTransport::OnNetworkRouteChanged);
  }

  explicit FakeDtlsTransport(std::unique_ptr<FakeIceTransport> ice)
      : owned_ice_transport_(std::move(ice)),
        transport_name_(owned_ice_transport_->transport_name()),
        component_(owned_ice_transport_->component()),
        dtls_fingerprint_("", rtc::ArrayView<const uint8_t>()) {
    ice_transport_ = owned_ice_transport_.get();
    ice_transport_->SignalReadPacket.connect(
        this, &FakeDtlsTransport::OnIceTransportReadPacket);
    ice_transport_->SignalNetworkRouteChanged.connect(
        this, &FakeDtlsTransport::OnNetworkRouteChanged);
  }

  // If this constructor is called, a new fake ICE transport will be created,
  // and this FakeDtlsTransport will take the ownership.
  FakeDtlsTransport(const std::string& name, int component)
      : FakeDtlsTransport(std::make_unique<FakeIceTransport>(name, component)) {
  }
  FakeDtlsTransport(const std::string& name,
                    int component,
                    rtc::Thread* network_thread)
      : FakeDtlsTransport(std::make_unique<FakeIceTransport>(name,
                                                             component,
                                                             network_thread)) {}

  ~FakeDtlsTransport() override {
    if (dest_ && dest_->dest_ == this) {
      dest_->dest_ = nullptr;
    }
  }

  // Get inner fake ICE transport.
  FakeIceTransport* fake_ice_transport() { return ice_transport_; }

  // If async, will send packets by "Post"-ing to message queue instead of
  // synchronously "Send"-ing.
  void SetAsync(bool async) { ice_transport_->SetAsync(async); }
  void SetAsyncDelay(int delay_ms) { ice_transport_->SetAsyncDelay(delay_ms); }

  // SetWritable, SetReceiving and SetDestination are the main methods that can
  // be used for testing, to simulate connectivity or lack thereof.
  void SetWritable(bool writable) {
    ice_transport_->SetWritable(writable);
    set_writable(writable);
  }
  void SetReceiving(bool receiving) {
    ice_transport_->SetReceiving(receiving);
    set_receiving(receiving);
  }
  void SetDtlsState(DtlsTransportState state) {
    dtls_state_ = state;
    SendDtlsState(this, dtls_state_);
  }

  // Simulates the two DTLS transports connecting to each other.
  // If |asymmetric| is true this method only affects this FakeDtlsTransport.
  // If false, it affects |dest| as well.
  void SetDestination(FakeDtlsTransport* dest, bool asymmetric = false) {
    if (dest == dest_) {
      return;
    }
    RTC_DCHECK(!dest || !dest_)
        << "Changing fake destination from one to another is not supported.";
    if (dest && !dest_) {
      // This simulates the DTLS handshake.
      dest_ = dest;
      if (local_cert_ && dest_->local_cert_) {
        do_dtls_ = true;
        RTC_LOG(LS_INFO) << "FakeDtlsTransport is doing DTLS";
      } else {
        do_dtls_ = false;
        RTC_LOG(LS_INFO) << "FakeDtlsTransport is not doing DTLS";
      }
      SetWritable(true);
      if (!asymmetric) {
        dest->SetDestination(this, true);
      }
      // If the |dtls_role_| is unset, set it to SSL_CLIENT by default.
      if (!dtls_role_) {
        dtls_role_ = std::move(rtc::SSL_CLIENT);
      }
      SetDtlsState(DTLS_TRANSPORT_CONNECTED);
      ice_transport_->SetDestination(
          static_cast<FakeIceTransport*>(dest->ice_transport()), asymmetric);
    } else {
      // Simulates loss of connectivity, by asymmetrically forgetting dest_.
      dest_ = nullptr;
      SetWritable(false);
      ice_transport_->SetDestination(nullptr, asymmetric);
    }
  }

  // Fake DtlsTransportInternal implementation.
  DtlsTransportState dtls_state() const override { return dtls_state_; }
  const std::string& transport_name() const override { return transport_name_; }
  int component() const override { return component_; }
  const rtc::SSLFingerprint& dtls_fingerprint() const {
    return dtls_fingerprint_;
  }
  bool SetRemoteFingerprint(const std::string& alg,
                            const uint8_t* digest,
                            size_t digest_len) override {
    dtls_fingerprint_ =
        rtc::SSLFingerprint(alg, rtc::MakeArrayView(digest, digest_len));
    return true;
  }
  bool SetDtlsRole(rtc::SSLRole role) override {
    dtls_role_ = std::move(role);
    return true;
  }
  bool GetDtlsRole(rtc::SSLRole* role) const override {
    if (!dtls_role_) {
      return false;
    }
    *role = *dtls_role_;
    return true;
  }
  bool SetLocalCertificate(
      const rtc::scoped_refptr<rtc::RTCCertificate>& certificate) override {
    do_dtls_ = true;
    local_cert_ = certificate;
    return true;
  }
  void SetRemoteSSLCertificate(rtc::FakeSSLCertificate* cert) {
    remote_cert_ = cert;
  }
  bool IsDtlsActive() const override { return do_dtls_; }
  bool GetSslVersionBytes(int* version) const override {
    if (!do_dtls_) {
      return false;
    }
    *version = 0x0102;
    return true;
  }
  bool GetSrtpCryptoSuite(int* crypto_suite) override {
    if (!do_dtls_) {
      return false;
    }
    *crypto_suite = crypto_suite_;
    return true;
  }
  void SetSrtpCryptoSuite(int crypto_suite) { crypto_suite_ = crypto_suite; }

  bool GetSslCipherSuite(int* cipher_suite) override {
    if (ssl_cipher_suite_) {
      *cipher_suite = *ssl_cipher_suite_;
      return true;
    }
    return false;
  }
  void SetSslCipherSuite(absl::optional<int> cipher_suite) {
    ssl_cipher_suite_ = cipher_suite;
  }
  rtc::scoped_refptr<rtc::RTCCertificate> GetLocalCertificate() const override {
    return local_cert_;
  }
  std::unique_ptr<rtc::SSLCertChain> GetRemoteSSLCertChain() const override {
    if (!remote_cert_) {
      return nullptr;
    }
    return std::make_unique<rtc::SSLCertChain>(remote_cert_->Clone());
  }
  bool ExportKeyingMaterial(const std::string& label,
                            const uint8_t* context,
                            size_t context_len,
                            bool use_context,
                            uint8_t* result,
                            size_t result_len) override {
    if (!do_dtls_) {
      return false;
    }
    memset(result, 0xff, result_len);
    return true;
  }
  void set_ssl_max_protocol_version(rtc::SSLProtocolVersion version) {
    ssl_max_version_ = version;
  }
  rtc::SSLProtocolVersion ssl_max_protocol_version() const {
    return ssl_max_version_;
  }

  IceTransportInternal* ice_transport() override { return ice_transport_; }

  // PacketTransportInternal implementation, which passes through to fake ICE
  // transport for sending actual packets.
  bool writable() const override { return writable_; }
  bool receiving() const override { return receiving_; }
  int SendPacket(const char* data,
                 size_t len,
                 const rtc::PacketOptions& options,
                 int flags) override {
    // We expect only SRTP packets to be sent through this interface.
    if (flags != PF_SRTP_BYPASS && flags != 0) {
      return -1;
    }
    return ice_transport_->SendPacket(data, len, options, flags);
  }
  int SetOption(rtc::Socket::Option opt, int value) override {
    return ice_transport_->SetOption(opt, value);
  }
  bool GetOption(rtc::Socket::Option opt, int* value) override {
    return ice_transport_->GetOption(opt, value);
  }
  int GetError() override { return ice_transport_->GetError(); }

  absl::optional<rtc::NetworkRoute> network_route() const override {
    return ice_transport_->network_route();
  }

 private:
  void OnIceTransportReadPacket(PacketTransportInternal* ice_,
                                const char* data,
                                size_t len,
                                const int64_t& packet_time_us,
                                int flags) {
    SignalReadPacket(this, data, len, packet_time_us, flags);
  }

  void set_receiving(bool receiving) {
    if (receiving_ == receiving) {
      return;
    }
    receiving_ = receiving;
    SignalReceivingState(this);
  }

  void set_writable(bool writable) {
    if (writable_ == writable) {
      return;
    }
    writable_ = writable;
    if (writable_) {
      SignalReadyToSend(this);
    }
    SignalWritableState(this);
  }

  void OnNetworkRouteChanged(absl::optional<rtc::NetworkRoute> network_route) {
    SignalNetworkRouteChanged(network_route);
  }

  FakeIceTransport* ice_transport_;
  std::unique_ptr<FakeIceTransport> owned_ice_transport_;
  std::string transport_name_;
  int component_;
  FakeDtlsTransport* dest_ = nullptr;
  rtc::scoped_refptr<rtc::RTCCertificate> local_cert_;
  rtc::FakeSSLCertificate* remote_cert_ = nullptr;
  bool do_dtls_ = false;
  rtc::SSLProtocolVersion ssl_max_version_ = rtc::SSL_PROTOCOL_DTLS_12;
  rtc::SSLFingerprint dtls_fingerprint_;
  absl::optional<rtc::SSLRole> dtls_role_;
  int crypto_suite_ = rtc::SRTP_AES128_CM_SHA1_80;
  absl::optional<int> ssl_cipher_suite_;

  DtlsTransportState dtls_state_ = DTLS_TRANSPORT_NEW;

  bool receiving_ = false;
  bool writable_ = false;
};

}  // namespace cricket

#endif  // P2P_BASE_FAKE_DTLS_TRANSPORT_H_

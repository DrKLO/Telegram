/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_DTLS_TRANSPORT_INTERNAL_H_
#define P2P_BASE_DTLS_TRANSPORT_INTERNAL_H_

#include <stddef.h>
#include <stdint.h>

#include <memory>
#include <string>
#include <utility>

#include "absl/base/attributes.h"
#include "absl/strings/string_view.h"
#include "api/crypto/crypto_options.h"
#include "api/dtls_transport_interface.h"
#include "api/scoped_refptr.h"
#include "p2p/base/ice_transport_internal.h"
#include "p2p/base/packet_transport_internal.h"
#include "rtc_base/callback_list.h"
#include "rtc_base/ssl_certificate.h"
#include "rtc_base/ssl_fingerprint.h"
#include "rtc_base/ssl_stream_adapter.h"

namespace cricket {

enum PacketFlags {
  PF_NORMAL = 0x00,       // A normal packet.
  PF_SRTP_BYPASS = 0x01,  // An encrypted SRTP packet; bypass any additional
                          // crypto provided by the transport (e.g. DTLS)
};

// DtlsTransportInternal is an internal interface that does DTLS, also
// negotiating SRTP crypto suites so that it may be used for DTLS-SRTP.
//
// Once the public interface is supported,
// (https://www.w3.org/TR/webrtc/#rtcdtlstransport-interface)
// the DtlsTransportInterface will be split from this class.
class DtlsTransportInternal : public rtc::PacketTransportInternal {
 public:
  ~DtlsTransportInternal() override;

  DtlsTransportInternal(const DtlsTransportInternal&) = delete;
  DtlsTransportInternal& operator=(const DtlsTransportInternal&) = delete;

  virtual webrtc::DtlsTransportState dtls_state() const = 0;

  virtual int component() const = 0;

  virtual bool IsDtlsActive() const = 0;

  virtual bool GetDtlsRole(rtc::SSLRole* role) const = 0;

  virtual bool SetDtlsRole(rtc::SSLRole role) = 0;

  // Finds out which TLS/DTLS version is running.
  virtual bool GetSslVersionBytes(int* version) const = 0;
  // Finds out which DTLS-SRTP cipher was negotiated.
  // TODO(zhihuang): Remove this once all dependencies implement this.
  virtual bool GetSrtpCryptoSuite(int* cipher) = 0;

  // Finds out which DTLS cipher was negotiated.
  // TODO(zhihuang): Remove this once all dependencies implement this.
  virtual bool GetSslCipherSuite(int* cipher) = 0;

  // Gets the local RTCCertificate used for DTLS.
  virtual rtc::scoped_refptr<rtc::RTCCertificate> GetLocalCertificate()
      const = 0;

  virtual bool SetLocalCertificate(
      const rtc::scoped_refptr<rtc::RTCCertificate>& certificate) = 0;

  // Gets a copy of the remote side's SSL certificate chain.
  virtual std::unique_ptr<rtc::SSLCertChain> GetRemoteSSLCertChain() const = 0;

  // Allows key material to be extracted for external encryption.
  virtual bool ExportKeyingMaterial(absl::string_view label,
                                    const uint8_t* context,
                                    size_t context_len,
                                    bool use_context,
                                    uint8_t* result,
                                    size_t result_len) = 0;

  // Set DTLS remote fingerprint. Must be after local identity set.
  ABSL_DEPRECATED("Use SetRemoteParameters instead.")
  virtual bool SetRemoteFingerprint(absl::string_view digest_alg,
                                    const uint8_t* digest,
                                    size_t digest_len) = 0;

  // Set DTLS remote fingerprint and role. Must be after local identity set.
  virtual webrtc::RTCError SetRemoteParameters(
      absl::string_view digest_alg,
      const uint8_t* digest,
      size_t digest_len,
      absl::optional<rtc::SSLRole> role) = 0;

  ABSL_DEPRECATED("Set the max version via construction.")
  bool SetSslMaxProtocolVersion(rtc::SSLProtocolVersion version) {
    return true;
  }

  // Expose the underneath IceTransport.
  virtual IceTransportInternal* ice_transport() = 0;

  // F: void(DtlsTransportInternal*, const webrtc::DtlsTransportState)
  template <typename F>
  void SubscribeDtlsTransportState(F&& callback) {
    dtls_transport_state_callback_list_.AddReceiver(std::forward<F>(callback));
  }

  template <typename F>
  void SubscribeDtlsTransportState(const void* id, F&& callback) {
    dtls_transport_state_callback_list_.AddReceiver(id,
                                                    std::forward<F>(callback));
  }
  // Unsubscribe the subscription with given id.
  void UnsubscribeDtlsTransportState(const void* id) {
    dtls_transport_state_callback_list_.RemoveReceivers(id);
  }

  void SendDtlsState(DtlsTransportInternal* transport,
                     webrtc::DtlsTransportState state) {
    dtls_transport_state_callback_list_.Send(transport, state);
  }

  // Emitted whenever the Dtls handshake failed on some transport channel.
  // F: void(rtc::SSLHandshakeError)
  template <typename F>
  void SubscribeDtlsHandshakeError(F&& callback) {
    dtls_handshake_error_callback_list_.AddReceiver(std::forward<F>(callback));
  }

  void SendDtlsHandshakeError(rtc::SSLHandshakeError error) {
    dtls_handshake_error_callback_list_.Send(error);
  }

 protected:
  DtlsTransportInternal();

 private:
  webrtc::CallbackList<const rtc::SSLHandshakeError>
      dtls_handshake_error_callback_list_;
  webrtc::CallbackList<DtlsTransportInternal*, const webrtc::DtlsTransportState>
      dtls_transport_state_callback_list_;
};

}  // namespace cricket

#endif  // P2P_BASE_DTLS_TRANSPORT_INTERNAL_H_

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

#include "api/crypto/crypto_options.h"
#include "api/dtls_transport_interface.h"
#include "api/scoped_refptr.h"
#include "p2p/base/ice_transport_internal.h"
#include "p2p/base/packet_transport_internal.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/ssl_certificate.h"
#include "rtc_base/ssl_fingerprint.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/third_party/sigslot/sigslot.h"

namespace cricket {

enum DtlsTransportState {
  // Haven't started negotiating.
  DTLS_TRANSPORT_NEW = 0,
  // Have started negotiating.
  DTLS_TRANSPORT_CONNECTING,
  // Negotiated, and has a secure connection.
  DTLS_TRANSPORT_CONNECTED,
  // Transport is closed.
  DTLS_TRANSPORT_CLOSED,
  // Failed due to some error in the handshake process.
  DTLS_TRANSPORT_FAILED,
};

webrtc::DtlsTransportState ConvertDtlsTransportState(
    cricket::DtlsTransportState cricket_state);

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

  virtual const webrtc::CryptoOptions& crypto_options() const = 0;

  virtual DtlsTransportState dtls_state() const = 0;

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
  virtual bool ExportKeyingMaterial(const std::string& label,
                                    const uint8_t* context,
                                    size_t context_len,
                                    bool use_context,
                                    uint8_t* result,
                                    size_t result_len) = 0;

  // Set DTLS remote fingerprint. Must be after local identity set.
  virtual bool SetRemoteFingerprint(const std::string& digest_alg,
                                    const uint8_t* digest,
                                    size_t digest_len) = 0;

  virtual bool SetSslMaxProtocolVersion(rtc::SSLProtocolVersion version) = 0;

  // Expose the underneath IceTransport.
  virtual IceTransportInternal* ice_transport() = 0;

  sigslot::signal2<DtlsTransportInternal*, DtlsTransportState> SignalDtlsState;

  // Emitted whenever the Dtls handshake failed on some transport channel.
  sigslot::signal1<rtc::SSLHandshakeError> SignalDtlsHandshakeError;

 protected:
  DtlsTransportInternal();

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(DtlsTransportInternal);
};

}  // namespace cricket

#endif  // P2P_BASE_DTLS_TRANSPORT_INTERNAL_H_

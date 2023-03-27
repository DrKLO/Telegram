/*
 *  Copyright 2011 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_DTLS_TRANSPORT_H_
#define P2P_BASE_DTLS_TRANSPORT_H_

#include <memory>
#include <string>
#include <vector>

#include "absl/strings/string_view.h"
#include "api/crypto/crypto_options.h"
#include "api/dtls_transport_interface.h"
#include "api/sequence_checker.h"
#include "p2p/base/dtls_transport_internal.h"
#include "p2p/base/ice_transport_internal.h"
#include "rtc_base/buffer.h"
#include "rtc_base/buffer_queue.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/stream.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/system/no_unique_address.h"

namespace rtc {
class PacketTransportInternal;
}

namespace cricket {

// A bridge between a packet-oriented/transport-type interface on
// the bottom and a StreamInterface on the top.
class StreamInterfaceChannel : public rtc::StreamInterface {
 public:
  explicit StreamInterfaceChannel(IceTransportInternal* ice_transport);

  StreamInterfaceChannel(const StreamInterfaceChannel&) = delete;
  StreamInterfaceChannel& operator=(const StreamInterfaceChannel&) = delete;

  // Push in a packet; this gets pulled out from Read().
  bool OnPacketReceived(const char* data, size_t size);

  // Implementations of StreamInterface
  rtc::StreamState GetState() const override;
  void Close() override;
  rtc::StreamResult Read(void* buffer,
                         size_t buffer_len,
                         size_t* read,
                         int* error) override;
  rtc::StreamResult Write(const void* data,
                          size_t data_len,
                          size_t* written,
                          int* error) override;

 private:
  RTC_NO_UNIQUE_ADDRESS webrtc::SequenceChecker sequence_checker_;
  IceTransportInternal* const ice_transport_;  // owned by DtlsTransport
  rtc::StreamState state_ RTC_GUARDED_BY(sequence_checker_);
  rtc::BufferQueue packets_ RTC_GUARDED_BY(sequence_checker_);
};

// This class provides a DTLS SSLStreamAdapter inside a TransportChannel-style
// packet-based interface, wrapping an existing TransportChannel instance
// (e.g a P2PTransportChannel)
// Here's the way this works:
//
//   DtlsTransport {
//       SSLStreamAdapter* dtls_ {
//           StreamInterfaceChannel downward_ {
//               IceTransportInternal* ice_transport_;
//           }
//       }
//   }
//
//   - Data which comes into DtlsTransport from the underlying
//     ice_transport_ via OnReadPacket() is checked for whether it is DTLS
//     or not, and if it is, is passed to DtlsTransport::HandleDtlsPacket,
//     which pushes it into to downward_. dtls_ is listening for events on
//     downward_, so it immediately calls downward_->Read().
//
//   - Data written to DtlsTransport is passed either to downward_ or directly
//     to ice_transport_, depending on whether DTLS is negotiated and whether
//     the flags include PF_SRTP_BYPASS
//
//   - The SSLStreamAdapter writes to downward_->Write() which translates it
//     into packet writes on ice_transport_.
//
// This class is not thread safe; all methods must be called on the same thread
// as the constructor.
class DtlsTransport : public DtlsTransportInternal {
 public:
  // `ice_transport` is the ICE transport this DTLS transport is wrapping.  It
  // must outlive this DTLS transport.
  //
  // `crypto_options` are the options used for the DTLS handshake. This affects
  // whether GCM crypto suites are negotiated.
  //
  // `event_log` is an optional RtcEventLog for logging state changes. It should
  // outlive the DtlsTransport.
  DtlsTransport(
      IceTransportInternal* ice_transport,
      const webrtc::CryptoOptions& crypto_options,
      webrtc::RtcEventLog* event_log,
      rtc::SSLProtocolVersion max_version = rtc::SSL_PROTOCOL_DTLS_12);

  ~DtlsTransport() override;

  DtlsTransport(const DtlsTransport&) = delete;
  DtlsTransport& operator=(const DtlsTransport&) = delete;

  webrtc::DtlsTransportState dtls_state() const override;
  const std::string& transport_name() const override;
  int component() const override;

  // DTLS is active if a local certificate was set. Otherwise this acts in a
  // "passthrough" mode, sending packets directly through the underlying ICE
  // transport.
  // TODO(deadbeef): Remove this weirdness, and handle it in the upper layers.
  bool IsDtlsActive() const override;

  // SetLocalCertificate is what makes DTLS active. It must be called before
  // SetRemoteFinterprint.
  // TODO(deadbeef): Once DtlsTransport no longer has the concept of being
  // "active" or not (acting as a passthrough if not active), just require this
  // certificate on construction or "Start".
  bool SetLocalCertificate(
      const rtc::scoped_refptr<rtc::RTCCertificate>& certificate) override;
  rtc::scoped_refptr<rtc::RTCCertificate> GetLocalCertificate() const override;

  // SetRemoteFingerprint must be called after SetLocalCertificate, and any
  // other methods like SetDtlsRole. It's what triggers the actual DTLS setup.
  // TODO(deadbeef): Rename to "Start" like in ORTC?
  bool SetRemoteFingerprint(absl::string_view digest_alg,
                            const uint8_t* digest,
                            size_t digest_len) override;

  // SetRemoteParameters must be called after SetLocalCertificate.
  webrtc::RTCError SetRemoteParameters(
      absl::string_view digest_alg,
      const uint8_t* digest,
      size_t digest_len,
      absl::optional<rtc::SSLRole> role) override;

  // Called to send a packet (via DTLS, if turned on).
  int SendPacket(const char* data,
                 size_t size,
                 const rtc::PacketOptions& options,
                 int flags) override;

  bool GetOption(rtc::Socket::Option opt, int* value) override;

  // Find out which TLS version was negotiated
  bool GetSslVersionBytes(int* version) const override;
  // Find out which DTLS-SRTP cipher was negotiated
  bool GetSrtpCryptoSuite(int* cipher) override;

  bool GetDtlsRole(rtc::SSLRole* role) const override;
  bool SetDtlsRole(rtc::SSLRole role) override;

  // Find out which DTLS cipher was negotiated
  bool GetSslCipherSuite(int* cipher) override;

  // Once DTLS has been established, this method retrieves the certificate
  // chain in use by the remote peer, for use in external identity
  // verification.
  std::unique_ptr<rtc::SSLCertChain> GetRemoteSSLCertChain() const override;

  // Once DTLS has established (i.e., this ice_transport is writable), this
  // method extracts the keys negotiated during the DTLS handshake, for use in
  // external encryption. DTLS-SRTP uses this to extract the needed SRTP keys.
  // See the SSLStreamAdapter documentation for info on the specific parameters.
  bool ExportKeyingMaterial(absl::string_view label,
                            const uint8_t* context,
                            size_t context_len,
                            bool use_context,
                            uint8_t* result,
                            size_t result_len) override;

  IceTransportInternal* ice_transport() override;

  // For informational purposes. Tells if the DTLS handshake has finished.
  // This may be true even if writable() is false, if the remote fingerprint
  // has not yet been verified.
  bool IsDtlsConnected();

  bool receiving() const override;
  bool writable() const override;

  int GetError() override;

  absl::optional<rtc::NetworkRoute> network_route() const override;

  int SetOption(rtc::Socket::Option opt, int value) override;

  std::string ToString() const {
    const absl::string_view RECEIVING_ABBREV[2] = {"_", "R"};
    const absl::string_view WRITABLE_ABBREV[2] = {"_", "W"};
    rtc::StringBuilder sb;
    sb << "DtlsTransport[" << transport_name() << "|" << component_ << "|"
       << RECEIVING_ABBREV[receiving()] << WRITABLE_ABBREV[writable()] << "]";
    return sb.Release();
  }

 private:
  void ConnectToIceTransport();

  void OnWritableState(rtc::PacketTransportInternal* transport);
  void OnReadPacket(rtc::PacketTransportInternal* transport,
                    const char* data,
                    size_t size,
                    const int64_t& packet_time_us,
                    int flags);
  void OnSentPacket(rtc::PacketTransportInternal* transport,
                    const rtc::SentPacket& sent_packet);
  void OnReadyToSend(rtc::PacketTransportInternal* transport);
  void OnReceivingState(rtc::PacketTransportInternal* transport);
  void OnDtlsEvent(rtc::StreamInterface* stream_, int sig, int err);
  void OnNetworkRouteChanged(absl::optional<rtc::NetworkRoute> network_route);
  bool SetupDtls();
  void MaybeStartDtls();
  bool HandleDtlsPacket(const char* data, size_t size);
  void OnDtlsHandshakeError(rtc::SSLHandshakeError error);
  void ConfigureHandshakeTimeout();

  void set_receiving(bool receiving);
  void set_writable(bool writable);
  // Sets the DTLS state, signaling if necessary.
  void set_dtls_state(webrtc::DtlsTransportState state);

  webrtc::SequenceChecker thread_checker_;

  const int component_;
  webrtc::DtlsTransportState dtls_state_ = webrtc::DtlsTransportState::kNew;
  // Underlying ice_transport, not owned by this class.
  IceTransportInternal* const ice_transport_;
  std::unique_ptr<rtc::SSLStreamAdapter> dtls_;  // The DTLS stream
  StreamInterfaceChannel*
      downward_;  // Wrapper for ice_transport_, owned by dtls_.
  const std::vector<int> srtp_ciphers_;  // SRTP ciphers to use with DTLS.
  bool dtls_active_ = false;
  rtc::scoped_refptr<rtc::RTCCertificate> local_certificate_;
  absl::optional<rtc::SSLRole> dtls_role_;
  const rtc::SSLProtocolVersion ssl_max_version_;
  rtc::Buffer remote_fingerprint_value_;
  std::string remote_fingerprint_algorithm_;

  // Cached DTLS ClientHello packet that was received before we started the
  // DTLS handshake. This could happen if the hello was received before the
  // ice transport became writable, or before a remote fingerprint was received.
  rtc::Buffer cached_client_hello_;

  bool receiving_ = false;
  bool writable_ = false;

  webrtc::RtcEventLog* const event_log_;
};

}  // namespace cricket

#endif  // P2P_BASE_DTLS_TRANSPORT_H_

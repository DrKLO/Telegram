/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_SCTP_TRANSPORT_INTERFACE_H_
#define API_SCTP_TRANSPORT_INTERFACE_H_

#include "absl/types/optional.h"
#include "api/dtls_transport_interface.h"
#include "api/ref_count.h"
#include "api/rtc_error.h"
#include "api/scoped_refptr.h"

namespace webrtc {

// States of a SCTP transport, corresponding to the JS API specification.
// http://w3c.github.io/webrtc-pc/#dom-rtcsctptransportstate
enum class SctpTransportState {
  kNew,         // Has not started negotiating yet. Non-standard state.
  kConnecting,  // In the process of negotiating an association.
  kConnected,   // Completed negotiation of an association.
  kClosed,      // Closed by local or remote party.
  kNumValues
};

// This object gives snapshot information about the changeable state of a
// SctpTransport.
// It reflects the readonly attributes of the object in the specification.
// http://w3c.github.io/webrtc-pc/#rtcsctptransport-interface
class RTC_EXPORT SctpTransportInformation {
 public:
  SctpTransportInformation() = default;
  SctpTransportInformation(const SctpTransportInformation&) = default;
  explicit SctpTransportInformation(SctpTransportState state);
  SctpTransportInformation(
      SctpTransportState state,
      rtc::scoped_refptr<DtlsTransportInterface> dtls_transport,
      absl::optional<double> max_message_size,
      absl::optional<int> max_channels);
  ~SctpTransportInformation();
  // The DTLS transport that supports this SCTP transport.
  rtc::scoped_refptr<DtlsTransportInterface> dtls_transport() const {
    return dtls_transport_;
  }
  SctpTransportState state() const { return state_; }
  absl::optional<double> MaxMessageSize() const { return max_message_size_; }
  absl::optional<int> MaxChannels() const { return max_channels_; }

 private:
  SctpTransportState state_;
  rtc::scoped_refptr<DtlsTransportInterface> dtls_transport_;
  absl::optional<double> max_message_size_;
  absl::optional<int> max_channels_;
};

class SctpTransportObserverInterface {
 public:
  // This callback carries information about the state of the transport.
  // The argument is a pass-by-value snapshot of the state.
  // The callback will be called on the network thread.
  virtual void OnStateChange(SctpTransportInformation info) = 0;

 protected:
  virtual ~SctpTransportObserverInterface() = default;
};

// A SCTP transport, as represented to the outside world.
// This object is created on the network thread, and can only be
// accessed on that thread, except for functions explicitly marked otherwise.
// References can be held by other threads, and destruction can therefore
// be initiated by other threads.
class SctpTransportInterface : public webrtc::RefCountInterface {
 public:
  // This function can be called from other threads.
  virtual rtc::scoped_refptr<DtlsTransportInterface> dtls_transport() const = 0;
  // Returns information on the state of the SctpTransport.
  // This function can be called from other threads.
  virtual SctpTransportInformation Information() const = 0;
  // Observer management.
  virtual void RegisterObserver(SctpTransportObserverInterface* observer) = 0;
  virtual void UnregisterObserver() = 0;
};

}  // namespace webrtc

#endif  // API_SCTP_TRANSPORT_INTERFACE_H_

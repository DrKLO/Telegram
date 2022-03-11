/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_SCTP_TRANSPORT_H_
#define PC_SCTP_TRANSPORT_H_

#include <memory>

#include "api/dtls_transport_interface.h"
#include "api/scoped_refptr.h"
#include "api/sctp_transport_interface.h"
#include "api/sequence_checker.h"
#include "media/sctp/sctp_transport_internal.h"
#include "p2p/base/dtls_transport_internal.h"
#include "pc/dtls_transport.h"
#include "rtc_base/checks.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/thread.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

// This implementation wraps a cricket::SctpTransport, and takes
// ownership of it.
// This object must be constructed and updated on the networking thread,
// the same thread as the one the cricket::SctpTransportInternal object
// lives on.
class SctpTransport : public SctpTransportInterface,
                      public sigslot::has_slots<> {
 public:
  explicit SctpTransport(
      std::unique_ptr<cricket::SctpTransportInternal> internal);

  rtc::scoped_refptr<DtlsTransportInterface> dtls_transport() const override;
  SctpTransportInformation Information() const override;
  void RegisterObserver(SctpTransportObserverInterface* observer) override;
  void UnregisterObserver() override;

  // Internal functions
  void Clear();
  void SetDtlsTransport(rtc::scoped_refptr<DtlsTransport>);
  // Initialize the cricket::SctpTransport. This can be called from
  // the signaling thread.
  void Start(int local_port, int remote_port, int max_message_size);

  // TODO(https://bugs.webrtc.org/10629): Move functions that need
  // internal() to be functions on the webrtc::SctpTransport interface,
  // and make the internal() function private.
  cricket::SctpTransportInternal* internal() {
    RTC_DCHECK_RUN_ON(owner_thread_);
    return internal_sctp_transport_.get();
  }

  const cricket::SctpTransportInternal* internal() const {
    RTC_DCHECK_RUN_ON(owner_thread_);
    return internal_sctp_transport_.get();
  }

 protected:
  ~SctpTransport() override;

 private:
  void UpdateInformation(SctpTransportState state);
  void OnInternalReadyToSendData();
  void OnAssociationChangeCommunicationUp();
  void OnInternalClosingProcedureStartedRemotely(int sid);
  void OnInternalClosingProcedureComplete(int sid);
  void OnDtlsStateChange(cricket::DtlsTransportInternal* transport,
                         DtlsTransportState state);

  // NOTE: `owner_thread_` is the thread that the SctpTransport object is
  // constructed on. In the context of PeerConnection, it's the network thread.
  rtc::Thread* const owner_thread_;
  SctpTransportInformation info_ RTC_GUARDED_BY(owner_thread_);
  std::unique_ptr<cricket::SctpTransportInternal> internal_sctp_transport_
      RTC_GUARDED_BY(owner_thread_);
  SctpTransportObserverInterface* observer_ RTC_GUARDED_BY(owner_thread_) =
      nullptr;
  rtc::scoped_refptr<DtlsTransport> dtls_transport_
      RTC_GUARDED_BY(owner_thread_);
};

}  // namespace webrtc
#endif  // PC_SCTP_TRANSPORT_H_

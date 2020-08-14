/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_DTLS_TRANSPORT_STATE_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_DTLS_TRANSPORT_STATE_H_

#include <memory>

#include "api/dtls_transport_interface.h"
#include "api/rtc_event_log/rtc_event.h"

namespace webrtc {

class RtcEventDtlsTransportState : public RtcEvent {
 public:
  explicit RtcEventDtlsTransportState(DtlsTransportState state);
  ~RtcEventDtlsTransportState() override;

  Type GetType() const override;
  bool IsConfigEvent() const override;

  std::unique_ptr<RtcEventDtlsTransportState> Copy() const;

  DtlsTransportState dtls_transport_state() const {
    return dtls_transport_state_;
  }

 private:
  RtcEventDtlsTransportState(const RtcEventDtlsTransportState& other);

  const DtlsTransportState dtls_transport_state_;
};

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_DTLS_TRANSPORT_STATE_H_

/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_RTCP_PACKET_INCOMING_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_RTCP_PACKET_INCOMING_H_

#include <stdint.h>

#include <memory>

#include "api/array_view.h"
#include "api/rtc_event_log/rtc_event.h"
#include "rtc_base/buffer.h"

namespace webrtc {

class RtcEventRtcpPacketIncoming final : public RtcEvent {
 public:
  explicit RtcEventRtcpPacketIncoming(rtc::ArrayView<const uint8_t> packet);
  ~RtcEventRtcpPacketIncoming() override;

  Type GetType() const override;

  bool IsConfigEvent() const override;

  std::unique_ptr<RtcEventRtcpPacketIncoming> Copy() const;

  const rtc::Buffer& packet() const { return packet_; }

 private:
  RtcEventRtcpPacketIncoming(const RtcEventRtcpPacketIncoming& other);

  rtc::Buffer packet_;
};

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_RTCP_PACKET_INCOMING_H_

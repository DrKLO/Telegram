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
#include <string>
#include <vector>

#include "absl/strings/string_view.h"
#include "api/array_view.h"
#include "api/rtc_event_log/rtc_event.h"
#include "logging/rtc_event_log/events/logged_rtp_rtcp.h"
#include "logging/rtc_event_log/events/rtc_event_field_encoding_parser.h"
#include "rtc_base/buffer.h"

namespace webrtc {

class RtcEventRtcpPacketIncoming final : public RtcEvent {
 public:
  static constexpr Type kType = Type::RtcpPacketIncoming;

  explicit RtcEventRtcpPacketIncoming(rtc::ArrayView<const uint8_t> packet);
  ~RtcEventRtcpPacketIncoming() override;

  Type GetType() const override { return kType; }
  bool IsConfigEvent() const override { return false; }

  std::unique_ptr<RtcEventRtcpPacketIncoming> Copy() const;

  const rtc::Buffer& packet() const { return packet_; }

  static std::string Encode(rtc::ArrayView<const RtcEvent*> batch) {
    // TODO(terelius): Implement
    return "";
  }

  static RtcEventLogParseStatus Parse(
      absl::string_view encoded_bytes,
      bool batched,
      std::vector<LoggedRtcpPacketIncoming>& output) {
    // TODO(terelius): Implement
    return RtcEventLogParseStatus::Error("Not Implemented", __FILE__, __LINE__);
  }

 private:
  RtcEventRtcpPacketIncoming(const RtcEventRtcpPacketIncoming& other);

  rtc::Buffer packet_;
};

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_RTCP_PACKET_INCOMING_H_

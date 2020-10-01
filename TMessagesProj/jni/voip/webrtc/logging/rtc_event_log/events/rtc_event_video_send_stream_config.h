/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_VIDEO_SEND_STREAM_CONFIG_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_VIDEO_SEND_STREAM_CONFIG_H_

#include <memory>

#include "api/rtc_event_log/rtc_event.h"
#include "logging/rtc_event_log/rtc_stream_config.h"

namespace webrtc {

class RtcEventVideoSendStreamConfig final : public RtcEvent {
 public:
  explicit RtcEventVideoSendStreamConfig(
      std::unique_ptr<rtclog::StreamConfig> config);
  ~RtcEventVideoSendStreamConfig() override;

  Type GetType() const override;

  bool IsConfigEvent() const override;

  std::unique_ptr<RtcEventVideoSendStreamConfig> Copy() const;

  const rtclog::StreamConfig& config() const { return *config_; }

 private:
  RtcEventVideoSendStreamConfig(const RtcEventVideoSendStreamConfig& other);

  const std::unique_ptr<const rtclog::StreamConfig> config_;
};

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_VIDEO_SEND_STREAM_CONFIG_H_

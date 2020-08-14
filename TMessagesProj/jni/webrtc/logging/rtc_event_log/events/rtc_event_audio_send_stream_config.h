/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_AUDIO_SEND_STREAM_CONFIG_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_AUDIO_SEND_STREAM_CONFIG_H_

#include <memory>

#include "api/rtc_event_log/rtc_event.h"

namespace webrtc {

namespace rtclog {
struct StreamConfig;
}  // namespace rtclog

class RtcEventAudioSendStreamConfig final : public RtcEvent {
 public:
  explicit RtcEventAudioSendStreamConfig(
      std::unique_ptr<rtclog::StreamConfig> config);
  ~RtcEventAudioSendStreamConfig() override;

  Type GetType() const override;

  bool IsConfigEvent() const override;

  std::unique_ptr<RtcEventAudioSendStreamConfig> Copy() const;

  const rtclog::StreamConfig& config() const { return *config_; }

 private:
  RtcEventAudioSendStreamConfig(const RtcEventAudioSendStreamConfig& other);

  const std::unique_ptr<const rtclog::StreamConfig> config_;
};

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_AUDIO_SEND_STREAM_CONFIG_H_

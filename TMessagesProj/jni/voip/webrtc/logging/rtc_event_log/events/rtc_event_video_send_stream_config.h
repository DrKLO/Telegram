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
#include <string>
#include <vector>

#include "absl/strings/string_view.h"
#include "api/rtc_event_log/rtc_event.h"
#include "api/units/timestamp.h"
#include "logging/rtc_event_log/events/rtc_event_field_encoding_parser.h"
#include "logging/rtc_event_log/rtc_stream_config.h"

namespace webrtc {

struct LoggedVideoSendConfig {
  LoggedVideoSendConfig() = default;
  LoggedVideoSendConfig(Timestamp timestamp, const rtclog::StreamConfig config)
      : timestamp(timestamp), config(config) {}

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }
  Timestamp log_time() const { return timestamp; }

  Timestamp timestamp = Timestamp::MinusInfinity();
  rtclog::StreamConfig config;
};

class RtcEventVideoSendStreamConfig final : public RtcEvent {
 public:
  static constexpr Type kType = Type::VideoSendStreamConfig;

  explicit RtcEventVideoSendStreamConfig(
      std::unique_ptr<rtclog::StreamConfig> config);
  ~RtcEventVideoSendStreamConfig() override;

  Type GetType() const override { return kType; }
  bool IsConfigEvent() const override { return true; }

  std::unique_ptr<RtcEventVideoSendStreamConfig> Copy() const;

  const rtclog::StreamConfig& config() const { return *config_; }

  static std::string Encode(rtc::ArrayView<const RtcEvent*> batch) {
    // TODO(terelius): Implement
    return "";
  }

  static RtcEventLogParseStatus Parse(
      absl::string_view encoded_bytes,
      bool batched,
      std::vector<LoggedVideoSendConfig>& output) {
    // TODO(terelius): Implement
    return RtcEventLogParseStatus::Error("Not Implemented", __FILE__, __LINE__);
  }

 private:
  RtcEventVideoSendStreamConfig(const RtcEventVideoSendStreamConfig& other);

  const std::unique_ptr<const rtclog::StreamConfig> config_;
};

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_VIDEO_SEND_STREAM_CONFIG_H_

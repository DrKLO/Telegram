/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_end_log.h"

#include "absl/strings/string_view.h"

namespace webrtc {
constexpr RtcEvent::Type RtcEventEndLog::kType;
constexpr EventParameters RtcEventEndLog::event_params_;

RtcEventEndLog::RtcEventEndLog(Timestamp timestamp)
    : RtcEvent(timestamp.us()) {}

RtcEventEndLog::RtcEventEndLog(const RtcEventEndLog& other)
    : RtcEvent(other.timestamp_us_) {}

RtcEventEndLog::~RtcEventEndLog() = default;

std::string RtcEventEndLog::Encode(rtc::ArrayView<const RtcEvent*> batch) {
  EventEncoder encoder(event_params_, batch);
  return encoder.AsString();
}

RtcEventLogParseStatus RtcEventEndLog::Parse(
    absl::string_view encoded_bytes,
    bool batched,
    std::vector<LoggedStopEvent>& output) {
  EventParser parser;
  auto status = parser.Initialize(encoded_bytes, batched);
  if (!status.ok())
    return status;

  rtc::ArrayView<LoggedStopEvent> output_batch =
      ExtendLoggedBatch(output, parser.NumEventsInBatch());

  constexpr FieldParameters timestamp_params{
      "timestamp_ms", FieldParameters::kTimestampField, FieldType::kVarInt, 64};
  RtcEventLogParseStatusOr<rtc::ArrayView<uint64_t>> result =
      parser.ParseNumericField(timestamp_params);
  if (!result.ok())
    return result.status();
  status = PopulateRtcEventTimestamp(result.value(),
                                     &LoggedStopEvent::timestamp, output_batch);
  if (!status.ok())
    return status;

  return RtcEventLogParseStatus::Success();
}

}  // namespace webrtc

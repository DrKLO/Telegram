/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_FIELD_ENCODING_PARSER_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_FIELD_ENCODING_PARSER_H_

#include <string>
#include <vector>

#include "logging/rtc_event_log/events/rtc_event_field_encoding.h"

// TODO(terelius): Compared to a generic 'Status' class, this
// class allows us additional information about the context
// in which the error occurred. This is currently limited to
// the source location (file and line), but we plan on adding
// information about the event and field name being parsed.
// If/when we start using absl::Status in WebRTC, consider
// whether payloads would be an appropriate alternative.
class RtcEventLogParseStatus {
  template <typename T>
  friend class RtcEventLogParseStatusOr;

 public:
  static RtcEventLogParseStatus Success() { return RtcEventLogParseStatus(); }
  static RtcEventLogParseStatus Error(std::string error,
                                      std::string file,
                                      int line) {
    return RtcEventLogParseStatus(error, file, line);
  }

  bool ok() const { return error_.empty(); }

  std::string message() const { return error_; }

 private:
  RtcEventLogParseStatus() : error_() {}
  RtcEventLogParseStatus(std::string error, std::string file, int line)
      : error_(error + " (" + file + ": " + std::to_string(line) + ")") {}

  std::string error_;
};

template <typename T>
class RtcEventLogParseStatusOr {
 public:
  explicit RtcEventLogParseStatusOr(RtcEventLogParseStatus status)
      : status_(status), value_() {}
  explicit RtcEventLogParseStatusOr(const T& value)
      : status_(), value_(value) {}

  bool ok() const { return status_.ok(); }

  std::string message() const { return status_.message(); }

  const T& value() const {
    RTC_DCHECK(ok());
    return value_;
  }

  T& value() {
    RTC_DCHECK(ok());
    return value_;
  }

  static RtcEventLogParseStatusOr Error(std::string error,
                                        std::string file,
                                        int line) {
    return RtcEventLogParseStatusOr(error, file, line);
  }

 private:
  RtcEventLogParseStatusOr() : status_() {}
  RtcEventLogParseStatusOr(std::string error, std::string file, int line)
      : status_(error, file, line), value_() {}

  RtcEventLogParseStatus status_;
  T value_;
};

namespace webrtc {

class EventParser {
 public:
  struct ValueAndPostionView {
    rtc::ArrayView<uint64_t> values;
    rtc::ArrayView<uint8_t> positions;
  };

  EventParser() = default;

  // N.B: This method stores a abls::string_view into the string to be
  // parsed. The caller is responsible for ensuring that the actual string
  // remains unmodified and outlives the EventParser.
  RtcEventLogParseStatus Initialize(absl::string_view s, bool batched);

  // Attempts to parse the field specified by `params`, skipping past
  // other fields that may occur before it. If 'required_field == true',
  // then failing to find the field is an error, otherwise the functions
  // return success, but with an empty view of values.
  RtcEventLogParseStatusOr<rtc::ArrayView<absl::string_view>> ParseStringField(
      const FieldParameters& params,
      bool required_field = true);
  RtcEventLogParseStatusOr<rtc::ArrayView<uint64_t>> ParseNumericField(
      const FieldParameters& params,
      bool required_field = true);
  RtcEventLogParseStatusOr<ValueAndPostionView> ParseOptionalNumericField(
      const FieldParameters& params,
      bool required_field = true);

  // Number of events in a batch.
  uint64_t NumEventsInBatch() const { return num_events_; }

  // Bytes remaining in `pending_data_`. Assuming there are no unknown
  // fields, BytesRemaining() should return 0 when all known fields
  // in the event have been parsed.
  size_t RemainingBytes() const { return pending_data_.size(); }

 private:
  uint64_t ReadLittleEndian(uint8_t bytes);
  uint64_t ReadVarInt();
  uint64_t ReadSingleValue(FieldType field_type);
  uint64_t ReadOptionalValuePositions();
  void ReadDeltasAndPopulateValues(FixedLengthEncodingParametersV3 params,
                                   uint64_t num_deltas,
                                   const uint64_t base);
  RtcEventLogParseStatus ParseNumericFieldInternal(uint64_t value_bit_width,
                                                   FieldType field_type);
  RtcEventLogParseStatus ParseStringFieldInternal();

  // Attempts to parse the field specified by `params`, skipping past
  // other fields that may occur before it. Returns
  // RtcEventLogParseStatus::Success() and populates `values_` (and
  // `positions_`) if the field is found. Returns
  // RtcEventLogParseStatus::Success() and clears `values_` (and `positions_`)
  // if the field doesn't exist. Returns a RtcEventLogParseStatus::Error() if
  // the log is incomplete, malformed or otherwise can't be parsed.
  RtcEventLogParseStatus ParseField(const FieldParameters& params);

  void SetError() { error_ = true; }
  bool Ok() const { return !error_; }

  rtc::ArrayView<uint64_t> GetValues() { return values_; }
  rtc::ArrayView<uint8_t> GetPositions() { return positions_; }
  rtc::ArrayView<absl::string_view> GetStrings() { return strings_; }

  void ClearTemporaries() {
    positions_.clear();
    values_.clear();
    strings_.clear();
  }

  // Tracks whether an error has occurred in one of the helper
  // functions above.
  bool error_ = false;

  // Temporary storage for result.
  std::vector<uint8_t> positions_;
  std::vector<uint64_t> values_;
  std::vector<absl::string_view> strings_;

  // String to be consumed.
  absl::string_view pending_data_;
  uint64_t num_events_ = 1;
  uint64_t last_field_id_ = FieldParameters::kTimestampField;
};

}  // namespace webrtc
#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_FIELD_ENCODING_PARSER_H_

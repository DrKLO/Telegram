/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_FIELD_ENCODING_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_FIELD_ENCODING_H_

#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/rtc_event_log/rtc_event.h"
#include "logging/rtc_event_log/encoder/rtc_event_log_encoder_common.h"
#include "logging/rtc_event_log/events/fixed_length_encoding_parameters_v3.h"
#include "logging/rtc_event_log/events/rtc_event_field_extraction.h"
#include "rtc_base/logging.h"

namespace webrtc {

// To maintain backwards compatibility with past (or future) logs,
// the constants in this enum must not be changed.
// New field types with numerical IDs 5-7 can be added, but old
// parsers will fail to parse events containing the new fields.
enum class FieldType : uint8_t {
  kFixed8 = 0,
  kFixed32 = 1,
  kFixed64 = 2,
  kVarInt = 3,
  kString = 4,
};

// EventParameters map an event name to a numerical ID.
struct EventParameters {
  // The name is primarily used for debugging purposes.
  const char* const name;
  //
  const RtcEvent::Type id;
};

// FieldParameters define the encoding for a field.
struct FieldParameters {
  // The name is primarily used for debugging purposes.
  const char* const name;
  // Numerical ID for the field. Must be strictly greater than 0,
  // and unique within each event type.
  const uint64_t field_id;
  // Encoding type for the base (i.e. non-delta) field in a batch.
  const FieldType field_type;
  // Number of bits after which wrap-around occurs. In most cases,
  // this should be the number of bits in the field data type, i.e.
  // 8 for an uint8_t, 32 for a int32_t and so on. However, `value_width`
  // can be used to achieve a more efficient encoding if it is known
  // that the field uses a smaller number of bits. For example, a
  // 15-bit counter could set `value_width` to 15 even if the data is
  // actually stored in a uint32_t.
  const uint64_t value_width;
  // Field ID 0 is reserved for timestamps.
  static constexpr uint64_t kTimestampField = 0;
};

// The EventEncoder is used to encode a batch of events.
class EventEncoder {
 public:
  EventEncoder(EventParameters params, rtc::ArrayView<const RtcEvent*> batch);

  void EncodeField(const FieldParameters& params,
                   const std::vector<uint64_t>& values,
                   const std::vector<bool>* positions = nullptr);

  void EncodeField(const FieldParameters& params,
                   const ValuesWithPositions& values);

  void EncodeField(const FieldParameters& params,
                   const std::vector<absl::string_view>& values);

  std::string AsString();

 private:
  size_t batch_size_;
  uint32_t event_tag_;
  std::vector<std::string> encoded_fields_;
};

std::string EncodeSingleValue(uint64_t value, FieldType field_type);
std::string EncodeDeltasV3(FixedLengthEncodingParametersV3 params,
                           uint64_t base,
                           rtc::ArrayView<const uint64_t> values);

}  // namespace webrtc
#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_FIELD_ENCODING_H_


/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_field_encoding_parser.h"

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "logging/rtc_event_log/encoder/var_int.h"
#include "logging/rtc_event_log/events/rtc_event_field_encoding.h"
#include "logging/rtc_event_log/events/rtc_event_log_parse_status.h"
#include "rtc_base/bitstream_reader.h"
#include "rtc_base/checks.h"

namespace {
absl::optional<webrtc::FieldType> ConvertFieldType(uint64_t value) {
  switch (value) {
    case static_cast<uint64_t>(webrtc::FieldType::kFixed8):
      return webrtc::FieldType::kFixed8;
    case static_cast<uint64_t>(webrtc::FieldType::kFixed32):
      return webrtc::FieldType::kFixed32;
    case static_cast<uint64_t>(webrtc::FieldType::kFixed64):
      return webrtc::FieldType::kFixed64;
    case static_cast<uint64_t>(webrtc::FieldType::kVarInt):
      return webrtc::FieldType::kVarInt;
    case static_cast<uint64_t>(webrtc::FieldType::kString):
      return webrtc::FieldType::kString;
    default:
      return absl::nullopt;
  }
}
}  // namespace

namespace webrtc {

uint64_t EventParser::ReadLittleEndian(uint8_t bytes) {
  RTC_DCHECK_LE(bytes, sizeof(uint64_t));
  RTC_DCHECK_GE(bytes, 1);

  uint64_t value = 0;

  if (bytes > pending_data_.length()) {
    SetError();
    return value;
  }

  const uint8_t* p = reinterpret_cast<const uint8_t*>(pending_data_.data());
  unsigned int shift = 0;
  uint8_t remaining = bytes;
  while (remaining > 0) {
    value += (static_cast<uint64_t>(*p) << shift);
    shift += 8;
    ++p;
    --remaining;
  }

  pending_data_ = pending_data_.substr(bytes);
  return value;
}

uint64_t EventParser::ReadVarInt() {
  uint64_t output = 0;
  bool success;
  std::tie(success, pending_data_) = DecodeVarInt(pending_data_, &output);
  if (!success) {
    SetError();
  }
  return output;
}

uint64_t EventParser::ReadOptionalValuePositions() {
  RTC_DCHECK(positions_.empty());
  size_t bits_to_read = NumEventsInBatch();
  positions_.reserve(bits_to_read);
  if (pending_data_.size() * 8 < bits_to_read) {
    SetError();
    return 0;
  }

  BitstreamReader reader(pending_data_);
  for (size_t i = 0; i < bits_to_read; i++) {
    positions_.push_back(reader.ReadBit());
  }
  if (!reader.Ok()) {
    SetError();
    return 0;
  }

  size_t num_existing_values =
      std::count(positions_.begin(), positions_.end(), 1);
  pending_data_ = pending_data_.substr((bits_to_read + 7) / 8);
  return num_existing_values;
}

uint64_t EventParser::ReadSingleValue(FieldType field_type) {
  switch (field_type) {
    case FieldType::kFixed8:
      return ReadLittleEndian(/*bytes=*/1);
    case FieldType::kFixed32:
      return ReadLittleEndian(/*bytes=*/4);
    case FieldType::kFixed64:
      return ReadLittleEndian(/*bytes=*/8);
    case FieldType::kVarInt:
      return ReadVarInt();
    case FieldType::kString:
      RTC_DCHECK_NOTREACHED();
      SetError();
      return 0;
  }
  RTC_DCHECK_NOTREACHED();
  SetError();
  return 0;
}

void EventParser::ReadDeltasAndPopulateValues(
    FixedLengthEncodingParametersV3 params,
    uint64_t num_deltas,
    uint64_t base) {
  RTC_DCHECK(values_.empty());
  values_.reserve(num_deltas + 1);
  values_.push_back(base);

  if (pending_data_.size() * 8 < num_deltas * params.delta_bit_width()) {
    SetError();
    return;
  }

  BitstreamReader reader(pending_data_);
  const uint64_t top_bit = static_cast<uint64_t>(1)
                           << (params.delta_bit_width() - 1);

  uint64_t value = base;
  for (uint64_t i = 0; i < num_deltas; ++i) {
    uint64_t delta = reader.ReadBits(params.delta_bit_width());
    RTC_DCHECK_LE(value, webrtc_event_logging::MaxUnsignedValueOfBitWidth(
                             params.value_bit_width()));
    RTC_DCHECK_LE(delta, webrtc_event_logging::MaxUnsignedValueOfBitWidth(
                             params.delta_bit_width()));
    bool negative_delta = params.signed_deltas() && ((delta & top_bit) != 0);
    if (negative_delta) {
      uint64_t delta_abs = (~delta & params.delta_mask()) + 1;
      value = (value - delta_abs) & params.value_mask();
    } else {
      value = (value + delta) & params.value_mask();
    }
    values_.push_back(value);
  }

  if (!reader.Ok()) {
    SetError();
    return;
  }

  pending_data_ =
      pending_data_.substr((num_deltas * params.delta_bit_width() + 7) / 8);
}

RtcEventLogParseStatus EventParser::Initialize(absl::string_view s,
                                               bool batched) {
  pending_data_ = s;
  num_events_ = 1;

  if (batched) {
    num_events_ = ReadVarInt();
    if (!Ok()) {
      return RtcEventLogParseStatus::Error(
          "Failed to read number of events in batch.", __FILE__, __LINE__);
    }
  }
  return RtcEventLogParseStatus::Success();
}

RtcEventLogParseStatus EventParser::ParseNumericFieldInternal(
    uint64_t value_bit_width,
    FieldType field_type) {
  RTC_DCHECK(values_.empty());
  RTC_DCHECK(positions_.empty());

  if (num_events_ == 1) {
    // Just a single value in the batch.
    uint64_t base = ReadSingleValue(field_type);
    if (!Ok()) {
      return RtcEventLogParseStatus::Error("Failed to read value", __FILE__,
                                           __LINE__);
    }
    positions_.push_back(true);
    values_.push_back(base);
  } else {
    // Delta compressed batch.
    // Read delta header.
    uint64_t header_value = ReadVarInt();
    if (!Ok())
      return RtcEventLogParseStatus::Error("Failed to read delta header",
                                           __FILE__, __LINE__);
    // NB: value_bit_width may be incorrect for the field, if this isn't the
    // field we are looking for.
    absl::optional<FixedLengthEncodingParametersV3> delta_header =
        FixedLengthEncodingParametersV3::ParseDeltaHeader(header_value,
                                                          value_bit_width);
    if (!delta_header.has_value()) {
      return RtcEventLogParseStatus::Error("Failed to parse delta header",
                                           __FILE__, __LINE__);
    }

    uint64_t num_existing_deltas = NumEventsInBatch() - 1;
    if (delta_header->values_optional()) {
      size_t num_nonempty_values = ReadOptionalValuePositions();
      if (!Ok()) {
        return RtcEventLogParseStatus::Error(
            "Failed to read positions of optional values", __FILE__, __LINE__);
      }
      if (num_nonempty_values < 1 || NumEventsInBatch() < num_nonempty_values) {
        return RtcEventLogParseStatus::Error(
            "Expected at least one non_empty value", __FILE__, __LINE__);
      }
      num_existing_deltas = num_nonempty_values - 1;
    } else {
      // All elements in the batch have values.
      positions_.assign(NumEventsInBatch(), 1u);
    }

    // Read base.
    uint64_t base = ReadSingleValue(field_type);
    if (!Ok()) {
      return RtcEventLogParseStatus::Error("Failed to read value", __FILE__,
                                           __LINE__);
    }

    if (delta_header->values_equal()) {
      // Duplicate the base value num_existing_deltas times.
      values_.assign(num_existing_deltas + 1, base);
    } else {
      // Read deltas; ceil(num_existing_deltas*delta_width/8) bits
      ReadDeltasAndPopulateValues(delta_header.value(), num_existing_deltas,
                                  base);
      if (!Ok()) {
        return RtcEventLogParseStatus::Error("Failed to decode deltas",
                                             __FILE__, __LINE__);
      }
    }
  }
  return RtcEventLogParseStatus::Success();
}

RtcEventLogParseStatus EventParser::ParseStringFieldInternal() {
  RTC_DCHECK(strings_.empty());
  if (num_events_ > 1) {
    // String encoding params reserved for future use.
    uint64_t encoding_params = ReadVarInt();
    if (!Ok()) {
      return RtcEventLogParseStatus::Error("Failed to read string encoding",
                                           __FILE__, __LINE__);
    }
    if (encoding_params != 0) {
      return RtcEventLogParseStatus::Error(
          "Unrecognized string encoding parameters", __FILE__, __LINE__);
    }
  }
  strings_.reserve(num_events_);
  for (uint64_t i = 0; i < num_events_; ++i) {
    // Just a single value in the batch.
    uint64_t size = ReadVarInt();
    if (!Ok()) {
      return RtcEventLogParseStatus::Error("Failed to read string size",
                                           __FILE__, __LINE__);
    }
    if (size > pending_data_.size()) {
      return RtcEventLogParseStatus::Error("String size exceeds remaining data",
                                           __FILE__, __LINE__);
    }
    strings_.push_back(pending_data_.substr(0, size));
    pending_data_ = pending_data_.substr(size);
  }
  return RtcEventLogParseStatus::Success();
}

RtcEventLogParseStatus EventParser::ParseField(const FieldParameters& params) {
  // Verify that the event parses fields in increasing order.
  if (params.field_id == FieldParameters::kTimestampField) {
    RTC_DCHECK_EQ(last_field_id_, FieldParameters::kTimestampField);
  } else {
    RTC_DCHECK_GT(params.field_id, last_field_id_);
  }
  last_field_id_ = params.field_id;

  // Initialization for positional fields that don't encode field ID and type.
  uint64_t field_id = params.field_id;
  FieldType field_type = params.field_type;

  // Fields are encoded in increasing field_id order.
  // Skip unknown fields with field_id < params.field_id until we either
  // find params.field_id or a field with higher id, in which case we know that
  // params.field_id doesn't exist.
  while (!pending_data_.empty()) {
    absl::string_view field_start = pending_data_;
    ClearTemporaries();

    // Read tag for non-positional fields.
    if (params.field_id != FieldParameters::kTimestampField) {
      uint64_t field_tag = ReadVarInt();
      if (!Ok())
        return RtcEventLogParseStatus::Error("Failed to read field tag",
                                             __FILE__, __LINE__);
      // Split tag into field ID and field type.
      field_id = field_tag >> 3;
      absl::optional<FieldType> conversion = ConvertFieldType(field_tag & 7u);
      if (!conversion.has_value())
        return RtcEventLogParseStatus::Error("Failed to parse field type",
                                             __FILE__, __LINE__);
      field_type = conversion.value();
    }

    if (field_id > params.field_id) {
      // We've passed all fields with ids less than or equal to what we are
      // looking for. Reset pending_data_ to first field with id higher than
      // params.field_id, since we didn't find the field we were looking for.
      pending_data_ = field_start;
      return RtcEventLogParseStatus::Success();
    }

    if (field_type == FieldType::kString) {
      auto status = ParseStringFieldInternal();
      if (!status.ok()) {
        return status;
      }
    } else {
      auto status = ParseNumericFieldInternal(params.value_width, field_type);
      if (!status.ok()) {
        return status;
      }
    }

    if (field_id == params.field_id) {
      // The field we're looking for has been found and values populated.
      return RtcEventLogParseStatus::Success();
    }
  }

  // Field not found because the event ended.
  ClearTemporaries();
  return RtcEventLogParseStatus::Success();
}

RtcEventLogParseStatusOr<rtc::ArrayView<absl::string_view>>
EventParser::ParseStringField(const FieldParameters& params,
                              bool required_field) {
  using StatusOr = RtcEventLogParseStatusOr<rtc::ArrayView<absl::string_view>>;
  RTC_DCHECK_EQ(params.field_type, FieldType::kString);
  auto status = ParseField(params);
  if (!status.ok())
    return StatusOr(status);
  rtc::ArrayView<absl::string_view> strings = GetStrings();
  if (required_field && strings.size() != NumEventsInBatch()) {
    return StatusOr::Error("Required string field not found", __FILE__,
                           __LINE__);
  }
  return StatusOr(strings);
}

RtcEventLogParseStatusOr<rtc::ArrayView<uint64_t>>
EventParser::ParseNumericField(const FieldParameters& params,
                               bool required_field) {
  using StatusOr = RtcEventLogParseStatusOr<rtc::ArrayView<uint64_t>>;
  RTC_DCHECK_NE(params.field_type, FieldType::kString);
  auto status = ParseField(params);
  if (!status.ok())
    return StatusOr(status);
  rtc::ArrayView<uint64_t> values = GetValues();
  if (required_field && values.size() != NumEventsInBatch()) {
    return StatusOr::Error("Required numerical field not found", __FILE__,
                           __LINE__);
  }
  return StatusOr(values);
}

RtcEventLogParseStatusOr<EventParser::ValueAndPostionView>
EventParser::ParseOptionalNumericField(const FieldParameters& params,
                                       bool required_field) {
  using StatusOr = RtcEventLogParseStatusOr<ValueAndPostionView>;
  RTC_DCHECK_NE(params.field_type, FieldType::kString);
  auto status = ParseField(params);
  if (!status.ok())
    return StatusOr(status);
  ValueAndPostionView view{GetValues(), GetPositions()};
  if (required_field && view.positions.size() != NumEventsInBatch()) {
    return StatusOr::Error("Required numerical field not found", __FILE__,
                           __LINE__);
  }
  return StatusOr(view);
}

}  // namespace webrtc

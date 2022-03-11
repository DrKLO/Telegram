/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_field_encoding.h"

#include <algorithm>
#include <limits>
#include <memory>
#include <utility>

#include "logging/rtc_event_log/encoder/bit_writer.h"
#include "logging/rtc_event_log/encoder/var_int.h"
#include "logging/rtc_event_log/events/rtc_event_field_extraction.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

using webrtc_event_logging::UnsignedDelta;

namespace {

std::string SerializeLittleEndian(uint64_t value, uint8_t bytes) {
  RTC_DCHECK_LE(bytes, sizeof(uint64_t));
  RTC_DCHECK_GE(bytes, 1);
  if (bytes < sizeof(uint64_t)) {
    // Note that shifting a 64-bit value by 64 (or more) bits is undefined.
    RTC_DCHECK_EQ(value >> (8 * bytes), 0);
  }
  std::string output(bytes, 0);
  // Getting a non-const pointer to the representation. See e.g.
  // https://en.cppreference.com/w/cpp/string/basic_string:
  // "The elements of a basic_string are stored contiguously,
  // that is, [...] a pointer to s[0] can be passed to functions
  // that expect a pointer to the first element of a null-terminated
  // CharT[] array."
  uint8_t* p = reinterpret_cast<uint8_t*>(&output[0]);
#ifdef WEBRTC_ARCH_LITTLE_ENDIAN
  memcpy(p, &value, bytes);
#else
  while (bytes > 0) {
    *p = static_cast<uint8_t>(value & 0xFF);
    value >>= 8;
    ++p;
    --bytes;
  }
#endif  // WEBRTC_ARCH_LITTLE_ENDIAN
  return output;
}

}  // namespace

namespace webrtc {

std::string EncodeOptionalValuePositions(std::vector<bool> positions) {
  BitWriter writer((positions.size() + 7) / 8);
  for (bool position : positions) {
    writer.WriteBits(position ? 1u : 0u, 1);
  }
  return writer.GetString();
}

std::string EncodeSingleValue(uint64_t value, FieldType field_type) {
  switch (field_type) {
    case FieldType::kFixed8:
      return SerializeLittleEndian(value, /*bytes=*/1);
    case FieldType::kFixed32:
      return SerializeLittleEndian(value, /*bytes=*/4);
    case FieldType::kFixed64:
      return SerializeLittleEndian(value, /*bytes=*/8);
    case FieldType::kVarInt:
      return EncodeVarInt(value);
    case FieldType::kString:
      RTC_DCHECK_NOTREACHED();
      return std::string();
  }
  RTC_DCHECK_NOTREACHED();
  return std::string();
}

absl::optional<FieldType> ConvertFieldType(uint64_t value) {
  switch (value) {
    case static_cast<uint64_t>(FieldType::kFixed8):
      return FieldType::kFixed8;
    case static_cast<uint64_t>(FieldType::kFixed32):
      return FieldType::kFixed32;
    case static_cast<uint64_t>(FieldType::kFixed64):
      return FieldType::kFixed64;
    case static_cast<uint64_t>(FieldType::kVarInt):
      return FieldType::kVarInt;
    case static_cast<uint64_t>(FieldType::kString):
      return FieldType::kString;
    default:
      return absl::nullopt;
  }
}

std::string EncodeDeltasV3(FixedLengthEncodingParametersV3 params,
                           uint64_t base,
                           rtc::ArrayView<const uint64_t> values) {
  size_t outputbound = (values.size() * params.delta_bit_width() + 7) / 8;
  BitWriter writer(outputbound);

  uint64_t previous = base;
  for (uint64_t value : values) {
    if (params.signed_deltas()) {
      uint64_t positive_delta =
          UnsignedDelta(previous, value, params.value_mask());
      uint64_t negative_delta =
          UnsignedDelta(value, previous, params.value_mask());
      uint64_t delta;
      if (positive_delta <= negative_delta) {
        delta = positive_delta;
      } else {
        // Compute the two's complement representation of a negative
        // delta, in a field width params_.delta_mask().
        RTC_DCHECK_GE(params.delta_mask(), negative_delta);
        RTC_DCHECK_LT(params.delta_mask() - negative_delta,
                      params.delta_mask());
        delta = params.delta_mask() - negative_delta + 1;
        RTC_DCHECK_LE(delta, params.delta_mask());
      }
      writer.WriteBits(delta, params.delta_bit_width());
    } else {
      uint64_t delta = UnsignedDelta(previous, value, params.value_mask());
      writer.WriteBits(delta, params.delta_bit_width());
    }
    previous = value;
  }

  return writer.GetString();
}

EventEncoder::EventEncoder(EventParameters params,
                           rtc::ArrayView<const RtcEvent*> batch) {
  batch_size_ = batch.size();
  if (!batch.empty()) {
    // Encode event type.
    uint32_t batched = batch.size() > 1 ? 1 : 0;
    event_tag_ = (static_cast<uint32_t>(params.id) << 1) + batched;

    // Event tag and number of encoded bytes will be filled in when the
    // encoding is finalized in AsString().

    // Encode number of events in batch
    if (batched) {
      encoded_fields_.push_back(EncodeVarInt(batch.size()));
    }

    // Encode timestamp
    std::vector<uint64_t> timestamps;
    timestamps.reserve(batch.size());
    for (const RtcEvent* event : batch) {
      timestamps.push_back(EncodeAsUnsigned(event->timestamp_ms()));
    }
    constexpr FieldParameters timestamp_params{"timestamp_ms",
                                               FieldParameters::kTimestampField,
                                               FieldType::kVarInt, 64};
    EncodeField(timestamp_params, timestamps);
  }
}

void EventEncoder::EncodeField(const FieldParameters& params,
                               const ValuesWithPositions& values) {
  return EncodeField(params, values.values, &values.position_mask);
}

void EventEncoder::EncodeField(const FieldParameters& params,
                               const std::vector<uint64_t>& values,
                               const std::vector<bool>* positions) {
  if (positions) {
    RTC_DCHECK_EQ(positions->size(), batch_size_);
    RTC_DCHECK_LE(values.size(), batch_size_);
  } else {
    RTC_DCHECK_EQ(values.size(), batch_size_);
  }

  if (values.size() == 0) {
    // If all values for a particular field is empty/nullopt,
    // then we completely skip the field even if the the batch is non-empty.
    return;
  }

  // We know that each event starts with the varint encoded timestamp,
  // so we omit that field tag (field id + field type). In all other
  // cases, we write the field tag.
  if (params.field_id != FieldParameters::kTimestampField) {
    RTC_DCHECK_LE(params.field_id, std::numeric_limits<uint64_t>::max() >> 3);
    uint64_t field_tag = params.field_id << 3;
    field_tag += static_cast<uint64_t>(params.field_type);
    encoded_fields_.push_back(EncodeVarInt(field_tag));
  }

  RTC_CHECK_GE(values.size(), 1);
  if (batch_size_ == 1) {
    encoded_fields_.push_back(EncodeSingleValue(values[0], params.field_type));
    return;
  }

  const bool values_optional = values.size() != batch_size_;

  // Compute delta parameters
  rtc::ArrayView<const uint64_t> all_values(values);
  uint64_t base = values[0];
  rtc::ArrayView<const uint64_t> remaining_values(all_values.subview(1));

  FixedLengthEncodingParametersV3 delta_params =
      FixedLengthEncodingParametersV3::CalculateParameters(
          base, remaining_values, params.value_width, values_optional);

  encoded_fields_.push_back(EncodeVarInt(delta_params.DeltaHeaderAsInt()));

  if (values_optional) {
    RTC_CHECK(positions);
    encoded_fields_.push_back(EncodeOptionalValuePositions(*positions));
  }
  // Base element, encoded as uint8, uint32, uint64 or varint
  encoded_fields_.push_back(EncodeSingleValue(base, params.field_type));

  // If all (existing) values are equal to the base, then we can skip
  // writing the all-zero deltas, and instead infer those from the delta
  // header.
  if (!delta_params.values_equal()) {
    encoded_fields_.push_back(
        EncodeDeltasV3(delta_params, base, remaining_values));
  }
}

void EventEncoder::EncodeField(const FieldParameters& params,
                               const std::vector<absl::string_view>& values) {
  RTC_DCHECK_EQ(values.size(), batch_size_);

  if (values.size() == 0) {
    // If all values for a particular field is empty/nullopt,
    // then we completely skip the field even if the the batch is non-empty.
    return;
  }

  // Write the field tag.
  RTC_CHECK_NE(params.field_id, FieldParameters::kTimestampField);
  RTC_DCHECK_LE(params.field_id, std::numeric_limits<uint64_t>::max() >> 3);
  RTC_DCHECK_EQ(params.field_type, FieldType::kString);
  uint64_t field_tag = params.field_id << 3;
  field_tag += static_cast<uint64_t>(params.field_type);
  encoded_fields_.push_back(EncodeVarInt(field_tag));

  if (values.size() > 1) {
    // If multiple values in the batch, write the encoding
    // parameters. (Values >0 reserved for future use.)
    uint64_t encoding_params = 0;
    encoded_fields_.push_back(EncodeVarInt(encoding_params));
  }

  // Write the strings as (length, data) pairs.
  for (absl::string_view s : values) {
    encoded_fields_.push_back(EncodeVarInt(s.size()));
    encoded_fields_.push_back(std::string(s));
  }
}

std::string EventEncoder::AsString() {
  std::string encoded_event;

  if (batch_size_ == 0) {
    RTC_DCHECK_EQ(encoded_fields_.size(), 0);
    return encoded_event;
  }

  // Compute size of encoded fields.
  size_t total_fields_size = 0;
  for (const std::string& s : encoded_fields_) {
    total_fields_size += s.size();
  }

  constexpr size_t kExpectedMaxEventTagBytes = 4;
  constexpr size_t kExpectedMaxSizeEncodingBytes = 4;
  encoded_event.reserve(kExpectedMaxEventTagBytes +
                        kExpectedMaxSizeEncodingBytes + total_fields_size);

  // Encode event tag (event id and whether batch or single event).
  encoded_event.append(EncodeVarInt(event_tag_));

  // Encode size of the remaining fields.
  encoded_event.append(EncodeVarInt(total_fields_size));

  // Append encoded fields.
  for (const std::string& s : encoded_fields_) {
    encoded_event.append(s);
  }

  return encoded_event;
}

}  // namespace webrtc

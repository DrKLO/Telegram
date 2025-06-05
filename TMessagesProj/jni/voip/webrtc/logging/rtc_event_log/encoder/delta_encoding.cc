/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/encoder/delta_encoding.h"

#include <algorithm>
#include <limits>
#include <memory>
#include <utility>

#include "absl/memory/memory.h"
#include "absl/strings/string_view.h"
#include "logging/rtc_event_log/encoder/bit_writer.h"
#include "logging/rtc_event_log/encoder/var_int.h"
#include "rtc_base/bit_buffer.h"
#include "rtc_base/bitstream_reader.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {
namespace {

// TODO(eladalon): Only build the decoder in tools and unit tests.

bool g_force_unsigned_for_testing = false;
bool g_force_signed_for_testing = false;

size_t BitsToBytes(size_t bits) {
  return (bits / 8) + (bits % 8 > 0 ? 1 : 0);
}

// TODO(eladalon): Replace by something more efficient.
uint64_t UnsignedBitWidth(uint64_t input, bool zero_val_as_zero_width = false) {
  if (zero_val_as_zero_width && input == 0) {
    return 0;
  }

  uint64_t width = 0;
  do {  // input == 0 -> width == 1
    width += 1;
    input >>= 1;
  } while (input != 0);
  return width;
}

uint64_t SignedBitWidth(uint64_t max_pos_magnitude,
                        uint64_t max_neg_magnitude) {
  const uint64_t bitwidth_pos = UnsignedBitWidth(max_pos_magnitude, true);
  const uint64_t bitwidth_neg =
      (max_neg_magnitude > 0) ? UnsignedBitWidth(max_neg_magnitude - 1, true)
                              : 0;
  return 1 + std::max(bitwidth_pos, bitwidth_neg);
}

// Return the maximum integer of a given bit width.
// Examples:
// MaxUnsignedValueOfBitWidth(1) = 0x01
// MaxUnsignedValueOfBitWidth(6) = 0x3f
// MaxUnsignedValueOfBitWidth(8) = 0xff
// MaxUnsignedValueOfBitWidth(32) = 0xffffffff
uint64_t MaxUnsignedValueOfBitWidth(uint64_t bit_width) {
  RTC_DCHECK_GE(bit_width, 1);
  RTC_DCHECK_LE(bit_width, 64);
  return (bit_width == 64) ? std::numeric_limits<uint64_t>::max()
                           : ((static_cast<uint64_t>(1) << bit_width) - 1);
}

// Computes the delta between `previous` and `current`, under the assumption
// that wrap-around occurs after `width` is exceeded.
uint64_t UnsignedDelta(uint64_t previous, uint64_t current, uint64_t bit_mask) {
  return (current - previous) & bit_mask;
}

// Determines the encoding type (e.g. fixed-size encoding).
// Given an encoding type, may also distinguish between some variants of it
// (e.g. which fields of the fixed-size encoding are explicitly mentioned by
// the header, and which are implicitly assumed to hold certain default values).
enum class EncodingType {
  kFixedSizeUnsignedDeltasNoEarlyWrapNoOpt = 0,
  kFixedSizeSignedDeltasEarlyWrapAndOptSupported = 1,
  kReserved1 = 2,
  kReserved2 = 3,
  kNumberOfEncodingTypes  // Keep last
};

// The width of each field in the encoding header. Note that this is the
// width in case the field exists; not all fields occur in all encoding types.
constexpr size_t kBitsInHeaderForEncodingType = 2;
constexpr size_t kBitsInHeaderForDeltaWidthBits = 6;
constexpr size_t kBitsInHeaderForSignedDeltas = 1;
constexpr size_t kBitsInHeaderForValuesOptional = 1;
constexpr size_t kBitsInHeaderForValueWidthBits = 6;

static_assert(static_cast<size_t>(EncodingType::kNumberOfEncodingTypes) <=
                  1 << kBitsInHeaderForEncodingType,
              "Not all encoding types fit.");

// Default values for when the encoding header does not specify explicitly.
constexpr bool kDefaultSignedDeltas = false;
constexpr bool kDefaultValuesOptional = false;
constexpr uint64_t kDefaultValueWidthBits = 64;

// Parameters for fixed-size delta-encoding/decoding.
// These are tailored for the sequence which will be encoded (e.g. widths).
class FixedLengthEncodingParameters final {
 public:
  static bool ValidParameters(uint64_t delta_width_bits,
                              bool signed_deltas,
                              bool values_optional,
                              uint64_t value_width_bits) {
    return (1 <= delta_width_bits && delta_width_bits <= 64 &&
            1 <= value_width_bits && value_width_bits <= 64 &&
            delta_width_bits <= value_width_bits);
  }

  FixedLengthEncodingParameters(uint64_t delta_width_bits,
                                bool signed_deltas,
                                bool values_optional,
                                uint64_t value_width_bits)
      : delta_width_bits_(delta_width_bits),
        signed_deltas_(signed_deltas),
        values_optional_(values_optional),
        value_width_bits_(value_width_bits),
        delta_mask_(MaxUnsignedValueOfBitWidth(delta_width_bits_)),
        value_mask_(MaxUnsignedValueOfBitWidth(value_width_bits_)) {
    RTC_DCHECK(ValidParameters(delta_width_bits, signed_deltas, values_optional,
                               value_width_bits));
  }

  // Number of bits necessary to hold the widest(*) of the deltas between the
  // values in the sequence.
  // (*) - Widest might not be the largest, if signed deltas are used.
  uint64_t delta_width_bits() const { return delta_width_bits_; }

  // Whether deltas are signed.
  bool signed_deltas() const { return signed_deltas_; }

  // Whether the values of the sequence are optional. That is, it may be
  // that some of them do not have a value (not even a sentinel value indicating
  // invalidity).
  bool values_optional() const { return values_optional_; }

  // Number of bits necessary to hold the largest value in the sequence.
  uint64_t value_width_bits() const { return value_width_bits_; }

  // Masks where only the bits relevant to the deltas/values are turned on.
  uint64_t delta_mask() const { return delta_mask_; }
  uint64_t value_mask() const { return value_mask_; }

  void SetSignedDeltas(bool signed_deltas) { signed_deltas_ = signed_deltas; }
  void SetDeltaWidthBits(uint64_t delta_width_bits) {
    delta_width_bits_ = delta_width_bits;
    delta_mask_ = MaxUnsignedValueOfBitWidth(delta_width_bits);
  }

 private:
  uint64_t delta_width_bits_;  // Normally const, but mutable in tests.
  bool signed_deltas_;         // Normally const, but mutable in tests.
  const bool values_optional_;
  const uint64_t value_width_bits_;

  uint64_t delta_mask_;  // Normally const, but mutable in tests.
  const uint64_t value_mask_;
};

// Performs delta-encoding of a single (non-empty) sequence of values, using
// an encoding where all deltas are encoded using the same number of bits.
// (With the exception of optional elements; those are encoded as a bit vector
// with one bit per element, plus a fixed number of bits for every element that
// has a value.)
class FixedLengthDeltaEncoder final {
 public:
  // See webrtc::EncodeDeltas() for general details.
  // This function return a bit pattern that would allow the decoder to
  // determine whether it was produced by FixedLengthDeltaEncoder, and can
  // therefore be decoded by FixedLengthDeltaDecoder, or whether it was produced
  // by a different encoder.
  static std::string EncodeDeltas(
      absl::optional<uint64_t> base,
      const std::vector<absl::optional<uint64_t>>& values);

  FixedLengthDeltaEncoder(const FixedLengthDeltaEncoder&) = delete;
  FixedLengthDeltaEncoder& operator=(const FixedLengthDeltaEncoder&) = delete;

 private:
  // Calculate min/max values of unsigned/signed deltas, given the bit width
  // of all the values in the series.
  static void CalculateMinAndMaxDeltas(
      absl::optional<uint64_t> base,
      const std::vector<absl::optional<uint64_t>>& values,
      uint64_t bit_width,
      uint64_t* max_unsigned_delta,
      uint64_t* max_pos_signed_delta,
      uint64_t* min_neg_signed_delta);

  // No effect outside of unit tests.
  // In unit tests, may lead to forcing signed/unsigned deltas, etc.
  static void ConsiderTestOverrides(FixedLengthEncodingParameters* params,
                                    uint64_t delta_width_bits_signed,
                                    uint64_t delta_width_bits_unsigned);

  // FixedLengthDeltaEncoder objects are to be created by EncodeDeltas() and
  // released by it before it returns. They're mostly a convenient way to
  // avoid having to pass a lot of state between different functions.
  // Therefore, it was deemed acceptable to let them have a reference to
  // `values`, whose lifetime must exceed the lifetime of `this`.
  FixedLengthDeltaEncoder(const FixedLengthEncodingParameters& params,
                          absl::optional<uint64_t> base,
                          const std::vector<absl::optional<uint64_t>>& values,
                          size_t existent_values_count);

  // Perform delta-encoding using the parameters given to the ctor on the
  // sequence of values given to the ctor.
  std::string Encode();

  // Exact lengths.
  size_t OutputLengthBytes(size_t existent_values_count) const;
  size_t HeaderLengthBits() const;
  size_t EncodedDeltasLengthBits(size_t existent_values_count) const;

  // Encode the compression parameters into the stream.
  void EncodeHeader();

  // Encode a given delta into the stream.
  void EncodeDelta(uint64_t previous, uint64_t current);
  void EncodeUnsignedDelta(uint64_t previous, uint64_t current);
  void EncodeSignedDelta(uint64_t previous, uint64_t current);

  // The parameters according to which encoding will be done (width of
  // fields, whether signed deltas should be used, etc.)
  const FixedLengthEncodingParameters params_;

  // The encoding scheme assumes that at least one value is transmitted OOB,
  // so that the first value can be encoded as a delta from that OOB value,
  // which is `base_`.
  const absl::optional<uint64_t> base_;

  // The values to be encoded.
  // Note: This is a non-owning reference. See comment above ctor for details.
  const std::vector<absl::optional<uint64_t>>& values_;

  // Buffer into which encoded values will be written.
  // This is created dynmically as a way to enforce that the rest of the
  // ctor has finished running when this is constructed, so that the lower
  // bound on the buffer size would be guaranteed correct.
  std::unique_ptr<BitWriter> writer_;
};

// TODO(eladalon): Reduce the number of passes.
std::string FixedLengthDeltaEncoder::EncodeDeltas(
    absl::optional<uint64_t> base,
    const std::vector<absl::optional<uint64_t>>& values) {
  RTC_DCHECK(!values.empty());

  // As a special case, if all of the elements are identical to the base,
  // (including, for optional fields, about their existence/non-existence),
  // the empty string is used to signal that.
  if (std::all_of(
          values.cbegin(), values.cend(),
          [base](absl::optional<uint64_t> val) { return val == base; })) {
    return std::string();
  }

  bool non_decreasing = true;
  uint64_t max_value_including_base = base.value_or(0u);
  size_t existent_values_count = 0;
  {
    uint64_t previous = base.value_or(0u);
    for (size_t i = 0; i < values.size(); ++i) {
      if (!values[i].has_value()) {
        continue;
      }
      ++existent_values_count;
      non_decreasing &= (previous <= values[i].value());
      max_value_including_base =
          std::max(max_value_including_base, values[i].value());
      previous = values[i].value();
    }
  }

  // If the sequence is non-decreasing, it may be assumed to have width = 64;
  // there's no reason to encode the actual max width in the encoding header.
  const uint64_t value_width_bits =
      non_decreasing ? 64 : UnsignedBitWidth(max_value_including_base);

  uint64_t max_unsigned_delta;
  uint64_t max_pos_signed_delta;
  uint64_t min_neg_signed_delta;
  CalculateMinAndMaxDeltas(base, values, value_width_bits, &max_unsigned_delta,
                           &max_pos_signed_delta, &min_neg_signed_delta);

  const uint64_t delta_width_bits_unsigned =
      UnsignedBitWidth(max_unsigned_delta);
  const uint64_t delta_width_bits_signed =
      SignedBitWidth(max_pos_signed_delta, min_neg_signed_delta);

  // Note: Preference for unsigned if the two have the same width (efficiency).
  const bool signed_deltas =
      delta_width_bits_signed < delta_width_bits_unsigned;
  const uint64_t delta_width_bits =
      signed_deltas ? delta_width_bits_signed : delta_width_bits_unsigned;

  const bool values_optional =
      !base.has_value() || (existent_values_count < values.size());

  FixedLengthEncodingParameters params(delta_width_bits, signed_deltas,
                                       values_optional, value_width_bits);

  // No effect in production.
  ConsiderTestOverrides(&params, delta_width_bits_signed,
                        delta_width_bits_unsigned);

  FixedLengthDeltaEncoder encoder(params, base, values, existent_values_count);
  return encoder.Encode();
}

void FixedLengthDeltaEncoder::CalculateMinAndMaxDeltas(
    absl::optional<uint64_t> base,
    const std::vector<absl::optional<uint64_t>>& values,
    uint64_t bit_width,
    uint64_t* max_unsigned_delta_out,
    uint64_t* max_pos_signed_delta_out,
    uint64_t* min_neg_signed_delta_out) {
  RTC_DCHECK(!values.empty());
  RTC_DCHECK(max_unsigned_delta_out);
  RTC_DCHECK(max_pos_signed_delta_out);
  RTC_DCHECK(min_neg_signed_delta_out);

  const uint64_t bit_mask = MaxUnsignedValueOfBitWidth(bit_width);

  uint64_t max_unsigned_delta = 0;
  uint64_t max_pos_signed_delta = 0;
  uint64_t min_neg_signed_delta = 0;

  absl::optional<uint64_t> prev = base;
  for (size_t i = 0; i < values.size(); ++i) {
    if (!values[i].has_value()) {
      continue;
    }

    if (!prev.has_value()) {
      // If the base is non-existent, the first existent value is encoded as
      // a varint, rather than as a delta.
      RTC_DCHECK(!base.has_value());
      prev = values[i];
      continue;
    }

    const uint64_t current = values[i].value();

    const uint64_t forward_delta = UnsignedDelta(*prev, current, bit_mask);
    const uint64_t backward_delta = UnsignedDelta(current, *prev, bit_mask);

    max_unsigned_delta = std::max(max_unsigned_delta, forward_delta);

    if (forward_delta < backward_delta) {
      max_pos_signed_delta = std::max(max_pos_signed_delta, forward_delta);
    } else {
      min_neg_signed_delta = std::max(min_neg_signed_delta, backward_delta);
    }

    prev = current;
  }

  *max_unsigned_delta_out = max_unsigned_delta;
  *max_pos_signed_delta_out = max_pos_signed_delta;
  *min_neg_signed_delta_out = min_neg_signed_delta;
}

void FixedLengthDeltaEncoder::ConsiderTestOverrides(
    FixedLengthEncodingParameters* params,
    uint64_t delta_width_bits_signed,
    uint64_t delta_width_bits_unsigned) {
  if (g_force_unsigned_for_testing) {
    params->SetDeltaWidthBits(delta_width_bits_unsigned);
    params->SetSignedDeltas(false);
  } else if (g_force_signed_for_testing) {
    params->SetDeltaWidthBits(delta_width_bits_signed);
    params->SetSignedDeltas(true);
  } else {
    // Unchanged.
  }
}

FixedLengthDeltaEncoder::FixedLengthDeltaEncoder(
    const FixedLengthEncodingParameters& params,
    absl::optional<uint64_t> base,
    const std::vector<absl::optional<uint64_t>>& values,
    size_t existent_values_count)
    : params_(params), base_(base), values_(values) {
  RTC_DCHECK(!values_.empty());
  writer_ =
      std::make_unique<BitWriter>(OutputLengthBytes(existent_values_count));
}

std::string FixedLengthDeltaEncoder::Encode() {
  EncodeHeader();

  if (params_.values_optional()) {
    // Encode which values exist and which don't.
    for (absl::optional<uint64_t> value : values_) {
      writer_->WriteBits(value.has_value() ? 1u : 0u, 1);
    }
  }

  absl::optional<uint64_t> previous = base_;
  for (absl::optional<uint64_t> value : values_) {
    if (!value.has_value()) {
      RTC_DCHECK(params_.values_optional());
      continue;
    }

    if (!previous.has_value()) {
      // If the base is non-existent, the first existent value is encoded as
      // a varint, rather than as a delta.
      RTC_DCHECK(!base_.has_value());
      writer_->WriteBits(EncodeVarInt(value.value()));
    } else {
      EncodeDelta(previous.value(), value.value());
    }

    previous = value;
  }

  return writer_->GetString();
}

size_t FixedLengthDeltaEncoder::OutputLengthBytes(
    size_t existent_values_count) const {
  return BitsToBytes(HeaderLengthBits() +
                     EncodedDeltasLengthBits(existent_values_count));
}

size_t FixedLengthDeltaEncoder::HeaderLengthBits() const {
  if (params_.signed_deltas() == kDefaultSignedDeltas &&
      params_.values_optional() == kDefaultValuesOptional &&
      params_.value_width_bits() == kDefaultValueWidthBits) {
    return kBitsInHeaderForEncodingType + kBitsInHeaderForDeltaWidthBits;
  } else {
    return kBitsInHeaderForEncodingType + kBitsInHeaderForDeltaWidthBits +
           kBitsInHeaderForSignedDeltas + kBitsInHeaderForValuesOptional +
           kBitsInHeaderForValueWidthBits;
  }
}

size_t FixedLengthDeltaEncoder::EncodedDeltasLengthBits(
    size_t existent_values_count) const {
  if (!params_.values_optional()) {
    return values_.size() * params_.delta_width_bits();
  } else {
    RTC_DCHECK_EQ(std::count_if(values_.begin(), values_.end(),
                                [](absl::optional<uint64_t> val) {
                                  return val.has_value();
                                }),
                  existent_values_count);
    // One bit for each delta, to indicate if the value exists, and delta_width
    // for each existent value, to indicate the delta itself.
    // If base_ is non-existent, the first value (if any) is encoded as a varint
    // rather than as a delta.
    const size_t existence_bitmap_size_bits = 1 * values_.size();
    const bool first_value_is_varint =
        !base_.has_value() && existent_values_count >= 1;
    const size_t first_value_varint_size_bits = 8 * kMaxVarIntLengthBytes;
    const size_t deltas_count = existent_values_count - first_value_is_varint;
    const size_t deltas_size_bits = deltas_count * params_.delta_width_bits();
    return existence_bitmap_size_bits + first_value_varint_size_bits +
           deltas_size_bits;
  }
}

void FixedLengthDeltaEncoder::EncodeHeader() {
  RTC_DCHECK(writer_);

  const EncodingType encoding_type =
      (params_.value_width_bits() == kDefaultValueWidthBits &&
       params_.signed_deltas() == kDefaultSignedDeltas &&
       params_.values_optional() == kDefaultValuesOptional)
          ? EncodingType::kFixedSizeUnsignedDeltasNoEarlyWrapNoOpt
          : EncodingType::kFixedSizeSignedDeltasEarlyWrapAndOptSupported;

  writer_->WriteBits(static_cast<uint64_t>(encoding_type),
                     kBitsInHeaderForEncodingType);

  // Note: Since it's meaningless for a field to be of width 0, when it comes
  // to fields that relate widths, we encode  width 1 as 0, width 2 as 1,

  writer_->WriteBits(params_.delta_width_bits() - 1,
                     kBitsInHeaderForDeltaWidthBits);

  if (encoding_type == EncodingType::kFixedSizeUnsignedDeltasNoEarlyWrapNoOpt) {
    return;
  }

  writer_->WriteBits(static_cast<uint64_t>(params_.signed_deltas()),
                     kBitsInHeaderForSignedDeltas);
  writer_->WriteBits(static_cast<uint64_t>(params_.values_optional()),
                     kBitsInHeaderForValuesOptional);
  writer_->WriteBits(params_.value_width_bits() - 1,
                     kBitsInHeaderForValueWidthBits);
}

void FixedLengthDeltaEncoder::EncodeDelta(uint64_t previous, uint64_t current) {
  if (params_.signed_deltas()) {
    EncodeSignedDelta(previous, current);
  } else {
    EncodeUnsignedDelta(previous, current);
  }
}

void FixedLengthDeltaEncoder::EncodeUnsignedDelta(uint64_t previous,
                                                  uint64_t current) {
  RTC_DCHECK(writer_);
  const uint64_t delta = UnsignedDelta(previous, current, params_.value_mask());
  writer_->WriteBits(delta, params_.delta_width_bits());
}

void FixedLengthDeltaEncoder::EncodeSignedDelta(uint64_t previous,
                                                uint64_t current) {
  RTC_DCHECK(writer_);

  const uint64_t forward_delta =
      UnsignedDelta(previous, current, params_.value_mask());
  const uint64_t backward_delta =
      UnsignedDelta(current, previous, params_.value_mask());

  uint64_t delta;
  if (forward_delta <= backward_delta) {
    delta = forward_delta;
  } else {
    // Compute the unsigned representation of a negative delta.
    // This is the two's complement representation of this negative value,
    // when deltas are of width params_.delta_mask().
    RTC_DCHECK_GE(params_.delta_mask(), backward_delta);
    RTC_DCHECK_LT(params_.delta_mask() - backward_delta, params_.delta_mask());
    delta = params_.delta_mask() - backward_delta + 1;
    RTC_DCHECK_LE(delta, params_.delta_mask());
  }

  writer_->WriteBits(delta, params_.delta_width_bits());
}

// Perform decoding of a a delta-encoded stream, extracting the original
// sequence of values.
class FixedLengthDeltaDecoder final {
 public:
  // Checks whether FixedLengthDeltaDecoder is a suitable decoder for this
  // bitstream. Note that this does NOT imply that stream is valid, and will
  // be decoded successfully. It DOES imply that all other decoder classes
  // will fail to decode this input, though.
  static bool IsSuitableDecoderFor(absl::string_view input);

  // Assuming that `input` is the result of fixed-size delta-encoding
  // that took place with the same value to `base` and over `num_of_deltas`
  // original values, this will return the sequence of original values.
  // If an error occurs (can happen if `input` is corrupt), an empty
  // vector will be returned.
  static std::vector<absl::optional<uint64_t>> DecodeDeltas(
      absl::string_view input,
      absl::optional<uint64_t> base,
      size_t num_of_deltas);

  FixedLengthDeltaDecoder(const FixedLengthDeltaDecoder&) = delete;
  FixedLengthDeltaDecoder& operator=(const FixedLengthDeltaDecoder&) = delete;

 private:
  // Reads the encoding header in `input` and returns a FixedLengthDeltaDecoder
  // with the corresponding configuration, that can be used to decode the
  // values in `input`.
  // If the encoding header is corrupt (contains an illegal configuration),
  // nullptr will be returned.
  // When a valid FixedLengthDeltaDecoder is returned, this does not mean that
  // the entire stream is free of error. Rather, only the encoding header is
  // examined and guaranteed.
  static std::unique_ptr<FixedLengthDeltaDecoder> Create(
      absl::string_view input,
      absl::optional<uint64_t> base,
      size_t num_of_deltas);

  // FixedLengthDeltaDecoder objects are to be created by DecodeDeltas() and
  // released by it before it returns. They're mostly a convenient way to
  // avoid having to pass a lot of state between different functions.
  // Therefore, it was deemed acceptable that `reader` does not own the buffer
  // it reads, meaning the lifetime of `this` must not exceed the lifetime
  // of `reader`'s underlying buffer.
  FixedLengthDeltaDecoder(BitstreamReader reader,
                          const FixedLengthEncodingParameters& params,
                          absl::optional<uint64_t> base,
                          size_t num_of_deltas);

  // Perform the decoding using the parameters given to the ctor.
  std::vector<absl::optional<uint64_t>> Decode();

  // Add `delta` to `base` to produce the next value in a sequence.
  // The delta is applied as signed/unsigned depending on the parameters
  // given to the ctor. Wrap-around is taken into account according to the
  // values' width, as specified by the aforementioned encoding parameters.
  uint64_t ApplyDelta(uint64_t base, uint64_t delta) const;

  // Helpers for ApplyDelta().
  uint64_t ApplyUnsignedDelta(uint64_t base, uint64_t delta) const;
  uint64_t ApplySignedDelta(uint64_t base, uint64_t delta) const;

  // Reader of the input stream to be decoded. Does not own that buffer.
  // See comment above ctor for details.
  BitstreamReader reader_;

  // The parameters according to which encoding will be done (width of
  // fields, whether signed deltas should be used, etc.)
  const FixedLengthEncodingParameters params_;

  // The encoding scheme assumes that at least one value is transmitted OOB,
  // so that the first value can be encoded as a delta from that OOB value,
  // which is `base_`.
  const absl::optional<uint64_t> base_;

  // The number of values to be known to be decoded.
  const size_t num_of_deltas_;
};

bool FixedLengthDeltaDecoder::IsSuitableDecoderFor(absl::string_view input) {
  BitstreamReader reader(input);
  uint64_t encoding_type_bits = reader.ReadBits(kBitsInHeaderForEncodingType);
  if (!reader.Ok()) {
    return false;
  }

  const auto encoding_type = static_cast<EncodingType>(encoding_type_bits);
  return encoding_type ==
             EncodingType::kFixedSizeUnsignedDeltasNoEarlyWrapNoOpt ||
         encoding_type ==
             EncodingType::kFixedSizeSignedDeltasEarlyWrapAndOptSupported;
}

std::vector<absl::optional<uint64_t>> FixedLengthDeltaDecoder::DecodeDeltas(
    absl::string_view input,
    absl::optional<uint64_t> base,
    size_t num_of_deltas) {
  auto decoder = FixedLengthDeltaDecoder::Create(input, base, num_of_deltas);
  if (!decoder) {
    return std::vector<absl::optional<uint64_t>>();
  }

  return decoder->Decode();
}

std::unique_ptr<FixedLengthDeltaDecoder> FixedLengthDeltaDecoder::Create(
    absl::string_view input,
    absl::optional<uint64_t> base,
    size_t num_of_deltas) {
  BitstreamReader reader(input);
  // Encoding type
  uint32_t encoding_type_bits = reader.ReadBits(kBitsInHeaderForEncodingType);
  if (!reader.Ok()) {
    return nullptr;
  }

  const EncodingType encoding = static_cast<EncodingType>(encoding_type_bits);
  if (encoding != EncodingType::kFixedSizeUnsignedDeltasNoEarlyWrapNoOpt &&
      encoding !=
          EncodingType::kFixedSizeSignedDeltasEarlyWrapAndOptSupported) {
    RTC_LOG(LS_WARNING) << "Unrecognized encoding type.";
    return nullptr;
  }

  // See encoding for +1's rationale.
  const uint64_t delta_width_bits =
      reader.ReadBits(kBitsInHeaderForDeltaWidthBits) + 1;
  RTC_DCHECK_LE(delta_width_bits, 64);

  // signed_deltas, values_optional, value_width_bits
  bool signed_deltas;
  bool values_optional;
  uint64_t value_width_bits;
  if (encoding == EncodingType::kFixedSizeUnsignedDeltasNoEarlyWrapNoOpt) {
    signed_deltas = kDefaultSignedDeltas;
    values_optional = kDefaultValuesOptional;
    value_width_bits = kDefaultValueWidthBits;
  } else {
    signed_deltas = reader.Read<bool>();
    values_optional = reader.Read<bool>();
    // See encoding for +1's rationale.
    value_width_bits = reader.ReadBits(kBitsInHeaderForValueWidthBits) + 1;
    RTC_DCHECK_LE(value_width_bits, 64);
  }

  if (!reader.Ok()) {
    return nullptr;
  }

  // Note: Because of the way the parameters are read, it is not possible
  // for illegal values to be read. We check nevertheless, in case the code
  // changes in the future in a way that breaks this promise.
  if (!FixedLengthEncodingParameters::ValidParameters(
          delta_width_bits, signed_deltas, values_optional, value_width_bits)) {
    RTC_LOG(LS_WARNING) << "Corrupt log; illegal encoding parameters.";
    return nullptr;
  }

  FixedLengthEncodingParameters params(delta_width_bits, signed_deltas,
                                       values_optional, value_width_bits);
  return absl::WrapUnique(
      new FixedLengthDeltaDecoder(reader, params, base, num_of_deltas));
}

FixedLengthDeltaDecoder::FixedLengthDeltaDecoder(
    BitstreamReader reader,
    const FixedLengthEncodingParameters& params,
    absl::optional<uint64_t> base,
    size_t num_of_deltas)
    : reader_(reader),
      params_(params),
      base_(base),
      num_of_deltas_(num_of_deltas) {
  RTC_DCHECK(reader_.Ok());
}

std::vector<absl::optional<uint64_t>> FixedLengthDeltaDecoder::Decode() {
  RTC_DCHECK(reader_.Ok());
  std::vector<bool> existing_values(num_of_deltas_);
  if (params_.values_optional()) {
    for (size_t i = 0; i < num_of_deltas_; ++i) {
      existing_values[i] = reader_.Read<bool>();
    }
  } else {
    std::fill(existing_values.begin(), existing_values.end(), true);
  }

  absl::optional<uint64_t> previous = base_;
  std::vector<absl::optional<uint64_t>> values(num_of_deltas_);

  for (size_t i = 0; i < num_of_deltas_; ++i) {
    if (!existing_values[i]) {
      RTC_DCHECK(params_.values_optional());
      continue;
    }

    if (!previous) {
      // If the base is non-existent, the first existent value is encoded as
      // a varint, rather than as a delta.
      RTC_DCHECK(!base_.has_value());
      values[i] = DecodeVarInt(reader_);
    } else {
      uint64_t delta = reader_.ReadBits(params_.delta_width_bits());
      values[i] = ApplyDelta(*previous, delta);
    }

    previous = values[i];
  }

  if (!reader_.Ok()) {
    values = {};
  }

  return values;
}

uint64_t FixedLengthDeltaDecoder::ApplyDelta(uint64_t base,
                                             uint64_t delta) const {
  RTC_DCHECK_LE(base, MaxUnsignedValueOfBitWidth(params_.value_width_bits()));
  RTC_DCHECK_LE(delta, MaxUnsignedValueOfBitWidth(params_.delta_width_bits()));
  return params_.signed_deltas() ? ApplySignedDelta(base, delta)
                                 : ApplyUnsignedDelta(base, delta);
}

uint64_t FixedLengthDeltaDecoder::ApplyUnsignedDelta(uint64_t base,
                                                     uint64_t delta) const {
  // Note: May still be used if signed deltas used.
  RTC_DCHECK_LE(base, MaxUnsignedValueOfBitWidth(params_.value_width_bits()));
  RTC_DCHECK_LE(delta, MaxUnsignedValueOfBitWidth(params_.delta_width_bits()));
  return (base + delta) & params_.value_mask();
}

uint64_t FixedLengthDeltaDecoder::ApplySignedDelta(uint64_t base,
                                                   uint64_t delta) const {
  RTC_DCHECK(params_.signed_deltas());
  RTC_DCHECK_LE(base, MaxUnsignedValueOfBitWidth(params_.value_width_bits()));
  RTC_DCHECK_LE(delta, MaxUnsignedValueOfBitWidth(params_.delta_width_bits()));

  const uint64_t top_bit = static_cast<uint64_t>(1)
                           << (params_.delta_width_bits() - 1);

  const bool positive_delta = ((delta & top_bit) == 0);
  if (positive_delta) {
    return ApplyUnsignedDelta(base, delta);
  }

  const uint64_t delta_abs = (~delta & params_.delta_mask()) + 1;
  return (base - delta_abs) & params_.value_mask();
}

}  // namespace

std::string EncodeDeltas(absl::optional<uint64_t> base,
                         const std::vector<absl::optional<uint64_t>>& values) {
  // TODO(eladalon): Support additional encodings.
  return FixedLengthDeltaEncoder::EncodeDeltas(base, values);
}

std::vector<absl::optional<uint64_t>> DecodeDeltas(
    absl::string_view input,
    absl::optional<uint64_t> base,
    size_t num_of_deltas) {
  RTC_DCHECK_GT(num_of_deltas, 0);  // Allows empty vector to indicate error.

  // The empty string is a special case indicating that all values were equal
  // to the base.
  if (input.empty()) {
    std::vector<absl::optional<uint64_t>> result(num_of_deltas);
    std::fill(result.begin(), result.end(), base);
    return result;
  }

  if (FixedLengthDeltaDecoder::IsSuitableDecoderFor(input)) {
    return FixedLengthDeltaDecoder::DecodeDeltas(input, base, num_of_deltas);
  }

  RTC_LOG(LS_WARNING) << "Could not decode delta-encoded stream.";
  return std::vector<absl::optional<uint64_t>>();
}

void SetFixedLengthEncoderDeltaSignednessForTesting(bool signedness) {
  g_force_unsigned_for_testing = !signedness;
  g_force_signed_for_testing = signedness;
}

void UnsetFixedLengthEncoderDeltaSignednessForTesting() {
  g_force_unsigned_for_testing = false;
  g_force_signed_for_testing = false;
}

}  // namespace webrtc

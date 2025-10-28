/*
 *  Copyright (c) 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/encoder/optional_blob_encoding.h"

#include <cstdint>

#include "rtc_base/bit_buffer.h"
#include "rtc_base/bitstream_reader.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

std::string EncodeOptionalBlobs(
    const std::vector<absl::optional<std::string>>& blobs) {
  if (blobs.empty()) {
    return {};
  }

  size_t reserve_size_bits = 1;
  size_t num_blobs_present = 0;
  for (const auto& blob : blobs) {
    if (blob.has_value()) {
      ++num_blobs_present;
      reserve_size_bits +=
          (rtc::BitBufferWriter::kMaxLeb128Length.bytes() + blob->size()) * 8;
    }
  }

  if (num_blobs_present == 0) {
    return {};
  }

  const bool all_blobs_present = num_blobs_present == blobs.size();
  if (!all_blobs_present) {
    reserve_size_bits += blobs.size();
  }

  std::vector<uint8_t> buffer((reserve_size_bits + 7) / 8);
  rtc::BitBufferWriter writer(buffer.data(), buffer.size());

  // Write present bits if all blobs are not present.
  writer.WriteBits(all_blobs_present, 1);
  if (!all_blobs_present) {
    for (const auto& blob : blobs) {
      writer.WriteBits(blob.has_value(), 1);
    }
  }

  // Byte align the writer.
  writer.ConsumeBits(writer.RemainingBitCount() % 8);

  // Write blobs.
  for (const auto& blob : blobs) {
    if (blob.has_value()) {
      writer.WriteLeb128(blob->length());
      writer.WriteString(*blob);
    }
  }

  size_t bytes_written;
  size_t bits_written;
  writer.GetCurrentOffset(&bytes_written, &bits_written);
  RTC_CHECK_EQ(bits_written, 0);
  RTC_CHECK_LE(bytes_written, buffer.size());

  return std::string(buffer.data(), buffer.data() + bytes_written);
}

std::vector<absl::optional<std::string>> DecodeOptionalBlobs(
    absl::string_view encoded_blobs,
    size_t num_of_blobs) {
  std::vector<absl::optional<std::string>> res(num_of_blobs);
  if (encoded_blobs.empty() || num_of_blobs == 0) {
    return res;
  }

  BitstreamReader reader(encoded_blobs);
  const bool all_blobs_present = reader.ReadBit();

  // Read present bits if all blobs are not present.
  std::vector<uint8_t> present;
  if (!all_blobs_present) {
    present.resize(num_of_blobs);
    for (size_t i = 0; i < num_of_blobs; ++i) {
      present[i] = reader.ReadBit();
    }
  }

  // Byte align the reader.
  reader.ConsumeBits(reader.RemainingBitCount() % 8);

  // Read the blobs.
  for (size_t i = 0; i < num_of_blobs; ++i) {
    if (!all_blobs_present && !present[i]) {
      continue;
    }
    res[i] = reader.ReadString(reader.ReadLeb128());
  }

  // The result is only valid if exactly all bits was consumed during decoding.
  if (!reader.Ok() || reader.RemainingBitCount() > 0) {
    return {};
  }

  return res;
}

}  // namespace webrtc

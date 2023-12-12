/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/encoder/var_int.h"

#include "rtc_base/bitstream_reader.h"
#include "rtc_base/checks.h"

// TODO(eladalon): Add unit tests.

namespace webrtc {

const size_t kMaxVarIntLengthBytes = 10;  // ceil(64 / 7.0) is 10.

std::string EncodeVarInt(uint64_t input) {
  std::string output;
  output.reserve(kMaxVarIntLengthBytes);

  do {
    uint8_t byte = static_cast<uint8_t>(input & 0x7f);
    input >>= 7;
    if (input > 0) {
      byte |= 0x80;
    }
    output += byte;
  } while (input > 0);

  RTC_DCHECK_GE(output.size(), 1u);
  RTC_DCHECK_LE(output.size(), kMaxVarIntLengthBytes);

  return output;
}

// There is some code duplication between the flavors of this function.
// For performance's sake, it's best to just keep it.
std::pair<bool, absl::string_view> DecodeVarInt(absl::string_view input,
                                                uint64_t* output) {
  RTC_DCHECK(output);

  uint64_t decoded = 0;
  for (size_t i = 0; i < input.length() && i < kMaxVarIntLengthBytes; ++i) {
    decoded += (static_cast<uint64_t>(input[i] & 0x7f)
                << static_cast<uint64_t>(7 * i));
    if (!(input[i] & 0x80)) {
      *output = decoded;
      return {true, input.substr(i + 1)};
    }
  }

  return {false, input};
}

// There is some code duplication between the flavors of this function.
// For performance's sake, it's best to just keep it.
uint64_t DecodeVarInt(BitstreamReader& input) {
  uint64_t decoded = 0;
  for (size_t i = 0; i < kMaxVarIntLengthBytes; ++i) {
    uint8_t byte = input.Read<uint8_t>();
    decoded +=
        (static_cast<uint64_t>(byte & 0x7f) << static_cast<uint64_t>(7 * i));
    if (!(byte & 0x80)) {
      return decoded;
    }
  }

  input.Invalidate();
  return 0;
}

}  // namespace webrtc

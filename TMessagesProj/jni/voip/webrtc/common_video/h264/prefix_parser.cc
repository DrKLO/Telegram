/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_video/h264/prefix_parser.h"

#include <cstdint>
#include <vector>

#include "common_video/h264/h264_common.h"
#include "rtc_base/bit_buffer.h"

namespace {
typedef absl::optional<webrtc::PrefixParser::PrefixState> OptionalPrefix;

#define RETURN_EMPTY_ON_FAIL(x) \
  if (!(x)) {                   \
    return OptionalPrefix();       \
  }
}  // namespace

namespace webrtc {

PrefixParser::PrefixState::PrefixState() = default;
PrefixParser::PrefixState::PrefixState(const PrefixState&) = default;
PrefixParser::PrefixState::~PrefixState() = default;

// General note: this is based off the 02/2016 version of the H.264 standard.
// You can find it on this page:
// http://www.itu.int/rec/T-REC-H.264

// Unpack RBSP and parse SVC extension state from the supplied buffer.
absl::optional<PrefixParser::PrefixState> PrefixParser::ParsePrefix(
    const uint8_t* data,
                                                        size_t length) {
  std::vector<uint8_t> unpacked_buffer = H264::ParseRbsp(data, length);
  rtc::BitBuffer bit_buffer(unpacked_buffer.data(), unpacked_buffer.size());
  return ParsePrefixUpToSvcExtension(&bit_buffer);
}

absl::optional<PrefixParser::PrefixState> PrefixParser::ParsePrefixUpToSvcExtension(
    rtc::BitBuffer* buffer) {
  // Now, we need to use a bit buffer to parse through the actual SVC extension
  // format. See Section 7.3.1 ("NAL unit syntax") and 7.3.1.1 ("NAL unit header
  // SVC extension syntax") of the H.264 standard for a complete description.

  PrefixState svc_extension;

  uint32_t svc_extension_flag = 0;
  // Make sure the svc_extension_flag is on.
  RETURN_EMPTY_ON_FAIL(buffer->ReadBits(&svc_extension_flag, 1));
  if (!svc_extension_flag)
    return OptionalPrefix();

  // idr_flag: u(1)
  RETURN_EMPTY_ON_FAIL(buffer->ReadBits(&svc_extension.idr_flag, 1));
  // priority_id: u(6)
  RETURN_EMPTY_ON_FAIL(buffer->ReadBits(&svc_extension.priority_id, 6));
  // no_inter_layer_pred_flag: u(1)
  RETURN_EMPTY_ON_FAIL(
      buffer->ReadBits(&svc_extension.no_inter_layer_pred_flag, 1));
  // dependency_id: u(3)
  RETURN_EMPTY_ON_FAIL(buffer->ReadBits(&svc_extension.dependency_id, 3));
  // quality_id: u(4)
  RETURN_EMPTY_ON_FAIL(buffer->ReadBits(&svc_extension.quality_id, 4));
  // temporal_id: u(3)
  RETURN_EMPTY_ON_FAIL(buffer->ReadBits(&svc_extension.temporal_id, 3));
  // use_ref_base_pic_flag: u(1)
  RETURN_EMPTY_ON_FAIL(
      buffer->ReadBits(&svc_extension.use_ref_base_pic_flag, 1));
  // discardable_flag: u(1)
  RETURN_EMPTY_ON_FAIL(buffer->ReadBits(&svc_extension.discardable_flag, 1));
  // output_flag: u(1)
  RETURN_EMPTY_ON_FAIL(buffer->ReadBits(&svc_extension.output_flag, 1));

  return OptionalPrefix(svc_extension);
}

}  // namespace webrtc

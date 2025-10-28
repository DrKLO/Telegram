/*
 *  Copyright (c) 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_DEPENDENCY_DESCRIPTOR_ENCODER_DECODER_H_
#define LOGGING_RTC_EVENT_LOG_DEPENDENCY_DESCRIPTOR_ENCODER_DECODER_H_

#include <vector>

#include "absl/types/optional.h"
#include "logging/rtc_event_log/events/rtc_event_log_parse_status.h"
#include "logging/rtc_event_log/rtc_event_log2_proto_include.h"
#include "rtc_base/bitstream_reader.h"
#include "rtc_base/checks.h"

namespace webrtc {

class RtcEventLogDependencyDescriptorEncoderDecoder {
 public:
  static absl::optional<rtclog2::DependencyDescriptorsWireInfo> Encode(
      const std::vector<rtc::ArrayView<const uint8_t>>& raw_dd_data);
  static RtcEventLogParseStatusOr<std::vector<std::vector<uint8_t>>> Decode(
      const rtclog2::DependencyDescriptorsWireInfo& dd_wire_info,
      size_t num_packets);
};

}  // namespace webrtc

#endif  //  LOGGING_RTC_EVENT_LOG_DEPENDENCY_DESCRIPTOR_ENCODER_DECODER_H_

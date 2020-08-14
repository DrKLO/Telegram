/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/h264_sprop_parameter_sets.h"

#include <stddef.h>
#include <stdint.h>

#include <string>
#include <vector>

#include "rtc_base/logging.h"
#include "rtc_base/third_party/base64/base64.h"

namespace {

bool DecodeAndConvert(const std::string& base64, std::vector<uint8_t>* binary) {
  return rtc::Base64::DecodeFromArray(base64.data(), base64.size(),
                                      rtc::Base64::DO_STRICT, binary, nullptr);
}
}  // namespace

namespace webrtc {

bool H264SpropParameterSets::DecodeSprop(const std::string& sprop) {
  size_t separator_pos = sprop.find(',');
  RTC_LOG(LS_INFO) << "Parsing sprop \"" << sprop << "\"";
  if ((separator_pos <= 0) || (separator_pos >= sprop.length() - 1)) {
    RTC_LOG(LS_WARNING) << "Invalid seperator position " << separator_pos
                        << " *" << sprop << "*";
    return false;
  }
  std::string sps_str = sprop.substr(0, separator_pos);
  std::string pps_str = sprop.substr(separator_pos + 1, std::string::npos);
  if (!DecodeAndConvert(sps_str, &sps_)) {
    RTC_LOG(LS_WARNING) << "Failed to decode sprop/sps *" << sprop << "*";
    return false;
  }
  if (!DecodeAndConvert(pps_str, &pps_)) {
    RTC_LOG(LS_WARNING) << "Failed to decode sprop/pps *" << sprop << "*";
    return false;
  }
  return true;
}

}  // namespace webrtc

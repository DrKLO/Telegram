/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/units/frequency.h"

#include "rtc_base/strings/string_builder.h"

namespace webrtc {
std::string ToString(Frequency value) {
  char buf[64];
  rtc::SimpleStringBuilder sb(buf);
  if (value.IsPlusInfinity()) {
    sb << "+inf Hz";
  } else if (value.IsMinusInfinity()) {
    sb << "-inf Hz";
  } else if (value.millihertz<int64_t>() % 1000 != 0) {
    sb.AppendFormat("%.3f Hz", value.hertz<double>());
  } else {
    sb << value.hertz<int64_t>() << " Hz";
  }
  return sb.str();
}
}  // namespace webrtc

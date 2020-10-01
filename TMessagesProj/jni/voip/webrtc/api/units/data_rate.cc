/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/units/data_rate.h"

#include "api/array_view.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {

std::string ToString(DataRate value) {
  char buf[64];
  rtc::SimpleStringBuilder sb(buf);
  if (value.IsPlusInfinity()) {
    sb << "+inf bps";
  } else if (value.IsMinusInfinity()) {
    sb << "-inf bps";
  } else {
    if (value.bps() == 0 || value.bps() % 1000 != 0) {
      sb << value.bps() << " bps";
    } else {
      sb << value.kbps() << " kbps";
    }
  }
  return sb.str();
}
}  // namespace webrtc

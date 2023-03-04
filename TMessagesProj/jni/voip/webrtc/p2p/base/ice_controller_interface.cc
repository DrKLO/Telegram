/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/ice_controller_interface.h"

#include <string>

#include "p2p/base/ice_switch_reason.h"

namespace cricket {

std::string IceRecheckEvent::ToString() const {
  std::string str = IceSwitchReasonToString(reason);
  if (recheck_delay_ms) {
    str += " (after delay: " + std::to_string(recheck_delay_ms) + ")";
  }
  return str;
}

}  // namespace cricket

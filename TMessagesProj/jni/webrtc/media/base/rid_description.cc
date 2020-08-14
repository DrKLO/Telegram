/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/base/rid_description.h"

namespace cricket {

RidDescription::RidDescription() = default;
RidDescription::RidDescription(const std::string& rid, RidDirection direction)
    : rid{rid}, direction{direction} {}
RidDescription::RidDescription(const RidDescription& other) = default;
RidDescription::~RidDescription() = default;
RidDescription& RidDescription::operator=(const RidDescription& other) =
    default;
bool RidDescription::operator==(const RidDescription& other) const {
  return rid == other.rid && direction == other.direction &&
         payload_types == other.payload_types &&
         restrictions == other.restrictions;
}

}  // namespace cricket

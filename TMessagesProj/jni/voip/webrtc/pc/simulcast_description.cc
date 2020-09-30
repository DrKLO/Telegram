/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/simulcast_description.h"

#include <utility>

#include "rtc_base/checks.h"

namespace cricket {

SimulcastLayer::SimulcastLayer(const std::string& rid, bool is_paused)
    : rid{rid}, is_paused{is_paused} {
  RTC_DCHECK(!rid.empty());
}

bool SimulcastLayer::operator==(const SimulcastLayer& other) const {
  return rid == other.rid && is_paused == other.is_paused;
}

void SimulcastLayerList::AddLayer(const SimulcastLayer& layer) {
  list_.push_back({layer});
}

void SimulcastLayerList::AddLayerWithAlternatives(
    const std::vector<SimulcastLayer>& rids) {
  RTC_DCHECK(!rids.empty());
  list_.push_back(rids);
}

const std::vector<SimulcastLayer>& SimulcastLayerList::operator[](
    size_t index) const {
  RTC_DCHECK_LT(index, list_.size());
  return list_[index];
}

bool SimulcastDescription::empty() const {
  return send_layers_.empty() && receive_layers_.empty();
}

std::vector<SimulcastLayer> SimulcastLayerList::GetAllLayers() const {
  std::vector<SimulcastLayer> result;
  for (auto groupIt = begin(); groupIt != end(); groupIt++) {
    for (auto it = groupIt->begin(); it != groupIt->end(); it++) {
      result.push_back(*it);
    }
  }

  return result;
}

}  // namespace cricket

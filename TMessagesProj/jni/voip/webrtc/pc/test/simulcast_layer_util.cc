/*
 *  Copyright 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/test/simulcast_layer_util.h"

#include "absl/algorithm/container.h"
#include "rtc_base/checks.h"

namespace webrtc {

std::vector<cricket::SimulcastLayer> CreateLayers(
    const std::vector<std::string>& rids,
    const std::vector<bool>& active) {
  RTC_DCHECK_EQ(rids.size(), active.size());
  std::vector<cricket::SimulcastLayer> result;
  absl::c_transform(rids, active, std::back_inserter(result),
                    [](const std::string& rid, bool is_active) {
                      return cricket::SimulcastLayer(rid, !is_active);
                    });
  return result;
}

std::vector<cricket::SimulcastLayer> CreateLayers(
    const std::vector<std::string>& rids,
    bool active) {
  return CreateLayers(rids, std::vector<bool>(rids.size(), active));
}

RtpTransceiverInit CreateTransceiverInit(
    const std::vector<cricket::SimulcastLayer>& layers) {
  RtpTransceiverInit init;
  for (const cricket::SimulcastLayer& layer : layers) {
    RtpEncodingParameters encoding;
    encoding.rid = layer.rid;
    encoding.active = !layer.is_paused;
    init.send_encodings.push_back(encoding);
  }
  return init;
}

cricket::SimulcastDescription RemoveSimulcast(SessionDescriptionInterface* sd) {
  auto mcd = sd->description()->contents()[0].media_description();
  auto result = mcd->simulcast_description();
  mcd->set_simulcast_description(cricket::SimulcastDescription());
  return result;
}

}  // namespace webrtc

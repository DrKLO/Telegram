/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/create_call.h"

#include <stdio.h>

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/test/simulated_network.h"
#include "api/units/time_delta.h"
#include "call/call.h"
#include "call/degraded_call.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/field_trial_list.h"
#include "rtc_base/experiments/field_trial_parser.h"

namespace webrtc {
namespace {
using TimeScopedNetworkConfig = DegradedCall::TimeScopedNetworkConfig;

std::vector<TimeScopedNetworkConfig> GetNetworkConfigs(
    const FieldTrialsView& trials,
    bool send) {
  FieldTrialStructList<TimeScopedNetworkConfig> trials_list(
      {FieldTrialStructMember("queue_length_packets",
                              [](TimeScopedNetworkConfig* p) {
                                // FieldTrialParser does not natively support
                                // size_t type, so use this ugly cast as
                                // workaround.
                                return reinterpret_cast<unsigned*>(
                                    &p->queue_length_packets);
                              }),
       FieldTrialStructMember(
           "queue_delay_ms",
           [](TimeScopedNetworkConfig* p) { return &p->queue_delay_ms; }),
       FieldTrialStructMember("delay_standard_deviation_ms",
                              [](TimeScopedNetworkConfig* p) {
                                return &p->delay_standard_deviation_ms;
                              }),
       FieldTrialStructMember(
           "link_capacity_kbps",
           [](TimeScopedNetworkConfig* p) { return &p->link_capacity_kbps; }),
       FieldTrialStructMember(
           "loss_percent",
           [](TimeScopedNetworkConfig* p) { return &p->loss_percent; }),
       FieldTrialStructMember(
           "allow_reordering",
           [](TimeScopedNetworkConfig* p) { return &p->allow_reordering; }),
       FieldTrialStructMember("avg_burst_loss_length",
                              [](TimeScopedNetworkConfig* p) {
                                return &p->avg_burst_loss_length;
                              }),
       FieldTrialStructMember(
           "packet_overhead",
           [](TimeScopedNetworkConfig* p) { return &p->packet_overhead; }),
       FieldTrialStructMember(
           "duration",
           [](TimeScopedNetworkConfig* p) { return &p->duration; })},
      {});
  ParseFieldTrial({&trials_list},
                  trials.Lookup(send ? "WebRTC-FakeNetworkSendConfig"
                                     : "WebRTC-FakeNetworkReceiveConfig"));
  return trials_list.Get();
}

}  // namespace

std::unique_ptr<Call> CreateCall(const CallConfig& config) {
  std::vector<DegradedCall::TimeScopedNetworkConfig> send_degradation_configs =
      GetNetworkConfigs(config.env.field_trials(), /*send=*/true);
  std::vector<DegradedCall::TimeScopedNetworkConfig>
      receive_degradation_configs =
          GetNetworkConfigs(config.env.field_trials(), /*send=*/false);

  std::unique_ptr<Call> call = Call::Create(config);

  if (!send_degradation_configs.empty() ||
      !receive_degradation_configs.empty()) {
    return std::make_unique<DegradedCall>(
        std::move(call), send_degradation_configs, receive_degradation_configs);
  }

  return call;
}

}  // namespace webrtc

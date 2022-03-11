/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/call_factory.h"

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
#include "call/rtp_transport_config.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/field_trial_list.h"
#include "rtc_base/experiments/field_trial_parser.h"

namespace webrtc {
namespace {
using TimeScopedNetworkConfig = DegradedCall::TimeScopedNetworkConfig;

bool ParseConfigParam(const WebRtcKeyValueConfig& trials,
                      absl::string_view exp_name,
                      int* field) {
  std::string group = trials.Lookup(exp_name);
  if (group.empty())
    return false;

  return (sscanf(group.c_str(), "%d", field) == 1);
}

absl::optional<TimeScopedNetworkConfig> ParseDegradationConfig(
    const WebRtcKeyValueConfig& trials,
    bool send) {
  std::string exp_prefix = "WebRTCFakeNetwork";
  if (send) {
    exp_prefix += "Send";
  } else {
    exp_prefix += "Receive";
  }

  TimeScopedNetworkConfig config;
  bool configured = false;
  configured |=
      ParseConfigParam(trials, exp_prefix + "DelayMs", &config.queue_delay_ms);
  configured |= ParseConfigParam(trials, exp_prefix + "DelayStdDevMs",
                                 &config.delay_standard_deviation_ms);
  int queue_length = 0;
  if (ParseConfigParam(trials, exp_prefix + "QueueLength", &queue_length)) {
    RTC_CHECK_GE(queue_length, 0);
    config.queue_length_packets = queue_length;
    configured = true;
  }
  configured |= ParseConfigParam(trials, exp_prefix + "CapacityKbps",
                                 &config.link_capacity_kbps);
  configured |= ParseConfigParam(trials, exp_prefix + "LossPercent",
                                 &config.loss_percent);
  int allow_reordering = 0;
  if (ParseConfigParam(trials, exp_prefix + "AllowReordering",
                       &allow_reordering)) {
    config.allow_reordering = true;
    configured = true;
  }
  configured |= ParseConfigParam(trials, exp_prefix + "AvgBurstLossLength",
                                 &config.avg_burst_loss_length);
  return configured ? absl::optional<TimeScopedNetworkConfig>(config)
                    : absl::nullopt;
}

std::vector<TimeScopedNetworkConfig> GetNetworkConfigs(
    const WebRtcKeyValueConfig& trials,
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
       FieldTrialStructMember("codel_active_queue_management",
                              [](TimeScopedNetworkConfig* p) {
                                return &p->codel_active_queue_management;
                              }),
       FieldTrialStructMember(
           "duration",
           [](TimeScopedNetworkConfig* p) { return &p->duration; })},
      {});
  ParseFieldTrial({&trials_list},
                  trials.Lookup(send ? "WebRTC-FakeNetworkSendConfig"
                                     : "WebRTC-FakeNetworkReceiveConfig"));
  std::vector<TimeScopedNetworkConfig> configs = trials_list.Get();
  if (configs.empty()) {
    // Try legacy fallback trials.
    absl::optional<DegradedCall::TimeScopedNetworkConfig> fallback_config =
        ParseDegradationConfig(trials, send);
    if (fallback_config.has_value()) {
      configs.push_back(*fallback_config);
    }
  }
  return configs;
}

}  // namespace

CallFactory::CallFactory() {
  call_thread_.Detach();
}

Call* CallFactory::CreateCall(const Call::Config& config) {
  RTC_DCHECK_RUN_ON(&call_thread_);
  RTC_DCHECK(config.trials);

  std::vector<DegradedCall::TimeScopedNetworkConfig> send_degradation_configs =
      GetNetworkConfigs(*config.trials, /*send=*/true);
  std::vector<DegradedCall::TimeScopedNetworkConfig>
      receive_degradation_configs =
          GetNetworkConfigs(*config.trials, /*send=*/false);

  RtpTransportConfig transportConfig = config.ExtractTransportConfig();

  if (!send_degradation_configs.empty() ||
      !receive_degradation_configs.empty()) {
    return new DegradedCall(
        std::unique_ptr<Call>(Call::Create(
            config, Clock::GetRealTimeClock(),
            SharedModuleThread::Create(
                ProcessThread::Create("ModuleProcessThread"), nullptr),
            config.rtp_transport_controller_send_factory->Create(
                transportConfig, Clock::GetRealTimeClock(),
                ProcessThread::Create("PacerThread")))),
        send_degradation_configs, receive_degradation_configs);
  }

  if (!module_thread_) {
    module_thread_ = SharedModuleThread::Create(
        ProcessThread::Create("SharedModThread"), [this]() {
          RTC_DCHECK_RUN_ON(&call_thread_);
          module_thread_ = nullptr;
        });
  }

  return Call::Create(config, Clock::GetRealTimeClock(), module_thread_,
                      config.rtp_transport_controller_send_factory->Create(
                          transportConfig, Clock::GetRealTimeClock(),
                          ProcessThread::Create("PacerThread")));
}

std::unique_ptr<CallFactoryInterface> CreateCallFactory() {
  return std::unique_ptr<CallFactoryInterface>(new CallFactory());
}

}  // namespace webrtc

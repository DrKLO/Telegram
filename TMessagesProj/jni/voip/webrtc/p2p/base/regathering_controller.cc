/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/regathering_controller.h"

#include "api/task_queue/pending_task_safety_flag.h"
#include "api/units/time_delta.h"

namespace webrtc {

BasicRegatheringController::BasicRegatheringController(
    const Config& config,
    cricket::IceTransportInternal* ice_transport,
    rtc::Thread* thread)
    : config_(config), ice_transport_(ice_transport), thread_(thread) {
  RTC_DCHECK(thread_);
  RTC_DCHECK_RUN_ON(thread_);
  RTC_DCHECK(ice_transport_);
  ice_transport_->SignalStateChanged.connect(
      this, &BasicRegatheringController::OnIceTransportStateChanged);
  ice_transport->SignalWritableState.connect(
      this, &BasicRegatheringController::OnIceTransportWritableState);
  ice_transport->SignalReceivingState.connect(
      this, &BasicRegatheringController::OnIceTransportReceivingState);
  ice_transport->SignalNetworkRouteChanged.connect(
      this, &BasicRegatheringController::OnIceTransportNetworkRouteChanged);
}

BasicRegatheringController::~BasicRegatheringController() {
  RTC_DCHECK_RUN_ON(thread_);
}

void BasicRegatheringController::Start() {
  RTC_DCHECK_RUN_ON(thread_);
  ScheduleRecurringRegatheringOnFailedNetworks();
}

void BasicRegatheringController::SetConfig(const Config& config) {
  RTC_DCHECK_RUN_ON(thread_);
  bool need_reschedule_on_failed_networks =
      pending_regathering_ && (config_.regather_on_failed_networks_interval !=
                               config.regather_on_failed_networks_interval);
  config_ = config;
  if (need_reschedule_on_failed_networks) {
    ScheduleRecurringRegatheringOnFailedNetworks();
  }
}

void BasicRegatheringController::
    ScheduleRecurringRegatheringOnFailedNetworks() {
  RTC_DCHECK_RUN_ON(thread_);
  RTC_DCHECK(config_.regather_on_failed_networks_interval >= 0);
  // Reset pending_regathering_ to cancel any potentially pending tasks.
  pending_regathering_.reset(new ScopedTaskSafety());

  thread_->PostDelayedTask(
      SafeTask(pending_regathering_->flag(),
               [this]() {
                 RTC_DCHECK_RUN_ON(thread_);
                 // Only regather when the current session is in the CLEARED
                 // state (i.e., not running or stopped). It is only
                 // possible to enter this state when we gather continually,
                 // so there is an implicit check on continual gathering
                 // here.
                 if (allocator_session_ && allocator_session_->IsCleared()) {
                   allocator_session_->RegatherOnFailedNetworks();
                 }
                 ScheduleRecurringRegatheringOnFailedNetworks();
               }),
      TimeDelta::Millis(config_.regather_on_failed_networks_interval));
}

}  // namespace webrtc

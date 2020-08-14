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

namespace webrtc {

BasicRegatheringController::BasicRegatheringController(
    const Config& config,
    cricket::IceTransportInternal* ice_transport,
    rtc::Thread* thread)
    : config_(config), ice_transport_(ice_transport), thread_(thread) {
  RTC_DCHECK(ice_transport_);
  RTC_DCHECK(thread_);
  ice_transport_->SignalStateChanged.connect(
      this, &BasicRegatheringController::OnIceTransportStateChanged);
  ice_transport->SignalWritableState.connect(
      this, &BasicRegatheringController::OnIceTransportWritableState);
  ice_transport->SignalReceivingState.connect(
      this, &BasicRegatheringController::OnIceTransportReceivingState);
  ice_transport->SignalNetworkRouteChanged.connect(
      this, &BasicRegatheringController::OnIceTransportNetworkRouteChanged);
}

BasicRegatheringController::~BasicRegatheringController() = default;

void BasicRegatheringController::Start() {
  ScheduleRecurringRegatheringOnFailedNetworks();
}

void BasicRegatheringController::SetConfig(const Config& config) {
  bool need_cancel_and_reschedule_on_failed_networks =
      has_recurring_schedule_on_failed_networks_ &&
      (config_.regather_on_failed_networks_interval !=
       config.regather_on_failed_networks_interval);
  config_ = config;
  if (need_cancel_and_reschedule_on_failed_networks) {
    CancelScheduledRecurringRegatheringOnFailedNetworks();
    ScheduleRecurringRegatheringOnFailedNetworks();
  }
}

void BasicRegatheringController::
    ScheduleRecurringRegatheringOnFailedNetworks() {
  RTC_DCHECK(config_.regather_on_failed_networks_interval >= 0);
  CancelScheduledRecurringRegatheringOnFailedNetworks();
  has_recurring_schedule_on_failed_networks_ = true;
  invoker_for_failed_networks_.AsyncInvokeDelayed<void>(
      RTC_FROM_HERE, thread_,
      rtc::Bind(
          &BasicRegatheringController::RegatherOnFailedNetworksIfDoneGathering,
          this),
      config_.regather_on_failed_networks_interval);
}

void BasicRegatheringController::RegatherOnFailedNetworksIfDoneGathering() {
  // Only regather when the current session is in the CLEARED state (i.e., not
  // running or stopped). It is only possible to enter this state when we gather
  // continually, so there is an implicit check on continual gathering here.
  if (allocator_session_ && allocator_session_->IsCleared()) {
    allocator_session_->RegatherOnFailedNetworks();
  }
  ScheduleRecurringRegatheringOnFailedNetworks();
}

void BasicRegatheringController::
    CancelScheduledRecurringRegatheringOnFailedNetworks() {
  invoker_for_failed_networks_.Clear();
  has_recurring_schedule_on_failed_networks_ = false;
}

}  // namespace webrtc

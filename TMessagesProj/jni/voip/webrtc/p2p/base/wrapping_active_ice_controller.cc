/*
 *  Copyright 2022 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/wrapping_active_ice_controller.h"

#include <memory>
#include <utility>
#include <vector>

#include "api/sequence_checker.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "api/units/time_delta.h"
#include "p2p/base/basic_ice_controller.h"
#include "p2p/base/connection.h"
#include "p2p/base/ice_agent_interface.h"
#include "p2p/base/ice_controller_interface.h"
#include "p2p/base/ice_switch_reason.h"
#include "p2p/base/ice_transport_internal.h"
#include "p2p/base/transport_description.h"
#include "rtc_base/logging.h"
#include "rtc_base/thread.h"
#include "rtc_base/time_utils.h"

namespace {
using ::webrtc::SafeTask;
using ::webrtc::TimeDelta;
}  // unnamed namespace

namespace cricket {

WrappingActiveIceController::WrappingActiveIceController(
    IceAgentInterface* ice_agent,
    std::unique_ptr<IceControllerInterface> wrapped)
    : network_thread_(rtc::Thread::Current()),
      wrapped_(std::move(wrapped)),
      agent_(*ice_agent) {
  RTC_DCHECK(ice_agent != nullptr);
}

WrappingActiveIceController::WrappingActiveIceController(
    IceAgentInterface* ice_agent,
    IceControllerFactoryInterface* wrapped_factory,
    const IceControllerFactoryArgs& wrapped_factory_args)
    : network_thread_(rtc::Thread::Current()), agent_(*ice_agent) {
  RTC_DCHECK(ice_agent != nullptr);
  if (wrapped_factory) {
    wrapped_ = wrapped_factory->Create(wrapped_factory_args);
  } else {
    wrapped_ = std::make_unique<BasicIceController>(wrapped_factory_args);
  }
}

WrappingActiveIceController::~WrappingActiveIceController() {}

void WrappingActiveIceController::SetIceConfig(const IceConfig& config) {
  RTC_DCHECK_RUN_ON(network_thread_);
  wrapped_->SetIceConfig(config);
}

bool WrappingActiveIceController::GetUseCandidateAttribute(
    const Connection* connection,
    NominationMode mode,
    IceMode remote_ice_mode) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  return wrapped_->GetUseCandidateAttr(connection, mode, remote_ice_mode);
}

void WrappingActiveIceController::OnConnectionAdded(
    const Connection* connection) {
  RTC_DCHECK_RUN_ON(network_thread_);
  wrapped_->AddConnection(connection);
}

void WrappingActiveIceController::OnConnectionPinged(
    const Connection* connection) {
  RTC_DCHECK_RUN_ON(network_thread_);
  wrapped_->MarkConnectionPinged(connection);
}

void WrappingActiveIceController::OnConnectionUpdated(
    const Connection* connection) {
  RTC_LOG(LS_VERBOSE) << "Connection report for " << connection->ToString();
  // Do nothing. Native ICE controllers have direct access to Connection, so no
  // need to update connection state separately.
}

void WrappingActiveIceController::OnConnectionSwitched(
    const Connection* connection) {
  RTC_DCHECK_RUN_ON(network_thread_);
  selected_connection_ = connection;
  wrapped_->SetSelectedConnection(connection);
}

void WrappingActiveIceController::OnConnectionDestroyed(
    const Connection* connection) {
  RTC_DCHECK_RUN_ON(network_thread_);
  wrapped_->OnConnectionDestroyed(connection);
}

void WrappingActiveIceController::MaybeStartPinging() {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (started_pinging_) {
    return;
  }

  if (wrapped_->HasPingableConnection()) {
    network_thread_->PostTask(
        SafeTask(task_safety_.flag(), [this]() { SelectAndPingConnection(); }));
    agent_.OnStartedPinging();
    started_pinging_ = true;
  }
}

void WrappingActiveIceController::SelectAndPingConnection() {
  RTC_DCHECK_RUN_ON(network_thread_);
  agent_.UpdateConnectionStates();

  IceControllerInterface::PingResult result =
      wrapped_->SelectConnectionToPing(agent_.GetLastPingSentMs());
  HandlePingResult(result);
}

void WrappingActiveIceController::HandlePingResult(
    IceControllerInterface::PingResult result) {
  RTC_DCHECK_RUN_ON(network_thread_);

  if (result.connection.has_value()) {
    agent_.SendPingRequest(result.connection.value());
  }

  network_thread_->PostDelayedTask(
      SafeTask(task_safety_.flag(), [this]() { SelectAndPingConnection(); }),
      TimeDelta::Millis(result.recheck_delay_ms));
}

void WrappingActiveIceController::OnSortAndSwitchRequest(
    IceSwitchReason reason) {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (!sort_pending_) {
    network_thread_->PostTask(SafeTask(task_safety_.flag(), [this, reason]() {
      SortAndSwitchToBestConnection(reason);
    }));
    sort_pending_ = true;
  }
}

void WrappingActiveIceController::OnImmediateSortAndSwitchRequest(
    IceSwitchReason reason) {
  RTC_DCHECK_RUN_ON(network_thread_);
  SortAndSwitchToBestConnection(reason);
}

void WrappingActiveIceController::SortAndSwitchToBestConnection(
    IceSwitchReason reason) {
  RTC_DCHECK_RUN_ON(network_thread_);

  // Make sure the connection states are up-to-date since this affects how they
  // will be sorted.
  agent_.UpdateConnectionStates();

  // Any changes after this point will require a re-sort.
  sort_pending_ = false;

  IceControllerInterface::SwitchResult result =
      wrapped_->SortAndSwitchConnection(reason);
  HandleSwitchResult(reason, result);
  UpdateStateOnConnectionsResorted();
}

bool WrappingActiveIceController::OnImmediateSwitchRequest(
    IceSwitchReason reason,
    const Connection* selected) {
  RTC_DCHECK_RUN_ON(network_thread_);
  IceControllerInterface::SwitchResult result =
      wrapped_->ShouldSwitchConnection(reason, selected);
  HandleSwitchResult(reason, result);
  return result.connection.has_value();
}

void WrappingActiveIceController::HandleSwitchResult(
    IceSwitchReason reason_for_switch,
    IceControllerInterface::SwitchResult result) {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (result.connection.has_value()) {
    RTC_LOG(LS_INFO) << "Switching selected connection due to: "
                     << IceSwitchReasonToString(reason_for_switch);
    agent_.SwitchSelectedConnection(result.connection.value(),
                                    reason_for_switch);
  }

  if (result.recheck_event.has_value()) {
    // If we do not switch to the connection because it missed the receiving
    // threshold, the new connection is in a better receiving state than the
    // currently selected connection. So we need to re-check whether it needs
    // to be switched at a later time.
    network_thread_->PostDelayedTask(
        SafeTask(task_safety_.flag(),
                 [this, recheck_reason = result.recheck_event->reason]() {
                   SortAndSwitchToBestConnection(recheck_reason);
                 }),
        TimeDelta::Millis(result.recheck_event->recheck_delay_ms));
  }

  agent_.ForgetLearnedStateForConnections(
      result.connections_to_forget_state_on);
}

void WrappingActiveIceController::UpdateStateOnConnectionsResorted() {
  RTC_DCHECK_RUN_ON(network_thread_);
  PruneConnections();

  // Update the internal state of the ICE agentl.
  agent_.UpdateState();

  // Also possibly start pinging.
  // We could start pinging if:
  // * The first connection was created.
  // * ICE credentials were provided.
  // * A TCP connection became connected.
  MaybeStartPinging();
}

void WrappingActiveIceController::PruneConnections() {
  RTC_DCHECK_RUN_ON(network_thread_);

  // The controlled side can prune only if the selected connection has been
  // nominated because otherwise it may prune the connection that will be
  // selected by the controlling side.
  // TODO(honghaiz): This is not enough to prevent a connection from being
  // pruned too early because with aggressive nomination, the controlling side
  // will nominate every connection until it becomes writable.
  if (agent_.GetIceRole() == ICEROLE_CONTROLLING ||
      (selected_connection_ && selected_connection_->nominated())) {
    std::vector<const Connection*> connections_to_prune =
        wrapped_->PruneConnections();
    agent_.PruneConnections(connections_to_prune);
  }
}

// Only for unit tests
const Connection* WrappingActiveIceController::FindNextPingableConnection() {
  RTC_DCHECK_RUN_ON(network_thread_);
  return wrapped_->FindNextPingableConnection();
}

}  // namespace cricket

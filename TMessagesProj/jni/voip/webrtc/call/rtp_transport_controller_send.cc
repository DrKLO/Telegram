/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "call/rtp_transport_controller_send.h"

#include <memory>
#include <utility>
#include <vector>

#include "absl/strings/match.h"
#include "absl/types/optional.h"
#include "api/transport/goog_cc_factory.h"
#include "api/transport/network_types.h"
#include "api/units/data_rate.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "call/rtp_video_sender.h"
#include "logging/rtc_event_log/events/rtc_event_remote_estimate.h"
#include "logging/rtc_event_log/events/rtc_event_route_change.h"
#include "modules/rtp_rtcp/source/rtcp_packet/transport_feedback.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/rate_limiter.h"

namespace webrtc {
namespace {
static const int64_t kRetransmitWindowSizeMs = 500;
static const size_t kMaxOverheadBytes = 500;

constexpr TimeDelta kPacerQueueUpdateInterval = TimeDelta::Millis(25);

TargetRateConstraints ConvertConstraints(int min_bitrate_bps,
                                         int max_bitrate_bps,
                                         int start_bitrate_bps,
                                         Clock* clock) {
  TargetRateConstraints msg;
  msg.at_time = Timestamp::Millis(clock->TimeInMilliseconds());
  msg.min_data_rate = min_bitrate_bps >= 0
                          ? DataRate::BitsPerSec(min_bitrate_bps)
                          : DataRate::Zero();
  msg.max_data_rate = max_bitrate_bps > 0
                          ? DataRate::BitsPerSec(max_bitrate_bps)
                          : DataRate::Infinity();
  if (start_bitrate_bps > 0)
    msg.starting_rate = DataRate::BitsPerSec(start_bitrate_bps);
  return msg;
}

TargetRateConstraints ConvertConstraints(const BitrateConstraints& contraints,
                                         Clock* clock) {
  return ConvertConstraints(contraints.min_bitrate_bps,
                            contraints.max_bitrate_bps,
                            contraints.start_bitrate_bps, clock);
}

bool IsEnabled(const WebRtcKeyValueConfig* trials, absl::string_view key) {
  RTC_DCHECK(trials != nullptr);
  return absl::StartsWith(trials->Lookup(key), "Enabled");
}

bool IsDisabled(const WebRtcKeyValueConfig* trials, absl::string_view key) {
  RTC_DCHECK(trials != nullptr);
  return absl::StartsWith(trials->Lookup(key), "Disabled");
}

bool IsRelayed(const rtc::NetworkRoute& route) {
  return route.local.uses_turn() || route.remote.uses_turn();
}

}  // namespace

RtpTransportControllerSend::PacerSettings::PacerSettings(
    const WebRtcKeyValueConfig* trials)
    : tq_disabled("Disabled"),
      holdback_window("holdback_window", PacingController::kMinSleepTime),
      holdback_packets("holdback_packets", -1) {
  ParseFieldTrial({&tq_disabled, &holdback_window, &holdback_packets},
                  trials->Lookup("WebRTC-TaskQueuePacer"));
}

RtpTransportControllerSend::RtpTransportControllerSend(
    Clock* clock,
    webrtc::RtcEventLog* event_log,
    NetworkStatePredictorFactoryInterface* predictor_factory,
    NetworkControllerFactoryInterface* controller_factory,
    const BitrateConstraints& bitrate_config,
    std::unique_ptr<ProcessThread> process_thread,
    TaskQueueFactory* task_queue_factory,
    const WebRtcKeyValueConfig* trials)
    : clock_(clock),
      event_log_(event_log),
      bitrate_configurator_(bitrate_config),
      pacer_started_(false),
      process_thread_(std::move(process_thread)),
      pacer_settings_(trials),
      process_thread_pacer_(pacer_settings_.use_task_queue_pacer()
                                ? nullptr
                                : new PacedSender(clock,
                                                  &packet_router_,
                                                  event_log,
                                                  trials,
                                                  process_thread_.get())),
      task_queue_pacer_(
          pacer_settings_.use_task_queue_pacer()
              ? new TaskQueuePacedSender(clock,
                                         &packet_router_,
                                         event_log,
                                         trials,
                                         task_queue_factory,
                                         pacer_settings_.holdback_window.Get(),
                                         pacer_settings_.holdback_packets.Get())
              : nullptr),
      observer_(nullptr),
      controller_factory_override_(controller_factory),
      controller_factory_fallback_(
          std::make_unique<GoogCcNetworkControllerFactory>(predictor_factory)),
      process_interval_(controller_factory_fallback_->GetProcessInterval()),
      last_report_block_time_(Timestamp::Millis(clock_->TimeInMilliseconds())),
      reset_feedback_on_route_change_(
          !IsEnabled(trials, "WebRTC-Bwe-NoFeedbackReset")),
      send_side_bwe_with_overhead_(
          !IsDisabled(trials, "WebRTC-SendSideBwe-WithOverhead")),
      add_pacing_to_cwin_(
          IsEnabled(trials, "WebRTC-AddPacingToCongestionWindowPushback")),
      relay_bandwidth_cap_("relay_cap", DataRate::PlusInfinity()),
      transport_overhead_bytes_per_packet_(0),
      network_available_(false),
      retransmission_rate_limiter_(clock, kRetransmitWindowSizeMs),
      task_queue_(task_queue_factory->CreateTaskQueue(
          "rtp_send_controller",
          TaskQueueFactory::Priority::NORMAL)) {
  ParseFieldTrial({&relay_bandwidth_cap_},
                  trials->Lookup("WebRTC-Bwe-NetworkRouteConstraints"));
  initial_config_.constraints = ConvertConstraints(bitrate_config, clock_);
  initial_config_.event_log = event_log;
  initial_config_.key_value_config = trials;
  RTC_DCHECK(bitrate_config.start_bitrate_bps > 0);

  pacer()->SetPacingRates(
      DataRate::BitsPerSec(bitrate_config.start_bitrate_bps), DataRate::Zero());

  if (absl::StartsWith(trials->Lookup("WebRTC-LazyPacerStart"), "Disabled")) {
    EnsureStarted();
  }
}

RtpTransportControllerSend::~RtpTransportControllerSend() {
  RTC_DCHECK(video_rtp_senders_.empty());
  process_thread_->Stop();
}

RtpVideoSenderInterface* RtpTransportControllerSend::CreateRtpVideoSender(
    const std::map<uint32_t, RtpState>& suspended_ssrcs,
    const std::map<uint32_t, RtpPayloadState>& states,
    const RtpConfig& rtp_config,
    int rtcp_report_interval_ms,
    Transport* send_transport,
    const RtpSenderObservers& observers,
    RtcEventLog* event_log,
    std::unique_ptr<FecController> fec_controller,
    const RtpSenderFrameEncryptionConfig& frame_encryption_config,
    rtc::scoped_refptr<FrameTransformerInterface> frame_transformer) {
  RTC_DCHECK_RUN_ON(&main_thread_);
  video_rtp_senders_.push_back(std::make_unique<RtpVideoSender>(
      clock_, suspended_ssrcs, states, rtp_config, rtcp_report_interval_ms,
      send_transport, observers,
      // TODO(holmer): Remove this circular dependency by injecting
      // the parts of RtpTransportControllerSendInterface that are really used.
      this, event_log, &retransmission_rate_limiter_, std::move(fec_controller),
      frame_encryption_config.frame_encryptor,
      frame_encryption_config.crypto_options, std::move(frame_transformer)));
  return video_rtp_senders_.back().get();
}

void RtpTransportControllerSend::DestroyRtpVideoSender(
    RtpVideoSenderInterface* rtp_video_sender) {
  RTC_DCHECK_RUN_ON(&main_thread_);
  std::vector<std::unique_ptr<RtpVideoSenderInterface>>::iterator it =
      video_rtp_senders_.end();
  for (it = video_rtp_senders_.begin(); it != video_rtp_senders_.end(); ++it) {
    if (it->get() == rtp_video_sender) {
      break;
    }
  }
  RTC_DCHECK(it != video_rtp_senders_.end());
  video_rtp_senders_.erase(it);
}

void RtpTransportControllerSend::UpdateControlState() {
  absl::optional<TargetTransferRate> update = control_handler_->GetUpdate();
  if (!update)
    return;
  retransmission_rate_limiter_.SetMaxRate(update->target_rate.bps());
  // We won't create control_handler_ until we have an observers.
  RTC_DCHECK(observer_ != nullptr);
  observer_->OnTargetTransferRate(*update);
}

RtpPacketPacer* RtpTransportControllerSend::pacer() {
  if (pacer_settings_.use_task_queue_pacer()) {
    return task_queue_pacer_.get();
  }
  return process_thread_pacer_.get();
}

const RtpPacketPacer* RtpTransportControllerSend::pacer() const {
  if (pacer_settings_.use_task_queue_pacer()) {
    return task_queue_pacer_.get();
  }
  return process_thread_pacer_.get();
}

rtc::TaskQueue* RtpTransportControllerSend::GetWorkerQueue() {
  return &task_queue_;
}

PacketRouter* RtpTransportControllerSend::packet_router() {
  return &packet_router_;
}

NetworkStateEstimateObserver*
RtpTransportControllerSend::network_state_estimate_observer() {
  return this;
}

TransportFeedbackObserver*
RtpTransportControllerSend::transport_feedback_observer() {
  return this;
}

RtpPacketSender* RtpTransportControllerSend::packet_sender() {
  if (pacer_settings_.use_task_queue_pacer()) {
    return task_queue_pacer_.get();
  }
  return process_thread_pacer_.get();
}

void RtpTransportControllerSend::SetAllocatedSendBitrateLimits(
    BitrateAllocationLimits limits) {
  RTC_DCHECK_RUN_ON(&task_queue_);
  streams_config_.min_total_allocated_bitrate = limits.min_allocatable_rate;
  streams_config_.max_padding_rate = limits.max_padding_rate;
  streams_config_.max_total_allocated_bitrate = limits.max_allocatable_rate;
  UpdateStreamsConfig();
}
void RtpTransportControllerSend::SetPacingFactor(float pacing_factor) {
  RTC_DCHECK_RUN_ON(&task_queue_);
  streams_config_.pacing_factor = pacing_factor;
  UpdateStreamsConfig();
}
void RtpTransportControllerSend::SetQueueTimeLimit(int limit_ms) {
  pacer()->SetQueueTimeLimit(TimeDelta::Millis(limit_ms));
}
StreamFeedbackProvider*
RtpTransportControllerSend::GetStreamFeedbackProvider() {
  return &feedback_demuxer_;
}

void RtpTransportControllerSend::RegisterTargetTransferRateObserver(
    TargetTransferRateObserver* observer) {
  task_queue_.PostTask([this, observer] {
    RTC_DCHECK_RUN_ON(&task_queue_);
    RTC_DCHECK(observer_ == nullptr);
    observer_ = observer;
    observer_->OnStartRateUpdate(*initial_config_.constraints.starting_rate);
    MaybeCreateControllers();
  });
}

bool RtpTransportControllerSend::IsRelevantRouteChange(
    const rtc::NetworkRoute& old_route,
    const rtc::NetworkRoute& new_route) const {
  // TODO(bugs.webrtc.org/11438): Experiment with using more information/
  // other conditions.
  bool connected_changed = old_route.connected != new_route.connected;
  bool route_ids_changed =
      old_route.local.network_id() != new_route.local.network_id() ||
      old_route.remote.network_id() != new_route.remote.network_id();
  if (relay_bandwidth_cap_->IsFinite()) {
    bool relaying_changed = IsRelayed(old_route) != IsRelayed(new_route);
    return connected_changed || route_ids_changed || relaying_changed;
  } else {
    return connected_changed || route_ids_changed;
  }
}

void RtpTransportControllerSend::OnNetworkRouteChanged(
    const std::string& transport_name,
    const rtc::NetworkRoute& network_route) {
  // Check if the network route is connected.

  if (!network_route.connected) {
    // TODO(honghaiz): Perhaps handle this in SignalChannelNetworkState and
    // consider merging these two methods.
    return;
  }

  absl::optional<BitrateConstraints> relay_constraint_update =
      ApplyOrLiftRelayCap(IsRelayed(network_route));

  // Check whether the network route has changed on each transport.
  auto result =
      network_routes_.insert(std::make_pair(transport_name, network_route));
  auto kv = result.first;
  bool inserted = result.second;
  if (inserted || !(kv->second == network_route)) {
    RTC_LOG(LS_INFO) << "Network route changed on transport " << transport_name
                     << ": new_route = " << network_route.DebugString();
    if (!inserted) {
      RTC_LOG(LS_INFO) << "old_route = " << kv->second.DebugString();
    }
  }

  if (inserted) {
    if (relay_constraint_update.has_value()) {
      UpdateBitrateConstraints(*relay_constraint_update);
    }
    task_queue_.PostTask([this, network_route] {
      RTC_DCHECK_RUN_ON(&task_queue_);
      transport_overhead_bytes_per_packet_ = network_route.packet_overhead;
    });
    // No need to reset BWE if this is the first time the network connects.
    return;
  }

  const rtc::NetworkRoute old_route = kv->second;
  kv->second = network_route;

  // Check if enough conditions of the new/old route has changed
  // to trigger resetting of bitrates (and a probe).
  if (IsRelevantRouteChange(old_route, network_route)) {
    BitrateConstraints bitrate_config = bitrate_configurator_.GetConfig();
    RTC_LOG(LS_INFO) << "Reset bitrates to min: "
                     << bitrate_config.min_bitrate_bps
                     << " bps, start: " << bitrate_config.start_bitrate_bps
                     << " bps,  max: " << bitrate_config.max_bitrate_bps
                     << " bps.";
    RTC_DCHECK_GT(bitrate_config.start_bitrate_bps, 0);

    if (event_log_) {
      event_log_->Log(std::make_unique<RtcEventRouteChange>(
          network_route.connected, network_route.packet_overhead));
    }
    NetworkRouteChange msg;
    msg.at_time = Timestamp::Millis(clock_->TimeInMilliseconds());
    msg.constraints = ConvertConstraints(bitrate_config, clock_);
    task_queue_.PostTask([this, msg, network_route] {
      RTC_DCHECK_RUN_ON(&task_queue_);
      transport_overhead_bytes_per_packet_ = network_route.packet_overhead;
      if (reset_feedback_on_route_change_) {
        transport_feedback_adapter_.SetNetworkRoute(network_route);
      }
      if (controller_) {
        PostUpdates(controller_->OnNetworkRouteChange(msg));
      } else {
        UpdateInitialConstraints(msg.constraints);
      }
      pacer()->UpdateOutstandingData(DataSize::Zero());
    });
  }
}
void RtpTransportControllerSend::OnNetworkAvailability(bool network_available) {
  RTC_DCHECK_RUN_ON(&main_thread_);
  RTC_LOG(LS_VERBOSE) << "SignalNetworkState "
                      << (network_available ? "Up" : "Down");
  NetworkAvailability msg;
  msg.at_time = Timestamp::Millis(clock_->TimeInMilliseconds());
  msg.network_available = network_available;
  task_queue_.PostTask([this, msg]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    if (network_available_ == msg.network_available)
      return;
    network_available_ = msg.network_available;
    if (network_available_) {
      pacer()->Resume();
    } else {
      pacer()->Pause();
    }
    pacer()->UpdateOutstandingData(DataSize::Zero());

    if (controller_) {
      control_handler_->SetNetworkAvailability(network_available_);
      PostUpdates(controller_->OnNetworkAvailability(msg));
      UpdateControlState();
    } else {
      MaybeCreateControllers();
    }
  });

  for (auto& rtp_sender : video_rtp_senders_) {
    rtp_sender->OnNetworkAvailability(network_available);
  }
}
RtcpBandwidthObserver* RtpTransportControllerSend::GetBandwidthObserver() {
  return this;
}
int64_t RtpTransportControllerSend::GetPacerQueuingDelayMs() const {
  return pacer()->OldestPacketWaitTime().ms();
}
absl::optional<Timestamp> RtpTransportControllerSend::GetFirstPacketTime()
    const {
  return pacer()->FirstSentPacketTime();
}
void RtpTransportControllerSend::EnablePeriodicAlrProbing(bool enable) {
  task_queue_.PostTask([this, enable]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    streams_config_.requests_alr_probing = enable;
    UpdateStreamsConfig();
  });
}
void RtpTransportControllerSend::OnSentPacket(
    const rtc::SentPacket& sent_packet) {
  task_queue_.PostTask([this, sent_packet]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    absl::optional<SentPacket> packet_msg =
        transport_feedback_adapter_.ProcessSentPacket(sent_packet);
    if (packet_msg) {
      // Only update outstanding data in pacer if:
      // 1. Packet feadback is used.
      // 2. The packet has not yet received an acknowledgement.
      // 3. It is not a retransmission of an earlier packet.
      pacer()->UpdateOutstandingData(
          transport_feedback_adapter_.GetOutstandingData());
      if (controller_)
        PostUpdates(controller_->OnSentPacket(*packet_msg));
    }
  });
}

void RtpTransportControllerSend::OnReceivedPacket(
    const ReceivedPacket& packet_msg) {
  task_queue_.PostTask([this, packet_msg]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    if (controller_)
      PostUpdates(controller_->OnReceivedPacket(packet_msg));
  });
}

void RtpTransportControllerSend::UpdateBitrateConstraints(
    const BitrateConstraints& updated) {
  TargetRateConstraints msg = ConvertConstraints(updated, clock_);
  task_queue_.PostTask([this, msg]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    if (controller_) {
      PostUpdates(controller_->OnTargetRateConstraints(msg));
    } else {
      UpdateInitialConstraints(msg);
    }
  });
}

void RtpTransportControllerSend::SetSdpBitrateParameters(
    const BitrateConstraints& constraints) {
  absl::optional<BitrateConstraints> updated =
      bitrate_configurator_.UpdateWithSdpParameters(constraints);
  if (updated.has_value()) {
    UpdateBitrateConstraints(*updated);
  } else {
    RTC_LOG(LS_VERBOSE)
        << "WebRTC.RtpTransportControllerSend.SetSdpBitrateParameters: "
           "nothing to update";
  }
}

void RtpTransportControllerSend::SetClientBitratePreferences(
    const BitrateSettings& preferences) {
  absl::optional<BitrateConstraints> updated =
      bitrate_configurator_.UpdateWithClientPreferences(preferences);
  if (updated.has_value()) {
    UpdateBitrateConstraints(*updated);
  } else {
    RTC_LOG(LS_VERBOSE)
        << "WebRTC.RtpTransportControllerSend.SetClientBitratePreferences: "
           "nothing to update";
  }
}

absl::optional<BitrateConstraints>
RtpTransportControllerSend::ApplyOrLiftRelayCap(bool is_relayed) {
  DataRate cap = is_relayed ? relay_bandwidth_cap_ : DataRate::PlusInfinity();
  return bitrate_configurator_.UpdateWithRelayCap(cap);
}

void RtpTransportControllerSend::OnTransportOverheadChanged(
    size_t transport_overhead_bytes_per_packet) {
  RTC_DCHECK_RUN_ON(&main_thread_);
  if (transport_overhead_bytes_per_packet >= kMaxOverheadBytes) {
    RTC_LOG(LS_ERROR) << "Transport overhead exceeds " << kMaxOverheadBytes;
    return;
  }

  pacer()->SetTransportOverhead(
      DataSize::Bytes(transport_overhead_bytes_per_packet));

  // TODO(holmer): Call AudioRtpSenders when they have been moved to
  // RtpTransportControllerSend.
  for (auto& rtp_video_sender : video_rtp_senders_) {
    rtp_video_sender->OnTransportOverheadChanged(
        transport_overhead_bytes_per_packet);
  }
}

void RtpTransportControllerSend::AccountForAudioPacketsInPacedSender(
    bool account_for_audio) {
  pacer()->SetAccountForAudioPackets(account_for_audio);
}

void RtpTransportControllerSend::IncludeOverheadInPacedSender() {
  pacer()->SetIncludeOverhead();
}

void RtpTransportControllerSend::EnsureStarted() {
  if (!pacer_started_) {
    pacer_started_ = true;
    if (pacer_settings_.use_task_queue_pacer()) {
      task_queue_pacer_->EnsureStarted();
    } else {
      process_thread_->Start();
    }
  }
}

void RtpTransportControllerSend::OnReceivedEstimatedBitrate(uint32_t bitrate) {
  RemoteBitrateReport msg;
  msg.receive_time = Timestamp::Millis(clock_->TimeInMilliseconds());
  msg.bandwidth = DataRate::BitsPerSec(bitrate);
  task_queue_.PostTask([this, msg]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    if (controller_)
      PostUpdates(controller_->OnRemoteBitrateReport(msg));
  });
}

void RtpTransportControllerSend::OnReceivedRtcpReceiverReport(
    const ReportBlockList& report_blocks,
    int64_t rtt_ms,
    int64_t now_ms) {
  task_queue_.PostTask([this, report_blocks, now_ms]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    OnReceivedRtcpReceiverReportBlocks(report_blocks, now_ms);
  });

  task_queue_.PostTask([this, now_ms, rtt_ms]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    RoundTripTimeUpdate report;
    report.receive_time = Timestamp::Millis(now_ms);
    report.round_trip_time = TimeDelta::Millis(rtt_ms);
    report.smoothed = false;
    if (controller_ && !report.round_trip_time.IsZero())
      PostUpdates(controller_->OnRoundTripTimeUpdate(report));
  });
}

void RtpTransportControllerSend::OnAddPacket(
    const RtpPacketSendInfo& packet_info) {
  Timestamp creation_time = Timestamp::Millis(clock_->TimeInMilliseconds());
  task_queue_.PostTask([this, packet_info, creation_time]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    feedback_demuxer_.AddPacket(packet_info);
    transport_feedback_adapter_.AddPacket(
        packet_info,
        send_side_bwe_with_overhead_ ? transport_overhead_bytes_per_packet_ : 0,
        creation_time);
  });
}

void RtpTransportControllerSend::OnTransportFeedback(
    const rtcp::TransportFeedback& feedback) {
  auto feedback_time = Timestamp::Millis(clock_->TimeInMilliseconds());
  task_queue_.PostTask([this, feedback, feedback_time]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    feedback_demuxer_.OnTransportFeedback(feedback);
    absl::optional<TransportPacketsFeedback> feedback_msg =
        transport_feedback_adapter_.ProcessTransportFeedback(feedback,
                                                             feedback_time);
    if (feedback_msg) {
      if (controller_)
        PostUpdates(controller_->OnTransportPacketsFeedback(*feedback_msg));

      // Only update outstanding data in pacer if any packet is first time
      // acked.
      pacer()->UpdateOutstandingData(
          transport_feedback_adapter_.GetOutstandingData());
    }
  });
}

void RtpTransportControllerSend::OnRemoteNetworkEstimate(
    NetworkStateEstimate estimate) {
  if (event_log_) {
    event_log_->Log(std::make_unique<RtcEventRemoteEstimate>(
        estimate.link_capacity_lower, estimate.link_capacity_upper));
  }
  estimate.update_time = Timestamp::Millis(clock_->TimeInMilliseconds());
  task_queue_.PostTask([this, estimate] {
    RTC_DCHECK_RUN_ON(&task_queue_);
    if (controller_)
      PostUpdates(controller_->OnNetworkStateEstimate(estimate));
  });
}

void RtpTransportControllerSend::MaybeCreateControllers() {
  RTC_DCHECK(!controller_);
  RTC_DCHECK(!control_handler_);

  if (!network_available_ || !observer_)
    return;
  control_handler_ = std::make_unique<CongestionControlHandler>();

  initial_config_.constraints.at_time =
      Timestamp::Millis(clock_->TimeInMilliseconds());
  initial_config_.stream_based_config = streams_config_;

  // TODO(srte): Use fallback controller if no feedback is available.
  if (controller_factory_override_) {
    RTC_LOG(LS_INFO) << "Creating overridden congestion controller";
    controller_ = controller_factory_override_->Create(initial_config_);
    process_interval_ = controller_factory_override_->GetProcessInterval();
  } else {
    RTC_LOG(LS_INFO) << "Creating fallback congestion controller";
    controller_ = controller_factory_fallback_->Create(initial_config_);
    process_interval_ = controller_factory_fallback_->GetProcessInterval();
  }
  UpdateControllerWithTimeInterval();
  StartProcessPeriodicTasks();
}

void RtpTransportControllerSend::UpdateInitialConstraints(
    TargetRateConstraints new_contraints) {
  if (!new_contraints.starting_rate)
    new_contraints.starting_rate = initial_config_.constraints.starting_rate;
  RTC_DCHECK(new_contraints.starting_rate);
  initial_config_.constraints = new_contraints;
}

void RtpTransportControllerSend::StartProcessPeriodicTasks() {
  if (!pacer_queue_update_task_.Running()) {
    pacer_queue_update_task_ = RepeatingTaskHandle::DelayedStart(
        task_queue_.Get(), kPacerQueueUpdateInterval, [this]() {
          RTC_DCHECK_RUN_ON(&task_queue_);
          TimeDelta expected_queue_time = pacer()->ExpectedQueueTime();
          control_handler_->SetPacerQueue(expected_queue_time);
          UpdateControlState();
          return kPacerQueueUpdateInterval;
        });
  }
  controller_task_.Stop();
  if (process_interval_.IsFinite()) {
    controller_task_ = RepeatingTaskHandle::DelayedStart(
        task_queue_.Get(), process_interval_, [this]() {
          RTC_DCHECK_RUN_ON(&task_queue_);
          UpdateControllerWithTimeInterval();
          return process_interval_;
        });
  }
}

void RtpTransportControllerSend::UpdateControllerWithTimeInterval() {
  RTC_DCHECK(controller_);
  ProcessInterval msg;
  msg.at_time = Timestamp::Millis(clock_->TimeInMilliseconds());
  if (add_pacing_to_cwin_)
    msg.pacer_queue = pacer()->QueueSizeData();
  PostUpdates(controller_->OnProcessInterval(msg));
}

void RtpTransportControllerSend::UpdateStreamsConfig() {
  streams_config_.at_time = Timestamp::Millis(clock_->TimeInMilliseconds());
  if (controller_)
    PostUpdates(controller_->OnStreamsConfig(streams_config_));
}

void RtpTransportControllerSend::PostUpdates(NetworkControlUpdate update) {
  if (update.congestion_window) {
    pacer()->SetCongestionWindow(*update.congestion_window);
  }
  if (update.pacer_config) {
    pacer()->SetPacingRates(update.pacer_config->data_rate(),
                            update.pacer_config->pad_rate());
  }
  for (const auto& probe : update.probe_cluster_configs) {
    pacer()->CreateProbeCluster(probe.target_data_rate, probe.id);
  }
  if (update.target_rate) {
    control_handler_->SetTargetRate(*update.target_rate);
    UpdateControlState();
  }
}

void RtpTransportControllerSend::OnReceivedRtcpReceiverReportBlocks(
    const ReportBlockList& report_blocks,
    int64_t now_ms) {
  if (report_blocks.empty())
    return;

  int total_packets_lost_delta = 0;
  int total_packets_delta = 0;

  // Compute the packet loss from all report blocks.
  for (const RTCPReportBlock& report_block : report_blocks) {
    auto it = last_report_blocks_.find(report_block.source_ssrc);
    if (it != last_report_blocks_.end()) {
      auto number_of_packets = report_block.extended_highest_sequence_number -
                               it->second.extended_highest_sequence_number;
      total_packets_delta += number_of_packets;
      auto lost_delta = report_block.packets_lost - it->second.packets_lost;
      total_packets_lost_delta += lost_delta;
    }
    last_report_blocks_[report_block.source_ssrc] = report_block;
  }
  // Can only compute delta if there has been previous blocks to compare to. If
  // not, total_packets_delta will be unchanged and there's nothing more to do.
  if (!total_packets_delta)
    return;
  int packets_received_delta = total_packets_delta - total_packets_lost_delta;
  // To detect lost packets, at least one packet has to be received. This check
  // is needed to avoid bandwith detection update in
  // VideoSendStreamTest.SuspendBelowMinBitrate

  if (packets_received_delta < 1)
    return;
  Timestamp now = Timestamp::Millis(now_ms);
  TransportLossReport msg;
  msg.packets_lost_delta = total_packets_lost_delta;
  msg.packets_received_delta = packets_received_delta;
  msg.receive_time = now;
  msg.start_time = last_report_block_time_;
  msg.end_time = now;
  if (controller_)
    PostUpdates(controller_->OnTransportLossReport(msg));
  last_report_block_time_ = now;
}

}  // namespace webrtc

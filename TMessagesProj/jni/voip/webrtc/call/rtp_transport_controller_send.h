/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef CALL_RTP_TRANSPORT_CONTROLLER_SEND_H_
#define CALL_RTP_TRANSPORT_CONTROLLER_SEND_H_

#include <atomic>
#include <map>
#include <memory>
#include <string>
#include <vector>

#include "api/network_state_predictor.h"
#include "api/sequence_checker.h"
#include "api/transport/network_control.h"
#include "api/units/data_rate.h"
#include "call/rtp_bitrate_configurator.h"
#include "call/rtp_transport_controller_send_interface.h"
#include "call/rtp_video_sender.h"
#include "modules/congestion_controller/rtp/control_handler.h"
#include "modules/congestion_controller/rtp/transport_feedback_adapter.h"
#include "modules/congestion_controller/rtp/transport_feedback_demuxer.h"
#include "modules/pacing/paced_sender.h"
#include "modules/pacing/packet_router.h"
#include "modules/pacing/rtp_packet_pacer.h"
#include "modules/pacing/task_queue_paced_sender.h"
#include "modules/utility/include/process_thread.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/network_route.h"
#include "rtc_base/race_checker.h"
#include "rtc_base/task_queue.h"
#include "rtc_base/task_utils/repeating_task.h"

namespace webrtc {
class Clock;
class FrameEncryptorInterface;
class RtcEventLog;

// TODO(nisse): When we get the underlying transports here, we should
// have one object implementing RtpTransportControllerSendInterface
// per transport, sharing the same congestion controller.
class RtpTransportControllerSend final
    : public RtpTransportControllerSendInterface,
      public RtcpBandwidthObserver,
      public TransportFeedbackObserver,
      public NetworkStateEstimateObserver {
 public:
  RtpTransportControllerSend(
      Clock* clock,
      RtcEventLog* event_log,
      NetworkStatePredictorFactoryInterface* predictor_factory,
      NetworkControllerFactoryInterface* controller_factory,
      const BitrateConstraints& bitrate_config,
      std::unique_ptr<ProcessThread> process_thread,
      TaskQueueFactory* task_queue_factory,
      const WebRtcKeyValueConfig* trials);
  ~RtpTransportControllerSend() override;

  // TODO(tommi): Change to std::unique_ptr<>.
  RtpVideoSenderInterface* CreateRtpVideoSender(
      const std::map<uint32_t, RtpState>& suspended_ssrcs,
      const std::map<uint32_t, RtpPayloadState>&
          states,  // move states into RtpTransportControllerSend
      const RtpConfig& rtp_config,
      int rtcp_report_interval_ms,
      Transport* send_transport,
      const RtpSenderObservers& observers,
      RtcEventLog* event_log,
      std::unique_ptr<FecController> fec_controller,
      const RtpSenderFrameEncryptionConfig& frame_encryption_config,
      rtc::scoped_refptr<FrameTransformerInterface> frame_transformer) override;
  void DestroyRtpVideoSender(
      RtpVideoSenderInterface* rtp_video_sender) override;

  // Implements RtpTransportControllerSendInterface
  rtc::TaskQueue* GetWorkerQueue() override;
  PacketRouter* packet_router() override;

  NetworkStateEstimateObserver* network_state_estimate_observer() override;
  TransportFeedbackObserver* transport_feedback_observer() override;
  RtpPacketSender* packet_sender() override;

  void SetAllocatedSendBitrateLimits(BitrateAllocationLimits limits) override;

  void SetPacingFactor(float pacing_factor) override;
  void SetQueueTimeLimit(int limit_ms) override;
  StreamFeedbackProvider* GetStreamFeedbackProvider() override;
  void RegisterTargetTransferRateObserver(
      TargetTransferRateObserver* observer) override;
  void OnNetworkRouteChanged(const std::string& transport_name,
                             const rtc::NetworkRoute& network_route) override;
  void OnNetworkAvailability(bool network_available) override;
  RtcpBandwidthObserver* GetBandwidthObserver() override;
  int64_t GetPacerQueuingDelayMs() const override;
  absl::optional<Timestamp> GetFirstPacketTime() const override;
  void EnablePeriodicAlrProbing(bool enable) override;
  void OnSentPacket(const rtc::SentPacket& sent_packet) override;
  void OnReceivedPacket(const ReceivedPacket& packet_msg) override;

  void SetSdpBitrateParameters(const BitrateConstraints& constraints) override;
  void SetClientBitratePreferences(const BitrateSettings& preferences) override;

  void OnTransportOverheadChanged(
      size_t transport_overhead_bytes_per_packet) override;

  void AccountForAudioPacketsInPacedSender(bool account_for_audio) override;
  void IncludeOverheadInPacedSender() override;
  void EnsureStarted() override;

  // Implements RtcpBandwidthObserver interface
  void OnReceivedEstimatedBitrate(uint32_t bitrate) override;
  void OnReceivedRtcpReceiverReport(const ReportBlockList& report_blocks,
                                    int64_t rtt,
                                    int64_t now_ms) override;

  // Implements TransportFeedbackObserver interface
  void OnAddPacket(const RtpPacketSendInfo& packet_info) override;
  void OnTransportFeedback(const rtcp::TransportFeedback& feedback) override;

  // Implements NetworkStateEstimateObserver interface
  void OnRemoteNetworkEstimate(NetworkStateEstimate estimate) override;

 private:
  struct PacerSettings {
    explicit PacerSettings(const WebRtcKeyValueConfig* trials);

    bool use_task_queue_pacer() const { return !tq_disabled.Get(); }

    FieldTrialFlag tq_disabled;  // Kill-switch not normally used.
    FieldTrialParameter<TimeDelta> holdback_window;
    FieldTrialParameter<int> holdback_packets;
  };

  void MaybeCreateControllers() RTC_RUN_ON(task_queue_);
  void UpdateInitialConstraints(TargetRateConstraints new_contraints)
      RTC_RUN_ON(task_queue_);

  void StartProcessPeriodicTasks() RTC_RUN_ON(task_queue_);
  void UpdateControllerWithTimeInterval() RTC_RUN_ON(task_queue_);

  absl::optional<BitrateConstraints> ApplyOrLiftRelayCap(bool is_relayed);
  bool IsRelevantRouteChange(const rtc::NetworkRoute& old_route,
                             const rtc::NetworkRoute& new_route) const;
  void UpdateBitrateConstraints(const BitrateConstraints& updated);
  void UpdateStreamsConfig() RTC_RUN_ON(task_queue_);
  void OnReceivedRtcpReceiverReportBlocks(const ReportBlockList& report_blocks,
                                          int64_t now_ms)
      RTC_RUN_ON(task_queue_);
  void PostUpdates(NetworkControlUpdate update) RTC_RUN_ON(task_queue_);
  void UpdateControlState() RTC_RUN_ON(task_queue_);
  RtpPacketPacer* pacer();
  const RtpPacketPacer* pacer() const;

  Clock* const clock_;
  RtcEventLog* const event_log_;
  SequenceChecker main_thread_;
  PacketRouter packet_router_;
  std::vector<std::unique_ptr<RtpVideoSenderInterface>> video_rtp_senders_
      RTC_GUARDED_BY(&main_thread_);
  RtpBitrateConfigurator bitrate_configurator_;
  std::map<std::string, rtc::NetworkRoute> network_routes_;
  bool pacer_started_;
  const std::unique_ptr<ProcessThread> process_thread_;
  const PacerSettings pacer_settings_;
  std::unique_ptr<PacedSender> process_thread_pacer_;
  std::unique_ptr<TaskQueuePacedSender> task_queue_pacer_;

  TargetTransferRateObserver* observer_ RTC_GUARDED_BY(task_queue_);
  TransportFeedbackDemuxer feedback_demuxer_;

  TransportFeedbackAdapter transport_feedback_adapter_
      RTC_GUARDED_BY(task_queue_);

  NetworkControllerFactoryInterface* const controller_factory_override_
      RTC_PT_GUARDED_BY(task_queue_);
  const std::unique_ptr<NetworkControllerFactoryInterface>
      controller_factory_fallback_ RTC_PT_GUARDED_BY(task_queue_);

  std::unique_ptr<CongestionControlHandler> control_handler_
      RTC_GUARDED_BY(task_queue_) RTC_PT_GUARDED_BY(task_queue_);

  std::unique_ptr<NetworkControllerInterface> controller_
      RTC_GUARDED_BY(task_queue_) RTC_PT_GUARDED_BY(task_queue_);

  TimeDelta process_interval_ RTC_GUARDED_BY(task_queue_);

  std::map<uint32_t, RTCPReportBlock> last_report_blocks_
      RTC_GUARDED_BY(task_queue_);
  Timestamp last_report_block_time_ RTC_GUARDED_BY(task_queue_);

  NetworkControllerConfig initial_config_ RTC_GUARDED_BY(task_queue_);
  StreamsConfig streams_config_ RTC_GUARDED_BY(task_queue_);

  const bool reset_feedback_on_route_change_;
  const bool send_side_bwe_with_overhead_;
  const bool add_pacing_to_cwin_;
  FieldTrialParameter<DataRate> relay_bandwidth_cap_;

  size_t transport_overhead_bytes_per_packet_ RTC_GUARDED_BY(task_queue_);
  bool network_available_ RTC_GUARDED_BY(task_queue_);
  RepeatingTaskHandle pacer_queue_update_task_ RTC_GUARDED_BY(task_queue_);
  RepeatingTaskHandle controller_task_ RTC_GUARDED_BY(task_queue_);

  // Protected by internal locks.
  RateLimiter retransmission_rate_limiter_;

  // TODO(perkj): `task_queue_` is supposed to replace `process_thread_`.
  // `task_queue_` is defined last to ensure all pending tasks are cancelled
  // and deleted before any other members.
  rtc::TaskQueue task_queue_;
  RTC_DISALLOW_COPY_AND_ASSIGN(RtpTransportControllerSend);
};

}  // namespace webrtc

#endif  // CALL_RTP_TRANSPORT_CONTROLLER_SEND_H_

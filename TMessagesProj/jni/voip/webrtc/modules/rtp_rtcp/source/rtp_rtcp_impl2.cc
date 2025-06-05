/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_rtcp_impl2.h"

#include <string.h>

#include <algorithm>
#include <cstdint>
#include <memory>
#include <set>
#include <string>
#include <utility>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/sequence_checker.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "modules/rtp_rtcp/source/rtcp_packet/dlrr.h"
#include "modules/rtp_rtcp/source/rtp_packet_history.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_config.h"
#include "modules/rtp_rtcp/source/time_util.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/ntp_time.h"

#ifdef _WIN32
// Disable warning C4355: 'this' : used in base member initializer list.
#pragma warning(disable : 4355)
#endif

namespace webrtc {
namespace {
constexpr TimeDelta kDefaultExpectedRetransmissionTime = TimeDelta::Millis(125);
constexpr TimeDelta kRttUpdateInterval = TimeDelta::Millis(1000);

RTCPSender::Configuration AddRtcpSendEvaluationCallback(
    RTCPSender::Configuration config,
    std::function<void(TimeDelta)> send_evaluation_callback) {
  config.schedule_next_rtcp_send_evaluation_function =
      std::move(send_evaluation_callback);
  return config;
}

RtpPacketHistory::PaddingMode GetPaddingMode(
    const FieldTrialsView* field_trials) {
  if (!field_trials ||
      !field_trials->IsDisabled("WebRTC-PaddingMode-RecentLargePacket")) {
    return RtpPacketHistory::PaddingMode::kRecentLargePacket;
  }
  return RtpPacketHistory::PaddingMode::kPriority;
}

}  // namespace

ModuleRtpRtcpImpl2::RtpSenderContext::RtpSenderContext(
    TaskQueueBase& worker_queue,
    const RtpRtcpInterface::Configuration& config)
    : packet_history(config.clock, GetPaddingMode(config.field_trials)),
      sequencer(config.local_media_ssrc,
                config.rtx_send_ssrc,
                /*require_marker_before_media_padding=*/!config.audio,
                config.clock),
      packet_sender(config, &packet_history),
      non_paced_sender(worker_queue, &packet_sender, &sequencer),
      packet_generator(
          config,
          &packet_history,
          config.paced_sender ? config.paced_sender : &non_paced_sender) {}

ModuleRtpRtcpImpl2::ModuleRtpRtcpImpl2(const Configuration& configuration)
    : worker_queue_(TaskQueueBase::Current()),
      rtcp_sender_(AddRtcpSendEvaluationCallback(
          RTCPSender::Configuration::FromRtpRtcpConfiguration(configuration),
          [this](TimeDelta duration) {
            ScheduleRtcpSendEvaluation(duration);
          })),
      rtcp_receiver_(configuration, this),
      clock_(configuration.clock),
      packet_overhead_(28),  // IPV4 UDP.
      nack_last_time_sent_full_ms_(0),
      nack_last_seq_number_sent_(0),
      rtt_stats_(configuration.rtt_stats),
      rtt_ms_(0) {
  RTC_DCHECK(worker_queue_);
  rtcp_thread_checker_.Detach();
  if (!configuration.receiver_only) {
    rtp_sender_ =
        std::make_unique<RtpSenderContext>(*worker_queue_, configuration);
    rtp_sender_->sequencing_checker.Detach();
    // Make sure rtcp sender use same timestamp offset as rtp sender.
    rtcp_sender_.SetTimestampOffset(
        rtp_sender_->packet_generator.TimestampOffset());
    rtp_sender_->packet_sender.SetTimestampOffset(
        rtp_sender_->packet_generator.TimestampOffset());
  }

  // Set default packet size limit.
  // TODO(nisse): Kind-of duplicates
  // webrtc::VideoSendStream::Config::Rtp::kDefaultMaxPacketSize.
  const size_t kTcpOverIpv4HeaderSize = 40;
  SetMaxRtpPacketSize(IP_PACKET_SIZE - kTcpOverIpv4HeaderSize);
  rtt_update_task_ = RepeatingTaskHandle::DelayedStart(
      worker_queue_, kRttUpdateInterval, [this]() {
        PeriodicUpdate();
        return kRttUpdateInterval;
      });
}

ModuleRtpRtcpImpl2::~ModuleRtpRtcpImpl2() {
  RTC_DCHECK_RUN_ON(worker_queue_);
  rtt_update_task_.Stop();
}

// static
std::unique_ptr<ModuleRtpRtcpImpl2> ModuleRtpRtcpImpl2::Create(
    const Configuration& configuration) {
  RTC_DCHECK(configuration.clock);
  RTC_DCHECK(TaskQueueBase::Current());
  return std::make_unique<ModuleRtpRtcpImpl2>(configuration);
}

void ModuleRtpRtcpImpl2::SetRtxSendStatus(int mode) {
  rtp_sender_->packet_generator.SetRtxStatus(mode);
}

int ModuleRtpRtcpImpl2::RtxSendStatus() const {
  return rtp_sender_ ? rtp_sender_->packet_generator.RtxStatus() : kRtxOff;
}

void ModuleRtpRtcpImpl2::SetRtxSendPayloadType(int payload_type,
                                               int associated_payload_type) {
  rtp_sender_->packet_generator.SetRtxPayloadType(payload_type,
                                                  associated_payload_type);
}

absl::optional<uint32_t> ModuleRtpRtcpImpl2::RtxSsrc() const {
  return rtp_sender_ ? rtp_sender_->packet_generator.RtxSsrc() : absl::nullopt;
}

absl::optional<uint32_t> ModuleRtpRtcpImpl2::FlexfecSsrc() const {
  if (rtp_sender_) {
    return rtp_sender_->packet_generator.FlexfecSsrc();
  }
  return absl::nullopt;
}

void ModuleRtpRtcpImpl2::IncomingRtcpPacket(
    rtc::ArrayView<const uint8_t> rtcp_packet) {
  RTC_DCHECK_RUN_ON(&rtcp_thread_checker_);
  rtcp_receiver_.IncomingPacket(rtcp_packet);
}

void ModuleRtpRtcpImpl2::RegisterSendPayloadFrequency(int payload_type,
                                                      int payload_frequency) {
  rtcp_sender_.SetRtpClockRate(payload_type, payload_frequency);
}

int32_t ModuleRtpRtcpImpl2::DeRegisterSendPayload(const int8_t payload_type) {
  return 0;
}

uint32_t ModuleRtpRtcpImpl2::StartTimestamp() const {
  return rtp_sender_->packet_generator.TimestampOffset();
}

// Configure start timestamp, default is a random number.
void ModuleRtpRtcpImpl2::SetStartTimestamp(const uint32_t timestamp) {
  rtcp_sender_.SetTimestampOffset(timestamp);
  rtp_sender_->packet_generator.SetTimestampOffset(timestamp);
  rtp_sender_->packet_sender.SetTimestampOffset(timestamp);
}

uint16_t ModuleRtpRtcpImpl2::SequenceNumber() const {
  RTC_DCHECK_RUN_ON(&rtp_sender_->sequencing_checker);
  return rtp_sender_->sequencer.media_sequence_number();
}

// Set SequenceNumber, default is a random number.
void ModuleRtpRtcpImpl2::SetSequenceNumber(const uint16_t seq_num) {
  RTC_DCHECK_RUN_ON(&rtp_sender_->sequencing_checker);
  if (rtp_sender_->sequencer.media_sequence_number() != seq_num) {
    rtp_sender_->sequencer.set_media_sequence_number(seq_num);
    rtp_sender_->packet_history.Clear();
  }
}

void ModuleRtpRtcpImpl2::SetRtpState(const RtpState& rtp_state) {
  RTC_DCHECK_RUN_ON(&rtp_sender_->sequencing_checker);
  rtp_sender_->packet_generator.SetRtpState(rtp_state);
  rtp_sender_->sequencer.SetRtpState(rtp_state);
  rtcp_sender_.SetTimestampOffset(rtp_state.start_timestamp);
  rtp_sender_->packet_sender.SetTimestampOffset(rtp_state.start_timestamp);
}

void ModuleRtpRtcpImpl2::SetRtxState(const RtpState& rtp_state) {
  RTC_DCHECK_RUN_ON(&rtp_sender_->sequencing_checker);
  rtp_sender_->packet_generator.SetRtxRtpState(rtp_state);
  rtp_sender_->sequencer.set_rtx_sequence_number(rtp_state.sequence_number);
}

RtpState ModuleRtpRtcpImpl2::GetRtpState() const {
  RTC_DCHECK_RUN_ON(&rtp_sender_->sequencing_checker);
  RtpState state = rtp_sender_->packet_generator.GetRtpState();
  rtp_sender_->sequencer.PopulateRtpState(state);
  return state;
}

RtpState ModuleRtpRtcpImpl2::GetRtxState() const {
  RTC_DCHECK_RUN_ON(&rtp_sender_->sequencing_checker);
  RtpState state = rtp_sender_->packet_generator.GetRtxRtpState();
  state.sequence_number = rtp_sender_->sequencer.rtx_sequence_number();
  return state;
}

void ModuleRtpRtcpImpl2::SetNonSenderRttMeasurement(bool enabled) {
  rtcp_sender_.SetNonSenderRttMeasurement(enabled);
  rtcp_receiver_.SetNonSenderRttMeasurement(enabled);
}

uint32_t ModuleRtpRtcpImpl2::local_media_ssrc() const {
  RTC_DCHECK_RUN_ON(&rtcp_thread_checker_);
  RTC_DCHECK_EQ(rtcp_receiver_.local_media_ssrc(), rtcp_sender_.SSRC());
  return rtcp_receiver_.local_media_ssrc();
}

void ModuleRtpRtcpImpl2::SetMid(absl::string_view mid) {
  if (rtp_sender_) {
    rtp_sender_->packet_generator.SetMid(mid);
  }
  // TODO(bugs.webrtc.org/4050): If we end up supporting the MID SDES item for
  // RTCP, this will need to be passed down to the RTCPSender also.
}

// TODO(pbos): Handle media and RTX streams separately (separate RTCP
// feedbacks).
RTCPSender::FeedbackState ModuleRtpRtcpImpl2::GetFeedbackState() {
  // TODO(bugs.webrtc.org/11581): Called by potentially multiple threads.
  // Mostly "Send*" methods. Make sure it's only called on the
  // construction thread.

  RTCPSender::FeedbackState state;
  // This is called also when receiver_only is true. Hence below
  // checks that rtp_sender_ exists.
  if (rtp_sender_) {
    StreamDataCounters rtp_stats;
    StreamDataCounters rtx_stats;
    rtp_sender_->packet_sender.GetDataCounters(&rtp_stats, &rtx_stats);
    state.packets_sent =
        rtp_stats.transmitted.packets + rtx_stats.transmitted.packets;
    state.media_bytes_sent = rtp_stats.transmitted.payload_bytes +
                             rtx_stats.transmitted.payload_bytes;
    state.send_bitrate =
        rtp_sender_->packet_sender.GetSendRates(clock_->CurrentTime()).Sum();
  }
  state.receiver = &rtcp_receiver_;

  if (absl::optional<RtpRtcpInterface::SenderReportStats> last_sr =
          rtcp_receiver_.GetSenderReportStats();
      last_sr.has_value()) {
    state.remote_sr = CompactNtp(last_sr->last_remote_timestamp);
    state.last_rr = last_sr->last_arrival_timestamp;
  }

  state.last_xr_rtis = rtcp_receiver_.ConsumeReceivedXrReferenceTimeInfo();

  return state;
}

int32_t ModuleRtpRtcpImpl2::SetSendingStatus(const bool sending) {
  if (rtcp_sender_.Sending() != sending) {
    // Sends RTCP BYE when going from true to false
    rtcp_sender_.SetSendingStatus(GetFeedbackState(), sending);
  }
  return 0;
}

bool ModuleRtpRtcpImpl2::Sending() const {
  return rtcp_sender_.Sending();
}

void ModuleRtpRtcpImpl2::SetSendingMediaStatus(const bool sending) {
  rtp_sender_->packet_generator.SetSendingMediaStatus(sending);
}

bool ModuleRtpRtcpImpl2::SendingMedia() const {
  return rtp_sender_ ? rtp_sender_->packet_generator.SendingMedia() : false;
}

bool ModuleRtpRtcpImpl2::IsAudioConfigured() const {
  return rtp_sender_ ? rtp_sender_->packet_generator.IsAudioConfigured()
                     : false;
}

void ModuleRtpRtcpImpl2::SetAsPartOfAllocation(bool part_of_allocation) {
  RTC_CHECK(rtp_sender_);
  rtp_sender_->packet_sender.ForceIncludeSendPacketsInAllocation(
      part_of_allocation);
}

bool ModuleRtpRtcpImpl2::OnSendingRtpFrame(uint32_t timestamp,
                                           int64_t capture_time_ms,
                                           int payload_type,
                                           bool force_sender_report) {
  if (!Sending()) {
    return false;
  }
  // TODO(bugs.webrtc.org/12873): Migrate this method and it's users to use
  // optional Timestamps.
  absl::optional<Timestamp> capture_time;
  if (capture_time_ms > 0) {
    capture_time = Timestamp::Millis(capture_time_ms);
  }
  absl::optional<int> payload_type_optional;
  if (payload_type >= 0)
    payload_type_optional = payload_type;

  auto closure = [this, timestamp, capture_time, payload_type_optional,
                  force_sender_report] {
    RTC_DCHECK_RUN_ON(worker_queue_);
    rtcp_sender_.SetLastRtpTime(timestamp, capture_time, payload_type_optional);
    // Make sure an RTCP report isn't queued behind a key frame.
    if (rtcp_sender_.TimeToSendRTCPReport(force_sender_report))
      rtcp_sender_.SendRTCP(GetFeedbackState(), kRtcpReport);
  };
  if (worker_queue_->IsCurrent()) {
    closure();
  } else {
    worker_queue_->PostTask(SafeTask(task_safety_.flag(), std::move(closure)));
  }
  return true;
}

bool ModuleRtpRtcpImpl2::TrySendPacket(std::unique_ptr<RtpPacketToSend> packet,
                                       const PacedPacketInfo& pacing_info) {
  RTC_DCHECK(rtp_sender_);
  RTC_DCHECK_RUN_ON(&rtp_sender_->sequencing_checker);
  if (!rtp_sender_->packet_generator.SendingMedia()) {
    return false;
  }
  if (packet->packet_type() == RtpPacketMediaType::kPadding &&
      packet->Ssrc() == rtp_sender_->packet_generator.SSRC() &&
      !rtp_sender_->sequencer.CanSendPaddingOnMediaSsrc()) {
    // New media packet preempted this generated padding packet, discard it.
    return false;
  }
  bool is_flexfec =
      packet->packet_type() == RtpPacketMediaType::kForwardErrorCorrection &&
      packet->Ssrc() == rtp_sender_->packet_generator.FlexfecSsrc();
  if (!is_flexfec) {
    rtp_sender_->sequencer.Sequence(*packet);
  }

  rtp_sender_->packet_sender.SendPacket(std::move(packet), pacing_info);
  return true;
}

void ModuleRtpRtcpImpl2::OnBatchComplete() {
  RTC_DCHECK(rtp_sender_);
  rtp_sender_->packet_sender.OnBatchComplete();
}

void ModuleRtpRtcpImpl2::SetFecProtectionParams(
    const FecProtectionParams& delta_params,
    const FecProtectionParams& key_params) {
  RTC_DCHECK(rtp_sender_);
  rtp_sender_->packet_sender.SetFecProtectionParameters(delta_params,
                                                        key_params);
}

std::vector<std::unique_ptr<RtpPacketToSend>>
ModuleRtpRtcpImpl2::FetchFecPackets() {
  RTC_DCHECK(rtp_sender_);
  RTC_DCHECK_RUN_ON(&rtp_sender_->sequencing_checker);
  return rtp_sender_->packet_sender.FetchFecPackets();
}

void ModuleRtpRtcpImpl2::OnAbortedRetransmissions(
    rtc::ArrayView<const uint16_t> sequence_numbers) {
  RTC_DCHECK(rtp_sender_);
  RTC_DCHECK_RUN_ON(&rtp_sender_->sequencing_checker);
  rtp_sender_->packet_sender.OnAbortedRetransmissions(sequence_numbers);
}

void ModuleRtpRtcpImpl2::OnPacketsAcknowledged(
    rtc::ArrayView<const uint16_t> sequence_numbers) {
  RTC_DCHECK(rtp_sender_);
  rtp_sender_->packet_history.CullAcknowledgedPackets(sequence_numbers);
}

bool ModuleRtpRtcpImpl2::SupportsPadding() const {
  RTC_DCHECK(rtp_sender_);
  return rtp_sender_->packet_generator.SupportsPadding();
}

bool ModuleRtpRtcpImpl2::SupportsRtxPayloadPadding() const {
  RTC_DCHECK(rtp_sender_);
  return rtp_sender_->packet_generator.SupportsRtxPayloadPadding();
}

std::vector<std::unique_ptr<RtpPacketToSend>>
ModuleRtpRtcpImpl2::GeneratePadding(size_t target_size_bytes) {
  RTC_DCHECK(rtp_sender_);
  RTC_DCHECK_RUN_ON(&rtp_sender_->sequencing_checker);

  return rtp_sender_->packet_generator.GeneratePadding(
      target_size_bytes, rtp_sender_->packet_sender.MediaHasBeenSent(),
      rtp_sender_->sequencer.CanSendPaddingOnMediaSsrc());
}

std::vector<RtpSequenceNumberMap::Info>
ModuleRtpRtcpImpl2::GetSentRtpPacketInfos(
    rtc::ArrayView<const uint16_t> sequence_numbers) const {
  RTC_DCHECK(rtp_sender_);
  return rtp_sender_->packet_sender.GetSentRtpPacketInfos(sequence_numbers);
}

size_t ModuleRtpRtcpImpl2::ExpectedPerPacketOverhead() const {
  if (!rtp_sender_) {
    return 0;
  }
  return rtp_sender_->packet_generator.ExpectedPerPacketOverhead();
}

void ModuleRtpRtcpImpl2::OnPacketSendingThreadSwitched() {
  // Ownership of sequencing is being transferred to another thread.
  rtp_sender_->sequencing_checker.Detach();
}

size_t ModuleRtpRtcpImpl2::MaxRtpPacketSize() const {
  RTC_DCHECK(rtp_sender_);
  return rtp_sender_->packet_generator.MaxRtpPacketSize();
}

void ModuleRtpRtcpImpl2::SetMaxRtpPacketSize(size_t rtp_packet_size) {
  RTC_DCHECK_LE(rtp_packet_size, IP_PACKET_SIZE)
      << "rtp packet size too large: " << rtp_packet_size;
  RTC_DCHECK_GT(rtp_packet_size, packet_overhead_)
      << "rtp packet size too small: " << rtp_packet_size;

  rtcp_sender_.SetMaxRtpPacketSize(rtp_packet_size);
  if (rtp_sender_) {
    rtp_sender_->packet_generator.SetMaxRtpPacketSize(rtp_packet_size);
  }
}

RtcpMode ModuleRtpRtcpImpl2::RTCP() const {
  return rtcp_sender_.Status();
}

// Configure RTCP status i.e on/off.
void ModuleRtpRtcpImpl2::SetRTCPStatus(const RtcpMode method) {
  rtcp_sender_.SetRTCPStatus(method);
}

int32_t ModuleRtpRtcpImpl2::SetCNAME(absl::string_view c_name) {
  return rtcp_sender_.SetCNAME(c_name);
}

absl::optional<TimeDelta> ModuleRtpRtcpImpl2::LastRtt() const {
  absl::optional<TimeDelta> rtt = rtcp_receiver_.LastRtt();
  if (!rtt.has_value()) {
    MutexLock lock(&mutex_rtt_);
    if (rtt_ms_ > 0) {
      rtt = TimeDelta::Millis(rtt_ms_);
    }
  }
  return rtt;
}

TimeDelta ModuleRtpRtcpImpl2::ExpectedRetransmissionTime() const {
  int64_t expected_retransmission_time_ms = rtt_ms();
  if (expected_retransmission_time_ms > 0) {
    return TimeDelta::Millis(expected_retransmission_time_ms);
  }
  // No rtt available (`kRttUpdateInterval` not yet passed?), so try to
  // poll avg_rtt_ms directly from rtcp receiver.
  if (absl::optional<TimeDelta> rtt = rtcp_receiver_.AverageRtt()) {
    return *rtt;
  }
  return kDefaultExpectedRetransmissionTime;
}

// Force a send of an RTCP packet.
// Normal SR and RR are triggered via the process function.
int32_t ModuleRtpRtcpImpl2::SendRTCP(RTCPPacketType packet_type) {
  return rtcp_sender_.SendRTCP(GetFeedbackState(), packet_type);
}

void ModuleRtpRtcpImpl2::GetSendStreamDataCounters(
    StreamDataCounters* rtp_counters,
    StreamDataCounters* rtx_counters) const {
  rtp_sender_->packet_sender.GetDataCounters(rtp_counters, rtx_counters);
}

// Received RTCP report.
std::vector<ReportBlockData> ModuleRtpRtcpImpl2::GetLatestReportBlockData()
    const {
  return rtcp_receiver_.GetLatestReportBlockData();
}

absl::optional<RtpRtcpInterface::SenderReportStats>
ModuleRtpRtcpImpl2::GetSenderReportStats() const {
  return rtcp_receiver_.GetSenderReportStats();
}

absl::optional<RtpRtcpInterface::NonSenderRttStats>
ModuleRtpRtcpImpl2::GetNonSenderRttStats() const {
  RTCPReceiver::NonSenderRttStats non_sender_rtt_stats =
      rtcp_receiver_.GetNonSenderRTT();
  return {{
      non_sender_rtt_stats.round_trip_time(),
      non_sender_rtt_stats.total_round_trip_time(),
      non_sender_rtt_stats.round_trip_time_measurements(),
  }};
}

// (REMB) Receiver Estimated Max Bitrate.
void ModuleRtpRtcpImpl2::SetRemb(int64_t bitrate_bps,
                                 std::vector<uint32_t> ssrcs) {
  rtcp_sender_.SetRemb(bitrate_bps, std::move(ssrcs));
}

void ModuleRtpRtcpImpl2::UnsetRemb() {
  rtcp_sender_.UnsetRemb();
}

void ModuleRtpRtcpImpl2::SetExtmapAllowMixed(bool extmap_allow_mixed) {
  rtp_sender_->packet_generator.SetExtmapAllowMixed(extmap_allow_mixed);
}

void ModuleRtpRtcpImpl2::RegisterRtpHeaderExtension(absl::string_view uri,
                                                    int id) {
  bool registered =
      rtp_sender_->packet_generator.RegisterRtpHeaderExtension(uri, id);
  RTC_CHECK(registered);
}

void ModuleRtpRtcpImpl2::DeregisterSendRtpHeaderExtension(
    absl::string_view uri) {
  rtp_sender_->packet_generator.DeregisterRtpHeaderExtension(uri);
}

void ModuleRtpRtcpImpl2::SetTmmbn(std::vector<rtcp::TmmbItem> bounding_set) {
  rtcp_sender_.SetTmmbn(std::move(bounding_set));
}

// Send a Negative acknowledgment packet.
int32_t ModuleRtpRtcpImpl2::SendNACK(const uint16_t* nack_list,
                                     const uint16_t size) {
  uint16_t nack_length = size;
  uint16_t start_id = 0;
  int64_t now_ms = clock_->TimeInMilliseconds();
  if (TimeToSendFullNackList(now_ms)) {
    nack_last_time_sent_full_ms_ = now_ms;
  } else {
    // Only send extended list.
    if (nack_last_seq_number_sent_ == nack_list[size - 1]) {
      // Last sequence number is the same, do not send list.
      return 0;
    }
    // Send new sequence numbers.
    for (int i = 0; i < size; ++i) {
      if (nack_last_seq_number_sent_ == nack_list[i]) {
        start_id = i + 1;
        break;
      }
    }
    nack_length = size - start_id;
  }

  // Our RTCP NACK implementation is limited to kRtcpMaxNackFields sequence
  // numbers per RTCP packet.
  if (nack_length > kRtcpMaxNackFields) {
    nack_length = kRtcpMaxNackFields;
  }
  nack_last_seq_number_sent_ = nack_list[start_id + nack_length - 1];

  return rtcp_sender_.SendRTCP(GetFeedbackState(), kRtcpNack, nack_length,
                               &nack_list[start_id]);
}

void ModuleRtpRtcpImpl2::SendNack(
    const std::vector<uint16_t>& sequence_numbers) {
  rtcp_sender_.SendRTCP(GetFeedbackState(), kRtcpNack, sequence_numbers.size(),
                        sequence_numbers.data());
}

bool ModuleRtpRtcpImpl2::TimeToSendFullNackList(int64_t now) const {
  // Use RTT from RtcpRttStats class if provided.
  int64_t rtt = rtt_ms();
  if (rtt == 0) {
    if (absl::optional<TimeDelta> average_rtt = rtcp_receiver_.AverageRtt()) {
      rtt = average_rtt->ms();
    }
  }

  const int64_t kStartUpRttMs = 100;
  int64_t wait_time = 5 + ((rtt * 3) >> 1);  // 5 + RTT * 1.5.
  if (rtt == 0) {
    wait_time = kStartUpRttMs;
  }

  // Send a full NACK list once within every `wait_time`.
  return now - nack_last_time_sent_full_ms_ > wait_time;
}

// Store the sent packets, needed to answer to Negative acknowledgment requests.
void ModuleRtpRtcpImpl2::SetStorePacketsStatus(const bool enable,
                                               const uint16_t number_to_store) {
  rtp_sender_->packet_history.SetStorePacketsStatus(
      enable ? RtpPacketHistory::StorageMode::kStoreAndCull
             : RtpPacketHistory::StorageMode::kDisabled,
      number_to_store);
}

bool ModuleRtpRtcpImpl2::StorePackets() const {
  return rtp_sender_->packet_history.GetStorageMode() !=
         RtpPacketHistory::StorageMode::kDisabled;
}

void ModuleRtpRtcpImpl2::SendCombinedRtcpPacket(
    std::vector<std::unique_ptr<rtcp::RtcpPacket>> rtcp_packets) {
  rtcp_sender_.SendCombinedRtcpPacket(std::move(rtcp_packets));
}

int32_t ModuleRtpRtcpImpl2::SendLossNotification(uint16_t last_decoded_seq_num,
                                                 uint16_t last_received_seq_num,
                                                 bool decodability_flag,
                                                 bool buffering_allowed) {
  return rtcp_sender_.SendLossNotification(
      GetFeedbackState(), last_decoded_seq_num, last_received_seq_num,
      decodability_flag, buffering_allowed);
}

void ModuleRtpRtcpImpl2::SetRemoteSSRC(const uint32_t ssrc) {
  // Inform about the incoming SSRC.
  rtcp_sender_.SetRemoteSSRC(ssrc);
  rtcp_receiver_.SetRemoteSSRC(ssrc);
}

void ModuleRtpRtcpImpl2::SetLocalSsrc(uint32_t local_ssrc) {
  RTC_DCHECK_RUN_ON(&rtcp_thread_checker_);
  rtcp_receiver_.set_local_media_ssrc(local_ssrc);
  rtcp_sender_.SetSsrc(local_ssrc);
}

RtpSendRates ModuleRtpRtcpImpl2::GetSendRates() const {
  RTC_DCHECK_RUN_ON(&rtp_sender_->sequencing_checker);
  return rtp_sender_->packet_sender.GetSendRates(clock_->CurrentTime());
}

void ModuleRtpRtcpImpl2::OnRequestSendReport() {
  SendRTCP(kRtcpSr);
}

void ModuleRtpRtcpImpl2::OnReceivedNack(
    const std::vector<uint16_t>& nack_sequence_numbers) {
  if (!rtp_sender_)
    return;

  if (!StorePackets() || nack_sequence_numbers.empty()) {
    return;
  }
  // Use RTT from RtcpRttStats class if provided.
  int64_t rtt = rtt_ms();
  if (rtt == 0) {
    if (absl::optional<TimeDelta> average_rtt = rtcp_receiver_.AverageRtt()) {
      rtt = average_rtt->ms();
    }
  }
  rtp_sender_->packet_generator.OnReceivedNack(nack_sequence_numbers, rtt);
}

void ModuleRtpRtcpImpl2::OnReceivedRtcpReportBlocks(
    rtc::ArrayView<const ReportBlockData> report_blocks) {
  if (rtp_sender_) {
    uint32_t ssrc = SSRC();
    absl::optional<uint32_t> rtx_ssrc;
    if (rtp_sender_->packet_generator.RtxStatus() != kRtxOff) {
      rtx_ssrc = rtp_sender_->packet_generator.RtxSsrc();
    }

    for (const ReportBlockData& report_block : report_blocks) {
      if (ssrc == report_block.source_ssrc()) {
        rtp_sender_->packet_generator.OnReceivedAckOnSsrc(
            report_block.extended_highest_sequence_number());
      } else if (rtx_ssrc == report_block.source_ssrc()) {
        rtp_sender_->packet_generator.OnReceivedAckOnRtxSsrc(
            report_block.extended_highest_sequence_number());
      }
    }
  }
}

void ModuleRtpRtcpImpl2::set_rtt_ms(int64_t rtt_ms) {
  RTC_DCHECK_RUN_ON(worker_queue_);
  {
    MutexLock lock(&mutex_rtt_);
    rtt_ms_ = rtt_ms;
  }
  if (rtp_sender_) {
    rtp_sender_->packet_history.SetRtt(TimeDelta::Millis(rtt_ms));
  }
}

int64_t ModuleRtpRtcpImpl2::rtt_ms() const {
  MutexLock lock(&mutex_rtt_);
  return rtt_ms_;
}

void ModuleRtpRtcpImpl2::SetVideoBitrateAllocation(
    const VideoBitrateAllocation& bitrate) {
  rtcp_sender_.SetVideoBitrateAllocation(bitrate);
}

RTPSender* ModuleRtpRtcpImpl2::RtpSender() {
  return rtp_sender_ ? &rtp_sender_->packet_generator : nullptr;
}

const RTPSender* ModuleRtpRtcpImpl2::RtpSender() const {
  return rtp_sender_ ? &rtp_sender_->packet_generator : nullptr;
}

void ModuleRtpRtcpImpl2::PeriodicUpdate() {
  RTC_DCHECK_RUN_ON(worker_queue_);

  Timestamp check_since = clock_->CurrentTime() - kRttUpdateInterval;
  absl::optional<TimeDelta> rtt =
      rtcp_receiver_.OnPeriodicRttUpdate(check_since, rtcp_sender_.Sending());
  if (rtt) {
    if (rtt_stats_) {
      rtt_stats_->OnRttUpdate(rtt->ms());
    }
    set_rtt_ms(rtt->ms());
  }
}

void ModuleRtpRtcpImpl2::MaybeSendRtcp() {
  RTC_DCHECK_RUN_ON(worker_queue_);
  if (rtcp_sender_.TimeToSendRTCPReport())
    rtcp_sender_.SendRTCP(GetFeedbackState(), kRtcpReport);
}

// TODO(bugs.webrtc.org/12889): Consider removing this function when the issue
// is resolved.
void ModuleRtpRtcpImpl2::MaybeSendRtcpAtOrAfterTimestamp(
    Timestamp execution_time) {
  RTC_DCHECK_RUN_ON(worker_queue_);
  Timestamp now = clock_->CurrentTime();
  if (now >= execution_time) {
    MaybeSendRtcp();
    return;
  }

  TimeDelta delta = execution_time - now;
  // TaskQueue may run task 1ms earlier, so don't print warning if in this case.
  if (delta > TimeDelta::Millis(1)) {
    RTC_DLOG(LS_WARNING) << "BUGBUG: Task queue scheduled delayed call "
                         << delta << " too early.";
  }

  ScheduleMaybeSendRtcpAtOrAfterTimestamp(execution_time, delta);
}

void ModuleRtpRtcpImpl2::ScheduleRtcpSendEvaluation(TimeDelta duration) {
  // We end up here under various sequences including the worker queue, and
  // the RTCPSender lock is held.
  // We're assuming that the fact that RTCPSender executes under other sequences
  // than the worker queue on which it's created on implies that external
  // synchronization is present and removes this activity before destruction.
  if (duration.IsZero()) {
    worker_queue_->PostTask(SafeTask(task_safety_.flag(), [this] {
      RTC_DCHECK_RUN_ON(worker_queue_);
      MaybeSendRtcp();
    }));
  } else {
    Timestamp execution_time = clock_->CurrentTime() + duration;
    ScheduleMaybeSendRtcpAtOrAfterTimestamp(execution_time, duration);
  }
}

void ModuleRtpRtcpImpl2::ScheduleMaybeSendRtcpAtOrAfterTimestamp(
    Timestamp execution_time,
    TimeDelta duration) {
  // We end up here under various sequences including the worker queue, and
  // the RTCPSender lock is held.
  // See note in ScheduleRtcpSendEvaluation about why `worker_queue_` can be
  // accessed.
  worker_queue_->PostDelayedTask(
      SafeTask(task_safety_.flag(),
               [this, execution_time] {
                 RTC_DCHECK_RUN_ON(worker_queue_);
                 MaybeSendRtcpAtOrAfterTimestamp(execution_time);
               }),
      duration.RoundUpTo(TimeDelta::Millis(1)));
}

}  // namespace webrtc

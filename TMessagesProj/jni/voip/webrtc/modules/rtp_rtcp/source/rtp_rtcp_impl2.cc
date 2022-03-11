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

#include "absl/types/optional.h"
#include "api/sequence_checker.h"
#include "api/transport/field_trial_based_config.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "modules/rtp_rtcp/source/rtcp_packet/dlrr.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_config.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/ntp_time.h"

#ifdef _WIN32
// Disable warning C4355: 'this' : used in base member initializer list.
#pragma warning(disable : 4355)
#endif

namespace webrtc {
namespace {
const int64_t kDefaultExpectedRetransmissionTimeMs = 125;

constexpr TimeDelta kRttUpdateInterval = TimeDelta::Millis(1000);

RTCPSender::Configuration AddRtcpSendEvaluationCallback(
    RTCPSender::Configuration config,
    std::function<void(TimeDelta)> send_evaluation_callback) {
  config.schedule_next_rtcp_send_evaluation_function =
      std::move(send_evaluation_callback);
  return config;
}

int DelayMillisForDuration(TimeDelta duration) {
  // TimeDelta::ms() rounds downwards sometimes which leads to too little time
  // slept. Account for this, unless `duration` is exactly representable in
  // millisecs.
  return (duration.us() + rtc::kNumMillisecsPerSec - 1) /
         rtc::kNumMicrosecsPerMillisec;
}
}  // namespace

ModuleRtpRtcpImpl2::RtpSenderContext::RtpSenderContext(
    const RtpRtcpInterface::Configuration& config)
    : packet_history(config.clock, config.enable_rtx_padding_prioritization),
      sequencer(config.local_media_ssrc,
                config.rtx_send_ssrc,
                /*require_marker_before_media_padding=*/!config.audio,
                config.clock),
      packet_sender(config, &packet_history),
      non_paced_sender(&packet_sender, &sequencer),
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
      remote_bitrate_(configuration.remote_bitrate_estimator),
      rtt_stats_(configuration.rtt_stats),
      rtt_ms_(0) {
  RTC_DCHECK(worker_queue_);
  rtcp_thread_checker_.Detach();
  if (!configuration.receiver_only) {
    rtp_sender_ = std::make_unique<RtpSenderContext>(configuration);
    rtp_sender_->sequencing_checker.Detach();
    // Make sure rtcp sender use same timestamp offset as rtp sender.
    rtcp_sender_.SetTimestampOffset(
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

void ModuleRtpRtcpImpl2::IncomingRtcpPacket(const uint8_t* rtcp_packet,
                                            const size_t length) {
  RTC_DCHECK_RUN_ON(&rtcp_thread_checker_);
  rtcp_receiver_.IncomingPacket(rtcp_packet, length);
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

void ModuleRtpRtcpImpl2::SetRid(const std::string& rid) {
  if (rtp_sender_) {
    rtp_sender_->packet_generator.SetRid(rid);
  }
}

void ModuleRtpRtcpImpl2::SetMid(const std::string& mid) {
  if (rtp_sender_) {
    rtp_sender_->packet_generator.SetMid(mid);
  }
  // TODO(bugs.webrtc.org/4050): If we end up supporting the MID SDES item for
  // RTCP, this will need to be passed down to the RTCPSender also.
}

void ModuleRtpRtcpImpl2::SetCsrcs(const std::vector<uint32_t>& csrcs) {
  rtcp_sender_.SetCsrcs(csrcs);
  rtp_sender_->packet_generator.SetCsrcs(csrcs);
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
        rtp_sender_->packet_sender.GetSendRates().Sum().bps<uint32_t>();
  }
  state.receiver = &rtcp_receiver_;

  uint32_t received_ntp_secs = 0;
  uint32_t received_ntp_frac = 0;
  state.remote_sr = 0;
  if (rtcp_receiver_.NTP(&received_ntp_secs, &received_ntp_frac,
                         /*rtcp_arrival_time_secs=*/&state.last_rr_ntp_secs,
                         /*rtcp_arrival_time_frac=*/&state.last_rr_ntp_frac,
                         /*rtcp_timestamp=*/nullptr,
                         /*remote_sender_packet_count=*/nullptr,
                         /*remote_sender_octet_count=*/nullptr,
                         /*remote_sender_reports_count=*/nullptr)) {
    state.remote_sr = ((received_ntp_secs & 0x0000ffff) << 16) +
                      ((received_ntp_frac & 0xffff0000) >> 16);
  }

  state.last_xr_rtis = rtcp_receiver_.ConsumeReceivedXrReferenceTimeInfo();

  return state;
}

// TODO(nisse): This method shouldn't be called for a receive-only
// stream. Delete rtp_sender_ check as soon as all applications are
// updated.
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

// TODO(nisse): This method shouldn't be called for a receive-only
// stream. Delete rtp_sender_ check as soon as all applications are
// updated.
void ModuleRtpRtcpImpl2::SetSendingMediaStatus(const bool sending) {
  if (rtp_sender_) {
    rtp_sender_->packet_generator.SetSendingMediaStatus(sending);
  } else {
    RTC_DCHECK(!sending);
  }
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
  if (!Sending())
    return false;

  // TODO(bugs.webrtc.org/12873): Migrate this method and it's users to use
  // optional Timestamps.
  absl::optional<Timestamp> capture_time;
  if (capture_time_ms > 0) {
    capture_time = Timestamp::Millis(capture_time_ms);
  }
  absl::optional<int> payload_type_optional;
  if (payload_type >= 0)
    payload_type_optional = payload_type;
  rtcp_sender_.SetLastRtpTime(timestamp, capture_time, payload_type_optional);
  // Make sure an RTCP report isn't queued behind a key frame.
  if (rtcp_sender_.TimeToSendRTCPReport(force_sender_report))
    rtcp_sender_.SendRTCP(GetFeedbackState(), kRtcpReport);

  return true;
}

bool ModuleRtpRtcpImpl2::TrySendPacket(RtpPacketToSend* packet,
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

  rtp_sender_->packet_sender.SendPacket(packet, pacing_info);
  return true;
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

int32_t ModuleRtpRtcpImpl2::SetCNAME(const char* c_name) {
  return rtcp_sender_.SetCNAME(c_name);
}

int32_t ModuleRtpRtcpImpl2::RemoteNTP(uint32_t* received_ntpsecs,
                                      uint32_t* received_ntpfrac,
                                      uint32_t* rtcp_arrival_time_secs,
                                      uint32_t* rtcp_arrival_time_frac,
                                      uint32_t* rtcp_timestamp) const {
  return rtcp_receiver_.NTP(received_ntpsecs, received_ntpfrac,
                            rtcp_arrival_time_secs, rtcp_arrival_time_frac,
                            rtcp_timestamp,
                            /*remote_sender_packet_count=*/nullptr,
                            /*remote_sender_octet_count=*/nullptr,
                            /*remote_sender_reports_count=*/nullptr)
             ? 0
             : -1;
}

// TODO(tommi): Check if `avg_rtt_ms`, `min_rtt_ms`, `max_rtt_ms` params are
// actually used in practice (some callers ask for it but don't use it). It
// could be that only `rtt` is needed and if so, then the fast path could be to
// just call rtt_ms() and rely on the calculation being done periodically.
int32_t ModuleRtpRtcpImpl2::RTT(const uint32_t remote_ssrc,
                                int64_t* rtt,
                                int64_t* avg_rtt,
                                int64_t* min_rtt,
                                int64_t* max_rtt) const {
  int32_t ret = rtcp_receiver_.RTT(remote_ssrc, rtt, avg_rtt, min_rtt, max_rtt);
  if (rtt && *rtt == 0) {
    // Try to get RTT from RtcpRttStats class.
    *rtt = rtt_ms();
  }
  return ret;
}

int64_t ModuleRtpRtcpImpl2::ExpectedRetransmissionTimeMs() const {
  int64_t expected_retransmission_time_ms = rtt_ms();
  if (expected_retransmission_time_ms > 0) {
    return expected_retransmission_time_ms;
  }
  // No rtt available (`kRttUpdateInterval` not yet passed?), so try to
  // poll avg_rtt_ms directly from rtcp receiver.
  if (rtcp_receiver_.RTT(rtcp_receiver_.RemoteSSRC(), nullptr,
                         &expected_retransmission_time_ms, nullptr,
                         nullptr) == 0) {
    return expected_retransmission_time_ms;
  }
  return kDefaultExpectedRetransmissionTimeMs;
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
  SenderReportStats stats;
  uint32_t remote_timestamp_secs;
  uint32_t remote_timestamp_frac;
  uint32_t arrival_timestamp_secs;
  uint32_t arrival_timestamp_frac;
  if (rtcp_receiver_.NTP(&remote_timestamp_secs, &remote_timestamp_frac,
                         &arrival_timestamp_secs, &arrival_timestamp_frac,
                         /*rtcp_timestamp=*/nullptr, &stats.packets_sent,
                         &stats.bytes_sent, &stats.reports_count)) {
    stats.last_remote_timestamp.Set(remote_timestamp_secs,
                                    remote_timestamp_frac);
    stats.last_arrival_timestamp.Set(arrival_timestamp_secs,
                                     arrival_timestamp_frac);
    return stats;
  }
  return absl::nullopt;
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
    rtcp_receiver_.RTT(rtcp_receiver_.RemoteSSRC(), NULL, &rtt, NULL, NULL);
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
  // Typically called on the `rtp_transport_queue_` owned by an
  // RtpTransportControllerSendInterface instance.
  return rtp_sender_->packet_sender.GetSendRates();
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
    rtcp_receiver_.RTT(rtcp_receiver_.RemoteSSRC(), NULL, &rtt, NULL, NULL);
  }
  rtp_sender_->packet_generator.OnReceivedNack(nack_sequence_numbers, rtt);
}

void ModuleRtpRtcpImpl2::OnReceivedRtcpReportBlocks(
    const ReportBlockList& report_blocks) {
  if (rtp_sender_) {
    uint32_t ssrc = SSRC();
    absl::optional<uint32_t> rtx_ssrc;
    if (rtp_sender_->packet_generator.RtxStatus() != kRtxOff) {
      rtx_ssrc = rtp_sender_->packet_generator.RtxSsrc();
    }

    for (const RTCPReportBlock& report_block : report_blocks) {
      if (ssrc == report_block.source_ssrc) {
        rtp_sender_->packet_generator.OnReceivedAckOnSsrc(
            report_block.extended_highest_sequence_number);
      } else if (rtx_ssrc && *rtx_ssrc == report_block.source_ssrc) {
        rtp_sender_->packet_generator.OnReceivedAckOnRtxSsrc(
            report_block.extended_highest_sequence_number);
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
    rtp_sender_->packet_history.SetRtt(rtt_ms);
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

// RTC_RUN_ON(worker_queue_);
void ModuleRtpRtcpImpl2::MaybeSendRtcp() {
  if (rtcp_sender_.TimeToSendRTCPReport())
    rtcp_sender_.SendRTCP(GetFeedbackState(), kRtcpReport);
}

// TODO(bugs.webrtc.org/12889): Consider removing this function when the issue
// is resolved.
// RTC_RUN_ON(worker_queue_);
void ModuleRtpRtcpImpl2::MaybeSendRtcpAtOrAfterTimestamp(
    Timestamp execution_time) {
  Timestamp now = clock_->CurrentTime();
  if (now >= execution_time) {
    MaybeSendRtcp();
    return;
  }

  RTC_DLOG(LS_WARNING)
      << "BUGBUG: Task queue scheduled delayed call too early.";

  ScheduleMaybeSendRtcpAtOrAfterTimestamp(execution_time, execution_time - now);
}

void ModuleRtpRtcpImpl2::ScheduleRtcpSendEvaluation(TimeDelta duration) {
  // We end up here under various sequences including the worker queue, and
  // the RTCPSender lock is held.
  // We're assuming that the fact that RTCPSender executes under other sequences
  // than the worker queue on which it's created on implies that external
  // synchronization is present and removes this activity before destruction.
  if (duration.IsZero()) {
    worker_queue_->PostTask(ToQueuedTask(task_safety_, [this] {
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
      ToQueuedTask(task_safety_,
                   [this, execution_time] {
                     RTC_DCHECK_RUN_ON(worker_queue_);
                     MaybeSendRtcpAtOrAfterTimestamp(execution_time);
                   }),
      DelayMillisForDuration(duration));
}

}  // namespace webrtc

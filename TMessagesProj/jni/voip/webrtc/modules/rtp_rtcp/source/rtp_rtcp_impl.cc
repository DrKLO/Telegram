/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_rtcp_impl.h"

#include <string.h>

#include <algorithm>
#include <cstdint>
#include <memory>
#include <set>
#include <string>
#include <utility>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "modules/rtp_rtcp/source/rtcp_packet/dlrr.h"
#include "modules/rtp_rtcp/source/rtcp_sender.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_config.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_interface.h"
#include "modules/rtp_rtcp/source/time_util.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/ntp_time.h"

#ifdef _WIN32
// Disable warning C4355: 'this' : used in base member initializer list.
#pragma warning(disable : 4355)
#endif

namespace webrtc {
namespace {
const int64_t kRtpRtcpRttProcessTimeMs = 1000;
const int64_t kRtpRtcpBitrateProcessTimeMs = 10;
constexpr TimeDelta kDefaultExpectedRetransmissionTime = TimeDelta::Millis(125);
}  // namespace

ModuleRtpRtcpImpl::RtpSenderContext::RtpSenderContext(
    const RtpRtcpInterface::Configuration& config)
    : packet_history(config.clock, RtpPacketHistory::PaddingMode::kPriority),
      sequencer_(config.local_media_ssrc,
                 config.rtx_send_ssrc,
                 /*require_marker_before_media_padding=*/!config.audio,
                 config.clock),
      packet_sender(config, &packet_history),
      non_paced_sender(&packet_sender, &sequencer_),
      packet_generator(
          config,
          &packet_history,
          config.paced_sender ? config.paced_sender : &non_paced_sender) {}

std::unique_ptr<RtpRtcp> RtpRtcp::DEPRECATED_Create(
    const Configuration& configuration) {
  RTC_DCHECK(configuration.clock);
  return std::make_unique<ModuleRtpRtcpImpl>(configuration);
}

ModuleRtpRtcpImpl::ModuleRtpRtcpImpl(const Configuration& configuration)
    : rtcp_sender_(
          RTCPSender::Configuration::FromRtpRtcpConfiguration(configuration)),
      rtcp_receiver_(configuration, this),
      clock_(configuration.clock),
      last_bitrate_process_time_(clock_->TimeInMilliseconds()),
      last_rtt_process_time_(clock_->TimeInMilliseconds()),
      packet_overhead_(28),  // IPV4 UDP.
      nack_last_time_sent_full_ms_(0),
      nack_last_seq_number_sent_(0),
      rtt_stats_(configuration.rtt_stats),
      rtt_ms_(0) {
  if (!configuration.receiver_only) {
    rtp_sender_ = std::make_unique<RtpSenderContext>(configuration);
    // Make sure rtcp sender use same timestamp offset as rtp sender.
    rtcp_sender_.SetTimestampOffset(
        rtp_sender_->packet_generator.TimestampOffset());
  }

  // Set default packet size limit.
  // TODO(nisse): Kind-of duplicates
  // webrtc::VideoSendStream::Config::Rtp::kDefaultMaxPacketSize.
  const size_t kTcpOverIpv4HeaderSize = 40;
  SetMaxRtpPacketSize(IP_PACKET_SIZE - kTcpOverIpv4HeaderSize);
}

ModuleRtpRtcpImpl::~ModuleRtpRtcpImpl() = default;

// Process any pending tasks such as timeouts (non time critical events).
void ModuleRtpRtcpImpl::Process() {
  const int64_t now = clock_->TimeInMilliseconds();

  if (rtp_sender_) {
    if (now >= last_bitrate_process_time_ + kRtpRtcpBitrateProcessTimeMs) {
      rtp_sender_->packet_sender.ProcessBitrateAndNotifyObservers();
      last_bitrate_process_time_ = now;
    }
  }

  // TODO(bugs.webrtc.org/11581): We update the RTT once a second, whereas other
  // things that run in this method are updated much more frequently. Move the
  // RTT checking over to the worker thread, which matches better with where the
  // stats are maintained.
  bool process_rtt = now >= last_rtt_process_time_ + kRtpRtcpRttProcessTimeMs;
  if (rtcp_sender_.Sending()) {
    // Process RTT if we have received a report block and we haven't
    // processed RTT for at least `kRtpRtcpRttProcessTimeMs` milliseconds.
    // Note that LastReceivedReportBlockMs() grabs a lock, so check
    // `process_rtt` first.
    if (process_rtt && rtt_stats_ != nullptr &&
        rtcp_receiver_.LastReceivedReportBlockMs() > last_rtt_process_time_) {
      TimeDelta max_rtt = TimeDelta::Zero();
      for (const auto& block : rtcp_receiver_.GetLatestReportBlockData()) {
        if (block.last_rtt() > max_rtt) {
          max_rtt = block.last_rtt();
        }
      }
      // Report the rtt.
      if (max_rtt > TimeDelta::Zero()) {
        rtt_stats_->OnRttUpdate(max_rtt.ms());
      }
    }

    // Verify receiver reports are delivered and the reported sequence number
    // is increasing.
    // TODO(bugs.webrtc.org/11581): The timeout value needs to be checked every
    // few seconds (see internals of RtcpRrTimeout). Here, we may be polling it
    // a couple of hundred times a second, which isn't great since it grabs a
    // lock. Note also that LastReceivedReportBlockMs() (called above) and
    // RtcpRrTimeout() both grab the same lock and check the same timer, so
    // it should be possible to consolidate that work somehow.
    if (rtcp_receiver_.RtcpRrTimeout()) {
      RTC_LOG_F(LS_WARNING) << "Timeout: No RTCP RR received.";
    } else if (rtcp_receiver_.RtcpRrSequenceNumberTimeout()) {
      RTC_LOG_F(LS_WARNING) << "Timeout: No increase in RTCP RR extended "
                               "highest sequence number.";
    }
  } else {
    // Report rtt from receiver.
    if (process_rtt && rtt_stats_ != nullptr) {
      absl::optional<TimeDelta> rtt = rtcp_receiver_.GetAndResetXrRrRtt();
      if (rtt.has_value()) {
        rtt_stats_->OnRttUpdate(rtt->ms());
      }
    }
  }

  // Get processed rtt.
  if (process_rtt) {
    last_rtt_process_time_ = now;
    if (rtt_stats_) {
      // Make sure we have a valid RTT before setting.
      int64_t last_rtt = rtt_stats_->LastProcessedRtt();
      if (last_rtt >= 0)
        set_rtt_ms(last_rtt);
    }
  }

  if (rtcp_sender_.TimeToSendRTCPReport())
    rtcp_sender_.SendRTCP(GetFeedbackState(), kRtcpReport);

  if (rtcp_sender_.TMMBR() && rtcp_receiver_.UpdateTmmbrTimers()) {
    rtcp_receiver_.NotifyTmmbrUpdated();
  }
}

void ModuleRtpRtcpImpl::SetRtxSendStatus(int mode) {
  rtp_sender_->packet_generator.SetRtxStatus(mode);
}

int ModuleRtpRtcpImpl::RtxSendStatus() const {
  return rtp_sender_ ? rtp_sender_->packet_generator.RtxStatus() : kRtxOff;
}

void ModuleRtpRtcpImpl::SetRtxSendPayloadType(int payload_type,
                                              int associated_payload_type) {
  rtp_sender_->packet_generator.SetRtxPayloadType(payload_type,
                                                  associated_payload_type);
}

absl::optional<uint32_t> ModuleRtpRtcpImpl::RtxSsrc() const {
  return rtp_sender_ ? rtp_sender_->packet_generator.RtxSsrc() : absl::nullopt;
}

absl::optional<uint32_t> ModuleRtpRtcpImpl::FlexfecSsrc() const {
  if (rtp_sender_) {
    return rtp_sender_->packet_generator.FlexfecSsrc();
  }
  return absl::nullopt;
}

void ModuleRtpRtcpImpl::IncomingRtcpPacket(
    rtc::ArrayView<const uint8_t> rtcp_packet) {
  rtcp_receiver_.IncomingPacket(rtcp_packet);
}

void ModuleRtpRtcpImpl::RegisterSendPayloadFrequency(int payload_type,
                                                     int payload_frequency) {
  rtcp_sender_.SetRtpClockRate(payload_type, payload_frequency);
}

int32_t ModuleRtpRtcpImpl::DeRegisterSendPayload(const int8_t payload_type) {
  return 0;
}

uint32_t ModuleRtpRtcpImpl::StartTimestamp() const {
  return rtp_sender_->packet_generator.TimestampOffset();
}

// Configure start timestamp, default is a random number.
void ModuleRtpRtcpImpl::SetStartTimestamp(const uint32_t timestamp) {
  rtcp_sender_.SetTimestampOffset(timestamp);
  rtp_sender_->packet_generator.SetTimestampOffset(timestamp);
  rtp_sender_->packet_sender.SetTimestampOffset(timestamp);
}

uint16_t ModuleRtpRtcpImpl::SequenceNumber() const {
  MutexLock lock(&rtp_sender_->sequencer_mutex);
  return rtp_sender_->sequencer_.media_sequence_number();
}

// Set SequenceNumber, default is a random number.
void ModuleRtpRtcpImpl::SetSequenceNumber(const uint16_t seq_num) {
  MutexLock lock(&rtp_sender_->sequencer_mutex);
  rtp_sender_->sequencer_.set_media_sequence_number(seq_num);
}

void ModuleRtpRtcpImpl::SetRtpState(const RtpState& rtp_state) {
  MutexLock lock(&rtp_sender_->sequencer_mutex);
  rtp_sender_->packet_generator.SetRtpState(rtp_state);
  rtp_sender_->sequencer_.SetRtpState(rtp_state);
  rtcp_sender_.SetTimestampOffset(rtp_state.start_timestamp);
}

void ModuleRtpRtcpImpl::SetRtxState(const RtpState& rtp_state) {
  MutexLock lock(&rtp_sender_->sequencer_mutex);
  rtp_sender_->packet_generator.SetRtxRtpState(rtp_state);
  rtp_sender_->sequencer_.set_rtx_sequence_number(rtp_state.sequence_number);
}

RtpState ModuleRtpRtcpImpl::GetRtpState() const {
  MutexLock lock(&rtp_sender_->sequencer_mutex);
  RtpState state = rtp_sender_->packet_generator.GetRtpState();
  rtp_sender_->sequencer_.PopulateRtpState(state);
  return state;
}

RtpState ModuleRtpRtcpImpl::GetRtxState() const {
  MutexLock lock(&rtp_sender_->sequencer_mutex);
  RtpState state = rtp_sender_->packet_generator.GetRtxRtpState();
  state.sequence_number = rtp_sender_->sequencer_.rtx_sequence_number();
  return state;
}

void ModuleRtpRtcpImpl::SetMid(absl::string_view mid) {
  if (rtp_sender_) {
    rtp_sender_->packet_generator.SetMid(mid);
  }
  // TODO(bugs.webrtc.org/4050): If we end up supporting the MID SDES item for
  // RTCP, this will need to be passed down to the RTCPSender also.
}

// TODO(pbos): Handle media and RTX streams separately (separate RTCP
// feedbacks).
RTCPSender::FeedbackState ModuleRtpRtcpImpl::GetFeedbackState() {
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
    state.send_bitrate = rtp_sender_->packet_sender.GetSendRates().Sum();
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

int32_t ModuleRtpRtcpImpl::SetSendingStatus(const bool sending) {
  if (rtcp_sender_.Sending() != sending) {
    rtcp_sender_.SetSendingStatus(GetFeedbackState(), sending);
  }
  return 0;
}

bool ModuleRtpRtcpImpl::Sending() const {
  return rtcp_sender_.Sending();
}

void ModuleRtpRtcpImpl::SetSendingMediaStatus(const bool sending) {
  rtp_sender_->packet_generator.SetSendingMediaStatus(sending);
}

bool ModuleRtpRtcpImpl::SendingMedia() const {
  return rtp_sender_ ? rtp_sender_->packet_generator.SendingMedia() : false;
}

bool ModuleRtpRtcpImpl::IsAudioConfigured() const {
  return rtp_sender_ ? rtp_sender_->packet_generator.IsAudioConfigured()
                     : false;
}

void ModuleRtpRtcpImpl::SetAsPartOfAllocation(bool part_of_allocation) {
  RTC_CHECK(rtp_sender_);
  rtp_sender_->packet_sender.ForceIncludeSendPacketsInAllocation(
      part_of_allocation);
}

bool ModuleRtpRtcpImpl::OnSendingRtpFrame(uint32_t timestamp,
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

bool ModuleRtpRtcpImpl::TrySendPacket(std::unique_ptr<RtpPacketToSend> packet,
                                      const PacedPacketInfo& pacing_info) {
  RTC_DCHECK(rtp_sender_);
  // TODO(sprang): Consider if we can remove this check.
  if (!rtp_sender_->packet_generator.SendingMedia()) {
    return false;
  }
  {
    MutexLock lock(&rtp_sender_->sequencer_mutex);
    if (packet->packet_type() == RtpPacketMediaType::kPadding &&
        packet->Ssrc() == rtp_sender_->packet_generator.SSRC() &&
        !rtp_sender_->sequencer_.CanSendPaddingOnMediaSsrc()) {
      // New media packet preempted this generated padding packet, discard it.
      return false;
    }
    bool is_flexfec =
        packet->packet_type() == RtpPacketMediaType::kForwardErrorCorrection &&
        packet->Ssrc() == rtp_sender_->packet_generator.FlexfecSsrc();
    if (!is_flexfec) {
      rtp_sender_->sequencer_.Sequence(*packet);
    }
  }
  rtp_sender_->packet_sender.SendPacket(packet.get(), pacing_info);
  return true;
}

void ModuleRtpRtcpImpl::SetFecProtectionParams(const FecProtectionParams&,
                                               const FecProtectionParams&) {
  // Deferred FEC not supported in deprecated RTP module.
}

std::vector<std::unique_ptr<RtpPacketToSend>>
ModuleRtpRtcpImpl::FetchFecPackets() {
  // Deferred FEC not supported in deprecated RTP module.
  return {};
}

void ModuleRtpRtcpImpl::OnAbortedRetransmissions(
    rtc::ArrayView<const uint16_t> sequence_numbers) {
  RTC_DCHECK_NOTREACHED()
      << "Stream flushing not supported with legacy rtp modules.";
}

void ModuleRtpRtcpImpl::OnPacketsAcknowledged(
    rtc::ArrayView<const uint16_t> sequence_numbers) {
  RTC_DCHECK(rtp_sender_);
  rtp_sender_->packet_history.CullAcknowledgedPackets(sequence_numbers);
}

bool ModuleRtpRtcpImpl::SupportsPadding() const {
  RTC_DCHECK(rtp_sender_);
  return rtp_sender_->packet_generator.SupportsPadding();
}

bool ModuleRtpRtcpImpl::SupportsRtxPayloadPadding() const {
  RTC_DCHECK(rtp_sender_);
  return rtp_sender_->packet_generator.SupportsRtxPayloadPadding();
}

std::vector<std::unique_ptr<RtpPacketToSend>>
ModuleRtpRtcpImpl::GeneratePadding(size_t target_size_bytes) {
  RTC_DCHECK(rtp_sender_);
  MutexLock lock(&rtp_sender_->sequencer_mutex);
  return rtp_sender_->packet_generator.GeneratePadding(
      target_size_bytes, rtp_sender_->packet_sender.MediaHasBeenSent(),
      rtp_sender_->sequencer_.CanSendPaddingOnMediaSsrc());
}

std::vector<RtpSequenceNumberMap::Info>
ModuleRtpRtcpImpl::GetSentRtpPacketInfos(
    rtc::ArrayView<const uint16_t> sequence_numbers) const {
  RTC_DCHECK(rtp_sender_);
  return rtp_sender_->packet_sender.GetSentRtpPacketInfos(sequence_numbers);
}

size_t ModuleRtpRtcpImpl::ExpectedPerPacketOverhead() const {
  if (!rtp_sender_) {
    return 0;
  }
  return rtp_sender_->packet_generator.ExpectedPerPacketOverhead();
}

void ModuleRtpRtcpImpl::OnPacketSendingThreadSwitched() {}

size_t ModuleRtpRtcpImpl::MaxRtpPacketSize() const {
  RTC_DCHECK(rtp_sender_);
  return rtp_sender_->packet_generator.MaxRtpPacketSize();
}

void ModuleRtpRtcpImpl::SetMaxRtpPacketSize(size_t rtp_packet_size) {
  RTC_DCHECK_LE(rtp_packet_size, IP_PACKET_SIZE)
      << "rtp packet size too large: " << rtp_packet_size;
  RTC_DCHECK_GT(rtp_packet_size, packet_overhead_)
      << "rtp packet size too small: " << rtp_packet_size;

  rtcp_sender_.SetMaxRtpPacketSize(rtp_packet_size);
  if (rtp_sender_) {
    rtp_sender_->packet_generator.SetMaxRtpPacketSize(rtp_packet_size);
  }
}

RtcpMode ModuleRtpRtcpImpl::RTCP() const {
  return rtcp_sender_.Status();
}

// Configure RTCP status i.e on/off.
void ModuleRtpRtcpImpl::SetRTCPStatus(const RtcpMode method) {
  rtcp_sender_.SetRTCPStatus(method);
}

int32_t ModuleRtpRtcpImpl::SetCNAME(absl::string_view c_name) {
  return rtcp_sender_.SetCNAME(c_name);
}

absl::optional<TimeDelta> ModuleRtpRtcpImpl::LastRtt() const {
  absl::optional<TimeDelta> rtt = rtcp_receiver_.LastRtt();
  if (!rtt.has_value()) {
    MutexLock lock(&mutex_rtt_);
    if (rtt_ms_ > 0) {
      rtt = TimeDelta::Millis(rtt_ms_);
    }
  }
  return rtt;
}

TimeDelta ModuleRtpRtcpImpl::ExpectedRetransmissionTime() const {
  int64_t expected_retransmission_time_ms = rtt_ms();
  if (expected_retransmission_time_ms > 0) {
    return TimeDelta::Millis(expected_retransmission_time_ms);
  }
  // No rtt available (`kRtpRtcpRttProcessTimeMs` not yet passed?), so try to
  // poll avg_rtt_ms directly from rtcp receiver.
  if (absl::optional<TimeDelta> rtt = rtcp_receiver_.AverageRtt()) {
    return *rtt;
  }
  return kDefaultExpectedRetransmissionTime;
}

// Force a send of an RTCP packet.
// Normal SR and RR are triggered via the process function.
int32_t ModuleRtpRtcpImpl::SendRTCP(RTCPPacketType packet_type) {
  return rtcp_sender_.SendRTCP(GetFeedbackState(), packet_type);
}

void ModuleRtpRtcpImpl::GetSendStreamDataCounters(
    StreamDataCounters* rtp_counters,
    StreamDataCounters* rtx_counters) const {
  rtp_sender_->packet_sender.GetDataCounters(rtp_counters, rtx_counters);
}

// Received RTCP report.
std::vector<ReportBlockData> ModuleRtpRtcpImpl::GetLatestReportBlockData()
    const {
  return rtcp_receiver_.GetLatestReportBlockData();
}

absl::optional<RtpRtcpInterface::SenderReportStats>
ModuleRtpRtcpImpl::GetSenderReportStats() const {
  return rtcp_receiver_.GetSenderReportStats();
}

absl::optional<RtpRtcpInterface::NonSenderRttStats>
ModuleRtpRtcpImpl::GetNonSenderRttStats() const {
  // This is not implemented for this legacy class.
  return absl::nullopt;
}

// (REMB) Receiver Estimated Max Bitrate.
void ModuleRtpRtcpImpl::SetRemb(int64_t bitrate_bps,
                                std::vector<uint32_t> ssrcs) {
  rtcp_sender_.SetRemb(bitrate_bps, std::move(ssrcs));
}

void ModuleRtpRtcpImpl::UnsetRemb() {
  rtcp_sender_.UnsetRemb();
}

void ModuleRtpRtcpImpl::SetExtmapAllowMixed(bool extmap_allow_mixed) {
  rtp_sender_->packet_generator.SetExtmapAllowMixed(extmap_allow_mixed);
}

void ModuleRtpRtcpImpl::RegisterRtpHeaderExtension(absl::string_view uri,
                                                   int id) {
  bool registered =
      rtp_sender_->packet_generator.RegisterRtpHeaderExtension(uri, id);
  RTC_CHECK(registered);
}

void ModuleRtpRtcpImpl::DeregisterSendRtpHeaderExtension(
    absl::string_view uri) {
  rtp_sender_->packet_generator.DeregisterRtpHeaderExtension(uri);
}

void ModuleRtpRtcpImpl::SetTmmbn(std::vector<rtcp::TmmbItem> bounding_set) {
  rtcp_sender_.SetTmmbn(std::move(bounding_set));
}

// Send a Negative acknowledgment packet.
int32_t ModuleRtpRtcpImpl::SendNACK(const uint16_t* nack_list,
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

void ModuleRtpRtcpImpl::SendNack(
    const std::vector<uint16_t>& sequence_numbers) {
  rtcp_sender_.SendRTCP(GetFeedbackState(), kRtcpNack, sequence_numbers.size(),
                        sequence_numbers.data());
}

bool ModuleRtpRtcpImpl::TimeToSendFullNackList(int64_t now) const {
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
void ModuleRtpRtcpImpl::SetStorePacketsStatus(const bool enable,
                                              const uint16_t number_to_store) {
  rtp_sender_->packet_history.SetStorePacketsStatus(
      enable ? RtpPacketHistory::StorageMode::kStoreAndCull
             : RtpPacketHistory::StorageMode::kDisabled,
      number_to_store);
}

bool ModuleRtpRtcpImpl::StorePackets() const {
  return rtp_sender_->packet_history.GetStorageMode() !=
         RtpPacketHistory::StorageMode::kDisabled;
}

void ModuleRtpRtcpImpl::SendCombinedRtcpPacket(
    std::vector<std::unique_ptr<rtcp::RtcpPacket>> rtcp_packets) {
  rtcp_sender_.SendCombinedRtcpPacket(std::move(rtcp_packets));
}

int32_t ModuleRtpRtcpImpl::SendLossNotification(uint16_t last_decoded_seq_num,
                                                uint16_t last_received_seq_num,
                                                bool decodability_flag,
                                                bool buffering_allowed) {
  return rtcp_sender_.SendLossNotification(
      GetFeedbackState(), last_decoded_seq_num, last_received_seq_num,
      decodability_flag, buffering_allowed);
}

void ModuleRtpRtcpImpl::SetRemoteSSRC(const uint32_t ssrc) {
  // Inform about the incoming SSRC.
  rtcp_sender_.SetRemoteSSRC(ssrc);
  rtcp_receiver_.SetRemoteSSRC(ssrc);
}

void ModuleRtpRtcpImpl::SetLocalSsrc(uint32_t local_ssrc) {
  rtcp_receiver_.set_local_media_ssrc(local_ssrc);
  rtcp_sender_.SetSsrc(local_ssrc);
}

RtpSendRates ModuleRtpRtcpImpl::GetSendRates() const {
  return rtp_sender_->packet_sender.GetSendRates();
}

void ModuleRtpRtcpImpl::OnRequestSendReport() {
  SendRTCP(kRtcpSr);
}

void ModuleRtpRtcpImpl::OnReceivedNack(
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

void ModuleRtpRtcpImpl::OnReceivedRtcpReportBlocks(
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

void ModuleRtpRtcpImpl::set_rtt_ms(int64_t rtt_ms) {
  {
    MutexLock lock(&mutex_rtt_);
    rtt_ms_ = rtt_ms;
  }
  if (rtp_sender_) {
    rtp_sender_->packet_history.SetRtt(TimeDelta::Millis(rtt_ms));
  }
}

int64_t ModuleRtpRtcpImpl::rtt_ms() const {
  MutexLock lock(&mutex_rtt_);
  return rtt_ms_;
}

void ModuleRtpRtcpImpl::SetVideoBitrateAllocation(
    const VideoBitrateAllocation& bitrate) {
  rtcp_sender_.SetVideoBitrateAllocation(bitrate);
}

RTPSender* ModuleRtpRtcpImpl::RtpSender() {
  return rtp_sender_ ? &rtp_sender_->packet_generator : nullptr;
}

const RTPSender* ModuleRtpRtcpImpl::RtpSender() const {
  return rtp_sender_ ? &rtp_sender_->packet_generator : nullptr;
}

}  // namespace webrtc

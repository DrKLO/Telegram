/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_sender.h"

#include <string.h>  // memcpy

#include <algorithm>  // std::min
#include <memory>
#include <utility>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "api/rtp_headers.h"
#include "api/units/data_rate.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "logging/rtc_event_log/events/rtc_event_rtcp_packet_outgoing.h"
#include "modules/rtp_rtcp/source/rtcp_packet/app.h"
#include "modules/rtp_rtcp/source/rtcp_packet/bye.h"
#include "modules/rtp_rtcp/source/rtcp_packet/compound_packet.h"
#include "modules/rtp_rtcp/source/rtcp_packet/extended_reports.h"
#include "modules/rtp_rtcp/source/rtcp_packet/fir.h"
#include "modules/rtp_rtcp/source/rtcp_packet/loss_notification.h"
#include "modules/rtp_rtcp/source/rtcp_packet/nack.h"
#include "modules/rtp_rtcp/source/rtcp_packet/pli.h"
#include "modules/rtp_rtcp/source/rtcp_packet/receiver_report.h"
#include "modules/rtp_rtcp/source/rtcp_packet/remb.h"
#include "modules/rtp_rtcp/source/rtcp_packet/sdes.h"
#include "modules/rtp_rtcp/source/rtcp_packet/sender_report.h"
#include "modules/rtp_rtcp/source/rtcp_packet/tmmbn.h"
#include "modules/rtp_rtcp/source/rtcp_packet/tmmbr.h"
#include "modules/rtp_rtcp/source/rtcp_packet/transport_feedback.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_impl2.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_interface.h"
#include "modules/rtp_rtcp/source/time_util.h"
#include "modules/rtp_rtcp/source/tmmbr_help.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/trace_event.h"

namespace webrtc {

namespace {
const uint32_t kRtcpAnyExtendedReports = kRtcpXrReceiverReferenceTime |
                                         kRtcpXrDlrrReportBlock |
                                         kRtcpXrTargetBitrate;
constexpr int32_t kDefaultVideoReportInterval = 1000;
constexpr int32_t kDefaultAudioReportInterval = 5000;
}  // namespace

// Helper to put several RTCP packets into lower layer datagram RTCP packet.
class RTCPSender::PacketSender {
 public:
  PacketSender(rtcp::RtcpPacket::PacketReadyCallback callback,
               size_t max_packet_size)
      : callback_(callback), max_packet_size_(max_packet_size) {
    RTC_CHECK_LE(max_packet_size, IP_PACKET_SIZE);
  }
  ~PacketSender() { RTC_DCHECK_EQ(index_, 0) << "Unsent rtcp packet."; }

  // Appends a packet to pending compound packet.
  // Sends rtcp packet if buffer is full and resets the buffer.
  void AppendPacket(const rtcp::RtcpPacket& packet) {
    packet.Create(buffer_, &index_, max_packet_size_, callback_);
  }

  // Sends pending rtcp packet.
  void Send() {
    if (index_ > 0) {
      callback_(rtc::ArrayView<const uint8_t>(buffer_, index_));
      index_ = 0;
    }
  }

 private:
  const rtcp::RtcpPacket::PacketReadyCallback callback_;
  const size_t max_packet_size_;
  size_t index_ = 0;
  uint8_t buffer_[IP_PACKET_SIZE];
};

RTCPSender::FeedbackState::FeedbackState()
    : packets_sent(0),
      media_bytes_sent(0),
      send_bitrate(DataRate::Zero()),
      remote_sr(0),
      receiver(nullptr) {}

RTCPSender::FeedbackState::FeedbackState(const FeedbackState&) = default;

RTCPSender::FeedbackState::FeedbackState(FeedbackState&&) = default;

RTCPSender::FeedbackState::~FeedbackState() = default;

class RTCPSender::RtcpContext {
 public:
  RtcpContext(const FeedbackState& feedback_state,
              int32_t nack_size,
              const uint16_t* nack_list,
              Timestamp now)
      : feedback_state_(feedback_state),
        nack_size_(nack_size),
        nack_list_(nack_list),
        now_(now) {}

  const FeedbackState& feedback_state_;
  const int32_t nack_size_;
  const uint16_t* nack_list_;
  const Timestamp now_;
};

RTCPSender::Configuration RTCPSender::Configuration::FromRtpRtcpConfiguration(
    const RtpRtcpInterface::Configuration& configuration) {
  RTCPSender::Configuration result;
  result.audio = configuration.audio;
  result.local_media_ssrc = configuration.local_media_ssrc;
  result.clock = configuration.clock;
  result.outgoing_transport = configuration.outgoing_transport;
  result.non_sender_rtt_measurement = configuration.non_sender_rtt_measurement;
  result.event_log = configuration.event_log;
  if (configuration.rtcp_report_interval_ms) {
    result.rtcp_report_interval =
        TimeDelta::Millis(configuration.rtcp_report_interval_ms);
  }
  result.receive_statistics = configuration.receive_statistics;
  result.rtcp_packet_type_counter_observer =
      configuration.rtcp_packet_type_counter_observer;
  return result;
}

RTCPSender::RTCPSender(Configuration config)
    : audio_(config.audio),
      ssrc_(config.local_media_ssrc),
      clock_(config.clock),
      random_(clock_->TimeInMicroseconds()),
      method_(RtcpMode::kOff),
      event_log_(config.event_log),
      transport_(config.outgoing_transport),
      report_interval_(config.rtcp_report_interval.value_or(
          TimeDelta::Millis(config.audio ? kDefaultAudioReportInterval
                                         : kDefaultVideoReportInterval))),
      schedule_next_rtcp_send_evaluation_function_(
          std::move(config.schedule_next_rtcp_send_evaluation_function)),
      sending_(false),
      timestamp_offset_(0),
      last_rtp_timestamp_(0),
      remote_ssrc_(0),
      receive_statistics_(config.receive_statistics),

      sequence_number_fir_(0),

      remb_bitrate_(0),

      tmmbr_send_bps_(0),
      packet_oh_send_(0),
      max_packet_size_(IP_PACKET_SIZE - 28),  // IPv4 + UDP by default.

      xr_send_receiver_reference_time_enabled_(
          config.non_sender_rtt_measurement),
      packet_type_counter_observer_(config.rtcp_packet_type_counter_observer),
      send_video_bitrate_allocation_(false),
      last_payload_type_(-1) {
  RTC_DCHECK(transport_ != nullptr);

  builders_[kRtcpSr] = &RTCPSender::BuildSR;
  builders_[kRtcpRr] = &RTCPSender::BuildRR;
  builders_[kRtcpSdes] = &RTCPSender::BuildSDES;
  builders_[kRtcpPli] = &RTCPSender::BuildPLI;
  builders_[kRtcpFir] = &RTCPSender::BuildFIR;
  builders_[kRtcpRemb] = &RTCPSender::BuildREMB;
  builders_[kRtcpBye] = &RTCPSender::BuildBYE;
  builders_[kRtcpLossNotification] = &RTCPSender::BuildLossNotification;
  builders_[kRtcpTmmbr] = &RTCPSender::BuildTMMBR;
  builders_[kRtcpTmmbn] = &RTCPSender::BuildTMMBN;
  builders_[kRtcpNack] = &RTCPSender::BuildNACK;
  builders_[kRtcpAnyExtendedReports] = &RTCPSender::BuildExtendedReports;
}

RTCPSender::~RTCPSender() {}

RtcpMode RTCPSender::Status() const {
  MutexLock lock(&mutex_rtcp_sender_);
  return method_;
}

void RTCPSender::SetRTCPStatus(RtcpMode new_method) {
  MutexLock lock(&mutex_rtcp_sender_);

  if (new_method == RtcpMode::kOff) {
    next_time_to_send_rtcp_ = absl::nullopt;
  } else if (method_ == RtcpMode::kOff) {
    // When switching on, reschedule the next packet
    SetNextRtcpSendEvaluationDuration(report_interval_ / 2);
  }
  method_ = new_method;
}

bool RTCPSender::Sending() const {
  MutexLock lock(&mutex_rtcp_sender_);
  return sending_;
}

void RTCPSender::SetSendingStatus(const FeedbackState& feedback_state,
                                  bool sending) {
  MutexLock lock(&mutex_rtcp_sender_);
  sending_ = sending;
}

void RTCPSender::SetNonSenderRttMeasurement(bool enabled) {
  MutexLock lock(&mutex_rtcp_sender_);
  xr_send_receiver_reference_time_enabled_ = enabled;
}

int32_t RTCPSender::SendLossNotification(const FeedbackState& feedback_state,
                                         uint16_t last_decoded_seq_num,
                                         uint16_t last_received_seq_num,
                                         bool decodability_flag,
                                         bool buffering_allowed) {
  int32_t error_code = -1;
  auto callback = [&](rtc::ArrayView<const uint8_t> packet) {
    transport_->SendRtcp(packet);
    error_code = 0;
    if (event_log_) {
      event_log_->Log(std::make_unique<RtcEventRtcpPacketOutgoing>(packet));
    }
  };
  absl::optional<PacketSender> sender;
  {
    MutexLock lock(&mutex_rtcp_sender_);

    if (!loss_notification_.Set(last_decoded_seq_num, last_received_seq_num,
                                decodability_flag)) {
      return -1;
    }

    SetFlag(kRtcpLossNotification, /*is_volatile=*/true);

    if (buffering_allowed) {
      // The loss notification will be batched with additional feedback
      // messages.
      return 0;
    }

    sender.emplace(callback, max_packet_size_);
    auto result = ComputeCompoundRTCPPacket(
        feedback_state, RTCPPacketType::kRtcpLossNotification, 0, nullptr,
        *sender);
    if (result) {
      return *result;
    }
  }
  sender->Send();

  return error_code;
}

void RTCPSender::SetRemb(int64_t bitrate_bps, std::vector<uint32_t> ssrcs) {
  RTC_CHECK_GE(bitrate_bps, 0);
  MutexLock lock(&mutex_rtcp_sender_);
  if (method_ == RtcpMode::kOff) {
    RTC_LOG(LS_WARNING) << "Can't send RTCP if it is disabled.";
    return;
  }
  remb_bitrate_ = bitrate_bps;
  remb_ssrcs_ = std::move(ssrcs);

  SetFlag(kRtcpRemb, /*is_volatile=*/false);
  // Send a REMB immediately if we have a new REMB. The frequency of REMBs is
  // throttled by the caller.
  SetNextRtcpSendEvaluationDuration(TimeDelta::Zero());
}

void RTCPSender::UnsetRemb() {
  MutexLock lock(&mutex_rtcp_sender_);
  // Stop sending REMB each report until it is reenabled and REMB data set.
  ConsumeFlag(kRtcpRemb, /*forced=*/true);
}

bool RTCPSender::TMMBR() const {
  MutexLock lock(&mutex_rtcp_sender_);
  return IsFlagPresent(RTCPPacketType::kRtcpTmmbr);
}

void RTCPSender::SetMaxRtpPacketSize(size_t max_packet_size) {
  MutexLock lock(&mutex_rtcp_sender_);
  max_packet_size_ = max_packet_size;
}

void RTCPSender::SetTimestampOffset(uint32_t timestamp_offset) {
  MutexLock lock(&mutex_rtcp_sender_);
  timestamp_offset_ = timestamp_offset;
}

void RTCPSender::SetLastRtpTime(uint32_t rtp_timestamp,
                                absl::optional<Timestamp> capture_time,
                                absl::optional<int8_t> payload_type) {
  MutexLock lock(&mutex_rtcp_sender_);
  // For compatibility with clients who don't set payload type correctly on all
  // calls.
  if (payload_type.has_value()) {
    last_payload_type_ = *payload_type;
  }
  last_rtp_timestamp_ = rtp_timestamp;
  if (!capture_time.has_value()) {
    // We don't currently get a capture time from VoiceEngine.
    last_frame_capture_time_ = clock_->CurrentTime();
  } else {
    last_frame_capture_time_ = *capture_time;
  }
}

void RTCPSender::SetRtpClockRate(int8_t payload_type, int rtp_clock_rate_hz) {
  MutexLock lock(&mutex_rtcp_sender_);
  rtp_clock_rates_khz_[payload_type] = rtp_clock_rate_hz / 1000;
}

uint32_t RTCPSender::SSRC() const {
  MutexLock lock(&mutex_rtcp_sender_);
  return ssrc_;
}

void RTCPSender::SetSsrc(uint32_t ssrc) {
  MutexLock lock(&mutex_rtcp_sender_);
  ssrc_ = ssrc;
}

void RTCPSender::SetRemoteSSRC(uint32_t ssrc) {
  MutexLock lock(&mutex_rtcp_sender_);
  remote_ssrc_ = ssrc;
}

int32_t RTCPSender::SetCNAME(absl::string_view c_name) {
  RTC_DCHECK_LT(c_name.size(), RTCP_CNAME_SIZE);
  MutexLock lock(&mutex_rtcp_sender_);
  cname_ = std::string(c_name);
  return 0;
}

bool RTCPSender::TimeToSendRTCPReport(bool send_keyframe_before_rtp) const {
  Timestamp now = clock_->CurrentTime();

  MutexLock lock(&mutex_rtcp_sender_);
  RTC_DCHECK(
      (method_ == RtcpMode::kOff && !next_time_to_send_rtcp_.has_value()) ||
      (method_ != RtcpMode::kOff && next_time_to_send_rtcp_.has_value()));
  if (method_ == RtcpMode::kOff)
    return false;

  if (!audio_ && send_keyframe_before_rtp) {
    // For video key-frames we want to send the RTCP before the large key-frame
    // if we have a 100 ms margin
    now += TimeDelta::Millis(100);
  }

  return now >= *next_time_to_send_rtcp_;
}

void RTCPSender::BuildSR(const RtcpContext& ctx, PacketSender& sender) {
  // Timestamp shouldn't be estimated before first media frame.
  RTC_DCHECK(last_frame_capture_time_.has_value());
  // The timestamp of this RTCP packet should be estimated as the timestamp of
  // the frame being captured at this moment. We are calculating that
  // timestamp as the last frame's timestamp + the time since the last frame
  // was captured.
  int rtp_rate = rtp_clock_rates_khz_[last_payload_type_];
  if (rtp_rate <= 0) {
    rtp_rate =
        (audio_ ? kBogusRtpRateForAudioRtcp : kVideoPayloadTypeFrequency) /
        1000;
  }
  // Round now_us_ to the closest millisecond, because Ntp time is rounded
  // when converted to milliseconds,
  uint32_t rtp_timestamp =
      timestamp_offset_ + last_rtp_timestamp_ +
      ((ctx.now_.us() + 500) / 1000 - last_frame_capture_time_->ms()) *
          rtp_rate;

  rtcp::SenderReport report;
  report.SetSenderSsrc(ssrc_);
  report.SetNtp(clock_->ConvertTimestampToNtpTime(ctx.now_));
  report.SetRtpTimestamp(rtp_timestamp);
  report.SetPacketCount(ctx.feedback_state_.packets_sent);
  report.SetOctetCount(ctx.feedback_state_.media_bytes_sent);
  report.SetReportBlocks(CreateReportBlocks(ctx.feedback_state_));
  sender.AppendPacket(report);
}

void RTCPSender::BuildSDES(const RtcpContext& ctx, PacketSender& sender) {
  size_t length_cname = cname_.length();
  RTC_CHECK_LT(length_cname, RTCP_CNAME_SIZE);

  rtcp::Sdes sdes;
  sdes.AddCName(ssrc_, cname_);
  sender.AppendPacket(sdes);
}

void RTCPSender::BuildRR(const RtcpContext& ctx, PacketSender& sender) {
  rtcp::ReceiverReport report;
  report.SetSenderSsrc(ssrc_);
  report.SetReportBlocks(CreateReportBlocks(ctx.feedback_state_));
  if (method_ == RtcpMode::kCompound || !report.report_blocks().empty()) {
    sender.AppendPacket(report);
  }
}

void RTCPSender::BuildPLI(const RtcpContext& ctx, PacketSender& sender) {
  rtcp::Pli pli;
  pli.SetSenderSsrc(ssrc_);
  pli.SetMediaSsrc(remote_ssrc_);

  ++packet_type_counter_.pli_packets;
  sender.AppendPacket(pli);
}

void RTCPSender::BuildFIR(const RtcpContext& ctx, PacketSender& sender) {
  ++sequence_number_fir_;

  rtcp::Fir fir;
  fir.SetSenderSsrc(ssrc_);
  fir.AddRequestTo(remote_ssrc_, sequence_number_fir_);

  ++packet_type_counter_.fir_packets;
  sender.AppendPacket(fir);
}

void RTCPSender::BuildREMB(const RtcpContext& ctx, PacketSender& sender) {
  rtcp::Remb remb;
  remb.SetSenderSsrc(ssrc_);
  remb.SetBitrateBps(remb_bitrate_);
  remb.SetSsrcs(remb_ssrcs_);
  sender.AppendPacket(remb);
}

void RTCPSender::SetTargetBitrate(unsigned int target_bitrate) {
  MutexLock lock(&mutex_rtcp_sender_);
  tmmbr_send_bps_ = target_bitrate;
}

void RTCPSender::BuildTMMBR(const RtcpContext& ctx, PacketSender& sender) {
  if (ctx.feedback_state_.receiver == nullptr)
    return;
  // Before sending the TMMBR check the received TMMBN, only an owner is
  // allowed to raise the bitrate:
  // * If the sender is an owner of the TMMBN -> send TMMBR
  // * If not an owner but the TMMBR would enter the TMMBN -> send TMMBR

  // get current bounding set from RTCP receiver
  bool tmmbr_owner = false;

  // holding mutex_rtcp_sender_ while calling RTCPreceiver which
  // will accuire criticalSectionRTCPReceiver_ is a potental deadlock but
  // since RTCPreceiver is not doing the reverse we should be fine
  std::vector<rtcp::TmmbItem> candidates =
      ctx.feedback_state_.receiver->BoundingSet(&tmmbr_owner);

  if (!candidates.empty()) {
    for (const auto& candidate : candidates) {
      if (candidate.bitrate_bps() == tmmbr_send_bps_ &&
          candidate.packet_overhead() == packet_oh_send_) {
        // Do not send the same tuple.
        return;
      }
    }
    if (!tmmbr_owner) {
      // Use received bounding set as candidate set.
      // Add current tuple.
      candidates.emplace_back(ssrc_, tmmbr_send_bps_, packet_oh_send_);

      // Find bounding set.
      std::vector<rtcp::TmmbItem> bounding =
          TMMBRHelp::FindBoundingSet(std::move(candidates));
      tmmbr_owner = TMMBRHelp::IsOwner(bounding, ssrc_);
      if (!tmmbr_owner) {
        // Did not enter bounding set, no meaning to send this request.
        return;
      }
    }
  }

  if (!tmmbr_send_bps_)
    return;

  rtcp::Tmmbr tmmbr;
  tmmbr.SetSenderSsrc(ssrc_);
  rtcp::TmmbItem request;
  request.set_ssrc(remote_ssrc_);
  request.set_bitrate_bps(tmmbr_send_bps_);
  request.set_packet_overhead(packet_oh_send_);
  tmmbr.AddTmmbr(request);
  sender.AppendPacket(tmmbr);
}

void RTCPSender::BuildTMMBN(const RtcpContext& ctx, PacketSender& sender) {
  rtcp::Tmmbn tmmbn;
  tmmbn.SetSenderSsrc(ssrc_);
  for (const rtcp::TmmbItem& tmmbr : tmmbn_to_send_) {
    if (tmmbr.bitrate_bps() > 0) {
      tmmbn.AddTmmbr(tmmbr);
    }
  }
  sender.AppendPacket(tmmbn);
}

void RTCPSender::BuildAPP(const RtcpContext& ctx, PacketSender& sender) {
  rtcp::App app;
  app.SetSenderSsrc(ssrc_);
  sender.AppendPacket(app);
}

void RTCPSender::BuildLossNotification(const RtcpContext& ctx,
                                       PacketSender& sender) {
  loss_notification_.SetSenderSsrc(ssrc_);
  loss_notification_.SetMediaSsrc(remote_ssrc_);
  sender.AppendPacket(loss_notification_);
}

void RTCPSender::BuildNACK(const RtcpContext& ctx, PacketSender& sender) {
  rtcp::Nack nack;
  nack.SetSenderSsrc(ssrc_);
  nack.SetMediaSsrc(remote_ssrc_);
  nack.SetPacketIds(ctx.nack_list_, ctx.nack_size_);

  // Report stats.
  for (int idx = 0; idx < ctx.nack_size_; ++idx) {
    nack_stats_.ReportRequest(ctx.nack_list_[idx]);
  }
  packet_type_counter_.nack_requests = nack_stats_.requests();
  packet_type_counter_.unique_nack_requests = nack_stats_.unique_requests();

  ++packet_type_counter_.nack_packets;
  sender.AppendPacket(nack);
}

void RTCPSender::BuildBYE(const RtcpContext& ctx, PacketSender& sender) {
  rtcp::Bye bye;
  bye.SetSenderSsrc(ssrc_);
  bye.SetCsrcs(csrcs_);
  sender.AppendPacket(bye);
}

void RTCPSender::BuildExtendedReports(const RtcpContext& ctx,
                                      PacketSender& sender) {
  rtcp::ExtendedReports xr;
  xr.SetSenderSsrc(ssrc_);

  if (!sending_ && xr_send_receiver_reference_time_enabled_) {
    rtcp::Rrtr rrtr;
    rrtr.SetNtp(clock_->ConvertTimestampToNtpTime(ctx.now_));
    xr.SetRrtr(rrtr);
  }

  for (const rtcp::ReceiveTimeInfo& rti : ctx.feedback_state_.last_xr_rtis) {
    xr.AddDlrrItem(rti);
  }

  if (send_video_bitrate_allocation_) {
    rtcp::TargetBitrate target_bitrate;

    for (int sl = 0; sl < kMaxSpatialLayers; ++sl) {
      for (int tl = 0; tl < kMaxTemporalStreams; ++tl) {
        if (video_bitrate_allocation_.HasBitrate(sl, tl)) {
          target_bitrate.AddTargetBitrate(
              sl, tl, video_bitrate_allocation_.GetBitrate(sl, tl) / 1000);
        }
      }
    }

    xr.SetTargetBitrate(target_bitrate);
    send_video_bitrate_allocation_ = false;
  }
  sender.AppendPacket(xr);
}

int32_t RTCPSender::SendRTCP(const FeedbackState& feedback_state,
                             RTCPPacketType packet_type,
                             int32_t nack_size,
                             const uint16_t* nack_list) {
  int32_t error_code = -1;
  auto callback = [&](rtc::ArrayView<const uint8_t> packet) {
    if (transport_->SendRtcp(packet)) {
      error_code = 0;
      if (event_log_) {
        event_log_->Log(std::make_unique<RtcEventRtcpPacketOutgoing>(packet));
      }
    }
  };
  absl::optional<PacketSender> sender;
  {
    MutexLock lock(&mutex_rtcp_sender_);
    sender.emplace(callback, max_packet_size_);
    auto result = ComputeCompoundRTCPPacket(feedback_state, packet_type,
                                            nack_size, nack_list, *sender);
    if (result) {
      return *result;
    }
  }
  sender->Send();

  return error_code;
}

absl::optional<int32_t> RTCPSender::ComputeCompoundRTCPPacket(
    const FeedbackState& feedback_state,
    RTCPPacketType packet_type,
    int32_t nack_size,
    const uint16_t* nack_list,
    PacketSender& sender) {
  if (method_ == RtcpMode::kOff) {
    RTC_LOG(LS_WARNING) << "Can't send RTCP if it is disabled.";
    return -1;
  }
  // Add the flag as volatile. Non volatile entries will not be overwritten.
  // The new volatile flag will be consumed by the end of this call.
  SetFlag(packet_type, true);

  // Prevent sending streams to send SR before any media has been sent.
  const bool can_calculate_rtp_timestamp = last_frame_capture_time_.has_value();
  if (!can_calculate_rtp_timestamp) {
    bool consumed_sr_flag = ConsumeFlag(kRtcpSr);
    bool consumed_report_flag = sending_ && ConsumeFlag(kRtcpReport);
    bool sender_report = consumed_report_flag || consumed_sr_flag;
    if (sender_report && AllVolatileFlagsConsumed()) {
      // This call was for Sender Report and nothing else.
      return 0;
    }
    if (sending_ && method_ == RtcpMode::kCompound) {
      // Not allowed to send any RTCP packet without sender report.
      return -1;
    }
  }

  // We need to send our NTP even if we haven't received any reports.
  RtcpContext context(feedback_state, nack_size, nack_list,
                      clock_->CurrentTime());

  PrepareReport(feedback_state);

  bool create_bye = false;

  auto it = report_flags_.begin();
  while (it != report_flags_.end()) {
    uint32_t rtcp_packet_type = it->type;

    if (it->is_volatile) {
      report_flags_.erase(it++);
    } else {
      ++it;
    }

    // If there is a BYE, don't append now - save it and append it
    // at the end later.
    if (rtcp_packet_type == kRtcpBye) {
      create_bye = true;
      continue;
    }
    auto builder_it = builders_.find(rtcp_packet_type);
    if (builder_it == builders_.end()) {
      RTC_DCHECK_NOTREACHED()
          << "Could not find builder for packet type " << rtcp_packet_type;
    } else {
      BuilderFunc func = builder_it->second;
      (this->*func)(context, sender);
    }
  }

  // Append the BYE now at the end
  if (create_bye) {
    BuildBYE(context, sender);
  }

  if (packet_type_counter_observer_ != nullptr) {
    packet_type_counter_observer_->RtcpPacketTypesCounterUpdated(
        remote_ssrc_, packet_type_counter_);
  }

  RTC_DCHECK(AllVolatileFlagsConsumed());
  return absl::nullopt;
}

TimeDelta RTCPSender::ComputeTimeUntilNextReport(DataRate send_bitrate) {
  /*
      For audio we use a configurable interval (default: 5 seconds)

      For video we use a configurable interval (default: 1 second)
          for a BW smaller than ~200 kbit/s, technicaly we break the max 5% RTCP
          BW for video but that should be extremely rare

  From RFC 3550, https://www.rfc-editor.org/rfc/rfc3550#section-6.2

      The RECOMMENDED value for the reduced minimum in seconds is 360
        divided by the session bandwidth in kilobits/second.  This minimum
        is smaller than 5 seconds for bandwidths greater than 72 kb/s.

      The interval between RTCP packets is varied randomly over the
        range [0.5,1.5] times the calculated interval to avoid unintended
        synchronization of all participants
  */

  TimeDelta min_interval = report_interval_;

  if (!audio_ && sending_ && send_bitrate > DataRate::BitsPerSec(72'000)) {
    // Calculate bandwidth for video; 360 / send bandwidth in kbit/s per
    // https://www.rfc-editor.org/rfc/rfc3550#section-6.2 recommendation.
    min_interval = std::min(TimeDelta::Seconds(360) / send_bitrate.kbps(),
                            report_interval_);
  }

  // The interval between RTCP packets is varied randomly over the
  // range [1/2,3/2] times the calculated interval.
  int min_interval_int = rtc::dchecked_cast<int>(min_interval.ms());
  TimeDelta time_to_next = TimeDelta::Millis(
      random_.Rand(min_interval_int * 1 / 2, min_interval_int * 3 / 2));

  // To be safer clamp the result.
  return std::max(time_to_next, TimeDelta::Millis(1));
}

void RTCPSender::PrepareReport(const FeedbackState& feedback_state) {
  bool generate_report;
  if (IsFlagPresent(kRtcpSr) || IsFlagPresent(kRtcpRr)) {
    // Report type already explicitly set, don't automatically populate.
    generate_report = true;
    RTC_DCHECK(ConsumeFlag(kRtcpReport) == false);
  } else {
    generate_report =
        (ConsumeFlag(kRtcpReport) && method_ == RtcpMode::kReducedSize) ||
        method_ == RtcpMode::kCompound;
    if (generate_report)
      SetFlag(sending_ ? kRtcpSr : kRtcpRr, true);
  }

  if (IsFlagPresent(kRtcpSr) || (IsFlagPresent(kRtcpRr) && !cname_.empty()))
    SetFlag(kRtcpSdes, true);

  if (generate_report) {
    if ((!sending_ && xr_send_receiver_reference_time_enabled_) ||
        !feedback_state.last_xr_rtis.empty() ||
        send_video_bitrate_allocation_) {
      SetFlag(kRtcpAnyExtendedReports, true);
    }

    SetNextRtcpSendEvaluationDuration(
        ComputeTimeUntilNextReport(feedback_state.send_bitrate));

    // RtcpSender expected to be used for sending either just sender reports
    // or just receiver reports.
    RTC_DCHECK(!(IsFlagPresent(kRtcpSr) && IsFlagPresent(kRtcpRr)));
  }
}

std::vector<rtcp::ReportBlock> RTCPSender::CreateReportBlocks(
    const FeedbackState& feedback_state) {
  std::vector<rtcp::ReportBlock> result;
  if (!receive_statistics_)
    return result;

  result = receive_statistics_->RtcpReportBlocks(RTCP_MAX_REPORT_BLOCKS);

  if (!result.empty() && feedback_state.last_rr.Valid()) {
    // Get our NTP as late as possible to avoid a race.
    uint32_t now = CompactNtp(clock_->CurrentNtpTime());
    uint32_t receive_time = CompactNtp(feedback_state.last_rr);
    uint32_t delay_since_last_sr = now - receive_time;

    for (auto& report_block : result) {
      report_block.SetLastSr(feedback_state.remote_sr);
      report_block.SetDelayLastSr(delay_since_last_sr);
    }
  }
  return result;
}

void RTCPSender::SetCsrcs(const std::vector<uint32_t>& csrcs) {
  RTC_DCHECK_LE(csrcs.size(), kRtpCsrcSize);
  MutexLock lock(&mutex_rtcp_sender_);
  csrcs_ = csrcs;
}

void RTCPSender::SetTmmbn(std::vector<rtcp::TmmbItem> bounding_set) {
  MutexLock lock(&mutex_rtcp_sender_);
  tmmbn_to_send_ = std::move(bounding_set);
  SetFlag(kRtcpTmmbn, true);
}

void RTCPSender::SetFlag(uint32_t type, bool is_volatile) {
  if (type & kRtcpAnyExtendedReports) {
    report_flags_.insert(ReportFlag(kRtcpAnyExtendedReports, is_volatile));
  } else {
    report_flags_.insert(ReportFlag(type, is_volatile));
  }
}

bool RTCPSender::IsFlagPresent(uint32_t type) const {
  return report_flags_.find(ReportFlag(type, false)) != report_flags_.end();
}

bool RTCPSender::ConsumeFlag(uint32_t type, bool forced) {
  auto it = report_flags_.find(ReportFlag(type, false));
  if (it == report_flags_.end())
    return false;
  if (it->is_volatile || forced)
    report_flags_.erase((it));
  return true;
}

bool RTCPSender::AllVolatileFlagsConsumed() const {
  for (const ReportFlag& flag : report_flags_) {
    if (flag.is_volatile)
      return false;
  }
  return true;
}

void RTCPSender::SetVideoBitrateAllocation(
    const VideoBitrateAllocation& bitrate) {
  MutexLock lock(&mutex_rtcp_sender_);
  if (method_ == RtcpMode::kOff) {
    RTC_LOG(LS_WARNING) << "Can't send RTCP if it is disabled.";
    return;
  }
  // Check if this allocation is first ever, or has a different set of
  // spatial/temporal layers signaled and enabled, if so trigger an rtcp report
  // as soon as possible.
  absl::optional<VideoBitrateAllocation> new_bitrate =
      CheckAndUpdateLayerStructure(bitrate);
  if (new_bitrate) {
    video_bitrate_allocation_ = *new_bitrate;
    RTC_LOG(LS_INFO) << "Emitting TargetBitrate XR for SSRC " << ssrc_
                     << " with new layers enabled/disabled: "
                     << video_bitrate_allocation_.ToString();
    SetNextRtcpSendEvaluationDuration(TimeDelta::Zero());
  } else {
    video_bitrate_allocation_ = bitrate;
  }

  send_video_bitrate_allocation_ = true;
  SetFlag(kRtcpAnyExtendedReports, true);
}

absl::optional<VideoBitrateAllocation> RTCPSender::CheckAndUpdateLayerStructure(
    const VideoBitrateAllocation& bitrate) const {
  absl::optional<VideoBitrateAllocation> updated_bitrate;
  for (size_t si = 0; si < kMaxSpatialLayers; ++si) {
    for (size_t ti = 0; ti < kMaxTemporalStreams; ++ti) {
      if (!updated_bitrate &&
          (bitrate.HasBitrate(si, ti) !=
               video_bitrate_allocation_.HasBitrate(si, ti) ||
           (bitrate.GetBitrate(si, ti) == 0) !=
               (video_bitrate_allocation_.GetBitrate(si, ti) == 0))) {
        updated_bitrate = bitrate;
      }
      if (video_bitrate_allocation_.GetBitrate(si, ti) > 0 &&
          bitrate.GetBitrate(si, ti) == 0) {
        // Make sure this stream disabling is explicitly signaled.
        updated_bitrate->SetBitrate(si, ti, 0);
      }
    }
  }

  return updated_bitrate;
}

void RTCPSender::SendCombinedRtcpPacket(
    std::vector<std::unique_ptr<rtcp::RtcpPacket>> rtcp_packets) {
  size_t max_packet_size;
  uint32_t ssrc;
  {
    MutexLock lock(&mutex_rtcp_sender_);
    if (method_ == RtcpMode::kOff) {
      RTC_LOG(LS_WARNING) << "Can't send RTCP if it is disabled.";
      return;
    }

    max_packet_size = max_packet_size_;
    ssrc = ssrc_;
  }
  RTC_DCHECK_LE(max_packet_size, IP_PACKET_SIZE);
  auto callback = [&](rtc::ArrayView<const uint8_t> packet) {
    if (transport_->SendRtcp(packet)) {
      if (event_log_)
        event_log_->Log(std::make_unique<RtcEventRtcpPacketOutgoing>(packet));
    }
  };
  PacketSender sender(callback, max_packet_size);
  for (auto& rtcp_packet : rtcp_packets) {
    rtcp_packet->SetSenderSsrc(ssrc);
    sender.AppendPacket(*rtcp_packet);
  }
  sender.Send();
}

void RTCPSender::SetNextRtcpSendEvaluationDuration(TimeDelta duration) {
  next_time_to_send_rtcp_ = clock_->CurrentTime() + duration;
  // TODO(bugs.webrtc.org/11581): make unconditional once downstream consumers
  // are using the callback method.
  if (schedule_next_rtcp_send_evaluation_function_)
    schedule_next_rtcp_send_evaluation_function_(duration);
}

}  // namespace webrtc

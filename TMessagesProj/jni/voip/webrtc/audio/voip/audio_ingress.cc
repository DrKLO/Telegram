/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "audio/voip/audio_ingress.h"

#include <algorithm>
#include <utility>
#include <vector>

#include "api/audio_codecs/audio_format.h"
#include "audio/utility/audio_frame_operations.h"
#include "modules/audio_coding/include/audio_coding_module.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtcp_packet/common_header.h"
#include "modules/rtp_rtcp/source/rtcp_packet/receiver_report.h"
#include "modules/rtp_rtcp/source/rtcp_packet/sender_report.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

namespace {

acm2::AcmReceiver::Config CreateAcmConfig(
    rtc::scoped_refptr<AudioDecoderFactory> decoder_factory) {
  acm2::AcmReceiver::Config acm_config;
  acm_config.neteq_config.enable_muted_state = true;
  acm_config.decoder_factory = decoder_factory;
  return acm_config;
}

}  // namespace

AudioIngress::AudioIngress(
    RtpRtcpInterface* rtp_rtcp,
    Clock* clock,
    ReceiveStatistics* receive_statistics,
    rtc::scoped_refptr<AudioDecoderFactory> decoder_factory)
    : playing_(false),
      remote_ssrc_(0),
      first_rtp_timestamp_(-1),
      rtp_receive_statistics_(receive_statistics),
      rtp_rtcp_(rtp_rtcp),
      acm_receiver_(CreateAcmConfig(decoder_factory)),
      ntp_estimator_(clock) {}

AudioIngress::~AudioIngress() = default;

AudioMixer::Source::AudioFrameInfo AudioIngress::GetAudioFrameWithInfo(
    int sampling_rate,
    AudioFrame* audio_frame) {
  audio_frame->sample_rate_hz_ = sampling_rate;

  // Get 10ms raw PCM data from the ACM.
  bool muted = false;
  if (acm_receiver_.GetAudio(sampling_rate, audio_frame, &muted) == -1) {
    RTC_DLOG(LS_ERROR) << "GetAudio() failed!";
    // In all likelihood, the audio in this frame is garbage. We return an
    // error so that the audio mixer module doesn't add it to the mix. As
    // a result, it won't be played out and the actions skipped here are
    // irrelevant.
    return AudioMixer::Source::AudioFrameInfo::kError;
  }

  if (muted) {
    AudioFrameOperations::Mute(audio_frame);
  }

  // Measure audio level.
  constexpr double kAudioSampleDurationSeconds = 0.01;
  output_audio_level_.ComputeLevel(*audio_frame, kAudioSampleDurationSeconds);

  // If caller invoked StopPlay(), then mute the frame.
  if (!playing_) {
    AudioFrameOperations::Mute(audio_frame);
    muted = true;
  }

  // Set first rtp timestamp with first audio frame with valid timestamp.
  if (first_rtp_timestamp_ < 0 && audio_frame->timestamp_ != 0) {
    first_rtp_timestamp_ = audio_frame->timestamp_;
  }

  if (first_rtp_timestamp_ >= 0) {
    // Compute elapsed and NTP times.
    int64_t unwrap_timestamp;
    {
      MutexLock lock(&lock_);
      unwrap_timestamp =
          timestamp_wrap_handler_.Unwrap(audio_frame->timestamp_);
      audio_frame->ntp_time_ms_ =
          ntp_estimator_.Estimate(audio_frame->timestamp_);
    }
    // For clock rate, default to the playout sampling rate if we haven't
    // received any packets yet.
    absl::optional<std::pair<int, SdpAudioFormat>> decoder =
        acm_receiver_.LastDecoder();
    int clock_rate = decoder ? decoder->second.clockrate_hz
                             : acm_receiver_.last_output_sample_rate_hz();
    RTC_DCHECK_GT(clock_rate, 0);
    audio_frame->elapsed_time_ms_ =
        (unwrap_timestamp - first_rtp_timestamp_) / (clock_rate / 1000);
  }

  return muted ? AudioMixer::Source::AudioFrameInfo::kMuted
               : AudioMixer::Source::AudioFrameInfo::kNormal;
}

bool AudioIngress::StartPlay() {
  {
    MutexLock lock(&lock_);
    if (receive_codec_info_.empty()) {
      RTC_DLOG(LS_WARNING) << "Receive codecs have not been set yet";
      return false;
    }
  }
  playing_ = true;
  return true;
}

void AudioIngress::SetReceiveCodecs(
    const std::map<int, SdpAudioFormat>& codecs) {
  {
    MutexLock lock(&lock_);
    for (const auto& kv : codecs) {
      receive_codec_info_[kv.first] = kv.second.clockrate_hz;
    }
  }
  acm_receiver_.SetCodecs(codecs);
}

void AudioIngress::ReceivedRTPPacket(rtc::ArrayView<const uint8_t> rtp_packet) {
  RtpPacketReceived rtp_packet_received;
  rtp_packet_received.Parse(rtp_packet.data(), rtp_packet.size());

  // Set payload type's sampling rate before we feed it into ReceiveStatistics.
  {
    MutexLock lock(&lock_);
    const auto& it =
        receive_codec_info_.find(rtp_packet_received.PayloadType());
    // If sampling rate info is not available in our received codec set, it
    // would mean that remote media endpoint is sending incorrect payload id
    // which can't be processed correctly especially on payload type id in
    // dynamic range.
    if (it == receive_codec_info_.end()) {
      RTC_DLOG(LS_WARNING) << "Unexpected payload id received: "
                           << rtp_packet_received.PayloadType();
      return;
    }
    rtp_packet_received.set_payload_type_frequency(it->second);
  }

  // Track current remote SSRC.
  if (rtp_packet_received.Ssrc() != remote_ssrc_) {
    rtp_rtcp_->SetRemoteSSRC(rtp_packet_received.Ssrc());
    remote_ssrc_.store(rtp_packet_received.Ssrc());
  }

  rtp_receive_statistics_->OnRtpPacket(rtp_packet_received);

  RTPHeader header;
  rtp_packet_received.GetHeader(&header);

  size_t packet_length = rtp_packet_received.size();
  if (packet_length < header.headerLength ||
      (packet_length - header.headerLength) < header.paddingLength) {
    RTC_DLOG(LS_ERROR) << "Packet length(" << packet_length << ") header("
                       << header.headerLength << ") padding("
                       << header.paddingLength << ")";
    return;
  }

  const uint8_t* payload = rtp_packet_received.data() + header.headerLength;
  size_t payload_length = packet_length - header.headerLength;
  size_t payload_data_length = payload_length - header.paddingLength;
  auto data_view = rtc::ArrayView<const uint8_t>(payload, payload_data_length);

  // Push the incoming payload (parsed and ready for decoding) into the ACM.
  if (acm_receiver_.InsertPacket(header, data_view) != 0) {
    RTC_DLOG(LS_ERROR) << "AudioIngress::ReceivedRTPPacket() unable to "
                          "push data to the ACM";
  }
}

void AudioIngress::ReceivedRTCPPacket(
    rtc::ArrayView<const uint8_t> rtcp_packet) {
  rtcp::CommonHeader rtcp_header;
  if (rtcp_header.Parse(rtcp_packet.data(), rtcp_packet.size()) &&
      (rtcp_header.type() == rtcp::SenderReport::kPacketType ||
       rtcp_header.type() == rtcp::ReceiverReport::kPacketType)) {
    RTC_DCHECK_GE(rtcp_packet.size(), 8);

    uint32_t sender_ssrc =
        ByteReader<uint32_t>::ReadBigEndian(rtcp_packet.data() + 4);

    // If we don't have remote ssrc at this point, it's likely that remote
    // endpoint is receive-only or it could have restarted the media.
    if (sender_ssrc != remote_ssrc_) {
      rtp_rtcp_->SetRemoteSSRC(sender_ssrc);
      remote_ssrc_.store(sender_ssrc);
    }
  }

  // Deliver RTCP packet to RTP/RTCP module for parsing and processing.
  rtp_rtcp_->IncomingRtcpPacket(rtcp_packet);

  absl::optional<TimeDelta> rtt = rtp_rtcp_->LastRtt();
  if (!rtt.has_value()) {
    // Waiting for valid RTT.
    return;
  }

  absl::optional<RtpRtcpInterface::SenderReportStats> last_sr =
      rtp_rtcp_->GetSenderReportStats();
  if (!last_sr.has_value()) {
    // Waiting for RTCP.
    return;
  }

  {
    MutexLock lock(&lock_);
    ntp_estimator_.UpdateRtcpTimestamp(*rtt, last_sr->last_remote_timestamp,
                                       last_sr->last_remote_rtp_timestamp);
  }
}

ChannelStatistics AudioIngress::GetChannelStatistics() {
  ChannelStatistics channel_stats;

  // Get clockrate for current decoder ahead of jitter calculation.
  uint32_t clockrate_hz = 0;
  absl::optional<std::pair<int, SdpAudioFormat>> decoder =
      acm_receiver_.LastDecoder();
  if (decoder) {
    clockrate_hz = decoder->second.clockrate_hz;
  }

  StreamStatistician* statistician =
      rtp_receive_statistics_->GetStatistician(remote_ssrc_);
  if (statistician) {
    RtpReceiveStats stats = statistician->GetStats();
    channel_stats.packets_lost = stats.packets_lost;
    channel_stats.packets_received = stats.packet_counter.packets;
    channel_stats.bytes_received = stats.packet_counter.payload_bytes;
    channel_stats.remote_ssrc = remote_ssrc_;
    if (clockrate_hz > 0) {
      channel_stats.jitter = static_cast<double>(stats.jitter) / clockrate_hz;
    }
  }

  // Get RTCP report using remote SSRC.
  const std::vector<ReportBlockData>& report_data =
      rtp_rtcp_->GetLatestReportBlockData();
  for (const ReportBlockData& rtcp_report : report_data) {
    if (rtp_rtcp_->SSRC() != rtcp_report.source_ssrc() ||
        remote_ssrc_ != rtcp_report.sender_ssrc()) {
      continue;
    }
    RemoteRtcpStatistics remote_stat;
    remote_stat.packets_lost = rtcp_report.cumulative_lost();
    remote_stat.fraction_lost = rtcp_report.fraction_lost();
    if (clockrate_hz > 0) {
      remote_stat.jitter = rtcp_report.jitter(clockrate_hz).seconds<double>();
    }
    if (rtcp_report.has_rtt()) {
      remote_stat.round_trip_time = rtcp_report.last_rtt().seconds<double>();
    }
    remote_stat.last_report_received_timestamp_ms =
        rtcp_report.report_block_timestamp_utc().ms();
    channel_stats.remote_rtcp = remote_stat;

    // Receive only channel won't send any RTP packets.
    if (!channel_stats.remote_ssrc.has_value()) {
      channel_stats.remote_ssrc = remote_ssrc_;
    }
    break;
  }

  return channel_stats;
}

}  // namespace webrtc

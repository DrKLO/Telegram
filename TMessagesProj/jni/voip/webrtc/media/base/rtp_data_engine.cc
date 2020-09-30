/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/base/rtp_data_engine.h"

#include <map>

#include "absl/strings/match.h"
#include "media/base/codec.h"
#include "media/base/media_constants.h"
#include "media/base/rtp_utils.h"
#include "media/base/stream_params.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/data_rate_limiter.h"
#include "rtc_base/helpers.h"
#include "rtc_base/logging.h"
#include "rtc_base/sanitizer.h"

namespace cricket {

// We want to avoid IP fragmentation.
static const size_t kDataMaxRtpPacketLen = 1200U;
// We reserve space after the RTP header for future wiggle room.
static const unsigned char kReservedSpace[] = {0x00, 0x00, 0x00, 0x00};

// Amount of overhead SRTP may take.  We need to leave room in the
// buffer for it, otherwise SRTP will fail later.  If SRTP ever uses
// more than this, we need to increase this number.
static const size_t kMaxSrtpHmacOverhead = 16;

RtpDataEngine::RtpDataEngine() {
  data_codecs_.push_back(
      DataCodec(kGoogleRtpDataCodecPlType, kGoogleRtpDataCodecName));
}

DataMediaChannel* RtpDataEngine::CreateChannel(const MediaConfig& config) {
  return new RtpDataMediaChannel(config);
}

static const DataCodec* FindCodecByName(const std::vector<DataCodec>& codecs,
                                        const std::string& name) {
  for (const DataCodec& codec : codecs) {
    if (absl::EqualsIgnoreCase(name, codec.name))
      return &codec;
  }
  return nullptr;
}

RtpDataMediaChannel::RtpDataMediaChannel(const MediaConfig& config)
    : DataMediaChannel(config) {
  Construct();
  SetPreferredDscp(rtc::DSCP_AF41);
}

void RtpDataMediaChannel::Construct() {
  sending_ = false;
  receiving_ = false;
  send_limiter_.reset(new rtc::DataRateLimiter(kDataMaxBandwidth / 8, 1.0));
}

RtpDataMediaChannel::~RtpDataMediaChannel() {
  std::map<uint32_t, RtpClock*>::const_iterator iter;
  for (iter = rtp_clock_by_send_ssrc_.begin();
       iter != rtp_clock_by_send_ssrc_.end(); ++iter) {
    delete iter->second;
  }
}

void RTC_NO_SANITIZE("float-cast-overflow")  // bugs.webrtc.org/8204
    RtpClock::Tick(double now, int* seq_num, uint32_t* timestamp) {
  *seq_num = ++last_seq_num_;
  *timestamp = timestamp_offset_ + static_cast<uint32_t>(now * clockrate_);
  // UBSan: 5.92374e+10 is outside the range of representable values of type
  // 'unsigned int'
}

const DataCodec* FindUnknownCodec(const std::vector<DataCodec>& codecs) {
  DataCodec data_codec(kGoogleRtpDataCodecPlType, kGoogleRtpDataCodecName);
  std::vector<DataCodec>::const_iterator iter;
  for (iter = codecs.begin(); iter != codecs.end(); ++iter) {
    if (!iter->Matches(data_codec)) {
      return &(*iter);
    }
  }
  return NULL;
}

const DataCodec* FindKnownCodec(const std::vector<DataCodec>& codecs) {
  DataCodec data_codec(kGoogleRtpDataCodecPlType, kGoogleRtpDataCodecName);
  std::vector<DataCodec>::const_iterator iter;
  for (iter = codecs.begin(); iter != codecs.end(); ++iter) {
    if (iter->Matches(data_codec)) {
      return &(*iter);
    }
  }
  return NULL;
}

bool RtpDataMediaChannel::SetRecvCodecs(const std::vector<DataCodec>& codecs) {
  const DataCodec* unknown_codec = FindUnknownCodec(codecs);
  if (unknown_codec) {
    RTC_LOG(LS_WARNING) << "Failed to SetRecvCodecs because of unknown codec: "
                        << unknown_codec->ToString();
    return false;
  }

  recv_codecs_ = codecs;
  return true;
}

bool RtpDataMediaChannel::SetSendCodecs(const std::vector<DataCodec>& codecs) {
  const DataCodec* known_codec = FindKnownCodec(codecs);
  if (!known_codec) {
    RTC_LOG(LS_WARNING)
        << "Failed to SetSendCodecs because there is no known codec.";
    return false;
  }

  send_codecs_ = codecs;
  return true;
}

bool RtpDataMediaChannel::SetSendParameters(const DataSendParameters& params) {
  return (SetSendCodecs(params.codecs) &&
          SetMaxSendBandwidth(params.max_bandwidth_bps));
}

bool RtpDataMediaChannel::SetRecvParameters(const DataRecvParameters& params) {
  return SetRecvCodecs(params.codecs);
}

bool RtpDataMediaChannel::AddSendStream(const StreamParams& stream) {
  if (!stream.has_ssrcs()) {
    return false;
  }

  if (GetStreamBySsrc(send_streams_, stream.first_ssrc())) {
    RTC_LOG(LS_WARNING) << "Not adding data send stream '" << stream.id
                        << "' with ssrc=" << stream.first_ssrc()
                        << " because stream already exists.";
    return false;
  }

  send_streams_.push_back(stream);
  // TODO(pthatcher): This should be per-stream, not per-ssrc.
  // And we should probably allow more than one per stream.
  rtp_clock_by_send_ssrc_[stream.first_ssrc()] =
      new RtpClock(kDataCodecClockrate, rtc::CreateRandomNonZeroId(),
                   rtc::CreateRandomNonZeroId());

  RTC_LOG(LS_INFO) << "Added data send stream '" << stream.id
                   << "' with ssrc=" << stream.first_ssrc();
  return true;
}

bool RtpDataMediaChannel::RemoveSendStream(uint32_t ssrc) {
  if (!GetStreamBySsrc(send_streams_, ssrc)) {
    return false;
  }

  RemoveStreamBySsrc(&send_streams_, ssrc);
  delete rtp_clock_by_send_ssrc_[ssrc];
  rtp_clock_by_send_ssrc_.erase(ssrc);
  return true;
}

bool RtpDataMediaChannel::AddRecvStream(const StreamParams& stream) {
  if (!stream.has_ssrcs()) {
    return false;
  }

  if (GetStreamBySsrc(recv_streams_, stream.first_ssrc())) {
    RTC_LOG(LS_WARNING) << "Not adding data recv stream '" << stream.id
                        << "' with ssrc=" << stream.first_ssrc()
                        << " because stream already exists.";
    return false;
  }

  recv_streams_.push_back(stream);
  RTC_LOG(LS_INFO) << "Added data recv stream '" << stream.id
                   << "' with ssrc=" << stream.first_ssrc();
  return true;
}

bool RtpDataMediaChannel::RemoveRecvStream(uint32_t ssrc) {
  RemoveStreamBySsrc(&recv_streams_, ssrc);
  return true;
}

// Not implemented.
void RtpDataMediaChannel::ResetUnsignaledRecvStream() {}

void RtpDataMediaChannel::OnPacketReceived(rtc::CopyOnWriteBuffer packet,
                                           int64_t /* packet_time_us */) {
  RtpHeader header;
  if (!GetRtpHeader(packet.cdata(), packet.size(), &header)) {
    return;
  }

  size_t header_length;
  if (!GetRtpHeaderLen(packet.cdata(), packet.size(), &header_length)) {
    return;
  }
  const char* data =
      packet.cdata<char>() + header_length + sizeof(kReservedSpace);
  size_t data_len = packet.size() - header_length - sizeof(kReservedSpace);

  if (!receiving_) {
    RTC_LOG(LS_WARNING) << "Not receiving packet " << header.ssrc << ":"
                        << header.seq_num << " before SetReceive(true) called.";
    return;
  }

  if (!FindCodecById(recv_codecs_, header.payload_type)) {
    return;
  }

  if (!GetStreamBySsrc(recv_streams_, header.ssrc)) {
    RTC_LOG(LS_WARNING) << "Received packet for unknown ssrc: " << header.ssrc;
    return;
  }

  // Uncomment this for easy debugging.
  // const auto* found_stream = GetStreamBySsrc(recv_streams_, header.ssrc);
  // RTC_LOG(LS_INFO) << "Received packet"
  //              << " groupid=" << found_stream.groupid
  //              << ", ssrc=" << header.ssrc
  //              << ", seqnum=" << header.seq_num
  //              << ", timestamp=" << header.timestamp
  //              << ", len=" << data_len;

  ReceiveDataParams params;
  params.ssrc = header.ssrc;
  params.seq_num = header.seq_num;
  params.timestamp = header.timestamp;
  SignalDataReceived(params, data, data_len);
}

bool RtpDataMediaChannel::SetMaxSendBandwidth(int bps) {
  if (bps <= 0) {
    bps = kDataMaxBandwidth;
  }
  send_limiter_.reset(new rtc::DataRateLimiter(bps / 8, 1.0));
  RTC_LOG(LS_INFO) << "RtpDataMediaChannel::SetSendBandwidth to " << bps
                   << "bps.";
  return true;
}

bool RtpDataMediaChannel::SendData(const SendDataParams& params,
                                   const rtc::CopyOnWriteBuffer& payload,
                                   SendDataResult* result) {
  if (result) {
    // If we return true, we'll set this to SDR_SUCCESS.
    *result = SDR_ERROR;
  }
  if (!sending_) {
    RTC_LOG(LS_WARNING) << "Not sending packet with ssrc=" << params.ssrc
                        << " len=" << payload.size()
                        << " before SetSend(true).";
    return false;
  }

  if (params.type != cricket::DMT_TEXT) {
    RTC_LOG(LS_WARNING)
        << "Not sending data because binary type is unsupported.";
    return false;
  }

  const StreamParams* found_stream =
      GetStreamBySsrc(send_streams_, params.ssrc);
  if (!found_stream) {
    RTC_LOG(LS_WARNING) << "Not sending data because ssrc is unknown: "
                        << params.ssrc;
    return false;
  }

  const DataCodec* found_codec =
      FindCodecByName(send_codecs_, kGoogleRtpDataCodecName);
  if (!found_codec) {
    RTC_LOG(LS_WARNING) << "Not sending data because codec is unknown: "
                        << kGoogleRtpDataCodecName;
    return false;
  }

  size_t packet_len = (kMinRtpPacketLen + sizeof(kReservedSpace) +
                       payload.size() + kMaxSrtpHmacOverhead);
  if (packet_len > kDataMaxRtpPacketLen) {
    return false;
  }

  double now =
      rtc::TimeMicros() / static_cast<double>(rtc::kNumMicrosecsPerSec);

  if (!send_limiter_->CanUse(packet_len, now)) {
    RTC_LOG(LS_VERBOSE) << "Dropped data packet of len=" << packet_len
                        << "; already sent " << send_limiter_->used_in_period()
                        << "/" << send_limiter_->max_per_period();
    return false;
  }

  RtpHeader header;
  header.payload_type = found_codec->id;
  header.ssrc = params.ssrc;
  rtp_clock_by_send_ssrc_[header.ssrc]->Tick(now, &header.seq_num,
                                             &header.timestamp);

  rtc::CopyOnWriteBuffer packet(kMinRtpPacketLen, packet_len);
  if (!SetRtpHeader(packet.data(), packet.size(), header)) {
    return false;
  }
  packet.AppendData(kReservedSpace);
  packet.AppendData(payload);

  RTC_LOG(LS_VERBOSE) << "Sent RTP data packet: "
                         " stream="
                      << found_stream->id << " ssrc=" << header.ssrc
                      << ", seqnum=" << header.seq_num
                      << ", timestamp=" << header.timestamp
                      << ", len=" << payload.size();

  rtc::PacketOptions options;
  options.info_signaled_after_sent.packet_type = rtc::PacketType::kData;
  MediaChannel::SendPacket(&packet, options);
  send_limiter_->Use(packet_len, now);
  if (result) {
    *result = SDR_SUCCESS;
  }
  return true;
}

}  // namespace cricket

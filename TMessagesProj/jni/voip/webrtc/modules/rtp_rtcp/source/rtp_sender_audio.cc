/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_sender_audio.h"

#include <string.h>

#include <memory>
#include <utility>
#include <vector>

#include "absl/strings/match.h"
#include "absl/types/optional.h"
#include "api/audio_codecs/audio_format.h"
#include "api/rtp_headers.h"
#include "modules/audio_coding/include/audio_coding_module_typedefs.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/absolute_capture_time_sender.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "modules/rtp_rtcp/source/rtp_packet.h"
#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "modules/rtp_rtcp/source/time_util.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/ntp_time.h"

namespace webrtc {

namespace {
[[maybe_unused]] const char* FrameTypeToString(AudioFrameType frame_type) {
  switch (frame_type) {
    case AudioFrameType::kEmptyFrame:
      return "empty";
    case AudioFrameType::kAudioFrameSpeech:
      return "audio_speech";
    case AudioFrameType::kAudioFrameCN:
      return "audio_cn";
  }
  RTC_CHECK_NOTREACHED();
}

}  // namespace

RTPSenderAudio::RTPSenderAudio(Clock* clock, RTPSender* rtp_sender)
    : clock_(clock),
      rtp_sender_(rtp_sender),
      absolute_capture_time_sender_(clock) {
  RTC_DCHECK(clock_);
}

RTPSenderAudio::~RTPSenderAudio() {}

int32_t RTPSenderAudio::RegisterAudioPayload(absl::string_view payload_name,
                                             const int8_t payload_type,
                                             const uint32_t frequency,
                                             const size_t channels,
                                             const uint32_t rate) {
  if (absl::EqualsIgnoreCase(payload_name, "cn")) {
    MutexLock lock(&send_audio_mutex_);
    //  we can have multiple CNG payload types
    switch (frequency) {
      case 8000:
        cngnb_payload_type_ = payload_type;
        break;
      case 16000:
        cngwb_payload_type_ = payload_type;
        break;
      case 32000:
        cngswb_payload_type_ = payload_type;
        break;
      case 48000:
        cngfb_payload_type_ = payload_type;
        break;
      default:
        return -1;
    }
  } else if (absl::EqualsIgnoreCase(payload_name, "telephone-event")) {
    MutexLock lock(&send_audio_mutex_);
    // Don't add it to the list
    // we dont want to allow send with a DTMF payloadtype
    dtmf_payload_type_ = payload_type;
    dtmf_payload_freq_ = frequency;
    return 0;
  } else if (payload_name == "audio") {
    MutexLock lock(&send_audio_mutex_);
    encoder_rtp_timestamp_frequency_ = rtc::dchecked_cast<int>(frequency);
    return 0;
  }
  return 0;
}

bool RTPSenderAudio::MarkerBit(AudioFrameType frame_type, int8_t payload_type) {
  MutexLock lock(&send_audio_mutex_);
  // for audio true for first packet in a speech burst
  bool marker_bit = false;
  if (last_payload_type_ != payload_type) {
    if (payload_type != -1 && (cngnb_payload_type_ == payload_type ||
                               cngwb_payload_type_ == payload_type ||
                               cngswb_payload_type_ == payload_type ||
                               cngfb_payload_type_ == payload_type)) {
      // Only set a marker bit when we change payload type to a non CNG
      return false;
    }

    // payload_type differ
    if (last_payload_type_ == -1) {
      if (frame_type != AudioFrameType::kAudioFrameCN) {
        // first packet and NOT CNG
        return true;
      } else {
        // first packet and CNG
        inband_vad_active_ = true;
        return false;
      }
    }

    // not first packet AND
    // not CNG AND
    // payload_type changed

    // set a marker bit when we change payload type
    marker_bit = true;
  }

  // For G.723 G.729, AMR etc we can have inband VAD
  if (frame_type == AudioFrameType::kAudioFrameCN) {
    inband_vad_active_ = true;
  } else if (inband_vad_active_) {
    inband_vad_active_ = false;
    marker_bit = true;
  }
  return marker_bit;
}

bool RTPSenderAudio::SendAudio(const RtpAudioFrame& frame) {
  RTC_DCHECK_GE(frame.payload_id, 0);
  RTC_DCHECK_LE(frame.payload_id, 127);
  TRACE_EVENT_ASYNC_STEP1("webrtc", "Audio", frame.rtp_timestamp, "Send",
                          "type", FrameTypeToString(frame.type));

  // From RFC 4733:
  // A source has wide latitude as to how often it sends event updates. A
  // natural interval is the spacing between non-event audio packets. [...]
  // Alternatively, a source MAY decide to use a different spacing for event
  // updates, with a value of 50 ms RECOMMENDED.
  constexpr int kDtmfIntervalTimeMs = 50;
  uint32_t dtmf_payload_freq = 0;
  absl::optional<AbsoluteCaptureTime> absolute_capture_time;
  {
    MutexLock lock(&send_audio_mutex_);
    dtmf_payload_freq = dtmf_payload_freq_;
    if (frame.capture_time.has_value()) {
      // Send absolute capture time periodically in order to optimize and save
      // network traffic. Missing absolute capture times can be interpolated on
      // the receiving end if sending intervals are small enough.
      absolute_capture_time = absolute_capture_time_sender_.OnSendPacket(
          rtp_sender_->SSRC(), frame.rtp_timestamp,
          // Replace missing value with 0 (invalid frequency), this will trigger
          // absolute capture time sending.
          encoder_rtp_timestamp_frequency_.value_or(0),
          clock_->ConvertTimestampToNtpTime(*frame.capture_time),
          /*estimated_capture_clock_offset=*/0);
    }
  }

  // Check if we have pending DTMFs to send
  if (!dtmf_event_is_on_ && dtmf_queue_.PendingDtmf()) {
    if ((clock_->TimeInMilliseconds() - dtmf_time_last_sent_) >
        kDtmfIntervalTimeMs) {
      // New tone to play
      dtmf_timestamp_ = frame.rtp_timestamp;
      if (dtmf_queue_.NextDtmf(&dtmf_current_event_)) {
        dtmf_event_first_packet_sent_ = false;
        dtmf_length_samples_ =
            dtmf_current_event_.duration_ms * (dtmf_payload_freq / 1000);
        dtmf_event_is_on_ = true;
      }
    }
  }

  // A source MAY send events and coded audio packets for the same time
  // but we don't support it
  if (dtmf_event_is_on_) {
    if (frame.type == AudioFrameType::kEmptyFrame) {
      // kEmptyFrame is used to drive the DTMF when in CN mode
      // it can be triggered more frequently than we want to send the
      // DTMF packets.
      const unsigned int dtmf_interval_time_rtp =
          dtmf_payload_freq * kDtmfIntervalTimeMs / 1000;
      if ((frame.rtp_timestamp - dtmf_timestamp_last_sent_) <
          dtmf_interval_time_rtp) {
        // not time to send yet
        return true;
      }
    }
    dtmf_timestamp_last_sent_ = frame.rtp_timestamp;
    uint32_t dtmf_duration_samples = frame.rtp_timestamp - dtmf_timestamp_;
    bool ended = false;
    bool send = true;

    if (dtmf_length_samples_ > dtmf_duration_samples) {
      if (dtmf_duration_samples <= 0) {
        // Skip send packet at start, since we shouldn't use duration 0
        send = false;
      }
    } else {
      ended = true;
      dtmf_event_is_on_ = false;
      dtmf_time_last_sent_ = clock_->TimeInMilliseconds();
    }
    if (send) {
      if (dtmf_duration_samples > 0xffff) {
        // RFC 4733 2.5.2.3 Long-Duration Events
        SendTelephoneEventPacket(ended, dtmf_timestamp_,
                                 static_cast<uint16_t>(0xffff), false);

        // set new timestap for this segment
        dtmf_timestamp_ = frame.rtp_timestamp;
        dtmf_duration_samples -= 0xffff;
        dtmf_length_samples_ -= 0xffff;

        return SendTelephoneEventPacket(
            ended, dtmf_timestamp_,
            static_cast<uint16_t>(dtmf_duration_samples), false);
      } else {
        if (!SendTelephoneEventPacket(ended, dtmf_timestamp_,
                                      dtmf_duration_samples,
                                      !dtmf_event_first_packet_sent_)) {
          return false;
        }
        dtmf_event_first_packet_sent_ = true;
        return true;
      }
    }
    return true;
  }
  if (frame.payload.empty()) {
    if (frame.type == AudioFrameType::kEmptyFrame) {
      // we don't send empty audio RTP packets
      // no error since we use it to either drive DTMF when we use VAD, or
      // enter DTX.
      return true;
    }
    return false;
  }

  std::unique_ptr<RtpPacketToSend> packet =
      rtp_sender_->AllocatePacket(frame.csrcs);
  packet->SetMarker(MarkerBit(frame.type, frame.payload_id));
  packet->SetPayloadType(frame.payload_id);
  packet->SetTimestamp(frame.rtp_timestamp);
  packet->set_capture_time(clock_->CurrentTime());
  // Set audio level extension, if included.
  packet->SetExtension<AudioLevel>(
      frame.type == AudioFrameType::kAudioFrameSpeech,
      frame.audio_level_dbov.value_or(127));

  if (absolute_capture_time.has_value()) {
    // It also checks that extension was registered during SDP negotiation. If
    // not then setter won't do anything.
    packet->SetExtension<AbsoluteCaptureTimeExtension>(*absolute_capture_time);
  }

  uint8_t* payload = packet->AllocatePayload(frame.payload.size());
  RTC_CHECK(payload);
  memcpy(payload, frame.payload.data(), frame.payload.size());

  {
    MutexLock lock(&send_audio_mutex_);
    last_payload_type_ = frame.payload_id;
  }
  TRACE_EVENT_ASYNC_END2("webrtc", "Audio", frame.rtp_timestamp, "timestamp",
                         packet->Timestamp(), "seqnum",
                         packet->SequenceNumber());
  packet->set_packet_type(RtpPacketMediaType::kAudio);
  packet->set_allow_retransmission(true);
  std::vector<std::unique_ptr<RtpPacketToSend>> packets(1);
  packets[0] = std::move(packet);
  rtp_sender_->EnqueuePackets(std::move(packets));
  if (first_packet_sent_()) {
    RTC_LOG(LS_INFO) << "First audio RTP packet sent to pacer";
  }
  return true;
}

// Send a TelephoneEvent tone using RFC 2833 (4733)
int32_t RTPSenderAudio::SendTelephoneEvent(uint8_t key,
                                           uint16_t time_ms,
                                           uint8_t level) {
  DtmfQueue::Event event;
  {
    MutexLock lock(&send_audio_mutex_);
    if (dtmf_payload_type_ < 0) {
      // TelephoneEvent payloadtype not configured
      return -1;
    }
    event.payload_type = dtmf_payload_type_;
  }
  event.key = key;
  event.duration_ms = time_ms;
  event.level = level;
  return dtmf_queue_.AddDtmf(event) ? 0 : -1;
}

bool RTPSenderAudio::SendTelephoneEventPacket(bool ended,
                                              uint32_t dtmf_timestamp,
                                              uint16_t duration,
                                              bool marker_bit) {
  size_t send_count = ended ? 3 : 1;

  std::vector<std::unique_ptr<RtpPacketToSend>> packets;
  packets.reserve(send_count);
  for (size_t i = 0; i < send_count; ++i) {
    // Send DTMF data.
    constexpr RtpPacketToSend::ExtensionManager* kNoExtensions = nullptr;
    constexpr size_t kDtmfSize = 4;
    auto packet = std::make_unique<RtpPacketToSend>(kNoExtensions,
                                                    kRtpHeaderSize + kDtmfSize);
    packet->SetPayloadType(dtmf_current_event_.payload_type);
    packet->SetMarker(marker_bit);
    packet->SetSsrc(rtp_sender_->SSRC());
    packet->SetTimestamp(dtmf_timestamp);
    packet->set_capture_time(clock_->CurrentTime());

    // Create DTMF data.
    uint8_t* dtmfbuffer = packet->AllocatePayload(kDtmfSize);
    RTC_DCHECK(dtmfbuffer);
    /*    From RFC 2833:
     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |     event     |E|R| volume    |          duration             |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    */
    // R bit always cleared
    uint8_t R = 0x00;
    uint8_t volume = dtmf_current_event_.level;

    // First packet un-ended
    uint8_t E = ended ? 0x80 : 0x00;

    // First byte is Event number, equals key number
    dtmfbuffer[0] = dtmf_current_event_.key;
    dtmfbuffer[1] = E | R | volume;
    ByteWriter<uint16_t>::WriteBigEndian(dtmfbuffer + 2, duration);

    packet->set_packet_type(RtpPacketMediaType::kAudio);
    packet->set_allow_retransmission(true);
    packets.push_back(std::move(packet));
  }
  rtp_sender_->EnqueuePackets(std::move(packets));
  return true;
}
}  // namespace webrtc

/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "audio/voip/audio_egress.h"

#include <utility>
#include <vector>

#include "rtc_base/logging.h"

namespace webrtc {

AudioEgress::AudioEgress(RtpRtcpInterface* rtp_rtcp,
                         Clock* clock,
                         TaskQueueFactory* task_queue_factory)
    : rtp_rtcp_(rtp_rtcp),
      rtp_sender_audio_(clock, rtp_rtcp_->RtpSender()),
      audio_coding_(AudioCodingModule::Create(AudioCodingModule::Config())),
      encoder_queue_(task_queue_factory->CreateTaskQueue(
          "AudioEncoder",
          TaskQueueFactory::Priority::NORMAL)) {
  audio_coding_->RegisterTransportCallback(this);
}

AudioEgress::~AudioEgress() {
  audio_coding_->RegisterTransportCallback(nullptr);
}

bool AudioEgress::IsSending() const {
  return rtp_rtcp_->SendingMedia();
}

void AudioEgress::SetEncoder(int payload_type,
                             const SdpAudioFormat& encoder_format,
                             std::unique_ptr<AudioEncoder> encoder) {
  RTC_DCHECK_GE(payload_type, 0);
  RTC_DCHECK_LE(payload_type, 127);

  SetEncoderFormat(encoder_format);

  // The RTP/RTCP module needs to know the RTP timestamp rate (i.e. clockrate)
  // as well as some other things, so we collect this info and send it along.
  rtp_rtcp_->RegisterSendPayloadFrequency(payload_type,
                                          encoder->RtpTimestampRateHz());
  rtp_sender_audio_.RegisterAudioPayload("audio", payload_type,
                                         encoder->RtpTimestampRateHz(),
                                         encoder->NumChannels(), 0);

  audio_coding_->SetEncoder(std::move(encoder));
}

bool AudioEgress::StartSend() {
  if (!GetEncoderFormat()) {
    RTC_DLOG(LS_WARNING) << "Send codec has not been set yet";
    return false;
  }
  rtp_rtcp_->SetSendingMediaStatus(true);
  return true;
}

void AudioEgress::StopSend() {
  rtp_rtcp_->SetSendingMediaStatus(false);
}

void AudioEgress::SendAudioData(std::unique_ptr<AudioFrame> audio_frame) {
  RTC_DCHECK_GT(audio_frame->samples_per_channel_, 0);
  RTC_DCHECK_LE(audio_frame->num_channels_, 8);

  encoder_queue_.PostTask(
      [this, audio_frame = std::move(audio_frame)]() mutable {
        RTC_DCHECK_RUN_ON(&encoder_queue_);
        if (!rtp_rtcp_->SendingMedia()) {
          return;
        }

        double duration_seconds =
            static_cast<double>(audio_frame->samples_per_channel_) /
            audio_frame->sample_rate_hz_;

        input_audio_level_.ComputeLevel(*audio_frame, duration_seconds);

        AudioFrameOperations::Mute(audio_frame.get(),
                                   encoder_context_.previously_muted_,
                                   encoder_context_.mute_);
        encoder_context_.previously_muted_ = encoder_context_.mute_;

        audio_frame->timestamp_ = encoder_context_.frame_rtp_timestamp_;

        // This call will trigger AudioPacketizationCallback::SendData if
        // encoding is done and payload is ready for packetization and
        // transmission. Otherwise, it will return without invoking the
        // callback.
        if (audio_coding_->Add10MsData(*audio_frame) < 0) {
          RTC_DLOG(LS_ERROR) << "ACM::Add10MsData() failed.";
          return;
        }

        encoder_context_.frame_rtp_timestamp_ +=
            rtc::dchecked_cast<uint32_t>(audio_frame->samples_per_channel_);
      });
}

int32_t AudioEgress::SendData(AudioFrameType frame_type,
                              uint8_t payload_type,
                              uint32_t timestamp,
                              const uint8_t* payload_data,
                              size_t payload_size) {
  RTC_DCHECK_RUN_ON(&encoder_queue_);

  rtc::ArrayView<const uint8_t> payload(payload_data, payload_size);

  // Currently we don't get a capture time from downstream modules (ADM,
  // AudioTransportImpl).
  // TODO(natim@webrtc.org): Integrate once it's ready.
  constexpr uint32_t kUndefinedCaptureTime = -1;

  // Push data from ACM to RTP/RTCP-module to deliver audio frame for
  // packetization.
  if (!rtp_rtcp_->OnSendingRtpFrame(timestamp, kUndefinedCaptureTime,
                                    payload_type,
                                    /*force_sender_report=*/false)) {
    return -1;
  }

  const uint32_t rtp_timestamp = timestamp + rtp_rtcp_->StartTimestamp();

  // This call will trigger Transport::SendPacket() from the RTP/RTCP module.
  if (!rtp_sender_audio_.SendAudio(frame_type, payload_type, rtp_timestamp,
                                   payload.data(), payload.size())) {
    RTC_DLOG(LS_ERROR)
        << "AudioEgress::SendData() failed to send data to RTP/RTCP module";
    return -1;
  }

  return 0;
}

void AudioEgress::RegisterTelephoneEventType(int rtp_payload_type,
                                             int sample_rate_hz) {
  RTC_DCHECK_GE(rtp_payload_type, 0);
  RTC_DCHECK_LE(rtp_payload_type, 127);

  rtp_rtcp_->RegisterSendPayloadFrequency(rtp_payload_type, sample_rate_hz);
  rtp_sender_audio_.RegisterAudioPayload("telephone-event", rtp_payload_type,
                                         sample_rate_hz, 0, 0);
}

bool AudioEgress::SendTelephoneEvent(int dtmf_event, int duration_ms) {
  RTC_DCHECK_GE(dtmf_event, 0);
  RTC_DCHECK_LE(dtmf_event, 255);
  RTC_DCHECK_GE(duration_ms, 0);
  RTC_DCHECK_LE(duration_ms, 65535);

  if (!IsSending()) {
    return false;
  }

  constexpr int kTelephoneEventAttenuationdB = 10;

  if (rtp_sender_audio_.SendTelephoneEvent(dtmf_event, duration_ms,
                                           kTelephoneEventAttenuationdB) != 0) {
    RTC_DLOG(LS_ERROR) << "SendTelephoneEvent() failed to send event";
    return false;
  }
  return true;
}

void AudioEgress::SetMute(bool mute) {
  encoder_queue_.PostTask([this, mute] {
    RTC_DCHECK_RUN_ON(&encoder_queue_);
    encoder_context_.mute_ = mute;
  });
}

}  // namespace webrtc

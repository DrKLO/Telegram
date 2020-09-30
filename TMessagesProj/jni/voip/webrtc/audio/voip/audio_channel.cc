/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "audio/voip/audio_channel.h"

#include <utility>
#include <vector>

#include "api/audio_codecs/audio_format.h"
#include "api/task_queue/task_queue_factory.h"
#include "modules/rtp_rtcp/include/receive_statistics.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_impl2.h"
#include "rtc_base/location.h"
#include "rtc_base/logging.h"

namespace webrtc {

namespace {

constexpr int kRtcpReportIntervalMs = 5000;

}  // namespace

AudioChannel::AudioChannel(
    Transport* transport,
    uint32_t local_ssrc,
    TaskQueueFactory* task_queue_factory,
    ProcessThread* process_thread,
    AudioMixer* audio_mixer,
    rtc::scoped_refptr<AudioDecoderFactory> decoder_factory)
    : audio_mixer_(audio_mixer), process_thread_(process_thread) {
  RTC_DCHECK(task_queue_factory);
  RTC_DCHECK(process_thread);
  RTC_DCHECK(audio_mixer);

  Clock* clock = Clock::GetRealTimeClock();
  receive_statistics_ = ReceiveStatistics::Create(clock);

  RtpRtcpInterface::Configuration rtp_config;
  rtp_config.clock = clock;
  rtp_config.audio = true;
  rtp_config.receive_statistics = receive_statistics_.get();
  rtp_config.rtcp_report_interval_ms = kRtcpReportIntervalMs;
  rtp_config.outgoing_transport = transport;
  rtp_config.local_media_ssrc = local_ssrc;

  rtp_rtcp_ = ModuleRtpRtcpImpl2::Create(rtp_config);

  rtp_rtcp_->SetSendingMediaStatus(false);
  rtp_rtcp_->SetRTCPStatus(RtcpMode::kCompound);

  // ProcessThread periodically services RTP stack for RTCP.
  process_thread_->RegisterModule(rtp_rtcp_.get(), RTC_FROM_HERE);

  ingress_ = std::make_unique<AudioIngress>(rtp_rtcp_.get(), clock,
                                            receive_statistics_.get(),
                                            std::move(decoder_factory));
  egress_ =
      std::make_unique<AudioEgress>(rtp_rtcp_.get(), clock, task_queue_factory);

  // Set the instance of audio ingress to be part of audio mixer for ADM to
  // fetch audio samples to play.
  audio_mixer_->AddSource(ingress_.get());
}

AudioChannel::~AudioChannel() {
  if (egress_->IsSending()) {
    StopSend();
  }
  if (ingress_->IsPlaying()) {
    StopPlay();
  }

  audio_mixer_->RemoveSource(ingress_.get());
  process_thread_->DeRegisterModule(rtp_rtcp_.get());
}

bool AudioChannel::StartSend() {
  // If encoder has not been set, return false.
  if (!egress_->StartSend()) {
    return false;
  }

  // Start sending with RTP stack if it has not been sending yet.
  if (!rtp_rtcp_->Sending()) {
    rtp_rtcp_->SetSendingStatus(true);
  }
  return true;
}

void AudioChannel::StopSend() {
  egress_->StopSend();

  // Deactivate RTP stack when both sending and receiving are stopped.
  // SetSendingStatus(false) triggers the transmission of RTCP BYE
  // message to remote endpoint.
  if (!ingress_->IsPlaying() && rtp_rtcp_->Sending()) {
    rtp_rtcp_->SetSendingStatus(false);
  }
}

bool AudioChannel::StartPlay() {
  // If decoders have not been set, return false.
  if (!ingress_->StartPlay()) {
    return false;
  }

  // If RTP stack is not sending then start sending as in recv-only mode, RTCP
  // receiver report is expected.
  if (!rtp_rtcp_->Sending()) {
    rtp_rtcp_->SetSendingStatus(true);
  }
  return true;
}

void AudioChannel::StopPlay() {
  ingress_->StopPlay();

  // Deactivate RTP stack only when both sending and receiving are stopped.
  if (!rtp_rtcp_->SendingMedia() && rtp_rtcp_->Sending()) {
    rtp_rtcp_->SetSendingStatus(false);
  }
}

}  // namespace webrtc

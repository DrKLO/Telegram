/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/audio_rtp_receiver.h"

#include <stddef.h>

#include <string>
#include <utility>
#include <vector>

#include "api/sequence_checker.h"
#include "pc/audio_track.h"
#include "pc/media_stream_track_proxy.h"
#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/task_utils/to_queued_task.h"

namespace webrtc {

AudioRtpReceiver::AudioRtpReceiver(
    rtc::Thread* worker_thread,
    std::string receiver_id,
    std::vector<std::string> stream_ids,
    bool is_unified_plan,
    cricket::VoiceMediaChannel* voice_channel /*= nullptr*/)
    : AudioRtpReceiver(worker_thread,
                       receiver_id,
                       CreateStreamsFromIds(std::move(stream_ids)),
                       is_unified_plan,
                       voice_channel) {}

AudioRtpReceiver::AudioRtpReceiver(
    rtc::Thread* worker_thread,
    const std::string& receiver_id,
    const std::vector<rtc::scoped_refptr<MediaStreamInterface>>& streams,
    bool is_unified_plan,
    cricket::VoiceMediaChannel* voice_channel /*= nullptr*/)
    : worker_thread_(worker_thread),
      id_(receiver_id),
      source_(rtc::make_ref_counted<RemoteAudioSource>(
          worker_thread,
          is_unified_plan
              ? RemoteAudioSource::OnAudioChannelGoneAction::kSurvive
              : RemoteAudioSource::OnAudioChannelGoneAction::kEnd)),
      track_(AudioTrackProxyWithInternal<AudioTrack>::Create(
          rtc::Thread::Current(),
          AudioTrack::Create(receiver_id, source_))),
      media_channel_(voice_channel),
      cached_track_enabled_(track_->internal()->enabled()),
      attachment_id_(GenerateUniqueId()),
      worker_thread_safety_(PendingTaskSafetyFlag::CreateDetachedInactive()) {
  RTC_DCHECK(worker_thread_);
  RTC_DCHECK(track_->GetSource()->remote());
  track_->RegisterObserver(this);
  track_->GetSource()->RegisterAudioObserver(this);
  SetStreams(streams);
}

AudioRtpReceiver::~AudioRtpReceiver() {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  RTC_DCHECK(!media_channel_);

  track_->GetSource()->UnregisterAudioObserver(this);
  track_->UnregisterObserver(this);
}

void AudioRtpReceiver::OnChanged() {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  const bool enabled = track_->internal()->enabled();
  if (cached_track_enabled_ == enabled)
    return;
  cached_track_enabled_ = enabled;
  worker_thread_->PostTask(
      ToQueuedTask(worker_thread_safety_, [this, enabled]() {
        RTC_DCHECK_RUN_ON(worker_thread_);
        Reconfigure(enabled);
      }));
}

// RTC_RUN_ON(worker_thread_)
void AudioRtpReceiver::SetOutputVolume_w(double volume) {
  RTC_DCHECK_GE(volume, 0.0);
  RTC_DCHECK_LE(volume, 10.0);

  if (!media_channel_)
    return;

  ssrc_ ? media_channel_->SetOutputVolume(*ssrc_, volume)
        : media_channel_->SetDefaultOutputVolume(volume);
}

void AudioRtpReceiver::OnSetVolume(double volume) {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  RTC_DCHECK_GE(volume, 0);
  RTC_DCHECK_LE(volume, 10);

  bool track_enabled = track_->internal()->enabled();
  worker_thread_->Invoke<void>(RTC_FROM_HERE, [&]() {
    RTC_DCHECK_RUN_ON(worker_thread_);
    // Update the cached_volume_ even when stopped, to allow clients to set
    // the volume before starting/restarting, eg see crbug.com/1272566.
    cached_volume_ = volume;
    // When the track is disabled, the volume of the source, which is the
    // corresponding WebRtc Voice Engine channel will be 0. So we do not
    // allow setting the volume to the source when the track is disabled.
    if (track_enabled)
      SetOutputVolume_w(volume);
  });
}

rtc::scoped_refptr<DtlsTransportInterface> AudioRtpReceiver::dtls_transport()
    const {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  return dtls_transport_;
}

std::vector<std::string> AudioRtpReceiver::stream_ids() const {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  std::vector<std::string> stream_ids(streams_.size());
  for (size_t i = 0; i < streams_.size(); ++i)
    stream_ids[i] = streams_[i]->id();
  return stream_ids;
}

std::vector<rtc::scoped_refptr<MediaStreamInterface>>
AudioRtpReceiver::streams() const {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  return streams_;
}

RtpParameters AudioRtpReceiver::GetParameters() const {
  RTC_DCHECK_RUN_ON(worker_thread_);
  if (!media_channel_)
    return RtpParameters();
  return ssrc_ ? media_channel_->GetRtpReceiveParameters(*ssrc_)
               : media_channel_->GetDefaultRtpReceiveParameters();
}

void AudioRtpReceiver::SetFrameDecryptor(
    rtc::scoped_refptr<FrameDecryptorInterface> frame_decryptor) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  frame_decryptor_ = std::move(frame_decryptor);
  // Special Case: Set the frame decryptor to any value on any existing channel.
  if (media_channel_ && ssrc_) {
    media_channel_->SetFrameDecryptor(*ssrc_, frame_decryptor_);
  }
}

rtc::scoped_refptr<FrameDecryptorInterface>
AudioRtpReceiver::GetFrameDecryptor() const {
  RTC_DCHECK_RUN_ON(worker_thread_);
  return frame_decryptor_;
}

void AudioRtpReceiver::Stop() {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  source_->SetState(MediaSourceInterface::kEnded);
  track_->internal()->set_ended();
}

void AudioRtpReceiver::SetSourceEnded() {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  source_->SetState(MediaSourceInterface::kEnded);
}

// RTC_RUN_ON(&signaling_thread_checker_)
void AudioRtpReceiver::RestartMediaChannel(absl::optional<uint32_t> ssrc) {
  bool enabled = track_->internal()->enabled();
  MediaSourceInterface::SourceState state = source_->state();
  worker_thread_->Invoke<void>(RTC_FROM_HERE, [&]() {
    RTC_DCHECK_RUN_ON(worker_thread_);
    RestartMediaChannel_w(std::move(ssrc), enabled, state);
  });
  source_->SetState(MediaSourceInterface::kLive);
}

// RTC_RUN_ON(worker_thread_)
void AudioRtpReceiver::RestartMediaChannel_w(
    absl::optional<uint32_t> ssrc,
    bool track_enabled,
    MediaSourceInterface::SourceState state) {
  if (!media_channel_)
    return;  // Can't restart.

  if (state != MediaSourceInterface::kInitializing) {
    if (ssrc_ == ssrc)
      return;
    source_->Stop(media_channel_, ssrc_);
  }

  ssrc_ = std::move(ssrc);
  source_->Start(media_channel_, ssrc_);
  if (ssrc_) {
    media_channel_->SetBaseMinimumPlayoutDelayMs(*ssrc_, delay_.GetMs());
  }

  Reconfigure(track_enabled);
}

void AudioRtpReceiver::SetupMediaChannel(uint32_t ssrc) {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  RestartMediaChannel(ssrc);
}

void AudioRtpReceiver::SetupUnsignaledMediaChannel() {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  RestartMediaChannel(absl::nullopt);
}

uint32_t AudioRtpReceiver::ssrc() const {
  RTC_DCHECK_RUN_ON(worker_thread_);
  return ssrc_.value_or(0);
}

void AudioRtpReceiver::set_stream_ids(std::vector<std::string> stream_ids) {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  SetStreams(CreateStreamsFromIds(std::move(stream_ids)));
}

void AudioRtpReceiver::set_transport(
    rtc::scoped_refptr<DtlsTransportInterface> dtls_transport) {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  dtls_transport_ = std::move(dtls_transport);
}

void AudioRtpReceiver::SetStreams(
    const std::vector<rtc::scoped_refptr<MediaStreamInterface>>& streams) {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  // Remove remote track from any streams that are going away.
  for (const auto& existing_stream : streams_) {
    bool removed = true;
    for (const auto& stream : streams) {
      if (existing_stream->id() == stream->id()) {
        RTC_DCHECK_EQ(existing_stream.get(), stream.get());
        removed = false;
        break;
      }
    }
    if (removed) {
      existing_stream->RemoveTrack(track_);
    }
  }
  // Add remote track to any streams that are new.
  for (const auto& stream : streams) {
    bool added = true;
    for (const auto& existing_stream : streams_) {
      if (stream->id() == existing_stream->id()) {
        RTC_DCHECK_EQ(stream.get(), existing_stream.get());
        added = false;
        break;
      }
    }
    if (added) {
      stream->AddTrack(track_);
    }
  }
  streams_ = streams;
}

std::vector<RtpSource> AudioRtpReceiver::GetSources() const {
  RTC_DCHECK_RUN_ON(worker_thread_);
  if (!media_channel_ || !ssrc_) {
    return {};
  }
  return media_channel_->GetSources(*ssrc_);
}

void AudioRtpReceiver::SetDepacketizerToDecoderFrameTransformer(
    rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  if (media_channel_) {
    media_channel_->SetDepacketizerToDecoderFrameTransformer(ssrc_.value_or(0),
                                                             frame_transformer);
  }
  frame_transformer_ = std::move(frame_transformer);
}

// RTC_RUN_ON(worker_thread_)
void AudioRtpReceiver::Reconfigure(bool track_enabled) {
  RTC_DCHECK(media_channel_);

  SetOutputVolume_w(track_enabled ? cached_volume_ : 0);

  if (ssrc_ && frame_decryptor_) {
    // Reattach the frame decryptor if we were reconfigured.
    media_channel_->SetFrameDecryptor(*ssrc_, frame_decryptor_);
  }

  if (frame_transformer_) {
    media_channel_->SetDepacketizerToDecoderFrameTransformer(
        ssrc_.value_or(0), frame_transformer_);
  }
}

void AudioRtpReceiver::SetObserver(RtpReceiverObserverInterface* observer) {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  observer_ = observer;
  // Deliver any notifications the observer may have missed by being set late.
  if (received_first_packet_ && observer_) {
    observer_->OnFirstPacketReceived(media_type());
  }
}

void AudioRtpReceiver::SetJitterBufferMinimumDelay(
    absl::optional<double> delay_seconds) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  delay_.Set(delay_seconds);
  if (media_channel_ && ssrc_)
    media_channel_->SetBaseMinimumPlayoutDelayMs(*ssrc_, delay_.GetMs());
}

void AudioRtpReceiver::SetMediaChannel(cricket::MediaChannel* media_channel) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_DCHECK(media_channel == nullptr ||
             media_channel->media_type() == media_type());
  if (!media_channel && media_channel_)
    SetOutputVolume_w(0.0);

  media_channel ? worker_thread_safety_->SetAlive()
                : worker_thread_safety_->SetNotAlive();
  media_channel_ = static_cast<cricket::VoiceMediaChannel*>(media_channel);
}

void AudioRtpReceiver::NotifyFirstPacketReceived() {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  if (observer_) {
    observer_->OnFirstPacketReceived(media_type());
  }
  received_first_packet_ = true;
}

}  // namespace webrtc

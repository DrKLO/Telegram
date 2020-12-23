/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/video_rtp_receiver.h"

#include <stddef.h>

#include <utility>
#include <vector>

#include "api/media_stream_proxy.h"
#include "api/video_track_source_proxy.h"
#include "pc/jitter_buffer_delay.h"
#include "pc/jitter_buffer_delay_proxy.h"
#include "pc/media_stream.h"
#include "pc/video_track.h"
#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/logging.h"
#include "rtc_base/trace_event.h"

namespace webrtc {

VideoRtpReceiver::VideoRtpReceiver(rtc::Thread* worker_thread,
                                   std::string receiver_id,
                                   std::vector<std::string> stream_ids)
    : VideoRtpReceiver(worker_thread,
                       receiver_id,
                       CreateStreamsFromIds(std::move(stream_ids))) {}

VideoRtpReceiver::VideoRtpReceiver(
    rtc::Thread* worker_thread,
    const std::string& receiver_id,
    const std::vector<rtc::scoped_refptr<MediaStreamInterface>>& streams)
    : worker_thread_(worker_thread),
      id_(receiver_id),
      source_(new RefCountedObject<VideoRtpTrackSource>(this)),
      track_(VideoTrackProxyWithInternal<VideoTrack>::Create(
          rtc::Thread::Current(),
          worker_thread,
          VideoTrack::Create(
              receiver_id,
              VideoTrackSourceProxy::Create(rtc::Thread::Current(),
                                            worker_thread,
                                            source_),
              worker_thread))),
      attachment_id_(GenerateUniqueId()),
      delay_(JitterBufferDelayProxy::Create(
          rtc::Thread::Current(),
          worker_thread,
          new rtc::RefCountedObject<JitterBufferDelay>(worker_thread))) {
  RTC_DCHECK(worker_thread_);
  SetStreams(streams);
  source_->SetState(MediaSourceInterface::kLive);
}

VideoRtpReceiver::~VideoRtpReceiver() {
  // Since cricket::VideoRenderer is not reference counted,
  // we need to remove it from the channel before we are deleted.
  Stop();
  // Make sure we can't be called by the |source_| anymore.
  worker_thread_->Invoke<void>(RTC_FROM_HERE,
                               [this] { source_->ClearCallback(); });
}

std::vector<std::string> VideoRtpReceiver::stream_ids() const {
  std::vector<std::string> stream_ids(streams_.size());
  for (size_t i = 0; i < streams_.size(); ++i)
    stream_ids[i] = streams_[i]->id();
  return stream_ids;
}

RtpParameters VideoRtpReceiver::GetParameters() const {
  if (!media_channel_ || stopped_) {
    return RtpParameters();
  }
  return worker_thread_->Invoke<RtpParameters>(RTC_FROM_HERE, [&] {
    return ssrc_ ? media_channel_->GetRtpReceiveParameters(*ssrc_)
                 : media_channel_->GetDefaultRtpReceiveParameters();
  });
}

void VideoRtpReceiver::SetFrameDecryptor(
    rtc::scoped_refptr<FrameDecryptorInterface> frame_decryptor) {
  frame_decryptor_ = std::move(frame_decryptor);
  // Special Case: Set the frame decryptor to any value on any existing channel.
  if (media_channel_ && ssrc_.has_value() && !stopped_) {
    worker_thread_->Invoke<void>(RTC_FROM_HERE, [&] {
      media_channel_->SetFrameDecryptor(*ssrc_, frame_decryptor_);
    });
  }
}

rtc::scoped_refptr<FrameDecryptorInterface>
VideoRtpReceiver::GetFrameDecryptor() const {
  return frame_decryptor_;
}

void VideoRtpReceiver::SetDepacketizerToDecoderFrameTransformer(
    rtc::scoped_refptr<FrameTransformerInterface> frame_transformer) {
  worker_thread_->Invoke<void>(RTC_FROM_HERE, [&] {
    RTC_DCHECK_RUN_ON(worker_thread_);
    frame_transformer_ = std::move(frame_transformer);
    if (media_channel_ && !stopped_) {
      media_channel_->SetDepacketizerToDecoderFrameTransformer(
          ssrc_.value_or(0), frame_transformer_);
    }
  });
}

void VideoRtpReceiver::Stop() {
  // TODO(deadbeef): Need to do more here to fully stop receiving packets.
  if (stopped_) {
    return;
  }
  source_->SetState(MediaSourceInterface::kEnded);
  if (!media_channel_) {
    RTC_LOG(LS_WARNING) << "VideoRtpReceiver::Stop: No video channel exists.";
  } else {
    // Allow that SetSink fails. This is the normal case when the underlying
    // media channel has already been deleted.
    worker_thread_->Invoke<void>(RTC_FROM_HERE, [&] {
      RTC_DCHECK_RUN_ON(worker_thread_);
      SetSink(nullptr);
    });
  }
  delay_->OnStop();
  stopped_ = true;
}

void VideoRtpReceiver::StopAndEndTrack() {
  Stop();
  track_->internal()->set_ended();
}

void VideoRtpReceiver::RestartMediaChannel(absl::optional<uint32_t> ssrc) {
  RTC_DCHECK(media_channel_);
  if (!stopped_ && ssrc_ == ssrc) {
    return;
  }
  worker_thread_->Invoke<void>(RTC_FROM_HERE, [&] {
    RTC_DCHECK_RUN_ON(worker_thread_);
    if (!stopped_) {
      SetSink(nullptr);
    }
    bool encoded_sink_enabled = saved_encoded_sink_enabled_;
    SetEncodedSinkEnabled(false);
    stopped_ = false;

    ssrc_ = ssrc;

    SetSink(source_->sink());
    if (encoded_sink_enabled) {
      SetEncodedSinkEnabled(true);
    }

    if (frame_transformer_ && media_channel_) {
      media_channel_->SetDepacketizerToDecoderFrameTransformer(
          ssrc_.value_or(0), frame_transformer_);
    }
  });

  // Attach any existing frame decryptor to the media channel.
  MaybeAttachFrameDecryptorToMediaChannel(
      ssrc, worker_thread_, frame_decryptor_, media_channel_, stopped_);
  // TODO(bugs.webrtc.org/8694): Stop using 0 to mean unsignalled SSRC
  // value.
  delay_->OnStart(media_channel_, ssrc.value_or(0));
}

void VideoRtpReceiver::SetSink(rtc::VideoSinkInterface<VideoFrame>* sink) {
  RTC_DCHECK(media_channel_);
  if (ssrc_) {
    media_channel_->SetSink(*ssrc_, sink);
    return;
  }
  media_channel_->SetDefaultSink(sink);
}

void VideoRtpReceiver::SetupMediaChannel(uint32_t ssrc) {
  if (!media_channel_) {
    RTC_LOG(LS_ERROR)
        << "VideoRtpReceiver::SetupMediaChannel: No video channel exists.";
  }
  RestartMediaChannel(ssrc);
}

void VideoRtpReceiver::SetupUnsignaledMediaChannel() {
  if (!media_channel_) {
    RTC_LOG(LS_ERROR) << "VideoRtpReceiver::SetupUnsignaledMediaChannel: No "
                         "video channel exists.";
  }
  RestartMediaChannel(absl::nullopt);
}

void VideoRtpReceiver::set_stream_ids(std::vector<std::string> stream_ids) {
  SetStreams(CreateStreamsFromIds(std::move(stream_ids)));
}

void VideoRtpReceiver::SetStreams(
    const std::vector<rtc::scoped_refptr<MediaStreamInterface>>& streams) {
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

void VideoRtpReceiver::SetObserver(RtpReceiverObserverInterface* observer) {
  observer_ = observer;
  // Deliver any notifications the observer may have missed by being set late.
  if (received_first_packet_ && observer_) {
    observer_->OnFirstPacketReceived(media_type());
  }
}

void VideoRtpReceiver::SetJitterBufferMinimumDelay(
    absl::optional<double> delay_seconds) {
  delay_->Set(delay_seconds);
}

void VideoRtpReceiver::SetMediaChannel(cricket::MediaChannel* media_channel) {
  RTC_DCHECK(media_channel == nullptr ||
             media_channel->media_type() == media_type());
  worker_thread_->Invoke<void>(RTC_FROM_HERE, [&] {
    RTC_DCHECK_RUN_ON(worker_thread_);
    bool encoded_sink_enabled = saved_encoded_sink_enabled_;
    if (encoded_sink_enabled && media_channel_) {
      // Turn off the old sink, if any.
      SetEncodedSinkEnabled(false);
    }

    media_channel_ = static_cast<cricket::VideoMediaChannel*>(media_channel);

    if (media_channel_) {
      if (saved_generate_keyframe_) {
        // TODO(bugs.webrtc.org/8694): Stop using 0 to mean unsignalled SSRC
        media_channel_->GenerateKeyFrame(ssrc_.value_or(0));
        saved_generate_keyframe_ = false;
      }
      if (encoded_sink_enabled) {
        SetEncodedSinkEnabled(true);
      }
      if (frame_transformer_) {
        media_channel_->SetDepacketizerToDecoderFrameTransformer(
            ssrc_.value_or(0), frame_transformer_);
      }
    }
  });
}

void VideoRtpReceiver::NotifyFirstPacketReceived() {
  if (observer_) {
    observer_->OnFirstPacketReceived(media_type());
  }
  received_first_packet_ = true;
}

std::vector<RtpSource> VideoRtpReceiver::GetSources() const {
  if (!media_channel_ || !ssrc_ || stopped_) {
    return {};
  }
  return worker_thread_->Invoke<std::vector<RtpSource>>(
      RTC_FROM_HERE, [&] { return media_channel_->GetSources(*ssrc_); });
}

void VideoRtpReceiver::OnGenerateKeyFrame() {
  RTC_DCHECK_RUN_ON(worker_thread_);
  if (!media_channel_) {
    RTC_LOG(LS_ERROR)
        << "VideoRtpReceiver::OnGenerateKeyFrame: No video channel exists.";
    return;
  }
  // TODO(bugs.webrtc.org/8694): Stop using 0 to mean unsignalled SSRC
  media_channel_->GenerateKeyFrame(ssrc_.value_or(0));
  // We need to remember to request generation of a new key frame if the media
  // channel changes, because there's no feedback whether the keyframe
  // generation has completed on the channel.
  saved_generate_keyframe_ = true;
}

void VideoRtpReceiver::OnEncodedSinkEnabled(bool enable) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  SetEncodedSinkEnabled(enable);
  // Always save the latest state of the callback in case the media_channel_
  // changes.
  saved_encoded_sink_enabled_ = enable;
}

void VideoRtpReceiver::SetEncodedSinkEnabled(bool enable) {
  if (media_channel_) {
    if (enable) {
      // TODO(bugs.webrtc.org/8694): Stop using 0 to mean unsignalled SSRC
      auto source = source_;
      media_channel_->SetRecordableEncodedFrameCallback(
          ssrc_.value_or(0),
          [source = std::move(source)](const RecordableEncodedFrame& frame) {
            source->BroadcastRecordableEncodedFrame(frame);
          });
    } else {
      // TODO(bugs.webrtc.org/8694): Stop using 0 to mean unsignalled SSRC
      media_channel_->ClearRecordableEncodedFrameCallback(ssrc_.value_or(0));
    }
  }
}

}  // namespace webrtc

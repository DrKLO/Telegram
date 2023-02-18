/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/rtp_transmission_manager.h"

#include <type_traits>
#include <utility>

#include "absl/types/optional.h"
#include "api/peer_connection_interface.h"
#include "api/rtp_transceiver_direction.h"
#include "pc/audio_rtp_receiver.h"
#include "pc/channel_interface.h"
#include "pc/legacy_stats_collector_interface.h"
#include "pc/video_rtp_receiver.h"
#include "rtc_base/checks.h"
#include "rtc_base/helpers.h"
#include "rtc_base/logging.h"

namespace webrtc {

namespace {

static const char kDefaultAudioSenderId[] = "defaulta0";
static const char kDefaultVideoSenderId[] = "defaultv0";

}  // namespace

RtpTransmissionManager::RtpTransmissionManager(
    bool is_unified_plan,
    ConnectionContext* context,
    UsagePattern* usage_pattern,
    PeerConnectionObserver* observer,
    LegacyStatsCollectorInterface* legacy_stats,
    std::function<void()> on_negotiation_needed)
    : is_unified_plan_(is_unified_plan),
      context_(context),
      usage_pattern_(usage_pattern),
      observer_(observer),
      legacy_stats_(legacy_stats),
      on_negotiation_needed_(on_negotiation_needed),
      weak_ptr_factory_(this) {}

void RtpTransmissionManager::Close() {
  closed_ = true;
  observer_ = nullptr;
}

// Implementation of SetStreamsObserver
void RtpTransmissionManager::OnSetStreams() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (IsUnifiedPlan())
    OnNegotiationNeeded();
}

// Function to call back to the PeerConnection when negotiation is needed
void RtpTransmissionManager::OnNegotiationNeeded() {
  on_negotiation_needed_();
}

// Function that returns the currently valid observer
PeerConnectionObserver* RtpTransmissionManager::Observer() const {
  RTC_DCHECK(!closed_);
  RTC_DCHECK(observer_);
  return observer_;
}

cricket::VoiceMediaChannel* RtpTransmissionManager::voice_media_channel()
    const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(!IsUnifiedPlan());
  auto* voice_channel = GetAudioTransceiver()->internal()->channel();
  if (voice_channel) {
    return voice_channel->voice_media_channel();
  } else {
    return nullptr;
  }
}

cricket::VideoMediaChannel* RtpTransmissionManager::video_media_channel()
    const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(!IsUnifiedPlan());
  auto* video_channel = GetVideoTransceiver()->internal()->channel();
  if (video_channel) {
    return video_channel->video_media_channel();
  } else {
    return nullptr;
  }
}

RTCErrorOr<rtc::scoped_refptr<RtpSenderInterface>>
RtpTransmissionManager::AddTrack(
    rtc::scoped_refptr<MediaStreamTrackInterface> track,
    const std::vector<std::string>& stream_ids,
    const std::vector<RtpEncodingParameters>* init_send_encodings) {
  RTC_DCHECK_RUN_ON(signaling_thread());

  return (IsUnifiedPlan()
              ? AddTrackUnifiedPlan(track, stream_ids, init_send_encodings)
              : AddTrackPlanB(track, stream_ids, init_send_encodings));
}

RTCErrorOr<rtc::scoped_refptr<RtpSenderInterface>>
RtpTransmissionManager::AddTrackPlanB(
    rtc::scoped_refptr<MediaStreamTrackInterface> track,
    const std::vector<std::string>& stream_ids,
    const std::vector<RtpEncodingParameters>* init_send_encodings) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (stream_ids.size() > 1u) {
    LOG_AND_RETURN_ERROR(RTCErrorType::UNSUPPORTED_OPERATION,
                         "AddTrack with more than one stream is not "
                         "supported with Plan B semantics.");
  }
  std::vector<std::string> adjusted_stream_ids = stream_ids;
  if (adjusted_stream_ids.empty()) {
    adjusted_stream_ids.push_back(rtc::CreateRandomUuid());
  }
  cricket::MediaType media_type =
      (track->kind() == MediaStreamTrackInterface::kAudioKind
           ? cricket::MEDIA_TYPE_AUDIO
           : cricket::MEDIA_TYPE_VIDEO);
  auto new_sender =
      CreateSender(media_type, track->id(), track, adjusted_stream_ids,
                   init_send_encodings ? *init_send_encodings
                                       : std::vector<RtpEncodingParameters>());
  if (track->kind() == MediaStreamTrackInterface::kAudioKind) {
    new_sender->internal()->SetMediaChannel(voice_media_channel());
    GetAudioTransceiver()->internal()->AddSender(new_sender);
    const RtpSenderInfo* sender_info =
        FindSenderInfo(local_audio_sender_infos_,
                       new_sender->internal()->stream_ids()[0], track->id());
    if (sender_info) {
      new_sender->internal()->SetSsrc(sender_info->first_ssrc);
    }
  } else {
    RTC_DCHECK_EQ(MediaStreamTrackInterface::kVideoKind, track->kind());
    new_sender->internal()->SetMediaChannel(video_media_channel());
    GetVideoTransceiver()->internal()->AddSender(new_sender);
    const RtpSenderInfo* sender_info =
        FindSenderInfo(local_video_sender_infos_,
                       new_sender->internal()->stream_ids()[0], track->id());
    if (sender_info) {
      new_sender->internal()->SetSsrc(sender_info->first_ssrc);
    }
  }
  return rtc::scoped_refptr<RtpSenderInterface>(new_sender);
}

RTCErrorOr<rtc::scoped_refptr<RtpSenderInterface>>
RtpTransmissionManager::AddTrackUnifiedPlan(
    rtc::scoped_refptr<MediaStreamTrackInterface> track,
    const std::vector<std::string>& stream_ids,
    const std::vector<RtpEncodingParameters>* init_send_encodings) {
  auto transceiver =
      FindFirstTransceiverForAddedTrack(track, init_send_encodings);
  if (transceiver) {
    RTC_LOG(LS_INFO) << "Reusing an existing "
                     << cricket::MediaTypeToString(transceiver->media_type())
                     << " transceiver for AddTrack.";
    if (transceiver->stopping()) {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                           "The existing transceiver is stopping.");
    }

    if (transceiver->direction() == RtpTransceiverDirection::kRecvOnly) {
      transceiver->internal()->set_direction(
          RtpTransceiverDirection::kSendRecv);
    } else if (transceiver->direction() == RtpTransceiverDirection::kInactive) {
      transceiver->internal()->set_direction(
          RtpTransceiverDirection::kSendOnly);
    }
    transceiver->sender()->SetTrack(track.get());
    transceiver->internal()->sender_internal()->set_stream_ids(stream_ids);
    transceiver->internal()->set_reused_for_addtrack(true);
  } else {
    cricket::MediaType media_type =
        (track->kind() == MediaStreamTrackInterface::kAudioKind
             ? cricket::MEDIA_TYPE_AUDIO
             : cricket::MEDIA_TYPE_VIDEO);
    RTC_LOG(LS_INFO) << "Adding " << cricket::MediaTypeToString(media_type)
                     << " transceiver in response to a call to AddTrack.";
    std::string sender_id = track->id();
    // Avoid creating a sender with an existing ID by generating a random ID.
    // This can happen if this is the second time AddTrack has created a sender
    // for this track.
    if (FindSenderById(sender_id)) {
      sender_id = rtc::CreateRandomUuid();
    }
    auto sender = CreateSender(media_type, sender_id, track, stream_ids,
                               init_send_encodings
                                   ? *init_send_encodings
                                   : std::vector<RtpEncodingParameters>());
    auto receiver = CreateReceiver(media_type, rtc::CreateRandomUuid());
    transceiver = CreateAndAddTransceiver(sender, receiver);
    transceiver->internal()->set_created_by_addtrack(true);
    transceiver->internal()->set_direction(RtpTransceiverDirection::kSendRecv);
  }
  return transceiver->sender();
}

rtc::scoped_refptr<RtpSenderProxyWithInternal<RtpSenderInternal>>
RtpTransmissionManager::CreateSender(
    cricket::MediaType media_type,
    const std::string& id,
    rtc::scoped_refptr<MediaStreamTrackInterface> track,
    const std::vector<std::string>& stream_ids,
    const std::vector<RtpEncodingParameters>& send_encodings) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  rtc::scoped_refptr<RtpSenderProxyWithInternal<RtpSenderInternal>> sender;
  if (media_type == cricket::MEDIA_TYPE_AUDIO) {
    RTC_DCHECK(!track ||
               (track->kind() == MediaStreamTrackInterface::kAudioKind));
    sender = RtpSenderProxyWithInternal<RtpSenderInternal>::Create(
        signaling_thread(),
        AudioRtpSender::Create(worker_thread(), id, legacy_stats_, this));
    NoteUsageEvent(UsageEvent::AUDIO_ADDED);
  } else {
    RTC_DCHECK_EQ(media_type, cricket::MEDIA_TYPE_VIDEO);
    RTC_DCHECK(!track ||
               (track->kind() == MediaStreamTrackInterface::kVideoKind));
    sender = RtpSenderProxyWithInternal<RtpSenderInternal>::Create(
        signaling_thread(), VideoRtpSender::Create(worker_thread(), id, this));
    NoteUsageEvent(UsageEvent::VIDEO_ADDED);
  }
  bool set_track_succeeded = sender->SetTrack(track.get());
  RTC_DCHECK(set_track_succeeded);
  sender->internal()->set_stream_ids(stream_ids);
  sender->internal()->set_init_send_encodings(send_encodings);
  return sender;
}

rtc::scoped_refptr<RtpReceiverProxyWithInternal<RtpReceiverInternal>>
RtpTransmissionManager::CreateReceiver(cricket::MediaType media_type,
                                       const std::string& receiver_id) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  rtc::scoped_refptr<RtpReceiverProxyWithInternal<RtpReceiverInternal>>
      receiver;
  if (media_type == cricket::MEDIA_TYPE_AUDIO) {
    receiver = RtpReceiverProxyWithInternal<RtpReceiverInternal>::Create(
        signaling_thread(), worker_thread(),
        rtc::make_ref_counted<AudioRtpReceiver>(worker_thread(), receiver_id,
                                                std::vector<std::string>({}),
                                                IsUnifiedPlan()));
    NoteUsageEvent(UsageEvent::AUDIO_ADDED);
  } else {
    RTC_DCHECK_EQ(media_type, cricket::MEDIA_TYPE_VIDEO);
    receiver = RtpReceiverProxyWithInternal<RtpReceiverInternal>::Create(
        signaling_thread(), worker_thread(),
        rtc::make_ref_counted<VideoRtpReceiver>(worker_thread(), receiver_id,
                                                std::vector<std::string>({})));
    NoteUsageEvent(UsageEvent::VIDEO_ADDED);
  }
  return receiver;
}

rtc::scoped_refptr<RtpTransceiverProxyWithInternal<RtpTransceiver>>
RtpTransmissionManager::CreateAndAddTransceiver(
    rtc::scoped_refptr<RtpSenderProxyWithInternal<RtpSenderInternal>> sender,
    rtc::scoped_refptr<RtpReceiverProxyWithInternal<RtpReceiverInternal>>
        receiver) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // Ensure that the new sender does not have an ID that is already in use by
  // another sender.
  // Allow receiver IDs to conflict since those come from remote SDP (which
  // could be invalid, but should not cause a crash).
  RTC_DCHECK(!FindSenderById(sender->id()));
  auto transceiver = RtpTransceiverProxyWithInternal<RtpTransceiver>::Create(
      signaling_thread(),
      rtc::make_ref_counted<RtpTransceiver>(
          sender, receiver, context_,
          sender->media_type() == cricket::MEDIA_TYPE_AUDIO
              ? media_engine()->voice().GetRtpHeaderExtensions()
              : media_engine()->video().GetRtpHeaderExtensions(),
          [this_weak_ptr = weak_ptr_factory_.GetWeakPtr()]() {
            if (this_weak_ptr) {
              this_weak_ptr->OnNegotiationNeeded();
            }
          }));
  transceivers()->Add(transceiver);
  return transceiver;
}

rtc::scoped_refptr<RtpTransceiverProxyWithInternal<RtpTransceiver>>
RtpTransmissionManager::FindFirstTransceiverForAddedTrack(
    rtc::scoped_refptr<MediaStreamTrackInterface> track,
    const std::vector<RtpEncodingParameters>* init_send_encodings) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(track);
  if (init_send_encodings != nullptr) {
    return nullptr;
  }
  for (auto transceiver : transceivers()->List()) {
    if (!transceiver->sender()->track() &&
        cricket::MediaTypeToString(transceiver->media_type()) ==
            track->kind() &&
        !transceiver->internal()->has_ever_been_used_to_send() &&
        !transceiver->stopped()) {
      return transceiver;
    }
  }
  return nullptr;
}

std::vector<rtc::scoped_refptr<RtpSenderProxyWithInternal<RtpSenderInternal>>>
RtpTransmissionManager::GetSendersInternal() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  std::vector<rtc::scoped_refptr<RtpSenderProxyWithInternal<RtpSenderInternal>>>
      all_senders;
  for (const auto& transceiver : transceivers_.List()) {
    if (IsUnifiedPlan() && transceiver->internal()->stopped())
      continue;

    auto senders = transceiver->internal()->senders();
    all_senders.insert(all_senders.end(), senders.begin(), senders.end());
  }
  return all_senders;
}

std::vector<
    rtc::scoped_refptr<RtpReceiverProxyWithInternal<RtpReceiverInternal>>>
RtpTransmissionManager::GetReceiversInternal() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  std::vector<
      rtc::scoped_refptr<RtpReceiverProxyWithInternal<RtpReceiverInternal>>>
      all_receivers;
  for (const auto& transceiver : transceivers_.List()) {
    if (IsUnifiedPlan() && transceiver->internal()->stopped())
      continue;

    auto receivers = transceiver->internal()->receivers();
    all_receivers.insert(all_receivers.end(), receivers.begin(),
                         receivers.end());
  }
  return all_receivers;
}

rtc::scoped_refptr<RtpTransceiverProxyWithInternal<RtpTransceiver>>
RtpTransmissionManager::GetAudioTransceiver() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // This method only works with Plan B SDP, where there is a single
  // audio/video transceiver.
  RTC_DCHECK(!IsUnifiedPlan());
  for (auto transceiver : transceivers_.List()) {
    if (transceiver->media_type() == cricket::MEDIA_TYPE_AUDIO) {
      return transceiver;
    }
  }
  RTC_DCHECK_NOTREACHED();
  return nullptr;
}

rtc::scoped_refptr<RtpTransceiverProxyWithInternal<RtpTransceiver>>
RtpTransmissionManager::GetVideoTransceiver() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // This method only works with Plan B SDP, where there is a single
  // audio/video transceiver.
  RTC_DCHECK(!IsUnifiedPlan());
  for (auto transceiver : transceivers_.List()) {
    if (transceiver->media_type() == cricket::MEDIA_TYPE_VIDEO) {
      return transceiver;
    }
  }
  RTC_DCHECK_NOTREACHED();
  return nullptr;
}

void RtpTransmissionManager::AddAudioTrack(AudioTrackInterface* track,
                                           MediaStreamInterface* stream) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(track);
  RTC_DCHECK(stream);
  auto sender = FindSenderForTrack(track);
  if (sender) {
    // We already have a sender for this track, so just change the stream_id
    // so that it's correct in the next call to CreateOffer.
    sender->internal()->set_stream_ids({stream->id()});
    return;
  }

  // Normal case; we've never seen this track before.
  auto new_sender = CreateSender(cricket::MEDIA_TYPE_AUDIO, track->id(),
                                 rtc::scoped_refptr<AudioTrackInterface>(track),
                                 {stream->id()}, {});
  new_sender->internal()->SetMediaChannel(voice_media_channel());
  GetAudioTransceiver()->internal()->AddSender(new_sender);
  // If the sender has already been configured in SDP, we call SetSsrc,
  // which will connect the sender to the underlying transport. This can
  // occur if a local session description that contains the ID of the sender
  // is set before AddStream is called. It can also occur if the local
  // session description is not changed and RemoveStream is called, and
  // later AddStream is called again with the same stream.
  const RtpSenderInfo* sender_info =
      FindSenderInfo(local_audio_sender_infos_, stream->id(), track->id());
  if (sender_info) {
    new_sender->internal()->SetSsrc(sender_info->first_ssrc);
  }
}

// TODO(deadbeef): Don't destroy RtpSenders here; they should be kept around
// indefinitely, when we have unified plan SDP.
void RtpTransmissionManager::RemoveAudioTrack(AudioTrackInterface* track,
                                              MediaStreamInterface* stream) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(!IsUnifiedPlan());
  auto sender = FindSenderForTrack(track);
  if (!sender) {
    RTC_LOG(LS_WARNING) << "RtpSender for track with id " << track->id()
                        << " doesn't exist.";
    return;
  }
  GetAudioTransceiver()->internal()->RemoveSender(sender.get());
}

void RtpTransmissionManager::AddVideoTrack(VideoTrackInterface* track,
                                           MediaStreamInterface* stream) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(track);
  RTC_DCHECK(stream);
  auto sender = FindSenderForTrack(track);
  if (sender) {
    // We already have a sender for this track, so just change the stream_id
    // so that it's correct in the next call to CreateOffer.
    sender->internal()->set_stream_ids({stream->id()});
    return;
  }

  // Normal case; we've never seen this track before.
  auto new_sender = CreateSender(cricket::MEDIA_TYPE_VIDEO, track->id(),
                                 rtc::scoped_refptr<VideoTrackInterface>(track),
                                 {stream->id()}, {});
  new_sender->internal()->SetMediaChannel(video_media_channel());
  GetVideoTransceiver()->internal()->AddSender(new_sender);
  const RtpSenderInfo* sender_info =
      FindSenderInfo(local_video_sender_infos_, stream->id(), track->id());
  if (sender_info) {
    new_sender->internal()->SetSsrc(sender_info->first_ssrc);
  }
}

void RtpTransmissionManager::RemoveVideoTrack(VideoTrackInterface* track,
                                              MediaStreamInterface* stream) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(!IsUnifiedPlan());
  auto sender = FindSenderForTrack(track);
  if (!sender) {
    RTC_LOG(LS_WARNING) << "RtpSender for track with id " << track->id()
                        << " doesn't exist.";
    return;
  }
  GetVideoTransceiver()->internal()->RemoveSender(sender.get());
}

void RtpTransmissionManager::CreateAudioReceiver(
    MediaStreamInterface* stream,
    const RtpSenderInfo& remote_sender_info) {
  RTC_DCHECK(!closed_);
  std::vector<rtc::scoped_refptr<MediaStreamInterface>> streams;
  streams.push_back(rtc::scoped_refptr<MediaStreamInterface>(stream));
  // TODO(https://crbug.com/webrtc/9480): When we remove remote_streams(), use
  // the constructor taking stream IDs instead.
  auto audio_receiver = rtc::make_ref_counted<AudioRtpReceiver>(
      worker_thread(), remote_sender_info.sender_id, streams, IsUnifiedPlan(),
      voice_media_channel());
  if (remote_sender_info.sender_id == kDefaultAudioSenderId) {
    audio_receiver->SetupUnsignaledMediaChannel();
  } else {
    audio_receiver->SetupMediaChannel(remote_sender_info.first_ssrc);
  }

  auto receiver = RtpReceiverProxyWithInternal<RtpReceiverInternal>::Create(
      signaling_thread(), worker_thread(), std::move(audio_receiver));
  GetAudioTransceiver()->internal()->AddReceiver(receiver);
  Observer()->OnAddTrack(receiver, streams);
  NoteUsageEvent(UsageEvent::AUDIO_ADDED);
}

void RtpTransmissionManager::CreateVideoReceiver(
    MediaStreamInterface* stream,
    const RtpSenderInfo& remote_sender_info) {
  RTC_DCHECK(!closed_);
  std::vector<rtc::scoped_refptr<MediaStreamInterface>> streams;
  streams.push_back(rtc::scoped_refptr<MediaStreamInterface>(stream));
  // TODO(https://crbug.com/webrtc/9480): When we remove remote_streams(), use
  // the constructor taking stream IDs instead.
  auto video_receiver = rtc::make_ref_counted<VideoRtpReceiver>(
      worker_thread(), remote_sender_info.sender_id, streams);

  video_receiver->SetupMediaChannel(
      remote_sender_info.sender_id == kDefaultVideoSenderId
          ? absl::nullopt
          : absl::optional<uint32_t>(remote_sender_info.first_ssrc),
      video_media_channel());

  auto receiver = RtpReceiverProxyWithInternal<RtpReceiverInternal>::Create(
      signaling_thread(), worker_thread(), std::move(video_receiver));
  GetVideoTransceiver()->internal()->AddReceiver(receiver);
  Observer()->OnAddTrack(receiver, streams);
  NoteUsageEvent(UsageEvent::VIDEO_ADDED);
}

// TODO(deadbeef): Keep RtpReceivers around even if track goes away in remote
// description.
rtc::scoped_refptr<RtpReceiverInterface>
RtpTransmissionManager::RemoveAndStopReceiver(
    const RtpSenderInfo& remote_sender_info) {
  auto receiver = FindReceiverById(remote_sender_info.sender_id);
  if (!receiver) {
    RTC_LOG(LS_WARNING) << "RtpReceiver for track with id "
                        << remote_sender_info.sender_id << " doesn't exist.";
    return nullptr;
  }
  if (receiver->media_type() == cricket::MEDIA_TYPE_AUDIO) {
    GetAudioTransceiver()->internal()->RemoveReceiver(receiver.get());
  } else {
    GetVideoTransceiver()->internal()->RemoveReceiver(receiver.get());
  }
  return receiver;
}

void RtpTransmissionManager::OnRemoteSenderAdded(
    const RtpSenderInfo& sender_info,
    MediaStreamInterface* stream,
    cricket::MediaType media_type) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_LOG(LS_INFO) << "Creating " << cricket::MediaTypeToString(media_type)
                   << " receiver for track_id=" << sender_info.sender_id
                   << " and stream_id=" << sender_info.stream_id;

  if (media_type == cricket::MEDIA_TYPE_AUDIO) {
    CreateAudioReceiver(stream, sender_info);
  } else if (media_type == cricket::MEDIA_TYPE_VIDEO) {
    CreateVideoReceiver(stream, sender_info);
  } else {
    RTC_DCHECK_NOTREACHED() << "Invalid media type";
  }
}

void RtpTransmissionManager::OnRemoteSenderRemoved(
    const RtpSenderInfo& sender_info,
    MediaStreamInterface* stream,
    cricket::MediaType media_type) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_LOG(LS_INFO) << "Removing " << cricket::MediaTypeToString(media_type)
                   << " receiver for track_id=" << sender_info.sender_id
                   << " and stream_id=" << sender_info.stream_id;

  rtc::scoped_refptr<RtpReceiverInterface> receiver;
  if (media_type == cricket::MEDIA_TYPE_AUDIO) {
    // When the MediaEngine audio channel is destroyed, the RemoteAudioSource
    // will be notified which will end the AudioRtpReceiver::track().
    receiver = RemoveAndStopReceiver(sender_info);
    rtc::scoped_refptr<AudioTrackInterface> audio_track =
        stream->FindAudioTrack(sender_info.sender_id);
    if (audio_track) {
      stream->RemoveTrack(audio_track);
    }
  } else if (media_type == cricket::MEDIA_TYPE_VIDEO) {
    // Stopping or destroying a VideoRtpReceiver will end the
    // VideoRtpReceiver::track().
    receiver = RemoveAndStopReceiver(sender_info);
    rtc::scoped_refptr<VideoTrackInterface> video_track =
        stream->FindVideoTrack(sender_info.sender_id);
    if (video_track) {
      // There's no guarantee the track is still available, e.g. the track may
      // have been removed from the stream by an application.
      stream->RemoveTrack(video_track);
    }
  } else {
    RTC_DCHECK_NOTREACHED() << "Invalid media type";
  }
  if (receiver) {
    RTC_DCHECK(!closed_);
    Observer()->OnRemoveTrack(receiver);
  }
}

void RtpTransmissionManager::OnLocalSenderAdded(
    const RtpSenderInfo& sender_info,
    cricket::MediaType media_type) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(!IsUnifiedPlan());
  auto sender = FindSenderById(sender_info.sender_id);
  if (!sender) {
    RTC_LOG(LS_WARNING) << "An unknown RtpSender with id "
                        << sender_info.sender_id
                        << " has been configured in the local description.";
    return;
  }

  if (sender->media_type() != media_type) {
    RTC_LOG(LS_WARNING) << "An RtpSender has been configured in the local"
                           " description with an unexpected media type.";
    return;
  }

  sender->internal()->set_stream_ids({sender_info.stream_id});
  sender->internal()->SetSsrc(sender_info.first_ssrc);
}

void RtpTransmissionManager::OnLocalSenderRemoved(
    const RtpSenderInfo& sender_info,
    cricket::MediaType media_type) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  auto sender = FindSenderById(sender_info.sender_id);
  if (!sender) {
    // This is the normal case. I.e., RemoveStream has been called and the
    // SessionDescriptions has been renegotiated.
    return;
  }

  // A sender has been removed from the SessionDescription but it's still
  // associated with the PeerConnection. This only occurs if the SDP doesn't
  // match with the calls to CreateSender, AddStream and RemoveStream.
  if (sender->media_type() != media_type) {
    RTC_LOG(LS_WARNING) << "An RtpSender has been configured in the local"
                           " description with an unexpected media type.";
    return;
  }

  sender->internal()->SetSsrc(0);
}

std::vector<RtpSenderInfo>* RtpTransmissionManager::GetRemoteSenderInfos(
    cricket::MediaType media_type) {
  RTC_DCHECK(media_type == cricket::MEDIA_TYPE_AUDIO ||
             media_type == cricket::MEDIA_TYPE_VIDEO);
  return (media_type == cricket::MEDIA_TYPE_AUDIO)
             ? &remote_audio_sender_infos_
             : &remote_video_sender_infos_;
}

std::vector<RtpSenderInfo>* RtpTransmissionManager::GetLocalSenderInfos(
    cricket::MediaType media_type) {
  RTC_DCHECK(media_type == cricket::MEDIA_TYPE_AUDIO ||
             media_type == cricket::MEDIA_TYPE_VIDEO);
  return (media_type == cricket::MEDIA_TYPE_AUDIO) ? &local_audio_sender_infos_
                                                   : &local_video_sender_infos_;
}

const RtpSenderInfo* RtpTransmissionManager::FindSenderInfo(
    const std::vector<RtpSenderInfo>& infos,
    const std::string& stream_id,
    const std::string& sender_id) const {
  for (const RtpSenderInfo& sender_info : infos) {
    if (sender_info.stream_id == stream_id &&
        sender_info.sender_id == sender_id) {
      return &sender_info;
    }
  }
  return nullptr;
}

rtc::scoped_refptr<RtpSenderProxyWithInternal<RtpSenderInternal>>
RtpTransmissionManager::FindSenderForTrack(
    MediaStreamTrackInterface* track) const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  for (const auto& transceiver : transceivers_.List()) {
    for (auto sender : transceiver->internal()->senders()) {
      if (sender->track() == track) {
        return sender;
      }
    }
  }
  return nullptr;
}

rtc::scoped_refptr<RtpSenderProxyWithInternal<RtpSenderInternal>>
RtpTransmissionManager::FindSenderById(const std::string& sender_id) const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  for (const auto& transceiver : transceivers_.List()) {
    for (auto sender : transceiver->internal()->senders()) {
      if (sender->id() == sender_id) {
        return sender;
      }
    }
  }
  return nullptr;
}

rtc::scoped_refptr<RtpReceiverProxyWithInternal<RtpReceiverInternal>>
RtpTransmissionManager::FindReceiverById(const std::string& receiver_id) const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  for (const auto& transceiver : transceivers_.List()) {
    for (auto receiver : transceiver->internal()->receivers()) {
      if (receiver->id() == receiver_id) {
        return receiver;
      }
    }
  }
  return nullptr;
}

cricket::MediaEngineInterface* RtpTransmissionManager::media_engine() const {
  return context_->media_engine();
}

}  // namespace webrtc

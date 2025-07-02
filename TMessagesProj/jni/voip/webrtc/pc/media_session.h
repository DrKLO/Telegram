/*
 *  Copyright 2004 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Types and classes used in media session descriptions.

#ifndef PC_MEDIA_SESSION_H_
#define PC_MEDIA_SESSION_H_

#include <map>
#include <memory>
#include <string>
#include <vector>

#include "api/crypto/crypto_options.h"
#include "api/media_types.h"
#include "api/rtp_parameters.h"
#include "api/rtp_transceiver_direction.h"
#include "media/base/media_constants.h"
#include "media/base/rid_description.h"
#include "media/base/stream_params.h"
#include "p2p/base/ice_credentials_iterator.h"
#include "p2p/base/transport_description.h"
#include "p2p/base/transport_description_factory.h"
#include "p2p/base/transport_info.h"
#include "pc/jsep_transport.h"
#include "pc/media_protocol_names.h"
#include "pc/session_description.h"
#include "pc/simulcast_description.h"
#include "rtc_base/memory/always_valid_pointer.h"
#include "rtc_base/unique_id_generator.h"

namespace webrtc {

// Forward declaration due to circular dependecy.
class ConnectionContext;

}  // namespace webrtc

namespace cricket {

class MediaEngineInterface;

// Default RTCP CNAME for unit tests.
const char kDefaultRtcpCname[] = "DefaultRtcpCname";

// Options for an RtpSender contained with an media description/"m=" section.
// Note: Spec-compliant Simulcast and legacy simulcast are mutually exclusive.
struct SenderOptions {
  std::string track_id;
  std::vector<std::string> stream_ids;
  // Use RIDs and Simulcast Layers to indicate spec-compliant Simulcast.
  std::vector<RidDescription> rids;
  SimulcastLayerList simulcast_layers;
  // Use `num_sim_layers` to indicate legacy simulcast.
  int num_sim_layers;
};

// Options for an individual media description/"m=" section.
struct MediaDescriptionOptions {
  MediaDescriptionOptions(MediaType type,
                          const std::string& mid,
                          webrtc::RtpTransceiverDirection direction,
                          bool stopped)
      : type(type), mid(mid), direction(direction), stopped(stopped) {}

  // TODO(deadbeef): When we don't support Plan B, there will only be one
  // sender per media description and this can be simplified.
  void AddAudioSender(const std::string& track_id,
                      const std::vector<std::string>& stream_ids);
  void AddVideoSender(const std::string& track_id,
                      const std::vector<std::string>& stream_ids,
                      const std::vector<RidDescription>& rids,
                      const SimulcastLayerList& simulcast_layers,
                      int num_sim_layers);

  MediaType type;
  std::string mid;
  webrtc::RtpTransceiverDirection direction;
  bool stopped;
  TransportOptions transport_options;
  // Note: There's no equivalent "RtpReceiverOptions" because only send
  // stream information goes in the local descriptions.
  std::vector<SenderOptions> sender_options;
  std::vector<webrtc::RtpCodecCapability> codec_preferences;
  std::vector<webrtc::RtpHeaderExtensionCapability> header_extensions;

 private:
  // Doesn't DCHECK on `type`.
  void AddSenderInternal(const std::string& track_id,
                         const std::vector<std::string>& stream_ids,
                         const std::vector<RidDescription>& rids,
                         const SimulcastLayerList& simulcast_layers,
                         int num_sim_layers);
};

// Provides a mechanism for describing how m= sections should be generated.
// The m= section with index X will use media_description_options[X]. There
// must be an option for each existing section if creating an answer, or a
// subsequent offer.
struct MediaSessionOptions {
  MediaSessionOptions() {}

  bool has_audio() const { return HasMediaDescription(MEDIA_TYPE_AUDIO); }
  bool has_video() const { return HasMediaDescription(MEDIA_TYPE_VIDEO); }
  bool has_data() const { return HasMediaDescription(MEDIA_TYPE_DATA); }

  bool HasMediaDescription(MediaType type) const;

  bool vad_enabled = true;  // When disabled, removes all CN codecs from SDP.
  bool rtcp_mux_enabled = true;
  bool bundle_enabled = false;
  bool offer_extmap_allow_mixed = false;
  bool raw_packetization_for_video = false;
  std::string rtcp_cname = kDefaultRtcpCname;
  webrtc::CryptoOptions crypto_options;
  // List of media description options in the same order that the media
  // descriptions will be generated.
  std::vector<MediaDescriptionOptions> media_description_options;
  std::vector<IceParameters> pooled_ice_credentials;

  // Use the draft-ietf-mmusic-sctp-sdp-03 obsolete syntax for SCTP
  // datachannels.
  // Default is true for backwards compatibility with clients that use
  // this internal interface.
  bool use_obsolete_sctp_sdp = true;
};

// Creates media session descriptions according to the supplied codecs and
// other fields, as well as the supplied per-call options.
// When creating answers, performs the appropriate negotiation
// of the various fields to determine the proper result.
class MediaSessionDescriptionFactory {
 public:
  // This constructor automatically sets up the factory to get its configuration
  // from the specified MediaEngine (when provided).
  // The TransportDescriptionFactory and the UniqueRandomIdGenerator are not
  // owned by MediaSessionDescriptionFactory, so they must be kept alive by the
  // user of this class.
  MediaSessionDescriptionFactory(cricket::MediaEngineInterface* media_engine,
                                 bool rtx_enabled,
                                 rtc::UniqueRandomIdGenerator* ssrc_generator,
                                 const TransportDescriptionFactory* factory);

  const AudioCodecs& audio_sendrecv_codecs() const;
  const AudioCodecs& audio_send_codecs() const;
  const AudioCodecs& audio_recv_codecs() const;
  void set_audio_codecs(const AudioCodecs& send_codecs,
                        const AudioCodecs& recv_codecs);
  const VideoCodecs& video_sendrecv_codecs() const;
  const VideoCodecs& video_send_codecs() const;
  const VideoCodecs& video_recv_codecs() const;
  void set_video_codecs(const VideoCodecs& send_codecs,
                        const VideoCodecs& recv_codecs);
  RtpHeaderExtensions filtered_rtp_header_extensions(
      RtpHeaderExtensions extensions) const;

  void set_enable_encrypted_rtp_header_extensions(bool enable) {
    enable_encrypted_rtp_header_extensions_ = enable;
  }

  void set_is_unified_plan(bool is_unified_plan) {
    is_unified_plan_ = is_unified_plan;
  }

  webrtc::RTCErrorOr<std::unique_ptr<SessionDescription>> CreateOfferOrError(
      const MediaSessionOptions& options,
      const SessionDescription* current_description) const;
  webrtc::RTCErrorOr<std::unique_ptr<SessionDescription>> CreateAnswerOrError(
      const SessionDescription* offer,
      const MediaSessionOptions& options,
      const SessionDescription* current_description) const;

 private:
  struct AudioVideoRtpHeaderExtensions {
    RtpHeaderExtensions audio;
    RtpHeaderExtensions video;
  };

  const AudioCodecs& GetAudioCodecsForOffer(
      const webrtc::RtpTransceiverDirection& direction) const;
  const AudioCodecs& GetAudioCodecsForAnswer(
      const webrtc::RtpTransceiverDirection& offer,
      const webrtc::RtpTransceiverDirection& answer) const;
  const VideoCodecs& GetVideoCodecsForOffer(
      const webrtc::RtpTransceiverDirection& direction) const;
  const VideoCodecs& GetVideoCodecsForAnswer(
      const webrtc::RtpTransceiverDirection& offer,
      const webrtc::RtpTransceiverDirection& answer) const;
  void GetCodecsForOffer(
      const std::vector<const ContentInfo*>& current_active_contents,
      AudioCodecs* audio_codecs,
      VideoCodecs* video_codecs) const;
  void GetCodecsForAnswer(
      const std::vector<const ContentInfo*>& current_active_contents,
      const SessionDescription& remote_offer,
      AudioCodecs* audio_codecs,
      VideoCodecs* video_codecs) const;
  AudioVideoRtpHeaderExtensions GetOfferedRtpHeaderExtensionsWithIds(
      const std::vector<const ContentInfo*>& current_active_contents,
      bool extmap_allow_mixed,
      const std::vector<MediaDescriptionOptions>& media_description_options)
      const;
  webrtc::RTCError AddTransportOffer(
      const std::string& content_name,
      const TransportOptions& transport_options,
      const SessionDescription* current_desc,
      SessionDescription* offer,
      IceCredentialsIterator* ice_credentials) const;

  std::unique_ptr<TransportDescription> CreateTransportAnswer(
      const std::string& content_name,
      const SessionDescription* offer_desc,
      const TransportOptions& transport_options,
      const SessionDescription* current_desc,
      bool require_transport_attributes,
      IceCredentialsIterator* ice_credentials) const;

  webrtc::RTCError AddTransportAnswer(
      const std::string& content_name,
      const TransportDescription& transport_desc,
      SessionDescription* answer_desc) const;

  // Helpers for adding media contents to the SessionDescription.
  webrtc::RTCError AddRtpContentForOffer(
      const MediaDescriptionOptions& media_description_options,
      const MediaSessionOptions& session_options,
      const ContentInfo* current_content,
      const SessionDescription* current_description,
      const RtpHeaderExtensions& header_extensions,
      const std::vector<Codec>& codecs,
      StreamParamsVec* current_streams,
      SessionDescription* desc,
      IceCredentialsIterator* ice_credentials) const;

  webrtc::RTCError AddDataContentForOffer(
      const MediaDescriptionOptions& media_description_options,
      const MediaSessionOptions& session_options,
      const ContentInfo* current_content,
      const SessionDescription* current_description,
      StreamParamsVec* current_streams,
      SessionDescription* desc,
      IceCredentialsIterator* ice_credentials) const;

  webrtc::RTCError AddUnsupportedContentForOffer(
      const MediaDescriptionOptions& media_description_options,
      const MediaSessionOptions& session_options,
      const ContentInfo* current_content,
      const SessionDescription* current_description,
      SessionDescription* desc,
      IceCredentialsIterator* ice_credentials) const;

  webrtc::RTCError AddRtpContentForAnswer(
      const MediaDescriptionOptions& media_description_options,
      const MediaSessionOptions& session_options,
      const ContentInfo* offer_content,
      const SessionDescription* offer_description,
      const ContentInfo* current_content,
      const SessionDescription* current_description,
      const TransportInfo* bundle_transport,
      const std::vector<Codec>& codecs,
      const RtpHeaderExtensions& header_extensions,
      StreamParamsVec* current_streams,
      SessionDescription* answer,
      IceCredentialsIterator* ice_credentials) const;

  webrtc::RTCError AddDataContentForAnswer(
      const MediaDescriptionOptions& media_description_options,
      const MediaSessionOptions& session_options,
      const ContentInfo* offer_content,
      const SessionDescription* offer_description,
      const ContentInfo* current_content,
      const SessionDescription* current_description,
      const TransportInfo* bundle_transport,
      StreamParamsVec* current_streams,
      SessionDescription* answer,
      IceCredentialsIterator* ice_credentials) const;

  webrtc::RTCError AddUnsupportedContentForAnswer(
      const MediaDescriptionOptions& media_description_options,
      const MediaSessionOptions& session_options,
      const ContentInfo* offer_content,
      const SessionDescription* offer_description,
      const ContentInfo* current_content,
      const SessionDescription* current_description,
      const TransportInfo* bundle_transport,
      SessionDescription* answer,
      IceCredentialsIterator* ice_credentials) const;

  void ComputeAudioCodecsIntersectionAndUnion();

  void ComputeVideoCodecsIntersectionAndUnion();

  rtc::UniqueRandomIdGenerator* ssrc_generator() const {
    return ssrc_generator_.get();
  }

  bool is_unified_plan_ = false;
  AudioCodecs audio_send_codecs_;
  AudioCodecs audio_recv_codecs_;
  // Intersection of send and recv.
  AudioCodecs audio_sendrecv_codecs_;
  // Union of send and recv.
  AudioCodecs all_audio_codecs_;
  VideoCodecs video_send_codecs_;
  VideoCodecs video_recv_codecs_;
  // Intersection of send and recv.
  VideoCodecs video_sendrecv_codecs_;
  // Union of send and recv.
  VideoCodecs all_video_codecs_;
  // This object may or may not be owned by this class.
  webrtc::AlwaysValidPointer<rtc::UniqueRandomIdGenerator> const
      ssrc_generator_;
  bool enable_encrypted_rtp_header_extensions_ = false;
  const TransportDescriptionFactory* transport_desc_factory_;
};

// Convenience functions.
bool IsMediaContent(const ContentInfo* content);
bool IsAudioContent(const ContentInfo* content);
bool IsVideoContent(const ContentInfo* content);
bool IsDataContent(const ContentInfo* content);
bool IsUnsupportedContent(const ContentInfo* content);
const ContentInfo* GetFirstMediaContent(const ContentInfos& contents,
                                        MediaType media_type);
const ContentInfo* GetFirstAudioContent(const ContentInfos& contents);
const ContentInfo* GetFirstVideoContent(const ContentInfos& contents);
const ContentInfo* GetFirstDataContent(const ContentInfos& contents);
const ContentInfo* GetFirstMediaContent(const SessionDescription* sdesc,
                                        MediaType media_type);
const ContentInfo* GetFirstAudioContent(const SessionDescription* sdesc);
const ContentInfo* GetFirstVideoContent(const SessionDescription* sdesc);
const ContentInfo* GetFirstDataContent(const SessionDescription* sdesc);
const AudioContentDescription* GetFirstAudioContentDescription(
    const SessionDescription* sdesc);
const VideoContentDescription* GetFirstVideoContentDescription(
    const SessionDescription* sdesc);
const SctpDataContentDescription* GetFirstSctpDataContentDescription(
    const SessionDescription* sdesc);
// Non-const versions of the above functions.
// Useful when modifying an existing description.
ContentInfo* GetFirstMediaContent(ContentInfos* contents, MediaType media_type);
ContentInfo* GetFirstAudioContent(ContentInfos* contents);
ContentInfo* GetFirstVideoContent(ContentInfos* contents);
ContentInfo* GetFirstDataContent(ContentInfos* contents);
ContentInfo* GetFirstMediaContent(SessionDescription* sdesc,
                                  MediaType media_type);
ContentInfo* GetFirstAudioContent(SessionDescription* sdesc);
ContentInfo* GetFirstVideoContent(SessionDescription* sdesc);
ContentInfo* GetFirstDataContent(SessionDescription* sdesc);
AudioContentDescription* GetFirstAudioContentDescription(
    SessionDescription* sdesc);
VideoContentDescription* GetFirstVideoContentDescription(
    SessionDescription* sdesc);
SctpDataContentDescription* GetFirstSctpDataContentDescription(
    SessionDescription* sdesc);

}  // namespace cricket

#endif  // PC_MEDIA_SESSION_H_

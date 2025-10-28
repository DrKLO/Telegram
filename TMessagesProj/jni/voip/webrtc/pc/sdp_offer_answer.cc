/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/sdp_offer_answer.h"

#include <algorithm>
#include <cstddef>
#include <iterator>
#include <map>
#include <memory>
#include <queue>
#include <string>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/memory/memory.h"
#include "absl/strings/match.h"
#include "absl/strings/string_view.h"
#include "api/array_view.h"
#include "api/crypto/crypto_options.h"
#include "api/dtls_transport_interface.h"
#include "api/field_trials_view.h"
#include "api/rtp_parameters.h"
#include "api/rtp_receiver_interface.h"
#include "api/rtp_sender_interface.h"
#include "api/video/builtin_video_bitrate_allocator_factory.h"
#include "media/base/codec.h"
#include "media/base/rid_description.h"
#include "p2p/base/ice_transport_internal.h"
#include "p2p/base/p2p_constants.h"
#include "p2p/base/p2p_transport_channel.h"
#include "p2p/base/port.h"
#include "p2p/base/transport_description.h"
#include "p2p/base/transport_description_factory.h"
#include "p2p/base/transport_info.h"
#include "pc/channel_interface.h"
#include "pc/dtls_transport.h"
#include "pc/legacy_stats_collector.h"
#include "pc/media_stream.h"
#include "pc/media_stream_proxy.h"
#include "pc/peer_connection_internal.h"
#include "pc/peer_connection_message_handler.h"
#include "pc/rtp_media_utils.h"
#include "pc/rtp_receiver_proxy.h"
#include "pc/rtp_sender.h"
#include "pc/rtp_sender_proxy.h"
#include "pc/simulcast_description.h"
#include "pc/usage_pattern.h"
#include "pc/used_ids.h"
#include "pc/webrtc_session_description_factory.h"
#include "rtc_base/helpers.h"
#include "rtc_base/logging.h"
#include "rtc_base/rtc_certificate.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/metrics.h"

using cricket::ContentInfo;
using cricket::ContentInfos;
using cricket::MediaContentDescription;
using cricket::MediaProtocolType;
using cricket::RidDescription;
using cricket::RidDirection;
using cricket::SessionDescription;
using cricket::SimulcastDescription;
using cricket::SimulcastLayer;
using cricket::SimulcastLayerList;
using cricket::StreamParams;
using cricket::TransportInfo;

namespace webrtc {

namespace {

typedef PeerConnectionInterface::RTCOfferAnswerOptions RTCOfferAnswerOptions;

// Error messages
const char kInvalidSdp[] = "Invalid session description.";
const char kInvalidCandidates[] = "Description contains invalid candidates.";
const char kBundleWithoutRtcpMux[] =
    "rtcp-mux must be enabled when BUNDLE "
    "is enabled.";
const char kMlineMismatchInAnswer[] =
    "The order of m-lines in answer doesn't match order in offer. Rejecting "
    "answer.";
const char kMlineMismatchInSubsequentOffer[] =
    "The order of m-lines in subsequent offer doesn't match order from "
    "previous offer/answer.";
const char kSdpWithoutIceUfragPwd[] =
    "Called with SDP without ice-ufrag and ice-pwd.";
const char kSdpWithoutDtlsFingerprint[] =
    "Called with SDP without DTLS fingerprint.";
const char kSdpWithoutCrypto[] = "Called with SDP without crypto setup.";

const char kSessionError[] = "Session error code: ";
const char kSessionErrorDesc[] = "Session error description: ";

// The length of RTCP CNAMEs.
static const int kRtcpCnameLength = 16;

// The maximum length of the MID attribute.
static constexpr size_t kMidMaxSize = 16;

const char kDefaultStreamId[] = "default";
// NOTE: Duplicated in peer_connection.cc:
static const char kDefaultAudioSenderId[] = "defaulta0";
static const char kDefaultVideoSenderId[] = "defaultv0";

void NoteAddIceCandidateResult(int result) {
  RTC_HISTOGRAM_ENUMERATION("WebRTC.PeerConnection.AddIceCandidate", result,
                            kAddIceCandidateMax);
}

std::map<std::string, const cricket::ContentGroup*> GetBundleGroupsByMid(
    const SessionDescription* desc) {
  std::vector<const cricket::ContentGroup*> bundle_groups =
      desc->GetGroupsByName(cricket::GROUP_TYPE_BUNDLE);
  std::map<std::string, const cricket::ContentGroup*> bundle_groups_by_mid;
  for (const cricket::ContentGroup* bundle_group : bundle_groups) {
    for (const std::string& content_name : bundle_group->content_names()) {
      bundle_groups_by_mid[content_name] = bundle_group;
    }
  }
  return bundle_groups_by_mid;
}

// Returns true if `new_desc` requests an ICE restart (i.e., new ufrag/pwd).
bool CheckForRemoteIceRestart(const SessionDescriptionInterface* old_desc,
                              const SessionDescriptionInterface* new_desc,
                              const std::string& content_name) {
  if (!old_desc) {
    return false;
  }
  const SessionDescription* new_sd = new_desc->description();
  const SessionDescription* old_sd = old_desc->description();
  const ContentInfo* cinfo = new_sd->GetContentByName(content_name);
  if (!cinfo || cinfo->rejected) {
    return false;
  }
  // If the content isn't rejected, check if ufrag and password has changed.
  const cricket::TransportDescription* new_transport_desc =
      new_sd->GetTransportDescriptionByName(content_name);
  const cricket::TransportDescription* old_transport_desc =
      old_sd->GetTransportDescriptionByName(content_name);
  if (!new_transport_desc || !old_transport_desc) {
    // No transport description exists. This is not an ICE restart.
    return false;
  }
  if (cricket::IceCredentialsChanged(
          old_transport_desc->ice_ufrag, old_transport_desc->ice_pwd,
          new_transport_desc->ice_ufrag, new_transport_desc->ice_pwd)) {
    RTC_LOG(LS_INFO) << "Remote peer requests ICE restart for " << content_name
                     << ".";
    return true;
  }
  return false;
}

// Generates a string error message for SetLocalDescription/SetRemoteDescription
// from an RTCError.
std::string GetSetDescriptionErrorMessage(cricket::ContentSource source,
                                          SdpType type,
                                          const RTCError& error) {
  rtc::StringBuilder oss;
  oss << "Failed to set " << (source == cricket::CS_LOCAL ? "local" : "remote")
      << " " << SdpTypeToString(type) << " sdp: ";
  RTC_DCHECK(!absl::StartsWith(error.message(), oss.str())) << error.message();
  oss << error.message();
  return oss.Release();
}

std::string GetStreamIdsString(rtc::ArrayView<const std::string> stream_ids) {
  std::string output = "streams=[";
  const char* separator = "";
  for (const auto& stream_id : stream_ids) {
    output.append(separator).append(stream_id);
    separator = ", ";
  }
  output.append("]");
  return output;
}

const ContentInfo* FindTransceiverMSection(
    RtpTransceiver* transceiver,
    const SessionDescriptionInterface* session_description) {
  return transceiver->mid()
             ? session_description->description()->GetContentByName(
                   *transceiver->mid())
             : nullptr;
}

// If the direction is "recvonly" or "inactive", treat the description
// as containing no streams.
// See: https://code.google.com/p/webrtc/issues/detail?id=5054
std::vector<cricket::StreamParams> GetActiveStreams(
    const cricket::MediaContentDescription* desc) {
  return RtpTransceiverDirectionHasSend(desc->direction())
             ? desc->streams()
             : std::vector<cricket::StreamParams>();
}

// Logic to decide if an m= section can be recycled. This means that the new
// m= section is not rejected, but the old local or remote m= section is
// rejected. `old_content_one` and `old_content_two` refer to the m= section
// of the old remote and old local descriptions in no particular order.
// We need to check both the old local and remote because either
// could be the most current from the latest negotation.
bool IsMediaSectionBeingRecycled(SdpType type,
                                 const ContentInfo& content,
                                 const ContentInfo* old_content_one,
                                 const ContentInfo* old_content_two) {
  return type == SdpType::kOffer && !content.rejected &&
         ((old_content_one && old_content_one->rejected) ||
          (old_content_two && old_content_two->rejected));
}

// Verify that the order of media sections in `new_desc` matches
// `current_desc`. The number of m= sections in `new_desc` should be no
// less than `current_desc`. In the case of checking an answer's
// `new_desc`, the `current_desc` is the last offer that was set as the
// local or remote. In the case of checking an offer's `new_desc` we
// check against the local and remote descriptions stored from the last
// negotiation, because either of these could be the most up to date for
// possible rejected m sections. These are the `current_desc` and
// `secondary_current_desc`.
bool MediaSectionsInSameOrder(const SessionDescription& current_desc,
                              const SessionDescription* secondary_current_desc,
                              const SessionDescription& new_desc,
                              const SdpType type) {
  if (current_desc.contents().size() > new_desc.contents().size()) {
    return false;
  }

  for (size_t i = 0; i < current_desc.contents().size(); ++i) {
    const cricket::ContentInfo* secondary_content_info = nullptr;
    if (secondary_current_desc &&
        i < secondary_current_desc->contents().size()) {
      secondary_content_info = &secondary_current_desc->contents()[i];
    }
    if (IsMediaSectionBeingRecycled(type, new_desc.contents()[i],
                                    &current_desc.contents()[i],
                                    secondary_content_info)) {
      // For new offer descriptions, if the media section can be recycled, it's
      // valid for the MID and media type to change.
      continue;
    }
    if (new_desc.contents()[i].name != current_desc.contents()[i].name) {
      return false;
    }
    const MediaContentDescription* new_desc_mdesc =
        new_desc.contents()[i].media_description();
    const MediaContentDescription* current_desc_mdesc =
        current_desc.contents()[i].media_description();
    if (new_desc_mdesc->type() != current_desc_mdesc->type()) {
      return false;
    }
  }
  return true;
}

bool MediaSectionsHaveSameCount(const SessionDescription& desc1,
                                const SessionDescription& desc2) {
  return desc1.contents().size() == desc2.contents().size();
}
// Checks that each non-rejected content has a DTLS
// fingerprint, unless it's in a BUNDLE group, in which case only the
// BUNDLE-tag section (first media section/description in the BUNDLE group)
// needs a ufrag and pwd. Mismatches, such as replying with a DTLS fingerprint
// to SDES keys, will be caught in JsepTransport negotiation, and backstopped
// by Channel's `srtp_required` check.
RTCError VerifyCrypto(const SessionDescription* desc,
                      bool dtls_enabled,
                      const std::map<std::string, const cricket::ContentGroup*>&
                          bundle_groups_by_mid) {
  for (const cricket::ContentInfo& content_info : desc->contents()) {
    if (content_info.rejected) {
      continue;
    }
    const std::string& mid = content_info.name;
    auto it = bundle_groups_by_mid.find(mid);
    const cricket::ContentGroup* bundle =
        it != bundle_groups_by_mid.end() ? it->second : nullptr;
    if (bundle && mid != *(bundle->FirstContentName())) {
      // This isn't the first media section in the BUNDLE group, so it's not
      // required to have crypto attributes, since only the crypto attributes
      // from the first section actually get used.
      continue;
    }

    // If the content isn't rejected or bundled into another m= section, crypto
    // must be present.
    const MediaContentDescription* media = content_info.media_description();
    const TransportInfo* tinfo = desc->GetTransportInfoByName(mid);
    if (!media || !tinfo) {
      // Something is not right.
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER, kInvalidSdp);
    }
    if (dtls_enabled) {
      if (!tinfo->description.identity_fingerprint) {
        LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                             kSdpWithoutDtlsFingerprint);
      }
    } else {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER, kSdpWithoutCrypto);
    }
  }
  return RTCError::OK();
}

// Checks that each non-rejected content has ice-ufrag and ice-pwd set, unless
// it's in a BUNDLE group, in which case only the BUNDLE-tag section (first
// media section/description in the BUNDLE group) needs a ufrag and pwd.
bool VerifyIceUfragPwdPresent(
    const SessionDescription* desc,
    const std::map<std::string, const cricket::ContentGroup*>&
        bundle_groups_by_mid) {
  for (const cricket::ContentInfo& content_info : desc->contents()) {
    if (content_info.rejected) {
      continue;
    }
    const std::string& mid = content_info.name;
    auto it = bundle_groups_by_mid.find(mid);
    const cricket::ContentGroup* bundle =
        it != bundle_groups_by_mid.end() ? it->second : nullptr;
    if (bundle && mid != *(bundle->FirstContentName())) {
      // This isn't the first media section in the BUNDLE group, so it's not
      // required to have ufrag/password, since only the ufrag/password from
      // the first section actually get used.
      continue;
    }

    // If the content isn't rejected or bundled into another m= section,
    // ice-ufrag and ice-pwd must be present.
    const TransportInfo* tinfo = desc->GetTransportInfoByName(mid);
    if (!tinfo) {
      // Something is not right.
      RTC_LOG(LS_ERROR) << kInvalidSdp;
      return false;
    }
    if (tinfo->description.ice_ufrag.empty() ||
        tinfo->description.ice_pwd.empty()) {
      RTC_LOG(LS_ERROR) << "Session description must have ice ufrag and pwd.";
      return false;
    }
  }
  return true;
}

RTCError ValidateMids(const cricket::SessionDescription& description) {
  std::set<std::string> mids;
  for (const cricket::ContentInfo& content : description.contents()) {
    if (content.name.empty()) {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                           "A media section is missing a MID attribute.");
    }
    if (content.name.size() > kMidMaxSize) {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                           "The MID attribute exceeds the maximum supported "
                           "length of 16 characters.");
    }
    if (!mids.insert(content.name).second) {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                           "Duplicate a=mid value '" + content.name + "'.");
    }
  }
  return RTCError::OK();
}

RTCError FindDuplicateCodecParameters(
    const RtpCodecParameters codec_parameters,
    std::map<int, RtpCodecParameters>& payload_to_codec_parameters) {
  auto existing_codec_parameters =
      payload_to_codec_parameters.find(codec_parameters.payload_type);
  if (existing_codec_parameters != payload_to_codec_parameters.end() &&
      codec_parameters != existing_codec_parameters->second) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                         "A BUNDLE group contains a codec collision for "
                         "payload_type='" +
                             rtc::ToString(codec_parameters.payload_type) +
                             ". All codecs must share the same type, "
                             "encoding name, clock rate and parameters.");
  }
  payload_to_codec_parameters.insert(
      std::make_pair(codec_parameters.payload_type, codec_parameters));
  return RTCError::OK();
}

RTCError ValidateBundledPayloadTypes(
    const cricket::SessionDescription& description) {
  // https://www.rfc-editor.org/rfc/rfc8843#name-payload-type-pt-value-reuse
  // ... all codecs associated with the payload type number MUST share an
  // identical codec configuration. This means that the codecs MUST share
  // the same media type, encoding name, clock rate, and any parameter
  // that can affect the codec configuration and packetization.
  std::vector<const cricket::ContentGroup*> bundle_groups =
      description.GetGroupsByName(cricket::GROUP_TYPE_BUNDLE);
  for (const cricket::ContentGroup* bundle_group : bundle_groups) {
    std::map<int, RtpCodecParameters> payload_to_codec_parameters;
    for (const std::string& content_name : bundle_group->content_names()) {
      const ContentInfo* content_description =
          description.GetContentByName(content_name);
      if (!content_description) {
        LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                             "A BUNDLE group contains a MID='" + content_name +
                                 "' matching no m= section.");
      }
      const cricket::MediaContentDescription* media_description =
          content_description->media_description();
      RTC_DCHECK(media_description);
      if (content_description->rejected || !media_description ||
          !media_description->has_codecs()) {
        continue;
      }
      const auto type = media_description->type();
      if (type == cricket::MEDIA_TYPE_AUDIO ||
          type == cricket::MEDIA_TYPE_VIDEO) {
        for (const auto& c : media_description->codecs()) {
          auto error = FindDuplicateCodecParameters(
              c.ToCodecParameters(), payload_to_codec_parameters);
          if (!error.ok()) {
            return error;
          }
        }
      }
    }
  }
  return RTCError::OK();
}

RTCError FindDuplicateHeaderExtensionIds(
    const RtpExtension extension,
    std::map<int, RtpExtension>& id_to_extension) {
  auto existing_extension = id_to_extension.find(extension.id);
  if (existing_extension != id_to_extension.end() &&
      !(extension.uri == existing_extension->second.uri &&
        extension.encrypt == existing_extension->second.encrypt)) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::INVALID_PARAMETER,
        "A BUNDLE group contains a codec collision for "
        "header extension id=" +
            rtc::ToString(extension.id) +
            ". The id must be the same across all bundled media descriptions");
  }
  id_to_extension.insert(std::make_pair(extension.id, extension));
  return RTCError::OK();
}

RTCError ValidateBundledRtpHeaderExtensions(
    const cricket::SessionDescription& description) {
  // https://www.rfc-editor.org/rfc/rfc8843#name-rtp-header-extensions-consi
  // ... the identifier used for a given extension MUST identify the same
  // extension across all the bundled media descriptions.
  std::vector<const cricket::ContentGroup*> bundle_groups =
      description.GetGroupsByName(cricket::GROUP_TYPE_BUNDLE);
  for (const cricket::ContentGroup* bundle_group : bundle_groups) {
    std::map<int, RtpExtension> id_to_extension;
    for (const std::string& content_name : bundle_group->content_names()) {
      const ContentInfo* content_description =
          description.GetContentByName(content_name);
      if (!content_description) {
        LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                             "A BUNDLE group contains a MID='" + content_name +
                                 "' matching no m= section.");
      }
      const cricket::MediaContentDescription* media_description =
          content_description->media_description();
      RTC_DCHECK(media_description);
      if (content_description->rejected || !media_description ||
          !media_description->has_codecs()) {
        continue;
      }

      for (const auto& extension : media_description->rtp_header_extensions()) {
        auto error =
            FindDuplicateHeaderExtensionIds(extension, id_to_extension);
        if (!error.ok()) {
          return error;
        }
      }
    }
  }
  return RTCError::OK();
}

RTCError ValidateRtpHeaderExtensionsForSpecSimulcast(
    const cricket::SessionDescription& description) {
  for (const ContentInfo& content : description.contents()) {
    if (content.type != MediaProtocolType::kRtp || content.rejected) {
      continue;
    }
    const auto media_description = content.media_description();
    if (!media_description->HasSimulcast()) {
      continue;
    }
    auto extensions = media_description->rtp_header_extensions();
    auto it = absl::c_find_if(extensions, [](const RtpExtension& ext) {
      return ext.uri == RtpExtension::kRidUri;
    });
    if (it == extensions.end()) {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                           "The media section with MID='" + content.mid() +
                               "' negotiates simulcast but does not negotiate "
                               "the RID RTP header extension.");
    }
  }
  return RTCError::OK();
}

RTCError ValidateSsrcGroups(const cricket::SessionDescription& description) {
  for (const ContentInfo& content : description.contents()) {
    if (content.type != MediaProtocolType::kRtp) {
      continue;
    }
    for (const StreamParams& stream : content.media_description()->streams()) {
      for (const cricket::SsrcGroup& group : stream.ssrc_groups) {
        // Validate the number of SSRCs for standard SSRC group semantics such
        // as FID and FEC-FR and the non-standard SIM group.
        if ((group.semantics == cricket::kFidSsrcGroupSemantics &&
             group.ssrcs.size() != 2) ||
            (group.semantics == cricket::kFecFrSsrcGroupSemantics &&
             group.ssrcs.size() != 2) ||
            (group.semantics == cricket::kSimSsrcGroupSemantics &&
             group.ssrcs.size() > kMaxSimulcastStreams)) {
          LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                               "The media section with MID='" + content.mid() +
                                   "' has a ssrc-group with semantics " +
                                   group.semantics +
                                   " and an unexpected number of SSRCs.");
        }
      }
    }
  }
  return RTCError::OK();
}

RTCError ValidatePayloadTypes(const cricket::SessionDescription& description) {
  for (const ContentInfo& content : description.contents()) {
    if (content.type != MediaProtocolType::kRtp) {
      continue;
    }
    const auto media_description = content.media_description();
    RTC_DCHECK(media_description);
    if (content.rejected || !media_description ||
        !media_description->has_codecs()) {
      continue;
    }
    const auto type = media_description->type();
    if (type == cricket::MEDIA_TYPE_AUDIO ||
        type == cricket::MEDIA_TYPE_VIDEO) {
      for (const auto& codec : media_description->codecs()) {
        if (!cricket::UsedPayloadTypes::IsIdValid(
                codec, media_description->rtcp_mux())) {
          LOG_AND_RETURN_ERROR(
              RTCErrorType::INVALID_PARAMETER,
              "The media section with MID='" + content.mid() +
                  "' used an invalid payload type " + rtc::ToString(codec.id) +
                  " for codec '" + codec.name + ", rtcp-mux:" +
                  (media_description->rtcp_mux() ? "enabled" : "disabled"));
        }
      }
    }
  }
  return RTCError::OK();
}

bool IsValidOfferToReceiveMedia(int value) {
  typedef PeerConnectionInterface::RTCOfferAnswerOptions Options;
  return (value >= Options::kUndefined) &&
         (value <= Options::kMaxOfferToReceiveMedia);
}

bool ValidateOfferAnswerOptions(
    const PeerConnectionInterface::RTCOfferAnswerOptions& rtc_options) {
  return IsValidOfferToReceiveMedia(rtc_options.offer_to_receive_audio) &&
         IsValidOfferToReceiveMedia(rtc_options.offer_to_receive_video);
}

// This method will extract any send encodings that were sent by the remote
// connection. This is currently only relevant for Simulcast scenario (where
// the number of layers may be communicated by the server).
std::vector<RtpEncodingParameters> GetSendEncodingsFromRemoteDescription(
    const MediaContentDescription& desc) {
  if (!desc.HasSimulcast()) {
    return {};
  }
  std::vector<RtpEncodingParameters> result;
  const SimulcastDescription& simulcast = desc.simulcast_description();

  // This is a remote description, the parameters we are after should appear
  // as receive streams.
  for (const auto& alternatives : simulcast.receive_layers()) {
    RTC_DCHECK(!alternatives.empty());
    // There is currently no way to specify or choose from alternatives.
    // We will always use the first alternative, which is the most preferred.
    const SimulcastLayer& layer = alternatives[0];
    RtpEncodingParameters parameters;
    parameters.rid = layer.rid;
    parameters.active = !layer.is_paused;
    result.push_back(parameters);
  }

  return result;
}

RTCError UpdateSimulcastLayerStatusInSender(
    const std::vector<SimulcastLayer>& layers,
    rtc::scoped_refptr<RtpSenderInternal> sender) {
  RTC_DCHECK(sender);
  RtpParameters parameters = sender->GetParametersInternalWithAllLayers();
  std::vector<std::string> disabled_layers;

  // The simulcast envelope cannot be changed, only the status of the streams.
  // So we will iterate over the send encodings rather than the layers.
  for (RtpEncodingParameters& encoding : parameters.encodings) {
    auto iter = std::find_if(layers.begin(), layers.end(),
                             [&encoding](const SimulcastLayer& layer) {
                               return layer.rid == encoding.rid;
                             });
    // A layer that cannot be found may have been removed by the remote party.
    if (iter == layers.end()) {
      disabled_layers.push_back(encoding.rid);
      continue;
    }

    encoding.active = !iter->is_paused;
  }

  RTCError result = sender->SetParametersInternalWithAllLayers(parameters);
  if (result.ok()) {
    result = sender->DisableEncodingLayers(disabled_layers);
  }

  return result;
}

bool SimulcastIsRejected(const ContentInfo* local_content,
                         const MediaContentDescription& answer_media_desc,
                         bool enable_encrypted_rtp_header_extensions) {
  bool simulcast_offered = local_content &&
                           local_content->media_description() &&
                           local_content->media_description()->HasSimulcast();
  bool simulcast_answered = answer_media_desc.HasSimulcast();
  bool rids_supported = RtpExtension::FindHeaderExtensionByUri(
      answer_media_desc.rtp_header_extensions(), RtpExtension::kRidUri,
      enable_encrypted_rtp_header_extensions
          ? RtpExtension::Filter::kPreferEncryptedExtension
          : RtpExtension::Filter::kDiscardEncryptedExtension);
  return simulcast_offered && (!simulcast_answered || !rids_supported);
}

RTCError DisableSimulcastInSender(
    rtc::scoped_refptr<RtpSenderInternal> sender) {
  RTC_DCHECK(sender);
  RtpParameters parameters = sender->GetParametersInternalWithAllLayers();
  if (parameters.encodings.size() <= 1) {
    return RTCError::OK();
  }

  std::vector<std::string> disabled_layers;
  std::transform(
      parameters.encodings.begin() + 1, parameters.encodings.end(),
      std::back_inserter(disabled_layers),
      [](const RtpEncodingParameters& encoding) { return encoding.rid; });
  return sender->DisableEncodingLayers(disabled_layers);
}

// The SDP parser used to populate these values by default for the 'content
// name' if an a=mid line was absent.
absl::string_view GetDefaultMidForPlanB(cricket::MediaType media_type) {
  switch (media_type) {
    case cricket::MEDIA_TYPE_AUDIO:
      return cricket::CN_AUDIO;
    case cricket::MEDIA_TYPE_VIDEO:
      return cricket::CN_VIDEO;
    case cricket::MEDIA_TYPE_DATA:
      return cricket::CN_DATA;
    case cricket::MEDIA_TYPE_UNSUPPORTED:
      return "not supported";
  }
  RTC_DCHECK_NOTREACHED();
  return "";
}

// Add options to |[audio/video]_media_description_options| from `senders`.
void AddPlanBRtpSenderOptions(
    const std::vector<rtc::scoped_refptr<
        RtpSenderProxyWithInternal<RtpSenderInternal>>>& senders,
    cricket::MediaDescriptionOptions* audio_media_description_options,
    cricket::MediaDescriptionOptions* video_media_description_options,
    int num_sim_layers) {
  for (const auto& sender : senders) {
    if (sender->media_type() == cricket::MEDIA_TYPE_AUDIO) {
      if (audio_media_description_options) {
        audio_media_description_options->AddAudioSender(
            sender->id(), sender->internal()->stream_ids());
      }
    } else {
      RTC_DCHECK(sender->media_type() == cricket::MEDIA_TYPE_VIDEO);
      if (video_media_description_options) {
        video_media_description_options->AddVideoSender(
            sender->id(), sender->internal()->stream_ids(), {},
            SimulcastLayerList(), num_sim_layers);
      }
    }
  }
}

cricket::MediaDescriptionOptions GetMediaDescriptionOptionsForTransceiver(
    RtpTransceiver* transceiver,
    const std::string& mid,
    bool is_create_offer) {
  // NOTE: a stopping transceiver should be treated as a stopped one in
  // createOffer as specified in
  // https://w3c.github.io/webrtc-pc/#dom-rtcpeerconnection-createoffer.
  bool stopped =
      is_create_offer ? transceiver->stopping() : transceiver->stopped();
  cricket::MediaDescriptionOptions media_description_options(
      transceiver->media_type(), mid, transceiver->direction(), stopped);
  media_description_options.codec_preferences =
      transceiver->codec_preferences();
  media_description_options.header_extensions =
      transceiver->GetHeaderExtensionsToNegotiate();
  // This behavior is specified in JSEP. The gist is that:
  // 1. The MSID is included if the RtpTransceiver's direction is sendonly or
  //    sendrecv.
  // 2. If the MSID is included, then it must be included in any subsequent
  //    offer/answer exactly the same until the RtpTransceiver is stopped.
  if (stopped || (!RtpTransceiverDirectionHasSend(transceiver->direction()) &&
                  !transceiver->has_ever_been_used_to_send())) {
    return media_description_options;
  }

  cricket::SenderOptions sender_options;
  sender_options.track_id = transceiver->sender()->id();
  sender_options.stream_ids = transceiver->sender()->stream_ids();

  // The following sets up RIDs and Simulcast.
  // RIDs are included if Simulcast is requested or if any RID was specified.
  RtpParameters send_parameters =
      transceiver->sender_internal()->GetParametersInternalWithAllLayers();
  bool has_rids = std::any_of(send_parameters.encodings.begin(),
                              send_parameters.encodings.end(),
                              [](const RtpEncodingParameters& encoding) {
                                return !encoding.rid.empty();
                              });

  std::vector<RidDescription> send_rids;
  SimulcastLayerList send_layers;
  for (const RtpEncodingParameters& encoding : send_parameters.encodings) {
    if (encoding.rid.empty()) {
      continue;
    }
    send_rids.push_back(RidDescription(encoding.rid, RidDirection::kSend));
    send_layers.AddLayer(SimulcastLayer(encoding.rid, !encoding.active));
  }

  if (has_rids) {
    sender_options.rids = send_rids;
  }

  sender_options.simulcast_layers = send_layers;
  // When RIDs are configured, we must set num_sim_layers to 0 to.
  // Otherwise, num_sim_layers must be 1 because either there is no
  // simulcast, or simulcast is acheived by munging the SDP.
  sender_options.num_sim_layers = has_rids ? 0 : 1;
  media_description_options.sender_options.push_back(sender_options);

  return media_description_options;
}

// Returns the ContentInfo at mline index `i`, or null if none exists.
const ContentInfo* GetContentByIndex(const SessionDescriptionInterface* sdesc,
                                     size_t i) {
  if (!sdesc) {
    return nullptr;
  }
  const ContentInfos& contents = sdesc->description()->contents();
  return (i < contents.size() ? &contents[i] : nullptr);
}

// From `rtc_options`, fill parts of `session_options` shared by all generated
// m= sectionss (in other words, nothing that involves a map/array).
void ExtractSharedMediaSessionOptions(
    const PeerConnectionInterface::RTCOfferAnswerOptions& rtc_options,
    cricket::MediaSessionOptions* session_options) {
  session_options->vad_enabled = rtc_options.voice_activity_detection;
  session_options->bundle_enabled = rtc_options.use_rtp_mux;
  session_options->raw_packetization_for_video =
      rtc_options.raw_packetization_for_video;
}

// Generate a RTCP CNAME when a PeerConnection is created.
std::string GenerateRtcpCname() {
  std::string cname;
  if (!rtc::CreateRandomString(kRtcpCnameLength, &cname)) {
    RTC_LOG(LS_ERROR) << "Failed to generate CNAME.";
    RTC_DCHECK_NOTREACHED();
  }
  return cname;
}

// Check if we can send `new_stream` on a PeerConnection.
bool CanAddLocalMediaStream(StreamCollectionInterface* current_streams,
                            MediaStreamInterface* new_stream) {
  if (!new_stream || !current_streams) {
    return false;
  }
  if (current_streams->find(new_stream->id()) != nullptr) {
    RTC_LOG(LS_ERROR) << "MediaStream with ID " << new_stream->id()
                      << " is already added.";
    return false;
  }
  return true;
}

rtc::scoped_refptr<DtlsTransport> LookupDtlsTransportByMid(
    rtc::Thread* network_thread,
    JsepTransportController* controller,
    const std::string& mid) {
  // TODO(tommi): Can we post this (and associated operations where this
  // function is called) to the network thread and avoid this BlockingCall?
  // We might be able to simplify a few things if we set the transport on
  // the network thread and then update the implementation to check that
  // the set_ and relevant get methods are always called on the network
  // thread (we'll need to update proxy maps).
  return network_thread->BlockingCall(
      [controller, &mid] { return controller->LookupDtlsTransportByMid(mid); });
}

bool ContentHasHeaderExtension(const cricket::ContentInfo& content_info,
                               absl::string_view header_extension_uri) {
  for (const RtpExtension& rtp_header_extension :
       content_info.media_description()->rtp_header_extensions()) {
    if (rtp_header_extension.uri == header_extension_uri) {
      return true;
    }
  }
  return false;
}

}  // namespace

void UpdateRtpHeaderExtensionPreferencesFromSdpMunging(
    const cricket::SessionDescription* description,
    TransceiverList* transceivers) {
  // This integrates the RTP Header Extension Control API and local SDP munging
  // for backward compability reasons. If something was enabled in the local
  // description via SDP munging, consider it non-stopped in the API as well
  // so that is shows up in subsequent offers/answers.
  RTC_DCHECK(description);
  RTC_DCHECK(transceivers);
  for (const auto& content : description->contents()) {
    auto transceiver = transceivers->FindByMid(content.name);
    if (!transceiver) {
      continue;
    }
    auto extension_capabilities = transceiver->GetHeaderExtensionsToNegotiate();
    // Set the capability of every extension we see here to "sendrecv".
    for (auto& ext : content.media_description()->rtp_header_extensions()) {
      auto it = absl::c_find_if(extension_capabilities,
                                [&ext](const RtpHeaderExtensionCapability c) {
                                  return ext.uri == c.uri;
                                });
      if (it != extension_capabilities.end()) {
        it->direction = RtpTransceiverDirection::kSendRecv;
      }
    }
    transceiver->SetHeaderExtensionsToNegotiate(extension_capabilities);
  }
}

// This class stores state related to a SetRemoteDescription operation, captures
// and reports potential errors that might occur and makes sure to notify the
// observer of the operation and the operations chain of completion.
class SdpOfferAnswerHandler::RemoteDescriptionOperation {
 public:
  RemoteDescriptionOperation(
      SdpOfferAnswerHandler* handler,
      std::unique_ptr<SessionDescriptionInterface> desc,
      rtc::scoped_refptr<SetRemoteDescriptionObserverInterface> observer,
      std::function<void()> operations_chain_callback)
      : handler_(handler),
        desc_(std::move(desc)),
        observer_(std::move(observer)),
        operations_chain_callback_(std::move(operations_chain_callback)),
        unified_plan_(handler_->IsUnifiedPlan()) {
    if (!desc_) {
      type_ = static_cast<SdpType>(-1);
      InvalidParam("SessionDescription is NULL.");
    } else {
      type_ = desc_->GetType();
    }
  }

  ~RemoteDescriptionOperation() {
    RTC_DCHECK_RUN_ON(handler_->signaling_thread());
    SignalCompletion();
    operations_chain_callback_();
  }

  bool ok() const { return error_.ok(); }

  // Notifies the observer that the operation is complete and releases the
  // reference to the observer.
  void SignalCompletion() {
    if (!observer_)
      return;

    if (!error_.ok() && type_ != static_cast<SdpType>(-1)) {
      std::string error_message =
          GetSetDescriptionErrorMessage(cricket::CS_REMOTE, type_, error_);
      RTC_LOG(LS_ERROR) << error_message;
      error_.set_message(error_message);
    }

    observer_->OnSetRemoteDescriptionComplete(error_);
    observer_ = nullptr;  // Only fire the notification once.
  }

  // If a session error has occurred the PeerConnection is in a possibly
  // inconsistent state so fail right away.
  bool HaveSessionError() {
    RTC_DCHECK(ok());
    if (handler_->session_error() != SessionError::kNone)
      InternalError(handler_->GetSessionErrorMsg());
    return !ok();
  }

  // Returns true if the operation was a rollback operation. If this function
  // returns true, the caller should consider the operation complete. Otherwise
  // proceed to the next step.
  bool MaybeRollback() {
    RTC_DCHECK_RUN_ON(handler_->signaling_thread());
    RTC_DCHECK(ok());
    if (type_ != SdpType::kRollback) {
      // Check if we can do an implicit rollback.
      if (type_ == SdpType::kOffer && unified_plan_ &&
          handler_->pc_->configuration()->enable_implicit_rollback &&
          handler_->signaling_state() ==
              PeerConnectionInterface::kHaveLocalOffer) {
        handler_->Rollback(type_);
      }
      return false;
    }

    if (unified_plan_) {
      error_ = handler_->Rollback(type_);
    } else if (type_ == SdpType::kRollback) {
      Unsupported("Rollback not supported in Plan B");
    }

    return true;
  }

  // Report to UMA the format of the received offer or answer.
  void ReportOfferAnswerUma() {
    RTC_DCHECK(ok());
    if (type_ == SdpType::kOffer || type_ == SdpType::kAnswer) {
      handler_->pc_->ReportSdpBundleUsage(*desc_.get());
    }
  }

  // Checks if the session description for the operation is valid. If not, the
  // function captures error information and returns false. Note that if the
  // return value is false, the operation should be considered done.
  bool IsDescriptionValid() {
    RTC_DCHECK_RUN_ON(handler_->signaling_thread());
    RTC_DCHECK(ok());
    RTC_DCHECK(bundle_groups_by_mid_.empty()) << "Already called?";
    bundle_groups_by_mid_ = GetBundleGroupsByMid(description());
    error_ = handler_->ValidateSessionDescription(
        desc_.get(), cricket::CS_REMOTE, bundle_groups_by_mid_);
    return ok();
  }

  // Transfers ownership of the session description object over to `handler_`.
  bool ReplaceRemoteDescriptionAndCheckError() {
    RTC_DCHECK_RUN_ON(handler_->signaling_thread());
    RTC_DCHECK(ok());
    RTC_DCHECK(desc_);
    RTC_DCHECK(!replaced_remote_description_);
#if RTC_DCHECK_IS_ON
    const auto* existing_remote_description = handler_->remote_description();
#endif

    error_ = handler_->ReplaceRemoteDescription(std::move(desc_), type_,
                                                &replaced_remote_description_);

    if (ok()) {
#if RTC_DCHECK_IS_ON
      // Sanity check that our `old_remote_description()` method always returns
      // the same value as `remote_description()` did before the call to
      // ReplaceRemoteDescription.
      RTC_DCHECK_EQ(existing_remote_description, old_remote_description());
#endif
    } else {
      SetAsSessionError();
    }

    return ok();
  }

  bool UpdateChannels() {
    RTC_DCHECK(ok());
    RTC_DCHECK(!desc_) << "ReplaceRemoteDescription hasn't been called";

    const auto* remote_description = handler_->remote_description();

    const cricket::SessionDescription* session_desc =
        remote_description->description();

    // Transport and Media channels will be created only when offer is set.
    if (unified_plan_) {
      error_ = handler_->UpdateTransceiversAndDataChannels(
          cricket::CS_REMOTE, *remote_description,
          handler_->local_description(), old_remote_description(),
          bundle_groups_by_mid_);
    } else {
      // Media channels will be created only when offer is set. These may use
      // new transports just created by PushdownTransportDescription.
      if (type_ == SdpType::kOffer) {
        // TODO(mallinath) - Handle CreateChannel failure, as new local
        // description is applied. Restore back to old description.
        error_ = handler_->CreateChannels(*session_desc);
      }
      // Remove unused channels if MediaContentDescription is rejected.
      handler_->RemoveUnusedChannels(session_desc);
    }

    return ok();
  }

  bool UpdateSessionState() {
    RTC_DCHECK(ok());
    error_ = handler_->UpdateSessionState(
        type_, cricket::CS_REMOTE,
        handler_->remote_description()->description(), bundle_groups_by_mid_);
    if (!ok())
      SetAsSessionError();
    return ok();
  }

  bool UseCandidatesInRemoteDescription() {
    RTC_DCHECK(ok());
    if (handler_->local_description() &&
        !handler_->UseCandidatesInRemoteDescription()) {
      InvalidParam(kInvalidCandidates);
    }
    return ok();
  }

  // Convenience getter for desc_->GetType().
  SdpType type() const { return type_; }
  bool unified_plan() const { return unified_plan_; }
  cricket::SessionDescription* description() { return desc_->description(); }

  const SessionDescriptionInterface* old_remote_description() const {
    RTC_DCHECK(!desc_) << "Called before replacing the remote description";
    if (type_ == SdpType::kAnswer)
      return replaced_remote_description_.get();
    return replaced_remote_description_
               ? replaced_remote_description_.get()
               : handler_->current_remote_description();
  }

  // Returns a reference to a cached map of bundle groups ordered by mid.
  // Note that this will only be valid after a successful call to
  // `IsDescriptionValid`.
  const std::map<std::string, const cricket::ContentGroup*>&
  bundle_groups_by_mid() const {
    RTC_DCHECK(ok());
    return bundle_groups_by_mid_;
  }

 private:
  // Convenience methods for populating the embedded `error_` object.
  void Unsupported(std::string message) {
    SetError(RTCErrorType::UNSUPPORTED_OPERATION, std::move(message));
  }

  void InvalidParam(std::string message) {
    SetError(RTCErrorType::INVALID_PARAMETER, std::move(message));
  }

  void InternalError(std::string message) {
    SetError(RTCErrorType::INTERNAL_ERROR, std::move(message));
  }

  void SetError(RTCErrorType type, std::string message) {
    RTC_DCHECK(ok()) << "Overwriting an existing error?";
    error_ = RTCError(type, std::move(message));
  }

  // Called when the PeerConnection could be in an inconsistent state and we set
  // the session error so that future calls to
  // SetLocalDescription/SetRemoteDescription fail.
  void SetAsSessionError() {
    RTC_DCHECK(!ok());
    handler_->SetSessionError(SessionError::kContent, error_.message());
  }

  SdpOfferAnswerHandler* const handler_;
  std::unique_ptr<SessionDescriptionInterface> desc_;
  // Keeps the replaced session description object alive while the operation
  // is taking place since methods that depend on `old_remote_description()`
  // for updating the state, need it.
  std::unique_ptr<SessionDescriptionInterface> replaced_remote_description_;
  rtc::scoped_refptr<SetRemoteDescriptionObserverInterface> observer_;
  std::function<void()> operations_chain_callback_;
  RTCError error_ = RTCError::OK();
  std::map<std::string, const cricket::ContentGroup*> bundle_groups_by_mid_;
  SdpType type_;
  const bool unified_plan_;
};
// Used by parameterless SetLocalDescription() to create an offer or answer.
// Upon completion of creating the session description, SetLocalDescription() is
// invoked with the result.
class SdpOfferAnswerHandler::ImplicitCreateSessionDescriptionObserver
    : public CreateSessionDescriptionObserver {
 public:
  ImplicitCreateSessionDescriptionObserver(
      rtc::WeakPtr<SdpOfferAnswerHandler> sdp_handler,
      rtc::scoped_refptr<SetLocalDescriptionObserverInterface>
          set_local_description_observer)
      : sdp_handler_(std::move(sdp_handler)),
        set_local_description_observer_(
            std::move(set_local_description_observer)) {}
  ~ImplicitCreateSessionDescriptionObserver() override {
    RTC_DCHECK(was_called_);
  }

  void SetOperationCompleteCallback(
      std::function<void()> operation_complete_callback) {
    operation_complete_callback_ = std::move(operation_complete_callback);
  }

  bool was_called() const { return was_called_; }

  void OnSuccess(SessionDescriptionInterface* desc_ptr) override {
    RTC_DCHECK(!was_called_);
    std::unique_ptr<SessionDescriptionInterface> desc(desc_ptr);
    was_called_ = true;

    // Abort early if `pc_` is no longer valid.
    if (!sdp_handler_) {
      operation_complete_callback_();
      return;
    }
    // DoSetLocalDescription() is a synchronous operation that invokes
    // `set_local_description_observer_` with the result.
    sdp_handler_->DoSetLocalDescription(
        std::move(desc), std::move(set_local_description_observer_));
    operation_complete_callback_();
  }

  void OnFailure(RTCError error) override {
    RTC_DCHECK(!was_called_);
    was_called_ = true;
    set_local_description_observer_->OnSetLocalDescriptionComplete(RTCError(
        error.type(), std::string("SetLocalDescription failed to create "
                                  "session description - ") +
                          error.message()));
    operation_complete_callback_();
  }

 private:
  bool was_called_ = false;
  rtc::WeakPtr<SdpOfferAnswerHandler> sdp_handler_;
  rtc::scoped_refptr<SetLocalDescriptionObserverInterface>
      set_local_description_observer_;
  std::function<void()> operation_complete_callback_;
};

// Wraps a CreateSessionDescriptionObserver and an OperationsChain operation
// complete callback. When the observer is invoked, the wrapped observer is
// invoked followed by invoking the completion callback.
class CreateSessionDescriptionObserverOperationWrapper
    : public CreateSessionDescriptionObserver {
 public:
  CreateSessionDescriptionObserverOperationWrapper(
      rtc::scoped_refptr<CreateSessionDescriptionObserver> observer,
      std::function<void()> operation_complete_callback)
      : observer_(std::move(observer)),
        operation_complete_callback_(std::move(operation_complete_callback)) {
    RTC_DCHECK(observer_);
  }
  ~CreateSessionDescriptionObserverOperationWrapper() override {
#if RTC_DCHECK_IS_ON
    RTC_DCHECK(was_called_);
#endif
  }

  void OnSuccess(SessionDescriptionInterface* desc) override {
#if RTC_DCHECK_IS_ON
    RTC_DCHECK(!was_called_);
    was_called_ = true;
#endif  // RTC_DCHECK_IS_ON
    // Completing the operation before invoking the observer allows the observer
    // to execute SetLocalDescription() without delay.
    operation_complete_callback_();
    observer_->OnSuccess(desc);
  }

  void OnFailure(RTCError error) override {
#if RTC_DCHECK_IS_ON
    RTC_DCHECK(!was_called_);
    was_called_ = true;
#endif  // RTC_DCHECK_IS_ON
    operation_complete_callback_();
    observer_->OnFailure(std::move(error));
  }

 private:
#if RTC_DCHECK_IS_ON
  bool was_called_ = false;
#endif  // RTC_DCHECK_IS_ON
  rtc::scoped_refptr<CreateSessionDescriptionObserver> observer_;
  std::function<void()> operation_complete_callback_;
};

// Wrapper for SetSessionDescriptionObserver that invokes the success or failure
// callback in a posted message handled by the peer connection. This introduces
// a delay that prevents recursive API calls by the observer, but this also
// means that the PeerConnection can be modified before the observer sees the
// result of the operation. This is ill-advised for synchronizing states.
//
// Implements both the SetLocalDescriptionObserverInterface and the
// SetRemoteDescriptionObserverInterface.
class SdpOfferAnswerHandler::SetSessionDescriptionObserverAdapter
    : public SetLocalDescriptionObserverInterface,
      public SetRemoteDescriptionObserverInterface {
 public:
  SetSessionDescriptionObserverAdapter(
      rtc::WeakPtr<SdpOfferAnswerHandler> handler,
      rtc::scoped_refptr<SetSessionDescriptionObserver> inner_observer)
      : handler_(std::move(handler)),
        inner_observer_(std::move(inner_observer)) {}

  // SetLocalDescriptionObserverInterface implementation.
  void OnSetLocalDescriptionComplete(RTCError error) override {
    OnSetDescriptionComplete(std::move(error));
  }
  // SetRemoteDescriptionObserverInterface implementation.
  void OnSetRemoteDescriptionComplete(RTCError error) override {
    OnSetDescriptionComplete(std::move(error));
  }

 private:
  void OnSetDescriptionComplete(RTCError error) {
    if (!handler_)
      return;
    if (error.ok()) {
      handler_->pc_->message_handler()->PostSetSessionDescriptionSuccess(
          inner_observer_.get());
    } else {
      handler_->pc_->message_handler()->PostSetSessionDescriptionFailure(
          inner_observer_.get(), std::move(error));
    }
  }

  rtc::WeakPtr<SdpOfferAnswerHandler> handler_;
  rtc::scoped_refptr<SetSessionDescriptionObserver> inner_observer_;
};

class SdpOfferAnswerHandler::LocalIceCredentialsToReplace {
 public:
  // Sets the ICE credentials that need restarting to the ICE credentials of
  // the current and pending descriptions.
  void SetIceCredentialsFromLocalDescriptions(
      const SessionDescriptionInterface* current_local_description,
      const SessionDescriptionInterface* pending_local_description) {
    ice_credentials_.clear();
    if (current_local_description) {
      AppendIceCredentialsFromSessionDescription(*current_local_description);
    }
    if (pending_local_description) {
      AppendIceCredentialsFromSessionDescription(*pending_local_description);
    }
  }

  void ClearIceCredentials() { ice_credentials_.clear(); }

  // Returns true if we have ICE credentials that need restarting.
  bool HasIceCredentials() const { return !ice_credentials_.empty(); }

  // Returns true if `local_description` shares no ICE credentials with the
  // ICE credentials that need restarting.
  bool SatisfiesIceRestart(
      const SessionDescriptionInterface& local_description) const {
    for (const auto& transport_info :
         local_description.description()->transport_infos()) {
      if (ice_credentials_.find(std::make_pair(
              transport_info.description.ice_ufrag,
              transport_info.description.ice_pwd)) != ice_credentials_.end()) {
        return false;
      }
    }
    return true;
  }

 private:
  void AppendIceCredentialsFromSessionDescription(
      const SessionDescriptionInterface& desc) {
    for (const auto& transport_info : desc.description()->transport_infos()) {
      ice_credentials_.insert(
          std::make_pair(transport_info.description.ice_ufrag,
                         transport_info.description.ice_pwd));
    }
  }

  std::set<std::pair<std::string, std::string>> ice_credentials_;
};

SdpOfferAnswerHandler::SdpOfferAnswerHandler(PeerConnectionSdpMethods* pc,
                                             ConnectionContext* context)
    : pc_(pc),
      context_(context),
      local_streams_(StreamCollection::Create()),
      remote_streams_(StreamCollection::Create()),
      operations_chain_(rtc::OperationsChain::Create()),
      rtcp_cname_(GenerateRtcpCname()),
      local_ice_credentials_to_replace_(new LocalIceCredentialsToReplace()),
      weak_ptr_factory_(this) {
  operations_chain_->SetOnChainEmptyCallback(
      [this_weak_ptr = weak_ptr_factory_.GetWeakPtr()]() {
        if (!this_weak_ptr)
          return;
        this_weak_ptr->OnOperationsChainEmpty();
      });
}

SdpOfferAnswerHandler::~SdpOfferAnswerHandler() {}

// Static
std::unique_ptr<SdpOfferAnswerHandler> SdpOfferAnswerHandler::Create(
    PeerConnectionSdpMethods* pc,
    const PeerConnectionInterface::RTCConfiguration& configuration,
    PeerConnectionDependencies& dependencies,
    ConnectionContext* context) {
  auto handler = absl::WrapUnique(new SdpOfferAnswerHandler(pc, context));
  handler->Initialize(configuration, dependencies, context);
  return handler;
}

void SdpOfferAnswerHandler::Initialize(
    const PeerConnectionInterface::RTCConfiguration& configuration,
    PeerConnectionDependencies& dependencies,
    ConnectionContext* context) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // 100 kbps is used by default, but can be overriden by a non-standard
  // RTCConfiguration value (not available on Web).
  video_options_.screencast_min_bitrate_kbps =
      configuration.screencast_min_bitrate.value_or(100);

  audio_options_.audio_jitter_buffer_max_packets =
      configuration.audio_jitter_buffer_max_packets;

  audio_options_.audio_jitter_buffer_fast_accelerate =
      configuration.audio_jitter_buffer_fast_accelerate;

  audio_options_.audio_jitter_buffer_min_delay_ms =
      configuration.audio_jitter_buffer_min_delay_ms;

  // Obtain a certificate from RTCConfiguration if any were provided (optional).
  rtc::scoped_refptr<rtc::RTCCertificate> certificate;
  if (!configuration.certificates.empty()) {
    // TODO(hbos,torbjorng): Decide on certificate-selection strategy instead of
    // just picking the first one. The decision should be made based on the DTLS
    // handshake. The DTLS negotiations need to know about all certificates.
    certificate = configuration.certificates[0];
  }

  webrtc_session_desc_factory_ =
      std::make_unique<WebRtcSessionDescriptionFactory>(
          context, this, pc_->session_id(), pc_->dtls_enabled(),
          std::move(dependencies.cert_generator), std::move(certificate),
          [this](const rtc::scoped_refptr<rtc::RTCCertificate>& certificate) {
            RTC_DCHECK_RUN_ON(signaling_thread());
            transport_controller_s()->SetLocalCertificate(certificate);
          },
          pc_->trials());

  if (pc_->options()->disable_encryption) {
    RTC_LOG(LS_INFO)
        << "Disabling encryption. This should only be done in tests.";
    webrtc_session_desc_factory_->SetInsecureForTesting();
  }

  webrtc_session_desc_factory_->set_enable_encrypted_rtp_header_extensions(
      pc_->GetCryptoOptions().srtp.enable_encrypted_rtp_header_extensions);
  webrtc_session_desc_factory_->set_is_unified_plan(IsUnifiedPlan());

  if (dependencies.video_bitrate_allocator_factory) {
    video_bitrate_allocator_factory_ =
        std::move(dependencies.video_bitrate_allocator_factory);
  } else {
    video_bitrate_allocator_factory_ =
        CreateBuiltinVideoBitrateAllocatorFactory();
  }
}

// ==================================================================
// Access to pc_ variables
cricket::MediaEngineInterface* SdpOfferAnswerHandler::media_engine() const {
  RTC_DCHECK(context_);
  return context_->media_engine();
}

TransceiverList* SdpOfferAnswerHandler::transceivers() {
  if (!pc_->rtp_manager()) {
    return nullptr;
  }
  return pc_->rtp_manager()->transceivers();
}

const TransceiverList* SdpOfferAnswerHandler::transceivers() const {
  if (!pc_->rtp_manager()) {
    return nullptr;
  }
  return pc_->rtp_manager()->transceivers();
}
JsepTransportController* SdpOfferAnswerHandler::transport_controller_s() {
  return pc_->transport_controller_s();
}
JsepTransportController* SdpOfferAnswerHandler::transport_controller_n() {
  return pc_->transport_controller_n();
}
const JsepTransportController* SdpOfferAnswerHandler::transport_controller_s()
    const {
  return pc_->transport_controller_s();
}
const JsepTransportController* SdpOfferAnswerHandler::transport_controller_n()
    const {
  return pc_->transport_controller_n();
}
DataChannelController* SdpOfferAnswerHandler::data_channel_controller() {
  return pc_->data_channel_controller();
}
const DataChannelController* SdpOfferAnswerHandler::data_channel_controller()
    const {
  return pc_->data_channel_controller();
}
cricket::PortAllocator* SdpOfferAnswerHandler::port_allocator() {
  return pc_->port_allocator();
}
const cricket::PortAllocator* SdpOfferAnswerHandler::port_allocator() const {
  return pc_->port_allocator();
}
RtpTransmissionManager* SdpOfferAnswerHandler::rtp_manager() {
  return pc_->rtp_manager();
}
const RtpTransmissionManager* SdpOfferAnswerHandler::rtp_manager() const {
  return pc_->rtp_manager();
}

// ===================================================================

void SdpOfferAnswerHandler::PrepareForShutdown() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  weak_ptr_factory_.InvalidateWeakPtrs();
}

void SdpOfferAnswerHandler::Close() {
  ChangeSignalingState(PeerConnectionInterface::kClosed);
}

void SdpOfferAnswerHandler::RestartIce() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  local_ice_credentials_to_replace_->SetIceCredentialsFromLocalDescriptions(
      current_local_description(), pending_local_description());
  UpdateNegotiationNeeded();
}

rtc::Thread* SdpOfferAnswerHandler::signaling_thread() const {
  return context_->signaling_thread();
}

rtc::Thread* SdpOfferAnswerHandler::network_thread() const {
  return context_->network_thread();
}

void SdpOfferAnswerHandler::CreateOffer(
    CreateSessionDescriptionObserver* observer,
    const PeerConnectionInterface::RTCOfferAnswerOptions& options) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // Chain this operation. If asynchronous operations are pending on the chain,
  // this operation will be queued to be invoked, otherwise the contents of the
  // lambda will execute immediately.
  operations_chain_->ChainOperation(
      [this_weak_ptr = weak_ptr_factory_.GetWeakPtr(),
       observer_refptr =
           rtc::scoped_refptr<CreateSessionDescriptionObserver>(observer),
       options](std::function<void()> operations_chain_callback) {
        // Abort early if `this_weak_ptr` is no longer valid.
        if (!this_weak_ptr) {
          observer_refptr->OnFailure(
              RTCError(RTCErrorType::INTERNAL_ERROR,
                       "CreateOffer failed because the session was shut down"));
          operations_chain_callback();
          return;
        }
        // The operation completes asynchronously when the wrapper is invoked.
        auto observer_wrapper = rtc::make_ref_counted<
            CreateSessionDescriptionObserverOperationWrapper>(
            std::move(observer_refptr), std::move(operations_chain_callback));
        this_weak_ptr->DoCreateOffer(options, observer_wrapper);
      });
}

void SdpOfferAnswerHandler::SetLocalDescription(
    SetSessionDescriptionObserver* observer,
    SessionDescriptionInterface* desc_ptr) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // Chain this operation. If asynchronous operations are pending on the chain,
  // this operation will be queued to be invoked, otherwise the contents of the
  // lambda will execute immediately.
  operations_chain_->ChainOperation(
      [this_weak_ptr = weak_ptr_factory_.GetWeakPtr(),
       observer_refptr =
           rtc::scoped_refptr<SetSessionDescriptionObserver>(observer),
       desc = std::unique_ptr<SessionDescriptionInterface>(desc_ptr)](
          std::function<void()> operations_chain_callback) mutable {
        // Abort early if `this_weak_ptr` is no longer valid.
        if (!this_weak_ptr) {
          // For consistency with SetSessionDescriptionObserverAdapter whose
          // posted messages doesn't get processed when the PC is destroyed, we
          // do not inform `observer_refptr` that the operation failed.
          operations_chain_callback();
          return;
        }
        // SetSessionDescriptionObserverAdapter takes care of making sure the
        // `observer_refptr` is invoked in a posted message.
        this_weak_ptr->DoSetLocalDescription(
            std::move(desc),
            rtc::make_ref_counted<SetSessionDescriptionObserverAdapter>(
                this_weak_ptr, observer_refptr));
        // For backwards-compatability reasons, we declare the operation as
        // completed here (rather than in a post), so that the operation chain
        // is not blocked by this operation when the observer is invoked. This
        // allows the observer to trigger subsequent offer/answer operations
        // synchronously if the operation chain is now empty.
        operations_chain_callback();
      });
}

void SdpOfferAnswerHandler::SetLocalDescription(
    std::unique_ptr<SessionDescriptionInterface> desc,
    rtc::scoped_refptr<SetLocalDescriptionObserverInterface> observer) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // Chain this operation. If asynchronous operations are pending on the chain,
  // this operation will be queued to be invoked, otherwise the contents of the
  // lambda will execute immediately.
  operations_chain_->ChainOperation(
      [this_weak_ptr = weak_ptr_factory_.GetWeakPtr(), observer,
       desc = std::move(desc)](
          std::function<void()> operations_chain_callback) mutable {
        // Abort early if `this_weak_ptr` is no longer valid.
        if (!this_weak_ptr) {
          observer->OnSetLocalDescriptionComplete(RTCError(
              RTCErrorType::INTERNAL_ERROR,
              "SetLocalDescription failed because the session was shut down"));
          operations_chain_callback();
          return;
        }
        this_weak_ptr->DoSetLocalDescription(std::move(desc), observer);
        // DoSetLocalDescription() is implemented as a synchronous operation.
        // The `observer` will already have been informed that it completed, and
        // we can mark this operation as complete without any loose ends.
        operations_chain_callback();
      });
}

void SdpOfferAnswerHandler::SetLocalDescription(
    SetSessionDescriptionObserver* observer) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  SetLocalDescription(
      rtc::make_ref_counted<SetSessionDescriptionObserverAdapter>(
          weak_ptr_factory_.GetWeakPtr(),
          rtc::scoped_refptr<SetSessionDescriptionObserver>(observer)));
}

void SdpOfferAnswerHandler::SetLocalDescription(
    rtc::scoped_refptr<SetLocalDescriptionObserverInterface> observer) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // The `create_sdp_observer` handles performing DoSetLocalDescription() with
  // the resulting description as well as completing the operation.
  auto create_sdp_observer =
      rtc::make_ref_counted<ImplicitCreateSessionDescriptionObserver>(
          weak_ptr_factory_.GetWeakPtr(), observer);
  // Chain this operation. If asynchronous operations are pending on the chain,
  // this operation will be queued to be invoked, otherwise the contents of the
  // lambda will execute immediately.
  operations_chain_->ChainOperation(
      [this_weak_ptr = weak_ptr_factory_.GetWeakPtr(),
       create_sdp_observer](std::function<void()> operations_chain_callback) {
        // The `create_sdp_observer` is responsible for completing the
        // operation.
        create_sdp_observer->SetOperationCompleteCallback(
            std::move(operations_chain_callback));
        // Abort early if `this_weak_ptr` is no longer valid. This triggers the
        // same code path as if DoCreateOffer() or DoCreateAnswer() failed.
        if (!this_weak_ptr) {
          create_sdp_observer->OnFailure(RTCError(
              RTCErrorType::INTERNAL_ERROR,
              "SetLocalDescription failed because the session was shut down"));
          return;
        }
        switch (this_weak_ptr->signaling_state()) {
          case PeerConnectionInterface::kStable:
          case PeerConnectionInterface::kHaveLocalOffer:
          case PeerConnectionInterface::kHaveRemotePrAnswer:
            // TODO(hbos): If [LastCreatedOffer] exists and still represents the
            // current state of the system, use that instead of creating another
            // offer.
            this_weak_ptr->DoCreateOffer(
                PeerConnectionInterface::RTCOfferAnswerOptions(),
                create_sdp_observer);
            break;
          case PeerConnectionInterface::kHaveLocalPrAnswer:
          case PeerConnectionInterface::kHaveRemoteOffer:
            // TODO(hbos): If [LastCreatedAnswer] exists and still represents
            // the current state of the system, use that instead of creating
            // another answer.
            this_weak_ptr->DoCreateAnswer(
                PeerConnectionInterface::RTCOfferAnswerOptions(),
                create_sdp_observer);
            break;
          case PeerConnectionInterface::kClosed:
            create_sdp_observer->OnFailure(RTCError(
                RTCErrorType::INVALID_STATE,
                "SetLocalDescription called when PeerConnection is closed."));
            break;
        }
      });
}

RTCError SdpOfferAnswerHandler::ApplyLocalDescription(
    std::unique_ptr<SessionDescriptionInterface> desc,
    const std::map<std::string, const cricket::ContentGroup*>&
        bundle_groups_by_mid) {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::ApplyLocalDescription");
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(desc);

  // Invalidate the stats caches to make sure that they get
  // updated the next time getStats() gets called, as updating the session
  // description affects the stats.
  pc_->ClearStatsCache();

  // Take a reference to the old local description since it's used below to
  // compare against the new local description. When setting the new local
  // description, grab ownership of the replaced session description in case it
  // is the same as `old_local_description`, to keep it alive for the duration
  // of the method.
  const SessionDescriptionInterface* old_local_description =
      local_description();
  std::unique_ptr<SessionDescriptionInterface> replaced_local_description;
  SdpType type = desc->GetType();
  if (type == SdpType::kAnswer) {
    replaced_local_description = pending_local_description_
                                     ? std::move(pending_local_description_)
                                     : std::move(current_local_description_);
    current_local_description_ = std::move(desc);
    pending_local_description_ = nullptr;
    current_remote_description_ = std::move(pending_remote_description_);
  } else {
    replaced_local_description = std::move(pending_local_description_);
    pending_local_description_ = std::move(desc);
  }
  if (!initial_offerer_) {
    initial_offerer_.emplace(type == SdpType::kOffer);
  }
  // The session description to apply now must be accessed by
  // `local_description()`.
  RTC_DCHECK(local_description());

  if (!is_caller_) {
    if (remote_description()) {
      // Remote description was applied first, so this PC is the callee.
      is_caller_ = false;
    } else {
      // Local description is applied first, so this PC is the caller.
      is_caller_ = true;
    }
  }

  RTCError error = PushdownTransportDescription(cricket::CS_LOCAL, type);
  if (!error.ok()) {
    return error;
  }

  if (IsUnifiedPlan()) {
    error = UpdateTransceiversAndDataChannels(
        cricket::CS_LOCAL, *local_description(), old_local_description,
        remote_description(), bundle_groups_by_mid);
    if (!error.ok()) {
      RTC_LOG(LS_ERROR) << error.message() << " (" << SdpTypeToString(type)
                        << ")";
      return error;
    }
    if (ConfiguredForMedia()) {
      std::vector<rtc::scoped_refptr<RtpTransceiverInterface>> remove_list;
      std::vector<rtc::scoped_refptr<MediaStreamInterface>> removed_streams;
      for (const auto& transceiver_ext : transceivers()->List()) {
        auto transceiver = transceiver_ext->internal();
        if (transceiver->stopped()) {
          continue;
        }

        // 2.2.7.1.1.(6-9): Set sender and receiver's transport slots.
        // Note that code paths that don't set MID won't be able to use
        // information about DTLS transports.
        if (transceiver->mid()) {
          auto dtls_transport = LookupDtlsTransportByMid(
              context_->network_thread(), transport_controller_s(),
              *transceiver->mid());
          transceiver->sender_internal()->set_transport(dtls_transport);
          transceiver->receiver_internal()->set_transport(dtls_transport);
        }

        const ContentInfo* content =
            FindMediaSectionForTransceiver(transceiver, local_description());
        if (!content) {
          continue;
        }
        const MediaContentDescription* media_desc =
            content->media_description();
        // 2.2.7.1.6: If description is of type "answer" or "pranswer", then run
        // the following steps:
        if (type == SdpType::kPrAnswer || type == SdpType::kAnswer) {
          // 2.2.7.1.6.1: If direction is "sendonly" or "inactive", and
          // transceiver's [[FiredDirection]] slot is either "sendrecv" or
          // "recvonly", process the removal of a remote track for the media
          // description, given transceiver, removeList, and muteTracks.
          if (!RtpTransceiverDirectionHasRecv(media_desc->direction()) &&
              (transceiver->fired_direction() &&
               RtpTransceiverDirectionHasRecv(
                   *transceiver->fired_direction()))) {
            ProcessRemovalOfRemoteTrack(transceiver_ext, &remove_list,
                                        &removed_streams);
          }
          // 2.2.7.1.6.2: Set transceiver's [[CurrentDirection]] and
          // [[FiredDirection]] slots to direction.
          transceiver->set_current_direction(media_desc->direction());
          transceiver->set_fired_direction(media_desc->direction());
        }
      }
      auto observer = pc_->Observer();
      for (const auto& transceiver : remove_list) {
        observer->OnRemoveTrack(transceiver->receiver());
      }
      for (const auto& stream : removed_streams) {
        observer->OnRemoveStream(stream);
      }
    }
  } else {
    // Media channels will be created only when offer is set. These may use new
    // transports just created by PushdownTransportDescription.
    if (type == SdpType::kOffer) {
      // TODO(bugs.webrtc.org/4676) - Handle CreateChannel failure, as new local
      // description is applied. Restore back to old description.
      error = CreateChannels(*local_description()->description());
      if (!error.ok()) {
        RTC_LOG(LS_ERROR) << error.message() << " (" << SdpTypeToString(type)
                          << ")";
        return error;
      }
    }
    // Remove unused channels if MediaContentDescription is rejected.
    RemoveUnusedChannels(local_description()->description());
  }

  error = UpdateSessionState(type, cricket::CS_LOCAL,
                             local_description()->description(),
                             bundle_groups_by_mid);
  if (!error.ok()) {
    RTC_LOG(LS_ERROR) << error.message() << " (" << SdpTypeToString(type)
                      << ")";
    return error;
  }

  // Now that we have a local description, we can push down remote candidates.
  UseCandidatesInRemoteDescription();

  pending_ice_restarts_.clear();
  if (session_error() != SessionError::kNone) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INTERNAL_ERROR, GetSessionErrorMsg());
  }

  // If setting the description decided our SSL role, allocate any necessary
  // SCTP sids.
  AllocateSctpSids();

  // Validate SSRCs, we do not allow duplicates.
  if (ConfiguredForMedia()) {
    std::set<uint32_t> used_ssrcs;
    for (const auto& content : local_description()->description()->contents()) {
      for (const auto& stream : content.media_description()->streams()) {
        for (uint32_t ssrc : stream.ssrcs) {
          auto result = used_ssrcs.insert(ssrc);
          if (!result.second) {
            LOG_AND_RETURN_ERROR(
                RTCErrorType::INVALID_PARAMETER,
                "Duplicate ssrc " + rtc::ToString(ssrc) + " is not allowed");
          }
        }
      }
    }
  }

  if (IsUnifiedPlan()) {
    if (ConfiguredForMedia()) {
      // We must use List and not ListInternal here because
      // transceivers()->StableState() is indexed by the non-internal refptr.
      for (const auto& transceiver_ext : transceivers()->List()) {
        auto transceiver = transceiver_ext->internal();
        if (transceiver->stopped()) {
          continue;
        }
        const ContentInfo* content =
            FindMediaSectionForTransceiver(transceiver, local_description());
        if (!content) {
          continue;
        }
        cricket::ChannelInterface* channel = transceiver->channel();
        if (content->rejected || !channel || channel->local_streams().empty()) {
          // 0 is a special value meaning "this sender has no associated send
          // stream". Need to call this so the sender won't attempt to configure
          // a no longer existing stream and run into DCHECKs in the lower
          // layers.
          transceiver->sender_internal()->SetSsrc(0);
        } else {
          // Get the StreamParams from the channel which could generate SSRCs.
          const std::vector<StreamParams>& streams = channel->local_streams();
          transceiver->sender_internal()->set_stream_ids(
              streams[0].stream_ids());
          auto encodings =
              transceiver->sender_internal()->init_send_encodings();
          transceiver->sender_internal()->SetSsrc(streams[0].first_ssrc());
          if (!encodings.empty()) {
            transceivers()
                ->StableState(transceiver_ext)
                ->SetInitSendEncodings(encodings);
          }
        }
      }
    }
  } else {
    // Plan B semantics.

    // Update state and SSRC of local MediaStreams and DataChannels based on the
    // local session description.
    const cricket::ContentInfo* audio_content =
        GetFirstAudioContent(local_description()->description());
    if (audio_content) {
      if (audio_content->rejected) {
        RemoveSenders(cricket::MEDIA_TYPE_AUDIO);
      } else {
        const cricket::MediaContentDescription* audio_desc =
            audio_content->media_description();
        UpdateLocalSenders(audio_desc->streams(), audio_desc->type());
      }
    }

    const cricket::ContentInfo* video_content =
        GetFirstVideoContent(local_description()->description());
    if (video_content) {
      if (video_content->rejected) {
        RemoveSenders(cricket::MEDIA_TYPE_VIDEO);
      } else {
        const cricket::MediaContentDescription* video_desc =
            video_content->media_description();
        UpdateLocalSenders(video_desc->streams(), video_desc->type());
      }
    }
  }

  // This function does nothing with data content.

  if (type == SdpType::kAnswer &&
      local_ice_credentials_to_replace_->SatisfiesIceRestart(
          *current_local_description_)) {
    local_ice_credentials_to_replace_->ClearIceCredentials();
  }

  if (IsUnifiedPlan()) {
    UpdateRtpHeaderExtensionPreferencesFromSdpMunging(
        local_description()->description(), transceivers());
  }

  return RTCError::OK();
}

void SdpOfferAnswerHandler::SetRemoteDescription(
    SetSessionDescriptionObserver* observer,
    SessionDescriptionInterface* desc_ptr) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // Chain this operation. If asynchronous operations are pending on the chain,
  // this operation will be queued to be invoked, otherwise the contents of the
  // lambda will execute immediately.
  operations_chain_->ChainOperation(
      [this_weak_ptr = weak_ptr_factory_.GetWeakPtr(),
       observer_refptr =
           rtc::scoped_refptr<SetSessionDescriptionObserver>(observer),
       desc = std::unique_ptr<SessionDescriptionInterface>(desc_ptr)](
          std::function<void()> operations_chain_callback) mutable {
        // Abort early if `this_weak_ptr` is no longer valid.
        if (!this_weak_ptr) {
          // For consistency with SetSessionDescriptionObserverAdapter whose
          // posted messages doesn't get processed when the PC is destroyed, we
          // do not inform `observer_refptr` that the operation failed.
          operations_chain_callback();
          return;
        }
        // SetSessionDescriptionObserverAdapter takes care of making sure the
        // `observer_refptr` is invoked in a posted message.
        this_weak_ptr->DoSetRemoteDescription(
            std::make_unique<RemoteDescriptionOperation>(
                this_weak_ptr.get(), std::move(desc),
                rtc::make_ref_counted<SetSessionDescriptionObserverAdapter>(
                    this_weak_ptr, observer_refptr),
                std::move(operations_chain_callback)));
      });
}

void SdpOfferAnswerHandler::SetRemoteDescription(
    std::unique_ptr<SessionDescriptionInterface> desc,
    rtc::scoped_refptr<SetRemoteDescriptionObserverInterface> observer) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // Chain this operation. If asynchronous operations are pending on the chain,
  // this operation will be queued to be invoked, otherwise the contents of the
  // lambda will execute immediately.
  operations_chain_->ChainOperation(
      [this_weak_ptr = weak_ptr_factory_.GetWeakPtr(), observer,
       desc = std::move(desc)](
          std::function<void()> operations_chain_callback) mutable {
        if (!observer) {
          RTC_DLOG(LS_ERROR) << "SetRemoteDescription - observer is NULL.";
          operations_chain_callback();
          return;
        }

        // Abort early if `this_weak_ptr` is no longer valid.
        if (!this_weak_ptr) {
          observer->OnSetRemoteDescriptionComplete(RTCError(
              RTCErrorType::INTERNAL_ERROR,
              "SetRemoteDescription failed because the session was shut down"));
          operations_chain_callback();
          return;
        }

        this_weak_ptr->DoSetRemoteDescription(
            std::make_unique<RemoteDescriptionOperation>(
                this_weak_ptr.get(), std::move(desc), std::move(observer),
                std::move(operations_chain_callback)));
      });
}

RTCError SdpOfferAnswerHandler::ReplaceRemoteDescription(
    std::unique_ptr<SessionDescriptionInterface> desc,
    SdpType sdp_type,
    std::unique_ptr<SessionDescriptionInterface>* replaced_description) {
  RTC_DCHECK(replaced_description);
  if (sdp_type == SdpType::kAnswer) {
    *replaced_description = pending_remote_description_
                                ? std::move(pending_remote_description_)
                                : std::move(current_remote_description_);
    current_remote_description_ = std::move(desc);
    pending_remote_description_ = nullptr;
    current_local_description_ = std::move(pending_local_description_);
  } else {
    *replaced_description = std::move(pending_remote_description_);
    pending_remote_description_ = std::move(desc);
  }

  // The session description to apply now must be accessed by
  // `remote_description()`.
  const cricket::SessionDescription* session_desc =
      remote_description()->description();

  const auto* local = local_description();

  // NOTE: This will perform a BlockingCall() to the network thread.
  return transport_controller_s()->SetRemoteDescription(
      sdp_type, local ? local->description() : nullptr, session_desc);
}

void SdpOfferAnswerHandler::ApplyRemoteDescription(
    std::unique_ptr<RemoteDescriptionOperation> operation) {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::ApplyRemoteDescription");
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(operation->description());

  // Invalidate the stats caches to make sure that they get
  // updated next time getStats() gets called, as updating the session
  // description affects the stats.
  pc_->ClearStatsCache();

  if (!operation->ReplaceRemoteDescriptionAndCheckError())
    return;

  if (!operation->UpdateChannels())
    return;

  // NOTE: Candidates allocation will be initiated only when
  // SetLocalDescription is called.
  if (!operation->UpdateSessionState())
    return;

  if (!operation->UseCandidatesInRemoteDescription())
    return;

  if (operation->old_remote_description()) {
    for (const cricket::ContentInfo& content :
         operation->old_remote_description()->description()->contents()) {
      // Check if this new SessionDescription contains new ICE ufrag and
      // password that indicates the remote peer requests an ICE restart.
      // TODO(deadbeef): When we start storing both the current and pending
      // remote description, this should reset pending_ice_restarts and compare
      // against the current description.
      if (CheckForRemoteIceRestart(operation->old_remote_description(),
                                   remote_description(), content.name)) {
        if (operation->type() == SdpType::kOffer) {
          pending_ice_restarts_.insert(content.name);
        }
      } else {
        // We retain all received candidates only if ICE is not restarted.
        // When ICE is restarted, all previous candidates belong to an old
        // generation and should not be kept.
        // TODO(deadbeef): This goes against the W3C spec which says the remote
        // description should only contain candidates from the last set remote
        // description plus any candidates added since then. We should remove
        // this once we're sure it won't break anything.
        WebRtcSessionDescriptionFactory::CopyCandidatesFromSessionDescription(
            operation->old_remote_description(), content.name,
            mutable_remote_description());
      }
    }
  }

  if (operation->HaveSessionError())
    return;

  // Set the the ICE connection state to connecting since the connection may
  // become writable with peer reflexive candidates before any remote candidate
  // is signaled.
  // TODO(pthatcher): This is a short-term solution for crbug/446908. A real fix
  // is to have a new signal the indicates a change in checking state from the
  // transport and expose a new checking() member from transport that can be
  // read to determine the current checking state. The existing SignalConnecting
  // actually means "gathering candidates", so cannot be be used here.
  if (remote_description()->GetType() != SdpType::kOffer &&
      remote_description()->number_of_mediasections() > 0u &&
      pc_->ice_connection_state_internal() ==
          PeerConnectionInterface::kIceConnectionNew) {
    pc_->SetIceConnectionState(PeerConnectionInterface::kIceConnectionChecking);
  }

  // If setting the description decided our SSL role, allocate any necessary
  // SCTP sids.
  AllocateSctpSids();

  if (operation->unified_plan()) {
    ApplyRemoteDescriptionUpdateTransceiverState(operation->type());
  }
  remote_peer_supports_msid_ =
      remote_description()->description()->msid_signaling() !=
      cricket::kMsidSignalingNotUsed;

  if (!operation->unified_plan()) {
    PlanBUpdateSendersAndReceivers(
        GetFirstAudioContent(remote_description()->description()),
        GetFirstAudioContentDescription(remote_description()->description()),
        GetFirstVideoContent(remote_description()->description()),
        GetFirstVideoContentDescription(remote_description()->description()));
  }

  if (operation->type() == SdpType::kAnswer) {
    if (local_ice_credentials_to_replace_->SatisfiesIceRestart(
            *current_local_description_)) {
      local_ice_credentials_to_replace_->ClearIceCredentials();
    }

    RemoveStoppedTransceivers();
  }

  // Consider the operation complete at this point.
  operation->SignalCompletion();

  SetRemoteDescriptionPostProcess(operation->type() == SdpType::kAnswer);
}

void SdpOfferAnswerHandler::ApplyRemoteDescriptionUpdateTransceiverState(
    SdpType sdp_type) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(IsUnifiedPlan());
  if (!ConfiguredForMedia()) {
    return;
  }
  std::vector<rtc::scoped_refptr<RtpTransceiverInterface>>
      now_receiving_transceivers;
  std::vector<rtc::scoped_refptr<RtpTransceiverInterface>> remove_list;
  std::vector<rtc::scoped_refptr<MediaStreamInterface>> added_streams;
  std::vector<rtc::scoped_refptr<MediaStreamInterface>> removed_streams;
  for (const auto& transceiver_ext : transceivers()->List()) {
    const auto transceiver = transceiver_ext->internal();
    const ContentInfo* content =
        FindMediaSectionForTransceiver(transceiver, remote_description());
    if (!content) {
      continue;
    }
    const MediaContentDescription* media_desc = content->media_description();
    RtpTransceiverDirection local_direction =
        RtpTransceiverDirectionReversed(media_desc->direction());
    // Remember the previous remote streams if this is a remote offer. This
    // makes it possible to rollback modifications to the streams.
    if (sdp_type == SdpType::kOffer) {
      transceivers()
          ->StableState(transceiver_ext)
          ->SetRemoteStreamIds(transceiver->receiver()->stream_ids());
    }
    // Roughly the same as steps 2.2.8.6 of section 4.4.1.6 "Set the
    // RTCSessionDescription: Set the associated remote streams given
    // transceiver.[[Receiver]], msids, addList, and removeList".
    // https://w3c.github.io/webrtc-pc/#set-the-rtcsessiondescription
    if (RtpTransceiverDirectionHasRecv(local_direction)) {
      std::vector<std::string> stream_ids;
      if (!media_desc->streams().empty()) {
        // The remote description has signaled the stream IDs.
        stream_ids = media_desc->streams()[0].stream_ids();
      }

      RTC_LOG(LS_INFO) << "Processing the MSIDs for MID=" << content->name
                       << " (" << GetStreamIdsString(stream_ids) << ").";
      SetAssociatedRemoteStreams(transceiver->receiver_internal(), stream_ids,
                                 &added_streams, &removed_streams);
      // From the WebRTC specification, steps 2.2.8.5/6 of section 4.4.1.6
      // "Set the RTCSessionDescription: If direction is sendrecv or recvonly,
      // and transceiver's current direction is neither sendrecv nor recvonly,
      // process the addition of a remote track for the media description.
      if (!transceiver->fired_direction() ||
          !RtpTransceiverDirectionHasRecv(*transceiver->fired_direction())) {
        RTC_LOG(LS_INFO) << "Processing the addition of a remote track for MID="
                         << content->name << ".";
        // Since the transceiver is passed to the user in an
        // OnTrack event, we must use the proxied transceiver.
        now_receiving_transceivers.push_back(transceiver_ext);
      }
    }
    // 2.2.8.1.9: If direction is "sendonly" or "inactive", and transceiver's
    // [[FiredDirection]] slot is either "sendrecv" or "recvonly", process the
    // removal of a remote track for the media description, given transceiver,
    // removeList, and muteTracks.
    if (!RtpTransceiverDirectionHasRecv(local_direction) &&
        (transceiver->fired_direction() &&
         RtpTransceiverDirectionHasRecv(*transceiver->fired_direction()))) {
      ProcessRemovalOfRemoteTrack(transceiver_ext, &remove_list,
                                  &removed_streams);
    }
    // 2.2.8.1.10: Set transceiver's [[FiredDirection]] slot to direction.
    if (sdp_type == SdpType::kOffer) {
      // Remember the previous fired direction if this is a remote offer. This
      // makes it possible to rollback modifications to [[FiredDirection]],
      // which is necessary for "ontrack" to fire in or after rollback.
      transceivers()
          ->StableState(transceiver_ext)
          ->SetFiredDirection(transceiver->fired_direction());
    }
    transceiver->set_fired_direction(local_direction);
    // 2.2.8.1.11: If description is of type "answer" or "pranswer", then run
    // the following steps:
    if (sdp_type == SdpType::kPrAnswer || sdp_type == SdpType::kAnswer) {
      // 2.2.8.1.11.1: Set transceiver's [[CurrentDirection]] slot to
      // direction.
      transceiver->set_current_direction(local_direction);
      // 2.2.8.1.11.[3-6]: Set the transport internal slots.
      if (transceiver->mid()) {
        auto dtls_transport = LookupDtlsTransportByMid(
            context_->network_thread(), transport_controller_s(),
            *transceiver->mid());
        transceiver->sender_internal()->set_transport(dtls_transport);
        transceiver->receiver_internal()->set_transport(dtls_transport);
      }
    }
    // 2.2.8.1.12: If the media description is rejected, and transceiver is
    // not already stopped, stop the RTCRtpTransceiver transceiver.
    if (content->rejected && !transceiver->stopped()) {
      RTC_LOG(LS_INFO) << "Stopping transceiver for MID=" << content->name
                       << " since the media section was rejected.";
      transceiver->StopTransceiverProcedure();
    }
    if (!content->rejected && RtpTransceiverDirectionHasRecv(local_direction)) {
      if (!media_desc->streams().empty() &&
          media_desc->streams()[0].has_ssrcs()) {
        uint32_t ssrc = media_desc->streams()[0].first_ssrc();
        transceiver->receiver_internal()->SetupMediaChannel(ssrc);
      } else {
        transceiver->receiver_internal()->SetupUnsignaledMediaChannel();
      }
    }
  }
  // Once all processing has finished, fire off callbacks.
  auto observer = pc_->Observer();
  for (const auto& transceiver : now_receiving_transceivers) {
    pc_->legacy_stats()->AddTrack(transceiver->receiver()->track().get());
    observer->OnTrack(transceiver);
    observer->OnAddTrack(transceiver->receiver(),
                         transceiver->receiver()->streams());
  }
  for (const auto& stream : added_streams) {
    observer->OnAddStream(stream);
  }
  for (const auto& transceiver : remove_list) {
    observer->OnRemoveTrack(transceiver->receiver());
  }
  for (const auto& stream : removed_streams) {
    observer->OnRemoveStream(stream);
  }
}

void SdpOfferAnswerHandler::PlanBUpdateSendersAndReceivers(
    const cricket::ContentInfo* audio_content,
    const cricket::AudioContentDescription* audio_desc,
    const cricket::ContentInfo* video_content,
    const cricket::VideoContentDescription* video_desc) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(!IsUnifiedPlan());

  // We wait to signal new streams until we finish processing the description,
  // since only at that point will new streams have all their tracks.
  rtc::scoped_refptr<StreamCollection> new_streams(StreamCollection::Create());

  // TODO(steveanton): When removing RTP senders/receivers in response to a
  // rejected media section, there is some cleanup logic that expects the
  // voice/ video channel to still be set. But in this method the voice/video
  // channel would have been destroyed by the SetRemoteDescription caller
  // above so the cleanup that relies on them fails to run. The RemoveSenders
  // calls should be moved to right before the DestroyChannel calls to fix
  // this.

  // Find all audio rtp streams and create corresponding remote AudioTracks
  // and MediaStreams.
  if (audio_content) {
    if (audio_content->rejected) {
      RemoveSenders(cricket::MEDIA_TYPE_AUDIO);
    } else {
      bool default_audio_track_needed =
          !remote_peer_supports_msid_ &&
          RtpTransceiverDirectionHasSend(audio_desc->direction());
      UpdateRemoteSendersList(GetActiveStreams(audio_desc),
                              default_audio_track_needed, audio_desc->type(),
                              new_streams.get());
    }
  }

  // Find all video rtp streams and create corresponding remote VideoTracks
  // and MediaStreams.
  if (video_content) {
    if (video_content->rejected) {
      RemoveSenders(cricket::MEDIA_TYPE_VIDEO);
    } else {
      bool default_video_track_needed =
          !remote_peer_supports_msid_ &&
          RtpTransceiverDirectionHasSend(video_desc->direction());
      UpdateRemoteSendersList(GetActiveStreams(video_desc),
                              default_video_track_needed, video_desc->type(),
                              new_streams.get());
    }
  }

  // Iterate new_streams and notify the observer about new MediaStreams.
  auto observer = pc_->Observer();
  for (size_t i = 0; i < new_streams->count(); ++i) {
    MediaStreamInterface* new_stream = new_streams->at(i);
    pc_->legacy_stats()->AddStream(new_stream);
    observer->OnAddStream(rtc::scoped_refptr<MediaStreamInterface>(new_stream));
  }

  UpdateEndedRemoteMediaStreams();
}

void SdpOfferAnswerHandler::DoSetLocalDescription(
    std::unique_ptr<SessionDescriptionInterface> desc,
    rtc::scoped_refptr<SetLocalDescriptionObserverInterface> observer) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::DoSetLocalDescription");

  if (!observer) {
    RTC_LOG(LS_ERROR) << "SetLocalDescription - observer is NULL.";
    return;
  }

  if (!desc) {
    observer->OnSetLocalDescriptionComplete(
        RTCError(RTCErrorType::INTERNAL_ERROR, "SessionDescription is NULL."));
    return;
  }

  // If a session error has occurred the PeerConnection is in a possibly
  // inconsistent state so fail right away.
  if (session_error() != SessionError::kNone) {
    std::string error_message = GetSessionErrorMsg();
    RTC_LOG(LS_ERROR) << "SetLocalDescription: " << error_message;
    observer->OnSetLocalDescriptionComplete(
        RTCError(RTCErrorType::INTERNAL_ERROR, std::move(error_message)));
    return;
  }

  // For SLD we support only explicit rollback.
  if (desc->GetType() == SdpType::kRollback) {
    if (IsUnifiedPlan()) {
      observer->OnSetLocalDescriptionComplete(Rollback(desc->GetType()));
    } else {
      observer->OnSetLocalDescriptionComplete(
          RTCError(RTCErrorType::UNSUPPORTED_OPERATION,
                   "Rollback not supported in Plan B"));
    }
    return;
  }

  std::map<std::string, const cricket::ContentGroup*> bundle_groups_by_mid =
      GetBundleGroupsByMid(desc->description());
  RTCError error = ValidateSessionDescription(desc.get(), cricket::CS_LOCAL,
                                              bundle_groups_by_mid);
  if (!error.ok()) {
    std::string error_message = GetSetDescriptionErrorMessage(
        cricket::CS_LOCAL, desc->GetType(), error);
    RTC_LOG(LS_ERROR) << error_message;
    observer->OnSetLocalDescriptionComplete(
        RTCError(error.type(), std::move(error_message)));
    return;
  }

  // Grab the description type before moving ownership to ApplyLocalDescription,
  // which may destroy it before returning.
  const SdpType type = desc->GetType();

  error = ApplyLocalDescription(std::move(desc), bundle_groups_by_mid);
  // `desc` may be destroyed at this point.

  if (!error.ok()) {
    // If ApplyLocalDescription fails, the PeerConnection could be in an
    // inconsistent state, so act conservatively here and set the session error
    // so that future calls to SetLocalDescription/SetRemoteDescription fail.
    SetSessionError(SessionError::kContent, error.message());
    std::string error_message =
        GetSetDescriptionErrorMessage(cricket::CS_LOCAL, type, error);
    RTC_LOG(LS_ERROR) << error_message;
    observer->OnSetLocalDescriptionComplete(
        RTCError(RTCErrorType::INTERNAL_ERROR, std::move(error_message)));
    return;
  }
  RTC_DCHECK(local_description());

  if (local_description()->GetType() == SdpType::kAnswer) {
    RemoveStoppedTransceivers();

    // TODO(deadbeef): We already had to hop to the network thread for
    // MaybeStartGathering...
    context_->network_thread()->BlockingCall(
        [this] { port_allocator()->DiscardCandidatePool(); });
  }

  observer->OnSetLocalDescriptionComplete(RTCError::OK());
  pc_->NoteUsageEvent(UsageEvent::SET_LOCAL_DESCRIPTION_SUCCEEDED);

  // Check if negotiation is needed. We must do this after informing the
  // observer that SetLocalDescription() has completed to ensure negotiation is
  // not needed prior to the promise resolving.
  if (IsUnifiedPlan()) {
    bool was_negotiation_needed = is_negotiation_needed_;
    UpdateNegotiationNeeded();
    if (signaling_state() == PeerConnectionInterface::kStable &&
        was_negotiation_needed && is_negotiation_needed_) {
      // Legacy version.
      pc_->Observer()->OnRenegotiationNeeded();
      // Spec-compliant version; the event may get invalidated before firing.
      GenerateNegotiationNeededEvent();
    }
  }

  // MaybeStartGathering needs to be called after informing the observer so that
  // we don't signal any candidates before signaling that SetLocalDescription
  // completed.
  transport_controller_s()->MaybeStartGathering();
}

void SdpOfferAnswerHandler::DoCreateOffer(
    const PeerConnectionInterface::RTCOfferAnswerOptions& options,
    rtc::scoped_refptr<CreateSessionDescriptionObserver> observer) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::DoCreateOffer");

  if (!observer) {
    RTC_LOG(LS_ERROR) << "CreateOffer - observer is NULL.";
    return;
  }

  if (pc_->IsClosed()) {
    std::string error = "CreateOffer called when PeerConnection is closed.";
    RTC_LOG(LS_ERROR) << error;
    pc_->message_handler()->PostCreateSessionDescriptionFailure(
        observer.get(),
        RTCError(RTCErrorType::INVALID_STATE, std::move(error)));
    return;
  }

  // If a session error has occurred the PeerConnection is in a possibly
  // inconsistent state so fail right away.
  if (session_error() != SessionError::kNone) {
    std::string error_message = GetSessionErrorMsg();
    RTC_LOG(LS_ERROR) << "CreateOffer: " << error_message;
    pc_->message_handler()->PostCreateSessionDescriptionFailure(
        observer.get(),
        RTCError(RTCErrorType::INTERNAL_ERROR, std::move(error_message)));
    return;
  }

  if (!ValidateOfferAnswerOptions(options)) {
    std::string error = "CreateOffer called with invalid options.";
    RTC_LOG(LS_ERROR) << error;
    pc_->message_handler()->PostCreateSessionDescriptionFailure(
        observer.get(),
        RTCError(RTCErrorType::INVALID_PARAMETER, std::move(error)));
    return;
  }

  // Legacy handling for offer_to_receive_audio and offer_to_receive_video.
  // Specified in WebRTC section 4.4.3.2 "Legacy configuration extensions".
  if (IsUnifiedPlan()) {
    RTCError error = HandleLegacyOfferOptions(options);
    if (!error.ok()) {
      pc_->message_handler()->PostCreateSessionDescriptionFailure(
          observer.get(), std::move(error));
      return;
    }
  }

  cricket::MediaSessionOptions session_options;
  GetOptionsForOffer(options, &session_options);
  webrtc_session_desc_factory_->CreateOffer(observer.get(), options,
                                            session_options);
}

void SdpOfferAnswerHandler::CreateAnswer(
    CreateSessionDescriptionObserver* observer,
    const PeerConnectionInterface::RTCOfferAnswerOptions& options) {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::CreateAnswer");
  RTC_DCHECK_RUN_ON(signaling_thread());
  // Chain this operation. If asynchronous operations are pending on the chain,
  // this operation will be queued to be invoked, otherwise the contents of the
  // lambda will execute immediately.
  operations_chain_->ChainOperation(
      [this_weak_ptr = weak_ptr_factory_.GetWeakPtr(),
       observer_refptr =
           rtc::scoped_refptr<CreateSessionDescriptionObserver>(observer),
       options](std::function<void()> operations_chain_callback) {
        // Abort early if `this_weak_ptr` is no longer valid.
        if (!this_weak_ptr) {
          observer_refptr->OnFailure(RTCError(
              RTCErrorType::INTERNAL_ERROR,
              "CreateAnswer failed because the session was shut down"));
          operations_chain_callback();
          return;
        }
        // The operation completes asynchronously when the wrapper is invoked.
        auto observer_wrapper = rtc::make_ref_counted<
            CreateSessionDescriptionObserverOperationWrapper>(
            std::move(observer_refptr), std::move(operations_chain_callback));
        this_weak_ptr->DoCreateAnswer(options, observer_wrapper);
      });
}

void SdpOfferAnswerHandler::DoCreateAnswer(
    const PeerConnectionInterface::RTCOfferAnswerOptions& options,
    rtc::scoped_refptr<CreateSessionDescriptionObserver> observer) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::DoCreateAnswer");
  if (!observer) {
    RTC_LOG(LS_ERROR) << "CreateAnswer - observer is NULL.";
    return;
  }

  // If a session error has occurred the PeerConnection is in a possibly
  // inconsistent state so fail right away.
  if (session_error() != SessionError::kNone) {
    std::string error_message = GetSessionErrorMsg();
    RTC_LOG(LS_ERROR) << "CreateAnswer: " << error_message;
    pc_->message_handler()->PostCreateSessionDescriptionFailure(
        observer.get(),
        RTCError(RTCErrorType::INTERNAL_ERROR, std::move(error_message)));
    return;
  }

  if (!(signaling_state_ == PeerConnectionInterface::kHaveRemoteOffer ||
        signaling_state_ == PeerConnectionInterface::kHaveLocalPrAnswer)) {
    std::string error =
        "PeerConnection cannot create an answer in a state other than "
        "have-remote-offer or have-local-pranswer.";
    RTC_LOG(LS_ERROR) << error;
    pc_->message_handler()->PostCreateSessionDescriptionFailure(
        observer.get(),
        RTCError(RTCErrorType::INVALID_STATE, std::move(error)));
    return;
  }

  // The remote description should be set if we're in the right state.
  RTC_DCHECK(remote_description());

  if (IsUnifiedPlan()) {
    if (options.offer_to_receive_audio !=
        PeerConnectionInterface::RTCOfferAnswerOptions::kUndefined) {
      RTC_LOG(LS_WARNING) << "CreateAnswer: offer_to_receive_audio is not "
                             "supported with Unified Plan semantics. Use the "
                             "RtpTransceiver API instead.";
    }
    if (options.offer_to_receive_video !=
        PeerConnectionInterface::RTCOfferAnswerOptions::kUndefined) {
      RTC_LOG(LS_WARNING) << "CreateAnswer: offer_to_receive_video is not "
                             "supported with Unified Plan semantics. Use the "
                             "RtpTransceiver API instead.";
    }
  }

  cricket::MediaSessionOptions session_options;
  GetOptionsForAnswer(options, &session_options);
  webrtc_session_desc_factory_->CreateAnswer(observer.get(), session_options);
}

void SdpOfferAnswerHandler::DoSetRemoteDescription(
    std::unique_ptr<RemoteDescriptionOperation> operation) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::DoSetRemoteDescription");

  if (!operation->ok())
    return;

  if (operation->HaveSessionError())
    return;

  if (operation->MaybeRollback())
    return;

  operation->ReportOfferAnswerUma();

  // Handle remote descriptions missing a=mid lines for interop with legacy
  // end points.
  FillInMissingRemoteMids(operation->description());
  if (!operation->IsDescriptionValid())
    return;

  ApplyRemoteDescription(std::move(operation));
}

// Called after a DoSetRemoteDescription operation completes.
void SdpOfferAnswerHandler::SetRemoteDescriptionPostProcess(bool was_answer) {
  RTC_DCHECK(remote_description());

  if (was_answer) {
    // TODO(deadbeef): We already had to hop to the network thread for
    // MaybeStartGathering...
    context_->network_thread()->BlockingCall(
        [this] { port_allocator()->DiscardCandidatePool(); });
  }

  pc_->NoteUsageEvent(UsageEvent::SET_REMOTE_DESCRIPTION_SUCCEEDED);

  // Check if negotiation is needed. We must do this after informing the
  // observer that SetRemoteDescription() has completed to ensure negotiation
  // is not needed prior to the promise resolving.
  if (IsUnifiedPlan()) {
    bool was_negotiation_needed = is_negotiation_needed_;
    UpdateNegotiationNeeded();
    if (signaling_state() == PeerConnectionInterface::kStable &&
        was_negotiation_needed && is_negotiation_needed_) {
      // Legacy version.
      pc_->Observer()->OnRenegotiationNeeded();
      // Spec-compliant version; the event may get invalidated before firing.
      GenerateNegotiationNeededEvent();
    }
  }
}

void SdpOfferAnswerHandler::SetAssociatedRemoteStreams(
    rtc::scoped_refptr<RtpReceiverInternal> receiver,
    const std::vector<std::string>& stream_ids,
    std::vector<rtc::scoped_refptr<MediaStreamInterface>>* added_streams,
    std::vector<rtc::scoped_refptr<MediaStreamInterface>>* removed_streams) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  std::vector<rtc::scoped_refptr<MediaStreamInterface>> media_streams;
  for (const std::string& stream_id : stream_ids) {
    rtc::scoped_refptr<MediaStreamInterface> stream(
        remote_streams_->find(stream_id));
    if (!stream) {
      stream = MediaStreamProxy::Create(rtc::Thread::Current(),
                                        MediaStream::Create(stream_id));
      remote_streams_->AddStream(stream);
      added_streams->push_back(stream);
    }
    media_streams.push_back(stream);
  }
  // Special case: "a=msid" missing, use random stream ID.
  if (media_streams.empty() &&
      !(remote_description()->description()->msid_signaling() &
        cricket::kMsidSignalingMediaSection)) {
    if (!missing_msid_default_stream_) {
      missing_msid_default_stream_ = MediaStreamProxy::Create(
          rtc::Thread::Current(), MediaStream::Create(rtc::CreateRandomUuid()));
      added_streams->push_back(missing_msid_default_stream_);
    }
    media_streams.push_back(missing_msid_default_stream_);
  }
  std::vector<rtc::scoped_refptr<MediaStreamInterface>> previous_streams =
      receiver->streams();
  // SetStreams() will add/remove the receiver's track to/from the streams.
  // This differs from the spec - the spec uses an "addList" and "removeList"
  // to update the stream-track relationships in a later step. We do this
  // earlier, changing the order of things, but the end-result is the same.
  // TODO(hbos): When we remove remote_streams(), use set_stream_ids()
  // instead. https://crbug.com/webrtc/9480
  receiver->SetStreams(media_streams);
  RemoveRemoteStreamsIfEmpty(previous_streams, removed_streams);
}

bool SdpOfferAnswerHandler::AddIceCandidate(
    const IceCandidateInterface* ice_candidate) {
  const AddIceCandidateResult result = AddIceCandidateInternal(ice_candidate);
  NoteAddIceCandidateResult(result);
  // If the return value is kAddIceCandidateFailNotReady, the candidate has
  // been added, although not 'ready', but that's a success.
  return result == kAddIceCandidateSuccess ||
         result == kAddIceCandidateFailNotReady;
}

AddIceCandidateResult SdpOfferAnswerHandler::AddIceCandidateInternal(
    const IceCandidateInterface* ice_candidate) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::AddIceCandidate");
  if (pc_->IsClosed()) {
    RTC_LOG(LS_ERROR) << "AddIceCandidate: PeerConnection is closed.";
    return kAddIceCandidateFailClosed;
  }

  if (!remote_description()) {
    RTC_LOG(LS_ERROR) << "AddIceCandidate: ICE candidates can't be added "
                         "without any remote session description.";
    return kAddIceCandidateFailNoRemoteDescription;
  }

  if (!ice_candidate) {
    RTC_LOG(LS_ERROR) << "AddIceCandidate: Candidate is null.";
    return kAddIceCandidateFailNullCandidate;
  }

  bool valid = false;
  bool ready = ReadyToUseRemoteCandidate(ice_candidate, nullptr, &valid);
  if (!valid) {
    return kAddIceCandidateFailNotValid;
  }

  // Add this candidate to the remote session description.
  if (!mutable_remote_description()->AddCandidate(ice_candidate)) {
    RTC_LOG(LS_ERROR) << "AddIceCandidate: Candidate cannot be used.";
    return kAddIceCandidateFailInAddition;
  }

  if (!ready) {
    RTC_LOG(LS_INFO) << "AddIceCandidate: Not ready to use candidate.";
    return kAddIceCandidateFailNotReady;
  }

  if (!UseCandidate(ice_candidate)) {
    return kAddIceCandidateFailNotUsable;
  }

  pc_->NoteUsageEvent(UsageEvent::ADD_ICE_CANDIDATE_SUCCEEDED);

  return kAddIceCandidateSuccess;
}

void SdpOfferAnswerHandler::AddIceCandidate(
    std::unique_ptr<IceCandidateInterface> candidate,
    std::function<void(RTCError)> callback) {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::AddIceCandidate");
  RTC_DCHECK_RUN_ON(signaling_thread());
  // Chain this operation. If asynchronous operations are pending on the
  // chain, this operation will be queued to be invoked, otherwise the
  // contents of the lambda will execute immediately.
  operations_chain_->ChainOperation(
      [this_weak_ptr = weak_ptr_factory_.GetWeakPtr(),
       candidate = std::move(candidate), callback = std::move(callback)](
          std::function<void()> operations_chain_callback) {
        auto result =
            this_weak_ptr
                ? this_weak_ptr->AddIceCandidateInternal(candidate.get())
                : kAddIceCandidateFailClosed;
        NoteAddIceCandidateResult(result);
        operations_chain_callback();
        switch (result) {
          case AddIceCandidateResult::kAddIceCandidateSuccess:
          case AddIceCandidateResult::kAddIceCandidateFailNotReady:
            // Success!
            callback(RTCError::OK());
            break;
          case AddIceCandidateResult::kAddIceCandidateFailClosed:
            // Note that the spec says to just abort without resolving the
            // promise in this case, but this layer must return an RTCError.
            callback(RTCError(
                RTCErrorType::INVALID_STATE,
                "AddIceCandidate failed because the session was shut down"));
            break;
          case AddIceCandidateResult::kAddIceCandidateFailNoRemoteDescription:
            // Spec: "If remoteDescription is null return a promise rejected
            // with a newly created InvalidStateError."
            callback(RTCError(RTCErrorType::INVALID_STATE,
                              "The remote description was null"));
            break;
          case AddIceCandidateResult::kAddIceCandidateFailNullCandidate:
            // TODO(https://crbug.com/935898): Handle end-of-candidates instead
            // of treating null candidate as an error.
            callback(RTCError(RTCErrorType::UNSUPPORTED_OPERATION,
                              "Error processing ICE candidate"));
            break;
          case AddIceCandidateResult::kAddIceCandidateFailNotValid:
          case AddIceCandidateResult::kAddIceCandidateFailInAddition:
          case AddIceCandidateResult::kAddIceCandidateFailNotUsable:
            // Spec: "If candidate could not be successfully added [...] Reject
            // p with a newly created OperationError and abort these steps."
            // UNSUPPORTED_OPERATION maps to OperationError.
            callback(RTCError(RTCErrorType::UNSUPPORTED_OPERATION,
                              "Error processing ICE candidate"));
            break;
          default:
            RTC_DCHECK_NOTREACHED();
        }
      });
}

bool SdpOfferAnswerHandler::RemoveIceCandidates(
    const std::vector<cricket::Candidate>& candidates) {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::RemoveIceCandidates");
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (pc_->IsClosed()) {
    RTC_LOG(LS_ERROR) << "RemoveIceCandidates: PeerConnection is closed.";
    return false;
  }

  if (!remote_description()) {
    RTC_LOG(LS_ERROR) << "RemoveIceCandidates: ICE candidates can't be removed "
                         "without any remote session description.";
    return false;
  }

  if (candidates.empty()) {
    RTC_LOG(LS_ERROR) << "RemoveIceCandidates: candidates are empty.";
    return false;
  }

  size_t number_removed =
      mutable_remote_description()->RemoveCandidates(candidates);
  if (number_removed != candidates.size()) {
    RTC_LOG(LS_ERROR)
        << "RemoveIceCandidates: Failed to remove candidates. Requested "
        << candidates.size() << " but only " << number_removed
        << " are removed.";
  }

  // Remove the candidates from the transport controller.
  RTCError error = transport_controller_s()->RemoveRemoteCandidates(candidates);
  if (!error.ok()) {
    RTC_LOG(LS_ERROR)
        << "RemoveIceCandidates: Error when removing remote candidates: "
        << error.message();
  }
  return true;
}

void SdpOfferAnswerHandler::AddLocalIceCandidate(
    const JsepIceCandidate* candidate) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (local_description()) {
    mutable_local_description()->AddCandidate(candidate);
  }
}

void SdpOfferAnswerHandler::RemoveLocalIceCandidates(
    const std::vector<cricket::Candidate>& candidates) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (local_description()) {
    mutable_local_description()->RemoveCandidates(candidates);
  }
}

const SessionDescriptionInterface* SdpOfferAnswerHandler::local_description()
    const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return pending_local_description_ ? pending_local_description_.get()
                                    : current_local_description_.get();
}

const SessionDescriptionInterface* SdpOfferAnswerHandler::remote_description()
    const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return pending_remote_description_ ? pending_remote_description_.get()
                                     : current_remote_description_.get();
}

const SessionDescriptionInterface*
SdpOfferAnswerHandler::current_local_description() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return current_local_description_.get();
}

const SessionDescriptionInterface*
SdpOfferAnswerHandler::current_remote_description() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return current_remote_description_.get();
}

const SessionDescriptionInterface*
SdpOfferAnswerHandler::pending_local_description() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return pending_local_description_.get();
}

const SessionDescriptionInterface*
SdpOfferAnswerHandler::pending_remote_description() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return pending_remote_description_.get();
}

PeerConnectionInterface::SignalingState SdpOfferAnswerHandler::signaling_state()
    const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return signaling_state_;
}

void SdpOfferAnswerHandler::ChangeSignalingState(
    PeerConnectionInterface::SignalingState signaling_state) {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::ChangeSignalingState");
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (signaling_state_ == signaling_state) {
    return;
  }
  RTC_LOG(LS_INFO) << "Session: " << pc_->session_id() << " Old state: "
                   << PeerConnectionInterface::AsString(signaling_state_)
                   << " New state: "
                   << PeerConnectionInterface::AsString(signaling_state);
  signaling_state_ = signaling_state;
  pc_->Observer()->OnSignalingChange(signaling_state_);
}

RTCError SdpOfferAnswerHandler::UpdateSessionState(
    SdpType type,
    cricket::ContentSource source,
    const cricket::SessionDescription* description,
    const std::map<std::string, const cricket::ContentGroup*>&
        bundle_groups_by_mid) {
  RTC_DCHECK_RUN_ON(signaling_thread());

  // If there's already a pending error then no state transition should
  // happen. But all call-sites should be verifying this before calling us!
  RTC_DCHECK(session_error() == SessionError::kNone);

  // If this is answer-ish we're ready to let media flow.
  if (type == SdpType::kPrAnswer || type == SdpType::kAnswer) {
    EnableSending();
  }

  // Update the signaling state according to the specified state machine (see
  // https://w3c.github.io/webrtc-pc/#rtcsignalingstate-enum).
  if (type == SdpType::kOffer) {
    ChangeSignalingState(source == cricket::CS_LOCAL
                             ? PeerConnectionInterface::kHaveLocalOffer
                             : PeerConnectionInterface::kHaveRemoteOffer);
  } else if (type == SdpType::kPrAnswer) {
    ChangeSignalingState(source == cricket::CS_LOCAL
                             ? PeerConnectionInterface::kHaveLocalPrAnswer
                             : PeerConnectionInterface::kHaveRemotePrAnswer);
  } else {
    RTC_DCHECK(type == SdpType::kAnswer);
    ChangeSignalingState(PeerConnectionInterface::kStable);
    if (ConfiguredForMedia()) {
      transceivers()->DiscardStableStates();
    }
  }

  // Update internal objects according to the session description's media
  // descriptions.
  return PushdownMediaDescription(type, source, bundle_groups_by_mid);
}

bool SdpOfferAnswerHandler::ShouldFireNegotiationNeededEvent(
    uint32_t event_id) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // Plan B? Always fire to conform with useless legacy behavior.
  if (!IsUnifiedPlan()) {
    return true;
  }
  // The event ID has been invalidated. Either negotiation is no longer needed
  // or a newer negotiation needed event has been generated.
  if (event_id != negotiation_needed_event_id_) {
    return false;
  }
  // The chain is no longer empty, update negotiation needed when it becomes
  // empty. This should generate a newer negotiation needed event, making this
  // one obsolete.
  if (!operations_chain_->IsEmpty()) {
    // Since we just suppressed an event that would have been fired, if
    // negotiation is still needed by the time the chain becomes empty again,
    // we must make sure to generate another event if negotiation is needed
    // then. This happens when `is_negotiation_needed_` goes from false to
    // true, so we set it to false until UpdateNegotiationNeeded() is called.
    is_negotiation_needed_ = false;
    update_negotiation_needed_on_empty_chain_ = true;
    return false;
  }
  // We must not fire if the signaling state is no longer "stable". If
  // negotiation is still needed when we return to "stable", a new negotiation
  // needed event will be generated, so this one can safely be suppressed.
  if (signaling_state_ != PeerConnectionInterface::kStable) {
    return false;
  }
  // All checks have passed - please fire "negotiationneeded" now!
  return true;
}

rtc::scoped_refptr<StreamCollectionInterface>
SdpOfferAnswerHandler::local_streams() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_CHECK(!IsUnifiedPlan()) << "local_streams is not available with Unified "
                                 "Plan SdpSemantics. Please use GetSenders "
                                 "instead.";
  return local_streams_;
}

rtc::scoped_refptr<StreamCollectionInterface>
SdpOfferAnswerHandler::remote_streams() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_CHECK(!IsUnifiedPlan()) << "remote_streams is not available with Unified "
                                 "Plan SdpSemantics. Please use GetReceivers "
                                 "instead.";
  return remote_streams_;
}

bool SdpOfferAnswerHandler::AddStream(MediaStreamInterface* local_stream) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_CHECK(!IsUnifiedPlan()) << "AddStream is not available with Unified Plan "
                                 "SdpSemantics. Please use AddTrack instead.";
  if (pc_->IsClosed()) {
    return false;
  }
  if (!CanAddLocalMediaStream(local_streams_.get(), local_stream)) {
    return false;
  }

  local_streams_->AddStream(
      rtc::scoped_refptr<MediaStreamInterface>(local_stream));
  auto observer = std::make_unique<MediaStreamObserver>(
      local_stream,
      [this](AudioTrackInterface* audio_track,
             MediaStreamInterface* media_stream) {
        RTC_DCHECK_RUN_ON(signaling_thread());
        OnAudioTrackAdded(audio_track, media_stream);
      },
      [this](AudioTrackInterface* audio_track,
             MediaStreamInterface* media_stream) {
        RTC_DCHECK_RUN_ON(signaling_thread());
        OnAudioTrackRemoved(audio_track, media_stream);
      },
      [this](VideoTrackInterface* video_track,
             MediaStreamInterface* media_stream) {
        RTC_DCHECK_RUN_ON(signaling_thread());
        OnVideoTrackAdded(video_track, media_stream);
      },
      [this](VideoTrackInterface* video_track,
             MediaStreamInterface* media_stream) {
        RTC_DCHECK_RUN_ON(signaling_thread());
        OnVideoTrackRemoved(video_track, media_stream);
      });
  stream_observers_.push_back(std::move(observer));

  for (const auto& track : local_stream->GetAudioTracks()) {
    rtp_manager()->AddAudioTrack(track.get(), local_stream);
  }
  for (const auto& track : local_stream->GetVideoTracks()) {
    rtp_manager()->AddVideoTrack(track.get(), local_stream);
  }

  pc_->legacy_stats()->AddStream(local_stream);
  UpdateNegotiationNeeded();
  return true;
}

void SdpOfferAnswerHandler::RemoveStream(MediaStreamInterface* local_stream) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_CHECK(!IsUnifiedPlan()) << "RemoveStream is not available with Unified "
                                 "Plan SdpSemantics. Please use RemoveTrack "
                                 "instead.";
  TRACE_EVENT0("webrtc", "PeerConnection::RemoveStream");
  if (!pc_->IsClosed()) {
    for (const auto& track : local_stream->GetAudioTracks()) {
      rtp_manager()->RemoveAudioTrack(track.get(), local_stream);
    }
    for (const auto& track : local_stream->GetVideoTracks()) {
      rtp_manager()->RemoveVideoTrack(track.get(), local_stream);
    }
  }
  local_streams_->RemoveStream(local_stream);
  stream_observers_.erase(
      std::remove_if(
          stream_observers_.begin(), stream_observers_.end(),
          [local_stream](const std::unique_ptr<MediaStreamObserver>& observer) {
            return observer->stream()->id().compare(local_stream->id()) == 0;
          }),
      stream_observers_.end());

  if (pc_->IsClosed()) {
    return;
  }
  UpdateNegotiationNeeded();
}

void SdpOfferAnswerHandler::OnAudioTrackAdded(AudioTrackInterface* track,
                                              MediaStreamInterface* stream) {
  if (pc_->IsClosed()) {
    return;
  }
  rtp_manager()->AddAudioTrack(track, stream);
  UpdateNegotiationNeeded();
}

void SdpOfferAnswerHandler::OnAudioTrackRemoved(AudioTrackInterface* track,
                                                MediaStreamInterface* stream) {
  if (pc_->IsClosed()) {
    return;
  }
  rtp_manager()->RemoveAudioTrack(track, stream);
  UpdateNegotiationNeeded();
}

void SdpOfferAnswerHandler::OnVideoTrackAdded(VideoTrackInterface* track,
                                              MediaStreamInterface* stream) {
  if (pc_->IsClosed()) {
    return;
  }
  rtp_manager()->AddVideoTrack(track, stream);
  UpdateNegotiationNeeded();
}

void SdpOfferAnswerHandler::OnVideoTrackRemoved(VideoTrackInterface* track,
                                                MediaStreamInterface* stream) {
  if (pc_->IsClosed()) {
    return;
  }
  rtp_manager()->RemoveVideoTrack(track, stream);
  UpdateNegotiationNeeded();
}

RTCError SdpOfferAnswerHandler::Rollback(SdpType desc_type) {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::Rollback");
  auto state = signaling_state();
  if (state != PeerConnectionInterface::kHaveLocalOffer &&
      state != PeerConnectionInterface::kHaveRemoteOffer) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::INVALID_STATE,
        (rtc::StringBuilder("Called in wrong signalingState: ")
         << (PeerConnectionInterface::AsString(signaling_state())))
            .Release());
  }
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(IsUnifiedPlan());
  std::vector<rtc::scoped_refptr<RtpTransceiverInterface>>
      now_receiving_transceivers;
  std::vector<rtc::scoped_refptr<MediaStreamInterface>> all_added_streams;
  std::vector<rtc::scoped_refptr<MediaStreamInterface>> all_removed_streams;
  std::vector<rtc::scoped_refptr<RtpReceiverInterface>> removed_receivers;

  for (auto&& transceivers_stable_state_pair : transceivers()->StableStates()) {
    auto transceiver = transceivers_stable_state_pair.first;
    auto state = transceivers_stable_state_pair.second;

    if (state.did_set_fired_direction()) {
      // If this rollback triggers going from not receiving to receving again,
      // we need to fire "ontrack".
      bool previously_fired_direction_is_recv =
          transceiver->fired_direction().has_value() &&
          RtpTransceiverDirectionHasRecv(*transceiver->fired_direction());
      bool currently_fired_direction_is_recv =
          state.fired_direction().has_value() &&
          RtpTransceiverDirectionHasRecv(state.fired_direction().value());
      if (!previously_fired_direction_is_recv &&
          currently_fired_direction_is_recv) {
        now_receiving_transceivers.push_back(transceiver);
      }
      transceiver->internal()->set_fired_direction(state.fired_direction());
    }

    if (state.remote_stream_ids()) {
      std::vector<rtc::scoped_refptr<MediaStreamInterface>> added_streams;
      std::vector<rtc::scoped_refptr<MediaStreamInterface>> removed_streams;
      SetAssociatedRemoteStreams(transceiver->internal()->receiver_internal(),
                                 state.remote_stream_ids().value(),
                                 &added_streams, &removed_streams);
      all_added_streams.insert(all_added_streams.end(), added_streams.begin(),
                               added_streams.end());
      all_removed_streams.insert(all_removed_streams.end(),
                                 removed_streams.begin(),
                                 removed_streams.end());
      if (!state.has_m_section() && !state.newly_created()) {
        continue;
      }
    }

    // Due to the above `continue` statement, the below code only runs if there
    // is a change in mid association (has_m_section), if the transceiver was
    // newly created (newly_created) or if remote streams were not set.

    RTC_DCHECK(transceiver->internal()->mid().has_value());
    transceiver->internal()->ClearChannel();

    if (signaling_state() == PeerConnectionInterface::kHaveRemoteOffer &&
        transceiver->receiver()) {
      removed_receivers.push_back(transceiver->receiver());
    }
    if (state.newly_created()) {
      if (transceiver->internal()->reused_for_addtrack()) {
        transceiver->internal()->set_created_by_addtrack(true);
      } else {
        transceiver->internal()->StopTransceiverProcedure();
        transceivers()->Remove(transceiver);
      }
    }
    if (state.init_send_encodings()) {
      transceiver->internal()->sender_internal()->set_init_send_encodings(
          state.init_send_encodings().value());
    }
    transceiver->internal()->sender_internal()->set_transport(nullptr);
    transceiver->internal()->receiver_internal()->set_transport(nullptr);
    if (state.has_m_section()) {
      transceiver->internal()->set_mid(state.mid());
      transceiver->internal()->set_mline_index(state.mline_index());
    }
  }
  RTCError e = transport_controller_s()->RollbackTransports();
  if (!e.ok()) {
    return e;
  }
  transceivers()->DiscardStableStates();
  pending_local_description_.reset();
  pending_remote_description_.reset();
  ChangeSignalingState(PeerConnectionInterface::kStable);

  // Once all processing has finished, fire off callbacks.
  for (const auto& transceiver : now_receiving_transceivers) {
    pc_->Observer()->OnTrack(transceiver);
    pc_->Observer()->OnAddTrack(transceiver->receiver(),
                                transceiver->receiver()->streams());
  }
  for (const auto& receiver : removed_receivers) {
    pc_->Observer()->OnRemoveTrack(receiver);
  }
  for (const auto& stream : all_added_streams) {
    pc_->Observer()->OnAddStream(stream);
  }
  for (const auto& stream : all_removed_streams) {
    pc_->Observer()->OnRemoveStream(stream);
  }

  // The assumption is that in case of implicit rollback
  // UpdateNegotiationNeeded gets called in SetRemoteDescription.
  if (desc_type == SdpType::kRollback) {
    UpdateNegotiationNeeded();
    if (is_negotiation_needed_) {
      // Legacy version.
      pc_->Observer()->OnRenegotiationNeeded();
      // Spec-compliant version; the event may get invalidated before firing.
      GenerateNegotiationNeededEvent();
    }
  }
  return RTCError::OK();
}

bool SdpOfferAnswerHandler::IsUnifiedPlan() const {
  return pc_->IsUnifiedPlan();
}

void SdpOfferAnswerHandler::OnOperationsChainEmpty() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (pc_->IsClosed() || !update_negotiation_needed_on_empty_chain_)
    return;
  update_negotiation_needed_on_empty_chain_ = false;
  // Firing when chain is empty is only supported in Unified Plan to avoid
  // Plan B regressions. (In Plan B, onnegotiationneeded is already broken
  // anyway, so firing it even more might just be confusing.)
  if (IsUnifiedPlan()) {
    UpdateNegotiationNeeded();
  }
}

absl::optional<bool> SdpOfferAnswerHandler::is_caller() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return is_caller_;
}

bool SdpOfferAnswerHandler::HasNewIceCredentials() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return local_ice_credentials_to_replace_->HasIceCredentials();
}

bool SdpOfferAnswerHandler::IceRestartPending(
    const std::string& content_name) const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return pending_ice_restarts_.find(content_name) !=
         pending_ice_restarts_.end();
}

bool SdpOfferAnswerHandler::NeedsIceRestart(
    const std::string& content_name) const {
  return pc_->NeedsIceRestart(content_name);
}

absl::optional<rtc::SSLRole> SdpOfferAnswerHandler::GetDtlsRole(
    const std::string& mid) const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return transport_controller_s()->GetDtlsRole(mid);
}

void SdpOfferAnswerHandler::UpdateNegotiationNeeded() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (!IsUnifiedPlan()) {
    pc_->Observer()->OnRenegotiationNeeded();
    GenerateNegotiationNeededEvent();
    return;
  }

  // In the spec, a task is queued here to run the following steps - this is
  // meant to ensure we do not fire onnegotiationneeded prematurely if
  // multiple changes are being made at once. In order to support Chromium's
  // implementation where the JavaScript representation of the PeerConnection
  // lives on a separate thread though, the queuing of a task is instead
  // performed by the PeerConnectionObserver posting from the signaling thread
  // to the JavaScript main thread that negotiation is needed. And because the
  // Operations Chain lives on the WebRTC signaling thread,
  // ShouldFireNegotiationNeededEvent() must be called before firing the event
  // to ensure the Operations Chain is still empty and the event has not been
  // invalidated.

  // If connection's [[IsClosed]] slot is true, abort these steps.
  if (pc_->IsClosed())
    return;

  // If connection's signaling state is not "stable", abort these steps.
  if (signaling_state() != PeerConnectionInterface::kStable)
    return;

  // NOTE
  // The negotiation-needed flag will be updated once the state transitions to
  // "stable", as part of the steps for setting an RTCSessionDescription.

  // If the result of checking if negotiation is needed is false, clear the
  // negotiation-needed flag by setting connection's [[NegotiationNeeded]]
  // slot to false, and abort these steps.
  bool is_negotiation_needed = CheckIfNegotiationIsNeeded();
  if (!is_negotiation_needed) {
    is_negotiation_needed_ = false;
    // Invalidate any negotiation needed event that may previosuly have been
    // generated.
    ++negotiation_needed_event_id_;
    return;
  }

  // If connection's [[NegotiationNeeded]] slot is already true, abort these
  // steps.
  if (is_negotiation_needed_)
    return;

  // Set connection's [[NegotiationNeeded]] slot to true.
  is_negotiation_needed_ = true;

  // Queue a task that runs the following steps:
  // If connection's [[IsClosed]] slot is true, abort these steps.
  // If connection's [[NegotiationNeeded]] slot is false, abort these steps.
  // Fire an event named negotiationneeded at connection.
  pc_->Observer()->OnRenegotiationNeeded();
  // Fire the spec-compliant version; when ShouldFireNegotiationNeededEvent()
  // is used in the task queued by the observer, this event will only fire
  // when the chain is empty.
  GenerateNegotiationNeededEvent();
}

void SdpOfferAnswerHandler::AllocateSctpSids() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (!local_description() || !remote_description()) {
    RTC_DLOG(LS_VERBOSE)
        << "Local and Remote descriptions must be applied to get the "
           "SSL Role of the SCTP transport.";
    return;
  }

  absl::optional<rtc::SSLRole> guessed_role = GuessSslRole();
  network_thread()->BlockingCall(
      [&, data_channel_controller = data_channel_controller()] {
        RTC_DCHECK_RUN_ON(network_thread());
        absl::optional<rtc::SSLRole> role = pc_->GetSctpSslRole_n();
        if (!role)
          role = guessed_role;
        if (role)
          data_channel_controller->AllocateSctpSids(*role);
      });
}

absl::optional<rtc::SSLRole> SdpOfferAnswerHandler::GuessSslRole() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (!pc_->sctp_mid())
    return absl::nullopt;

  // TODO(bugs.webrtc.org/13668): This guesswork is guessing wrong (returning
  // SSL_CLIENT = ACTIVE) if remote offer has role ACTIVE, but we'll be able
  // to detect that by looking at the SDP.
  //
  // The phases of establishing an SCTP session are:
  //
  // Offerer:
  //
  // * Before negotiation: Neither is_caller nor sctp_mid exists.
  // * After setting an offer as local description: is_caller is known (true),
  //   sctp_mid is known, but we don't know the SSL role for sure (or if we'll
  //   eventually get an SCTP session).
  // * After setting an answer as the remote description: We know is_caller,
  //   sctp_mid and that we'll get the SCTP channel established (m-section
  //   wasn't rejected).
  // * Special case: The SCTP  m-section was rejected: Close datachannels.
  // * We MAY know the SSL role if we offered actpass and got back active or
  //   passive; if the other end is a webrtc implementation, it will be active.
  // * After the TLS handshake: We have a definitive answer on the SSL role.
  //
  // Answerer:
  //
  // * After setting an offer as remote description: We know is_caller (false).
  // * If there was an SCTP session, we know the SCTP mid. We also know the
  //   SSL role, since if the remote offer was actpass or passive, we'll answer
  //   active, and if the remote offer was active, we're passive.
  // * Special case: No SCTP m= line. We don't know for sure if the remote
  //   doesn't support it or just didn't offer it. Not sure what we do in this
  //   case (logic would suggest fire a `negotiationneeded` event and generate a
  //   subsequent offer, but this needs to be tested).
  // * After the TLS handshake: We know that TLS obeyed the protocol. There
  //   should be an error surfaced somewhere if it didn't.
  // * "Guessing" should always be correct if we get an SCTP session and are not
  //   the offerer.

  return is_caller() ? rtc::SSL_SERVER : rtc::SSL_CLIENT;
}

bool SdpOfferAnswerHandler::CheckIfNegotiationIsNeeded() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // 1. If any implementation-specific negotiation is required, as described
  // at the start of this section, return true.

  // 2. If connection.[[LocalIceCredentialsToReplace]] is not empty, return
  // true.
  if (local_ice_credentials_to_replace_->HasIceCredentials()) {
    return true;
  }

  // 3. Let description be connection.[[CurrentLocalDescription]].
  const SessionDescriptionInterface* description = current_local_description();
  if (!description)
    return true;

  // 4. If connection has created any RTCDataChannels, and no m= section in
  // description has been negotiated yet for data, return true.
  if (data_channel_controller()->HasUsedDataChannels()) {
    const cricket::ContentInfo* data_content =
        cricket::GetFirstDataContent(description->description()->contents());
    if (!data_content) {
      return true;
    }
    // The remote end might have rejected the data content.
    const cricket::ContentInfo* remote_data_content =
        current_remote_description()
            ? current_remote_description()->description()->GetContentByName(
                  data_content->name)
            : nullptr;
    if (remote_data_content && remote_data_content->rejected) {
      return true;
    }
  }
  if (!ConfiguredForMedia()) {
    return false;
  }

  // 5. For each transceiver in connection's set of transceivers, perform the
  // following checks:
  for (const auto& transceiver : transceivers()->ListInternal()) {
    const ContentInfo* current_local_msection =
        FindTransceiverMSection(transceiver, description);

    const ContentInfo* current_remote_msection =
        FindTransceiverMSection(transceiver, current_remote_description());

    // 5.4 If transceiver is stopped and is associated with an m= section,
    // but the associated m= section is not yet rejected in
    // connection.[[CurrentLocalDescription]] or
    // connection.[[CurrentRemoteDescription]], return true.
    if (transceiver->stopped()) {
      RTC_DCHECK(transceiver->stopping());
      if (current_local_msection && !current_local_msection->rejected &&
          ((current_remote_msection && !current_remote_msection->rejected) ||
           !current_remote_msection)) {
        return true;
      }
      continue;
    }

    // 5.1 If transceiver.[[Stopping]] is true and transceiver.[[Stopped]] is
    // false, return true.
    if (transceiver->stopping() && !transceiver->stopped())
      return true;

    // 5.2 If transceiver isn't stopped and isn't yet associated with an m=
    // section in description, return true.
    if (!current_local_msection)
      return true;

    const MediaContentDescription* current_local_media_description =
        current_local_msection->media_description();
    // 5.3 If transceiver isn't stopped and is associated with an m= section
    // in description then perform the following checks:

    // 5.3.1 If transceiver.[[Direction]] is "sendrecv" or "sendonly", and the
    // associated m= section in description either doesn't contain a single
    // "a=msid" line, or the number of MSIDs from the "a=msid" lines in this
    // m= section, or the MSID values themselves, differ from what is in
    // transceiver.sender.[[AssociatedMediaStreamIds]], return true.
    if (RtpTransceiverDirectionHasSend(transceiver->direction())) {
      if (current_local_media_description->streams().size() == 0)
        return true;

      std::vector<std::string> msection_msids;
      for (const auto& stream : current_local_media_description->streams()) {
        for (const std::string& msid : stream.stream_ids())
          msection_msids.push_back(msid);
      }

      std::vector<std::string> transceiver_msids =
          transceiver->sender()->stream_ids();
      if (msection_msids.size() != transceiver_msids.size())
        return true;

      absl::c_sort(transceiver_msids);
      absl::c_sort(msection_msids);
      if (transceiver_msids != msection_msids)
        return true;
    }

    // 5.3.2 If description is of type "offer", and the direction of the
    // associated m= section in neither connection.[[CurrentLocalDescription]]
    // nor connection.[[CurrentRemoteDescription]] matches
    // transceiver.[[Direction]], return true.
    if (description->GetType() == SdpType::kOffer) {
      if (!current_remote_description())
        return true;

      if (!current_remote_msection)
        return true;

      RtpTransceiverDirection current_local_direction =
          current_local_media_description->direction();
      RtpTransceiverDirection current_remote_direction =
          current_remote_msection->media_description()->direction();
      if (transceiver->direction() != current_local_direction &&
          transceiver->direction() !=
              RtpTransceiverDirectionReversed(current_remote_direction)) {
        return true;
      }
    }

    // 5.3.3 If description is of type "answer", and the direction of the
    // associated m= section in the description does not match
    // transceiver.[[Direction]] intersected with the offered direction (as
    // described in [JSEP] (section 5.3.1.)), return true.
    if (description->GetType() == SdpType::kAnswer) {
      if (!remote_description())
        return true;

      const ContentInfo* offered_remote_msection =
          FindTransceiverMSection(transceiver, remote_description());

      RtpTransceiverDirection offered_direction =
          offered_remote_msection
              ? offered_remote_msection->media_description()->direction()
              : RtpTransceiverDirection::kInactive;

      if (current_local_media_description->direction() !=
          (RtpTransceiverDirectionIntersection(
              transceiver->direction(),
              RtpTransceiverDirectionReversed(offered_direction)))) {
        return true;
      }
    }
  }
  // If all the preceding checks were performed and true was not returned,
  // nothing remains to be negotiated; return false.
  return false;
}

void SdpOfferAnswerHandler::GenerateNegotiationNeededEvent() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  ++negotiation_needed_event_id_;
  pc_->Observer()->OnNegotiationNeededEvent(negotiation_needed_event_id_);
}

RTCError SdpOfferAnswerHandler::ValidateSessionDescription(
    const SessionDescriptionInterface* sdesc,
    cricket::ContentSource source,
    const std::map<std::string, const cricket::ContentGroup*>&
        bundle_groups_by_mid) {
  // An assumption is that a check for session error is done at a higher level.
  RTC_DCHECK_EQ(SessionError::kNone, session_error());

  if (!sdesc || !sdesc->description()) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER, kInvalidSdp);
  }

  SdpType type = sdesc->GetType();
  if ((source == cricket::CS_LOCAL && !ExpectSetLocalDescription(type)) ||
      (source == cricket::CS_REMOTE && !ExpectSetRemoteDescription(type))) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::INVALID_STATE,
        (rtc::StringBuilder("Called in wrong state: ")
         << PeerConnectionInterface::AsString(signaling_state()))
            .Release());
  }

  RTCError error = ValidateMids(*sdesc->description());
  if (!error.ok()) {
    return error;
  }

  // Verify crypto settings.
  std::string crypto_error;
  if (pc_->dtls_enabled()) {
    RTCError crypto_error = VerifyCrypto(
        sdesc->description(), pc_->dtls_enabled(), bundle_groups_by_mid);
    if (!crypto_error.ok()) {
      return crypto_error;
    }
  }

  // Verify ice-ufrag and ice-pwd.
  if (!VerifyIceUfragPwdPresent(sdesc->description(), bundle_groups_by_mid)) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                         kSdpWithoutIceUfragPwd);
  }

  // Validate that there are no collisions of bundled payload types.
  error = ValidateBundledPayloadTypes(*sdesc->description());
  // TODO(bugs.webrtc.org/14420): actually reject.
  RTC_HISTOGRAM_BOOLEAN("WebRTC.PeerConnection.ValidBundledPayloadTypes",
                        error.ok());

  // Validate that there are no collisions of bundled header extensions ids.
  error = ValidateBundledRtpHeaderExtensions(*sdesc->description());
  if (!error.ok()) {
    return error;
  }

  // TODO(crbug.com/1459124): remove killswitch after rollout.
  error = ValidateSsrcGroups(*sdesc->description());
  if (!error.ok() &&
      !pc_->trials().IsDisabled("WebRTC-PreventSsrcGroupsWithUnexpectedSize")) {
    return error;
  }

  if (!pc_->ValidateBundleSettings(sdesc->description(),
                                   bundle_groups_by_mid)) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                         kBundleWithoutRtcpMux);
  }

  error = ValidatePayloadTypes(*sdesc->description());
  if (!error.ok()) {
    return error;
  }

  // TODO(skvlad): When the local rtcp-mux policy is Require, reject any
  // m-lines that do not rtcp-mux enabled.

  // Verify m-lines in Answer when compared against Offer.
  if (type == SdpType::kPrAnswer || type == SdpType::kAnswer) {
    // With an answer we want to compare the new answer session description
    // with the offer's session description from the current negotiation.
    const cricket::SessionDescription* offer_desc =
        (source == cricket::CS_LOCAL) ? remote_description()->description()
                                      : local_description()->description();
    if (!MediaSectionsHaveSameCount(*offer_desc, *sdesc->description()) ||
        !MediaSectionsInSameOrder(*offer_desc, nullptr, *sdesc->description(),
                                  type)) {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                           kMlineMismatchInAnswer);
    }
  } else {
    // The re-offers should respect the order of m= sections in current
    // description. See RFC3264 Section 8 paragraph 4 for more details.
    // With a re-offer, either the current local or current remote
    // descriptions could be the most up to date, so we would like to check
    // against both of them if they exist. It could be the case that one of
    // them has a 0 port for a media section, but the other does not. This is
    // important to check against in the case that we are recycling an m=
    // section.
    const cricket::SessionDescription* current_desc = nullptr;
    const cricket::SessionDescription* secondary_current_desc = nullptr;
    if (local_description()) {
      current_desc = local_description()->description();
      if (remote_description()) {
        secondary_current_desc = remote_description()->description();
      }
    } else if (remote_description()) {
      current_desc = remote_description()->description();
    }
    if (current_desc &&
        !MediaSectionsInSameOrder(*current_desc, secondary_current_desc,
                                  *sdesc->description(), type)) {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                           kMlineMismatchInSubsequentOffer);
    }
  }

  if (IsUnifiedPlan()) {
    // Ensure that each audio and video media section has at most one
    // "StreamParams". This will return an error if receiving a session
    // description from a "Plan B" endpoint which adds multiple tracks of the
    // same type. With Unified Plan, there can only be at most one track per
    // media section.
    for (const ContentInfo& content : sdesc->description()->contents()) {
      const MediaContentDescription& desc = *content.media_description();
      if ((desc.type() == cricket::MEDIA_TYPE_AUDIO ||
           desc.type() == cricket::MEDIA_TYPE_VIDEO) &&
          desc.streams().size() > 1u) {
        LOG_AND_RETURN_ERROR(
            RTCErrorType::INVALID_PARAMETER,
            "Media section has more than one track specified with a=ssrc lines "
            "which is not supported with Unified Plan.");
      }
    }
    // Validate spec-simulcast which only works if the remote end negotiated the
    // mid and rid header extension.
    error = ValidateRtpHeaderExtensionsForSpecSimulcast(*sdesc->description());
    if (!error.ok()) {
      return error;
    }
  }

  return RTCError::OK();
}

RTCError SdpOfferAnswerHandler::UpdateTransceiversAndDataChannels(
    cricket::ContentSource source,
    const SessionDescriptionInterface& new_session,
    const SessionDescriptionInterface* old_local_description,
    const SessionDescriptionInterface* old_remote_description,
    const std::map<std::string, const cricket::ContentGroup*>&
        bundle_groups_by_mid) {
  TRACE_EVENT0("webrtc",
               "SdpOfferAnswerHandler::UpdateTransceiversAndDataChannels");
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(IsUnifiedPlan());

  if (new_session.GetType() == SdpType::kOffer) {
    // If the BUNDLE policy is max-bundle, then we know for sure that all
    // transports will be bundled from the start. Return an error if
    // max-bundle is specified but the session description does not have a
    // BUNDLE group.
    if (pc_->configuration()->bundle_policy ==
            PeerConnectionInterface::kBundlePolicyMaxBundle &&
        bundle_groups_by_mid.empty()) {
      LOG_AND_RETURN_ERROR(
          RTCErrorType::INVALID_PARAMETER,
          "max-bundle configured but session description has no BUNDLE group");
    }
  }

  const ContentInfos& new_contents = new_session.description()->contents();
  for (size_t i = 0; i < new_contents.size(); ++i) {
    const cricket::ContentInfo& new_content = new_contents[i];
    cricket::MediaType media_type = new_content.media_description()->type();
    mid_generator_.AddKnownId(new_content.name);
    auto it = bundle_groups_by_mid.find(new_content.name);
    const cricket::ContentGroup* bundle_group =
        it != bundle_groups_by_mid.end() ? it->second : nullptr;
    if (media_type == cricket::MEDIA_TYPE_AUDIO ||
        media_type == cricket::MEDIA_TYPE_VIDEO) {
      const cricket::ContentInfo* old_local_content = nullptr;
      if (old_local_description &&
          i < old_local_description->description()->contents().size()) {
        old_local_content =
            &old_local_description->description()->contents()[i];
      }
      const cricket::ContentInfo* old_remote_content = nullptr;
      if (old_remote_description &&
          i < old_remote_description->description()->contents().size()) {
        old_remote_content =
            &old_remote_description->description()->contents()[i];
      }
      auto transceiver_or_error =
          AssociateTransceiver(source, new_session.GetType(), i, new_content,
                               old_local_content, old_remote_content);
      if (!transceiver_or_error.ok()) {
        // In the case where a transceiver is rejected locally prior to being
        // associated, we don't expect to find a transceiver, but might find it
        // in the case where state is still "stopping", not "stopped".
        if (new_content.rejected) {
          continue;
        }
        return transceiver_or_error.MoveError();
      }
      auto transceiver = transceiver_or_error.MoveValue();
      RTCError error =
          UpdateTransceiverChannel(transceiver, new_content, bundle_group);
      // Handle locally rejected content. This code path is only needed for apps
      // that SDP munge. Remote rejected content is handled in
      // ApplyRemoteDescriptionUpdateTransceiverState().
      if (source == cricket::ContentSource::CS_LOCAL && new_content.rejected) {
        // Local offer.
        if (new_session.GetType() == SdpType::kOffer) {
          // If the RtpTransceiver API was used, it would already have made the
          // transceiver stopping. But if the rejection was caused by SDP
          // munging then we need to ensure the transceiver is stopping here.
          if (!transceiver->internal()->stopping()) {
            transceiver->internal()->StopStandard();
          }
          RTC_DCHECK(transceiver->internal()->stopping());
        } else {
          // Local answer.
          RTC_DCHECK(new_session.GetType() == SdpType::kAnswer ||
                     new_session.GetType() == SdpType::kPrAnswer);
          // When RtpTransceiver API is used, rejection happens in the offer and
          // the transceiver will already be stopped at local answer time
          // (calling stop between SRD(offer) and SLD(answer) would not reject
          // the content in the answer - instead this would trigger a follow-up
          // O/A exchange). So if the content was rejected but the transceiver
          // is not already stopped, SDP munging has happened and we need to
          // ensure the transceiver is stopped.
          if (!transceiver->internal()->stopped()) {
            transceiver->internal()->StopTransceiverProcedure();
          }
          RTC_DCHECK(transceiver->internal()->stopped());
        }
      }
      if (!error.ok()) {
        return error;
      }
    } else if (media_type == cricket::MEDIA_TYPE_DATA) {
      const auto data_mid = pc_->sctp_mid();
      if (data_mid && new_content.name != data_mid.value()) {
        // Ignore all but the first data section.
        RTC_LOG(LS_INFO) << "Ignoring data media section with MID="
                         << new_content.name;
        continue;
      }
      RTCError error =
          UpdateDataChannelTransport(source, new_content, bundle_group);
      if (!error.ok()) {
        return error;
      }
    } else if (media_type == cricket::MEDIA_TYPE_UNSUPPORTED) {
      RTC_LOG(LS_INFO) << "Ignoring unsupported media type";
    } else {
      LOG_AND_RETURN_ERROR(RTCErrorType::INTERNAL_ERROR,
                           "Unknown section type.");
    }
  }

  return RTCError::OK();
}

RTCErrorOr<rtc::scoped_refptr<RtpTransceiverProxyWithInternal<RtpTransceiver>>>
SdpOfferAnswerHandler::AssociateTransceiver(
    cricket::ContentSource source,
    SdpType type,
    size_t mline_index,
    const ContentInfo& content,
    const ContentInfo* old_local_content,
    const ContentInfo* old_remote_content) {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::AssociateTransceiver");
  RTC_DCHECK(IsUnifiedPlan());
#if RTC_DCHECK_IS_ON
  // If this is an offer then the m= section might be recycled. If the m=
  // section is being recycled (defined as: rejected in the current local or
  // remote description and not rejected in new description), the transceiver
  // should have been removed by RemoveStoppedtransceivers()->
  if (IsMediaSectionBeingRecycled(type, content, old_local_content,
                                  old_remote_content)) {
    const std::string& old_mid =
        (old_local_content && old_local_content->rejected)
            ? old_local_content->name
            : old_remote_content->name;
    auto old_transceiver = transceivers()->FindByMid(old_mid);
    // The transceiver should be disassociated in RemoveStoppedTransceivers()
    RTC_DCHECK(!old_transceiver);
  }
#endif

  const MediaContentDescription* media_desc = content.media_description();
  auto transceiver = transceivers()->FindByMid(content.name);
  if (source == cricket::CS_LOCAL) {
    // Find the RtpTransceiver that corresponds to this m= section, using the
    // mapping between transceivers and m= section indices established when
    // creating the offer.
    if (!transceiver) {
      transceiver = transceivers()->FindByMLineIndex(mline_index);
    }
    if (!transceiver) {
      // This may happen normally when media sections are rejected.
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                           "Transceiver not found based on m-line index");
    }
  } else {
    RTC_DCHECK_EQ(source, cricket::CS_REMOTE);
    // If the m= section is sendrecv or recvonly, and there are RtpTransceivers
    // of the same type...
    // When simulcast is requested, a transceiver cannot be associated because
    // AddTrack cannot be called to initialize it.
    if (!transceiver &&
        RtpTransceiverDirectionHasRecv(media_desc->direction()) &&
        !media_desc->HasSimulcast()) {
      transceiver = FindAvailableTransceiverToReceive(media_desc->type());
    }
    // If no RtpTransceiver was found in the previous step, create one with a
    // recvonly direction.
    if (!transceiver) {
      RTC_LOG(LS_INFO) << "Adding "
                       << cricket::MediaTypeToString(media_desc->type())
                       << " transceiver for MID=" << content.name
                       << " at i=" << mline_index
                       << " in response to the remote description.";
      std::string sender_id = rtc::CreateRandomUuid();
      std::vector<RtpEncodingParameters> send_encodings =
          GetSendEncodingsFromRemoteDescription(*media_desc);
      auto sender = rtp_manager()->CreateSender(media_desc->type(), sender_id,
                                                nullptr, {}, send_encodings);
      std::string receiver_id;
      if (!media_desc->streams().empty()) {
        receiver_id = media_desc->streams()[0].id;
      } else {
        receiver_id = rtc::CreateRandomUuid();
      }
      auto receiver =
          rtp_manager()->CreateReceiver(media_desc->type(), receiver_id);
      transceiver = rtp_manager()->CreateAndAddTransceiver(sender, receiver);
      transceiver->internal()->set_direction(
          RtpTransceiverDirection::kRecvOnly);
      if (type == SdpType::kOffer) {
        transceivers()->StableState(transceiver)->set_newly_created();
      }
    }

    RTC_DCHECK(transceiver);

    // Check if the offer indicated simulcast but the answer rejected it.
    // This can happen when simulcast is not supported on the remote party.
    if (SimulcastIsRejected(old_local_content, *media_desc,
                            pc_->GetCryptoOptions()
                                .srtp.enable_encrypted_rtp_header_extensions)) {
      RTCError error =
          DisableSimulcastInSender(transceiver->internal()->sender_internal());
      if (!error.ok()) {
        RTC_LOG(LS_ERROR) << "Failed to remove rejected simulcast.";
        return std::move(error);
      }
    }
  }

  if (transceiver->media_type() != media_desc->type()) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::INVALID_PARAMETER,
        "Transceiver type does not match media description type.");
  }

  if (media_desc->HasSimulcast()) {
    std::vector<SimulcastLayer> layers =
        source == cricket::CS_LOCAL
            ? media_desc->simulcast_description().send_layers().GetAllLayers()
            : media_desc->simulcast_description()
                  .receive_layers()
                  .GetAllLayers();
    RTCError error = UpdateSimulcastLayerStatusInSender(
        layers, transceiver->internal()->sender_internal());
    if (!error.ok()) {
      RTC_LOG(LS_ERROR) << "Failed updating status for simulcast layers.";
      return std::move(error);
    }
  }
  if (type == SdpType::kOffer) {
    bool state_changes = transceiver->internal()->mid() != content.name ||
                         transceiver->internal()->mline_index() != mline_index;
    if (state_changes) {
      transceivers()
          ->StableState(transceiver)
          ->SetMSectionIfUnset(transceiver->internal()->mid(),
                               transceiver->internal()->mline_index());
    }
  }
  // Associate the found or created RtpTransceiver with the m= section by
  // setting the value of the RtpTransceiver's mid property to the MID of the m=
  // section, and establish a mapping between the transceiver and the index of
  // the m= section.
  transceiver->internal()->set_mid(content.name);
  transceiver->internal()->set_mline_index(mline_index);
  return std::move(transceiver);
}

RTCError SdpOfferAnswerHandler::UpdateTransceiverChannel(
    rtc::scoped_refptr<RtpTransceiverProxyWithInternal<RtpTransceiver>>
        transceiver,
    const cricket::ContentInfo& content,
    const cricket::ContentGroup* bundle_group) {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::UpdateTransceiverChannel");
  RTC_DCHECK(IsUnifiedPlan());
  RTC_DCHECK(transceiver);
  cricket::ChannelInterface* channel = transceiver->internal()->channel();
  if (content.rejected) {
    if (channel) {
      transceiver->internal()->ClearChannel();
    }
  } else {
    if (!channel) {
      auto error = transceiver->internal()->CreateChannel(
          content.name, pc_->call_ptr(), pc_->configuration()->media_config,
          pc_->SrtpRequired(), pc_->GetCryptoOptions(), audio_options(),
          video_options(), video_bitrate_allocator_factory_.get(),
          [&](absl::string_view mid) {
            RTC_DCHECK_RUN_ON(network_thread());
            return transport_controller_n()->GetRtpTransport(mid);
          });
      if (!error.ok()) {
        return error;
      }
    }
  }
  return RTCError::OK();
}

RTCError SdpOfferAnswerHandler::UpdateDataChannelTransport(
    cricket::ContentSource source,
    const cricket::ContentInfo& content,
    const cricket::ContentGroup* bundle_group) {
  if (content.rejected) {
    RTC_LOG(LS_INFO) << "Rejected data channel transport with mid="
                     << content.mid();

    rtc::StringBuilder sb;
    sb << "Rejected data channel transport with mid=" << content.mid();
    RTCError error(RTCErrorType::OPERATION_ERROR_WITH_DATA, sb.Release());
    error.set_error_detail(RTCErrorDetailType::DATA_CHANNEL_FAILURE);
    pc_->DestroyDataChannelTransport(error);
  } else if (!pc_->CreateDataChannelTransport(content.name)) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INTERNAL_ERROR,
                         "Failed to create data channel.");
  }
  return RTCError::OK();
}

bool SdpOfferAnswerHandler::ExpectSetLocalDescription(SdpType type) {
  PeerConnectionInterface::SignalingState state = signaling_state();
  if (type == SdpType::kOffer) {
    return (state == PeerConnectionInterface::kStable) ||
           (state == PeerConnectionInterface::kHaveLocalOffer);
  } else {
    RTC_DCHECK(type == SdpType::kPrAnswer || type == SdpType::kAnswer);
    return (state == PeerConnectionInterface::kHaveRemoteOffer) ||
           (state == PeerConnectionInterface::kHaveLocalPrAnswer);
  }
}

bool SdpOfferAnswerHandler::ExpectSetRemoteDescription(SdpType type) {
  PeerConnectionInterface::SignalingState state = signaling_state();
  if (type == SdpType::kOffer) {
    return (state == PeerConnectionInterface::kStable) ||
           (state == PeerConnectionInterface::kHaveRemoteOffer);
  } else {
    RTC_DCHECK(type == SdpType::kPrAnswer || type == SdpType::kAnswer);
    return (state == PeerConnectionInterface::kHaveLocalOffer) ||
           (state == PeerConnectionInterface::kHaveRemotePrAnswer);
  }
}

void SdpOfferAnswerHandler::FillInMissingRemoteMids(
    cricket::SessionDescription* new_remote_description) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(new_remote_description);
  const cricket::ContentInfos no_infos;
  const cricket::ContentInfos& local_contents =
      (local_description() ? local_description()->description()->contents()
                           : no_infos);
  const cricket::ContentInfos& remote_contents =
      (remote_description() ? remote_description()->description()->contents()
                            : no_infos);
  for (size_t i = 0; i < new_remote_description->contents().size(); ++i) {
    cricket::ContentInfo& content = new_remote_description->contents()[i];
    if (!content.name.empty()) {
      continue;
    }
    std::string new_mid;
    absl::string_view source_explanation;
    if (IsUnifiedPlan()) {
      if (i < local_contents.size()) {
        new_mid = local_contents[i].name;
        source_explanation = "from the matching local media section";
      } else if (i < remote_contents.size()) {
        new_mid = remote_contents[i].name;
        source_explanation = "from the matching previous remote media section";
      } else {
        new_mid = mid_generator_.GenerateString();
        source_explanation = "generated just now";
      }
    } else {
      new_mid = std::string(
          GetDefaultMidForPlanB(content.media_description()->type()));
      source_explanation = "to match pre-existing behavior";
    }
    RTC_DCHECK(!new_mid.empty());
    content.name = new_mid;
    new_remote_description->transport_infos()[i].content_name = new_mid;
    RTC_LOG(LS_INFO) << "SetRemoteDescription: Remote media section at i=" << i
                     << " is missing an a=mid line. Filling in the value '"
                     << new_mid << "' " << source_explanation << ".";
  }
}

rtc::scoped_refptr<RtpTransceiverProxyWithInternal<RtpTransceiver>>
SdpOfferAnswerHandler::FindAvailableTransceiverToReceive(
    cricket::MediaType media_type) const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(IsUnifiedPlan());
  // From JSEP section 5.10 (Applying a Remote Description):
  // If the m= section is sendrecv or recvonly, and there are RtpTransceivers of
  // the same type that were added to the PeerConnection by addTrack and are not
  // associated with any m= section and are not stopped, find the first such
  // RtpTransceiver.
  for (auto transceiver : transceivers()->List()) {
    if (transceiver->media_type() == media_type &&
        transceiver->internal()->created_by_addtrack() && !transceiver->mid() &&
        !transceiver->stopped()) {
      return transceiver;
    }
  }
  return nullptr;
}

const cricket::ContentInfo*
SdpOfferAnswerHandler::FindMediaSectionForTransceiver(
    const RtpTransceiver* transceiver,
    const SessionDescriptionInterface* sdesc) const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(transceiver);
  RTC_DCHECK(sdesc);
  if (IsUnifiedPlan()) {
    if (!transceiver->mid()) {
      // This transceiver is not associated with a media section yet.
      return nullptr;
    }
    return sdesc->description()->GetContentByName(*transceiver->mid());
  } else {
    // Plan B only allows at most one audio and one video section, so use the
    // first media section of that type.
    return cricket::GetFirstMediaContent(sdesc->description()->contents(),
                                         transceiver->media_type());
  }
}

void SdpOfferAnswerHandler::GetOptionsForOffer(
    const PeerConnectionInterface::RTCOfferAnswerOptions& offer_answer_options,
    cricket::MediaSessionOptions* session_options) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  ExtractSharedMediaSessionOptions(offer_answer_options, session_options);

  if (IsUnifiedPlan()) {
    GetOptionsForUnifiedPlanOffer(offer_answer_options, session_options);
  } else {
    GetOptionsForPlanBOffer(offer_answer_options, session_options);
  }

  // Apply ICE restart flag and renomination flag.
  bool ice_restart = offer_answer_options.ice_restart || HasNewIceCredentials();
  for (auto& options : session_options->media_description_options) {
    options.transport_options.ice_restart = ice_restart;
    options.transport_options.enable_ice_renomination =
        pc_->configuration()->enable_ice_renomination;
  }

  session_options->rtcp_cname = rtcp_cname_;
  session_options->crypto_options = pc_->GetCryptoOptions();
  session_options->pooled_ice_credentials =
      context_->network_thread()->BlockingCall(
          [this] { return port_allocator()->GetPooledIceCredentials(); });
  session_options->offer_extmap_allow_mixed =
      pc_->configuration()->offer_extmap_allow_mixed;

  // Allow fallback for using obsolete SCTP syntax.
  // Note that the default in `session_options` is true, while
  // the default in `options` is false.
  session_options->use_obsolete_sctp_sdp =
      offer_answer_options.use_obsolete_sctp_sdp;
}

void SdpOfferAnswerHandler::GetOptionsForPlanBOffer(
    const PeerConnectionInterface::RTCOfferAnswerOptions& offer_answer_options,
    cricket::MediaSessionOptions* session_options) {
  bool offer_new_data_description =
      data_channel_controller()->HasUsedDataChannels();
  bool send_audio = false;
  bool send_video = false;
  bool recv_audio = false;
  bool recv_video = false;
  if (ConfiguredForMedia()) {
    // Figure out transceiver directional preferences.
    send_audio =
        !rtp_manager()->GetAudioTransceiver()->internal()->senders().empty();
    send_video =
        !rtp_manager()->GetVideoTransceiver()->internal()->senders().empty();

    // By default, generate sendrecv/recvonly m= sections.
    recv_audio = true;
    recv_video = true;
  }
  // By default, only offer a new m= section if we have media to send with it.
  bool offer_new_audio_description = send_audio;
  bool offer_new_video_description = send_video;
  if (ConfiguredForMedia()) {
    // The "offer_to_receive_X" options allow those defaults to be overridden.
    if (offer_answer_options.offer_to_receive_audio !=
        PeerConnectionInterface::RTCOfferAnswerOptions::kUndefined) {
      recv_audio = (offer_answer_options.offer_to_receive_audio > 0);
      offer_new_audio_description =
          offer_new_audio_description ||
          (offer_answer_options.offer_to_receive_audio > 0);
    }
    if (offer_answer_options.offer_to_receive_video !=
        RTCOfferAnswerOptions::kUndefined) {
      recv_video = (offer_answer_options.offer_to_receive_video > 0);
      offer_new_video_description =
          offer_new_video_description ||
          (offer_answer_options.offer_to_receive_video > 0);
    }
  }
  absl::optional<size_t> audio_index;
  absl::optional<size_t> video_index;
  absl::optional<size_t> data_index;
  // If a current description exists, generate m= sections in the same order,
  // using the first audio/video/data section that appears and rejecting
  // extraneous ones.
  if (local_description()) {
    GenerateMediaDescriptionOptions(
        local_description(),
        RtpTransceiverDirectionFromSendRecv(send_audio, recv_audio),
        RtpTransceiverDirectionFromSendRecv(send_video, recv_video),
        &audio_index, &video_index, &data_index, session_options);
  }

  if (ConfiguredForMedia()) {
    // Add audio/video/data m= sections to the end if needed.
    if (!audio_index && offer_new_audio_description) {
      cricket::MediaDescriptionOptions options(
          cricket::MEDIA_TYPE_AUDIO, cricket::CN_AUDIO,
          RtpTransceiverDirectionFromSendRecv(send_audio, recv_audio), false);
      options.header_extensions =
          media_engine()->voice().GetRtpHeaderExtensions();
      session_options->media_description_options.push_back(options);
      audio_index = session_options->media_description_options.size() - 1;
    }
    if (!video_index && offer_new_video_description) {
      cricket::MediaDescriptionOptions options(
          cricket::MEDIA_TYPE_VIDEO, cricket::CN_VIDEO,
          RtpTransceiverDirectionFromSendRecv(send_video, recv_video), false);
      options.header_extensions =
          media_engine()->video().GetRtpHeaderExtensions();
      session_options->media_description_options.push_back(options);
      video_index = session_options->media_description_options.size() - 1;
    }
    cricket::MediaDescriptionOptions* audio_media_description_options =
        !audio_index
            ? nullptr
            : &session_options->media_description_options[*audio_index];
    cricket::MediaDescriptionOptions* video_media_description_options =
        !video_index
            ? nullptr
            : &session_options->media_description_options[*video_index];

    AddPlanBRtpSenderOptions(rtp_manager()->GetSendersInternal(),
                             audio_media_description_options,
                             video_media_description_options,
                             offer_answer_options.num_simulcast_layers);
  }
  if (!data_index && offer_new_data_description) {
    session_options->media_description_options.push_back(
        GetMediaDescriptionOptionsForActiveData(cricket::CN_DATA));
  }
}

void SdpOfferAnswerHandler::GetOptionsForUnifiedPlanOffer(
    const RTCOfferAnswerOptions& offer_answer_options,
    cricket::MediaSessionOptions* session_options) {
  // Rules for generating an offer are dictated by JSEP sections 5.2.1 (Initial
  // Offers) and 5.2.2 (Subsequent Offers).
  RTC_DCHECK_EQ(session_options->media_description_options.size(), 0);
  const ContentInfos no_infos;
  const ContentInfos& local_contents =
      (local_description() ? local_description()->description()->contents()
                           : no_infos);
  const ContentInfos& remote_contents =
      (remote_description() ? remote_description()->description()->contents()
                            : no_infos);
  // The mline indices that can be recycled. New transceivers should reuse these
  // slots first.
  std::queue<size_t> recycleable_mline_indices;
  // First, go through each media section that exists in either the local or
  // remote description and generate a media section in this offer for the
  // associated transceiver. If a media section can be recycled, generate a
  // default, rejected media section here that can be later overwritten.
  for (size_t i = 0;
       i < std::max(local_contents.size(), remote_contents.size()); ++i) {
    // Either `local_content` or `remote_content` is non-null.
    const ContentInfo* local_content =
        (i < local_contents.size() ? &local_contents[i] : nullptr);
    const ContentInfo* current_local_content =
        GetContentByIndex(current_local_description(), i);
    const ContentInfo* remote_content =
        (i < remote_contents.size() ? &remote_contents[i] : nullptr);
    const ContentInfo* current_remote_content =
        GetContentByIndex(current_remote_description(), i);
    bool had_been_rejected =
        (current_local_content && current_local_content->rejected) ||
        (current_remote_content && current_remote_content->rejected);
    const std::string& mid =
        (local_content ? local_content->name : remote_content->name);
    cricket::MediaType media_type =
        (local_content ? local_content->media_description()->type()
                       : remote_content->media_description()->type());
    if (media_type == cricket::MEDIA_TYPE_AUDIO ||
        media_type == cricket::MEDIA_TYPE_VIDEO) {
      // A media section is considered eligible for recycling if it is marked as
      // rejected in either the current local or current remote description.
      auto transceiver = transceivers()->FindByMid(mid);
      if (!transceiver) {
        // No associated transceiver. The media section has been stopped.
        recycleable_mline_indices.push(i);
        session_options->media_description_options.push_back(
            cricket::MediaDescriptionOptions(media_type, mid,
                                             RtpTransceiverDirection::kInactive,
                                             /*stopped=*/true));
      } else {
        // NOTE: a stopping transceiver should be treated as a stopped one in
        // createOffer as specified in
        // https://w3c.github.io/webrtc-pc/#dom-rtcpeerconnection-createoffer.
        if (had_been_rejected && transceiver->stopping()) {
          session_options->media_description_options.push_back(
              cricket::MediaDescriptionOptions(
                  transceiver->media_type(), mid,
                  RtpTransceiverDirection::kInactive,
                  /*stopped=*/true));
          recycleable_mline_indices.push(i);
        } else {
          session_options->media_description_options.push_back(
              GetMediaDescriptionOptionsForTransceiver(
                  transceiver->internal(), mid,
                  /*is_create_offer=*/true));
          // CreateOffer shouldn't really cause any state changes in
          // PeerConnection, but we need a way to match new transceivers to new
          // media sections in SetLocalDescription and JSEP specifies this is
          // done by recording the index of the media section generated for the
          // transceiver in the offer.
          transceiver->internal()->set_mline_index(i);
        }
      }
    } else if (media_type == cricket::MEDIA_TYPE_UNSUPPORTED) {
      RTC_DCHECK(local_content->rejected);
      session_options->media_description_options.push_back(
          cricket::MediaDescriptionOptions(media_type, mid,
                                           RtpTransceiverDirection::kInactive,
                                           /*stopped=*/true));
    } else {
      RTC_CHECK_EQ(cricket::MEDIA_TYPE_DATA, media_type);
      if (had_been_rejected) {
        session_options->media_description_options.push_back(
            GetMediaDescriptionOptionsForRejectedData(mid));
      } else {
        const auto data_mid = pc_->sctp_mid();
        if (data_mid.has_value() && mid == data_mid.value()) {
          session_options->media_description_options.push_back(
              GetMediaDescriptionOptionsForActiveData(mid));
        } else {
          if (!data_mid.has_value()) {
            RTC_LOG(LS_ERROR) << "Datachannel transport not available: " << mid;
          }
          session_options->media_description_options.push_back(
              GetMediaDescriptionOptionsForRejectedData(mid));
        }
      }
    }
  }

  // Next, look for transceivers that are newly added (that is, are not stopped
  // and not associated). Reuse media sections marked as recyclable first,
  // otherwise append to the end of the offer. New media sections should be
  // added in the order they were added to the PeerConnection.
  if (ConfiguredForMedia()) {
    for (const auto& transceiver : transceivers()->ListInternal()) {
      if (transceiver->mid() || transceiver->stopping()) {
        continue;
      }
      size_t mline_index;
      if (!recycleable_mline_indices.empty()) {
        mline_index = recycleable_mline_indices.front();
        recycleable_mline_indices.pop();
        session_options->media_description_options[mline_index] =
            GetMediaDescriptionOptionsForTransceiver(
                transceiver, mid_generator_.GenerateString(),
                /*is_create_offer=*/true);
      } else {
        mline_index = session_options->media_description_options.size();
        session_options->media_description_options.push_back(
            GetMediaDescriptionOptionsForTransceiver(
                transceiver, mid_generator_.GenerateString(),
                /*is_create_offer=*/true));
      }
      // See comment above for why CreateOffer changes the transceiver's state.
      transceiver->set_mline_index(mline_index);
    }
  }
  // Lastly, add a m-section if we have requested local data channels and an
  // m section does not already exist.
  if (!pc_->sctp_mid() && data_channel_controller()->HasDataChannels()) {
    // Attempt to recycle a stopped m-line.
    // TODO(crbug.com/1442604): sctp_mid() should return the mid if one was
    // ever created but rejected.
    bool recycled = false;
    for (size_t i = 0; i < session_options->media_description_options.size();
         i++) {
      auto media_description = session_options->media_description_options[i];
      if (media_description.type == cricket::MEDIA_TYPE_DATA &&
          media_description.stopped) {
        session_options->media_description_options[i] =
            GetMediaDescriptionOptionsForActiveData(media_description.mid);
        recycled = true;
        break;
      }
    }
    if (!recycled) {
      session_options->media_description_options.push_back(
          GetMediaDescriptionOptionsForActiveData(
              mid_generator_.GenerateString()));
    }
  }
}

void SdpOfferAnswerHandler::GetOptionsForAnswer(
    const RTCOfferAnswerOptions& offer_answer_options,
    cricket::MediaSessionOptions* session_options) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  ExtractSharedMediaSessionOptions(offer_answer_options, session_options);

  if (IsUnifiedPlan()) {
    GetOptionsForUnifiedPlanAnswer(offer_answer_options, session_options);
  } else {
    GetOptionsForPlanBAnswer(offer_answer_options, session_options);
  }

  // Apply ICE renomination flag.
  for (auto& options : session_options->media_description_options) {
    options.transport_options.enable_ice_renomination =
        pc_->configuration()->enable_ice_renomination;
  }

  session_options->rtcp_cname = rtcp_cname_;
  session_options->crypto_options = pc_->GetCryptoOptions();
  session_options->pooled_ice_credentials =
      context_->network_thread()->BlockingCall(
          [this] { return port_allocator()->GetPooledIceCredentials(); });
}

void SdpOfferAnswerHandler::GetOptionsForPlanBAnswer(
    const PeerConnectionInterface::RTCOfferAnswerOptions& offer_answer_options,
    cricket::MediaSessionOptions* session_options) {
  bool send_audio = false;
  bool recv_audio = false;
  bool send_video = false;
  bool recv_video = false;

  if (ConfiguredForMedia()) {
    // Figure out transceiver directional preferences.
    send_audio =
        !rtp_manager()->GetAudioTransceiver()->internal()->senders().empty();
    send_video =
        !rtp_manager()->GetVideoTransceiver()->internal()->senders().empty();

    // By default, generate sendrecv/recvonly m= sections. The direction is also
    // restricted by the direction in the offer.
    recv_audio = true;
    recv_video = true;

    // The "offer_to_receive_X" options allow those defaults to be overridden.
    if (offer_answer_options.offer_to_receive_audio !=
        RTCOfferAnswerOptions::kUndefined) {
      recv_audio = (offer_answer_options.offer_to_receive_audio > 0);
    }
    if (offer_answer_options.offer_to_receive_video !=
        RTCOfferAnswerOptions::kUndefined) {
      recv_video = (offer_answer_options.offer_to_receive_video > 0);
    }
  }

  absl::optional<size_t> audio_index;
  absl::optional<size_t> video_index;
  absl::optional<size_t> data_index;

  // Generate m= sections that match those in the offer.
  // Note that mediasession.cc will handle intersection our preferred
  // direction with the offered direction.
  GenerateMediaDescriptionOptions(
      remote_description(),
      RtpTransceiverDirectionFromSendRecv(send_audio, recv_audio),
      RtpTransceiverDirectionFromSendRecv(send_video, recv_video), &audio_index,
      &video_index, &data_index, session_options);

  cricket::MediaDescriptionOptions* audio_media_description_options =
      !audio_index ? nullptr
                   : &session_options->media_description_options[*audio_index];
  cricket::MediaDescriptionOptions* video_media_description_options =
      !video_index ? nullptr
                   : &session_options->media_description_options[*video_index];

  if (ConfiguredForMedia()) {
    AddPlanBRtpSenderOptions(rtp_manager()->GetSendersInternal(),
                             audio_media_description_options,
                             video_media_description_options,
                             offer_answer_options.num_simulcast_layers);
  }
}

void SdpOfferAnswerHandler::GetOptionsForUnifiedPlanAnswer(
    const PeerConnectionInterface::RTCOfferAnswerOptions& offer_answer_options,
    cricket::MediaSessionOptions* session_options) {
  // Rules for generating an answer are dictated by JSEP sections 5.3.1 (Initial
  // Answers) and 5.3.2 (Subsequent Answers).
  RTC_DCHECK(remote_description());
  RTC_DCHECK(remote_description()->GetType() == SdpType::kOffer);
  for (const ContentInfo& content :
       remote_description()->description()->contents()) {
    cricket::MediaType media_type = content.media_description()->type();
    if (media_type == cricket::MEDIA_TYPE_AUDIO ||
        media_type == cricket::MEDIA_TYPE_VIDEO) {
      auto transceiver = transceivers()->FindByMid(content.name);
      if (transceiver) {
        session_options->media_description_options.push_back(
            GetMediaDescriptionOptionsForTransceiver(
                transceiver->internal(), content.name,
                /*is_create_offer=*/false));
      } else {
        // This should only happen with rejected transceivers.
        RTC_DCHECK(content.rejected);
        session_options->media_description_options.push_back(
            cricket::MediaDescriptionOptions(media_type, content.name,
                                             RtpTransceiverDirection::kInactive,
                                             /*stopped=*/true));
      }
    } else if (media_type == cricket::MEDIA_TYPE_UNSUPPORTED) {
      RTC_DCHECK(content.rejected);
      session_options->media_description_options.push_back(
          cricket::MediaDescriptionOptions(media_type, content.name,
                                           RtpTransceiverDirection::kInactive,
                                           /*stopped=*/true));
    } else {
      RTC_CHECK_EQ(cricket::MEDIA_TYPE_DATA, media_type);
      // Reject all data sections if data channels are disabled.
      // Reject a data section if it has already been rejected.
      // Reject all data sections except for the first one.
      if (content.rejected || content.name != *(pc_->sctp_mid())) {
        session_options->media_description_options.push_back(
            GetMediaDescriptionOptionsForRejectedData(content.name));
      } else {
        session_options->media_description_options.push_back(
            GetMediaDescriptionOptionsForActiveData(content.name));
      }
    }
  }
}

const char* SdpOfferAnswerHandler::SessionErrorToString(
    SessionError error) const {
  switch (error) {
    case SessionError::kNone:
      return "ERROR_NONE";
    case SessionError::kContent:
      return "ERROR_CONTENT";
    case SessionError::kTransport:
      return "ERROR_TRANSPORT";
  }
  RTC_DCHECK_NOTREACHED();
  return "";
}

std::string SdpOfferAnswerHandler::GetSessionErrorMsg() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  rtc::StringBuilder desc;
  desc << kSessionError << SessionErrorToString(session_error()) << ". ";
  desc << kSessionErrorDesc << session_error_desc() << ".";
  return desc.Release();
}

void SdpOfferAnswerHandler::SetSessionError(SessionError error,
                                            const std::string& error_desc) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (error != session_error_) {
    session_error_ = error;
    session_error_desc_ = error_desc;
  }
}

RTCError SdpOfferAnswerHandler::HandleLegacyOfferOptions(
    const PeerConnectionInterface::RTCOfferAnswerOptions& options) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(IsUnifiedPlan());

  if (options.offer_to_receive_audio == 0) {
    RemoveRecvDirectionFromReceivingTransceiversOfType(
        cricket::MEDIA_TYPE_AUDIO);
  } else if (options.offer_to_receive_audio == 1) {
    AddUpToOneReceivingTransceiverOfType(cricket::MEDIA_TYPE_AUDIO);
  } else if (options.offer_to_receive_audio > 1) {
    LOG_AND_RETURN_ERROR(RTCErrorType::UNSUPPORTED_PARAMETER,
                         "offer_to_receive_audio > 1 is not supported.");
  }

  if (options.offer_to_receive_video == 0) {
    RemoveRecvDirectionFromReceivingTransceiversOfType(
        cricket::MEDIA_TYPE_VIDEO);
  } else if (options.offer_to_receive_video == 1) {
    AddUpToOneReceivingTransceiverOfType(cricket::MEDIA_TYPE_VIDEO);
  } else if (options.offer_to_receive_video > 1) {
    LOG_AND_RETURN_ERROR(RTCErrorType::UNSUPPORTED_PARAMETER,
                         "offer_to_receive_video > 1 is not supported.");
  }

  return RTCError::OK();
}

void SdpOfferAnswerHandler::RemoveRecvDirectionFromReceivingTransceiversOfType(
    cricket::MediaType media_type) {
  for (const auto& transceiver : GetReceivingTransceiversOfType(media_type)) {
    RtpTransceiverDirection new_direction =
        RtpTransceiverDirectionWithRecvSet(transceiver->direction(), false);
    if (new_direction != transceiver->direction()) {
      RTC_LOG(LS_INFO) << "Changing " << cricket::MediaTypeToString(media_type)
                       << " transceiver (MID="
                       << transceiver->mid().value_or("<not set>") << ") from "
                       << RtpTransceiverDirectionToString(
                              transceiver->direction())
                       << " to "
                       << RtpTransceiverDirectionToString(new_direction)
                       << " since CreateOffer specified offer_to_receive=0";
      transceiver->internal()->set_direction(new_direction);
    }
  }
}

void SdpOfferAnswerHandler::AddUpToOneReceivingTransceiverOfType(
    cricket::MediaType media_type) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (GetReceivingTransceiversOfType(media_type).empty()) {
    RTC_LOG(LS_INFO)
        << "Adding one recvonly " << cricket::MediaTypeToString(media_type)
        << " transceiver since CreateOffer specified offer_to_receive=1";
    RtpTransceiverInit init;
    init.direction = RtpTransceiverDirection::kRecvOnly;
    pc_->AddTransceiver(media_type, nullptr, init,
                        /*update_negotiation_needed=*/false);
  }
}

std::vector<rtc::scoped_refptr<RtpTransceiverProxyWithInternal<RtpTransceiver>>>
SdpOfferAnswerHandler::GetReceivingTransceiversOfType(
    cricket::MediaType media_type) {
  std::vector<
      rtc::scoped_refptr<RtpTransceiverProxyWithInternal<RtpTransceiver>>>
      receiving_transceivers;
  for (const auto& transceiver : transceivers()->List()) {
    if (!transceiver->stopped() && transceiver->media_type() == media_type &&
        RtpTransceiverDirectionHasRecv(transceiver->direction())) {
      receiving_transceivers.push_back(transceiver);
    }
  }
  return receiving_transceivers;
}

void SdpOfferAnswerHandler::ProcessRemovalOfRemoteTrack(
    rtc::scoped_refptr<RtpTransceiverProxyWithInternal<RtpTransceiver>>
        transceiver,
    std::vector<rtc::scoped_refptr<RtpTransceiverInterface>>* remove_list,
    std::vector<rtc::scoped_refptr<MediaStreamInterface>>* removed_streams) {
  RTC_DCHECK(transceiver->mid());
  RTC_LOG(LS_INFO) << "Processing the removal of a track for MID="
                   << *transceiver->mid();
  std::vector<rtc::scoped_refptr<MediaStreamInterface>> previous_streams =
      transceiver->internal()->receiver_internal()->streams();
  // This will remove the remote track from the streams.
  transceiver->internal()->receiver_internal()->set_stream_ids({});
  remove_list->push_back(transceiver);
  RemoveRemoteStreamsIfEmpty(previous_streams, removed_streams);
}

void SdpOfferAnswerHandler::RemoveRemoteStreamsIfEmpty(
    const std::vector<rtc::scoped_refptr<MediaStreamInterface>>& remote_streams,
    std::vector<rtc::scoped_refptr<MediaStreamInterface>>* removed_streams) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // TODO(https://crbug.com/webrtc/9480): When we use stream IDs instead of
  // streams, see if the stream was removed by checking if this was the last
  // receiver with that stream ID.
  for (const auto& remote_stream : remote_streams) {
    if (remote_stream->GetAudioTracks().empty() &&
        remote_stream->GetVideoTracks().empty()) {
      remote_streams_->RemoveStream(remote_stream.get());
      removed_streams->push_back(remote_stream);
    }
  }
}

void SdpOfferAnswerHandler::RemoveSenders(cricket::MediaType media_type) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  UpdateLocalSenders(std::vector<cricket::StreamParams>(), media_type);
  UpdateRemoteSendersList(std::vector<cricket::StreamParams>(), false,
                          media_type, nullptr);
}

void SdpOfferAnswerHandler::UpdateLocalSenders(
    const std::vector<cricket::StreamParams>& streams,
    cricket::MediaType media_type) {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::UpdateLocalSenders");
  RTC_DCHECK_RUN_ON(signaling_thread());
  std::vector<RtpSenderInfo>* current_senders =
      rtp_manager()->GetLocalSenderInfos(media_type);

  // Find removed tracks. I.e., tracks where the track id, stream id or ssrc
  // don't match the new StreamParam.
  for (auto sender_it = current_senders->begin();
       sender_it != current_senders->end();
       /* incremented manually */) {
    const RtpSenderInfo& info = *sender_it;
    const cricket::StreamParams* params =
        cricket::GetStreamBySsrc(streams, info.first_ssrc);
    if (!params || params->id != info.sender_id ||
        params->first_stream_id() != info.stream_id) {
      rtp_manager()->OnLocalSenderRemoved(info, media_type);
      sender_it = current_senders->erase(sender_it);
    } else {
      ++sender_it;
    }
  }

  // Find new and active senders.
  for (const cricket::StreamParams& params : streams) {
    // The sync_label is the MediaStream label and the `stream.id` is the
    // sender id.
    const std::string& stream_id = params.first_stream_id();
    const std::string& sender_id = params.id;
    uint32_t ssrc = params.first_ssrc();
    const RtpSenderInfo* sender_info =
        rtp_manager()->FindSenderInfo(*current_senders, stream_id, sender_id);
    if (!sender_info) {
      current_senders->push_back(RtpSenderInfo(stream_id, sender_id, ssrc));
      rtp_manager()->OnLocalSenderAdded(current_senders->back(), media_type);
    }
  }
}

void SdpOfferAnswerHandler::UpdateRemoteSendersList(
    const cricket::StreamParamsVec& streams,
    bool default_sender_needed,
    cricket::MediaType media_type,
    StreamCollection* new_streams) {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::UpdateRemoteSendersList");
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(!IsUnifiedPlan());

  std::vector<RtpSenderInfo>* current_senders =
      rtp_manager()->GetRemoteSenderInfos(media_type);

  // Find removed senders. I.e., senders where the sender id or ssrc don't match
  // the new StreamParam.
  for (auto sender_it = current_senders->begin();
       sender_it != current_senders->end();
       /* incremented manually */) {
    const RtpSenderInfo& info = *sender_it;
    const cricket::StreamParams* params =
        cricket::GetStreamBySsrc(streams, info.first_ssrc);
    std::string params_stream_id;
    if (params) {
      params_stream_id =
          (!params->first_stream_id().empty() ? params->first_stream_id()
                                              : kDefaultStreamId);
    }
    bool sender_exists = params && params->id == info.sender_id &&
                         params_stream_id == info.stream_id;
    // If this is a default track, and we still need it, don't remove it.
    if ((info.stream_id == kDefaultStreamId && default_sender_needed) ||
        sender_exists) {
      ++sender_it;
    } else {
      rtp_manager()->OnRemoteSenderRemoved(
          info, remote_streams_->find(info.stream_id), media_type);
      sender_it = current_senders->erase(sender_it);
    }
  }

  // Find new and active senders.
  for (const cricket::StreamParams& params : streams) {
    if (!params.has_ssrcs()) {
      // The remote endpoint has streams, but didn't signal ssrcs. For an active
      // sender, this means it is coming from a Unified Plan endpoint,so we just
      // create a default.
      default_sender_needed = true;
      break;
    }

    // `params.id` is the sender id and the stream id uses the first of
    // `params.stream_ids`. The remote description could come from a Unified
    // Plan endpoint, with multiple or no stream_ids() signaled. Since this is
    // not supported in Plan B, we just take the first here and create the
    // default stream ID if none is specified.
    const std::string& stream_id =
        (!params.first_stream_id().empty() ? params.first_stream_id()
                                           : kDefaultStreamId);
    const std::string& sender_id = params.id;
    uint32_t ssrc = params.first_ssrc();

    rtc::scoped_refptr<MediaStreamInterface> stream(
        remote_streams_->find(stream_id));
    if (!stream) {
      // This is a new MediaStream. Create a new remote MediaStream.
      stream = MediaStreamProxy::Create(rtc::Thread::Current(),
                                        MediaStream::Create(stream_id));
      remote_streams_->AddStream(stream);
      new_streams->AddStream(stream);
    }

    const RtpSenderInfo* sender_info =
        rtp_manager()->FindSenderInfo(*current_senders, stream_id, sender_id);
    if (!sender_info) {
      current_senders->push_back(RtpSenderInfo(stream_id, sender_id, ssrc));
      rtp_manager()->OnRemoteSenderAdded(current_senders->back(), stream.get(),
                                         media_type);
    }
  }

  // Add default sender if necessary.
  if (default_sender_needed) {
    rtc::scoped_refptr<MediaStreamInterface> default_stream(
        remote_streams_->find(kDefaultStreamId));
    if (!default_stream) {
      // Create the new default MediaStream.
      default_stream = MediaStreamProxy::Create(
          rtc::Thread::Current(), MediaStream::Create(kDefaultStreamId));
      remote_streams_->AddStream(default_stream);
      new_streams->AddStream(default_stream);
    }
    std::string default_sender_id = (media_type == cricket::MEDIA_TYPE_AUDIO)
                                        ? kDefaultAudioSenderId
                                        : kDefaultVideoSenderId;
    const RtpSenderInfo* default_sender_info = rtp_manager()->FindSenderInfo(
        *current_senders, kDefaultStreamId, default_sender_id);
    if (!default_sender_info) {
      current_senders->push_back(
          RtpSenderInfo(kDefaultStreamId, default_sender_id, /*ssrc=*/0));
      rtp_manager()->OnRemoteSenderAdded(current_senders->back(),
                                         default_stream.get(), media_type);
    }
  }
}

void SdpOfferAnswerHandler::EnableSending() {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::EnableSending");
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (!ConfiguredForMedia()) {
    return;
  }
  for (const auto& transceiver : transceivers()->ListInternal()) {
    cricket::ChannelInterface* channel = transceiver->channel();
    if (channel) {
      channel->Enable(true);
    }
  }
}

RTCError SdpOfferAnswerHandler::PushdownMediaDescription(
    SdpType type,
    cricket::ContentSource source,
    const std::map<std::string, const cricket::ContentGroup*>&
        bundle_groups_by_mid) {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::PushdownMediaDescription");
  const SessionDescriptionInterface* sdesc =
      (source == cricket::CS_LOCAL ? local_description()
                                   : remote_description());
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(sdesc);

  if (ConfiguredForMedia()) {
    // Note: This will perform a BlockingCall over to the worker thread, which
    // we'll also do in a loop below.
    if (!UpdatePayloadTypeDemuxingState(source, bundle_groups_by_mid)) {
      // Note that this is never expected to fail, since RtpDemuxer doesn't
      // return an error when changing payload type demux criteria, which is all
      // this does.
      LOG_AND_RETURN_ERROR(RTCErrorType::INTERNAL_ERROR,
                           "Failed to update payload type demuxing state.");
    }

    // Push down the new SDP media section for each audio/video transceiver.
    auto rtp_transceivers = transceivers()->ListInternal();
    std::vector<
        std::pair<cricket::ChannelInterface*, const MediaContentDescription*>>
        channels;
    for (const auto& transceiver : rtp_transceivers) {
      const ContentInfo* content_info =
          FindMediaSectionForTransceiver(transceiver, sdesc);
      cricket::ChannelInterface* channel = transceiver->channel();
      if (!channel || !content_info || content_info->rejected) {
        continue;
      }
      const MediaContentDescription* content_desc =
          content_info->media_description();
      if (!content_desc) {
        continue;
      }

      transceiver->OnNegotiationUpdate(type, content_desc);
      channels.push_back(std::make_pair(channel, content_desc));
    }

    // This for-loop of invokes helps audio impairment during re-negotiations.
    // One of the causes is that downstairs decoder creation is synchronous at
    // the moment, and that a decoder is created for each codec listed in the
    // SDP.
    //
    // TODO(bugs.webrtc.org/12840): consider merging the invokes again after
    // these projects have shipped:
    // - bugs.webrtc.org/12462
    // - crbug.com/1157227
    // - crbug.com/1187289
    for (const auto& entry : channels) {
      std::string error;
      bool success = context_->worker_thread()->BlockingCall([&]() {
        return (source == cricket::CS_LOCAL)
                   ? entry.first->SetLocalContent(entry.second, type, error)
                   : entry.first->SetRemoteContent(entry.second, type, error);
      });
      if (!success) {
        LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER, error);
      }
    }
  }
  // Need complete offer/answer with an SCTP m= section before starting SCTP,
  // according to https://tools.ietf.org/html/draft-ietf-mmusic-sctp-sdp-19
  if (pc_->sctp_mid() && local_description() && remote_description()) {
    auto local_sctp_description = cricket::GetFirstSctpDataContentDescription(
        local_description()->description());
    auto remote_sctp_description = cricket::GetFirstSctpDataContentDescription(
        remote_description()->description());
    if (local_sctp_description && remote_sctp_description) {
      int max_message_size;
      // A remote max message size of zero means "any size supported".
      // We configure the connection with our own max message size.
      if (remote_sctp_description->max_message_size() == 0) {
        max_message_size = local_sctp_description->max_message_size();
      } else {
        max_message_size =
            std::min(local_sctp_description->max_message_size(),
                     remote_sctp_description->max_message_size());
      }
      pc_->StartSctpTransport(local_sctp_description->port(),
                              remote_sctp_description->port(),
                              max_message_size);
    }
  }

  return RTCError::OK();
}

RTCError SdpOfferAnswerHandler::PushdownTransportDescription(
    cricket::ContentSource source,
    SdpType type) {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::PushdownTransportDescription");
  RTC_DCHECK_RUN_ON(signaling_thread());

  if (source == cricket::CS_LOCAL) {
    const SessionDescriptionInterface* sdesc = local_description();
    RTC_DCHECK(sdesc);
    const auto* remote = remote_description();
    return transport_controller_s()->SetLocalDescription(
        type, sdesc->description(), remote ? remote->description() : nullptr);
  } else {
    const SessionDescriptionInterface* sdesc = remote_description();
    RTC_DCHECK(sdesc);
    const auto* local = local_description();
    return transport_controller_s()->SetRemoteDescription(
        type, local ? local->description() : nullptr, sdesc->description());
  }
}

void SdpOfferAnswerHandler::RemoveStoppedTransceivers() {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::RemoveStoppedTransceivers");
  RTC_DCHECK_RUN_ON(signaling_thread());
  // 3.2.10.1: For each transceiver in the connection's set of transceivers
  //           run the following steps:
  if (!IsUnifiedPlan())
    return;
  if (!ConfiguredForMedia()) {
    return;
  }
  // Traverse a copy of the transceiver list.
  auto transceiver_list = transceivers()->List();
  for (auto transceiver : transceiver_list) {
    // 3.2.10.1.1: If transceiver is stopped, associated with an m= section
    //             and the associated m= section is rejected in
    //             connection.[[CurrentLocalDescription]] or
    //             connection.[[CurrentRemoteDescription]], remove the
    //             transceiver from the connection's set of transceivers.
    if (!transceiver->stopped()) {
      continue;
    }
    const ContentInfo* local_content = FindMediaSectionForTransceiver(
        transceiver->internal(), local_description());
    const ContentInfo* remote_content = FindMediaSectionForTransceiver(
        transceiver->internal(), remote_description());
    if ((local_content && local_content->rejected) ||
        (remote_content && remote_content->rejected)) {
      RTC_LOG(LS_INFO) << "Dissociating transceiver"
                          " since the media section is being recycled.";
      transceiver->internal()->set_mid(absl::nullopt);
      transceiver->internal()->set_mline_index(absl::nullopt);
    } else if (!local_content && !remote_content) {
      // TODO(bugs.webrtc.org/11973): Consider if this should be removed already
      // See https://github.com/w3c/webrtc-pc/issues/2576
      RTC_LOG(LS_INFO)
          << "Dropping stopped transceiver that was never associated";
    }
    transceivers()->Remove(transceiver);
  }
}

void SdpOfferAnswerHandler::RemoveUnusedChannels(
    const SessionDescription* desc) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (ConfiguredForMedia()) {
    // Destroy video channel first since it may have a pointer to the
    // voice channel.
    const cricket::ContentInfo* video_info =
        cricket::GetFirstVideoContent(desc);
    if (!video_info || video_info->rejected) {
      rtp_manager()->GetVideoTransceiver()->internal()->ClearChannel();
    }

    const cricket::ContentInfo* audio_info =
        cricket::GetFirstAudioContent(desc);
    if (!audio_info || audio_info->rejected) {
      rtp_manager()->GetAudioTransceiver()->internal()->ClearChannel();
    }
  }
  const cricket::ContentInfo* data_info = cricket::GetFirstDataContent(desc);
  if (!data_info) {
    RTCError error(RTCErrorType::OPERATION_ERROR_WITH_DATA,
                   "No data channel section in the description.");
    error.set_error_detail(RTCErrorDetailType::DATA_CHANNEL_FAILURE);
    pc_->DestroyDataChannelTransport(error);
  } else if (data_info->rejected) {
    rtc::StringBuilder sb;
    sb << "Rejected data channel with mid=" << data_info->name << ".";

    RTCError error(RTCErrorType::OPERATION_ERROR_WITH_DATA, sb.Release());
    error.set_error_detail(RTCErrorDetailType::DATA_CHANNEL_FAILURE);
    pc_->DestroyDataChannelTransport(error);
  }
}

void SdpOfferAnswerHandler::UpdateEndedRemoteMediaStreams() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  std::vector<rtc::scoped_refptr<MediaStreamInterface>> streams_to_remove;
  for (size_t i = 0; i < remote_streams_->count(); ++i) {
    MediaStreamInterface* stream = remote_streams_->at(i);
    if (stream->GetAudioTracks().empty() && stream->GetVideoTracks().empty()) {
      streams_to_remove.push_back(
          rtc::scoped_refptr<MediaStreamInterface>(stream));
    }
  }

  for (auto& stream : streams_to_remove) {
    remote_streams_->RemoveStream(stream.get());
    pc_->Observer()->OnRemoveStream(std::move(stream));
  }
}

bool SdpOfferAnswerHandler::UseCandidatesInRemoteDescription() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  auto* remote_desc = remote_description();
  if (!remote_desc) {
    return true;
  }
  bool ret = true;

  for (size_t m = 0; m < remote_desc->number_of_mediasections(); ++m) {
    const IceCandidateCollection* candidates = remote_desc->candidates(m);
    for (size_t n = 0; n < candidates->count(); ++n) {
      const IceCandidateInterface* candidate = candidates->at(n);
      bool valid = false;
      if (!ReadyToUseRemoteCandidate(candidate, remote_desc, &valid)) {
        if (valid) {
          RTC_LOG(LS_INFO)
              << "UseCandidatesInRemoteDescription: Not ready to use "
                 "candidate.";
        }
        continue;
      }
      ret = UseCandidate(candidate);
      if (!ret) {
        break;
      }
    }
  }
  return ret;
}

bool SdpOfferAnswerHandler::UseCandidate(
    const IceCandidateInterface* candidate) {
  RTC_DCHECK_RUN_ON(signaling_thread());

  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;

  RTCErrorOr<const cricket::ContentInfo*> result =
      FindContentInfo(remote_description(), candidate);
  if (!result.ok())
    return false;

  const cricket::Candidate& c = candidate->candidate();
  RTCError error = cricket::VerifyCandidate(c);
  if (!error.ok()) {
    RTC_LOG(LS_WARNING) << "Invalid candidate: " << c.ToString();
    return true;
  }

  pc_->AddRemoteCandidate(result.value()->name, c);

  return true;
}

// We need to check the local/remote description for the Transport instead of
// the session, because a new Transport added during renegotiation may have
// them unset while the session has them set from the previous negotiation.
// Not doing so may trigger the auto generation of transport description and
// mess up DTLS identity information, ICE credential, etc.
bool SdpOfferAnswerHandler::ReadyToUseRemoteCandidate(
    const IceCandidateInterface* candidate,
    const SessionDescriptionInterface* remote_desc,
    bool* valid) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  *valid = true;

  const SessionDescriptionInterface* current_remote_desc =
      remote_desc ? remote_desc : remote_description();

  if (!current_remote_desc) {
    return false;
  }

  RTCErrorOr<const cricket::ContentInfo*> result =
      FindContentInfo(current_remote_desc, candidate);
  if (!result.ok()) {
    RTC_LOG(LS_ERROR) << "ReadyToUseRemoteCandidate: Invalid candidate. "
                      << result.error().message();

    *valid = false;
    return false;
  }

  return true;
}

RTCErrorOr<const cricket::ContentInfo*> SdpOfferAnswerHandler::FindContentInfo(
    const SessionDescriptionInterface* description,
    const IceCandidateInterface* candidate) {
  if (!candidate->sdp_mid().empty()) {
    auto& contents = description->description()->contents();
    auto it = absl::c_find_if(
        contents, [candidate](const cricket::ContentInfo& content_info) {
          return content_info.mid() == candidate->sdp_mid();
        });
    if (it == contents.end()) {
      LOG_AND_RETURN_ERROR(
          RTCErrorType::INVALID_PARAMETER,
          "Mid " + candidate->sdp_mid() +
              " specified but no media section with that mid found.");
    } else {
      return &*it;
    }
  } else if (candidate->sdp_mline_index() >= 0) {
    size_t mediacontent_index =
        static_cast<size_t>(candidate->sdp_mline_index());
    size_t content_size = description->description()->contents().size();
    if (mediacontent_index < content_size) {
      return &description->description()->contents()[mediacontent_index];
    } else {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_RANGE,
                           "Media line index (" +
                               rtc::ToString(candidate->sdp_mline_index()) +
                               ") out of range (number of mlines: " +
                               rtc::ToString(content_size) + ").");
    }
  }

  LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                       "Neither sdp_mline_index nor sdp_mid specified.");
}

RTCError SdpOfferAnswerHandler::CreateChannels(const SessionDescription& desc) {
  TRACE_EVENT0("webrtc", "SdpOfferAnswerHandler::CreateChannels");
  // Creating the media channels. Transports should already have been created
  // at this point.
  RTC_DCHECK_RUN_ON(signaling_thread());
  const cricket::ContentInfo* voice = cricket::GetFirstAudioContent(&desc);
  if (voice && !voice->rejected &&
      !rtp_manager()->GetAudioTransceiver()->internal()->channel()) {
    auto error =
        rtp_manager()->GetAudioTransceiver()->internal()->CreateChannel(
            voice->name, pc_->call_ptr(), pc_->configuration()->media_config,
            pc_->SrtpRequired(), pc_->GetCryptoOptions(), audio_options(),
            video_options(), video_bitrate_allocator_factory_.get(),
            [&](absl::string_view mid) {
              RTC_DCHECK_RUN_ON(network_thread());
              return transport_controller_n()->GetRtpTransport(mid);
            });
    if (!error.ok()) {
      return error;
    }
  }

  const cricket::ContentInfo* video = cricket::GetFirstVideoContent(&desc);
  if (video && !video->rejected &&
      !rtp_manager()->GetVideoTransceiver()->internal()->channel()) {
    auto error =
        rtp_manager()->GetVideoTransceiver()->internal()->CreateChannel(
            video->name, pc_->call_ptr(), pc_->configuration()->media_config,
            pc_->SrtpRequired(), pc_->GetCryptoOptions(),

            audio_options(), video_options(),
            video_bitrate_allocator_factory_.get(), [&](absl::string_view mid) {
              RTC_DCHECK_RUN_ON(network_thread());
              return transport_controller_n()->GetRtpTransport(mid);
            });
    if (!error.ok()) {
      return error;
    }
  }

  const cricket::ContentInfo* data = cricket::GetFirstDataContent(&desc);
  if (data && !data->rejected && !pc_->CreateDataChannelTransport(data->name)) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INTERNAL_ERROR,
                         "Failed to create data channel.");
  }

  return RTCError::OK();
}

void SdpOfferAnswerHandler::DestroyMediaChannels() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (!transceivers()) {
    return;
  }

  RTC_LOG_THREAD_BLOCK_COUNT();

  // Destroy video channels first since they may have a pointer to a voice
  // channel.
  auto list = transceivers()->List();
  RTC_DCHECK_BLOCK_COUNT_NO_MORE_THAN(0);

  for (const auto& transceiver : list) {
    if (transceiver->media_type() == cricket::MEDIA_TYPE_VIDEO) {
      transceiver->internal()->ClearChannel();
    }
  }
  for (const auto& transceiver : list) {
    if (transceiver->media_type() == cricket::MEDIA_TYPE_AUDIO) {
      transceiver->internal()->ClearChannel();
    }
  }
}

void SdpOfferAnswerHandler::GenerateMediaDescriptionOptions(
    const SessionDescriptionInterface* session_desc,
    RtpTransceiverDirection audio_direction,
    RtpTransceiverDirection video_direction,
    absl::optional<size_t>* audio_index,
    absl::optional<size_t>* video_index,
    absl::optional<size_t>* data_index,
    cricket::MediaSessionOptions* session_options) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  for (const cricket::ContentInfo& content :
       session_desc->description()->contents()) {
    if (IsAudioContent(&content)) {
      // If we already have an audio m= section, reject this extra one.
      if (*audio_index) {
        session_options->media_description_options.push_back(
            cricket::MediaDescriptionOptions(
                cricket::MEDIA_TYPE_AUDIO, content.name,
                RtpTransceiverDirection::kInactive, /*stopped=*/true));
      } else {
        bool stopped = (audio_direction == RtpTransceiverDirection::kInactive);
        session_options->media_description_options.push_back(
            cricket::MediaDescriptionOptions(cricket::MEDIA_TYPE_AUDIO,
                                             content.name, audio_direction,
                                             stopped));
        *audio_index = session_options->media_description_options.size() - 1;
      }
      session_options->media_description_options.back().header_extensions =
          media_engine()->voice().GetRtpHeaderExtensions();
    } else if (IsVideoContent(&content)) {
      // If we already have an video m= section, reject this extra one.
      if (*video_index) {
        session_options->media_description_options.push_back(
            cricket::MediaDescriptionOptions(
                cricket::MEDIA_TYPE_VIDEO, content.name,
                RtpTransceiverDirection::kInactive, /*stopped=*/true));
      } else {
        bool stopped = (video_direction == RtpTransceiverDirection::kInactive);
        session_options->media_description_options.push_back(
            cricket::MediaDescriptionOptions(cricket::MEDIA_TYPE_VIDEO,
                                             content.name, video_direction,
                                             stopped));
        *video_index = session_options->media_description_options.size() - 1;
      }
      session_options->media_description_options.back().header_extensions =
          media_engine()->video().GetRtpHeaderExtensions();
    } else if (IsUnsupportedContent(&content)) {
      session_options->media_description_options.push_back(
          cricket::MediaDescriptionOptions(cricket::MEDIA_TYPE_UNSUPPORTED,
                                           content.name,
                                           RtpTransceiverDirection::kInactive,
                                           /*stopped=*/true));
    } else {
      RTC_DCHECK(IsDataContent(&content));
      // If we already have an data m= section, reject this extra one.
      if (*data_index) {
        session_options->media_description_options.push_back(
            GetMediaDescriptionOptionsForRejectedData(content.name));
      } else {
        session_options->media_description_options.push_back(
            GetMediaDescriptionOptionsForActiveData(content.name));
        *data_index = session_options->media_description_options.size() - 1;
      }
    }
  }
}

cricket::MediaDescriptionOptions
SdpOfferAnswerHandler::GetMediaDescriptionOptionsForActiveData(
    const std::string& mid) const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // Direction for data sections is meaningless, but legacy endpoints might
  // expect sendrecv.
  cricket::MediaDescriptionOptions options(cricket::MEDIA_TYPE_DATA, mid,
                                           RtpTransceiverDirection::kSendRecv,
                                           /*stopped=*/false);
  return options;
}

cricket::MediaDescriptionOptions
SdpOfferAnswerHandler::GetMediaDescriptionOptionsForRejectedData(
    const std::string& mid) const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  cricket::MediaDescriptionOptions options(cricket::MEDIA_TYPE_DATA, mid,
                                           RtpTransceiverDirection::kInactive,
                                           /*stopped=*/true);
  return options;
}

bool SdpOfferAnswerHandler::UpdatePayloadTypeDemuxingState(
    cricket::ContentSource source,
    const std::map<std::string, const cricket::ContentGroup*>&
        bundle_groups_by_mid) {
  TRACE_EVENT0("webrtc",
               "SdpOfferAnswerHandler::UpdatePayloadTypeDemuxingState");
  RTC_DCHECK_RUN_ON(signaling_thread());
  // We may need to delete any created default streams and disable creation of
  // new ones on the basis of payload type. This is needed to avoid SSRC
  // collisions in Call's RtpDemuxer, in the case that a transceiver has
  // created a default stream, and then some other channel gets the SSRC
  // signaled in the corresponding Unified Plan "m=" section. Specifically, we
  // need to disable payload type based demuxing when two bundled "m=" sections
  // are using the same payload type(s). For more context
  // see https://bugs.chromium.org/p/webrtc/issues/detail?id=11477
  const SessionDescriptionInterface* sdesc =
      (source == cricket::CS_LOCAL ? local_description()
                                   : remote_description());
  struct PayloadTypes {
    std::set<int> audio_payload_types;
    std::set<int> video_payload_types;
    bool pt_demuxing_possible_audio = true;
    bool pt_demuxing_possible_video = true;
  };
  std::map<const cricket::ContentGroup*, PayloadTypes> payload_types_by_bundle;
  // If the MID is missing from *any* receiving m= section, this is set to true.
  bool mid_header_extension_missing_audio = false;
  bool mid_header_extension_missing_video = false;
  for (auto& content_info : sdesc->description()->contents()) {
    auto it = bundle_groups_by_mid.find(content_info.name);
    const cricket::ContentGroup* bundle_group =
        it != bundle_groups_by_mid.end() ? it->second : nullptr;
    // If this m= section isn't bundled, it's safe to demux by payload type
    // since other m= sections using the same payload type will also be using
    // different transports.
    if (!bundle_group) {
      continue;
    }
    PayloadTypes* payload_types = &payload_types_by_bundle[bundle_group];
    if (content_info.rejected ||
        (source == cricket::ContentSource::CS_LOCAL &&
         !RtpTransceiverDirectionHasRecv(
             content_info.media_description()->direction())) ||
        (source == cricket::ContentSource::CS_REMOTE &&
         !RtpTransceiverDirectionHasSend(
             content_info.media_description()->direction()))) {
      // Ignore transceivers that are not receiving.
      continue;
    }
    const cricket::MediaType media_type =
        content_info.media_description()->type();
    if (media_type == cricket::MediaType::MEDIA_TYPE_AUDIO ||
        media_type == cricket::MediaType::MEDIA_TYPE_VIDEO) {
      if (media_type == cricket::MediaType::MEDIA_TYPE_AUDIO &&
          !mid_header_extension_missing_audio) {
        mid_header_extension_missing_audio =
            !ContentHasHeaderExtension(content_info, RtpExtension::kMidUri);
      } else if (media_type == cricket::MEDIA_TYPE_VIDEO &&
                 !mid_header_extension_missing_video) {
        mid_header_extension_missing_video =
            !ContentHasHeaderExtension(content_info, RtpExtension::kMidUri);
      }
      const cricket::MediaContentDescription* media_desc =
          content_info.media_description();
      for (const cricket::Codec& codec : media_desc->codecs()) {
        if (media_type == cricket::MediaType::MEDIA_TYPE_AUDIO) {
          if (payload_types->audio_payload_types.count(codec.id)) {
            // Two m= sections are using the same payload type, thus demuxing
            // by payload type is not possible.
            if (media_type == cricket::MediaType::MEDIA_TYPE_AUDIO) {
              payload_types->pt_demuxing_possible_audio = false;
            }
          }
          payload_types->audio_payload_types.insert(codec.id);
        } else if (media_type == cricket::MEDIA_TYPE_VIDEO) {
          if (payload_types->video_payload_types.count(codec.id)) {
            // Two m= sections are using the same payload type, thus demuxing
            // by payload type is not possible.
            payload_types->pt_demuxing_possible_video = false;
          }
          payload_types->video_payload_types.insert(codec.id);
        }
      }
    }
  }

  // In Unified Plan, payload type demuxing is useful for legacy endpoints that
  // don't support the MID header extension, but it can also cause incorrrect
  // forwarding of packets when going from one m= section to multiple m=
  // sections in the same BUNDLE. This only happens if media arrives prior to
  // negotiation, but this can cause missing video and unsignalled ssrc bugs
  // severe enough to warrant disabling PT demuxing in such cases. Therefore, if
  // a MID header extension is present on all m= sections for a given kind
  // (audio/video) then we use that as an OK to disable payload type demuxing in
  // BUNDLEs of that kind. However if PT demuxing was ever turned on (e.g. MID
  // was ever removed on ANY m= section of that kind) then we continue to allow
  // PT demuxing in order to prevent disabling it in follow-up O/A exchanges and
  // allowing early media by PT.
  bool bundled_pt_demux_allowed_audio = !IsUnifiedPlan() ||
                                        mid_header_extension_missing_audio ||
                                        pt_demuxing_has_been_used_audio_;
  bool bundled_pt_demux_allowed_video = !IsUnifiedPlan() ||
                                        mid_header_extension_missing_video ||
                                        pt_demuxing_has_been_used_video_;

  // Gather all updates ahead of time so that all channels can be updated in a
  // single BlockingCall; necessary due to thread guards.
  std::vector<std::pair<bool, cricket::ChannelInterface*>> channels_to_update;
  for (const auto& transceiver : transceivers()->ListInternal()) {
    cricket::ChannelInterface* channel = transceiver->channel();
    const ContentInfo* content =
        FindMediaSectionForTransceiver(transceiver, sdesc);
    if (!channel || !content) {
      continue;
    }

    const cricket::MediaType media_type = channel->media_type();
    if (media_type != cricket::MediaType::MEDIA_TYPE_AUDIO &&
        media_type != cricket::MediaType::MEDIA_TYPE_VIDEO) {
      continue;
    }

    RtpTransceiverDirection local_direction =
        content->media_description()->direction();
    if (source == cricket::CS_REMOTE) {
      local_direction = RtpTransceiverDirectionReversed(local_direction);
    }

    auto bundle_it = bundle_groups_by_mid.find(channel->mid());
    const cricket::ContentGroup* bundle_group =
        bundle_it != bundle_groups_by_mid.end() ? bundle_it->second : nullptr;
    bool pt_demux_enabled = RtpTransceiverDirectionHasRecv(local_direction);
    if (media_type == cricket::MediaType::MEDIA_TYPE_AUDIO) {
      pt_demux_enabled &=
          !bundle_group ||
          (bundled_pt_demux_allowed_audio &&
           payload_types_by_bundle[bundle_group].pt_demuxing_possible_audio);
      if (pt_demux_enabled) {
        pt_demuxing_has_been_used_audio_ = true;
      }
    } else {
      RTC_DCHECK_EQ(media_type, cricket::MediaType::MEDIA_TYPE_VIDEO);
      pt_demux_enabled &=
          !bundle_group ||
          (bundled_pt_demux_allowed_video &&
           payload_types_by_bundle[bundle_group].pt_demuxing_possible_video);
      if (pt_demux_enabled) {
        pt_demuxing_has_been_used_video_ = true;
      }
    }

    channels_to_update.emplace_back(pt_demux_enabled, transceiver->channel());
  }

  if (channels_to_update.empty()) {
    return true;
  }

  // TODO(bugs.webrtc.org/11993): This BlockingCall() will also block on the
  // network thread for every demuxer sink that needs to be updated. The demuxer
  // state needs to be fully (and only) managed on the network thread and once
  // that's the case, there's no need to stop by on the worker. Ideally we could
  // also do this without blocking.
  return context_->worker_thread()->BlockingCall([&channels_to_update]() {
    for (const auto& it : channels_to_update) {
      if (!it.second->SetPayloadTypeDemuxingEnabled(it.first)) {
        // Note that the state has already been irrevocably changed at this
        // point. Is it useful to stop the loop?
        return false;
      }
    }
    return true;
  });
}

bool SdpOfferAnswerHandler::ConfiguredForMedia() const {
  return context_->media_engine();
}

}  // namespace webrtc

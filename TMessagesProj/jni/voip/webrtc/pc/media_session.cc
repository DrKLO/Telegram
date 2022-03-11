/*
 *  Copyright 2004 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/media_session.h"

#include <stddef.h>

#include <algorithm>
#include <map>
#include <string>
#include <unordered_map>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/strings/match.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/crypto_params.h"
#include "media/base/codec.h"
#include "media/base/media_constants.h"
#include "media/base/sdp_video_format_utils.h"
#include "media/sctp/sctp_transport_internal.h"
#include "p2p/base/p2p_constants.h"
#include "pc/channel_manager.h"
#include "pc/media_protocol_names.h"
#include "pc/rtp_media_utils.h"
#include "pc/used_ids.h"
#include "rtc_base/checks.h"
#include "rtc_base/helpers.h"
#include "rtc_base/logging.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/third_party/base64/base64.h"
#include "rtc_base/unique_id_generator.h"
#include "system_wrappers/include/field_trial.h"

namespace {

using rtc::UniqueRandomIdGenerator;
using webrtc::RtpTransceiverDirection;

const char kInline[] = "inline:";

void GetSupportedSdesCryptoSuiteNames(
    void (*func)(const webrtc::CryptoOptions&, std::vector<int>*),
    const webrtc::CryptoOptions& crypto_options,
    std::vector<std::string>* names) {
  std::vector<int> crypto_suites;
  func(crypto_options, &crypto_suites);
  for (const auto crypto : crypto_suites) {
    names->push_back(rtc::SrtpCryptoSuiteToName(crypto));
  }
}

webrtc::RtpExtension RtpExtensionFromCapability(
    const webrtc::RtpHeaderExtensionCapability& capability) {
  return webrtc::RtpExtension(capability.uri,
                              capability.preferred_id.value_or(1));
}

cricket::RtpHeaderExtensions RtpHeaderExtensionsFromCapabilities(
    const std::vector<webrtc::RtpHeaderExtensionCapability>& capabilities) {
  cricket::RtpHeaderExtensions exts;
  for (const auto& capability : capabilities) {
    exts.push_back(RtpExtensionFromCapability(capability));
  }
  return exts;
}

std::vector<webrtc::RtpHeaderExtensionCapability>
UnstoppedRtpHeaderExtensionCapabilities(
    std::vector<webrtc::RtpHeaderExtensionCapability> capabilities) {
  capabilities.erase(
      std::remove_if(
          capabilities.begin(), capabilities.end(),
          [](const webrtc::RtpHeaderExtensionCapability& capability) {
            return capability.direction == RtpTransceiverDirection::kStopped;
          }),
      capabilities.end());
  return capabilities;
}

bool IsCapabilityPresent(const webrtc::RtpHeaderExtensionCapability& capability,
                         const cricket::RtpHeaderExtensions& extensions) {
  return std::find_if(extensions.begin(), extensions.end(),
                      [&capability](const webrtc::RtpExtension& extension) {
                        return capability.uri == extension.uri;
                      }) != extensions.end();
}

cricket::RtpHeaderExtensions UnstoppedOrPresentRtpHeaderExtensions(
    const std::vector<webrtc::RtpHeaderExtensionCapability>& capabilities,
    const cricket::RtpHeaderExtensions& unencrypted,
    const cricket::RtpHeaderExtensions& encrypted) {
  cricket::RtpHeaderExtensions extensions;
  for (const auto& capability : capabilities) {
    if (capability.direction != RtpTransceiverDirection::kStopped ||
        IsCapabilityPresent(capability, unencrypted) ||
        IsCapabilityPresent(capability, encrypted)) {
      extensions.push_back(RtpExtensionFromCapability(capability));
    }
  }
  return extensions;
}

}  // namespace

namespace cricket {

static RtpTransceiverDirection NegotiateRtpTransceiverDirection(
    RtpTransceiverDirection offer,
    RtpTransceiverDirection wants) {
  bool offer_send = webrtc::RtpTransceiverDirectionHasSend(offer);
  bool offer_recv = webrtc::RtpTransceiverDirectionHasRecv(offer);
  bool wants_send = webrtc::RtpTransceiverDirectionHasSend(wants);
  bool wants_recv = webrtc::RtpTransceiverDirectionHasRecv(wants);
  return webrtc::RtpTransceiverDirectionFromSendRecv(offer_recv && wants_send,
                                                     offer_send && wants_recv);
}

static bool IsMediaContentOfType(const ContentInfo* content,
                                 MediaType media_type) {
  if (!content || !content->media_description()) {
    return false;
  }
  return content->media_description()->type() == media_type;
}

static bool CreateCryptoParams(int tag,
                               const std::string& cipher,
                               CryptoParams* crypto_out) {
  int key_len;
  int salt_len;
  if (!rtc::GetSrtpKeyAndSaltLengths(rtc::SrtpCryptoSuiteFromName(cipher),
                                     &key_len, &salt_len)) {
    return false;
  }

  int master_key_len = key_len + salt_len;
  std::string master_key;
  if (!rtc::CreateRandomData(master_key_len, &master_key)) {
    return false;
  }

  RTC_CHECK_EQ(master_key_len, master_key.size());
  std::string key = rtc::Base64::Encode(master_key);

  crypto_out->tag = tag;
  crypto_out->cipher_suite = cipher;
  crypto_out->key_params = kInline;
  crypto_out->key_params += key;
  return true;
}

static bool AddCryptoParams(const std::string& cipher_suite,
                            CryptoParamsVec* cryptos_out) {
  int size = static_cast<int>(cryptos_out->size());

  cryptos_out->resize(size + 1);
  return CreateCryptoParams(size, cipher_suite, &cryptos_out->at(size));
}

void AddMediaCryptos(const CryptoParamsVec& cryptos,
                     MediaContentDescription* media) {
  for (const CryptoParams& crypto : cryptos) {
    media->AddCrypto(crypto);
  }
}

bool CreateMediaCryptos(const std::vector<std::string>& crypto_suites,
                        MediaContentDescription* media) {
  CryptoParamsVec cryptos;
  for (const std::string& crypto_suite : crypto_suites) {
    if (!AddCryptoParams(crypto_suite, &cryptos)) {
      return false;
    }
  }
  AddMediaCryptos(cryptos, media);
  return true;
}

const CryptoParamsVec* GetCryptos(const ContentInfo* content) {
  if (!content || !content->media_description()) {
    return nullptr;
  }
  return &content->media_description()->cryptos();
}

bool FindMatchingCrypto(const CryptoParamsVec& cryptos,
                        const CryptoParams& crypto,
                        CryptoParams* crypto_out) {
  auto it = absl::c_find_if(
      cryptos, [&crypto](const CryptoParams& c) { return crypto.Matches(c); });
  if (it == cryptos.end()) {
    return false;
  }
  *crypto_out = *it;
  return true;
}

// For audio, HMAC 32 (if enabled) is prefered over HMAC 80 because of the
// low overhead.
void GetSupportedAudioSdesCryptoSuites(
    const webrtc::CryptoOptions& crypto_options,
    std::vector<int>* crypto_suites) {
  if (crypto_options.srtp.enable_aes128_sha1_32_crypto_cipher) {
    crypto_suites->push_back(rtc::kSrtpAes128CmSha1_32);
  }
  crypto_suites->push_back(rtc::kSrtpAes128CmSha1_80);
  if (crypto_options.srtp.enable_gcm_crypto_suites) {
    crypto_suites->push_back(rtc::kSrtpAeadAes256Gcm);
    crypto_suites->push_back(rtc::kSrtpAeadAes128Gcm);
  }
}

void GetSupportedAudioSdesCryptoSuiteNames(
    const webrtc::CryptoOptions& crypto_options,
    std::vector<std::string>* crypto_suite_names) {
  GetSupportedSdesCryptoSuiteNames(GetSupportedAudioSdesCryptoSuites,
                                   crypto_options, crypto_suite_names);
}

void GetSupportedVideoSdesCryptoSuites(
    const webrtc::CryptoOptions& crypto_options,
    std::vector<int>* crypto_suites) {
  crypto_suites->push_back(rtc::kSrtpAes128CmSha1_80);
  if (crypto_options.srtp.enable_gcm_crypto_suites) {
    crypto_suites->push_back(rtc::kSrtpAeadAes256Gcm);
    crypto_suites->push_back(rtc::kSrtpAeadAes128Gcm);
  }
}

void GetSupportedVideoSdesCryptoSuiteNames(
    const webrtc::CryptoOptions& crypto_options,
    std::vector<std::string>* crypto_suite_names) {
  GetSupportedSdesCryptoSuiteNames(GetSupportedVideoSdesCryptoSuites,
                                   crypto_options, crypto_suite_names);
}

void GetSupportedDataSdesCryptoSuites(
    const webrtc::CryptoOptions& crypto_options,
    std::vector<int>* crypto_suites) {
  crypto_suites->push_back(rtc::kSrtpAes128CmSha1_80);
  if (crypto_options.srtp.enable_gcm_crypto_suites) {
    crypto_suites->push_back(rtc::kSrtpAeadAes256Gcm);
    crypto_suites->push_back(rtc::kSrtpAeadAes128Gcm);
  }
}

void GetSupportedDataSdesCryptoSuiteNames(
    const webrtc::CryptoOptions& crypto_options,
    std::vector<std::string>* crypto_suite_names) {
  GetSupportedSdesCryptoSuiteNames(GetSupportedDataSdesCryptoSuites,
                                   crypto_options, crypto_suite_names);
}

// Support any GCM cipher (if enabled through options). For video support only
// 80-bit SHA1 HMAC. For audio 32-bit HMAC is tolerated (if enabled) unless
// bundle is enabled because it is low overhead.
// Pick the crypto in the list that is supported.
static bool SelectCrypto(const MediaContentDescription* offer,
                         bool bundle,
                         const webrtc::CryptoOptions& crypto_options,
                         CryptoParams* crypto_out) {
  bool audio = offer->type() == MEDIA_TYPE_AUDIO;
  const CryptoParamsVec& cryptos = offer->cryptos();

  for (const CryptoParams& crypto : cryptos) {
    if ((crypto_options.srtp.enable_gcm_crypto_suites &&
         rtc::IsGcmCryptoSuiteName(crypto.cipher_suite)) ||
        rtc::kCsAesCm128HmacSha1_80 == crypto.cipher_suite ||
        (rtc::kCsAesCm128HmacSha1_32 == crypto.cipher_suite && audio &&
         !bundle && crypto_options.srtp.enable_aes128_sha1_32_crypto_cipher)) {
      return CreateCryptoParams(crypto.tag, crypto.cipher_suite, crypto_out);
    }
  }
  return false;
}

// Finds all StreamParams of all media types and attach them to stream_params.
static StreamParamsVec GetCurrentStreamParams(
    const std::vector<const ContentInfo*>& active_local_contents) {
  StreamParamsVec stream_params;
  for (const ContentInfo* content : active_local_contents) {
    for (const StreamParams& params : content->media_description()->streams()) {
      stream_params.push_back(params);
    }
  }
  return stream_params;
}

static StreamParams CreateStreamParamsForNewSenderWithSsrcs(
    const SenderOptions& sender,
    const std::string& rtcp_cname,
    bool include_rtx_streams,
    bool include_flexfec_stream,
    UniqueRandomIdGenerator* ssrc_generator) {
  StreamParams result;
  result.id = sender.track_id;

  // TODO(brandtr): Update when we support multistream protection.
  if (include_flexfec_stream && sender.num_sim_layers > 1) {
    include_flexfec_stream = false;
    RTC_LOG(LS_WARNING)
        << "Our FlexFEC implementation only supports protecting "
           "a single media streams. This session has multiple "
           "media streams however, so no FlexFEC SSRC will be generated.";
  }
  if (include_flexfec_stream &&
      !webrtc::field_trial::IsEnabled("WebRTC-FlexFEC-03")) {
    include_flexfec_stream = false;
    RTC_LOG(LS_WARNING)
        << "WebRTC-FlexFEC trial is not enabled, not sending FlexFEC";
  }

  result.GenerateSsrcs(sender.num_sim_layers, include_rtx_streams,
                       include_flexfec_stream, ssrc_generator);

  result.cname = rtcp_cname;
  result.set_stream_ids(sender.stream_ids);

  return result;
}

static bool ValidateSimulcastLayers(
    const std::vector<RidDescription>& rids,
    const SimulcastLayerList& simulcast_layers) {
  return absl::c_all_of(
      simulcast_layers.GetAllLayers(), [&rids](const SimulcastLayer& layer) {
        return absl::c_any_of(rids, [&layer](const RidDescription& rid) {
          return rid.rid == layer.rid;
        });
      });
}

static StreamParams CreateStreamParamsForNewSenderWithRids(
    const SenderOptions& sender,
    const std::string& rtcp_cname) {
  RTC_DCHECK(!sender.rids.empty());
  RTC_DCHECK_EQ(sender.num_sim_layers, 0)
      << "RIDs are the compliant way to indicate simulcast.";
  RTC_DCHECK(ValidateSimulcastLayers(sender.rids, sender.simulcast_layers));
  StreamParams result;
  result.id = sender.track_id;
  result.cname = rtcp_cname;
  result.set_stream_ids(sender.stream_ids);

  // More than one rid should be signaled.
  if (sender.rids.size() > 1) {
    result.set_rids(sender.rids);
  }

  return result;
}

// Adds SimulcastDescription if indicated by the media description options.
// MediaContentDescription should already be set up with the send rids.
static void AddSimulcastToMediaDescription(
    const MediaDescriptionOptions& media_description_options,
    MediaContentDescription* description) {
  RTC_DCHECK(description);

  // Check if we are using RIDs in this scenario.
  if (absl::c_all_of(description->streams(), [](const StreamParams& params) {
        return !params.has_rids();
      })) {
    return;
  }

  RTC_DCHECK_EQ(1, description->streams().size())
      << "RIDs are only supported in Unified Plan semantics.";
  RTC_DCHECK_EQ(1, media_description_options.sender_options.size());
  RTC_DCHECK(description->type() == MediaType::MEDIA_TYPE_AUDIO ||
             description->type() == MediaType::MEDIA_TYPE_VIDEO);

  // One RID or less indicates that simulcast is not needed.
  if (description->streams()[0].rids().size() <= 1) {
    return;
  }

  // Only negotiate the send layers.
  SimulcastDescription simulcast;
  simulcast.send_layers() =
      media_description_options.sender_options[0].simulcast_layers;
  description->set_simulcast_description(simulcast);
}

// Adds a StreamParams for each SenderOptions in `sender_options` to
// content_description.
// `current_params` - All currently known StreamParams of any media type.
template <class C>
static bool AddStreamParams(
    const std::vector<SenderOptions>& sender_options,
    const std::string& rtcp_cname,
    UniqueRandomIdGenerator* ssrc_generator,
    StreamParamsVec* current_streams,
    MediaContentDescriptionImpl<C>* content_description) {
  // SCTP streams are not negotiated using SDP/ContentDescriptions.
  if (IsSctpProtocol(content_description->protocol())) {
    return true;
  }

  const bool include_rtx_streams =
      ContainsRtxCodec(content_description->codecs());

  const bool include_flexfec_stream =
      ContainsFlexfecCodec(content_description->codecs());

  for (const SenderOptions& sender : sender_options) {
    StreamParams* param = GetStreamByIds(*current_streams, sender.track_id);
    if (!param) {
      // This is a new sender.
      StreamParams stream_param =
          sender.rids.empty()
              ?
              // Signal SSRCs and legacy simulcast (if requested).
              CreateStreamParamsForNewSenderWithSsrcs(
                  sender, rtcp_cname, include_rtx_streams,
                  include_flexfec_stream, ssrc_generator)
              :
              // Signal RIDs and spec-compliant simulcast (if requested).
              CreateStreamParamsForNewSenderWithRids(sender, rtcp_cname);

      content_description->AddStream(stream_param);

      // Store the new StreamParams in current_streams.
      // This is necessary so that we can use the CNAME for other media types.
      current_streams->push_back(stream_param);
    } else {
      // Use existing generated SSRCs/groups, but update the sync_label if
      // necessary. This may be needed if a MediaStreamTrack was moved from one
      // MediaStream to another.
      param->set_stream_ids(sender.stream_ids);
      content_description->AddStream(*param);
    }
  }
  return true;
}

// Updates the transport infos of the `sdesc` according to the given
// `bundle_group`. The transport infos of the content names within the
// `bundle_group` should be updated to use the ufrag, pwd and DTLS role of the
// first content within the `bundle_group`.
static bool UpdateTransportInfoForBundle(const ContentGroup& bundle_group,
                                         SessionDescription* sdesc) {
  // The bundle should not be empty.
  if (!sdesc || !bundle_group.FirstContentName()) {
    return false;
  }

  // We should definitely have a transport for the first content.
  const std::string& selected_content_name = *bundle_group.FirstContentName();
  const TransportInfo* selected_transport_info =
      sdesc->GetTransportInfoByName(selected_content_name);
  if (!selected_transport_info) {
    return false;
  }

  // Set the other contents to use the same ICE credentials.
  const std::string& selected_ufrag =
      selected_transport_info->description.ice_ufrag;
  const std::string& selected_pwd =
      selected_transport_info->description.ice_pwd;
  ConnectionRole selected_connection_role =
      selected_transport_info->description.connection_role;
  for (TransportInfo& transport_info : sdesc->transport_infos()) {
    if (bundle_group.HasContentName(transport_info.content_name) &&
        transport_info.content_name != selected_content_name) {
      transport_info.description.ice_ufrag = selected_ufrag;
      transport_info.description.ice_pwd = selected_pwd;
      transport_info.description.connection_role = selected_connection_role;
    }
  }
  return true;
}

// Gets the CryptoParamsVec of the given `content_name` from `sdesc`, and
// sets it to `cryptos`.
static bool GetCryptosByName(const SessionDescription* sdesc,
                             const std::string& content_name,
                             CryptoParamsVec* cryptos) {
  if (!sdesc || !cryptos) {
    return false;
  }
  const ContentInfo* content = sdesc->GetContentByName(content_name);
  if (!content || !content->media_description()) {
    return false;
  }
  *cryptos = content->media_description()->cryptos();
  return true;
}

// Prunes the `target_cryptos` by removing the crypto params (cipher_suite)
// which are not available in `filter`.
static void PruneCryptos(const CryptoParamsVec& filter,
                         CryptoParamsVec* target_cryptos) {
  if (!target_cryptos) {
    return;
  }

  target_cryptos->erase(
      std::remove_if(target_cryptos->begin(), target_cryptos->end(),
                     // Returns true if the `crypto`'s cipher_suite is not
                     // found in `filter`.
                     [&filter](const CryptoParams& crypto) {
                       for (const CryptoParams& entry : filter) {
                         if (entry.cipher_suite == crypto.cipher_suite)
                           return false;
                       }
                       return true;
                     }),
      target_cryptos->end());
}

static bool IsRtpContent(SessionDescription* sdesc,
                         const std::string& content_name) {
  bool is_rtp = false;
  ContentInfo* content = sdesc->GetContentByName(content_name);
  if (content && content->media_description()) {
    is_rtp = IsRtpProtocol(content->media_description()->protocol());
  }
  return is_rtp;
}

// Updates the crypto parameters of the `sdesc` according to the given
// `bundle_group`. The crypto parameters of all the contents within the
// `bundle_group` should be updated to use the common subset of the
// available cryptos.
static bool UpdateCryptoParamsForBundle(const ContentGroup& bundle_group,
                                        SessionDescription* sdesc) {
  // The bundle should not be empty.
  if (!sdesc || !bundle_group.FirstContentName()) {
    return false;
  }

  bool common_cryptos_needed = false;
  // Get the common cryptos.
  const ContentNames& content_names = bundle_group.content_names();
  CryptoParamsVec common_cryptos;
  bool first = true;
  for (const std::string& content_name : content_names) {
    if (!IsRtpContent(sdesc, content_name)) {
      continue;
    }
    // The common cryptos are needed if any of the content does not have DTLS
    // enabled.
    if (!sdesc->GetTransportInfoByName(content_name)->description.secure()) {
      common_cryptos_needed = true;
    }
    if (first) {
      first = false;
      // Initial the common_cryptos with the first content in the bundle group.
      if (!GetCryptosByName(sdesc, content_name, &common_cryptos)) {
        return false;
      }
      if (common_cryptos.empty()) {
        // If there's no crypto params, we should just return.
        return true;
      }
    } else {
      CryptoParamsVec cryptos;
      if (!GetCryptosByName(sdesc, content_name, &cryptos)) {
        return false;
      }
      PruneCryptos(cryptos, &common_cryptos);
    }
  }

  if (common_cryptos.empty() && common_cryptos_needed) {
    return false;
  }

  // Update to use the common cryptos.
  for (const std::string& content_name : content_names) {
    if (!IsRtpContent(sdesc, content_name)) {
      continue;
    }
    ContentInfo* content = sdesc->GetContentByName(content_name);
    if (IsMediaContent(content)) {
      MediaContentDescription* media_desc = content->media_description();
      if (!media_desc) {
        return false;
      }
      media_desc->set_cryptos(common_cryptos);
    }
  }
  return true;
}

static std::vector<const ContentInfo*> GetActiveContents(
    const SessionDescription& description,
    const MediaSessionOptions& session_options) {
  std::vector<const ContentInfo*> active_contents;
  for (size_t i = 0; i < description.contents().size(); ++i) {
    RTC_DCHECK_LT(i, session_options.media_description_options.size());
    const ContentInfo& content = description.contents()[i];
    const MediaDescriptionOptions& media_options =
        session_options.media_description_options[i];
    if (!content.rejected && !media_options.stopped &&
        content.name == media_options.mid) {
      active_contents.push_back(&content);
    }
  }
  return active_contents;
}

template <class C>
static bool ContainsRtxCodec(const std::vector<C>& codecs) {
  for (const auto& codec : codecs) {
    if (IsRtxCodec(codec)) {
      return true;
    }
  }
  return false;
}

template <class C>
static bool IsRedCodec(const C& codec) {
  return absl::EqualsIgnoreCase(codec.name, kRedCodecName);
}

template <class C>
static bool IsRtxCodec(const C& codec) {
  return absl::EqualsIgnoreCase(codec.name, kRtxCodecName);
}

template <class C>
static bool ContainsFlexfecCodec(const std::vector<C>& codecs) {
  for (const auto& codec : codecs) {
    if (IsFlexfecCodec(codec)) {
      return true;
    }
  }
  return false;
}

template <class C>
static bool IsFlexfecCodec(const C& codec) {
  return absl::EqualsIgnoreCase(codec.name, kFlexfecCodecName);
}

// Create a media content to be offered for the given `sender_options`,
// according to the given options.rtcp_mux, session_options.is_muc, codecs,
// secure_transport, crypto, and current_streams. If we don't currently have
// crypto (in current_cryptos) and it is enabled (in secure_policy), crypto is
// created (according to crypto_suites). The created content is added to the
// offer.
static bool CreateContentOffer(
    const MediaDescriptionOptions& media_description_options,
    const MediaSessionOptions& session_options,
    const SecurePolicy& secure_policy,
    const CryptoParamsVec* current_cryptos,
    const std::vector<std::string>& crypto_suites,
    const RtpHeaderExtensions& rtp_extensions,
    UniqueRandomIdGenerator* ssrc_generator,
    StreamParamsVec* current_streams,
    MediaContentDescription* offer) {
  offer->set_rtcp_mux(session_options.rtcp_mux_enabled);
  if (offer->type() == cricket::MEDIA_TYPE_VIDEO) {
    offer->set_rtcp_reduced_size(true);
  }

  // Build the vector of header extensions with directions for this
  // media_description's options.
  RtpHeaderExtensions extensions;
  for (auto extension_with_id : rtp_extensions) {
    for (const auto& extension : media_description_options.header_extensions) {
      if (extension_with_id.uri == extension.uri) {
        // TODO(crbug.com/1051821): Configure the extension direction from
        // the information in the media_description_options extension
        // capability.
        extensions.push_back(extension_with_id);
      }
    }
  }
  offer->set_rtp_header_extensions(extensions);

  AddSimulcastToMediaDescription(media_description_options, offer);

  if (secure_policy != SEC_DISABLED) {
    if (current_cryptos) {
      AddMediaCryptos(*current_cryptos, offer);
    }
    if (offer->cryptos().empty()) {
      if (!CreateMediaCryptos(crypto_suites, offer)) {
        return false;
      }
    }
  }

  if (secure_policy == SEC_REQUIRED && offer->cryptos().empty()) {
    return false;
  }
  return true;
}
template <class C>
static bool CreateMediaContentOffer(
    const MediaDescriptionOptions& media_description_options,
    const MediaSessionOptions& session_options,
    const std::vector<C>& codecs,
    const SecurePolicy& secure_policy,
    const CryptoParamsVec* current_cryptos,
    const std::vector<std::string>& crypto_suites,
    const RtpHeaderExtensions& rtp_extensions,
    UniqueRandomIdGenerator* ssrc_generator,
    StreamParamsVec* current_streams,
    MediaContentDescriptionImpl<C>* offer) {
  offer->AddCodecs(codecs);
  if (!AddStreamParams(media_description_options.sender_options,
                       session_options.rtcp_cname, ssrc_generator,
                       current_streams, offer)) {
    return false;
  }

  return CreateContentOffer(media_description_options, session_options,
                            secure_policy, current_cryptos, crypto_suites,
                            rtp_extensions, ssrc_generator, current_streams,
                            offer);
}

template <class C>
static bool ReferencedCodecsMatch(const std::vector<C>& codecs1,
                                  const int codec1_id,
                                  const std::vector<C>& codecs2,
                                  const int codec2_id) {
  const C* codec1 = FindCodecById(codecs1, codec1_id);
  const C* codec2 = FindCodecById(codecs2, codec2_id);
  return codec1 != nullptr && codec2 != nullptr && codec1->Matches(*codec2);
}

template <class C>
static void NegotiatePacketization(const C& local_codec,
                                   const C& remote_codec,
                                   C* negotiated_codec) {}

template <>
void NegotiatePacketization(const VideoCodec& local_codec,
                            const VideoCodec& remote_codec,
                            VideoCodec* negotiated_codec) {
  negotiated_codec->packetization =
      VideoCodec::IntersectPacketization(local_codec, remote_codec);
}

template <class C>
static void NegotiateCodecs(const std::vector<C>& local_codecs,
                            const std::vector<C>& offered_codecs,
                            std::vector<C>* negotiated_codecs,
                            bool keep_offer_order) {
  for (const C& ours : local_codecs) {
    C theirs;
    // Note that we intentionally only find one matching codec for each of our
    // local codecs, in case the remote offer contains duplicate codecs.
    if (FindMatchingCodec(local_codecs, offered_codecs, ours, &theirs)) {
      C negotiated = ours;
      NegotiatePacketization(ours, theirs, &negotiated);
      negotiated.IntersectFeedbackParams(theirs);
      if (IsRtxCodec(negotiated)) {
        const auto apt_it =
            theirs.params.find(kCodecParamAssociatedPayloadType);
        // FindMatchingCodec shouldn't return something with no apt value.
        RTC_DCHECK(apt_it != theirs.params.end());
        negotiated.SetParam(kCodecParamAssociatedPayloadType, apt_it->second);

        // We support parsing the declarative rtx-time parameter.
        const auto rtx_time_it = theirs.params.find(kCodecParamRtxTime);
        if (rtx_time_it != theirs.params.end()) {
          negotiated.SetParam(kCodecParamRtxTime, rtx_time_it->second);
        }
      } else if (IsRedCodec(negotiated)) {
        const auto red_it = theirs.params.find(kCodecParamNotInNameValueFormat);
        if (red_it != theirs.params.end()) {
          negotiated.SetParam(kCodecParamNotInNameValueFormat, red_it->second);
        }
      }
      if (absl::EqualsIgnoreCase(ours.name, kH264CodecName)) {
        webrtc::H264GenerateProfileLevelIdForAnswer(ours.params, theirs.params,
                                                    &negotiated.params);
      }
      negotiated.id = theirs.id;
      negotiated.name = theirs.name;
      negotiated_codecs->push_back(std::move(negotiated));
    }
  }
  if (keep_offer_order) {
    // RFC3264: Although the answerer MAY list the formats in their desired
    // order of preference, it is RECOMMENDED that unless there is a
    // specific reason, the answerer list formats in the same relative order
    // they were present in the offer.
    // This can be skipped when the transceiver has any codec preferences.
    std::unordered_map<int, int> payload_type_preferences;
    int preference = static_cast<int>(offered_codecs.size() + 1);
    for (const C& codec : offered_codecs) {
      payload_type_preferences[codec.id] = preference--;
    }
    absl::c_sort(*negotiated_codecs, [&payload_type_preferences](const C& a,
                                                                 const C& b) {
      return payload_type_preferences[a.id] > payload_type_preferences[b.id];
    });
  }
}

// Finds a codec in `codecs2` that matches `codec_to_match`, which is
// a member of `codecs1`. If `codec_to_match` is an RED or RTX codec, both
// the codecs themselves and their associated codecs must match.
template <class C>
static bool FindMatchingCodec(const std::vector<C>& codecs1,
                              const std::vector<C>& codecs2,
                              const C& codec_to_match,
                              C* found_codec) {
  // `codec_to_match` should be a member of `codecs1`, in order to look up
  // RED/RTX codecs' associated codecs correctly. If not, that's a programming
  // error.
  RTC_DCHECK(absl::c_any_of(codecs1, [&codec_to_match](const C& codec) {
    return &codec == &codec_to_match;
  }));
  for (const C& potential_match : codecs2) {
    if (potential_match.Matches(codec_to_match)) {
      if (IsRtxCodec(codec_to_match)) {
        int apt_value_1 = 0;
        int apt_value_2 = 0;
        if (!codec_to_match.GetParam(kCodecParamAssociatedPayloadType,
                                     &apt_value_1) ||
            !potential_match.GetParam(kCodecParamAssociatedPayloadType,
                                      &apt_value_2)) {
          RTC_LOG(LS_WARNING) << "RTX missing associated payload type.";
          continue;
        }
        if (!ReferencedCodecsMatch(codecs1, apt_value_1, codecs2,
                                   apt_value_2)) {
          continue;
        }
      } else if (IsRedCodec(codec_to_match)) {
        auto red_parameters_1 =
            codec_to_match.params.find(kCodecParamNotInNameValueFormat);
        auto red_parameters_2 =
            potential_match.params.find(kCodecParamNotInNameValueFormat);
        bool has_parameters_1 = red_parameters_1 != codec_to_match.params.end();
        bool has_parameters_2 =
            red_parameters_2 != potential_match.params.end();
        if (has_parameters_1 && has_parameters_2) {
          // Mixed reference codecs (i.e. 111/112) are not supported.
          // Different levels of redundancy between offer and answer are
          // since RED is considered to be declarative.
          std::vector<std::string> redundant_payloads_1;
          std::vector<std::string> redundant_payloads_2;
          rtc::split(red_parameters_1->second, '/', &redundant_payloads_1);
          rtc::split(red_parameters_2->second, '/', &redundant_payloads_2);
          if (redundant_payloads_1.size() > 0 &&
              redundant_payloads_2.size() > 0) {
            bool consistent = true;
            for (size_t i = 1; i < redundant_payloads_1.size(); i++) {
              if (redundant_payloads_1[i] != redundant_payloads_1[0]) {
                consistent = false;
                break;
              }
            }
            for (size_t i = 1; i < redundant_payloads_2.size(); i++) {
              if (redundant_payloads_2[i] != redundant_payloads_2[0]) {
                consistent = false;
                break;
              }
            }
            if (!consistent) {
              continue;
            }

            int red_value_1;
            int red_value_2;
            if (rtc::FromString(redundant_payloads_1[0], &red_value_1) &&
                rtc::FromString(redundant_payloads_2[0], &red_value_2)) {
              if (!ReferencedCodecsMatch(codecs1, red_value_1, codecs2,
                                         red_value_2)) {
                continue;
              }
            }
          }
        } else if (has_parameters_1 != has_parameters_2) {
          continue;
        }
      }
      if (found_codec) {
        *found_codec = potential_match;
      }
      return true;
    }
  }
  return false;
}

// Find the codec in `codec_list` that `rtx_codec` is associated with.
template <class C>
static const C* GetAssociatedCodecForRtx(const std::vector<C>& codec_list,
                                         const C& rtx_codec) {
  std::string associated_pt_str;
  if (!rtx_codec.GetParam(kCodecParamAssociatedPayloadType,
                          &associated_pt_str)) {
    RTC_LOG(LS_WARNING) << "RTX codec " << rtx_codec.name
                        << " is missing an associated payload type.";
    return nullptr;
  }

  int associated_pt;
  if (!rtc::FromString(associated_pt_str, &associated_pt)) {
    RTC_LOG(LS_WARNING) << "Couldn't convert payload type " << associated_pt_str
                        << " of RTX codec " << rtx_codec.name
                        << " to an integer.";
    return nullptr;
  }

  // Find the associated codec for the RTX codec.
  const C* associated_codec = FindCodecById(codec_list, associated_pt);
  if (!associated_codec) {
    RTC_LOG(LS_WARNING) << "Couldn't find associated codec with payload type "
                        << associated_pt << " for RTX codec " << rtx_codec.name
                        << ".";
  }
  return associated_codec;
}

// Find the codec in `codec_list` that `red_codec` is associated with.
template <class C>
static const C* GetAssociatedCodecForRed(const std::vector<C>& codec_list,
                                         const C& red_codec) {
  std::string fmtp;
  if (!red_codec.GetParam(kCodecParamNotInNameValueFormat, &fmtp)) {
    // Normal for video/RED.
    RTC_LOG(LS_WARNING) << "RED codec " << red_codec.name
                        << " is missing an associated payload type.";
    return nullptr;
  }

  std::vector<std::string> redundant_payloads;
  rtc::split(fmtp, '/', &redundant_payloads);
  if (redundant_payloads.size() < 2) {
    return nullptr;
  }

  std::string associated_pt_str = redundant_payloads[0];
  int associated_pt;
  if (!rtc::FromString(associated_pt_str, &associated_pt)) {
    RTC_LOG(LS_WARNING) << "Couldn't convert first payload type "
                        << associated_pt_str << " of RED codec "
                        << red_codec.name << " to an integer.";
    return nullptr;
  }

  // Find the associated codec for the RED codec.
  const C* associated_codec = FindCodecById(codec_list, associated_pt);
  if (!associated_codec) {
    RTC_LOG(LS_WARNING) << "Couldn't find associated codec with payload type "
                        << associated_pt << " for RED codec " << red_codec.name
                        << ".";
  }
  return associated_codec;
}

// Adds all codecs from `reference_codecs` to `offered_codecs` that don't
// already exist in `offered_codecs` and ensure the payload types don't
// collide.
template <class C>
static void MergeCodecs(const std::vector<C>& reference_codecs,
                        std::vector<C>* offered_codecs,
                        UsedPayloadTypes* used_pltypes) {
  // Add all new codecs that are not RTX/RED codecs.
  // The two-pass splitting of the loops means preferring payload types
  // of actual codecs with respect to collisions.
  for (const C& reference_codec : reference_codecs) {
    if (!IsRtxCodec(reference_codec) && !IsRedCodec(reference_codec) &&
        !FindMatchingCodec<C>(reference_codecs, *offered_codecs,
                              reference_codec, nullptr)) {
      C codec = reference_codec;
      used_pltypes->FindAndSetIdUsed(&codec);
      offered_codecs->push_back(codec);
    }
  }

  // Add all new RTX or RED codecs.
  for (const C& reference_codec : reference_codecs) {
    if (IsRtxCodec(reference_codec) &&
        !FindMatchingCodec<C>(reference_codecs, *offered_codecs,
                              reference_codec, nullptr)) {
      C rtx_codec = reference_codec;
      const C* associated_codec =
          GetAssociatedCodecForRtx(reference_codecs, rtx_codec);
      if (!associated_codec) {
        continue;
      }
      // Find a codec in the offered list that matches the reference codec.
      // Its payload type may be different than the reference codec.
      C matching_codec;
      if (!FindMatchingCodec<C>(reference_codecs, *offered_codecs,
                                *associated_codec, &matching_codec)) {
        RTC_LOG(LS_WARNING)
            << "Couldn't find matching " << associated_codec->name << " codec.";
        continue;
      }

      rtx_codec.params[kCodecParamAssociatedPayloadType] =
          rtc::ToString(matching_codec.id);
      used_pltypes->FindAndSetIdUsed(&rtx_codec);
      offered_codecs->push_back(rtx_codec);
    } else if (IsRedCodec(reference_codec) &&
               !FindMatchingCodec<C>(reference_codecs, *offered_codecs,
                                     reference_codec, nullptr)) {
      C red_codec = reference_codec;
      const C* associated_codec =
          GetAssociatedCodecForRed(reference_codecs, red_codec);
      if (associated_codec) {
        C matching_codec;
        if (!FindMatchingCodec<C>(reference_codecs, *offered_codecs,
                                  *associated_codec, &matching_codec)) {
          RTC_LOG(LS_WARNING) << "Couldn't find matching "
                              << associated_codec->name << " codec.";
          continue;
        }

        red_codec.params[kCodecParamNotInNameValueFormat] =
            rtc::ToString(matching_codec.id) + "/" +
            rtc::ToString(matching_codec.id);
      }
      used_pltypes->FindAndSetIdUsed(&red_codec);
      offered_codecs->push_back(red_codec);
    }
  }
}

// `codecs` is a full list of codecs with correct payload type mappings, which
// don't conflict with mappings of the other media type; `supported_codecs` is
// a list filtered for the media section`s direction but with default payload
// types.
template <typename Codecs>
static Codecs MatchCodecPreference(
    const std::vector<webrtc::RtpCodecCapability>& codec_preferences,
    const Codecs& codecs,
    const Codecs& supported_codecs) {
  Codecs filtered_codecs;
  bool want_rtx = false;
  bool want_red = false;

  for (const auto& codec_preference : codec_preferences) {
    if (IsRtxCodec(codec_preference)) {
      want_rtx = true;
    } else if (IsRedCodec(codec_preference)) {
      want_red = true;
    }
  }
  for (const auto& codec_preference : codec_preferences) {
    auto found_codec = absl::c_find_if(
        supported_codecs,
        [&codec_preference](const typename Codecs::value_type& codec) {
          webrtc::RtpCodecParameters codec_parameters =
              codec.ToCodecParameters();
          return codec_parameters.name == codec_preference.name &&
                 codec_parameters.kind == codec_preference.kind &&
                 codec_parameters.num_channels ==
                     codec_preference.num_channels &&
                 codec_parameters.clock_rate == codec_preference.clock_rate &&
                 codec_parameters.parameters == codec_preference.parameters;
        });

    if (found_codec != supported_codecs.end()) {
      typename Codecs::value_type found_codec_with_correct_pt;
      if (FindMatchingCodec(supported_codecs, codecs, *found_codec,
                            &found_codec_with_correct_pt)) {
        filtered_codecs.push_back(found_codec_with_correct_pt);
        std::string id = rtc::ToString(found_codec_with_correct_pt.id);
        // Search for the matching rtx or red codec.
        if (want_red || want_rtx) {
          for (const auto& codec : codecs) {
            if (IsRtxCodec(codec)) {
              const auto apt =
                  codec.params.find(cricket::kCodecParamAssociatedPayloadType);
              if (apt != codec.params.end() && apt->second == id) {
                filtered_codecs.push_back(codec);
                break;
              }
            } else if (IsRedCodec(codec)) {
              // For RED, do not insert the codec again if it was already
              // inserted. audio/red for opus gets enabled by having RED before
              // the primary codec.
              const auto fmtp =
                  codec.params.find(cricket::kCodecParamNotInNameValueFormat);
              if (fmtp != codec.params.end()) {
                std::vector<std::string> redundant_payloads;
                rtc::split(fmtp->second, '/', &redundant_payloads);
                if (redundant_payloads.size() > 0 &&
                    redundant_payloads[0] == id) {
                  if (std::find(filtered_codecs.begin(), filtered_codecs.end(),
                                codec) == filtered_codecs.end()) {
                    filtered_codecs.push_back(codec);
                  }
                  break;
                }
              }
            }
          }
        }
      }
    }
  }

  return filtered_codecs;
}

// Compute the union of `codecs1` and `codecs2`.
template <class C>
std::vector<C> ComputeCodecsUnion(const std::vector<C>& codecs1,
                                  const std::vector<C>& codecs2) {
  std::vector<C> all_codecs;
  UsedPayloadTypes used_payload_types;
  for (const C& codec : codecs1) {
    C codec_mutable = codec;
    used_payload_types.FindAndSetIdUsed(&codec_mutable);
    all_codecs.push_back(codec_mutable);
  }

  // Use MergeCodecs to merge the second half of our list as it already checks
  // and fixes problems with duplicate payload types.
  MergeCodecs<C>(codecs2, &all_codecs, &used_payload_types);

  return all_codecs;
}

// Adds all extensions from `reference_extensions` to `offered_extensions` that
// don't already exist in `offered_extensions` and ensure the IDs don't
// collide. If an extension is added, it's also added to `regular_extensions` or
// `encrypted_extensions`, and if the extension is in `regular_extensions` or
// `encrypted_extensions`, its ID is marked as used in `used_ids`.
// `offered_extensions` is for either audio or video while `regular_extensions`
// and `encrypted_extensions` are used for both audio and video. There could be
// overlap between audio extensions and video extensions.
static void MergeRtpHdrExts(const RtpHeaderExtensions& reference_extensions,
                            RtpHeaderExtensions* offered_extensions,
                            RtpHeaderExtensions* regular_extensions,
                            RtpHeaderExtensions* encrypted_extensions,
                            UsedRtpHeaderExtensionIds* used_ids) {
  for (auto reference_extension : reference_extensions) {
    if (!webrtc::RtpExtension::FindHeaderExtensionByUriAndEncryption(
            *offered_extensions, reference_extension.uri,
            reference_extension.encrypt)) {
      if (reference_extension.encrypt) {
        const webrtc::RtpExtension* existing =
            webrtc::RtpExtension::FindHeaderExtensionByUriAndEncryption(
                *encrypted_extensions, reference_extension.uri,
                reference_extension.encrypt);
        if (existing) {
          offered_extensions->push_back(*existing);
        } else {
          used_ids->FindAndSetIdUsed(&reference_extension);
          encrypted_extensions->push_back(reference_extension);
          offered_extensions->push_back(reference_extension);
        }
      } else {
        const webrtc::RtpExtension* existing =
            webrtc::RtpExtension::FindHeaderExtensionByUriAndEncryption(
                *regular_extensions, reference_extension.uri,
                reference_extension.encrypt);
        if (existing) {
          offered_extensions->push_back(*existing);
        } else {
          used_ids->FindAndSetIdUsed(&reference_extension);
          regular_extensions->push_back(reference_extension);
          offered_extensions->push_back(reference_extension);
        }
      }
    }
  }
}

static void AddEncryptedVersionsOfHdrExts(
    RtpHeaderExtensions* offered_extensions,
    RtpHeaderExtensions* encrypted_extensions,
    UsedRtpHeaderExtensionIds* used_ids) {
  RtpHeaderExtensions encrypted_extensions_to_add;
  for (const auto& extension : *offered_extensions) {
    // Skip existing encrypted offered extension
    if (extension.encrypt) {
      continue;
    }

    // Skip if we cannot encrypt the extension
    if (!webrtc::RtpExtension::IsEncryptionSupported(extension.uri)) {
      continue;
    }

    // Skip if an encrypted extension with that URI already exists in the
    // offered extensions.
    const bool have_encrypted_extension =
        webrtc::RtpExtension::FindHeaderExtensionByUriAndEncryption(
            *offered_extensions, extension.uri, true);
    if (have_encrypted_extension) {
      continue;
    }

    // Determine if a shared encrypted extension with that URI already exists.
    const webrtc::RtpExtension* shared_encrypted_extension =
        webrtc::RtpExtension::FindHeaderExtensionByUriAndEncryption(
            *encrypted_extensions, extension.uri, true);
    if (shared_encrypted_extension) {
      // Re-use the shared encrypted extension
      encrypted_extensions_to_add.push_back(*shared_encrypted_extension);
      continue;
    }

    // None exists. Create a new shared encrypted extension from the
    // non-encrypted one.
    webrtc::RtpExtension new_encrypted_extension(extension);
    new_encrypted_extension.encrypt = true;
    used_ids->FindAndSetIdUsed(&new_encrypted_extension);
    encrypted_extensions->push_back(new_encrypted_extension);
    encrypted_extensions_to_add.push_back(new_encrypted_extension);
  }

  // Append the additional encrypted extensions to be offered
  offered_extensions->insert(offered_extensions->end(),
                             encrypted_extensions_to_add.begin(),
                             encrypted_extensions_to_add.end());
}

// Mostly identical to RtpExtension::FindHeaderExtensionByUri but discards any
// encrypted extensions that this implementation cannot encrypt.
static const webrtc::RtpExtension* FindHeaderExtensionByUriDiscardUnsupported(
    const std::vector<webrtc::RtpExtension>& extensions,
    absl::string_view uri,
    webrtc::RtpExtension::Filter filter) {
  // Note: While it's technically possible to decrypt extensions that we don't
  // encrypt, the symmetric API of libsrtp does not allow us to supply
  // different IDs for encryption/decryption of header extensions depending on
  // whether the packet is inbound or outbound. Thereby, we are limited to
  // what we can send in encrypted form.
  if (!webrtc::RtpExtension::IsEncryptionSupported(uri)) {
    // If there's no encryption support and we only want encrypted extensions,
    // there's no point in continuing the search here.
    if (filter == webrtc::RtpExtension::kRequireEncryptedExtension) {
      return nullptr;
    }

    // Instruct to only return non-encrypted extensions
    filter = webrtc::RtpExtension::Filter::kDiscardEncryptedExtension;
  }

  return webrtc::RtpExtension::FindHeaderExtensionByUri(extensions, uri,
                                                        filter);
}

static void NegotiateRtpHeaderExtensions(
    const RtpHeaderExtensions& local_extensions,
    const RtpHeaderExtensions& offered_extensions,
    webrtc::RtpExtension::Filter filter,
    RtpHeaderExtensions* negotiated_extensions) {
  // TransportSequenceNumberV2 is not offered by default. The special logic for
  // the TransportSequenceNumber extensions works as follows:
  // Offer       Answer
  // V1          V1 if in local_extensions.
  // V1 and V2   V2 regardless of local_extensions.
  // V2          V2 regardless of local_extensions.
  const webrtc::RtpExtension* transport_sequence_number_v2_offer =
      FindHeaderExtensionByUriDiscardUnsupported(
          offered_extensions,
          webrtc::RtpExtension::kTransportSequenceNumberV2Uri, filter);

  bool frame_descriptor_in_local = false;
  bool dependency_descriptor_in_local = false;
  bool abs_capture_time_in_local = false;

  for (const webrtc::RtpExtension& ours : local_extensions) {
    if (ours.uri == webrtc::RtpExtension::kGenericFrameDescriptorUri00)
      frame_descriptor_in_local = true;
    else if (ours.uri == webrtc::RtpExtension::kDependencyDescriptorUri)
      dependency_descriptor_in_local = true;
    else if (ours.uri == webrtc::RtpExtension::kAbsoluteCaptureTimeUri)
      abs_capture_time_in_local = true;
    const webrtc::RtpExtension* theirs =
        FindHeaderExtensionByUriDiscardUnsupported(offered_extensions, ours.uri,
                                                   filter);
    if (theirs) {
      if (transport_sequence_number_v2_offer &&
          ours.uri == webrtc::RtpExtension::kTransportSequenceNumberUri) {
        // Don't respond to
        // http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01
        // if we get an offer including
        // http://www.webrtc.org/experiments/rtp-hdrext/transport-wide-cc-02
        continue;
      } else {
        // We respond with their RTP header extension id.
        negotiated_extensions->push_back(*theirs);
      }
    }
  }

  if (transport_sequence_number_v2_offer) {
    // Respond that we support kTransportSequenceNumberV2Uri.
    negotiated_extensions->push_back(*transport_sequence_number_v2_offer);
  }

  // Frame descriptors support. If the extension is not present locally, but is
  // in the offer, we add it to the list.
  if (!dependency_descriptor_in_local) {
    const webrtc::RtpExtension* theirs =
        FindHeaderExtensionByUriDiscardUnsupported(
            offered_extensions, webrtc::RtpExtension::kDependencyDescriptorUri,
            filter);
    if (theirs) {
      negotiated_extensions->push_back(*theirs);
    }
  }
  if (!frame_descriptor_in_local) {
    const webrtc::RtpExtension* theirs =
        FindHeaderExtensionByUriDiscardUnsupported(
            offered_extensions,
            webrtc::RtpExtension::kGenericFrameDescriptorUri00, filter);
    if (theirs) {
      negotiated_extensions->push_back(*theirs);
    }
  }

  // Absolute capture time support. If the extension is not present locally, but
  // is in the offer, we add it to the list.
  if (!abs_capture_time_in_local) {
    const webrtc::RtpExtension* theirs =
        FindHeaderExtensionByUriDiscardUnsupported(
            offered_extensions, webrtc::RtpExtension::kAbsoluteCaptureTimeUri,
            filter);
    if (theirs) {
      negotiated_extensions->push_back(*theirs);
    }
  }
}

static void StripCNCodecs(AudioCodecs* audio_codecs) {
  audio_codecs->erase(std::remove_if(audio_codecs->begin(), audio_codecs->end(),
                                     [](const AudioCodec& codec) {
                                       return absl::EqualsIgnoreCase(
                                           codec.name, kComfortNoiseCodecName);
                                     }),
                      audio_codecs->end());
}

template <class C>
static bool SetCodecsInAnswer(
    const MediaContentDescriptionImpl<C>* offer,
    const std::vector<C>& local_codecs,
    const MediaDescriptionOptions& media_description_options,
    const MediaSessionOptions& session_options,
    UniqueRandomIdGenerator* ssrc_generator,
    StreamParamsVec* current_streams,
    MediaContentDescriptionImpl<C>* answer) {
  std::vector<C> negotiated_codecs;
  NegotiateCodecs(local_codecs, offer->codecs(), &negotiated_codecs,
                  media_description_options.codec_preferences.empty());
  answer->AddCodecs(negotiated_codecs);
  answer->set_protocol(offer->protocol());
  if (!AddStreamParams(media_description_options.sender_options,
                       session_options.rtcp_cname, ssrc_generator,
                       current_streams, answer)) {
    return false;  // Something went seriously wrong.
  }
  return true;
}

// Create a media content to be answered for the given `sender_options`
// according to the given session_options.rtcp_mux, session_options.streams,
// codecs, crypto, and current_streams.  If we don't currently have crypto (in
// current_cryptos) and it is enabled (in secure_policy), crypto is created
// (according to crypto_suites). The codecs, rtcp_mux, and crypto are all
// negotiated with the offer. If the negotiation fails, this method returns
// false.  The created content is added to the offer.
static bool CreateMediaContentAnswer(
    const MediaContentDescription* offer,
    const MediaDescriptionOptions& media_description_options,
    const MediaSessionOptions& session_options,
    const SecurePolicy& sdes_policy,
    const CryptoParamsVec* current_cryptos,
    const RtpHeaderExtensions& local_rtp_extensions,
    UniqueRandomIdGenerator* ssrc_generator,
    bool enable_encrypted_rtp_header_extensions,
    StreamParamsVec* current_streams,
    bool bundle_enabled,
    MediaContentDescription* answer) {
  answer->set_extmap_allow_mixed_enum(offer->extmap_allow_mixed_enum());
  const webrtc::RtpExtension::Filter extensions_filter =
      enable_encrypted_rtp_header_extensions
          ? webrtc::RtpExtension::Filter::kPreferEncryptedExtension
          : webrtc::RtpExtension::Filter::kDiscardEncryptedExtension;
  RtpHeaderExtensions negotiated_rtp_extensions;
  NegotiateRtpHeaderExtensions(local_rtp_extensions,
                               offer->rtp_header_extensions(),
                               extensions_filter, &negotiated_rtp_extensions);
  answer->set_rtp_header_extensions(negotiated_rtp_extensions);

  answer->set_rtcp_mux(session_options.rtcp_mux_enabled && offer->rtcp_mux());
  if (answer->type() == cricket::MEDIA_TYPE_VIDEO) {
    answer->set_rtcp_reduced_size(offer->rtcp_reduced_size());
  }

  answer->set_remote_estimate(offer->remote_estimate());

  if (sdes_policy != SEC_DISABLED) {
    CryptoParams crypto;
    if (SelectCrypto(offer, bundle_enabled, session_options.crypto_options,
                     &crypto)) {
      if (current_cryptos) {
        FindMatchingCrypto(*current_cryptos, crypto, &crypto);
      }
      answer->AddCrypto(crypto);
    }
  }

  if (answer->cryptos().empty() && sdes_policy == SEC_REQUIRED) {
    return false;
  }

  AddSimulcastToMediaDescription(media_description_options, answer);

  answer->set_direction(NegotiateRtpTransceiverDirection(
      offer->direction(), media_description_options.direction));

  return true;
}

static bool IsMediaProtocolSupported(MediaType type,
                                     const std::string& protocol,
                                     bool secure_transport) {
  // Since not all applications serialize and deserialize the media protocol,
  // we will have to accept `protocol` to be empty.
  if (protocol.empty()) {
    return true;
  }

  if (type == MEDIA_TYPE_DATA) {
    // Check for SCTP
    if (secure_transport) {
      // Most likely scenarios first.
      return IsDtlsSctp(protocol);
    } else {
      return IsPlainSctp(protocol);
    }
  }

  // Allow for non-DTLS RTP protocol even when using DTLS because that's what
  // JSEP specifies.
  if (secure_transport) {
    // Most likely scenarios first.
    return IsDtlsRtp(protocol) || IsPlainRtp(protocol);
  } else {
    return IsPlainRtp(protocol);
  }
}

static void SetMediaProtocol(bool secure_transport,
                             MediaContentDescription* desc) {
  if (!desc->cryptos().empty())
    desc->set_protocol(kMediaProtocolSavpf);
  else if (secure_transport)
    desc->set_protocol(kMediaProtocolDtlsSavpf);
  else
    desc->set_protocol(kMediaProtocolAvpf);
}

// Gets the TransportInfo of the given `content_name` from the
// `current_description`. If doesn't exist, returns a new one.
static const TransportDescription* GetTransportDescription(
    const std::string& content_name,
    const SessionDescription* current_description) {
  const TransportDescription* desc = NULL;
  if (current_description) {
    const TransportInfo* info =
        current_description->GetTransportInfoByName(content_name);
    if (info) {
      desc = &info->description;
    }
  }
  return desc;
}

// Gets the current DTLS state from the transport description.
static bool IsDtlsActive(const ContentInfo* content,
                         const SessionDescription* current_description) {
  if (!content) {
    return false;
  }

  size_t msection_index = content - &current_description->contents()[0];

  if (current_description->transport_infos().size() <= msection_index) {
    return false;
  }

  return current_description->transport_infos()[msection_index]
      .description.secure();
}

void MediaDescriptionOptions::AddAudioSender(
    const std::string& track_id,
    const std::vector<std::string>& stream_ids) {
  RTC_DCHECK(type == MEDIA_TYPE_AUDIO);
  AddSenderInternal(track_id, stream_ids, {}, SimulcastLayerList(), 1);
}

void MediaDescriptionOptions::AddVideoSender(
    const std::string& track_id,
    const std::vector<std::string>& stream_ids,
    const std::vector<RidDescription>& rids,
    const SimulcastLayerList& simulcast_layers,
    int num_sim_layers) {
  RTC_DCHECK(type == MEDIA_TYPE_VIDEO);
  RTC_DCHECK(rids.empty() || num_sim_layers == 0)
      << "RIDs are the compliant way to indicate simulcast.";
  RTC_DCHECK(ValidateSimulcastLayers(rids, simulcast_layers));
  AddSenderInternal(track_id, stream_ids, rids, simulcast_layers,
                    num_sim_layers);
}

void MediaDescriptionOptions::AddSenderInternal(
    const std::string& track_id,
    const std::vector<std::string>& stream_ids,
    const std::vector<RidDescription>& rids,
    const SimulcastLayerList& simulcast_layers,
    int num_sim_layers) {
  // TODO(steveanton): Support any number of stream ids.
  RTC_CHECK(stream_ids.size() == 1U);
  SenderOptions options;
  options.track_id = track_id;
  options.stream_ids = stream_ids;
  options.simulcast_layers = simulcast_layers;
  options.rids = rids;
  options.num_sim_layers = num_sim_layers;
  sender_options.push_back(options);
}

bool MediaSessionOptions::HasMediaDescription(MediaType type) const {
  return absl::c_any_of(
      media_description_options,
      [type](const MediaDescriptionOptions& t) { return t.type == type; });
}

MediaSessionDescriptionFactory::MediaSessionDescriptionFactory(
    const TransportDescriptionFactory* transport_desc_factory,
    rtc::UniqueRandomIdGenerator* ssrc_generator)
    : ssrc_generator_(ssrc_generator),
      transport_desc_factory_(transport_desc_factory) {
  RTC_DCHECK(ssrc_generator_);
}

MediaSessionDescriptionFactory::MediaSessionDescriptionFactory(
    ChannelManager* channel_manager,
    const TransportDescriptionFactory* transport_desc_factory)
    : MediaSessionDescriptionFactory(transport_desc_factory,
                                     &channel_manager->ssrc_generator()) {
  channel_manager->GetSupportedAudioSendCodecs(&audio_send_codecs_);
  channel_manager->GetSupportedAudioReceiveCodecs(&audio_recv_codecs_);
  channel_manager->GetSupportedVideoSendCodecs(&video_send_codecs_);
  channel_manager->GetSupportedVideoReceiveCodecs(&video_recv_codecs_);
  ComputeAudioCodecsIntersectionAndUnion();
  ComputeVideoCodecsIntersectionAndUnion();
}

const AudioCodecs& MediaSessionDescriptionFactory::audio_sendrecv_codecs()
    const {
  return audio_sendrecv_codecs_;
}

const AudioCodecs& MediaSessionDescriptionFactory::audio_send_codecs() const {
  return audio_send_codecs_;
}

const AudioCodecs& MediaSessionDescriptionFactory::audio_recv_codecs() const {
  return audio_recv_codecs_;
}

void MediaSessionDescriptionFactory::set_audio_codecs(
    const AudioCodecs& send_codecs,
    const AudioCodecs& recv_codecs) {
  audio_send_codecs_ = send_codecs;
  audio_recv_codecs_ = recv_codecs;
  ComputeAudioCodecsIntersectionAndUnion();
}

const VideoCodecs& MediaSessionDescriptionFactory::video_sendrecv_codecs()
    const {
  return video_sendrecv_codecs_;
}

const VideoCodecs& MediaSessionDescriptionFactory::video_send_codecs() const {
  return video_send_codecs_;
}

const VideoCodecs& MediaSessionDescriptionFactory::video_recv_codecs() const {
  return video_recv_codecs_;
}

void MediaSessionDescriptionFactory::set_video_codecs(
    const VideoCodecs& send_codecs,
    const VideoCodecs& recv_codecs) {
  video_send_codecs_ = send_codecs;
  video_recv_codecs_ = recv_codecs;
  ComputeVideoCodecsIntersectionAndUnion();
}

static void RemoveUnifiedPlanExtensions(RtpHeaderExtensions* extensions) {
  RTC_DCHECK(extensions);

  extensions->erase(
      std::remove_if(extensions->begin(), extensions->end(),
                     [](auto extension) {
                       return extension.uri == webrtc::RtpExtension::kMidUri ||
                              extension.uri == webrtc::RtpExtension::kRidUri ||
                              extension.uri ==
                                  webrtc::RtpExtension::kRepairedRidUri;
                     }),
      extensions->end());
}

RtpHeaderExtensions
MediaSessionDescriptionFactory::filtered_rtp_header_extensions(
    RtpHeaderExtensions extensions) const {
  if (!is_unified_plan_) {
    RemoveUnifiedPlanExtensions(&extensions);
  }
  return extensions;
}

std::unique_ptr<SessionDescription> MediaSessionDescriptionFactory::CreateOffer(
    const MediaSessionOptions& session_options,
    const SessionDescription* current_description) const {
  // Must have options for each existing section.
  if (current_description) {
    RTC_DCHECK_LE(current_description->contents().size(),
                  session_options.media_description_options.size());
  }

  IceCredentialsIterator ice_credentials(
      session_options.pooled_ice_credentials);

  std::vector<const ContentInfo*> current_active_contents;
  if (current_description) {
    current_active_contents =
        GetActiveContents(*current_description, session_options);
  }

  StreamParamsVec current_streams =
      GetCurrentStreamParams(current_active_contents);

  AudioCodecs offer_audio_codecs;
  VideoCodecs offer_video_codecs;
  GetCodecsForOffer(current_active_contents, &offer_audio_codecs,
                    &offer_video_codecs);
  AudioVideoRtpHeaderExtensions extensions_with_ids =
      GetOfferedRtpHeaderExtensionsWithIds(
          current_active_contents, session_options.offer_extmap_allow_mixed,
          session_options.media_description_options);

  auto offer = std::make_unique<SessionDescription>();

  // Iterate through the media description options, matching with existing media
  // descriptions in `current_description`.
  size_t msection_index = 0;
  for (const MediaDescriptionOptions& media_description_options :
       session_options.media_description_options) {
    const ContentInfo* current_content = nullptr;
    if (current_description &&
        msection_index < current_description->contents().size()) {
      current_content = &current_description->contents()[msection_index];
      // Media type must match unless this media section is being recycled.
      RTC_DCHECK(current_content->name != media_description_options.mid ||
                 IsMediaContentOfType(current_content,
                                      media_description_options.type));
    }
    switch (media_description_options.type) {
      case MEDIA_TYPE_AUDIO:
        if (!AddAudioContentForOffer(media_description_options, session_options,
                                     current_content, current_description,
                                     extensions_with_ids.audio,
                                     offer_audio_codecs, &current_streams,
                                     offer.get(), &ice_credentials)) {
          return nullptr;
        }
        break;
      case MEDIA_TYPE_VIDEO:
        if (!AddVideoContentForOffer(media_description_options, session_options,
                                     current_content, current_description,
                                     extensions_with_ids.video,
                                     offer_video_codecs, &current_streams,
                                     offer.get(), &ice_credentials)) {
          return nullptr;
        }
        break;
      case MEDIA_TYPE_DATA:
        if (!AddDataContentForOffer(media_description_options, session_options,
                                    current_content, current_description,
                                    &current_streams, offer.get(),
                                    &ice_credentials)) {
          return nullptr;
        }
        break;
      case MEDIA_TYPE_UNSUPPORTED:
        if (!AddUnsupportedContentForOffer(
                media_description_options, session_options, current_content,
                current_description, offer.get(), &ice_credentials)) {
          return nullptr;
        }
        break;
      default:
        RTC_DCHECK_NOTREACHED();
    }
    ++msection_index;
  }

  // Bundle the contents together, if we've been asked to do so, and update any
  // parameters that need to be tweaked for BUNDLE.
  if (session_options.bundle_enabled) {
    ContentGroup offer_bundle(GROUP_TYPE_BUNDLE);
    for (const ContentInfo& content : offer->contents()) {
      if (content.rejected) {
        continue;
      }
      // TODO(deadbeef): There are conditions that make bundling two media
      // descriptions together illegal. For example, they use the same payload
      // type to represent different codecs, or same IDs for different header
      // extensions. We need to detect this and not try to bundle those media
      // descriptions together.
      offer_bundle.AddContentName(content.name);
    }
    if (!offer_bundle.content_names().empty()) {
      offer->AddGroup(offer_bundle);
      if (!UpdateTransportInfoForBundle(offer_bundle, offer.get())) {
        RTC_LOG(LS_ERROR)
            << "CreateOffer failed to UpdateTransportInfoForBundle.";
        return nullptr;
      }
      if (!UpdateCryptoParamsForBundle(offer_bundle, offer.get())) {
        RTC_LOG(LS_ERROR)
            << "CreateOffer failed to UpdateCryptoParamsForBundle.";
        return nullptr;
      }
    }
  }

  // The following determines how to signal MSIDs to ensure compatibility with
  // older endpoints (in particular, older Plan B endpoints).
  if (is_unified_plan_) {
    // Be conservative and signal using both a=msid and a=ssrc lines. Unified
    // Plan answerers will look at a=msid and Plan B answerers will look at the
    // a=ssrc MSID line.
    offer->set_msid_signaling(cricket::kMsidSignalingMediaSection |
                              cricket::kMsidSignalingSsrcAttribute);
  } else {
    // Plan B always signals MSID using a=ssrc lines.
    offer->set_msid_signaling(cricket::kMsidSignalingSsrcAttribute);
  }

  offer->set_extmap_allow_mixed(session_options.offer_extmap_allow_mixed);

  return offer;
}

std::unique_ptr<SessionDescription>
MediaSessionDescriptionFactory::CreateAnswer(
    const SessionDescription* offer,
    const MediaSessionOptions& session_options,
    const SessionDescription* current_description) const {
  if (!offer) {
    return nullptr;
  }

  // Must have options for exactly as many sections as in the offer.
  RTC_DCHECK_EQ(offer->contents().size(),
                session_options.media_description_options.size());

  IceCredentialsIterator ice_credentials(
      session_options.pooled_ice_credentials);

  std::vector<const ContentInfo*> current_active_contents;
  if (current_description) {
    current_active_contents =
        GetActiveContents(*current_description, session_options);
  }

  StreamParamsVec current_streams =
      GetCurrentStreamParams(current_active_contents);

  // Get list of all possible codecs that respects existing payload type
  // mappings and uses a single payload type space.
  //
  // Note that these lists may be further filtered for each m= section; this
  // step is done just to establish the payload type mappings shared by all
  // sections.
  AudioCodecs answer_audio_codecs;
  VideoCodecs answer_video_codecs;
  GetCodecsForAnswer(current_active_contents, *offer, &answer_audio_codecs,
                     &answer_video_codecs);

  auto answer = std::make_unique<SessionDescription>();

  // If the offer supports BUNDLE, and we want to use it too, create a BUNDLE
  // group in the answer with the appropriate content names.
  std::vector<const ContentGroup*> offer_bundles =
      offer->GetGroupsByName(GROUP_TYPE_BUNDLE);
  // There are as many answer BUNDLE groups as offer BUNDLE groups (even if
  // rejected, we respond with an empty group). `offer_bundles`,
  // `answer_bundles` and `bundle_transports` share the same size and indices.
  std::vector<ContentGroup> answer_bundles;
  std::vector<std::unique_ptr<TransportInfo>> bundle_transports;
  answer_bundles.reserve(offer_bundles.size());
  bundle_transports.reserve(offer_bundles.size());
  for (size_t i = 0; i < offer_bundles.size(); ++i) {
    answer_bundles.emplace_back(GROUP_TYPE_BUNDLE);
    bundle_transports.emplace_back(nullptr);
  }

  answer->set_extmap_allow_mixed(offer->extmap_allow_mixed());

  // Iterate through the media description options, matching with existing
  // media descriptions in `current_description`.
  size_t msection_index = 0;
  for (const MediaDescriptionOptions& media_description_options :
       session_options.media_description_options) {
    const ContentInfo* offer_content = &offer->contents()[msection_index];
    // Media types and MIDs must match between the remote offer and the
    // MediaDescriptionOptions.
    RTC_DCHECK(
        IsMediaContentOfType(offer_content, media_description_options.type));
    RTC_DCHECK(media_description_options.mid == offer_content->name);
    // Get the index of the BUNDLE group that this MID belongs to, if any.
    absl::optional<size_t> bundle_index;
    for (size_t i = 0; i < offer_bundles.size(); ++i) {
      if (offer_bundles[i]->HasContentName(media_description_options.mid)) {
        bundle_index = i;
        break;
      }
    }
    TransportInfo* bundle_transport =
        bundle_index.has_value() ? bundle_transports[bundle_index.value()].get()
                                 : nullptr;

    const ContentInfo* current_content = nullptr;
    if (current_description &&
        msection_index < current_description->contents().size()) {
      current_content = &current_description->contents()[msection_index];
    }
    RtpHeaderExtensions header_extensions = RtpHeaderExtensionsFromCapabilities(
        UnstoppedRtpHeaderExtensionCapabilities(
            media_description_options.header_extensions));
    switch (media_description_options.type) {
      case MEDIA_TYPE_AUDIO:
        if (!AddAudioContentForAnswer(
                media_description_options, session_options, offer_content,
                offer, current_content, current_description, bundle_transport,
                answer_audio_codecs, header_extensions, &current_streams,
                answer.get(), &ice_credentials)) {
          return nullptr;
        }
        break;
      case MEDIA_TYPE_VIDEO:
        if (!AddVideoContentForAnswer(
                media_description_options, session_options, offer_content,
                offer, current_content, current_description, bundle_transport,
                answer_video_codecs, header_extensions, &current_streams,
                answer.get(), &ice_credentials)) {
          return nullptr;
        }
        break;
      case MEDIA_TYPE_DATA:
        if (!AddDataContentForAnswer(
                media_description_options, session_options, offer_content,
                offer, current_content, current_description, bundle_transport,
                &current_streams, answer.get(), &ice_credentials)) {
          return nullptr;
        }
        break;
      case MEDIA_TYPE_UNSUPPORTED:
        if (!AddUnsupportedContentForAnswer(
                media_description_options, session_options, offer_content,
                offer, current_content, current_description, bundle_transport,
                answer.get(), &ice_credentials)) {
          return nullptr;
        }
        break;
      default:
        RTC_DCHECK_NOTREACHED();
    }
    ++msection_index;
    // See if we can add the newly generated m= section to the BUNDLE group in
    // the answer.
    ContentInfo& added = answer->contents().back();
    if (!added.rejected && session_options.bundle_enabled &&
        bundle_index.has_value()) {
      // The `bundle_index` is for `media_description_options.mid`.
      RTC_DCHECK_EQ(media_description_options.mid, added.name);
      answer_bundles[bundle_index.value()].AddContentName(added.name);
      bundle_transports[bundle_index.value()].reset(
          new TransportInfo(*answer->GetTransportInfoByName(added.name)));
    }
  }

  // If BUNDLE group(s) were offered, put the same number of BUNDLE groups in
  // the answer even if they're empty. RFC5888 says:
  //
  //   A SIP entity that receives an offer that contains an "a=group" line
  //   with semantics that are understood MUST return an answer that
  //   contains an "a=group" line with the same semantics.
  if (!offer_bundles.empty()) {
    for (const ContentGroup& answer_bundle : answer_bundles) {
      answer->AddGroup(answer_bundle);

      if (answer_bundle.FirstContentName()) {
        // Share the same ICE credentials and crypto params across all contents,
        // as BUNDLE requires.
        if (!UpdateTransportInfoForBundle(answer_bundle, answer.get())) {
          RTC_LOG(LS_ERROR)
              << "CreateAnswer failed to UpdateTransportInfoForBundle.";
          return NULL;
        }

        if (!UpdateCryptoParamsForBundle(answer_bundle, answer.get())) {
          RTC_LOG(LS_ERROR)
              << "CreateAnswer failed to UpdateCryptoParamsForBundle.";
          return NULL;
        }
      }
    }
  }

  // The following determines how to signal MSIDs to ensure compatibility with
  // older endpoints (in particular, older Plan B endpoints).
  if (is_unified_plan_) {
    // Unified Plan needs to look at what the offer included to find the most
    // compatible answer.
    if (offer->msid_signaling() == 0) {
      // We end up here in one of three cases:
      // 1. An empty offer. We'll reply with an empty answer so it doesn't
      //    matter what we pick here.
      // 2. A data channel only offer. We won't add any MSIDs to the answer so
      //    it also doesn't matter what we pick here.
      // 3. Media that's either sendonly or inactive from the remote endpoint.
      //    We don't have any information to say whether the endpoint is Plan B
      //    or Unified Plan, so be conservative and send both.
      answer->set_msid_signaling(cricket::kMsidSignalingMediaSection |
                                 cricket::kMsidSignalingSsrcAttribute);
    } else if (offer->msid_signaling() ==
               (cricket::kMsidSignalingMediaSection |
                cricket::kMsidSignalingSsrcAttribute)) {
      // If both a=msid and a=ssrc MSID signaling methods were used, we're
      // probably talking to a Unified Plan endpoint so respond with just
      // a=msid.
      answer->set_msid_signaling(cricket::kMsidSignalingMediaSection);
    } else {
      // Otherwise, it's clear which method the offerer is using so repeat that
      // back to them.
      answer->set_msid_signaling(offer->msid_signaling());
    }
  } else {
    // Plan B always signals MSID using a=ssrc lines.
    answer->set_msid_signaling(cricket::kMsidSignalingSsrcAttribute);
  }

  return answer;
}

const AudioCodecs& MediaSessionDescriptionFactory::GetAudioCodecsForOffer(
    const RtpTransceiverDirection& direction) const {
  switch (direction) {
    // If stream is inactive - generate list as if sendrecv.
    case RtpTransceiverDirection::kSendRecv:
    case RtpTransceiverDirection::kStopped:
    case RtpTransceiverDirection::kInactive:
      return audio_sendrecv_codecs_;
    case RtpTransceiverDirection::kSendOnly:
      return audio_send_codecs_;
    case RtpTransceiverDirection::kRecvOnly:
      return audio_recv_codecs_;
  }
  RTC_CHECK_NOTREACHED();
}

const AudioCodecs& MediaSessionDescriptionFactory::GetAudioCodecsForAnswer(
    const RtpTransceiverDirection& offer,
    const RtpTransceiverDirection& answer) const {
  switch (answer) {
    // For inactive and sendrecv answers, generate lists as if we were to accept
    // the offer's direction. See RFC 3264 Section 6.1.
    case RtpTransceiverDirection::kSendRecv:
    case RtpTransceiverDirection::kStopped:
    case RtpTransceiverDirection::kInactive:
      return GetAudioCodecsForOffer(
          webrtc::RtpTransceiverDirectionReversed(offer));
    case RtpTransceiverDirection::kSendOnly:
      return audio_send_codecs_;
    case RtpTransceiverDirection::kRecvOnly:
      return audio_recv_codecs_;
  }
  RTC_CHECK_NOTREACHED();
}

const VideoCodecs& MediaSessionDescriptionFactory::GetVideoCodecsForOffer(
    const RtpTransceiverDirection& direction) const {
  switch (direction) {
    // If stream is inactive - generate list as if sendrecv.
    case RtpTransceiverDirection::kSendRecv:
    case RtpTransceiverDirection::kStopped:
    case RtpTransceiverDirection::kInactive:
      return video_sendrecv_codecs_;
    case RtpTransceiverDirection::kSendOnly:
      return video_send_codecs_;
    case RtpTransceiverDirection::kRecvOnly:
      return video_recv_codecs_;
  }
  RTC_CHECK_NOTREACHED();
}

const VideoCodecs& MediaSessionDescriptionFactory::GetVideoCodecsForAnswer(
    const RtpTransceiverDirection& offer,
    const RtpTransceiverDirection& answer) const {
  switch (answer) {
    // For inactive and sendrecv answers, generate lists as if we were to accept
    // the offer's direction. See RFC 3264 Section 6.1.
    case RtpTransceiverDirection::kSendRecv:
    case RtpTransceiverDirection::kStopped:
    case RtpTransceiverDirection::kInactive:
      return GetVideoCodecsForOffer(
          webrtc::RtpTransceiverDirectionReversed(offer));
    case RtpTransceiverDirection::kSendOnly:
      return video_send_codecs_;
    case RtpTransceiverDirection::kRecvOnly:
      return video_recv_codecs_;
  }
  RTC_CHECK_NOTREACHED();
}

void MergeCodecsFromDescription(
    const std::vector<const ContentInfo*>& current_active_contents,
    AudioCodecs* audio_codecs,
    VideoCodecs* video_codecs,
    UsedPayloadTypes* used_pltypes) {
  for (const ContentInfo* content : current_active_contents) {
    if (IsMediaContentOfType(content, MEDIA_TYPE_AUDIO)) {
      const AudioContentDescription* audio =
          content->media_description()->as_audio();
      MergeCodecs<AudioCodec>(audio->codecs(), audio_codecs, used_pltypes);
    } else if (IsMediaContentOfType(content, MEDIA_TYPE_VIDEO)) {
      const VideoContentDescription* video =
          content->media_description()->as_video();
      MergeCodecs<VideoCodec>(video->codecs(), video_codecs, used_pltypes);
    }
  }
}

// Getting codecs for an offer involves these steps:
//
// 1. Construct payload type -> codec mappings for current description.
// 2. Add any reference codecs that weren't already present
// 3. For each individual media description (m= section), filter codecs based
//    on the directional attribute (happens in another method).
void MediaSessionDescriptionFactory::GetCodecsForOffer(
    const std::vector<const ContentInfo*>& current_active_contents,
    AudioCodecs* audio_codecs,
    VideoCodecs* video_codecs) const {
  // First - get all codecs from the current description if the media type
  // is used. Add them to `used_pltypes` so the payload type is not reused if a
  // new media type is added.
  UsedPayloadTypes used_pltypes;
  MergeCodecsFromDescription(current_active_contents, audio_codecs,
                             video_codecs, &used_pltypes);

  // Add our codecs that are not in the current description.
  MergeCodecs<AudioCodec>(all_audio_codecs_, audio_codecs, &used_pltypes);
  MergeCodecs<VideoCodec>(all_video_codecs_, video_codecs, &used_pltypes);
}

// Getting codecs for an answer involves these steps:
//
// 1. Construct payload type -> codec mappings for current description.
// 2. Add any codecs from the offer that weren't already present.
// 3. Add any remaining codecs that weren't already present.
// 4. For each individual media description (m= section), filter codecs based
//    on the directional attribute (happens in another method).
void MediaSessionDescriptionFactory::GetCodecsForAnswer(
    const std::vector<const ContentInfo*>& current_active_contents,
    const SessionDescription& remote_offer,
    AudioCodecs* audio_codecs,
    VideoCodecs* video_codecs) const {
  // First - get all codecs from the current description if the media type
  // is used. Add them to `used_pltypes` so the payload type is not reused if a
  // new media type is added.
  UsedPayloadTypes used_pltypes;
  MergeCodecsFromDescription(current_active_contents, audio_codecs,
                             video_codecs, &used_pltypes);

  // Second - filter out codecs that we don't support at all and should ignore.
  AudioCodecs filtered_offered_audio_codecs;
  VideoCodecs filtered_offered_video_codecs;
  for (const ContentInfo& content : remote_offer.contents()) {
    if (IsMediaContentOfType(&content, MEDIA_TYPE_AUDIO)) {
      const AudioContentDescription* audio =
          content.media_description()->as_audio();
      for (const AudioCodec& offered_audio_codec : audio->codecs()) {
        if (!FindMatchingCodec<AudioCodec>(audio->codecs(),
                                           filtered_offered_audio_codecs,
                                           offered_audio_codec, nullptr) &&
            FindMatchingCodec<AudioCodec>(audio->codecs(), all_audio_codecs_,
                                          offered_audio_codec, nullptr)) {
          filtered_offered_audio_codecs.push_back(offered_audio_codec);
        }
      }
    } else if (IsMediaContentOfType(&content, MEDIA_TYPE_VIDEO)) {
      const VideoContentDescription* video =
          content.media_description()->as_video();
      for (const VideoCodec& offered_video_codec : video->codecs()) {
        if (!FindMatchingCodec<VideoCodec>(video->codecs(),
                                           filtered_offered_video_codecs,
                                           offered_video_codec, nullptr) &&
            FindMatchingCodec<VideoCodec>(video->codecs(), all_video_codecs_,
                                          offered_video_codec, nullptr)) {
          filtered_offered_video_codecs.push_back(offered_video_codec);
        }
      }
    }
  }

  // Add codecs that are not in the current description but were in
  // `remote_offer`.
  MergeCodecs<AudioCodec>(filtered_offered_audio_codecs, audio_codecs,
                          &used_pltypes);
  MergeCodecs<VideoCodec>(filtered_offered_video_codecs, video_codecs,
                          &used_pltypes);
}

MediaSessionDescriptionFactory::AudioVideoRtpHeaderExtensions
MediaSessionDescriptionFactory::GetOfferedRtpHeaderExtensionsWithIds(
    const std::vector<const ContentInfo*>& current_active_contents,
    bool extmap_allow_mixed,
    const std::vector<MediaDescriptionOptions>& media_description_options)
    const {
  // All header extensions allocated from the same range to avoid potential
  // issues when using BUNDLE.

  // Strictly speaking the SDP attribute extmap_allow_mixed signals that the
  // receiver supports an RTP stream where one- and two-byte RTP header
  // extensions are mixed. For backwards compatibility reasons it's used in
  // WebRTC to signal that two-byte RTP header extensions are supported.
  UsedRtpHeaderExtensionIds used_ids(
      extmap_allow_mixed ? UsedRtpHeaderExtensionIds::IdDomain::kTwoByteAllowed
                         : UsedRtpHeaderExtensionIds::IdDomain::kOneByteOnly);
  RtpHeaderExtensions all_regular_extensions;
  RtpHeaderExtensions all_encrypted_extensions;

  AudioVideoRtpHeaderExtensions offered_extensions;
  // First - get all extensions from the current description if the media type
  // is used.
  // Add them to `used_ids` so the local ids are not reused if a new media
  // type is added.
  for (const ContentInfo* content : current_active_contents) {
    if (IsMediaContentOfType(content, MEDIA_TYPE_AUDIO)) {
      const AudioContentDescription* audio =
          content->media_description()->as_audio();
      MergeRtpHdrExts(audio->rtp_header_extensions(), &offered_extensions.audio,
                      &all_regular_extensions, &all_encrypted_extensions,
                      &used_ids);
    } else if (IsMediaContentOfType(content, MEDIA_TYPE_VIDEO)) {
      const VideoContentDescription* video =
          content->media_description()->as_video();
      MergeRtpHdrExts(video->rtp_header_extensions(), &offered_extensions.video,
                      &all_regular_extensions, &all_encrypted_extensions,
                      &used_ids);
    }
  }

  // Add all encountered header extensions in the media description options that
  // are not in the current description.

  for (const auto& entry : media_description_options) {
    RtpHeaderExtensions filtered_extensions =
        filtered_rtp_header_extensions(UnstoppedOrPresentRtpHeaderExtensions(
            entry.header_extensions, all_regular_extensions,
            all_encrypted_extensions));
    if (entry.type == MEDIA_TYPE_AUDIO)
      MergeRtpHdrExts(filtered_extensions, &offered_extensions.audio,
                      &all_regular_extensions, &all_encrypted_extensions,
                      &used_ids);
    else if (entry.type == MEDIA_TYPE_VIDEO)
      MergeRtpHdrExts(filtered_extensions, &offered_extensions.video,
                      &all_regular_extensions, &all_encrypted_extensions,
                      &used_ids);
  }
  // TODO(jbauch): Support adding encrypted header extensions to existing
  // sessions.
  if (enable_encrypted_rtp_header_extensions_ &&
      current_active_contents.empty()) {
    AddEncryptedVersionsOfHdrExts(&offered_extensions.audio,
                                  &all_encrypted_extensions, &used_ids);
    AddEncryptedVersionsOfHdrExts(&offered_extensions.video,
                                  &all_encrypted_extensions, &used_ids);
  }
  return offered_extensions;
}

bool MediaSessionDescriptionFactory::AddTransportOffer(
    const std::string& content_name,
    const TransportOptions& transport_options,
    const SessionDescription* current_desc,
    SessionDescription* offer_desc,
    IceCredentialsIterator* ice_credentials) const {
  if (!transport_desc_factory_)
    return false;
  const TransportDescription* current_tdesc =
      GetTransportDescription(content_name, current_desc);
  std::unique_ptr<TransportDescription> new_tdesc(
      transport_desc_factory_->CreateOffer(transport_options, current_tdesc,
                                           ice_credentials));
  if (!new_tdesc) {
    RTC_LOG(LS_ERROR) << "Failed to AddTransportOffer, content name="
                      << content_name;
  }
  offer_desc->AddTransportInfo(TransportInfo(content_name, *new_tdesc));
  return true;
}

std::unique_ptr<TransportDescription>
MediaSessionDescriptionFactory::CreateTransportAnswer(
    const std::string& content_name,
    const SessionDescription* offer_desc,
    const TransportOptions& transport_options,
    const SessionDescription* current_desc,
    bool require_transport_attributes,
    IceCredentialsIterator* ice_credentials) const {
  if (!transport_desc_factory_)
    return NULL;
  const TransportDescription* offer_tdesc =
      GetTransportDescription(content_name, offer_desc);
  const TransportDescription* current_tdesc =
      GetTransportDescription(content_name, current_desc);
  return transport_desc_factory_->CreateAnswer(offer_tdesc, transport_options,
                                               require_transport_attributes,
                                               current_tdesc, ice_credentials);
}

bool MediaSessionDescriptionFactory::AddTransportAnswer(
    const std::string& content_name,
    const TransportDescription& transport_desc,
    SessionDescription* answer_desc) const {
  answer_desc->AddTransportInfo(TransportInfo(content_name, transport_desc));
  return true;
}

// `audio_codecs` = set of all possible codecs that can be used, with correct
// payload type mappings
//
// `supported_audio_codecs` = set of codecs that are supported for the direction
// of this m= section
//
// acd->codecs() = set of previously negotiated codecs for this m= section
//
// The payload types should come from audio_codecs, but the order should come
// from acd->codecs() and then supported_codecs, to ensure that re-offers don't
// change existing codec priority, and that new codecs are added with the right
// priority.
bool MediaSessionDescriptionFactory::AddAudioContentForOffer(
    const MediaDescriptionOptions& media_description_options,
    const MediaSessionOptions& session_options,
    const ContentInfo* current_content,
    const SessionDescription* current_description,
    const RtpHeaderExtensions& audio_rtp_extensions,
    const AudioCodecs& audio_codecs,
    StreamParamsVec* current_streams,
    SessionDescription* desc,
    IceCredentialsIterator* ice_credentials) const {
  // Filter audio_codecs (which includes all codecs, with correctly remapped
  // payload types) based on transceiver direction.
  const AudioCodecs& supported_audio_codecs =
      GetAudioCodecsForOffer(media_description_options.direction);

  AudioCodecs filtered_codecs;

  if (!media_description_options.codec_preferences.empty()) {
    // Add the codecs from the current transceiver's codec preferences.
    // They override any existing codecs from previous negotiations.
    filtered_codecs =
        MatchCodecPreference(media_description_options.codec_preferences,
                             audio_codecs, supported_audio_codecs);
  } else {
    // Add the codecs from current content if it exists and is not rejected nor
    // recycled.
    if (current_content && !current_content->rejected &&
        current_content->name == media_description_options.mid) {
      RTC_CHECK(IsMediaContentOfType(current_content, MEDIA_TYPE_AUDIO));
      const AudioContentDescription* acd =
          current_content->media_description()->as_audio();
      for (const AudioCodec& codec : acd->codecs()) {
        if (FindMatchingCodec<AudioCodec>(acd->codecs(), audio_codecs, codec,
                                          nullptr)) {
          filtered_codecs.push_back(codec);
        }
      }
    }
    // Add other supported audio codecs.
    AudioCodec found_codec;
    for (const AudioCodec& codec : supported_audio_codecs) {
      if (FindMatchingCodec<AudioCodec>(supported_audio_codecs, audio_codecs,
                                        codec, &found_codec) &&
          !FindMatchingCodec<AudioCodec>(supported_audio_codecs,
                                         filtered_codecs, codec, nullptr)) {
        // Use the `found_codec` from `audio_codecs` because it has the
        // correctly mapped payload type.
        filtered_codecs.push_back(found_codec);
      }
    }
  }
  if (!session_options.vad_enabled) {
    // If application doesn't want CN codecs in offer.
    StripCNCodecs(&filtered_codecs);
  }

  cricket::SecurePolicy sdes_policy =
      IsDtlsActive(current_content, current_description) ? cricket::SEC_DISABLED
                                                         : secure();

  auto audio = std::make_unique<AudioContentDescription>();
  std::vector<std::string> crypto_suites;
  GetSupportedAudioSdesCryptoSuiteNames(session_options.crypto_options,
                                        &crypto_suites);
  if (!CreateMediaContentOffer(media_description_options, session_options,
                               filtered_codecs, sdes_policy,
                               GetCryptos(current_content), crypto_suites,
                               audio_rtp_extensions, ssrc_generator_,
                               current_streams, audio.get())) {
    return false;
  }

  bool secure_transport = (transport_desc_factory_->secure() != SEC_DISABLED);
  SetMediaProtocol(secure_transport, audio.get());

  audio->set_direction(media_description_options.direction);

  desc->AddContent(media_description_options.mid, MediaProtocolType::kRtp,
                   media_description_options.stopped, std::move(audio));
  if (!AddTransportOffer(media_description_options.mid,
                         media_description_options.transport_options,
                         current_description, desc, ice_credentials)) {
    return false;
  }

  return true;
}

// TODO(kron): This function is very similar to AddAudioContentForOffer.
// Refactor to reuse shared code.
bool MediaSessionDescriptionFactory::AddVideoContentForOffer(
    const MediaDescriptionOptions& media_description_options,
    const MediaSessionOptions& session_options,
    const ContentInfo* current_content,
    const SessionDescription* current_description,
    const RtpHeaderExtensions& video_rtp_extensions,
    const VideoCodecs& video_codecs,
    StreamParamsVec* current_streams,
    SessionDescription* desc,
    IceCredentialsIterator* ice_credentials) const {
  // Filter video_codecs (which includes all codecs, with correctly remapped
  // payload types) based on transceiver direction.
  const VideoCodecs& supported_video_codecs =
      GetVideoCodecsForOffer(media_description_options.direction);

  VideoCodecs filtered_codecs;

  if (!media_description_options.codec_preferences.empty()) {
    // Add the codecs from the current transceiver's codec preferences.
    // They override any existing codecs from previous negotiations.
    filtered_codecs =
        MatchCodecPreference(media_description_options.codec_preferences,
                             video_codecs, supported_video_codecs);
  } else {
    // Add the codecs from current content if it exists and is not rejected nor
    // recycled.
    if (current_content && !current_content->rejected &&
        current_content->name == media_description_options.mid) {
      RTC_CHECK(IsMediaContentOfType(current_content, MEDIA_TYPE_VIDEO));
      const VideoContentDescription* vcd =
          current_content->media_description()->as_video();
      for (const VideoCodec& codec : vcd->codecs()) {
        if (FindMatchingCodec<VideoCodec>(vcd->codecs(), video_codecs, codec,
                                          nullptr)) {
          filtered_codecs.push_back(codec);
        }
      }
    }
    // Add other supported video codecs.
    VideoCodec found_codec;
    for (const VideoCodec& codec : supported_video_codecs) {
      if (FindMatchingCodec<VideoCodec>(supported_video_codecs, video_codecs,
                                        codec, &found_codec) &&
          !FindMatchingCodec<VideoCodec>(supported_video_codecs,
                                         filtered_codecs, codec, nullptr)) {
        // Use the `found_codec` from `video_codecs` because it has the
        // correctly mapped payload type.
        if (IsRtxCodec(codec)) {
          // For RTX we might need to adjust the apt parameter if we got a
          // remote offer without RTX for a codec for which we support RTX.
          auto referenced_codec =
              GetAssociatedCodecForRtx(supported_video_codecs, codec);
          RTC_DCHECK(referenced_codec);

          // Find the codec we should be referencing and point to it.
          VideoCodec changed_referenced_codec;
          if (FindMatchingCodec<VideoCodec>(supported_video_codecs,
                                            filtered_codecs, *referenced_codec,
                                            &changed_referenced_codec)) {
            found_codec.SetParam(kCodecParamAssociatedPayloadType,
                                 changed_referenced_codec.id);
          }
        }
        filtered_codecs.push_back(found_codec);
      }
    }
  }

  if (session_options.raw_packetization_for_video) {
    for (VideoCodec& codec : filtered_codecs) {
      if (codec.GetCodecType() == VideoCodec::CODEC_VIDEO) {
        codec.packetization = kPacketizationParamRaw;
      }
    }
  }

  cricket::SecurePolicy sdes_policy =
      IsDtlsActive(current_content, current_description) ? cricket::SEC_DISABLED
                                                         : secure();
  auto video = std::make_unique<VideoContentDescription>();
  std::vector<std::string> crypto_suites;
  GetSupportedVideoSdesCryptoSuiteNames(session_options.crypto_options,
                                        &crypto_suites);
  if (!CreateMediaContentOffer(media_description_options, session_options,
                               filtered_codecs, sdes_policy,
                               GetCryptos(current_content), crypto_suites,
                               video_rtp_extensions, ssrc_generator_,
                               current_streams, video.get())) {
    return false;
  }

  video->set_bandwidth(kAutoBandwidth);

  bool secure_transport = (transport_desc_factory_->secure() != SEC_DISABLED);
  SetMediaProtocol(secure_transport, video.get());

  video->set_direction(media_description_options.direction);

  desc->AddContent(media_description_options.mid, MediaProtocolType::kRtp,
                   media_description_options.stopped, std::move(video));
  if (!AddTransportOffer(media_description_options.mid,
                         media_description_options.transport_options,
                         current_description, desc, ice_credentials)) {
    return false;
  }

  return true;
}

bool MediaSessionDescriptionFactory::AddDataContentForOffer(
    const MediaDescriptionOptions& media_description_options,
    const MediaSessionOptions& session_options,
    const ContentInfo* current_content,
    const SessionDescription* current_description,
    StreamParamsVec* current_streams,
    SessionDescription* desc,
    IceCredentialsIterator* ice_credentials) const {
  auto data = std::make_unique<SctpDataContentDescription>();

  bool secure_transport = (transport_desc_factory_->secure() != SEC_DISABLED);

  cricket::SecurePolicy sdes_policy =
      IsDtlsActive(current_content, current_description) ? cricket::SEC_DISABLED
                                                         : secure();
  std::vector<std::string> crypto_suites;
  // SDES doesn't make sense for SCTP, so we disable it, and we only
  // get SDES crypto suites for RTP-based data channels.
  sdes_policy = cricket::SEC_DISABLED;
  // Unlike SetMediaProtocol below, we need to set the protocol
  // before we call CreateMediaContentOffer.  Otherwise,
  // CreateMediaContentOffer won't know this is SCTP and will
  // generate SSRCs rather than SIDs.
  data->set_protocol(secure_transport ? kMediaProtocolUdpDtlsSctp
                                      : kMediaProtocolSctp);
  data->set_use_sctpmap(session_options.use_obsolete_sctp_sdp);
  data->set_max_message_size(kSctpSendBufferSize);

  if (!CreateContentOffer(media_description_options, session_options,
                          sdes_policy, GetCryptos(current_content),
                          crypto_suites, RtpHeaderExtensions(), ssrc_generator_,
                          current_streams, data.get())) {
    return false;
  }

  desc->AddContent(media_description_options.mid, MediaProtocolType::kSctp,
                   media_description_options.stopped, std::move(data));
  if (!AddTransportOffer(media_description_options.mid,
                         media_description_options.transport_options,
                         current_description, desc, ice_credentials)) {
    return false;
  }
  return true;
}

bool MediaSessionDescriptionFactory::AddUnsupportedContentForOffer(
    const MediaDescriptionOptions& media_description_options,
    const MediaSessionOptions& session_options,
    const ContentInfo* current_content,
    const SessionDescription* current_description,
    SessionDescription* desc,
    IceCredentialsIterator* ice_credentials) const {
  RTC_CHECK(IsMediaContentOfType(current_content, MEDIA_TYPE_UNSUPPORTED));

  const UnsupportedContentDescription* current_unsupported_description =
      current_content->media_description()->as_unsupported();
  auto unsupported = std::make_unique<UnsupportedContentDescription>(
      current_unsupported_description->media_type());
  unsupported->set_protocol(current_content->media_description()->protocol());
  desc->AddContent(media_description_options.mid, MediaProtocolType::kOther,
                   /*rejected=*/true, std::move(unsupported));

  if (!AddTransportOffer(media_description_options.mid,
                         media_description_options.transport_options,
                         current_description, desc, ice_credentials)) {
    return false;
  }
  return true;
}

// `audio_codecs` = set of all possible codecs that can be used, with correct
// payload type mappings
//
// `supported_audio_codecs` = set of codecs that are supported for the direction
// of this m= section
//
// acd->codecs() = set of previously negotiated codecs for this m= section
//
// The payload types should come from audio_codecs, but the order should come
// from acd->codecs() and then supported_codecs, to ensure that re-offers don't
// change existing codec priority, and that new codecs are added with the right
// priority.
bool MediaSessionDescriptionFactory::AddAudioContentForAnswer(
    const MediaDescriptionOptions& media_description_options,
    const MediaSessionOptions& session_options,
    const ContentInfo* offer_content,
    const SessionDescription* offer_description,
    const ContentInfo* current_content,
    const SessionDescription* current_description,
    const TransportInfo* bundle_transport,
    const AudioCodecs& audio_codecs,
    const RtpHeaderExtensions& default_audio_rtp_header_extensions,
    StreamParamsVec* current_streams,
    SessionDescription* answer,
    IceCredentialsIterator* ice_credentials) const {
  RTC_CHECK(IsMediaContentOfType(offer_content, MEDIA_TYPE_AUDIO));
  const AudioContentDescription* offer_audio_description =
      offer_content->media_description()->as_audio();

  std::unique_ptr<TransportDescription> audio_transport = CreateTransportAnswer(
      media_description_options.mid, offer_description,
      media_description_options.transport_options, current_description,
      bundle_transport != nullptr, ice_credentials);
  if (!audio_transport) {
    return false;
  }

  // Pick codecs based on the requested communications direction in the offer
  // and the selected direction in the answer.
  // Note these will be filtered one final time in CreateMediaContentAnswer.
  auto wants_rtd = media_description_options.direction;
  auto offer_rtd = offer_audio_description->direction();
  auto answer_rtd = NegotiateRtpTransceiverDirection(offer_rtd, wants_rtd);
  AudioCodecs supported_audio_codecs =
      GetAudioCodecsForAnswer(offer_rtd, answer_rtd);

  AudioCodecs filtered_codecs;

  if (!media_description_options.codec_preferences.empty()) {
    filtered_codecs =
        MatchCodecPreference(media_description_options.codec_preferences,
                             audio_codecs, supported_audio_codecs);
  } else {
    // Add the codecs from current content if it exists and is not rejected nor
    // recycled.
    if (current_content && !current_content->rejected &&
        current_content->name == media_description_options.mid) {
      RTC_CHECK(IsMediaContentOfType(current_content, MEDIA_TYPE_AUDIO));
      const AudioContentDescription* acd =
          current_content->media_description()->as_audio();
      for (const AudioCodec& codec : acd->codecs()) {
        if (FindMatchingCodec<AudioCodec>(acd->codecs(), audio_codecs, codec,
                                          nullptr)) {
          filtered_codecs.push_back(codec);
        }
      }
    }
    // Add other supported audio codecs.
    for (const AudioCodec& codec : supported_audio_codecs) {
      if (FindMatchingCodec<AudioCodec>(supported_audio_codecs, audio_codecs,
                                        codec, nullptr) &&
          !FindMatchingCodec<AudioCodec>(supported_audio_codecs,
                                         filtered_codecs, codec, nullptr)) {
        // We should use the local codec with local parameters and the codec id
        // would be correctly mapped in `NegotiateCodecs`.
        filtered_codecs.push_back(codec);
      }
    }
  }
  if (!session_options.vad_enabled) {
    // If application doesn't want CN codecs in answer.
    StripCNCodecs(&filtered_codecs);
  }

  bool bundle_enabled = offer_description->HasGroup(GROUP_TYPE_BUNDLE) &&
                        session_options.bundle_enabled;
  auto audio_answer = std::make_unique<AudioContentDescription>();
  // Do not require or create SDES cryptos if DTLS is used.
  cricket::SecurePolicy sdes_policy =
      audio_transport->secure() ? cricket::SEC_DISABLED : secure();
  if (!SetCodecsInAnswer(offer_audio_description, filtered_codecs,
                         media_description_options, session_options,
                         ssrc_generator_, current_streams,
                         audio_answer.get())) {
    return false;
  }
  if (!CreateMediaContentAnswer(
          offer_audio_description, media_description_options, session_options,
          sdes_policy, GetCryptos(current_content),
          filtered_rtp_header_extensions(default_audio_rtp_header_extensions),
          ssrc_generator_, enable_encrypted_rtp_header_extensions_,
          current_streams, bundle_enabled, audio_answer.get())) {
    return false;  // Fails the session setup.
  }

  bool secure = bundle_transport ? bundle_transport->description.secure()
                                 : audio_transport->secure();
  bool rejected = media_description_options.stopped ||
                  offer_content->rejected ||
                  !IsMediaProtocolSupported(MEDIA_TYPE_AUDIO,
                                            audio_answer->protocol(), secure);
  if (!AddTransportAnswer(media_description_options.mid,
                          *(audio_transport.get()), answer)) {
    return false;
  }

  if (rejected) {
    RTC_LOG(LS_INFO) << "Audio m= section '" << media_description_options.mid
                     << "' being rejected in answer.";
  }

  answer->AddContent(media_description_options.mid, offer_content->type,
                     rejected, std::move(audio_answer));
  return true;
}

// TODO(kron): This function is very similar to AddAudioContentForAnswer.
// Refactor to reuse shared code.
bool MediaSessionDescriptionFactory::AddVideoContentForAnswer(
    const MediaDescriptionOptions& media_description_options,
    const MediaSessionOptions& session_options,
    const ContentInfo* offer_content,
    const SessionDescription* offer_description,
    const ContentInfo* current_content,
    const SessionDescription* current_description,
    const TransportInfo* bundle_transport,
    const VideoCodecs& video_codecs,
    const RtpHeaderExtensions& default_video_rtp_header_extensions,
    StreamParamsVec* current_streams,
    SessionDescription* answer,
    IceCredentialsIterator* ice_credentials) const {
  RTC_CHECK(IsMediaContentOfType(offer_content, MEDIA_TYPE_VIDEO));
  const VideoContentDescription* offer_video_description =
      offer_content->media_description()->as_video();

  std::unique_ptr<TransportDescription> video_transport = CreateTransportAnswer(
      media_description_options.mid, offer_description,
      media_description_options.transport_options, current_description,
      bundle_transport != nullptr, ice_credentials);
  if (!video_transport) {
    return false;
  }

  // Pick codecs based on the requested communications direction in the offer
  // and the selected direction in the answer.
  // Note these will be filtered one final time in CreateMediaContentAnswer.
  auto wants_rtd = media_description_options.direction;
  auto offer_rtd = offer_video_description->direction();
  auto answer_rtd = NegotiateRtpTransceiverDirection(offer_rtd, wants_rtd);
  VideoCodecs supported_video_codecs =
      GetVideoCodecsForAnswer(offer_rtd, answer_rtd);

  VideoCodecs filtered_codecs;

  if (!media_description_options.codec_preferences.empty()) {
    filtered_codecs =
        MatchCodecPreference(media_description_options.codec_preferences,
                             video_codecs, supported_video_codecs);
  } else {
    // Add the codecs from current content if it exists and is not rejected nor
    // recycled.
    if (current_content && !current_content->rejected &&
        current_content->name == media_description_options.mid) {
      RTC_CHECK(IsMediaContentOfType(current_content, MEDIA_TYPE_VIDEO));
      const VideoContentDescription* vcd =
          current_content->media_description()->as_video();
      for (const VideoCodec& codec : vcd->codecs()) {
        if (FindMatchingCodec<VideoCodec>(vcd->codecs(), video_codecs, codec,
                                          nullptr)) {
          filtered_codecs.push_back(codec);
        }
      }
    }

    // Add other supported video codecs.
    VideoCodecs other_video_codecs;
    for (const VideoCodec& codec : supported_video_codecs) {
      if (FindMatchingCodec<VideoCodec>(supported_video_codecs, video_codecs,
                                        codec, nullptr) &&
          !FindMatchingCodec<VideoCodec>(supported_video_codecs,
                                         filtered_codecs, codec, nullptr)) {
        // We should use the local codec with local parameters and the codec id
        // would be correctly mapped in `NegotiateCodecs`.
        other_video_codecs.push_back(codec);
      }
    }

    // Use ComputeCodecsUnion to avoid having duplicate payload IDs
    filtered_codecs =
        ComputeCodecsUnion<VideoCodec>(filtered_codecs, other_video_codecs);
  }

  if (session_options.raw_packetization_for_video) {
    for (VideoCodec& codec : filtered_codecs) {
      if (codec.GetCodecType() == VideoCodec::CODEC_VIDEO) {
        codec.packetization = kPacketizationParamRaw;
      }
    }
  }

  bool bundle_enabled = offer_description->HasGroup(GROUP_TYPE_BUNDLE) &&
                        session_options.bundle_enabled;
  auto video_answer = std::make_unique<VideoContentDescription>();
  // Do not require or create SDES cryptos if DTLS is used.
  cricket::SecurePolicy sdes_policy =
      video_transport->secure() ? cricket::SEC_DISABLED : secure();
  if (!SetCodecsInAnswer(offer_video_description, filtered_codecs,
                         media_description_options, session_options,
                         ssrc_generator_, current_streams,
                         video_answer.get())) {
    return false;
  }
  if (!CreateMediaContentAnswer(
          offer_video_description, media_description_options, session_options,
          sdes_policy, GetCryptos(current_content),
          filtered_rtp_header_extensions(default_video_rtp_header_extensions),
          ssrc_generator_, enable_encrypted_rtp_header_extensions_,
          current_streams, bundle_enabled, video_answer.get())) {
    return false;  // Failed the sessin setup.
  }
  bool secure = bundle_transport ? bundle_transport->description.secure()
                                 : video_transport->secure();
  bool rejected = media_description_options.stopped ||
                  offer_content->rejected ||
                  !IsMediaProtocolSupported(MEDIA_TYPE_VIDEO,
                                            video_answer->protocol(), secure);
  if (!AddTransportAnswer(media_description_options.mid,
                          *(video_transport.get()), answer)) {
    return false;
  }

  if (!rejected) {
    video_answer->set_bandwidth(kAutoBandwidth);
  } else {
    RTC_LOG(LS_INFO) << "Video m= section '" << media_description_options.mid
                     << "' being rejected in answer.";
  }
  answer->AddContent(media_description_options.mid, offer_content->type,
                     rejected, std::move(video_answer));
  return true;
}

bool MediaSessionDescriptionFactory::AddDataContentForAnswer(
    const MediaDescriptionOptions& media_description_options,
    const MediaSessionOptions& session_options,
    const ContentInfo* offer_content,
    const SessionDescription* offer_description,
    const ContentInfo* current_content,
    const SessionDescription* current_description,
    const TransportInfo* bundle_transport,
    StreamParamsVec* current_streams,
    SessionDescription* answer,
    IceCredentialsIterator* ice_credentials) const {
  std::unique_ptr<TransportDescription> data_transport = CreateTransportAnswer(
      media_description_options.mid, offer_description,
      media_description_options.transport_options, current_description,
      bundle_transport != nullptr, ice_credentials);
  if (!data_transport) {
    return false;
  }

  // Do not require or create SDES cryptos if DTLS is used.
  cricket::SecurePolicy sdes_policy =
      data_transport->secure() ? cricket::SEC_DISABLED : secure();
  bool bundle_enabled = offer_description->HasGroup(GROUP_TYPE_BUNDLE) &&
                        session_options.bundle_enabled;
  RTC_CHECK(IsMediaContentOfType(offer_content, MEDIA_TYPE_DATA));
  std::unique_ptr<MediaContentDescription> data_answer;
  if (offer_content->media_description()->as_sctp()) {
    // SCTP data content
    data_answer = std::make_unique<SctpDataContentDescription>();
    const SctpDataContentDescription* offer_data_description =
        offer_content->media_description()->as_sctp();
    // Respond with the offerer's proto, whatever it is.
    data_answer->as_sctp()->set_protocol(offer_data_description->protocol());
    // Respond with our max message size or the remote max messsage size,
    // whichever is smaller.
    // 0 is treated specially - it means "I can accept any size". Since
    // we do not implement infinite size messages, reply with
    // kSctpSendBufferSize.
    if (offer_data_description->max_message_size() == 0) {
      data_answer->as_sctp()->set_max_message_size(kSctpSendBufferSize);
    } else {
      data_answer->as_sctp()->set_max_message_size(std::min(
          offer_data_description->max_message_size(), kSctpSendBufferSize));
    }
    if (!CreateMediaContentAnswer(
            offer_data_description, media_description_options, session_options,
            sdes_policy, GetCryptos(current_content), RtpHeaderExtensions(),
            ssrc_generator_, enable_encrypted_rtp_header_extensions_,
            current_streams, bundle_enabled, data_answer.get())) {
      return false;  // Fails the session setup.
    }
    // Respond with sctpmap if the offer uses sctpmap.
    bool offer_uses_sctpmap = offer_data_description->use_sctpmap();
    data_answer->as_sctp()->set_use_sctpmap(offer_uses_sctpmap);
  } else {
    RTC_DCHECK_NOTREACHED() << "Non-SCTP data content found";
  }

  bool secure = bundle_transport ? bundle_transport->description.secure()
                                 : data_transport->secure();

  bool rejected = media_description_options.stopped ||
                  offer_content->rejected ||
                  !IsMediaProtocolSupported(MEDIA_TYPE_DATA,
                                            data_answer->protocol(), secure);
  if (!AddTransportAnswer(media_description_options.mid,
                          *(data_transport.get()), answer)) {
    return false;
  }

  answer->AddContent(media_description_options.mid, offer_content->type,
                     rejected, std::move(data_answer));
  return true;
}

bool MediaSessionDescriptionFactory::AddUnsupportedContentForAnswer(
    const MediaDescriptionOptions& media_description_options,
    const MediaSessionOptions& session_options,
    const ContentInfo* offer_content,
    const SessionDescription* offer_description,
    const ContentInfo* current_content,
    const SessionDescription* current_description,
    const TransportInfo* bundle_transport,
    SessionDescription* answer,
    IceCredentialsIterator* ice_credentials) const {
  std::unique_ptr<TransportDescription> unsupported_transport =
      CreateTransportAnswer(media_description_options.mid, offer_description,
                            media_description_options.transport_options,
                            current_description, bundle_transport != nullptr,
                            ice_credentials);
  if (!unsupported_transport) {
    return false;
  }
  RTC_CHECK(IsMediaContentOfType(offer_content, MEDIA_TYPE_UNSUPPORTED));

  const UnsupportedContentDescription* offer_unsupported_description =
      offer_content->media_description()->as_unsupported();
  std::unique_ptr<MediaContentDescription> unsupported_answer =
      std::make_unique<UnsupportedContentDescription>(
          offer_unsupported_description->media_type());
  unsupported_answer->set_protocol(offer_unsupported_description->protocol());

  if (!AddTransportAnswer(media_description_options.mid,
                          *(unsupported_transport.get()), answer)) {
    return false;
  }
  answer->AddContent(media_description_options.mid, offer_content->type,
                     /*rejected=*/true, std::move(unsupported_answer));
  return true;
}

void MediaSessionDescriptionFactory::ComputeAudioCodecsIntersectionAndUnion() {
  audio_sendrecv_codecs_.clear();
  all_audio_codecs_.clear();
  // Compute the audio codecs union.
  for (const AudioCodec& send : audio_send_codecs_) {
    all_audio_codecs_.push_back(send);
    if (!FindMatchingCodec<AudioCodec>(audio_send_codecs_, audio_recv_codecs_,
                                       send, nullptr)) {
      // It doesn't make sense to have an RTX codec we support sending but not
      // receiving.
      RTC_DCHECK(!IsRtxCodec(send));
    }
  }
  for (const AudioCodec& recv : audio_recv_codecs_) {
    if (!FindMatchingCodec<AudioCodec>(audio_recv_codecs_, audio_send_codecs_,
                                       recv, nullptr)) {
      all_audio_codecs_.push_back(recv);
    }
  }
  // Use NegotiateCodecs to merge our codec lists, since the operation is
  // essentially the same. Put send_codecs as the offered_codecs, which is the
  // order we'd like to follow. The reasoning is that encoding is usually more
  // expensive than decoding, and prioritizing a codec in the send list probably
  // means it's a codec we can handle efficiently.
  NegotiateCodecs(audio_recv_codecs_, audio_send_codecs_,
                  &audio_sendrecv_codecs_, true);
}

void MediaSessionDescriptionFactory::ComputeVideoCodecsIntersectionAndUnion() {
  video_sendrecv_codecs_.clear();

  // Use ComputeCodecsUnion to avoid having duplicate payload IDs
  all_video_codecs_ =
      ComputeCodecsUnion(video_recv_codecs_, video_send_codecs_);

  // Use NegotiateCodecs to merge our codec lists, since the operation is
  // essentially the same. Put send_codecs as the offered_codecs, which is the
  // order we'd like to follow. The reasoning is that encoding is usually more
  // expensive than decoding, and prioritizing a codec in the send list probably
  // means it's a codec we can handle efficiently.
  NegotiateCodecs(video_recv_codecs_, video_send_codecs_,
                  &video_sendrecv_codecs_, true);
}

bool IsMediaContent(const ContentInfo* content) {
  return (content && (content->type == MediaProtocolType::kRtp ||
                      content->type == MediaProtocolType::kSctp));
}

bool IsAudioContent(const ContentInfo* content) {
  return IsMediaContentOfType(content, MEDIA_TYPE_AUDIO);
}

bool IsVideoContent(const ContentInfo* content) {
  return IsMediaContentOfType(content, MEDIA_TYPE_VIDEO);
}

bool IsDataContent(const ContentInfo* content) {
  return IsMediaContentOfType(content, MEDIA_TYPE_DATA);
}

bool IsUnsupportedContent(const ContentInfo* content) {
  return IsMediaContentOfType(content, MEDIA_TYPE_UNSUPPORTED);
}

const ContentInfo* GetFirstMediaContent(const ContentInfos& contents,
                                        MediaType media_type) {
  for (const ContentInfo& content : contents) {
    if (IsMediaContentOfType(&content, media_type)) {
      return &content;
    }
  }
  return nullptr;
}

const ContentInfo* GetFirstAudioContent(const ContentInfos& contents) {
  return GetFirstMediaContent(contents, MEDIA_TYPE_AUDIO);
}

const ContentInfo* GetFirstVideoContent(const ContentInfos& contents) {
  return GetFirstMediaContent(contents, MEDIA_TYPE_VIDEO);
}

const ContentInfo* GetFirstDataContent(const ContentInfos& contents) {
  return GetFirstMediaContent(contents, MEDIA_TYPE_DATA);
}

const ContentInfo* GetFirstMediaContent(const SessionDescription* sdesc,
                                        MediaType media_type) {
  if (sdesc == nullptr) {
    return nullptr;
  }

  return GetFirstMediaContent(sdesc->contents(), media_type);
}

const ContentInfo* GetFirstAudioContent(const SessionDescription* sdesc) {
  return GetFirstMediaContent(sdesc, MEDIA_TYPE_AUDIO);
}

const ContentInfo* GetFirstVideoContent(const SessionDescription* sdesc) {
  return GetFirstMediaContent(sdesc, MEDIA_TYPE_VIDEO);
}

const ContentInfo* GetFirstDataContent(const SessionDescription* sdesc) {
  return GetFirstMediaContent(sdesc, MEDIA_TYPE_DATA);
}

const MediaContentDescription* GetFirstMediaContentDescription(
    const SessionDescription* sdesc,
    MediaType media_type) {
  const ContentInfo* content = GetFirstMediaContent(sdesc, media_type);
  return (content ? content->media_description() : nullptr);
}

const AudioContentDescription* GetFirstAudioContentDescription(
    const SessionDescription* sdesc) {
  auto desc = GetFirstMediaContentDescription(sdesc, MEDIA_TYPE_AUDIO);
  return desc ? desc->as_audio() : nullptr;
}

const VideoContentDescription* GetFirstVideoContentDescription(
    const SessionDescription* sdesc) {
  auto desc = GetFirstMediaContentDescription(sdesc, MEDIA_TYPE_VIDEO);
  return desc ? desc->as_video() : nullptr;
}

const SctpDataContentDescription* GetFirstSctpDataContentDescription(
    const SessionDescription* sdesc) {
  auto desc = GetFirstMediaContentDescription(sdesc, MEDIA_TYPE_DATA);
  return desc ? desc->as_sctp() : nullptr;
}

//
// Non-const versions of the above functions.
//

ContentInfo* GetFirstMediaContent(ContentInfos* contents,
                                  MediaType media_type) {
  for (ContentInfo& content : *contents) {
    if (IsMediaContentOfType(&content, media_type)) {
      return &content;
    }
  }
  return nullptr;
}

ContentInfo* GetFirstAudioContent(ContentInfos* contents) {
  return GetFirstMediaContent(contents, MEDIA_TYPE_AUDIO);
}

ContentInfo* GetFirstVideoContent(ContentInfos* contents) {
  return GetFirstMediaContent(contents, MEDIA_TYPE_VIDEO);
}

ContentInfo* GetFirstDataContent(ContentInfos* contents) {
  return GetFirstMediaContent(contents, MEDIA_TYPE_DATA);
}

ContentInfo* GetFirstMediaContent(SessionDescription* sdesc,
                                  MediaType media_type) {
  if (sdesc == nullptr) {
    return nullptr;
  }

  return GetFirstMediaContent(&sdesc->contents(), media_type);
}

ContentInfo* GetFirstAudioContent(SessionDescription* sdesc) {
  return GetFirstMediaContent(sdesc, MEDIA_TYPE_AUDIO);
}

ContentInfo* GetFirstVideoContent(SessionDescription* sdesc) {
  return GetFirstMediaContent(sdesc, MEDIA_TYPE_VIDEO);
}

ContentInfo* GetFirstDataContent(SessionDescription* sdesc) {
  return GetFirstMediaContent(sdesc, MEDIA_TYPE_DATA);
}

MediaContentDescription* GetFirstMediaContentDescription(
    SessionDescription* sdesc,
    MediaType media_type) {
  ContentInfo* content = GetFirstMediaContent(sdesc, media_type);
  return (content ? content->media_description() : nullptr);
}

AudioContentDescription* GetFirstAudioContentDescription(
    SessionDescription* sdesc) {
  auto desc = GetFirstMediaContentDescription(sdesc, MEDIA_TYPE_AUDIO);
  return desc ? desc->as_audio() : nullptr;
}

VideoContentDescription* GetFirstVideoContentDescription(
    SessionDescription* sdesc) {
  auto desc = GetFirstMediaContentDescription(sdesc, MEDIA_TYPE_VIDEO);
  return desc ? desc->as_video() : nullptr;
}

SctpDataContentDescription* GetFirstSctpDataContentDescription(
    SessionDescription* sdesc) {
  auto desc = GetFirstMediaContentDescription(sdesc, MEDIA_TYPE_DATA);
  return desc ? desc->as_sctp() : nullptr;
}

}  // namespace cricket

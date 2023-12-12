#include "v2_4_0_0/InstanceV2_4_0_0Impl.h"

#include "LogSinkImpl.h"
#include "VideoCaptureInterfaceImpl.h"
#include "VideoCapturerInterface.h"
#include "v2/NativeNetworkingImpl.h"
#include "v2_4_0_0/Signaling_4_0_0.h"

#include "CodecSelectHelper.h"
#include "platform/PlatformInterface.h"

#include "api/audio_codecs/audio_decoder_factory_template.h"
#include "api/audio_codecs/audio_encoder_factory_template.h"
#include "api/audio_codecs/opus/audio_decoder_opus.h"
#include "api/audio_codecs/opus/audio_decoder_multi_channel_opus.h"
#include "api/audio_codecs/opus/audio_encoder_opus.h"
#include "api/audio_codecs/L16/audio_decoder_L16.h"
#include "api/audio_codecs/L16/audio_encoder_L16.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "media/engine/webrtc_media_engine.h"
#include "system_wrappers/include/field_trial.h"
#include "api/video/builtin_video_bitrate_allocator_factory.h"
#include "call/call.h"
#include "api/call/audio_sink.h"
#include "modules/audio_processing/audio_buffer.h"
#include "absl/strings/match.h"
#include "pc/rtp_transport.h"
#include "pc/channel.h"
#include "audio/audio_state.h"
#include "modules/audio_coding/neteq/default_neteq_factory.h"
#include "modules/audio_coding/include/audio_coding_module.h"
#include "api/candidate.h"
#include "api/jsep_ice_candidate.h"
#include "pc/used_ids.h"
#include "media/base/sdp_video_format_utils.h"

#include "ChannelManager.h"
#include "AudioFrame.h"
#include "ThreadLocalObject.h"
#include "Manager.h"
#include "NetworkManager.h"
#include "VideoCaptureInterfaceImpl.h"
#include "platform/PlatformInterface.h"
#include "LogSinkImpl.h"
#include "CodecSelectHelper.h"
#include "AudioDeviceHelper.h"
#include "v2/SignalingEncryption.h"
#ifdef WEBRTC_IOS
#include "platform/darwin/iOS/tgcalls_audio_device_module_ios.h"
#endif
#include <random>
#include <sstream>

#include "FieldTrialsConfig.h"

namespace tgcalls {
namespace {

static std::string intToString(int value) {
    return std::to_string(value);
}

static VideoCaptureInterfaceObject *GetVideoCaptureAssumingSameThread(VideoCaptureInterface *videoCapture) {
    return videoCapture
        ? static_cast<VideoCaptureInterfaceImpl*>(videoCapture)->object()->getSyncAssumingSameThread()
        : nullptr;
}

struct OutgoingVideoFormat {
    cricket::VideoCodec videoCodec;
    absl::optional<cricket::VideoCodec> rtxCodec;
};

static void addDefaultFeedbackParams(cricket::VideoCodec *codec) {
    // Don't add any feedback params for RED and ULPFEC.
    if (codec->name == cricket::kRedCodecName || codec->name == cricket::kUlpfecCodecName) {
        return;
    }
    codec->AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamRemb, cricket::kParamValueEmpty));
    codec->AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamTransportCc, cricket::kParamValueEmpty));
    // Don't add any more feedback params for FLEXFEC.
    if (codec->name == cricket::kFlexfecCodecName) {
        return;
    }
    codec->AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamCcm, cricket::kRtcpFbCcmParamFir));
    codec->AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamNack, cricket::kParamValueEmpty));
    codec->AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamNack, cricket::kRtcpFbNackParamPli));
}

template <class C>
static bool IsRtxCodec(const C& codec) {
  return absl::EqualsIgnoreCase(codec.name, cricket::kRtxCodecName);
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

// Finds a codec in |codecs2| that matches |codec_to_match|, which is
// a member of |codecs1|. If |codec_to_match| is an RTX codec, both
// the codecs themselves and their associated codecs must match.
template <class C>
static bool FindMatchingCodec(const std::vector<C>& codecs1,
                              const std::vector<C>& codecs2,
                              const C& codec_to_match,
                              C* found_codec) {
  // |codec_to_match| should be a member of |codecs1|, in order to look up RTX
  // codecs' associated codecs correctly. If not, that's a programming error.
  RTC_DCHECK(absl::c_any_of(codecs1, [&codec_to_match](const C& codec) {
    return &codec == &codec_to_match;
  }));
  for (const C& potential_match : codecs2) {
    if (potential_match.Matches(codec_to_match)) {
      if (IsRtxCodec(codec_to_match)) {
        int apt_value_1 = 0;
        int apt_value_2 = 0;
        if (!codec_to_match.GetParam(cricket::kCodecParamAssociatedPayloadType,
                                     &apt_value_1) ||
            !potential_match.GetParam(cricket::kCodecParamAssociatedPayloadType,
                                      &apt_value_2)) {
          RTC_LOG(LS_WARNING) << "RTX missing associated payload type.";
          continue;
        }
        if (!ReferencedCodecsMatch(codecs1, apt_value_1, codecs2,
                                   apt_value_2)) {
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

template <class C>
static void NegotiatePacketization(const C& local_codec,
                                   const C& remote_codec,
                                   C* negotiated_codec) {}

template <>
void NegotiatePacketization(const cricket::VideoCodec& local_codec,
                            const cricket::VideoCodec& remote_codec,
                            cricket::VideoCodec* negotiated_codec) {
  negotiated_codec->packetization =
    cricket::VideoCodec::IntersectPacketization(local_codec, remote_codec);
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
          theirs.params.find(cricket::kCodecParamAssociatedPayloadType);
        // FindMatchingCodec shouldn't return something with no apt value.
        RTC_DCHECK(apt_it != theirs.params.end());
        negotiated.SetParam(cricket::kCodecParamAssociatedPayloadType, apt_it->second);
      }
      if (absl::EqualsIgnoreCase(ours.name, cricket::kH264CodecName)) {
        webrtc::H264GenerateProfileLevelIdForAnswer(
            ours.params, theirs.params, &negotiated.params);
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

// Find the codec in |codec_list| that |rtx_codec| is associated with.
template <class C>
static const C* GetAssociatedCodec(const std::vector<C>& codec_list,
                                   const C& rtx_codec) {
  std::string associated_pt_str;
  if (!rtx_codec.GetParam(cricket::kCodecParamAssociatedPayloadType,
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

  // Find the associated reference codec for the reference RTX codec.
  const C* associated_codec = FindCodecById(codec_list, associated_pt);
  if (!associated_codec) {
    RTC_LOG(LS_WARNING) << "Couldn't find associated codec with payload type "
                        << associated_pt << " for RTX codec " << rtx_codec.name
                        << ".";
  }
  return associated_codec;
}

// Adds all codecs from |reference_codecs| to |offered_codecs| that don't
// already exist in |offered_codecs| and ensure the payload types don't
// collide.
template <class C>
static void MergeCodecs(const std::vector<C>& reference_codecs,
                        std::vector<C>* offered_codecs,
                        cricket::UsedPayloadTypes* used_pltypes) {
  // Add all new codecs that are not RTX codecs.
  for (const C& reference_codec : reference_codecs) {
    if (!IsRtxCodec(reference_codec) &&
        !FindMatchingCodec<C>(reference_codecs, *offered_codecs,
                              reference_codec, nullptr)) {
      C codec = reference_codec;
      used_pltypes->FindAndSetIdUsed(&codec);
      offered_codecs->push_back(codec);
    }
  }

  // Add all new RTX codecs.
  for (const C& reference_codec : reference_codecs) {
    if (IsRtxCodec(reference_codec) &&
        !FindMatchingCodec<C>(reference_codecs, *offered_codecs,
                              reference_codec, nullptr)) {
      C rtx_codec = reference_codec;
      const C* associated_codec =
          GetAssociatedCodec(reference_codecs, rtx_codec);
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

      rtx_codec.params[cricket::kCodecParamAssociatedPayloadType] =
          rtc::ToString(matching_codec.id);
      used_pltypes->FindAndSetIdUsed(&rtx_codec);
      offered_codecs->push_back(rtx_codec);
    }
  }
}

static std::vector<OutgoingVideoFormat> generateAvailableVideoFormats(std::vector<webrtc::SdpVideoFormat> const &formats) {
    if (formats.empty()) {
        return {};
    }

    constexpr int kFirstDynamicPayloadType = 100;
    constexpr int kLastDynamicPayloadType = 127;

    int payload_type = kFirstDynamicPayloadType;

    std::vector<OutgoingVideoFormat> result;

    //bool codecSelected = false;

    for (const auto &format : formats) {
        /*if (codecSelected) {
            break;
        }*/

        bool alreadyAdded = false;
        for (const auto &it : result) {
            if (it.videoCodec.name == format.name) {
                alreadyAdded = true;
                break;
            }
        }
        if (alreadyAdded) {
            continue;
        }

        OutgoingVideoFormat resultFormat;

        cricket::VideoCodec codec(format);
        codec.id = payload_type;
        addDefaultFeedbackParams(&codec);

        resultFormat.videoCodec = codec;
        //codecSelected = true;

        // Increment payload type.
        ++payload_type;
        if (payload_type > kLastDynamicPayloadType) {
            RTC_LOG(LS_ERROR) << "Out of dynamic payload types, skipping the rest.";
            break;
        }

        // Add associated RTX codec for non-FEC codecs.
        if (!absl::EqualsIgnoreCase(codec.name, cricket::kUlpfecCodecName) &&
            !absl::EqualsIgnoreCase(codec.name, cricket::kFlexfecCodecName)) {
            resultFormat.rtxCodec = cricket::VideoCodec::CreateRtxCodec(payload_type, codec.id);

            // Increment payload type.
            ++payload_type;
            if (payload_type > kLastDynamicPayloadType) {
                RTC_LOG(LS_ERROR) << "Out of dynamic payload types, skipping the rest.";
                break;
            }
        }

        result.push_back(std::move(resultFormat));
    }
    return result;
}

static void getCodecsFromMediaContent(signaling_4_0_0::MediaContent const &content, std::vector<cricket::VideoCodec> &codecs) {
    for (const auto &payloadType : content.payloadTypes) {
        cricket::VideoCodec codec(payloadType.id, payloadType.name);
        for (const auto &feedbackType : payloadType.feedbackTypes) {
            codec.AddFeedbackParam(cricket::FeedbackParam(feedbackType.type, feedbackType.subtype));
        }
        for (const auto &parameter : payloadType.parameters) {
            codec.SetParam(parameter.first, parameter.second);
        }
        codecs.push_back(std::move(codec));
    }
}

static std::vector<signaling_4_0_0::PayloadType> getPayloadTypesFromVideoCodecs(std::vector<cricket::VideoCodec> const &codecs) {
    std::vector<signaling_4_0_0::PayloadType> payloadTypes;

    for (const auto &codec : codecs) {
        signaling_4_0_0::PayloadType payloadType;

        payloadType.id = codec.id;
        payloadType.name = codec.name;
        payloadType.clockrate = 90000;
        payloadType.channels = 0;

        for (const auto &feedbackParam : codec.feedback_params.params()) {
            signaling_4_0_0::FeedbackType feedbackType;
            feedbackType.type = feedbackParam.id();
            feedbackType.subtype = feedbackParam.param();
            payloadType.feedbackTypes.push_back(std::move(feedbackType));
        }

        for (const auto &param : codec.params) {
            payloadType.parameters.push_back(std::make_pair(param.first, param.second));
        }

        payloadTypes.push_back(std::move(payloadType));
    }

    return payloadTypes;
}

static void getCodecsFromMediaContent(signaling_4_0_0::MediaContent const &content, std::vector<cricket::AudioCodec> &codecs) {
    for (const auto &payloadType : content.payloadTypes) {
        cricket::AudioCodec codec(payloadType.id, payloadType.name, payloadType.clockrate, 0, payloadType.channels);
        for (const auto &feedbackType : payloadType.feedbackTypes) {
            codec.AddFeedbackParam(cricket::FeedbackParam(feedbackType.type, feedbackType.subtype));
        }
        for (const auto &parameter : payloadType.parameters) {
            codec.SetParam(parameter.first, parameter.second);
        }
        codecs.push_back(std::move(codec));
    }
}

static std::vector<signaling_4_0_0::PayloadType> getPayloadTypesFromAudioCodecs(std::vector<cricket::AudioCodec> const &codecs) {
    std::vector<signaling_4_0_0::PayloadType> payloadTypes;

    for (const auto &codec : codecs) {
        signaling_4_0_0::PayloadType payloadType;

        payloadType.id = codec.id;
        payloadType.name = codec.name;
        payloadType.clockrate = codec.clockrate;
        payloadType.channels = (uint32_t)codec.channels;

        for (const auto &feedbackParam : codec.feedback_params.params()) {
            signaling_4_0_0::FeedbackType feedbackType;
            feedbackType.type = feedbackParam.id();
            feedbackType.subtype = feedbackParam.param();
            payloadType.feedbackTypes.push_back(std::move(feedbackType));
        }

        for (const auto &param : codec.params) {
            payloadType.parameters.push_back(std::make_pair(param.first, param.second));
        }

        payloadTypes.push_back(std::move(payloadType));
    }

    return payloadTypes;
}

template <class C>
struct NegotiatedMediaContent {
    uint32_t ssrc = 0;
    std::vector<signaling_4_0_0::SsrcGroup> ssrcGroups;
    std::vector<webrtc::RtpExtension> rtpExtensions;
    std::vector<C> codecs;
};

static bool FindByUri(const cricket::RtpHeaderExtensions& extensions,
                      const webrtc::RtpExtension& ext_to_match,
                      webrtc::RtpExtension* found_extension) {
    // We assume that all URIs are given in a canonical format.
    const webrtc::RtpExtension* found =
    webrtc::RtpExtension::FindHeaderExtensionByUri(
        extensions,
        ext_to_match.uri,
        webrtc::RtpExtension::Filter::kPreferEncryptedExtension
    );
    if (!found) {
        return false;
    }
    if (found_extension) {
        *found_extension = *found;
    }
    return true;
}

template <class C>
static NegotiatedMediaContent<C> negotiateMediaContent(signaling_4_0_0::MediaContent const &baseMediaContent, signaling_4_0_0::MediaContent const &localContent, signaling_4_0_0::MediaContent const &remoteContent, bool isAnswer) {
    std::vector<C> localCodecs;
    getCodecsFromMediaContent(localContent, localCodecs);

    std::vector<C> remoteCodecs;
    getCodecsFromMediaContent(remoteContent, remoteCodecs);

    std::vector<C> negotiatedCodecs;

    cricket::UsedPayloadTypes usedPayloadTypes;
    NegotiateCodecs<C>(localCodecs, remoteCodecs, &negotiatedCodecs, true);

    NegotiatedMediaContent<C> result;

    result.ssrc = baseMediaContent.ssrc;
    result.ssrcGroups = baseMediaContent.ssrcGroups;
    result.codecs = std::move(negotiatedCodecs);

    cricket::UsedRtpHeaderExtensionIds extensionIds(cricket::UsedRtpHeaderExtensionIds::IdDomain::kOneByteOnly);

    for (const auto &extension : remoteContent.rtpExtensions) {
        if (isAnswer) {
            webrtc::RtpExtension found;
            if (!FindByUri(localContent.rtpExtensions, extension, &found)) {
                continue;
            }
        }

        webrtc::RtpExtension mutableExtension = extension;
        extensionIds.FindAndSetIdUsed(&mutableExtension);
        result.rtpExtensions.push_back(std::move(mutableExtension));
    }

    if (!isAnswer) {
        for (const auto &extension : localContent.rtpExtensions) {
            webrtc::RtpExtension found;
            if (!FindByUri(result.rtpExtensions, extension, &found)) {
                webrtc::RtpExtension mutableExtension = extension;
                extensionIds.FindAndSetIdUsed(&mutableExtension);
                result.rtpExtensions.push_back(std::move(mutableExtension));
            }
        }
    }

    return result;
}

class OutgoingAudioChannel : public sigslot::has_slots<> {
public:
    static absl::optional<signaling_4_0_0::MediaContent> createOutgoingContentDescription() {
        signaling_4_0_0::MediaContent mediaContent;

        auto generator = std::mt19937(std::random_device()());
        auto distribution = std::uniform_int_distribution<uint32_t>();
        do {
            mediaContent.ssrc = distribution(generator) & 0x7fffffffU;
        } while (!mediaContent.ssrc);

        mediaContent.rtpExtensions.emplace_back(webrtc::RtpExtension::kAudioLevelUri, 1);
        mediaContent.rtpExtensions.emplace_back(webrtc::RtpExtension::kAbsSendTimeUri, 2);
        mediaContent.rtpExtensions.emplace_back(webrtc::RtpExtension::kTransportSequenceNumberUri, 3);

        cricket::AudioCodec opusCodec(109, "opus", 48000, 0, 2);
        opusCodec.AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamTransportCc));
        opusCodec.SetParam(cricket::kCodecParamUseInbandFec, 1);
        opusCodec.SetParam(cricket::kCodecParamMinPTime, 60);

        mediaContent.payloadTypes = getPayloadTypesFromAudioCodecs({ opusCodec });

        return mediaContent;
    }

public:
    OutgoingAudioChannel(
        webrtc::Call *call,
        ChannelManager *channelManager,
        rtc::UniqueRandomIdGenerator *uniqueRandomIdGenerator,
        webrtc::LocalAudioSinkAdapter *audioSource,
        webrtc::RtpTransport *rtpTransport,
        NegotiatedMediaContent<cricket::AudioCodec> const &mediaContent,
        std::shared_ptr<Threads> threads
    ) :
    _threads(threads),
    _ssrc(mediaContent.ssrc),
    _call(call),
    _channelManager(channelManager),
    _audioSource(audioSource) {
        cricket::AudioOptions audioOptions;
        bool _disableOutgoingAudioProcessing = false;

        if (_disableOutgoingAudioProcessing) {
            audioOptions.echo_cancellation = false;
            audioOptions.noise_suppression = false;
            audioOptions.auto_gain_control = false;
            audioOptions.highpass_filter = false;
            //audioOptions.typing_detection = false;
            //audioOptions.residual_echo_detector = false;
        } else {
            audioOptions.echo_cancellation = true;
            audioOptions.noise_suppression = true;
        }

        std::vector<std::string> streamIds;
        streamIds.push_back("1");

        _outgoingAudioChannel = _channelManager->CreateVoiceChannel(call, cricket::MediaConfig(), "audio0", false, NativeNetworkingImpl::getDefaulCryptoOptions(), audioOptions);
        _threads->getNetworkThread()->BlockingCall([&]() {
            _outgoingAudioChannel->SetRtpTransport(rtpTransport);
        });

        std::vector<cricket::AudioCodec> codecs;
        for (const auto &codec : mediaContent.codecs) {
            if (codec.name == "opus") {
                auto mutableCodec = codec;

                const uint8_t opusMinBitrateKbps = 16;
                const uint8_t opusMaxBitrateKbps = 32;
                const uint8_t opusStartBitrateKbps = 32;
                const uint8_t opusPTimeMs = 60;

                mutableCodec.SetParam(cricket::kCodecParamMinBitrate, opusMinBitrateKbps);
                mutableCodec.SetParam(cricket::kCodecParamStartBitrate, opusStartBitrateKbps);
                mutableCodec.SetParam(cricket::kCodecParamMaxBitrate, opusMaxBitrateKbps);
                mutableCodec.SetParam(cricket::kCodecParamUseInbandFec, 1);
                mutableCodec.SetParam(cricket::kCodecParamPTime, opusPTimeMs);

                codecs.push_back(std::move(mutableCodec));
            }
        }

        auto outgoingAudioDescription = std::make_unique<cricket::AudioContentDescription>();
        for (const auto &rtpExtension : mediaContent.rtpExtensions) {
            outgoingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(rtpExtension.uri, rtpExtension.id));
        }
        outgoingAudioDescription->set_rtcp_mux(true);
        outgoingAudioDescription->set_rtcp_reduced_size(true);
        outgoingAudioDescription->set_direction(webrtc::RtpTransceiverDirection::kSendOnly);
        outgoingAudioDescription->set_codecs(codecs);
        outgoingAudioDescription->set_bandwidth(-1);
        outgoingAudioDescription->AddStream(cricket::StreamParams::CreateLegacy(_ssrc));

        auto incomingAudioDescription = std::make_unique<cricket::AudioContentDescription>();
        for (const auto &rtpExtension : mediaContent.rtpExtensions) {
            incomingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(rtpExtension.uri, rtpExtension.id));
        }
        incomingAudioDescription->set_rtcp_mux(true);
        incomingAudioDescription->set_rtcp_reduced_size(true);
        incomingAudioDescription->set_direction(webrtc::RtpTransceiverDirection::kRecvOnly);
        incomingAudioDescription->set_codecs(codecs);
        incomingAudioDescription->set_bandwidth(-1);

        _threads->getWorkerThread()->BlockingCall([&]() {
            _outgoingAudioChannel->SetPayloadTypeDemuxingEnabled(false);
            std::string errorDesc;
            _outgoingAudioChannel->SetLocalContent(outgoingAudioDescription.get(), webrtc::SdpType::kOffer, errorDesc);
            _outgoingAudioChannel->SetRemoteContent(incomingAudioDescription.get(), webrtc::SdpType::kAnswer, errorDesc);
        });

        setIsMuted(false);
    }

    ~OutgoingAudioChannel() {
        _outgoingAudioChannel->Enable(false);
        _threads->getNetworkThread()->BlockingCall([&]() {
            _outgoingAudioChannel->SetRtpTransport(nullptr);
        });
        _channelManager->DestroyChannel(_outgoingAudioChannel);
        _outgoingAudioChannel = nullptr;
    }

    void setIsMuted(bool isMuted) {
        if (_isMuted != isMuted) {
            _isMuted = isMuted;

            _outgoingAudioChannel->Enable(!_isMuted);
            _threads->getWorkerThread()->BlockingCall([&]() {
                _outgoingAudioChannel->media_channel()->SetAudioSend(_ssrc, !_isMuted, nullptr, _audioSource);
            });
        }
    }

private:
    void OnSentPacket_w(const rtc::SentPacket& sent_packet) {
        _call->OnSentPacket(sent_packet);
    }

private:
    std::shared_ptr<Threads> _threads;
    uint32_t _ssrc = 0;
    webrtc::Call *_call = nullptr;
    ChannelManager *_channelManager = nullptr;
    webrtc::LocalAudioSinkAdapter *_audioSource = nullptr;
    cricket::VoiceChannel *_outgoingAudioChannel = nullptr;

    bool _isMuted = true;
};

class IncomingV2AudioChannel : public sigslot::has_slots<> {
public:
    IncomingV2AudioChannel(
        ChannelManager *channelManager,
        webrtc::Call *call,
        webrtc::RtpTransport *rtpTransport,
        rtc::UniqueRandomIdGenerator *randomIdGenerator,
        NegotiatedMediaContent<cricket::AudioCodec> const &mediaContent,
        std::shared_ptr<Threads> threads) :
    _threads(threads),
    _ssrc(mediaContent.ssrc),
    _channelManager(channelManager),
    _call(call) {
        _creationTimestamp = rtc::TimeMillis();

        cricket::AudioOptions audioOptions;
        audioOptions.audio_jitter_buffer_fast_accelerate = true;
        audioOptions.audio_jitter_buffer_min_delay_ms = 50;

        std::string streamId = std::string("stream1");

        _audioChannel = _channelManager->CreateVoiceChannel(call, cricket::MediaConfig(), "0", false, NativeNetworkingImpl::getDefaulCryptoOptions(), audioOptions);
        _threads->getNetworkThread()->BlockingCall([&]() {
            _audioChannel->SetRtpTransport(rtpTransport);
        });

        auto audioCodecs = mediaContent.codecs;

        auto outgoingAudioDescription = std::make_unique<cricket::AudioContentDescription>();
        for (const auto &rtpExtension : mediaContent.rtpExtensions) {
            outgoingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(rtpExtension.uri, rtpExtension.id));
        }
        outgoingAudioDescription->set_rtcp_mux(true);
        outgoingAudioDescription->set_rtcp_reduced_size(true);
        outgoingAudioDescription->set_direction(webrtc::RtpTransceiverDirection::kRecvOnly);
        outgoingAudioDescription->set_codecs(audioCodecs);
        outgoingAudioDescription->set_bandwidth(-1);

        auto incomingAudioDescription = std::make_unique<cricket::AudioContentDescription>();
        for (const auto &rtpExtension : mediaContent.rtpExtensions) {
            incomingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(rtpExtension.uri, rtpExtension.id));
        }
        incomingAudioDescription->set_rtcp_mux(true);
        incomingAudioDescription->set_rtcp_reduced_size(true);
        incomingAudioDescription->set_direction(webrtc::RtpTransceiverDirection::kSendOnly);
        incomingAudioDescription->set_codecs(audioCodecs);
        incomingAudioDescription->set_bandwidth(-1);
        cricket::StreamParams streamParams = cricket::StreamParams::CreateLegacy(mediaContent.ssrc);
        streamParams.set_stream_ids({ streamId });
        incomingAudioDescription->AddStream(streamParams);

        threads->getWorkerThread()->BlockingCall([&]() {
            _audioChannel->SetPayloadTypeDemuxingEnabled(false);
            std::string errorDesc;
            _audioChannel->SetLocalContent(outgoingAudioDescription.get(), webrtc::SdpType::kOffer, errorDesc);
            _audioChannel->SetRemoteContent(incomingAudioDescription.get(), webrtc::SdpType::kAnswer, errorDesc);
        });

        outgoingAudioDescription.reset();
        incomingAudioDescription.reset();

        //std::unique_ptr<AudioSinkImpl> audioLevelSink(new AudioSinkImpl(onAudioLevelUpdated, _ssrc, std::move(onAudioFrame)));
        //_audioChannel->media_channel()->SetRawAudioSink(ssrc.networkSsrc, std::move(audioLevelSink));

        _audioChannel->Enable(true);
    }

    ~IncomingV2AudioChannel() {
        _audioChannel->Enable(false);
        _threads->getNetworkThread()->BlockingCall([&]() {
            _audioChannel->SetRtpTransport(nullptr);
        });
        _channelManager->DestroyChannel(_audioChannel);
        _audioChannel = nullptr;
    }

    void setVolume(double value) {
        _audioChannel->media_channel()->SetOutputVolume(_ssrc, value);
    }

    void updateActivity() {
        _activityTimestamp = rtc::TimeMillis();
    }

    int64_t getActivity() {
        return _activityTimestamp;
    }

private:
    void OnSentPacket_w(const rtc::SentPacket& sent_packet) {
        _call->OnSentPacket(sent_packet);
    }

private:
    std::shared_ptr<Threads> _threads;
    uint32_t _ssrc = 0;
    // Memory is managed by _channelManager
    cricket::VoiceChannel *_audioChannel = nullptr;
    // Memory is managed externally
    ChannelManager *_channelManager = nullptr;
    webrtc::Call *_call = nullptr;
    int64_t _creationTimestamp = 0;
    int64_t _activityTimestamp = 0;
};

class OutgoingVideoChannel : public sigslot::has_slots<>, public std::enable_shared_from_this<OutgoingVideoChannel> {
public:
    static absl::optional<signaling_4_0_0::MediaContent> createOutgoingContentDescription(std::vector<webrtc::SdpVideoFormat> const &availableVideoFormats, bool isScreencast) {
        signaling_4_0_0::MediaContent mediaContent;

        auto generator = std::mt19937(std::random_device()());
        auto distribution = std::uniform_int_distribution<uint32_t>();
        do {
            mediaContent.ssrc = distribution(generator) & 0x7fffffffU;
        } while (!mediaContent.ssrc);

        mediaContent.rtpExtensions.emplace_back(webrtc::RtpExtension::kAbsSendTimeUri, 2);
        mediaContent.rtpExtensions.emplace_back(webrtc::RtpExtension::kTransportSequenceNumberUri, 3);
        mediaContent.rtpExtensions.emplace_back(webrtc::RtpExtension::kVideoRotationUri, 13);

        signaling_4_0_0::SsrcGroup fidGroup;
        fidGroup.semantics = "FID";
        fidGroup.ssrcs.push_back(mediaContent.ssrc);
        fidGroup.ssrcs.push_back(mediaContent.ssrc + 1);
        mediaContent.ssrcGroups.push_back(std::move(fidGroup));

        auto unsortedVideoFormats = generateAvailableVideoFormats(availableVideoFormats);

        std::vector<std::string> formatPreferences;
        if (isScreencast) {
            formatPreferences.push_back(cricket::kVp8CodecName);
        } else {
#ifndef WEBRTC_DISABLE_H265
            formatPreferences.push_back(cricket::kH265CodecName);
#endif
            formatPreferences.push_back(cricket::kH264CodecName);
        }

        std::vector<OutgoingVideoFormat> videoFormats;
        for (const auto &name : formatPreferences) {
            for (size_t i = 0; i < unsortedVideoFormats.size(); i++) {
                if (absl::EqualsIgnoreCase(name, unsortedVideoFormats[i].videoCodec.name)) {
                    videoFormats.push_back(unsortedVideoFormats[i]);
                    unsortedVideoFormats.erase(unsortedVideoFormats.begin() + i);
                    break;
                }
            }
        }
        for (const auto &format : unsortedVideoFormats) {
            videoFormats.push_back(format);
        }

        for (const auto &format : videoFormats) {
            signaling_4_0_0::PayloadType videoPayload;
            videoPayload.id = format.videoCodec.id;
            videoPayload.name = format.videoCodec.name;
            videoPayload.clockrate = format.videoCodec.clockrate;
            videoPayload.channels = 0;

            std::vector<signaling_4_0_0::FeedbackType> videoFeedbackTypes;

            signaling_4_0_0::FeedbackType fbGoogRemb;
            fbGoogRemb.type = "goog-remb";
            videoFeedbackTypes.push_back(fbGoogRemb);

            signaling_4_0_0::FeedbackType fbTransportCc;
            fbTransportCc.type = "transport-cc";
            videoFeedbackTypes.push_back(fbTransportCc);

            signaling_4_0_0::FeedbackType fbCcmFir;
            fbCcmFir.type = "ccm";
            fbCcmFir.subtype = "fir";
            videoFeedbackTypes.push_back(fbCcmFir);

            signaling_4_0_0::FeedbackType fbNack;
            fbNack.type = "nack";
            videoFeedbackTypes.push_back(fbNack);

            signaling_4_0_0::FeedbackType fbNackPli;
            fbNackPli.type = "nack";
            fbNackPli.subtype = "pli";
            videoFeedbackTypes.push_back(fbNackPli);

            videoPayload.feedbackTypes = videoFeedbackTypes;
            videoPayload.parameters = {};

            mediaContent.payloadTypes.push_back(std::move(videoPayload));

            if (format.rtxCodec) {
                signaling_4_0_0::PayloadType rtxPayload;
                rtxPayload.id = format.rtxCodec->id;
                rtxPayload.name = format.rtxCodec->name;
                rtxPayload.clockrate = format.rtxCodec->clockrate;
                rtxPayload.parameters.push_back(std::make_pair("apt", intToString(videoPayload.id)));
                mediaContent.payloadTypes.push_back(std::move(rtxPayload));
            }
        }

        return mediaContent;
    }

public:
    OutgoingVideoChannel(
        std::shared_ptr<Threads> threads,
        ChannelManager *channelManager,
        webrtc::Call *call,
        webrtc::RtpTransport *rtpTransport,
        rtc::UniqueRandomIdGenerator *randomIdGenerator,
        webrtc::VideoBitrateAllocatorFactory *videoBitrateAllocatorFactory,
        std::function<void()> rotationUpdated,
        NegotiatedMediaContent<cricket::VideoCodec> const &mediaContent,
        bool isScreencast
    ) :
    _threads(threads),
    _mainSsrc(mediaContent.ssrc),
    _call(call),
    _channelManager(channelManager),
    _rotationUpdated(rotationUpdated) {
        cricket::VideoOptions videoOptions;
        videoOptions.is_screencast = isScreencast;
        _outgoingVideoChannel = _channelManager->CreateVideoChannel(call, cricket::MediaConfig(), "out" + intToString(mediaContent.ssrc), false, NativeNetworkingImpl::getDefaulCryptoOptions(), videoOptions, videoBitrateAllocatorFactory);
        _threads->getNetworkThread()->BlockingCall([&]() {
            _outgoingVideoChannel->SetRtpTransport(rtpTransport);
        });

        auto videoCodecs = mediaContent.codecs;

        auto outgoingVideoDescription = std::make_unique<cricket::VideoContentDescription>();
        for (const auto &rtpExtension : mediaContent.rtpExtensions) {
            outgoingVideoDescription->AddRtpHeaderExtension(rtpExtension);
        }

        outgoingVideoDescription->set_rtcp_mux(true);
        outgoingVideoDescription->set_rtcp_reduced_size(true);
        outgoingVideoDescription->set_direction(webrtc::RtpTransceiverDirection::kSendOnly);
        outgoingVideoDescription->set_codecs(videoCodecs);
        outgoingVideoDescription->set_bandwidth(-1);

        cricket::StreamParams videoSendStreamParams;

        for (const auto &ssrcGroup : mediaContent.ssrcGroups) {
            for (auto ssrc : ssrcGroup.ssrcs) {
                videoSendStreamParams.ssrcs.push_back(ssrc);
            }

            cricket::SsrcGroup mappedGroup(ssrcGroup.semantics, ssrcGroup.ssrcs);
            videoSendStreamParams.ssrc_groups.push_back(std::move(mappedGroup));
        }

        videoSendStreamParams.cname = "cname";

        outgoingVideoDescription->AddStream(videoSendStreamParams);

        auto incomingVideoDescription = std::make_unique<cricket::VideoContentDescription>();
        for (const auto &rtpExtension : mediaContent.rtpExtensions) {
            incomingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(rtpExtension.uri, rtpExtension.id));
        }
        incomingVideoDescription->set_rtcp_mux(true);
        incomingVideoDescription->set_rtcp_reduced_size(true);
        incomingVideoDescription->set_direction(webrtc::RtpTransceiverDirection::kRecvOnly);
        incomingVideoDescription->set_codecs(videoCodecs);
        incomingVideoDescription->set_bandwidth(-1);

        threads->getWorkerThread()->BlockingCall([&]() {
            _outgoingVideoChannel->SetPayloadTypeDemuxingEnabled(false);
            std::string errorDesc;
            _outgoingVideoChannel->SetLocalContent(outgoingVideoDescription.get(), webrtc::SdpType::kOffer, errorDesc);
            _outgoingVideoChannel->SetRemoteContent(incomingVideoDescription.get(), webrtc::SdpType::kAnswer, errorDesc);

            webrtc::RtpParameters rtpParameters = _outgoingVideoChannel->media_channel()->GetRtpSendParameters(mediaContent.ssrc);

            _outgoingVideoChannel->media_channel()->SetRtpSendParameters(mediaContent.ssrc, rtpParameters);
        });

        _outgoingVideoChannel->Enable(false);

        threads->getWorkerThread()->BlockingCall([&]() {
            _outgoingVideoChannel->media_channel()->SetVideoSend(mediaContent.ssrc, NULL, nullptr);
        });
    }

    ~OutgoingVideoChannel() {
        _outgoingVideoChannel->Enable(false);
        _threads->getNetworkThread()->BlockingCall([&]() {
            _outgoingVideoChannel->SetRtpTransport(nullptr);
        });
        _channelManager->DestroyChannel(_outgoingVideoChannel);
        _outgoingVideoChannel = nullptr;
    }

    void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) {
        _videoCapture = videoCapture;

        if (_videoCapture) {
            _outgoingVideoChannel->Enable(true);
            auto videoCaptureImpl = GetVideoCaptureAssumingSameThread(_videoCapture.get());

            _threads->getWorkerThread()->BlockingCall([&]() {
                _outgoingVideoChannel->media_channel()->SetVideoSend(_mainSsrc, NULL, videoCaptureImpl->source().get());
            });

            const auto weak = std::weak_ptr<OutgoingVideoChannel>(shared_from_this());
            videoCaptureImpl->setRotationUpdated([threads = _threads, weak](int angle) {
                threads->getMediaThread()->PostTask([=] {
                    const auto strong = weak.lock();
                    if (!strong) {
                        return;
                    }
                    signaling_4_0_0::MediaStateMessage::VideoRotation videoRotation = signaling_4_0_0::MediaStateMessage::VideoRotation::Rotation0;
                    switch (angle) {
                        case 0: {
                            videoRotation = signaling_4_0_0::MediaStateMessage::VideoRotation::Rotation0;
                            break;
                        }
                        case 90: {
                            videoRotation = signaling_4_0_0::MediaStateMessage::VideoRotation::Rotation90;
                            break;
                        }
                        case 180: {
                            videoRotation = signaling_4_0_0::MediaStateMessage::VideoRotation::Rotation180;
                            break;
                        }
                        case 270: {
                            videoRotation = signaling_4_0_0::MediaStateMessage::VideoRotation::Rotation270;
                            break;
                        }
                        default: {
                            videoRotation = signaling_4_0_0::MediaStateMessage::VideoRotation::Rotation0;
                            break;
                        }
                    }
                    if (strong->_videoRotation != videoRotation) {
                        strong->_videoRotation = videoRotation;
                        strong->_rotationUpdated();
                    }
                });
            });

            switch (videoCaptureImpl->getRotation()) {
                case 0: {
                    _videoRotation = signaling_4_0_0::MediaStateMessage::VideoRotation::Rotation0;
                    break;
                }
                case 90: {
                    _videoRotation = signaling_4_0_0::MediaStateMessage::VideoRotation::Rotation90;
                    break;
                }
                case 180: {
                    _videoRotation = signaling_4_0_0::MediaStateMessage::VideoRotation::Rotation180;
                    break;
                }
                case 270: {
                    _videoRotation = signaling_4_0_0::MediaStateMessage::VideoRotation::Rotation270;
                    break;
                }
                default: {
                    _videoRotation = signaling_4_0_0::MediaStateMessage::VideoRotation::Rotation0;
                    break;
                }
            }
        } else {
            _videoRotation = signaling_4_0_0::MediaStateMessage::VideoRotation::Rotation0;
            _outgoingVideoChannel->Enable(false);

            _threads->getWorkerThread()->BlockingCall([&]() {
                _outgoingVideoChannel->media_channel()->SetVideoSend(_mainSsrc, NULL, nullptr);
            });
        }
    }

public:
    std::shared_ptr<VideoCaptureInterface> videoCapture() {
        return _videoCapture;
    }

    signaling_4_0_0::MediaStateMessage::VideoRotation getRotation() {
        return _videoRotation;
    }

private:
    void OnSentPacket_w(const rtc::SentPacket& sent_packet) {
        _call->OnSentPacket(sent_packet);
    }

private:
    std::shared_ptr<Threads> _threads;

    uint32_t _mainSsrc = 0;
    webrtc::Call *_call = nullptr;
    ChannelManager *_channelManager = nullptr;
    cricket::VideoChannel *_outgoingVideoChannel = nullptr;

    std::function<void()> _rotationUpdated;

    std::shared_ptr<VideoCaptureInterface> _videoCapture;
    signaling_4_0_0::MediaStateMessage::VideoRotation _videoRotation = signaling_4_0_0::MediaStateMessage::VideoRotation::Rotation0;
};

class VideoSinkImpl : public rtc::VideoSinkInterface<webrtc::VideoFrame> {
public:
    VideoSinkImpl() {
    }

    virtual ~VideoSinkImpl() {
    }

    virtual void OnFrame(const webrtc::VideoFrame& frame) override {
        //_lastFrame = frame;
        for (int i = (int)(_sinks.size()) - 1; i >= 0; i--) {
            auto strong = _sinks[i].lock();
            if (!strong) {
                _sinks.erase(_sinks.begin() + i);
            } else {
                strong->OnFrame(frame);
            }
        }
    }

    virtual void OnDiscardedFrame() override {
        for (int i = (int)(_sinks.size()) - 1; i >= 0; i--) {
            auto strong = _sinks[i].lock();
            if (!strong) {
                _sinks.erase(_sinks.begin() + i);
            } else {
                strong->OnDiscardedFrame();
            }
        }
    }

    void addSink(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> impl) {
        _sinks.push_back(impl);
        if (_lastFrame) {
            auto strong = impl.lock();
            if (strong) {
                strong->OnFrame(_lastFrame.value());
            }
        }
    }

private:
    std::vector<std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>> _sinks;
    absl::optional<webrtc::VideoFrame> _lastFrame;
};

class IncomingV2VideoChannel : public sigslot::has_slots<> {
public:
    IncomingV2VideoChannel(
        ChannelManager *channelManager,
        webrtc::Call *call,
        webrtc::RtpTransport *rtpTransport,
        rtc::UniqueRandomIdGenerator *randomIdGenerator,
        NegotiatedMediaContent<cricket::VideoCodec> const &mediaContent,
        std::string const &streamId,
        std::shared_ptr<Threads> threads) :
    _threads(threads),
    _channelManager(channelManager),
    _call(call) {
        _videoSink.reset(new VideoSinkImpl());

        _videoBitrateAllocatorFactory = webrtc::CreateBuiltinVideoBitrateAllocatorFactory();

        _videoChannel = _channelManager->CreateVideoChannel(call, cricket::MediaConfig(), streamId, false, NativeNetworkingImpl::getDefaulCryptoOptions(), cricket::VideoOptions(), _videoBitrateAllocatorFactory.get());
        _threads->getNetworkThread()->BlockingCall([&]() {
            _videoChannel->SetRtpTransport(rtpTransport);
        });

        std::vector<cricket::VideoCodec> videoCodecs = mediaContent.codecs;

        auto outgoingVideoDescription = std::make_unique<cricket::VideoContentDescription>();
        for (const auto &rtpExtension : mediaContent.rtpExtensions) {
            outgoingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(rtpExtension.uri, rtpExtension.id));
        }
        outgoingVideoDescription->set_rtcp_mux(true);
        outgoingVideoDescription->set_rtcp_reduced_size(true);
        outgoingVideoDescription->set_direction(webrtc::RtpTransceiverDirection::kRecvOnly);
        outgoingVideoDescription->set_codecs(videoCodecs);
        outgoingVideoDescription->set_bandwidth(-1);

        cricket::StreamParams videoRecvStreamParams;

        _mainVideoSsrc = mediaContent.ssrc;

        std::vector<uint32_t> allSsrcs;
        for (const auto &group : mediaContent.ssrcGroups) {
            for (auto ssrc : group.ssrcs) {
                if (std::find(allSsrcs.begin(), allSsrcs.end(), ssrc) == allSsrcs.end()) {
                    allSsrcs.push_back(ssrc);
                }
            }

            cricket::SsrcGroup parsedGroup(group.semantics, group.ssrcs);
            videoRecvStreamParams.ssrc_groups.push_back(parsedGroup);
        }
        videoRecvStreamParams.ssrcs = allSsrcs;

        videoRecvStreamParams.cname = "cname";
        videoRecvStreamParams.set_stream_ids({ streamId });

        auto incomingVideoDescription = std::make_unique<cricket::VideoContentDescription>();
        for (const auto &rtpExtension : mediaContent.rtpExtensions) {
            incomingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(rtpExtension.uri, rtpExtension.id));
        }
        incomingVideoDescription->set_rtcp_mux(true);
        incomingVideoDescription->set_rtcp_reduced_size(true);
        incomingVideoDescription->set_direction(webrtc::RtpTransceiverDirection::kSendOnly);
        incomingVideoDescription->set_codecs(videoCodecs);
        incomingVideoDescription->set_bandwidth(-1);

        incomingVideoDescription->AddStream(videoRecvStreamParams);

        threads->getWorkerThread()->BlockingCall([&]() {
            _videoChannel->SetPayloadTypeDemuxingEnabled(false);
            std::string errorDesc;
            _videoChannel->SetLocalContent(outgoingVideoDescription.get(), webrtc::SdpType::kOffer, errorDesc);
            _videoChannel->SetRemoteContent(incomingVideoDescription.get(), webrtc::SdpType::kAnswer, errorDesc);

            _videoChannel->media_channel()->SetSink(_mainVideoSsrc, _videoSink.get());
        });

        _videoChannel->Enable(true);
    }

    ~IncomingV2VideoChannel() {
        _videoChannel->Enable(false);
        _threads->getNetworkThread()->BlockingCall([&]() {
            _videoChannel->SetRtpTransport(nullptr);
        });
        _channelManager->DestroyChannel(_videoChannel);
        _videoChannel = nullptr;
    }

    void addSink(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> impl) {
        _videoSink->addSink(impl);
    }

private:
    void OnSentPacket_w(const rtc::SentPacket& sent_packet) {
        _call->OnSentPacket(sent_packet);
    }

private:
    std::shared_ptr<Threads> _threads;
    uint32_t _mainVideoSsrc = 0;
    std::unique_ptr<VideoSinkImpl> _videoSink;
    std::unique_ptr<webrtc::VideoBitrateAllocatorFactory> _videoBitrateAllocatorFactory;
    // Memory is managed by _channelManager
    cricket::VideoChannel *_videoChannel;
    // Memory is managed externally
    ChannelManager *_channelManager = nullptr;
    webrtc::Call *_call = nullptr;
};

} // namespace

class InstanceV2_4_0_0ImplInternal : public std::enable_shared_from_this<InstanceV2_4_0_0ImplInternal> {
public:
    InstanceV2_4_0_0ImplInternal(Descriptor &&descriptor, std::shared_ptr<Threads> threads) :
    _threads(threads),
    _rtcServers(descriptor.rtcServers),
    _enableP2P(descriptor.config.enableP2P),
    _encryptionKey(std::move(descriptor.encryptionKey)),
    _stateUpdated(descriptor.stateUpdated),
    _signalBarsUpdated(descriptor.signalBarsUpdated),
    _audioLevelUpdated(descriptor.audioLevelsUpdated),
    _remoteBatteryLevelIsLowUpdated(descriptor.remoteBatteryLevelIsLowUpdated),
    _remoteMediaStateUpdated(descriptor.remoteMediaStateUpdated),
    _remotePrefferedAspectRatioUpdated(descriptor.remotePrefferedAspectRatioUpdated),
    _signalingDataEmitted(descriptor.signalingDataEmitted),
    _createAudioDeviceModule(descriptor.createAudioDeviceModule),
    _eventLog(std::make_unique<webrtc::RtcEventLogNull>()),
    _taskQueueFactory(webrtc::CreateDefaultTaskQueueFactory()),
    _videoCapture(descriptor.videoCapture),
    _platformContext(descriptor.platformContext) {
    }

    ~InstanceV2_4_0_0ImplInternal() {
        _incomingAudioChannel.reset();
        _incomingVideoChannel.reset();
        _incomingScreencastChannel.reset();
        _outgoingAudioChannel.reset();
        _outgoingVideoChannel.reset();
        _outgoingScreencastChannel.reset();
        _currentSink.reset();

        _channelManager.reset();

        _threads->getWorkerThread()->BlockingCall([&]() {
            _call.reset();
            _audioDeviceModule = nullptr;
        });

        _networking->perform([](NativeNetworkingImpl *networking) {
            networking->stop();
        });

        _threads->getNetworkThread()->BlockingCall([]() {
        });
    }

    void start() {
        const auto weak = std::weak_ptr<InstanceV2_4_0_0ImplInternal>(shared_from_this());

        _networking.reset(new ThreadLocalObject<NativeNetworkingImpl>(_threads->getNetworkThread(), [weak, threads = _threads, encryptionKey = _encryptionKey, isOutgoing = _encryptionKey.isOutgoing, rtcServers = _rtcServers, enableP2P = _enableP2P]() {
            return new NativeNetworkingImpl(InstanceNetworking::Configuration{
                .encryptionKey = encryptionKey,
                .isOutgoing = isOutgoing,
                .enableStunMarking = false,
                .enableTCP = false,
                .enableP2P = enableP2P,
                .rtcServers = rtcServers,
                .stateUpdated = [threads, weak](const InstanceNetworking::State &state) {
                    threads->getMediaThread()->PostTask([=] {
                        const auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }
                        strong->onNetworkStateUpdated(state);
                    });
                },
                .candidateGathered = [threads, weak](const cricket::Candidate &candidate) {
                    threads->getMediaThread()->PostTask([=] {
                        const auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }

                        strong->sendCandidate(candidate);
                    });
                },
                .transportMessageReceived = [threads, weak](rtc::CopyOnWriteBuffer const &packet, bool isMissing) {
                    threads->getMediaThread()->PostTask([=] {
                        const auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }
                    });
                },
                .rtcpPacketReceived = [threads, weak](rtc::CopyOnWriteBuffer const &packet, int64_t timestamp) {
                    const auto strong = weak.lock();
                    if (!strong) {
                        return;
                    }
                    strong->_call->Receiver()->DeliverPacket(webrtc::MediaType::ANY, packet, timestamp);
                },
                .dataChannelStateUpdated = [threads, weak](bool isDataChannelOpen) {
                    threads->getMediaThread()->PostTask([=] {
                        const auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }
                        strong->onDataChannelStateUpdated(isDataChannelOpen);
                    });
                },
                .dataChannelMessageReceived = [threads, weak](std::string const &message) {
                    threads->getMediaThread()->PostTask([=] {
                        const auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }
                        strong->onDataChannelMessage(message);
                    });
                },
                .threads = threads
            });
        }));

        PlatformInterface::SharedInstance()->configurePlatformAudio();

        //setAudioInputDevice(_initialInputDeviceId);
        //setAudioOutputDevice(_initialOutputDeviceId);

        _threads->getWorkerThread()->BlockingCall([&]() {
            _audioDeviceModule = createAudioDeviceModule();
        });

        cricket::MediaEngineDependencies mediaDeps;
        mediaDeps.task_queue_factory = _taskQueueFactory.get();
        mediaDeps.audio_encoder_factory = webrtc::CreateAudioEncoderFactory<webrtc::AudioEncoderOpus, webrtc::AudioEncoderL16>();
        mediaDeps.audio_decoder_factory = webrtc::CreateAudioDecoderFactory<webrtc::AudioDecoderOpus, webrtc::AudioDecoderL16>();

        mediaDeps.video_encoder_factory = PlatformInterface::SharedInstance()->makeVideoEncoderFactory(_platformContext, true);
        mediaDeps.video_decoder_factory = PlatformInterface::SharedInstance()->makeVideoDecoderFactory(_platformContext);

        mediaDeps.adm = _audioDeviceModule;

        webrtc:: AudioProcessingBuilder builder;
        mediaDeps.audio_processing = builder.Create();


        _availableVideoFormats = mediaDeps.video_encoder_factory->GetSupportedFormats();

        std::unique_ptr<cricket::MediaEngineInterface> mediaEngine = cricket::CreateMediaEngine(std::move(mediaDeps));

        _channelManager = ChannelManager::Create(
            std::move(mediaEngine),
            _threads->getWorkerThread(),
            _threads->getNetworkThread()
        );

        webrtc::Call::Config callConfig(_eventLog.get(), _threads->getNetworkThread());
        callConfig.task_queue_factory = _taskQueueFactory.get();
        callConfig.trials = &fieldTrialsBasedConfig;

        _threads->getNetworkThread()->BlockingCall([&]() {
            _rtpTransport = _networking->getSyncAssumingSameThread()->getRtpTransport();
        });

        _threads->getWorkerThread()->BlockingCall([&]() {
            callConfig.audio_state = _channelManager->media_engine()->voice().GetAudioState();
            _call.reset(webrtc::Call::Create(callConfig));
        });

        _uniqueRandomIdGenerator.reset(new rtc::UniqueRandomIdGenerator());

        _threads->getNetworkThread()->BlockingCall([this]() {
            _rtpTransport = _networking->getSyncAssumingSameThread()->getRtpTransport();
        });

        _videoBitrateAllocatorFactory = webrtc::CreateBuiltinVideoBitrateAllocatorFactory();

        _networking->perform([](NativeNetworkingImpl *networking) {
            networking->start();
        });

        if (_videoCapture) {
            setVideoCapture(_videoCapture);
        }

        beginSignaling();

        adjustBitratePreferences(true);
    }

    void sendSignalingMessage(signaling_4_0_0::Message const &message) {
        auto data = message.serialize();

        RTC_LOG(LS_INFO) << "sendSignalingMessage: " << std::string(data.begin(), data.end());

        if (_signalingEncryption) {
            if (const auto encryptedData = _signalingEncryption->encryptOutgoing(data)) {
                _signalingDataEmitted(std::vector<uint8_t>(encryptedData->data(), encryptedData->data() + encryptedData->size()));
            } else {
                RTC_LOG(LS_ERROR) << "sendSignalingMessage: failed to encrypt payload";
            }
        } else {
            _signalingDataEmitted(data);
        }
    }

    void beginSignaling() {
        _signalingEncryption.reset(new SignalingEncryption(_encryptionKey));

        if (_encryptionKey.isOutgoing) {
            _outgoingAudioContent = OutgoingAudioChannel::createOutgoingContentDescription();
            _outgoingVideoContent = OutgoingVideoChannel::createOutgoingContentDescription(_availableVideoFormats, false);
            _outgoingScreencastContent = OutgoingVideoChannel::createOutgoingContentDescription(_availableVideoFormats, true);

            sendInitialSetup();
        }
    }

    void createNegotiatedChannels() {
        if (_negotiatedOutgoingVideoContent) {
            const auto weak = std::weak_ptr<InstanceV2_4_0_0ImplInternal>(shared_from_this());

            _outgoingVideoChannel.reset(new OutgoingVideoChannel(
                _threads,
                _channelManager.get(),
                _call.get(),
                _rtpTransport,
                _uniqueRandomIdGenerator.get(),
                _videoBitrateAllocatorFactory.get(),
                [threads = _threads, weak]() {
                    threads->getMediaThread()->PostTask([=] {
                        const auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }
                        strong->sendMediaState();
                    });
                },
                _negotiatedOutgoingVideoContent.value(),
                false
            ));

            if (_videoCapture) {
                _outgoingVideoChannel->setVideoCapture(_videoCapture);
            }
        }

        if (_negotiatedOutgoingScreencastContent) {
            const auto weak = std::weak_ptr<InstanceV2_4_0_0ImplInternal>(shared_from_this());

            _outgoingScreencastChannel.reset(new OutgoingVideoChannel(
                _threads,
                _channelManager.get(),
                _call.get(),
                _rtpTransport,
                _uniqueRandomIdGenerator.get(),
                _videoBitrateAllocatorFactory.get(),
                [threads = _threads, weak]() {
                    threads->getMediaThread()->PostTask([=] {
                        const auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }
                        strong->sendMediaState();
                    });
                },
                _negotiatedOutgoingScreencastContent.value(),
                true
            ));

            if (_screencastCapture) {
                _outgoingScreencastChannel->setVideoCapture(_screencastCapture);
            }
        }

        if (_negotiatedOutgoingAudioContent) {
            _outgoingAudioChannel.reset(new OutgoingAudioChannel(
                _call.get(),
                _channelManager.get(),
                _uniqueRandomIdGenerator.get(),
                &_audioSource,
                _rtpTransport,
                _negotiatedOutgoingAudioContent.value(),
                _threads
            ));
            _outgoingAudioChannel->setIsMuted(_isMicrophoneMuted);
        }

        adjustBitratePreferences(true);
    }

    void sendInitialSetup() {
        const auto weak = std::weak_ptr<InstanceV2_4_0_0ImplInternal>(shared_from_this());

        _networking->perform([weak, threads = _threads, isOutgoing = _encryptionKey.isOutgoing](NativeNetworkingImpl *networking) {
            auto localFingerprint = networking->getLocalFingerprint();
            std::string hash = localFingerprint->algorithm;
            std::string fingerprint = localFingerprint->GetRfc4572Fingerprint();
            std::string setup;
            if (isOutgoing) {
                setup = "actpass";
            } else {
                setup = "passive";
            }

            auto localIceParams = networking->getLocalIceParameters();
            std::string ufrag = localIceParams.ufrag;
            std::string pwd = localIceParams.pwd;

            threads->getMediaThread()->PostTask([weak, ufrag, pwd, hash, fingerprint, setup, localIceParams]() {
                const auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                signaling_4_0_0::InitialSetupMessage data;

                if (strong->_outgoingAudioContent) {
                    data.audio = strong->_outgoingAudioContent.value();
                }
                if (strong->_outgoingVideoContent) {
                    data.video = strong->_outgoingVideoContent.value();
                }
                if (strong->_outgoingScreencastContent) {
                    data.screencast = strong->_outgoingScreencastContent.value();
                }

                data.ufrag = ufrag;
                data.pwd = pwd;

                signaling_4_0_0::DtlsFingerprint dtlsFingerprint;
                dtlsFingerprint.hash = hash;
                dtlsFingerprint.fingerprint = fingerprint;
                dtlsFingerprint.setup = setup;
                data.fingerprints.push_back(std::move(dtlsFingerprint));

                signaling_4_0_0::Message message;
                message.data = std::move(data);
                strong->sendSignalingMessage(message);
            });
        });
    }

    void receiveSignalingData(const std::vector<uint8_t> &data) {
        std::vector<uint8_t> decryptedData;

        if (_signalingEncryption) {
            const auto rawDecryptedData = _signalingEncryption->decryptIncoming(data);
            if (!rawDecryptedData) {
                RTC_LOG(LS_ERROR) << "receiveSignalingData: could not decrypt payload";

                return;
            }

            decryptedData = std::vector<uint8_t>(rawDecryptedData->data(), rawDecryptedData->data() + rawDecryptedData->size());
        } else {
            decryptedData = data;
        }

        processSignalingData(decryptedData);
    }

    void processSignalingData(const std::vector<uint8_t> &data) {
        RTC_LOG(LS_INFO) << "processSignalingData: " << std::string(data.begin(), data.end());

        const auto message = signaling_4_0_0::Message::parse(data);
        if (!message) {
            return;
        }
        const auto messageData = &message->data;
        if (const auto initialSetup = absl::get_if<signaling_4_0_0::InitialSetupMessage>(messageData)) {
            PeerIceParameters remoteIceParameters;
            remoteIceParameters.ufrag = initialSetup->ufrag;
            remoteIceParameters.pwd = initialSetup->pwd;

            std::unique_ptr<rtc::SSLFingerprint> fingerprint;
            std::string sslSetup;
            if (initialSetup->fingerprints.size() != 0) {
                fingerprint = rtc::SSLFingerprint::CreateUniqueFromRfc4572(initialSetup->fingerprints[0].hash, initialSetup->fingerprints[0].fingerprint);
                sslSetup = initialSetup->fingerprints[0].setup;
            }

            _networking->perform([threads = _threads, remoteIceParameters = std::move(remoteIceParameters), fingerprint = std::move(fingerprint), sslSetup = std::move(sslSetup)](NativeNetworkingImpl *networking) {
                networking->setRemoteParams(remoteIceParameters, fingerprint.get(), sslSetup);
            });

            if (const auto audio = initialSetup->audio) {
                if (_encryptionKey.isOutgoing) {
                    if (_outgoingAudioContent) {
                        _negotiatedOutgoingAudioContent = negotiateMediaContent<cricket::AudioCodec>(_outgoingAudioContent.value(), _outgoingAudioContent.value(), audio.value(), false);
                        const auto incomingAudioContent = negotiateMediaContent<cricket::AudioCodec>(audio.value(), _outgoingAudioContent.value(), audio.value(), false);

                        signaling_4_0_0::MediaContent outgoingAudioContent;

                        outgoingAudioContent.ssrc = _outgoingAudioContent->ssrc;
                        outgoingAudioContent.ssrcGroups = _outgoingAudioContent->ssrcGroups;
                        outgoingAudioContent.rtpExtensions = _negotiatedOutgoingAudioContent->rtpExtensions;
                        outgoingAudioContent.payloadTypes = getPayloadTypesFromAudioCodecs(_negotiatedOutgoingAudioContent->codecs);

                        _outgoingAudioContent = std::move(outgoingAudioContent);

                        _incomingAudioChannel.reset(new IncomingV2AudioChannel(
                            _channelManager.get(),
                            _call.get(),
                            _rtpTransport,
                            _uniqueRandomIdGenerator.get(),
                            incomingAudioContent,
                            _threads
                        ));
                    }
                } else {
                    const auto generatedOutgoingContent = OutgoingAudioChannel::createOutgoingContentDescription();

                    if (generatedOutgoingContent) {
                        _negotiatedOutgoingAudioContent = negotiateMediaContent<cricket::AudioCodec>(generatedOutgoingContent.value(), generatedOutgoingContent.value(), audio.value(), true);
                        const auto incomingAudioContent = negotiateMediaContent<cricket::AudioCodec>(audio.value(), generatedOutgoingContent.value(), audio.value(), true);

                        if (_negotiatedOutgoingAudioContent) {
                            signaling_4_0_0::MediaContent outgoingAudioContent;

                            outgoingAudioContent.ssrc = generatedOutgoingContent->ssrc;
                            outgoingAudioContent.ssrcGroups = generatedOutgoingContent->ssrcGroups;
                            outgoingAudioContent.rtpExtensions = _negotiatedOutgoingAudioContent->rtpExtensions;
                            outgoingAudioContent.payloadTypes = getPayloadTypesFromAudioCodecs(_negotiatedOutgoingAudioContent->codecs);

                            _outgoingAudioContent = std::move(outgoingAudioContent);

                            _incomingAudioChannel.reset(new IncomingV2AudioChannel(
                                _channelManager.get(),
                                _call.get(),
                                _rtpTransport,
                                _uniqueRandomIdGenerator.get(),
                                incomingAudioContent,
                                _threads
                            ));
                        }
                    }
                }
            }

            if (const auto video = initialSetup->video) {
                if (_encryptionKey.isOutgoing) {
                    if (_outgoingVideoContent) {
                        _negotiatedOutgoingVideoContent = negotiateMediaContent<cricket::VideoCodec>(_outgoingVideoContent.value(), _outgoingVideoContent.value(), video.value(), false);
                        const auto incomingVideoContent = negotiateMediaContent<cricket::VideoCodec>(video.value(), _outgoingVideoContent.value(), video.value(), false);

                        signaling_4_0_0::MediaContent outgoingVideoContent;

                        outgoingVideoContent.ssrc = _outgoingVideoContent->ssrc;
                        outgoingVideoContent.ssrcGroups = _outgoingVideoContent->ssrcGroups;
                        outgoingVideoContent.rtpExtensions = _negotiatedOutgoingVideoContent->rtpExtensions;
                        outgoingVideoContent.payloadTypes = getPayloadTypesFromVideoCodecs(_negotiatedOutgoingVideoContent->codecs);

                        _outgoingVideoContent = std::move(outgoingVideoContent);

                        _incomingVideoChannel.reset(new IncomingV2VideoChannel(
                            _channelManager.get(),
                            _call.get(),
                            _rtpTransport,
                            _uniqueRandomIdGenerator.get(),
                            incomingVideoContent,
                            "1",
                            _threads
                        ));
                        _incomingVideoChannel->addSink(_currentSink);
                    }
                } else {
                    const auto generatedOutgoingContent = OutgoingVideoChannel::createOutgoingContentDescription(_availableVideoFormats, false);

                    if (generatedOutgoingContent) {
                        _negotiatedOutgoingVideoContent = negotiateMediaContent<cricket::VideoCodec>(generatedOutgoingContent.value(), generatedOutgoingContent.value(), video.value(), true);
                        const auto incomingVideoContent = negotiateMediaContent<cricket::VideoCodec>(video.value(), generatedOutgoingContent.value(), video.value(), true);

                        if (_negotiatedOutgoingVideoContent) {
                            signaling_4_0_0::MediaContent outgoingVideoContent;

                            outgoingVideoContent.ssrc = generatedOutgoingContent->ssrc;
                            outgoingVideoContent.ssrcGroups = generatedOutgoingContent->ssrcGroups;
                            outgoingVideoContent.rtpExtensions = _negotiatedOutgoingVideoContent->rtpExtensions;
                            outgoingVideoContent.payloadTypes = getPayloadTypesFromVideoCodecs(_negotiatedOutgoingVideoContent->codecs);

                            _outgoingVideoContent = std::move(outgoingVideoContent);

                            _incomingVideoChannel.reset(new IncomingV2VideoChannel(
                                _channelManager.get(),
                                _call.get(),
                                _rtpTransport,
                                _uniqueRandomIdGenerator.get(),
                                incomingVideoContent,
                                "1",
                                _threads
                            ));
                            _incomingVideoChannel->addSink(_currentSink);
                        }
                    }
                }
            }

            if (const auto screencast = initialSetup->screencast) {
                if (_encryptionKey.isOutgoing) {
                    if (_outgoingScreencastContent) {
                        _negotiatedOutgoingScreencastContent = negotiateMediaContent<cricket::VideoCodec>(_outgoingScreencastContent.value(), _outgoingScreencastContent.value(), screencast.value(), false);
                        const auto incomingScreencastContent = negotiateMediaContent<cricket::VideoCodec>(screencast.value(), _outgoingScreencastContent.value(), screencast.value(), false);

                        signaling_4_0_0::MediaContent outgoingScreencastContent;

                        outgoingScreencastContent.ssrc = _outgoingScreencastContent->ssrc;
                        outgoingScreencastContent.ssrcGroups = _outgoingScreencastContent->ssrcGroups;
                        outgoingScreencastContent.rtpExtensions = _negotiatedOutgoingScreencastContent->rtpExtensions;
                        outgoingScreencastContent.payloadTypes = getPayloadTypesFromVideoCodecs(_negotiatedOutgoingScreencastContent->codecs);

                        _outgoingScreencastContent = std::move(outgoingScreencastContent);

                        _incomingScreencastChannel.reset(new IncomingV2VideoChannel(
                            _channelManager.get(),
                            _call.get(),
                            _rtpTransport,
                            _uniqueRandomIdGenerator.get(),
                            incomingScreencastContent,
                            "2",
                            _threads
                        ));
                        _incomingScreencastChannel->addSink(_currentSink);
                    }
                } else {
                    const auto generatedOutgoingContent = OutgoingVideoChannel::createOutgoingContentDescription(_availableVideoFormats, true);

                    if (generatedOutgoingContent) {
                        _negotiatedOutgoingScreencastContent = negotiateMediaContent<cricket::VideoCodec>(generatedOutgoingContent.value(), generatedOutgoingContent.value(), screencast.value(), true);
                        const auto incomingScreencastContent = negotiateMediaContent<cricket::VideoCodec>(screencast.value(), generatedOutgoingContent.value(), screencast.value(), true);

                        if (_negotiatedOutgoingScreencastContent) {
                            signaling_4_0_0::MediaContent outgoingScreencastContent;

                            outgoingScreencastContent.ssrc = generatedOutgoingContent->ssrc;
                            outgoingScreencastContent.ssrcGroups = generatedOutgoingContent->ssrcGroups;
                            outgoingScreencastContent.rtpExtensions = _negotiatedOutgoingScreencastContent->rtpExtensions;
                            outgoingScreencastContent.payloadTypes = getPayloadTypesFromVideoCodecs(_negotiatedOutgoingScreencastContent->codecs);

                            _outgoingScreencastContent = std::move(outgoingScreencastContent);

                            _incomingScreencastChannel.reset(new IncomingV2VideoChannel(
                                _channelManager.get(),
                                _call.get(),
                                _rtpTransport,
                                _uniqueRandomIdGenerator.get(),
                                incomingScreencastContent,
                                "2",
                                _threads
                            ));
                            _incomingScreencastChannel->addSink(_currentSink);
                        }
                    }
                }
            }

            createNegotiatedChannels();

            if (!_encryptionKey.isOutgoing) {
                sendInitialSetup();
            }

            _handshakeCompleted = true;
            commitPendingIceCandidates();
        } else if (const auto candidatesList = absl::get_if<signaling_4_0_0::CandidatesMessage>(messageData)) {
            for (const auto &candidate : candidatesList->iceCandidates) {
                webrtc::JsepIceCandidate parseCandidate{ std::string(), 0 };
                if (!parseCandidate.Initialize(candidate.sdpString, nullptr)) {
                    RTC_LOG(LS_ERROR) << "Could not parse candidate: " << candidate.sdpString;
                    continue;
                }
                _pendingIceCandidates.push_back(parseCandidate.candidate());
            }

            if (_handshakeCompleted) {
                commitPendingIceCandidates();
            }
        } else if (const auto mediaState = absl::get_if<signaling_4_0_0::MediaStateMessage>(messageData)) {
            AudioState mappedAudioState;
            if (mediaState->isMuted) {
                mappedAudioState = AudioState::Muted;
            } else {
                mappedAudioState = AudioState::Active;
            }

            VideoState mappedVideoState;
            switch (mediaState->videoState) {
                case signaling_4_0_0::MediaStateMessage::VideoState::Inactive: {
                    mappedVideoState = VideoState::Inactive;
                    break;
                }
                case signaling_4_0_0::MediaStateMessage::VideoState::Suspended: {
                    mappedVideoState = VideoState::Paused;
                    break;
                }
                case signaling_4_0_0::MediaStateMessage::VideoState::Active: {
                    mappedVideoState = VideoState::Active;
                    break;
                }
                default: {
                    RTC_FATAL() << "Unknown videoState";
                    break;
                }
            }

            VideoState mappedScreencastState;
            switch (mediaState->screencastState) {
                case signaling_4_0_0::MediaStateMessage::VideoState::Inactive: {
                    mappedScreencastState = VideoState::Inactive;
                    break;
                }
                case signaling_4_0_0::MediaStateMessage::VideoState::Suspended: {
                    mappedScreencastState = VideoState::Paused;
                    break;
                }
                case signaling_4_0_0::MediaStateMessage::VideoState::Active: {
                    mappedScreencastState = VideoState::Active;
                    break;
                }
                default: {
                    RTC_FATAL() << "Unknown videoState";
                    break;
                }
            }

            VideoState effectiveVideoState = mappedVideoState;
            if (mappedScreencastState == VideoState::Active || mappedScreencastState == VideoState::Paused) {
                effectiveVideoState = mappedScreencastState;
            }

            if (_remoteMediaStateUpdated) {
                _remoteMediaStateUpdated(mappedAudioState, effectiveVideoState);
            }

            if (_remoteBatteryLevelIsLowUpdated) {
                _remoteBatteryLevelIsLowUpdated(mediaState->isBatteryLow);
            }
        }
    }

    void commitPendingIceCandidates() {
        if (_pendingIceCandidates.size() == 0) {
            return;
        }
        _networking->perform([threads = _threads, parsedCandidates = _pendingIceCandidates](NativeNetworkingImpl *networking) {
            networking->addCandidates(parsedCandidates);
        });
        _pendingIceCandidates.clear();
    }

    void onNetworkStateUpdated(InstanceNetworking::State const &state) {
        State mappedState;
        if (state.isReadyToSendData) {
            mappedState = State::Established;
        } else {
            mappedState = State::Reconnecting;
        }
        _stateUpdated(mappedState);
    }

    void onDataChannelStateUpdated(bool isDataChannelOpen) {
        if (_isDataChannelOpen != isDataChannelOpen) {
            _isDataChannelOpen = isDataChannelOpen;

            if (_isDataChannelOpen) {
                sendMediaState();
            }
        }
    }

    void sendDataChannelMessage(signaling_4_0_0::Message const &message) {
        if (!_isDataChannelOpen) {
            RTC_LOG(LS_ERROR) << "sendDataChannelMessage called, but data channel is not open";
            return;
        }
        auto data = message.serialize();
        std::string stringData(data.begin(), data.end());
        RTC_LOG(LS_INFO) << "sendDataChannelMessage: " << stringData;
        _networking->perform([stringData = std::move(stringData)](NativeNetworkingImpl *networking) {
            networking->sendDataChannelMessage(stringData);
        });
    }

    void onDataChannelMessage(std::string const &message) {
        RTC_LOG(LS_INFO) << "dataChannelMessage received: " << message;
        std::vector<uint8_t> data(message.begin(), message.end());
        processSignalingData(data);
    }

    void sendMediaState() {
        if (!_isDataChannelOpen) {
            return;
        }
        signaling_4_0_0::Message message;
        signaling_4_0_0::MediaStateMessage data;
        data.isMuted = _isMicrophoneMuted;
        data.isBatteryLow = _isBatteryLow;
        if (_outgoingVideoChannel) {
            if (_outgoingVideoChannel->videoCapture()) {
                data.videoState = signaling_4_0_0::MediaStateMessage::VideoState::Active;
            } else{
                data.videoState = signaling_4_0_0::MediaStateMessage::VideoState::Inactive;
            }
            data.videoRotation = _outgoingVideoChannel->getRotation();
        } else {
            data.videoState = signaling_4_0_0::MediaStateMessage::VideoState::Inactive;
            data.videoRotation = signaling_4_0_0::MediaStateMessage::VideoRotation::Rotation0;
        }
        if (_outgoingScreencastChannel) {
            if (_outgoingScreencastChannel->videoCapture()) {
                data.screencastState = signaling_4_0_0::MediaStateMessage::VideoState::Active;
            } else{
                data.screencastState = signaling_4_0_0::MediaStateMessage::VideoState::Inactive;
            }
        } else {
            data.screencastState = signaling_4_0_0::MediaStateMessage::VideoState::Inactive;
        }
        message.data = std::move(data);
        sendDataChannelMessage(message);
    }

    void sendCandidate(const cricket::Candidate &candidate) {
        cricket::Candidate patchedCandidate = candidate;
        patchedCandidate.set_component(1);

        signaling_4_0_0::CandidatesMessage data;

        signaling_4_0_0::IceCandidate serializedCandidate;

        webrtc::JsepIceCandidate iceCandidate{ std::string(), 0 };
        iceCandidate.SetCandidate(patchedCandidate);
        std::string serialized;
        const auto success = iceCandidate.ToString(&serialized);
        assert(success);
        (void)success;

        serializedCandidate.sdpString = serialized;

        data.iceCandidates.push_back(std::move(serializedCandidate));

        signaling_4_0_0::Message message;
        message.data = std::move(data);
        sendSignalingMessage(message);
    }

    void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) {
        auto videoCaptureImpl = GetVideoCaptureAssumingSameThread(videoCapture.get());
        if (videoCaptureImpl) {
            if (videoCaptureImpl->isScreenCapture()) {
                _videoCapture = nullptr;
                _screencastCapture = videoCapture;

                if (_outgoingVideoChannel) {
                    _outgoingVideoChannel->setVideoCapture(nullptr);
                }

                if (_outgoingScreencastChannel) {
                    _outgoingScreencastChannel->setVideoCapture(videoCapture);
                }

                sendMediaState();
                adjustBitratePreferences(true);
            } else {
                _videoCapture = videoCapture;
                _screencastCapture = nullptr;

                if (_outgoingVideoChannel) {
                    _outgoingVideoChannel->setVideoCapture(videoCapture);
                }

                if (_outgoingScreencastChannel) {
                    _outgoingScreencastChannel->setVideoCapture(nullptr);
                }

                sendMediaState();
                adjustBitratePreferences(true);
            }
        } else {
            _videoCapture = nullptr;
            _screencastCapture = nullptr;

            if (_outgoingVideoChannel) {
                _outgoingVideoChannel->setVideoCapture(nullptr);
            }

            if (_outgoingScreencastChannel) {
                _outgoingScreencastChannel->setVideoCapture(nullptr);
            }

            sendMediaState();
            adjustBitratePreferences(true);
        }
    }

    void setRequestedVideoAspect(float aspect) {
    }

    void setNetworkType(NetworkType networkType) {

    }

    void setMuteMicrophone(bool muteMicrophone) {
        if (_isMicrophoneMuted != muteMicrophone) {
            _isMicrophoneMuted = muteMicrophone;

            if (_outgoingAudioChannel) {
                _outgoingAudioChannel->setIsMuted(muteMicrophone);
            }

            sendMediaState();
        }
    }

    void setIncomingVideoOutput(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
        _currentSink = sink;
        if (_incomingVideoChannel) {
            _incomingVideoChannel->addSink(sink);
        }
        if (_incomingScreencastChannel) {
            _incomingScreencastChannel->addSink(sink);
        }
    }

    void setAudioInputDevice(std::string id) {

    }

    void setAudioOutputDevice(std::string id) {

    }

    void setIsLowBatteryLevel(bool isLowBatteryLevel) {
        if (_isBatteryLow != isLowBatteryLevel) {
            _isBatteryLow = isLowBatteryLevel;
            sendMediaState();
        }
    }

    void stop(std::function<void(FinalState)> completion) {
        completion({});
    }

    void adjustBitratePreferences(bool resetStartBitrate) {
        webrtc::BitrateConstraints preferences;
        if (_videoCapture || _screencastCapture) {
            preferences.min_bitrate_bps = 64000;
            if (resetStartBitrate) {
                preferences.start_bitrate_bps = (100 + 800 + 32 + 100) * 1000;
            }
            preferences.max_bitrate_bps = (100 + 200 + 800 + 32 + 100) * 1000;
        } else {
            preferences.min_bitrate_bps = 32000;
            if (resetStartBitrate) {
                preferences.start_bitrate_bps = 32000;
            }
            preferences.max_bitrate_bps = 32000;
        }

        _call->GetTransportControllerSend()->SetSdpBitrateParameters(preferences);
    }

private:
    rtc::scoped_refptr<webrtc::AudioDeviceModule> createAudioDeviceModule() {
        const auto create = [&](webrtc::AudioDeviceModule::AudioLayer layer) {
#ifdef WEBRTC_IOS
            return rtc::make_ref_counted<webrtc::tgcalls_ios_adm::AudioDeviceModuleIOS>(false, false, 1);
#else
            return webrtc::AudioDeviceModule::Create(
                layer,
                _taskQueueFactory.get());
#endif
        };
        const auto check = [&](const rtc::scoped_refptr<webrtc::AudioDeviceModule> &result) {
            return (result && result->Init() == 0) ? result : nullptr;
        };
        if (_createAudioDeviceModule) {
            if (const auto result = check(_createAudioDeviceModule(_taskQueueFactory.get()))) {
                return result;
            }
        }
        return check(create(webrtc::AudioDeviceModule::kPlatformDefaultAudio));
    }

private:
    std::shared_ptr<Threads> _threads;
    std::vector<RtcServer> _rtcServers;
    bool _enableP2P = false;
    EncryptionKey _encryptionKey;
    std::function<void(State)> _stateUpdated;
    std::function<void(int)> _signalBarsUpdated;
    std::function<void(float, float)> _audioLevelUpdated;
    std::function<void(bool)> _remoteBatteryLevelIsLowUpdated;
    std::function<void(AudioState, VideoState)> _remoteMediaStateUpdated;
    std::function<void(float)> _remotePrefferedAspectRatioUpdated;
    std::function<void(const std::vector<uint8_t> &)> _signalingDataEmitted;
    std::function<rtc::scoped_refptr<webrtc::AudioDeviceModule>(webrtc::TaskQueueFactory*)> _createAudioDeviceModule;

    std::unique_ptr<SignalingEncryption> _signalingEncryption;

    bool _handshakeCompleted = false;
    std::vector<cricket::Candidate> _pendingIceCandidates;
    bool _isDataChannelOpen = false;

    std::unique_ptr<webrtc::RtcEventLogNull> _eventLog;
    std::unique_ptr<webrtc::TaskQueueFactory> _taskQueueFactory;
    std::unique_ptr<webrtc::Call> _call;
    webrtc::LocalAudioSinkAdapter _audioSource;
    rtc::scoped_refptr<webrtc::AudioDeviceModule> _audioDeviceModule;

    std::unique_ptr<rtc::UniqueRandomIdGenerator> _uniqueRandomIdGenerator;
    webrtc::RtpTransport *_rtpTransport = nullptr;
    std::unique_ptr<ChannelManager> _channelManager;
    std::unique_ptr<webrtc::VideoBitrateAllocatorFactory> _videoBitrateAllocatorFactory;

    std::shared_ptr<ThreadLocalObject<NativeNetworkingImpl>> _networking;

    absl::optional<signaling_4_0_0::MediaContent> _outgoingAudioContent;
    absl::optional<NegotiatedMediaContent<cricket::AudioCodec>> _negotiatedOutgoingAudioContent;

    std::unique_ptr<OutgoingAudioChannel> _outgoingAudioChannel;
    bool _isMicrophoneMuted = false;

    std::vector<webrtc::SdpVideoFormat> _availableVideoFormats;

    absl::optional<signaling_4_0_0::MediaContent> _outgoingVideoContent;
    absl::optional<NegotiatedMediaContent<cricket::VideoCodec>> _negotiatedOutgoingVideoContent;

    absl::optional<signaling_4_0_0::MediaContent> _outgoingScreencastContent;
    absl::optional<NegotiatedMediaContent<cricket::VideoCodec>> _negotiatedOutgoingScreencastContent;

    std::shared_ptr<OutgoingVideoChannel> _outgoingVideoChannel;
    std::shared_ptr<OutgoingVideoChannel> _outgoingScreencastChannel;

    bool _isBatteryLow = false;

    std::unique_ptr<IncomingV2AudioChannel> _incomingAudioChannel;
    std::unique_ptr<IncomingV2VideoChannel> _incomingVideoChannel;
    std::unique_ptr<IncomingV2VideoChannel> _incomingScreencastChannel;

    std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> _currentSink;

    std::shared_ptr<VideoCaptureInterface> _videoCapture;
    std::shared_ptr<VideoCaptureInterface> _screencastCapture;
    std::shared_ptr<PlatformContext> _platformContext;
};

InstanceV2_4_0_0Impl::InstanceV2_4_0_0Impl(Descriptor &&descriptor) {
    if (descriptor.config.logPath.data.size() != 0) {
        _logSink = std::make_unique<LogSinkImpl>(descriptor.config.logPath);
    }
    rtc::LogMessage::LogToDebug(rtc::LS_INFO);
    rtc::LogMessage::SetLogToStderr(false);
    if (_logSink) {
        rtc::LogMessage::AddLogToStream(_logSink.get(), rtc::LS_INFO);
    }

    _threads = StaticThreads::getThreads();
    _internal.reset(new ThreadLocalObject<InstanceV2_4_0_0ImplInternal>(_threads->getMediaThread(), [descriptor = std::move(descriptor), threads = _threads]() mutable {
        return new InstanceV2_4_0_0ImplInternal(std::move(descriptor), threads);
    }));
    _internal->perform([](InstanceV2_4_0_0ImplInternal *internal) {
        internal->start();
    });
}

InstanceV2_4_0_0Impl::~InstanceV2_4_0_0Impl() {
    rtc::LogMessage::RemoveLogToStream(_logSink.get());
}

void InstanceV2_4_0_0Impl::receiveSignalingData(const std::vector<uint8_t> &data) {
    _internal->perform([data](InstanceV2_4_0_0ImplInternal *internal) {
        internal->receiveSignalingData(data);
    });
}

void InstanceV2_4_0_0Impl::setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) {
    _internal->perform([videoCapture](InstanceV2_4_0_0ImplInternal *internal) {
        internal->setVideoCapture(videoCapture);
    });
}

void InstanceV2_4_0_0Impl::setRequestedVideoAspect(float aspect) {
    _internal->perform([aspect](InstanceV2_4_0_0ImplInternal *internal) {
        internal->setRequestedVideoAspect(aspect);
    });
}

void InstanceV2_4_0_0Impl::setNetworkType(NetworkType networkType) {
    _internal->perform([networkType](InstanceV2_4_0_0ImplInternal *internal) {
        internal->setNetworkType(networkType);
    });
}

void InstanceV2_4_0_0Impl::setMuteMicrophone(bool muteMicrophone) {
    _internal->perform([muteMicrophone](InstanceV2_4_0_0ImplInternal *internal) {
        internal->setMuteMicrophone(muteMicrophone);
    });
}

void InstanceV2_4_0_0Impl::setIncomingVideoOutput(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    _internal->perform([sink](InstanceV2_4_0_0ImplInternal *internal) {
        internal->setIncomingVideoOutput(sink);
    });
}

void InstanceV2_4_0_0Impl::setAudioInputDevice(std::string id) {
    _internal->perform([id](InstanceV2_4_0_0ImplInternal *internal) {
        internal->setAudioInputDevice(id);
    });
}

void InstanceV2_4_0_0Impl::setAudioOutputDevice(std::string id) {
    _internal->perform([id](InstanceV2_4_0_0ImplInternal *internal) {
        internal->setAudioOutputDevice(id);
    });
}

void InstanceV2_4_0_0Impl::setIsLowBatteryLevel(bool isLowBatteryLevel) {
    _internal->perform([isLowBatteryLevel](InstanceV2_4_0_0ImplInternal *internal) {
        internal->setIsLowBatteryLevel(isLowBatteryLevel);
    });
}

void InstanceV2_4_0_0Impl::setInputVolume(float level) {
}

void InstanceV2_4_0_0Impl::setOutputVolume(float level) {
}

void InstanceV2_4_0_0Impl::setAudioOutputDuckingEnabled(bool enabled) {
}

void InstanceV2_4_0_0Impl::setAudioOutputGainControlEnabled(bool enabled) {
}

void InstanceV2_4_0_0Impl::setEchoCancellationStrength(int strength) {
}

std::vector<std::string> InstanceV2_4_0_0Impl::GetVersions() {
    std::vector<std::string> result;
    result.push_back("6.0.0");
    return result;
}

int InstanceV2_4_0_0Impl::GetConnectionMaxLayer() {
    return 92;
}

std::string InstanceV2_4_0_0Impl::getLastError() {
    return "";
}

std::string InstanceV2_4_0_0Impl::getDebugInfo() {
    return "";
}

int64_t InstanceV2_4_0_0Impl::getPreferredRelayId() {
    return 0;
}

TrafficStats InstanceV2_4_0_0Impl::getTrafficStats() {
    return {};
}

PersistentState InstanceV2_4_0_0Impl::getPersistentState() {
    return {};
}

void InstanceV2_4_0_0Impl::stop(std::function<void(FinalState)> completion) {
    std::string debugLog;
    if (_logSink) {
        debugLog = _logSink->result();
    }
    _internal->perform([completion, debugLog = std::move(debugLog)](InstanceV2_4_0_0ImplInternal *internal) mutable {
        internal->stop([completion, debugLog = std::move(debugLog)](FinalState finalState) mutable {
            finalState.debugLog = debugLog;
            completion(finalState);
        });
    });
}

template <>
bool Register<InstanceV2_4_0_0Impl>() {
    return Meta::RegisterOne<InstanceV2_4_0_0Impl>();
}

} // namespace tgcalls

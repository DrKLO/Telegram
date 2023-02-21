#include "v2/ContentNegotiation.h"

#include "rtc_base/rtc_certificate_generator.h"
#include "media/base/media_engine.h"

#include <sstream>

namespace tgcalls {

namespace {

signaling::MediaContent convertContentInfoToSingalingContent(cricket::ContentInfo const &content) {
    signaling::MediaContent mappedContent;
    
    switch (content.media_description()->type()) {
        case cricket::MediaType::MEDIA_TYPE_AUDIO: {
            mappedContent.type = signaling::MediaContent::Type::Audio;
            
            for (const auto &codec : content.media_description()->as_audio()->codecs()) {
                signaling::PayloadType mappedPayloadType;
                mappedPayloadType.id = codec.id;
                mappedPayloadType.name = codec.name;
                mappedPayloadType.clockrate = codec.clockrate;
                mappedPayloadType.channels = (uint32_t)codec.channels;
                
                for (const auto &feedbackType : codec.feedback_params.params()) {
                    signaling::FeedbackType mappedFeedbackType;
                    mappedFeedbackType.type = feedbackType.id();
                    mappedFeedbackType.subtype = feedbackType.param();
                    mappedPayloadType.feedbackTypes.push_back(std::move(mappedFeedbackType));
                }
                
                for (const auto &parameter : codec.params) {
                    mappedPayloadType.parameters.push_back(std::make_pair(parameter.first, parameter.second));
                }
                std::sort(mappedPayloadType.parameters.begin(), mappedPayloadType.parameters.end(), [](std::pair<std::string, std::string> const &lhs, std::pair<std::string, std::string> const &rhs) -> bool {
                    return lhs.first < rhs.first;
                });
                
                mappedContent.payloadTypes.push_back(std::move(mappedPayloadType));
            }
            break;
        }
        case cricket::MediaType::MEDIA_TYPE_VIDEO: {
            mappedContent.type = signaling::MediaContent::Type::Video;
            
            for (const auto &codec : content.media_description()->as_video()->codecs()) {
                signaling::PayloadType mappedPayloadType;
                mappedPayloadType.id = codec.id;
                mappedPayloadType.name = codec.name;
                mappedPayloadType.clockrate = codec.clockrate;
                mappedPayloadType.channels = 0;
                
                for (const auto &feedbackType : codec.feedback_params.params()) {
                    signaling::FeedbackType mappedFeedbackType;
                    mappedFeedbackType.type = feedbackType.id();
                    mappedFeedbackType.subtype = feedbackType.param();
                    mappedPayloadType.feedbackTypes.push_back(std::move(mappedFeedbackType));
                }
                
                for (const auto &parameter : codec.params) {
                    mappedPayloadType.parameters.push_back(std::make_pair(parameter.first, parameter.second));
                }
                std::sort(mappedPayloadType.parameters.begin(), mappedPayloadType.parameters.end(), [](std::pair<std::string, std::string> const &lhs, std::pair<std::string, std::string> const &rhs) -> bool {
                    return lhs.first < rhs.first;
                });
                
                mappedContent.payloadTypes.push_back(std::move(mappedPayloadType));
            }
            break;
        }
        default: {
            RTC_FATAL() << "Unknown media type";
            break;
        }
    }
    
    if (!content.media_description()->streams().empty()) {
        mappedContent.ssrc = content.media_description()->streams()[0].first_ssrc();
        for (const auto &ssrcGroup : content.media_description()->streams()[0].ssrc_groups) {
            signaling::SsrcGroup mappedSsrcGroup;
            mappedSsrcGroup.semantics = ssrcGroup.semantics;
            mappedSsrcGroup.ssrcs = ssrcGroup.ssrcs;
            mappedContent.ssrcGroups.push_back(std::move(mappedSsrcGroup));
        }
    }
    
    for (const auto &extension : content.media_description()->rtp_header_extensions()) {
        mappedContent.rtpExtensions.push_back(extension);
    }
    
    return mappedContent;
}

cricket::ContentInfo convertSingalingContentToContentInfo(std::string const &contentId, signaling::MediaContent const &content, webrtc::RtpTransceiverDirection direction) {
    std::unique_ptr<cricket::MediaContentDescription> contentDescription;
    
    switch (content.type) {
        case signaling::MediaContent::Type::Audio: {
            auto audioDescription = std::make_unique<cricket::AudioContentDescription>();
            
            for (const auto &payloadType : content.payloadTypes) {
                cricket::AudioCodec mappedCodec((int)payloadType.id, payloadType.name, (int)payloadType.clockrate, 0, payloadType.channels);
                for (const auto &parameter : payloadType.parameters) {
                    mappedCodec.params.insert(parameter);
                }
                for (const auto &feedbackParam : payloadType.feedbackTypes) {
                    mappedCodec.AddFeedbackParam(cricket::FeedbackParam(feedbackParam.type, feedbackParam.subtype));
                }
                audioDescription->AddCodec(mappedCodec);
            }
            
            contentDescription = std::move(audioDescription);
            
            break;
        }
        case signaling::MediaContent::Type::Video: {
            auto videoDescription = std::make_unique<cricket::VideoContentDescription>();
            
            for (const auto &payloadType : content.payloadTypes) {
                cricket::VideoCodec mappedCodec((int)payloadType.id, payloadType.name);
                for (const auto &parameter : payloadType.parameters) {
                    mappedCodec.params.insert(parameter);
                }
                for (const auto &feedbackParam : payloadType.feedbackTypes) {
                    mappedCodec.AddFeedbackParam(cricket::FeedbackParam(feedbackParam.type, feedbackParam.subtype));
                }
                videoDescription->AddCodec(mappedCodec);
            }
            
            contentDescription = std::move(videoDescription);
            
            break;
        }
        default: {
            RTC_FATAL() << "Unknown media type";
            break;
        }
    }
    
    cricket::StreamParams streamParams;
    streamParams.id = contentId;
    streamParams.set_stream_ids({ contentId });
    streamParams.add_ssrc(content.ssrc);
    for (const auto &ssrcGroup : content.ssrcGroups) {
        streamParams.ssrc_groups.push_back(cricket::SsrcGroup(ssrcGroup.semantics, ssrcGroup.ssrcs));
        for (const auto &ssrc : ssrcGroup.ssrcs) {
            if (!streamParams.has_ssrc(ssrc)) {
                streamParams.add_ssrc(ssrc);
            }
        }
    }
    contentDescription->AddStream(streamParams);
    
    for (const auto &extension : content.rtpExtensions) {
        contentDescription->AddRtpHeaderExtension(extension);
    }
    
    contentDescription->set_direction(direction);
    contentDescription->set_rtcp_mux(true);
    
    cricket::ContentInfo mappedContentInfo(cricket::MediaProtocolType::kRtp);
    mappedContentInfo.name = contentId;
    mappedContentInfo.rejected = false;
    mappedContentInfo.bundle_only = false;
    mappedContentInfo.set_media_description(std::move(contentDescription));
    
    return mappedContentInfo;
}

cricket::ContentInfo createInactiveContentInfo(std::string const &contentId) {
    std::unique_ptr<cricket::MediaContentDescription> contentDescription;
    
    auto audioDescription = std::make_unique<cricket::AudioContentDescription>();
    contentDescription = std::move(audioDescription);
    
    contentDescription->set_direction(webrtc::RtpTransceiverDirection::kInactive);
    contentDescription->set_rtcp_mux(true);
    
    cricket::ContentInfo mappedContentInfo(cricket::MediaProtocolType::kRtp);
    mappedContentInfo.name = contentId;
    mappedContentInfo.rejected = false;
    mappedContentInfo.bundle_only = false;
    mappedContentInfo.set_media_description(std::move(contentDescription));
    
    return mappedContentInfo;
}

std::string contentIdBySsrc(uint32_t ssrc) {
    std::ostringstream contentIdString;
    
    contentIdString << ssrc;
    
    return contentIdString.str();
}

}

ContentNegotiationContext::ContentNegotiationContext(const webrtc::WebRtcKeyValueConfig& fieldTrials, bool isOutgoing, rtc::UniqueRandomIdGenerator *uniqueRandomIdGenerator) :
_isOutgoing(isOutgoing),
_uniqueRandomIdGenerator(uniqueRandomIdGenerator) {
    _transportDescriptionFactory = std::make_unique<cricket::TransportDescriptionFactory>(fieldTrials);
    
    // tempCertificate is only used to fill in the local SDP
    auto tempCertificate = rtc::RTCCertificateGenerator::GenerateCertificate(rtc::KeyParams(rtc::KT_ECDSA), absl::nullopt);
    _transportDescriptionFactory->set_secure(cricket::SecurePolicy::SEC_REQUIRED);
    _transportDescriptionFactory->set_certificate(tempCertificate);
    
    _sessionDescriptionFactory = std::make_unique<cricket::MediaSessionDescriptionFactory>(_transportDescriptionFactory.get(), uniqueRandomIdGenerator);
    
    _needNegotiation = true;
}

ContentNegotiationContext::~ContentNegotiationContext() {
    
}

void ContentNegotiationContext::copyCodecsFromChannelManager(cricket::MediaEngineInterface *mediaEngine, bool randomize) {
    cricket::AudioCodecs audioSendCodecs = mediaEngine->voice().send_codecs();
    cricket::AudioCodecs audioRecvCodecs = mediaEngine->voice().recv_codecs();
    cricket::VideoCodecs videoSendCodecs = mediaEngine->video().send_codecs();
    cricket::VideoCodecs videoRecvCodecs = mediaEngine->video().recv_codecs();
    
    for (const auto &codec : audioSendCodecs) {
        if (codec.name == "opus") {
            audioSendCodecs = { codec };
            audioRecvCodecs = { codec };
            break;
        }
    }
    
    if (randomize) {
        for (auto &codec : audioSendCodecs) {
            codec.id += 3;
        }
        for (auto &codec : videoSendCodecs) {
            codec.id += 3;
        }
        for (auto &codec : audioRecvCodecs) {
            codec.id += 3;
        }
        for (auto &codec : videoRecvCodecs) {
            codec.id += 3;
        }
    }
    
    _sessionDescriptionFactory->set_audio_codecs(audioSendCodecs, audioRecvCodecs);
    _sessionDescriptionFactory->set_video_codecs(videoSendCodecs, videoRecvCodecs);
    
    int absSendTimeUriId = 2;
    int transportSequenceNumberUriId = 3;
    int videoRotationUri = 13;
    
    if (randomize) {
        absSendTimeUriId = 3;
        transportSequenceNumberUriId = 2;
        videoRotationUri = 4;
    }
    
    _rtpAudioExtensions.emplace_back(webrtc::RtpExtension::kAbsSendTimeUri, absSendTimeUriId);
    _rtpAudioExtensions.emplace_back(webrtc::RtpExtension::kTransportSequenceNumberUri, transportSequenceNumberUriId);
    
    _rtpVideoExtensions.emplace_back(webrtc::RtpExtension::kAbsSendTimeUri, absSendTimeUriId);
    _rtpVideoExtensions.emplace_back(webrtc::RtpExtension::kTransportSequenceNumberUri, transportSequenceNumberUriId);
    _rtpVideoExtensions.emplace_back(webrtc::RtpExtension::kVideoRotationUri, videoRotationUri);
}

std::string ContentNegotiationContext::addOutgoingChannel(signaling::MediaContent::Type mediaType) {
    std::string channelId = takeNextOutgoingChannelId();
    
    cricket::MediaType mappedMediaType;
    std::vector<webrtc::RtpHeaderExtensionCapability> rtpExtensions;
    switch (mediaType) {
        case signaling::MediaContent::Type::Audio: {
            mappedMediaType = cricket::MediaType::MEDIA_TYPE_AUDIO;
            rtpExtensions = _rtpAudioExtensions;
            break;
        }
        case signaling::MediaContent::Type::Video: {
            mappedMediaType = cricket::MediaType::MEDIA_TYPE_VIDEO;
            rtpExtensions = _rtpVideoExtensions;
            break;
        }
        default: {
            RTC_FATAL() << "Unknown media type";
            break;
        }
    }
    cricket::MediaDescriptionOptions offerDescription(mappedMediaType, channelId, webrtc::RtpTransceiverDirection::kSendOnly, false);
    offerDescription.header_extensions = rtpExtensions;
    
    switch (mediaType) {
        case signaling::MediaContent::Type::Audio: {
            offerDescription.AddAudioSender(channelId, { channelId });
            break;
        }
        case signaling::MediaContent::Type::Video: {
            cricket::SimulcastLayerList simulcastLayers;
            offerDescription.AddVideoSender(channelId, { channelId }, {}, simulcastLayers, 1);
            break;
        }
        default: {
            RTC_FATAL() << "Unknown media type";
            break;
        }
    }
    
    _outgoingChannelDescriptions.emplace_back(std::move(offerDescription));
    _needNegotiation = true;
    
    return channelId;
}

void ContentNegotiationContext::removeOutgoingChannel(std::string const &id) {
    for (size_t i = 0; i < _outgoingChannels.size(); i++) {
        if (_outgoingChannelDescriptions[i].description.mid == id) {
            _outgoingChannelDescriptions.erase(_outgoingChannelDescriptions.begin() + i);
            
            _needNegotiation = true;
            
            break;
        }
    }
}

std::unique_ptr<cricket::SessionDescription> ContentNegotiationContext::currentSessionDescriptionFromCoordinatedState() {
    if (_channelIdOrder.empty()) {
        return nullptr;
    }
    
    auto sessionDescription = std::make_unique<cricket::SessionDescription>();
    
    for (const auto &id : _channelIdOrder) {
        bool found = false;
        
        for (const auto &channel : _incomingChannels) {
            if (contentIdBySsrc(channel.ssrc) == id) {
                found = true;
                
                auto mappedContent = convertSingalingContentToContentInfo(contentIdBySsrc(channel.ssrc), channel, webrtc::RtpTransceiverDirection::kRecvOnly);
                
                cricket::TransportDescription transportDescription;
                cricket::TransportInfo transportInfo(contentIdBySsrc(channel.ssrc), transportDescription);
                sessionDescription->AddTransportInfo(transportInfo);
                
                sessionDescription->AddContent(std::move(mappedContent));
                
                break;
            }
        }
        
        for (const auto &channel : _outgoingChannels) {
            if (channel.id == id) {
                found = true;
                
                auto mappedContent = convertSingalingContentToContentInfo(channel.id, channel.content, webrtc::RtpTransceiverDirection::kSendOnly);
                
                cricket::TransportDescription transportDescription;
                cricket::TransportInfo transportInfo(mappedContent.name, transportDescription);
                sessionDescription->AddTransportInfo(transportInfo);
                
                sessionDescription->AddContent(std::move(mappedContent));
                
                break;
            }
        }
        
        if (!found) {
            auto mappedContent = createInactiveContentInfo("_" + id);
            
            cricket::TransportDescription transportDescription;
            cricket::TransportInfo transportInfo(mappedContent.name, transportDescription);
            sessionDescription->AddTransportInfo(transportInfo);
            
            sessionDescription->AddContent(std::move(mappedContent));
        }
    }
    
    return sessionDescription;
}

static cricket::MediaDescriptionOptions getIncomingContentDescription(signaling::MediaContent const &content) {
    auto mappedContent = convertSingalingContentToContentInfo(contentIdBySsrc(content.ssrc), content, webrtc::RtpTransceiverDirection::kSendOnly);
    
    cricket::MediaDescriptionOptions contentDescription(mappedContent.media_description()->type(), mappedContent.name, webrtc::RtpTransceiverDirection::kRecvOnly, false);
    for (const auto &extension : mappedContent.media_description()->rtp_header_extensions()) {
        contentDescription.header_extensions.emplace_back(extension.uri, extension.id);
    }
    
    return contentDescription;
}

std::unique_ptr<ContentNegotiationContext::NegotiationContents> ContentNegotiationContext::getPendingOffer() {
    if (!_needNegotiation) {
        return nullptr;
    }
    if (_pendingOutgoingOffer) {
        return nullptr;
    }
    
    _pendingOutgoingOffer = std::make_unique<PendingOutgoingOffer>();
    _pendingOutgoingOffer->exchangeId = _uniqueRandomIdGenerator->GenerateId();
    
    auto currentSessionDescription = currentSessionDescriptionFromCoordinatedState();
    
    cricket::MediaSessionOptions offerOptions;
    offerOptions.offer_extmap_allow_mixed = true;
    offerOptions.bundle_enabled = true;
    
    for (const auto &id : _channelIdOrder) {
        bool found = false;
        
        for (const auto &channel : _outgoingChannelDescriptions) {
            if (channel.description.mid == id) {
                found = true;
                offerOptions.media_description_options.push_back(channel.description);
                
                break;
            }
        }
        
        for (const auto &content : _incomingChannels) {
            if (contentIdBySsrc(content.ssrc) == id) {
                found = true;
                offerOptions.media_description_options.push_back(getIncomingContentDescription(content));
                
                break;
            }
        }
        
        if (!found) {
            cricket::MediaDescriptionOptions contentDescription(cricket::MediaType::MEDIA_TYPE_AUDIO, "_" + id, webrtc::RtpTransceiverDirection::kInactive, false);
            offerOptions.media_description_options.push_back(contentDescription);
        }
    }
    
    for (const auto &channel : _outgoingChannelDescriptions) {
        if (std::find(_channelIdOrder.begin(), _channelIdOrder.end(), channel.description.mid) == _channelIdOrder.end()) {
            _channelIdOrder.push_back(channel.description.mid);
            
            offerOptions.media_description_options.push_back(channel.description);
        }
        
        for (const auto &content : _incomingChannels) {
            if (std::find(_channelIdOrder.begin(), _channelIdOrder.end(), contentIdBySsrc(content.ssrc)) == _channelIdOrder.end()) {
                _channelIdOrder.push_back(contentIdBySsrc(content.ssrc));
                
                offerOptions.media_description_options.push_back(getIncomingContentDescription(content));
            }
        }
    }
    
    std::unique_ptr<cricket::SessionDescription> offer = _sessionDescriptionFactory->CreateOffer(offerOptions, currentSessionDescription.get());
    
    auto mappedOffer = std::make_unique<ContentNegotiationContext::NegotiationContents>();
    
    mappedOffer->exchangeId = _pendingOutgoingOffer->exchangeId;
    
    for (const auto &content : offer->contents()) {
        auto mappedContent = convertContentInfoToSingalingContent(content);
        
        if (content.media_description()->direction() == webrtc::RtpTransceiverDirection::kSendOnly) {
            mappedOffer->contents.push_back(std::move(mappedContent));
            
            for (auto &channel : _outgoingChannelDescriptions) {
                if (channel.description.mid == content.mid()) {
                    channel.ssrc = mappedContent.ssrc;
                    channel.ssrcGroups = mappedContent.ssrcGroups;
                }
            }
        }
    }
    
    return mappedOffer;
}

std::unique_ptr<ContentNegotiationContext::NegotiationContents> ContentNegotiationContext::setRemoteNegotiationContent(std::unique_ptr<NegotiationContents> &&remoteNegotiationContent) {
    if (!remoteNegotiationContent) {
        return nullptr;
    }
    
    if (_pendingOutgoingOffer) {
        if (remoteNegotiationContent->exchangeId == _pendingOutgoingOffer->exchangeId) {
            setAnswer(std::move(remoteNegotiationContent));
            return nullptr;
        } else {
            // race condition detected — call initiator wins
            if (!_isOutgoing) {
                _pendingOutgoingOffer.reset();
                return getAnswer(std::move(remoteNegotiationContent));
            } else {
                return nullptr;
            }
        }
    } else {
        return getAnswer(std::move(remoteNegotiationContent));
    }
}

std::unique_ptr<ContentNegotiationContext::NegotiationContents> ContentNegotiationContext::getAnswer(std::unique_ptr<ContentNegotiationContext::NegotiationContents> &&offer) {
    auto currentSessionDescription = currentSessionDescriptionFromCoordinatedState();
    
    auto mappedOffer = std::make_unique<cricket::SessionDescription>();
    
    cricket::MediaSessionOptions answerOptions;
    answerOptions.offer_extmap_allow_mixed = true;
    answerOptions.bundle_enabled = true;
    
    for (const auto &id : _channelIdOrder) {
        bool found = false;
        
        for (const auto &channel : _outgoingChannels) {
            if (channel.id == id) {
                found = true;
                
                auto mappedContent = convertSingalingContentToContentInfo(channel.id, channel.content, webrtc::RtpTransceiverDirection::kRecvOnly);
                
                cricket::MediaDescriptionOptions contentDescription(mappedContent.media_description()->type(), mappedContent.name, webrtc::RtpTransceiverDirection::kSendOnly, false);
                for (const auto &extension : mappedContent.media_description()->rtp_header_extensions()) {
                    contentDescription.header_extensions.emplace_back(extension.uri, extension.id);
                }
                answerOptions.media_description_options.push_back(contentDescription);
                
                cricket::TransportDescription transportDescription;
                cricket::TransportInfo transportInfo(channel.id, transportDescription);
                mappedOffer->AddTransportInfo(transportInfo);
                
                mappedOffer->AddContent(std::move(mappedContent));
                
                break;
            }
        }
        
        for (const auto &content : offer->contents) {
            if (contentIdBySsrc(content.ssrc) == id) {
                found = true;
                
                auto mappedContent = convertSingalingContentToContentInfo(contentIdBySsrc(content.ssrc), content, webrtc::RtpTransceiverDirection::kSendOnly);
                
                cricket::MediaDescriptionOptions contentDescription(mappedContent.media_description()->type(), mappedContent.name, webrtc::RtpTransceiverDirection::kRecvOnly, false);
                for (const auto &extension : mappedContent.media_description()->rtp_header_extensions()) {
                    contentDescription.header_extensions.emplace_back(extension.uri, extension.id);
                }
                answerOptions.media_description_options.push_back(contentDescription);
                
                cricket::TransportDescription transportDescription;
                cricket::TransportInfo transportInfo(mappedContent.mid(), transportDescription);
                mappedOffer->AddTransportInfo(transportInfo);
                
                mappedOffer->AddContent(std::move(mappedContent));
                
                break;
            }
        }
        
        if (!found) {
            auto mappedContent = createInactiveContentInfo("_" + id);
            
            cricket::MediaDescriptionOptions contentDescription(cricket::MediaType::MEDIA_TYPE_AUDIO, "_" + id, webrtc::RtpTransceiverDirection::kInactive, false);
            answerOptions.media_description_options.push_back(contentDescription);
            
            cricket::TransportDescription transportDescription;
            cricket::TransportInfo transportInfo(mappedContent.mid(), transportDescription);
            mappedOffer->AddTransportInfo(transportInfo);
            
            mappedOffer->AddContent(std::move(mappedContent));
        }
    }
    
    for (const auto &content : offer->contents) {
        if (std::find(_channelIdOrder.begin(), _channelIdOrder.end(), contentIdBySsrc(content.ssrc)) == _channelIdOrder.end()) {
            _channelIdOrder.push_back(contentIdBySsrc(content.ssrc));
            
            answerOptions.media_description_options.push_back(getIncomingContentDescription(content));
            
            auto mappedContent = convertSingalingContentToContentInfo(contentIdBySsrc(content.ssrc), content, webrtc::RtpTransceiverDirection::kSendOnly);
            
            cricket::TransportDescription transportDescription;
            cricket::TransportInfo transportInfo(mappedContent.mid(), transportDescription);
            mappedOffer->AddTransportInfo(transportInfo);
            
            mappedOffer->AddContent(std::move(mappedContent));
        }
    }
    
    std::unique_ptr<cricket::SessionDescription> answer = _sessionDescriptionFactory->CreateAnswer(mappedOffer.get(), answerOptions, currentSessionDescription.get());
    
    auto mappedAnswer = std::make_unique<NegotiationContents>();
    
    mappedAnswer->exchangeId = offer->exchangeId;
    
    std::vector<signaling::MediaContent> incomingChannels;
    
    for (const auto &content : answer->contents()) {
        auto mappedContent = convertContentInfoToSingalingContent(content);
        
        if (content.media_description()->direction() == webrtc::RtpTransceiverDirection::kRecvOnly) {
            for (const auto &offerContent : offer->contents) {
                if (contentIdBySsrc(offerContent.ssrc) == content.mid()) {
                    mappedContent.ssrc = offerContent.ssrc;
                    mappedContent.ssrcGroups = offerContent.ssrcGroups;
                    
                    break;
                }
            }
            
            incomingChannels.push_back(mappedContent);
            mappedAnswer->contents.push_back(std::move(mappedContent));
        }
    }
    
    _incomingChannels = incomingChannels;
    
    return mappedAnswer;
}

void ContentNegotiationContext::setAnswer(std::unique_ptr<ContentNegotiationContext::NegotiationContents> &&answer) {
    if (!_pendingOutgoingOffer) {
        return;
    }
    if (_pendingOutgoingOffer->exchangeId != answer->exchangeId) {
        return;
    }
    
    _pendingOutgoingOffer.reset();
    _needNegotiation = false;
    
    _outgoingChannels.clear();
    
    for (const auto &content : answer->contents) {
        for (const auto &pendingChannel : _outgoingChannelDescriptions) {
            if (pendingChannel.ssrc != 0 && content.ssrc == pendingChannel.ssrc) {
                _outgoingChannels.emplace_back(pendingChannel.description.mid, content);
                
                break;
            }
        }
    }
}

std::string ContentNegotiationContext::takeNextOutgoingChannelId() {
    std::ostringstream result;
    result << "m" << _nextOutgoingChannelId;
    _nextOutgoingChannelId++;
    
    return result.str();
}

std::unique_ptr<ContentNegotiationContext::CoordinatedState> ContentNegotiationContext::coordinatedState() const {
    auto result = std::make_unique<ContentNegotiationContext::CoordinatedState>();
    
    result->incomingContents = _incomingChannels;
    for (const auto &channel : _outgoingChannels) {
        bool found = false;
        
        for (const auto &channelDescription : _outgoingChannelDescriptions) {
            if (channelDescription.description.mid == channel.id) {
                found = true;
                break;
            }
        }
        
        if (found) {
            result->outgoingContents.push_back(channel.content);
        }
    }
    
    return result;
}

absl::optional<uint32_t> ContentNegotiationContext::outgoingChannelSsrc(std::string const &id) const {
    for (const auto &channel : _outgoingChannels) {
        bool found = false;
        
        for (const auto &channelDescription : _outgoingChannelDescriptions) {
            if (channelDescription.description.mid == channel.id) {
                found = true;
                break;
            }
        }
        
        if (found && channel.id == id) {
            if (channel.content.ssrc != 0) {
                return channel.content.ssrc;
            }
        }
    }
    
    return absl::nullopt;
}

} // namespace tgcalls

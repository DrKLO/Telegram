#include "v2/SignalingTranslator.h"

#include "api/jsep_ice_candidate.h"
#include "rtc_base/ssl_fingerprint.h"
#include "rtc_base/logging.h"
#include "p2p/base/transport_description.h"

#include <sstream>

namespace tgcalls {

namespace {

std::string contentIdBySsrc(uint32_t ssrc) {
    std::ostringstream result;
    result << ssrc;
    return result.str();
}

cricket::ConnectionRole connectionRoleFromSetup(const std::string &setup) {
    if (setup == "active") {
        return cricket::CONNECTIONROLE_ACTIVE;
    } else if (setup == "passive") {
        return cricket::CONNECTIONROLE_PASSIVE;
    } else if (setup == "actpass") {
        return cricket::CONNECTIONROLE_ACTPASS;
    } else {
        return cricket::CONNECTIONROLE_NONE;
    }
}

std::string setupFromConnectionRole(cricket::ConnectionRole role) {
    switch (role) {
        case cricket::CONNECTIONROLE_ACTIVE: return "active";
        case cricket::CONNECTIONROLE_PASSIVE: return "passive";
        case cricket::CONNECTIONROLE_ACTPASS: return "actpass";
        default: return "actpass";
    }
}

} // namespace

SignalingTranslator::SignalingTranslator(bool isOutgoing)
    : _isOutgoing(isOutgoing) {
}

signaling::InitialSetupMessage SignalingTranslator::extractInitialSetup(
    const cricket::SessionDescription *desc) {
    signaling::InitialSetupMessage result;

    if (!desc->contents().empty()) {
        const auto &firstContentName = desc->contents()[0].name;
        const auto *transportInfo = desc->GetTransportInfoByName(firstContentName);
        if (transportInfo) {
            result.ufrag = transportInfo->description.ice_ufrag;
            result.pwd = transportInfo->description.ice_pwd;
            result.supportsRenomination = true;

            signaling::DtlsFingerprint dtlsFingerprint;
            if (transportInfo->description.identity_fingerprint) {
                dtlsFingerprint.hash = transportInfo->description.identity_fingerprint->algorithm;
                dtlsFingerprint.fingerprint = transportInfo->description.identity_fingerprint->GetRfc4572Fingerprint();
            }
            dtlsFingerprint.setup = setupFromConnectionRole(transportInfo->description.connection_role);
            result.fingerprints.push_back(std::move(dtlsFingerprint));
        }
    }

    return result;
}

signaling::NegotiateChannelsMessage SignalingTranslator::extractNegotiateChannels(
    const cricket::SessionDescription *desc, uint32_t exchangeId) {
    signaling::NegotiateChannelsMessage result;
    result.exchangeId = exchangeId;

    for (const auto &content : desc->contents()) {
        // Skip data channel (SCTP) contents — only extract audio/video
        if (content.media_description() &&
            content.media_description()->type() != cricket::MediaType::MEDIA_TYPE_AUDIO &&
            content.media_description()->type() != cricket::MediaType::MEDIA_TYPE_VIDEO) {
            continue;
        }
        result.contents.push_back(
            signaling::convertContentInfoToSignalingContent(content));
    }

    return result;
}

signaling::CandidatesMessage SignalingTranslator::extractCandidates(
    const webrtc::IceCandidateInterface *candidate) {
    signaling::CandidatesMessage result;

    std::string sdpString;
    if (candidate->ToString(&sdpString)) {
        signaling::IceCandidate iceCandidate;
        iceCandidate.sdpString = sdpString;
        result.iceCandidates.push_back(std::move(iceCandidate));
    }

    return result;
}

bool SignalingTranslator::receiveInitialSetup(signaling::InitialSetupMessage message) {
    _storedRemoteInitialSetup = message;
    _pendingRemoteInitialSetup = std::move(message);
    return _pendingRemoteNegotiateChannels.has_value();
}

bool SignalingTranslator::receiveNegotiateChannels(signaling::NegotiateChannelsMessage message) {
    _pendingRemoteNegotiateChannels = std::move(message);
    return _pendingRemoteInitialSetup.has_value();
}

bool SignalingTranslator::hasCompleteRemoteDescription() const {
    return _pendingRemoteInitialSetup.has_value() && _pendingRemoteNegotiateChannels.has_value();
}

uint32_t SignalingTranslator::lastReceivedExchangeId() const {
    if (_pendingRemoteNegotiateChannels.has_value()) {
        return _pendingRemoteNegotiateChannels->exchangeId;
    }
    return 0;
}

std::unique_ptr<webrtc::SessionDescriptionInterface> SignalingTranslator::buildRemoteDescription(
    const cricket::SessionDescription *localDescription) {
    if (!hasCompleteRemoteDescription()) {
        return nullptr;
    }

    auto result = buildDescription(
        _pendingRemoteInitialSetup.value(),
        _pendingRemoteNegotiateChannels.value(),
        localDescription);

    _pendingRemoteInitialSetup.reset();
    _pendingRemoteNegotiateChannels.reset();

    return result;
}

std::unique_ptr<webrtc::SessionDescriptionInterface> SignalingTranslator::buildRemoteDescriptionForRenegotiation(
    const signaling::NegotiateChannelsMessage &channels) {
    if (!_storedRemoteInitialSetup.has_value()) {
        return nullptr;
    }

    return buildDescription(_storedRemoteInitialSetup.value(), channels);
}

std::unique_ptr<webrtc::SessionDescriptionInterface> SignalingTranslator::buildDescription(
    const signaling::InitialSetupMessage &setup,
    const signaling::NegotiateChannelsMessage &channels,
    const cricket::SessionDescription *localDescription) {

    // The remote description type is the opposite of our local role:
    // - If we're outgoing (caller), the remote is the callee sending an answer
    // - If we're incoming (callee), the remote is the caller sending an offer
    webrtc::SdpType sdpType = _isOutgoing ? webrtc::SdpType::kAnswer : webrtc::SdpType::kOffer;

    auto cricketDesc = std::make_unique<cricket::SessionDescription>();
    auto transportDesc = makeTransportDescription(setup, sdpType == webrtc::SdpType::kOffer);

    std::vector<std::string> contentNames;

    if (localDescription && sdpType == webrtc::SdpType::kAnswer) {
        // When building a remote answer, match the local offer's m-line structure.
        // Audio/video m-lines get populated from the NegotiateChannelsMessage.
        // Non-audio/video m-lines (e.g. data channel) are added as rejected.
        int channelIndex = 0;
        for (const auto &localContent : localDescription->contents()) {
            auto mediaType = localContent.media_description()->type();
            std::string contentId = localContent.name;
            contentNames.push_back(contentId);

            if ((mediaType == cricket::MediaType::MEDIA_TYPE_AUDIO ||
                 mediaType == cricket::MediaType::MEDIA_TYPE_VIDEO) &&
                channelIndex < (int)channels.contents.size()) {
                // Populate from signaling message
                auto contentInfo = signaling::convertSignalingContentToContentInfo(
                    contentId, channels.contents[channelIndex], webrtc::RtpTransceiverDirection::kRecvOnly);
                cricketDesc->AddContent(std::move(contentInfo));
                channelIndex++;
            } else {
                // Add rejected content matching the local m-line (e.g. data channel)
                auto clonedDesc = localContent.media_description()->Clone();
                clonedDesc->set_direction(webrtc::RtpTransceiverDirection::kInactive);
                cricket::ContentInfo rejectedContent(localContent.type);
                rejectedContent.name = contentId;
                rejectedContent.rejected = true;
                rejectedContent.set_media_description(std::move(clonedDesc));
                cricketDesc->AddContent(std::move(rejectedContent));
            }

            cricketDesc->AddTransportInfo(cricket::TransportInfo(contentId, transportDesc));
        }
    } else {
        // Building a remote offer (callee side), or no local description available.
        // Use sequential content names from the signaling message.
        int contentIndex = 0;
        for (const auto &content : channels.contents) {
            std::string contentId = std::to_string(contentIndex++);
            contentNames.push_back(contentId);

            webrtc::RtpTransceiverDirection direction =
                (sdpType == webrtc::SdpType::kOffer)
                    ? webrtc::RtpTransceiverDirection::kSendOnly
                    : webrtc::RtpTransceiverDirection::kRecvOnly;

            auto contentInfo = signaling::convertSignalingContentToContentInfo(
                contentId, content, direction);
            cricketDesc->AddContent(std::move(contentInfo));
            cricketDesc->AddTransportInfo(cricket::TransportInfo(contentId, transportDesc));
        }
    }

    if (!contentNames.empty()) {
        cricket::ContentGroup bundleGroup(cricket::GROUP_TYPE_BUNDLE);
        for (const auto &name : contentNames) {
            bundleGroup.AddContentName(name);
        }
        cricketDesc->AddGroup(bundleGroup);
    }

    auto jsepDesc = std::make_unique<webrtc::JsepSessionDescription>(
        sdpType,
        std::move(cricketDesc),
        "0", "0");

    return jsepDesc;
}

cricket::TransportDescription SignalingTranslator::makeTransportDescription(
    const signaling::InitialSetupMessage &setup, bool isRemoteOffer) {
    cricket::TransportDescription transportDesc;
    transportDesc.ice_ufrag = setup.ufrag;
    transportDesc.ice_pwd = setup.pwd;
    transportDesc.ice_mode = cricket::ICEMODE_FULL;

    if (!setup.fingerprints.empty()) {
        auto fingerprint = rtc::SSLFingerprint::CreateUniqueFromRfc4572(
            setup.fingerprints[0].hash, setup.fingerprints[0].fingerprint);
        if (fingerprint) {
            transportDesc.identity_fingerprint = std::move(fingerprint);
        }
        transportDesc.connection_role = connectionRoleFromSetup(setup.fingerprints[0].setup);
    }

    return transportDesc;
}

std::vector<std::unique_ptr<webrtc::IceCandidateInterface>> SignalingTranslator::parseCandidates(
    const signaling::CandidatesMessage &message) {
    std::vector<std::unique_ptr<webrtc::IceCandidateInterface>> result;

    for (const auto &candidate : message.iceCandidates) {
        webrtc::SdpParseError parseError;
        webrtc::IceCandidateInterface *iceCandidate = webrtc::CreateIceCandidate("0", 0, candidate.sdpString, &parseError);
        if (iceCandidate) {
            result.emplace_back(std::unique_ptr<webrtc::IceCandidateInterface>(iceCandidate));
        } else {
            RTC_LOG(LS_ERROR) << "SignalingTranslator: Could not parse candidate: " << candidate.sdpString;
        }
    }

    return result;
}

}  // namespace tgcalls

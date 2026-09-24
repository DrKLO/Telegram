#ifndef TGCALLS_SIGNALING_TRANSLATOR_H_
#define TGCALLS_SIGNALING_TRANSLATOR_H_

#include <cstdint>
#include <memory>
#include <string>
#include <vector>
#include <functional>

#include "v2/Signaling.h"
#include "api/jsep.h"
#include "api/jsep_session_description.h"
#include "pc/session_description.h"
#include "api/peer_connection_interface.h"

namespace tgcalls {

class SignalingTranslator {
public:
    SignalingTranslator(bool isOutgoing);

    // Outbound: extract V2Impl messages from PeerConnection's local description
    signaling::InitialSetupMessage extractInitialSetup(
        const cricket::SessionDescription *desc);
    signaling::NegotiateChannelsMessage extractNegotiateChannels(
        const cricket::SessionDescription *desc, uint32_t exchangeId);
    signaling::CandidatesMessage extractCandidates(
        const webrtc::IceCandidateInterface *candidate);

    // Inbound: receive V2Impl messages, buffer, and build JsepSessionDescription
    bool receiveInitialSetup(signaling::InitialSetupMessage message);
    bool receiveNegotiateChannels(signaling::NegotiateChannelsMessage message);

    // Build the buffered remote description.
    // If localDescription is provided, non-audio/video m-lines (e.g. data channel)
    // are padded as rejected contents to match the local offer's m-line count.
    std::unique_ptr<webrtc::SessionDescriptionInterface> buildRemoteDescription(
        const cricket::SessionDescription *localDescription = nullptr);

    // Parse V2Impl CandidatesMessage into PeerConnection ICE candidates
    std::vector<std::unique_ptr<webrtc::IceCandidateInterface>> parseCandidates(
        const signaling::CandidatesMessage &message);

    // State queries
    bool hasCompleteRemoteDescription() const;
    uint32_t lastReceivedExchangeId() const;

    // For renegotiation
    std::unique_ptr<webrtc::SessionDescriptionInterface> buildRemoteDescriptionForRenegotiation(
        const signaling::NegotiateChannelsMessage &channels);

private:
    bool _isOutgoing;

    absl::optional<signaling::InitialSetupMessage> _pendingRemoteInitialSetup;
    absl::optional<signaling::NegotiateChannelsMessage> _pendingRemoteNegotiateChannels;
    absl::optional<signaling::InitialSetupMessage> _storedRemoteInitialSetup;

    std::unique_ptr<webrtc::SessionDescriptionInterface> buildDescription(
        const signaling::InitialSetupMessage &setup,
        const signaling::NegotiateChannelsMessage &channels,
        const cricket::SessionDescription *localDescription = nullptr);

    static cricket::TransportDescription makeTransportDescription(
        const signaling::InitialSetupMessage &setup, bool isRemoteOffer);
};

}  // namespace tgcalls

#endif  // TGCALLS_SIGNALING_TRANSLATOR_H_

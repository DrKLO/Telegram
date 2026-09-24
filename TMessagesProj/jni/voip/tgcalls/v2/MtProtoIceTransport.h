#ifndef TGCALLS_MTPROTO_ICE_TRANSPORT_H
#define TGCALLS_MTPROTO_ICE_TRANSPORT_H

#include <memory>
#include <string>

#include "Instance.h"
#include "api/ice_transport_interface.h"
#include "p2p/base/ice_transport_internal.h"

namespace tgcalls {

class EncryptedConnection;

// Decorates a real P2PTransportChannel, applying mtproto (EncryptedConnection)
// to payload traffic. Sits ABOVE ICE, so the agent's own STUN binding requests
// bypass it - matching 13.0.0, where MtProtoPacketTransport wraps the channel
// the same way. See docs/superpowers/specs/2026-09-01-tgcalls-mtproto-peerconnection-design.md.
class MtProtoIceTransport : public cricket::IceTransportInternal {
public:
    MtProtoIceTransport(std::unique_ptr<cricket::IceTransportInternal> inner, EncryptionKey encryptionKey);
    ~MtProtoIceTransport() override;

    cricket::IceTransportInternal *inner() { return _inner.get(); }

    // rtc::PacketTransportInternal
    const std::string &transport_name() const override;
    bool writable() const override;
    bool receiving() const override;
    int SendPacket(const char *data, size_t len, const rtc::PacketOptions &options, int flags) override;
    int SetOption(rtc::Socket::Option opt, int value) override;
    bool GetOption(rtc::Socket::Option opt, int *value) override;
    int GetError() override;
    absl::optional<rtc::NetworkRoute> network_route() const override;

    // cricket::IceTransportInternal. All pure forwards.
    //
    // Deliberately NOT overridden: SetIceCredentials / SetRemoteIceCredentials.
    // They are virtual but not pure, and their base implementations delegate to
    // SetIceParameters / SetRemoteIceParameters (ice_transport_internal.cc:139-147),
    // which we do forward. Overriding them would be redundant.
    cricket::IceTransportState GetState() const override;
    webrtc::IceTransportState GetIceTransportState() const override;
    int component() const override;
    cricket::IceRole GetIceRole() const override;
    void SetIceRole(cricket::IceRole role) override;
    void SetIceTiebreaker(uint64_t tiebreaker) override;
    void SetIceParameters(const cricket::IceParameters &ice_params) override;
    void SetRemoteIceParameters(const cricket::IceParameters &ice_params) override;
    void SetRemoteIceMode(cricket::IceMode mode) override;
    void SetIceConfig(const cricket::IceConfig &config) override;
    void MaybeStartGathering() override;
    void AddRemoteCandidate(const cricket::Candidate &candidate) override;
    void RemoveRemoteCandidate(const cricket::Candidate &candidate) override;
    void RemoveAllRemoteCandidates() override;
    cricket::IceGatheringState gathering_state() const override;
    bool GetStats(cricket::IceTransportStats *ice_transport_stats) override;
    absl::optional<int> GetRttEstimate() override;
    const cricket::Connection *selected_connection() const override;
    absl::optional<const cricket::CandidatePair> GetSelectedCandidatePair() const override;

private:
    // Every bridge lives in this one method so a missing one is visible in
    // review, and so an upstream addition shows up as an obvious hole rather
    // than being scattered across the file.
    void installBridges();

    // 7 rtc::PacketTransportInternal signals.
    void onInnerWritableState(rtc::PacketTransportInternal *transport);
    void onInnerReadyToSend(rtc::PacketTransportInternal *transport);
    void onInnerReceivingState(rtc::PacketTransportInternal *transport);
    void onInnerReadPacket(rtc::PacketTransportInternal *transport, const char *data, size_t size, const int64_t &timestamp, int flags);
    void onInnerSentPacket(rtc::PacketTransportInternal *transport, const rtc::SentPacket &packet);
    void onInnerNetworkRouteChanged(absl::optional<rtc::NetworkRoute> route);
    void onInnerClosed(rtc::PacketTransportInternal *transport);

    // 7 cricket::IceTransportInternal signals.
    void onInnerGatheringState(cricket::IceTransportInternal *transport);
    void onInnerCandidateGathered(cricket::IceTransportInternal *transport, const cricket::Candidate &candidate);
    void onInnerRouteChange(cricket::IceTransportInternal *transport, const cricket::Candidate &candidate);
    void onInnerRoleConflict(cricket::IceTransportInternal *transport);
    void onInnerStateChanged(cricket::IceTransportInternal *transport);
    void onInnerIceTransportStateChanged(cricket::IceTransportInternal *transport);
    void onInnerDestroyed(cricket::IceTransportInternal *transport);

    void processReadPacket(rtc::CopyOnWriteBuffer const &data, int64_t timestamp);

    std::unique_ptr<cricket::IceTransportInternal> _inner;
    std::unique_ptr<EncryptedConnection> _transportEncryption;
};

// Injected via PeerConnectionDependencies::ice_transport_factory. Builds the
// real P2PTransportChannel exactly as webrtc's own factory does
// (api/ice_transport_factory.cc), then wraps it.
class MtProtoIceTransportFactory : public webrtc::IceTransportFactory {
public:
    explicit MtProtoIceTransportFactory(EncryptionKey encryptionKey);

    rtc::scoped_refptr<webrtc::IceTransportInterface> CreateIceTransport(
        const std::string &transport_name,
        int component,
        webrtc::IceTransportInit init) override;

private:
    EncryptionKey _encryptionKey;
};

} // namespace tgcalls

#endif

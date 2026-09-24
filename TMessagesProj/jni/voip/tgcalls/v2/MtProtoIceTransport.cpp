#include "v2/MtProtoIceTransport.h"

#include "EncryptedConnection.h"

#include "media/base/rtp_utils.h"
#include "api/make_ref_counted.h"
#include "p2p/base/p2p_transport_channel.h"

namespace {

// Matches 13.0.0's MtProtoPacketTransport (NativeNetworkingImpl.cpp:355).
constexpr uint32_t kSctpMagic = 0xdcdcdcdc;

} // namespace

namespace tgcalls {

MtProtoIceTransport::MtProtoIceTransport(std::unique_ptr<cricket::IceTransportInternal> inner, EncryptionKey encryptionKey) :
_inner(std::move(inner)) {
    _transportEncryption = std::make_unique<EncryptedConnection>(
        EncryptedConnection::Type::Transport,
        encryptionKey,
        [](int delayMs, int cause) {
        }
    );
    installBridges();
}

MtProtoIceTransport::~MtProtoIceTransport() {
    _inner->SignalWritableState.disconnect(this);
    _inner->SignalReadyToSend.disconnect(this);
    _inner->SignalReceivingState.disconnect(this);
    _inner->SignalReadPacket.disconnect(this);
    _inner->SignalSentPacket.disconnect(this);
    _inner->SignalNetworkRouteChanged.disconnect(this);
    _inner->SignalClosed.disconnect(this);

    _inner->SignalGatheringState.disconnect(this);
    _inner->SignalCandidateGathered.disconnect(this);
    _inner->SignalRouteChange.disconnect(this);
    _inner->SignalRoleConflict.disconnect(this);
    _inner->SignalStateChanged.disconnect(this);
    _inner->SignalIceTransportStateChanged.disconnect(this);
    _inner->SignalDestroyed.disconnect(this);
}

void MtProtoIceTransport::installBridges() {
    // ---- 7 PacketTransportInternal signals ----
    _inner->SignalWritableState.connect(this, &MtProtoIceTransport::onInnerWritableState);
    _inner->SignalReadyToSend.connect(this, &MtProtoIceTransport::onInnerReadyToSend);
    _inner->SignalReceivingState.connect(this, &MtProtoIceTransport::onInnerReceivingState);
    _inner->SignalReadPacket.connect(this, &MtProtoIceTransport::onInnerReadPacket);
    _inner->SignalSentPacket.connect(this, &MtProtoIceTransport::onInnerSentPacket);
    _inner->SignalNetworkRouteChanged.connect(this, &MtProtoIceTransport::onInnerNetworkRouteChanged);
    _inner->SignalClosed.connect(this, &MtProtoIceTransport::onInnerClosed);

    // ---- 7 IceTransportInternal signals ----
    _inner->SignalGatheringState.connect(this, &MtProtoIceTransport::onInnerGatheringState);
    _inner->SignalCandidateGathered.connect(this, &MtProtoIceTransport::onInnerCandidateGathered);
    _inner->SignalRouteChange.connect(this, &MtProtoIceTransport::onInnerRouteChange);
    _inner->SignalRoleConflict.connect(this, &MtProtoIceTransport::onInnerRoleConflict);
    _inner->SignalStateChanged.connect(this, &MtProtoIceTransport::onInnerStateChanged);
    _inner->SignalIceTransportStateChanged.connect(this, &MtProtoIceTransport::onInnerIceTransportStateChanged);
    _inner->SignalDestroyed.connect(this, &MtProtoIceTransport::onInnerDestroyed);

    // ---- 4 IceTransportInternal callbacks ----
    // These setters are NOT virtual, so they cannot be overridden: the
    // controller sets them on US. The storage is protected, so each lambda
    // installed on the inner transport reads our own inherited member.
    // Each setter DCHECKs it is set only once, so install exactly here.
    _inner->SetGatheringStateCallback([this](cricket::IceTransportInternal *) {
        if (gathering_state_callback_) {
            gathering_state_callback_(this);
        }
    });
    _inner->SetCandidateErrorCallback([this](cricket::IceTransportInternal *, const cricket::IceCandidateErrorEvent &event) {
        if (candidate_error_callback_) {
            candidate_error_callback_(this, event);
        }
    });
    _inner->SetCandidatesRemovedCallback([this](cricket::IceTransportInternal *, const cricket::Candidates &candidates) {
        if (candidates_removed_callback_) {
            candidates_removed_callback_(this, candidates);
        }
    });
    _inner->SetCandidatePairChangeCallback([this](const cricket::CandidatePairChangeEvent &event) {
        if (candidate_pair_change_callback_) {
            candidate_pair_change_callback_(event);
        }
    });
}

// Every handler re-emits with `this`, never the inner transport:
// JsepTransportController identifies transports by pointer.

void MtProtoIceTransport::onInnerWritableState(rtc::PacketTransportInternal *) {
    SignalWritableState(this);
}

void MtProtoIceTransport::onInnerReadyToSend(rtc::PacketTransportInternal *) {
    SignalReadyToSend(this);
}

void MtProtoIceTransport::onInnerReceivingState(rtc::PacketTransportInternal *) {
    SignalReceivingState(this);
}

void MtProtoIceTransport::onInnerReadPacket(rtc::PacketTransportInternal *, const char *data, size_t size, const int64_t &timestamp, int) {
    if (const auto packet = _transportEncryption->handleIncomingRawPacket(data, size)) {
        processReadPacket(packet.value().main.message, timestamp);
        for (const auto &additional : packet.value().additional) {
            processReadPacket(additional.message, timestamp);
        }
    }
}

void MtProtoIceTransport::processReadPacket(rtc::CopyOnWriteBuffer const &data, int64_t timestamp) {
    // ALWAYS emit flags == 0: DtlsTransport::OnReadPacket DCHECKs it
    // (dtls_transport.cc:599) and would abort a -c dbg build otherwise.
    //
    // So the prefix is stripped for wire parity only - it cannot drive demux,
    // because DtlsTransport re-emits with flags 0 regardless. Demux upstream is
    // by mutual filtering: RtpTransport drops non-RTP (rtp_transport.cc:266-270)
    // and the SCTP layer validates its own packets.
    if (data.size() >= 4) {
        uint32_t header = 0;
        memcpy(&header, data.data(), 4);
        if (header == kSctpMagic) {
            SignalReadPacket(this, (const char *)(data.data() + 4), data.size() - 4, timestamp, 0);
            return;
        }
    }
    SignalReadPacket(this, (const char *)data.data(), data.size(), timestamp, 0);
}

void MtProtoIceTransport::onInnerSentPacket(rtc::PacketTransportInternal *, const rtc::SentPacket &packet) {
    SignalSentPacket(this, packet);
}

void MtProtoIceTransport::onInnerNetworkRouteChanged(absl::optional<rtc::NetworkRoute> route) {
    SignalNetworkRouteChanged(route);
}

void MtProtoIceTransport::onInnerClosed(rtc::PacketTransportInternal *) {
    SignalClosed(this);
}

void MtProtoIceTransport::onInnerGatheringState(cricket::IceTransportInternal *) {
    SignalGatheringState(this);
}

void MtProtoIceTransport::onInnerCandidateGathered(cricket::IceTransportInternal *, const cricket::Candidate &candidate) {
    SignalCandidateGathered(this, candidate);
}

void MtProtoIceTransport::onInnerRouteChange(cricket::IceTransportInternal *, const cricket::Candidate &candidate) {
    SignalRouteChange(this, candidate);
}

void MtProtoIceTransport::onInnerRoleConflict(cricket::IceTransportInternal *) {
    SignalRoleConflict(this);
}

void MtProtoIceTransport::onInnerStateChanged(cricket::IceTransportInternal *) {
    SignalStateChanged(this);
}

void MtProtoIceTransport::onInnerIceTransportStateChanged(cricket::IceTransportInternal *) {
    SignalIceTransportStateChanged(this);
}

void MtProtoIceTransport::onInnerDestroyed(cricket::IceTransportInternal *) {
    SignalDestroyed(this);
}

const std::string &MtProtoIceTransport::transport_name() const {
    return _inner->transport_name();
}

bool MtProtoIceTransport::writable() const {
    return _inner->writable();
}

bool MtProtoIceTransport::receiving() const {
    return _inner->receiving();
}

int MtProtoIceTransport::SendPacket(const char *data, size_t len, const rtc::PacketOptions &options, int flags) {
    // 13.0.0 selects the SCTP prefix from `flags`, but the inactive DtlsTransport
    // above us drops that argument (dtls_transport.cc:433), so we always observe
    // 0. Infer the type instead, to keep byte parity with 13.0.0's framing.
    //
    // This is a heuristic where 13.0.0 had ground truth: a packet
    // InferRtpPacketType misjudges would be framed wrongly. RTP/RTCP detection
    // keys on the version bits, so SCTP misread as RTP is the case to watch.
    const auto packetType = cricket::InferRtpPacketType(rtc::MakeArrayView(data, len));
    const bool isRtp = (packetType != cricket::RtpPacketType::kUnknown);

    rtc::CopyOnWriteBuffer buffer;
    if (!isRtp) {
        uint32_t magic = kSctpMagic;
        buffer.AppendData((const unsigned char *)&magic, 4);
    }
    buffer.AppendData((const unsigned char *)data, len);

    if (const auto encryptedPacket = _transportEncryption->prepareForSendingRawMessage(buffer, false)) {
        return _inner->SendPacket((const char *)encryptedPacket->bytes.data(), encryptedPacket->bytes.size(), options, 0);
    }
    return 0;
}

int MtProtoIceTransport::SetOption(rtc::Socket::Option opt, int value) {
    return _inner->SetOption(opt, value);
}

bool MtProtoIceTransport::GetOption(rtc::Socket::Option opt, int *value) {
    return _inner->GetOption(opt, value);
}

int MtProtoIceTransport::GetError() {
    return _inner->GetError();
}

absl::optional<rtc::NetworkRoute> MtProtoIceTransport::network_route() const {
    return _inner->network_route();
}


cricket::IceTransportState MtProtoIceTransport::GetState() const {
    return _inner->GetState();
}

webrtc::IceTransportState MtProtoIceTransport::GetIceTransportState() const {
    return _inner->GetIceTransportState();
}

int MtProtoIceTransport::component() const {
    return _inner->component();
}

cricket::IceRole MtProtoIceTransport::GetIceRole() const {
    return _inner->GetIceRole();
}

void MtProtoIceTransport::SetIceRole(cricket::IceRole role) {
    _inner->SetIceRole(role);
}

void MtProtoIceTransport::SetIceTiebreaker(uint64_t tiebreaker) {
    _inner->SetIceTiebreaker(tiebreaker);
}

void MtProtoIceTransport::SetIceParameters(const cricket::IceParameters &ice_params) {
    _inner->SetIceParameters(ice_params);
}

void MtProtoIceTransport::SetRemoteIceParameters(const cricket::IceParameters &ice_params) {
    _inner->SetRemoteIceParameters(ice_params);
}

void MtProtoIceTransport::SetRemoteIceMode(cricket::IceMode mode) {
    _inner->SetRemoteIceMode(mode);
}

void MtProtoIceTransport::SetIceConfig(const cricket::IceConfig &config) {
    _inner->SetIceConfig(config);
}

void MtProtoIceTransport::MaybeStartGathering() {
    _inner->MaybeStartGathering();
}

void MtProtoIceTransport::AddRemoteCandidate(const cricket::Candidate &candidate) {
    _inner->AddRemoteCandidate(candidate);
}

void MtProtoIceTransport::RemoveRemoteCandidate(const cricket::Candidate &candidate) {
    _inner->RemoveRemoteCandidate(candidate);
}

void MtProtoIceTransport::RemoveAllRemoteCandidates() {
    _inner->RemoveAllRemoteCandidates();
}

cricket::IceGatheringState MtProtoIceTransport::gathering_state() const {
    return _inner->gathering_state();
}

bool MtProtoIceTransport::GetStats(cricket::IceTransportStats *ice_transport_stats) {
    return _inner->GetStats(ice_transport_stats);
}

absl::optional<int> MtProtoIceTransport::GetRttEstimate() {
    return _inner->GetRttEstimate();
}

const cricket::Connection *MtProtoIceTransport::selected_connection() const {
    return _inner->selected_connection();
}

absl::optional<const cricket::CandidatePair> MtProtoIceTransport::GetSelectedCandidatePair() const {
    return _inner->GetSelectedCandidatePair();
}


namespace {

class MtProtoIceTransportWrapper : public webrtc::IceTransportInterface {
public:
    explicit MtProtoIceTransportWrapper(std::unique_ptr<MtProtoIceTransport> internal) :
    _internal(std::move(internal)) {
    }

    cricket::IceTransportInternal *internal() override {
        return _internal.get();
    }

private:
    std::unique_ptr<MtProtoIceTransport> _internal;
};

} // namespace

MtProtoIceTransportFactory::MtProtoIceTransportFactory(EncryptionKey encryptionKey) :
_encryptionKey(std::move(encryptionKey)) {
}

rtc::scoped_refptr<webrtc::IceTransportInterface> MtProtoIceTransportFactory::CreateIceTransport(
    const std::string &transport_name,
    int component,
    webrtc::IceTransportInit init
) {
    auto channel = cricket::P2PTransportChannel::Create(transport_name, component, std::move(init));
    auto decorated = std::make_unique<MtProtoIceTransport>(std::move(channel), _encryptionKey);
    return rtc::make_ref_counted<MtProtoIceTransportWrapper>(std::move(decorated));
}

} // namespace tgcalls

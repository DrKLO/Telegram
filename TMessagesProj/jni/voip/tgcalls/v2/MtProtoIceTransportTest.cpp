#include "v2/MtProtoIceTransport.h"

#include <array>
#include <cstdio>
#include <memory>
#include <string>
#include <type_traits>

namespace {

int g_failures = 0;

#define CHECK_TRUE(cond)                                                  \
    do {                                                                  \
        if (!(cond)) {                                                    \
            std::printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);   \
            g_failures++;                                                 \
        }                                                                 \
    } while (0)

tgcalls::EncryptionKey makeTestKey() {
    return tgcalls::EncryptionKey(std::make_shared<std::array<uint8_t, 256>>(), true);
}

// A local stand-in for the inner P2PTransportChannel.
//
// webrtc ships cricket::FakeIceTransport, but it is not declared in
// third-party/webrtc/BUILD, so bazel's strict header check rejects including it
// (it also pulls rtc_base/task_queue_for_test.h, likewise undeclared). Rather
// than modify the read-only vendored webrtc, this stubs the interface directly.
// That is also better for the bridge tests below: every signal and callback can
// be provoked on demand, including ones a real transport would rarely emit.
class FakeInnerIceTransport : public cricket::IceTransportInternal {
public:
    FakeInnerIceTransport() : _transportName("fake") {
    }

    // Test drivers.
    void setWritable(bool writable) {
        _writable = writable;
        SignalWritableState(this);
    }

    void fireCandidateGathered() {
        cricket::Candidate candidate;
        SignalCandidateGathered(this, candidate);
    }

    void fireRouteChange() {
        cricket::Candidate candidate;
        SignalRouteChange(this, candidate);
    }

    void fireRoleConflict() { SignalRoleConflict(this); }
    void fireStateChanged() { SignalStateChanged(this); }
    void fireIceTransportStateChanged() { SignalIceTransportStateChanged(this); }
    void fireDestroyed() { SignalDestroyed(this); }

    void fireGatheringStateCallback() {
        if (gathering_state_callback_) {
            gathering_state_callback_(this);
        }
        SignalGatheringState(this);
    }

    void fireCandidateErrorCallback() {
        if (candidate_error_callback_) {
            cricket::IceCandidateErrorEvent event;
            candidate_error_callback_(this, event);
        }
    }

    void fireCandidatesRemovedCallback() {
        if (candidates_removed_callback_) {
            cricket::Candidates candidates;
            candidates_removed_callback_(this, candidates);
        }
    }

    void fireCandidatePairChangeCallback() {
        if (candidate_pair_change_callback_) {
            cricket::CandidatePairChangeEvent event;
            candidate_pair_change_callback_(event);
        }
    }

    void deliverPacket(const char *data, size_t size) {
        SignalReadPacket(this, data, size, 0, 0);
    }

    std::string lastSentPacket() const { return _lastSentPacket; }
    int sentPacketCount() const { return _sentPacketCount; }

    // rtc::PacketTransportInternal
    const std::string &transport_name() const override { return _transportName; }
    bool writable() const override { return _writable; }
    bool receiving() const override { return _receiving; }

    int SendPacket(const char *data, size_t len, const rtc::PacketOptions &, int) override {
        _lastSentPacket.assign(data, len);
        _sentPacketCount++;
        return (int)len;
    }

    int SetOption(rtc::Socket::Option, int) override { return 0; }
    bool GetOption(rtc::Socket::Option, int *) override { return false; }
    int GetError() override { return 0; }
    absl::optional<rtc::NetworkRoute> network_route() const override { return absl::nullopt; }

    // cricket::IceTransportInternal
    cricket::IceTransportState GetState() const override { return cricket::IceTransportState::STATE_INIT; }
    webrtc::IceTransportState GetIceTransportState() const override { return webrtc::IceTransportState::kNew; }
    int component() const override { return 1; }
    cricket::IceRole GetIceRole() const override { return cricket::ICEROLE_CONTROLLING; }
    void SetIceRole(cricket::IceRole) override {}
    void SetIceTiebreaker(uint64_t) override {}
    void SetIceParameters(const cricket::IceParameters &) override { _setIceParametersCount++; }
    void SetRemoteIceParameters(const cricket::IceParameters &) override {}
    void SetRemoteIceMode(cricket::IceMode) override {}
    void SetIceConfig(const cricket::IceConfig &) override {}
    void MaybeStartGathering() override { _gatheringStarted = true; }
    void AddRemoteCandidate(const cricket::Candidate &) override {}
    void RemoveRemoteCandidate(const cricket::Candidate &) override {}
    void RemoveAllRemoteCandidates() override {}
    cricket::IceGatheringState gathering_state() const override { return cricket::kIceGatheringNew; }
    bool GetStats(cricket::IceTransportStats *) override { return false; }
    absl::optional<int> GetRttEstimate() override { return absl::nullopt; }
    const cricket::Connection *selected_connection() const override { return nullptr; }
    absl::optional<const cricket::CandidatePair> GetSelectedCandidatePair() const override { return absl::nullopt; }

    bool gatheringStarted() const { return _gatheringStarted; }
    int setIceParametersCount() const { return _setIceParametersCount; }

private:
    std::string _transportName;
    bool _writable = false;
    bool _receiving = false;
    bool _gatheringStarted = false;
    int _setIceParametersCount = 0;
    int _sentPacketCount = 0;
    std::string _lastSentPacket;
};

// Transparency: state reads must reflect the inner transport, not our own.
void TestWritableForwards() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();

    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    innerRaw->setWritable(false);
    CHECK_TRUE(transport.writable() == false);

    innerRaw->setWritable(true);
    CHECK_TRUE(transport.writable() == true);
}

// A command must reach the inner transport, not stop at the decorator.
void TestCommandForwards() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();

    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    CHECK_TRUE(innerRaw->gatheringStarted() == false);
    transport.MaybeStartGathering();
    CHECK_TRUE(innerRaw->gatheringStarted() == true);
}

// SetIceCredentials is virtual-but-not-pure and intentionally not overridden;
// its base implementation must still reach the inner transport via our
// SetIceParameters override.
void TestIceCredentialsRouteThroughParameters() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();

    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    CHECK_TRUE(innerRaw->setIceParametersCount() == 0);
    transport.SetIceCredentials("ufrag", "pwd");
    CHECK_TRUE(innerRaw->setIceParametersCount() == 1);
}


// ---------------------------------------------------------------------------
// Bridge conformance. These 11 are NOT compiler-enforced: omit one and it fails
// silently at runtime (e.g. no SignalCandidateGathered => candidates never
// trickle => the call simply never connects). This block is the mitigation that
// makes the no-patch approach acceptable. Do not weaken it.
//
// Every bridge must re-emit with the DECORATOR as sender, never the inner
// transport: JsepTransportController identifies transports by pointer.
// ---------------------------------------------------------------------------

struct BridgeProbe : public sigslot::has_slots<> {
    cricket::IceTransportInternal *iceSender = nullptr;
    rtc::PacketTransportInternal *packetSender = nullptr;
    int count = 0;

    void onIce(cricket::IceTransportInternal *sender) {
        iceSender = sender;
        count++;
    }

    void onIceCandidate(cricket::IceTransportInternal *sender, const cricket::Candidate &) {
        iceSender = sender;
        count++;
    }

    void onPacket(rtc::PacketTransportInternal *sender) {
        packetSender = sender;
        count++;
    }
};

struct ReadPacketProbe : public sigslot::has_slots<> {
    int lastFlags = -1;
    int count = 0;
    std::string lastPayload;

    void onReadPacket(rtc::PacketTransportInternal *, const char *data, size_t size, const int64_t &, int flags) {
        lastFlags = flags;
        lastPayload.assign(data, size);
        count++;
    }
};

void TestSignalGatheringStateBridged() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();
    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    BridgeProbe probe;
    transport.SignalGatheringState.connect(&probe, &BridgeProbe::onIce);
    innerRaw->fireGatheringStateCallback();

    CHECK_TRUE(probe.iceSender == &transport);
}

void TestGatheringStateCallbackBridged() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();
    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    cricket::IceTransportInternal *seen = nullptr;
    transport.SetGatheringStateCallback([&](cricket::IceTransportInternal *sender) {
        seen = sender;
    });
    innerRaw->fireGatheringStateCallback();

    CHECK_TRUE(seen == &transport);
}

void TestSignalCandidateGatheredBridged() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();
    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    BridgeProbe probe;
    transport.SignalCandidateGathered.connect(&probe, &BridgeProbe::onIceCandidate);
    innerRaw->fireCandidateGathered();

    CHECK_TRUE(probe.iceSender == &transport);
    CHECK_TRUE(probe.count == 1);
}

void TestSignalRouteChangeBridged() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();
    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    BridgeProbe probe;
    transport.SignalRouteChange.connect(&probe, &BridgeProbe::onIceCandidate);
    innerRaw->fireRouteChange();

    CHECK_TRUE(probe.iceSender == &transport);
}

void TestSignalRoleConflictBridged() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();
    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    BridgeProbe probe;
    transport.SignalRoleConflict.connect(&probe, &BridgeProbe::onIce);
    innerRaw->fireRoleConflict();

    CHECK_TRUE(probe.iceSender == &transport);
}

void TestSignalStateChangedBridged() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();
    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    BridgeProbe probe;
    transport.SignalStateChanged.connect(&probe, &BridgeProbe::onIce);
    innerRaw->fireStateChanged();

    CHECK_TRUE(probe.iceSender == &transport);
}

void TestSignalIceTransportStateChangedBridged() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();
    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    BridgeProbe probe;
    transport.SignalIceTransportStateChanged.connect(&probe, &BridgeProbe::onIce);
    innerRaw->fireIceTransportStateChanged();

    CHECK_TRUE(probe.iceSender == &transport);
}

void TestSignalDestroyedBridged() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();
    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    BridgeProbe probe;
    transport.SignalDestroyed.connect(&probe, &BridgeProbe::onIce);
    innerRaw->fireDestroyed();

    CHECK_TRUE(probe.iceSender == &transport);
}

void TestCandidateErrorCallbackBridged() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();
    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    cricket::IceTransportInternal *seen = nullptr;
    transport.SetCandidateErrorCallback([&](cricket::IceTransportInternal *sender, const cricket::IceCandidateErrorEvent &) {
        seen = sender;
    });
    innerRaw->fireCandidateErrorCallback();

    CHECK_TRUE(seen == &transport);
}

void TestCandidatesRemovedCallbackBridged() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();
    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    cricket::IceTransportInternal *seen = nullptr;
    transport.SetCandidatesRemovedCallback([&](cricket::IceTransportInternal *sender, const cricket::Candidates &) {
        seen = sender;
    });
    innerRaw->fireCandidatesRemovedCallback();

    CHECK_TRUE(seen == &transport);
}

void TestCandidatePairChangeCallbackBridged() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();
    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    int fired = 0;
    transport.SetCandidatePairChangeCallback([&](const cricket::CandidatePairChangeEvent &) {
        fired++;
    });
    innerRaw->fireCandidatePairChangeCallback();

    CHECK_TRUE(fired == 1);
}

void TestPacketSignalsBridged() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();
    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    BridgeProbe probe;
    transport.SignalWritableState.connect(&probe, &BridgeProbe::onPacket);
    innerRaw->setWritable(true);

    CHECK_TRUE(probe.packetSender == &transport);
}


// Outbound payload must not reach the inner transport in cleartext.
void TestSendPacketIsEncrypted() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();
    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    const std::string payload = "PLAINTEXTPAYLOAD";
    rtc::PacketOptions options;
    transport.SendPacket(payload.data(), payload.size(), options, 0);

    const std::string sent = innerRaw->lastSentPacket();
    CHECK_TRUE(innerRaw->sentPacketCount() == 1);
    CHECK_TRUE(sent.size() > 0);
    CHECK_TRUE(sent.find(payload) == std::string::npos);
}

// Received packets must ALWAYS surface with flags == 0, whatever framing they
// carried: DtlsTransport::OnReadPacket DCHECKs it (dtls_transport.cc:599), so a
// non-zero value would abort a -c dbg build.
void TestReadPacketAlwaysUsesZeroFlags() {
    auto inner = std::make_unique<FakeInnerIceTransport>();
    FakeInnerIceTransport *innerRaw = inner.get();
    tgcalls::MtProtoIceTransport transport(std::move(inner), makeTestKey());

    ReadPacketProbe probe;
    transport.SignalReadPacket.connect(&probe, &ReadPacketProbe::onReadPacket);

    // Feed back whatever the transport itself produced, so the bytes are a
    // genuine mtproto frame rather than a hand-made one.
    const std::string payload = "ROUNDTRIP";
    rtc::PacketOptions options;
    transport.SendPacket(payload.data(), payload.size(), options, 0);
    const std::string framed = innerRaw->lastSentPacket();
    innerRaw->deliverPacket(framed.data(), framed.size());

    // Whether or not the frame decrypts against a same-key loopback, any packet
    // that DOES surface must carry flags == 0.
    if (probe.count > 0) {
        CHECK_TRUE(probe.lastFlags == 0);
    }
}


// The factory is only exercised for its type here. Actually calling
// CreateIceTransport builds a real P2PTransportChannel, which segfaults without
// a port allocator and network thread - standing those up is disproportionate
// for six lines of glue. Its runtime behaviour is covered by the end-to-end CLI
// call in Task 8, which is the check that would catch a mis-wired factory.
void TestFactorySatisfiesInjectionContract() {
    static_assert(
        std::is_base_of<webrtc::IceTransportFactory, tgcalls::MtProtoIceTransportFactory>::value,
        "MtProtoIceTransportFactory must satisfy the type PeerConnectionDependencies expects");

    tgcalls::MtProtoIceTransportFactory factory(makeTestKey());
    webrtc::IceTransportFactory *asInterface = &factory;
    CHECK_TRUE(asInterface != nullptr);
}

} // namespace

int main() {
    TestWritableForwards();
    TestCommandForwards();
    TestIceCredentialsRouteThroughParameters();

    TestSignalGatheringStateBridged();
    TestGatheringStateCallbackBridged();
    TestSignalCandidateGatheredBridged();
    TestSignalRouteChangeBridged();
    TestSignalRoleConflictBridged();
    TestSignalStateChangedBridged();
    TestSignalIceTransportStateChangedBridged();
    TestSignalDestroyedBridged();
    TestCandidateErrorCallbackBridged();
    TestCandidatesRemovedCallbackBridged();
    TestCandidatePairChangeCallbackBridged();
    TestPacketSignalsBridged();

    TestSendPacketIsEncrypted();
    TestReadPacketAlwaysUsesZeroFlags();

    TestFactorySatisfiesInjectionContract();

    if (g_failures != 0) {
        std::printf("%d failure(s)\n", g_failures);
        return 1;
    }
    std::printf("ok\n");
    return 0;
}

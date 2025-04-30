#ifndef TGCALLS_NATIVE_NETWORKING_IMPL_H
#define TGCALLS_NATIVE_NETWORKING_IMPL_H

#ifdef WEBRTC_WIN
// Compiler errors in conflicting Windows headers if not included here.
#include <winsock2.h>
#endif // WEBRTC_WIN

#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "api/candidate.h"
#include "media/base/media_channel.h"
#include "rtc_base/ssl_fingerprint.h"
#include "pc/sctp_data_channel.h"
#include "p2p/base/port.h"
#include "api/transport/field_trial_based_config.h"

#include <functional>
#include <memory>

#include "InstanceNetworking.h"
#include "Message.h"
#include "ThreadLocalObject.h"
#include "Instance.h"

namespace rtc {
class BasicPacketSocketFactory;
class BasicNetworkManager;
class PacketTransportInternal;
struct NetworkRoute;
} // namespace rtc

namespace cricket {
class BasicPortAllocator;
class P2PTransportChannel;
class IceTransportInternal;
class DtlsTransport;
class RelayPortFactoryInterface;
} // namespace cricket

namespace webrtc {
class TurnCustomizer;
class DtlsSrtpTransport;
class RtpTransport;
class AsyncDnsResolverFactoryInterface;
} // namespace webrtc

namespace tgcalls {

struct Message;
class SctpDataChannelProviderInterfaceImpl;
class Threads;

class NativeNetworkingImpl : public InstanceNetworking, public sigslot::has_slots<>, public std::enable_shared_from_this<NativeNetworkingImpl> {
public:
    static webrtc::CryptoOptions getDefaulCryptoOptions();

    NativeNetworkingImpl(Configuration &&configuration);
    virtual ~NativeNetworkingImpl();

    virtual void start() override;
    virtual void stop() override;

    virtual PeerIceParameters getLocalIceParameters() override;
    virtual std::unique_ptr<rtc::SSLFingerprint> getLocalFingerprint() override;
    virtual void setRemoteParams(PeerIceParameters const &remoteIceParameters, rtc::SSLFingerprint *fingerprint, std::string const &sslSetup) override;
    virtual void addCandidates(std::vector<cricket::Candidate> const &candidates) override;

    virtual void sendDataChannelMessage(std::string const &message) override;

    virtual webrtc::RtpTransport *getRtpTransport() override;

private:
    void resetDtlsSrtpTransport();
    void checkConnectionTimeout();
    void candidateGathered(cricket::IceTransportInternal *transport, const cricket::Candidate &candidate);
    void candidateGatheringState(cricket::IceTransportInternal *transport);
    void OnTransportWritableState_n(rtc::PacketTransportInternal *transport);
    void OnTransportReceivingState_n(rtc::PacketTransportInternal *transport);
    void transportStateChanged(cricket::IceTransportInternal *transport);
    void transportReadyToSend(cricket::IceTransportInternal *transport);
    void transportRouteChanged(absl::optional<rtc::NetworkRoute> route);
    void candidatePairChanged(cricket::CandidatePairChangeEvent const &event);
    void DtlsReadyToSend(bool DtlsReadyToSend);
    void UpdateAggregateStates_n();
    void RtpPacketReceived_n(rtc::CopyOnWriteBuffer *packet, int64_t packet_time_us, bool isUnresolved);
    void OnRtcpPacketReceived_n(rtc::CopyOnWriteBuffer *packet, int64_t packet_time_us);

    void sctpReadyToSendData();
    
    void notifyStateUpdated();
    
    void processPendingLocalStandaloneReflectorCandidates();

    std::shared_ptr<Threads> _threads;
    bool _isOutgoing = false;
    EncryptionKey _encryptionKey;
    bool _enableStunMarking = false;
    bool _enableTCP = false;
    bool _enableP2P = false;
    std::vector<RtcServer> _rtcServers;
    absl::optional<Proxy> _proxy;
    std::map<std::string, json11::Json> _customParameters;

    std::function<void(const InstanceNetworking::State &)> _stateUpdated;
    std::function<void(const cricket::Candidate &)> _candidateGathered;
    std::function<void(rtc::CopyOnWriteBuffer const &, bool)> _transportMessageReceived;
    std::function<void(rtc::CopyOnWriteBuffer const &, int64_t)> _rtcpPacketReceived;
    std::function<void(bool)> _dataChannelStateUpdated;
    std::function<void(std::string const &)> _dataChannelMessageReceived;

    std::unique_ptr<rtc::NetworkMonitorFactory> _networkMonitorFactory;
    rtc::SocketFactory *_underlyingSocketFactory = nullptr;
    std::unique_ptr<rtc::PacketSocketFactory> _socketFactory;
    std::unique_ptr<rtc::NetworkManager> _networkManager;
    std::unique_ptr<webrtc::TurnCustomizer> _turnCustomizer;
    std::unique_ptr<cricket::RelayPortFactoryInterface> _relayPortFactory;
    std::unique_ptr<cricket::BasicPortAllocator> _portAllocator;
    std::unique_ptr<webrtc::AsyncDnsResolverFactoryInterface> _asyncResolverFactory;
    std::unique_ptr<cricket::P2PTransportChannel> _transportChannel;
    std::unique_ptr<webrtc::RtpTransport> _mtProtoRtpTransport;
    std::unique_ptr<cricket::DtlsTransport> _dtlsTransport;
    std::unique_ptr<webrtc::DtlsSrtpTransport> _dtlsSrtpTransport;

    std::unique_ptr<SctpDataChannelProviderInterfaceImpl> _dataChannelInterface;

    webrtc::scoped_refptr<rtc::RTCCertificate> _localCertificate;
    PeerIceParameters _localIceParameters;
    absl::optional<PeerIceParameters> _remoteIceParameters;

    bool _isConnected = false;
    bool _isFailed = false;
    int64_t _lastDisconnectedTimestamp = 0;
    absl::optional<RouteDescription> _currentRouteDescription;
    absl::optional<ConnectionDescription> _currentConnectionDescription;
    
    std::vector<cricket::Candidate> _pendingLocalStandaloneReflectorCandidates;
};

} // namespace tgcalls

#endif

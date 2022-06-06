#include "v2/NativeNetworkingImpl.h"

#include "p2p/base/basic_packet_socket_factory.h"
#include "p2p/client/basic_port_allocator.h"
#include "p2p/base/p2p_transport_channel.h"
#include "p2p/base/basic_async_resolver_factory.h"
#include "api/packet_socket_factory.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "p2p/base/ice_credentials_iterator.h"
#include "api/jsep_ice_candidate.h"
#include "p2p/base/dtls_transport.h"
#include "p2p/base/dtls_transport_factory.h"
#include "pc/dtls_srtp_transport.h"
#include "pc/dtls_transport.h"
#include "pc/jsep_transport_controller.h"
#include "api/async_dns_resolver.h"

#include "TurnCustomizerImpl.h"
#include "SctpDataChannelProviderInterfaceImpl.h"
#include "StaticThreads.h"
#include "platform/PlatformInterface.h"
#include "p2p/base/turn_port.h"

namespace tgcalls {

namespace {

class CryptStringImpl : public rtc::CryptStringImpl {
public:
    CryptStringImpl(std::string const &value) :
    _value(value) {
    }
    
    virtual ~CryptStringImpl() override {
    }
    
    virtual size_t GetLength() const override {
        return _value.size();
    }
    
    virtual void CopyTo(char* dest, bool nullterminate) const override {
        memcpy(dest, _value.data(), _value.size());
        if (nullterminate) {
            dest[_value.size()] = 0;
        }
    }
    virtual std::string UrlEncode() const override {
        return _value;
    }
    virtual CryptStringImpl* Copy() const override {
        return new CryptStringImpl(_value);
    }
    
    virtual void CopyRawTo(std::vector<unsigned char>* dest) const override {
        dest->resize(_value.size());
        memcpy(dest->data(), _value.data(), _value.size());
    }
    
private:
    std::string _value;
};

class WrappedAsyncPacketSocket : public rtc::AsyncPacketSocket {
public:
    WrappedAsyncPacketSocket(std::unique_ptr<rtc::AsyncPacketSocket> &&wrappedSocket) :
    _wrappedSocket(std::move(wrappedSocket)) {
        _wrappedSocket->SignalReadPacket.connect(this, &WrappedAsyncPacketSocket::onReadPacket);
        _wrappedSocket->SignalSentPacket.connect(this, &WrappedAsyncPacketSocket::onSentPacket);
        _wrappedSocket->SignalReadyToSend.connect(this, &WrappedAsyncPacketSocket::onReadyToSend);
        _wrappedSocket->SignalAddressReady.connect(this, &WrappedAsyncPacketSocket::onAddressReady);
        _wrappedSocket->SignalConnect.connect(this, &WrappedAsyncPacketSocket::onConnect);
        _wrappedSocket->SignalClose.connect(this, &WrappedAsyncPacketSocket::onClose);
    }
    
    virtual ~WrappedAsyncPacketSocket() override {
        _wrappedSocket->SignalReadPacket.disconnect(this);
        _wrappedSocket->SignalSentPacket.disconnect(this);
        _wrappedSocket->SignalReadyToSend.disconnect(this);
        _wrappedSocket->SignalAddressReady.disconnect(this);
        _wrappedSocket->SignalConnect.disconnect(this);
        _wrappedSocket->SignalClose.disconnect(this);
        
        _wrappedSocket.reset();
    }

    virtual rtc::SocketAddress GetLocalAddress() const override {
        return _wrappedSocket->GetLocalAddress();
    }

    virtual rtc::SocketAddress GetRemoteAddress() const override {
        return _wrappedSocket->GetRemoteAddress();
    }

    virtual int Send(const void* pv, size_t cb, const rtc::PacketOptions& options) override {
        return _wrappedSocket->Send(pv, cb, options);
    }
    
    virtual int SendTo(const void* pv,
                       size_t cb,
                       const rtc::SocketAddress& addr,
                       const rtc::PacketOptions& options) override {
        return _wrappedSocket->SendTo(pv, cb, addr, options);
    }

    virtual int Close() override {
        return _wrappedSocket->Close();
    }

    virtual State GetState() const override {
        return _wrappedSocket->GetState();
    }

    virtual int GetOption(rtc::Socket::Option opt, int* value) override {
        return _wrappedSocket->GetOption(opt, value);
    }
    
    virtual int SetOption(rtc::Socket::Option opt, int value) override {
        return _wrappedSocket->SetOption(opt, value);
    }

    virtual int GetError() const override {
        return _wrappedSocket->GetError();
    }
    
    virtual void SetError(int error) override {
        _wrappedSocket->SetError(error);
    }
    
private:
    void onReadPacket(AsyncPacketSocket *socket, const char *data, size_t size, const rtc::SocketAddress &address, const int64_t &timestamp) {
        SignalReadPacket.emit(this, data, size, address, timestamp);
    }
    
    void onSentPacket(AsyncPacketSocket *socket, const rtc::SentPacket &packet) {
        SignalSentPacket.emit(this, packet);
    }

    void onReadyToSend(AsyncPacketSocket *socket) {
        SignalReadyToSend.emit(this);
    }
    
    void onAddressReady(AsyncPacketSocket *socket, const rtc::SocketAddress &address) {
        SignalAddressReady.emit(this, address);
    }

    void onConnect(AsyncPacketSocket *socket) {
        SignalConnect.emit(this);
    }
    
    void onClose(AsyncPacketSocket *socket, int value) {
        SignalClose(this, value);
    }
    
private:
    std::unique_ptr<rtc::AsyncPacketSocket> _wrappedSocket;
};

class WrappedBasicPacketSocketFactory : public rtc::BasicPacketSocketFactory {
public:
    explicit WrappedBasicPacketSocketFactory(rtc::SocketFactory* socket_factory) :
    rtc::BasicPacketSocketFactory(socket_factory) {
    }
    
    virtual ~WrappedBasicPacketSocketFactory() override {
    }
    
    rtc::AsyncPacketSocket* CreateUdpSocket(const rtc::SocketAddress& local_address, uint16_t min_port, uint16_t max_port) override {
        rtc::AsyncPacketSocket *socket = rtc::BasicPacketSocketFactory::CreateUdpSocket(local_address, min_port, max_port);
        if (socket) {
            std::unique_ptr<rtc::AsyncPacketSocket> socketPtr;
            socketPtr.reset(socket);
            
            return new WrappedAsyncPacketSocket(std::move(socketPtr));
        } else {
            return nullptr;
        }
    }
    
private:

};

}

NativeNetworkingImpl::ConnectionDescription::CandidateDescription NativeNetworkingImpl::connectionDescriptionFromCandidate(cricket::Candidate const &candidate) {
    NativeNetworkingImpl::ConnectionDescription::CandidateDescription result;
    
    result.type = candidate.type();
    result.protocol = candidate.protocol();
    result.address = candidate.address().ToString();
    
    return result;
}

webrtc::CryptoOptions NativeNetworkingImpl::getDefaulCryptoOptions() {
    auto options = webrtc::CryptoOptions();
    options.srtp.enable_aes128_sha1_80_crypto_cipher = true;
    options.srtp.enable_gcm_crypto_suites = true;
    return options;
}

NativeNetworkingImpl::NativeNetworkingImpl(Configuration &&configuration) :
_threads(std::move(configuration.threads)),
_isOutgoing(configuration.isOutgoing),
_enableStunMarking(configuration.enableStunMarking),
_enableTCP(configuration.enableTCP),
_enableP2P(configuration.enableP2P),
_rtcServers(configuration.rtcServers),
_proxy(configuration.proxy),
_stateUpdated(std::move(configuration.stateUpdated)),
_candidateGathered(std::move(configuration.candidateGathered)),
_transportMessageReceived(std::move(configuration.transportMessageReceived)),
_rtcpPacketReceived(std::move(configuration.rtcpPacketReceived)),
_dataChannelStateUpdated(configuration.dataChannelStateUpdated),
_dataChannelMessageReceived(configuration.dataChannelMessageReceived) {
    assert(_threads->getNetworkThread()->IsCurrent());
    
    _localIceParameters = PeerIceParameters(rtc::CreateRandomString(cricket::ICE_UFRAG_LENGTH), rtc::CreateRandomString(cricket::ICE_PWD_LENGTH), true);
    
    _localCertificate = rtc::RTCCertificateGenerator::GenerateCertificate(rtc::KeyParams(rtc::KT_ECDSA), absl::nullopt);
    
    _networkMonitorFactory = PlatformInterface::SharedInstance()->createNetworkMonitorFactory();
    _socketFactory.reset(new rtc::BasicPacketSocketFactory(_threads->getNetworkThread()->socketserver()));
    _networkManager = std::make_unique<rtc::BasicNetworkManager>(_networkMonitorFactory.get(), nullptr);
    
    _asyncResolverFactory = std::make_unique<webrtc::WrappingAsyncDnsResolverFactory>(std::make_unique<webrtc::BasicAsyncResolverFactory>());
    
    _dtlsSrtpTransport = std::make_unique<webrtc::DtlsSrtpTransport>(true);
    _dtlsSrtpTransport->SetDtlsTransports(nullptr, nullptr);
    _dtlsSrtpTransport->SetActiveResetSrtpParams(false);
    _dtlsSrtpTransport->SignalReadyToSend.connect(this, &NativeNetworkingImpl::DtlsReadyToSend);
    _dtlsSrtpTransport->SignalRtpPacketReceived.connect(this, &NativeNetworkingImpl::RtpPacketReceived_n);
    _dtlsSrtpTransport->SignalRtcpPacketReceived.connect(this, &NativeNetworkingImpl::OnRtcpPacketReceived_n);
    
    resetDtlsSrtpTransport();
}

NativeNetworkingImpl::~NativeNetworkingImpl() {
    assert(_threads->getNetworkThread()->IsCurrent());

    RTC_LOG(LS_INFO) << "NativeNetworkingImpl::~NativeNetworkingImpl()";

    _dtlsSrtpTransport.reset();
    _dtlsTransport.reset();
    _dataChannelInterface.reset();
    _transportChannel.reset();
    _asyncResolverFactory.reset();
    _portAllocator.reset();
    _networkManager.reset();
    _socketFactory.reset();
    _networkMonitorFactory.reset();
}

void NativeNetworkingImpl::resetDtlsSrtpTransport() {
    if (_enableStunMarking) {
        _turnCustomizer.reset(new TurnCustomizerImpl());
    }

    _portAllocator.reset(new cricket::BasicPortAllocator(_networkManager.get(), _socketFactory.get(), _turnCustomizer.get(), nullptr));

    uint32_t flags = _portAllocator->flags();

    flags |=
        //cricket::PORTALLOCATOR_ENABLE_SHARED_SOCKET |
        cricket::PORTALLOCATOR_ENABLE_IPV6 |
        cricket::PORTALLOCATOR_ENABLE_IPV6_ON_WIFI;

    /*if (_proxy) {
        rtc::ProxyInfo proxyInfo;
        proxyInfo.type = rtc::ProxyType::PROXY_SOCKS5;
        proxyInfo.address = rtc::SocketAddress(_proxy->host, _proxy->port);
        proxyInfo.username = _proxy->login;
        proxyInfo.password = rtc::CryptString(CryptStringImpl(_proxy->password));
        _portAllocator->set_proxy("t/1.0", proxyInfo);
        
        flags &= ~cricket::PORTALLOCATOR_DISABLE_TCP;
    } else */{
        if (!_enableTCP) {
            flags |= cricket::PORTALLOCATOR_DISABLE_TCP;
        }
    }
    
    if (_proxy || !_enableP2P) {
        flags |= cricket::PORTALLOCATOR_DISABLE_UDP;
        flags |= cricket::PORTALLOCATOR_DISABLE_STUN;
        uint32_t candidateFilter = _portAllocator->candidate_filter();
        candidateFilter &= ~(cricket::CF_REFLEXIVE);
        _portAllocator->SetCandidateFilter(candidateFilter);
    }
    
    _portAllocator->set_step_delay(cricket::kMinimumStepDelay);

    _portAllocator->set_flags(flags);
    _portAllocator->Initialize();

    cricket::ServerAddresses stunServers;
    std::vector<cricket::RelayServerConfig> turnServers;

    for (auto &server : _rtcServers) {
        if (server.isTurn) {
            turnServers.push_back(cricket::RelayServerConfig(
                rtc::SocketAddress(server.host, server.port),
                server.login,
                server.password,
                cricket::PROTO_UDP
            ));
        } else {
            rtc::SocketAddress stunAddress = rtc::SocketAddress(server.host, server.port);
            stunServers.insert(stunAddress);
        }
    }

    _portAllocator->SetConfiguration(stunServers, turnServers, 0, webrtc::NO_PRUNE, _turnCustomizer.get());

    
    _transportChannel = cricket::P2PTransportChannel::Create("transport", 0, _portAllocator.get(), _asyncResolverFactory.get());

    cricket::IceConfig iceConfig;
    iceConfig.continual_gathering_policy = cricket::GATHER_CONTINUALLY;
    iceConfig.prioritize_most_likely_candidate_pairs = true;
    iceConfig.regather_on_failed_networks_interval = 8000;
    _transportChannel->SetIceConfig(iceConfig);

    cricket::IceParameters localIceParameters(
        _localIceParameters.ufrag,
        _localIceParameters.pwd,
        _localIceParameters.supportsRenomination
    );

    _transportChannel->SetIceParameters(localIceParameters);
    _transportChannel->SetIceRole(_isOutgoing ? cricket::ICEROLE_CONTROLLING : cricket::ICEROLE_CONTROLLED);
    _transportChannel->SetRemoteIceMode(cricket::ICEMODE_FULL);

    _transportChannel->SignalCandidateGathered.connect(this, &NativeNetworkingImpl::candidateGathered);
    _transportChannel->SignalIceTransportStateChanged.connect(this, &NativeNetworkingImpl::transportStateChanged);
    _transportChannel->SignalCandidatePairChanged.connect(this, &NativeNetworkingImpl::candidatePairChanged);
    _transportChannel->SignalNetworkRouteChanged.connect(this, &NativeNetworkingImpl::transportRouteChanged);

    webrtc::CryptoOptions cryptoOptions = NativeNetworkingImpl::getDefaulCryptoOptions();
    _dtlsTransport.reset(new cricket::DtlsTransport(_transportChannel.get(), cryptoOptions, nullptr));

    _dtlsTransport->SignalWritableState.connect(
        this, &NativeNetworkingImpl::OnTransportWritableState_n);
    _dtlsTransport->SignalReceivingState.connect(
        this, &NativeNetworkingImpl::OnTransportReceivingState_n);

    _dtlsTransport->SetLocalCertificate(_localCertificate);
    
    _dtlsSrtpTransport->SetDtlsTransports(_dtlsTransport.get(), nullptr);
}

void NativeNetworkingImpl::start() {
    _transportChannel->MaybeStartGathering();

    const auto weak = std::weak_ptr<NativeNetworkingImpl>(shared_from_this());
    _dataChannelInterface.reset(new SctpDataChannelProviderInterfaceImpl(
        _dtlsTransport.get(),
        _isOutgoing,
        [weak, threads = _threads](bool state) {
            assert(threads->getNetworkThread()->IsCurrent());
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->_dataChannelStateUpdated(state);
        },
        [weak, threads = _threads]() {
            assert(threads->getNetworkThread()->IsCurrent());
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            //strong->restartDataChannel();
        },
        [weak, threads = _threads](std::string const &message) {
            assert(threads->getNetworkThread()->IsCurrent());
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->_dataChannelMessageReceived(message);
        },
        _threads
    ));
    
    _lastDisconnectedTimestamp = rtc::TimeMillis();
    checkConnectionTimeout();
}

void NativeNetworkingImpl::stop() {
    _transportChannel->SignalCandidateGathered.disconnect(this);
    _transportChannel->SignalIceTransportStateChanged.disconnect(this);
    _transportChannel->SignalReadPacket.disconnect(this);
    _transportChannel->SignalNetworkRouteChanged.disconnect(this);
    
    _dtlsTransport->SignalWritableState.disconnect(this);
    _dtlsTransport->SignalReceivingState.disconnect(this);
    
    _dtlsSrtpTransport->SetDtlsTransports(nullptr, nullptr);
    
    _dataChannelInterface.reset();
    _dtlsTransport.reset();
    _transportChannel.reset();
    _portAllocator.reset();
    
    _localIceParameters = PeerIceParameters(rtc::CreateRandomString(cricket::ICE_UFRAG_LENGTH), rtc::CreateRandomString(cricket::ICE_PWD_LENGTH), true);
    
    _localCertificate = rtc::RTCCertificateGenerator::GenerateCertificate(rtc::KeyParams(rtc::KT_ECDSA), absl::nullopt);
    
    resetDtlsSrtpTransport();
}

PeerIceParameters NativeNetworkingImpl::getLocalIceParameters() {
    return _localIceParameters;
}

std::unique_ptr<rtc::SSLFingerprint> NativeNetworkingImpl::getLocalFingerprint() {
    auto certificate = _localCertificate;
    if (!certificate) {
        return nullptr;
    }
    return rtc::SSLFingerprint::CreateFromCertificate(*certificate);
}

void NativeNetworkingImpl::setRemoteParams(PeerIceParameters const &remoteIceParameters, rtc::SSLFingerprint *fingerprint, std::string const &sslSetup) {
    _remoteIceParameters = remoteIceParameters;

    cricket::IceParameters parameters(
        remoteIceParameters.ufrag,
        remoteIceParameters.pwd,
        remoteIceParameters.supportsRenomination
    );

    _transportChannel->SetRemoteIceParameters(parameters);

    if (sslSetup == "active") {
        _dtlsTransport->SetDtlsRole(rtc::SSLRole::SSL_SERVER);
    } else if (sslSetup == "passive") {
        _dtlsTransport->SetDtlsRole(rtc::SSLRole::SSL_CLIENT);
    } else {
        _dtlsTransport->SetDtlsRole(_isOutgoing ? rtc::SSLRole::SSL_CLIENT : rtc::SSLRole::SSL_SERVER);
    }

    if (fingerprint) {
        _dtlsTransport->SetRemoteFingerprint(fingerprint->algorithm, fingerprint->digest.data(), fingerprint->digest.size());
    }
}

void NativeNetworkingImpl::addCandidates(std::vector<cricket::Candidate> const &candidates) {
    for (const auto &candidate : candidates) {
        _transportChannel->AddRemoteCandidate(candidate);
    }
}

void NativeNetworkingImpl::sendDataChannelMessage(std::string const &message) {
    if (_dataChannelInterface) {
        _dataChannelInterface->sendDataChannelMessage(message);
    }
}

webrtc::RtpTransport *NativeNetworkingImpl::getRtpTransport() {
    return _dtlsSrtpTransport.get();
}

void NativeNetworkingImpl::checkConnectionTimeout() {
    const auto weak = std::weak_ptr<NativeNetworkingImpl>(shared_from_this());
    _threads->getNetworkThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
        auto strong = weak.lock();
        if (!strong) {
            return;
        }

        int64_t currentTimestamp = rtc::TimeMillis();
        const int64_t maxTimeout = 20000;

        if (!strong->_isConnected && strong->_lastDisconnectedTimestamp + maxTimeout < currentTimestamp) {
            RTC_LOG(LS_INFO) << "NativeNetworkingImpl timeout " << (currentTimestamp - strong->_lastDisconnectedTimestamp) << " ms";
            
            strong->_isFailed = true;
            strong->notifyStateUpdated();
        }

        strong->checkConnectionTimeout();
    }, 1000);
}

void NativeNetworkingImpl::candidateGathered(cricket::IceTransportInternal *transport, const cricket::Candidate &candidate) {
    assert(_threads->getNetworkThread()->IsCurrent());

    _candidateGathered(candidate);
}

void NativeNetworkingImpl::candidateGatheringState(cricket::IceTransportInternal *transport) {
    assert(_threads->getNetworkThread()->IsCurrent());
}

void NativeNetworkingImpl::OnTransportWritableState_n(rtc::PacketTransportInternal *transport) {
    assert(_threads->getNetworkThread()->IsCurrent());

    UpdateAggregateStates_n();
}
void NativeNetworkingImpl::OnTransportReceivingState_n(rtc::PacketTransportInternal *transport) {
    assert(_threads->getNetworkThread()->IsCurrent());

    UpdateAggregateStates_n();
}

void NativeNetworkingImpl::DtlsReadyToSend(bool isReadyToSend) {
    UpdateAggregateStates_n();

    if (isReadyToSend) {
        const auto weak = std::weak_ptr<NativeNetworkingImpl>(shared_from_this());
        _threads->getNetworkThread()->PostTask(RTC_FROM_HERE, [weak]() {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->UpdateAggregateStates_n();
        });
    }
}

void NativeNetworkingImpl::transportStateChanged(cricket::IceTransportInternal *transport) {
    UpdateAggregateStates_n();
}

void NativeNetworkingImpl::transportReadyToSend(cricket::IceTransportInternal *transport) {
    assert(_threads->getNetworkThread()->IsCurrent());
}

void NativeNetworkingImpl::transportRouteChanged(absl::optional<rtc::NetworkRoute> route) {
    assert(_threads->getNetworkThread()->IsCurrent());
    
    if (route.has_value()) {
        /*cricket::IceTransportStats iceTransportStats;
        if (_transportChannel->GetStats(&iceTransportStats)) {
        }*/
        
        RTC_LOG(LS_INFO) << "NativeNetworkingImpl route changed: " << route->DebugString();
        
        bool localIsWifi = route->local.adapter_type() == rtc::AdapterType::ADAPTER_TYPE_WIFI;
        bool remoteIsWifi = route->remote.adapter_type() == rtc::AdapterType::ADAPTER_TYPE_WIFI;
        
        RTC_LOG(LS_INFO) << "NativeNetworkingImpl is wifi: local=" << localIsWifi << ", remote=" << remoteIsWifi;
        
        std::string localDescription = route->local.uses_turn() ? "turn" : "p2p";
        std::string remoteDescription = route->remote.uses_turn() ? "turn" : "p2p";
        
        RouteDescription routeDescription(localDescription, remoteDescription);
        
        if (!_currentRouteDescription || routeDescription != _currentRouteDescription.value()) {
            _currentRouteDescription = std::move(routeDescription);
            notifyStateUpdated();
        }
    }
}

void NativeNetworkingImpl::candidatePairChanged(cricket::CandidatePairChangeEvent const &event) {
    ConnectionDescription connectionDescription;
    
    connectionDescription.local = connectionDescriptionFromCandidate(event.selected_candidate_pair.local);
    connectionDescription.remote = connectionDescriptionFromCandidate(event.selected_candidate_pair.remote);
    
    if (!_currentConnectionDescription || _currentConnectionDescription.value() != connectionDescription) {
        _currentConnectionDescription = std::move(connectionDescription);
        notifyStateUpdated();
    }
}

void NativeNetworkingImpl::RtpPacketReceived_n(rtc::CopyOnWriteBuffer *packet, int64_t packet_time_us, bool isUnresolved) {
    if (_transportMessageReceived) {
        _transportMessageReceived(*packet, isUnresolved);
    }
}

void NativeNetworkingImpl::OnRtcpPacketReceived_n(rtc::CopyOnWriteBuffer *packet, int64_t packet_time_us) {
    if (_rtcpPacketReceived) {
        _rtcpPacketReceived(*packet, packet_time_us);
    }
}

void NativeNetworkingImpl::UpdateAggregateStates_n() {
    assert(_threads->getNetworkThread()->IsCurrent());

    auto state = _transportChannel->GetIceTransportState();
    bool isConnected = false;
    switch (state) {
        case webrtc::IceTransportState::kConnected:
        case webrtc::IceTransportState::kCompleted:
            isConnected = true;
            break;
        default:
            break;
    }

    if (!_dtlsSrtpTransport->IsWritable(false)) {
        isConnected = false;
    }

    if (_isConnected != isConnected) {
        _isConnected = isConnected;
        
        if (!isConnected) {
            _lastDisconnectedTimestamp = rtc::TimeMillis();
        }

        notifyStateUpdated();

        if (_dataChannelInterface) {
            _dataChannelInterface->updateIsConnected(isConnected);
        }
    }
}

void NativeNetworkingImpl::notifyStateUpdated() {
    NativeNetworkingImpl::State emitState;
    emitState.isReadyToSendData = _isConnected;
    emitState.route = _currentRouteDescription;
    emitState.connection = _currentConnectionDescription;
    emitState.isFailed = _isFailed;
    _stateUpdated(emitState);
}

void NativeNetworkingImpl::sctpReadyToSendData() {
}

void NativeNetworkingImpl::sctpDataReceived(const cricket::ReceiveDataParams& params, const rtc::CopyOnWriteBuffer& buffer) {
}

} // namespace tgcalls

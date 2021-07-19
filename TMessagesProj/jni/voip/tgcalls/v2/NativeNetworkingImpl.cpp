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
#include "TurnCustomizerImpl.h"
#include "SctpDataChannelProviderInterfaceImpl.h"
#include "StaticThreads.h"

namespace tgcalls {

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
_stateUpdated(std::move(configuration.stateUpdated)),
_candidateGathered(std::move(configuration.candidateGathered)),
_transportMessageReceived(std::move(configuration.transportMessageReceived)),
_rtcpPacketReceived(std::move(configuration.rtcpPacketReceived)),
_dataChannelStateUpdated(configuration.dataChannelStateUpdated),
_dataChannelMessageReceived(configuration.dataChannelMessageReceived) {
    assert(_threads->getNetworkThread()->IsCurrent());
    
    _localIceParameters = PeerIceParameters(rtc::CreateRandomString(cricket::ICE_UFRAG_LENGTH), rtc::CreateRandomString(cricket::ICE_PWD_LENGTH));
    
    _localCertificate = rtc::RTCCertificateGenerator::GenerateCertificate(rtc::KeyParams(rtc::KT_ECDSA), absl::nullopt);
    
    _socketFactory.reset(new rtc::BasicPacketSocketFactory(_threads->getNetworkThread()));
    _networkManager = std::make_unique<rtc::BasicNetworkManager>();
    _asyncResolverFactory = std::make_unique<webrtc::BasicAsyncResolverFactory>();
    
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

    if (!_enableTCP) {
        flags |= cricket::PORTALLOCATOR_DISABLE_TCP;
    }
    if (!_enableP2P) {
        flags |= cricket::PORTALLOCATOR_DISABLE_UDP;
        flags |= cricket::PORTALLOCATOR_DISABLE_STUN;
        uint32_t candidateFilter = _portAllocator->candidate_filter();
        candidateFilter &= ~(cricket::CF_REFLEXIVE);
        _portAllocator->SetCandidateFilter(candidateFilter);
    }

    _portAllocator->set_step_delay(cricket::kMinimumStepDelay);

    //TODO: figure out the proxy setup
    /*if (_proxy) {
        rtc::ProxyInfo proxyInfo;
        proxyInfo.type = rtc::ProxyType::PROXY_SOCKS5;
        proxyInfo.address = rtc::SocketAddress(_proxy->host, _proxy->port);
        proxyInfo.username = _proxy->login;
        proxyInfo.password = rtc::CryptString(TgCallsCryptStringImpl(_proxy->password));
        _portAllocator->set_proxy("t/1.0", proxyInfo);
    }*/

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

    _portAllocator->SetConfiguration(stunServers, turnServers, 2, webrtc::NO_PRUNE, _turnCustomizer.get());

    _transportChannel.reset(new cricket::P2PTransportChannel("transport", 0, _portAllocator.get(), _asyncResolverFactory.get(), nullptr));

    cricket::IceConfig iceConfig;
    iceConfig.continual_gathering_policy = cricket::GATHER_CONTINUALLY;
    iceConfig.prioritize_most_likely_candidate_pairs = true;
    iceConfig.regather_on_failed_networks_interval = 8000;
    _transportChannel->SetIceConfig(iceConfig);

    cricket::IceParameters localIceParameters(
        _localIceParameters.ufrag,
        _localIceParameters.pwd,
        false
    );

    _transportChannel->SetIceParameters(localIceParameters);
    _transportChannel->SetIceRole(_isOutgoing ? cricket::ICEROLE_CONTROLLING : cricket::ICEROLE_CONTROLLED);
    _transportChannel->SetRemoteIceMode(cricket::ICEMODE_FULL);

    _transportChannel->SignalCandidateGathered.connect(this, &NativeNetworkingImpl::candidateGathered);
    _transportChannel->SignalIceTransportStateChanged.connect(this, &NativeNetworkingImpl::transportStateChanged);
    _transportChannel->SignalReadPacket.connect(this, &NativeNetworkingImpl::transportPacketReceived);

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
}

void NativeNetworkingImpl::stop() {
    _transportChannel->SignalCandidateGathered.disconnect(this);
    _transportChannel->SignalIceTransportStateChanged.disconnect(this);
    _transportChannel->SignalReadPacket.disconnect(this);
    
    _dtlsTransport->SignalWritableState.disconnect(this);
    _dtlsTransport->SignalReceivingState.disconnect(this);
    
    _dtlsSrtpTransport->SetDtlsTransports(nullptr, nullptr);
    
    _dataChannelInterface.reset();
    _dtlsTransport.reset();
    _transportChannel.reset();
    _portAllocator.reset();
    
    _localIceParameters = PeerIceParameters(rtc::CreateRandomString(cricket::ICE_UFRAG_LENGTH), rtc::CreateRandomString(cricket::ICE_PWD_LENGTH));
    
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
        false
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

        if (strong->_lastNetworkActivityMs + maxTimeout < currentTimestamp) {
            NativeNetworkingImpl::State emitState;
            emitState.isReadyToSendData = false;
            emitState.isFailed = true;
            strong->_stateUpdated(emitState);
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

void NativeNetworkingImpl::transportPacketReceived(rtc::PacketTransportInternal *transport, const char *bytes, size_t size, const int64_t &timestamp, int unused) {
    assert(_threads->getNetworkThread()->IsCurrent());

    _lastNetworkActivityMs = rtc::TimeMillis();
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

        NativeNetworkingImpl::State emitState;
        emitState.isReadyToSendData = isConnected;
        _stateUpdated(emitState);

        if (_dataChannelInterface) {
            _dataChannelInterface->updateIsConnected(isConnected);
        }
    }
}

void NativeNetworkingImpl::sctpReadyToSendData() {
}

void NativeNetworkingImpl::sctpDataReceived(const cricket::ReceiveDataParams& params, const rtc::CopyOnWriteBuffer& buffer) {
}

} // namespace tgcalls

#include "NetworkManager.h"

#include "Message.h"

#include "p2p/base/basic_packet_socket_factory.h"
#include "v2/ReflectorRelayPortFactory.h"
#include "p2p/client/basic_port_allocator.h"
#include "p2p/base/p2p_transport_channel.h"
#include "p2p/base/basic_async_resolver_factory.h"
#include "api/packet_socket_factory.h"
#include "p2p/base/ice_credentials_iterator.h"
#include "api/jsep_ice_candidate.h"
#include "rtc_base/network_monitor_factory.h"

#include "TurnCustomizerImpl.h"
#include "platform/PlatformInterface.h"

extern "C" {
#include <openssl/sha.h>
#include <openssl/aes.h>
#ifndef OPENSSL_IS_BORINGSSL
#include <openssl/modes.h>
#endif
#include <openssl/rand.h>
#include <openssl/crypto.h>
} // extern "C"

namespace tgcalls {

class TgCallsCryptStringImpl : public rtc::CryptStringImpl {
public:
    TgCallsCryptStringImpl(std::string const &value) :
    _value(value) {
    }
    
    virtual ~TgCallsCryptStringImpl() override {
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
        return new TgCallsCryptStringImpl(_value);
    }
    
    virtual void CopyRawTo(std::vector<unsigned char>* dest) const override {
        dest->resize(_value.size());
        memcpy(dest->data(), _value.data(), _value.size());
    }
    
private:
    std::string _value;
};

NetworkManager::NetworkManager(
	rtc::Thread *thread,
	EncryptionKey encryptionKey,
	bool enableP2P,
    bool enableTCP,
    bool enableStunMarking,
	std::vector<RtcServer> const &rtcServers,
    std::unique_ptr<Proxy> proxy,
	std::function<void(const NetworkManager::State &)> stateUpdated,
	std::function<void(DecryptedMessage &&)> transportMessageReceived,
	std::function<void(Message &&)> sendSignalingMessage,
	std::function<void(int delayMs, int cause)> sendTransportServiceAsync) :
_thread(thread),
_enableP2P(enableP2P),
_enableTCP(enableTCP),
_enableStunMarking(enableStunMarking),
_rtcServers(rtcServers),
_proxy(std::move(proxy)),
_transport(
	EncryptedConnection::Type::Transport,
	encryptionKey,
	[=](int delayMs, int cause) { sendTransportServiceAsync(delayMs, cause); }),
_isOutgoing(encryptionKey.isOutgoing),
_stateUpdated(std::move(stateUpdated)),
_transportMessageReceived(std::move(transportMessageReceived)),
_sendSignalingMessage(std::move(sendSignalingMessage)),
_localIceParameters(rtc::CreateRandomString(cricket::ICE_UFRAG_LENGTH), rtc::CreateRandomString(cricket::ICE_PWD_LENGTH), false) {
	assert(_thread->IsCurrent());

    _networkMonitorFactory = PlatformInterface::SharedInstance()->createNetworkMonitorFactory();
}

NetworkManager::~NetworkManager() {
	assert(_thread->IsCurrent());
    
    RTC_LOG(LS_INFO) << "NetworkManager::~NetworkManager()";

	_transportChannel.reset();
	_asyncResolverFactory.reset();
	_portAllocator.reset();
	_networkManager.reset();
	_socketFactory.reset();
    _networkMonitorFactory.reset();
}

void NetworkManager::start() {
    _socketFactory.reset(new rtc::BasicPacketSocketFactory(_thread->socketserver()));

    _networkManager = std::make_unique<rtc::BasicNetworkManager>(_networkMonitorFactory.get(), _thread->socketserver());
    
    if (_enableStunMarking) {
        _turnCustomizer.reset(new TurnCustomizerImpl());
    }
    
    _relayPortFactory.reset(new ReflectorRelayPortFactory(_rtcServers, false, 0, _thread->socketserver()));
    
    _portAllocator.reset(new cricket::BasicPortAllocator(_networkManager.get(), _socketFactory.get(), _turnCustomizer.get(), _relayPortFactory.get()));

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
    
    if (_proxy) {
        rtc::ProxyInfo proxyInfo;
        proxyInfo.type = rtc::ProxyType::PROXY_SOCKS5;
        proxyInfo.address = rtc::SocketAddress(_proxy->host, _proxy->port);
        proxyInfo.username = _proxy->login;
        proxyInfo.password = rtc::CryptString(TgCallsCryptStringImpl(_proxy->password));
        _portAllocator->set_proxy("t/1.0", proxyInfo);
    }
    
    _portAllocator->set_flags(flags);
    _portAllocator->Initialize();

    cricket::ServerAddresses stunServers;
    std::vector<cricket::RelayServerConfig> turnServers;

    for (auto &server : _rtcServers) {
        if (server.isTcp) {
            continue;
        }
        
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

    _asyncResolverFactory = std::make_unique<webrtc::BasicAsyncDnsResolverFactory>();

    webrtc::IceTransportInit iceTransportInit;
    iceTransportInit.set_port_allocator(_portAllocator.get());
    iceTransportInit.set_async_dns_resolver_factory(_asyncResolverFactory.get());

    _transportChannel = cricket::P2PTransportChannel::Create("transport", 0, std::move(iceTransportInit));

    cricket::IceConfig iceConfig;
    iceConfig.continual_gathering_policy = cricket::GATHER_CONTINUALLY;
    iceConfig.prioritize_most_likely_candidate_pairs = true;
    iceConfig.regather_on_failed_networks_interval = cricket::REGATHER_ON_FAILED_NETWORKS_INTERVAL;
    _transportChannel->SetIceConfig(iceConfig);

    cricket::IceParameters localIceParameters(
        _localIceParameters.ufrag,
        _localIceParameters.pwd,
        false
    );

    _transportChannel->SetIceParameters(localIceParameters);
    _transportChannel->SetIceRole(_isOutgoing ? cricket::ICEROLE_CONTROLLING : cricket::ICEROLE_CONTROLLED);

    _transportChannel->SignalCandidateGathered.connect(this, &NetworkManager::candidateGathered);
    _transportChannel->SignalGatheringState.connect(this, &NetworkManager::candidateGatheringState);
    _transportChannel->SignalIceTransportStateChanged.connect(this, &NetworkManager::transportStateChanged);
    _transportChannel->SignalReadPacket.connect(this, &NetworkManager::transportPacketReceived);
    _transportChannel->SignalNetworkRouteChanged.connect(this, &NetworkManager::transportRouteChanged);

    _transportChannel->MaybeStartGathering();

    _transportChannel->SetRemoteIceMode(cricket::ICEMODE_FULL);
    
    _lastNetworkActivityMs = rtc::TimeMillis();
    
    checkConnectionTimeout();
}

void NetworkManager::receiveSignalingMessage(DecryptedMessage &&message) {
	const auto list = absl::get_if<CandidatesListMessage>(&message.message.data);
	assert(list != nullptr);

    if (!_remoteIceParameters.has_value()) {
        PeerIceParameters parameters(list->iceParameters.ufrag, list->iceParameters.pwd, false);
        _remoteIceParameters = parameters;

        cricket::IceParameters remoteIceParameters(
            parameters.ufrag,
            parameters.pwd,
            false
        );

        _transportChannel->SetRemoteIceParameters(remoteIceParameters);
    }

	for (const auto &candidate : list->candidates) {
		_transportChannel->AddRemoteCandidate(candidate);
	}
}

uint32_t NetworkManager::sendMessage(const Message &message) {
	if (const auto prepared = _transport.prepareForSending(message)) {
		rtc::PacketOptions packetOptions;
		_transportChannel->SendPacket((const char *)prepared->bytes.data(), prepared->bytes.size(), packetOptions, 0);
        addTrafficStats(prepared->bytes.size(), false);
		return prepared->counter;
	}
	return 0;
}

void NetworkManager::sendTransportService(int cause) {
	if (const auto prepared = _transport.prepareForSendingService(cause)) {
		rtc::PacketOptions packetOptions;
		_transportChannel->SendPacket((const char *)prepared->bytes.data(), prepared->bytes.size(), packetOptions, 0);
        addTrafficStats(prepared->bytes.size(), false);
	}
}

void NetworkManager::setIsLocalNetworkLowCost(bool isLocalNetworkLowCost) {
    _isLocalNetworkLowCost = isLocalNetworkLowCost;
    
    logCurrentNetworkState();
}

TrafficStats NetworkManager::getNetworkStats() {
    TrafficStats stats;
    stats.bytesSentWifi = _trafficStatsWifi.outgoing;
    stats.bytesReceivedWifi = _trafficStatsWifi.incoming;
    stats.bytesSentMobile = _trafficStatsCellular.outgoing;
    stats.bytesReceivedMobile = _trafficStatsCellular.incoming;
    return stats;
}

void NetworkManager::fillCallStats(CallStats &callStats) {
    callStats.networkRecords = _networkRecords;
}

void NetworkManager::logCurrentNetworkState() {
    if (!_currentEndpointType.has_value()) {
        return;
    }
    
    CallStatsNetworkRecord record;
    record.timestamp = (int32_t)(rtc::TimeMillis() / 1000);
    record.endpointType = *_currentEndpointType;
    record.isLowCost = _isLocalNetworkLowCost;
    _networkRecords.push_back(std::move(record));
}

void NetworkManager::checkConnectionTimeout() {
    const auto weak = std::weak_ptr<NetworkManager>(shared_from_this());
    _thread->PostDelayedTask([weak]() {
        auto strong = weak.lock();
        if (!strong) {
            return;
        }
        
        int64_t currentTimestamp = rtc::TimeMillis();
        const int64_t maxTimeout = 20000;
        
        if (strong->_lastNetworkActivityMs + maxTimeout < currentTimestamp) {
            NetworkManager::State emitState;
            emitState.isReadyToSendData = false;
            emitState.isFailed = true;
            strong->_stateUpdated(emitState);
        }
        
        strong->checkConnectionTimeout();
    }, webrtc::TimeDelta::Millis(1000));
}

void NetworkManager::candidateGathered(cricket::IceTransportInternal *transport, const cricket::Candidate &candidate) {
	assert(_thread->IsCurrent());
	_sendSignalingMessage({ CandidatesListMessage{ { 1, candidate }, _localIceParameters } });
}

void NetworkManager::candidateGatheringState(cricket::IceTransportInternal *transport) {
	assert(_thread->IsCurrent());
}

void NetworkManager::transportStateChanged(cricket::IceTransportInternal *transport) {
	assert(_thread->IsCurrent());

	auto state = transport->GetIceTransportState();
	bool isConnected = false;
	switch (state) {
		case webrtc::IceTransportState::kConnected:
		case webrtc::IceTransportState::kCompleted:
			isConnected = true;
			break;
		default:
			break;
	}
	NetworkManager::State emitState;
	emitState.isReadyToSendData = isConnected;
	_stateUpdated(emitState);
}

void NetworkManager::transportReadyToSend(cricket::IceTransportInternal *transport) {
	assert(_thread->IsCurrent());
}

void NetworkManager::transportPacketReceived(rtc::PacketTransportInternal *transport, const char *bytes, size_t size, const int64_t &timestamp, int unused) {
	assert(_thread->IsCurrent());
    
    _lastNetworkActivityMs = rtc::TimeMillis();
    
    addTrafficStats(size, true);

	if (auto decrypted = _transport.handleIncomingPacket(bytes, size)) {
		if (_transportMessageReceived) {
			_transportMessageReceived(std::move(decrypted->main));
			for (auto &message : decrypted->additional) {
				_transportMessageReceived(std::move(message));
			}
		}
	}
}

void NetworkManager::transportRouteChanged(absl::optional<rtc::NetworkRoute> route) {
    assert(_thread->IsCurrent());
    
    if (route.has_value()) {
        RTC_LOG(LS_INFO) << "NetworkManager route changed: " << route->DebugString();
        
        bool localIsWifi = route->local.adapter_type() == rtc::AdapterType::ADAPTER_TYPE_WIFI;
        bool remoteIsWifi = route->remote.adapter_type() == rtc::AdapterType::ADAPTER_TYPE_WIFI;
        
        RTC_LOG(LS_INFO) << "NetworkManager is wifi: local=" << localIsWifi << ", remote=" << remoteIsWifi;
        
        CallStatsConnectionEndpointType endpointType;
        if (route->local.uses_turn()) {
            endpointType = CallStatsConnectionEndpointType::ConnectionEndpointTURN;
        } else {
            endpointType = CallStatsConnectionEndpointType::ConnectionEndpointP2P;
        }
        if (!_currentEndpointType.has_value() || _currentEndpointType != endpointType) {
            _currentEndpointType = endpointType;
            logCurrentNetworkState();
        }
    }
}

void NetworkManager::addTrafficStats(int64_t byteCount, bool isIncoming) {
    if (_isLocalNetworkLowCost) {
        if (isIncoming) {
            _trafficStatsWifi.incoming += byteCount;
        } else {
            _trafficStatsWifi.outgoing += byteCount;
        }
    } else {
        if (isIncoming) {
            _trafficStatsCellular.incoming += byteCount;
        } else {
            _trafficStatsCellular.outgoing += byteCount;
        }
    }
}

} // namespace tgcalls

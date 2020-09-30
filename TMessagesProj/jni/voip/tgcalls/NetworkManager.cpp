#include "NetworkManager.h"

#include "Message.h"

#include "p2p/base/basic_packet_socket_factory.h"
#include "p2p/client/basic_port_allocator.h"
#include "p2p/base/p2p_transport_channel.h"
#include "p2p/base/basic_async_resolver_factory.h"
#include "api/packet_socket_factory.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "p2p/base/ice_credentials_iterator.h"
#include "api/jsep_ice_candidate.h"

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

NetworkManager::NetworkManager(
	rtc::Thread *thread,
	EncryptionKey encryptionKey,
	bool enableP2P,
	std::vector<RtcServer> const &rtcServers,
	std::function<void(const NetworkManager::State &)> stateUpdated,
	std::function<void(DecryptedMessage &&)> transportMessageReceived,
	std::function<void(Message &&)> sendSignalingMessage,
	std::function<void(int delayMs, int cause)> sendTransportServiceAsync) :
_thread(thread),
_enableP2P(enableP2P),
_rtcServers(rtcServers),
_transport(
	EncryptedConnection::Type::Transport,
	encryptionKey,
	[=](int delayMs, int cause) { sendTransportServiceAsync(delayMs, cause); }),
_isOutgoing(encryptionKey.isOutgoing),
_stateUpdated(std::move(stateUpdated)),
_transportMessageReceived(std::move(transportMessageReceived)),
_sendSignalingMessage(std::move(sendSignalingMessage)),
_localIceParameters(rtc::CreateRandomString(cricket::ICE_UFRAG_LENGTH), rtc::CreateRandomString(cricket::ICE_PWD_LENGTH)) {
	assert(_thread->IsCurrent());
}

NetworkManager::~NetworkManager() {
	assert(_thread->IsCurrent());
    
    RTC_LOG(LS_INFO) << "NetworkManager::~NetworkManager()";

	_transportChannel.reset();
	_asyncResolverFactory.reset();
	_portAllocator.reset();
	_networkManager.reset();
	_socketFactory.reset();
}

void NetworkManager::start() {
    _socketFactory.reset(new rtc::BasicPacketSocketFactory(_thread));

    _networkManager = std::make_unique<rtc::BasicNetworkManager>();
    _portAllocator.reset(new cricket::BasicPortAllocator(_networkManager.get(), _socketFactory.get(), nullptr, nullptr));

    uint32_t flags = cricket::PORTALLOCATOR_DISABLE_TCP;
    if (!_enableP2P) {
        flags |= cricket::PORTALLOCATOR_DISABLE_UDP;
        flags |= cricket::PORTALLOCATOR_DISABLE_STUN;
    }
    _portAllocator->set_flags(_portAllocator->flags() | flags);
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

    _portAllocator->SetConfiguration(stunServers, turnServers, 2, webrtc::NO_PRUNE);

    _asyncResolverFactory = std::make_unique<webrtc::BasicAsyncResolverFactory>();
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
        PeerIceParameters parameters(list->iceParameters.ufrag, list->iceParameters.pwd);
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
}

TrafficStats NetworkManager::getNetworkStats() {
    TrafficStats stats;
    stats.bytesSentWifi = _trafficStatsWifi.outgoing;
    stats.bytesReceivedWifi = _trafficStatsWifi.incoming;
    stats.bytesSentMobile = _trafficStatsCellular.outgoing;
    stats.bytesReceivedMobile = _trafficStatsCellular.incoming;
    return stats;
}

void NetworkManager::checkConnectionTimeout() {
    const auto weak = std::weak_ptr<NetworkManager>(shared_from_this());
    _thread->PostDelayedTask(RTC_FROM_HERE, [weak]() {
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
    }, 1000);
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

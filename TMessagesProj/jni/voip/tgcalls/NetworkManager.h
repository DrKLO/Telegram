#ifndef TGCALLS_NETWORK_MANAGER_H
#define TGCALLS_NETWORK_MANAGER_H

#include "rtc_base/thread.h"

#include "EncryptedConnection.h"
#include "Instance.h"
#include "Message.h"
#include "Stats.h"

#include "rtc_base/copy_on_write_buffer.h"
#include "api/candidate.h"
#include "rtc_base/network_monitor_factory.h"
#include "api/async_dns_resolver.h"

#include <functional>
#include <memory>

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
class RelayPortFactoryInterface;
} // namespace cricket

namespace webrtc {
class BasicAsyncResolverFactory;
class TurnCustomizer;
} // namespace webrtc

namespace tgcalls {

struct Message;

class NetworkManager : public sigslot::has_slots<>, public std::enable_shared_from_this<NetworkManager> {
public:
	struct State {
		bool isReadyToSendData = false;
        bool isFailed = false;
	};
    
    struct InterfaceTrafficStats {
        int64_t incoming = 0;
        int64_t outgoing = 0;
    };

	NetworkManager(
		rtc::Thread *thread,
		EncryptionKey encryptionKey,
		bool enableP2P,
        bool enableTCP,
        bool enableStunMarking,
		std::vector<RtcServer> const &rtcServers,
        std::unique_ptr<Proxy> proxy,
		std::function<void(const State &)> stateUpdated,
		std::function<void(DecryptedMessage &&)> transportMessageReceived,
		std::function<void(Message &&)> sendSignalingMessage,
		std::function<void(int delayMs, int cause)> sendTransportServiceAsync);
	~NetworkManager();

    void start();
	void receiveSignalingMessage(DecryptedMessage &&message);
	uint32_t sendMessage(const Message &message);
	void sendTransportService(int cause);
    void setIsLocalNetworkLowCost(bool isLocalNetworkLowCost);
    TrafficStats getNetworkStats();
    void fillCallStats(CallStats &callStats);
    void logCurrentNetworkState();

private:
    void checkConnectionTimeout();
	void candidateGathered(cricket::IceTransportInternal *transport, const cricket::Candidate &candidate);
	void candidateGatheringState(cricket::IceTransportInternal *transport);
	void transportStateChanged(cricket::IceTransportInternal *transport);
	void transportReadyToSend(cricket::IceTransportInternal *transport);
	void transportPacketReceived(rtc::PacketTransportInternal *transport, const char *bytes, size_t size, const int64_t &timestamp, int unused);
    void transportRouteChanged(absl::optional<rtc::NetworkRoute> route);
    void addTrafficStats(int64_t byteCount, bool isIncoming);

	rtc::Thread *_thread = nullptr;
    bool _enableP2P = false;
    bool _enableTCP = false;
    bool _enableStunMarking = false;
    std::vector<RtcServer> _rtcServers;
    std::unique_ptr<Proxy> _proxy;
	EncryptedConnection _transport;
	bool _isOutgoing = false;
	std::function<void(const NetworkManager::State &)> _stateUpdated;
	std::function<void(DecryptedMessage &&)> _transportMessageReceived;
	std::function<void(Message &&)> _sendSignalingMessage;

    std::unique_ptr<rtc::NetworkMonitorFactory> _networkMonitorFactory;
	std::unique_ptr<rtc::BasicPacketSocketFactory> _socketFactory;
	std::unique_ptr<rtc::BasicNetworkManager> _networkManager;
    std::unique_ptr<webrtc::TurnCustomizer> _turnCustomizer;
    std::unique_ptr<cricket::RelayPortFactoryInterface> _relayPortFactory;
	std::unique_ptr<cricket::BasicPortAllocator> _portAllocator;
	std::unique_ptr<webrtc::AsyncDnsResolverFactoryInterface> _asyncResolverFactory;
	std::unique_ptr<cricket::P2PTransportChannel> _transportChannel;

    PeerIceParameters _localIceParameters;
    absl::optional<PeerIceParameters> _remoteIceParameters;
    
    bool _isLocalNetworkLowCost = false;
    int64_t _lastNetworkActivityMs = 0;
    InterfaceTrafficStats _trafficStatsWifi;
    InterfaceTrafficStats _trafficStatsCellular;
    
    absl::optional<CallStatsConnectionEndpointType> _currentEndpointType;
    std::vector<CallStatsNetworkRecord> _networkRecords;
};

} // namespace tgcalls

#endif

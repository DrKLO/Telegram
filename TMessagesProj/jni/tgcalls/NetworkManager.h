#ifndef TGCALLS_NETWORK_MANAGER_H
#define TGCALLS_NETWORK_MANAGER_H

#include "rtc_base/thread.h"

#include "EncryptedConnection.h"
#include "Instance.h"
#include "Message.h"

#include "rtc_base/copy_on_write_buffer.h"
#include "api/candidate.h"

#include <functional>
#include <memory>

namespace rtc {
class BasicPacketSocketFactory;
class BasicNetworkManager;
class PacketTransportInternal;
class NetworkRoute;
} // namespace rtc

namespace cricket {
class BasicPortAllocator;
class P2PTransportChannel;
class IceTransportInternal;
} // namespace cricket

namespace webrtc {
class BasicAsyncResolverFactory;
} // namespace webrtc

namespace tgcalls {

struct Message;

class NetworkManager : public sigslot::has_slots<> {
public:
	struct State {
		bool isReadyToSendData = false;
	};

	NetworkManager(
		rtc::Thread *thread,
		EncryptionKey encryptionKey,
		bool enableP2P,
		std::vector<RtcServer> const &rtcServers,
		std::function<void(const State &)> stateUpdated,
		std::function<void(DecryptedMessage &&)> transportMessageReceived,
		std::function<void(Message &&)> sendSignalingMessage,
		std::function<void(int delayMs, int cause)> sendTransportServiceAsync);
	~NetworkManager();

	void receiveSignalingMessage(DecryptedMessage &&message);
	uint32_t sendMessage(const Message &message);
	void sendTransportService(int cause);

private:
	void candidateGathered(cricket::IceTransportInternal *transport, const cricket::Candidate &candidate);
	void candidateGatheringState(cricket::IceTransportInternal *transport);
	void transportStateChanged(cricket::IceTransportInternal *transport);
	void transportReadyToSend(cricket::IceTransportInternal *transport);
	void transportPacketReceived(rtc::PacketTransportInternal *transport, const char *bytes, size_t size, const int64_t &timestamp, int unused);
    void transportRouteChanged(absl::optional<rtc::NetworkRoute> route);

	rtc::Thread *_thread = nullptr;
	EncryptedConnection _transport;
	bool _isOutgoing = false;
	std::function<void(const NetworkManager::State &)> _stateUpdated;
	std::function<void(DecryptedMessage &&)> _transportMessageReceived;
	std::function<void(Message &&)> _sendSignalingMessage;

	std::unique_ptr<rtc::BasicPacketSocketFactory> _socketFactory;
	std::unique_ptr<rtc::BasicNetworkManager> _networkManager;
	std::unique_ptr<cricket::BasicPortAllocator> _portAllocator;
	std::unique_ptr<webrtc::BasicAsyncResolverFactory> _asyncResolverFactory;
	std::unique_ptr<cricket::P2PTransportChannel> _transportChannel;

    PeerIceParameters _localIceParameters;
    absl::optional<PeerIceParameters> _remoteIceParameters;
};

} // namespace tgcalls

#endif

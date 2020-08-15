#ifndef TGCALLS_MANAGER_H
#define TGCALLS_MANAGER_H

#include "ThreadLocalObject.h"
#include "EncryptedConnection.h"
#include "NetworkManager.h"
#include "MediaManager.h"
#include "Instance.h"

namespace tgcalls {

class Manager final : public std::enable_shared_from_this<Manager> {
public:
	static rtc::Thread *getMediaThread();

	Manager(rtc::Thread *thread, Descriptor &&descriptor);
	~Manager();

	void start();
	void receiveSignalingData(const std::vector<uint8_t> &data);
	void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture);
    void setMuteOutgoingAudio(bool mute);
	void setIncomingVideoOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink);
    void setIsLowBatteryLevel(bool isLowBatteryLevel);
    void setIsLocalNetworkLowCost(bool isLocalNetworkLowCost);
    void getNetworkStats(std::function<void(TrafficStats)> completion);
    
private:
	void sendSignalingAsync(int delayMs, int cause);
	void receiveMessage(DecryptedMessage &&message);
    bool calculateIsCurrentNetworkLowCost() const;
    void updateIsCurrentNetworkLowCost(bool wasLowCost);
    void sendInitialSignalingMessages();

	rtc::Thread *_thread;
	EncryptionKey _encryptionKey;
	EncryptedConnection _signaling;
	bool _enableP2P = false;
    ProtocolVersion _protocolVersion = ProtocolVersion::V0;
	std::vector<RtcServer> _rtcServers;
	std::shared_ptr<VideoCaptureInterface> _videoCapture;
	std::function<void(State)> _stateUpdated;
	std::function<void(AudioState, VideoState)> _remoteMediaStateUpdated;
    std::function<void(bool)> _remoteBatteryLevelIsLowUpdated;
    std::function<void(float)> _remotePrefferedAspectRatioUpdated;
	std::function<void(const std::vector<uint8_t> &)> _signalingDataEmitted;
    std::function<void(int)> _signalBarsUpdated;
	std::function<uint32_t(const Message &)> _sendSignalingMessage;
	std::function<void(Message&&)> _sendTransportMessage;
	std::unique_ptr<ThreadLocalObject<NetworkManager>> _networkManager;
	std::unique_ptr<ThreadLocalObject<MediaManager>> _mediaManager;
	State _state = State::Reconnecting;
    bool _didConnectOnce = false;
    float _localPreferredVideoAspectRatio = 0.0f;
    bool _enableHighBitrateVideo = false;
    std::vector<std::string> _preferredCodecs;
    bool _localNetworkIsLowCost = false;
    bool _remoteNetworkIsLowCost = false;

};

} // namespace tgcalls

#endif

#ifndef TGCALLS_MANAGER_H
#define TGCALLS_MANAGER_H

#include "ThreadLocalObject.h"
#include "EncryptedConnection.h"
#include "NetworkManager.h"
#include "MediaManager.h"
#include "Instance.h"
#include "Stats.h"

namespace tgcalls {

class Manager final : public std::enable_shared_from_this<Manager> {
private:
    struct ResolvedNetworkStatus {
        bool isLowCost = false;
        bool isLowDataRequested = false;

        bool operator==(const ResolvedNetworkStatus &rhs) const;
        bool operator!=(const ResolvedNetworkStatus &rhs) const;
    };

public:
	static rtc::Thread *getMediaThread();

	Manager(rtc::Thread *thread, Descriptor &&descriptor);
	~Manager();

	void start();
	void receiveSignalingData(const std::vector<uint8_t> &data);
	void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture);
    void sendVideoDeviceUpdated();
    void setRequestedVideoAspect(float aspect);
    void setMuteOutgoingAudio(bool mute);
	void setIncomingVideoOutput(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink);
    void setIsLowBatteryLevel(bool isLowBatteryLevel);
    void setIsLocalNetworkLowCost(bool isLocalNetworkLowCost);
    void getNetworkStats(std::function<void(TrafficStats, CallStats)> completion);


	void setAudioInputDevice(std::string id);
	void setAudioOutputDevice(std::string id);
	void setInputVolume(float level);
	void setOutputVolume(float level);

    void addExternalAudioSamples(std::vector<uint8_t> &&samples);

private:
	void sendSignalingAsync(int delayMs, int cause);
	void receiveMessage(DecryptedMessage &&message);
    void updateCurrentResolvedNetworkStatus();
    void sendInitialSignalingMessages();

	rtc::Thread *_thread;
	EncryptionKey _encryptionKey;
	EncryptedConnection _signaling;
	bool _enableP2P = false;
    bool _enableTCP = false;
    bool _enableStunMarking = false;
    ProtocolVersion _protocolVersion = ProtocolVersion::V0;
    FilePath _statsLogPath;
	std::vector<RtcServer> _rtcServers;
	std::unique_ptr<Proxy> _proxy;
	MediaDevicesConfig _mediaDevicesConfig;
	std::shared_ptr<VideoCaptureInterface> _videoCapture;
	std::function<void(State)> _stateUpdated;
	std::function<void(AudioState, VideoState)> _remoteMediaStateUpdated;
    std::function<void(bool)> _remoteBatteryLevelIsLowUpdated;
    std::function<void(float)> _remotePrefferedAspectRatioUpdated;
	std::function<void(const std::vector<uint8_t> &)> _signalingDataEmitted;
    std::function<void(int)> _signalBarsUpdated;
    std::function<void(float, float)> _audioLevelUpdated;
	std::function<rtc::scoped_refptr<webrtc::AudioDeviceModule>(webrtc::TaskQueueFactory*)> _createAudioDeviceModule;
	std::function<uint32_t(const Message &)> _sendSignalingMessage;
	std::function<void(Message&&)> _sendTransportMessage;
	std::unique_ptr<ThreadLocalObject<NetworkManager>> _networkManager;
	std::unique_ptr<ThreadLocalObject<MediaManager>> _mediaManager;
	State _state = State::Reconnecting;
    bool _didConnectOnce = false;
    bool _enableHighBitrateVideo = false;
    DataSaving _dataSaving = DataSaving::Never;
    std::vector<std::string> _preferredCodecs;
    bool _localNetworkIsLowCost = false;
    bool _remoteNetworkIsLowCost = false;
    bool _remoteIsLowDataRequested = false;
    absl::optional<ResolvedNetworkStatus> _currentResolvedLocalNetworkStatus;
    absl::optional<ResolvedNetworkStatus> _currentResolvedNetworkStatus;

	std::shared_ptr<PlatformContext> _platformContext;

};

} // namespace tgcalls

#endif

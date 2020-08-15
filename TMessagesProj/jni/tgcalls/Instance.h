#ifndef TGCALLS_INSTANCE_H
#define TGCALLS_INSTANCE_H

#include <functional>
#include <vector>
#include <string>
#include <memory>
#include <map>

namespace rtc {
template <typename VideoFrameT>
class VideoSinkInterface;
} // namespace rtc

namespace webrtc {
class VideoFrame;
} // namespace webrtc

namespace tgcalls {

class VideoCaptureInterface;

struct Proxy {
	std::string host;
	uint16_t port = 0;
	std::string login;
	std::string password;
};

struct RtcServer {
	std::string host;
	uint16_t port = 0;
	std::string login;
	std::string password;
	bool isTurn = false;
};

enum class EndpointType {
	Inet,
	Lan,
	UdpRelay,
	TcpRelay
};

struct EndpointHost {
	std::string ipv4;
	std::string ipv6;
};

struct Endpoint {
	int64_t endpointId = 0;
	EndpointHost host;
	uint16_t port = 0;
	EndpointType type = EndpointType{};
	unsigned char peerTag[16] = { 0 };
};

enum class ProtocolVersion {
    V0,
    V1 // Low-cost network negotiation
};

enum class NetworkType {
	Unknown,
	Gprs,
	Edge,
	ThirdGeneration,
	Hspa,
	Lte,
	WiFi,
	Ethernet,
	OtherHighSpeed,
	OtherLowSpeed,
	OtherMobile,
	Dialup
};

enum class DataSaving {
	Never,
	Mobile,
	Always
};

struct PersistentState {
	std::vector<uint8_t> value;
};

struct Config {
	double initializationTimeout = 0.;
	double receiveTimeout = 0.;
	DataSaving dataSaving = DataSaving::Never;
	bool enableP2P = false;
	bool enableAEC = false;
	bool enableNS = false;
	bool enableAGC = false;
	bool enableCallUpgrade = false;
	bool enableVolumeControl = false;
#ifndef _WIN32
	std::string logPath;
#else
	std::wstring logPath;
#endif
	int maxApiLayer = 0;
    float preferredAspectRatio;
    bool enableHighBitrateVideo = false;
    std::vector<std::string> preferredVideoCodecs;
    ProtocolVersion protocolVersion = ProtocolVersion::V0;
};

struct EncryptionKey {
	static constexpr int kSize = 256;

	std::shared_ptr<const std::array<uint8_t, kSize>> value;
	bool isOutgoing = false;

    EncryptionKey(
		std::shared_ptr<std::array<uint8_t, kSize>> value,
		bool isOutgoing)
	: value(std::move(value)), isOutgoing(isOutgoing) {
    }
};

enum class State {
	WaitInit,
	WaitInitAck,
	Established,
	Failed,
	Reconnecting
};

// Defined in VideoCaptureInterface.h
enum class VideoState;

enum class AudioState {
	Muted,
	Active,
};

struct TrafficStats {
	uint64_t bytesSentWifi = 0;
	uint64_t bytesReceivedWifi = 0;
	uint64_t bytesSentMobile = 0;
	uint64_t bytesReceivedMobile = 0;
};

struct FinalState {
	PersistentState persistentState;
	std::string debugLog;
	TrafficStats trafficStats;
	bool isRatingSuggested = false;
};

class Instance {
protected:
	Instance() = default;

public:
	virtual ~Instance() = default;

	virtual void setNetworkType(NetworkType networkType) = 0;
	virtual void setMuteMicrophone(bool muteMicrophone) = 0;
	virtual void setAudioOutputGainControlEnabled(bool enabled) = 0;
	virtual void setEchoCancellationStrength(int strength) = 0;

	virtual void setIncomingVideoOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) = 0;

	virtual void setAudioInputDevice(std::string id) = 0;
	virtual void setAudioOutputDevice(std::string id) = 0;
	virtual void setInputVolume(float level) = 0;
	virtual void setOutputVolume(float level) = 0;
	virtual void setAudioOutputDuckingEnabled(bool enabled) = 0;
    
    virtual void setIsLowBatteryLevel(bool isLowBatteryLevel) = 0;

	virtual std::string getLastError() = 0;
	virtual std::string getDebugInfo() = 0;
	virtual int64_t getPreferredRelayId() = 0;
	virtual TrafficStats getTrafficStats() = 0;
	virtual PersistentState getPersistentState() = 0;

	virtual void receiveSignalingData(const std::vector<uint8_t> &data) = 0;
	virtual void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) = 0;

	virtual void stop(std::function<void(FinalState)> completion) = 0;

};

template <typename Implementation>
bool Register();

struct Descriptor {
	Config config;
	PersistentState persistentState;
	std::vector<Endpoint> endpoints;
	std::unique_ptr<Proxy> proxy;
	std::vector<RtcServer> rtcServers;
	NetworkType initialNetworkType = NetworkType();
	EncryptionKey encryptionKey;
	std::shared_ptr<VideoCaptureInterface> videoCapture;
	std::function<void(State)> stateUpdated;
	std::function<void(int)> signalBarsUpdated;
    std::function<void(bool)> remoteBatteryLevelIsLowUpdated;
	std::function<void(AudioState, VideoState)> remoteMediaStateUpdated;
    std::function<void(float)> remotePrefferedAspectRatioUpdated;
	std::function<void(const std::vector<uint8_t> &)> signalingDataEmitted;
};

class Meta {
public:
	virtual ~Meta() = default;

	virtual std::unique_ptr<Instance> construct(Descriptor &&descriptor) = 0;
	virtual int connectionMaxLayer() = 0;
	virtual std::vector<std::string> versions() = 0;

	static std::unique_ptr<Instance> Create(
		const std::string &version,
		Descriptor &&descriptor);
	static std::vector<std::string> Versions();
	static int MaxLayer();

private:
	template <typename Implementation>
	friend bool Register();

	template <typename Implementation>
	static bool RegisterOne();
	static void RegisterOne(std::shared_ptr<Meta> meta);

};

template <typename Implementation>
bool Meta::RegisterOne() {
	class MetaImpl final : public Meta {
	public:
		int connectionMaxLayer() override {
			return Implementation::GetConnectionMaxLayer();
		}
		std::vector<std::string> versions() override {
			return Implementation::GetVersions();
		}
		std::unique_ptr<Instance> construct(Descriptor &&descriptor) override {
			return std::make_unique<Implementation>(std::move(descriptor));
		}
	};
	RegisterOne(std::make_shared<MetaImpl>());
	return true;
}

void SetLoggingFunction(std::function<void(std::string const &)> loggingFunction);

} // namespace tgcalls

#endif

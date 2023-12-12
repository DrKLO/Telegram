#ifndef TGCALLS_INSTANCEV2_4_0_0_IMPL_H
#define TGCALLS_INSTANCEV2_4_0_0_IMPL_H

#include "Instance.h"
#include "StaticThreads.h"

namespace tgcalls {

class LogSinkImpl;

class Manager;
template <typename T>
class ThreadLocalObject;

class InstanceV2_4_0_0ImplInternal;

class InstanceV2_4_0_0Impl final : public Instance {
public:
	explicit InstanceV2_4_0_0Impl(Descriptor &&descriptor);
	~InstanceV2_4_0_0Impl() override;

	void receiveSignalingData(const std::vector<uint8_t> &data) override;
	void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) override;
    void setRequestedVideoAspect(float aspect) override;
	void setNetworkType(NetworkType networkType) override;
	void setMuteMicrophone(bool muteMicrophone) override;
	bool supportsVideo() override {
		return true;
	}
	void setIncomingVideoOutput(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) override;
	void setAudioOutputGainControlEnabled(bool enabled) override;
	void setEchoCancellationStrength(int strength) override;
	void setAudioInputDevice(std::string id) override;
	void setAudioOutputDevice(std::string id) override;
	void setInputVolume(float level) override;
	void setOutputVolume(float level) override;
	void setAudioOutputDuckingEnabled(bool enabled) override;
    void setIsLowBatteryLevel(bool isLowBatteryLevel) override;
    static std::vector<std::string> GetVersions();
    static int GetConnectionMaxLayer();
	std::string getLastError() override;
	std::string getDebugInfo() override;
	int64_t getPreferredRelayId() override;
	TrafficStats getTrafficStats() override;
	PersistentState getPersistentState() override;
	void stop(std::function<void(FinalState)> completion) override;
    void sendVideoDeviceUpdated() override {
    }

private:
    std::shared_ptr<Threads> _threads;
	std::unique_ptr<ThreadLocalObject<InstanceV2_4_0_0ImplInternal>> _internal;
	std::unique_ptr<LogSinkImpl> _logSink;

};

} // namespace tgcalls

#endif

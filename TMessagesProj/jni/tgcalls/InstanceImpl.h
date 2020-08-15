#ifndef TGCALLS_INSTANCE_IMPL_H
#define TGCALLS_INSTANCE_IMPL_H

#include "Instance.h"

namespace tgcalls {

class LogSinkImpl;

class Manager;
template <typename T>
class ThreadLocalObject;

class InstanceImpl final : public Instance {
public:
	explicit InstanceImpl(Descriptor &&descriptor);
	~InstanceImpl() override;

	static int GetConnectionMaxLayer();
	static std::vector<std::string> GetVersions();

	void receiveSignalingData(const std::vector<uint8_t> &data) override;
	void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) override;
	void setNetworkType(NetworkType networkType) override;
	void setMuteMicrophone(bool muteMicrophone) override;
	void setIncomingVideoOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) override;
	void setAudioOutputGainControlEnabled(bool enabled) override;
	void setEchoCancellationStrength(int strength) override;
	void setAudioInputDevice(std::string id) override;
	void setAudioOutputDevice(std::string id) override;
	void setInputVolume(float level) override;
	void setOutputVolume(float level) override;
	void setAudioOutputDuckingEnabled(bool enabled) override;
    void setIsLowBatteryLevel(bool isLowBatteryLevel) override;
	std::string getLastError() override;
	std::string getDebugInfo() override;
	int64_t getPreferredRelayId() override;
	TrafficStats getTrafficStats() override;
	PersistentState getPersistentState() override;
	void stop(std::function<void(FinalState)> completion) override;
	//void controllerStateCallback(Controller::State state);

private:
	std::unique_ptr<ThreadLocalObject<Manager>> _manager;
	std::unique_ptr<LogSinkImpl> _logSink;

};

} // namespace tgcalls

#endif

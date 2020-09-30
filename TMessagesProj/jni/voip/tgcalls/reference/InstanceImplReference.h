#ifndef TGCALLS_INSTANCE_IMPL_REFERENCE_H
#define TGCALLS_INSTANCE_IMPL_REFERENCE_H

#include "Instance.h"
#include "ThreadLocalObject.h"

namespace tgcalls {

class LogSinkImpl;
class InstanceImplReferenceInternal;

class InstanceImplReference : public Instance {
public:
	explicit InstanceImplReference(Descriptor &&descriptor);
	~InstanceImplReference();

	void receiveSignalingData(const std::vector<uint8_t> &data) override;
	void setNetworkType(NetworkType networkType) override;
	void setMuteMicrophone(bool muteMicrophone) override;
	void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) override;
	void setIncomingVideoOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) override;
	void setAudioOutputGainControlEnabled(bool enabled) override;
	void setEchoCancellationStrength(int strength) override;
	void setAudioInputDevice(std::string id) override;
	void setAudioOutputDevice(std::string id) override;
	void setInputVolume(float level) override;
	void setOutputVolume(float level) override;
	void setAudioOutputDuckingEnabled(bool enabled) override;
    void setIsLowBatteryLevel(bool isLowBatteryLevel) override;
    static int GetConnectionMaxLayer();
    static std::vector<std::string> GetVersions();
	std::string getLastError() override;
	std::string getDebugInfo() override;
	int64_t getPreferredRelayId() override;
	TrafficStats getTrafficStats() override;
	PersistentState getPersistentState() override;
	void stop(std::function<void(FinalState)> completion) override;

private:
    std::unique_ptr<LogSinkImpl> logSink_;
    std::unique_ptr<ThreadLocalObject<InstanceImplReferenceInternal>> internal_;

};

} // namespace tgcalls

#endif

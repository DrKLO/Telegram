#include "InstanceImpl.h"

#include "LogSinkImpl.h"
#include "Manager.h"
#include "MediaManager.h"
#include "VideoCaptureInterfaceImpl.h"
#include "VideoCapturerInterface.h"

namespace tgcalls {
namespace {

rtc::Thread *makeManagerThread() {
	static std::unique_ptr<rtc::Thread> value = rtc::Thread::Create();
	value->SetName("WebRTC-Manager", nullptr);
	value->Start();
	return value.get();
}


rtc::Thread *getManagerThread() {
	static rtc::Thread *value = makeManagerThread();
	return value;
}

} // namespace

InstanceImpl::InstanceImpl(Descriptor &&descriptor)
: _logSink(std::make_unique<LogSinkImpl>(descriptor.config)) {
    rtc::LogMessage::LogToDebug(rtc::LS_INFO);
    rtc::LogMessage::SetLogToStderr(false);
	rtc::LogMessage::AddLogToStream(_logSink.get(), rtc::LS_INFO);
    
    auto networkType = descriptor.initialNetworkType;

	_manager.reset(new ThreadLocalObject<Manager>(getManagerThread(), [descriptor = std::move(descriptor)]() mutable {
		return new Manager(getManagerThread(), std::move(descriptor));
	}));
	_manager->perform(RTC_FROM_HERE, [](Manager *manager) {
		manager->start();
	});
    
    setNetworkType(networkType);
}

InstanceImpl::~InstanceImpl() {
	rtc::LogMessage::RemoveLogToStream(_logSink.get());
}

void InstanceImpl::receiveSignalingData(const std::vector<uint8_t> &data) {
	_manager->perform(RTC_FROM_HERE, [data](Manager *manager) {
		manager->receiveSignalingData(data);
	});
};

void InstanceImpl::setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) {
    _manager->perform(RTC_FROM_HERE, [videoCapture](Manager *manager) {
        manager->setVideoCapture(videoCapture);
    });
}

void InstanceImpl::setNetworkType(NetworkType networkType) {
    bool isLowCostNetwork = false;
    switch (networkType) {
        case NetworkType::WiFi:
        case NetworkType::Ethernet:
            isLowCostNetwork = true;
            break;
        default:
            break;
    }
    
    _manager->perform(RTC_FROM_HERE, [isLowCostNetwork](Manager *manager) {
        manager->setIsLocalNetworkLowCost(isLowCostNetwork);
    });
}

void InstanceImpl::setMuteMicrophone(bool muteMicrophone) {
	_manager->perform(RTC_FROM_HERE, [muteMicrophone](Manager *manager) {
		manager->setMuteOutgoingAudio(muteMicrophone);
	});
}

void InstanceImpl::setIncomingVideoOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
	_manager->perform(RTC_FROM_HERE, [sink](Manager *manager) {
		manager->setIncomingVideoOutput(sink);
	});
}

void InstanceImpl::setAudioOutputGainControlEnabled(bool enabled) {
}

void InstanceImpl::setEchoCancellationStrength(int strength) {
}

void InstanceImpl::setAudioInputDevice(std::string id) {
	// TODO: not implemented
}

void InstanceImpl::setAudioOutputDevice(std::string id) {
	// TODO: not implemented
}

void InstanceImpl::setInputVolume(float level) {
	// TODO: not implemented
}

void InstanceImpl::setOutputVolume(float level) {
	// TODO: not implemented
}

void InstanceImpl::setAudioOutputDuckingEnabled(bool enabled) {
	// TODO: not implemented
}

void InstanceImpl::setIsLowBatteryLevel(bool isLowBatteryLevel) {
    _manager->perform(RTC_FROM_HERE, [isLowBatteryLevel](Manager *manager) {
        manager->setIsLowBatteryLevel(isLowBatteryLevel);
    });
}

std::string InstanceImpl::getLastError() {
	return "";  // TODO: not implemented
}

std::string InstanceImpl::getDebugInfo() {
	return "";  // TODO: not implemented
}

int64_t InstanceImpl::getPreferredRelayId() {
	return 0;  // we don't have endpoint ids
}

TrafficStats InstanceImpl::getTrafficStats() {
	return TrafficStats{};  // TODO: not implemented
}

PersistentState InstanceImpl::getPersistentState() {
	return PersistentState{};  // we dont't have such information
}

void InstanceImpl::stop(std::function<void(FinalState)> completion) {
    std::string debugLog = _logSink->result();
    
    _manager->perform(RTC_FROM_HERE, [completion, debugLog = std::move(debugLog)](Manager *manager) {
        manager->getNetworkStats([completion, debugLog = std::move(debugLog)](TrafficStats stats) {
            FinalState finalState;
            finalState.debugLog = debugLog;
            finalState.isRatingSuggested = false;
            finalState.trafficStats = stats;
            
            completion(finalState);
        });
    });
}

/*void InstanceImpl::controllerStateCallback(Controller::State state) {
	if (onStateUpdated_) {
		const auto mappedState = [&] {
			switch (state) {
			case Controller::State::WaitInit:
				return State::WaitInit;
			case Controller::State::WaitInitAck:
				return State::WaitInitAck;
			case Controller::State::Established:
				return State::Estabilished;
			case Controller::State::Failed:
				return State::Failed;
			case Controller::State::Reconnecting:
				return State::Reconnecting;
			default:
				return State::Estabilished;
			}
		}();

		onStateUpdated_(mappedState);
	}
}*/

int InstanceImpl::GetConnectionMaxLayer() {
	return 92;  // TODO: retrieve from LayerBase
}

std::vector<std::string> InstanceImpl::GetVersions() {
    std::vector<std::string> result;
    result.push_back("2.7.7");
    result.push_back("3.0.0");
    return result;
}

template <>
bool Register<InstanceImpl>() {
	return Meta::RegisterOne<InstanceImpl>();
}

} // namespace tgcalls

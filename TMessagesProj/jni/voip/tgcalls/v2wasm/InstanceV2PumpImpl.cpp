#include "v2wasm/InstanceV2PumpImpl.h"

#include "LogSinkImpl.h"
#include "ThreadLocalObject.h"
#include "v2wasm/CallCoreHost.h"

namespace tgcalls {

InstanceV2PumpImpl::InstanceV2PumpImpl(Descriptor &&descriptor) {
    if (descriptor.config.logPath.data.size() != 0) {
        _logSink = std::make_unique<LogSinkImpl>(descriptor.config.logPath);
    }
    rtc::LogMessage::LogToDebug(rtc::LS_INFO);
    rtc::LogMessage::SetLogToStderr(false);
    if (_logSink) {
        rtc::LogMessage::AddLogToStream(_logSink.get(), rtc::LS_INFO);
    }

    _threads = StaticThreads::getThreads();
    _internal.reset(new ThreadLocalObject<CallCoreHost>(_threads->getMediaThread(), [descriptor = std::move(descriptor), threads = _threads]() mutable {
        return std::make_shared<CallCoreHost>(std::move(descriptor), threads);
    }));
    _internal->perform([](CallCoreHost *internal) {
        internal->start();
    });
}

InstanceV2PumpImpl::~InstanceV2PumpImpl() {
    rtc::LogMessage::RemoveLogToStream(_logSink.get());
}

void InstanceV2PumpImpl::receiveSignalingData(const std::vector<uint8_t> &data) {
    _internal->perform([data](CallCoreHost *internal) {
        internal->receiveSignalingData(data);
    });
}

void InstanceV2PumpImpl::setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) {
    _internal->perform([videoCapture](CallCoreHost *internal) {
        internal->setVideoCapture(videoCapture);
    });
}

void InstanceV2PumpImpl::setRequestedVideoAspect(float aspect) {
}

void InstanceV2PumpImpl::setNetworkType(NetworkType networkType) {
}

void InstanceV2PumpImpl::setMuteMicrophone(bool muteMicrophone) {
    _internal->perform([muteMicrophone](CallCoreHost *internal) {
        internal->setMuteMicrophone(muteMicrophone);
    });
}

void InstanceV2PumpImpl::setIncomingVideoOutput(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    _internal->perform([sink](CallCoreHost *internal) {
        internal->setIncomingVideoOutput(sink);
    });
}

void InstanceV2PumpImpl::setAudioInputDevice(std::string id) {
    _internal->perform([id](CallCoreHost *internal) {
        internal->setAudioInputDevice(id);
    });
}

void InstanceV2PumpImpl::setAudioOutputDevice(std::string id) {
    _internal->perform([id](CallCoreHost *internal) {
        internal->setAudioOutputDevice(id);
    });
}

void InstanceV2PumpImpl::setIsLowBatteryLevel(bool isLowBatteryLevel) {
    _internal->perform([isLowBatteryLevel](CallCoreHost *internal) {
        internal->setIsLowBatteryLevel(isLowBatteryLevel);
    });
}

void InstanceV2PumpImpl::setInputVolume(float level) {
}

void InstanceV2PumpImpl::setOutputVolume(float level) {
}

void InstanceV2PumpImpl::setAudioOutputDuckingEnabled(bool enabled) {
}

void InstanceV2PumpImpl::setAudioOutputGainControlEnabled(bool enabled) {
}

void InstanceV2PumpImpl::setEchoCancellationStrength(int strength) {
}

std::vector<std::string> InstanceV2PumpImpl::GetVersions() {
    std::vector<std::string> result;
    // Same class, same harness, same core source for both: the ONLY
    // difference is which backend CallCoreHost constructs (see
    // versionUsesWasmCore). That is what makes an 18-vs-19 A/B attributable
    // to the substrate and nothing else.
    result.push_back("18.0.0");
    result.push_back("19.0.0");
    return result;
}

int InstanceV2PumpImpl::GetConnectionMaxLayer() {
    return 92;
}

std::string InstanceV2PumpImpl::getLastError() {
    return "";
}

std::string InstanceV2PumpImpl::getDebugInfo() {
    return "";
}

int64_t InstanceV2PumpImpl::getPreferredRelayId() {
    return 0;
}

TrafficStats InstanceV2PumpImpl::getTrafficStats() {
    return {};
}

PersistentState InstanceV2PumpImpl::getPersistentState() {
    return {};
}

void InstanceV2PumpImpl::stop(std::function<void(FinalState)> completion) {
    std::string debugLog;
    if (_logSink) {
        debugLog = _logSink->result();
    }
    _internal->perform([completion, debugLog = std::move(debugLog)](CallCoreHost *internal) mutable {
        internal->stop([completion, debugLog = std::move(debugLog)](FinalState finalState) mutable {
            finalState.debugLog = debugLog;
            completion(finalState);
        });
    });
}

template <>
bool Register<InstanceV2PumpImpl>() {
    return Meta::RegisterOne<InstanceV2PumpImpl>();
}

} // namespace tgcalls

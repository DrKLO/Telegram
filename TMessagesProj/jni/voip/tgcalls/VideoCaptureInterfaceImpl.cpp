#include <tgnet/FileLog.h>
#include "VideoCaptureInterfaceImpl.h"

#include "VideoCapturerInterface.h"
#include "Manager.h"
#include "MediaManager.h"
#include "platform/PlatformInterface.h"
#include "StaticThreads.h"

namespace tgcalls {

VideoCaptureInterfaceObject::VideoCaptureInterfaceObject(std::string deviceId, std::shared_ptr<PlatformContext> platformContext, Threads &threads)
: _videoSource(PlatformInterface::SharedInstance()->makeVideoSource(threads.getMediaThread(), threads.getWorkerThread())) {
	_platformContext = platformContext;
    
	switchToDevice(deviceId);
}

VideoCaptureInterfaceObject::~VideoCaptureInterfaceObject() {
	if (_videoCapturer && _currentUncroppedSink != nullptr) {
		_videoCapturer->setUncroppedOutput(nullptr);
	}
}

webrtc::VideoTrackSourceInterface *VideoCaptureInterfaceObject::source() {
	return _videoSource;
}

void VideoCaptureInterfaceObject::switchToDevice(std::string deviceId) {
    if (_videoCapturer && _currentUncroppedSink) {
		_videoCapturer->setUncroppedOutput(nullptr);
    }
	if (_videoSource) {
        //this should outlive the capturer
        _videoCapturer = NULL;
		_videoCapturer = PlatformInterface::SharedInstance()->makeVideoCapturer(_videoSource, deviceId, [this](VideoState state) {
			if (this->_stateUpdated) {
				this->_stateUpdated(state);
			}
        }, [this](PlatformCaptureInfo info) {
            if (this->_shouldBeAdaptedToReceiverAspectRate != info.shouldBeAdaptedToReceiverAspectRate) {
                this->_shouldBeAdaptedToReceiverAspectRate = info.shouldBeAdaptedToReceiverAspectRate;
                this->updateAspectRateAdaptation();
            }
        }, _platformContext, _videoCapturerResolution);
	}
	if (_videoCapturer) {
//        if (_preferredAspectRatio > 0) {
//            _videoCapturer->setPreferredCaptureAspectRatio(_preferredAspectRatio);
//        }
		if (_currentUncroppedSink) {
			_videoCapturer->setUncroppedOutput(_currentUncroppedSink);
		}
		_videoCapturer->setState(_state);
	}
}

void VideoCaptureInterfaceObject::setState(VideoState state) {
	if (_state != state) {
		_state = state;
		if (_videoCapturer) {
			_videoCapturer->setState(state);
		}
	}
}

void VideoCaptureInterfaceObject::setPreferredAspectRatio(float aspectRatio) {
    _preferredAspectRatio = aspectRatio;
    updateAspectRateAdaptation();
}

void VideoCaptureInterfaceObject::updateAspectRateAdaptation() {
    if (_videoCapturer) {
        if (_videoCapturerResolution.first != 0 && _videoCapturerResolution.second != 0) {
            if (_preferredAspectRatio > 0.01 && _shouldBeAdaptedToReceiverAspectRate) {
                float originalWidth = (float)_videoCapturerResolution.first;
                float originalHeight = (float)_videoCapturerResolution.second;
                
                float aspectRatio = _preferredAspectRatio;
                
                float width = (originalWidth > aspectRatio * originalHeight)
                    ? int(std::round(aspectRatio * originalHeight))
                    : originalWidth;
                float height = (originalWidth > aspectRatio * originalHeight)
                    ? originalHeight
                    : int(std::round(originalHeight / aspectRatio));
                
                PlatformInterface::SharedInstance()->adaptVideoSource(_videoSource, (int)width, (int)height, 30);
            } else {
                PlatformInterface::SharedInstance()->adaptVideoSource(_videoSource, _videoCapturerResolution.first, _videoCapturerResolution.second, 30);
            }
        }
    }
}

void VideoCaptureInterfaceObject::setOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
	if (_videoCapturer) {
		_videoCapturer->setUncroppedOutput(sink);
	}
	_currentUncroppedSink = sink;
}

void VideoCaptureInterfaceObject::setStateUpdated(std::function<void(VideoState)> stateUpdated) {
	_stateUpdated = stateUpdated;
}

VideoCaptureInterfaceImpl::VideoCaptureInterfaceImpl(std::string deviceId,
   std::shared_ptr<PlatformContext> platformContext, std::shared_ptr<Threads> threads) :
_platformContext(platformContext),
_impl(threads->getMediaThread(), [deviceId, platformContext, threads]() {
	return new VideoCaptureInterfaceObject(deviceId, platformContext, *threads);
}) {
}

VideoCaptureInterfaceImpl::~VideoCaptureInterfaceImpl() = default;

void VideoCaptureInterfaceImpl::switchToDevice(std::string deviceId) {
	_impl.perform(RTC_FROM_HERE, [deviceId](VideoCaptureInterfaceObject *impl) {
		impl->switchToDevice(deviceId);
	});
}

void VideoCaptureInterfaceImpl::setState(VideoState state) {
	_impl.perform(RTC_FROM_HERE, [state](VideoCaptureInterfaceObject *impl) {
		impl->setState(state);
	});
}

void VideoCaptureInterfaceImpl::setPreferredAspectRatio(float aspectRatio) {
    _impl.perform(RTC_FROM_HERE, [aspectRatio](VideoCaptureInterfaceObject *impl) {
        impl->setPreferredAspectRatio(aspectRatio);
    });
}

void VideoCaptureInterfaceImpl::setOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
	_impl.perform(RTC_FROM_HERE, [sink](VideoCaptureInterfaceObject *impl) {
		impl->setOutput(sink);
	});
}

std::shared_ptr<PlatformContext> VideoCaptureInterfaceImpl::getPlatformContext() {
	return _platformContext;
}

ThreadLocalObject<VideoCaptureInterfaceObject> *VideoCaptureInterfaceImpl::object() {
	return &_impl;
}

} // namespace tgcalls

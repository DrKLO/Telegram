#include "VideoCaptureInterfaceImpl.h"

#include "VideoCapturerInterface.h"
#include "Manager.h"
#include "MediaManager.h"
#include "platform/PlatformInterface.h"

namespace tgcalls {

VideoCaptureInterfaceObject::VideoCaptureInterfaceObject(std::shared_ptr<PlatformContext> platformContext) {
	_videoSource = PlatformInterface::SharedInstance()->makeVideoSource(Manager::getMediaThread(), MediaManager::getWorkerThread());
	_platformContext = platformContext;
	//this should outlive the capturer
	if (_videoSource) {
		_videoCapturer = PlatformInterface::SharedInstance()->makeVideoCapturer(_videoSource, _useFrontCamera, [this](VideoState state) {
			if (this->_stateUpdated) {
				this->_stateUpdated(state);
			}
		}, platformContext, _videoCapturerResolution);
	}
}

VideoCaptureInterfaceObject::~VideoCaptureInterfaceObject() {
	if (_videoCapturer && _currentUncroppedSink != nullptr) {
		//_videoSource->RemoveSink(_currentSink.get());
		_videoCapturer->setUncroppedOutput(nullptr);
	}
}

void VideoCaptureInterfaceObject::switchCamera() {
	_useFrontCamera = !_useFrontCamera;
    if (_videoCapturer && _currentUncroppedSink) {
		_videoCapturer->setUncroppedOutput(nullptr);
    }
	if (_videoSource) {
		_videoCapturer = PlatformInterface::SharedInstance()->makeVideoCapturer(_videoSource, _useFrontCamera, [this](VideoState state) {
			if (this->_stateUpdated) {
				this->_stateUpdated(state);
			}
		}, _platformContext, _videoCapturerResolution);
	}
	if (_videoCapturer) {
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
	if (_videoCapturer) {
        if (aspectRatio > 0.01 && _videoCapturerResolution.first != 0 && _videoCapturerResolution.second != 0) {
            float originalWidth = (float)_videoCapturerResolution.first;
            float originalHeight = (float)_videoCapturerResolution.second;
            
            float width = (originalWidth > aspectRatio * originalHeight)
                ? int(std::round(aspectRatio * originalHeight))
                : originalWidth;
            float height = (originalWidth > aspectRatio * originalHeight)
                ? originalHeight
                : int(std::round(originalHeight / aspectRatio));
            
            PlatformInterface::SharedInstance()->adaptVideoSource(_videoSource, (int)width, (int)height, 30);
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

VideoCaptureInterfaceImpl::VideoCaptureInterfaceImpl(std::shared_ptr<PlatformContext> platformContext) :
_impl(Manager::getMediaThread(), [platformContext]() {
	return new VideoCaptureInterfaceObject(platformContext);
}) {
}

VideoCaptureInterfaceImpl::~VideoCaptureInterfaceImpl() = default;

void VideoCaptureInterfaceImpl::switchCamera() {
	_impl.perform(RTC_FROM_HERE, [](VideoCaptureInterfaceObject *impl) {
		impl->switchCamera();
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

ThreadLocalObject<VideoCaptureInterfaceObject> *VideoCaptureInterfaceImpl::object() {
	return &_impl;
}

} // namespace tgcalls

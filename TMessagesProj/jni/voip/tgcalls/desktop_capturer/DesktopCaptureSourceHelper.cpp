//
//  DesktopCaptureSourceHelper.m
//  TgVoipWebrtc
//
//  Created by Mikhail Filimonov on 28.12.2020.
//  Copyright © 2020 Mikhail Filimonov. All rights reserved.
//

#include "tgcalls/desktop_capturer/DesktopCaptureSourceHelper.h"

#include <iostream>
#include <memory>
#include <algorithm>
#include <chrono>
#include <iostream>
#include <vector>
#include <functional>

#include "tgcalls/desktop_capturer/DesktopCaptureSourceManager.h"
#include "rtc_base/thread.h"
#include "api/video/video_sink_interface.h"
#include "api/video/video_frame.h"
#include "modules/desktop_capture/desktop_and_cursor_composer.h"
#include "modules/desktop_capture/desktop_capturer.h"
#include "system_wrappers/include/clock.h"
#include "api/video/i420_buffer.h"
#include "third_party/libyuv/include/libyuv.h"

#ifdef WEBRTC_MAC
#import <QuartzCore/QuartzCore.h>
#endif // WEBRTC_MAC

namespace tgcalls {
namespace {

#ifdef WEBRTC_MAC
class CaptureScheduler {
public:
    void runAsync(std::function<void()> method) {
		dispatch_async(dispatch_get_main_queue(), ^{
			method();
		});
    }
    void runDelayed(int delayMs, std::function<void()> method) {
        const auto time = dispatch_time(
            DISPATCH_TIME_NOW,
            ((long long)delayMs * NSEC_PER_SEC) / 1000);
        dispatch_after(time, dispatch_get_main_queue(), ^{
            method();
        });
    }
};
#else // WEBRTC_MAC
rtc::Thread *GlobalCapturerThread() {
    static auto result = [] {
        auto thread = rtc::Thread::Create();
        thread->SetName("WebRTC-DesktopCapturer", nullptr);
        thread->Start();
        return thread;
    }();
    return result.get();
}

class CaptureScheduler {
public:
    CaptureScheduler() : _thread(GlobalCapturerThread()) {
    }

    void runAsync(std::function<void()> method) {
        _thread->PostTask(std::move(method));
    }
    void runDelayed(int delayMs, std::function<void()> method) {
        _thread->PostDelayedTask(std::move(method), webrtc::TimeDelta::Millis(delayMs));
    }

private:
    rtc::Thread *_thread;

};
#endif // WEBRTC_MAC

class SourceFrameCallbackImpl : public webrtc::DesktopCapturer::Callback {
public:
    SourceFrameCallbackImpl(DesktopSize size, int fps);

    void OnCaptureResult(
        webrtc::DesktopCapturer::Result result,
        std::unique_ptr<webrtc::DesktopFrame> frame) override;
	void setOutput(
		std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink);
	void setSecondaryOutput(
		std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink);
    void setOnFatalError(std::function<void ()>);
    void setOnPause(std::function<void (bool)>);
private:
    rtc::scoped_refptr<webrtc::I420Buffer> i420_buffer_;
	std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> _sink;
	std::shared_ptr<
        rtc::VideoSinkInterface<webrtc::VideoFrame>> _secondarySink;
    DesktopSize size_;
    std::function<void ()> _onFatalError;
    std::function<void (bool)> _onPause;
};

class DesktopSourceRenderer {
public:
    DesktopSourceRenderer(
        CaptureScheduler &scheduler,
        DesktopCaptureSource source,
        DesktopCaptureSourceData data);

    void start();
    void stop();
    void setOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink);
    void setSecondaryOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink);
    void loop();
    void setOnFatalError(std::function<void ()>);
    void setOnPause(std::function<void (bool)>);
private:
    CaptureScheduler &_scheduler;
    std::unique_ptr<webrtc::DesktopCapturer> _capturer;
    SourceFrameCallbackImpl _callback;
    std::shared_ptr<bool> _timerGuard;
    std::function<void()> _onFatalError;
    std::function<void(bool)> _onPause;
    bool _isRunning = false;
    bool _fatalError = false;
    bool _currentlyOnPause = false;
    double _delayMs = 0.;

};

SourceFrameCallbackImpl::SourceFrameCallbackImpl(DesktopSize size, int fps)
: size_(size) {
}

void SourceFrameCallbackImpl::OnCaptureResult(
	    webrtc::DesktopCapturer::Result result,
	    std::unique_ptr<webrtc::DesktopFrame> frame) {

    const auto failed = (result != webrtc::DesktopCapturer::Result::SUCCESS)
        || !frame
        || frame->size().equals({ 1, 1 });
    if (failed) {
        if (result == webrtc::DesktopCapturer::Result::ERROR_PERMANENT) {
            if (_onFatalError) {
                _onFatalError();
            }
        } else if (_onPause) {
            _onPause(true);
        }
        return;
    } else if (_onPause) {
        _onPause(false);
    }

    const auto frameSize = frame->size();
    auto fittedSize = (frameSize.width() >= size_.width * 2
        || frameSize.height() >= size_.height * 2)
        ? DesktopSize{ frameSize.width() / 2, frameSize.height() / 2 }
        : DesktopSize{ frameSize.width(), frameSize.height() };

    fittedSize.width -= (fittedSize.width % 4);
    fittedSize.height -= (fittedSize.height % 4);

    const auto outputSize = webrtc::DesktopSize{
        fittedSize.width,
        fittedSize.height
    };

    webrtc::BasicDesktopFrame outputFrame{ outputSize };

	const auto outputRect = webrtc::DesktopRect::MakeSize(outputSize);

	const auto outputRectData = outputFrame.data() +
        outputFrame.stride() * outputRect.top() +
		webrtc::DesktopFrame::kBytesPerPixel * outputRect.left();


	libyuv::ARGBScale(
		frame->data(),
		frame->stride(),
		frame->size().width(),
		frame->size().height(),
        outputRectData,
        outputFrame.stride(),
        outputSize.width(),
        outputSize.height(),
		libyuv::kFilterBilinear);

	int width = outputFrame.size().width();
	int height = outputFrame.size().height();
	int stride_y = width;
	int stride_uv = (width + 1) / 2;

	if (!i420_buffer_
        || i420_buffer_->width() != width
        || i420_buffer_->height() != height) {
		i420_buffer_ = webrtc::I420Buffer::Create(
            width,
            height,
            stride_y,
            stride_uv,
            stride_uv);
	}

	int i420Result = libyuv::ConvertToI420(
        outputFrame.data(),
		width * height,
		i420_buffer_->MutableDataY(), i420_buffer_->StrideY(),
		i420_buffer_->MutableDataU(), i420_buffer_->StrideU(),
		i420_buffer_->MutableDataV(), i420_buffer_->StrideV(),
		0, 0,
		width, height,
		width, height,
		libyuv::kRotate0,
		libyuv::FOURCC_ARGB);


	assert(i420Result == 0);
	(void)i420Result;
	webrtc::VideoFrame nativeVideoFrame = webrtc::VideoFrame(
		i420_buffer_,
		webrtc::kVideoRotation_0,
        webrtc::Clock::GetRealTimeClock()->CurrentTime().us());
	if (const auto sink = _sink.get()) {
		_sink->OnFrame(nativeVideoFrame);
	}
	if (const auto sink = _secondarySink.get()) {
		sink->OnFrame(nativeVideoFrame);
	}
}

void SourceFrameCallbackImpl::setOutput(
        std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    _sink = std::move(sink);
}

void SourceFrameCallbackImpl::setOnFatalError(std::function<void ()> error) {
    _onFatalError = error;
}
void SourceFrameCallbackImpl::setOnPause(std::function<void (bool)> pause) {
    _onPause = pause;
}

void SourceFrameCallbackImpl::setSecondaryOutput(
        std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    _secondarySink = std::move(sink);
}

DesktopSourceRenderer::DesktopSourceRenderer(
    CaptureScheduler &scheduler,
    DesktopCaptureSource source,
    DesktopCaptureSourceData data)
: _scheduler(scheduler)
, _callback(data.aspectSize, data.fps)
, _delayMs(1000. / data.fps) {
	_callback.setOnFatalError([=] {
		stop();
		_fatalError = true;
		if (_onFatalError) _onFatalError();
	});

    _callback.setOnPause([=] (bool pause) {
        bool previousOnPause = _currentlyOnPause;
        _currentlyOnPause = pause;
        if (previousOnPause != _currentlyOnPause) {
            if (_onPause) _onPause(pause);
        }
    });

    auto options = webrtc::DesktopCaptureOptions::CreateDefault();
    options.set_disable_effects(true);
    options.set_detect_updated_region(true);

#ifdef WEBRTC_WIN
    options.set_allow_directx_capturer(true);
#elif defined WEBRTC_MAC
    options.set_allow_iosurface(true);
#elif defined WEBRTC_USE_PIPEWIRE
    options.set_allow_pipewire(true);
#endif // WEBRTC_WIN || WEBRTC_MAC

    _capturer = webrtc::DesktopCapturer::CreateGenericCapturer(options);
    if (!_capturer) {
        if (source.isWindow()) {
            _capturer = webrtc::DesktopCapturer::CreateWindowCapturer(options);
        } else {
            _capturer = webrtc::DesktopCapturer::CreateScreenCapturer(options);
        }
        if (!_capturer) {
            _fatalError = true;
            return;
        }
    }
    if (data.captureMouse) {
        _capturer = std::make_unique<webrtc::DesktopAndCursorComposer>(
            std::move(_capturer),
            options);
    }
    _capturer->SelectSource(source.uniqueId());
    _capturer->Start(&_callback);
}

void DesktopSourceRenderer::start() {
    if (!_capturer || _isRunning) {
        return;
    }
//    ++GlobalCount;
//#ifdef WEBRTC_MAC
//    NSLog(@"current capture count: %d", GlobalCount);
//#endif // WEBRTC_MAC

    _isRunning = true;
    _timerGuard = std::make_shared<bool>(true);
    loop();
}

void DesktopSourceRenderer::stop() {
//    if (_isRunning) {
//        GlobalCount--;
//
//#ifdef WEBRTC_MAC
//        NSLog(@"current capture count: %d", GlobalCount);
//#endif // WEBRTC_MAC
//    }
    _isRunning = false;
    _timerGuard = nullptr;
}

void DesktopSourceRenderer::loop() {
    if (!_capturer || !_isRunning) {
        return;
    }

    _capturer->CaptureFrame();
    const auto guard = std::weak_ptr<bool>(_timerGuard);
    _scheduler.runDelayed(_delayMs, [=] {
        if (guard.lock()) {
            loop();
        }
    });
}

void DesktopSourceRenderer::setOnFatalError(std::function<void ()> error) {
    if (_fatalError) {
        error();
    } else {
        _onFatalError = std::move(error);
    }
}

void DesktopSourceRenderer::setOnPause(std::function<void (bool)> pause) {
    if (_currentlyOnPause) {
        pause(true);
    }
    _onPause = std::move(pause);
}

void DesktopSourceRenderer::setOutput(
        std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    _callback.setOutput(std::move(sink));
}

void DesktopSourceRenderer::setSecondaryOutput(
        std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    _callback.setSecondaryOutput(std::move(sink));
}

} // namespace

struct DesktopCaptureSourceHelper::Renderer {
    CaptureScheduler scheduler;
    std::unique_ptr<DesktopSourceRenderer> renderer;
};

DesktopCaptureSource DesktopCaptureSourceForKey(
	    const std::string &uniqueKey) {
    if (!ShouldBeDesktopCapture(uniqueKey)) {
		return DesktopCaptureSource::Invalid();
    }
    if (uniqueKey == "desktop_capturer_pipewire") {
        return DesktopCaptureSource(0, "pipewire", false);
    }
    const auto windowPrefix = std::string("desktop_capturer_window_");
    const auto isWindow = (uniqueKey.find(windowPrefix) == 0);
    DesktopCaptureSourceManager manager(isWindow
        ? DesktopCaptureType::Window
        : DesktopCaptureType::Screen);
	const auto sources = manager.sources();

    // "desktop_capturer_window_".size() == "desktop_capturer_screen_".size()
    const auto keyId = std::stoll(uniqueKey.substr(windowPrefix.size()));
    for (const auto &source : sources) {
        if (source.uniqueId() == keyId) {
            return source;
        }
    }
    return DesktopCaptureSource::Invalid();
}

bool ShouldBeDesktopCapture(const std::string &uniqueKey) {
    return (uniqueKey.find("desktop_capturer_") == 0);
}

DesktopCaptureSourceHelper::DesktopCaptureSourceHelper(
	DesktopCaptureSource source,
	DesktopCaptureSourceData data)
: _renderer(std::make_shared<Renderer>()) {
    _renderer->scheduler.runAsync([renderer = _renderer, source, data] {
        renderer->renderer = std::make_unique<DesktopSourceRenderer>(
            renderer->scheduler,
            source,
            data);
    });
}

DesktopCaptureSourceHelper::~DesktopCaptureSourceHelper() {
    _renderer->scheduler.runAsync([renderer = _renderer] {
    });
}

void DesktopCaptureSourceHelper::setOutput(
    std::shared_ptr<
        rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) const {
    _renderer->scheduler.runAsync([renderer = _renderer, sink] {
        renderer->renderer->setOutput(sink);
    });
}

void DesktopCaptureSourceHelper::setSecondaryOutput(
    std::shared_ptr<
        rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) const {
	_renderer->scheduler.runAsync([renderer = _renderer, sink] {
		renderer->renderer->setSecondaryOutput(sink);
	});
}

void DesktopCaptureSourceHelper::start() const {
	_renderer->scheduler.runAsync([renderer = _renderer] {
		renderer->renderer->start();
	});
}
void DesktopCaptureSourceHelper::setOnFatalError(std::function<void ()> error) const {
    _renderer->scheduler.runAsync([renderer = _renderer, error = error] {
        renderer->renderer->setOnFatalError(error);
    });
}
void DesktopCaptureSourceHelper::setOnPause(std::function<void (bool)> pause) const {
    _renderer->scheduler.runAsync([renderer = _renderer, pause = pause] {
        renderer->renderer->setOnPause(pause);
    });
}

void DesktopCaptureSourceHelper::stop() const {
	_renderer->scheduler.runAsync([renderer = _renderer] {
		renderer->renderer->stop();
	});
}

} // namespace tgcalls

//
//  DesktopCaptureSourceHelper.h
//  TgVoipWebrtc
//
//  Created by Mikhail Filimonov on 28.12.2020.
//  Copyright © 2020 Mikhail Filimonov. All rights reserved.
//
#ifndef TGCALLS_DESKTOP_CAPTURE_SOURCE_HELPER_H__
#define TGCALLS_DESKTOP_CAPTURE_SOURCE_HELPER_H__

#include "tgcalls/desktop_capturer/DesktopCaptureSource.h"

#include <memory>
#include <functional>

namespace webrtc {
class VideoFrame;
} // namespace webrtc

namespace rtc {
template <typename T>
class VideoSinkInterface;
} // namespace rtc

namespace tgcalls {

DesktopCaptureSource DesktopCaptureSourceForKey(
	const std::string &uniqueKey);
bool ShouldBeDesktopCapture(const std::string &uniqueKey);

class DesktopCaptureSourceHelper {
public:
	DesktopCaptureSourceHelper(
		DesktopCaptureSource source,
		DesktopCaptureSourceData data);
	~DesktopCaptureSourceHelper();

	void setOutput(
		std::shared_ptr<
			rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) const;
	void setSecondaryOutput(
		std::shared_ptr<
		rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) const;
	void start() const;
	void stop() const;
    void setOnFatalError(std::function<void ()>) const;
    void setOnPause(std::function<void (bool)>) const;
private:
	struct Renderer;
	std::shared_ptr<Renderer> _renderer;

};

} // namespace tgcalls

#endif // TGCALLS_DESKTOP_CAPTURE_SOURCE_HELPER_H__

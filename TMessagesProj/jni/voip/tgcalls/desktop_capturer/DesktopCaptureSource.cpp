//
//  DesktopCaptureSource.m
//  TgVoipWebrtc
//
//  Created by Mikhail Filimonov on 29.12.2020.
//  Copyright © 2020 Mikhail Filimonov. All rights reserved.
//

#include "tgcalls/desktop_capturer/DesktopCaptureSource.h"

namespace tgcalls {

std::string DesktopCaptureSourceData::cachedKey() const {
    return std::to_string(aspectSize.width)
        + 'x'
        + std::to_string(aspectSize.height)
        + ':'
        + std::to_string(fps)
        + ':'
        + (captureMouse ? '1' : '0');
}

DesktopCaptureSource::DesktopCaptureSource(
	long long uniqueId,
	std::string title,
    bool isWindow)
: _uniqueId(uniqueId)
, _title(std::move(title))
, _isWindow(isWindow) {
}

long long DesktopCaptureSource::uniqueId() const {
    return _uniqueId;
}

bool DesktopCaptureSource::isWindow() const {
    return _isWindow;
}

std::string DesktopCaptureSource::deviceIdKey() const {
    return std::string("desktop_capturer_")
        + (_isWindow ? "window_" : "screen_")
        + std::to_string(uniqueId());
}

std::string DesktopCaptureSource::title() const {
    return _isWindow ? _title : "Screen";
}

std::string DesktopCaptureSource::uniqueKey() const {
    return std::to_string(_uniqueId)
        + ':'
        + (_isWindow ? "Window" : "Screen");
}

std::string DesktopCaptureSource::deviceIdKey() {
    return static_cast<const DesktopCaptureSource*>(this)->deviceIdKey();
}

std::string DesktopCaptureSource::title() {
	return static_cast<const DesktopCaptureSource*>(this)->title();
}

std::string DesktopCaptureSource::uniqueKey() {
	return static_cast<const DesktopCaptureSource*>(this)->uniqueKey();
}

} // namespace tgcalls

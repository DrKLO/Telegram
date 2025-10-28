//
//  DesktopCaptureSourceManager.m
//  TgVoipWebrtc
//
//  Created by Mikhail Filimonov on 28.12.2020.
//  Copyright © 2020 Mikhail Filimonov. All rights reserved.
//

#include "tgcalls/desktop_capturer/DesktopCaptureSourceManager.h"

#include "modules/desktop_capture/desktop_and_cursor_composer.h"
#include "modules/desktop_capture/desktop_capturer_differ_wrapper.h"
#include "third_party/libyuv/include/libyuv.h"
#include "api/video/i420_buffer.h"
#include "tgcalls/desktop_capturer/DesktopCaptureSourceHelper.h"


namespace tgcalls {

DesktopCaptureSourceManager::DesktopCaptureSourceManager(
    DesktopCaptureType type)
: _capturer(CreateForType(type))
 , _type(type) {
}

DesktopCaptureSourceManager::~DesktopCaptureSourceManager() = default;

webrtc::DesktopCaptureOptions DesktopCaptureSourceManager::OptionsForType(
    DesktopCaptureType type) {
    auto result = webrtc::DesktopCaptureOptions::CreateDefault();
#ifdef WEBRTC_WIN
    result.set_allow_directx_capturer(true);
#elif defined WEBRTC_MAC
    result.set_allow_iosurface(type == DesktopCaptureType::Screen);
#elif defined WEBRTC_USE_PIPEWIRE
    result.set_allow_pipewire(true);
#endif // WEBRTC_WIN || WEBRTC_MAC
    result.set_detect_updated_region(true);
    return result;
}

auto DesktopCaptureSourceManager::CreateForType(DesktopCaptureType type)
-> std::unique_ptr<webrtc::DesktopCapturer> {
    const auto options = OptionsForType(type);
    if (auto result = webrtc::DesktopCapturer::CreateGenericCapturer(options)) {
        return result;
    }
    return (type == DesktopCaptureType::Screen)
        ? webrtc::DesktopCapturer::CreateScreenCapturer(options)
        : webrtc::DesktopCapturer::CreateWindowCapturer(options);
}

std::vector<DesktopCaptureSource> DesktopCaptureSourceManager::sources() {
    auto result = std::vector<DesktopCaptureSource>();
	auto list = webrtc::DesktopCapturer::SourceList();
    if (_capturer && _capturer->GetSourceList(&list)) {
        const auto isWindow = (_type == DesktopCaptureType::Window);
        for (const auto &source : list) {
            result.emplace_back(source.id, source.title, isWindow);
        }
    }
    return result;
}

} // namespace tgcalls

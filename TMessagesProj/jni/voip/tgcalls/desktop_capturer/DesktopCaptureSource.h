//
//  DesktopCaptureSource.h
//  TgVoipWebrtc
//
//  Created by Mikhail Filimonov on 29.12.2020.
//  Copyright © 2020 Mikhail Filimonov. All rights reserved.
//
#ifndef TGCALLS_DESKTOP_CAPTURE_SOURCE_H__
#define TGCALLS_DESKTOP_CAPTURE_SOURCE_H__

#include <string>

#ifdef WEBRTC_WIN
// Compiler errors in conflicting Windows headers if not included here.
#include <winsock2.h>
#endif // WEBRTC_WIN

namespace tgcalls {

class VideoSource {
public:
	virtual ~VideoSource() = default;

	virtual std::string deviceIdKey() = 0;
	virtual std::string title() = 0;
	virtual std::string uniqueKey() = 0;
};

struct DesktopSize {
	int width = 0;
	int height = 0;
};

struct DesktopCaptureSourceData {
	DesktopSize aspectSize;
	double fps = 24.;
	bool captureMouse = true;

	std::string cachedKey() const;
};

class DesktopCaptureSource : public VideoSource {
public:
	DesktopCaptureSource(
		long long uniqueId,
		std::string title,
		bool isWindow);

	static DesktopCaptureSource Invalid() {
		return InvalidTag{};
	}

	long long uniqueId() const;
	bool isWindow() const;

	std::string deviceIdKey() const;
	std::string title() const;
	std::string uniqueKey() const;

	bool valid() const {
		return _valid;
	}
	explicit operator bool() const {
		return _valid;
	}

private:
	struct InvalidTag {};
	DesktopCaptureSource(InvalidTag) : _valid(false) {
	}

	std::string deviceIdKey() override;
	std::string title() override;
	std::string uniqueKey() override;

	long long _uniqueId = 0;
	std::string _title;
	bool _isWindow = false;
	bool _valid = true;

};

} // namespace tgcalls

#endif // TGCALLS_DESKTOP_CAPTURE_SOURCE_H__

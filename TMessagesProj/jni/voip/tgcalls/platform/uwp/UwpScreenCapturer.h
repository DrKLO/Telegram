#ifndef TGCALLS_UWP_SCREEN_CAPTURER_H
#define TGCALLS_UWP_SCREEN_CAPTURER_H

#include "api/scoped_refptr.h"
#include "api/video/video_frame.h"
#include "api/video/video_source_interface.h"
#include "media/base/video_adapter.h"
#include "modules/video_capture/video_capture.h"

#include "VideoCaptureInterface.h"

#include <memory>
#include <vector>
#include <stddef.h>

#include <d3d11.h>
#include <windows.graphics.directx.direct3d11.interop.h>
#include <windows.graphics.capture.h>

#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Graphics.Capture.h>
#include <winrt/Windows.System.h>

using namespace winrt::Windows::Graphics;
using namespace winrt::Windows::Graphics::Capture;
using namespace winrt::Windows::System;

namespace tgcalls {

class UwpScreenCapturer {
public:
	explicit UwpScreenCapturer(
		std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink, GraphicsCaptureItem item);
	~UwpScreenCapturer();

	void setState(VideoState state);
	void setPreferredCaptureAspectRatio(float aspectRatio);
	void setOnFatalError(std::function<void ()> error);
	void setOnPause(std::function<void(bool)> pause);

	std::pair<int, int> resolution() const;

private:
	void create();
	void destroy();

	void onFatalError();

	void OnFrame(std::vector<uint8_t> bytes, int width, int height);

	bool item_closed_;
	bool is_capture_started_;
	SizeInt32 previous_size_;
	Direct3D11CaptureFramePool frame_pool_ = nullptr;
	GraphicsCaptureItem item_;
	GraphicsCaptureSession session_ = nullptr;
	winrt::com_ptr<ID3D11Device> d3d11_device_;
	winrt::com_ptr<IInspectable> direct3d_device_;
	winrt::com_ptr<ID3D11Texture2D> mapped_texture_ = nullptr;
	winrt::slim_mutex lock_;
	DispatcherQueue queue_= nullptr;
	DispatcherQueueController queueController_= nullptr;
	DispatcherQueueTimer repeatingTimer_= nullptr;
	HRESULT CreateMappedTexture(winrt::com_ptr<ID3D11Texture2D> src_texture, UINT width = 0, UINT height = 0);

	//void OnFrameArrived(Direct3D11CaptureFramePool const& sender, winrt::Windows::Foundation::IInspectable const& args);
	void OnFrameArrived(DispatcherQueueTimer const& sender, winrt::Windows::Foundation::IInspectable const& args);
	void OnClosed(GraphicsCaptureItem const& sender, winrt::Windows::Foundation::IInspectable const& args);

	std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> _sink;

	VideoState _state = VideoState::Inactive;
	std::pair<int, int> _dimensions;
	std::function<void()> _onFatalError;
	bool _fatalError = false;
	std::function<void(bool)> _onPause;
	bool _paused = false;
	float _aspectRatio = 0.;
};

}  // namespace tgcalls

#endif

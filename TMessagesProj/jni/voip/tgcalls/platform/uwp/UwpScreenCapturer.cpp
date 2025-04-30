#include "UwpScreenCapturer.h"

#include "api/video/i420_buffer.h"
#include "api/video/video_frame_buffer.h"
#include "api/video/video_rotation.h"
#include "modules/video_capture/video_capture_factory.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

#include <stdint.h>
#include <memory>
#include <algorithm>
#include <libyuv.h>
#include <StaticThreads.h>

namespace tgcalls {
namespace {

constexpr auto kPreferredWidth = 640;
constexpr auto kPreferredHeight = 480;
constexpr auto kPreferredFps = 30;

// We must use a BGRA pixel format that has 4 bytes per pixel, as required by
// the DesktopFrame interface.
constexpr auto kPixelFormat = winrt::Windows::Graphics::DirectX::DirectXPixelFormat::B8G8R8A8UIntNormalized;

// We only want 1 buffer in our frame pool to reduce latency. If we had more,
// they would sit in the pool for longer and be stale by the time we are asked
// for a new frame.
constexpr int kNumBuffers = 1;

} // namespace

UwpScreenCapturer::UwpScreenCapturer(
	std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink, GraphicsCaptureItem item)
: _sink(sink),
item_(item) {
}

UwpScreenCapturer::~UwpScreenCapturer() {
	destroy();
}

void UwpScreenCapturer::create() {
	winrt::slim_lock_guard const guard(lock_);

	RTC_DCHECK(!is_capture_started_);

	if (item_closed_) {
		RTC_LOG(LS_ERROR) << "The target source has been closed.";
		//RecordStartCaptureResult(StartCaptureResult::kSourceClosed);
		onFatalError();
		return;
	}

	HRESULT hr = D3D11CreateDevice(
	  /*adapter=*/nullptr, D3D_DRIVER_TYPE_HARDWARE,
	  /*software_rasterizer=*/nullptr, D3D11_CREATE_DEVICE_BGRA_SUPPORT,
	  /*feature_levels=*/nullptr, /*feature_levels_size=*/0, D3D11_SDK_VERSION,
	  d3d11_device_.put(), /*feature_level=*/nullptr, /*device_context=*/nullptr);
	if (hr == DXGI_ERROR_UNSUPPORTED) {
		// If a hardware device could not be created, use WARP which is a high speed
		// software device.
		hr = D3D11CreateDevice(
			/*adapter=*/nullptr, D3D_DRIVER_TYPE_WARP,
			/*software_rasterizer=*/nullptr, D3D11_CREATE_DEVICE_BGRA_SUPPORT,
			/*feature_levels=*/nullptr, /*feature_levels_size=*/0,
			D3D11_SDK_VERSION, d3d11_device_.put(), /*feature_level=*/nullptr,
			/*device_context=*/nullptr);
	}

	RTC_DCHECK(d3d11_device_);
	RTC_DCHECK(item_);

	// Listen for the Closed event, to detect if the source we are capturing is
	// closed (e.g. application window is closed or monitor is disconnected). If
	// it is, we should abort the capture.
	item_.Closed({ this, &UwpScreenCapturer::OnClosed });

	winrt::com_ptr<IDXGIDevice> dxgi_device;
	hr = d3d11_device_->QueryInterface(IID_PPV_ARGS(&dxgi_device));
	if (FAILED(hr)) {
		//RecordStartCaptureResult(StartCaptureResult::kDxgiDeviceCastFailed);
		onFatalError();
		return;
	}

	hr = CreateDirect3D11DeviceFromDXGIDevice(dxgi_device.get(), direct3d_device_.put());
	if (FAILED(hr)) {
		//RecordStartCaptureResult(StartCaptureResult::kD3dDeviceCreationFailed);
		onFatalError();
		return;
	}

	// Cast to FramePoolStatics2 so we can use CreateFreeThreaded and avoid the
	// need to have a DispatcherQueue. We don't listen for the FrameArrived event,
	// so there's no difference.

	previous_size_ = item_.Size();
	_dimensions = std::make_pair(previous_size_.Width, previous_size_.Height);

	winrt::Windows::Graphics::DirectX::Direct3D11::IDirect3DDevice directDevice;
	direct3d_device_->QueryInterface(winrt::guid_of<winrt::Windows::Graphics::DirectX::Direct3D11::IDirect3DDevice>(), winrt::put_abi(directDevice));

	frame_pool_ = Direct3D11CaptureFramePool::CreateFreeThreaded(directDevice, kPixelFormat, kNumBuffers, previous_size_);
	//frame_pool_.FrameArrived({ this, &UwpScreenCapturer::OnFrameArrived });

	session_ = frame_pool_.CreateCaptureSession(item_);
	session_.StartCapture();

	is_capture_started_ = true;
	queueController_ = DispatcherQueueController::CreateOnDedicatedThread();
	queue_ = queueController_.DispatcherQueue();

	repeatingTimer_ = queue_.CreateTimer();
	repeatingTimer_.Interval(std::chrono::milliseconds{ 1000 / kPreferredFps });
	repeatingTimer_.Tick({this, &UwpScreenCapturer::OnFrameArrived});
	repeatingTimer_.Start();
}

//void UwpScreenCapturer::OnFrameArrived(Direct3D11CaptureFramePool const& sender, winrt::Windows::Foundation::IInspectable const&) {
void UwpScreenCapturer::OnFrameArrived(DispatcherQueueTimer const& sender, winrt::Windows::Foundation::IInspectable const& args) {
	winrt::slim_lock_guard const guard(lock_);

	if (item_closed_ || _state != VideoState::Active) {
		RTC_LOG(LS_ERROR) << "The target source has been closed.";
		onFatalError();
		return;
	}

	RTC_DCHECK(is_capture_started_);

	auto capture_frame = frame_pool_.TryGetNextFrame();
	if (!capture_frame) {
		// When resuming the capture after minimizing a window there seems to be a subsequent
		// frame drop, as I'm lazing to deal with this from here this event is debounced in C# code.
		if (!_paused) {
			_paused = true;

			if (_onPause){
				_onPause(true);
			}
		}

		//RecordGetFrameResult(GetFrameResult::kFrameDropped);
		return /*hr*/;
	}

	if (_paused) {
		_paused = false;

		if (_onPause){
			_onPause(false);
		}
	}

	// We need to get this CaptureFrame as an ID3D11Texture2D so that we can get
	// the raw image data in the format required by the DesktopFrame interface.
	auto d3d_surface = capture_frame.Surface();

	auto direct3DDxgiInterfaceAccess
		= d3d_surface.as<Windows::Graphics::DirectX::Direct3D11::IDirect3DDxgiInterfaceAccess>();

	winrt::com_ptr<ID3D11Texture2D> texture_2D;
	auto hr = direct3DDxgiInterfaceAccess->GetInterface(IID_PPV_ARGS(&texture_2D));
	if (FAILED(hr)) {
		//RecordGetFrameResult(GetFrameResult::kTexture2dCastFailed);
		onFatalError();
		return;
	}

	if (!mapped_texture_) {
		hr = CreateMappedTexture(texture_2D);
		if (FAILED(hr)) {
			//RecordGetFrameResult(GetFrameResult::kCreateMappedTextureFailed);
			onFatalError();
			return;
		}
	}

	//// We need to copy |texture_2D| into |mapped_texture_| as the latter has the
	//// D3D11_CPU_ACCESS_READ flag set, which lets us access the image data.
	//// Otherwise it would only be readable by the GPU.
	winrt::com_ptr<ID3D11DeviceContext> d3d_context;
	d3d11_device_->GetImmediateContext(d3d_context.put());
	d3d_context->CopyResource(mapped_texture_.get(), texture_2D.get());

	D3D11_MAPPED_SUBRESOURCE map_info;
	hr = d3d_context->Map(mapped_texture_.get(), /*subresource_index=*/0,
						D3D11_MAP_READ, /*D3D11_MAP_FLAG_DO_NOT_WAIT=*/0,
						&map_info);
	if (FAILED(hr)) {
		//RecordGetFrameResult(GetFrameResult::kMapFrameFailed);
		onFatalError();
		return;
	}

	auto new_size = capture_frame.ContentSize();

	// If the size has changed since the last capture, we must be sure to use
	// the smaller dimensions. Otherwise we might overrun our buffer, or
	// read stale data from the last frame.
	int image_height = std::min(previous_size_.Height, new_size.Height);
	int image_width = std::min(previous_size_.Width, new_size.Width);
	int row_data_length = image_width * 4;

	// Make a copy of the data pointed to by |map_info.pData| so we are free to
	// unmap our texture.
	uint8_t* src_data = static_cast<uint8_t*>(map_info.pData);
	std::vector<uint8_t> image_data;
	image_data.reserve(image_height * row_data_length);
	uint8_t* image_data_ptr = image_data.data();
	for (int i = 0; i < image_height; i++) {
		memcpy(image_data_ptr, src_data, row_data_length);
		image_data_ptr += row_data_length;
		src_data += map_info.RowPitch;
	}

	if (_state == VideoState::Active && !item_closed_) {
		// Transfer ownership of |image_data| to the output_frame.
		OnFrame(std::move(image_data), image_width, image_height);
	}

	d3d_context->Unmap(mapped_texture_.get(), 0);

	// If the size changed, we must resize the texture and frame pool to fit the
	// new size.
	if (previous_size_.Height != new_size.Height ||
		previous_size_.Width != new_size.Width) {
		hr = CreateMappedTexture(texture_2D, new_size.Width, new_size.Height);
		if (FAILED(hr)) {
			//RecordGetFrameResult(GetFrameResult::kResizeMappedTextureFailed);
			onFatalError();
			return;
		}

		winrt::Windows::Graphics::DirectX::Direct3D11::IDirect3DDevice directDevice;
		direct3d_device_->QueryInterface(winrt::guid_of<winrt::Windows::Graphics::DirectX::Direct3D11::IDirect3DDevice>(), winrt::put_abi(directDevice));

		frame_pool_.Recreate(directDevice, kPixelFormat, kNumBuffers, new_size);
	}

	//RecordGetFrameResult(GetFrameResult::kSuccess);

	previous_size_ = new_size;
}

void UwpScreenCapturer::OnClosed(GraphicsCaptureItem const& sender, winrt::Windows::Foundation::IInspectable const&)
{
	winrt::slim_lock_guard const guard(lock_);

	RTC_LOG(LS_INFO) << "Capture target has been closed.";
	item_closed_ = true;
	is_capture_started_ = false;

	onFatalError();
}

HRESULT UwpScreenCapturer::CreateMappedTexture(winrt::com_ptr<ID3D11Texture2D> src_texture, UINT width, UINT height) {
	if (mapped_texture_ != nullptr) {
		mapped_texture_ = nullptr;
	}

  D3D11_TEXTURE2D_DESC src_desc;
  src_texture->GetDesc(&src_desc);
  D3D11_TEXTURE2D_DESC map_desc;
  map_desc.Width = width == 0 ? src_desc.Width : width;
  map_desc.Height = height == 0 ? src_desc.Height : height;
  map_desc.MipLevels = src_desc.MipLevels;
  map_desc.ArraySize = src_desc.ArraySize;
  map_desc.Format = src_desc.Format;
  map_desc.SampleDesc = src_desc.SampleDesc;
  map_desc.Usage = D3D11_USAGE_STAGING;
  map_desc.BindFlags = 0;
  map_desc.CPUAccessFlags = D3D11_CPU_ACCESS_READ;
  map_desc.MiscFlags = 0;
  return d3d11_device_->CreateTexture2D(&map_desc, nullptr, mapped_texture_.put());
}

void UwpScreenCapturer::setState(VideoState state) {
	if (_state == state) {
		return;
	}
	_state = state;
	if (_state == VideoState::Active) {
		create();
	} else {
		destroy();
	}
}

void UwpScreenCapturer::setPreferredCaptureAspectRatio(float aspectRatio) {
	_aspectRatio = aspectRatio;
}

void UwpScreenCapturer::setOnFatalError(std::function<void ()> error) {
	if (_fatalError) {
		error();
	} else {
		_onFatalError = std::move(error);
	}
}

void UwpScreenCapturer::setOnPause(std::function<void(bool)> pause) {
	if (_paused) {
		pause(true);
	}

	_onPause = std::move(pause);
}

std::pair<int, int> UwpScreenCapturer::resolution() const {
	return _dimensions;
}

void UwpScreenCapturer::onFatalError() {
	if (repeatingTimer_ != nullptr) {
		repeatingTimer_.Stop();
		repeatingTimer_ = nullptr;
		queue_ = nullptr;
		queueController_ = nullptr;
	}

	if (session_ != nullptr) {
		session_.Close();
		item_ = nullptr;
		item_closed_ = true;
	}

	if (frame_pool_ != nullptr) {
		frame_pool_.Close();
	}

	mapped_texture_ = nullptr;
	frame_pool_ = nullptr;
	session_ = nullptr;
	direct3d_device_ = nullptr;
	d3d11_device_ = nullptr;

	_fatalError = true;
	if (_onFatalError) {
		_onFatalError();
	}
}

void UwpScreenCapturer::destroy() {
	winrt::slim_lock_guard const guard(lock_);

	_onFatalError = nullptr;
	onFatalError();
}

void UwpScreenCapturer::OnFrame(std::vector<uint8_t> bytes, int width, int height) {
	if (_state != VideoState::Active) {
		return;
	}

	int dst_width = width & ~1;
	int dst_height = abs(height) & ~1;
	int dst_stride_y = dst_width;
	int dst_stride_uv = (dst_width + 1) / 2;

	uint8_t* plane_y = bytes.data();
	size_t videoFrameLength = bytes.size();
	int32_t stride_y = width * 4;
	uint8_t* plane_uv = plane_y + videoFrameLength;
	int32_t stride_uv = stride_y / 2;

	rtc::scoped_refptr<webrtc::I420Buffer> buffer = webrtc::I420Buffer::Create(
		dst_width, dst_height, dst_stride_y, dst_stride_uv, dst_stride_uv);

	const int conversionResult = libyuv::ConvertToI420(
		plane_y, videoFrameLength, stride_y, plane_uv, stride_uv,
		buffer.get()->MutableDataY(), buffer.get()->StrideY(),
		buffer.get()->MutableDataU(), buffer.get()->StrideU(),
		buffer.get()->MutableDataV(), buffer.get()->StrideV(),
		0, 0,  // No Cropping
		width, height, dst_width, dst_height, libyuv::kRotate0,
		libyuv::FOURCC_ARGB);
	if (conversionResult < 0) {
		RTC_LOG(LS_ERROR) << "Failed to convert capture frame from type "
			<< static_cast<int>(libyuv::FOURCC_ARGB) << "to I420.";
		return;
	}

	webrtc::VideoFrame captureFrame =
		webrtc::VideoFrame::Builder()
		.set_video_frame_buffer(buffer)
		.set_timestamp_rtp(0)
		.set_timestamp_ms(rtc::TimeMillis())
		.set_rotation(webrtc::kVideoRotation_0)
		.build();

	_sink->OnFrame(captureFrame);
}

}  // namespace tgcalls

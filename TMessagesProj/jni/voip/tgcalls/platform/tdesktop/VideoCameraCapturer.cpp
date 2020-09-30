#include "VideoCameraCapturer.h"

#include "api/video/i420_buffer.h"
#include "api/video/video_frame_buffer.h"
#include "api/video/video_rotation.h"
#include "modules/video_capture/video_capture_factory.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

#include <stdint.h>
#include <memory>
#include <algorithm>

namespace tgcalls {

VideoCameraCapturer::VideoCameraCapturer(const CreateTag &) {
}

VideoCameraCapturer::~VideoCameraCapturer() {
	destroy();
}

bool VideoCameraCapturer::init(
		size_t width,
		size_t height,
		size_t target_fps,
		size_t capture_device_index) {
	std::unique_ptr<webrtc::VideoCaptureModule::DeviceInfo> device_info(
		webrtc::VideoCaptureFactory::CreateDeviceInfo());

	char device_name[256];
	char unique_name[256];
	if (device_info->GetDeviceName(static_cast<uint32_t>(capture_device_index),
		device_name, sizeof(device_name), unique_name,
		sizeof(unique_name)) != 0) {
		destroy();
		return false;
	}

	_module = webrtc::VideoCaptureFactory::Create(unique_name);
	if (!_module) {
		return false;
	}
	_module->RegisterCaptureDataCallback(this);

	device_info->GetCapability(_module->CurrentDeviceName(), 0, _capability);

	_capability.width = static_cast<int32_t>(width);
	_capability.height = static_cast<int32_t>(height);
	_capability.maxFPS = static_cast<int32_t>(target_fps);
	_capability.videoType = webrtc::VideoType::kI420;

	if (_module->StartCapture(_capability) != 0) {
		destroy();
		return false;
	}

	RTC_CHECK(_module->CaptureStarted());

	return true;
}

void VideoCameraCapturer::setState(VideoState state) {
	if (_state == state) {
		return;
	}
	_state = state;
	if (_state == VideoState::Inactive) {
		_module->StopCapture();
	} else {
		_module->StartCapture(_capability);
	}
}

void VideoCameraCapturer::setPreferredCaptureAspectRatio(float aspectRatio) {
	_aspectRatio = aspectRatio;
}

std::unique_ptr<VideoCameraCapturer> VideoCameraCapturer::Create(
		size_t width,
		size_t height,
		size_t target_fps,
		size_t capture_device_index) {
	auto result = std::make_unique<VideoCameraCapturer>(CreateTag{});
	if (!result->init(width, height, target_fps, capture_device_index)) {
		RTC_LOG(LS_WARNING)
			<< "Failed to create VideoCameraCapturer("
			<< "w = " << width << ", "
			<< "h = " << height << ", "
			<< "fps = " << target_fps << ")";
		return nullptr;
	}
	return result;
}

void VideoCameraCapturer::destroy() {
	if (!_module) {
		return;
	}

	_module->StopCapture();
	_module->DeRegisterCaptureDataCallback();
	_module = nullptr;
}

void VideoCameraCapturer::OnFrame(const webrtc::VideoFrame &frame) {
	if (_state != VideoState::Active) {
		return;
	}
	//int cropped_width = 0;
	//int cropped_height = 0;
	//int out_width = 0;
	//int out_height = 0;

	//if (!_videoAdapter.AdaptFrameResolution(
	//	frame.width(), frame.height(), frame.timestamp_us() * 1000,
	//	&cropped_width, &cropped_height, &out_width, &out_height)) {
	//	// Drop frame in order to respect frame rate constraint.
	//	return;
	//}
	//if (out_height != frame.height() || out_width != frame.width()) {
	//	// Video adapter has requested a down-scale. Allocate a new buffer and
	//	// return scaled version.
	//	// For simplicity, only scale here without cropping.
	//	rtc::scoped_refptr<webrtc::I420Buffer> scaled_buffer =
	//		webrtc::I420Buffer::Create(out_width, out_height);
	//	scaled_buffer->ScaleFrom(*frame.video_frame_buffer()->ToI420());
	//	webrtc::VideoFrame::Builder new_frame_builder =
	//		webrtc::VideoFrame::Builder()
	//		.set_video_frame_buffer(scaled_buffer)
	//		.set_rotation(webrtc::kVideoRotation_0)
	//		.set_timestamp_us(frame.timestamp_us())
	//		.set_id(frame.id());
	//	if (frame.has_update_rect()) {
	//		webrtc::VideoFrame::UpdateRect new_rect = frame.update_rect().ScaleWithFrame(
	//			frame.width(), frame.height(), 0, 0, frame.width(), frame.height(),
	//			out_width, out_height);
	//		new_frame_builder.set_update_rect(new_rect);
	//	}
	//	_broadcaster.OnFrame(new_frame_builder.build());

	//} else {
	//	// No adaptations needed, just return the frame as is.
	//	_broadcaster.OnFrame(frame);
	//}

	if (_aspectRatio <= 0.001) {
		_broadcaster.OnFrame(frame);
		return;
	}
	const auto originalWidth = frame.width();
	const auto originalHeight = frame.height();
	auto width = (originalWidth > _aspectRatio * originalHeight)
		? int(std::round(_aspectRatio * originalHeight))
		: originalWidth;
	auto height = (originalWidth > _aspectRatio * originalHeight)
		? originalHeight
		: int(std::round(originalHeight / _aspectRatio));
	if ((width >= originalWidth && height >= originalHeight) || !width || !height) {
		_broadcaster.OnFrame(frame);
		return;
	}

	width &= ~int(1);
	height &= ~int(1);
	const auto left = (originalWidth - width) / 2;
	const auto top = (originalHeight - height) / 2;
	rtc::scoped_refptr<webrtc::I420Buffer> croppedBuffer =
		webrtc::I420Buffer::Create(width, height);
	croppedBuffer->CropAndScaleFrom(
		*frame.video_frame_buffer()->ToI420(),
		left,
		top,
		width,
		height);
	webrtc::VideoFrame::Builder croppedBuilder =
		webrtc::VideoFrame::Builder()
		.set_video_frame_buffer(croppedBuffer)
		.set_rotation(webrtc::kVideoRotation_0)
		.set_timestamp_us(frame.timestamp_us())
		.set_id(frame.id());
	if (frame.has_update_rect()) {
		croppedBuilder.set_update_rect(frame.update_rect().ScaleWithFrame(
			frame.width(),
			frame.height(),
			left,
			top,
			width,
			height,
			width,
			height));
	}
	_broadcaster.OnFrame(croppedBuilder.build());
}

void VideoCameraCapturer::AddOrUpdateSink(
		rtc::VideoSinkInterface<webrtc::VideoFrame> *sink,
		const rtc::VideoSinkWants &wants) {
	_broadcaster.AddOrUpdateSink(sink, wants);
	updateVideoAdapter();
}

void VideoCameraCapturer::RemoveSink(rtc::VideoSinkInterface<webrtc::VideoFrame> *sink) {
	_broadcaster.RemoveSink(sink);
	updateVideoAdapter();
}

void VideoCameraCapturer::updateVideoAdapter() {
	//_videoAdapter.OnSinkWants(_broadcaster.wants());
}

}  // namespace tgcalls

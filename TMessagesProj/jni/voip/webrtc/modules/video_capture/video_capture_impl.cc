/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_capture/video_capture_impl.h"

#include <stdlib.h>
#include <string.h>

#include "api/video/i420_buffer.h"
#include "api/video/video_frame_buffer.h"
#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "modules/video_capture/video_capture_config.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"
#include "third_party/libyuv/include/libyuv.h"

namespace webrtc {
namespace videocapturemodule {

const char* VideoCaptureImpl::CurrentDeviceName() const {
  RTC_DCHECK_RUN_ON(&api_checker_);
  return _deviceUniqueId;
}

// static
int32_t VideoCaptureImpl::RotationFromDegrees(int degrees,
                                              VideoRotation* rotation) {
  switch (degrees) {
    case 0:
      *rotation = kVideoRotation_0;
      return 0;
    case 90:
      *rotation = kVideoRotation_90;
      return 0;
    case 180:
      *rotation = kVideoRotation_180;
      return 0;
    case 270:
      *rotation = kVideoRotation_270;
      return 0;
    default:
      return -1;
      ;
  }
}

// static
int32_t VideoCaptureImpl::RotationInDegrees(VideoRotation rotation,
                                            int* degrees) {
  switch (rotation) {
    case kVideoRotation_0:
      *degrees = 0;
      return 0;
    case kVideoRotation_90:
      *degrees = 90;
      return 0;
    case kVideoRotation_180:
      *degrees = 180;
      return 0;
    case kVideoRotation_270:
      *degrees = 270;
      return 0;
  }
  return -1;
}

VideoCaptureImpl::VideoCaptureImpl()
    : _deviceUniqueId(NULL),
      _requestedCapability(),
      _lastProcessTimeNanos(rtc::TimeNanos()),
      _lastFrameRateCallbackTimeNanos(rtc::TimeNanos()),
      _dataCallBack(NULL),
      _rawDataCallBack(NULL),
      _lastProcessFrameTimeNanos(rtc::TimeNanos()),
      _rotateFrame(kVideoRotation_0),
      apply_rotation_(false) {
  _requestedCapability.width = kDefaultWidth;
  _requestedCapability.height = kDefaultHeight;
  _requestedCapability.maxFPS = 30;
  _requestedCapability.videoType = VideoType::kI420;
  memset(_incomingFrameTimesNanos, 0, sizeof(_incomingFrameTimesNanos));
}

VideoCaptureImpl::~VideoCaptureImpl() {
  RTC_DCHECK_RUN_ON(&api_checker_);
  DeRegisterCaptureDataCallback();
  if (_deviceUniqueId)
    delete[] _deviceUniqueId;
}

void VideoCaptureImpl::RegisterCaptureDataCallback(
    rtc::VideoSinkInterface<VideoFrame>* dataCallBack) {
  MutexLock lock(&api_lock_);
  RTC_DCHECK(!_rawDataCallBack);
  _dataCallBack = dataCallBack;
}

void VideoCaptureImpl::RegisterCaptureDataCallback(
    RawVideoSinkInterface* dataCallBack) {
  MutexLock lock(&api_lock_);
  RTC_DCHECK(!_dataCallBack);
  _rawDataCallBack = dataCallBack;
}

void VideoCaptureImpl::DeRegisterCaptureDataCallback() {
  MutexLock lock(&api_lock_);
  _dataCallBack = NULL;
  _rawDataCallBack = NULL;
}
int32_t VideoCaptureImpl::DeliverCapturedFrame(VideoFrame& captureFrame) {
  RTC_CHECK_RUNS_SERIALIZED(&capture_checker_);

  UpdateFrameCount();  // frame count used for local frame rate callback.

  if (_dataCallBack) {
    _dataCallBack->OnFrame(captureFrame);
  }

  return 0;
}

void VideoCaptureImpl::DeliverRawFrame(uint8_t* videoFrame,
                                       size_t videoFrameLength,
                                       const VideoCaptureCapability& frameInfo,
                                       int64_t captureTime) {
  RTC_CHECK_RUNS_SERIALIZED(&capture_checker_);

  UpdateFrameCount();
  _rawDataCallBack->OnRawFrame(videoFrame, videoFrameLength, frameInfo,
                               _rotateFrame, captureTime);
}

int32_t VideoCaptureImpl::IncomingFrame(uint8_t* videoFrame,
                                        size_t videoFrameLength,
                                        const VideoCaptureCapability& frameInfo,
                                        int64_t captureTime /*=0*/) {
  RTC_CHECK_RUNS_SERIALIZED(&capture_checker_);
  MutexLock lock(&api_lock_);

  const int32_t width = frameInfo.width;
  const int32_t height = frameInfo.height;

  TRACE_EVENT1("webrtc", "VC::IncomingFrame", "capture_time", captureTime);

  if (_rawDataCallBack) {
    DeliverRawFrame(videoFrame, videoFrameLength, frameInfo, captureTime);
    return 0;
  }

  // Not encoded, convert to I420.
  if (frameInfo.videoType != VideoType::kMJPEG) {
    // Allow buffers larger than expected. On linux gstreamer allocates buffers
    // page-aligned and v4l2loopback passes us the buffer size verbatim which
    // for most cases is larger than expected.
    // See https://github.com/umlaeute/v4l2loopback/issues/190.
    if (auto size = CalcBufferSize(frameInfo.videoType, width, abs(height));
        videoFrameLength < size) {
      RTC_LOG(LS_ERROR) << "Wrong incoming frame length. Expected " << size
                        << ", Got " << videoFrameLength << ".";
      return -1;
    }
  }

  int stride_y = width;
  int stride_uv = (width + 1) / 2;
  int target_width = width;
  int target_height = abs(height);

  if (apply_rotation_) {
    // Rotating resolution when for 90/270 degree rotations.
    if (_rotateFrame == kVideoRotation_90 ||
        _rotateFrame == kVideoRotation_270) {
      target_width = abs(height);
      target_height = width;
    }
  }

  // Setting absolute height (in case it was negative).
  // In Windows, the image starts bottom left, instead of top left.
  // Setting a negative source height, inverts the image (within LibYuv).
  rtc::scoped_refptr<I420Buffer> buffer = I420Buffer::Create(
      target_width, target_height, stride_y, stride_uv, stride_uv);

  libyuv::RotationMode rotation_mode = libyuv::kRotate0;
  if (apply_rotation_) {
    switch (_rotateFrame) {
      case kVideoRotation_0:
        rotation_mode = libyuv::kRotate0;
        break;
      case kVideoRotation_90:
        rotation_mode = libyuv::kRotate90;
        break;
      case kVideoRotation_180:
        rotation_mode = libyuv::kRotate180;
        break;
      case kVideoRotation_270:
        rotation_mode = libyuv::kRotate270;
        break;
    }
  }

  const int conversionResult = libyuv::ConvertToI420(
      videoFrame, videoFrameLength, buffer.get()->MutableDataY(),
      buffer.get()->StrideY(), buffer.get()->MutableDataU(),
      buffer.get()->StrideU(), buffer.get()->MutableDataV(),
      buffer.get()->StrideV(), 0, 0,  // No Cropping
      width, height, target_width, target_height, rotation_mode,
      ConvertVideoType(frameInfo.videoType));
  if (conversionResult != 0) {
    RTC_LOG(LS_ERROR) << "Failed to convert capture frame from type "
                      << static_cast<int>(frameInfo.videoType) << "to I420.";
    return -1;
  }

  VideoFrame captureFrame =
      VideoFrame::Builder()
          .set_video_frame_buffer(buffer)
          .set_timestamp_rtp(0)
          .set_timestamp_ms(rtc::TimeMillis())
          .set_rotation(!apply_rotation_ ? _rotateFrame : kVideoRotation_0)
          .build();
  captureFrame.set_ntp_time_ms(captureTime);

  DeliverCapturedFrame(captureFrame);

  return 0;
}

int32_t VideoCaptureImpl::StartCapture(
    const VideoCaptureCapability& capability) {
  RTC_DCHECK_RUN_ON(&api_checker_);
  _requestedCapability = capability;
  return -1;
}

int32_t VideoCaptureImpl::StopCapture() {
  return -1;
}

bool VideoCaptureImpl::CaptureStarted() {
  return false;
}

int32_t VideoCaptureImpl::CaptureSettings(
    VideoCaptureCapability& /*settings*/) {
  return -1;
}

int32_t VideoCaptureImpl::SetCaptureRotation(VideoRotation rotation) {
  MutexLock lock(&api_lock_);
  _rotateFrame = rotation;
  return 0;
}

bool VideoCaptureImpl::SetApplyRotation(bool enable) {
  MutexLock lock(&api_lock_);
  apply_rotation_ = enable;
  return true;
}

bool VideoCaptureImpl::GetApplyRotation() {
  MutexLock lock(&api_lock_);
  return apply_rotation_;
}

void VideoCaptureImpl::UpdateFrameCount() {
  RTC_CHECK_RUNS_SERIALIZED(&capture_checker_);

  if (_incomingFrameTimesNanos[0] / rtc::kNumNanosecsPerMicrosec == 0) {
    // first no shift
  } else {
    // shift
    for (int i = (kFrameRateCountHistorySize - 2); i >= 0; --i) {
      _incomingFrameTimesNanos[i + 1] = _incomingFrameTimesNanos[i];
    }
  }
  _incomingFrameTimesNanos[0] = rtc::TimeNanos();
}

uint32_t VideoCaptureImpl::CalculateFrameRate(int64_t now_ns) {
  RTC_CHECK_RUNS_SERIALIZED(&capture_checker_);

  int32_t num = 0;
  int32_t nrOfFrames = 0;
  for (num = 1; num < (kFrameRateCountHistorySize - 1); ++num) {
    if (_incomingFrameTimesNanos[num] <= 0 ||
        (now_ns - _incomingFrameTimesNanos[num]) /
                rtc::kNumNanosecsPerMillisec >
            kFrameRateHistoryWindowMs) {  // don't use data older than 2sec
      break;
    } else {
      nrOfFrames++;
    }
  }
  if (num > 1) {
    int64_t diff = (now_ns - _incomingFrameTimesNanos[num - 1]) /
                   rtc::kNumNanosecsPerMillisec;
    if (diff > 0) {
      return uint32_t((nrOfFrames * 1000.0f / diff) + 0.5f);
    }
  }

  return nrOfFrames;
}
}  // namespace videocapturemodule
}  // namespace webrtc

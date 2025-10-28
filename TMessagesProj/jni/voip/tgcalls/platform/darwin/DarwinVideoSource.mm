/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "DarwinVideoSource.h"

#import "base/RTCVideoFrame.h"
#import "base/RTCVideoFrameBuffer.h"
#import "components/video_frame_buffer/RTCCVPixelBuffer.h"

#include "api/video/i420_buffer.h"
#include "sdk/objc/native/src/objc_frame_buffer.h"

namespace tgcalls {

DarwinVideoTrackSource::DarwinVideoTrackSource()
    : AdaptedVideoTrackSource(/* required resolution alignment */ 2) {}

bool DarwinVideoTrackSource::is_screencast() const {
  return false;
}

absl::optional<bool> DarwinVideoTrackSource::needs_denoising() const {
  return false;
}

webrtc::MediaSourceInterface::SourceState DarwinVideoTrackSource::state() const {
  return SourceState::kLive;
}

bool DarwinVideoTrackSource::remote() const {
  return false;
}

void DarwinVideoTrackSource::OnOutputFormatRequest(int width, int height, int fps) {
  cricket::VideoFormat format(width, height, cricket::VideoFormat::FpsToInterval(fps), 0);
  video_adapter()->OnOutputFormatRequest(format);
}

void DarwinVideoTrackSource::OnCapturedFrame(RTC_OBJC_TYPE(RTCVideoFrame) * frame) {
  const int64_t timestamp_us = frame.timeStampNs / rtc::kNumNanosecsPerMicrosec;
  const int64_t translated_timestamp_us =
      timestamp_aligner_.TranslateTimestamp(timestamp_us, rtc::TimeMicros());

  int adapted_width;
  int adapted_height;
  int crop_width;
  int crop_height;
  int crop_x;
  int crop_y;
  if (!AdaptFrame(frame.width,
                  frame.height,
                  timestamp_us,
                  &adapted_width,
                  &adapted_height,
                  &crop_width,
                  &crop_height,
                  &crop_x,
                  &crop_y)) {
    return;
  }

  webrtc::scoped_refptr<webrtc::VideoFrameBuffer> buffer;
  if (adapted_width == frame.width && adapted_height == frame.height) {
    // No adaption - optimized path.
    @autoreleasepool {
    buffer = new rtc::RefCountedObject<webrtc::ObjCFrameBuffer>(frame.buffer);
    }
  } else if ([frame.buffer isKindOfClass:[RTC_OBJC_TYPE(RTCCVPixelBuffer) class]]) {
    // Adapted CVPixelBuffer frame.
    @autoreleasepool {
    RTC_OBJC_TYPE(RTCCVPixelBuffer) *rtcPixelBuffer =
        (RTC_OBJC_TYPE(RTCCVPixelBuffer) *)frame.buffer;
    buffer = new rtc::RefCountedObject<webrtc::ObjCFrameBuffer>([[RTC_OBJC_TYPE(RTCCVPixelBuffer) alloc]
        initWithPixelBuffer:rtcPixelBuffer.pixelBuffer
               adaptedWidth:adapted_width
              adaptedHeight:adapted_height
                  cropWidth:crop_width
                 cropHeight:crop_height
                      cropX:crop_x + rtcPixelBuffer.cropX
                      cropY:crop_y + rtcPixelBuffer.cropY]);
    }
  } else {
    @autoreleasepool {
    // Adapted I420 frame.
    // TODO(magjed): Optimize this I420 path.
    webrtc::scoped_refptr<webrtc::I420Buffer> i420_buffer = webrtc::I420Buffer::Create(adapted_width, adapted_height);
    buffer = new rtc::RefCountedObject<webrtc::ObjCFrameBuffer>(frame.buffer);
    i420_buffer->CropAndScaleFrom(*buffer->ToI420(), crop_x, crop_y, crop_width, crop_height);
    buffer = i420_buffer;
    }
  }

  // Applying rotation is only supported for legacy reasons and performance is
  // not critical here.
    webrtc::VideoRotation rotation = static_cast<webrtc::VideoRotation>(frame.rotation);
  if (apply_rotation() && rotation != webrtc::kVideoRotation_0) {
    buffer = webrtc::I420Buffer::Rotate(*buffer->ToI420(), rotation);
    rotation = webrtc::kVideoRotation_0;
  }

  OnFrame(webrtc::VideoFrame::Builder()
              .set_video_frame_buffer(buffer)
              .set_rotation(rotation)
              .set_timestamp_us(translated_timestamp_us)
              .build());
}

bool DarwinVideoTrackSource::OnCapturedFrame(const webrtc::VideoFrame& frame) {
    const int64_t timestamp_us = frame.timestamp_us() / rtc::kNumNanosecsPerMicrosec;
    const int64_t translated_timestamp_us =
        timestamp_aligner_.TranslateTimestamp(timestamp_us, rtc::TimeMicros());

    int adapted_width;
    int adapted_height;
    int crop_width;
    int crop_height;
    int crop_x;
    int crop_y;
    if (!AdaptFrame(frame.width(),
                    frame.height(),
                    timestamp_us,
                    &adapted_width,
                    &adapted_height,
                    &crop_width,
                    &crop_height,
                    &crop_x,
                    &crop_y)) {
      return false;
    }

    webrtc::scoped_refptr<webrtc::VideoFrameBuffer> buffer;
    if (adapted_width == frame.width() && adapted_height == frame.height()) {
        buffer = frame.video_frame_buffer();
    } else {
        webrtc::scoped_refptr<webrtc::I420Buffer> i420_buffer = webrtc::I420Buffer::Create(adapted_width, adapted_height);
        buffer = frame.video_frame_buffer();
        i420_buffer->CropAndScaleFrom(*buffer->ToI420(), crop_x, crop_y, crop_width, crop_height);
        buffer = i420_buffer;
    }

    // Applying rotation is only supported for legacy reasons and performance is
    // not critical here.
    webrtc::VideoRotation rotation = frame.rotation();
    if (apply_rotation() && rotation != webrtc::kVideoRotation_0) {
      buffer = webrtc::I420Buffer::Rotate(*buffer->ToI420(), rotation);
      rotation = webrtc::kVideoRotation_0;
    }

    OnFrame(webrtc::VideoFrame::Builder()
                .set_video_frame_buffer(buffer)
                .set_rotation(rotation)
                .set_timestamp_us(translated_timestamp_us)
                .build());

    return true;
}

}  // namespace webrtc

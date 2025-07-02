/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef TGCALLS_DARWIN_VIDEO_SOURCE_H
#define TGCALLS_DARWIN_VIDEO_SOURCE_H
//#ifdef WEBRTC_IOS

#import "base/RTCVideoCapturer.h"

#include "base/RTCMacros.h"
#include "media/base/adapted_video_track_source.h"
#include "rtc_base/timestamp_aligner.h"

RTC_FWD_DECL_OBJC_CLASS(RTC_OBJC_TYPE(RTCVideoFrame));

namespace tgcalls {

class DarwinVideoTrackSource : public rtc::AdaptedVideoTrackSource {
 public:
  DarwinVideoTrackSource();

  // This class can not be used for implementing screen casting. Hopefully, this
  // function will be removed before we add that to iOS/Mac.
  bool is_screencast() const override;

  // Indicates that the encoder should denoise video before encoding it.
  // If it is not set, the default configuration is used which is different
  // depending on video codec.
  absl::optional<bool> needs_denoising() const override;

  SourceState state() const override;

  bool remote() const override;

  void OnCapturedFrame(RTC_OBJC_TYPE(RTCVideoFrame) * frame);
  bool OnCapturedFrame(const webrtc::VideoFrame& frame);

  // Called by RTCVideoSource.
  void OnOutputFormatRequest(int width, int height, int fps);

 private:
  rtc::VideoBroadcaster broadcaster_;
  rtc::TimestampAligner timestamp_aligner_;

};

}  // namespace tgcalls

//#endif //WEBRTC_IOS
#endif

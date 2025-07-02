/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/video_rtp_track_source.h"

#include <stddef.h>

#include <algorithm>

#include "rtc_base/checks.h"

namespace webrtc {

VideoRtpTrackSource::VideoRtpTrackSource(Callback* callback)
    : VideoTrackSource(true /* remote */), callback_(callback) {}

void VideoRtpTrackSource::ClearCallback() {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  callback_ = nullptr;
}

rtc::VideoSourceInterface<VideoFrame>* VideoRtpTrackSource::source() {
  return &broadcaster_;
}
rtc::VideoSinkInterface<VideoFrame>* VideoRtpTrackSource::sink() {
  return &broadcaster_;
}

void VideoRtpTrackSource::BroadcastRecordableEncodedFrame(
    const RecordableEncodedFrame& frame) const {
  MutexLock lock(&mu_);
  for (rtc::VideoSinkInterface<RecordableEncodedFrame>* sink : encoded_sinks_) {
    sink->OnFrame(frame);
  }
}

bool VideoRtpTrackSource::SupportsEncodedOutput() const {
  return true;
}

void VideoRtpTrackSource::GenerateKeyFrame() {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  if (callback_) {
    callback_->OnGenerateKeyFrame();
  }
}

void VideoRtpTrackSource::AddEncodedSink(
    rtc::VideoSinkInterface<RecordableEncodedFrame>* sink) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  RTC_DCHECK(sink);
  size_t size = 0;
  {
    MutexLock lock(&mu_);
    RTC_DCHECK(std::find(encoded_sinks_.begin(), encoded_sinks_.end(), sink) ==
               encoded_sinks_.end());
    encoded_sinks_.push_back(sink);
    size = encoded_sinks_.size();
  }
  if (size == 1 && callback_) {
    callback_->OnEncodedSinkEnabled(true);
  }
}

void VideoRtpTrackSource::RemoveEncodedSink(
    rtc::VideoSinkInterface<RecordableEncodedFrame>* sink) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  size_t size = 0;
  {
    MutexLock lock(&mu_);
    auto it = std::find(encoded_sinks_.begin(), encoded_sinks_.end(), sink);
    if (it != encoded_sinks_.end()) {
      encoded_sinks_.erase(it);
    }
    size = encoded_sinks_.size();
  }
  if (size == 0 && callback_) {
    callback_->OnEncodedSinkEnabled(false);
  }
}

}  // namespace webrtc

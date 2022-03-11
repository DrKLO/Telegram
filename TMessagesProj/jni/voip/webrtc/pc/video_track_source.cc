/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/video_track_source.h"

#include "rtc_base/checks.h"

namespace webrtc {

VideoTrackSource::VideoTrackSource(bool remote)
    : state_(kInitializing), remote_(remote) {
  worker_thread_checker_.Detach();
}

void VideoTrackSource::SetState(SourceState new_state) {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  if (state_ != new_state) {
    state_ = new_state;
    FireOnChanged();
  }
}

void VideoTrackSource::AddOrUpdateSink(
    rtc::VideoSinkInterface<VideoFrame>* sink,
    const rtc::VideoSinkWants& wants) {
  RTC_DCHECK(worker_thread_checker_.IsCurrent());
  source()->AddOrUpdateSink(sink, wants);
}

void VideoTrackSource::RemoveSink(rtc::VideoSinkInterface<VideoFrame>* sink) {
  RTC_DCHECK(worker_thread_checker_.IsCurrent());
  source()->RemoveSink(sink);
}

}  //  namespace webrtc

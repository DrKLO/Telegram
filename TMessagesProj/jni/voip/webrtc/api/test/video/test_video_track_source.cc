/*
 *  Copyright (c) 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/test/video/test_video_track_source.h"

#include <utility>

#include "absl/types/optional.h"
#include "api/media_stream_interface.h"
#include "api/sequence_checker.h"
#include "api/video/video_frame.h"
#include "api/video/video_sink_interface.h"
#include "api/video/video_source_interface.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace test {

TestVideoTrackSource::TestVideoTrackSource(
    bool remote,
    absl::optional<std::string> stream_label)
    : stream_label_(std::move(stream_label)),
      state_(kInitializing),
      remote_(remote) {
  worker_thread_checker_.Detach();
  signaling_thread_checker_.Detach();
}

VideoTrackSourceInterface::SourceState TestVideoTrackSource::state() const {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  return state_;
}

void TestVideoTrackSource::SetState(SourceState new_state) {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  if (state_ != new_state) {
    state_ = new_state;
    FireOnChanged();
  }
}

void TestVideoTrackSource::AddOrUpdateSink(
    rtc::VideoSinkInterface<VideoFrame>* sink,
    const rtc::VideoSinkWants& wants) {
  RTC_DCHECK(worker_thread_checker_.IsCurrent());
  source()->AddOrUpdateSink(sink, wants);
}

void TestVideoTrackSource::RemoveSink(
    rtc::VideoSinkInterface<VideoFrame>* sink) {
  RTC_DCHECK(worker_thread_checker_.IsCurrent());
  source()->RemoveSink(sink);
}

}  // namespace test
}  // namespace webrtc

/*
 *  Copyright 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/video_track.h"

#include <string>
#include <vector>

#include "api/notifier.h"
#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/ref_counted_object.h"

namespace webrtc {

VideoTrack::VideoTrack(const std::string& label,
                       VideoTrackSourceInterface* video_source,
                       rtc::Thread* worker_thread)
    : MediaStreamTrack<VideoTrackInterface>(label),
      worker_thread_(worker_thread),
      video_source_(video_source),
      content_hint_(ContentHint::kNone) {
  video_source_->RegisterObserver(this);
}

VideoTrack::~VideoTrack() {
  video_source_->UnregisterObserver(this);
}

std::string VideoTrack::kind() const {
  return kVideoKind;
}

// AddOrUpdateSink and RemoveSink should be called on the worker
// thread.
void VideoTrack::AddOrUpdateSink(rtc::VideoSinkInterface<VideoFrame>* sink,
                                 const rtc::VideoSinkWants& wants) {
  RTC_DCHECK(worker_thread_->IsCurrent());
  VideoSourceBase::AddOrUpdateSink(sink, wants);
  rtc::VideoSinkWants modified_wants = wants;
  modified_wants.black_frames = !enabled();
  video_source_->AddOrUpdateSink(sink, modified_wants);
}

void VideoTrack::RemoveSink(rtc::VideoSinkInterface<VideoFrame>* sink) {
  RTC_DCHECK(worker_thread_->IsCurrent());
  VideoSourceBase::RemoveSink(sink);
  video_source_->RemoveSink(sink);
}

VideoTrackInterface::ContentHint VideoTrack::content_hint() const {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  return content_hint_;
}

void VideoTrack::set_content_hint(ContentHint hint) {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  if (content_hint_ == hint)
    return;
  content_hint_ = hint;
  Notifier<VideoTrackInterface>::FireOnChanged();
}

bool VideoTrack::set_enabled(bool enable) {
  RTC_DCHECK(signaling_thread_checker_.IsCurrent());
  worker_thread_->Invoke<void>(RTC_FROM_HERE, [enable, this] {
    RTC_DCHECK(worker_thread_->IsCurrent());
    for (auto& sink_pair : sink_pairs()) {
      rtc::VideoSinkWants modified_wants = sink_pair.wants;
      modified_wants.black_frames = !enable;
      video_source_->AddOrUpdateSink(sink_pair.sink, modified_wants);
    }
  });
  return MediaStreamTrack<VideoTrackInterface>::set_enabled(enable);
}

void VideoTrack::OnChanged() {
  RTC_DCHECK(signaling_thread_checker_.IsCurrent());
  if (video_source_->state() == MediaSourceInterface::kEnded) {
    set_state(kEnded);
  } else {
    set_state(kLive);
  }
}

rtc::scoped_refptr<VideoTrack> VideoTrack::Create(
    const std::string& id,
    VideoTrackSourceInterface* source,
    rtc::Thread* worker_thread) {
  rtc::RefCountedObject<VideoTrack>* track =
      new rtc::RefCountedObject<VideoTrack>(id, source, worker_thread);
  return track;
}

}  // namespace webrtc

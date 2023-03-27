/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/base/video_broadcaster.h"

#include <algorithm>
#include <vector>

#include "absl/types/optional.h"
#include "api/video/i420_buffer.h"
#include "api/video/video_rotation.h"
#include "media/base/video_common.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace rtc {

VideoBroadcaster::VideoBroadcaster() = default;
VideoBroadcaster::~VideoBroadcaster() = default;

void VideoBroadcaster::AddOrUpdateSink(
    VideoSinkInterface<webrtc::VideoFrame>* sink,
    const VideoSinkWants& wants) {
  RTC_DCHECK(sink != nullptr);
  webrtc::MutexLock lock(&sinks_and_wants_lock_);
  if (!FindSinkPair(sink)) {
    // `Sink` is a new sink, which didn't receive previous frame.
    previous_frame_sent_to_all_sinks_ = false;

    if (last_constraints_.has_value()) {
      RTC_LOG(LS_INFO) << __func__ << " forwarding stored constraints min_fps "
                       << last_constraints_->min_fps.value_or(-1) << " max_fps "
                       << last_constraints_->max_fps.value_or(-1);
      sink->OnConstraintsChanged(*last_constraints_);
    }
  }
  VideoSourceBase::AddOrUpdateSink(sink, wants);
  UpdateWants();
}

void VideoBroadcaster::RemoveSink(
    VideoSinkInterface<webrtc::VideoFrame>* sink) {
  RTC_DCHECK(sink != nullptr);
  webrtc::MutexLock lock(&sinks_and_wants_lock_);
  VideoSourceBase::RemoveSink(sink);
  UpdateWants();
}

bool VideoBroadcaster::frame_wanted() const {
  webrtc::MutexLock lock(&sinks_and_wants_lock_);
  return !sink_pairs().empty();
}

VideoSinkWants VideoBroadcaster::wants() const {
  webrtc::MutexLock lock(&sinks_and_wants_lock_);
  return current_wants_;
}

void VideoBroadcaster::OnFrame(const webrtc::VideoFrame& frame) {
  webrtc::MutexLock lock(&sinks_and_wants_lock_);
  bool current_frame_was_discarded = false;
  for (auto& sink_pair : sink_pairs()) {
    if (sink_pair.wants.rotation_applied &&
        frame.rotation() != webrtc::kVideoRotation_0) {
      // Calls to OnFrame are not synchronized with changes to the sink wants.
      // When rotation_applied is set to true, one or a few frames may get here
      // with rotation still pending. Protect sinks that don't expect any
      // pending rotation.
      RTC_LOG(LS_VERBOSE) << "Discarding frame with unexpected rotation.";
      sink_pair.sink->OnDiscardedFrame();
      current_frame_was_discarded = true;
      continue;
    }
    if (sink_pair.wants.black_frames) {
      webrtc::VideoFrame black_frame =
          webrtc::VideoFrame::Builder()
              .set_video_frame_buffer(
                  GetBlackFrameBuffer(frame.width(), frame.height()))
              .set_rotation(frame.rotation())
              .set_timestamp_us(frame.timestamp_us())
              .set_id(frame.id())
              .build();
      sink_pair.sink->OnFrame(black_frame);
    } else if (!previous_frame_sent_to_all_sinks_ && frame.has_update_rect()) {
      // Since last frame was not sent to some sinks, no reliable update
      // information is available, so we need to clear the update rect.
      webrtc::VideoFrame copy = frame;
      copy.clear_update_rect();
      sink_pair.sink->OnFrame(copy);
    } else {
      sink_pair.sink->OnFrame(frame);
    }
  }
  previous_frame_sent_to_all_sinks_ = !current_frame_was_discarded;
}

void VideoBroadcaster::OnDiscardedFrame() {
  webrtc::MutexLock lock(&sinks_and_wants_lock_);
  for (auto& sink_pair : sink_pairs()) {
    sink_pair.sink->OnDiscardedFrame();
  }
}

void VideoBroadcaster::ProcessConstraints(
    const webrtc::VideoTrackSourceConstraints& constraints) {
  webrtc::MutexLock lock(&sinks_and_wants_lock_);
  RTC_LOG(LS_INFO) << __func__ << " min_fps "
                   << constraints.min_fps.value_or(-1) << " max_fps "
                   << constraints.max_fps.value_or(-1) << " broadcasting to "
                   << sink_pairs().size() << " sinks.";
  last_constraints_ = constraints;
  for (auto& sink_pair : sink_pairs())
    sink_pair.sink->OnConstraintsChanged(constraints);
}

void VideoBroadcaster::UpdateWants() {
  VideoSinkWants wants;
  wants.rotation_applied = false;
  wants.resolution_alignment = 1;
  wants.aggregates.emplace(VideoSinkWants::Aggregates());
  wants.is_active = false;

  // TODO(webrtc:14451) : I think it makes sense to always
  // "ignore" encoders that are not active. But that would
  // probably require a controlled roll out with a field trials?
  // To play it safe, only ignore inactive encoders is there is an
  // active encoder using the new api (requested_resolution),
  // this means that there is only a behavioural change when using new
  // api.
  bool ignore_inactive_encoders_old_api = false;
  for (auto& sink : sink_pairs()) {
    if (sink.wants.is_active && sink.wants.requested_resolution.has_value()) {
      ignore_inactive_encoders_old_api = true;
      break;
    }
  }

  for (auto& sink : sink_pairs()) {
    if (!sink.wants.is_active &&
        (sink.wants.requested_resolution || ignore_inactive_encoders_old_api)) {
      continue;
    }
    // wants.rotation_applied == ANY(sink.wants.rotation_applied)
    if (sink.wants.rotation_applied) {
      wants.rotation_applied = true;
    }
    // wants.max_pixel_count == MIN(sink.wants.max_pixel_count)
    if (sink.wants.max_pixel_count < wants.max_pixel_count) {
      wants.max_pixel_count = sink.wants.max_pixel_count;
    }
    // Select the minimum requested target_pixel_count, if any, of all sinks so
    // that we don't over utilize the resources for any one.
    // TODO(sprang): Consider using the median instead, since the limit can be
    // expressed by max_pixel_count.
    if (sink.wants.target_pixel_count &&
        (!wants.target_pixel_count ||
         (*sink.wants.target_pixel_count < *wants.target_pixel_count))) {
      wants.target_pixel_count = sink.wants.target_pixel_count;
    }
    // Select the minimum for the requested max framerates.
    if (sink.wants.max_framerate_fps < wants.max_framerate_fps) {
      wants.max_framerate_fps = sink.wants.max_framerate_fps;
    }
    wants.resolution_alignment = cricket::LeastCommonMultiple(
        wants.resolution_alignment, sink.wants.resolution_alignment);

    // Pick MAX(requested_resolution) since the actual can be downscaled
    // in encoder instead.
    if (sink.wants.requested_resolution) {
      if (!wants.requested_resolution) {
        wants.requested_resolution = sink.wants.requested_resolution;
      } else {
        wants.requested_resolution->width =
            std::max(wants.requested_resolution->width,
                     sink.wants.requested_resolution->width);
        wants.requested_resolution->height =
            std::max(wants.requested_resolution->height,
                     sink.wants.requested_resolution->height);
      }
    } else if (sink.wants.is_active) {
      wants.aggregates->any_active_without_requested_resolution = true;
    }

    wants.is_active |= sink.wants.is_active;
  }

  if (wants.target_pixel_count &&
      *wants.target_pixel_count >= wants.max_pixel_count) {
    wants.target_pixel_count.emplace(wants.max_pixel_count);
  }
  current_wants_ = wants;
}

const rtc::scoped_refptr<webrtc::VideoFrameBuffer>&
VideoBroadcaster::GetBlackFrameBuffer(int width, int height) {
  if (!black_frame_buffer_ || black_frame_buffer_->width() != width ||
      black_frame_buffer_->height() != height) {
    rtc::scoped_refptr<webrtc::I420Buffer> buffer =
        webrtc::I420Buffer::Create(width, height);
    webrtc::I420Buffer::SetBlack(buffer.get());
    black_frame_buffer_ = buffer;
  }

  return black_frame_buffer_;
}

}  // namespace rtc

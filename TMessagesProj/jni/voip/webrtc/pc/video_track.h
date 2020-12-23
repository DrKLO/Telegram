/*
 *  Copyright 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_VIDEO_TRACK_H_
#define PC_VIDEO_TRACK_H_

#include <string>

#include "api/media_stream_interface.h"
#include "api/media_stream_track.h"
#include "api/scoped_refptr.h"
#include "api/video/video_frame.h"
#include "api/video/video_sink_interface.h"
#include "api/video/video_source_interface.h"
#include "media/base/video_source_base.h"
#include "rtc_base/thread.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/thread_checker.h"

namespace webrtc {

class VideoTrack : public MediaStreamTrack<VideoTrackInterface>,
                   public rtc::VideoSourceBase,
                   public ObserverInterface {
 public:
  static rtc::scoped_refptr<VideoTrack> Create(
      const std::string& label,
      VideoTrackSourceInterface* source,
      rtc::Thread* worker_thread);

  void AddOrUpdateSink(rtc::VideoSinkInterface<VideoFrame>* sink,
                       const rtc::VideoSinkWants& wants) override;
  void RemoveSink(rtc::VideoSinkInterface<VideoFrame>* sink) override;

  VideoTrackSourceInterface* GetSource() const override {
    return video_source_.get();
  }
  ContentHint content_hint() const override;
  void set_content_hint(ContentHint hint) override;
  bool set_enabled(bool enable) override;
  std::string kind() const override;

 protected:
  VideoTrack(const std::string& id,
             VideoTrackSourceInterface* video_source,
             rtc::Thread* worker_thread);
  ~VideoTrack();

 private:
  // Implements ObserverInterface. Observes |video_source_| state.
  void OnChanged() override;

  rtc::Thread* const worker_thread_;
  rtc::ThreadChecker signaling_thread_checker_;
  rtc::scoped_refptr<VideoTrackSourceInterface> video_source_;
  ContentHint content_hint_ RTC_GUARDED_BY(signaling_thread_checker_);
};

}  // namespace webrtc

#endif  // PC_VIDEO_TRACK_H_

/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_BASE_VIDEO_SOURCE_BASE_H_
#define MEDIA_BASE_VIDEO_SOURCE_BASE_H_

#include <vector>

#include "api/sequence_checker.h"
#include "api/video/video_frame.h"
#include "api/video/video_sink_interface.h"
#include "api/video/video_source_interface.h"
#include "rtc_base/system/no_unique_address.h"

namespace rtc {

// VideoSourceBase is not thread safe. Before using this class, consider using
// VideoSourceBaseGuarded below instead, which is an identical implementation
// but applies a sequence checker to help protect internal state.
// TODO(bugs.webrtc.org/12780): Delete this class.
class VideoSourceBase : public VideoSourceInterface<webrtc::VideoFrame> {
 public:
  VideoSourceBase();
  ~VideoSourceBase() override;
  void AddOrUpdateSink(VideoSinkInterface<webrtc::VideoFrame>* sink,
                       const VideoSinkWants& wants) override;
  void RemoveSink(VideoSinkInterface<webrtc::VideoFrame>* sink) override;

 protected:
  struct SinkPair {
    SinkPair(VideoSinkInterface<webrtc::VideoFrame>* sink, VideoSinkWants wants)
        : sink(sink), wants(wants) {}
    VideoSinkInterface<webrtc::VideoFrame>* sink;
    VideoSinkWants wants;
  };
  SinkPair* FindSinkPair(const VideoSinkInterface<webrtc::VideoFrame>* sink);

  const std::vector<SinkPair>& sink_pairs() const { return sinks_; }

 private:
  std::vector<SinkPair> sinks_;
};

// VideoSourceBaseGuarded assumes that operations related to sinks, occur on the
// same TQ/thread that the object was constructed on.
class VideoSourceBaseGuarded : public VideoSourceInterface<webrtc::VideoFrame> {
 public:
  VideoSourceBaseGuarded();
  ~VideoSourceBaseGuarded() override;

  void AddOrUpdateSink(VideoSinkInterface<webrtc::VideoFrame>* sink,
                       const VideoSinkWants& wants) override;
  void RemoveSink(VideoSinkInterface<webrtc::VideoFrame>* sink) override;

 protected:
  struct SinkPair {
    SinkPair(VideoSinkInterface<webrtc::VideoFrame>* sink, VideoSinkWants wants)
        : sink(sink), wants(wants) {}
    VideoSinkInterface<webrtc::VideoFrame>* sink;
    VideoSinkWants wants;
  };

  SinkPair* FindSinkPair(const VideoSinkInterface<webrtc::VideoFrame>* sink);
  const std::vector<SinkPair>& sink_pairs() const;

  // Keep the `source_sequence_` checker protected to allow sub classes the
  // ability to call Detach() if/when appropriate.
  RTC_NO_UNIQUE_ADDRESS webrtc::SequenceChecker source_sequence_;

 private:
  std::vector<SinkPair> sinks_ RTC_GUARDED_BY(&source_sequence_);
};

}  // namespace rtc

#endif  // MEDIA_BASE_VIDEO_SOURCE_BASE_H_

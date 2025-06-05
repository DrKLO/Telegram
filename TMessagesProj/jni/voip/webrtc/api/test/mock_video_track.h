/*
 *  Copyright 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_MOCK_VIDEO_TRACK_H_
#define API_TEST_MOCK_VIDEO_TRACK_H_

#include <string>

#include "api/media_stream_interface.h"
#include "api/scoped_refptr.h"
#include "rtc_base/ref_counted_object.h"
#include "test/gmock.h"

namespace webrtc {

class MockVideoTrack
    : public rtc::RefCountedObject<webrtc::VideoTrackInterface> {
 public:
  static rtc::scoped_refptr<MockVideoTrack> Create() {
    return rtc::scoped_refptr<MockVideoTrack>(new MockVideoTrack());
  }

  // NotifierInterface
  MOCK_METHOD(void,
              RegisterObserver,
              (ObserverInterface * observer),
              (override));
  MOCK_METHOD(void,
              UnregisterObserver,
              (ObserverInterface * observer),
              (override));

  // MediaStreamTrackInterface
  MOCK_METHOD(std::string, kind, (), (const, override));
  MOCK_METHOD(std::string, id, (), (const, override));
  MOCK_METHOD(bool, enabled, (), (const, override));
  MOCK_METHOD(bool, set_enabled, (bool enable), (override));
  MOCK_METHOD(TrackState, state, (), (const, override));

  // VideoSourceInterface
  MOCK_METHOD(void,
              AddOrUpdateSink,
              (rtc::VideoSinkInterface<VideoFrame> * sink,
               const rtc::VideoSinkWants& wants),
              (override));
  // RemoveSink must guarantee that at the time the method returns,
  // there is no current and no future calls to VideoSinkInterface::OnFrame.
  MOCK_METHOD(void,
              RemoveSink,
              (rtc::VideoSinkInterface<VideoFrame> * sink),
              (override));

  // VideoTrackInterface
  MOCK_METHOD(VideoTrackSourceInterface*, GetSource, (), (const, override));

  MOCK_METHOD(ContentHint, content_hint, (), (const, override));
  MOCK_METHOD(void, set_content_hint, (ContentHint hint), (override));
};

}  // namespace webrtc

#endif  // API_TEST_MOCK_VIDEO_TRACK_H_

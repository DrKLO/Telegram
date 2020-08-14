/*
 *  Copyright 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file includes proxy classes for tracks. The purpose is
// to make sure tracks are only accessed from the signaling thread.

#ifndef API_MEDIA_STREAM_TRACK_PROXY_H_
#define API_MEDIA_STREAM_TRACK_PROXY_H_

#include <string>

#include "api/media_stream_interface.h"
#include "api/proxy.h"

namespace webrtc {

// TODO(deadbeef): Move this to .cc file and out of api/. What threads methods
// are called on is an implementation detail.

BEGIN_SIGNALING_PROXY_MAP(AudioTrack)
PROXY_SIGNALING_THREAD_DESTRUCTOR()
PROXY_CONSTMETHOD0(std::string, kind)
PROXY_CONSTMETHOD0(std::string, id)
PROXY_CONSTMETHOD0(TrackState, state)
PROXY_CONSTMETHOD0(bool, enabled)
PROXY_CONSTMETHOD0(AudioSourceInterface*, GetSource)
PROXY_METHOD1(void, AddSink, AudioTrackSinkInterface*)
PROXY_METHOD1(void, RemoveSink, AudioTrackSinkInterface*)
PROXY_METHOD1(bool, GetSignalLevel, int*)
PROXY_METHOD0(rtc::scoped_refptr<AudioProcessorInterface>, GetAudioProcessor)
PROXY_METHOD1(bool, set_enabled, bool)
PROXY_METHOD1(void, RegisterObserver, ObserverInterface*)
PROXY_METHOD1(void, UnregisterObserver, ObserverInterface*)
END_PROXY_MAP()

BEGIN_PROXY_MAP(VideoTrack)
PROXY_SIGNALING_THREAD_DESTRUCTOR()
PROXY_CONSTMETHOD0(std::string, kind)
PROXY_CONSTMETHOD0(std::string, id)
PROXY_CONSTMETHOD0(TrackState, state)
PROXY_CONSTMETHOD0(bool, enabled)
PROXY_METHOD1(bool, set_enabled, bool)
PROXY_CONSTMETHOD0(ContentHint, content_hint)
PROXY_METHOD1(void, set_content_hint, ContentHint)
PROXY_WORKER_METHOD2(void,
                     AddOrUpdateSink,
                     rtc::VideoSinkInterface<VideoFrame>*,
                     const rtc::VideoSinkWants&)
PROXY_WORKER_METHOD1(void, RemoveSink, rtc::VideoSinkInterface<VideoFrame>*)
PROXY_CONSTMETHOD0(VideoTrackSourceInterface*, GetSource)

PROXY_METHOD1(void, RegisterObserver, ObserverInterface*)
PROXY_METHOD1(void, UnregisterObserver, ObserverInterface*)
END_PROXY_MAP()

}  // namespace webrtc

#endif  // API_MEDIA_STREAM_TRACK_PROXY_H_

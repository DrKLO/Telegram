/*
 *  Copyright 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/video_track_source_proxy.h"

#include "api/media_stream_interface.h"
#include "api/scoped_refptr.h"
#include "api/video_track_source_proxy_factory.h"
#include "rtc_base/thread.h"

namespace webrtc {

rtc::scoped_refptr<VideoTrackSourceInterface> CreateVideoTrackSourceProxy(
    rtc::Thread* signaling_thread,
    rtc::Thread* worker_thread,
    VideoTrackSourceInterface* source) {
  return VideoTrackSourceProxy::Create(
      signaling_thread, worker_thread,
      rtc::scoped_refptr<VideoTrackSourceInterface>(source));
}

}  // namespace webrtc

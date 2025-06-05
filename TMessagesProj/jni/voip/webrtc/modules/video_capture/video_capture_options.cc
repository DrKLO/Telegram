/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_capture/video_capture_options.h"

#if defined(WEBRTC_USE_PIPEWIRE)
#include "modules/video_capture/linux/pipewire_session.h"
#endif

namespace webrtc {

VideoCaptureOptions::VideoCaptureOptions() {}
VideoCaptureOptions::VideoCaptureOptions(const VideoCaptureOptions& options) =
    default;
VideoCaptureOptions::VideoCaptureOptions(VideoCaptureOptions&& options) =
    default;
VideoCaptureOptions::~VideoCaptureOptions() {}

VideoCaptureOptions& VideoCaptureOptions::operator=(
    const VideoCaptureOptions& options) = default;
VideoCaptureOptions& VideoCaptureOptions::operator=(
    VideoCaptureOptions&& options) = default;

void VideoCaptureOptions::Init(Callback* callback) {
#if defined(WEBRTC_USE_PIPEWIRE)
  if (allow_pipewire_) {
    pipewire_session_ =
        rtc::make_ref_counted<videocapturemodule::PipeWireSession>();
    pipewire_session_->Init(callback, pipewire_fd_);
    return;
  }
#endif
#if defined(WEBRTC_LINUX)
  if (!allow_v4l2_)
    callback->OnInitialized(Status::UNAVAILABLE);
  else
#endif
    callback->OnInitialized(Status::SUCCESS);
}

#if defined(WEBRTC_USE_PIPEWIRE)
rtc::scoped_refptr<videocapturemodule::PipeWireSession>
VideoCaptureOptions::pipewire_session() {
  return pipewire_session_;
}
#endif

}  // namespace webrtc

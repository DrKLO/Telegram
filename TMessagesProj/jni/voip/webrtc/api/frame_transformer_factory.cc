/*
 *  Copyright 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/frame_transformer_factory.h"

#include "audio/channel_send_frame_transformer_delegate.h"
#include "modules/rtp_rtcp/source/rtp_sender_video_frame_transformer_delegate.h"

namespace webrtc {

std::unique_ptr<TransformableVideoFrameInterface> CreateVideoSenderFrame() {
  RTC_CHECK_NOTREACHED();
  return nullptr;
}

std::unique_ptr<TransformableVideoFrameInterface> CreateVideoReceiverFrame() {
  RTC_CHECK_NOTREACHED();
  return nullptr;
}

std::unique_ptr<TransformableAudioFrameInterface> CloneAudioFrame(
    TransformableAudioFrameInterface* original) {
  // At the moment, only making sender frames is supported.
  return CloneSenderAudioFrame(original);
}

std::unique_ptr<TransformableVideoFrameInterface> CloneVideoFrame(
    TransformableVideoFrameInterface* original) {
  // At the moment, only making sender frames from receiver frames is supported.
  return CloneSenderVideoFrame(original);
}

}  // namespace webrtc

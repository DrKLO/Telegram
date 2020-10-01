/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video/video_stream_encoder_create.h"

#include <memory>

#include "video/adaptation/overuse_frame_detector.h"
#include "video/video_stream_encoder.h"

namespace webrtc {

std::unique_ptr<VideoStreamEncoderInterface> CreateVideoStreamEncoder(
    Clock* clock,
    TaskQueueFactory* task_queue_factory,
    uint32_t number_of_cores,
    VideoStreamEncoderObserver* encoder_stats_observer,
    const VideoStreamEncoderSettings& settings) {
  return std::make_unique<VideoStreamEncoder>(
      clock, number_of_cores, encoder_stats_observer, settings,
      std::make_unique<OveruseFrameDetector>(encoder_stats_observer),
      task_queue_factory);
}

}  // namespace webrtc

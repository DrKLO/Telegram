/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VIDEO_VIDEO_STREAM_ENCODER_CREATE_H_
#define API_VIDEO_VIDEO_STREAM_ENCODER_CREATE_H_

#include <stdint.h>

#include <memory>

#include "api/task_queue/task_queue_factory.h"
#include "api/video/video_frame.h"
#include "api/video/video_sink_interface.h"
#include "api/video/video_stream_encoder_interface.h"
#include "api/video/video_stream_encoder_observer.h"
#include "api/video/video_stream_encoder_settings.h"

namespace webrtc {
// TODO(srte): Find a way to avoid this forward declaration.
class Clock;

std::unique_ptr<VideoStreamEncoderInterface> CreateVideoStreamEncoder(
    Clock* clock,
    TaskQueueFactory* task_queue_factory,
    uint32_t number_of_cores,
    VideoStreamEncoderObserver* encoder_stats_observer,
    const VideoStreamEncoderSettings& settings);
}  // namespace webrtc

#endif  // API_VIDEO_VIDEO_STREAM_ENCODER_CREATE_H_

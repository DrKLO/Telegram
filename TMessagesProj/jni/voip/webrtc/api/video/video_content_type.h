/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VIDEO_VIDEO_CONTENT_TYPE_H_
#define API_VIDEO_VIDEO_CONTENT_TYPE_H_

#include <stdint.h>

namespace webrtc {

enum class VideoContentType : uint8_t {
  UNSPECIFIED = 0,
  SCREENSHARE = 1,
};

namespace videocontenttypehelpers {
bool SetExperimentId(VideoContentType* content_type, uint8_t experiment_id);
bool SetSimulcastId(VideoContentType* content_type, uint8_t simulcast_id);

uint8_t GetExperimentId(const VideoContentType& content_type);
uint8_t GetSimulcastId(const VideoContentType& content_type);

bool IsScreenshare(const VideoContentType& content_type);

bool IsValidContentType(uint8_t value);

const char* ToString(const VideoContentType& content_type);
}  // namespace videocontenttypehelpers

}  // namespace webrtc

#endif  // API_VIDEO_VIDEO_CONTENT_TYPE_H_

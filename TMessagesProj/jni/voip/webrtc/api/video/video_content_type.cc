/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video/video_content_type.h"

// VideoContentType stored as a single byte, which is sent over the network.
// Structure:
//
//  0 1 2 3 4 5 6 7
// +---------------+
// |r r e e e s s c|
//
// where:
// r - reserved bits.
// e - 3-bit number of an experiment group counted from 1. 0 means there's no
// experiment ongoing.
// s - 2-bit simulcast stream id or spatial layer, counted from 1. 0 means that
// no simulcast information is set.
// c - content type. 0 means real-time video, 1 means screenshare.
//

namespace webrtc {
namespace videocontenttypehelpers {

namespace {
static constexpr uint8_t kScreenshareBitsSize = 1;
static constexpr uint8_t kScreenshareBitsMask =
    (1u << kScreenshareBitsSize) - 1;

static constexpr uint8_t kSimulcastShift = 1;
static constexpr uint8_t kSimulcastBitsSize = 2;
static constexpr uint8_t kSimulcastBitsMask = ((1u << kSimulcastBitsSize) - 1)
                                              << kSimulcastShift;  // 0b00000110

static constexpr uint8_t kExperimentShift = 3;
static constexpr uint8_t kExperimentBitsSize = 3;
static constexpr uint8_t kExperimentBitsMask =
    ((1u << kExperimentBitsSize) - 1) << kExperimentShift;  // 0b00111000

static constexpr uint8_t kTotalBitsSize =
    kScreenshareBitsSize + kSimulcastBitsSize + kExperimentBitsSize;
}  // namespace

bool SetExperimentId(VideoContentType* content_type, uint8_t experiment_id) {
  // Store in bits 2-4.
  if (experiment_id >= (1 << kExperimentBitsSize))
    return false;
  *content_type = static_cast<VideoContentType>(
      (static_cast<uint8_t>(*content_type) & ~kExperimentBitsMask) |
      ((experiment_id << kExperimentShift) & kExperimentBitsMask));
  return true;
}

bool SetSimulcastId(VideoContentType* content_type, uint8_t simulcast_id) {
  // Store in bits 5-6.
  if (simulcast_id >= (1 << kSimulcastBitsSize))
    return false;
  *content_type = static_cast<VideoContentType>(
      (static_cast<uint8_t>(*content_type) & ~kSimulcastBitsMask) |
      ((simulcast_id << kSimulcastShift) & kSimulcastBitsMask));
  return true;
}

uint8_t GetExperimentId(const VideoContentType& content_type) {
  return (static_cast<uint8_t>(content_type) & kExperimentBitsMask) >>
         kExperimentShift;
}
uint8_t GetSimulcastId(const VideoContentType& content_type) {
  return (static_cast<uint8_t>(content_type) & kSimulcastBitsMask) >>
         kSimulcastShift;
}

bool IsScreenshare(const VideoContentType& content_type) {
  return (static_cast<uint8_t>(content_type) & kScreenshareBitsMask) > 0;
}

bool IsValidContentType(uint8_t value) {
  // Any 6-bit value is allowed.
  return value < (1 << kTotalBitsSize);
}

const char* ToString(const VideoContentType& content_type) {
  return IsScreenshare(content_type) ? "screen" : "realtime";
}
}  // namespace videocontenttypehelpers
}  // namespace webrtc

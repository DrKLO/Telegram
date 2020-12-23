/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_TIMESTAMP_MAP_H_
#define MODULES_VIDEO_CODING_TIMESTAMP_MAP_H_

#include <memory>

namespace webrtc {

struct VCMFrameInformation;

class VCMTimestampMap {
 public:
  explicit VCMTimestampMap(size_t capacity);
  ~VCMTimestampMap();

  void Add(uint32_t timestamp, VCMFrameInformation* data);
  VCMFrameInformation* Pop(uint32_t timestamp);
  size_t Size() const;

 private:
  struct TimestampDataTuple {
    uint32_t timestamp;
    VCMFrameInformation* data;
  };
  bool IsEmpty() const;

  std::unique_ptr<TimestampDataTuple[]> ring_buffer_;
  const size_t capacity_;
  size_t next_add_idx_;
  size_t next_pop_idx_;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_TIMESTAMP_MAP_H_

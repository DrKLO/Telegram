/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VIDEO_VIDEO_FRAME_METADATA_H_
#define API_VIDEO_VIDEO_FRAME_METADATA_H_

#include <cstdint>

#include "absl/container/inlined_vector.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/transport/rtp/dependency_descriptor.h"

namespace webrtc {

struct RTPVideoHeader;

// A subset of metadata from the RTP video header, exposed in insertable streams
// API.
class VideoFrameMetadata {
 public:
  explicit VideoFrameMetadata(const RTPVideoHeader& header);
  VideoFrameMetadata(const VideoFrameMetadata&) = default;
  VideoFrameMetadata& operator=(const VideoFrameMetadata&) = default;

  uint16_t GetWidth() const { return width_; }
  uint16_t GetHeight() const { return height_; }
  absl::optional<int64_t> GetFrameId() const { return frame_id_; }
  int GetSpatialIndex() const { return spatial_index_; }
  int GetTemporalIndex() const { return temporal_index_; }

  rtc::ArrayView<const int64_t> GetFrameDependencies() const {
    return frame_dependencies_;
  }

  rtc::ArrayView<const DecodeTargetIndication> GetDecodeTargetIndications()
      const {
    return decode_target_indications_;
  }

 private:
  int16_t width_;
  int16_t height_;
  absl::optional<int64_t> frame_id_;
  int spatial_index_ = 0;
  int temporal_index_ = 0;
  absl::InlinedVector<int64_t, 5> frame_dependencies_;
  absl::InlinedVector<DecodeTargetIndication, 10> decode_target_indications_;
};
}  // namespace webrtc

#endif  // API_VIDEO_VIDEO_FRAME_METADATA_H_

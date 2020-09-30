/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_UTILITY_DECODED_FRAMES_HISTORY_H_
#define MODULES_VIDEO_CODING_UTILITY_DECODED_FRAMES_HISTORY_H_

#include <stdint.h>

#include <bitset>
#include <vector>

#include "absl/types/optional.h"
#include "api/video/encoded_frame.h"

namespace webrtc {
namespace video_coding {

class DecodedFramesHistory {
 public:
  // window_size - how much frames back to the past are actually remembered.
  explicit DecodedFramesHistory(size_t window_size);
  ~DecodedFramesHistory();
  // Called for each decoded frame. Assumes picture id's are non-decreasing.
  void InsertDecoded(const VideoLayerFrameId& frameid, uint32_t timestamp);
  // Query if the following (picture_id, spatial_id) pair was inserted before.
  // Should be at most less by window_size-1 than the last inserted picture id.
  bool WasDecoded(const VideoLayerFrameId& frameid);

  void Clear();

  absl::optional<VideoLayerFrameId> GetLastDecodedFrameId();
  absl::optional<uint32_t> GetLastDecodedFrameTimestamp();

 private:
  struct LayerHistory {
    LayerHistory();
    ~LayerHistory();
    // Cyclic bitset buffer. Stores last known |window_size| bits.
    std::vector<bool> buffer;
    absl::optional<int64_t> last_picture_id;
  };

  int PictureIdToIndex(int64_t frame_id) const;

  const int window_size_;
  std::vector<LayerHistory> layers_;
  absl::optional<VideoLayerFrameId> last_decoded_frame_;
  absl::optional<uint32_t> last_decoded_frame_timestamp_;
};

}  // namespace video_coding
}  // namespace webrtc
#endif  // MODULES_VIDEO_CODING_UTILITY_DECODED_FRAMES_HISTORY_H_

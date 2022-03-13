/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_FRAME_BUFFER3_H_
#define MODULES_VIDEO_CODING_FRAME_BUFFER3_H_

#include <map>
#include <memory>
#include <utility>

#include "absl/container/inlined_vector.h"
#include "absl/types/optional.h"
#include "api/units/timestamp.h"
#include "api/video/encoded_frame.h"
#include "modules/video_coding/utility/decoded_frames_history.h"

namespace webrtc {
// The high level idea of the FrameBuffer is to order frames received from the
// network into a decodable stream. Frames are order by frame ID, and grouped
// into temporal units by timestamp. A temporal unit is decodable after all
// referenced frames outside the unit has been decoded, and a temporal unit is
// continuous if all referenced frames are directly or indirectly decodable.
// The FrameBuffer is thread-unsafe.
class FrameBuffer {
 public:
  // The `max_size` determines the maxmimum number of frames the buffer will
  // store, and max_decode_history determines how far back (by frame ID) the
  // buffer will store if a frame was decoded or not.
  FrameBuffer(int max_size, int max_decode_history);
  FrameBuffer(const FrameBuffer&) = delete;
  FrameBuffer& operator=(const FrameBuffer&) = delete;
  ~FrameBuffer() = default;

  // Inserted frames may only reference backwards, and must have no duplicate
  // references.
  void InsertFrame(std::unique_ptr<EncodedFrame> frame);

  // Mark all frames belonging to the next decodable temporal unit as decoded
  // and returns them.
  absl::InlinedVector<std::unique_ptr<EncodedFrame>, 4>
  ExtractNextDecodableTemporalUnit();

  // Drop all frames in the next decodable unit.
  void DropNextDecodableTemporalUnit();

  absl::optional<int64_t> LastContinuousFrameId() const;
  absl::optional<int64_t> LastContinuousTemporalUnitFrameId() const;
  absl::optional<uint32_t> NextDecodableTemporalUnitRtpTimestamp() const;
  absl::optional<uint32_t> LastDecodableTemporalUnitRtpTimestamp() const;

  int GetTotalNumberOfContinuousTemporalUnits() const;
  int GetTotalNumberOfDroppedFrames() const;

 private:
  struct FrameInfo {
    std::unique_ptr<EncodedFrame> encoded_frame;
    bool continuous = false;
  };

  using FrameMap = std::map<int64_t, FrameInfo>;
  using FrameIterator = FrameMap::iterator;

  struct TemporalUnit {
    // Both first and last are inclusive.
    FrameIterator first_frame;
    FrameIterator last_frame;
  };

  bool IsContinuous(const FrameIterator& it) const;
  void PropagateContinuity(const FrameIterator& frame_it);
  void FindNextAndLastDecodableTemporalUnit();
  void Clear();

  const bool legacy_frame_id_jump_behavior_;
  const size_t max_size_;
  FrameMap frames_;
  absl::optional<TemporalUnit> next_decodable_temporal_unit_;
  absl::optional<uint32_t> last_decodable_temporal_unit_timestamp_;
  absl::optional<int64_t> last_continuous_frame_id_;
  absl::optional<int64_t> last_continuous_temporal_unit_frame_id_;
  video_coding::DecodedFramesHistory decoded_frame_history_;

  int num_continuous_temporal_units_ = 0;
  int num_dropped_frames_ = 0;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_FRAME_BUFFER3_H_

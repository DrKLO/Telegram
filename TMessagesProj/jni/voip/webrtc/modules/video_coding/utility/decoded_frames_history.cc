/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/utility/decoded_frames_history.h"

#include <algorithm>

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace video_coding {

DecodedFramesHistory::DecodedFramesHistory(size_t window_size)
    : buffer_(window_size) {}

DecodedFramesHistory::~DecodedFramesHistory() = default;

void DecodedFramesHistory::InsertDecoded(int64_t frame_id, uint32_t timestamp) {
  last_decoded_frame_ = frame_id;
  last_decoded_frame_timestamp_ = timestamp;
  int new_index = FrameIdToIndex(frame_id);

  RTC_DCHECK(last_frame_id_ < frame_id);

  // Clears expired values from the cyclic buffer_.
  if (last_frame_id_) {
    int64_t id_jump = frame_id - *last_frame_id_;
    int last_index = FrameIdToIndex(*last_frame_id_);

    if (id_jump >= static_cast<int64_t>(buffer_.size())) {
      std::fill(buffer_.begin(), buffer_.end(), false);
    } else if (new_index > last_index) {
      std::fill(buffer_.begin() + last_index + 1, buffer_.begin() + new_index,
                false);
    } else {
      std::fill(buffer_.begin() + last_index + 1, buffer_.end(), false);
      std::fill(buffer_.begin(), buffer_.begin() + new_index, false);
    }
  }

  buffer_[new_index] = true;
  last_frame_id_ = frame_id;
}

bool DecodedFramesHistory::WasDecoded(int64_t frame_id) const {
  if (!last_frame_id_)
    return false;

  // Reference to the picture_id out of the stored should happen.
  if (frame_id <= *last_frame_id_ - static_cast<int64_t>(buffer_.size())) {
    RTC_LOG(LS_WARNING) << "Referencing a frame out of the window. "
                           "Assuming it was undecoded to avoid artifacts.";
    return false;
  }

  if (frame_id > last_frame_id_)
    return false;

  return buffer_[FrameIdToIndex(frame_id)];
}

void DecodedFramesHistory::Clear() {
  last_decoded_frame_timestamp_.reset();
  last_decoded_frame_.reset();
  std::fill(buffer_.begin(), buffer_.end(), false);
  last_frame_id_.reset();
}

absl::optional<int64_t> DecodedFramesHistory::GetLastDecodedFrameId() const {
  return last_decoded_frame_;
}

absl::optional<uint32_t> DecodedFramesHistory::GetLastDecodedFrameTimestamp()
    const {
  return last_decoded_frame_timestamp_;
}

int DecodedFramesHistory::FrameIdToIndex(int64_t frame_id) const {
  int m = frame_id % buffer_.size();
  return m >= 0 ? m : m + buffer_.size();
}

}  // namespace video_coding
}  // namespace webrtc

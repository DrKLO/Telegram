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

DecodedFramesHistory::LayerHistory::LayerHistory() = default;
DecodedFramesHistory::LayerHistory::~LayerHistory() = default;

DecodedFramesHistory::DecodedFramesHistory(size_t window_size)
    : window_size_(window_size) {}

DecodedFramesHistory::~DecodedFramesHistory() = default;

void DecodedFramesHistory::InsertDecoded(const VideoLayerFrameId& frameid,
                                         uint32_t timestamp) {
  last_decoded_frame_ = frameid;
  last_decoded_frame_timestamp_ = timestamp;
  if (static_cast<int>(layers_.size()) < frameid.spatial_layer + 1) {
    size_t old_size = layers_.size();
    layers_.resize(frameid.spatial_layer + 1);

    for (size_t i = old_size; i < layers_.size(); ++i)
      layers_[i].buffer.resize(window_size_);

    layers_[frameid.spatial_layer].last_picture_id = frameid.picture_id;
    layers_[frameid.spatial_layer]
        .buffer[PictureIdToIndex(frameid.picture_id)] = true;
    return;
  }

  int new_index = PictureIdToIndex(frameid.picture_id);
  LayerHistory& history = layers_[frameid.spatial_layer];

  RTC_DCHECK(history.last_picture_id < frameid.picture_id);

  // Clears expired values from the cyclic buffer.
  if (history.last_picture_id) {
    int64_t id_jump = frameid.picture_id - *history.last_picture_id;
    int last_index = PictureIdToIndex(*history.last_picture_id);

    if (id_jump >= window_size_) {
      std::fill(history.buffer.begin(), history.buffer.end(), false);
    } else if (new_index > last_index) {
      std::fill(history.buffer.begin() + last_index + 1,
                history.buffer.begin() + new_index, false);
    } else {
      std::fill(history.buffer.begin() + last_index + 1, history.buffer.end(),
                false);
      std::fill(history.buffer.begin(), history.buffer.begin() + new_index,
                false);
    }
  }

  history.buffer[new_index] = true;
  history.last_picture_id = frameid.picture_id;
}

bool DecodedFramesHistory::WasDecoded(const VideoLayerFrameId& frameid) {
  // Unseen before spatial layer.
  if (static_cast<int>(layers_.size()) < frameid.spatial_layer + 1)
    return false;

  LayerHistory& history = layers_[frameid.spatial_layer];

  if (!history.last_picture_id)
    return false;

  // Reference to the picture_id out of the stored history should happen.
  if (frameid.picture_id <= *history.last_picture_id - window_size_) {
    RTC_LOG(LS_WARNING) << "Referencing a frame out of the history window. "
                           "Assuming it was undecoded to avoid artifacts.";
    return false;
  }

  if (frameid.picture_id > history.last_picture_id)
    return false;

  return history.buffer[PictureIdToIndex(frameid.picture_id)];
}

void DecodedFramesHistory::Clear() {
  layers_.clear();
  last_decoded_frame_timestamp_.reset();
  last_decoded_frame_.reset();
}

absl::optional<VideoLayerFrameId>
DecodedFramesHistory::GetLastDecodedFrameId() {
  return last_decoded_frame_;
}

absl::optional<uint32_t> DecodedFramesHistory::GetLastDecodedFrameTimestamp() {
  return last_decoded_frame_timestamp_;
}

int DecodedFramesHistory::PictureIdToIndex(int64_t frame_id) const {
  int m = frame_id % window_size_;
  return m >= 0 ? m : m + window_size_;
}

}  // namespace video_coding
}  // namespace webrtc

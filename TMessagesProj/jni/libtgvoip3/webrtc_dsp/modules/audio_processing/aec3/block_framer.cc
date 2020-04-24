/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/block_framer.h"

#include <algorithm>

#include "modules/audio_processing/aec3/aec3_common.h"
#include "rtc_base/checks.h"

namespace webrtc {

BlockFramer::BlockFramer(size_t num_bands)
    : num_bands_(num_bands),
      buffer_(num_bands_, std::vector<float>(kBlockSize, 0.f)) {}

BlockFramer::~BlockFramer() = default;

// All the constants are chosen so that the buffer is either empty or has enough
// samples for InsertBlockAndExtractSubFrame to produce a frame. In order to
// achieve this, the InsertBlockAndExtractSubFrame and InsertBlock methods need
// to be called in the correct order.
void BlockFramer::InsertBlock(const std::vector<std::vector<float>>& block) {
  RTC_DCHECK_EQ(num_bands_, block.size());
  for (size_t i = 0; i < num_bands_; ++i) {
    RTC_DCHECK_EQ(kBlockSize, block[i].size());
    RTC_DCHECK_EQ(0, buffer_[i].size());
    buffer_[i].insert(buffer_[i].begin(), block[i].begin(), block[i].end());
  }
}

void BlockFramer::InsertBlockAndExtractSubFrame(
    const std::vector<std::vector<float>>& block,
    std::vector<rtc::ArrayView<float>>* sub_frame) {
  RTC_DCHECK(sub_frame);
  RTC_DCHECK_EQ(num_bands_, block.size());
  RTC_DCHECK_EQ(num_bands_, sub_frame->size());
  for (size_t i = 0; i < num_bands_; ++i) {
    RTC_DCHECK_LE(kSubFrameLength, buffer_[i].size() + kBlockSize);
    RTC_DCHECK_EQ(kBlockSize, block[i].size());
    RTC_DCHECK_GE(kBlockSize, buffer_[i].size());
    RTC_DCHECK_EQ(kSubFrameLength, (*sub_frame)[i].size());
    const int samples_to_frame = kSubFrameLength - buffer_[i].size();
    std::copy(buffer_[i].begin(), buffer_[i].end(), (*sub_frame)[i].begin());
    std::copy(block[i].begin(), block[i].begin() + samples_to_frame,
              (*sub_frame)[i].begin() + buffer_[i].size());
    buffer_[i].clear();
    buffer_[i].insert(buffer_[i].begin(), block[i].begin() + samples_to_frame,
                      block[i].end());
  }
}

}  // namespace webrtc

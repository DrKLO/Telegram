/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/frame_blocker.h"

#include "modules/audio_processing/aec3/aec3_common.h"
#include "rtc_base/checks.h"

namespace webrtc {

FrameBlocker::FrameBlocker(size_t num_bands)
    : num_bands_(num_bands), buffer_(num_bands_) {
  for (auto& b : buffer_) {
    b.reserve(kBlockSize);
    RTC_DCHECK(b.empty());
  }
}

FrameBlocker::~FrameBlocker() = default;

void FrameBlocker::InsertSubFrameAndExtractBlock(
    const std::vector<rtc::ArrayView<float>>& sub_frame,
    std::vector<std::vector<float>>* block) {
  RTC_DCHECK(block);
  RTC_DCHECK_EQ(num_bands_, block->size());
  RTC_DCHECK_EQ(num_bands_, sub_frame.size());
  for (size_t i = 0; i < num_bands_; ++i) {
    RTC_DCHECK_GE(kBlockSize - 16, buffer_[i].size());
    RTC_DCHECK_EQ(kBlockSize, (*block)[i].size());
    RTC_DCHECK_EQ(kSubFrameLength, sub_frame[i].size());
    const int samples_to_block = kBlockSize - buffer_[i].size();
    (*block)[i].clear();
    (*block)[i].insert((*block)[i].begin(), buffer_[i].begin(),
                       buffer_[i].end());
    (*block)[i].insert((*block)[i].begin() + buffer_[i].size(),
                       sub_frame[i].begin(),
                       sub_frame[i].begin() + samples_to_block);
    buffer_[i].clear();
    buffer_[i].insert(buffer_[i].begin(),
                      sub_frame[i].begin() + samples_to_block,
                      sub_frame[i].end());
  }
}

bool FrameBlocker::IsBlockAvailable() const {
  return kBlockSize == buffer_[0].size();
}

void FrameBlocker::ExtractBlock(std::vector<std::vector<float>>* block) {
  RTC_DCHECK(block);
  RTC_DCHECK_EQ(num_bands_, block->size());
  RTC_DCHECK(IsBlockAvailable());
  for (size_t i = 0; i < num_bands_; ++i) {
    RTC_DCHECK_EQ(kBlockSize, buffer_[i].size());
    RTC_DCHECK_EQ(kBlockSize, (*block)[i].size());
    (*block)[i].clear();
    (*block)[i].insert((*block)[i].begin(), buffer_[i].begin(),
                       buffer_[i].end());
    buffer_[i].clear();
  }
}

}  // namespace webrtc

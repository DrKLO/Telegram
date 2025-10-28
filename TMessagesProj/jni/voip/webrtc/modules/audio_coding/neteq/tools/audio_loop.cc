/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/audio_loop.h"

#include <stdio.h>
#include <string.h>

#include "absl/strings/string_view.h"

namespace webrtc {
namespace test {

bool AudioLoop::Init(absl::string_view file_name,
                     size_t max_loop_length_samples,
                     size_t block_length_samples) {
  FILE* fp = fopen(std::string(file_name).c_str(), "rb");
  if (!fp)
    return false;

  audio_array_.reset(
      new int16_t[max_loop_length_samples + block_length_samples]);
  size_t samples_read =
      fread(audio_array_.get(), sizeof(int16_t), max_loop_length_samples, fp);
  fclose(fp);

  // Block length must be shorter than the loop length.
  if (block_length_samples > samples_read)
    return false;

  // Add an extra block length of samples to the end of the array, starting
  // over again from the beginning of the array. This is done to simplify
  // the reading process when reading over the end of the loop.
  memcpy(&audio_array_[samples_read], audio_array_.get(),
         block_length_samples * sizeof(int16_t));

  loop_length_samples_ = samples_read;
  block_length_samples_ = block_length_samples;
  next_index_ = 0;
  return true;
}

rtc::ArrayView<const int16_t> AudioLoop::GetNextBlock() {
  // Check that the AudioLoop is initialized.
  if (block_length_samples_ == 0)
    return rtc::ArrayView<const int16_t>();

  const int16_t* output_ptr = &audio_array_[next_index_];
  next_index_ = (next_index_ + block_length_samples_) % loop_length_samples_;
  return rtc::ArrayView<const int16_t>(output_ptr, block_length_samples_);
}

}  // namespace test
}  // namespace webrtc

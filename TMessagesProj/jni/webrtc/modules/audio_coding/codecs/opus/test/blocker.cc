/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/opus/test/blocker.h"

#include <string.h>

#include "rtc_base/checks.h"

namespace {

// Adds |a| and |b| frame by frame into |result| (basically matrix addition).
void AddFrames(const float* const* a,
               size_t a_start_index,
               const float* const* b,
               int b_start_index,
               size_t num_frames,
               size_t num_channels,
               float* const* result,
               size_t result_start_index) {
  for (size_t i = 0; i < num_channels; ++i) {
    for (size_t j = 0; j < num_frames; ++j) {
      result[i][j + result_start_index] =
          a[i][j + a_start_index] + b[i][j + b_start_index];
    }
  }
}

// Copies |src| into |dst| channel by channel.
void CopyFrames(const float* const* src,
                size_t src_start_index,
                size_t num_frames,
                size_t num_channels,
                float* const* dst,
                size_t dst_start_index) {
  for (size_t i = 0; i < num_channels; ++i) {
    memcpy(&dst[i][dst_start_index], &src[i][src_start_index],
           num_frames * sizeof(dst[i][dst_start_index]));
  }
}

// Moves |src| into |dst| channel by channel.
void MoveFrames(const float* const* src,
                size_t src_start_index,
                size_t num_frames,
                size_t num_channels,
                float* const* dst,
                size_t dst_start_index) {
  for (size_t i = 0; i < num_channels; ++i) {
    memmove(&dst[i][dst_start_index], &src[i][src_start_index],
            num_frames * sizeof(dst[i][dst_start_index]));
  }
}

void ZeroOut(float* const* buffer,
             size_t starting_idx,
             size_t num_frames,
             size_t num_channels) {
  for (size_t i = 0; i < num_channels; ++i) {
    memset(&buffer[i][starting_idx], 0,
           num_frames * sizeof(buffer[i][starting_idx]));
  }
}

// Pointwise multiplies each channel of |frames| with |window|. Results are
// stored in |frames|.
void ApplyWindow(const float* window,
                 size_t num_frames,
                 size_t num_channels,
                 float* const* frames) {
  for (size_t i = 0; i < num_channels; ++i) {
    for (size_t j = 0; j < num_frames; ++j) {
      frames[i][j] = frames[i][j] * window[j];
    }
  }
}

size_t gcd(size_t a, size_t b) {
  size_t tmp;
  while (b) {
    tmp = a;
    a = b;
    b = tmp % b;
  }
  return a;
}

}  // namespace

namespace webrtc {

Blocker::Blocker(size_t chunk_size,
                 size_t block_size,
                 size_t num_input_channels,
                 size_t num_output_channels,
                 const float* window,
                 size_t shift_amount,
                 BlockerCallback* callback)
    : chunk_size_(chunk_size),
      block_size_(block_size),
      num_input_channels_(num_input_channels),
      num_output_channels_(num_output_channels),
      initial_delay_(block_size_ - gcd(chunk_size, shift_amount)),
      frame_offset_(0),
      input_buffer_(num_input_channels_, chunk_size_ + initial_delay_),
      output_buffer_(chunk_size_ + initial_delay_, num_output_channels_),
      input_block_(block_size_, num_input_channels_),
      output_block_(block_size_, num_output_channels_),
      window_(new float[block_size_]),
      shift_amount_(shift_amount),
      callback_(callback) {
  RTC_CHECK_LE(num_output_channels_, num_input_channels_);
  RTC_CHECK_LE(shift_amount_, block_size_);

  memcpy(window_.get(), window, block_size_ * sizeof(*window_.get()));
  input_buffer_.MoveReadPositionBackward(initial_delay_);
}

Blocker::~Blocker() = default;

// When block_size < chunk_size the input and output buffers look like this:
//
//                      delay*             chunk_size    chunk_size + delay*
//  buffer: <-------------|---------------------|---------------|>
//                _a_              _b_                 _c_
//
// On each call to ProcessChunk():
// 1. New input gets read into sections _b_ and _c_ of the input buffer.
// 2. We block starting from frame_offset.
// 3. We block until we reach a block |bl| that doesn't contain any frames
//    from sections _a_ or _b_ of the input buffer.
// 4. We window the current block, fire the callback for processing, window
//    again, and overlap/add to the output buffer.
// 5. We copy sections _a_ and _b_ of the output buffer into output.
// 6. For both the input and the output buffers, we copy section _c_ into
//    section _a_.
// 7. We set the new frame_offset to be the difference between the first frame
//    of |bl| and the border between sections _b_ and _c_.
//
// When block_size > chunk_size the input and output buffers look like this:
//
//                   chunk_size               delay*       chunk_size + delay*
//  buffer: <-------------|---------------------|---------------|>
//                _a_              _b_                 _c_
//
// On each call to ProcessChunk():
// The procedure is the same as above, except for:
// 1. New input gets read into section _c_ of the input buffer.
// 3. We block until we reach a block |bl| that doesn't contain any frames
//    from section _a_ of the input buffer.
// 5. We copy section _a_ of the output buffer into output.
// 6. For both the input and the output buffers, we copy sections _b_ and _c_
//    into section _a_ and _b_.
// 7. We set the new frame_offset to be the difference between the first frame
//    of |bl| and the border between sections _a_ and _b_.
//
// * delay here refers to inintial_delay_
//
// TODO(claguna): Look at using ring buffers to eliminate some copies.
void Blocker::ProcessChunk(const float* const* input,
                           size_t chunk_size,
                           size_t num_input_channels,
                           size_t num_output_channels,
                           float* const* output) {
  RTC_CHECK_EQ(chunk_size, chunk_size_);
  RTC_CHECK_EQ(num_input_channels, num_input_channels_);
  RTC_CHECK_EQ(num_output_channels, num_output_channels_);

  input_buffer_.Write(input, num_input_channels, chunk_size_);
  size_t first_frame_in_block = frame_offset_;

  // Loop through blocks.
  while (first_frame_in_block < chunk_size_) {
    input_buffer_.Read(input_block_.channels(), num_input_channels,
                       block_size_);
    input_buffer_.MoveReadPositionBackward(block_size_ - shift_amount_);

    ApplyWindow(window_.get(), block_size_, num_input_channels_,
                input_block_.channels());
    callback_->ProcessBlock(input_block_.channels(), block_size_,
                            num_input_channels_, num_output_channels_,
                            output_block_.channels());
    ApplyWindow(window_.get(), block_size_, num_output_channels_,
                output_block_.channels());

    AddFrames(output_buffer_.channels(), first_frame_in_block,
              output_block_.channels(), 0, block_size_, num_output_channels_,
              output_buffer_.channels(), first_frame_in_block);

    first_frame_in_block += shift_amount_;
  }

  // Copy output buffer to output
  CopyFrames(output_buffer_.channels(), 0, chunk_size_, num_output_channels_,
             output, 0);

  // Copy output buffer [chunk_size_, chunk_size_ + initial_delay]
  // to output buffer [0, initial_delay], zero the rest.
  MoveFrames(output_buffer_.channels(), chunk_size, initial_delay_,
             num_output_channels_, output_buffer_.channels(), 0);
  ZeroOut(output_buffer_.channels(), initial_delay_, chunk_size_,
          num_output_channels_);

  // Calculate new starting frames.
  frame_offset_ = first_frame_in_block - chunk_size_;
}

}  // namespace webrtc

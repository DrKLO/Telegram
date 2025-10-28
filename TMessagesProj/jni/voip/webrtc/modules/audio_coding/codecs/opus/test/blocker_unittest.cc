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

#include <memory>

#include "rtc_base/arraysize.h"
#include "test/gtest.h"

namespace {

// Callback Function to add 3 to every sample in the signal.
class PlusThreeBlockerCallback : public webrtc::BlockerCallback {
 public:
  void ProcessBlock(const float* const* input,
                    size_t num_frames,
                    size_t num_input_channels,
                    size_t num_output_channels,
                    float* const* output) override {
    for (size_t i = 0; i < num_output_channels; ++i) {
      for (size_t j = 0; j < num_frames; ++j) {
        output[i][j] = input[i][j] + 3;
      }
    }
  }
};

// No-op Callback Function.
class CopyBlockerCallback : public webrtc::BlockerCallback {
 public:
  void ProcessBlock(const float* const* input,
                    size_t num_frames,
                    size_t num_input_channels,
                    size_t num_output_channels,
                    float* const* output) override {
    for (size_t i = 0; i < num_output_channels; ++i) {
      for (size_t j = 0; j < num_frames; ++j) {
        output[i][j] = input[i][j];
      }
    }
  }
};

}  // namespace

namespace webrtc {

// Tests blocking with a window that multiplies the signal by 2, a callback
// that adds 3 to each sample in the signal, and different combinations of chunk
// size, block size, and shift amount.
class BlockerTest : public ::testing::Test {
 protected:
  void RunTest(Blocker* blocker,
               size_t chunk_size,
               size_t num_frames,
               const float* const* input,
               float* const* input_chunk,
               float* const* output,
               float* const* output_chunk,
               size_t num_input_channels,
               size_t num_output_channels) {
    size_t start = 0;
    size_t end = chunk_size - 1;
    while (end < num_frames) {
      CopyTo(input_chunk, 0, start, num_input_channels, chunk_size, input);
      blocker->ProcessChunk(input_chunk, chunk_size, num_input_channels,
                            num_output_channels, output_chunk);
      CopyTo(output, start, 0, num_output_channels, chunk_size, output_chunk);

      start += chunk_size;
      end += chunk_size;
    }
  }

  void ValidateSignalEquality(const float* const* expected,
                              const float* const* actual,
                              size_t num_channels,
                              size_t num_frames) {
    for (size_t i = 0; i < num_channels; ++i) {
      for (size_t j = 0; j < num_frames; ++j) {
        EXPECT_FLOAT_EQ(expected[i][j], actual[i][j]);
      }
    }
  }

  void ValidateInitialDelay(const float* const* output,
                            size_t num_channels,
                            size_t num_frames,
                            size_t initial_delay) {
    for (size_t i = 0; i < num_channels; ++i) {
      for (size_t j = 0; j < num_frames; ++j) {
        if (j < initial_delay) {
          EXPECT_FLOAT_EQ(output[i][j], 0.f);
        } else {
          EXPECT_GT(output[i][j], 0.f);
        }
      }
    }
  }

  static void CopyTo(float* const* dst,
                     size_t start_index_dst,
                     size_t start_index_src,
                     size_t num_channels,
                     size_t num_frames,
                     const float* const* src) {
    for (size_t i = 0; i < num_channels; ++i) {
      memcpy(&dst[i][start_index_dst], &src[i][start_index_src],
             num_frames * sizeof(float));
    }
  }
};

TEST_F(BlockerTest, TestBlockerMutuallyPrimeChunkandBlockSize) {
  const size_t kNumInputChannels = 3;
  const size_t kNumOutputChannels = 2;
  const size_t kNumFrames = 10;
  const size_t kBlockSize = 4;
  const size_t kChunkSize = 5;
  const size_t kShiftAmount = 2;

  const float kInput[kNumInputChannels][kNumFrames] = {
      {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
      {2, 2, 2, 2, 2, 2, 2, 2, 2, 2},
      {3, 3, 3, 3, 3, 3, 3, 3, 3, 3}};
  ChannelBuffer<float> input_cb(kNumFrames, kNumInputChannels);
  input_cb.SetDataForTesting(kInput[0], sizeof(kInput) / sizeof(**kInput));

  const float kExpectedOutput[kNumInputChannels][kNumFrames] = {
      {6, 6, 12, 20, 20, 20, 20, 20, 20, 20},
      {6, 6, 12, 28, 28, 28, 28, 28, 28, 28}};
  ChannelBuffer<float> expected_output_cb(kNumFrames, kNumInputChannels);
  expected_output_cb.SetDataForTesting(
      kExpectedOutput[0], sizeof(kExpectedOutput) / sizeof(**kExpectedOutput));

  const float kWindow[kBlockSize] = {2.f, 2.f, 2.f, 2.f};

  ChannelBuffer<float> actual_output_cb(kNumFrames, kNumOutputChannels);
  ChannelBuffer<float> input_chunk_cb(kChunkSize, kNumInputChannels);
  ChannelBuffer<float> output_chunk_cb(kChunkSize, kNumOutputChannels);

  PlusThreeBlockerCallback callback;
  Blocker blocker(kChunkSize, kBlockSize, kNumInputChannels, kNumOutputChannels,
                  kWindow, kShiftAmount, &callback);

  RunTest(&blocker, kChunkSize, kNumFrames, input_cb.channels(),
          input_chunk_cb.channels(), actual_output_cb.channels(),
          output_chunk_cb.channels(), kNumInputChannels, kNumOutputChannels);

  ValidateSignalEquality(expected_output_cb.channels(),
                         actual_output_cb.channels(), kNumOutputChannels,
                         kNumFrames);
}

TEST_F(BlockerTest, TestBlockerMutuallyPrimeShiftAndBlockSize) {
  const size_t kNumInputChannels = 3;
  const size_t kNumOutputChannels = 2;
  const size_t kNumFrames = 12;
  const size_t kBlockSize = 4;
  const size_t kChunkSize = 6;
  const size_t kShiftAmount = 3;

  const float kInput[kNumInputChannels][kNumFrames] = {
      {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
      {2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2},
      {3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3}};
  ChannelBuffer<float> input_cb(kNumFrames, kNumInputChannels);
  input_cb.SetDataForTesting(kInput[0], sizeof(kInput) / sizeof(**kInput));

  const float kExpectedOutput[kNumOutputChannels][kNumFrames] = {
      {6, 10, 10, 20, 10, 10, 20, 10, 10, 20, 10, 10},
      {6, 14, 14, 28, 14, 14, 28, 14, 14, 28, 14, 14}};
  ChannelBuffer<float> expected_output_cb(kNumFrames, kNumOutputChannels);
  expected_output_cb.SetDataForTesting(
      kExpectedOutput[0], sizeof(kExpectedOutput) / sizeof(**kExpectedOutput));

  const float kWindow[kBlockSize] = {2.f, 2.f, 2.f, 2.f};

  ChannelBuffer<float> actual_output_cb(kNumFrames, kNumOutputChannels);
  ChannelBuffer<float> input_chunk_cb(kChunkSize, kNumInputChannels);
  ChannelBuffer<float> output_chunk_cb(kChunkSize, kNumOutputChannels);

  PlusThreeBlockerCallback callback;
  Blocker blocker(kChunkSize, kBlockSize, kNumInputChannels, kNumOutputChannels,
                  kWindow, kShiftAmount, &callback);

  RunTest(&blocker, kChunkSize, kNumFrames, input_cb.channels(),
          input_chunk_cb.channels(), actual_output_cb.channels(),
          output_chunk_cb.channels(), kNumInputChannels, kNumOutputChannels);

  ValidateSignalEquality(expected_output_cb.channels(),
                         actual_output_cb.channels(), kNumOutputChannels,
                         kNumFrames);
}

TEST_F(BlockerTest, TestBlockerNoOverlap) {
  const size_t kNumInputChannels = 3;
  const size_t kNumOutputChannels = 2;
  const size_t kNumFrames = 12;
  const size_t kBlockSize = 4;
  const size_t kChunkSize = 4;
  const size_t kShiftAmount = 4;

  const float kInput[kNumInputChannels][kNumFrames] = {
      {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
      {2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2},
      {3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3}};
  ChannelBuffer<float> input_cb(kNumFrames, kNumInputChannels);
  input_cb.SetDataForTesting(kInput[0], sizeof(kInput) / sizeof(**kInput));

  const float kExpectedOutput[kNumOutputChannels][kNumFrames] = {
      {10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10},
      {14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14}};
  ChannelBuffer<float> expected_output_cb(kNumFrames, kNumOutputChannels);
  expected_output_cb.SetDataForTesting(
      kExpectedOutput[0], sizeof(kExpectedOutput) / sizeof(**kExpectedOutput));

  const float kWindow[kBlockSize] = {2.f, 2.f, 2.f, 2.f};

  ChannelBuffer<float> actual_output_cb(kNumFrames, kNumOutputChannels);
  ChannelBuffer<float> input_chunk_cb(kChunkSize, kNumInputChannels);
  ChannelBuffer<float> output_chunk_cb(kChunkSize, kNumOutputChannels);

  PlusThreeBlockerCallback callback;
  Blocker blocker(kChunkSize, kBlockSize, kNumInputChannels, kNumOutputChannels,
                  kWindow, kShiftAmount, &callback);

  RunTest(&blocker, kChunkSize, kNumFrames, input_cb.channels(),
          input_chunk_cb.channels(), actual_output_cb.channels(),
          output_chunk_cb.channels(), kNumInputChannels, kNumOutputChannels);

  ValidateSignalEquality(expected_output_cb.channels(),
                         actual_output_cb.channels(), kNumOutputChannels,
                         kNumFrames);
}

TEST_F(BlockerTest, InitialDelaysAreMinimum) {
  const size_t kNumInputChannels = 3;
  const size_t kNumOutputChannels = 2;
  const size_t kNumFrames = 1280;
  const size_t kChunkSize[] = {80,  80,  80,  80,  80,  80,
                               160, 160, 160, 160, 160, 160};
  const size_t kBlockSize[] = {64,  64,  64,  128, 128, 128,
                               128, 128, 128, 256, 256, 256};
  const size_t kShiftAmount[] = {16, 32, 64,  32, 64,  128,
                                 32, 64, 128, 64, 128, 256};
  const size_t kInitialDelay[] = {48, 48, 48, 112, 112, 112,
                                  96, 96, 96, 224, 224, 224};

  float input[kNumInputChannels][kNumFrames];
  for (size_t i = 0; i < kNumInputChannels; ++i) {
    for (size_t j = 0; j < kNumFrames; ++j) {
      input[i][j] = i + 1;
    }
  }
  ChannelBuffer<float> input_cb(kNumFrames, kNumInputChannels);
  input_cb.SetDataForTesting(input[0], sizeof(input) / sizeof(**input));

  ChannelBuffer<float> output_cb(kNumFrames, kNumOutputChannels);

  CopyBlockerCallback callback;

  for (size_t i = 0; i < arraysize(kChunkSize); ++i) {
    std::unique_ptr<float[]> window(new float[kBlockSize[i]]);
    for (size_t j = 0; j < kBlockSize[i]; ++j) {
      window[j] = 1.f;
    }

    ChannelBuffer<float> input_chunk_cb(kChunkSize[i], kNumInputChannels);
    ChannelBuffer<float> output_chunk_cb(kChunkSize[i], kNumOutputChannels);

    Blocker blocker(kChunkSize[i], kBlockSize[i], kNumInputChannels,
                    kNumOutputChannels, window.get(), kShiftAmount[i],
                    &callback);

    RunTest(&blocker, kChunkSize[i], kNumFrames, input_cb.channels(),
            input_chunk_cb.channels(), output_cb.channels(),
            output_chunk_cb.channels(), kNumInputChannels, kNumOutputChannels);

    ValidateInitialDelay(output_cb.channels(), kNumOutputChannels, kNumFrames,
                         kInitialDelay[i]);
  }
}

}  // namespace webrtc

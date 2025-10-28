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

#include <string>
#include <vector>

#include "modules/audio_processing/aec3/aec3_common.h"
#include "rtc_base/strings/string_builder.h"
#include "test/gtest.h"

namespace webrtc {
namespace {

void SetupSubFrameView(
    std::vector<std::vector<std::vector<float>>>* sub_frame,
    std::vector<std::vector<rtc::ArrayView<float>>>* sub_frame_view) {
  for (size_t band = 0; band < sub_frame_view->size(); ++band) {
    for (size_t channel = 0; channel < (*sub_frame_view)[band].size();
         ++channel) {
      (*sub_frame_view)[band][channel] =
          rtc::ArrayView<float>((*sub_frame)[band][channel].data(),
                                (*sub_frame)[band][channel].size());
    }
  }
}

float ComputeSampleValue(size_t chunk_counter,
                         size_t chunk_size,
                         size_t band,
                         size_t channel,
                         size_t sample_index,
                         int offset) {
  float value = static_cast<int>(100 + chunk_counter * chunk_size +
                                 sample_index + channel) +
                offset;
  return 5000 * band + value;
}

bool VerifySubFrame(
    size_t sub_frame_counter,
    int offset,
    const std::vector<std::vector<rtc::ArrayView<float>>>& sub_frame_view) {
  for (size_t band = 0; band < sub_frame_view.size(); ++band) {
    for (size_t channel = 0; channel < sub_frame_view[band].size(); ++channel) {
      for (size_t sample = 0; sample < sub_frame_view[band][channel].size();
           ++sample) {
        const float reference_value = ComputeSampleValue(
            sub_frame_counter, kSubFrameLength, band, channel, sample, offset);
        if (reference_value != sub_frame_view[band][channel][sample]) {
          return false;
        }
      }
    }
  }
  return true;
}

void FillBlock(size_t block_counter, Block* block) {
  for (int band = 0; band < block->NumBands(); ++band) {
    for (int channel = 0; channel < block->NumChannels(); ++channel) {
      auto b = block->View(band, channel);
      for (size_t sample = 0; sample < kBlockSize; ++sample) {
        b[sample] = ComputeSampleValue(block_counter, kBlockSize, band, channel,
                                       sample, 0);
      }
    }
  }
}

// Verifies that the BlockFramer is able to produce the expected frame content.
void RunFramerTest(int sample_rate_hz, size_t num_channels) {
  constexpr size_t kNumSubFramesToProcess = 10;
  const size_t num_bands = NumBandsForRate(sample_rate_hz);

  Block block(num_bands, num_channels);
  std::vector<std::vector<std::vector<float>>> output_sub_frame(
      num_bands, std::vector<std::vector<float>>(
                     num_channels, std::vector<float>(kSubFrameLength, 0.f)));
  std::vector<std::vector<rtc::ArrayView<float>>> output_sub_frame_view(
      num_bands, std::vector<rtc::ArrayView<float>>(num_channels));
  SetupSubFrameView(&output_sub_frame, &output_sub_frame_view);
  BlockFramer framer(num_bands, num_channels);

  size_t block_index = 0;
  for (size_t sub_frame_index = 0; sub_frame_index < kNumSubFramesToProcess;
       ++sub_frame_index) {
    FillBlock(block_index++, &block);
    framer.InsertBlockAndExtractSubFrame(block, &output_sub_frame_view);
    if (sub_frame_index > 1) {
      EXPECT_TRUE(VerifySubFrame(sub_frame_index, -64, output_sub_frame_view));
    }

    if ((sub_frame_index + 1) % 4 == 0) {
      FillBlock(block_index++, &block);
      framer.InsertBlock(block);
    }
  }
}

#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
// Verifies that the BlockFramer crashes if the InsertBlockAndExtractSubFrame
// method is called for inputs with the wrong number of bands or band lengths.
void RunWronglySizedInsertAndExtractParametersTest(
    int sample_rate_hz,
    size_t correct_num_channels,
    size_t num_block_bands,
    size_t num_block_channels,
    size_t num_sub_frame_bands,
    size_t num_sub_frame_channels,
    size_t sub_frame_length) {
  const size_t correct_num_bands = NumBandsForRate(sample_rate_hz);

  Block block(num_block_bands, num_block_channels);
  std::vector<std::vector<std::vector<float>>> output_sub_frame(
      num_sub_frame_bands,
      std::vector<std::vector<float>>(
          num_sub_frame_channels, std::vector<float>(sub_frame_length, 0.f)));
  std::vector<std::vector<rtc::ArrayView<float>>> output_sub_frame_view(
      output_sub_frame.size(),
      std::vector<rtc::ArrayView<float>>(num_sub_frame_channels));
  SetupSubFrameView(&output_sub_frame, &output_sub_frame_view);
  BlockFramer framer(correct_num_bands, correct_num_channels);
  EXPECT_DEATH(
      framer.InsertBlockAndExtractSubFrame(block, &output_sub_frame_view), "");
}

// Verifies that the BlockFramer crashes if the InsertBlock method is called for
// inputs with the wrong number of bands or band lengths.
void RunWronglySizedInsertParameterTest(int sample_rate_hz,
                                        size_t correct_num_channels,
                                        size_t num_block_bands,
                                        size_t num_block_channels) {
  const size_t correct_num_bands = NumBandsForRate(sample_rate_hz);

  Block correct_block(correct_num_bands, correct_num_channels);
  Block wrong_block(num_block_bands, num_block_channels);
  std::vector<std::vector<std::vector<float>>> output_sub_frame(
      correct_num_bands,
      std::vector<std::vector<float>>(
          correct_num_channels, std::vector<float>(kSubFrameLength, 0.f)));
  std::vector<std::vector<rtc::ArrayView<float>>> output_sub_frame_view(
      output_sub_frame.size(),
      std::vector<rtc::ArrayView<float>>(correct_num_channels));
  SetupSubFrameView(&output_sub_frame, &output_sub_frame_view);
  BlockFramer framer(correct_num_bands, correct_num_channels);
  framer.InsertBlockAndExtractSubFrame(correct_block, &output_sub_frame_view);
  framer.InsertBlockAndExtractSubFrame(correct_block, &output_sub_frame_view);
  framer.InsertBlockAndExtractSubFrame(correct_block, &output_sub_frame_view);
  framer.InsertBlockAndExtractSubFrame(correct_block, &output_sub_frame_view);

  EXPECT_DEATH(framer.InsertBlock(wrong_block), "");
}

// Verifies that the BlockFramer crashes if the InsertBlock method is called
// after a wrong number of previous InsertBlockAndExtractSubFrame method calls
// have been made.

void RunWronglyInsertOrderTest(int sample_rate_hz,
                               size_t num_channels,
                               size_t num_preceeding_api_calls) {
  const size_t correct_num_bands = NumBandsForRate(sample_rate_hz);

  Block block(correct_num_bands, num_channels);
  std::vector<std::vector<std::vector<float>>> output_sub_frame(
      correct_num_bands,
      std::vector<std::vector<float>>(
          num_channels, std::vector<float>(kSubFrameLength, 0.f)));
  std::vector<std::vector<rtc::ArrayView<float>>> output_sub_frame_view(
      output_sub_frame.size(),
      std::vector<rtc::ArrayView<float>>(num_channels));
  SetupSubFrameView(&output_sub_frame, &output_sub_frame_view);
  BlockFramer framer(correct_num_bands, num_channels);
  for (size_t k = 0; k < num_preceeding_api_calls; ++k) {
    framer.InsertBlockAndExtractSubFrame(block, &output_sub_frame_view);
  }

  EXPECT_DEATH(framer.InsertBlock(block), "");
}
#endif

std::string ProduceDebugText(int sample_rate_hz, size_t num_channels) {
  rtc::StringBuilder ss;
  ss << "Sample rate: " << sample_rate_hz;
  ss << ", number of channels: " << num_channels;
  return ss.Release();
}

}  // namespace

#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
TEST(BlockFramerDeathTest,
     WrongNumberOfBandsInBlockForInsertBlockAndExtractSubFrame) {
  for (auto rate : {16000, 32000, 48000}) {
    for (auto correct_num_channels : {1, 2, 8}) {
      SCOPED_TRACE(ProduceDebugText(rate, correct_num_channels));
      const size_t correct_num_bands = NumBandsForRate(rate);
      const size_t wrong_num_bands = (correct_num_bands % 3) + 1;
      RunWronglySizedInsertAndExtractParametersTest(
          rate, correct_num_channels, wrong_num_bands, correct_num_channels,
          correct_num_bands, correct_num_channels, kSubFrameLength);
    }
  }
}

TEST(BlockFramerDeathTest,
     WrongNumberOfChannelsInBlockForInsertBlockAndExtractSubFrame) {
  for (auto rate : {16000, 32000, 48000}) {
    for (auto correct_num_channels : {1, 2, 8}) {
      SCOPED_TRACE(ProduceDebugText(rate, correct_num_channels));
      const size_t correct_num_bands = NumBandsForRate(rate);
      const size_t wrong_num_channels = correct_num_channels + 1;
      RunWronglySizedInsertAndExtractParametersTest(
          rate, correct_num_channels, correct_num_bands, wrong_num_channels,
          correct_num_bands, correct_num_channels, kSubFrameLength);
    }
  }
}

TEST(BlockFramerDeathTest,
     WrongNumberOfBandsInSubFrameForInsertBlockAndExtractSubFrame) {
  for (auto rate : {16000, 32000, 48000}) {
    for (auto correct_num_channels : {1, 2, 8}) {
      SCOPED_TRACE(ProduceDebugText(rate, correct_num_channels));
      const size_t correct_num_bands = NumBandsForRate(rate);
      const size_t wrong_num_bands = (correct_num_bands % 3) + 1;
      RunWronglySizedInsertAndExtractParametersTest(
          rate, correct_num_channels, correct_num_bands, correct_num_channels,
          wrong_num_bands, correct_num_channels, kSubFrameLength);
    }
  }
}

TEST(BlockFramerDeathTest,
     WrongNumberOfChannelsInSubFrameForInsertBlockAndExtractSubFrame) {
  for (auto rate : {16000, 32000, 48000}) {
    for (auto correct_num_channels : {1, 2, 8}) {
      SCOPED_TRACE(ProduceDebugText(rate, correct_num_channels));
      const size_t correct_num_bands = NumBandsForRate(rate);
      const size_t wrong_num_channels = correct_num_channels + 1;
      RunWronglySizedInsertAndExtractParametersTest(
          rate, correct_num_channels, correct_num_bands, correct_num_channels,
          correct_num_bands, wrong_num_channels, kSubFrameLength);
    }
  }
}

TEST(BlockFramerDeathTest,
     WrongNumberOfSamplesInSubFrameForInsertBlockAndExtractSubFrame) {
  const size_t correct_num_channels = 1;
  for (auto rate : {16000, 32000, 48000}) {
    SCOPED_TRACE(ProduceDebugText(rate, correct_num_channels));
    const size_t correct_num_bands = NumBandsForRate(rate);
    RunWronglySizedInsertAndExtractParametersTest(
        rate, correct_num_channels, correct_num_bands, correct_num_channels,
        correct_num_bands, correct_num_channels, kSubFrameLength - 1);
  }
}

TEST(BlockFramerDeathTest, WrongNumberOfBandsInBlockForInsertBlock) {
  for (auto rate : {16000, 32000, 48000}) {
    for (auto correct_num_channels : {1, 2, 8}) {
      SCOPED_TRACE(ProduceDebugText(rate, correct_num_channels));
      const size_t correct_num_bands = NumBandsForRate(rate);
      const size_t wrong_num_bands = (correct_num_bands % 3) + 1;
      RunWronglySizedInsertParameterTest(rate, correct_num_channels,
                                         wrong_num_bands, correct_num_channels);
    }
  }
}

TEST(BlockFramerDeathTest, WrongNumberOfChannelsInBlockForInsertBlock) {
  for (auto rate : {16000, 32000, 48000}) {
    for (auto correct_num_channels : {1, 2, 8}) {
      SCOPED_TRACE(ProduceDebugText(rate, correct_num_channels));
      const size_t correct_num_bands = NumBandsForRate(rate);
      const size_t wrong_num_channels = correct_num_channels + 1;
      RunWronglySizedInsertParameterTest(rate, correct_num_channels,
                                         correct_num_bands, wrong_num_channels);
    }
  }
}

TEST(BlockFramerDeathTest, WrongNumberOfPreceedingApiCallsForInsertBlock) {
  for (size_t num_channels : {1, 2, 8}) {
    for (auto rate : {16000, 32000, 48000}) {
      for (size_t num_calls = 0; num_calls < 4; ++num_calls) {
        rtc::StringBuilder ss;
        ss << "Sample rate: " << rate;
        ss << ", Num channels: " << num_channels;
        ss << ", Num preceeding InsertBlockAndExtractSubFrame calls: "
           << num_calls;

        SCOPED_TRACE(ss.str());
        RunWronglyInsertOrderTest(rate, num_channels, num_calls);
      }
    }
  }
}

// Verifies that the verification for 0 number of channels works.
TEST(BlockFramerDeathTest, ZeroNumberOfChannelsParameter) {
  EXPECT_DEATH(BlockFramer(16000, 0), "");
}

// Verifies that the verification for 0 number of bands works.
TEST(BlockFramerDeathTest, ZeroNumberOfBandsParameter) {
  EXPECT_DEATH(BlockFramer(0, 1), "");
}

// Verifies that the verification for null sub_frame pointer works.
TEST(BlockFramerDeathTest, NullSubFrameParameter) {
  EXPECT_DEATH(
      BlockFramer(1, 1).InsertBlockAndExtractSubFrame(Block(1, 1), nullptr),
      "");
}

#endif

TEST(BlockFramer, FrameBitexactness) {
  for (auto rate : {16000, 32000, 48000}) {
    for (auto num_channels : {1, 2, 4, 8}) {
      SCOPED_TRACE(ProduceDebugText(rate, num_channels));
      RunFramerTest(rate, num_channels);
    }
  }
}

}  // namespace webrtc

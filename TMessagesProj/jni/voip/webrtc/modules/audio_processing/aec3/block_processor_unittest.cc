/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/block_processor.h"

#include <memory>
#include <string>
#include <vector>

#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/mock/mock_echo_remover.h"
#include "modules/audio_processing/aec3/mock/mock_render_delay_buffer.h"
#include "modules/audio_processing/aec3/mock/mock_render_delay_controller.h"
#include "modules/audio_processing/test/echo_canceller_test_tools.h"
#include "rtc_base/checks.h"
#include "rtc_base/random.h"
#include "rtc_base/strings/string_builder.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {
namespace {

using ::testing::_;
using ::testing::AtLeast;
using ::testing::NiceMock;
using ::testing::Return;
using ::testing::StrictMock;

// Verifies that the basic BlockProcessor functionality works and that the API
// methods are callable.
void RunBasicSetupAndApiCallTest(int sample_rate_hz, int num_iterations) {
  constexpr size_t kNumRenderChannels = 1;
  constexpr size_t kNumCaptureChannels = 1;

  std::unique_ptr<BlockProcessor> block_processor(
      BlockProcessor::Create(EchoCanceller3Config(), sample_rate_hz,
                             kNumRenderChannels, kNumCaptureChannels));
  Block block(NumBandsForRate(sample_rate_hz), kNumRenderChannels, 1000.f);
  for (int k = 0; k < num_iterations; ++k) {
    block_processor->BufferRender(block);
    block_processor->ProcessCapture(false, false, nullptr, &block);
    block_processor->UpdateEchoLeakageStatus(false);
  }
}

#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
void RunRenderBlockSizeVerificationTest(int sample_rate_hz) {
  constexpr size_t kNumRenderChannels = 1;
  constexpr size_t kNumCaptureChannels = 1;

  std::unique_ptr<BlockProcessor> block_processor(
      BlockProcessor::Create(EchoCanceller3Config(), sample_rate_hz,
                             kNumRenderChannels, kNumCaptureChannels));
  Block block(NumBandsForRate(sample_rate_hz), kNumRenderChannels);

  EXPECT_DEATH(block_processor->BufferRender(block), "");
}

void RunRenderNumBandsVerificationTest(int sample_rate_hz) {
  constexpr size_t kNumRenderChannels = 1;
  constexpr size_t kNumCaptureChannels = 1;

  const size_t wrong_num_bands = NumBandsForRate(sample_rate_hz) < 3
                                     ? NumBandsForRate(sample_rate_hz) + 1
                                     : 1;
  std::unique_ptr<BlockProcessor> block_processor(
      BlockProcessor::Create(EchoCanceller3Config(), sample_rate_hz,
                             kNumRenderChannels, kNumCaptureChannels));
  Block block(wrong_num_bands, kNumRenderChannels);

  EXPECT_DEATH(block_processor->BufferRender(block), "");
}

void RunCaptureNumBandsVerificationTest(int sample_rate_hz) {
  constexpr size_t kNumRenderChannels = 1;
  constexpr size_t kNumCaptureChannels = 1;

  const size_t wrong_num_bands = NumBandsForRate(sample_rate_hz) < 3
                                     ? NumBandsForRate(sample_rate_hz) + 1
                                     : 1;
  std::unique_ptr<BlockProcessor> block_processor(
      BlockProcessor::Create(EchoCanceller3Config(), sample_rate_hz,
                             kNumRenderChannels, kNumCaptureChannels));
  Block block(wrong_num_bands, kNumRenderChannels);

  EXPECT_DEATH(block_processor->ProcessCapture(false, false, nullptr, &block),
               "");
}
#endif

std::string ProduceDebugText(int sample_rate_hz) {
  rtc::StringBuilder ss;
  ss << "Sample rate: " << sample_rate_hz;
  return ss.Release();
}

void FillSampleVector(int call_counter,
                      int delay,
                      rtc::ArrayView<float> samples) {
  for (size_t i = 0; i < samples.size(); ++i) {
    samples[i] = (call_counter - delay) * 10000.0f + i;
  }
}

}  // namespace

// Verifies that the delay controller functionality is properly integrated with
// the render delay buffer inside block processor.
// TODO(peah): Activate the unittest once the required code has been landed.
TEST(BlockProcessor, DISABLED_DelayControllerIntegration) {
  constexpr size_t kNumRenderChannels = 1;
  constexpr size_t kNumCaptureChannels = 1;
  constexpr size_t kNumBlocks = 310;
  constexpr size_t kDelayInSamples = 640;
  constexpr size_t kDelayHeadroom = 1;
  constexpr size_t kDelayInBlocks =
      kDelayInSamples / kBlockSize - kDelayHeadroom;
  Random random_generator(42U);
  for (auto rate : {16000, 32000, 48000}) {
    SCOPED_TRACE(ProduceDebugText(rate));
    std::unique_ptr<testing::StrictMock<webrtc::test::MockRenderDelayBuffer>>
        render_delay_buffer_mock(
            new StrictMock<webrtc::test::MockRenderDelayBuffer>(rate, 1));
    EXPECT_CALL(*render_delay_buffer_mock, Insert(_))
        .Times(kNumBlocks)
        .WillRepeatedly(Return(RenderDelayBuffer::BufferingEvent::kNone));
    EXPECT_CALL(*render_delay_buffer_mock, AlignFromDelay(kDelayInBlocks))
        .Times(AtLeast(1));
    EXPECT_CALL(*render_delay_buffer_mock, MaxDelay()).WillOnce(Return(30));
    EXPECT_CALL(*render_delay_buffer_mock, Delay())
        .Times(kNumBlocks + 1)
        .WillRepeatedly(Return(0));
    std::unique_ptr<BlockProcessor> block_processor(BlockProcessor::Create(
        EchoCanceller3Config(), rate, kNumRenderChannels, kNumCaptureChannels,
        std::move(render_delay_buffer_mock)));

    Block render_block(NumBandsForRate(rate), kNumRenderChannels);
    Block capture_block(NumBandsForRate(rate), kNumCaptureChannels);
    DelayBuffer<float> signal_delay_buffer(kDelayInSamples);
    for (size_t k = 0; k < kNumBlocks; ++k) {
      RandomizeSampleVector(&random_generator,
                            render_block.View(/*band=*/0, /*capture=*/0));
      signal_delay_buffer.Delay(render_block.View(/*band=*/0, /*capture=*/0),
                                capture_block.View(/*band=*/0, /*capture=*/0));
      block_processor->BufferRender(render_block);
      block_processor->ProcessCapture(false, false, nullptr, &capture_block);
    }
  }
}

// Verifies that BlockProcessor submodules are called in a proper manner.
TEST(BlockProcessor, DISABLED_SubmoduleIntegration) {
  constexpr size_t kNumBlocks = 310;
  constexpr size_t kNumRenderChannels = 1;
  constexpr size_t kNumCaptureChannels = 1;

  Random random_generator(42U);
  for (auto rate : {16000, 32000, 48000}) {
    SCOPED_TRACE(ProduceDebugText(rate));
    std::unique_ptr<testing::StrictMock<webrtc::test::MockRenderDelayBuffer>>
        render_delay_buffer_mock(
            new StrictMock<webrtc::test::MockRenderDelayBuffer>(rate, 1));
    std::unique_ptr<
        ::testing::StrictMock<webrtc::test::MockRenderDelayController>>
        render_delay_controller_mock(
            new StrictMock<webrtc::test::MockRenderDelayController>());
    std::unique_ptr<testing::StrictMock<webrtc::test::MockEchoRemover>>
        echo_remover_mock(new StrictMock<webrtc::test::MockEchoRemover>());

    EXPECT_CALL(*render_delay_buffer_mock, Insert(_))
        .Times(kNumBlocks - 1)
        .WillRepeatedly(Return(RenderDelayBuffer::BufferingEvent::kNone));
    EXPECT_CALL(*render_delay_buffer_mock, PrepareCaptureProcessing())
        .Times(kNumBlocks);
    EXPECT_CALL(*render_delay_buffer_mock, AlignFromDelay(9)).Times(AtLeast(1));
    EXPECT_CALL(*render_delay_buffer_mock, Delay())
        .Times(kNumBlocks)
        .WillRepeatedly(Return(0));
    EXPECT_CALL(*render_delay_controller_mock, GetDelay(_, _, _))
        .Times(kNumBlocks);
    EXPECT_CALL(*echo_remover_mock, ProcessCapture(_, _, _, _, _, _))
        .Times(kNumBlocks);
    EXPECT_CALL(*echo_remover_mock, UpdateEchoLeakageStatus(_))
        .Times(kNumBlocks);

    std::unique_ptr<BlockProcessor> block_processor(BlockProcessor::Create(
        EchoCanceller3Config(), rate, kNumRenderChannels, kNumCaptureChannels,
        std::move(render_delay_buffer_mock),
        std::move(render_delay_controller_mock), std::move(echo_remover_mock)));

    Block render_block(NumBandsForRate(rate), kNumRenderChannels);
    Block capture_block(NumBandsForRate(rate), kNumCaptureChannels);
    DelayBuffer<float> signal_delay_buffer(640);
    for (size_t k = 0; k < kNumBlocks; ++k) {
      RandomizeSampleVector(&random_generator,
                            render_block.View(/*band=*/0, /*capture=*/0));
      signal_delay_buffer.Delay(render_block.View(/*band=*/0, /*capture=*/0),
                                capture_block.View(/*band=*/0, /*capture=*/0));
      block_processor->BufferRender(render_block);
      block_processor->ProcessCapture(false, false, nullptr, &capture_block);
      block_processor->UpdateEchoLeakageStatus(false);
    }
  }
}

TEST(BlockProcessor, BasicSetupAndApiCalls) {
  for (auto rate : {16000, 32000, 48000}) {
    SCOPED_TRACE(ProduceDebugText(rate));
    RunBasicSetupAndApiCallTest(rate, 1);
  }
}

TEST(BlockProcessor, TestLongerCall) {
  RunBasicSetupAndApiCallTest(16000, 20 * kNumBlocksPerSecond);
}

#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
// TODO(gustaf): Re-enable the test once the issue with memory leaks during
// DEATH tests on test bots has been fixed.
TEST(BlockProcessorDeathTest, DISABLED_VerifyRenderBlockSizeCheck) {
  for (auto rate : {16000, 32000, 48000}) {
    SCOPED_TRACE(ProduceDebugText(rate));
    RunRenderBlockSizeVerificationTest(rate);
  }
}

TEST(BlockProcessorDeathTest, VerifyRenderNumBandsCheck) {
  for (auto rate : {16000, 32000, 48000}) {
    SCOPED_TRACE(ProduceDebugText(rate));
    RunRenderNumBandsVerificationTest(rate);
  }
}

// TODO(peah): Verify the check for correct number of bands in the capture
// signal.
TEST(BlockProcessorDeathTest, VerifyCaptureNumBandsCheck) {
  for (auto rate : {16000, 32000, 48000}) {
    SCOPED_TRACE(ProduceDebugText(rate));
    RunCaptureNumBandsVerificationTest(rate);
  }
}

// Verifiers that the verification for null ProcessCapture input works.
TEST(BlockProcessorDeathTest, NullProcessCaptureParameter) {
  EXPECT_DEATH(std::unique_ptr<BlockProcessor>(
                   BlockProcessor::Create(EchoCanceller3Config(), 16000, 1, 1))
                   ->ProcessCapture(false, false, nullptr, nullptr),
               "");
}

// Verifies the check for correct sample rate.
// TODO(peah): Re-enable the test once the issue with memory leaks during DEATH
// tests on test bots has been fixed.
TEST(BlockProcessor, DISABLED_WrongSampleRate) {
  EXPECT_DEATH(std::unique_ptr<BlockProcessor>(
                   BlockProcessor::Create(EchoCanceller3Config(), 8001, 1, 1)),
               "");
}

#endif

// Verifies that external delay estimator delays are applied correctly when a
// call begins with a sequence of capture blocks.
TEST(BlockProcessor, ExternalDelayAppliedCorrectlyWithInitialCaptureCalls) {
  constexpr int kNumRenderChannels = 1;
  constexpr int kNumCaptureChannels = 1;
  constexpr int kSampleRateHz = 16000;

  EchoCanceller3Config config;
  config.delay.use_external_delay_estimator = true;

  std::unique_ptr<RenderDelayBuffer> delay_buffer(
      RenderDelayBuffer::Create(config, kSampleRateHz, kNumRenderChannels));

  std::unique_ptr<testing::NiceMock<webrtc::test::MockEchoRemover>>
      echo_remover_mock(new NiceMock<webrtc::test::MockEchoRemover>());
  webrtc::test::MockEchoRemover* echo_remover_mock_pointer =
      echo_remover_mock.get();

  std::unique_ptr<BlockProcessor> block_processor(BlockProcessor::Create(
      config, kSampleRateHz, kNumRenderChannels, kNumCaptureChannels,
      std::move(delay_buffer), /*delay_controller=*/nullptr,
      std::move(echo_remover_mock)));

  Block render_block(NumBandsForRate(kSampleRateHz), kNumRenderChannels);
  Block capture_block(NumBandsForRate(kSampleRateHz), kNumCaptureChannels);

  // Process...
  // - 10 capture calls, where no render data is available,
  // - 10 render calls, populating the buffer,
  // - 2 capture calls, verifying that the delay was applied correctly.
  constexpr int kDelayInBlocks = 5;
  constexpr int kDelayInMs = 20;
  block_processor->SetAudioBufferDelay(kDelayInMs);

  int capture_call_counter = 0;
  int render_call_counter = 0;
  for (size_t k = 0; k < 10; ++k) {
    FillSampleVector(++capture_call_counter, kDelayInBlocks,
                     capture_block.View(/*band=*/0, /*capture=*/0));
    block_processor->ProcessCapture(false, false, nullptr, &capture_block);
  }
  for (size_t k = 0; k < 10; ++k) {
    FillSampleVector(++render_call_counter, 0,
                     render_block.View(/*band=*/0, /*capture=*/0));
    block_processor->BufferRender(render_block);
  }

  EXPECT_CALL(*echo_remover_mock_pointer, ProcessCapture)
      .WillRepeatedly(
          [](EchoPathVariability /*echo_path_variability*/,
             bool /*capture_signal_saturation*/,
             const absl::optional<DelayEstimate>& /*external_delay*/,
             RenderBuffer* render_buffer, Block* /*linear_output*/,
             Block* capture) {
            const auto& render = render_buffer->GetBlock(0);
            const auto render_view = render.View(/*band=*/0, /*channel=*/0);
            const auto capture_view = capture->View(/*band=*/0, /*channel=*/0);
            for (size_t i = 0; i < kBlockSize; ++i) {
              EXPECT_FLOAT_EQ(render_view[i], capture_view[i]);
            }
          });

  FillSampleVector(++capture_call_counter, kDelayInBlocks,
                   capture_block.View(/*band=*/0, /*capture=*/0));
  block_processor->ProcessCapture(false, false, nullptr, &capture_block);

  FillSampleVector(++capture_call_counter, kDelayInBlocks,
                   capture_block.View(/*band=*/0, /*capture=*/0));
  block_processor->ProcessCapture(false, false, nullptr, &capture_block);
}

}  // namespace webrtc

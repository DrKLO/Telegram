/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/rnn_vad/test_utils.h"

#include <memory>

#include "rtc_base/checks.h"
#include "rtc_base/system/arch.h"
#include "system_wrappers/include/cpu_features_wrapper.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {
namespace rnn_vad {
namespace test {
namespace {

using ReaderPairType =
    std::pair<std::unique_ptr<BinaryFileReader<float>>, const size_t>;

}  // namespace

using webrtc::test::ResourcePath;

void ExpectEqualFloatArray(rtc::ArrayView<const float> expected,
                           rtc::ArrayView<const float> computed) {
  ASSERT_EQ(expected.size(), computed.size());
  for (size_t i = 0; i < expected.size(); ++i) {
    SCOPED_TRACE(i);
    EXPECT_FLOAT_EQ(expected[i], computed[i]);
  }
}

void ExpectNearAbsolute(rtc::ArrayView<const float> expected,
                        rtc::ArrayView<const float> computed,
                        float tolerance) {
  ASSERT_EQ(expected.size(), computed.size());
  for (size_t i = 0; i < expected.size(); ++i) {
    SCOPED_TRACE(i);
    EXPECT_NEAR(expected[i], computed[i], tolerance);
  }
}

std::pair<std::unique_ptr<BinaryFileReader<int16_t, float>>, const size_t>
CreatePcmSamplesReader(const size_t frame_length) {
  auto ptr = std::make_unique<BinaryFileReader<int16_t, float>>(
      test::ResourcePath("audio_processing/agc2/rnn_vad/samples", "pcm"),
      frame_length);
  // The last incomplete frame is ignored.
  return {std::move(ptr), ptr->data_length() / frame_length};
}

ReaderPairType CreatePitchBuffer24kHzReader() {
  constexpr size_t cols = 864;
  auto ptr = std::make_unique<BinaryFileReader<float>>(
      ResourcePath("audio_processing/agc2/rnn_vad/pitch_buf_24k", "dat"), cols);
  return {std::move(ptr), rtc::CheckedDivExact(ptr->data_length(), cols)};
}

ReaderPairType CreateLpResidualAndPitchPeriodGainReader() {
  constexpr size_t num_lp_residual_coeffs = 864;
  auto ptr = std::make_unique<BinaryFileReader<float>>(
      ResourcePath("audio_processing/agc2/rnn_vad/pitch_lp_res", "dat"),
      num_lp_residual_coeffs);
  return {std::move(ptr),
          rtc::CheckedDivExact(ptr->data_length(), 2 + num_lp_residual_coeffs)};
}

ReaderPairType CreateVadProbsReader() {
  auto ptr = std::make_unique<BinaryFileReader<float>>(
      test::ResourcePath("audio_processing/agc2/rnn_vad/vad_prob", "dat"));
  return {std::move(ptr), ptr->data_length()};
}

PitchTestData::PitchTestData() {
  BinaryFileReader<float> test_data_reader(
      ResourcePath("audio_processing/agc2/rnn_vad/pitch_search_int", "dat"),
      static_cast<size_t>(1396));
  test_data_reader.ReadChunk(test_data_);
}

PitchTestData::~PitchTestData() = default;

rtc::ArrayView<const float, kBufSize24kHz> PitchTestData::GetPitchBufView()
    const {
  return {test_data_.data(), kBufSize24kHz};
}

rtc::ArrayView<const float, kNumPitchBufSquareEnergies>
PitchTestData::GetPitchBufSquareEnergiesView() const {
  return {test_data_.data() + kBufSize24kHz, kNumPitchBufSquareEnergies};
}

rtc::ArrayView<const float, kNumPitchBufAutoCorrCoeffs>
PitchTestData::GetPitchBufAutoCorrCoeffsView() const {
  return {test_data_.data() + kBufSize24kHz + kNumPitchBufSquareEnergies,
          kNumPitchBufAutoCorrCoeffs};
}

bool IsOptimizationAvailable(Optimization optimization) {
  switch (optimization) {
    case Optimization::kSse2:
#if defined(WEBRTC_ARCH_X86_FAMILY)
      return WebRtc_GetCPUInfo(kSSE2) != 0;
#else
      return false;
#endif
    case Optimization::kNeon:
#if defined(WEBRTC_HAS_NEON)
      return true;
#else
      return false;
#endif
    case Optimization::kNone:
      return true;
  }
}

}  // namespace test
}  // namespace rnn_vad
}  // namespace webrtc

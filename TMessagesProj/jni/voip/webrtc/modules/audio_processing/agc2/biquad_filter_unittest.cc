/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/biquad_filter.h"

#include <algorithm>
#include <array>
#include <cmath>

// TODO(bugs.webrtc.org/8948): Add when the issue is fixed.
// #include "test/fpe_observer.h"
#include "rtc_base/gunit.h"

namespace webrtc {
namespace {

constexpr int kFrameSize = 8;
constexpr int kNumFrames = 4;
using FloatArraySequence =
    std::array<std::array<float, kFrameSize>, kNumFrames>;

constexpr FloatArraySequence kBiQuadInputSeq = {
    {{{-87.166290f, -8.029022f, 101.619583f, -0.294296f, -5.825764f, -8.890625f,
       10.310432f, 54.845333f}},
     {{-64.647644f, -6.883945f, 11.059189f, -95.242538f, -108.870834f,
       11.024944f, 63.044102f, -52.709583f}},
     {{-32.350529f, -18.108028f, -74.022339f, -8.986874f, -1.525581f,
       103.705513f, 6.346226f, -14.319557f}},
     {{22.645832f, -64.597153f, 55.462521f, -109.393188f, 10.117825f,
       -40.019642f, -98.612228f, -8.330326f}}}};

// Computed as `scipy.signal.butter(N=2, Wn=60/24000, btype='highpass')`.
constexpr BiQuadFilter::Config kBiQuadConfig{
    {0.99446179f, -1.98892358f, 0.99446179f},
    {-1.98889291f, 0.98895425f}};

// Comparing to scipy. The expected output is generated as follows:
// zi = np.float32([0, 0])
// for i in range(4):
//   yn, zi = scipy.signal.lfilter(B, A, x[i], zi=zi)
//   print(yn)
constexpr FloatArraySequence kBiQuadOutputSeq = {
    {{{-86.68354497f, -7.02175351f, 102.10290352f, -0.37487333f, -5.87205847f,
       -8.85521608f, 10.33772563f, 54.51157181f}},
     {{-64.92531604f, -6.76395978f, 11.15534507f, -94.68073341f, -107.18177856f,
       13.24642474f, 64.84288941f, -50.97822629f}},
     {{-30.1579652f, -15.64850899f, -71.06662821f, -5.5883229f, 1.91175353f,
       106.5572003f, 8.57183046f, -12.06298473f}},
     {{24.84286614f, -62.18094158f, 57.91488056f, -106.65685933f, 13.38760103f,
       -36.60367134f, -94.44880104f, -3.59920354f}}}};

// Fails for every pair from two equally sized rtc::ArrayView<float> views such
// that their relative error is above a given threshold. If the expected value
// of a pair is 0, `tolerance` is used to check the absolute error.
void ExpectNearRelative(rtc::ArrayView<const float> expected,
                        rtc::ArrayView<const float> computed,
                        const float tolerance) {
  // The relative error is undefined when the expected value is 0.
  // When that happens, check the absolute error instead. `safe_den` is used
  // below to implement such logic.
  auto safe_den = [](float x) { return (x == 0.0f) ? 1.0f : std::fabs(x); };
  ASSERT_EQ(expected.size(), computed.size());
  for (size_t i = 0; i < expected.size(); ++i) {
    const float abs_diff = std::fabs(expected[i] - computed[i]);
    // No failure when the values are equal.
    if (abs_diff == 0.0f) {
      continue;
    }
    SCOPED_TRACE(i);
    SCOPED_TRACE(expected[i]);
    SCOPED_TRACE(computed[i]);
    EXPECT_LE(abs_diff / safe_den(expected[i]), tolerance);
  }
}

// Checks that filtering works when different containers are used both as input
// and as output.
TEST(BiQuadFilterTest, FilterNotInPlace) {
  BiQuadFilter filter(kBiQuadConfig);
  std::array<float, kFrameSize> samples;

  // TODO(https://bugs.webrtc.org/8948): Add when the issue is fixed.
  // FloatingPointExceptionObserver fpe_observer;

  for (int i = 0; i < kNumFrames; ++i) {
    SCOPED_TRACE(i);
    filter.Process(kBiQuadInputSeq[i], samples);
    ExpectNearRelative(kBiQuadOutputSeq[i], samples, 2e-4f);
  }
}

// Checks that filtering works when the same container is used both as input and
// as output.
TEST(BiQuadFilterTest, FilterInPlace) {
  BiQuadFilter filter(kBiQuadConfig);
  std::array<float, kFrameSize> samples;

  // TODO(https://bugs.webrtc.org/8948): Add when the issue is fixed.
  // FloatingPointExceptionObserver fpe_observer;

  for (int i = 0; i < kNumFrames; ++i) {
    SCOPED_TRACE(i);
    std::copy(kBiQuadInputSeq[i].begin(), kBiQuadInputSeq[i].end(),
              samples.begin());
    filter.Process({samples}, {samples});
    ExpectNearRelative(kBiQuadOutputSeq[i], samples, 2e-4f);
  }
}

// Checks that different configurations produce different outputs.
TEST(BiQuadFilterTest, SetConfigDifferentOutput) {
  BiQuadFilter filter(/*config=*/{{0.97803048f, -1.95606096f, 0.97803048f},
                                  {-1.95557824f, 0.95654368f}});

  std::array<float, kFrameSize> samples1;
  for (int i = 0; i < kNumFrames; ++i) {
    filter.Process(kBiQuadInputSeq[i], samples1);
  }

  filter.SetConfig(
      {{0.09763107f, 0.19526215f, 0.09763107f}, {-0.94280904f, 0.33333333f}});
  std::array<float, kFrameSize> samples2;
  for (int i = 0; i < kNumFrames; ++i) {
    filter.Process(kBiQuadInputSeq[i], samples2);
  }

  EXPECT_NE(samples1, samples2);
}

// Checks that when `SetConfig()` is called but the filter coefficients are the
// same the filter state is reset.
TEST(BiQuadFilterTest, SetConfigResetsState) {
  BiQuadFilter filter(kBiQuadConfig);

  std::array<float, kFrameSize> samples1;
  for (int i = 0; i < kNumFrames; ++i) {
    filter.Process(kBiQuadInputSeq[i], samples1);
  }

  filter.SetConfig(kBiQuadConfig);
  std::array<float, kFrameSize> samples2;
  for (int i = 0; i < kNumFrames; ++i) {
    filter.Process(kBiQuadInputSeq[i], samples2);
  }

  EXPECT_EQ(samples1, samples2);
}

// Checks that when `Reset()` is called the filter state is reset.
TEST(BiQuadFilterTest, Reset) {
  BiQuadFilter filter(kBiQuadConfig);

  std::array<float, kFrameSize> samples1;
  for (int i = 0; i < kNumFrames; ++i) {
    filter.Process(kBiQuadInputSeq[i], samples1);
  }

  filter.Reset();
  std::array<float, kFrameSize> samples2;
  for (int i = 0; i < kNumFrames; ++i) {
    filter.Process(kBiQuadInputSeq[i], samples2);
  }

  EXPECT_EQ(samples1, samples2);
}

}  // namespace
}  // namespace webrtc

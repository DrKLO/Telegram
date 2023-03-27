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

#include <algorithm>
#include <fstream>
#include <memory>
#include <string>
#include <type_traits>
#include <vector>

#include "absl/strings/string_view.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_compare.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {
namespace rnn_vad {
namespace {

// File reader for binary files that contain a sequence of values with
// arithmetic type `T`. The values of type `T` that are read are cast to float.
template <typename T>
class FloatFileReader : public FileReader {
 public:
  static_assert(std::is_arithmetic<T>::value, "");
  explicit FloatFileReader(absl::string_view filename)
      : is_(std::string(filename), std::ios::binary | std::ios::ate),
        size_(is_.tellg() / sizeof(T)) {
    RTC_CHECK(is_);
    SeekBeginning();
  }
  FloatFileReader(const FloatFileReader&) = delete;
  FloatFileReader& operator=(const FloatFileReader&) = delete;
  ~FloatFileReader() = default;

  int size() const override { return size_; }
  bool ReadChunk(rtc::ArrayView<float> dst) override {
    const std::streamsize bytes_to_read = dst.size() * sizeof(T);
    if (std::is_same<T, float>::value) {
      is_.read(reinterpret_cast<char*>(dst.data()), bytes_to_read);
    } else {
      buffer_.resize(dst.size());
      is_.read(reinterpret_cast<char*>(buffer_.data()), bytes_to_read);
      std::transform(buffer_.begin(), buffer_.end(), dst.begin(),
                     [](const T& v) -> float { return static_cast<float>(v); });
    }
    return is_.gcount() == bytes_to_read;
  }
  bool ReadValue(float& dst) override { return ReadChunk({&dst, 1}); }
  void SeekForward(int hop) override { is_.seekg(hop * sizeof(T), is_.cur); }
  void SeekBeginning() override { is_.seekg(0, is_.beg); }

 private:
  std::ifstream is_;
  const int size_;
  std::vector<T> buffer_;
};

}  // namespace

using webrtc::test::ResourcePath;

void ExpectEqualFloatArray(rtc::ArrayView<const float> expected,
                           rtc::ArrayView<const float> computed) {
  ASSERT_EQ(expected.size(), computed.size());
  for (int i = 0; rtc::SafeLt(i, expected.size()); ++i) {
    SCOPED_TRACE(i);
    EXPECT_FLOAT_EQ(expected[i], computed[i]);
  }
}

void ExpectNearAbsolute(rtc::ArrayView<const float> expected,
                        rtc::ArrayView<const float> computed,
                        float tolerance) {
  ASSERT_EQ(expected.size(), computed.size());
  for (int i = 0; rtc::SafeLt(i, expected.size()); ++i) {
    SCOPED_TRACE(i);
    EXPECT_NEAR(expected[i], computed[i], tolerance);
  }
}

std::unique_ptr<FileReader> CreatePcmSamplesReader() {
  return std::make_unique<FloatFileReader<int16_t>>(
      /*filename=*/test::ResourcePath("audio_processing/agc2/rnn_vad/samples",
                                      "pcm"));
}

ChunksFileReader CreatePitchBuffer24kHzReader() {
  auto reader = std::make_unique<FloatFileReader<float>>(
      /*filename=*/test::ResourcePath(
          "audio_processing/agc2/rnn_vad/pitch_buf_24k", "dat"));
  const int num_chunks = rtc::CheckedDivExact(reader->size(), kBufSize24kHz);
  return {/*chunk_size=*/kBufSize24kHz, num_chunks, std::move(reader)};
}

ChunksFileReader CreateLpResidualAndPitchInfoReader() {
  constexpr int kPitchInfoSize = 2;  // Pitch period and strength.
  constexpr int kChunkSize = kBufSize24kHz + kPitchInfoSize;
  auto reader = std::make_unique<FloatFileReader<float>>(
      /*filename=*/test::ResourcePath(
          "audio_processing/agc2/rnn_vad/pitch_lp_res", "dat"));
  const int num_chunks = rtc::CheckedDivExact(reader->size(), kChunkSize);
  return {kChunkSize, num_chunks, std::move(reader)};
}

std::unique_ptr<FileReader> CreateGruInputReader() {
  return std::make_unique<FloatFileReader<float>>(
      /*filename=*/test::ResourcePath("audio_processing/agc2/rnn_vad/gru_in",
                                      "dat"));
}

std::unique_ptr<FileReader> CreateVadProbsReader() {
  return std::make_unique<FloatFileReader<float>>(
      /*filename=*/test::ResourcePath("audio_processing/agc2/rnn_vad/vad_prob",
                                      "dat"));
}

PitchTestData::PitchTestData() {
  FloatFileReader<float> reader(
      /*filename=*/ResourcePath(
          "audio_processing/agc2/rnn_vad/pitch_search_int", "dat"));
  reader.ReadChunk(pitch_buffer_24k_);
  reader.ReadChunk(square_energies_24k_);
  reader.ReadChunk(auto_correlation_12k_);
  // Reverse the order of the squared energy values.
  // Required after the WebRTC CL 191703 which switched to forward computation.
  std::reverse(square_energies_24k_.begin(), square_energies_24k_.end());
}

PitchTestData::~PitchTestData() = default;

}  // namespace rnn_vad
}  // namespace webrtc

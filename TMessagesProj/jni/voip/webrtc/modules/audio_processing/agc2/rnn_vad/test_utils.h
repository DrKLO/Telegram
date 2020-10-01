/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_RNN_VAD_TEST_UTILS_H_
#define MODULES_AUDIO_PROCESSING_AGC2_RNN_VAD_TEST_UTILS_H_

#include <algorithm>
#include <array>
#include <fstream>
#include <limits>
#include <memory>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

#include "api/array_view.h"
#include "modules/audio_processing/agc2/rnn_vad/common.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace rnn_vad {
namespace test {

constexpr float kFloatMin = std::numeric_limits<float>::min();

// Fails for every pair from two equally sized rtc::ArrayView<float> views such
// that the values in the pair do not match.
void ExpectEqualFloatArray(rtc::ArrayView<const float> expected,
                           rtc::ArrayView<const float> computed);

// Fails for every pair from two equally sized rtc::ArrayView<float> views such
// that their absolute error is above a given threshold.
void ExpectNearAbsolute(rtc::ArrayView<const float> expected,
                        rtc::ArrayView<const float> computed,
                        float tolerance);

// Reader for binary files consisting of an arbitrary long sequence of elements
// having type T. It is possible to read and cast to another type D at once.
template <typename T, typename D = T>
class BinaryFileReader {
 public:
  explicit BinaryFileReader(const std::string& file_path, size_t chunk_size = 0)
      : is_(file_path, std::ios::binary | std::ios::ate),
        data_length_(is_.tellg() / sizeof(T)),
        chunk_size_(chunk_size) {
    RTC_CHECK(is_);
    SeekBeginning();
    buf_.resize(chunk_size_);
  }
  BinaryFileReader(const BinaryFileReader&) = delete;
  BinaryFileReader& operator=(const BinaryFileReader&) = delete;
  ~BinaryFileReader() = default;
  size_t data_length() const { return data_length_; }
  bool ReadValue(D* dst) {
    if (std::is_same<T, D>::value) {
      is_.read(reinterpret_cast<char*>(dst), sizeof(T));
    } else {
      T v;
      is_.read(reinterpret_cast<char*>(&v), sizeof(T));
      *dst = static_cast<D>(v);
    }
    return is_.gcount() == sizeof(T);
  }
  // If |chunk_size| was specified in the ctor, it will check that the size of
  // |dst| equals |chunk_size|.
  bool ReadChunk(rtc::ArrayView<D> dst) {
    RTC_DCHECK((chunk_size_ == 0) || (chunk_size_ == dst.size()));
    const std::streamsize bytes_to_read = dst.size() * sizeof(T);
    if (std::is_same<T, D>::value) {
      is_.read(reinterpret_cast<char*>(dst.data()), bytes_to_read);
    } else {
      is_.read(reinterpret_cast<char*>(buf_.data()), bytes_to_read);
      std::transform(buf_.begin(), buf_.end(), dst.begin(),
                     [](const T& v) -> D { return static_cast<D>(v); });
    }
    return is_.gcount() == bytes_to_read;
  }
  void SeekForward(size_t items) { is_.seekg(items * sizeof(T), is_.cur); }
  void SeekBeginning() { is_.seekg(0, is_.beg); }

 private:
  std::ifstream is_;
  const size_t data_length_;
  const size_t chunk_size_;
  std::vector<T> buf_;
};

// Writer for binary files.
template <typename T>
class BinaryFileWriter {
 public:
  explicit BinaryFileWriter(const std::string& file_path)
      : os_(file_path, std::ios::binary) {}
  BinaryFileWriter(const BinaryFileWriter&) = delete;
  BinaryFileWriter& operator=(const BinaryFileWriter&) = delete;
  ~BinaryFileWriter() = default;
  static_assert(std::is_arithmetic<T>::value, "");
  void WriteChunk(rtc::ArrayView<const T> value) {
    const std::streamsize bytes_to_write = value.size() * sizeof(T);
    os_.write(reinterpret_cast<const char*>(value.data()), bytes_to_write);
  }

 private:
  std::ofstream os_;
};

// Factories for resource file readers.
// The functions below return a pair where the first item is a reader unique
// pointer and the second the number of chunks that can be read from the file.
// Creates a reader for the PCM samples that casts from S16 to float and reads
// chunks with length |frame_length|.
std::pair<std::unique_ptr<BinaryFileReader<int16_t, float>>, const size_t>
CreatePcmSamplesReader(const size_t frame_length);
// Creates a reader for the pitch buffer content at 24 kHz.
std::pair<std::unique_ptr<BinaryFileReader<float>>, const size_t>
CreatePitchBuffer24kHzReader();
// Creates a reader for the the LP residual coefficients and the pitch period
// and gain values.
std::pair<std::unique_ptr<BinaryFileReader<float>>, const size_t>
CreateLpResidualAndPitchPeriodGainReader();
// Creates a reader for the VAD probabilities.
std::pair<std::unique_ptr<BinaryFileReader<float>>, const size_t>
CreateVadProbsReader();

constexpr size_t kNumPitchBufAutoCorrCoeffs = 147;
constexpr size_t kNumPitchBufSquareEnergies = 385;
constexpr size_t kPitchTestDataSize =
    kBufSize24kHz + kNumPitchBufSquareEnergies + kNumPitchBufAutoCorrCoeffs;

// Class to retrieve a test pitch buffer content and the expected output for the
// analysis steps.
class PitchTestData {
 public:
  PitchTestData();
  ~PitchTestData();
  rtc::ArrayView<const float, kBufSize24kHz> GetPitchBufView() const;
  rtc::ArrayView<const float, kNumPitchBufSquareEnergies>
  GetPitchBufSquareEnergiesView() const;
  rtc::ArrayView<const float, kNumPitchBufAutoCorrCoeffs>
  GetPitchBufAutoCorrCoeffsView() const;

 private:
  std::array<float, kPitchTestDataSize> test_data_;
};

// Returns true if the given optimization is available.
bool IsOptimizationAvailable(Optimization optimization);

}  // namespace test
}  // namespace rnn_vad
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_RNN_VAD_TEST_UTILS_H_

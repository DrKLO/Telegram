/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/wav_file.h"

#include <errno.h>

#include <algorithm>
#include <array>
#include <cstdio>
#include <type_traits>
#include <utility>

#include "common_audio/include/audio_util.h"
#include "rtc_base/checks.h"
#include "rtc_base/system/arch.h"

namespace webrtc {
namespace {

static_assert(std::is_trivially_destructible<WavFormat>::value, "");

// Checks whether the format is supported or not.
bool FormatSupported(WavFormat format) {
  // Only PCM and IEEE Float formats are supported.
  return format == WavFormat::kWavFormatPcm ||
         format == WavFormat::kWavFormatIeeeFloat;
}

// Doesn't take ownership of the file handle and won't close it.
class WavHeaderFileReader : public WavHeaderReader {
 public:
  explicit WavHeaderFileReader(FileWrapper* file) : file_(file) {}

  WavHeaderFileReader(const WavHeaderFileReader&) = delete;
  WavHeaderFileReader& operator=(const WavHeaderFileReader&) = delete;

  size_t Read(void* buf, size_t num_bytes) override {
    size_t count = file_->Read(buf, num_bytes);
    pos_ += count;
    return count;
  }
  bool SeekForward(uint32_t num_bytes) override {
    bool success = file_->SeekRelative(num_bytes);
    if (success) {
      pos_ += num_bytes;
    }
    return success;
  }
  int64_t GetPosition() override { return pos_; }

 private:
  FileWrapper* file_;
  int64_t pos_ = 0;
};

constexpr size_t kMaxChunksize = 4096;

}  // namespace

WavReader::WavReader(const std::string& filename)
    : WavReader(FileWrapper::OpenReadOnly(filename)) {}

WavReader::WavReader(FileWrapper file) : file_(std::move(file)) {
  RTC_CHECK(file_.is_open())
      << "Invalid file. Could not create file handle for wav file.";

  WavHeaderFileReader readable(&file_);
  size_t bytes_per_sample;
  RTC_CHECK(ReadWavHeader(&readable, &num_channels_, &sample_rate_, &format_,
                          &bytes_per_sample, &num_samples_in_file_,
                          &data_start_pos_));
  num_unread_samples_ = num_samples_in_file_;
  RTC_CHECK(FormatSupported(format_)) << "Non-implemented wav-format";
}

void WavReader::Reset() {
  RTC_CHECK(file_.SeekTo(data_start_pos_))
      << "Failed to set position in the file to WAV data start position";
  num_unread_samples_ = num_samples_in_file_;
}

size_t WavReader::ReadSamples(const size_t num_samples,
                              int16_t* const samples) {
#ifndef WEBRTC_ARCH_LITTLE_ENDIAN
#error "Need to convert samples to big-endian when reading from WAV file"
#endif

  size_t num_samples_left_to_read = num_samples;
  size_t next_chunk_start = 0;
  while (num_samples_left_to_read > 0 && num_unread_samples_ > 0) {
    const size_t chunk_size = std::min(
        std::min(kMaxChunksize, num_samples_left_to_read), num_unread_samples_);
    size_t num_bytes_read;
    size_t num_samples_read;
    if (format_ == WavFormat::kWavFormatIeeeFloat) {
      std::array<float, kMaxChunksize> samples_to_convert;
      num_bytes_read = file_.Read(samples_to_convert.data(),
                                  chunk_size * sizeof(samples_to_convert[0]));
      num_samples_read = num_bytes_read / sizeof(samples_to_convert[0]);

      for (size_t j = 0; j < num_samples_read; ++j) {
        samples[next_chunk_start + j] = FloatToS16(samples_to_convert[j]);
      }
    } else {
      RTC_CHECK_EQ(format_, WavFormat::kWavFormatPcm);
      num_bytes_read = file_.Read(&samples[next_chunk_start],
                                  chunk_size * sizeof(samples[0]));
      num_samples_read = num_bytes_read / sizeof(samples[0]);
    }
    RTC_CHECK(num_samples_read == 0 || (num_bytes_read % num_samples_read) == 0)
        << "Corrupt file: file ended in the middle of a sample.";
    RTC_CHECK(num_samples_read == chunk_size || file_.ReadEof())
        << "Corrupt file: payload size does not match header.";

    next_chunk_start += num_samples_read;
    num_unread_samples_ -= num_samples_read;
    num_samples_left_to_read -= num_samples_read;
  }

  return num_samples - num_samples_left_to_read;
}

size_t WavReader::ReadSamples(const size_t num_samples, float* const samples) {
#ifndef WEBRTC_ARCH_LITTLE_ENDIAN
#error "Need to convert samples to big-endian when reading from WAV file"
#endif

  size_t num_samples_left_to_read = num_samples;
  size_t next_chunk_start = 0;
  while (num_samples_left_to_read > 0 && num_unread_samples_ > 0) {
    const size_t chunk_size = std::min(
        std::min(kMaxChunksize, num_samples_left_to_read), num_unread_samples_);
    size_t num_bytes_read;
    size_t num_samples_read;
    if (format_ == WavFormat::kWavFormatPcm) {
      std::array<int16_t, kMaxChunksize> samples_to_convert;
      num_bytes_read = file_.Read(samples_to_convert.data(),
                                  chunk_size * sizeof(samples_to_convert[0]));
      num_samples_read = num_bytes_read / sizeof(samples_to_convert[0]);

      for (size_t j = 0; j < num_samples_read; ++j) {
        samples[next_chunk_start + j] =
            static_cast<float>(samples_to_convert[j]);
      }
    } else {
      RTC_CHECK_EQ(format_, WavFormat::kWavFormatIeeeFloat);
      num_bytes_read = file_.Read(&samples[next_chunk_start],
                                  chunk_size * sizeof(samples[0]));
      num_samples_read = num_bytes_read / sizeof(samples[0]);

      for (size_t j = 0; j < num_samples_read; ++j) {
        samples[next_chunk_start + j] =
            FloatToFloatS16(samples[next_chunk_start + j]);
      }
    }
    RTC_CHECK(num_samples_read == 0 || (num_bytes_read % num_samples_read) == 0)
        << "Corrupt file: file ended in the middle of a sample.";
    RTC_CHECK(num_samples_read == chunk_size || file_.ReadEof())
        << "Corrupt file: payload size does not match header.";

    next_chunk_start += num_samples_read;
    num_unread_samples_ -= num_samples_read;
    num_samples_left_to_read -= num_samples_read;
  }

  return num_samples - num_samples_left_to_read;
}

void WavReader::Close() {
  file_.Close();
}

WavWriter::WavWriter(const std::string& filename,
                     int sample_rate,
                     size_t num_channels,
                     SampleFormat sample_format)
    // Unlike plain fopen, OpenWriteOnly takes care of filename utf8 ->
    // wchar conversion on windows.
    : WavWriter(FileWrapper::OpenWriteOnly(filename),
                sample_rate,
                num_channels,
                sample_format) {}

WavWriter::WavWriter(FileWrapper file,
                     int sample_rate,
                     size_t num_channels,
                     SampleFormat sample_format)
    : sample_rate_(sample_rate),
      num_channels_(num_channels),
      num_samples_written_(0),
      format_(sample_format == SampleFormat::kInt16
                  ? WavFormat::kWavFormatPcm
                  : WavFormat::kWavFormatIeeeFloat),
      file_(std::move(file)) {
  // Handle errors from the OpenWriteOnly call in above constructor.
  RTC_CHECK(file_.is_open()) << "Invalid file. Could not create wav file.";

  RTC_CHECK(CheckWavParameters(num_channels_, sample_rate_, format_,
                               num_samples_written_));

  // Write a blank placeholder header, since we need to know the total number
  // of samples before we can fill in the real data.
  static const uint8_t blank_header[MaxWavHeaderSize()] = {0};
  RTC_CHECK(file_.Write(blank_header, WavHeaderSize(format_)));
}

void WavWriter::WriteSamples(const int16_t* samples, size_t num_samples) {
#ifndef WEBRTC_ARCH_LITTLE_ENDIAN
#error "Need to convert samples to little-endian when writing to WAV file"
#endif

  for (size_t i = 0; i < num_samples; i += kMaxChunksize) {
    const size_t num_remaining_samples = num_samples - i;
    const size_t num_samples_to_write =
        std::min(kMaxChunksize, num_remaining_samples);

    if (format_ == WavFormat::kWavFormatPcm) {
      RTC_CHECK(
          file_.Write(&samples[i], num_samples_to_write * sizeof(samples[0])));
    } else {
      RTC_CHECK_EQ(format_, WavFormat::kWavFormatIeeeFloat);
      std::array<float, kMaxChunksize> converted_samples;
      for (size_t j = 0; j < num_samples_to_write; ++j) {
        converted_samples[j] = S16ToFloat(samples[i + j]);
      }
      RTC_CHECK(
          file_.Write(converted_samples.data(),
                      num_samples_to_write * sizeof(converted_samples[0])));
    }

    num_samples_written_ += num_samples_to_write;
    RTC_CHECK_GE(num_samples_written_,
                 num_samples_to_write);  // detect size_t overflow
  }
}

void WavWriter::WriteSamples(const float* samples, size_t num_samples) {
#ifndef WEBRTC_ARCH_LITTLE_ENDIAN
#error "Need to convert samples to little-endian when writing to WAV file"
#endif

  for (size_t i = 0; i < num_samples; i += kMaxChunksize) {
    const size_t num_remaining_samples = num_samples - i;
    const size_t num_samples_to_write =
        std::min(kMaxChunksize, num_remaining_samples);

    if (format_ == WavFormat::kWavFormatPcm) {
      std::array<int16_t, kMaxChunksize> converted_samples;
      for (size_t j = 0; j < num_samples_to_write; ++j) {
        converted_samples[j] = FloatS16ToS16(samples[i + j]);
      }
      RTC_CHECK(
          file_.Write(converted_samples.data(),
                      num_samples_to_write * sizeof(converted_samples[0])));
    } else {
      RTC_CHECK_EQ(format_, WavFormat::kWavFormatIeeeFloat);
      std::array<float, kMaxChunksize> converted_samples;
      for (size_t j = 0; j < num_samples_to_write; ++j) {
        converted_samples[j] = FloatS16ToFloat(samples[i + j]);
      }
      RTC_CHECK(
          file_.Write(converted_samples.data(),
                      num_samples_to_write * sizeof(converted_samples[0])));
    }

    num_samples_written_ += num_samples_to_write;
    RTC_CHECK(num_samples_written_ >=
              num_samples_to_write);  // detect size_t overflow
  }
}

void WavWriter::Close() {
  RTC_CHECK(file_.Rewind());
  std::array<uint8_t, MaxWavHeaderSize()> header;
  size_t header_size;
  WriteWavHeader(num_channels_, sample_rate_, format_, num_samples_written_,
                 header.data(), &header_size);
  RTC_CHECK(file_.Write(header.data(), header_size));
  RTC_CHECK(file_.Close());
}

}  // namespace webrtc

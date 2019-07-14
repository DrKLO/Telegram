/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef COMMON_AUDIO_WAV_FILE_H_
#define COMMON_AUDIO_WAV_FILE_H_

#ifdef __cplusplus

#include <stdint.h>
#include <cstddef>
#include <string>

#include "rtc_base/constructormagic.h"
#include "rtc_base/platform_file.h"

namespace webrtc {

// Interface to provide access to WAV file parameters.
class WavFile {
 public:
  virtual ~WavFile() {}

  virtual int sample_rate() const = 0;
  virtual size_t num_channels() const = 0;
  virtual size_t num_samples() const = 0;
};

// Simple C++ class for writing 16-bit PCM WAV files. All error handling is
// by calls to RTC_CHECK(), making it unsuitable for anything but debug code.
class WavWriter final : public WavFile {
 public:
  // Open a new WAV file for writing.
  WavWriter(const std::string& filename, int sample_rate, size_t num_channels);

  // Open a new WAV file for writing.
  WavWriter(rtc::PlatformFile file, int sample_rate, size_t num_channels);

  // Close the WAV file, after writing its header.
  ~WavWriter() override;

  // Write additional samples to the file. Each sample is in the range
  // [-32768,32767], and there must be the previously specified number of
  // interleaved channels.
  void WriteSamples(const float* samples, size_t num_samples);
  void WriteSamples(const int16_t* samples, size_t num_samples);

  int sample_rate() const override;
  size_t num_channels() const override;
  size_t num_samples() const override;

 private:
  void Close();
  const int sample_rate_;
  const size_t num_channels_;
  size_t num_samples_;  // Total number of samples written to file.
  FILE* file_handle_;   // Output file, owned by this class

  RTC_DISALLOW_COPY_AND_ASSIGN(WavWriter);
};

// Follows the conventions of WavWriter.
class WavReader final : public WavFile {
 public:
  // Opens an existing WAV file for reading.
  explicit WavReader(const std::string& filename);

  // Opens an existing WAV file for reading.
  explicit WavReader(rtc::PlatformFile file);

  // Close the WAV file.
  ~WavReader() override;

  // Returns the number of samples read. If this is less than requested,
  // verifies that the end of the file was reached.
  size_t ReadSamples(size_t num_samples, float* samples);
  size_t ReadSamples(size_t num_samples, int16_t* samples);

  int sample_rate() const override;
  size_t num_channels() const override;
  size_t num_samples() const override;

 private:
  void Close();
  int sample_rate_;
  size_t num_channels_;
  size_t num_samples_;  // Total number of samples in the file.
  size_t num_samples_remaining_;
  FILE* file_handle_;  // Input file, owned by this class.

  RTC_DISALLOW_COPY_AND_ASSIGN(WavReader);
};

}  // namespace webrtc

extern "C" {
#endif  // __cplusplus

// C wrappers for the WavWriter class.
typedef struct rtc_WavWriter rtc_WavWriter;
rtc_WavWriter* rtc_WavOpen(const char* filename,
                           int sample_rate,
                           size_t num_channels);
void rtc_WavClose(rtc_WavWriter* wf);
void rtc_WavWriteSamples(rtc_WavWriter* wf,
                         const float* samples,
                         size_t num_samples);
int rtc_WavSampleRate(const rtc_WavWriter* wf);
size_t rtc_WavNumChannels(const rtc_WavWriter* wf);
size_t rtc_WavNumSamples(const rtc_WavWriter* wf);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // COMMON_AUDIO_WAV_FILE_H_

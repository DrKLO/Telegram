/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/resample_input_audio_file.h"

#include <memory>

#include "rtc_base/checks.h"

namespace webrtc {
namespace test {

bool ResampleInputAudioFile::Read(size_t samples,
                                  int output_rate_hz,
                                  int16_t* destination) {
  const size_t samples_to_read = samples * file_rate_hz_ / output_rate_hz;
  RTC_CHECK_EQ(samples_to_read * output_rate_hz, samples * file_rate_hz_)
      << "Frame size and sample rates don't add up to an integer.";
  std::unique_ptr<int16_t[]> temp_destination(new int16_t[samples_to_read]);
  if (!InputAudioFile::Read(samples_to_read, temp_destination.get()))
    return false;
  resampler_.ResetIfNeeded(file_rate_hz_, output_rate_hz, 1);
  size_t output_length = 0;
  RTC_CHECK_EQ(resampler_.Push(temp_destination.get(), samples_to_read,
                               destination, samples, output_length),
               0);
  RTC_CHECK_EQ(samples, output_length);
  return true;
}

bool ResampleInputAudioFile::Read(size_t samples, int16_t* destination) {
  RTC_CHECK_GT(output_rate_hz_, 0) << "Output rate not set.";
  return Read(samples, output_rate_hz_, destination);
}

void ResampleInputAudioFile::set_output_rate_hz(int rate_hz) {
  output_rate_hz_ = rate_hz;
}

}  // namespace test
}  // namespace webrtc

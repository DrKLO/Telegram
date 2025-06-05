/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <cfloat>
#include <cstdio>
#include <cstdlib>
#include <memory>
#include <vector>

#include "modules/audio_processing/transient/file_utils.h"
#include "modules/audio_processing/transient/transient_detector.h"
#include "rtc_base/system/file_wrapper.h"

using webrtc::FileWrapper;
using webrtc::TransientDetector;

// Application to generate a RTP timing file.
// Opens the PCM file and divides the signal in frames.
// Creates a send times array, one for each step.
// Each block that contains a transient, has an infinite send time.
// The resultant array is written to a DAT file
// Returns -1 on error or `lost_packets` otherwise.
int main(int argc, char* argv[]) {
  if (argc != 5) {
    printf("\n%s - Application to generate a RTP timing file.\n\n", argv[0]);
    printf("%s PCMfile DATfile chunkSize sampleRate\n\n", argv[0]);
    printf("Opens the PCMfile with sampleRate in Hertz.\n");
    printf("Creates a send times array, one for each chunkSize ");
    printf("milliseconds step.\n");
    printf("Each block that contains a transient, has an infinite send time. ");
    printf("The resultant array is written to a DATfile.\n\n");
    return 0;
  }

  FileWrapper pcm_file = FileWrapper::OpenReadOnly(argv[1]);
  if (!pcm_file.is_open()) {
    printf("\nThe %s could not be opened.\n\n", argv[1]);
    return -1;
  }

  FileWrapper dat_file = FileWrapper::OpenWriteOnly(argv[2]);
  if (!dat_file.is_open()) {
    printf("\nThe %s could not be opened.\n\n", argv[2]);
    return -1;
  }

  int chunk_size_ms = atoi(argv[3]);
  if (chunk_size_ms <= 0) {
    printf("\nThe chunkSize must be a positive integer\n\n");
    return -1;
  }

  int sample_rate_hz = atoi(argv[4]);
  if (sample_rate_hz <= 0) {
    printf("\nThe sampleRate must be a positive integer\n\n");
    return -1;
  }

  TransientDetector detector(sample_rate_hz);
  int lost_packets = 0;
  size_t audio_buffer_length = chunk_size_ms * sample_rate_hz / 1000;
  std::unique_ptr<float[]> audio_buffer(new float[audio_buffer_length]);
  std::vector<float> send_times;

  // Read first buffer from the PCM test file.
  size_t file_samples_read = ReadInt16FromFileToFloatBuffer(
      &pcm_file, audio_buffer_length, audio_buffer.get());
  for (int time = 0; file_samples_read > 0; time += chunk_size_ms) {
    // Pad the rest of the buffer with zeros.
    for (size_t i = file_samples_read; i < audio_buffer_length; ++i) {
      audio_buffer[i] = 0.0;
    }
    float value =
        detector.Detect(audio_buffer.get(), audio_buffer_length, NULL, 0);
    if (value < 0.5f) {
      value = time;
    } else {
      value = FLT_MAX;
      ++lost_packets;
    }
    send_times.push_back(value);

    // Read next buffer from the PCM test file.
    file_samples_read = ReadInt16FromFileToFloatBuffer(
        &pcm_file, audio_buffer_length, audio_buffer.get());
  }

  size_t floats_written =
      WriteFloatBufferToFile(&dat_file, send_times.size(), &send_times[0]);

  if (floats_written == 0) {
    printf("\nThe send times could not be written to DAT file\n\n");
    return -1;
  }

  pcm_file.Close();
  dat_file.Close();

  return lost_packets;
}

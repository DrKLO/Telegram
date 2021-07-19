/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/transient/file_utils.h"

#include <memory>

#include "rtc_base/system/file_wrapper.h"

namespace webrtc {

int ConvertByteArrayToFloat(const uint8_t bytes[4], float* out) {
  if (!bytes || !out) {
    return -1;
  }

  uint32_t binary_value = 0;
  for (int i = 3; i >= 0; --i) {
    binary_value <<= 8;
    binary_value += bytes[i];
  }

  *out = bit_cast<float>(binary_value);

  return 0;
}

int ConvertByteArrayToDouble(const uint8_t bytes[8], double* out) {
  if (!bytes || !out) {
    return -1;
  }

  uint64_t binary_value = 0;
  for (int i = 7; i >= 0; --i) {
    binary_value <<= 8;
    binary_value += bytes[i];
  }

  *out = bit_cast<double>(binary_value);

  return 0;
}

int ConvertFloatToByteArray(float value, uint8_t out_bytes[4]) {
  if (!out_bytes) {
    return -1;
  }

  uint32_t binary_value = bit_cast<uint32_t>(value);
  for (size_t i = 0; i < 4; ++i) {
    out_bytes[i] = binary_value;
    binary_value >>= 8;
  }

  return 0;
}

int ConvertDoubleToByteArray(double value, uint8_t out_bytes[8]) {
  if (!out_bytes) {
    return -1;
  }

  uint64_t binary_value = bit_cast<uint64_t>(value);
  for (size_t i = 0; i < 8; ++i) {
    out_bytes[i] = binary_value;
    binary_value >>= 8;
  }

  return 0;
}

size_t ReadInt16BufferFromFile(FileWrapper* file,
                               size_t length,
                               int16_t* buffer) {
  if (!file || !file->is_open() || !buffer || length <= 0) {
    return 0;
  }

  std::unique_ptr<uint8_t[]> byte_array(new uint8_t[2]);

  size_t int16s_read = 0;

  while (int16s_read < length) {
    size_t bytes_read = file->Read(byte_array.get(), 2);
    if (bytes_read < 2) {
      break;
    }
    int16_t value = byte_array[1];
    value <<= 8;
    value += byte_array[0];
    buffer[int16s_read] = value;
    ++int16s_read;
  }

  return int16s_read;
}

size_t ReadInt16FromFileToFloatBuffer(FileWrapper* file,
                                      size_t length,
                                      float* buffer) {
  if (!file || !file->is_open() || !buffer || length <= 0) {
    return 0;
  }

  std::unique_ptr<int16_t[]> buffer16(new int16_t[length]);

  size_t int16s_read = ReadInt16BufferFromFile(file, length, buffer16.get());

  for (size_t i = 0; i < int16s_read; ++i) {
    buffer[i] = buffer16[i];
  }

  return int16s_read;
}

size_t ReadInt16FromFileToDoubleBuffer(FileWrapper* file,
                                       size_t length,
                                       double* buffer) {
  if (!file || !file->is_open() || !buffer || length <= 0) {
    return 0;
  }

  std::unique_ptr<int16_t[]> buffer16(new int16_t[length]);

  size_t int16s_read = ReadInt16BufferFromFile(file, length, buffer16.get());

  for (size_t i = 0; i < int16s_read; ++i) {
    buffer[i] = buffer16[i];
  }

  return int16s_read;
}

size_t ReadFloatBufferFromFile(FileWrapper* file,
                               size_t length,
                               float* buffer) {
  if (!file || !file->is_open() || !buffer || length <= 0) {
    return 0;
  }

  std::unique_ptr<uint8_t[]> byte_array(new uint8_t[4]);

  size_t floats_read = 0;

  while (floats_read < length) {
    size_t bytes_read = file->Read(byte_array.get(), 4);
    if (bytes_read < 4) {
      break;
    }
    ConvertByteArrayToFloat(byte_array.get(), &buffer[floats_read]);
    ++floats_read;
  }

  return floats_read;
}

size_t ReadDoubleBufferFromFile(FileWrapper* file,
                                size_t length,
                                double* buffer) {
  if (!file || !file->is_open() || !buffer || length <= 0) {
    return 0;
  }

  std::unique_ptr<uint8_t[]> byte_array(new uint8_t[8]);

  size_t doubles_read = 0;

  while (doubles_read < length) {
    size_t bytes_read = file->Read(byte_array.get(), 8);
    if (bytes_read < 8) {
      break;
    }
    ConvertByteArrayToDouble(byte_array.get(), &buffer[doubles_read]);
    ++doubles_read;
  }

  return doubles_read;
}

size_t WriteInt16BufferToFile(FileWrapper* file,
                              size_t length,
                              const int16_t* buffer) {
  if (!file || !file->is_open() || !buffer || length <= 0) {
    return 0;
  }

  std::unique_ptr<uint8_t[]> byte_array(new uint8_t[2]);

  size_t int16s_written = 0;

  for (int16s_written = 0; int16s_written < length; ++int16s_written) {
    // Get byte representation.
    byte_array[0] = buffer[int16s_written] & 0xFF;
    byte_array[1] = (buffer[int16s_written] >> 8) & 0xFF;

    file->Write(byte_array.get(), 2);
  }

  file->Flush();

  return int16s_written;
}

size_t WriteFloatBufferToFile(FileWrapper* file,
                              size_t length,
                              const float* buffer) {
  if (!file || !file->is_open() || !buffer || length <= 0) {
    return 0;
  }

  std::unique_ptr<uint8_t[]> byte_array(new uint8_t[4]);

  size_t floats_written = 0;

  for (floats_written = 0; floats_written < length; ++floats_written) {
    // Get byte representation.
    ConvertFloatToByteArray(buffer[floats_written], byte_array.get());

    file->Write(byte_array.get(), 4);
  }

  file->Flush();

  return floats_written;
}

size_t WriteDoubleBufferToFile(FileWrapper* file,
                               size_t length,
                               const double* buffer) {
  if (!file || !file->is_open() || !buffer || length <= 0) {
    return 0;
  }

  std::unique_ptr<uint8_t[]> byte_array(new uint8_t[8]);

  size_t doubles_written = 0;

  for (doubles_written = 0; doubles_written < length; ++doubles_written) {
    // Get byte representation.
    ConvertDoubleToByteArray(buffer[doubles_written], byte_array.get());

    file->Write(byte_array.get(), 8);
  }

  file->Flush();

  return doubles_written;
}

}  // namespace webrtc

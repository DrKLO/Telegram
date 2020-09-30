/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_TRANSIENT_FILE_UTILS_H_
#define MODULES_AUDIO_PROCESSING_TRANSIENT_FILE_UTILS_H_

#include <string.h>

#include "rtc_base/system/file_wrapper.h"

namespace webrtc {

// This is a copy of the cast included in the Chromium codebase here:
// http://cs.chromium.org/src/third_party/cld/base/casts.h
template <class Dest, class Source>
inline Dest bit_cast(const Source& source) {
  // A compile error here means your Dest and Source have different sizes.
  static_assert(sizeof(Dest) == sizeof(Source),
                "Dest and Source have different sizes");

  Dest dest;
  memcpy(&dest, &source, sizeof(dest));
  return dest;
}

// Converts the byte array with binary float representation to float.
// Bytes must be in little-endian order.
// Returns 0 if correct, -1 on error.
int ConvertByteArrayToFloat(const uint8_t bytes[4], float* out);

// Converts the byte array with binary double representation to double.
// Bytes must be in little-endian order.
// Returns 0 if correct, -1 on error.
int ConvertByteArrayToDouble(const uint8_t bytes[8], double* out);

// Converts a float to a byte array with binary float representation.
// Bytes will be in little-endian order.
// Returns 0 if correct, -1 on error.
int ConvertFloatToByteArray(float value, uint8_t out_bytes[4]);

// Converts a double to a byte array with binary double representation.
// Bytes will be in little-endian order.
// Returns 0 if correct, -1 on error.
int ConvertDoubleToByteArray(double value, uint8_t out_bytes[8]);

// Reads |length| 16-bit integers from |file| to |buffer|.
// |file| must be previously opened.
// Returns the number of 16-bit integers read or -1 on error.
size_t ReadInt16BufferFromFile(FileWrapper* file,
                               size_t length,
                               int16_t* buffer);

// Reads |length| 16-bit integers from |file| and stores those values
// (converting them) in |buffer|.
// |file| must be previously opened.
// Returns the number of 16-bit integers read or -1 on error.
size_t ReadInt16FromFileToFloatBuffer(FileWrapper* file,
                                      size_t length,
                                      float* buffer);

// Reads |length| 16-bit integers from |file| and stores those values
// (converting them) in |buffer|.
// |file| must be previously opened.
// Returns the number of 16-bit integers read or -1 on error.
size_t ReadInt16FromFileToDoubleBuffer(FileWrapper* file,
                                       size_t length,
                                       double* buffer);

// Reads |length| floats in binary representation (4 bytes) from |file| to
// |buffer|.
// |file| must be previously opened.
// Returns the number of floats read or -1 on error.
size_t ReadFloatBufferFromFile(FileWrapper* file, size_t length, float* buffer);

// Reads |length| doubles in binary representation (8 bytes) from |file| to
// |buffer|.
// |file| must be previously opened.
// Returns the number of doubles read or -1 on error.
size_t ReadDoubleBufferFromFile(FileWrapper* file,
                                size_t length,
                                double* buffer);

// Writes |length| 16-bit integers from |buffer| in binary representation (2
// bytes) to |file|. It flushes |file|, so after this call there are no
// writings pending.
// |file| must be previously opened.
// Returns the number of doubles written or -1 on error.
size_t WriteInt16BufferToFile(FileWrapper* file,
                              size_t length,
                              const int16_t* buffer);

// Writes |length| floats from |buffer| in binary representation (4 bytes) to
// |file|. It flushes |file|, so after this call there are no writtings pending.
// |file| must be previously opened.
// Returns the number of doubles written or -1 on error.
size_t WriteFloatBufferToFile(FileWrapper* file,
                              size_t length,
                              const float* buffer);

// Writes |length| doubles from |buffer| in binary representation (8 bytes) to
// |file|. It flushes |file|, so after this call there are no writings pending.
// |file| must be previously opened.
// Returns the number of doubles written or -1 on error.
size_t WriteDoubleBufferToFile(FileWrapper* file,
                               size_t length,
                               const double* buffer);

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_TRANSIENT_FILE_UTILS_H_

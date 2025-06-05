/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_HELPERS_H_
#define RTC_BASE_HELPERS_H_

#include <stddef.h>
#include <stdint.h>

#include <memory>
#include <string>

#include "absl/strings/string_view.h"
#include "rtc_base/system/rtc_export.h"

namespace rtc {

// Interface for RNG implementations.
class RandomGenerator {
 public:
  virtual ~RandomGenerator() {}
  virtual bool Init(const void* seed, size_t len) = 0;
  virtual bool Generate(void* buf, size_t len) = 0;
};

// Sets the default random generator as the source of randomness. The default
// source uses the OpenSSL RNG and provides cryptographically secure randomness.
void SetDefaultRandomGenerator();

// Set a custom random generator. Results produced by CreateRandomXyz
// are cryptographically random iff the output of the supplied generator is
// cryptographically random.
void SetRandomGenerator(std::unique_ptr<RandomGenerator> generator);

// For testing, we can return predictable data.
void SetRandomTestMode(bool test);

// Initializes the RNG, and seeds it with the specified entropy.
bool InitRandom(int seed);
bool InitRandom(const char* seed, size_t len);

// Generates a (cryptographically) random string of the given length.
// We generate base64 values so that they will be printable.
RTC_EXPORT std::string CreateRandomString(size_t length);

// Generates a (cryptographically) random string of the given length.
// We generate base64 values so that they will be printable.
// Return false if the random number generator failed.
RTC_EXPORT bool CreateRandomString(size_t length, std::string* str);

// Generates a (cryptographically) random string of the given length,
// with characters from the given table. Return false if the random
// number generator failed.
// For ease of implementation, the function requires that the table
// size evenly divide 256; otherwise, it returns false.
RTC_EXPORT bool CreateRandomString(size_t length,
                                   absl::string_view table,
                                   std::string* str);

// Generates (cryptographically) random data of the given length.
// Return false if the random number generator failed.
bool CreateRandomData(size_t length, std::string* data);

// Generates a (cryptographically) random UUID version 4 string.
std::string CreateRandomUuid();

// Generates a random id.
uint32_t CreateRandomId();

// Generates a 64 bit random id.
RTC_EXPORT uint64_t CreateRandomId64();

// Generates a random id > 0.
uint32_t CreateRandomNonZeroId();

// Generates a random double between 0.0 (inclusive) and 1.0 (exclusive).
double CreateRandomDouble();

// Compute moving average with the given ratio between the previous average
// value and the current value.
double GetNextMovingAverage(double prev_average, double cur, double ratio);

}  // namespace rtc

#endif  // RTC_BASE_HELPERS_H_

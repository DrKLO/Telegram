/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/helpers.h"

#include <openssl/rand.h>

#include <cstdint>
#include <limits>
#include <memory>

#include "absl/strings/string_view.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

// Protect against max macro inclusion.
#undef max

namespace rtc {

// Base class for RNG implementations.
class RandomGenerator {
 public:
  virtual ~RandomGenerator() {}
  virtual bool Init(const void* seed, size_t len) = 0;
  virtual bool Generate(void* buf, size_t len) = 0;
};

// The OpenSSL RNG.
class SecureRandomGenerator : public RandomGenerator {
 public:
  SecureRandomGenerator() {}
  ~SecureRandomGenerator() override {}
  bool Init(const void* seed, size_t len) override { return true; }
  bool Generate(void* buf, size_t len) override {
    return (RAND_bytes(reinterpret_cast<unsigned char*>(buf), len) > 0);
  }
};

// A test random generator, for predictable output.
class TestRandomGenerator : public RandomGenerator {
 public:
  TestRandomGenerator() : seed_(7) {}
  ~TestRandomGenerator() override {}
  bool Init(const void* seed, size_t len) override { return true; }
  bool Generate(void* buf, size_t len) override {
    for (size_t i = 0; i < len; ++i) {
      static_cast<uint8_t*>(buf)[i] = static_cast<uint8_t>(GetRandom());
    }
    return true;
  }

 private:
  int GetRandom() {
    return ((seed_ = seed_ * 214013L + 2531011L) >> 16) & 0x7fff;
  }
  int seed_;
};

namespace {

// TODO: Use Base64::Base64Table instead.
static const char kBase64[64] = {
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
    'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
    'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
    'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'};

static const char kHex[16] = {'0', '1', '2', '3', '4', '5', '6', '7',
                              '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

static const char kUuidDigit17[4] = {'8', '9', 'a', 'b'};

// This round about way of creating a global RNG is to safe-guard against
// indeterminant static initialization order.
std::unique_ptr<RandomGenerator>& GetGlobalRng() {
  static std::unique_ptr<RandomGenerator>& global_rng =
      *new std::unique_ptr<RandomGenerator>(new SecureRandomGenerator());

  return global_rng;
}

RandomGenerator& Rng() {
  return *GetGlobalRng();
}

}  // namespace

void SetRandomTestMode(bool test) {
  if (!test) {
    GetGlobalRng().reset(new SecureRandomGenerator());
  } else {
    GetGlobalRng().reset(new TestRandomGenerator());
  }
}

bool InitRandom(int seed) {
  return InitRandom(reinterpret_cast<const char*>(&seed), sizeof(seed));
}

bool InitRandom(const char* seed, size_t len) {
  if (!Rng().Init(seed, len)) {
    RTC_LOG(LS_ERROR) << "Failed to init random generator!";
    return false;
  }
  return true;
}

std::string CreateRandomString(size_t len) {
  std::string str;
  RTC_CHECK(CreateRandomString(len, &str));
  return str;
}

static bool CreateRandomString(size_t len,
                               const char* table,
                               int table_size,
                               std::string* str) {
  str->clear();
  // Avoid biased modulo division below.
  if (256 % table_size) {
    RTC_LOG(LS_ERROR) << "Table size must divide 256 evenly!";
    return false;
  }
  std::unique_ptr<uint8_t[]> bytes(new uint8_t[len]);
  if (!Rng().Generate(bytes.get(), len)) {
    RTC_LOG(LS_ERROR) << "Failed to generate random string!";
    return false;
  }
  str->reserve(len);
  for (size_t i = 0; i < len; ++i) {
    str->push_back(table[bytes[i] % table_size]);
  }
  return true;
}

bool CreateRandomString(size_t len, std::string* str) {
  return CreateRandomString(len, kBase64, 64, str);
}

bool CreateRandomString(size_t len, absl::string_view table, std::string* str) {
  return CreateRandomString(len, table.data(), static_cast<int>(table.size()),
                            str);
}

bool CreateRandomData(size_t length, std::string* data) {
  data->resize(length);
  // std::string is guaranteed to use contiguous memory in c++11 so we can
  // safely write directly to it.
  return Rng().Generate(&data->at(0), length);
}

// Version 4 UUID is of the form:
// xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
// Where 'x' is a hex digit, and 'y' is 8, 9, a or b.
std::string CreateRandomUuid() {
  std::string str;
  std::unique_ptr<uint8_t[]> bytes(new uint8_t[31]);
  RTC_CHECK(Rng().Generate(bytes.get(), 31));
  str.reserve(36);
  for (size_t i = 0; i < 8; ++i) {
    str.push_back(kHex[bytes[i] % 16]);
  }
  str.push_back('-');
  for (size_t i = 8; i < 12; ++i) {
    str.push_back(kHex[bytes[i] % 16]);
  }
  str.push_back('-');
  str.push_back('4');
  for (size_t i = 12; i < 15; ++i) {
    str.push_back(kHex[bytes[i] % 16]);
  }
  str.push_back('-');
  str.push_back(kUuidDigit17[bytes[15] % 4]);
  for (size_t i = 16; i < 19; ++i) {
    str.push_back(kHex[bytes[i] % 16]);
  }
  str.push_back('-');
  for (size_t i = 19; i < 31; ++i) {
    str.push_back(kHex[bytes[i] % 16]);
  }
  return str;
}

uint32_t CreateRandomId() {
  uint32_t id;
  RTC_CHECK(Rng().Generate(&id, sizeof(id)));
  return id;
}

uint64_t CreateRandomId64() {
  return static_cast<uint64_t>(CreateRandomId()) << 32 | CreateRandomId();
}

uint32_t CreateRandomNonZeroId() {
  uint32_t id;
  do {
    id = CreateRandomId();
  } while (id == 0);
  return id;
}

double CreateRandomDouble() {
  return CreateRandomId() / (std::numeric_limits<uint32_t>::max() +
                             std::numeric_limits<double>::epsilon());
}

double GetNextMovingAverage(double prev_average, double cur, double ratio) {
  return (ratio * prev_average + cur) / (ratio + 1);
}

}  // namespace rtc

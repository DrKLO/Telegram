// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <fuzzer/FuzzedDataProvider.h>

#include "base/macros.h"
#include "base/pickle.h"

namespace {
constexpr int kIterations = 16;
constexpr int kReadControlBytes = 32;
constexpr int kReadDataTypes = 17;
constexpr int kMaxReadLength = 1024;
constexpr int kMaxSkipBytes = 1024;
}  // namespace

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  if (size < kReadControlBytes) {
    return 0;
  }
  // Use the first kReadControlBytes bytes of the fuzzer input to control how
  // the pickled data is read.
  FuzzedDataProvider data_provider(data, kReadControlBytes);
  data += kReadControlBytes;
  size -= kReadControlBytes;

  base::Pickle pickle(reinterpret_cast<const char*>(data), size);
  base::PickleIterator iter(pickle);
  for (int i = 0; i < kIterations; i++) {
    uint8_t read_type = data_provider.ConsumeIntegral<uint8_t>();
    switch (read_type % kReadDataTypes) {
      case 0: {
        bool result = 0;
        ignore_result(iter.ReadBool(&result));
        break;
      }
      case 1: {
        int result = 0;
        ignore_result(iter.ReadInt(&result));
        break;
      }
      case 2: {
        long result = 0;
        ignore_result(iter.ReadLong(&result));
        break;
      }
      case 3: {
        uint16_t result = 0;
        ignore_result(iter.ReadUInt16(&result));
        break;
      }
      case 4: {
        uint32_t result = 0;
        ignore_result(iter.ReadUInt32(&result));
        break;
      }
      case 5: {
        int64_t result = 0;
        ignore_result(iter.ReadInt64(&result));
        break;
      }
      case 6: {
        uint64_t result = 0;
        ignore_result(iter.ReadUInt64(&result));
        break;
      }
      case 7: {
        float result = 0;
        ignore_result(iter.ReadFloat(&result));
        break;
      }
      case 8: {
        double result = 0;
        ignore_result(iter.ReadDouble(&result));
        break;
      }
      case 9: {
        std::string result;
        ignore_result(iter.ReadString(&result));
        break;
      }
      case 10: {
        base::StringPiece result;
        ignore_result(iter.ReadStringPiece(&result));
        break;
      }
      case 11: {
        base::string16 result;
        ignore_result(iter.ReadString16(&result));
        break;
      }
      case 12: {
        base::StringPiece16 result;
        ignore_result(iter.ReadStringPiece16(&result));
        break;
      }
      case 13: {
        const char* data_result = nullptr;
        int length_result = 0;
        ignore_result(iter.ReadData(&data_result, &length_result));
        break;
      }
      case 14: {
        const char* data_result = nullptr;
        int read_length =
            data_provider.ConsumeIntegralInRange(0, kMaxReadLength);
        ignore_result(iter.ReadBytes(&data_result, read_length));
        break;
      }
      case 15: {
        int result = 0;
        ignore_result(iter.ReadLength(&result));
        break;
      }
      case 16: {
        ignore_result(iter.SkipBytes(
            data_provider.ConsumeIntegralInRange(0, kMaxSkipBytes)));
        break;
      }
    }
  }

  return 0;
}

// Copyright (c) 2011, Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

// memory_range_unittest.cc: Unit tests for google_breakpad::MemoryRange.

#include "breakpad_googletest_includes.h"
#include "common/memory_range.h"

using google_breakpad::MemoryRange;
using testing::Message;

namespace {

const uint32_t kBuffer[10] = { 0 };
const size_t kBufferSize = sizeof(kBuffer);
const uint8_t* kBufferPointer = reinterpret_cast<const uint8_t*>(kBuffer);

// Test vectors for verifying Covers, GetData, and Subrange.
const struct {
  bool valid;
  size_t offset;
  size_t length;
} kSubranges[] = {
  { true, 0, 0 },
  { true, 0, 2 },
  { true, 0, kBufferSize },
  { true, 2, 0 },
  { true, 2, 4 },
  { true, 2, kBufferSize - 2 },
  { true, kBufferSize - 1, 1 },
  { false, kBufferSize, 0 },
  { false, kBufferSize, static_cast<size_t>(-1) },
  { false, kBufferSize + 1, 0 },
  { false, static_cast<size_t>(-1), 2 },
  { false, 1, kBufferSize },
  { false, kBufferSize - 1, 2 },
  { false, 0, static_cast<size_t>(-1) },
  { false, 1, static_cast<size_t>(-1) },
};
const size_t kNumSubranges = sizeof(kSubranges) / sizeof(kSubranges[0]);

// Test vectors for verifying GetArrayElement.
const struct {
  size_t offset;
  size_t size;
  size_t index;
  const void* const pointer;
} kElements[] = {
  // Valid array elemenets
  { 0, 1, 0, kBufferPointer },
  { 0, 1, 1, kBufferPointer + 1 },
  { 0, 1, kBufferSize - 1, kBufferPointer + kBufferSize - 1 },
  { 0, 2, 1, kBufferPointer + 2 },
  { 0, 4, 2, kBufferPointer + 8 },
  { 0, 4, 9, kBufferPointer + 36 },
  { kBufferSize - 1, 1, 0, kBufferPointer + kBufferSize - 1 },
  // Invalid array elemenets
  { 0, 1, kBufferSize, NULL },
  { 0, 4, 10, NULL },
  { kBufferSize - 1, 1, 1, NULL },
  { kBufferSize - 1, 2, 0, NULL },
  { kBufferSize, 1, 0, NULL },
};
const size_t kNumElements = sizeof(kElements) / sizeof(kElements[0]);

}  // namespace

TEST(MemoryRangeTest, DefaultConstructor) {
  MemoryRange range;
  EXPECT_EQ(NULL, range.data());
  EXPECT_EQ(0U, range.length());
}

TEST(MemoryRangeTest, ConstructorWithDataAndLength) {
  MemoryRange range(kBuffer, kBufferSize);
  EXPECT_EQ(kBufferPointer, range.data());
  EXPECT_EQ(kBufferSize, range.length());
}

TEST(MemoryRangeTest, Reset) {
  MemoryRange range;
  range.Reset();
  EXPECT_EQ(NULL, range.data());
  EXPECT_EQ(0U, range.length());

  range.Set(kBuffer, kBufferSize);
  EXPECT_EQ(kBufferPointer, range.data());
  EXPECT_EQ(kBufferSize, range.length());

  range.Reset();
  EXPECT_EQ(NULL, range.data());
  EXPECT_EQ(0U, range.length());
}

TEST(MemoryRangeTest, Set) {
  MemoryRange range;
  range.Set(kBuffer, kBufferSize);
  EXPECT_EQ(kBufferPointer, range.data());
  EXPECT_EQ(kBufferSize, range.length());

  range.Set(NULL, 0);
  EXPECT_EQ(NULL, range.data());
  EXPECT_EQ(0U, range.length());
}

TEST(MemoryRangeTest, SubrangeOfEmptyMemoryRange) {
  MemoryRange range;
  MemoryRange subrange = range.Subrange(0, 10);
  EXPECT_EQ(NULL, subrange.data());
  EXPECT_EQ(0U, subrange.length());
}

TEST(MemoryRangeTest, SubrangeAndGetData) {
  MemoryRange range(kBuffer, kBufferSize);
  for (size_t i = 0; i < kNumSubranges; ++i) {
    bool valid = kSubranges[i].valid;
    size_t sub_offset = kSubranges[i].offset;
    size_t sub_length = kSubranges[i].length;
    SCOPED_TRACE(Message() << "offset=" << sub_offset
                 << ", length=" << sub_length);

    MemoryRange subrange = range.Subrange(sub_offset, sub_length);
    if (valid) {
      EXPECT_TRUE(range.Covers(sub_offset, sub_length));
      EXPECT_EQ(kBufferPointer + sub_offset,
                range.GetData(sub_offset, sub_length));
      EXPECT_EQ(kBufferPointer + sub_offset, subrange.data());
      EXPECT_EQ(sub_length, subrange.length());
    } else {
      EXPECT_FALSE(range.Covers(sub_offset, sub_length));
      EXPECT_EQ(NULL, range.GetData(sub_offset, sub_length));
      EXPECT_EQ(NULL, subrange.data());
      EXPECT_EQ(0U, subrange.length());
    }
  }
}

TEST(MemoryRangeTest, GetDataWithTemplateType) {
  MemoryRange range(kBuffer, kBufferSize);
  const char* char_pointer = range.GetData<char>(0);
  EXPECT_EQ(reinterpret_cast<const char*>(kBufferPointer), char_pointer);
  const int* int_pointer = range.GetData<int>(0);
  EXPECT_EQ(reinterpret_cast<const int*>(kBufferPointer), int_pointer);
}

TEST(MemoryRangeTest, GetArrayElement) {
  MemoryRange range(kBuffer, kBufferSize);
  for (size_t i = 0; i < kNumElements; ++i) {
    size_t element_offset = kElements[i].offset;
    size_t element_size = kElements[i].size;
    unsigned element_index = kElements[i].index;
    const void* const element_pointer = kElements[i].pointer;
    SCOPED_TRACE(Message() << "offset=" << element_offset
                 << ", size=" << element_size
                 << ", index=" << element_index);
    EXPECT_EQ(element_pointer, range.GetArrayElement(
        element_offset, element_size, element_index));
  }
}

TEST(MemoryRangeTest, GetArrayElmentWithTemplateType) {
  MemoryRange range(kBuffer, kBufferSize);
  const char* char_pointer = range.GetArrayElement<char>(0, 0);
  EXPECT_EQ(reinterpret_cast<const char*>(kBufferPointer), char_pointer);
  const int* int_pointer = range.GetArrayElement<int>(0, 0);
  EXPECT_EQ(reinterpret_cast<const int*>(kBufferPointer), int_pointer);
}

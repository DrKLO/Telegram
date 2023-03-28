// Copyright (c) 2010, Google Inc.
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

// static_contained_range_map_unittest.cc: Unit tests for
// StaticContainedRangeMap.
//
// Author: Siyang Xie (lambxsy@google.com)

#include "breakpad_googletest_includes.h"
#include "common/scoped_ptr.h"
#include "processor/contained_range_map-inl.h"
#include "processor/static_contained_range_map-inl.h"
#include "processor/simple_serializer-inl.h"
#include "processor/map_serializers-inl.h"
#include "processor/logging.h"

namespace {

typedef google_breakpad::ContainedRangeMap<unsigned int, int> CRMMap;
typedef google_breakpad::StaticContainedRangeMap<unsigned int, int> TestMap;

// Each element in test_data contains the expected result when calling
// RetrieveRange on an address.
const int test_data[] = {
  0,   // 0
  0,   // 1
  0,   // 2
  0,   // 3
  0,   // 4
  0,   // 5
  0,   // 6
  0,   // 7
  9,   // 8
  7,   // 9
  1,   // 10
  5,   // 11
  6,   // 12
  6,   // 13
  6,   // 14
  6,   // 15
  6,   // 16
  6,   // 17
  6,   // 18
  5,   // 19
  7,   // 20
  8,   // 21
  0,   // 22
  0,   // 23
  0,   // 24
  0,   // 25
  0,   // 26
  0,   // 27
  0,   // 28
  0,   // 29
  10,  // 30
  10,  // 31
  10,  // 32
  11,  // 33
  11,  // 34
  11,  // 35
  0,   // 36
  0,   // 37
  0,   // 38
  0,   // 39
  14,  // 40
  14,  // 41
  14,  // 42
  14,  // 43
  15,  // 44
  15,  // 45
  15,  // 46
  15,  // 47
  0,   // 48
  0,   // 49
  19,  // 50
  18,  // 51
  18,  // 52
  18,  // 53
  18,  // 54
  18,  // 55
  18,  // 56
  18,  // 57
  18,  // 58
  20,  // 59
  21,  // 60
  25,  // 61
  26,  // 62
  26,  // 63
  26,  // 64
  26,  // 65
  26,  // 66
  26,  // 67
  24,  // 68
  22,  // 69
  30,  // 70
  30,  // 71
  30,  // 72
  30,  // 73
  31,  // 74
  31,  // 75
  30,  // 76
  32,  // 77
  32,  // 78
  30,  // 79
  34,  // 80
  35,  // 81
  36,  // 82
  39,  // 83
  38,  // 84
  37,  // 85
  43,  // 86
  44,  // 87
  41,  // 88
  45,  // 89
  42,  // 90
  0,   // 91
  0,   // 92
  0,   // 93
  0,   // 94
  0,   // 95
  0,   // 96
  0,   // 97
  0,   // 98
  0    // 99
};

}  // namespace

namespace google_breakpad {

class TestStaticCRMMap : public ::testing::Test {
 protected:
  void SetUp();

  // A referrence map for testing StaticCRMMap.
  google_breakpad::ContainedRangeMap<unsigned int, int> crm_map_;

  // Static version of crm_map using serialized data of crm_map.
  // The goal of testing is to make sure TestMap provides same results for
  // lookup operation(s) as CRMMap does.
  google_breakpad::StaticContainedRangeMap<unsigned int, int> test_map_;

  google_breakpad::ContainedRangeMapSerializer<unsigned int, int> serializer_;

  scoped_array<char> serialized_data_;
};

void TestStaticCRMMap::SetUp() {
  // First, do the StoreRange tests.  This validates the containment
  // rules.
  // We confirm the referrence map correctly stores data during setup.
  ASSERT_TRUE (crm_map_.StoreRange(10, 10,  1));
  ASSERT_FALSE(crm_map_.StoreRange(10, 10,  2));  // exactly equal to 1
  ASSERT_FALSE(crm_map_.StoreRange(11, 10,  3));  // begins inside 1 and extends up
  ASSERT_FALSE(crm_map_.StoreRange( 9, 10,  4));  // begins below 1 and ends inside
  ASSERT_TRUE (crm_map_.StoreRange(11,  9,  5));  // contained by existing
  ASSERT_TRUE (crm_map_.StoreRange(12,  7,  6));
  ASSERT_TRUE (crm_map_.StoreRange( 9, 12,  7));  // contains existing
  ASSERT_TRUE (crm_map_.StoreRange( 9, 13,  8));
  ASSERT_TRUE (crm_map_.StoreRange( 8, 14,  9));
  ASSERT_TRUE (crm_map_.StoreRange(30,  3, 10));
  ASSERT_TRUE (crm_map_.StoreRange(33,  3, 11));
  ASSERT_TRUE (crm_map_.StoreRange(30,  6, 12));  // storable but totally masked
  ASSERT_TRUE (crm_map_.StoreRange(40,  8, 13));  // will be totally masked
  ASSERT_TRUE (crm_map_.StoreRange(40,  4, 14));
  ASSERT_TRUE (crm_map_.StoreRange(44,  4, 15));
  ASSERT_FALSE(crm_map_.StoreRange(32, 10, 16));  // begins in #10, ends in #14
  ASSERT_FALSE(crm_map_.StoreRange(50,  0, 17));  // zero length
  ASSERT_TRUE (crm_map_.StoreRange(50, 10, 18));
  ASSERT_TRUE (crm_map_.StoreRange(50,  1, 19));
  ASSERT_TRUE (crm_map_.StoreRange(59,  1, 20));
  ASSERT_TRUE (crm_map_.StoreRange(60,  1, 21));
  ASSERT_TRUE (crm_map_.StoreRange(69,  1, 22));
  ASSERT_TRUE (crm_map_.StoreRange(60, 10, 23));
  ASSERT_TRUE (crm_map_.StoreRange(68,  1, 24));
  ASSERT_TRUE (crm_map_.StoreRange(61,  1, 25));
  ASSERT_TRUE (crm_map_.StoreRange(61,  8, 26));
  ASSERT_FALSE(crm_map_.StoreRange(59,  9, 27));
  ASSERT_FALSE(crm_map_.StoreRange(59, 10, 28));
  ASSERT_FALSE(crm_map_.StoreRange(59, 11, 29));
  ASSERT_TRUE (crm_map_.StoreRange(70, 10, 30));
  ASSERT_TRUE (crm_map_.StoreRange(74,  2, 31));
  ASSERT_TRUE (crm_map_.StoreRange(77,  2, 32));
  ASSERT_FALSE(crm_map_.StoreRange(72,  6, 33));
  ASSERT_TRUE (crm_map_.StoreRange(80,  3, 34));
  ASSERT_TRUE (crm_map_.StoreRange(81,  1, 35));
  ASSERT_TRUE (crm_map_.StoreRange(82,  1, 36));
  ASSERT_TRUE (crm_map_.StoreRange(83,  3, 37));
  ASSERT_TRUE (crm_map_.StoreRange(84,  1, 38));
  ASSERT_TRUE (crm_map_.StoreRange(83,  1, 39));
  ASSERT_TRUE (crm_map_.StoreRange(86,  5, 40));
  ASSERT_TRUE (crm_map_.StoreRange(88,  1, 41));
  ASSERT_TRUE (crm_map_.StoreRange(90,  1, 42));
  ASSERT_TRUE (crm_map_.StoreRange(86,  1, 43));
  ASSERT_TRUE (crm_map_.StoreRange(87,  1, 44));
  ASSERT_TRUE (crm_map_.StoreRange(89,  1, 45));
  ASSERT_TRUE (crm_map_.StoreRange(87,  4, 46));
  ASSERT_TRUE (crm_map_.StoreRange(87,  3, 47));
  ASSERT_FALSE(crm_map_.StoreRange(86,  2, 48));

  // Serialize crm_map to generate serialized data.
  unsigned int size;
  serialized_data_.reset(serializer_.Serialize(&crm_map_, &size));
  BPLOG(INFO) << "Serialized data size: " << size << " Bytes.";

  // Construct test_map_ from serialized data.
  test_map_ = TestMap(serialized_data_.get());
}

TEST_F(TestStaticCRMMap, TestEmptyMap) {
  CRMMap empty_crm_map;

  unsigned int size;
  scoped_array<char> serialized_data;
  serialized_data.reset(serializer_.Serialize(&empty_crm_map, &size));
  scoped_ptr<TestMap> test_map(new TestMap(serialized_data.get()));

  const unsigned int kCorrectSizeForEmptyMap = 16;
  ASSERT_EQ(kCorrectSizeForEmptyMap, size);

  const int *entry_test;
  ASSERT_FALSE(test_map->RetrieveRange(-1, entry_test));
  ASSERT_FALSE(test_map->RetrieveRange(0, entry_test));
  ASSERT_FALSE(test_map->RetrieveRange(10, entry_test));
}

TEST_F(TestStaticCRMMap, TestSingleElementMap) {
  CRMMap crm_map;
  // Test on one element:
  int entry = 1;
  crm_map.StoreRange(10, 10,  entry);

  unsigned int size;
  scoped_array<char> serialized_data;
  serialized_data.reset(serializer_.Serialize(&crm_map, &size));
  scoped_ptr<TestMap> test_map(new TestMap(serialized_data.get()));

  const unsigned int kCorrectSizeForSingleElementMap = 40;
  ASSERT_EQ(kCorrectSizeForSingleElementMap, size);

  const int *entry_test;
  ASSERT_FALSE(test_map->RetrieveRange(-1, entry_test));
  ASSERT_FALSE(test_map->RetrieveRange(0, entry_test));
  ASSERT_TRUE(test_map->RetrieveRange(10, entry_test));
  ASSERT_EQ(*entry_test, entry);
  ASSERT_TRUE(test_map->RetrieveRange(13, entry_test));
  ASSERT_EQ(*entry_test, entry);
}

TEST_F(TestStaticCRMMap, RunTestData) {
  unsigned int test_high = sizeof(test_data) / sizeof(test_data[0]);

  // Now, do the RetrieveRange tests.  This further validates that the
  // objects were stored properly and that retrieval returns the correct
  // object.
  // If GENERATE_TEST_DATA is defined, instead of the retrieval tests, a
  // new test_data array will be printed.  Exercise caution when doing this.
  // Be sure to verify the results manually!
#ifdef GENERATE_TEST_DATA
  printf("  const int test_data[] = {\n");
#endif  // GENERATE_TEST_DATA

  for (unsigned int address = 0; address < test_high; ++address) {
    const int *entryptr;
    int value = 0;
    if (test_map_.RetrieveRange(address, entryptr))
      value = *entryptr;

#ifndef GENERATE_TEST_DATA
    // Don't use ASSERT inside the loop because it won't show the failed
    // |address|, and the line number will always be the same.  That makes
    // it difficult to figure out which test failed.
    EXPECT_EQ(value, test_data[address]) << "FAIL: retrieve address "
                                         << address;
#else  // !GENERATE_TEST_DATA
    printf("    %d%c%s  // %d\n", value,
                                  address == test_high - 1 ? ' ' : ',',
                                  value < 10 ? " " : "",
                                  address);
#endif  // !GENERATE_TEST_DATA
  }

#ifdef GENERATE_TEST_DATA
  printf("  };\n");
#endif  // GENERATE_TEST_DATA
}

}  // namespace google_breakpad

int main(int argc, char *argv[]) {
  ::testing::InitGoogleTest(&argc, argv);

  return RUN_ALL_TESTS();
}

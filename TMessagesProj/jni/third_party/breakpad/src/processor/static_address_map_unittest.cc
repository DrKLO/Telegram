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

// static_address_map_unittest.cc: Unit tests for StaticAddressMap.
//
// Author: Siyang Xie (lambxsy@google.com)

#include <climits>
#include <cstdlib>
#include <ctime>
#include <string>
#include <iostream>
#include <sstream>

#include "breakpad_googletest_includes.h"
#include "common/using_std_string.h"
#include "processor/address_map-inl.h"
#include "processor/static_address_map-inl.h"
#include "processor/simple_serializer-inl.h"
#include "map_serializers-inl.h"

typedef google_breakpad::StaticAddressMap<int, char> TestMap;
typedef google_breakpad::AddressMap<int, string> AddrMap;

class TestStaticAddressMap : public ::testing::Test {
 protected:
  void SetUp() {
    for (int testcase = 0; testcase < kNumberTestCases; ++testcase) {
      testdata[testcase] = new int[testsize[testcase]];
    }

    // Test data set0: NULL (empty map)

    // Test data set1: single element.
    testdata[1][0] = 10;

    // Test data set2: six elements.
    const int tempdata[] = {5, 10, 14, 15, 16, 20};
    for (int i = 0; i < testsize[2]; ++i)
      testdata[2][i] = tempdata[i];

    // Test data set3:
    srand(time(NULL));
    for (int i = 0; i < testsize[3]; ++i)
      testdata[3][i] = rand();

    // Setup maps.
    std::stringstream sstream;
    for (int testcase = 0; testcase < kNumberTestCases; ++testcase) {
      for (int data_item = 0; data_item < testsize[testcase]; ++data_item) {
        sstream.clear();
        sstream << "test " << testdata[testcase][data_item];
        addr_map[testcase].Store(testdata[testcase][data_item], sstream.str());
      }
      map_data[testcase] = serializer.Serialize(addr_map[testcase], NULL);
      test_map[testcase] = TestMap(map_data[testcase]);
    }
  }

  void TearDown() {
    for (int i = 0; i < kNumberTestCases; ++i) {
      delete [] map_data[i];
      delete [] testdata[i];
    }
  }

  void CompareRetrieveResult(int testcase, int target) {
    int address;
    int address_test;
    string entry;
    string entry_test;
    const char *entry_cstring = NULL;
    bool found;
    bool found_test;

    found = addr_map[testcase].Retrieve(target, &entry, &address);
    found_test =
        test_map[testcase].Retrieve(target, entry_cstring, &address_test);

    ASSERT_EQ(found, found_test);

    if (found && found_test) {
      ASSERT_EQ(address, address_test);
      entry_test = entry_cstring;
      ASSERT_EQ(entry, entry_test);
    }
  }

  void RetrieveTester(int testcase) {
    int target;
    target = INT_MIN;
    CompareRetrieveResult(testcase, target);
    target = INT_MAX;
    CompareRetrieveResult(testcase, target);

    srand(time(0));
    for (int data_item = 0; data_item < testsize[testcase]; ++data_item) {
      // Retrive (aka, search) for target address and compare results from
      // AddressMap and StaticAddressMap.

      // First, assign the search target to be one of original testdata that is
      // known to exist in the map.
      target = testdata[testcase][data_item];
      CompareRetrieveResult(testcase, target);
      // Then, add +2 / -1 bias to target value, in order to test searching for
      // a target address not stored in the map.
      target -= 1;
      CompareRetrieveResult(testcase, target);
      target += 3;
      CompareRetrieveResult(testcase, target);
      // Repeatedly test searching for random target addresses.
      target = rand();
      CompareRetrieveResult(testcase, target);
    }
  }

  // Test data sets:
  static const int kNumberTestCases = 4;
  static const int testsize[];
  int *testdata[kNumberTestCases];

  AddrMap addr_map[kNumberTestCases];
  TestMap test_map[kNumberTestCases];
  char *map_data[kNumberTestCases];
  google_breakpad::AddressMapSerializer<int, string> serializer;
};

const int TestStaticAddressMap::testsize[] = {0, 1, 6, 1000};

TEST_F(TestStaticAddressMap, TestEmptyMap) {
  int testcase = 0;
  int target;
  target = INT_MIN;
  CompareRetrieveResult(testcase, target);
  target = INT_MAX;
  CompareRetrieveResult(testcase, target);
  for (int data_item = 0; data_item < testsize[testcase]; ++data_item) {
    target = testdata[testcase][data_item];
    CompareRetrieveResult(testcase, target);
    target -= 1;
    CompareRetrieveResult(testcase, target);
    target += 3;
    CompareRetrieveResult(testcase, target);
    target = rand();
    CompareRetrieveResult(testcase, target);
  }
}

TEST_F(TestStaticAddressMap, TestOneElementMap) {
  int testcase = 1;
  int target;
  target = INT_MIN;
  CompareRetrieveResult(testcase, target);
  target = INT_MAX;
  CompareRetrieveResult(testcase, target);
  for (int data_item = 0; data_item < testsize[testcase]; ++data_item) {
    target = testdata[testcase][data_item];
    CompareRetrieveResult(testcase, target);
    target -= 1;
    CompareRetrieveResult(testcase, target);
    target += 3;
    CompareRetrieveResult(testcase, target);
    target = rand();
    CompareRetrieveResult(testcase, target);
  }
}

TEST_F(TestStaticAddressMap, TestSixElementsMap) {
  int testcase = 2;
  int target;
  target = INT_MIN;
  CompareRetrieveResult(testcase, target);
  target = INT_MAX;
  CompareRetrieveResult(testcase, target);
  for (int data_item = 0; data_item < testsize[testcase]; ++data_item) {
    target = testdata[testcase][data_item];
    CompareRetrieveResult(testcase, target);
    target -= 1;
    CompareRetrieveResult(testcase, target);
    target += 3;
    CompareRetrieveResult(testcase, target);
    target = rand();
    CompareRetrieveResult(testcase, target);
  }
}

TEST_F(TestStaticAddressMap, Test1000RandomElementsMap) {
  int testcase = 3;
  int target;
  target = INT_MIN;
  CompareRetrieveResult(testcase, target);
  target = INT_MAX;
  CompareRetrieveResult(testcase, target);
  for (int data_item = 0; data_item < testsize[testcase]; ++data_item) {
    target = testdata[testcase][data_item];
    CompareRetrieveResult(testcase, target);
    target -= 1;
    CompareRetrieveResult(testcase, target);
    target += 3;
    CompareRetrieveResult(testcase, target);
    target = rand();
    CompareRetrieveResult(testcase, target);
  }
}

int main(int argc, char *argv[]) {
  ::testing::InitGoogleTest(&argc, argv);

  return RUN_ALL_TESTS();
}

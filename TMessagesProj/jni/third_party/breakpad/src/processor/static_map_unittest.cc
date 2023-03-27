// Copyright (c) 2010 Google Inc.
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

// static_map_unittest.cc: Unit tests for StaticMap.
//
// Author: Siyang Xie (lambxsy@google.com)

#include <climits>
#include <map>

#include "breakpad_googletest_includes.h"
#include "processor/static_map-inl.h"


typedef int ValueType;
typedef int KeyType;
typedef google_breakpad::StaticMap< KeyType, ValueType > TestMap;
typedef std::map< KeyType, ValueType > StdMap;

template<typename Key, typename Value>
class SimpleMapSerializer {
 public:
  static char* Serialize(const std::map<Key, Value> &stdmap,
                   unsigned int* size = NULL) {
    unsigned int size_per_node =
        sizeof(uint32_t) + sizeof(Key) + sizeof(Value);
    unsigned int memsize = sizeof(int32_t) + size_per_node * stdmap.size();
    if (size) *size = memsize;

    // Allocate memory for serialized data:
    char* mem = reinterpret_cast<char*>(operator new(memsize));
    char* address = mem;

    // Writer the number of nodes:
    new (address) uint32_t(static_cast<uint32_t>(stdmap.size()));
    address += sizeof(uint32_t);

    // Nodes' offset:
    uint32_t* offsets = reinterpret_cast<uint32_t*>(address);
    address += sizeof(uint32_t) * stdmap.size();

    // Keys:
    Key* keys = reinterpret_cast<Key*>(address);
    address += sizeof(Key) * stdmap.size();

    // Traversing map:
    typename std::map<Key, Value>::const_iterator iter = stdmap.begin();
    for (int index = 0; iter != stdmap.end(); ++iter, ++index) {
      offsets[index] = static_cast<unsigned int>(address - mem);
      keys[index] = iter->first;
      new (address) Value(iter->second);
      address += sizeof(Value);
    }
    return mem;
  }
};


class TestInvalidMap : public ::testing::Test {
 protected:
  void SetUp() {
    memset(data, 0, kMemorySize);
  }

  // 40 Bytes memory can hold a StaticMap with up to 3 nodes.
  static const int kMemorySize = 40;
  char data[kMemorySize];
  TestMap test_map;
};

TEST_F(TestInvalidMap, TestNegativeNumberNodes) {
  memset(data, 0xff, sizeof(uint32_t));  // Set the number of nodes = -1
  test_map = TestMap(data);
  ASSERT_FALSE(test_map.ValidateInMemoryStructure());
}

TEST_F(TestInvalidMap, TestWrongOffsets) {
  uint32_t* header = reinterpret_cast<uint32_t*>(data);
  const uint32_t kNumNodes = 2;
  const uint32_t kHeaderOffset =
        sizeof(uint32_t) + kNumNodes * (sizeof(uint32_t) + sizeof(KeyType));

  header[0] = kNumNodes;
  header[1] = kHeaderOffset + 3;   // Wrong offset for first node
  test_map = TestMap(data);
  ASSERT_FALSE(test_map.ValidateInMemoryStructure());

  header[1] = kHeaderOffset;       // Correct offset for first node
  header[2] = kHeaderOffset - 1;   // Wrong offset for second node
  test_map = TestMap(data);
  ASSERT_FALSE(test_map.ValidateInMemoryStructure());
}

TEST_F(TestInvalidMap, TestUnSortedKeys) {
  uint32_t* header = reinterpret_cast<uint32_t*>(data);
  const uint32_t kNumNodes = 2;
  const uint32_t kHeaderOffset =
      sizeof(uint32_t) + kNumNodes * (sizeof(uint32_t) + sizeof(KeyType));
  header[0] = kNumNodes;
  header[1] = kHeaderOffset;
  header[2] = kHeaderOffset + sizeof(ValueType);

  KeyType* keys = reinterpret_cast<KeyType*>(
      data + (kNumNodes + 1) * sizeof(uint32_t));
  // Set keys in non-increasing order.
  keys[0] = 10;
  keys[1] = 7;
  test_map = TestMap(data);
  ASSERT_FALSE(test_map.ValidateInMemoryStructure());
}


class TestValidMap : public ::testing::Test {
 protected:
  void SetUp() {
    int testcase = 0;

    // Empty map.
    map_data[testcase] =
        serializer.Serialize(std_map[testcase], &size[testcase]);
    test_map[testcase] = TestMap(map_data[testcase]);
    ++testcase;

    // Single element.
    std_map[testcase].insert(std::make_pair(2, 8));
    map_data[testcase] =
        serializer.Serialize(std_map[testcase], &size[testcase]);
    test_map[testcase] = TestMap(map_data[testcase]);
    ++testcase;

    // 100 elements.
    for (int i = 0; i < 100; ++i)
          std_map[testcase].insert(std::make_pair(i, 2 * i));
    map_data[testcase] =
        serializer.Serialize(std_map[testcase], &size[testcase]);
    test_map[testcase] = TestMap(map_data[testcase]);
    ++testcase;

    // 1000 random elements.
    for (int i = 0; i < 1000; ++i)
      std_map[testcase].insert(std::make_pair(rand(), rand()));
    map_data[testcase] =
        serializer.Serialize(std_map[testcase], &size[testcase]);
    test_map[testcase] = TestMap(map_data[testcase]);

    // Set correct size of memory allocation for each test case.
    unsigned int size_per_node =
        sizeof(uint32_t) + sizeof(KeyType) + sizeof(ValueType);
    for (testcase = 0; testcase < kNumberTestCases; ++testcase) {
      correct_size[testcase] =
          sizeof(uint32_t) + std_map[testcase].size() * size_per_node;
    }
  }

  void TearDown() {
    for (int i = 0;i < kNumberTestCases; ++i)
      delete map_data[i];
  }


  void IteratorTester(int test_case) {
    // scan through:
    iter_test = test_map[test_case].begin();
    iter_std = std_map[test_case].begin();

    for (; iter_test != test_map[test_case].end() &&
           iter_std != std_map[test_case].end();
         ++iter_test, ++iter_std) {
      ASSERT_EQ(iter_test.GetKey(), iter_std->first);
      ASSERT_EQ(*(iter_test.GetValuePtr()), iter_std->second);
    }
    ASSERT_TRUE(iter_test == test_map[test_case].end()
             && iter_std == std_map[test_case].end());

    // Boundary testcase.
    if (!std_map[test_case].empty()) {
      // rear boundary case:
      iter_test = test_map[test_case].end();
      iter_std = std_map[test_case].end();
      --iter_std;
      --iter_test;
      ASSERT_EQ(iter_test.GetKey(), iter_std->first);
      ASSERT_EQ(*(iter_test.GetValuePtr()), iter_std->second);

      ++iter_test;
      ++iter_std;
      ASSERT_TRUE(iter_test == test_map[test_case].end());

      --iter_test;
      --iter_std;
      ASSERT_TRUE(iter_test != test_map[test_case].end());
      ASSERT_TRUE(iter_test == test_map[test_case].last());
      ASSERT_EQ(iter_test.GetKey(), iter_std->first);
      ASSERT_EQ(*(iter_test.GetValuePtr()), iter_std->second);

      // front boundary case:
      iter_test = test_map[test_case].begin();
      --iter_test;
      ASSERT_TRUE(iter_test == test_map[test_case].begin());
    }
  }

  void CompareLookupResult(int test_case) {
    bool found1 = (iter_test != test_map[test_case].end());
    bool found2 = (iter_std != std_map[test_case].end());
    ASSERT_EQ(found1, found2);

    if (found1 && found2) {
      ASSERT_EQ(iter_test.GetKey(), iter_std->first);
      ASSERT_EQ(*(iter_test.GetValuePtr()), iter_std->second);
    }
  }

  void FindTester(int test_case, const KeyType &key) {
    iter_test = test_map[test_case].find(key);
    iter_std = std_map[test_case].find(key);
    CompareLookupResult(test_case);
  }

  void LowerBoundTester(int test_case, const KeyType &key) {
    iter_test = test_map[test_case].lower_bound(key);
    iter_std = std_map[test_case].lower_bound(key);
    CompareLookupResult(test_case);
  }

  void UpperBoundTester(int test_case, const KeyType &key) {
    iter_test = test_map[test_case].upper_bound(key);
    iter_std = std_map[test_case].upper_bound(key);
    CompareLookupResult(test_case);
  }

  void LookupTester(int test_case) {
    StdMap::const_iterator iter;
    // Test find():
    for (iter = std_map[test_case].begin();
        iter != std_map[test_case].end();
        ++iter) {
      FindTester(test_case, iter->first);
      FindTester(test_case, iter->first + 1);
      FindTester(test_case, iter->first - 1);
    }
    FindTester(test_case, INT_MIN);
    FindTester(test_case, INT_MAX);
    // random test:
    for (int i = 0; i < rand()%5000 + 5000; ++i)
      FindTester(test_case, rand());

    // Test lower_bound():
    for (iter = std_map[test_case].begin();
        iter != std_map[test_case].end();
        ++iter) {
      LowerBoundTester(test_case, iter->first);
      LowerBoundTester(test_case, iter->first + 1);
      LowerBoundTester(test_case, iter->first - 1);
    }
    LowerBoundTester(test_case, INT_MIN);
    LowerBoundTester(test_case, INT_MAX);
    // random test:
    for (int i = 0; i < rand()%5000 + 5000; ++i)
      LowerBoundTester(test_case, rand());

    // Test upper_bound():
    for (iter = std_map[test_case].begin();
        iter != std_map[test_case].end();
        ++iter) {
      UpperBoundTester(test_case, iter->first);
      UpperBoundTester(test_case, iter->first + 1);
      UpperBoundTester(test_case, iter->first - 1);
    }
    UpperBoundTester(test_case, INT_MIN);
    UpperBoundTester(test_case, INT_MAX);
    // random test:
    for (int i = 0; i < rand()%5000 + 5000; ++i)
      UpperBoundTester(test_case, rand());
  }

  static const int kNumberTestCases = 4;
  StdMap std_map[kNumberTestCases];
  TestMap test_map[kNumberTestCases];
  TestMap::const_iterator iter_test;
  StdMap::const_iterator iter_std;
  char* map_data[kNumberTestCases];
  unsigned int size[kNumberTestCases];
  unsigned int correct_size[kNumberTestCases];
  SimpleMapSerializer<KeyType, ValueType> serializer;
};

TEST_F(TestValidMap, TestEmptyMap) {
  int test_case = 0;
  // Assert memory size allocated during serialization is correct.
  ASSERT_EQ(correct_size[test_case], size[test_case]);

  // Sanity check of serialized data:
  ASSERT_TRUE(test_map[test_case].ValidateInMemoryStructure());
  ASSERT_EQ(std_map[test_case].empty(), test_map[test_case].empty());
  ASSERT_EQ(std_map[test_case].size(), test_map[test_case].size());

  // Test Iterator.
  IteratorTester(test_case);

  // Test lookup operations.
  LookupTester(test_case);
}

TEST_F(TestValidMap, TestSingleElement) {
  int test_case = 1;
  // Assert memory size allocated during serialization is correct.
  ASSERT_EQ(correct_size[test_case], size[test_case]);

  // Sanity check of serialized data:
  ASSERT_TRUE(test_map[test_case].ValidateInMemoryStructure());
  ASSERT_EQ(std_map[test_case].empty(), test_map[test_case].empty());
  ASSERT_EQ(std_map[test_case].size(), test_map[test_case].size());

  // Test Iterator.
  IteratorTester(test_case);

  // Test lookup operations.
  LookupTester(test_case);
}

TEST_F(TestValidMap, Test100Elements) {
  int test_case = 2;
  // Assert memory size allocated during serialization is correct.
  ASSERT_EQ(correct_size[test_case], size[test_case]);

  // Sanity check of serialized data:
  ASSERT_TRUE(test_map[test_case].ValidateInMemoryStructure());
  ASSERT_EQ(std_map[test_case].empty(), test_map[test_case].empty());
  ASSERT_EQ(std_map[test_case].size(), test_map[test_case].size());

  // Test Iterator.
  IteratorTester(test_case);

  // Test lookup operations.
  LookupTester(test_case);
}

TEST_F(TestValidMap, Test1000RandomElements) {
  int test_case = 3;
  // Assert memory size allocated during serialization is correct.
  ASSERT_EQ(correct_size[test_case], size[test_case]);

  // Sanity check of serialized data:
  ASSERT_TRUE(test_map[test_case].ValidateInMemoryStructure());
  ASSERT_EQ(std_map[test_case].empty(), test_map[test_case].empty());
  ASSERT_EQ(std_map[test_case].size(), test_map[test_case].size());

  // Test Iterator.
  IteratorTester(test_case);

  // Test lookup operations.
  LookupTester(test_case);
}

int main(int argc, char *argv[]) {
  ::testing::InitGoogleTest(&argc, argv);

  return RUN_ALL_TESTS();
}

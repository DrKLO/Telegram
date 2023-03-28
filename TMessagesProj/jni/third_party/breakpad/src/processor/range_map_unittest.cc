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

// range_map_unittest.cc: Unit tests for RangeMap
//
// Author: Mark Mentovai


#include <limits.h>
#include <stdio.h>

#include "processor/range_map-inl.h"

#include "common/scoped_ptr.h"
#include "processor/linked_ptr.h"
#include "processor/logging.h"

namespace {


using google_breakpad::linked_ptr;
using google_breakpad::scoped_ptr;
using google_breakpad::RangeMap;


// A CountedObject holds an int.  A global (not thread safe!) count of
// allocated CountedObjects is maintained to help test memory management.
class CountedObject {
 public:
  explicit CountedObject(int id) : id_(id) { ++count_; }
  ~CountedObject() { --count_; }

  static int count() { return count_; }
  int id() const { return id_; }

 private:
  static int count_;
  int id_;
};

int CountedObject::count_;


typedef int AddressType;
typedef RangeMap< AddressType, linked_ptr<CountedObject> > TestMap;


// RangeTest contains data to use for store and retrieve tests.  See
// RunTests for descriptions of the tests.
struct RangeTest {
  // Base address to use for test
  AddressType address;

  // Size of range to use for test
  AddressType size;

  // Unique ID of range - unstorable ranges must have unique IDs too
  int id;

  // Whether this range is expected to be stored successfully or not
  bool expect_storable;
};


// A RangeTestSet encompasses multiple RangeTests, which are run in
// sequence on the same RangeMap.
struct RangeTestSet {
  // An array of RangeTests
  const RangeTest *range_tests;

  // The number of tests in the set
  unsigned int range_test_count;
};


// StoreTest uses the data in a RangeTest and calls StoreRange on the
// test RangeMap.  It returns true if the expected result occurred, and
// false if something else happened.
static bool StoreTest(TestMap *range_map, const RangeTest *range_test) {
  linked_ptr<CountedObject> object(new CountedObject(range_test->id));
  bool stored = range_map->StoreRange(range_test->address,
                                      range_test->size,
                                      object);

  if (stored != range_test->expect_storable) {
    fprintf(stderr, "FAILED: "
            "StoreRange id %d, expected %s, observed %s\n",
            range_test->id,
            range_test->expect_storable ? "storable" : "not storable",
            stored ? "stored" : "not stored");
    return false;
  }

  return true;
}


// RetrieveTest uses the data in RangeTest and calls RetrieveRange on the
// test RangeMap.  If it retrieves the expected value (which can be no
// map entry at the specified range,) it returns true, otherwise, it returns
// false.  RetrieveTest will check the values around the base address and
// the high address of a range to guard against off-by-one errors.
static bool RetrieveTest(TestMap *range_map, const RangeTest *range_test) {
  for (unsigned int side = 0; side <= 1; ++side) {
    // When side == 0, check the low side (base address) of each range.
    // When side == 1, check the high side (base + size) of each range.

    // Check one-less and one-greater than the target address in addition
    // to the target address itself.

    // If the size of the range is only 1, don't check one greater than
    // the base or one less than the high - for a successfully stored
    // range, these tests would erroneously fail because the range is too
    // small.
    AddressType low_offset = -1;
    AddressType high_offset = 1;
    if (range_test->size == 1) {
      if (!side)          // When checking the low side,
        high_offset = 0;  // don't check one over the target.
      else                // When checking the high side,
        low_offset = 0;   // don't check one under the target.
    }

    for (AddressType offset = low_offset; offset <= high_offset; ++offset) {
      AddressType address =
          offset +
          (!side ? range_test->address :
                   range_test->address + range_test->size - 1);

      bool expected_result = false;  // This is correct for tests not stored.
      if (range_test->expect_storable) {
        if (offset == 0)             // When checking the target address,
          expected_result = true;    // test should always succeed.
        else if (offset == -1)       // When checking one below the target,
          expected_result = side;    // should fail low and succeed high.
        else                         // When checking one above the target,
          expected_result = !side;   // should succeed low and fail high.
      }

      linked_ptr<CountedObject> object;
      AddressType retrieved_base = AddressType();
      AddressType retrieved_size = AddressType();
      bool retrieved = range_map->RetrieveRange(address, &object,
                                                &retrieved_base,
                                                &retrieved_size);

      bool observed_result = retrieved && object->id() == range_test->id;

      if (observed_result != expected_result) {
        fprintf(stderr, "FAILED: "
                        "RetrieveRange id %d, side %d, offset %d, "
                        "expected %s, observed %s\n",
                        range_test->id,
                        side,
                        offset,
                        expected_result ? "true" : "false",
                        observed_result ? "true" : "false");
        return false;
      }

      // If a range was successfully retrieved, check that the returned
      // bounds match the range as stored.
      if (observed_result == true &&
          (retrieved_base != range_test->address ||
           retrieved_size != range_test->size)) {
        fprintf(stderr, "FAILED: "
                        "RetrieveRange id %d, side %d, offset %d, "
                        "expected base/size %d/%d, observed %d/%d\n",
                        range_test->id,
                        side,
                        offset,
                        range_test->address, range_test->size,
                        retrieved_base, retrieved_size);
        return false;
      }

      // Now, check RetrieveNearestRange.  The nearest range is always
      // expected to be different from the test range when checking one
      // less than the low side.
      bool expected_nearest = range_test->expect_storable;
      if (!side && offset < 0)
        expected_nearest = false;

      linked_ptr<CountedObject> nearest_object;
      AddressType nearest_base = AddressType();
      AddressType nearest_size = AddressType();
      bool retrieved_nearest = range_map->RetrieveNearestRange(address,
                                                               &nearest_object,
                                                               &nearest_base,
                                                               &nearest_size);

      // When checking one greater than the high side, RetrieveNearestRange
      // should usually return the test range.  When a different range begins
      // at that address, though, then RetrieveNearestRange should return the
      // range at the address instead of the test range.
      if (side && offset > 0 && nearest_base == address) {
        expected_nearest = false;
      }

      bool observed_nearest = retrieved_nearest &&
                              nearest_object->id() == range_test->id;

      if (observed_nearest != expected_nearest) {
        fprintf(stderr, "FAILED: "
                        "RetrieveNearestRange id %d, side %d, offset %d, "
                        "expected %s, observed %s\n",
                        range_test->id,
                        side,
                        offset,
                        expected_nearest ? "true" : "false",
                        observed_nearest ? "true" : "false");
        return false;
      }

      // If a range was successfully retrieved, check that the returned
      // bounds match the range as stored.
      if (expected_nearest &&
          (nearest_base != range_test->address ||
           nearest_size != range_test->size)) {
        fprintf(stderr, "FAILED: "
                        "RetrieveNearestRange id %d, side %d, offset %d, "
                        "expected base/size %d/%d, observed %d/%d\n",
                        range_test->id,
                        side,
                        offset,
                        range_test->address, range_test->size,
                        nearest_base, nearest_size);
        return false;
      }
    }
  }

  return true;
}


// Test RetrieveRangeAtIndex, which is supposed to return objects in order
// according to their addresses.  This test is performed by looping through
// the map, calling RetrieveRangeAtIndex for all possible indices in sequence,
// and verifying that each call returns a different object than the previous
// call, and that ranges are returned with increasing base addresses.  Returns
// false if the test fails.
static bool RetrieveIndexTest(TestMap *range_map, int set) {
  linked_ptr<CountedObject> object;
  CountedObject *last_object = NULL;
  AddressType last_base = 0;

  int object_count = range_map->GetCount();
  for (int object_index = 0; object_index < object_count; ++object_index) {
    AddressType base;
    if (!range_map->RetrieveRangeAtIndex(object_index, &object, &base, NULL)) {
      fprintf(stderr, "FAILED: RetrieveRangeAtIndex set %d index %d, "
              "expected success, observed failure\n",
              set, object_index);
      return false;
    }

    if (!object.get()) {
      fprintf(stderr, "FAILED: RetrieveRangeAtIndex set %d index %d, "
              "expected object, observed NULL\n",
              set, object_index);
      return false;
    }

    // It's impossible to do these comparisons unless there's a previous
    // object to compare against.
    if (last_object) {
      // The object must be different from the last one.
      if (object->id() == last_object->id()) {
        fprintf(stderr, "FAILED: RetrieveRangeAtIndex set %d index %d, "
                "expected different objects, observed same objects (%d)\n",
                set, object_index, object->id());
        return false;
      }

      // Each object must have a base greater than the previous object's base.
      if (base <= last_base) {
        fprintf(stderr, "FAILED: RetrieveRangeAtIndex set %d index %d, "
                "expected different bases, observed same bases (%d)\n",
                set, object_index, base);
        return false;
      }
    }

    last_object = object.get();
    last_base = base;
  }

  // Make sure that RetrieveRangeAtIndex doesn't allow lookups at indices that
  // are too high.
  if (range_map->RetrieveRangeAtIndex(object_count, &object, NULL, NULL)) {
    fprintf(stderr, "FAILED: RetrieveRangeAtIndex set %d index %d (too large), "
            "expected failure, observed success\n",
            set, object_count);
    return false;
  }

  return true;
}

// Additional RetriveAtIndex test to expose the bug in RetrieveRangeAtIndex().
// Bug info: RetrieveRangeAtIndex() previously retrieves the high address of
// entry, however, it is supposed to retrieve the base address of entry as
// stated in the comment in range_map.h.
static bool RetriveAtIndexTest2() {
  scoped_ptr<TestMap> range_map(new TestMap());

  // Store ranges with base address = 2 * object_id:
  const int range_size = 2;
  for (int object_id = 0; object_id < 100; ++object_id) {
    linked_ptr<CountedObject> object(new CountedObject(object_id));
    int base_address = 2 * object_id;
    range_map->StoreRange(base_address, range_size, object);
  }

  linked_ptr<CountedObject> object;
  int object_count = range_map->GetCount();
  for (int object_index = 0; object_index < object_count; ++object_index) {
    AddressType base;
    if (!range_map->RetrieveRangeAtIndex(object_index, &object, &base, NULL)) {
      fprintf(stderr, "FAILED: RetrieveAtIndexTest2 index %d, "
              "expected success, observed failure\n", object_index);
      return false;
    }

    int expected_base = 2 * object->id();
    if (base != expected_base) {
      fprintf(stderr, "FAILED: RetriveAtIndexTest2 index %d, "
              "expected base %d, observed base %d",
              object_index, expected_base, base);
      return false;
    }
  }

  return true;
}


// RunTests runs a series of test sets.
static bool RunTests() {
  // These tests will be run sequentially.  The first set of tests exercises
  // most functions of RangeTest, and verifies all of the bounds-checking.
  const RangeTest range_tests_0[] = {
    { INT_MIN,     16,      1,  true },   // lowest possible range
    { -2,          5,       2,  true },   // a range through zero
    { INT_MAX - 9, 11,      3,  false },  // tests anti-overflow
    { INT_MAX - 9, 10,      4,  true },   // highest possible range
    { 5,           0,       5,  false },  // tests anti-zero-size
    { 5,           1,       6,  true },   // smallest possible range
    { -20,         15,      7,  true },   // entirely negative

    { 10,          10,      10, true },   // causes the following tests to fail
    { 9,           10,      11, false },  // one-less base, one-less high
    { 9,           11,      12, false },  // one-less base, identical high
    { 9,           12,      13, false },  // completely contains existing
    { 10,          9,       14, false },  // identical base, one-less high
    { 10,          10,      15, false },  // exactly identical to existing range
    { 10,          11,      16, false },  // identical base, one-greater high
    { 11,          8,       17, false },  // contained completely within
    { 11,          9,       18, false },  // one-greater base, identical high
    { 11,          10,      19, false },  // one-greater base, one-greater high
    { 9,           2,       20, false },  // overlaps bottom by one
    { 10,          1,       21, false },  // overlaps bottom by one, contained
    { 19,          1,       22, false },  // overlaps top by one, contained
    { 19,          2,       23, false },  // overlaps top by one

    { 9,           1,       24, true },   // directly below without overlap
    { 20,          1,       25, true },   // directly above without overlap

    { 6,           3,       26, true },   // exactly between two ranges, gapless
    { 7,           3,       27, false },  // tries to span two ranges
    { 7,           5,       28, false },  // tries to span three ranges
    { 4,           20,      29, false },  // tries to contain several ranges

    { 30,          50,      30, true },
    { 90,          25,      31, true },
    { 35,          65,      32, false },  // tries to span two noncontiguous
    { 120,         10000,   33, true },   // > 8-bit
    { 20000,       20000,   34, true },   // > 8-bit
    { 0x10001,     0x10001, 35, true },   // > 16-bit

    { 27,          -1,      36, false }   // tests high < base
  };

  // Attempt to fill the entire space.  The entire space must be filled with
  // three stores because AddressType is signed for these tests, so RangeMap
  // treats the size as signed and rejects sizes that appear to be negative.
  // Even if these tests were run as unsigned, two stores would be needed
  // to fill the space because the entire size of the space could only be
  // described by using one more bit than would be present in AddressType.
  const RangeTest range_tests_1[] = {
    { INT_MIN, INT_MAX, 50, true },   // From INT_MIN to -2, inclusive
    { -1,      2,       51, true },   // From -1 to 0, inclusive
    { 1,       INT_MAX, 52, true },   // From 1 to INT_MAX, inclusive
    { INT_MIN, INT_MAX, 53, false },  // Can't fill the space twice
    { -1,      2,       54, false },
    { 1,       INT_MAX, 55, false },
    { -3,      6,       56, false },  // -3 to 2, inclusive - spans 3 ranges
  };

  // A light round of testing to verify that RetrieveRange does the right
  // the right thing at the extremities of the range when nothing is stored
  // there.  Checks are forced without storing anything at the extremities
  // by setting size = 0.
  const RangeTest range_tests_2[] = {
    { INT_MIN, 0, 100, false },  // makes RetrieveRange check low end
    { -1,      3, 101, true },
    { INT_MAX, 0, 102, false },  // makes RetrieveRange check high end
  };

  // Similar to the previous test set, but with a couple of ranges closer
  // to the extremities.
  const RangeTest range_tests_3[] = {
    { INT_MIN + 1, 1, 110, true },
    { INT_MAX - 1, 1, 111, true },
    { INT_MIN,     0, 112, false },  // makes RetrieveRange check low end
    { INT_MAX,     0, 113, false }   // makes RetrieveRange check high end
  };

  // The range map is cleared between sets of tests listed here.
  const RangeTestSet range_test_sets[] = {
    { range_tests_0, sizeof(range_tests_0) / sizeof(RangeTest) },
    { range_tests_1, sizeof(range_tests_1) / sizeof(RangeTest) },
    { range_tests_2, sizeof(range_tests_2) / sizeof(RangeTest) },
    { range_tests_3, sizeof(range_tests_3) / sizeof(RangeTest) },
    { range_tests_0, sizeof(range_tests_0) / sizeof(RangeTest) }   // Run again
  };

  // Maintain the range map in a pointer so that deletion can be meaningfully
  // tested.
  scoped_ptr<TestMap> range_map(new TestMap());

  // Run all of the test sets in sequence.
  unsigned int range_test_set_count = sizeof(range_test_sets) /
                                      sizeof(RangeTestSet);
  for (unsigned int range_test_set_index = 0;
       range_test_set_index < range_test_set_count;
       ++range_test_set_index) {
    const RangeTest *range_tests =
        range_test_sets[range_test_set_index].range_tests;
    unsigned int range_test_count =
        range_test_sets[range_test_set_index].range_test_count;

    // Run the StoreRange test, which validates StoreRange and initializes
    // the RangeMap with data for the RetrieveRange test.
    int stored_count = 0;  // The number of ranges successfully stored
    for (unsigned int range_test_index = 0;
         range_test_index < range_test_count;
         ++range_test_index) {
      const RangeTest *range_test = &range_tests[range_test_index];
      if (!StoreTest(range_map.get(), range_test))
        return false;

      if (range_test->expect_storable)
        ++stored_count;
    }

    // There should be exactly one CountedObject for everything successfully
    // stored in the RangeMap.
    if (CountedObject::count() != stored_count) {
      fprintf(stderr, "FAILED: "
              "stored object counts don't match, expected %d, observed %d\n",
              stored_count,
              CountedObject::count());

      return false;
    }

    // The RangeMap's own count of objects should also match.
    if (range_map->GetCount() != stored_count) {
      fprintf(stderr, "FAILED: stored object count doesn't match GetCount, "
              "expected %d, observed %d\n",
              stored_count, range_map->GetCount());

      return false;
    }

    // Run the RetrieveRange test
    for (unsigned int range_test_index = 0;
         range_test_index < range_test_count;
         ++range_test_index) {
      const RangeTest *range_test = &range_tests[range_test_index];
      if (!RetrieveTest(range_map.get(), range_test))
        return false;
    }

    if (!RetrieveIndexTest(range_map.get(), range_test_set_index))
      return false;

    // Clear the map between test sets.  If this is the final test set,
    // delete the map instead to test destruction.
    if (range_test_set_index < range_test_set_count - 1)
      range_map->Clear();
    else
      range_map.reset();

    // Test that all stored objects are freed when the RangeMap is cleared
    // or deleted.
    if (CountedObject::count() != 0) {
      fprintf(stderr, "FAILED: "
              "did not free all objects after %s, %d still allocated\n",
              range_test_set_index < range_test_set_count - 1 ? "clear"
                                                              : "delete",
              CountedObject::count());

      return false;
    }
  }

  if (!RetriveAtIndexTest2()) {
    fprintf(stderr, "FAILED: did not pass RetrieveAtIndexTest2()\n");
    return false;
  }

  return true;
}


}  // namespace


int main(int argc, char **argv) {
  BPLOG_INIT(&argc, &argv);

  return RunTests() ? 0 : 1;
}

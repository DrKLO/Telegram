/* Copyright (c) 2018, Google Inc.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

#include <openssl/stack.h>

#include <limits.h>

#include <algorithm>
#include <memory>
#include <utility>
#include <vector>

#include <gtest/gtest.h>

#include <openssl/mem.h>


// Define a custom stack type for testing.
using TEST_INT = int;

static void TEST_INT_free(TEST_INT *x) { OPENSSL_free(x); }

BSSL_NAMESPACE_BEGIN
BORINGSSL_MAKE_DELETER(TEST_INT, TEST_INT_free)
BSSL_NAMESPACE_END

static bssl::UniquePtr<TEST_INT> TEST_INT_new(int x) {
  bssl::UniquePtr<TEST_INT> ret(
      static_cast<TEST_INT *>(OPENSSL_malloc(sizeof(TEST_INT))));
  if (!ret) {
    return nullptr;
  }
  *ret = x;
  return ret;
}

DEFINE_STACK_OF(TEST_INT)

struct ShallowStackDeleter {
  void operator()(STACK_OF(TEST_INT) *sk) const { sk_TEST_INT_free(sk); }
};

using ShallowStack = std::unique_ptr<STACK_OF(TEST_INT), ShallowStackDeleter>;

// kNull is treated as a nullptr expectation for purposes of ExpectStackEquals.
// The tests in this file will never use it as a test value.
static const int kNull = INT_MIN;

static void ExpectStackEquals(const STACK_OF(TEST_INT) *sk,
                              const std::vector<int> &vec) {
  EXPECT_EQ(vec.size(), sk_TEST_INT_num(sk));
  for (size_t i = 0; i < vec.size(); i++) {
    SCOPED_TRACE(i);
    const TEST_INT *obj = sk_TEST_INT_value(sk, i);
    if (vec[i] == kNull) {
      EXPECT_FALSE(obj);
    } else {
      EXPECT_TRUE(obj);
      if (obj) {
        EXPECT_EQ(vec[i], *obj);
      }
    }
  }

  // Reading out-of-bounds fails.
  EXPECT_FALSE(sk_TEST_INT_value(sk, vec.size()));
  EXPECT_FALSE(sk_TEST_INT_value(sk, vec.size() + 1));
}

TEST(StackTest, Basic) {
  bssl::UniquePtr<STACK_OF(TEST_INT)> sk(sk_TEST_INT_new_null());
  ASSERT_TRUE(sk);

  // The stack starts out empty.
  ExpectStackEquals(sk.get(), {});

  // Removing elements from an empty stack does nothing.
  EXPECT_FALSE(sk_TEST_INT_pop(sk.get()));
  EXPECT_FALSE(sk_TEST_INT_shift(sk.get()));
  EXPECT_FALSE(sk_TEST_INT_delete(sk.get(), 0));

  // Push some elements.
  for (int i = 0; i < 6; i++) {
    auto value = TEST_INT_new(i);
    ASSERT_TRUE(value);
    ASSERT_TRUE(bssl::PushToStack(sk.get(), std::move(value)));
  }

  ExpectStackEquals(sk.get(), {0, 1, 2, 3, 4, 5});

  // Items may be inserted in the middle.
  auto value = TEST_INT_new(6);
  ASSERT_TRUE(value);
  // Hold on to the object for later.
  TEST_INT *raw = value.get();
  ASSERT_TRUE(sk_TEST_INT_insert(sk.get(), value.get(), 4));
  value.release();  // sk_TEST_INT_insert takes ownership on success.

  ExpectStackEquals(sk.get(), {0, 1, 2, 3, 6, 4, 5});

  // Without a comparison function, find searches by pointer.
  value = TEST_INT_new(6);
  ASSERT_TRUE(value);
  size_t index;
  EXPECT_FALSE(sk_TEST_INT_find(sk.get(), &index, value.get()));
  ASSERT_TRUE(sk_TEST_INT_find(sk.get(), &index, raw));
  EXPECT_EQ(4u, index);

  // sk_TEST_INT_insert can also insert values at the end.
  value = TEST_INT_new(7);
  ASSERT_TRUE(value);
  ASSERT_TRUE(sk_TEST_INT_insert(sk.get(), value.get(), 7));
  value.release();  // sk_TEST_INT_insert takes ownership on success.

  ExpectStackEquals(sk.get(), {0, 1, 2, 3, 6, 4, 5, 7});

  // Out-of-bounds indices are clamped.
  value = TEST_INT_new(8);
  ASSERT_TRUE(value);
  ASSERT_TRUE(sk_TEST_INT_insert(sk.get(), value.get(), 999));
  value.release();  // sk_TEST_INT_insert takes ownership on success.

  ExpectStackEquals(sk.get(), {0, 1, 2, 3, 6, 4, 5, 7, 8});

  // Test removing elements from various places.
  bssl::UniquePtr<TEST_INT> removed(sk_TEST_INT_pop(sk.get()));
  EXPECT_EQ(8, *removed);
  ExpectStackEquals(sk.get(), {0, 1, 2, 3, 6, 4, 5, 7});

  removed.reset(sk_TEST_INT_shift(sk.get()));
  EXPECT_EQ(0, *removed);
  ExpectStackEquals(sk.get(), {1, 2, 3, 6, 4, 5, 7});

  removed.reset(sk_TEST_INT_delete(sk.get(), 2));
  EXPECT_EQ(3, *removed);
  ExpectStackEquals(sk.get(), {1, 2, 6, 4, 5, 7});

  // Objects may also be deleted by pointer.
  removed.reset(sk_TEST_INT_delete_ptr(sk.get(), raw));
  EXPECT_EQ(raw, removed.get());
  ExpectStackEquals(sk.get(), {1, 2, 4, 5, 7});

  // Deleting is a no-op is the object is not found.
  value = TEST_INT_new(100);
  ASSERT_TRUE(value);
  EXPECT_FALSE(sk_TEST_INT_delete_ptr(sk.get(), value.get()));

  // Insert nullptr to test deep copy handling of it.
  ASSERT_TRUE(sk_TEST_INT_insert(sk.get(), nullptr, 0));
  ExpectStackEquals(sk.get(), {kNull, 1, 2, 4, 5, 7});

  // Test both deep and shallow copies.
  bssl::UniquePtr<STACK_OF(TEST_INT)> copy(sk_TEST_INT_deep_copy(
      sk.get(),
      [](TEST_INT *x) -> TEST_INT * {
        return x == nullptr ? nullptr : TEST_INT_new(*x).release();
      },
      TEST_INT_free));
  ASSERT_TRUE(copy);
  ExpectStackEquals(copy.get(), {kNull, 1, 2, 4, 5, 7});

  ShallowStack shallow(sk_TEST_INT_dup(sk.get()));
  ASSERT_TRUE(shallow);
  ASSERT_EQ(sk_TEST_INT_num(sk.get()), sk_TEST_INT_num(shallow.get()));
  for (size_t i = 0; i < sk_TEST_INT_num(sk.get()); i++) {
    EXPECT_EQ(sk_TEST_INT_value(sk.get(), i),
              sk_TEST_INT_value(shallow.get(), i));
  }

  // Deep copies may fail. This should clean up temporaries.
  EXPECT_FALSE(sk_TEST_INT_deep_copy(sk.get(),
                                     [](TEST_INT *x) -> TEST_INT * {
                                       return x == nullptr || *x == 4
                                                  ? nullptr
                                                  : TEST_INT_new(*x).release();
                                     },
                                     TEST_INT_free));

  // sk_TEST_INT_zero clears a stack, but does not free the elements.
  ShallowStack shallow2(sk_TEST_INT_dup(sk.get()));
  ASSERT_TRUE(shallow2);
  sk_TEST_INT_zero(shallow2.get());
  ExpectStackEquals(shallow2.get(), {});
}

TEST(StackTest, BigStack) {
  bssl::UniquePtr<STACK_OF(TEST_INT)> sk(sk_TEST_INT_new_null());
  ASSERT_TRUE(sk);

  std::vector<int> expected;
  static const int kCount = 100000;
  for (int i = 0; i < kCount; i++) {
    auto value = TEST_INT_new(i);
    ASSERT_TRUE(value);
    ASSERT_TRUE(bssl::PushToStack(sk.get(), std::move(value)));
    expected.push_back(i);
  }
  ExpectStackEquals(sk.get(), expected);
}

static uint64_t g_compare_count = 0;

static int compare(const TEST_INT **a, const TEST_INT **b) {
  g_compare_count++;
  if (**a < **b) {
    return -1;
  }
  if (**a > **b) {
    return 1;
  }
  return 0;
}

static int compare_reverse(const TEST_INT **a, const TEST_INT **b) {
  return -compare(a, b);
}

TEST(StackTest, Sorted) {
  std::vector<int> vec_sorted = {0, 1, 2, 3, 4, 5, 6};
  std::vector<int> vec = vec_sorted;
  do {
    bssl::UniquePtr<STACK_OF(TEST_INT)> sk(sk_TEST_INT_new(compare));
    ASSERT_TRUE(sk);
    for (int v : vec) {
      auto value = TEST_INT_new(v);
      ASSERT_TRUE(value);
      ASSERT_TRUE(bssl::PushToStack(sk.get(), std::move(value)));
    }

    // The stack is not (known to be) sorted.
    EXPECT_FALSE(sk_TEST_INT_is_sorted(sk.get()));

    // With a comparison function, find matches by value.
    auto ten = TEST_INT_new(10);
    ASSERT_TRUE(ten);
    size_t index;
    EXPECT_FALSE(sk_TEST_INT_find(sk.get(), &index, ten.get()));

    auto three = TEST_INT_new(3);
    ASSERT_TRUE(three);
    ASSERT_TRUE(sk_TEST_INT_find(sk.get(), &index, three.get()));
    EXPECT_EQ(3, *sk_TEST_INT_value(sk.get(), index));

    sk_TEST_INT_sort(sk.get());
    EXPECT_TRUE(sk_TEST_INT_is_sorted(sk.get()));
    ExpectStackEquals(sk.get(), vec_sorted);

    // Sorting an already-sorted list is a no-op.
    uint64_t old_compare_count = g_compare_count;
    sk_TEST_INT_sort(sk.get());
    EXPECT_EQ(old_compare_count, g_compare_count);
    EXPECT_TRUE(sk_TEST_INT_is_sorted(sk.get()));
    ExpectStackEquals(sk.get(), vec_sorted);

    // When sorted, find uses binary search.
    ASSERT_TRUE(ten);
    EXPECT_FALSE(sk_TEST_INT_find(sk.get(), &index, ten.get()));

    ASSERT_TRUE(three);
    ASSERT_TRUE(sk_TEST_INT_find(sk.get(), &index, three.get()));
    EXPECT_EQ(3u, index);

    // Copies preserve comparison and sorted information.
    bssl::UniquePtr<STACK_OF(TEST_INT)> copy(sk_TEST_INT_deep_copy(
        sk.get(),
        [](TEST_INT *x) -> TEST_INT * { return TEST_INT_new(*x).release(); },
        TEST_INT_free));
    ASSERT_TRUE(copy);
    EXPECT_TRUE(sk_TEST_INT_is_sorted(copy.get()));
    ASSERT_TRUE(sk_TEST_INT_find(copy.get(), &index, three.get()));
    EXPECT_EQ(3u, index);

    ShallowStack copy2(sk_TEST_INT_dup(sk.get()));
    ASSERT_TRUE(copy2);
    EXPECT_TRUE(sk_TEST_INT_is_sorted(copy2.get()));
    ASSERT_TRUE(sk_TEST_INT_find(copy2.get(), &index, three.get()));
    EXPECT_EQ(3u, index);

    // Removing elements does not affect sortedness.
    TEST_INT_free(sk_TEST_INT_delete(sk.get(), 0));
    EXPECT_TRUE(sk_TEST_INT_is_sorted(sk.get()));

    // Changing the comparison function invalidates sortedness.
    sk_TEST_INT_set_cmp_func(sk.get(), compare_reverse);
    EXPECT_FALSE(sk_TEST_INT_is_sorted(sk.get()));
    ASSERT_TRUE(sk_TEST_INT_find(sk.get(), &index, three.get()));
    EXPECT_EQ(2u, index);

    sk_TEST_INT_sort(sk.get());
    ExpectStackEquals(sk.get(), {6, 5, 4, 3, 2, 1});
    ASSERT_TRUE(sk_TEST_INT_find(sk.get(), &index, three.get()));
    EXPECT_EQ(3u, index);

    // Inserting a new element invalidates sortedness.
    auto tmp = TEST_INT_new(10);
    ASSERT_TRUE(tmp);
    ASSERT_TRUE(bssl::PushToStack(sk.get(), std::move(tmp)));
    EXPECT_FALSE(sk_TEST_INT_is_sorted(sk.get()));
    ASSERT_TRUE(sk_TEST_INT_find(sk.get(), &index, ten.get()));
    EXPECT_EQ(6u, index);
  } while (std::next_permutation(vec.begin(), vec.end()));
}

// sk_*_find should return the first matching element in all cases.
TEST(StackTest, FindFirst) {
  bssl::UniquePtr<STACK_OF(TEST_INT)> sk(sk_TEST_INT_new(compare));
  auto value = TEST_INT_new(1);
  ASSERT_TRUE(value);
  ASSERT_TRUE(bssl::PushToStack(sk.get(), std::move(value)));
  for (int i = 0; i < 10; i++) {
    value = TEST_INT_new(2);
    ASSERT_TRUE(value);
    ASSERT_TRUE(bssl::PushToStack(sk.get(), std::move(value)));
  }

  const TEST_INT *two = sk_TEST_INT_value(sk.get(), 1);
  // Pointer-based equality.
  size_t index;
  ASSERT_TRUE(sk_TEST_INT_find(sk.get(), &index, two));
  EXPECT_EQ(1u, index);

  // Comparator-based equality, unsorted.
  sk_TEST_INT_set_cmp_func(sk.get(), compare);
  EXPECT_FALSE(sk_TEST_INT_is_sorted(sk.get()));
  ASSERT_TRUE(sk_TEST_INT_find(sk.get(), &index, two));
  EXPECT_EQ(1u, index);

  // Comparator-based equality, sorted.
  sk_TEST_INT_sort(sk.get());
  EXPECT_TRUE(sk_TEST_INT_is_sorted(sk.get()));
  ASSERT_TRUE(sk_TEST_INT_find(sk.get(), &index, two));
  EXPECT_EQ(1u, index);

  // Comparator-based equality, sorted and at the front.
  sk_TEST_INT_set_cmp_func(sk.get(), compare_reverse);
  sk_TEST_INT_sort(sk.get());
  EXPECT_TRUE(sk_TEST_INT_is_sorted(sk.get()));
  ASSERT_TRUE(sk_TEST_INT_find(sk.get(), &index, two));
  EXPECT_EQ(0u, index);
}

// Exhaustively test the binary search.
TEST(StackTest, BinarySearch) {
  static const size_t kCount = 100;
  for (size_t i = 0; i < kCount; i++) {
    SCOPED_TRACE(i);
    for (size_t j = i; j <= kCount; j++) {
      SCOPED_TRACE(j);
      // Make a stack where [0, i) are below, [i, j) match, and [j, kCount) are
      // above.
      bssl::UniquePtr<STACK_OF(TEST_INT)> sk(sk_TEST_INT_new(compare));
      ASSERT_TRUE(sk);
      for (size_t k = 0; k < i; k++) {
        auto value = TEST_INT_new(-1);
        ASSERT_TRUE(value);
        ASSERT_TRUE(bssl::PushToStack(sk.get(), std::move(value)));
      }
      for (size_t k = i; k < j; k++) {
        auto value = TEST_INT_new(0);
        ASSERT_TRUE(value);
        ASSERT_TRUE(bssl::PushToStack(sk.get(), std::move(value)));
      }
      for (size_t k = j; k < kCount; k++) {
        auto value = TEST_INT_new(1);
        ASSERT_TRUE(value);
        ASSERT_TRUE(bssl::PushToStack(sk.get(), std::move(value)));
      }
      sk_TEST_INT_sort(sk.get());

      auto key = TEST_INT_new(0);
      ASSERT_TRUE(key);

      size_t idx;
      int found = sk_TEST_INT_find(sk.get(), &idx, key.get());
      if (i == j) {
        EXPECT_FALSE(found);
      } else {
        ASSERT_TRUE(found);
        EXPECT_EQ(i, idx);
      }
    }
  }
}

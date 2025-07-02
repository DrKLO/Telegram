/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/audio_vector.h"

#include <stdlib.h>

#include <string>

#include "rtc_base/numerics/safe_conversions.h"
#include "test/gtest.h"

namespace webrtc {

class AudioVectorTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    // Populate test array.
    for (size_t i = 0; i < array_length(); ++i) {
      array_[i] = rtc::checked_cast<int16_t>(i);
    }
  }

  size_t array_length() const { return sizeof(array_) / sizeof(array_[0]); }

  int16_t array_[10];
};

// Create and destroy AudioVector objects, both empty and with a predefined
// length.
TEST_F(AudioVectorTest, CreateAndDestroy) {
  AudioVector vec1;
  EXPECT_TRUE(vec1.Empty());
  EXPECT_EQ(0u, vec1.Size());

  size_t initial_size = 17;
  AudioVector vec2(initial_size);
  EXPECT_FALSE(vec2.Empty());
  EXPECT_EQ(initial_size, vec2.Size());
}

// Test the subscript operator [] for getting and setting.
TEST_F(AudioVectorTest, SubscriptOperator) {
  AudioVector vec(array_length());
  for (size_t i = 0; i < array_length(); ++i) {
    vec[i] = static_cast<int16_t>(i);
    const int16_t& value = vec[i];  // Make sure to use the const version.
    EXPECT_EQ(static_cast<int16_t>(i), value);
  }
}

// Test the PushBack method and the CopyFrom method. The Clear method is also
// invoked.
TEST_F(AudioVectorTest, PushBackAndCopy) {
  AudioVector vec;
  AudioVector vec_copy;
  vec.PushBack(array_, array_length());
  vec.CopyTo(&vec_copy);  // Copy from `vec` to `vec_copy`.
  ASSERT_EQ(array_length(), vec.Size());
  ASSERT_EQ(array_length(), vec_copy.Size());
  for (size_t i = 0; i < array_length(); ++i) {
    EXPECT_EQ(array_[i], vec[i]);
    EXPECT_EQ(array_[i], vec_copy[i]);
  }

  // Clear `vec` and verify that it is empty.
  vec.Clear();
  EXPECT_TRUE(vec.Empty());

  // Now copy the empty vector and verify that the copy becomes empty too.
  vec.CopyTo(&vec_copy);
  EXPECT_TRUE(vec_copy.Empty());
}

// Test the PushBack method with another AudioVector as input argument.
TEST_F(AudioVectorTest, PushBackVector) {
  static const size_t kLength = 10;
  AudioVector vec1(kLength);
  AudioVector vec2(kLength);
  // Set the first vector to [0, 1, ..., kLength - 1].
  // Set the second vector to [kLength, kLength + 1, ..., 2 * kLength - 1].
  for (size_t i = 0; i < kLength; ++i) {
    vec1[i] = static_cast<int16_t>(i);
    vec2[i] = static_cast<int16_t>(i + kLength);
  }
  // Append vec2 to the back of vec1.
  vec1.PushBack(vec2);
  ASSERT_EQ(2 * kLength, vec1.Size());
  for (size_t i = 0; i < 2 * kLength; ++i) {
    EXPECT_EQ(static_cast<int16_t>(i), vec1[i]);
  }
}

// Test the PushFront method.
TEST_F(AudioVectorTest, PushFront) {
  AudioVector vec;
  vec.PushFront(array_, array_length());
  ASSERT_EQ(array_length(), vec.Size());
  for (size_t i = 0; i < array_length(); ++i) {
    EXPECT_EQ(array_[i], vec[i]);
  }
}

// Test the PushFront method with another AudioVector as input argument.
TEST_F(AudioVectorTest, PushFrontVector) {
  static const size_t kLength = 10;
  AudioVector vec1(kLength);
  AudioVector vec2(kLength);
  // Set the first vector to [0, 1, ..., kLength - 1].
  // Set the second vector to [kLength, kLength + 1, ..., 2 * kLength - 1].
  for (size_t i = 0; i < kLength; ++i) {
    vec1[i] = static_cast<int16_t>(i);
    vec2[i] = static_cast<int16_t>(i + kLength);
  }
  // Prepend vec1 to the front of vec2.
  vec2.PushFront(vec1);
  ASSERT_EQ(2 * kLength, vec2.Size());
  for (size_t i = 0; i < 2 * kLength; ++i) {
    EXPECT_EQ(static_cast<int16_t>(i), vec2[i]);
  }
}

// Test the PopFront method.
TEST_F(AudioVectorTest, PopFront) {
  AudioVector vec;
  vec.PushBack(array_, array_length());
  vec.PopFront(1);  // Remove one element.
  EXPECT_EQ(array_length() - 1u, vec.Size());
  for (size_t i = 0; i < array_length() - 1; ++i) {
    EXPECT_EQ(static_cast<int16_t>(i + 1), vec[i]);
  }
  vec.PopFront(array_length());  // Remove more elements than vector size.
  EXPECT_EQ(0u, vec.Size());
}

// Test the PopBack method.
TEST_F(AudioVectorTest, PopBack) {
  AudioVector vec;
  vec.PushBack(array_, array_length());
  vec.PopBack(1);  // Remove one element.
  EXPECT_EQ(array_length() - 1u, vec.Size());
  for (size_t i = 0; i < array_length() - 1; ++i) {
    EXPECT_EQ(static_cast<int16_t>(i), vec[i]);
  }
  vec.PopBack(array_length());  // Remove more elements than vector size.
  EXPECT_EQ(0u, vec.Size());
}

// Test the Extend method.
TEST_F(AudioVectorTest, Extend) {
  AudioVector vec;
  vec.PushBack(array_, array_length());
  vec.Extend(5);  // Extend with 5 elements, which should all be zeros.
  ASSERT_EQ(array_length() + 5u, vec.Size());
  // Verify that all are zero.
  for (size_t i = array_length(); i < array_length() + 5; ++i) {
    EXPECT_EQ(0, vec[i]);
  }
}

// Test the InsertAt method with an insert position in the middle of the vector.
TEST_F(AudioVectorTest, InsertAt) {
  AudioVector vec;
  vec.PushBack(array_, array_length());
  static const int kNewLength = 5;
  int16_t new_array[kNewLength];
  // Set array elements to {100, 101, 102, ... }.
  for (int i = 0; i < kNewLength; ++i) {
    new_array[i] = 100 + i;
  }
  int insert_position = 5;
  vec.InsertAt(new_array, kNewLength, insert_position);
  // Verify that the vector looks as follows:
  // {0, 1, ..., `insert_position` - 1, 100, 101, ..., 100 + kNewLength - 1,
  //  `insert_position`, `insert_position` + 1, ..., kLength - 1}.
  size_t pos = 0;
  for (int i = 0; i < insert_position; ++i) {
    EXPECT_EQ(array_[i], vec[pos]);
    ++pos;
  }
  for (int i = 0; i < kNewLength; ++i) {
    EXPECT_EQ(new_array[i], vec[pos]);
    ++pos;
  }
  for (size_t i = insert_position; i < array_length(); ++i) {
    EXPECT_EQ(array_[i], vec[pos]);
    ++pos;
  }
}

// Test the InsertZerosAt method with an insert position in the middle of the
// vector. Use the InsertAt method as reference.
TEST_F(AudioVectorTest, InsertZerosAt) {
  AudioVector vec;
  AudioVector vec_ref;
  vec.PushBack(array_, array_length());
  vec_ref.PushBack(array_, array_length());
  static const int kNewLength = 5;
  int insert_position = 5;
  vec.InsertZerosAt(kNewLength, insert_position);
  int16_t new_array[kNewLength] = {0};  // All zero elements.
  vec_ref.InsertAt(new_array, kNewLength, insert_position);
  // Verify that the vectors are identical.
  ASSERT_EQ(vec_ref.Size(), vec.Size());
  for (size_t i = 0; i < vec.Size(); ++i) {
    EXPECT_EQ(vec_ref[i], vec[i]);
  }
}

// Test the InsertAt method with an insert position at the start of the vector.
TEST_F(AudioVectorTest, InsertAtBeginning) {
  AudioVector vec;
  vec.PushBack(array_, array_length());
  static const int kNewLength = 5;
  int16_t new_array[kNewLength];
  // Set array elements to {100, 101, 102, ... }.
  for (int i = 0; i < kNewLength; ++i) {
    new_array[i] = 100 + i;
  }
  int insert_position = 0;
  vec.InsertAt(new_array, kNewLength, insert_position);
  // Verify that the vector looks as follows:
  // {100, 101, ..., 100 + kNewLength - 1,
  //  0, 1, ..., kLength - 1}.
  size_t pos = 0;
  for (int i = 0; i < kNewLength; ++i) {
    EXPECT_EQ(new_array[i], vec[pos]);
    ++pos;
  }
  for (size_t i = insert_position; i < array_length(); ++i) {
    EXPECT_EQ(array_[i], vec[pos]);
    ++pos;
  }
}

// Test the InsertAt method with an insert position at the end of the vector.
TEST_F(AudioVectorTest, InsertAtEnd) {
  AudioVector vec;
  vec.PushBack(array_, array_length());
  static const int kNewLength = 5;
  int16_t new_array[kNewLength];
  // Set array elements to {100, 101, 102, ... }.
  for (int i = 0; i < kNewLength; ++i) {
    new_array[i] = 100 + i;
  }
  int insert_position = rtc::checked_cast<int>(array_length());
  vec.InsertAt(new_array, kNewLength, insert_position);
  // Verify that the vector looks as follows:
  // {0, 1, ..., kLength - 1, 100, 101, ..., 100 + kNewLength - 1 }.
  size_t pos = 0;
  for (size_t i = 0; i < array_length(); ++i) {
    EXPECT_EQ(array_[i], vec[pos]);
    ++pos;
  }
  for (int i = 0; i < kNewLength; ++i) {
    EXPECT_EQ(new_array[i], vec[pos]);
    ++pos;
  }
}

// Test the InsertAt method with an insert position beyond the end of the
// vector. Verify that a position beyond the end of the vector does not lead to
// an error. The expected outcome is the same as if the vector end was used as
// input position. That is, the input position should be capped at the maximum
// allowed value.
TEST_F(AudioVectorTest, InsertBeyondEnd) {
  AudioVector vec;
  vec.PushBack(array_, array_length());
  static const int kNewLength = 5;
  int16_t new_array[kNewLength];
  // Set array elements to {100, 101, 102, ... }.
  for (int i = 0; i < kNewLength; ++i) {
    new_array[i] = 100 + i;
  }
  int insert_position =
      rtc::checked_cast<int>(array_length() + 10);  // Too large.
  vec.InsertAt(new_array, kNewLength, insert_position);
  // Verify that the vector looks as follows:
  // {0, 1, ..., kLength - 1, 100, 101, ..., 100 + kNewLength - 1 }.
  size_t pos = 0;
  for (size_t i = 0; i < array_length(); ++i) {
    EXPECT_EQ(array_[i], vec[pos]);
    ++pos;
  }
  for (int i = 0; i < kNewLength; ++i) {
    EXPECT_EQ(new_array[i], vec[pos]);
    ++pos;
  }
}

// Test the OverwriteAt method with a position such that all of the new values
// fit within the old vector.
TEST_F(AudioVectorTest, OverwriteAt) {
  AudioVector vec;
  vec.PushBack(array_, array_length());
  static const int kNewLength = 5;
  int16_t new_array[kNewLength];
  // Set array elements to {100, 101, 102, ... }.
  for (int i = 0; i < kNewLength; ++i) {
    new_array[i] = 100 + i;
  }
  size_t insert_position = 2;
  vec.OverwriteAt(new_array, kNewLength, insert_position);
  // Verify that the vector looks as follows:
  // {0, ..., `insert_position` - 1, 100, 101, ..., 100 + kNewLength - 1,
  //  `insert_position`, `insert_position` + 1, ..., kLength - 1}.
  size_t pos = 0;
  for (pos = 0; pos < insert_position; ++pos) {
    EXPECT_EQ(array_[pos], vec[pos]);
  }
  for (int i = 0; i < kNewLength; ++i) {
    EXPECT_EQ(new_array[i], vec[pos]);
    ++pos;
  }
  for (; pos < array_length(); ++pos) {
    EXPECT_EQ(array_[pos], vec[pos]);
  }
}

// Test the OverwriteAt method with a position such that some of the new values
// extend beyond the end of the current vector. This is valid, and the vector is
// expected to expand to accommodate the new values.
TEST_F(AudioVectorTest, OverwriteBeyondEnd) {
  AudioVector vec;
  vec.PushBack(array_, array_length());
  static const int kNewLength = 5;
  int16_t new_array[kNewLength];
  // Set array elements to {100, 101, 102, ... }.
  for (int i = 0; i < kNewLength; ++i) {
    new_array[i] = 100 + i;
  }
  int insert_position = rtc::checked_cast<int>(array_length() - 2);
  vec.OverwriteAt(new_array, kNewLength, insert_position);
  ASSERT_EQ(array_length() - 2u + kNewLength, vec.Size());
  // Verify that the vector looks as follows:
  // {0, ..., `insert_position` - 1, 100, 101, ..., 100 + kNewLength - 1,
  //  `insert_position`, `insert_position` + 1, ..., kLength - 1}.
  int pos = 0;
  for (pos = 0; pos < insert_position; ++pos) {
    EXPECT_EQ(array_[pos], vec[pos]);
  }
  for (int i = 0; i < kNewLength; ++i) {
    EXPECT_EQ(new_array[i], vec[pos]);
    ++pos;
  }
  // Verify that we checked to the end of `vec`.
  EXPECT_EQ(vec.Size(), static_cast<size_t>(pos));
}

TEST_F(AudioVectorTest, CrossFade) {
  static const size_t kLength = 100;
  static const size_t kFadeLength = 10;
  AudioVector vec1(kLength);
  AudioVector vec2(kLength);
  // Set all vector elements to 0 in `vec1` and 100 in `vec2`.
  for (size_t i = 0; i < kLength; ++i) {
    vec1[i] = 0;
    vec2[i] = 100;
  }
  vec1.CrossFade(vec2, kFadeLength);
  ASSERT_EQ(2 * kLength - kFadeLength, vec1.Size());
  // First part untouched.
  for (size_t i = 0; i < kLength - kFadeLength; ++i) {
    EXPECT_EQ(0, vec1[i]);
  }
  // Check mixing zone.
  for (size_t i = 0; i < kFadeLength; ++i) {
    EXPECT_NEAR((i + 1) * 100 / (kFadeLength + 1),
                vec1[kLength - kFadeLength + i], 1);
  }
  // Second part untouched.
  for (size_t i = kLength; i < vec1.Size(); ++i) {
    EXPECT_EQ(100, vec1[i]);
  }
}

}  // namespace webrtc

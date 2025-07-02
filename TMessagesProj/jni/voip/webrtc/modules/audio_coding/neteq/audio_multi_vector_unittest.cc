/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/audio_multi_vector.h"

#include <stdlib.h>

#include <string>
#include <vector>

#include "rtc_base/numerics/safe_conversions.h"
#include "test/gtest.h"

namespace webrtc {

// This is a value-parameterized test. The test cases are instantiated with
// different values for the test parameter, which is used to determine the
// number of channels in the AudioMultiBuffer. Note that it is not possible
// to combine typed testing with value-parameterized testing, and since the
// tests for AudioVector already covers a number of different type parameters,
// this test focuses on testing different number of channels, and keeping the
// value type constant.

class AudioMultiVectorTest : public ::testing::TestWithParam<size_t> {
 protected:
  AudioMultiVectorTest()
      : num_channels_(GetParam()),  // Get the test parameter.
        array_interleaved_(num_channels_ * array_length()) {}

  ~AudioMultiVectorTest() = default;

  virtual void SetUp() {
    // Populate test arrays.
    for (size_t i = 0; i < array_length(); ++i) {
      array_[i] = static_cast<int16_t>(i);
    }
    int16_t* ptr = array_interleaved_.data();
    // Write 100, 101, 102, ... for first channel.
    // Write 200, 201, 202, ... for second channel.
    // And so on.
    for (size_t i = 0; i < array_length(); ++i) {
      for (size_t j = 1; j <= num_channels_; ++j) {
        *ptr = rtc::checked_cast<int16_t>(j * 100 + i);
        ++ptr;
      }
    }
  }

  size_t array_length() const { return sizeof(array_) / sizeof(array_[0]); }

  const size_t num_channels_;
  int16_t array_[10];
  std::vector<int16_t> array_interleaved_;
};

// Create and destroy AudioMultiVector objects, both empty and with a predefined
// length.
TEST_P(AudioMultiVectorTest, CreateAndDestroy) {
  AudioMultiVector vec1(num_channels_);
  EXPECT_TRUE(vec1.Empty());
  EXPECT_EQ(num_channels_, vec1.Channels());
  EXPECT_EQ(0u, vec1.Size());

  size_t initial_size = 17;
  AudioMultiVector vec2(num_channels_, initial_size);
  EXPECT_FALSE(vec2.Empty());
  EXPECT_EQ(num_channels_, vec2.Channels());
  EXPECT_EQ(initial_size, vec2.Size());
}

// Test the subscript operator [] for getting and setting.
TEST_P(AudioMultiVectorTest, SubscriptOperator) {
  AudioMultiVector vec(num_channels_, array_length());
  for (size_t channel = 0; channel < num_channels_; ++channel) {
    for (size_t i = 0; i < array_length(); ++i) {
      vec[channel][i] = static_cast<int16_t>(i);
      // Make sure to use the const version.
      const AudioVector& audio_vec = vec[channel];
      EXPECT_EQ(static_cast<int16_t>(i), audio_vec[i]);
    }
  }
}

// Test the PushBackInterleaved method and the CopyFrom method. The Clear
// method is also invoked.
TEST_P(AudioMultiVectorTest, PushBackInterleavedAndCopy) {
  AudioMultiVector vec(num_channels_);
  vec.PushBackInterleaved(array_interleaved_);
  AudioMultiVector vec_copy(num_channels_);
  vec.CopyTo(&vec_copy);  // Copy from `vec` to `vec_copy`.
  ASSERT_EQ(num_channels_, vec.Channels());
  ASSERT_EQ(array_length(), vec.Size());
  ASSERT_EQ(num_channels_, vec_copy.Channels());
  ASSERT_EQ(array_length(), vec_copy.Size());
  for (size_t channel = 0; channel < vec.Channels(); ++channel) {
    for (size_t i = 0; i < array_length(); ++i) {
      EXPECT_EQ(static_cast<int16_t>((channel + 1) * 100 + i), vec[channel][i]);
      EXPECT_EQ(vec[channel][i], vec_copy[channel][i]);
    }
  }

  // Clear `vec` and verify that it is empty.
  vec.Clear();
  EXPECT_TRUE(vec.Empty());

  // Now copy the empty vector and verify that the copy becomes empty too.
  vec.CopyTo(&vec_copy);
  EXPECT_TRUE(vec_copy.Empty());
}

// Try to copy to a NULL pointer. Nothing should happen.
TEST_P(AudioMultiVectorTest, CopyToNull) {
  AudioMultiVector vec(num_channels_);
  AudioMultiVector* vec_copy = NULL;
  vec.PushBackInterleaved(array_interleaved_);
  vec.CopyTo(vec_copy);
}

// Test the PushBack method with another AudioMultiVector as input argument.
TEST_P(AudioMultiVectorTest, PushBackVector) {
  AudioMultiVector vec1(num_channels_, array_length());
  AudioMultiVector vec2(num_channels_, array_length());
  // Set the first vector to [0, 1, ..., array_length() - 1] +
  //   100 * channel_number.
  // Set the second vector to [array_length(), array_length() + 1, ...,
  //   2 * array_length() - 1] + 100 * channel_number.
  for (size_t channel = 0; channel < num_channels_; ++channel) {
    for (size_t i = 0; i < array_length(); ++i) {
      vec1[channel][i] = static_cast<int16_t>(i + 100 * channel);
      vec2[channel][i] =
          static_cast<int16_t>(i + 100 * channel + array_length());
    }
  }
  // Append vec2 to the back of vec1.
  vec1.PushBack(vec2);
  ASSERT_EQ(2u * array_length(), vec1.Size());
  for (size_t channel = 0; channel < num_channels_; ++channel) {
    for (size_t i = 0; i < 2 * array_length(); ++i) {
      EXPECT_EQ(static_cast<int16_t>(i + 100 * channel), vec1[channel][i]);
    }
  }
}

// Test the PushBackFromIndex method.
TEST_P(AudioMultiVectorTest, PushBackFromIndex) {
  AudioMultiVector vec1(num_channels_);
  vec1.PushBackInterleaved(array_interleaved_);
  AudioMultiVector vec2(num_channels_);

  // Append vec1 to the back of vec2 (which is empty). Read vec1 from the second
  // last element.
  vec2.PushBackFromIndex(vec1, array_length() - 2);
  ASSERT_EQ(2u, vec2.Size());
  for (size_t channel = 0; channel < num_channels_; ++channel) {
    for (size_t i = 0; i < 2; ++i) {
      EXPECT_EQ(array_interleaved_[channel +
                                   num_channels_ * (array_length() - 2 + i)],
                vec2[channel][i]);
    }
  }
}

// Starts with pushing some values to the vector, then test the Zeros method.
TEST_P(AudioMultiVectorTest, Zeros) {
  AudioMultiVector vec(num_channels_);
  vec.PushBackInterleaved(array_interleaved_);
  vec.Zeros(2 * array_length());
  ASSERT_EQ(num_channels_, vec.Channels());
  ASSERT_EQ(2u * array_length(), vec.Size());
  for (size_t channel = 0; channel < num_channels_; ++channel) {
    for (size_t i = 0; i < 2 * array_length(); ++i) {
      EXPECT_EQ(0, vec[channel][i]);
    }
  }
}

// Test the ReadInterleaved method
TEST_P(AudioMultiVectorTest, ReadInterleaved) {
  AudioMultiVector vec(num_channels_);
  vec.PushBackInterleaved(array_interleaved_);
  int16_t* output = new int16_t[array_interleaved_.size()];
  // Read 5 samples.
  size_t read_samples = 5;
  EXPECT_EQ(num_channels_ * read_samples,
            vec.ReadInterleaved(read_samples, output));
  EXPECT_EQ(0, memcmp(array_interleaved_.data(), output,
                      read_samples * sizeof(int16_t)));

  // Read too many samples. Expect to get all samples from the vector.
  EXPECT_EQ(array_interleaved_.size(),
            vec.ReadInterleaved(array_length() + 1, output));
  EXPECT_EQ(0, memcmp(array_interleaved_.data(), output,
                      read_samples * sizeof(int16_t)));

  delete[] output;
}

// Test the PopFront method.
TEST_P(AudioMultiVectorTest, PopFront) {
  AudioMultiVector vec(num_channels_);
  vec.PushBackInterleaved(array_interleaved_);
  vec.PopFront(1);  // Remove one element from each channel.
  ASSERT_EQ(array_length() - 1u, vec.Size());
  // Let `ptr` point to the second element of the first channel in the
  // interleaved array.
  int16_t* ptr = &array_interleaved_[num_channels_];
  for (size_t i = 0; i < array_length() - 1; ++i) {
    for (size_t channel = 0; channel < num_channels_; ++channel) {
      EXPECT_EQ(*ptr, vec[channel][i]);
      ++ptr;
    }
  }
  vec.PopFront(array_length());  // Remove more elements than vector size.
  EXPECT_EQ(0u, vec.Size());
}

// Test the PopBack method.
TEST_P(AudioMultiVectorTest, PopBack) {
  AudioMultiVector vec(num_channels_);
  vec.PushBackInterleaved(array_interleaved_);
  vec.PopBack(1);  // Remove one element from each channel.
  ASSERT_EQ(array_length() - 1u, vec.Size());
  // Let `ptr` point to the first element of the first channel in the
  // interleaved array.
  int16_t* ptr = array_interleaved_.data();
  for (size_t i = 0; i < array_length() - 1; ++i) {
    for (size_t channel = 0; channel < num_channels_; ++channel) {
      EXPECT_EQ(*ptr, vec[channel][i]);
      ++ptr;
    }
  }
  vec.PopBack(array_length());  // Remove more elements than vector size.
  EXPECT_EQ(0u, vec.Size());
}

// Test the AssertSize method.
TEST_P(AudioMultiVectorTest, AssertSize) {
  AudioMultiVector vec(num_channels_, array_length());
  EXPECT_EQ(array_length(), vec.Size());
  // Start with asserting with smaller sizes than already allocated.
  vec.AssertSize(0);
  vec.AssertSize(array_length() - 1);
  // Nothing should have changed.
  EXPECT_EQ(array_length(), vec.Size());
  // Assert with one element longer than already allocated.
  vec.AssertSize(array_length() + 1);
  // Expect vector to have grown.
  EXPECT_EQ(array_length() + 1, vec.Size());
  // Also check the individual AudioVectors.
  for (size_t channel = 0; channel < vec.Channels(); ++channel) {
    EXPECT_EQ(array_length() + 1u, vec[channel].Size());
  }
}

// Test the PushBack method with another AudioMultiVector as input argument.
TEST_P(AudioMultiVectorTest, OverwriteAt) {
  AudioMultiVector vec1(num_channels_);
  vec1.PushBackInterleaved(array_interleaved_);
  AudioMultiVector vec2(num_channels_);
  vec2.Zeros(3);  // 3 zeros in each channel.
  // Overwrite vec2 at position 5.
  vec1.OverwriteAt(vec2, 3, 5);
  // Verify result.
  // Length remains the same.
  ASSERT_EQ(array_length(), vec1.Size());
  int16_t* ptr = array_interleaved_.data();
  for (size_t i = 0; i < array_length() - 1; ++i) {
    for (size_t channel = 0; channel < num_channels_; ++channel) {
      if (i >= 5 && i <= 7) {
        // Elements 5, 6, 7 should have been replaced with zeros.
        EXPECT_EQ(0, vec1[channel][i]);
      } else {
        EXPECT_EQ(*ptr, vec1[channel][i]);
      }
      ++ptr;
    }
  }
}

// Test the CopyChannel method, when the test is instantiated with at least two
// channels.
TEST_P(AudioMultiVectorTest, CopyChannel) {
  if (num_channels_ < 2)
    return;

  AudioMultiVector vec(num_channels_);
  vec.PushBackInterleaved(array_interleaved_);
  // Create a reference copy.
  AudioMultiVector ref(num_channels_);
  ref.PushBack(vec);
  // Copy from first to last channel.
  vec.CopyChannel(0, num_channels_ - 1);
  // Verify that the first and last channels are identical; the others should
  // be left untouched.
  for (size_t i = 0; i < array_length(); ++i) {
    // Verify that all but the last channel are untouched.
    for (size_t channel = 0; channel < num_channels_ - 1; ++channel) {
      EXPECT_EQ(ref[channel][i], vec[channel][i]);
    }
    // Verify that the last and the first channels are identical.
    EXPECT_EQ(vec[0][i], vec[num_channels_ - 1][i]);
  }
}

TEST_P(AudioMultiVectorTest, PushBackEmptyArray) {
  AudioMultiVector vec(num_channels_);
  vec.PushBackInterleaved({});
  EXPECT_TRUE(vec.Empty());
}

INSTANTIATE_TEST_SUITE_P(TestNumChannels,
                         AudioMultiVectorTest,
                         ::testing::Values(static_cast<size_t>(1),
                                           static_cast<size_t>(2),
                                           static_cast<size_t>(5)));
}  // namespace webrtc

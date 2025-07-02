/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/random.h"

#include <math.h>

#include <limits>
#include <vector>

#include "rtc_base/numerics/math_utils.h"  // unsigned difference
#include "test/gtest.h"

namespace webrtc {

namespace {
// Computes the positive remainder of x/n.
template <typename T>
T fdiv_remainder(T x, T n) {
  RTC_CHECK_GE(n, 0);
  T remainder = x % n;
  if (remainder < 0)
    remainder += n;
  return remainder;
}
}  // namespace

// Sample a number of random integers of type T. Divide them into buckets
// based on the remainder when dividing by bucket_count and check that each
// bucket gets roughly the expected number of elements.
template <typename T>
void UniformBucketTest(T bucket_count, int samples, Random* prng) {
  std::vector<int> buckets(bucket_count, 0);

  uint64_t total_values = 1ull << (std::numeric_limits<T>::digits +
                                   std::numeric_limits<T>::is_signed);
  T upper_limit =
      std::numeric_limits<T>::max() -
      static_cast<T>(total_values % static_cast<uint64_t>(bucket_count));
  ASSERT_GT(upper_limit, std::numeric_limits<T>::max() / 2);

  for (int i = 0; i < samples; i++) {
    T sample;
    do {
      // We exclude a few numbers from the range so that it is divisible by
      // the number of buckets. If we are unlucky and hit one of the excluded
      // numbers we just resample. Note that if the number of buckets is a
      // power of 2, then we don't have to exclude anything.
      sample = prng->Rand<T>();
    } while (sample > upper_limit);
    buckets[fdiv_remainder(sample, bucket_count)]++;
  }

  for (T i = 0; i < bucket_count; i++) {
    // Expect the result to be within 3 standard deviations of the mean.
    EXPECT_NEAR(buckets[i], samples / bucket_count,
                3 * sqrt(samples / bucket_count));
  }
}

TEST(RandomNumberGeneratorTest, BucketTestSignedChar) {
  Random prng(7297352569824ull);
  UniformBucketTest<signed char>(64, 640000, &prng);
  UniformBucketTest<signed char>(11, 440000, &prng);
  UniformBucketTest<signed char>(3, 270000, &prng);
}

TEST(RandomNumberGeneratorTest, BucketTestUnsignedChar) {
  Random prng(7297352569824ull);
  UniformBucketTest<unsigned char>(64, 640000, &prng);
  UniformBucketTest<unsigned char>(11, 440000, &prng);
  UniformBucketTest<unsigned char>(3, 270000, &prng);
}

TEST(RandomNumberGeneratorTest, BucketTestSignedShort) {
  Random prng(7297352569824ull);
  UniformBucketTest<int16_t>(64, 640000, &prng);
  UniformBucketTest<int16_t>(11, 440000, &prng);
  UniformBucketTest<int16_t>(3, 270000, &prng);
}

TEST(RandomNumberGeneratorTest, BucketTestUnsignedShort) {
  Random prng(7297352569824ull);
  UniformBucketTest<uint16_t>(64, 640000, &prng);
  UniformBucketTest<uint16_t>(11, 440000, &prng);
  UniformBucketTest<uint16_t>(3, 270000, &prng);
}

TEST(RandomNumberGeneratorTest, BucketTestSignedInt) {
  Random prng(7297352569824ull);
  UniformBucketTest<signed int>(64, 640000, &prng);
  UniformBucketTest<signed int>(11, 440000, &prng);
  UniformBucketTest<signed int>(3, 270000, &prng);
}

TEST(RandomNumberGeneratorTest, BucketTestUnsignedInt) {
  Random prng(7297352569824ull);
  UniformBucketTest<unsigned int>(64, 640000, &prng);
  UniformBucketTest<unsigned int>(11, 440000, &prng);
  UniformBucketTest<unsigned int>(3, 270000, &prng);
}

// The range of the random numbers is divided into bucket_count intervals
// of consecutive numbers. Check that approximately equally many numbers
// from each inteval are generated.
void BucketTestSignedInterval(unsigned int bucket_count,
                              unsigned int samples,
                              int32_t low,
                              int32_t high,
                              int sigma_level,
                              Random* prng) {
  std::vector<unsigned int> buckets(bucket_count, 0);

  ASSERT_GE(high, low);
  ASSERT_GE(bucket_count, 2u);
  uint32_t interval = webrtc_impl::unsigned_difference<int32_t>(high, low) + 1;
  uint32_t numbers_per_bucket;
  if (interval == 0) {
    // The computation high - low + 1 should be 2^32 but overflowed
    // Hence, bucket_count must be a power of 2
    ASSERT_EQ(bucket_count & (bucket_count - 1), 0u);
    numbers_per_bucket = (0x80000000u / bucket_count) * 2;
  } else {
    ASSERT_EQ(interval % bucket_count, 0u);
    numbers_per_bucket = interval / bucket_count;
  }

  for (unsigned int i = 0; i < samples; i++) {
    int32_t sample = prng->Rand(low, high);
    EXPECT_LE(low, sample);
    EXPECT_GE(high, sample);
    buckets[webrtc_impl::unsigned_difference<int32_t>(sample, low) /
            numbers_per_bucket]++;
  }

  for (unsigned int i = 0; i < bucket_count; i++) {
    // Expect the result to be within 3 standard deviations of the mean,
    // or more generally, within sigma_level standard deviations of the mean.
    double mean = static_cast<double>(samples) / bucket_count;
    EXPECT_NEAR(buckets[i], mean, sigma_level * sqrt(mean));
  }
}

// The range of the random numbers is divided into bucket_count intervals
// of consecutive numbers. Check that approximately equally many numbers
// from each inteval are generated.
void BucketTestUnsignedInterval(unsigned int bucket_count,
                                unsigned int samples,
                                uint32_t low,
                                uint32_t high,
                                int sigma_level,
                                Random* prng) {
  std::vector<unsigned int> buckets(bucket_count, 0);

  ASSERT_GE(high, low);
  ASSERT_GE(bucket_count, 2u);
  uint32_t interval = high - low + 1;
  uint32_t numbers_per_bucket;
  if (interval == 0) {
    // The computation high - low + 1 should be 2^32 but overflowed
    // Hence, bucket_count must be a power of 2
    ASSERT_EQ(bucket_count & (bucket_count - 1), 0u);
    numbers_per_bucket = (0x80000000u / bucket_count) * 2;
  } else {
    ASSERT_EQ(interval % bucket_count, 0u);
    numbers_per_bucket = interval / bucket_count;
  }

  for (unsigned int i = 0; i < samples; i++) {
    uint32_t sample = prng->Rand(low, high);
    EXPECT_LE(low, sample);
    EXPECT_GE(high, sample);
    buckets[(sample - low) / numbers_per_bucket]++;
  }

  for (unsigned int i = 0; i < bucket_count; i++) {
    // Expect the result to be within 3 standard deviations of the mean,
    // or more generally, within sigma_level standard deviations of the mean.
    double mean = static_cast<double>(samples) / bucket_count;
    EXPECT_NEAR(buckets[i], mean, sigma_level * sqrt(mean));
  }
}

TEST(RandomNumberGeneratorTest, UniformUnsignedInterval) {
  Random prng(299792458ull);
  BucketTestUnsignedInterval(2, 100000, 0, 1, 3, &prng);
  BucketTestUnsignedInterval(7, 100000, 1, 14, 3, &prng);
  BucketTestUnsignedInterval(11, 100000, 1000, 1010, 3, &prng);
  BucketTestUnsignedInterval(100, 100000, 0, 99, 3, &prng);
  BucketTestUnsignedInterval(2, 100000, 0, 4294967295, 3, &prng);
  BucketTestUnsignedInterval(17, 100000, 455, 2147484110, 3, &prng);
  // 99.7% of all samples will be within 3 standard deviations of the mean,
  // but since we test 1000 buckets we allow an interval of 4 sigma.
  BucketTestUnsignedInterval(1000, 1000000, 0, 2147483999, 4, &prng);
}

TEST(RandomNumberGeneratorTest, UniformSignedInterval) {
  Random prng(66260695729ull);
  BucketTestSignedInterval(2, 100000, 0, 1, 3, &prng);
  BucketTestSignedInterval(7, 100000, -2, 4, 3, &prng);
  BucketTestSignedInterval(11, 100000, 1000, 1010, 3, &prng);
  BucketTestSignedInterval(100, 100000, 0, 99, 3, &prng);
  BucketTestSignedInterval(2, 100000, std::numeric_limits<int32_t>::min(),
                           std::numeric_limits<int32_t>::max(), 3, &prng);
  BucketTestSignedInterval(17, 100000, -1073741826, 1073741829, 3, &prng);
  // 99.7% of all samples will be within 3 standard deviations of the mean,
  // but since we test 1000 buckets we allow an interval of 4 sigma.
  BucketTestSignedInterval(1000, 1000000, -352, 2147483647, 4, &prng);
}

// The range of the random numbers is divided into bucket_count intervals
// of consecutive numbers. Check that approximately equally many numbers
// from each inteval are generated.
void BucketTestFloat(unsigned int bucket_count,
                     unsigned int samples,
                     int sigma_level,
                     Random* prng) {
  ASSERT_GE(bucket_count, 2u);
  std::vector<unsigned int> buckets(bucket_count, 0);

  for (unsigned int i = 0; i < samples; i++) {
    uint32_t sample = bucket_count * prng->Rand<float>();
    EXPECT_LE(0u, sample);
    EXPECT_GE(bucket_count - 1, sample);
    buckets[sample]++;
  }

  for (unsigned int i = 0; i < bucket_count; i++) {
    // Expect the result to be within 3 standard deviations of the mean,
    // or more generally, within sigma_level standard deviations of the mean.
    double mean = static_cast<double>(samples) / bucket_count;
    EXPECT_NEAR(buckets[i], mean, sigma_level * sqrt(mean));
  }
}

TEST(RandomNumberGeneratorTest, UniformFloatInterval) {
  Random prng(1380648813ull);
  BucketTestFloat(100, 100000, 3, &prng);
  // 99.7% of all samples will be within 3 standard deviations of the mean,
  // but since we test 1000 buckets we allow an interval of 4 sigma.
  // BucketTestSignedInterval(1000, 1000000, -352, 2147483647, 4, &prng);
}

TEST(RandomNumberGeneratorTest, SignedHasSameBitPattern) {
  Random prng_signed(66738480ull), prng_unsigned(66738480ull);

  for (int i = 0; i < 1000; i++) {
    signed int s = prng_signed.Rand<signed int>();
    unsigned int u = prng_unsigned.Rand<unsigned int>();
    EXPECT_EQ(u, static_cast<unsigned int>(s));
  }

  for (int i = 0; i < 1000; i++) {
    int16_t s = prng_signed.Rand<int16_t>();
    uint16_t u = prng_unsigned.Rand<uint16_t>();
    EXPECT_EQ(u, static_cast<uint16_t>(s));
  }

  for (int i = 0; i < 1000; i++) {
    signed char s = prng_signed.Rand<signed char>();
    unsigned char u = prng_unsigned.Rand<unsigned char>();
    EXPECT_EQ(u, static_cast<unsigned char>(s));
  }
}

TEST(RandomNumberGeneratorTest, Gaussian) {
  const int kN = 100000;
  const int kBuckets = 100;
  const double kMean = 49;
  const double kStddev = 10;

  Random prng(1256637061);

  std::vector<unsigned int> buckets(kBuckets, 0);
  for (int i = 0; i < kN; i++) {
    int index = prng.Gaussian(kMean, kStddev) + 0.5;
    if (index >= 0 && index < kBuckets) {
      buckets[index]++;
    }
  }

  const double kPi = 3.14159265358979323846;
  const double kScale = 1 / (kStddev * sqrt(2.0 * kPi));
  const double kDiv = -2.0 * kStddev * kStddev;
  for (int n = 0; n < kBuckets; ++n) {
    // Use Simpsons rule to estimate the probability that a random gaussian
    // sample is in the interval [n-0.5, n+0.5].
    double f_left = kScale * exp((n - kMean - 0.5) * (n - kMean - 0.5) / kDiv);
    double f_mid = kScale * exp((n - kMean) * (n - kMean) / kDiv);
    double f_right = kScale * exp((n - kMean + 0.5) * (n - kMean + 0.5) / kDiv);
    double normal_dist = (f_left + 4 * f_mid + f_right) / 6;
    // Expect the number of samples to be within 3 standard deviations
    // (rounded up) of the expected number of samples in the bucket.
    EXPECT_NEAR(buckets[n], kN * normal_dist, 3 * sqrt(kN * normal_dist) + 1);
  }
}

}  // namespace webrtc

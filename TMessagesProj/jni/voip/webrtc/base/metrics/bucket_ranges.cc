// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/metrics/bucket_ranges.h"

#include <cmath>

#include "base/logging.h"
#include "base/metrics/crc32.h"

namespace base {

BucketRanges::BucketRanges(size_t num_ranges)
    : ranges_(num_ranges, 0),
      checksum_(0) {}

BucketRanges::~BucketRanges() = default;

uint32_t BucketRanges::CalculateChecksum() const {
  // Crc of empty ranges_ happens to be 0. This early exit prevents trying to
  // take the address of ranges_[0] which will fail for an empty vector even
  // if that address is never used.
  const size_t ranges_size = ranges_.size();
  if (ranges_size == 0)
    return 0;

  // Checksum is seeded with the ranges "size".
  return Crc32(static_cast<uint32_t>(ranges_size), &ranges_[0],
               sizeof(ranges_[0]) * ranges_size);
}

bool BucketRanges::HasValidChecksum() const {
  return CalculateChecksum() == checksum_;
}

void BucketRanges::ResetChecksum() {
  checksum_ = CalculateChecksum();
}

bool BucketRanges::Equals(const BucketRanges* other) const {
  if (checksum_ != other->checksum_)
    return false;
  if (ranges_.size() != other->ranges_.size())
    return false;
  for (size_t index = 0; index < ranges_.size(); ++index) {
    if (ranges_[index] != other->ranges_[index])
      return false;
  }
  return true;
}

}  // namespace base

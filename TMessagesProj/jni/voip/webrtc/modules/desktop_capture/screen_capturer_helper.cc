/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/screen_capturer_helper.h"

#include <assert.h>

namespace webrtc {

ScreenCapturerHelper::ScreenCapturerHelper()
    : invalid_region_lock_(RWLockWrapper::CreateRWLock()), log_grid_size_(0) {}

ScreenCapturerHelper::~ScreenCapturerHelper() {}

void ScreenCapturerHelper::ClearInvalidRegion() {
  WriteLockScoped scoped_invalid_region_lock(*invalid_region_lock_);
  invalid_region_.Clear();
}

void ScreenCapturerHelper::InvalidateRegion(
    const DesktopRegion& invalid_region) {
  WriteLockScoped scoped_invalid_region_lock(*invalid_region_lock_);
  invalid_region_.AddRegion(invalid_region);
}

void ScreenCapturerHelper::InvalidateScreen(const DesktopSize& size) {
  WriteLockScoped scoped_invalid_region_lock(*invalid_region_lock_);
  invalid_region_.AddRect(DesktopRect::MakeSize(size));
}

void ScreenCapturerHelper::TakeInvalidRegion(DesktopRegion* invalid_region) {
  invalid_region->Clear();

  {
    WriteLockScoped scoped_invalid_region_lock(*invalid_region_lock_);
    invalid_region->Swap(&invalid_region_);
  }

  if (log_grid_size_ > 0) {
    DesktopRegion expanded_region;
    ExpandToGrid(*invalid_region, log_grid_size_, &expanded_region);
    expanded_region.Swap(invalid_region);

    invalid_region->IntersectWith(DesktopRect::MakeSize(size_most_recent_));
  }
}

void ScreenCapturerHelper::SetLogGridSize(int log_grid_size) {
  log_grid_size_ = log_grid_size;
}

const DesktopSize& ScreenCapturerHelper::size_most_recent() const {
  return size_most_recent_;
}

void ScreenCapturerHelper::set_size_most_recent(const DesktopSize& size) {
  size_most_recent_ = size;
}

// Returns the largest multiple of |n| that is <= |x|.
// |n| must be a power of 2. |nMask| is ~(|n| - 1).
static int DownToMultiple(int x, int nMask) {
  return (x & nMask);
}

// Returns the smallest multiple of |n| that is >= |x|.
// |n| must be a power of 2. |nMask| is ~(|n| - 1).
static int UpToMultiple(int x, int n, int nMask) {
  return ((x + n - 1) & nMask);
}

void ScreenCapturerHelper::ExpandToGrid(const DesktopRegion& region,
                                        int log_grid_size,
                                        DesktopRegion* result) {
  assert(log_grid_size >= 1);
  int grid_size = 1 << log_grid_size;
  int grid_size_mask = ~(grid_size - 1);

  result->Clear();
  for (DesktopRegion::Iterator it(region); !it.IsAtEnd(); it.Advance()) {
    int left = DownToMultiple(it.rect().left(), grid_size_mask);
    int right = UpToMultiple(it.rect().right(), grid_size, grid_size_mask);
    int top = DownToMultiple(it.rect().top(), grid_size_mask);
    int bottom = UpToMultiple(it.rect().bottom(), grid_size, grid_size_mask);
    result->AddRect(DesktopRect::MakeLTRB(left, top, right, bottom));
  }
}

}  // namespace webrtc

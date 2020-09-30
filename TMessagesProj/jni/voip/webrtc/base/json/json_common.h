// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_JSON_JSON_COMMON_H_
#define BASE_JSON_JSON_COMMON_H_

#include <stddef.h>

#include "base/logging.h"
#include "base/macros.h"

namespace base {
namespace internal {

// Chosen to support 99.9% of documents found in the wild late 2016.
// http://crbug.com/673263
const size_t kAbsoluteMaxDepth = 200;

// Simple class that checks for maximum recursion/stack overflow.
class StackMarker {
 public:
  StackMarker(size_t max_depth, size_t* depth)
      : max_depth_(max_depth), depth_(depth) {
    ++(*depth_);
    DCHECK_LE(*depth_, max_depth_);
  }
  ~StackMarker() { --(*depth_); }

  bool IsTooDeep() const { return *depth_ >= max_depth_; }

 private:
  const size_t max_depth_;
  size_t* const depth_;

  DISALLOW_COPY_AND_ASSIGN(StackMarker);
};

}  // namespace internal
}  // namespace base

#endif  // BASE_JSON_JSON_COMMON_H_

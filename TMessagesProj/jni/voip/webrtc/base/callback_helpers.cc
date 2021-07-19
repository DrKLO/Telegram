// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/callback_helpers.h"

namespace base {

ScopedClosureRunner::ScopedClosureRunner() = default;

ScopedClosureRunner::ScopedClosureRunner(OnceClosure closure)
    : closure_(std::move(closure)) {}

ScopedClosureRunner::~ScopedClosureRunner() {
  if (!closure_.is_null())
    std::move(closure_).Run();
}

ScopedClosureRunner::ScopedClosureRunner(ScopedClosureRunner&& other)
    : closure_(other.Release()) {}

ScopedClosureRunner& ScopedClosureRunner::operator=(
    ScopedClosureRunner&& other) {
  ReplaceClosure(other.Release());
  return *this;
}

void ScopedClosureRunner::RunAndReset() {
  std::move(closure_).Run();
}

void ScopedClosureRunner::ReplaceClosure(OnceClosure closure) {
  closure_ = std::move(closure);
}

OnceClosure ScopedClosureRunner::Release() {
  return std::move(closure_);
}

}  // namespace base

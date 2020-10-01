// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/launch.h"
#include "build/build_config.h"

namespace base {

LaunchOptions::LaunchOptions() = default;

LaunchOptions::LaunchOptions(const LaunchOptions& other) = default;

LaunchOptions::~LaunchOptions() = default;

LaunchOptions LaunchOptionsForTest() {
  LaunchOptions options;
#if defined(OS_LINUX)
  // To prevent accidental privilege sharing to an untrusted child, processes
  // are started with PR_SET_NO_NEW_PRIVS. Do not set that here, since this
  // new child will be used for testing only.
  options.allow_new_privs = true;
#endif
  return options;
}

}  // namespace base

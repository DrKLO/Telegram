// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Workaround for crosbug:629593.  Using AFDO on the tcmalloc files is
// causing problems. The tcmalloc files depend on stack layouts and
// AFDO can mess with them. Better not to use AFDO there.  This is a
// temporary hack. We will add a mechanism in the build system to
// avoid using -fauto-profile for tcmalloc files.
#if !defined(__clang__) && \
    (defined(OS_CHROMEOS) || (__GNUC__ > 5 && __GNUC__ < 7))
// Note that this option only seems to be available in the chromeos GCC 4.9
// toolchain, and stock GCC 5 upto 7.
#pragma GCC optimize ("no-auto-profile")
#endif

#if defined(TCMALLOC_FOR_DEBUGALLOCATION)
#include "third_party/tcmalloc/chromium/src/debugallocation.cc"
#else
#include "third_party/tcmalloc/chromium/src/tcmalloc.cc"
#endif

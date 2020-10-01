// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <string>

#include "base/android/jni_android.h"
#include "base/android/jni_string.h"
#include "base/android/library_loader/anchor_functions_buildflags.h"
#include "base/android/library_loader/library_loader_hooks.h"
#include "base/android/library_loader/library_prefetcher.h"
#include "base/android/scoped_java_ref.h"
#include "base/base_jni_headers/LibraryPrefetcher_jni.h"
#include "base/logging.h"

namespace base {
namespace android {

static void JNI_LibraryPrefetcher_ForkAndPrefetchNativeLibrary(JNIEnv* env) {
#if BUILDFLAG(SUPPORTS_CODE_ORDERING)
  return NativeLibraryPrefetcher::ForkAndPrefetchNativeLibrary(
      IsUsingOrderfileOptimization());
#endif
}

static jint JNI_LibraryPrefetcher_PercentageOfResidentNativeLibraryCode(
    JNIEnv* env) {
#if BUILDFLAG(SUPPORTS_CODE_ORDERING)
  return NativeLibraryPrefetcher::PercentageOfResidentNativeLibraryCode();
#else
  return -1;
#endif
}

static void JNI_LibraryPrefetcher_PeriodicallyCollectResidency(JNIEnv* env) {
#if BUILDFLAG(SUPPORTS_CODE_ORDERING)
  NativeLibraryPrefetcher::PeriodicallyCollectResidency();
#else
  LOG(WARNING) << "Collecting residency is not supported.";
#endif
}

}  // namespace android
}  // namespace base

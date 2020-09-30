// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <jni.h>

#include "base/android/apk_assets.h"

#include "base/android/jni_array.h"
#include "base/android/jni_string.h"
#include "base/android/scoped_java_ref.h"
#include "base/base_jni_headers/ApkAssets_jni.h"
#include "base/file_descriptor_store.h"

namespace base {
namespace android {

int OpenApkAsset(const std::string& file_path,
                 base::MemoryMappedFile::Region* region) {
  // The AssetManager API of the NDK does not expose a method for accessing raw
  // resources :(
  JNIEnv* env = base::android::AttachCurrentThread();
  ScopedJavaLocalRef<jlongArray> jarr = Java_ApkAssets_open(
      env, base::android::ConvertUTF8ToJavaString(env, file_path));
  std::vector<jlong> results;
  base::android::JavaLongArrayToLongVector(env, jarr, &results);
  CHECK_EQ(3U, results.size());
  int fd = static_cast<int>(results[0]);
  region->offset = results[1];
  region->size = results[2];
  return fd;
}

bool RegisterApkAssetWithFileDescriptorStore(const std::string& key,
                                             const base::FilePath& file_path) {
  base::MemoryMappedFile::Region region =
      base::MemoryMappedFile::Region::kWholeFile;
  int asset_fd = OpenApkAsset(file_path.value(), &region);
  if (asset_fd == -1)
    return false;
  base::FileDescriptorStore::GetInstance().Set(key, base::ScopedFD(asset_fd),
                                               region);
  return true;
}

}  // namespace android
}  // namespace base

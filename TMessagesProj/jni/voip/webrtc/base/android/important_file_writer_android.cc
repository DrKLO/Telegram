// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <string>

#include "base/android/jni_array.h"
#include "base/android/jni_string.h"
#include "base/base_jni_headers/ImportantFileWriterAndroid_jni.h"
#include "base/files/important_file_writer.h"
#include "base/threading/thread_restrictions.h"

namespace base {
namespace android {

static jboolean JNI_ImportantFileWriterAndroid_WriteFileAtomically(
    JNIEnv* env,
    const JavaParamRef<jstring>& file_name,
    const JavaParamRef<jbyteArray>& data) {
  // This is called on the UI thread during shutdown to save tab data, so
  // needs to enable IO.
  base::ThreadRestrictions::ScopedAllowIO allow_io;
  std::string native_file_name;
  base::android::ConvertJavaStringToUTF8(env, file_name, &native_file_name);
  base::FilePath path(native_file_name);
  std::string native_data_string;
  JavaByteArrayToString(env, data, &native_data_string);
  bool result = base::ImportantFileWriter::WriteFileAtomically(
      path, native_data_string);
  return result;
}

}  // namespace android
}  // namespace base

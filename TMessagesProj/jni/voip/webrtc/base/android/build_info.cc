// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/build_info.h"

#include <string>

#include "base/android/jni_android.h"
#include "base/android/jni_array.h"
#include "base/android/scoped_java_ref.h"
#include "base/base_jni_headers/BuildInfo_jni.h"
#include "base/logging.h"
#include "base/memory/singleton.h"
#include "base/strings/string_number_conversions.h"

namespace base {
namespace android {

namespace {

// We are leaking these strings.
const char* StrDupParam(const std::vector<std::string>& params, int index) {
  return strdup(params[index].c_str());
}

int GetIntParam(const std::vector<std::string>& params, int index) {
  int ret = 0;
  bool success = StringToInt(params[index], &ret);
  DCHECK(success);
  return ret;
}

}  // namespace

struct BuildInfoSingletonTraits {
  static BuildInfo* New() {
    JNIEnv* env = AttachCurrentThread();
    ScopedJavaLocalRef<jobjectArray> params_objs = Java_BuildInfo_getAll(env);
    std::vector<std::string> params;
    AppendJavaStringArrayToStringVector(env, params_objs, &params);
    return new BuildInfo(params);
  }

  static void Delete(BuildInfo* x) {
    // We're leaking this type, see kRegisterAtExit.
    NOTREACHED();
  }

  static const bool kRegisterAtExit = false;
#if DCHECK_IS_ON()
  static const bool kAllowedToAccessOnNonjoinableThread = true;
#endif
};

BuildInfo::BuildInfo(const std::vector<std::string>& params)
    : brand_(StrDupParam(params, 0)),
      device_(StrDupParam(params, 1)),
      android_build_id_(StrDupParam(params, 2)),
      manufacturer_(StrDupParam(params, 3)),
      model_(StrDupParam(params, 4)),
      sdk_int_(GetIntParam(params, 5)),
      build_type_(StrDupParam(params, 6)),
      board_(StrDupParam(params, 7)),
      host_package_name_(StrDupParam(params, 8)),
      host_version_code_(StrDupParam(params, 9)),
      host_package_label_(StrDupParam(params, 10)),
      package_name_(StrDupParam(params, 11)),
      package_version_code_(StrDupParam(params, 12)),
      package_version_name_(StrDupParam(params, 13)),
      android_build_fp_(StrDupParam(params, 14)),
      gms_version_code_(StrDupParam(params, 15)),
      installer_package_name_(StrDupParam(params, 16)),
      abi_name_(StrDupParam(params, 17)),
      firebase_app_id_(StrDupParam(params, 18)),
      custom_themes_(StrDupParam(params, 19)),
      resources_version_(StrDupParam(params, 20)),
      extracted_file_suffix_(params[21]),
      is_at_least_q_(GetIntParam(params, 22)),
      targets_at_least_r_(GetIntParam(params, 23)),
      is_debug_android_(GetIntParam(params, 24)) {}

// static
BuildInfo* BuildInfo::GetInstance() {
  return Singleton<BuildInfo, BuildInfoSingletonTraits >::get();
}

}  // namespace android
}  // namespace base

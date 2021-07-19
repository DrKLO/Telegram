// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/jni_array.h"
#include "base/android/jni_string.h"
#include "base/base_jni_headers/CommandLine_jni.h"
#include "base/command_line.h"
#include "base/logging.h"

using base::android::ConvertUTF8ToJavaString;
using base::android::ConvertJavaStringToUTF8;
using base::android::JavaParamRef;
using base::android::ScopedJavaLocalRef;
using base::CommandLine;

namespace {

void JNI_CommandLine_AppendJavaStringArrayToCommandLine(
    JNIEnv* env,
    const JavaParamRef<jobjectArray>& array,
    bool includes_program) {
  std::vector<std::string> vec;
  if (array)
    base::android::AppendJavaStringArrayToStringVector(env, array, &vec);
  if (!includes_program)
    vec.insert(vec.begin(), std::string());
  CommandLine extra_command_line(vec);
  CommandLine::ForCurrentProcess()->AppendArguments(extra_command_line,
                                                    includes_program);
}

}  // namespace

static jboolean JNI_CommandLine_HasSwitch(
    JNIEnv* env,
    const JavaParamRef<jstring>& jswitch) {
  std::string switch_string(ConvertJavaStringToUTF8(env, jswitch));
  return CommandLine::ForCurrentProcess()->HasSwitch(switch_string);
}

static ScopedJavaLocalRef<jstring> JNI_CommandLine_GetSwitchValue(
    JNIEnv* env,
    const JavaParamRef<jstring>& jswitch) {
  std::string switch_string(ConvertJavaStringToUTF8(env, jswitch));
  std::string value(CommandLine::ForCurrentProcess()->GetSwitchValueNative(
      switch_string));
  if (value.empty())
    return ScopedJavaLocalRef<jstring>();
  return ConvertUTF8ToJavaString(env, value);
}

static ScopedJavaLocalRef<jobjectArray> JNI_CommandLine_GetSwitchesFlattened(
    JNIEnv* env) {
  // JNI doesn't support returning Maps. Instead, express this map as a 1
  // dimensional array: [ key1, value1, key2, value2, ... ]
  std::vector<std::string> keys_and_values;
  for (const auto& entry : CommandLine::ForCurrentProcess()->GetSwitches()) {
    keys_and_values.push_back(entry.first);
    keys_and_values.push_back(entry.second);
  }
  return base::android::ToJavaArrayOfStrings(env, keys_and_values);
}

static void JNI_CommandLine_AppendSwitch(JNIEnv* env,
                                         const JavaParamRef<jstring>& jswitch) {
  std::string switch_string(ConvertJavaStringToUTF8(env, jswitch));
  CommandLine::ForCurrentProcess()->AppendSwitch(switch_string);
}

static void JNI_CommandLine_AppendSwitchWithValue(
    JNIEnv* env,
    const JavaParamRef<jstring>& jswitch,
    const JavaParamRef<jstring>& jvalue) {
  std::string switch_string(ConvertJavaStringToUTF8(env, jswitch));
  std::string value_string(ConvertJavaStringToUTF8(env, jvalue));
  CommandLine::ForCurrentProcess()->AppendSwitchASCII(switch_string,
                                                      value_string);
}

static void JNI_CommandLine_AppendSwitchesAndArguments(
    JNIEnv* env,
    const JavaParamRef<jobjectArray>& array) {
  JNI_CommandLine_AppendJavaStringArrayToCommandLine(env, array, false);
}

static void JNI_CommandLine_RemoveSwitch(JNIEnv* env,
                                         const JavaParamRef<jstring>& jswitch) {
  std::string switch_string(ConvertJavaStringToUTF8(env, jswitch));
  CommandLine::ForCurrentProcess()->RemoveSwitch(switch_string);
}

static void JNI_CommandLine_Init(
    JNIEnv* env,
    const JavaParamRef<jobjectArray>& init_command_line) {
  // TODO(port): Make an overload of Init() that takes StringVector rather than
  // have to round-trip via AppendArguments.
  CommandLine::Init(0, nullptr);
  JNI_CommandLine_AppendJavaStringArrayToCommandLine(env, init_command_line,
                                                     true);
}

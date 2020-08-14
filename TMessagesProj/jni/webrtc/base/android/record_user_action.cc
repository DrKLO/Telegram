// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/jni_string.h"
#include "base/base_jni_headers/RecordUserAction_jni.h"
#include "base/bind.h"
#include "base/callback.h"
#include "base/metrics/user_metrics.h"

namespace {
struct ActionCallbackWrapper {
  base::ActionCallback action_callback;
};
}  // namespace

namespace base {
namespace android {

static void OnActionRecorded(const JavaRef<jobject>& callback,
                             const std::string& action,
                             TimeTicks action_time) {
  JNIEnv* env = AttachCurrentThread();
  Java_UserActionCallback_onActionRecorded(
      env, callback, ConvertUTF8ToJavaString(env, action));
}

static jlong JNI_RecordUserAction_AddActionCallbackForTesting(
    JNIEnv* env,
    const JavaParamRef<jobject>& callback) {
  // Create a wrapper for the ActionCallback, so it can life on the heap until
  // RemoveActionCallbackForTesting() is called.
  auto* wrapper = new ActionCallbackWrapper{base::BindRepeating(
      &OnActionRecorded, ScopedJavaGlobalRef<jobject>(env, callback))};
  base::AddActionCallback(wrapper->action_callback);
  return reinterpret_cast<intptr_t>(wrapper);
}

static void JNI_RecordUserAction_RemoveActionCallbackForTesting(
    JNIEnv* env,
    jlong callback_id) {
  DCHECK(callback_id);
  auto* wrapper = reinterpret_cast<ActionCallbackWrapper*>(callback_id);
  base::RemoveActionCallback(wrapper->action_callback);
  delete wrapper;
}

}  // namespace android
}  // namespace base

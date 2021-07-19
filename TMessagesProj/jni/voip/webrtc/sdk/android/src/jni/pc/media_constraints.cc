/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/media_constraints.h"

#include <memory>

#include "sdk/android/generated_peerconnection_jni/MediaConstraints_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

namespace {

// Helper for translating a List<Pair<String, String>> to a Constraints.
MediaConstraints::Constraints PopulateConstraintsFromJavaPairList(
    JNIEnv* env,
    const JavaRef<jobject>& j_list) {
  MediaConstraints::Constraints constraints;
  for (const JavaRef<jobject>& entry : Iterable(env, j_list)) {
    constraints.emplace_back(
        JavaToStdString(env, Java_KeyValuePair_getKey(env, entry)),
        JavaToStdString(env, Java_KeyValuePair_getValue(env, entry)));
  }
  return constraints;
}

}  // namespace

// Copies all needed data so Java object is no longer needed at return.
std::unique_ptr<MediaConstraints> JavaToNativeMediaConstraints(
    JNIEnv* env,
    const JavaRef<jobject>& j_constraints) {
  return std::make_unique<MediaConstraints>(
      PopulateConstraintsFromJavaPairList(
          env, Java_MediaConstraints_getMandatory(env, j_constraints)),
      PopulateConstraintsFromJavaPairList(
          env, Java_MediaConstraints_getOptional(env, j_constraints)));
}

}  // namespace jni
}  // namespace webrtc

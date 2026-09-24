// Copyright 2012 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "third_party/jni_zero/scoped_java_ref.h"

#include "third_party/jni_zero/core.h"

namespace jni_zero {
namespace {

const int kDefaultLocalFrameCapacity = 16;

}  // namespace

ScopedJavaLocalFrame::ScopedJavaLocalFrame(JNIEnv* env) : env_(env) {
  int failed = env_->PushLocalFrame(kDefaultLocalFrameCapacity);
  JNI_ZERO_DCHECK(!failed);
}

ScopedJavaLocalFrame::ScopedJavaLocalFrame(JNIEnv* env, int capacity)
    : env_(env) {
  int failed = env_->PushLocalFrame(capacity);
  JNI_ZERO_DCHECK(!failed);
}

ScopedJavaLocalFrame::~ScopedJavaLocalFrame() {
  env_->PopLocalFrame(nullptr);
}

#if JNI_ZERO_DCHECK_IS_ON()
// This constructor is inlined when DCHECKs are disabled; don't add anything
// else here.
JavaRef<jobject>::JavaRef(JNIEnv* env, jobject obj) : obj_(obj) {
  if (obj) {
    JNI_ZERO_DCHECK(env && env->GetObjectRefType(obj) == JNILocalRefType);
  }
}
#endif

JNIEnv* JavaRef<jobject>::SetNewLocalRef(JNIEnv* env, jobject obj) {
  if (!env) {
    env = AttachCurrentThread();
  } else {
    JNI_ZERO_DCHECK(env == AttachCurrentThread());  // Is |env| on correct thread.
  }
  if (obj) {
    obj = env->NewLocalRef(obj);
  }
  if (obj_) {
    env->DeleteLocalRef(obj_);
  }
  obj_ = obj;
  return env;
}

void JavaRef<jobject>::SetNewGlobalRef(JNIEnv* env, jobject obj) {
  if (!env) {
    env = AttachCurrentThread();
  } else {
    JNI_ZERO_DCHECK(env == AttachCurrentThread());  // Is |env| on correct thread.
  }
  if (obj) {
    obj = env->NewGlobalRef(obj);
  }
  if (obj_) {
    env->DeleteGlobalRef(obj_);
  }
  obj_ = obj;
}

void JavaRef<jobject>::ResetLocalRef(JNIEnv* env) {
  if (obj_) {
    JNI_ZERO_DCHECK(env == AttachCurrentThread());  // Is |env| on correct thread.
    env->DeleteLocalRef(obj_);
    obj_ = nullptr;
  }
}

void JavaRef<jobject>::ResetGlobalRef() {
  if (obj_) {
    AttachCurrentThread()->DeleteGlobalRef(obj_);
    obj_ = nullptr;
  }
}

jobject JavaRef<jobject>::ReleaseInternal() {
  jobject obj = obj_;
  obj_ = nullptr;
  return obj;
}

}  // namespace jni_zero

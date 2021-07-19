// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/java_exception_reporter.h"

#include "base/android/jni_android.h"
#include "base/android/jni_string.h"
#include "base/android/scoped_java_ref.h"
#include "base/base_jni_headers/JavaExceptionReporter_jni.h"
#include "base/bind.h"
#include "base/callback_forward.h"
#include "base/debug/dump_without_crashing.h"
#include "base/lazy_instance.h"

using base::android::JavaParamRef;
using base::android::JavaRef;

namespace base {
namespace android {

namespace {

void (*g_java_exception_callback)(const char*);

using JavaExceptionFilter =
    base::RepeatingCallback<bool(const JavaRef<jthrowable>&)>;

LazyInstance<JavaExceptionFilter>::Leaky g_java_exception_filter;

}  // namespace

void InitJavaExceptionReporter() {
  JNIEnv* env = base::android::AttachCurrentThread();
  // Since JavaExceptionReporter#installHandler will chain through to the
  // default handler, the default handler should cause a crash as if it's a
  // normal java exception. Prefer to crash the browser process in java rather
  // than native since for webview, the embedding app may have installed its
  // own JavaExceptionReporter handler and would expect it to be called.
  constexpr bool crash_after_report = false;
  SetJavaExceptionFilter(
      base::BindRepeating([](const JavaRef<jthrowable>&) { return true; }));
  Java_JavaExceptionReporter_installHandler(env, crash_after_report);
}

void InitJavaExceptionReporterForChildProcess() {
  JNIEnv* env = base::android::AttachCurrentThread();
  constexpr bool crash_after_report = true;
  SetJavaExceptionFilter(
      base::BindRepeating([](const JavaRef<jthrowable>&) { return true; }));
  Java_JavaExceptionReporter_installHandler(env, crash_after_report);
}

void SetJavaExceptionFilter(JavaExceptionFilter java_exception_filter) {
  g_java_exception_filter.Get() = std::move(java_exception_filter);
}

void SetJavaExceptionCallback(void (*callback)(const char*)) {
  DCHECK(!g_java_exception_callback);
  g_java_exception_callback = callback;
}

void SetJavaException(const char* exception) {
  DCHECK(g_java_exception_callback);
  g_java_exception_callback(exception);
}

void JNI_JavaExceptionReporter_ReportJavaException(
    JNIEnv* env,
    jboolean crash_after_report,
    const JavaParamRef<jthrowable>& e) {
  std::string exception_info = base::android::GetJavaExceptionInfo(env, e);
  bool should_report_exception = g_java_exception_filter.Get().Run(e);
  if (should_report_exception) {
    SetJavaException(exception_info.c_str());
  }
  if (crash_after_report) {
    LOG(ERROR) << exception_info;
    LOG(FATAL) << "Uncaught exception";
  }
  if (should_report_exception) {
    base::debug::DumpWithoutCrashing();
    SetJavaException(nullptr);
  }
}

void JNI_JavaExceptionReporter_ReportJavaStackTrace(
    JNIEnv* env,
    const JavaParamRef<jstring>& stack_trace) {
  SetJavaException(ConvertJavaStringToUTF8(stack_trace).c_str());
  base::debug::DumpWithoutCrashing();
  SetJavaException(nullptr);
}

}  // namespace android
}  // namespace base

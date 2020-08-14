// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ANDROID_JNI_ARRAY_H_
#define BASE_ANDROID_JNI_ARRAY_H_

#include <jni.h>
#include <stddef.h>
#include <stdint.h>
#include <string>
#include <vector>

#include "base/android/scoped_java_ref.h"
#include "base/containers/span.h"
#include "base/strings/string16.h"

namespace base {
namespace android {

// Returns a new Java byte array converted from the given bytes array.
BASE_EXPORT ScopedJavaLocalRef<jbyteArray> ToJavaByteArray(JNIEnv* env,
                                                           const uint8_t* bytes,
                                                           size_t len);

BASE_EXPORT ScopedJavaLocalRef<jbyteArray> ToJavaByteArray(
    JNIEnv* env,
    base::span<const uint8_t> bytes);

// Returns a new Java byte array converted from the given string. No UTF-8
// conversion is performed.
BASE_EXPORT ScopedJavaLocalRef<jbyteArray> ToJavaByteArray(
    JNIEnv* env,
    const std::string& str);

// Returns a new Java boolean array converted from the given bool array.
BASE_EXPORT ScopedJavaLocalRef<jbooleanArray>
ToJavaBooleanArray(JNIEnv* env, const bool* bools, size_t len);

// Returns a new Java int array converted from the given int array.
BASE_EXPORT ScopedJavaLocalRef<jintArray> ToJavaIntArray(
    JNIEnv* env, const int* ints, size_t len);

BASE_EXPORT ScopedJavaLocalRef<jintArray> ToJavaIntArray(
    JNIEnv* env,
    base::span<const int> ints);

// Returns a new Java long array converted from the given int64_t array.
BASE_EXPORT ScopedJavaLocalRef<jlongArray> ToJavaLongArray(JNIEnv* env,
                                                           const int64_t* longs,
                                                           size_t len);

BASE_EXPORT ScopedJavaLocalRef<jlongArray> ToJavaLongArray(
    JNIEnv* env,
    base::span<const int64_t> longs);

// Returns a new Java float array converted from the given C++ float array.
BASE_EXPORT ScopedJavaLocalRef<jfloatArray> ToJavaFloatArray(
    JNIEnv* env, const float* floats, size_t len);

BASE_EXPORT ScopedJavaLocalRef<jfloatArray> ToJavaFloatArray(
    JNIEnv* env,
    base::span<const float> floats);

// Returns a new Java double array converted from the given C++ double array.
BASE_EXPORT ScopedJavaLocalRef<jdoubleArray>
ToJavaDoubleArray(JNIEnv* env, const double* doubles, size_t len);

BASE_EXPORT ScopedJavaLocalRef<jdoubleArray> ToJavaDoubleArray(
    JNIEnv* env,
    base::span<const double> doubles);

// Returns a array of Java byte array converted from |v|.
BASE_EXPORT ScopedJavaLocalRef<jobjectArray> ToJavaArrayOfByteArray(
    JNIEnv* env,
    base::span<const std::string> v);

BASE_EXPORT ScopedJavaLocalRef<jobjectArray> ToJavaArrayOfByteArray(
    JNIEnv* env,
    base::span<std::vector<uint8_t>> v);

BASE_EXPORT ScopedJavaLocalRef<jobjectArray> ToJavaArrayOfStrings(
    JNIEnv* env,
    base::span<const std::string> v);

BASE_EXPORT ScopedJavaLocalRef<jobjectArray> ToJavaArrayOfStrings(
    JNIEnv* env,
    base::span<const string16> v);

BASE_EXPORT ScopedJavaLocalRef<jobjectArray> ToJavaArrayOfStringArray(
    JNIEnv* env,
    base::span<const std::vector<string16>> v);

// Converts a Java string array to a native array.
BASE_EXPORT void AppendJavaStringArrayToStringVector(
    JNIEnv* env,
    const JavaRef<jobjectArray>& array,
    std::vector<string16>* out);

BASE_EXPORT void AppendJavaStringArrayToStringVector(
    JNIEnv* env,
    const JavaRef<jobjectArray>& array,
    std::vector<std::string>* out);

// Appends the Java bytes in |bytes_array| onto the end of |out|.
BASE_EXPORT void AppendJavaByteArrayToByteVector(
    JNIEnv* env,
    const JavaRef<jbyteArray>& byte_array,
    std::vector<uint8_t>* out);

// Replaces the content of |out| with the Java bytes in |byte_array|.
BASE_EXPORT void JavaByteArrayToByteVector(
    JNIEnv* env,
    const JavaRef<jbyteArray>& byte_array,
    std::vector<uint8_t>* out);

// Replaces the content of |out| with the Java bytes in |byte_array|. No UTF-8
// conversion is performed.
BASE_EXPORT void JavaByteArrayToString(JNIEnv* env,
                                       const JavaRef<jbyteArray>& byte_array,
                                       std::string* out);

// Replaces the content of |out| with the Java booleans in |boolean_array|.
BASE_EXPORT void JavaBooleanArrayToBoolVector(
    JNIEnv* env,
    const JavaRef<jbooleanArray>& boolean_array,
    std::vector<bool>* out);

// Replaces the content of |out| with the Java ints in |int_array|.
BASE_EXPORT void JavaIntArrayToIntVector(JNIEnv* env,
                                         const JavaRef<jintArray>& int_array,
                                         std::vector<int>* out);

// Replaces the content of |out| with the Java longs in |long_array|.
BASE_EXPORT void JavaLongArrayToInt64Vector(
    JNIEnv* env,
    const JavaRef<jlongArray>& long_array,
    std::vector<int64_t>* out);

// Replaces the content of |out| with the Java longs in |long_array|.
BASE_EXPORT void JavaLongArrayToLongVector(
    JNIEnv* env,
    const JavaRef<jlongArray>& long_array,
    std::vector<jlong>* out);

// Replaces the content of |out| with the Java floats in |float_array|.
BASE_EXPORT void JavaFloatArrayToFloatVector(
    JNIEnv* env,
    const JavaRef<jfloatArray>& float_array,
    std::vector<float>* out);

// Assuming |array| is an byte[][] (array of byte arrays), replaces the
// content of |out| with the corresponding vector of strings. No UTF-8
// conversion is performed.
BASE_EXPORT void JavaArrayOfByteArrayToStringVector(
    JNIEnv* env,
    const JavaRef<jobjectArray>& array,
    std::vector<std::string>* out);

// Assuming |array| is an byte[][] (array of byte arrays), replaces the
// content of |out| with the corresponding vector of vector of uint8. No UTF-8
// conversion is performed.
BASE_EXPORT void JavaArrayOfByteArrayToBytesVector(
    JNIEnv* env,
    const JavaRef<jobjectArray>& array,
    std::vector<std::vector<uint8_t>>* out);

// Assuming |array| is an String[][] (array of String arrays), replaces the
// content of |out| with the corresponding vector of string vectors. No UTF-8
// conversion is performed.
BASE_EXPORT void Java2dStringArrayTo2dStringVector(
    JNIEnv* env,
    const JavaRef<jobjectArray>& array,
    std::vector<std::vector<string16>>* out);

// Assuming |array| is an int[][] (array of int arrays), replaces the
// contents of |out| with the corresponding vectors of ints.
BASE_EXPORT void JavaArrayOfIntArrayToIntVector(
    JNIEnv* env,
    const JavaRef<jobjectArray>& array,
    std::vector<std::vector<int>>* out);

}  // namespace android
}  // namespace base

#endif  // BASE_ANDROID_JNI_ARRAY_H_

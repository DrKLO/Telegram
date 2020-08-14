/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/generated_video_jni/JavaI420Buffer_jni.h"
#include "third_party/libyuv/include/libyuv/scale.h"

namespace webrtc {
namespace jni {

static void JNI_JavaI420Buffer_CropAndScaleI420(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_src_y,
    jint src_stride_y,
    const JavaParamRef<jobject>& j_src_u,
    jint src_stride_u,
    const JavaParamRef<jobject>& j_src_v,
    jint src_stride_v,
    jint crop_x,
    jint crop_y,
    jint crop_width,
    jint crop_height,
    const JavaParamRef<jobject>& j_dst_y,
    jint dst_stride_y,
    const JavaParamRef<jobject>& j_dst_u,
    jint dst_stride_u,
    const JavaParamRef<jobject>& j_dst_v,
    jint dst_stride_v,
    jint scale_width,
    jint scale_height) {
  uint8_t const* src_y =
      static_cast<uint8_t*>(jni->GetDirectBufferAddress(j_src_y.obj()));
  uint8_t const* src_u =
      static_cast<uint8_t*>(jni->GetDirectBufferAddress(j_src_u.obj()));
  uint8_t const* src_v =
      static_cast<uint8_t*>(jni->GetDirectBufferAddress(j_src_v.obj()));
  uint8_t* dst_y =
      static_cast<uint8_t*>(jni->GetDirectBufferAddress(j_dst_y.obj()));
  uint8_t* dst_u =
      static_cast<uint8_t*>(jni->GetDirectBufferAddress(j_dst_u.obj()));
  uint8_t* dst_v =
      static_cast<uint8_t*>(jni->GetDirectBufferAddress(j_dst_v.obj()));

  // Perform cropping using pointer arithmetic.
  src_y += crop_x + crop_y * src_stride_y;
  src_u += crop_x / 2 + crop_y / 2 * src_stride_u;
  src_v += crop_x / 2 + crop_y / 2 * src_stride_v;

  bool ret = libyuv::I420Scale(
      src_y, src_stride_y, src_u, src_stride_u, src_v, src_stride_v, crop_width,
      crop_height, dst_y, dst_stride_y, dst_u, dst_stride_u, dst_v,
      dst_stride_v, scale_width, scale_height, libyuv::kFilterBox);
  RTC_DCHECK_EQ(ret, 0) << "I420Scale failed";
}

}  // namespace jni
}  // namespace webrtc

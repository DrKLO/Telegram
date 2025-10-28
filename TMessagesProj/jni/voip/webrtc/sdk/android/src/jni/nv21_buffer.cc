/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <jni.h>

#include <vector>

#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "rtc_base/checks.h"
#include "sdk/android/generated_video_jni/NV21Buffer_jni.h"
#include "third_party/libyuv/include/libyuv/convert.h"
#include "third_party/libyuv/include/libyuv/scale.h"

namespace webrtc {
namespace jni {

static void JNI_NV21Buffer_CropAndScale(JNIEnv* jni,
                                        jint crop_x,
                                        jint crop_y,
                                        jint crop_width,
                                        jint crop_height,
                                        jint scale_width,
                                        jint scale_height,
                                        const JavaParamRef<jbyteArray>& j_src,
                                        jint src_width,
                                        jint src_height,
                                        const JavaParamRef<jobject>& j_dst_y,
                                        jint dst_stride_y,
                                        const JavaParamRef<jobject>& j_dst_u,
                                        jint dst_stride_u,
                                        const JavaParamRef<jobject>& j_dst_v,
                                        jint dst_stride_v) {
  const int src_stride_y = src_width;
  const int src_stride_uv = src_width;
  const int crop_chroma_x = crop_x / 2;
  const int crop_chroma_y = crop_y / 2;

  jboolean was_copy;
  jbyte* src_bytes = jni->GetByteArrayElements(j_src.obj(), &was_copy);
  RTC_DCHECK(!was_copy);
  uint8_t const* src_y = reinterpret_cast<uint8_t const*>(src_bytes);
  uint8_t const* src_uv = src_y + src_height * src_stride_y;

  uint8_t* dst_y =
      static_cast<uint8_t*>(jni->GetDirectBufferAddress(j_dst_y.obj()));
  uint8_t* dst_u =
      static_cast<uint8_t*>(jni->GetDirectBufferAddress(j_dst_u.obj()));
  uint8_t* dst_v =
      static_cast<uint8_t*>(jni->GetDirectBufferAddress(j_dst_v.obj()));

  // Crop using pointer arithmetic.
  src_y += crop_x + crop_y * src_stride_y;
  src_uv += 2 * crop_chroma_x + crop_chroma_y * src_stride_uv;

  NV12ToI420Scaler scaler;
  // U- and V-planes are swapped because this is NV21 not NV12.
  scaler.NV12ToI420Scale(src_y, src_stride_y, src_uv, src_stride_uv, crop_width,
                         crop_height, dst_y, dst_stride_y, dst_v, dst_stride_v,
                         dst_u, dst_stride_u, scale_width, scale_height);

  jni->ReleaseByteArrayElements(j_src.obj(), src_bytes, JNI_ABORT);
}

}  // namespace jni
}  // namespace webrtc

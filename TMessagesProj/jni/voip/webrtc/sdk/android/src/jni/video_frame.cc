/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/video_frame.h"

#include <memory>

#include "api/scoped_refptr.h"
#include "common_video/include/video_frame_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/time_utils.h"
#include "sdk/android/generated_video_jni/VideoFrame_jni.h"
#include "sdk/android/src/jni/jni_helpers.h"
#include "sdk/android/src/jni/wrapped_native_i420_buffer.h"

namespace webrtc {
namespace jni {

namespace {

class AndroidVideoI420Buffer : public I420BufferInterface {
 public:
  // Adopts and takes ownership of the Java VideoFrame.Buffer. I.e. retain()
  // will not be called, but release() will be called when the returned
  // AndroidVideoBuffer is destroyed.
  static rtc::scoped_refptr<AndroidVideoI420Buffer> Adopt(
      JNIEnv* jni,
      int width,
      int height,
      const JavaRef<jobject>& j_video_frame_buffer);

 protected:
  // Should not be called directly. Adopts the buffer. Use Adopt() instead for
  // clarity.
  AndroidVideoI420Buffer(JNIEnv* jni,
                         int width,
                         int height,
                         const JavaRef<jobject>& j_video_frame_buffer);
  ~AndroidVideoI420Buffer() override;

 private:
  const uint8_t* DataY() const override { return data_y_; }
  const uint8_t* DataU() const override { return data_u_; }
  const uint8_t* DataV() const override { return data_v_; }

  int StrideY() const override { return stride_y_; }
  int StrideU() const override { return stride_u_; }
  int StrideV() const override { return stride_v_; }

  int width() const override { return width_; }
  int height() const override { return height_; }

  const int width_;
  const int height_;
  // Holds a VideoFrame.I420Buffer.
  const ScopedJavaGlobalRef<jobject> j_video_frame_buffer_;

  const uint8_t* data_y_;
  const uint8_t* data_u_;
  const uint8_t* data_v_;
  int stride_y_;
  int stride_u_;
  int stride_v_;
};

rtc::scoped_refptr<AndroidVideoI420Buffer> AndroidVideoI420Buffer::Adopt(
    JNIEnv* jni,
    int width,
    int height,
    const JavaRef<jobject>& j_video_frame_buffer) {
  return rtc::make_ref_counted<AndroidVideoI420Buffer>(jni, width, height,
                                                       j_video_frame_buffer);
}

AndroidVideoI420Buffer::AndroidVideoI420Buffer(
    JNIEnv* jni,
    int width,
    int height,
    const JavaRef<jobject>& j_video_frame_buffer)
    : width_(width),
      height_(height),
      j_video_frame_buffer_(jni, j_video_frame_buffer) {
  ScopedJavaLocalRef<jobject> j_data_y =
      Java_I420Buffer_getDataY(jni, j_video_frame_buffer);
  ScopedJavaLocalRef<jobject> j_data_u =
      Java_I420Buffer_getDataU(jni, j_video_frame_buffer);
  ScopedJavaLocalRef<jobject> j_data_v =
      Java_I420Buffer_getDataV(jni, j_video_frame_buffer);

  data_y_ =
      static_cast<const uint8_t*>(jni->GetDirectBufferAddress(j_data_y.obj()));
  data_u_ =
      static_cast<const uint8_t*>(jni->GetDirectBufferAddress(j_data_u.obj()));
  data_v_ =
      static_cast<const uint8_t*>(jni->GetDirectBufferAddress(j_data_v.obj()));

  stride_y_ = Java_I420Buffer_getStrideY(jni, j_video_frame_buffer);
  stride_u_ = Java_I420Buffer_getStrideU(jni, j_video_frame_buffer);
  stride_v_ = Java_I420Buffer_getStrideV(jni, j_video_frame_buffer);
}

AndroidVideoI420Buffer::~AndroidVideoI420Buffer() {
  JNIEnv* jni = AttachCurrentThreadIfNeeded();
  Java_Buffer_release(jni, j_video_frame_buffer_);
}

}  // namespace

int64_t GetJavaVideoFrameTimestampNs(JNIEnv* jni,
                                     const JavaRef<jobject>& j_video_frame) {
  return Java_VideoFrame_getTimestampNs(jni, j_video_frame);
}

rtc::scoped_refptr<AndroidVideoBuffer> AndroidVideoBuffer::Adopt(
    JNIEnv* jni,
    const JavaRef<jobject>& j_video_frame_buffer) {
  return rtc::make_ref_counted<AndroidVideoBuffer>(jni, j_video_frame_buffer);
}

rtc::scoped_refptr<AndroidVideoBuffer> AndroidVideoBuffer::Create(
    JNIEnv* jni,
    const JavaRef<jobject>& j_video_frame_buffer) {
  Java_Buffer_retain(jni, j_video_frame_buffer);
  return Adopt(jni, j_video_frame_buffer);
}

AndroidVideoBuffer::AndroidVideoBuffer(
    JNIEnv* jni,
    const JavaRef<jobject>& j_video_frame_buffer)
    : width_(Java_Buffer_getWidth(jni, j_video_frame_buffer)),
      height_(Java_Buffer_getHeight(jni, j_video_frame_buffer)),
      j_video_frame_buffer_(jni, j_video_frame_buffer) {}

AndroidVideoBuffer::~AndroidVideoBuffer() {
  JNIEnv* jni = AttachCurrentThreadIfNeeded();
  Java_Buffer_release(jni, j_video_frame_buffer_);
}

const ScopedJavaGlobalRef<jobject>& AndroidVideoBuffer::video_frame_buffer()
    const {
  return j_video_frame_buffer_;
}

rtc::scoped_refptr<VideoFrameBuffer> AndroidVideoBuffer::CropAndScale(
    int crop_x,
    int crop_y,
    int crop_width,
    int crop_height,
    int scale_width,
    int scale_height) {
  JNIEnv* jni = AttachCurrentThreadIfNeeded();
  return Adopt(jni, Java_Buffer_cropAndScale(jni, j_video_frame_buffer_, crop_x,
                                             crop_y, crop_width, crop_height,
                                             scale_width, scale_height));
}

VideoFrameBuffer::Type AndroidVideoBuffer::type() const {
  return Type::kNative;
}

int AndroidVideoBuffer::width() const {
  return width_;
}

int AndroidVideoBuffer::height() const {
  return height_;
}

rtc::scoped_refptr<I420BufferInterface> AndroidVideoBuffer::ToI420() {
  JNIEnv* jni = AttachCurrentThreadIfNeeded();
  ScopedJavaLocalRef<jobject> j_i420_buffer =
      Java_Buffer_toI420(jni, j_video_frame_buffer_);

  // We don't need to retain the buffer because toI420 returns a new object that
  // we are assumed to take the ownership of.
  return AndroidVideoI420Buffer::Adopt(jni, width_, height_, j_i420_buffer);
}

VideoFrame JavaToNativeFrame(JNIEnv* jni,
                             const JavaRef<jobject>& j_video_frame,
                             uint32_t timestamp_rtp) {
  ScopedJavaLocalRef<jobject> j_video_frame_buffer =
      Java_VideoFrame_getBuffer(jni, j_video_frame);
  int rotation = Java_VideoFrame_getRotation(jni, j_video_frame);
  int64_t timestamp_ns = Java_VideoFrame_getTimestampNs(jni, j_video_frame);
  rtc::scoped_refptr<AndroidVideoBuffer> buffer =
      AndroidVideoBuffer::Create(jni, j_video_frame_buffer);
  return VideoFrame::Builder()
      .set_video_frame_buffer(buffer)
      .set_timestamp_rtp(timestamp_rtp)
      .set_timestamp_ms(timestamp_ns / rtc::kNumNanosecsPerMillisec)
      .set_rotation(static_cast<VideoRotation>(rotation))
      .build();
}

ScopedJavaLocalRef<jobject> NativeToJavaVideoFrame(JNIEnv* jni,
                                                   const VideoFrame& frame) {
  rtc::scoped_refptr<VideoFrameBuffer> buffer = frame.video_frame_buffer();

  if (buffer->type() == VideoFrameBuffer::Type::kNative) {
    AndroidVideoBuffer* android_buffer =
        static_cast<AndroidVideoBuffer*>(buffer.get());
    ScopedJavaLocalRef<jobject> j_video_frame_buffer(
        jni, android_buffer->video_frame_buffer());
    Java_Buffer_retain(jni, j_video_frame_buffer);
    return Java_VideoFrame_Constructor(
        jni, j_video_frame_buffer, static_cast<jint>(frame.rotation()),
        static_cast<jlong>(frame.timestamp_us() *
                           rtc::kNumNanosecsPerMicrosec));
  } else {
    return Java_VideoFrame_Constructor(
        jni, WrapI420Buffer(jni, buffer->ToI420()),
        static_cast<jint>(frame.rotation()),
        static_cast<jlong>(frame.timestamp_us() *
                           rtc::kNumNanosecsPerMicrosec));
  }
}

void ReleaseJavaVideoFrame(JNIEnv* jni, const JavaRef<jobject>& j_video_frame) {
  Java_VideoFrame_release(jni, j_video_frame);
}

}  // namespace jni
}  // namespace webrtc

// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ANDROID_ANDROID_IMAGE_READER_ABI_H_
#define BASE_ANDROID_ANDROID_IMAGE_READER_ABI_H_

// Minimal binary interface definitions for AImage,AImageReader
// and ANativeWindow based on include/media/NdkImage.h,
// include/media/NdkImageReader.h and include/android/native_window_jni.h
// from the Android NDK for platform level 26+. This is only
// intended for use from the AndroidImageReader wrapper for building
// without NDK platform level support, it is not a general-use header
// and is not complete. Only the functions/data types which
// are currently needed by media/gpu/android/image_reader_gl_owner.h are
// included in this ABI
//
// Please refer to the API documentation for details:
// https://developer.android.com/ndk/reference/group/media (AIMage and
// AImageReader)
// https://developer.android.com/ndk/reference/group/native-activity
// (ANativeWindow)

#include <android/native_window.h>
#include <media/NdkMediaError.h>

#include <jni.h>
#include <stdint.h>

// Use "C" linkage to match the original header file. This isn't strictly
// required since the file is not declaring global functions, but the types
// should remain in the global namespace for compatibility, and it's a reminder
// that forward declarations elsewhere should use "extern "C" to avoid
// namespace issues.
extern "C" {

// For AImage
typedef struct AHardwareBuffer AHardwareBuffer;

typedef struct AImage AImage;

typedef struct AImageCropRect {
  int32_t left;
  int32_t top;
  int32_t right;
  int32_t bottom;
} AImageCropRect;

enum AIMAGE_FORMATS {
  AIMAGE_FORMAT_YUV_420_888 = 0x23,
  AIMAGE_FORMAT_PRIVATE = 0x22
};

using pAImage_delete = void (*)(AImage* image);

using pAImage_deleteAsync = void (*)(AImage* image, int releaseFenceFd);

using pAImage_getHardwareBuffer = media_status_t (*)(const AImage* image,
                                                     AHardwareBuffer** buffer);

using pAImage_getWidth = media_status_t (*)(const AImage* image,
                                            int32_t* width);

using pAImage_getHeight = media_status_t (*)(const AImage* image,
                                             int32_t* height);

using pAImage_getCropRect = media_status_t (*)(const AImage* image,
                                               AImageCropRect* rect);

// For AImageReader

typedef struct AImageReader AImageReader;

typedef void (*AImageReader_ImageCallback)(void* context, AImageReader* reader);

typedef struct AImageReader_ImageListener {
  void* context;
  AImageReader_ImageCallback onImageAvailable;
} AImageReader_ImageListener;

using pAImageReader_newWithUsage = media_status_t (*)(int32_t width,
                                                      int32_t height,
                                                      int32_t format,
                                                      uint64_t usage,
                                                      int32_t maxImages,
                                                      AImageReader** reader);

using pAImageReader_setImageListener =
    media_status_t (*)(AImageReader* reader,
                       AImageReader_ImageListener* listener);

using pAImageReader_delete = void (*)(AImageReader* reader);

using pAImageReader_getWindow = media_status_t (*)(AImageReader* reader,
                                                   ANativeWindow** window);

using pAImageReader_getFormat = media_status_t (*)(const AImageReader* reader,
                                                   int32_t* format);

using pAImageReader_acquireLatestImageAsync =
    media_status_t (*)(AImageReader* reader,
                       AImage** image,
                       int* acquireFenceFd);

using pAImageReader_acquireNextImageAsync =
    media_status_t (*)(AImageReader* reader,
                       AImage** image,
                       int* acquireFenceFd);

// For ANativeWindow
using pANativeWindow_toSurface = jobject (*)(JNIEnv* env,
                                             ANativeWindow* window);

}  // extern "C"

#endif  // BASE_ANDROID_ANDROID_IMAGE_READER_ABI_H_

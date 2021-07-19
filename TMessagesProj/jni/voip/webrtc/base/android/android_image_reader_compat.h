// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ANDROID_ANDROID_IMAGE_READER_COMPAT_H_
#define BASE_ANDROID_ANDROID_IMAGE_READER_COMPAT_H_

#include "base/android/android_image_reader_abi.h"
#include "base/base_export.h"
#include "base/macros.h"
#include "base/no_destructor.h"

namespace base {
namespace android {

// This class provides runtime support for working with AImage, AImageReader and
// ANativeWindow objects on Android O systems without requiring building for the
// Android O NDK level. Don't call GetInstance() unless IsSupported() returns
// true.
class BASE_EXPORT AndroidImageReader {
 public:
  // Thread safe GetInstance.
  static AndroidImageReader& GetInstance();

  // Check if the image reader usage is supported. This function returns TRUE
  // if android version is >=OREO, image reader support is not disabled and all
  // the required functions are loaded.
  bool IsSupported();

  // Naming convention of all the below functions are chosen to exactly match
  // the function names in the NDK.
  void AImage_delete(AImage* image);
  void AImage_deleteAsync(AImage* image, int releaseFenceFd);
  media_status_t AImage_getHardwareBuffer(const AImage* image,
                                          AHardwareBuffer** buffer);
  media_status_t AImage_getWidth(const AImage* image, int32_t* width);
  media_status_t AImage_getHeight(const AImage* image, int32_t* height);
  media_status_t AImage_getCropRect(const AImage* image, AImageCropRect* rect);
  media_status_t AImageReader_newWithUsage(int32_t width,
                                           int32_t height,
                                           int32_t format,
                                           uint64_t usage,
                                           int32_t maxImages,
                                           AImageReader** reader);
  media_status_t AImageReader_setImageListener(
      AImageReader* reader,
      AImageReader_ImageListener* listener);
  void AImageReader_delete(AImageReader* reader);
  media_status_t AImageReader_getFormat(const AImageReader* reader,
                                        int32_t* format);
  media_status_t AImageReader_getWindow(AImageReader* reader,
                                        ANativeWindow** window);
  media_status_t AImageReader_acquireLatestImageAsync(AImageReader* reader,
                                                      AImage** image,
                                                      int* acquireFenceFd);
  media_status_t AImageReader_acquireNextImageAsync(AImageReader* reader,
                                                    AImage** image,
                                                    int* acquireFenceFd);
  jobject ANativeWindow_toSurface(JNIEnv* env, ANativeWindow* window);

 private:
  friend class base::NoDestructor<AndroidImageReader>;

  AndroidImageReader();
  bool LoadFunctions();

  bool is_supported_;
  pAImage_delete AImage_delete_;
  pAImage_deleteAsync AImage_deleteAsync_;
  pAImage_getHardwareBuffer AImage_getHardwareBuffer_;
  pAImage_getWidth AImage_getWidth_;
  pAImage_getHeight AImage_getHeight_;
  pAImage_getCropRect AImage_getCropRect_;
  pAImageReader_newWithUsage AImageReader_newWithUsage_;
  pAImageReader_setImageListener AImageReader_setImageListener_;
  pAImageReader_delete AImageReader_delete_;
  pAImageReader_getFormat AImageReader_getFormat_;
  pAImageReader_getWindow AImageReader_getWindow_;
  pAImageReader_acquireLatestImageAsync AImageReader_acquireLatestImageAsync_;
  pAImageReader_acquireNextImageAsync AImageReader_acquireNextImageAsync_;
  pANativeWindow_toSurface ANativeWindow_toSurface_;

  DISALLOW_COPY_AND_ASSIGN(AndroidImageReader);
};

}  // namespace android
}  // namespace base

#endif  // BASE_ANDROID_ANDROID_IMAGE_READER_COMPAT_H_

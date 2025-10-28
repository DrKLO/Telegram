/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/mac/desktop_frame_cgimage.h"

#include <AvailabilityMacros.h>

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

// static
std::unique_ptr<DesktopFrameCGImage> DesktopFrameCGImage::CreateForDisplay(
    CGDirectDisplayID display_id) {
  // Create an image containing a snapshot of the display.
  rtc::ScopedCFTypeRef<CGImageRef> cg_image(CGDisplayCreateImage(display_id));
  if (!cg_image) {
    return nullptr;
  }

  return DesktopFrameCGImage::CreateFromCGImage(cg_image);
}

// static
std::unique_ptr<DesktopFrameCGImage> DesktopFrameCGImage::CreateForWindow(CGWindowID window_id) {
  rtc::ScopedCFTypeRef<CGImageRef> cg_image(
      CGWindowListCreateImage(CGRectNull,
                              kCGWindowListOptionIncludingWindow,
                              window_id,
                              kCGWindowImageBoundsIgnoreFraming));
  if (!cg_image) {
    return nullptr;
  }

  return DesktopFrameCGImage::CreateFromCGImage(cg_image);
}

// static
std::unique_ptr<DesktopFrameCGImage> DesktopFrameCGImage::CreateFromCGImage(
    rtc::ScopedCFTypeRef<CGImageRef> cg_image) {
  // Verify that the image has 32-bit depth.
  int bits_per_pixel = CGImageGetBitsPerPixel(cg_image.get());
  if (bits_per_pixel / 8 != DesktopFrame::kBytesPerPixel) {
    RTC_LOG(LS_ERROR) << "CGDisplayCreateImage() returned imaged with " << bits_per_pixel
                      << " bits per pixel. Only 32-bit depth is supported.";
    return nullptr;
  }

  // Request access to the raw pixel data via the image's DataProvider.
  CGDataProviderRef cg_provider = CGImageGetDataProvider(cg_image.get());
  RTC_DCHECK(cg_provider);

  // CGDataProviderCopyData returns a new data object containing a copy of the provider’s
  // data.
  rtc::ScopedCFTypeRef<CFDataRef> cg_data(CGDataProviderCopyData(cg_provider));
  RTC_DCHECK(cg_data);

  // CFDataGetBytePtr returns a read-only pointer to the bytes of a CFData object.
  uint8_t* data = const_cast<uint8_t*>(CFDataGetBytePtr(cg_data.get()));
  RTC_DCHECK(data);

  DesktopSize size(CGImageGetWidth(cg_image.get()), CGImageGetHeight(cg_image.get()));
  int stride = CGImageGetBytesPerRow(cg_image.get());

  std::unique_ptr<DesktopFrameCGImage> frame(
      new DesktopFrameCGImage(size, stride, data, cg_image, cg_data));

  CGColorSpaceRef cg_color_space = CGImageGetColorSpace(cg_image.get());
  if (cg_color_space) {
#if !defined(MAC_OS_X_VERSION_10_13) || MAC_OS_X_VERSION_MIN_REQUIRED < MAC_OS_X_VERSION_10_13
    rtc::ScopedCFTypeRef<CFDataRef> cf_icc_profile(CGColorSpaceCopyICCProfile(cg_color_space));
#else
    rtc::ScopedCFTypeRef<CFDataRef> cf_icc_profile(CGColorSpaceCopyICCData(cg_color_space));
#endif
    if (cf_icc_profile) {
      const uint8_t* data_as_byte =
          reinterpret_cast<const uint8_t*>(CFDataGetBytePtr(cf_icc_profile.get()));
      const size_t data_size = CFDataGetLength(cf_icc_profile.get());
      if (data_as_byte && data_size > 0) {
        frame->set_icc_profile(std::vector<uint8_t>(data_as_byte, data_as_byte + data_size));
      }
    }
  }

  return frame;
}

DesktopFrameCGImage::DesktopFrameCGImage(DesktopSize size,
                                         int stride,
                                         uint8_t* data,
                                         rtc::ScopedCFTypeRef<CGImageRef> cg_image,
                                         rtc::ScopedCFTypeRef<CFDataRef> cg_data)
    : DesktopFrame(size, stride, data, nullptr), cg_image_(cg_image), cg_data_(cg_data) {
  RTC_DCHECK(cg_image_);
  RTC_DCHECK(cg_data_);
}

DesktopFrameCGImage::~DesktopFrameCGImage() = default;

}  // namespace webrtc

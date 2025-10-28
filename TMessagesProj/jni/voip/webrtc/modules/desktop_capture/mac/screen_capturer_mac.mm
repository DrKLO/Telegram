/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <utility>

#include "modules/desktop_capture/mac/screen_capturer_mac.h"

#include "modules/desktop_capture/mac/desktop_frame_provider.h"
#include "modules/desktop_capture/mac/window_list_utils.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"
#include "sdk/objc/helpers/scoped_cftyperef.h"

namespace webrtc {

namespace {

// Scales all coordinates of a rect by a specified factor.
DesktopRect ScaleAndRoundCGRect(const CGRect& rect, float scale) {
  return DesktopRect::MakeLTRB(static_cast<int>(floor(rect.origin.x * scale)),
                               static_cast<int>(floor(rect.origin.y * scale)),
                               static_cast<int>(ceil((rect.origin.x + rect.size.width) * scale)),
                               static_cast<int>(ceil((rect.origin.y + rect.size.height) * scale)));
}

// Copy pixels in the `rect` from `src_place` to `dest_plane`. `rect` should be
// relative to the origin of `src_plane` and `dest_plane`.
void CopyRect(const uint8_t* src_plane,
              int src_plane_stride,
              uint8_t* dest_plane,
              int dest_plane_stride,
              int bytes_per_pixel,
              const DesktopRect& rect) {
  // Get the address of the starting point.
  const int src_y_offset = src_plane_stride * rect.top();
  const int dest_y_offset = dest_plane_stride * rect.top();
  const int x_offset = bytes_per_pixel * rect.left();
  src_plane += src_y_offset + x_offset;
  dest_plane += dest_y_offset + x_offset;

  // Copy pixels in the rectangle line by line.
  const int bytes_per_line = bytes_per_pixel * rect.width();
  const int height = rect.height();
  for (int i = 0; i < height; ++i) {
    memcpy(dest_plane, src_plane, bytes_per_line);
    src_plane += src_plane_stride;
    dest_plane += dest_plane_stride;
  }
}

// Returns an array of CGWindowID for all the on-screen windows except
// `window_to_exclude`, or NULL if the window is not found or it fails. The
// caller should release the returned CFArrayRef.
CFArrayRef CreateWindowListWithExclusion(CGWindowID window_to_exclude) {
  if (!window_to_exclude) return nullptr;

  CFArrayRef all_windows =
      CGWindowListCopyWindowInfo(kCGWindowListOptionOnScreenOnly, kCGNullWindowID);
  if (!all_windows) return nullptr;

  CFMutableArrayRef returned_array =
      CFArrayCreateMutable(nullptr, CFArrayGetCount(all_windows), nullptr);

  bool found = false;
  for (CFIndex i = 0; i < CFArrayGetCount(all_windows); ++i) {
    CFDictionaryRef window =
        reinterpret_cast<CFDictionaryRef>(CFArrayGetValueAtIndex(all_windows, i));

    CGWindowID id = GetWindowId(window);
    if (id == window_to_exclude) {
      found = true;
      continue;
    }
    CFArrayAppendValue(returned_array, reinterpret_cast<void*>(id));
  }
  CFRelease(all_windows);

  if (!found) {
    CFRelease(returned_array);
    returned_array = nullptr;
  }
  return returned_array;
}

// Returns the bounds of `window` in physical pixels, enlarged by a small amount
// on four edges to take account of the border/shadow effects.
DesktopRect GetExcludedWindowPixelBounds(CGWindowID window, float dip_to_pixel_scale) {
  // The amount of pixels to add to the actual window bounds to take into
  // account of the border/shadow effects.
  static const int kBorderEffectSize = 20;
  CGRect rect;
  CGWindowID ids[1];
  ids[0] = window;

  CFArrayRef window_id_array =
      CFArrayCreate(nullptr, reinterpret_cast<const void**>(&ids), 1, nullptr);
  CFArrayRef window_array = CGWindowListCreateDescriptionFromArray(window_id_array);

  if (CFArrayGetCount(window_array) > 0) {
    CFDictionaryRef win =
        reinterpret_cast<CFDictionaryRef>(CFArrayGetValueAtIndex(window_array, 0));
    CFDictionaryRef bounds_ref =
        reinterpret_cast<CFDictionaryRef>(CFDictionaryGetValue(win, kCGWindowBounds));
    CGRectMakeWithDictionaryRepresentation(bounds_ref, &rect);
  }

  CFRelease(window_id_array);
  CFRelease(window_array);

  rect.origin.x -= kBorderEffectSize;
  rect.origin.y -= kBorderEffectSize;
  rect.size.width += kBorderEffectSize * 2;
  rect.size.height += kBorderEffectSize * 2;
  // `rect` is in DIP, so convert to physical pixels.
  return ScaleAndRoundCGRect(rect, dip_to_pixel_scale);
}

// Create an image of the given region using the given `window_list`.
// `pixel_bounds` should be in the primary display's coordinate in physical
// pixels.
rtc::ScopedCFTypeRef<CGImageRef> CreateExcludedWindowRegionImage(const DesktopRect& pixel_bounds,
                                                                 float dip_to_pixel_scale,
                                                                 CFArrayRef window_list) {
  CGRect window_bounds;
  // The origin is in DIP while the size is in physical pixels. That's what
  // CGWindowListCreateImageFromArray expects.
  window_bounds.origin.x = pixel_bounds.left() / dip_to_pixel_scale;
  window_bounds.origin.y = pixel_bounds.top() / dip_to_pixel_scale;
  window_bounds.size.width = pixel_bounds.width();
  window_bounds.size.height = pixel_bounds.height();

  return rtc::ScopedCFTypeRef<CGImageRef>(
      CGWindowListCreateImageFromArray(window_bounds, window_list, kCGWindowImageDefault));
}

}  // namespace

ScreenCapturerMac::ScreenCapturerMac(
    rtc::scoped_refptr<DesktopConfigurationMonitor> desktop_config_monitor,
    bool detect_updated_region,
    bool allow_iosurface)
    : detect_updated_region_(detect_updated_region),
      desktop_config_monitor_(desktop_config_monitor),
      desktop_frame_provider_(allow_iosurface) {
  RTC_LOG(LS_INFO) << "Allow IOSurface: " << allow_iosurface;
  thread_checker_.Detach();
}

ScreenCapturerMac::~ScreenCapturerMac() {
  RTC_DCHECK(thread_checker_.IsCurrent());
  ReleaseBuffers();
  UnregisterRefreshAndMoveHandlers();
}

bool ScreenCapturerMac::Init() {
  TRACE_EVENT0("webrtc", "ScreenCapturerMac::Init");
  desktop_config_ = desktop_config_monitor_->desktop_configuration();
  return true;
}

void ScreenCapturerMac::ReleaseBuffers() {
  // The buffers might be in use by the encoder, so don't delete them here.
  // Instead, mark them as "needs update"; next time the buffers are used by
  // the capturer, they will be recreated if necessary.
  queue_.Reset();
}

void ScreenCapturerMac::Start(Callback* callback) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(!callback_);
  RTC_DCHECK(callback);
  TRACE_EVENT_INSTANT1(
      "webrtc", "ScreenCapturermac::Start", "target display id ", current_display_);

  callback_ = callback;
  // Start and operate CGDisplayStream handler all from capture thread.
  if (!RegisterRefreshAndMoveHandlers()) {
    RTC_LOG(LS_ERROR) << "Failed to register refresh and move handlers.";
    callback_->OnCaptureResult(Result::ERROR_PERMANENT, nullptr);
    return;
  }
  ScreenConfigurationChanged();
}

void ScreenCapturerMac::CaptureFrame() {
  RTC_DCHECK(thread_checker_.IsCurrent());
  TRACE_EVENT0("webrtc", "creenCapturerMac::CaptureFrame");
  int64_t capture_start_time_nanos = rtc::TimeNanos();

  queue_.MoveToNextFrame();
  if (queue_.current_frame() && queue_.current_frame()->IsShared()) {
    RTC_DLOG(LS_WARNING) << "Overwriting frame that is still shared.";
  }

  MacDesktopConfiguration new_config = desktop_config_monitor_->desktop_configuration();
  if (!desktop_config_.Equals(new_config)) {
    desktop_config_ = new_config;
    // If the display configuraiton has changed then refresh capturer data
    // structures. Occasionally, the refresh and move handlers are lost when
    // the screen mode changes, so re-register them here.
    UnregisterRefreshAndMoveHandlers();
    if (!RegisterRefreshAndMoveHandlers()) {
      RTC_LOG(LS_ERROR) << "Failed to register refresh and move handlers.";
      callback_->OnCaptureResult(Result::ERROR_PERMANENT, nullptr);
      return;
    }
    ScreenConfigurationChanged();
  }

  // When screen is zoomed in/out, OSX only updates the part of Rects currently
  // displayed on screen, with relative location to current top-left on screen.
  // This will cause problems when we copy the dirty regions to the captured
  // image. So we invalidate the whole screen to copy all the screen contents.
  // With CGI method, the zooming will be ignored and the whole screen contents
  // will be captured as before.
  // With IOSurface method, the zoomed screen contents will be captured.
  if (UAZoomEnabled()) {
    helper_.InvalidateScreen(screen_pixel_bounds_.size());
  }

  DesktopRegion region;
  helper_.TakeInvalidRegion(&region);

  // If the current buffer is from an older generation then allocate a new one.
  // Note that we can't reallocate other buffers at this point, since the caller
  // may still be reading from them.
  if (!queue_.current_frame()) queue_.ReplaceCurrentFrame(SharedDesktopFrame::Wrap(CreateFrame()));

  DesktopFrame* current_frame = queue_.current_frame();

  if (!CgBlit(*current_frame, region)) {
    callback_->OnCaptureResult(Result::ERROR_PERMANENT, nullptr);
    return;
  }
  std::unique_ptr<DesktopFrame> new_frame = queue_.current_frame()->Share();
  if (detect_updated_region_) {
    *new_frame->mutable_updated_region() = region;
  } else {
    new_frame->mutable_updated_region()->AddRect(DesktopRect::MakeSize(new_frame->size()));
  }

  if (current_display_) {
    const MacDisplayConfiguration* config =
        desktop_config_.FindDisplayConfigurationById(current_display_);
    if (config) {
      new_frame->set_top_left(
          config->bounds.top_left().subtract(desktop_config_.bounds.top_left()));
    }
  }

  helper_.set_size_most_recent(new_frame->size());

  new_frame->set_capture_time_ms((rtc::TimeNanos() - capture_start_time_nanos) /
                                 rtc::kNumNanosecsPerMillisec);
  callback_->OnCaptureResult(Result::SUCCESS, std::move(new_frame));
}

void ScreenCapturerMac::SetExcludedWindow(WindowId window) {
  excluded_window_ = window;
}

bool ScreenCapturerMac::GetSourceList(SourceList* screens) {
  RTC_DCHECK(screens->size() == 0);

  for (MacDisplayConfigurations::iterator it = desktop_config_.displays.begin();
       it != desktop_config_.displays.end();
       ++it) {
    screens->push_back({it->id, std::string()});
  }
  return true;
}

bool ScreenCapturerMac::SelectSource(SourceId id) {
  if (id == kFullDesktopScreenId) {
    current_display_ = 0;
  } else {
    const MacDisplayConfiguration* config =
        desktop_config_.FindDisplayConfigurationById(static_cast<CGDirectDisplayID>(id));
    if (!config) return false;
    current_display_ = config->id;
  }

  ScreenConfigurationChanged();
  return true;
}

bool ScreenCapturerMac::CgBlit(const DesktopFrame& frame, const DesktopRegion& region) {
  // If not all screen region is dirty, copy the entire contents of the previous capture buffer,
  // to capture over.
  if (queue_.previous_frame() && !region.Equals(DesktopRegion(screen_pixel_bounds_))) {
    memcpy(frame.data(), queue_.previous_frame()->data(), frame.stride() * frame.size().height());
  }

  MacDisplayConfigurations displays_to_capture;
  if (current_display_) {
    // Capturing a single screen. Note that the screen id may change when
    // screens are added or removed.
    const MacDisplayConfiguration* config =
        desktop_config_.FindDisplayConfigurationById(current_display_);
    if (config) {
      displays_to_capture.push_back(*config);
    } else {
      RTC_LOG(LS_ERROR) << "The selected screen cannot be found for capturing.";
      return false;
    }
  } else {
    // Capturing the whole desktop.
    displays_to_capture = desktop_config_.displays;
  }

  // Create the window list once for all displays.
  CFArrayRef window_list = CreateWindowListWithExclusion(excluded_window_);

  for (size_t i = 0; i < displays_to_capture.size(); ++i) {
    const MacDisplayConfiguration& display_config = displays_to_capture[i];

    // Capturing mixed-DPI on one surface is hard, so we only return displays
    // that match the "primary" display's DPI. The primary display is always
    // the first in the list.
    if (i > 0 && display_config.dip_to_pixel_scale != displays_to_capture[0].dip_to_pixel_scale) {
      continue;
    }
    // Determine the display's position relative to the desktop, in pixels.
    DesktopRect display_bounds = display_config.pixel_bounds;
    display_bounds.Translate(-screen_pixel_bounds_.left(), -screen_pixel_bounds_.top());

    // Determine which parts of the blit region, if any, lay within the monitor.
    DesktopRegion copy_region = region;
    copy_region.IntersectWith(display_bounds);
    if (copy_region.is_empty()) continue;

    // Translate the region to be copied into display-relative coordinates.
    copy_region.Translate(-display_bounds.left(), -display_bounds.top());

    DesktopRect excluded_window_bounds;
    rtc::ScopedCFTypeRef<CGImageRef> excluded_image;
    if (excluded_window_ && window_list) {
      // Get the region of the excluded window relative the primary display.
      excluded_window_bounds =
          GetExcludedWindowPixelBounds(excluded_window_, display_config.dip_to_pixel_scale);
      excluded_window_bounds.IntersectWith(display_config.pixel_bounds);

      // Create the image under the excluded window first, because it's faster
      // than captuing the whole display.
      if (!excluded_window_bounds.is_empty()) {
        excluded_image = CreateExcludedWindowRegionImage(
            excluded_window_bounds, display_config.dip_to_pixel_scale, window_list);
      }
    }

    std::unique_ptr<DesktopFrame> frame_source =
        desktop_frame_provider_.TakeLatestFrameForDisplay(display_config.id);
    if (!frame_source) {
      continue;
    }

    const uint8_t* display_base_address = frame_source->data();
    int src_bytes_per_row = frame_source->stride();
    RTC_DCHECK(display_base_address);

    // `frame_source` size may be different from display_bounds in case the screen was
    // resized recently.
    copy_region.IntersectWith(frame_source->rect());

    // Copy the dirty region from the display buffer into our desktop buffer.
    uint8_t* out_ptr = frame.GetFrameDataAtPos(display_bounds.top_left());
    for (DesktopRegion::Iterator it(copy_region); !it.IsAtEnd(); it.Advance()) {
      CopyRect(display_base_address,
               src_bytes_per_row,
               out_ptr,
               frame.stride(),
               DesktopFrame::kBytesPerPixel,
               it.rect());
    }

    if (excluded_image) {
      CGDataProviderRef provider = CGImageGetDataProvider(excluded_image.get());
      rtc::ScopedCFTypeRef<CFDataRef> excluded_image_data(CGDataProviderCopyData(provider));
      RTC_DCHECK(excluded_image_data);
      display_base_address = CFDataGetBytePtr(excluded_image_data.get());
      src_bytes_per_row = CGImageGetBytesPerRow(excluded_image.get());

      // Translate the bounds relative to the desktop, because `frame` data
      // starts from the desktop top-left corner.
      DesktopRect window_bounds_relative_to_desktop(excluded_window_bounds);
      window_bounds_relative_to_desktop.Translate(-screen_pixel_bounds_.left(),
                                                  -screen_pixel_bounds_.top());

      DesktopRect rect_to_copy = DesktopRect::MakeSize(excluded_window_bounds.size());
      rect_to_copy.IntersectWith(DesktopRect::MakeWH(CGImageGetWidth(excluded_image.get()),
                                                     CGImageGetHeight(excluded_image.get())));

      if (CGImageGetBitsPerPixel(excluded_image.get()) / 8 == DesktopFrame::kBytesPerPixel) {
        CopyRect(display_base_address,
                 src_bytes_per_row,
                 frame.GetFrameDataAtPos(window_bounds_relative_to_desktop.top_left()),
                 frame.stride(),
                 DesktopFrame::kBytesPerPixel,
                 rect_to_copy);
      }
    }
  }
  if (window_list) CFRelease(window_list);
  return true;
}

void ScreenCapturerMac::ScreenConfigurationChanged() {
  if (current_display_) {
    const MacDisplayConfiguration* config =
        desktop_config_.FindDisplayConfigurationById(current_display_);
    screen_pixel_bounds_ = config ? config->pixel_bounds : DesktopRect();
    dip_to_pixel_scale_ = config ? config->dip_to_pixel_scale : 1.0f;
  } else {
    screen_pixel_bounds_ = desktop_config_.pixel_bounds;
    dip_to_pixel_scale_ = desktop_config_.dip_to_pixel_scale;
  }

  // Release existing buffers, which will be of the wrong size.
  ReleaseBuffers();

  // Clear the dirty region, in case the display is down-sizing.
  helper_.ClearInvalidRegion();

  // Re-mark the entire desktop as dirty.
  helper_.InvalidateScreen(screen_pixel_bounds_.size());

  // Make sure the frame buffers will be reallocated.
  queue_.Reset();
}

bool ScreenCapturerMac::RegisterRefreshAndMoveHandlers() {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!desktop_frame_provider_.allow_iosurface()) {
    return true;
  }

  desktop_config_ = desktop_config_monitor_->desktop_configuration();
  for (const auto& config : desktop_config_.displays) {
    size_t pixel_width = config.pixel_bounds.width();
    size_t pixel_height = config.pixel_bounds.height();
    if (pixel_width == 0 || pixel_height == 0) continue;
    CGDirectDisplayID display_id = config.id;
    DesktopVector display_origin = config.pixel_bounds.top_left();

    CGDisplayStreamFrameAvailableHandler handler = ^(CGDisplayStreamFrameStatus status,
                                                     uint64_t display_time,
                                                     IOSurfaceRef frame_surface,
                                                     CGDisplayStreamUpdateRef updateRef) {
      RTC_DCHECK(thread_checker_.IsCurrent());
      if (status == kCGDisplayStreamFrameStatusStopped) return;

      // Only pay attention to frame updates.
      if (status != kCGDisplayStreamFrameStatusFrameComplete) return;

      size_t count = 0;
      const CGRect* rects =
          CGDisplayStreamUpdateGetRects(updateRef, kCGDisplayStreamUpdateDirtyRects, &count);
      if (count != 0) {
        // According to CGDisplayStream.h, it's safe to call
        // CGDisplayStreamStop() from within the callback.
        ScreenRefresh(display_id, count, rects, display_origin, frame_surface);
      }
    };

    rtc::ScopedCFTypeRef<CFDictionaryRef> properties_dict(
        CFDictionaryCreate(kCFAllocatorDefault,
                           (const void*[]){kCGDisplayStreamShowCursor},
                           (const void*[]){kCFBooleanFalse},
                           1,
                           &kCFTypeDictionaryKeyCallBacks,
                           &kCFTypeDictionaryValueCallBacks));

    CGDisplayStreamRef display_stream = CGDisplayStreamCreate(
        display_id, pixel_width, pixel_height, 'BGRA', properties_dict.get(), handler);

    if (display_stream) {
      CGError error = CGDisplayStreamStart(display_stream);
      if (error != kCGErrorSuccess) return false;

      CFRunLoopSourceRef source = CGDisplayStreamGetRunLoopSource(display_stream);
      CFRunLoopAddSource(CFRunLoopGetCurrent(), source, kCFRunLoopCommonModes);
      display_streams_.push_back(display_stream);
    }
  }

  return true;
}

void ScreenCapturerMac::UnregisterRefreshAndMoveHandlers() {
  RTC_DCHECK(thread_checker_.IsCurrent());

  for (CGDisplayStreamRef stream : display_streams_) {
    CFRunLoopSourceRef source = CGDisplayStreamGetRunLoopSource(stream);
    CFRunLoopRemoveSource(CFRunLoopGetCurrent(), source, kCFRunLoopCommonModes);
    CGDisplayStreamStop(stream);
    CFRelease(stream);
  }
  display_streams_.clear();

  // Release obsolete io surfaces.
  desktop_frame_provider_.Release();
}

void ScreenCapturerMac::ScreenRefresh(CGDirectDisplayID display_id,
                                      CGRectCount count,
                                      const CGRect* rect_array,
                                      DesktopVector display_origin,
                                      IOSurfaceRef io_surface) {
  if (screen_pixel_bounds_.is_empty()) ScreenConfigurationChanged();

  // The refresh rects are in display coordinates. We want to translate to
  // framebuffer coordinates. If a specific display is being captured, then no
  // change is necessary. If all displays are being captured, then we want to
  // translate by the origin of the display.
  DesktopVector translate_vector;
  if (!current_display_) translate_vector = display_origin;

  DesktopRegion region;
  for (CGRectCount i = 0; i < count; ++i) {
    // All rects are already in physical pixel coordinates.
    DesktopRect rect = DesktopRect::MakeXYWH(rect_array[i].origin.x,
                                             rect_array[i].origin.y,
                                             rect_array[i].size.width,
                                             rect_array[i].size.height);

    rect.Translate(translate_vector);

    region.AddRect(rect);
  }
  // Always having the latest iosurface before invalidating a region.
  // See https://bugs.chromium.org/p/webrtc/issues/detail?id=8652 for details.
  desktop_frame_provider_.InvalidateIOSurface(
      display_id, rtc::ScopedCFTypeRef<IOSurfaceRef>(io_surface, rtc::RetainPolicy::RETAIN));
  helper_.InvalidateRegion(region);
}

std::unique_ptr<DesktopFrame> ScreenCapturerMac::CreateFrame() {
  std::unique_ptr<DesktopFrame> frame(new BasicDesktopFrame(screen_pixel_bounds_.size()));
  frame->set_dpi(
      DesktopVector(kStandardDPI * dip_to_pixel_scale_, kStandardDPI * dip_to_pixel_scale_));
  return frame;
}

}  // namespace webrtc

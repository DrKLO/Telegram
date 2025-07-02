/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/mac/window_list_utils.h"

#include <ApplicationServices/ApplicationServices.h>

#include <algorithm>
#include <cmath>
#include <iterator>
#include <limits>
#include <list>
#include <map>
#include <memory>
#include <utility>

#include "rtc_base/checks.h"

static_assert(static_cast<webrtc::WindowId>(kCGNullWindowID) ==
                  webrtc::kNullWindowId,
              "kNullWindowId needs to equal to kCGNullWindowID.");

namespace webrtc {

namespace {

// WindowName of the status indicator dot shown since Monterey in the taskbar.
// Testing on 12.2.1 shows this is independent of system language setting.
const CFStringRef kStatusIndicator = CFSTR("StatusIndicator");
const CFStringRef kStatusIndicatorOwnerName = CFSTR("Window Server");

bool ToUtf8(const CFStringRef str16, std::string* str8) {
  size_t maxlen = CFStringGetMaximumSizeForEncoding(CFStringGetLength(str16),
                                                    kCFStringEncodingUTF8) +
                  1;
  std::unique_ptr<char[]> buffer(new char[maxlen]);
  if (!buffer ||
      !CFStringGetCString(str16, buffer.get(), maxlen, kCFStringEncodingUTF8)) {
    return false;
  }
  str8->assign(buffer.get());
  return true;
}

// Get CFDictionaryRef from `id` and call `on_window` against it. This function
// returns false if native APIs fail, typically it indicates that the `id` does
// not represent a window. `on_window` will not be called if false is returned
// from this function.
bool GetWindowRef(CGWindowID id,
                  rtc::FunctionView<void(CFDictionaryRef)> on_window) {
  RTC_DCHECK(on_window);

  // TODO(zijiehe): `id` is a 32-bit integer, casting it to an array seems not
  // safe enough. Maybe we should create a new
  // const void* arr[] = {
  //   reinterpret_cast<void*>(id) }
  // };
  CFArrayRef window_id_array =
      CFArrayCreate(NULL, reinterpret_cast<const void**>(&id), 1, NULL);
  CFArrayRef window_array =
      CGWindowListCreateDescriptionFromArray(window_id_array);

  bool result = false;
  // TODO(zijiehe): CFArrayGetCount(window_array) should always return 1.
  // Otherwise, we should treat it as failure.
  if (window_array && CFArrayGetCount(window_array)) {
    on_window(reinterpret_cast<CFDictionaryRef>(
        CFArrayGetValueAtIndex(window_array, 0)));
    result = true;
  }

  if (window_array) {
    CFRelease(window_array);
  }
  CFRelease(window_id_array);
  return result;
}

}  // namespace

bool GetWindowList(rtc::FunctionView<bool(CFDictionaryRef)> on_window,
                   bool ignore_minimized,
                   bool only_zero_layer) {
  RTC_DCHECK(on_window);

  // Only get on screen, non-desktop windows.
  // According to
  // https://developer.apple.com/documentation/coregraphics/cgwindowlistoption/1454105-optiononscreenonly
  // , when kCGWindowListOptionOnScreenOnly is used, the order of windows are in
  // decreasing z-order.
  CFArrayRef window_array = CGWindowListCopyWindowInfo(
      kCGWindowListOptionOnScreenOnly | kCGWindowListExcludeDesktopElements,
      kCGNullWindowID);
  if (!window_array)
    return false;

  MacDesktopConfiguration desktop_config = MacDesktopConfiguration::GetCurrent(
      MacDesktopConfiguration::TopLeftOrigin);

  // Check windows to make sure they have an id, title, and use window layer
  // other than 0.
  CFIndex count = CFArrayGetCount(window_array);
  for (CFIndex i = 0; i < count; i++) {
    CFDictionaryRef window = reinterpret_cast<CFDictionaryRef>(
        CFArrayGetValueAtIndex(window_array, i));
    if (!window) {
      continue;
    }

    CFNumberRef window_id = reinterpret_cast<CFNumberRef>(
        CFDictionaryGetValue(window, kCGWindowNumber));
    if (!window_id) {
      continue;
    }

    CFNumberRef window_layer = reinterpret_cast<CFNumberRef>(
        CFDictionaryGetValue(window, kCGWindowLayer));
    if (!window_layer) {
      continue;
    }

    // Skip windows with layer!=0 (menu, dock).
    int layer;
    if (!CFNumberGetValue(window_layer, kCFNumberIntType, &layer)) {
      continue;
    }
    if (only_zero_layer && layer != 0) {
      continue;
    }

    // Skip windows that are minimized and not full screen.
    if (ignore_minimized && !IsWindowOnScreen(window) &&
        !IsWindowFullScreen(desktop_config, window)) {
      continue;
    }

    // If window title is empty, only consider it if it is either on screen or
    // fullscreen.
    CFStringRef window_title = reinterpret_cast<CFStringRef>(
        CFDictionaryGetValue(window, kCGWindowName));
    if (!window_title && !IsWindowOnScreen(window) &&
        !IsWindowFullScreen(desktop_config, window)) {
      continue;
    }

    CFStringRef window_owner_name = reinterpret_cast<CFStringRef>(
        CFDictionaryGetValue(window, kCGWindowOwnerName));
    // Ignore the red dot status indicator shown in the stats bar. Unlike the
    // rest of the system UI it has a window_layer of 0, so was otherwise
    // included. See crbug.com/1297731.
    if (window_title && CFEqual(window_title, kStatusIndicator) &&
        window_owner_name &&
        CFEqual(window_owner_name, kStatusIndicatorOwnerName)) {
      continue;
    }

    if (!on_window(window)) {
      break;
    }
  }

  CFRelease(window_array);
  return true;
}

bool GetWindowList(DesktopCapturer::SourceList* windows,
                   bool ignore_minimized,
                   bool only_zero_layer) {
  // Use a std::list so that iterators are preversed upon insertion and
  // deletion.
  std::list<DesktopCapturer::Source> sources;
  std::map<int, std::list<DesktopCapturer::Source>::const_iterator> pid_itr_map;
  const bool ret = GetWindowList(
      [&sources, &pid_itr_map](CFDictionaryRef window) {
        WindowId window_id = GetWindowId(window);
        if (window_id != kNullWindowId) {
          const std::string title = GetWindowTitle(window);
          const int pid = GetWindowOwnerPid(window);
          // Check if window for the same pid have been already inserted.
          std::map<int,
                   std::list<DesktopCapturer::Source>::const_iterator>::iterator
              itr = pid_itr_map.find(pid);

          // Only consider empty titles if the app has no other window with a
          // proper title.
          if (title.empty()) {
            std::string owner_name = GetWindowOwnerName(window);

            // At this time we do not know if there will be other windows
            // for the same pid unless they have been already inserted, hence
            // the check in the map. Also skip the window if owner name is
            // empty too.
            if (!owner_name.empty() && (itr == pid_itr_map.end())) {
              sources.push_back(DesktopCapturer::Source{window_id, owner_name});
              RTC_DCHECK(!sources.empty());
              // Get an iterator on the last valid element in the source list.
              std::list<DesktopCapturer::Source>::const_iterator last_source =
                  --sources.end();
              pid_itr_map.insert(
                  std::pair<int,
                            std::list<DesktopCapturer::Source>::const_iterator>(
                      pid, last_source));
            }
          } else {
            sources.push_back(DesktopCapturer::Source{window_id, title});
            // Once the window with empty title has been removed no other empty
            // windows are allowed for the same pid.
            if (itr != pid_itr_map.end() && (itr->second != sources.end())) {
              sources.erase(itr->second);
              // sdt::list::end() never changes during the lifetime of that
              // list.
              itr->second = sources.end();
            }
          }
        }
        return true;
      },
      ignore_minimized, only_zero_layer);

  if (!ret)
    return false;

  RTC_DCHECK(windows);
  windows->reserve(windows->size() + sources.size());
  std::copy(std::begin(sources), std::end(sources),
            std::back_inserter(*windows));

  return true;
}

// Returns true if the window is occupying a full screen.
bool IsWindowFullScreen(const MacDesktopConfiguration& desktop_config,
                        CFDictionaryRef window) {
  bool fullscreen = false;
  CFDictionaryRef bounds_ref = reinterpret_cast<CFDictionaryRef>(
      CFDictionaryGetValue(window, kCGWindowBounds));

  CGRect bounds;
  if (bounds_ref &&
      CGRectMakeWithDictionaryRepresentation(bounds_ref, &bounds)) {
    for (MacDisplayConfigurations::const_iterator it =
             desktop_config.displays.begin();
         it != desktop_config.displays.end(); it++) {
      if (it->bounds.equals(
              DesktopRect::MakeXYWH(bounds.origin.x, bounds.origin.y,
                                    bounds.size.width, bounds.size.height))) {
        fullscreen = true;
        break;
      }
    }
  }

  return fullscreen;
}

bool IsWindowFullScreen(const MacDesktopConfiguration& desktop_config,
                        CGWindowID id) {
  bool fullscreen = false;
  GetWindowRef(id, [&](CFDictionaryRef window) {
    fullscreen = IsWindowFullScreen(desktop_config, window);
  });
  return fullscreen;
}

bool IsWindowOnScreen(CFDictionaryRef window) {
  CFBooleanRef on_screen = reinterpret_cast<CFBooleanRef>(
      CFDictionaryGetValue(window, kCGWindowIsOnscreen));
  return on_screen != NULL && CFBooleanGetValue(on_screen);
}

bool IsWindowOnScreen(CGWindowID id) {
  bool on_screen;
  if (GetWindowRef(id, [&on_screen](CFDictionaryRef window) {
        on_screen = IsWindowOnScreen(window);
      })) {
    return on_screen;
  }
  return false;
}

std::string GetWindowTitle(CFDictionaryRef window) {
  CFStringRef title = reinterpret_cast<CFStringRef>(
      CFDictionaryGetValue(window, kCGWindowName));
  std::string result;
  if (title && ToUtf8(title, &result)) {
    return result;
  }

  return std::string();
}

std::string GetWindowTitle(CGWindowID id) {
  std::string title;
  if (GetWindowRef(id, [&title](CFDictionaryRef window) {
        title = GetWindowTitle(window);
      })) {
    return title;
  }
  return std::string();
}

std::string GetWindowOwnerName(CFDictionaryRef window) {
  CFStringRef owner_name = reinterpret_cast<CFStringRef>(
      CFDictionaryGetValue(window, kCGWindowOwnerName));
  std::string result;
  if (owner_name && ToUtf8(owner_name, &result)) {
    return result;
  }
  return std::string();
}

std::string GetWindowOwnerName(CGWindowID id) {
  std::string owner_name;
  if (GetWindowRef(id, [&owner_name](CFDictionaryRef window) {
        owner_name = GetWindowOwnerName(window);
      })) {
    return owner_name;
  }
  return std::string();
}

WindowId GetWindowId(CFDictionaryRef window) {
  CFNumberRef window_id = reinterpret_cast<CFNumberRef>(
      CFDictionaryGetValue(window, kCGWindowNumber));
  if (!window_id) {
    return kNullWindowId;
  }

  // Note: WindowId is 64-bit on 64-bit system, but CGWindowID is always 32-bit.
  // CFNumberGetValue() fills only top 32 bits, so we should use CGWindowID to
  // receive the window id.
  CGWindowID id;
  if (!CFNumberGetValue(window_id, kCFNumberIntType, &id)) {
    return kNullWindowId;
  }

  return id;
}

int GetWindowOwnerPid(CFDictionaryRef window) {
  CFNumberRef window_pid = reinterpret_cast<CFNumberRef>(
      CFDictionaryGetValue(window, kCGWindowOwnerPID));
  if (!window_pid) {
    return 0;
  }

  int pid;
  if (!CFNumberGetValue(window_pid, kCFNumberIntType, &pid)) {
    return 0;
  }

  return pid;
}

int GetWindowOwnerPid(CGWindowID id) {
  int pid;
  if (GetWindowRef(id, [&pid](CFDictionaryRef window) {
        pid = GetWindowOwnerPid(window);
      })) {
    return pid;
  }
  return 0;
}

float GetScaleFactorAtPosition(const MacDesktopConfiguration& desktop_config,
                               DesktopVector position) {
  // Find the dpi to physical pixel scale for the screen where the mouse cursor
  // is.
  for (auto it = desktop_config.displays.begin();
       it != desktop_config.displays.end(); ++it) {
    if (it->bounds.Contains(position)) {
      return it->dip_to_pixel_scale;
    }
  }
  return 1;
}

float GetWindowScaleFactor(CGWindowID id, DesktopSize size) {
  DesktopRect window_bounds = GetWindowBounds(id);
  float scale = 1.0f;

  if (!window_bounds.is_empty() && !size.is_empty()) {
    float scale_x = size.width() / window_bounds.width();
    float scale_y = size.height() / window_bounds.height();
    // Currently the scale in X and Y directions must be same.
    if ((std::fabs(scale_x - scale_y) <=
         std::numeric_limits<float>::epsilon() * std::max(scale_x, scale_y)) &&
        scale_x > 0.0f) {
      scale = scale_x;
    }
  }

  return scale;
}

DesktopRect GetWindowBounds(CFDictionaryRef window) {
  CFDictionaryRef window_bounds = reinterpret_cast<CFDictionaryRef>(
      CFDictionaryGetValue(window, kCGWindowBounds));
  if (!window_bounds) {
    return DesktopRect();
  }

  CGRect gc_window_rect;
  if (!CGRectMakeWithDictionaryRepresentation(window_bounds, &gc_window_rect)) {
    return DesktopRect();
  }

  return DesktopRect::MakeXYWH(gc_window_rect.origin.x, gc_window_rect.origin.y,
                               gc_window_rect.size.width,
                               gc_window_rect.size.height);
}

DesktopRect GetWindowBounds(CGWindowID id) {
  DesktopRect result;
  if (GetWindowRef(id, [&result](CFDictionaryRef window) {
        result = GetWindowBounds(window);
      })) {
    return result;
  }
  return DesktopRect();
}

}  // namespace webrtc

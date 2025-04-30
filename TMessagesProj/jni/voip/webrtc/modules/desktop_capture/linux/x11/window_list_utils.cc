/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/linux/x11/window_list_utils.h"

#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include <string.h>

#include <algorithm>

#include "modules/desktop_capture/linux/x11/x_error_trap.h"
#include "modules/desktop_capture/linux/x11/x_window_property.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

namespace {

class DeferXFree {
 public:
  explicit DeferXFree(void* data) : data_(data) {}
  ~DeferXFree();

 private:
  void* const data_;
};

DeferXFree::~DeferXFree() {
  if (data_)
    XFree(data_);
}

// Iterates through `window` hierarchy to find first visible window, i.e. one
// that has WM_STATE property set to NormalState.
// See http://tronche.com/gui/x/icccm/sec-4.html#s-4.1.3.1 .
::Window GetApplicationWindow(XAtomCache* cache, ::Window window) {
  int32_t state = GetWindowState(cache, window);
  if (state == NormalState) {
    // Window has WM_STATE==NormalState. Return it.
    return window;
  } else if (state == IconicState) {
    // Window is in minimized. Skip it.
    return 0;
  }

  RTC_DCHECK_EQ(state, WithdrawnState);
  // If the window is in WithdrawnState then look at all of its children.
  ::Window root, parent;
  ::Window* children;
  unsigned int num_children;
  if (!XQueryTree(cache->display(), window, &root, &parent, &children,
                  &num_children)) {
    RTC_LOG(LS_ERROR) << "Failed to query for child windows although window"
                         "does not have a valid WM_STATE.";
    return 0;
  }
  ::Window app_window = 0;
  for (unsigned int i = 0; i < num_children; ++i) {
    app_window = GetApplicationWindow(cache, children[i]);
    if (app_window)
      break;
  }

  if (children)
    XFree(children);
  return app_window;
}

// Returns true if the `window` is a desktop element.
bool IsDesktopElement(XAtomCache* cache, ::Window window) {
  RTC_DCHECK(cache);
  if (window == 0)
    return false;

  // First look for _NET_WM_WINDOW_TYPE. The standard
  // (http://standards.freedesktop.org/wm-spec/latest/ar01s05.html#id2760306)
  // says this hint *should* be present on all windows, and we use the existence
  // of _NET_WM_WINDOW_TYPE_NORMAL in the property to indicate a window is not
  // a desktop element (that is, only "normal" windows should be shareable).
  XWindowProperty<uint32_t> window_type(cache->display(), window,
                                        cache->WindowType());
  if (window_type.is_valid() && window_type.size() > 0) {
    uint32_t* end = window_type.data() + window_type.size();
    bool is_normal =
        (end != std::find(window_type.data(), end, cache->WindowTypeNormal()));
    return !is_normal;
  }

  // Fall back on using the hint.
  XClassHint class_hint;
  Status status = XGetClassHint(cache->display(), window, &class_hint);
  if (status == 0) {
    // No hints, assume this is a normal application window.
    return false;
  }

  DeferXFree free_res_name(class_hint.res_name);
  DeferXFree free_res_class(class_hint.res_class);
  return strcmp("gnome-panel", class_hint.res_name) == 0 ||
         strcmp("desktop_window", class_hint.res_name) == 0;
}

}  // namespace

int32_t GetWindowState(XAtomCache* cache, ::Window window) {
  // Get WM_STATE property of the window.
  XWindowProperty<uint32_t> window_state(cache->display(), window,
                                         cache->WmState());

  // WM_STATE is considered to be set to WithdrawnState when it missing.
  return window_state.is_valid() ? *window_state.data() : WithdrawnState;
}

bool GetWindowList(XAtomCache* cache,
                   rtc::FunctionView<bool(::Window)> on_window) {
  RTC_DCHECK(cache);
  RTC_DCHECK(on_window);
  ::Display* const display = cache->display();

  int failed_screens = 0;
  const int num_screens = XScreenCount(display);
  for (int screen = 0; screen < num_screens; screen++) {
    ::Window root_window = XRootWindow(display, screen);
    ::Window parent;
    ::Window* children;
    unsigned int num_children;
    {
      XErrorTrap error_trap(display);
      if (XQueryTree(display, root_window, &root_window, &parent, &children,
                     &num_children) == 0 ||
          error_trap.GetLastErrorAndDisable() != 0) {
        failed_screens++;
        RTC_LOG(LS_ERROR) << "Failed to query for child windows for screen "
                          << screen;
        continue;
      }
    }

    DeferXFree free_children(children);

    for (unsigned int i = 0; i < num_children; i++) {
      // Iterates in reverse order to return windows from front to back.
      ::Window app_window =
          GetApplicationWindow(cache, children[num_children - 1 - i]);
      if (app_window && !IsDesktopElement(cache, app_window)) {
        if (!on_window(app_window)) {
          return true;
        }
      }
    }
  }

  return failed_screens < num_screens;
}

bool GetWindowRect(::Display* display,
                   ::Window window,
                   DesktopRect* rect,
                   XWindowAttributes* attributes /* = nullptr */) {
  XWindowAttributes local_attributes;
  int offset_x;
  int offset_y;
  if (attributes == nullptr) {
    attributes = &local_attributes;
  }

  {
    XErrorTrap error_trap(display);
    if (!XGetWindowAttributes(display, window, attributes) ||
        error_trap.GetLastErrorAndDisable() != 0) {
      return false;
    }
  }
  *rect = DesktopRectFromXAttributes(*attributes);

  {
    XErrorTrap error_trap(display);
    ::Window child;
    if (!XTranslateCoordinates(display, window, attributes->root, -rect->left(),
                               -rect->top(), &offset_x, &offset_y, &child) ||
        error_trap.GetLastErrorAndDisable() != 0) {
      return false;
    }
  }
  rect->Translate(offset_x, offset_y);
  return true;
}

}  // namespace webrtc

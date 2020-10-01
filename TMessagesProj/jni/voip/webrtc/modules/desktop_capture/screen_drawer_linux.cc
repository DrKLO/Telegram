/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <X11/X.h>
#include <X11/Xlib.h>
#include <string.h>

#include <memory>

#include "api/scoped_refptr.h"
#include "modules/desktop_capture/desktop_capture_types.h"
#include "modules/desktop_capture/desktop_geometry.h"
#include "modules/desktop_capture/linux/shared_x_display.h"
#include "modules/desktop_capture/rgba_color.h"
#include "modules/desktop_capture/screen_drawer.h"
#include "modules/desktop_capture/screen_drawer_lock_posix.h"
#include "rtc_base/checks.h"
#include "system_wrappers/include/sleep.h"

namespace webrtc {

namespace {

// A ScreenDrawer implementation for X11.
class ScreenDrawerLinux : public ScreenDrawer {
 public:
  ScreenDrawerLinux();
  ~ScreenDrawerLinux() override;

  // ScreenDrawer interface.
  DesktopRect DrawableRegion() override;
  void DrawRectangle(DesktopRect rect, RgbaColor color) override;
  void Clear() override;
  void WaitForPendingDraws() override;
  bool MayDrawIncompleteShapes() override;
  WindowId window_id() const override;

 private:
  // Bring the window to the front, this can help to avoid the impact from other
  // windows or shadow effect.
  void BringToFront();

  rtc::scoped_refptr<SharedXDisplay> display_;
  int screen_num_;
  DesktopRect rect_;
  Window window_;
  GC context_;
  Colormap colormap_;
};

ScreenDrawerLinux::ScreenDrawerLinux() {
  display_ = SharedXDisplay::CreateDefault();
  RTC_CHECK(display_.get());
  screen_num_ = DefaultScreen(display_->display());
  XWindowAttributes root_attributes;
  if (!XGetWindowAttributes(display_->display(),
                            RootWindow(display_->display(), screen_num_),
                            &root_attributes)) {
    RTC_NOTREACHED() << "Failed to get root window size.";
  }
  window_ = XCreateSimpleWindow(
      display_->display(), RootWindow(display_->display(), screen_num_), 0, 0,
      root_attributes.width, root_attributes.height, 0,
      BlackPixel(display_->display(), screen_num_),
      BlackPixel(display_->display(), screen_num_));
  XSelectInput(display_->display(), window_, StructureNotifyMask);
  XMapWindow(display_->display(), window_);
  while (true) {
    XEvent event;
    XNextEvent(display_->display(), &event);
    if (event.type == MapNotify) {
      break;
    }
  }
  XFlush(display_->display());
  Window child;
  int x, y;
  if (!XTranslateCoordinates(display_->display(), window_,
                             RootWindow(display_->display(), screen_num_), 0, 0,
                             &x, &y, &child)) {
    RTC_NOTREACHED() << "Failed to get window position.";
  }
  // Some window manager does not allow a window to cover two or more monitors.
  // So if the window is on the first monitor of a two-monitor system, the
  // second half won't be able to show up without changing configurations of WM,
  // and its DrawableRegion() is not accurate.
  rect_ = DesktopRect::MakeLTRB(x, y, root_attributes.width,
                                root_attributes.height);
  context_ = DefaultGC(display_->display(), screen_num_);
  colormap_ = DefaultColormap(display_->display(), screen_num_);
  BringToFront();
  // Wait for window animations.
  SleepMs(200);
}

ScreenDrawerLinux::~ScreenDrawerLinux() {
  XUnmapWindow(display_->display(), window_);
  XDestroyWindow(display_->display(), window_);
}

DesktopRect ScreenDrawerLinux::DrawableRegion() {
  return rect_;
}

void ScreenDrawerLinux::DrawRectangle(DesktopRect rect, RgbaColor color) {
  rect.Translate(-rect_.left(), -rect_.top());
  XColor xcolor;
  // X11 does not support Alpha.
  // X11 uses 16 bits for each primary color, so we need to slightly normalize
  // a 8 bits channel to 16 bits channel, by setting the low 8 bits as its high
  // 8 bits to avoid a mismatch of color returned by capturer.
  xcolor.red = (color.red << 8) + color.red;
  xcolor.green = (color.green << 8) + color.green;
  xcolor.blue = (color.blue << 8) + color.blue;
  xcolor.flags = DoRed | DoGreen | DoBlue;
  XAllocColor(display_->display(), colormap_, &xcolor);
  XSetForeground(display_->display(), context_, xcolor.pixel);
  XFillRectangle(display_->display(), window_, context_, rect.left(),
                 rect.top(), rect.width(), rect.height());
  XFlush(display_->display());
}

void ScreenDrawerLinux::Clear() {
  DrawRectangle(rect_, RgbaColor(0, 0, 0));
}

// TODO(zijiehe): Find the right signal from X11 to indicate the finish of all
// pending paintings.
void ScreenDrawerLinux::WaitForPendingDraws() {
  SleepMs(50);
}

bool ScreenDrawerLinux::MayDrawIncompleteShapes() {
  return true;
}

WindowId ScreenDrawerLinux::window_id() const {
  return window_;
}

void ScreenDrawerLinux::BringToFront() {
  Atom state_above = XInternAtom(display_->display(), "_NET_WM_STATE_ABOVE", 1);
  Atom window_state = XInternAtom(display_->display(), "_NET_WM_STATE", 1);
  if (state_above == None || window_state == None) {
    // Fallback to use XRaiseWindow, it's not reliable if two windows are both
    // raise itself to the top.
    XRaiseWindow(display_->display(), window_);
    return;
  }

  XEvent event;
  memset(&event, 0, sizeof(event));
  event.type = ClientMessage;
  event.xclient.window = window_;
  event.xclient.message_type = window_state;
  event.xclient.format = 32;
  event.xclient.data.l[0] = 1;  // _NET_WM_STATE_ADD
  event.xclient.data.l[1] = state_above;
  XSendEvent(display_->display(), RootWindow(display_->display(), screen_num_),
             False, SubstructureRedirectMask | SubstructureNotifyMask, &event);
}

}  // namespace

// static
std::unique_ptr<ScreenDrawerLock> ScreenDrawerLock::Create() {
  return std::make_unique<ScreenDrawerLockPosix>();
}

// static
std::unique_ptr<ScreenDrawer> ScreenDrawer::Create() {
  if (SharedXDisplay::CreateDefault().get()) {
    return std::make_unique<ScreenDrawerLinux>();
  }
  return nullptr;
}

}  // namespace webrtc

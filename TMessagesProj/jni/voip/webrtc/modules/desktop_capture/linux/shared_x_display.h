/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_DESKTOP_CAPTURE_LINUX_SHARED_X_DISPLAY_H_
#define MODULES_DESKTOP_CAPTURE_LINUX_SHARED_X_DISPLAY_H_

#include <map>
#include <string>
#include <vector>

#include "api/ref_counted_base.h"
#include "api/scoped_refptr.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/system/rtc_export.h"

// Including Xlib.h will involve evil defines (Bool, Status, True, False), which
// easily conflict with other headers.
typedef struct _XDisplay Display;
typedef union _XEvent XEvent;

namespace webrtc {

// A ref-counted object to store XDisplay connection.
class RTC_EXPORT SharedXDisplay : public rtc::RefCountedBase {
 public:
  class XEventHandler {
   public:
    virtual ~XEventHandler() {}

    // Processes XEvent. Returns true if the event has been handled.
    virtual bool HandleXEvent(const XEvent& event) = 0;
  };

  // Takes ownership of |display|.
  explicit SharedXDisplay(Display* display);

  // Creates a new X11 Display for the |display_name|. NULL is returned if X11
  // connection failed. Equivalent to CreateDefault() when |display_name| is
  // empty.
  static rtc::scoped_refptr<SharedXDisplay> Create(
      const std::string& display_name);

  // Creates X11 Display connection for the default display (e.g. specified in
  // DISPLAY). NULL is returned if X11 connection failed.
  static rtc::scoped_refptr<SharedXDisplay> CreateDefault();

  Display* display() { return display_; }

  // Adds a new event |handler| for XEvent's of |type|.
  void AddEventHandler(int type, XEventHandler* handler);

  // Removes event |handler| added using |AddEventHandler|. Doesn't do anything
  // if |handler| is not registered.
  void RemoveEventHandler(int type, XEventHandler* handler);

  // Processes pending XEvents, calling corresponding event handlers.
  void ProcessPendingXEvents();

  void IgnoreXServerGrabs();

 protected:
  ~SharedXDisplay() override;

 private:
  typedef std::map<int, std::vector<XEventHandler*> > EventHandlersMap;

  Display* display_;

  EventHandlersMap event_handlers_;

  RTC_DISALLOW_COPY_AND_ASSIGN(SharedXDisplay);
};

}  // namespace webrtc

#endif  // MODULES_DESKTOP_CAPTURE_LINUX_SHARED_X_DISPLAY_H_

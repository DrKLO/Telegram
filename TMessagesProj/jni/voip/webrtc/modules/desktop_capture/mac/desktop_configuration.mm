/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/mac/desktop_configuration.h"

#include <math.h>
#include <algorithm>
#include <Cocoa/Cocoa.h>

#include "rtc_base/checks.h"

#if !defined(MAC_OS_X_VERSION_10_7) || \
    MAC_OS_X_VERSION_MIN_REQUIRED < MAC_OS_X_VERSION_10_7

@interface NSScreen (LionAPI)
- (CGFloat)backingScaleFactor;
- (NSRect)convertRectToBacking:(NSRect)aRect;
@end

#endif  // MAC_OS_X_VERSION_10_7

namespace webrtc {

namespace {

DesktopRect NSRectToDesktopRect(const NSRect& ns_rect) {
  return DesktopRect::MakeLTRB(
      static_cast<int>(floor(ns_rect.origin.x)),
      static_cast<int>(floor(ns_rect.origin.y)),
      static_cast<int>(ceil(ns_rect.origin.x + ns_rect.size.width)),
      static_cast<int>(ceil(ns_rect.origin.y + ns_rect.size.height)));
}

// Inverts the position of `rect` from bottom-up coordinates to top-down,
// relative to `bounds`.
void InvertRectYOrigin(const DesktopRect& bounds,
                       DesktopRect* rect) {
  RTC_DCHECK_EQ(bounds.top(), 0);
  *rect = DesktopRect::MakeXYWH(
      rect->left(), bounds.bottom() - rect->bottom(),
      rect->width(), rect->height());
}

MacDisplayConfiguration GetConfigurationForScreen(NSScreen* screen) {
  MacDisplayConfiguration display_config;

  // Fetch the NSScreenNumber, which is also the CGDirectDisplayID.
  NSDictionary* device_description = [screen deviceDescription];
  display_config.id = static_cast<CGDirectDisplayID>(
      [[device_description objectForKey:@"NSScreenNumber"] intValue]);

  // Determine the display's logical & physical dimensions.
  NSRect ns_bounds = [screen frame];
  display_config.bounds = NSRectToDesktopRect(ns_bounds);

  // If the host is running Mac OS X 10.7+ or later, query the scaling factor
  // between logical and physical (aka "backing") pixels, otherwise assume 1:1.
  if ([screen respondsToSelector:@selector(backingScaleFactor)] &&
      [screen respondsToSelector:@selector(convertRectToBacking:)]) {
    display_config.dip_to_pixel_scale = [screen backingScaleFactor];
    NSRect ns_pixel_bounds = [screen convertRectToBacking: ns_bounds];
    display_config.pixel_bounds = NSRectToDesktopRect(ns_pixel_bounds);
  } else {
    display_config.pixel_bounds = display_config.bounds;
  }

  // Determine if the display is built-in or external.
  display_config.is_builtin = CGDisplayIsBuiltin(display_config.id);

  return display_config;
}

}  // namespace

MacDisplayConfiguration::MacDisplayConfiguration() = default;
MacDisplayConfiguration::MacDisplayConfiguration(
    const MacDisplayConfiguration& other) = default;
MacDisplayConfiguration::MacDisplayConfiguration(
    MacDisplayConfiguration&& other) = default;
MacDisplayConfiguration::~MacDisplayConfiguration() = default;

MacDisplayConfiguration& MacDisplayConfiguration::operator=(
    const MacDisplayConfiguration& other) = default;
MacDisplayConfiguration& MacDisplayConfiguration::operator=(
    MacDisplayConfiguration&& other) = default;

MacDesktopConfiguration::MacDesktopConfiguration() = default;
MacDesktopConfiguration::MacDesktopConfiguration(
    const MacDesktopConfiguration& other) = default;
MacDesktopConfiguration::MacDesktopConfiguration(
    MacDesktopConfiguration&& other) = default;
MacDesktopConfiguration::~MacDesktopConfiguration() = default;

MacDesktopConfiguration& MacDesktopConfiguration::operator=(
    const MacDesktopConfiguration& other) = default;
MacDesktopConfiguration& MacDesktopConfiguration::operator=(
    MacDesktopConfiguration&& other) = default;

// static
MacDesktopConfiguration MacDesktopConfiguration::GetCurrent(Origin origin) {
  MacDesktopConfiguration desktop_config;

  NSArray* screens = [NSScreen screens];
  RTC_DCHECK(screens);

  // Iterator over the monitors, adding the primary monitor and monitors whose
  // DPI match that of the primary monitor.
  for (NSUInteger i = 0; i < [screens count]; ++i) {
    MacDisplayConfiguration display_config =
        GetConfigurationForScreen([screens objectAtIndex: i]);

    if (i == 0)
      desktop_config.dip_to_pixel_scale = display_config.dip_to_pixel_scale;

    // Cocoa uses bottom-up coordinates, so if the caller wants top-down then
    // we need to invert the positions of secondary monitors relative to the
    // primary one (the primary monitor's position is (0,0) in both systems).
    if (i > 0 && origin == TopLeftOrigin) {
      InvertRectYOrigin(desktop_config.displays[0].bounds,
                        &display_config.bounds);
      // `display_bounds` is density dependent, so we need to convert the
      // primay monitor's position into the secondary monitor's density context.
      float scaling_factor = display_config.dip_to_pixel_scale /
          desktop_config.displays[0].dip_to_pixel_scale;
      DesktopRect primary_bounds = DesktopRect::MakeLTRB(
          desktop_config.displays[0].pixel_bounds.left() * scaling_factor,
          desktop_config.displays[0].pixel_bounds.top() * scaling_factor,
          desktop_config.displays[0].pixel_bounds.right() * scaling_factor,
          desktop_config.displays[0].pixel_bounds.bottom() * scaling_factor);
      InvertRectYOrigin(primary_bounds, &display_config.pixel_bounds);
    }

    // Add the display to the configuration.
    desktop_config.displays.push_back(display_config);

    // Update the desktop bounds to account for this display, unless the current
    // display uses different DPI settings.
    if (display_config.dip_to_pixel_scale ==
        desktop_config.dip_to_pixel_scale) {
      desktop_config.bounds.UnionWith(display_config.bounds);
      desktop_config.pixel_bounds.UnionWith(display_config.pixel_bounds);
    }
  }

  return desktop_config;
}

// For convenience of comparing MacDisplayConfigurations in
// MacDesktopConfiguration::Equals.
bool operator==(const MacDisplayConfiguration& left,
                const MacDisplayConfiguration& right) {
  return left.id == right.id &&
      left.bounds.equals(right.bounds) &&
      left.pixel_bounds.equals(right.pixel_bounds) &&
      left.dip_to_pixel_scale == right.dip_to_pixel_scale;
}

bool MacDesktopConfiguration::Equals(const MacDesktopConfiguration& other) {
  return bounds.equals(other.bounds) &&
      pixel_bounds.equals(other.pixel_bounds) &&
      dip_to_pixel_scale == other.dip_to_pixel_scale &&
      displays == other.displays;
}

const MacDisplayConfiguration*
MacDesktopConfiguration::FindDisplayConfigurationById(
    CGDirectDisplayID id) {
  bool is_builtin = CGDisplayIsBuiltin(id);
  for (MacDisplayConfigurations::const_iterator it = displays.begin();
      it != displays.end(); ++it) {
    // The MBP having both discrete and integrated graphic cards will do
    // automate graphics switching by default. When it switches from discrete to
    // integrated one, the current display ID of the built-in display will
    // change and this will cause screen capture stops.
    // So make screen capture of built-in display continuing even if its display
    // ID is changed.
    if ((is_builtin && it->is_builtin) || (!is_builtin && it->id == id)) return &(*it);
  }
  return NULL;
}

}  // namespace webrtc

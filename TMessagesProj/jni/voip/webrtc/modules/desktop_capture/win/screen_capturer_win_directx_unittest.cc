/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/win/screen_capturer_win_directx.h"

#include <string>
#include <vector>

#include "modules/desktop_capture/desktop_capturer.h"
#include "test/gtest.h"

namespace webrtc {

// This test cannot ensure GetScreenListFromDeviceNames() won't reorder the
// devices in its output, since the device name is missing.
TEST(ScreenCaptureUtilsTest, GetScreenListFromDeviceNamesAndGetIndex) {
  const std::vector<std::string> device_names = {
      "\\\\.\\DISPLAY0",
      "\\\\.\\DISPLAY1",
      "\\\\.\\DISPLAY2",
  };
  DesktopCapturer::SourceList screens;
  ASSERT_TRUE(ScreenCapturerWinDirectx::GetScreenListFromDeviceNames(
      device_names, &screens));
  ASSERT_EQ(device_names.size(), screens.size());

  for (size_t i = 0; i < screens.size(); i++) {
    ASSERT_EQ(ScreenCapturerWinDirectx::GetIndexFromScreenId(screens[i].id,
                                                             device_names),
              static_cast<int>(i));
  }
}

}  // namespace webrtc

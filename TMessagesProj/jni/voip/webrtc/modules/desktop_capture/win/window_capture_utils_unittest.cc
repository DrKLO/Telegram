/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/win/window_capture_utils.h"

#include <winuser.h>

#include <algorithm>
#include <memory>
#include <mutex>

#include "modules/desktop_capture/desktop_capturer.h"
#include "modules/desktop_capture/win/test_support/test_window.h"
#include "rtc_base/task_queue_for_test.h"
#include "rtc_base/thread.h"
#include "test/gtest.h"

namespace webrtc {
namespace {

const char kWindowThreadName[] = "window_capture_utils_test_thread";
const WCHAR kWindowTitle[] = L"Window Capture Utils Test";

std::unique_ptr<rtc::Thread> SetUpUnresponsiveWindow(std::mutex& mtx,
                                                     WindowInfo& info) {
  std::unique_ptr<rtc::Thread> window_thread;
  window_thread = rtc::Thread::Create();
  window_thread->SetName(kWindowThreadName, nullptr);
  window_thread->Start();

  SendTask(window_thread.get(), [&] { info = CreateTestWindow(kWindowTitle); });

  // Intentionally create a deadlock to cause the window to become unresponsive.
  mtx.lock();
  window_thread->PostTask([&mtx]() {
    mtx.lock();
    mtx.unlock();
  });

  return window_thread;
}

}  // namespace

TEST(WindowCaptureUtilsTest, GetWindowList) {
  WindowInfo info = CreateTestWindow(kWindowTitle);
  DesktopCapturer::SourceList window_list;
  ASSERT_TRUE(GetWindowList(GetWindowListFlags::kNone, &window_list));
  EXPECT_GT(window_list.size(), 0ULL);
  EXPECT_NE(std::find_if(window_list.begin(), window_list.end(),
                         [&info](DesktopCapturer::Source window) {
                           return reinterpret_cast<HWND>(window.id) ==
                                  info.hwnd;
                         }),
            window_list.end());
  DestroyTestWindow(info);
}

TEST(WindowCaptureUtilsTest, IncludeUnresponsiveWindows) {
  std::mutex mtx;
  WindowInfo info;
  std::unique_ptr<rtc::Thread> window_thread =
      SetUpUnresponsiveWindow(mtx, info);

  EXPECT_FALSE(IsWindowResponding(info.hwnd));

  DesktopCapturer::SourceList window_list;
  ASSERT_TRUE(GetWindowList(GetWindowListFlags::kNone, &window_list));
  EXPECT_GT(window_list.size(), 0ULL);
  EXPECT_NE(std::find_if(window_list.begin(), window_list.end(),
                         [&info](DesktopCapturer::Source window) {
                           return reinterpret_cast<HWND>(window.id) ==
                                  info.hwnd;
                         }),
            window_list.end());

  mtx.unlock();
  SendTask(window_thread.get(), [&info]() { DestroyTestWindow(info); });
  window_thread->Stop();
}

TEST(WindowCaptureUtilsTest, IgnoreUnresponsiveWindows) {
  std::mutex mtx;
  WindowInfo info;
  std::unique_ptr<rtc::Thread> window_thread =
      SetUpUnresponsiveWindow(mtx, info);

  EXPECT_FALSE(IsWindowResponding(info.hwnd));

  DesktopCapturer::SourceList window_list;
  ASSERT_TRUE(
      GetWindowList(GetWindowListFlags::kIgnoreUnresponsive, &window_list));
  EXPECT_EQ(std::find_if(window_list.begin(), window_list.end(),
                         [&info](DesktopCapturer::Source window) {
                           return reinterpret_cast<HWND>(window.id) ==
                                  info.hwnd;
                         }),
            window_list.end());

  mtx.unlock();
  SendTask(window_thread.get(), [&info]() { DestroyTestWindow(info); });
  window_thread->Stop();
}

TEST(WindowCaptureUtilsTest, IncludeUntitledWindows) {
  WindowInfo info = CreateTestWindow(L"");
  DesktopCapturer::SourceList window_list;
  ASSERT_TRUE(GetWindowList(GetWindowListFlags::kNone, &window_list));
  EXPECT_GT(window_list.size(), 0ULL);
  EXPECT_NE(std::find_if(window_list.begin(), window_list.end(),
                         [&info](DesktopCapturer::Source window) {
                           return reinterpret_cast<HWND>(window.id) ==
                                  info.hwnd;
                         }),
            window_list.end());
  DestroyTestWindow(info);
}

TEST(WindowCaptureUtilsTest, IgnoreUntitledWindows) {
  WindowInfo info = CreateTestWindow(L"");
  DesktopCapturer::SourceList window_list;
  ASSERT_TRUE(GetWindowList(GetWindowListFlags::kIgnoreUntitled, &window_list));
  EXPECT_EQ(std::find_if(window_list.begin(), window_list.end(),
                         [&info](DesktopCapturer::Source window) {
                           return reinterpret_cast<HWND>(window.id) ==
                                  info.hwnd;
                         }),
            window_list.end());
  DestroyTestWindow(info);
}

TEST(WindowCaptureUtilsTest, IgnoreCurrentProcessWindows) {
  WindowInfo info = CreateTestWindow(kWindowTitle);
  DesktopCapturer::SourceList window_list;
  ASSERT_TRUE(GetWindowList(GetWindowListFlags::kIgnoreCurrentProcessWindows,
                            &window_list));
  EXPECT_EQ(std::find_if(window_list.begin(), window_list.end(),
                         [&info](DesktopCapturer::Source window) {
                           return reinterpret_cast<HWND>(window.id) ==
                                  info.hwnd;
                         }),
            window_list.end());
  DestroyTestWindow(info);
}

}  // namespace webrtc

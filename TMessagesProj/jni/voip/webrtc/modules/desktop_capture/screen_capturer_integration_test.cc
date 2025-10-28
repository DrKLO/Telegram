/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <string.h>

#include <algorithm>
#include <initializer_list>
#include <iostream>  // TODO(zijiehe): Remove once flaky has been resolved.
#include <memory>
#include <utility>

// TODO(zijiehe): Remove once flaky has been resolved.
#include "modules/desktop_capture/desktop_capture_options.h"
#include "modules/desktop_capture/desktop_capturer.h"
#include "modules/desktop_capture/desktop_frame.h"
#include "modules/desktop_capture/desktop_region.h"
#include "modules/desktop_capture/mock_desktop_capturer_callback.h"
#include "modules/desktop_capture/rgba_color.h"
#include "modules/desktop_capture/screen_drawer.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/third_party/base64/base64.h"
#include "test/gmock.h"
#include "test/gtest.h"

#if defined(WEBRTC_WIN)
#include "modules/desktop_capture/win/screen_capturer_win_directx.h"
#include "rtc_base/win/windows_version.h"
#endif  // defined(WEBRTC_WIN)

using ::testing::_;

namespace webrtc {

namespace {

ACTION_P2(SaveCaptureResult, result, dest) {
  *result = arg0;
  *dest = std::move(*arg1);
}

// Returns true if color in `rect` of `frame` is `color`.
bool ArePixelsColoredBy(const DesktopFrame& frame,
                        DesktopRect rect,
                        RgbaColor color,
                        bool may_partially_draw) {
  if (!may_partially_draw) {
    // updated_region() should cover the painted area.
    DesktopRegion updated_region(frame.updated_region());
    updated_region.IntersectWith(rect);
    if (!updated_region.Equals(DesktopRegion(rect))) {
      return false;
    }
  }

  // Color in the `rect` should be `color`.
  uint8_t* row = frame.GetFrameDataAtPos(rect.top_left());
  for (int i = 0; i < rect.height(); i++) {
    uint8_t* column = row;
    for (int j = 0; j < rect.width(); j++) {
      if (color != RgbaColor(column)) {
        return false;
      }
      column += DesktopFrame::kBytesPerPixel;
    }
    row += frame.stride();
  }
  return true;
}

}  // namespace

class ScreenCapturerIntegrationTest : public ::testing::Test {
 public:
  void SetUp() override {
    capturer_ = DesktopCapturer::CreateScreenCapturer(
        DesktopCaptureOptions::CreateDefault());
  }

 protected:
  void TestCaptureUpdatedRegion(
      std::initializer_list<DesktopCapturer*> capturers) {
    RTC_DCHECK(capturers.size() > 0);
// A large enough area for the tests, which should be able to be fulfilled
// by most systems.
#if defined(WEBRTC_WIN)
    // On Windows, an interesting warning window may pop up randomly. The root
    // cause is still under investigation, so reduce the test area to work
    // around. Bug https://bugs.chromium.org/p/webrtc/issues/detail?id=6666.
    const int kTestArea = 416;
#else
    const int kTestArea = 512;
#endif
    const int kRectSize = 32;
    std::unique_ptr<ScreenDrawer> drawer = ScreenDrawer::Create();
    if (!drawer || drawer->DrawableRegion().is_empty()) {
      RTC_LOG(LS_WARNING)
          << "No ScreenDrawer implementation for current platform.";
      return;
    }
    if (drawer->DrawableRegion().width() < kTestArea ||
        drawer->DrawableRegion().height() < kTestArea) {
      RTC_LOG(LS_WARNING)
          << "ScreenDrawer::DrawableRegion() is too small for the "
             "CaptureUpdatedRegion tests.";
      return;
    }

    for (DesktopCapturer* capturer : capturers) {
      capturer->Start(&callback_);
    }

    // Draw a set of `kRectSize` by `kRectSize` rectangles at (`i`, `i`), or
    // `i` by `i` rectangles at (`kRectSize`, `kRectSize`). One of (controlled
    // by `c`) its primary colors is `i`, and the other two are 0x7f. So we
    // won't draw a black or white rectangle.
    for (int c = 0; c < 3; c++) {
      // A fixed size rectangle.
      for (int i = 0; i < kTestArea - kRectSize; i += 16) {
        DesktopRect rect = DesktopRect::MakeXYWH(i, i, kRectSize, kRectSize);
        rect.Translate(drawer->DrawableRegion().top_left());
        RgbaColor color((c == 0 ? (i & 0xff) : 0x7f),
                        (c == 1 ? (i & 0xff) : 0x7f),
                        (c == 2 ? (i & 0xff) : 0x7f));
        // Fail fast.
        ASSERT_NO_FATAL_FAILURE(
            TestCaptureOneFrame(capturers, drawer.get(), rect, color));
      }

      // A variable-size rectangle.
      for (int i = 0; i < kTestArea - kRectSize; i += 16) {
        DesktopRect rect = DesktopRect::MakeXYWH(kRectSize, kRectSize, i, i);
        rect.Translate(drawer->DrawableRegion().top_left());
        RgbaColor color((c == 0 ? (i & 0xff) : 0x7f),
                        (c == 1 ? (i & 0xff) : 0x7f),
                        (c == 2 ? (i & 0xff) : 0x7f));
        // Fail fast.
        ASSERT_NO_FATAL_FAILURE(
            TestCaptureOneFrame(capturers, drawer.get(), rect, color));
      }
    }
  }

  void TestCaptureUpdatedRegion() {
    TestCaptureUpdatedRegion({capturer_.get()});
  }

#if defined(WEBRTC_WIN)
  // Enable allow_directx_capturer in DesktopCaptureOptions, but let
  // DesktopCapturer::CreateScreenCapturer() to decide whether a DirectX
  // capturer should be used.
  void MaybeCreateDirectxCapturer() {
    DesktopCaptureOptions options(DesktopCaptureOptions::CreateDefault());
    options.set_allow_directx_capturer(true);
    capturer_ = DesktopCapturer::CreateScreenCapturer(options);
  }

  bool CreateDirectxCapturer() {
    if (!ScreenCapturerWinDirectx::IsSupported()) {
      RTC_LOG(LS_WARNING) << "Directx capturer is not supported";
      return false;
    }

    MaybeCreateDirectxCapturer();
    return true;
  }
#endif  // defined(WEBRTC_WIN)

  std::unique_ptr<DesktopCapturer> capturer_;
  MockDesktopCapturerCallback callback_;

 private:
  // Repeats capturing the frame by using `capturers` one-by-one for 600 times,
  // typically 30 seconds, until they succeeded captured a `color` rectangle at
  // `rect`. This function uses `drawer`->WaitForPendingDraws() between two
  // attempts to wait for the screen to update.
  void TestCaptureOneFrame(std::vector<DesktopCapturer*> capturers,
                           ScreenDrawer* drawer,
                           DesktopRect rect,
                           RgbaColor color) {
    const int wait_capture_round = 600;
    drawer->Clear();
    size_t succeeded_capturers = 0;
    for (int i = 0; i < wait_capture_round; i++) {
      drawer->DrawRectangle(rect, color);
      drawer->WaitForPendingDraws();
      for (size_t j = 0; j < capturers.size(); j++) {
        if (capturers[j] == nullptr) {
          // DesktopCapturer should return an empty updated_region() if no
          // update detected. So we won't test it again if it has captured the
          // rectangle we drew.
          continue;
        }
        std::unique_ptr<DesktopFrame> frame = CaptureFrame(capturers[j]);
        if (!frame) {
          // CaptureFrame() has triggered an assertion failure already, we only
          // need to return here.
          return;
        }

        if (ArePixelsColoredBy(*frame, rect, color,
                               drawer->MayDrawIncompleteShapes())) {
          capturers[j] = nullptr;
          succeeded_capturers++;
        }
        // The following else if statement is for debugging purpose only, which
        // should be removed after flaky of ScreenCapturerIntegrationTest has
        // been resolved.
        else if (i == wait_capture_round - 1) {
          std::string result;
          rtc::Base64::EncodeFromArray(
              frame->data(), frame->size().height() * frame->stride(), &result);
          std::cout << frame->size().width() << " x " << frame->size().height()
                    << std::endl;
          // Split the entire string (can be over 4M) into several lines to
          // avoid browser from sticking.
          static const size_t kLineLength = 32768;
          const char* result_end = result.c_str() + result.length();
          for (const char* it = result.c_str(); it < result_end;
               it += kLineLength) {
            const size_t max_length = result_end - it;
            std::cout << std::string(it, std::min(kLineLength, max_length))
                      << std::endl;
          }
          std::cout << "Failed to capture rectangle " << rect.left() << " x "
                    << rect.top() << " - " << rect.right() << " x "
                    << rect.bottom() << " with color ("
                    << static_cast<int>(color.red) << ", "
                    << static_cast<int>(color.green) << ", "
                    << static_cast<int>(color.blue) << ", "
                    << static_cast<int>(color.alpha) << ")" << std::endl;
          ASSERT_TRUE(false) << "ScreenCapturerIntegrationTest may be flaky. "
                                "Please kindly FYI the broken link to "
                                "zijiehe@chromium.org for investigation. If "
                                "the failure continually happens, but I have "
                                "not responded as quick as expected, disable "
                                "*all* tests in "
                                "screen_capturer_integration_test.cc to "
                                "unblock other developers.";
        }
      }

      if (succeeded_capturers == capturers.size()) {
        break;
      }
    }

    ASSERT_EQ(succeeded_capturers, capturers.size());
  }

  // Expects `capturer` to successfully capture a frame, and returns it.
  std::unique_ptr<DesktopFrame> CaptureFrame(DesktopCapturer* capturer) {
    for (int i = 0; i < 10; i++) {
      std::unique_ptr<DesktopFrame> frame;
      DesktopCapturer::Result result;
      EXPECT_CALL(callback_, OnCaptureResultPtr(_, _))
          .WillOnce(SaveCaptureResult(&result, &frame));
      capturer->CaptureFrame();
      ::testing::Mock::VerifyAndClearExpectations(&callback_);
      if (result == DesktopCapturer::Result::SUCCESS) {
        EXPECT_TRUE(frame);
        return frame;
      } else {
        EXPECT_FALSE(frame);
      }
    }

    EXPECT_TRUE(false);
    return nullptr;
  }
};

#if defined(WEBRTC_WIN)
// ScreenCapturerWinGdi randomly returns blank screen, the root cause is still
// unknown. Bug, https://bugs.chromium.org/p/webrtc/issues/detail?id=6843.
#define MAYBE_CaptureUpdatedRegion DISABLED_CaptureUpdatedRegion
#else
#define MAYBE_CaptureUpdatedRegion CaptureUpdatedRegion
#endif
TEST_F(ScreenCapturerIntegrationTest, MAYBE_CaptureUpdatedRegion) {
  TestCaptureUpdatedRegion();
}

#if defined(WEBRTC_WIN)
// ScreenCapturerWinGdi randomly returns blank screen, the root cause is still
// unknown. Bug, https://bugs.chromium.org/p/webrtc/issues/detail?id=6843.
#define MAYBE_TwoCapturers DISABLED_TwoCapturers
#else
#define MAYBE_TwoCapturers TwoCapturers
#endif
TEST_F(ScreenCapturerIntegrationTest, MAYBE_TwoCapturers) {
  std::unique_ptr<DesktopCapturer> capturer2 = std::move(capturer_);
  SetUp();
  TestCaptureUpdatedRegion({capturer_.get(), capturer2.get()});
}

#if defined(WEBRTC_WIN)

// Windows cannot capture contents on VMs hosted in GCE. See bug
// https://bugs.chromium.org/p/webrtc/issues/detail?id=8153.
TEST_F(ScreenCapturerIntegrationTest,
       DISABLED_CaptureUpdatedRegionWithDirectxCapturer) {
  if (!CreateDirectxCapturer()) {
    return;
  }

  TestCaptureUpdatedRegion();
}

TEST_F(ScreenCapturerIntegrationTest, DISABLED_TwoDirectxCapturers) {
  if (!CreateDirectxCapturer()) {
    return;
  }

  std::unique_ptr<DesktopCapturer> capturer2 = std::move(capturer_);
  RTC_CHECK(CreateDirectxCapturer());
  TestCaptureUpdatedRegion({capturer_.get(), capturer2.get()});
}

TEST_F(ScreenCapturerIntegrationTest,
       DISABLED_MaybeCaptureUpdatedRegionWithDirectxCapturer) {
  if (rtc::rtc_win::GetVersion() < rtc::rtc_win::Version::VERSION_WIN8) {
    // ScreenCapturerWinGdi randomly returns blank screen, the root cause is
    // still unknown. Bug,
    // https://bugs.chromium.org/p/webrtc/issues/detail?id=6843.
    // On Windows 7 or early version, MaybeCreateDirectxCapturer() always
    // creates GDI capturer.
    return;
  }
  // Even DirectX capturer is not supported in current system, we should be able
  // to select a usable capturer.
  MaybeCreateDirectxCapturer();
  TestCaptureUpdatedRegion();
}

#endif  // defined(WEBRTC_WIN)

}  // namespace webrtc

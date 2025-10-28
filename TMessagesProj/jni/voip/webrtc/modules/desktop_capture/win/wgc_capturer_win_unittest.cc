/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/win/wgc_capturer_win.h"

#include <string>
#include <utility>
#include <vector>

#include "modules/desktop_capture/desktop_capture_options.h"
#include "modules/desktop_capture/desktop_capture_types.h"
#include "modules/desktop_capture/desktop_capturer.h"
#include "modules/desktop_capture/win/test_support/test_window.h"
#include "modules/desktop_capture/win/wgc_capture_session.h"
#include "modules/desktop_capture/win/window_capture_utils.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/task_queue_for_test.h"
#include "rtc_base/thread.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/win/scoped_com_initializer.h"
#include "rtc_base/win/windows_version.h"
#include "system_wrappers/include/metrics.h"
#include "system_wrappers/include/sleep.h"
#include "test/gtest.h"

namespace webrtc {
namespace {

constexpr char kWindowThreadName[] = "wgc_capturer_test_window_thread";
constexpr WCHAR kWindowTitle[] = L"WGC Capturer Test Window";

constexpr char kCapturerImplHistogram[] =
    "WebRTC.DesktopCapture.Win.DesktopCapturerImpl";

constexpr char kCapturerResultHistogram[] =
    "WebRTC.DesktopCapture.Win.WgcCapturerResult";
constexpr int kSuccess = 0;
constexpr int kSessionStartFailure = 4;

constexpr char kCaptureSessionResultHistogram[] =
    "WebRTC.DesktopCapture.Win.WgcCaptureSessionStartResult";
constexpr int kSourceClosed = 1;

constexpr char kCaptureTimeHistogram[] =
    "WebRTC.DesktopCapture.Win.WgcCapturerFrameTime";

// The capturer keeps `kNumBuffers` in its frame pool, so we need to request
// that many frames to clear those out. The next frame will have the new size
// (if the size has changed) so we will resize the frame pool at this point.
// Then, we need to clear any frames that may have delivered to the frame pool
// before the resize. Finally, the next frame will be guaranteed to be the new
// size.
constexpr int kNumCapturesToFlushBuffers =
    WgcCaptureSession::kNumBuffers * 2 + 1;

constexpr int kSmallWindowWidth = 200;
constexpr int kSmallWindowHeight = 100;
constexpr int kMediumWindowWidth = 300;
constexpr int kMediumWindowHeight = 200;
constexpr int kLargeWindowWidth = 400;
constexpr int kLargeWindowHeight = 500;

// The size of the image we capture is slightly smaller than the actual size of
// the window.
constexpr int kWindowWidthSubtrahend = 14;
constexpr int kWindowHeightSubtrahend = 7;

// Custom message constants so we can direct our thread to close windows and
// quit running.
constexpr UINT kDestroyWindow = WM_APP;
constexpr UINT kQuitRunning = WM_APP + 1;

// When testing changes to real windows, sometimes the effects (close or resize)
// don't happen immediately, we want to keep trying until we see the effect but
// only for a reasonable amount of time.
constexpr int kMaxTries = 50;

}  // namespace

class WgcCapturerWinTest : public ::testing::TestWithParam<CaptureType>,
                           public DesktopCapturer::Callback {
 public:
  void SetUp() override {
    com_initializer_ =
        std::make_unique<ScopedCOMInitializer>(ScopedCOMInitializer::kMTA);
    EXPECT_TRUE(com_initializer_->Succeeded());

    if (!IsWgcSupported(GetParam())) {
      RTC_LOG(LS_INFO)
          << "Skipping WgcCapturerWinTests on unsupported platforms.";
      GTEST_SKIP();
    }
  }

  void SetUpForWindowCapture(int window_width = kMediumWindowWidth,
                             int window_height = kMediumWindowHeight) {
    capturer_ = WgcCapturerWin::CreateRawWindowCapturer(
        DesktopCaptureOptions::CreateDefault());
    CreateWindowOnSeparateThread(window_width, window_height);
    StartWindowThreadMessageLoop();
    source_id_ = GetTestWindowIdFromSourceList();
  }

  void SetUpForScreenCapture() {
    capturer_ = WgcCapturerWin::CreateRawScreenCapturer(
        DesktopCaptureOptions::CreateDefault());
    source_id_ = GetScreenIdFromSourceList();
  }

  void TearDown() override {
    if (window_open_) {
      CloseTestWindow();
    }
  }

  // The window must live on a separate thread so that we can run a message pump
  // without blocking the test thread. This is necessary if we are interested in
  // having GraphicsCaptureItem events (i.e. the Closed event) fire, and it more
  // closely resembles how capture works in the wild.
  void CreateWindowOnSeparateThread(int window_width, int window_height) {
    window_thread_ = rtc::Thread::Create();
    window_thread_->SetName(kWindowThreadName, nullptr);
    window_thread_->Start();
    SendTask(window_thread_.get(), [this, window_width, window_height]() {
      window_thread_id_ = GetCurrentThreadId();
      window_info_ =
          CreateTestWindow(kWindowTitle, window_height, window_width);
      window_open_ = true;

      while (!IsWindowResponding(window_info_.hwnd)) {
        RTC_LOG(LS_INFO) << "Waiting for test window to become responsive in "
                            "WgcWindowCaptureTest.";
      }

      while (!IsWindowValidAndVisible(window_info_.hwnd)) {
        RTC_LOG(LS_INFO) << "Waiting for test window to be visible in "
                            "WgcWindowCaptureTest.";
      }
    });

    ASSERT_TRUE(window_thread_->RunningForTest());
    ASSERT_FALSE(window_thread_->IsCurrent());
  }

  void StartWindowThreadMessageLoop() {
    window_thread_->PostTask([this]() {
      MSG msg;
      BOOL gm;
      while ((gm = ::GetMessage(&msg, NULL, 0, 0)) != 0 && gm != -1) {
        ::DispatchMessage(&msg);
        if (msg.message == kDestroyWindow) {
          DestroyTestWindow(window_info_);
        }
        if (msg.message == kQuitRunning) {
          PostQuitMessage(0);
        }
      }
    });
  }

  void CloseTestWindow() {
    ::PostThreadMessage(window_thread_id_, kDestroyWindow, 0, 0);
    ::PostThreadMessage(window_thread_id_, kQuitRunning, 0, 0);
    window_thread_->Stop();
    window_open_ = false;
  }

  DesktopCapturer::SourceId GetTestWindowIdFromSourceList() {
    // Frequently, the test window will not show up in GetSourceList because it
    // was created too recently. Since we are confident the window will be found
    // eventually we loop here until we find it.
    intptr_t src_id = 0;
    do {
      DesktopCapturer::SourceList sources;
      EXPECT_TRUE(capturer_->GetSourceList(&sources));
      auto it = std::find_if(
          sources.begin(), sources.end(),
          [&](const DesktopCapturer::Source& src) {
            return src.id == reinterpret_cast<intptr_t>(window_info_.hwnd);
          });

      if (it != sources.end())
        src_id = it->id;
    } while (src_id != reinterpret_cast<intptr_t>(window_info_.hwnd));

    return src_id;
  }

  DesktopCapturer::SourceId GetScreenIdFromSourceList() {
    DesktopCapturer::SourceList sources;
    EXPECT_TRUE(capturer_->GetSourceList(&sources));
    EXPECT_GT(sources.size(), 0ULL);
    return sources[0].id;
  }

  void DoCapture(int num_captures = 1) {
    // Capture the requested number of frames. We expect the first capture to
    // always succeed. If we're asked for multiple frames, we do expect to see a
    // a couple dropped frames due to resizing the window.
    const int max_tries = num_captures == 1 ? 1 : kMaxTries;
    int success_count = 0;
    for (int i = 0; success_count < num_captures && i < max_tries; i++) {
      capturer_->CaptureFrame();
      if (result_ == DesktopCapturer::Result::ERROR_PERMANENT)
        break;
      if (result_ == DesktopCapturer::Result::SUCCESS)
        success_count++;
    }

    total_successful_captures_ += success_count;
    EXPECT_EQ(success_count, num_captures);
    EXPECT_EQ(result_, DesktopCapturer::Result::SUCCESS);
    EXPECT_TRUE(frame_);
    EXPECT_GE(metrics::NumEvents(kCapturerResultHistogram, kSuccess),
              total_successful_captures_);
  }

  void ValidateFrame(int expected_width, int expected_height) {
    EXPECT_EQ(frame_->size().width(), expected_width - kWindowWidthSubtrahend);
    EXPECT_EQ(frame_->size().height(),
              expected_height - kWindowHeightSubtrahend);

    // Verify the buffer contains as much data as it should.
    int data_length = frame_->stride() * frame_->size().height();

    // The first and last pixel should have the same color because they will be
    // from the border of the window.
    // Pixels have 4 bytes of data so the whole pixel needs a uint32_t to fit.
    uint32_t first_pixel = static_cast<uint32_t>(*frame_->data());
    uint32_t last_pixel = static_cast<uint32_t>(
        *(frame_->data() + data_length - DesktopFrame::kBytesPerPixel));
    EXPECT_EQ(first_pixel, last_pixel);

    // Let's also check a pixel from the middle of the content area, which the
    // test window will paint a consistent color for us to verify.
    uint8_t* middle_pixel = frame_->data() + (data_length / 2);

    int sub_pixel_offset = DesktopFrame::kBytesPerPixel / 4;
    EXPECT_EQ(*middle_pixel, kTestWindowBValue);
    middle_pixel += sub_pixel_offset;
    EXPECT_EQ(*middle_pixel, kTestWindowGValue);
    middle_pixel += sub_pixel_offset;
    EXPECT_EQ(*middle_pixel, kTestWindowRValue);
    middle_pixel += sub_pixel_offset;

    // The window is opaque so we expect 0xFF for the Alpha channel.
    EXPECT_EQ(*middle_pixel, 0xFF);
  }

  // DesktopCapturer::Callback interface
  // The capturer synchronously invokes this method before `CaptureFrame()`
  // returns.
  void OnCaptureResult(DesktopCapturer::Result result,
                       std::unique_ptr<DesktopFrame> frame) override {
    result_ = result;
    frame_ = std::move(frame);
  }

 protected:
  std::unique_ptr<ScopedCOMInitializer> com_initializer_;
  DWORD window_thread_id_;
  std::unique_ptr<rtc::Thread> window_thread_;
  WindowInfo window_info_;
  intptr_t source_id_;
  bool window_open_ = false;
  DesktopCapturer::Result result_;
  int total_successful_captures_ = 0;
  std::unique_ptr<DesktopFrame> frame_;
  std::unique_ptr<DesktopCapturer> capturer_;
};

TEST_P(WgcCapturerWinTest, SelectValidSource) {
  if (GetParam() == CaptureType::kWindow) {
    SetUpForWindowCapture();
  } else {
    SetUpForScreenCapture();
  }

  EXPECT_TRUE(capturer_->SelectSource(source_id_));
}

TEST_P(WgcCapturerWinTest, SelectInvalidSource) {
  if (GetParam() == CaptureType::kWindow) {
    capturer_ = WgcCapturerWin::CreateRawWindowCapturer(
        DesktopCaptureOptions::CreateDefault());
    source_id_ = kNullWindowId;
  } else {
    capturer_ = WgcCapturerWin::CreateRawScreenCapturer(
        DesktopCaptureOptions::CreateDefault());
    source_id_ = kInvalidScreenId;
  }

  EXPECT_FALSE(capturer_->SelectSource(source_id_));
}

TEST_P(WgcCapturerWinTest, Capture) {
  if (GetParam() == CaptureType::kWindow) {
    SetUpForWindowCapture();
  } else {
    SetUpForScreenCapture();
  }

  EXPECT_TRUE(capturer_->SelectSource(source_id_));

  capturer_->Start(this);
  EXPECT_GE(metrics::NumEvents(kCapturerImplHistogram,
                               DesktopCapturerId::kWgcCapturerWin),
            1);

  DoCapture();
  EXPECT_GT(frame_->size().width(), 0);
  EXPECT_GT(frame_->size().height(), 0);
}

TEST_P(WgcCapturerWinTest, CaptureTime) {
  if (GetParam() == CaptureType::kWindow) {
    SetUpForWindowCapture();
  } else {
    SetUpForScreenCapture();
  }

  EXPECT_TRUE(capturer_->SelectSource(source_id_));
  capturer_->Start(this);

  int64_t start_time;
  start_time = rtc::TimeNanos();
  capturer_->CaptureFrame();

  int capture_time_ms =
      (rtc::TimeNanos() - start_time) / rtc::kNumNanosecsPerMillisec;
  EXPECT_EQ(result_, DesktopCapturer::Result::SUCCESS);
  EXPECT_TRUE(frame_);

  // The test may measure the time slightly differently than the capturer. So we
  // just check if it's within 5 ms.
  EXPECT_NEAR(frame_->capture_time_ms(), capture_time_ms, 5);
  EXPECT_GE(
      metrics::NumEvents(kCaptureTimeHistogram, frame_->capture_time_ms()), 1);
}

INSTANTIATE_TEST_SUITE_P(SourceAgnostic,
                         WgcCapturerWinTest,
                         ::testing::Values(CaptureType::kWindow,
                                           CaptureType::kScreen));

TEST(WgcCapturerNoMonitorTest, NoMonitors) {
  ScopedCOMInitializer com_initializer(ScopedCOMInitializer::kMTA);
  EXPECT_TRUE(com_initializer.Succeeded());
  if (HasActiveDisplay()) {
    RTC_LOG(LS_INFO) << "Skip WgcCapturerWinTest designed specifically for "
                        "systems with no monitors";
    GTEST_SKIP();
  }

  // A bug in `CreateForMonitor` prevents screen capture when no displays are
  // attached.
  EXPECT_FALSE(IsWgcSupported(CaptureType::kScreen));

  // A bug in the DWM (Desktop Window Manager) prevents it from providing image
  // data if there are no displays attached. This was fixed in Windows 11.
  if (rtc::rtc_win::GetVersion() < rtc::rtc_win::Version::VERSION_WIN11)
    EXPECT_FALSE(IsWgcSupported(CaptureType::kWindow));
  else
    EXPECT_TRUE(IsWgcSupported(CaptureType::kWindow));
}

class WgcCapturerMonitorTest : public WgcCapturerWinTest {
 public:
  void SetUp() {
    com_initializer_ =
        std::make_unique<ScopedCOMInitializer>(ScopedCOMInitializer::kMTA);
    EXPECT_TRUE(com_initializer_->Succeeded());

    if (!IsWgcSupported(CaptureType::kScreen)) {
      RTC_LOG(LS_INFO)
          << "Skipping WgcCapturerWinTests on unsupported platforms.";
      GTEST_SKIP();
    }
  }
};

TEST_F(WgcCapturerMonitorTest, FocusOnMonitor) {
  SetUpForScreenCapture();
  EXPECT_TRUE(capturer_->SelectSource(0));

  // You can't set focus on a monitor.
  EXPECT_FALSE(capturer_->FocusOnSelectedSource());
}

TEST_F(WgcCapturerMonitorTest, CaptureAllMonitors) {
  SetUpForScreenCapture();
  EXPECT_TRUE(capturer_->SelectSource(kFullDesktopScreenId));

  capturer_->Start(this);
  DoCapture();
  EXPECT_GT(frame_->size().width(), 0);
  EXPECT_GT(frame_->size().height(), 0);
}

class WgcCapturerWindowTest : public WgcCapturerWinTest {
 public:
  void SetUp() {
    com_initializer_ =
        std::make_unique<ScopedCOMInitializer>(ScopedCOMInitializer::kMTA);
    EXPECT_TRUE(com_initializer_->Succeeded());

    if (!IsWgcSupported(CaptureType::kWindow)) {
      RTC_LOG(LS_INFO)
          << "Skipping WgcCapturerWinTests on unsupported platforms.";
      GTEST_SKIP();
    }
  }
};

TEST_F(WgcCapturerWindowTest, FocusOnWindow) {
  capturer_ = WgcCapturerWin::CreateRawWindowCapturer(
      DesktopCaptureOptions::CreateDefault());
  window_info_ = CreateTestWindow(kWindowTitle);
  source_id_ = GetScreenIdFromSourceList();

  EXPECT_TRUE(capturer_->SelectSource(source_id_));
  EXPECT_TRUE(capturer_->FocusOnSelectedSource());

  HWND hwnd = reinterpret_cast<HWND>(source_id_);
  EXPECT_EQ(hwnd, ::GetActiveWindow());
  EXPECT_EQ(hwnd, ::GetForegroundWindow());
  EXPECT_EQ(hwnd, ::GetFocus());
  DestroyTestWindow(window_info_);
}

TEST_F(WgcCapturerWindowTest, SelectMinimizedWindow) {
  SetUpForWindowCapture();
  MinimizeTestWindow(reinterpret_cast<HWND>(source_id_));
  EXPECT_FALSE(capturer_->SelectSource(source_id_));

  UnminimizeTestWindow(reinterpret_cast<HWND>(source_id_));
  EXPECT_TRUE(capturer_->SelectSource(source_id_));
}

TEST_F(WgcCapturerWindowTest, SelectClosedWindow) {
  SetUpForWindowCapture();
  EXPECT_TRUE(capturer_->SelectSource(source_id_));

  CloseTestWindow();
  EXPECT_FALSE(capturer_->SelectSource(source_id_));
}

TEST_F(WgcCapturerWindowTest, UnsupportedWindowStyle) {
  // Create a window with the WS_EX_TOOLWINDOW style, which WGC does not
  // support.
  window_info_ = CreateTestWindow(kWindowTitle, kMediumWindowWidth,
                                  kMediumWindowHeight, WS_EX_TOOLWINDOW);
  capturer_ = WgcCapturerWin::CreateRawWindowCapturer(
      DesktopCaptureOptions::CreateDefault());
  DesktopCapturer::SourceList sources;
  EXPECT_TRUE(capturer_->GetSourceList(&sources));
  auto it = std::find_if(
      sources.begin(), sources.end(), [&](const DesktopCapturer::Source& src) {
        return src.id == reinterpret_cast<intptr_t>(window_info_.hwnd);
      });

  // We should not find the window, since we filter for unsupported styles.
  EXPECT_EQ(it, sources.end());
  DestroyTestWindow(window_info_);
}

TEST_F(WgcCapturerWindowTest, IncreaseWindowSizeMidCapture) {
  SetUpForWindowCapture(kSmallWindowWidth, kSmallWindowHeight);
  EXPECT_TRUE(capturer_->SelectSource(source_id_));

  capturer_->Start(this);
  DoCapture();
  ValidateFrame(kSmallWindowWidth, kSmallWindowHeight);

  ResizeTestWindow(window_info_.hwnd, kSmallWindowWidth, kMediumWindowHeight);
  DoCapture(kNumCapturesToFlushBuffers);
  ValidateFrame(kSmallWindowWidth, kMediumWindowHeight);

  ResizeTestWindow(window_info_.hwnd, kLargeWindowWidth, kMediumWindowHeight);
  DoCapture(kNumCapturesToFlushBuffers);
  ValidateFrame(kLargeWindowWidth, kMediumWindowHeight);
}

TEST_F(WgcCapturerWindowTest, ReduceWindowSizeMidCapture) {
  SetUpForWindowCapture(kLargeWindowWidth, kLargeWindowHeight);
  EXPECT_TRUE(capturer_->SelectSource(source_id_));

  capturer_->Start(this);
  DoCapture();
  ValidateFrame(kLargeWindowWidth, kLargeWindowHeight);

  ResizeTestWindow(window_info_.hwnd, kLargeWindowWidth, kMediumWindowHeight);
  DoCapture(kNumCapturesToFlushBuffers);
  ValidateFrame(kLargeWindowWidth, kMediumWindowHeight);

  ResizeTestWindow(window_info_.hwnd, kSmallWindowWidth, kMediumWindowHeight);
  DoCapture(kNumCapturesToFlushBuffers);
  ValidateFrame(kSmallWindowWidth, kMediumWindowHeight);
}

TEST_F(WgcCapturerWindowTest, MinimizeWindowMidCapture) {
  SetUpForWindowCapture();
  EXPECT_TRUE(capturer_->SelectSource(source_id_));

  capturer_->Start(this);

  // Minmize the window and capture should continue but return temporary errors.
  MinimizeTestWindow(window_info_.hwnd);
  for (int i = 0; i < 5; ++i) {
    capturer_->CaptureFrame();
    EXPECT_EQ(result_, DesktopCapturer::Result::ERROR_TEMPORARY);
  }

  // Reopen the window and the capture should continue normally.
  UnminimizeTestWindow(window_info_.hwnd);
  DoCapture();
  // We can't verify the window size here because the test window does not
  // repaint itself after it is unminimized, but capturing successfully is still
  // a good test.
}

TEST_F(WgcCapturerWindowTest, CloseWindowMidCapture) {
  SetUpForWindowCapture();
  EXPECT_TRUE(capturer_->SelectSource(source_id_));

  capturer_->Start(this);
  DoCapture();
  ValidateFrame(kMediumWindowWidth, kMediumWindowHeight);

  CloseTestWindow();

  // We need to pump our message queue so the Closed event will be delivered to
  // the capturer's event handler. If we are too early and the Closed event
  // hasn't arrived yet we should keep trying until the capturer receives it and
  // stops.
  auto* wgc_capturer = static_cast<WgcCapturerWin*>(capturer_.get());
  MSG msg;
  for (int i = 0;
       wgc_capturer->IsSourceBeingCaptured(source_id_) && i < kMaxTries; ++i) {
    // Unlike GetMessage, PeekMessage will not hang if there are no messages in
    // the queue.
    PeekMessage(&msg, 0, 0, 0, PM_REMOVE);
    SleepMs(1);
  }

  EXPECT_FALSE(wgc_capturer->IsSourceBeingCaptured(source_id_));

  // The frame pool can buffer `kNumBuffers` frames. We must consume these
  // and then make one more call to CaptureFrame before we expect to see the
  // failure.
  int num_tries = 0;
  do {
    capturer_->CaptureFrame();
  } while (result_ == DesktopCapturer::Result::SUCCESS &&
           ++num_tries <= WgcCaptureSession::kNumBuffers);

  EXPECT_GE(metrics::NumEvents(kCapturerResultHistogram, kSessionStartFailure),
            1);
  EXPECT_GE(metrics::NumEvents(kCaptureSessionResultHistogram, kSourceClosed),
            1);
  EXPECT_EQ(result_, DesktopCapturer::Result::ERROR_PERMANENT);
}

}  // namespace webrtc

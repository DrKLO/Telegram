/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/win/screen_capturer_win_gdi.h"

#include <utility>

#include "modules/desktop_capture/desktop_capture_metrics_helper.h"
#include "modules/desktop_capture/desktop_capture_options.h"
#include "modules/desktop_capture/desktop_capture_types.h"
#include "modules/desktop_capture/desktop_frame.h"
#include "modules/desktop_capture/desktop_frame_win.h"
#include "modules/desktop_capture/desktop_region.h"
#include "modules/desktop_capture/mouse_cursor.h"
#include "modules/desktop_capture/win/cursor.h"
#include "modules/desktop_capture/win/desktop.h"
#include "modules/desktop_capture/win/screen_capture_utils.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

namespace {

// Constants from dwmapi.h.
const UINT DWM_EC_DISABLECOMPOSITION = 0;
const UINT DWM_EC_ENABLECOMPOSITION = 1;

const wchar_t kDwmapiLibraryName[] = L"dwmapi.dll";

}  // namespace

ScreenCapturerWinGdi::ScreenCapturerWinGdi(
    const DesktopCaptureOptions& options) {
  if (options.disable_effects()) {
    // Load dwmapi.dll dynamically since it is not available on XP.
    if (!dwmapi_library_)
      dwmapi_library_ = LoadLibraryW(kDwmapiLibraryName);

    if (dwmapi_library_) {
      composition_func_ = reinterpret_cast<DwmEnableCompositionFunc>(
          GetProcAddress(dwmapi_library_, "DwmEnableComposition"));
    }
  }
}

ScreenCapturerWinGdi::~ScreenCapturerWinGdi() {
  if (desktop_dc_)
    ReleaseDC(NULL, desktop_dc_);
  if (memory_dc_)
    DeleteDC(memory_dc_);

  // Restore Aero.
  if (composition_func_)
    (*composition_func_)(DWM_EC_ENABLECOMPOSITION);

  if (dwmapi_library_)
    FreeLibrary(dwmapi_library_);
}

void ScreenCapturerWinGdi::SetSharedMemoryFactory(
    std::unique_ptr<SharedMemoryFactory> shared_memory_factory) {
  shared_memory_factory_ = std::move(shared_memory_factory);
}

void ScreenCapturerWinGdi::CaptureFrame() {
  TRACE_EVENT0("webrtc", "ScreenCapturerWinGdi::CaptureFrame");
  int64_t capture_start_time_nanos = rtc::TimeNanos();

  queue_.MoveToNextFrame();
  if (queue_.current_frame() && queue_.current_frame()->IsShared()) {
    RTC_DLOG(LS_WARNING) << "Overwriting frame that is still shared.";
  }

  // Make sure the GDI capture resources are up-to-date.
  PrepareCaptureResources();

  if (!CaptureImage()) {
    RTC_LOG(LS_WARNING) << "Failed to capture screen by GDI.";
    callback_->OnCaptureResult(Result::ERROR_TEMPORARY, nullptr);
    return;
  }

  // Emit the current frame.
  std::unique_ptr<DesktopFrame> frame = queue_.current_frame()->Share();
  frame->set_dpi(DesktopVector(GetDeviceCaps(desktop_dc_, LOGPIXELSX),
                               GetDeviceCaps(desktop_dc_, LOGPIXELSY)));
  frame->mutable_updated_region()->SetRect(
      DesktopRect::MakeSize(frame->size()));

  int capture_time_ms = (rtc::TimeNanos() - capture_start_time_nanos) /
                        rtc::kNumNanosecsPerMillisec;
  RTC_HISTOGRAM_COUNTS_1000(
      "WebRTC.DesktopCapture.Win.ScreenGdiCapturerFrameTime", capture_time_ms);
  frame->set_capture_time_ms(capture_time_ms);
  frame->set_capturer_id(DesktopCapturerId::kScreenCapturerWinGdi);
  callback_->OnCaptureResult(Result::SUCCESS, std::move(frame));
}

bool ScreenCapturerWinGdi::GetSourceList(SourceList* sources) {
  return webrtc::GetScreenList(sources);
}

bool ScreenCapturerWinGdi::SelectSource(SourceId id) {
  bool valid = IsScreenValid(id, &current_device_key_);
  if (valid)
    current_screen_id_ = id;
  return valid;
}

void ScreenCapturerWinGdi::Start(Callback* callback) {
  RTC_DCHECK(!callback_);
  RTC_DCHECK(callback);
  RecordCapturerImpl(DesktopCapturerId::kScreenCapturerWinGdi);

  callback_ = callback;

  // Vote to disable Aero composited desktop effects while capturing. Windows
  // will restore Aero automatically if the process exits. This has no effect
  // under Windows 8 or higher.  See crbug.com/124018.
  if (composition_func_)
    (*composition_func_)(DWM_EC_DISABLECOMPOSITION);
}

void ScreenCapturerWinGdi::PrepareCaptureResources() {
  // Switch to the desktop receiving user input if different from the current
  // one.
  std::unique_ptr<Desktop> input_desktop(Desktop::GetInputDesktop());
  if (input_desktop && !desktop_.IsSame(*input_desktop)) {
    // Release GDI resources otherwise SetThreadDesktop will fail.
    if (desktop_dc_) {
      ReleaseDC(NULL, desktop_dc_);
      desktop_dc_ = nullptr;
    }

    if (memory_dc_) {
      DeleteDC(memory_dc_);
      memory_dc_ = nullptr;
    }

    // If SetThreadDesktop() fails, the thread is still assigned a desktop.
    // So we can continue capture screen bits, just from the wrong desktop.
    desktop_.SetThreadDesktop(input_desktop.release());

    // Re-assert our vote to disable Aero.
    // See crbug.com/124018 and crbug.com/129906.
    if (composition_func_) {
      (*composition_func_)(DWM_EC_DISABLECOMPOSITION);
    }
  }

  // If the display configurations have changed then recreate GDI resources.
  if (display_configuration_monitor_.IsChanged(kFullDesktopScreenId)) {
    if (desktop_dc_) {
      ReleaseDC(NULL, desktop_dc_);
      desktop_dc_ = nullptr;
    }
    if (memory_dc_) {
      DeleteDC(memory_dc_);
      memory_dc_ = nullptr;
    }
  }

  if (!desktop_dc_) {
    RTC_DCHECK(!memory_dc_);

    // Create GDI device contexts to capture from the desktop into memory.
    desktop_dc_ = GetDC(nullptr);
    RTC_CHECK(desktop_dc_);
    memory_dc_ = CreateCompatibleDC(desktop_dc_);
    RTC_CHECK(memory_dc_);

    // Make sure the frame buffers will be reallocated.
    queue_.Reset();
  }
}

bool ScreenCapturerWinGdi::CaptureImage() {
  DesktopRect screen_rect =
      GetScreenRect(current_screen_id_, current_device_key_);
  if (screen_rect.is_empty()) {
    RTC_LOG(LS_WARNING) << "Failed to get screen rect.";
    return false;
  }

  DesktopSize size = screen_rect.size();
  // If the current buffer is from an older generation then allocate a new one.
  // Note that we can't reallocate other buffers at this point, since the caller
  // may still be reading from them.
  if (!queue_.current_frame() ||
      !queue_.current_frame()->size().equals(screen_rect.size())) {
    RTC_DCHECK(desktop_dc_);
    RTC_DCHECK(memory_dc_);

    std::unique_ptr<DesktopFrame> buffer = DesktopFrameWin::Create(
        size, shared_memory_factory_.get(), desktop_dc_);
    if (!buffer) {
      RTC_LOG(LS_WARNING) << "Failed to create frame buffer.";
      return false;
    }
    queue_.ReplaceCurrentFrame(SharedDesktopFrame::Wrap(std::move(buffer)));
  }
  queue_.current_frame()->set_top_left(
      screen_rect.top_left().subtract(GetFullscreenRect().top_left()));

  // Select the target bitmap into the memory dc and copy the rect from desktop
  // to memory.
  DesktopFrameWin* current = static_cast<DesktopFrameWin*>(
      queue_.current_frame()->GetUnderlyingFrame());
  HGDIOBJ previous_object = SelectObject(memory_dc_, current->bitmap());
  if (!previous_object || previous_object == HGDI_ERROR) {
    RTC_LOG(LS_WARNING) << "Failed to select current bitmap into memery dc.";
    return false;
  }

  bool result = (BitBlt(memory_dc_, 0, 0, screen_rect.width(),
                        screen_rect.height(), desktop_dc_, screen_rect.left(),
                        screen_rect.top(), SRCCOPY | CAPTUREBLT) != FALSE);
  if (!result) {
    RTC_LOG_GLE(LS_WARNING) << "BitBlt failed";
  }

  // Select back the previously selected object to that the device contect
  // could be destroyed independently of the bitmap if needed.
  SelectObject(memory_dc_, previous_object);

  return result;
}

}  // namespace webrtc

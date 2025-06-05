/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/win/screen_capturer_win_magnifier.h"

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
#include "system_wrappers/include/metrics.h"

namespace webrtc {

namespace {
DWORD GetTlsIndex() {
  static const DWORD tls_index = TlsAlloc();
  RTC_DCHECK(tls_index != TLS_OUT_OF_INDEXES);
  return tls_index;
}

}  // namespace

// kMagnifierWindowClass has to be "Magnifier" according to the Magnification
// API. The other strings can be anything.
static wchar_t kMagnifierHostClass[] = L"ScreenCapturerWinMagnifierHost";
static wchar_t kHostWindowName[] = L"MagnifierHost";
static wchar_t kMagnifierWindowClass[] = L"Magnifier";
static wchar_t kMagnifierWindowName[] = L"MagnifierWindow";

ScreenCapturerWinMagnifier::ScreenCapturerWinMagnifier() = default;
ScreenCapturerWinMagnifier::~ScreenCapturerWinMagnifier() {
  // DestroyWindow must be called before MagUninitialize. magnifier_window_ is
  // destroyed automatically when host_window_ is destroyed.
  if (host_window_)
    DestroyWindow(host_window_);

  if (magnifier_initialized_)
    mag_uninitialize_func_();

  if (mag_lib_handle_)
    FreeLibrary(mag_lib_handle_);

  if (desktop_dc_)
    ReleaseDC(NULL, desktop_dc_);
}

void ScreenCapturerWinMagnifier::Start(Callback* callback) {
  RTC_DCHECK(!callback_);
  RTC_DCHECK(callback);
  RecordCapturerImpl(DesktopCapturerId::kScreenCapturerWinMagnifier);

  callback_ = callback;

  if (!InitializeMagnifier()) {
    RTC_LOG_F(LS_WARNING) << "Magnifier initialization failed.";
  }
}

void ScreenCapturerWinMagnifier::SetSharedMemoryFactory(
    std::unique_ptr<SharedMemoryFactory> shared_memory_factory) {
  shared_memory_factory_ = std::move(shared_memory_factory);
}

void ScreenCapturerWinMagnifier::CaptureFrame() {
  RTC_DCHECK(callback_);
  if (!magnifier_initialized_) {
    RTC_LOG_F(LS_WARNING) << "Magnifier initialization failed.";
    callback_->OnCaptureResult(Result::ERROR_PERMANENT, nullptr);
    return;
  }

  int64_t capture_start_time_nanos = rtc::TimeNanos();

  // Switch to the desktop receiving user input if different from the current
  // one.
  std::unique_ptr<Desktop> input_desktop(Desktop::GetInputDesktop());
  if (input_desktop.get() != NULL && !desktop_.IsSame(*input_desktop)) {
    // Release GDI resources otherwise SetThreadDesktop will fail.
    if (desktop_dc_) {
      ReleaseDC(NULL, desktop_dc_);
      desktop_dc_ = NULL;
    }
    // If SetThreadDesktop() fails, the thread is still assigned a desktop.
    // So we can continue capture screen bits, just from the wrong desktop.
    desktop_.SetThreadDesktop(input_desktop.release());
  }

  DesktopRect rect = GetScreenRect(current_screen_id_, current_device_key_);
  queue_.MoveToNextFrame();
  CreateCurrentFrameIfNecessary(rect.size());
  // CaptureImage may fail in some situations, e.g. windows8 metro mode. So
  // defer to the fallback capturer if magnifier capturer did not work.
  if (!CaptureImage(rect)) {
    RTC_LOG_F(LS_WARNING) << "Magnifier capturer failed to capture a frame.";
    callback_->OnCaptureResult(Result::ERROR_PERMANENT, nullptr);
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
      "WebRTC.DesktopCapture.Win.MagnifierCapturerFrameTime", capture_time_ms);
  frame->set_capture_time_ms(capture_time_ms);
  frame->set_capturer_id(DesktopCapturerId::kScreenCapturerWinMagnifier);
  callback_->OnCaptureResult(Result::SUCCESS, std::move(frame));
}

bool ScreenCapturerWinMagnifier::GetSourceList(SourceList* sources) {
  return webrtc::GetScreenList(sources);
}

bool ScreenCapturerWinMagnifier::SelectSource(SourceId id) {
  if (IsScreenValid(id, &current_device_key_)) {
    current_screen_id_ = id;
    return true;
  }

  return false;
}

void ScreenCapturerWinMagnifier::SetExcludedWindow(WindowId excluded_window) {
  excluded_window_ = (HWND)excluded_window;
  if (excluded_window_ && magnifier_initialized_) {
    set_window_filter_list_func_(magnifier_window_, MW_FILTERMODE_EXCLUDE, 1,
                                 &excluded_window_);
  }
}

bool ScreenCapturerWinMagnifier::CaptureImage(const DesktopRect& rect) {
  RTC_DCHECK(magnifier_initialized_);

  // Set the magnifier control to cover the captured rect. The content of the
  // magnifier control will be the captured image.
  BOOL result = SetWindowPos(magnifier_window_, NULL, rect.left(), rect.top(),
                             rect.width(), rect.height(), 0);
  if (!result) {
    RTC_LOG_F(LS_WARNING) << "Failed to call SetWindowPos: " << GetLastError()
                          << ". Rect = {" << rect.left() << ", " << rect.top()
                          << ", " << rect.right() << ", " << rect.bottom()
                          << "}";
    return false;
  }

  magnifier_capture_succeeded_ = false;

  RECT native_rect = {rect.left(), rect.top(), rect.right(), rect.bottom()};

  TlsSetValue(GetTlsIndex(), this);
  // OnCaptured will be called via OnMagImageScalingCallback and fill in the
  // frame before set_window_source_func_ returns.
  result = set_window_source_func_(magnifier_window_, native_rect);

  if (!result) {
    RTC_LOG_F(LS_WARNING) << "Failed to call MagSetWindowSource: "
                          << GetLastError() << ". Rect = {" << rect.left()
                          << ", " << rect.top() << ", " << rect.right() << ", "
                          << rect.bottom() << "}";
    return false;
  }

  return magnifier_capture_succeeded_;
}

BOOL ScreenCapturerWinMagnifier::OnMagImageScalingCallback(
    HWND hwnd,
    void* srcdata,
    MAGIMAGEHEADER srcheader,
    void* destdata,
    MAGIMAGEHEADER destheader,
    RECT unclipped,
    RECT clipped,
    HRGN dirty) {
  ScreenCapturerWinMagnifier* owner =
      reinterpret_cast<ScreenCapturerWinMagnifier*>(TlsGetValue(GetTlsIndex()));
  TlsSetValue(GetTlsIndex(), nullptr);
  owner->OnCaptured(srcdata, srcheader);

  return TRUE;
}

// TODO(zijiehe): These functions are available on Windows Vista or upper, so we
// do not need to use LoadLibrary and GetProcAddress anymore. Use regular
// include and function calls instead of a dynamical loaded library.
bool ScreenCapturerWinMagnifier::InitializeMagnifier() {
  RTC_DCHECK(!magnifier_initialized_);

  if (GetSystemMetrics(SM_CMONITORS) != 1) {
    // Do not try to use the magnifier in multi-screen setup (where the API
    // crashes sometimes).
    RTC_LOG_F(LS_WARNING) << "Magnifier capturer cannot work on multi-screen "
                             "system.";
    return false;
  }

  desktop_dc_ = GetDC(nullptr);

  mag_lib_handle_ = LoadLibraryW(L"Magnification.dll");
  if (!mag_lib_handle_)
    return false;

  // Initialize Magnification API function pointers.
  mag_initialize_func_ = reinterpret_cast<MagInitializeFunc>(
      GetProcAddress(mag_lib_handle_, "MagInitialize"));
  mag_uninitialize_func_ = reinterpret_cast<MagUninitializeFunc>(
      GetProcAddress(mag_lib_handle_, "MagUninitialize"));
  set_window_source_func_ = reinterpret_cast<MagSetWindowSourceFunc>(
      GetProcAddress(mag_lib_handle_, "MagSetWindowSource"));
  set_window_filter_list_func_ = reinterpret_cast<MagSetWindowFilterListFunc>(
      GetProcAddress(mag_lib_handle_, "MagSetWindowFilterList"));
  set_image_scaling_callback_func_ =
      reinterpret_cast<MagSetImageScalingCallbackFunc>(
          GetProcAddress(mag_lib_handle_, "MagSetImageScalingCallback"));

  if (!mag_initialize_func_ || !mag_uninitialize_func_ ||
      !set_window_source_func_ || !set_window_filter_list_func_ ||
      !set_image_scaling_callback_func_) {
    RTC_LOG_F(LS_WARNING) << "Failed to initialize ScreenCapturerWinMagnifier: "
                             "library functions missing.";
    return false;
  }

  BOOL result = mag_initialize_func_();
  if (!result) {
    RTC_LOG_F(LS_WARNING) << "Failed to initialize ScreenCapturerWinMagnifier: "
                             "error from MagInitialize "
                          << GetLastError();
    return false;
  }

  HMODULE hInstance = nullptr;
  result =
      GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS |
                             GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                         reinterpret_cast<char*>(&DefWindowProc), &hInstance);
  if (!result) {
    mag_uninitialize_func_();
    RTC_LOG_F(LS_WARNING) << "Failed to initialize ScreenCapturerWinMagnifier: "
                             "error from GetModulehandleExA "
                          << GetLastError();
    return false;
  }

  // Register the host window class. See the MSDN documentation of the
  // Magnification API for more infomation.
  WNDCLASSEXW wcex = {};
  wcex.cbSize = sizeof(WNDCLASSEX);
  wcex.lpfnWndProc = &DefWindowProc;
  wcex.hInstance = hInstance;
  wcex.hCursor = LoadCursor(nullptr, IDC_ARROW);
  wcex.lpszClassName = kMagnifierHostClass;

  // Ignore the error which may happen when the class is already registered.
  RegisterClassExW(&wcex);

  // Create the host window.
  host_window_ =
      CreateWindowExW(WS_EX_LAYERED, kMagnifierHostClass, kHostWindowName, 0, 0,
                      0, 0, 0, nullptr, nullptr, hInstance, nullptr);
  if (!host_window_) {
    mag_uninitialize_func_();
    RTC_LOG_F(LS_WARNING) << "Failed to initialize ScreenCapturerWinMagnifier: "
                             "error from creating host window "
                          << GetLastError();
    return false;
  }

  // Create the magnifier control.
  magnifier_window_ = CreateWindowW(kMagnifierWindowClass, kMagnifierWindowName,
                                    WS_CHILD | WS_VISIBLE, 0, 0, 0, 0,
                                    host_window_, nullptr, hInstance, nullptr);
  if (!magnifier_window_) {
    mag_uninitialize_func_();
    RTC_LOG_F(LS_WARNING) << "Failed to initialize ScreenCapturerWinMagnifier: "
                             "error from creating magnifier window "
                          << GetLastError();
    return false;
  }

  // Hide the host window.
  ShowWindow(host_window_, SW_HIDE);

  // Set the scaling callback to receive captured image.
  result = set_image_scaling_callback_func_(
      magnifier_window_,
      &ScreenCapturerWinMagnifier::OnMagImageScalingCallback);
  if (!result) {
    mag_uninitialize_func_();
    RTC_LOG_F(LS_WARNING) << "Failed to initialize ScreenCapturerWinMagnifier: "
                             "error from MagSetImageScalingCallback "
                          << GetLastError();
    return false;
  }

  if (excluded_window_) {
    result = set_window_filter_list_func_(
        magnifier_window_, MW_FILTERMODE_EXCLUDE, 1, &excluded_window_);
    if (!result) {
      mag_uninitialize_func_();
      RTC_LOG_F(LS_WARNING)
          << "Failed to initialize ScreenCapturerWinMagnifier: "
             "error from MagSetWindowFilterList "
          << GetLastError();
      return false;
    }
  }

  magnifier_initialized_ = true;
  return true;
}

void ScreenCapturerWinMagnifier::OnCaptured(void* data,
                                            const MAGIMAGEHEADER& header) {
  DesktopFrame* current_frame = queue_.current_frame();

  // Verify the format.
  // TODO(jiayl): support capturing sources with pixel formats other than RGBA.
  int captured_bytes_per_pixel = header.cbSize / header.width / header.height;
  if (header.format != GUID_WICPixelFormat32bppRGBA ||
      header.width != static_cast<UINT>(current_frame->size().width()) ||
      header.height != static_cast<UINT>(current_frame->size().height()) ||
      header.stride != static_cast<UINT>(current_frame->stride()) ||
      captured_bytes_per_pixel != DesktopFrame::kBytesPerPixel) {
    RTC_LOG_F(LS_WARNING)
        << "Output format does not match the captured format: "
           "width = "
        << header.width
        << ", "
           "height = "
        << header.height
        << ", "
           "stride = "
        << header.stride
        << ", "
           "bpp = "
        << captured_bytes_per_pixel
        << ", "
           "pixel format RGBA ? "
        << (header.format == GUID_WICPixelFormat32bppRGBA) << ".";
    return;
  }

  // Copy the data into the frame.
  current_frame->CopyPixelsFrom(
      reinterpret_cast<uint8_t*>(data), header.stride,
      DesktopRect::MakeXYWH(0, 0, header.width, header.height));

  magnifier_capture_succeeded_ = true;
}

void ScreenCapturerWinMagnifier::CreateCurrentFrameIfNecessary(
    const DesktopSize& size) {
  // If the current buffer is from an older generation then allocate a new one.
  // Note that we can't reallocate other buffers at this point, since the caller
  // may still be reading from them.
  if (!queue_.current_frame() || !queue_.current_frame()->size().equals(size)) {
    std::unique_ptr<DesktopFrame> frame =
        shared_memory_factory_
            ? SharedMemoryDesktopFrame::Create(size,
                                               shared_memory_factory_.get())
            : std::unique_ptr<DesktopFrame>(new BasicDesktopFrame(size));
    queue_.ReplaceCurrentFrame(SharedDesktopFrame::Wrap(std::move(frame)));
  }
}

}  // namespace webrtc

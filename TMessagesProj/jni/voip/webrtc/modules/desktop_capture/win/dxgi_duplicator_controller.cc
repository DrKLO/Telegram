/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/win/dxgi_duplicator_controller.h"

#include <windows.h>

#include <algorithm>
#include <string>

#include "modules/desktop_capture/desktop_capture_types.h"
#include "modules/desktop_capture/win/dxgi_frame.h"
#include "modules/desktop_capture/win/screen_capture_utils.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/sleep.h"

namespace webrtc {

namespace {

constexpr DWORD kInvalidSessionId = 0xFFFFFFFF;

DWORD GetCurrentSessionId() {
  DWORD session_id = kInvalidSessionId;
  if (!::ProcessIdToSessionId(::GetCurrentProcessId(), &session_id)) {
    RTC_LOG(LS_WARNING)
        << "Failed to retrieve current session Id, current binary "
           "may not have required priviledge.";
  }
  return session_id;
}

bool IsConsoleSession() {
  return WTSGetActiveConsoleSessionId() == GetCurrentSessionId();
}

}  // namespace

// static
std::string DxgiDuplicatorController::ResultName(
    DxgiDuplicatorController::Result result) {
  switch (result) {
    case Result::SUCCEEDED:
      return "Succeeded";
    case Result::UNSUPPORTED_SESSION:
      return "Unsupported session";
    case Result::FRAME_PREPARE_FAILED:
      return "Frame preparation failed";
    case Result::INITIALIZATION_FAILED:
      return "Initialization failed";
    case Result::DUPLICATION_FAILED:
      return "Duplication failed";
    case Result::INVALID_MONITOR_ID:
      return "Invalid monitor id";
    default:
      return "Unknown error";
  }
}

// static
rtc::scoped_refptr<DxgiDuplicatorController>
DxgiDuplicatorController::Instance() {
  // The static instance won't be deleted to ensure it can be used by other
  // threads even during program exiting.
  static DxgiDuplicatorController* instance = new DxgiDuplicatorController();
  return rtc::scoped_refptr<DxgiDuplicatorController>(instance);
}

// static
bool DxgiDuplicatorController::IsCurrentSessionSupported() {
  DWORD current_session_id = GetCurrentSessionId();
  return current_session_id != kInvalidSessionId && current_session_id != 0;
}

DxgiDuplicatorController::DxgiDuplicatorController() : refcount_(0) {}

void DxgiDuplicatorController::AddRef() {
  int refcount = (++refcount_);
  RTC_DCHECK(refcount > 0);
}

void DxgiDuplicatorController::Release() {
  int refcount = (--refcount_);
  RTC_DCHECK(refcount >= 0);
  if (refcount == 0) {
    RTC_LOG(LS_WARNING) << "Count of references reaches zero, "
                           "DxgiDuplicatorController will be unloaded.";
    Unload();
  }
}

bool DxgiDuplicatorController::IsSupported() {
  MutexLock lock(&mutex_);
  return Initialize();
}

bool DxgiDuplicatorController::RetrieveD3dInfo(D3dInfo* info) {
  bool result = false;
  {
    MutexLock lock(&mutex_);
    result = Initialize();
    *info = d3d_info_;
  }
  if (!result) {
    RTC_LOG(LS_WARNING) << "Failed to initialize DXGI components, the D3dInfo "
                           "retrieved may not accurate or out of date.";
  }
  return result;
}

DxgiDuplicatorController::Result DxgiDuplicatorController::Duplicate(
    DxgiFrame* frame) {
  return DoDuplicate(frame, -1);
}

DxgiDuplicatorController::Result DxgiDuplicatorController::DuplicateMonitor(
    DxgiFrame* frame,
    int monitor_id) {
  RTC_DCHECK_GE(monitor_id, 0);
  return DoDuplicate(frame, monitor_id);
}

DesktopVector DxgiDuplicatorController::system_dpi() {
  MutexLock lock(&mutex_);
  if (Initialize()) {
    return system_dpi_;
  }
  return DesktopVector();
}

int DxgiDuplicatorController::ScreenCount() {
  MutexLock lock(&mutex_);
  if (Initialize()) {
    return ScreenCountUnlocked();
  }
  return 0;
}

bool DxgiDuplicatorController::GetDeviceNames(
    std::vector<std::string>* output) {
  MutexLock lock(&mutex_);
  if (Initialize()) {
    GetDeviceNamesUnlocked(output);
    return true;
  }
  return false;
}

DxgiDuplicatorController::Result DxgiDuplicatorController::DoDuplicate(
    DxgiFrame* frame,
    int monitor_id) {
  RTC_DCHECK(frame);
  MutexLock lock(&mutex_);

  // The dxgi components and APIs do not update the screen resolution without
  // a reinitialization. So we use the GetDC() function to retrieve the screen
  // resolution to decide whether dxgi components need to be reinitialized.
  // If the screen resolution changed, it's very likely the next Duplicate()
  // function call will fail because of a missing monitor or the frame size is
  // not enough to store the output. So we reinitialize dxgi components in-place
  // to avoid a capture failure.
  // But there is no guarantee GetDC() function returns the same resolution as
  // dxgi APIs, we still rely on dxgi components to return the output frame
  // size.
  // TODO(zijiehe): Confirm whether IDXGIOutput::GetDesc() and
  // IDXGIOutputDuplication::GetDesc() can detect the resolution change without
  // reinitialization.
  if (display_configuration_monitor_.IsChanged(frame->source_id_)) {
    Deinitialize();
  }

  if (!Initialize()) {
    if (succeeded_duplications_ == 0 && !IsCurrentSessionSupported()) {
      RTC_LOG(LS_WARNING) << "Current binary is running in session 0. DXGI "
                             "components cannot be initialized.";
      return Result::UNSUPPORTED_SESSION;
    }

    // Cannot initialize COM components now, display mode may be changing.
    return Result::INITIALIZATION_FAILED;
  }

  if (!frame->Prepare(SelectedDesktopSize(monitor_id), monitor_id)) {
    return Result::FRAME_PREPARE_FAILED;
  }

  frame->frame()->mutable_updated_region()->Clear();

  if (DoDuplicateUnlocked(frame->context(), monitor_id, frame->frame())) {
    succeeded_duplications_++;
    return Result::SUCCEEDED;
  }
  if (monitor_id >= ScreenCountUnlocked()) {
    // It's a user error to provide a `monitor_id` larger than screen count. We
    // do not need to deinitialize.
    return Result::INVALID_MONITOR_ID;
  }

  // If the `monitor_id` is valid, but DoDuplicateUnlocked() failed, something
  // must be wrong from capturer APIs. We should Deinitialize().
  Deinitialize();
  return Result::DUPLICATION_FAILED;
}

void DxgiDuplicatorController::Unload() {
  MutexLock lock(&mutex_);
  Deinitialize();
}

void DxgiDuplicatorController::Unregister(const Context* const context) {
  MutexLock lock(&mutex_);
  if (ContextExpired(context)) {
    // The Context has not been setup after a recent initialization, so it
    // should not been registered in duplicators.
    return;
  }
  for (size_t i = 0; i < duplicators_.size(); i++) {
    duplicators_[i].Unregister(&context->contexts[i]);
  }
}

bool DxgiDuplicatorController::Initialize() {
  if (!duplicators_.empty()) {
    return true;
  }

  if (DoInitialize()) {
    return true;
  }
  Deinitialize();
  return false;
}

bool DxgiDuplicatorController::DoInitialize() {
  RTC_DCHECK(desktop_rect_.is_empty());
  RTC_DCHECK(duplicators_.empty());

  d3d_info_.min_feature_level = static_cast<D3D_FEATURE_LEVEL>(0);
  d3d_info_.max_feature_level = static_cast<D3D_FEATURE_LEVEL>(0);

  std::vector<D3dDevice> devices = D3dDevice::EnumDevices();
  if (devices.empty()) {
    RTC_LOG(LS_WARNING) << "No D3dDevice found.";
    return false;
  }

  for (size_t i = 0; i < devices.size(); i++) {
    D3D_FEATURE_LEVEL feature_level =
        devices[i].d3d_device()->GetFeatureLevel();
    if (d3d_info_.max_feature_level == 0 ||
        feature_level > d3d_info_.max_feature_level) {
      d3d_info_.max_feature_level = feature_level;
    }
    if (d3d_info_.min_feature_level == 0 ||
        feature_level < d3d_info_.min_feature_level) {
      d3d_info_.min_feature_level = feature_level;
    }

    DxgiAdapterDuplicator duplicator(devices[i]);
    // There may be several video cards on the system, some of them may not
    // support IDXGOutputDuplication. But they should not impact others from
    // taking effect, so we should continually try other adapters. This usually
    // happens when a non-official virtual adapter is installed on the system.
    if (!duplicator.Initialize()) {
      RTC_LOG(LS_WARNING) << "Failed to initialize DxgiAdapterDuplicator on "
                             "adapter "
                          << i;
      continue;
    }
    RTC_DCHECK(!duplicator.desktop_rect().is_empty());
    duplicators_.push_back(std::move(duplicator));

    desktop_rect_.UnionWith(duplicators_.back().desktop_rect());
  }
  TranslateRect();

  HDC hdc = GetDC(nullptr);
  // Use old DPI value if failed.
  if (hdc) {
    system_dpi_.set(GetDeviceCaps(hdc, LOGPIXELSX),
                    GetDeviceCaps(hdc, LOGPIXELSY));
    ReleaseDC(nullptr, hdc);
  }

  identity_++;

  if (duplicators_.empty()) {
    RTC_LOG(LS_WARNING)
        << "Cannot initialize any DxgiAdapterDuplicator instance.";
  }

  return !duplicators_.empty();
}

void DxgiDuplicatorController::Deinitialize() {
  desktop_rect_ = DesktopRect();
  duplicators_.clear();
  display_configuration_monitor_.Reset();
}

bool DxgiDuplicatorController::ContextExpired(
    const Context* const context) const {
  RTC_DCHECK(context);
  return context->controller_id != identity_ ||
         context->contexts.size() != duplicators_.size();
}

void DxgiDuplicatorController::Setup(Context* context) {
  if (ContextExpired(context)) {
    RTC_DCHECK(context);
    context->contexts.clear();
    context->contexts.resize(duplicators_.size());
    for (size_t i = 0; i < duplicators_.size(); i++) {
      duplicators_[i].Setup(&context->contexts[i]);
    }
    context->controller_id = identity_;
  }
}

bool DxgiDuplicatorController::DoDuplicateUnlocked(Context* context,
                                                   int monitor_id,
                                                   SharedDesktopFrame* target) {
  Setup(context);

  if (!EnsureFrameCaptured(context, target)) {
    return false;
  }

  bool result = false;
  if (monitor_id < 0) {
    // Capture entire screen.
    result = DoDuplicateAll(context, target);
  } else {
    result = DoDuplicateOne(context, monitor_id, target);
  }

  if (result) {
    target->set_dpi(system_dpi_);
    return true;
  }

  return false;
}

bool DxgiDuplicatorController::DoDuplicateAll(Context* context,
                                              SharedDesktopFrame* target) {
  for (size_t i = 0; i < duplicators_.size(); i++) {
    if (!duplicators_[i].Duplicate(&context->contexts[i], target)) {
      return false;
    }
  }
  return true;
}

bool DxgiDuplicatorController::DoDuplicateOne(Context* context,
                                              int monitor_id,
                                              SharedDesktopFrame* target) {
  RTC_DCHECK(monitor_id >= 0);
  for (size_t i = 0; i < duplicators_.size() && i < context->contexts.size();
       i++) {
    if (monitor_id >= duplicators_[i].screen_count()) {
      monitor_id -= duplicators_[i].screen_count();
    } else {
      if (duplicators_[i].DuplicateMonitor(&context->contexts[i], monitor_id,
                                           target)) {
        target->set_top_left(duplicators_[i].ScreenRect(monitor_id).top_left());
        return true;
      }
      return false;
    }
  }
  return false;
}

int64_t DxgiDuplicatorController::GetNumFramesCaptured() const {
  int64_t min = INT64_MAX;
  for (const auto& duplicator : duplicators_) {
    min = std::min(min, duplicator.GetNumFramesCaptured());
  }

  return min;
}

DesktopSize DxgiDuplicatorController::desktop_size() const {
  return desktop_rect_.size();
}

DesktopRect DxgiDuplicatorController::ScreenRect(int id) const {
  RTC_DCHECK(id >= 0);
  for (size_t i = 0; i < duplicators_.size(); i++) {
    if (id >= duplicators_[i].screen_count()) {
      id -= duplicators_[i].screen_count();
    } else {
      return duplicators_[i].ScreenRect(id);
    }
  }
  return DesktopRect();
}

int DxgiDuplicatorController::ScreenCountUnlocked() const {
  int result = 0;
  for (auto& duplicator : duplicators_) {
    result += duplicator.screen_count();
  }
  return result;
}

void DxgiDuplicatorController::GetDeviceNamesUnlocked(
    std::vector<std::string>* output) const {
  RTC_DCHECK(output);
  for (auto& duplicator : duplicators_) {
    for (int i = 0; i < duplicator.screen_count(); i++) {
      output->push_back(duplicator.GetDeviceName(i));
    }
  }
}

DesktopSize DxgiDuplicatorController::SelectedDesktopSize(
    int monitor_id) const {
  if (monitor_id < 0) {
    return desktop_size();
  }

  return ScreenRect(monitor_id).size();
}

bool DxgiDuplicatorController::EnsureFrameCaptured(Context* context,
                                                   SharedDesktopFrame* target) {
  // On a modern system, the FPS / monitor refresh rate is usually larger than
  // or equal to 60. So 17 milliseconds is enough to capture at least one frame.
  const int64_t ms_per_frame = 17;
  // Skip frames to ensure a full frame refresh has occurred and the DXGI
  // machinery is producing frames before this function returns.
  int64_t frames_to_skip = 1;
  // The total time out milliseconds for this function. If we cannot get enough
  // frames during this time interval, this function returns false, and cause
  // the DXGI components to be reinitialized. This usually should not happen
  // unless the system is switching display mode when this function is being
  // called. 500 milliseconds should be enough for ~30 frames.
  const int64_t timeout_ms = 500;

  if (GetNumFramesCaptured() == 0 && !IsConsoleSession()) {
    // When capturing a console session, waiting for a single frame is
    // sufficient to ensure that DXGI output duplication is working. When the
    // session is not attached to the console, it has been observed that DXGI
    // may produce up to 4 frames (typically 1-2 though) before stopping. When
    // this condition occurs, no errors are returned from the output duplication
    // API, it simply appears that nothing is changing on the screen. Thus for
    // detached sessions, we need to capture a few extra frames before we can be
    // confident that output duplication was initialized properly.
    frames_to_skip = 5;
  }

  if (GetNumFramesCaptured() >= frames_to_skip) {
    return true;
  }

  std::unique_ptr<SharedDesktopFrame> fallback_frame;
  SharedDesktopFrame* shared_frame = nullptr;
  if (target->size().width() >= desktop_size().width() &&
      target->size().height() >= desktop_size().height()) {
    // `target` is large enough to cover entire screen, we do not need to use
    // `fallback_frame`.
    shared_frame = target;
  } else {
    fallback_frame = SharedDesktopFrame::Wrap(
        std::unique_ptr<DesktopFrame>(new BasicDesktopFrame(desktop_size())));
    shared_frame = fallback_frame.get();
  }

  const int64_t start_ms = rtc::TimeMillis();
  while (GetNumFramesCaptured() < frames_to_skip) {
    if (!DoDuplicateAll(context, shared_frame)) {
      return false;
    }

    // Calling DoDuplicateAll() may change the number of frames captured.
    if (GetNumFramesCaptured() >= frames_to_skip) {
      break;
    }

    if (rtc::TimeMillis() - start_ms > timeout_ms) {
      RTC_LOG(LS_ERROR) << "Failed to capture " << frames_to_skip
                        << " frames "
                           "within "
                        << timeout_ms << " milliseconds.";
      return false;
    }

    // Sleep `ms_per_frame` before attempting to capture the next frame to
    // ensure the video adapter has time to update the screen.
    webrtc::SleepMs(ms_per_frame);
  }
  return true;
}

void DxgiDuplicatorController::TranslateRect() {
  const DesktopVector position =
      DesktopVector().subtract(desktop_rect_.top_left());
  desktop_rect_.Translate(position);
  for (auto& duplicator : duplicators_) {
    duplicator.TranslateRect(position);
  }
}

}  // namespace webrtc

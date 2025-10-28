/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/linux/wayland/base_capturer_pipewire.h"

#include "modules/desktop_capture/desktop_capture_options.h"
#include "modules/desktop_capture/desktop_capturer.h"
#include "modules/desktop_capture/linux/wayland/restore_token_manager.h"
#include "modules/portal/pipewire_utils.h"
#include "modules/portal/xdg_desktop_portal_utils.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"

namespace webrtc {

namespace {

using xdg_portal::RequestResponse;
using xdg_portal::ScreenCapturePortalInterface;
using xdg_portal::SessionDetails;

}  // namespace

// static
bool BaseCapturerPipeWire::IsSupported() {
  // Unfortunately, the best way we have to check if PipeWire is available is
  // to try to initialize it.
  // InitializePipeWire should prevent us from repeatedly initializing PipeWire,
  // but we also don't really expect support to change without the application
  // restarting.
  static bool supported =
      DesktopCapturer::IsRunningUnderWayland() && InitializePipeWire();
  return supported;
}

BaseCapturerPipeWire::BaseCapturerPipeWire(const DesktopCaptureOptions& options,
                                           CaptureType type)
    : BaseCapturerPipeWire(options,
                           std::make_unique<ScreenCastPortal>(type, this)) {
  is_screencast_portal_ = true;
}

BaseCapturerPipeWire::BaseCapturerPipeWire(
    const DesktopCaptureOptions& options,
    std::unique_ptr<ScreenCapturePortalInterface> portal)
    : options_(options),
      is_screencast_portal_(false),
      portal_(std::move(portal)) {
  source_id_ = RestoreTokenManager::GetInstance().GetUnusedId();
  options_.screencast_stream()->SetUseDamageRegion(
      options_.pipewire_use_damage_region());
}

BaseCapturerPipeWire::~BaseCapturerPipeWire() {
  options_.screencast_stream()->StopScreenCastStream();
}

void BaseCapturerPipeWire::OnScreenCastRequestResult(RequestResponse result,
                                                     uint32_t stream_node_id,
                                                     int fd) {
  is_portal_open_ = false;

  // Reset the value of capturer_failed_ in case we succeed below. If we fail,
  // then it'll set it to the right value again soon enough.
  capturer_failed_ = false;
  if (result != RequestResponse::kSuccess ||
      !options_.screencast_stream()->StartScreenCastStream(
          stream_node_id, fd, options_.get_width(), options_.get_height(),
          options_.prefer_cursor_embedded(),
          send_frames_immediately_ ? callback_ : nullptr)) {
    capturer_failed_ = true;
    RTC_LOG(LS_ERROR) << "ScreenCastPortal failed: "
                      << static_cast<uint>(result);
  } else if (ScreenCastPortal* screencast_portal = GetScreenCastPortal()) {
    if (!screencast_portal->RestoreToken().empty()) {
      const SourceId token_id =
          selected_source_id_ ? selected_source_id_ : source_id_;
      RestoreTokenManager::GetInstance().AddToken(
          token_id, screencast_portal->RestoreToken());
    }
  }

  if (!delegated_source_list_observer_)
    return;

  switch (result) {
    case RequestResponse::kUnknown:
      RTC_DCHECK_NOTREACHED();
      break;
    case RequestResponse::kSuccess:
      delegated_source_list_observer_->OnSelection();
      break;
    case RequestResponse::kUserCancelled:
      delegated_source_list_observer_->OnCancelled();
      break;
    case RequestResponse::kError:
      delegated_source_list_observer_->OnError();
      break;
  }
}

void BaseCapturerPipeWire::OnScreenCastSessionClosed() {
  if (!capturer_failed_) {
    options_.screencast_stream()->StopScreenCastStream();
  }
}

void BaseCapturerPipeWire::UpdateResolution(uint32_t width, uint32_t height) {
  if (!capturer_failed_) {
    options_.screencast_stream()->UpdateScreenCastStreamResolution(width,
                                                                   height);
  }
}

void BaseCapturerPipeWire::SetMaxFrameRate(uint32_t max_frame_rate) {
  if (!capturer_failed_) {
    options_.screencast_stream()->UpdateScreenCastStreamFrameRate(
        max_frame_rate);
  }
}

void BaseCapturerPipeWire::Start(Callback* callback) {
  RTC_DCHECK(!callback_);
  RTC_DCHECK(callback);

  callback_ = callback;

  if (ScreenCastPortal* screencast_portal = GetScreenCastPortal()) {
    screencast_portal->SetPersistMode(
        ScreenCastPortal::PersistMode::kTransient);
    if (selected_source_id_) {
      screencast_portal->SetRestoreToken(
          RestoreTokenManager::GetInstance().GetToken(selected_source_id_));
    }
  }

  is_portal_open_ = true;
  portal_->Start();
}

void BaseCapturerPipeWire::CaptureFrame() {
  TRACE_EVENT0("webrtc", "BaseCapturerPipeWire::CaptureFrame");
  if (capturer_failed_) {
    // This could be recoverable if the source list is re-summoned; but for our
    // purposes this is fine, since it requires intervention to resolve and
    // essentially starts a new capture.
    callback_->OnCaptureResult(Result::ERROR_PERMANENT, nullptr);
    return;
  }

  int64_t capture_start_time_nanos = rtc::TimeNanos();
  std::unique_ptr<DesktopFrame> frame =
      options_.screencast_stream()->CaptureFrame();

  if (!frame || !frame->data()) {
    callback_->OnCaptureResult(Result::ERROR_TEMPORARY, nullptr);
    return;
  }

  // TODO(julien.isorce): http://crbug.com/945468. Set the icc profile on
  // the frame, see ScreenCapturerX11::CaptureFrame.

  frame->set_capturer_id(DesktopCapturerId::kWaylandCapturerLinux);
  frame->set_capture_time_ms((rtc::TimeNanos() - capture_start_time_nanos) /
                             rtc::kNumNanosecsPerMillisec);
  callback_->OnCaptureResult(Result::SUCCESS, std::move(frame));
}

bool BaseCapturerPipeWire::GetSourceList(SourceList* sources) {
  RTC_DCHECK(sources->size() == 0);
  // List of available screens is already presented by the xdg-desktop-portal,
  // so we just need a (valid) source id for any callers to pass around, even
  // though it doesn't mean anything to us. Until the user selects a source in
  // xdg-desktop-portal we'll just end up returning empty frames. Note that "0"
  // is often treated as a null/placeholder id, so we shouldn't use that.
  // TODO(https://crbug.com/1297671): Reconsider type of ID when plumbing
  // token that will enable stream re-use.
  sources->push_back({source_id_});
  return true;
}

bool BaseCapturerPipeWire::SelectSource(SourceId id) {
  // Screen selection is handled by the xdg-desktop-portal.
  selected_source_id_ = id;
  return true;
}

DelegatedSourceListController*
BaseCapturerPipeWire::GetDelegatedSourceListController() {
  return this;
}

void BaseCapturerPipeWire::Observe(Observer* observer) {
  RTC_DCHECK(!delegated_source_list_observer_ || !observer);
  delegated_source_list_observer_ = observer;
}

void BaseCapturerPipeWire::EnsureVisible() {
  RTC_DCHECK(callback_);
  if (is_portal_open_)
    return;

  // Clear any previously selected state/capture
  portal_->Stop();
  options_.screencast_stream()->StopScreenCastStream();

  // Get a new source id to reflect that the source has changed.
  source_id_ = RestoreTokenManager::GetInstance().GetUnusedId();

  is_portal_open_ = true;
  portal_->Start();
}

void BaseCapturerPipeWire::EnsureHidden() {
  if (!is_portal_open_)
    return;

  is_portal_open_ = false;
  portal_->Stop();
}

SessionDetails BaseCapturerPipeWire::GetSessionDetails() {
  return portal_->GetSessionDetails();
}

ScreenCastPortal* BaseCapturerPipeWire::GetScreenCastPortal() {
  return is_screencast_portal_ ? static_cast<ScreenCastPortal*>(portal_.get())
                               : nullptr;
}

void BaseCapturerPipeWire::SendFramesImmediately(bool send_frames_immediately) {
  send_frames_immediately_ = send_frames_immediately;
}

}  // namespace webrtc

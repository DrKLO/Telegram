/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/full_screen_window_detector.h"
#include "modules/desktop_capture/full_screen_application_handler.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

FullScreenWindowDetector::FullScreenWindowDetector(
    ApplicationHandlerFactory application_handler_factory)
    : application_handler_factory_(application_handler_factory),
      last_update_time_ms_(0),
      previous_source_id_(0),
      no_handler_source_id_(0) {}

DesktopCapturer::SourceId FullScreenWindowDetector::FindFullScreenWindow(
    DesktopCapturer::SourceId original_source_id) {
  if (app_handler_ == nullptr ||
      app_handler_->GetSourceId() != original_source_id) {
    return 0;
  }
  return app_handler_->FindFullScreenWindow(window_list_, last_update_time_ms_);
}

void FullScreenWindowDetector::UpdateWindowListIfNeeded(
    DesktopCapturer::SourceId original_source_id,
    rtc::FunctionView<bool(DesktopCapturer::SourceList*)> get_sources) {
  const bool skip_update = previous_source_id_ != original_source_id;
  previous_source_id_ = original_source_id;

  // Here is an attempt to avoid redundant creating application handler in case
  // when an instance of WindowCapturer is used to generate a thumbnail to show
  // in picker by calling SelectSource and CaptureFrame for every available
  // source.
  if (skip_update) {
    return;
  }

  CreateApplicationHandlerIfNeeded(original_source_id);
  if (app_handler_ == nullptr) {
    // There is no FullScreenApplicationHandler specific for
    // current application
    return;
  }

  constexpr int64_t kUpdateIntervalMs = 500;

  if ((rtc::TimeMillis() - last_update_time_ms_) <= kUpdateIntervalMs) {
    return;
  }

  DesktopCapturer::SourceList window_list;
  if (get_sources(&window_list)) {
    last_update_time_ms_ = rtc::TimeMillis();
    window_list_.swap(window_list);
  }
}

void FullScreenWindowDetector::CreateApplicationHandlerIfNeeded(
    DesktopCapturer::SourceId source_id) {
  if (no_handler_source_id_ == source_id) {
    return;
  }

  if (app_handler_ == nullptr || app_handler_->GetSourceId() != source_id) {
    app_handler_ = application_handler_factory_
                       ? application_handler_factory_(source_id)
                       : nullptr;
  }

  if (app_handler_ == nullptr) {
    no_handler_source_id_ = source_id;
  }
}

}  // namespace webrtc

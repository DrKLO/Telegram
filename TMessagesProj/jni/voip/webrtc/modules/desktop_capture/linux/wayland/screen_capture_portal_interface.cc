/*
 *  Copyright 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/desktop_capture/linux/wayland/screen_capture_portal_interface.h"

#include <string>

#include "modules/portal/xdg_desktop_portal_utils.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace xdg_portal {

void ScreenCapturePortalInterface::RequestSessionUsingProxy(
    GAsyncResult* result) {
  Scoped<GError> error;
  GDBusProxy* proxy = g_dbus_proxy_new_finish(result, error.receive());
  if (!proxy) {
    // Ignore the error caused by user cancelling the request via `cancellable_`
    if (g_error_matches(error.get(), G_IO_ERROR, G_IO_ERROR_CANCELLED))
      return;
    RTC_LOG(LS_ERROR) << "Failed to get a proxy for the portal: "
                      << error->message;
    OnPortalDone(RequestResponse::kError);
    return;
  }

  RTC_LOG(LS_INFO) << "Successfully created proxy for the portal.";
  RequestSession(proxy);
}

void ScreenCapturePortalInterface::OnSessionRequestResult(
    GDBusProxy* proxy,
    GAsyncResult* result) {
  Scoped<GError> error;
  Scoped<GVariant> variant(
      g_dbus_proxy_call_finish(proxy, result, error.receive()));
  if (!variant) {
    // Ignore the error caused by user cancelling the request via `cancellable_`
    if (g_error_matches(error.get(), G_IO_ERROR, G_IO_ERROR_CANCELLED))
      return;
    RTC_LOG(LS_ERROR) << "Failed to request session: " << error->message;
    OnPortalDone(RequestResponse::kError);
    return;
  }

  RTC_LOG(LS_INFO) << "Initializing the session.";

  Scoped<char> handle;
  g_variant_get_child(variant.get(), /*index=*/0, /*format_string=*/"o",
                      &handle);
  if (!handle) {
    RTC_LOG(LS_ERROR) << "Failed to initialize the session.";
    OnPortalDone(RequestResponse::kError);
    return;
  }
}

void ScreenCapturePortalInterface::RegisterSessionClosedSignalHandler(
    const SessionClosedSignalHandler session_close_signal_handler,
    GVariant* parameters,
    GDBusConnection* connection,
    std::string& session_handle,
    guint& session_closed_signal_id) {
  uint32_t portal_response = 2;
  Scoped<GVariant> response_data;
  g_variant_get(parameters, /*format_string=*/"(u@a{sv})", &portal_response,
                response_data.receive());

  if (RequestResponseFromPortalResponse(portal_response) !=
      RequestResponse::kSuccess) {
    RTC_LOG(LS_ERROR) << "Failed to request the session subscription.";
    OnPortalDone(RequestResponse::kError);
    return;
  }

  Scoped<GVariant> g_session_handle(
      g_variant_lookup_value(response_data.get(), /*key=*/"session_handle",
                             /*expected_type=*/nullptr));
  session_handle = g_variant_get_string(
      /*value=*/g_session_handle.get(), /*length=*/nullptr);

  if (session_handle.empty()) {
    RTC_LOG(LS_ERROR) << "Could not get session handle despite valid response";
    OnPortalDone(RequestResponse::kError);
    return;
  }

  session_closed_signal_id = g_dbus_connection_signal_subscribe(
      connection, kDesktopBusName, kSessionInterfaceName, /*member=*/"Closed",
      session_handle.c_str(), /*arg0=*/nullptr, G_DBUS_SIGNAL_FLAGS_NONE,
      session_close_signal_handler, this, /*user_data_free_func=*/nullptr);
}

void ScreenCapturePortalInterface::OnStartRequestResult(GDBusProxy* proxy,
                                                        GAsyncResult* result) {
  Scoped<GError> error;
  Scoped<GVariant> variant(
      g_dbus_proxy_call_finish(proxy, result, error.receive()));
  if (!variant) {
    if (g_error_matches(error.get(), G_IO_ERROR, G_IO_ERROR_CANCELLED))
      return;
    RTC_LOG(LS_ERROR) << "Failed to start the portal session: "
                      << error->message;
    OnPortalDone(RequestResponse::kError);
    return;
  }

  Scoped<char> handle;
  g_variant_get_child(variant.get(), 0, "o", handle.receive());
  if (!handle) {
    RTC_LOG(LS_ERROR) << "Failed to initialize the start portal session.";
    OnPortalDone(RequestResponse::kError);
    return;
  }

  RTC_LOG(LS_INFO) << "Subscribed to the start signal.";
}

}  // namespace xdg_portal
}  // namespace webrtc

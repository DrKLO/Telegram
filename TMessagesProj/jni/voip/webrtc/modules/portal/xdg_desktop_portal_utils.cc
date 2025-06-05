/*
 *  Copyright 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/portal/xdg_desktop_portal_utils.h"

#include <string>

#include "absl/strings/string_view.h"
#include "modules/portal/scoped_glib.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace xdg_portal {

std::string RequestResponseToString(RequestResponse request) {
  switch (request) {
    case RequestResponse::kUnknown:
      return "kUnknown";
    case RequestResponse::kSuccess:
      return "kSuccess";
    case RequestResponse::kUserCancelled:
      return "kUserCancelled";
    case RequestResponse::kError:
      return "kError";
    default:
      return "Uknown";
  }
}

RequestResponse RequestResponseFromPortalResponse(uint32_t portal_response) {
  // See:
  //  https://docs.flatpak.org/en/latest/portal-api-reference.html#gdbus-signal-org-freedesktop-portal-Request.Response
  switch (portal_response) {
    case 0:
      return RequestResponse::kSuccess;
    case 1:
      return RequestResponse::kUserCancelled;
    case 2:
      return RequestResponse::kError;
    default:
      return RequestResponse::kUnknown;
  }
}

std::string PrepareSignalHandle(absl::string_view token,
                                GDBusConnection* connection) {
  Scoped<char> sender(
      g_strdup(g_dbus_connection_get_unique_name(connection) + 1));
  for (int i = 0; sender.get()[i]; ++i) {
    if (sender.get()[i] == '.') {
      sender.get()[i] = '_';
    }
  }
  const char* handle =
      g_strconcat(kDesktopRequestObjectPath, "/", sender.get(), "/",
                  std::string(token).c_str(), /*end of varargs*/ nullptr);
  return handle;
}

uint32_t SetupRequestResponseSignal(absl::string_view object_path,
                                    const GDBusSignalCallback callback,
                                    gpointer user_data,
                                    GDBusConnection* connection) {
  return g_dbus_connection_signal_subscribe(
      connection, kDesktopBusName, kRequestInterfaceName, "Response",
      std::string(object_path).c_str(), /*arg0=*/nullptr,
      G_DBUS_SIGNAL_FLAGS_NO_MATCH_RULE, callback, user_data,
      /*user_data_free_func=*/nullptr);
}

void RequestSessionProxy(absl::string_view interface_name,
                         const ProxyRequestCallback proxy_request_callback,
                         GCancellable* cancellable,
                         gpointer user_data) {
  g_dbus_proxy_new_for_bus(
      G_BUS_TYPE_SESSION, G_DBUS_PROXY_FLAGS_NONE, /*info=*/nullptr,
      kDesktopBusName, kDesktopObjectPath, std::string(interface_name).c_str(),
      cancellable,
      reinterpret_cast<GAsyncReadyCallback>(proxy_request_callback), user_data);
}

void SetupSessionRequestHandlers(
    absl::string_view portal_prefix,
    const SessionRequestCallback session_request_callback,
    const SessionRequestResponseSignalHandler request_response_signale_handler,
    GDBusConnection* connection,
    GDBusProxy* proxy,
    GCancellable* cancellable,
    std::string& portal_handle,
    guint& session_request_signal_id,
    gpointer user_data) {
  GVariantBuilder builder;
  Scoped<char> variant_string;

  g_variant_builder_init(&builder, G_VARIANT_TYPE_VARDICT);
  variant_string =
      g_strdup_printf("%.*s_session%d", static_cast<int>(portal_prefix.size()),
                      portal_prefix.data(), g_random_int_range(0, G_MAXINT));
  g_variant_builder_add(&builder, "{sv}", "session_handle_token",
                        g_variant_new_string(variant_string.get()));

  variant_string =
      g_strdup_printf("%.*s_%d", static_cast<int>(portal_prefix.size()),
                      portal_prefix.data(), g_random_int_range(0, G_MAXINT));
  g_variant_builder_add(&builder, "{sv}", "handle_token",
                        g_variant_new_string(variant_string.get()));

  portal_handle = PrepareSignalHandle(variant_string.get(), connection);
  session_request_signal_id = SetupRequestResponseSignal(
      portal_handle.c_str(), request_response_signale_handler, user_data,
      connection);

  RTC_LOG(LS_INFO) << "Desktop session requested.";
  g_dbus_proxy_call(
      proxy, "CreateSession", g_variant_new("(a{sv})", &builder),
      G_DBUS_CALL_FLAGS_NONE, /*timeout=*/-1, cancellable,
      reinterpret_cast<GAsyncReadyCallback>(session_request_callback),
      user_data);
}

void StartSessionRequest(
    absl::string_view prefix,
    absl::string_view session_handle,
    const StartRequestResponseSignalHandler signal_handler,
    const SessionStartRequestedHandler session_started_handler,
    GDBusProxy* proxy,
    GDBusConnection* connection,
    GCancellable* cancellable,
    guint& start_request_signal_id,
    std::string& start_handle,
    gpointer user_data) {
  GVariantBuilder builder;
  Scoped<char> variant_string;

  g_variant_builder_init(&builder, G_VARIANT_TYPE_VARDICT);
  variant_string =
      g_strdup_printf("%.*s%d", static_cast<int>(prefix.size()), prefix.data(),
                      g_random_int_range(0, G_MAXINT));
  g_variant_builder_add(&builder, "{sv}", "handle_token",
                        g_variant_new_string(variant_string.get()));

  start_handle = PrepareSignalHandle(variant_string.get(), connection);
  start_request_signal_id = SetupRequestResponseSignal(
      start_handle.c_str(), signal_handler, user_data, connection);

  // "Identifier for the application window", this is Wayland, so not "x11:...".
  const char parent_window[] = "";

  RTC_LOG(LS_INFO) << "Starting the portal session.";
  g_dbus_proxy_call(
      proxy, "Start",
      g_variant_new("(osa{sv})", std::string(session_handle).c_str(),
                    parent_window, &builder),
      G_DBUS_CALL_FLAGS_NONE, /*timeout=*/-1, cancellable,
      reinterpret_cast<GAsyncReadyCallback>(session_started_handler),
      user_data);
}

void TearDownSession(absl::string_view session_handle,
                     GDBusProxy* proxy,
                     GCancellable* cancellable,
                     GDBusConnection* connection) {
  if (!session_handle.empty()) {
    Scoped<GDBusMessage> message(g_dbus_message_new_method_call(
        kDesktopBusName, std::string(session_handle).c_str(),
        kSessionInterfaceName, "Close"));
    if (message.get()) {
      Scoped<GError> error;
      g_dbus_connection_send_message(connection, message.get(),
                                     G_DBUS_SEND_MESSAGE_FLAGS_NONE,
                                     /*out_serial=*/nullptr, error.receive());
      if (error.get()) {
        RTC_LOG(LS_ERROR) << "Failed to close the session: " << error->message;
      }
    }
  }

  if (cancellable) {
    g_cancellable_cancel(cancellable);
    g_object_unref(cancellable);
  }

  if (proxy) {
    g_object_unref(proxy);
  }
}

}  // namespace xdg_portal
}  // namespace webrtc

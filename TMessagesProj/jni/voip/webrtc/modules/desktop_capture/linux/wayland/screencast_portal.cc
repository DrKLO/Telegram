/*
 *  Copyright 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/linux/wayland/screencast_portal.h"

#include <gio/gunixfdlist.h>
#include <glib-object.h>

#include "modules/portal/scoped_glib.h"
#include "modules/portal/xdg_desktop_portal_utils.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

using xdg_portal::kScreenCastInterfaceName;
using xdg_portal::PrepareSignalHandle;
using xdg_portal::RequestResponse;
using xdg_portal::RequestResponseFromPortalResponse;
using xdg_portal::RequestSessionProxy;
using xdg_portal::SetupRequestResponseSignal;
using xdg_portal::SetupSessionRequestHandlers;
using xdg_portal::StartSessionRequest;
using xdg_portal::TearDownSession;

}  // namespace

// static
ScreenCastPortal::CaptureSourceType ScreenCastPortal::ToCaptureSourceType(
    CaptureType type) {
  switch (type) {
    case CaptureType::kScreen:
      return ScreenCastPortal::CaptureSourceType::kScreen;
    case CaptureType::kWindow:
      return ScreenCastPortal::CaptureSourceType::kWindow;
    case CaptureType::kAnyScreenContent:
      return ScreenCastPortal::CaptureSourceType::kAnyScreenContent;
  }
}

ScreenCastPortal::ScreenCastPortal(CaptureType type, PortalNotifier* notifier)
    : ScreenCastPortal(type,
                       notifier,
                       OnProxyRequested,
                       OnSourcesRequestResponseSignal,
                       this) {}

ScreenCastPortal::ScreenCastPortal(
    CaptureType type,
    PortalNotifier* notifier,
    ProxyRequestResponseHandler proxy_request_response_handler,
    SourcesRequestResponseSignalHandler sources_request_response_signal_handler,
    gpointer user_data,
    bool prefer_cursor_embedded)
    : notifier_(notifier),
      capture_source_type_(ToCaptureSourceType(type)),
      cursor_mode_(prefer_cursor_embedded ? CursorMode::kEmbedded
                                          : CursorMode::kMetadata),
      proxy_request_response_handler_(proxy_request_response_handler),
      sources_request_response_signal_handler_(
          sources_request_response_signal_handler),
      user_data_(user_data) {}

ScreenCastPortal::~ScreenCastPortal() {
  Stop();
}

void ScreenCastPortal::Stop() {
  UnsubscribeSignalHandlers();
  TearDownSession(std::move(session_handle_), proxy_, cancellable_,
                  connection_);
  session_handle_ = "";
  cancellable_ = nullptr;
  proxy_ = nullptr;
  restore_token_ = "";

  if (pw_fd_ != kInvalidPipeWireFd) {
    close(pw_fd_);
    pw_fd_ = kInvalidPipeWireFd;
  }
}

void ScreenCastPortal::UnsubscribeSignalHandlers() {
  if (start_request_signal_id_) {
    g_dbus_connection_signal_unsubscribe(connection_, start_request_signal_id_);
    start_request_signal_id_ = 0;
  }

  if (sources_request_signal_id_) {
    g_dbus_connection_signal_unsubscribe(connection_,
                                         sources_request_signal_id_);
    sources_request_signal_id_ = 0;
  }

  if (session_request_signal_id_) {
    g_dbus_connection_signal_unsubscribe(connection_,
                                         session_request_signal_id_);
    session_request_signal_id_ = 0;
  }
}

void ScreenCastPortal::SetSessionDetails(
    const xdg_portal::SessionDetails& session_details) {
  if (session_details.proxy) {
    proxy_ = session_details.proxy;
    connection_ = g_dbus_proxy_get_connection(proxy_);
  }
  if (session_details.cancellable) {
    cancellable_ = session_details.cancellable;
  }
  if (!session_details.session_handle.empty()) {
    session_handle_ = session_details.session_handle;
  }
  if (session_details.pipewire_stream_node_id) {
    pw_stream_node_id_ = session_details.pipewire_stream_node_id;
  }
}

void ScreenCastPortal::Start() {
  cancellable_ = g_cancellable_new();
  RequestSessionProxy(kScreenCastInterfaceName, proxy_request_response_handler_,
                      cancellable_, this);
}

xdg_portal::SessionDetails ScreenCastPortal::GetSessionDetails() {
  return {};  // No-op
}

void ScreenCastPortal::OnPortalDone(RequestResponse result) {
  notifier_->OnScreenCastRequestResult(result, pw_stream_node_id_, pw_fd_);
  if (result != RequestResponse::kSuccess) {
    Stop();
  }
}

// static
void ScreenCastPortal::OnProxyRequested(GObject* gobject,
                                        GAsyncResult* result,
                                        gpointer user_data) {
  static_cast<ScreenCastPortal*>(user_data)->RequestSessionUsingProxy(result);
}

void ScreenCastPortal::RequestSession(GDBusProxy* proxy) {
  proxy_ = proxy;
  connection_ = g_dbus_proxy_get_connection(proxy_);
  SetupSessionRequestHandlers(
      "webrtc", OnSessionRequested, OnSessionRequestResponseSignal, connection_,
      proxy_, cancellable_, portal_handle_, session_request_signal_id_, this);
}

// static
void ScreenCastPortal::OnSessionRequested(GDBusProxy* proxy,
                                          GAsyncResult* result,
                                          gpointer user_data) {
  static_cast<ScreenCastPortal*>(user_data)->OnSessionRequestResult(proxy,
                                                                    result);
}

// static
void ScreenCastPortal::OnSessionRequestResponseSignal(
    GDBusConnection* connection,
    const char* sender_name,
    const char* object_path,
    const char* interface_name,
    const char* signal_name,
    GVariant* parameters,
    gpointer user_data) {
  ScreenCastPortal* that = static_cast<ScreenCastPortal*>(user_data);
  RTC_DCHECK(that);
  that->RegisterSessionClosedSignalHandler(
      OnSessionClosedSignal, parameters, that->connection_,
      that->session_handle_, that->session_closed_signal_id_);

  // Do not continue if we don't get session_handle back. The call above will
  // already notify the capturer there is a failure, but we would still continue
  // to make following request and crash on that.
  if (!that->session_handle_.empty()) {
    that->SourcesRequest();
  }
}

// static
void ScreenCastPortal::OnSessionClosedSignal(GDBusConnection* connection,
                                             const char* sender_name,
                                             const char* object_path,
                                             const char* interface_name,
                                             const char* signal_name,
                                             GVariant* parameters,
                                             gpointer user_data) {
  ScreenCastPortal* that = static_cast<ScreenCastPortal*>(user_data);
  RTC_DCHECK(that);

  RTC_LOG(LS_INFO) << "Received closed signal from session.";

  that->notifier_->OnScreenCastSessionClosed();

  // Unsubscribe from the signal and free the session handle to avoid calling
  // Session::Close from the destructor since it's already closed
  g_dbus_connection_signal_unsubscribe(that->connection_,
                                       that->session_closed_signal_id_);
}

void ScreenCastPortal::SourcesRequest() {
  GVariantBuilder builder;
  Scoped<char> variant_string;

  g_variant_builder_init(&builder, G_VARIANT_TYPE_VARDICT);
  // We want to record monitor content.
  g_variant_builder_add(
      &builder, "{sv}", "types",
      g_variant_new_uint32(static_cast<uint32_t>(capture_source_type_)));
  // We don't want to allow selection of multiple sources.
  g_variant_builder_add(&builder, "{sv}", "multiple",
                        g_variant_new_boolean(false));

  Scoped<GVariant> cursorModesVariant(
      g_dbus_proxy_get_cached_property(proxy_, "AvailableCursorModes"));
  if (cursorModesVariant.get()) {
    uint32_t modes = 0;
    g_variant_get(cursorModesVariant.get(), "u", &modes);
    // Make request only if this mode is advertised by the portal
    // implementation.
    if (modes & static_cast<uint32_t>(cursor_mode_)) {
      g_variant_builder_add(
          &builder, "{sv}", "cursor_mode",
          g_variant_new_uint32(static_cast<uint32_t>(cursor_mode_)));
    }
  }

  Scoped<GVariant> versionVariant(
      g_dbus_proxy_get_cached_property(proxy_, "version"));
  if (versionVariant.get()) {
    uint32_t version = 0;
    g_variant_get(versionVariant.get(), "u", &version);
    // Make request only if xdg-desktop-portal has required API version
    if (version >= 4) {
      g_variant_builder_add(
          &builder, "{sv}", "persist_mode",
          g_variant_new_uint32(static_cast<uint32_t>(persist_mode_)));
      if (!restore_token_.empty()) {
        g_variant_builder_add(&builder, "{sv}", "restore_token",
                              g_variant_new_string(restore_token_.c_str()));
      }
    }
  }

  variant_string = g_strdup_printf("webrtc%d", g_random_int_range(0, G_MAXINT));
  g_variant_builder_add(&builder, "{sv}", "handle_token",
                        g_variant_new_string(variant_string.get()));

  sources_handle_ = PrepareSignalHandle(variant_string.get(), connection_);
  sources_request_signal_id_ = SetupRequestResponseSignal(
      sources_handle_.c_str(), sources_request_response_signal_handler_,
      user_data_, connection_);

  RTC_LOG(LS_INFO) << "Requesting sources from the screen cast session.";
  g_dbus_proxy_call(
      proxy_, "SelectSources",
      g_variant_new("(oa{sv})", session_handle_.c_str(), &builder),
      G_DBUS_CALL_FLAGS_NONE, /*timeout=*/-1, cancellable_,
      reinterpret_cast<GAsyncReadyCallback>(OnSourcesRequested), this);
}

// static
void ScreenCastPortal::OnSourcesRequested(GDBusProxy* proxy,
                                          GAsyncResult* result,
                                          gpointer user_data) {
  ScreenCastPortal* that = static_cast<ScreenCastPortal*>(user_data);
  RTC_DCHECK(that);

  Scoped<GError> error;
  Scoped<GVariant> variant(
      g_dbus_proxy_call_finish(proxy, result, error.receive()));
  if (!variant) {
    if (g_error_matches(error.get(), G_IO_ERROR, G_IO_ERROR_CANCELLED))
      return;
    RTC_LOG(LS_ERROR) << "Failed to request the sources: " << error->message;
    that->OnPortalDone(RequestResponse::kError);
    return;
  }

  RTC_LOG(LS_INFO) << "Sources requested from the screen cast session.";

  Scoped<char> handle;
  g_variant_get_child(variant.get(), 0, "o", handle.receive());
  if (!handle) {
    RTC_LOG(LS_ERROR) << "Failed to initialize the screen cast session.";
    if (that->sources_request_signal_id_) {
      g_dbus_connection_signal_unsubscribe(that->connection_,
                                           that->sources_request_signal_id_);
      that->sources_request_signal_id_ = 0;
    }
    that->OnPortalDone(RequestResponse::kError);
    return;
  }

  RTC_LOG(LS_INFO) << "Subscribed to sources signal.";
}

// static
void ScreenCastPortal::OnSourcesRequestResponseSignal(
    GDBusConnection* connection,
    const char* sender_name,
    const char* object_path,
    const char* interface_name,
    const char* signal_name,
    GVariant* parameters,
    gpointer user_data) {
  ScreenCastPortal* that = static_cast<ScreenCastPortal*>(user_data);
  RTC_DCHECK(that);

  RTC_LOG(LS_INFO) << "Received sources signal from session.";

  uint32_t portal_response;
  g_variant_get(parameters, "(u@a{sv})", &portal_response, nullptr);
  if (portal_response) {
    RTC_LOG(LS_ERROR)
        << "Failed to select sources for the screen cast session.";
    that->OnPortalDone(RequestResponse::kError);
    return;
  }

  that->StartRequest();
}

void ScreenCastPortal::StartRequest() {
  StartSessionRequest("webrtc", session_handle_, OnStartRequestResponseSignal,
                      OnStartRequested, proxy_, connection_, cancellable_,
                      start_request_signal_id_, start_handle_, this);
}

// static
void ScreenCastPortal::OnStartRequested(GDBusProxy* proxy,
                                        GAsyncResult* result,
                                        gpointer user_data) {
  static_cast<ScreenCastPortal*>(user_data)->OnStartRequestResult(proxy,
                                                                  result);
}

// static
void ScreenCastPortal::OnStartRequestResponseSignal(GDBusConnection* connection,
                                                    const char* sender_name,
                                                    const char* object_path,
                                                    const char* interface_name,
                                                    const char* signal_name,
                                                    GVariant* parameters,
                                                    gpointer user_data) {
  ScreenCastPortal* that = static_cast<ScreenCastPortal*>(user_data);
  RTC_DCHECK(that);

  RTC_LOG(LS_INFO) << "Start signal received.";
  uint32_t portal_response;
  Scoped<GVariant> response_data;
  Scoped<GVariantIter> iter;
  Scoped<char> restore_token;
  g_variant_get(parameters, "(u@a{sv})", &portal_response,
                response_data.receive());
  if (portal_response || !response_data) {
    RTC_LOG(LS_ERROR) << "Failed to start the screen cast session.";
    that->OnPortalDone(RequestResponseFromPortalResponse(portal_response));
    return;
  }

  // Array of PipeWire streams. See
  // https://github.com/flatpak/xdg-desktop-portal/blob/main/data/org.freedesktop.portal.ScreenCast.xml
  // documentation for <method name="Start">.
  if (g_variant_lookup(response_data.get(), "streams", "a(ua{sv})",
                       iter.receive())) {
    Scoped<GVariant> variant;

    while (g_variant_iter_next(iter.get(), "@(ua{sv})", variant.receive())) {
      uint32_t stream_id;
      uint32_t type;
      Scoped<GVariant> options;

      g_variant_get(variant.get(), "(u@a{sv})", &stream_id, options.receive());
      RTC_DCHECK(options.get());

      if (g_variant_lookup(options.get(), "source_type", "u", &type)) {
        that->capture_source_type_ =
            static_cast<ScreenCastPortal::CaptureSourceType>(type);
      }

      that->pw_stream_node_id_ = stream_id;

      break;
    }
  }

  if (g_variant_lookup(response_data.get(), "restore_token", "s",
                       restore_token.receive())) {
    that->restore_token_ = restore_token.get();
  }

  that->OpenPipeWireRemote();
}

uint32_t ScreenCastPortal::pipewire_stream_node_id() {
  return pw_stream_node_id_;
}

void ScreenCastPortal::SetPersistMode(ScreenCastPortal::PersistMode mode) {
  persist_mode_ = mode;
}

void ScreenCastPortal::SetRestoreToken(const std::string& token) {
  restore_token_ = token;
}

std::string ScreenCastPortal::RestoreToken() const {
  return restore_token_;
}

void ScreenCastPortal::OpenPipeWireRemote() {
  GVariantBuilder builder;
  g_variant_builder_init(&builder, G_VARIANT_TYPE_VARDICT);

  RTC_LOG(LS_INFO) << "Opening the PipeWire remote.";

  g_dbus_proxy_call_with_unix_fd_list(
      proxy_, "OpenPipeWireRemote",
      g_variant_new("(oa{sv})", session_handle_.c_str(), &builder),
      G_DBUS_CALL_FLAGS_NONE, /*timeout=*/-1, /*fd_list=*/nullptr, cancellable_,
      reinterpret_cast<GAsyncReadyCallback>(OnOpenPipeWireRemoteRequested),
      this);
}

// static
void ScreenCastPortal::OnOpenPipeWireRemoteRequested(GDBusProxy* proxy,
                                                     GAsyncResult* result,
                                                     gpointer user_data) {
  ScreenCastPortal* that = static_cast<ScreenCastPortal*>(user_data);
  RTC_DCHECK(that);

  Scoped<GError> error;
  Scoped<GUnixFDList> outlist;
  Scoped<GVariant> variant(g_dbus_proxy_call_with_unix_fd_list_finish(
      proxy, outlist.receive(), result, error.receive()));
  if (!variant) {
    if (g_error_matches(error.get(), G_IO_ERROR, G_IO_ERROR_CANCELLED))
      return;
    RTC_LOG(LS_ERROR) << "Failed to open the PipeWire remote: "
                      << error->message;
    that->OnPortalDone(RequestResponse::kError);
    return;
  }

  int32_t index;
  g_variant_get(variant.get(), "(h)", &index);

  that->pw_fd_ = g_unix_fd_list_get(outlist.get(), index, error.receive());

  if (that->pw_fd_ == kInvalidPipeWireFd) {
    RTC_LOG(LS_ERROR) << "Failed to get file descriptor from the list: "
                      << error->message;
    that->OnPortalDone(RequestResponse::kError);
    return;
  }

  that->OnPortalDone(RequestResponse::kSuccess);
}

}  // namespace webrtc

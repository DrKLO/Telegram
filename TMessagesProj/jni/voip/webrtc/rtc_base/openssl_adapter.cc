/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/openssl_adapter.h"

#include <errno.h>
#include <openssl/bio.h>
#include <openssl/err.h>

#include "absl/strings/string_view.h"
#ifdef OPENSSL_IS_BORINGSSL
#include <openssl/pool.h>
#endif
#include <openssl/rand.h>
#include <openssl/x509.h>
#include <string.h>
#include <time.h>

#include <memory>

// Use CRYPTO_BUFFER APIs if available and we have no dependency on X509
// objects.
#if defined(OPENSSL_IS_BORINGSSL) && \
    defined(WEBRTC_EXCLUDE_BUILT_IN_SSL_ROOT_CERTS)
#define WEBRTC_USE_CRYPTO_BUFFER_CALLBACK
#endif

#include "absl/memory/memory.h"
#include "api/units/time_delta.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/openssl.h"
#ifdef OPENSSL_IS_BORINGSSL
#include "rtc_base/boringssl_identity.h"
#else
#include "rtc_base/openssl_identity.h"
#endif
#include "rtc_base/openssl_utility.h"
#include "rtc_base/strings/str_join.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/thread.h"

//////////////////////////////////////////////////////////////////////
// SocketBIO
//////////////////////////////////////////////////////////////////////

static int socket_write(BIO* h, const char* buf, int num);
static int socket_read(BIO* h, char* buf, int size);
static int socket_puts(BIO* h, const char* str);
static long socket_ctrl(BIO* h, int cmd, long arg1, void* arg2);  // NOLINT
static int socket_new(BIO* h);
static int socket_free(BIO* data);

static BIO_METHOD* BIO_socket_method() {
  static BIO_METHOD* methods = [] {
    BIO_METHOD* methods = BIO_meth_new(BIO_TYPE_BIO, "socket");
    BIO_meth_set_write(methods, socket_write);
    BIO_meth_set_read(methods, socket_read);
    BIO_meth_set_puts(methods, socket_puts);
    BIO_meth_set_ctrl(methods, socket_ctrl);
    BIO_meth_set_create(methods, socket_new);
    BIO_meth_set_destroy(methods, socket_free);
    return methods;
  }();
  return methods;
}

static BIO* BIO_new_socket(rtc::Socket* socket) {
  BIO* ret = BIO_new(BIO_socket_method());
  if (ret == nullptr) {
    return nullptr;
  }
  BIO_set_data(ret, socket);
  return ret;
}

static int socket_new(BIO* b) {
  BIO_set_shutdown(b, 0);
  BIO_set_init(b, 1);
  BIO_set_data(b, 0);
  return 1;
}

static int socket_free(BIO* b) {
  if (b == nullptr)
    return 0;
  return 1;
}

static int socket_read(BIO* b, char* out, int outl) {
  if (!out)
    return -1;
  rtc::Socket* socket = static_cast<rtc::Socket*>(BIO_get_data(b));
  BIO_clear_retry_flags(b);
  int result = socket->Recv(out, outl, nullptr);
  if (result > 0) {
    return result;
  } else if (socket->IsBlocking()) {
    BIO_set_retry_read(b);
  }
  return -1;
}

static int socket_write(BIO* b, const char* in, int inl) {
  if (!in)
    return -1;
  rtc::Socket* socket = static_cast<rtc::Socket*>(BIO_get_data(b));
  BIO_clear_retry_flags(b);
  int result = socket->Send(in, inl);
  if (result > 0) {
    return result;
  } else if (socket->IsBlocking()) {
    BIO_set_retry_write(b);
  }
  return -1;
}

static int socket_puts(BIO* b, const char* str) {
  return socket_write(b, str, rtc::checked_cast<int>(strlen(str)));
}

static long socket_ctrl(BIO* b, int cmd, long num, void* ptr) {  // NOLINT
  switch (cmd) {
    case BIO_CTRL_RESET:
      return 0;
    case BIO_CTRL_EOF: {
      rtc::Socket* socket = static_cast<rtc::Socket*>(ptr);
      // 1 means socket closed.
      return (socket->GetState() == rtc::Socket::CS_CLOSED) ? 1 : 0;
    }
    case BIO_CTRL_WPENDING:
    case BIO_CTRL_PENDING:
      return 0;
    case BIO_CTRL_FLUSH:
      return 1;
    default:
      return 0;
  }
}

static void LogSslError() {
  // Walk down the error stack to find the SSL error.
  uint32_t error_code;
  const char* file;
  int line;
  do {
    error_code = ERR_get_error_line(&file, &line);
    if (ERR_GET_LIB(error_code) == ERR_LIB_SSL) {
      RTC_LOG(LS_ERROR) << "ERR_LIB_SSL: " << error_code << ", " << file << ":"
                        << line;
      break;
    }
  } while (error_code != 0);
}

/////////////////////////////////////////////////////////////////////////////
// OpenSSLAdapter
/////////////////////////////////////////////////////////////////////////////

namespace rtc {

using ::webrtc::TimeDelta;

bool OpenSSLAdapter::InitializeSSL() {
  if (!SSL_library_init())
    return false;
#if !defined(ADDRESS_SANITIZER) || !defined(WEBRTC_MAC) || defined(WEBRTC_IOS)
  // Loading the error strings crashes mac_asan.  Omit this debugging aid there.
  SSL_load_error_strings();
#endif
  ERR_load_BIO_strings();
  OpenSSL_add_all_algorithms();
  RAND_poll();
  return true;
}

bool OpenSSLAdapter::CleanupSSL() {
  return true;
}

OpenSSLAdapter::OpenSSLAdapter(Socket* socket,
                               OpenSSLSessionCache* ssl_session_cache,
                               SSLCertificateVerifier* ssl_cert_verifier)
    : SSLAdapter(socket),
      ssl_session_cache_(ssl_session_cache),
      ssl_cert_verifier_(ssl_cert_verifier),
      state_(SSL_NONE),
      role_(SSL_CLIENT),
      ssl_read_needs_write_(false),
      ssl_write_needs_read_(false),
      ssl_(nullptr),
      ssl_ctx_(nullptr),
      ssl_mode_(SSL_MODE_TLS),
      ignore_bad_cert_(false),
      custom_cert_verifier_status_(false) {
  // If a factory is used, take a reference on the factory's SSL_CTX.
  // Otherwise, we'll create our own later.
  // Either way, we'll release our reference via SSL_CTX_free() in Cleanup().
  if (ssl_session_cache_ != nullptr) {
    ssl_ctx_ = ssl_session_cache_->GetSSLContext();
    RTC_DCHECK(ssl_ctx_);
    // Note: if using OpenSSL, requires version 1.1.0 or later.
    SSL_CTX_up_ref(ssl_ctx_);
  }
}

OpenSSLAdapter::~OpenSSLAdapter() {
  Cleanup();
}

void OpenSSLAdapter::SetIgnoreBadCert(bool ignore) {
  ignore_bad_cert_ = ignore;
}

void OpenSSLAdapter::SetAlpnProtocols(const std::vector<std::string>& protos) {
  alpn_protocols_ = protos;
}

void OpenSSLAdapter::SetEllipticCurves(const std::vector<std::string>& curves) {
  elliptic_curves_ = curves;
}

void OpenSSLAdapter::SetMode(SSLMode mode) {
  RTC_DCHECK(!ssl_ctx_);
  RTC_DCHECK(state_ == SSL_NONE);
  ssl_mode_ = mode;
}

void OpenSSLAdapter::SetCertVerifier(
    SSLCertificateVerifier* ssl_cert_verifier) {
  RTC_DCHECK(!ssl_ctx_);
  ssl_cert_verifier_ = ssl_cert_verifier;
}

void OpenSSLAdapter::SetIdentity(std::unique_ptr<SSLIdentity> identity) {
  RTC_DCHECK(!identity_);
#ifdef OPENSSL_IS_BORINGSSL
  identity_ =
      absl::WrapUnique(static_cast<BoringSSLIdentity*>(identity.release()));
#else
  identity_ =
      absl::WrapUnique(static_cast<OpenSSLIdentity*>(identity.release()));
#endif
}

void OpenSSLAdapter::SetRole(SSLRole role) {
  role_ = role;
}

int OpenSSLAdapter::StartSSL(absl::string_view hostname) {
  if (state_ != SSL_NONE)
    return -1;

  ssl_host_name_.assign(hostname.data(), hostname.size());

  if (GetSocket()->GetState() != Socket::CS_CONNECTED) {
    state_ = SSL_WAIT;
    return 0;
  }

  state_ = SSL_CONNECTING;
  if (int err = BeginSSL()) {
    Error("BeginSSL", err, false);
    return err;
  }

  return 0;
}

int OpenSSLAdapter::BeginSSL() {
  RTC_LOG(LS_INFO) << "OpenSSLAdapter::BeginSSL: " << ssl_host_name_;
  RTC_DCHECK(state_ == SSL_CONNECTING);

  // Cleanup action to deal with on error cleanup a bit cleaner.
  EarlyExitCatcher early_exit_catcher(*this);

  // First set up the context. We should either have a factory, with its own
  // pre-existing context, or be running standalone, in which case we will
  // need to create one, and specify `false` to disable session caching.
  if (ssl_session_cache_ == nullptr) {
    RTC_DCHECK(!ssl_ctx_);
    ssl_ctx_ = CreateContext(ssl_mode_, false);
  }

  if (!ssl_ctx_) {
    return -1;
  }

  if (identity_ && !identity_->ConfigureIdentity(ssl_ctx_)) {
    return -1;
  }

  std::unique_ptr<BIO, decltype(&::BIO_free)> bio{BIO_new_socket(GetSocket()),
                                                  ::BIO_free};
  if (!bio) {
    return -1;
  }

  ssl_ = SSL_new(ssl_ctx_);
  if (!ssl_) {
    return -1;
  }

  SSL_set_app_data(ssl_, this);

  // SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER allows different buffers to be passed
  // into SSL_write when a record could only be partially transmitted (and thus
  // requires another call to SSL_write to finish transmission). This allows us
  // to copy the data into our own buffer when this occurs, since the original
  // buffer can't safely be accessed after control exits Send.
  // TODO(deadbeef): Do we want SSL_MODE_ENABLE_PARTIAL_WRITE? It doesn't
  // appear Send handles partial writes properly, though maybe we never notice
  // since we never send more than 16KB at once..
  SSL_set_mode(ssl_, SSL_MODE_ENABLE_PARTIAL_WRITE |
                         SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER);

  // Enable SNI, if a hostname is supplied.
  if (!ssl_host_name_.empty()) {
    SSL_set_tlsext_host_name(ssl_, ssl_host_name_.c_str());

    // Enable session caching, if configured and a hostname is supplied.
    if (ssl_session_cache_ != nullptr) {
      SSL_SESSION* cached = ssl_session_cache_->LookupSession(ssl_host_name_);
      if (cached) {
        if (SSL_set_session(ssl_, cached) == 0) {
          RTC_LOG(LS_WARNING) << "Failed to apply SSL session from cache";
          return -1;
        }

        RTC_LOG(LS_INFO) << "Attempting to resume SSL session to "
                         << ssl_host_name_;
      }
    }
  }

#ifdef OPENSSL_IS_BORINGSSL
  // Set a couple common TLS extensions; even though we don't use them yet.
  SSL_enable_ocsp_stapling(ssl_);
  SSL_enable_signed_cert_timestamps(ssl_);
#endif

  if (!alpn_protocols_.empty()) {
    std::string tls_alpn_string = TransformAlpnProtocols(alpn_protocols_);
    if (!tls_alpn_string.empty()) {
      SSL_set_alpn_protos(
          ssl_, reinterpret_cast<const unsigned char*>(tls_alpn_string.data()),
          rtc::dchecked_cast<unsigned>(tls_alpn_string.size()));
    }
  }

  if (!elliptic_curves_.empty()) {
    SSL_set1_curves_list(ssl_, webrtc::StrJoin(elliptic_curves_, ":").c_str());
  }

  // Now that the initial config is done, transfer ownership of `bio` to the
  // SSL object. If ContinueSSL() fails, the bio will be freed in Cleanup().
  SSL_set_bio(ssl_, bio.get(), bio.get());
  bio.release();

  // Do the connect.
  int err = ContinueSSL();
  if (err != 0) {
    return err;
  }
  early_exit_catcher.disable();
  return 0;
}

int OpenSSLAdapter::ContinueSSL() {
  RTC_DCHECK(state_ == SSL_CONNECTING);

  // Clear the DTLS timer
  timer_.reset();

  int code = (role_ == SSL_CLIENT) ? SSL_connect(ssl_) : SSL_accept(ssl_);
  switch (SSL_get_error(ssl_, code)) {
    case SSL_ERROR_NONE:
      if (!SSLPostConnectionCheck(ssl_, ssl_host_name_)) {
        RTC_LOG(LS_ERROR) << "TLS post connection check failed";
        // make sure we close the socket
        Cleanup();
        // The connect failed so return -1 to shut down the socket
        return -1;
      }

      state_ = SSL_CONNECTED;
      AsyncSocketAdapter::OnConnectEvent(this);
      // TODO(benwright): Refactor this code path.
      // Don't let ourselves go away during the callbacks
      // PRefPtr<OpenSSLAdapter> lock(this);
      // RTC_LOG(LS_INFO) << " -- onStreamReadable";
      // AsyncSocketAdapter::OnReadEvent(this);
      // RTC_LOG(LS_INFO) << " -- onStreamWriteable";
      // AsyncSocketAdapter::OnWriteEvent(this);
      break;

    case SSL_ERROR_WANT_READ:
      RTC_LOG(LS_VERBOSE) << " -- error want read";
      struct timeval timeout;
      if (DTLSv1_get_timeout(ssl_, &timeout)) {
        TimeDelta delay = TimeDelta::Seconds(timeout.tv_sec) +
                          TimeDelta::Micros(timeout.tv_usec);
        Thread::Current()->PostDelayedTask(
            SafeTask(timer_.flag(), [this] { OnTimeout(); }), delay);
      }
      break;

    case SSL_ERROR_WANT_WRITE:
      break;

    case SSL_ERROR_ZERO_RETURN:
    default:
      RTC_LOG(LS_WARNING) << "ContinueSSL -- error " << code;
      return (code != 0) ? code : -1;
  }

  return 0;
}

void OpenSSLAdapter::Error(absl::string_view context, int err, bool signal) {
  RTC_LOG(LS_WARNING) << "OpenSSLAdapter::Error(" << context << ", " << err
                      << ")";
  state_ = SSL_ERROR;
  SetError(err);
  if (signal) {
    AsyncSocketAdapter::OnCloseEvent(this, err);
  }
}

void OpenSSLAdapter::Cleanup() {
  RTC_LOG(LS_INFO) << "OpenSSLAdapter::Cleanup";

  state_ = SSL_NONE;
  ssl_read_needs_write_ = false;
  ssl_write_needs_read_ = false;
  custom_cert_verifier_status_ = false;
  pending_data_.Clear();

  if (ssl_) {
    SSL_free(ssl_);
    ssl_ = nullptr;
  }

  if (ssl_ctx_) {
    SSL_CTX_free(ssl_ctx_);
    ssl_ctx_ = nullptr;
  }
  identity_.reset();

  // Clear the DTLS timer
  timer_.reset();
}

int OpenSSLAdapter::DoSslWrite(const void* pv, size_t cb, int* error) {
  // If we have pending data (that was previously only partially written by
  // SSL_write), we shouldn't be attempting to write anything else.
  RTC_DCHECK(pending_data_.empty() || pv == pending_data_.data());
  RTC_DCHECK(error != nullptr);

  ssl_write_needs_read_ = false;
  int ret = SSL_write(ssl_, pv, checked_cast<int>(cb));
  *error = SSL_get_error(ssl_, ret);
  switch (*error) {
    case SSL_ERROR_NONE:
      // Success!
      return ret;
    case SSL_ERROR_WANT_READ:
      RTC_LOG(LS_INFO) << " -- error want read";
      ssl_write_needs_read_ = true;
      SetError(EWOULDBLOCK);
      break;
    case SSL_ERROR_WANT_WRITE:
      RTC_LOG(LS_INFO) << " -- error want write";
      SetError(EWOULDBLOCK);
      break;
    case SSL_ERROR_ZERO_RETURN:
      SetError(EWOULDBLOCK);
      // do we need to signal closure?
      break;
    case SSL_ERROR_SSL:
      LogSslError();
      Error("SSL_write", ret ? ret : -1, false);
      break;
    default:
      Error("SSL_write", ret ? ret : -1, false);
      break;
  }

  return SOCKET_ERROR;
}

///////////////////////////////////////////////////////////////////////////////
// Socket Implementation
///////////////////////////////////////////////////////////////////////////////

int OpenSSLAdapter::Send(const void* pv, size_t cb) {
  switch (state_) {
    case SSL_NONE:
      return AsyncSocketAdapter::Send(pv, cb);
    case SSL_WAIT:
    case SSL_CONNECTING:
      SetError(ENOTCONN);
      return SOCKET_ERROR;
    case SSL_CONNECTED:
      break;
    case SSL_ERROR:
    default:
      return SOCKET_ERROR;
  }

  int ret;
  int error;

  if (!pending_data_.empty()) {
    ret = DoSslWrite(pending_data_.data(), pending_data_.size(), &error);
    if (ret != static_cast<int>(pending_data_.size())) {
      // We couldn't finish sending the pending data, so we definitely can't
      // send any more data. Return with an EWOULDBLOCK error.
      SetError(EWOULDBLOCK);
      return SOCKET_ERROR;
    }
    // We completed sending the data previously passed into SSL_write! Now
    // we're allowed to send more data.
    pending_data_.Clear();
  }

  // OpenSSL will return an error if we try to write zero bytes
  if (cb == 0) {
    return 0;
  }

  ret = DoSslWrite(pv, cb, &error);

  // If SSL_write fails with SSL_ERROR_WANT_READ or SSL_ERROR_WANT_WRITE, this
  // means the underlying socket is blocked on reading or (more typically)
  // writing. When this happens, OpenSSL requires that the next call to
  // SSL_write uses the same arguments (though, with
  // SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER, the actual buffer pointer may be
  // different).
  //
  // However, after Send exits, we will have lost access to data the user of
  // this class is trying to send, and there's no guarantee that the user of
  // this class will call Send with the same arguements when it fails. So, we
  // buffer the data ourselves. When we know the underlying socket is writable
  // again from OnWriteEvent (or if Send is called again before that happens),
  // we'll retry sending this buffered data.
  if (error == SSL_ERROR_WANT_READ || error == SSL_ERROR_WANT_WRITE) {
    // Shouldn't be able to get to this point if we already have pending data.
    RTC_DCHECK(pending_data_.empty());
    RTC_LOG(LS_WARNING)
        << "SSL_write couldn't write to the underlying socket; buffering data.";
    pending_data_.SetData(static_cast<const uint8_t*>(pv), cb);
    // Since we're taking responsibility for sending this data, return its full
    // size. The user of this class can consider it sent.
    return rtc::dchecked_cast<int>(cb);
  }
  return ret;
}

int OpenSSLAdapter::SendTo(const void* pv,
                           size_t cb,
                           const SocketAddress& addr) {
  if (GetSocket()->GetState() == Socket::CS_CONNECTED &&
      addr == GetSocket()->GetRemoteAddress()) {
    return Send(pv, cb);
  }

  SetError(ENOTCONN);
  return SOCKET_ERROR;
}

int OpenSSLAdapter::Recv(void* pv, size_t cb, int64_t* timestamp) {
  switch (state_) {
    case SSL_NONE:
      return AsyncSocketAdapter::Recv(pv, cb, timestamp);
    case SSL_WAIT:
    case SSL_CONNECTING:
      SetError(ENOTCONN);
      return SOCKET_ERROR;
    case SSL_CONNECTED:
      break;
    case SSL_ERROR:
    default:
      return SOCKET_ERROR;
  }

  // Don't trust OpenSSL with zero byte reads
  if (cb == 0) {
    return 0;
  }

  ssl_read_needs_write_ = false;
  int code = SSL_read(ssl_, pv, checked_cast<int>(cb));
  int error = SSL_get_error(ssl_, code);

  switch (error) {
    case SSL_ERROR_NONE:
      return code;
    case SSL_ERROR_WANT_READ:
      SetError(EWOULDBLOCK);
      break;
    case SSL_ERROR_WANT_WRITE:
      ssl_read_needs_write_ = true;
      SetError(EWOULDBLOCK);
      break;
    case SSL_ERROR_ZERO_RETURN:
      SetError(EWOULDBLOCK);
      // do we need to signal closure?
      break;
    case SSL_ERROR_SSL:
      LogSslError();
      Error("SSL_read", (code ? code : -1), false);
      break;
    default:
      Error("SSL_read", (code ? code : -1), false);
      break;
  }
  return SOCKET_ERROR;
}

int OpenSSLAdapter::RecvFrom(void* pv,
                             size_t cb,
                             SocketAddress* paddr,
                             int64_t* timestamp) {
  if (GetSocket()->GetState() == Socket::CS_CONNECTED) {
    int ret = Recv(pv, cb, timestamp);
    *paddr = GetRemoteAddress();
    return ret;
  }

  SetError(ENOTCONN);
  return SOCKET_ERROR;
}

int OpenSSLAdapter::Close() {
  Cleanup();
  state_ = SSL_NONE;
  return AsyncSocketAdapter::Close();
}

Socket::ConnState OpenSSLAdapter::GetState() const {
  ConnState state = GetSocket()->GetState();
  if ((state == CS_CONNECTED) &&
      ((state_ == SSL_WAIT) || (state_ == SSL_CONNECTING))) {
    state = CS_CONNECTING;
  }
  return state;
}

bool OpenSSLAdapter::IsResumedSession() {
  return (ssl_ && SSL_session_reused(ssl_) == 1);
}

void OpenSSLAdapter::OnTimeout() {
  RTC_LOG(LS_INFO) << "DTLS timeout expired";
  DTLSv1_handle_timeout(ssl_);
  ContinueSSL();
}

void OpenSSLAdapter::OnConnectEvent(Socket* socket) {
  RTC_LOG(LS_INFO) << "OpenSSLAdapter::OnConnectEvent";
  if (state_ != SSL_WAIT) {
    RTC_DCHECK(state_ == SSL_NONE);
    AsyncSocketAdapter::OnConnectEvent(socket);
    return;
  }

  state_ = SSL_CONNECTING;
  if (int err = BeginSSL()) {
    AsyncSocketAdapter::OnCloseEvent(socket, err);
  }
}

void OpenSSLAdapter::OnReadEvent(Socket* socket) {
  if (state_ == SSL_NONE) {
    AsyncSocketAdapter::OnReadEvent(socket);
    return;
  }

  if (state_ == SSL_CONNECTING) {
    if (int err = ContinueSSL()) {
      Error("ContinueSSL", err);
    }
    return;
  }

  if (state_ != SSL_CONNECTED) {
    return;
  }

  // Don't let ourselves go away during the callbacks
  // PRefPtr<OpenSSLAdapter> lock(this); // TODO(benwright): fix this
  if (ssl_write_needs_read_) {
    AsyncSocketAdapter::OnWriteEvent(socket);
  }

  AsyncSocketAdapter::OnReadEvent(socket);
}

void OpenSSLAdapter::OnWriteEvent(Socket* socket) {
  if (state_ == SSL_NONE) {
    AsyncSocketAdapter::OnWriteEvent(socket);
    return;
  }

  if (state_ == SSL_CONNECTING) {
    if (int err = ContinueSSL()) {
      Error("ContinueSSL", err);
    }
    return;
  }

  if (state_ != SSL_CONNECTED) {
    return;
  }

  // Don't let ourselves go away during the callbacks
  // PRefPtr<OpenSSLAdapter> lock(this); // TODO(benwright): fix this

  if (ssl_read_needs_write_) {
    AsyncSocketAdapter::OnReadEvent(socket);
  }

  // If a previous SSL_write failed due to the underlying socket being blocked,
  // this will attempt finishing the write operation.
  if (!pending_data_.empty()) {
    int error;
    if (DoSslWrite(pending_data_.data(), pending_data_.size(), &error) ==
        static_cast<int>(pending_data_.size())) {
      pending_data_.Clear();
    }
  }

  AsyncSocketAdapter::OnWriteEvent(socket);
}

void OpenSSLAdapter::OnCloseEvent(Socket* socket, int err) {
  RTC_LOG(LS_INFO) << "OpenSSLAdapter::OnCloseEvent(" << err << ")";
  AsyncSocketAdapter::OnCloseEvent(socket, err);
}

bool OpenSSLAdapter::SSLPostConnectionCheck(SSL* ssl, absl::string_view host) {
  bool is_valid_cert_name =
      openssl::VerifyPeerCertMatchesHost(ssl, host) &&
      (SSL_get_verify_result(ssl) == X509_V_OK || custom_cert_verifier_status_);

  if (!is_valid_cert_name && ignore_bad_cert_) {
    RTC_DLOG(LS_WARNING) << "Other TLS post connection checks failed. "
                            "ignore_bad_cert_ set to true. Overriding name "
                            "verification failure!";
    is_valid_cert_name = true;
  }
  return is_valid_cert_name;
}

void OpenSSLAdapter::SSLInfoCallback(const SSL* s, int where, int value) {
  std::string type;
  bool info_log = false;
  bool alert_log = false;
  switch (where) {
    case SSL_CB_EXIT:
      info_log = true;
      type = "exit";
      break;
    case SSL_CB_ALERT:
      alert_log = true;
      type = "alert";
      break;
    case SSL_CB_READ_ALERT:
      alert_log = true;
      type = "read_alert";
      break;
    case SSL_CB_WRITE_ALERT:
      alert_log = true;
      type = "write_alert";
      break;
    case SSL_CB_ACCEPT_LOOP:
      info_log = true;
      type = "accept_loop";
      break;
    case SSL_CB_ACCEPT_EXIT:
      info_log = true;
      type = "accept_exit";
      break;
    case SSL_CB_CONNECT_LOOP:
      info_log = true;
      type = "connect_loop";
      break;
    case SSL_CB_CONNECT_EXIT:
      info_log = true;
      type = "connect_exit";
      break;
    case SSL_CB_HANDSHAKE_START:
      info_log = true;
      type = "handshake_start";
      break;
    case SSL_CB_HANDSHAKE_DONE:
      info_log = true;
      type = "handshake_done";
      break;
    case SSL_CB_LOOP:
    case SSL_CB_READ:
    case SSL_CB_WRITE:
    default:
      break;
  }

  if (info_log) {
    RTC_LOG(LS_INFO) << type << " " << SSL_state_string_long(s);
  }
  if (alert_log) {
    RTC_LOG(LS_WARNING) << type << " " << SSL_alert_type_string_long(value)
                        << " " << SSL_alert_desc_string_long(value) << " "
                        << SSL_state_string_long(s);
  }
}

#ifdef WEBRTC_USE_CRYPTO_BUFFER_CALLBACK
// static
enum ssl_verify_result_t OpenSSLAdapter::SSLVerifyCallback(SSL* ssl,
                                                           uint8_t* out_alert) {
  // Get our stream pointer from the SSL context.
  OpenSSLAdapter* stream =
      reinterpret_cast<OpenSSLAdapter*>(SSL_get_app_data(ssl));

  ssl_verify_result_t ret = stream->SSLVerifyInternal(ssl, out_alert);

  // Should only be used for debugging and development.
  if (ret != ssl_verify_ok && stream->ignore_bad_cert_) {
    RTC_DLOG(LS_WARNING) << "Ignoring cert error while verifying cert chain";
    return ssl_verify_ok;
  }

  return ret;
}

enum ssl_verify_result_t OpenSSLAdapter::SSLVerifyInternal(SSL* ssl,
                                                           uint8_t* out_alert) {
  if (ssl_cert_verifier_ == nullptr) {
    RTC_LOG(LS_WARNING) << "Built-in trusted root certificates disabled but no "
                           "SSL verify callback provided.";
    return ssl_verify_invalid;
  }

  RTC_LOG(LS_INFO) << "Invoking SSL Verify Callback.";
  const STACK_OF(CRYPTO_BUFFER)* chain = SSL_get0_peer_certificates(ssl);
  if (sk_CRYPTO_BUFFER_num(chain) == 0) {
    RTC_LOG(LS_ERROR) << "Peer certificate chain empty?";
    return ssl_verify_invalid;
  }

  BoringSSLCertificate cert(bssl::UpRef(sk_CRYPTO_BUFFER_value(chain, 0)));
  if (!ssl_cert_verifier_->Verify(cert)) {
    RTC_LOG(LS_WARNING) << "Failed to verify certificate using custom callback";
    return ssl_verify_invalid;
  }

  custom_cert_verifier_status_ = true;
  RTC_LOG(LS_INFO) << "Validated certificate using custom callback";
  return ssl_verify_ok;
}
#else  // WEBRTC_USE_CRYPTO_BUFFER_CALLBACK
int OpenSSLAdapter::SSLVerifyCallback(int status, X509_STORE_CTX* store) {
  // Get our stream pointer from the store
  SSL* ssl = reinterpret_cast<SSL*>(
      X509_STORE_CTX_get_ex_data(store, SSL_get_ex_data_X509_STORE_CTX_idx()));

  OpenSSLAdapter* stream =
      reinterpret_cast<OpenSSLAdapter*>(SSL_get_app_data(ssl));
  // Update status with the custom verifier.
  // Status is unchanged if verification fails.
  status = stream->SSLVerifyInternal(status, ssl, store);

  // Should only be used for debugging and development.
  if (!status && stream->ignore_bad_cert_) {
    RTC_DLOG(LS_WARNING) << "Ignoring cert error while verifying cert chain";
    return 1;
  }

  return status;
}

int OpenSSLAdapter::SSLVerifyInternal(int previous_status,
                                      SSL* ssl,
                                      X509_STORE_CTX* store) {
#if !defined(NDEBUG)
  if (!previous_status) {
    char data[256];
    X509* cert = X509_STORE_CTX_get_current_cert(store);
    int depth = X509_STORE_CTX_get_error_depth(store);
    int err = X509_STORE_CTX_get_error(store);

    RTC_DLOG(LS_INFO) << "Error with certificate at depth: " << depth;
    X509_NAME_oneline(X509_get_issuer_name(cert), data, sizeof(data));
    RTC_DLOG(LS_INFO) << "  issuer  = " << data;
    X509_NAME_oneline(X509_get_subject_name(cert), data, sizeof(data));
    RTC_DLOG(LS_INFO) << "  subject = " << data;
    RTC_DLOG(LS_INFO) << "  err     = " << err << ":"
                      << X509_verify_cert_error_string(err);
  }
#endif
  // `ssl_cert_verifier_` is used to override errors; if there is no error
  // there is no reason to call it.
  if (previous_status || ssl_cert_verifier_ == nullptr) {
    return previous_status;
  }

  RTC_LOG(LS_INFO) << "Invoking SSL Verify Callback.";
#ifdef OPENSSL_IS_BORINGSSL
  // Convert X509 to CRYPTO_BUFFER.
  uint8_t* data = nullptr;
  int length = i2d_X509(X509_STORE_CTX_get_current_cert(store), &data);
  if (length < 0) {
    RTC_LOG(LS_ERROR) << "Failed to encode X509.";
    return previous_status;
  }
  bssl::UniquePtr<uint8_t> owned_data(data);
  bssl::UniquePtr<CRYPTO_BUFFER> crypto_buffer(
      CRYPTO_BUFFER_new(data, length, openssl::GetBufferPool()));
  if (!crypto_buffer) {
    RTC_LOG(LS_ERROR) << "Failed to allocate CRYPTO_BUFFER.";
    return previous_status;
  }
  const BoringSSLCertificate cert(std::move(crypto_buffer));
#else
  const OpenSSLCertificate cert(X509_STORE_CTX_get_current_cert(store));
#endif
  if (!ssl_cert_verifier_->Verify(cert)) {
    RTC_LOG(LS_INFO) << "Failed to verify certificate using custom callback";
    return previous_status;
  }

  custom_cert_verifier_status_ = true;
  RTC_LOG(LS_INFO) << "Validated certificate using custom callback";
  return 1;
}
#endif  // !defined(WEBRTC_USE_CRYPTO_BUFFER_CALLBACK)

int OpenSSLAdapter::NewSSLSessionCallback(SSL* ssl, SSL_SESSION* session) {
  OpenSSLAdapter* stream =
      reinterpret_cast<OpenSSLAdapter*>(SSL_get_app_data(ssl));
  RTC_DCHECK(stream->ssl_session_cache_);
  RTC_LOG(LS_INFO) << "Caching SSL session for " << stream->ssl_host_name_;
  stream->ssl_session_cache_->AddSession(stream->ssl_host_name_, session);
  return 1;  // We've taken ownership of the session; OpenSSL shouldn't free it.
}

SSL_CTX* OpenSSLAdapter::CreateContext(SSLMode mode, bool enable_cache) {
#ifdef WEBRTC_USE_CRYPTO_BUFFER_CALLBACK
  // If X509 objects aren't used, we can use these methods to avoid
  // linking the sizable crypto/x509 code.
  SSL_CTX* ctx = SSL_CTX_new(mode == SSL_MODE_DTLS ? DTLS_with_buffers_method()
                                                   : TLS_with_buffers_method());
#else
  SSL_CTX* ctx =
      SSL_CTX_new(mode == SSL_MODE_DTLS ? DTLS_method() : TLS_method());
#endif
  if (ctx == nullptr) {
    unsigned long error = ERR_get_error();  // NOLINT: type used by OpenSSL.
    RTC_LOG(LS_WARNING) << "SSL_CTX creation failed: " << '"'
                        << ERR_reason_error_string(error)
                        << "\" "
                           "(error="
                        << error << ')';
    return nullptr;
  }

#ifndef WEBRTC_EXCLUDE_BUILT_IN_SSL_ROOT_CERTS
  if (!openssl::LoadBuiltinSSLRootCertificates(ctx)) {
    RTC_LOG(LS_ERROR) << "SSL_CTX creation failed: Failed to load any trusted "
                         "ssl root certificates.";
    SSL_CTX_free(ctx);
    return nullptr;
  }
#endif  // WEBRTC_EXCLUDE_BUILT_IN_SSL_ROOT_CERTS

#if !defined(NDEBUG)
  SSL_CTX_set_info_callback(ctx, SSLInfoCallback);
#endif

#ifdef OPENSSL_IS_BORINGSSL
  SSL_CTX_set0_buffer_pool(ctx, openssl::GetBufferPool());
#endif

#ifdef WEBRTC_USE_CRYPTO_BUFFER_CALLBACK
  SSL_CTX_set_custom_verify(ctx, SSL_VERIFY_PEER, SSLVerifyCallback);
#else
  SSL_CTX_set_verify(ctx, SSL_VERIFY_PEER, SSLVerifyCallback);
  // Verify certificate chains up to a depth of 4. This is not
  // needed for DTLS-SRTP which uses self-signed certificates
  // (so the depth is 0) but is required to support TURN/TLS.
  SSL_CTX_set_verify_depth(ctx, 4);
#endif
  // Use defaults, but disable HMAC-SHA256 and HMAC-SHA384 ciphers
  // (note that SHA256 and SHA384 only select legacy CBC ciphers).
  // Additionally disable HMAC-SHA1 ciphers in ECDSA. These are the remaining
  // CBC-mode ECDSA ciphers. Finally, disable 3DES.
  SSL_CTX_set_cipher_list(
      ctx, "ALL:!SHA256:!SHA384:!aPSK:!ECDSA+SHA1:!ADH:!LOW:!EXP:!MD5:!3DES");

  if (mode == SSL_MODE_DTLS) {
    SSL_CTX_set_read_ahead(ctx, 1);
  }

  if (enable_cache) {
    SSL_CTX_set_session_cache_mode(ctx, SSL_SESS_CACHE_CLIENT);
    SSL_CTX_sess_set_new_cb(ctx, &OpenSSLAdapter::NewSSLSessionCallback);
  }

  return ctx;
}

std::string TransformAlpnProtocols(
    const std::vector<std::string>& alpn_protocols) {
  // Transforms the alpn_protocols list to the format expected by
  // Open/BoringSSL. This requires joining the protocols into a single string
  // and prepending a character with the size of the protocol string before
  // each protocol.
  std::string transformed_alpn;
  for (const std::string& proto : alpn_protocols) {
    if (proto.size() == 0 || proto.size() > 0xFF) {
      RTC_LOG(LS_ERROR) << "OpenSSLAdapter::Error("
                           "TransformAlpnProtocols received proto with size "
                        << proto.size() << ")";
      return "";
    }
    transformed_alpn += static_cast<char>(proto.size());
    transformed_alpn += proto;
    RTC_LOG(LS_VERBOSE) << "TransformAlpnProtocols: Adding proto: " << proto;
  }
  return transformed_alpn;
}

//////////////////////////////////////////////////////////////////////
// OpenSSLAdapterFactory
//////////////////////////////////////////////////////////////////////

OpenSSLAdapterFactory::OpenSSLAdapterFactory() = default;

OpenSSLAdapterFactory::~OpenSSLAdapterFactory() = default;

void OpenSSLAdapterFactory::SetMode(SSLMode mode) {
  RTC_DCHECK(!ssl_session_cache_);
  ssl_mode_ = mode;
}

void OpenSSLAdapterFactory::SetCertVerifier(
    SSLCertificateVerifier* ssl_cert_verifier) {
  RTC_DCHECK(!ssl_session_cache_);
  ssl_cert_verifier_ = ssl_cert_verifier;
}

void OpenSSLAdapterFactory::SetIdentity(std::unique_ptr<SSLIdentity> identity) {
  RTC_DCHECK(!ssl_session_cache_);
  identity_ = std::move(identity);
}

void OpenSSLAdapterFactory::SetRole(SSLRole role) {
  RTC_DCHECK(!ssl_session_cache_);
  ssl_role_ = role;
}

void OpenSSLAdapterFactory::SetIgnoreBadCert(bool ignore) {
  RTC_DCHECK(!ssl_session_cache_);
  ignore_bad_cert_ = ignore;
}

OpenSSLAdapter* OpenSSLAdapterFactory::CreateAdapter(Socket* socket) {
  if (ssl_session_cache_ == nullptr) {
    SSL_CTX* ssl_ctx = OpenSSLAdapter::CreateContext(ssl_mode_, true);
    if (ssl_ctx == nullptr) {
      return nullptr;
    }
    // The OpenSSLSessionCache will upref the ssl_ctx.
    ssl_session_cache_ =
        std::make_unique<OpenSSLSessionCache>(ssl_mode_, ssl_ctx);
    SSL_CTX_free(ssl_ctx);
  }
  OpenSSLAdapter* ssl_adapter =
      new OpenSSLAdapter(socket, ssl_session_cache_.get(), ssl_cert_verifier_);
  ssl_adapter->SetRole(ssl_role_);
  ssl_adapter->SetIgnoreBadCert(ignore_bad_cert_);
  if (identity_) {
    ssl_adapter->SetIdentity(identity_->Clone());
  }
  return ssl_adapter;
}

OpenSSLAdapter::EarlyExitCatcher::EarlyExitCatcher(OpenSSLAdapter& adapter_ptr)
    : adapter_ptr_(adapter_ptr) {}

void OpenSSLAdapter::EarlyExitCatcher::disable() {
  disabled_ = true;
}

OpenSSLAdapter::EarlyExitCatcher::~EarlyExitCatcher() {
  if (!disabled_) {
    adapter_ptr_.Cleanup();
  }
}

}  // namespace rtc

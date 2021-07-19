/* Copyright (c) 2014, Google Inc.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

#include "test_config.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <memory>

#include <openssl/base64.h>
#include <openssl/rand.h>
#include <openssl/ssl.h>

#include "../../crypto/internal.h"
#include "../internal.h"
#include "test_state.h"

namespace {

template <typename T>
struct Flag {
  const char *flag;
  T TestConfig::*member;
};

// FindField looks for the flag in |flags| that matches |flag|. If one is found,
// it returns a pointer to the corresponding field in |config|. Otherwise, it
// returns NULL.
template<typename T, size_t N>
T *FindField(TestConfig *config, const Flag<T> (&flags)[N], const char *flag) {
  for (size_t i = 0; i < N; i++) {
    if (strcmp(flag, flags[i].flag) == 0) {
      return &(config->*(flags[i].member));
    }
  }
  return NULL;
}

const Flag<bool> kBoolFlags[] = {
    {"-server", &TestConfig::is_server},
    {"-dtls", &TestConfig::is_dtls},
    {"-fallback-scsv", &TestConfig::fallback_scsv},
    {"-require-any-client-certificate",
     &TestConfig::require_any_client_certificate},
    {"-false-start", &TestConfig::false_start},
    {"-async", &TestConfig::async},
    {"-write-different-record-sizes",
     &TestConfig::write_different_record_sizes},
    {"-cbc-record-splitting", &TestConfig::cbc_record_splitting},
    {"-partial-write", &TestConfig::partial_write},
    {"-no-tls13", &TestConfig::no_tls13},
    {"-no-tls12", &TestConfig::no_tls12},
    {"-no-tls11", &TestConfig::no_tls11},
    {"-no-tls1", &TestConfig::no_tls1},
    {"-no-ticket", &TestConfig::no_ticket},
    {"-enable-channel-id", &TestConfig::enable_channel_id},
    {"-shim-writes-first", &TestConfig::shim_writes_first},
    {"-expect-session-miss", &TestConfig::expect_session_miss},
    {"-decline-alpn", &TestConfig::decline_alpn},
    {"-select-empty-alpn", &TestConfig::select_empty_alpn},
    {"-expect-extended-master-secret",
     &TestConfig::expect_extended_master_secret},
    {"-enable-ocsp-stapling", &TestConfig::enable_ocsp_stapling},
    {"-enable-signed-cert-timestamps",
     &TestConfig::enable_signed_cert_timestamps},
    {"-implicit-handshake", &TestConfig::implicit_handshake},
    {"-use-early-callback", &TestConfig::use_early_callback},
    {"-fail-early-callback", &TestConfig::fail_early_callback},
    {"-install-ddos-callback", &TestConfig::install_ddos_callback},
    {"-fail-ddos-callback", &TestConfig::fail_ddos_callback},
    {"-fail-cert-callback", &TestConfig::fail_cert_callback},
    {"-handshake-never-done", &TestConfig::handshake_never_done},
    {"-use-export-context", &TestConfig::use_export_context},
    {"-tls-unique", &TestConfig::tls_unique},
    {"-expect-ticket-renewal", &TestConfig::expect_ticket_renewal},
    {"-expect-no-session", &TestConfig::expect_no_session},
    {"-expect-ticket-supports-early-data",
     &TestConfig::expect_ticket_supports_early_data},
    {"-use-ticket-callback", &TestConfig::use_ticket_callback},
    {"-renew-ticket", &TestConfig::renew_ticket},
    {"-enable-early-data", &TestConfig::enable_early_data},
    {"-check-close-notify", &TestConfig::check_close_notify},
    {"-shim-shuts-down", &TestConfig::shim_shuts_down},
    {"-verify-fail", &TestConfig::verify_fail},
    {"-verify-peer", &TestConfig::verify_peer},
    {"-verify-peer-if-no-obc", &TestConfig::verify_peer_if_no_obc},
    {"-expect-verify-result", &TestConfig::expect_verify_result},
    {"-renegotiate-once", &TestConfig::renegotiate_once},
    {"-renegotiate-freely", &TestConfig::renegotiate_freely},
    {"-renegotiate-ignore", &TestConfig::renegotiate_ignore},
    {"-forbid-renegotiation-after-handshake",
     &TestConfig::forbid_renegotiation_after_handshake},
    {"-enable-all-curves", &TestConfig::enable_all_curves},
    {"-use-old-client-cert-callback",
     &TestConfig::use_old_client_cert_callback},
    {"-send-alert", &TestConfig::send_alert},
    {"-peek-then-read", &TestConfig::peek_then_read},
    {"-enable-grease", &TestConfig::enable_grease},
    {"-use-exporter-between-reads", &TestConfig::use_exporter_between_reads},
    {"-retain-only-sha256-client-cert",
     &TestConfig::retain_only_sha256_client_cert},
    {"-expect-sha256-client-cert", &TestConfig::expect_sha256_client_cert},
    {"-read-with-unfinished-write", &TestConfig::read_with_unfinished_write},
    {"-expect-secure-renegotiation", &TestConfig::expect_secure_renegotiation},
    {"-expect-no-secure-renegotiation",
     &TestConfig::expect_no_secure_renegotiation},
    {"-expect-session-id", &TestConfig::expect_session_id},
    {"-expect-no-session-id", &TestConfig::expect_no_session_id},
    {"-expect-accept-early-data", &TestConfig::expect_accept_early_data},
    {"-expect-reject-early-data", &TestConfig::expect_reject_early_data},
    {"-expect-no-offer-early-data", &TestConfig::expect_no_offer_early_data},
    {"-no-op-extra-handshake", &TestConfig::no_op_extra_handshake},
    {"-handshake-twice", &TestConfig::handshake_twice},
    {"-allow-unknown-alpn-protos", &TestConfig::allow_unknown_alpn_protos},
    {"-enable-ed25519", &TestConfig::enable_ed25519},
    {"-use-custom-verify-callback", &TestConfig::use_custom_verify_callback},
    {"-allow-false-start-without-alpn",
     &TestConfig::allow_false_start_without_alpn},
    {"-ignore-tls13-downgrade", &TestConfig::ignore_tls13_downgrade},
    {"-expect-tls13-downgrade", &TestConfig::expect_tls13_downgrade},
    {"-handoff", &TestConfig::handoff},
    {"-no-rsa-pss-rsae-certs", &TestConfig::no_rsa_pss_rsae_certs},
    {"-use-ocsp-callback", &TestConfig::use_ocsp_callback},
    {"-set-ocsp-in-callback", &TestConfig::set_ocsp_in_callback},
    {"-decline-ocsp-callback", &TestConfig::decline_ocsp_callback},
    {"-fail-ocsp-callback", &TestConfig::fail_ocsp_callback},
    {"-install-cert-compression-algs",
     &TestConfig::install_cert_compression_algs},
    {"-is-handshaker-supported", &TestConfig::is_handshaker_supported},
    {"-handshaker-resume", &TestConfig::handshaker_resume},
    {"-reverify-on-resume", &TestConfig::reverify_on_resume},
    {"-enforce-rsa-key-usage", &TestConfig::enforce_rsa_key_usage},
    {"-jdk11-workaround", &TestConfig::jdk11_workaround},
    {"-server-preference", &TestConfig::server_preference},
    {"-export-traffic-secrets", &TestConfig::export_traffic_secrets},
    {"-key-update", &TestConfig::key_update},
    {"-expect-delegated-credential-used",
     &TestConfig::expect_delegated_credential_used},
    {"-enable-pq-experiment-signal", &TestConfig::enable_pq_experiment_signal},
    {"-expect-pq-experiment-signal", &TestConfig::expect_pq_experiment_signal},
};

const Flag<std::string> kStringFlags[] = {
    {"-write-settings", &TestConfig::write_settings},
    {"-key-file", &TestConfig::key_file},
    {"-cert-file", &TestConfig::cert_file},
    {"-expect-server-name", &TestConfig::expect_server_name},
    {"-advertise-npn", &TestConfig::advertise_npn},
    {"-expect-next-proto", &TestConfig::expect_next_proto},
    {"-select-next-proto", &TestConfig::select_next_proto},
    {"-send-channel-id", &TestConfig::send_channel_id},
    {"-host-name", &TestConfig::host_name},
    {"-advertise-alpn", &TestConfig::advertise_alpn},
    {"-expect-alpn", &TestConfig::expect_alpn},
    {"-expect-late-alpn", &TestConfig::expect_late_alpn},
    {"-expect-advertised-alpn", &TestConfig::expect_advertised_alpn},
    {"-select-alpn", &TestConfig::select_alpn},
    {"-psk", &TestConfig::psk},
    {"-psk-identity", &TestConfig::psk_identity},
    {"-srtp-profiles", &TestConfig::srtp_profiles},
    {"-cipher", &TestConfig::cipher},
    {"-export-label", &TestConfig::export_label},
    {"-export-context", &TestConfig::export_context},
    {"-expect-peer-cert-file", &TestConfig::expect_peer_cert_file},
    {"-use-client-ca-list", &TestConfig::use_client_ca_list},
    {"-expect-client-ca-list", &TestConfig::expect_client_ca_list},
    {"-expect-msg-callback", &TestConfig::expect_msg_callback},
    {"-handshaker-path", &TestConfig::handshaker_path},
    {"-delegated-credential", &TestConfig::delegated_credential},
    {"-expect-early-data-reason", &TestConfig::expect_early_data_reason},
};

const Flag<std::string> kBase64Flags[] = {
    {"-expect-certificate-types", &TestConfig::expect_certificate_types},
    {"-expect-channel-id", &TestConfig::expect_channel_id},
    {"-token-binding-params", &TestConfig::send_token_binding_params},
    {"-expect-ocsp-response", &TestConfig::expect_ocsp_response},
    {"-expect-signed-cert-timestamps",
     &TestConfig::expect_signed_cert_timestamps},
    {"-ocsp-response", &TestConfig::ocsp_response},
    {"-signed-cert-timestamps", &TestConfig::signed_cert_timestamps},
    {"-ticket-key", &TestConfig::ticket_key},
    {"-quic-transport-params", &TestConfig::quic_transport_params},
    {"-expect-quic-transport-params",
     &TestConfig::expect_quic_transport_params},
};

const Flag<int> kIntFlags[] = {
    {"-port", &TestConfig::port},
    {"-resume-count", &TestConfig::resume_count},
    {"-expect-token-binding-param", &TestConfig::expect_token_binding_param},
    {"-min-version", &TestConfig::min_version},
    {"-max-version", &TestConfig::max_version},
    {"-expect-version", &TestConfig::expect_version},
    {"-mtu", &TestConfig::mtu},
    {"-export-keying-material", &TestConfig::export_keying_material},
    {"-expect-total-renegotiations", &TestConfig::expect_total_renegotiations},
    {"-expect-peer-signature-algorithm",
     &TestConfig::expect_peer_signature_algorithm},
    {"-expect-curve-id", &TestConfig::expect_curve_id},
    {"-initial-timeout-duration-ms", &TestConfig::initial_timeout_duration_ms},
    {"-max-cert-list", &TestConfig::max_cert_list},
    {"-expect-cipher-aes", &TestConfig::expect_cipher_aes},
    {"-expect-cipher-no-aes", &TestConfig::expect_cipher_no_aes},
    {"-resumption-delay", &TestConfig::resumption_delay},
    {"-max-send-fragment", &TestConfig::max_send_fragment},
    {"-read-size", &TestConfig::read_size},
    {"-expect-ticket-age-skew", &TestConfig::expect_ticket_age_skew},
};

const Flag<std::vector<int>> kIntVectorFlags[] = {
    {"-signing-prefs", &TestConfig::signing_prefs},
    {"-verify-prefs", &TestConfig::verify_prefs},
    {"-expect-peer-verify-pref", &TestConfig::expect_peer_verify_prefs},
    {"-curves", &TestConfig::curves},
};

bool ParseFlag(char *flag, int argc, char **argv, int *i,
               bool skip, TestConfig *out_config) {
  bool *bool_field = FindField(out_config, kBoolFlags, flag);
  if (bool_field != NULL) {
    if (!skip) {
      *bool_field = true;
    }
    return true;
  }

  std::string *string_field = FindField(out_config, kStringFlags, flag);
  if (string_field != NULL) {
    *i = *i + 1;
    if (*i >= argc) {
      fprintf(stderr, "Missing parameter.\n");
      return false;
    }
    if (!skip) {
      string_field->assign(argv[*i]);
    }
    return true;
  }

  std::string *base64_field = FindField(out_config, kBase64Flags, flag);
  if (base64_field != NULL) {
    *i = *i + 1;
    if (*i >= argc) {
      fprintf(stderr, "Missing parameter.\n");
      return false;
    }
    size_t len;
    if (!EVP_DecodedLength(&len, strlen(argv[*i]))) {
      fprintf(stderr, "Invalid base64: %s.\n", argv[*i]);
      return false;
    }
    std::unique_ptr<uint8_t[]> decoded(new uint8_t[len]);
    if (!EVP_DecodeBase64(decoded.get(), &len, len,
                          reinterpret_cast<const uint8_t *>(argv[*i]),
                          strlen(argv[*i]))) {
      fprintf(stderr, "Invalid base64: %s.\n", argv[*i]);
      return false;
    }
    if (!skip) {
      base64_field->assign(reinterpret_cast<const char *>(decoded.get()),
                           len);
    }
    return true;
  }

  int *int_field = FindField(out_config, kIntFlags, flag);
  if (int_field) {
    *i = *i + 1;
    if (*i >= argc) {
      fprintf(stderr, "Missing parameter.\n");
      return false;
    }
    if (!skip) {
      *int_field = atoi(argv[*i]);
    }
    return true;
  }

  std::vector<int> *int_vector_field =
      FindField(out_config, kIntVectorFlags, flag);
  if (int_vector_field) {
    *i = *i + 1;
    if (*i >= argc) {
      fprintf(stderr, "Missing parameter.\n");
      return false;
    }

    // Each instance of the flag adds to the list.
    if (!skip) {
      int_vector_field->push_back(atoi(argv[*i]));
    }
    return true;
  }

  fprintf(stderr, "Unknown argument: %s.\n", flag);
  return false;
}

const char kInit[] = "-on-initial";
const char kResume[] = "-on-resume";
const char kRetry[] = "-on-retry";

}  // namespace

bool ParseConfig(int argc, char **argv,
                 TestConfig *out_initial,
                 TestConfig *out_resume,
                 TestConfig *out_retry) {
  out_initial->argc = out_resume->argc = out_retry->argc = argc;
  out_initial->argv = out_resume->argv = out_retry->argv = argv;
  for (int i = 0; i < argc; i++) {
    bool skip = false;
    char *flag = argv[i];
    if (strncmp(flag, kInit, strlen(kInit)) == 0) {
      if (!ParseFlag(flag + strlen(kInit), argc, argv, &i, skip, out_initial)) {
        return false;
      }
    } else if (strncmp(flag, kResume, strlen(kResume)) == 0) {
      if (!ParseFlag(flag + strlen(kResume), argc, argv, &i, skip,
                     out_resume)) {
        return false;
      }
    } else if (strncmp(flag, kRetry, strlen(kRetry)) == 0) {
      if (!ParseFlag(flag + strlen(kRetry), argc, argv, &i, skip, out_retry)) {
        return false;
      }
    } else {
      int i_init = i;
      int i_resume = i;
      if (!ParseFlag(flag, argc, argv, &i_init, skip, out_initial) ||
          !ParseFlag(flag, argc, argv, &i_resume, skip, out_resume) ||
          !ParseFlag(flag, argc, argv, &i, skip, out_retry)) {
        return false;
      }
    }
  }

  return true;
}

static CRYPTO_once_t once = CRYPTO_ONCE_INIT;
static int g_config_index = 0;
static CRYPTO_BUFFER_POOL *g_pool = nullptr;

static void init_once() {
  g_config_index = SSL_get_ex_new_index(0, NULL, NULL, NULL, NULL);
  if (g_config_index < 0) {
    abort();
  }
  g_pool = CRYPTO_BUFFER_POOL_new();
  if (!g_pool) {
    abort();
  }
}

bool SetTestConfig(SSL *ssl, const TestConfig *config) {
  CRYPTO_once(&once, init_once);
  return SSL_set_ex_data(ssl, g_config_index, (void *)config) == 1;
}

const TestConfig *GetTestConfig(const SSL *ssl) {
  CRYPTO_once(&once, init_once);
  return (const TestConfig *)SSL_get_ex_data(ssl, g_config_index);
}

static int LegacyOCSPCallback(SSL *ssl, void *arg) {
  const TestConfig *config = GetTestConfig(ssl);
  if (!SSL_is_server(ssl)) {
    return !config->fail_ocsp_callback;
  }

  if (!config->ocsp_response.empty() && config->set_ocsp_in_callback &&
      !SSL_set_ocsp_response(ssl, (const uint8_t *)config->ocsp_response.data(),
                             config->ocsp_response.size())) {
    return SSL_TLSEXT_ERR_ALERT_FATAL;
  }
  if (config->fail_ocsp_callback) {
    return SSL_TLSEXT_ERR_ALERT_FATAL;
  }
  if (config->decline_ocsp_callback) {
    return SSL_TLSEXT_ERR_NOACK;
  }
  return SSL_TLSEXT_ERR_OK;
}

static int ServerNameCallback(SSL *ssl, int *out_alert, void *arg) {
  // SNI must be accessible from the SNI callback.
  const TestConfig *config = GetTestConfig(ssl);
  const char *server_name = SSL_get_servername(ssl, TLSEXT_NAMETYPE_host_name);
  if (server_name == nullptr ||
      std::string(server_name) != config->expect_server_name) {
    fprintf(stderr, "servername mismatch (got %s; want %s).\n", server_name,
            config->expect_server_name.c_str());
    return SSL_TLSEXT_ERR_ALERT_FATAL;
  }

  return SSL_TLSEXT_ERR_OK;
}

static int NextProtoSelectCallback(SSL *ssl, uint8_t **out, uint8_t *outlen,
                                   const uint8_t *in, unsigned inlen,
                                   void *arg) {
  const TestConfig *config = GetTestConfig(ssl);
  if (config->select_next_proto.empty()) {
    return SSL_TLSEXT_ERR_NOACK;
  }

  *out = (uint8_t *)config->select_next_proto.data();
  *outlen = config->select_next_proto.size();
  return SSL_TLSEXT_ERR_OK;
}

static int NextProtosAdvertisedCallback(SSL *ssl, const uint8_t **out,
                                        unsigned int *out_len, void *arg) {
  const TestConfig *config = GetTestConfig(ssl);
  if (config->advertise_npn.empty()) {
    return SSL_TLSEXT_ERR_NOACK;
  }

  *out = (const uint8_t *)config->advertise_npn.data();
  *out_len = config->advertise_npn.size();
  return SSL_TLSEXT_ERR_OK;
}

static void MessageCallback(int is_write, int version, int content_type,
                            const void *buf, size_t len, SSL *ssl, void *arg) {
  const uint8_t *buf_u8 = reinterpret_cast<const uint8_t *>(buf);
  const TestConfig *config = GetTestConfig(ssl);
  TestState *state = GetTestState(ssl);
  if (!state->msg_callback_ok) {
    return;
  }

  if (content_type == SSL3_RT_HEADER) {
    if (len !=
        (config->is_dtls ? DTLS1_RT_HEADER_LENGTH : SSL3_RT_HEADER_LENGTH)) {
      fprintf(stderr, "Incorrect length for record header: %zu.\n", len);
      state->msg_callback_ok = false;
    }
    return;
  }

  state->msg_callback_text += is_write ? "write " : "read ";
  switch (content_type) {
    case 0:
      if (version != SSL2_VERSION) {
        fprintf(stderr, "Incorrect version for V2ClientHello: %x.\n", version);
        state->msg_callback_ok = false;
        return;
      }
      state->msg_callback_text += "v2clienthello\n";
      return;

    case SSL3_RT_HANDSHAKE: {
      CBS cbs;
      CBS_init(&cbs, buf_u8, len);
      uint8_t type;
      uint32_t msg_len;
      if (!CBS_get_u8(&cbs, &type) ||
          // TODO(davidben): Reporting on entire messages would be more
          // consistent than fragments.
          (config->is_dtls &&
           !CBS_skip(&cbs, 3 /* total */ + 2 /* seq */ + 3 /* frag_off */)) ||
          !CBS_get_u24(&cbs, &msg_len) || !CBS_skip(&cbs, msg_len) ||
          CBS_len(&cbs) != 0) {
        fprintf(stderr, "Could not parse handshake message.\n");
        state->msg_callback_ok = false;
        return;
      }
      char text[16];
      snprintf(text, sizeof(text), "hs %d\n", type);
      state->msg_callback_text += text;
      return;
    }

    case SSL3_RT_CHANGE_CIPHER_SPEC:
      if (len != 1 || buf_u8[0] != 1) {
        fprintf(stderr, "Invalid ChangeCipherSpec.\n");
        state->msg_callback_ok = false;
        return;
      }
      state->msg_callback_text += "ccs\n";
      return;

    case SSL3_RT_ALERT:
      if (len != 2) {
        fprintf(stderr, "Invalid alert.\n");
        state->msg_callback_ok = false;
        return;
      }
      char text[16];
      snprintf(text, sizeof(text), "alert %d %d\n", buf_u8[0], buf_u8[1]);
      state->msg_callback_text += text;
      return;

    default:
      fprintf(stderr, "Invalid content_type: %d.\n", content_type);
      state->msg_callback_ok = false;
  }
}

static int TicketKeyCallback(SSL *ssl, uint8_t *key_name, uint8_t *iv,
                             EVP_CIPHER_CTX *ctx, HMAC_CTX *hmac_ctx,
                             int encrypt) {
  if (!encrypt) {
    if (GetTestState(ssl)->ticket_decrypt_done) {
      fprintf(stderr, "TicketKeyCallback called after completion.\n");
      return -1;
    }

    GetTestState(ssl)->ticket_decrypt_done = true;
  }

  // This is just test code, so use the all-zeros key.
  static const uint8_t kZeros[16] = {0};

  if (encrypt) {
    OPENSSL_memcpy(key_name, kZeros, sizeof(kZeros));
    RAND_bytes(iv, 16);
  } else if (OPENSSL_memcmp(key_name, kZeros, 16) != 0) {
    return 0;
  }

  if (!HMAC_Init_ex(hmac_ctx, kZeros, sizeof(kZeros), EVP_sha256(), NULL) ||
      !EVP_CipherInit_ex(ctx, EVP_aes_128_cbc(), NULL, kZeros, iv, encrypt)) {
    return -1;
  }

  if (!encrypt) {
    return GetTestConfig(ssl)->renew_ticket ? 2 : 1;
  }
  return 1;
}

static int NewSessionCallback(SSL *ssl, SSL_SESSION *session) {
  // This callback is called as the handshake completes. |SSL_get_session|
  // must continue to work and, historically, |SSL_in_init| returned false at
  // this point.
  if (SSL_in_init(ssl) || SSL_get_session(ssl) == nullptr) {
    fprintf(stderr, "Invalid state for NewSessionCallback.\n");
    abort();
  }

  GetTestState(ssl)->got_new_session = true;
  GetTestState(ssl)->new_session.reset(session);
  return 1;
}

static void InfoCallback(const SSL *ssl, int type, int val) {
  if (type == SSL_CB_HANDSHAKE_DONE) {
    if (GetTestConfig(ssl)->handshake_never_done) {
      fprintf(stderr, "Handshake unexpectedly completed.\n");
      // Abort before any expected error code is printed, to ensure the overall
      // test fails.
      abort();
    }
    // This callback is called when the handshake completes. |SSL_get_session|
    // must continue to work and |SSL_in_init| must return false.
    if (SSL_in_init(ssl) || SSL_get_session(ssl) == nullptr) {
      fprintf(stderr, "Invalid state for SSL_CB_HANDSHAKE_DONE.\n");
      abort();
    }
    GetTestState(ssl)->handshake_done = true;

    // Callbacks may be called again on a new handshake.
    GetTestState(ssl)->ticket_decrypt_done = false;
    GetTestState(ssl)->alpn_select_done = false;
  }
}

static void ChannelIdCallback(SSL *ssl, EVP_PKEY **out_pkey) {
  *out_pkey = GetTestState(ssl)->channel_id.release();
}

static SSL_SESSION *GetSessionCallback(SSL *ssl, const uint8_t *data, int len,
                                       int *copy) {
  TestState *async_state = GetTestState(ssl);
  if (async_state->session) {
    *copy = 0;
    return async_state->session.release();
  } else if (async_state->pending_session) {
    return SSL_magic_pending_session_ptr();
  } else {
    return NULL;
  }
}

static void CurrentTimeCallback(const SSL *ssl, timeval *out_clock) {
  *out_clock = *GetClock();
}

static int AlpnSelectCallback(SSL *ssl, const uint8_t **out, uint8_t *outlen,
                              const uint8_t *in, unsigned inlen, void *arg) {
  if (GetTestState(ssl)->alpn_select_done) {
    fprintf(stderr, "AlpnSelectCallback called after completion.\n");
    exit(1);
  }

  GetTestState(ssl)->alpn_select_done = true;

  const TestConfig *config = GetTestConfig(ssl);
  if (config->decline_alpn) {
    return SSL_TLSEXT_ERR_NOACK;
  }

  if (!config->expect_advertised_alpn.empty() &&
      (config->expect_advertised_alpn.size() != inlen ||
       OPENSSL_memcmp(config->expect_advertised_alpn.data(), in, inlen) !=
           0)) {
    fprintf(stderr, "bad ALPN select callback inputs.\n");
    exit(1);
  }

  assert(config->select_alpn.empty() || !config->select_empty_alpn);
  *out = (const uint8_t *)config->select_alpn.data();
  *outlen = config->select_alpn.size();
  return SSL_TLSEXT_ERR_OK;
}

static bool CheckVerifyCallback(SSL *ssl) {
  const TestConfig *config = GetTestConfig(ssl);
  if (!config->expect_ocsp_response.empty()) {
    const uint8_t *data;
    size_t len;
    SSL_get0_ocsp_response(ssl, &data, &len);
    if (len == 0) {
      fprintf(stderr, "OCSP response not available in verify callback.\n");
      return false;
    }
  }

  if (GetTestState(ssl)->cert_verified) {
    fprintf(stderr, "Certificate verified twice.\n");
    return false;
  }

  return true;
}

static int CertVerifyCallback(X509_STORE_CTX *store_ctx, void *arg) {
  SSL *ssl = (SSL *)X509_STORE_CTX_get_ex_data(
      store_ctx, SSL_get_ex_data_X509_STORE_CTX_idx());
  const TestConfig *config = GetTestConfig(ssl);
  if (!CheckVerifyCallback(ssl)) {
    return 0;
  }

  GetTestState(ssl)->cert_verified = true;
  if (config->verify_fail) {
    store_ctx->error = X509_V_ERR_APPLICATION_VERIFICATION;
    return 0;
  }

  return 1;
}

bool LoadCertificate(bssl::UniquePtr<X509> *out_x509,
                     bssl::UniquePtr<STACK_OF(X509)> *out_chain,
                     const std::string &file) {
  bssl::UniquePtr<BIO> bio(BIO_new(BIO_s_file()));
  if (!bio || !BIO_read_filename(bio.get(), file.c_str())) {
    return false;
  }

  out_x509->reset(PEM_read_bio_X509(bio.get(), nullptr, nullptr, nullptr));
  if (!*out_x509) {
    return false;
  }

  out_chain->reset(sk_X509_new_null());
  if (!*out_chain) {
    return false;
  }

  // Keep reading the certificate chain.
  for (;;) {
    bssl::UniquePtr<X509> cert(
        PEM_read_bio_X509(bio.get(), nullptr, nullptr, nullptr));
    if (!cert) {
      break;
    }

    if (!bssl::PushToStack(out_chain->get(), std::move(cert))) {
      return false;
    }
  }

  uint32_t err = ERR_peek_last_error();
  if (ERR_GET_LIB(err) != ERR_LIB_PEM ||
      ERR_GET_REASON(err) != PEM_R_NO_START_LINE) {
    return false;
  }

  ERR_clear_error();
  return true;
}

bssl::UniquePtr<EVP_PKEY> LoadPrivateKey(const std::string &file) {
  bssl::UniquePtr<BIO> bio(BIO_new(BIO_s_file()));
  if (!bio || !BIO_read_filename(bio.get(), file.c_str())) {
    return nullptr;
  }
  return bssl::UniquePtr<EVP_PKEY>(
      PEM_read_bio_PrivateKey(bio.get(), NULL, NULL, NULL));
}

static bool GetCertificate(SSL *ssl, bssl::UniquePtr<X509> *out_x509,
                           bssl::UniquePtr<STACK_OF(X509)> *out_chain,
                           bssl::UniquePtr<EVP_PKEY> *out_pkey) {
  const TestConfig *config = GetTestConfig(ssl);

  if (!config->signing_prefs.empty()) {
    std::vector<uint16_t> u16s(config->signing_prefs.begin(),
                               config->signing_prefs.end());
    if (!SSL_set_signing_algorithm_prefs(ssl, u16s.data(), u16s.size())) {
      return false;
    }
  }

  if (!config->key_file.empty()) {
    *out_pkey = LoadPrivateKey(config->key_file.c_str());
    if (!*out_pkey) {
      return false;
    }
  }
  if (!config->cert_file.empty() &&
      !LoadCertificate(out_x509, out_chain, config->cert_file.c_str())) {
    return false;
  }
  if (!config->ocsp_response.empty() && !config->set_ocsp_in_callback &&
      !SSL_set_ocsp_response(ssl, (const uint8_t *)config->ocsp_response.data(),
                             config->ocsp_response.size())) {
    return false;
  }
  return true;
}

static bool FromHexDigit(uint8_t *out, char c) {
  if ('0' <= c && c <= '9') {
    *out = c - '0';
    return true;
  }
  if ('a' <= c && c <= 'f') {
    *out = c - 'a' + 10;
    return true;
  }
  if ('A' <= c && c <= 'F') {
    *out = c - 'A' + 10;
    return true;
  }
  return false;
}

static bool HexDecode(std::string *out, const std::string &in) {
  if ((in.size() & 1) != 0) {
    return false;
  }

  std::unique_ptr<uint8_t[]> buf(new uint8_t[in.size() / 2]);
  for (size_t i = 0; i < in.size() / 2; i++) {
    uint8_t high, low;
    if (!FromHexDigit(&high, in[i * 2]) || !FromHexDigit(&low, in[i * 2 + 1])) {
      return false;
    }
    buf[i] = (high << 4) | low;
  }

  out->assign(reinterpret_cast<const char *>(buf.get()), in.size() / 2);
  return true;
}

static std::vector<std::string> SplitParts(const std::string &in,
                                           const char delim) {
  std::vector<std::string> ret;
  size_t start = 0;

  for (size_t i = 0; i < in.size(); i++) {
    if (in[i] == delim) {
      ret.push_back(in.substr(start, i - start));
      start = i + 1;
    }
  }

  ret.push_back(in.substr(start, std::string::npos));
  return ret;
}

static std::vector<std::string> DecodeHexStrings(
    const std::string &hex_strings) {
  std::vector<std::string> ret;
  const std::vector<std::string> parts = SplitParts(hex_strings, ',');

  for (const auto &part : parts) {
    std::string binary;
    if (!HexDecode(&binary, part)) {
      fprintf(stderr, "Bad hex string: %s.\n", part.c_str());
      return ret;
    }

    ret.push_back(binary);
  }

  return ret;
}

static bssl::UniquePtr<STACK_OF(X509_NAME)> DecodeHexX509Names(
    const std::string &hex_names) {
  const std::vector<std::string> der_names = DecodeHexStrings(hex_names);
  bssl::UniquePtr<STACK_OF(X509_NAME)> ret(sk_X509_NAME_new_null());
  if (!ret) {
    return nullptr;
  }

  for (const auto &der_name : der_names) {
    const uint8_t *const data =
        reinterpret_cast<const uint8_t *>(der_name.data());
    const uint8_t *derp = data;
    bssl::UniquePtr<X509_NAME> name(
        d2i_X509_NAME(nullptr, &derp, der_name.size()));
    if (!name || derp != data + der_name.size()) {
      fprintf(stderr, "Failed to parse X509_NAME.\n");
      return nullptr;
    }

    if (!bssl::PushToStack(ret.get(), std::move(name))) {
      return nullptr;
    }
  }

  return ret;
}

static bool CheckPeerVerifyPrefs(SSL *ssl) {
  const TestConfig *config = GetTestConfig(ssl);
  if (!config->expect_peer_verify_prefs.empty()) {
    const uint16_t *peer_sigalgs;
    size_t num_peer_sigalgs =
        SSL_get0_peer_verify_algorithms(ssl, &peer_sigalgs);
    if (config->expect_peer_verify_prefs.size() != num_peer_sigalgs) {
      fprintf(stderr,
              "peer verify preferences length mismatch (got %zu, wanted %zu)\n",
              num_peer_sigalgs, config->expect_peer_verify_prefs.size());
      return false;
    }
    for (size_t i = 0; i < num_peer_sigalgs; i++) {
      if (static_cast<int>(peer_sigalgs[i]) !=
          config->expect_peer_verify_prefs[i]) {
        fprintf(stderr,
                "peer verify preference %zu mismatch (got %04x, wanted %04x\n",
                i, peer_sigalgs[i], config->expect_peer_verify_prefs[i]);
        return false;
      }
    }
  }
  return true;
}

static bool CheckCertificateRequest(SSL *ssl) {
  const TestConfig *config = GetTestConfig(ssl);

  if (!CheckPeerVerifyPrefs(ssl)) {
    return false;
  }

  if (!config->expect_certificate_types.empty()) {
    const uint8_t *certificate_types;
    size_t certificate_types_len =
        SSL_get0_certificate_types(ssl, &certificate_types);
    if (certificate_types_len != config->expect_certificate_types.size() ||
        OPENSSL_memcmp(certificate_types,
                       config->expect_certificate_types.data(),
                       certificate_types_len) != 0) {
      fprintf(stderr, "certificate types mismatch.\n");
      return false;
    }
  }

  if (!config->expect_client_ca_list.empty()) {
    bssl::UniquePtr<STACK_OF(X509_NAME)> expected =
        DecodeHexX509Names(config->expect_client_ca_list);
    const size_t num_expected = sk_X509_NAME_num(expected.get());

    const STACK_OF(X509_NAME) *received = SSL_get_client_CA_list(ssl);
    const size_t num_received = sk_X509_NAME_num(received);

    if (num_received != num_expected) {
      fprintf(stderr, "expected %u names in CertificateRequest but got %u.\n",
              static_cast<unsigned>(num_expected),
              static_cast<unsigned>(num_received));
      return false;
    }

    for (size_t i = 0; i < num_received; i++) {
      if (X509_NAME_cmp(sk_X509_NAME_value(received, i),
                        sk_X509_NAME_value(expected.get(), i)) != 0) {
        fprintf(stderr, "names in CertificateRequest differ at index #%d.\n",
                static_cast<unsigned>(i));
        return false;
      }
    }

    const STACK_OF(CRYPTO_BUFFER) *buffers = SSL_get0_server_requested_CAs(ssl);
    if (sk_CRYPTO_BUFFER_num(buffers) != num_received) {
      fprintf(stderr,
              "Mismatch between SSL_get_server_requested_CAs and "
              "SSL_get_client_CA_list.\n");
      return false;
    }
  }

  return true;
}

static int ClientCertCallback(SSL *ssl, X509 **out_x509, EVP_PKEY **out_pkey) {
  if (!CheckCertificateRequest(ssl)) {
    return -1;
  }

  if (GetTestConfig(ssl)->async && !GetTestState(ssl)->cert_ready) {
    return -1;
  }

  bssl::UniquePtr<X509> x509;
  bssl::UniquePtr<STACK_OF(X509)> chain;
  bssl::UniquePtr<EVP_PKEY> pkey;
  if (!GetCertificate(ssl, &x509, &chain, &pkey)) {
    return -1;
  }

  // Return zero for no certificate.
  if (!x509) {
    return 0;
  }

  // Chains and asynchronous private keys are not supported with client_cert_cb.
  *out_x509 = x509.release();
  *out_pkey = pkey.release();
  return 1;
}

static ssl_private_key_result_t AsyncPrivateKeySign(
    SSL *ssl, uint8_t *out, size_t *out_len, size_t max_out,
    uint16_t signature_algorithm, const uint8_t *in, size_t in_len) {
  TestState *test_state = GetTestState(ssl);
  if (!test_state->private_key_result.empty()) {
    fprintf(stderr, "AsyncPrivateKeySign called with operation pending.\n");
    abort();
  }

  if (EVP_PKEY_id(test_state->private_key.get()) !=
      SSL_get_signature_algorithm_key_type(signature_algorithm)) {
    fprintf(stderr, "Key type does not match signature algorithm.\n");
    abort();
  }

  // Determine the hash.
  const EVP_MD *md = SSL_get_signature_algorithm_digest(signature_algorithm);
  bssl::ScopedEVP_MD_CTX ctx;
  EVP_PKEY_CTX *pctx;
  if (!EVP_DigestSignInit(ctx.get(), &pctx, md, nullptr,
                          test_state->private_key.get())) {
    return ssl_private_key_failure;
  }

  // Configure additional signature parameters.
  if (SSL_is_signature_algorithm_rsa_pss(signature_algorithm)) {
    if (!EVP_PKEY_CTX_set_rsa_padding(pctx, RSA_PKCS1_PSS_PADDING) ||
        !EVP_PKEY_CTX_set_rsa_pss_saltlen(pctx, -1 /* salt len = hash len */)) {
      return ssl_private_key_failure;
    }
  }

  // Write the signature into |test_state|.
  size_t len = 0;
  if (!EVP_DigestSign(ctx.get(), nullptr, &len, in, in_len)) {
    return ssl_private_key_failure;
  }
  test_state->private_key_result.resize(len);
  if (!EVP_DigestSign(ctx.get(), test_state->private_key_result.data(), &len,
                      in, in_len)) {
    return ssl_private_key_failure;
  }
  test_state->private_key_result.resize(len);

  // The signature will be released asynchronously in |AsyncPrivateKeyComplete|.
  return ssl_private_key_retry;
}

static ssl_private_key_result_t AsyncPrivateKeyDecrypt(SSL *ssl, uint8_t *out,
                                                       size_t *out_len,
                                                       size_t max_out,
                                                       const uint8_t *in,
                                                       size_t in_len) {
  TestState *test_state = GetTestState(ssl);
  if (!test_state->private_key_result.empty()) {
    fprintf(stderr, "AsyncPrivateKeyDecrypt called with operation pending.\n");
    abort();
  }

  RSA *rsa = EVP_PKEY_get0_RSA(test_state->private_key.get());
  if (rsa == NULL) {
    fprintf(stderr, "AsyncPrivateKeyDecrypt called with incorrect key type.\n");
    abort();
  }
  test_state->private_key_result.resize(RSA_size(rsa));
  if (!RSA_decrypt(rsa, out_len, test_state->private_key_result.data(),
                   RSA_size(rsa), in, in_len, RSA_NO_PADDING)) {
    return ssl_private_key_failure;
  }

  test_state->private_key_result.resize(*out_len);

  // The decryption will be released asynchronously in |AsyncPrivateComplete|.
  return ssl_private_key_retry;
}

static ssl_private_key_result_t AsyncPrivateKeyComplete(SSL *ssl, uint8_t *out,
                                                        size_t *out_len,
                                                        size_t max_out) {
  TestState *test_state = GetTestState(ssl);
  if (test_state->private_key_result.empty()) {
    fprintf(stderr,
            "AsyncPrivateKeyComplete called without operation pending.\n");
    abort();
  }

  if (test_state->private_key_retries < 2) {
    // Only return the decryption on the second attempt, to test both incomplete
    // |decrypt| and |decrypt_complete|.
    return ssl_private_key_retry;
  }

  if (max_out < test_state->private_key_result.size()) {
    fprintf(stderr, "Output buffer too small.\n");
    return ssl_private_key_failure;
  }
  OPENSSL_memcpy(out, test_state->private_key_result.data(),
                 test_state->private_key_result.size());
  *out_len = test_state->private_key_result.size();

  test_state->private_key_result.clear();
  test_state->private_key_retries = 0;
  return ssl_private_key_success;
}

static const SSL_PRIVATE_KEY_METHOD g_async_private_key_method = {
    AsyncPrivateKeySign,
    AsyncPrivateKeyDecrypt,
    AsyncPrivateKeyComplete,
};

static bool InstallCertificate(SSL *ssl) {
  bssl::UniquePtr<X509> x509;
  bssl::UniquePtr<STACK_OF(X509)> chain;
  bssl::UniquePtr<EVP_PKEY> pkey;
  if (!GetCertificate(ssl, &x509, &chain, &pkey)) {
    return false;
  }

  if (pkey) {
    TestState *test_state = GetTestState(ssl);
    const TestConfig *config = GetTestConfig(ssl);
    if (config->async) {
      test_state->private_key = std::move(pkey);
      SSL_set_private_key_method(ssl, &g_async_private_key_method);
    } else if (!SSL_use_PrivateKey(ssl, pkey.get())) {
      return false;
    }
  }

  if (x509 && !SSL_use_certificate(ssl, x509.get())) {
    return false;
  }

  if (sk_X509_num(chain.get()) > 0 && !SSL_set1_chain(ssl, chain.get())) {
    return false;
  }

  return true;
}

static enum ssl_select_cert_result_t SelectCertificateCallback(
    const SSL_CLIENT_HELLO *client_hello) {
  const TestConfig *config = GetTestConfig(client_hello->ssl);
  GetTestState(client_hello->ssl)->early_callback_called = true;

  if (!config->expect_server_name.empty()) {
    const char *server_name =
        SSL_get_servername(client_hello->ssl, TLSEXT_NAMETYPE_host_name);
    if (server_name == nullptr ||
        std::string(server_name) != config->expect_server_name) {
      fprintf(stderr,
              "Server name mismatch in early callback (got %s; want %s).\n",
              server_name, config->expect_server_name.c_str());
      return ssl_select_cert_error;
    }
  }

  if (config->fail_early_callback) {
    return ssl_select_cert_error;
  }

  // Install the certificate in the early callback.
  if (config->use_early_callback) {
    bool early_callback_ready =
        GetTestState(client_hello->ssl)->early_callback_ready;
    if (config->async && !early_callback_ready) {
      // Install the certificate asynchronously.
      return ssl_select_cert_retry;
    }
    if (!InstallCertificate(client_hello->ssl)) {
      return ssl_select_cert_error;
    }
  }
  return ssl_select_cert_success;
}

bssl::UniquePtr<SSL_CTX> TestConfig::SetupCtx(SSL_CTX *old_ctx) const {
  bssl::UniquePtr<SSL_CTX> ssl_ctx(
      SSL_CTX_new(is_dtls ? DTLS_method() : TLS_method()));
  if (!ssl_ctx) {
    return nullptr;
  }

  CRYPTO_once(&once, init_once);
  SSL_CTX_set0_buffer_pool(ssl_ctx.get(), g_pool);

  // Enable TLS 1.3 for tests.
  if (!is_dtls &&
      !SSL_CTX_set_max_proto_version(ssl_ctx.get(), TLS1_3_VERSION)) {
    return nullptr;
  }

  std::string cipher_list = "ALL";
  if (!cipher.empty()) {
    cipher_list = cipher;
    SSL_CTX_set_options(ssl_ctx.get(), SSL_OP_CIPHER_SERVER_PREFERENCE);
  }
  if (!SSL_CTX_set_strict_cipher_list(ssl_ctx.get(), cipher_list.c_str())) {
    return nullptr;
  }

  if (async && is_server) {
    // Disable the internal session cache. To test asynchronous session lookup,
    // we use an external session cache.
    SSL_CTX_set_session_cache_mode(
        ssl_ctx.get(), SSL_SESS_CACHE_BOTH | SSL_SESS_CACHE_NO_INTERNAL);
    SSL_CTX_sess_set_get_cb(ssl_ctx.get(), GetSessionCallback);
  } else {
    SSL_CTX_set_session_cache_mode(ssl_ctx.get(), SSL_SESS_CACHE_BOTH);
  }

  SSL_CTX_set_select_certificate_cb(ssl_ctx.get(), SelectCertificateCallback);

  if (use_old_client_cert_callback) {
    SSL_CTX_set_client_cert_cb(ssl_ctx.get(), ClientCertCallback);
  }

  SSL_CTX_set_next_protos_advertised_cb(ssl_ctx.get(),
                                        NextProtosAdvertisedCallback, NULL);
  if (!select_next_proto.empty()) {
    SSL_CTX_set_next_proto_select_cb(ssl_ctx.get(), NextProtoSelectCallback,
                                     NULL);
  }

  if (!select_alpn.empty() || decline_alpn || select_empty_alpn) {
    SSL_CTX_set_alpn_select_cb(ssl_ctx.get(), AlpnSelectCallback, NULL);
  }

  SSL_CTX_set_channel_id_cb(ssl_ctx.get(), ChannelIdCallback);

  SSL_CTX_set_current_time_cb(ssl_ctx.get(), CurrentTimeCallback);

  SSL_CTX_set_info_callback(ssl_ctx.get(), InfoCallback);
  SSL_CTX_sess_set_new_cb(ssl_ctx.get(), NewSessionCallback);

  if (use_ticket_callback) {
    SSL_CTX_set_tlsext_ticket_key_cb(ssl_ctx.get(), TicketKeyCallback);
  }

  if (!use_custom_verify_callback) {
    SSL_CTX_set_cert_verify_callback(ssl_ctx.get(), CertVerifyCallback, NULL);
  }

  if (!signed_cert_timestamps.empty() &&
      !SSL_CTX_set_signed_cert_timestamp_list(
          ssl_ctx.get(), (const uint8_t *)signed_cert_timestamps.data(),
          signed_cert_timestamps.size())) {
    return nullptr;
  }

  if (!use_client_ca_list.empty()) {
    if (use_client_ca_list == "<NULL>") {
      SSL_CTX_set_client_CA_list(ssl_ctx.get(), nullptr);
    } else if (use_client_ca_list == "<EMPTY>") {
      bssl::UniquePtr<STACK_OF(X509_NAME)> names;
      SSL_CTX_set_client_CA_list(ssl_ctx.get(), names.release());
    } else {
      bssl::UniquePtr<STACK_OF(X509_NAME)> names =
          DecodeHexX509Names(use_client_ca_list);
      SSL_CTX_set_client_CA_list(ssl_ctx.get(), names.release());
    }
  }

  if (enable_grease) {
    SSL_CTX_set_grease_enabled(ssl_ctx.get(), 1);
  }

  if (!expect_server_name.empty()) {
    SSL_CTX_set_tlsext_servername_callback(ssl_ctx.get(), ServerNameCallback);
  }

  if (enable_early_data) {
    SSL_CTX_set_early_data_enabled(ssl_ctx.get(), 1);
  }

  if (allow_unknown_alpn_protos) {
    SSL_CTX_set_allow_unknown_alpn_protos(ssl_ctx.get(), 1);
  }

  if (enable_ed25519) {
    SSL_CTX_set_ed25519_enabled(ssl_ctx.get(), 1);
  }
  if (no_rsa_pss_rsae_certs) {
    SSL_CTX_set_rsa_pss_rsae_certs_enabled(ssl_ctx.get(), 0);
  }

  if (!verify_prefs.empty()) {
    std::vector<uint16_t> u16s(verify_prefs.begin(), verify_prefs.end());
    if (!SSL_CTX_set_verify_algorithm_prefs(ssl_ctx.get(), u16s.data(),
                                            u16s.size())) {
      return nullptr;
    }
  }

  SSL_CTX_set_msg_callback(ssl_ctx.get(), MessageCallback);

  if (allow_false_start_without_alpn) {
    SSL_CTX_set_false_start_allowed_without_alpn(ssl_ctx.get(), 1);
  }

  if (ignore_tls13_downgrade) {
    SSL_CTX_set_ignore_tls13_downgrade(ssl_ctx.get(), 1);
  }

  if (use_ocsp_callback) {
    SSL_CTX_set_tlsext_status_cb(ssl_ctx.get(), LegacyOCSPCallback);
  }

  if (old_ctx) {
    uint8_t keys[48];
    if (!SSL_CTX_get_tlsext_ticket_keys(old_ctx, &keys, sizeof(keys)) ||
        !SSL_CTX_set_tlsext_ticket_keys(ssl_ctx.get(), keys, sizeof(keys))) {
      return nullptr;
    }
    CopySessions(ssl_ctx.get(), old_ctx);
  } else if (!ticket_key.empty() &&
             !SSL_CTX_set_tlsext_ticket_keys(ssl_ctx.get(), ticket_key.data(),
                                             ticket_key.size())) {
    return nullptr;
  }

  if (install_cert_compression_algs &&
      (!SSL_CTX_add_cert_compression_alg(
           ssl_ctx.get(), 0xff02,
           [](SSL *ssl, CBB *out, const uint8_t *in, size_t in_len) -> int {
             if (!CBB_add_u8(out, 1) || !CBB_add_u8(out, 2) ||
                 !CBB_add_u8(out, 3) || !CBB_add_u8(out, 4) ||
                 !CBB_add_bytes(out, in, in_len)) {
               return 0;
             }
             return 1;
           },
           [](SSL *ssl, CRYPTO_BUFFER **out, size_t uncompressed_len,
              const uint8_t *in, size_t in_len) -> int {
             if (in_len < 4 || in[0] != 1 || in[1] != 2 || in[2] != 3 ||
                 in[3] != 4 || uncompressed_len != in_len - 4) {
               return 0;
             }
             const bssl::Span<const uint8_t> uncompressed(in + 4, in_len - 4);
             *out = CRYPTO_BUFFER_new(uncompressed.data(), uncompressed.size(),
                                      nullptr);
             return 1;
           }) ||
       !SSL_CTX_add_cert_compression_alg(
           ssl_ctx.get(), 0xff01,
           [](SSL *ssl, CBB *out, const uint8_t *in, size_t in_len) -> int {
             if (in_len < 2 || in[0] != 0 || in[1] != 0) {
               return 0;
             }
             return CBB_add_bytes(out, in + 2, in_len - 2);
           },
           [](SSL *ssl, CRYPTO_BUFFER **out, size_t uncompressed_len,
              const uint8_t *in, size_t in_len) -> int {
             if (uncompressed_len != 2 + in_len) {
               return 0;
             }
             std::unique_ptr<uint8_t[]> buf(new uint8_t[2 + in_len]);
             buf[0] = 0;
             buf[1] = 0;
             OPENSSL_memcpy(&buf[2], in, in_len);
             *out = CRYPTO_BUFFER_new(buf.get(), 2 + in_len, nullptr);
             return 1;
           }))) {
    fprintf(stderr, "SSL_CTX_add_cert_compression_alg failed.\n");
    abort();
  }

  if (server_preference) {
    SSL_CTX_set_options(ssl_ctx.get(), SSL_OP_CIPHER_SERVER_PREFERENCE);
  }

  if (enable_pq_experiment_signal) {
    SSL_CTX_enable_pq_experiment_signal(ssl_ctx.get());
  }

  return ssl_ctx;
}

static int DDoSCallback(const SSL_CLIENT_HELLO *client_hello) {
  const TestConfig *config = GetTestConfig(client_hello->ssl);
  return config->fail_ddos_callback ? 0 : 1;
}

static unsigned PskClientCallback(SSL *ssl, const char *hint,
                                  char *out_identity, unsigned max_identity_len,
                                  uint8_t *out_psk, unsigned max_psk_len) {
  const TestConfig *config = GetTestConfig(ssl);

  if (config->psk_identity.empty()) {
    if (hint != nullptr) {
      fprintf(stderr, "Server PSK hint was non-null.\n");
      return 0;
    }
  } else if (hint == nullptr ||
             strcmp(hint, config->psk_identity.c_str()) != 0) {
    fprintf(stderr, "Server PSK hint did not match.\n");
    return 0;
  }

  // Account for the trailing '\0' for the identity.
  if (config->psk_identity.size() >= max_identity_len ||
      config->psk.size() > max_psk_len) {
    fprintf(stderr, "PSK buffers too small.\n");
    return 0;
  }

  BUF_strlcpy(out_identity, config->psk_identity.c_str(), max_identity_len);
  OPENSSL_memcpy(out_psk, config->psk.data(), config->psk.size());
  return config->psk.size();
}

static unsigned PskServerCallback(SSL *ssl, const char *identity,
                                  uint8_t *out_psk, unsigned max_psk_len) {
  const TestConfig *config = GetTestConfig(ssl);

  if (strcmp(identity, config->psk_identity.c_str()) != 0) {
    fprintf(stderr, "Client PSK identity did not match.\n");
    return 0;
  }

  if (config->psk.size() > max_psk_len) {
    fprintf(stderr, "PSK buffers too small.\n");
    return 0;
  }

  OPENSSL_memcpy(out_psk, config->psk.data(), config->psk.size());
  return config->psk.size();
}

static ssl_verify_result_t CustomVerifyCallback(SSL *ssl, uint8_t *out_alert) {
  const TestConfig *config = GetTestConfig(ssl);
  if (!CheckVerifyCallback(ssl)) {
    return ssl_verify_invalid;
  }

  if (config->async && !GetTestState(ssl)->custom_verify_ready) {
    return ssl_verify_retry;
  }

  GetTestState(ssl)->cert_verified = true;
  if (config->verify_fail) {
    return ssl_verify_invalid;
  }

  return ssl_verify_ok;
}

static int CertCallback(SSL *ssl, void *arg) {
  const TestConfig *config = GetTestConfig(ssl);

  // Check the peer certificate metadata is as expected.
  if ((!SSL_is_server(ssl) && !CheckCertificateRequest(ssl)) ||
      !CheckPeerVerifyPrefs(ssl)) {
    return -1;
  }

  if (config->fail_cert_callback) {
    return 0;
  }

  // The certificate will be installed via other means.
  if (!config->async || config->use_early_callback) {
    return 1;
  }

  if (!GetTestState(ssl)->cert_ready) {
    return -1;
  }
  if (!InstallCertificate(ssl)) {
    return 0;
  }
  return 1;
}

bssl::UniquePtr<SSL> TestConfig::NewSSL(
    SSL_CTX *ssl_ctx, SSL_SESSION *session, bool is_resume,
    std::unique_ptr<TestState> test_state) const {
  bssl::UniquePtr<SSL> ssl(SSL_new(ssl_ctx));
  if (!ssl) {
    return nullptr;
  }

  if (!SetTestConfig(ssl.get(), this)) {
    return nullptr;
  }
  if (test_state != nullptr) {
    if (!SetTestState(ssl.get(), std::move(test_state))) {
      return nullptr;
    }
    GetTestState(ssl.get())->is_resume = is_resume;
  }

  if (fallback_scsv && !SSL_set_mode(ssl.get(), SSL_MODE_SEND_FALLBACK_SCSV)) {
    return nullptr;
  }
  // Install the certificate synchronously if nothing else will handle it.
  if (!use_early_callback && !use_old_client_cert_callback && !async &&
      !InstallCertificate(ssl.get())) {
    return nullptr;
  }
  if (!use_old_client_cert_callback) {
    SSL_set_cert_cb(ssl.get(), CertCallback, nullptr);
  }
  int mode = SSL_VERIFY_NONE;
  if (require_any_client_certificate) {
    mode = SSL_VERIFY_PEER | SSL_VERIFY_FAIL_IF_NO_PEER_CERT;
  }
  if (verify_peer) {
    mode = SSL_VERIFY_PEER;
  }
  if (verify_peer_if_no_obc) {
    // Set SSL_VERIFY_FAIL_IF_NO_PEER_CERT so testing whether client
    // certificates were requested is easy.
    mode = SSL_VERIFY_PEER | SSL_VERIFY_PEER_IF_NO_OBC |
           SSL_VERIFY_FAIL_IF_NO_PEER_CERT;
  }
  if (use_custom_verify_callback) {
    SSL_set_custom_verify(ssl.get(), mode, CustomVerifyCallback);
  } else if (mode != SSL_VERIFY_NONE) {
    SSL_set_verify(ssl.get(), mode, NULL);
  }
  if (false_start) {
    SSL_set_mode(ssl.get(), SSL_MODE_ENABLE_FALSE_START);
  }
  if (cbc_record_splitting) {
    SSL_set_mode(ssl.get(), SSL_MODE_CBC_RECORD_SPLITTING);
  }
  if (partial_write) {
    SSL_set_mode(ssl.get(), SSL_MODE_ENABLE_PARTIAL_WRITE);
  }
  if (reverify_on_resume) {
    SSL_CTX_set_reverify_on_resume(ssl_ctx, 1);
  }
  if (enforce_rsa_key_usage) {
    SSL_set_enforce_rsa_key_usage(ssl.get(), 1);
  }
  if (no_tls13) {
    SSL_set_options(ssl.get(), SSL_OP_NO_TLSv1_3);
  }
  if (no_tls12) {
    SSL_set_options(ssl.get(), SSL_OP_NO_TLSv1_2);
  }
  if (no_tls11) {
    SSL_set_options(ssl.get(), SSL_OP_NO_TLSv1_1);
  }
  if (no_tls1) {
    SSL_set_options(ssl.get(), SSL_OP_NO_TLSv1);
  }
  if (no_ticket) {
    SSL_set_options(ssl.get(), SSL_OP_NO_TICKET);
  }
  if (!expect_channel_id.empty() || enable_channel_id) {
    SSL_set_tls_channel_id_enabled(ssl.get(), 1);
  }
  if (!send_channel_id.empty()) {
    SSL_set_tls_channel_id_enabled(ssl.get(), 1);
    if (!async) {
      // The async case will be supplied by |ChannelIdCallback|.
      bssl::UniquePtr<EVP_PKEY> pkey = LoadPrivateKey(send_channel_id);
      if (!pkey || !SSL_set1_tls_channel_id(ssl.get(), pkey.get())) {
        return nullptr;
      }
    }
  }
  if (!send_token_binding_params.empty()) {
    SSL_set_token_binding_params(
        ssl.get(),
        reinterpret_cast<const uint8_t *>(send_token_binding_params.data()),
        send_token_binding_params.length());
  }
  if (!host_name.empty() &&
      !SSL_set_tlsext_host_name(ssl.get(), host_name.c_str())) {
    return nullptr;
  }
  if (!advertise_alpn.empty() &&
      SSL_set_alpn_protos(ssl.get(), (const uint8_t *)advertise_alpn.data(),
                          advertise_alpn.size()) != 0) {
    return nullptr;
  }
  if (!psk.empty()) {
    SSL_set_psk_client_callback(ssl.get(), PskClientCallback);
    SSL_set_psk_server_callback(ssl.get(), PskServerCallback);
  }
  if (!psk_identity.empty() &&
      !SSL_use_psk_identity_hint(ssl.get(), psk_identity.c_str())) {
    return nullptr;
  }
  if (!srtp_profiles.empty() &&
      !SSL_set_srtp_profiles(ssl.get(), srtp_profiles.c_str())) {
    return nullptr;
  }
  if (enable_ocsp_stapling) {
    SSL_enable_ocsp_stapling(ssl.get());
  }
  if (enable_signed_cert_timestamps) {
    SSL_enable_signed_cert_timestamps(ssl.get());
  }
  if (min_version != 0 &&
      !SSL_set_min_proto_version(ssl.get(), (uint16_t)min_version)) {
    return nullptr;
  }
  if (max_version != 0 &&
      !SSL_set_max_proto_version(ssl.get(), (uint16_t)max_version)) {
    return nullptr;
  }
  if (mtu != 0) {
    SSL_set_options(ssl.get(), SSL_OP_NO_QUERY_MTU);
    SSL_set_mtu(ssl.get(), mtu);
  }
  if (install_ddos_callback) {
    SSL_CTX_set_dos_protection_cb(ssl_ctx, DDoSCallback);
  }
  SSL_set_shed_handshake_config(ssl.get(), true);
  if (renegotiate_once) {
    SSL_set_renegotiate_mode(ssl.get(), ssl_renegotiate_once);
  }
  if (renegotiate_freely || forbid_renegotiation_after_handshake) {
    // |forbid_renegotiation_after_handshake| will disable renegotiation later.
    SSL_set_renegotiate_mode(ssl.get(), ssl_renegotiate_freely);
  }
  if (renegotiate_ignore) {
    SSL_set_renegotiate_mode(ssl.get(), ssl_renegotiate_ignore);
  }
  if (!check_close_notify) {
    SSL_set_quiet_shutdown(ssl.get(), 1);
  }
  if (!curves.empty()) {
    std::vector<int> nids;
    for (auto curve : curves) {
      switch (curve) {
        case SSL_CURVE_SECP224R1:
          nids.push_back(NID_secp224r1);
          break;

        case SSL_CURVE_SECP256R1:
          nids.push_back(NID_X9_62_prime256v1);
          break;

        case SSL_CURVE_SECP384R1:
          nids.push_back(NID_secp384r1);
          break;

        case SSL_CURVE_SECP521R1:
          nids.push_back(NID_secp521r1);
          break;

        case SSL_CURVE_X25519:
          nids.push_back(NID_X25519);
          break;

        case SSL_CURVE_CECPQ2:
          nids.push_back(NID_CECPQ2);
          break;
        case SSL_CURVE_CECPQ2b:
          nids.push_back(NID_CECPQ2b);
          break;
      }
      if (!SSL_set1_curves(ssl.get(), &nids[0], nids.size())) {
        return nullptr;
      }
    }
  }
  if (enable_all_curves) {
    static const int kAllCurves[] = {
        NID_secp224r1, NID_X9_62_prime256v1, NID_secp384r1, NID_secp521r1,
        NID_X25519,    NID_CECPQ2,           NID_CECPQ2b,
    };
    if (!SSL_set1_curves(ssl.get(), kAllCurves,
                         OPENSSL_ARRAY_SIZE(kAllCurves))) {
      return nullptr;
    }
  }
  if (initial_timeout_duration_ms > 0) {
    DTLSv1_set_initial_timeout_duration(ssl.get(), initial_timeout_duration_ms);
  }
  if (max_cert_list > 0) {
    SSL_set_max_cert_list(ssl.get(), max_cert_list);
  }
  if (retain_only_sha256_client_cert) {
    SSL_set_retain_only_sha256_of_client_certs(ssl.get(), 1);
  }
  if (max_send_fragment > 0) {
    SSL_set_max_send_fragment(ssl.get(), max_send_fragment);
  }
  if (!quic_transport_params.empty()) {
    if (!SSL_set_quic_transport_params(
            ssl.get(),
            reinterpret_cast<const uint8_t *>(quic_transport_params.data()),
            quic_transport_params.size())) {
      return nullptr;
    }
  }
  if (jdk11_workaround) {
    SSL_set_jdk11_workaround(ssl.get(), 1);
  }

  if (session != NULL) {
    if (!is_server) {
      if (SSL_set_session(ssl.get(), session) != 1) {
        return nullptr;
      }
    } else if (async) {
      // The internal session cache is disabled, so install the session
      // manually.
      SSL_SESSION_up_ref(session);
      GetTestState(ssl.get())->pending_session.reset(session);
    }
  }

  if (!delegated_credential.empty()) {
    std::string::size_type comma = delegated_credential.find(',');
    if (comma == std::string::npos) {
      fprintf(stderr,
              "failed to find comma in delegated credential argument.\n");
      return nullptr;
    }

    const std::string dc_hex = delegated_credential.substr(0, comma);
    const std::string pkcs8_hex = delegated_credential.substr(comma + 1);
    std::string dc, pkcs8;
    if (!HexDecode(&dc, dc_hex) || !HexDecode(&pkcs8, pkcs8_hex)) {
      fprintf(stderr, "failed to hex decode delegated credential argument.\n");
      return nullptr;
    }

    CBS dc_cbs(bssl::Span<const uint8_t>(
        reinterpret_cast<const uint8_t *>(dc.data()), dc.size()));
    CBS pkcs8_cbs(bssl::Span<const uint8_t>(
        reinterpret_cast<const uint8_t *>(pkcs8.data()), pkcs8.size()));

    bssl::UniquePtr<EVP_PKEY> priv(EVP_parse_private_key(&pkcs8_cbs));
    if (!priv) {
      fprintf(stderr, "failed to parse delegated credential private key.\n");
      return nullptr;
    }

    bssl::UniquePtr<CRYPTO_BUFFER> dc_buf(
        CRYPTO_BUFFER_new_from_CBS(&dc_cbs, nullptr));
    if (!SSL_set1_delegated_credential(ssl.get(), dc_buf.get(),
                                      priv.get(), nullptr)) {
      fprintf(stderr, "SSL_set1_delegated_credential failed.\n");
      return nullptr;
    }
  }

  return ssl;
}

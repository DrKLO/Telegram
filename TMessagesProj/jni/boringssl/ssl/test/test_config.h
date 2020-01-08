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

#ifndef HEADER_TEST_CONFIG
#define HEADER_TEST_CONFIG

#include <string>
#include <vector>

#include <openssl/base.h>
#include <openssl/x509.h>

#include "test_state.h"

struct TestConfig {
  int port = 0;
  bool is_server = false;
  bool is_dtls = false;
  int resume_count = 0;
  std::string write_settings;
  bool fallback_scsv = false;
  std::vector<int> signing_prefs;
  std::vector<int> verify_prefs;
  std::vector<int> expect_peer_verify_prefs;
  std::vector<int> curves;
  std::string key_file;
  std::string cert_file;
  std::string expect_server_name;
  std::string expect_certificate_types;
  bool require_any_client_certificate = false;
  std::string advertise_npn;
  std::string expect_next_proto;
  bool false_start = false;
  std::string select_next_proto;
  bool async = false;
  bool write_different_record_sizes = false;
  bool cbc_record_splitting = false;
  bool partial_write = false;
  bool no_tls13 = false;
  bool no_tls12 = false;
  bool no_tls11 = false;
  bool no_tls1 = false;
  bool no_ticket = false;
  std::string expect_channel_id;
  bool enable_channel_id = false;
  std::string send_channel_id;
  int expect_token_binding_param = -1;
  std::string send_token_binding_params;
  bool shim_writes_first = false;
  std::string host_name;
  std::string advertise_alpn;
  std::string expect_alpn;
  std::string expect_late_alpn;
  std::string expect_advertised_alpn;
  std::string select_alpn;
  bool decline_alpn = false;
  bool select_empty_alpn = false;
  std::string quic_transport_params;
  std::string expect_quic_transport_params;
  bool expect_session_miss = false;
  bool expect_extended_master_secret = false;
  std::string psk;
  std::string psk_identity;
  std::string srtp_profiles;
  bool enable_ocsp_stapling = false;
  std::string expect_ocsp_response;
  bool enable_signed_cert_timestamps = false;
  std::string expect_signed_cert_timestamps;
  int min_version = 0;
  int max_version = 0;
  int expect_version = 0;
  int mtu = 0;
  bool implicit_handshake = false;
  bool use_early_callback = false;
  bool fail_early_callback = false;
  bool install_ddos_callback = false;
  bool fail_ddos_callback = false;
  bool fail_cert_callback = false;
  std::string cipher;
  bool handshake_never_done = false;
  int export_keying_material = 0;
  std::string export_label;
  std::string export_context;
  bool use_export_context = false;
  bool tls_unique = false;
  bool expect_ticket_renewal = false;
  bool expect_no_session = false;
  bool expect_ticket_supports_early_data = false;
  bool expect_accept_early_data = false;
  bool expect_reject_early_data = false;
  bool expect_no_offer_early_data = false;
  bool use_ticket_callback = false;
  bool renew_ticket = false;
  bool enable_early_data = false;
  bool enable_client_custom_extension = false;
  bool enable_server_custom_extension = false;
  bool custom_extension_skip = false;
  bool custom_extension_fail_add = false;
  std::string ocsp_response;
  bool check_close_notify = false;
  bool shim_shuts_down = false;
  bool verify_fail = false;
  bool verify_peer = false;
  bool verify_peer_if_no_obc = false;
  bool expect_verify_result = false;
  std::string signed_cert_timestamps;
  int expect_total_renegotiations = 0;
  bool renegotiate_once = false;
  bool renegotiate_freely = false;
  bool renegotiate_ignore = false;
  bool forbid_renegotiation_after_handshake = false;
  int expect_peer_signature_algorithm = 0;
  bool enable_all_curves = false;
  int expect_curve_id = 0;
  bool use_old_client_cert_callback = false;
  int initial_timeout_duration_ms = 0;
  std::string use_client_ca_list;
  std::string expect_client_ca_list;
  bool send_alert = false;
  bool peek_then_read = false;
  bool enable_grease = false;
  int max_cert_list = 0;
  std::string ticket_key;
  bool use_exporter_between_reads = false;
  int expect_cipher_aes = 0;
  int expect_cipher_no_aes = 0;
  std::string expect_peer_cert_file;
  int resumption_delay = 0;
  bool retain_only_sha256_client_cert = false;
  bool expect_sha256_client_cert = false;
  bool read_with_unfinished_write = false;
  bool expect_secure_renegotiation = false;
  bool expect_no_secure_renegotiation = false;
  int max_send_fragment = 0;
  int read_size = 0;
  bool expect_session_id = false;
  bool expect_no_session_id = false;
  int expect_ticket_age_skew = 0;
  bool no_op_extra_handshake = false;
  bool handshake_twice = false;
  bool allow_unknown_alpn_protos = false;
  bool enable_ed25519 = false;
  bool use_custom_verify_callback = false;
  std::string expect_msg_callback;
  bool allow_false_start_without_alpn = false;
  bool ignore_tls13_downgrade = false;
  bool expect_tls13_downgrade = false;
  bool handoff = false;
  bool no_rsa_pss_rsae_certs = false;
  bool use_ocsp_callback = false;
  bool set_ocsp_in_callback = false;
  bool decline_ocsp_callback = false;
  bool fail_ocsp_callback = false;
  bool install_cert_compression_algs = false;
  bool reverify_on_resume = false;
  bool enforce_rsa_key_usage = false;
  bool is_handshaker_supported = false;
  bool handshaker_resume = false;
  std::string handshaker_path;
  bool jdk11_workaround = false;
  bool server_preference = false;
  bool export_traffic_secrets = false;
  bool key_update = false;
  bool expect_delegated_credential_used = false;
  std::string delegated_credential;
  std::string expect_early_data_reason;
  bool enable_pq_experiment_signal = false;
  bool expect_pq_experiment_signal = false;

  int argc;
  char **argv;

  bssl::UniquePtr<SSL_CTX> SetupCtx(SSL_CTX *old_ctx) const;

  bssl::UniquePtr<SSL> NewSSL(SSL_CTX *ssl_ctx, SSL_SESSION *session,
                              bool is_resume,
                              std::unique_ptr<TestState> test_state) const;
};

bool ParseConfig(int argc, char **argv, TestConfig *out_initial,
                 TestConfig *out_resume, TestConfig *out_retry);

bool SetTestConfig(SSL *ssl, const TestConfig *config);

const TestConfig *GetTestConfig(const SSL *ssl);

bool LoadCertificate(bssl::UniquePtr<X509> *out_x509,
                     bssl::UniquePtr<STACK_OF(X509)> *out_chain,
                     const std::string &file);

bssl::UniquePtr<EVP_PKEY> LoadPrivateKey(const std::string &file);

#endif  // HEADER_TEST_CONFIG

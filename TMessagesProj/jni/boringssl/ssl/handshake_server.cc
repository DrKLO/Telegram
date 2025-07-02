// Copyright 1995-2016 The OpenSSL Project Authors. All Rights Reserved.
// Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
// Copyright 2005 Nokia. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <openssl/ssl.h>

#include <assert.h>
#include <string.h>

#include <openssl/bn.h>
#include <openssl/bytestring.h>
#include <openssl/cipher.h>
#include <openssl/curve25519.h>
#include <openssl/digest.h>
#include <openssl/ec.h>
#include <openssl/ecdsa.h>
#include <openssl/err.h>
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <openssl/md5.h>
#include <openssl/mem.h>
#include <openssl/nid.h>
#include <openssl/rand.h>
#include <openssl/x509.h>

#include "../crypto/internal.h"
#include "internal.h"


BSSL_NAMESPACE_BEGIN

bool ssl_client_cipher_list_contains_cipher(
    const SSL_CLIENT_HELLO *client_hello, uint16_t id) {
  CBS cipher_suites;
  CBS_init(&cipher_suites, client_hello->cipher_suites,
           client_hello->cipher_suites_len);

  while (CBS_len(&cipher_suites) > 0) {
    uint16_t got_id;
    if (!CBS_get_u16(&cipher_suites, &got_id)) {
      return false;
    }

    if (got_id == id) {
      return true;
    }
  }

  return false;
}

static bool negotiate_version(SSL_HANDSHAKE *hs, uint8_t *out_alert,
                              const SSL_CLIENT_HELLO *client_hello) {
  SSL *const ssl = hs->ssl;
  assert(ssl->s3->version == 0);
  CBS supported_versions, versions;
  if (ssl_client_hello_get_extension(client_hello, &supported_versions,
                                     TLSEXT_TYPE_supported_versions)) {
    if (!CBS_get_u8_length_prefixed(&supported_versions, &versions) ||  //
        CBS_len(&supported_versions) != 0 ||                            //
        CBS_len(&versions) == 0) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_DECODE_ERROR);
      *out_alert = SSL_AD_DECODE_ERROR;
      return false;
    }
  } else {
    // Convert the ClientHello version to an equivalent supported_versions
    // extension.
    static const uint8_t kTLSVersions[] = {
        0x03, 0x03,  // TLS 1.2
        0x03, 0x02,  // TLS 1.1
        0x03, 0x01,  // TLS 1
    };

    static const uint8_t kDTLSVersions[] = {
        0xfe, 0xfd,  // DTLS 1.2
        0xfe, 0xff,  // DTLS 1.0
    };

    size_t versions_len = 0;
    if (SSL_is_dtls(ssl)) {
      if (client_hello->version <= DTLS1_2_VERSION) {
        versions_len = 4;
      } else if (client_hello->version <= DTLS1_VERSION) {
        versions_len = 2;
      }
      versions = Span(kDTLSVersions).last(versions_len);
    } else {
      if (client_hello->version >= TLS1_2_VERSION) {
        versions_len = 6;
      } else if (client_hello->version >= TLS1_1_VERSION) {
        versions_len = 4;
      } else if (client_hello->version >= TLS1_VERSION) {
        versions_len = 2;
      }
      versions = Span(kTLSVersions).last(versions_len);
    }
  }

  if (!ssl_negotiate_version(hs, out_alert, &ssl->s3->version, &versions)) {
    return false;
  }

  // Handle FALLBACK_SCSV.
  if (ssl_client_cipher_list_contains_cipher(client_hello,
                                             SSL3_CK_FALLBACK_SCSV & 0xffff) &&
      ssl_protocol_version(ssl) < hs->max_version) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INAPPROPRIATE_FALLBACK);
    *out_alert = SSL3_AD_INAPPROPRIATE_FALLBACK;
    return false;
  }

  return true;
}

static UniquePtr<STACK_OF(SSL_CIPHER)> ssl_parse_client_cipher_list(
    const SSL_CLIENT_HELLO *client_hello) {
  CBS cipher_suites;
  CBS_init(&cipher_suites, client_hello->cipher_suites,
           client_hello->cipher_suites_len);

  UniquePtr<STACK_OF(SSL_CIPHER)> sk(sk_SSL_CIPHER_new_null());
  if (!sk) {
    return nullptr;
  }

  while (CBS_len(&cipher_suites) > 0) {
    uint16_t cipher_suite;

    if (!CBS_get_u16(&cipher_suites, &cipher_suite)) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_ERROR_IN_RECEIVED_CIPHER_LIST);
      return nullptr;
    }

    const SSL_CIPHER *c = SSL_get_cipher_by_value(cipher_suite);
    if (c != NULL && !sk_SSL_CIPHER_push(sk.get(), c)) {
      return nullptr;
    }
  }

  return sk;
}

static const SSL_CIPHER *choose_cipher(SSL_HANDSHAKE *hs,
                                       const STACK_OF(SSL_CIPHER) *client_pref,
                                       uint32_t mask_k, uint32_t mask_a) {
  SSL *const ssl = hs->ssl;
  const STACK_OF(SSL_CIPHER) *prio, *allow;
  // in_group_flags will either be NULL, or will point to an array of bytes
  // which indicate equal-preference groups in the |prio| stack. See the
  // comment about |in_group_flags| in the |SSLCipherPreferenceList|
  // struct.
  const bool *in_group_flags;
  // group_min contains the minimal index so far found in a group, or -1 if no
  // such value exists yet.
  int group_min = -1;

  const SSLCipherPreferenceList *server_pref =
      hs->config->cipher_list ? hs->config->cipher_list.get()
                              : ssl->ctx->cipher_list.get();
  if (ssl->options & SSL_OP_CIPHER_SERVER_PREFERENCE) {
    prio = server_pref->ciphers.get();
    in_group_flags = server_pref->in_group_flags;
    allow = client_pref;
  } else {
    prio = client_pref;
    in_group_flags = NULL;
    allow = server_pref->ciphers.get();
  }

  for (size_t i = 0; i < sk_SSL_CIPHER_num(prio); i++) {
    const SSL_CIPHER *c = sk_SSL_CIPHER_value(prio, i);

    size_t cipher_index;
    if (  // Check if the cipher is supported for the current version.
        SSL_CIPHER_get_min_version(c) <= ssl_protocol_version(ssl) &&  //
        ssl_protocol_version(ssl) <= SSL_CIPHER_get_max_version(c) &&  //
        // Check the cipher is supported for the server configuration.
        (c->algorithm_mkey & mask_k) &&  //
        (c->algorithm_auth & mask_a) &&  //
        // Check the cipher is in the |allow| list.
        sk_SSL_CIPHER_find(allow, &cipher_index, c)) {
      if (in_group_flags != NULL && in_group_flags[i]) {
        // This element of |prio| is in a group. Update the minimum index found
        // so far and continue looking.
        if (group_min == -1 || (size_t)group_min > cipher_index) {
          group_min = cipher_index;
        }
      } else {
        if (group_min != -1 && (size_t)group_min < cipher_index) {
          cipher_index = group_min;
        }
        return sk_SSL_CIPHER_value(allow, cipher_index);
      }
    }

    if (in_group_flags != NULL && !in_group_flags[i] && group_min != -1) {
      // We are about to leave a group, but we found a match in it, so that's
      // our answer.
      return sk_SSL_CIPHER_value(allow, group_min);
    }
  }

  OPENSSL_PUT_ERROR(SSL, SSL_R_NO_SHARED_CIPHER);
  return nullptr;
}

struct TLS12ServerParams {
  bool ok() const { return cipher != nullptr; }

  const SSL_CIPHER *cipher = nullptr;
  uint16_t signature_algorithm = 0;
};

static TLS12ServerParams choose_params(SSL_HANDSHAKE *hs,
                                       const SSL_CREDENTIAL *cred,
                                       const STACK_OF(SSL_CIPHER) *client_pref,
                                       bool has_ecdhe_group) {
  // Determine the usable cipher suites.
  uint32_t mask_k = 0, mask_a = 0;
  if (has_ecdhe_group) {
    mask_k |= SSL_kECDHE;
  }
  if (hs->config->psk_server_callback != nullptr) {
    mask_k |= SSL_kPSK;
    mask_a |= SSL_aPSK;
  }
  uint16_t sigalg = 0;
  if (cred != nullptr && cred->type == SSLCredentialType::kX509) {
    bool sign_ok = tls1_choose_signature_algorithm(hs, cred, &sigalg);
    ERR_clear_error();

    // ECDSA keys must additionally be checked against the peer's supported
    // curve list.
    int key_type = EVP_PKEY_id(cred->pubkey.get());
    if (key_type == EVP_PKEY_EC) {
      EC_KEY *ec_key = EVP_PKEY_get0_EC_KEY(cred->pubkey.get());
      uint16_t group_id;
      if (!ssl_nid_to_group_id(
              &group_id, EC_GROUP_get_curve_name(EC_KEY_get0_group(ec_key))) ||
          std::find(hs->peer_supported_group_list.begin(),
                    hs->peer_supported_group_list.end(),
                    group_id) == hs->peer_supported_group_list.end()) {
        sign_ok = false;

        // If this would make us unable to pick any cipher, return an error.
        // This is not strictly necessary, but it gives us a more specific
        // error to help the caller diagnose issues.
        if (mask_a == 0) {
          OPENSSL_PUT_ERROR(SSL, SSL_R_WRONG_CURVE);
          return TLS12ServerParams();
        }
      }
    }

    mask_a |= ssl_cipher_auth_mask_for_key(cred->pubkey.get(), sign_ok);
    if (key_type == EVP_PKEY_RSA) {
      mask_k |= SSL_kRSA;
    }
  }

  TLS12ServerParams params;
  params.cipher = choose_cipher(hs, client_pref, mask_k, mask_a);
  if (params.cipher == nullptr ||
      (cred != nullptr &&
       !ssl_credential_matches_requested_issuers(hs, cred))) {
    return TLS12ServerParams();
  }
  // Only report the selected signature algorithm if it will be used.
  if (ssl_cipher_requires_server_key_exchange(params.cipher) &&
      ssl_cipher_uses_certificate_auth(params.cipher)) {
    params.signature_algorithm = sigalg;
  }
  return params;
}

static enum ssl_hs_wait_t do_start_accept(SSL_HANDSHAKE *hs) {
  ssl_do_info_callback(hs->ssl, SSL_CB_HANDSHAKE_START, 1);
  hs->state = state12_read_client_hello;
  return ssl_hs_ok;
}

// is_probably_jdk11_with_tls13 returns whether |client_hello| was probably sent
// from a JDK 11 client with both TLS 1.3 and a prior version enabled.
static bool is_probably_jdk11_with_tls13(const SSL_CLIENT_HELLO *client_hello) {
  // JDK 11 ClientHellos contain a number of unusual properties which should
  // limit false positives.

  // JDK 11 does not support ChaCha20-Poly1305. This is unusual: many modern
  // clients implement ChaCha20-Poly1305.
  if (ssl_client_cipher_list_contains_cipher(
          client_hello, TLS1_3_CK_CHACHA20_POLY1305_SHA256 & 0xffff)) {
    return false;
  }

  // JDK 11 always sends extensions in a particular order.
  constexpr uint16_t kMaxFragmentLength = 0x0001;
  constexpr uint16_t kStatusRequestV2 = 0x0011;
  static constexpr struct {
    uint16_t id;
    bool required;
  } kJavaExtensions[] = {
      {TLSEXT_TYPE_server_name, false},
      {kMaxFragmentLength, false},
      {TLSEXT_TYPE_status_request, false},
      {TLSEXT_TYPE_supported_groups, true},
      {TLSEXT_TYPE_ec_point_formats, false},
      {TLSEXT_TYPE_signature_algorithms, true},
      // Java always sends signature_algorithms_cert.
      {TLSEXT_TYPE_signature_algorithms_cert, true},
      {TLSEXT_TYPE_application_layer_protocol_negotiation, false},
      {kStatusRequestV2, false},
      {TLSEXT_TYPE_extended_master_secret, false},
      {TLSEXT_TYPE_supported_versions, true},
      {TLSEXT_TYPE_cookie, false},
      {TLSEXT_TYPE_psk_key_exchange_modes, true},
      {TLSEXT_TYPE_key_share, true},
      {TLSEXT_TYPE_renegotiate, false},
      {TLSEXT_TYPE_pre_shared_key, false},
  };
  Span<const uint8_t> sigalgs, sigalgs_cert;
  bool has_status_request = false, has_status_request_v2 = false;
  CBS extensions, supported_groups;
  CBS_init(&extensions, client_hello->extensions, client_hello->extensions_len);
  for (const auto &java_extension : kJavaExtensions) {
    CBS copy = extensions;
    uint16_t id;
    if (CBS_get_u16(&copy, &id) && id == java_extension.id) {
      // The next extension is the one we expected.
      extensions = copy;
      CBS body;
      if (!CBS_get_u16_length_prefixed(&extensions, &body)) {
        return false;
      }
      switch (id) {
        case TLSEXT_TYPE_status_request:
          has_status_request = true;
          break;
        case kStatusRequestV2:
          has_status_request_v2 = true;
          break;
        case TLSEXT_TYPE_signature_algorithms:
          sigalgs = body;
          break;
        case TLSEXT_TYPE_signature_algorithms_cert:
          sigalgs_cert = body;
          break;
        case TLSEXT_TYPE_supported_groups:
          supported_groups = body;
          break;
      }
    } else if (java_extension.required) {
      return false;
    }
  }
  if (CBS_len(&extensions) != 0) {
    return false;
  }

  // JDK 11 never advertises X25519. It is not offered by default, and
  // -Djdk.tls.namedGroups=x25519 does not work. This is unusual: many modern
  // clients implement X25519.
  while (CBS_len(&supported_groups) > 0) {
    uint16_t group;
    if (!CBS_get_u16(&supported_groups, &group) ||  //
        group == SSL_GROUP_X25519) {
      return false;
    }
  }

  if (  // JDK 11 always sends the same contents in signature_algorithms and
        // signature_algorithms_cert. This is unusual:
        // signature_algorithms_cert, if omitted, is treated as if it were
        // signature_algorithms.
      sigalgs != sigalgs_cert ||
      // When TLS 1.2 or below is enabled, JDK 11 sends status_request_v2 iff it
      // sends status_request. This is unusual: status_request_v2 is not widely
      // implemented.
      has_status_request != has_status_request_v2) {
    return false;
  }

  return true;
}

static bool decrypt_ech(SSL_HANDSHAKE *hs, uint8_t *out_alert,
                        const SSL_CLIENT_HELLO *client_hello) {
  SSL *const ssl = hs->ssl;
  CBS body;
  if (!ssl_client_hello_get_extension(client_hello, &body,
                                      TLSEXT_TYPE_encrypted_client_hello)) {
    return true;
  }
  uint8_t type;
  if (!CBS_get_u8(&body, &type)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_DECODE_ERROR);
    *out_alert = SSL_AD_DECODE_ERROR;
    return false;
  }
  if (type != ECH_CLIENT_OUTER) {
    return true;
  }
  // This is a ClientHelloOuter ECH extension. Attempt to decrypt it.
  uint8_t config_id;
  uint16_t kdf_id, aead_id;
  CBS enc, payload;
  if (!CBS_get_u16(&body, &kdf_id) ||   //
      !CBS_get_u16(&body, &aead_id) ||  //
      !CBS_get_u8(&body, &config_id) ||
      !CBS_get_u16_length_prefixed(&body, &enc) ||
      !CBS_get_u16_length_prefixed(&body, &payload) ||  //
      CBS_len(&body) != 0) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_DECODE_ERROR);
    *out_alert = SSL_AD_DECODE_ERROR;
    return false;
  }

  {
    MutexReadLock lock(&ssl->ctx->lock);
    hs->ech_keys = UpRef(ssl->ctx->ech_keys);
  }

  if (!hs->ech_keys) {
    ssl->s3->ech_status = ssl_ech_rejected;
    return true;
  }

  for (const auto &config : hs->ech_keys->configs) {
    hs->ech_hpke_ctx.Reset();
    if (config_id != config->ech_config().config_id ||
        !config->SetupContext(hs->ech_hpke_ctx.get(), kdf_id, aead_id, enc)) {
      // Ignore the error and try another ECHConfig.
      ERR_clear_error();
      continue;
    }
    bool is_decrypt_error;
    if (!ssl_client_hello_decrypt(hs, out_alert, &is_decrypt_error,
                                  &hs->ech_client_hello_buf, client_hello,
                                  payload)) {
      if (is_decrypt_error) {
        // Ignore the error and try another ECHConfig.
        ERR_clear_error();
        // The |out_alert| calling convention currently relies on a default of
        // |SSL_AD_DECODE_ERROR|. https://crbug.com/boringssl/373 tracks
        // switching to sum types, which avoids this.
        *out_alert = SSL_AD_DECODE_ERROR;
        continue;
      }
      OPENSSL_PUT_ERROR(SSL, SSL_R_DECRYPTION_FAILED);
      return false;
    }
    hs->ech_config_id = config_id;
    ssl->s3->ech_status = ssl_ech_accepted;
    return true;
  }

  // If we did not accept ECH, proceed with the ClientHelloOuter. Note this
  // could be key mismatch or ECH GREASE, so we must complete the handshake
  // as usual, except EncryptedExtensions will contain retry configs.
  ssl->s3->ech_status = ssl_ech_rejected;
  return true;
}

static bool extract_sni(SSL_HANDSHAKE *hs, uint8_t *out_alert,
                        const SSL_CLIENT_HELLO *client_hello) {
  SSL *const ssl = hs->ssl;
  CBS sni;
  if (!ssl_client_hello_get_extension(client_hello, &sni,
                                      TLSEXT_TYPE_server_name)) {
    // No SNI extension to parse.
    //
    // Clear state in case we previously extracted SNI from ClientHelloOuter.
    ssl->s3->hostname.reset();
    return true;
  }

  CBS server_name_list, host_name;
  uint8_t name_type;
  if (!CBS_get_u16_length_prefixed(&sni, &server_name_list) ||  //
      !CBS_get_u8(&server_name_list, &name_type) ||             //
      // Although the server_name extension was intended to be extensible to
      // new name types and multiple names, OpenSSL 1.0.x had a bug which meant
      // different name types will cause an error. Further, RFC 4366 originally
      // defined syntax inextensibly. RFC 6066 corrected this mistake, but
      // adding new name types is no longer feasible.
      //
      // Act as if the extensibility does not exist to simplify parsing.
      !CBS_get_u16_length_prefixed(&server_name_list, &host_name) ||  //
      CBS_len(&server_name_list) != 0 ||                              //
      CBS_len(&sni) != 0) {
    *out_alert = SSL_AD_DECODE_ERROR;
    return false;
  }

  if (name_type != TLSEXT_NAMETYPE_host_name ||         //
      CBS_len(&host_name) == 0 ||                       //
      CBS_len(&host_name) > TLSEXT_MAXLEN_host_name ||  //
      CBS_contains_zero_byte(&host_name)) {
    *out_alert = SSL_AD_UNRECOGNIZED_NAME;
    return false;
  }

  // Copy the hostname as a string.
  char *raw = nullptr;
  if (!CBS_strdup(&host_name, &raw)) {
    *out_alert = SSL_AD_INTERNAL_ERROR;
    return false;
  }
  ssl->s3->hostname.reset(raw);
  return true;
}

static enum ssl_hs_wait_t do_read_client_hello(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;

  SSLMessage msg;
  if (!ssl->method->get_message(ssl, &msg)) {
    return ssl_hs_read_message;
  }

  if (!ssl_check_message_type(ssl, msg, SSL3_MT_CLIENT_HELLO)) {
    return ssl_hs_error;
  }

  SSL_CLIENT_HELLO client_hello;
  if (!SSL_parse_client_hello(ssl, &client_hello, CBS_data(&msg.body),
                              CBS_len(&msg.body))) {
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_DECODE_ERROR);
    return ssl_hs_error;
  }

  // ClientHello should be the end of the flight. We check this early to cover
  // all protocol versions.
  if (ssl->method->has_unprocessed_handshake_data(ssl)) {
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_UNEXPECTED_MESSAGE);
    OPENSSL_PUT_ERROR(SSL, SSL_R_EXCESS_HANDSHAKE_DATA);
    return ssl_hs_error;
  }

  if (hs->config->handoff) {
    return ssl_hs_handoff;
  }

  uint8_t alert = SSL_AD_DECODE_ERROR;
  // We check for rejection status in case we've rewound the state machine after
  // determining `ClientHelloInner` is invalid.
  if (ssl->s3->ech_status != ssl_ech_rejected &&
      !decrypt_ech(hs, &alert, &client_hello)) {
    ssl_send_alert(ssl, SSL3_AL_FATAL, alert);
    return ssl_hs_error;
  }

  // ECH may have changed which ClientHello we process. Update |msg| and
  // |client_hello| in case.
  if (!hs->GetClientHello(&msg, &client_hello)) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
    return ssl_hs_error;
  }

  if (!extract_sni(hs, &alert, &client_hello)) {
    ssl_send_alert(ssl, SSL3_AL_FATAL, alert);
    return ssl_hs_error;
  }

  hs->state = state12_read_client_hello_after_ech;
  return ssl_hs_ok;
}

static enum ssl_hs_wait_t do_read_client_hello_after_ech(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;

  SSLMessage msg_unused;
  SSL_CLIENT_HELLO client_hello;
  if (!hs->GetClientHello(&msg_unused, &client_hello)) {
    return ssl_hs_error;
  }

  // Run the early callback.
  if (ssl->ctx->select_certificate_cb != NULL) {
    switch (ssl->ctx->select_certificate_cb(&client_hello)) {
      case ssl_select_cert_retry:
        return ssl_hs_certificate_selection_pending;

      case ssl_select_cert_disable_ech:
        hs->ech_client_hello_buf.Reset();
        hs->ech_keys = nullptr;
        hs->state = state12_read_client_hello;
        ssl->s3->ech_status = ssl_ech_rejected;
        return ssl_hs_ok;

      case ssl_select_cert_error:
        // Connection rejected.
        OPENSSL_PUT_ERROR(SSL, SSL_R_CONNECTION_REJECTED);
        ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_HANDSHAKE_FAILURE);
        return ssl_hs_error;

      default:
          /* fallthrough */;
    }
  }

  // Freeze the version range after the early callback.
  if (!ssl_get_version_range(hs, &hs->min_version, &hs->max_version)) {
    return ssl_hs_error;
  }

  if (hs->config->jdk11_workaround &&
      is_probably_jdk11_with_tls13(&client_hello)) {
    hs->apply_jdk11_workaround = true;
  }

  uint8_t alert = SSL_AD_DECODE_ERROR;
  if (!negotiate_version(hs, &alert, &client_hello)) {
    ssl_send_alert(ssl, SSL3_AL_FATAL, alert);
    return ssl_hs_error;
  }

  hs->client_version = client_hello.version;
  if (client_hello.random_len != SSL3_RANDOM_SIZE) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
    return ssl_hs_error;
  }
  OPENSSL_memcpy(ssl->s3->client_random, client_hello.random,
                 client_hello.random_len);

  // Only null compression is supported. TLS 1.3 further requires the peer
  // advertise no other compression.
  if (OPENSSL_memchr(client_hello.compression_methods, 0,
                     client_hello.compression_methods_len) == NULL ||
      (ssl_protocol_version(ssl) >= TLS1_3_VERSION &&
       client_hello.compression_methods_len != 1)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_INVALID_COMPRESSION_LIST);
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_ILLEGAL_PARAMETER);
    return ssl_hs_error;
  }

  // TLS extensions.
  if (!ssl_parse_clienthello_tlsext(hs, &client_hello)) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_PARSE_TLSEXT);
    return ssl_hs_error;
  }

  hs->state = state12_cert_callback;
  return ssl_hs_ok;
}

static enum ssl_hs_wait_t do_cert_callback(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;

  // Call |cert_cb| to update server certificates if required.
  if (hs->config->cert->cert_cb != NULL) {
    int rv = hs->config->cert->cert_cb(ssl, hs->config->cert->cert_cb_arg);
    if (rv == 0) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_CERT_CB_ERROR);
      ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_INTERNAL_ERROR);
      return ssl_hs_error;
    }
    if (rv < 0) {
      return ssl_hs_x509_lookup;
    }
  }

  if (hs->ocsp_stapling_requested &&
      ssl->ctx->legacy_ocsp_callback != nullptr) {
    switch (ssl->ctx->legacy_ocsp_callback(
        ssl, ssl->ctx->legacy_ocsp_callback_arg)) {
      case SSL_TLSEXT_ERR_OK:
        break;
      case SSL_TLSEXT_ERR_NOACK:
        hs->ocsp_stapling_requested = false;
        break;
      default:
        OPENSSL_PUT_ERROR(SSL, SSL_R_OCSP_CB_ERROR);
        ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_INTERNAL_ERROR);
        return ssl_hs_error;
    }
  }

  if (ssl_protocol_version(ssl) >= TLS1_3_VERSION) {
    // Jump to the TLS 1.3 state machine.
    hs->state = state12_tls13;
    return ssl_hs_ok;
  }

  // It should not be possible to negotiate TLS 1.2 with ECH. The
  // ClientHelloInner decoding function rejects ClientHellos which offer TLS 1.2
  // or below.
  assert(ssl->s3->ech_status != ssl_ech_accepted);

  ssl->s3->early_data_reason = ssl_early_data_protocol_version;

  hs->state = state12_select_parameters;
  return ssl_hs_ok;
}

static enum ssl_hs_wait_t do_tls13(SSL_HANDSHAKE *hs) {
  enum ssl_hs_wait_t wait = tls13_server_handshake(hs);
  if (wait == ssl_hs_ok) {
    hs->state = state12_finish_server_handshake;
    return ssl_hs_ok;
  }

  return wait;
}

static enum ssl_hs_wait_t do_select_parameters(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;
  SSLMessage msg;
  SSL_CLIENT_HELLO client_hello;
  if (!hs->GetClientHello(&msg, &client_hello)) {
    return ssl_hs_error;
  }

  // Determine the ECDHE group to use, if we are to use ECDHE.
  uint16_t group_id = 0;
  bool has_ecdhe_group = tls1_get_shared_group(hs, &group_id);

  // Select the credential and cipher suite. This must be done after |cert_cb|
  // runs, so the final credential list is known.
  //
  // TODO(davidben): In the course of picking these, we also pick the ECDHE
  // group and signature algorithm. It would be tidier if we saved that decision
  // and avoided redoing it later.
  UniquePtr<STACK_OF(SSL_CIPHER)> client_pref =
      ssl_parse_client_cipher_list(&client_hello);
  if (client_pref == nullptr) {
    return ssl_hs_error;
  }
  Array<SSL_CREDENTIAL *> creds;
  if (!ssl_get_full_credential_list(hs, &creds)) {
    return ssl_hs_error;
  }
  TLS12ServerParams params;
  if (creds.empty()) {
    // The caller may have configured no credentials, but set a PSK callback.
    params =
        choose_params(hs, /*cred=*/nullptr, client_pref.get(), has_ecdhe_group);
  } else {
    // Select the first credential which works.
    for (SSL_CREDENTIAL *cred : creds) {
      ERR_clear_error();
      params = choose_params(hs, cred, client_pref.get(), has_ecdhe_group);
      if (params.ok()) {
        hs->credential = UpRef(cred);
        break;
      }
    }
  }
  if (!params.ok()) {
    // The error from the last attempt is in the error queue.
    assert(ERR_peek_error() != 0);
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_HANDSHAKE_FAILURE);
    return ssl_hs_error;
  }
  hs->new_cipher = params.cipher;
  hs->signature_algorithm = params.signature_algorithm;

  // |SSL_parse_client_hello| checks that |client_hello.session_id| is not too
  // large.
  hs->session_id.CopyFrom(
      Span(client_hello.session_id, client_hello.session_id_len));

  // Determine whether we are doing session resumption.
  UniquePtr<SSL_SESSION> session;
  bool tickets_supported = false, renew_ticket = false;
  enum ssl_hs_wait_t wait = ssl_get_prev_session(
      hs, &session, &tickets_supported, &renew_ticket, &client_hello);
  if (wait != ssl_hs_ok) {
    return wait;
  }

  if (session) {
    if (session->extended_master_secret && !hs->extended_master_secret) {
      // A ClientHello without EMS that attempts to resume a session with EMS
      // is fatal to the connection.
      OPENSSL_PUT_ERROR(SSL, SSL_R_RESUMED_EMS_SESSION_WITHOUT_EMS_EXTENSION);
      ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_HANDSHAKE_FAILURE);
      return ssl_hs_error;
    }

    if (!ssl_session_is_resumable(hs, session.get()) ||
        // If the client offers the EMS extension, but the previous session
        // didn't use it, then negotiate a new session.
        hs->extended_master_secret != session->extended_master_secret) {
      session.reset();
    }
  }

  if (session) {
    // Use the old session.
    hs->ticket_expected = renew_ticket;
    ssl->session = std::move(session);
    ssl->s3->session_reused = true;
    hs->can_release_private_key = true;
  } else {
    hs->ticket_expected = tickets_supported;
    ssl_set_session(ssl, nullptr);
    if (!ssl_get_new_session(hs)) {
      return ssl_hs_error;
    }

    // Assign a session ID if not using session tickets.
    if (!hs->ticket_expected &&
        (ssl->ctx->session_cache_mode & SSL_SESS_CACHE_SERVER)) {
      hs->new_session->session_id.ResizeForOverwrite(
          SSL3_SSL_SESSION_ID_LENGTH);
      RAND_bytes(hs->new_session->session_id.data(),
                 hs->new_session->session_id.size());
    }
  }

  if (ssl->ctx->dos_protection_cb != NULL &&
      ssl->ctx->dos_protection_cb(&client_hello) == 0) {
    // Connection rejected for DOS reasons.
    OPENSSL_PUT_ERROR(SSL, SSL_R_CONNECTION_REJECTED);
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_INTERNAL_ERROR);
    return ssl_hs_error;
  }

  if (ssl->session == NULL) {
    hs->new_session->cipher = hs->new_cipher;
    if (hs->new_session->cipher->algorithm_mkey & SSL_kECDHE) {
      assert(has_ecdhe_group);
      hs->new_session->group_id = group_id;
    }

    // Determine whether to request a client certificate. CertificateRequest may
    // only be sent in certificate-based ciphers.
    hs->cert_request = (hs->config->verify_mode & SSL_VERIFY_PEER) &&
                       ssl_cipher_uses_certificate_auth(hs->new_cipher);
    if (!hs->cert_request) {
      // OpenSSL returns X509_V_OK when no certificates are requested. This is
      // classed by them as a bug, but it's assumed by at least NGINX.
      hs->new_session->verify_result = X509_V_OK;
    }
  }

  // HTTP/2 negotiation depends on the cipher suite, so ALPN negotiation was
  // deferred. Complete it now.
  uint8_t alert = SSL_AD_DECODE_ERROR;
  if (!ssl_negotiate_alpn(hs, &alert, &client_hello)) {
    ssl_send_alert(ssl, SSL3_AL_FATAL, alert);
    return ssl_hs_error;
  }

  // Now that all parameters are known, initialize the handshake hash and hash
  // the ClientHello.
  if (!hs->transcript.InitHash(ssl_protocol_version(ssl), hs->new_cipher)) {
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_INTERNAL_ERROR);
    return ssl_hs_error;
  }

  // Handback includes the whole handshake transcript, so we cannot free the
  // transcript buffer in the handback case.
  if (!hs->cert_request && !hs->handback) {
    hs->transcript.FreeBuffer();
  }

  if (!ssl_hash_message(hs, msg)) {
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_INTERNAL_ERROR);
    return ssl_hs_error;
  }

  ssl->method->next_message(ssl);

  hs->state = state12_send_server_hello;
  return ssl_hs_ok;
}

static void copy_suffix(Span<uint8_t> out, Span<const uint8_t> in) {
  out = out.last(in.size());
  OPENSSL_memcpy(out.data(), in.data(), in.size());
}

static enum ssl_hs_wait_t do_send_server_hello(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;

  // We only accept ChannelIDs on connections with ECDHE in order to avoid a
  // known attack while we fix ChannelID itself.
  if (hs->channel_id_negotiated &&
      (hs->new_cipher->algorithm_mkey & SSL_kECDHE) == 0) {
    hs->channel_id_negotiated = false;
  }

  // If this is a resumption and the original handshake didn't support
  // ChannelID then we didn't record the original handshake hashes in the
  // session and so cannot resume with ChannelIDs.
  if (ssl->session != nullptr &&
      ssl->session->original_handshake_hash.empty()) {
    hs->channel_id_negotiated = false;
  }

  SSL_HANDSHAKE_HINTS *const hints = hs->hints.get();
  if (hints && !hs->hints_requested &&
      hints->server_random_tls12.size() == SSL3_RANDOM_SIZE) {
    OPENSSL_memcpy(ssl->s3->server_random, hints->server_random_tls12.data(),
                   SSL3_RANDOM_SIZE);
  } else {
    OPENSSL_timeval now = ssl_ctx_get_current_time(ssl->ctx.get());
    CRYPTO_store_u32_be(ssl->s3->server_random,
                        static_cast<uint32_t>(now.tv_sec));
    if (!RAND_bytes(ssl->s3->server_random + 4, SSL3_RANDOM_SIZE - 4)) {
      return ssl_hs_error;
    }
    if (hints && hs->hints_requested &&
        !hints->server_random_tls12.CopyFrom(ssl->s3->server_random)) {
      return ssl_hs_error;
    }
  }

  // Implement the TLS 1.3 anti-downgrade feature.
  if (hs->max_version >= TLS1_3_VERSION) {
    if (ssl_protocol_version(ssl) == TLS1_2_VERSION) {
      if (hs->apply_jdk11_workaround) {
        // JDK 11 implements the TLS 1.3 downgrade signal, so we cannot send it
        // here. However, the signal is only effective if all TLS 1.2
        // ServerHellos produced by the server are marked. Thus we send a
        // different non-standard signal for the time being, until JDK 11.0.2 is
        // released and clients have updated.
        copy_suffix(ssl->s3->server_random, kJDK11DowngradeRandom);
      } else {
        copy_suffix(ssl->s3->server_random, kTLS13DowngradeRandom);
      }
    } else {
      copy_suffix(ssl->s3->server_random, kTLS12DowngradeRandom);
    }
  }

  Span<const uint8_t> session_id;
  if (ssl->session != nullptr) {
    // Echo the session ID from the ClientHello to indicate resumption.
    session_id = hs->session_id;
  } else {
    session_id = hs->new_session->session_id;
  }

  ScopedCBB cbb;
  CBB body, session_id_bytes;
  if (!ssl->method->init_message(ssl, cbb.get(), &body, SSL3_MT_SERVER_HELLO) ||
      !CBB_add_u16(&body, ssl->s3->version) ||
      !CBB_add_bytes(&body, ssl->s3->server_random, SSL3_RANDOM_SIZE) ||
      !CBB_add_u8_length_prefixed(&body, &session_id_bytes) ||
      !CBB_add_bytes(&session_id_bytes, session_id.data(), session_id.size()) ||
      !CBB_add_u16(&body, SSL_CIPHER_get_protocol_id(hs->new_cipher)) ||
      !CBB_add_u8(&body, 0 /* no compression */) ||
      !ssl_add_serverhello_tlsext(hs, &body) ||
      !ssl_add_message_cbb(ssl, cbb.get())) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
    return ssl_hs_error;
  }

  if (ssl->session != nullptr) {
    // No additional hints to generate in resumption.
    if (hs->hints_requested) {
      return ssl_hs_hints_ready;
    }
    hs->state = state12_send_server_finished;
  } else {
    hs->state = state12_send_server_certificate;
  }
  return ssl_hs_ok;
}

static enum ssl_hs_wait_t do_send_server_certificate(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;
  ScopedCBB cbb;

  if (ssl_cipher_uses_certificate_auth(hs->new_cipher)) {
    assert(hs->credential != nullptr);
    if (!ssl_send_tls12_certificate(hs)) {
      return ssl_hs_error;
    }

    if (hs->certificate_status_expected) {
      CBB body, ocsp_response;
      if (!ssl->method->init_message(ssl, cbb.get(), &body,
                                     SSL3_MT_CERTIFICATE_STATUS) ||
          !CBB_add_u8(&body, TLSEXT_STATUSTYPE_ocsp) ||
          !CBB_add_u24_length_prefixed(&body, &ocsp_response) ||
          !CBB_add_bytes(
              &ocsp_response,
              CRYPTO_BUFFER_data(hs->credential->ocsp_response.get()),
              CRYPTO_BUFFER_len(hs->credential->ocsp_response.get())) ||
          !ssl_add_message_cbb(ssl, cbb.get())) {
        OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
        return ssl_hs_error;
      }
    }
  }

  // Assemble ServerKeyExchange parameters if needed.
  uint32_t alg_k = hs->new_cipher->algorithm_mkey;
  uint32_t alg_a = hs->new_cipher->algorithm_auth;
  if (ssl_cipher_requires_server_key_exchange(hs->new_cipher) ||
      ((alg_a & SSL_aPSK) && hs->config->psk_identity_hint)) {
    // Pre-allocate enough room to comfortably fit an ECDHE public key. Prepend
    // the client and server randoms for the signing transcript.
    CBB child;
    if (!CBB_init(cbb.get(), SSL3_RANDOM_SIZE * 2 + 128) ||
        !CBB_add_bytes(cbb.get(), ssl->s3->client_random, SSL3_RANDOM_SIZE) ||
        !CBB_add_bytes(cbb.get(), ssl->s3->server_random, SSL3_RANDOM_SIZE)) {
      return ssl_hs_error;
    }

    // PSK ciphers begin with an identity hint.
    if (alg_a & SSL_aPSK) {
      size_t len = hs->config->psk_identity_hint == nullptr
                       ? 0
                       : strlen(hs->config->psk_identity_hint.get());
      if (!CBB_add_u16_length_prefixed(cbb.get(), &child) ||
          !CBB_add_bytes(&child,
                         (const uint8_t *)hs->config->psk_identity_hint.get(),
                         len)) {
        return ssl_hs_error;
      }
    }

    if (alg_k & SSL_kECDHE) {
      assert(hs->new_session->group_id != 0);
      hs->key_shares[0] = SSLKeyShare::Create(hs->new_session->group_id);
      if (!hs->key_shares[0] ||                                  //
          !CBB_add_u8(cbb.get(), NAMED_CURVE_TYPE) ||            //
          !CBB_add_u16(cbb.get(), hs->new_session->group_id) ||  //
          !CBB_add_u8_length_prefixed(cbb.get(), &child)) {
        return ssl_hs_error;
      }

      SSL_HANDSHAKE_HINTS *const hints = hs->hints.get();
      bool hint_ok = false;
      if (hints && !hs->hints_requested &&
          hints->ecdhe_group_id == hs->new_session->group_id &&
          !hints->ecdhe_public_key.empty() &&
          !hints->ecdhe_private_key.empty()) {
        CBS cbs = CBS(hints->ecdhe_private_key);
        hint_ok = hs->key_shares[0]->DeserializePrivateKey(&cbs);
      }
      if (hint_ok) {
        // Reuse the ECDH key from handshake hints.
        if (!CBB_add_bytes(&child, hints->ecdhe_public_key.data(),
                           hints->ecdhe_public_key.size())) {
          return ssl_hs_error;
        }
      } else {
        // Generate a key, and emit the public half.
        if (!hs->key_shares[0]->Generate(&child)) {
          return ssl_hs_error;
        }
        // If generating hints, save the ECDHE key.
        if (hints && hs->hints_requested) {
          bssl::ScopedCBB private_key_cbb;
          if (!hints->ecdhe_public_key.CopyFrom(
                  Span(CBB_data(&child), CBB_len(&child))) ||
              !CBB_init(private_key_cbb.get(), 32) ||
              !hs->key_shares[0]->SerializePrivateKey(private_key_cbb.get()) ||
              !CBBFinishArray(private_key_cbb.get(),
                              &hints->ecdhe_private_key)) {
            return ssl_hs_error;
          }
          hints->ecdhe_group_id = hs->new_session->group_id;
        }
      }
    } else {
      assert(alg_k & SSL_kPSK);
    }

    if (!CBBFinishArray(cbb.get(), &hs->server_params)) {
      return ssl_hs_error;
    }
  }

  hs->state = state12_send_server_key_exchange;
  return ssl_hs_ok;
}

static enum ssl_hs_wait_t do_send_server_key_exchange(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;

  if (hs->server_params.size() == 0) {
    hs->state = state12_send_server_hello_done;
    return ssl_hs_ok;
  }

  ScopedCBB cbb;
  CBB body, child;
  if (!ssl->method->init_message(ssl, cbb.get(), &body,
                                 SSL3_MT_SERVER_KEY_EXCHANGE) ||
      // |hs->server_params| contains a prefix for signing.
      hs->server_params.size() < 2 * SSL3_RANDOM_SIZE ||
      !CBB_add_bytes(&body, hs->server_params.data() + 2 * SSL3_RANDOM_SIZE,
                     hs->server_params.size() - 2 * SSL3_RANDOM_SIZE)) {
    return ssl_hs_error;
  }

  // Add a signature.
  if (ssl_cipher_uses_certificate_auth(hs->new_cipher)) {
    // Determine the signature algorithm.
    uint16_t signature_algorithm;
    if (!tls1_choose_signature_algorithm(hs, hs->credential.get(),
                                         &signature_algorithm)) {
      ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_HANDSHAKE_FAILURE);
      return ssl_hs_error;
    }
    if (ssl_protocol_version(ssl) >= TLS1_2_VERSION) {
      if (!CBB_add_u16(&body, signature_algorithm)) {
        OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
        ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_INTERNAL_ERROR);
        return ssl_hs_error;
      }
    }

    // Add space for the signature.
    const size_t max_sig_len = EVP_PKEY_size(hs->credential->pubkey.get());
    uint8_t *ptr;
    if (!CBB_add_u16_length_prefixed(&body, &child) ||
        !CBB_reserve(&child, &ptr, max_sig_len)) {
      return ssl_hs_error;
    }

    size_t sig_len;
    switch (ssl_private_key_sign(hs, ptr, &sig_len, max_sig_len,
                                 signature_algorithm, hs->server_params)) {
      case ssl_private_key_success:
        if (!CBB_did_write(&child, sig_len)) {
          return ssl_hs_error;
        }
        break;
      case ssl_private_key_failure:
        return ssl_hs_error;
      case ssl_private_key_retry:
        return ssl_hs_private_key_operation;
    }
  }

  hs->can_release_private_key = true;
  if (!ssl_add_message_cbb(ssl, cbb.get())) {
    return ssl_hs_error;
  }

  hs->server_params.Reset();

  hs->state = state12_send_server_hello_done;
  return ssl_hs_ok;
}

static enum ssl_hs_wait_t do_send_server_hello_done(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;
  if (hs->hints_requested) {
    return ssl_hs_hints_ready;
  }

  ScopedCBB cbb;
  CBB body;

  if (hs->cert_request) {
    CBB cert_types, sigalgs_cbb;
    if (!ssl->method->init_message(ssl, cbb.get(), &body,
                                   SSL3_MT_CERTIFICATE_REQUEST) ||
        !CBB_add_u8_length_prefixed(&body, &cert_types) ||
        !CBB_add_u8(&cert_types, SSL3_CT_RSA_SIGN) ||
        !CBB_add_u8(&cert_types, TLS_CT_ECDSA_SIGN) ||
        (ssl_protocol_version(ssl) >= TLS1_2_VERSION &&
         (!CBB_add_u16_length_prefixed(&body, &sigalgs_cbb) ||
          !tls12_add_verify_sigalgs(hs, &sigalgs_cbb))) ||
        !ssl_add_client_CA_list(hs, &body) ||
        !ssl_add_message_cbb(ssl, cbb.get())) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
      return ssl_hs_error;
    }
  }

  if (!ssl->method->init_message(ssl, cbb.get(), &body,
                                 SSL3_MT_SERVER_HELLO_DONE) ||
      !ssl_add_message_cbb(ssl, cbb.get())) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
    return ssl_hs_error;
  }

  hs->state = state12_read_client_certificate;
  return ssl_hs_flush;
}

static enum ssl_hs_wait_t do_read_client_certificate(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;

  if (hs->handback && hs->new_cipher->algorithm_mkey == SSL_kECDHE) {
    return ssl_hs_handback;
  }
  if (!hs->cert_request) {
    hs->state = state12_verify_client_certificate;
    return ssl_hs_ok;
  }

  SSLMessage msg;
  if (!ssl->method->get_message(ssl, &msg)) {
    return ssl_hs_read_message;
  }

  if (!ssl_check_message_type(ssl, msg, SSL3_MT_CERTIFICATE)) {
    return ssl_hs_error;
  }

  if (!ssl_hash_message(hs, msg)) {
    return ssl_hs_error;
  }

  CBS certificate_msg = msg.body;
  uint8_t alert = SSL_AD_DECODE_ERROR;
  if (!ssl_parse_cert_chain(&alert, &hs->new_session->certs, &hs->peer_pubkey,
                            hs->config->retain_only_sha256_of_client_certs
                                ? hs->new_session->peer_sha256
                                : nullptr,
                            &certificate_msg, ssl->ctx->pool)) {
    ssl_send_alert(ssl, SSL3_AL_FATAL, alert);
    return ssl_hs_error;
  }

  if (CBS_len(&certificate_msg) != 0 ||
      !ssl->ctx->x509_method->session_cache_objects(hs->new_session.get())) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_DECODE_ERROR);
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_DECODE_ERROR);
    return ssl_hs_error;
  }

  if (sk_CRYPTO_BUFFER_num(hs->new_session->certs.get()) == 0) {
    // No client certificate so the handshake buffer may be discarded.
    hs->transcript.FreeBuffer();

    if (hs->config->verify_mode & SSL_VERIFY_FAIL_IF_NO_PEER_CERT) {
      // Fail for TLS only if we required a certificate
      OPENSSL_PUT_ERROR(SSL, SSL_R_PEER_DID_NOT_RETURN_A_CERTIFICATE);
      ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_HANDSHAKE_FAILURE);
      return ssl_hs_error;
    }

    // OpenSSL returns X509_V_OK when no certificates are received. This is
    // classed by them as a bug, but it's assumed by at least NGINX.
    hs->new_session->verify_result = X509_V_OK;
  } else if (hs->config->retain_only_sha256_of_client_certs) {
    // The hash will have been filled in.
    hs->new_session->peer_sha256_valid = true;
  }

  ssl->method->next_message(ssl);
  hs->state = state12_verify_client_certificate;
  return ssl_hs_ok;
}

static enum ssl_hs_wait_t do_verify_client_certificate(SSL_HANDSHAKE *hs) {
  if (sk_CRYPTO_BUFFER_num(hs->new_session->certs.get()) > 0) {
    switch (ssl_verify_peer_cert(hs)) {
      case ssl_verify_ok:
        break;
      case ssl_verify_invalid:
        return ssl_hs_error;
      case ssl_verify_retry:
        return ssl_hs_certificate_verify;
    }
  }

  hs->state = state12_read_client_key_exchange;
  return ssl_hs_ok;
}

static enum ssl_hs_wait_t do_read_client_key_exchange(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;
  SSLMessage msg;
  if (!ssl->method->get_message(ssl, &msg)) {
    return ssl_hs_read_message;
  }

  if (!ssl_check_message_type(ssl, msg, SSL3_MT_CLIENT_KEY_EXCHANGE)) {
    return ssl_hs_error;
  }

  CBS client_key_exchange = msg.body;
  uint32_t alg_k = hs->new_cipher->algorithm_mkey;
  uint32_t alg_a = hs->new_cipher->algorithm_auth;

  // If using a PSK key exchange, parse the PSK identity.
  if (alg_a & SSL_aPSK) {
    CBS psk_identity;

    // If using PSK, the ClientKeyExchange contains a psk_identity. If PSK,
    // then this is the only field in the message.
    if (!CBS_get_u16_length_prefixed(&client_key_exchange, &psk_identity) ||
        ((alg_k & SSL_kPSK) && CBS_len(&client_key_exchange) != 0)) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_DECODE_ERROR);
      ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_DECODE_ERROR);
      return ssl_hs_error;
    }

    if (CBS_len(&psk_identity) > PSK_MAX_IDENTITY_LEN ||
        CBS_contains_zero_byte(&psk_identity)) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_DATA_LENGTH_TOO_LONG);
      ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_ILLEGAL_PARAMETER);
      return ssl_hs_error;
    }
    char *raw = nullptr;
    if (!CBS_strdup(&psk_identity, &raw)) {
      ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_INTERNAL_ERROR);
      return ssl_hs_error;
    }
    hs->new_session->psk_identity.reset(raw);
  }

  // Depending on the key exchange method, compute |premaster_secret|.
  Array<uint8_t> premaster_secret;
  if (alg_k & SSL_kRSA) {
    CBS encrypted_premaster_secret;
    if (!CBS_get_u16_length_prefixed(&client_key_exchange,
                                     &encrypted_premaster_secret) ||
        CBS_len(&client_key_exchange) != 0) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_DECODE_ERROR);
      ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_DECODE_ERROR);
      return ssl_hs_error;
    }

    // Allocate a buffer large enough for an RSA decryption.
    Array<uint8_t> decrypt_buf;
    if (!decrypt_buf.InitForOverwrite(
            EVP_PKEY_size(hs->credential->pubkey.get()))) {
      return ssl_hs_error;
    }

    // Decrypt with no padding. PKCS#1 padding will be removed as part of the
    // timing-sensitive code below.
    size_t decrypt_len;
    switch (ssl_private_key_decrypt(hs, decrypt_buf.data(), &decrypt_len,
                                    decrypt_buf.size(),
                                    encrypted_premaster_secret)) {
      case ssl_private_key_success:
        break;
      case ssl_private_key_failure:
        return ssl_hs_error;
      case ssl_private_key_retry:
        return ssl_hs_private_key_operation;
    }

    if (decrypt_len != decrypt_buf.size()) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_DECRYPTION_FAILED);
      ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_DECRYPT_ERROR);
      return ssl_hs_error;
    }

    CONSTTIME_SECRET(decrypt_buf.data(), decrypt_len);

    // Prepare a random premaster, to be used on invalid padding. See RFC 5246,
    // section 7.4.7.1.
    if (!premaster_secret.InitForOverwrite(SSL_MAX_MASTER_KEY_LENGTH) ||
        !RAND_bytes(premaster_secret.data(), premaster_secret.size())) {
      return ssl_hs_error;
    }

    // The smallest padded premaster is 11 bytes of overhead. Small keys are
    // publicly invalid.
    if (decrypt_len < 11 + premaster_secret.size()) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_DECRYPTION_FAILED);
      ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_DECRYPT_ERROR);
      return ssl_hs_error;
    }

    // Check the padding. See RFC 3447, section 7.2.2.
    size_t padding_len = decrypt_len - premaster_secret.size();
    uint8_t good = constant_time_eq_int_8(decrypt_buf[0], 0) &
                   constant_time_eq_int_8(decrypt_buf[1], 2);
    for (size_t i = 2; i < padding_len - 1; i++) {
      good &= ~constant_time_is_zero_8(decrypt_buf[i]);
    }
    good &= constant_time_is_zero_8(decrypt_buf[padding_len - 1]);

    // The premaster secret must begin with |client_version|. This too must be
    // checked in constant time (http://eprint.iacr.org/2003/052/).
    good &= constant_time_eq_8(decrypt_buf[padding_len],
                               (unsigned)(hs->client_version >> 8));
    good &= constant_time_eq_8(decrypt_buf[padding_len + 1],
                               (unsigned)(hs->client_version & 0xff));

    // Select, in constant time, either the decrypted premaster or the random
    // premaster based on |good|.
    for (size_t i = 0; i < premaster_secret.size(); i++) {
      premaster_secret[i] = constant_time_select_8(
          good, decrypt_buf[padding_len + i], premaster_secret[i]);
    }
  } else if (alg_k & SSL_kECDHE) {
    // Parse the ClientKeyExchange.
    CBS ciphertext;
    if (!CBS_get_u8_length_prefixed(&client_key_exchange, &ciphertext) ||
        CBS_len(&client_key_exchange) != 0) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_DECODE_ERROR);
      ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_DECODE_ERROR);
      return ssl_hs_error;
    }

    // Decapsulate the premaster secret.
    uint8_t alert = SSL_AD_DECODE_ERROR;
    if (!hs->key_shares[0]->Decap(&premaster_secret, &alert, ciphertext)) {
      ssl_send_alert(ssl, SSL3_AL_FATAL, alert);
      return ssl_hs_error;
    }

    // The key exchange state may now be discarded.
    hs->key_shares[0].reset();
    hs->key_shares[1].reset();
  } else if (!(alg_k & SSL_kPSK)) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_HANDSHAKE_FAILURE);
    return ssl_hs_error;
  }

  // For a PSK cipher suite, the actual pre-master secret is combined with the
  // pre-shared key.
  if (alg_a & SSL_aPSK) {
    if (hs->config->psk_server_callback == NULL) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
      ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_INTERNAL_ERROR);
      return ssl_hs_error;
    }

    // Look up the key for the identity.
    uint8_t psk[PSK_MAX_PSK_LEN];
    unsigned psk_len = hs->config->psk_server_callback(
        ssl, hs->new_session->psk_identity.get(), psk, sizeof(psk));
    if (psk_len > PSK_MAX_PSK_LEN) {
      OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
      ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_INTERNAL_ERROR);
      return ssl_hs_error;
    } else if (psk_len == 0) {
      // PSK related to the given identity not found.
      OPENSSL_PUT_ERROR(SSL, SSL_R_PSK_IDENTITY_NOT_FOUND);
      ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_UNKNOWN_PSK_IDENTITY);
      return ssl_hs_error;
    }

    if (alg_k & SSL_kPSK) {
      // In plain PSK, other_secret is a block of 0s with the same length as the
      // pre-shared key.
      if (!premaster_secret.Init(psk_len)) {
        return ssl_hs_error;
      }
    }

    ScopedCBB new_premaster;
    CBB child;
    if (!CBB_init(new_premaster.get(),
                  2 + psk_len + 2 + premaster_secret.size()) ||
        !CBB_add_u16_length_prefixed(new_premaster.get(), &child) ||
        !CBB_add_bytes(&child, premaster_secret.data(),
                       premaster_secret.size()) ||
        !CBB_add_u16_length_prefixed(new_premaster.get(), &child) ||
        !CBB_add_bytes(&child, psk, psk_len) ||
        !CBBFinishArray(new_premaster.get(), &premaster_secret)) {
      return ssl_hs_error;
    }
  }

  if (!ssl_hash_message(hs, msg)) {
    return ssl_hs_error;
  }

  // Compute the master secret.
  hs->new_session->secret.ResizeForOverwrite(SSL3_MASTER_SECRET_SIZE);
  if (!tls1_generate_master_secret(hs, Span(hs->new_session->secret),
                                   premaster_secret)) {
    return ssl_hs_error;
  }
  hs->new_session->extended_master_secret = hs->extended_master_secret;
  // Declassify the secret to undo the RSA decryption validation above. We are
  // not currently running most of the TLS library with constant-time
  // validation.
  // TODO(crbug.com/42290551): Remove this and cover the TLS library too.
  CONSTTIME_DECLASSIFY(hs->new_session->secret.data(),
                       hs->new_session->secret.size());
  hs->can_release_private_key = true;

  ssl->method->next_message(ssl);
  hs->state = state12_read_client_certificate_verify;
  return ssl_hs_ok;
}

static enum ssl_hs_wait_t do_read_client_certificate_verify(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;

  // Only RSA and ECDSA client certificates are supported, so a
  // CertificateVerify is required if and only if there's a client certificate.
  if (!hs->peer_pubkey) {
    hs->transcript.FreeBuffer();
    hs->state = state12_read_change_cipher_spec;
    return ssl_hs_ok;
  }

  SSLMessage msg;
  if (!ssl->method->get_message(ssl, &msg)) {
    return ssl_hs_read_message;
  }

  if (!ssl_check_message_type(ssl, msg, SSL3_MT_CERTIFICATE_VERIFY)) {
    return ssl_hs_error;
  }

  // The peer certificate must be valid for signing.
  const CRYPTO_BUFFER *leaf =
      sk_CRYPTO_BUFFER_value(hs->new_session->certs.get(), 0);
  CBS leaf_cbs;
  CRYPTO_BUFFER_init_CBS(leaf, &leaf_cbs);
  if (!ssl_cert_check_key_usage(&leaf_cbs, key_usage_digital_signature)) {
    return ssl_hs_error;
  }

  CBS certificate_verify = msg.body, signature;

  // Determine the signature algorithm.
  uint16_t signature_algorithm = 0;
  if (ssl_protocol_version(ssl) >= TLS1_2_VERSION) {
    if (!CBS_get_u16(&certificate_verify, &signature_algorithm)) {
      OPENSSL_PUT_ERROR(SSL, SSL_R_DECODE_ERROR);
      ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_DECODE_ERROR);
      return ssl_hs_error;
    }
    uint8_t alert = SSL_AD_DECODE_ERROR;
    if (!tls12_check_peer_sigalg(hs, &alert, signature_algorithm,
                                 hs->peer_pubkey.get())) {
      ssl_send_alert(ssl, SSL3_AL_FATAL, alert);
      return ssl_hs_error;
    }
    hs->new_session->peer_signature_algorithm = signature_algorithm;
  } else if (!tls1_get_legacy_signature_algorithm(&signature_algorithm,
                                                  hs->peer_pubkey.get())) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_PEER_ERROR_UNSUPPORTED_CERTIFICATE_TYPE);
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_UNSUPPORTED_CERTIFICATE);
    return ssl_hs_error;
  }

  // Parse and verify the signature.
  if (!CBS_get_u16_length_prefixed(&certificate_verify, &signature) ||
      CBS_len(&certificate_verify) != 0) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_DECODE_ERROR);
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_DECODE_ERROR);
    return ssl_hs_error;
  }

  if (!ssl_public_key_verify(ssl, signature, signature_algorithm,
                             hs->peer_pubkey.get(), hs->transcript.buffer())) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_BAD_SIGNATURE);
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_DECRYPT_ERROR);
    return ssl_hs_error;
  }

  // The handshake buffer is no longer necessary, and we may hash the current
  // message.
  hs->transcript.FreeBuffer();
  if (!ssl_hash_message(hs, msg)) {
    return ssl_hs_error;
  }

  ssl->method->next_message(ssl);
  hs->state = state12_read_change_cipher_spec;
  return ssl_hs_ok;
}

static enum ssl_hs_wait_t do_read_change_cipher_spec(SSL_HANDSHAKE *hs) {
  if (hs->handback && hs->ssl->session != NULL) {
    return ssl_hs_handback;
  }
  hs->state = state12_process_change_cipher_spec;
  return ssl_hs_read_change_cipher_spec;
}

static enum ssl_hs_wait_t do_process_change_cipher_spec(SSL_HANDSHAKE *hs) {
  if (!tls1_change_cipher_state(hs, evp_aead_open)) {
    return ssl_hs_error;
  }

  hs->state = state12_read_next_proto;
  return ssl_hs_ok;
}

static enum ssl_hs_wait_t do_read_next_proto(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;

  if (!hs->next_proto_neg_seen) {
    hs->state = state12_read_channel_id;
    return ssl_hs_ok;
  }

  SSLMessage msg;
  if (!ssl->method->get_message(ssl, &msg)) {
    return ssl_hs_read_message;
  }

  if (!ssl_check_message_type(ssl, msg, SSL3_MT_NEXT_PROTO) ||
      !ssl_hash_message(hs, msg)) {
    return ssl_hs_error;
  }

  CBS next_protocol = msg.body, selected_protocol, padding;
  if (!CBS_get_u8_length_prefixed(&next_protocol, &selected_protocol) ||
      !CBS_get_u8_length_prefixed(&next_protocol, &padding) ||
      CBS_len(&next_protocol) != 0) {
    OPENSSL_PUT_ERROR(SSL, SSL_R_DECODE_ERROR);
    ssl_send_alert(ssl, SSL3_AL_FATAL, SSL_AD_DECODE_ERROR);
    return ssl_hs_error;
  }

  if (!ssl->s3->next_proto_negotiated.CopyFrom(selected_protocol)) {
    return ssl_hs_error;
  }

  ssl->method->next_message(ssl);
  hs->state = state12_read_channel_id;
  return ssl_hs_ok;
}

static enum ssl_hs_wait_t do_read_channel_id(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;

  if (!hs->channel_id_negotiated) {
    hs->state = state12_read_client_finished;
    return ssl_hs_ok;
  }

  SSLMessage msg;
  if (!ssl->method->get_message(ssl, &msg)) {
    return ssl_hs_read_message;
  }

  if (!ssl_check_message_type(ssl, msg, SSL3_MT_CHANNEL_ID) ||
      !tls1_verify_channel_id(hs, msg) ||  //
      !ssl_hash_message(hs, msg)) {
    return ssl_hs_error;
  }

  ssl->method->next_message(ssl);
  hs->state = state12_read_client_finished;
  return ssl_hs_ok;
}

static enum ssl_hs_wait_t do_read_client_finished(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;
  enum ssl_hs_wait_t wait = ssl_get_finished(hs);
  if (wait != ssl_hs_ok) {
    return wait;
  }

  if (ssl->session != NULL) {
    hs->state = state12_finish_server_handshake;
  } else {
    hs->state = state12_send_server_finished;
  }

  // If this is a full handshake with ChannelID then record the handshake
  // hashes in |hs->new_session| in case we need them to verify a
  // ChannelID signature on a resumption of this session in the future.
  if (ssl->session == NULL && ssl->s3->channel_id_valid &&
      !tls1_record_handshake_hashes_for_channel_id(hs)) {
    return ssl_hs_error;
  }

  return ssl_hs_ok;
}

static enum ssl_hs_wait_t do_send_server_finished(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;

  if (hs->ticket_expected) {
    const SSL_SESSION *session;
    UniquePtr<SSL_SESSION> session_copy;
    if (ssl->session == NULL) {
      // Fix the timeout to measure from the ticket issuance time.
      ssl_session_rebase_time(ssl, hs->new_session.get());
      session = hs->new_session.get();
    } else {
      // We are renewing an existing session. Duplicate the session to adjust
      // the timeout.
      session_copy =
          SSL_SESSION_dup(ssl->session.get(), SSL_SESSION_INCLUDE_NONAUTH);
      if (!session_copy) {
        return ssl_hs_error;
      }

      ssl_session_rebase_time(ssl, session_copy.get());
      session = session_copy.get();
    }

    ScopedCBB cbb;
    CBB body, ticket;
    if (!ssl->method->init_message(ssl, cbb.get(), &body,
                                   SSL3_MT_NEW_SESSION_TICKET) ||
        !CBB_add_u32(&body, session->timeout) ||
        !CBB_add_u16_length_prefixed(&body, &ticket) ||
        !ssl_encrypt_ticket(hs, &ticket, session) ||
        // |ticket| may be empty to skip sending a ticket. In TLS 1.2, servers
        // skip sending tickets by sending empty NewSessionTicket, so no special
        // handling is needed.
        !ssl_add_message_cbb(ssl, cbb.get())) {
      return ssl_hs_error;
    }
  }

  if (!ssl->method->add_change_cipher_spec(ssl) ||     //
      !tls1_change_cipher_state(hs, evp_aead_seal) ||  //
      !ssl_send_finished(hs)) {
    return ssl_hs_error;
  }

  if (ssl->session != NULL) {
    hs->state = state12_read_change_cipher_spec;
  } else {
    hs->state = state12_finish_server_handshake;
  }
  return ssl_hs_flush;
}

static enum ssl_hs_wait_t do_finish_server_handshake(SSL_HANDSHAKE *hs) {
  SSL *const ssl = hs->ssl;

  if (hs->handback) {
    return ssl_hs_handback;
  }

  ssl->method->on_handshake_complete(ssl);

  // If we aren't retaining peer certificates then we can discard it now.
  if (hs->new_session != NULL &&
      hs->config->retain_only_sha256_of_client_certs) {
    hs->new_session->certs.reset();
    ssl->ctx->x509_method->session_clear(hs->new_session.get());
  }

  bool has_new_session = hs->new_session != nullptr;
  if (has_new_session) {
    assert(ssl->session == nullptr);
    ssl->s3->established_session = std::move(hs->new_session);
    ssl->s3->established_session->not_resumable = false;
  } else {
    assert(ssl->session != nullptr);
    ssl->s3->established_session = UpRef(ssl->session);
  }

  hs->handshake_finalized = true;
  ssl->s3->initial_handshake_complete = true;
  if (has_new_session) {
    ssl_update_cache(ssl);
  }

  hs->state = state12_done;
  return ssl_hs_ok;
}

enum ssl_hs_wait_t ssl_server_handshake(SSL_HANDSHAKE *hs) {
  while (hs->state != state12_done) {
    enum ssl_hs_wait_t ret = ssl_hs_error;
    enum tls12_server_hs_state_t state =
        static_cast<enum tls12_server_hs_state_t>(hs->state);
    switch (state) {
      case state12_start_accept:
        ret = do_start_accept(hs);
        break;
      case state12_read_client_hello:
        ret = do_read_client_hello(hs);
        break;
      case state12_read_client_hello_after_ech:
        ret = do_read_client_hello_after_ech(hs);
        break;
      case state12_cert_callback:
        ret = do_cert_callback(hs);
        break;
      case state12_tls13:
        ret = do_tls13(hs);
        break;
      case state12_select_parameters:
        ret = do_select_parameters(hs);
        break;
      case state12_send_server_hello:
        ret = do_send_server_hello(hs);
        break;
      case state12_send_server_certificate:
        ret = do_send_server_certificate(hs);
        break;
      case state12_send_server_key_exchange:
        ret = do_send_server_key_exchange(hs);
        break;
      case state12_send_server_hello_done:
        ret = do_send_server_hello_done(hs);
        break;
      case state12_read_client_certificate:
        ret = do_read_client_certificate(hs);
        break;
      case state12_verify_client_certificate:
        ret = do_verify_client_certificate(hs);
        break;
      case state12_read_client_key_exchange:
        ret = do_read_client_key_exchange(hs);
        break;
      case state12_read_client_certificate_verify:
        ret = do_read_client_certificate_verify(hs);
        break;
      case state12_read_change_cipher_spec:
        ret = do_read_change_cipher_spec(hs);
        break;
      case state12_process_change_cipher_spec:
        ret = do_process_change_cipher_spec(hs);
        break;
      case state12_read_next_proto:
        ret = do_read_next_proto(hs);
        break;
      case state12_read_channel_id:
        ret = do_read_channel_id(hs);
        break;
      case state12_read_client_finished:
        ret = do_read_client_finished(hs);
        break;
      case state12_send_server_finished:
        ret = do_send_server_finished(hs);
        break;
      case state12_finish_server_handshake:
        ret = do_finish_server_handshake(hs);
        break;
      case state12_done:
        ret = ssl_hs_ok;
        break;
    }

    if (hs->state != state) {
      ssl_do_info_callback(hs->ssl, SSL_CB_ACCEPT_LOOP, 1);
    }

    if (ret != ssl_hs_ok) {
      return ret;
    }
  }

  ssl_do_info_callback(hs->ssl, SSL_CB_HANDSHAKE_DONE, 1);
  return ssl_hs_ok;
}

const char *ssl_server_handshake_state(SSL_HANDSHAKE *hs) {
  enum tls12_server_hs_state_t state =
      static_cast<enum tls12_server_hs_state_t>(hs->state);
  switch (state) {
    case state12_start_accept:
      return "TLS server start_accept";
    case state12_read_client_hello:
      return "TLS server read_client_hello";
    case state12_read_client_hello_after_ech:
      return "TLS server read_client_hello_after_ech";
    case state12_cert_callback:
      return "TLS server cert_callback";
    case state12_tls13:
      return tls13_server_handshake_state(hs);
    case state12_select_parameters:
      return "TLS server select_parameters";
    case state12_send_server_hello:
      return "TLS server send_server_hello";
    case state12_send_server_certificate:
      return "TLS server send_server_certificate";
    case state12_send_server_key_exchange:
      return "TLS server send_server_key_exchange";
    case state12_send_server_hello_done:
      return "TLS server send_server_hello_done";
    case state12_read_client_certificate:
      return "TLS server read_client_certificate";
    case state12_verify_client_certificate:
      return "TLS server verify_client_certificate";
    case state12_read_client_key_exchange:
      return "TLS server read_client_key_exchange";
    case state12_read_client_certificate_verify:
      return "TLS server read_client_certificate_verify";
    case state12_read_change_cipher_spec:
      return "TLS server read_change_cipher_spec";
    case state12_process_change_cipher_spec:
      return "TLS server process_change_cipher_spec";
    case state12_read_next_proto:
      return "TLS server read_next_proto";
    case state12_read_channel_id:
      return "TLS server read_channel_id";
    case state12_read_client_finished:
      return "TLS server read_client_finished";
    case state12_send_server_finished:
      return "TLS server send_server_finished";
    case state12_finish_server_handshake:
      return "TLS server finish_server_handshake";
    case state12_done:
      return "TLS server done";
  }

  return "TLS server unknown";
}

BSSL_NAMESPACE_END

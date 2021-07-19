/* Copyright (c) 2017, Google Inc.
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

#include <openssl/evp.h>

#include <openssl/bytestring.h>
#include <openssl/curve25519.h>
#include <openssl/err.h>
#include <openssl/mem.h>

#include "internal.h"
#include "../internal.h"


static void ed25519_free(EVP_PKEY *pkey) {
  OPENSSL_free(pkey->pkey.ptr);
  pkey->pkey.ptr = NULL;
}

static int ed25519_set_priv_raw(EVP_PKEY *pkey, const uint8_t *in, size_t len) {
  if (len != 32) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
    return 0;
  }

  ED25519_KEY *key = OPENSSL_malloc(sizeof(ED25519_KEY));
  if (key == NULL) {
    OPENSSL_PUT_ERROR(EVP, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  // The RFC 8032 encoding stores only the 32-byte seed, so we must recover the
  // full representation which we use from it.
  uint8_t pubkey_unused[32];
  ED25519_keypair_from_seed(pubkey_unused, key->key.priv, in);
  key->has_private = 1;

  ed25519_free(pkey);
  pkey->pkey.ptr = key;
  return 1;
}

static int ed25519_set_pub_raw(EVP_PKEY *pkey, const uint8_t *in, size_t len) {
  if (len != 32) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
    return 0;
  }

  ED25519_KEY *key = OPENSSL_malloc(sizeof(ED25519_KEY));
  if (key == NULL) {
    OPENSSL_PUT_ERROR(EVP, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  OPENSSL_memcpy(key->key.pub.value, in, 32);
  key->has_private = 0;

  ed25519_free(pkey);
  pkey->pkey.ptr = key;
  return 1;
}

static int ed25519_get_priv_raw(const EVP_PKEY *pkey, uint8_t *out,
                                size_t *out_len) {
  const ED25519_KEY *key = pkey->pkey.ptr;
  if (!key->has_private) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_NOT_A_PRIVATE_KEY);
    return 0;
  }

  if (out == NULL) {
    *out_len = 32;
    return 1;
  }

  if (*out_len < 32) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_BUFFER_TOO_SMALL);
    return 0;
  }

  // The raw private key format is the first 32 bytes of the private key.
  OPENSSL_memcpy(out, key->key.priv, 32);
  *out_len = 32;
  return 1;
}

static int ed25519_get_pub_raw(const EVP_PKEY *pkey, uint8_t *out,
                               size_t *out_len) {
  const ED25519_KEY *key = pkey->pkey.ptr;
  if (out == NULL) {
    *out_len = 32;
    return 1;
  }

  if (*out_len < 32) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_BUFFER_TOO_SMALL);
    return 0;
  }

  OPENSSL_memcpy(out, key->key.pub.value, 32);
  *out_len = 32;
  return 1;
}

static int ed25519_pub_decode(EVP_PKEY *out, CBS *params, CBS *key) {
  // See RFC 8410, section 4.

  // The parameters must be omitted. Public keys have length 32.
  if (CBS_len(params) != 0) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
    return 0;
  }

  return ed25519_set_pub_raw(out, CBS_data(key), CBS_len(key));
}

static int ed25519_pub_encode(CBB *out, const EVP_PKEY *pkey) {
  const ED25519_KEY *key = pkey->pkey.ptr;

  // See RFC 8410, section 4.
  CBB spki, algorithm, oid, key_bitstring;
  if (!CBB_add_asn1(out, &spki, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&spki, &algorithm, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&algorithm, &oid, CBS_ASN1_OBJECT) ||
      !CBB_add_bytes(&oid, ed25519_asn1_meth.oid, ed25519_asn1_meth.oid_len) ||
      !CBB_add_asn1(&spki, &key_bitstring, CBS_ASN1_BITSTRING) ||
      !CBB_add_u8(&key_bitstring, 0 /* padding */) ||
      !CBB_add_bytes(&key_bitstring, key->key.pub.value, 32) ||
      !CBB_flush(out)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_ENCODE_ERROR);
    return 0;
  }

  return 1;
}

static int ed25519_pub_cmp(const EVP_PKEY *a, const EVP_PKEY *b) {
  const ED25519_KEY *a_key = a->pkey.ptr;
  const ED25519_KEY *b_key = b->pkey.ptr;
  return OPENSSL_memcmp(a_key->key.pub.value, b_key->key.pub.value, 32) == 0;
}

static int ed25519_priv_decode(EVP_PKEY *out, CBS *params, CBS *key) {
  // See RFC 8410, section 7.

  // Parameters must be empty. The key is a 32-byte value wrapped in an extra
  // OCTET STRING layer.
  CBS inner;
  if (CBS_len(params) != 0 ||
      !CBS_get_asn1(key, &inner, CBS_ASN1_OCTETSTRING) ||
      CBS_len(key) != 0) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
    return 0;
  }

  return ed25519_set_priv_raw(out, CBS_data(&inner), CBS_len(&inner));
}

static int ed25519_priv_encode(CBB *out, const EVP_PKEY *pkey) {
  ED25519_KEY *key = pkey->pkey.ptr;
  if (!key->has_private) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_NOT_A_PRIVATE_KEY);
    return 0;
  }

  // See RFC 8410, section 7.
  CBB pkcs8, algorithm, oid, private_key, inner;
  if (!CBB_add_asn1(out, &pkcs8, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1_uint64(&pkcs8, 0 /* version */) ||
      !CBB_add_asn1(&pkcs8, &algorithm, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1(&algorithm, &oid, CBS_ASN1_OBJECT) ||
      !CBB_add_bytes(&oid, ed25519_asn1_meth.oid, ed25519_asn1_meth.oid_len) ||
      !CBB_add_asn1(&pkcs8, &private_key, CBS_ASN1_OCTETSTRING) ||
      !CBB_add_asn1(&private_key, &inner, CBS_ASN1_OCTETSTRING) ||
      // The PKCS#8 encoding stores only the 32-byte seed which is the first 32
      // bytes of the private key.
      !CBB_add_bytes(&inner, key->key.priv, 32) ||
      !CBB_flush(out)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_ENCODE_ERROR);
    return 0;
  }

  return 1;
}

static int ed25519_size(const EVP_PKEY *pkey) { return 64; }

static int ed25519_bits(const EVP_PKEY *pkey) { return 253; }

const EVP_PKEY_ASN1_METHOD ed25519_asn1_meth = {
    EVP_PKEY_ED25519,
    {0x2b, 0x65, 0x70},
    3,
    ed25519_pub_decode,
    ed25519_pub_encode,
    ed25519_pub_cmp,
    ed25519_priv_decode,
    ed25519_priv_encode,
    ed25519_set_priv_raw,
    ed25519_set_pub_raw,
    ed25519_get_priv_raw,
    ed25519_get_pub_raw,
    NULL /* pkey_opaque */,
    ed25519_size,
    ed25519_bits,
    NULL /* param_missing */,
    NULL /* param_copy */,
    NULL /* param_cmp */,
    ed25519_free,
};

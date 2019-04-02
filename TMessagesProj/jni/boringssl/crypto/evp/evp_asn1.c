/* Copyright (C) 1995-1998 Eric Young (eay@cryptsoft.com)
 * All rights reserved.
 *
 * This package is an SSL implementation written
 * by Eric Young (eay@cryptsoft.com).
 * The implementation was written so as to conform with Netscapes SSL.
 *
 * This library is free for commercial and non-commercial use as long as
 * the following conditions are aheared to.  The following conditions
 * apply to all code found in this distribution, be it the RC4, RSA,
 * lhash, DES, etc., code; not just the SSL code.  The SSL documentation
 * included with this distribution is covered by the same copyright terms
 * except that the holder is Tim Hudson (tjh@cryptsoft.com).
 *
 * Copyright remains Eric Young's, and as such any Copyright notices in
 * the code are not to be removed.
 * If this package is used in a product, Eric Young should be given attribution
 * as the author of the parts of the library used.
 * This can be in the form of a textual message at program startup or
 * in documentation (online or textual) provided with the package.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *    "This product includes cryptographic software written by
 *     Eric Young (eay@cryptsoft.com)"
 *    The word 'cryptographic' can be left out if the rouines from the library
 *    being used are not cryptographic related :-).
 * 4. If you include any Windows specific code (or a derivative thereof) from
 *    the apps directory (application code) you must include an acknowledgement:
 *    "This product includes software written by Tim Hudson (tjh@cryptsoft.com)"
 *
 * THIS SOFTWARE IS PROVIDED BY ERIC YOUNG ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * The licence and distribution terms for any publically available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution licence
 * [including the GNU Public Licence.] */

#include <openssl/evp.h>

#include <string.h>

#include <openssl/bytestring.h>
#include <openssl/dsa.h>
#include <openssl/ec_key.h>
#include <openssl/err.h>
#include <openssl/rsa.h>

#include "internal.h"
#include "../internal.h"


static const EVP_PKEY_ASN1_METHOD *const kASN1Methods[] = {
    &rsa_asn1_meth,
    &ec_asn1_meth,
    &dsa_asn1_meth,
    &ed25519_asn1_meth,
};

static int parse_key_type(CBS *cbs, int *out_type) {
  CBS oid;
  if (!CBS_get_asn1(cbs, &oid, CBS_ASN1_OBJECT)) {
    return 0;
  }

  for (unsigned i = 0; i < OPENSSL_ARRAY_SIZE(kASN1Methods); i++) {
    const EVP_PKEY_ASN1_METHOD *method = kASN1Methods[i];
    if (CBS_len(&oid) == method->oid_len &&
        OPENSSL_memcmp(CBS_data(&oid), method->oid, method->oid_len) == 0) {
      *out_type = method->pkey_id;
      return 1;
    }
  }

  return 0;
}

EVP_PKEY *EVP_parse_public_key(CBS *cbs) {
  // Parse the SubjectPublicKeyInfo.
  CBS spki, algorithm, key;
  int type;
  uint8_t padding;
  if (!CBS_get_asn1(cbs, &spki, CBS_ASN1_SEQUENCE) ||
      !CBS_get_asn1(&spki, &algorithm, CBS_ASN1_SEQUENCE) ||
      !parse_key_type(&algorithm, &type) ||
      !CBS_get_asn1(&spki, &key, CBS_ASN1_BITSTRING) ||
      CBS_len(&spki) != 0 ||
      // Every key type defined encodes the key as a byte string with the same
      // conversion to BIT STRING.
      !CBS_get_u8(&key, &padding) ||
      padding != 0) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
    return NULL;
  }

  // Set up an |EVP_PKEY| of the appropriate type.
  EVP_PKEY *ret = EVP_PKEY_new();
  if (ret == NULL ||
      !EVP_PKEY_set_type(ret, type)) {
    goto err;
  }

  // Call into the type-specific SPKI decoding function.
  if (ret->ameth->pub_decode == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_UNSUPPORTED_ALGORITHM);
    goto err;
  }
  if (!ret->ameth->pub_decode(ret, &algorithm, &key)) {
    goto err;
  }

  return ret;

err:
  EVP_PKEY_free(ret);
  return NULL;
}

int EVP_marshal_public_key(CBB *cbb, const EVP_PKEY *key) {
  if (key->ameth == NULL || key->ameth->pub_encode == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_UNSUPPORTED_ALGORITHM);
    return 0;
  }

  return key->ameth->pub_encode(cbb, key);
}

EVP_PKEY *EVP_parse_private_key(CBS *cbs) {
  // Parse the PrivateKeyInfo.
  CBS pkcs8, algorithm, key;
  uint64_t version;
  int type;
  if (!CBS_get_asn1(cbs, &pkcs8, CBS_ASN1_SEQUENCE) ||
      !CBS_get_asn1_uint64(&pkcs8, &version) ||
      version != 0 ||
      !CBS_get_asn1(&pkcs8, &algorithm, CBS_ASN1_SEQUENCE) ||
      !parse_key_type(&algorithm, &type) ||
      !CBS_get_asn1(&pkcs8, &key, CBS_ASN1_OCTETSTRING)) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
    return NULL;
  }

  // A PrivateKeyInfo ends with a SET of Attributes which we ignore.

  // Set up an |EVP_PKEY| of the appropriate type.
  EVP_PKEY *ret = EVP_PKEY_new();
  if (ret == NULL ||
      !EVP_PKEY_set_type(ret, type)) {
    goto err;
  }

  // Call into the type-specific PrivateKeyInfo decoding function.
  if (ret->ameth->priv_decode == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_UNSUPPORTED_ALGORITHM);
    goto err;
  }
  if (!ret->ameth->priv_decode(ret, &algorithm, &key)) {
    goto err;
  }

  return ret;

err:
  EVP_PKEY_free(ret);
  return NULL;
}

int EVP_marshal_private_key(CBB *cbb, const EVP_PKEY *key) {
  if (key->ameth == NULL || key->ameth->priv_encode == NULL) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_UNSUPPORTED_ALGORITHM);
    return 0;
  }

  return key->ameth->priv_encode(cbb, key);
}

static EVP_PKEY *old_priv_decode(CBS *cbs, int type) {
  EVP_PKEY *ret = EVP_PKEY_new();
  if (ret == NULL) {
    return NULL;
  }

  switch (type) {
    case EVP_PKEY_EC: {
      EC_KEY *ec_key = EC_KEY_parse_private_key(cbs, NULL);
      if (ec_key == NULL || !EVP_PKEY_assign_EC_KEY(ret, ec_key)) {
        EC_KEY_free(ec_key);
        goto err;
      }
      return ret;
    }
    case EVP_PKEY_DSA: {
      DSA *dsa = DSA_parse_private_key(cbs);
      if (dsa == NULL || !EVP_PKEY_assign_DSA(ret, dsa)) {
        DSA_free(dsa);
        goto err;
      }
      return ret;
    }
    case EVP_PKEY_RSA: {
      RSA *rsa = RSA_parse_private_key(cbs);
      if (rsa == NULL || !EVP_PKEY_assign_RSA(ret, rsa)) {
        RSA_free(rsa);
        goto err;
      }
      return ret;
    }
    default:
      OPENSSL_PUT_ERROR(EVP, EVP_R_UNKNOWN_PUBLIC_KEY_TYPE);
      goto err;
  }

err:
  EVP_PKEY_free(ret);
  return NULL;
}

EVP_PKEY *d2i_PrivateKey(int type, EVP_PKEY **out, const uint8_t **inp,
                         long len) {
  if (len < 0) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
    return NULL;
  }

  // Parse with the legacy format.
  CBS cbs;
  CBS_init(&cbs, *inp, (size_t)len);
  EVP_PKEY *ret = old_priv_decode(&cbs, type);
  if (ret == NULL) {
    // Try again with PKCS#8.
    ERR_clear_error();
    CBS_init(&cbs, *inp, (size_t)len);
    ret = EVP_parse_private_key(&cbs);
    if (ret == NULL) {
      return NULL;
    }
    if (ret->type != type) {
      OPENSSL_PUT_ERROR(EVP, EVP_R_DIFFERENT_KEY_TYPES);
      EVP_PKEY_free(ret);
      return NULL;
    }
  }

  if (out != NULL) {
    EVP_PKEY_free(*out);
    *out = ret;
  }
  *inp = CBS_data(&cbs);
  return ret;
}

// num_elements parses one SEQUENCE from |in| and returns the number of elements
// in it. On parse error, it returns zero.
static size_t num_elements(const uint8_t *in, size_t in_len) {
  CBS cbs, sequence;
  CBS_init(&cbs, in, (size_t)in_len);

  if (!CBS_get_asn1(&cbs, &sequence, CBS_ASN1_SEQUENCE)) {
    return 0;
  }

  size_t count = 0;
  while (CBS_len(&sequence) > 0) {
    if (!CBS_get_any_asn1_element(&sequence, NULL, NULL, NULL)) {
      return 0;
    }

    count++;
  }

  return count;
}

EVP_PKEY *d2i_AutoPrivateKey(EVP_PKEY **out, const uint8_t **inp, long len) {
  if (len < 0) {
    OPENSSL_PUT_ERROR(EVP, EVP_R_DECODE_ERROR);
    return NULL;
  }

  // Parse the input as a PKCS#8 PrivateKeyInfo.
  CBS cbs;
  CBS_init(&cbs, *inp, (size_t)len);
  EVP_PKEY *ret = EVP_parse_private_key(&cbs);
  if (ret != NULL) {
    if (out != NULL) {
      EVP_PKEY_free(*out);
      *out = ret;
    }
    *inp = CBS_data(&cbs);
    return ret;
  }
  ERR_clear_error();

  // Count the elements to determine the legacy key format.
  switch (num_elements(*inp, (size_t)len)) {
    case 4:
      return d2i_PrivateKey(EVP_PKEY_EC, out, inp, len);

    case 6:
      return d2i_PrivateKey(EVP_PKEY_DSA, out, inp, len);

    default:
      return d2i_PrivateKey(EVP_PKEY_RSA, out, inp, len);
  }
}

int i2d_PublicKey(EVP_PKEY *key, uint8_t **outp) {
  switch (key->type) {
    case EVP_PKEY_RSA:
      return i2d_RSAPublicKey(key->pkey.rsa, outp);
    case EVP_PKEY_DSA:
      return i2d_DSAPublicKey(key->pkey.dsa, outp);
    case EVP_PKEY_EC:
      return i2o_ECPublicKey(key->pkey.ec, outp);
    default:
      OPENSSL_PUT_ERROR(EVP, EVP_R_UNSUPPORTED_PUBLIC_KEY_TYPE);
      return -1;
  }
}

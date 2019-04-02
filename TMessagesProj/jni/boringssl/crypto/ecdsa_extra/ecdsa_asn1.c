/* ====================================================================
 * Copyright (c) 1998-2005 The OpenSSL Project.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All advertising materials mentioning features or use of this
 *    software must display the following acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit. (http://www.OpenSSL.org/)"
 *
 * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    openssl-core@OpenSSL.org.
 *
 * 5. Products derived from this software may not be called "OpenSSL"
 *    nor may "OpenSSL" appear in their names without prior written
 *    permission of the OpenSSL Project.
 *
 * 6. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit (http://www.OpenSSL.org/)"
 *
 * THIS SOFTWARE IS PROVIDED BY THE OpenSSL PROJECT ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * This product includes cryptographic software written by Eric Young
 * (eay@cryptsoft.com).  This product includes software written by Tim
 * Hudson (tjh@cryptsoft.com). */

#include <openssl/ecdsa.h>

#include <limits.h>
#include <string.h>

#include <openssl/bn.h>
#include <openssl/bytestring.h>
#include <openssl/err.h>
#include <openssl/ec_key.h>
#include <openssl/mem.h>

#include "../bytestring/internal.h"
#include "../fipsmodule/ec/internal.h"
#include "../internal.h"


int ECDSA_sign(int type, const uint8_t *digest, size_t digest_len, uint8_t *sig,
               unsigned int *sig_len, const EC_KEY *eckey) {
  if (eckey->ecdsa_meth && eckey->ecdsa_meth->sign) {
    return eckey->ecdsa_meth->sign(digest, digest_len, sig, sig_len,
                                   (EC_KEY*) eckey /* cast away const */);
  }

  int ret = 0;
  ECDSA_SIG *s = NULL;

  if (eckey->ecdsa_meth && eckey->ecdsa_meth->sign) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_NOT_IMPLEMENTED);
    *sig_len = 0;
    goto err;
  }

  s = ECDSA_do_sign(digest, digest_len, eckey);
  if (s == NULL) {
    *sig_len = 0;
    goto err;
  }

  CBB cbb;
  CBB_zero(&cbb);
  size_t len;
  if (!CBB_init_fixed(&cbb, sig, ECDSA_size(eckey)) ||
      !ECDSA_SIG_marshal(&cbb, s) ||
      !CBB_finish(&cbb, NULL, &len)) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_ENCODE_ERROR);
    CBB_cleanup(&cbb);
    *sig_len = 0;
    goto err;
  }
  *sig_len = (unsigned)len;
  ret = 1;

err:
  ECDSA_SIG_free(s);
  return ret;
}

int ECDSA_verify(int type, const uint8_t *digest, size_t digest_len,
                 const uint8_t *sig, size_t sig_len, const EC_KEY *eckey) {
  ECDSA_SIG *s;
  int ret = 0;
  uint8_t *der = NULL;

  // Decode the ECDSA signature.
  s = ECDSA_SIG_from_bytes(sig, sig_len);
  if (s == NULL) {
    goto err;
  }

  // Defend against potential laxness in the DER parser.
  size_t der_len;
  if (!ECDSA_SIG_to_bytes(&der, &der_len, s) ||
      der_len != sig_len || OPENSSL_memcmp(sig, der, sig_len) != 0) {
    // This should never happen. crypto/bytestring is strictly DER.
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_INTERNAL_ERROR);
    goto err;
  }

  ret = ECDSA_do_verify(digest, digest_len, s, eckey);

err:
  OPENSSL_free(der);
  ECDSA_SIG_free(s);
  return ret;
}


size_t ECDSA_size(const EC_KEY *key) {
  if (key == NULL) {
    return 0;
  }

  size_t group_order_size;
  if (key->ecdsa_meth && key->ecdsa_meth->group_order_size) {
    group_order_size = key->ecdsa_meth->group_order_size(key);
  } else {
    const EC_GROUP *group = EC_KEY_get0_group(key);
    if (group == NULL) {
      return 0;
    }

    group_order_size = BN_num_bytes(EC_GROUP_get0_order(group));
  }

  return ECDSA_SIG_max_len(group_order_size);
}

ECDSA_SIG *ECDSA_SIG_parse(CBS *cbs) {
  ECDSA_SIG *ret = ECDSA_SIG_new();
  if (ret == NULL) {
    return NULL;
  }
  CBS child;
  if (!CBS_get_asn1(cbs, &child, CBS_ASN1_SEQUENCE) ||
      !BN_parse_asn1_unsigned(&child, ret->r) ||
      !BN_parse_asn1_unsigned(&child, ret->s) ||
      CBS_len(&child) != 0) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_BAD_SIGNATURE);
    ECDSA_SIG_free(ret);
    return NULL;
  }
  return ret;
}

ECDSA_SIG *ECDSA_SIG_from_bytes(const uint8_t *in, size_t in_len) {
  CBS cbs;
  CBS_init(&cbs, in, in_len);
  ECDSA_SIG *ret = ECDSA_SIG_parse(&cbs);
  if (ret == NULL || CBS_len(&cbs) != 0) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_BAD_SIGNATURE);
    ECDSA_SIG_free(ret);
    return NULL;
  }
  return ret;
}

int ECDSA_SIG_marshal(CBB *cbb, const ECDSA_SIG *sig) {
  CBB child;
  if (!CBB_add_asn1(cbb, &child, CBS_ASN1_SEQUENCE) ||
      !BN_marshal_asn1(&child, sig->r) ||
      !BN_marshal_asn1(&child, sig->s) ||
      !CBB_flush(cbb)) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_ENCODE_ERROR);
    return 0;
  }
  return 1;
}

int ECDSA_SIG_to_bytes(uint8_t **out_bytes, size_t *out_len,
                       const ECDSA_SIG *sig) {
  CBB cbb;
  CBB_zero(&cbb);
  if (!CBB_init(&cbb, 0) ||
      !ECDSA_SIG_marshal(&cbb, sig) ||
      !CBB_finish(&cbb, out_bytes, out_len)) {
    OPENSSL_PUT_ERROR(ECDSA, ECDSA_R_ENCODE_ERROR);
    CBB_cleanup(&cbb);
    return 0;
  }
  return 1;
}

// der_len_len returns the number of bytes needed to represent a length of |len|
// in DER.
static size_t der_len_len(size_t len) {
  if (len < 0x80) {
    return 1;
  }
  size_t ret = 1;
  while (len > 0) {
    ret++;
    len >>= 8;
  }
  return ret;
}

size_t ECDSA_SIG_max_len(size_t order_len) {
  // Compute the maximum length of an |order_len| byte integer. Defensively
  // assume that the leading 0x00 is included.
  size_t integer_len = 1 /* tag */ + der_len_len(order_len + 1) + 1 + order_len;
  if (integer_len < order_len) {
    return 0;
  }
  // An ECDSA signature is two INTEGERs.
  size_t value_len = 2 * integer_len;
  if (value_len < integer_len) {
    return 0;
  }
  // Add the header.
  size_t ret = 1 /* tag */ + der_len_len(value_len) + value_len;
  if (ret < value_len) {
    return 0;
  }
  return ret;
}

ECDSA_SIG *d2i_ECDSA_SIG(ECDSA_SIG **out, const uint8_t **inp, long len) {
  if (len < 0) {
    return NULL;
  }
  CBS cbs;
  CBS_init(&cbs, *inp, (size_t)len);
  ECDSA_SIG *ret = ECDSA_SIG_parse(&cbs);
  if (ret == NULL) {
    return NULL;
  }
  if (out != NULL) {
    ECDSA_SIG_free(*out);
    *out = ret;
  }
  *inp = CBS_data(&cbs);
  return ret;
}

int i2d_ECDSA_SIG(const ECDSA_SIG *sig, uint8_t **outp) {
  CBB cbb;
  if (!CBB_init(&cbb, 0) ||
      !ECDSA_SIG_marshal(&cbb, sig)) {
    CBB_cleanup(&cbb);
    return -1;
  }
  return CBB_finish_i2d(&cbb, outp);
}

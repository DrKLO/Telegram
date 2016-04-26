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

#include "../ec/internal.h"


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

    BIGNUM *order = BN_new();
    if (order == NULL) {
      return 0;
    }
    if (!EC_GROUP_get_order(group, order, NULL)) {
      BN_clear_free(order);
      return 0;
    }

    group_order_size = BN_num_bytes(order);
    BN_clear_free(order);
  }

  return ECDSA_SIG_max_len(group_order_size);
}

ECDSA_SIG *ECDSA_SIG_new(void) {
  ECDSA_SIG *sig = OPENSSL_malloc(sizeof(ECDSA_SIG));
  if (sig == NULL) {
    return NULL;
  }
  sig->r = BN_new();
  sig->s = BN_new();
  if (sig->r == NULL || sig->s == NULL) {
    ECDSA_SIG_free(sig);
    return NULL;
  }
  return sig;
}

void ECDSA_SIG_free(ECDSA_SIG *sig) {
  if (sig == NULL) {
    return;
  }

  BN_free(sig->r);
  BN_free(sig->s);
  OPENSSL_free(sig);
}

ECDSA_SIG *ECDSA_SIG_parse(CBS *cbs) {
  ECDSA_SIG *ret = ECDSA_SIG_new();
  if (ret == NULL) {
    return NULL;
  }
  CBS child;
  if (!CBS_get_asn1(cbs, &child, CBS_ASN1_SEQUENCE) ||
      !BN_cbs2unsigned(&child, ret->r) ||
      !BN_cbs2unsigned(&child, ret->s) ||
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
      !BN_bn2cbb(&child, sig->r) ||
      !BN_bn2cbb(&child, sig->s) ||
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

/* der_len_len returns the number of bytes needed to represent a length of |len|
 * in DER. */
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
  /* Compute the maximum length of an |order_len| byte integer. Defensively
   * assume that the leading 0x00 is included. */
  size_t integer_len = 1 /* tag */ + der_len_len(order_len + 1) + 1 + order_len;
  if (integer_len < order_len) {
    return 0;
  }
  /* An ECDSA signature is two INTEGERs. */
  size_t value_len = 2 * integer_len;
  if (value_len < integer_len) {
    return 0;
  }
  /* Add the header. */
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
  *inp += (size_t)len - CBS_len(&cbs);
  return ret;
}

int i2d_ECDSA_SIG(const ECDSA_SIG *sig, uint8_t **outp) {
  uint8_t *der;
  size_t der_len;
  if (!ECDSA_SIG_to_bytes(&der, &der_len, sig)) {
    return -1;
  }
  if (der_len > INT_MAX) {
    OPENSSL_PUT_ERROR(ECDSA, ERR_R_OVERFLOW);
    OPENSSL_free(der);
    return -1;
  }
  if (outp != NULL) {
    if (*outp == NULL) {
      *outp = der;
      der = NULL;
    } else {
      memcpy(*outp, der, der_len);
      *outp += der_len;
    }
  }
  OPENSSL_free(der);
  return (int)der_len;
}

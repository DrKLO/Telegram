/* Written by Dr Stephen N Henson (steve@openssl.org) for the OpenSSL
 * project 2000.
 */
/* ====================================================================
 * Copyright (c) 2000-2005 The OpenSSL Project.  All rights reserved.
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
 *    licensing@OpenSSL.org.
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

#include <openssl/dh.h>

#include <assert.h>
#include <limits.h>

#include <openssl/bn.h>
#include <openssl/bytestring.h>
#include <openssl/err.h>

#include "../bytestring/internal.h"


static int parse_integer(CBS *cbs, BIGNUM **out) {
  assert(*out == NULL);
  *out = BN_new();
  if (*out == NULL) {
    return 0;
  }
  return BN_parse_asn1_unsigned(cbs, *out);
}

static int marshal_integer(CBB *cbb, BIGNUM *bn) {
  if (bn == NULL) {
    // A DH object may be missing some components.
    OPENSSL_PUT_ERROR(DH, ERR_R_PASSED_NULL_PARAMETER);
    return 0;
  }
  return BN_marshal_asn1(cbb, bn);
}

DH *DH_parse_parameters(CBS *cbs) {
  DH *ret = DH_new();
  if (ret == NULL) {
    return NULL;
  }

  CBS child;
  if (!CBS_get_asn1(cbs, &child, CBS_ASN1_SEQUENCE) ||
      !parse_integer(&child, &ret->p) ||
      !parse_integer(&child, &ret->g)) {
    goto err;
  }

  uint64_t priv_length;
  if (CBS_len(&child) != 0) {
    if (!CBS_get_asn1_uint64(&child, &priv_length) ||
        priv_length > UINT_MAX) {
      goto err;
    }
    ret->priv_length = (unsigned)priv_length;
  }

  if (CBS_len(&child) != 0) {
    goto err;
  }

  return ret;

err:
  OPENSSL_PUT_ERROR(DH, DH_R_DECODE_ERROR);
  DH_free(ret);
  return NULL;
}

int DH_marshal_parameters(CBB *cbb, const DH *dh) {
  CBB child;
  if (!CBB_add_asn1(cbb, &child, CBS_ASN1_SEQUENCE) ||
      !marshal_integer(&child, dh->p) ||
      !marshal_integer(&child, dh->g) ||
      (dh->priv_length != 0 &&
       !CBB_add_asn1_uint64(&child, dh->priv_length)) ||
      !CBB_flush(cbb)) {
    OPENSSL_PUT_ERROR(DH, DH_R_ENCODE_ERROR);
    return 0;
  }
  return 1;
}

DH *d2i_DHparams(DH **out, const uint8_t **inp, long len) {
  if (len < 0) {
    return NULL;
  }
  CBS cbs;
  CBS_init(&cbs, *inp, (size_t)len);
  DH *ret = DH_parse_parameters(&cbs);
  if (ret == NULL) {
    return NULL;
  }
  if (out != NULL) {
    DH_free(*out);
    *out = ret;
  }
  *inp = CBS_data(&cbs);
  return ret;
}

int i2d_DHparams(const DH *in, uint8_t **outp) {
  CBB cbb;
  if (!CBB_init(&cbb, 0) ||
      !DH_marshal_parameters(&cbb, in)) {
    CBB_cleanup(&cbb);
    return -1;
  }
  return CBB_finish_i2d(&cbb, outp);
}

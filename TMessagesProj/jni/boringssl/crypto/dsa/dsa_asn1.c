/* Written by Dr Stephen N Henson (steve@openssl.org) for the OpenSSL
 * project 2000. */
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

#include <openssl/dsa.h>

#include <assert.h>

#include <openssl/bn.h>
#include <openssl/bytestring.h>
#include <openssl/err.h>
#include <openssl/mem.h>

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
    // A DSA object may be missing some components.
    OPENSSL_PUT_ERROR(DSA, ERR_R_PASSED_NULL_PARAMETER);
    return 0;
  }
  return BN_marshal_asn1(cbb, bn);
}

DSA_SIG *DSA_SIG_parse(CBS *cbs) {
  DSA_SIG *ret = DSA_SIG_new();
  if (ret == NULL) {
    return NULL;
  }
  CBS child;
  if (!CBS_get_asn1(cbs, &child, CBS_ASN1_SEQUENCE) ||
      !parse_integer(&child, &ret->r) ||
      !parse_integer(&child, &ret->s) ||
      CBS_len(&child) != 0) {
    OPENSSL_PUT_ERROR(DSA, DSA_R_DECODE_ERROR);
    DSA_SIG_free(ret);
    return NULL;
  }
  return ret;
}

int DSA_SIG_marshal(CBB *cbb, const DSA_SIG *sig) {
  CBB child;
  if (!CBB_add_asn1(cbb, &child, CBS_ASN1_SEQUENCE) ||
      !marshal_integer(&child, sig->r) ||
      !marshal_integer(&child, sig->s) ||
      !CBB_flush(cbb)) {
    OPENSSL_PUT_ERROR(DSA, DSA_R_ENCODE_ERROR);
    return 0;
  }
  return 1;
}

DSA *DSA_parse_public_key(CBS *cbs) {
  DSA *ret = DSA_new();
  if (ret == NULL) {
    return NULL;
  }
  CBS child;
  if (!CBS_get_asn1(cbs, &child, CBS_ASN1_SEQUENCE) ||
      !parse_integer(&child, &ret->pub_key) ||
      !parse_integer(&child, &ret->p) ||
      !parse_integer(&child, &ret->q) ||
      !parse_integer(&child, &ret->g) ||
      CBS_len(&child) != 0) {
    OPENSSL_PUT_ERROR(DSA, DSA_R_DECODE_ERROR);
    DSA_free(ret);
    return NULL;
  }
  return ret;
}

int DSA_marshal_public_key(CBB *cbb, const DSA *dsa) {
  CBB child;
  if (!CBB_add_asn1(cbb, &child, CBS_ASN1_SEQUENCE) ||
      !marshal_integer(&child, dsa->pub_key) ||
      !marshal_integer(&child, dsa->p) ||
      !marshal_integer(&child, dsa->q) ||
      !marshal_integer(&child, dsa->g) ||
      !CBB_flush(cbb)) {
    OPENSSL_PUT_ERROR(DSA, DSA_R_ENCODE_ERROR);
    return 0;
  }
  return 1;
}

DSA *DSA_parse_parameters(CBS *cbs) {
  DSA *ret = DSA_new();
  if (ret == NULL) {
    return NULL;
  }
  CBS child;
  if (!CBS_get_asn1(cbs, &child, CBS_ASN1_SEQUENCE) ||
      !parse_integer(&child, &ret->p) ||
      !parse_integer(&child, &ret->q) ||
      !parse_integer(&child, &ret->g) ||
      CBS_len(&child) != 0) {
    OPENSSL_PUT_ERROR(DSA, DSA_R_DECODE_ERROR);
    DSA_free(ret);
    return NULL;
  }
  return ret;
}

int DSA_marshal_parameters(CBB *cbb, const DSA *dsa) {
  CBB child;
  if (!CBB_add_asn1(cbb, &child, CBS_ASN1_SEQUENCE) ||
      !marshal_integer(&child, dsa->p) ||
      !marshal_integer(&child, dsa->q) ||
      !marshal_integer(&child, dsa->g) ||
      !CBB_flush(cbb)) {
    OPENSSL_PUT_ERROR(DSA, DSA_R_ENCODE_ERROR);
    return 0;
  }
  return 1;
}

DSA *DSA_parse_private_key(CBS *cbs) {
  DSA *ret = DSA_new();
  if (ret == NULL) {
    return NULL;
  }

  CBS child;
  uint64_t version;
  if (!CBS_get_asn1(cbs, &child, CBS_ASN1_SEQUENCE) ||
      !CBS_get_asn1_uint64(&child, &version)) {
    OPENSSL_PUT_ERROR(DSA, DSA_R_DECODE_ERROR);
    goto err;
  }

  if (version != 0) {
    OPENSSL_PUT_ERROR(DSA, DSA_R_BAD_VERSION);
    goto err;
  }

  if (!parse_integer(&child, &ret->p) ||
      !parse_integer(&child, &ret->q) ||
      !parse_integer(&child, &ret->g) ||
      !parse_integer(&child, &ret->pub_key) ||
      !parse_integer(&child, &ret->priv_key) ||
      CBS_len(&child) != 0) {
    OPENSSL_PUT_ERROR(DSA, DSA_R_DECODE_ERROR);
    goto err;
  }
  return ret;

err:
  DSA_free(ret);
  return NULL;
}

int DSA_marshal_private_key(CBB *cbb, const DSA *dsa) {
  CBB child;
  if (!CBB_add_asn1(cbb, &child, CBS_ASN1_SEQUENCE) ||
      !CBB_add_asn1_uint64(&child, 0 /* version */) ||
      !marshal_integer(&child, dsa->p) ||
      !marshal_integer(&child, dsa->q) ||
      !marshal_integer(&child, dsa->g) ||
      !marshal_integer(&child, dsa->pub_key) ||
      !marshal_integer(&child, dsa->priv_key) ||
      !CBB_flush(cbb)) {
    OPENSSL_PUT_ERROR(DSA, DSA_R_ENCODE_ERROR);
    return 0;
  }
  return 1;
}

DSA_SIG *d2i_DSA_SIG(DSA_SIG **out_sig, const uint8_t **inp, long len) {
  if (len < 0) {
    return NULL;
  }
  CBS cbs;
  CBS_init(&cbs, *inp, (size_t)len);
  DSA_SIG *ret = DSA_SIG_parse(&cbs);
  if (ret == NULL) {
    return NULL;
  }
  if (out_sig != NULL) {
    DSA_SIG_free(*out_sig);
    *out_sig = ret;
  }
  *inp = CBS_data(&cbs);
  return ret;
}

int i2d_DSA_SIG(const DSA_SIG *in, uint8_t **outp) {
  CBB cbb;
  if (!CBB_init(&cbb, 0) ||
      !DSA_SIG_marshal(&cbb, in)) {
    CBB_cleanup(&cbb);
    return -1;
  }
  return CBB_finish_i2d(&cbb, outp);
}

DSA *d2i_DSAPublicKey(DSA **out, const uint8_t **inp, long len) {
  if (len < 0) {
    return NULL;
  }
  CBS cbs;
  CBS_init(&cbs, *inp, (size_t)len);
  DSA *ret = DSA_parse_public_key(&cbs);
  if (ret == NULL) {
    return NULL;
  }
  if (out != NULL) {
    DSA_free(*out);
    *out = ret;
  }
  *inp = CBS_data(&cbs);
  return ret;
}

int i2d_DSAPublicKey(const DSA *in, uint8_t **outp) {
  CBB cbb;
  if (!CBB_init(&cbb, 0) ||
      !DSA_marshal_public_key(&cbb, in)) {
    CBB_cleanup(&cbb);
    return -1;
  }
  return CBB_finish_i2d(&cbb, outp);
}

DSA *d2i_DSAPrivateKey(DSA **out, const uint8_t **inp, long len) {
  if (len < 0) {
    return NULL;
  }
  CBS cbs;
  CBS_init(&cbs, *inp, (size_t)len);
  DSA *ret = DSA_parse_private_key(&cbs);
  if (ret == NULL) {
    return NULL;
  }
  if (out != NULL) {
    DSA_free(*out);
    *out = ret;
  }
  *inp = CBS_data(&cbs);
  return ret;
}

int i2d_DSAPrivateKey(const DSA *in, uint8_t **outp) {
  CBB cbb;
  if (!CBB_init(&cbb, 0) ||
      !DSA_marshal_private_key(&cbb, in)) {
    CBB_cleanup(&cbb);
    return -1;
  }
  return CBB_finish_i2d(&cbb, outp);
}

DSA *d2i_DSAparams(DSA **out, const uint8_t **inp, long len) {
  if (len < 0) {
    return NULL;
  }
  CBS cbs;
  CBS_init(&cbs, *inp, (size_t)len);
  DSA *ret = DSA_parse_parameters(&cbs);
  if (ret == NULL) {
    return NULL;
  }
  if (out != NULL) {
    DSA_free(*out);
    *out = ret;
  }
  *inp = CBS_data(&cbs);
  return ret;
}

int i2d_DSAparams(const DSA *in, uint8_t **outp) {
  CBB cbb;
  if (!CBB_init(&cbb, 0) ||
      !DSA_marshal_parameters(&cbb, in)) {
    CBB_cleanup(&cbb);
    return -1;
  }
  return CBB_finish_i2d(&cbb, outp);
}

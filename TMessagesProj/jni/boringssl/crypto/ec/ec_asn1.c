/* Written by Nils Larsch for the OpenSSL project. */
/* ====================================================================
 * Copyright (c) 2000-2003 The OpenSSL Project.  All rights reserved.
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

#include <openssl/ec.h>

#include <string.h>

#include <openssl/asn1.h>
#include <openssl/asn1t.h>
#include <openssl/bn.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/obj.h>

#include "internal.h"


typedef struct x9_62_fieldid_st {
  ASN1_OBJECT *fieldType;
  union {
    char *ptr;
    /* NID_X9_62_prime_field */
    ASN1_INTEGER *prime;
    /* anything else */
    ASN1_TYPE *other;
  } p;
} X9_62_FIELDID;

ASN1_ADB_TEMPLATE(fieldID_def) = ASN1_SIMPLE(X9_62_FIELDID, p.other, ASN1_ANY);

ASN1_ADB(X9_62_FIELDID) = {
  ADB_ENTRY(NID_X9_62_prime_field, ASN1_SIMPLE(X9_62_FIELDID, p.prime, ASN1_INTEGER)),
} ASN1_ADB_END(X9_62_FIELDID, 0, fieldType, 0, &fieldID_def_tt, NULL);

ASN1_SEQUENCE(X9_62_FIELDID) = {
  ASN1_SIMPLE(X9_62_FIELDID, fieldType, ASN1_OBJECT),
  ASN1_ADB_OBJECT(X9_62_FIELDID)
} ASN1_SEQUENCE_END(X9_62_FIELDID);

typedef struct x9_62_curve_st {
  ASN1_OCTET_STRING *a;
  ASN1_OCTET_STRING *b;
  ASN1_BIT_STRING *seed;
} X9_62_CURVE;

ASN1_SEQUENCE(X9_62_CURVE) = {
  ASN1_SIMPLE(X9_62_CURVE, a, ASN1_OCTET_STRING),
  ASN1_SIMPLE(X9_62_CURVE, b, ASN1_OCTET_STRING),
  ASN1_OPT(X9_62_CURVE, seed, ASN1_BIT_STRING)
} ASN1_SEQUENCE_END(X9_62_CURVE);

typedef struct ec_parameters_st {
  long version;
  X9_62_FIELDID *fieldID;
  X9_62_CURVE *curve;
  ASN1_OCTET_STRING *base;
  ASN1_INTEGER *order;
  ASN1_INTEGER *cofactor;
} ECPARAMETERS;

DECLARE_ASN1_ALLOC_FUNCTIONS(ECPARAMETERS);

ASN1_SEQUENCE(ECPARAMETERS) = {
    ASN1_SIMPLE(ECPARAMETERS, version, LONG),
    ASN1_SIMPLE(ECPARAMETERS, fieldID, X9_62_FIELDID),
    ASN1_SIMPLE(ECPARAMETERS, curve, X9_62_CURVE),
    ASN1_SIMPLE(ECPARAMETERS, base, ASN1_OCTET_STRING),
    ASN1_SIMPLE(ECPARAMETERS, order, ASN1_INTEGER),
    ASN1_OPT(ECPARAMETERS, cofactor, ASN1_INTEGER)
} ASN1_SEQUENCE_END(ECPARAMETERS);

IMPLEMENT_ASN1_ALLOC_FUNCTIONS(ECPARAMETERS);

typedef struct ecpk_parameters_st {
  int type;
  union {
    ASN1_OBJECT *named_curve;
    ECPARAMETERS *parameters;
  } value;
} ECPKPARAMETERS;

/* SEC1 ECPrivateKey */
typedef struct ec_privatekey_st {
  long version;
  ASN1_OCTET_STRING *privateKey;
  ECPKPARAMETERS *parameters;
  ASN1_BIT_STRING *publicKey;
} EC_PRIVATEKEY;

DECLARE_ASN1_FUNCTIONS_const(ECPKPARAMETERS);
DECLARE_ASN1_ENCODE_FUNCTIONS_const(ECPKPARAMETERS, ECPKPARAMETERS);

ASN1_CHOICE(ECPKPARAMETERS) = {
    ASN1_SIMPLE(ECPKPARAMETERS, value.named_curve, ASN1_OBJECT),
    ASN1_SIMPLE(ECPKPARAMETERS, value.parameters, ECPARAMETERS),
} ASN1_CHOICE_END(ECPKPARAMETERS);

IMPLEMENT_ASN1_FUNCTIONS_const(ECPKPARAMETERS);

DECLARE_ASN1_FUNCTIONS_const(EC_PRIVATEKEY);
DECLARE_ASN1_ENCODE_FUNCTIONS_const(EC_PRIVATEKEY, EC_PRIVATEKEY);

ASN1_SEQUENCE(EC_PRIVATEKEY) = {
    ASN1_SIMPLE(EC_PRIVATEKEY, version, LONG),
    ASN1_SIMPLE(EC_PRIVATEKEY, privateKey, ASN1_OCTET_STRING),
    ASN1_EXP_OPT(EC_PRIVATEKEY, parameters, ECPKPARAMETERS, 0),
    ASN1_EXP_OPT(EC_PRIVATEKEY, publicKey, ASN1_BIT_STRING, 1),
} ASN1_SEQUENCE_END(EC_PRIVATEKEY);

IMPLEMENT_ASN1_FUNCTIONS_const(EC_PRIVATEKEY);


ECPKPARAMETERS *ec_asn1_group2pkparameters(const EC_GROUP *group,
                                           ECPKPARAMETERS *params) {
  int ok = 0, nid;
  ECPKPARAMETERS *ret = params;

  if (ret == NULL) {
    ret = ECPKPARAMETERS_new();
    if (ret == NULL) {
      OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
      return NULL;
    }
  } else {
    ASN1_OBJECT_free(ret->value.named_curve);
  }

  /* use the ASN.1 OID to describe the the elliptic curve parameters. */
  nid = EC_GROUP_get_curve_name(group);
  if (nid) {
    ret->type = 0;
    ret->value.named_curve = (ASN1_OBJECT*) OBJ_nid2obj(nid);
    ok = ret->value.named_curve != NULL;
  }

  if (!ok) {
    ECPKPARAMETERS_free(ret);
    return NULL;
  }

  return ret;
}

EC_GROUP *ec_asn1_pkparameters2group(const ECPKPARAMETERS *params) {
  EC_GROUP *ret = NULL;
  int nid = NID_undef;

  if (params == NULL) {
    OPENSSL_PUT_ERROR(EC, EC_R_MISSING_PARAMETERS);
    return NULL;
  }

  if (params->type == 0) {
    nid = OBJ_obj2nid(params->value.named_curve);
  } else if (params->type == 1) {
    /* We don't support arbitary curves so we attempt to recognise it from the
     * group order. */
    const ECPARAMETERS *ecparams = params->value.parameters;
    unsigned i;
    const struct built_in_curve *curve;

    for (i = 0; OPENSSL_built_in_curves[i].nid != NID_undef; i++) {
      curve = &OPENSSL_built_in_curves[i];
      const unsigned param_len = curve->data->param_len;
      if (ecparams->order->length == param_len &&
          memcmp(ecparams->order->data, &curve->data->data[param_len * 5],
                 param_len) == 0) {
        nid = curve->nid;
        break;
      }
    }
  }

  if (nid == NID_undef) {
    OPENSSL_PUT_ERROR(EC, EC_R_NON_NAMED_CURVE);
    return NULL;
  }

  ret = EC_GROUP_new_by_curve_name(nid);
  if (ret == NULL) {
    OPENSSL_PUT_ERROR(EC, EC_R_EC_GROUP_NEW_BY_NAME_FAILURE);
    return NULL;
  }

  return ret;
}

static EC_GROUP *d2i_ECPKParameters(EC_GROUP **groupp, const uint8_t **inp,
                                    long len) {
  EC_GROUP *group = NULL;
  ECPKPARAMETERS *params = NULL;

  params = d2i_ECPKPARAMETERS(NULL, inp, len);
  if (params == NULL) {
    OPENSSL_PUT_ERROR(EC, EC_R_D2I_ECPKPARAMETERS_FAILURE);
    ECPKPARAMETERS_free(params);
    return NULL;
  }

  group = ec_asn1_pkparameters2group(params);
  if (group == NULL) {
    OPENSSL_PUT_ERROR(EC, EC_R_PKPARAMETERS2GROUP_FAILURE);
    ECPKPARAMETERS_free(params);
    return NULL;
  }

  if (groupp) {
    EC_GROUP_free(*groupp);
    *groupp = group;
  }

  ECPKPARAMETERS_free(params);
  return group;
}

static int i2d_ECPKParameters(const EC_GROUP *group, uint8_t **outp) {
  int ret = 0;
  ECPKPARAMETERS *tmp = ec_asn1_group2pkparameters(group, NULL);
  if (tmp == NULL) {
    OPENSSL_PUT_ERROR(EC, EC_R_GROUP2PKPARAMETERS_FAILURE);
    return 0;
  }
  ret = i2d_ECPKPARAMETERS(tmp, outp);
  if (ret == 0) {
    OPENSSL_PUT_ERROR(EC, EC_R_I2D_ECPKPARAMETERS_FAILURE);
    ECPKPARAMETERS_free(tmp);
    return 0;
  }
  ECPKPARAMETERS_free(tmp);
  return ret;
}

EC_KEY *d2i_ECPrivateKey(EC_KEY **a, const uint8_t **in, long len) {
  int ok = 0;
  EC_KEY *ret = NULL;
  EC_PRIVATEKEY *priv_key = NULL;

  priv_key = d2i_EC_PRIVATEKEY(NULL, in, len);
  if (priv_key == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
    return NULL;
  }

  if (a == NULL || *a == NULL) {
    ret = EC_KEY_new();
    if (ret == NULL) {
      OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
      goto err;
    }
  } else {
    ret = *a;
  }

  if (priv_key->parameters) {
    EC_GROUP_free(ret->group);
    ret->group = ec_asn1_pkparameters2group(priv_key->parameters);
  }

  if (ret->group == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
    goto err;
  }

  ret->version = priv_key->version;

  if (priv_key->privateKey) {
    ret->priv_key =
        BN_bin2bn(M_ASN1_STRING_data(priv_key->privateKey),
                  M_ASN1_STRING_length(priv_key->privateKey), ret->priv_key);
    if (ret->priv_key == NULL) {
      OPENSSL_PUT_ERROR(EC, ERR_R_BN_LIB);
      goto err;
    }
  } else {
    OPENSSL_PUT_ERROR(EC, EC_R_MISSING_PRIVATE_KEY);
    goto err;
  }

  EC_POINT_free(ret->pub_key);
  ret->pub_key = EC_POINT_new(ret->group);
  if (ret->pub_key == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
    goto err;
  }

  if (priv_key->publicKey) {
    const uint8_t *pub_oct;
    int pub_oct_len;

    pub_oct = M_ASN1_STRING_data(priv_key->publicKey);
    pub_oct_len = M_ASN1_STRING_length(priv_key->publicKey);
    /* The first byte (the point conversion form) must be present. */
    if (pub_oct_len <= 0) {
      OPENSSL_PUT_ERROR(EC, EC_R_BUFFER_TOO_SMALL);
      goto err;
    }
    /* Save the point conversion form. */
    ret->conv_form = (point_conversion_form_t)(pub_oct[0] & ~0x01);
    if (!EC_POINT_oct2point(ret->group, ret->pub_key, pub_oct, pub_oct_len,
                            NULL)) {
      OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
      goto err;
    }
  } else {
    if (!EC_POINT_mul(ret->group, ret->pub_key, ret->priv_key, NULL, NULL,
                      NULL)) {
      OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
      goto err;
    }
    /* Remember the original private-key-only encoding. */
    ret->enc_flag |= EC_PKEY_NO_PUBKEY;
  }

  if (a) {
    *a = ret;
  }
  ok = 1;

err:
  if (!ok) {
    if (a == NULL || *a != ret) {
      EC_KEY_free(ret);
    }
    ret = NULL;
  }

  EC_PRIVATEKEY_free(priv_key);

  return ret;
}

int i2d_ECPrivateKey(const EC_KEY *key, uint8_t **outp) {
  int ret = 0, ok = 0;
  uint8_t *buffer = NULL;
  size_t buf_len = 0, tmp_len;
  EC_PRIVATEKEY *priv_key = NULL;

  if (key == NULL || key->group == NULL || key->priv_key == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_PASSED_NULL_PARAMETER);
    goto err;
  }

  priv_key = EC_PRIVATEKEY_new();
  if (priv_key == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  priv_key->version = key->version;

  buf_len = BN_num_bytes(&key->group->order);
  buffer = OPENSSL_malloc(buf_len);
  if (buffer == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  if (!BN_bn2bin_padded(buffer, buf_len, key->priv_key)) {
    OPENSSL_PUT_ERROR(EC, ERR_R_BN_LIB);
    goto err;
  }

  if (!M_ASN1_OCTET_STRING_set(priv_key->privateKey, buffer, buf_len)) {
    OPENSSL_PUT_ERROR(EC, ERR_R_ASN1_LIB);
    goto err;
  }

  /* TODO(fork): replace this flexibility with key sensible default? */
  if (!(key->enc_flag & EC_PKEY_NO_PARAMETERS)) {
    if ((priv_key->parameters = ec_asn1_group2pkparameters(
             key->group, priv_key->parameters)) == NULL) {
      OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
      goto err;
    }
  }

  /* TODO(fork): replace this flexibility with key sensible default? */
  if (!(key->enc_flag & EC_PKEY_NO_PUBKEY) && key->pub_key != NULL) {
    priv_key->publicKey = M_ASN1_BIT_STRING_new();
    if (priv_key->publicKey == NULL) {
      OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
      goto err;
    }

    tmp_len = EC_POINT_point2oct(key->group, key->pub_key, key->conv_form, NULL,
                                 0, NULL);

    if (tmp_len > buf_len) {
      uint8_t *tmp_buffer = OPENSSL_realloc(buffer, tmp_len);
      if (!tmp_buffer) {
        OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
        goto err;
      }
      buffer = tmp_buffer;
      buf_len = tmp_len;
    }

    if (!EC_POINT_point2oct(key->group, key->pub_key, key->conv_form, buffer,
                            buf_len, NULL)) {
      OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
      goto err;
    }

    priv_key->publicKey->flags &= ~(ASN1_STRING_FLAG_BITS_LEFT | 0x07);
    priv_key->publicKey->flags |= ASN1_STRING_FLAG_BITS_LEFT;
    if (!M_ASN1_BIT_STRING_set(priv_key->publicKey, buffer, buf_len)) {
      OPENSSL_PUT_ERROR(EC, ERR_R_ASN1_LIB);
      goto err;
    }
  }

  ret = i2d_EC_PRIVATEKEY(priv_key, outp);
  if (ret == 0) {
    OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
    goto err;
  }
  ok = 1;

err:
  OPENSSL_free(buffer);
  EC_PRIVATEKEY_free(priv_key);
  return (ok ? ret : 0);
}

int i2d_ECParameters(const EC_KEY *key, uint8_t **outp) {
  if (key == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_PASSED_NULL_PARAMETER);
    return 0;
  }
  return i2d_ECPKParameters(key->group, outp);
}

EC_KEY *d2i_ECParameters(EC_KEY **key, const uint8_t **inp, long len) {
  EC_KEY *ret;

  if (inp == NULL || *inp == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_PASSED_NULL_PARAMETER);
    return NULL;
  }

  if (key == NULL || *key == NULL) {
    ret = EC_KEY_new();
    if (ret == NULL) {
      OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
      return NULL;
    }
  } else {
    ret = *key;
  }

  if (!d2i_ECPKParameters(&ret->group, inp, len)) {
    OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
    if (key == NULL || *key == NULL) {
      EC_KEY_free(ret);
    }
    return NULL;
  }

  if (key) {
    *key = ret;
  }
  return ret;
}

EC_KEY *o2i_ECPublicKey(EC_KEY **keyp, const uint8_t **inp, long len) {
  EC_KEY *ret = NULL;

  if (keyp == NULL || *keyp == NULL || (*keyp)->group == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_PASSED_NULL_PARAMETER);
    return 0;
  }
  ret = *keyp;
  if (ret->pub_key == NULL &&
      (ret->pub_key = EC_POINT_new(ret->group)) == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
    return 0;
  }
  if (!EC_POINT_oct2point(ret->group, ret->pub_key, *inp, len, NULL)) {
    OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
    return 0;
  }
  /* save the point conversion form */
  ret->conv_form = (point_conversion_form_t)(*inp[0] & ~0x01);
  *inp += len;
  return ret;
}

int i2o_ECPublicKey(const EC_KEY *key, uint8_t **outp) {
  size_t buf_len = 0;
  int new_buffer = 0;

  if (key == NULL) {
    OPENSSL_PUT_ERROR(EC, ERR_R_PASSED_NULL_PARAMETER);
    return 0;
  }

  buf_len = EC_POINT_point2oct(key->group, key->pub_key, key->conv_form, NULL,
                               0, NULL);

  if (outp == NULL || buf_len == 0) {
    /* out == NULL => just return the length of the octet string */
    return buf_len;
  }

  if (*outp == NULL) {
    *outp = OPENSSL_malloc(buf_len);
    if (*outp == NULL) {
      OPENSSL_PUT_ERROR(EC, ERR_R_MALLOC_FAILURE);
      return 0;
    }
    new_buffer = 1;
  }
  if (!EC_POINT_point2oct(key->group, key->pub_key, key->conv_form, *outp,
                          buf_len, NULL)) {
    OPENSSL_PUT_ERROR(EC, ERR_R_EC_LIB);
    if (new_buffer) {
      OPENSSL_free(*outp);
      *outp = NULL;
    }
    return 0;
  }

  if (!new_buffer) {
    *outp += buf_len;
  }
  return buf_len;
}

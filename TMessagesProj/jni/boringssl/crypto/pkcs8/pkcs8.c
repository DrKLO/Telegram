/* Written by Dr Stephen N Henson (steve@openssl.org) for the OpenSSL
 * project 1999.
 */
/* ====================================================================
 * Copyright (c) 1999 The OpenSSL Project.  All rights reserved.
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

#include <openssl/pkcs8.h>

#include <assert.h>
#include <limits.h>
#include <string.h>

#include <openssl/asn1.h>
#include <openssl/bn.h>
#include <openssl/buf.h>
#include <openssl/cipher.h>
#include <openssl/digest.h>
#include <openssl/err.h>
#include <openssl/hmac.h>
#include <openssl/mem.h>
#include <openssl/x509.h>

#include "../bytestring/internal.h"
#include "../evp/internal.h"


#define PKCS12_KEY_ID 1
#define PKCS12_IV_ID 2
#define PKCS12_MAC_ID 3

static int ascii_to_ucs2(const char *ascii, size_t ascii_len,
                         uint8_t **out, size_t *out_len) {
  uint8_t *unitmp;
  size_t ulen, i;

  ulen = ascii_len * 2 + 2;
  if (ulen < ascii_len) {
    return 0;
  }
  unitmp = OPENSSL_malloc(ulen);
  if (unitmp == NULL) {
    return 0;
  }
  for (i = 0; i < ulen - 2; i += 2) {
    unitmp[i] = 0;
    unitmp[i + 1] = ascii[i >> 1];
  }

  /* Make result double null terminated */
  unitmp[ulen - 2] = 0;
  unitmp[ulen - 1] = 0;
  *out_len = ulen;
  *out = unitmp;
  return 1;
}

static int pkcs12_key_gen_raw(const uint8_t *pass_raw, size_t pass_raw_len,
                              const uint8_t *salt, size_t salt_len,
                              int id, int iterations,
                              size_t out_len, uint8_t *out,
                              const EVP_MD *md_type) {
  uint8_t *B, *D, *I, *p, *Ai;
  int Slen, Plen, Ilen, Ijlen;
  int i, j, v;
  size_t u;
  int ret = 0;
  BIGNUM *Ij, *Bpl1; /* These hold Ij and B + 1 */
  EVP_MD_CTX ctx;

  EVP_MD_CTX_init(&ctx);
  v = EVP_MD_block_size(md_type);
  u = EVP_MD_size(md_type);
  D = OPENSSL_malloc(v);
  Ai = OPENSSL_malloc(u);
  B = OPENSSL_malloc(v + 1);
  Slen = v * ((salt_len + v - 1) / v);
  if (pass_raw_len) {
    Plen = v * ((pass_raw_len + v - 1) / v);
  } else {
    Plen = 0;
  }
  Ilen = Slen + Plen;
  I = OPENSSL_malloc(Ilen);
  Ij = BN_new();
  Bpl1 = BN_new();
  if (!D || !Ai || !B || !I || !Ij || !Bpl1) {
    goto err;
  }
  for (i = 0; i < v; i++) {
    D[i] = id;
  }
  p = I;
  for (i = 0; i < Slen; i++) {
    *p++ = salt[i % salt_len];
  }
  for (i = 0; i < Plen; i++) {
    *p++ = pass_raw[i % pass_raw_len];
  }
  for (;;) {
    if (!EVP_DigestInit_ex(&ctx, md_type, NULL) ||
        !EVP_DigestUpdate(&ctx, D, v) ||
        !EVP_DigestUpdate(&ctx, I, Ilen) ||
        !EVP_DigestFinal_ex(&ctx, Ai, NULL)) {
      goto err;
    }
    for (j = 1; j < iterations; j++) {
      if (!EVP_DigestInit_ex(&ctx, md_type, NULL) ||
          !EVP_DigestUpdate(&ctx, Ai, u) ||
          !EVP_DigestFinal_ex(&ctx, Ai, NULL)) {
        goto err;
      }
    }
    memcpy(out, Ai, out_len < u ? out_len : u);
    if (u >= out_len) {
      ret = 1;
      goto end;
    }
    out_len -= u;
    out += u;
    for (j = 0; j < v; j++) {
      B[j] = Ai[j % u];
    }
    /* Work out B + 1 first then can use B as tmp space */
    if (!BN_bin2bn(B, v, Bpl1) ||
        !BN_add_word(Bpl1, 1)) {
      goto err;
    }
    for (j = 0; j < Ilen; j += v) {
      if (!BN_bin2bn(I + j, v, Ij) ||
          !BN_add(Ij, Ij, Bpl1) ||
          !BN_bn2bin(Ij, B)) {
        goto err;
      }
      Ijlen = BN_num_bytes(Ij);
      /* If more than 2^(v*8) - 1 cut off MSB */
      if (Ijlen > v) {
        if (!BN_bn2bin(Ij, B)) {
          goto err;
        }
        memcpy(I + j, B + 1, v);
        /* If less than v bytes pad with zeroes */
      } else if (Ijlen < v) {
        memset(I + j, 0, v - Ijlen);
        if (!BN_bn2bin(Ij, I + j + v - Ijlen)) {
          goto err;
        }
      } else if (!BN_bn2bin(Ij, I + j)) {
        goto err;
      }
    }
  }

err:
  OPENSSL_PUT_ERROR(PKCS8, ERR_R_MALLOC_FAILURE);

end:
  OPENSSL_free(Ai);
  OPENSSL_free(B);
  OPENSSL_free(D);
  OPENSSL_free(I);
  BN_free(Ij);
  BN_free(Bpl1);
  EVP_MD_CTX_cleanup(&ctx);

  return ret;
}

static int pkcs12_pbe_keyivgen(EVP_CIPHER_CTX *ctx, const uint8_t *pass_raw,
                               size_t pass_raw_len, ASN1_TYPE *param,
                               const EVP_CIPHER *cipher, const EVP_MD *md,
                               int is_encrypt) {
  PBEPARAM *pbe;
  int salt_len, iterations, ret;
  uint8_t *salt;
  const uint8_t *pbuf;
  uint8_t key[EVP_MAX_KEY_LENGTH], iv[EVP_MAX_IV_LENGTH];

  /* Extract useful info from parameter */
  if (param == NULL || param->type != V_ASN1_SEQUENCE ||
      param->value.sequence == NULL) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_DECODE_ERROR);
    return 0;
  }

  pbuf = param->value.sequence->data;
  pbe = d2i_PBEPARAM(NULL, &pbuf, param->value.sequence->length);
  if (pbe == NULL) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_DECODE_ERROR);
    return 0;
  }

  if (!pbe->iter) {
    iterations = 1;
  } else {
    iterations = ASN1_INTEGER_get(pbe->iter);
  }
  salt = pbe->salt->data;
  salt_len = pbe->salt->length;
  if (!pkcs12_key_gen_raw(pass_raw, pass_raw_len, salt, salt_len, PKCS12_KEY_ID,
                          iterations, EVP_CIPHER_key_length(cipher), key, md)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_KEY_GEN_ERROR);
    PBEPARAM_free(pbe);
    return 0;
  }
  if (!pkcs12_key_gen_raw(pass_raw, pass_raw_len, salt, salt_len, PKCS12_IV_ID,
                          iterations, EVP_CIPHER_iv_length(cipher), iv, md)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_KEY_GEN_ERROR);
    PBEPARAM_free(pbe);
    return 0;
  }
  PBEPARAM_free(pbe);
  ret = EVP_CipherInit_ex(ctx, cipher, NULL, key, iv, is_encrypt);
  OPENSSL_cleanse(key, EVP_MAX_KEY_LENGTH);
  OPENSSL_cleanse(iv, EVP_MAX_IV_LENGTH);
  return ret;
}

typedef int (*keygen_func)(EVP_CIPHER_CTX *ctx, const uint8_t *pass_raw,
                           size_t pass_raw_len, ASN1_TYPE *param,
                           const EVP_CIPHER *cipher, const EVP_MD *md,
                           int is_encrypt);

struct pbe_suite {
  int pbe_nid;
  const EVP_CIPHER* (*cipher_func)(void);
  const EVP_MD* (*md_func)(void);
  keygen_func keygen;
};

static const struct pbe_suite kBuiltinPBE[] = {
    {
     NID_pbe_WithSHA1And40BitRC2_CBC, EVP_rc2_40_cbc, EVP_sha1, pkcs12_pbe_keyivgen,
    },
    {
     NID_pbe_WithSHA1And128BitRC4, EVP_rc4, EVP_sha1, pkcs12_pbe_keyivgen,
    },
    {
     NID_pbe_WithSHA1And3_Key_TripleDES_CBC, EVP_des_ede3_cbc, EVP_sha1,
     pkcs12_pbe_keyivgen,
    },
};

static int pbe_cipher_init(ASN1_OBJECT *pbe_obj,
                           const uint8_t *pass_raw, size_t pass_raw_len,
                           ASN1_TYPE *param,
                           EVP_CIPHER_CTX *ctx, int is_encrypt) {
  const EVP_CIPHER *cipher;
  const EVP_MD *md;
  unsigned i;

  const struct pbe_suite *suite = NULL;
  const int pbe_nid = OBJ_obj2nid(pbe_obj);

  for (i = 0; i < sizeof(kBuiltinPBE) / sizeof(struct pbe_suite); i++) {
    if (kBuiltinPBE[i].pbe_nid == pbe_nid) {
      suite = &kBuiltinPBE[i];
      break;
    }
  }

  if (suite == NULL) {
    char obj_str[80];
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_UNKNOWN_ALGORITHM);
    if (!pbe_obj) {
      strncpy(obj_str, "NULL", sizeof(obj_str));
    } else {
      i2t_ASN1_OBJECT(obj_str, sizeof(obj_str), pbe_obj);
    }
    ERR_add_error_data(2, "TYPE=", obj_str);
    return 0;
  }

  if (suite->cipher_func == NULL) {
    cipher = NULL;
  } else {
    cipher = suite->cipher_func();
    if (!cipher) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_UNKNOWN_CIPHER);
      return 0;
    }
  }

  if (suite->md_func == NULL) {
    md = NULL;
  } else {
    md = suite->md_func();
    if (!md) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_UNKNOWN_DIGEST);
      return 0;
    }
  }

  if (!suite->keygen(ctx, pass_raw, pass_raw_len, param, cipher, md,
                     is_encrypt)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_KEYGEN_FAILURE);
    return 0;
  }

  return 1;
}

static int pbe_crypt(const X509_ALGOR *algor,
                     const uint8_t *pass_raw, size_t pass_raw_len,
                     const uint8_t *in, size_t in_len,
                     uint8_t **out, size_t *out_len,
                     int is_encrypt) {
  uint8_t *buf;
  int n, ret = 0;
  EVP_CIPHER_CTX ctx;
  unsigned block_size;

  EVP_CIPHER_CTX_init(&ctx);

  if (!pbe_cipher_init(algor->algorithm, pass_raw, pass_raw_len,
                       algor->parameter, &ctx, is_encrypt)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_UNKNOWN_CIPHER_ALGORITHM);
    return 0;
  }
  block_size = EVP_CIPHER_CTX_block_size(&ctx);

  if (in_len + block_size < in_len) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_TOO_LONG);
    goto err;
  }

  buf = OPENSSL_malloc(in_len + block_size);
  if (buf == NULL) {
    OPENSSL_PUT_ERROR(PKCS8, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  if (!EVP_CipherUpdate(&ctx, buf, &n, in, in_len)) {
    OPENSSL_free(buf);
    OPENSSL_PUT_ERROR(PKCS8, ERR_R_EVP_LIB);
    goto err;
  }
  *out_len = n;

  if (!EVP_CipherFinal_ex(&ctx, buf + n, &n)) {
    OPENSSL_free(buf);
    OPENSSL_PUT_ERROR(PKCS8, ERR_R_EVP_LIB);
    goto err;
  }
  *out_len += n;
  *out = buf;
  ret = 1;

err:
  EVP_CIPHER_CTX_cleanup(&ctx);
  return ret;
}

static void *pkcs12_item_decrypt_d2i(X509_ALGOR *algor, const ASN1_ITEM *it,
                                     const uint8_t *pass_raw,
                                     size_t pass_raw_len,
                                     ASN1_OCTET_STRING *oct) {
  uint8_t *out;
  const uint8_t *p;
  void *ret;
  size_t out_len;

  if (!pbe_crypt(algor, pass_raw, pass_raw_len, oct->data, oct->length,
                 &out, &out_len, 0 /* decrypt */)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_CRYPT_ERROR);
    return NULL;
  }
  p = out;
  ret = ASN1_item_d2i(NULL, &p, out_len, it);
  OPENSSL_cleanse(out, out_len);
  if (!ret) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_DECODE_ERROR);
  }
  OPENSSL_free(out);
  return ret;
}

PKCS8_PRIV_KEY_INFO *PKCS8_decrypt(X509_SIG *pkcs8, const char *pass,
                                   int pass_len) {
  uint8_t *pass_raw = NULL;
  size_t pass_raw_len = 0;
  PKCS8_PRIV_KEY_INFO *ret;

  if (pass) {
    if (pass_len == -1) {
      pass_len = strlen(pass);
    }
    if (!ascii_to_ucs2(pass, pass_len, &pass_raw, &pass_raw_len)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_DECODE_ERROR);
      return NULL;
    }
  }

  ret = PKCS8_decrypt_pbe(pkcs8, pass_raw, pass_raw_len);

  if (pass_raw) {
    OPENSSL_cleanse(pass_raw, pass_raw_len);
    OPENSSL_free(pass_raw);
  }
  return ret;
}

PKCS8_PRIV_KEY_INFO *PKCS8_decrypt_pbe(X509_SIG *pkcs8, const uint8_t *pass_raw,
                                       size_t pass_raw_len) {
  return pkcs12_item_decrypt_d2i(pkcs8->algor,
                                 ASN1_ITEM_rptr(PKCS8_PRIV_KEY_INFO), pass_raw,
                                 pass_raw_len, pkcs8->digest);
}

static ASN1_OCTET_STRING *pkcs12_item_i2d_encrypt(X509_ALGOR *algor,
                                                  const ASN1_ITEM *it,
                                                  const uint8_t *pass_raw,
                                                  size_t pass_raw_len, void *obj) {
  ASN1_OCTET_STRING *oct;
  uint8_t *in = NULL;
  int in_len;
  size_t crypt_len;

  oct = M_ASN1_OCTET_STRING_new();
  if (oct == NULL) {
    OPENSSL_PUT_ERROR(PKCS8, ERR_R_MALLOC_FAILURE);
    return NULL;
  }
  in_len = ASN1_item_i2d(obj, &in, it);
  if (!in) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_ENCODE_ERROR);
    return NULL;
  }
  if (!pbe_crypt(algor, pass_raw, pass_raw_len, in, in_len, &oct->data, &crypt_len,
                 1 /* encrypt */)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_ENCRYPT_ERROR);
    OPENSSL_free(in);
    return NULL;
  }
  oct->length = crypt_len;
  OPENSSL_cleanse(in, in_len);
  OPENSSL_free(in);
  return oct;
}

X509_SIG *PKCS8_encrypt(int pbe_nid, const EVP_CIPHER *cipher, const char *pass,
                        int pass_len, uint8_t *salt, size_t salt_len,
                        int iterations, PKCS8_PRIV_KEY_INFO *p8inf) {
  uint8_t *pass_raw = NULL;
  size_t pass_raw_len = 0;
  X509_SIG *ret;

  if (pass) {
    if (pass_len == -1) {
      pass_len = strlen(pass);
    }
    if (!ascii_to_ucs2(pass, pass_len, &pass_raw, &pass_raw_len)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_DECODE_ERROR);
      return NULL;
    }
  }

  ret = PKCS8_encrypt_pbe(pbe_nid, pass_raw, pass_raw_len,
                          salt, salt_len, iterations, p8inf);

  if (pass_raw) {
    OPENSSL_cleanse(pass_raw, pass_raw_len);
    OPENSSL_free(pass_raw);
  }
  return ret;
}

X509_SIG *PKCS8_encrypt_pbe(int pbe_nid,
                            const uint8_t *pass_raw, size_t pass_raw_len,
                            uint8_t *salt, size_t salt_len,
                            int iterations, PKCS8_PRIV_KEY_INFO *p8inf) {
  X509_SIG *pkcs8 = NULL;
  X509_ALGOR *pbe;

  pkcs8 = X509_SIG_new();
  if (pkcs8 == NULL) {
    OPENSSL_PUT_ERROR(PKCS8, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  pbe = PKCS5_pbe_set(pbe_nid, iterations, salt, salt_len);
  if (!pbe) {
    OPENSSL_PUT_ERROR(PKCS8, ERR_R_ASN1_LIB);
    goto err;
  }

  X509_ALGOR_free(pkcs8->algor);
  pkcs8->algor = pbe;
  M_ASN1_OCTET_STRING_free(pkcs8->digest);
  pkcs8->digest = pkcs12_item_i2d_encrypt(
      pbe, ASN1_ITEM_rptr(PKCS8_PRIV_KEY_INFO), pass_raw, pass_raw_len, p8inf);
  if (!pkcs8->digest) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_ENCRYPT_ERROR);
    goto err;
  }

  return pkcs8;

err:
  X509_SIG_free(pkcs8);
  return NULL;
}

EVP_PKEY *EVP_PKCS82PKEY(PKCS8_PRIV_KEY_INFO *p8) {
  EVP_PKEY *pkey = NULL;
  ASN1_OBJECT *algoid;
  char obj_tmp[80];

  if (!PKCS8_pkey_get0(&algoid, NULL, NULL, NULL, p8)) {
    return NULL;
  }

  pkey = EVP_PKEY_new();
  if (pkey == NULL) {
    OPENSSL_PUT_ERROR(PKCS8, ERR_R_MALLOC_FAILURE);
    return NULL;
  }

  if (!EVP_PKEY_set_type(pkey, OBJ_obj2nid(algoid))) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_UNSUPPORTED_PRIVATE_KEY_ALGORITHM);
    i2t_ASN1_OBJECT(obj_tmp, 80, algoid);
    ERR_add_error_data(2, "TYPE=", obj_tmp);
    goto error;
  }

  if (pkey->ameth->priv_decode) {
    if (!pkey->ameth->priv_decode(pkey, p8)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_PRIVATE_KEY_DECODE_ERROR);
      goto error;
    }
  } else {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_METHOD_NOT_SUPPORTED);
    goto error;
  }

  return pkey;

error:
  EVP_PKEY_free(pkey);
  return NULL;
}

PKCS8_PRIV_KEY_INFO *EVP_PKEY2PKCS8(EVP_PKEY *pkey) {
  PKCS8_PRIV_KEY_INFO *p8;

  p8 = PKCS8_PRIV_KEY_INFO_new();
  if (p8 == NULL) {
    OPENSSL_PUT_ERROR(PKCS8, ERR_R_MALLOC_FAILURE);
    return NULL;
  }
  p8->broken = PKCS8_OK;

  if (pkey->ameth) {
    if (pkey->ameth->priv_encode) {
      if (!pkey->ameth->priv_encode(p8, pkey)) {
        OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_PRIVATE_KEY_ENCODE_ERROR);
        goto error;
      }
    } else {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_METHOD_NOT_SUPPORTED);
      goto error;
    }
  } else {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_UNSUPPORTED_PRIVATE_KEY_ALGORITHM);
    goto error;
  }
  return p8;

error:
  PKCS8_PRIV_KEY_INFO_free(p8);
  return NULL;
}

struct pkcs12_context {
  EVP_PKEY **out_key;
  STACK_OF(X509) *out_certs;
  uint8_t *password;
  size_t password_len;
};

static int PKCS12_handle_content_info(CBS *content_info, unsigned depth,
                                      struct pkcs12_context *ctx);

/* PKCS12_handle_content_infos parses a series of PKCS#7 ContentInfos in a
 * SEQUENCE. */
static int PKCS12_handle_content_infos(CBS *content_infos,
                                       unsigned depth,
                                       struct pkcs12_context *ctx) {
  uint8_t *der_bytes = NULL;
  size_t der_len;
  CBS in;
  int ret = 0;

  /* Generally we only expect depths 0 (the top level, with a
   * pkcs7-encryptedData and a pkcs7-data) and depth 1 (the various PKCS#12
   * bags). */
  if (depth > 3) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_PKCS12_TOO_DEEPLY_NESTED);
    return 0;
  }

  /* Although a BER->DER conversion is done at the beginning of |PKCS12_parse|,
   * the ASN.1 data gets wrapped in OCTETSTRINGs and/or encrypted and the
   * conversion cannot see through those wrappings. So each time we step
   * through one we need to convert to DER again. */
  if (!CBS_asn1_ber_to_der(content_infos, &der_bytes, &der_len)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    return 0;
  }

  if (der_bytes != NULL) {
    CBS_init(&in, der_bytes, der_len);
  } else {
    CBS_init(&in, CBS_data(content_infos), CBS_len(content_infos));
  }

  if (!CBS_get_asn1(&in, &in, CBS_ASN1_SEQUENCE)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    goto err;
  }

  while (CBS_len(&in) > 0) {
    CBS content_info;
    if (!CBS_get_asn1(&in, &content_info, CBS_ASN1_SEQUENCE)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      goto err;
    }

    if (!PKCS12_handle_content_info(&content_info, depth + 1, ctx)) {
      goto err;
    }
  }

  /* NSS includes additional data after the SEQUENCE, but it's an (unwrapped)
   * copy of the same encrypted private key (with the same IV and
   * ciphertext)! */

  ret = 1;

err:
  OPENSSL_free(der_bytes);
  return ret;
}

/* PKCS12_handle_content_info parses a single PKCS#7 ContentInfo element in a
 * PKCS#12 structure. */
static int PKCS12_handle_content_info(CBS *content_info, unsigned depth,
                                      struct pkcs12_context *ctx) {
  CBS content_type, wrapped_contents, contents, content_infos;
  int nid, ret = 0;

  if (!CBS_get_asn1(content_info, &content_type, CBS_ASN1_OBJECT) ||
      !CBS_get_asn1(content_info, &wrapped_contents,
                        CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    goto err;
  }

  nid = OBJ_cbs2nid(&content_type);
  if (nid == NID_pkcs7_encrypted) {
    /* See https://tools.ietf.org/html/rfc2315#section-13.
     *
     * PKCS#7 encrypted data inside a PKCS#12 structure is generally an
     * encrypted certificate bag and it's generally encrypted with 40-bit
     * RC2-CBC. */
    CBS version_bytes, eci, contents_type, ai, encrypted_contents;
    X509_ALGOR *algor = NULL;
    const uint8_t *inp;
    uint8_t *out;
    size_t out_len;

    if (!CBS_get_asn1(&wrapped_contents, &contents, CBS_ASN1_SEQUENCE) ||
        !CBS_get_asn1(&contents, &version_bytes, CBS_ASN1_INTEGER) ||
        /* EncryptedContentInfo, see
         * https://tools.ietf.org/html/rfc2315#section-10.1 */
        !CBS_get_asn1(&contents, &eci, CBS_ASN1_SEQUENCE) ||
        !CBS_get_asn1(&eci, &contents_type, CBS_ASN1_OBJECT) ||
        /* AlgorithmIdentifier, see
         * https://tools.ietf.org/html/rfc5280#section-4.1.1.2 */
        !CBS_get_asn1_element(&eci, &ai, CBS_ASN1_SEQUENCE) ||
        !CBS_get_asn1(&eci, &encrypted_contents,
                      CBS_ASN1_CONTEXT_SPECIFIC | 0)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      goto err;
    }

    if (OBJ_cbs2nid(&contents_type) != NID_pkcs7_data) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      goto err;
    }

    inp = CBS_data(&ai);
    algor = d2i_X509_ALGOR(NULL, &inp, CBS_len(&ai));
    if (algor == NULL) {
      goto err;
    }
    if (inp != CBS_data(&ai) + CBS_len(&ai)) {
      X509_ALGOR_free(algor);
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      goto err;
    }

    if (!pbe_crypt(algor, ctx->password, ctx->password_len,
                   CBS_data(&encrypted_contents), CBS_len(&encrypted_contents),
                   &out, &out_len, 0 /* decrypt */)) {
      X509_ALGOR_free(algor);
      goto err;
    }
    X509_ALGOR_free(algor);

    CBS_init(&content_infos, out, out_len);
    ret = PKCS12_handle_content_infos(&content_infos, depth + 1, ctx);
    OPENSSL_free(out);
  } else if (nid == NID_pkcs7_data) {
    CBS octet_string_contents;

    if (!CBS_get_asn1(&wrapped_contents, &octet_string_contents,
                          CBS_ASN1_OCTETSTRING)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      goto err;
    }

    ret = PKCS12_handle_content_infos(&octet_string_contents, depth + 1, ctx);
  } else if (nid == NID_pkcs8ShroudedKeyBag) {
    /* See ftp://ftp.rsasecurity.com/pub/pkcs/pkcs-12/pkcs-12v1.pdf, section
     * 4.2.2. */
    const uint8_t *inp = CBS_data(&wrapped_contents);
    PKCS8_PRIV_KEY_INFO *pki = NULL;
    X509_SIG *encrypted = NULL;

    if (*ctx->out_key) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_MULTIPLE_PRIVATE_KEYS_IN_PKCS12);
      goto err;
    }

    /* encrypted isn't actually an X.509 signature, but it has the same
     * structure as one and so |X509_SIG| is reused to store it. */
    encrypted = d2i_X509_SIG(NULL, &inp, CBS_len(&wrapped_contents));
    if (encrypted == NULL) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      goto err;
    }
    if (inp != CBS_data(&wrapped_contents) + CBS_len(&wrapped_contents)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      X509_SIG_free(encrypted);
      goto err;
    }

    pki = PKCS8_decrypt_pbe(encrypted, ctx->password, ctx->password_len);
    X509_SIG_free(encrypted);
    if (pki == NULL) {
      goto err;
    }

    *ctx->out_key = EVP_PKCS82PKEY(pki);
    PKCS8_PRIV_KEY_INFO_free(pki);

    if (ctx->out_key == NULL) {
      goto err;
    }
    ret = 1;
  } else if (nid == NID_certBag) {
    CBS cert_bag, cert_type, wrapped_cert, cert;

    if (!CBS_get_asn1(&wrapped_contents, &cert_bag, CBS_ASN1_SEQUENCE) ||
        !CBS_get_asn1(&cert_bag, &cert_type, CBS_ASN1_OBJECT) ||
        !CBS_get_asn1(&cert_bag, &wrapped_cert,
                      CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0) ||
        !CBS_get_asn1(&wrapped_cert, &cert, CBS_ASN1_OCTETSTRING)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      goto err;
    }

    if (OBJ_cbs2nid(&cert_type) == NID_x509Certificate) {
      const uint8_t *inp = CBS_data(&cert);
      X509 *x509 = d2i_X509(NULL, &inp, CBS_len(&cert));
      if (!x509) {
        OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
        goto err;
      }
      if (inp != CBS_data(&cert) + CBS_len(&cert)) {
        OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
        X509_free(x509);
        goto err;
      }

      if (0 == sk_X509_push(ctx->out_certs, x509)) {
        X509_free(x509);
        goto err;
      }
    }
    ret = 1;
  } else {
    /* Unknown element type - ignore it. */
    ret = 1;
  }

err:
  return ret;
}

int PKCS12_get_key_and_certs(EVP_PKEY **out_key, STACK_OF(X509) *out_certs,
                             CBS *ber_in, const char *password) {
  uint8_t *der_bytes = NULL;
  size_t der_len;
  CBS in, pfx, mac_data, authsafe, content_type, wrapped_authsafes, authsafes;
  uint64_t version;
  int ret = 0;
  struct pkcs12_context ctx;
  const size_t original_out_certs_len = sk_X509_num(out_certs);

  /* The input may be in BER format. */
  if (!CBS_asn1_ber_to_der(ber_in, &der_bytes, &der_len)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    return 0;
  }
  if (der_bytes != NULL) {
    CBS_init(&in, der_bytes, der_len);
  } else {
    CBS_init(&in, CBS_data(ber_in), CBS_len(ber_in));
  }

  *out_key = NULL;
  memset(&ctx, 0, sizeof(ctx));

  /* See ftp://ftp.rsasecurity.com/pub/pkcs/pkcs-12/pkcs-12v1.pdf, section
   * four. */
  if (!CBS_get_asn1(&in, &pfx, CBS_ASN1_SEQUENCE) ||
      CBS_len(&in) != 0 ||
      !CBS_get_asn1_uint64(&pfx, &version)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    goto err;
  }

  if (version < 3) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_VERSION);
    goto err;
  }

  if (!CBS_get_asn1(&pfx, &authsafe, CBS_ASN1_SEQUENCE)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    goto err;
  }

  if (CBS_len(&pfx) == 0) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_MISSING_MAC);
    goto err;
  }

  if (!CBS_get_asn1(&pfx, &mac_data, CBS_ASN1_SEQUENCE)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    goto err;
  }

  /* authsafe is a PKCS#7 ContentInfo. See
   * https://tools.ietf.org/html/rfc2315#section-7. */
  if (!CBS_get_asn1(&authsafe, &content_type, CBS_ASN1_OBJECT) ||
      !CBS_get_asn1(&authsafe, &wrapped_authsafes,
                        CBS_ASN1_CONTEXT_SPECIFIC | CBS_ASN1_CONSTRUCTED | 0)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    goto err;
  }

  /* The content type can either be |NID_pkcs7_data| or |NID_pkcs7_signed|. The
   * latter indicates that it's signed by a public key, which isn't
   * supported. */
  if (OBJ_cbs2nid(&content_type) != NID_pkcs7_data) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_PKCS12_PUBLIC_KEY_INTEGRITY_NOT_SUPPORTED);
    goto err;
  }

  if (!CBS_get_asn1(&wrapped_authsafes, &authsafes, CBS_ASN1_OCTETSTRING)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
    goto err;
  }

  ctx.out_key = out_key;
  ctx.out_certs = out_certs;
  if (!ascii_to_ucs2(password, strlen(password), &ctx.password,
                     &ctx.password_len)) {
    OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_DECODE_ERROR);
    goto err;
  }

  /* Verify the MAC. */
  {
    CBS mac, hash_type_seq, hash_oid, salt, expected_mac;
    uint64_t iterations;
    int hash_nid;
    const EVP_MD *md;
    uint8_t hmac_key[EVP_MAX_MD_SIZE];
    uint8_t hmac[EVP_MAX_MD_SIZE];
    unsigned hmac_len;

    if (!CBS_get_asn1(&mac_data, &mac, CBS_ASN1_SEQUENCE) ||
        !CBS_get_asn1(&mac, &hash_type_seq, CBS_ASN1_SEQUENCE) ||
        !CBS_get_asn1(&hash_type_seq, &hash_oid, CBS_ASN1_OBJECT) ||
        !CBS_get_asn1(&mac, &expected_mac, CBS_ASN1_OCTETSTRING) ||
        !CBS_get_asn1(&mac_data, &salt, CBS_ASN1_OCTETSTRING)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
      goto err;
    }

    /* The iteration count is optional and the default is one. */
    iterations = 1;
    if (CBS_len(&mac_data) > 0) {
      if (!CBS_get_asn1_uint64(&mac_data, &iterations) ||
          iterations > INT_MAX) {
        OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_BAD_PKCS12_DATA);
        goto err;
      }
    }

    hash_nid = OBJ_cbs2nid(&hash_oid);
    if (hash_nid == NID_undef ||
        (md = EVP_get_digestbynid(hash_nid)) == NULL) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_UNKNOWN_HASH);
      goto err;
    }

    if (!pkcs12_key_gen_raw(ctx.password, ctx.password_len, CBS_data(&salt),
                            CBS_len(&salt), PKCS12_MAC_ID, iterations,
                            EVP_MD_size(md), hmac_key, md)) {
      goto err;
    }

    if (NULL == HMAC(md, hmac_key, EVP_MD_size(md), CBS_data(&authsafes),
                     CBS_len(&authsafes), hmac, &hmac_len)) {
      goto err;
    }

    if (!CBS_mem_equal(&expected_mac, hmac, hmac_len)) {
      OPENSSL_PUT_ERROR(PKCS8, PKCS8_R_INCORRECT_PASSWORD);
      goto err;
    }
  }

  /* authsafes contains a series of PKCS#7 ContentInfos. */
  if (!PKCS12_handle_content_infos(&authsafes, 0, &ctx)) {
    goto err;
  }

  ret = 1;

err:
  OPENSSL_free(ctx.password);
  OPENSSL_free(der_bytes);
  if (!ret) {
    EVP_PKEY_free(*out_key);
    *out_key = NULL;
    while (sk_X509_num(out_certs) > original_out_certs_len) {
      X509 *x509 = sk_X509_pop(out_certs);
      X509_free(x509);
    }
  }

  return ret;
}

void PKCS12_PBE_add(void) {}

struct pkcs12_st {
  uint8_t *ber_bytes;
  size_t ber_len;
};

PKCS12* d2i_PKCS12(PKCS12 **out_p12, const uint8_t **ber_bytes, size_t ber_len) {
  PKCS12 *p12;

  /* out_p12 must be NULL because we don't export the PKCS12 structure. */
  assert(out_p12 == NULL);

  p12 = OPENSSL_malloc(sizeof(PKCS12));
  if (!p12) {
    return NULL;
  }

  p12->ber_bytes = OPENSSL_malloc(ber_len);
  if (!p12->ber_bytes) {
    OPENSSL_free(p12);
    return NULL;
  }

  memcpy(p12->ber_bytes, *ber_bytes, ber_len);
  p12->ber_len = ber_len;
  *ber_bytes += ber_len;

  return p12;
}

PKCS12* d2i_PKCS12_bio(BIO *bio, PKCS12 **out_p12) {
  size_t used = 0;
  BUF_MEM *buf;
  const uint8_t *dummy;
  static const size_t kMaxSize = 256 * 1024;
  PKCS12 *ret = NULL;

  buf = BUF_MEM_new();
  if (buf == NULL) {
    return NULL;
  }
  if (BUF_MEM_grow(buf, 8192) == 0) {
    goto out;
  }

  for (;;) {
    int n = BIO_read(bio, &buf->data[used], buf->length - used);
    if (n < 0) {
      goto out;
    }

    if (n == 0) {
      break;
    }
    used += n;

    if (used < buf->length) {
      continue;
    }

    if (buf->length > kMaxSize ||
        BUF_MEM_grow(buf, buf->length * 2) == 0) {
      goto out;
    }
  }

  dummy = (uint8_t*) buf->data;
  ret = d2i_PKCS12(out_p12, &dummy, used);

out:
  BUF_MEM_free(buf);
  return ret;
}

PKCS12* d2i_PKCS12_fp(FILE *fp, PKCS12 **out_p12) {
  BIO *bio;
  PKCS12 *ret;

  bio = BIO_new_fp(fp, 0 /* don't take ownership */);
  if (!bio) {
    return NULL;
  }

  ret = d2i_PKCS12_bio(bio, out_p12);
  BIO_free(bio);
  return ret;
}

int PKCS12_parse(const PKCS12 *p12, const char *password, EVP_PKEY **out_pkey,
                 X509 **out_cert, STACK_OF(X509) **out_ca_certs) {
  CBS ber_bytes;
  STACK_OF(X509) *ca_certs = NULL;
  char ca_certs_alloced = 0;

  if (out_ca_certs != NULL && *out_ca_certs != NULL) {
    ca_certs = *out_ca_certs;
  }

  if (!ca_certs) {
    ca_certs = sk_X509_new_null();
    if (ca_certs == NULL) {
      OPENSSL_PUT_ERROR(PKCS8, ERR_R_MALLOC_FAILURE);
      return 0;
    }
    ca_certs_alloced = 1;
  }

  CBS_init(&ber_bytes, p12->ber_bytes, p12->ber_len);
  if (!PKCS12_get_key_and_certs(out_pkey, ca_certs, &ber_bytes, password)) {
    if (ca_certs_alloced) {
      sk_X509_free(ca_certs);
    }
    return 0;
  }

  *out_cert = NULL;
  if (sk_X509_num(ca_certs) > 0) {
    *out_cert = sk_X509_shift(ca_certs);
  }

  if (out_ca_certs) {
    *out_ca_certs = ca_certs;
  } else {
    sk_X509_pop_free(ca_certs, X509_free);
  }

  return 1;
}

void PKCS12_free(PKCS12 *p12) {
  OPENSSL_free(p12->ber_bytes);
  OPENSSL_free(p12);
}

/* ====================================================================
 * Copyright (c) 2006 The OpenSSL Project.  All rights reserved.
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

#include <openssl/evp.h>

#include <openssl/bio.h>
#include <openssl/bn.h>
#include <openssl/dsa.h>
#include <openssl/ec.h>
#include <openssl/ec_key.h>
#include <openssl/mem.h>
#include <openssl/rsa.h>

#include "../internal.h"
#include "../fipsmodule/rsa/internal.h"


static int bn_print(BIO *bp, const char *number, const BIGNUM *num,
                    uint8_t *buf, int off) {
  if (num == NULL) {
    return 1;
  }

  if (!BIO_indent(bp, off, 128)) {
    return 0;
  }
  if (BN_is_zero(num)) {
    if (BIO_printf(bp, "%s 0\n", number) <= 0) {
      return 0;
    }
    return 1;
  }

  if (BN_num_bytes(num) <= sizeof(long)) {
    const char *neg = BN_is_negative(num) ? "-" : "";
    if (BIO_printf(bp, "%s %s%lu (%s0x%lx)\n", number, neg,
                   (unsigned long)num->d[0], neg,
                   (unsigned long)num->d[0]) <= 0) {
      return 0;
    }
  } else {
    buf[0] = 0;
    if (BIO_printf(bp, "%s%s", number,
                   (BN_is_negative(num)) ? " (Negative)" : "") <= 0) {
      return 0;
    }
    int n = BN_bn2bin(num, &buf[1]);

    if (buf[1] & 0x80) {
      n++;
    } else {
      buf++;
    }

    int i;
    for (i = 0; i < n; i++) {
      if ((i % 15) == 0) {
        if (BIO_puts(bp, "\n") <= 0 ||
            !BIO_indent(bp, off + 4, 128)) {
          return 0;
        }
      }
      if (BIO_printf(bp, "%02x%s", buf[i], ((i + 1) == n) ? "" : ":") <= 0) {
        return 0;
      }
    }
    if (BIO_write(bp, "\n", 1) <= 0) {
      return 0;
    }
  }
  return 1;
}

static void update_buflen(const BIGNUM *b, size_t *pbuflen) {
  if (!b) {
    return;
  }

  size_t len = BN_num_bytes(b);
  if (*pbuflen < len) {
    *pbuflen = len;
  }
}

// RSA keys.

static int do_rsa_print(BIO *out, const RSA *rsa, int off,
                        int include_private) {
  const char *s, *str;
  uint8_t *m = NULL;
  int ret = 0, mod_len = 0;
  size_t buf_len = 0;

  update_buflen(rsa->n, &buf_len);
  update_buflen(rsa->e, &buf_len);

  if (include_private) {
    update_buflen(rsa->d, &buf_len);
    update_buflen(rsa->p, &buf_len);
    update_buflen(rsa->q, &buf_len);
    update_buflen(rsa->dmp1, &buf_len);
    update_buflen(rsa->dmq1, &buf_len);
    update_buflen(rsa->iqmp, &buf_len);
  }

  m = (uint8_t *)OPENSSL_malloc(buf_len + 10);
  if (m == NULL) {
    OPENSSL_PUT_ERROR(EVP, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  if (rsa->n != NULL) {
    mod_len = BN_num_bits(rsa->n);
  }

  if (!BIO_indent(out, off, 128)) {
    goto err;
  }

  if (include_private && rsa->d) {
    if (BIO_printf(out, "Private-Key: (%d bit)\n", mod_len) <= 0) {
      goto err;
    }
    str = "modulus:";
    s = "publicExponent:";
  } else {
    if (BIO_printf(out, "Public-Key: (%d bit)\n", mod_len) <= 0) {
      goto err;
    }
    str = "Modulus:";
    s = "Exponent:";
  }
  if (!bn_print(out, str, rsa->n, m, off) ||
      !bn_print(out, s, rsa->e, m, off)) {
    goto err;
  }

  if (include_private) {
    if (!bn_print(out, "privateExponent:", rsa->d, m, off) ||
        !bn_print(out, "prime1:", rsa->p, m, off) ||
        !bn_print(out, "prime2:", rsa->q, m, off) ||
        !bn_print(out, "exponent1:", rsa->dmp1, m, off) ||
        !bn_print(out, "exponent2:", rsa->dmq1, m, off) ||
        !bn_print(out, "coefficient:", rsa->iqmp, m, off)) {
      goto err;
    }
  }
  ret = 1;

err:
  OPENSSL_free(m);
  return ret;
}

static int rsa_pub_print(BIO *bp, const EVP_PKEY *pkey, int indent,
                         ASN1_PCTX *ctx) {
  return do_rsa_print(bp, pkey->pkey.rsa, indent, 0);
}

static int rsa_priv_print(BIO *bp, const EVP_PKEY *pkey, int indent,
                          ASN1_PCTX *ctx) {
  return do_rsa_print(bp, pkey->pkey.rsa, indent, 1);
}


// DSA keys.

static int do_dsa_print(BIO *bp, const DSA *x, int off, int ptype) {
  uint8_t *m = NULL;
  int ret = 0;
  size_t buf_len = 0;
  const char *ktype = NULL;

  const BIGNUM *priv_key, *pub_key;

  priv_key = NULL;
  if (ptype == 2) {
    priv_key = x->priv_key;
  }

  pub_key = NULL;
  if (ptype > 0) {
    pub_key = x->pub_key;
  }

  ktype = "DSA-Parameters";
  if (ptype == 2) {
    ktype = "Private-Key";
  } else if (ptype == 1) {
    ktype = "Public-Key";
  }

  update_buflen(x->p, &buf_len);
  update_buflen(x->q, &buf_len);
  update_buflen(x->g, &buf_len);
  update_buflen(priv_key, &buf_len);
  update_buflen(pub_key, &buf_len);

  m = (uint8_t *)OPENSSL_malloc(buf_len + 10);
  if (m == NULL) {
    OPENSSL_PUT_ERROR(EVP, ERR_R_MALLOC_FAILURE);
    goto err;
  }

  if (priv_key) {
    if (!BIO_indent(bp, off, 128) ||
        BIO_printf(bp, "%s: (%d bit)\n", ktype, BN_num_bits(x->p)) <= 0) {
      goto err;
    }
  }

  if (!bn_print(bp, "priv:", priv_key, m, off) ||
      !bn_print(bp, "pub: ", pub_key, m, off) ||
      !bn_print(bp, "P:   ", x->p, m, off) ||
      !bn_print(bp, "Q:   ", x->q, m, off) ||
      !bn_print(bp, "G:   ", x->g, m, off)) {
    goto err;
  }
  ret = 1;

err:
  OPENSSL_free(m);
  return ret;
}

static int dsa_param_print(BIO *bp, const EVP_PKEY *pkey, int indent,
                           ASN1_PCTX *ctx) {
  return do_dsa_print(bp, pkey->pkey.dsa, indent, 0);
}

static int dsa_pub_print(BIO *bp, const EVP_PKEY *pkey, int indent,
                         ASN1_PCTX *ctx) {
  return do_dsa_print(bp, pkey->pkey.dsa, indent, 1);
}

static int dsa_priv_print(BIO *bp, const EVP_PKEY *pkey, int indent,
                          ASN1_PCTX *ctx) {
  return do_dsa_print(bp, pkey->pkey.dsa, indent, 2);
}


// EC keys.

static int do_EC_KEY_print(BIO *bp, const EC_KEY *x, int off, int ktype) {
  uint8_t *buffer = NULL;
  const char *ecstr;
  size_t buf_len = 0, i;
  int ret = 0, reason = ERR_R_BIO_LIB;
  BIGNUM *order = NULL;
  BN_CTX *ctx = NULL;
  const EC_GROUP *group;
  const EC_POINT *public_key;
  const BIGNUM *priv_key;
  uint8_t *pub_key_bytes = NULL;
  size_t pub_key_bytes_len = 0;

  if (x == NULL || (group = EC_KEY_get0_group(x)) == NULL) {
    reason = ERR_R_PASSED_NULL_PARAMETER;
    goto err;
  }

  ctx = BN_CTX_new();
  if (ctx == NULL) {
    reason = ERR_R_MALLOC_FAILURE;
    goto err;
  }

  if (ktype > 0) {
    public_key = EC_KEY_get0_public_key(x);
    if (public_key != NULL) {
      pub_key_bytes_len = EC_POINT_point2oct(
          group, public_key, EC_KEY_get_conv_form(x), NULL, 0, ctx);
      if (pub_key_bytes_len == 0) {
        reason = ERR_R_MALLOC_FAILURE;
        goto err;
      }
      pub_key_bytes = OPENSSL_malloc(pub_key_bytes_len);
      if (pub_key_bytes == NULL) {
        reason = ERR_R_MALLOC_FAILURE;
        goto err;
      }
      pub_key_bytes_len =
          EC_POINT_point2oct(group, public_key, EC_KEY_get_conv_form(x),
                             pub_key_bytes, pub_key_bytes_len, ctx);
      if (pub_key_bytes_len == 0) {
        reason = ERR_R_MALLOC_FAILURE;
        goto err;
      }
      buf_len = pub_key_bytes_len;
    }
  }

  if (ktype == 2) {
    priv_key = EC_KEY_get0_private_key(x);
    if (priv_key && (i = (size_t)BN_num_bytes(priv_key)) > buf_len) {
      buf_len = i;
    }
  } else {
    priv_key = NULL;
  }

  if (ktype > 0) {
    buf_len += 10;
    if ((buffer = OPENSSL_malloc(buf_len)) == NULL) {
      reason = ERR_R_MALLOC_FAILURE;
      goto err;
    }
  }
  if (ktype == 2) {
    ecstr = "Private-Key";
  } else if (ktype == 1) {
    ecstr = "Public-Key";
  } else {
    ecstr = "ECDSA-Parameters";
  }

  if (!BIO_indent(bp, off, 128)) {
    goto err;
  }
  order = BN_new();
  if (order == NULL || !EC_GROUP_get_order(group, order, NULL) ||
      BIO_printf(bp, "%s: (%d bit)\n", ecstr, BN_num_bits(order)) <= 0) {
    goto err;
  }

  if ((priv_key != NULL) &&
      !bn_print(bp, "priv:", priv_key, buffer, off)) {
    goto err;
  }
  if (pub_key_bytes != NULL) {
    BIO_hexdump(bp, pub_key_bytes, pub_key_bytes_len, off);
  }
  // TODO(fork): implement
  /*
  if (!ECPKParameters_print(bp, group, off))
    goto err; */
  ret = 1;

err:
  if (!ret) {
    OPENSSL_PUT_ERROR(EVP, reason);
  }
  OPENSSL_free(pub_key_bytes);
  BN_free(order);
  BN_CTX_free(ctx);
  OPENSSL_free(buffer);
  return ret;
}

static int eckey_param_print(BIO *bp, const EVP_PKEY *pkey, int indent,
                             ASN1_PCTX *ctx) {
  return do_EC_KEY_print(bp, pkey->pkey.ec, indent, 0);
}

static int eckey_pub_print(BIO *bp, const EVP_PKEY *pkey, int indent,
                           ASN1_PCTX *ctx) {
  return do_EC_KEY_print(bp, pkey->pkey.ec, indent, 1);
}


static int eckey_priv_print(BIO *bp, const EVP_PKEY *pkey, int indent,
                            ASN1_PCTX *ctx) {
  return do_EC_KEY_print(bp, pkey->pkey.ec, indent, 2);
}


typedef struct {
  int type;
  int (*pub_print)(BIO *out, const EVP_PKEY *pkey, int indent, ASN1_PCTX *pctx);
  int (*priv_print)(BIO *out, const EVP_PKEY *pkey, int indent,
                    ASN1_PCTX *pctx);
  int (*param_print)(BIO *out, const EVP_PKEY *pkey, int indent,
                     ASN1_PCTX *pctx);
} EVP_PKEY_PRINT_METHOD;

static EVP_PKEY_PRINT_METHOD kPrintMethods[] = {
    {
        EVP_PKEY_RSA,
        rsa_pub_print,
        rsa_priv_print,
        NULL /* param_print */,
    },
    {
        EVP_PKEY_DSA,
        dsa_pub_print,
        dsa_priv_print,
        dsa_param_print,
    },
    {
        EVP_PKEY_EC,
        eckey_pub_print,
        eckey_priv_print,
        eckey_param_print,
    },
};

static size_t kPrintMethodsLen = OPENSSL_ARRAY_SIZE(kPrintMethods);

static EVP_PKEY_PRINT_METHOD *find_method(int type) {
  for (size_t i = 0; i < kPrintMethodsLen; i++) {
    if (kPrintMethods[i].type == type) {
      return &kPrintMethods[i];
    }
  }
  return NULL;
}

static int print_unsupported(BIO *out, const EVP_PKEY *pkey, int indent,
                             const char *kstr) {
  BIO_indent(out, indent, 128);
  BIO_printf(out, "%s algorithm unsupported\n", kstr);
  return 1;
}

int EVP_PKEY_print_public(BIO *out, const EVP_PKEY *pkey, int indent,
                          ASN1_PCTX *pctx) {
  EVP_PKEY_PRINT_METHOD *method = find_method(pkey->type);
  if (method != NULL && method->pub_print != NULL) {
    return method->pub_print(out, pkey, indent, pctx);
  }
  return print_unsupported(out, pkey, indent, "Public Key");
}

int EVP_PKEY_print_private(BIO *out, const EVP_PKEY *pkey, int indent,
                           ASN1_PCTX *pctx) {
  EVP_PKEY_PRINT_METHOD *method = find_method(pkey->type);
  if (method != NULL && method->priv_print != NULL) {
    return method->priv_print(out, pkey, indent, pctx);
  }
  return print_unsupported(out, pkey, indent, "Private Key");
}

int EVP_PKEY_print_params(BIO *out, const EVP_PKEY *pkey, int indent,
                          ASN1_PCTX *pctx) {
  EVP_PKEY_PRINT_METHOD *method = find_method(pkey->type);
  if (method != NULL && method->param_print != NULL) {
    return method->param_print(out, pkey, indent, pctx);
  }
  return print_unsupported(out, pkey, indent, "Parameters");
}

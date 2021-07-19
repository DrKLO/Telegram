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

#include <openssl/x509.h>

#include <openssl/bio.h>
#include <openssl/err.h>
#include <openssl/mem.h>


/* |X509_R_UNSUPPORTED_ALGORITHM| is no longer emitted, but continue to define
 * it to avoid downstream churn. */
OPENSSL_DECLARE_ERROR_REASON(X509, UNSUPPORTED_ALGORITHM)

int PKCS8_pkey_set0(PKCS8_PRIV_KEY_INFO *priv, ASN1_OBJECT *aobj, int version,
                    int ptype, void *pval, uint8_t *penc, int penclen) {
  uint8_t **ppenc = NULL;
  if (version >= 0) {
    if (!ASN1_INTEGER_set(priv->version, version)) {
      return 0;
    }
  }

  if (penc) {
    int pmtype;
    ASN1_OCTET_STRING *oct;

    oct = ASN1_OCTET_STRING_new();
    if (!oct) {
      return 0;
    }
    oct->data = penc;
    ppenc = &oct->data;
    oct->length = penclen;
    if (priv->broken == PKCS8_NO_OCTET) {
      pmtype = V_ASN1_SEQUENCE;
    } else {
      pmtype = V_ASN1_OCTET_STRING;
    }
    ASN1_TYPE_set(priv->pkey, pmtype, oct);
  }

  if (!X509_ALGOR_set0(priv->pkeyalg, aobj, ptype, pval)) {
    /* If call fails do not swallow 'enc' */
    if (ppenc) {
      *ppenc = NULL;
    }
    return 0;
  }

  return 1;
}

int PKCS8_pkey_get0(ASN1_OBJECT **ppkalg, const uint8_t **pk, int *ppklen,
                    X509_ALGOR **pa, PKCS8_PRIV_KEY_INFO *p8) {
  if (ppkalg) {
    *ppkalg = p8->pkeyalg->algorithm;
  }

  if (p8->pkey->type == V_ASN1_OCTET_STRING) {
    p8->broken = PKCS8_OK;
    if (pk) {
      *pk = p8->pkey->value.octet_string->data;
      *ppklen = p8->pkey->value.octet_string->length;
    }
  } else if (p8->pkey->type == V_ASN1_SEQUENCE) {
    p8->broken = PKCS8_NO_OCTET;
    if (pk) {
      *pk = p8->pkey->value.sequence->data;
      *ppklen = p8->pkey->value.sequence->length;
    }
  } else {
    return 0;
  }

  if (pa) {
    *pa = p8->pkeyalg;
  }
  return 1;
}

int X509_signature_dump(BIO *bp, const ASN1_STRING *sig, int indent) {
  const uint8_t *s;
  int i, n;

  n = sig->length;
  s = sig->data;
  for (i = 0; i < n; i++) {
    if ((i % 18) == 0) {
      if (BIO_write(bp, "\n", 1) <= 0 ||
          BIO_indent(bp, indent, indent) <= 0) {
        return 0;
      }
    }
    if (BIO_printf(bp, "%02x%s", s[i], ((i + 1) == n) ? "" : ":") <= 0) {
      return 0;
    }
  }
  if (BIO_write(bp, "\n", 1) != 1) {
    return 0;
  }

  return 1;
}

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

#include <stdio.h>

#include <openssl/bn.h>
#include <openssl/buffer.h>
#include <openssl/err.h>
#include <openssl/objects.h>
#include <openssl/x509.h>
#include <openssl/x509v3.h>


int X509_REQ_print_fp(FILE *fp, X509_REQ *x) {
  BIO *bio = BIO_new(BIO_s_file());
  if (bio == NULL) {
    OPENSSL_PUT_ERROR(X509, ERR_R_BUF_LIB);
    return 0;
  }

  BIO_set_fp(bio, fp, BIO_NOCLOSE);
  int ret = X509_REQ_print(bio, x);
  BIO_free(bio);
  return ret;
}

int X509_REQ_print_ex(BIO *bio, X509_REQ *x, unsigned long nmflags,
                      unsigned long cflag) {
  long l;
  EVP_PKEY *pkey;
  STACK_OF(X509_ATTRIBUTE) * sk;
  char mlch = ' ';

  int nmindent = 0;

  if ((nmflags & XN_FLAG_SEP_MASK) == XN_FLAG_SEP_MULTILINE) {
    mlch = '\n';
    nmindent = 12;
  }

  if (nmflags == X509_FLAG_COMPAT) {
    nmindent = 16;
  }

  X509_REQ_INFO *ri = x->req_info;
  if (!(cflag & X509_FLAG_NO_HEADER)) {
    if (BIO_write(bio, "Certificate Request:\n", 21) <= 0 ||
        BIO_write(bio, "    Data:\n", 10) <= 0) {
      goto err;
    }
  }
  if (!(cflag & X509_FLAG_NO_VERSION)) {
    l = X509_REQ_get_version(x);
    if (BIO_printf(bio, "%8sVersion: %ld (0x%lx)\n", "", l + 1, l) <= 0) {
      goto err;
    }
  }
  if (!(cflag & X509_FLAG_NO_SUBJECT)) {
    if (BIO_printf(bio, "        Subject:%c", mlch) <= 0 ||
        X509_NAME_print_ex(bio, ri->subject, nmindent, nmflags) < 0 ||
        BIO_write(bio, "\n", 1) <= 0) {
      goto err;
    }
  }
  if (!(cflag & X509_FLAG_NO_PUBKEY)) {
    if (BIO_write(bio, "        Subject Public Key Info:\n", 33) <= 0 ||
        BIO_printf(bio, "%12sPublic Key Algorithm: ", "") <= 0 ||
        i2a_ASN1_OBJECT(bio, ri->pubkey->algor->algorithm) <= 0 ||
        BIO_puts(bio, "\n") <= 0) {
      goto err;
    }

    pkey = X509_REQ_get_pubkey(x);
    if (pkey == NULL) {
      BIO_printf(bio, "%12sUnable to load Public Key\n", "");
      ERR_print_errors(bio);
    } else {
      EVP_PKEY_print_public(bio, pkey, 16, NULL);
      EVP_PKEY_free(pkey);
    }
  }

  if (!(cflag & X509_FLAG_NO_ATTRIBUTES)) {
    if (BIO_printf(bio, "%8sAttributes:\n", "") <= 0) {
      goto err;
    }

    sk = x->req_info->attributes;
    if (sk_X509_ATTRIBUTE_num(sk) == 0) {
      if (BIO_printf(bio, "%12sa0:00\n", "") <= 0) {
        goto err;
      }
    } else {
      size_t i;
      for (i = 0; i < sk_X509_ATTRIBUTE_num(sk); i++) {
        X509_ATTRIBUTE *a = sk_X509_ATTRIBUTE_value(sk, i);
        ASN1_OBJECT *aobj = X509_ATTRIBUTE_get0_object(a);

        if (X509_REQ_extension_nid(OBJ_obj2nid(aobj))) {
          continue;
        }

        if (BIO_printf(bio, "%12s", "") <= 0) {
          goto err;
        }

        const int num_attrs = X509_ATTRIBUTE_count(a);
        const int obj_str_len = i2a_ASN1_OBJECT(bio, aobj);
        if (obj_str_len <= 0) {
          if (BIO_puts(bio, "(Unable to print attribute ID.)\n") < 0) {
            goto err;
          } else {
            continue;
          }
        }

        int j;
        for (j = 0; j < num_attrs; j++) {
          const ASN1_TYPE *at = X509_ATTRIBUTE_get0_type(a, j);
          const int type = at->type;
          ASN1_BIT_STRING *bs = at->value.asn1_string;

          int k;
          for (k = 25 - obj_str_len; k > 0; k--) {
            if (BIO_write(bio, " ", 1) != 1) {
              goto err;
            }
          }

          if (BIO_puts(bio, ":") <= 0) {
            goto err;
          }

          if (type == V_ASN1_PRINTABLESTRING ||
              type == V_ASN1_UTF8STRING ||
              type == V_ASN1_IA5STRING ||
              type == V_ASN1_T61STRING) {
            if (BIO_write(bio, (char *)bs->data, bs->length) != bs->length) {
              goto err;
            }
            BIO_puts(bio, "\n");
          } else {
            BIO_puts(bio, "unable to print attribute\n");
          }
        }
      }
    }
  }

  if (!(cflag & X509_FLAG_NO_EXTENSIONS)) {
    STACK_OF(X509_EXTENSION) *exts = X509_REQ_get_extensions(x);
    if (exts) {
      BIO_printf(bio, "%8sRequested Extensions:\n", "");

      size_t i;
      for (i = 0; i < sk_X509_EXTENSION_num(exts); i++) {
        X509_EXTENSION *ex = sk_X509_EXTENSION_value(exts, i);
        if (BIO_printf(bio, "%12s", "") <= 0) {
          goto err;
        }
        ASN1_OBJECT *obj = X509_EXTENSION_get_object(ex);
        i2a_ASN1_OBJECT(bio, obj);
        const int is_critical = X509_EXTENSION_get_critical(ex);
        if (BIO_printf(bio, ": %s\n", is_critical ? "critical" : "") <= 0) {
          goto err;
        }
        if (!X509V3_EXT_print(bio, ex, cflag, 16)) {
          BIO_printf(bio, "%16s", "");
          ASN1_STRING_print(bio, X509_EXTENSION_get_data(ex));
        }
        if (BIO_write(bio, "\n", 1) <= 0) {
          goto err;
        }
      }
      sk_X509_EXTENSION_pop_free(exts, X509_EXTENSION_free);
    }
  }

  if (!(cflag & X509_FLAG_NO_SIGDUMP) &&
      !X509_signature_print(bio, x->sig_alg, x->signature)) {
    goto err;
  }

  return 1;

err:
  OPENSSL_PUT_ERROR(X509, ERR_R_BUF_LIB);
  return 0;
}

int X509_REQ_print(BIO *bio, X509_REQ *req) {
  return X509_REQ_print_ex(bio, req, XN_FLAG_COMPAT, X509_FLAG_COMPAT);
}

/* crypto/asn1/x_x509.c */
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

#include <assert.h>
#include <limits.h>
#include <stdio.h>

#include <openssl/asn1t.h>
#include <openssl/evp.h>
#include <openssl/mem.h>
#include <openssl/obj.h>
#include <openssl/pool.h>
#include <openssl/thread.h>
#include <openssl/x509.h>
#include <openssl/x509v3.h>

#include "../internal.h"

static CRYPTO_EX_DATA_CLASS g_ex_data_class = CRYPTO_EX_DATA_CLASS_INIT;

ASN1_SEQUENCE_enc(X509_CINF, enc, 0) = {
        ASN1_EXP_OPT(X509_CINF, version, ASN1_INTEGER, 0),
        ASN1_SIMPLE(X509_CINF, serialNumber, ASN1_INTEGER),
        ASN1_SIMPLE(X509_CINF, signature, X509_ALGOR),
        ASN1_SIMPLE(X509_CINF, issuer, X509_NAME),
        ASN1_SIMPLE(X509_CINF, validity, X509_VAL),
        ASN1_SIMPLE(X509_CINF, subject, X509_NAME),
        ASN1_SIMPLE(X509_CINF, key, X509_PUBKEY),
        ASN1_IMP_OPT(X509_CINF, issuerUID, ASN1_BIT_STRING, 1),
        ASN1_IMP_OPT(X509_CINF, subjectUID, ASN1_BIT_STRING, 2),
        ASN1_EXP_SEQUENCE_OF_OPT(X509_CINF, extensions, X509_EXTENSION, 3)
} ASN1_SEQUENCE_END_enc(X509_CINF, X509_CINF)

IMPLEMENT_ASN1_FUNCTIONS(X509_CINF)
/* X509 top level structure needs a bit of customisation */

extern void policy_cache_free(X509_POLICY_CACHE *cache);

static int x509_cb(int operation, ASN1_VALUE **pval, const ASN1_ITEM *it,
                   void *exarg)
{
    X509 *ret = (X509 *)*pval;

    switch (operation) {

    case ASN1_OP_NEW_POST:
        ret->name = NULL;
        ret->ex_flags = 0;
        ret->ex_pathlen = -1;
        ret->skid = NULL;
        ret->akid = NULL;
        ret->aux = NULL;
        ret->crldp = NULL;
        ret->buf = NULL;
        CRYPTO_new_ex_data(&ret->ex_data);
        CRYPTO_MUTEX_init(&ret->lock);
        break;

    case ASN1_OP_D2I_PRE:
        CRYPTO_BUFFER_free(ret->buf);
        ret->buf = NULL;
        break;

    case ASN1_OP_D2I_POST:
        if (ret->name != NULL)
            OPENSSL_free(ret->name);
        ret->name = X509_NAME_oneline(ret->cert_info->subject, NULL, 0);
        break;

    case ASN1_OP_FREE_POST:
        CRYPTO_MUTEX_cleanup(&ret->lock);
        CRYPTO_free_ex_data(&g_ex_data_class, ret, &ret->ex_data);
        X509_CERT_AUX_free(ret->aux);
        ASN1_OCTET_STRING_free(ret->skid);
        AUTHORITY_KEYID_free(ret->akid);
        CRL_DIST_POINTS_free(ret->crldp);
        policy_cache_free(ret->policy_cache);
        GENERAL_NAMES_free(ret->altname);
        NAME_CONSTRAINTS_free(ret->nc);
        CRYPTO_BUFFER_free(ret->buf);
        OPENSSL_free(ret->name);
        break;

    }

    return 1;

}

ASN1_SEQUENCE_ref(X509, x509_cb) = {
        ASN1_SIMPLE(X509, cert_info, X509_CINF),
        ASN1_SIMPLE(X509, sig_alg, X509_ALGOR),
        ASN1_SIMPLE(X509, signature, ASN1_BIT_STRING)
} ASN1_SEQUENCE_END_ref(X509, X509)

IMPLEMENT_ASN1_FUNCTIONS(X509)

IMPLEMENT_ASN1_DUP_FUNCTION(X509)

X509 *X509_parse_from_buffer(CRYPTO_BUFFER *buf) {
  if (CRYPTO_BUFFER_len(buf) > LONG_MAX) {
    OPENSSL_PUT_ERROR(SSL, ERR_R_OVERFLOW);
    return 0;
  }

  X509 *x509 = X509_new();
  if (x509 == NULL) {
    return NULL;
  }

  x509->cert_info->enc.alias_only_on_next_parse = 1;

  const uint8_t *inp = CRYPTO_BUFFER_data(buf);
  X509 *x509p = x509;
  X509 *ret = d2i_X509(&x509p, &inp, CRYPTO_BUFFER_len(buf));
  if (ret == NULL ||
      inp - CRYPTO_BUFFER_data(buf) != (ptrdiff_t)CRYPTO_BUFFER_len(buf)) {
    X509_free(x509p);
    return NULL;
  }
  assert(x509p == x509);
  assert(ret == x509);

  CRYPTO_BUFFER_up_ref(buf);
  ret->buf = buf;

  return ret;
}

int X509_up_ref(X509 *x)
{
    CRYPTO_refcount_inc(&x->references);
    return 1;
}

int X509_get_ex_new_index(long argl, void *argp, CRYPTO_EX_unused * unused,
                          CRYPTO_EX_dup *dup_unused, CRYPTO_EX_free *free_func)
{
    int index;
    if (!CRYPTO_get_ex_new_index(&g_ex_data_class, &index, argl, argp,
                                 free_func)) {
        return -1;
    }
    return index;
}

int X509_set_ex_data(X509 *r, int idx, void *arg)
{
    return (CRYPTO_set_ex_data(&r->ex_data, idx, arg));
}

void *X509_get_ex_data(X509 *r, int idx)
{
    return (CRYPTO_get_ex_data(&r->ex_data, idx));
}

/*
 * X509_AUX ASN1 routines. X509_AUX is the name given to a certificate with
 * extra info tagged on the end. Since these functions set how a certificate
 * is trusted they should only be used when the certificate comes from a
 * reliable source such as local storage.
 */

X509 *d2i_X509_AUX(X509 **a, const unsigned char **pp, long length)
{
    const unsigned char *q = *pp;
    X509 *ret;
    int freeret = 0;

    if (!a || *a == NULL)
        freeret = 1;
    ret = d2i_X509(a, &q, length);
    /* If certificate unreadable then forget it */
    if (!ret)
        return NULL;
    /* update length */
    length -= q - *pp;
    /* Parse auxiliary information if there is any. */
    if (length > 0 && !d2i_X509_CERT_AUX(&ret->aux, &q, length))
        goto err;
    *pp = q;
    return ret;
 err:
    if (freeret) {
        X509_free(ret);
        if (a)
            *a = NULL;
    }
    return NULL;
}

/*
 * Serialize trusted certificate to *pp or just return the required buffer
 * length if pp == NULL.  We ultimately want to avoid modifying *pp in the
 * error path, but that depends on similar hygiene in lower-level functions.
 * Here we avoid compounding the problem.
 */
static int i2d_x509_aux_internal(X509 *a, unsigned char **pp)
{
    int length, tmplen;
    unsigned char *start = pp != NULL ? *pp : NULL;

    assert(pp == NULL || *pp != NULL);

    /*
     * This might perturb *pp on error, but fixing that belongs in i2d_X509()
     * not here.  It should be that if a == NULL length is zero, but we check
     * both just in case.
     */
    length = i2d_X509(a, pp);
    if (length <= 0 || a == NULL) {
        return length;
    }

    tmplen = i2d_X509_CERT_AUX(a->aux, pp);
    if (tmplen < 0) {
        if (start != NULL)
            *pp = start;
        return tmplen;
    }
    length += tmplen;

    return length;
}

/*
 * Serialize trusted certificate to *pp, or just return the required buffer
 * length if pp == NULL.
 *
 * When pp is not NULL, but *pp == NULL, we allocate the buffer, but since
 * we're writing two ASN.1 objects back to back, we can't have i2d_X509() do
 * the allocation, nor can we allow i2d_X509_CERT_AUX() to increment the
 * allocated buffer.
 */
int i2d_X509_AUX(X509 *a, unsigned char **pp)
{
    int length;
    unsigned char *tmp;

    /* Buffer provided by caller */
    if (pp == NULL || *pp != NULL)
        return i2d_x509_aux_internal(a, pp);

    /* Obtain the combined length */
    if ((length = i2d_x509_aux_internal(a, NULL)) <= 0)
        return length;

    /* Allocate requisite combined storage */
    *pp = tmp = OPENSSL_malloc(length);
    if (tmp == NULL)
        return -1; /* Push error onto error stack? */

    /* Encode, but keep *pp at the originally malloced pointer */
    length = i2d_x509_aux_internal(a, &tmp);
    if (length <= 0) {
        OPENSSL_free(*pp);
        *pp = NULL;
    }
    return length;
}

int i2d_re_X509_tbs(X509 *x, unsigned char **pp)
{
    x->cert_info->enc.modified = 1;
    return i2d_X509_CINF(x->cert_info, pp);
}

void X509_get0_signature(const ASN1_BIT_STRING **psig, const X509_ALGOR **palg,
                         const X509 *x)
{
    if (psig)
        *psig = x->signature;
    if (palg)
        *palg = x->sig_alg;
}

int X509_get_signature_nid(const X509 *x)
{
    return OBJ_obj2nid(x->sig_alg->algorithm);
}

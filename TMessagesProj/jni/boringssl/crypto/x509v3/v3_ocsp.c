/*
 * Copyright 2000-2016 The OpenSSL Project Authors. All Rights Reserved.
 *
 * Licensed under the OpenSSL license (the "License").  You may not use
 * this file except in compliance with the License.  You can obtain a copy
 * in the file LICENSE in the source distribution or at
 * https://www.openssl.org/source/license.html
 */

#include <openssl/x509v3.h>

#include <openssl/asn1.h>
#include <openssl/bio.h>
#include <openssl/nid.h>

/*
 * OCSP extensions and a couple of CRL entry extensions
 */

static int i2r_ocsp_acutoff(const X509V3_EXT_METHOD *method, void *nonce,
                            BIO *out, int indent);

static int i2r_ocsp_nocheck(const X509V3_EXT_METHOD *method,
                            void *nocheck, BIO *out, int indent);
static void *s2i_ocsp_nocheck(const X509V3_EXT_METHOD *method,
                              X509V3_CTX *ctx, const char *str);

const X509V3_EXT_METHOD v3_crl_invdate = {
    NID_invalidity_date, 0, ASN1_ITEM_ref(ASN1_GENERALIZEDTIME),
    0, 0, 0, 0,
    0, 0,
    0, 0,
    i2r_ocsp_acutoff, 0,
    NULL
};

const X509V3_EXT_METHOD v3_ocsp_nocheck = {
    NID_id_pkix_OCSP_noCheck, 0, ASN1_ITEM_ref(ASN1_NULL),
    0, 0, 0, 0,
    0, s2i_ocsp_nocheck,
    0, 0,
    i2r_ocsp_nocheck, 0,
    NULL
};

static int i2r_ocsp_acutoff(const X509V3_EXT_METHOD *method, void *cutoff,
                            BIO *bp, int ind)
{
    if (BIO_printf(bp, "%*s", ind, "") <= 0)
        return 0;
    if (!ASN1_GENERALIZEDTIME_print(bp, cutoff))
        return 0;
    return 1;
}

/* Nocheck is just a single NULL. Don't print anything and always set it */

static int i2r_ocsp_nocheck(const X509V3_EXT_METHOD *method, void *nocheck,
                            BIO *out, int indent)
{
    return 1;
}

static void *s2i_ocsp_nocheck(const X509V3_EXT_METHOD *method,
                              X509V3_CTX *ctx, const char *str)
{
    return ASN1_NULL_new();
}

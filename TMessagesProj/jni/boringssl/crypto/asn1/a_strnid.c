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

#include <openssl/asn1.h>

#include <stdlib.h>             /* For bsearch */
#include <string.h>

#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/obj.h>
#include <openssl/stack.h>

DEFINE_STACK_OF(ASN1_STRING_TABLE)

static STACK_OF(ASN1_STRING_TABLE) *stable = NULL;
static void st_free(ASN1_STRING_TABLE *tbl);

/*
 * This is the global mask for the mbstring functions: this is use to mask
 * out certain types (such as BMPString and UTF8String) because certain
 * software (e.g. Netscape) has problems with them.
 */

static unsigned long global_mask = B_ASN1_UTF8STRING;

void ASN1_STRING_set_default_mask(unsigned long mask)
{
    global_mask = mask;
}

unsigned long ASN1_STRING_get_default_mask(void)
{
    return global_mask;
}

/*
 * This function sets the default to various "flavours" of configuration.
 * based on an ASCII string. Currently this is: MASK:XXXX : a numerical mask
 * value. nobmp : Don't use BMPStrings (just Printable, T61). pkix : PKIX
 * recommendation in RFC2459. utf8only : only use UTF8Strings (RFC2459
 * recommendation for 2004). default: the default value, Printable, T61, BMP.
 */

int ASN1_STRING_set_default_mask_asc(const char *p)
{
    unsigned long mask;
    char *end;
    if (!strncmp(p, "MASK:", 5)) {
        if (!p[5])
            return 0;
        mask = strtoul(p + 5, &end, 0);
        if (*end)
            return 0;
    } else if (!strcmp(p, "nombstr"))
        mask = ~((unsigned long)(B_ASN1_BMPSTRING | B_ASN1_UTF8STRING));
    else if (!strcmp(p, "pkix"))
        mask = ~((unsigned long)B_ASN1_T61STRING);
    else if (!strcmp(p, "utf8only"))
        mask = B_ASN1_UTF8STRING;
    else if (!strcmp(p, "default"))
        mask = 0xFFFFFFFFL;
    else
        return 0;
    ASN1_STRING_set_default_mask(mask);
    return 1;
}

/*
 * The following function generates an ASN1_STRING based on limits in a
 * table. Frequently the types and length of an ASN1_STRING are restricted by
 * a corresponding OID. For example certificates and certificate requests.
 */

ASN1_STRING *ASN1_STRING_set_by_NID(ASN1_STRING **out,
                                    const unsigned char *in, int inlen,
                                    int inform, int nid)
{
    ASN1_STRING_TABLE *tbl;
    ASN1_STRING *str = NULL;
    unsigned long mask;
    int ret;
    if (!out)
        out = &str;
    tbl = ASN1_STRING_TABLE_get(nid);
    if (tbl) {
        mask = tbl->mask;
        if (!(tbl->flags & STABLE_NO_MASK))
            mask &= global_mask;
        ret = ASN1_mbstring_ncopy(out, in, inlen, inform, mask,
                                  tbl->minsize, tbl->maxsize);
    } else
        ret =
            ASN1_mbstring_copy(out, in, inlen, inform,
                               DIRSTRING_TYPE & global_mask);
    if (ret <= 0)
        return NULL;
    return *out;
}

/*
 * Now the tables and helper functions for the string table:
 */

/* size limits: this stuff is taken straight from RFC3280 */

#define ub_name                         32768
#define ub_common_name                  64
#define ub_locality_name                128
#define ub_state_name                   128
#define ub_organization_name            64
#define ub_organization_unit_name       64
#define ub_title                        64
#define ub_email_address                128
#define ub_serial_number                64

/* This table must be kept in NID order */

static const ASN1_STRING_TABLE tbl_standard[] = {
    {NID_commonName, 1, ub_common_name, DIRSTRING_TYPE, 0},
    {NID_countryName, 2, 2, B_ASN1_PRINTABLESTRING, STABLE_NO_MASK},
    {NID_localityName, 1, ub_locality_name, DIRSTRING_TYPE, 0},
    {NID_stateOrProvinceName, 1, ub_state_name, DIRSTRING_TYPE, 0},
    {NID_organizationName, 1, ub_organization_name, DIRSTRING_TYPE, 0},
    {NID_organizationalUnitName, 1, ub_organization_unit_name, DIRSTRING_TYPE,
     0},
    {NID_pkcs9_emailAddress, 1, ub_email_address, B_ASN1_IA5STRING,
     STABLE_NO_MASK},
    {NID_pkcs9_unstructuredName, 1, -1, PKCS9STRING_TYPE, 0},
    {NID_pkcs9_challengePassword, 1, -1, PKCS9STRING_TYPE, 0},
    {NID_pkcs9_unstructuredAddress, 1, -1, DIRSTRING_TYPE, 0},
    {NID_givenName, 1, ub_name, DIRSTRING_TYPE, 0},
    {NID_surname, 1, ub_name, DIRSTRING_TYPE, 0},
    {NID_initials, 1, ub_name, DIRSTRING_TYPE, 0},
    {NID_serialNumber, 1, ub_serial_number, B_ASN1_PRINTABLESTRING,
     STABLE_NO_MASK},
    {NID_friendlyName, -1, -1, B_ASN1_BMPSTRING, STABLE_NO_MASK},
    {NID_name, 1, ub_name, DIRSTRING_TYPE, 0},
    {NID_dnQualifier, -1, -1, B_ASN1_PRINTABLESTRING, STABLE_NO_MASK},
    {NID_domainComponent, 1, -1, B_ASN1_IA5STRING, STABLE_NO_MASK},
    {NID_ms_csp_name, -1, -1, B_ASN1_BMPSTRING, STABLE_NO_MASK}
};

static int sk_table_cmp(const ASN1_STRING_TABLE **a,
                        const ASN1_STRING_TABLE **b)
{
    return (*a)->nid - (*b)->nid;
}

static int table_cmp(const void *in_a, const void *in_b)
{
    const ASN1_STRING_TABLE *a = in_a;
    const ASN1_STRING_TABLE *b = in_b;
    return a->nid - b->nid;
}

ASN1_STRING_TABLE *ASN1_STRING_TABLE_get(int nid)
{
    int found;
    size_t idx;
    ASN1_STRING_TABLE *ttmp;
    ASN1_STRING_TABLE fnd;
    fnd.nid = nid;

    ttmp =
        bsearch(&fnd, tbl_standard,
                sizeof(tbl_standard) / sizeof(ASN1_STRING_TABLE),
                sizeof(ASN1_STRING_TABLE), table_cmp);
    if (ttmp)
        return ttmp;
    if (!stable)
        return NULL;
    found = sk_ASN1_STRING_TABLE_find(stable, &idx, &fnd);
    if (!found)
        return NULL;
    return sk_ASN1_STRING_TABLE_value(stable, idx);
}

int ASN1_STRING_TABLE_add(int nid,
                          long minsize, long maxsize, unsigned long mask,
                          unsigned long flags)
{
    ASN1_STRING_TABLE *tmp;
    char new_nid = 0;
    flags &= ~STABLE_FLAGS_MALLOC;
    if (!stable)
        stable = sk_ASN1_STRING_TABLE_new(sk_table_cmp);
    if (!stable) {
        OPENSSL_PUT_ERROR(ASN1, ERR_R_MALLOC_FAILURE);
        return 0;
    }
    if (!(tmp = ASN1_STRING_TABLE_get(nid))) {
        tmp = OPENSSL_malloc(sizeof(ASN1_STRING_TABLE));
        if (!tmp) {
            OPENSSL_PUT_ERROR(ASN1, ERR_R_MALLOC_FAILURE);
            return 0;
        }
        tmp->flags = flags | STABLE_FLAGS_MALLOC;
        tmp->nid = nid;
        tmp->minsize = tmp->maxsize = -1;
        new_nid = 1;
    } else
        tmp->flags = (tmp->flags & STABLE_FLAGS_MALLOC) | flags;
    if (minsize != -1)
        tmp->minsize = minsize;
    if (maxsize != -1)
        tmp->maxsize = maxsize;
    tmp->mask = mask;
    if (new_nid)
        sk_ASN1_STRING_TABLE_push(stable, tmp);
    return 1;
}

void ASN1_STRING_TABLE_cleanup(void)
{
    STACK_OF(ASN1_STRING_TABLE) *tmp;
    tmp = stable;
    if (!tmp)
        return;
    stable = NULL;
    sk_ASN1_STRING_TABLE_pop_free(tmp, st_free);
}

static void st_free(ASN1_STRING_TABLE *tbl)
{
    if (tbl->flags & STABLE_FLAGS_MALLOC)
        OPENSSL_free(tbl);
}

#ifdef STRING_TABLE_TEST

int main(void)
{
    ASN1_STRING_TABLE *tmp;
    int i, last_nid = -1;

    for (tmp = tbl_standard, i = 0;
         i < sizeof(tbl_standard) / sizeof(ASN1_STRING_TABLE); i++, tmp++) {
        if (tmp->nid < last_nid) {
            last_nid = 0;
            break;
        }
        last_nid = tmp->nid;
    }

    if (last_nid != 0) {
        printf("Table order OK\n");
        exit(0);
    }

    for (tmp = tbl_standard, i = 0;
         i < sizeof(tbl_standard) / sizeof(ASN1_STRING_TABLE); i++, tmp++)
        printf("Index %d, NID %d, Name=%s\n", i, tmp->nid,
               OBJ_nid2ln(tmp->nid));

    return 0;
}

#endif

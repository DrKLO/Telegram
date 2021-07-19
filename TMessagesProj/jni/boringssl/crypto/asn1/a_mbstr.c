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

#include <limits.h>
#include <string.h>

#include <openssl/bytestring.h>
#include <openssl/err.h>
#include <openssl/mem.h>

#include "asn1_locl.h"
#include "../bytestring/internal.h"

static int is_printable(uint32_t value);

/*
 * These functions take a string in UTF8, ASCII or multibyte form and a mask
 * of permissible ASN1 string types. It then works out the minimal type
 * (using the order Printable < IA5 < T61 < BMP < Universal < UTF8) and
 * creates a string of the correct type with the supplied data. Yes this is
 * horrible: it has to be :-( The 'ncopy' form checks minimum and maximum
 * size limits too.
 */

int ASN1_mbstring_copy(ASN1_STRING **out, const unsigned char *in, int len,
                       int inform, unsigned long mask)
{
    return ASN1_mbstring_ncopy(out, in, len, inform, mask, 0, 0);
}

OPENSSL_DECLARE_ERROR_REASON(ASN1, INVALID_BMPSTRING)
OPENSSL_DECLARE_ERROR_REASON(ASN1, INVALID_UNIVERSALSTRING)
OPENSSL_DECLARE_ERROR_REASON(ASN1, INVALID_UTF8STRING)

int ASN1_mbstring_ncopy(ASN1_STRING **out, const unsigned char *in, int len,
                        int inform, unsigned long mask,
                        long minsize, long maxsize)
{
    int str_type;
    char free_out;
    ASN1_STRING *dest;
    size_t nchar = 0;
    char strbuf[32];
    if (len == -1)
        len = strlen((const char *)in);
    if (!mask)
        mask = DIRSTRING_TYPE;

    int (*decode_func)(CBS *, uint32_t*);
    int error;
    switch (inform) {
    case MBSTRING_BMP:
        decode_func = cbs_get_ucs2_be;
        error = ASN1_R_INVALID_BMPSTRING;
        break;

    case MBSTRING_UNIV:
        decode_func = cbs_get_utf32_be;
        error = ASN1_R_INVALID_UNIVERSALSTRING;
        break;

    case MBSTRING_UTF8:
        decode_func = cbs_get_utf8;
        error = ASN1_R_INVALID_UTF8STRING;
        break;

    case MBSTRING_ASC:
        decode_func = cbs_get_latin1;
        error = ERR_R_INTERNAL_ERROR;  // Latin-1 inputs are never invalid.
        break;

    default:
        OPENSSL_PUT_ERROR(ASN1, ASN1_R_UNKNOWN_FORMAT);
        return -1;
    }

    /* Check |minsize| and |maxsize| and work out the minimal type, if any. */
    CBS cbs;
    CBS_init(&cbs, in, len);
    size_t utf8_len = 0;
    while (CBS_len(&cbs) != 0) {
        uint32_t c;
        if (!decode_func(&cbs, &c)) {
            OPENSSL_PUT_ERROR(ASN1, error);
            return -1;
        }
        if (nchar == 0 &&
            (inform == MBSTRING_BMP || inform == MBSTRING_UNIV) &&
            c == 0xfeff) {
            /* Reject byte-order mark. We could drop it but that would mean
             * adding ambiguity around whether a BOM was included or not when
             * matching strings.
             *
             * For a little-endian UCS-2 string, the BOM will appear as 0xfffe
             * and will be rejected as noncharacter, below. */
            OPENSSL_PUT_ERROR(ASN1, ASN1_R_ILLEGAL_CHARACTERS);
            return -1;
        }

        /* Update which output formats are still possible. */
        if ((mask & B_ASN1_PRINTABLESTRING) && !is_printable(c)) {
            mask &= ~B_ASN1_PRINTABLESTRING;
        }
        if ((mask & B_ASN1_IA5STRING) && (c > 127)) {
            mask &= ~B_ASN1_IA5STRING;
        }
        if ((mask & B_ASN1_T61STRING) && (c > 0xff)) {
            mask &= ~B_ASN1_T61STRING;
        }
        if ((mask & B_ASN1_BMPSTRING) && (c > 0xffff)) {
            mask &= ~B_ASN1_BMPSTRING;
        }
        if (!mask) {
            OPENSSL_PUT_ERROR(ASN1, ASN1_R_ILLEGAL_CHARACTERS);
            return -1;
        }

        nchar++;
        utf8_len += cbb_get_utf8_len(c);
    }

    if (minsize > 0 && nchar < (size_t)minsize) {
        OPENSSL_PUT_ERROR(ASN1, ASN1_R_STRING_TOO_SHORT);
        BIO_snprintf(strbuf, sizeof strbuf, "%ld", minsize);
        ERR_add_error_data(2, "minsize=", strbuf);
        return -1;
    }

    if (maxsize > 0 && nchar > (size_t)maxsize) {
        OPENSSL_PUT_ERROR(ASN1, ASN1_R_STRING_TOO_LONG);
        BIO_snprintf(strbuf, sizeof strbuf, "%ld", maxsize);
        ERR_add_error_data(2, "maxsize=", strbuf);
        return -1;
    }

    /* Now work out output format and string type */
    int (*encode_func)(CBB *, uint32_t) = cbb_add_latin1;
    size_t size_estimate = nchar;
    int outform = MBSTRING_ASC;
    if (mask & B_ASN1_PRINTABLESTRING) {
        str_type = V_ASN1_PRINTABLESTRING;
    } else if (mask & B_ASN1_IA5STRING) {
        str_type = V_ASN1_IA5STRING;
    } else if (mask & B_ASN1_T61STRING) {
        str_type = V_ASN1_T61STRING;
    } else if (mask & B_ASN1_BMPSTRING) {
        str_type = V_ASN1_BMPSTRING;
        outform = MBSTRING_BMP;
        encode_func = cbb_add_ucs2_be;
        size_estimate = 2 * nchar;
    } else if (mask & B_ASN1_UNIVERSALSTRING) {
        str_type = V_ASN1_UNIVERSALSTRING;
        encode_func = cbb_add_utf32_be;
        size_estimate = 4 * nchar;
        outform = MBSTRING_UNIV;
    } else {
        str_type = V_ASN1_UTF8STRING;
        outform = MBSTRING_UTF8;
        encode_func = cbb_add_utf8;
        size_estimate = utf8_len;
    }

    if (!out)
        return str_type;
    if (*out) {
        free_out = 0;
        dest = *out;
        if (dest->data) {
            dest->length = 0;
            OPENSSL_free(dest->data);
            dest->data = NULL;
        }
        dest->type = str_type;
    } else {
        free_out = 1;
        dest = ASN1_STRING_type_new(str_type);
        if (!dest) {
            OPENSSL_PUT_ERROR(ASN1, ERR_R_MALLOC_FAILURE);
            return -1;
        }
        *out = dest;
    }

    /* If both the same type just copy across */
    if (inform == outform) {
        if (!ASN1_STRING_set(dest, in, len)) {
            OPENSSL_PUT_ERROR(ASN1, ERR_R_MALLOC_FAILURE);
            return -1;
        }
        return str_type;
    }

    CBB cbb;
    if (!CBB_init(&cbb, size_estimate + 1)) {
        OPENSSL_PUT_ERROR(ASN1, ERR_R_MALLOC_FAILURE);
        goto err;
    }
    CBS_init(&cbs, in, len);
    while (CBS_len(&cbs) != 0) {
        uint32_t c;
        if (!decode_func(&cbs, &c) ||
            !encode_func(&cbb, c)) {
            OPENSSL_PUT_ERROR(ASN1, ERR_R_INTERNAL_ERROR);
            goto err;
        }
    }
    uint8_t *data = NULL;
    size_t data_len;
    if (/* OpenSSL historically NUL-terminated this value with a single byte,
         * even for |MBSTRING_BMP| and |MBSTRING_UNIV|. */
        !CBB_add_u8(&cbb, 0) ||
        !CBB_finish(&cbb, &data, &data_len) ||
        data_len < 1 ||
        data_len > INT_MAX) {
        OPENSSL_PUT_ERROR(ASN1, ERR_R_INTERNAL_ERROR);
        OPENSSL_free(data);
        goto err;
    }
    dest->length = (int)(data_len - 1);
    dest->data = data;
    return str_type;

 err:
    if (free_out)
        ASN1_STRING_free(dest);
    CBB_cleanup(&cbb);
    return -1;
}

/* Return 1 if the character is permitted in a PrintableString */
static int is_printable(uint32_t value)
{
    int ch;
    if (value > 0x7f)
        return 0;
    ch = (int)value;
    /*
     * Note: we can't use 'isalnum' because certain accented characters may
     * count as alphanumeric in some environments.
     */
    if ((ch >= 'a') && (ch <= 'z'))
        return 1;
    if ((ch >= 'A') && (ch <= 'Z'))
        return 1;
    if ((ch >= '0') && (ch <= '9'))
        return 1;
    if ((ch == ' ') || strchr("'()+,-./:=?", ch))
        return 1;
    return 0;
}

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

#include <string.h>

#include <openssl/err.h>
#include <openssl/mem.h>

#include "asn1_locl.h"

static int traverse_string(const unsigned char *p, int len, int inform,
                           int (*rfunc) (uint32_t value, void *in),
                           void *arg);
static int in_utf8(uint32_t value, void *arg);
static int out_utf8(uint32_t value, void *arg);
static int type_str(uint32_t value, void *arg);
static int cpy_asc(uint32_t value, void *arg);
static int cpy_bmp(uint32_t value, void *arg);
static int cpy_univ(uint32_t value, void *arg);
static int cpy_utf8(uint32_t value, void *arg);
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

int ASN1_mbstring_ncopy(ASN1_STRING **out, const unsigned char *in, int len,
                        int inform, unsigned long mask,
                        long minsize, long maxsize)
{
    int str_type;
    int ret;
    char free_out;
    int outform, outlen = 0;
    ASN1_STRING *dest;
    unsigned char *p;
    int nchar;
    char strbuf[32];
    int (*cpyfunc) (uint32_t, void *) = NULL;
    if (len == -1)
        len = strlen((const char *)in);
    if (!mask)
        mask = DIRSTRING_TYPE;

    /* First do a string check and work out the number of characters */
    switch (inform) {

    case MBSTRING_BMP:
        if (len & 1) {
            OPENSSL_PUT_ERROR(ASN1, ASN1_R_INVALID_BMPSTRING_LENGTH);
            return -1;
        }
        nchar = len >> 1;
        break;

    case MBSTRING_UNIV:
        if (len & 3) {
            OPENSSL_PUT_ERROR(ASN1, ASN1_R_INVALID_UNIVERSALSTRING_LENGTH);
            return -1;
        }
        nchar = len >> 2;
        break;

    case MBSTRING_UTF8:
        nchar = 0;
        /* This counts the characters and does utf8 syntax checking */
        ret = traverse_string(in, len, MBSTRING_UTF8, in_utf8, &nchar);
        if (ret < 0) {
            OPENSSL_PUT_ERROR(ASN1, ASN1_R_INVALID_UTF8STRING);
            return -1;
        }
        break;

    case MBSTRING_ASC:
        nchar = len;
        break;

    default:
        OPENSSL_PUT_ERROR(ASN1, ASN1_R_UNKNOWN_FORMAT);
        return -1;
    }

    if ((minsize > 0) && (nchar < minsize)) {
        OPENSSL_PUT_ERROR(ASN1, ASN1_R_STRING_TOO_SHORT);
        BIO_snprintf(strbuf, sizeof strbuf, "%ld", minsize);
        ERR_add_error_data(2, "minsize=", strbuf);
        return -1;
    }

    if ((maxsize > 0) && (nchar > maxsize)) {
        OPENSSL_PUT_ERROR(ASN1, ASN1_R_STRING_TOO_LONG);
        BIO_snprintf(strbuf, sizeof strbuf, "%ld", maxsize);
        ERR_add_error_data(2, "maxsize=", strbuf);
        return -1;
    }

    /* Now work out minimal type (if any) */
    if (traverse_string(in, len, inform, type_str, &mask) < 0) {
        OPENSSL_PUT_ERROR(ASN1, ASN1_R_ILLEGAL_CHARACTERS);
        return -1;
    }

    /* Now work out output format and string type */
    outform = MBSTRING_ASC;
    if (mask & B_ASN1_PRINTABLESTRING)
        str_type = V_ASN1_PRINTABLESTRING;
    else if (mask & B_ASN1_IA5STRING)
        str_type = V_ASN1_IA5STRING;
    else if (mask & B_ASN1_T61STRING)
        str_type = V_ASN1_T61STRING;
    else if (mask & B_ASN1_BMPSTRING) {
        str_type = V_ASN1_BMPSTRING;
        outform = MBSTRING_BMP;
    } else if (mask & B_ASN1_UNIVERSALSTRING) {
        str_type = V_ASN1_UNIVERSALSTRING;
        outform = MBSTRING_UNIV;
    } else {
        str_type = V_ASN1_UTF8STRING;
        outform = MBSTRING_UTF8;
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

    /* Work out how much space the destination will need */
    switch (outform) {
    case MBSTRING_ASC:
        outlen = nchar;
        cpyfunc = cpy_asc;
        break;

    case MBSTRING_BMP:
        outlen = nchar << 1;
        cpyfunc = cpy_bmp;
        break;

    case MBSTRING_UNIV:
        outlen = nchar << 2;
        cpyfunc = cpy_univ;
        break;

    case MBSTRING_UTF8:
        outlen = 0;
        traverse_string(in, len, inform, out_utf8, &outlen);
        cpyfunc = cpy_utf8;
        break;
    }
    if (!(p = OPENSSL_malloc(outlen + 1))) {
        if (free_out)
            ASN1_STRING_free(dest);
        OPENSSL_PUT_ERROR(ASN1, ERR_R_MALLOC_FAILURE);
        return -1;
    }
    dest->length = outlen;
    dest->data = p;
    p[outlen] = 0;
    traverse_string(in, len, inform, cpyfunc, &p);
    return str_type;
}

/*
 * This function traverses a string and passes the value of each character to
 * an optional function along with a void * argument.
 */

static int traverse_string(const unsigned char *p, int len, int inform,
                           int (*rfunc) (uint32_t value, void *in),
                           void *arg)
{
    uint32_t value;
    int ret;
    while (len) {
        if (inform == MBSTRING_ASC) {
            value = *p++;
            len--;
        } else if (inform == MBSTRING_BMP) {
            value = *p++ << 8;
            value |= *p++;
            len -= 2;
        } else if (inform == MBSTRING_UNIV) {
            value = ((uint32_t)*p++) << 24;
            value |= ((uint32_t)*p++) << 16;
            value |= *p++ << 8;
            value |= *p++;
            len -= 4;
        } else {
            ret = UTF8_getc(p, len, &value);
            if (ret < 0)
                return -1;
            len -= ret;
            p += ret;
        }
        if (rfunc) {
            ret = rfunc(value, arg);
            if (ret <= 0)
                return ret;
        }
    }
    return 1;
}

/* Various utility functions for traverse_string */

/* Just count number of characters */

static int in_utf8(uint32_t value, void *arg)
{
    int *nchar;
    nchar = arg;
    (*nchar)++;
    return 1;
}

/* Determine size of output as a UTF8 String */

static int out_utf8(uint32_t value, void *arg)
{
    int *outlen;
    outlen = arg;
    *outlen += UTF8_putc(NULL, -1, value);
    return 1;
}

/*
 * Determine the "type" of a string: check each character against a supplied
 * "mask".
 */

static int type_str(uint32_t value, void *arg)
{
    unsigned long types;
    types = *((unsigned long *)arg);
    if ((types & B_ASN1_PRINTABLESTRING) && !is_printable(value))
        types &= ~B_ASN1_PRINTABLESTRING;
    if ((types & B_ASN1_IA5STRING) && (value > 127))
        types &= ~B_ASN1_IA5STRING;
    if ((types & B_ASN1_T61STRING) && (value > 0xff))
        types &= ~B_ASN1_T61STRING;
    if ((types & B_ASN1_BMPSTRING) && (value > 0xffff))
        types &= ~B_ASN1_BMPSTRING;
    if (!types)
        return -1;
    *((unsigned long *)arg) = types;
    return 1;
}

/* Copy one byte per character ASCII like strings */

static int cpy_asc(uint32_t value, void *arg)
{
    unsigned char **p, *q;
    p = arg;
    q = *p;
    *q = (unsigned char)value;
    (*p)++;
    return 1;
}

/* Copy two byte per character BMPStrings */

static int cpy_bmp(uint32_t value, void *arg)
{
    unsigned char **p, *q;
    p = arg;
    q = *p;
    *q++ = (unsigned char)((value >> 8) & 0xff);
    *q = (unsigned char)(value & 0xff);
    *p += 2;
    return 1;
}

/* Copy four byte per character UniversalStrings */

static int cpy_univ(uint32_t value, void *arg)
{
    unsigned char **p, *q;
    p = arg;
    q = *p;
    *q++ = (unsigned char)((value >> 24) & 0xff);
    *q++ = (unsigned char)((value >> 16) & 0xff);
    *q++ = (unsigned char)((value >> 8) & 0xff);
    *q = (unsigned char)(value & 0xff);
    *p += 4;
    return 1;
}

/* Copy to a UTF8String */

static int cpy_utf8(uint32_t value, void *arg)
{
    unsigned char **p;
    int ret;
    p = arg;
    /* We already know there is enough room so pass 0xff as the length */
    ret = UTF8_putc(*p, 0xff, value);
    *p += ret;
    return 1;
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

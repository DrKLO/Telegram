/*
 * datatypes.c
 *
 * data types for finite fields and functions for input, output, and
 * manipulation
 *
 * David A. McGrew
 * Cisco Systems, Inc.
 */
/*
 *
 * Copyright (c) 2001-2017 Cisco Systems, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 *   Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 *   Neither the name of the Cisco Systems, Inc. nor the names of its
 *   contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

#include "config.h"

#ifdef OPENSSL
#include <openssl/crypto.h>
#endif

#include "datatypes.h"

static const int8_t octet_weight[256] = {
    0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4, 1, 2, 2, 3, 2, 3, 3, 4,
    2, 3, 3, 4, 3, 4, 4, 5, 1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
    2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6, 1, 2, 2, 3, 2, 3, 3, 4,
    2, 3, 3, 4, 3, 4, 4, 5, 2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
    2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6, 3, 4, 4, 5, 4, 5, 5, 6,
    4, 5, 5, 6, 5, 6, 6, 7, 1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
    2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6, 2, 3, 3, 4, 3, 4, 4, 5,
    3, 4, 4, 5, 4, 5, 5, 6, 3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
    2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6, 3, 4, 4, 5, 4, 5, 5, 6,
    4, 5, 5, 6, 5, 6, 6, 7, 3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
    4, 5, 5, 6, 5, 6, 6, 7, 5, 6, 6, 7, 6, 7, 7, 8
};

int octet_get_weight(uint8_t octet)
{
    return (int)octet_weight[octet];
}

/*
 * bit_string is a buffer that is used to hold output strings, e.g.
 * for printing.
 */

/* the value MAX_PRINT_STRING_LEN is defined in datatypes.h */

char bit_string[MAX_PRINT_STRING_LEN];

uint8_t srtp_nibble_to_hex_char(uint8_t nibble)
{
    char buf[16] = { '0', '1', '2', '3', '4', '5', '6', '7',
                     '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
    return buf[nibble & 0xF];
}

char *srtp_octet_string_hex_string(const void *s, int length)
{
    const uint8_t *str = (const uint8_t *)s;
    int i;

    /* double length, since one octet takes two hex characters */
    length *= 2;

    /* truncate string if it would be too long */
    if (length > MAX_PRINT_STRING_LEN)
        length = MAX_PRINT_STRING_LEN - 2;

    for (i = 0; i < length; i += 2) {
        bit_string[i] = srtp_nibble_to_hex_char(*str >> 4);
        bit_string[i + 1] = srtp_nibble_to_hex_char(*str++ & 0xF);
    }
    bit_string[i] = 0; /* null terminate string */
    return bit_string;
}

char *v128_hex_string(v128_t *x)
{
    int i, j;

    for (i = j = 0; i < 16; i++) {
        bit_string[j++] = srtp_nibble_to_hex_char(x->v8[i] >> 4);
        bit_string[j++] = srtp_nibble_to_hex_char(x->v8[i] & 0xF);
    }

    bit_string[j] = 0; /* null terminate string */
    return bit_string;
}

char *v128_bit_string(v128_t *x)
{
    int j, i;
    uint32_t mask;

    for (j = i = 0; j < 4; j++) {
        for (mask = 0x80000000; mask > 0; mask >>= 1) {
            if (x->v32[j] & mask)
                bit_string[i] = '1';
            else
                bit_string[i] = '0';
            ++i;
        }
    }
    bit_string[128] = 0; /* null terminate string */

    return bit_string;
}

void v128_copy_octet_string(v128_t *x, const uint8_t s[16])
{
#ifdef ALIGNMENT_32BIT_REQUIRED
    if ((((uint32_t)&s[0]) & 0x3) != 0)
#endif
    {
        x->v8[0] = s[0];
        x->v8[1] = s[1];
        x->v8[2] = s[2];
        x->v8[3] = s[3];
        x->v8[4] = s[4];
        x->v8[5] = s[5];
        x->v8[6] = s[6];
        x->v8[7] = s[7];
        x->v8[8] = s[8];
        x->v8[9] = s[9];
        x->v8[10] = s[10];
        x->v8[11] = s[11];
        x->v8[12] = s[12];
        x->v8[13] = s[13];
        x->v8[14] = s[14];
        x->v8[15] = s[15];
    }
#ifdef ALIGNMENT_32BIT_REQUIRED
    else {
        v128_t *v = (v128_t *)&s[0];

        v128_copy(x, v);
    }
#endif
}

#ifndef DATATYPES_USE_MACROS /* little functions are not macros */

void v128_set_to_zero(v128_t *x)
{
    _v128_set_to_zero(x);
}

void v128_copy(v128_t *x, const v128_t *y)
{
    _v128_copy(x, y);
}

void v128_xor(v128_t *z, v128_t *x, v128_t *y)
{
    _v128_xor(z, x, y);
}

void v128_and(v128_t *z, v128_t *x, v128_t *y)
{
    _v128_and(z, x, y);
}

void v128_or(v128_t *z, v128_t *x, v128_t *y)
{
    _v128_or(z, x, y);
}

void v128_complement(v128_t *x)
{
    _v128_complement(x);
}

int v128_is_eq(const v128_t *x, const v128_t *y)
{
    return _v128_is_eq(x, y);
}

int v128_xor_eq(v128_t *x, const v128_t *y)
{
    return _v128_xor_eq(x, y);
}

int v128_get_bit(const v128_t *x, int i)
{
    return _v128_get_bit(x, i);
}

void v128_set_bit(v128_t *x, int i)
{
    _v128_set_bit(x, i);
}

void v128_clear_bit(v128_t *x, int i)
{
    _v128_clear_bit(x, i);
}

void v128_set_bit_to(v128_t *x, int i, int y)
{
    _v128_set_bit_to(x, i, y);
}

#endif /* DATATYPES_USE_MACROS */

void v128_right_shift(v128_t *x, int shift)
{
    const int base_index = shift >> 5;
    const int bit_index = shift & 31;
    int i, from;
    uint32_t b;

    if (shift > 127) {
        v128_set_to_zero(x);
        return;
    }

    if (bit_index == 0) {
        /* copy each word from left size to right side */
        x->v32[4 - 1] = x->v32[4 - 1 - base_index];
        for (i = 4 - 1; i > base_index; i--)
            x->v32[i - 1] = x->v32[i - 1 - base_index];

    } else {
        /* set each word to the "or" of the two bit-shifted words */
        for (i = 4; i > base_index; i--) {
            from = i - 1 - base_index;
            b = x->v32[from] << bit_index;
            if (from > 0)
                b |= x->v32[from - 1] >> (32 - bit_index);
            x->v32[i - 1] = b;
        }
    }

    /* now wrap up the final portion */
    for (i = 0; i < base_index; i++)
        x->v32[i] = 0;
}

void v128_left_shift(v128_t *x, int shift)
{
    int i;
    const int base_index = shift >> 5;
    const int bit_index = shift & 31;

    if (shift > 127) {
        v128_set_to_zero(x);
        return;
    }

    if (bit_index == 0) {
        for (i = 0; i < 4 - base_index; i++)
            x->v32[i] = x->v32[i + base_index];
    } else {
        for (i = 0; i < 4 - base_index - 1; i++)
            x->v32[i] = (x->v32[i + base_index] >> bit_index) ^
                        (x->v32[i + base_index + 1] << (32 - bit_index));
        x->v32[4 - base_index - 1] = x->v32[4 - 1] >> bit_index;
    }

    /* now wrap up the final portion */
    for (i = 4 - base_index; i < 4; i++)
        x->v32[i] = 0;
}

/* functions manipulating bitvector_t */

#ifndef DATATYPES_USE_MACROS /* little functions are not macros */

int bitvector_get_bit(const bitvector_t *v, int bit_index)
{
    return _bitvector_get_bit(v, bit_index);
}

void bitvector_set_bit(bitvector_t *v, int bit_index)
{
    _bitvector_set_bit(v, bit_index);
}

void bitvector_clear_bit(bitvector_t *v, int bit_index)
{
    _bitvector_clear_bit(v, bit_index);
}

#endif /* DATATYPES_USE_MACROS */

int bitvector_alloc(bitvector_t *v, unsigned long length)
{
    unsigned long l;

    /* Round length up to a multiple of bits_per_word */
    length =
        (length + bits_per_word - 1) & ~(unsigned long)((bits_per_word - 1));

    l = length / bits_per_word * bytes_per_word;

    /* allocate memory, then set parameters */
    if (l == 0) {
        v->word = NULL;
        v->length = 0;
        return -1;
    } else {
        v->word = (uint32_t *)srtp_crypto_alloc(l);
        if (v->word == NULL) {
            v->length = 0;
            return -1;
        }
    }
    v->length = length;

    /* initialize bitvector to zero */
    bitvector_set_to_zero(v);

    return 0;
}

void bitvector_dealloc(bitvector_t *v)
{
    if (v->word != NULL)
        srtp_crypto_free(v->word);
    v->word = NULL;
    v->length = 0;
}

void bitvector_set_to_zero(bitvector_t *x)
{
    /* C99 guarantees that memset(0) will set the value 0 for uint32_t */
    memset(x->word, 0, x->length >> 3);
}

char *bitvector_bit_string(bitvector_t *x, char *buf, int len)
{
    int j, i;
    uint32_t mask;

    for (j = i = 0; j < (int)(x->length >> 5) && i < len - 1; j++) {
        for (mask = 0x80000000; mask > 0; mask >>= 1) {
            if (x->word[j] & mask)
                buf[i] = '1';
            else
                buf[i] = '0';
            ++i;
            if (i >= len - 1)
                break;
        }
    }
    buf[i] = 0; /* null terminate string */

    return buf;
}

void bitvector_left_shift(bitvector_t *x, int shift)
{
    int i;
    const int base_index = shift >> 5;
    const int bit_index = shift & 31;
    const int word_length = x->length >> 5;

    if (shift >= (int)x->length) {
        bitvector_set_to_zero(x);
        return;
    }

    if (bit_index == 0) {
        for (i = 0; i < word_length - base_index; i++)
            x->word[i] = x->word[i + base_index];
    } else {
        for (i = 0; i < word_length - base_index - 1; i++)
            x->word[i] = (x->word[i + base_index] >> bit_index) ^
                         (x->word[i + base_index + 1] << (32 - bit_index));
        x->word[word_length - base_index - 1] =
            x->word[word_length - 1] >> bit_index;
    }

    /* now wrap up the final portion */
    for (i = word_length - base_index; i < word_length; i++)
        x->word[i] = 0;
}

int octet_string_is_eq(uint8_t *a, uint8_t *b, int len)
{
    uint8_t *end = b + len;
    uint8_t accumulator = 0;

    /*
     * We use this somewhat obscure implementation to try to ensure the running
     * time only depends on len, even accounting for compiler optimizations.
     * The accumulator ends up zero iff the strings are equal.
     */
    while (b < end)
        accumulator |= (*a++ ^ *b++);

    /* Return 1 if *not* equal. */
    return accumulator != 0;
}

void srtp_cleanse(void *s, size_t len)
{
    volatile unsigned char *p = (volatile unsigned char *)s;
    while (len--)
        *p++ = 0;
}

void octet_string_set_to_zero(void *s, size_t len)
{
#ifdef OPENSSL
    OPENSSL_cleanse(s, len);
#else
    srtp_cleanse(s, len);
#endif
}

#ifdef TESTAPP_SOURCE

static const char b64chars[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                               "abcdefghijklmnopqrstuvwxyz0123456789+/";

static int base64_block_to_octet_triple(char *out, char *in)
{
    unsigned char sextets[4] = { 0 };
    int j = 0;
    int i;

    for (i = 0; i < 4; i++) {
        char *p = strchr(b64chars, in[i]);
        if (p != NULL)
            sextets[i] = p - b64chars;
        else
            j++;
    }

    out[0] = (sextets[0] << 2) | (sextets[1] >> 4);
    if (j < 2)
        out[1] = (sextets[1] << 4) | (sextets[2] >> 2);
    if (j < 1)
        out[2] = (sextets[2] << 6) | sextets[3];
    return j;
}

int base64_string_to_octet_string(char *out, int *pad, char *in, int len)
{
    int k = 0;
    int i = 0;
    int j = 0;
    if (len % 4 != 0)
        return 0;

    while (i < len && j == 0) {
        j = base64_block_to_octet_triple(out + k, in + i);
        k += 3;
        i += 4;
    }
    *pad = j;
    return i;
}

#endif

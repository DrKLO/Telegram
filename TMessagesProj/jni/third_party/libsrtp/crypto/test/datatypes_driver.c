/*
 * datatypes_driver.c
 *
 * a test driver for crypto/math datatypes
 *
 * David A. McGrew
 * Cisco Systems, Inc.
 */

/*
 *
 * Copyright (c) 2001-2017, Cisco Systems, Inc.
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

#include <stdio.h>  /* for printf() */
#include <string.h> /* for strlen() */
#include "datatypes.h"
#include "util.h"

void byte_order(void);

void test_hex_string_funcs(void);

void print_string(char *s);

void test_bswap(void);

int main(void)
{
    /*
     * this program includes various and sundry tests for fundamental
     * datatypes.  it's a grab-bag of throwaway code, retained only in
     * case of future problems
     */

    int i, j;
    v128_t x;
    char *r = "The Moving Finger writes; and, having writ,\n"
              "Moves on: nor all thy Piety nor Wit\n"
              "Shall lure it back to cancel half a Line,\n"
              "Nor all thy Tears wash out a Word of it.";
    char *s = "incomplet";

    print_string(r);
    print_string(s);

    byte_order();
    test_hex_string_funcs();

    for (j = 0; j < 128; j++) {
        v128_set_to_zero(&x);
        /*      x.v32[0] = (1 << j); */
        v128_set_bit(&x, j);
        printf("%s\n", v128_bit_string(&x));
        v128_clear_bit(&x, j);
        printf("%s\n", v128_bit_string(&x));
    }

    printf("----------------------------------------------\n");
    v128_set_to_zero(&x);
    for (i = 0; i < 128; i++) {
        v128_set_bit(&x, i);
    }
    printf("%s\n", v128_bit_string(&x));

    printf("----------------------------------------------\n");
    v128_set_to_zero(&x);
    v128_set_bit(&x, 0);
    for (i = 0; i < 128; i++) {
        printf("%s\n", v128_bit_string(&x));
        v128_right_shift(&x, 1);
    }
    printf("----------------------------------------------\n");
    v128_set_to_zero(&x);
    v128_set_bit(&x, 127);
    for (i = 0; i < 128; i++) {
        printf("%s\n", v128_bit_string(&x));
        v128_left_shift(&x, 1);
    }
    printf("----------------------------------------------\n");
    for (i = 0; i < 128; i++) {
        v128_set_to_zero(&x);
        v128_set_bit(&x, 127);
        v128_left_shift(&x, i);
        printf("%s\n", v128_bit_string(&x));
    }
    printf("----------------------------------------------\n");
    v128_set_to_zero(&x);
    for (i = 0; i < 128; i += 2) {
        v128_set_bit(&x, i);
    }
    printf("bit_string: { %s }\n", v128_bit_string(&x));
    printf("get_bit:    { ");
    for (i = 0; i < 128; i++) {
        if (v128_get_bit(&x, i) == 1)
            printf("1");
        else
            printf("0");
    }
    printf(" } \n");

    test_bswap();

    return 0;
}

/* byte_order() prints out byte ordering of datatypes */

void byte_order(void)
{
    int i;
    v128_t e;
#if 0
  v16_t b;
  v32_t c;
  v64_t d;

  for (i=0; i < sizeof(b); i++)
    b.octet[i] = i;
  for (i=0; i < sizeof(c); i++)
    c.octet[i] = i;
  for (i=0; i < sizeof(d); i++)
    d.octet[i] = i;
  
  printf("v128_t:\t%s\n", v128_hex_string(&e));
  printf("v64_t:\t%s\n", v64_hex_string(&d));
  printf("v32_t:\t%s\n", v32_hex_string(c));
  printf("v16_t:\t%s\n", v16_hex_string(b));

  c.value = 0x01020304;
  printf("v32_t:\t%s\n", v32_hex_string(c));
  b.value = 0x0102;
  printf("v16_t:\t%s\n", v16_hex_string(b));

  printf("uint16_t ordering:\n");

  c.value = 0x00010002;
  printf("v32_t:\t%x%x\n", c.v16[0], c.v16[1]);
#endif

    printf("byte ordering of crypto/math datatypes:\n");
    for (i = 0; i < sizeof(e); i++)
        e.v8[i] = i;
    printf("v128_t: %s\n", v128_hex_string(&e));
}

void test_hex_string_funcs(void)
{
    char hex1[] = "abadcafe";
    char hex2[] = "0123456789abcdefqqqqq";
    char raw[10];
    int len;

    len = hex_string_to_octet_string(raw, hex1, strlen(hex1));
    printf("computed length: %d\tstring: %s\n", len,
           octet_string_hex_string(raw, len / 2));
    printf("expected length: %u\tstring: %s\n", (unsigned)strlen(hex1), hex1);

    len = hex_string_to_octet_string(raw, hex2, strlen(hex2));
    printf("computed length: %d\tstring: %s\n", len,
           octet_string_hex_string(raw, len / 2));
    printf("expected length: %d\tstring: %s\n", 16, "0123456789abcdef");
}

void print_string(char *s)
{
    size_t i;
    printf("%s\n", s);
    printf("strlen(s) = %u\n", (unsigned)strlen(s));
    printf("{ ");
    for (i = 0; i < strlen(s); i++) {
        printf("0x%x, ", s[i]);
        if (((i + 1) % 8) == 0)
            printf("\n   ");
    }
    printf("}\n");
}

void test_bswap(void)
{
    uint32_t x = 0x11223344;
    uint64_t y = 0x1122334455667788LL;

    printf("before: %0x\nafter:  %0x\n", x, (unsigned int)be32_to_cpu(x));
    printf("before: %0llx\nafter:  %0llx\n", (unsigned long long)y,
           (unsigned long long)be64_to_cpu(y));

    y = 1234;

    printf("1234: %0llx\n", (unsigned long long)y);
    printf("as octet string: %s\n", octet_string_hex_string((uint8_t *)&y, 8));
    y = be64_to_cpu(y);
    printf("bswapped octet string: %s\n",
           octet_string_hex_string((uint8_t *)&y, 8));
}

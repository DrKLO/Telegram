/*
 * ut_sim.c
 *
 * an unreliable transport simulator
 * (for testing replay databases and suchlike)
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
#include "ut_sim.h"

int ut_compar(const void *a, const void *b)
{
    return rand() > (RAND_MAX / 2) ? -1 : 1;
}

void ut_init(ut_connection *utc)
{
    int i;
    utc->index = 0;

    for (i = 0; i < UT_BUF; i++)
        utc->buffer[i] = i;

    qsort(utc->buffer, UT_BUF, sizeof(uint32_t), ut_compar);

    utc->index = UT_BUF - 1;
}

uint32_t ut_next_index(ut_connection *utc)
{
    uint32_t tmp;

    tmp = utc->buffer[0];
    utc->index++;
    utc->buffer[0] = utc->index;

    qsort(utc->buffer, UT_BUF, sizeof(uint32_t), ut_compar);

    return tmp;
}

#ifdef UT_TEST

#include <stdio.h>

int main()
{
    uint32_t i, irecvd, idiff;
    ut_connection utc;

    ut_init(&utc);

    for (i = 0; i < 1000; i++) {
        irecvd = ut_next_index(&utc);
        idiff = i - irecvd;
        printf("%lu\t%lu\t%d\n", i, irecvd, idiff);
    }

    return 0;
}

#endif

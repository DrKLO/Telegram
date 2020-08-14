/*
 * stats.c
 *
 * statistical tests
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

#include "stat.h"

srtp_debug_module_t srtp_mod_stat = {
    0,                  /* debugging is off by default */
    (char *)"stat test" /* printable module name       */
};

/*
 * each test assumes that 20,000 bits (2500 octets) of data is
 * provided as input
 */

#define STAT_TEST_DATA_LEN 2500

srtp_err_status_t stat_test_monobit(uint8_t *data)
{
    uint8_t *data_end = data + STAT_TEST_DATA_LEN;
    uint16_t ones_count;

    ones_count = 0;
    while (data < data_end) {
        ones_count += octet_get_weight(*data);
        data++;
    }

    debug_print(srtp_mod_stat, "bit count: %d", ones_count);

    if ((ones_count < 9725) || (ones_count > 10275))
        return srtp_err_status_algo_fail;

    return srtp_err_status_ok;
}

srtp_err_status_t stat_test_poker(uint8_t *data)
{
    int i;
    uint8_t *data_end = data + STAT_TEST_DATA_LEN;
    double poker;
    uint16_t f[16] = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    while (data < data_end) {
        f[*data & 0x0f]++; /* increment freq. count for low nibble  */
        f[(*data) >> 4]++; /* increment freq. count for high nibble */
        data++;
    }

    poker = 0.0;
    for (i = 0; i < 16; i++)
        poker += (double)f[i] * f[i];

    poker *= (16.0 / 5000.0);
    poker -= 5000.0;

    debug_print(srtp_mod_stat, "poker test: %f\n", poker);

    if ((poker < 2.16) || (poker > 46.17))
        return srtp_err_status_algo_fail;

    return srtp_err_status_ok;
}

/*
 * runs[i] holds the number of runs of size (i-1)
 */

srtp_err_status_t stat_test_runs(uint8_t *data)
{
    uint8_t *data_end = data + STAT_TEST_DATA_LEN;
    uint16_t runs[6] = { 0, 0, 0, 0, 0, 0 };
    uint16_t gaps[6] = { 0, 0, 0, 0, 0, 0 };
    uint16_t lo_value[6] = { 2315, 1114, 527, 240, 103, 103 };
    uint16_t hi_value[6] = { 2685, 1386, 723, 384, 209, 209 };
    int state = 0;
    uint16_t mask;
    int i;

    /*
     * the state variable holds the number of bits in the
     * current run (or gap, if negative)
     */

    while (data < data_end) {
        /* loop over the bits of this byte */
        for (mask = 1; mask < 256; mask <<= 1) {
            if (*data & mask) {
                /* next bit is a one  */
                if (state > 0) {
                    /* prefix is a run, so increment the run-count  */
                    state++;

                    /* check for long runs */
                    if (state > 25) {
                        debug_print(srtp_mod_stat, ">25 runs: %d", state);
                        return srtp_err_status_algo_fail;
                    }

                } else if (state < 0) {
                    /* prefix is a gap  */
                    if (state < -25) {
                        debug_print(srtp_mod_stat, ">25 gaps: %d", state);
                        return srtp_err_status_algo_fail; /* long-runs test
                                                             failed   */
                    }
                    if (state < -6) {
                        state = -6; /* group together gaps > 5 */
                    }
                    gaps[-1 - state]++; /* increment gap count      */
                    state = 1;          /* set state at one set bit */
                } else {
                    /* state is zero; this happens only at initialization */
                    state = 1;
                }
            } else {
                /* next bit is a zero  */
                if (state > 0) {
                    /* prefix is a run */
                    if (state > 25) {
                        debug_print(srtp_mod_stat, ">25 runs (2): %d", state);
                        return srtp_err_status_algo_fail; /* long-runs test
                                                             failed   */
                    }
                    if (state > 6) {
                        state = 6; /* group together runs > 5 */
                    }
                    runs[state - 1]++; /* increment run count       */
                    state = -1;        /* set state at one zero bit */
                } else if (state < 0) {
                    /* prefix is a gap, so increment gap-count (decrement state)
                     */
                    state--;

                    /* check for long gaps */
                    if (state < -25) {
                        debug_print(srtp_mod_stat, ">25 gaps (2): %d", state);
                        return srtp_err_status_algo_fail;
                    }

                } else {
                    /* state is zero; this happens only at initialization */
                    state = -1;
                }
            }
        }

        /* move along to next octet */
        data++;
    }

    if (srtp_mod_stat.on) {
        debug_print(srtp_mod_stat, "runs test", NULL);
        for (i = 0; i < 6; i++)
            debug_print(srtp_mod_stat, "  runs[]: %d", runs[i]);
        for (i = 0; i < 6; i++)
            debug_print(srtp_mod_stat, "  gaps[]: %d", gaps[i]);
    }

    /* check run and gap counts against the fixed limits */
    for (i = 0; i < 6; i++)
        if ((runs[i] < lo_value[i]) || (runs[i] > hi_value[i]) ||
            (gaps[i] < lo_value[i]) || (gaps[i] > hi_value[i]))
            return srtp_err_status_algo_fail;

    return srtp_err_status_ok;
}

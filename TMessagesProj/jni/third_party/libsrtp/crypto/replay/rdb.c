/*
 * rdb.c
 *
 * Implements a replay database for packet security
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

#include "rdb.h"

/*
 * this implementation of a replay database works as follows:
 *
 * window_start is the index of the first packet in the window
 * bitmask      a bit-buffer, containing the most recently entered
 *              index as the leftmost bit
 *
 */

/* srtp_rdb_init initalizes rdb */
srtp_err_status_t srtp_rdb_init(srtp_rdb_t *rdb)
{
    v128_set_to_zero(&rdb->bitmask);
    rdb->window_start = 0;
    return srtp_err_status_ok;
}

/*
 * srtp_rdb_check checks to see if index appears in rdb
 */
srtp_err_status_t srtp_rdb_check(const srtp_rdb_t *rdb, uint32_t p_index)
{
    /* if the index appears after (or at very end of) the window, its good */
    if (p_index >= rdb->window_start + rdb_bits_in_bitmask) {
        return srtp_err_status_ok;
    }

    /* if the index appears before the window, its bad */
    if (p_index < rdb->window_start) {
        return srtp_err_status_replay_old;
    }

    /* otherwise, the index appears within the window, so check the bitmask */
    if (v128_get_bit(&rdb->bitmask, (p_index - rdb->window_start)) == 1) {
        return srtp_err_status_replay_fail;
    }

    /* otherwise, the index is okay */
    return srtp_err_status_ok;
}

/*
 * srtp_rdb_add_index adds index to srtp_rdb_t (and does *not* check if
 * index appears in db)
 *
 * this function should be called only after srtp_rdb_check has
 * indicated that the index does not appear in the rdb, e.g., a mutex
 * should protect the rdb between these calls
 */
srtp_err_status_t srtp_rdb_add_index(srtp_rdb_t *rdb, uint32_t p_index)
{
    unsigned int delta;

    if (p_index < rdb->window_start)
        return srtp_err_status_replay_fail;

    delta = (p_index - rdb->window_start);
    if (delta < rdb_bits_in_bitmask) {
        /* if the p_index is within the window, set the appropriate bit */
        v128_set_bit(&rdb->bitmask, delta);

    } else {
        delta -= rdb_bits_in_bitmask - 1;

        /* shift the window forward by delta bits*/
        v128_left_shift(&rdb->bitmask, delta);
        v128_set_bit(&rdb->bitmask, rdb_bits_in_bitmask - 1);
        rdb->window_start += delta;
    }

    return srtp_err_status_ok;
}

srtp_err_status_t srtp_rdb_increment(srtp_rdb_t *rdb)
{
    if (rdb->window_start >= 0x7fffffff) {
        return srtp_err_status_key_expired;
    }
    ++rdb->window_start;
    return srtp_err_status_ok;
}

uint32_t srtp_rdb_get_value(const srtp_rdb_t *rdb)
{
    return rdb->window_start;
}

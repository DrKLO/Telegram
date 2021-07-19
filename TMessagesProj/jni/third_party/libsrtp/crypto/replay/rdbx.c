/*
 * rdbx.c
 *
 * a replay database with extended range, using a rollover counter
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

#include "rdbx.h"

/*
 * from RFC 3711:
 *
 * A receiver reconstructs the index i of a packet with sequence
 *  number SEQ using the estimate
 *
 * i = 2^16 * v + SEQ,
 *
 * where v is chosen from the set { ROC-1, ROC, ROC+1 } such that i is
 * closest to the value 2^16 * ROC + s_l.  If the value r+1 is used,
 * then the rollover counter r in the cryptographic context is
 * incremented by one (if the packet containing s is authentic).
 */

/*
 * rdbx implementation notes
 *
 * A srtp_xtd_seq_num_t is essentially a sequence number for which some of
 * the data on the wire are implicit.  It logically consists of a
 * rollover counter and a sequence number; the sequence number is the
 * explicit part, and the rollover counter is the implicit part.
 *
 * Upon receiving a sequence_number (e.g. in a newly received SRTP
 * packet), the complete srtp_xtd_seq_num_t can be estimated by using a
 * local srtp_xtd_seq_num_t as a basis.  This is done using the function
 * srtp_index_guess(&local, &guess, seq_from_packet).  This function
 * returns the difference of the guess and the local value.  The local
 * srtp_xtd_seq_num_t can be moved forward to the guess using the function
 * srtp_index_advance(&guess, delta), where delta is the difference.
 *
 *
 * A srtp_rdbx_t consists of a srtp_xtd_seq_num_t and a bitmask.  The index is
 * highest sequence number that has been received, and the bitmask indicates
 * which of the recent indicies have been received as well.  The
 * highest bit in the bitmask corresponds to the index in the bitmask.
 */

void srtp_index_init(srtp_xtd_seq_num_t *pi)
{
#ifdef NO_64BIT_MATH
    *pi = make64(0, 0);
#else
    *pi = 0;
#endif
}

void srtp_index_advance(srtp_xtd_seq_num_t *pi, srtp_sequence_number_t s)
{
#ifdef NO_64BIT_MATH
    /* a > ~b means a+b will generate a carry */
    /* s is uint16 here */
    *pi = make64(high32(*pi) + (s > ~low32(*pi) ? 1 : 0), low32(*pi) + s);
#else
    *pi += s;
#endif
}

/*
 * srtp_index_guess(local, guess, s)
 *
 * given a srtp_xtd_seq_num_t local (which represents the last
 * known-to-be-good received srtp_xtd_seq_num_t) and a sequence number s
 * (from a newly arrived packet), sets the contents of *guess to
 * contain the best guess of the packet index to which s corresponds,
 * and returns the difference between *guess and *local
 *
 * nota bene - the output is a signed integer, DON'T cast it to a
 * unsigned integer!
 */

int32_t srtp_index_guess(const srtp_xtd_seq_num_t *local,
                         srtp_xtd_seq_num_t *guess,
                         srtp_sequence_number_t s)
{
#ifdef NO_64BIT_MATH
    uint32_t local_roc = ((high32(*local) << 16) | (low32(*local) >> 16));
    uint16_t local_seq = (uint16_t)(low32(*local));
#else
    uint32_t local_roc = (uint32_t)(*local >> 16);
    uint16_t local_seq = (uint16_t)*local;
#endif
    uint32_t guess_roc;
    uint16_t guess_seq;
    int32_t difference;

    if (local_seq < seq_num_median) {
        if (s - local_seq > seq_num_median) {
            guess_roc = local_roc - 1;
            difference = s - local_seq - seq_num_max;
        } else {
            guess_roc = local_roc;
            difference = s - local_seq;
        }
    } else {
        if (local_seq - seq_num_median > s) {
            guess_roc = local_roc + 1;
            difference = s - local_seq + seq_num_max;
        } else {
            guess_roc = local_roc;
            difference = s - local_seq;
        }
    }
    guess_seq = s;

/* Note: guess_roc is 32 bits, so this generates a 48-bit result! */
#ifdef NO_64BIT_MATH
    *guess = make64(guess_roc >> 16, (guess_roc << 16) | guess_seq);
#else
    *guess = (((uint64_t)guess_roc) << 16) | guess_seq;
#endif

    return difference;
}

/*
 * rdbx
 *
 */

/*
 *  srtp_rdbx_init(&r, ws) initializes the srtp_rdbx_t pointed to by r with
 * window size ws
 */
srtp_err_status_t srtp_rdbx_init(srtp_rdbx_t *rdbx, unsigned long ws)
{
    if (ws == 0) {
        return srtp_err_status_bad_param;
    }

    if (bitvector_alloc(&rdbx->bitmask, ws) != 0) {
        return srtp_err_status_alloc_fail;
    }

    srtp_index_init(&rdbx->index);

    return srtp_err_status_ok;
}

/*
 *  srtp_rdbx_dealloc(&r) frees memory for the srtp_rdbx_t pointed to by r
 */
srtp_err_status_t srtp_rdbx_dealloc(srtp_rdbx_t *rdbx)
{
    bitvector_dealloc(&rdbx->bitmask);

    return srtp_err_status_ok;
}

/*
 * srtp_rdbx_set_roc(rdbx, roc) initalizes the srtp_rdbx_t at the location rdbx
 * to have the rollover counter value roc.  If that value is less than
 * the current rollover counter value, then the function returns
 * srtp_err_status_replay_old; otherwise, srtp_err_status_ok is returned.
 *
 */
srtp_err_status_t srtp_rdbx_set_roc(srtp_rdbx_t *rdbx, uint32_t roc)
{
    bitvector_set_to_zero(&rdbx->bitmask);

#ifdef NO_64BIT_MATH
#error not yet implemented
#else

    /* make sure that we're not moving backwards */
    if (roc < (rdbx->index >> 16)) {
        return srtp_err_status_replay_old;
    }

    rdbx->index &= 0xffff;                /* retain lowest 16 bits */
    rdbx->index |= ((uint64_t)roc) << 16; /* set ROC */
#endif

    return srtp_err_status_ok;
}

/*
 * srtp_rdbx_get_packet_index(rdbx) returns the value of the packet index
 * for the srtp_rdbx_t pointed to by rdbx
 *
 */
srtp_xtd_seq_num_t srtp_rdbx_get_packet_index(const srtp_rdbx_t *rdbx)
{
    return rdbx->index;
}

/*
 * srtp_rdbx_get_window_size(rdbx) returns the value of the window size
 * for the srtp_rdbx_t pointed to by rdbx
 *
 */
unsigned long srtp_rdbx_get_window_size(const srtp_rdbx_t *rdbx)
{
    return bitvector_get_length(&rdbx->bitmask);
}

/*
 * srtp_rdbx_check(&r, delta) checks to see if the srtp_xtd_seq_num_t
 * which is at rdbx->index + delta is in the rdb
 */
srtp_err_status_t srtp_rdbx_check(const srtp_rdbx_t *rdbx, int delta)
{
    if (delta > 0) { /* if delta is positive, it's good */
        return srtp_err_status_ok;
    } else if ((int)(bitvector_get_length(&rdbx->bitmask) - 1) + delta < 0) {
        /* if delta is lower than the bitmask, it's bad */
        return srtp_err_status_replay_old;
    } else if (bitvector_get_bit(
                   &rdbx->bitmask,
                   (int)(bitvector_get_length(&rdbx->bitmask) - 1) + delta) ==
               1) {
        /* delta is within the window, so check the bitmask */
        return srtp_err_status_replay_fail;
    }
    /* otherwise, the index is okay */

    return srtp_err_status_ok;
}

/*
 * srtp_rdbx_add_index adds the srtp_xtd_seq_num_t at rdbx->window_start + d to
 * replay_db (and does *not* check if that srtp_xtd_seq_num_t appears in db)
 *
 * this function should be called only after replay_check has
 * indicated that the index does not appear in the rdbx, e.g., a mutex
 * should protect the rdbx between these calls if need be
 */
srtp_err_status_t srtp_rdbx_add_index(srtp_rdbx_t *rdbx, int delta)
{
    if (delta > 0) {
        /* shift forward by delta */
        srtp_index_advance(&rdbx->index, delta);
        bitvector_left_shift(&rdbx->bitmask, delta);
        bitvector_set_bit(&rdbx->bitmask,
                          bitvector_get_length(&rdbx->bitmask) - 1);
    } else {
        /* delta is in window */
        bitvector_set_bit(&rdbx->bitmask,
                          bitvector_get_length(&rdbx->bitmask) - 1 + delta);
    }

    /* note that we need not consider the case that delta == 0 */

    return srtp_err_status_ok;
}

/*
 * srtp_rdbx_estimate_index(rdbx, guess, s)
 *
 * given an rdbx and a sequence number s (from a newly arrived packet),
 * sets the contents of *guess to contain the best guess of the packet
 * index to which s corresponds, and returns the difference between
 * *guess and the locally stored synch info
 */
int32_t srtp_rdbx_estimate_index(const srtp_rdbx_t *rdbx,
                                 srtp_xtd_seq_num_t *guess,
                                 srtp_sequence_number_t s)
{
/*
 * if the sequence number and rollover counter in the rdbx are
 * non-zero, then use the srtp_index_guess(...) function, otherwise, just
 * set the rollover counter to zero (since the srtp_index_guess(...)
 * function might incorrectly guess that the rollover counter is
 * 0xffffffff)
 */

#ifdef NO_64BIT_MATH
    /* seq_num_median = 0x8000 */
    if (high32(rdbx->index) > 0 || low32(rdbx->index) > seq_num_median)
#else
    if (rdbx->index > seq_num_median)
#endif
    {
        return srtp_index_guess(&rdbx->index, guess, s);
    }

#ifdef NO_64BIT_MATH
    *guess = make64(0, (uint32_t)s);
#else
    *guess = s;
#endif

#ifdef NO_64BIT_MATH
    return s - (uint16_t)low32(rdbx->index);
#else
    return s - (uint16_t)rdbx->index;
#endif
}

/*
 * srtp_rdbx_get_roc(rdbx)
 *
 * Get the current rollover counter
 *
 */
uint32_t srtp_rdbx_get_roc(const srtp_rdbx_t *rdbx)
{
    uint32_t roc;

#ifdef NO_64BIT_MATH
    roc = ((high32(rdbx->index) << 16) | (low32(rdbx->index) >> 16));
#else
    roc = (uint32_t)(rdbx->index >> 16);
#endif

    return roc;
}

/*
 * srtp_rdbx_set_roc_seq(rdbx, roc, seq) initalizes the srtp_rdbx_t at the
 * location rdbx to have the rollover counter value roc and packet sequence
 * number seq.  If the new rollover counter value is less than the current
 * rollover counter value, then the function returns
 * srtp_err_status_replay_old, otherwise, srtp_err_status_ok is returned.
 */
srtp_err_status_t srtp_rdbx_set_roc_seq(srtp_rdbx_t *rdbx,
                                        uint32_t roc,
                                        uint16_t seq)
{
#ifdef NO_64BIT_MATH
#error not yet implemented
#else

    /* make sure that we're not moving backwards */
    if (roc < (rdbx->index >> 16)) {
        return srtp_err_status_replay_old;
    }

    rdbx->index = seq;
    rdbx->index |= ((uint64_t)roc) << 16; /* set ROC */
#endif

    bitvector_set_to_zero(&rdbx->bitmask);

    return srtp_err_status_ok;
}

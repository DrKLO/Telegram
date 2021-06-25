/*
 * exp golomb vlc stuff
 * Copyright (c) 2003 Michael Niedermayer <michaelni@gmx.at>
 * Copyright (c) 2004 Alex Beregszaszi
 *
 * This file is part of FFmpeg.
 *
 * FFmpeg is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * FFmpeg is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with FFmpeg; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

/**
 * @file
 * @brief
 *     exp golomb vlc stuff
 * @author Michael Niedermayer <michaelni@gmx.at> and Alex Beregszaszi
 */

#ifndef AVCODEC_GOLOMB_H
#define AVCODEC_GOLOMB_H

#include <stdint.h>

#include "get_bits.h"

#define INVALID_VLC           0x80000000

extern const uint8_t ff_golomb_vlc_len[512];
extern const uint8_t ff_ue_golomb_vlc_code[512];
extern const  int8_t ff_se_golomb_vlc_code[512];
extern const uint8_t ff_ue_golomb_len[256];

extern const uint8_t ff_interleaved_golomb_vlc_len[256];
extern const uint8_t ff_interleaved_ue_golomb_vlc_code[256];
extern const  int8_t ff_interleaved_se_golomb_vlc_code[256];
extern const uint8_t ff_interleaved_dirac_golomb_vlc_code[256];

/**
 * Read an unsigned Exp-Golomb code in the range 0 to 8190.
 *
 * @returns the read value or a negative error code.
 */
static inline int get_ue_golomb(GetBitContext *gb)
{
    unsigned int buf;

#if CACHED_BITSTREAM_READER
    buf = show_bits_long(gb, 32);

    if (buf >= (1 << 27)) {
        buf >>= 32 - 9;
        skip_bits_long(gb, ff_golomb_vlc_len[buf]);

        return ff_ue_golomb_vlc_code[buf];
    } else {
        int log = 2 * av_log2(buf) - 31;
        buf >>= log;
        buf--;
        skip_bits_long(gb, 32 - log);

        return buf;
    }
#else
    OPEN_READER(re, gb);
    UPDATE_CACHE(re, gb);
    buf = GET_CACHE(re, gb);

    if (buf >= (1 << 27)) {
        buf >>= 32 - 9;
        LAST_SKIP_BITS(re, gb, ff_golomb_vlc_len[buf]);
        CLOSE_READER(re, gb);

        return ff_ue_golomb_vlc_code[buf];
    } else {
        int log = 2 * av_log2(buf) - 31;
        LAST_SKIP_BITS(re, gb, 32 - log);
        CLOSE_READER(re, gb);
        if (log < 7) {
            av_log(NULL, AV_LOG_ERROR, "Invalid UE golomb code\n");
            return AVERROR_INVALIDDATA;
        }
        buf >>= log;
        buf--;

        return buf;
    }
#endif
}

/**
 * Read an unsigned Exp-Golomb code in the range 0 to UINT32_MAX-1.
 */
static inline unsigned get_ue_golomb_long(GetBitContext *gb)
{
    unsigned buf, log;

    buf = show_bits_long(gb, 32);
    log = 31 - av_log2(buf);
    skip_bits_long(gb, log);

    return get_bits_long(gb, log + 1) - 1;
}

/**
 * read unsigned exp golomb code, constraint to a max of 31.
 * the return value is undefined if the stored value exceeds 31.
 */
static inline int get_ue_golomb_31(GetBitContext *gb)
{
    unsigned int buf;

#if CACHED_BITSTREAM_READER
    buf = show_bits_long(gb, 32);

    buf >>= 32 - 9;
    skip_bits_long(gb, ff_golomb_vlc_len[buf]);
#else

    OPEN_READER(re, gb);
    UPDATE_CACHE(re, gb);
    buf = GET_CACHE(re, gb);

    buf >>= 32 - 9;
    LAST_SKIP_BITS(re, gb, ff_golomb_vlc_len[buf]);
    CLOSE_READER(re, gb);
#endif

    return ff_ue_golomb_vlc_code[buf];
}

static inline unsigned get_interleaved_ue_golomb(GetBitContext *gb)
{
    uint32_t buf;

#if CACHED_BITSTREAM_READER
    buf = show_bits_long(gb, 32);

    if (buf & 0xAA800000) {
        buf >>= 32 - 8;
        skip_bits_long(gb, ff_interleaved_golomb_vlc_len[buf]);

        return ff_interleaved_ue_golomb_vlc_code[buf];
    } else {
        unsigned ret = 1;

        do {
            buf >>= 32 - 8;
            skip_bits_long(gb, FFMIN(ff_interleaved_golomb_vlc_len[buf], 8));

            if (ff_interleaved_golomb_vlc_len[buf] != 9) {
                ret <<= (ff_interleaved_golomb_vlc_len[buf] - 1) >> 1;
                ret  |= ff_interleaved_dirac_golomb_vlc_code[buf];
                break;
            }
            ret = (ret << 4) | ff_interleaved_dirac_golomb_vlc_code[buf];
            buf = show_bits_long(gb, 32);
        } while (get_bits_left(gb) > 0);

        return ret - 1;
    }
#else
    OPEN_READER(re, gb);
    UPDATE_CACHE(re, gb);
    buf = GET_CACHE(re, gb);

    if (buf & 0xAA800000) {
        buf >>= 32 - 8;
        LAST_SKIP_BITS(re, gb, ff_interleaved_golomb_vlc_len[buf]);
        CLOSE_READER(re, gb);

        return ff_interleaved_ue_golomb_vlc_code[buf];
    } else {
        unsigned ret = 1;

        do {
            buf >>= 32 - 8;
            LAST_SKIP_BITS(re, gb,
                           FFMIN(ff_interleaved_golomb_vlc_len[buf], 8));

            if (ff_interleaved_golomb_vlc_len[buf] != 9) {
                ret <<= (ff_interleaved_golomb_vlc_len[buf] - 1) >> 1;
                ret  |= ff_interleaved_dirac_golomb_vlc_code[buf];
                break;
            }
            ret = (ret << 4) | ff_interleaved_dirac_golomb_vlc_code[buf];
            UPDATE_CACHE(re, gb);
            buf = GET_CACHE(re, gb);
        } while (ret<0x8000000U && BITS_AVAILABLE(re, gb));

        CLOSE_READER(re, gb);
        return ret - 1;
    }
#endif
}

/**
 * read unsigned truncated exp golomb code.
 */
static inline int get_te0_golomb(GetBitContext *gb, int range)
{
    av_assert2(range >= 1);

    if (range == 1)
        return 0;
    else if (range == 2)
        return get_bits1(gb) ^ 1;
    else
        return get_ue_golomb(gb);
}

/**
 * read unsigned truncated exp golomb code.
 */
static inline int get_te_golomb(GetBitContext *gb, int range)
{
    av_assert2(range >= 1);

    if (range == 2)
        return get_bits1(gb) ^ 1;
    else
        return get_ue_golomb(gb);
}

/**
 * read signed exp golomb code.
 */
static inline int get_se_golomb(GetBitContext *gb)
{
    unsigned int buf;

#if CACHED_BITSTREAM_READER
    buf = show_bits_long(gb, 32);

    if (buf >= (1 << 27)) {
        buf >>= 32 - 9;
        skip_bits_long(gb, ff_golomb_vlc_len[buf]);

        return ff_se_golomb_vlc_code[buf];
    } else {
        int log = 2 * av_log2(buf) - 31;
        buf >>= log;

        skip_bits_long(gb, 32 - log);

        if (buf & 1)
            buf = -(buf >> 1);
        else
            buf = (buf >> 1);

        return buf;
    }
#else
    OPEN_READER(re, gb);
    UPDATE_CACHE(re, gb);
    buf = GET_CACHE(re, gb);

    if (buf >= (1 << 27)) {
        buf >>= 32 - 9;
        LAST_SKIP_BITS(re, gb, ff_golomb_vlc_len[buf]);
        CLOSE_READER(re, gb);

        return ff_se_golomb_vlc_code[buf];
    } else {
        int log = av_log2(buf), sign;
        LAST_SKIP_BITS(re, gb, 31 - log);
        UPDATE_CACHE(re, gb);
        buf = GET_CACHE(re, gb);

        buf >>= log;

        LAST_SKIP_BITS(re, gb, 32 - log);
        CLOSE_READER(re, gb);

        sign = -(buf & 1);
        buf  = ((buf >> 1) ^ sign) - sign;

        return buf;
    }
#endif
}

static inline int get_se_golomb_long(GetBitContext *gb)
{
    unsigned int buf = get_ue_golomb_long(gb);
    int sign = (buf & 1) - 1;
    return ((buf >> 1) ^ sign) + 1;
}

static inline int get_interleaved_se_golomb(GetBitContext *gb)
{
    unsigned int buf;

#if CACHED_BITSTREAM_READER
    buf = show_bits_long(gb, 32);

    if (buf & 0xAA800000) {
        buf >>= 32 - 8;
        skip_bits_long(gb, ff_interleaved_golomb_vlc_len[buf]);

        return ff_interleaved_se_golomb_vlc_code[buf];
    } else {
        int log;
        skip_bits(gb, 8);
        buf |= 1 | show_bits(gb, 24);

        if ((buf & 0xAAAAAAAA) == 0)
            return INVALID_VLC;

        for (log = 31; (buf & 0x80000000) == 0; log--)
            buf = (buf << 2) - ((buf << log) >> (log - 1)) + (buf >> 30);

        skip_bits_long(gb, 63 - 2 * log - 8);

        return (signed) (((((buf << log) >> log) - 1) ^ -(buf & 0x1)) + 1) >> 1;
    }
#else
    OPEN_READER(re, gb);
    UPDATE_CACHE(re, gb);
    buf = GET_CACHE(re, gb);

    if (buf & 0xAA800000) {
        buf >>= 32 - 8;
        LAST_SKIP_BITS(re, gb, ff_interleaved_golomb_vlc_len[buf]);
        CLOSE_READER(re, gb);

        return ff_interleaved_se_golomb_vlc_code[buf];
    } else {
        int log;
        LAST_SKIP_BITS(re, gb, 8);
        UPDATE_CACHE(re, gb);
        buf |= 1 | (GET_CACHE(re, gb) >> 8);

        if ((buf & 0xAAAAAAAA) == 0)
            return INVALID_VLC;

        for (log = 31; (buf & 0x80000000) == 0; log--)
            buf = (buf << 2) - ((buf << log) >> (log - 1)) + (buf >> 30);

        LAST_SKIP_BITS(re, gb, 63 - 2 * log - 8);
        CLOSE_READER(re, gb);

        return (signed) (((((buf << log) >> log) - 1) ^ -(buf & 0x1)) + 1) >> 1;
    }
#endif
}

static inline int dirac_get_se_golomb(GetBitContext *gb)
{
    uint32_t ret = get_interleaved_ue_golomb(gb);

    if (ret) {
        int sign = -get_bits1(gb);
        ret = (ret ^ sign) - sign;
    }

    return ret;
}

/**
 * read unsigned golomb rice code (ffv1).
 */
static inline int get_ur_golomb(GetBitContext *gb, int k, int limit,
                                int esc_len)
{
    unsigned int buf;
    int log;

#if CACHED_BITSTREAM_READER
    buf = show_bits_long(gb, 32);

    log = av_log2(buf);

    if (log > 31 - limit) {
        buf >>= log - k;
        buf  += (30 - log) << k;
        skip_bits_long(gb, 32 + k - log);

        return buf;
    } else {
        skip_bits_long(gb, limit);
        buf = get_bits_long(gb, esc_len);

        return buf + limit - 1;
    }
#else
    OPEN_READER(re, gb);
    UPDATE_CACHE(re, gb);
    buf = GET_CACHE(re, gb);

    log = av_log2(buf);

    if (log > 31 - limit) {
        buf >>= log - k;
        buf  += (30U - log) << k;
        LAST_SKIP_BITS(re, gb, 32 + k - log);
        CLOSE_READER(re, gb);

        return buf;
    } else {
        LAST_SKIP_BITS(re, gb, limit);
        UPDATE_CACHE(re, gb);

        buf = SHOW_UBITS(re, gb, esc_len);

        LAST_SKIP_BITS(re, gb, esc_len);
        CLOSE_READER(re, gb);

        return buf + limit - 1;
    }
#endif
}

#ifdef TRACE

static inline int get_ue(GetBitContext *s, const char *file, const char *func,
                         int line)
{
    int show = show_bits(s, 24);
    int pos  = get_bits_count(s);
    int i    = get_ue_golomb(s);
    int len  = get_bits_count(s) - pos;
    int bits = show >> (24 - len);

    av_log(NULL, AV_LOG_DEBUG, "%5d %2d %3d ue  @%5d in %s %s:%d\n",
           bits, len, i, pos, file, func, line);

    return i;
}

static inline int get_se(GetBitContext *s, const char *file, const char *func,
                         int line)
{
    int show = show_bits(s, 24);
    int pos  = get_bits_count(s);
    int i    = get_se_golomb(s);
    int len  = get_bits_count(s) - pos;
    int bits = show >> (24 - len);

    av_log(NULL, AV_LOG_DEBUG, "%5d %2d %3d se  @%5d in %s %s:%d\n",
           bits, len, i, pos, file, func, line);

    return i;
}

static inline int get_te(GetBitContext *s, int r, char *file, const char *func,
                         int line)
{
    int show = show_bits(s, 24);
    int pos  = get_bits_count(s);
    int i    = get_te0_golomb(s, r);
    int len  = get_bits_count(s) - pos;
    int bits = show >> (24 - len);

    av_log(NULL, AV_LOG_DEBUG, "%5d %2d %3d te  @%5d in %s %s:%d\n",
           bits, len, i, pos, file, func, line);

    return i;
}

#define get_ue_golomb(a) get_ue(a, __FILE__, __func__, __LINE__)
#define get_se_golomb(a) get_se(a, __FILE__, __func__, __LINE__)
#define get_te_golomb(a, r)  get_te(a, r, __FILE__, __func__, __LINE__)
#define get_te0_golomb(a, r) get_te(a, r, __FILE__, __func__, __LINE__)

#endif /* TRACE */

#endif /* AVCODEC_GOLOMB_H */

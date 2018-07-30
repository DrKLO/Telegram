#ifndef FLAC__PRIVATE__MD5_H
#define FLAC__PRIVATE__MD5_H

/*
 * This is the header file for the MD5 message-digest algorithm.
 * The algorithm is due to Ron Rivest.  This code was
 * written by Colin Plumb in 1993, no copyright is claimed.
 * This code is in the public domain; do with it what you wish.
 *
 * Equivalent code is available from RSA Data Security, Inc.
 * This code has been tested against that, and is equivalent,
 * except that you don't need to include two pages of legalese
 * with every copy.
 *
 * To compute the message digest of a chunk of bytes, declare an
 * MD5Context structure, pass it to MD5Init, call MD5Update as
 * needed on buffers full of bytes, and then call MD5Final, which
 * will fill a supplied 16-byte array with the digest.
 *
 * Changed so as no longer to depend on Colin Plumb's `usual.h'
 * header definitions; now uses stuff from dpkg's config.h
 *  - Ian Jackson <ijackson@nyx.cs.du.edu>.
 * Still in the public domain.
 *
 * Josh Coalson: made some changes to integrate with libFLAC.
 * Still in the public domain, with no warranty.
 */

#include "FLAC/ordinals.h"

typedef union {
	FLAC__byte *p8;
	FLAC__int16 *p16;
	FLAC__int32 *p32;
} FLAC__multibyte;

typedef struct {
	FLAC__uint32 in[16];
	FLAC__uint32 buf[4];
	FLAC__uint32 bytes[2];
	FLAC__multibyte internal_buf;
	size_t capacity;
} FLAC__MD5Context;

void FLAC__MD5Init(FLAC__MD5Context *context);
void FLAC__MD5Final(FLAC__byte digest[16], FLAC__MD5Context *context);

FLAC__bool FLAC__MD5Accumulate(FLAC__MD5Context *ctx, const FLAC__int32 * const signal[], uint32_t channels, uint32_t samples, uint32_t bytes_per_sample);

#endif

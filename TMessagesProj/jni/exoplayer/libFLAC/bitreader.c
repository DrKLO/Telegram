/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2000-2009  Josh Coalson
 * Copyright (C) 2011-2016  Xiph.Org Foundation
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * - Neither the name of the Xiph.org Foundation nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifdef HAVE_CONFIG_H
#  include <config.h>
#endif

#include <stdlib.h>
#include <string.h>
#include "private/bitmath.h"
#include "private/bitreader.h"
#include "private/crc.h"
#include "private/macros.h"
#include "FLAC/assert.h"
#include "share/compat.h"
#include "share/endswap.h"

/* Things should be fastest when this matches the machine word size */
/* WATCHOUT: if you change this you must also change the following #defines down to COUNT_ZERO_MSBS2 below to match */
/* WATCHOUT: there are a few places where the code will not work unless brword is >= 32 bits wide */
/*           also, some sections currently only have fast versions for 4 or 8 bytes per word */

#if (ENABLE_64_BIT_WORDS == 0)

typedef FLAC__uint32 brword;
#define FLAC__BYTES_PER_WORD 4		/* sizeof brword */
#define FLAC__BITS_PER_WORD 32
#define FLAC__WORD_ALL_ONES ((FLAC__uint32)0xffffffff)
/* SWAP_BE_WORD_TO_HOST swaps bytes in a brword (which is always big-endian) if necessary to match host byte order */
#if WORDS_BIGENDIAN
#define SWAP_BE_WORD_TO_HOST(x) (x)
#else
#define SWAP_BE_WORD_TO_HOST(x) ENDSWAP_32(x)
#endif
/* counts the # of zero MSBs in a word */
#define COUNT_ZERO_MSBS(word) FLAC__clz_uint32(word)
#define COUNT_ZERO_MSBS2(word) FLAC__clz2_uint32(word)

#else

typedef FLAC__uint64 brword;
#define FLAC__BYTES_PER_WORD 8		/* sizeof brword */
#define FLAC__BITS_PER_WORD 64
#define FLAC__WORD_ALL_ONES ((FLAC__uint64)FLAC__U64L(0xffffffffffffffff))
/* SWAP_BE_WORD_TO_HOST swaps bytes in a brword (which is always big-endian) if necessary to match host byte order */
#if WORDS_BIGENDIAN
#define SWAP_BE_WORD_TO_HOST(x) (x)
#else
#define SWAP_BE_WORD_TO_HOST(x) ENDSWAP_64(x)
#endif
/* counts the # of zero MSBs in a word */
#define COUNT_ZERO_MSBS(word) FLAC__clz_uint64(word)
#define COUNT_ZERO_MSBS2(word) FLAC__clz2_uint64(word)

#endif

/*
 * This should be at least twice as large as the largest number of words
 * required to represent any 'number' (in any encoding) you are going to
 * read.  With FLAC this is on the order of maybe a few hundred bits.
 * If the buffer is smaller than that, the decoder won't be able to read
 * in a whole number that is in a variable length encoding (e.g. Rice).
 * But to be practical it should be at least 1K bytes.
 *
 * Increase this number to decrease the number of read callbacks, at the
 * expense of using more memory.  Or decrease for the reverse effect,
 * keeping in mind the limit from the first paragraph.  The optimal size
 * also depends on the CPU cache size and other factors; some twiddling
 * may be necessary to squeeze out the best performance.
 */
static const uint32_t FLAC__BITREADER_DEFAULT_CAPACITY = 65536u / FLAC__BITS_PER_WORD; /* in words */

struct FLAC__BitReader {
	/* any partially-consumed word at the head will stay right-justified as bits are consumed from the left */
	/* any incomplete word at the tail will be left-justified, and bytes from the read callback are added on the right */
	brword *buffer;
	uint32_t capacity; /* in words */
	uint32_t words; /* # of completed words in buffer */
	uint32_t bytes; /* # of bytes in incomplete word at buffer[words] */
	uint32_t consumed_words; /* #words ... */
	uint32_t consumed_bits; /* ... + (#bits of head word) already consumed from the front of buffer */
	uint32_t read_crc16; /* the running frame CRC */
	uint32_t crc16_align; /* the number of bits in the current consumed word that should not be CRC'd */
	FLAC__BitReaderReadCallback read_callback;
	void *client_data;
};

static inline void crc16_update_word_(FLAC__BitReader *br, brword word)
{
	register uint32_t crc = br->read_crc16;
#if FLAC__BYTES_PER_WORD == 4
	switch(br->crc16_align) {
		case  0: crc = FLAC__CRC16_UPDATE((uint32_t)(word >> 24), crc); /* Falls through. */
		case  8: crc = FLAC__CRC16_UPDATE((uint32_t)((word >> 16) & 0xff), crc); /* Falls through. */
		case 16: crc = FLAC__CRC16_UPDATE((uint32_t)((word >> 8) & 0xff), crc); /* Falls through. */
		case 24: br->read_crc16 = FLAC__CRC16_UPDATE((uint32_t)(word & 0xff), crc); /* Falls through. */
	}
#elif FLAC__BYTES_PER_WORD == 8
	switch(br->crc16_align) {
		case  0: crc = FLAC__CRC16_UPDATE((uint32_t)(word >> 56), crc); /* Falls through. */
		case  8: crc = FLAC__CRC16_UPDATE((uint32_t)((word >> 48) & 0xff), crc); /* Falls through. */
		case 16: crc = FLAC__CRC16_UPDATE((uint32_t)((word >> 40) & 0xff), crc); /* Falls through. */
		case 24: crc = FLAC__CRC16_UPDATE((uint32_t)((word >> 32) & 0xff), crc); /* Falls through. */
		case 32: crc = FLAC__CRC16_UPDATE((uint32_t)((word >> 24) & 0xff), crc); /* Falls through. */
		case 40: crc = FLAC__CRC16_UPDATE((uint32_t)((word >> 16) & 0xff), crc); /* Falls through. */
		case 48: crc = FLAC__CRC16_UPDATE((uint32_t)((word >> 8) & 0xff), crc); /* Falls through. */
		case 56: br->read_crc16 = FLAC__CRC16_UPDATE((uint32_t)(word & 0xff), crc);
	}
#else
	for( ; br->crc16_align < FLAC__BITS_PER_WORD; br->crc16_align += 8)
		crc = FLAC__CRC16_UPDATE((uint32_t)((word >> (FLAC__BITS_PER_WORD-8-br->crc16_align)) & 0xff), crc);
	br->read_crc16 = crc;
#endif
	br->crc16_align = 0;
}

static FLAC__bool bitreader_read_from_client_(FLAC__BitReader *br)
{
	uint32_t start, end;
	size_t bytes;
	FLAC__byte *target;

	/* first shift the unconsumed buffer data toward the front as much as possible */
	if(br->consumed_words > 0) {
		start = br->consumed_words;
		end = br->words + (br->bytes? 1:0);
		memmove(br->buffer, br->buffer+start, FLAC__BYTES_PER_WORD * (end - start));

		br->words -= start;
		br->consumed_words = 0;
	}

	/*
	 * set the target for reading, taking into account word alignment and endianness
	 */
	bytes = (br->capacity - br->words) * FLAC__BYTES_PER_WORD - br->bytes;
	if(bytes == 0)
		return false; /* no space left, buffer is too small; see note for FLAC__BITREADER_DEFAULT_CAPACITY  */
	target = ((FLAC__byte*)(br->buffer+br->words)) + br->bytes;

	/* before reading, if the existing reader looks like this (say brword is 32 bits wide)
	 *   bitstream :  11 22 33 44 55            br->words=1 br->bytes=1 (partial tail word is left-justified)
	 *   buffer[BE]:  11 22 33 44 55 ?? ?? ??   (shown layed out as bytes sequentially in memory)
	 *   buffer[LE]:  44 33 22 11 ?? ?? ?? 55   (?? being don't-care)
	 *                               ^^-------target, bytes=3
	 * on LE machines, have to byteswap the odd tail word so nothing is
	 * overwritten:
	 */
#if WORDS_BIGENDIAN
#else
	if(br->bytes)
		br->buffer[br->words] = SWAP_BE_WORD_TO_HOST(br->buffer[br->words]);
#endif

	/* now it looks like:
	 *   bitstream :  11 22 33 44 55            br->words=1 br->bytes=1
	 *   buffer[BE]:  11 22 33 44 55 ?? ?? ??
	 *   buffer[LE]:  44 33 22 11 55 ?? ?? ??
	 *                               ^^-------target, bytes=3
	 */

	/* read in the data; note that the callback may return a smaller number of bytes */
	if(!br->read_callback(target, &bytes, br->client_data))
		return false;

	/* after reading bytes 66 77 88 99 AA BB CC DD EE FF from the client:
	 *   bitstream :  11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF
	 *   buffer[BE]:  11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF ??
	 *   buffer[LE]:  44 33 22 11 55 66 77 88 99 AA BB CC DD EE FF ??
	 * now have to byteswap on LE machines:
	 */
#if WORDS_BIGENDIAN
#else
	end = (br->words*FLAC__BYTES_PER_WORD + br->bytes + (uint32_t)bytes + (FLAC__BYTES_PER_WORD-1)) / FLAC__BYTES_PER_WORD;
	for(start = br->words; start < end; start++)
		br->buffer[start] = SWAP_BE_WORD_TO_HOST(br->buffer[start]);
#endif

	/* now it looks like:
	 *   bitstream :  11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF
	 *   buffer[BE]:  11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF ??
	 *   buffer[LE]:  44 33 22 11 88 77 66 55 CC BB AA 99 ?? FF EE DD
	 * finally we'll update the reader values:
	 */
	end = br->words*FLAC__BYTES_PER_WORD + br->bytes + (uint32_t)bytes;
	br->words = end / FLAC__BYTES_PER_WORD;
	br->bytes = end % FLAC__BYTES_PER_WORD;

	return true;
}

/***********************************************************************
 *
 * Class constructor/destructor
 *
 ***********************************************************************/

FLAC__BitReader *FLAC__bitreader_new(void)
{
	FLAC__BitReader *br = calloc(1, sizeof(FLAC__BitReader));

	/* calloc() implies:
		memset(br, 0, sizeof(FLAC__BitReader));
		br->buffer = 0;
		br->capacity = 0;
		br->words = br->bytes = 0;
		br->consumed_words = br->consumed_bits = 0;
		br->read_callback = 0;
		br->client_data = 0;
	*/
	return br;
}

void FLAC__bitreader_delete(FLAC__BitReader *br)
{
	FLAC__ASSERT(0 != br);

	FLAC__bitreader_free(br);
	free(br);
}

/***********************************************************************
 *
 * Public class methods
 *
 ***********************************************************************/

FLAC__bool FLAC__bitreader_init(FLAC__BitReader *br, FLAC__BitReaderReadCallback rcb, void *cd)
{
	FLAC__ASSERT(0 != br);

	br->words = br->bytes = 0;
	br->consumed_words = br->consumed_bits = 0;
	br->capacity = FLAC__BITREADER_DEFAULT_CAPACITY;
	br->buffer = malloc(sizeof(brword) * br->capacity);
	if(br->buffer == 0)
		return false;
	br->read_callback = rcb;
	br->client_data = cd;

	return true;
}

void FLAC__bitreader_free(FLAC__BitReader *br)
{
	FLAC__ASSERT(0 != br);

	if(0 != br->buffer)
		free(br->buffer);
	br->buffer = 0;
	br->capacity = 0;
	br->words = br->bytes = 0;
	br->consumed_words = br->consumed_bits = 0;
	br->read_callback = 0;
	br->client_data = 0;
}

FLAC__bool FLAC__bitreader_clear(FLAC__BitReader *br)
{
	br->words = br->bytes = 0;
	br->consumed_words = br->consumed_bits = 0;
	return true;
}

void FLAC__bitreader_dump(const FLAC__BitReader *br, FILE *out)
{
	uint32_t i, j;
	if(br == 0) {
		fprintf(out, "bitreader is NULL\n");
	}
	else {
		fprintf(out, "bitreader: capacity=%u words=%u bytes=%u consumed: words=%u, bits=%u\n", br->capacity, br->words, br->bytes, br->consumed_words, br->consumed_bits);

		for(i = 0; i < br->words; i++) {
			fprintf(out, "%08X: ", i);
			for(j = 0; j < FLAC__BITS_PER_WORD; j++)
				if(i < br->consumed_words || (i == br->consumed_words && j < br->consumed_bits))
					fprintf(out, ".");
				else
					fprintf(out, "%01d", br->buffer[i] & ((brword)1 << (FLAC__BITS_PER_WORD-j-1)) ? 1:0);
			fprintf(out, "\n");
		}
		if(br->bytes > 0) {
			fprintf(out, "%08X: ", i);
			for(j = 0; j < br->bytes*8; j++)
				if(i < br->consumed_words || (i == br->consumed_words && j < br->consumed_bits))
					fprintf(out, ".");
				else
					fprintf(out, "%01d", br->buffer[i] & ((brword)1 << (br->bytes*8-j-1)) ? 1:0);
			fprintf(out, "\n");
		}
	}
}

void FLAC__bitreader_reset_read_crc16(FLAC__BitReader *br, FLAC__uint16 seed)
{
	FLAC__ASSERT(0 != br);
	FLAC__ASSERT(0 != br->buffer);
	FLAC__ASSERT((br->consumed_bits & 7) == 0);

	br->read_crc16 = (uint32_t)seed;
	br->crc16_align = br->consumed_bits;
}

FLAC__uint16 FLAC__bitreader_get_read_crc16(FLAC__BitReader *br)
{
	FLAC__ASSERT(0 != br);
	FLAC__ASSERT(0 != br->buffer);
	FLAC__ASSERT((br->consumed_bits & 7) == 0);
	FLAC__ASSERT(br->crc16_align <= br->consumed_bits);

	/* CRC any tail bytes in a partially-consumed word */
	if(br->consumed_bits) {
		const brword tail = br->buffer[br->consumed_words];
		for( ; br->crc16_align < br->consumed_bits; br->crc16_align += 8)
			br->read_crc16 = FLAC__CRC16_UPDATE((uint32_t)((tail >> (FLAC__BITS_PER_WORD-8-br->crc16_align)) & 0xff), br->read_crc16);
	}
	return br->read_crc16;
}

inline FLAC__bool FLAC__bitreader_is_consumed_byte_aligned(const FLAC__BitReader *br)
{
	return ((br->consumed_bits & 7) == 0);
}

inline uint32_t FLAC__bitreader_bits_left_for_byte_alignment(const FLAC__BitReader *br)
{
	return 8 - (br->consumed_bits & 7);
}

inline uint32_t FLAC__bitreader_get_input_bits_unconsumed(const FLAC__BitReader *br)
{
	return (br->words-br->consumed_words)*FLAC__BITS_PER_WORD + br->bytes*8 - br->consumed_bits;
}

FLAC__bool FLAC__bitreader_read_raw_uint32(FLAC__BitReader *br, FLAC__uint32 *val, uint32_t bits)
{
	FLAC__ASSERT(0 != br);
	FLAC__ASSERT(0 != br->buffer);

	FLAC__ASSERT(bits <= 32);
	FLAC__ASSERT((br->capacity*FLAC__BITS_PER_WORD) * 2 >= bits);
	FLAC__ASSERT(br->consumed_words <= br->words);

	/* WATCHOUT: code does not work with <32bit words; we can make things much faster with this assertion */
	FLAC__ASSERT(FLAC__BITS_PER_WORD >= 32);

	if(bits == 0) { /* OPT: investigate if this can ever happen, maybe change to assertion */
		*val = 0;
		return true;
	}

	while((br->words-br->consumed_words)*FLAC__BITS_PER_WORD + br->bytes*8 - br->consumed_bits < bits) {
		if(!bitreader_read_from_client_(br))
			return false;
	}
	if(br->consumed_words < br->words) { /* if we've not consumed up to a partial tail word... */
		/* OPT: taking out the consumed_bits==0 "else" case below might make things faster if less code allows the compiler to inline this function */
		if(br->consumed_bits) {
			/* this also works when consumed_bits==0, it's just a little slower than necessary for that case */
			const uint32_t n = FLAC__BITS_PER_WORD - br->consumed_bits;
			const brword word = br->buffer[br->consumed_words];
			if(bits < n) {
				*val = (FLAC__uint32)((word & (FLAC__WORD_ALL_ONES >> br->consumed_bits)) >> (n-bits)); /* The result has <= 32 non-zero bits */
				br->consumed_bits += bits;
				return true;
			}
			/* (FLAC__BITS_PER_WORD - br->consumed_bits <= bits) ==> (FLAC__WORD_ALL_ONES >> br->consumed_bits) has no more than 'bits' non-zero bits */
			*val = (FLAC__uint32)(word & (FLAC__WORD_ALL_ONES >> br->consumed_bits));
			bits -= n;
			crc16_update_word_(br, word);
			br->consumed_words++;
			br->consumed_bits = 0;
			if(bits) { /* if there are still bits left to read, there have to be less than 32 so they will all be in the next word */
				*val <<= bits;
				*val |= (FLAC__uint32)(br->buffer[br->consumed_words] >> (FLAC__BITS_PER_WORD-bits));
				br->consumed_bits = bits;
			}
			return true;
		}
		else { /* br->consumed_bits == 0 */
			const brword word = br->buffer[br->consumed_words];
			if(bits < FLAC__BITS_PER_WORD) {
				*val = (FLAC__uint32)(word >> (FLAC__BITS_PER_WORD-bits));
				br->consumed_bits = bits;
				return true;
			}
			/* at this point bits == FLAC__BITS_PER_WORD == 32; because of previous assertions, it can't be larger */
			*val = (FLAC__uint32)word;
			crc16_update_word_(br, word);
			br->consumed_words++;
			return true;
		}
	}
	else {
		/* in this case we're starting our read at a partial tail word;
		 * the reader has guaranteed that we have at least 'bits' bits
		 * available to read, which makes this case simpler.
		 */
		/* OPT: taking out the consumed_bits==0 "else" case below might make things faster if less code allows the compiler to inline this function */
		if(br->consumed_bits) {
			/* this also works when consumed_bits==0, it's just a little slower than necessary for that case */
			FLAC__ASSERT(br->consumed_bits + bits <= br->bytes*8);
			*val = (FLAC__uint32)((br->buffer[br->consumed_words] & (FLAC__WORD_ALL_ONES >> br->consumed_bits)) >> (FLAC__BITS_PER_WORD-br->consumed_bits-bits));
			br->consumed_bits += bits;
			return true;
		}
		else {
			*val = (FLAC__uint32)(br->buffer[br->consumed_words] >> (FLAC__BITS_PER_WORD-bits));
			br->consumed_bits += bits;
			return true;
		}
	}
}

FLAC__bool FLAC__bitreader_read_raw_int32(FLAC__BitReader *br, FLAC__int32 *val, uint32_t bits)
{
	FLAC__uint32 uval, mask;
	/* OPT: inline raw uint32 code here, or make into a macro if possible in the .h file */
	if(!FLAC__bitreader_read_raw_uint32(br, &uval, bits))
		return false;
	/* sign-extend *val assuming it is currently bits wide. */
	/* From: https://graphics.stanford.edu/~seander/bithacks.html#FixedSignExtend */
	mask = 1u << (bits - 1);
	*val = (uval ^ mask) - mask;
	return true;
}

FLAC__bool FLAC__bitreader_read_raw_uint64(FLAC__BitReader *br, FLAC__uint64 *val, uint32_t bits)
{
	FLAC__uint32 hi, lo;

	if(bits > 32) {
		if(!FLAC__bitreader_read_raw_uint32(br, &hi, bits-32))
			return false;
		if(!FLAC__bitreader_read_raw_uint32(br, &lo, 32))
			return false;
		*val = hi;
		*val <<= 32;
		*val |= lo;
	}
	else {
		if(!FLAC__bitreader_read_raw_uint32(br, &lo, bits))
			return false;
		*val = lo;
	}
	return true;
}

inline FLAC__bool FLAC__bitreader_read_uint32_little_endian(FLAC__BitReader *br, FLAC__uint32 *val)
{
	FLAC__uint32 x8, x32 = 0;

	/* this doesn't need to be that fast as currently it is only used for vorbis comments */

	if(!FLAC__bitreader_read_raw_uint32(br, &x32, 8))
		return false;

	if(!FLAC__bitreader_read_raw_uint32(br, &x8, 8))
		return false;
	x32 |= (x8 << 8);

	if(!FLAC__bitreader_read_raw_uint32(br, &x8, 8))
		return false;
	x32 |= (x8 << 16);

	if(!FLAC__bitreader_read_raw_uint32(br, &x8, 8))
		return false;
	x32 |= (x8 << 24);

	*val = x32;
	return true;
}

FLAC__bool FLAC__bitreader_skip_bits_no_crc(FLAC__BitReader *br, uint32_t bits)
{
	/*
	 * OPT: a faster implementation is possible but probably not that useful
	 * since this is only called a couple of times in the metadata readers.
	 */
	FLAC__ASSERT(0 != br);
	FLAC__ASSERT(0 != br->buffer);

	if(bits > 0) {
		const uint32_t n = br->consumed_bits & 7;
		uint32_t m;
		FLAC__uint32 x;

		if(n != 0) {
			m = flac_min(8-n, bits);
			if(!FLAC__bitreader_read_raw_uint32(br, &x, m))
				return false;
			bits -= m;
		}
		m = bits / 8;
		if(m > 0) {
			if(!FLAC__bitreader_skip_byte_block_aligned_no_crc(br, m))
				return false;
			bits %= 8;
		}
		if(bits > 0) {
			if(!FLAC__bitreader_read_raw_uint32(br, &x, bits))
				return false;
		}
	}

	return true;
}

FLAC__bool FLAC__bitreader_skip_byte_block_aligned_no_crc(FLAC__BitReader *br, uint32_t nvals)
{
	FLAC__uint32 x;

	FLAC__ASSERT(0 != br);
	FLAC__ASSERT(0 != br->buffer);
	FLAC__ASSERT(FLAC__bitreader_is_consumed_byte_aligned(br));

	/* step 1: skip over partial head word to get word aligned */
	while(nvals && br->consumed_bits) { /* i.e. run until we read 'nvals' bytes or we hit the end of the head word */
		if(!FLAC__bitreader_read_raw_uint32(br, &x, 8))
			return false;
		nvals--;
	}
	if(0 == nvals)
		return true;
	/* step 2: skip whole words in chunks */
	while(nvals >= FLAC__BYTES_PER_WORD) {
		if(br->consumed_words < br->words) {
			br->consumed_words++;
			nvals -= FLAC__BYTES_PER_WORD;
		}
		else if(!bitreader_read_from_client_(br))
			return false;
	}
	/* step 3: skip any remainder from partial tail bytes */
	while(nvals) {
		if(!FLAC__bitreader_read_raw_uint32(br, &x, 8))
			return false;
		nvals--;
	}

	return true;
}

FLAC__bool FLAC__bitreader_read_byte_block_aligned_no_crc(FLAC__BitReader *br, FLAC__byte *val, uint32_t nvals)
{
	FLAC__uint32 x;

	FLAC__ASSERT(0 != br);
	FLAC__ASSERT(0 != br->buffer);
	FLAC__ASSERT(FLAC__bitreader_is_consumed_byte_aligned(br));

	/* step 1: read from partial head word to get word aligned */
	while(nvals && br->consumed_bits) { /* i.e. run until we read 'nvals' bytes or we hit the end of the head word */
		if(!FLAC__bitreader_read_raw_uint32(br, &x, 8))
			return false;
		*val++ = (FLAC__byte)x;
		nvals--;
	}
	if(0 == nvals)
		return true;
	/* step 2: read whole words in chunks */
	while(nvals >= FLAC__BYTES_PER_WORD) {
		if(br->consumed_words < br->words) {
			const brword word = br->buffer[br->consumed_words++];
#if FLAC__BYTES_PER_WORD == 4
			val[0] = (FLAC__byte)(word >> 24);
			val[1] = (FLAC__byte)(word >> 16);
			val[2] = (FLAC__byte)(word >> 8);
			val[3] = (FLAC__byte)word;
#elif FLAC__BYTES_PER_WORD == 8
			val[0] = (FLAC__byte)(word >> 56);
			val[1] = (FLAC__byte)(word >> 48);
			val[2] = (FLAC__byte)(word >> 40);
			val[3] = (FLAC__byte)(word >> 32);
			val[4] = (FLAC__byte)(word >> 24);
			val[5] = (FLAC__byte)(word >> 16);
			val[6] = (FLAC__byte)(word >> 8);
			val[7] = (FLAC__byte)word;
#else
			for(x = 0; x < FLAC__BYTES_PER_WORD; x++)
				val[x] = (FLAC__byte)(word >> (8*(FLAC__BYTES_PER_WORD-x-1)));
#endif
			val += FLAC__BYTES_PER_WORD;
			nvals -= FLAC__BYTES_PER_WORD;
		}
		else if(!bitreader_read_from_client_(br))
			return false;
	}
	/* step 3: read any remainder from partial tail bytes */
	while(nvals) {
		if(!FLAC__bitreader_read_raw_uint32(br, &x, 8))
			return false;
		*val++ = (FLAC__byte)x;
		nvals--;
	}

	return true;
}

FLAC__bool FLAC__bitreader_read_unary_unsigned(FLAC__BitReader *br, uint32_t *val)
#if 0 /* slow but readable version */
{
	uint32_t bit;

	FLAC__ASSERT(0 != br);
	FLAC__ASSERT(0 != br->buffer);

	*val = 0;
	while(1) {
		if(!FLAC__bitreader_read_bit(br, &bit))
			return false;
		if(bit)
			break;
		else
			*val++;
	}
	return true;
}
#else
{
	uint32_t i;

	FLAC__ASSERT(0 != br);
	FLAC__ASSERT(0 != br->buffer);

	*val = 0;
	while(1) {
		while(br->consumed_words < br->words) { /* if we've not consumed up to a partial tail word... */
			brword b = br->buffer[br->consumed_words] << br->consumed_bits;
			if(b) {
				i = COUNT_ZERO_MSBS(b);
				*val += i;
				i++;
				br->consumed_bits += i;
				if(br->consumed_bits >= FLAC__BITS_PER_WORD) { /* faster way of testing if(br->consumed_bits == FLAC__BITS_PER_WORD) */
					crc16_update_word_(br, br->buffer[br->consumed_words]);
					br->consumed_words++;
					br->consumed_bits = 0;
				}
				return true;
			}
			else {
				*val += FLAC__BITS_PER_WORD - br->consumed_bits;
				crc16_update_word_(br, br->buffer[br->consumed_words]);
				br->consumed_words++;
				br->consumed_bits = 0;
				/* didn't find stop bit yet, have to keep going... */
			}
		}
		/* at this point we've eaten up all the whole words; have to try
		 * reading through any tail bytes before calling the read callback.
		 * this is a repeat of the above logic adjusted for the fact we
		 * don't have a whole word.  note though if the client is feeding
		 * us data a byte at a time (unlikely), br->consumed_bits may not
		 * be zero.
		 */
		if(br->bytes*8 > br->consumed_bits) {
			const uint32_t end = br->bytes * 8;
			brword b = (br->buffer[br->consumed_words] & (FLAC__WORD_ALL_ONES << (FLAC__BITS_PER_WORD-end))) << br->consumed_bits;
			if(b) {
				i = COUNT_ZERO_MSBS(b);
				*val += i;
				i++;
				br->consumed_bits += i;
				FLAC__ASSERT(br->consumed_bits < FLAC__BITS_PER_WORD);
				return true;
			}
			else {
				*val += end - br->consumed_bits;
				br->consumed_bits = end;
				FLAC__ASSERT(br->consumed_bits < FLAC__BITS_PER_WORD);
				/* didn't find stop bit yet, have to keep going... */
			}
		}
		if(!bitreader_read_from_client_(br))
			return false;
	}
}
#endif

FLAC__bool FLAC__bitreader_read_rice_signed(FLAC__BitReader *br, int *val, uint32_t parameter)
{
	FLAC__uint32 lsbs = 0, msbs = 0;
	uint32_t uval;

	FLAC__ASSERT(0 != br);
	FLAC__ASSERT(0 != br->buffer);
	FLAC__ASSERT(parameter <= 31);

	/* read the unary MSBs and end bit */
	if(!FLAC__bitreader_read_unary_unsigned(br, &msbs))
		return false;

	/* read the binary LSBs */
	if(!FLAC__bitreader_read_raw_uint32(br, &lsbs, parameter))
		return false;

	/* compose the value */
	uval = (msbs << parameter) | lsbs;
	if(uval & 1)
		*val = -((int)(uval >> 1)) - 1;
	else
		*val = (int)(uval >> 1);

	return true;
}

/* this is by far the most heavily used reader call.  it ain't pretty but it's fast */
FLAC__bool FLAC__bitreader_read_rice_signed_block(FLAC__BitReader *br, int vals[], uint32_t nvals, uint32_t parameter)
{
	/* try and get br->consumed_words and br->consumed_bits into register;
	 * must remember to flush them back to *br before calling other
	 * bitreader functions that use them, and before returning */
	uint32_t cwords, words, lsbs, msbs, x, y;
	uint32_t ucbits; /* keep track of the number of unconsumed bits in word */
	brword b;
	int *val, *end;

	FLAC__ASSERT(0 != br);
	FLAC__ASSERT(0 != br->buffer);
	/* WATCHOUT: code does not work with <32bit words; we can make things much faster with this assertion */
	FLAC__ASSERT(FLAC__BITS_PER_WORD >= 32);
	FLAC__ASSERT(parameter < 32);
	/* the above two asserts also guarantee that the binary part never straddles more than 2 words, so we don't have to loop to read it */

	val = vals;
	end = vals + nvals;

	if(parameter == 0) {
		while(val < end) {
			/* read the unary MSBs and end bit */
			if(!FLAC__bitreader_read_unary_unsigned(br, &msbs))
				return false;

			*val++ = (int)(msbs >> 1) ^ -(int)(msbs & 1);
		}

		return true;
	}

	FLAC__ASSERT(parameter > 0);

	cwords = br->consumed_words;
	words = br->words;

	/* if we've not consumed up to a partial tail word... */
	if(cwords >= words) {
		x = 0;
		goto process_tail;
	}

	ucbits = FLAC__BITS_PER_WORD - br->consumed_bits;
	b = br->buffer[cwords] << br->consumed_bits;  /* keep unconsumed bits aligned to left */

	while(val < end) {
		/* read the unary MSBs and end bit */
		x = y = COUNT_ZERO_MSBS2(b);
		if(x == FLAC__BITS_PER_WORD) {
			x = ucbits;
			do {
				/* didn't find stop bit yet, have to keep going... */
				crc16_update_word_(br, br->buffer[cwords++]);
				if (cwords >= words)
					goto incomplete_msbs;
				b = br->buffer[cwords];
				y = COUNT_ZERO_MSBS2(b);
				x += y;
			} while(y == FLAC__BITS_PER_WORD);
		}
		b <<= y;
		b <<= 1; /* account for stop bit */
		ucbits = (ucbits - x - 1) % FLAC__BITS_PER_WORD;
		msbs = x;

		/* read the binary LSBs */
		x = (FLAC__uint32)(b >> (FLAC__BITS_PER_WORD - parameter)); /* parameter < 32, so we can cast to 32-bit uint32_t */
		if(parameter <= ucbits) {
			ucbits -= parameter;
			b <<= parameter;
		} else {
			/* there are still bits left to read, they will all be in the next word */
			crc16_update_word_(br, br->buffer[cwords++]);
			if (cwords >= words)
				goto incomplete_lsbs;
			b = br->buffer[cwords];
			ucbits += FLAC__BITS_PER_WORD - parameter;
			x |= (FLAC__uint32)(b >> ucbits);
			b <<= FLAC__BITS_PER_WORD - ucbits;
		}
		lsbs = x;

		/* compose the value */
		x = (msbs << parameter) | lsbs;
		*val++ = (int)(x >> 1) ^ -(int)(x & 1);

		continue;

		/* at this point we've eaten up all the whole words */
process_tail:
		do {
			if(0) {
incomplete_msbs:
				br->consumed_bits = 0;
				br->consumed_words = cwords;
			}

			/* read the unary MSBs and end bit */
			if(!FLAC__bitreader_read_unary_unsigned(br, &msbs))
				return false;
			msbs += x;
			x = ucbits = 0;

			if(0) {
incomplete_lsbs:
				br->consumed_bits = 0;
				br->consumed_words = cwords;
			}

			/* read the binary LSBs */
			if(!FLAC__bitreader_read_raw_uint32(br, &lsbs, parameter - ucbits))
				return false;
			lsbs = x | lsbs;

			/* compose the value */
			x = (msbs << parameter) | lsbs;
			*val++ = (int)(x >> 1) ^ -(int)(x & 1);
			x = 0;

			cwords = br->consumed_words;
			words = br->words;
			ucbits = FLAC__BITS_PER_WORD - br->consumed_bits;
			b = br->buffer[cwords] << br->consumed_bits;
		} while(cwords >= words && val < end);
	}

	if(ucbits == 0 && cwords < words) {
		/* don't leave the head word with no unconsumed bits */
		crc16_update_word_(br, br->buffer[cwords++]);
		ucbits = FLAC__BITS_PER_WORD;
	}

	br->consumed_bits = FLAC__BITS_PER_WORD - ucbits;
	br->consumed_words = cwords;

	return true;
}

#if 0 /* UNUSED */
FLAC__bool FLAC__bitreader_read_golomb_signed(FLAC__BitReader *br, int *val, uint32_t parameter)
{
	FLAC__uint32 lsbs = 0, msbs = 0;
	uint32_t bit, uval, k;

	FLAC__ASSERT(0 != br);
	FLAC__ASSERT(0 != br->buffer);

	k = FLAC__bitmath_ilog2(parameter);

	/* read the unary MSBs and end bit */
	if(!FLAC__bitreader_read_unary_unsigned(br, &msbs))
		return false;

	/* read the binary LSBs */
	if(!FLAC__bitreader_read_raw_uint32(br, &lsbs, k))
		return false;

	if(parameter == 1u<<k) {
		/* compose the value */
		uval = (msbs << k) | lsbs;
	}
	else {
		uint32_t d = (1 << (k+1)) - parameter;
		if(lsbs >= d) {
			if(!FLAC__bitreader_read_bit(br, &bit))
				return false;
			lsbs <<= 1;
			lsbs |= bit;
			lsbs -= d;
		}
		/* compose the value */
		uval = msbs * parameter + lsbs;
	}

	/* unfold uint32_t to signed */
	if(uval & 1)
		*val = -((int)(uval >> 1)) - 1;
	else
		*val = (int)(uval >> 1);

	return true;
}

FLAC__bool FLAC__bitreader_read_golomb_unsigned(FLAC__BitReader *br, uint32_t *val, uint32_t parameter)
{
	FLAC__uint32 lsbs, msbs = 0;
	uint32_t bit, k;

	FLAC__ASSERT(0 != br);
	FLAC__ASSERT(0 != br->buffer);

	k = FLAC__bitmath_ilog2(parameter);

	/* read the unary MSBs and end bit */
	if(!FLAC__bitreader_read_unary_unsigned(br, &msbs))
		return false;

	/* read the binary LSBs */
	if(!FLAC__bitreader_read_raw_uint32(br, &lsbs, k))
		return false;

	if(parameter == 1u<<k) {
		/* compose the value */
		*val = (msbs << k) | lsbs;
	}
	else {
		uint32_t d = (1 << (k+1)) - parameter;
		if(lsbs >= d) {
			if(!FLAC__bitreader_read_bit(br, &bit))
				return false;
			lsbs <<= 1;
			lsbs |= bit;
			lsbs -= d;
		}
		/* compose the value */
		*val = msbs * parameter + lsbs;
	}

	return true;
}
#endif /* UNUSED */

/* on return, if *val == 0xffffffff then the utf-8 sequence was invalid, but the return value will be true */
FLAC__bool FLAC__bitreader_read_utf8_uint32(FLAC__BitReader *br, FLAC__uint32 *val, FLAC__byte *raw, uint32_t *rawlen)
{
	FLAC__uint32 v = 0;
	FLAC__uint32 x;
	uint32_t i;

	if(!FLAC__bitreader_read_raw_uint32(br, &x, 8))
		return false;
	if(raw)
		raw[(*rawlen)++] = (FLAC__byte)x;
	if(!(x & 0x80)) { /* 0xxxxxxx */
		v = x;
		i = 0;
	}
	else if(x & 0xC0 && !(x & 0x20)) { /* 110xxxxx */
		v = x & 0x1F;
		i = 1;
	}
	else if(x & 0xE0 && !(x & 0x10)) { /* 1110xxxx */
		v = x & 0x0F;
		i = 2;
	}
	else if(x & 0xF0 && !(x & 0x08)) { /* 11110xxx */
		v = x & 0x07;
		i = 3;
	}
	else if(x & 0xF8 && !(x & 0x04)) { /* 111110xx */
		v = x & 0x03;
		i = 4;
	}
	else if(x & 0xFC && !(x & 0x02)) { /* 1111110x */
		v = x & 0x01;
		i = 5;
	}
	else {
		*val = 0xffffffff;
		return true;
	}
	for( ; i; i--) {
		if(!FLAC__bitreader_read_raw_uint32(br, &x, 8))
			return false;
		if(raw)
			raw[(*rawlen)++] = (FLAC__byte)x;
		if(!(x & 0x80) || (x & 0x40)) { /* 10xxxxxx */
			*val = 0xffffffff;
			return true;
		}
		v <<= 6;
		v |= (x & 0x3F);
	}
	*val = v;
	return true;
}

/* on return, if *val == 0xffffffffffffffff then the utf-8 sequence was invalid, but the return value will be true */
FLAC__bool FLAC__bitreader_read_utf8_uint64(FLAC__BitReader *br, FLAC__uint64 *val, FLAC__byte *raw, uint32_t *rawlen)
{
	FLAC__uint64 v = 0;
	FLAC__uint32 x;
	uint32_t i;

	if(!FLAC__bitreader_read_raw_uint32(br, &x, 8))
		return false;
	if(raw)
		raw[(*rawlen)++] = (FLAC__byte)x;
	if(!(x & 0x80)) { /* 0xxxxxxx */
		v = x;
		i = 0;
	}
	else if(x & 0xC0 && !(x & 0x20)) { /* 110xxxxx */
		v = x & 0x1F;
		i = 1;
	}
	else if(x & 0xE0 && !(x & 0x10)) { /* 1110xxxx */
		v = x & 0x0F;
		i = 2;
	}
	else if(x & 0xF0 && !(x & 0x08)) { /* 11110xxx */
		v = x & 0x07;
		i = 3;
	}
	else if(x & 0xF8 && !(x & 0x04)) { /* 111110xx */
		v = x & 0x03;
		i = 4;
	}
	else if(x & 0xFC && !(x & 0x02)) { /* 1111110x */
		v = x & 0x01;
		i = 5;
	}
	else if(x & 0xFE && !(x & 0x01)) { /* 11111110 */
		v = 0;
		i = 6;
	}
	else {
		*val = FLAC__U64L(0xffffffffffffffff);
		return true;
	}
	for( ; i; i--) {
		if(!FLAC__bitreader_read_raw_uint32(br, &x, 8))
			return false;
		if(raw)
			raw[(*rawlen)++] = (FLAC__byte)x;
		if(!(x & 0x80) || (x & 0x40)) { /* 10xxxxxx */
			*val = FLAC__U64L(0xffffffffffffffff);
			return true;
		}
		v <<= 6;
		v |= (x & 0x3F);
	}
	*val = v;
	return true;
}

/* These functions are declared inline in this file but are also callable as
 * externs from elsewhere.
 * According to the C99 spec, section 6.7.4, simply providing a function
 * prototype in a header file without 'inline' and making the function inline
 * in this file should be sufficient.
 * Unfortunately, the Microsoft VS compiler doesn't pick them up externally. To
 * fix that we add extern declarations here.
 */
extern FLAC__bool FLAC__bitreader_is_consumed_byte_aligned(const FLAC__BitReader *br);
extern uint32_t FLAC__bitreader_bits_left_for_byte_alignment(const FLAC__BitReader *br);
extern uint32_t FLAC__bitreader_get_input_bits_unconsumed(const FLAC__BitReader *br);
extern FLAC__bool FLAC__bitreader_read_uint32_little_endian(FLAC__BitReader *br, FLAC__uint32 *val);

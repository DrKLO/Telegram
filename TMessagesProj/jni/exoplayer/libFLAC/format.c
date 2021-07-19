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

#include <stdio.h>
#include <stdlib.h> /* for qsort() */
#include <string.h> /* for memset() */
#include "FLAC/assert.h"
#include "FLAC/format.h"
#include "share/alloc.h"
#include "share/compat.h"
#include "private/format.h"
#include "private/macros.h"

/* PACKAGE_VERSION should come from configure */
#define PACKAGE_VERSION "1.3.2"
FLAC_API const char *FLAC__VERSION_STRING = PACKAGE_VERSION;

FLAC_API const char *FLAC__VENDOR_STRING = "reference libFLAC " PACKAGE_VERSION " 20170101";

FLAC_API const FLAC__byte FLAC__STREAM_SYNC_STRING[4] = { 'f','L','a','C' };
FLAC_API const uint32_t FLAC__STREAM_SYNC = 0x664C6143;
FLAC_API const uint32_t FLAC__STREAM_SYNC_LEN = 32; /* bits */

FLAC_API const uint32_t FLAC__STREAM_METADATA_STREAMINFO_MIN_BLOCK_SIZE_LEN = 16; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_STREAMINFO_MAX_BLOCK_SIZE_LEN = 16; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_STREAMINFO_MIN_FRAME_SIZE_LEN = 24; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_STREAMINFO_MAX_FRAME_SIZE_LEN = 24; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_STREAMINFO_SAMPLE_RATE_LEN = 20; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_STREAMINFO_CHANNELS_LEN = 3; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_STREAMINFO_BITS_PER_SAMPLE_LEN = 5; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_STREAMINFO_TOTAL_SAMPLES_LEN = 36; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_STREAMINFO_MD5SUM_LEN = 128; /* bits */

FLAC_API const uint32_t FLAC__STREAM_METADATA_APPLICATION_ID_LEN = 32; /* bits */

FLAC_API const uint32_t FLAC__STREAM_METADATA_SEEKPOINT_SAMPLE_NUMBER_LEN = 64; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_SEEKPOINT_STREAM_OFFSET_LEN = 64; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_SEEKPOINT_FRAME_SAMPLES_LEN = 16; /* bits */

FLAC_API const FLAC__uint64 FLAC__STREAM_METADATA_SEEKPOINT_PLACEHOLDER = FLAC__U64L(0xffffffffffffffff);

FLAC_API const uint32_t FLAC__STREAM_METADATA_VORBIS_COMMENT_ENTRY_LENGTH_LEN = 32; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_VORBIS_COMMENT_NUM_COMMENTS_LEN = 32; /* bits */

FLAC_API const uint32_t FLAC__STREAM_METADATA_CUESHEET_INDEX_OFFSET_LEN = 64; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_CUESHEET_INDEX_NUMBER_LEN = 8; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_CUESHEET_INDEX_RESERVED_LEN = 3*8; /* bits */

FLAC_API const uint32_t FLAC__STREAM_METADATA_CUESHEET_TRACK_OFFSET_LEN = 64; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_CUESHEET_TRACK_NUMBER_LEN = 8; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_CUESHEET_TRACK_ISRC_LEN = 12*8; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_CUESHEET_TRACK_TYPE_LEN = 1; /* bit */
FLAC_API const uint32_t FLAC__STREAM_METADATA_CUESHEET_TRACK_PRE_EMPHASIS_LEN = 1; /* bit */
FLAC_API const uint32_t FLAC__STREAM_METADATA_CUESHEET_TRACK_RESERVED_LEN = 6+13*8; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_CUESHEET_TRACK_NUM_INDICES_LEN = 8; /* bits */

FLAC_API const uint32_t FLAC__STREAM_METADATA_CUESHEET_MEDIA_CATALOG_NUMBER_LEN = 128*8; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_CUESHEET_LEAD_IN_LEN = 64; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_CUESHEET_IS_CD_LEN = 1; /* bit */
FLAC_API const uint32_t FLAC__STREAM_METADATA_CUESHEET_RESERVED_LEN = 7+258*8; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_CUESHEET_NUM_TRACKS_LEN = 8; /* bits */

FLAC_API const uint32_t FLAC__STREAM_METADATA_PICTURE_TYPE_LEN = 32; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_PICTURE_MIME_TYPE_LENGTH_LEN = 32; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_PICTURE_DESCRIPTION_LENGTH_LEN = 32; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_PICTURE_WIDTH_LEN = 32; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_PICTURE_HEIGHT_LEN = 32; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_PICTURE_DEPTH_LEN = 32; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_PICTURE_COLORS_LEN = 32; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_PICTURE_DATA_LENGTH_LEN = 32; /* bits */

FLAC_API const uint32_t FLAC__STREAM_METADATA_IS_LAST_LEN = 1; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_TYPE_LEN = 7; /* bits */
FLAC_API const uint32_t FLAC__STREAM_METADATA_LENGTH_LEN = 24; /* bits */

FLAC_API const uint32_t FLAC__FRAME_HEADER_SYNC = 0x3ffe;
FLAC_API const uint32_t FLAC__FRAME_HEADER_SYNC_LEN = 14; /* bits */
FLAC_API const uint32_t FLAC__FRAME_HEADER_RESERVED_LEN = 1; /* bits */
FLAC_API const uint32_t FLAC__FRAME_HEADER_BLOCKING_STRATEGY_LEN = 1; /* bits */
FLAC_API const uint32_t FLAC__FRAME_HEADER_BLOCK_SIZE_LEN = 4; /* bits */
FLAC_API const uint32_t FLAC__FRAME_HEADER_SAMPLE_RATE_LEN = 4; /* bits */
FLAC_API const uint32_t FLAC__FRAME_HEADER_CHANNEL_ASSIGNMENT_LEN = 4; /* bits */
FLAC_API const uint32_t FLAC__FRAME_HEADER_BITS_PER_SAMPLE_LEN = 3; /* bits */
FLAC_API const uint32_t FLAC__FRAME_HEADER_ZERO_PAD_LEN = 1; /* bits */
FLAC_API const uint32_t FLAC__FRAME_HEADER_CRC_LEN = 8; /* bits */

FLAC_API const uint32_t FLAC__FRAME_FOOTER_CRC_LEN = 16; /* bits */

FLAC_API const uint32_t FLAC__ENTROPY_CODING_METHOD_TYPE_LEN = 2; /* bits */
FLAC_API const uint32_t FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE_ORDER_LEN = 4; /* bits */
FLAC_API const uint32_t FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE_PARAMETER_LEN = 4; /* bits */
FLAC_API const uint32_t FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE2_PARAMETER_LEN = 5; /* bits */
FLAC_API const uint32_t FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE_RAW_LEN = 5; /* bits */

FLAC_API const uint32_t FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE_ESCAPE_PARAMETER = 15; /* == (1<<FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE_PARAMETER_LEN)-1 */
FLAC_API const uint32_t FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE2_ESCAPE_PARAMETER = 31; /* == (1<<FLAC__ENTROPY_CODING_METHOD_PARTITIONED_RICE2_PARAMETER_LEN)-1 */

FLAC_API const char * const FLAC__EntropyCodingMethodTypeString[] = {
	"PARTITIONED_RICE",
	"PARTITIONED_RICE2"
};

FLAC_API const uint32_t FLAC__SUBFRAME_LPC_QLP_COEFF_PRECISION_LEN = 4; /* bits */
FLAC_API const uint32_t FLAC__SUBFRAME_LPC_QLP_SHIFT_LEN = 5; /* bits */

FLAC_API const uint32_t FLAC__SUBFRAME_ZERO_PAD_LEN = 1; /* bits */
FLAC_API const uint32_t FLAC__SUBFRAME_TYPE_LEN = 6; /* bits */
FLAC_API const uint32_t FLAC__SUBFRAME_WASTED_BITS_FLAG_LEN = 1; /* bits */

FLAC_API const uint32_t FLAC__SUBFRAME_TYPE_CONSTANT_BYTE_ALIGNED_MASK = 0x00;
FLAC_API const uint32_t FLAC__SUBFRAME_TYPE_VERBATIM_BYTE_ALIGNED_MASK = 0x02;
FLAC_API const uint32_t FLAC__SUBFRAME_TYPE_FIXED_BYTE_ALIGNED_MASK = 0x10;
FLAC_API const uint32_t FLAC__SUBFRAME_TYPE_LPC_BYTE_ALIGNED_MASK = 0x40;

FLAC_API const char * const FLAC__SubframeTypeString[] = {
	"CONSTANT",
	"VERBATIM",
	"FIXED",
	"LPC"
};

FLAC_API const char * const FLAC__ChannelAssignmentString[] = {
	"INDEPENDENT",
	"LEFT_SIDE",
	"RIGHT_SIDE",
	"MID_SIDE"
};

FLAC_API const char * const FLAC__FrameNumberTypeString[] = {
	"FRAME_NUMBER_TYPE_FRAME_NUMBER",
	"FRAME_NUMBER_TYPE_SAMPLE_NUMBER"
};

FLAC_API const char * const FLAC__MetadataTypeString[] = {
	"STREAMINFO",
	"PADDING",
	"APPLICATION",
	"SEEKTABLE",
	"VORBIS_COMMENT",
	"CUESHEET",
	"PICTURE"
};

FLAC_API const char * const FLAC__StreamMetadata_Picture_TypeString[] = {
	"Other",
	"32x32 pixels 'file icon' (PNG only)",
	"Other file icon",
	"Cover (front)",
	"Cover (back)",
	"Leaflet page",
	"Media (e.g. label side of CD)",
	"Lead artist/lead performer/soloist",
	"Artist/performer",
	"Conductor",
	"Band/Orchestra",
	"Composer",
	"Lyricist/text writer",
	"Recording Location",
	"During recording",
	"During performance",
	"Movie/video screen capture",
	"A bright coloured fish",
	"Illustration",
	"Band/artist logotype",
	"Publisher/Studio logotype"
};

FLAC_API FLAC__bool FLAC__format_sample_rate_is_valid(uint32_t sample_rate)
{
	if(sample_rate == 0 || sample_rate > FLAC__MAX_SAMPLE_RATE) {
		return false;
	}
	else
		return true;
}

FLAC_API FLAC__bool FLAC__format_blocksize_is_subset(uint32_t blocksize, uint32_t sample_rate)
{
	if(blocksize > 16384)
		return false;
	else if(sample_rate <= 48000 && blocksize > 4608)
		return false;
	else
		return true;
}

FLAC_API FLAC__bool FLAC__format_sample_rate_is_subset(uint32_t sample_rate)
{
	if(
		!FLAC__format_sample_rate_is_valid(sample_rate) ||
		(
			sample_rate >= (1u << 16) &&
			!(sample_rate % 1000 == 0 || sample_rate % 10 == 0)
		)
	) {
		return false;
	}
	else
		return true;
}

/* @@@@ add to unit tests; it is already indirectly tested by the metadata_object tests */
FLAC_API FLAC__bool FLAC__format_seektable_is_legal(const FLAC__StreamMetadata_SeekTable *seek_table)
{
	uint32_t i;
	FLAC__uint64 prev_sample_number = 0;
	FLAC__bool got_prev = false;

	FLAC__ASSERT(0 != seek_table);

	for(i = 0; i < seek_table->num_points; i++) {
		if(got_prev) {
			if(
				seek_table->points[i].sample_number != FLAC__STREAM_METADATA_SEEKPOINT_PLACEHOLDER &&
				seek_table->points[i].sample_number <= prev_sample_number
			)
				return false;
		}
		prev_sample_number = seek_table->points[i].sample_number;
		got_prev = true;
	}

	return true;
}

/* used as the sort predicate for qsort() */
static int seekpoint_compare_(const FLAC__StreamMetadata_SeekPoint *l, const FLAC__StreamMetadata_SeekPoint *r)
{
	/* we don't just 'return l->sample_number - r->sample_number' since the result (FLAC__int64) might overflow an 'int' */
	if(l->sample_number == r->sample_number)
		return 0;
	else if(l->sample_number < r->sample_number)
		return -1;
	else
		return 1;
}

/* @@@@ add to unit tests; it is already indirectly tested by the metadata_object tests */
FLAC_API uint32_t FLAC__format_seektable_sort(FLAC__StreamMetadata_SeekTable *seek_table)
{
	uint32_t i, j;
	FLAC__bool first;

	FLAC__ASSERT(0 != seek_table);

	if (seek_table->num_points == 0)
		return 0;

	/* sort the seekpoints */
	qsort(seek_table->points, seek_table->num_points, sizeof(FLAC__StreamMetadata_SeekPoint), (int (*)(const void *, const void *))seekpoint_compare_);

	/* uniquify the seekpoints */
	first = true;
	for(i = j = 0; i < seek_table->num_points; i++) {
		if(seek_table->points[i].sample_number != FLAC__STREAM_METADATA_SEEKPOINT_PLACEHOLDER) {
			if(!first) {
				if(seek_table->points[i].sample_number == seek_table->points[j-1].sample_number)
					continue;
			}
		}
		first = false;
		seek_table->points[j++] = seek_table->points[i];
	}

	for(i = j; i < seek_table->num_points; i++) {
		seek_table->points[i].sample_number = FLAC__STREAM_METADATA_SEEKPOINT_PLACEHOLDER;
		seek_table->points[i].stream_offset = 0;
		seek_table->points[i].frame_samples = 0;
	}

	return j;
}

/*
 * also disallows non-shortest-form encodings, c.f.
 *   http://www.unicode.org/versions/corrigendum1.html
 * and a more clear explanation at the end of this section:
 *   http://www.cl.cam.ac.uk/~mgk25/unicode.html#utf-8
 */
static uint32_t utf8len_(const FLAC__byte *utf8)
{
	FLAC__ASSERT(0 != utf8);
	if ((utf8[0] & 0x80) == 0) {
		return 1;
	}
	else if ((utf8[0] & 0xE0) == 0xC0 && (utf8[1] & 0xC0) == 0x80) {
		if ((utf8[0] & 0xFE) == 0xC0) /* overlong sequence check */
			return 0;
		return 2;
	}
	else if ((utf8[0] & 0xF0) == 0xE0 && (utf8[1] & 0xC0) == 0x80 && (utf8[2] & 0xC0) == 0x80) {
		if (utf8[0] == 0xE0 && (utf8[1] & 0xE0) == 0x80) /* overlong sequence check */
			return 0;
		/* illegal surrogates check (U+D800...U+DFFF and U+FFFE...U+FFFF) */
		if (utf8[0] == 0xED && (utf8[1] & 0xE0) == 0xA0) /* D800-DFFF */
			return 0;
		if (utf8[0] == 0xEF && utf8[1] == 0xBF && (utf8[2] & 0xFE) == 0xBE) /* FFFE-FFFF */
			return 0;
		return 3;
	}
	else if ((utf8[0] & 0xF8) == 0xF0 && (utf8[1] & 0xC0) == 0x80 && (utf8[2] & 0xC0) == 0x80 && (utf8[3] & 0xC0) == 0x80) {
		if (utf8[0] == 0xF0 && (utf8[1] & 0xF0) == 0x80) /* overlong sequence check */
			return 0;
		return 4;
	}
	else if ((utf8[0] & 0xFC) == 0xF8 && (utf8[1] & 0xC0) == 0x80 && (utf8[2] & 0xC0) == 0x80 && (utf8[3] & 0xC0) == 0x80 && (utf8[4] & 0xC0) == 0x80) {
		if (utf8[0] == 0xF8 && (utf8[1] & 0xF8) == 0x80) /* overlong sequence check */
			return 0;
		return 5;
	}
	else if ((utf8[0] & 0xFE) == 0xFC && (utf8[1] & 0xC0) == 0x80 && (utf8[2] & 0xC0) == 0x80 && (utf8[3] & 0xC0) == 0x80 && (utf8[4] & 0xC0) == 0x80 && (utf8[5] & 0xC0) == 0x80) {
		if (utf8[0] == 0xFC && (utf8[1] & 0xFC) == 0x80) /* overlong sequence check */
			return 0;
		return 6;
	}
	else {
		return 0;
	}
}

FLAC_API FLAC__bool FLAC__format_vorbiscomment_entry_name_is_legal(const char *name)
{
	char c;
	for(c = *name; c; c = *(++name))
		if(c < 0x20 || c == 0x3d || c > 0x7d)
			return false;
	return true;
}

FLAC_API FLAC__bool FLAC__format_vorbiscomment_entry_value_is_legal(const FLAC__byte *value, uint32_t length)
{
	if(length == (uint32_t)(-1)) {
		while(*value) {
			uint32_t n = utf8len_(value);
			if(n == 0)
				return false;
			value += n;
		}
	}
	else {
		const FLAC__byte *end = value + length;
		while(value < end) {
			uint32_t n = utf8len_(value);
			if(n == 0)
				return false;
			value += n;
		}
		if(value != end)
			return false;
	}
	return true;
}

FLAC_API FLAC__bool FLAC__format_vorbiscomment_entry_is_legal(const FLAC__byte *entry, uint32_t length)
{
	const FLAC__byte *s, *end;

	for(s = entry, end = s + length; s < end && *s != '='; s++) {
		if(*s < 0x20 || *s > 0x7D)
			return false;
	}
	if(s == end)
		return false;

	s++; /* skip '=' */

	while(s < end) {
		uint32_t n = utf8len_(s);
		if(n == 0)
			return false;
		s += n;
	}
	if(s != end)
		return false;

	return true;
}

/* @@@@ add to unit tests; it is already indirectly tested by the metadata_object tests */
FLAC_API FLAC__bool FLAC__format_cuesheet_is_legal(const FLAC__StreamMetadata_CueSheet *cue_sheet, FLAC__bool check_cd_da_subset, const char **violation)
{
	uint32_t i, j;

	if(check_cd_da_subset) {
		if(cue_sheet->lead_in < 2 * 44100) {
			if(violation) *violation = "CD-DA cue sheet must have a lead-in length of at least 2 seconds";
			return false;
		}
		if(cue_sheet->lead_in % 588 != 0) {
			if(violation) *violation = "CD-DA cue sheet lead-in length must be evenly divisible by 588 samples";
			return false;
		}
	}

	if(cue_sheet->num_tracks == 0) {
		if(violation) *violation = "cue sheet must have at least one track (the lead-out)";
		return false;
	}

	if(check_cd_da_subset && cue_sheet->tracks[cue_sheet->num_tracks-1].number != 170) {
		if(violation) *violation = "CD-DA cue sheet must have a lead-out track number 170 (0xAA)";
		return false;
	}

	for(i = 0; i < cue_sheet->num_tracks; i++) {
		if(cue_sheet->tracks[i].number == 0) {
			if(violation) *violation = "cue sheet may not have a track number 0";
			return false;
		}

		if(check_cd_da_subset) {
			if(!((cue_sheet->tracks[i].number >= 1 && cue_sheet->tracks[i].number <= 99) || cue_sheet->tracks[i].number == 170)) {
				if(violation) *violation = "CD-DA cue sheet track number must be 1-99 or 170";
				return false;
			}
		}

		if(check_cd_da_subset && cue_sheet->tracks[i].offset % 588 != 0) {
			if(violation) {
				if(i == cue_sheet->num_tracks-1) /* the lead-out track... */
					*violation = "CD-DA cue sheet lead-out offset must be evenly divisible by 588 samples";
				else
					*violation = "CD-DA cue sheet track offset must be evenly divisible by 588 samples";
			}
			return false;
		}

		if(i < cue_sheet->num_tracks - 1) {
			if(cue_sheet->tracks[i].num_indices == 0) {
				if(violation) *violation = "cue sheet track must have at least one index point";
				return false;
			}

			if(cue_sheet->tracks[i].indices[0].number > 1) {
				if(violation) *violation = "cue sheet track's first index number must be 0 or 1";
				return false;
			}
		}

		for(j = 0; j < cue_sheet->tracks[i].num_indices; j++) {
			if(check_cd_da_subset && cue_sheet->tracks[i].indices[j].offset % 588 != 0) {
				if(violation) *violation = "CD-DA cue sheet track index offset must be evenly divisible by 588 samples";
				return false;
			}

			if(j > 0) {
				if(cue_sheet->tracks[i].indices[j].number != cue_sheet->tracks[i].indices[j-1].number + 1) {
					if(violation) *violation = "cue sheet track index numbers must increase by 1";
					return false;
				}
			}
		}
	}

	return true;
}

/* @@@@ add to unit tests; it is already indirectly tested by the metadata_object tests */
FLAC_API FLAC__bool FLAC__format_picture_is_legal(const FLAC__StreamMetadata_Picture *picture, const char **violation)
{
	char *p;
	FLAC__byte *b;

	for(p = picture->mime_type; *p; p++) {
		if(*p < 0x20 || *p > 0x7e) {
			if(violation) *violation = "MIME type string must contain only printable ASCII characters (0x20-0x7e)";
			return false;
		}
	}

	for(b = picture->description; *b; ) {
		uint32_t n = utf8len_(b);
		if(n == 0) {
			if(violation) *violation = "description string must be valid UTF-8";
			return false;
		}
		b += n;
	}

	return true;
}

/*
 * These routines are private to libFLAC
 */
uint32_t FLAC__format_get_max_rice_partition_order(uint32_t blocksize, uint32_t predictor_order)
{
	return
		FLAC__format_get_max_rice_partition_order_from_blocksize_limited_max_and_predictor_order(
			FLAC__format_get_max_rice_partition_order_from_blocksize(blocksize),
			blocksize,
			predictor_order
		);
}

uint32_t FLAC__format_get_max_rice_partition_order_from_blocksize(uint32_t blocksize)
{
	uint32_t max_rice_partition_order = 0;
	while(!(blocksize & 1)) {
		max_rice_partition_order++;
		blocksize >>= 1;
	}
	return flac_min(FLAC__MAX_RICE_PARTITION_ORDER, max_rice_partition_order);
}

uint32_t FLAC__format_get_max_rice_partition_order_from_blocksize_limited_max_and_predictor_order(uint32_t limit, uint32_t blocksize, uint32_t predictor_order)
{
	uint32_t max_rice_partition_order = limit;

	while(max_rice_partition_order > 0 && (blocksize >> max_rice_partition_order) <= predictor_order)
		max_rice_partition_order--;

	FLAC__ASSERT(
		(max_rice_partition_order == 0 && blocksize >= predictor_order) ||
		(max_rice_partition_order > 0 && blocksize >> max_rice_partition_order > predictor_order)
	);

	return max_rice_partition_order;
}

void FLAC__format_entropy_coding_method_partitioned_rice_contents_init(FLAC__EntropyCodingMethod_PartitionedRiceContents *object)
{
	FLAC__ASSERT(0 != object);

	object->parameters = 0;
	object->raw_bits = 0;
	object->capacity_by_order = 0;
}

void FLAC__format_entropy_coding_method_partitioned_rice_contents_clear(FLAC__EntropyCodingMethod_PartitionedRiceContents *object)
{
	FLAC__ASSERT(0 != object);

	if(0 != object->parameters)
		free(object->parameters);
	if(0 != object->raw_bits)
		free(object->raw_bits);
	FLAC__format_entropy_coding_method_partitioned_rice_contents_init(object);
}

FLAC__bool FLAC__format_entropy_coding_method_partitioned_rice_contents_ensure_size(FLAC__EntropyCodingMethod_PartitionedRiceContents *object, uint32_t max_partition_order)
{
	FLAC__ASSERT(0 != object);

	FLAC__ASSERT(object->capacity_by_order > 0 || (0 == object->parameters && 0 == object->raw_bits));

	if(object->capacity_by_order < max_partition_order) {
		if(0 == (object->parameters = safe_realloc_(object->parameters, sizeof(uint32_t)*(1 << max_partition_order))))
			return false;
		if(0 == (object->raw_bits = safe_realloc_(object->raw_bits, sizeof(uint32_t)*(1 << max_partition_order))))
			return false;
		memset(object->raw_bits, 0, sizeof(uint32_t)*(1 << max_partition_order));
		object->capacity_by_order = max_partition_order;
	}

	return true;
}

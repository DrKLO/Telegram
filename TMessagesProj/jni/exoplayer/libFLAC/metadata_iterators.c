/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2001-2009  Josh Coalson
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

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>

#include <sys/stat.h> /* for stat(), maybe chmod() */

#include "private/metadata.h"

#include "FLAC/assert.h"
#include "FLAC/stream_decoder.h"
#include "share/alloc.h"
#include "share/compat.h"
#include "share/macros.h"
#include "share/safe_str.h"
#include "private/macros.h"
#include "private/memory.h"

/* Alias the first (in share/alloc.h) to the second (in src/libFLAC/memory.c). */
#define safe_malloc_mul_2op_ safe_malloc_mul_2op_p

/****************************************************************************
 *
 * Local function declarations
 *
 ***************************************************************************/

static void pack_uint32_(FLAC__uint32 val, FLAC__byte *b, uint32_t bytes);
static void pack_uint32_little_endian_(FLAC__uint32 val, FLAC__byte *b, uint32_t bytes);
static void pack_uint64_(FLAC__uint64 val, FLAC__byte *b, uint32_t bytes);
static FLAC__uint32 unpack_uint32_(FLAC__byte *b, uint32_t bytes);
static FLAC__uint32 unpack_uint32_little_endian_(FLAC__byte *b, uint32_t bytes);
static FLAC__uint64 unpack_uint64_(FLAC__byte *b, uint32_t bytes);

static FLAC__bool read_metadata_block_header_(FLAC__Metadata_SimpleIterator *iterator);
static FLAC__bool read_metadata_block_data_(FLAC__Metadata_SimpleIterator *iterator, FLAC__StreamMetadata *block);
static FLAC__bool read_metadata_block_header_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__bool *is_last, FLAC__MetadataType *type, uint32_t *length);
static FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__IOCallback_Seek seek_cb, FLAC__StreamMetadata *block);
static FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_streaminfo_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_StreamInfo *block);
static FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_padding_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Seek seek_cb, FLAC__StreamMetadata_Padding *block, uint32_t block_length);
static FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_application_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_Application *block, uint32_t block_length);
static FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_seektable_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_SeekTable *block, uint32_t block_length);
static FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_vorbis_comment_entry_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_VorbisComment_Entry *entry, uint32_t max_length);
static FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_vorbis_comment_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__IOCallback_Seek seek_cb, FLAC__StreamMetadata_VorbisComment *block, uint32_t block_length);
static FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_cuesheet_track_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_CueSheet_Track *track);
static FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_cuesheet_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_CueSheet *block);
static FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_picture_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_Picture *block);
static FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_unknown_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_Unknown *block, uint32_t block_length);

static FLAC__bool write_metadata_block_header_(FILE *file, FLAC__Metadata_SimpleIteratorStatus *status, const FLAC__StreamMetadata *block);
static FLAC__bool write_metadata_block_data_(FILE *file, FLAC__Metadata_SimpleIteratorStatus *status, const FLAC__StreamMetadata *block);
static FLAC__bool write_metadata_block_header_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata *block);
static FLAC__bool write_metadata_block_data_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata *block);
static FLAC__bool write_metadata_block_data_streaminfo_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_StreamInfo *block);
static FLAC__bool write_metadata_block_data_padding_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_Padding *block, uint32_t block_length);
static FLAC__bool write_metadata_block_data_application_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_Application *block, uint32_t block_length);
static FLAC__bool write_metadata_block_data_seektable_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_SeekTable *block);
static FLAC__bool write_metadata_block_data_vorbis_comment_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_VorbisComment *block);
static FLAC__bool write_metadata_block_data_cuesheet_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_CueSheet *block);
static FLAC__bool write_metadata_block_data_picture_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_Picture *block);
static FLAC__bool write_metadata_block_data_unknown_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_Unknown *block, uint32_t block_length);

static FLAC__bool write_metadata_block_stationary_(FLAC__Metadata_SimpleIterator *iterator, const FLAC__StreamMetadata *block);
static FLAC__bool write_metadata_block_stationary_with_padding_(FLAC__Metadata_SimpleIterator *iterator, FLAC__StreamMetadata *block, uint32_t padding_length, FLAC__bool padding_is_last);
static FLAC__bool rewrite_whole_file_(FLAC__Metadata_SimpleIterator *iterator, FLAC__StreamMetadata *block, FLAC__bool append);

static void simple_iterator_push_(FLAC__Metadata_SimpleIterator *iterator);
static FLAC__bool simple_iterator_pop_(FLAC__Metadata_SimpleIterator *iterator);

static uint32_t seek_to_first_metadata_block_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__IOCallback_Seek seek_cb);
static uint32_t seek_to_first_metadata_block_(FILE *f);

static FLAC__bool simple_iterator_copy_file_prefix_(FLAC__Metadata_SimpleIterator *iterator, FILE **tempfile, char **tempfilename, FLAC__bool append);
static FLAC__bool simple_iterator_copy_file_postfix_(FLAC__Metadata_SimpleIterator *iterator, FILE **tempfile, char **tempfilename, int fixup_is_last_code, FLAC__off_t fixup_is_last_flag_offset, FLAC__bool backup);

static FLAC__bool copy_n_bytes_from_file_(FILE *file, FILE *tempfile, FLAC__off_t bytes, FLAC__Metadata_SimpleIteratorStatus *status);
static FLAC__bool copy_n_bytes_from_file_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__IOHandle temp_handle, FLAC__IOCallback_Write temp_write_cb, FLAC__off_t bytes, FLAC__Metadata_SimpleIteratorStatus *status);
static FLAC__bool copy_remaining_bytes_from_file_(FILE *file, FILE *tempfile, FLAC__Metadata_SimpleIteratorStatus *status);
static FLAC__bool copy_remaining_bytes_from_file_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__IOCallback_Eof eof_cb, FLAC__IOHandle temp_handle, FLAC__IOCallback_Write temp_write_cb, FLAC__Metadata_SimpleIteratorStatus *status);

static FLAC__bool open_tempfile_(const char *filename, const char *tempfile_path_prefix, FILE **tempfile, char **tempfilename, FLAC__Metadata_SimpleIteratorStatus *status);
static FLAC__bool transport_tempfile_(const char *filename, FILE **tempfile, char **tempfilename, FLAC__Metadata_SimpleIteratorStatus *status);
static void cleanup_tempfile_(FILE **tempfile, char **tempfilename);

static FLAC__bool get_file_stats_(const char *filename, struct flac_stat_s *stats);
static void set_file_stats_(const char *filename, struct flac_stat_s *stats);

static int fseek_wrapper_(FLAC__IOHandle handle, FLAC__int64 offset, int whence);
static FLAC__int64 ftell_wrapper_(FLAC__IOHandle handle);

static FLAC__Metadata_ChainStatus get_equivalent_status_(FLAC__Metadata_SimpleIteratorStatus status);


#ifdef FLAC__VALGRIND_TESTING
static size_t local__fwrite(const void *ptr, size_t size, size_t nmemb, FILE *stream)
{
	size_t ret = fwrite(ptr, size, nmemb, stream);
	if(!ferror(stream))
		fflush(stream);
	return ret;
}
#else
#define local__fwrite fwrite
#endif

/****************************************************************************
 *
 * Level 0 implementation
 *
 ***************************************************************************/

static FLAC__StreamDecoderWriteStatus write_callback_(const FLAC__StreamDecoder *decoder, const FLAC__Frame *frame, const FLAC__int32 * const buffer[], void *client_data);
static void metadata_callback_(const FLAC__StreamDecoder *decoder, const FLAC__StreamMetadata *metadata, void *client_data);
static void error_callback_(const FLAC__StreamDecoder *decoder, FLAC__StreamDecoderErrorStatus status, void *client_data);

typedef struct {
	FLAC__bool got_error;
	FLAC__StreamMetadata *object;
} level0_client_data;

static FLAC__StreamMetadata *get_one_metadata_block_(const char *filename, FLAC__MetadataType type)
{
	level0_client_data cd;
	FLAC__StreamDecoder *decoder;

	FLAC__ASSERT(0 != filename);

	cd.got_error = false;
	cd.object = 0;

	decoder = FLAC__stream_decoder_new();

	if(0 == decoder)
		return 0;

	FLAC__stream_decoder_set_md5_checking(decoder, false);
	FLAC__stream_decoder_set_metadata_ignore_all(decoder);
	FLAC__stream_decoder_set_metadata_respond(decoder, type);

	if(FLAC__stream_decoder_init_file(decoder, filename, write_callback_, metadata_callback_, error_callback_, &cd) != FLAC__STREAM_DECODER_INIT_STATUS_OK || cd.got_error) {
		(void)FLAC__stream_decoder_finish(decoder);
		FLAC__stream_decoder_delete(decoder);
		return 0;
	}

	if(!FLAC__stream_decoder_process_until_end_of_metadata(decoder) || cd.got_error) {
		(void)FLAC__stream_decoder_finish(decoder);
		FLAC__stream_decoder_delete(decoder);
		if(0 != cd.object)
			FLAC__metadata_object_delete(cd.object);
		return 0;
	}

	(void)FLAC__stream_decoder_finish(decoder);
	FLAC__stream_decoder_delete(decoder);

	return cd.object;
}

FLAC_API FLAC__bool FLAC__metadata_get_streaminfo(const char *filename, FLAC__StreamMetadata *streaminfo)
{
	FLAC__StreamMetadata *object;

	FLAC__ASSERT(0 != filename);
	FLAC__ASSERT(0 != streaminfo);

	object = get_one_metadata_block_(filename, FLAC__METADATA_TYPE_STREAMINFO);

	if (object) {
		/* can just copy the contents since STREAMINFO has no internal structure */
		*streaminfo = *object;
		FLAC__metadata_object_delete(object);
		return true;
	}
	else {
		return false;
	}
}

FLAC_API FLAC__bool FLAC__metadata_get_tags(const char *filename, FLAC__StreamMetadata **tags)
{
	FLAC__ASSERT(0 != filename);
	FLAC__ASSERT(0 != tags);

	*tags = get_one_metadata_block_(filename, FLAC__METADATA_TYPE_VORBIS_COMMENT);

	return 0 != *tags;
}

FLAC_API FLAC__bool FLAC__metadata_get_cuesheet(const char *filename, FLAC__StreamMetadata **cuesheet)
{
	FLAC__ASSERT(0 != filename);
	FLAC__ASSERT(0 != cuesheet);

	*cuesheet = get_one_metadata_block_(filename, FLAC__METADATA_TYPE_CUESHEET);

	return 0 != *cuesheet;
}

FLAC__StreamDecoderWriteStatus write_callback_(const FLAC__StreamDecoder *decoder, const FLAC__Frame *frame, const FLAC__int32 * const buffer[], void *client_data)
{
	(void)decoder, (void)frame, (void)buffer, (void)client_data;

	return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE;
}

void metadata_callback_(const FLAC__StreamDecoder *decoder, const FLAC__StreamMetadata *metadata, void *client_data)
{
	level0_client_data *cd = (level0_client_data *)client_data;
	(void)decoder;

	/*
	 * we assume we only get here when the one metadata block we were
	 * looking for was passed to us
	 */
	if(!cd->got_error && 0 == cd->object) {
		if(0 == (cd->object = FLAC__metadata_object_clone(metadata)))
			cd->got_error = true;
	}
}

void error_callback_(const FLAC__StreamDecoder *decoder, FLAC__StreamDecoderErrorStatus status, void *client_data)
{
	level0_client_data *cd = (level0_client_data *)client_data;
	(void)decoder;

	if(status != FLAC__STREAM_DECODER_ERROR_STATUS_LOST_SYNC)
		cd->got_error = true;
}

FLAC_API FLAC__bool FLAC__metadata_get_picture(const char *filename, FLAC__StreamMetadata **picture, FLAC__StreamMetadata_Picture_Type type, const char *mime_type, const FLAC__byte *description, uint32_t max_width, uint32_t max_height, uint32_t max_depth, uint32_t max_colors)
{
	FLAC__Metadata_SimpleIterator *it;
	FLAC__uint64 max_area_seen = 0;
	FLAC__uint64 max_depth_seen = 0;

	FLAC__ASSERT(0 != filename);
	FLAC__ASSERT(0 != picture);

	*picture = 0;

	it = FLAC__metadata_simple_iterator_new();
	if(0 == it)
		return false;
	if(!FLAC__metadata_simple_iterator_init(it, filename, /*read_only=*/true, /*preserve_file_stats=*/true)) {
		FLAC__metadata_simple_iterator_delete(it);
		return false;
	}
	do {
		if(FLAC__metadata_simple_iterator_get_block_type(it) == FLAC__METADATA_TYPE_PICTURE) {
			FLAC__StreamMetadata *obj = FLAC__metadata_simple_iterator_get_block(it);
			FLAC__uint64 area = (FLAC__uint64)obj->data.picture.width * (FLAC__uint64)obj->data.picture.height;
			/* check constraints */
			if(
				(type == (FLAC__StreamMetadata_Picture_Type)(-1) || type == obj->data.picture.type) &&
				(mime_type == 0 || !strcmp(mime_type, obj->data.picture.mime_type)) &&
				(description == 0 || !strcmp((const char *)description, (const char *)obj->data.picture.description)) &&
				obj->data.picture.width <= max_width &&
				obj->data.picture.height <= max_height &&
				obj->data.picture.depth <= max_depth &&
				obj->data.picture.colors <= max_colors &&
				(area > max_area_seen || (area == max_area_seen && obj->data.picture.depth > max_depth_seen))
			) {
				if(*picture)
					FLAC__metadata_object_delete(*picture);
				*picture = obj;
				max_area_seen = area;
				max_depth_seen = obj->data.picture.depth;
			}
			else {
				FLAC__metadata_object_delete(obj);
			}
		}
	} while(FLAC__metadata_simple_iterator_next(it));

	FLAC__metadata_simple_iterator_delete(it);

	return (0 != *picture);
}


/****************************************************************************
 *
 * Level 1 implementation
 *
 ***************************************************************************/

#define SIMPLE_ITERATOR_MAX_PUSH_DEPTH (1+4)
/* 1 for initial offset, +4 for our own personal use */

struct FLAC__Metadata_SimpleIterator {
	FILE *file;
	char *filename, *tempfile_path_prefix;
	struct flac_stat_s stats;
	FLAC__bool has_stats;
	FLAC__bool is_writable;
	FLAC__Metadata_SimpleIteratorStatus status;
	FLAC__off_t offset[SIMPLE_ITERATOR_MAX_PUSH_DEPTH];
	FLAC__off_t first_offset; /* this is the offset to the STREAMINFO block */
	uint32_t depth;
	/* this is the metadata block header of the current block we are pointing to: */
	FLAC__bool is_last;
	FLAC__MetadataType type;
	uint32_t length;
};

FLAC_API const char * const FLAC__Metadata_SimpleIteratorStatusString[] = {
	"FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK",
	"FLAC__METADATA_SIMPLE_ITERATOR_STATUS_ILLEGAL_INPUT",
	"FLAC__METADATA_SIMPLE_ITERATOR_STATUS_ERROR_OPENING_FILE",
	"FLAC__METADATA_SIMPLE_ITERATOR_STATUS_NOT_A_FLAC_FILE",
	"FLAC__METADATA_SIMPLE_ITERATOR_STATUS_NOT_WRITABLE",
	"FLAC__METADATA_SIMPLE_ITERATOR_STATUS_BAD_METADATA",
	"FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR",
	"FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR",
	"FLAC__METADATA_SIMPLE_ITERATOR_STATUS_WRITE_ERROR",
	"FLAC__METADATA_SIMPLE_ITERATOR_STATUS_RENAME_ERROR",
	"FLAC__METADATA_SIMPLE_ITERATOR_STATUS_UNLINK_ERROR",
	"FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR",
	"FLAC__METADATA_SIMPLE_ITERATOR_STATUS_INTERNAL_ERROR"
};


FLAC_API FLAC__Metadata_SimpleIterator *FLAC__metadata_simple_iterator_new(void)
{
	FLAC__Metadata_SimpleIterator *iterator = calloc(1, sizeof(FLAC__Metadata_SimpleIterator));

	if(0 != iterator) {
		iterator->file = 0;
		iterator->filename = 0;
		iterator->tempfile_path_prefix = 0;
		iterator->has_stats = false;
		iterator->is_writable = false;
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK;
		iterator->first_offset = iterator->offset[0] = -1;
		iterator->depth = 0;
	}

	return iterator;
}

static void simple_iterator_free_guts_(FLAC__Metadata_SimpleIterator *iterator)
{
	FLAC__ASSERT(0 != iterator);

	if(0 != iterator->file) {
		fclose(iterator->file);
		iterator->file = 0;
		if(iterator->has_stats)
			set_file_stats_(iterator->filename, &iterator->stats);
	}
	if(0 != iterator->filename) {
		free(iterator->filename);
		iterator->filename = 0;
	}
	if(0 != iterator->tempfile_path_prefix) {
		free(iterator->tempfile_path_prefix);
		iterator->tempfile_path_prefix = 0;
	}
}

FLAC_API void FLAC__metadata_simple_iterator_delete(FLAC__Metadata_SimpleIterator *iterator)
{
	FLAC__ASSERT(0 != iterator);

	simple_iterator_free_guts_(iterator);
	free(iterator);
}

FLAC_API FLAC__Metadata_SimpleIteratorStatus FLAC__metadata_simple_iterator_status(FLAC__Metadata_SimpleIterator *iterator)
{
	FLAC__Metadata_SimpleIteratorStatus status;

	FLAC__ASSERT(0 != iterator);

	status = iterator->status;
	iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK;
	return status;
}

static FLAC__bool simple_iterator_prime_input_(FLAC__Metadata_SimpleIterator *iterator, FLAC__bool read_only)
{
	uint32_t ret;

	FLAC__ASSERT(0 != iterator);

	if(read_only || 0 == (iterator->file = flac_fopen(iterator->filename, "r+b"))) {
		iterator->is_writable = false;
		if(read_only || errno == EACCES) {
			if(0 == (iterator->file = flac_fopen(iterator->filename, "rb"))) {
				iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_ERROR_OPENING_FILE;
				return false;
			}
		}
		else {
			iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_ERROR_OPENING_FILE;
			return false;
		}
	}
	else {
		iterator->is_writable = true;
	}

	ret = seek_to_first_metadata_block_(iterator->file);
	switch(ret) {
		case 0:
			iterator->depth = 0;
			iterator->first_offset = iterator->offset[iterator->depth] = ftello(iterator->file);
			return read_metadata_block_header_(iterator);
		case 1:
			iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
			return false;
		case 2:
			iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
			return false;
		case 3:
			iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_NOT_A_FLAC_FILE;
			return false;
		default:
			FLAC__ASSERT(0);
			return false;
	}
}

#if 0
@@@ If we decide to finish implementing this, put this comment back in metadata.h
/*
 * The 'tempfile_path_prefix' allows you to specify a directory where
 * tempfiles should go.  Remember that if your metadata edits cause the
 * FLAC file to grow, the entire file will have to be rewritten.  If
 * 'tempfile_path_prefix' is NULL, the temp file will be written in the
 * same directory as the original FLAC file.  This makes replacing the
 * original with the tempfile fast but requires extra space in the same
 * partition for the tempfile.  If space is a problem, you can pass a
 * directory name belonging to a different partition in
 * 'tempfile_path_prefix'.  Note that you should use the forward slash
 * '/' as the directory separator.  A trailing slash is not needed; it
 * will be added automatically.
 */
FLAC__bool FLAC__metadata_simple_iterator_init(FLAC__Metadata_SimpleIterator *iterator, const char *filename, FLAC__bool preserve_file_stats, const char *tempfile_path_prefix);
#endif

FLAC_API FLAC__bool FLAC__metadata_simple_iterator_init(FLAC__Metadata_SimpleIterator *iterator, const char *filename, FLAC__bool read_only, FLAC__bool preserve_file_stats)
{
	const char *tempfile_path_prefix = 0; /*@@@ search for comments near 'flac_rename(...)' for what it will take to finish implementing this */

	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != filename);

	simple_iterator_free_guts_(iterator);

	if(!read_only && preserve_file_stats)
		iterator->has_stats = get_file_stats_(filename, &iterator->stats);

	if(0 == (iterator->filename = strdup(filename))) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR;
		return false;
	}
	if(0 != tempfile_path_prefix && 0 == (iterator->tempfile_path_prefix = strdup(tempfile_path_prefix))) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR;
		return false;
	}

	return simple_iterator_prime_input_(iterator, read_only);
}

FLAC_API FLAC__bool FLAC__metadata_simple_iterator_is_writable(const FLAC__Metadata_SimpleIterator *iterator)
{
	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->file);

	return iterator->is_writable;
}

FLAC_API FLAC__bool FLAC__metadata_simple_iterator_next(FLAC__Metadata_SimpleIterator *iterator)
{
	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->file);

	if(iterator->is_last)
		return false;

	if(0 != fseeko(iterator->file, iterator->length, SEEK_CUR)) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
		return false;
	}

	iterator->offset[iterator->depth] = ftello(iterator->file);

	return read_metadata_block_header_(iterator);
}

FLAC_API FLAC__bool FLAC__metadata_simple_iterator_prev(FLAC__Metadata_SimpleIterator *iterator)
{
	FLAC__off_t this_offset;

	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->file);

	if(iterator->offset[iterator->depth] == iterator->first_offset)
		return false;

	if(0 != fseeko(iterator->file, iterator->first_offset, SEEK_SET)) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
		return false;
	}
	this_offset = iterator->first_offset;
	if(!read_metadata_block_header_(iterator))
		return false;

	/* we ignore any error from ftello() and catch it in fseeko() */
	while(ftello(iterator->file) + (FLAC__off_t)iterator->length < iterator->offset[iterator->depth]) {
		if(0 != fseeko(iterator->file, iterator->length, SEEK_CUR)) {
			iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
			return false;
		}
		this_offset = ftello(iterator->file);
		if(!read_metadata_block_header_(iterator))
			return false;
	}

	iterator->offset[iterator->depth] = this_offset;

	return true;
}

/*@@@@add to tests*/
FLAC_API FLAC__bool FLAC__metadata_simple_iterator_is_last(const FLAC__Metadata_SimpleIterator *iterator)
{
	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->file);

	return iterator->is_last;
}

/*@@@@add to tests*/
FLAC_API off_t FLAC__metadata_simple_iterator_get_block_offset(const FLAC__Metadata_SimpleIterator *iterator)
{
	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->file);

	return iterator->offset[iterator->depth];
}

FLAC_API FLAC__MetadataType FLAC__metadata_simple_iterator_get_block_type(const FLAC__Metadata_SimpleIterator *iterator)
{
	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->file);

	return iterator->type;
}

/*@@@@add to tests*/
FLAC_API uint32_t FLAC__metadata_simple_iterator_get_block_length(const FLAC__Metadata_SimpleIterator *iterator)
{
	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->file);

	return iterator->length;
}

/*@@@@add to tests*/
FLAC_API FLAC__bool FLAC__metadata_simple_iterator_get_application_id(FLAC__Metadata_SimpleIterator *iterator, FLAC__byte *id)
{
	const uint32_t id_bytes = FLAC__STREAM_METADATA_APPLICATION_ID_LEN / 8;

	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->file);
	FLAC__ASSERT(0 != id);

	if(iterator->type != FLAC__METADATA_TYPE_APPLICATION) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_ILLEGAL_INPUT;
		return false;
	}

	if(fread(id, 1, id_bytes, iterator->file) != id_bytes) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
		return false;
	}

	/* back up */
	if(0 != fseeko(iterator->file, -((int)id_bytes), SEEK_CUR)) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
		return false;
	}

	return true;
}

FLAC_API FLAC__StreamMetadata *FLAC__metadata_simple_iterator_get_block(FLAC__Metadata_SimpleIterator *iterator)
{
	FLAC__StreamMetadata *block = FLAC__metadata_object_new(iterator->type);

	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->file);

	if(0 != block) {
		block->is_last = iterator->is_last;
		block->length = iterator->length;

		if(!read_metadata_block_data_(iterator, block)) {
			FLAC__metadata_object_delete(block);
			return 0;
		}

		/* back up to the beginning of the block data to stay consistent */
		if(0 != fseeko(iterator->file, iterator->offset[iterator->depth] + FLAC__STREAM_METADATA_HEADER_LENGTH, SEEK_SET)) {
			iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
			FLAC__metadata_object_delete(block);
			return 0;
		}
	}
	else
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR;

	return block;
}

FLAC_API FLAC__bool FLAC__metadata_simple_iterator_set_block(FLAC__Metadata_SimpleIterator *iterator, FLAC__StreamMetadata *block, FLAC__bool use_padding)
{
	FLAC__ASSERT_DECLARATION(FLAC__off_t debug_target_offset = iterator->offset[iterator->depth];)
	FLAC__bool ret;

	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->file);
	FLAC__ASSERT(0 != block);

	if(!iterator->is_writable) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_NOT_WRITABLE;
		return false;
	}

	if(iterator->type == FLAC__METADATA_TYPE_STREAMINFO || block->type == FLAC__METADATA_TYPE_STREAMINFO) {
		if(iterator->type != block->type) {
			iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_ILLEGAL_INPUT;
			return false;
		}
	}

	block->is_last = iterator->is_last;

	if(iterator->length == block->length)
		return write_metadata_block_stationary_(iterator, block);
	else if(iterator->length > block->length) {
		if(use_padding && iterator->length >= FLAC__STREAM_METADATA_HEADER_LENGTH + block->length) {
			ret = write_metadata_block_stationary_with_padding_(iterator, block, iterator->length - FLAC__STREAM_METADATA_HEADER_LENGTH - block->length, block->is_last);
			FLAC__ASSERT(!ret || iterator->offset[iterator->depth] == debug_target_offset);
			FLAC__ASSERT(!ret || ftello(iterator->file) == debug_target_offset + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH);
			return ret;
		}
		else {
			ret = rewrite_whole_file_(iterator, block, /*append=*/false);
			FLAC__ASSERT(!ret || iterator->offset[iterator->depth] == debug_target_offset);
			FLAC__ASSERT(!ret || ftello(iterator->file) == debug_target_offset + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH);
			return ret;
		}
	}
	else /* iterator->length < block->length */ {
		uint32_t padding_leftover = 0;
		FLAC__bool padding_is_last = false;
		if(use_padding) {
			/* first see if we can even use padding */
			if(iterator->is_last) {
				use_padding = false;
			}
			else {
				const uint32_t extra_padding_bytes_required = block->length - iterator->length;
				simple_iterator_push_(iterator);
				if(!FLAC__metadata_simple_iterator_next(iterator)) {
					(void)simple_iterator_pop_(iterator);
					return false;
				}
				if(iterator->type != FLAC__METADATA_TYPE_PADDING) {
					use_padding = false;
				}
				else {
					if(FLAC__STREAM_METADATA_HEADER_LENGTH + iterator->length == extra_padding_bytes_required) {
						padding_leftover = 0;
						block->is_last = iterator->is_last;
					}
					else if(iterator->length < extra_padding_bytes_required)
						use_padding = false;
					else {
						padding_leftover = FLAC__STREAM_METADATA_HEADER_LENGTH + iterator->length - extra_padding_bytes_required;
						padding_is_last = iterator->is_last;
						block->is_last = false;
					}
				}
				if(!simple_iterator_pop_(iterator))
					return false;
			}
		}
		if(use_padding) {
			if(padding_leftover == 0) {
				ret = write_metadata_block_stationary_(iterator, block);
				FLAC__ASSERT(!ret || iterator->offset[iterator->depth] == debug_target_offset);
				FLAC__ASSERT(!ret || ftello(iterator->file) == debug_target_offset + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH);
				return ret;
			}
			else {
				FLAC__ASSERT(padding_leftover >= FLAC__STREAM_METADATA_HEADER_LENGTH);
				ret = write_metadata_block_stationary_with_padding_(iterator, block, padding_leftover - FLAC__STREAM_METADATA_HEADER_LENGTH, padding_is_last);
				FLAC__ASSERT(!ret || iterator->offset[iterator->depth] == debug_target_offset);
				FLAC__ASSERT(!ret || ftello(iterator->file) == debug_target_offset + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH);
				return ret;
			}
		}
		else {
			ret = rewrite_whole_file_(iterator, block, /*append=*/false);
			FLAC__ASSERT(!ret || iterator->offset[iterator->depth] == debug_target_offset);
			FLAC__ASSERT(!ret || ftello(iterator->file) == debug_target_offset + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH);
			return ret;
		}
	}
}

FLAC_API FLAC__bool FLAC__metadata_simple_iterator_insert_block_after(FLAC__Metadata_SimpleIterator *iterator, FLAC__StreamMetadata *block, FLAC__bool use_padding)
{
	uint32_t padding_leftover = 0;
	FLAC__bool padding_is_last = false;

	FLAC__ASSERT_DECLARATION(FLAC__off_t debug_target_offset = iterator->offset[iterator->depth] + FLAC__STREAM_METADATA_HEADER_LENGTH + iterator->length;)
	FLAC__bool ret;

	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->file);
	FLAC__ASSERT(0 != block);

	if(!iterator->is_writable) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_NOT_WRITABLE;
		return false;
	}

	if(block->type == FLAC__METADATA_TYPE_STREAMINFO) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_ILLEGAL_INPUT;
		return false;
	}

	block->is_last = iterator->is_last;

	if(use_padding) {
		/* first see if we can even use padding */
		if(iterator->is_last) {
			use_padding = false;
		}
		else {
			simple_iterator_push_(iterator);
			if(!FLAC__metadata_simple_iterator_next(iterator)) {
				(void)simple_iterator_pop_(iterator);
				return false;
			}
			if(iterator->type != FLAC__METADATA_TYPE_PADDING) {
				use_padding = false;
			}
			else {
				if(iterator->length == block->length) {
					padding_leftover = 0;
					block->is_last = iterator->is_last;
				}
				else if(iterator->length < FLAC__STREAM_METADATA_HEADER_LENGTH + block->length)
					use_padding = false;
				else {
					padding_leftover = iterator->length - block->length;
					padding_is_last = iterator->is_last;
					block->is_last = false;
				}
			}
			if(!simple_iterator_pop_(iterator))
				return false;
		}
	}
	if(use_padding) {
		/* move to the next block, which is suitable padding */
		if(!FLAC__metadata_simple_iterator_next(iterator))
			return false;
		if(padding_leftover == 0) {
			ret = write_metadata_block_stationary_(iterator, block);
			FLAC__ASSERT(iterator->offset[iterator->depth] == debug_target_offset);
			FLAC__ASSERT(ftello(iterator->file) == debug_target_offset + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH);
			return ret;
		}
		else {
			FLAC__ASSERT(padding_leftover >= FLAC__STREAM_METADATA_HEADER_LENGTH);
			ret = write_metadata_block_stationary_with_padding_(iterator, block, padding_leftover - FLAC__STREAM_METADATA_HEADER_LENGTH, padding_is_last);
			FLAC__ASSERT(iterator->offset[iterator->depth] == debug_target_offset);
			FLAC__ASSERT(ftello(iterator->file) == debug_target_offset + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH);
			return ret;
		}
	}
	else {
		ret = rewrite_whole_file_(iterator, block, /*append=*/true);
		FLAC__ASSERT(iterator->offset[iterator->depth] == debug_target_offset);
		FLAC__ASSERT(ftello(iterator->file) == debug_target_offset + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH);
		return ret;
	}
}

FLAC_API FLAC__bool FLAC__metadata_simple_iterator_delete_block(FLAC__Metadata_SimpleIterator *iterator, FLAC__bool use_padding)
{
	FLAC__ASSERT_DECLARATION(FLAC__off_t debug_target_offset = iterator->offset[iterator->depth];)
	FLAC__bool ret;

	if(!iterator->is_writable) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_NOT_WRITABLE;
		return false;
	}

	if(iterator->type == FLAC__METADATA_TYPE_STREAMINFO) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_ILLEGAL_INPUT;
		return false;
	}

	if(use_padding) {
		FLAC__StreamMetadata *padding = FLAC__metadata_object_new(FLAC__METADATA_TYPE_PADDING);
		if(0 == padding) {
			iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR;
			return false;
		}
		padding->length = iterator->length;
		if(!FLAC__metadata_simple_iterator_set_block(iterator, padding, false)) {
			FLAC__metadata_object_delete(padding);
			return false;
		}
		FLAC__metadata_object_delete(padding);
		if(!FLAC__metadata_simple_iterator_prev(iterator))
			return false;
		FLAC__ASSERT(iterator->offset[iterator->depth] + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH + (FLAC__off_t)iterator->length == debug_target_offset);
		FLAC__ASSERT(ftello(iterator->file) + (FLAC__off_t)iterator->length == debug_target_offset);
		return true;
	}
	else {
		ret = rewrite_whole_file_(iterator, 0, /*append=*/false);
		FLAC__ASSERT(iterator->offset[iterator->depth] + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH + (FLAC__off_t)iterator->length == debug_target_offset);
		FLAC__ASSERT(ftello(iterator->file) + (FLAC__off_t)iterator->length == debug_target_offset);
		return ret;
	}
}



/****************************************************************************
 *
 * Level 2 implementation
 *
 ***************************************************************************/


typedef struct FLAC__Metadata_Node {
	FLAC__StreamMetadata *data;
	struct FLAC__Metadata_Node *prev, *next;
} FLAC__Metadata_Node;

struct FLAC__Metadata_Chain {
	char *filename; /* will be NULL if using callbacks */
	FLAC__bool is_ogg;
	FLAC__Metadata_Node *head;
	FLAC__Metadata_Node *tail;
	uint32_t nodes;
	FLAC__Metadata_ChainStatus status;
	FLAC__off_t first_offset, last_offset;
	/*
	 * This is the length of the chain initially read from the FLAC file.
	 * it is used to compare against the current length to decide whether
	 * or not the whole file has to be rewritten.
	 */
	FLAC__off_t initial_length;
	/* @@@ hacky, these are currently only needed by ogg reader */
	FLAC__IOHandle handle;
	FLAC__IOCallback_Read read_cb;
};

struct FLAC__Metadata_Iterator {
	FLAC__Metadata_Chain *chain;
	FLAC__Metadata_Node *current;
};

FLAC_API const char * const FLAC__Metadata_ChainStatusString[] = {
	"FLAC__METADATA_CHAIN_STATUS_OK",
	"FLAC__METADATA_CHAIN_STATUS_ILLEGAL_INPUT",
	"FLAC__METADATA_CHAIN_STATUS_ERROR_OPENING_FILE",
	"FLAC__METADATA_CHAIN_STATUS_NOT_A_FLAC_FILE",
	"FLAC__METADATA_CHAIN_STATUS_NOT_WRITABLE",
	"FLAC__METADATA_CHAIN_STATUS_BAD_METADATA",
	"FLAC__METADATA_CHAIN_STATUS_READ_ERROR",
	"FLAC__METADATA_CHAIN_STATUS_SEEK_ERROR",
	"FLAC__METADATA_CHAIN_STATUS_WRITE_ERROR",
	"FLAC__METADATA_CHAIN_STATUS_RENAME_ERROR",
	"FLAC__METADATA_CHAIN_STATUS_UNLINK_ERROR",
	"FLAC__METADATA_CHAIN_STATUS_MEMORY_ALLOCATION_ERROR",
	"FLAC__METADATA_CHAIN_STATUS_INTERNAL_ERROR",
	"FLAC__METADATA_CHAIN_STATUS_INVALID_CALLBACKS",
	"FLAC__METADATA_CHAIN_STATUS_READ_WRITE_MISMATCH",
	"FLAC__METADATA_CHAIN_STATUS_WRONG_WRITE_CALL"
};


static FLAC__Metadata_Node *node_new_(void)
{
	return calloc(1, sizeof(FLAC__Metadata_Node));
}

static void node_delete_(FLAC__Metadata_Node *node)
{
	FLAC__ASSERT(0 != node);
	if(0 != node->data)
		FLAC__metadata_object_delete(node->data);
	free(node);
}

static void chain_init_(FLAC__Metadata_Chain *chain)
{
	FLAC__ASSERT(0 != chain);

	chain->filename = 0;
	chain->is_ogg = false;
	chain->head = chain->tail = 0;
	chain->nodes = 0;
	chain->status = FLAC__METADATA_CHAIN_STATUS_OK;
	chain->initial_length = 0;
	chain->read_cb = 0;
}

static void chain_clear_(FLAC__Metadata_Chain *chain)
{
	FLAC__Metadata_Node *node, *next;

	FLAC__ASSERT(0 != chain);

	for(node = chain->head; node; ) {
		next = node->next;
		node_delete_(node);
		node = next;
	}

	if(0 != chain->filename)
		free(chain->filename);

	chain_init_(chain);
}

static void chain_append_node_(FLAC__Metadata_Chain *chain, FLAC__Metadata_Node *node)
{
	FLAC__ASSERT(0 != chain);
	FLAC__ASSERT(0 != node);
	FLAC__ASSERT(0 != node->data);

	node->next = node->prev = 0;
	node->data->is_last = true;
	if(0 != chain->tail)
		chain->tail->data->is_last = false;

	if(0 == chain->head)
		chain->head = node;
	else {
		FLAC__ASSERT(0 != chain->tail);
		chain->tail->next = node;
		node->prev = chain->tail;
	}
	chain->tail = node;
	chain->nodes++;
}

static void chain_remove_node_(FLAC__Metadata_Chain *chain, FLAC__Metadata_Node *node)
{
	FLAC__ASSERT(0 != chain);
	FLAC__ASSERT(0 != node);

	if(node == chain->head)
		chain->head = node->next;
	else
		node->prev->next = node->next;

	if(node == chain->tail)
		chain->tail = node->prev;
	else
		node->next->prev = node->prev;

	if(0 != chain->tail)
		chain->tail->data->is_last = true;

	chain->nodes--;
}

static void chain_delete_node_(FLAC__Metadata_Chain *chain, FLAC__Metadata_Node *node)
{
	chain_remove_node_(chain, node);
	node_delete_(node);
}

static FLAC__off_t chain_calculate_length_(FLAC__Metadata_Chain *chain)
{
	const FLAC__Metadata_Node *node;
	FLAC__off_t length = 0;
	for(node = chain->head; node; node = node->next)
		length += (FLAC__STREAM_METADATA_HEADER_LENGTH + node->data->length);
	return length;
}

static void iterator_insert_node_(FLAC__Metadata_Iterator *iterator, FLAC__Metadata_Node *node)
{
	FLAC__ASSERT(0 != node);
	FLAC__ASSERT(0 != node->data);
	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->current);
	FLAC__ASSERT(0 != iterator->chain);
	FLAC__ASSERT(0 != iterator->chain->head);
	FLAC__ASSERT(0 != iterator->chain->tail);

	node->data->is_last = false;

	node->prev = iterator->current->prev;
	node->next = iterator->current;

	if(0 == node->prev)
		iterator->chain->head = node;
	else
		node->prev->next = node;

	iterator->current->prev = node;

	iterator->chain->nodes++;
}

static void iterator_insert_node_after_(FLAC__Metadata_Iterator *iterator, FLAC__Metadata_Node *node)
{
	FLAC__ASSERT(0 != node);
	FLAC__ASSERT(0 != node->data);
	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->current);
	FLAC__ASSERT(0 != iterator->chain);
	FLAC__ASSERT(0 != iterator->chain->head);
	FLAC__ASSERT(0 != iterator->chain->tail);

	iterator->current->data->is_last = false;

	node->prev = iterator->current;
	node->next = iterator->current->next;

	if(0 == node->next)
		iterator->chain->tail = node;
	else
		node->next->prev = node;

	node->prev->next = node;

	iterator->chain->tail->data->is_last = true;

	iterator->chain->nodes++;
}

/* return true iff node and node->next are both padding */
static FLAC__bool chain_merge_adjacent_padding_(FLAC__Metadata_Chain *chain, FLAC__Metadata_Node *node)
{
	if(node->data->type == FLAC__METADATA_TYPE_PADDING && 0 != node->next && node->next->data->type == FLAC__METADATA_TYPE_PADDING) {
		const uint32_t growth = FLAC__STREAM_METADATA_HEADER_LENGTH + node->next->data->length;
		node->data->length += growth; /* new block size can be greater than max metadata block size, but it'll be fixed later in chain_prepare_for_write_() */

		chain_delete_node_(chain, node->next);
		return true;
	}
	else
		return false;
}

/* Returns the new length of the chain, or 0 if there was an error. */
/* WATCHOUT: This can get called multiple times before a write, so
 * it should still work when this happens.
 */
/* WATCHOUT: Make sure to also update the logic in
 * FLAC__metadata_chain_check_if_tempfile_needed() if the logic here changes.
 */
static FLAC__off_t chain_prepare_for_write_(FLAC__Metadata_Chain *chain, FLAC__bool use_padding)
{
	FLAC__off_t current_length = chain_calculate_length_(chain);

	if(use_padding) {
		/* if the metadata shrank and the last block is padding, we just extend the last padding block */
		if(current_length < chain->initial_length && chain->tail->data->type == FLAC__METADATA_TYPE_PADDING) {
			const FLAC__off_t delta = chain->initial_length - current_length;
			chain->tail->data->length += delta;
			current_length += delta;
			FLAC__ASSERT(current_length == chain->initial_length);
		}
		/* if the metadata shrank more than 4 bytes then there's room to add another padding block */
		else if(current_length + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH <= chain->initial_length) {
			FLAC__StreamMetadata *padding;
			FLAC__Metadata_Node *node;
			if(0 == (padding = FLAC__metadata_object_new(FLAC__METADATA_TYPE_PADDING))) {
				chain->status = FLAC__METADATA_CHAIN_STATUS_MEMORY_ALLOCATION_ERROR;
				return 0;
			}
			padding->length = chain->initial_length - (FLAC__STREAM_METADATA_HEADER_LENGTH + current_length);
			if(0 == (node = node_new_())) {
				FLAC__metadata_object_delete(padding);
				chain->status = FLAC__METADATA_CHAIN_STATUS_MEMORY_ALLOCATION_ERROR;
				return 0;
			}
			node->data = padding;
			chain_append_node_(chain, node);
			current_length = chain_calculate_length_(chain);
			FLAC__ASSERT(current_length == chain->initial_length);
		}
		/* if the metadata grew but the last block is padding, try cutting the padding to restore the original length so we don't have to rewrite the whole file */
		else if(current_length > chain->initial_length) {
			const FLAC__off_t delta = current_length - chain->initial_length;
			if(chain->tail->data->type == FLAC__METADATA_TYPE_PADDING) {
				/* if the delta is exactly the size of the last padding block, remove the padding block */
				if((FLAC__off_t)chain->tail->data->length + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH == delta) {
					chain_delete_node_(chain, chain->tail);
					current_length = chain_calculate_length_(chain);
					FLAC__ASSERT(current_length == chain->initial_length);
				}
				/* if there is at least 'delta' bytes of padding, trim the padding down */
				else if((FLAC__off_t)chain->tail->data->length >= delta) {
					chain->tail->data->length -= delta;
					current_length -= delta;
					FLAC__ASSERT(current_length == chain->initial_length);
				}
			}
		}
	}

	/* check sizes of all metadata blocks; reduce padding size if necessary */
	{
		FLAC__Metadata_Node *node;
		for (node = chain->head; node; node = node->next) {
			if(node->data->length >= (1u << FLAC__STREAM_METADATA_LENGTH_LEN)) {
				if(node->data->type == FLAC__METADATA_TYPE_PADDING) {
					node->data->length = (1u << FLAC__STREAM_METADATA_LENGTH_LEN) - 1;
					current_length = chain_calculate_length_(chain);
				} else {
					chain->status = FLAC__METADATA_CHAIN_STATUS_BAD_METADATA;
					return 0;
				}
			}
		}
	}

	return current_length;
}

static FLAC__bool chain_read_cb_(FLAC__Metadata_Chain *chain, FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__IOCallback_Seek seek_cb, FLAC__IOCallback_Tell tell_cb)
{
	FLAC__Metadata_Node *node;

	FLAC__ASSERT(0 != chain);

	/* we assume we're already at the beginning of the file */

	switch(seek_to_first_metadata_block_cb_(handle, read_cb, seek_cb)) {
		case 0:
			break;
		case 1:
			chain->status = FLAC__METADATA_CHAIN_STATUS_READ_ERROR;
			return false;
		case 2:
			chain->status = FLAC__METADATA_CHAIN_STATUS_SEEK_ERROR;
			return false;
		case 3:
			chain->status = FLAC__METADATA_CHAIN_STATUS_NOT_A_FLAC_FILE;
			return false;
		default:
			FLAC__ASSERT(0);
			return false;
	}

	{
		FLAC__int64 pos = tell_cb(handle);
		if(pos < 0) {
			chain->status = FLAC__METADATA_CHAIN_STATUS_READ_ERROR;
			return false;
		}
		chain->first_offset = (FLAC__off_t)pos;
	}

	{
		FLAC__bool is_last;
		FLAC__MetadataType type;
		uint32_t length;

		do {
			node = node_new_();
			if(0 == node) {
				chain->status = FLAC__METADATA_CHAIN_STATUS_MEMORY_ALLOCATION_ERROR;
				return false;
			}

			if(!read_metadata_block_header_cb_(handle, read_cb, &is_last, &type, &length)) {
				node_delete_(node);
				chain->status = FLAC__METADATA_CHAIN_STATUS_READ_ERROR;
				return false;
			}

			node->data = FLAC__metadata_object_new(type);
			if(0 == node->data) {
				node_delete_(node);
				chain->status = FLAC__METADATA_CHAIN_STATUS_MEMORY_ALLOCATION_ERROR;
				return false;
			}

			node->data->is_last = is_last;
			node->data->length = length;

			chain->status = get_equivalent_status_(read_metadata_block_data_cb_(handle, read_cb, seek_cb, node->data));
			if(chain->status != FLAC__METADATA_CHAIN_STATUS_OK) {
				node_delete_(node);
				return false;
			}
			chain_append_node_(chain, node);
		} while(!is_last);
	}

	{
		FLAC__int64 pos = tell_cb(handle);
		if(pos < 0) {
			chain->status = FLAC__METADATA_CHAIN_STATUS_READ_ERROR;
			return false;
		}
		chain->last_offset = (FLAC__off_t)pos;
	}

	chain->initial_length = chain_calculate_length_(chain);

	return true;
}

static FLAC__StreamDecoderReadStatus chain_read_ogg_read_cb_(const FLAC__StreamDecoder *decoder, FLAC__byte buffer[], size_t *bytes, void *client_data)
{
	FLAC__Metadata_Chain *chain = (FLAC__Metadata_Chain*)client_data;
	(void)decoder;
	if(*bytes > 0 && chain->status == FLAC__METADATA_CHAIN_STATUS_OK) {
		*bytes = chain->read_cb(buffer, sizeof(FLAC__byte), *bytes, chain->handle);
		if(*bytes == 0)
			return FLAC__STREAM_DECODER_READ_STATUS_END_OF_STREAM;
		else
			return FLAC__STREAM_DECODER_READ_STATUS_CONTINUE;
	}
	else
		return FLAC__STREAM_DECODER_READ_STATUS_ABORT;
}

static FLAC__StreamDecoderWriteStatus chain_read_ogg_write_cb_(const FLAC__StreamDecoder *decoder, const FLAC__Frame *frame, const FLAC__int32 * const buffer[], void *client_data)
{
	(void)decoder, (void)frame, (void)buffer, (void)client_data;
	return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
}

static void chain_read_ogg_metadata_cb_(const FLAC__StreamDecoder *decoder, const FLAC__StreamMetadata *metadata, void *client_data)
{
	FLAC__Metadata_Chain *chain = (FLAC__Metadata_Chain*)client_data;
	FLAC__Metadata_Node *node;

	(void)decoder;

	node = node_new_();
	if(0 == node) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_MEMORY_ALLOCATION_ERROR;
		return;
	}

	node->data = FLAC__metadata_object_clone(metadata);
	if(0 == node->data) {
		node_delete_(node);
		chain->status = FLAC__METADATA_CHAIN_STATUS_MEMORY_ALLOCATION_ERROR;
		return;
	}

	chain_append_node_(chain, node);
}

static void chain_read_ogg_error_cb_(const FLAC__StreamDecoder *decoder, FLAC__StreamDecoderErrorStatus status, void *client_data)
{
	FLAC__Metadata_Chain *chain = (FLAC__Metadata_Chain*)client_data;
	(void)decoder, (void)status;
	chain->status = FLAC__METADATA_CHAIN_STATUS_INTERNAL_ERROR; /*@@@ maybe needs better error code */
}

static FLAC__bool chain_read_ogg_cb_(FLAC__Metadata_Chain *chain, FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb)
{
	FLAC__StreamDecoder *decoder;

	FLAC__ASSERT(0 != chain);

	/* we assume we're already at the beginning of the file */

	chain->handle = handle;
	chain->read_cb = read_cb;
	if(0 == (decoder = FLAC__stream_decoder_new())) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_MEMORY_ALLOCATION_ERROR;
		return false;
	}
	FLAC__stream_decoder_set_metadata_respond_all(decoder);
	if(FLAC__stream_decoder_init_ogg_stream(decoder, chain_read_ogg_read_cb_, /*seek_callback=*/0, /*tell_callback=*/0, /*length_callback=*/0, /*eof_callback=*/0, chain_read_ogg_write_cb_, chain_read_ogg_metadata_cb_, chain_read_ogg_error_cb_, chain) != FLAC__STREAM_DECODER_INIT_STATUS_OK) {
		FLAC__stream_decoder_delete(decoder);
		chain->status = FLAC__METADATA_CHAIN_STATUS_INTERNAL_ERROR; /*@@@ maybe needs better error code */
		return false;
	}

	chain->first_offset = 0; /*@@@ wrong; will need to be set correctly to implement metadata writing for Ogg FLAC */

	if(!FLAC__stream_decoder_process_until_end_of_metadata(decoder))
		chain->status = FLAC__METADATA_CHAIN_STATUS_INTERNAL_ERROR; /*@@@ maybe needs better error code */
	if(chain->status != FLAC__METADATA_CHAIN_STATUS_OK) {
		FLAC__stream_decoder_delete(decoder);
		return false;
	}

	FLAC__stream_decoder_delete(decoder);

	chain->last_offset = 0; /*@@@ wrong; will need to be set correctly to implement metadata writing for Ogg FLAC */

	chain->initial_length = chain_calculate_length_(chain);

	return true;
}

static FLAC__bool chain_rewrite_metadata_in_place_cb_(FLAC__Metadata_Chain *chain, FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, FLAC__IOCallback_Seek seek_cb)
{
	FLAC__Metadata_Node *node;

	FLAC__ASSERT(0 != chain);
	FLAC__ASSERT(0 != chain->head);

	if(0 != seek_cb(handle, chain->first_offset, SEEK_SET)) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_SEEK_ERROR;
		return false;
	}

	for(node = chain->head; node; node = node->next) {
		if(!write_metadata_block_header_cb_(handle, write_cb, node->data)) {
			chain->status = FLAC__METADATA_CHAIN_STATUS_WRITE_ERROR;
			return false;
		}
		if(!write_metadata_block_data_cb_(handle, write_cb, node->data)) {
			chain->status = FLAC__METADATA_CHAIN_STATUS_WRITE_ERROR;
			return false;
		}
	}

	/*FLAC__ASSERT(fflush(), ftello() == chain->last_offset);*/

	chain->status = FLAC__METADATA_CHAIN_STATUS_OK;
	return true;
}

static FLAC__bool chain_rewrite_metadata_in_place_(FLAC__Metadata_Chain *chain)
{
	FILE *file;
	FLAC__bool ret;

	FLAC__ASSERT(0 != chain->filename);

	if(0 == (file = flac_fopen(chain->filename, "r+b"))) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_ERROR_OPENING_FILE;
		return false;
	}

	/* chain_rewrite_metadata_in_place_cb_() sets chain->status for us */
	ret = chain_rewrite_metadata_in_place_cb_(chain, (FLAC__IOHandle)file, (FLAC__IOCallback_Write)fwrite, fseek_wrapper_);

	fclose(file);

	return ret;
}

static FLAC__bool chain_rewrite_file_(FLAC__Metadata_Chain *chain, const char *tempfile_path_prefix)
{
	FILE *f, *tempfile = NULL;
	char *tempfilename;
	FLAC__Metadata_SimpleIteratorStatus status;
	const FLAC__Metadata_Node *node;

	FLAC__ASSERT(0 != chain);
	FLAC__ASSERT(0 != chain->filename);
	FLAC__ASSERT(0 != chain->head);

	/* copy the file prefix (data up to first metadata block */
	if(0 == (f = flac_fopen(chain->filename, "rb"))) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_ERROR_OPENING_FILE;
		return false;
	}
	if(!open_tempfile_(chain->filename, tempfile_path_prefix, &tempfile, &tempfilename, &status)) {
		chain->status = get_equivalent_status_(status);
		goto err;
	}
	if(!copy_n_bytes_from_file_(f, tempfile, chain->first_offset, &status)) {
		chain->status = get_equivalent_status_(status);
		goto err;
	}

	/* write the metadata */
	for(node = chain->head; node; node = node->next) {
		if(!write_metadata_block_header_(tempfile, &status, node->data)) {
			chain->status = get_equivalent_status_(status);
			goto err;
		}
		if(!write_metadata_block_data_(tempfile, &status, node->data)) {
			chain->status = get_equivalent_status_(status);
			goto err;
		}
	}
	/*FLAC__ASSERT(fflush(), ftello() == chain->last_offset);*/

	/* copy the file postfix (everything after the metadata) */
	if(0 != fseeko(f, chain->last_offset, SEEK_SET)) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_SEEK_ERROR;
		goto err;
	}
	if(!copy_remaining_bytes_from_file_(f, tempfile, &status)) {
		chain->status = get_equivalent_status_(status);
		goto err;
	}

	/* move the tempfile on top of the original */
	(void)fclose(f);
	if(!transport_tempfile_(chain->filename, &tempfile, &tempfilename, &status))
		return false;

	return true;

err:
	(void)fclose(f);
	cleanup_tempfile_(&tempfile, &tempfilename);
	return false;
}

/* assumes 'handle' is already at beginning of file */
static FLAC__bool chain_rewrite_file_cb_(FLAC__Metadata_Chain *chain, FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__IOCallback_Seek seek_cb, FLAC__IOCallback_Eof eof_cb, FLAC__IOHandle temp_handle, FLAC__IOCallback_Write temp_write_cb)
{
	FLAC__Metadata_SimpleIteratorStatus status;
	const FLAC__Metadata_Node *node;

	FLAC__ASSERT(0 != chain);
	FLAC__ASSERT(0 == chain->filename);
	FLAC__ASSERT(0 != chain->head);

	/* copy the file prefix (data up to first metadata block */
	if(!copy_n_bytes_from_file_cb_(handle, read_cb, temp_handle, temp_write_cb, chain->first_offset, &status)) {
		chain->status = get_equivalent_status_(status);
		return false;
	}

	/* write the metadata */
	for(node = chain->head; node; node = node->next) {
		if(!write_metadata_block_header_cb_(temp_handle, temp_write_cb, node->data)) {
			chain->status = FLAC__METADATA_CHAIN_STATUS_WRITE_ERROR;
			return false;
		}
		if(!write_metadata_block_data_cb_(temp_handle, temp_write_cb, node->data)) {
			chain->status = FLAC__METADATA_CHAIN_STATUS_WRITE_ERROR;
			return false;
		}
	}
	/*FLAC__ASSERT(fflush(), ftello() == chain->last_offset);*/

	/* copy the file postfix (everything after the metadata) */
	if(0 != seek_cb(handle, chain->last_offset, SEEK_SET)) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_SEEK_ERROR;
		return false;
	}
	if(!copy_remaining_bytes_from_file_cb_(handle, read_cb, eof_cb, temp_handle, temp_write_cb, &status)) {
		chain->status = get_equivalent_status_(status);
		return false;
	}

	return true;
}

FLAC_API FLAC__Metadata_Chain *FLAC__metadata_chain_new(void)
{
	FLAC__Metadata_Chain *chain = calloc(1, sizeof(FLAC__Metadata_Chain));

	if(0 != chain)
		chain_init_(chain);

	return chain;
}

FLAC_API void FLAC__metadata_chain_delete(FLAC__Metadata_Chain *chain)
{
	FLAC__ASSERT(0 != chain);

	chain_clear_(chain);

	free(chain);
}

FLAC_API FLAC__Metadata_ChainStatus FLAC__metadata_chain_status(FLAC__Metadata_Chain *chain)
{
	FLAC__Metadata_ChainStatus status;

	FLAC__ASSERT(0 != chain);

	status = chain->status;
	chain->status = FLAC__METADATA_CHAIN_STATUS_OK;
	return status;
}

static FLAC__bool chain_read_(FLAC__Metadata_Chain *chain, const char *filename, FLAC__bool is_ogg)
{
	FILE *file;
	FLAC__bool ret;

	FLAC__ASSERT(0 != chain);
	FLAC__ASSERT(0 != filename);

	chain_clear_(chain);

	if(0 == (chain->filename = strdup(filename))) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_MEMORY_ALLOCATION_ERROR;
		return false;
	}

	chain->is_ogg = is_ogg;

	if(0 == (file = flac_fopen(filename, "rb"))) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_ERROR_OPENING_FILE;
		return false;
	}

	/* the function also sets chain->status for us */
	ret = is_ogg?
		chain_read_ogg_cb_(chain, file, (FLAC__IOCallback_Read)fread) :
		chain_read_cb_(chain, file, (FLAC__IOCallback_Read)fread, fseek_wrapper_, ftell_wrapper_)
	;

	fclose(file);

	return ret;
}

FLAC_API FLAC__bool FLAC__metadata_chain_read(FLAC__Metadata_Chain *chain, const char *filename)
{
	return chain_read_(chain, filename, /*is_ogg=*/false);
}

/*@@@@add to tests*/
FLAC_API FLAC__bool FLAC__metadata_chain_read_ogg(FLAC__Metadata_Chain *chain, const char *filename)
{
	return chain_read_(chain, filename, /*is_ogg=*/true);
}

static FLAC__bool chain_read_with_callbacks_(FLAC__Metadata_Chain *chain, FLAC__IOHandle handle, FLAC__IOCallbacks callbacks, FLAC__bool is_ogg)
{
	FLAC__bool ret;

	FLAC__ASSERT(0 != chain);

	chain_clear_(chain);

	if (0 == callbacks.read || 0 == callbacks.seek || 0 == callbacks.tell) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_INVALID_CALLBACKS;
		return false;
	}

	chain->is_ogg = is_ogg;

	/* rewind */
	if(0 != callbacks.seek(handle, 0, SEEK_SET)) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_SEEK_ERROR;
		return false;
	}

	/* the function also sets chain->status for us */
	ret = is_ogg?
		chain_read_ogg_cb_(chain, handle, callbacks.read) :
		chain_read_cb_(chain, handle, callbacks.read, callbacks.seek, callbacks.tell)
	;

	return ret;
}

FLAC_API FLAC__bool FLAC__metadata_chain_read_with_callbacks(FLAC__Metadata_Chain *chain, FLAC__IOHandle handle, FLAC__IOCallbacks callbacks)
{
	return chain_read_with_callbacks_(chain, handle, callbacks, /*is_ogg=*/false);
}

/*@@@@add to tests*/
FLAC_API FLAC__bool FLAC__metadata_chain_read_ogg_with_callbacks(FLAC__Metadata_Chain *chain, FLAC__IOHandle handle, FLAC__IOCallbacks callbacks)
{
	return chain_read_with_callbacks_(chain, handle, callbacks, /*is_ogg=*/true);
}

typedef enum {
	LBS_NONE = 0,
	LBS_SIZE_CHANGED,
	LBS_BLOCK_ADDED,
	LBS_BLOCK_REMOVED
} LastBlockState;

FLAC_API FLAC__bool FLAC__metadata_chain_check_if_tempfile_needed(FLAC__Metadata_Chain *chain, FLAC__bool use_padding)
{
	/* This does all the same checks that are in chain_prepare_for_write_()
	 * but doesn't actually alter the chain.  Make sure to update the logic
	 * here if chain_prepare_for_write_() changes.
	 */
	FLAC__off_t current_length;
	LastBlockState lbs_state = LBS_NONE;
	uint32_t lbs_size = 0;

	FLAC__ASSERT(0 != chain);

	current_length = chain_calculate_length_(chain);

	if(use_padding) {
		const FLAC__Metadata_Node * const node = chain->tail;
		/* if the metadata shrank and the last block is padding, we just extend the last padding block */
		if(current_length < chain->initial_length && node->data->type == FLAC__METADATA_TYPE_PADDING) {
			lbs_state = LBS_SIZE_CHANGED;
			lbs_size = node->data->length + (chain->initial_length - current_length);
		}
		/* if the metadata shrank more than 4 bytes then there's room to add another padding block */
		else if(current_length + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH <= chain->initial_length) {
			lbs_state = LBS_BLOCK_ADDED;
			lbs_size = chain->initial_length - (current_length + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH);
		}
		/* if the metadata grew but the last block is padding, try cutting the padding to restore the original length so we don't have to rewrite the whole file */
		else if(current_length > chain->initial_length) {
			const FLAC__off_t delta = current_length - chain->initial_length;
			if(node->data->type == FLAC__METADATA_TYPE_PADDING) {
				/* if the delta is exactly the size of the last padding block, remove the padding block */
				if((FLAC__off_t)node->data->length + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH == delta) {
					lbs_state = LBS_BLOCK_REMOVED;
					lbs_size = 0;
				}
				/* if there is at least 'delta' bytes of padding, trim the padding down */
				else if((FLAC__off_t)node->data->length >= delta) {
					lbs_state = LBS_SIZE_CHANGED;
					lbs_size = node->data->length - delta;
				}
			}
		}
	}

	current_length = 0;
	/* check sizes of all metadata blocks; reduce padding size if necessary */
	{
		const FLAC__Metadata_Node *node;
		for(node = chain->head; node; node = node->next) {
			uint32_t block_len = node->data->length;
			if(node == chain->tail) {
				if(lbs_state == LBS_BLOCK_REMOVED)
					continue;
				else if(lbs_state == LBS_SIZE_CHANGED)
					block_len = lbs_size;
			}
			if(block_len >= (1u << FLAC__STREAM_METADATA_LENGTH_LEN)) {
				if(node->data->type == FLAC__METADATA_TYPE_PADDING)
					block_len = (1u << FLAC__STREAM_METADATA_LENGTH_LEN) - 1;
				else
					return false /* the return value doesn't matter */;
			}
			current_length += (FLAC__STREAM_METADATA_HEADER_LENGTH + block_len);
		}

		if(lbs_state == LBS_BLOCK_ADDED) {
			/* test added padding block */
			uint32_t block_len = lbs_size;
			if(block_len >= (1u << FLAC__STREAM_METADATA_LENGTH_LEN))
				block_len = (1u << FLAC__STREAM_METADATA_LENGTH_LEN) - 1;
			current_length += (FLAC__STREAM_METADATA_HEADER_LENGTH + block_len);
		}
	}

	return (current_length != chain->initial_length);
}

FLAC_API FLAC__bool FLAC__metadata_chain_write(FLAC__Metadata_Chain *chain, FLAC__bool use_padding, FLAC__bool preserve_file_stats)
{
	struct flac_stat_s stats;
	const char *tempfile_path_prefix = 0;
	FLAC__off_t current_length;

	FLAC__ASSERT(0 != chain);

	if (chain->is_ogg) { /* cannot write back to Ogg FLAC yet */
		chain->status = FLAC__METADATA_CHAIN_STATUS_INTERNAL_ERROR;
		return false;
	}

	if (0 == chain->filename) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_READ_WRITE_MISMATCH;
		return false;
	}

	current_length = chain_prepare_for_write_(chain, use_padding);

	/* a return value of 0 means there was an error; chain->status is already set */
	if (0 == current_length)
		return false;

	if(preserve_file_stats)
		get_file_stats_(chain->filename, &stats);

	if(current_length == chain->initial_length) {
		if(!chain_rewrite_metadata_in_place_(chain))
			return false;
	}
	else {
		if(!chain_rewrite_file_(chain, tempfile_path_prefix))
			return false;

		/* recompute lengths and offsets */
		{
			const FLAC__Metadata_Node *node;
			chain->initial_length = current_length;
			chain->last_offset = chain->first_offset;
			for(node = chain->head; node; node = node->next)
				chain->last_offset += (FLAC__STREAM_METADATA_HEADER_LENGTH + node->data->length);
		}
	}

	if(preserve_file_stats)
		set_file_stats_(chain->filename, &stats);

	return true;
}

FLAC_API FLAC__bool FLAC__metadata_chain_write_with_callbacks(FLAC__Metadata_Chain *chain, FLAC__bool use_padding, FLAC__IOHandle handle, FLAC__IOCallbacks callbacks)
{
	FLAC__off_t current_length;

	FLAC__ASSERT(0 != chain);

	if (chain->is_ogg) { /* cannot write back to Ogg FLAC yet */
		chain->status = FLAC__METADATA_CHAIN_STATUS_INTERNAL_ERROR;
		return false;
	}

	if (0 != chain->filename) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_READ_WRITE_MISMATCH;
		return false;
	}

	if (0 == callbacks.write || 0 == callbacks.seek) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_INVALID_CALLBACKS;
		return false;
	}

	if (FLAC__metadata_chain_check_if_tempfile_needed(chain, use_padding)) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_WRONG_WRITE_CALL;
		return false;
	}

	current_length = chain_prepare_for_write_(chain, use_padding);

	/* a return value of 0 means there was an error; chain->status is already set */
	if (0 == current_length)
		return false;

	FLAC__ASSERT(current_length == chain->initial_length);

	return chain_rewrite_metadata_in_place_cb_(chain, handle, callbacks.write, callbacks.seek);
}

FLAC_API FLAC__bool FLAC__metadata_chain_write_with_callbacks_and_tempfile(FLAC__Metadata_Chain *chain, FLAC__bool use_padding, FLAC__IOHandle handle, FLAC__IOCallbacks callbacks, FLAC__IOHandle temp_handle, FLAC__IOCallbacks temp_callbacks)
{
	FLAC__off_t current_length;

	FLAC__ASSERT(0 != chain);

	if (chain->is_ogg) { /* cannot write back to Ogg FLAC yet */
		chain->status = FLAC__METADATA_CHAIN_STATUS_INTERNAL_ERROR;
		return false;
	}

	if (0 != chain->filename) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_READ_WRITE_MISMATCH;
		return false;
	}

	if (0 == callbacks.read || 0 == callbacks.seek || 0 == callbacks.eof) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_INVALID_CALLBACKS;
		return false;
	}
	if (0 == temp_callbacks.write) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_INVALID_CALLBACKS;
		return false;
	}

	if (!FLAC__metadata_chain_check_if_tempfile_needed(chain, use_padding)) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_WRONG_WRITE_CALL;
		return false;
	}

	current_length = chain_prepare_for_write_(chain, use_padding);

	/* a return value of 0 means there was an error; chain->status is already set */
	if (0 == current_length)
		return false;

	FLAC__ASSERT(current_length != chain->initial_length);

	/* rewind */
	if(0 != callbacks.seek(handle, 0, SEEK_SET)) {
		chain->status = FLAC__METADATA_CHAIN_STATUS_SEEK_ERROR;
		return false;
	}

	if(!chain_rewrite_file_cb_(chain, handle, callbacks.read, callbacks.seek, callbacks.eof, temp_handle, temp_callbacks.write))
		return false;

	/* recompute lengths and offsets */
	{
		const FLAC__Metadata_Node *node;
		chain->initial_length = current_length;
		chain->last_offset = chain->first_offset;
		for(node = chain->head; node; node = node->next)
			chain->last_offset += (FLAC__STREAM_METADATA_HEADER_LENGTH + node->data->length);
	}

	return true;
}

FLAC_API void FLAC__metadata_chain_merge_padding(FLAC__Metadata_Chain *chain)
{
	FLAC__Metadata_Node *node;

	FLAC__ASSERT(0 != chain);

	for(node = chain->head; node; ) {
		if(!chain_merge_adjacent_padding_(chain, node))
			node = node->next;
	}
}

FLAC_API void FLAC__metadata_chain_sort_padding(FLAC__Metadata_Chain *chain)
{
	FLAC__Metadata_Node *node, *save;
	uint32_t i;

	FLAC__ASSERT(0 != chain);

	/*
	 * Don't try and be too smart... this simple algo is good enough for
	 * the small number of nodes that we deal with.
	 */
	for(i = 0, node = chain->head; i < chain->nodes; i++) {
		if(node->data->type == FLAC__METADATA_TYPE_PADDING) {
			save = node->next;
			chain_remove_node_(chain, node);
			chain_append_node_(chain, node);
			node = save;
		}
		else {
			node = node->next;
		}
	}

	FLAC__metadata_chain_merge_padding(chain);
}


FLAC_API FLAC__Metadata_Iterator *FLAC__metadata_iterator_new(void)
{
	FLAC__Metadata_Iterator *iterator = calloc(1, sizeof(FLAC__Metadata_Iterator));

	/* calloc() implies:
		iterator->current = 0;
		iterator->chain = 0;
	*/

	return iterator;
}

FLAC_API void FLAC__metadata_iterator_delete(FLAC__Metadata_Iterator *iterator)
{
	FLAC__ASSERT(0 != iterator);

	free(iterator);
}

FLAC_API void FLAC__metadata_iterator_init(FLAC__Metadata_Iterator *iterator, FLAC__Metadata_Chain *chain)
{
	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != chain);
	FLAC__ASSERT(0 != chain->head);

	iterator->chain = chain;
	iterator->current = chain->head;
}

FLAC_API FLAC__bool FLAC__metadata_iterator_next(FLAC__Metadata_Iterator *iterator)
{
	FLAC__ASSERT(0 != iterator);

	if(0 == iterator->current || 0 == iterator->current->next)
		return false;

	iterator->current = iterator->current->next;
	return true;
}

FLAC_API FLAC__bool FLAC__metadata_iterator_prev(FLAC__Metadata_Iterator *iterator)
{
	FLAC__ASSERT(0 != iterator);

	if(0 == iterator->current || 0 == iterator->current->prev)
		return false;

	iterator->current = iterator->current->prev;
	return true;
}

FLAC_API FLAC__MetadataType FLAC__metadata_iterator_get_block_type(const FLAC__Metadata_Iterator *iterator)
{
	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->current);
	FLAC__ASSERT(0 != iterator->current->data);

	return iterator->current->data->type;
}

FLAC_API FLAC__StreamMetadata *FLAC__metadata_iterator_get_block(FLAC__Metadata_Iterator *iterator)
{
	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->current);

	return iterator->current->data;
}

FLAC_API FLAC__bool FLAC__metadata_iterator_set_block(FLAC__Metadata_Iterator *iterator, FLAC__StreamMetadata *block)
{
	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != block);
	return FLAC__metadata_iterator_delete_block(iterator, false) && FLAC__metadata_iterator_insert_block_after(iterator, block);
}

FLAC_API FLAC__bool FLAC__metadata_iterator_delete_block(FLAC__Metadata_Iterator *iterator, FLAC__bool replace_with_padding)
{
	FLAC__Metadata_Node *save;

	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->current);

	if(0 == iterator->current->prev) {
		FLAC__ASSERT(iterator->current->data->type == FLAC__METADATA_TYPE_STREAMINFO);
		return false;
	}

	save = iterator->current->prev;

	if(replace_with_padding) {
		FLAC__metadata_object_delete_data(iterator->current->data);
		iterator->current->data->type = FLAC__METADATA_TYPE_PADDING;
	}
	else {
		chain_delete_node_(iterator->chain, iterator->current);
	}

	iterator->current = save;
	return true;
}

FLAC_API FLAC__bool FLAC__metadata_iterator_insert_block_before(FLAC__Metadata_Iterator *iterator, FLAC__StreamMetadata *block)
{
	FLAC__Metadata_Node *node;

	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->current);
	FLAC__ASSERT(0 != block);

	if(block->type == FLAC__METADATA_TYPE_STREAMINFO)
		return false;

	if(0 == iterator->current->prev) {
		FLAC__ASSERT(iterator->current->data->type == FLAC__METADATA_TYPE_STREAMINFO);
		return false;
	}

	if(0 == (node = node_new_()))
		return false;

	node->data = block;
	iterator_insert_node_(iterator, node);
	iterator->current = node;
	return true;
}

FLAC_API FLAC__bool FLAC__metadata_iterator_insert_block_after(FLAC__Metadata_Iterator *iterator, FLAC__StreamMetadata *block)
{
	FLAC__Metadata_Node *node;

	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->current);
	FLAC__ASSERT(0 != block);

	if(block->type == FLAC__METADATA_TYPE_STREAMINFO)
		return false;

	if(0 == (node = node_new_()))
		return false;

	node->data = block;
	iterator_insert_node_after_(iterator, node);
	iterator->current = node;
	return true;
}


/****************************************************************************
 *
 * Local function definitions
 *
 ***************************************************************************/

void pack_uint32_(FLAC__uint32 val, FLAC__byte *b, uint32_t bytes)
{
	uint32_t i;

	b += bytes;

	for(i = 0; i < bytes; i++) {
		*(--b) = (FLAC__byte)(val & 0xff);
		val >>= 8;
	}
}

void pack_uint32_little_endian_(FLAC__uint32 val, FLAC__byte *b, uint32_t bytes)
{
	uint32_t i;

	for(i = 0; i < bytes; i++) {
		*(b++) = (FLAC__byte)(val & 0xff);
		val >>= 8;
	}
}

void pack_uint64_(FLAC__uint64 val, FLAC__byte *b, uint32_t bytes)
{
	uint32_t i;

	b += bytes;

	for(i = 0; i < bytes; i++) {
		*(--b) = (FLAC__byte)(val & 0xff);
		val >>= 8;
	}
}

FLAC__uint32 unpack_uint32_(FLAC__byte *b, uint32_t bytes)
{
	FLAC__uint32 ret = 0;
	uint32_t i;

	for(i = 0; i < bytes; i++)
		ret = (ret << 8) | (FLAC__uint32)(*b++);

	return ret;
}

FLAC__uint32 unpack_uint32_little_endian_(FLAC__byte *b, uint32_t bytes)
{
	FLAC__uint32 ret = 0;
	uint32_t i;

	b += bytes;

	for(i = 0; i < bytes; i++)
		ret = (ret << 8) | (FLAC__uint32)(*--b);

	return ret;
}

FLAC__uint64 unpack_uint64_(FLAC__byte *b, uint32_t bytes)
{
	FLAC__uint64 ret = 0;
	uint32_t i;

	for(i = 0; i < bytes; i++)
		ret = (ret << 8) | (FLAC__uint64)(*b++);

	return ret;
}

FLAC__bool read_metadata_block_header_(FLAC__Metadata_SimpleIterator *iterator)
{
	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->file);

	if(!read_metadata_block_header_cb_((FLAC__IOHandle)iterator->file, (FLAC__IOCallback_Read)fread, &iterator->is_last, &iterator->type, &iterator->length)) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
		return false;
	}

	return true;
}

FLAC__bool read_metadata_block_data_(FLAC__Metadata_SimpleIterator *iterator, FLAC__StreamMetadata *block)
{
	FLAC__ASSERT(0 != iterator);
	FLAC__ASSERT(0 != iterator->file);

	iterator->status = read_metadata_block_data_cb_((FLAC__IOHandle)iterator->file, (FLAC__IOCallback_Read)fread, fseek_wrapper_, block);

	return (iterator->status == FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK);
}

FLAC__bool read_metadata_block_header_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__bool *is_last, FLAC__MetadataType *type, uint32_t *length)
{
	FLAC__byte raw_header[FLAC__STREAM_METADATA_HEADER_LENGTH];

	if(read_cb(raw_header, 1, FLAC__STREAM_METADATA_HEADER_LENGTH, handle) != FLAC__STREAM_METADATA_HEADER_LENGTH)
		return false;

	*is_last = raw_header[0] & 0x80? true : false;
	*type = (FLAC__MetadataType)(raw_header[0] & 0x7f);
	*length = unpack_uint32_(raw_header + 1, 3);

	/* Note that we don't check:
	 *    if(iterator->type >= FLAC__METADATA_TYPE_UNDEFINED)
	 * we just will read in an opaque block
	 */

	return true;
}

FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__IOCallback_Seek seek_cb, FLAC__StreamMetadata *block)
{
	switch(block->type) {
		case FLAC__METADATA_TYPE_STREAMINFO:
			return read_metadata_block_data_streaminfo_cb_(handle, read_cb, &block->data.stream_info);
		case FLAC__METADATA_TYPE_PADDING:
			return read_metadata_block_data_padding_cb_(handle, seek_cb, &block->data.padding, block->length);
		case FLAC__METADATA_TYPE_APPLICATION:
			return read_metadata_block_data_application_cb_(handle, read_cb, &block->data.application, block->length);
		case FLAC__METADATA_TYPE_SEEKTABLE:
			return read_metadata_block_data_seektable_cb_(handle, read_cb, &block->data.seek_table, block->length);
		case FLAC__METADATA_TYPE_VORBIS_COMMENT:
			return read_metadata_block_data_vorbis_comment_cb_(handle, read_cb, seek_cb, &block->data.vorbis_comment, block->length);
		case FLAC__METADATA_TYPE_CUESHEET:
			return read_metadata_block_data_cuesheet_cb_(handle, read_cb, &block->data.cue_sheet);
		case FLAC__METADATA_TYPE_PICTURE:
			return read_metadata_block_data_picture_cb_(handle, read_cb, &block->data.picture);
		default:
			return read_metadata_block_data_unknown_cb_(handle, read_cb, &block->data.unknown, block->length);
	}
}

FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_streaminfo_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_StreamInfo *block)
{
	FLAC__byte buffer[FLAC__STREAM_METADATA_STREAMINFO_LENGTH], *b;

	if(read_cb(buffer, 1, FLAC__STREAM_METADATA_STREAMINFO_LENGTH, handle) != FLAC__STREAM_METADATA_STREAMINFO_LENGTH)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;

	b = buffer;

	/* we are using hardcoded numbers for simplicity but we should
	 * probably eventually write a bit-level unpacker and use the
	 * _STREAMINFO_ constants.
	 */
	block->min_blocksize = unpack_uint32_(b, 2); b += 2;
	block->max_blocksize = unpack_uint32_(b, 2); b += 2;
	block->min_framesize = unpack_uint32_(b, 3); b += 3;
	block->max_framesize = unpack_uint32_(b, 3); b += 3;
	block->sample_rate = (unpack_uint32_(b, 2) << 4) | ((uint32_t)(b[2] & 0xf0) >> 4);
	block->channels = (uint32_t)((b[2] & 0x0e) >> 1) + 1;
	block->bits_per_sample = ((((uint32_t)(b[2] & 0x01)) << 4) | (((uint32_t)(b[3] & 0xf0)) >> 4)) + 1;
	block->total_samples = (((FLAC__uint64)(b[3] & 0x0f)) << 32) | unpack_uint64_(b+4, 4);
	memcpy(block->md5sum, b+8, 16);

	return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK;
}

FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_padding_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Seek seek_cb, FLAC__StreamMetadata_Padding *block, uint32_t block_length)
{
	(void)block; /* nothing to do; we don't care about reading the padding bytes */

	if(0 != seek_cb(handle, block_length, SEEK_CUR))
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;

	return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK;
}

FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_application_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_Application *block, uint32_t block_length)
{
	const uint32_t id_bytes = FLAC__STREAM_METADATA_APPLICATION_ID_LEN / 8;

	if(read_cb(block->id, 1, id_bytes, handle) != id_bytes)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;

	if(block_length < id_bytes)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;

	block_length -= id_bytes;

	if(block_length == 0) {
		block->data = 0;
	}
	else {
		if(0 == (block->data = malloc(block_length)))
			return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR;

		if(read_cb(block->data, 1, block_length, handle) != block_length)
			return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	}

	return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK;
}

FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_seektable_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_SeekTable *block, uint32_t block_length)
{
	uint32_t i;
	FLAC__byte buffer[FLAC__STREAM_METADATA_SEEKPOINT_LENGTH];

	FLAC__ASSERT(block_length % FLAC__STREAM_METADATA_SEEKPOINT_LENGTH == 0);

	block->num_points = block_length / FLAC__STREAM_METADATA_SEEKPOINT_LENGTH;

	if(block->num_points == 0)
		block->points = 0;
	else if(0 == (block->points = safe_malloc_mul_2op_p(block->num_points, /*times*/sizeof(FLAC__StreamMetadata_SeekPoint))))
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR;

	for(i = 0; i < block->num_points; i++) {
		if(read_cb(buffer, 1, FLAC__STREAM_METADATA_SEEKPOINT_LENGTH, handle) != FLAC__STREAM_METADATA_SEEKPOINT_LENGTH)
			return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
		/* some MAGIC NUMBERs here */
		block->points[i].sample_number = unpack_uint64_(buffer, 8);
		block->points[i].stream_offset = unpack_uint64_(buffer+8, 8);
		block->points[i].frame_samples = unpack_uint32_(buffer+16, 2);
	}

	return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK;
}

FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_vorbis_comment_entry_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_VorbisComment_Entry *entry, uint32_t max_length)
{
	const uint32_t entry_length_len = FLAC__STREAM_METADATA_VORBIS_COMMENT_ENTRY_LENGTH_LEN / 8;
	FLAC__byte buffer[4]; /* magic number is asserted below */

	FLAC__ASSERT(FLAC__STREAM_METADATA_VORBIS_COMMENT_ENTRY_LENGTH_LEN / 8 == sizeof(buffer));

	if(max_length < entry_length_len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_BAD_METADATA;

	max_length -= entry_length_len;
	if(read_cb(buffer, 1, entry_length_len, handle) != entry_length_len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	entry->length = unpack_uint32_little_endian_(buffer, entry_length_len);
	if(max_length < entry->length) {
		entry->length = 0;
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_BAD_METADATA;
	} else max_length -= entry->length;

	if(0 != entry->entry)
		free(entry->entry);

	if(entry->length == 0) {
		entry->entry = 0;
	}
	else {
		if(0 == (entry->entry = safe_malloc_add_2op_(entry->length, /*+*/1)))
			return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR;

		if(read_cb(entry->entry, 1, entry->length, handle) != entry->length)
			return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;

		entry->entry[entry->length] = '\0';
	}

	return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK;
}

FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_vorbis_comment_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__IOCallback_Seek seek_cb, FLAC__StreamMetadata_VorbisComment *block, uint32_t block_length)
{
	uint32_t i;
	FLAC__Metadata_SimpleIteratorStatus status;
	const uint32_t num_comments_len = FLAC__STREAM_METADATA_VORBIS_COMMENT_NUM_COMMENTS_LEN / 8;
	FLAC__byte buffer[4]; /* magic number is asserted below */

	FLAC__ASSERT(FLAC__STREAM_METADATA_VORBIS_COMMENT_NUM_COMMENTS_LEN / 8 == sizeof(buffer));

	status = read_metadata_block_data_vorbis_comment_entry_cb_(handle, read_cb, &(block->vendor_string), block_length);
	if(block_length >= 4)
		block_length -= 4;
	if(status == FLAC__METADATA_SIMPLE_ITERATOR_STATUS_BAD_METADATA)
		goto skip;
	else if(status != FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK)
		return status;
	block_length -= block->vendor_string.length;

	if(block_length < num_comments_len) goto skip; else block_length -= num_comments_len;
	if(read_cb(buffer, 1, num_comments_len, handle) != num_comments_len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	block->num_comments = unpack_uint32_little_endian_(buffer, num_comments_len);

	if(block->num_comments == 0) {
		block->comments = 0;
	}
	else if(0 == (block->comments = calloc(block->num_comments, sizeof(FLAC__StreamMetadata_VorbisComment_Entry)))) {
		block->num_comments = 0;
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR;
	}

	for(i = 0; i < block->num_comments; i++) {
		status = read_metadata_block_data_vorbis_comment_entry_cb_(handle, read_cb, block->comments + i, block_length);
		if(block_length >= 4) block_length -= 4;
		if(status == FLAC__METADATA_SIMPLE_ITERATOR_STATUS_BAD_METADATA) {
			block->num_comments = i;
			goto skip;
		}
		else if(status != FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK) return status;
		block_length -= block->comments[i].length;
	}

  skip:
	if(block_length > 0) {
		/* bad metadata */
		if(0 != seek_cb(handle, block_length, SEEK_CUR))
			return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
	}

	return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK;
}

FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_cuesheet_track_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_CueSheet_Track *track)
{
	uint32_t i, len;
	FLAC__byte buffer[32]; /* asserted below that this is big enough */

	FLAC__ASSERT(sizeof(buffer) >= sizeof(FLAC__uint64));
	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_CUESHEET_INDEX_RESERVED_LEN/8);
	FLAC__ASSERT(sizeof(buffer) >= (FLAC__STREAM_METADATA_CUESHEET_TRACK_TYPE_LEN + FLAC__STREAM_METADATA_CUESHEET_TRACK_PRE_EMPHASIS_LEN + FLAC__STREAM_METADATA_CUESHEET_TRACK_RESERVED_LEN) / 8);

	FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_TRACK_OFFSET_LEN % 8 == 0);
	len = FLAC__STREAM_METADATA_CUESHEET_TRACK_OFFSET_LEN / 8;
	if(read_cb(buffer, 1, len, handle) != len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	track->offset = unpack_uint64_(buffer, len);

	FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_TRACK_NUMBER_LEN % 8 == 0);
	len = FLAC__STREAM_METADATA_CUESHEET_TRACK_NUMBER_LEN / 8;
	if(read_cb(buffer, 1, len, handle) != len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	track->number = (FLAC__byte)unpack_uint32_(buffer, len);

	FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_TRACK_ISRC_LEN % 8 == 0);
	len = FLAC__STREAM_METADATA_CUESHEET_TRACK_ISRC_LEN / 8;
	if(read_cb(track->isrc, 1, len, handle) != len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;

	FLAC__ASSERT((FLAC__STREAM_METADATA_CUESHEET_TRACK_TYPE_LEN + FLAC__STREAM_METADATA_CUESHEET_TRACK_PRE_EMPHASIS_LEN + FLAC__STREAM_METADATA_CUESHEET_TRACK_RESERVED_LEN) % 8 == 0);
	len = (FLAC__STREAM_METADATA_CUESHEET_TRACK_TYPE_LEN + FLAC__STREAM_METADATA_CUESHEET_TRACK_PRE_EMPHASIS_LEN + FLAC__STREAM_METADATA_CUESHEET_TRACK_RESERVED_LEN) / 8;
	if(read_cb(buffer, 1, len, handle) != len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_TRACK_TYPE_LEN == 1);
	FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_TRACK_PRE_EMPHASIS_LEN == 1);
	track->type = buffer[0] >> 7;
	track->pre_emphasis = (buffer[0] >> 6) & 1;

	FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_TRACK_NUM_INDICES_LEN % 8 == 0);
	len = FLAC__STREAM_METADATA_CUESHEET_TRACK_NUM_INDICES_LEN / 8;
	if(read_cb(buffer, 1, len, handle) != len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	track->num_indices = (FLAC__byte)unpack_uint32_(buffer, len);

	if(track->num_indices == 0) {
		track->indices = 0;
	}
	else if(0 == (track->indices = calloc(track->num_indices, sizeof(FLAC__StreamMetadata_CueSheet_Index))))
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR;

	for(i = 0; i < track->num_indices; i++) {
		FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_INDEX_OFFSET_LEN % 8 == 0);
		len = FLAC__STREAM_METADATA_CUESHEET_INDEX_OFFSET_LEN / 8;
		if(read_cb(buffer, 1, len, handle) != len)
			return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
		track->indices[i].offset = unpack_uint64_(buffer, len);

		FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_INDEX_NUMBER_LEN % 8 == 0);
		len = FLAC__STREAM_METADATA_CUESHEET_INDEX_NUMBER_LEN / 8;
		if(read_cb(buffer, 1, len, handle) != len)
			return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
		track->indices[i].number = (FLAC__byte)unpack_uint32_(buffer, len);

		FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_INDEX_RESERVED_LEN % 8 == 0);
		len = FLAC__STREAM_METADATA_CUESHEET_INDEX_RESERVED_LEN / 8;
		if(read_cb(buffer, 1, len, handle) != len)
			return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	}

	return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK;
}

FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_cuesheet_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_CueSheet *block)
{
	uint32_t i, len;
	FLAC__Metadata_SimpleIteratorStatus status;
	FLAC__byte buffer[1024]; /* MSVC needs a constant expression so we put a magic number and assert */

	FLAC__ASSERT((FLAC__STREAM_METADATA_CUESHEET_IS_CD_LEN + FLAC__STREAM_METADATA_CUESHEET_RESERVED_LEN)/8 <= sizeof(buffer));
	FLAC__ASSERT(sizeof(FLAC__uint64) <= sizeof(buffer));

	FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_MEDIA_CATALOG_NUMBER_LEN % 8 == 0);
	len = FLAC__STREAM_METADATA_CUESHEET_MEDIA_CATALOG_NUMBER_LEN / 8;
	if(read_cb(block->media_catalog_number, 1, len, handle) != len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;

	FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_LEAD_IN_LEN % 8 == 0);
	len = FLAC__STREAM_METADATA_CUESHEET_LEAD_IN_LEN / 8;
	if(read_cb(buffer, 1, len, handle) != len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	block->lead_in = unpack_uint64_(buffer, len);

	FLAC__ASSERT((FLAC__STREAM_METADATA_CUESHEET_IS_CD_LEN + FLAC__STREAM_METADATA_CUESHEET_RESERVED_LEN) % 8 == 0);
	len = (FLAC__STREAM_METADATA_CUESHEET_IS_CD_LEN + FLAC__STREAM_METADATA_CUESHEET_RESERVED_LEN) / 8;
	if(read_cb(buffer, 1, len, handle) != len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	block->is_cd = buffer[0]&0x80? true : false;

	FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_NUM_TRACKS_LEN % 8 == 0);
	len = FLAC__STREAM_METADATA_CUESHEET_NUM_TRACKS_LEN / 8;
	if(read_cb(buffer, 1, len, handle) != len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	block->num_tracks = unpack_uint32_(buffer, len);

	if(block->num_tracks == 0) {
		block->tracks = 0;
	}
	else if(0 == (block->tracks = calloc(block->num_tracks, sizeof(FLAC__StreamMetadata_CueSheet_Track))))
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR;

	for(i = 0; i < block->num_tracks; i++) {
		if(FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK != (status = read_metadata_block_data_cuesheet_track_cb_(handle, read_cb, block->tracks + i)))
			return status;
	}

	return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK;
}

static FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_picture_cstring_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__byte **data, FLAC__uint32 *length, FLAC__uint32 length_len)
{
	FLAC__byte buffer[sizeof(FLAC__uint32)];

	FLAC__ASSERT(0 != data);
	FLAC__ASSERT(length_len%8 == 0);

	length_len /= 8; /* convert to bytes */

	FLAC__ASSERT(sizeof(buffer) >= length_len);

	if(read_cb(buffer, 1, length_len, handle) != length_len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	*length = unpack_uint32_(buffer, length_len);

	if(0 != *data)
		free(*data);

	if(0 == (*data = safe_malloc_add_2op_(*length, /*+*/1)))
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR;

	if(*length > 0) {
		if(read_cb(*data, 1, *length, handle) != *length)
			return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	}

	(*data)[*length] = '\0';

	return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK;
}

FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_picture_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_Picture *block)
{
	FLAC__Metadata_SimpleIteratorStatus status;
	FLAC__byte buffer[4]; /* asserted below that this is big enough */
	FLAC__uint32 len;

	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_PICTURE_TYPE_LEN/8);
	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_PICTURE_WIDTH_LEN/8);
	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_PICTURE_HEIGHT_LEN/8);
	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_PICTURE_DEPTH_LEN/8);
	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_PICTURE_COLORS_LEN/8);

	FLAC__ASSERT(FLAC__STREAM_METADATA_PICTURE_TYPE_LEN % 8 == 0);
	len = FLAC__STREAM_METADATA_PICTURE_TYPE_LEN / 8;
	if(read_cb(buffer, 1, len, handle) != len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	block->type = (FLAC__StreamMetadata_Picture_Type)unpack_uint32_(buffer, len);

	if((status = read_metadata_block_data_picture_cstring_cb_(handle, read_cb, (FLAC__byte**)(&(block->mime_type)), &len, FLAC__STREAM_METADATA_PICTURE_MIME_TYPE_LENGTH_LEN)) != FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK)
		return status;

	if((status = read_metadata_block_data_picture_cstring_cb_(handle, read_cb, &(block->description), &len, FLAC__STREAM_METADATA_PICTURE_DESCRIPTION_LENGTH_LEN)) != FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK)
		return status;

	FLAC__ASSERT(FLAC__STREAM_METADATA_PICTURE_WIDTH_LEN % 8 == 0);
	len = FLAC__STREAM_METADATA_PICTURE_WIDTH_LEN / 8;
	if(read_cb(buffer, 1, len, handle) != len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	block->width = unpack_uint32_(buffer, len);

	FLAC__ASSERT(FLAC__STREAM_METADATA_PICTURE_HEIGHT_LEN % 8 == 0);
	len = FLAC__STREAM_METADATA_PICTURE_HEIGHT_LEN / 8;
	if(read_cb(buffer, 1, len, handle) != len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	block->height = unpack_uint32_(buffer, len);

	FLAC__ASSERT(FLAC__STREAM_METADATA_PICTURE_DEPTH_LEN % 8 == 0);
	len = FLAC__STREAM_METADATA_PICTURE_DEPTH_LEN / 8;
	if(read_cb(buffer, 1, len, handle) != len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	block->depth = unpack_uint32_(buffer, len);

	FLAC__ASSERT(FLAC__STREAM_METADATA_PICTURE_COLORS_LEN % 8 == 0);
	len = FLAC__STREAM_METADATA_PICTURE_COLORS_LEN / 8;
	if(read_cb(buffer, 1, len, handle) != len)
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	block->colors = unpack_uint32_(buffer, len);

	/* for convenience we use read_metadata_block_data_picture_cstring_cb_() even though it adds an extra terminating NUL we don't use */
	if((status = read_metadata_block_data_picture_cstring_cb_(handle, read_cb, &(block->data), &(block->data_length), FLAC__STREAM_METADATA_PICTURE_DATA_LENGTH_LEN)) != FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK)
		return status;

	return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK;
}

FLAC__Metadata_SimpleIteratorStatus read_metadata_block_data_unknown_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__StreamMetadata_Unknown *block, uint32_t block_length)
{
	if(block_length == 0) {
		block->data = 0;
	}
	else {
		if(0 == (block->data = malloc(block_length)))
			return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR;

		if(read_cb(block->data, 1, block_length, handle) != block_length)
			return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
	}

	return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK;
}

FLAC__bool write_metadata_block_header_(FILE *file, FLAC__Metadata_SimpleIteratorStatus *status, const FLAC__StreamMetadata *block)
{
	FLAC__ASSERT(0 != file);
	FLAC__ASSERT(0 != status);

	if(!write_metadata_block_header_cb_((FLAC__IOHandle)file, (FLAC__IOCallback_Write)fwrite, block)) {
		*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_WRITE_ERROR;
		return false;
	}

	return true;
}

FLAC__bool write_metadata_block_data_(FILE *file, FLAC__Metadata_SimpleIteratorStatus *status, const FLAC__StreamMetadata *block)
{
	FLAC__ASSERT(0 != file);
	FLAC__ASSERT(0 != status);

	if (write_metadata_block_data_cb_((FLAC__IOHandle)file, (FLAC__IOCallback_Write)fwrite, block)) {
		*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK;
		return true;
	}
	else {
		*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_WRITE_ERROR;
		return false;
	}
}

FLAC__bool write_metadata_block_header_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata *block)
{
	FLAC__byte buffer[FLAC__STREAM_METADATA_HEADER_LENGTH];

	FLAC__ASSERT(block->length < (1u << FLAC__STREAM_METADATA_LENGTH_LEN));
	/* double protection */
	if(block->length >= (1u << FLAC__STREAM_METADATA_LENGTH_LEN))
		return false;

	buffer[0] = (block->is_last? 0x80 : 0) | (FLAC__byte)block->type;
	pack_uint32_(block->length, buffer + 1, 3);

	if(write_cb(buffer, 1, FLAC__STREAM_METADATA_HEADER_LENGTH, handle) != FLAC__STREAM_METADATA_HEADER_LENGTH)
		return false;

	return true;
}

FLAC__bool write_metadata_block_data_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata *block)
{
	FLAC__ASSERT(0 != block);

	switch(block->type) {
		case FLAC__METADATA_TYPE_STREAMINFO:
			return write_metadata_block_data_streaminfo_cb_(handle, write_cb, &block->data.stream_info);
		case FLAC__METADATA_TYPE_PADDING:
			return write_metadata_block_data_padding_cb_(handle, write_cb, &block->data.padding, block->length);
		case FLAC__METADATA_TYPE_APPLICATION:
			return write_metadata_block_data_application_cb_(handle, write_cb, &block->data.application, block->length);
		case FLAC__METADATA_TYPE_SEEKTABLE:
			return write_metadata_block_data_seektable_cb_(handle, write_cb, &block->data.seek_table);
		case FLAC__METADATA_TYPE_VORBIS_COMMENT:
			return write_metadata_block_data_vorbis_comment_cb_(handle, write_cb, &block->data.vorbis_comment);
		case FLAC__METADATA_TYPE_CUESHEET:
			return write_metadata_block_data_cuesheet_cb_(handle, write_cb, &block->data.cue_sheet);
		case FLAC__METADATA_TYPE_PICTURE:
			return write_metadata_block_data_picture_cb_(handle, write_cb, &block->data.picture);
		default:
			return write_metadata_block_data_unknown_cb_(handle, write_cb, &block->data.unknown, block->length);
	}
}

FLAC__bool write_metadata_block_data_streaminfo_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_StreamInfo *block)
{
	FLAC__byte buffer[FLAC__STREAM_METADATA_STREAMINFO_LENGTH];
	const uint32_t channels1 = block->channels - 1;
	const uint32_t bps1 = block->bits_per_sample - 1;

	/* we are using hardcoded numbers for simplicity but we should
	 * probably eventually write a bit-level packer and use the
	 * _STREAMINFO_ constants.
	 */
	pack_uint32_(block->min_blocksize, buffer, 2);
	pack_uint32_(block->max_blocksize, buffer+2, 2);
	pack_uint32_(block->min_framesize, buffer+4, 3);
	pack_uint32_(block->max_framesize, buffer+7, 3);
	buffer[10] = (block->sample_rate >> 12) & 0xff;
	buffer[11] = (block->sample_rate >> 4) & 0xff;
	buffer[12] = ((block->sample_rate & 0x0f) << 4) | (channels1 << 1) | (bps1 >> 4);
	buffer[13] = (FLAC__byte)(((bps1 & 0x0f) << 4) | ((block->total_samples >> 32) & 0x0f));
	pack_uint32_((FLAC__uint32)block->total_samples, buffer+14, 4);
	memcpy(buffer+18, block->md5sum, 16);

	if(write_cb(buffer, 1, FLAC__STREAM_METADATA_STREAMINFO_LENGTH, handle) != FLAC__STREAM_METADATA_STREAMINFO_LENGTH)
		return false;

	return true;
}

FLAC__bool write_metadata_block_data_padding_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_Padding *block, uint32_t block_length)
{
	uint32_t i, n = block_length;
	FLAC__byte buffer[1024];

	(void)block;

	memset(buffer, 0, 1024);

	for(i = 0; i < n/1024; i++)
		if(write_cb(buffer, 1, 1024, handle) != 1024)
			return false;

	n %= 1024;

	if(write_cb(buffer, 1, n, handle) != n)
		return false;

	return true;
}

FLAC__bool write_metadata_block_data_application_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_Application *block, uint32_t block_length)
{
	const uint32_t id_bytes = FLAC__STREAM_METADATA_APPLICATION_ID_LEN / 8;

	if(write_cb(block->id, 1, id_bytes, handle) != id_bytes)
		return false;

	block_length -= id_bytes;

	if(write_cb(block->data, 1, block_length, handle) != block_length)
		return false;

	return true;
}

FLAC__bool write_metadata_block_data_seektable_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_SeekTable *block)
{
	uint32_t i;
	FLAC__byte buffer[FLAC__STREAM_METADATA_SEEKPOINT_LENGTH];

	for(i = 0; i < block->num_points; i++) {
		/* some MAGIC NUMBERs here */
		pack_uint64_(block->points[i].sample_number, buffer, 8);
		pack_uint64_(block->points[i].stream_offset, buffer+8, 8);
		pack_uint32_(block->points[i].frame_samples, buffer+16, 2);
		if(write_cb(buffer, 1, FLAC__STREAM_METADATA_SEEKPOINT_LENGTH, handle) != FLAC__STREAM_METADATA_SEEKPOINT_LENGTH)
			return false;
	}

	return true;
}

FLAC__bool write_metadata_block_data_vorbis_comment_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_VorbisComment *block)
{
	uint32_t i;
	const uint32_t entry_length_len = FLAC__STREAM_METADATA_VORBIS_COMMENT_ENTRY_LENGTH_LEN / 8;
	const uint32_t num_comments_len = FLAC__STREAM_METADATA_VORBIS_COMMENT_NUM_COMMENTS_LEN / 8;
	FLAC__byte buffer[4]; /* magic number is asserted below */

	FLAC__ASSERT(flac_max(FLAC__STREAM_METADATA_VORBIS_COMMENT_ENTRY_LENGTH_LEN, FLAC__STREAM_METADATA_VORBIS_COMMENT_NUM_COMMENTS_LEN) / 8 == sizeof(buffer));

	pack_uint32_little_endian_(block->vendor_string.length, buffer, entry_length_len);
	if(write_cb(buffer, 1, entry_length_len, handle) != entry_length_len)
		return false;
	if(write_cb(block->vendor_string.entry, 1, block->vendor_string.length, handle) != block->vendor_string.length)
		return false;

	pack_uint32_little_endian_(block->num_comments, buffer, num_comments_len);
	if(write_cb(buffer, 1, num_comments_len, handle) != num_comments_len)
		return false;

	for(i = 0; i < block->num_comments; i++) {
		pack_uint32_little_endian_(block->comments[i].length, buffer, entry_length_len);
		if(write_cb(buffer, 1, entry_length_len, handle) != entry_length_len)
			return false;
		if(write_cb(block->comments[i].entry, 1, block->comments[i].length, handle) != block->comments[i].length)
			return false;
	}

	return true;
}

FLAC__bool write_metadata_block_data_cuesheet_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_CueSheet *block)
{
	uint32_t i, j, len;
	FLAC__byte buffer[1024]; /* asserted below that this is big enough */

	FLAC__ASSERT(sizeof(buffer) >= sizeof(FLAC__uint64));
	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_CUESHEET_RESERVED_LEN/8);
	FLAC__ASSERT(sizeof(buffer) >= (FLAC__STREAM_METADATA_CUESHEET_TRACK_TYPE_LEN + FLAC__STREAM_METADATA_CUESHEET_TRACK_PRE_EMPHASIS_LEN + FLAC__STREAM_METADATA_CUESHEET_IS_CD_LEN + FLAC__STREAM_METADATA_CUESHEET_TRACK_RESERVED_LEN)/8);
	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_CUESHEET_INDEX_RESERVED_LEN/8);

	FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_MEDIA_CATALOG_NUMBER_LEN % 8 == 0);
	len = FLAC__STREAM_METADATA_CUESHEET_MEDIA_CATALOG_NUMBER_LEN / 8;
	if(write_cb(block->media_catalog_number, 1, len, handle) != len)
		return false;

	FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_LEAD_IN_LEN % 8 == 0);
	len = FLAC__STREAM_METADATA_CUESHEET_LEAD_IN_LEN / 8;
	pack_uint64_(block->lead_in, buffer, len);
	if(write_cb(buffer, 1, len, handle) != len)
		return false;

	FLAC__ASSERT((FLAC__STREAM_METADATA_CUESHEET_IS_CD_LEN + FLAC__STREAM_METADATA_CUESHEET_RESERVED_LEN) % 8 == 0);
	len = (FLAC__STREAM_METADATA_CUESHEET_IS_CD_LEN + FLAC__STREAM_METADATA_CUESHEET_RESERVED_LEN) / 8;
	memset(buffer, 0, len);
	if(block->is_cd)
		buffer[0] |= 0x80;
	if(write_cb(buffer, 1, len, handle) != len)
		return false;

	FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_NUM_TRACKS_LEN % 8 == 0);
	len = FLAC__STREAM_METADATA_CUESHEET_NUM_TRACKS_LEN / 8;
	pack_uint32_(block->num_tracks, buffer, len);
	if(write_cb(buffer, 1, len, handle) != len)
		return false;

	for(i = 0; i < block->num_tracks; i++) {
		FLAC__StreamMetadata_CueSheet_Track *track = block->tracks + i;

		FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_TRACK_OFFSET_LEN % 8 == 0);
		len = FLAC__STREAM_METADATA_CUESHEET_TRACK_OFFSET_LEN / 8;
		pack_uint64_(track->offset, buffer, len);
		if(write_cb(buffer, 1, len, handle) != len)
			return false;

		FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_TRACK_NUMBER_LEN % 8 == 0);
		len = FLAC__STREAM_METADATA_CUESHEET_TRACK_NUMBER_LEN / 8;
		pack_uint32_(track->number, buffer, len);
		if(write_cb(buffer, 1, len, handle) != len)
			return false;

		FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_TRACK_ISRC_LEN % 8 == 0);
		len = FLAC__STREAM_METADATA_CUESHEET_TRACK_ISRC_LEN / 8;
		if(write_cb(track->isrc, 1, len, handle) != len)
			return false;

		FLAC__ASSERT((FLAC__STREAM_METADATA_CUESHEET_TRACK_TYPE_LEN + FLAC__STREAM_METADATA_CUESHEET_TRACK_PRE_EMPHASIS_LEN + FLAC__STREAM_METADATA_CUESHEET_TRACK_RESERVED_LEN) % 8 == 0);
		len = (FLAC__STREAM_METADATA_CUESHEET_TRACK_TYPE_LEN + FLAC__STREAM_METADATA_CUESHEET_TRACK_PRE_EMPHASIS_LEN + FLAC__STREAM_METADATA_CUESHEET_TRACK_RESERVED_LEN) / 8;
		memset(buffer, 0, len);
		buffer[0] = (track->type << 7) | (track->pre_emphasis << 6);
		if(write_cb(buffer, 1, len, handle) != len)
			return false;

		FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_TRACK_NUM_INDICES_LEN % 8 == 0);
		len = FLAC__STREAM_METADATA_CUESHEET_TRACK_NUM_INDICES_LEN / 8;
		pack_uint32_(track->num_indices, buffer, len);
		if(write_cb(buffer, 1, len, handle) != len)
			return false;

		for(j = 0; j < track->num_indices; j++) {
			FLAC__StreamMetadata_CueSheet_Index *indx = track->indices + j;

			FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_INDEX_OFFSET_LEN % 8 == 0);
			len = FLAC__STREAM_METADATA_CUESHEET_INDEX_OFFSET_LEN / 8;
			pack_uint64_(indx->offset, buffer, len);
			if(write_cb(buffer, 1, len, handle) != len)
				return false;

			FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_INDEX_NUMBER_LEN % 8 == 0);
			len = FLAC__STREAM_METADATA_CUESHEET_INDEX_NUMBER_LEN / 8;
			pack_uint32_(indx->number, buffer, len);
			if(write_cb(buffer, 1, len, handle) != len)
				return false;

			FLAC__ASSERT(FLAC__STREAM_METADATA_CUESHEET_INDEX_RESERVED_LEN % 8 == 0);
			len = FLAC__STREAM_METADATA_CUESHEET_INDEX_RESERVED_LEN / 8;
			memset(buffer, 0, len);
			if(write_cb(buffer, 1, len, handle) != len)
				return false;
		}
	}

	return true;
}

FLAC__bool write_metadata_block_data_picture_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_Picture *block)
{
	uint32_t len;
	size_t slen;
	FLAC__byte buffer[4]; /* magic number is asserted below */

	FLAC__ASSERT(0 == FLAC__STREAM_METADATA_PICTURE_TYPE_LEN%8);
	FLAC__ASSERT(0 == FLAC__STREAM_METADATA_PICTURE_MIME_TYPE_LENGTH_LEN%8);
	FLAC__ASSERT(0 == FLAC__STREAM_METADATA_PICTURE_DESCRIPTION_LENGTH_LEN%8);
	FLAC__ASSERT(0 == FLAC__STREAM_METADATA_PICTURE_WIDTH_LEN%8);
	FLAC__ASSERT(0 == FLAC__STREAM_METADATA_PICTURE_HEIGHT_LEN%8);
	FLAC__ASSERT(0 == FLAC__STREAM_METADATA_PICTURE_DEPTH_LEN%8);
	FLAC__ASSERT(0 == FLAC__STREAM_METADATA_PICTURE_COLORS_LEN%8);
	FLAC__ASSERT(0 == FLAC__STREAM_METADATA_PICTURE_DATA_LENGTH_LEN%8);
	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_PICTURE_TYPE_LEN/8);
	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_PICTURE_MIME_TYPE_LENGTH_LEN/8);
	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_PICTURE_DESCRIPTION_LENGTH_LEN/8);
	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_PICTURE_WIDTH_LEN/8);
	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_PICTURE_HEIGHT_LEN/8);
	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_PICTURE_DEPTH_LEN/8);
	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_PICTURE_COLORS_LEN/8);
	FLAC__ASSERT(sizeof(buffer) >= FLAC__STREAM_METADATA_PICTURE_DATA_LENGTH_LEN/8);

	len = FLAC__STREAM_METADATA_PICTURE_TYPE_LEN/8;
	pack_uint32_(block->type, buffer, len);
	if(write_cb(buffer, 1, len, handle) != len)
		return false;

	len = FLAC__STREAM_METADATA_PICTURE_MIME_TYPE_LENGTH_LEN/8;
	slen = strlen(block->mime_type);
	pack_uint32_(slen, buffer, len);
	if(write_cb(buffer, 1, len, handle) != len)
		return false;
	if(write_cb(block->mime_type, 1, slen, handle) != slen)
		return false;

	len = FLAC__STREAM_METADATA_PICTURE_DESCRIPTION_LENGTH_LEN/8;
	slen = strlen((const char *)block->description);
	pack_uint32_(slen, buffer, len);
	if(write_cb(buffer, 1, len, handle) != len)
		return false;
	if(write_cb(block->description, 1, slen, handle) != slen)
		return false;

	len = FLAC__STREAM_METADATA_PICTURE_WIDTH_LEN/8;
	pack_uint32_(block->width, buffer, len);
	if(write_cb(buffer, 1, len, handle) != len)
		return false;

	len = FLAC__STREAM_METADATA_PICTURE_HEIGHT_LEN/8;
	pack_uint32_(block->height, buffer, len);
	if(write_cb(buffer, 1, len, handle) != len)
		return false;

	len = FLAC__STREAM_METADATA_PICTURE_DEPTH_LEN/8;
	pack_uint32_(block->depth, buffer, len);
	if(write_cb(buffer, 1, len, handle) != len)
		return false;

	len = FLAC__STREAM_METADATA_PICTURE_COLORS_LEN/8;
	pack_uint32_(block->colors, buffer, len);
	if(write_cb(buffer, 1, len, handle) != len)
		return false;

	len = FLAC__STREAM_METADATA_PICTURE_DATA_LENGTH_LEN/8;
	pack_uint32_(block->data_length, buffer, len);
	if(write_cb(buffer, 1, len, handle) != len)
		return false;
	if(write_cb(block->data, 1, block->data_length, handle) != block->data_length)
		return false;

	return true;
}

FLAC__bool write_metadata_block_data_unknown_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Write write_cb, const FLAC__StreamMetadata_Unknown *block, uint32_t block_length)
{
	if(write_cb(block->data, 1, block_length, handle) != block_length)
		return false;

	return true;
}

FLAC__bool write_metadata_block_stationary_(FLAC__Metadata_SimpleIterator *iterator, const FLAC__StreamMetadata *block)
{
	if(0 != fseeko(iterator->file, iterator->offset[iterator->depth], SEEK_SET)) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
		return false;
	}

	if(!write_metadata_block_header_(iterator->file, &iterator->status, block))
		return false;

	if(!write_metadata_block_data_(iterator->file, &iterator->status, block))
		return false;

	if(0 != fseeko(iterator->file, iterator->offset[iterator->depth], SEEK_SET)) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
		return false;
	}

	return read_metadata_block_header_(iterator);
}

FLAC__bool write_metadata_block_stationary_with_padding_(FLAC__Metadata_SimpleIterator *iterator, FLAC__StreamMetadata *block, uint32_t padding_length, FLAC__bool padding_is_last)
{
	FLAC__StreamMetadata *padding;

	if(0 != fseeko(iterator->file, iterator->offset[iterator->depth], SEEK_SET)) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
		return false;
	}

	block->is_last = false;

	if(!write_metadata_block_header_(iterator->file, &iterator->status, block))
		return false;

	if(!write_metadata_block_data_(iterator->file, &iterator->status, block))
		return false;

	if(0 == (padding = FLAC__metadata_object_new(FLAC__METADATA_TYPE_PADDING)))
		return FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR;

	padding->is_last = padding_is_last;
	padding->length = padding_length;

	if(!write_metadata_block_header_(iterator->file, &iterator->status, padding)) {
		FLAC__metadata_object_delete(padding);
		return false;
	}

	if(!write_metadata_block_data_(iterator->file, &iterator->status, padding)) {
		FLAC__metadata_object_delete(padding);
		return false;
	}

	FLAC__metadata_object_delete(padding);

	if(0 != fseeko(iterator->file, iterator->offset[iterator->depth], SEEK_SET)) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
		return false;
	}

	return read_metadata_block_header_(iterator);
}

FLAC__bool rewrite_whole_file_(FLAC__Metadata_SimpleIterator *iterator, FLAC__StreamMetadata *block, FLAC__bool append)
{
	FILE *tempfile = NULL;
	char *tempfilename = NULL;
	int fixup_is_last_code = 0; /* 0 => no need to change any is_last flags */
	FLAC__off_t fixup_is_last_flag_offset = -1;

	FLAC__ASSERT(0 != block || append == false);

	if(iterator->is_last) {
		if(append) {
			fixup_is_last_code = 1; /* 1 => clear the is_last flag at the following offset */
			fixup_is_last_flag_offset = iterator->offset[iterator->depth];
		}
		else if(0 == block) {
			simple_iterator_push_(iterator);
			if(!FLAC__metadata_simple_iterator_prev(iterator)) {
				(void)simple_iterator_pop_(iterator);
				return false;
			}
			fixup_is_last_code = -1; /* -1 => set the is_last the flag at the following offset */
			fixup_is_last_flag_offset = iterator->offset[iterator->depth];
			if(!simple_iterator_pop_(iterator))
				return false;
		}
	}

	if(!simple_iterator_copy_file_prefix_(iterator, &tempfile, &tempfilename, append))
		return false;

	if(0 != block) {
		if(!write_metadata_block_header_(tempfile, &iterator->status, block)) {
			cleanup_tempfile_(&tempfile, &tempfilename);
			return false;
		}

		if(!write_metadata_block_data_(tempfile, &iterator->status, block)) {
			cleanup_tempfile_(&tempfile, &tempfilename);
			return false;
		}
	}

	if(!simple_iterator_copy_file_postfix_(iterator, &tempfile, &tempfilename, fixup_is_last_code, fixup_is_last_flag_offset, block==0))
		return false;

	if(append)
		return FLAC__metadata_simple_iterator_next(iterator);

	return true;
}

void simple_iterator_push_(FLAC__Metadata_SimpleIterator *iterator)
{
	FLAC__ASSERT(iterator->depth+1 < SIMPLE_ITERATOR_MAX_PUSH_DEPTH);
	iterator->offset[iterator->depth+1] = iterator->offset[iterator->depth];
	iterator->depth++;
}

FLAC__bool simple_iterator_pop_(FLAC__Metadata_SimpleIterator *iterator)
{
	FLAC__ASSERT(iterator->depth > 0);
	iterator->depth--;
	if(0 != fseeko(iterator->file, iterator->offset[iterator->depth], SEEK_SET)) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
		return false;
	}

	return read_metadata_block_header_(iterator);
}

/* return meanings:
 * 0: ok
 * 1: read error
 * 2: seek error
 * 3: not a FLAC file
 */
uint32_t seek_to_first_metadata_block_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__IOCallback_Seek seek_cb)
{
	FLAC__byte buffer[4];
	size_t n;
	uint32_t i;

	FLAC__ASSERT(FLAC__STREAM_SYNC_LENGTH == sizeof(buffer));

	/* skip any id3v2 tag */
	errno = 0;
	n = read_cb(buffer, 1, 4, handle);
	if(errno)
		return 1;
	else if(n != 4)
		return 3;
	else if(0 == memcmp(buffer, "ID3", 3)) {
		uint32_t tag_length = 0;

		/* skip to the tag length */
		if(seek_cb(handle, 2, SEEK_CUR) < 0)
			return 2;

		/* read the length */
		for(i = 0; i < 4; i++) {
			if(read_cb(buffer, 1, 1, handle) < 1 || buffer[0] & 0x80)
				return 1;
			tag_length <<= 7;
			tag_length |= (buffer[0] & 0x7f);
		}

		/* skip the rest of the tag */
		if(seek_cb(handle, tag_length, SEEK_CUR) < 0)
			return 2;

		/* read the stream sync code */
		errno = 0;
		n = read_cb(buffer, 1, 4, handle);
		if(errno)
			return 1;
		else if(n != 4)
			return 3;
	}

	/* check for the fLaC signature */
	if(0 == memcmp(FLAC__STREAM_SYNC_STRING, buffer, FLAC__STREAM_SYNC_LENGTH))
		return 0;
	else
		return 3;
}

uint32_t seek_to_first_metadata_block_(FILE *f)
{
	return seek_to_first_metadata_block_cb_((FLAC__IOHandle)f, (FLAC__IOCallback_Read)fread, fseek_wrapper_);
}

FLAC__bool simple_iterator_copy_file_prefix_(FLAC__Metadata_SimpleIterator *iterator, FILE **tempfile, char **tempfilename, FLAC__bool append)
{
	const FLAC__off_t offset_end = append? iterator->offset[iterator->depth] + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH + (FLAC__off_t)iterator->length : iterator->offset[iterator->depth];

	if(0 != fseeko(iterator->file, 0, SEEK_SET)) {
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
		return false;
	}
	if(!open_tempfile_(iterator->filename, iterator->tempfile_path_prefix, tempfile, tempfilename, &iterator->status)) {
		cleanup_tempfile_(tempfile, tempfilename);
		return false;
	}
	if(!copy_n_bytes_from_file_(iterator->file, *tempfile, offset_end, &iterator->status)) {
		cleanup_tempfile_(tempfile, tempfilename);
		return false;
	}

	return true;
}

FLAC__bool simple_iterator_copy_file_postfix_(FLAC__Metadata_SimpleIterator *iterator, FILE **tempfile, char **tempfilename, int fixup_is_last_code, FLAC__off_t fixup_is_last_flag_offset, FLAC__bool backup)
{
	FLAC__off_t save_offset = iterator->offset[iterator->depth];
	FLAC__ASSERT(0 != *tempfile);

	if(0 != fseeko(iterator->file, save_offset + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH + (FLAC__off_t)iterator->length, SEEK_SET)) {
		cleanup_tempfile_(tempfile, tempfilename);
		iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
		return false;
	}
	if(!copy_remaining_bytes_from_file_(iterator->file, *tempfile, &iterator->status)) {
		cleanup_tempfile_(tempfile, tempfilename);
		return false;
	}

	if(fixup_is_last_code != 0) {
		/*
		 * if code == 1, it means a block was appended to the end so
		 *   we have to clear the is_last flag of the previous block
		 * if code == -1, it means the last block was deleted so
		 *   we have to set the is_last flag of the previous block
		 */
		/* MAGIC NUMBERs here; we know the is_last flag is the high bit of the byte at this location */
		FLAC__byte x;
		if(0 != fseeko(*tempfile, fixup_is_last_flag_offset, SEEK_SET)) {
			cleanup_tempfile_(tempfile, tempfilename);
			iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
			return false;
		}
		if(fread(&x, 1, 1, *tempfile) != 1) {
			cleanup_tempfile_(tempfile, tempfilename);
			iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
			return false;
		}
		if(fixup_is_last_code > 0) {
			FLAC__ASSERT(x & 0x80);
			x &= 0x7f;
		}
		else {
			FLAC__ASSERT(!(x & 0x80));
			x |= 0x80;
		}
		if(0 != fseeko(*tempfile, fixup_is_last_flag_offset, SEEK_SET)) {
			cleanup_tempfile_(tempfile, tempfilename);
			iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR;
			return false;
		}
		if(local__fwrite(&x, 1, 1, *tempfile) != 1) {
			cleanup_tempfile_(tempfile, tempfilename);
			iterator->status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_WRITE_ERROR;
			return false;
		}
	}

	(void)fclose(iterator->file);

	if(!transport_tempfile_(iterator->filename, tempfile, tempfilename, &iterator->status))
		return false;

	if(iterator->has_stats)
		set_file_stats_(iterator->filename, &iterator->stats);

	if(!simple_iterator_prime_input_(iterator, !iterator->is_writable))
		return false;
	if(backup) {
		while(iterator->offset[iterator->depth] + (FLAC__off_t)FLAC__STREAM_METADATA_HEADER_LENGTH + (FLAC__off_t)iterator->length < save_offset)
			if(!FLAC__metadata_simple_iterator_next(iterator))
				return false;
		return true;
	}
	else {
		/* move the iterator to it's original block faster by faking a push, then doing a pop_ */
		FLAC__ASSERT(iterator->depth == 0);
		iterator->offset[0] = save_offset;
		iterator->depth++;
		return simple_iterator_pop_(iterator);
	}
}

FLAC__bool copy_n_bytes_from_file_(FILE *file, FILE *tempfile, FLAC__off_t bytes, FLAC__Metadata_SimpleIteratorStatus *status)
{
	FLAC__byte buffer[8192];
	size_t n;

	FLAC__ASSERT(bytes >= 0);
	while(bytes > 0) {
		n = flac_min(sizeof(buffer), (size_t)bytes);
		if(fread(buffer, 1, n, file) != n) {
			*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
			return false;
		}
		if(local__fwrite(buffer, 1, n, tempfile) != n) {
			*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_WRITE_ERROR;
			return false;
		}
		bytes -= n;
	}

	return true;
}

FLAC__bool copy_n_bytes_from_file_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__IOHandle temp_handle, FLAC__IOCallback_Write temp_write_cb, FLAC__off_t bytes, FLAC__Metadata_SimpleIteratorStatus *status)
{
	FLAC__byte buffer[8192];
	size_t n;

	FLAC__ASSERT(bytes >= 0);
	while(bytes > 0) {
		n = flac_min(sizeof(buffer), (size_t)bytes);
		if(read_cb(buffer, 1, n, handle) != n) {
			*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
			return false;
		}
		if(temp_write_cb(buffer, 1, n, temp_handle) != n) {
			*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_WRITE_ERROR;
			return false;
		}
		bytes -= n;
	}

	return true;
}

FLAC__bool copy_remaining_bytes_from_file_(FILE *file, FILE *tempfile, FLAC__Metadata_SimpleIteratorStatus *status)
{
	FLAC__byte buffer[8192];
	size_t n;

	while(!feof(file)) {
		n = fread(buffer, 1, sizeof(buffer), file);
		if(n == 0 && !feof(file)) {
			*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
			return false;
		}
		if(n > 0 && local__fwrite(buffer, 1, n, tempfile) != n) {
			*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_WRITE_ERROR;
			return false;
		}
	}

	return true;
}

FLAC__bool copy_remaining_bytes_from_file_cb_(FLAC__IOHandle handle, FLAC__IOCallback_Read read_cb, FLAC__IOCallback_Eof eof_cb, FLAC__IOHandle temp_handle, FLAC__IOCallback_Write temp_write_cb, FLAC__Metadata_SimpleIteratorStatus *status)
{
	FLAC__byte buffer[8192];
	size_t n;

	while(!eof_cb(handle)) {
		n = read_cb(buffer, 1, sizeof(buffer), handle);
		if(n == 0 && !eof_cb(handle)) {
			*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR;
			return false;
		}
		if(n > 0 && temp_write_cb(buffer, 1, n, temp_handle) != n) {
			*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_WRITE_ERROR;
			return false;
		}
	}

	return true;
}

static int
local_snprintf(char *str, size_t size, const char *fmt, ...)
{
	va_list va;
	int rc;

#if defined _MSC_VER
	if (size == 0)
		return 1024;
#endif

	va_start (va, fmt);

#if defined _MSC_VER
	rc = vsnprintf_s (str, size, _TRUNCATE, fmt, va);
	if (rc < 0)
		rc = size - 1;
#elif defined __MINGW32__
	rc = __mingw_vsnprintf (str, size, fmt, va);
#else
	rc = vsnprintf (str, size, fmt, va);
#endif
	va_end (va);

	return rc;
}

FLAC__bool open_tempfile_(const char *filename, const char *tempfile_path_prefix, FILE **tempfile, char **tempfilename, FLAC__Metadata_SimpleIteratorStatus *status)
{
	static const char *tempfile_suffix = ".metadata_edit";
	if(0 == tempfile_path_prefix) {
		size_t dest_len = strlen(filename) + strlen(tempfile_suffix) + 1;
		if(0 == (*tempfilename = safe_malloc_(dest_len))) {
			*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR;
			return false;
		}
		local_snprintf(*tempfilename, dest_len, "%s%s", filename, tempfile_suffix);
	}
	else {
		const char *p = strrchr(filename, '/');
		size_t dest_len;
		if(0 == p)
			p = filename;
		else
			p++;

		dest_len = strlen(tempfile_path_prefix) + strlen(p) + strlen(tempfile_suffix) + 2;

		if(0 == (*tempfilename = safe_malloc_(dest_len))) {
			*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR;
			return false;
		}
		local_snprintf(*tempfilename, dest_len, "%s/%s%s", tempfile_path_prefix, p, tempfile_suffix);
	}

	if(0 == (*tempfile = flac_fopen(*tempfilename, "w+b"))) {
		*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_ERROR_OPENING_FILE;
		return false;
	}

	return true;
}

FLAC__bool transport_tempfile_(const char *filename, FILE **tempfile, char **tempfilename, FLAC__Metadata_SimpleIteratorStatus *status)
{
	FLAC__ASSERT(0 != filename);
	FLAC__ASSERT(0 != tempfile);
	FLAC__ASSERT(0 != *tempfile);
	FLAC__ASSERT(0 != tempfilename);
	FLAC__ASSERT(0 != *tempfilename);
	FLAC__ASSERT(0 != status);

	(void)fclose(*tempfile);
	*tempfile = 0;

#if defined _MSC_VER || defined __BORLANDC__ || defined __MINGW32__ || defined __EMX__
	/* on some flavors of windows, flac_rename() will fail if the destination already exists */
	if(flac_unlink(filename) < 0) {
		cleanup_tempfile_(tempfile, tempfilename);
		*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_UNLINK_ERROR;
		return false;
	}
#endif

	/*@@@ to fully support the tempfile_path_prefix we need to update this piece to actually copy across filesystems instead of just flac_rename(): */
	if(0 != flac_rename(*tempfilename, filename)) {
		cleanup_tempfile_(tempfile, tempfilename);
		*status = FLAC__METADATA_SIMPLE_ITERATOR_STATUS_RENAME_ERROR;
		return false;
	}

	cleanup_tempfile_(tempfile, tempfilename);

	return true;
}

void cleanup_tempfile_(FILE **tempfile, char **tempfilename)
{
	if(0 != *tempfile) {
		(void)fclose(*tempfile);
		*tempfile = 0;
	}

	if(0 != *tempfilename) {
		(void)flac_unlink(*tempfilename);
		free(*tempfilename);
		*tempfilename = 0;
	}
}

FLAC__bool get_file_stats_(const char *filename, struct flac_stat_s *stats)
{
	FLAC__ASSERT(0 != filename);
	FLAC__ASSERT(0 != stats);
	return (0 == flac_stat(filename, stats));
}

void set_file_stats_(const char *filename, struct flac_stat_s *stats)
{
	struct utimbuf srctime;

	FLAC__ASSERT(0 != filename);
	FLAC__ASSERT(0 != stats);

	srctime.actime = stats->st_atime;
	srctime.modtime = stats->st_mtime;
	(void)flac_chmod(filename, stats->st_mode);
	(void)flac_utime(filename, &srctime);
#if !defined _MSC_VER && !defined __BORLANDC__ && !defined __MINGW32__
	FLAC_CHECK_RETURN(chown(filename, stats->st_uid, -1));
	FLAC_CHECK_RETURN(chown(filename, -1, stats->st_gid));
#endif
}

int fseek_wrapper_(FLAC__IOHandle handle, FLAC__int64 offset, int whence)
{
	return fseeko((FILE*)handle, (FLAC__off_t)offset, whence);
}

FLAC__int64 ftell_wrapper_(FLAC__IOHandle handle)
{
	return ftello((FILE*)handle);
}

FLAC__Metadata_ChainStatus get_equivalent_status_(FLAC__Metadata_SimpleIteratorStatus status)
{
	switch(status) {
		case FLAC__METADATA_SIMPLE_ITERATOR_STATUS_OK:
			return FLAC__METADATA_CHAIN_STATUS_OK;
		case FLAC__METADATA_SIMPLE_ITERATOR_STATUS_ILLEGAL_INPUT:
			return FLAC__METADATA_CHAIN_STATUS_ILLEGAL_INPUT;
		case FLAC__METADATA_SIMPLE_ITERATOR_STATUS_ERROR_OPENING_FILE:
			return FLAC__METADATA_CHAIN_STATUS_ERROR_OPENING_FILE;
		case FLAC__METADATA_SIMPLE_ITERATOR_STATUS_NOT_A_FLAC_FILE:
			return FLAC__METADATA_CHAIN_STATUS_NOT_A_FLAC_FILE;
		case FLAC__METADATA_SIMPLE_ITERATOR_STATUS_NOT_WRITABLE:
			return FLAC__METADATA_CHAIN_STATUS_NOT_WRITABLE;
		case FLAC__METADATA_SIMPLE_ITERATOR_STATUS_BAD_METADATA:
			return FLAC__METADATA_CHAIN_STATUS_BAD_METADATA;
		case FLAC__METADATA_SIMPLE_ITERATOR_STATUS_READ_ERROR:
			return FLAC__METADATA_CHAIN_STATUS_READ_ERROR;
		case FLAC__METADATA_SIMPLE_ITERATOR_STATUS_SEEK_ERROR:
			return FLAC__METADATA_CHAIN_STATUS_SEEK_ERROR;
		case FLAC__METADATA_SIMPLE_ITERATOR_STATUS_WRITE_ERROR:
			return FLAC__METADATA_CHAIN_STATUS_WRITE_ERROR;
		case FLAC__METADATA_SIMPLE_ITERATOR_STATUS_RENAME_ERROR:
			return FLAC__METADATA_CHAIN_STATUS_RENAME_ERROR;
		case FLAC__METADATA_SIMPLE_ITERATOR_STATUS_UNLINK_ERROR:
			return FLAC__METADATA_CHAIN_STATUS_UNLINK_ERROR;
		case FLAC__METADATA_SIMPLE_ITERATOR_STATUS_MEMORY_ALLOCATION_ERROR:
			return FLAC__METADATA_CHAIN_STATUS_MEMORY_ALLOCATION_ERROR;
		case FLAC__METADATA_SIMPLE_ITERATOR_STATUS_INTERNAL_ERROR:
		default:
			return FLAC__METADATA_CHAIN_STATUS_INTERNAL_ERROR;
	}
}

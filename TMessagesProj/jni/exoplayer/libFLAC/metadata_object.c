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

#include <stdlib.h>
#include <string.h>

#include "private/metadata.h"
#include "private/memory.h"

#include "FLAC/assert.h"
#include "share/alloc.h"
#include "share/compat.h"

/* Alias the first (in share/alloc.h) to the second (in src/libFLAC/memory.c). */
#define safe_malloc_mul_2op_ safe_malloc_mul_2op_p


/****************************************************************************
 *
 * Local routines
 *
 ***************************************************************************/

/* copy bytes:
 *  from = NULL  && bytes = 0
 *       to <- NULL
 *  from != NULL && bytes > 0
 *       to <- copy of from
 *  else ASSERT
 * malloc error leaves 'to' unchanged
 */
static FLAC__bool copy_bytes_(FLAC__byte **to, const FLAC__byte *from, uint32_t bytes)
{
	FLAC__ASSERT(to != NULL);
	if (bytes > 0 && from != NULL) {
		FLAC__byte *x;
		if ((x = safe_malloc_(bytes)) == NULL)
			return false;
		memcpy(x, from, bytes);
		*to = x;
	}
	else {
		*to = 0;
	}
	return true;
}

#if 0 /* UNUSED */
/* like copy_bytes_(), but free()s the original '*to' if the copy succeeds and the original '*to' is non-NULL */
static FLAC__bool free_copy_bytes_(FLAC__byte **to, const FLAC__byte *from, uint32_t bytes)
{
	FLAC__byte *copy;
	FLAC__ASSERT(to != NULL);
	if (copy_bytes_(&copy, from, bytes)) {
		free(*to);
		*to = copy;
		return true;
	}
	else
		return false;
}
#endif

/* reallocate entry to 1 byte larger and add a terminating NUL */
/* realloc() failure leaves entry unchanged */
static FLAC__bool ensure_null_terminated_(FLAC__byte **entry, uint32_t length)
{
	FLAC__byte *x = safe_realloc_add_2op_(*entry, length, /*+*/1);
	if (x != NULL) {
		x[length] = '\0';
		*entry = x;
		return true;
	}
	else
		return false;
}

/* copies the NUL-terminated C-string 'from' to '*to', leaving '*to'
 * unchanged if malloc fails, free()ing the original '*to' if it
 * succeeds and the original '*to' was not NULL
 */
static FLAC__bool copy_cstring_(char **to, const char *from)
{
	char *copy = strdup(from);
	FLAC__ASSERT(to != NULL);
	if (copy) {
		free(*to);
		*to = copy;
		return true;
	}
	else
		return false;
}

static FLAC__bool copy_vcentry_(FLAC__StreamMetadata_VorbisComment_Entry *to, const FLAC__StreamMetadata_VorbisComment_Entry *from)
{
	to->length = from->length;
	if (from->entry == 0) {
		FLAC__ASSERT(from->length == 0);
		to->entry = 0;
	}
	else {
		FLAC__byte *x;
		FLAC__ASSERT(from->length > 0);
		if ((x = safe_malloc_add_2op_(from->length, /*+*/1)) == NULL)
			return false;
		memcpy(x, from->entry, from->length);
		x[from->length] = '\0';
		to->entry = x;
	}
	return true;
}

static FLAC__bool copy_track_(FLAC__StreamMetadata_CueSheet_Track *to, const FLAC__StreamMetadata_CueSheet_Track *from)
{
	memcpy(to, from, sizeof(FLAC__StreamMetadata_CueSheet_Track));
	if (from->indices == 0) {
		FLAC__ASSERT(from->num_indices == 0);
	}
	else {
		FLAC__StreamMetadata_CueSheet_Index *x;
		FLAC__ASSERT(from->num_indices > 0);
		if ((x = safe_malloc_mul_2op_p(from->num_indices, /*times*/sizeof(FLAC__StreamMetadata_CueSheet_Index))) == NULL)
			return false;
		memcpy(x, from->indices, from->num_indices * sizeof(FLAC__StreamMetadata_CueSheet_Index));
		to->indices = x;
	}
	return true;
}

static void seektable_calculate_length_(FLAC__StreamMetadata *object)
{
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_SEEKTABLE);

	object->length = object->data.seek_table.num_points * FLAC__STREAM_METADATA_SEEKPOINT_LENGTH;
}

static FLAC__StreamMetadata_SeekPoint *seekpoint_array_new_(uint32_t num_points)
{
	FLAC__StreamMetadata_SeekPoint *object_array;

	FLAC__ASSERT(num_points > 0);

	object_array = safe_malloc_mul_2op_p(num_points, /*times*/sizeof(FLAC__StreamMetadata_SeekPoint));

	if (object_array != NULL) {
		uint32_t i;
		for (i = 0; i < num_points; i++) {
			object_array[i].sample_number = FLAC__STREAM_METADATA_SEEKPOINT_PLACEHOLDER;
			object_array[i].stream_offset = 0;
			object_array[i].frame_samples = 0;
		}
	}

	return object_array;
}

static void vorbiscomment_calculate_length_(FLAC__StreamMetadata *object)
{
	uint32_t i;

	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_VORBIS_COMMENT);

	object->length = (FLAC__STREAM_METADATA_VORBIS_COMMENT_ENTRY_LENGTH_LEN) / 8;
	object->length += object->data.vorbis_comment.vendor_string.length;
	object->length += (FLAC__STREAM_METADATA_VORBIS_COMMENT_NUM_COMMENTS_LEN) / 8;
	for (i = 0; i < object->data.vorbis_comment.num_comments; i++) {
		object->length += (FLAC__STREAM_METADATA_VORBIS_COMMENT_ENTRY_LENGTH_LEN / 8);
		object->length += object->data.vorbis_comment.comments[i].length;
	}
}

static FLAC__StreamMetadata_VorbisComment_Entry *vorbiscomment_entry_array_new_(uint32_t num_comments)
{
	FLAC__ASSERT(num_comments > 0);

	return safe_calloc_(num_comments, sizeof(FLAC__StreamMetadata_VorbisComment_Entry));
}

static void vorbiscomment_entry_array_delete_(FLAC__StreamMetadata_VorbisComment_Entry *object_array, uint32_t num_comments)
{
	uint32_t i;

	FLAC__ASSERT(object_array != NULL && num_comments > 0);

	for (i = 0; i < num_comments; i++)
		free(object_array[i].entry);

	free(object_array);
}

static FLAC__StreamMetadata_VorbisComment_Entry *vorbiscomment_entry_array_copy_(const FLAC__StreamMetadata_VorbisComment_Entry *object_array, uint32_t num_comments)
{
	FLAC__StreamMetadata_VorbisComment_Entry *return_array;

	FLAC__ASSERT(object_array != NULL);
	FLAC__ASSERT(num_comments > 0);

	return_array = vorbiscomment_entry_array_new_(num_comments);

	if (return_array != NULL) {
		uint32_t i;

		for (i = 0; i < num_comments; i++) {
			if (!copy_vcentry_(return_array+i, object_array+i)) {
				vorbiscomment_entry_array_delete_(return_array, num_comments);
				return 0;
			}
		}
	}

	return return_array;
}

static FLAC__bool vorbiscomment_set_entry_(FLAC__StreamMetadata *object, FLAC__StreamMetadata_VorbisComment_Entry *dest, const FLAC__StreamMetadata_VorbisComment_Entry *src, FLAC__bool copy)
{
	FLAC__byte *save;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(dest != NULL);
	FLAC__ASSERT(src != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_VORBIS_COMMENT);
	FLAC__ASSERT((src->entry != NULL && src->length > 0) || (src->entry == NULL && src->length == 0));

	save = dest->entry;

	if (src->entry != NULL) {
		if (copy) {
			/* do the copy first so that if we fail we leave the dest object untouched */
			if (!copy_vcentry_(dest, src))
				return false;
		}
		else {
			/* we have to make sure that the string we're taking over is null-terminated */

			/*
			 * Stripping the const from src->entry is OK since we're taking
			 * ownership of the pointer.  This is a hack around a deficiency
			 * in the API where the same function is used for 'copy' and
			 * 'own', but the source entry is a const pointer.  If we were
			 * precise, the 'own' flavor would be a separate function with a
			 * non-const source pointer.  But it's not, so we hack away.
			 */
			if (!ensure_null_terminated_((FLAC__byte**)(&src->entry), src->length))
				return false;
			*dest = *src;
		}
	}
	else {
		/* the src is null */
		*dest = *src;
	}

	free(save);

	vorbiscomment_calculate_length_(object);
	return true;
}

static int vorbiscomment_find_entry_from_(const FLAC__StreamMetadata *object, uint32_t offset, const char *field_name, uint32_t field_name_length)
{
	uint32_t i;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_VORBIS_COMMENT);
	FLAC__ASSERT(field_name != NULL);

	for (i = offset; i < object->data.vorbis_comment.num_comments; i++) {
		if (FLAC__metadata_object_vorbiscomment_entry_matches(object->data.vorbis_comment.comments[i], field_name, field_name_length))
			return (int)i;
	}

	return -1;
}

static void cuesheet_calculate_length_(FLAC__StreamMetadata *object)
{
	uint32_t i;

	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_CUESHEET);

	object->length = (
		FLAC__STREAM_METADATA_CUESHEET_MEDIA_CATALOG_NUMBER_LEN +
		FLAC__STREAM_METADATA_CUESHEET_LEAD_IN_LEN +
		FLAC__STREAM_METADATA_CUESHEET_IS_CD_LEN +
		FLAC__STREAM_METADATA_CUESHEET_RESERVED_LEN +
		FLAC__STREAM_METADATA_CUESHEET_NUM_TRACKS_LEN
	) / 8;

	object->length += object->data.cue_sheet.num_tracks * (
		FLAC__STREAM_METADATA_CUESHEET_TRACK_OFFSET_LEN +
		FLAC__STREAM_METADATA_CUESHEET_TRACK_NUMBER_LEN +
		FLAC__STREAM_METADATA_CUESHEET_TRACK_ISRC_LEN +
		FLAC__STREAM_METADATA_CUESHEET_TRACK_TYPE_LEN +
		FLAC__STREAM_METADATA_CUESHEET_TRACK_PRE_EMPHASIS_LEN +
		FLAC__STREAM_METADATA_CUESHEET_TRACK_RESERVED_LEN +
		FLAC__STREAM_METADATA_CUESHEET_TRACK_NUM_INDICES_LEN
	) / 8;

	for (i = 0; i < object->data.cue_sheet.num_tracks; i++) {
		object->length += object->data.cue_sheet.tracks[i].num_indices * (
			FLAC__STREAM_METADATA_CUESHEET_INDEX_OFFSET_LEN +
			FLAC__STREAM_METADATA_CUESHEET_INDEX_NUMBER_LEN +
			FLAC__STREAM_METADATA_CUESHEET_INDEX_RESERVED_LEN
		) / 8;
	}
}

static FLAC__StreamMetadata_CueSheet_Index *cuesheet_track_index_array_new_(uint32_t num_indices)
{
	FLAC__ASSERT(num_indices > 0);

	return safe_calloc_(num_indices, sizeof(FLAC__StreamMetadata_CueSheet_Index));
}

static FLAC__StreamMetadata_CueSheet_Track *cuesheet_track_array_new_(uint32_t num_tracks)
{
	FLAC__ASSERT(num_tracks > 0);

	return safe_calloc_(num_tracks, sizeof(FLAC__StreamMetadata_CueSheet_Track));
}

static void cuesheet_track_array_delete_(FLAC__StreamMetadata_CueSheet_Track *object_array, uint32_t num_tracks)
{
	uint32_t i;

	FLAC__ASSERT(object_array != NULL && num_tracks > 0);

	for (i = 0; i < num_tracks; i++) {
		if (object_array[i].indices != 0) {
			FLAC__ASSERT(object_array[i].num_indices > 0);
			free(object_array[i].indices);
		}
	}

	free(object_array);
}

static FLAC__StreamMetadata_CueSheet_Track *cuesheet_track_array_copy_(const FLAC__StreamMetadata_CueSheet_Track *object_array, uint32_t num_tracks)
{
	FLAC__StreamMetadata_CueSheet_Track *return_array;

	FLAC__ASSERT(object_array != NULL);
	FLAC__ASSERT(num_tracks > 0);

	return_array = cuesheet_track_array_new_(num_tracks);

	if (return_array != NULL) {
		uint32_t i;

		for (i = 0; i < num_tracks; i++) {
			if (!copy_track_(return_array+i, object_array+i)) {
				cuesheet_track_array_delete_(return_array, num_tracks);
				return 0;
			}
		}
	}

	return return_array;
}

static FLAC__bool cuesheet_set_track_(FLAC__StreamMetadata *object, FLAC__StreamMetadata_CueSheet_Track *dest, const FLAC__StreamMetadata_CueSheet_Track *src, FLAC__bool copy)
{
	FLAC__StreamMetadata_CueSheet_Index *save;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(dest != NULL);
	FLAC__ASSERT(src != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_CUESHEET);
	FLAC__ASSERT((src->indices != NULL && src->num_indices > 0) || (src->indices == NULL && src->num_indices == 0));

	save = dest->indices;

	/* do the copy first so that if we fail we leave the object untouched */
	if (copy) {
		if (!copy_track_(dest, src))
			return false;
	}
	else {
		*dest = *src;
	}

	free(save);

	cuesheet_calculate_length_(object);
	return true;
}


/****************************************************************************
 *
 * Metadata object routines
 *
 ***************************************************************************/

FLAC_API FLAC__StreamMetadata *FLAC__metadata_object_new(FLAC__MetadataType type)
{
	FLAC__StreamMetadata *object;

	if (type > FLAC__MAX_METADATA_TYPE)
		return 0;

	object = calloc(1, sizeof(FLAC__StreamMetadata));
	if (object != NULL) {
		object->is_last = false;
		object->type = type;
		switch(type) {
			case FLAC__METADATA_TYPE_STREAMINFO:
				object->length = FLAC__STREAM_METADATA_STREAMINFO_LENGTH;
				break;
			case FLAC__METADATA_TYPE_PADDING:
				/* calloc() took care of this for us:
				object->length = 0;
				*/
				break;
			case FLAC__METADATA_TYPE_APPLICATION:
				object->length = FLAC__STREAM_METADATA_APPLICATION_ID_LEN / 8;
				/* calloc() took care of this for us:
				object->data.application.data = 0;
				*/
				break;
			case FLAC__METADATA_TYPE_SEEKTABLE:
				/* calloc() took care of this for us:
				object->length = 0;
				object->data.seek_table.num_points = 0;
				object->data.seek_table.points = 0;
				*/
				break;
			case FLAC__METADATA_TYPE_VORBIS_COMMENT:
				object->data.vorbis_comment.vendor_string.length = (uint32_t)strlen(FLAC__VENDOR_STRING);
				if (!copy_bytes_(&object->data.vorbis_comment.vendor_string.entry, (const FLAC__byte*)FLAC__VENDOR_STRING, object->data.vorbis_comment.vendor_string.length+1)) {
					free(object);
					return 0;
				}
				vorbiscomment_calculate_length_(object);
				break;
			case FLAC__METADATA_TYPE_CUESHEET:
				cuesheet_calculate_length_(object);
				break;
			case FLAC__METADATA_TYPE_PICTURE:
				object->length = (
					FLAC__STREAM_METADATA_PICTURE_TYPE_LEN +
					FLAC__STREAM_METADATA_PICTURE_MIME_TYPE_LENGTH_LEN + /* empty mime_type string */
					FLAC__STREAM_METADATA_PICTURE_DESCRIPTION_LENGTH_LEN + /* empty description string */
					FLAC__STREAM_METADATA_PICTURE_WIDTH_LEN +
					FLAC__STREAM_METADATA_PICTURE_HEIGHT_LEN +
					FLAC__STREAM_METADATA_PICTURE_DEPTH_LEN +
					FLAC__STREAM_METADATA_PICTURE_COLORS_LEN +
					FLAC__STREAM_METADATA_PICTURE_DATA_LENGTH_LEN +
					0 /* no data */
				) / 8;
				object->data.picture.type = FLAC__STREAM_METADATA_PICTURE_TYPE_OTHER;
				object->data.picture.mime_type = 0;
				object->data.picture.description = 0;
				/* calloc() took care of this for us:
				object->data.picture.width = 0;
				object->data.picture.height = 0;
				object->data.picture.depth = 0;
				object->data.picture.colors = 0;
				object->data.picture.data_length = 0;
				object->data.picture.data = 0;
				*/
				/* now initialize mime_type and description with empty strings to make things easier on the client */
				if (!copy_cstring_(&object->data.picture.mime_type, "")) {
					free(object);
					return 0;
				}
				if (!copy_cstring_((char**)(&object->data.picture.description), "")) {
					free(object->data.picture.mime_type);
					free(object);
					return 0;
				}
				break;
			default:
				/* calloc() took care of this for us:
				object->length = 0;
				object->data.unknown.data = 0;
				*/
				break;
		}
	}

	return object;
}

FLAC_API FLAC__StreamMetadata *FLAC__metadata_object_clone(const FLAC__StreamMetadata *object)
{
	FLAC__StreamMetadata *to;

	FLAC__ASSERT(object != NULL);

	if ((to = FLAC__metadata_object_new(object->type)) != NULL) {
		to->is_last = object->is_last;
		to->type = object->type;
		to->length = object->length;
		switch(to->type) {
			case FLAC__METADATA_TYPE_STREAMINFO:
				memcpy(&to->data.stream_info, &object->data.stream_info, sizeof(FLAC__StreamMetadata_StreamInfo));
				break;
			case FLAC__METADATA_TYPE_PADDING:
				break;
			case FLAC__METADATA_TYPE_APPLICATION:
				if (to->length < FLAC__STREAM_METADATA_APPLICATION_ID_LEN / 8) { /* underflow check */
					FLAC__metadata_object_delete(to);
					return 0;
				}
				memcpy(&to->data.application.id, &object->data.application.id, FLAC__STREAM_METADATA_APPLICATION_ID_LEN / 8);
				if (!copy_bytes_(&to->data.application.data, object->data.application.data, object->length - FLAC__STREAM_METADATA_APPLICATION_ID_LEN / 8)) {
					FLAC__metadata_object_delete(to);
					return 0;
				}
				break;
			case FLAC__METADATA_TYPE_SEEKTABLE:
				to->data.seek_table.num_points = object->data.seek_table.num_points;
				if (to->data.seek_table.num_points > UINT32_MAX / sizeof(FLAC__StreamMetadata_SeekPoint)) { /* overflow check */
					FLAC__metadata_object_delete(to);
					return 0;
				}
				if (!copy_bytes_((FLAC__byte**)&to->data.seek_table.points, (FLAC__byte*)object->data.seek_table.points, object->data.seek_table.num_points * sizeof(FLAC__StreamMetadata_SeekPoint))) {
					FLAC__metadata_object_delete(to);
					return 0;
				}
				break;
			case FLAC__METADATA_TYPE_VORBIS_COMMENT:
				if (to->data.vorbis_comment.vendor_string.entry != NULL) {
					free(to->data.vorbis_comment.vendor_string.entry);
					to->data.vorbis_comment.vendor_string.entry = 0;
				}
				if (!copy_vcentry_(&to->data.vorbis_comment.vendor_string, &object->data.vorbis_comment.vendor_string)) {
					FLAC__metadata_object_delete(to);
					return 0;
				}
				if (object->data.vorbis_comment.num_comments == 0) {
					to->data.vorbis_comment.comments = 0;
				}
				else {
					to->data.vorbis_comment.comments = vorbiscomment_entry_array_copy_(object->data.vorbis_comment.comments, object->data.vorbis_comment.num_comments);
					if (to->data.vorbis_comment.comments == NULL) {
						to->data.vorbis_comment.num_comments = 0;
						FLAC__metadata_object_delete(to);
						return 0;
					}
				}
				to->data.vorbis_comment.num_comments = object->data.vorbis_comment.num_comments;
				break;
			case FLAC__METADATA_TYPE_CUESHEET:
				memcpy(&to->data.cue_sheet, &object->data.cue_sheet, sizeof(FLAC__StreamMetadata_CueSheet));
				if (object->data.cue_sheet.num_tracks == 0) {
					FLAC__ASSERT(object->data.cue_sheet.tracks == NULL);
				}
				else {
					FLAC__ASSERT(object->data.cue_sheet.tracks != 0);
					to->data.cue_sheet.tracks = cuesheet_track_array_copy_(object->data.cue_sheet.tracks, object->data.cue_sheet.num_tracks);
					if (to->data.cue_sheet.tracks == NULL) {
						FLAC__metadata_object_delete(to);
						return 0;
					}
				}
				break;
			case FLAC__METADATA_TYPE_PICTURE:
				to->data.picture.type = object->data.picture.type;
				if (!copy_cstring_(&to->data.picture.mime_type, object->data.picture.mime_type)) {
					FLAC__metadata_object_delete(to);
					return 0;
				}
				if (!copy_cstring_((char**)(&to->data.picture.description), (const char*)object->data.picture.description)) {
					FLAC__metadata_object_delete(to);
					return 0;
				}
				to->data.picture.width = object->data.picture.width;
				to->data.picture.height = object->data.picture.height;
				to->data.picture.depth = object->data.picture.depth;
				to->data.picture.colors = object->data.picture.colors;
				to->data.picture.data_length = object->data.picture.data_length;
				if (!copy_bytes_((&to->data.picture.data), object->data.picture.data, object->data.picture.data_length)) {
					FLAC__metadata_object_delete(to);
					return 0;
				}
				break;
			default:
				if (!copy_bytes_(&to->data.unknown.data, object->data.unknown.data, object->length)) {
					FLAC__metadata_object_delete(to);
					return 0;
				}
				break;
		}
	}

	return to;
}

void FLAC__metadata_object_delete_data(FLAC__StreamMetadata *object)
{
	FLAC__ASSERT(object != NULL);

	switch(object->type) {
		case FLAC__METADATA_TYPE_STREAMINFO:
		case FLAC__METADATA_TYPE_PADDING:
			break;
		case FLAC__METADATA_TYPE_APPLICATION:
			if (object->data.application.data != NULL) {
				free(object->data.application.data);
				object->data.application.data = NULL;
			}
			break;
		case FLAC__METADATA_TYPE_SEEKTABLE:
			if (object->data.seek_table.points != NULL) {
				free(object->data.seek_table.points);
				object->data.seek_table.points = NULL;
			}
			break;
		case FLAC__METADATA_TYPE_VORBIS_COMMENT:
			if (object->data.vorbis_comment.vendor_string.entry != NULL) {
				free(object->data.vorbis_comment.vendor_string.entry);
				object->data.vorbis_comment.vendor_string.entry = 0;
			}
			if (object->data.vorbis_comment.comments != NULL) {
				FLAC__ASSERT(object->data.vorbis_comment.num_comments > 0);
				vorbiscomment_entry_array_delete_(object->data.vorbis_comment.comments, object->data.vorbis_comment.num_comments);
				object->data.vorbis_comment.comments = NULL;
				object->data.vorbis_comment.num_comments = 0;
			}
			break;
		case FLAC__METADATA_TYPE_CUESHEET:
			if (object->data.cue_sheet.tracks != NULL) {
				FLAC__ASSERT(object->data.cue_sheet.num_tracks > 0);
				cuesheet_track_array_delete_(object->data.cue_sheet.tracks, object->data.cue_sheet.num_tracks);
				object->data.cue_sheet.tracks = NULL;
				object->data.cue_sheet.num_tracks = 0;
			}
			break;
		case FLAC__METADATA_TYPE_PICTURE:
			if (object->data.picture.mime_type != NULL) {
				free(object->data.picture.mime_type);
				object->data.picture.mime_type = NULL;
			}
			if (object->data.picture.description != NULL) {
				free(object->data.picture.description);
				object->data.picture.description = NULL;
			}
			if (object->data.picture.data != NULL) {
				free(object->data.picture.data);
				object->data.picture.data = NULL;
			}
			break;
		default:
			if (object->data.unknown.data != NULL) {
				free(object->data.unknown.data);
				object->data.unknown.data = NULL;
			}
			break;
	}
}

FLAC_API void FLAC__metadata_object_delete(FLAC__StreamMetadata *object)
{
	FLAC__metadata_object_delete_data(object);
	free(object);
}

static FLAC__bool compare_block_data_streaminfo_(const FLAC__StreamMetadata_StreamInfo *block1, const FLAC__StreamMetadata_StreamInfo *block2)
{
	if (block1->min_blocksize != block2->min_blocksize)
		return false;
	if (block1->max_blocksize != block2->max_blocksize)
		return false;
	if (block1->min_framesize != block2->min_framesize)
		return false;
	if (block1->max_framesize != block2->max_framesize)
		return false;
	if (block1->sample_rate != block2->sample_rate)
		return false;
	if (block1->channels != block2->channels)
		return false;
	if (block1->bits_per_sample != block2->bits_per_sample)
		return false;
	if (block1->total_samples != block2->total_samples)
		return false;
	if (memcmp(block1->md5sum, block2->md5sum, 16) != 0)
		return false;
	return true;
}

static FLAC__bool compare_block_data_application_(const FLAC__StreamMetadata_Application *block1, const FLAC__StreamMetadata_Application *block2, uint32_t block_length)
{
	FLAC__ASSERT(block1 != NULL);
	FLAC__ASSERT(block2 != NULL);
	FLAC__ASSERT(block_length >= sizeof(block1->id));

	if (memcmp(block1->id, block2->id, sizeof(block1->id)) != 0)
		return false;
	if (block1->data != NULL && block2->data != NULL)
		return memcmp(block1->data, block2->data, block_length - sizeof(block1->id)) == 0;
	else
		return block1->data == block2->data;
}

static FLAC__bool compare_block_data_seektable_(const FLAC__StreamMetadata_SeekTable *block1, const FLAC__StreamMetadata_SeekTable *block2)
{
	uint32_t i;

	FLAC__ASSERT(block1 != NULL);
	FLAC__ASSERT(block2 != NULL);

	if (block1->num_points != block2->num_points)
		return false;

	if (block1->points != NULL && block2->points != NULL) {
		for (i = 0; i < block1->num_points; i++) {
			if (block1->points[i].sample_number != block2->points[i].sample_number)
				return false;
			if (block1->points[i].stream_offset != block2->points[i].stream_offset)
				return false;
			if (block1->points[i].frame_samples != block2->points[i].frame_samples)
				return false;
		}
		return true;
	}
	else
		return block1->points == block2->points;
}

static FLAC__bool compare_block_data_vorbiscomment_(const FLAC__StreamMetadata_VorbisComment *block1, const FLAC__StreamMetadata_VorbisComment *block2)
{
	uint32_t i;

	if (block1->vendor_string.length != block2->vendor_string.length)
		return false;

	if (block1->vendor_string.entry != NULL && block2->vendor_string.entry != NULL) {
		if (memcmp(block1->vendor_string.entry, block2->vendor_string.entry, block1->vendor_string.length) != 0)
			return false;
	}
	else if (block1->vendor_string.entry != block2->vendor_string.entry)
		return false;

	if (block1->num_comments != block2->num_comments)
		return false;

	for (i = 0; i < block1->num_comments; i++) {
		if (block1->comments[i].entry != NULL && block2->comments[i].entry != NULL) {
			if (memcmp(block1->comments[i].entry, block2->comments[i].entry, block1->comments[i].length) != 0)
				return false;
		}
		else if (block1->comments[i].entry != block2->comments[i].entry)
			return false;
	}
	return true;
}

static FLAC__bool compare_block_data_cuesheet_(const FLAC__StreamMetadata_CueSheet *block1, const FLAC__StreamMetadata_CueSheet *block2)
{
	uint32_t i, j;

	if (strcmp(block1->media_catalog_number, block2->media_catalog_number) != 0)
		return false;

	if (block1->lead_in != block2->lead_in)
		return false;

	if (block1->is_cd != block2->is_cd)
		return false;

	if (block1->num_tracks != block2->num_tracks)
		return false;

	if (block1->tracks != NULL && block2->tracks != NULL) {
		FLAC__ASSERT(block1->num_tracks > 0);
		for (i = 0; i < block1->num_tracks; i++) {
			if (block1->tracks[i].offset != block2->tracks[i].offset)
				return false;
			if (block1->tracks[i].number != block2->tracks[i].number)
				return false;
			if (memcmp(block1->tracks[i].isrc, block2->tracks[i].isrc, sizeof(block1->tracks[i].isrc)) != 0)
				return false;
			if (block1->tracks[i].type != block2->tracks[i].type)
				return false;
			if (block1->tracks[i].pre_emphasis != block2->tracks[i].pre_emphasis)
				return false;
			if (block1->tracks[i].num_indices != block2->tracks[i].num_indices)
				return false;
			if (block1->tracks[i].indices != NULL && block2->tracks[i].indices != NULL) {
				FLAC__ASSERT(block1->tracks[i].num_indices > 0);
				for (j = 0; j < block1->tracks[i].num_indices; j++) {
					if (block1->tracks[i].indices[j].offset != block2->tracks[i].indices[j].offset)
						return false;
					if (block1->tracks[i].indices[j].number != block2->tracks[i].indices[j].number)
						return false;
				}
			}
			else if (block1->tracks[i].indices != block2->tracks[i].indices)
				return false;
		}
	}
	else if (block1->tracks != block2->tracks)
		return false;
	return true;
}

static FLAC__bool compare_block_data_picture_(const FLAC__StreamMetadata_Picture *block1, const FLAC__StreamMetadata_Picture *block2)
{
	if (block1->type != block2->type)
		return false;
	if (block1->mime_type != block2->mime_type && (block1->mime_type == 0 || block2->mime_type == 0 || strcmp(block1->mime_type, block2->mime_type)))
		return false;
	if (block1->description != block2->description && (block1->description == 0 || block2->description == 0 || strcmp((const char *)block1->description, (const char *)block2->description)))
		return false;
	if (block1->width != block2->width)
		return false;
	if (block1->height != block2->height)
		return false;
	if (block1->depth != block2->depth)
		return false;
	if (block1->colors != block2->colors)
		return false;
	if (block1->data_length != block2->data_length)
		return false;
	if (block1->data != block2->data && (block1->data == NULL || block2->data == NULL || memcmp(block1->data, block2->data, block1->data_length)))
		return false;
	return true;
}

static FLAC__bool compare_block_data_unknown_(const FLAC__StreamMetadata_Unknown *block1, const FLAC__StreamMetadata_Unknown *block2, uint32_t block_length)
{
	FLAC__ASSERT(block1 != NULL);
	FLAC__ASSERT(block2 != NULL);

	if (block1->data != NULL && block2->data != NULL)
		return memcmp(block1->data, block2->data, block_length) == 0;
	else
		return block1->data == block2->data;
}

FLAC_API FLAC__bool FLAC__metadata_object_is_equal(const FLAC__StreamMetadata *block1, const FLAC__StreamMetadata *block2)
{
	FLAC__ASSERT(block1 != NULL);
	FLAC__ASSERT(block2 != NULL);

	if (block1->type != block2->type) {
		return false;
	}
	if (block1->is_last != block2->is_last) {
		return false;
	}
	if (block1->length != block2->length) {
		return false;
	}
	switch(block1->type) {
		case FLAC__METADATA_TYPE_STREAMINFO:
			return compare_block_data_streaminfo_(&block1->data.stream_info, &block2->data.stream_info);
		case FLAC__METADATA_TYPE_PADDING:
			return true; /* we don't compare the padding guts */
		case FLAC__METADATA_TYPE_APPLICATION:
			return compare_block_data_application_(&block1->data.application, &block2->data.application, block1->length);
		case FLAC__METADATA_TYPE_SEEKTABLE:
			return compare_block_data_seektable_(&block1->data.seek_table, &block2->data.seek_table);
		case FLAC__METADATA_TYPE_VORBIS_COMMENT:
			return compare_block_data_vorbiscomment_(&block1->data.vorbis_comment, &block2->data.vorbis_comment);
		case FLAC__METADATA_TYPE_CUESHEET:
			return compare_block_data_cuesheet_(&block1->data.cue_sheet, &block2->data.cue_sheet);
		case FLAC__METADATA_TYPE_PICTURE:
			return compare_block_data_picture_(&block1->data.picture, &block2->data.picture);
		default:
			return compare_block_data_unknown_(&block1->data.unknown, &block2->data.unknown, block1->length);
	}
}

FLAC_API FLAC__bool FLAC__metadata_object_application_set_data(FLAC__StreamMetadata *object, FLAC__byte *data, uint32_t length, FLAC__bool copy)
{
	FLAC__byte *save;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_APPLICATION);
	FLAC__ASSERT((data != NULL && length > 0) || (data == NULL && length == 0 && copy == false));

	save = object->data.application.data;

	/* do the copy first so that if we fail we leave the object untouched */
	if (copy) {
		if (!copy_bytes_(&object->data.application.data, data, length))
			return false;
	}
	else {
		object->data.application.data = data;
	}

	free(save);

	object->length = FLAC__STREAM_METADATA_APPLICATION_ID_LEN / 8 + length;
	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_seektable_resize_points(FLAC__StreamMetadata *object, uint32_t new_num_points)
{
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_SEEKTABLE);

	if (object->data.seek_table.points == 0) {
		FLAC__ASSERT(object->data.seek_table.num_points == 0);
		if (new_num_points == 0)
			return true;
		else if ((object->data.seek_table.points = seekpoint_array_new_(new_num_points)) == 0)
			return false;
	}
	else {
		const size_t old_size = object->data.seek_table.num_points * sizeof(FLAC__StreamMetadata_SeekPoint);
		const size_t new_size = new_num_points * sizeof(FLAC__StreamMetadata_SeekPoint);

		/* overflow check */
		if (new_num_points > UINT32_MAX / sizeof(FLAC__StreamMetadata_SeekPoint))
			return false;

		FLAC__ASSERT(object->data.seek_table.num_points > 0);

		if (new_size == 0) {
			free(object->data.seek_table.points);
			object->data.seek_table.points = 0;
		}
		else if ((object->data.seek_table.points = safe_realloc_(object->data.seek_table.points, new_size)) == NULL)
			return false;

		/* if growing, set new elements to placeholders */
		if (new_size > old_size) {
			uint32_t i;
			for (i = object->data.seek_table.num_points; i < new_num_points; i++) {
				object->data.seek_table.points[i].sample_number = FLAC__STREAM_METADATA_SEEKPOINT_PLACEHOLDER;
				object->data.seek_table.points[i].stream_offset = 0;
				object->data.seek_table.points[i].frame_samples = 0;
			}
		}
	}

	object->data.seek_table.num_points = new_num_points;

	seektable_calculate_length_(object);
	return true;
}

FLAC_API void FLAC__metadata_object_seektable_set_point(FLAC__StreamMetadata *object, uint32_t point_num, FLAC__StreamMetadata_SeekPoint point)
{
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_SEEKTABLE);
	FLAC__ASSERT(point_num < object->data.seek_table.num_points);

	object->data.seek_table.points[point_num] = point;
}

FLAC_API FLAC__bool FLAC__metadata_object_seektable_insert_point(FLAC__StreamMetadata *object, uint32_t point_num, FLAC__StreamMetadata_SeekPoint point)
{
	int i;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_SEEKTABLE);
	FLAC__ASSERT(point_num <= object->data.seek_table.num_points);

	if (!FLAC__metadata_object_seektable_resize_points(object, object->data.seek_table.num_points+1))
		return false;

	/* move all points >= point_num forward one space */
	for (i = (int)object->data.seek_table.num_points-1; i > (int)point_num; i--)
		object->data.seek_table.points[i] = object->data.seek_table.points[i-1];

	FLAC__metadata_object_seektable_set_point(object, point_num, point);
	seektable_calculate_length_(object);
	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_seektable_delete_point(FLAC__StreamMetadata *object, uint32_t point_num)
{
	uint32_t i;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_SEEKTABLE);
	FLAC__ASSERT(point_num < object->data.seek_table.num_points);

	/* move all points > point_num backward one space */
	for (i = point_num; i < object->data.seek_table.num_points-1; i++)
		object->data.seek_table.points[i] = object->data.seek_table.points[i+1];

	return FLAC__metadata_object_seektable_resize_points(object, object->data.seek_table.num_points-1);
}

FLAC_API FLAC__bool FLAC__metadata_object_seektable_is_legal(const FLAC__StreamMetadata *object)
{
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_SEEKTABLE);

	return FLAC__format_seektable_is_legal(&object->data.seek_table);
}

FLAC_API FLAC__bool FLAC__metadata_object_seektable_template_append_placeholders(FLAC__StreamMetadata *object, uint32_t num)
{
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_SEEKTABLE);

	if (num > 0)
		/* WATCHOUT: we rely on the fact that growing the array adds PLACEHOLDERS at the end */
		return FLAC__metadata_object_seektable_resize_points(object, object->data.seek_table.num_points + num);
	else
		return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_seektable_template_append_point(FLAC__StreamMetadata *object, FLAC__uint64 sample_number)
{
	FLAC__StreamMetadata_SeekTable *seek_table;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_SEEKTABLE);

	seek_table = &object->data.seek_table;

	if (!FLAC__metadata_object_seektable_resize_points(object, seek_table->num_points + 1))
		return false;

	seek_table->points[seek_table->num_points - 1].sample_number = sample_number;
	seek_table->points[seek_table->num_points - 1].stream_offset = 0;
	seek_table->points[seek_table->num_points - 1].frame_samples = 0;

	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_seektable_template_append_points(FLAC__StreamMetadata *object, FLAC__uint64 sample_numbers[], uint32_t num)
{
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_SEEKTABLE);
	FLAC__ASSERT(sample_numbers != 0 || num == 0);

	if (num > 0) {
		FLAC__StreamMetadata_SeekTable *seek_table = &object->data.seek_table;
		uint32_t i, j;

		i = seek_table->num_points;

		if (!FLAC__metadata_object_seektable_resize_points(object, seek_table->num_points + num))
			return false;

		for (j = 0; j < num; i++, j++) {
			seek_table->points[i].sample_number = sample_numbers[j];
			seek_table->points[i].stream_offset = 0;
			seek_table->points[i].frame_samples = 0;
		}
	}

	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_seektable_template_append_spaced_points(FLAC__StreamMetadata *object, uint32_t num, FLAC__uint64 total_samples)
{
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_SEEKTABLE);
	FLAC__ASSERT(total_samples > 0);

	if (num > 0 && total_samples > 0) {
		FLAC__StreamMetadata_SeekTable *seek_table = &object->data.seek_table;
		uint32_t i, j;

		i = seek_table->num_points;

		if (!FLAC__metadata_object_seektable_resize_points(object, seek_table->num_points + num))
			return false;

		for (j = 0; j < num; i++, j++) {
			seek_table->points[i].sample_number = total_samples * (FLAC__uint64)j / (FLAC__uint64)num;
			seek_table->points[i].stream_offset = 0;
			seek_table->points[i].frame_samples = 0;
		}
	}

	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_seektable_template_append_spaced_points_by_samples(FLAC__StreamMetadata *object, uint32_t samples, FLAC__uint64 total_samples)
{
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_SEEKTABLE);
	FLAC__ASSERT(samples > 0);
	FLAC__ASSERT(total_samples > 0);

	if (samples > 0 && total_samples > 0) {
		FLAC__StreamMetadata_SeekTable *seek_table = &object->data.seek_table;
		uint32_t i, j;
		FLAC__uint64 num, sample;

		num = 1 + total_samples / samples; /* 1+ for the first sample at 0 */
		/* now account for the fact that we don't place a seekpoint at "total_samples" since samples are number from 0: */
		if (total_samples % samples == 0)
			num--;

		/* Put a strict upper bound on the number of allowed seek points. */
		if (num > 32768) {
			/* Set the bound and recalculate samples accordingly. */
			num = 32768;
			samples = total_samples / num;
		}

		i = seek_table->num_points;

		if (!FLAC__metadata_object_seektable_resize_points(object, seek_table->num_points + (uint32_t)num))
			return false;

		sample = 0;
		for (j = 0; j < num; i++, j++, sample += samples) {
			seek_table->points[i].sample_number = sample;
			seek_table->points[i].stream_offset = 0;
			seek_table->points[i].frame_samples = 0;
		}
	}

	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_seektable_template_sort(FLAC__StreamMetadata *object, FLAC__bool compact)
{
	uint32_t unique;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_SEEKTABLE);

	unique = FLAC__format_seektable_sort(&object->data.seek_table);

	return !compact || FLAC__metadata_object_seektable_resize_points(object, unique);
}

FLAC_API FLAC__bool FLAC__metadata_object_vorbiscomment_set_vendor_string(FLAC__StreamMetadata *object, FLAC__StreamMetadata_VorbisComment_Entry entry, FLAC__bool copy)
{
	if (!FLAC__format_vorbiscomment_entry_value_is_legal(entry.entry, entry.length))
		return false;
	return vorbiscomment_set_entry_(object, &object->data.vorbis_comment.vendor_string, &entry, copy);
}

FLAC_API FLAC__bool FLAC__metadata_object_vorbiscomment_resize_comments(FLAC__StreamMetadata *object, uint32_t new_num_comments)
{
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_VORBIS_COMMENT);

	if (object->data.vorbis_comment.comments == NULL) {
		FLAC__ASSERT(object->data.vorbis_comment.num_comments == 0);
		if (new_num_comments == 0)
			return true;
		else if ((object->data.vorbis_comment.comments = vorbiscomment_entry_array_new_(new_num_comments)) == NULL)
			return false;
	}
	else {
		const size_t old_size = object->data.vorbis_comment.num_comments * sizeof(FLAC__StreamMetadata_VorbisComment_Entry);
		const size_t new_size = new_num_comments * sizeof(FLAC__StreamMetadata_VorbisComment_Entry);

		/* overflow check */
		if (new_num_comments > UINT32_MAX / sizeof(FLAC__StreamMetadata_VorbisComment_Entry))
			return false;

		FLAC__ASSERT(object->data.vorbis_comment.num_comments > 0);

		/* if shrinking, free the truncated entries */
		if (new_num_comments < object->data.vorbis_comment.num_comments) {
			uint32_t i;
			for (i = new_num_comments; i < object->data.vorbis_comment.num_comments; i++)
				if (object->data.vorbis_comment.comments[i].entry != NULL)
					free(object->data.vorbis_comment.comments[i].entry);
		}

		if (new_size == 0) {
			free(object->data.vorbis_comment.comments);
			object->data.vorbis_comment.comments = 0;
		}
		else {
			FLAC__StreamMetadata_VorbisComment_Entry *oldptr = object->data.vorbis_comment.comments;
			if ((object->data.vorbis_comment.comments = realloc(object->data.vorbis_comment.comments, new_size)) == NULL) {
				vorbiscomment_entry_array_delete_(oldptr, object->data.vorbis_comment.num_comments);
				object->data.vorbis_comment.num_comments = 0;
				return false;
			}
		}

		/* if growing, zero all the length/pointers of new elements */
		if (new_size > old_size)
			memset(object->data.vorbis_comment.comments + object->data.vorbis_comment.num_comments, 0, new_size - old_size);
	}

	object->data.vorbis_comment.num_comments = new_num_comments;

	vorbiscomment_calculate_length_(object);
	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_vorbiscomment_set_comment(FLAC__StreamMetadata *object, uint32_t comment_num, FLAC__StreamMetadata_VorbisComment_Entry entry, FLAC__bool copy)
{
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(comment_num < object->data.vorbis_comment.num_comments);

	if (!FLAC__format_vorbiscomment_entry_is_legal(entry.entry, entry.length))
		return false;
	return vorbiscomment_set_entry_(object, &object->data.vorbis_comment.comments[comment_num], &entry, copy);
}

FLAC_API FLAC__bool FLAC__metadata_object_vorbiscomment_insert_comment(FLAC__StreamMetadata *object, uint32_t comment_num, FLAC__StreamMetadata_VorbisComment_Entry entry, FLAC__bool copy)
{
	FLAC__StreamMetadata_VorbisComment *vc;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_VORBIS_COMMENT);
	FLAC__ASSERT(comment_num <= object->data.vorbis_comment.num_comments);

	if (!FLAC__format_vorbiscomment_entry_is_legal(entry.entry, entry.length))
		return false;

	vc = &object->data.vorbis_comment;

	if (!FLAC__metadata_object_vorbiscomment_resize_comments(object, vc->num_comments+1))
		return false;

	/* move all comments >= comment_num forward one space */
	memmove(&vc->comments[comment_num+1], &vc->comments[comment_num], sizeof(FLAC__StreamMetadata_VorbisComment_Entry)*(vc->num_comments-1-comment_num));
	vc->comments[comment_num].length = 0;
	vc->comments[comment_num].entry = 0;

	return FLAC__metadata_object_vorbiscomment_set_comment(object, comment_num, entry, copy);
}

FLAC_API FLAC__bool FLAC__metadata_object_vorbiscomment_append_comment(FLAC__StreamMetadata *object, FLAC__StreamMetadata_VorbisComment_Entry entry, FLAC__bool copy)
{
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_VORBIS_COMMENT);
	return FLAC__metadata_object_vorbiscomment_insert_comment(object, object->data.vorbis_comment.num_comments, entry, copy);
}

FLAC_API FLAC__bool FLAC__metadata_object_vorbiscomment_replace_comment(FLAC__StreamMetadata *object, FLAC__StreamMetadata_VorbisComment_Entry entry, FLAC__bool all, FLAC__bool copy)
{
	FLAC__ASSERT(entry.entry != NULL && entry.length > 0);

	if (!FLAC__format_vorbiscomment_entry_is_legal(entry.entry, entry.length))
		return false;

	{
		int i;
		size_t field_name_length;
		const FLAC__byte *eq = (FLAC__byte*)memchr(entry.entry, '=', entry.length);

		if (eq == NULL)
			return false; /* double protection */

		field_name_length = eq-entry.entry;

		i = vorbiscomment_find_entry_from_(object, 0, (const char *)entry.entry, field_name_length);
		if (i >= 0) {
			uint32_t indx = (uint32_t)i;
			if (!FLAC__metadata_object_vorbiscomment_set_comment(object, indx, entry, copy))
				return false;
			entry = object->data.vorbis_comment.comments[indx];
			indx++; /* skip over replaced comment */
			if (all && indx < object->data.vorbis_comment.num_comments) {
				i = vorbiscomment_find_entry_from_(object, indx, (const char *)entry.entry, field_name_length);
				while (i >= 0) {
					indx = (uint32_t)i;
					if (!FLAC__metadata_object_vorbiscomment_delete_comment(object, indx))
						return false;
					if (indx < object->data.vorbis_comment.num_comments)
						i = vorbiscomment_find_entry_from_(object, indx, (const char *)entry.entry, field_name_length);
					else
						i = -1;
				}
			}
			return true;
		}
		else
			return FLAC__metadata_object_vorbiscomment_append_comment(object, entry, copy);
	}
}

FLAC_API FLAC__bool FLAC__metadata_object_vorbiscomment_delete_comment(FLAC__StreamMetadata *object, uint32_t comment_num)
{
	FLAC__StreamMetadata_VorbisComment *vc;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_VORBIS_COMMENT);
	FLAC__ASSERT(comment_num < object->data.vorbis_comment.num_comments);

	vc = &object->data.vorbis_comment;

	/* free the comment at comment_num */
	free(vc->comments[comment_num].entry);

	/* move all comments > comment_num backward one space */
	memmove(&vc->comments[comment_num], &vc->comments[comment_num+1], sizeof(FLAC__StreamMetadata_VorbisComment_Entry)*(vc->num_comments-comment_num-1));
	vc->comments[vc->num_comments-1].length = 0;
	vc->comments[vc->num_comments-1].entry = 0;

	return FLAC__metadata_object_vorbiscomment_resize_comments(object, vc->num_comments-1);
}

FLAC_API FLAC__bool FLAC__metadata_object_vorbiscomment_entry_from_name_value_pair(FLAC__StreamMetadata_VorbisComment_Entry *entry, const char *field_name, const char *field_value)
{
	FLAC__ASSERT(entry != NULL);
	FLAC__ASSERT(field_name != NULL);
	FLAC__ASSERT(field_value != NULL);

	if (!FLAC__format_vorbiscomment_entry_name_is_legal(field_name))
		return false;
	if (!FLAC__format_vorbiscomment_entry_value_is_legal((const FLAC__byte *)field_value, (uint32_t)(-1)))
		return false;

	{
		const size_t nn = strlen(field_name);
		const size_t nv = strlen(field_value);
		entry->length = nn + 1 /*=*/ + nv;
		if ((entry->entry = safe_malloc_add_4op_(nn, /*+*/1, /*+*/nv, /*+*/1)) == NULL)
			return false;
		memcpy(entry->entry, field_name, nn);
		entry->entry[nn] = '=';
		memcpy(entry->entry+nn+1, field_value, nv);
		entry->entry[entry->length] = '\0';
	}

	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_vorbiscomment_entry_to_name_value_pair(const FLAC__StreamMetadata_VorbisComment_Entry entry, char **field_name, char **field_value)
{
	FLAC__ASSERT(entry.entry != NULL && entry.length > 0);
	FLAC__ASSERT(field_name != NULL);
	FLAC__ASSERT(field_value != NULL);

	if (!FLAC__format_vorbiscomment_entry_is_legal(entry.entry, entry.length))
		return false;

	{
		const FLAC__byte *eq = (FLAC__byte*)memchr(entry.entry, '=', entry.length);
		const size_t nn = eq-entry.entry;
		const size_t nv = entry.length-nn-1; /* -1 for the '=' */

		if (eq == NULL)
			return false; /* double protection */
		if ((*field_name = safe_malloc_add_2op_(nn, /*+*/1)) == NULL)
			return false;
		if ((*field_value = safe_malloc_add_2op_(nv, /*+*/1)) == NULL) {
			free(*field_name);
			return false;
		}
		memcpy(*field_name, entry.entry, nn);
		memcpy(*field_value, entry.entry+nn+1, nv);
		(*field_name)[nn] = '\0';
		(*field_value)[nv] = '\0';
	}

	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_vorbiscomment_entry_matches(const FLAC__StreamMetadata_VorbisComment_Entry entry, const char *field_name, uint32_t field_name_length)
{
	FLAC__ASSERT(entry.entry != NULL && entry.length > 0);
	{
		const FLAC__byte *eq = (FLAC__byte*)memchr(entry.entry, '=', entry.length);
		return (eq != NULL && (uint32_t)(eq-entry.entry) == field_name_length && FLAC__STRNCASECMP(field_name, (const char *)entry.entry, field_name_length) == 0);
	}
}

FLAC_API int FLAC__metadata_object_vorbiscomment_find_entry_from(const FLAC__StreamMetadata *object, uint32_t offset, const char *field_name)
{
	FLAC__ASSERT(field_name != NULL);

	return vorbiscomment_find_entry_from_(object, offset, field_name, strlen(field_name));
}

FLAC_API int FLAC__metadata_object_vorbiscomment_remove_entry_matching(FLAC__StreamMetadata *object, const char *field_name)
{
	const uint32_t field_name_length = strlen(field_name);
	uint32_t i;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_VORBIS_COMMENT);

	for (i = 0; i < object->data.vorbis_comment.num_comments; i++) {
		if (FLAC__metadata_object_vorbiscomment_entry_matches(object->data.vorbis_comment.comments[i], field_name, field_name_length)) {
			if (!FLAC__metadata_object_vorbiscomment_delete_comment(object, i))
				return -1;
			else
				return 1;
		}
	}

	return 0;
}

FLAC_API int FLAC__metadata_object_vorbiscomment_remove_entries_matching(FLAC__StreamMetadata *object, const char *field_name)
{
	FLAC__bool ok = true;
	uint32_t matching = 0;
	const uint32_t field_name_length = strlen(field_name);
	int i;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_VORBIS_COMMENT);

	/* must delete from end to start otherwise it will interfere with our iteration */
	for (i = (int)object->data.vorbis_comment.num_comments - 1; ok && i >= 0; i--) {
		if (FLAC__metadata_object_vorbiscomment_entry_matches(object->data.vorbis_comment.comments[i], field_name, field_name_length)) {
			matching++;
			ok &= FLAC__metadata_object_vorbiscomment_delete_comment(object, (uint32_t)i);
		}
	}

	return ok? (int)matching : -1;
}

FLAC_API FLAC__StreamMetadata_CueSheet_Track *FLAC__metadata_object_cuesheet_track_new(void)
{
	return calloc(1, sizeof(FLAC__StreamMetadata_CueSheet_Track));
}

FLAC_API FLAC__StreamMetadata_CueSheet_Track *FLAC__metadata_object_cuesheet_track_clone(const FLAC__StreamMetadata_CueSheet_Track *object)
{
	FLAC__StreamMetadata_CueSheet_Track *to;

	FLAC__ASSERT(object != NULL);

	if ((to = FLAC__metadata_object_cuesheet_track_new()) != NULL) {
		if (!copy_track_(to, object)) {
			FLAC__metadata_object_cuesheet_track_delete(to);
			return 0;
		}
	}

	return to;
}

void FLAC__metadata_object_cuesheet_track_delete_data(FLAC__StreamMetadata_CueSheet_Track *object)
{
	FLAC__ASSERT(object != NULL);

	if (object->indices != NULL) {
		FLAC__ASSERT(object->num_indices > 0);
		free(object->indices);
	}
}

FLAC_API void FLAC__metadata_object_cuesheet_track_delete(FLAC__StreamMetadata_CueSheet_Track *object)
{
	FLAC__metadata_object_cuesheet_track_delete_data(object);
	free(object);
}

FLAC_API FLAC__bool FLAC__metadata_object_cuesheet_track_resize_indices(FLAC__StreamMetadata *object, uint32_t track_num, uint32_t new_num_indices)
{
	FLAC__StreamMetadata_CueSheet_Track *track;
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_CUESHEET);
	FLAC__ASSERT(track_num < object->data.cue_sheet.num_tracks);

	track = &object->data.cue_sheet.tracks[track_num];

	if (track->indices == NULL) {
		FLAC__ASSERT(track->num_indices == 0);
		if (new_num_indices == 0)
			return true;
		else if ((track->indices = cuesheet_track_index_array_new_(new_num_indices)) == NULL)
			return false;
	}
	else {
		const size_t old_size = track->num_indices * sizeof(FLAC__StreamMetadata_CueSheet_Index);
		const size_t new_size = new_num_indices * sizeof(FLAC__StreamMetadata_CueSheet_Index);

		/* overflow check */
		if (new_num_indices > UINT32_MAX / sizeof(FLAC__StreamMetadata_CueSheet_Index))
			return false;

		FLAC__ASSERT(track->num_indices > 0);

		if (new_size == 0) {
			free(track->indices);
			track->indices = 0;
		}
		else if ((track->indices = safe_realloc_(track->indices, new_size)) == NULL)
			return false;

		/* if growing, zero all the lengths/pointers of new elements */
		if (new_size > old_size)
			memset(track->indices + track->num_indices, 0, new_size - old_size);
	}

	track->num_indices = new_num_indices;

	cuesheet_calculate_length_(object);
	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_cuesheet_track_insert_index(FLAC__StreamMetadata *object, uint32_t track_num, uint32_t index_num, FLAC__StreamMetadata_CueSheet_Index indx)
{
	FLAC__StreamMetadata_CueSheet_Track *track;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_CUESHEET);
	FLAC__ASSERT(track_num < object->data.cue_sheet.num_tracks);
	FLAC__ASSERT(index_num <= object->data.cue_sheet.tracks[track_num].num_indices);

	track = &object->data.cue_sheet.tracks[track_num];

	if (!FLAC__metadata_object_cuesheet_track_resize_indices(object, track_num, track->num_indices+1))
		return false;

	/* move all indices >= index_num forward one space */
	memmove(&track->indices[index_num+1], &track->indices[index_num], sizeof(FLAC__StreamMetadata_CueSheet_Index)*(track->num_indices-1-index_num));

	track->indices[index_num] = indx;
	cuesheet_calculate_length_(object);
	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_cuesheet_track_insert_blank_index(FLAC__StreamMetadata *object, uint32_t track_num, uint32_t index_num)
{
	FLAC__StreamMetadata_CueSheet_Index indx;
	memset(&indx, 0, sizeof(indx));
	return FLAC__metadata_object_cuesheet_track_insert_index(object, track_num, index_num, indx);
}

FLAC_API FLAC__bool FLAC__metadata_object_cuesheet_track_delete_index(FLAC__StreamMetadata *object, uint32_t track_num, uint32_t index_num)
{
	FLAC__StreamMetadata_CueSheet_Track *track;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_CUESHEET);
	FLAC__ASSERT(track_num < object->data.cue_sheet.num_tracks);
	FLAC__ASSERT(index_num < object->data.cue_sheet.tracks[track_num].num_indices);

	track = &object->data.cue_sheet.tracks[track_num];

	/* move all indices > index_num backward one space */
	memmove(&track->indices[index_num], &track->indices[index_num+1], sizeof(FLAC__StreamMetadata_CueSheet_Index)*(track->num_indices-index_num-1));

	FLAC__metadata_object_cuesheet_track_resize_indices(object, track_num, track->num_indices-1);
	cuesheet_calculate_length_(object);
	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_cuesheet_resize_tracks(FLAC__StreamMetadata *object, uint32_t new_num_tracks)
{
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_CUESHEET);

	if (object->data.cue_sheet.tracks == NULL) {
		FLAC__ASSERT(object->data.cue_sheet.num_tracks == 0);
		if (new_num_tracks == 0)
			return true;
		else if ((object->data.cue_sheet.tracks = cuesheet_track_array_new_(new_num_tracks)) == NULL)
			return false;
	}
	else {
		const size_t old_size = object->data.cue_sheet.num_tracks * sizeof(FLAC__StreamMetadata_CueSheet_Track);
		const size_t new_size = new_num_tracks * sizeof(FLAC__StreamMetadata_CueSheet_Track);

		/* overflow check */
		if (new_num_tracks > UINT32_MAX / sizeof(FLAC__StreamMetadata_CueSheet_Track))
			return false;

		FLAC__ASSERT(object->data.cue_sheet.num_tracks > 0);

		/* if shrinking, free the truncated entries */
		if (new_num_tracks < object->data.cue_sheet.num_tracks) {
			uint32_t i;
			for (i = new_num_tracks; i < object->data.cue_sheet.num_tracks; i++)
				free(object->data.cue_sheet.tracks[i].indices);
		}

		if (new_size == 0) {
			free(object->data.cue_sheet.tracks);
			object->data.cue_sheet.tracks = 0;
		}
		else if ((object->data.cue_sheet.tracks = safe_realloc_(object->data.cue_sheet.tracks, new_size)) == NULL)
			return false;

		/* if growing, zero all the lengths/pointers of new elements */
		if (new_size > old_size)
			memset(object->data.cue_sheet.tracks + object->data.cue_sheet.num_tracks, 0, new_size - old_size);
	}

	object->data.cue_sheet.num_tracks = new_num_tracks;

	cuesheet_calculate_length_(object);
	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_cuesheet_set_track(FLAC__StreamMetadata *object, uint32_t track_num, FLAC__StreamMetadata_CueSheet_Track *track, FLAC__bool copy)
{
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(track_num < object->data.cue_sheet.num_tracks);

	return cuesheet_set_track_(object, object->data.cue_sheet.tracks + track_num, track, copy);
}

FLAC_API FLAC__bool FLAC__metadata_object_cuesheet_insert_track(FLAC__StreamMetadata *object, uint32_t track_num, FLAC__StreamMetadata_CueSheet_Track *track, FLAC__bool copy)
{
	FLAC__StreamMetadata_CueSheet *cs;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_CUESHEET);
	FLAC__ASSERT(track_num <= object->data.cue_sheet.num_tracks);

	cs = &object->data.cue_sheet;

	if (!FLAC__metadata_object_cuesheet_resize_tracks(object, cs->num_tracks+1))
		return false;

	/* move all tracks >= track_num forward one space */
	memmove(&cs->tracks[track_num+1], &cs->tracks[track_num], sizeof(FLAC__StreamMetadata_CueSheet_Track)*(cs->num_tracks-1-track_num));
	cs->tracks[track_num].num_indices = 0;
	cs->tracks[track_num].indices = 0;

	return FLAC__metadata_object_cuesheet_set_track(object, track_num, track, copy);
}

FLAC_API FLAC__bool FLAC__metadata_object_cuesheet_insert_blank_track(FLAC__StreamMetadata *object, uint32_t track_num)
{
	FLAC__StreamMetadata_CueSheet_Track track;
	memset(&track, 0, sizeof(track));
	return FLAC__metadata_object_cuesheet_insert_track(object, track_num, &track, /*copy=*/false);
}

FLAC_API FLAC__bool FLAC__metadata_object_cuesheet_delete_track(FLAC__StreamMetadata *object, uint32_t track_num)
{
	FLAC__StreamMetadata_CueSheet *cs;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_CUESHEET);
	FLAC__ASSERT(track_num < object->data.cue_sheet.num_tracks);

	cs = &object->data.cue_sheet;

	/* free the track at track_num */
	free(cs->tracks[track_num].indices);

	/* move all tracks > track_num backward one space */
	memmove(&cs->tracks[track_num], &cs->tracks[track_num+1], sizeof(FLAC__StreamMetadata_CueSheet_Track)*(cs->num_tracks-track_num-1));
	cs->tracks[cs->num_tracks-1].num_indices = 0;
	cs->tracks[cs->num_tracks-1].indices = 0;

	return FLAC__metadata_object_cuesheet_resize_tracks(object, cs->num_tracks-1);
}

FLAC_API FLAC__bool FLAC__metadata_object_cuesheet_is_legal(const FLAC__StreamMetadata *object, FLAC__bool check_cd_da_subset, const char **violation)
{
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_CUESHEET);

	return FLAC__format_cuesheet_is_legal(&object->data.cue_sheet, check_cd_da_subset, violation);
}

static FLAC__uint64 get_index_01_offset_(const FLAC__StreamMetadata_CueSheet *cs, uint32_t track)
{
	if (track >= (cs->num_tracks-1) || cs->tracks[track].num_indices < 1)
		return 0;
	else if (cs->tracks[track].indices[0].number == 1)
		return cs->tracks[track].indices[0].offset + cs->tracks[track].offset + cs->lead_in;
	else if (cs->tracks[track].num_indices < 2)
		return 0;
	else if (cs->tracks[track].indices[1].number == 1)
		return cs->tracks[track].indices[1].offset + cs->tracks[track].offset + cs->lead_in;
	else
		return 0;
}

static FLAC__uint32 cddb_add_digits_(FLAC__uint32 x)
{
	FLAC__uint32 n = 0;
	while (x) {
		n += (x%10);
		x /= 10;
	}
	return n;
}

/*@@@@add to tests*/
FLAC_API FLAC__uint32 FLAC__metadata_object_cuesheet_calculate_cddb_id(const FLAC__StreamMetadata *object)
{
	const FLAC__StreamMetadata_CueSheet *cs;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_CUESHEET);

	cs = &object->data.cue_sheet;

	if (cs->num_tracks < 2) /* need at least one real track and the lead-out track */
		return 0;

	{
		FLAC__uint32 i, length, sum = 0;
		for (i = 0; i < (cs->num_tracks-1); i++) /* -1 to avoid counting the lead-out */
			sum += cddb_add_digits_((FLAC__uint32)(get_index_01_offset_(cs, i) / 44100));
		length = (FLAC__uint32)((cs->tracks[cs->num_tracks-1].offset+cs->lead_in) / 44100) - (FLAC__uint32)(get_index_01_offset_(cs, 0) / 44100);

		return (sum % 0xFF) << 24 | length << 8 | (FLAC__uint32)(cs->num_tracks-1);
	}
}

FLAC_API FLAC__bool FLAC__metadata_object_picture_set_mime_type(FLAC__StreamMetadata *object, char *mime_type, FLAC__bool copy)
{
	char *old;
	size_t old_length, new_length;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_PICTURE);
	FLAC__ASSERT(mime_type != NULL);

	old = object->data.picture.mime_type;
	old_length = old? strlen(old) : 0;
	new_length = strlen(mime_type);

	/* do the copy first so that if we fail we leave the object untouched */
	if (copy) {
		if (new_length >= SIZE_MAX) /* overflow check */
			return false;
		if (!copy_bytes_((FLAC__byte**)(&object->data.picture.mime_type), (FLAC__byte*)mime_type, new_length+1))
			return false;
	}
	else {
		object->data.picture.mime_type = mime_type;
	}

	free(old);

	object->length -= old_length;
	object->length += new_length;
	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_picture_set_description(FLAC__StreamMetadata *object, FLAC__byte *description, FLAC__bool copy)
{
	FLAC__byte *old;
	size_t old_length, new_length;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_PICTURE);
	FLAC__ASSERT(description != NULL);

	old = object->data.picture.description;
	old_length = old? strlen((const char *)old) : 0;
	new_length = strlen((const char *)description);

	/* do the copy first so that if we fail we leave the object untouched */
	if (copy) {
		if (new_length >= SIZE_MAX) /* overflow check */
			return false;
		if (!copy_bytes_(&object->data.picture.description, description, new_length+1))
			return false;
	}
	else {
		object->data.picture.description = description;
	}

	free(old);

	object->length -= old_length;
	object->length += new_length;
	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_picture_set_data(FLAC__StreamMetadata *object, FLAC__byte *data, FLAC__uint32 length, FLAC__bool copy)
{
	FLAC__byte *old;

	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_PICTURE);
	FLAC__ASSERT((data != NULL && length > 0) || (data == NULL && length == 0 && copy == false));

	old = object->data.picture.data;

	/* do the copy first so that if we fail we leave the object untouched */
	if (copy) {
		if (!copy_bytes_(&object->data.picture.data, data, length))
			return false;
	}
	else {
		object->data.picture.data = data;
	}

	free(old);

	object->length -= object->data.picture.data_length;
	object->data.picture.data_length = length;
	object->length += length;
	return true;
}

FLAC_API FLAC__bool FLAC__metadata_object_picture_is_legal(const FLAC__StreamMetadata *object, const char **violation)
{
	FLAC__ASSERT(object != NULL);
	FLAC__ASSERT(object->type == FLAC__METADATA_TYPE_PICTURE);

	return FLAC__format_picture_is_legal(&object->data.picture, violation);
}

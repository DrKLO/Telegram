/* grabbag - Convenience lib for various routines common to several tools
 * Copyright (C) 2002-2009  Josh Coalson
 * Copyright (C) 2011-2016  Xiph.Org Foundation
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

/* This .h cannot be included by itself; #include "share/grabbag.h" instead. */

#ifndef GRABBAG__CUESHEET_H
#define GRABBAG__CUESHEET_H

#include <stdio.h>
#include "FLAC/metadata.h"

#ifdef __cplusplus
extern "C" {
#endif

unsigned grabbag__cuesheet_msf_to_frame(unsigned minutes, unsigned seconds, unsigned frames);
void grabbag__cuesheet_frame_to_msf(unsigned frame, unsigned *minutes, unsigned *seconds, unsigned *frames);

FLAC__StreamMetadata *grabbag__cuesheet_parse(FILE *file, const char **error_message, unsigned *last_line_read, unsigned sample_rate, FLAC__bool is_cdda, FLAC__uint64 lead_out_offset);

void grabbag__cuesheet_emit(FILE *file, const FLAC__StreamMetadata *cuesheet, const char *file_reference);

#ifdef __cplusplus
}
#endif

#endif

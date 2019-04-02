/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2013-2016  Xiph.Org Foundation
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

#ifdef _WIN32

#ifndef flac__windows_unicode_filenames_h
#define flac__windows_unicode_filenames_h

#include <stdio.h>
#include <sys/stat.h>
#include <sys/utime.h>
#include "FLAC/ordinals.h"

#ifdef __cplusplus
extern "C" {
#endif

void flac_internal_set_utf8_filenames(FLAC__bool flag);
FLAC__bool flac_internal_get_utf8_filenames(void);
#define flac_set_utf8_filenames flac_internal_set_utf8_filenames
#define flac_get_utf8_filenames flac_internal_get_utf8_filenames

FILE* flac_internal_fopen_utf8(const char *filename, const char *mode);
int flac_internal_stat64_utf8(const char *path, struct __stat64 *buffer);
int flac_internal_chmod_utf8(const char *filename, int pmode);
int flac_internal_utime_utf8(const char *filename, struct utimbuf *times);
int flac_internal_unlink_utf8(const char *filename);
int flac_internal_rename_utf8(const char *oldname, const char *newname);

#include <windows.h>
HANDLE WINAPI flac_internal_CreateFile_utf8(const char *lpFileName, DWORD dwDesiredAccess, DWORD dwShareMode, LPSECURITY_ATTRIBUTES lpSecurityAttributes, DWORD dwCreationDisposition, DWORD dwFlagsAndAttributes, HANDLE hTemplateFile);
#define CreateFile_utf8 flac_internal_CreateFile_utf8

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif
#endif

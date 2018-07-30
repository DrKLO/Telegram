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

#ifdef HAVE_CONFIG_H
#  include <config.h>
#endif

#include <io.h>
#include "share/windows_unicode_filenames.h"

/* convert UTF-8 back to WCHAR. Caller is responsible for freeing memory */
static wchar_t *wchar_from_utf8(const char *str)
{
	wchar_t *widestr;
	int len;

	if (!str)
		return NULL;
	if ((len = MultiByteToWideChar(CP_UTF8, 0, str, -1, NULL, 0)) == 0)
		return NULL;
	if ((widestr = (wchar_t *)malloc(len*sizeof(wchar_t))) == NULL)
		return NULL;
	if (MultiByteToWideChar(CP_UTF8, 0, str, -1, widestr, len) == 0) {
		free(widestr);
		widestr = NULL;
	}

	return widestr;
}


static FLAC__bool utf8_filenames = false;


void flac_internal_set_utf8_filenames(FLAC__bool flag)
{
	utf8_filenames = flag ? true : false;
}

FLAC__bool flac_internal_get_utf8_filenames(void)
{
	return utf8_filenames;
}

/* file functions */

FILE* flac_internal_fopen_utf8(const char *filename, const char *mode)
{
	if (!utf8_filenames) {
		return fopen(filename, mode);
	} else {
		wchar_t *wname = NULL;
		wchar_t *wmode = NULL;
		FILE *f = NULL;

		do {
			if (!(wname = wchar_from_utf8(filename))) break;
			if (!(wmode = wchar_from_utf8(mode))) break;
			f = _wfopen(wname, wmode);
		} while(0);

		free(wname);
		free(wmode);

		return f;
	}
}

int flac_internal_stat64_utf8(const char *path, struct __stat64 *buffer)
{
	if (!utf8_filenames) {
		return _stat64(path, buffer);
	} else {
		wchar_t *wpath;
		int ret;

		if (!(wpath = wchar_from_utf8(path))) return -1;
		ret = _wstat64(wpath, buffer);
		free(wpath);

		return ret;
	}
}

int flac_internal_chmod_utf8(const char *filename, int pmode)
{
	if (!utf8_filenames) {
		return _chmod(filename, pmode);
	} else {
		wchar_t *wname;
		int ret;

		if (!(wname = wchar_from_utf8(filename))) return -1;
		ret = _wchmod(wname, pmode);
		free(wname);

		return ret;
	}
}

int flac_internal_utime_utf8(const char *filename, struct utimbuf *times)
{
	if (!utf8_filenames) {
		return utime(filename, times);
	} else {
		wchar_t *wname;
		struct __utimbuf64 ut;
		int ret;

		if (!(wname = wchar_from_utf8(filename))) return -1;
		ut.actime = times->actime;
		ut.modtime = times->modtime;
		ret = _wutime64(wname, &ut);
		free(wname);

		return ret;
	}
}

int flac_internal_unlink_utf8(const char *filename)
{
	if (!utf8_filenames) {
		return _unlink(filename);
	} else {
		wchar_t *wname;
		int ret;

		if (!(wname = wchar_from_utf8(filename))) return -1;
		ret = _wunlink(wname);
		free(wname);

		return ret;
	}
}

int flac_internal_rename_utf8(const char *oldname, const char *newname)
{
	if (!utf8_filenames) {
		return rename(oldname, newname);
	} else {
		wchar_t *wold = NULL;
		wchar_t *wnew = NULL;
		int ret = -1;

		do {
			if (!(wold = wchar_from_utf8(oldname))) break;
			if (!(wnew = wchar_from_utf8(newname))) break;
			ret = _wrename(wold, wnew);
		} while(0);

		free(wold);
		free(wnew);

		return ret;
	}
}

HANDLE WINAPI flac_internal_CreateFile_utf8(const char *lpFileName, DWORD dwDesiredAccess, DWORD dwShareMode, LPSECURITY_ATTRIBUTES lpSecurityAttributes, DWORD dwCreationDisposition, DWORD dwFlagsAndAttributes, HANDLE hTemplateFile)
{
#if _MSC_VER > 1900 && WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_APP)
	wchar_t *wname;
	HANDLE handle = INVALID_HANDLE_VALUE;

	if ((wname = wchar_from_utf8(lpFileName)) != NULL) {

		handle = CreateFile2(wname, dwDesiredAccess, dwShareMode, CREATE_ALWAYS, NULL);
		free(wname);
	}
#else
	if (!utf8_filenames) {
		return CreateFileA(lpFileName, dwDesiredAccess, dwShareMode, lpSecurityAttributes, dwCreationDisposition, dwFlagsAndAttributes, hTemplateFile);
	} else {
		wchar_t *wname;
		HANDLE handle = INVALID_HANDLE_VALUE;

		if ((wname = wchar_from_utf8(lpFileName)) != NULL) {
			handle = CreateFileW(wname, dwDesiredAccess, dwShareMode, lpSecurityAttributes, dwCreationDisposition, dwFlagsAndAttributes, hTemplateFile);
			free(wname);
		}

		return handle;
	}
#endif
}

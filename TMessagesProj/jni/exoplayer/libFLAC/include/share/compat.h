/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2012-2016  Xiph.org Foundation
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

/* This is the prefered location of all CPP hackery to make $random_compiler
 * work like something approaching a C99 (or maybe more accurately GNU99)
 * compiler.
 *
 * It is assumed that this header will be included after "config.h".
 */

#ifndef FLAC__SHARE__COMPAT_H
#define FLAC__SHARE__COMPAT_H

#if defined _WIN32 && !defined __CYGWIN__
/* where MSVC puts unlink() */
# include <io.h>
#else
# include <unistd.h>
#endif

#if defined _MSC_VER || defined __BORLANDC__ || defined __MINGW32__
#include <sys/types.h> /* for off_t */
#define FLAC__off_t __int64 /* use this instead of off_t to fix the 2 GB limit */
#if !defined __MINGW32__
#define fseeko _fseeki64
#define ftello _ftelli64
#else /* MinGW */
#if !defined(HAVE_FSEEKO)
#define fseeko fseeko64
#define ftello ftello64
#endif
#endif
#else
#define FLAC__off_t off_t
#endif

#if HAVE_INTTYPES_H
#define __STDC_FORMAT_MACROS
#include <inttypes.h>
#endif

#if defined(_MSC_VER)
#define strtoll _strtoi64
#define strtoull _strtoui64
#endif

#if defined(_MSC_VER) && !defined(__cplusplus)
#define inline __inline
#endif

#if defined __INTEL_COMPILER || (defined _MSC_VER && defined _WIN64)
/* MSVS generates VERY slow 32-bit code with __restrict */
#define flac_restrict __restrict
#elif defined __GNUC__
#define flac_restrict __restrict__
#else
#define flac_restrict
#endif

#define FLAC__U64L(x) x##ULL

#if defined _MSC_VER || defined __MINGW32__
#define FLAC__STRCASECMP _stricmp
#define FLAC__STRNCASECMP _strnicmp
#elif defined __BORLANDC__
#define FLAC__STRCASECMP stricmp
#define FLAC__STRNCASECMP strnicmp
#else
#define FLAC__STRCASECMP strcasecmp
#define FLAC__STRNCASECMP strncasecmp
#endif

#if defined _MSC_VER || defined __MINGW32__ || defined __EMX__
#include <io.h> /* for _setmode(), chmod() */
#include <fcntl.h> /* for _O_BINARY */
#else
#include <unistd.h> /* for chown(), unlink() */
#endif

#if defined _MSC_VER || defined __BORLANDC__ || defined __MINGW32__
#if defined __BORLANDC__
#include <utime.h> /* for utime() */
#else
#include <sys/utime.h> /* for utime() */
#endif
#else
#include <sys/types.h> /* some flavors of BSD (like OS X) require this to get time_t */
#include <utime.h> /* for utime() */
#endif

#if defined _MSC_VER
#  if _MSC_VER >= 1800
#    include <inttypes.h>
#  elif _MSC_VER >= 1600
/* Visual Studio 2010 has decent C99 support */
#    include <stdint.h>
#    define PRIu64 "llu"
#    define PRId64 "lld"
#    define PRIx64 "llx"
#  else
#    include <limits.h>
#    ifndef UINT32_MAX
#      define UINT32_MAX _UI32_MAX
#    endif
#    define PRIu64 "I64u"
#    define PRId64 "I64d"
#    define PRIx64 "I64x"
#  endif
#endif /* defined _MSC_VER */

#ifdef _WIN32
/* All char* strings are in UTF-8 format. Added to support Unicode files on Windows */

#include "share/win_utf8_io.h"
#define flac_printf printf_utf8
#define flac_fprintf fprintf_utf8
#define flac_vfprintf vfprintf_utf8

#include "share/windows_unicode_filenames.h"
#define flac_fopen flac_internal_fopen_utf8
#define flac_chmod flac_internal_chmod_utf8
#define flac_utime flac_internal_utime_utf8
#define flac_unlink flac_internal_unlink_utf8
#define flac_rename flac_internal_rename_utf8
#define flac_stat flac_internal_stat64_utf8

#else

#define flac_printf printf
#define flac_fprintf fprintf
#define flac_vfprintf vfprintf

#define flac_fopen fopen
#define flac_chmod chmod
#define flac_utime utime
#define flac_unlink unlink
#define flac_rename rename
#define flac_stat stat

#endif

#ifdef _WIN32
#define flac_stat_s __stat64 /* stat struct */
#define flac_fstat _fstat64
#else
#define flac_stat_s stat /* stat struct */
#define flac_fstat fstat
#endif

#ifdef ANDROID
#include <limits.h>
#endif

#ifndef M_LN2
#define M_LN2 0.69314718055994530942
#endif
#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

/* FLAC needs to compile and work correctly on systems with a normal ISO C99
 * snprintf as well as Microsoft Visual Studio which has an non-standards
 * conformant snprint_s function.
 *
 * This function wraps the MS version to behave more like the ISO version.
 */
#include <stdarg.h>
#ifdef __cplusplus
extern "C" {
#endif
int flac_snprintf(char *str, size_t size, const char *fmt, ...);
int flac_vsnprintf(char *str, size_t size, const char *fmt, va_list va);
#ifdef __cplusplus
};
#endif

#endif /* FLAC__SHARE__COMPAT_H */

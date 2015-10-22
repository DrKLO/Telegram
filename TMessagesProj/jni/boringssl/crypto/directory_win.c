/* $LP: LPlib/source/LPdir_win.c,v 1.10 2004/08/26 13:36:05 _cvs_levitte Exp $ */
/*
 * Copyright (c) 2004, Richard Levitte <richard@levitte.org>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include "directory.h"


#if defined(OPENSSL_WINDOWS)

#pragma warning(push, 3)
#include <windows.h>
#pragma warning(pop)
#include <errno.h>
#include <string.h>
#include <tchar.h>

#ifndef NAME_MAX
#define NAME_MAX 255
#endif

#include <openssl/mem.h>


struct OPENSSL_dir_context_st {
  WIN32_FIND_DATA ctx;
  HANDLE handle;
  char entry_name[NAME_MAX + 1];
};

const char *OPENSSL_DIR_read(OPENSSL_DIR_CTX **ctx, const char *directory) {
  if (ctx == NULL || directory == NULL) {
    errno = EINVAL;
    return 0;
  }

  errno = 0;
  if (*ctx == NULL) {
    *ctx = malloc(sizeof(OPENSSL_DIR_CTX));
    if (*ctx == NULL) {
      errno = ENOMEM;
      return 0;
    }
    memset(*ctx, 0, sizeof(OPENSSL_DIR_CTX));

    if (sizeof(TCHAR) != sizeof(char)) {
      TCHAR *wdir = NULL;
      /* len_0 denotes string length *with* trailing 0 */
      size_t index = 0, len_0 = strlen(directory) + 1;

      wdir = (TCHAR *)malloc(len_0 * sizeof(TCHAR));
      if (wdir == NULL) {
        free(*ctx);
        *ctx = NULL;
        errno = ENOMEM;
        return 0;
      }

      if (!MultiByteToWideChar(CP_ACP, 0, directory, len_0, (WCHAR *)wdir,
                               len_0)) {
        for (index = 0; index < len_0; index++) {
          wdir[index] = (TCHAR)directory[index];
        }
      }

      (*ctx)->handle = FindFirstFile(wdir, &(*ctx)->ctx);

      free(wdir);
    } else {
      (*ctx)->handle = FindFirstFile((TCHAR *)directory, &(*ctx)->ctx);
    }

    if ((*ctx)->handle == INVALID_HANDLE_VALUE) {
      free(*ctx);
      *ctx = NULL;
      errno = EINVAL;
      return 0;
    }
  } else {
    if (FindNextFile((*ctx)->handle, &(*ctx)->ctx) == FALSE) {
      return 0;
    }
  }

  if (sizeof(TCHAR) != sizeof(char)) {
    TCHAR *wdir = (*ctx)->ctx.cFileName;
    size_t index, len_0 = 0;

    while (wdir[len_0] && len_0 < (sizeof((*ctx)->entry_name) - 1)) {
      len_0++;
    }
    len_0++;

    if (!WideCharToMultiByte(CP_ACP, 0, (WCHAR *)wdir, len_0,
                             (*ctx)->entry_name, sizeof((*ctx)->entry_name),
                             NULL, 0)) {
      for (index = 0; index < len_0; index++) {
        (*ctx)->entry_name[index] = (char)wdir[index];
      }
    }
  } else {
    strncpy((*ctx)->entry_name, (const char *)(*ctx)->ctx.cFileName,
            sizeof((*ctx)->entry_name) - 1);
  }

  (*ctx)->entry_name[sizeof((*ctx)->entry_name) - 1] = '\0';

  return (*ctx)->entry_name;
}

int OPENSSL_DIR_end(OPENSSL_DIR_CTX **ctx) {
  if (ctx != NULL && *ctx != NULL) {
    FindClose((*ctx)->handle);
    free(*ctx);
    *ctx = NULL;
    return 1;
  }
  errno = EINVAL;
  return 0;
}

#endif  /* OPENSSL_WINDOWS */

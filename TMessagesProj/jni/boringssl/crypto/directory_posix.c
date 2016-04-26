/* $LP: LPlib/source/LPdir_unix.c,v 1.11 2004/09/23 22:07:22 _cvs_levitte Exp $ */
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
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

#if !defined(_POSIX_C_SOURCE)
#define _POSIX_C_SOURCE 201409  /* for readdir_r */
#endif

#include "directory.h"


#if !defined(OPENSSL_WINDOWS)

#include <dirent.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>

#if defined(OPENSSL_PNACL)
/* pnacl doesn't include readdir_r! So we do the best we can. */
int readdir_r(DIR *dirp, struct dirent *entry, struct dirent **result) {
  errno = 0;
  *result = readdir(dirp);
  if (*result != NULL) {
    return 0;
  }
  if (errno) {
    return 1;
  }
  return 0;
}
#endif

struct OPENSSL_dir_context_st {
  DIR *dir;
  struct dirent dirent;
};

const char *OPENSSL_DIR_read(OPENSSL_DIR_CTX **ctx, const char *directory) {
  struct dirent *dirent;

  if (ctx == NULL || directory == NULL) {
    errno = EINVAL;
    return NULL;
  }

  errno = 0;
  if (*ctx == NULL) {
    *ctx = malloc(sizeof(OPENSSL_DIR_CTX));
    if (*ctx == NULL) {
      errno = ENOMEM;
      return 0;
    }
    memset(*ctx, 0, sizeof(OPENSSL_DIR_CTX));

    (*ctx)->dir = opendir(directory);
    if ((*ctx)->dir == NULL) {
      int save_errno = errno; /* Probably not needed, but I'm paranoid */
      free(*ctx);
      *ctx = NULL;
      errno = save_errno;
      return 0;
    }
  }

  if (readdir_r((*ctx)->dir, &(*ctx)->dirent, &dirent) != 0 ||
      dirent == NULL) {
    return 0;
  }

  return (*ctx)->dirent.d_name;
}

int OPENSSL_DIR_end(OPENSSL_DIR_CTX **ctx) {
  if (ctx != NULL && *ctx != NULL) {
    int r = closedir((*ctx)->dir);
    free(*ctx);
    *ctx = NULL;
    return r == 0;
  }

  errno = EINVAL;
  return 0;
}

#endif  /* !OPENSSL_WINDOWS */

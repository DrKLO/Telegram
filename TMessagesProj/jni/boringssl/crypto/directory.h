/* Copied from Richard Levitte's (richard@levitte.org) LP library.  All
 * symbol names have been changed, with permission from the author. */

/* $LP: LPlib/source/LPdir.h,v 1.1 2004/06/14 08:56:04 _cvs_levitte Exp $ */
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
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#ifndef OPENSSL_HEADER_DIRECTORY_H
#define OPENSSL_HEADER_DIRECTORY_H

#include <openssl/base.h>

#if defined(__cplusplus)
extern "C" {
#endif


/* Directory functions abstract the O/S specific operations for opening and
 * reading directories in the filesystem. */


/* OPENSSL_dir_context_st is an opaque structure that represents an open
 * directory and a position in that directory. */
typedef struct OPENSSL_dir_context_st OPENSSL_DIR_CTX;

/* OPENSSL_DIR_read reads a single filename from |ctx|. On the first call,
 * |directory| must be given and |*ctx| must be NULL. Subsequent calls with the
 * same |*ctx| will return subsequent file names until it returns NULL to
 * indicate EOF. The strings returned reference a buffer internal to the
 * |OPENSSL_DIR_CTX| and will be overridden by subsequent calls. */
OPENSSL_EXPORT const char *OPENSSL_DIR_read(OPENSSL_DIR_CTX **ctx,
                                            const char *directory);

/* OPENSSL_DIR_end closes |*ctx|. It returns one on success and zero on
 * error. */
OPENSSL_EXPORT int OPENSSL_DIR_end(OPENSSL_DIR_CTX **ctx);


#if defined(__cplusplus)
}  /* extern C */
#endif

#endif  /* OPENSSL_HEADER_DIRECTORY_H */

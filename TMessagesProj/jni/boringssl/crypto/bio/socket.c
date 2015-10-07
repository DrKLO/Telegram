/* crypto/bio/bss_sock.c */
/* Copyright (C) 1995-1998 Eric Young (eay@cryptsoft.com)
 * All rights reserved.
 *
 * This package is an SSL implementation written
 * by Eric Young (eay@cryptsoft.com).
 * The implementation was written so as to conform with Netscapes SSL.
 *
 * This library is free for commercial and non-commercial use as long as
 * the following conditions are aheared to.  The following conditions
 * apply to all code found in this distribution, be it the RC4, RSA,
 * lhash, DES, etc., code; not just the SSL code.  The SSL documentation
 * included with this distribution is covered by the same copyright terms
 * except that the holder is Tim Hudson (tjh@cryptsoft.com).
 *
 * Copyright remains Eric Young's, and as such any Copyright notices in
 * the code are not to be removed.
 * If this package is used in a product, Eric Young should be given attribution
 * as the author of the parts of the library used.
 * This can be in the form of a textual message at program startup or
 * in documentation (online or textual) provided with the package.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *    "This product includes cryptographic software written by
 *     Eric Young (eay@cryptsoft.com)"
 *    The word 'cryptographic' can be left out if the rouines from the library
 *    being used are not cryptographic related :-).
 * 4. If you include any Windows specific code (or a derivative thereof) from
 *    the apps directory (application code) you must include an acknowledgement:
 *    "This product includes software written by Tim Hudson (tjh@cryptsoft.com)"
 *
 * THIS SOFTWARE IS PROVIDED BY ERIC YOUNG ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * The licence and distribution terms for any publically available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution licence
 * [including the GNU Public Licence.] */

#include <openssl/bio.h>

#include <fcntl.h>
#include <string.h>

#if !defined(OPENSSL_WINDOWS)
#include <unistd.h>
#else
#pragma warning(push, 3)
#include <winsock2.h>
#pragma warning(pop)

#pragma comment(lib, "Ws2_32.lib")
#endif

#include "internal.h"


#if !defined(OPENSSL_WINDOWS)
static int closesocket(int sock) {
  return close(sock);
}
#endif

static int sock_new(BIO *bio) {
  bio->init = 0;
  bio->num = 0;
  bio->ptr = NULL;
  bio->flags = 0;
  return 1;
}

static int sock_free(BIO *bio) {
  if (bio == NULL) {
    return 0;
  }

  if (bio->shutdown) {
    if (bio->init) {
      closesocket(bio->num);
    }
    bio->init = 0;
    bio->flags = 0;
  }
  return 1;
}

static int sock_read(BIO *b, char *out, int outl) {
  int ret = 0;

  if (out == NULL) {
    return 0;
  }

  bio_clear_socket_error();
  ret = recv(b->num, out, outl, 0);
  BIO_clear_retry_flags(b);
  if (ret <= 0) {
    if (bio_fd_should_retry(ret)) {
      BIO_set_retry_read(b);
    }
  }
  return ret;
}

static int sock_write(BIO *b, const char *in, int inl) {
  int ret;

  bio_clear_socket_error();
  ret = send(b->num, in, inl, 0);
  BIO_clear_retry_flags(b);
  if (ret <= 0) {
    if (bio_fd_should_retry(ret)) {
      BIO_set_retry_write(b);
    }
  }
  return ret;
}

static int sock_puts(BIO *bp, const char *str) {
  return sock_write(bp, str, strlen(str));
}

static long sock_ctrl(BIO *b, int cmd, long num, void *ptr) {
  long ret = 1;
  int *ip;

  switch (cmd) {
    case BIO_C_SET_FD:
      sock_free(b);
      b->num = *((int *)ptr);
      b->shutdown = (int)num;
      b->init = 1;
      break;
    case BIO_C_GET_FD:
      if (b->init) {
        ip = (int *)ptr;
        if (ip != NULL) {
          *ip = b->num;
        }
        ret = b->num;
      } else {
        ret = -1;
      }
      break;
    case BIO_CTRL_GET_CLOSE:
      ret = b->shutdown;
      break;
    case BIO_CTRL_SET_CLOSE:
      b->shutdown = (int)num;
      break;
    case BIO_CTRL_FLUSH:
      ret = 1;
      break;
    default:
      ret = 0;
      break;
  }
  return ret;
}

static const BIO_METHOD methods_sockp = {
    BIO_TYPE_SOCKET,  "socket",  sock_write, sock_read, sock_puts,
    NULL /* gets, */, sock_ctrl, sock_new,   sock_free, NULL,
};

const BIO_METHOD *BIO_s_socket(void) { return &methods_sockp; }

BIO *BIO_new_socket(int fd, int close_flag) {
  BIO *ret;

  ret = BIO_new(BIO_s_socket());
  if (ret == NULL) {
    return NULL;
  }
  BIO_set_fd(ret, fd, close_flag);
  return ret;
}

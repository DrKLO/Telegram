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

#include <limits.h>
#include <string.h>

#include <openssl/buf.h>
#include <openssl/err.h>
#include <openssl/mem.h>

#include "../internal.h"


BIO *BIO_new_mem_buf(const void *buf, int len) {
  BIO *ret;
  BUF_MEM *b;
  const size_t size = len < 0 ? strlen((char *)buf) : (size_t)len;

  if (!buf && len != 0) {
    OPENSSL_PUT_ERROR(BIO, BIO_R_NULL_PARAMETER);
    return NULL;
  }

  ret = BIO_new(BIO_s_mem());
  if (ret == NULL) {
    return NULL;
  }

  b = (BUF_MEM *)ret->ptr;
  // BIO_FLAGS_MEM_RDONLY ensures |b->data| is not written to.
  b->data = (void *)buf;
  b->length = size;
  b->max = size;

  ret->flags |= BIO_FLAGS_MEM_RDONLY;

  // |num| is used to store the value that this BIO will return when it runs
  // out of data. If it's negative then the retry flags will also be set. Since
  // this is static data, retrying wont help
  ret->num = 0;

  return ret;
}

static int mem_new(BIO *bio) {
  BUF_MEM *b;

  b = BUF_MEM_new();
  if (b == NULL) {
    return 0;
  }

  // |shutdown| is used to store the close flag: whether the BIO has ownership
  // of the BUF_MEM.
  bio->shutdown = 1;
  bio->init = 1;
  bio->num = -1;
  bio->ptr = (char *)b;

  return 1;
}

static int mem_free(BIO *bio) {
  BUF_MEM *b;

  if (bio == NULL) {
    return 0;
  }

  if (!bio->shutdown || !bio->init || bio->ptr == NULL) {
    return 1;
  }

  b = (BUF_MEM *)bio->ptr;
  if (bio->flags & BIO_FLAGS_MEM_RDONLY) {
    b->data = NULL;
  }
  BUF_MEM_free(b);
  bio->ptr = NULL;
  return 1;
}

static int mem_read(BIO *bio, char *out, int outl) {
  int ret;
  BUF_MEM *b = (BUF_MEM*) bio->ptr;

  BIO_clear_retry_flags(bio);
  ret = outl;
  if (b->length < INT_MAX && ret > (int)b->length) {
    ret = b->length;
  }

  if (ret > 0) {
    OPENSSL_memcpy(out, b->data, ret);
    b->length -= ret;
    if (bio->flags & BIO_FLAGS_MEM_RDONLY) {
      b->data += ret;
    } else {
      OPENSSL_memmove(b->data, &b->data[ret], b->length);
    }
  } else if (b->length == 0) {
    ret = bio->num;
    if (ret != 0) {
      BIO_set_retry_read(bio);
    }
  }
  return ret;
}

static int mem_write(BIO *bio, const char *in, int inl) {
  int ret = -1;
  int blen;
  BUF_MEM *b;

  b = (BUF_MEM *)bio->ptr;

  if (bio->flags & BIO_FLAGS_MEM_RDONLY) {
    OPENSSL_PUT_ERROR(BIO, BIO_R_WRITE_TO_READ_ONLY_BIO);
    goto err;
  }

  BIO_clear_retry_flags(bio);
  blen = b->length;
  if (INT_MAX - blen < inl) {
    goto err;
  }
  if (BUF_MEM_grow_clean(b, blen + inl) != ((size_t) blen) + inl) {
    goto err;
  }
  OPENSSL_memcpy(&b->data[blen], in, inl);
  ret = inl;

err:
  return ret;
}

static int mem_gets(BIO *bio, char *buf, int size) {
  int i, j;
  char *p;
  BUF_MEM *b = (BUF_MEM *)bio->ptr;

  BIO_clear_retry_flags(bio);
  j = b->length;
  if (size - 1 < j) {
    j = size - 1;
  }
  if (j <= 0) {
    if (size > 0) {
      *buf = 0;
    }
    return 0;
  }

  p = b->data;
  for (i = 0; i < j; i++) {
    if (p[i] == '\n') {
      i++;
      break;
    }
  }

  // i is now the max num of bytes to copy, either j or up to and including the
  // first newline

  i = mem_read(bio, buf, i);
  if (i > 0) {
    buf[i] = '\0';
  }
  return i;
}

static long mem_ctrl(BIO *bio, int cmd, long num, void *ptr) {
  long ret = 1;
  char **pptr;

  BUF_MEM *b = (BUF_MEM *)bio->ptr;

  switch (cmd) {
    case BIO_CTRL_RESET:
      if (b->data != NULL) {
        // For read only case reset to the start again
        if (bio->flags & BIO_FLAGS_MEM_RDONLY) {
          b->data -= b->max - b->length;
          b->length = b->max;
        } else {
          OPENSSL_memset(b->data, 0, b->max);
          b->length = 0;
        }
      }
      break;
    case BIO_CTRL_EOF:
      ret = (long)(b->length == 0);
      break;
    case BIO_C_SET_BUF_MEM_EOF_RETURN:
      bio->num = (int)num;
      break;
    case BIO_CTRL_INFO:
      ret = (long)b->length;
      if (ptr != NULL) {
        pptr = (char **)ptr;
        *pptr = (char *)&b->data[0];
      }
      break;
    case BIO_C_SET_BUF_MEM:
      mem_free(bio);
      bio->shutdown = (int)num;
      bio->ptr = ptr;
      break;
    case BIO_C_GET_BUF_MEM_PTR:
      if (ptr != NULL) {
        pptr = (char **)ptr;
        *pptr = (char *)b;
      }
      break;
    case BIO_CTRL_GET_CLOSE:
      ret = (long)bio->shutdown;
      break;
    case BIO_CTRL_SET_CLOSE:
      bio->shutdown = (int)num;
      break;

    case BIO_CTRL_WPENDING:
      ret = 0L;
      break;
    case BIO_CTRL_PENDING:
      ret = (long)b->length;
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

static const BIO_METHOD mem_method = {
    BIO_TYPE_MEM,    "memory buffer",
    mem_write,       mem_read,
    NULL /* puts */, mem_gets,
    mem_ctrl,        mem_new,
    mem_free,        NULL /* callback_ctrl */,
};

const BIO_METHOD *BIO_s_mem(void) { return &mem_method; }

int BIO_mem_contents(const BIO *bio, const uint8_t **out_contents,
                     size_t *out_len) {
  const BUF_MEM *b;
  if (bio->method != &mem_method) {
    return 0;
  }

  b = (BUF_MEM *)bio->ptr;
  *out_contents = (uint8_t *)b->data;
  *out_len = b->length;
  return 1;
}

long BIO_get_mem_data(BIO *bio, char **contents) {
  return BIO_ctrl(bio, BIO_CTRL_INFO, 0, (char *) contents);
}

int BIO_get_mem_ptr(BIO *bio, BUF_MEM **out) {
  return BIO_ctrl(bio, BIO_C_GET_BUF_MEM_PTR, 0, (char *) out);
}

int BIO_set_mem_buf(BIO *bio, BUF_MEM *b, int take_ownership) {
  return BIO_ctrl(bio, BIO_C_SET_BUF_MEM, take_ownership, (char *) b);
}

int BIO_set_mem_eof_return(BIO *bio, int eof_value) {
  return BIO_ctrl(bio, BIO_C_SET_BUF_MEM_EOF_RETURN, eof_value, NULL);
}

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

#include <assert.h>
#include <errno.h>
#include <limits.h>
#include <string.h>

#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/thread.h>

#include "../internal.h"


/* BIO_set initialises a BIO structure to have the given type and sets the
 * reference count to one. It returns one on success or zero on error. */
static int bio_set(BIO *bio, const BIO_METHOD *method) {
  /* This function can be called with a stack allocated |BIO| so we have to
   * assume that the contents of |BIO| are arbitary. This also means that it'll
   * leak memory if you call |BIO_set| twice on the same BIO. */
  memset(bio, 0, sizeof(BIO));

  bio->method = method;
  bio->shutdown = 1;
  bio->references = 1;

  if (method->create != NULL && !method->create(bio)) {
    return 0;
  }

  return 1;
}

BIO *BIO_new(const BIO_METHOD *method) {
  BIO *ret = OPENSSL_malloc(sizeof(BIO));
  if (ret == NULL) {
    OPENSSL_PUT_ERROR(BIO, ERR_R_MALLOC_FAILURE);
    return NULL;
  }

  if (!bio_set(ret, method)) {
    OPENSSL_free(ret);
    ret = NULL;
  }

  return ret;
}

int BIO_free(BIO *bio) {
  BIO *next_bio;

  for (; bio != NULL; bio = next_bio) {
    if (!CRYPTO_refcount_dec_and_test_zero(&bio->references)) {
      return 0;
    }

    if (bio->callback != NULL) {
      int i = (int)bio->callback(bio, BIO_CB_FREE, NULL, 0, 0, 1);
      if (i <= 0) {
        return i;
      }
    }

    next_bio = BIO_pop(bio);

    if (bio->method != NULL && bio->method->destroy != NULL) {
      bio->method->destroy(bio);
    }

    OPENSSL_free(bio);
  }
  return 1;
}

BIO *BIO_up_ref(BIO *bio) {
  CRYPTO_refcount_inc(&bio->references);
  return bio;
}

void BIO_vfree(BIO *bio) {
  BIO_free(bio);
}

void BIO_free_all(BIO *bio) {
  BIO_free(bio);
}

static int bio_io(BIO *bio, void *buf, int len, size_t method_offset,
                  int callback_flags, size_t *num) {
  int i;
  typedef int (*io_func_t)(BIO *, char *, int);
  io_func_t io_func = NULL;

  if (bio != NULL && bio->method != NULL) {
    io_func =
        *((const io_func_t *)(((const uint8_t *)bio->method) + method_offset));
  }

  if (io_func == NULL) {
    OPENSSL_PUT_ERROR(BIO, BIO_R_UNSUPPORTED_METHOD);
    return -2;
  }

  if (bio->callback != NULL) {
    i = (int) bio->callback(bio, callback_flags, buf, len, 0L, 1L);
    if (i <= 0) {
      return i;
    }
  }

  if (!bio->init) {
    OPENSSL_PUT_ERROR(BIO, BIO_R_UNINITIALIZED);
    return -2;
  }

  i = 0;
  if (buf != NULL && len > 0) {
    i = io_func(bio, buf, len);
  }

  if (i > 0) {
    *num += i;
  }

  if (bio->callback != NULL) {
    i = (int)(bio->callback(bio, callback_flags | BIO_CB_RETURN, buf, len, 0L,
                            (long)i));
  }

  return i;
}

int BIO_read(BIO *bio, void *buf, int len) {
  return bio_io(bio, buf, len, offsetof(BIO_METHOD, bread), BIO_CB_READ,
                &bio->num_read);
}

int BIO_gets(BIO *bio, char *buf, int len) {
  return bio_io(bio, buf, len, offsetof(BIO_METHOD, bgets), BIO_CB_GETS,
                &bio->num_read);
}

int BIO_write(BIO *bio, const void *in, int inl) {
  return bio_io(bio, (char *)in, inl, offsetof(BIO_METHOD, bwrite),
                BIO_CB_WRITE, &bio->num_write);
}

int BIO_puts(BIO *bio, const char *in) {
  return BIO_write(bio, in, strlen(in));
}

int BIO_flush(BIO *bio) {
  return BIO_ctrl(bio, BIO_CTRL_FLUSH, 0, NULL);
}

long BIO_ctrl(BIO *bio, int cmd, long larg, void *parg) {
  long ret;

  if (bio == NULL) {
    return 0;
  }

  if (bio->method == NULL || bio->method->ctrl == NULL) {
    OPENSSL_PUT_ERROR(BIO, BIO_R_UNSUPPORTED_METHOD);
    return -2;
  }

  if (bio->callback != NULL) {
    ret = bio->callback(bio, BIO_CB_CTRL, parg, cmd, larg, 1);
    if (ret <= 0) {
      return ret;
    }
  }

  ret = bio->method->ctrl(bio, cmd, larg, parg);

  if (bio->callback != NULL) {
    ret = bio->callback(bio, BIO_CB_CTRL | BIO_CB_RETURN, parg, cmd, larg, ret);
  }

  return ret;
}

char *BIO_ptr_ctrl(BIO *b, int cmd, long larg) {
  char *p = NULL;

  if (BIO_ctrl(b, cmd, larg, (void *)&p) <= 0) {
    return NULL;
  }

  return p;
}

long BIO_int_ctrl(BIO *b, int cmd, long larg, int iarg) {
  int i = iarg;

  return BIO_ctrl(b, cmd, larg, (void *)&i);
}

int BIO_reset(BIO *bio) {
  return BIO_ctrl(bio, BIO_CTRL_RESET, 0, NULL);
}

void BIO_set_flags(BIO *bio, int flags) {
  bio->flags |= flags;
}

int BIO_test_flags(const BIO *bio, int flags) {
  return bio->flags & flags;
}

int BIO_should_read(const BIO *bio) {
  return BIO_test_flags(bio, BIO_FLAGS_READ);
}

int BIO_should_write(const BIO *bio) {
  return BIO_test_flags(bio, BIO_FLAGS_WRITE);
}

int BIO_should_retry(const BIO *bio) {
  return BIO_test_flags(bio, BIO_FLAGS_SHOULD_RETRY);
}

int BIO_should_io_special(const BIO *bio) {
  return BIO_test_flags(bio, BIO_FLAGS_IO_SPECIAL);
}

int BIO_get_retry_reason(const BIO *bio) { return bio->retry_reason; }

void BIO_clear_flags(BIO *bio, int flags) {
  bio->flags &= ~flags;
}

void BIO_set_retry_read(BIO *bio) {
  bio->flags |= BIO_FLAGS_READ | BIO_FLAGS_SHOULD_RETRY;
}

void BIO_set_retry_write(BIO *bio) {
  bio->flags |= BIO_FLAGS_WRITE | BIO_FLAGS_SHOULD_RETRY;
}

static const int kRetryFlags = BIO_FLAGS_RWS | BIO_FLAGS_SHOULD_RETRY;

int BIO_get_retry_flags(BIO *bio) {
  return bio->flags & kRetryFlags;
}

void BIO_clear_retry_flags(BIO *bio) {
  bio->flags &= ~kRetryFlags;
  bio->retry_reason = 0;
}

int BIO_method_type(const BIO *bio) { return bio->method->type; }

void BIO_copy_next_retry(BIO *bio) {
  BIO_clear_retry_flags(bio);
  BIO_set_flags(bio, BIO_get_retry_flags(bio->next_bio));
  bio->retry_reason = bio->next_bio->retry_reason;
}

long BIO_callback_ctrl(BIO *bio, int cmd, bio_info_cb fp) {
  long ret;
  bio_info_cb cb;

  if (bio == NULL) {
    return 0;
  }

  if (bio->method == NULL || bio->method->callback_ctrl == NULL) {
    OPENSSL_PUT_ERROR(BIO, BIO_R_UNSUPPORTED_METHOD);
    return 0;
  }

  cb = bio->callback;

  if (cb != NULL) {
    ret = cb(bio, BIO_CB_CTRL, (void *)&fp, cmd, 0, 1L);
    if (ret <= 0) {
      return ret;
    }
  }

  ret = bio->method->callback_ctrl(bio, cmd, fp);

  if (cb != NULL) {
    ret = cb(bio, BIO_CB_CTRL | BIO_CB_RETURN, (void *)&fp, cmd, 0, ret);
  }

  return ret;
}

size_t BIO_pending(const BIO *bio) {
  return BIO_ctrl((BIO *) bio, BIO_CTRL_PENDING, 0, NULL);
}

size_t BIO_ctrl_pending(const BIO *bio) {
  return BIO_pending(bio);
}

size_t BIO_wpending(const BIO *bio) {
  return BIO_ctrl((BIO *) bio, BIO_CTRL_WPENDING, 0, NULL);
}

int BIO_set_close(BIO *bio, int close_flag) {
  return BIO_ctrl(bio, BIO_CTRL_SET_CLOSE, close_flag, NULL);
}

void BIO_set_callback(BIO *bio, bio_info_cb callback_func) {
  bio->callback = callback_func;
}

void BIO_set_callback_arg(BIO *bio, char *arg) {
  bio->cb_arg = arg;
}

char *BIO_get_callback_arg(const BIO *bio) {
  return bio->cb_arg;
}

OPENSSL_EXPORT size_t BIO_number_read(const BIO *bio) {
  return bio->num_read;
}

OPENSSL_EXPORT size_t BIO_number_written(const BIO *bio) {
  return bio->num_write;
}

BIO *BIO_push(BIO *bio, BIO *appended_bio) {
  BIO *last_bio;

  if (bio == NULL) {
    return bio;
  }

  last_bio = bio;
  while (last_bio->next_bio != NULL) {
    last_bio = last_bio->next_bio;
  }

  last_bio->next_bio = appended_bio;
  return bio;
}

BIO *BIO_pop(BIO *bio) {
  BIO *ret;

  if (bio == NULL) {
    return NULL;
  }
  ret = bio->next_bio;
  bio->next_bio = NULL;
  return ret;
}

BIO *BIO_next(BIO *bio) {
  if (!bio) {
    return NULL;
  }
  return bio->next_bio;
}

BIO *BIO_find_type(BIO *bio, int type) {
  int method_type, mask;

  if (!bio) {
    return NULL;
  }
  mask = type & 0xff;

  do {
    if (bio->method != NULL) {
      method_type = bio->method->type;

      if (!mask) {
        if (method_type & type) {
          return bio;
        }
      } else if (method_type == type) {
        return bio;
      }
    }
    bio = bio->next_bio;
  } while (bio != NULL);

  return NULL;
}

int BIO_indent(BIO *bio, unsigned indent, unsigned max_indent) {
  if (indent > max_indent) {
    indent = max_indent;
  }

  while (indent--) {
    if (BIO_puts(bio, " ") != 1) {
      return 0;
    }
  }
  return 1;
}

static int print_bio(const char *str, size_t len, void *bio) {
  return BIO_write((BIO *)bio, str, len);
}

void BIO_print_errors(BIO *bio) {
  ERR_print_errors_cb(print_bio, bio);
}

void ERR_print_errors(BIO *bio) {
  BIO_print_errors(bio);
}

/* bio_read_all reads everything from |bio| and prepends |prefix| to it. On
 * success, |*out| is set to an allocated buffer (which should be freed with
 * |OPENSSL_free|), |*out_len| is set to its length and one is returned. The
 * buffer will contain |prefix| followed by the contents of |bio|. On failure,
 * zero is returned.
 *
 * The function will fail if the size of the output would equal or exceed
 * |max_len|. */
static int bio_read_all(BIO *bio, uint8_t **out, size_t *out_len,
                        const uint8_t *prefix, size_t prefix_len,
                        size_t max_len) {
  static const size_t kChunkSize = 4096;

  size_t len = prefix_len + kChunkSize;
  if (len > max_len) {
    len = max_len;
  }
  if (len < prefix_len) {
    return 0;
  }
  *out = OPENSSL_malloc(len);
  if (*out == NULL) {
    return 0;
  }
  memcpy(*out, prefix, prefix_len);
  size_t done = prefix_len;

  for (;;) {
    if (done == len) {
      OPENSSL_free(*out);
      return 0;
    }
    const size_t todo = len - done;
    assert(todo < INT_MAX);
    const int n = BIO_read(bio, *out + done, todo);
    if (n == 0) {
      *out_len = done;
      return 1;
    } else if (n == -1) {
      OPENSSL_free(*out);
      return 0;
    }

    done += n;
    if (len < max_len && len - done < kChunkSize / 2) {
      len += kChunkSize;
      if (len < kChunkSize || len > max_len) {
        len = max_len;
      }
      uint8_t *new_buf = OPENSSL_realloc(*out, len);
      if (new_buf == NULL) {
        OPENSSL_free(*out);
        return 0;
      }
      *out = new_buf;
    }
  }
}

int BIO_read_asn1(BIO *bio, uint8_t **out, size_t *out_len, size_t max_len) {
  uint8_t header[6];

  static const size_t kInitialHeaderLen = 2;
  if (BIO_read(bio, header, kInitialHeaderLen) != kInitialHeaderLen) {
    return 0;
  }

  const uint8_t tag = header[0];
  const uint8_t length_byte = header[1];

  if ((tag & 0x1f) == 0x1f) {
    /* Long form tags are not supported. */
    return 0;
  }

  size_t len, header_len;
  if ((length_byte & 0x80) == 0) {
    /* Short form length. */
    len = length_byte;
    header_len = kInitialHeaderLen;
  } else {
    const size_t num_bytes = length_byte & 0x7f;

    if ((tag & 0x20 /* constructed */) != 0 && num_bytes == 0) {
      /* indefinite length. */
      return bio_read_all(bio, out, out_len, header, kInitialHeaderLen,
                          max_len);
    }

    if (num_bytes == 0 || num_bytes > 4) {
      return 0;
    }

    if (BIO_read(bio, header + kInitialHeaderLen, num_bytes) != num_bytes) {
      return 0;
    }
    header_len = kInitialHeaderLen + num_bytes;

    uint32_t len32 = 0;
    unsigned i;
    for (i = 0; i < num_bytes; i++) {
      len32 <<= 8;
      len32 |= header[kInitialHeaderLen + i];
    }

    if (len32 < 128) {
      /* Length should have used short-form encoding. */
      return 0;
    }

    if ((len32 >> ((num_bytes-1)*8)) == 0) {
      /* Length should have been at least one byte shorter. */
      return 0;
    }

    len = len32;
  }

  if (len + header_len < len ||
      len + header_len > max_len) {
    return 0;
  }
  len += header_len;
  *out_len = len;

  *out = OPENSSL_malloc(len);
  if (*out == NULL) {
    return 0;
  }
  memcpy(*out, header, header_len);
  if (BIO_read(bio, (*out) + header_len, len - header_len) !=
      len - header_len) {
    OPENSSL_free(*out);
    return 0;
  }

  return 1;
}

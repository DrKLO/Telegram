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

#include <string.h>

#include <openssl/buf.h>
#include <openssl/err.h>
#include <openssl/mem.h>


#define DEFAULT_BUFFER_SIZE 4096

typedef struct bio_f_buffer_ctx_struct {
  /* Buffers are setup like this:
   *
   * <---------------------- size ----------------------->
   * +---------------------------------------------------+
   * | consumed | remaining          | free space        |
   * +---------------------------------------------------+
   * <-- off --><------- len ------->
   */

  int ibuf_size;  /* how big is the input buffer */
  int obuf_size;  /* how big is the output buffer */

  char *ibuf;   /* the char array */
  int ibuf_len; /* how many bytes are in it */
  int ibuf_off; /* write/read offset */

  char *obuf;   /* the char array */
  int obuf_len; /* how many bytes are in it */
  int obuf_off; /* write/read offset */
} BIO_F_BUFFER_CTX;

static int buffer_new(BIO *bio) {
  BIO_F_BUFFER_CTX *ctx;

  ctx = OPENSSL_malloc(sizeof(BIO_F_BUFFER_CTX));
  if (ctx == NULL) {
    return 0;
  }
  memset(ctx, 0, sizeof(BIO_F_BUFFER_CTX));

  ctx->ibuf = OPENSSL_malloc(DEFAULT_BUFFER_SIZE);
  if (ctx->ibuf == NULL) {
    goto err1;
  }
  ctx->obuf = (char *)OPENSSL_malloc(DEFAULT_BUFFER_SIZE);
  if (ctx->obuf == NULL) {
    goto err2;
  }
  ctx->ibuf_size = DEFAULT_BUFFER_SIZE;
  ctx->obuf_size = DEFAULT_BUFFER_SIZE;

  bio->init = 1;
  bio->ptr = (char *)ctx;
  return 1;

err2:
  OPENSSL_free(ctx->ibuf);

err1:
  OPENSSL_free(ctx);
  return 0;
}

static int buffer_free(BIO *bio) {
  BIO_F_BUFFER_CTX *ctx;

  if (bio == NULL || bio->ptr == NULL) {
    return 0;
  }

  ctx = (BIO_F_BUFFER_CTX *)bio->ptr;
  OPENSSL_free(ctx->ibuf);
  OPENSSL_free(ctx->obuf);
  OPENSSL_free(bio->ptr);

  bio->ptr = NULL;
  bio->init = 0;
  bio->flags = 0;

  return 1;
}

static int buffer_read(BIO *bio, char *out, int outl) {
  int i, num = 0;
  BIO_F_BUFFER_CTX *ctx;

  ctx = (BIO_F_BUFFER_CTX *)bio->ptr;

  if (ctx == NULL || bio->next_bio == NULL) {
    return 0;
  }

  num = 0;
  BIO_clear_retry_flags(bio);

  for (;;) {
    i = ctx->ibuf_len;
    /* If there is stuff left over, grab it */
    if (i != 0) {
      if (i > outl) {
        i = outl;
      }
      memcpy(out, &ctx->ibuf[ctx->ibuf_off], i);
      ctx->ibuf_off += i;
      ctx->ibuf_len -= i;
      num += i;
      if (outl == i) {
        return num;
      }
      outl -= i;
      out += i;
    }

    /* We may have done a partial read. Try to do more. We have nothing in the
     * buffer. If we get an error and have read some data, just return it and
     * let them retry to get the error again. Copy direct to parent address
     * space */
    if (outl > ctx->ibuf_size) {
      for (;;) {
        i = BIO_read(bio->next_bio, out, outl);
        if (i <= 0) {
          BIO_copy_next_retry(bio);
          if (i < 0) {
            return (num > 0) ? num : i;
          }
          return num;
        }
        num += i;
        if (outl == i) {
          return num;
        }
        out += i;
        outl -= i;
      }
    }
    /* else */

    /* we are going to be doing some buffering */
    i = BIO_read(bio->next_bio, ctx->ibuf, ctx->ibuf_size);
    if (i <= 0) {
      BIO_copy_next_retry(bio);
      if (i < 0) {
        return (num > 0) ? num : i;
      }
      return num;
    }
    ctx->ibuf_off = 0;
    ctx->ibuf_len = i;
  }
}

static int buffer_write(BIO *b, const char *in, int inl) {
  int i, num = 0;
  BIO_F_BUFFER_CTX *ctx;

  ctx = (BIO_F_BUFFER_CTX *)b->ptr;
  if (ctx == NULL || b->next_bio == NULL) {
    return 0;
  }

  BIO_clear_retry_flags(b);

  for (;;) {
    i = ctx->obuf_size - (ctx->obuf_off + ctx->obuf_len);
    /* add to buffer and return */
    if (i >= inl) {
      memcpy(&ctx->obuf[ctx->obuf_off + ctx->obuf_len], in, inl);
      ctx->obuf_len += inl;
      return num + inl;
    }
    /* else */
    /* stuff already in buffer, so add to it first, then flush */
    if (ctx->obuf_len != 0) {
      if (i > 0) {
        memcpy(&ctx->obuf[ctx->obuf_off + ctx->obuf_len], in, i);
        in += i;
        inl -= i;
        num += i;
        ctx->obuf_len += i;
      }

      /* we now have a full buffer needing flushing */
      for (;;) {
        i = BIO_write(b->next_bio, &ctx->obuf[ctx->obuf_off], ctx->obuf_len);
        if (i <= 0) {
          BIO_copy_next_retry(b);

          if (i < 0) {
            return (num > 0) ? num : i;
          }
          return num;
        }
        ctx->obuf_off += i;
        ctx->obuf_len -= i;
        if (ctx->obuf_len == 0) {
          break;
        }
      }
    }

    /* we only get here if the buffer has been flushed and we
     * still have stuff to write */
    ctx->obuf_off = 0;

    /* we now have inl bytes to write */
    while (inl >= ctx->obuf_size) {
      i = BIO_write(b->next_bio, in, inl);
      if (i <= 0) {
        BIO_copy_next_retry(b);
        if (i < 0) {
          return (num > 0) ? num : i;
        }
        return num;
      }
      num += i;
      in += i;
      inl -= i;
      if (inl == 0) {
        return num;
      }
    }

    /* copy the rest into the buffer since we have only a small
     * amount left */
  }
}

static long buffer_ctrl(BIO *b, int cmd, long num, void *ptr) {
  BIO_F_BUFFER_CTX *ctx;
  long ret = 1;
  char *p1, *p2;
  int r, *ip;
  int ibs, obs;

  ctx = (BIO_F_BUFFER_CTX *)b->ptr;

  switch (cmd) {
    case BIO_CTRL_RESET:
      ctx->ibuf_off = 0;
      ctx->ibuf_len = 0;
      ctx->obuf_off = 0;
      ctx->obuf_len = 0;
      if (b->next_bio == NULL) {
        return 0;
      }
      ret = BIO_ctrl(b->next_bio, cmd, num, ptr);
      break;

    case BIO_CTRL_INFO:
      ret = ctx->obuf_len;
      break;

    case BIO_CTRL_WPENDING:
      ret = (long)ctx->obuf_len;
      if (ret == 0) {
        if (b->next_bio == NULL) {
          return 0;
        }
        ret = BIO_ctrl(b->next_bio, cmd, num, ptr);
      }
      break;

    case BIO_CTRL_PENDING:
      ret = (long)ctx->ibuf_len;
      if (ret == 0) {
        if (b->next_bio == NULL) {
          return 0;
        }
        ret = BIO_ctrl(b->next_bio, cmd, num, ptr);
      }
      break;

    case BIO_C_SET_BUFF_SIZE:
      ip = (int *)ptr;
      if (*ip == 0) {
        ibs = (int)num;
        obs = ctx->obuf_size;
      } else /* if (*ip == 1) */ {
        ibs = ctx->ibuf_size;
        obs = (int)num;
      }
      p1 = ctx->ibuf;
      p2 = ctx->obuf;
      if (ibs > DEFAULT_BUFFER_SIZE && ibs != ctx->ibuf_size) {
        p1 = (char *)OPENSSL_malloc(ibs);
        if (p1 == NULL) {
          goto malloc_error;
        }
      }
      if (obs > DEFAULT_BUFFER_SIZE && obs != ctx->obuf_size) {
        p2 = (char *)OPENSSL_malloc(obs);
        if (p2 == NULL) {
          if (p1 != ctx->ibuf) {
            OPENSSL_free(p1);
          }
          goto malloc_error;
        }
      }

      if (ctx->ibuf != p1) {
        OPENSSL_free(ctx->ibuf);
        ctx->ibuf = p1;
        ctx->ibuf_size = ibs;
      }
      ctx->ibuf_off = 0;
      ctx->ibuf_len = 0;

      if (ctx->obuf != p2) {
        OPENSSL_free(ctx->obuf);
        ctx->obuf = p2;
        ctx->obuf_size = obs;
      }
      ctx->obuf_off = 0;
      ctx->obuf_len = 0;
      break;

    case BIO_CTRL_FLUSH:
      if (b->next_bio == NULL) {
        return 0;
      }

      while (ctx->obuf_len > 0) {
        BIO_clear_retry_flags(b);
        r = BIO_write(b->next_bio, &(ctx->obuf[ctx->obuf_off]),
                      ctx->obuf_len);
        BIO_copy_next_retry(b);
        if (r <= 0) {
          return r;
        }
        ctx->obuf_off += r;
        ctx->obuf_len -= r;
      }

      ctx->obuf_len = 0;
      ctx->obuf_off = 0;
      ret = BIO_ctrl(b->next_bio, cmd, num, ptr);
      break;

    default:
      if (b->next_bio == NULL) {
        return 0;
      }
      BIO_clear_retry_flags(b);
      ret = BIO_ctrl(b->next_bio, cmd, num, ptr);
      BIO_copy_next_retry(b);
      break;
  }
  return ret;

malloc_error:
  OPENSSL_PUT_ERROR(BIO, ERR_R_MALLOC_FAILURE);
  return 0;
}

static long buffer_callback_ctrl(BIO *b, int cmd, bio_info_cb fp) {
  long ret = 1;

  if (b->next_bio == NULL) {
    return 0;
  }

  switch (cmd) {
    default:
      ret = BIO_callback_ctrl(b->next_bio, cmd, fp);
      break;
  }
  return ret;
}

static int buffer_gets(BIO *b, char *buf, int size) {
  BIO_F_BUFFER_CTX *ctx;
  int num = 0, i, flag;
  char *p;

  ctx = (BIO_F_BUFFER_CTX *)b->ptr;
  if (buf == NULL || size <= 0) {
    return 0;
  }

  size--; /* reserve space for a '\0' */
  BIO_clear_retry_flags(b);

  for (;;) {
    if (ctx->ibuf_len > 0) {
      p = &ctx->ibuf[ctx->ibuf_off];
      flag = 0;
      for (i = 0; (i < ctx->ibuf_len) && (i < size); i++) {
        *(buf++) = p[i];
        if (p[i] == '\n') {
          flag = 1;
          i++;
          break;
        }
      }
      num += i;
      size -= i;
      ctx->ibuf_len -= i;
      ctx->ibuf_off += i;
      if (flag || size == 0) {
        *buf = '\0';
        return num;
      }
    } else /* read another chunk */
    {
      i = BIO_read(b->next_bio, ctx->ibuf, ctx->ibuf_size);
      if (i <= 0) {
        BIO_copy_next_retry(b);
        *buf = '\0';
        if (i < 0) {
          return (num > 0) ? num : i;
        }
        return num;
      }
      ctx->ibuf_len = i;
      ctx->ibuf_off = 0;
    }
  }
}

static int buffer_puts(BIO *b, const char *str) {
  return buffer_write(b, str, strlen(str));
}

static const BIO_METHOD methods_buffer = {
    BIO_TYPE_BUFFER, "buffer",             buffer_write, buffer_read,
    buffer_puts,     buffer_gets,          buffer_ctrl,  buffer_new,
    buffer_free,     buffer_callback_ctrl,
};

const BIO_METHOD *BIO_f_buffer(void) { return &methods_buffer; }

int BIO_set_read_buffer_size(BIO *bio, int buffer_size) {
  return BIO_int_ctrl(bio, BIO_C_SET_BUFF_SIZE, buffer_size, 0);
}

int BIO_set_write_buffer_size(BIO *bio, int buffer_size) {
  return BIO_int_ctrl(bio, BIO_C_SET_BUFF_SIZE, buffer_size, 1);
}

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

#include "../internal.h"


// hexdump_ctx contains the state of a hexdump.
struct hexdump_ctx {
  BIO *bio;
  char right_chars[18];  // the contents of the right-hand side, ASCII dump.
  unsigned used;         // number of bytes in the current line.
  size_t n;              // number of bytes total.
  unsigned indent;
};

static void hexbyte(char *out, uint8_t b) {
  static const char hextable[] = "0123456789abcdef";
  out[0] = hextable[b>>4];
  out[1] = hextable[b&0x0f];
}

static char to_char(uint8_t b) {
  if (b < 32 || b > 126) {
          return '.';
  }
  return b;
}

// hexdump_write adds |len| bytes of |data| to the current hex dump described by
// |ctx|.
static int hexdump_write(struct hexdump_ctx *ctx, const uint8_t *data,
                         size_t len) {
  char buf[10];
  unsigned l;

  // Output lines look like:
  // 00000010  2e 2f 30 31 32 33 34 35  36 37 38 ... 3c 3d // |./0123456789:;<=|
  // ^ offset                          ^ extra space           ^ ASCII of line

  for (size_t i = 0; i < len; i++) {
    if (ctx->used == 0) {
      // The beginning of a line.
      BIO_indent(ctx->bio, ctx->indent, UINT_MAX);

      hexbyte(&buf[0], ctx->n >> 24);
      hexbyte(&buf[2], ctx->n >> 16);
      hexbyte(&buf[4], ctx->n >> 8);
      hexbyte(&buf[6], ctx->n);
      buf[8] = buf[9] = ' ';
      if (BIO_write(ctx->bio, buf, 10) < 0) {
        return 0;
      }
    }

    hexbyte(buf, data[i]);
    buf[2] = ' ';
    l = 3;
    if (ctx->used == 7) {
      // There's an additional space after the 8th byte.
      buf[3] = ' ';
      l = 4;
    } else if (ctx->used == 15) {
      // At the end of the line there's an extra space and the bar for the
      // right column.
      buf[3] = ' ';
      buf[4] = '|';
      l = 5;
    }

    if (BIO_write(ctx->bio, buf, l) < 0) {
      return 0;
    }
    ctx->right_chars[ctx->used] = to_char(data[i]);
    ctx->used++;
    ctx->n++;
    if (ctx->used == 16) {
      ctx->right_chars[16] = '|';
      ctx->right_chars[17] = '\n';
      if (BIO_write(ctx->bio, ctx->right_chars, sizeof(ctx->right_chars)) < 0) {
        return 0;
      }
      ctx->used = 0;
    }
  }

  return 1;
}

// finish flushes any buffered data in |ctx|.
static int finish(struct hexdump_ctx *ctx) {
  // See the comments in |hexdump| for the details of this format.
  const unsigned n_bytes = ctx->used;
  unsigned l;
  char buf[5];

  if (n_bytes == 0) {
    return 1;
  }

  OPENSSL_memset(buf, ' ', 4);
  buf[4] = '|';

  for (; ctx->used < 16; ctx->used++) {
    l = 3;
    if (ctx->used == 7) {
      l = 4;
    } else if (ctx->used == 15) {
      l = 5;
    }
    if (BIO_write(ctx->bio, buf, l) < 0) {
      return 0;
    }
  }

  ctx->right_chars[n_bytes] = '|';
  ctx->right_chars[n_bytes + 1] = '\n';
  if (BIO_write(ctx->bio, ctx->right_chars, n_bytes + 2) < 0) {
    return 0;
  }
  return 1;
}

int BIO_hexdump(BIO *bio, const uint8_t *data, size_t len, unsigned indent) {
  struct hexdump_ctx ctx;
  OPENSSL_memset(&ctx, 0, sizeof(ctx));
  ctx.bio = bio;
  ctx.indent = indent;

  if (!hexdump_write(&ctx, data, len) || !finish(&ctx)) {
    return 0;
  }

  return 1;
}

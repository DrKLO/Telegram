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

#include <openssl/base64.h>

#include <assert.h>
#include <limits.h>
#include <string.h>


static const unsigned char data_bin2ascii[65] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

#define conv_bin2ascii(a) (data_bin2ascii[(a) & 0x3f])

/* 64 char lines
 * pad input with 0
 * left over chars are set to =
 * 1 byte  => xx==
 * 2 bytes => xxx=
 * 3 bytes => xxxx
 */
#define BIN_PER_LINE    (64/4*3)
#define CHUNKS_PER_LINE (64/4)
#define CHAR_PER_LINE   (64+1)

/* 0xF0 is a EOLN
 * 0xF1 is ignore but next needs to be 0xF0 (for \r\n processing).
 * 0xF2 is EOF
 * 0xE0 is ignore at start of line.
 * 0xFF is error */

#define B64_EOLN 0xF0
#define B64_CR 0xF1
#define B64_EOF 0xF2
#define B64_WS 0xE0
#define B64_ERROR 0xFF
#define B64_NOT_BASE64(a) (((a) | 0x13) == 0xF3)

static const uint8_t data_ascii2bin[128] = {
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xE0, 0xF0, 0xFF,
    0xFF, 0xF1, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xE0, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x3E, 0xFF, 0xF2, 0xFF, 0x3F,
    0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
    0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12,
    0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24,
    0x25, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F, 0x30,
    0x31, 0x32, 0x33, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
};

static uint8_t conv_ascii2bin(uint8_t a) {
  if (a >= 128) {
    return 0xFF;
  }
  return data_ascii2bin[a];
}

void EVP_EncodeInit(EVP_ENCODE_CTX *ctx) {
  ctx->length = 48;
  ctx->num = 0;
  ctx->line_num = 0;
}

void EVP_EncodeUpdate(EVP_ENCODE_CTX *ctx, uint8_t *out, int *out_len,
                      const uint8_t *in, size_t in_len) {
  unsigned i, j;
  unsigned total = 0;

  *out_len = 0;
  if (in_len == 0) {
    return;
  }

  assert(ctx->length <= sizeof(ctx->enc_data));

  if (ctx->num + in_len < ctx->length) {
    memcpy(&ctx->enc_data[ctx->num], in, in_len);
    ctx->num += in_len;
    return;
  }
  if (ctx->num != 0) {
    i = ctx->length - ctx->num;
    memcpy(&ctx->enc_data[ctx->num], in, i);
    in += i;
    in_len -= i;
    j = EVP_EncodeBlock(out, ctx->enc_data, ctx->length);
    ctx->num = 0;
    out += j;
    *(out++) = '\n';
    *out = '\0';
    total = j + 1;
  }
  while (in_len >= ctx->length) {
    j = EVP_EncodeBlock(out, in, ctx->length);
    in += ctx->length;
    in_len -= ctx->length;
    out += j;
    *(out++) = '\n';
    *out = '\0';
    total += j + 1;
  }
  if (in_len != 0) {
    memcpy(&ctx->enc_data[0], in, in_len);
  }
  ctx->num = in_len;
  *out_len = total;
}

void EVP_EncodeFinal(EVP_ENCODE_CTX *ctx, uint8_t *out, int *out_len) {
  unsigned ret = 0;

  if (ctx->num != 0) {
    ret = EVP_EncodeBlock(out, ctx->enc_data, ctx->num);
    out[ret++] = '\n';
    out[ret] = '\0';
    ctx->num = 0;
  }
  *out_len = ret;
}

size_t EVP_EncodeBlock(uint8_t *dst, const uint8_t *src, size_t src_len) {
  uint32_t l;
  size_t remaining = src_len, ret = 0;

  while (remaining) {
    if (remaining >= 3) {
      l = (((uint32_t)src[0]) << 16L) | (((uint32_t)src[1]) << 8L) | src[2];
      *(dst++) = conv_bin2ascii(l >> 18L);
      *(dst++) = conv_bin2ascii(l >> 12L);
      *(dst++) = conv_bin2ascii(l >> 6L);
      *(dst++) = conv_bin2ascii(l);
      remaining -= 3;
    } else {
      l = ((uint32_t)src[0]) << 16L;
      if (remaining == 2) {
        l |= ((uint32_t)src[1] << 8L);
      }

      *(dst++) = conv_bin2ascii(l >> 18L);
      *(dst++) = conv_bin2ascii(l >> 12L);
      *(dst++) = (remaining == 1) ? '=' : conv_bin2ascii(l >> 6L);
      *(dst++) = '=';
      remaining = 0;
    }
    ret += 4;
    src += 3;
  }

  *dst = '\0';
  return ret;
}

int EVP_DecodedLength(size_t *out_len, size_t len) {
  if (len % 4 != 0) {
    return 0;
  }
  *out_len = (len / 4) * 3;
  return 1;
}

int EVP_DecodeBase64(uint8_t *out, size_t *out_len, size_t max_out,
                     const uint8_t *in, size_t in_len) {
  uint8_t a, b, c, d;
  size_t pad_len = 0, len = 0, max_len, i;
  uint32_t l;

  if (!EVP_DecodedLength(&max_len, in_len) || max_out < max_len) {
    return 0;
  }

  for (i = 0; i < in_len; i += 4) {
    a = conv_ascii2bin(*(in++));
    b = conv_ascii2bin(*(in++));
    if (i + 4 == in_len && in[1] == '=') {
        if (in[0] == '=') {
          pad_len = 2;
        } else {
          pad_len = 1;
        }
    }
    if (pad_len < 2) {
      c = conv_ascii2bin(*(in++));
    } else {
      c = 0;
    }
    if (pad_len < 1) {
      d = conv_ascii2bin(*(in++));
    } else {
      d = 0;
    }
    if ((a & 0x80) || (b & 0x80) || (c & 0x80) || (d & 0x80)) {
      return 0;
    }
    l = ((((uint32_t)a) << 18L) | (((uint32_t)b) << 12L) |
         (((uint32_t)c) << 6L) | (((uint32_t)d)));
    *(out++) = (uint8_t)(l >> 16L) & 0xff;
    if (pad_len < 2) {
      *(out++) = (uint8_t)(l >> 8L) & 0xff;
    }
    if (pad_len < 1) {
      *(out++) = (uint8_t)(l) & 0xff;
    }
    len += 3 - pad_len;
  }
  *out_len = len;
  return 1;
}

void EVP_DecodeInit(EVP_ENCODE_CTX *ctx) {
  ctx->length = 30;
  ctx->num = 0;
  ctx->line_num = 0;
  ctx->expect_nl = 0;
}

int EVP_DecodeUpdate(EVP_ENCODE_CTX *ctx, uint8_t *out, int *out_len,
                     const uint8_t *in, size_t in_len) {
  int seof = -1, eof = 0, rv = -1, v, tmp, exp_nl;
  uint8_t *d;
  unsigned i, n, ln, ret = 0;

  n = ctx->num;
  d = ctx->enc_data;
  ln = ctx->line_num;
  exp_nl = ctx->expect_nl;

  /* last line of input. */
  if (in_len == 0 || (n == 0 && conv_ascii2bin(in[0]) == B64_EOF)) {
    rv = 0;
    goto end;
  }

  /* We parse the input data */
  for (i = 0; i < in_len; i++) {
    /* If the current line is > 80 characters, scream alot */
    if (ln >= 80) {
      rv = -1;
      goto end;
    }

    /* Get char and put it into the buffer */
    tmp = *(in++);
    v = conv_ascii2bin(tmp);
    /* only save the good data :-) */
    if (!B64_NOT_BASE64(v)) {
      assert(n < sizeof(ctx->enc_data));
      d[n++] = tmp;
      ln++;
    } else if (v == B64_ERROR) {
      rv = -1;
      goto end;
    }

    /* have we seen a '=' which is 'definitly' the last
     * input line.  seof will point to the character that
     * holds it. and eof will hold how many characters to
     * chop off. */
    if (tmp == '=') {
      if (seof == -1) {
        seof = n;
      }
      eof++;
      if (eof > 2) {
        /* There are, at most, two equals signs at the end of base64 data. */
        rv = -1;
        goto end;
      }
    }

    if (v == B64_CR) {
      ln = 0;
      if (exp_nl) {
        continue;
      }
    }

    /* eoln */
    if (v == B64_EOLN) {
      ln = 0;
      if (exp_nl) {
        exp_nl = 0;
        continue;
      }
    }
    exp_nl = 0;

    /* If we are at the end of input and it looks like a
     * line, process it. */
    if ((i + 1) == in_len && (((n & 3) == 0) || eof)) {
      v = B64_EOF;
      /* In case things were given us in really small
         records (so two '=' were given in separate
         updates), eof may contain the incorrect number
         of ending bytes to skip, so let's redo the count */
      eof = 0;
      if (d[n - 1] == '=') {
        eof++;
      }
      if (d[n - 2] == '=') {
        eof++;
      }
      /* There will never be more than two '=' */
    }

    if ((v == B64_EOF && (n & 3) == 0) || n >= 64) {
      /* This is needed to work correctly on 64 byte input
       * lines.  We process the line and then need to
       * accept the '\n' */
      if (v != B64_EOF && n >= 64) {
        exp_nl = 1;
      }
      if (n > 0) {
        /* TODO(davidben): Switch this to EVP_DecodeBase64. */
        v = EVP_DecodeBlock(out, d, n);
        n = 0;
        if (v < 0) {
          rv = 0;
          goto end;
        }
        if (eof > v) {
          rv = -1;
          goto end;
        }
        ret += (v - eof);
      } else {
        eof = 1;
        v = 0;
      }

      /* This is the case where we have had a short
       * but valid input line */
      if (v < (int)ctx->length && eof) {
        rv = 0;
        goto end;
      } else {
        ctx->length = v;
      }

      if (seof >= 0) {
        rv = 0;
        goto end;
      }
      out += v;
    }
  }
  rv = 1;

end:
  *out_len = ret;
  ctx->num = n;
  ctx->line_num = ln;
  ctx->expect_nl = exp_nl;
  return rv;
}

int EVP_DecodeFinal(EVP_ENCODE_CTX *ctx, uint8_t *out, int *outl) {
  int i;

  *outl = 0;
  if (ctx->num != 0) {
    /* TODO(davidben): Switch this to EVP_DecodeBase64. */
    i = EVP_DecodeBlock(out, ctx->enc_data, ctx->num);
    if (i < 0) {
      return -1;
    }
    ctx->num = 0;
    *outl = i;
    return 1;
  } else {
    return 1;
  }
}

int EVP_DecodeBlock(uint8_t *dst, const uint8_t *src, size_t src_len) {
  size_t dst_len;

  /* trim white space from the start of the line. */
  while (conv_ascii2bin(*src) == B64_WS && src_len > 0) {
    src++;
    src_len--;
  }

  /* strip off stuff at the end of the line
   * ascii2bin values B64_WS, B64_EOLN, B64_EOLN and B64_EOF */
  while (src_len > 3 && B64_NOT_BASE64(conv_ascii2bin(src[src_len - 1]))) {
    src_len--;
  }

  if (!EVP_DecodedLength(&dst_len, src_len) || dst_len > INT_MAX) {
    return -1;
  }
  if (!EVP_DecodeBase64(dst, &dst_len, dst_len, src, src_len)) {
    return -1;
  }

  /* EVP_DecodeBlock does not take padding into account, so put the
   * NULs back in... so the caller can strip them back out. */
  while (dst_len % 3 != 0) {
    dst[dst_len++] = '\0';
  }
  assert(dst_len <= INT_MAX);

  return dst_len;
}

int EVP_EncodedLength(size_t *out_len, size_t len) {
  if (len + 2 < len) {
    return 0;
  }
  len += 2;
  len /= 3;
  if (((len << 2) >> 2) != len) {
    return 0;
  }
  len <<= 2;
  if (len + 1 < len) {
    return 0;
  }
  len++;
  *out_len = len;
  return 1;
}

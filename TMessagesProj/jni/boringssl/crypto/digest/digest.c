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

#include <openssl/digest.h>

#include <assert.h>
#include <string.h>

#include <openssl/err.h>
#include <openssl/obj.h>
#include <openssl/mem.h>

#include "internal.h"


int EVP_MD_type(const EVP_MD *md) { return md->type; }

uint32_t EVP_MD_flags(const EVP_MD *md) { return md->flags; }

size_t EVP_MD_size(const EVP_MD *md) { return md->md_size; }

size_t EVP_MD_block_size(const EVP_MD *md) { return md->block_size; }


void EVP_MD_CTX_init(EVP_MD_CTX *ctx) { memset(ctx, 0, sizeof(EVP_MD_CTX)); }

EVP_MD_CTX *EVP_MD_CTX_create(void) {
  EVP_MD_CTX *ctx = OPENSSL_malloc(sizeof(EVP_MD_CTX));

  if (ctx) {
    EVP_MD_CTX_init(ctx);
  }

  return ctx;
}

int EVP_MD_CTX_cleanup(EVP_MD_CTX *ctx) {
  if (ctx->digest && ctx->digest->ctx_size && ctx->md_data) {
    OPENSSL_cleanse(ctx->md_data, ctx->digest->ctx_size);
    OPENSSL_free(ctx->md_data);
  }

  assert(ctx->pctx == NULL || ctx->pctx_ops != NULL);
  if (ctx->pctx_ops) {
    ctx->pctx_ops->free(ctx->pctx);
  }

  EVP_MD_CTX_init(ctx);

  return 1;
}

void EVP_MD_CTX_destroy(EVP_MD_CTX *ctx) {
  if (!ctx) {
    return;
  }

  EVP_MD_CTX_cleanup(ctx);
  OPENSSL_free(ctx);
}

int EVP_MD_CTX_copy_ex(EVP_MD_CTX *out, const EVP_MD_CTX *in) {
  uint8_t *tmp_buf = NULL;

  if (in == NULL || in->digest == NULL) {
    OPENSSL_PUT_ERROR(DIGEST, DIGEST_R_INPUT_NOT_INITIALIZED);
    return 0;
  }

  if (out->digest == in->digest) {
    /* |md_data| will be the correct size in this case so it's removed from
     * |out| at this point so that |EVP_MD_CTX_cleanup| doesn't free it and
     * then it's reused. */
    tmp_buf = out->md_data;
    out->md_data = NULL;
  }

  EVP_MD_CTX_cleanup(out);
  memcpy(out, in, sizeof(EVP_MD_CTX));

  if (in->md_data && in->digest->ctx_size) {
    if (tmp_buf) {
      out->md_data = tmp_buf;
    } else {
      out->md_data = OPENSSL_malloc(in->digest->ctx_size);
      if (!out->md_data) {
        OPENSSL_PUT_ERROR(DIGEST, ERR_R_MALLOC_FAILURE);
        return 0;
      }
    }
    memcpy(out->md_data, in->md_data, in->digest->ctx_size);
  }

  assert(in->pctx == NULL || in->pctx_ops != NULL);
  if (in->pctx && in->pctx_ops) {
    out->pctx = in->pctx_ops->dup(in->pctx);
    if (!out->pctx) {
      EVP_MD_CTX_cleanup(out);
      return 0;
    }
  }

  return 1;
}

int EVP_MD_CTX_copy(EVP_MD_CTX *out, const EVP_MD_CTX *in) {
  EVP_MD_CTX_init(out);
  return EVP_MD_CTX_copy_ex(out, in);
}

int EVP_DigestInit_ex(EVP_MD_CTX *ctx, const EVP_MD *type, ENGINE *engine) {
  if (ctx->digest != type) {
    if (ctx->digest && ctx->digest->ctx_size > 0) {
      OPENSSL_free(ctx->md_data);
    }
    ctx->digest = type;
    if (type->ctx_size > 0) {
      ctx->md_data = OPENSSL_malloc(type->ctx_size);
      if (ctx->md_data == NULL) {
        OPENSSL_PUT_ERROR(DIGEST, ERR_R_MALLOC_FAILURE);
        return 0;
      }
    }
  }

  assert(ctx->pctx == NULL || ctx->pctx_ops != NULL);

  ctx->digest->init(ctx);
  return 1;
}

int EVP_DigestInit(EVP_MD_CTX *ctx, const EVP_MD *type) {
  EVP_MD_CTX_init(ctx);
  return EVP_DigestInit_ex(ctx, type, NULL);
}

int EVP_DigestUpdate(EVP_MD_CTX *ctx, const void *data, size_t len) {
  ctx->digest->update(ctx, data, len);
  return 1;
}

int EVP_DigestFinal_ex(EVP_MD_CTX *ctx, uint8_t *md_out, unsigned int *size) {
  assert(ctx->digest->md_size <= EVP_MAX_MD_SIZE);
  ctx->digest->final(ctx, md_out);
  if (size != NULL) {
    *size = ctx->digest->md_size;
  }
  OPENSSL_cleanse(ctx->md_data, ctx->digest->ctx_size);
  return 1;
}

int EVP_DigestFinal(EVP_MD_CTX *ctx, uint8_t *md, unsigned int *size) {
  EVP_DigestFinal_ex(ctx, md, size);
  EVP_MD_CTX_cleanup(ctx);
  return 1;
}

int EVP_Digest(const void *data, size_t count, uint8_t *out_md,
               unsigned int *out_size, const EVP_MD *type, ENGINE *impl) {
  EVP_MD_CTX ctx;
  int ret;

  EVP_MD_CTX_init(&ctx);
  ret = EVP_DigestInit_ex(&ctx, type, impl) &&
        EVP_DigestUpdate(&ctx, data, count) &&
        EVP_DigestFinal_ex(&ctx, out_md, out_size);
  EVP_MD_CTX_cleanup(&ctx);

  return ret;
}


const EVP_MD *EVP_MD_CTX_md(const EVP_MD_CTX *ctx) {
  if (ctx == NULL) {
    return NULL;
  }
  return ctx->digest;
}

unsigned EVP_MD_CTX_size(const EVP_MD_CTX *ctx) {
  return EVP_MD_size(EVP_MD_CTX_md(ctx));
}

unsigned EVP_MD_CTX_block_size(const EVP_MD_CTX *ctx) {
  return EVP_MD_block_size(EVP_MD_CTX_md(ctx));
}

int EVP_MD_CTX_type(const EVP_MD_CTX *ctx) {
  return EVP_MD_type(EVP_MD_CTX_md(ctx));
}

int EVP_add_digest(const EVP_MD *digest) {
  return 1;
}

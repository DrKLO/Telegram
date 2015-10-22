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

#include <openssl/dh.h>

#include <string.h>

#include <openssl/bn.h>
#include <openssl/buf.h>
#include <openssl/err.h>
#include <openssl/ex_data.h>
#include <openssl/mem.h>
#include <openssl/thread.h>

#include "internal.h"
#include "../internal.h"


extern const DH_METHOD DH_default_method;

static CRYPTO_EX_DATA_CLASS g_ex_data_class = CRYPTO_EX_DATA_CLASS_INIT;

DH *DH_new(void) { return DH_new_method(NULL); }

DH *DH_new_method(const ENGINE *engine) {
  DH *dh = (DH *)OPENSSL_malloc(sizeof(DH));
  if (dh == NULL) {
    OPENSSL_PUT_ERROR(DH, ERR_R_MALLOC_FAILURE);
    return NULL;
  }

  memset(dh, 0, sizeof(DH));

  if (engine) {
    dh->meth = ENGINE_get_DH_method(engine);
  }

  if (dh->meth == NULL) {
    dh->meth = (DH_METHOD*) &DH_default_method;
  }
  METHOD_ref(dh->meth);

  CRYPTO_MUTEX_init(&dh->method_mont_p_lock);

  dh->references = 1;
  if (!CRYPTO_new_ex_data(&g_ex_data_class, dh, &dh->ex_data)) {
    OPENSSL_free(dh);
    return NULL;
  }

  if (dh->meth->init && !dh->meth->init(dh)) {
    CRYPTO_free_ex_data(&g_ex_data_class, dh, &dh->ex_data);
    METHOD_unref(dh->meth);
    OPENSSL_free(dh);
    return NULL;
  }

  return dh;
}

void DH_free(DH *dh) {
  if (dh == NULL) {
    return;
  }

  if (!CRYPTO_refcount_dec_and_test_zero(&dh->references)) {
    return;
  }

  if (dh->meth->finish) {
    dh->meth->finish(dh);
  }
  METHOD_unref(dh->meth);

  CRYPTO_free_ex_data(&g_ex_data_class, dh, &dh->ex_data);

  if (dh->method_mont_p) BN_MONT_CTX_free(dh->method_mont_p);
  if (dh->p != NULL) BN_clear_free(dh->p);
  if (dh->g != NULL) BN_clear_free(dh->g);
  if (dh->q != NULL) BN_clear_free(dh->q);
  if (dh->j != NULL) BN_clear_free(dh->j);
  if (dh->seed) OPENSSL_free(dh->seed);
  if (dh->counter != NULL) BN_clear_free(dh->counter);
  if (dh->pub_key != NULL) BN_clear_free(dh->pub_key);
  if (dh->priv_key != NULL) BN_clear_free(dh->priv_key);
  CRYPTO_MUTEX_cleanup(&dh->method_mont_p_lock);

  OPENSSL_free(dh);
}

int DH_generate_parameters_ex(DH *dh, int prime_bits, int generator, BN_GENCB *cb) {
  if (dh->meth->generate_parameters) {
    return dh->meth->generate_parameters(dh, prime_bits, generator, cb);
  }
  return DH_default_method.generate_parameters(dh, prime_bits, generator, cb);
}

int DH_generate_key(DH *dh) {
  if (dh->meth->generate_key) {
    return dh->meth->generate_key(dh);
  }
  return DH_default_method.generate_key(dh);
}

int DH_compute_key(unsigned char *out, const BIGNUM *peers_key, DH *dh) {
  if (dh->meth->compute_key) {
    return dh->meth->compute_key(dh, out, peers_key);
  }
  return DH_default_method.compute_key(dh, out, peers_key);
}

int DH_size(const DH *dh) { return BN_num_bytes(dh->p); }

unsigned DH_num_bits(const DH *dh) { return BN_num_bits(dh->p); }

int DH_up_ref(DH *dh) {
  CRYPTO_refcount_inc(&dh->references);
  return 1;
}

static int int_dh_bn_cpy(BIGNUM **dst, const BIGNUM *src) {
  BIGNUM *a = NULL;

  if (src) {
    a = BN_dup(src);
    if (!a) {
      return 0;
    }
  }

  BN_free(*dst);
  *dst = a;
  return 1;
}

static int int_dh_param_copy(DH *to, const DH *from, int is_x942) {
  if (is_x942 == -1) {
    is_x942 = !!from->q;
  }
  if (!int_dh_bn_cpy(&to->p, from->p) ||
      !int_dh_bn_cpy(&to->g, from->g)) {
    return 0;
  }

  if (!is_x942) {
    return 1;
  }

  if (!int_dh_bn_cpy(&to->q, from->q) ||
      !int_dh_bn_cpy(&to->j, from->j)) {
    return 0;
  }

  OPENSSL_free(to->seed);
  to->seed = NULL;
  to->seedlen = 0;

  if (from->seed) {
    to->seed = BUF_memdup(from->seed, from->seedlen);
    if (!to->seed) {
      return 0;
    }
    to->seedlen = from->seedlen;
  }

  return 1;
}

DH *DHparams_dup(const DH *dh) {
  DH *ret = DH_new();
  if (!ret) {
    return NULL;
  }

  if (!int_dh_param_copy(ret, dh, -1)) {
    DH_free(ret);
    return NULL;
  }

  return ret;
}

int DH_get_ex_new_index(long argl, void *argp, CRYPTO_EX_new *new_func,
                        CRYPTO_EX_dup *dup_func, CRYPTO_EX_free *free_func) {
  int index;
  if (!CRYPTO_get_ex_new_index(&g_ex_data_class, &index, argl, argp, new_func,
                               dup_func, free_func)) {
    return -1;
  }
  return index;
}

int DH_set_ex_data(DH *d, int idx, void *arg) {
  return (CRYPTO_set_ex_data(&d->ex_data, idx, arg));
}

void *DH_get_ex_data(DH *d, int idx) {
  return (CRYPTO_get_ex_data(&d->ex_data, idx));
}

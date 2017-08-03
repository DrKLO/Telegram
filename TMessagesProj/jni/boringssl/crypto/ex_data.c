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
 * [including the GNU Public Licence.]
 */
/* ====================================================================
 * Copyright (c) 1998-2001 The OpenSSL Project.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All advertising materials mentioning features or use of this
 *    software must display the following acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit. (http://www.openssl.org/)"
 *
 * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    openssl-core@openssl.org.
 *
 * 5. Products derived from this software may not be called "OpenSSL"
 *    nor may "OpenSSL" appear in their names without prior written
 *    permission of the OpenSSL Project.
 *
 * 6. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit (http://www.openssl.org/)"
 *
 * THIS SOFTWARE IS PROVIDED BY THE OpenSSL PROJECT ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * This product includes cryptographic software written by Eric Young
 * (eay@cryptsoft.com).  This product includes software written by Tim
 * Hudson (tjh@cryptsoft.com). */

#include <openssl/ex_data.h>

#include <assert.h>
#include <string.h>

#include <openssl/crypto.h>
#include <openssl/err.h>
#include <openssl/lhash.h>
#include <openssl/mem.h>
#include <openssl/stack.h>
#include <openssl/thread.h>

#include "internal.h"


struct crypto_ex_data_func_st {
  long argl;  /* Arbitary long */
  void *argp; /* Arbitary void pointer */
  CRYPTO_EX_new *new_func;
  CRYPTO_EX_free *free_func;
  CRYPTO_EX_dup *dup_func;
};

int CRYPTO_get_ex_new_index(CRYPTO_EX_DATA_CLASS *ex_data_class, int *out_index,
                            long argl, void *argp, CRYPTO_EX_new *new_func,
                            CRYPTO_EX_dup *dup_func,
                            CRYPTO_EX_free *free_func) {
  CRYPTO_EX_DATA_FUNCS *funcs;
  int ret = 0;

  funcs = OPENSSL_malloc(sizeof(CRYPTO_EX_DATA_FUNCS));
  if (funcs == NULL) {
    OPENSSL_PUT_ERROR(CRYPTO, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  funcs->argl = argl;
  funcs->argp = argp;
  funcs->new_func = new_func;
  funcs->dup_func = dup_func;
  funcs->free_func = free_func;

  CRYPTO_STATIC_MUTEX_lock_write(&ex_data_class->lock);

  if (ex_data_class->meth == NULL) {
    ex_data_class->meth = sk_CRYPTO_EX_DATA_FUNCS_new_null();
  }

  if (ex_data_class->meth == NULL ||
      !sk_CRYPTO_EX_DATA_FUNCS_push(ex_data_class->meth, funcs)) {
    OPENSSL_PUT_ERROR(CRYPTO, ERR_R_MALLOC_FAILURE);
    OPENSSL_free(funcs);
    goto err;
  }

  *out_index = sk_CRYPTO_EX_DATA_FUNCS_num(ex_data_class->meth) - 1 +
               ex_data_class->num_reserved;
  ret = 1;

err:
  CRYPTO_STATIC_MUTEX_unlock(&ex_data_class->lock);
  return ret;
}

int CRYPTO_set_ex_data(CRYPTO_EX_DATA *ad, int index, void *val) {
  int n, i;

  if (ad->sk == NULL) {
    ad->sk = sk_void_new_null();
    if (ad->sk == NULL) {
      OPENSSL_PUT_ERROR(CRYPTO, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  }

  n = sk_void_num(ad->sk);

  /* Add NULL values until the stack is long enough. */
  for (i = n; i <= index; i++) {
    if (!sk_void_push(ad->sk, NULL)) {
      OPENSSL_PUT_ERROR(CRYPTO, ERR_R_MALLOC_FAILURE);
      return 0;
    }
  }

  sk_void_set(ad->sk, index, val);
  return 1;
}

void *CRYPTO_get_ex_data(const CRYPTO_EX_DATA *ad, int idx) {
  if (ad->sk == NULL || idx < 0 || (size_t)idx >= sk_void_num(ad->sk)) {
    return NULL;
  }
  return sk_void_value(ad->sk, idx);
}

/* get_func_pointers takes a copy of the CRYPTO_EX_DATA_FUNCS pointers, if any,
 * for the given class. If there are some pointers, it sets |*out| to point to
 * a fresh stack of them. Otherwise it sets |*out| to NULL. It returns one on
 * success or zero on error. */
static int get_func_pointers(STACK_OF(CRYPTO_EX_DATA_FUNCS) **out,
                             CRYPTO_EX_DATA_CLASS *ex_data_class) {
  size_t n;

  *out = NULL;

  /* CRYPTO_EX_DATA_FUNCS structures are static once set, so we can take a
   * shallow copy of the list under lock and then use the structures without
   * the lock held. */
  CRYPTO_STATIC_MUTEX_lock_read(&ex_data_class->lock);
  n = sk_CRYPTO_EX_DATA_FUNCS_num(ex_data_class->meth);
  if (n > 0) {
    *out = sk_CRYPTO_EX_DATA_FUNCS_dup(ex_data_class->meth);
  }
  CRYPTO_STATIC_MUTEX_unlock(&ex_data_class->lock);

  if (n > 0 && *out == NULL) {
    OPENSSL_PUT_ERROR(CRYPTO, ERR_R_MALLOC_FAILURE);
    return 0;
  }

  return 1;
}

int CRYPTO_new_ex_data(CRYPTO_EX_DATA_CLASS *ex_data_class, void *obj,
                       CRYPTO_EX_DATA *ad) {
  STACK_OF(CRYPTO_EX_DATA_FUNCS) *func_pointers;
  size_t i;

  ad->sk = NULL;

  if (!get_func_pointers(&func_pointers, ex_data_class)) {
    return 0;
  }

  for (i = 0; i < sk_CRYPTO_EX_DATA_FUNCS_num(func_pointers); i++) {
    CRYPTO_EX_DATA_FUNCS *func_pointer =
        sk_CRYPTO_EX_DATA_FUNCS_value(func_pointers, i);
    if (func_pointer->new_func) {
      func_pointer->new_func(obj, NULL, ad, i + ex_data_class->num_reserved,
                             func_pointer->argl, func_pointer->argp);
    }
  }

  sk_CRYPTO_EX_DATA_FUNCS_free(func_pointers);

  return 1;
}

int CRYPTO_dup_ex_data(CRYPTO_EX_DATA_CLASS *ex_data_class, CRYPTO_EX_DATA *to,
                       const CRYPTO_EX_DATA *from) {
  STACK_OF(CRYPTO_EX_DATA_FUNCS) *func_pointers;
  size_t i;

  if (!from->sk) {
    /* In this case, |from| is blank, which is also the initial state of |to|,
     * so there's nothing to do. */
    return 1;
  }

  if (!get_func_pointers(&func_pointers, ex_data_class)) {
    return 0;
  }

  for (i = 0; i < sk_CRYPTO_EX_DATA_FUNCS_num(func_pointers); i++) {
    CRYPTO_EX_DATA_FUNCS *func_pointer =
        sk_CRYPTO_EX_DATA_FUNCS_value(func_pointers, i);
    void *ptr = CRYPTO_get_ex_data(from, i + ex_data_class->num_reserved);
    if (func_pointer->dup_func) {
      func_pointer->dup_func(to, from, &ptr, i + ex_data_class->num_reserved,
                             func_pointer->argl, func_pointer->argp);
    }
    CRYPTO_set_ex_data(to, i + ex_data_class->num_reserved, ptr);
  }

  sk_CRYPTO_EX_DATA_FUNCS_free(func_pointers);

  return 1;
}

void CRYPTO_free_ex_data(CRYPTO_EX_DATA_CLASS *ex_data_class, void *obj,
                         CRYPTO_EX_DATA *ad) {
  STACK_OF(CRYPTO_EX_DATA_FUNCS) *func_pointers;
  size_t i;

  if (!get_func_pointers(&func_pointers, ex_data_class)) {
    return;
  }

  for (i = 0; i < sk_CRYPTO_EX_DATA_FUNCS_num(func_pointers); i++) {
    CRYPTO_EX_DATA_FUNCS *func_pointer =
        sk_CRYPTO_EX_DATA_FUNCS_value(func_pointers, i);
    if (func_pointer->free_func) {
      void *ptr = CRYPTO_get_ex_data(ad, i + ex_data_class->num_reserved);
      func_pointer->free_func(obj, ptr, ad, i + ex_data_class->num_reserved,
                              func_pointer->argl, func_pointer->argp);
    }
  }

  sk_CRYPTO_EX_DATA_FUNCS_free(func_pointers);

  sk_void_free(ad->sk);
  ad->sk = NULL;
}

void CRYPTO_cleanup_all_ex_data(void) {}

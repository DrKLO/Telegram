/* Copyright (c) 2014, Google Inc.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

#include <openssl/engine.h>

#include <string.h>
#include <assert.h>

#include <openssl/dh.h>
#include <openssl/dsa.h>
#include <openssl/ec_key.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/rsa.h>
#include <openssl/thread.h>


struct engine_st {
  DH_METHOD *dh_method;
  DSA_METHOD *dsa_method;
  RSA_METHOD *rsa_method;
  ECDSA_METHOD *ecdsa_method;
};

ENGINE *ENGINE_new(void) {
  ENGINE *engine = OPENSSL_malloc(sizeof(ENGINE));
  if (engine == NULL) {
    return NULL;
  }

  memset(engine, 0, sizeof(ENGINE));
  return engine;
}

void ENGINE_free(ENGINE *engine) {
  /* Methods are currently required to be static so are not unref'ed. */
  OPENSSL_free(engine);
}

/* set_method takes a pointer to a method and its given size and sets
 * |*out_member| to point to it. This function might want to be extended in the
 * future to support making a copy of the method so that a stable ABI for
 * ENGINEs can be supported. But, for the moment, all *_METHODS must be
 * static. */
static int set_method(void **out_member, const void *method, size_t method_size,
                      size_t compiled_size) {
  const struct openssl_method_common_st *common = method;
  if (method_size != compiled_size || !common->is_static) {
    return 0;
  }

  *out_member = (void*) method;
  return 1;
}

int ENGINE_set_DH_method(ENGINE *engine, const DH_METHOD *method,
                         size_t method_size) {
  return set_method((void **)&engine->dh_method, method, method_size,
                    sizeof(DH_METHOD));
}

DH_METHOD *ENGINE_get_DH_method(const ENGINE *engine) {
  return engine->dh_method;
}

int ENGINE_set_DSA_method(ENGINE *engine, const DSA_METHOD *method,
                         size_t method_size) {
  return set_method((void **)&engine->dsa_method, method, method_size,
                    sizeof(DSA_METHOD));
}

DSA_METHOD *ENGINE_get_DSA_method(const ENGINE *engine) {
  return engine->dsa_method;
}

int ENGINE_set_RSA_method(ENGINE *engine, const RSA_METHOD *method,
                         size_t method_size) {
  return set_method((void **)&engine->rsa_method, method, method_size,
                    sizeof(RSA_METHOD));
}

RSA_METHOD *ENGINE_get_RSA_method(const ENGINE *engine) {
  return engine->rsa_method;
}

int ENGINE_set_ECDSA_method(ENGINE *engine, const ECDSA_METHOD *method,
                            size_t method_size) {
  return set_method((void **)&engine->ecdsa_method, method, method_size,
                    sizeof(ECDSA_METHOD));
}

ECDSA_METHOD *ENGINE_get_ECDSA_method(const ENGINE *engine) {
  return engine->ecdsa_method;
}

void METHOD_ref(void *method_in) {
  assert(((struct openssl_method_common_st*) method_in)->is_static);
}

void METHOD_unref(void *method_in) {
  struct openssl_method_common_st *method = method_in;

  if (method == NULL) {
    return;
  }
  assert(method->is_static);
}

OPENSSL_DECLARE_ERROR_REASON(ENGINE, OPERATION_NOT_SUPPORTED);

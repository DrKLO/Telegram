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

#include <openssl/stack.h>

#include <string.h>

#include <openssl/mem.h>

/* kMinSize is the number of pointers that will be initially allocated in a new
 * stack. */
static const size_t kMinSize = 4;

_STACK *sk_new(stack_cmp_func comp) {
  _STACK *ret;

  ret = OPENSSL_malloc(sizeof(_STACK));
  if (ret == NULL) {
    goto err;
  }
  memset(ret, 0, sizeof(_STACK));

  ret->data = OPENSSL_malloc(sizeof(void *) * kMinSize);
  if (ret->data == NULL) {
    goto err;
  }

  memset(ret->data, 0, sizeof(void *) * kMinSize);

  ret->comp = comp;
  ret->num_alloc = kMinSize;

  return ret;

err:
  OPENSSL_free(ret);
  return NULL;
}

_STACK *sk_new_null(void) { return sk_new(NULL); }

size_t sk_num(const _STACK *sk) {
  if (sk == NULL) {
    return 0;
  }
  return sk->num;
}

void sk_zero(_STACK *sk) {
  if (sk == NULL || sk->num == 0) {
    return;
  }
  memset(sk->data, 0, sizeof(void*) * sk->num);
  sk->num = 0;
  sk->sorted = 0;
}

void *sk_value(const _STACK *sk, size_t i) {
  if (!sk || i >= sk->num) {
    return NULL;
  }
  return sk->data[i];
}

void *sk_set(_STACK *sk, size_t i, void *value) {
  if (!sk || i >= sk->num) {
    return NULL;
  }
  return sk->data[i] = value;
}

void sk_free(_STACK *sk) {
  if (sk == NULL) {
    return;
  }
  OPENSSL_free(sk->data);
  OPENSSL_free(sk);
}

void sk_pop_free(_STACK *sk, void (*func)(void *)) {
  size_t i;

  if (sk == NULL) {
    return;
  }

  for (i = 0; i < sk->num; i++) {
    if (sk->data[i] != NULL) {
      func(sk->data[i]);
    }
  }
  sk_free(sk);
}

size_t sk_insert(_STACK *sk, void *p, size_t where) {
  if (sk == NULL) {
    return 0;
  }

  if (sk->num_alloc <= sk->num + 1) {
    /* Attempt to double the size of the array. */
    size_t new_alloc = sk->num_alloc << 1;
    size_t alloc_size = new_alloc * sizeof(void *);
    void **data;

    /* If the doubling overflowed, try to increment. */
    if (new_alloc < sk->num_alloc || alloc_size / sizeof(void *) != new_alloc) {
      new_alloc = sk->num_alloc + 1;
      alloc_size = new_alloc * sizeof(void *);
    }

    /* If the increment also overflowed, fail. */
    if (new_alloc < sk->num_alloc || alloc_size / sizeof(void *) != new_alloc) {
      return 0;
    }

    data = OPENSSL_realloc(sk->data, alloc_size);
    if (data == NULL) {
      return 0;
    }

    sk->data = data;
    sk->num_alloc = new_alloc;
  }

  if (where >= sk->num) {
    sk->data[sk->num] = p;
  } else {
    memmove(&sk->data[where + 1], &sk->data[where],
            sizeof(void *) * (sk->num - where));
    sk->data[where] = p;
  }

  sk->num++;
  sk->sorted = 0;

  return sk->num;
}

void *sk_delete(_STACK *sk, size_t where) {
  void *ret;

  if (!sk || where >= sk->num) {
    return NULL;
  }

  ret = sk->data[where];

  if (where != sk->num - 1) {
    memmove(&sk->data[where], &sk->data[where + 1],
            sizeof(void *) * (sk->num - where - 1));
  }

  sk->num--;
  return ret;
}

void *sk_delete_ptr(_STACK *sk, void *p) {
  size_t i;

  if (sk == NULL) {
    return NULL;
  }

  for (i = 0; i < sk->num; i++) {
    if (sk->data[i] == p) {
      return sk_delete(sk, i);
    }
  }

  return NULL;
}

int sk_find(_STACK *sk, size_t *out_index, void *p) {
  const void *const *r;
  size_t i;
  int (*comp_func)(const void *,const void *);

  if (sk == NULL) {
    return 0;
  }

  if (sk->comp == NULL) {
    /* Use pointer equality when no comparison function has been set. */
    for (i = 0; i < sk->num; i++) {
      if (sk->data[i] == p) {
        if (out_index) {
          *out_index = i;
        }
        return 1;
      }
    }
    return 0;
  }

  if (p == NULL) {
    return 0;
  }

  sk_sort(sk);

  /* sk->comp is a function that takes pointers to pointers to elements, but
   * qsort and bsearch take a comparison function that just takes pointers to
   * elements. However, since we're passing an array of pointers to
   * qsort/bsearch, we can just cast the comparison function and everything
   * works. */
  comp_func=(int (*)(const void *,const void *))(sk->comp);
  r = bsearch(&p, sk->data, sk->num, sizeof(void *), comp_func);
  if (r == NULL) {
    return 0;
  }
  i = ((void **)r) - sk->data;
  /* This function always returns the first result. */
  while (i > 0 && sk->comp((const void**) &p, (const void**) &sk->data[i-1]) == 0) {
    i--;
  }
  if (out_index) {
    *out_index = i;
  }
  return 1;
}

void *sk_shift(_STACK *sk) {
  if (sk == NULL) {
    return NULL;
  }
  if (sk->num == 0) {
    return NULL;
  }
  return sk_delete(sk, 0);
}

size_t sk_push(_STACK *sk, void *p) { return (sk_insert(sk, p, sk->num)); }

void *sk_pop(_STACK *sk) {
  if (sk == NULL) {
    return NULL;
  }
  if (sk->num == 0) {
    return NULL;
  }
  return sk_delete(sk, sk->num - 1);
}

_STACK *sk_dup(const _STACK *sk) {
  _STACK *ret;
  void **s;

  if (sk == NULL) {
    return NULL;
  }

  ret = sk_new(sk->comp);
  if (ret == NULL) {
    goto err;
  }

  s = (void **)OPENSSL_realloc(ret->data, sizeof(void *) * sk->num_alloc);
  if (s == NULL) {
    goto err;
  }
  ret->data = s;

  ret->num = sk->num;
  memcpy(ret->data, sk->data, sizeof(void *) * sk->num);
  ret->sorted = sk->sorted;
  ret->num_alloc = sk->num_alloc;
  ret->comp = sk->comp;
  return ret;

err:
  sk_free(ret);
  return NULL;
}

void sk_sort(_STACK *sk) {
  int (*comp_func)(const void *,const void *);

  if (sk == NULL || sk->sorted) {
    return;
  }

  /* See the comment in sk_find about this cast. */
  comp_func = (int (*)(const void *, const void *))(sk->comp);
  qsort(sk->data, sk->num, sizeof(void *), comp_func);
  sk->sorted = 1;
}

int sk_is_sorted(const _STACK *sk) {
  if (!sk) {
    return 1;
  }
  return sk->sorted;
}

stack_cmp_func sk_set_cmp_func(_STACK *sk, stack_cmp_func comp) {
  stack_cmp_func old = sk->comp;

  if (sk->comp != comp) {
    sk->sorted = 0;
  }
  sk->comp = comp;

  return old;
}

_STACK *sk_deep_copy(const _STACK *sk, void *(*copy_func)(void *),
                     void (*free_func)(void *)) {
  _STACK *ret = sk_dup(sk);
  if (ret == NULL) {
    return NULL;
  }

  size_t i;
  for (i = 0; i < ret->num; i++) {
    if (ret->data[i] == NULL) {
      continue;
    }
    ret->data[i] = copy_func(ret->data[i]);
    if (ret->data[i] == NULL) {
      size_t j;
      for (j = 0; j < i; j++) {
        if (ret->data[j] != NULL) {
          free_func(ret->data[j]);
        }
      }
      sk_free(ret);
      return NULL;
    }
  }

  return ret;
}

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

#include <assert.h>
#include <string.h>

#include <openssl/mem.h>

#include "../internal.h"


// kMinSize is the number of pointers that will be initially allocated in a new
// stack.
static const size_t kMinSize = 4;

_STACK *sk_new(stack_cmp_func comp) {
  _STACK *ret;

  ret = OPENSSL_malloc(sizeof(_STACK));
  if (ret == NULL) {
    goto err;
  }
  OPENSSL_memset(ret, 0, sizeof(_STACK));

  ret->data = OPENSSL_malloc(sizeof(void *) * kMinSize);
  if (ret->data == NULL) {
    goto err;
  }

  OPENSSL_memset(ret->data, 0, sizeof(void *) * kMinSize);

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
  OPENSSL_memset(sk->data, 0, sizeof(void*) * sk->num);
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

void sk_pop_free_ex(_STACK *sk, void (*call_free_func)(stack_free_func, void *),
                    stack_free_func free_func) {
  if (sk == NULL) {
    return;
  }

  for (size_t i = 0; i < sk->num; i++) {
    if (sk->data[i] != NULL) {
      call_free_func(free_func, sk->data[i]);
    }
  }
  sk_free(sk);
}

// Historically, |sk_pop_free| called the function as |stack_free_func|
// directly. This is undefined in C. Some callers called |sk_pop_free| directly,
// so we must maintain a compatibility version for now.
static void call_free_func_legacy(stack_free_func func, void *ptr) {
  func(ptr);
}

void sk_pop_free(_STACK *sk, stack_free_func free_func) {
  sk_pop_free_ex(sk, call_free_func_legacy, free_func);
}

size_t sk_insert(_STACK *sk, void *p, size_t where) {
  if (sk == NULL) {
    return 0;
  }

  if (sk->num_alloc <= sk->num + 1) {
    // Attempt to double the size of the array.
    size_t new_alloc = sk->num_alloc << 1;
    size_t alloc_size = new_alloc * sizeof(void *);
    void **data;

    // If the doubling overflowed, try to increment.
    if (new_alloc < sk->num_alloc || alloc_size / sizeof(void *) != new_alloc) {
      new_alloc = sk->num_alloc + 1;
      alloc_size = new_alloc * sizeof(void *);
    }

    // If the increment also overflowed, fail.
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
    OPENSSL_memmove(&sk->data[where + 1], &sk->data[where],
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
    OPENSSL_memmove(&sk->data[where], &sk->data[where + 1],
            sizeof(void *) * (sk->num - where - 1));
  }

  sk->num--;
  return ret;
}

void *sk_delete_ptr(_STACK *sk, const void *p) {
  if (sk == NULL) {
    return NULL;
  }

  for (size_t i = 0; i < sk->num; i++) {
    if (sk->data[i] == p) {
      return sk_delete(sk, i);
    }
  }

  return NULL;
}

int sk_find(const _STACK *sk, size_t *out_index, const void *p,
            int (*call_cmp_func)(stack_cmp_func, const void **,
                                 const void **)) {
  if (sk == NULL) {
    return 0;
  }

  if (sk->comp == NULL) {
    // Use pointer equality when no comparison function has been set.
    for (size_t i = 0; i < sk->num; i++) {
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

  if (!sk_is_sorted(sk)) {
    for (size_t i = 0; i < sk->num; i++) {
      const void *elem = sk->data[i];
      if (call_cmp_func(sk->comp, &p, &elem) == 0) {
        if (out_index) {
          *out_index = i;
        }
        return 1;
      }
    }
    return 0;
  }

  // The stack is sorted, so binary search to find the element.
  //
  // |lo| and |hi| maintain a half-open interval of where the answer may be. All
  // indices such that |lo <= idx < hi| are candidates.
  size_t lo = 0, hi = sk->num;
  while (lo < hi) {
    // Bias |mid| towards |lo|. See the |r == 0| case below.
    size_t mid = lo + (hi - lo - 1) / 2;
    assert(lo <= mid && mid < hi);
    const void *elem = sk->data[mid];
    int r = call_cmp_func(sk->comp, &p, &elem);
    if (r > 0) {
      lo = mid + 1;  // |mid| is too low.
    } else if (r < 0) {
      hi = mid;  // |mid| is too high.
    } else {
      // |mid| matches. However, this function returns the earliest match, so we
      // can only return if the range has size one.
      if (hi - lo == 1) {
        if (out_index != NULL) {
          *out_index = mid;
        }
        return 1;
      }
      // The sample is biased towards |lo|. |mid| can only be |hi - 1| if
      // |hi - lo| was one, so this makes forward progress.
      assert(mid + 1 < hi);
      hi = mid + 1;
    }
  }

  assert(lo == hi);
  return 0;  // Not found.
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
  OPENSSL_memcpy(ret->data, sk->data, sizeof(void *) * sk->num);
  ret->sorted = sk->sorted;
  ret->num_alloc = sk->num_alloc;
  ret->comp = sk->comp;
  return ret;

err:
  sk_free(ret);
  return NULL;
}

void sk_sort(_STACK *sk) {
  if (sk == NULL || sk->comp == NULL || sk->sorted) {
    return;
  }

  // sk->comp is a function that takes pointers to pointers to elements, but
  // qsort take a comparison function that just takes pointers to elements.
  // However, since we're passing an array of pointers to qsort, we can just
  // cast the comparison function and everything works.
  //
  // TODO(davidben): This is undefined behavior, but the call is in libc so,
  // e.g., CFI does not notice. Unfortunately, |qsort| is missing a void*
  // parameter in its callback and |qsort_s| / |qsort_r| are a mess of
  // incompatibility.
  if (sk->num >= 2) {
    int (*comp_func)(const void *, const void *) =
        (int (*)(const void *, const void *))(sk->comp);
    qsort(sk->data, sk->num, sizeof(void *), comp_func);
  }
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

_STACK *sk_deep_copy(const _STACK *sk,
                     void *(*call_copy_func)(stack_copy_func, void *),
                     stack_copy_func copy_func,
                     void (*call_free_func)(stack_free_func, void *),
                     stack_free_func free_func) {
  _STACK *ret = sk_dup(sk);
  if (ret == NULL) {
    return NULL;
  }

  for (size_t i = 0; i < ret->num; i++) {
    if (ret->data[i] == NULL) {
      continue;
    }
    ret->data[i] = call_copy_func(copy_func, ret->data[i]);
    if (ret->data[i] == NULL) {
      for (size_t j = 0; j < i; j++) {
        if (ret->data[j] != NULL) {
          call_free_func(free_func, ret->data[j]);
        }
      }
      sk_free(ret);
      return NULL;
    }
  }

  return ret;
}

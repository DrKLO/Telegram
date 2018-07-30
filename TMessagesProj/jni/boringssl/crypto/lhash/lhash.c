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

#include <openssl/lhash.h>

#include <assert.h>
#include <limits.h>
#include <string.h>

#include <openssl/mem.h>

#include "../internal.h"


// kMinNumBuckets is the minimum size of the buckets array in an |_LHASH|.
static const size_t kMinNumBuckets = 16;

// kMaxAverageChainLength contains the maximum, average chain length. When the
// average chain length exceeds this value, the hash table will be resized.
static const size_t kMaxAverageChainLength = 2;
static const size_t kMinAverageChainLength = 1;

struct lhash_st {
  // num_items contains the total number of items in the hash table.
  size_t num_items;
  // buckets is an array of |num_buckets| pointers. Each points to the head of
  // a chain of LHASH_ITEM objects that have the same hash value, mod
  // |num_buckets|.
  LHASH_ITEM **buckets;
  // num_buckets contains the length of |buckets|. This value is always >=
  // kMinNumBuckets.
  size_t num_buckets;
  // callback_depth contains the current depth of |lh_doall| or |lh_doall_arg|
  // calls. If non-zero then this suppresses resizing of the |buckets| array,
  // which would otherwise disrupt the iteration.
  unsigned callback_depth;

  lhash_cmp_func comp;
  lhash_hash_func hash;
};

_LHASH *lh_new(lhash_hash_func hash, lhash_cmp_func comp) {
  _LHASH *ret = OPENSSL_malloc(sizeof(_LHASH));
  if (ret == NULL) {
    return NULL;
  }
  OPENSSL_memset(ret, 0, sizeof(_LHASH));

  ret->num_buckets = kMinNumBuckets;
  ret->buckets = OPENSSL_malloc(sizeof(LHASH_ITEM *) * ret->num_buckets);
  if (ret->buckets == NULL) {
    OPENSSL_free(ret);
    return NULL;
  }
  OPENSSL_memset(ret->buckets, 0, sizeof(LHASH_ITEM *) * ret->num_buckets);

  ret->comp = comp;
  ret->hash = hash;
  return ret;
}

void lh_free(_LHASH *lh) {
  if (lh == NULL) {
    return;
  }

  for (size_t i = 0; i < lh->num_buckets; i++) {
    LHASH_ITEM *next;
    for (LHASH_ITEM *n = lh->buckets[i]; n != NULL; n = next) {
      next = n->next;
      OPENSSL_free(n);
    }
  }

  OPENSSL_free(lh->buckets);
  OPENSSL_free(lh);
}

size_t lh_num_items(const _LHASH *lh) { return lh->num_items; }

// get_next_ptr_and_hash returns a pointer to the pointer that points to the
// item equal to |data|. In other words, it searches for an item equal to |data|
// and, if it's at the start of a chain, then it returns a pointer to an
// element of |lh->buckets|, otherwise it returns a pointer to the |next|
// element of the previous item in the chain. If an element equal to |data| is
// not found, it returns a pointer that points to a NULL pointer. If |out_hash|
// is not NULL, then it also puts the hash value of |data| in |*out_hash|.
static LHASH_ITEM **get_next_ptr_and_hash(const _LHASH *lh, uint32_t *out_hash,
                                          const void *data) {
  const uint32_t hash = lh->hash(data);
  LHASH_ITEM *cur, **ret;

  if (out_hash != NULL) {
    *out_hash = hash;
  }

  ret = &lh->buckets[hash % lh->num_buckets];
  for (cur = *ret; cur != NULL; cur = *ret) {
    if (lh->comp(cur->data, data) == 0) {
      break;
    }
    ret = &cur->next;
  }

  return ret;
}

void *lh_retrieve(const _LHASH *lh, const void *data) {
  LHASH_ITEM **next_ptr;

  next_ptr = get_next_ptr_and_hash(lh, NULL, data);

  if (*next_ptr == NULL) {
    return NULL;
  }

  return (*next_ptr)->data;
}

// lh_rebucket allocates a new array of |new_num_buckets| pointers and
// redistributes the existing items into it before making it |lh->buckets| and
// freeing the old array.
static void lh_rebucket(_LHASH *lh, const size_t new_num_buckets) {
  LHASH_ITEM **new_buckets, *cur, *next;
  size_t i, alloc_size;

  alloc_size = sizeof(LHASH_ITEM *) * new_num_buckets;
  if (alloc_size / sizeof(LHASH_ITEM*) != new_num_buckets) {
    return;
  }

  new_buckets = OPENSSL_malloc(alloc_size);
  if (new_buckets == NULL) {
    return;
  }
  OPENSSL_memset(new_buckets, 0, alloc_size);

  for (i = 0; i < lh->num_buckets; i++) {
    for (cur = lh->buckets[i]; cur != NULL; cur = next) {
      const size_t new_bucket = cur->hash % new_num_buckets;
      next = cur->next;
      cur->next = new_buckets[new_bucket];
      new_buckets[new_bucket] = cur;
    }
  }

  OPENSSL_free(lh->buckets);

  lh->num_buckets = new_num_buckets;
  lh->buckets = new_buckets;
}

// lh_maybe_resize resizes the |buckets| array if needed.
static void lh_maybe_resize(_LHASH *lh) {
  size_t avg_chain_length;

  if (lh->callback_depth > 0) {
    // Don't resize the hash if we are currently iterating over it.
    return;
  }

  assert(lh->num_buckets >= kMinNumBuckets);
  avg_chain_length = lh->num_items / lh->num_buckets;

  if (avg_chain_length > kMaxAverageChainLength) {
    const size_t new_num_buckets = lh->num_buckets * 2;

    if (new_num_buckets > lh->num_buckets) {
      lh_rebucket(lh, new_num_buckets);
    }
  } else if (avg_chain_length < kMinAverageChainLength &&
             lh->num_buckets > kMinNumBuckets) {
    size_t new_num_buckets = lh->num_buckets / 2;

    if (new_num_buckets < kMinNumBuckets) {
      new_num_buckets = kMinNumBuckets;
    }

    lh_rebucket(lh, new_num_buckets);
  }
}

int lh_insert(_LHASH *lh, void **old_data, void *data) {
  uint32_t hash;
  LHASH_ITEM **next_ptr, *item;

  *old_data = NULL;
  next_ptr = get_next_ptr_and_hash(lh, &hash, data);


  if (*next_ptr != NULL) {
    // An element equal to |data| already exists in the hash table. It will be
    // replaced.
    *old_data = (*next_ptr)->data;
    (*next_ptr)->data = data;
    return 1;
  }

  // An element equal to |data| doesn't exist in the hash table yet.
  item = OPENSSL_malloc(sizeof(LHASH_ITEM));
  if (item == NULL) {
    return 0;
  }

  item->data = data;
  item->hash = hash;
  item->next = NULL;
  *next_ptr = item;
  lh->num_items++;
  lh_maybe_resize(lh);

  return 1;
}

void *lh_delete(_LHASH *lh, const void *data) {
  LHASH_ITEM **next_ptr, *item, *ret;

  next_ptr = get_next_ptr_and_hash(lh, NULL, data);

  if (*next_ptr == NULL) {
    // No such element.
    return NULL;
  }

  item = *next_ptr;
  *next_ptr = item->next;
  ret = item->data;
  OPENSSL_free(item);

  lh->num_items--;
  lh_maybe_resize(lh);

  return ret;
}

static void lh_doall_internal(_LHASH *lh, void (*no_arg_func)(void *),
                              void (*arg_func)(void *, void *), void *arg) {
  if (lh == NULL) {
    return;
  }

  if (lh->callback_depth < UINT_MAX) {
    // |callback_depth| is a saturating counter.
    lh->callback_depth++;
  }

  for (size_t i = 0; i < lh->num_buckets; i++) {
    LHASH_ITEM *next;
    for (LHASH_ITEM *cur = lh->buckets[i]; cur != NULL; cur = next) {
      next = cur->next;
      if (arg_func) {
        arg_func(cur->data, arg);
      } else {
        no_arg_func(cur->data);
      }
    }
  }

  if (lh->callback_depth < UINT_MAX) {
    lh->callback_depth--;
  }

  // The callback may have added or removed elements and the non-zero value of
  // |callback_depth| will have suppressed any resizing. Thus any needed
  // resizing is done here.
  lh_maybe_resize(lh);
}

void lh_doall(_LHASH *lh, void (*func)(void *)) {
  lh_doall_internal(lh, func, NULL, NULL);
}

void lh_doall_arg(_LHASH *lh, void (*func)(void *, void *), void *arg) {
  lh_doall_internal(lh, NULL, func, arg);
}

uint32_t lh_strhash(const char *c) {
  if (c == NULL) {
    return 0;
  }

  return OPENSSL_hash32(c, strlen(c));
}

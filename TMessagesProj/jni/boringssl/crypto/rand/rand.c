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

#include <openssl/rand.h>

#include <limits.h>
#include <string.h>

#include <openssl/chacha.h>
#include <openssl/mem.h>

#include "internal.h"
#include "../internal.h"


/* It's assumed that the operating system always has an unfailing source of
 * entropy which is accessed via |CRYPTO_sysrand|. (If the operating system
 * entropy source fails, it's up to |CRYPTO_sysrand| to abort the process—we
 * don't try to handle it.)
 *
 * In addition, the hardware may provide a low-latency RNG. Intel's rdrand
 * instruction is the canonical example of this. When a hardware RNG is
 * available we don't need to worry about an RNG failure arising from fork()ing
 * the process or moving a VM, so we can keep thread-local RNG state and XOR
 * the hardware entropy in.
 *
 * (We assume that the OS entropy is safe from fork()ing and VM duplication.
 * This might be a bit of a leap of faith, esp on Windows, but there's nothing
 * that we can do about it.) */

/* rand_thread_state contains the per-thread state for the RNG. This is only
 * used if the system has support for a hardware RNG. */
struct rand_thread_state {
  uint8_t key[32];
  uint64_t calls_used;
  size_t bytes_used;
  uint8_t partial_block[64];
  unsigned partial_block_used;
};

/* kMaxCallsPerRefresh is the maximum number of |RAND_bytes| calls that we'll
 * serve before reading a new key from the operating system. This only applies
 * if we have a hardware RNG. */
static const unsigned kMaxCallsPerRefresh = 1024;

/* kMaxBytesPerRefresh is the maximum number of bytes that we'll return from
 * |RAND_bytes| before reading a new key from the operating system. This only
 * applies if we have a hardware RNG. */
static const uint64_t kMaxBytesPerRefresh = 1024 * 1024;

/* rand_thread_state_free frees a |rand_thread_state|. This is called when a
 * thread exits. */
static void rand_thread_state_free(void *state) {
  if (state == NULL) {
    return;
  }

  OPENSSL_cleanse(state, sizeof(struct rand_thread_state));
  OPENSSL_free(state);
}

int RAND_bytes(uint8_t *buf, size_t len) {
  if (len == 0) {
    return 1;
  }

  if (!CRYPTO_hwrand(buf, len)) {
    /* Without a hardware RNG to save us from address-space duplication, the OS
     * entropy is used directly. */
    CRYPTO_sysrand(buf, len);
    return 1;
  }

  struct rand_thread_state *state =
      CRYPTO_get_thread_local(OPENSSL_THREAD_LOCAL_RAND);
  if (state == NULL) {
    state = OPENSSL_malloc(sizeof(struct rand_thread_state));
    if (state == NULL ||
        !CRYPTO_set_thread_local(OPENSSL_THREAD_LOCAL_RAND, state,
                                 rand_thread_state_free)) {
      CRYPTO_sysrand(buf, len);
      return 1;
    }

    memset(state->partial_block, 0, sizeof(state->partial_block));
    state->calls_used = kMaxCallsPerRefresh;
  }

  if (state->calls_used >= kMaxCallsPerRefresh ||
      state->bytes_used >= kMaxBytesPerRefresh) {
    CRYPTO_sysrand(state->key, sizeof(state->key));
    state->calls_used = 0;
    state->bytes_used = 0;
    state->partial_block_used = sizeof(state->partial_block);
  }

  if (len >= sizeof(state->partial_block)) {
    size_t remaining = len;
    while (remaining > 0) {
      // kMaxBytesPerCall is only 2GB, while ChaCha can handle 256GB. But this
      // is sufficient and easier on 32-bit.
      static const size_t kMaxBytesPerCall = 0x80000000;
      size_t todo = remaining;
      if (todo > kMaxBytesPerCall) {
        todo = kMaxBytesPerCall;
      }
      CRYPTO_chacha_20(buf, buf, todo, state->key,
                       (uint8_t *)&state->calls_used, 0);
      buf += todo;
      remaining -= todo;
      state->calls_used++;
    }
  } else {
    if (sizeof(state->partial_block) - state->partial_block_used < len) {
      CRYPTO_chacha_20(state->partial_block, state->partial_block,
                       sizeof(state->partial_block), state->key,
                       (uint8_t *)&state->calls_used, 0);
      state->partial_block_used = 0;
    }

    unsigned i;
    for (i = 0; i < len; i++) {
      buf[i] ^= state->partial_block[state->partial_block_used++];
    }
    state->calls_used++;
  }
  state->bytes_used += len;

  return 1;
}

int RAND_pseudo_bytes(uint8_t *buf, size_t len) {
  return RAND_bytes(buf, len);
}

void RAND_seed(const void *buf, int num) {}

int RAND_load_file(const char *path, long num) {
  if (num < 0) {  /* read the "whole file" */
    return 1;
  } else if (num <= INT_MAX) {
    return (int) num;
  } else {
    return INT_MAX;
  }
}

void RAND_add(const void *buf, int num, double entropy) {}

int RAND_egd(const char *path) {
  return 255;
}

int RAND_poll(void) {
  return 1;
}

int RAND_status(void) {
  return 1;
}

static const struct rand_meth_st kSSLeayMethod = {
  RAND_seed,
  RAND_bytes,
  RAND_cleanup,
  RAND_add,
  RAND_pseudo_bytes,
  RAND_status,
};

RAND_METHOD *RAND_SSLeay(void) {
  return (RAND_METHOD*) &kSSLeayMethod;
}

void RAND_set_rand_method(const RAND_METHOD *method) {}

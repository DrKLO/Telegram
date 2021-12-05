/* Copyright (c) 2015, Google Inc.
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

#include "internal.h"

#if defined(OPENSSL_PTHREADS)

#include <pthread.h>
#include <stdlib.h>
#include <string.h>

#include <openssl/mem.h>
#include <openssl/type_check.h>


OPENSSL_STATIC_ASSERT(sizeof(CRYPTO_MUTEX) >= sizeof(pthread_rwlock_t),
                      "CRYPTO_MUTEX is too small");

void CRYPTO_MUTEX_init(CRYPTO_MUTEX *lock) {
  if (pthread_rwlock_init((pthread_rwlock_t *) lock, NULL) != 0) {
    abort();
  }
}

void CRYPTO_MUTEX_lock_read(CRYPTO_MUTEX *lock) {
  if (pthread_rwlock_rdlock((pthread_rwlock_t *) lock) != 0) {
    abort();
  }
}

void CRYPTO_MUTEX_lock_write(CRYPTO_MUTEX *lock) {
  if (pthread_rwlock_wrlock((pthread_rwlock_t *) lock) != 0) {
    abort();
  }
}

void CRYPTO_MUTEX_unlock_read(CRYPTO_MUTEX *lock) {
  if (pthread_rwlock_unlock((pthread_rwlock_t *) lock) != 0) {
    abort();
  }
}

void CRYPTO_MUTEX_unlock_write(CRYPTO_MUTEX *lock) {
  if (pthread_rwlock_unlock((pthread_rwlock_t *) lock) != 0) {
    abort();
  }
}

void CRYPTO_MUTEX_cleanup(CRYPTO_MUTEX *lock) {
  pthread_rwlock_destroy((pthread_rwlock_t *) lock);
}

void CRYPTO_STATIC_MUTEX_lock_read(struct CRYPTO_STATIC_MUTEX *lock) {
  if (pthread_rwlock_rdlock(&lock->lock) != 0) {
    abort();
  }
}

void CRYPTO_STATIC_MUTEX_lock_write(struct CRYPTO_STATIC_MUTEX *lock) {
  if (pthread_rwlock_wrlock(&lock->lock) != 0) {
    abort();
  }
}

void CRYPTO_STATIC_MUTEX_unlock_read(struct CRYPTO_STATIC_MUTEX *lock) {
  if (pthread_rwlock_unlock(&lock->lock) != 0) {
    abort();
  }
}

void CRYPTO_STATIC_MUTEX_unlock_write(struct CRYPTO_STATIC_MUTEX *lock) {
  if (pthread_rwlock_unlock(&lock->lock) != 0) {
    abort();
  }
}

void CRYPTO_once(CRYPTO_once_t *once, void (*init)(void)) {
  if (pthread_once(once, init) != 0) {
    abort();
  }
}

static pthread_mutex_t g_destructors_lock = PTHREAD_MUTEX_INITIALIZER;
static thread_local_destructor_t g_destructors[NUM_OPENSSL_THREAD_LOCALS];

// thread_local_destructor is called when a thread exits. It releases thread
// local data for that thread only.
static void thread_local_destructor(void *arg) {
  if (arg == NULL) {
    return;
  }

  thread_local_destructor_t destructors[NUM_OPENSSL_THREAD_LOCALS];
  if (pthread_mutex_lock(&g_destructors_lock) != 0) {
    return;
  }
  OPENSSL_memcpy(destructors, g_destructors, sizeof(destructors));
  pthread_mutex_unlock(&g_destructors_lock);

  unsigned i;
  void **pointers = arg;
  for (i = 0; i < NUM_OPENSSL_THREAD_LOCALS; i++) {
    if (destructors[i] != NULL) {
      destructors[i](pointers[i]);
    }
  }

  OPENSSL_free(pointers);
}

static pthread_once_t g_thread_local_init_once = PTHREAD_ONCE_INIT;
static pthread_key_t g_thread_local_key;
static int g_thread_local_key_created = 0;

// OPENSSL_DANGEROUS_RELEASE_PTHREAD_KEY can be defined to cause
// |pthread_key_delete| to be called in a destructor function. This can be
// useful for programs that dlclose BoringSSL.
//
// Note that dlclose()ing BoringSSL is not supported and will leak memory:
// thread-local values will be leaked as well as anything initialised via a
// once. The |pthread_key_t| is destroyed because they run out very quickly,
// while the other leaks are slow, and this allows code that happens to use
// dlclose() despite all the problems to continue functioning.
//
// This is marked "dangerous" because it can cause multi-threaded processes to
// crash (even if they don't use dlclose): if the destructor runs while other
// threads are still executing then they may end up using an invalid key to
// access thread-local variables.
//
// This may be removed after February 2020.
#if defined(OPENSSL_DANGEROUS_RELEASE_PTHREAD_KEY) && \
    (defined(__GNUC__) || defined(__clang__))
// thread_key_destructor is called when the library is unloaded with dlclose.
static void thread_key_destructor(void) __attribute__((destructor, unused));
static void thread_key_destructor(void) {
  if (g_thread_local_key_created) {
    g_thread_local_key_created = 0;
    pthread_key_delete(g_thread_local_key);
  }
}
#endif

static void thread_local_init(void) {
  g_thread_local_key_created =
      pthread_key_create(&g_thread_local_key, thread_local_destructor) == 0;
}

void *CRYPTO_get_thread_local(thread_local_data_t index) {
  CRYPTO_once(&g_thread_local_init_once, thread_local_init);
  if (!g_thread_local_key_created) {
    return NULL;
  }

  void **pointers = pthread_getspecific(g_thread_local_key);
  if (pointers == NULL) {
    return NULL;
  }
  return pointers[index];
}

int CRYPTO_set_thread_local(thread_local_data_t index, void *value,
                            thread_local_destructor_t destructor) {
  CRYPTO_once(&g_thread_local_init_once, thread_local_init);
  if (!g_thread_local_key_created) {
    destructor(value);
    return 0;
  }

  void **pointers = pthread_getspecific(g_thread_local_key);
  if (pointers == NULL) {
    pointers = OPENSSL_malloc(sizeof(void *) * NUM_OPENSSL_THREAD_LOCALS);
    if (pointers == NULL) {
      destructor(value);
      return 0;
    }
    OPENSSL_memset(pointers, 0, sizeof(void *) * NUM_OPENSSL_THREAD_LOCALS);
    if (pthread_setspecific(g_thread_local_key, pointers) != 0) {
      OPENSSL_free(pointers);
      destructor(value);
      return 0;
    }
  }

  if (pthread_mutex_lock(&g_destructors_lock) != 0) {
    destructor(value);
    return 0;
  }
  g_destructors[index] = destructor;
  pthread_mutex_unlock(&g_destructors_lock);

  pointers[index] = value;
  return 1;
}

#endif  // OPENSSL_PTHREADS

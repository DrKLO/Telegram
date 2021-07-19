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

#include <chrono>
#include <thread>

#include <gtest/gtest.h>

#include <openssl/crypto.h>
#include <openssl/rand.h>

#include "test/test_util.h"


#if defined(OPENSSL_THREADS)

static unsigned g_once_init_called = 0;

static void once_init(void) {
  g_once_init_called++;

  // Sleep briefly so one |call_once_func| instance will call |CRYPTO_once|
  // while the other is running this function.
  std::this_thread::sleep_for(std::chrono::milliseconds(1));
}

static CRYPTO_once_t g_test_once = CRYPTO_ONCE_INIT;

TEST(ThreadTest, Once) {
  ASSERT_EQ(0u, g_once_init_called)
      << "g_once_init_called was non-zero at start.";

  auto call_once_func = [] { CRYPTO_once(&g_test_once, once_init); };
  std::thread thread1(call_once_func), thread2(call_once_func);
  thread1.join();
  thread2.join();

  CRYPTO_once(&g_test_once, once_init);

  EXPECT_EQ(1u, g_once_init_called);
}

static CRYPTO_once_t once_init_value = CRYPTO_ONCE_INIT;
static CRYPTO_once_t once_bss;

static struct CRYPTO_STATIC_MUTEX mutex_init_value = CRYPTO_STATIC_MUTEX_INIT;
static struct CRYPTO_STATIC_MUTEX mutex_bss;

static CRYPTO_EX_DATA_CLASS ex_data_class_value = CRYPTO_EX_DATA_CLASS_INIT;
static CRYPTO_EX_DATA_CLASS ex_data_class_bss;

TEST(ThreadTest, InitZeros) {
  if (FIPS_mode()) {
    // Our FIPS tooling currently requires that |CRYPTO_ONCE_INIT|,
    // |CRYPTO_STATIC_MUTEX_INIT| and |CRYPTO_EX_DATA_CLASS| are all zeros and
    // so can be placed in the BSS section.
    EXPECT_EQ(Bytes((uint8_t *)&once_bss, sizeof(once_bss)),
              Bytes((uint8_t *)&once_init_value, sizeof(once_init_value)));
    EXPECT_EQ(Bytes((uint8_t *)&mutex_bss, sizeof(mutex_bss)),
              Bytes((uint8_t *)&mutex_init_value, sizeof(mutex_init_value)));
    EXPECT_EQ(
        Bytes((uint8_t *)&ex_data_class_bss, sizeof(ex_data_class_bss)),
        Bytes((uint8_t *)&ex_data_class_value, sizeof(ex_data_class_value)));
  }
}

static int g_test_thread_ok = 0;
static unsigned g_destructor_called_count = 0;

static void thread_local_destructor(void *arg) {
  if (arg == NULL) {
    return;
  }

  unsigned *count = reinterpret_cast<unsigned*>(arg);
  (*count)++;
}

TEST(ThreadTest, ThreadLocal) {
  ASSERT_EQ(nullptr, CRYPTO_get_thread_local(OPENSSL_THREAD_LOCAL_TEST))
      << "Thread-local data was non-NULL at start.";

  std::thread thread([] {
    if (CRYPTO_get_thread_local(OPENSSL_THREAD_LOCAL_TEST) != NULL ||
        !CRYPTO_set_thread_local(OPENSSL_THREAD_LOCAL_TEST,
                                 &g_destructor_called_count,
                                 thread_local_destructor) ||
        CRYPTO_get_thread_local(OPENSSL_THREAD_LOCAL_TEST) !=
            &g_destructor_called_count) {
      return;
    }

    g_test_thread_ok = 1;
  });
  thread.join();

  EXPECT_TRUE(g_test_thread_ok) << "Thread-local data didn't work in thread.";
  EXPECT_EQ(1u, g_destructor_called_count);

  // Create a no-op thread to test that the thread destructor function works
  // even if thread-local storage wasn't used for a thread.
  thread = std::thread([] {});
  thread.join();
}

TEST(ThreadTest, RandState) {
  // In FIPS mode, rand.c maintains a linked-list of thread-local data because
  // we're required to clear it on process exit. This test exercises removing a
  // value from that list.
  uint8_t buf[1];
  RAND_bytes(buf, sizeof(buf));

  std::thread thread([] {
    uint8_t buf2[1];
    RAND_bytes(buf2, sizeof(buf2));
  });
  thread.join();
}

#endif  // OPENSSL_THREADS

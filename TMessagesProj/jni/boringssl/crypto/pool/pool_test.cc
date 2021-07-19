/* Copyright (c) 2016, Google Inc.
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

#include <gtest/gtest.h>

#include <openssl/pool.h>

#include "../test/test_util.h"

#if defined(OPENSSL_THREADS)
#include <chrono>
#include <thread>
#endif


TEST(PoolTest, Unpooled) {
  static const uint8_t kData[4] = {1, 2, 3, 4};
  bssl::UniquePtr<CRYPTO_BUFFER> buf(
      CRYPTO_BUFFER_new(kData, sizeof(kData), nullptr));
  ASSERT_TRUE(buf);

  EXPECT_EQ(Bytes(kData),
            Bytes(CRYPTO_BUFFER_data(buf.get()), CRYPTO_BUFFER_len(buf.get())));

  // Test that reference-counting works properly.
  bssl::UniquePtr<CRYPTO_BUFFER> buf2 = bssl::UpRef(buf);
}

TEST(PoolTest, Empty) {
  bssl::UniquePtr<CRYPTO_BUFFER> buf(CRYPTO_BUFFER_new(nullptr, 0, nullptr));
  ASSERT_TRUE(buf);

  EXPECT_EQ(Bytes(""),
            Bytes(CRYPTO_BUFFER_data(buf.get()), CRYPTO_BUFFER_len(buf.get())));
}

TEST(PoolTest, Pooled) {
  bssl::UniquePtr<CRYPTO_BUFFER_POOL> pool(CRYPTO_BUFFER_POOL_new());
  ASSERT_TRUE(pool);

  static const uint8_t kData[4] = {1, 2, 3, 4};
  bssl::UniquePtr<CRYPTO_BUFFER> buf(
      CRYPTO_BUFFER_new(kData, sizeof(kData), pool.get()));
  ASSERT_TRUE(buf);

  bssl::UniquePtr<CRYPTO_BUFFER> buf2(
      CRYPTO_BUFFER_new(kData, sizeof(kData), pool.get()));
  ASSERT_TRUE(buf2);

  EXPECT_EQ(buf.get(), buf2.get()) << "CRYPTO_BUFFER_POOL did not dedup data.";
}

#if defined(OPENSSL_THREADS)
TEST(PoolTest, Threads) {
  bssl::UniquePtr<CRYPTO_BUFFER_POOL> pool(CRYPTO_BUFFER_POOL_new());
  ASSERT_TRUE(pool);

  // Race threads making pooled |CRYPTO_BUFFER|s.
  static const uint8_t kData[4] = {1, 2, 3, 4};
  static const uint8_t kData2[3] = {4, 5, 6};
  bssl::UniquePtr<CRYPTO_BUFFER> buf, buf2, buf3;
  {
    std::thread thread([&] {
      buf.reset(CRYPTO_BUFFER_new(kData, sizeof(kData), pool.get()));
    });
    std::thread thread2([&] {
      buf2.reset(CRYPTO_BUFFER_new(kData, sizeof(kData), pool.get()));
    });
    buf3.reset(CRYPTO_BUFFER_new(kData2, sizeof(kData2), pool.get()));
    thread.join();
    thread2.join();
  }

  ASSERT_TRUE(buf);
  ASSERT_TRUE(buf2);
  ASSERT_TRUE(buf3);
  EXPECT_EQ(buf.get(), buf2.get()) << "CRYPTO_BUFFER_POOL did not dedup data.";
  EXPECT_NE(buf.get(), buf3.get())
      << "CRYPTO_BUFFER_POOL incorrectly deduped data.";
  EXPECT_EQ(Bytes(kData),
            Bytes(CRYPTO_BUFFER_data(buf.get()), CRYPTO_BUFFER_len(buf.get())));
  EXPECT_EQ(Bytes(kData2), Bytes(CRYPTO_BUFFER_data(buf3.get()),
                                 CRYPTO_BUFFER_len(buf3.get())));

  // Reference-counting of |CRYPTO_BUFFER| interacts with pooling. Race an
  // increment and free.
  {
    bssl::UniquePtr<CRYPTO_BUFFER> buf_ref;
    std::thread thread([&] { buf_ref = bssl::UpRef(buf); });
    buf2.reset();
    thread.join();
  }

  // |buf|'s data is still valid.
  EXPECT_EQ(Bytes(kData), Bytes(CRYPTO_BUFFER_data(buf.get()),
                                CRYPTO_BUFFER_len(buf.get())));

  // Race a thread re-creating the |CRYPTO_BUFFER| with another thread freeing
  // it. Do this twice with sleeps so ThreadSanitizer can observe two different
  // interleavings. Ideally we would run this test under a tool that could
  // search all interleavings.
  {
    std::thread thread([&] {
      std::this_thread::sleep_for(std::chrono::milliseconds(1));
      buf.reset();
    });
    buf2.reset(CRYPTO_BUFFER_new(kData, sizeof(kData), pool.get()));
    thread.join();

    ASSERT_TRUE(buf2);
    EXPECT_EQ(Bytes(kData), Bytes(CRYPTO_BUFFER_data(buf2.get()),
                                  CRYPTO_BUFFER_len(buf2.get())));
    buf = std::move(buf2);
  }

  {
    std::thread thread([&] { buf.reset(); });
    std::this_thread::sleep_for(std::chrono::milliseconds(1));
    buf2.reset(CRYPTO_BUFFER_new(kData, sizeof(kData), pool.get()));
    thread.join();

    ASSERT_TRUE(buf2);
    EXPECT_EQ(Bytes(kData), Bytes(CRYPTO_BUFFER_data(buf2.get()),
                                  CRYPTO_BUFFER_len(buf2.get())));
    buf = std::move(buf2);
  }

  // Finally, race the frees.
  {
    buf2 = bssl::UpRef(buf);
    std::thread thread([&] { buf.reset(); });
    std::thread thread2([&] { buf3.reset(); });
    buf2.reset();
    thread.join();
    thread2.join();
  }
}
#endif

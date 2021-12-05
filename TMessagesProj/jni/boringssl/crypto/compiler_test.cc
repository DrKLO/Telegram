/* Copyright (c) 2017, Google Inc.
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

#include <limits.h>
#include <stdint.h>

#include <type_traits>

#include <gtest/gtest.h>

#include "test/test_util.h"


template <typename T>
static void CheckRepresentation(T value) {
  SCOPED_TRACE(value);

  // Convert to the corresponding two's-complement unsigned value. We use an
  // unsigned value so the right-shift below has defined value. Right-shifts of
  // negative numbers in C are implementation defined.
  //
  // If |T| is already unsigned, this is a no-op, as desired.
  //
  // If |T| is signed, conversion to unsigned is defined to repeatedly add or
  // subtract (numerically, not within |T|) one more than the unsigned type's
  // maximum value until it fits (this must be a power of two). This is the
  // conversion we want.
  using UnsignedT = typename std::make_unsigned<T>::type;
  UnsignedT value_u = static_cast<UnsignedT>(value);
  EXPECT_EQ(sizeof(UnsignedT), sizeof(T));

  // Integers must be little-endian.
  uint8_t expected[sizeof(UnsignedT)];
  for (size_t i = 0; i < sizeof(UnsignedT); i++) {
    expected[i] = static_cast<uint8_t>(value_u);
    // Divide instead of right-shift to appease compilers that warn if |T| is a
    // char. The explicit cast is also needed to appease MSVC if integer
    // promotion happened.
    value_u = static_cast<UnsignedT>(value_u / 256);
  }
  EXPECT_EQ(0u, value_u);

  // Check that |value| has the expected representation.
  EXPECT_EQ(Bytes(expected),
            Bytes(reinterpret_cast<const uint8_t *>(&value), sizeof(value)));
}

TEST(CompilerTest, IntegerRepresentation) {
  EXPECT_EQ(8, CHAR_BIT);
  EXPECT_EQ(0xff, static_cast<int>(UCHAR_MAX));

  // uint8_t is assumed to be unsigned char. I.e., casting to uint8_t should be
  // as good as unsigned char for strict aliasing purposes.
  uint8_t u8 = 0;
  unsigned char *ptr = &u8;
  (void)ptr;

  // Sized integers have the expected size.
  EXPECT_EQ(1u, sizeof(uint8_t));
  EXPECT_EQ(2u, sizeof(uint16_t));
  EXPECT_EQ(4u, sizeof(uint32_t));
  EXPECT_EQ(8u, sizeof(uint64_t));

  // size_t does not exceed uint64_t.
  EXPECT_LE(sizeof(size_t), 8u);

  // int must be 32-bit or larger.
  EXPECT_LE(0x7fffffff, INT_MAX);
  EXPECT_LE(0xffffffffu, UINT_MAX);

  CheckRepresentation(static_cast<signed char>(127));
  CheckRepresentation(static_cast<signed char>(1));
  CheckRepresentation(static_cast<signed char>(0));
  CheckRepresentation(static_cast<signed char>(-1));
  CheckRepresentation(static_cast<signed char>(-42));
  CheckRepresentation(static_cast<signed char>(-128));

  CheckRepresentation(static_cast<int>(INT_MAX));
  CheckRepresentation(static_cast<int>(0x12345678));
  CheckRepresentation(static_cast<int>(1));
  CheckRepresentation(static_cast<int>(0));
  CheckRepresentation(static_cast<int>(-1));
  CheckRepresentation(static_cast<int>(-0x12345678));
  CheckRepresentation(static_cast<int>(INT_MIN));

  CheckRepresentation(static_cast<unsigned>(UINT_MAX));
  CheckRepresentation(static_cast<unsigned>(0x12345678));
  CheckRepresentation(static_cast<unsigned>(1));
  CheckRepresentation(static_cast<unsigned>(0));

  CheckRepresentation(static_cast<long>(LONG_MAX));
  CheckRepresentation(static_cast<long>(0x12345678));
  CheckRepresentation(static_cast<long>(1));
  CheckRepresentation(static_cast<long>(0));
  CheckRepresentation(static_cast<long>(-1));
  CheckRepresentation(static_cast<long>(-0x12345678));
  CheckRepresentation(static_cast<long>(LONG_MIN));

  CheckRepresentation(static_cast<unsigned long>(ULONG_MAX));
  CheckRepresentation(static_cast<unsigned long>(0x12345678));
  CheckRepresentation(static_cast<unsigned long>(1));
  CheckRepresentation(static_cast<unsigned long>(0));

  CheckRepresentation(static_cast<int16_t>(0x7fff));
  CheckRepresentation(static_cast<int16_t>(0x1234));
  CheckRepresentation(static_cast<int16_t>(1));
  CheckRepresentation(static_cast<int16_t>(0));
  CheckRepresentation(static_cast<int16_t>(-1));
  CheckRepresentation(static_cast<int16_t>(-0x7fff - 1));

  CheckRepresentation(static_cast<uint16_t>(0xffff));
  CheckRepresentation(static_cast<uint16_t>(0x1234));
  CheckRepresentation(static_cast<uint16_t>(1));
  CheckRepresentation(static_cast<uint16_t>(0));

  CheckRepresentation(static_cast<int32_t>(0x7fffffff));
  CheckRepresentation(static_cast<int32_t>(0x12345678));
  CheckRepresentation(static_cast<int32_t>(1));
  CheckRepresentation(static_cast<int32_t>(0));
  CheckRepresentation(static_cast<int32_t>(-1));
  CheckRepresentation(static_cast<int32_t>(-0x7fffffff - 1));

  CheckRepresentation(static_cast<uint32_t>(0xffffffff));
  CheckRepresentation(static_cast<uint32_t>(0x12345678));
  CheckRepresentation(static_cast<uint32_t>(1));
  CheckRepresentation(static_cast<uint32_t>(0));

  CheckRepresentation(static_cast<int64_t>(0x7fffffffffffffff));
  CheckRepresentation(static_cast<int64_t>(0x123456789abcdef0));
  CheckRepresentation(static_cast<int64_t>(1));
  CheckRepresentation(static_cast<int64_t>(0));
  CheckRepresentation(static_cast<int64_t>(-1));
  CheckRepresentation(static_cast<int64_t>(-0x7fffffffffffffff - 1));

  CheckRepresentation(static_cast<uint64_t>(0xffffffffffffffff));
  CheckRepresentation(static_cast<uint64_t>(0x12345678abcdef0));
  CheckRepresentation(static_cast<uint64_t>(1));
  CheckRepresentation(static_cast<uint64_t>(0));
}

TEST(CompilerTest, PointerRepresentation) {
  // Converting pointers to integers and doing arithmetic on those values are
  // both defined. Converting those values back into pointers is undefined,
  // but, for aliasing checks, we require that the implementation-defined
  // result of that computation commutes with pointer arithmetic.
  char chars[256];
  for (size_t i = 0; i < sizeof(chars); i++) {
    EXPECT_EQ(reinterpret_cast<uintptr_t>(chars) + i,
              reinterpret_cast<uintptr_t>(chars + i));
  }

  int ints[256];
  for (size_t i = 0; i < OPENSSL_ARRAY_SIZE(ints); i++) {
    EXPECT_EQ(reinterpret_cast<uintptr_t>(ints) + i * sizeof(int),
              reinterpret_cast<uintptr_t>(ints + i));
  }

  // nullptr must be represented by all zeros in memory. This is necessary so
  // structs may be initialized by memset(0).
  int *null = nullptr;
  uint8_t bytes[sizeof(null)] = {0};
  EXPECT_EQ(Bytes(bytes),
            Bytes(reinterpret_cast<uint8_t *>(&null), sizeof(null)));
}

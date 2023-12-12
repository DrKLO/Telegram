// Copyright (c) 2010 Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

// Original author: Jim Blandy <jimb@mozilla.com> <jimb@red-bean.com>

// byte_cursor_unittest.cc: Unit tests for google_breakpad::ByteBuffer
// and google_breakpad::ByteCursor.

#include <string>

#include <string.h>

#include "breakpad_googletest_includes.h"
#include "common/byte_cursor.h"
#include "common/using_std_string.h"

using google_breakpad::ByteBuffer;
using google_breakpad::ByteCursor;

TEST(Buffer, SizeOfNothing) {
  uint8_t data[1];
  ByteBuffer buffer(data, 0);
  EXPECT_EQ(0U, buffer.Size());
}

TEST(Buffer, SizeOfSomething) {
  uint8_t data[10];
  ByteBuffer buffer(data, sizeof(data));
  EXPECT_EQ(10U, buffer.Size());
}

TEST(Extent, AvailableEmpty) {
  uint8_t data[1];
  ByteBuffer buffer(data, 0);
  ByteCursor cursor(&buffer);
  EXPECT_EQ(0U, cursor.Available());
}

TEST(Extent, AtEndEmpty) {
  uint8_t data[1];
  ByteBuffer buffer(data, 0);
  ByteCursor cursor(&buffer);
  EXPECT_TRUE(cursor.AtEnd());
}

TEST(Extent, AsBoolEmpty) {
  uint8_t data[1];
  ByteBuffer buffer(data, 0);
  ByteCursor cursor(&buffer);
  EXPECT_TRUE(cursor);
}

TEST(Extent, AvailableSome) {
  uint8_t data[10];
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  EXPECT_EQ(10U, cursor.Available());
}

TEST(Extent, AtEndSome) {
  uint8_t data[10];
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  EXPECT_FALSE(cursor.AtEnd());
  EXPECT_TRUE(cursor.Skip(sizeof(data)).AtEnd());
}

TEST(Extent, AsBoolSome) {
  uint8_t data[10];
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  EXPECT_TRUE(cursor);
  EXPECT_TRUE(cursor.Skip(sizeof(data)));
  EXPECT_FALSE(cursor.Skip(1));
}

TEST(Extent, Cursor) {
  uint8_t data[] = { 0xf7,
                     0x9f, 0xbe,
                     0x67, 0xfb, 0xd3, 0x58,
                     0x6f, 0x36, 0xde, 0xd1,
                     0x2a, 0x2a, 0x2a };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);

  uint8_t a;
  uint16_t b;
  uint32_t c;
  uint32_t d;
  uint8_t stars[3];

  EXPECT_EQ(data + 0U, cursor.here());

  EXPECT_TRUE(cursor >> a);
  EXPECT_EQ(data + 1U, cursor.here());

  EXPECT_TRUE(cursor >> b);
  EXPECT_EQ(data + 3U, cursor.here());

  EXPECT_TRUE(cursor >> c);
  EXPECT_EQ(data + 7U, cursor.here());

  EXPECT_TRUE(cursor.Skip(4));
  EXPECT_EQ(data + 11U, cursor.here());

  EXPECT_TRUE(cursor.Read(stars, 3));
  EXPECT_EQ(data + 14U, cursor.here());

  EXPECT_FALSE(cursor >> d);
  EXPECT_EQ(data + 14U, cursor.here());
}

TEST(Extent, SetOffset) {
  uint8_t data[] = { 0x5c, 0x79, 0x8c, 0xd5 };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);

  uint8_t a, b, c, d, e;
  EXPECT_TRUE(cursor >> a);
  EXPECT_EQ(0x5cU, a);
  EXPECT_EQ(data + 1U, cursor.here());
  EXPECT_TRUE(((cursor >> b).set_here(data + 3) >> c).set_here(data + 1)
              >> d >> e);
  EXPECT_EQ(0x79U, b);
  EXPECT_EQ(0xd5U, c);
  EXPECT_EQ(0x79U, d);
  EXPECT_EQ(0x8cU, e);
  EXPECT_EQ(data + 3U, cursor.here());
}

TEST(BigEndian, Signed1) {
  uint8_t data[] = { 0x00, 0x7f, 0x80, 0xff };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  cursor.set_big_endian(true);
  int a, b, c, d, e;
  ASSERT_TRUE(cursor
              .Read(1, true, &a)
              .Read(1, true, &b)
              .Read(1, true, &c)
              .Read(1, true, &d));
  EXPECT_EQ(0,     a);
  EXPECT_EQ(0x7f,  b);
  EXPECT_EQ(-0x80, c);
  EXPECT_EQ(-1,    d);
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(1, true, &e));
}

TEST(BigEndian, Signed2) {
  uint8_t data[] = { 0x00, 0x00,   0x00, 0x80,   0x7f, 0xff,
                     0x80, 0x00,   0x80, 0x80,   0xff, 0xff,
                     0x39, 0xf1,   0x8a, 0xbc,   0x5a, 0xec };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer, true);
  int a, b, c, d, e, f, g, h, i, j;
  ASSERT_TRUE(cursor
              .Read(2, true, &a)
              .Read(2, true, &b)
              .Read(2, true, &c)
              .Read(2, true, &d)
              .Read(2, true, &e)
              .Read(2, true, &f)
              .Read(2, true, &g)
              .Read(2, true, &h)
              .Read(2, true, &i));
  EXPECT_EQ(0,       a);
  EXPECT_EQ(0x80,    b);
  EXPECT_EQ(0x7fff,  c);
  EXPECT_EQ(-0x8000, d);
  EXPECT_EQ(-0x7f80, e);
  EXPECT_EQ(-1,      f);
  EXPECT_EQ(0x39f1,  g);
  EXPECT_EQ(-0x7544, h);
  EXPECT_EQ(0x5aec,  i);
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(2, true, &j));
}

TEST(BigEndian, Signed4) {
  uint8_t data[] = { 0x00, 0x00, 0x00, 0x00,
                     0x7f, 0xff, 0xff, 0xff,
                     0x80, 0x00, 0x00, 0x00,
                     0xff, 0xff, 0xff, 0xff,
                     0xb6, 0xb1, 0xff, 0xef,
                     0x19, 0x6a, 0xca, 0x46 };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  cursor.set_big_endian(true);
  int64_t a, b, c, d, e, f, g;
  ASSERT_TRUE(cursor
              .Read(4, true, &a)
              .Read(4, true, &b)
              .Read(4, true, &c)
              .Read(4, true, &d)
              .Read(4, true, &e)
              .Read(4, true, &f));
  EXPECT_EQ(0,                    a);
  EXPECT_EQ(0x7fffffff,           b);
  EXPECT_EQ(-0x80000000LL,        c);
  EXPECT_EQ(-1,                   d);
  EXPECT_EQ((int32_t) 0xb6b1ffef, e);
  EXPECT_EQ(0x196aca46,           f);
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(4, true, &g));
}

TEST(BigEndian, Signed8) {
  uint8_t data[] = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
                     0x7f, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                     0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                     0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                     0x93, 0x20, 0xd5, 0xe9, 0xd2, 0xd5, 0x87, 0x9c,
                     0x4e, 0x42, 0x49, 0xd2, 0x7f, 0x84, 0x14, 0xa4 };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer, true);
  int64_t a, b, c, d, e, f, g;
  ASSERT_TRUE(cursor
              .Read(8, true, &a)
              .Read(8, true, &b)
              .Read(8, true, &c)
              .Read(8, true, &d)
              .Read(8, true, &e)
              .Read(8, true, &f));
  EXPECT_EQ(0,                               a);
  EXPECT_EQ(0x7fffffffffffffffLL,            b);
  EXPECT_EQ(-0x7fffffffffffffffLL - 1,       c);
  EXPECT_EQ(-1,                              d);
  EXPECT_EQ((int64_t) 0x9320d5e9d2d5879cULL, e);
  EXPECT_EQ(0x4e4249d27f8414a4LL,            f);  
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(8, true, &g));
}

TEST(BigEndian, Unsigned1) {
  uint8_t data[] = { 0x00, 0x7f, 0x80, 0xff };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  cursor.set_big_endian(true);
  int32_t a, b, c, d, e;
  ASSERT_TRUE(cursor
              .Read(1, false, &a)
              .Read(1, false, &b)
              .Read(1, false, &c)
              .Read(1, false, &d));
  EXPECT_EQ(0,    a);
  EXPECT_EQ(0x7f, b);
  EXPECT_EQ(0x80, c);
  EXPECT_EQ(0xff, d);
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(1, false, &e));
}

TEST(BigEndian, Unsigned2) {
  uint8_t data[] = { 0x00, 0x00,   0x00, 0x80,   0x7f, 0xff,
                     0x80, 0x00,   0x80, 0x80,   0xff, 0xff,
                     0x39, 0xf1,   0x8a, 0xbc,   0x5a, 0xec };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer, true);
  int64_t a, b, c, d, e, f, g, h, i, j;
  ASSERT_TRUE(cursor
              .Read(2, false, &a)
              .Read(2, false, &b)
              .Read(2, false, &c)
              .Read(2, false, &d)
              .Read(2, false, &e)
              .Read(2, false, &f)
              .Read(2, false, &g)
              .Read(2, false, &h)
              .Read(2, false, &i));
  EXPECT_EQ(0,      a);
  EXPECT_EQ(0x80,   b);
  EXPECT_EQ(0x7fff, c);
  EXPECT_EQ(0x8000, d);
  EXPECT_EQ(0x8080, e);
  EXPECT_EQ(0xffff, f);
  EXPECT_EQ(0x39f1, g);
  EXPECT_EQ(0x8abc, h);
  EXPECT_EQ(0x5aec, i);
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(2, false, &j));
}

TEST(BigEndian, Unsigned4) {
  uint8_t data[] = { 0x00, 0x00, 0x00, 0x00,
                     0x7f, 0xff, 0xff, 0xff,
                     0x80, 0x00, 0x00, 0x00,
                     0xff, 0xff, 0xff, 0xff,
                     0xb6, 0xb1, 0xff, 0xef,
                     0x19, 0x6a, 0xca, 0x46 };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  cursor.set_big_endian(true);
  int64_t a, b, c, d, e, f, g;
  ASSERT_TRUE(cursor
              .Read(4, false, &a)
              .Read(4, false, &b)
              .Read(4, false, &c)
              .Read(4, false, &d)
              .Read(4, false, &e)
              .Read(4, false, &f));
  EXPECT_EQ(0,          a);
  EXPECT_EQ(0x7fffffff, b);
  EXPECT_EQ(0x80000000, c);
  EXPECT_EQ(0xffffffff, d);
  EXPECT_EQ(0xb6b1ffef, e);
  EXPECT_EQ(0x196aca46, f);
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(4, false, &g));
}

TEST(BigEndian, Unsigned8) {
  uint8_t data[] = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
                     0x7f, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                     0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                     0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                     0x93, 0x20, 0xd5, 0xe9, 0xd2, 0xd5, 0x87, 0x9c,
                     0x4e, 0x42, 0x49, 0xd2, 0x7f, 0x84, 0x14, 0xa4 };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer, true);
  uint64_t a, b, c, d, e, f, g;
  ASSERT_TRUE(cursor
              .Read(8, false, &a)
              .Read(8, false, &b)
              .Read(8, false, &c)
              .Read(8, false, &d)
              .Read(8, false, &e)
              .Read(8, false, &f));
  EXPECT_EQ(0U,                    a);
  EXPECT_EQ(0x7fffffffffffffffULL, b);
  EXPECT_EQ(0x8000000000000000ULL, c);
  EXPECT_EQ(0xffffffffffffffffULL, d);
  EXPECT_EQ(0x9320d5e9d2d5879cULL, e);
  EXPECT_EQ(0x4e4249d27f8414a4ULL, f);  
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(8, false, &g));
}

TEST(LittleEndian, Signed1) {
  uint8_t data[] = { 0x00, 0x7f, 0x80, 0xff };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  int32_t a, b, c, d, e;
  ASSERT_TRUE(cursor
              .Read(1, true, &a)
              .Read(1, true, &b)
              .Read(1, true, &c)
              .Read(1, true, &d));
  EXPECT_EQ(0,     a);
  EXPECT_EQ(0x7f,  b);
  EXPECT_EQ(-0x80, c);
  EXPECT_EQ(-1,    d);
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(1, true, &e));
}

TEST(LittleEndian, Signed2) {
  uint8_t data[] = { 0x00, 0x00,   0x80, 0x00,   0xff, 0x7f,
                     0x00, 0x80,   0x80, 0x80,   0xff, 0xff,
                     0xf1, 0x39,   0xbc, 0x8a,   0xec, 0x5a };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer, false);
  int32_t a, b, c, d, e, f, g, h, i, j;
  ASSERT_TRUE(cursor
              .Read(2, true, &a)
              .Read(2, true, &b)
              .Read(2, true, &c)
              .Read(2, true, &d)
              .Read(2, true, &e)
              .Read(2, true, &f)
              .Read(2, true, &g)
              .Read(2, true, &h)
              .Read(2, true, &i));
  EXPECT_EQ(0,       a);
  EXPECT_EQ(0x80,    b);
  EXPECT_EQ(0x7fff,  c);
  EXPECT_EQ(-0x8000, d);
  EXPECT_EQ(-0x7f80, e);
  EXPECT_EQ(-1,      f);
  EXPECT_EQ(0x39f1,  g);
  EXPECT_EQ(-0x7544, h);
  EXPECT_EQ(0x5aec,  i);
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(2, true, &j));
}

TEST(LittleEndian, Signed4) {
  uint8_t data[] = { 0x00, 0x00, 0x00, 0x00,
                     0xff, 0xff, 0xff, 0x7f,
                     0x00, 0x00, 0x00, 0x80,
                     0xff, 0xff, 0xff, 0xff,
                     0xef, 0xff, 0xb1, 0xb6, 
                     0x46, 0xca, 0x6a, 0x19 };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  int64_t a, b, c, d, e, f, g;
  ASSERT_TRUE(cursor
              .Read(4, true, &a)
              .Read(4, true, &b)
              .Read(4, true, &c)
              .Read(4, true, &d)
              .Read(4, true, &e)
              .Read(4, true, &f));
  EXPECT_EQ(0,                    a);
  EXPECT_EQ(0x7fffffff,           b);
  EXPECT_EQ(-0x80000000LL,        c);
  EXPECT_EQ(-1,                   d);
  EXPECT_EQ((int32_t) 0xb6b1ffef, e);
  EXPECT_EQ(0x196aca46,           f);
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(4, true, &g));
}

TEST(LittleEndian, Signed8) {
  uint8_t data[] = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
                     0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x7f,
                     0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x80,
                     0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                     0x9c, 0x87, 0xd5, 0xd2, 0xe9, 0xd5, 0x20, 0x93,
                     0xa4, 0x14, 0x84, 0x7f, 0xd2, 0x49, 0x42, 0x4e };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer, false);
  int64_t a, b, c, d, e, f, g;
  ASSERT_TRUE(cursor
              .Read(8, true, &a)
              .Read(8, true, &b)
              .Read(8, true, &c)
              .Read(8, true, &d)
              .Read(8, true, &e)
              .Read(8, true, &f));
  EXPECT_EQ(0,                               a);
  EXPECT_EQ(0x7fffffffffffffffLL,            b);
  EXPECT_EQ(-0x7fffffffffffffffLL - 1,       c);
  EXPECT_EQ(-1,                              d);
  EXPECT_EQ((int64_t) 0x9320d5e9d2d5879cULL, e);
  EXPECT_EQ(0x4e4249d27f8414a4LL,            f);  
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(8, true, &g));
}

TEST(LittleEndian, Unsigned1) {
  uint8_t data[] = { 0x00, 0x7f, 0x80, 0xff };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  int32_t a, b, c, d, e;
  ASSERT_TRUE(cursor
              .Read(1, false, &a)
              .Read(1, false, &b)
              .Read(1, false, &c)
              .Read(1, false, &d));
  EXPECT_EQ(0,    a);
  EXPECT_EQ(0x7f, b);
  EXPECT_EQ(0x80, c);
  EXPECT_EQ(0xff, d);
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(1, false, &e));
}

TEST(LittleEndian, Unsigned2) {
  uint8_t data[] = { 0x00, 0x00,   0x80, 0x00,   0xff, 0x7f,
                     0x00, 0x80,   0x80, 0x80,   0xff, 0xff,
                     0xf1, 0x39,   0xbc, 0x8a,   0xec, 0x5a };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  int32_t a, b, c, d, e, f, g, h, i, j;
  ASSERT_TRUE(cursor
              .Read(2, false, &a)
              .Read(2, false, &b)
              .Read(2, false, &c)
              .Read(2, false, &d)
              .Read(2, false, &e)
              .Read(2, false, &f)
              .Read(2, false, &g)
              .Read(2, false, &h)
              .Read(2, false, &i));
  EXPECT_EQ(0,      a);
  EXPECT_EQ(0x80,   b);
  EXPECT_EQ(0x7fff, c);
  EXPECT_EQ(0x8000, d);
  EXPECT_EQ(0x8080, e);
  EXPECT_EQ(0xffff, f);
  EXPECT_EQ(0x39f1, g);
  EXPECT_EQ(0x8abc, h);
  EXPECT_EQ(0x5aec, i);
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(2, false, &j));
}

TEST(LittleEndian, Unsigned4) {
  uint8_t data[] = { 0x00, 0x00, 0x00, 0x00,
                     0xff, 0xff, 0xff, 0x7f,
                     0x00, 0x00, 0x00, 0x80,
                     0xff, 0xff, 0xff, 0xff,
                     0xef, 0xff, 0xb1, 0xb6,
                     0x46, 0xca, 0x6a, 0x19 };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  int64_t a, b, c, d, e, f, g;
  ASSERT_TRUE(cursor
              .Read(4, false, &a)
              .Read(4, false, &b)
              .Read(4, false, &c)
              .Read(4, false, &d)
              .Read(4, false, &e)
              .Read(4, false, &f));
  EXPECT_EQ(0,          a);
  EXPECT_EQ(0x7fffffff, b);
  EXPECT_EQ(0x80000000, c);
  EXPECT_EQ(0xffffffff, d);
  EXPECT_EQ(0xb6b1ffef, e);
  EXPECT_EQ(0x196aca46, f);
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(4, false, &g));
}

TEST(LittleEndian, Unsigned8) {
  uint8_t data[] = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
                     0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x7f,
                     0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x80,
                     0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                     0x9c, 0x87, 0xd5, 0xd2, 0xe9, 0xd5, 0x20, 0x93,
                     0xa4, 0x14, 0x84, 0x7f, 0xd2, 0x49, 0x42, 0x4e };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  uint64_t a, b, c, d, e, f, g;
  ASSERT_TRUE(cursor
              .Read(8, false, &a)
              .Read(8, false, &b)
              .Read(8, false, &c)
              .Read(8, false, &d)
              .Read(8, false, &e)
              .Read(8, false, &f));
  EXPECT_EQ(0U,                    a);
  EXPECT_EQ(0x7fffffffffffffffULL, b);
  EXPECT_EQ(0x8000000000000000ULL, c);
  EXPECT_EQ(0xffffffffffffffffULL, d);
  EXPECT_EQ(0x9320d5e9d2d5879cULL, e);
  EXPECT_EQ(0x4e4249d27f8414a4ULL, f);  
  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor.Read(8, false, &g));
}

TEST(Extractor, Signed1) {
  uint8_t data[] = { 0xfd };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  int8_t a;
  EXPECT_TRUE(cursor >> a);
  EXPECT_EQ(-3, a);
  EXPECT_FALSE(cursor >> a);
}

TEST(Extractor, Signed2) {
  uint8_t data[] = { 0x13, 0xcd };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  int16_t a;
  EXPECT_TRUE(cursor >> a);
  EXPECT_EQ(-13037, a);
  EXPECT_FALSE(cursor >> a);
}

TEST(Extractor, Signed4) {
  uint8_t data[] = { 0xd2, 0xe4, 0x53, 0xe9 };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  int32_t a;
  // For some reason, G++ 4.4.1 complains:
  //   warning: array subscript is above array bounds
  // in ByteCursor::Read(size_t, bool, T *) as it inlines this call, but
  // I'm not able to see how such a reference would occur.
  EXPECT_TRUE(cursor >> a);
  EXPECT_EQ(-380377902, a);
  EXPECT_FALSE(cursor >> a);
}

TEST(Extractor, Unsigned1) {
  uint8_t data[] = { 0xfd };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  uint8_t a;
  EXPECT_TRUE(cursor >> a);
  EXPECT_EQ(0xfd, a);
  EXPECT_FALSE(cursor >> a);
}

TEST(Extractor, Unsigned2) {
  uint8_t data[] = { 0x13, 0xcd };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  uint16_t a;
  EXPECT_TRUE(cursor >> a);
  EXPECT_EQ(0xcd13, a);
  EXPECT_FALSE(cursor >> a);
}

TEST(Extractor, Unsigned4) {
  uint8_t data[] = { 0xd2, 0xe4, 0x53, 0xe9 };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  uint32_t a;
  // For some reason, G++ 4.4.1 complains:
  //   warning: array subscript is above array bounds
  // in ByteCursor::Read(size_t, bool, T *) as it inlines this call, but
  // I'm not able to see how such a reference would occur.
  EXPECT_TRUE(cursor >> a);
  EXPECT_EQ(0xe953e4d2, a);
  EXPECT_FALSE(cursor >> a);
  EXPECT_FALSE(cursor >> a);
}

TEST(Extractor, Mixed) {
  uint8_t data[] = { 0x42,
                     0x25, 0x0b,
                     0x3d, 0x25, 0xed, 0x2a,
                     0xec, 0x16, 0x9e, 0x14, 0x61, 0x5b, 0x2c, 0xcf,
                     0xd8,
                     0x22, 0xa5,
                     0x3a, 0x02, 0x6a, 0xd7,
                     0x93, 0x2a, 0x2d, 0x8d, 0xb4, 0x95, 0xe0, 0xc6 };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);
  cursor.set_big_endian(true);

  uint8_t a;
  uint16_t b;
  uint32_t c;
  uint64_t d;
  int8_t e;
  int16_t f;
  int32_t g;
  int64_t h;
  int z;
  EXPECT_FALSE(cursor.AtEnd());
  EXPECT_TRUE(cursor >> a >> b >> c >> d >> e >> f >> g >> h);
  EXPECT_EQ(0x42U, a);
  EXPECT_EQ(0x250bU, b);
  EXPECT_EQ(0x3d25ed2aU, c);
  EXPECT_EQ(0xec169e14615b2ccfULL, d);
  EXPECT_EQ(-40, e);
  EXPECT_EQ(0x22a5, f);
  EXPECT_EQ(0x3a026ad7, g);
  EXPECT_EQ(-7842405714468937530LL, h);

  EXPECT_TRUE(cursor.AtEnd());
  EXPECT_FALSE(cursor >> z);
}

TEST(Strings, Zero) {
  uint8_t data[] = { 0xa6 };
  ByteBuffer buffer(data, 0);
  ByteCursor cursor(&buffer);

  uint8_t received[1];
  received[0] = 0xc2;
  EXPECT_TRUE(cursor.Read(received, 0));
  EXPECT_EQ(0xc2U, received[0]);
}

TEST(Strings, Some) {
  uint8_t data[] = { 0x5d, 0x31, 0x09, 0xa6, 0x2e, 0x2c, 0x83, 0xbb };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);

  uint8_t received[7] = { 0xa7, 0xf7, 0x43, 0x0c, 0x27, 0xea, 0xed };
  EXPECT_TRUE(cursor.Skip(2).Read(received, 5));
  uint8_t expected[7] = { 0x09, 0xa6, 0x2e, 0x2c, 0x83, 0xea, 0xed };
  EXPECT_TRUE(memcmp(received, expected, 7) == 0);
}

TEST(Strings, TooMuch) {
  uint8_t data[] = { 0x5d, 0x31, 0x09, 0xa6, 0x2e, 0x2c, 0x83, 0xbb };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);

  uint8_t received1[3];
  uint8_t received2[3];
  uint8_t received3[3];
  EXPECT_FALSE(cursor
               .Read(received1, 3)
               .Read(received2, 3)
               .Read(received3, 3));
  uint8_t expected1[3] = { 0x5d, 0x31, 0x09 };
  uint8_t expected2[3] = { 0xa6, 0x2e, 0x2c };

  EXPECT_TRUE(memcmp(received1, expected1, 3) == 0);
  EXPECT_TRUE(memcmp(received2, expected2, 3) == 0);
}

TEST(Strings, PointTo) {
  uint8_t data[] = { 0x83, 0x80, 0xb4, 0x38, 0x00, 0x2c, 0x0a, 0x27 };
  ByteBuffer buffer(data, sizeof(data));
  ByteCursor cursor(&buffer);

  const uint8_t *received1;
  const uint8_t *received2;
  const uint8_t *received3;
  const uint8_t *received4;
  EXPECT_FALSE(cursor
               .PointTo(&received1, 3)
               .PointTo(&received2, 3)
               .PointTo(&received3)
               .PointTo(&received4, 3));
  EXPECT_EQ(data + 0, received1);
  EXPECT_EQ(data + 3, received2);
  EXPECT_EQ(data + 6, received3);
  EXPECT_EQ(NULL, received4);
}

TEST(Strings, CString) {
  uint8_t data[] = "abc\0\0foo";
  ByteBuffer buffer(data, sizeof(data) - 1);  // don't include terminating '\0'
  ByteCursor cursor(&buffer);

  string a, b, c;
  EXPECT_TRUE(cursor.CString(&a).CString(&b));
  EXPECT_EQ("abc", a);
  EXPECT_EQ("", b);
  EXPECT_FALSE(cursor.CString(&c));
  EXPECT_EQ("", c);
  EXPECT_TRUE(cursor.AtEnd());
}

TEST(Strings, CStringLimit) {
  uint8_t data[] = "abcdef\0\0foobar";
  ByteBuffer buffer(data, sizeof(data) - 1);  // don't include terminating '\0'
  ByteCursor cursor(&buffer);

  string a, b, c, d, e;

  EXPECT_TRUE(cursor.CString(&a, 3));
  EXPECT_EQ("abc", a);

  EXPECT_TRUE(cursor.CString(&b, 0));
  EXPECT_EQ("", b);

  EXPECT_TRUE(cursor.CString(&c, 6));
  EXPECT_EQ("def", c);

  EXPECT_TRUE(cursor.CString(&d, 4));
  EXPECT_EQ("ooba", d);

  EXPECT_FALSE(cursor.CString(&e, 4));
  EXPECT_EQ("", e);

  EXPECT_TRUE(cursor.AtEnd());
}

//  uint8_t data[] = { 0xa6, 0x54, 0xdf, 0x67, 0x51, 0x43, 0xac, 0xf1 };
//  ByteBuffer buffer(data, sizeof(data));

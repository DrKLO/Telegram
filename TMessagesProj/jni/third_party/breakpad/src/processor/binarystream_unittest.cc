// Copyright (c) 2010, Google Inc.
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

#include <ios>
#include <string>
#include <vector>

#include "breakpad_googletest_includes.h"
#include "common/using_std_string.h"
#include "processor/binarystream.h"

namespace {
using std::ios_base;
using std::vector;
using google_breakpad::binarystream;


class BinaryStreamBasicTest : public ::testing::Test {
protected:
  binarystream stream;
};
 
TEST_F(BinaryStreamBasicTest, ReadU8) {
  uint8_t u8 = 0;
  ASSERT_FALSE(stream.eof());
  stream >> u8;
  ASSERT_TRUE(stream.eof());
  EXPECT_EQ(0U, u8);
  stream.rewind();
  stream.clear();
  stream << (uint8_t)1;
  ASSERT_FALSE(stream.eof());
  stream >> u8;
  EXPECT_EQ(1, u8);
  EXPECT_FALSE(stream.eof());
}

TEST_F(BinaryStreamBasicTest, ReadU16) {
  uint16_t u16 = 0;
  ASSERT_FALSE(stream.eof());
  stream >> u16;
  ASSERT_TRUE(stream.eof());
  EXPECT_EQ(0U, u16);
  stream.rewind();
  stream.clear();
  stream << (uint16_t)1;
  ASSERT_FALSE(stream.eof());
  stream >> u16;
  EXPECT_EQ(1, u16);
  EXPECT_FALSE(stream.eof());
}

TEST_F(BinaryStreamBasicTest, ReadU32) {
  uint32_t u32 = 0;
  ASSERT_FALSE(stream.eof());
  stream >> u32;
  ASSERT_TRUE(stream.eof());
  EXPECT_EQ(0U, u32);
  stream.rewind();
  stream.clear();
  stream << (uint32_t)1;
  ASSERT_FALSE(stream.eof());
  stream >> u32;
  EXPECT_EQ(1U, u32);
  EXPECT_FALSE(stream.eof());
}

TEST_F(BinaryStreamBasicTest, ReadU64) {
  uint64_t u64 = 0;
  ASSERT_FALSE(stream.eof());
  stream >> u64;
  ASSERT_TRUE(stream.eof());
  EXPECT_EQ(0U, u64);
  stream.rewind();
  stream.clear();
  stream << (uint64_t)1;
  ASSERT_FALSE(stream.eof());
  stream >> u64;
  EXPECT_EQ(1U, u64);
  EXPECT_FALSE(stream.eof());
}

TEST_F(BinaryStreamBasicTest, ReadString) {
  string s("");
  ASSERT_FALSE(stream.eof());
  stream >> s;
  ASSERT_TRUE(stream.eof());
  EXPECT_EQ("", s);
  // write an empty string to the stream, read it back
  s = "abcd";
  stream.rewind();
  stream.clear();
  stream << string("");
  stream >> s;
  EXPECT_EQ("", s);
  EXPECT_FALSE(stream.eof());
  stream.rewind();
  stream.clear();
  stream << string("test");
  ASSERT_FALSE(stream.eof());
  stream >> s;
  EXPECT_EQ("test", s);
  EXPECT_FALSE(stream.eof());
}

TEST_F(BinaryStreamBasicTest, ReadEmptyString) {
  string s("abc");
  stream << string("");
  stream >> s;
  EXPECT_EQ("", s);
}

TEST_F(BinaryStreamBasicTest, ReadMultiU8) {
  const uint8_t ea = 0, eb = 100, ec = 200, ed = 0xFF;
  uint8_t a, b, c, d, e;
  stream << ea << eb << ec << ed;
  stream >> a >> b >> c >> d;
  ASSERT_FALSE(stream.eof());
  EXPECT_EQ(ea, a);
  EXPECT_EQ(eb, b);
  EXPECT_EQ(ec, c);
  EXPECT_EQ(ed, d);
  ASSERT_FALSE(stream.eof());
  e = 0;
  stream >> e;
  EXPECT_EQ(0U, e);
  ASSERT_TRUE(stream.eof());
  // try reading all at once, including one past eof
  stream.rewind();
  stream.clear();
  ASSERT_FALSE(stream.eof());
  a = b = c = d = e = 0;
  stream << ea << eb << ec << ed;
  stream >> a >> b >> c >> d >> e;
  EXPECT_EQ(ea, a);
  EXPECT_EQ(eb, b);
  EXPECT_EQ(ec, c);
  EXPECT_EQ(ed, d);
  EXPECT_EQ(0U, e);
  EXPECT_TRUE(stream.eof());
}

TEST_F(BinaryStreamBasicTest, ReadMultiU16) {
  const uint16_t ea = 0, eb = 0x100, ec = 0x8000, ed = 0xFFFF;
  uint16_t a, b, c, d, e;
  stream << ea << eb << ec << ed;
  stream >> a >> b >> c >> d;
  ASSERT_FALSE(stream.eof());
  EXPECT_EQ(ea, a);
  EXPECT_EQ(eb, b);
  EXPECT_EQ(ec, c);
  EXPECT_EQ(ed, d);
  ASSERT_FALSE(stream.eof());
  e = 0;
  stream >> e;
  EXPECT_EQ(0U, e);
  EXPECT_TRUE(stream.eof());
  // try reading all at once, including one past eof
  stream.rewind();
  stream.clear();
  ASSERT_FALSE(stream.eof());
  a = b = c = d = e = 0;
  stream << ea << eb << ec << ed;
  stream >> a >> b >> c >> d >> e;
  EXPECT_EQ(ea, a);
  EXPECT_EQ(eb, b);
  EXPECT_EQ(ec, c);
  EXPECT_EQ(ed, d);
  EXPECT_EQ(0U, e);
  EXPECT_TRUE(stream.eof());
}

TEST_F(BinaryStreamBasicTest, ReadMultiU32) {
  const uint32_t ea = 0, eb = 0x10000, ec = 0x8000000, ed = 0xFFFFFFFF;
  uint32_t a, b, c, d, e;
  stream << ea << eb << ec << ed;
  stream >> a >> b >> c >> d;
  ASSERT_FALSE(stream.eof());
  EXPECT_EQ(ea, a);
  EXPECT_EQ(eb, b);
  EXPECT_EQ(ec, c);
  EXPECT_EQ(ed, d);
  ASSERT_FALSE(stream.eof());
  e = 0;
  stream >> e;
  EXPECT_EQ(0U, e);
  EXPECT_TRUE(stream.eof());
  // try reading all at once, including one past eof
  stream.rewind();
  stream.clear();
  ASSERT_FALSE(stream.eof());
  a = b = c = d = e = 0;
  stream << ea << eb << ec << ed;
  stream >> a >> b >> c >> d >> e;
  EXPECT_EQ(ea, a);
  EXPECT_EQ(eb, b);
  EXPECT_EQ(ec, c);
  EXPECT_EQ(ed, d);
  EXPECT_EQ(0U, e);
  EXPECT_TRUE(stream.eof());
}

TEST_F(BinaryStreamBasicTest, ReadMultiU64) {
  const uint64_t ea = 0, eb = 0x10000, ec = 0x100000000ULL,
    ed = 0xFFFFFFFFFFFFFFFFULL;
  uint64_t a, b, c, d, e;
  stream << ea << eb << ec << ed;
  stream >> a >> b >> c >> d;
  ASSERT_FALSE(stream.eof());
  EXPECT_EQ(ea, a);
  EXPECT_EQ(eb, b);
  EXPECT_EQ(ec, c);
  EXPECT_EQ(ed, d);
  ASSERT_FALSE(stream.eof());
  e = 0;
  stream >> e;
  EXPECT_EQ(0U, e);
  EXPECT_TRUE(stream.eof());
  // try reading all at once, including one past eof
  stream.rewind();
  stream.clear();
  ASSERT_FALSE(stream.eof());
  a = b = c = d = e = 0;
  stream << ea << eb << ec << ed;
  stream >> a >> b >> c >> d >> e;
  EXPECT_EQ(ea, a);
  EXPECT_EQ(eb, b);
  EXPECT_EQ(ec, c);
  EXPECT_EQ(ed, d);
  EXPECT_EQ(0U, e);
  EXPECT_TRUE(stream.eof());
}

TEST_F(BinaryStreamBasicTest, ReadMixed) {
  const uint8_t e8 = 0x10;
  const uint16_t e16 = 0x2020;
  const uint32_t e32 = 0x30303030;
  const uint64_t e64 = 0x4040404040404040ULL;
  const string es = "test";
  uint8_t u8 = 0;
  uint16_t u16 = 0;
  uint32_t u32 = 0;
  uint64_t u64 = 0;
  string s("test");
  stream << e8 << e16 << e32 << e64 << es;
  stream >> u8 >> u16 >> u32 >> u64 >> s;
  EXPECT_FALSE(stream.eof());
  EXPECT_EQ(e8, u8);
  EXPECT_EQ(e16, u16);
  EXPECT_EQ(e32, u32);
  EXPECT_EQ(e64, u64);
  EXPECT_EQ(es, s);
}

TEST_F(BinaryStreamBasicTest, ReadStringMissing) {
  // ensure that reading a string where only the length is present fails
  uint16_t u16 = 8;
  stream << u16;
  stream.rewind();
  string s("");
  stream >> s;
  EXPECT_EQ("", s);
  EXPECT_TRUE(stream.eof());
}

TEST_F(BinaryStreamBasicTest, ReadStringTruncated) {
  // ensure that reading a string where not all the data is present fails
  uint16_t u16 = 8;
  stream << u16;
  stream << (uint8_t)'t' << (uint8_t)'e' << (uint8_t)'s' << (uint8_t)'t';
  stream.rewind();
  string s("");
  stream >> s;
  EXPECT_EQ("", s);
  EXPECT_TRUE(stream.eof());
}

TEST_F(BinaryStreamBasicTest, StreamByteLength) {
  // Test that the stream buffer contains the right amount of data
  stream << (uint8_t)0 << (uint16_t)1 << (uint32_t)2 << (uint64_t)3
         << string("test");
  string s = stream.str();
  EXPECT_EQ(21U, s.length());
}

TEST_F(BinaryStreamBasicTest, AppendStreamResultsByteLength) {
  // Test that appending the str() results from two streams
  // gives the right byte length
  binarystream stream2;
  stream << (uint8_t)0 << (uint16_t)1;
  stream2 << (uint32_t)0 << (uint64_t)2
          << string("test");
  string s = stream.str();
  string s2 = stream2.str();
  s.append(s2);
  EXPECT_EQ(21U, s.length());
}

TEST_F(BinaryStreamBasicTest, StreamSetStr) {
  const string es("test");
  stream << es;
  binarystream stream2;
  stream2.str(stream.str());
  string s;
  stream2 >> s;
  EXPECT_FALSE(stream2.eof());
  EXPECT_EQ("test", s);
  s = "";
  stream2.str(stream.str());
  stream2.rewind();
  stream2 >> s;
  EXPECT_FALSE(stream2.eof());
  EXPECT_EQ("test", s);
}

class BinaryStreamU8Test : public ::testing::Test {
protected:
  binarystream stream;

  void SetUp() {
    stream << (uint8_t)1;
  }
};

TEST_F(BinaryStreamU8Test, ReadU16) {
  uint16_t u16 = 0;
  ASSERT_FALSE(stream.eof());
  stream >> u16;
  ASSERT_TRUE(stream.eof());
  EXPECT_EQ(0U, u16);
}

TEST_F(BinaryStreamU8Test, ReadU32) {
  uint32_t u32 = 0;
  ASSERT_FALSE(stream.eof());
  stream >> u32;
  ASSERT_TRUE(stream.eof());
  EXPECT_EQ(0U, u32);
}

TEST_F(BinaryStreamU8Test, ReadU64) {
  uint64_t u64 = 0;
  ASSERT_FALSE(stream.eof());
  stream >> u64;
  ASSERT_TRUE(stream.eof());
  EXPECT_EQ(0U, u64);
}

TEST_F(BinaryStreamU8Test, ReadString) {
  string s("");
  ASSERT_FALSE(stream.eof());
  stream >> s;
  ASSERT_TRUE(stream.eof());
  EXPECT_EQ("", s);
}


TEST(BinaryStreamTest, InitWithData) {
  const char *data = "abcd";
  binarystream stream(data);
  uint8_t a, b, c, d;
  stream >> a >> b >> c >> d;
  ASSERT_FALSE(stream.eof());
  EXPECT_EQ('a', a);
  EXPECT_EQ('b', b);
  EXPECT_EQ('c', c);
  EXPECT_EQ('d', d);
}

TEST(BinaryStreamTest, InitWithDataLeadingNull) {
  const char *data = "\0abcd";
  binarystream stream(data, 5);
  uint8_t z, a, b, c, d;
  stream >> z >> a >> b >> c >> d;
  ASSERT_FALSE(stream.eof());
  EXPECT_EQ(0U, z);
  EXPECT_EQ('a', a);
  EXPECT_EQ('b', b);
  EXPECT_EQ('c', c);
  EXPECT_EQ('d', d);
}

TEST(BinaryStreamTest, InitWithDataVector) {
  vector<char> data;
  data.push_back('a');
  data.push_back('b');
  data.push_back('c');
  data.push_back('d');
  data.push_back('e');
  data.resize(4);
  binarystream stream(&data[0], data.size());
  uint8_t a, b, c, d;
  stream >> a >> b >> c >> d;
  ASSERT_FALSE(stream.eof());
  EXPECT_EQ('a', a);
  EXPECT_EQ('b', b);
  EXPECT_EQ('c', c);
  EXPECT_EQ('d', d);
}

} // namespace

int main(int argc, char *argv[]) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}

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

#include <arpa/inet.h>
#include <limits.h>

#include <string>
#include <vector>

#include "common/using_std_string.h"
#include "processor/binarystream.h"

namespace google_breakpad {
using std::vector;

binarystream &binarystream::operator>>(string &str) {
  uint16_t length;
  *this >> length;
  if (eof())
    return *this;
  if (length == 0) {
    str.clear();
    return *this;
  }
  vector<char> buffer(length);
  stream_.read(&buffer[0], length);
  if (!eof())
    str.assign(&buffer[0], length);
  return *this;
}

binarystream &binarystream::operator>>(uint8_t &u8) {
  stream_.read((char *)&u8, 1);
  return *this;
}

binarystream &binarystream::operator>>(uint16_t &u16) {
  uint16_t temp;
  stream_.read((char *)&temp, 2);
  if (!eof())
    u16 = ntohs(temp);
  return *this;
}

binarystream &binarystream::operator>>(uint32_t &u32) {
  uint32_t temp;
  stream_.read((char *)&temp, 4);
  if (!eof())
    u32 = ntohl(temp);
  return *this;
}

binarystream &binarystream::operator>>(uint64_t &u64) {
  uint32_t lower, upper;
  *this >> lower >> upper;
  if (!eof())
    u64 = static_cast<uint64_t>(lower) | (static_cast<uint64_t>(upper) << 32);
  return *this;
}

binarystream &binarystream::operator<<(const string &str) {
  if (str.length() > USHRT_MAX) {
    // truncate to 16-bit length
    *this << static_cast<uint16_t>(USHRT_MAX);
    stream_.write(str.c_str(), USHRT_MAX);
  } else {
    *this << (uint16_t)(str.length() & 0xFFFF);
    stream_.write(str.c_str(), str.length());
  }
  return *this;
}

binarystream &binarystream::operator<<(uint8_t u8) {
  stream_.write((const char*)&u8, 1);
  return *this;
}

binarystream &binarystream::operator<<(uint16_t u16) {
  u16 = htons(u16);
  stream_.write((const char*)&u16, 2);
  return *this;
}

binarystream &binarystream::operator<<(uint32_t u32) {
  u32 = htonl(u32);
  stream_.write((const char*)&u32, 4);
  return *this;
}

binarystream &binarystream::operator<<(uint64_t u64) {
  // write 64-bit ints as two 32-bit ints, so we can byte-swap them easily
  uint32_t lower = static_cast<uint32_t>(u64 & 0xFFFFFFFF);
  uint32_t upper = static_cast<uint32_t>(u64 >> 32);
  *this << lower << upper;
  return *this;
}

}  // namespace google_breakpad

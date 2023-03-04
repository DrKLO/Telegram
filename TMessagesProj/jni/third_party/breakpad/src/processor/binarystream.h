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

// binarystream implements part of the std::iostream interface as a
// wrapper around std::stringstream to allow reading and writing strings
// and integers of known size.

#ifndef GOOGLE_BREAKPAD_PROCESSOR_BINARYSTREAM_H_
#define GOOGLE_BREAKPAD_PROCESSOR_BINARYSTREAM_H_

#include <sstream>
#include <string>

#include "common/using_std_string.h"
#include "google_breakpad/common/breakpad_types.h"

namespace google_breakpad {
using std::ios_base;
using std::ios;

class binarystream {
 public:
  explicit binarystream(ios_base::openmode which = ios_base::out|ios_base::in)
    : stream_(which) {}
  explicit binarystream(const string &str,
                        ios_base::openmode which = ios_base::out|ios_base::in)
    : stream_(str, which) {}
  explicit binarystream(const char *str, size_t size,
                        ios_base::openmode which = ios_base::out|ios_base::in)
    : stream_(string(str, size), which) {}

  binarystream &operator>>(string &str);
  binarystream &operator>>(uint8_t &u8);
  binarystream &operator>>(uint16_t &u16);
  binarystream &operator>>(uint32_t &u32);
  binarystream &operator>>(uint64_t &u64);

  // Note: strings are truncated at 65535 characters
  binarystream &operator<<(const string &str);
  binarystream &operator<<(uint8_t u8);
  binarystream &operator<<(uint16_t u16);
  binarystream &operator<<(uint32_t u32);
  binarystream &operator<<(uint64_t u64);

  // Forward a few methods directly from the stream object
  bool eof() const { return stream_.eof(); }
  void clear() { stream_.clear(); }
  string str() const { return stream_.str(); }
  void str(const string &s) { stream_.str(s); }
    
  // Seek both read and write pointers to the beginning of the stream.
  void rewind() {
    stream_.seekg (0, ios::beg);
    stream_.seekp (0, ios::beg);
    // This is to clear all the error flags, since only the EOF flag is cleared
    // with seekg().
    stream_.clear();
  }

 private:
  std::stringstream stream_;
};

}  // namespace google_breakpad

#endif  // GOOGLE_BREAKPAD_PROCESSOR_BINARYSTREAM_H_

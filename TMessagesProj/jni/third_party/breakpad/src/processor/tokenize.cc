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

#include <string.h>

#include <string>
#include <vector>

#include "common/using_std_string.h"

namespace google_breakpad {

#ifdef _WIN32
#define strtok_r strtok_s
#endif

using std::vector;

bool Tokenize(char *line,
	      const char *separators,
	      int max_tokens,
	      vector<char*> *tokens) {
  tokens->clear();
  tokens->reserve(max_tokens);

  int remaining = max_tokens;

  // Split tokens on the separator character.
  // strip them out before exhausting max_tokens.
  char *save_ptr;
  char *token = strtok_r(line, separators, &save_ptr);
  while (token && --remaining > 0) {
    tokens->push_back(token);
    if (remaining > 1)
      token = strtok_r(NULL, separators, &save_ptr);
  }

  // If there's anything left, just add it as a single token.
  if (remaining == 0 && (token = strtok_r(NULL, "\r\n", &save_ptr))) {
    tokens->push_back(token);
  }

  return tokens->size() == static_cast<unsigned int>(max_tokens);
}

void StringToVector(const string &str, vector<char> &vec) {
  vec.resize(str.length() + 1);
  std::copy(str.begin(), str.end(),
	    vec.begin());
  vec[str.length()] = '\0';
}

} // namespace google_breakpad

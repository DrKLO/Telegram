// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/unguessable_token.h"

#include "base/format_macros.h"
#include "base/no_destructor.h"
#include "base/rand_util.h"
#include "base/strings/stringprintf.h"

namespace base {

UnguessableToken::UnguessableToken(const base::Token& token) : token_(token) {}

// static
UnguessableToken UnguessableToken::Create() {
  return UnguessableToken(Token::CreateRandom());
}

// static
const UnguessableToken& UnguessableToken::Null() {
  static const NoDestructor<UnguessableToken> null_token;
  return *null_token;
}

// static
UnguessableToken UnguessableToken::Deserialize(uint64_t high, uint64_t low) {
  // Receiving a zeroed out UnguessableToken from another process means that it
  // was never initialized via Create(). Treat this case as a security issue.
  DCHECK(!(high == 0 && low == 0));
  return UnguessableToken(Token{high, low});
}

std::ostream& operator<<(std::ostream& out, const UnguessableToken& token) {
  return out << "(" << token.ToString() << ")";
}

}  // namespace base

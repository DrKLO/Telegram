// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/base64url.h"

#include <stddef.h>

#include "base/base64.h"
#include "base/macros.h"
#include "base/numerics/safe_math.h"
#include "base/strings/string_util.h"
#include "third_party/modp_b64/modp_b64.h"

namespace base {

const char kPaddingChar = '=';

// Base64url maps {+, /} to {-, _} in order for the encoded content to be safe
// to use in a URL. These characters will be translated by this implementation.
const char kBase64Chars[] = "+/";
const char kBase64UrlSafeChars[] = "-_";

void Base64UrlEncode(const StringPiece& input,
                     Base64UrlEncodePolicy policy,
                     std::string* output) {
  Base64Encode(input, output);

  ReplaceChars(*output, "+", "-", output);
  ReplaceChars(*output, "/", "_", output);

  switch (policy) {
    case Base64UrlEncodePolicy::INCLUDE_PADDING:
      // The padding included in |*output| will not be amended.
      break;
    case Base64UrlEncodePolicy::OMIT_PADDING:
      // The padding included in |*output| will be removed.
      const size_t last_non_padding_pos =
          output->find_last_not_of(kPaddingChar);
      if (last_non_padding_pos != std::string::npos)
        output->resize(last_non_padding_pos + 1);

      break;
  }
}

bool Base64UrlDecode(const StringPiece& input,
                     Base64UrlDecodePolicy policy,
                     std::string* output) {
  // Characters outside of the base64url alphabet are disallowed, which includes
  // the {+, /} characters found in the conventional base64 alphabet.
  if (input.find_first_of(kBase64Chars) != std::string::npos)
    return false;

  const size_t required_padding_characters = input.size() % 4;
  const bool needs_replacement =
      input.find_first_of(kBase64UrlSafeChars) != std::string::npos;

  switch (policy) {
    case Base64UrlDecodePolicy::REQUIRE_PADDING:
      // Fail if the required padding is not included in |input|.
      if (required_padding_characters > 0)
        return false;
      break;
    case Base64UrlDecodePolicy::IGNORE_PADDING:
      // Missing padding will be silently appended.
      break;
    case Base64UrlDecodePolicy::DISALLOW_PADDING:
      // Fail if padding characters are included in |input|.
      if (input.find_first_of(kPaddingChar) != std::string::npos)
        return false;
      break;
  }

  // If the string either needs replacement of URL-safe characters to normal
  // base64 ones, or additional padding, a copy of |input| needs to be made in
  // order to make these adjustments without side effects.
  if (required_padding_characters > 0 || needs_replacement) {
    std::string base64_input;

    CheckedNumeric<size_t> base64_input_size = input.size();
    if (required_padding_characters > 0)
      base64_input_size += 4 - required_padding_characters;

    base64_input.reserve(base64_input_size.ValueOrDie());
    base64_input.append(input.data(), input.size());

    // Substitute the base64url URL-safe characters to their base64 equivalents.
    ReplaceChars(base64_input, "-", "+", &base64_input);
    ReplaceChars(base64_input, "_", "/", &base64_input);

    // Append the necessary padding characters.
    base64_input.resize(base64_input_size.ValueOrDie(), '=');

    return Base64Decode(base64_input, output);
  }

  return Base64Decode(input, output);
}

}  // namespace base

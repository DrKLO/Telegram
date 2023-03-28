/*
 *  Copyright 2011 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/message_digest.h"

#include <string.h>

#include <cstdint>
#include <memory>

#include "absl/strings/string_view.h"
#include "rtc_base/openssl_digest.h"
#include "rtc_base/string_encode.h"

namespace rtc {

// From RFC 4572.
const char DIGEST_MD5[] = "md5";
const char DIGEST_SHA_1[] = "sha-1";
const char DIGEST_SHA_224[] = "sha-224";
const char DIGEST_SHA_256[] = "sha-256";
const char DIGEST_SHA_384[] = "sha-384";
const char DIGEST_SHA_512[] = "sha-512";

static const size_t kBlockSize = 64;  // valid for SHA-256 and down

MessageDigest* MessageDigestFactory::Create(absl::string_view alg) {
  MessageDigest* digest = new OpenSSLDigest(alg);
  if (digest->Size() == 0) {  // invalid algorithm
    delete digest;
    digest = nullptr;
  }
  return digest;
}

bool IsFips180DigestAlgorithm(absl::string_view alg) {
  // These are the FIPS 180 algorithms.  According to RFC 4572 Section 5,
  // "Self-signed certificates (for which legacy certificates are not a
  // consideration) MUST use one of the FIPS 180 algorithms (SHA-1,
  // SHA-224, SHA-256, SHA-384, or SHA-512) as their signature algorithm,
  // and thus also MUST use it to calculate certificate fingerprints."
  return alg == DIGEST_SHA_1 || alg == DIGEST_SHA_224 ||
         alg == DIGEST_SHA_256 || alg == DIGEST_SHA_384 ||
         alg == DIGEST_SHA_512;
}

size_t ComputeDigest(MessageDigest* digest,
                     const void* input,
                     size_t in_len,
                     void* output,
                     size_t out_len) {
  digest->Update(input, in_len);
  return digest->Finish(output, out_len);
}

size_t ComputeDigest(absl::string_view alg,
                     const void* input,
                     size_t in_len,
                     void* output,
                     size_t out_len) {
  std::unique_ptr<MessageDigest> digest(MessageDigestFactory::Create(alg));
  return (digest) ? ComputeDigest(digest.get(), input, in_len, output, out_len)
                  : 0;
}

std::string ComputeDigest(MessageDigest* digest, absl::string_view input) {
  std::unique_ptr<char[]> output(new char[digest->Size()]);
  ComputeDigest(digest, input.data(), input.size(), output.get(),
                digest->Size());
  return hex_encode(absl::string_view(output.get(), digest->Size()));
}

bool ComputeDigest(absl::string_view alg,
                   absl::string_view input,
                   std::string* output) {
  std::unique_ptr<MessageDigest> digest(MessageDigestFactory::Create(alg));
  if (!digest) {
    return false;
  }
  *output = ComputeDigest(digest.get(), input);
  return true;
}

std::string ComputeDigest(absl::string_view alg, absl::string_view input) {
  std::string output;
  ComputeDigest(alg, input, &output);
  return output;
}

// Compute a RFC 2104 HMAC: H(K XOR opad, H(K XOR ipad, text))
size_t ComputeHmac(MessageDigest* digest,
                   const void* key,
                   size_t key_len,
                   const void* input,
                   size_t in_len,
                   void* output,
                   size_t out_len) {
  // We only handle algorithms with a 64-byte blocksize.
  // TODO: Add BlockSize() method to MessageDigest.
  size_t block_len = kBlockSize;
  if (digest->Size() > 32) {
    return 0;
  }
  // Copy the key to a block-sized buffer to simplify padding.
  // If the key is longer than a block, hash it and use the result instead.
  std::unique_ptr<uint8_t[]> new_key(new uint8_t[block_len]);
  if (key_len > block_len) {
    ComputeDigest(digest, key, key_len, new_key.get(), block_len);
    memset(new_key.get() + digest->Size(), 0, block_len - digest->Size());
  } else {
    memcpy(new_key.get(), key, key_len);
    memset(new_key.get() + key_len, 0, block_len - key_len);
  }
  // Set up the padding from the key, salting appropriately for each padding.
  std::unique_ptr<uint8_t[]> o_pad(new uint8_t[block_len]);
  std::unique_ptr<uint8_t[]> i_pad(new uint8_t[block_len]);
  for (size_t i = 0; i < block_len; ++i) {
    o_pad[i] = 0x5c ^ new_key[i];
    i_pad[i] = 0x36 ^ new_key[i];
  }
  // Inner hash; hash the inner padding, and then the input buffer.
  std::unique_ptr<uint8_t[]> inner(new uint8_t[digest->Size()]);
  digest->Update(i_pad.get(), block_len);
  digest->Update(input, in_len);
  digest->Finish(inner.get(), digest->Size());
  // Outer hash; hash the outer padding, and then the result of the inner hash.
  digest->Update(o_pad.get(), block_len);
  digest->Update(inner.get(), digest->Size());
  return digest->Finish(output, out_len);
}

size_t ComputeHmac(absl::string_view alg,
                   const void* key,
                   size_t key_len,
                   const void* input,
                   size_t in_len,
                   void* output,
                   size_t out_len) {
  std::unique_ptr<MessageDigest> digest(MessageDigestFactory::Create(alg));
  if (!digest) {
    return 0;
  }
  return ComputeHmac(digest.get(), key, key_len, input, in_len, output,
                     out_len);
}

std::string ComputeHmac(MessageDigest* digest,
                        absl::string_view key,
                        absl::string_view input) {
  std::unique_ptr<char[]> output(new char[digest->Size()]);
  ComputeHmac(digest, key.data(), key.size(), input.data(), input.size(),
              output.get(), digest->Size());
  return hex_encode(absl::string_view(output.get(), digest->Size()));
}

bool ComputeHmac(absl::string_view alg,
                 absl::string_view key,
                 absl::string_view input,
                 std::string* output) {
  std::unique_ptr<MessageDigest> digest(MessageDigestFactory::Create(alg));
  if (!digest) {
    return false;
  }
  *output = ComputeHmac(digest.get(), key, input);
  return true;
}

std::string ComputeHmac(absl::string_view alg,
                        absl::string_view key,
                        absl::string_view input) {
  std::string output;
  ComputeHmac(alg, key, input, &output);
  return output;
}

}  // namespace rtc

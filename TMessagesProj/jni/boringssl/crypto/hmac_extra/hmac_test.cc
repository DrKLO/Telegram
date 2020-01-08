/* Copyright (C) 1995-1998 Eric Young (eay@cryptsoft.com)
 * All rights reserved.
 *
 * This package is an SSL implementation written
 * by Eric Young (eay@cryptsoft.com).
 * The implementation was written so as to conform with Netscapes SSL.
 *
 * This library is free for commercial and non-commercial use as long as
 * the following conditions are aheared to.  The following conditions
 * apply to all code found in this distribution, be it the RC4, RSA,
 * lhash, DES, etc., code; not just the SSL code.  The SSL documentation
 * included with this distribution is covered by the same copyright terms
 * except that the holder is Tim Hudson (tjh@cryptsoft.com).
 *
 * Copyright remains Eric Young's, and as such any Copyright notices in
 * the code are not to be removed.
 * If this package is used in a product, Eric Young should be given attribution
 * as the author of the parts of the library used.
 * This can be in the form of a textual message at program startup or
 * in documentation (online or textual) provided with the package.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *    "This product includes cryptographic software written by
 *     Eric Young (eay@cryptsoft.com)"
 *    The word 'cryptographic' can be left out if the rouines from the library
 *    being used are not cryptographic related :-).
 * 4. If you include any Windows specific code (or a derivative thereof) from
 *    the apps directory (application code) you must include an acknowledgement:
 *    "This product includes software written by Tim Hudson (tjh@cryptsoft.com)"
 *
 * THIS SOFTWARE IS PROVIDED BY ERIC YOUNG ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * The licence and distribution terms for any publically available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution licence
 * [including the GNU Public Licence.] */

#include <memory>
#include <string>
#include <vector>

#include <gtest/gtest.h>

#include <openssl/digest.h>
#include <openssl/hmac.h>

#include "../test/file_test.h"
#include "../test/test_util.h"


static const EVP_MD *GetDigest(const std::string &name) {
  if (name == "MD5") {
    return EVP_md5();
  } else if (name == "SHA1") {
    return EVP_sha1();
  } else if (name == "SHA224") {
    return EVP_sha224();
  } else if (name == "SHA256") {
    return EVP_sha256();
  } else if (name == "SHA384") {
    return EVP_sha384();
  } else if (name == "SHA512") {
    return EVP_sha512();
  }
  return nullptr;
}

TEST(HMACTest, TestVectors) {
  FileTestGTest("crypto/hmac_extra/hmac_tests.txt", [](FileTest *t) {
    std::string digest_str;
    ASSERT_TRUE(t->GetAttribute(&digest_str, "HMAC"));
    const EVP_MD *digest = GetDigest(digest_str);
    ASSERT_TRUE(digest) << "Unknown digest: " << digest_str;

    std::vector<uint8_t> key, input, output;
    ASSERT_TRUE(t->GetBytes(&key, "Key"));
    ASSERT_TRUE(t->GetBytes(&input, "Input"));
    ASSERT_TRUE(t->GetBytes(&output, "Output"));
    ASSERT_EQ(EVP_MD_size(digest), output.size());

    // Test using the one-shot API.
    unsigned expected_mac_len = EVP_MD_size(digest);
    std::unique_ptr<uint8_t[]> mac(new uint8_t[expected_mac_len]);
    unsigned mac_len;
    ASSERT_TRUE(HMAC(digest, key.data(), key.size(), input.data(), input.size(),
                     mac.get(), &mac_len));
    EXPECT_EQ(Bytes(output), Bytes(mac.get(), mac_len));

    // Test using HMAC_CTX.
    bssl::ScopedHMAC_CTX ctx;
    ASSERT_TRUE(
        HMAC_Init_ex(ctx.get(), key.data(), key.size(), digest, nullptr));
    ASSERT_TRUE(HMAC_Update(ctx.get(), input.data(), input.size()));
    ASSERT_TRUE(HMAC_Final(ctx.get(), mac.get(), &mac_len));
    EXPECT_EQ(Bytes(output), Bytes(mac.get(), mac_len));

    // Test that an HMAC_CTX may be reset with the same key.
    ASSERT_TRUE(HMAC_Init_ex(ctx.get(), nullptr, 0, digest, nullptr));
    ASSERT_TRUE(HMAC_Update(ctx.get(), input.data(), input.size()));
    ASSERT_TRUE(HMAC_Final(ctx.get(), mac.get(), &mac_len));
    EXPECT_EQ(Bytes(output), Bytes(mac.get(), mac_len));

    // Test feeding the input in byte by byte.
    ASSERT_TRUE(HMAC_Init_ex(ctx.get(), nullptr, 0, nullptr, nullptr));
    for (size_t i = 0; i < input.size(); i++) {
      ASSERT_TRUE(HMAC_Update(ctx.get(), &input[i], 1));
    }
    ASSERT_TRUE(HMAC_Final(ctx.get(), mac.get(), &mac_len));
    EXPECT_EQ(Bytes(output), Bytes(mac.get(), mac_len));
  });
}

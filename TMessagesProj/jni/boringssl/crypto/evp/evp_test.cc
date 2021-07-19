/*
 * Written by Dr Stephen N Henson (steve@openssl.org) for the OpenSSL
 * project.
 */
/* ====================================================================
 * Copyright (c) 2015 The OpenSSL Project.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All advertising materials mentioning features or use of this
 *    software must display the following acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit. (http://www.OpenSSL.org/)"
 *
 * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    licensing@OpenSSL.org.
 *
 * 5. Products derived from this software may not be called "OpenSSL"
 *    nor may "OpenSSL" appear in their names without prior written
 *    permission of the OpenSSL Project.
 *
 * 6. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit (http://www.OpenSSL.org/)"
 *
 * THIS SOFTWARE IS PROVIDED BY THE OpenSSL PROJECT ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 */

#include <openssl/evp.h>

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

OPENSSL_MSVC_PRAGMA(warning(push))
OPENSSL_MSVC_PRAGMA(warning(disable: 4702))

#include <map>
#include <string>
#include <utility>
#include <vector>

OPENSSL_MSVC_PRAGMA(warning(pop))

#include <gtest/gtest.h>

#include <openssl/buf.h>
#include <openssl/bytestring.h>
#include <openssl/crypto.h>
#include <openssl/digest.h>
#include <openssl/dsa.h>
#include <openssl/err.h>
#include <openssl/rsa.h>

#include "../test/file_test.h"
#include "../test/test_util.h"
#include "../test/wycheproof_util.h"


// evp_test dispatches between multiple test types. PrivateKey tests take a key
// name parameter and single block, decode it as a PEM private key, and save it
// under that key name. Decrypt, Sign, and Verify tests take a previously
// imported key name as parameter and test their respective operations.

static const EVP_MD *GetDigest(FileTest *t, const std::string &name) {
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
  ADD_FAILURE() << "Unknown digest: " << name;
  return nullptr;
}

static int GetKeyType(FileTest *t, const std::string &name) {
  if (name == "RSA") {
    return EVP_PKEY_RSA;
  }
  if (name == "EC") {
    return EVP_PKEY_EC;
  }
  if (name == "DSA") {
    return EVP_PKEY_DSA;
  }
  if (name == "Ed25519") {
    return EVP_PKEY_ED25519;
  }
  if (name == "X25519") {
    return EVP_PKEY_X25519;
  }
  ADD_FAILURE() << "Unknown key type: " << name;
  return EVP_PKEY_NONE;
}

static int GetRSAPadding(FileTest *t, int *out, const std::string &name) {
  if (name == "PKCS1") {
    *out = RSA_PKCS1_PADDING;
    return true;
  }
  if (name == "PSS") {
    *out = RSA_PKCS1_PSS_PADDING;
    return true;
  }
  if (name == "OAEP") {
    *out = RSA_PKCS1_OAEP_PADDING;
    return true;
  }
  ADD_FAILURE() << "Unknown RSA padding mode: " << name;
  return false;
}

using KeyMap = std::map<std::string, bssl::UniquePtr<EVP_PKEY>>;

static bool ImportKey(FileTest *t, KeyMap *key_map,
                      EVP_PKEY *(*parse_func)(CBS *cbs),
                      int (*marshal_func)(CBB *cbb, const EVP_PKEY *key)) {
  std::vector<uint8_t> input;
  if (!t->GetBytes(&input, "Input")) {
    return false;
  }

  CBS cbs;
  CBS_init(&cbs, input.data(), input.size());
  bssl::UniquePtr<EVP_PKEY> pkey(parse_func(&cbs));
  if (!pkey) {
    return false;
  }

  std::string key_type;
  if (!t->GetAttribute(&key_type, "Type")) {
    return false;
  }
  EXPECT_EQ(GetKeyType(t, key_type), EVP_PKEY_id(pkey.get()));

  // The key must re-encode correctly.
  bssl::ScopedCBB cbb;
  uint8_t *der;
  size_t der_len;
  if (!CBB_init(cbb.get(), 0) ||
      !marshal_func(cbb.get(), pkey.get()) ||
      !CBB_finish(cbb.get(), &der, &der_len)) {
    return false;
  }
  bssl::UniquePtr<uint8_t> free_der(der);

  std::vector<uint8_t> output = input;
  if (t->HasAttribute("Output") &&
      !t->GetBytes(&output, "Output")) {
    return false;
  }
  EXPECT_EQ(Bytes(output), Bytes(der, der_len))
      << "Re-encoding the key did not match.";

  if (t->HasAttribute("ExpectNoRawPrivate")) {
    size_t len;
    EXPECT_FALSE(EVP_PKEY_get_raw_private_key(pkey.get(), nullptr, &len));
  } else if (t->HasAttribute("ExpectRawPrivate")) {
    std::vector<uint8_t> expected;
    if (!t->GetBytes(&expected, "ExpectRawPrivate")) {
      return false;
    }

    std::vector<uint8_t> raw;
    size_t len;
    if (!EVP_PKEY_get_raw_private_key(pkey.get(), nullptr, &len)) {
      return false;
    }
    raw.resize(len);
    if (!EVP_PKEY_get_raw_private_key(pkey.get(), raw.data(), &len)) {
      return false;
    }
    raw.resize(len);
    EXPECT_EQ(Bytes(raw), Bytes(expected));

    // Short buffers should be rejected.
    raw.resize(len - 1);
    len = raw.size();
    EXPECT_FALSE(EVP_PKEY_get_raw_private_key(pkey.get(), raw.data(), &len));
  }

  if (t->HasAttribute("ExpectNoRawPublic")) {
    size_t len;
    EXPECT_FALSE(EVP_PKEY_get_raw_public_key(pkey.get(), nullptr, &len));
  } else if (t->HasAttribute("ExpectRawPublic")) {
    std::vector<uint8_t> expected;
    if (!t->GetBytes(&expected, "ExpectRawPublic")) {
      return false;
    }

    std::vector<uint8_t> raw;
    size_t len;
    if (!EVP_PKEY_get_raw_public_key(pkey.get(), nullptr, &len)) {
      return false;
    }
    raw.resize(len);
    if (!EVP_PKEY_get_raw_public_key(pkey.get(), raw.data(), &len)) {
      return false;
    }
    raw.resize(len);
    EXPECT_EQ(Bytes(raw), Bytes(expected));

    // Short buffers should be rejected.
    raw.resize(len - 1);
    len = raw.size();
    EXPECT_FALSE(EVP_PKEY_get_raw_public_key(pkey.get(), raw.data(), &len));
  }

  // Save the key for future tests.
  const std::string &key_name = t->GetParameter();
  EXPECT_EQ(0u, key_map->count(key_name)) << "Duplicate key: " << key_name;
  (*key_map)[key_name] = std::move(pkey);
  return true;
}

// SetupContext configures |ctx| based on attributes in |t|, with the exception
// of the signing digest which must be configured externally.
static bool SetupContext(FileTest *t, KeyMap *key_map, EVP_PKEY_CTX *ctx) {
  if (t->HasAttribute("RSAPadding")) {
    int padding;
    if (!GetRSAPadding(t, &padding, t->GetAttributeOrDie("RSAPadding")) ||
        !EVP_PKEY_CTX_set_rsa_padding(ctx, padding)) {
      return false;
    }
  }
  if (t->HasAttribute("PSSSaltLength") &&
      !EVP_PKEY_CTX_set_rsa_pss_saltlen(
          ctx, atoi(t->GetAttributeOrDie("PSSSaltLength").c_str()))) {
    return false;
  }
  if (t->HasAttribute("MGF1Digest")) {
    const EVP_MD *digest = GetDigest(t, t->GetAttributeOrDie("MGF1Digest"));
    if (digest == nullptr || !EVP_PKEY_CTX_set_rsa_mgf1_md(ctx, digest)) {
      return false;
    }
  }
  if (t->HasAttribute("OAEPDigest")) {
    const EVP_MD *digest = GetDigest(t, t->GetAttributeOrDie("OAEPDigest"));
    if (digest == nullptr || !EVP_PKEY_CTX_set_rsa_oaep_md(ctx, digest)) {
      return false;
    }
  }
  if (t->HasAttribute("OAEPLabel")) {
    std::vector<uint8_t> label;
    if (!t->GetBytes(&label, "OAEPLabel")) {
      return false;
    }
    // For historical reasons, |EVP_PKEY_CTX_set0_rsa_oaep_label| expects to be
    // take ownership of the input.
    bssl::UniquePtr<uint8_t> buf(
        reinterpret_cast<uint8_t *>(BUF_memdup(label.data(), label.size())));
    if (!buf ||
        !EVP_PKEY_CTX_set0_rsa_oaep_label(ctx, buf.get(), label.size())) {
      return false;
    }
    buf.release();
  }
  if (t->HasAttribute("DerivePeer")) {
    std::string derive_peer = t->GetAttributeOrDie("DerivePeer");
    if (key_map->count(derive_peer) == 0) {
      ADD_FAILURE() << "Could not find key " << derive_peer;
      return false;
    }
    EVP_PKEY *derive_peer_key = (*key_map)[derive_peer].get();
    if (!EVP_PKEY_derive_set_peer(ctx, derive_peer_key)) {
      return false;
    }
  }
  return true;
}

static bool TestDerive(FileTest *t, KeyMap *key_map, EVP_PKEY *key) {
  bssl::UniquePtr<EVP_PKEY_CTX> ctx(EVP_PKEY_CTX_new(key, nullptr));
  if (!ctx ||
      !EVP_PKEY_derive_init(ctx.get()) ||
      !SetupContext(t, key_map, ctx.get())) {
    return false;
  }

  bssl::UniquePtr<EVP_PKEY_CTX> copy(EVP_PKEY_CTX_dup(ctx.get()));
  if (!copy) {
    return false;
  }

  for (EVP_PKEY_CTX *pctx : {ctx.get(), copy.get()}) {
    size_t len;
    std::vector<uint8_t> actual, output;
    if (!EVP_PKEY_derive(pctx, nullptr, &len)) {
      return false;
    }
    actual.resize(len);
    if (!EVP_PKEY_derive(pctx, actual.data(), &len)) {
      return false;
    }
    actual.resize(len);

    // Defer looking up the attribute so Error works properly.
    if (!t->GetBytes(&output, "Output")) {
      return false;
    }
    EXPECT_EQ(Bytes(output), Bytes(actual));

    // Test when the buffer is too large.
    actual.resize(len + 1);
    len = actual.size();
    if (!EVP_PKEY_derive(pctx, actual.data(), &len)) {
      return false;
    }
    actual.resize(len);
    EXPECT_EQ(Bytes(output), Bytes(actual));

    // Test when the buffer is too small.
    actual.resize(len - 1);
    len = actual.size();
    if (t->HasAttribute("SmallBufferTruncates")) {
      if (!EVP_PKEY_derive(pctx, actual.data(), &len)) {
        return false;
      }
      actual.resize(len);
      EXPECT_EQ(Bytes(output.data(), len), Bytes(actual));
    } else {
      EXPECT_FALSE(EVP_PKEY_derive(pctx, actual.data(), &len));
      ERR_clear_error();
    }
  }
  return true;
}

static bool TestEVP(FileTest *t, KeyMap *key_map) {
  if (t->GetType() == "PrivateKey") {
    return ImportKey(t, key_map, EVP_parse_private_key,
                     EVP_marshal_private_key);
  }

  if (t->GetType() == "PublicKey") {
    return ImportKey(t, key_map, EVP_parse_public_key, EVP_marshal_public_key);
  }

  // Load the key.
  const std::string &key_name = t->GetParameter();
  if (key_map->count(key_name) == 0) {
    ADD_FAILURE() << "Could not find key " << key_name;
    return false;
  }
  EVP_PKEY *key = (*key_map)[key_name].get();

  int (*key_op_init)(EVP_PKEY_CTX *ctx) = nullptr;
  int (*key_op)(EVP_PKEY_CTX *ctx, uint8_t *out, size_t *out_len,
                const uint8_t *in, size_t in_len) = nullptr;
  int (*md_op_init)(EVP_MD_CTX * ctx, EVP_PKEY_CTX * *pctx, const EVP_MD *type,
                    ENGINE *e, EVP_PKEY *pkey) = nullptr;
  bool is_verify = false;
  if (t->GetType() == "Decrypt") {
    key_op_init = EVP_PKEY_decrypt_init;
    key_op = EVP_PKEY_decrypt;
  } else if (t->GetType() == "Sign") {
    key_op_init = EVP_PKEY_sign_init;
    key_op = EVP_PKEY_sign;
  } else if (t->GetType() == "Verify") {
    key_op_init = EVP_PKEY_verify_init;
    is_verify = true;
  } else if (t->GetType() == "SignMessage") {
    md_op_init = EVP_DigestSignInit;
  } else if (t->GetType() == "VerifyMessage") {
    md_op_init = EVP_DigestVerifyInit;
    is_verify = true;
  } else if (t->GetType() == "Encrypt") {
    key_op_init = EVP_PKEY_encrypt_init;
    key_op = EVP_PKEY_encrypt;
  } else if (t->GetType() == "Derive") {
    return TestDerive(t, key_map, key);
  } else {
    ADD_FAILURE() << "Unknown test " << t->GetType();
    return false;
  }

  const EVP_MD *digest = nullptr;
  if (t->HasAttribute("Digest")) {
    digest = GetDigest(t, t->GetAttributeOrDie("Digest"));
    if (digest == nullptr) {
      return false;
    }
  }

  // For verify tests, the "output" is the signature. Read it now so that, for
  // tests which expect a failure in SetupContext, the attribute is still
  // consumed.
  std::vector<uint8_t> input, actual, output;
  if (!t->GetBytes(&input, "Input") ||
      (is_verify && !t->GetBytes(&output, "Output"))) {
    return false;
  }

  if (md_op_init) {
    bssl::ScopedEVP_MD_CTX ctx, copy;
    EVP_PKEY_CTX *pctx;
    if (!md_op_init(ctx.get(), &pctx, digest, nullptr, key) ||
        !SetupContext(t, key_map, pctx) ||
        !EVP_MD_CTX_copy_ex(copy.get(), ctx.get())) {
      return false;
    }

    if (is_verify) {
      return EVP_DigestVerify(ctx.get(), output.data(), output.size(),
                              input.data(), input.size()) &&
             EVP_DigestVerify(copy.get(), output.data(), output.size(),
                              input.data(), input.size());
    }

    size_t len;
    if (!EVP_DigestSign(ctx.get(), nullptr, &len, input.data(), input.size())) {
      return false;
    }
    actual.resize(len);
    if (!EVP_DigestSign(ctx.get(), actual.data(), &len, input.data(),
                        input.size()) ||
        !t->GetBytes(&output, "Output")) {
      return false;
    }
    actual.resize(len);
    EXPECT_EQ(Bytes(output), Bytes(actual));

    // Repeat the test with |copy|, to check |EVP_MD_CTX_copy_ex| duplicated
    // everything.
    if (!EVP_DigestSign(copy.get(), nullptr, &len, input.data(),
                        input.size())) {
      return false;
    }
    actual.resize(len);
    if (!EVP_DigestSign(copy.get(), actual.data(), &len, input.data(),
                        input.size()) ||
        !t->GetBytes(&output, "Output")) {
      return false;
    }
    actual.resize(len);
    EXPECT_EQ(Bytes(output), Bytes(actual));
    return true;
  }

  bssl::UniquePtr<EVP_PKEY_CTX> ctx(EVP_PKEY_CTX_new(key, nullptr));
  if (!ctx ||
      !key_op_init(ctx.get()) ||
      (digest != nullptr &&
       !EVP_PKEY_CTX_set_signature_md(ctx.get(), digest)) ||
      !SetupContext(t, key_map, ctx.get())) {
    return false;
  }

  bssl::UniquePtr<EVP_PKEY_CTX> copy(EVP_PKEY_CTX_dup(ctx.get()));
  if (!copy) {
    return false;
  }

  if (is_verify) {
    return EVP_PKEY_verify(ctx.get(), output.data(), output.size(),
                           input.data(), input.size()) &&
           EVP_PKEY_verify(copy.get(), output.data(), output.size(),
                           input.data(), input.size());
  }

  for (EVP_PKEY_CTX *pctx : {ctx.get(), copy.get()}) {
    size_t len;
    if (!key_op(pctx, nullptr, &len, input.data(), input.size())) {
      return false;
    }
    actual.resize(len);
    if (!key_op(pctx, actual.data(), &len, input.data(), input.size())) {
      return false;
    }

    if (t->HasAttribute("CheckDecrypt")) {
      // Encryption is non-deterministic, so we check by decrypting.
      size_t plaintext_len;
      bssl::UniquePtr<EVP_PKEY_CTX> decrypt_ctx(EVP_PKEY_CTX_new(key, nullptr));
      if (!decrypt_ctx ||
          !EVP_PKEY_decrypt_init(decrypt_ctx.get()) ||
          (digest != nullptr &&
           !EVP_PKEY_CTX_set_signature_md(decrypt_ctx.get(), digest)) ||
          !SetupContext(t, key_map, decrypt_ctx.get()) ||
          !EVP_PKEY_decrypt(decrypt_ctx.get(), nullptr, &plaintext_len,
                            actual.data(), actual.size())) {
        return false;
      }
      output.resize(plaintext_len);
      if (!EVP_PKEY_decrypt(decrypt_ctx.get(), output.data(), &plaintext_len,
                            actual.data(), actual.size())) {
        ADD_FAILURE() << "Could not decrypt result.";
        return false;
      }
      output.resize(plaintext_len);
      EXPECT_EQ(Bytes(input), Bytes(output)) << "Decrypted result mismatch.";
    } else if (t->HasAttribute("CheckVerify")) {
      // Some signature schemes are non-deterministic, so we check by verifying.
      bssl::UniquePtr<EVP_PKEY_CTX> verify_ctx(EVP_PKEY_CTX_new(key, nullptr));
      if (!verify_ctx ||
          !EVP_PKEY_verify_init(verify_ctx.get()) ||
          (digest != nullptr &&
           !EVP_PKEY_CTX_set_signature_md(verify_ctx.get(), digest)) ||
          !SetupContext(t, key_map, verify_ctx.get())) {
        return false;
      }
      if (t->HasAttribute("VerifyPSSSaltLength")) {
        if (!EVP_PKEY_CTX_set_rsa_pss_saltlen(
                verify_ctx.get(),
                atoi(t->GetAttributeOrDie("VerifyPSSSaltLength").c_str()))) {
          return false;
        }
      }
      EXPECT_TRUE(EVP_PKEY_verify(verify_ctx.get(), actual.data(),
                                  actual.size(), input.data(), input.size()))
          << "Could not verify result.";
    } else {
      // By default, check by comparing the result against Output.
      if (!t->GetBytes(&output, "Output")) {
        return false;
      }
      actual.resize(len);
      EXPECT_EQ(Bytes(output), Bytes(actual));
    }
  }
  return true;
}

TEST(EVPTest, TestVectors) {
  KeyMap key_map;
  FileTestGTest("crypto/evp/evp_tests.txt", [&](FileTest *t) {
    bool result = TestEVP(t, &key_map);
    if (t->HasAttribute("Error")) {
      ASSERT_FALSE(result) << "Operation unexpectedly succeeded.";
      uint32_t err = ERR_peek_error();
      EXPECT_EQ(t->GetAttributeOrDie("Error"), ERR_reason_error_string(err));
    } else if (!result) {
      ADD_FAILURE() << "Operation unexpectedly failed.";
    }
  });
}

static void RunWycheproofTest(const char *path) {
  SCOPED_TRACE(path);
  FileTestGTest(path, [](FileTest *t) {
    t->IgnoreInstruction("key.type");
    // Extra ECDSA fields.
    t->IgnoreInstruction("key.curve");
    t->IgnoreInstruction("key.keySize");
    t->IgnoreInstruction("key.wx");
    t->IgnoreInstruction("key.wy");
    t->IgnoreInstruction("key.uncompressed");
    // Extra RSA fields.
    t->IgnoreInstruction("e");
    t->IgnoreInstruction("keyAsn");
    t->IgnoreInstruction("keysize");
    t->IgnoreInstruction("n");
    t->IgnoreAttribute("padding");
    t->IgnoreInstruction("keyJwk.alg");
    t->IgnoreInstruction("keyJwk.e");
    t->IgnoreInstruction("keyJwk.kid");
    t->IgnoreInstruction("keyJwk.kty");
    t->IgnoreInstruction("keyJwk.n");
    // Extra EdDSA fields.
    t->IgnoreInstruction("key.pk");
    t->IgnoreInstruction("key.sk");
    t->IgnoreInstruction("jwk.crv");
    t->IgnoreInstruction("jwk.d");
    t->IgnoreInstruction("jwk.kid");
    t->IgnoreInstruction("jwk.kty");
    t->IgnoreInstruction("jwk.x");
    // Extra DSA fields.
    t->IgnoreInstruction("key.g");
    t->IgnoreInstruction("key.p");
    t->IgnoreInstruction("key.q");
    t->IgnoreInstruction("key.y");

    std::vector<uint8_t> der;
    ASSERT_TRUE(t->GetInstructionBytes(&der, "keyDer"));
    CBS cbs;
    CBS_init(&cbs, der.data(), der.size());
    bssl::UniquePtr<EVP_PKEY> key(EVP_parse_public_key(&cbs));
    ASSERT_TRUE(key);

    const EVP_MD *md = nullptr;
    if (t->HasInstruction("sha")) {
      md = GetWycheproofDigest(t, "sha", true);
      ASSERT_TRUE(md);
    }

    bool is_pss = t->HasInstruction("mgf");
    const EVP_MD *mgf1_md = nullptr;
    int pss_salt_len = -1;
    if (is_pss) {
      ASSERT_EQ("MGF1", t->GetInstructionOrDie("mgf"));
      mgf1_md = GetWycheproofDigest(t, "mgfSha", true);

      std::string s_len;
      ASSERT_TRUE(t->GetInstruction(&s_len, "sLen"));
      pss_salt_len = atoi(s_len.c_str());
    }

    std::vector<uint8_t> msg;
    ASSERT_TRUE(t->GetBytes(&msg, "msg"));
    std::vector<uint8_t> sig;
    ASSERT_TRUE(t->GetBytes(&sig, "sig"));
    WycheproofResult result;
    ASSERT_TRUE(GetWycheproofResult(t, &result));

    if (EVP_PKEY_id(key.get()) == EVP_PKEY_DSA) {
      // DSA is deprecated and is not usable via EVP.
      DSA *dsa = EVP_PKEY_get0_DSA(key.get());
      uint8_t digest[EVP_MAX_MD_SIZE];
      unsigned digest_len;
      ASSERT_TRUE(
          EVP_Digest(msg.data(), msg.size(), digest, &digest_len, md, nullptr));
      int valid;
      bool sig_ok = DSA_check_signature(&valid, digest, digest_len, sig.data(),
                                        sig.size(), dsa) &&
                    valid;
      if (result == WycheproofResult::kValid) {
        EXPECT_TRUE(sig_ok);
      } else if (result == WycheproofResult::kInvalid) {
        EXPECT_FALSE(sig_ok);
      } else {
        // this is a legacy signature, which may or may not be accepted.
      }
    } else {
      bssl::ScopedEVP_MD_CTX ctx;
      EVP_PKEY_CTX *pctx;
      ASSERT_TRUE(
          EVP_DigestVerifyInit(ctx.get(), &pctx, md, nullptr, key.get()));
      if (is_pss) {
        ASSERT_TRUE(EVP_PKEY_CTX_set_rsa_padding(pctx, RSA_PKCS1_PSS_PADDING));
        ASSERT_TRUE(EVP_PKEY_CTX_set_rsa_mgf1_md(pctx, mgf1_md));
        ASSERT_TRUE(EVP_PKEY_CTX_set_rsa_pss_saltlen(pctx, pss_salt_len));
      }
      int ret = EVP_DigestVerify(ctx.get(), sig.data(), sig.size(), msg.data(),
                                 msg.size());
      if (result == WycheproofResult::kValid) {
        EXPECT_EQ(1, ret);
      } else if (result == WycheproofResult::kInvalid) {
        EXPECT_EQ(0, ret);
      } else {
        // this is a legacy signature, which may or may not be accepted.
        EXPECT_TRUE(ret == 1 || ret == 0);
      }
    }
  });
}

TEST(EVPTest, WycheproofDSA) {
  RunWycheproofTest("third_party/wycheproof_testvectors/dsa_test.txt");
}

TEST(EVPTest, WycheproofECDSAP224) {
  RunWycheproofTest(
      "third_party/wycheproof_testvectors/ecdsa_secp224r1_sha224_test.txt");
  RunWycheproofTest(
      "third_party/wycheproof_testvectors/ecdsa_secp224r1_sha256_test.txt");
  RunWycheproofTest(
      "third_party/wycheproof_testvectors/ecdsa_secp224r1_sha512_test.txt");
}

TEST(EVPTest, WycheproofECDSAP256) {
  RunWycheproofTest(
      "third_party/wycheproof_testvectors/ecdsa_secp256r1_sha256_test.txt");
  RunWycheproofTest(
      "third_party/wycheproof_testvectors/ecdsa_secp256r1_sha512_test.txt");
}

TEST(EVPTest, WycheproofECDSAP384) {
  RunWycheproofTest(
      "third_party/wycheproof_testvectors/ecdsa_secp384r1_sha384_test.txt");
}

TEST(EVPTest, WycheproofECDSAP521) {
  RunWycheproofTest(
      "third_party/wycheproof_testvectors/ecdsa_secp384r1_sha512_test.txt");
  RunWycheproofTest(
      "third_party/wycheproof_testvectors/ecdsa_secp521r1_sha512_test.txt");
}

TEST(EVPTest, WycheproofEdDSA) {
  RunWycheproofTest("third_party/wycheproof_testvectors/eddsa_test.txt");
}

TEST(EVPTest, WycheproofRSAPKCS1) {
  RunWycheproofTest(
      "third_party/wycheproof_testvectors/rsa_signature_test.txt");
}

TEST(EVPTest, WycheproofRSAPSS) {
  RunWycheproofTest(
      "third_party/wycheproof_testvectors/rsa_pss_2048_sha1_mgf1_20_test.txt");
  RunWycheproofTest(
      "third_party/wycheproof_testvectors/rsa_pss_2048_sha256_mgf1_0_test.txt");
  RunWycheproofTest(
      "third_party/wycheproof_testvectors/"
      "rsa_pss_2048_sha256_mgf1_32_test.txt");
  RunWycheproofTest(
      "third_party/wycheproof_testvectors/"
      "rsa_pss_3072_sha256_mgf1_32_test.txt");
  RunWycheproofTest(
      "third_party/wycheproof_testvectors/"
      "rsa_pss_4096_sha256_mgf1_32_test.txt");
  RunWycheproofTest(
      "third_party/wycheproof_testvectors/"
      "rsa_pss_4096_sha512_mgf1_32_test.txt");
  RunWycheproofTest("third_party/wycheproof_testvectors/rsa_pss_misc_test.txt");
}

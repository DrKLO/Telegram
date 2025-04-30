// Copyright 2024 The BoringSSL Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <openssl/mldsa.h>

#include <memory>
#include <vector>

#include <gtest/gtest.h>

#include <openssl/bytestring.h>
#include <openssl/mem.h>
#include <openssl/span.h>

#include "../fipsmodule/bcm_interface.h"
#include "../internal.h"
#include "../test/file_test.h"
#include "../test/test_util.h"


namespace {

template <typename T>
std::vector<uint8_t> Marshal(bcm_status (*marshal_func)(CBB *, const T *),
                             const T *t) {
  bssl::ScopedCBB cbb;
  uint8_t *encoded;
  size_t encoded_len;
  if (!CBB_init(cbb.get(), 1) ||                             //
      marshal_func(cbb.get(), t) != bcm_status::approved ||  //
      !CBB_finish(cbb.get(), &encoded, &encoded_len)) {
    abort();
  }

  std::vector<uint8_t> ret(encoded, encoded + encoded_len);
  OPENSSL_free(encoded);
  return ret;
}

// This test is very slow, so it is disabled by default.
TEST(MLDSATest, DISABLED_BitFlips) {
  std::vector<uint8_t> encoded_public_key(MLDSA65_PUBLIC_KEY_BYTES);
  auto priv = std::make_unique<MLDSA65_private_key>();
  uint8_t seed[MLDSA_SEED_BYTES];
  EXPECT_TRUE(
      MLDSA65_generate_key(encoded_public_key.data(), seed, priv.get()));

  std::vector<uint8_t> encoded_signature(MLDSA65_SIGNATURE_BYTES);
  static const uint8_t kMessage[] = {'H', 'e', 'l', 'l', 'o', ' ',
                                     'w', 'o', 'r', 'l', 'd'};
  EXPECT_TRUE(MLDSA65_sign(encoded_signature.data(), priv.get(), kMessage,
                           sizeof(kMessage), nullptr, 0));

  auto pub = std::make_unique<MLDSA65_public_key>();
  CBS cbs = CBS(encoded_public_key);
  ASSERT_TRUE(MLDSA65_parse_public_key(pub.get(), &cbs));

  EXPECT_EQ(MLDSA65_verify(pub.get(), encoded_signature.data(),
                           encoded_signature.size(), kMessage, sizeof(kMessage),
                           nullptr, 0),
            1);

  for (size_t i = 0; i < MLDSA65_SIGNATURE_BYTES; i++) {
    for (int j = 0; j < 8; j++) {
      encoded_signature[i] ^= 1 << j;
      EXPECT_EQ(MLDSA65_verify(pub.get(), encoded_signature.data(),
                               encoded_signature.size(), kMessage,
                               sizeof(kMessage), nullptr, 0),
                0)
          << "Bit flip in signature at byte " << i << " bit " << j
          << " didn't cause a verification failure";
      encoded_signature[i] ^= 1 << j;
    }
  }
}

template <
    typename PrivateKey, typename PublicKey, size_t PublicKeyBytes,
    size_t SignatureBytes, int (*Generate)(uint8_t *, uint8_t *, PrivateKey *),
    int (*Sign)(uint8_t *, const PrivateKey *, const uint8_t *, size_t,
                const uint8_t *, size_t),
    int (*ParsePublicKey)(PublicKey *, CBS *),
    int (*Verify)(const PublicKey *, const uint8_t *, size_t, const uint8_t *,
                  size_t, const uint8_t *, size_t),
    int (*PrivateKeyFromSeed)(PrivateKey *, const uint8_t *, size_t),
    typename BCMPrivateKey, bcm_status (*ParsePrivate)(BCMPrivateKey *, CBS *),
    bcm_status (*MarshalPrivate)(CBB *, const BCMPrivateKey *)>
static void MLDSABasicTest() {
  std::vector<uint8_t> encoded_public_key(PublicKeyBytes);
  auto priv = std::make_unique<PrivateKey>();
  uint8_t seed[MLDSA_SEED_BYTES];
  EXPECT_TRUE(Generate(encoded_public_key.data(), seed, priv.get()));

  const std::vector<uint8_t> encoded_private_key =
      Marshal(MarshalPrivate, reinterpret_cast<BCMPrivateKey *>(priv.get()));
  CBS cbs = CBS(encoded_private_key);
  EXPECT_TRUE(bcm_success(
      ParsePrivate(reinterpret_cast<BCMPrivateKey *>(priv.get()), &cbs)));

  std::vector<uint8_t> encoded_signature(SignatureBytes);
  static const uint8_t kMessage[] = {'H', 'e', 'l', 'l', 'o', ' ',
                                     'w', 'o', 'r', 'l', 'd'};
  static const uint8_t kContext[] = {'c', 't', 'x'};
  EXPECT_TRUE(Sign(encoded_signature.data(), priv.get(), kMessage,
                   sizeof(kMessage), kContext, sizeof(kContext)));

  auto pub = std::make_unique<PublicKey>();
  cbs = CBS(encoded_public_key);
  ASSERT_TRUE(ParsePublicKey(pub.get(), &cbs));

  EXPECT_EQ(
      Verify(pub.get(), encoded_signature.data(), encoded_signature.size(),
             kMessage, sizeof(kMessage), kContext, sizeof(kContext)),
      1);

  auto priv2 = std::make_unique<PrivateKey>();
  EXPECT_TRUE(PrivateKeyFromSeed(priv2.get(), seed, sizeof(seed)));

  EXPECT_EQ(
      Bytes(Declassified(Marshal(
          MarshalPrivate, reinterpret_cast<BCMPrivateKey *>(priv.get())))),
      Bytes(Declassified(Marshal(
          MarshalPrivate, reinterpret_cast<BCMPrivateKey *>(priv2.get())))));
}

TEST(MLDSATest, Basic65) {
  MLDSABasicTest<MLDSA65_private_key, MLDSA65_public_key,
                 MLDSA65_PUBLIC_KEY_BYTES, MLDSA65_SIGNATURE_BYTES,
                 MLDSA65_generate_key, MLDSA65_sign, MLDSA65_parse_public_key,
                 MLDSA65_verify, MLDSA65_private_key_from_seed,
                 BCM_mldsa65_private_key, BCM_mldsa65_parse_private_key,
                 BCM_mldsa65_marshal_private_key>();
}

// These are the wrapper functions needed for `MLDSABasicTest`. ML-DSA-87 isn't
// publicly exposed yet, so they are included here. It's good to exercise the
// ML-DSA-65 wrapper functions so that they aren't untested (even if they are
// quite trivial) thus `MLDSABasicTest` is done this way around.

struct MLDSA87_private_key {
  BCM_mldsa87_private_key priv;
};

struct MLDSA87_public_key {
  BCM_mldsa87_public_key pub;
};

static int MLDSA87_generate_key(
    uint8_t out_encoded_public_key[BCM_MLDSA87_PUBLIC_KEY_BYTES],
    uint8_t out_seed[MLDSA_SEED_BYTES],
    struct MLDSA87_private_key *out_private_key) {
  return bcm_success(BCM_mldsa87_generate_key(
      out_encoded_public_key, out_seed,
      reinterpret_cast<BCM_mldsa87_private_key *>(out_private_key)));
}

static int MLDSA87_private_key_from_seed(
    struct MLDSA87_private_key *out_private_key, const uint8_t *seed,
    size_t seed_len) {
  if (seed_len != BCM_MLDSA_SEED_BYTES) {
    return 0;
  }
  return bcm_success(BCM_mldsa87_private_key_from_seed(
      reinterpret_cast<BCM_mldsa87_private_key *>(out_private_key), seed));
}

static int MLDSA87_sign(
    uint8_t out_encoded_signature[BCM_MLDSA87_SIGNATURE_BYTES],
    const struct MLDSA87_private_key *private_key, const uint8_t *msg,
    size_t msg_len, const uint8_t *context, size_t context_len) {
  return bcm_success(BCM_mldsa87_sign(
      out_encoded_signature,
      reinterpret_cast<const BCM_mldsa87_private_key *>(private_key), msg,
      msg_len, context, context_len));
}

static int MLDSA87_verify(const struct MLDSA87_public_key *public_key,
                          const uint8_t *signature, size_t signature_len,
                          const uint8_t *msg, size_t msg_len,
                          const uint8_t *context, size_t context_len) {
  if (context_len > 255 || signature_len != BCM_MLDSA87_SIGNATURE_BYTES) {
    return 0;
  }
  return bcm_success(BCM_mldsa87_verify(
      reinterpret_cast<const BCM_mldsa87_public_key *>(public_key), signature,
      msg, msg_len, context, context_len));
}

static int MLDSA87_parse_public_key(struct MLDSA87_public_key *public_key,
                                    CBS *in) {
  return bcm_success(BCM_mldsa87_parse_public_key(
      reinterpret_cast<BCM_mldsa87_public_key *>(public_key), in));
}

TEST(MLDSATest, Basic87) {
  MLDSABasicTest<MLDSA87_private_key, MLDSA87_public_key,
                 BCM_MLDSA87_PUBLIC_KEY_BYTES, BCM_MLDSA87_SIGNATURE_BYTES,
                 MLDSA87_generate_key, MLDSA87_sign, MLDSA87_parse_public_key,
                 MLDSA87_verify, MLDSA87_private_key_from_seed,
                 BCM_mldsa87_private_key, BCM_mldsa87_parse_private_key,
                 BCM_mldsa87_marshal_private_key>();
}

TEST(MLDSATest, SignatureIsRandomized) {
  std::vector<uint8_t> encoded_public_key(MLDSA65_PUBLIC_KEY_BYTES);
  auto priv = std::make_unique<MLDSA65_private_key>();
  uint8_t seed[MLDSA_SEED_BYTES];
  EXPECT_TRUE(
      MLDSA65_generate_key(encoded_public_key.data(), seed, priv.get()));

  auto pub = std::make_unique<MLDSA65_public_key>();
  CBS cbs = CBS(encoded_public_key);
  ASSERT_TRUE(MLDSA65_parse_public_key(pub.get(), &cbs));

  std::vector<uint8_t> encoded_signature1(MLDSA65_SIGNATURE_BYTES);
  std::vector<uint8_t> encoded_signature2(MLDSA65_SIGNATURE_BYTES);
  static const uint8_t kMessage[] = {'H', 'e', 'l', 'l', 'o', ' ',
                                     'w', 'o', 'r', 'l', 'd'};
  EXPECT_TRUE(MLDSA65_sign(encoded_signature1.data(), priv.get(), kMessage,
                           sizeof(kMessage), nullptr, 0));
  EXPECT_TRUE(MLDSA65_sign(encoded_signature2.data(), priv.get(), kMessage,
                           sizeof(kMessage), nullptr, 0));

  EXPECT_NE(Bytes(encoded_signature1), Bytes(encoded_signature2));

  // Even though the signatures are different, they both verify.
  EXPECT_EQ(MLDSA65_verify(pub.get(), encoded_signature1.data(),
                           encoded_signature1.size(), kMessage,
                           sizeof(kMessage), nullptr, 0),
            1);
  EXPECT_EQ(MLDSA65_verify(pub.get(), encoded_signature2.data(),
                           encoded_signature2.size(), kMessage,
                           sizeof(kMessage), nullptr, 0),
            1);
}

TEST(MLDSATest, PublicFromPrivateIsConsistent) {
  std::vector<uint8_t> encoded_public_key(MLDSA65_PUBLIC_KEY_BYTES);
  auto priv = std::make_unique<MLDSA65_private_key>();
  uint8_t seed[MLDSA_SEED_BYTES];
  EXPECT_TRUE(
      MLDSA65_generate_key(encoded_public_key.data(), seed, priv.get()));

  auto pub = std::make_unique<MLDSA65_public_key>();
  EXPECT_TRUE(MLDSA65_public_from_private(pub.get(), priv.get()));

  std::vector<uint8_t> encoded_public_key2(MLDSA65_PUBLIC_KEY_BYTES);

  CBB cbb;
  CBB_init_fixed(&cbb, encoded_public_key2.data(), encoded_public_key2.size());
  ASSERT_TRUE(MLDSA65_marshal_public_key(&cbb, pub.get()));

  EXPECT_EQ(Bytes(encoded_public_key2), Bytes(encoded_public_key));
}

TEST(MLDSATest, InvalidPublicKeyEncodingLength) {
  // Encode a public key with a trailing 0 at the end.
  std::vector<uint8_t> encoded_public_key(MLDSA65_PUBLIC_KEY_BYTES + 1);
  auto priv = std::make_unique<MLDSA65_private_key>();
  uint8_t seed[MLDSA_SEED_BYTES];
  EXPECT_TRUE(
      MLDSA65_generate_key(encoded_public_key.data(), seed, priv.get()));

  // Public key is 1 byte too short.
  CBS cbs =
      CBS(bssl::Span(encoded_public_key).first(MLDSA65_PUBLIC_KEY_BYTES - 1));
  auto parsed_pub = std::make_unique<MLDSA65_public_key>();
  EXPECT_FALSE(MLDSA65_parse_public_key(parsed_pub.get(), &cbs));

  // Public key has the correct length.
  cbs = CBS(bssl::Span(encoded_public_key).first(MLDSA65_PUBLIC_KEY_BYTES));
  EXPECT_TRUE(MLDSA65_parse_public_key(parsed_pub.get(), &cbs));

  // Public key is 1 byte too long.
  cbs = CBS(encoded_public_key);
  EXPECT_FALSE(MLDSA65_parse_public_key(parsed_pub.get(), &cbs));
}

TEST(MLDSATest, InvalidPrivateKeyEncodingLength) {
  std::vector<uint8_t> encoded_public_key(MLDSA65_PUBLIC_KEY_BYTES);
  auto priv = std::make_unique<BCM_mldsa65_private_key>();
  uint8_t seed[MLDSA_SEED_BYTES];
  EXPECT_TRUE(bcm_success(
      BCM_mldsa65_generate_key(encoded_public_key.data(), seed, priv.get())));

  CBB cbb;
  std::vector<uint8_t> malformed_private_key(MLDSA65_PRIVATE_KEY_BYTES + 1, 0);
  CBB_init_fixed(&cbb, malformed_private_key.data(), MLDSA65_PRIVATE_KEY_BYTES);
  ASSERT_TRUE(bcm_success(BCM_mldsa65_marshal_private_key(
      &cbb, reinterpret_cast<BCM_mldsa65_private_key *>(priv.get()))));

  CBS cbs;
  auto parsed_priv = std::make_unique<BCM_mldsa65_private_key>();

  // Private key is 1 byte too short.
  CBS_init(&cbs, malformed_private_key.data(), MLDSA65_PRIVATE_KEY_BYTES - 1);
  EXPECT_FALSE(
      bcm_success(BCM_mldsa65_parse_private_key(parsed_priv.get(), &cbs)));

  // Private key has the correct length.
  CBS_init(&cbs, malformed_private_key.data(), MLDSA65_PRIVATE_KEY_BYTES);
  EXPECT_TRUE(
      bcm_success(BCM_mldsa65_parse_private_key(parsed_priv.get(), &cbs)));

  // Private key is 1 byte too long.
  CBS_init(&cbs, malformed_private_key.data(), MLDSA65_PRIVATE_KEY_BYTES + 1);
  EXPECT_FALSE(
      bcm_success(BCM_mldsa65_parse_private_key(parsed_priv.get(), &cbs)));
}

template <typename PrivateKey, typename PublicKey, size_t SignatureBytes,
          bcm_status (*ParsePrivateKey)(PrivateKey *, CBS *),
          bcm_status (*SignInternal)(uint8_t *, const PrivateKey *,
                                     const uint8_t *, size_t, const uint8_t *,
                                     size_t, const uint8_t *, size_t,
                                     const uint8_t *),
          bcm_status (*PublicFromPrivate)(PublicKey *, const PrivateKey *),
          bcm_status (*VerifyInternal)(const PublicKey *, const uint8_t *,
                                       const uint8_t *, size_t, const uint8_t *,
                                       size_t, const uint8_t *, size_t)>
static void MLDSASigGenTest(FileTest *t) {
  std::vector<uint8_t> private_key_bytes, msg, expected_signature;
  ASSERT_TRUE(t->GetBytes(&private_key_bytes, "sk"));
  ASSERT_TRUE(t->GetBytes(&msg, "message"));
  ASSERT_TRUE(t->GetBytes(&expected_signature, "signature"));

  auto priv = std::make_unique<PrivateKey>();
  CBS cbs;
  CBS_init(&cbs, private_key_bytes.data(), private_key_bytes.size());
  EXPECT_TRUE(bcm_success(ParsePrivateKey(priv.get(), &cbs)));

  const uint8_t zero_randomizer[BCM_MLDSA_SIGNATURE_RANDOMIZER_BYTES] = {0};
  std::vector<uint8_t> signature(SignatureBytes);
  EXPECT_TRUE(bcm_success(SignInternal(signature.data(), priv.get(), msg.data(),
                                       msg.size(), nullptr, 0, nullptr, 0,
                                       zero_randomizer)));

  EXPECT_EQ(Bytes(signature), Bytes(expected_signature));

  auto pub = std::make_unique<PublicKey>();
  ASSERT_TRUE(bcm_success(PublicFromPrivate(pub.get(), priv.get())));
  EXPECT_TRUE(
      bcm_success(VerifyInternal(pub.get(), signature.data(), msg.data(),
                                 msg.size(), nullptr, 0, nullptr, 0)));
}

TEST(MLDSATest, SigGenTests65) {
  FileTestGTest(
      "crypto/mldsa/mldsa_nist_siggen_65_tests.txt",
      MLDSASigGenTest<BCM_mldsa65_private_key, BCM_mldsa65_public_key,
                      MLDSA65_SIGNATURE_BYTES, BCM_mldsa65_parse_private_key,
                      BCM_mldsa65_sign_internal,
                      BCM_mldsa65_public_from_private,
                      BCM_mldsa65_verify_internal>);
}

TEST(MLDSATest, SigGenTests87) {
  FileTestGTest(
      "crypto/mldsa/mldsa_nist_siggen_87_tests.txt",
      MLDSASigGenTest<BCM_mldsa87_private_key, BCM_mldsa87_public_key,
                      BCM_MLDSA87_SIGNATURE_BYTES,
                      BCM_mldsa87_parse_private_key, BCM_mldsa87_sign_internal,
                      BCM_mldsa87_public_from_private,
                      BCM_mldsa87_verify_internal>);
}

template <typename PrivateKey, size_t PublicKeyBytes,
          bcm_status (*Generate)(uint8_t *, PrivateKey *, const uint8_t *),
          bcm_status (*MarshalPrivate)(CBB *, const PrivateKey *)>
static void MLDSAKeyGenTest(FileTest *t) {
  std::vector<uint8_t> seed, expected_public_key, expected_private_key;
  ASSERT_TRUE(t->GetBytes(&seed, "seed"));
  CONSTTIME_SECRET(seed.data(), seed.size());
  ASSERT_TRUE(t->GetBytes(&expected_public_key, "pub"));
  ASSERT_TRUE(t->GetBytes(&expected_private_key, "priv"));

  std::vector<uint8_t> encoded_public_key(PublicKeyBytes);
  auto priv = std::make_unique<PrivateKey>();
  ASSERT_TRUE(bcm_success(
      Generate(encoded_public_key.data(), priv.get(), seed.data())));

  const std::vector<uint8_t> encoded_private_key =
      Marshal(MarshalPrivate, priv.get());

  EXPECT_EQ(Bytes(encoded_public_key), Bytes(expected_public_key));
  EXPECT_EQ(Bytes(Declassified(encoded_private_key)),
            Bytes(expected_private_key));
}

TEST(MLDSATest, KeyGenTests65) {
  FileTestGTest(
      "crypto/mldsa/mldsa_nist_keygen_65_tests.txt",
      MLDSAKeyGenTest<BCM_mldsa65_private_key, MLDSA65_PUBLIC_KEY_BYTES,
                      BCM_mldsa65_generate_key_external_entropy,
                      BCM_mldsa65_marshal_private_key>);
}

TEST(MLDSATest, KeyGenTests87) {
  FileTestGTest(
      "crypto/mldsa/mldsa_nist_keygen_87_tests.txt",
      MLDSAKeyGenTest<BCM_mldsa87_private_key, BCM_MLDSA87_PUBLIC_KEY_BYTES,
                      BCM_mldsa87_generate_key_external_entropy,
                      BCM_mldsa87_marshal_private_key>);
}

template <
    typename PrivateKey, bcm_status_t (*ParsePrivateKey)(PrivateKey *, CBS *),
    size_t SignatureBytes,
    bcm_status_t (*SignInternal)(uint8_t *, const PrivateKey *, const uint8_t *,
                                 size_t, const uint8_t *, size_t,
                                 const uint8_t *, size_t, const uint8_t *)>
static void MLDSAWycheproofSignTest(FileTest *t) {
  std::vector<uint8_t> private_key_bytes, msg, expected_signature, context;
  ASSERT_TRUE(t->GetInstructionBytes(&private_key_bytes, "privateKey"));
  ASSERT_TRUE(t->GetBytes(&msg, "msg"));
  ASSERT_TRUE(t->GetBytes(&expected_signature, "sig"));
  if (t->HasAttribute("ctx")) {
    t->GetBytes(&context, "ctx");
  }
  std::string result;
  ASSERT_TRUE(t->GetAttribute(&result, "result"));
  t->IgnoreAttribute("flags");

  CBS cbs;
  CBS_init(&cbs, private_key_bytes.data(), private_key_bytes.size());
  auto priv = std::make_unique<PrivateKey>();
  const int priv_ok = bcm_success(ParsePrivateKey(priv.get(), &cbs));

  if (!priv_ok) {
    ASSERT_TRUE(result != "valid");
    return;
  }

  // Unfortunately we need to reimplement the context length check here because
  // we are using the internal function in order to pass in an all-zero
  // randomizer.
  if (context.size() > 255) {
    ASSERT_TRUE(result != "valid");
    return;
  }

  const uint8_t zero_randomizer[BCM_MLDSA_SIGNATURE_RANDOMIZER_BYTES] = {0};
  std::vector<uint8_t> signature(SignatureBytes);
  const uint8_t context_prefix[2] = {0, static_cast<uint8_t>(context.size())};
  EXPECT_TRUE(bcm_success(SignInternal(signature.data(), priv.get(), msg.data(),
                                       msg.size(), context_prefix,
                                       sizeof(context_prefix), context.data(),
                                       context.size(), zero_randomizer)));

  EXPECT_EQ(Bytes(signature), Bytes(expected_signature));
}

TEST(MLDSATest, WycheproofSignTests65) {
  FileTestGTest(
      "third_party/wycheproof_testvectors/mldsa_65_standard_sign_test.txt",
      MLDSAWycheproofSignTest<
          BCM_mldsa65_private_key, BCM_mldsa65_parse_private_key,
          MLDSA65_SIGNATURE_BYTES, BCM_mldsa65_sign_internal>);
}

TEST(MLDSATest, WycheproofSignTests87) {
  FileTestGTest(
      "third_party/wycheproof_testvectors/mldsa_87_standard_sign_test.txt",
      MLDSAWycheproofSignTest<
          BCM_mldsa87_private_key, BCM_mldsa87_parse_private_key,
          BCM_MLDSA87_SIGNATURE_BYTES, BCM_mldsa87_sign_internal>);
}

template <typename PublicKey, size_t SignatureLength,
          bcm_status_t (*ParsePublicKey)(PublicKey *, CBS *),
          bcm_status_t (*Verify)(const PublicKey *, const uint8_t *,
                                 const uint8_t *, size_t, const uint8_t *,
                                 size_t)>
static void MLDSAWycheproofVerifyTest(FileTest *t) {
  std::vector<uint8_t> public_key_bytes, msg, signature, context;
  ASSERT_TRUE(t->GetInstructionBytes(&public_key_bytes, "publicKey"));
  ASSERT_TRUE(t->GetBytes(&msg, "msg"));
  ASSERT_TRUE(t->GetBytes(&signature, "sig"));
  if (t->HasAttribute("ctx")) {
    t->GetBytes(&context, "ctx");
  }
  std::string result, flags;
  ASSERT_TRUE(t->GetAttribute(&result, "result"));
  ASSERT_TRUE(t->GetAttribute(&flags, "flags"));

  CBS cbs;
  CBS_init(&cbs, public_key_bytes.data(), public_key_bytes.size());
  auto pub = std::make_unique<PublicKey>();
  const int pub_ok = bcm_success(ParsePublicKey(pub.get(), &cbs));

  if (!pub_ok) {
    EXPECT_EQ(flags, "IncorrectPublicKeyLength");
    return;
  }

  const int sig_ok =
      signature.size() == SignatureLength && context.size() <= 255 &&
      bcm_success(Verify(pub.get(), signature.data(), msg.data(), msg.size(),
                         context.data(), context.size()));
  if (!sig_ok) {
    EXPECT_EQ(result, "invalid");
  } else {
    EXPECT_EQ(result, "valid");
  }
}

TEST(MLDSATest, WycheproofVerifyTests65) {
  FileTestGTest(
      "third_party/wycheproof_testvectors/mldsa_65_standard_verify_test.txt",
      MLDSAWycheproofVerifyTest<
          BCM_mldsa65_public_key, BCM_MLDSA65_SIGNATURE_BYTES,
          BCM_mldsa65_parse_public_key, BCM_mldsa65_verify>);
}

TEST(MLDSATest, WycheproofVerifyTests87) {
  FileTestGTest(
      "third_party/wycheproof_testvectors/mldsa_87_standard_verify_test.txt",
      MLDSAWycheproofVerifyTest<
          BCM_mldsa87_public_key, BCM_MLDSA87_SIGNATURE_BYTES,
          BCM_mldsa87_parse_public_key, BCM_mldsa87_verify>);
}

TEST(MLDSATest, Self) { ASSERT_TRUE(boringssl_self_test_mldsa()); }

TEST(MLDSATest, PWCT) {
  uint8_t seed[BCM_MLDSA_SEED_BYTES];

  auto pub65 = std::make_unique<uint8_t[]>(BCM_MLDSA65_PUBLIC_KEY_BYTES);
  auto priv65 = std::make_unique<BCM_mldsa65_private_key>();
  ASSERT_EQ(BCM_mldsa65_generate_key_fips(pub65.get(), seed, priv65.get()),
            bcm_status::approved);

  auto pub87 = std::make_unique<uint8_t[]>(BCM_MLDSA87_PUBLIC_KEY_BYTES);
  auto priv87 = std::make_unique<BCM_mldsa87_private_key>();
  ASSERT_EQ(BCM_mldsa87_generate_key_fips(pub87.get(), seed, priv87.get()),
            bcm_status::approved);
}

TEST(MLDSATest, NullptrArgumentsToCreate) {
  // For FIPS reasons, this should fail rather than crash.
  ASSERT_EQ(BCM_mldsa65_generate_key_fips(nullptr, nullptr, nullptr),
            bcm_status::failure);
  ASSERT_EQ(BCM_mldsa87_generate_key_fips(nullptr, nullptr, nullptr),
            bcm_status::failure);
  ASSERT_EQ(
      BCM_mldsa65_generate_key_external_entropy_fips(nullptr, nullptr, nullptr),
      bcm_status::failure);
  ASSERT_EQ(
      BCM_mldsa87_generate_key_external_entropy_fips(nullptr, nullptr, nullptr),
      bcm_status::failure);
}

}  // namespace

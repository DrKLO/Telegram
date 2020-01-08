/* Copyright (c) 2014, Google Inc.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

#include <algorithm>
#include <string>
#include <functional>
#include <memory>
#include <vector>

#include <assert.h>
#include <errno.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <openssl/aead.h>
#include <openssl/aes.h>
#include <openssl/bn.h>
#include <openssl/curve25519.h>
#include <openssl/digest.h>
#include <openssl/err.h>
#include <openssl/ec.h>
#include <openssl/ecdsa.h>
#include <openssl/ec_key.h>
#include <openssl/evp.h>
#include <openssl/hrss.h>
#include <openssl/nid.h>
#include <openssl/rand.h>
#include <openssl/rsa.h>

#if defined(OPENSSL_WINDOWS)
OPENSSL_MSVC_PRAGMA(warning(push, 3))
#include <windows.h>
OPENSSL_MSVC_PRAGMA(warning(pop))
#elif defined(OPENSSL_APPLE)
#include <sys/time.h>
#else
#include <time.h>
#endif

#include "../crypto/internal.h"
#include "internal.h"

#include "../third_party/sike/sike.h"


// TimeResults represents the results of benchmarking a function.
struct TimeResults {
  // num_calls is the number of function calls done in the time period.
  unsigned num_calls;
  // us is the number of microseconds that elapsed in the time period.
  unsigned us;

  void Print(const std::string &description) {
    printf("Did %u %s operations in %uus (%.1f ops/sec)\n", num_calls,
           description.c_str(), us,
           (static_cast<double>(num_calls) / us) * 1000000);
  }

  void PrintWithBytes(const std::string &description, size_t bytes_per_call) {
    printf("Did %u %s operations in %uus (%.1f ops/sec): %.1f MB/s\n",
           num_calls, description.c_str(), us,
           (static_cast<double>(num_calls) / us) * 1000000,
           static_cast<double>(bytes_per_call * num_calls) / us);
  }
};

#if defined(OPENSSL_WINDOWS)
static uint64_t time_now() { return GetTickCount64() * 1000; }
#elif defined(OPENSSL_APPLE)
static uint64_t time_now() {
  struct timeval tv;
  uint64_t ret;

  gettimeofday(&tv, NULL);
  ret = tv.tv_sec;
  ret *= 1000000;
  ret += tv.tv_usec;
  return ret;
}
#else
static uint64_t time_now() {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);

  uint64_t ret = ts.tv_sec;
  ret *= 1000000;
  ret += ts.tv_nsec / 1000;
  return ret;
}
#endif

static uint64_t g_timeout_seconds = 1;
static std::vector<size_t> g_chunk_lengths = {16, 256, 1350, 8192, 16384};

static bool TimeFunction(TimeResults *results, std::function<bool()> func) {
  // total_us is the total amount of time that we'll aim to measure a function
  // for.
  const uint64_t total_us = g_timeout_seconds * 1000000;
  uint64_t start = time_now(), now, delta;
  unsigned done = 0, iterations_between_time_checks;

  if (!func()) {
    return false;
  }
  now = time_now();
  delta = now - start;
  if (delta == 0) {
    iterations_between_time_checks = 250;
  } else {
    // Aim for about 100ms between time checks.
    iterations_between_time_checks =
        static_cast<double>(100000) / static_cast<double>(delta);
    if (iterations_between_time_checks > 1000) {
      iterations_between_time_checks = 1000;
    } else if (iterations_between_time_checks < 1) {
      iterations_between_time_checks = 1;
    }
  }

  for (;;) {
    for (unsigned i = 0; i < iterations_between_time_checks; i++) {
      if (!func()) {
        return false;
      }
      done++;
    }

    now = time_now();
    if (now - start > total_us) {
      break;
    }
  }

  results->us = now - start;
  results->num_calls = done;
  return true;
}

static bool SpeedRSA(const std::string &selected) {
  if (!selected.empty() && selected.find("RSA") == std::string::npos) {
    return true;
  }

  static const struct {
    const char *name;
    const uint8_t *key;
    const size_t key_len;
  } kRSAKeys[] = {
    {"RSA 2048", kDERRSAPrivate2048, kDERRSAPrivate2048Len},
    {"RSA 4096", kDERRSAPrivate4096, kDERRSAPrivate4096Len},
  };

  for (unsigned i = 0; i < OPENSSL_ARRAY_SIZE(kRSAKeys); i++) {
    const std::string name = kRSAKeys[i].name;

    bssl::UniquePtr<RSA> key(
        RSA_private_key_from_bytes(kRSAKeys[i].key, kRSAKeys[i].key_len));
    if (key == nullptr) {
      fprintf(stderr, "Failed to parse %s key.\n", name.c_str());
      ERR_print_errors_fp(stderr);
      return false;
    }

    std::unique_ptr<uint8_t[]> sig(new uint8_t[RSA_size(key.get())]);
    const uint8_t fake_sha256_hash[32] = {0};
    unsigned sig_len;

    TimeResults results;
    if (!TimeFunction(&results,
                      [&key, &sig, &fake_sha256_hash, &sig_len]() -> bool {
          // Usually during RSA signing we're using a long-lived |RSA| that has
          // already had all of its |BN_MONT_CTX|s constructed, so it makes
          // sense to use |key| directly here.
          return RSA_sign(NID_sha256, fake_sha256_hash, sizeof(fake_sha256_hash),
                          sig.get(), &sig_len, key.get());
        })) {
      fprintf(stderr, "RSA_sign failed.\n");
      ERR_print_errors_fp(stderr);
      return false;
    }
    results.Print(name + " signing");

    if (!TimeFunction(&results,
                      [&key, &fake_sha256_hash, &sig, sig_len]() -> bool {
          return RSA_verify(
              NID_sha256, fake_sha256_hash, sizeof(fake_sha256_hash),
              sig.get(), sig_len, key.get());
        })) {
      fprintf(stderr, "RSA_verify failed.\n");
      ERR_print_errors_fp(stderr);
      return false;
    }
    results.Print(name + " verify (same key)");

    if (!TimeFunction(&results,
                      [&key, &fake_sha256_hash, &sig, sig_len]() -> bool {
          // Usually during RSA verification we have to parse an RSA key from a
          // certificate or similar, in which case we'd need to construct a new
          // RSA key, with a new |BN_MONT_CTX| for the public modulus. If we
          // were to use |key| directly instead, then these costs wouldn't be
          // accounted for.
          bssl::UniquePtr<RSA> verify_key(RSA_new());
          if (!verify_key) {
            return false;
          }
          verify_key->n = BN_dup(key->n);
          verify_key->e = BN_dup(key->e);
          if (!verify_key->n ||
              !verify_key->e) {
            return false;
          }
          return RSA_verify(NID_sha256, fake_sha256_hash,
                            sizeof(fake_sha256_hash), sig.get(), sig_len,
                            verify_key.get());
        })) {
      fprintf(stderr, "RSA_verify failed.\n");
      ERR_print_errors_fp(stderr);
      return false;
    }
    results.Print(name + " verify (fresh key)");
  }

  return true;
}

static bool SpeedRSAKeyGen(const std::string &selected) {
  // Don't run this by default because it's so slow.
  if (selected != "RSAKeyGen") {
    return true;
  }

  bssl::UniquePtr<BIGNUM> e(BN_new());
  if (!BN_set_word(e.get(), 65537)) {
    return false;
  }

  const std::vector<int> kSizes = {2048, 3072, 4096};
  for (int size : kSizes) {
    const uint64_t start = time_now();
    unsigned num_calls = 0;
    unsigned us;
    std::vector<unsigned> durations;

    for (;;) {
      bssl::UniquePtr<RSA> rsa(RSA_new());

      const uint64_t iteration_start = time_now();
      if (!RSA_generate_key_ex(rsa.get(), size, e.get(), nullptr)) {
        fprintf(stderr, "RSA_generate_key_ex failed.\n");
        ERR_print_errors_fp(stderr);
        return false;
      }
      const uint64_t iteration_end = time_now();

      num_calls++;
      durations.push_back(iteration_end - iteration_start);

      us = iteration_end - start;
      if (us > 30 * 1000000 /* 30 secs */) {
        break;
      }
    }

    std::sort(durations.begin(), durations.end());
    printf("Did %u RSA %d key-gen operations in %uus (%.1f ops/sec)\n",
           num_calls, size, us,
           (static_cast<double>(num_calls) / us) * 1000000);
    const size_t n = durations.size();
    assert(n > 0);

    // |min| and |max| must be stored in temporary variables to avoid an MSVC
    // bug on x86. There, size_t is a typedef for unsigned, but MSVC's printf
    // warning tries to retain the distinction and suggest %zu for size_t
    // instead of %u. It gets confused if std::vector<unsigned> and
    // std::vector<size_t> are both instantiated. Being typedefs, the two
    // instantiations are identical, which somehow breaks the size_t vs unsigned
    // metadata.
    unsigned min = durations[0];
    unsigned median = n & 1 ? durations[n / 2]
                            : (durations[n / 2 - 1] + durations[n / 2]) / 2;
    unsigned max = durations[n - 1];
    printf("  min: %uus, median: %uus, max: %uus\n", min, median, max);
  }

  return true;
}

static bool SpeedSIKEP434(const std::string &selected) {
  if (!selected.empty() && selected.find("SIKE") == std::string::npos) {
    return true;
  }
  // speed generation
  uint8_t public_SIKE[SIKE_PUB_BYTESZ];
  uint8_t private_SIKE[SIKE_PRV_BYTESZ];
  uint8_t ct[SIKE_CT_BYTESZ];
  bool res;

  {
    TimeResults results;
    res = TimeFunction(&results,
                [&private_SIKE, &public_SIKE]() -> bool {
      return (SIKE_keypair(private_SIKE, public_SIKE) == 1);
    });
    results.Print("SIKE/P434 generate");
  }

  if (!res) {
    fprintf(stderr, "Failed to time SIKE_keypair.\n");
    return false;
  }

  {
    TimeResults results;
    TimeFunction(&results,
                [&ct, &public_SIKE]() -> bool {
      uint8_t ss[SIKE_SS_BYTESZ];
      SIKE_encaps(ss, ct, public_SIKE);
      return true;
    });
    results.Print("SIKE/P434 encap");
  }

  if (!res) {
    fprintf(stderr, "Failed to time SIKE_encaps.\n");
    return false;
  }

  {
    TimeResults results;
    TimeFunction(&results,
                [&ct, &public_SIKE, &private_SIKE]() -> bool {
      uint8_t ss[SIKE_SS_BYTESZ];
      SIKE_decaps(ss, ct, public_SIKE, private_SIKE);
      return true;
    });
    results.Print("SIKE/P434 decap");
  }

  if (!res) {
    fprintf(stderr, "Failed to time SIKE_decaps.\n");
    return false;
  }
  return true;
}

static uint8_t *align(uint8_t *in, unsigned alignment) {
  return reinterpret_cast<uint8_t *>(
      (reinterpret_cast<uintptr_t>(in) + alignment) &
      ~static_cast<size_t>(alignment - 1));
}

static std::string ChunkLenSuffix(size_t chunk_len) {
  char buf[32];
  snprintf(buf, sizeof(buf), " (%zu byte%s)", chunk_len,
           chunk_len != 1 ? "s" : "");
  return buf;
}

static bool SpeedAEADChunk(const EVP_AEAD *aead, std::string name,
                           size_t chunk_len, size_t ad_len,
                           evp_aead_direction_t direction) {
  static const unsigned kAlignment = 16;

  name += ChunkLenSuffix(chunk_len);
  bssl::ScopedEVP_AEAD_CTX ctx;
  const size_t key_len = EVP_AEAD_key_length(aead);
  const size_t nonce_len = EVP_AEAD_nonce_length(aead);
  const size_t overhead_len = EVP_AEAD_max_overhead(aead);

  std::unique_ptr<uint8_t[]> key(new uint8_t[key_len]);
  OPENSSL_memset(key.get(), 0, key_len);
  std::unique_ptr<uint8_t[]> nonce(new uint8_t[nonce_len]);
  OPENSSL_memset(nonce.get(), 0, nonce_len);
  std::unique_ptr<uint8_t[]> in_storage(new uint8_t[chunk_len + kAlignment]);
  // N.B. for EVP_AEAD_CTX_seal_scatter the input and output buffers may be the
  // same size. However, in the direction == evp_aead_open case we still use
  // non-scattering seal, hence we add overhead_len to the size of this buffer.
  std::unique_ptr<uint8_t[]> out_storage(
      new uint8_t[chunk_len + overhead_len + kAlignment]);
  std::unique_ptr<uint8_t[]> in2_storage(
      new uint8_t[chunk_len + overhead_len + kAlignment]);
  std::unique_ptr<uint8_t[]> ad(new uint8_t[ad_len]);
  OPENSSL_memset(ad.get(), 0, ad_len);
  std::unique_ptr<uint8_t[]> tag_storage(
      new uint8_t[overhead_len + kAlignment]);


  uint8_t *const in = align(in_storage.get(), kAlignment);
  OPENSSL_memset(in, 0, chunk_len);
  uint8_t *const out = align(out_storage.get(), kAlignment);
  OPENSSL_memset(out, 0, chunk_len + overhead_len);
  uint8_t *const tag = align(tag_storage.get(), kAlignment);
  OPENSSL_memset(tag, 0, overhead_len);
  uint8_t *const in2 = align(in2_storage.get(), kAlignment);

  if (!EVP_AEAD_CTX_init_with_direction(ctx.get(), aead, key.get(), key_len,
                                        EVP_AEAD_DEFAULT_TAG_LENGTH,
                                        evp_aead_seal)) {
    fprintf(stderr, "Failed to create EVP_AEAD_CTX.\n");
    ERR_print_errors_fp(stderr);
    return false;
  }

  TimeResults results;
  if (direction == evp_aead_seal) {
    if (!TimeFunction(&results,
                      [chunk_len, nonce_len, ad_len, overhead_len, in, out, tag,
                       &ctx, &nonce, &ad]() -> bool {
                        size_t tag_len;
                        return EVP_AEAD_CTX_seal_scatter(
                            ctx.get(), out, tag, &tag_len, overhead_len,
                            nonce.get(), nonce_len, in, chunk_len, nullptr, 0,
                            ad.get(), ad_len);
                      })) {
      fprintf(stderr, "EVP_AEAD_CTX_seal failed.\n");
      ERR_print_errors_fp(stderr);
      return false;
    }
  } else {
    size_t out_len;
    EVP_AEAD_CTX_seal(ctx.get(), out, &out_len, chunk_len + overhead_len,
                      nonce.get(), nonce_len, in, chunk_len, ad.get(), ad_len);

    ctx.Reset();
    if (!EVP_AEAD_CTX_init_with_direction(ctx.get(), aead, key.get(), key_len,
                                          EVP_AEAD_DEFAULT_TAG_LENGTH,
                                          evp_aead_open)) {
      fprintf(stderr, "Failed to create EVP_AEAD_CTX.\n");
      ERR_print_errors_fp(stderr);
      return false;
    }

    if (!TimeFunction(&results,
                      [chunk_len, overhead_len, nonce_len, ad_len, in2, out,
                       out_len, &ctx, &nonce, &ad]() -> bool {
                        size_t in2_len;
                        // N.B. EVP_AEAD_CTX_open_gather is not implemented for
                        // all AEADs.
                        return EVP_AEAD_CTX_open(ctx.get(), in2, &in2_len,
                                                 chunk_len + overhead_len,
                                                 nonce.get(), nonce_len, out,
                                                 out_len, ad.get(), ad_len);
                      })) {
      fprintf(stderr, "EVP_AEAD_CTX_open failed.\n");
      ERR_print_errors_fp(stderr);
      return false;
    }
  }

  results.PrintWithBytes(
      name + (direction == evp_aead_seal ? " seal" : " open"), chunk_len);
  return true;
}

static bool SpeedAEAD(const EVP_AEAD *aead, const std::string &name,
                      size_t ad_len, const std::string &selected) {
  if (!selected.empty() && name.find(selected) == std::string::npos) {
    return true;
  }

  for (size_t chunk_len : g_chunk_lengths) {
    if (!SpeedAEADChunk(aead, name, chunk_len, ad_len, evp_aead_seal)) {
      return false;
    }
  }
  return true;
}

static bool SpeedAEADOpen(const EVP_AEAD *aead, const std::string &name,
                          size_t ad_len, const std::string &selected) {
  if (!selected.empty() && name.find(selected) == std::string::npos) {
    return true;
  }

  for (size_t chunk_len : g_chunk_lengths) {
    if (!SpeedAEADChunk(aead, name, chunk_len, ad_len, evp_aead_open)) {
      return false;
    }
  }

  return true;
}

static bool SpeedAESBlock(const std::string &name, unsigned bits,
                          const std::string &selected) {
  if (!selected.empty() && name.find(selected) == std::string::npos) {
    return true;
  }

  static const uint8_t kZero[32] = {0};

  {
    TimeResults results;
    if (!TimeFunction(&results, [&]() -> bool {
          AES_KEY key;
          return AES_set_encrypt_key(kZero, bits, &key) == 0;
        })) {
      fprintf(stderr, "AES_set_encrypt_key failed.\n");
      return false;
    }
    results.Print(name + " encrypt setup");
  }

  {
    AES_KEY key;
    if (AES_set_encrypt_key(kZero, bits, &key) != 0) {
      return false;
    }
    uint8_t block[16] = {0};
    TimeResults results;
    if (!TimeFunction(&results, [&]() -> bool {
          AES_encrypt(block, block, &key);
          return true;
        })) {
      fprintf(stderr, "AES_encrypt failed.\n");
      return false;
    }
    results.Print(name + " encrypt");
  }

  {
    TimeResults results;
    if (!TimeFunction(&results, [&]() -> bool {
          AES_KEY key;
          return AES_set_decrypt_key(kZero, bits, &key) == 0;
        })) {
      fprintf(stderr, "AES_set_decrypt_key failed.\n");
      return false;
    }
    results.Print(name + " decrypt setup");
  }

  {
    AES_KEY key;
    if (AES_set_decrypt_key(kZero, bits, &key) != 0) {
      return false;
    }
    uint8_t block[16] = {0};
    TimeResults results;
    if (!TimeFunction(&results, [&]() -> bool {
          AES_decrypt(block, block, &key);
          return true;
        })) {
      fprintf(stderr, "AES_decrypt failed.\n");
      return false;
    }
    results.Print(name + " decrypt");
  }

  return true;
}

static bool SpeedHashChunk(const EVP_MD *md, std::string name,
                           size_t chunk_len) {
  bssl::ScopedEVP_MD_CTX ctx;
  uint8_t scratch[16384];

  if (chunk_len > sizeof(scratch)) {
    return false;
  }

  name += ChunkLenSuffix(chunk_len);
  TimeResults results;
  if (!TimeFunction(&results, [&ctx, md, chunk_len, &scratch]() -> bool {
        uint8_t digest[EVP_MAX_MD_SIZE];
        unsigned int md_len;

        return EVP_DigestInit_ex(ctx.get(), md, NULL /* ENGINE */) &&
               EVP_DigestUpdate(ctx.get(), scratch, chunk_len) &&
               EVP_DigestFinal_ex(ctx.get(), digest, &md_len);
      })) {
    fprintf(stderr, "EVP_DigestInit_ex failed.\n");
    ERR_print_errors_fp(stderr);
    return false;
  }

  results.PrintWithBytes(name, chunk_len);
  return true;
}

static bool SpeedHash(const EVP_MD *md, const std::string &name,
                      const std::string &selected) {
  if (!selected.empty() && name.find(selected) == std::string::npos) {
    return true;
  }

  for (size_t chunk_len : g_chunk_lengths) {
    if (!SpeedHashChunk(md, name, chunk_len)) {
      return false;
    }
  }

  return true;
}

static bool SpeedRandomChunk(std::string name, size_t chunk_len) {
  uint8_t scratch[16384];

  if (chunk_len > sizeof(scratch)) {
    return false;
  }

  name += ChunkLenSuffix(chunk_len);
  TimeResults results;
  if (!TimeFunction(&results, [chunk_len, &scratch]() -> bool {
        RAND_bytes(scratch, chunk_len);
        return true;
      })) {
    return false;
  }

  results.PrintWithBytes(name, chunk_len);
  return true;
}

static bool SpeedRandom(const std::string &selected) {
  if (!selected.empty() && selected != "RNG") {
    return true;
  }

  for (size_t chunk_len : g_chunk_lengths) {
    if (!SpeedRandomChunk("RNG", chunk_len)) {
      return false;
    }
  }

  return true;
}

static bool SpeedECDHCurve(const std::string &name, int nid,
                           const std::string &selected) {
  if (!selected.empty() && name.find(selected) == std::string::npos) {
    return true;
  }

  bssl::UniquePtr<EC_KEY> peer_key(EC_KEY_new_by_curve_name(nid));
  if (!peer_key ||
      !EC_KEY_generate_key(peer_key.get())) {
    return false;
  }

  size_t peer_value_len = EC_POINT_point2oct(
      EC_KEY_get0_group(peer_key.get()), EC_KEY_get0_public_key(peer_key.get()),
      POINT_CONVERSION_UNCOMPRESSED, nullptr, 0, nullptr);
  if (peer_value_len == 0) {
    return false;
  }
  std::unique_ptr<uint8_t[]> peer_value(new uint8_t[peer_value_len]);
  peer_value_len = EC_POINT_point2oct(
      EC_KEY_get0_group(peer_key.get()), EC_KEY_get0_public_key(peer_key.get()),
      POINT_CONVERSION_UNCOMPRESSED, peer_value.get(), peer_value_len, nullptr);
  if (peer_value_len == 0) {
    return false;
  }

  TimeResults results;
  if (!TimeFunction(&results, [nid, peer_value_len, &peer_value]() -> bool {
        bssl::UniquePtr<EC_KEY> key(EC_KEY_new_by_curve_name(nid));
        if (!key ||
            !EC_KEY_generate_key(key.get())) {
          return false;
        }
        const EC_GROUP *const group = EC_KEY_get0_group(key.get());
        bssl::UniquePtr<EC_POINT> point(EC_POINT_new(group));
        bssl::UniquePtr<EC_POINT> peer_point(EC_POINT_new(group));
        bssl::UniquePtr<BN_CTX> ctx(BN_CTX_new());

        bssl::UniquePtr<BIGNUM> x(BN_new());
        bssl::UniquePtr<BIGNUM> y(BN_new());

        if (!point || !peer_point || !ctx || !x || !y ||
            !EC_POINT_oct2point(group, peer_point.get(), peer_value.get(),
                                peer_value_len, ctx.get()) ||
            !EC_POINT_mul(group, point.get(), NULL, peer_point.get(),
                          EC_KEY_get0_private_key(key.get()), ctx.get()) ||
            !EC_POINT_get_affine_coordinates_GFp(group, point.get(), x.get(),
                                                 y.get(), ctx.get())) {
          return false;
        }

        return true;
      })) {
    return false;
  }

  results.Print(name);
  return true;
}

static bool SpeedECDSACurve(const std::string &name, int nid,
                            const std::string &selected) {
  if (!selected.empty() && name.find(selected) == std::string::npos) {
    return true;
  }

  bssl::UniquePtr<EC_KEY> key(EC_KEY_new_by_curve_name(nid));
  if (!key ||
      !EC_KEY_generate_key(key.get())) {
    return false;
  }

  uint8_t signature[256];
  if (ECDSA_size(key.get()) > sizeof(signature)) {
    return false;
  }
  uint8_t digest[20];
  OPENSSL_memset(digest, 42, sizeof(digest));
  unsigned sig_len;

  TimeResults results;
  if (!TimeFunction(&results, [&key, &signature, &digest, &sig_len]() -> bool {
        return ECDSA_sign(0, digest, sizeof(digest), signature, &sig_len,
                          key.get()) == 1;
      })) {
    return false;
  }

  results.Print(name + " signing");

  if (!TimeFunction(&results, [&key, &signature, &digest, sig_len]() -> bool {
        return ECDSA_verify(0, digest, sizeof(digest), signature, sig_len,
                            key.get()) == 1;
      })) {
    return false;
  }

  results.Print(name + " verify");

  return true;
}

static bool SpeedECDH(const std::string &selected) {
  return SpeedECDHCurve("ECDH P-224", NID_secp224r1, selected) &&
         SpeedECDHCurve("ECDH P-256", NID_X9_62_prime256v1, selected) &&
         SpeedECDHCurve("ECDH P-384", NID_secp384r1, selected) &&
         SpeedECDHCurve("ECDH P-521", NID_secp521r1, selected);
}

static bool SpeedECDSA(const std::string &selected) {
  return SpeedECDSACurve("ECDSA P-224", NID_secp224r1, selected) &&
         SpeedECDSACurve("ECDSA P-256", NID_X9_62_prime256v1, selected) &&
         SpeedECDSACurve("ECDSA P-384", NID_secp384r1, selected) &&
         SpeedECDSACurve("ECDSA P-521", NID_secp521r1, selected);
}

static bool Speed25519(const std::string &selected) {
  if (!selected.empty() && selected.find("25519") == std::string::npos) {
    return true;
  }

  TimeResults results;

  uint8_t public_key[32], private_key[64];

  if (!TimeFunction(&results, [&public_key, &private_key]() -> bool {
        ED25519_keypair(public_key, private_key);
        return true;
      })) {
    return false;
  }

  results.Print("Ed25519 key generation");

  static const uint8_t kMessage[] = {0, 1, 2, 3, 4, 5};
  uint8_t signature[64];

  if (!TimeFunction(&results, [&private_key, &signature]() -> bool {
        return ED25519_sign(signature, kMessage, sizeof(kMessage),
                            private_key) == 1;
      })) {
    return false;
  }

  results.Print("Ed25519 signing");

  if (!TimeFunction(&results, [&public_key, &signature]() -> bool {
        return ED25519_verify(kMessage, sizeof(kMessage), signature,
                              public_key) == 1;
      })) {
    fprintf(stderr, "Ed25519 verify failed.\n");
    return false;
  }

  results.Print("Ed25519 verify");

  if (!TimeFunction(&results, []() -> bool {
        uint8_t out[32], in[32];
        OPENSSL_memset(in, 0, sizeof(in));
        X25519_public_from_private(out, in);
        return true;
      })) {
    fprintf(stderr, "Curve25519 base-point multiplication failed.\n");
    return false;
  }

  results.Print("Curve25519 base-point multiplication");

  if (!TimeFunction(&results, []() -> bool {
        uint8_t out[32], in1[32], in2[32];
        OPENSSL_memset(in1, 0, sizeof(in1));
        OPENSSL_memset(in2, 0, sizeof(in2));
        in1[0] = 1;
        in2[0] = 9;
        return X25519(out, in1, in2) == 1;
      })) {
    fprintf(stderr, "Curve25519 arbitrary point multiplication failed.\n");
    return false;
  }

  results.Print("Curve25519 arbitrary point multiplication");

  return true;
}

static bool SpeedSPAKE2(const std::string &selected) {
  if (!selected.empty() && selected.find("SPAKE2") == std::string::npos) {
    return true;
  }

  TimeResults results;

  static const uint8_t kAliceName[] = {'A'};
  static const uint8_t kBobName[] = {'B'};
  static const uint8_t kPassword[] = "password";
  bssl::UniquePtr<SPAKE2_CTX> alice(SPAKE2_CTX_new(spake2_role_alice,
                                    kAliceName, sizeof(kAliceName), kBobName,
                                    sizeof(kBobName)));
  uint8_t alice_msg[SPAKE2_MAX_MSG_SIZE];
  size_t alice_msg_len;

  if (!SPAKE2_generate_msg(alice.get(), alice_msg, &alice_msg_len,
                           sizeof(alice_msg),
                           kPassword, sizeof(kPassword))) {
    fprintf(stderr, "SPAKE2_generate_msg failed.\n");
    return false;
  }

  if (!TimeFunction(&results, [&alice_msg, alice_msg_len]() -> bool {
        bssl::UniquePtr<SPAKE2_CTX> bob(SPAKE2_CTX_new(spake2_role_bob,
                                        kBobName, sizeof(kBobName), kAliceName,
                                        sizeof(kAliceName)));
        uint8_t bob_msg[SPAKE2_MAX_MSG_SIZE], bob_key[64];
        size_t bob_msg_len, bob_key_len;
        if (!SPAKE2_generate_msg(bob.get(), bob_msg, &bob_msg_len,
                                 sizeof(bob_msg), kPassword,
                                 sizeof(kPassword)) ||
            !SPAKE2_process_msg(bob.get(), bob_key, &bob_key_len,
                                sizeof(bob_key), alice_msg, alice_msg_len)) {
          return false;
        }

        return true;
      })) {
    fprintf(stderr, "SPAKE2 failed.\n");
  }

  results.Print("SPAKE2 over Ed25519");

  return true;
}

static bool SpeedScrypt(const std::string &selected) {
  if (!selected.empty() && selected.find("scrypt") == std::string::npos) {
    return true;
  }

  TimeResults results;

  static const char kPassword[] = "password";
  static const uint8_t kSalt[] = "NaCl";

  if (!TimeFunction(&results, [&]() -> bool {
        uint8_t out[64];
        return !!EVP_PBE_scrypt(kPassword, sizeof(kPassword) - 1, kSalt,
                                sizeof(kSalt) - 1, 1024, 8, 16, 0 /* max_mem */,
                                out, sizeof(out));
      })) {
    fprintf(stderr, "scrypt failed.\n");
    return false;
  }
  results.Print("scrypt (N = 1024, r = 8, p = 16)");

  if (!TimeFunction(&results, [&]() -> bool {
        uint8_t out[64];
        return !!EVP_PBE_scrypt(kPassword, sizeof(kPassword) - 1, kSalt,
                                sizeof(kSalt) - 1, 16384, 8, 1, 0 /* max_mem */,
                                out, sizeof(out));
      })) {
    fprintf(stderr, "scrypt failed.\n");
    return false;
  }
  results.Print("scrypt (N = 16384, r = 8, p = 1)");

  return true;
}

static bool SpeedHRSS(const std::string &selected) {
  if (!selected.empty() && selected != "HRSS") {
    return true;
  }

  TimeResults results;

  if (!TimeFunction(&results, []() -> bool {
    struct HRSS_public_key pub;
    struct HRSS_private_key priv;
    uint8_t entropy[HRSS_GENERATE_KEY_BYTES];
    RAND_bytes(entropy, sizeof(entropy));
    HRSS_generate_key(&pub, &priv, entropy);
    return true;
  })) {
    fprintf(stderr, "Failed to time HRSS_generate_key.\n");
    return false;
  }

  results.Print("HRSS generate");

  struct HRSS_public_key pub;
  struct HRSS_private_key priv;
  uint8_t key_entropy[HRSS_GENERATE_KEY_BYTES];
  RAND_bytes(key_entropy, sizeof(key_entropy));
  HRSS_generate_key(&pub, &priv, key_entropy);

  uint8_t ciphertext[HRSS_CIPHERTEXT_BYTES];
  if (!TimeFunction(&results, [&pub, &ciphertext]() -> bool {
    uint8_t entropy[HRSS_ENCAP_BYTES];
    uint8_t shared_key[HRSS_KEY_BYTES];
    RAND_bytes(entropy, sizeof(entropy));
    HRSS_encap(ciphertext, shared_key, &pub, entropy);
    return true;
  })) {
    fprintf(stderr, "Failed to time HRSS_encap.\n");
    return false;
  }

  results.Print("HRSS encap");

  if (!TimeFunction(&results, [&priv, &ciphertext]() -> bool {
    uint8_t shared_key[HRSS_KEY_BYTES];
    HRSS_decap(shared_key, &priv, ciphertext, sizeof(ciphertext));
    return true;
  })) {
    fprintf(stderr, "Failed to time HRSS_encap.\n");
    return false;
  }

  results.Print("HRSS decap");

  return true;
}

static const struct argument kArguments[] = {
    {
        "-filter",
        kOptionalArgument,
        "A filter on the speed tests to run",
    },
    {
        "-timeout",
        kOptionalArgument,
        "The number of seconds to run each test for (default is 1)",
    },
    {
        "-chunks",
        kOptionalArgument,
        "A comma-separated list of input sizes to run tests at (default is "
        "16,256,1350,8192,16384)",
    },
    {
        "",
        kOptionalArgument,
        "",
    },
};

bool Speed(const std::vector<std::string> &args) {
  std::map<std::string, std::string> args_map;
  if (!ParseKeyValueArguments(&args_map, args, kArguments)) {
    PrintUsage(kArguments);
    return false;
  }

  std::string selected;
  if (args_map.count("-filter") != 0) {
    selected = args_map["-filter"];
  }

  if (args_map.count("-timeout") != 0) {
    g_timeout_seconds = atoi(args_map["-timeout"].c_str());
  }

  if (args_map.count("-chunks") != 0) {
    g_chunk_lengths.clear();
    const char *start = args_map["-chunks"].data();
    const char *end = start + args_map["-chunks"].size();
    while (start != end) {
      errno = 0;
      char *ptr;
      unsigned long long val = strtoull(start, &ptr, 10);
      if (ptr == start /* no numeric characters found */ ||
          errno == ERANGE /* overflow */ ||
          static_cast<size_t>(val) != val) {
        fprintf(stderr, "Error parsing -chunks argument\n");
        return false;
      }
      g_chunk_lengths.push_back(static_cast<size_t>(val));
      start = ptr;
      if (start != end) {
        if (*start != ',') {
          fprintf(stderr, "Error parsing -chunks argument\n");
          return false;
        }
        start++;
      }
    }
  }

  // kTLSADLen is the number of bytes of additional data that TLS passes to
  // AEADs.
  static const size_t kTLSADLen = 13;
  // kLegacyADLen is the number of bytes that TLS passes to the "legacy" AEADs.
  // These are AEADs that weren't originally defined as AEADs, but which we use
  // via the AEAD interface. In order for that to work, they have some TLS
  // knowledge in them and construct a couple of the AD bytes internally.
  static const size_t kLegacyADLen = kTLSADLen - 2;

  if (!SpeedRSA(selected) ||
      !SpeedAEAD(EVP_aead_aes_128_gcm(), "AES-128-GCM", kTLSADLen, selected) ||
      !SpeedAEAD(EVP_aead_aes_256_gcm(), "AES-256-GCM", kTLSADLen, selected) ||
      !SpeedAEAD(EVP_aead_chacha20_poly1305(), "ChaCha20-Poly1305", kTLSADLen,
                 selected) ||
      !SpeedAEAD(EVP_aead_des_ede3_cbc_sha1_tls(), "DES-EDE3-CBC-SHA1",
                 kLegacyADLen, selected) ||
      !SpeedAEAD(EVP_aead_aes_128_cbc_sha1_tls(), "AES-128-CBC-SHA1",
                 kLegacyADLen, selected) ||
      !SpeedAEAD(EVP_aead_aes_256_cbc_sha1_tls(), "AES-256-CBC-SHA1",
                 kLegacyADLen, selected) ||
      !SpeedAEADOpen(EVP_aead_aes_128_cbc_sha1_tls(), "AES-128-CBC-SHA1",
                     kLegacyADLen, selected) ||
      !SpeedAEADOpen(EVP_aead_aes_256_cbc_sha1_tls(), "AES-256-CBC-SHA1",
                     kLegacyADLen, selected) ||
      !SpeedAEAD(EVP_aead_aes_128_gcm_siv(), "AES-128-GCM-SIV", kTLSADLen,
                 selected) ||
      !SpeedAEAD(EVP_aead_aes_256_gcm_siv(), "AES-256-GCM-SIV", kTLSADLen,
                 selected) ||
      !SpeedAEADOpen(EVP_aead_aes_128_gcm_siv(), "AES-128-GCM-SIV", kTLSADLen,
                     selected) ||
      !SpeedAEADOpen(EVP_aead_aes_256_gcm_siv(), "AES-256-GCM-SIV", kTLSADLen,
                     selected) ||
      !SpeedAEAD(EVP_aead_aes_128_ccm_bluetooth(), "AES-128-CCM-Bluetooth",
                 kTLSADLen, selected) ||
      !SpeedAESBlock("AES-128", 128, selected) ||
      !SpeedAESBlock("AES-256", 256, selected) ||
      !SpeedHash(EVP_sha1(), "SHA-1", selected) ||
      !SpeedHash(EVP_sha256(), "SHA-256", selected) ||
      !SpeedHash(EVP_sha512(), "SHA-512", selected) ||
      !SpeedRandom(selected) ||
      !SpeedECDH(selected) ||
      !SpeedECDSA(selected) ||
      !Speed25519(selected) ||
      !SpeedSIKEP434(selected) ||
      !SpeedSPAKE2(selected) ||
      !SpeedScrypt(selected) ||
      !SpeedRSAKeyGen(selected) ||
      !SpeedHRSS(selected)) {
    return false;
  }

  return true;
}

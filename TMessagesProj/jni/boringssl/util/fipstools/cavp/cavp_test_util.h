/* Copyright (c) 2017, Google Inc.
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

#ifndef OPENSSL_HEADER_CRYPTO_FIPSMODULE_CAVP_TEST_UTIL_H
#define OPENSSL_HEADER_CRYPTO_FIPSMODULE_CAVP_TEST_UTIL_H

#include <stdlib.h>
#include <string>
#include <vector>

#include <openssl/aead.h>
#include <openssl/cipher.h>

#include "../crypto/test/file_test.h"


std::string EncodeHex(const uint8_t *in, size_t in_len);

const EVP_CIPHER *GetCipher(const std::string &name);

bool CipherOperation(const EVP_CIPHER *cipher, std::vector<uint8_t> *out,
                     bool encrypt, const std::vector<uint8_t> &key,
                     const std::vector<uint8_t> &iv,
                     const std::vector<uint8_t> &in);

bool AEADEncrypt(const EVP_AEAD *aead, std::vector<uint8_t> *ct,
                 std::vector<uint8_t> *tag, size_t tag_len,
                 const std::vector<uint8_t> &key,
                 const std::vector<uint8_t> &pt,
                 const std::vector<uint8_t> &aad,
                 const std::vector<uint8_t> &iv);

bool AEADDecrypt(const EVP_AEAD *aead, std::vector<uint8_t> *pt, size_t pt_len,
                 const std::vector<uint8_t> &key,
                 const std::vector<uint8_t> &aad,
                 const std::vector<uint8_t> &ct,
                 const std::vector<uint8_t> &tag,
                 const std::vector<uint8_t> &iv);

bssl::UniquePtr<BIGNUM> GetBIGNUM(FileTest *t, const char *attribute);

int GetECGroupNIDFromInstruction(FileTest *t, const char **out_str = nullptr);

const EVP_MD *GetDigestFromInstruction(FileTest *t);

void EchoComment(const std::string& comment);

int cavp_aes_gcm_test_main(int argc, char **argv);
int cavp_aes_test_main(int argc, char **argv);
int cavp_ctr_drbg_test_main(int argc, char **argv);
int cavp_ecdsa2_keypair_test_main(int argc, char **argv);
int cavp_ecdsa2_pkv_test_main(int argc, char **argv);
int cavp_ecdsa2_siggen_test_main(int argc, char **argv);
int cavp_ecdsa2_sigver_test_main(int argc, char **argv);
int cavp_hmac_test_main(int argc, char **argv);
int cavp_kas_test_main(int argc, char **argv);
int cavp_keywrap_test_main(int argc, char **argv);
int cavp_rsa2_keygen_test_main(int argc, char **argv);
int cavp_rsa2_siggen_test_main(int argc, char **argv);
int cavp_rsa2_sigver_test_main(int argc, char **argv);
int cavp_sha_monte_test_main(int argc, char **argv);
int cavp_sha_test_main(int argc, char **argv);
int cavp_tdes_test_main(int argc, char **argv);
int cavp_tlskdf_test_main(int argc, char **argv);


#endif  // OPENSSL_HEADER_CRYPTO_FIPSMODULE_CAVP_TEST_UTIL_H

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

#include "cavp_test_util.h"

#include <openssl/bn.h>
#include <openssl/digest.h>
#include <openssl/ec.h>
#include <openssl/nid.h>


std::string EncodeHex(const uint8_t *in, size_t in_len) {
  static const char kHexDigits[] = "0123456789abcdef";
  std::string ret;
  ret.reserve(in_len * 2);
  for (size_t i = 0; i < in_len; i++) {
    ret += kHexDigits[in[i] >> 4];
    ret += kHexDigits[in[i] & 0xf];
  }
  return ret;
}

const EVP_CIPHER *GetCipher(const std::string &name) {
  if (name == "des-cbc") {
    return EVP_des_cbc();
  } else if (name == "des-ecb") {
    return EVP_des_ecb();
  } else if (name == "des-ede") {
    return EVP_des_ede();
  } else if (name == "des-ede3") {
    return EVP_des_ede3();
  } else if (name == "des-ede-cbc") {
    return EVP_des_ede_cbc();
  } else if (name == "des-ede3-cbc") {
    return EVP_des_ede3_cbc();
  } else if (name == "rc4") {
    return EVP_rc4();
  } else if (name == "aes-128-ecb") {
    return EVP_aes_128_ecb();
  } else if (name == "aes-256-ecb") {
    return EVP_aes_256_ecb();
  } else if (name == "aes-128-cbc") {
    return EVP_aes_128_cbc();
  } else if (name == "aes-128-gcm") {
    return EVP_aes_128_gcm();
  } else if (name == "aes-128-ofb") {
    return EVP_aes_128_ofb();
  } else if (name == "aes-192-cbc") {
    return EVP_aes_192_cbc();
  } else if (name == "aes-192-ctr") {
    return EVP_aes_192_ctr();
  } else if (name == "aes-192-ecb") {
    return EVP_aes_192_ecb();
  } else if (name == "aes-256-cbc") {
    return EVP_aes_256_cbc();
  } else if (name == "aes-128-ctr") {
    return EVP_aes_128_ctr();
  } else if (name == "aes-256-ctr") {
    return EVP_aes_256_ctr();
  } else if (name == "aes-256-gcm") {
    return EVP_aes_256_gcm();
  } else if (name == "aes-256-ofb") {
    return EVP_aes_256_ofb();
  }
  return nullptr;
}

bool CipherOperation(const EVP_CIPHER *cipher, std::vector<uint8_t> *out,
                     bool encrypt, const std::vector<uint8_t> &key,
                     const std::vector<uint8_t> &iv,
                     const std::vector<uint8_t> &in) {
  bssl::ScopedEVP_CIPHER_CTX ctx;
  if (!EVP_CipherInit_ex(ctx.get(), cipher, nullptr, nullptr, nullptr,
                         encrypt ? 1 : 0)) {
    return false;
  }
  if (!iv.empty() && iv.size() != EVP_CIPHER_CTX_iv_length(ctx.get())) {
    return false;
  }

  int result_len1 = 0, result_len2;
  *out = std::vector<uint8_t>(in.size());
  if (!EVP_CIPHER_CTX_set_key_length(ctx.get(), key.size()) ||
      !EVP_CipherInit_ex(ctx.get(), nullptr, nullptr, key.data(), iv.data(),
                         -1) ||
      !EVP_CIPHER_CTX_set_padding(ctx.get(), 0) ||
      !EVP_CipherUpdate(ctx.get(), out->data(), &result_len1, in.data(),
                        in.size()) ||
      !EVP_CipherFinal_ex(ctx.get(), out->data() + result_len1, &result_len2)) {
    return false;
  }
  out->resize(result_len1 + result_len2);

  return true;
}

bool AEADEncrypt(const EVP_AEAD *aead, std::vector<uint8_t> *ct,
                 std::vector<uint8_t> *tag, size_t tag_len,
                 const std::vector<uint8_t> &key,
                 const std::vector<uint8_t> &pt,
                 const std::vector<uint8_t> &aad,
                 const std::vector<uint8_t> &iv) {
  bssl::ScopedEVP_AEAD_CTX ctx;
  if (!EVP_AEAD_CTX_init(ctx.get(), aead, key.data(), key.size(), tag_len,
                         nullptr)) {
    return false;
  }

  std::vector<uint8_t> out;
  out.resize(pt.size() + EVP_AEAD_max_overhead(aead));
  size_t out_len;
  if (!EVP_AEAD_CTX_seal(ctx.get(), out.data(), &out_len, out.size(), iv.data(),
                         iv.size(), pt.data(), pt.size(), aad.data(),
                         aad.size())) {
    return false;
  }
  out.resize(out_len);

  ct->assign(out.begin(), out.end() - tag_len);
  tag->assign(out.end() - tag_len, out.end());

  return true;
}

bool AEADDecrypt(const EVP_AEAD *aead, std::vector<uint8_t> *pt, size_t pt_len,
                 const std::vector<uint8_t> &key,
                 const std::vector<uint8_t> &aad,
                 const std::vector<uint8_t> &ct,
                 const std::vector<uint8_t> &tag,
                 const std::vector<uint8_t> &iv) {
  bssl::ScopedEVP_AEAD_CTX ctx;
  if (!EVP_AEAD_CTX_init_with_direction(ctx.get(), aead, key.data(), key.size(),
                                        tag.size(), evp_aead_open)) {
    return false;
  }
  std::vector<uint8_t> in = ct;
  in.reserve(ct.size() + tag.size());
  in.insert(in.end(), tag.begin(), tag.end());

  pt->resize(pt_len);
  size_t out_pt_len;
  if (!EVP_AEAD_CTX_open(ctx.get(), pt->data(), &out_pt_len, pt->size(),
                         iv.data(), iv.size(), in.data(), in.size(), aad.data(),
                         aad.size()) ||
      out_pt_len != pt_len) {
    return false;
  }
  return true;
}

static int HexToBIGNUM(bssl::UniquePtr<BIGNUM> *out, const char *in) {
  BIGNUM *raw = NULL;
  int ret = BN_hex2bn(&raw, in);
  out->reset(raw);
  return ret;
}

bssl::UniquePtr<BIGNUM> GetBIGNUM(FileTest *t, const char *attribute) {
  std::string hex;
  if (!t->GetAttribute(&hex, attribute)) {
    return nullptr;
  }

  bssl::UniquePtr<BIGNUM> ret;
  if (HexToBIGNUM(&ret, hex.c_str()) != static_cast<int>(hex.size())) {
    t->PrintLine("Could not decode '%s'.", hex.c_str());
    return nullptr;
  }
  return ret;
}

int GetECGroupNIDFromInstruction(FileTest *t, const char **out_str) {
  const char *dummy;
  if (out_str == nullptr) {
    out_str = &dummy;
  }

  if (t->HasInstruction("P-224")) {
    *out_str = "P-224";
    return NID_secp224r1;
  }
  if (t->HasInstruction("P-256")) {
    *out_str = "P-256";
    return NID_X9_62_prime256v1;
  }
  if (t->HasInstruction("P-384")) {
    *out_str = "P-384";
    return NID_secp384r1;
  }
  if (t->HasInstruction("P-521")) {
    *out_str = "P-521";
    return NID_secp521r1;
  }
  t->PrintLine("No supported group specified.");
  return NID_undef;
}

const EVP_MD *GetDigestFromInstruction(FileTest *t) {
  if (t->HasInstruction("SHA-1")) {
    return EVP_sha1();
  }
  if (t->HasInstruction("SHA-224")) {
    return EVP_sha224();
  }
  if (t->HasInstruction("SHA-256")) {
    return EVP_sha256();
  }
  if (t->HasInstruction("SHA-384")) {
    return EVP_sha384();
  }
  if (t->HasInstruction("SHA-512")) {
    return EVP_sha512();
  }
  t->PrintLine("No supported digest function specified.");
  return nullptr;
}

void EchoComment(const std::string& comment) {
  fwrite(comment.c_str(), comment.size(), 1, stdout);
}

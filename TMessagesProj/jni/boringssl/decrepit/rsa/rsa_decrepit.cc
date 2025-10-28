// Copyright 2002-2016 The OpenSSL Project Authors. All Rights Reserved.
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

#include <openssl/rsa.h>

#include <assert.h>

#include <openssl/bn.h>


RSA *RSA_generate_key(int bits, uint64_t e_value, void *callback,
                      void *cb_arg) {
  assert(callback == NULL);
  assert(cb_arg == NULL);

  RSA *rsa = RSA_new();
  BIGNUM *e = BN_new();

  if (rsa == NULL ||
      e == NULL ||
      !BN_set_u64(e, e_value) ||
      !RSA_generate_key_ex(rsa, bits, e, NULL)) {
    goto err;
  }

  BN_free(e);
  return rsa;

err:
  BN_free(e);
  RSA_free(rsa);
  return NULL;
}

int RSA_padding_add_PKCS1_PSS(const RSA *rsa, uint8_t *EM, const uint8_t *mHash,
                              const EVP_MD *Hash, int sLen) {
  return RSA_padding_add_PKCS1_PSS_mgf1(rsa, EM, mHash, Hash, NULL, sLen);
}

int RSA_verify_PKCS1_PSS(const RSA *rsa, const uint8_t *mHash,
                         const EVP_MD *Hash, const uint8_t *EM, int sLen) {
  return RSA_verify_PKCS1_PSS_mgf1(rsa, mHash, Hash, NULL, EM, sLen);
}

int RSA_padding_add_PKCS1_OAEP(uint8_t *to, size_t to_len,
                               const uint8_t *from, size_t from_len,
                               const uint8_t *param, size_t param_len) {
  return RSA_padding_add_PKCS1_OAEP_mgf1(to, to_len, from, from_len, param,
                                         param_len, NULL, NULL);
}

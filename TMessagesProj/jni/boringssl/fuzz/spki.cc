// Copyright 2016 The BoringSSL Authors
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

#include <openssl/bytestring.h>
#include <openssl/err.h>
#include <openssl/evp.h>
#include <openssl/mem.h>

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *buf, size_t len) {
  CBS cbs;
  CBS_init(&cbs, buf, len);
  EVP_PKEY *pkey = EVP_parse_public_key(&cbs);
  if (pkey == NULL) {
    ERR_clear_error();
    return 0;
  }

  uint8_t *der;
  size_t der_len;
  CBB cbb;
  if (CBB_init(&cbb, 0) &&
      EVP_marshal_public_key(&cbb, pkey) &&
      CBB_finish(&cbb, &der, &der_len)) {
    OPENSSL_free(der);
  }
  CBB_cleanup(&cbb);
  EVP_PKEY_free(pkey);
  ERR_clear_error();
  return 0;
}

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

#ifndef OPENSSL_HEADER_AES_IGE_H
#define OPENSSL_HEADER_AES_IGE_H

#include <openssl/base.h>   // IWYU pragma: export
#include <openssl/aes.h>

#if defined(__cplusplus)
extern "C" {
#endif

OPENSSL_EXPORT void AES_ige_encrypt(const unsigned char *in, unsigned char *out,
                     size_t length, const AES_KEY *key,
                     unsigned char *ivec, const int enc);
OPENSSL_EXPORT void AES_bi_ige_encrypt(const unsigned char *in, unsigned char *out,
                        size_t length, const AES_KEY *key,
                        const AES_KEY *key2, const unsigned char *ivec,
                        const int enc);

#if defined(__cplusplus)
}  // extern C
#endif

#endif  // OPENSSL_HEADER_AES_IGE_H

// Copyright 2019 The BoringSSL Authors
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

#include <stdint.h>


#if defined(BORINGSSL_FIPS) && defined(BORINGSSL_SHARED_LIBRARY)
// BORINGSSL_bcm_text_hash is is default hash value for the FIPS integrity check
// that must be replaced with the real value during the build process. This
// value need only be distinct, i.e. so that we can safely search-and-replace it
// in an object file.
extern const uint8_t BORINGSSL_bcm_text_hash[32] = {
    0xae, 0x2c, 0xea, 0x2a, 0xbd, 0xa6, 0xf3, 0xec, 0x97, 0x7f, 0x9b,
    0xf6, 0x94, 0x9a, 0xfc, 0x83, 0x68, 0x27, 0xcb, 0xa0, 0xa0, 0x9f,
    0x6b, 0x6f, 0xde, 0x52, 0xcd, 0xe2, 0xcd, 0xff, 0x31, 0x80,
};
#endif  // FIPS && SHARED_LIBRARY

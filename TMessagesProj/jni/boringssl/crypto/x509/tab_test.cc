// Copyright 1999-2016 The OpenSSL Project Authors. All Rights Reserved.
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

#if !defined(BORINGSSL_SHARED_LIBRARY)

#include <gtest/gtest.h>

#include <openssl/x509.h>

#include "../internal.h"
#include "ext_dat.h"

// Check ext_data.h is correct.
TEST(X509V3Test, TabTest) {
  EXPECT_EQ(OPENSSL_ARRAY_SIZE(standard_exts), STANDARD_EXTENSION_COUNT);
  for (size_t i = 1; i < OPENSSL_ARRAY_SIZE(standard_exts); i++) {
    SCOPED_TRACE(i);
    EXPECT_LT(standard_exts[i-1]->ext_nid, standard_exts[i]->ext_nid);
  }
}

#endif  // !BORINGSSL_SHARED_LIBRARY

// Copyright 2021 The BoringSSL Authors
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

#include "internal.h"

#if defined(OPENSSL_AARCH64) && defined(OPENSSL_APPLE) && \
    !defined(OPENSSL_STATIC_ARMCAP) && !defined(OPENSSL_NO_ASM)

#include <sys/sysctl.h>
#include <sys/types.h>


static int has_hw_feature(const char *name) {
  int value;
  size_t len = sizeof(value);
  if (sysctlbyname(name, &value, &len, NULL, 0) != 0) {
    return 0;
  }
  if (len != sizeof(int)) {
    // This should not happen. All the values queried should be integer-valued.
    assert(0);
    return 0;
  }

  // Per sys/sysctl.h:
  //
  //   Selectors that return errors are not support on the system. Supported
  //   features will return 1 if they are recommended or 0 if they are supported
  //   but are not expected to help performance. Future versions of these
  //   selectors may return larger values as necessary so it is best to test for
  //   non zero.
  return value != 0;
}

void OPENSSL_cpuid_setup(void) {
  // Apple ARM64 platforms have NEON and cryptography extensions available
  // statically, so we do not need to query them. In particular, there sometimes
  // are no sysctls corresponding to such features. See below.
#if !defined(__ARM_NEON) || !defined(__ARM_FEATURE_AES) || \
    !defined(__ARM_FEATURE_SHA2)
#error "NEON and crypto extensions should be statically available."
#endif
  OPENSSL_armcap_P =
      ARMV7_NEON | ARMV8_AES | ARMV8_PMULL | ARMV8_SHA1 | ARMV8_SHA256;

  // See Apple's documentation for sysctl names:
  // https://developer.apple.com/documentation/kernel/1387446-sysctlbyname/determining_instruction_set_characteristics
  //
  // The new feature names, e.g. "hw.optional.arm.FEAT_SHA512", are only
  // available in macOS 12. For compatibility with macOS 11, we also support
  // the old names. The old names don't have values for features like FEAT_AES,
  // so instead we detect them statically above.
  //
  // If querying new sysctls, update the Chromium sandbox definition. See
  // https://crrev.com/c/4415225.
  if (has_hw_feature("hw.optional.arm.FEAT_SHA512") ||
      has_hw_feature("hw.optional.armv8_2_sha512")) {
    OPENSSL_armcap_P |= ARMV8_SHA512;
  }
}

#endif  // OPENSSL_AARCH64 && OPENSSL_APPLE && !OPENSSL_STATIC_ARMCAP

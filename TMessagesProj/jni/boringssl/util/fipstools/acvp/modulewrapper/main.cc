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

#include <inttypes.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <string>
#include <string_view>

#include <openssl/crypto.h>
#include <openssl/span.h>

#include "modulewrapper.h"


int main(int argc, char **argv) {
  if (argc == 2 && strcmp(argv[1], "--version") == 0) {
    printf("Built for architecture: ");

#if defined(OPENSSL_X86_64)
    puts("x86-64 (64-bit)");
#elif defined(OPENSSL_ARM)
    puts("ARM (32-bit)");
#elif defined(OPENSSL_AARCH64)
    puts("aarch64 (64-bit)");
#else
#error "FIPS build not supported on this architecture"
#endif

    if (!FIPS_mode()) {
      printf("Module not in FIPS mode\n");
      abort();
    }
    printf("Module is in FIPS mode\n");

    const uint32_t module_version = FIPS_version();
    if (module_version == 0) {
      printf("No module version set\n");
      abort();
    }
    printf("Module: '%s', version: %" PRIu32 " hash:\n", FIPS_module_name(),
           module_version);

#if !defined(BORINGSSL_FIPS)
    // |module_version| will be zero, so the non-FIPS build will never get
    // this far.
    printf("Non zero module version in non-FIPS build - should not happen!\n");
    abort();
#elif defined(OPENSSL_ASAN)
    printf("(not available when compiled for ASAN)");
#else
    const uint8_t *module_hash = FIPS_module_hash();
    for (size_t i = 0; i < SHA256_DIGEST_LENGTH; i++) {
      printf("%02x", module_hash[i]);
    }
    printf("\n");
#endif
    printf("Hardware acceleration enabled: %s\n",
           CRYPTO_has_asm() ? "yes" : "no");

    return 0;
  } else if (argc != 1) {
    fprintf(stderr, "Usage: %s [--version]\n", argv[0]);
    return 4;
  }

  // modulewrapper buffers responses to the greatest degree allowed in order to
  // fully exercise the async handling in acvptool.
  std::unique_ptr<bssl::acvp::RequestBuffer> buffer =
      bssl::acvp::RequestBuffer::New();
  const bssl::acvp::ReplyCallback write_reply = std::bind(
      bssl::acvp::WriteReplyToFd, STDOUT_FILENO, std::placeholders::_1);
  const bssl::acvp::ReplyCallback buffer_reply =
      std::bind(bssl::acvp::WriteReplyToBuffer, std::placeholders::_1);

  for (;;) {
    const bssl::Span<const bssl::Span<const uint8_t>> args =
        ParseArgsFromFd(STDIN_FILENO, buffer.get());
    if (args.empty()) {
      return 1;
    }

    auto name = bssl::BytesAsStringView(args[0]);
    if (name == "flush") {
      if (!bssl::acvp::FlushBuffer(STDOUT_FILENO)) {
        abort();
      }
      continue;
    }

    const bssl::acvp::Handler handler = bssl::acvp::FindHandler(args);
    if (!handler) {
      return 2;
    }

    auto &reply_callback = name == "getConfig" ? write_reply : buffer_reply;
    if (!handler(args.subspan(1).data(), reply_callback)) {
      fprintf(stderr, "\'%s\' operation failed.\n", std::string(name).c_str());
      return 3;
    }
  }
}

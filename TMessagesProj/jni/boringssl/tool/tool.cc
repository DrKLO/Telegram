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

#include <string>
#include <vector>

#include <openssl/crypto.h>
#include <openssl/err.h>
#include <openssl/ssl.h>

#if defined(OPENSSL_WINDOWS)
#include <fcntl.h>
#include <io.h>
#else
#include <libgen.h>
#endif

#include "internal.h"


static bool IsFIPS(const std::vector<std::string> &args) {
  printf("%d\n", FIPS_mode());
  return true;
}

typedef bool (*tool_func_t)(const std::vector<std::string> &args);

struct Tool {
  const char *name;
  tool_func_t func;
};

static const Tool kTools[] = {
  { "ciphers", Ciphers },
  { "client", Client },
  { "isfips", IsFIPS },
  { "generate-ed25519", GenerateEd25519Key },
  { "genrsa", GenerateRSAKey },
  { "md5sum", MD5Sum },
  { "pkcs12", DoPKCS12 },
  { "rand", Rand },
  { "s_client", Client },
  { "s_server", Server },
  { "server", Server },
  { "sha1sum", SHA1Sum },
  { "sha224sum", SHA224Sum },
  { "sha256sum", SHA256Sum },
  { "sha384sum", SHA384Sum },
  { "sha512sum", SHA512Sum },
  { "sign", Sign },
  { "speed", Speed },
  { "", nullptr },
};

static void usage(const char *name) {
  printf("Usage: %s COMMAND\n", name);
  printf("\n");
  printf("Available commands:\n");

  for (size_t i = 0;; i++) {
    const Tool &tool = kTools[i];
    if (tool.func == nullptr) {
      break;
    }
    printf("    %s\n", tool.name);
  }
}

static tool_func_t FindTool(const std::string &name) {
  for (size_t i = 0;; i++) {
    const Tool &tool = kTools[i];
    if (tool.func == nullptr || name == tool.name) {
      return tool.func;
    }
  }
}

int main(int argc, char **argv) {
#if defined(OPENSSL_WINDOWS)
  // Read and write in binary mode. This makes bssl on Windows consistent with
  // bssl on other platforms, and also makes it consistent with MSYS's commands
  // like diff(1) and md5sum(1). This is especially important for the digest
  // commands.
  if (_setmode(_fileno(stdin), _O_BINARY) == -1) {
    perror("_setmode(_fileno(stdin), O_BINARY)");
    return 1;
  }
  if (_setmode(_fileno(stdout), _O_BINARY) == -1) {
    perror("_setmode(_fileno(stdout), O_BINARY)");
    return 1;
  }
  if (_setmode(_fileno(stderr), _O_BINARY) == -1) {
    perror("_setmode(_fileno(stderr), O_BINARY)");
    return 1;
  }
#endif

  CRYPTO_library_init();

  int starting_arg = 1;
  tool_func_t tool = nullptr;
#if !defined(OPENSSL_WINDOWS)
  tool = FindTool(basename(argv[0]));
#endif
  if (tool == nullptr) {
    starting_arg++;
    if (argc > 1) {
      tool = FindTool(argv[1]);
    }
  }
  if (tool == nullptr) {
    usage(argv[0]);
    return 1;
  }

  std::vector<std::string> args;
  for (int i = starting_arg; i < argc; i++) {
    args.push_back(argv[i]);
  }

  if (!tool(args)) {
    ERR_print_errors_fp(stderr);
    return 1;
  }

  return 0;
}

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

#include <openssl/base.h>

#include <memory>
#include <string>
#include <vector>

#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#if defined(OPENSSL_WINDOWS)
#include <io.h>
#else
#include <unistd.h>
#endif

#include <openssl/bytestring.h>
#include <openssl/err.h>
#include <openssl/pem.h>
#include <openssl/pkcs8.h>
#include <openssl/stack.h>

#include "../crypto/internal.h"
#include "internal.h"


#if defined(OPENSSL_WINDOWS)
typedef int read_result_t;
#else
typedef ssize_t read_result_t;
#endif

static const struct argument kArguments[] = {
    {
     "-dump", kOptionalArgument,
     "Dump the key and contents of the given file to stdout",
    },
    {
     "", kOptionalArgument, "",
    },
};

bool DoPKCS12(const std::vector<std::string> &args) {
  std::map<std::string, std::string> args_map;

  if (!ParseKeyValueArguments(&args_map, args, kArguments) ||
      args_map["-dump"].empty()) {
    PrintUsage(kArguments);
    return false;
  }

  int fd = BORINGSSL_OPEN(args_map["-dump"].c_str(), O_RDONLY);
  if (fd < 0) {
    perror("open");
    return false;
  }

  struct stat st;
  if (fstat(fd, &st)) {
    perror("fstat");
    BORINGSSL_CLOSE(fd);
    return false;
  }
  const size_t size = st.st_size;

  std::unique_ptr<uint8_t[]> contents(new uint8_t[size]);
  read_result_t n;
  size_t off = 0;
  do {
    n = BORINGSSL_READ(fd, &contents[off], size - off);
    if (n >= 0) {
      off += static_cast<size_t>(n);
    }
  } while ((n > 0 && off < size) || (n == -1 && errno == EINTR));

  if (off != size) {
    perror("read");
    BORINGSSL_CLOSE(fd);
    return false;
  }

  BORINGSSL_CLOSE(fd);

  printf("Enter password: ");
  fflush(stdout);

  char password[256];
  off = 0;
  do {
    n = BORINGSSL_READ(0, &password[off], sizeof(password) - 1 - off);
    if (n >= 0) {
      off += static_cast<size_t>(n);
    }
  } while ((n > 0 && OPENSSL_memchr(password, '\n', off) == NULL &&
            off < sizeof(password) - 1) ||
           (n == -1 && errno == EINTR));

  char *newline = reinterpret_cast<char *>(OPENSSL_memchr(password, '\n', off));
  if (newline == NULL) {
    return false;
  }
  *newline = 0;

  CBS pkcs12;
  CBS_init(&pkcs12, contents.get(), size);

  EVP_PKEY *key;
  bssl::UniquePtr<STACK_OF(X509)> certs(sk_X509_new_null());

  if (!PKCS12_get_key_and_certs(&key, certs.get(), &pkcs12, password)) {
    fprintf(stderr, "Failed to parse PKCS#12 data:\n");
    ERR_print_errors_fp(stderr);
    return false;
  }
  bssl::UniquePtr<EVP_PKEY> key_owned(key);

  if (key != NULL) {
    PEM_write_PrivateKey(stdout, key, NULL, NULL, 0, NULL, NULL);
  }

  for (size_t i = 0; i < sk_X509_num(certs.get()); i++) {
    PEM_write_X509(stdout, sk_X509_value(certs.get(), i));
  }

  return true;
}

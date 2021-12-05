/* Copyright (c) 2015, Google Inc.
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

#include <stdint.h>
#include <stdlib.h>

#include <openssl/ssl.h>

#include "internal.h"


bool Ciphers(const std::vector<std::string> &args) {
  bool openssl_name = false;
  if (args.size() == 2 && args[0] == "-openssl-name") {
    openssl_name = true;
  } else if (args.size() != 1) {
    fprintf(stderr,
            "Usage: bssl ciphers [-openssl-name] <cipher suite string>\n");
    return false;
  }

  const std::string &ciphers_string = args.back();

  bssl::UniquePtr<SSL_CTX> ctx(SSL_CTX_new(TLS_method()));
  if (!SSL_CTX_set_strict_cipher_list(ctx.get(), ciphers_string.c_str())) {
    fprintf(stderr, "Failed to parse cipher suite config.\n");
    ERR_print_errors_fp(stderr);
    return false;
  }

  STACK_OF(SSL_CIPHER) *ciphers = SSL_CTX_get_ciphers(ctx.get());

  bool last_in_group = false;
  for (size_t i = 0; i < sk_SSL_CIPHER_num(ciphers); i++) {
    bool in_group = SSL_CTX_cipher_in_group(ctx.get(), i);
    const SSL_CIPHER *cipher = sk_SSL_CIPHER_value(ciphers, i);

    if (in_group && !last_in_group) {
      printf("[\n  ");
    } else if (last_in_group) {
      printf("  ");
    }

    printf("%s\n", openssl_name ? SSL_CIPHER_get_name(cipher)
                                : SSL_CIPHER_standard_name(cipher));

    if (!in_group && last_in_group) {
      printf("]\n");
    }
    last_in_group = in_group;
  }

  return true;
}

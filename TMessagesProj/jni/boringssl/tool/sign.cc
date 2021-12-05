/* Copyright (c) 2017, Google Inc.
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

#include <map>
#include <vector>

#include <openssl/bio.h>
#include <openssl/evp.h>
#include <openssl/pem.h>

#include "internal.h"


static const struct argument kArguments[] = {
    {"-key", kRequiredArgument, "The private key, in PEM format, to sign with"},
    {"-digest", kOptionalArgument, "The digest algorithm to use"},
    {"", kOptionalArgument, ""},
};

bool Sign(const std::vector<std::string> &args) {
  std::map<std::string, std::string> args_map;
  if (!ParseKeyValueArguments(&args_map, args, kArguments)) {
    PrintUsage(kArguments);
    return false;
  }

  // Load the private key.
  bssl::UniquePtr<BIO> bio(BIO_new(BIO_s_file()));
  if (!bio || !BIO_read_filename(bio.get(), args_map["-key"].c_str())) {
    return false;
  }
  bssl::UniquePtr<EVP_PKEY> key(
      PEM_read_bio_PrivateKey(bio.get(), nullptr, nullptr, nullptr));
  if (!key) {
    return false;
  }

  const EVP_MD *md = nullptr;
  if (args_map.count("-digest")) {
    md = EVP_get_digestbyname(args_map["-digest"].c_str());
    if (md == nullptr) {
      fprintf(stderr, "Unknown digest algorithm: %s\n",
              args_map["-digest"].c_str());
      return false;
    }
  }

  bssl::ScopedEVP_MD_CTX ctx;
  if (!EVP_DigestSignInit(ctx.get(), nullptr, md, nullptr, key.get())) {
    return false;
  }

  std::vector<uint8_t> data;
  if (!ReadAll(&data, stdin)) {
    fprintf(stderr, "Error reading input.\n");
    return false;
  }

  size_t sig_len = EVP_PKEY_size(key.get());
  std::unique_ptr<uint8_t[]> sig(new uint8_t[sig_len]);
  if (!EVP_DigestSign(ctx.get(), sig.get(), &sig_len, data.data(),
                      data.size())) {
    return false;
  }

  if (fwrite(sig.get(), 1, sig_len, stdout) != sig_len) {
    fprintf(stderr, "Error writing signature.\n");
    return false;
  }

  return true;
}

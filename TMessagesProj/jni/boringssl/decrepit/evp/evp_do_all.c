/* Copyright (c) 2016, Google Inc.
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

#include <openssl/evp.h>


void EVP_CIPHER_do_all_sorted(void (*callback)(const EVP_CIPHER *cipher,
                                               const char *name,
                                               const char *unused, void *arg),
                              void *arg) {
  callback(EVP_aes_128_cbc(), "AES-128-CBC", NULL, arg);
  callback(EVP_aes_192_cbc(), "AES-192-CBC", NULL, arg);
  callback(EVP_aes_256_cbc(), "AES-256-CBC", NULL, arg);
  callback(EVP_aes_128_ctr(), "AES-128-CTR", NULL, arg);
  callback(EVP_aes_192_ctr(), "AES-192-CTR", NULL, arg);
  callback(EVP_aes_256_ctr(), "AES-256-CTR", NULL, arg);
  callback(EVP_aes_128_ecb(), "AES-128-ECB", NULL, arg);
  callback(EVP_aes_192_ecb(), "AES-192-ECB", NULL, arg);
  callback(EVP_aes_256_ecb(), "AES-256-ECB", NULL, arg);
  callback(EVP_aes_128_ofb(), "AES-128-OFB", NULL, arg);
  callback(EVP_aes_192_ofb(), "AES-192-OFB", NULL, arg);
  callback(EVP_aes_256_ofb(), "AES-256-OFB", NULL, arg);
  callback(EVP_aes_128_gcm(), "AES-128-GCM", NULL, arg);
  callback(EVP_aes_192_gcm(), "AES-192-GCM", NULL, arg);
  callback(EVP_aes_256_gcm(), "AES-256-GCM", NULL, arg);
  callback(EVP_des_cbc(), "DES-CBC", NULL, arg);
  callback(EVP_des_ecb(), "DES-ECB", NULL, arg);
  callback(EVP_des_ede(), "DES-EDE", NULL, arg);
  callback(EVP_des_ede_cbc(), "DES-EDE-CBC", NULL, arg);
  callback(EVP_des_ede3_cbc(), "DES-EDE3-CBC", NULL, arg);
  callback(EVP_rc2_cbc(), "RC2-CBC", NULL, arg);
  callback(EVP_rc4(), "RC4", NULL, arg);

  // OpenSSL returns everything twice, the second time in lower case.
  callback(EVP_aes_128_cbc(), "aes-128-cbc", NULL, arg);
  callback(EVP_aes_192_cbc(), "aes-192-cbc", NULL, arg);
  callback(EVP_aes_256_cbc(), "aes-256-cbc", NULL, arg);
  callback(EVP_aes_128_ctr(), "aes-128-ctr", NULL, arg);
  callback(EVP_aes_192_ctr(), "aes-192-ctr", NULL, arg);
  callback(EVP_aes_256_ctr(), "aes-256-ctr", NULL, arg);
  callback(EVP_aes_128_ecb(), "aes-128-ecb", NULL, arg);
  callback(EVP_aes_192_ecb(), "aes-192-ecb", NULL, arg);
  callback(EVP_aes_256_ecb(), "aes-256-ecb", NULL, arg);
  callback(EVP_aes_128_ofb(), "aes-128-ofb", NULL, arg);
  callback(EVP_aes_192_ofb(), "aes-192-ofb", NULL, arg);
  callback(EVP_aes_256_ofb(), "aes-256-ofb", NULL, arg);
  callback(EVP_aes_128_gcm(), "aes-128-gcm", NULL, arg);
  callback(EVP_aes_192_gcm(), "aes-192-gcm", NULL, arg);
  callback(EVP_aes_256_gcm(), "aes-256-gcm", NULL, arg);
  callback(EVP_des_cbc(), "des-cbc", NULL, arg);
  callback(EVP_des_ecb(), "des-ecb", NULL, arg);
  callback(EVP_des_ede(), "des-ede", NULL, arg);
  callback(EVP_des_ede_cbc(), "des-ede-cbc", NULL, arg);
  callback(EVP_des_ede3_cbc(), "des-ede3-cbc", NULL, arg);
  callback(EVP_rc2_cbc(), "rc2-cbc", NULL, arg);
  callback(EVP_rc4(), "rc4", NULL, arg);
}

void EVP_MD_do_all_sorted(void (*callback)(const EVP_MD *cipher,
                                           const char *name, const char *unused,
                                           void *arg),
                          void *arg) {
  callback(EVP_md4(), "MD4", NULL, arg);
  callback(EVP_md5(), "MD5", NULL, arg);
  callback(EVP_sha1(), "SHA1", NULL, arg);
  callback(EVP_sha224(), "SHA224", NULL, arg);
  callback(EVP_sha256(), "SHA256", NULL, arg);
  callback(EVP_sha384(), "SHA384", NULL, arg);
  callback(EVP_sha512(), "SHA512", NULL, arg);

  callback(EVP_md4(), "md4", NULL, arg);
  callback(EVP_md5(), "md5", NULL, arg);
  callback(EVP_sha1(), "sha1", NULL, arg);
  callback(EVP_sha224(), "sha224", NULL, arg);
  callback(EVP_sha256(), "sha256", NULL, arg);
  callback(EVP_sha384(), "sha384", NULL, arg);
  callback(EVP_sha512(), "sha512", NULL, arg);
}

/*
 * Copyright 2006-2017 The OpenSSL Project Authors. All Rights Reserved.
 *
 * Licensed under the OpenSSL license (the "License").  You may not use
 * this file except in compliance with the License.  You can obtain a copy
 * in the file LICENSE in the source distribution or at
 * https://www.openssl.org/source/license.html
 */

#include <openssl/rsa.h>

#include <openssl/evp.h>


int RSA_print(BIO *bio, const RSA *rsa, int indent) {
  EVP_PKEY *pkey = EVP_PKEY_new();
  int ret = pkey != NULL &&
            EVP_PKEY_set1_RSA(pkey, (RSA *)rsa) &&
            EVP_PKEY_print_private(bio, pkey, indent, NULL);
  EVP_PKEY_free(pkey);
  return ret;
}

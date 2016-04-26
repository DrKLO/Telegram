/* Originally written by Bodo Moeller for the OpenSSL project.
 * ====================================================================
 * Copyright (c) 1998-2005 The OpenSSL Project.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All advertising materials mentioning features or use of this
 *    software must display the following acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit. (http://www.openssl.org/)"
 *
 * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    openssl-core@openssl.org.
 *
 * 5. Products derived from this software may not be called "OpenSSL"
 *    nor may "OpenSSL" appear in their names without prior written
 *    permission of the OpenSSL Project.
 *
 * 6. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit (http://www.openssl.org/)"
 *
 * THIS SOFTWARE IS PROVIDED BY THE OpenSSL PROJECT ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * This product includes cryptographic software written by Eric Young
 * (eay@cryptsoft.com).  This product includes software written by Tim
 * Hudson (tjh@cryptsoft.com).
 *
 */
/* ====================================================================
 * Copyright 2002 Sun Microsystems, Inc. ALL RIGHTS RESERVED.
 *
 * Portions of the attached software ("Contribution") are developed by
 * SUN MICROSYSTEMS, INC., and are contributed to the OpenSSL project.
 *
 * The Contribution is licensed pursuant to the OpenSSL open source
 * license provided above.
 *
 * The elliptic curve binary polynomial software is originally written by
 * Sheueling Chang Shantz and Douglas Stebila of Sun Microsystems
 * Laboratories. */

#include <stdio.h>

#include <openssl/bn.h>
#include <openssl/crypto.h>
#include <openssl/ec.h>
#include <openssl/obj.h>


int example_EC_POINT_mul(void) {
  /* This example ensures that 10×∞ + G = G, in P-256. */
  EC_GROUP *group = NULL;
  EC_POINT *p = NULL, *result = NULL;
  BIGNUM *n = NULL;
  int ret = 0;
  const EC_POINT *generator;

  group = EC_GROUP_new_by_curve_name(NID_X9_62_prime256v1);
  p = EC_POINT_new(group);
  result = EC_POINT_new(group);
  n = BN_new();

  if (p == NULL ||
      result == NULL ||
      group == NULL ||
      n == NULL ||
      !EC_POINT_set_to_infinity(group, p) ||
      !BN_set_word(n, 10)) {
    goto err;
  }

  /* First check that 10×∞ = ∞. */
  if (!EC_POINT_mul(group, result, NULL, p, n, NULL) ||
      !EC_POINT_is_at_infinity(group, result)) {
    goto err;
  }

  generator = EC_GROUP_get0_generator(group);

  /* Now check that 10×∞ + G = G. */
  if (!EC_POINT_mul(group, result, BN_value_one(), p, n, NULL) ||
      EC_POINT_cmp(group, result, generator, NULL) != 0) {
    goto err;
  }

  ret = 1;

err:
  BN_free(n);
  EC_POINT_free(result);
  EC_POINT_free(p);
  EC_GROUP_free(group);

  return ret;
}

int main(void) {
  CRYPTO_library_init();

  if (!example_EC_POINT_mul()) {
    fprintf(stderr, "failed\n");
    return 1;
  }

  printf("PASS\n");
  return 0;
}

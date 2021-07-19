/* ====================================================================
 * Copyright (c) 2011 The OpenSSL Project.  All rights reserved.
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
 *    for use in the OpenSSL Toolkit. (http://www.OpenSSL.org/)"
 *
 * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    licensing@OpenSSL.org.
 *
 * 5. Products derived from this software may not be called "OpenSSL"
 *    nor may "OpenSSL" appear in their names without prior written
 *    permission of the OpenSSL Project.
 *
 * 6. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit (http://www.OpenSSL.org/)"
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
 * Hudson (tjh@cryptsoft.com). */

#include <openssl/dh.h>

#include <openssl/bn.h>

#include "../fipsmodule/bn/internal.h"


BIGNUM *BN_get_rfc3526_prime_1536(BIGNUM *ret) {
  static const BN_ULONG kPrime1536Data[] = {
      TOBN(0xffffffff, 0xffffffff), TOBN(0xf1746c08, 0xca237327),
      TOBN(0x670c354e, 0x4abc9804), TOBN(0x9ed52907, 0x7096966d),
      TOBN(0x1c62f356, 0x208552bb), TOBN(0x83655d23, 0xdca3ad96),
      TOBN(0x69163fa8, 0xfd24cf5f), TOBN(0x98da4836, 0x1c55d39a),
      TOBN(0xc2007cb8, 0xa163bf05), TOBN(0x49286651, 0xece45b3d),
      TOBN(0xae9f2411, 0x7c4b1fe6), TOBN(0xee386bfb, 0x5a899fa5),
      TOBN(0x0bff5cb6, 0xf406b7ed), TOBN(0xf44c42e9, 0xa637ed6b),
      TOBN(0xe485b576, 0x625e7ec6), TOBN(0x4fe1356d, 0x6d51c245),
      TOBN(0x302b0a6d, 0xf25f1437), TOBN(0xef9519b3, 0xcd3a431b),
      TOBN(0x514a0879, 0x8e3404dd), TOBN(0x020bbea6, 0x3b139b22),
      TOBN(0x29024e08, 0x8a67cc74), TOBN(0xc4c6628b, 0x80dc1cd1),
      TOBN(0xc90fdaa2, 0x2168c234), TOBN(0xffffffff, 0xffffffff),
  };

  static const BIGNUM kPrime1536BN = STATIC_BIGNUM(kPrime1536Data);

  BIGNUM *alloc = NULL;
  if (ret == NULL) {
    alloc = BN_new();
    if (alloc == NULL) {
      return NULL;
    }
    ret = alloc;
  }

  if (!BN_copy(ret, &kPrime1536BN)) {
    BN_free(alloc);
    return NULL;
  }

  return ret;
}

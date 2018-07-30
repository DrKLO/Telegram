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


#include <openssl/bytestring.h>

#include <assert.h>
#include <limits.h>
#include <string.h>

#include <openssl/mem.h>

#include "internal.h"
#include "../internal.h"


int CBB_finish_i2d(CBB *cbb, uint8_t **outp) {
  assert(cbb->base->can_resize);

  uint8_t *der;
  size_t der_len;
  if (!CBB_finish(cbb, &der, &der_len)) {
    CBB_cleanup(cbb);
    return -1;
  }
  if (der_len > INT_MAX) {
    OPENSSL_free(der);
    return -1;
  }
  if (outp != NULL) {
    if (*outp == NULL) {
      *outp = der;
      der = NULL;
    } else {
      OPENSSL_memcpy(*outp, der, der_len);
      *outp += der_len;
    }
  }
  OPENSSL_free(der);
  return (int)der_len;
}

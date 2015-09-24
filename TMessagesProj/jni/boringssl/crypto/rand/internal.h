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

#ifndef OPENSSL_HEADER_CRYPTO_RAND_INTERNAL_H
#define OPENSSL_HEADER_CRYPTO_RAND_INTERNAL_H

#if defined(__cplusplus)
extern "C" {
#endif


/* CRYPTO_sysrand fills |len| bytes at |buf| with entropy from the operating
 * system. */
void CRYPTO_sysrand(uint8_t *buf, size_t len);

/* CRYPTO_hwrand fills |len| bytes at |buf| with entropy from the hardware. It
 * returns one on success or zero on hardware failure or if hardware support is
 * unavailable. */
int CRYPTO_hwrand(uint8_t *buf, size_t len);


#if defined(__cplusplus)
}  /* extern C */
#endif

#endif  /* OPENSSL_HEADER_CRYPTO_RAND_INTERNAL_H */

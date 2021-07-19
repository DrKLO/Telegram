/* ====================================================================
 * Copyright (c) 2012 The OpenSSL Project.  All rights reserved.
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
 * Hudson (tjh@cryptsoft.com). */

#include <assert.h>
#include <string.h>

#include <openssl/digest.h>
#include <openssl/nid.h>
#include <openssl/sha.h>

#include "../internal.h"
#include "internal.h"
#include "../fipsmodule/cipher/internal.h"


// MAX_HASH_BIT_COUNT_BYTES is the maximum number of bytes in the hash's length
// field. (SHA-384/512 have 128-bit length.)
#define MAX_HASH_BIT_COUNT_BYTES 16

// MAX_HASH_BLOCK_SIZE is the maximum hash block size that we'll support.
// Currently SHA-384/512 has a 128-byte block size and that's the largest
// supported by TLS.)
#define MAX_HASH_BLOCK_SIZE 128

int EVP_tls_cbc_remove_padding(crypto_word_t *out_padding_ok, size_t *out_len,
                               const uint8_t *in, size_t in_len,
                               size_t block_size, size_t mac_size) {
  const size_t overhead = 1 /* padding length byte */ + mac_size;

  // These lengths are all public so we can test them in non-constant time.
  if (overhead > in_len) {
    return 0;
  }

  size_t padding_length = in[in_len - 1];

  crypto_word_t good = constant_time_ge_w(in_len, overhead + padding_length);
  // The padding consists of a length byte at the end of the record and
  // then that many bytes of padding, all with the same value as the
  // length byte. Thus, with the length byte included, there are i+1
  // bytes of padding.
  //
  // We can't check just |padding_length+1| bytes because that leaks
  // decrypted information. Therefore we always have to check the maximum
  // amount of padding possible. (Again, the length of the record is
  // public information so we can use it.)
  size_t to_check = 256;  // maximum amount of padding, inc length byte.
  if (to_check > in_len) {
    to_check = in_len;
  }

  for (size_t i = 0; i < to_check; i++) {
    uint8_t mask = constant_time_ge_8(padding_length, i);
    uint8_t b = in[in_len - 1 - i];
    // The final |padding_length+1| bytes should all have the value
    // |padding_length|. Therefore the XOR should be zero.
    good &= ~(mask & (padding_length ^ b));
  }

  // If any of the final |padding_length+1| bytes had the wrong value,
  // one or more of the lower eight bits of |good| will be cleared.
  good = constant_time_eq_w(0xff, good & 0xff);

  // Always treat |padding_length| as zero on error. If, assuming block size of
  // 16, a padding of [<15 arbitrary bytes> 15] treated |padding_length| as 16
  // and returned -1, distinguishing good MAC and bad padding from bad MAC and
  // bad padding would give POODLE's padding oracle.
  padding_length = good & (padding_length + 1);
  *out_len = in_len - padding_length;
  *out_padding_ok = good;
  return 1;
}

void EVP_tls_cbc_copy_mac(uint8_t *out, size_t md_size, const uint8_t *in,
                          size_t in_len, size_t orig_len) {
  uint8_t rotated_mac1[EVP_MAX_MD_SIZE], rotated_mac2[EVP_MAX_MD_SIZE];
  uint8_t *rotated_mac = rotated_mac1;
  uint8_t *rotated_mac_tmp = rotated_mac2;

  // mac_end is the index of |in| just after the end of the MAC.
  size_t mac_end = in_len;
  size_t mac_start = mac_end - md_size;

  assert(orig_len >= in_len);
  assert(in_len >= md_size);
  assert(md_size <= EVP_MAX_MD_SIZE);

  // scan_start contains the number of bytes that we can ignore because
  // the MAC's position can only vary by 255 bytes.
  size_t scan_start = 0;
  // This information is public so it's safe to branch based on it.
  if (orig_len > md_size + 255 + 1) {
    scan_start = orig_len - (md_size + 255 + 1);
  }

  size_t rotate_offset = 0;
  uint8_t mac_started = 0;
  OPENSSL_memset(rotated_mac, 0, md_size);
  for (size_t i = scan_start, j = 0; i < orig_len; i++, j++) {
    if (j >= md_size) {
      j -= md_size;
    }
    crypto_word_t is_mac_start = constant_time_eq_w(i, mac_start);
    mac_started |= is_mac_start;
    uint8_t mac_ended = constant_time_ge_8(i, mac_end);
    rotated_mac[j] |= in[i] & mac_started & ~mac_ended;
    // Save the offset that |mac_start| is mapped to.
    rotate_offset |= j & is_mac_start;
  }

  // Now rotate the MAC. We rotate in log(md_size) steps, one for each bit
  // position.
  for (size_t offset = 1; offset < md_size; offset <<= 1, rotate_offset >>= 1) {
    // Rotate by |offset| iff the corresponding bit is set in
    // |rotate_offset|, placing the result in |rotated_mac_tmp|.
    const uint8_t skip_rotate = (rotate_offset & 1) - 1;
    for (size_t i = 0, j = offset; i < md_size; i++, j++) {
      if (j >= md_size) {
        j -= md_size;
      }
      rotated_mac_tmp[i] =
          constant_time_select_8(skip_rotate, rotated_mac[i], rotated_mac[j]);
    }

    // Swap pointers so |rotated_mac| contains the (possibly) rotated value.
    // Note the number of iterations and thus the identity of these pointers is
    // public information.
    uint8_t *tmp = rotated_mac;
    rotated_mac = rotated_mac_tmp;
    rotated_mac_tmp = tmp;
  }

  OPENSSL_memcpy(out, rotated_mac, md_size);
}

// u32toBE serialises an unsigned, 32-bit number (n) as four bytes at (p) in
// big-endian order. The value of p is advanced by four.
#define u32toBE(n, p)                \
  do {                               \
    *((p)++) = (uint8_t)((n) >> 24); \
    *((p)++) = (uint8_t)((n) >> 16); \
    *((p)++) = (uint8_t)((n) >> 8);  \
    *((p)++) = (uint8_t)((n));       \
  } while (0)

// u64toBE serialises an unsigned, 64-bit number (n) as eight bytes at (p) in
// big-endian order. The value of p is advanced by eight.
#define u64toBE(n, p)                \
  do {                               \
    *((p)++) = (uint8_t)((n) >> 56); \
    *((p)++) = (uint8_t)((n) >> 48); \
    *((p)++) = (uint8_t)((n) >> 40); \
    *((p)++) = (uint8_t)((n) >> 32); \
    *((p)++) = (uint8_t)((n) >> 24); \
    *((p)++) = (uint8_t)((n) >> 16); \
    *((p)++) = (uint8_t)((n) >> 8);  \
    *((p)++) = (uint8_t)((n));       \
  } while (0)

typedef union {
  SHA_CTX sha1;
  SHA256_CTX sha256;
  SHA512_CTX sha512;
} HASH_CTX;

static void tls1_sha1_transform(HASH_CTX *ctx, const uint8_t *block) {
  SHA1_Transform(&ctx->sha1, block);
}

static void tls1_sha256_transform(HASH_CTX *ctx, const uint8_t *block) {
  SHA256_Transform(&ctx->sha256, block);
}

static void tls1_sha512_transform(HASH_CTX *ctx, const uint8_t *block) {
  SHA512_Transform(&ctx->sha512, block);
}

// These functions serialize the state of a hash and thus perform the standard
// "final" operation without adding the padding and length that such a function
// typically does.
static void tls1_sha1_final_raw(HASH_CTX *ctx, uint8_t *md_out) {
  SHA_CTX *sha1 = &ctx->sha1;
  u32toBE(sha1->h[0], md_out);
  u32toBE(sha1->h[1], md_out);
  u32toBE(sha1->h[2], md_out);
  u32toBE(sha1->h[3], md_out);
  u32toBE(sha1->h[4], md_out);
}

static void tls1_sha256_final_raw(HASH_CTX *ctx, uint8_t *md_out) {
  SHA256_CTX *sha256 = &ctx->sha256;
  for (unsigned i = 0; i < 8; i++) {
    u32toBE(sha256->h[i], md_out);
  }
}

static void tls1_sha512_final_raw(HASH_CTX *ctx, uint8_t *md_out) {
  SHA512_CTX *sha512 = &ctx->sha512;
  for (unsigned i = 0; i < 8; i++) {
    u64toBE(sha512->h[i], md_out);
  }
}

int EVP_tls_cbc_record_digest_supported(const EVP_MD *md) {
  switch (EVP_MD_type(md)) {
    case NID_sha1:
    case NID_sha256:
    case NID_sha384:
      return 1;

    default:
      return 0;
  }
}

int EVP_tls_cbc_digest_record(const EVP_MD *md, uint8_t *md_out,
                              size_t *md_out_size, const uint8_t header[13],
                              const uint8_t *data, size_t data_plus_mac_size,
                              size_t data_plus_mac_plus_padding_size,
                              const uint8_t *mac_secret,
                              unsigned mac_secret_length) {
  HASH_CTX md_state;
  void (*md_final_raw)(HASH_CTX *ctx, uint8_t *md_out);
  void (*md_transform)(HASH_CTX *ctx, const uint8_t *block);
  unsigned md_size, md_block_size = 64, md_block_shift = 6;
  // md_length_size is the number of bytes in the length field that terminates
  // the hash.
  unsigned md_length_size = 8;

  // Bound the acceptable input so we can forget about many possible overflows
  // later in this function. This is redundant with the record size limits in
  // TLS.
  if (data_plus_mac_plus_padding_size >= 1024 * 1024) {
    assert(0);
    return 0;
  }

  switch (EVP_MD_type(md)) {
    case NID_sha1:
      SHA1_Init(&md_state.sha1);
      md_final_raw = tls1_sha1_final_raw;
      md_transform = tls1_sha1_transform;
      md_size = SHA_DIGEST_LENGTH;
      break;

    case NID_sha256:
      SHA256_Init(&md_state.sha256);
      md_final_raw = tls1_sha256_final_raw;
      md_transform = tls1_sha256_transform;
      md_size = SHA256_DIGEST_LENGTH;
      break;

    case NID_sha384:
      SHA384_Init(&md_state.sha512);
      md_final_raw = tls1_sha512_final_raw;
      md_transform = tls1_sha512_transform;
      md_size = SHA384_DIGEST_LENGTH;
      md_block_size = 128;
      md_block_shift = 7;
      md_length_size = 16;
      break;

    default:
      // EVP_tls_cbc_record_digest_supported should have been called first to
      // check that the hash function is supported.
      assert(0);
      *md_out_size = 0;
      return 0;
  }

  assert(md_length_size <= MAX_HASH_BIT_COUNT_BYTES);
  assert(md_block_size <= MAX_HASH_BLOCK_SIZE);
  assert(md_block_size == (1u << md_block_shift));
  assert(md_size <= EVP_MAX_MD_SIZE);

  static const size_t kHeaderLength = 13;

  // kVarianceBlocks is the number of blocks of the hash that we have to
  // calculate in constant time because they could be altered by the
  // padding value.
  //
  // TLSv1 has MACs up to 48 bytes long (SHA-384) and the padding is not
  // required to be minimal. Therefore we say that the final |kVarianceBlocks|
  // blocks can vary based on the padding and on the hash used. This value
  // must be derived from public information.
  const size_t kVarianceBlocks =
     ( 255 + 1 + // maximum padding bytes + padding length
       md_size + // length of hash's output
       md_block_size - 1 // ceiling
     ) / md_block_size
     + 1; // the 0x80 marker and the encoded message length could or not
          // require an extra block; since the exact value depends on the
          // message length; thus, one extra block is always added to run
          // in constant time.

  // From now on we're dealing with the MAC, which conceptually has 13
  // bytes of `header' before the start of the data.
  size_t len = data_plus_mac_plus_padding_size + kHeaderLength;
  // max_mac_bytes contains the maximum bytes of bytes in the MAC, including
  // |header|, assuming that there's no padding.
  size_t max_mac_bytes = len - md_size - 1;
  // num_blocks is the maximum number of hash blocks.
  size_t num_blocks =
      (max_mac_bytes + 1 + md_length_size + md_block_size - 1) / md_block_size;
  // In order to calculate the MAC in constant time we have to handle
  // the final blocks specially because the padding value could cause the
  // end to appear somewhere in the final |kVarianceBlocks| blocks and we
  // can't leak where. However, |num_starting_blocks| worth of data can
  // be hashed right away because no padding value can affect whether
  // they are plaintext.
  size_t num_starting_blocks = 0;
  // k is the starting byte offset into the conceptual header||data where
  // we start processing.
  size_t k = 0;
  // mac_end_offset is the index just past the end of the data to be MACed.
  size_t mac_end_offset = data_plus_mac_size + kHeaderLength - md_size;
  // c is the index of the 0x80 byte in the final hash block that contains
  // application data.
  size_t c = mac_end_offset & (md_block_size - 1);
  // index_a is the hash block number that contains the 0x80 terminating value.
  size_t index_a = mac_end_offset >> md_block_shift;
  // index_b is the hash block number that contains the 64-bit hash length, in
  // bits.
  size_t index_b = (mac_end_offset + md_length_size) >> md_block_shift;

  if (num_blocks > kVarianceBlocks) {
    num_starting_blocks = num_blocks - kVarianceBlocks;
    k = md_block_size * num_starting_blocks;
  }

  // bits is the hash-length in bits. It includes the additional hash
  // block for the masked HMAC key.
  size_t bits = 8 * mac_end_offset;  // at most 18 bits to represent

  // Compute the initial HMAC block.
  bits += 8 * md_block_size;
  // hmac_pad is the masked HMAC key.
  uint8_t hmac_pad[MAX_HASH_BLOCK_SIZE];
  OPENSSL_memset(hmac_pad, 0, md_block_size);
  assert(mac_secret_length <= sizeof(hmac_pad));
  OPENSSL_memcpy(hmac_pad, mac_secret, mac_secret_length);
  for (size_t i = 0; i < md_block_size; i++) {
    hmac_pad[i] ^= 0x36;
  }

  md_transform(&md_state, hmac_pad);

  // The length check means |bits| fits in four bytes.
  uint8_t length_bytes[MAX_HASH_BIT_COUNT_BYTES];
  OPENSSL_memset(length_bytes, 0, md_length_size - 4);
  length_bytes[md_length_size - 4] = (uint8_t)(bits >> 24);
  length_bytes[md_length_size - 3] = (uint8_t)(bits >> 16);
  length_bytes[md_length_size - 2] = (uint8_t)(bits >> 8);
  length_bytes[md_length_size - 1] = (uint8_t)bits;

  if (k > 0) {
    // k is a multiple of md_block_size.
    uint8_t first_block[MAX_HASH_BLOCK_SIZE];
    OPENSSL_memcpy(first_block, header, 13);
    OPENSSL_memcpy(first_block + 13, data, md_block_size - 13);
    md_transform(&md_state, first_block);
    for (size_t i = 1; i < k / md_block_size; i++) {
      md_transform(&md_state, data + md_block_size * i - 13);
    }
  }

  uint8_t mac_out[EVP_MAX_MD_SIZE];
  OPENSSL_memset(mac_out, 0, sizeof(mac_out));

  // We now process the final hash blocks. For each block, we construct
  // it in constant time. If the |i==index_a| then we'll include the 0x80
  // bytes and zero pad etc. For each block we selectively copy it, in
  // constant time, to |mac_out|.
  for (size_t i = num_starting_blocks;
       i <= num_starting_blocks + kVarianceBlocks; i++) {
    uint8_t block[MAX_HASH_BLOCK_SIZE];
    uint8_t is_block_a = constant_time_eq_8(i, index_a);
    uint8_t is_block_b = constant_time_eq_8(i, index_b);
    for (size_t j = 0; j < md_block_size; j++) {
      uint8_t b = 0;
      if (k < kHeaderLength) {
        b = header[k];
      } else if (k < data_plus_mac_plus_padding_size + kHeaderLength) {
        b = data[k - kHeaderLength];
      }
      k++;

      uint8_t is_past_c = is_block_a & constant_time_ge_8(j, c);
      uint8_t is_past_cp1 = is_block_a & constant_time_ge_8(j, c + 1);
      // If this is the block containing the end of the
      // application data, and we are at the offset for the
      // 0x80 value, then overwrite b with 0x80.
      b = constant_time_select_8(is_past_c, 0x80, b);
      // If this the the block containing the end of the
      // application data and we're past the 0x80 value then
      // just write zero.
      b = b & ~is_past_cp1;
      // If this is index_b (the final block), but not
      // index_a (the end of the data), then the 64-bit
      // length didn't fit into index_a and we're having to
      // add an extra block of zeros.
      b &= ~is_block_b | is_block_a;

      // The final bytes of one of the blocks contains the
      // length.
      if (j >= md_block_size - md_length_size) {
        // If this is index_b, write a length byte.
        b = constant_time_select_8(
            is_block_b, length_bytes[j - (md_block_size - md_length_size)], b);
      }
      block[j] = b;
    }

    md_transform(&md_state, block);
    md_final_raw(&md_state, block);
    // If this is index_b, copy the hash value to |mac_out|.
    for (size_t j = 0; j < md_size; j++) {
      mac_out[j] |= block[j] & is_block_b;
    }
  }

  EVP_MD_CTX md_ctx;
  EVP_MD_CTX_init(&md_ctx);
  if (!EVP_DigestInit_ex(&md_ctx, md, NULL /* engine */)) {
    EVP_MD_CTX_cleanup(&md_ctx);
    return 0;
  }

  // Complete the HMAC in the standard manner.
  for (size_t i = 0; i < md_block_size; i++) {
    hmac_pad[i] ^= 0x6a;
  }

  EVP_DigestUpdate(&md_ctx, hmac_pad, md_block_size);
  EVP_DigestUpdate(&md_ctx, mac_out, md_size);
  unsigned md_out_size_u;
  EVP_DigestFinal(&md_ctx, md_out, &md_out_size_u);
  *md_out_size = md_out_size_u;
  EVP_MD_CTX_cleanup(&md_ctx);

  return 1;
}

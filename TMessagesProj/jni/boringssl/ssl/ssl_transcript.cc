/* Copyright (C) 1995-1998 Eric Young (eay@cryptsoft.com)
 * All rights reserved.
 *
 * This package is an SSL implementation written
 * by Eric Young (eay@cryptsoft.com).
 * The implementation was written so as to conform with Netscapes SSL.
 *
 * This library is free for commercial and non-commercial use as long as
 * the following conditions are aheared to.  The following conditions
 * apply to all code found in this distribution, be it the RC4, RSA,
 * lhash, DES, etc., code; not just the SSL code.  The SSL documentation
 * included with this distribution is covered by the same copyright terms
 * except that the holder is Tim Hudson (tjh@cryptsoft.com).
 *
 * Copyright remains Eric Young's, and as such any Copyright notices in
 * the code are not to be removed.
 * If this package is used in a product, Eric Young should be given attribution
 * as the author of the parts of the library used.
 * This can be in the form of a textual message at program startup or
 * in documentation (online or textual) provided with the package.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *    "This product includes cryptographic software written by
 *     Eric Young (eay@cryptsoft.com)"
 *    The word 'cryptographic' can be left out if the rouines from the library
 *    being used are not cryptographic related :-).
 * 4. If you include any Windows specific code (or a derivative thereof) from
 *    the apps directory (application code) you must include an acknowledgement:
 *    "This product includes software written by Tim Hudson (tjh@cryptsoft.com)"
 *
 * THIS SOFTWARE IS PROVIDED BY ERIC YOUNG ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * The licence and distribution terms for any publically available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution licence
 * [including the GNU Public Licence.]
 */
/* ====================================================================
 * Copyright (c) 1998-2007 The OpenSSL Project.  All rights reserved.
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
 * Copyright 2005 Nokia. All rights reserved.
 *
 * The portions of the attached software ("Contribution") is developed by
 * Nokia Corporation and is licensed pursuant to the OpenSSL open source
 * license.
 *
 * The Contribution, originally written by Mika Kousa and Pasi Eronen of
 * Nokia Corporation, consists of the "PSK" (Pre-Shared Key) ciphersuites
 * support (see RFC 4279) to OpenSSL.
 *
 * No patent licenses or other rights except those expressly stated in
 * the OpenSSL open source license shall be deemed granted or received
 * expressly, by implication, estoppel, or otherwise.
 *
 * No assurances are provided by Nokia that the Contribution does not
 * infringe the patent or other intellectual property rights of any third
 * party or that the license provides you with all the necessary rights
 * to make use of the Contribution.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND. IN
 * ADDITION TO THE DISCLAIMERS INCLUDED IN THE LICENSE, NOKIA
 * SPECIFICALLY DISCLAIMS ANY LIABILITY FOR CLAIMS BROUGHT BY YOU OR ANY
 * OTHER ENTITY BASED ON INFRINGEMENT OF INTELLECTUAL PROPERTY RIGHTS OR
 * OTHERWISE. */

#include <openssl/ssl.h>

#include <openssl/buf.h>
#include <openssl/digest.h>
#include <openssl/err.h>

#include "internal.h"


BSSL_NAMESPACE_BEGIN

SSLTranscript::SSLTranscript() {}

SSLTranscript::~SSLTranscript() {}

bool SSLTranscript::Init() {
  buffer_.reset(BUF_MEM_new());
  if (!buffer_) {
    return false;
  }

  hash_.Reset();
  return true;
}

// InitDigestWithData calls |EVP_DigestInit_ex| on |ctx| with |md| and then
// writes the data in |buf| to it.
static bool InitDigestWithData(EVP_MD_CTX *ctx, const EVP_MD *md,
                               const BUF_MEM *buf) {
  if (!EVP_DigestInit_ex(ctx, md, NULL)) {
    return false;
  }
  EVP_DigestUpdate(ctx, buf->data, buf->length);
  return true;
}

bool SSLTranscript::InitHash(uint16_t version, const SSL_CIPHER *cipher) {
  const EVP_MD *md = ssl_get_handshake_digest(version, cipher);
  return InitDigestWithData(hash_.get(), md, buffer_.get());
}

void SSLTranscript::FreeBuffer() {
  buffer_.reset();
}

size_t SSLTranscript::DigestLen() const {
  return EVP_MD_size(Digest());
}

const EVP_MD *SSLTranscript::Digest() const {
  return EVP_MD_CTX_md(hash_.get());
}

bool SSLTranscript::UpdateForHelloRetryRequest() {
  if (buffer_) {
    buffer_->length = 0;
  }

  uint8_t old_hash[EVP_MAX_MD_SIZE];
  size_t hash_len;
  if (!GetHash(old_hash, &hash_len)) {
    return false;
  }
  const uint8_t header[4] = {SSL3_MT_MESSAGE_HASH, 0, 0,
                             static_cast<uint8_t>(hash_len)};
  if (!EVP_DigestInit_ex(hash_.get(), Digest(), nullptr) ||
      !Update(header) ||
      !Update(MakeConstSpan(old_hash, hash_len))) {
    return false;
  }
  return true;
}

bool SSLTranscript::CopyToHashContext(EVP_MD_CTX *ctx, const EVP_MD *digest) {
  const EVP_MD *transcript_digest = Digest();
  if (transcript_digest != nullptr &&
      EVP_MD_type(transcript_digest) == EVP_MD_type(digest)) {
    return EVP_MD_CTX_copy_ex(ctx, hash_.get());
  }

  if (buffer_) {
    return EVP_DigestInit_ex(ctx, digest, nullptr) &&
           EVP_DigestUpdate(ctx, buffer_->data, buffer_->length);
  }

  OPENSSL_PUT_ERROR(SSL, ERR_R_INTERNAL_ERROR);
  return false;
}

bool SSLTranscript::Update(Span<const uint8_t> in) {
  // Depending on the state of the handshake, either the handshake buffer may be
  // active, the rolling hash, or both.
  if (buffer_ &&
      !BUF_MEM_append(buffer_.get(), in.data(), in.size())) {
    return false;
  }

  if (EVP_MD_CTX_md(hash_.get()) != NULL) {
    EVP_DigestUpdate(hash_.get(), in.data(), in.size());
  }

  return true;
}

bool SSLTranscript::GetHash(uint8_t *out, size_t *out_len) {
  ScopedEVP_MD_CTX ctx;
  unsigned len;
  if (!EVP_MD_CTX_copy_ex(ctx.get(), hash_.get()) ||
      !EVP_DigestFinal_ex(ctx.get(), out, &len)) {
    return false;
  }
  *out_len = len;
  return true;
}

bool SSLTranscript::GetFinishedMAC(uint8_t *out, size_t *out_len,
                                   const SSL_SESSION *session,
                                   bool from_server) {
  static const char kClientLabel[] = "client finished";
  static const char kServerLabel[] = "server finished";
  auto label = from_server
                   ? MakeConstSpan(kServerLabel, sizeof(kServerLabel) - 1)
                   : MakeConstSpan(kClientLabel, sizeof(kClientLabel) - 1);

  uint8_t digest[EVP_MAX_MD_SIZE];
  size_t digest_len;
  if (!GetHash(digest, &digest_len)) {
    return false;
  }

  static const size_t kFinishedLen = 12;
  if (!tls1_prf(Digest(), MakeSpan(out, kFinishedLen),
                MakeConstSpan(session->master_key, session->master_key_length),
                label, MakeConstSpan(digest, digest_len), {})) {
    return false;
  }

  *out_len = kFinishedLen;
  return true;
}

BSSL_NAMESPACE_END

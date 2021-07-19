/* ====================================================================
 * Copyright (c) 1998-2003 The OpenSSL Project.  All rights reserved.
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

#include <openssl/bio.h>

#include <assert.h>
#include <string.h>

#include <openssl/buf.h>
#include <openssl/err.h>
#include <openssl/mem.h>

#include "../internal.h"


struct bio_bio_st {
  BIO *peer;  // NULL if buf == NULL.
              // If peer != NULL, then peer->ptr is also a bio_bio_st,
              // and its "peer" member points back to us.
              // peer != NULL iff init != 0 in the BIO.

  // This is for what we write (i.e. reading uses peer's struct):
  int closed;     // valid iff peer != NULL
  size_t len;     // valid iff buf != NULL; 0 if peer == NULL
  size_t offset;  // valid iff buf != NULL; 0 if len == 0
  size_t size;
  uint8_t *buf;  // "size" elements (if != NULL)

  size_t request;  // valid iff peer != NULL; 0 if len != 0,
                   // otherwise set by peer to number of bytes
                   // it (unsuccessfully) tried to read,
                   // never more than buffer space (size-len) warrants.
};

static int bio_new(BIO *bio) {
  struct bio_bio_st *b;

  b = OPENSSL_malloc(sizeof *b);
  if (b == NULL) {
    return 0;
  }
  OPENSSL_memset(b, 0, sizeof(struct bio_bio_st));

  b->size = 17 * 1024;  // enough for one TLS record (just a default)
  bio->ptr = b;
  return 1;
}

static void bio_destroy_pair(BIO *bio) {
  struct bio_bio_st *b = bio->ptr;
  BIO *peer_bio;
  struct bio_bio_st *peer_b;

  if (b == NULL) {
    return;
  }

  peer_bio = b->peer;
  if (peer_bio == NULL) {
    return;
  }

  peer_b = peer_bio->ptr;

  assert(peer_b != NULL);
  assert(peer_b->peer == bio);

  peer_b->peer = NULL;
  peer_bio->init = 0;
  assert(peer_b->buf != NULL);
  peer_b->len = 0;
  peer_b->offset = 0;

  b->peer = NULL;
  bio->init = 0;
  assert(b->buf != NULL);
  b->len = 0;
  b->offset = 0;
}

static int bio_free(BIO *bio) {
  struct bio_bio_st *b;

  if (bio == NULL) {
    return 0;
  }
  b = bio->ptr;

  assert(b != NULL);

  if (b->peer) {
    bio_destroy_pair(bio);
  }

  OPENSSL_free(b->buf);
  OPENSSL_free(b);

  return 1;
}

static int bio_read(BIO *bio, char *buf, int size_) {
  size_t size = size_;
  size_t rest;
  struct bio_bio_st *b, *peer_b;

  BIO_clear_retry_flags(bio);

  if (!bio->init) {
    return 0;
  }

  b = bio->ptr;
  assert(b != NULL);
  assert(b->peer != NULL);
  peer_b = b->peer->ptr;
  assert(peer_b != NULL);
  assert(peer_b->buf != NULL);

  peer_b->request = 0;  // will be set in "retry_read" situation

  if (buf == NULL || size == 0) {
    return 0;
  }

  if (peer_b->len == 0) {
    if (peer_b->closed) {
      return 0;  // writer has closed, and no data is left
    } else {
      BIO_set_retry_read(bio);  // buffer is empty
      if (size <= peer_b->size) {
        peer_b->request = size;
      } else {
        // don't ask for more than the peer can
        // deliver in one write
        peer_b->request = peer_b->size;
      }
      return -1;
    }
  }

  // we can read
  if (peer_b->len < size) {
    size = peer_b->len;
  }

  // now read "size" bytes
  rest = size;

  assert(rest > 0);
  // one or two iterations
  do {
    size_t chunk;

    assert(rest <= peer_b->len);
    if (peer_b->offset + rest <= peer_b->size) {
      chunk = rest;
    } else {
      // wrap around ring buffer
      chunk = peer_b->size - peer_b->offset;
    }
    assert(peer_b->offset + chunk <= peer_b->size);

    OPENSSL_memcpy(buf, peer_b->buf + peer_b->offset, chunk);

    peer_b->len -= chunk;
    if (peer_b->len) {
      peer_b->offset += chunk;
      assert(peer_b->offset <= peer_b->size);
      if (peer_b->offset == peer_b->size) {
        peer_b->offset = 0;
      }
      buf += chunk;
    } else {
      // buffer now empty, no need to advance "buf"
      assert(chunk == rest);
      peer_b->offset = 0;
    }
    rest -= chunk;
  } while (rest);

  return size;
}

static int bio_write(BIO *bio, const char *buf, int num_) {
  size_t num = num_;
  size_t rest;
  struct bio_bio_st *b;

  BIO_clear_retry_flags(bio);

  if (!bio->init || buf == NULL || num == 0) {
    return 0;
  }

  b = bio->ptr;
  assert(b != NULL);
  assert(b->peer != NULL);
  assert(b->buf != NULL);

  b->request = 0;
  if (b->closed) {
    // we already closed
    OPENSSL_PUT_ERROR(BIO, BIO_R_BROKEN_PIPE);
    return -1;
  }

  assert(b->len <= b->size);

  if (b->len == b->size) {
    BIO_set_retry_write(bio);  // buffer is full
    return -1;
  }

  // we can write
  if (num > b->size - b->len) {
    num = b->size - b->len;
  }

  // now write "num" bytes
  rest = num;

  assert(rest > 0);
  // one or two iterations
  do {
    size_t write_offset;
    size_t chunk;

    assert(b->len + rest <= b->size);

    write_offset = b->offset + b->len;
    if (write_offset >= b->size) {
      write_offset -= b->size;
    }
    // b->buf[write_offset] is the first byte we can write to.

    if (write_offset + rest <= b->size) {
      chunk = rest;
    } else {
      // wrap around ring buffer
      chunk = b->size - write_offset;
    }

    OPENSSL_memcpy(b->buf + write_offset, buf, chunk);

    b->len += chunk;

    assert(b->len <= b->size);

    rest -= chunk;
    buf += chunk;
  } while (rest);

  return num;
}

static int bio_make_pair(BIO *bio1, BIO *bio2, size_t writebuf1_len,
                         size_t writebuf2_len) {
  struct bio_bio_st *b1, *b2;

  assert(bio1 != NULL);
  assert(bio2 != NULL);

  b1 = bio1->ptr;
  b2 = bio2->ptr;

  if (b1->peer != NULL || b2->peer != NULL) {
    OPENSSL_PUT_ERROR(BIO, BIO_R_IN_USE);
    return 0;
  }

  if (b1->buf == NULL) {
    if (writebuf1_len) {
      b1->size = writebuf1_len;
    }
    b1->buf = OPENSSL_malloc(b1->size);
    if (b1->buf == NULL) {
      OPENSSL_PUT_ERROR(BIO, ERR_R_MALLOC_FAILURE);
      return 0;
    }
    b1->len = 0;
    b1->offset = 0;
  }

  if (b2->buf == NULL) {
    if (writebuf2_len) {
      b2->size = writebuf2_len;
    }
    b2->buf = OPENSSL_malloc(b2->size);
    if (b2->buf == NULL) {
      OPENSSL_PUT_ERROR(BIO, ERR_R_MALLOC_FAILURE);
      return 0;
    }
    b2->len = 0;
    b2->offset = 0;
  }

  b1->peer = bio2;
  b1->closed = 0;
  b1->request = 0;
  b2->peer = bio1;
  b2->closed = 0;
  b2->request = 0;

  bio1->init = 1;
  bio2->init = 1;

  return 1;
}

static long bio_ctrl(BIO *bio, int cmd, long num, void *ptr) {
  long ret;
  struct bio_bio_st *b = bio->ptr;

  assert(b != NULL);

  switch (cmd) {
    // specific CTRL codes

    case BIO_C_GET_WRITE_BUF_SIZE:
      ret = (long)b->size;
      break;

    case BIO_C_GET_WRITE_GUARANTEE:
      // How many bytes can the caller feed to the next write
      // without having to keep any?
      if (b->peer == NULL || b->closed) {
        ret = 0;
      } else {
        ret = (long)b->size - b->len;
      }
      break;

    case BIO_C_GET_READ_REQUEST:
      // If the peer unsuccessfully tried to read, how many bytes
      // were requested?  (As with BIO_CTRL_PENDING, that number
      // can usually be treated as boolean.)
      ret = (long)b->request;
      break;

    case BIO_C_RESET_READ_REQUEST:
      // Reset request.  (Can be useful after read attempts
      // at the other side that are meant to be non-blocking,
      // e.g. when probing SSL_read to see if any data is
      // available.)
      b->request = 0;
      ret = 1;
      break;

    case BIO_C_SHUTDOWN_WR:
      // similar to shutdown(..., SHUT_WR)
      b->closed = 1;
      ret = 1;
      break;

    // standard CTRL codes follow

    case BIO_CTRL_GET_CLOSE:
      ret = bio->shutdown;
      break;

    case BIO_CTRL_SET_CLOSE:
      bio->shutdown = (int)num;
      ret = 1;
      break;

    case BIO_CTRL_PENDING:
      if (b->peer != NULL) {
        struct bio_bio_st *peer_b = b->peer->ptr;
        ret = (long)peer_b->len;
      } else {
        ret = 0;
      }
      break;

    case BIO_CTRL_WPENDING:
      ret = 0;
      if (b->buf != NULL) {
        ret = (long)b->len;
      }
      break;

    case BIO_CTRL_FLUSH:
      ret = 1;
      break;

    case BIO_CTRL_EOF: {
      BIO *other_bio = ptr;

      if (other_bio) {
        struct bio_bio_st *other_b = other_bio->ptr;
        assert(other_b != NULL);
        ret = other_b->len == 0 && other_b->closed;
      } else {
        ret = 1;
      }
    } break;

    default:
      ret = 0;
  }
  return ret;
}


static const BIO_METHOD methods_biop = {
    BIO_TYPE_BIO,    "BIO pair", bio_write, bio_read, NULL /* puts */,
    NULL /* gets */, bio_ctrl,   bio_new,   bio_free, NULL /* callback_ctrl */,
};

static const BIO_METHOD *bio_s_bio(void) { return &methods_biop; }

int BIO_new_bio_pair(BIO** bio1_p, size_t writebuf1_len,
                     BIO** bio2_p, size_t writebuf2_len) {
  BIO *bio1 = BIO_new(bio_s_bio());
  BIO *bio2 = BIO_new(bio_s_bio());
  if (bio1 == NULL || bio2 == NULL ||
      !bio_make_pair(bio1, bio2, writebuf1_len, writebuf2_len)) {
    BIO_free(bio1);
    BIO_free(bio2);
    *bio1_p = NULL;
    *bio2_p = NULL;
    return 0;
  }

  *bio1_p = bio1;
  *bio2_p = bio2;
  return 1;
}

size_t BIO_ctrl_get_read_request(BIO *bio) {
  return BIO_ctrl(bio, BIO_C_GET_READ_REQUEST, 0, NULL);
}

size_t BIO_ctrl_get_write_guarantee(BIO *bio) {
  return BIO_ctrl(bio, BIO_C_GET_WRITE_GUARANTEE, 0, NULL);
}

int BIO_shutdown_wr(BIO *bio) {
  return BIO_ctrl(bio, BIO_C_SHUTDOWN_WR, 0, NULL);
}

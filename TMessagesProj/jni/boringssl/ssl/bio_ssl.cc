/*
 * Copyright 1995-2016 The OpenSSL Project Authors. All Rights Reserved.
 *
 * Licensed under the OpenSSL license (the "License").  You may not use
 * this file except in compliance with the License.  You can obtain a copy
 * in the file LICENSE in the source distribution or at
 * https://www.openssl.org/source/license.html
 */

#include <openssl/ssl.h>

#include <openssl/bio.h>


static SSL *get_ssl(BIO *bio) {
  return reinterpret_cast<SSL *>(bio->ptr);
}

static int ssl_read(BIO *bio, char *out, int outl) {
  SSL *ssl = get_ssl(bio);
  if (ssl == NULL) {
    return 0;
  }

  BIO_clear_retry_flags(bio);

  const int ret = SSL_read(ssl, out, outl);

  switch (SSL_get_error(ssl, ret)) {
    case SSL_ERROR_WANT_READ:
      BIO_set_retry_read(bio);
      break;

    case SSL_ERROR_WANT_WRITE:
      BIO_set_retry_write(bio);
      break;

    case SSL_ERROR_WANT_ACCEPT:
      BIO_set_retry_special(bio);
      bio->retry_reason = BIO_RR_ACCEPT;
      break;

    case SSL_ERROR_WANT_CONNECT:
      BIO_set_retry_special(bio);
      bio->retry_reason = BIO_RR_CONNECT;
      break;

    case SSL_ERROR_NONE:
    case SSL_ERROR_SYSCALL:
    case SSL_ERROR_SSL:
    case SSL_ERROR_ZERO_RETURN:
    default:
      break;
  }

  return ret;
}

static int ssl_write(BIO *bio, const char *out, int outl) {
  SSL *ssl = get_ssl(bio);
  if (ssl == NULL) {
    return 0;
  }

  BIO_clear_retry_flags(bio);

  const int ret = SSL_write(ssl, out, outl);

  switch (SSL_get_error(ssl, ret)) {
    case SSL_ERROR_WANT_WRITE:
      BIO_set_retry_write(bio);
      break;

    case SSL_ERROR_WANT_READ:
      BIO_set_retry_read(bio);
      break;

    case SSL_ERROR_WANT_CONNECT:
      BIO_set_retry_special(bio);
      bio->retry_reason = BIO_RR_CONNECT;
      break;

    case SSL_ERROR_NONE:
    case SSL_ERROR_SYSCALL:
    case SSL_ERROR_SSL:
    default:
      break;
  }

  return ret;
}

static long ssl_ctrl(BIO *bio, int cmd, long num, void *ptr) {
  SSL *ssl = get_ssl(bio);
  if (ssl == NULL && cmd != BIO_C_SET_SSL) {
    return 0;
  }

  switch (cmd) {
    case BIO_C_SET_SSL:
      bio->shutdown = num;
      bio->ptr = ptr;
      bio->init = 1;
      return 1;

    case BIO_CTRL_GET_CLOSE:
      return bio->shutdown;

    case BIO_CTRL_SET_CLOSE:
      bio->shutdown = num;
      return 1;

    case BIO_CTRL_WPENDING:
      return BIO_ctrl(SSL_get_wbio(ssl), cmd, num, ptr);

    case BIO_CTRL_PENDING:
      return SSL_pending(ssl);

    case BIO_CTRL_FLUSH: {
      BIO_clear_retry_flags(bio);
      long ret = BIO_ctrl(SSL_get_wbio(ssl), cmd, num, ptr);
      BIO_copy_next_retry(bio);
      return ret;
    }

    case BIO_CTRL_PUSH:
    case BIO_CTRL_POP:
    case BIO_CTRL_DUP:
      return -1;

    default:
      return BIO_ctrl(SSL_get_rbio(ssl), cmd, num, ptr);
  }
}

static int ssl_new(BIO *bio) {
  return 1;
}

static int ssl_free(BIO *bio) {
  SSL *ssl = get_ssl(bio);

  if (ssl == NULL) {
    return 1;
  }

  SSL_shutdown(ssl);
  if (bio->shutdown) {
    SSL_free(ssl);
  }

  return 1;
}

static long ssl_callback_ctrl(BIO *bio, int cmd, bio_info_cb fp) {
  SSL *ssl = get_ssl(bio);
  if (ssl == NULL) {
    return 0;
  }

  switch (cmd) {
    case BIO_CTRL_SET_CALLBACK:
      return -1;

    default:
      return BIO_callback_ctrl(SSL_get_rbio(ssl), cmd, fp);
  }
}

static const BIO_METHOD ssl_method = {
    BIO_TYPE_SSL, "SSL",    ssl_write, ssl_read, NULL,
    NULL,         ssl_ctrl, ssl_new,   ssl_free, ssl_callback_ctrl,
};

const BIO_METHOD *BIO_f_ssl(void) { return &ssl_method; }

long BIO_set_ssl(BIO *bio, SSL *ssl, int take_owership) {
  return BIO_ctrl(bio, BIO_C_SET_SSL, take_owership, ssl);
}

// Copyright 1995-2016 The OpenSSL Project Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <openssl/bio.h>

#if !defined(OPENSSL_NO_SOCK)

#include <fcntl.h>
#include <string.h>

#if !defined(OPENSSL_WINDOWS)
#include <unistd.h>
#else
#include <winsock2.h>
OPENSSL_MSVC_PRAGMA(comment(lib, "Ws2_32.lib"))
#endif

#include "internal.h"


#if !defined(OPENSSL_WINDOWS)
static int closesocket(int sock) {
  return close(sock);
}
#endif

static int sock_free(BIO *bio) {
  if (bio->shutdown) {
    if (bio->init) {
      closesocket(bio->num);
    }
    bio->init = 0;
    bio->flags = 0;
  }
  return 1;
}

static int sock_read(BIO *b, char *out, int outl) {
  if (out == NULL) {
    return 0;
  }

  bio_clear_socket_error();
#if defined(OPENSSL_WINDOWS)
  int ret = recv(b->num, out, outl, 0);
#else
  int ret = (int)read(b->num, out, outl);
#endif
  BIO_clear_retry_flags(b);
  if (ret <= 0) {
    if (bio_socket_should_retry(ret)) {
      BIO_set_retry_read(b);
    }
  }
  return ret;
}

static int sock_write(BIO *b, const char *in, int inl) {
  bio_clear_socket_error();
#if defined(OPENSSL_WINDOWS)
  int ret = send(b->num, in, inl, 0);
#else
  int ret = (int)write(b->num, in, inl);
#endif
  BIO_clear_retry_flags(b);
  if (ret <= 0) {
    if (bio_socket_should_retry(ret)) {
      BIO_set_retry_write(b);
    }
  }
  return ret;
}

static long sock_ctrl(BIO *b, int cmd, long num, void *ptr) {
  long ret = 1;
  int *ip;

  switch (cmd) {
    case BIO_C_SET_FD:
      sock_free(b);
      b->num = *((int *)ptr);
      b->shutdown = (int)num;
      b->init = 1;
      break;
    case BIO_C_GET_FD:
      if (b->init) {
        ip = (int *)ptr;
        if (ip != NULL) {
          *ip = b->num;
        }
        ret = b->num;
      } else {
        ret = -1;
      }
      break;
    case BIO_CTRL_GET_CLOSE:
      ret = b->shutdown;
      break;
    case BIO_CTRL_SET_CLOSE:
      b->shutdown = (int)num;
      break;
    case BIO_CTRL_FLUSH:
      ret = 1;
      break;
    default:
      ret = 0;
      break;
  }
  return ret;
}

static const BIO_METHOD methods_sockp = {
    BIO_TYPE_SOCKET, "socket",
    sock_write,      sock_read,
    NULL /* puts */, NULL /* gets, */,
    sock_ctrl,       NULL /* create */,
    sock_free,       NULL /* callback_ctrl */,
};

const BIO_METHOD *BIO_s_socket(void) { return &methods_sockp; }

BIO *BIO_new_socket(int fd, int close_flag) {
  BIO *ret;

  ret = BIO_new(BIO_s_socket());
  if (ret == NULL) {
    return NULL;
  }
  BIO_set_fd(ret, fd, close_flag);
  return ret;
}

#endif  // OPENSSL_NO_SOCK

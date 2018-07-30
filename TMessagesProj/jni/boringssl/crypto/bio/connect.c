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
 * [including the GNU Public Licence.] */

#include <openssl/bio.h>

#include <assert.h>
#include <errno.h>
#include <string.h>

#if !defined(OPENSSL_WINDOWS)
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#else
OPENSSL_MSVC_PRAGMA(warning(push, 3))
#include <winsock2.h>
#include <ws2tcpip.h>
OPENSSL_MSVC_PRAGMA(warning(pop))
#endif

#include <openssl/buf.h>
#include <openssl/err.h>
#include <openssl/mem.h>

#include "internal.h"
#include "../internal.h"


enum {
  BIO_CONN_S_BEFORE,
  BIO_CONN_S_BLOCKED_CONNECT,
  BIO_CONN_S_OK,
};

typedef struct bio_connect_st {
  int state;

  char *param_hostname;
  char *param_port;
  int nbio;

  unsigned short port;

  struct sockaddr_storage them;
  socklen_t them_length;

  // the file descriptor is kept in bio->num in order to match the socket
  // BIO.

  // info_callback is called when the connection is initially made
  // callback(BIO,state,ret);  The callback should return 'ret', state is for
  // compatibility with the SSL info_callback.
  int (*info_callback)(const BIO *bio, int state, int ret);
} BIO_CONNECT;

#if !defined(OPENSSL_WINDOWS)
static int closesocket(int sock) {
  return close(sock);
}
#endif

// split_host_and_port sets |*out_host| and |*out_port| to the host and port
// parsed from |name|. It returns one on success or zero on error. Even when
// successful, |*out_port| may be NULL on return if no port was specified.
static int split_host_and_port(char **out_host, char **out_port, const char *name) {
  const char *host, *port = NULL;
  size_t host_len = 0;

  *out_host = NULL;
  *out_port = NULL;

  if (name[0] == '[') {  // bracketed IPv6 address
    const char *close = strchr(name, ']');
    if (close == NULL) {
      return 0;
    }
    host = name + 1;
    host_len = close - host;
    if (close[1] == ':') {  // [IP]:port
      port = close + 2;
    } else if (close[1] != 0) {
      return 0;
    }
  } else {
    const char *colon = strchr(name, ':');
    if (colon == NULL || strchr(colon + 1, ':') != NULL) {  // IPv6 address
      host = name;
      host_len = strlen(name);
    } else {  // host:port
      host = name;
      host_len = colon - name;
      port = colon + 1;
    }
  }

  *out_host = BUF_strndup(host, host_len);
  if (*out_host == NULL) {
    return 0;
  }
  if (port == NULL) {
    *out_port = NULL;
    return 1;
  }
  *out_port = OPENSSL_strdup(port);
  if (*out_port == NULL) {
    OPENSSL_free(*out_host);
    *out_host = NULL;
    return 0;
  }
  return 1;
}

static int conn_state(BIO *bio, BIO_CONNECT *c) {
  int ret = -1, i;
  int (*cb)(const BIO *, int, int) = NULL;

  if (c->info_callback != NULL) {
    cb = c->info_callback;
  }

  for (;;) {
    switch (c->state) {
      case BIO_CONN_S_BEFORE:
        // If there's a hostname and a port, assume that both are
        // exactly what they say. If there is only a hostname, try
        // (just once) to split it into a hostname and port.

        if (c->param_hostname == NULL) {
          OPENSSL_PUT_ERROR(BIO, BIO_R_NO_HOSTNAME_SPECIFIED);
          goto exit_loop;
        }

        if (c->param_port == NULL) {
          char *host, *port;
          if (!split_host_and_port(&host, &port, c->param_hostname) ||
              port == NULL) {
            OPENSSL_free(host);
            OPENSSL_free(port);
            OPENSSL_PUT_ERROR(BIO, BIO_R_NO_PORT_SPECIFIED);
            ERR_add_error_data(2, "host=", c->param_hostname);
            goto exit_loop;
          }

          OPENSSL_free(c->param_port);
          c->param_port = port;
          OPENSSL_free(c->param_hostname);
          c->param_hostname = host;
        }

        if (!bio_ip_and_port_to_socket_and_addr(
                &bio->num, &c->them, &c->them_length, c->param_hostname,
                c->param_port)) {
          OPENSSL_PUT_ERROR(BIO, BIO_R_UNABLE_TO_CREATE_SOCKET);
          ERR_add_error_data(4, "host=", c->param_hostname, ":", c->param_port);
          goto exit_loop;
        }

        if (c->nbio) {
          if (!bio_socket_nbio(bio->num, 1)) {
            OPENSSL_PUT_ERROR(BIO, BIO_R_ERROR_SETTING_NBIO);
            ERR_add_error_data(4, "host=", c->param_hostname, ":",
                               c->param_port);
            goto exit_loop;
          }
        }

        i = 1;
        ret = setsockopt(bio->num, SOL_SOCKET, SO_KEEPALIVE, (char *)&i,
                         sizeof(i));
        if (ret < 0) {
          OPENSSL_PUT_SYSTEM_ERROR();
          OPENSSL_PUT_ERROR(BIO, BIO_R_KEEPALIVE);
          ERR_add_error_data(4, "host=", c->param_hostname, ":", c->param_port);
          goto exit_loop;
        }

        BIO_clear_retry_flags(bio);
        ret = connect(bio->num, (struct sockaddr*) &c->them, c->them_length);
        if (ret < 0) {
          if (bio_fd_should_retry(ret)) {
            BIO_set_flags(bio, (BIO_FLAGS_IO_SPECIAL | BIO_FLAGS_SHOULD_RETRY));
            c->state = BIO_CONN_S_BLOCKED_CONNECT;
            bio->retry_reason = BIO_RR_CONNECT;
          } else {
            OPENSSL_PUT_SYSTEM_ERROR();
            OPENSSL_PUT_ERROR(BIO, BIO_R_CONNECT_ERROR);
            ERR_add_error_data(4, "host=", c->param_hostname, ":",
                               c->param_port);
          }
          goto exit_loop;
        } else {
          c->state = BIO_CONN_S_OK;
        }
        break;

      case BIO_CONN_S_BLOCKED_CONNECT:
        i = bio_sock_error(bio->num);
        if (i) {
          if (bio_fd_should_retry(ret)) {
            BIO_set_flags(bio, (BIO_FLAGS_IO_SPECIAL | BIO_FLAGS_SHOULD_RETRY));
            c->state = BIO_CONN_S_BLOCKED_CONNECT;
            bio->retry_reason = BIO_RR_CONNECT;
            ret = -1;
          } else {
            BIO_clear_retry_flags(bio);
            OPENSSL_PUT_SYSTEM_ERROR();
            OPENSSL_PUT_ERROR(BIO, BIO_R_NBIO_CONNECT_ERROR);
            ERR_add_error_data(4, "host=", c->param_hostname, ":", c->param_port);
            ret = 0;
          }
          goto exit_loop;
        } else {
          c->state = BIO_CONN_S_OK;
        }
        break;

      case BIO_CONN_S_OK:
        ret = 1;
        goto exit_loop;
      default:
        assert(0);
        goto exit_loop;
    }

    if (cb != NULL) {
      ret = cb((BIO *)bio, c->state, ret);
      if (ret == 0) {
        goto end;
      }
    }
  }

exit_loop:
  if (cb != NULL) {
    ret = cb((BIO *)bio, c->state, ret);
  }

end:
  return ret;
}

static BIO_CONNECT *BIO_CONNECT_new(void) {
  BIO_CONNECT *ret = OPENSSL_malloc(sizeof(BIO_CONNECT));

  if (ret == NULL) {
    return NULL;
  }
  OPENSSL_memset(ret, 0, sizeof(BIO_CONNECT));

  ret->state = BIO_CONN_S_BEFORE;
  return ret;
}

static void BIO_CONNECT_free(BIO_CONNECT *c) {
  if (c == NULL) {
    return;
  }

  OPENSSL_free(c->param_hostname);
  OPENSSL_free(c->param_port);
  OPENSSL_free(c);
}

static int conn_new(BIO *bio) {
  bio->init = 0;
  bio->num = -1;
  bio->flags = 0;
  bio->ptr = (char *)BIO_CONNECT_new();
  return bio->ptr != NULL;
}

static void conn_close_socket(BIO *bio) {
  BIO_CONNECT *c = (BIO_CONNECT *) bio->ptr;

  if (bio->num == -1) {
    return;
  }

  // Only do a shutdown if things were established
  if (c->state == BIO_CONN_S_OK) {
    shutdown(bio->num, 2);
  }
  closesocket(bio->num);
  bio->num = -1;
}

static int conn_free(BIO *bio) {
  if (bio == NULL) {
    return 0;
  }

  if (bio->shutdown) {
    conn_close_socket(bio);
  }

  BIO_CONNECT_free((BIO_CONNECT*) bio->ptr);

  return 1;
}

static int conn_read(BIO *bio, char *out, int out_len) {
  int ret = 0;
  BIO_CONNECT *data;

  data = (BIO_CONNECT *)bio->ptr;
  if (data->state != BIO_CONN_S_OK) {
    ret = conn_state(bio, data);
    if (ret <= 0) {
      return ret;
    }
  }

  bio_clear_socket_error();
  ret = recv(bio->num, out, out_len, 0);
  BIO_clear_retry_flags(bio);
  if (ret <= 0) {
    if (bio_fd_should_retry(ret)) {
      BIO_set_retry_read(bio);
    }
  }

  return ret;
}

static int conn_write(BIO *bio, const char *in, int in_len) {
  int ret;
  BIO_CONNECT *data;

  data = (BIO_CONNECT *)bio->ptr;
  if (data->state != BIO_CONN_S_OK) {
    ret = conn_state(bio, data);
    if (ret <= 0) {
      return ret;
    }
  }

  bio_clear_socket_error();
  ret = send(bio->num, in, in_len, 0);
  BIO_clear_retry_flags(bio);
  if (ret <= 0) {
    if (bio_fd_should_retry(ret)) {
      BIO_set_retry_write(bio);
    }
  }

  return ret;
}

static long conn_ctrl(BIO *bio, int cmd, long num, void *ptr) {
  int *ip;
  long ret = 1;
  BIO_CONNECT *data;

  data = (BIO_CONNECT *)bio->ptr;

  switch (cmd) {
    case BIO_CTRL_RESET:
      ret = 0;
      data->state = BIO_CONN_S_BEFORE;
      conn_close_socket(bio);
      bio->flags = 0;
      break;
    case BIO_C_DO_STATE_MACHINE:
      // use this one to start the connection
      if (data->state != BIO_CONN_S_OK) {
        ret = (long)conn_state(bio, data);
      } else {
        ret = 1;
      }
      break;
    case BIO_C_SET_CONNECT:
      if (ptr != NULL) {
        bio->init = 1;
        if (num == 0) {
          OPENSSL_free(data->param_hostname);
          data->param_hostname = BUF_strdup(ptr);
          if (data->param_hostname == NULL) {
            ret = 0;
          }
        } else if (num == 1) {
          OPENSSL_free(data->param_port);
          data->param_port = BUF_strdup(ptr);
          if (data->param_port == NULL) {
            ret = 0;
          }
        } else {
          ret = 0;
        }
      }
      break;
    case BIO_C_SET_NBIO:
      data->nbio = (int)num;
      break;
    case BIO_C_GET_FD:
      if (bio->init) {
        ip = (int *)ptr;
        if (ip != NULL) {
          *ip = bio->num;
        }
        ret = bio->num;
      } else {
        ret = -1;
      }
      break;
    case BIO_CTRL_GET_CLOSE:
      ret = bio->shutdown;
      break;
    case BIO_CTRL_SET_CLOSE:
      bio->shutdown = (int)num;
      break;
    case BIO_CTRL_PENDING:
    case BIO_CTRL_WPENDING:
      ret = 0;
      break;
    case BIO_CTRL_FLUSH:
      break;
    case BIO_CTRL_GET_CALLBACK: {
      int (**fptr)(const BIO *bio, int state, int xret);
      fptr = (int (**)(const BIO *bio, int state, int xret))ptr;
      *fptr = data->info_callback;
    } break;
    default:
      ret = 0;
      break;
  }
  return ret;
}

static long conn_callback_ctrl(BIO *bio, int cmd, bio_info_cb fp) {
  long ret = 1;
  BIO_CONNECT *data;

  data = (BIO_CONNECT *)bio->ptr;

  switch (cmd) {
    case BIO_CTRL_SET_CALLBACK:
      data->info_callback = (int (*)(const struct bio_st *, int, int))fp;
      break;
    default:
      ret = 0;
      break;
  }
  return ret;
}

BIO *BIO_new_connect(const char *hostname) {
  BIO *ret;

  ret = BIO_new(BIO_s_connect());
  if (ret == NULL) {
    return NULL;
  }
  if (!BIO_set_conn_hostname(ret, hostname)) {
    BIO_free(ret);
    return NULL;
  }
  return ret;
}

static const BIO_METHOD methods_connectp = {
    BIO_TYPE_CONNECT, "socket connect",   conn_write, conn_read,
    NULL /* puts */,  NULL /* gets */,    conn_ctrl,  conn_new,
    conn_free,        conn_callback_ctrl,
};

const BIO_METHOD *BIO_s_connect(void) { return &methods_connectp; }

int BIO_set_conn_hostname(BIO *bio, const char *name) {
  return BIO_ctrl(bio, BIO_C_SET_CONNECT, 0, (void*) name);
}

int BIO_set_conn_port(BIO *bio, const char *port_str) {
  return BIO_ctrl(bio, BIO_C_SET_CONNECT, 1, (void*) port_str);
}

int BIO_set_conn_int_port(BIO *bio, const int *port) {
  char buf[DECIMAL_SIZE(int) + 1];
  BIO_snprintf(buf, sizeof(buf), "%d", *port);
  return BIO_set_conn_port(bio, buf);
}

int BIO_set_nbio(BIO *bio, int on) {
  return BIO_ctrl(bio, BIO_C_SET_NBIO, on, NULL);
}

int BIO_do_connect(BIO *bio) {
  return BIO_ctrl(bio, BIO_C_DO_STATE_MACHINE, 0, NULL);
}

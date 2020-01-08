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
 * Copyright (c) 1998-2006 The OpenSSL Project.  All rights reserved.
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

#include <openssl/err.h>

#include <assert.h>
#include <errno.h>
#include <inttypes.h>
#include <string.h>

#if defined(OPENSSL_WINDOWS)
OPENSSL_MSVC_PRAGMA(warning(push, 3))
#include <windows.h>
OPENSSL_MSVC_PRAGMA(warning(pop))
#endif

#include <openssl/mem.h>
#include <openssl/thread.h>

#include "../internal.h"
#include "./internal.h"


struct err_error_st {
  // file contains the filename where the error occurred.
  const char *file;
  // data contains a NUL-terminated string with optional data. It must be freed
  // with |OPENSSL_free|.
  char *data;
  // packed contains the error library and reason, as packed by ERR_PACK.
  uint32_t packed;
  // line contains the line number where the error occurred.
  uint16_t line;
  // mark indicates a reversion point in the queue. See |ERR_pop_to_mark|.
  unsigned mark : 1;
};

// ERR_STATE contains the per-thread, error queue.
typedef struct err_state_st {
  // errors contains the ERR_NUM_ERRORS most recent errors, organised as a ring
  // buffer.
  struct err_error_st errors[ERR_NUM_ERRORS];
  // top contains the index one past the most recent error. If |top| equals
  // |bottom| then the queue is empty.
  unsigned top;
  // bottom contains the index of the last error in the queue.
  unsigned bottom;

  // to_free, if not NULL, contains a pointer owned by this structure that was
  // previously a |data| pointer of one of the elements of |errors|.
  void *to_free;
} ERR_STATE;

extern const uint32_t kOpenSSLReasonValues[];
extern const size_t kOpenSSLReasonValuesLen;
extern const char kOpenSSLReasonStringData[];

// err_clear clears the given queued error.
static void err_clear(struct err_error_st *error) {
  OPENSSL_free(error->data);
  OPENSSL_memset(error, 0, sizeof(struct err_error_st));
}

static void err_copy(struct err_error_st *dst, const struct err_error_st *src) {
  err_clear(dst);
  dst->file = src->file;
  if (src->data != NULL) {
    dst->data = OPENSSL_strdup(src->data);
  }
  dst->packed = src->packed;
  dst->line = src->line;
}

// global_next_library contains the next custom library value to return.
static int global_next_library = ERR_NUM_LIBS;

// global_next_library_mutex protects |global_next_library| from concurrent
// updates.
static struct CRYPTO_STATIC_MUTEX global_next_library_mutex =
    CRYPTO_STATIC_MUTEX_INIT;

static void err_state_free(void *statep) {
  ERR_STATE *state = statep;

  if (state == NULL) {
    return;
  }

  for (unsigned i = 0; i < ERR_NUM_ERRORS; i++) {
    err_clear(&state->errors[i]);
  }
  OPENSSL_free(state->to_free);
  OPENSSL_free(state);
}

// err_get_state gets the ERR_STATE object for the current thread.
static ERR_STATE *err_get_state(void) {
  ERR_STATE *state = CRYPTO_get_thread_local(OPENSSL_THREAD_LOCAL_ERR);
  if (state == NULL) {
    state = OPENSSL_malloc(sizeof(ERR_STATE));
    if (state == NULL) {
      return NULL;
    }
    OPENSSL_memset(state, 0, sizeof(ERR_STATE));
    if (!CRYPTO_set_thread_local(OPENSSL_THREAD_LOCAL_ERR, state,
                                 err_state_free)) {
      return NULL;
    }
  }

  return state;
}

static uint32_t get_error_values(int inc, int top, const char **file, int *line,
                                 const char **data, int *flags) {
  unsigned i = 0;
  ERR_STATE *state;
  struct err_error_st *error;
  uint32_t ret;

  state = err_get_state();
  if (state == NULL || state->bottom == state->top) {
    return 0;
  }

  if (top) {
    assert(!inc);
    // last error
    i = state->top;
  } else {
    i = (state->bottom + 1) % ERR_NUM_ERRORS;
  }

  error = &state->errors[i];
  ret = error->packed;

  if (file != NULL && line != NULL) {
    if (error->file == NULL) {
      *file = "NA";
      *line = 0;
    } else {
      *file = error->file;
      *line = error->line;
    }
  }

  if (data != NULL) {
    if (error->data == NULL) {
      *data = "";
      if (flags != NULL) {
        *flags = 0;
      }
    } else {
      *data = error->data;
      if (flags != NULL) {
        *flags = ERR_FLAG_STRING;
      }
      // If this error is being removed, take ownership of data from
      // the error. The semantics are such that the caller doesn't
      // take ownership either. Instead the error system takes
      // ownership and retains it until the next call that affects the
      // error queue.
      if (inc) {
        if (error->data != NULL) {
          OPENSSL_free(state->to_free);
          state->to_free = error->data;
        }
        error->data = NULL;
      }
    }
  }

  if (inc) {
    assert(!top);
    err_clear(error);
    state->bottom = i;
  }

  return ret;
}

uint32_t ERR_get_error(void) {
  return get_error_values(1 /* inc */, 0 /* bottom */, NULL, NULL, NULL, NULL);
}

uint32_t ERR_get_error_line(const char **file, int *line) {
  return get_error_values(1 /* inc */, 0 /* bottom */, file, line, NULL, NULL);
}

uint32_t ERR_get_error_line_data(const char **file, int *line,
                                 const char **data, int *flags) {
  return get_error_values(1 /* inc */, 0 /* bottom */, file, line, data, flags);
}

uint32_t ERR_peek_error(void) {
  return get_error_values(0 /* peek */, 0 /* bottom */, NULL, NULL, NULL, NULL);
}

uint32_t ERR_peek_error_line(const char **file, int *line) {
  return get_error_values(0 /* peek */, 0 /* bottom */, file, line, NULL, NULL);
}

uint32_t ERR_peek_error_line_data(const char **file, int *line,
                                  const char **data, int *flags) {
  return get_error_values(0 /* peek */, 0 /* bottom */, file, line, data,
                          flags);
}

uint32_t ERR_peek_last_error(void) {
  return get_error_values(0 /* peek */, 1 /* top */, NULL, NULL, NULL, NULL);
}

uint32_t ERR_peek_last_error_line(const char **file, int *line) {
  return get_error_values(0 /* peek */, 1 /* top */, file, line, NULL, NULL);
}

uint32_t ERR_peek_last_error_line_data(const char **file, int *line,
                                       const char **data, int *flags) {
  return get_error_values(0 /* peek */, 1 /* top */, file, line, data, flags);
}

void ERR_clear_error(void) {
  ERR_STATE *const state = err_get_state();
  unsigned i;

  if (state == NULL) {
    return;
  }

  for (i = 0; i < ERR_NUM_ERRORS; i++) {
    err_clear(&state->errors[i]);
  }
  OPENSSL_free(state->to_free);
  state->to_free = NULL;

  state->top = state->bottom = 0;
}

void ERR_remove_thread_state(const CRYPTO_THREADID *tid) {
  if (tid != NULL) {
    assert(0);
    return;
  }

  ERR_clear_error();
}

int ERR_get_next_error_library(void) {
  int ret;

  CRYPTO_STATIC_MUTEX_lock_write(&global_next_library_mutex);
  ret = global_next_library++;
  CRYPTO_STATIC_MUTEX_unlock_write(&global_next_library_mutex);

  return ret;
}

void ERR_remove_state(unsigned long pid) {
  ERR_clear_error();
}

void ERR_clear_system_error(void) {
  errno = 0;
}

char *ERR_error_string(uint32_t packed_error, char *ret) {
  static char buf[ERR_ERROR_STRING_BUF_LEN];

  if (ret == NULL) {
    // TODO(fork): remove this.
    ret = buf;
  }

#if !defined(NDEBUG)
  // This is aimed to help catch callers who don't provide
  // |ERR_ERROR_STRING_BUF_LEN| bytes of space.
  OPENSSL_memset(ret, 0, ERR_ERROR_STRING_BUF_LEN);
#endif

  ERR_error_string_n(packed_error, ret, ERR_ERROR_STRING_BUF_LEN);

  return ret;
}

void ERR_error_string_n(uint32_t packed_error, char *buf, size_t len) {
  char lib_buf[64], reason_buf[64];
  const char *lib_str, *reason_str;
  unsigned lib, reason;

  if (len == 0) {
    return;
  }

  lib = ERR_GET_LIB(packed_error);
  reason = ERR_GET_REASON(packed_error);

  lib_str = ERR_lib_error_string(packed_error);
  reason_str = ERR_reason_error_string(packed_error);

  if (lib_str == NULL) {
    BIO_snprintf(lib_buf, sizeof(lib_buf), "lib(%u)", lib);
    lib_str = lib_buf;
  }

 if (reason_str == NULL) {
    BIO_snprintf(reason_buf, sizeof(reason_buf), "reason(%u)", reason);
    reason_str = reason_buf;
  }

  BIO_snprintf(buf, len, "error:%08" PRIx32 ":%s:OPENSSL_internal:%s",
               packed_error, lib_str, reason_str);

  if (strlen(buf) == len - 1) {
    // output may be truncated; make sure we always have 5 colon-separated
    // fields, i.e. 4 colons.
    static const unsigned num_colons = 4;
    unsigned i;
    char *s = buf;

    if (len <= num_colons) {
      // In this situation it's not possible to ensure that the correct number
      // of colons are included in the output.
      return;
    }

    for (i = 0; i < num_colons; i++) {
      char *colon = strchr(s, ':');
      char *last_pos = &buf[len - 1] - num_colons + i;

      if (colon == NULL || colon > last_pos) {
        // set colon |i| at last possible position (buf[len-1] is the
        // terminating 0). If we're setting this colon, then all whole of the
        // rest of the string must be colons in order to have the correct
        // number.
        OPENSSL_memset(last_pos, ':', num_colons - i);
        break;
      }

      s = colon + 1;
    }
  }
}

// err_string_cmp is a compare function for searching error values with
// |bsearch| in |err_string_lookup|.
static int err_string_cmp(const void *a, const void *b) {
  const uint32_t a_key = *((const uint32_t*) a) >> 15;
  const uint32_t b_key = *((const uint32_t*) b) >> 15;

  if (a_key < b_key) {
    return -1;
  } else if (a_key > b_key) {
    return 1;
  } else {
    return 0;
  }
}

// err_string_lookup looks up the string associated with |lib| and |key| in
// |values| and |string_data|. It returns the string or NULL if not found.
static const char *err_string_lookup(uint32_t lib, uint32_t key,
                                     const uint32_t *values,
                                     size_t num_values,
                                     const char *string_data) {
  // |values| points to data in err_data.h, which is generated by
  // err_data_generate.go. It's an array of uint32_t values. Each value has the
  // following structure:
  //   | lib  |    key    |    offset     |
  //   |6 bits|  11 bits  |    15 bits    |
  //
  // The |lib| value is a library identifier: one of the |ERR_LIB_*| values.
  // The |key| is a reason code, depending on the context.
  // The |offset| is the number of bytes from the start of |string_data| where
  // the (NUL terminated) string for this value can be found.
  //
  // Values are sorted based on treating the |lib| and |key| part as an
  // unsigned integer.
  if (lib >= (1 << 6) || key >= (1 << 11)) {
    return NULL;
  }
  uint32_t search_key = lib << 26 | key << 15;
  const uint32_t *result = bsearch(&search_key, values, num_values,
                                   sizeof(uint32_t), err_string_cmp);
  if (result == NULL) {
    return NULL;
  }

  return &string_data[(*result) & 0x7fff];
}

static const char *const kLibraryNames[ERR_NUM_LIBS] = {
    "invalid library (0)",
    "unknown library",                            // ERR_LIB_NONE
    "system library",                             // ERR_LIB_SYS
    "bignum routines",                            // ERR_LIB_BN
    "RSA routines",                               // ERR_LIB_RSA
    "Diffie-Hellman routines",                    // ERR_LIB_DH
    "public key routines",                        // ERR_LIB_EVP
    "memory buffer routines",                     // ERR_LIB_BUF
    "object identifier routines",                 // ERR_LIB_OBJ
    "PEM routines",                               // ERR_LIB_PEM
    "DSA routines",                               // ERR_LIB_DSA
    "X.509 certificate routines",                 // ERR_LIB_X509
    "ASN.1 encoding routines",                    // ERR_LIB_ASN1
    "configuration file routines",                // ERR_LIB_CONF
    "common libcrypto routines",                  // ERR_LIB_CRYPTO
    "elliptic curve routines",                    // ERR_LIB_EC
    "SSL routines",                               // ERR_LIB_SSL
    "BIO routines",                               // ERR_LIB_BIO
    "PKCS7 routines",                             // ERR_LIB_PKCS7
    "PKCS8 routines",                             // ERR_LIB_PKCS8
    "X509 V3 routines",                           // ERR_LIB_X509V3
    "random number generator",                    // ERR_LIB_RAND
    "ENGINE routines",                            // ERR_LIB_ENGINE
    "OCSP routines",                              // ERR_LIB_OCSP
    "UI routines",                                // ERR_LIB_UI
    "COMP routines",                              // ERR_LIB_COMP
    "ECDSA routines",                             // ERR_LIB_ECDSA
    "ECDH routines",                              // ERR_LIB_ECDH
    "HMAC routines",                              // ERR_LIB_HMAC
    "Digest functions",                           // ERR_LIB_DIGEST
    "Cipher functions",                           // ERR_LIB_CIPHER
    "HKDF functions",                             // ERR_LIB_HKDF
    "User defined functions",                     // ERR_LIB_USER
};

const char *ERR_lib_error_string(uint32_t packed_error) {
  const uint32_t lib = ERR_GET_LIB(packed_error);

  if (lib >= ERR_NUM_LIBS) {
    return NULL;
  }
  return kLibraryNames[lib];
}

const char *ERR_func_error_string(uint32_t packed_error) {
  return "OPENSSL_internal";
}

const char *ERR_reason_error_string(uint32_t packed_error) {
  const uint32_t lib = ERR_GET_LIB(packed_error);
  const uint32_t reason = ERR_GET_REASON(packed_error);

  if (lib == ERR_LIB_SYS) {
    if (reason < 127) {
      return strerror(reason);
    }
    return NULL;
  }

  if (reason < ERR_NUM_LIBS) {
    return kLibraryNames[reason];
  }

  if (reason < 100) {
    switch (reason) {
      case ERR_R_MALLOC_FAILURE:
        return "malloc failure";
      case ERR_R_SHOULD_NOT_HAVE_BEEN_CALLED:
        return "function should not have been called";
      case ERR_R_PASSED_NULL_PARAMETER:
        return "passed a null parameter";
      case ERR_R_INTERNAL_ERROR:
        return "internal error";
      case ERR_R_OVERFLOW:
        return "overflow";
      default:
        return NULL;
    }
  }

  return err_string_lookup(lib, reason, kOpenSSLReasonValues,
                           kOpenSSLReasonValuesLen, kOpenSSLReasonStringData);
}

void ERR_print_errors_cb(ERR_print_errors_callback_t callback, void *ctx) {
  char buf[ERR_ERROR_STRING_BUF_LEN];
  char buf2[1024];
  const char *file, *data;
  int line, flags;
  uint32_t packed_error;

  // thread_hash is the least-significant bits of the |ERR_STATE| pointer value
  // for this thread.
  const unsigned long thread_hash = (uintptr_t) err_get_state();

  for (;;) {
    packed_error = ERR_get_error_line_data(&file, &line, &data, &flags);
    if (packed_error == 0) {
      break;
    }

    ERR_error_string_n(packed_error, buf, sizeof(buf));
    BIO_snprintf(buf2, sizeof(buf2), "%lu:%s:%s:%d:%s\n", thread_hash, buf,
                 file, line, (flags & ERR_FLAG_STRING) ? data : "");
    if (callback(buf2, strlen(buf2), ctx) <= 0) {
      break;
    }
  }
}

static int print_errors_to_file(const char* msg, size_t msg_len, void* ctx) {
  assert(msg[msg_len] == '\0');
  FILE* fp = ctx;
  int res = fputs(msg, fp);
  return res < 0 ? 0 : 1;
}

void ERR_print_errors_fp(FILE *file) {
  ERR_print_errors_cb(print_errors_to_file, file);
}

// err_set_error_data sets the data on the most recent error.
static void err_set_error_data(char *data) {
  ERR_STATE *const state = err_get_state();
  struct err_error_st *error;

  if (state == NULL || state->top == state->bottom) {
    OPENSSL_free(data);
    return;
  }

  error = &state->errors[state->top];

  OPENSSL_free(error->data);
  error->data = data;
}

void ERR_put_error(int library, int unused, int reason, const char *file,
                   unsigned line) {
  ERR_STATE *const state = err_get_state();
  struct err_error_st *error;

  if (state == NULL) {
    return;
  }

  if (library == ERR_LIB_SYS && reason == 0) {
#if defined(OPENSSL_WINDOWS)
    reason = GetLastError();
#else
    reason = errno;
#endif
  }

  state->top = (state->top + 1) % ERR_NUM_ERRORS;
  if (state->top == state->bottom) {
    state->bottom = (state->bottom + 1) % ERR_NUM_ERRORS;
  }

  error = &state->errors[state->top];
  err_clear(error);
  error->file = file;
  error->line = line;
  error->packed = ERR_PACK(library, reason);
}

// ERR_add_error_data_vdata takes a variable number of const char* pointers,
// concatenates them and sets the result as the data on the most recent
// error.
static void err_add_error_vdata(unsigned num, va_list args) {
  size_t alloced, new_len, len = 0, substr_len;
  char *buf;
  const char *substr;
  unsigned i;

  alloced = 80;
  buf = OPENSSL_malloc(alloced + 1);
  if (buf == NULL) {
    return;
  }

  for (i = 0; i < num; i++) {
    substr = va_arg(args, const char *);
    if (substr == NULL) {
      continue;
    }

    substr_len = strlen(substr);
    new_len = len + substr_len;
    if (new_len > alloced) {
      char *new_buf;

      if (alloced + 20 + 1 < alloced) {
        // overflow.
        OPENSSL_free(buf);
        return;
      }

      alloced = new_len + 20;
      new_buf = OPENSSL_realloc(buf, alloced + 1);
      if (new_buf == NULL) {
        OPENSSL_free(buf);
        return;
      }
      buf = new_buf;
    }

    OPENSSL_memcpy(buf + len, substr, substr_len);
    len = new_len;
  }

  buf[len] = 0;
  err_set_error_data(buf);
}

void ERR_add_error_data(unsigned count, ...) {
  va_list args;
  va_start(args, count);
  err_add_error_vdata(count, args);
  va_end(args);
}

void ERR_add_error_dataf(const char *format, ...) {
  va_list ap;
  char *buf;
  static const unsigned buf_len = 256;

  // A fixed-size buffer is used because va_copy (which would be needed in
  // order to call vsnprintf twice and measure the buffer) wasn't defined until
  // C99.
  buf = OPENSSL_malloc(buf_len + 1);
  if (buf == NULL) {
    return;
  }

  va_start(ap, format);
  BIO_vsnprintf(buf, buf_len, format, ap);
  buf[buf_len] = 0;
  va_end(ap);

  err_set_error_data(buf);
}

int ERR_set_mark(void) {
  ERR_STATE *const state = err_get_state();

  if (state == NULL || state->bottom == state->top) {
    return 0;
  }
  state->errors[state->top].mark = 1;
  return 1;
}

int ERR_pop_to_mark(void) {
  ERR_STATE *const state = err_get_state();

  if (state == NULL) {
    return 0;
  }

  while (state->bottom != state->top) {
    struct err_error_st *error = &state->errors[state->top];

    if (error->mark) {
      error->mark = 0;
      return 1;
    }

    err_clear(error);
    if (state->top == 0) {
      state->top = ERR_NUM_ERRORS - 1;
    } else {
      state->top--;
    }
  }

  return 0;
}

void ERR_load_crypto_strings(void) {}

void ERR_free_strings(void) {}

void ERR_load_BIO_strings(void) {}

void ERR_load_ERR_strings(void) {}

void ERR_load_RAND_strings(void) {}

struct err_save_state_st {
  struct err_error_st *errors;
  size_t num_errors;
};

void ERR_SAVE_STATE_free(ERR_SAVE_STATE *state) {
  if (state == NULL) {
    return;
  }
  for (size_t i = 0; i < state->num_errors; i++) {
    err_clear(&state->errors[i]);
  }
  OPENSSL_free(state->errors);
  OPENSSL_free(state);
}

ERR_SAVE_STATE *ERR_save_state(void) {
  ERR_STATE *const state = err_get_state();
  if (state == NULL || state->top == state->bottom) {
    return NULL;
  }

  ERR_SAVE_STATE *ret = OPENSSL_malloc(sizeof(ERR_SAVE_STATE));
  if (ret == NULL) {
    return NULL;
  }

  // Errors are stored in the range (bottom, top].
  size_t num_errors = state->top >= state->bottom
                          ? state->top - state->bottom
                          : ERR_NUM_ERRORS + state->top - state->bottom;
  assert(num_errors < ERR_NUM_ERRORS);
  ret->errors = OPENSSL_malloc(num_errors * sizeof(struct err_error_st));
  if (ret->errors == NULL) {
    OPENSSL_free(ret);
    return NULL;
  }
  OPENSSL_memset(ret->errors, 0, num_errors * sizeof(struct err_error_st));
  ret->num_errors = num_errors;

  for (size_t i = 0; i < num_errors; i++) {
    size_t j = (state->bottom + i + 1) % ERR_NUM_ERRORS;
    err_copy(&ret->errors[i], &state->errors[j]);
  }
  return ret;
}

void ERR_restore_state(const ERR_SAVE_STATE *state) {
  if (state == NULL || state->num_errors == 0) {
    ERR_clear_error();
    return;
  }

  ERR_STATE *const dst = err_get_state();
  if (dst == NULL) {
    return;
  }

  for (size_t i = 0; i < state->num_errors; i++) {
    err_copy(&dst->errors[i], &state->errors[i]);
  }
  dst->top = state->num_errors - 1;
  dst->bottom = ERR_NUM_ERRORS - 1;
}

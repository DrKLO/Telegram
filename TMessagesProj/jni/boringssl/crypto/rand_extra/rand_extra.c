/* Copyright (c) 2017, Google Inc.
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

#include <openssl/rand.h>

#include <limits.h>


void RAND_seed(const void *buf, int num) {
  // OpenSSH calls |RAND_seed| before jailing on the assumption that any needed
  // file descriptors etc will be opened.
  uint8_t unused;
  RAND_bytes(&unused, sizeof(unused));
}

int RAND_load_file(const char *path, long num) {
  if (num < 0) {  // read the "whole file"
    return 1;
  } else if (num <= INT_MAX) {
    return (int) num;
  } else {
    return INT_MAX;
  }
}

const char *RAND_file_name(char *buf, size_t num) { return NULL; }

void RAND_add(const void *buf, int num, double entropy) {}

int RAND_egd(const char *path) {
  return 255;
}

int RAND_poll(void) {
  return 1;
}

int RAND_status(void) {
  return 1;
}

static const struct rand_meth_st kSSLeayMethod = {
  RAND_seed,
  RAND_bytes,
  RAND_cleanup,
  RAND_add,
  RAND_pseudo_bytes,
  RAND_status,
};

RAND_METHOD *RAND_SSLeay(void) {
  return (RAND_METHOD*) &kSSLeayMethod;
}

const RAND_METHOD *RAND_get_rand_method(void) { return RAND_SSLeay(); }

void RAND_set_rand_method(const RAND_METHOD *method) {}

void RAND_cleanup(void) {}

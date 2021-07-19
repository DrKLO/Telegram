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

#include <openssl/cpu.h>

#if defined(OPENSSL_ARM) && !defined(OPENSSL_STATIC_ARMCAP)
#include <errno.h>
#include <fcntl.h>
#include <sys/types.h>
#include <unistd.h>

#include <openssl/arm_arch.h>
#include <openssl/buf.h>
#include <openssl/mem.h>

#include "cpu-arm-linux.h"

#define AT_HWCAP 16
#define AT_HWCAP2 26

// |getauxval| is not available on Android until API level 20. Link it as a weak
// symbol and use other methods as fallback.
unsigned long getauxval(unsigned long type) __attribute__((weak));

static int open_eintr(const char *path, int flags) {
  int ret;
  do {
    ret = open(path, flags);
  } while (ret < 0 && errno == EINTR);
  return ret;
}

static ssize_t read_eintr(int fd, void *out, size_t len) {
  ssize_t ret;
  do {
    ret = read(fd, out, len);
  } while (ret < 0 && errno == EINTR);
  return ret;
}

// read_full reads exactly |len| bytes from |fd| to |out|. On error or end of
// file, it returns zero.
static int read_full(int fd, void *out, size_t len) {
  char *outp = out;
  while (len > 0) {
    ssize_t ret = read_eintr(fd, outp, len);
    if (ret <= 0) {
      return 0;
    }
    outp += ret;
    len -= ret;
  }
  return 1;
}

// read_file opens |path| and reads until end-of-file. On success, it returns
// one and sets |*out_ptr| and |*out_len| to a newly-allocated buffer with the
// contents. Otherwise, it returns zero.
static int read_file(char **out_ptr, size_t *out_len, const char *path) {
  int fd = open_eintr(path, O_RDONLY);
  if (fd < 0) {
    return 0;
  }

  static const size_t kReadSize = 1024;
  int ret = 0;
  size_t cap = kReadSize, len = 0;
  char *buf = OPENSSL_malloc(cap);
  if (buf == NULL) {
    goto err;
  }

  for (;;) {
    if (cap - len < kReadSize) {
      size_t new_cap = cap * 2;
      if (new_cap < cap) {
        goto err;
      }
      char *new_buf = OPENSSL_realloc(buf, new_cap);
      if (new_buf == NULL) {
        goto err;
      }
      buf = new_buf;
      cap = new_cap;
    }

    ssize_t bytes_read = read_eintr(fd, buf + len, kReadSize);
    if (bytes_read < 0) {
      goto err;
    }
    if (bytes_read == 0) {
      break;
    }
    len += bytes_read;
  }

  *out_ptr = buf;
  *out_len = len;
  ret = 1;
  buf = NULL;

err:
  OPENSSL_free(buf);
  close(fd);
  return ret;
}

// getauxval_proc behaves like |getauxval| but reads from /proc/self/auxv.
static unsigned long getauxval_proc(unsigned long type) {
  int fd = open_eintr("/proc/self/auxv", O_RDONLY);
  if (fd < 0) {
    return 0;
  }

  struct {
    unsigned long tag;
    unsigned long value;
  } entry;

  for (;;) {
    if (!read_full(fd, &entry, sizeof(entry)) ||
        (entry.tag == 0 && entry.value == 0)) {
      break;
    }
    if (entry.tag == type) {
      close(fd);
      return entry.value;
    }
  }
  close(fd);
  return 0;
}

extern uint32_t OPENSSL_armcap_P;

static int g_has_broken_neon, g_needs_hwcap2_workaround;

void OPENSSL_cpuid_setup(void) {
  char *cpuinfo_data;
  size_t cpuinfo_len;
  if (!read_file(&cpuinfo_data, &cpuinfo_len, "/proc/cpuinfo")) {
    return;
  }
  STRING_PIECE cpuinfo;
  cpuinfo.data = cpuinfo_data;
  cpuinfo.len = cpuinfo_len;

  // |getauxval| is not available on Android until API level 20. If it is
  // unavailable, read from /proc/self/auxv as a fallback. This is unreadable
  // on some versions of Android, so further fall back to /proc/cpuinfo.
  //
  // See
  // https://android.googlesource.com/platform/ndk/+/882ac8f3392858991a0e1af33b4b7387ec856bd2
  // and b/13679666 (Google-internal) for details.
  unsigned long hwcap = 0;
  if (getauxval != NULL) {
    hwcap = getauxval(AT_HWCAP);
  }
  if (hwcap == 0) {
    hwcap = getauxval_proc(AT_HWCAP);
  }
  if (hwcap == 0) {
    hwcap = crypto_get_arm_hwcap_from_cpuinfo(&cpuinfo);
  }

  // Clear NEON support if known broken.
  g_has_broken_neon = crypto_cpuinfo_has_broken_neon(&cpuinfo);
  if (g_has_broken_neon) {
    hwcap &= ~HWCAP_NEON;
  }

  // Matching OpenSSL, only report other features if NEON is present.
  if (hwcap & HWCAP_NEON) {
    OPENSSL_armcap_P |= ARMV7_NEON;

    // Some ARMv8 Android devices don't expose AT_HWCAP2. Fall back to
    // /proc/cpuinfo. See https://crbug.com/596156.
    unsigned long hwcap2 = 0;
    if (getauxval != NULL) {
      hwcap2 = getauxval(AT_HWCAP2);
    }
    if (hwcap2 == 0) {
      hwcap2 = crypto_get_arm_hwcap2_from_cpuinfo(&cpuinfo);
      g_needs_hwcap2_workaround = hwcap2 != 0;
    }

    if (hwcap2 & HWCAP2_AES) {
      OPENSSL_armcap_P |= ARMV8_AES;
    }
    if (hwcap2 & HWCAP2_PMULL) {
      OPENSSL_armcap_P |= ARMV8_PMULL;
    }
    if (hwcap2 & HWCAP2_SHA1) {
      OPENSSL_armcap_P |= ARMV8_SHA1;
    }
    if (hwcap2 & HWCAP2_SHA2) {
      OPENSSL_armcap_P |= ARMV8_SHA256;
    }
  }

  OPENSSL_free(cpuinfo_data);
}

int CRYPTO_has_broken_NEON(void) { return g_has_broken_neon; }

int CRYPTO_needs_hwcap2_workaround(void) { return g_needs_hwcap2_workaround; }

#endif  // OPENSSL_ARM && !OPENSSL_STATIC_ARMCAP

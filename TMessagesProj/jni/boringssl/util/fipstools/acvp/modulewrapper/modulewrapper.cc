/* Copyright (c) 2019, Google Inc.
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

#include <vector>

#include <assert.h>
#include <string.h>
#include <sys/uio.h>
#include <unistd.h>
#include <cstdarg>

#include <openssl/aes.h>
#include <openssl/sha.h>
#include <openssl/span.h>

static constexpr size_t kMaxArgs = 8;
static constexpr size_t kMaxArgLength = (1 << 20);
static constexpr size_t kMaxNameLength = 30;

static_assert((kMaxArgs - 1 * kMaxArgLength) + kMaxNameLength > (1 << 30),
              "Argument limits permit excessive messages");

using namespace bssl;

static bool ReadAll(int fd, void *in_data, size_t data_len) {
  uint8_t *data = reinterpret_cast<uint8_t *>(in_data);
  size_t done = 0;

  while (done < data_len) {
    ssize_t r;
    do {
      r = read(fd, &data[done], data_len - done);
    } while (r == -1 && errno == EINTR);

    if (r <= 0) {
      return false;
    }

    done += r;
  }

  return true;
}

template <typename... Args>
static bool WriteReply(int fd, Args... args) {
  std::vector<Span<const uint8_t>> spans = {args...};
  if (spans.empty() || spans.size() > kMaxArgs) {
    abort();
  }

  uint32_t nums[1 + kMaxArgs];
  iovec iovs[kMaxArgs + 1];
  nums[0] = spans.size();
  iovs[0].iov_base = nums;
  iovs[0].iov_len = sizeof(uint32_t) * (1 + spans.size());

  for (size_t i = 0; i < spans.size(); i++) {
    const auto &span = spans[i];
    nums[i + 1] = span.size();
    iovs[i + 1].iov_base = const_cast<uint8_t *>(span.data());
    iovs[i + 1].iov_len = span.size();
  }

  const size_t num_iov = spans.size() + 1;
  size_t iov_done = 0;
  while (iov_done < num_iov) {
    ssize_t r;
    do {
      r = writev(fd, &iovs[iov_done], num_iov - iov_done);
    } while (r == -1 && errno == EINTR);

    if (r <= 0) {
      return false;
    }

    size_t written = r;
    for (size_t i = iov_done; written > 0 && i < num_iov; i++) {
      iovec &iov = iovs[i];

      size_t done = written;
      if (done > iov.iov_len) {
        done = iov.iov_len;
      }

      iov.iov_base = reinterpret_cast<uint8_t *>(iov.iov_base) + done;
      iov.iov_len -= done;
      written -= done;

      if (iov.iov_len == 0) {
        iov_done++;
      }
    }

    assert(written == 0);
  }

  return true;
}

static bool GetConfig(const Span<const uint8_t> args[]) {
  static constexpr char kConfig[] =
      "["
      "{"
      "  \"algorithm\": \"SHA2-224\","
      "  \"revision\": \"1.0\","
      "  \"messageLength\": [{"
      "    \"min\": 0, \"max\": 65528, \"increment\": 8"
      "  }]"
      "},"
      "{"
      "  \"algorithm\": \"SHA2-256\","
      "  \"revision\": \"1.0\","
      "  \"messageLength\": [{"
      "    \"min\": 0, \"max\": 65528, \"increment\": 8"
      "  }]"
      "},"
      "{"
      "  \"algorithm\": \"SHA2-384\","
      "  \"revision\": \"1.0\","
      "  \"messageLength\": [{"
      "    \"min\": 0, \"max\": 65528, \"increment\": 8"
      "  }]"
      "},"
      "{"
      "  \"algorithm\": \"SHA2-512\","
      "  \"revision\": \"1.0\","
      "  \"messageLength\": [{"
      "    \"min\": 0, \"max\": 65528, \"increment\": 8"
      "  }]"
      "},"
      "{"
      "  \"algorithm\": \"SHA-1\","
      "  \"revision\": \"1.0\","
      "  \"messageLength\": [{"
      "    \"min\": 0, \"max\": 65528, \"increment\": 8"
      "  }]"
      "},"
      "{"
      "  \"algorithm\": \"ACVP-AES-ECB\","
      "  \"revision\": \"1.0\","
      "  \"direction\": [\"encrypt\", \"decrypt\"],"
      "  \"keyLen\": [128, 192, 256]"
      "},"
      "{"
      "  \"algorithm\": \"ACVP-AES-CBC\","
      "  \"revision\": \"1.0\","
      "  \"direction\": [\"encrypt\", \"decrypt\"],"
      "  \"keyLen\": [128, 192, 256]"
      "}"
      "]";
  return WriteReply(
      STDOUT_FILENO,
      Span<const uint8_t>(reinterpret_cast<const uint8_t *>(kConfig),
                          sizeof(kConfig) - 1));
}

template <uint8_t *(*OneShotHash)(const uint8_t *, size_t, uint8_t *),
          size_t DigestLength>
static bool Hash(const Span<const uint8_t> args[]) {
  uint8_t digest[DigestLength];
  OneShotHash(args[0].data(), args[0].size(), digest);
  return WriteReply(STDOUT_FILENO, Span<const uint8_t>(digest));
}

template <int (*SetKey)(const uint8_t *key, unsigned bits, AES_KEY *out),
          void (*Block)(const uint8_t *in, uint8_t *out, const AES_KEY *key)>
static bool AES(const Span<const uint8_t> args[]) {
  AES_KEY key;
  if (SetKey(args[0].data(), args[0].size() * 8, &key) != 0) {
    return false;
  }
  if (args[1].size() % AES_BLOCK_SIZE != 0) {
    return false;
  }

  std::vector<uint8_t> out;
  out.resize(args[1].size());
  for (size_t i = 0; i < args[1].size(); i += AES_BLOCK_SIZE) {
    Block(args[1].data() + i, &out[i], &key);
  }
  return WriteReply(STDOUT_FILENO, Span<const uint8_t>(out));
}

template <int (*SetKey)(const uint8_t *key, unsigned bits, AES_KEY *out),
          int Direction>
static bool AES_CBC(const Span<const uint8_t> args[]) {
  AES_KEY key;
  if (SetKey(args[0].data(), args[0].size() * 8, &key) != 0) {
    return false;
  }
  if (args[1].size() % AES_BLOCK_SIZE != 0 ||
      args[2].size() != AES_BLOCK_SIZE) {
    return false;
  }
  uint8_t iv[AES_BLOCK_SIZE];
  memcpy(iv, args[2].data(), AES_BLOCK_SIZE);

  std::vector<uint8_t> out;
  out.resize(args[1].size());
  AES_cbc_encrypt(args[1].data(), out.data(), args[1].size(), &key, iv,
                  Direction);
  return WriteReply(STDOUT_FILENO, Span<const uint8_t>(out));
}

static constexpr struct {
  const char name[kMaxNameLength + 1];
  uint8_t expected_args;
  bool (*handler)(const Span<const uint8_t>[]);
} kFunctions[] = {
    {"getConfig", 0, GetConfig},
    {"SHA-1", 1, Hash<SHA1, SHA_DIGEST_LENGTH>},
    {"SHA2-224", 1, Hash<SHA224, SHA224_DIGEST_LENGTH>},
    {"SHA2-256", 1, Hash<SHA256, SHA256_DIGEST_LENGTH>},
    {"SHA2-384", 1, Hash<SHA384, SHA256_DIGEST_LENGTH>},
    {"SHA2-512", 1, Hash<SHA512, SHA512_DIGEST_LENGTH>},
    {"AES/encrypt", 2, AES<AES_set_encrypt_key, AES_encrypt>},
    {"AES/decrypt", 2, AES<AES_set_decrypt_key, AES_decrypt>},
    {"AES-CBC/encrypt", 3, AES_CBC<AES_set_encrypt_key, AES_ENCRYPT>},
    {"AES-CBC/decrypt", 3, AES_CBC<AES_set_decrypt_key, AES_DECRYPT>},
};

int main() {
  uint32_t nums[1 + kMaxArgs];
  uint8_t *buf = nullptr;
  size_t buf_len = 0;
  Span<const uint8_t> args[kMaxArgs];

  for (;;) {
    if (!ReadAll(STDIN_FILENO, nums, sizeof(uint32_t) * 2)) {
      return 1;
    }

    const size_t num_args = nums[0];
    if (num_args == 0) {
      fprintf(stderr, "Invalid, zero-argument operation requested.\n");
      return 2;
    } else if (num_args > kMaxArgs) {
      fprintf(stderr,
              "Operation requested with %zu args, but %zu is the limit.\n",
              num_args, kMaxArgs);
      return 2;
    }

    if (num_args > 1 &&
        !ReadAll(STDIN_FILENO, &nums[2], sizeof(uint32_t) * (num_args - 1))) {
      return 1;
    }

    size_t need = 0;
    for (size_t i = 0; i < num_args; i++) {
      const size_t arg_length = nums[i + 1];
      if (i == 0 && arg_length > kMaxNameLength) {
        fprintf(stderr,
                "Operation with name of length %zu exceeded limit of %zu.\n",
                arg_length, kMaxNameLength);
        return 2;
      } else if (arg_length > kMaxArgLength) {
        fprintf(
            stderr,
            "Operation with argument of length %zu exceeded limit of %zu.\n",
            arg_length, kMaxArgLength);
        return 2;
      }

      // static_assert around kMaxArgs etc enforces that this doesn't overflow.
      need += arg_length;
    }

    if (need > buf_len) {
      free(buf);
      size_t alloced = need + (need >> 1);
      if (alloced < need) {
        abort();
      }
      buf = reinterpret_cast<uint8_t *>(malloc(alloced));
      if (buf == nullptr) {
        abort();
      }
      buf_len = alloced;
    }

    if (!ReadAll(STDIN_FILENO, buf, need)) {
      return 1;
    }

    size_t offset = 0;
    for (size_t i = 0; i < num_args; i++) {
      args[i] = Span<const uint8_t>(&buf[offset], nums[i + 1]);
      offset += nums[i + 1];
    }

    bool found = true;
    for (const auto &func : kFunctions) {
      if (args[0].size() == strlen(func.name) &&
          memcmp(args[0].data(), func.name, args[0].size()) == 0) {
        if (num_args - 1 != func.expected_args) {
          fprintf(stderr,
                  "\'%s\' operation received %zu arguments but expected %u.\n",
                  func.name, num_args - 1, func.expected_args);
          return 2;
        }

        if (!func.handler(&args[1])) {
          return 4;
        }

        found = true;
        break;
      }
    }

    if (!found) {
      const std::string name(reinterpret_cast<const char *>(args[0].data()),
                             args[0].size());
      fprintf(stderr, "Unknown operation: %s\n", name.c_str());
      return 3;
    }
  }
}

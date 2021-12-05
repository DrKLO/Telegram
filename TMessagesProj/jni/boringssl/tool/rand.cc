/* Copyright (c) 2015, Google Inc.
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

#include <string>
#include <vector>

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#include <openssl/rand.h>

#include "internal.h"


static const struct argument kArguments[] = {
    {
     "-hex", kBooleanArgument,
     "Hex encoded output."
    },
    {
     "", kOptionalArgument, "",
    },
};

bool Rand(const std::vector<std::string> &args) {
  bool forever = true, hex = false;
  size_t len = 0;

  if (!args.empty()) {
    std::vector<std::string> args_copy(args);
    const std::string &last_arg = args.back();

    if (!last_arg.empty() && last_arg[0] != '-') {
      char *endptr;
      unsigned long long num = strtoull(last_arg.c_str(), &endptr, 10);
      if (*endptr == 0) {
        len = num;
        forever = false;
        args_copy.pop_back();
      }
    }

    std::map<std::string, std::string> args_map;
    if (!ParseKeyValueArguments(&args_map, args_copy, kArguments)) {
      PrintUsage(kArguments);
      return false;
    }

    hex = args_map.count("-hex") > 0;
  }

  uint8_t buf[4096];
  uint8_t hex_buf[8192];

  size_t done = 0;
  while (forever || done < len) {
    size_t todo = sizeof(buf);
    if (!forever && todo > len - done) {
      todo = len - done;
    }
    RAND_bytes(buf, todo);
    if (hex) {
      static const char hextable[16 + 1] = "0123456789abcdef";
      for (unsigned i = 0; i < todo; i++) {
        hex_buf[i*2] = hextable[buf[i] >> 4];
        hex_buf[i*2 + 1] = hextable[buf[i] & 0xf];
      }
      if (fwrite(hex_buf, todo*2, 1, stdout) != 1) {
        return false;
      }
    } else {
      if (fwrite(buf, todo, 1, stdout) != 1) {
        return false;
      }
    }
    done += todo;
  }

  if (hex && fwrite("\n", 1, 1, stdout) != 1) {
    return false;
  }

  return true;
}

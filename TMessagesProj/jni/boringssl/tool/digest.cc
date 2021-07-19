/* Copyright (c) 2014, Google Inc.
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

#include <openssl/base.h>

#include <memory>
#include <string>
#include <vector>

#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdio.h>
#include <sys/stat.h>
#include <sys/types.h>

#if !defined(OPENSSL_WINDOWS)
#include <string.h>
#include <unistd.h>
#if !defined(O_BINARY)
#define O_BINARY 0
#endif
#else
OPENSSL_MSVC_PRAGMA(warning(push, 3))
#include <windows.h>
OPENSSL_MSVC_PRAGMA(warning(pop))
#include <io.h>
#define PATH_MAX MAX_PATH
typedef int ssize_t;
#endif

#include <openssl/digest.h>

#include "internal.h"


struct close_delete {
  void operator()(int *fd) {
    BORINGSSL_CLOSE(*fd);
  }
};

template<typename T, typename R, R (*func) (T*)>
struct func_delete {
  void operator()(T* obj) {
    func(obj);
  }
};

// Source is an awkward expression of a union type in C++: Stdin | File filename.
struct Source {
  enum Type {
    STDIN,
  };

  Source() : is_stdin_(false) {}
  explicit Source(Type) : is_stdin_(true) {}
  explicit Source(const std::string &name)
      : is_stdin_(false), filename_(name) {}

  bool is_stdin() const { return is_stdin_; }
  const std::string &filename() const { return filename_; }

 private:
  bool is_stdin_;
  std::string filename_;
};

static const char kStdinName[] = "standard input";

// OpenFile opens the regular file named |filename| and sets |*out_fd| to be a
// file descriptor to it. Returns true on sucess or prints an error to stderr
// and returns false on error.
static bool OpenFile(int *out_fd, const std::string &filename) {
  *out_fd = -1;

  int fd = BORINGSSL_OPEN(filename.c_str(), O_RDONLY | O_BINARY);
  if (fd < 0) {
    fprintf(stderr, "Failed to open input file '%s': %s\n", filename.c_str(),
            strerror(errno));
    return false;
  }
  std::unique_ptr<int, close_delete> scoped_fd(&fd);

#if !defined(OPENSSL_WINDOWS)
  struct stat st;
  if (fstat(fd, &st)) {
    fprintf(stderr, "Failed to stat input file '%s': %s\n", filename.c_str(),
            strerror(errno));
    return false;
  }

  if (!S_ISREG(st.st_mode)) {
    fprintf(stderr, "%s: not a regular file\n", filename.c_str());
    return false;
  }
#endif

  *out_fd = fd;
  scoped_fd.release();
  return true;
}

// SumFile hashes the contents of |source| with |md| and sets |*out_hex| to the
// hex-encoded result.
//
// It returns true on success or prints an error to stderr and returns false on
// error.
static bool SumFile(std::string *out_hex, const EVP_MD *md,
                    const Source &source) {
  std::unique_ptr<int, close_delete> scoped_fd;
  int fd;

  if (source.is_stdin()) {
    fd = 0;
  } else {
    if (!OpenFile(&fd, source.filename())) {
      return false;
    }
    scoped_fd.reset(&fd);
  }

  static const size_t kBufSize = 8192;
  std::unique_ptr<uint8_t[]> buf(new uint8_t[kBufSize]);

  bssl::ScopedEVP_MD_CTX ctx;
  if (!EVP_DigestInit_ex(ctx.get(), md, NULL)) {
    fprintf(stderr, "Failed to initialize EVP_MD_CTX.\n");
    return false;
  }

  for (;;) {
    ssize_t n;

    do {
      n = BORINGSSL_READ(fd, buf.get(), kBufSize);
    } while (n == -1 && errno == EINTR);

    if (n == 0) {
      break;
    } else if (n < 0) {
      fprintf(stderr, "Failed to read from %s: %s\n",
              source.is_stdin() ? kStdinName : source.filename().c_str(),
              strerror(errno));
      return false;
    }

    if (!EVP_DigestUpdate(ctx.get(), buf.get(), n)) {
      fprintf(stderr, "Failed to update hash.\n");
      return false;
    }
  }

  uint8_t digest[EVP_MAX_MD_SIZE];
  unsigned digest_len;
  if (!EVP_DigestFinal_ex(ctx.get(), digest, &digest_len)) {
    fprintf(stderr, "Failed to finish hash.\n");
    return false;
  }

  char hex_digest[EVP_MAX_MD_SIZE * 2];
  static const char kHextable[] = "0123456789abcdef";
  for (unsigned i = 0; i < digest_len; i++) {
    const uint8_t b = digest[i];
    hex_digest[i * 2] = kHextable[b >> 4];
    hex_digest[i * 2 + 1] = kHextable[b & 0xf];
  }
  *out_hex = std::string(hex_digest, digest_len * 2);

  return true;
}

// PrintFileSum hashes |source| with |md| and prints a line to stdout in the
// format of the coreutils *sum utilities. It returns true on success or prints
// an error to stderr and returns false on error.
static bool PrintFileSum(const EVP_MD *md, const Source &source) {
  std::string hex_digest;
  if (!SumFile(&hex_digest, md, source)) {
    return false;
  }

  // TODO: When given "--binary" or "-b", we should print " *" instead of "  "
  // between the digest and the filename.
  //
  // MSYS and Cygwin md5sum default to binary mode by default, whereas other
  // platforms' tools default to text mode by default. We default to text mode
  // by default and consider text mode equivalent to binary mode (i.e. we
  // always use Unix semantics, even on Windows), which means that our default
  // output will differ from the MSYS and Cygwin tools' default output.
  printf("%s  %s\n", hex_digest.c_str(),
         source.is_stdin() ? "-" : source.filename().c_str());
  return true;
}

// CheckModeArguments contains arguments for the check mode. See the
// sha256sum(1) man page for details.
struct CheckModeArguments {
  bool quiet = false;
  bool status = false;
  bool warn = false;
  bool strict = false;
};

// Check reads lines from |source| where each line is in the format of the
// coreutils *sum utilities. It attempts to verify each hash by reading the
// file named in the line.
//
// It returns true if all files were verified and, if |args.strict|, no input
// lines had formatting errors. Otherwise it prints errors to stderr and
// returns false.
static bool Check(const CheckModeArguments &args, const EVP_MD *md,
                  const Source &source) {
  std::unique_ptr<FILE, func_delete<FILE, int, fclose>> scoped_file;
  FILE *file;

  if (source.is_stdin()) {
    file = stdin;
  } else {
    int fd;
    if (!OpenFile(&fd, source.filename())) {
      return false;
    }

    file = BORINGSSL_FDOPEN(fd, "rb");
    if (!file) {
      perror("fdopen");
      BORINGSSL_CLOSE(fd);
      return false;
    }

    scoped_file = std::unique_ptr<FILE, func_delete<FILE, int, fclose>>(file);
  }

  const size_t hex_size = EVP_MD_size(md) * 2;
  char line[EVP_MAX_MD_SIZE * 2 + 2 /* spaces */ + PATH_MAX + 1 /* newline */ +
            1 /* NUL */];
  unsigned bad_lines = 0;
  unsigned parsed_lines = 0;
  unsigned error_lines = 0;
  unsigned bad_hash_lines = 0;
  unsigned line_no = 0;
  bool ok = true;
  bool draining_overlong_line = false;

  for (;;) {
    line_no++;

    if (fgets(line, sizeof(line), file) == nullptr) {
      if (feof(file)) {
        break;
      }
      fprintf(stderr, "Error reading from input.\n");
      return false;
    }

    size_t len = strlen(line);

    if (draining_overlong_line) {
      if (line[len - 1] == '\n') {
        draining_overlong_line = false;
      }
      continue;
    }

    const bool overlong = line[len - 1] != '\n' && !feof(file);

    if (len < hex_size + 2 /* spaces */ + 1 /* filename */ ||
        line[hex_size] != ' ' ||
        line[hex_size + 1] != ' ' ||
        overlong) {
      bad_lines++;
      if (args.warn) {
        fprintf(stderr, "%s: %u: improperly formatted line\n",
                source.is_stdin() ? kStdinName : source.filename().c_str(), line_no);
      }
      if (args.strict) {
        ok = false;
      }
      if (overlong) {
        draining_overlong_line = true;
      }
      continue;
    }

    if (line[len - 1] == '\n') {
      line[len - 1] = 0;
      len--;
    }

    parsed_lines++;

    // coreutils does not attempt to restrict relative or absolute paths in the
    // input so nor does this code.
    std::string calculated_hex_digest;
    const std::string target_filename(&line[hex_size + 2]);
    Source target_source;
    if (target_filename == "-") {
      // coreutils reads from stdin if the filename is "-".
      target_source = Source(Source::STDIN);
    } else {
      target_source = Source(target_filename);
    }

    if (!SumFile(&calculated_hex_digest, md, target_source)) {
      error_lines++;
      ok = false;
      continue;
    }

    if (calculated_hex_digest != std::string(line, hex_size)) {
      bad_hash_lines++;
      if (!args.status) {
        printf("%s: FAILED\n", target_filename.c_str());
      }
      ok = false;
      continue;
    }

    if (!args.quiet) {
      printf("%s: OK\n", target_filename.c_str());
    }
  }

  if (!args.status) {
    if (bad_lines > 0 && parsed_lines > 0) {
      fprintf(stderr, "WARNING: %u line%s improperly formatted\n", bad_lines,
              bad_lines == 1 ? " is" : "s are");
    }
    if (error_lines > 0) {
      fprintf(stderr, "WARNING: %u computed checksum(s) did NOT match\n",
              error_lines);
    }
  }

  if (parsed_lines == 0) {
    fprintf(stderr, "%s: no properly formatted checksum lines found.\n",
            source.is_stdin() ? kStdinName : source.filename().c_str());
    ok = false;
  }

  return ok;
}

// DigestSum acts like the coreutils *sum utilites, with the given hash
// function.
static bool DigestSum(const EVP_MD *md,
                      const std::vector<std::string> &args) {
  bool check_mode = false;
  CheckModeArguments check_args;
  bool check_mode_args_given = false;
  std::vector<Source> sources;

  auto it = args.begin();
  while (it != args.end()) {
    const std::string &arg = *it;
    if (!arg.empty() && arg[0] != '-') {
      break;
    }

    it++;

    if (arg == "--") {
      break;
    }

    if (arg == "-") {
      // "-" ends the argument list and indicates that stdin should be used.
      sources.push_back(Source(Source::STDIN));
      break;
    }

    if (arg.size() >= 2 && arg[0] == '-' && arg[1] != '-') {
      for (size_t i = 1; i < arg.size(); i++) {
        switch (arg[i]) {
          case 'b':
          case 't':
            // Binary/text mode – irrelevent, even on Windows.
            break;
          case 'c':
            check_mode = true;
            break;
          case 'w':
            check_mode_args_given = true;
            check_args.warn = true;
            break;
          default:
            fprintf(stderr, "Unknown option '%c'.\n", arg[i]);
            return false;
        }
      }
    } else if (arg == "--binary" || arg == "--text") {
      // Binary/text mode – irrelevent, even on Windows.
    } else if (arg == "--check") {
      check_mode = true;
    } else if (arg == "--quiet") {
      check_mode_args_given = true;
      check_args.quiet = true;
    } else if (arg == "--status") {
      check_mode_args_given = true;
      check_args.status = true;
    } else if (arg == "--warn") {
      check_mode_args_given = true;
      check_args.warn = true;
    } else if (arg == "--strict") {
      check_mode_args_given = true;
      check_args.strict = true;
    } else {
      fprintf(stderr, "Unknown option '%s'.\n", arg.c_str());
      return false;
    }
  }

  if (check_mode_args_given && !check_mode) {
    fprintf(
        stderr,
        "Check mode arguments are only meaningful when verifying checksums.\n");
    return false;
  }

  for (; it != args.end(); it++) {
    sources.push_back(Source(*it));
  }

  if (sources.empty()) {
    sources.push_back(Source(Source::STDIN));
  }

  bool ok = true;

  if (check_mode) {
    for (auto &source : sources) {
      ok &= Check(check_args, md, source);
    }
  } else {
    for (auto &source : sources) {
      ok &= PrintFileSum(md, source);
    }
  }

  return ok;
}

bool MD5Sum(const std::vector<std::string> &args) {
  return DigestSum(EVP_md5(), args);
}

bool SHA1Sum(const std::vector<std::string> &args) {
  return DigestSum(EVP_sha1(), args);
}

bool SHA224Sum(const std::vector<std::string> &args) {
  return DigestSum(EVP_sha224(), args);
}

bool SHA256Sum(const std::vector<std::string> &args) {
  return DigestSum(EVP_sha256(), args);
}

bool SHA384Sum(const std::vector<std::string> &args) {
  return DigestSum(EVP_sha384(), args);
}

bool SHA512Sum(const std::vector<std::string> &args) {
  return DigestSum(EVP_sha512(), args);
}

/* Copyright (c) 2018, Google Inc.
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

#include "handshake_util.h"

#include <assert.h>
#if defined(OPENSSL_LINUX) && !defined(OPENSSL_ANDROID)
#include <errno.h>
#include <fcntl.h>
#include <spawn.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#endif

#include <functional>

#include "async_bio.h"
#include "packeted_bio.h"
#include "test_config.h"
#include "test_state.h"

#include <openssl/ssl.h>

using namespace bssl;

bool RetryAsync(SSL *ssl, int ret) {
  const TestConfig *config = GetTestConfig(ssl);
  TestState *test_state = GetTestState(ssl);
  // No error or not async; don't retry.
  if (ret >= 0 || !config->async) {
    return false;
  }

  if (test_state->packeted_bio != nullptr &&
      PacketedBioAdvanceClock(test_state->packeted_bio)) {
    // The DTLS retransmit logic silently ignores write failures. So the test
    // may progress, allow writes through synchronously.
    AsyncBioEnforceWriteQuota(test_state->async_bio, false);
    int timeout_ret = DTLSv1_handle_timeout(ssl);
    AsyncBioEnforceWriteQuota(test_state->async_bio, true);

    if (timeout_ret < 0) {
      fprintf(stderr, "Error retransmitting.\n");
      return false;
    }
    return true;
  }

  // See if we needed to read or write more. If so, allow one byte through on
  // the appropriate end to maximally stress the state machine.
  switch (SSL_get_error(ssl, ret)) {
    case SSL_ERROR_WANT_READ:
      AsyncBioAllowRead(test_state->async_bio, 1);
      return true;
    case SSL_ERROR_WANT_WRITE:
      AsyncBioAllowWrite(test_state->async_bio, 1);
      return true;
    case SSL_ERROR_WANT_CHANNEL_ID_LOOKUP: {
      UniquePtr<EVP_PKEY> pkey = LoadPrivateKey(config->send_channel_id);
      if (!pkey) {
        return false;
      }
      test_state->channel_id = std::move(pkey);
      return true;
    }
    case SSL_ERROR_WANT_X509_LOOKUP:
      test_state->cert_ready = true;
      return true;
    case SSL_ERROR_PENDING_SESSION:
      test_state->session = std::move(test_state->pending_session);
      return true;
    case SSL_ERROR_PENDING_CERTIFICATE:
      test_state->early_callback_ready = true;
      return true;
    case SSL_ERROR_WANT_PRIVATE_KEY_OPERATION:
      test_state->private_key_retries++;
      return true;
    case SSL_ERROR_WANT_CERTIFICATE_VERIFY:
      test_state->custom_verify_ready = true;
      return true;
    default:
      return false;
  }
}

int CheckIdempotentError(const char *name, SSL *ssl,
                         std::function<int()> func) {
  int ret = func();
  int ssl_err = SSL_get_error(ssl, ret);
  uint32_t err = ERR_peek_error();
  if (ssl_err == SSL_ERROR_SSL || ssl_err == SSL_ERROR_ZERO_RETURN) {
    int ret2 = func();
    int ssl_err2 = SSL_get_error(ssl, ret2);
    uint32_t err2 = ERR_peek_error();
    if (ret != ret2 || ssl_err != ssl_err2 || err != err2) {
      fprintf(stderr, "Repeating %s did not replay the error.\n", name);
      char buf[256];
      ERR_error_string_n(err, buf, sizeof(buf));
      fprintf(stderr, "Wanted: %d %d %s\n", ret, ssl_err, buf);
      ERR_error_string_n(err2, buf, sizeof(buf));
      fprintf(stderr, "Got:    %d %d %s\n", ret2, ssl_err2, buf);
      // runner treats exit code 90 as always failing. Otherwise, it may
      // accidentally consider the result an expected protocol failure.
      exit(90);
    }
  }
  return ret;
}

#if defined(OPENSSL_LINUX) && !defined(OPENSSL_ANDROID)

// MoveBIOs moves the |BIO|s of |src| to |dst|.  It is used for handoff.
static void MoveBIOs(SSL *dest, SSL *src) {
  BIO *rbio = SSL_get_rbio(src);
  BIO_up_ref(rbio);
  SSL_set0_rbio(dest, rbio);

  BIO *wbio = SSL_get_wbio(src);
  BIO_up_ref(wbio);
  SSL_set0_wbio(dest, wbio);

  SSL_set0_rbio(src, nullptr);
  SSL_set0_wbio(src, nullptr);
}

static bool HandoffReady(SSL *ssl, int ret) {
  return ret < 0 && SSL_get_error(ssl, ret) == SSL_ERROR_HANDOFF;
}

static ssize_t read_eintr(int fd, void *out, size_t len) {
  ssize_t ret;
  do {
    ret = read(fd, out, len);
  } while (ret < 0 && errno == EINTR);
  return ret;
}

static ssize_t write_eintr(int fd, const void *in, size_t len) {
  ssize_t ret;
  do {
    ret = write(fd, in, len);
  } while (ret < 0 && errno == EINTR);
  return ret;
}

static ssize_t waitpid_eintr(pid_t pid, int *wstatus, int options) {
  pid_t ret;
  do {
    ret = waitpid(pid, wstatus, options);
  } while (ret < 0 && errno == EINTR);
  return ret;
}

// Proxy relays data between |socket|, which is connected to the client, and the
// handshaker, which is connected to the numerically specified file descriptors,
// until the handshaker returns control.
static bool Proxy(BIO *socket, bool async, int control, int rfd, int wfd) {
  for (;;) {
    fd_set rfds;
    FD_ZERO(&rfds);
    FD_SET(wfd, &rfds);
    FD_SET(control, &rfds);
    int fd_max = wfd > control ? wfd : control;
    if (select(fd_max + 1, &rfds, nullptr, nullptr, nullptr) == -1) {
      perror("select");
      return false;
    }

    char buf[64];
    ssize_t bytes;
    if (FD_ISSET(wfd, &rfds) &&
        (bytes = read_eintr(wfd, buf, sizeof(buf))) > 0) {
      char *b = buf;
      while (bytes) {
        int written = BIO_write(socket, b, bytes);
        if (!written) {
          fprintf(stderr, "BIO_write wrote nothing\n");
          return false;
        }
        if (written < 0) {
          if (async) {
            AsyncBioAllowWrite(socket, 1);
            continue;
          }
          fprintf(stderr, "BIO_write failed\n");
          return false;
        }
        b += written;
        bytes -= written;
      }
      // Flush all pending data from the handshaker to the client before
      // considering control messages.
      continue;
    }

    if (!FD_ISSET(control, &rfds)) {
      continue;
    }

    char msg;
    if (read_eintr(control, &msg, 1) != 1) {
      perror("read");
      return false;
    }
    switch (msg) {
      case kControlMsgHandback:
        return true;
      case kControlMsgError:
        return false;
      case kControlMsgWantRead:
        break;
      default:
        fprintf(stderr, "Unknown control message from handshaker: %c\n", msg);
        return false;
    }

    char readbuf[64];
    if (async) {
      AsyncBioAllowRead(socket, 1);
    }
    int read = BIO_read(socket, readbuf, sizeof(readbuf));
    if (read < 1) {
      fprintf(stderr, "BIO_read failed\n");
      return false;
    }
    ssize_t written = write_eintr(rfd, readbuf, read);
    if (written == -1) {
      perror("write");
      return false;
    }
    if (written != read) {
      fprintf(stderr, "short write (%zu of %d bytes)\n", written, read);
      return false;
    }
    // The handshaker blocks on the control channel, so we have to signal
    // it that the data have been written.
    msg = kControlMsgWriteCompleted;
    if (write_eintr(control, &msg, 1) != 1) {
      perror("write");
      return false;
    }
  }
}

class ScopedFD {
 public:
  explicit ScopedFD(int fd): fd_(fd) {}
  ~ScopedFD() { close(fd_); }
 private:
  const int fd_;
};

// RunHandshaker forks and execs the handshaker binary, handing off |input|,
// and, after proxying some amount of handshake traffic, handing back |out|.
static bool RunHandshaker(BIO *bio, const TestConfig *config, bool is_resume,
                          const Array<uint8_t> &input,
                          Array<uint8_t> *out) {
  if (config->handshaker_path.empty()) {
    fprintf(stderr, "no -handshaker-path specified\n");
    return false;
  }
  struct stat dummy;
  if (stat(config->handshaker_path.c_str(), &dummy) == -1) {
    perror(config->handshaker_path.c_str());
    return false;
  }

  // A datagram socket guarantees that writes are all-or-nothing.
  int control[2];
  if (socketpair(AF_LOCAL, SOCK_DGRAM, 0, control) != 0) {
    perror("socketpair");
    return false;
  }
  int rfd[2], wfd[2];
  // We use pipes, rather than some other mechanism, for their buffers.  During
  // the handshake, this process acts as a dumb proxy until receiving the
  // handback signal, which arrives asynchronously.  The race condition means
  // that this process could incorrectly proxy post-handshake data from the
  // client to the handshaker.
  //
  // To avoid this, this process never proxies data to the handshaker that the
  // handshaker has not explicitly requested as a result of hitting
  // |SSL_ERROR_WANT_READ|.  Pipes allow the data to sit in a buffer while the
  // two processes synchronize over the |control| channel.
  if (pipe(rfd) != 0 || pipe(wfd) != 0) {
    perror("pipe2");
    return false;
  }

  fflush(stdout);
  fflush(stderr);

  std::vector<char *> args;
  bssl::UniquePtr<char> handshaker_path(
      OPENSSL_strdup(config->handshaker_path.c_str()));
  args.push_back(handshaker_path.get());
  char resume[] = "-handshaker-resume";
  if (is_resume) {
    args.push_back(resume);
  }
  // config->argv omits argv[0].
  for (int j = 0; j < config->argc; ++j) {
    args.push_back(config->argv[j]);
  }
  args.push_back(nullptr);

  posix_spawn_file_actions_t actions;
  if (posix_spawn_file_actions_init(&actions) != 0 ||
      posix_spawn_file_actions_addclose(&actions, control[0]) ||
      posix_spawn_file_actions_addclose(&actions, rfd[1]) ||
      posix_spawn_file_actions_addclose(&actions, wfd[0])) {
    return false;
  }
  assert(kFdControl != rfd[0]);
  assert(kFdControl != wfd[1]);
  if (control[1] != kFdControl &&
      posix_spawn_file_actions_adddup2(&actions, control[1], kFdControl) != 0) {
    return false;
  }
  assert(kFdProxyToHandshaker != wfd[1]);
  if (rfd[0] != kFdProxyToHandshaker &&
      posix_spawn_file_actions_adddup2(&actions, rfd[0],
                                       kFdProxyToHandshaker) != 0) {
    return false;
  }
  if (wfd[1] != kFdHandshakerToProxy &&
      posix_spawn_file_actions_adddup2(&actions, wfd[1],
                                       kFdHandshakerToProxy) != 0) {
      return false;
  }

  // MSan doesn't know that |posix_spawn| initializes its output, so initialize
  // it to -1.
  pid_t handshaker_pid = -1;
  int ret = posix_spawn(&handshaker_pid, args[0], &actions, nullptr,
                        args.data(), environ);
  if (posix_spawn_file_actions_destroy(&actions) != 0 ||
      ret != 0) {
    return false;
  }

  close(control[1]);
  close(rfd[0]);
  close(wfd[1]);
  ScopedFD rfd_closer(rfd[1]);
  ScopedFD wfd_closer(wfd[0]);
  ScopedFD control_closer(control[0]);

  if (write_eintr(control[0], input.data(), input.size()) == -1) {
    perror("write");
    return false;
  }
  bool ok = Proxy(bio, config->async, control[0], rfd[1], wfd[0]);
  int wstatus;
  if (waitpid_eintr(handshaker_pid, &wstatus, 0) != handshaker_pid) {
    perror("waitpid");
    return false;
  }
  if (ok && wstatus) {
    fprintf(stderr, "handshaker exited irregularly\n");
    return false;
  }
  if (!ok) {
    return false;  // This is a "good", i.e. expected, error.
  }

  constexpr size_t kBufSize = 1024 * 1024;
  bssl::UniquePtr<uint8_t> buf((uint8_t *) OPENSSL_malloc(kBufSize));
  int len = read_eintr(control[0], buf.get(), kBufSize);
  if (len == -1) {
    perror("read");
    return false;
  }
  out->CopyFrom({buf.get(), (size_t)len});
  return true;
}

// PrepareHandoff accepts the |ClientHello| from |ssl| and serializes state to
// be passed to the handshaker.  The serialized state includes both the SSL
// handoff, as well test-related state.
static bool PrepareHandoff(SSL *ssl, SettingsWriter *writer,
                           Array<uint8_t> *out_handoff) {
  SSL_set_handoff_mode(ssl, 1);

  const TestConfig *config = GetTestConfig(ssl);
  int ret = -1;
  do {
    ret = CheckIdempotentError(
        "SSL_do_handshake", ssl,
        [&]() -> int { return SSL_do_handshake(ssl); });
  } while (!HandoffReady(ssl, ret) &&
           config->async &&
           RetryAsync(ssl, ret));
  if (!HandoffReady(ssl, ret)) {
    fprintf(stderr, "Handshake failed while waiting for handoff.\n");
    return false;
  }

  ScopedCBB cbb;
  SSL_CLIENT_HELLO hello;
  if (!CBB_init(cbb.get(), 512) ||
      !SSL_serialize_handoff(ssl, cbb.get(), &hello) ||
      !writer->WriteHandoff({CBB_data(cbb.get()), CBB_len(cbb.get())}) ||
      !SerializeContextState(ssl->ctx.get(), cbb.get()) ||
      !GetTestState(ssl)->Serialize(cbb.get())) {
    fprintf(stderr, "Handoff serialisation failed.\n");
    return false;
  }
  return CBBFinishArray(cbb.get(), out_handoff);
}

// DoSplitHandshake delegates the SSL handshake to a separate process, called
// the handshaker.  This process proxies I/O between the handshaker and the
// client, using the |BIO| from |ssl|.  After a successful handshake, |ssl| is
// replaced with a new |SSL| object, in a way that is intended to be invisible
// to the caller.
bool DoSplitHandshake(UniquePtr<SSL> *ssl, SettingsWriter *writer,
                      bool is_resume) {
  assert(SSL_get_rbio(ssl->get()) == SSL_get_wbio(ssl->get()));
  Array<uint8_t> handshaker_input;
  const TestConfig *config = GetTestConfig(ssl->get());
  // out is the response from the handshaker, which includes a serialized
  // handback message, but also serialized updates to the |TestState|.
  Array<uint8_t> out;
  if (!PrepareHandoff(ssl->get(), writer, &handshaker_input) ||
      !RunHandshaker(SSL_get_rbio(ssl->get()), config, is_resume,
                     handshaker_input, &out)) {
    fprintf(stderr, "Handoff failed.\n");
    return false;
  }

  UniquePtr<SSL> ssl_handback =
      config->NewSSL((*ssl)->ctx.get(), nullptr, false, nullptr);
  if (!ssl_handback) {
    return false;
  }
  CBS output, handback;
  CBS_init(&output, out.data(), out.size());
  if (!CBS_get_u24_length_prefixed(&output, &handback) ||
      !DeserializeContextState(&output, ssl_handback->ctx.get()) ||
      !SetTestState(ssl_handback.get(), TestState::Deserialize(
          &output, ssl_handback->ctx.get())) ||
      !GetTestState(ssl_handback.get()) ||
      !writer->WriteHandback(handback) ||
      !SSL_apply_handback(ssl_handback.get(), handback)) {
    fprintf(stderr, "Handback failed.\n");
    return false;
  }
  MoveBIOs(ssl_handback.get(), ssl->get());
  GetTestState(ssl_handback.get())->async_bio =
      GetTestState(ssl->get())->async_bio;
  GetTestState(ssl->get())->async_bio = nullptr;

  *ssl = std::move(ssl_handback);
  return true;
}

#endif  // defined(OPENSSL_LINUX) && !defined(OPENSSL_ANDROID)

// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/sync_socket.h"

#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <poll.h>
#include <stddef.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/types.h>

#if defined(OS_SOLARIS)
#include <sys/filio.h>
#endif

#include "base/files/file_util.h"
#include "base/logging.h"
#include "base/threading/scoped_blocking_call.h"
#include "build/build_config.h"

namespace base {

namespace {
// To avoid users sending negative message lengths to Send/Receive
// we clamp message lengths, which are size_t, to no more than INT_MAX.
const size_t kMaxMessageLength = static_cast<size_t>(INT_MAX);

// Writes |length| of |buffer| into |handle|.  Returns the number of bytes
// written or zero on error.  |length| must be greater than 0.
size_t SendHelper(SyncSocket::Handle handle,
                  const void* buffer,
                  size_t length) {
  DCHECK_GT(length, 0u);
  DCHECK_LE(length, kMaxMessageLength);
  DCHECK_NE(handle, SyncSocket::kInvalidHandle);
  const char* charbuffer = static_cast<const char*>(buffer);
  return WriteFileDescriptor(handle, charbuffer, length)
             ? static_cast<size_t>(length)
             : 0;
}

}  // namespace

// static
bool SyncSocket::CreatePair(SyncSocket* socket_a, SyncSocket* socket_b) {
  DCHECK_NE(socket_a, socket_b);
  DCHECK(!socket_a->IsValid());
  DCHECK(!socket_b->IsValid());

#if defined(OS_MACOSX)
  int nosigpipe = 1;
#endif  // defined(OS_MACOSX)

  ScopedHandle handles[2];

  {
    Handle raw_handles[2] = {kInvalidHandle, kInvalidHandle};
    if (socketpair(AF_UNIX, SOCK_STREAM, 0, raw_handles) != 0) {
      return false;
    }
    handles[0].reset(raw_handles[0]);
    handles[1].reset(raw_handles[1]);
  }

#if defined(OS_MACOSX)
  // On OSX an attempt to read or write to a closed socket may generate a
  // SIGPIPE rather than returning -1.  setsockopt will shut this off.
  if (0 != setsockopt(handles[0].get(), SOL_SOCKET, SO_NOSIGPIPE, &nosigpipe,
                      sizeof(nosigpipe)) ||
      0 != setsockopt(handles[1].get(), SOL_SOCKET, SO_NOSIGPIPE, &nosigpipe,
                      sizeof(nosigpipe))) {
    return false;
  }
#endif

  // Copy the handles out for successful return.
  socket_a->handle_ = std::move(handles[0]);
  socket_b->handle_ = std::move(handles[1]);

  return true;
}

void SyncSocket::Close() {
  handle_.reset();
}

size_t SyncSocket::Send(const void* buffer, size_t length) {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  return SendHelper(handle(), buffer, length);
}

size_t SyncSocket::Receive(void* buffer, size_t length) {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  DCHECK_GT(length, 0u);
  DCHECK_LE(length, kMaxMessageLength);
  DCHECK(IsValid());
  char* charbuffer = static_cast<char*>(buffer);
  if (ReadFromFD(handle(), charbuffer, length))
    return length;
  return 0;
}

size_t SyncSocket::ReceiveWithTimeout(void* buffer,
                                      size_t length,
                                      TimeDelta timeout) {
  ScopedBlockingCall scoped_blocking_call(FROM_HERE, BlockingType::MAY_BLOCK);
  DCHECK_GT(length, 0u);
  DCHECK_LE(length, kMaxMessageLength);
  DCHECK(IsValid());

  // Only timeouts greater than zero and less than one second are allowed.
  DCHECK_GT(timeout.InMicroseconds(), 0);
  DCHECK_LT(timeout.InMicroseconds(),
            TimeDelta::FromSeconds(1).InMicroseconds());

  // Track the start time so we can reduce the timeout as data is read.
  TimeTicks start_time = TimeTicks::Now();
  const TimeTicks finish_time = start_time + timeout;

  struct pollfd pollfd;
  pollfd.fd = handle();
  pollfd.events = POLLIN;
  pollfd.revents = 0;

  size_t bytes_read_total = 0;
  while (bytes_read_total < length) {
    const TimeDelta this_timeout = finish_time - TimeTicks::Now();
    const int timeout_ms =
        static_cast<int>(this_timeout.InMillisecondsRoundedUp());
    if (timeout_ms <= 0)
      break;
    const int poll_result = poll(&pollfd, 1, timeout_ms);
    // Handle EINTR manually since we need to update the timeout value.
    if (poll_result == -1 && errno == EINTR)
      continue;
    // Return if other type of error or a timeout.
    if (poll_result <= 0)
      return bytes_read_total;

    // poll() only tells us that data is ready for reading, not how much.  We
    // must Peek() for the amount ready for reading to avoid blocking.
    // At hang up (POLLHUP), the write end has been closed and there might still
    // be data to be read.
    // No special handling is needed for error (POLLERR); we can let any of the
    // following operations fail and handle it there.
    DCHECK(pollfd.revents & (POLLIN | POLLHUP | POLLERR)) << pollfd.revents;
    const size_t bytes_to_read = std::min(Peek(), length - bytes_read_total);

    // There may be zero bytes to read if the socket at the other end closed.
    if (!bytes_to_read)
      return bytes_read_total;

    const size_t bytes_received =
        Receive(static_cast<char*>(buffer) + bytes_read_total, bytes_to_read);
    bytes_read_total += bytes_received;
    if (bytes_received != bytes_to_read)
      return bytes_read_total;
  }

  return bytes_read_total;
}

size_t SyncSocket::Peek() {
  DCHECK(IsValid());
  int number_chars = 0;
  if (ioctl(handle_.get(), FIONREAD, &number_chars) == -1) {
    // If there is an error in ioctl, signal that the channel would block.
    return 0;
  }
  DCHECK_GE(number_chars, 0);
  return number_chars;
}

bool SyncSocket::IsValid() const {
  return handle_.is_valid();
}

SyncSocket::Handle SyncSocket::handle() const {
  return handle_.get();
}

SyncSocket::Handle SyncSocket::Release() {
  return handle_.release();
}

bool CancelableSyncSocket::Shutdown() {
  DCHECK(IsValid());
  return HANDLE_EINTR(shutdown(handle(), SHUT_RDWR)) >= 0;
}

size_t CancelableSyncSocket::Send(const void* buffer, size_t length) {
  DCHECK_GT(length, 0u);
  DCHECK_LE(length, kMaxMessageLength);
  DCHECK(IsValid());

  const int flags = fcntl(handle(), F_GETFL);
  if (flags != -1 && (flags & O_NONBLOCK) == 0) {
    // Set the socket to non-blocking mode for sending if its original mode
    // is blocking.
    fcntl(handle(), F_SETFL, flags | O_NONBLOCK);
  }

  const size_t len = SendHelper(handle(), buffer, length);

  if (flags != -1 && (flags & O_NONBLOCK) == 0) {
    // Restore the original flags.
    fcntl(handle(), F_SETFL, flags);
  }

  return len;
}

// static
bool CancelableSyncSocket::CreatePair(CancelableSyncSocket* socket_a,
                                      CancelableSyncSocket* socket_b) {
  return SyncSocket::CreatePair(socket_a, socket_b);
}

}  // namespace base

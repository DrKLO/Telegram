// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/sync_socket.h"

#include <errno.h>
#include <limits.h>
#include <stddef.h>
#include <stdio.h>
#include <sys/types.h>

#include "base/logging.h"

namespace base {

// static
bool SyncSocket::CreatePair(SyncSocket* socket_a, SyncSocket* socket_b) {
  return false;
}

void SyncSocket::Close() {
  handle_.reset();
}

size_t SyncSocket::Send(const void* buffer, size_t length) {
  const ssize_t bytes_written = write(handle(), buffer, length);
  return bytes_written > 0 ? bytes_written : 0;
}

size_t SyncSocket::Receive(void* buffer, size_t length) {
  const ssize_t bytes_read = read(handle(), buffer, length);
  return bytes_read > 0 ? bytes_read : 0;
}

size_t SyncSocket::ReceiveWithTimeout(void* buffer, size_t length, TimeDelta) {
  NOTIMPLEMENTED();
  return 0;
}

size_t SyncSocket::Peek() {
  NOTIMPLEMENTED();
  return 0;
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

size_t CancelableSyncSocket::Send(const void* buffer, size_t length) {
  return SyncSocket::Send(buffer, length);
}

bool CancelableSyncSocket::Shutdown() {
  Close();
  return true;
}

// static
bool CancelableSyncSocket::CreatePair(CancelableSyncSocket* socket_a,
                                      CancelableSyncSocket* socket_b) {
  return SyncSocket::CreatePair(socket_a, socket_b);
}

}  // namespace base

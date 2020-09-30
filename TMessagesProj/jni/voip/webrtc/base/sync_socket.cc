// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/sync_socket.h"

namespace base {

const SyncSocket::Handle SyncSocket::kInvalidHandle = kInvalidPlatformFile;

SyncSocket::SyncSocket() = default;

SyncSocket::SyncSocket(Handle handle) : handle_(handle) {}

SyncSocket::SyncSocket(ScopedHandle handle) : handle_(std::move(handle)) {}

SyncSocket::~SyncSocket() = default;

SyncSocket::ScopedHandle SyncSocket::Take() {
  return std::move(handle_);
}

CancelableSyncSocket::CancelableSyncSocket() = default;

CancelableSyncSocket::CancelableSyncSocket(Handle handle)
    : SyncSocket(handle) {}

CancelableSyncSocket::CancelableSyncSocket(ScopedHandle handle)
    : SyncSocket(std::move(handle)) {}

}  // namespace base

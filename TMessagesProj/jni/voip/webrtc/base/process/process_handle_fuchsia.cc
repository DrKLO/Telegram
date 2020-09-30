// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/process_handle.h"

#include <zircon/process.h>
#include <zircon/status.h>
#include <zircon/syscalls.h>

#include "base/logging.h"

namespace base {

ProcessId GetCurrentProcId() {
  return GetProcId(GetCurrentProcessHandle());
}

ProcessHandle GetCurrentProcessHandle() {
  // Note that zx_process_self() returns a real handle, and ownership is not
  // transferred to the caller (i.e. this should never be closed).
  return zx_process_self();
}

ProcessId GetProcId(ProcessHandle process) {
  zx_info_handle_basic_t basic;
  zx_status_t status = zx_object_get_info(process, ZX_INFO_HANDLE_BASIC, &basic,
                                          sizeof(basic), nullptr, nullptr);
  if (status != ZX_OK) {
    DLOG(ERROR) << "zx_object_get_info failed: "
                << zx_status_get_string(status);
    return ZX_KOID_INVALID;
  }
  return basic.koid;
}

}  // namespace base

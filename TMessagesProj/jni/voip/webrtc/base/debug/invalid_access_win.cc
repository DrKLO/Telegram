// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/debug/invalid_access_win.h"

#include <stdlib.h>
#include <windows.h>

#include "base/logging.h"
#include "base/win/windows_version.h"

namespace base {
namespace debug {
namespace win {

namespace {

void CreateSyntheticHeapCorruption() {
  EXCEPTION_RECORD record = {};
  record.ExceptionCode = STATUS_HEAP_CORRUPTION;
  RaiseFailFastException(&record, nullptr,
                         FAIL_FAST_GENERATE_EXCEPTION_ADDRESS);
}

}  // namespace

void TerminateWithHeapCorruption() {
  __try {
    // Pre-Windows 10, it's hard to trigger a heap corruption fast fail, so
    // artificially create one instead.
    if (base::win::GetVersion() < base::win::Version::WIN10)
      CreateSyntheticHeapCorruption();
    HANDLE heap = ::HeapCreate(0, 0, 0);
    CHECK(heap);
    CHECK(HeapSetInformation(heap, HeapEnableTerminationOnCorruption, nullptr,
                             0));
    void* addr = ::HeapAlloc(heap, 0, 0x1000);
    CHECK(addr);
    // Corrupt heap header.
    char* addr_mutable = reinterpret_cast<char*>(addr);
    memset(addr_mutable - sizeof(addr), 0xCC, sizeof(addr));

    HeapFree(heap, 0, addr);
    HeapDestroy(heap);
  } __except (EXCEPTION_EXECUTE_HANDLER) {
    // Heap corruption exception should never be caught.
    CHECK(false);
  }
  // Should never reach here.
  abort();
}

}  // namespace win
}  // namespace debug
}  // namespace base

/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/win/scoped_com_initializer.h"

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

ScopedCOMInitializer::ScopedCOMInitializer() {
  RTC_DLOG(INFO) << "Single-Threaded Apartment (STA) COM thread";
  Initialize(COINIT_APARTMENTTHREADED);
}

// Constructor for MTA initialization.
ScopedCOMInitializer::ScopedCOMInitializer(SelectMTA mta) {
  RTC_DLOG(INFO) << "Multi-Threaded Apartment (MTA) COM thread";
  Initialize(COINIT_MULTITHREADED);
}

ScopedCOMInitializer::~ScopedCOMInitializer() {
  if (Succeeded()) {
    CoUninitialize();
  }
}

void ScopedCOMInitializer::Initialize(COINIT init) {
  // Initializes the COM library for use by the calling thread, sets the
  // thread's concurrency model, and creates a new apartment for the thread
  // if one is required. CoInitializeEx must be called at least once, and is
  // usually called only once, for each thread that uses the COM library.
  hr_ = CoInitializeEx(NULL, init);
  RTC_CHECK_NE(RPC_E_CHANGED_MODE, hr_)
      << "Invalid COM thread model change (MTA->STA)";
  // Multiple calls to CoInitializeEx by the same thread are allowed as long
  // as they pass the same concurrency flag, but subsequent valid calls
  // return S_FALSE. To close the COM library gracefully on a thread, each
  // successful call to CoInitializeEx, including any call that returns
  // S_FALSE, must be balanced by a corresponding call to CoUninitialize.
  if (hr_ == S_OK) {
    RTC_DLOG(INFO)
        << "The COM library was initialized successfully on this thread";
  } else if (hr_ == S_FALSE) {
    RTC_DLOG(WARNING)
        << "The COM library is already initialized on this thread";
  }
}

}  // namespace webrtc

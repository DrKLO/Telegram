/*
 *  Copyright (c) 2010 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_device/linux/latebindingsymboltable_linux.h"

#include "absl/strings/string_view.h"
#include "rtc_base/logging.h"

#ifdef WEBRTC_LINUX
#include <dlfcn.h>
#endif

namespace webrtc {
namespace adm_linux {

inline static const char* GetDllError() {
#ifdef WEBRTC_LINUX
  char* err = dlerror();
  if (err) {
    return err;
  } else {
    return "No error";
  }
#else
#error Not implemented
#endif
}

DllHandle InternalLoadDll(absl::string_view dll_name) {
#ifdef WEBRTC_LINUX
  DllHandle handle = dlopen(std::string(dll_name).c_str(), RTLD_NOW);
#else
#error Not implemented
#endif
  if (handle == kInvalidDllHandle) {
    RTC_LOG(LS_WARNING) << "Can't load " << dll_name << " : " << GetDllError();
  }
  return handle;
}

void InternalUnloadDll(DllHandle handle) {
#ifdef WEBRTC_LINUX
// TODO(pbos): Remove this dlclose() exclusion when leaks and suppressions from
// here are gone (or AddressSanitizer can display them properly).
//
// Skip dlclose() on AddressSanitizer as leaks including this module in the
// stack trace gets displayed as <unknown module> instead of the actual library
// -> it can not be suppressed.
// https://code.google.com/p/address-sanitizer/issues/detail?id=89
#if !defined(ADDRESS_SANITIZER)
  if (dlclose(handle) != 0) {
    RTC_LOG(LS_ERROR) << GetDllError();
  }
#endif  // !defined(ADDRESS_SANITIZER)
#else
#error Not implemented
#endif
}

static bool LoadSymbol(DllHandle handle,
                       absl::string_view symbol_name,
                       void** symbol) {
#ifdef WEBRTC_LINUX
  *symbol = dlsym(handle, std::string(symbol_name).c_str());
  char* err = dlerror();
  if (err) {
    RTC_LOG(LS_ERROR) << "Error loading symbol " << symbol_name << " : " << err;
    return false;
  } else if (!*symbol) {
    RTC_LOG(LS_ERROR) << "Symbol " << symbol_name << " is NULL";
    return false;
  }
  return true;
#else
#error Not implemented
#endif
}

// This routine MUST assign SOME value for every symbol, even if that value is
// NULL, or else some symbols may be left with uninitialized data that the
// caller may later interpret as a valid address.
bool InternalLoadSymbols(DllHandle handle,
                         int num_symbols,
                         const char* const symbol_names[],
                         void* symbols[]) {
#ifdef WEBRTC_LINUX
  // Clear any old errors.
  dlerror();
#endif
  for (int i = 0; i < num_symbols; ++i) {
    if (!LoadSymbol(handle, symbol_names[i], &symbols[i])) {
      return false;
    }
  }
  return true;
}

}  // namespace adm_linux
}  // namespace webrtc

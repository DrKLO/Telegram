/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/shared_memory.h"

namespace webrtc {

#if defined(WEBRTC_WIN)
const SharedMemory::Handle SharedMemory::kInvalidHandle = NULL;
#else
const SharedMemory::Handle SharedMemory::kInvalidHandle = -1;
#endif

SharedMemory::SharedMemory(void* data, size_t size, Handle handle, int id)
    : data_(data), size_(size), handle_(handle), id_(id) {}

}  // namespace webrtc

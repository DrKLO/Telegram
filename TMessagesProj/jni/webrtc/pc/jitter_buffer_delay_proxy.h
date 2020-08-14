/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_JITTER_BUFFER_DELAY_PROXY_H_
#define PC_JITTER_BUFFER_DELAY_PROXY_H_

#include <stdint.h>

#include "api/proxy.h"
#include "media/base/delayable.h"
#include "pc/jitter_buffer_delay_interface.h"

namespace webrtc {

BEGIN_PROXY_MAP(JitterBufferDelay)
PROXY_SIGNALING_THREAD_DESTRUCTOR()
PROXY_METHOD2(void, OnStart, cricket::Delayable*, uint32_t)
PROXY_METHOD0(void, OnStop)
PROXY_WORKER_METHOD1(void, Set, absl::optional<double>)
END_PROXY_MAP()

}  // namespace webrtc

#endif  // PC_JITTER_BUFFER_DELAY_PROXY_H_

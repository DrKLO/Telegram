/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/owned_factory_and_threads.h"

#include "sdk/android/src/jni/jni_helpers.h"

namespace webrtc {
namespace jni {

OwnedFactoryAndThreads::OwnedFactoryAndThreads(
    std::unique_ptr<rtc::SocketFactory> socket_factory,
    std::unique_ptr<rtc::Thread> network_thread,
    std::unique_ptr<rtc::Thread> worker_thread,
    std::unique_ptr<rtc::Thread> signaling_thread,
    const rtc::scoped_refptr<PeerConnectionFactoryInterface>& factory)
    : socket_factory_(std::move(socket_factory)),
      network_thread_(std::move(network_thread)),
      worker_thread_(std::move(worker_thread)),
      signaling_thread_(std::move(signaling_thread)),
      factory_(factory) {}

}  // namespace jni
}  // namespace webrtc

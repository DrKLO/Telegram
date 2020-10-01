/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "sdk/android/native_api/peerconnection/peer_connection_factory.h"

#include <jni.h>

#include <memory>
#include <utility>

#include "sdk/android/src/jni/pc/peer_connection_factory.h"

namespace webrtc {

jobject NativeToJavaPeerConnectionFactory(
    JNIEnv* jni,
    rtc::scoped_refptr<webrtc::PeerConnectionFactoryInterface> pcf,
    std::unique_ptr<rtc::Thread> network_thread,
    std::unique_ptr<rtc::Thread> worker_thread,
    std::unique_ptr<rtc::Thread> signaling_thread) {
  return webrtc::jni::NativeToJavaPeerConnectionFactory(
      jni, pcf, std::move(network_thread), std::move(worker_thread),
      std::move(signaling_thread));
}

}  // namespace webrtc

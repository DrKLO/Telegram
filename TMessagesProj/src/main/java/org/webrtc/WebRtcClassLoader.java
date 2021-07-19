/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

/**
 * This class provides a ClassLoader that is capable of loading WebRTC Java classes regardless of
 * what thread it's called from. Such a ClassLoader is needed for the few cases where the JNI
 * mechanism is unable to automatically determine the appropriate ClassLoader instance.
 */
class WebRtcClassLoader {
  @CalledByNative
  static Object getClassLoader() {
    Object loader = WebRtcClassLoader.class.getClassLoader();
    if (loader == null) {
      throw new RuntimeException("Failed to get WebRTC class loader.");
    }
    return loader;
  }
}

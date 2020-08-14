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

import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * This class is only used from jni_helper.cc to give some Java functionality that were not possible
 * to generate in other ways due to bugs.webrtc.org/8606 and bugs.webrtc.org/8632.
 */
class JniHelper {
  // TODO(bugs.webrtc.org/8632): Remove.
  @CalledByNative
  static byte[] getStringBytes(String s) {
    try {
      return s.getBytes("ISO-8859-1");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("ISO-8859-1 is unsupported");
    }
  }

  // TODO(bugs.webrtc.org/8632): Remove.
  @CalledByNative
  static Object getStringClass() {
    return String.class;
  }

  // TODO(bugs.webrtc.org/8606): Remove.
  @CalledByNative
  static Object getKey(Map.Entry entry) {
    return entry.getKey();
  }

  // TODO(bugs.webrtc.org/8606): Remove.
  @CalledByNative
  static Object getValue(Map.Entry entry) {
    return entry.getValue();
  }
}

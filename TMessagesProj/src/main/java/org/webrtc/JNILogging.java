/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import org.webrtc.CalledByNative;
import org.webrtc.Loggable;
import org.webrtc.Logging.Severity;

class JNILogging {
  private final Loggable loggable;

  public JNILogging(Loggable loggable) {
    this.loggable = loggable;
  }

  @CalledByNative
  public void logToInjectable(String message, Integer severity, String tag) {
    loggable.onLogMessage(message, Severity.values()[severity], tag);
  }
}

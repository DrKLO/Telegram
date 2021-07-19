/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

public class CallSessionFileRotatingLogSink {
  private long nativeSink;

  public static byte[] getLogData(String dirPath) {
    if (dirPath == null) {
      throw new IllegalArgumentException("dirPath may not be null.");
    }
    return nativeGetLogData(dirPath);
  }

  public CallSessionFileRotatingLogSink(
      String dirPath, int maxFileSize, Logging.Severity severity) {
    if (dirPath == null) {
      throw new IllegalArgumentException("dirPath may not be null.");
    }
    nativeSink = nativeAddSink(dirPath, maxFileSize, severity.ordinal());
  }

  public void dispose() {
    if (nativeSink != 0) {
      nativeDeleteSink(nativeSink);
      nativeSink = 0;
    }
  }

  private static native long nativeAddSink(String dirPath, int maxFileSize, int severity);
  private static native void nativeDeleteSink(long sink);
  private static native byte[] nativeGetLogData(String dirPath);
}

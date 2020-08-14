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

/** Java wrapper for a C++ TurnCustomizer. */
public class TurnCustomizer {
  private long nativeTurnCustomizer;

  public TurnCustomizer(long nativeTurnCustomizer) {
    this.nativeTurnCustomizer = nativeTurnCustomizer;
  }

  public void dispose() {
    checkTurnCustomizerExists();
    nativeFreeTurnCustomizer(nativeTurnCustomizer);
    nativeTurnCustomizer = 0;
  }

  private static native void nativeFreeTurnCustomizer(long turnCustomizer);

  /** Return a pointer to webrtc::TurnCustomizer. */
  @CalledByNative
  long getNativeTurnCustomizer() {
    checkTurnCustomizerExists();
    return nativeTurnCustomizer;
  }

  private void checkTurnCustomizerExists() {
    if (nativeTurnCustomizer == 0) {
      throw new IllegalStateException("TurnCustomizer has been disposed.");
    }
  }
}

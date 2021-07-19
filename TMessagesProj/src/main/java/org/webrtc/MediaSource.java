/*
 *  Copyright 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

/** Java wrapper for a C++ MediaSourceInterface. */
public class MediaSource {
  /** Tracks MediaSourceInterface.SourceState */
  public enum State {
    INITIALIZING,
    LIVE,
    ENDED,
    MUTED;

    @CalledByNative("State")
    static State fromNativeIndex(int nativeIndex) {
      return values()[nativeIndex];
    }
  }

  private final RefCountDelegate refCountDelegate;
  private long nativeSource;

  public MediaSource(long nativeSource) {
    refCountDelegate = new RefCountDelegate(() -> JniCommon.nativeReleaseRef(nativeSource));
    this.nativeSource = nativeSource;
  }

  public State state() {
    checkMediaSourceExists();
    return nativeGetState(nativeSource);
  }

  public void dispose() {
    checkMediaSourceExists();
    refCountDelegate.release();
    nativeSource = 0;
  }

  /** Returns a pointer to webrtc::MediaSourceInterface. */
  protected long getNativeMediaSource() {
    checkMediaSourceExists();
    return nativeSource;
  }

  /**
   * Runs code in {@code runnable} holding a reference to the media source. If the object has
   * already been released, does nothing.
   */
  void runWithReference(Runnable runnable) {
    if (refCountDelegate.safeRetain()) {
      try {
        runnable.run();
      } finally {
        refCountDelegate.release();
      }
    }
  }

  private void checkMediaSourceExists() {
    if (nativeSource == 0) {
      throw new IllegalStateException("MediaSource has been disposed.");
    }
  }

  private static native State nativeGetState(long pointer);
}

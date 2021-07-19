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

/** Java wrapper for a C++ AudioTrackInterface */
public class AudioTrack extends MediaStreamTrack {
  public AudioTrack(long nativeTrack) {
    super(nativeTrack);
  }

  /** Sets the volume for the underlying MediaSource. Volume is a gain value in the range
   *  0 to 10.
   */
  public void setVolume(double volume) {
    nativeSetVolume(getNativeAudioTrack(), volume);
  }

  /** Returns a pointer to webrtc::AudioTrackInterface. */
  long getNativeAudioTrack() {
    return getNativeMediaStreamTrack();
  }

  private static native void nativeSetVolume(long track, double volume);
}

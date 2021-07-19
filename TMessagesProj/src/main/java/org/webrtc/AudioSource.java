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

/**
 * Java wrapper for a C++ AudioSourceInterface.  Used as the source for one or
 * more {@code AudioTrack} objects.
 */
public class AudioSource extends MediaSource {
  public AudioSource(long nativeSource) {
    super(nativeSource);
  }

  /** Returns a pointer to webrtc::AudioSourceInterface. */
  long getNativeAudioSource() {
    return getNativeMediaSource();
  }
}

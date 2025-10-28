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

import java.util.IdentityHashMap;

/** Java version of VideoTrackInterface. */
public class VideoTrack extends MediaStreamTrack {
  private final IdentityHashMap<VideoSink, Long> sinks = new IdentityHashMap<VideoSink, Long>();

  public VideoTrack(long nativeTrack) {
    super(nativeTrack);
  }

  /**
   * Adds a VideoSink to the track.
   *
   * A track can have any number of VideoSinks. VideoSinks will replace
   * renderers. However, converting old style texture frames will involve costly
   * conversion to I420 so it is not recommended to upgrade before all your
   * sources produce VideoFrames.
   */
  public void addSink(VideoSink sink) {
    if (sink == null) {
      throw new IllegalArgumentException("The VideoSink is not allowed to be null");
    }
    // We allow calling addSink() with the same sink multiple times. This is similar to the C++
    // VideoTrack::AddOrUpdateSink().
    if (!sinks.containsKey(sink)) {
      final long nativeSink = nativeWrapSink(sink);
      sinks.put(sink, nativeSink);
      nativeAddSink(getNativeMediaStreamTrack(), nativeSink);
    }
  }

  /**
   * Removes a VideoSink from the track.
   *
   * If the VideoSink was not attached to the track, this is a no-op.
   */
  public void removeSink(VideoSink sink) {
    final Long nativeSink = sinks.remove(sink);
    if (nativeSink != null) {
      nativeRemoveSink(getNativeMediaStreamTrack(), nativeSink);
      nativeFreeSink(nativeSink);
    }
  }

  @Override
  public void dispose() {
    for (long nativeSink : sinks.values()) {
      nativeRemoveSink(getNativeMediaStreamTrack(), nativeSink);
      nativeFreeSink(nativeSink);
    }
    sinks.clear();
    super.dispose();
  }

  /** Returns a pointer to webrtc::VideoTrackInterface. */
  public long getNativeVideoTrack() {
    return getNativeMediaStreamTrack();
  }

  private static native void nativeAddSink(long track, long nativeSink);
  private static native void nativeRemoveSink(long track, long nativeSink);
  private static native long nativeWrapSink(VideoSink sink);
  private static native void nativeFreeSink(long sink);
}

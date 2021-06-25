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
 * Java version of rtc::VideoSinkInterface.
 */
public interface VideoSink {
  /**
   * Implementations should call frame.retain() if they need to hold a reference to the frame after
   * this function returns. Each call to retain() should be followed by a call to frame.release()
   * when the reference is no longer needed.
   */
  @CalledByNative void onFrame(VideoFrame frame);

  default void setParentSink(VideoSink parent) {

  }
}

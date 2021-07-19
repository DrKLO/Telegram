/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

/**
 * Interface for observering a capturer. Passed to {@link VideoCapturer#initialize}. Provided by
 * {@link VideoSource#getCapturerObserver}.
 *
 * All callbacks must be executed on a single thread.
 */
public interface CapturerObserver {
  /** Notify if the capturer have been started successfully or not. */
  void onCapturerStarted(boolean success);
  /** Notify that the capturer has been stopped. */
  void onCapturerStopped();

  /** Delivers a captured frame. */
  void onFrameCaptured(VideoFrame frame);
}

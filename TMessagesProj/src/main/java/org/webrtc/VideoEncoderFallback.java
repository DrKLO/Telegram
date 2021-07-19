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
 * A combined video encoder that falls back on a secondary encoder if the primary encoder fails.
 */
public class VideoEncoderFallback extends WrappedNativeVideoEncoder {
  private final VideoEncoder fallback;
  private final VideoEncoder primary;

  public VideoEncoderFallback(VideoEncoder fallback, VideoEncoder primary) {
    this.fallback = fallback;
    this.primary = primary;
  }

  @Override
  public long createNativeVideoEncoder() {
    return nativeCreateEncoder(fallback, primary);
  }

  @Override
  public boolean isHardwareEncoder() {
    return primary.isHardwareEncoder();
  }

  private static native long nativeCreateEncoder(VideoEncoder fallback, VideoEncoder primary);
}

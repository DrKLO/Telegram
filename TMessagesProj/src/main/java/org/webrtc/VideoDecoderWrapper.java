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

import org.webrtc.VideoDecoder;

/**
 * This class contains the Java glue code for JNI generation of VideoDecoder.
 */
class VideoDecoderWrapper {
  @CalledByNative
  static VideoDecoder.Callback createDecoderCallback(final long nativeDecoder) {
    return (VideoFrame frame, Integer decodeTimeMs,
               Integer qp) -> nativeOnDecodedFrame(nativeDecoder, frame, decodeTimeMs, qp);
  }

  private static native void nativeOnDecodedFrame(
      long nativeVideoDecoderWrapper, VideoFrame frame, Integer decodeTimeMs, Integer qp);
}

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

import androidx.annotation.Nullable;

/** Factory for creating VideoDecoders. */
public interface VideoDecoderFactory {
  /**
   * Creates a VideoDecoder for the given codec. Supports the same codecs supported by
   * VideoEncoderFactory.
   */
  @Nullable @CalledByNative VideoDecoder createDecoder(VideoCodecInfo info);

  /**
   * Enumerates the list of supported video codecs.
   */
  @CalledByNative
  default VideoCodecInfo[] getSupportedCodecs() {
    return new VideoCodecInfo[0];
  }
}

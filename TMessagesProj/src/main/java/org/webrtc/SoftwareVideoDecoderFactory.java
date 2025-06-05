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
import java.util.List;

public class SoftwareVideoDecoderFactory implements VideoDecoderFactory {
  private static final String TAG = "SoftwareVideoDecoderFactory";

  private final long nativeFactory;

  public SoftwareVideoDecoderFactory() {
    this.nativeFactory = nativeCreateFactory();
  }

  @Nullable
  @Override
  public VideoDecoder createDecoder(VideoCodecInfo info) {
    if (!nativeIsSupported(nativeFactory, info)) {
      Logging.w(TAG, "Trying to create decoder for unsupported format. " + info);
      return null;
    }
    return new WrappedNativeVideoDecoder() {
      @Override
      public long createNative(long webrtcEnvRef) {
        return nativeCreate(nativeFactory, webrtcEnvRef, info);
      }
    };
  }

  @Override
  public VideoCodecInfo[] getSupportedCodecs() {
    return nativeGetSupportedCodecs(nativeFactory).toArray(new VideoCodecInfo[0]);
  }

  private static native long nativeCreateFactory();

  private static native boolean nativeIsSupported(long factory, VideoCodecInfo info);

  private static native long nativeCreate(
      long factory, long webrtcEnvRef, VideoCodecInfo info);

  private static native List<VideoCodecInfo> nativeGetSupportedCodecs(long factory);
}

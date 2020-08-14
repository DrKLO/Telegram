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
 * This class creates a native {@code webrtc::AudioEncoderFactory} with the builtin audio encoders.
 */
public class BuiltinAudioEncoderFactoryFactory implements AudioEncoderFactoryFactory {
  @Override
  public long createNativeAudioEncoderFactory() {
    return nativeCreateBuiltinAudioEncoderFactory();
  }

  private static native long nativeCreateBuiltinAudioEncoderFactory();
}

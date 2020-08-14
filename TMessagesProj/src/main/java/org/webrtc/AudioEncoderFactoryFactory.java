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
 * Implementations of this interface can create a native {@code webrtc::AudioEncoderFactory}.
 */
public interface AudioEncoderFactoryFactory {
  /**
   * Returns a pointer to a {@code webrtc::AudioEncoderFactory}. The caller takes ownership.
   */
  long createNativeAudioEncoderFactory();
}

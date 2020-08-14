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
 * The FrameEncryptor interface allows Java API users to provide a pointer to
 * their native implementation of the FrameEncryptorInterface.
 * FrameEncyptors are extremely performance sensitive as they must process all
 * outgoing video and audio frames. Due to this reason they should always be
 * backed by a native implementation.
 * @note Not ready for production use.
 */
public interface FrameEncryptor {
  /**
   * @return A FrameEncryptorInterface pointer.
   */
  long getNativeFrameEncryptor();
}

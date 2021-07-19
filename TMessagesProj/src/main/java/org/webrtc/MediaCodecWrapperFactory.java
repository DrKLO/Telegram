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

import java.io.IOException;

interface MediaCodecWrapperFactory {
  /**
   * Creates a new {@link MediaCodecWrapper} by codec name.
   *
   * <p>For additional information see {@link android.media.MediaCodec#createByCodecName}.
   */
  MediaCodecWrapper createByCodecName(String name) throws IOException;
}

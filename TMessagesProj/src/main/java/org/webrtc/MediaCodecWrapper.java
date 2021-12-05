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

import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Bundle;
import android.view.Surface;
import java.nio.ByteBuffer;

/**
 * Subset of methods defined in {@link android.media.MediaCodec} needed by
 * {@link HardwareVideoEncoder} and {@link AndroidVideoDecoder}. This interface
 * exists to allow mocking and using a fake implementation in tests.
 */
interface MediaCodecWrapper {
  void configure(MediaFormat format, Surface surface, MediaCrypto crypto, int flags);

  void start();

  void flush();

  void stop();

  void release();

  int dequeueInputBuffer(long timeoutUs);

  void queueInputBuffer(int index, int offset, int size, long presentationTimeUs, int flags);

  int dequeueOutputBuffer(MediaCodec.BufferInfo info, long timeoutUs);

  void releaseOutputBuffer(int index, boolean render);

  MediaFormat getOutputFormat();

  ByteBuffer[] getInputBuffers();

  ByteBuffer[] getOutputBuffers();

  Surface createInputSurface();

  void setParameters(Bundle params);
}

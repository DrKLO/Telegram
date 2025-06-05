/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.video.spherical;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;

/** A {@link Renderer} that parses the camera motion track. */
public final class CameraMotionRenderer extends BaseRenderer {

  private static final String TAG = "CameraMotionRenderer";
  // The amount of time to read samples ahead of the current time.
  private static final int SAMPLE_WINDOW_DURATION_US = 100_000;

  private final DecoderInputBuffer buffer;
  private final ParsableByteArray scratch;

  private long offsetUs;
  @Nullable private CameraMotionListener listener;
  private long lastTimestampUs;

  public CameraMotionRenderer() {
    super(C.TRACK_TYPE_CAMERA_MOTION);
    buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    scratch = new ParsableByteArray();
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public @Capabilities int supportsFormat(Format format) {
    return MimeTypes.APPLICATION_CAMERA_MOTION.equals(format.sampleMimeType)
        ? RendererCapabilities.create(C.FORMAT_HANDLED)
        : RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
  }

  @Override
  public void handleMessage(@MessageType int messageType, @Nullable Object message)
      throws ExoPlaybackException {
    if (messageType == MSG_SET_CAMERA_MOTION_LISTENER) {
      listener = (CameraMotionListener) message;
    } else {
      super.handleMessage(messageType, message);
    }
  }

  @Override
  protected void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs) {
    this.offsetUs = offsetUs;
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) {
    lastTimestampUs = Long.MIN_VALUE;
    resetListener();
  }

  @Override
  protected void onDisabled() {
    resetListener();
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) {
    // Keep reading available samples as long as the sample time is not too far into the future.
    while (!hasReadStreamToEnd() && lastTimestampUs < positionUs + SAMPLE_WINDOW_DURATION_US) {
      buffer.clear();
      FormatHolder formatHolder = getFormatHolder();
      @ReadDataResult int result = readSource(formatHolder, buffer, /* readFlags= */ 0);
      if (result != C.RESULT_BUFFER_READ || buffer.isEndOfStream()) {
        return;
      }

      lastTimestampUs = buffer.timeUs;
      if (listener == null || buffer.isDecodeOnly()) {
        continue;
      }

      buffer.flip();
      @Nullable float[] rotation = parseMetadata(Util.castNonNull(buffer.data));
      if (rotation == null) {
        continue;
      }

      Util.castNonNull(listener).onCameraMotion(lastTimestampUs - offsetUs, rotation);
    }
  }

  @Override
  public boolean isEnded() {
    return hasReadStreamToEnd();
  }

  @Override
  public boolean isReady() {
    return true;
  }

  @Nullable
  private float[] parseMetadata(ByteBuffer data) {
    if (data.remaining() != 16) {
      return null;
    }
    scratch.reset(data.array(), data.limit());
    scratch.setPosition(data.arrayOffset() + 4); // skip reserved bytes too.
    float[] result = new float[3];
    for (int i = 0; i < 3; i++) {
      result[i] = Float.intBitsToFloat(scratch.readLittleEndianInt());
    }
    return result;
  }

  private void resetListener() {
    if (listener != null) {
      listener.onCameraMotionReset();
    }
  }
}

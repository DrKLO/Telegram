/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.telegram.messenger.exoplayer2.metadata;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import org.telegram.messenger.exoplayer2.BaseRenderer;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.ExoPlaybackException;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.FormatHolder;
import org.telegram.messenger.exoplayer2.decoder.DecoderInputBuffer;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.nio.ByteBuffer;

/**
 * A renderer for metadata.
 */
public final class MetadataRenderer extends BaseRenderer implements Callback {

  /**
   * Receives output from a {@link MetadataRenderer}.
   */
  public interface Output {

    /**
     * Called each time there is a metadata associated with current playback time.
     *
     * @param metadata The metadata.
     */
    void onMetadata(Metadata metadata);

  }

  private static final int MSG_INVOKE_RENDERER = 0;

  private final MetadataDecoder metadataDecoder;
  private final Output output;
  private final Handler outputHandler;
  private final FormatHolder formatHolder;
  private final DecoderInputBuffer buffer;

  private boolean inputStreamEnded;
  private long pendingMetadataTimestamp;
  private Metadata pendingMetadata;

  /**
   * @param output The output.
   * @param outputLooper The looper associated with the thread on which the output should be called.
   *     If the output makes use of standard Android UI components, then this should normally be the
   *     looper associated with the application's main thread, which can be obtained using
   *     {@link android.app.Activity#getMainLooper()}. Null may be passed if the output should be
   *     called directly on the player's internal rendering thread.
   * @param metadataDecoder A decoder for the metadata.
   */
  public MetadataRenderer(Output output, Looper outputLooper, MetadataDecoder metadataDecoder) {
    super(C.TRACK_TYPE_METADATA);
    this.output = Assertions.checkNotNull(output);
    this.outputHandler = outputLooper == null ? null : new Handler(outputLooper, this);
    this.metadataDecoder = Assertions.checkNotNull(metadataDecoder);
    formatHolder = new FormatHolder();
    buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
  }

  @Override
  public int supportsFormat(Format format) {
    return metadataDecoder.canDecode(format.sampleMimeType) ? FORMAT_HANDLED
        : FORMAT_UNSUPPORTED_TYPE;
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) {
    pendingMetadata = null;
    inputStreamEnded = false;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (!inputStreamEnded && pendingMetadata == null) {
      buffer.clear();
      int result = readSource(formatHolder, buffer);
      if (result == C.RESULT_BUFFER_READ) {
        if (buffer.isEndOfStream()) {
          inputStreamEnded = true;
        } else {
          pendingMetadataTimestamp = buffer.timeUs;
          try {
            buffer.flip();
            ByteBuffer bufferData = buffer.data;
            pendingMetadata = metadataDecoder.decode(bufferData.array(), bufferData.limit());
          } catch (MetadataDecoderException e) {
            throw ExoPlaybackException.createForRenderer(e, getIndex());
          }
        }
      }
    }

    if (pendingMetadata != null && pendingMetadataTimestamp <= positionUs) {
      invokeRenderer(pendingMetadata);
      pendingMetadata = null;
    }
  }

  @Override
  protected void onDisabled() {
    pendingMetadata = null;
    super.onDisabled();
  }

  @Override
  public boolean isEnded() {
    return inputStreamEnded;
  }

  @Override
  public boolean isReady() {
    return true;
  }

  private void invokeRenderer(Metadata metadata) {
    if (outputHandler != null) {
      outputHandler.obtainMessage(MSG_INVOKE_RENDERER, metadata).sendToTarget();
    } else {
      invokeRendererInternal(metadata);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_INVOKE_RENDERER:
        invokeRendererInternal((Metadata) msg.obj);
        return true;
    }
    return false;
  }

  private void invokeRendererInternal(Metadata metadata) {
    output.onMetadata(metadata);
  }

}

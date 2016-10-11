/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.metadata;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import org.telegram.messenger.exoplayer.ExoPlaybackException;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.MediaFormatHolder;
import org.telegram.messenger.exoplayer.SampleHolder;
import org.telegram.messenger.exoplayer.SampleSource;
import org.telegram.messenger.exoplayer.SampleSourceTrackRenderer;
import org.telegram.messenger.exoplayer.TrackRenderer;
import org.telegram.messenger.exoplayer.util.Assertions;
import java.io.IOException;

/**
 * A {@link TrackRenderer} for metadata embedded in a media stream.
 *
 * @param <T> The type of the metadata.
 */
public final class MetadataTrackRenderer<T> extends SampleSourceTrackRenderer implements Callback {

  /**
   * An interface for components that process metadata.
   *
   * @param <T> The type of the metadata.
   */
  public interface MetadataRenderer<T> {

    /**
     * Invoked each time there is a metadata associated with current playback time.
     *
     * @param metadata The metadata to process.
     */
    void onMetadata(T metadata);

  }

  private static final int MSG_INVOKE_RENDERER = 0;

  private final MetadataParser<T> metadataParser;
  private final MetadataRenderer<T> metadataRenderer;
  private final Handler metadataHandler;
  private final MediaFormatHolder formatHolder;
  private final SampleHolder sampleHolder;

  private boolean inputStreamEnded;
  private long pendingMetadataTimestamp;
  private T pendingMetadata;

  /**
   * @param source A source from which samples containing metadata can be read.
   * @param metadataParser A parser for parsing the metadata.
   * @param metadataRenderer The metadata renderer to receive the parsed metadata.
   * @param metadataRendererLooper The looper associated with the thread on which metadataRenderer
   *     should be invoked. If the renderer makes use of standard Android UI components, then this
   *     should normally be the looper associated with the applications' main thread, which can be
   *     obtained using {@link android.app.Activity#getMainLooper()}. Null may be passed if the
   *     renderer should be invoked directly on the player's internal rendering thread.
   */
  public MetadataTrackRenderer(SampleSource source, MetadataParser<T> metadataParser,
      MetadataRenderer<T> metadataRenderer, Looper metadataRendererLooper) {
    super(source);
    this.metadataParser = Assertions.checkNotNull(metadataParser);
    this.metadataRenderer = Assertions.checkNotNull(metadataRenderer);
    this.metadataHandler = metadataRendererLooper == null ? null
        : new Handler(metadataRendererLooper, this);
    formatHolder = new MediaFormatHolder();
    sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_NORMAL);
  }

  @Override
  protected boolean handlesTrack(MediaFormat mediaFormat) {
    return metadataParser.canParse(mediaFormat.mimeType);
  }

  @Override
  protected void onDiscontinuity(long positionUs) {
    pendingMetadata = null;
    inputStreamEnded = false;
  }

  @Override
  protected void doSomeWork(long positionUs, long elapsedRealtimeUs, boolean sourceIsReady)
      throws ExoPlaybackException {
    if (!inputStreamEnded && pendingMetadata == null) {
      sampleHolder.clearData();
      int result = readSource(positionUs, formatHolder, sampleHolder);
      if (result == SampleSource.SAMPLE_READ) {
        pendingMetadataTimestamp = sampleHolder.timeUs;
        try {
          pendingMetadata = metadataParser.parse(sampleHolder.data.array(), sampleHolder.size);
        } catch (IOException e) {
          throw new ExoPlaybackException(e);
        }
      } else if (result == SampleSource.END_OF_STREAM) {
        inputStreamEnded = true;
      }
    }

    if (pendingMetadata != null && pendingMetadataTimestamp <= positionUs) {
      invokeRenderer(pendingMetadata);
      pendingMetadata = null;
    }
  }

  @Override
  protected void onDisabled() throws ExoPlaybackException {
    pendingMetadata = null;
    super.onDisabled();
  }

  @Override
  protected long getBufferedPositionUs() {
    return TrackRenderer.END_OF_TRACK_US;
  }

  @Override
  protected boolean isEnded() {
    return inputStreamEnded;
  }

  @Override
  protected boolean isReady() {
    return true;
  }

  private void invokeRenderer(T metadata) {
    if (metadataHandler != null) {
      metadataHandler.obtainMessage(MSG_INVOKE_RENDERER, metadata).sendToTarget();
    } else {
      invokeRendererInternal(metadata);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_INVOKE_RENDERER:
        invokeRendererInternal((T) msg.obj);
        return true;
    }
    return false;
  }

  private void invokeRendererInternal(T metadata) {
    metadataRenderer.onMetadata(metadata);
  }

}

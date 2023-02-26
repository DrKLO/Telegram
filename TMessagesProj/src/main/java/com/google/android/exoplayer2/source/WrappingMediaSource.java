/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * An abstract {@link MediaSource} wrapping a single child {@link MediaSource}.
 *
 * <p>The implementation may want to override the following methods as needed:
 *
 * <ul>
 *   <li>{@link #getMediaItem()}: Amend the {@link MediaItem} for this media source. This is only
 *       used before the child source is prepared.
 *   <li>{@link #onChildSourceInfoRefreshed(Timeline)}: Called whenever the child source's {@link
 *       Timeline} changed. This {@link Timeline} can be amended if needed, for example using {@link
 *       ForwardingTimeline}. The {@link Timeline} for the wrapping source needs to be published
 *       with {@link #refreshSourceInfo(Timeline)}.
 *   <li>{@link #createPeriod}/{@link #releasePeriod}: These methods create and release {@link
 *       MediaPeriod} instances. They typically forward to the wrapped media source and optionally
 *       wrap the returned {@link MediaPeriod}.
 * </ul>
 *
 * <p>Other methods like {@link #prepareSourceInternal}, {@link #enableInternal}, {@link
 * #disableInternal} or {@link #releaseSourceInternal} only need to be overwritten if required for
 * resource management.
 */
public abstract class WrappingMediaSource extends CompositeMediaSource<Void> {

  private static final Void CHILD_SOURCE_ID = null;

  /** The wrapped child {@link MediaSource}. */
  protected final MediaSource mediaSource;

  /**
   * Creates the wrapping {@link MediaSource}.
   *
   * @param mediaSource The wrapped child {@link MediaSource}.
   */
  protected WrappingMediaSource(MediaSource mediaSource) {
    this.mediaSource = mediaSource;
  }

  @Override
  protected final void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    super.prepareSourceInternal(mediaTransferListener);
    prepareSourceInternal();
  }

  /**
   * Starts source preparation and enables the source, see {@link #prepareSource(MediaSourceCaller,
   * TransferListener, PlayerId)}. This method is called at most once until the next call to {@link
   * #releaseSourceInternal()}.
   */
  protected void prepareSourceInternal() {
    prepareChildSource();
  }

  @Nullable
  @Override
  public Timeline getInitialTimeline() {
    return mediaSource.getInitialTimeline();
  }

  @Override
  public boolean isSingleWindow() {
    return mediaSource.isSingleWindow();
  }

  /**
   * Returns the {@link MediaItem} for this media source.
   *
   * <p>This method can be overridden to amend the {@link MediaItem} of the child source. It is only
   * used before the child source is prepared.
   *
   * @see MediaSource#getMediaItem()
   */
  @Override
  public MediaItem getMediaItem() {
    return mediaSource.getMediaItem();
  }

  /**
   * Creates the requested {@link MediaPeriod}.
   *
   * <p>This method typically forwards to the wrapped media source and optionally wraps the returned
   * {@link MediaPeriod}.
   *
   * @see MediaSource#createPeriod(MediaPeriodId, Allocator, long)
   */
  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    return mediaSource.createPeriod(id, allocator, startPositionUs);
  }

  /**
   * Releases a {@link MediaPeriod}.
   *
   * <p>This method typically forwards to the wrapped media source and optionally unwraps the
   * provided {@link MediaPeriod}.
   *
   * @see MediaSource#releasePeriod(MediaPeriod)
   */
  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    mediaSource.releasePeriod(mediaPeriod);
  }

  @Override
  protected final void onChildSourceInfoRefreshed(
      Void childSourceId, MediaSource mediaSource, Timeline newTimeline) {
    onChildSourceInfoRefreshed(newTimeline);
  }

  /**
   * Called when the child source info has been refreshed.
   *
   * <p>This {@link Timeline} can be amended if needed, for example using {@link
   * ForwardingTimeline}. The {@link Timeline} for the wrapping source needs to be published with
   * {@link #refreshSourceInfo(Timeline)}.
   *
   * @param newTimeline The timeline of the child source.
   */
  protected void onChildSourceInfoRefreshed(Timeline newTimeline) {
    refreshSourceInfo(newTimeline);
  }

  @Override
  protected final int getWindowIndexForChildWindowIndex(Void childSourceId, int windowIndex) {
    return getWindowIndexForChildWindowIndex(windowIndex);
  }

  /**
   * Returns the window index in the wrapping source corresponding to the specified window index in
   * a child source. The default implementation does not change the window index.
   *
   * @param windowIndex A window index of the child source.
   * @return The corresponding window index in the wrapping source.
   */
  protected int getWindowIndexForChildWindowIndex(int windowIndex) {
    return windowIndex;
  }

  @Nullable
  @Override
  protected final MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(
      Void childSourceId, MediaPeriodId mediaPeriodId) {
    return getMediaPeriodIdForChildMediaPeriodId(mediaPeriodId);
  }

  /**
   * Returns the {@link MediaPeriodId} in the wrapping source corresponding to the specified {@link
   * MediaPeriodId} in a child source. The default implementation does not change the media period
   * id.
   *
   * @param mediaPeriodId A {@link MediaPeriodId} of the child source.
   * @return The corresponding {@link MediaPeriodId} in the wrapping source. Null if no
   *     corresponding media period id can be determined.
   */
  @Nullable
  protected MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(MediaPeriodId mediaPeriodId) {
    return mediaPeriodId;
  }

  @Override
  protected final long getMediaTimeForChildMediaTime(Void childSourceId, long mediaTimeMs) {
    return getMediaTimeForChildMediaTime(mediaTimeMs);
  }

  /**
   * Returns the media time in the {@link MediaPeriod} of the wrapping source corresponding to the
   * specified media time in the {@link MediaPeriod} of the child source. The default implementation
   * does not change the media time.
   *
   * @param mediaTimeMs A media time in the {@link MediaPeriod} of the child source, in
   *     milliseconds.
   * @return The corresponding media time in the {@link MediaPeriod} of the wrapping source, in
   *     milliseconds.
   */
  protected long getMediaTimeForChildMediaTime(long mediaTimeMs) {
    return mediaTimeMs;
  }

  /**
   * Prepares the wrapped child source.
   *
   * <p>{@link #onChildSourceInfoRefreshed(Timeline)} will be called when the child source updates
   * its timeline.
   *
   * <p>If sources aren't explicitly released with {@link #releaseChildSource()} they will be
   * released in {@link #releaseSourceInternal()}.
   */
  protected final void prepareChildSource() {
    prepareChildSource(CHILD_SOURCE_ID, mediaSource);
  }

  /** Enables the child source. */
  protected final void enableChildSource() {
    enableChildSource(CHILD_SOURCE_ID);
  }

  /** Disables the child source. */
  protected final void disableChildSource() {
    disableChildSource(CHILD_SOURCE_ID);
  }

  /** Releases the child source. */
  protected final void releaseChildSource() {
    releaseChildSource(CHILD_SOURCE_ID);
  }
}

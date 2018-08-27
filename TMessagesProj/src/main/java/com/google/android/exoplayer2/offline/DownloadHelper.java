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
package com.google.android.exoplayer2.offline;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.source.TrackGroupArray;
import java.io.IOException;
import java.util.List;

/** A helper for initializing and removing downloads. */
public abstract class DownloadHelper {

  /** A callback to be notified when the {@link DownloadHelper} is prepared. */
  public interface Callback {

    /**
     * Called when preparation completes.
     *
     * @param helper The reporting {@link DownloadHelper}.
     */
    void onPrepared(DownloadHelper helper);

    /**
     * Called when preparation fails.
     *
     * @param helper The reporting {@link DownloadHelper}.
     * @param e The error.
     */
    void onPrepareError(DownloadHelper helper, IOException e);
  }

  /**
   * Initializes the helper for starting a download.
   *
   * @param callback A callback to be notified when preparation completes or fails. The callback
   *     will be invoked on the calling thread unless that thread does not have an associated {@link
   *     Looper}, in which case it will be called on the application's main thread.
   */
  public void prepare(final Callback callback) {
    final Handler handler =
        new Handler(Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper());
    new Thread() {
      @Override
      public void run() {
        try {
          prepareInternal();
          handler.post(
              new Runnable() {
                @Override
                public void run() {
                  callback.onPrepared(DownloadHelper.this);
                }
              });
        } catch (final IOException e) {
          handler.post(
              new Runnable() {
                @Override
                public void run() {
                  callback.onPrepareError(DownloadHelper.this, e);
                }
              });
        }
      }
    }.start();
  }

  /**
   * Called on a background thread during preparation.
   *
   * @throws IOException If preparation fails.
   */
  protected abstract void prepareInternal() throws IOException;

  /**
   * Returns the number of periods for which media is available. Must not be called until after
   * preparation completes.
   */
  public abstract int getPeriodCount();

  /**
   * Returns the track groups for the given period. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index.
   * @return The track groups for the period. May be {@link TrackGroupArray#EMPTY} for single stream
   *     content.
   */
  public abstract TrackGroupArray getTrackGroups(int periodIndex);

  /**
   * Builds a {@link DownloadAction} for downloading the specified tracks. Must not be called until
   * after preparation completes.
   *
   * @param data Application provided data to store in {@link DownloadAction#data}.
   * @param trackKeys The selected tracks. If empty, all streams will be downloaded.
   * @return The built {@link DownloadAction}.
   */
  public abstract DownloadAction getDownloadAction(@Nullable byte[] data, List<TrackKey> trackKeys);

  /**
   * Builds a {@link DownloadAction} for removing the media. May be called in any state.
   *
   * @param data Application provided data to store in {@link DownloadAction#data}.
   * @return The built {@link DownloadAction}.
   */
  public abstract DownloadAction getRemoveAction(@Nullable byte[] data);
}

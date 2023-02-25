/*
 * Copyright (C) 2017 The Android Open Source Project
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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import java.io.IOException;
import java.util.concurrent.CancellationException;

/** Downloads and removes a piece of content. */
public interface Downloader {

  /** Receives progress updates during download operations. */
  interface ProgressListener {

    /**
     * Called when progress is made during a download operation.
     *
     * <p>May be called directly from {@link #download}, or from any other thread used by the
     * downloader. In all cases, {@link #download} is guaranteed not to return until after the last
     * call to this method has finished executing.
     *
     * @param contentLength The length of the content in bytes, or {@link C#LENGTH_UNSET} if
     *     unknown.
     * @param bytesDownloaded The number of bytes that have been downloaded.
     * @param percentDownloaded The percentage of the content that has been downloaded, or {@link
     *     C#PERCENTAGE_UNSET}.
     */
    void onProgress(long contentLength, long bytesDownloaded, float percentDownloaded);
  }

  /**
   * Downloads the content.
   *
   * <p>If downloading fails, this method can be called again to resume the download. It cannot be
   * called again after the download has been {@link #cancel canceled}.
   *
   * <p>If downloading is canceled whilst this method is executing, then it is expected that it will
   * return reasonably quickly. However, there are no guarantees about how the method will return,
   * meaning that it can return without throwing, or by throwing any of its documented exceptions.
   * The caller must use its own knowledge about whether downloading has been canceled to determine
   * whether this is why the method has returned, rather than relying on the method returning in a
   * particular way.
   *
   * @param progressListener A listener to receive progress updates, or {@code null}.
   * @throws IOException If the download failed to complete successfully.
   * @throws InterruptedException If the download was interrupted.
   * @throws CancellationException If the download was canceled.
   */
  void download(@Nullable ProgressListener progressListener)
      throws IOException, InterruptedException;

  /**
   * Permanently cancels the downloading by this downloader. The caller should also interrupt the
   * downloading thread immediately after calling this method.
   *
   * <p>Once canceled, {@link #download} cannot be called again.
   */
  void cancel();

  /** Removes the content. */
  void remove();
}

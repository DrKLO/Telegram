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
package org.telegram.messenger.exoplayer2.source;

import org.telegram.messenger.exoplayer2.C;

// TODO: Clarify the requirements for implementing this interface [Internal ref: b/36250203].
/**
 * A loader that can proceed in approximate synchronization with other loaders.
 */
public interface SequenceableLoader {

  /**
   * A callback to be notified of {@link SequenceableLoader} events.
   */
  interface Callback<T extends SequenceableLoader> {

    /**
     * Called by the loader to indicate that it wishes for its {@link #continueLoading(long)} method
     * to be called when it can continue to load data. Called on the playback thread.
     */
    void onContinueLoadingRequested(T source);

  }

  /**
   * Returns an estimate of the position up to which data is buffered.
   *
   * @return An estimate of the absolute position in microseconds up to which data is buffered, or
   *     {@link C#TIME_END_OF_SOURCE} if the data is fully buffered.
   */
  long getBufferedPositionUs();

  /**
   * Returns the next load time, or {@link C#TIME_END_OF_SOURCE} if loading has finished.
   */
  long getNextLoadPositionUs();

  /**
   * Attempts to continue loading.
   *
   * @param positionUs The current playback position.
   * @return True if progress was made, meaning that {@link #getNextLoadPositionUs()} will return
   *     a different value than prior to the call. False otherwise.
   */
  boolean continueLoading(long positionUs);

}

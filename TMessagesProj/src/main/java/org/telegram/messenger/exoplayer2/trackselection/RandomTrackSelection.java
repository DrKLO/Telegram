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
package org.telegram.messenger.exoplayer2.trackselection;

import android.os.SystemClock;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.source.TrackGroup;
import java.util.Random;

/**
 * A {@link TrackSelection} whose selected track is updated randomly.
 */
public final class RandomTrackSelection extends BaseTrackSelection {

  /**
   * Factory for {@link RandomTrackSelection} instances.
   */
  public static final class Factory implements TrackSelection.Factory {

    private final Random random;

    public Factory() {
      random = new Random();
    }

    /**
     * @param seed A seed for the {@link Random} instance used by the factory.
     */
    public Factory(int seed) {
      random = new Random(seed);
    }

    @Override
    public RandomTrackSelection createTrackSelection(TrackGroup group, int... tracks) {
      return new RandomTrackSelection(group, tracks, random);
    }

  }

  private final Random random;

  private int selectedIndex;

  /**
   * @param group The {@link TrackGroup}. Must not be null.
   * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
   *     null or empty. May be in any order.
   */
  public RandomTrackSelection(TrackGroup group, int... tracks) {
    super(group, tracks);
    random = new Random();
    selectedIndex = random.nextInt(length);
  }

  /**
   * @param group The {@link TrackGroup}. Must not be null.
   * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
   *     null or empty. May be in any order.
   * @param seed A seed for the {@link Random} instance used to update the selected track.
   */
  public RandomTrackSelection(TrackGroup group, int[] tracks, long seed) {
    this(group, tracks, new Random(seed));
  }

  /**
   * @param group The {@link TrackGroup}. Must not be null.
   * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
   *     null or empty. May be in any order.
   * @param random A source of random numbers.
   */
  public RandomTrackSelection(TrackGroup group, int[] tracks, Random random) {
    super(group, tracks);
    this.random = random;
    selectedIndex = random.nextInt(length);
  }

  @Override
  public void updateSelectedTrack(long bufferedDurationUs) {
    // Count the number of non-blacklisted formats.
    long nowMs = SystemClock.elapsedRealtime();
    int nonBlacklistedFormatCount = 0;
    for (int i = 0; i < length; i++) {
      if (!isBlacklisted(i, nowMs)) {
        nonBlacklistedFormatCount++;
      }
    }

    selectedIndex = random.nextInt(nonBlacklistedFormatCount);
    if (nonBlacklistedFormatCount != length) {
      // Adjust the format index to account for blacklisted formats.
      nonBlacklistedFormatCount = 0;
      for (int i = 0; i < length; i++) {
        if (!isBlacklisted(i, nowMs) && selectedIndex == nonBlacklistedFormatCount++) {
          selectedIndex = i;
          return;
        }
      }
    }
  }

  @Override
  public int getSelectedIndex() {
    return selectedIndex;
  }

  @Override
  public int getSelectionReason() {
    return C.SELECTION_REASON_ADAPTIVE;
  }

  @Override
  public Object getSelectionData() {
    return null;
  }

}

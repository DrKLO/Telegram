/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.ui;

import android.view.ViewGroup;
import androidx.annotation.Nullable;
import com.google.common.collect.ImmutableList;
import java.util.List;

/** Provides information about views for the ad playback UI. */
public interface AdViewProvider {

  /**
   * Returns the {@link ViewGroup} on top of the player that will show any ad UI, or {@code null} if
   * playing audio-only ads. Any views on top of the returned view group must be described by {@link
   * AdOverlayInfo AdOverlayInfos} returned by {@link #getAdOverlayInfos()}, for accurate
   * viewability measurement.
   */
  @Nullable
  ViewGroup getAdViewGroup();

  /**
   * Returns a list of {@link AdOverlayInfo} instances describing views that are on top of the ad
   * view group, but that are essential for controlling playback and should be excluded from ad
   * viewability measurements.
   *
   * <p>Each view must be either a fully transparent overlay (for capturing touch events), or a
   * small piece of transient UI that is essential to the user experience of playback (such as a
   * button to pause/resume playback or a transient full-screen or cast button). For more
   * information see the documentation for your ads loader.
   */
  default List<AdOverlayInfo> getAdOverlayInfos() {
    return ImmutableList.of();
  }
}

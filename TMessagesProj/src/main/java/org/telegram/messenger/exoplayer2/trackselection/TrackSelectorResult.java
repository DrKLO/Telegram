/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.telegram.messenger.exoplayer2.trackselection;

import org.telegram.messenger.exoplayer2.RendererConfiguration;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.util.Util;

/**
 * The result of a {@link TrackSelector} operation.
 */
public final class TrackSelectorResult {

  /**
   * The track groups that were provided to the {@link TrackSelector}.
   */
  public final TrackGroupArray groups;
  /**
   * A {@link TrackSelectionArray} containing the track selection for each renderer.
   */
  public final TrackSelectionArray selections;
  /**
   * An opaque object that will be returned to {@link TrackSelector#onSelectionActivated(Object)}
   * should the selections be activated.
   */
  public final Object info;
  /**
   * A {@link RendererConfiguration} for each renderer, to be used with the selections.
   */
  public final RendererConfiguration[] rendererConfigurations;

  /**
   * @param groups The track groups provided to the {@link TrackSelector}.
   * @param selections A {@link TrackSelectionArray} containing the selection for each renderer.
   * @param info An opaque object that will be returned to
   *     {@link TrackSelector#onSelectionActivated(Object)} should the selection be activated.
   * @param rendererConfigurations A {@link RendererConfiguration} for each renderer, to be used
   *     with the selections.
   */
  public TrackSelectorResult(TrackGroupArray groups, TrackSelectionArray selections, Object info,
      RendererConfiguration[] rendererConfigurations) {
    this.groups = groups;
    this.selections = selections;
    this.info = info;
    this.rendererConfigurations = rendererConfigurations;
  }

  /**
   * Returns whether this result is equivalent to {@code other} for all renderers.
   *
   * @param other The other {@link TrackSelectorResult}. May be null, in which case {@code false}
   *     will be returned.
   * @return Whether this result is equivalent to {@code other} for all renderers.
   */
  public boolean isEquivalent(TrackSelectorResult other) {
    if (other == null) {
      return false;
    }
    for (int i = 0; i < selections.length; i++) {
      if (!isEquivalent(other, i)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns whether this result is equivalent to {@code other} for the renderer at the given index.
   * The results are equivalent if they have equal track selections and configurations for the
   * renderer.
   *
   * @param other The other {@link TrackSelectorResult}. May be null, in which case {@code false}
   *     will be returned.
   * @param index The renderer index to check for equivalence.
   * @return Whether this result is equivalent to {@code other} for the renderer at the specified
   *     index.
   */
  public boolean isEquivalent(TrackSelectorResult other, int index) {
    if (other == null) {
      return false;
    }
    return Util.areEqual(selections.get(index), other.selections.get(index))
        && Util.areEqual(rendererConfigurations[index], other.rendererConfigurations[index]);
  }

}

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

import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import org.telegram.messenger.exoplayer2.ExoPlaybackException;
import org.telegram.messenger.exoplayer2.RendererCapabilities;
import org.telegram.messenger.exoplayer2.source.TrackGroup;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.util.Util;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for {@link TrackSelector}s that first establish a mapping between {@link TrackGroup}s
 * and renderers, and then from that mapping create a {@link TrackSelection} for each renderer.
 */
public abstract class MappingTrackSelector extends TrackSelector {

  /**
   * A track selection override.
   */
  public static final class SelectionOverride {

    public final TrackSelection.Factory factory;
    public final int groupIndex;
    public final int[] tracks;
    public final int length;

    /**
     * @param factory A factory for creating selections from this override.
     * @param groupIndex The overriding group index.
     * @param tracks The overriding track indices within the group.
     */
    public SelectionOverride(TrackSelection.Factory factory, int groupIndex, int... tracks) {
      this.factory = factory;
      this.groupIndex = groupIndex;
      this.tracks = tracks;
      this.length = tracks.length;
    }

    /**
     * Creates an selection from this override.
     *
     * @param groups The groups whose selection is being overridden.
     * @return The selection.
     */
    public TrackSelection createTrackSelection(TrackGroupArray groups) {
      return factory.createTrackSelection(groups.get(groupIndex), tracks);
    }

    /**
     * Returns whether this override contains the specified track index.
     */
    public boolean containsTrack(int track) {
      for (int overrideTrack : tracks) {
        if (overrideTrack == track) {
          return true;
        }
      }
      return false;
    }

  }

  private final SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides;
  private final SparseBooleanArray rendererDisabledFlags;

  private MappedTrackInfo currentMappedTrackInfo;

  public MappingTrackSelector() {
    selectionOverrides = new SparseArray<>();
    rendererDisabledFlags = new SparseBooleanArray();
  }

  /**
   * Returns the mapping information associated with the current track selections, or null if no
   * selection is currently active.
   */
  public final MappedTrackInfo getCurrentMappedTrackInfo() {
    return currentMappedTrackInfo;
  }

  /**
   * Sets whether the renderer at the specified index is disabled.
   *
   * @param rendererIndex The renderer index.
   * @param disabled Whether the renderer is disabled.
   */
  public final void setRendererDisabled(int rendererIndex, boolean disabled) {
    if (rendererDisabledFlags.get(rendererIndex) == disabled) {
      // The disabled flag is unchanged.
      return;
    }
    rendererDisabledFlags.put(rendererIndex, disabled);
    invalidate();
  }

  /**
   * Returns whether the renderer is disabled.
   *
   * @param rendererIndex The renderer index.
   * @return Whether the renderer is disabled.
   */
  public final boolean getRendererDisabled(int rendererIndex) {
    return rendererDisabledFlags.get(rendererIndex);
  }

  /**
   * Overrides the track selection for the renderer at a specified index.
   * <p>
   * When the {@link TrackGroupArray} available to the renderer at the specified index matches the
   * one provided, the override is applied. When the {@link TrackGroupArray} does not match, the
   * override has no effect. The override replaces any previous override for the renderer and the
   * provided {@link TrackGroupArray}.
   * <p>
   * Passing a {@code null} override will explicitly disable the renderer. To remove overrides use
   * {@link #clearSelectionOverride(int, TrackGroupArray)}, {@link #clearSelectionOverrides(int)}
   * or {@link #clearSelectionOverrides()}.
   *
   * @param rendererIndex The renderer index.
   * @param groups The {@link TrackGroupArray} for which the override should be applied.
   * @param override The override.
   */
  // TODO - Don't allow overrides that select unsupported tracks, unless some flag has been
  // explicitly set by the user to indicate that they want this.
  public final void setSelectionOverride(int rendererIndex, TrackGroupArray groups,
      SelectionOverride override) {
    Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
    if (overrides == null) {
      overrides = new HashMap<>();
      selectionOverrides.put(rendererIndex, overrides);
    }
    if (overrides.containsKey(groups) && Util.areEqual(overrides.get(groups), override)) {
      // The override is unchanged.
      return;
    }
    overrides.put(groups, override);
    invalidate();
  }

  /**
   * Returns whether there is an override for the specified renderer and {@link TrackGroupArray}.
   *
   * @param rendererIndex The renderer index.
   * @param groups The {@link TrackGroupArray}.
   * @return Whether there is an override.
   */
  public final boolean hasSelectionOverride(int rendererIndex, TrackGroupArray groups) {
    Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
    return overrides != null && overrides.containsKey(groups);
  }

  /**
   * Returns the override for the specified renderer and {@link TrackGroupArray}.
   *
   * @param rendererIndex The renderer index.
   * @param groups The {@link TrackGroupArray}.
   * @return The override, or null if no override exists.
   */
  public final SelectionOverride getSelectionOverride(int rendererIndex, TrackGroupArray groups) {
    Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
    return overrides != null ? overrides.get(groups) : null;
  }

  /**
   * Clears a track selection override for the specified renderer and {@link TrackGroupArray}.
   *
   * @param rendererIndex The renderer index.
   * @param groups The {@link TrackGroupArray} for which the override should be cleared.
   */
  public final void clearSelectionOverride(int rendererIndex, TrackGroupArray groups) {
    Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
    if (overrides == null || !overrides.containsKey(groups)) {
      // Nothing to clear.
      return;
    }
    overrides.remove(groups);
    if (overrides.isEmpty()) {
      selectionOverrides.remove(rendererIndex);
    }
    invalidate();
  }

  /**
   * Clears all track selection override for the specified renderer.
   *
   * @param rendererIndex The renderer index.
   */
  public final void clearSelectionOverrides(int rendererIndex) {
    Map<TrackGroupArray, ?> overrides = selectionOverrides.get(rendererIndex);
    if (overrides == null || overrides.isEmpty()) {
      // Nothing to clear.
      return;
    }
    selectionOverrides.remove(rendererIndex);
    invalidate();
  }

  /**
   * Clears all track selection overrides.
   */
  public final void clearSelectionOverrides() {
    if (selectionOverrides.size() == 0) {
      // Nothing to clear.
      return;
    }
    selectionOverrides.clear();
    invalidate();
  }

  // TrackSelector implementation.

  @Override
  public final Pair<TrackSelectionArray, Object> selectTracks(
      RendererCapabilities[] rendererCapabilities, TrackGroupArray trackGroups)
      throws ExoPlaybackException {
    // Structures into which data will be written during the selection. The extra item at the end
    // of each array is to store data associated with track groups that cannot be associated with
    // any renderer.
    int[] rendererTrackGroupCounts = new int[rendererCapabilities.length + 1];
    TrackGroup[][] rendererTrackGroups = new TrackGroup[rendererCapabilities.length + 1][];
    int[][][] rendererFormatSupports = new int[rendererCapabilities.length + 1][][];
    for (int i = 0; i < rendererTrackGroups.length; i++) {
      rendererTrackGroups[i] = new TrackGroup[trackGroups.length];
      rendererFormatSupports[i] = new int[trackGroups.length][];
    }

    // Determine the extent to which each renderer supports mixed mimeType adaptation.
    int[] mixedMimeTypeAdaptationSupport = getMixedMimeTypeAdaptationSupport(rendererCapabilities);

    // Associate each track group to a preferred renderer, and evaluate the support that the
    // renderer provides for each track in the group.
    for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
      TrackGroup group = trackGroups.get(groupIndex);
      // Associate the group to a preferred renderer.
      int rendererIndex = findRenderer(rendererCapabilities, group);
      // Evaluate the support that the renderer provides for each track in the group.
      int[] rendererFormatSupport = rendererIndex == rendererCapabilities.length
          ? new int[group.length] : getFormatSupport(rendererCapabilities[rendererIndex], group);
      // Stash the results.
      int rendererTrackGroupCount = rendererTrackGroupCounts[rendererIndex];
      rendererTrackGroups[rendererIndex][rendererTrackGroupCount] = group;
      rendererFormatSupports[rendererIndex][rendererTrackGroupCount] = rendererFormatSupport;
      rendererTrackGroupCounts[rendererIndex]++;
    }

    // Create a track group array for each renderer, and trim each rendererFormatSupports entry.
    TrackGroupArray[] rendererTrackGroupArrays = new TrackGroupArray[rendererCapabilities.length];
    int[] rendererTrackTypes = new int[rendererCapabilities.length];
    for (int i = 0; i < rendererCapabilities.length; i++) {
      int rendererTrackGroupCount = rendererTrackGroupCounts[i];
      rendererTrackGroupArrays[i] = new TrackGroupArray(
          Arrays.copyOf(rendererTrackGroups[i], rendererTrackGroupCount));
      rendererFormatSupports[i] = Arrays.copyOf(rendererFormatSupports[i], rendererTrackGroupCount);
      rendererTrackTypes[i] = rendererCapabilities[i].getTrackType();
    }

    // Create a track group array for track groups not associated with a renderer.
    int unassociatedTrackGroupCount = rendererTrackGroupCounts[rendererCapabilities.length];
    TrackGroupArray unassociatedTrackGroupArray = new TrackGroupArray(Arrays.copyOf(
        rendererTrackGroups[rendererCapabilities.length], unassociatedTrackGroupCount));

    TrackSelection[] trackSelections = selectTracks(rendererCapabilities, rendererTrackGroupArrays,
        rendererFormatSupports);

    // Apply track disabling and overriding.
    for (int i = 0; i < rendererCapabilities.length; i++) {
      if (rendererDisabledFlags.get(i)) {
        trackSelections[i] = null;
      } else {
        TrackGroupArray rendererTrackGroup = rendererTrackGroupArrays[i];
        Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(i);
        SelectionOverride override = overrides == null ? null : overrides.get(rendererTrackGroup);
        if (override != null) {
          trackSelections[i] = override.createTrackSelection(rendererTrackGroup);
        }
      }
    }

    // Package up the track information and selections.
    MappedTrackInfo mappedTrackInfo = new MappedTrackInfo(rendererTrackTypes,
        rendererTrackGroupArrays, mixedMimeTypeAdaptationSupport, rendererFormatSupports,
        unassociatedTrackGroupArray);
    return Pair.<TrackSelectionArray, Object>create(new TrackSelectionArray(trackSelections),
        mappedTrackInfo);
  }

  @Override
  public final void onSelectionActivated(Object info) {
    currentMappedTrackInfo = (MappedTrackInfo) info;
  }

  /**
   * Given an array of renderers and a set of {@link TrackGroup}s mapped to each of them, provides a
   * {@link TrackSelection} per renderer.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderers for which
   *     {@link TrackSelection}s are to be generated.
   * @param rendererTrackGroupArrays An array of {@link TrackGroupArray}s where each entry
   *     corresponds to the renderer of equal index in {@code renderers}.
   * @param rendererFormatSupports Maps every available track to a specific level of support as
   *     defined by the renderer {@code FORMAT_*} constants.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected abstract TrackSelection[] selectTracks(RendererCapabilities[] rendererCapabilities,
      TrackGroupArray[] rendererTrackGroupArrays, int[][][] rendererFormatSupports)
      throws ExoPlaybackException;

  /**
   * Finds the renderer to which the provided {@link TrackGroup} should be associated.
   * <p>
   * A {@link TrackGroup} is associated to a renderer that reports
   * {@link RendererCapabilities#FORMAT_HANDLED} support for one or more of the tracks in the group,
   * or {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES} if no such renderer exists, or
   * {@link RendererCapabilities#FORMAT_UNSUPPORTED_SUBTYPE} if again no such renderer exists. In
   * the case that two or more renderers report the same level of support, the renderer with the
   * lowest index is associated.
   * <p>
   * If all renderers report {@link RendererCapabilities#FORMAT_UNSUPPORTED_TYPE} for all of the
   * tracks in the group, then {@code renderers.length} is returned to indicate that no association
   * was made.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderers.
   * @param group The {@link TrackGroup} whose associated renderer is to be found.
   * @return The index of the associated renderer, or {@code renderers.length} if no
   *     association was made.
   * @throws ExoPlaybackException If an error occurs finding a renderer.
   */
  private static int findRenderer(RendererCapabilities[] rendererCapabilities, TrackGroup group)
      throws ExoPlaybackException {
    int bestRendererIndex = rendererCapabilities.length;
    int bestSupportLevel = RendererCapabilities.FORMAT_UNSUPPORTED_TYPE;
    for (int rendererIndex = 0; rendererIndex < rendererCapabilities.length; rendererIndex++) {
      RendererCapabilities rendererCapability = rendererCapabilities[rendererIndex];
      for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
        int trackSupportLevel = rendererCapability.supportsFormat(group.getFormat(trackIndex));
        if (trackSupportLevel > bestSupportLevel) {
          bestRendererIndex = rendererIndex;
          bestSupportLevel = trackSupportLevel;
          if (bestSupportLevel == RendererCapabilities.FORMAT_HANDLED) {
            // We can't do better.
            return bestRendererIndex;
          }
        }
      }
    }
    return bestRendererIndex;
  }

  /**
   * Calls {@link RendererCapabilities#supportsFormat} for each track in the specified
   * {@link TrackGroup}, returning the results in an array.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderer.
   * @param group The {@link TrackGroup} to evaluate.
   * @return An array containing the result of calling
   *     {@link RendererCapabilities#supportsFormat} for each track in the group.
   * @throws ExoPlaybackException If an error occurs determining the format support.
   */
  private static int[] getFormatSupport(RendererCapabilities rendererCapabilities, TrackGroup group)
      throws ExoPlaybackException {
    int[] formatSupport = new int[group.length];
    for (int i = 0; i < group.length; i++) {
      formatSupport[i] = rendererCapabilities.supportsFormat(group.getFormat(i));
    }
    return formatSupport;
  }

  /**
   * Calls {@link RendererCapabilities#supportsMixedMimeTypeAdaptation()} for each renderer,
   * returning the results in an array.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderers.
   * @return An array containing the result of calling
   *     {@link RendererCapabilities#supportsMixedMimeTypeAdaptation()} for each renderer.
   * @throws ExoPlaybackException If an error occurs determining the adaptation support.
   */
  private static int[] getMixedMimeTypeAdaptationSupport(
      RendererCapabilities[] rendererCapabilities) throws ExoPlaybackException {
    int[] mixedMimeTypeAdaptationSupport = new int[rendererCapabilities.length];
    for (int i = 0; i < mixedMimeTypeAdaptationSupport.length; i++) {
      mixedMimeTypeAdaptationSupport[i] = rendererCapabilities[i].supportsMixedMimeTypeAdaptation();
    }
    return mixedMimeTypeAdaptationSupport;
  }

  /**
   * Provides track information for each renderer.
   */
  public static final class MappedTrackInfo {

    /**
     * The renderer does not have any associated tracks.
     */
    public static final int RENDERER_SUPPORT_NO_TRACKS = 0;
    /**
     * The renderer has associated tracks, but cannot play any of them.
     */
    public static final int RENDERER_SUPPORT_UNPLAYABLE_TRACKS = 1;
    /**
     * The renderer has associated tracks, and can play at least one of them.
     */
    public static final int RENDERER_SUPPORT_PLAYABLE_TRACKS = 2;

    /**
     * The number of renderers to which tracks are mapped.
     */
    public final int length;

    private final int[] rendererTrackTypes;
    private final TrackGroupArray[] trackGroups;
    private final int[] mixedMimeTypeAdaptiveSupport;
    private final int[][][] formatSupport;
    private final TrackGroupArray unassociatedTrackGroups;

    /**
     * @param rendererTrackTypes The track type supported by each renderer.
     * @param trackGroups The {@link TrackGroupArray}s for each renderer.
     * @param mixedMimeTypeAdaptiveSupport The result of
     *     {@link RendererCapabilities#supportsMixedMimeTypeAdaptation()} for each renderer.
     * @param formatSupport The result of {@link RendererCapabilities#supportsFormat} for each
     *     track, indexed by renderer index, group index and track index (in that order).
     * @param unassociatedTrackGroups Contains {@link TrackGroup}s not associated with any renderer.
     */
    /* package */ MappedTrackInfo(int[] rendererTrackTypes,
        TrackGroupArray[] trackGroups, int[] mixedMimeTypeAdaptiveSupport,
        int[][][] formatSupport, TrackGroupArray unassociatedTrackGroups) {
      this.rendererTrackTypes = rendererTrackTypes;
      this.trackGroups = trackGroups;
      this.formatSupport = formatSupport;
      this.mixedMimeTypeAdaptiveSupport = mixedMimeTypeAdaptiveSupport;
      this.unassociatedTrackGroups = unassociatedTrackGroups;
      this.length = trackGroups.length;
    }

    /**
     * Returns the array of {@link TrackGroup}s associated to the renderer at a specified index.
     *
     * @param rendererIndex The renderer index.
     * @return The corresponding {@link TrackGroup}s.
     */
    public TrackGroupArray getTrackGroups(int rendererIndex) {
      return trackGroups[rendererIndex];
    }

    /**
     * Returns the extent to which a renderer can support playback of the tracks associated to it.
     *
     * @param rendererIndex The renderer index.
     * @return One of {@link #RENDERER_SUPPORT_PLAYABLE_TRACKS},
     *     {@link #RENDERER_SUPPORT_UNPLAYABLE_TRACKS} and {@link #RENDERER_SUPPORT_NO_TRACKS}.
     */
    public int getRendererSupport(int rendererIndex) {
      boolean hasTracks = false;
      int[][] rendererFormatSupport = formatSupport[rendererIndex];
      for (int i = 0; i < rendererFormatSupport.length; i++) {
        for (int j = 0; j < rendererFormatSupport[i].length; j++) {
          hasTracks = true;
          if ((rendererFormatSupport[i][j] & RendererCapabilities.FORMAT_SUPPORT_MASK)
              == RendererCapabilities.FORMAT_HANDLED) {
            return RENDERER_SUPPORT_PLAYABLE_TRACKS;
          }
        }
      }
      return hasTracks ? RENDERER_SUPPORT_UNPLAYABLE_TRACKS : RENDERER_SUPPORT_NO_TRACKS;
    }

    /**
     * Returns the extent to which the format of an individual track is supported by the renderer.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the group to which the track belongs.
     * @param trackIndex The index of the track within the group.
     * @return One of {@link RendererCapabilities#FORMAT_HANDLED},
     *     {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES},
     *     {@link RendererCapabilities#FORMAT_UNSUPPORTED_SUBTYPE} and
     *     {@link RendererCapabilities#FORMAT_UNSUPPORTED_TYPE}.
     */
    public int getTrackFormatSupport(int rendererIndex, int groupIndex, int trackIndex) {
      return formatSupport[rendererIndex][groupIndex][trackIndex]
          & RendererCapabilities.FORMAT_SUPPORT_MASK;
    }

    /**
     * Returns the extent to which the renderer supports adaptation between supported tracks in a
     * specified {@link TrackGroup}.
     * <p>
     * Tracks for which {@link #getTrackFormatSupport(int, int, int)} returns
     * {@link RendererCapabilities#FORMAT_HANDLED} are always considered.
     * Tracks for which {@link #getTrackFormatSupport(int, int, int)} returns
     * {@link RendererCapabilities#FORMAT_UNSUPPORTED_TYPE} or
     * {@link RendererCapabilities#FORMAT_UNSUPPORTED_SUBTYPE} are never considered.
     * Tracks for which {@link #getTrackFormatSupport(int, int, int)} returns
     * {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES} are considered only if
     * {@code includeCapabilitiesExceededTracks} is set to {@code true}.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the group.
     * @param includeCapabilitiesExceededTracks True if formats that exceed the capabilities of the
     *     renderer should be included when determining support. False otherwise.
     * @return One of {@link RendererCapabilities#ADAPTIVE_SEAMLESS},
     *     {@link RendererCapabilities#ADAPTIVE_NOT_SEAMLESS} and
     *     {@link RendererCapabilities#ADAPTIVE_NOT_SUPPORTED}.
     */
    public int getAdaptiveSupport(int rendererIndex, int groupIndex,
        boolean includeCapabilitiesExceededTracks) {
      int trackCount = trackGroups[rendererIndex].get(groupIndex).length;
      // Iterate over the tracks in the group, recording the indices of those to consider.
      int[] trackIndices = new int[trackCount];
      int trackIndexCount = 0;
      for (int i = 0; i < trackCount; i++) {
        int fixedSupport = getTrackFormatSupport(rendererIndex, groupIndex, i);
        if (fixedSupport == RendererCapabilities.FORMAT_HANDLED
            || (includeCapabilitiesExceededTracks
            && fixedSupport == RendererCapabilities.FORMAT_EXCEEDS_CAPABILITIES)) {
          trackIndices[trackIndexCount++] = i;
        }
      }
      trackIndices = Arrays.copyOf(trackIndices, trackIndexCount);
      return getAdaptiveSupport(rendererIndex, groupIndex, trackIndices);
    }

    /**
     * Returns the extent to which the renderer supports adaptation between specified tracks within
     * a {@link TrackGroup}.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the group.
     * @return One of {@link RendererCapabilities#ADAPTIVE_SEAMLESS},
     *     {@link RendererCapabilities#ADAPTIVE_NOT_SEAMLESS} and
     *     {@link RendererCapabilities#ADAPTIVE_NOT_SUPPORTED}.
     */
    public int getAdaptiveSupport(int rendererIndex, int groupIndex, int[] trackIndices) {
      int handledTrackCount = 0;
      int adaptiveSupport = RendererCapabilities.ADAPTIVE_SEAMLESS;
      boolean multipleMimeTypes = false;
      String firstSampleMimeType = null;
      for (int i = 0; i < trackIndices.length; i++) {
        int trackIndex = trackIndices[i];
        String sampleMimeType = trackGroups[rendererIndex].get(groupIndex).getFormat(trackIndex)
            .sampleMimeType;
        if (handledTrackCount++ == 0) {
          firstSampleMimeType = sampleMimeType;
        } else {
          multipleMimeTypes |= !Util.areEqual(firstSampleMimeType, sampleMimeType);
        }
        adaptiveSupport = Math.min(adaptiveSupport, formatSupport[rendererIndex][groupIndex][i]
            & RendererCapabilities.ADAPTIVE_SUPPORT_MASK);
      }
      return multipleMimeTypes
          ? Math.min(adaptiveSupport, mixedMimeTypeAdaptiveSupport[rendererIndex])
          : adaptiveSupport;
    }

    /**
     * Returns the {@link TrackGroup}s not associated with any renderer.
     */
    public TrackGroupArray getUnassociatedTrackGroups() {
      return unassociatedTrackGroups;
    }

    /**
     * Returns true if tracks of the specified type exist and have been associated with renderers,
     * but are all unplayable. Returns false in all other cases.
     *
     * @param trackType The track type.
     * @return True if tracks of the specified type exist, if at least one renderer exists that
     *     handles tracks of the specified type, and if all of the tracks if the specified type are
     *     unplayable. False in all other cases.
     */
    public boolean hasOnlyUnplayableTracks(int trackType) {
      int rendererSupport = RENDERER_SUPPORT_NO_TRACKS;
      for (int i = 0; i < length; i++) {
        if (rendererTrackTypes[i] == trackType) {
          rendererSupport = Math.max(rendererSupport, getRendererSupport(i));
        }
      }
      return rendererSupport == RENDERER_SUPPORT_UNPLAYABLE_TRACKS;
    }

  }

}

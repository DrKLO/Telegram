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
package com.google.android.exoplayer2.trackselection;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.util.Pair;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.FormatSupport;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererCapabilities.AdaptiveSupport;
import com.google.android.exoplayer2.RendererCapabilities.Capabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * Base class for {@link TrackSelector}s that first establish a mapping between {@link TrackGroup}s
 * and {@link Renderer}s, and then from that mapping create a {@link ExoTrackSelection} for each
 * renderer.
 */
public abstract class MappingTrackSelector extends TrackSelector {

  /** Provides mapped track information for each renderer. */
  public static final class MappedTrackInfo {

    /**
     * Levels of renderer support. Higher numerical values indicate higher levels of support. One of
     * {@link #RENDERER_SUPPORT_NO_TRACKS}, {@link #RENDERER_SUPPORT_UNSUPPORTED_TRACKS}, {@link
     * #RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS} or {@link #RENDERER_SUPPORT_PLAYABLE_TRACKS}.
     */
    // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
    // with Kotlin usages from before TYPE_USE was added.
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
    @IntDef({
      RENDERER_SUPPORT_NO_TRACKS,
      RENDERER_SUPPORT_UNSUPPORTED_TRACKS,
      RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS,
      RENDERER_SUPPORT_PLAYABLE_TRACKS
    })
    public @interface RendererSupport {}
    /** The renderer does not have any associated tracks. */
    public static final int RENDERER_SUPPORT_NO_TRACKS = 0;
    /**
     * The renderer has tracks mapped to it, but all are unsupported. In other words, {@link
     * #getTrackSupport(int, int, int)} returns {@link C#FORMAT_UNSUPPORTED_DRM}, {@link
     * C#FORMAT_UNSUPPORTED_SUBTYPE} or {@link C#FORMAT_UNSUPPORTED_TYPE} for all tracks mapped to
     * the renderer.
     */
    public static final int RENDERER_SUPPORT_UNSUPPORTED_TRACKS = 1;
    /**
     * The renderer has tracks mapped to it and at least one is of a supported type, but all such
     * tracks exceed the renderer's capabilities. In other words, {@link #getTrackSupport(int, int,
     * int)} returns {@link C#FORMAT_EXCEEDS_CAPABILITIES} for at least one track mapped to the
     * renderer, but does not return {@link C#FORMAT_HANDLED} for any tracks mapped to the renderer.
     */
    public static final int RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS = 2;
    /**
     * The renderer has tracks mapped to it, and at least one such track is playable. In other
     * words, {@link #getTrackSupport(int, int, int)} returns {@link C#FORMAT_HANDLED} for at least
     * one track mapped to the renderer.
     */
    public static final int RENDERER_SUPPORT_PLAYABLE_TRACKS = 3;

    private final int rendererCount;
    private final String[] rendererNames;
    private final @C.TrackType int[] rendererTrackTypes;
    private final TrackGroupArray[] rendererTrackGroups;
    private final @AdaptiveSupport int[] rendererMixedMimeTypeAdaptiveSupports;
    private final @Capabilities int[][][] rendererFormatSupports;
    private final TrackGroupArray unmappedTrackGroups;

    /**
     * @param rendererNames The name of each renderer.
     * @param rendererTrackTypes The {@link C.TrackType track type} handled by each renderer.
     * @param rendererTrackGroups The {@link TrackGroup}s mapped to each renderer.
     * @param rendererMixedMimeTypeAdaptiveSupports The {@link AdaptiveSupport} for mixed MIME type
     *     adaptation for the renderer.
     * @param rendererFormatSupports The {@link Capabilities} for each mapped track, indexed by
     *     renderer, track group and track (in that order).
     * @param unmappedTrackGroups {@link TrackGroup}s not mapped to any renderer.
     */
    @VisibleForTesting
    /* package */ MappedTrackInfo(
        String[] rendererNames,
        @C.TrackType int[] rendererTrackTypes,
        TrackGroupArray[] rendererTrackGroups,
        @AdaptiveSupport int[] rendererMixedMimeTypeAdaptiveSupports,
        @Capabilities int[][][] rendererFormatSupports,
        TrackGroupArray unmappedTrackGroups) {
      this.rendererNames = rendererNames;
      this.rendererTrackTypes = rendererTrackTypes;
      this.rendererTrackGroups = rendererTrackGroups;
      this.rendererFormatSupports = rendererFormatSupports;
      this.rendererMixedMimeTypeAdaptiveSupports = rendererMixedMimeTypeAdaptiveSupports;
      this.unmappedTrackGroups = unmappedTrackGroups;
      this.rendererCount = rendererTrackTypes.length;
    }

    /** Returns the number of renderers. */
    public int getRendererCount() {
      return rendererCount;
    }

    /**
     * Returns the name of the renderer at a given index.
     *
     * @see Renderer#getName()
     * @param rendererIndex The renderer index.
     * @return The name of the renderer.
     */
    public String getRendererName(int rendererIndex) {
      return rendererNames[rendererIndex];
    }

    /**
     * Returns the track type that the renderer at a given index handles.
     *
     * @see Renderer#getTrackType()
     * @param rendererIndex The renderer index.
     * @return The {@link C.TrackType} of the renderer.
     */
    public @C.TrackType int getRendererType(int rendererIndex) {
      return rendererTrackTypes[rendererIndex];
    }

    /**
     * Returns the {@link TrackGroup}s mapped to the renderer at the specified index.
     *
     * @param rendererIndex The renderer index.
     * @return The corresponding {@link TrackGroup}s.
     */
    public TrackGroupArray getTrackGroups(int rendererIndex) {
      return rendererTrackGroups[rendererIndex];
    }

    /**
     * Returns the extent to which a renderer can play the tracks that are mapped to it.
     *
     * @param rendererIndex The renderer index.
     * @return The {@link RendererSupport}.
     */
    public @RendererSupport int getRendererSupport(int rendererIndex) {
      @RendererSupport int bestRendererSupport = RENDERER_SUPPORT_NO_TRACKS;
      @Capabilities int[][] rendererFormatSupport = rendererFormatSupports[rendererIndex];
      for (@Capabilities int[] trackGroupFormatSupport : rendererFormatSupport) {
        for (@Capabilities int trackFormatSupport : trackGroupFormatSupport) {
          int trackRendererSupport;
          switch (RendererCapabilities.getFormatSupport(trackFormatSupport)) {
            case C.FORMAT_HANDLED:
              return RENDERER_SUPPORT_PLAYABLE_TRACKS;
            case C.FORMAT_EXCEEDS_CAPABILITIES:
              trackRendererSupport = RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS;
              break;
            case C.FORMAT_UNSUPPORTED_TYPE:
            case C.FORMAT_UNSUPPORTED_SUBTYPE:
            case C.FORMAT_UNSUPPORTED_DRM:
              trackRendererSupport = RENDERER_SUPPORT_UNSUPPORTED_TRACKS;
              break;
            default:
              throw new IllegalStateException();
          }
          bestRendererSupport = max(bestRendererSupport, trackRendererSupport);
        }
      }
      return bestRendererSupport;
    }

    /**
     * Returns the extent to which tracks of a specified type are supported. This is the best level
     * of support obtained from {@link #getRendererSupport(int)} for all renderers that handle the
     * specified type. If no such renderers exist then {@link #RENDERER_SUPPORT_NO_TRACKS} is
     * returned.
     *
     * @param trackType The {@link C.TrackType track type}.
     * @return The {@link RendererSupport}.
     */
    public @RendererSupport int getTypeSupport(@C.TrackType int trackType) {
      @RendererSupport int bestRendererSupport = RENDERER_SUPPORT_NO_TRACKS;
      for (int i = 0; i < rendererCount; i++) {
        if (rendererTrackTypes[i] == trackType) {
          bestRendererSupport = max(bestRendererSupport, getRendererSupport(i));
        }
      }
      return bestRendererSupport;
    }

    /**
     * Returns the {@link Capabilities} of the renderer for an individual track.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the track group to which the track belongs.
     * @param trackIndex The index of the track within the track group.
     * @return The {@link Capabilities}.
     */
    public @Capabilities int getCapabilities(int rendererIndex, int groupIndex, int trackIndex) {
      return rendererFormatSupports[rendererIndex][groupIndex][trackIndex];
    }

    /**
     * Returns the extent to which an individual track is supported by the renderer.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the track group to which the track belongs.
     * @param trackIndex The index of the track within the track group.
     * @return The {@link FormatSupport}.
     */
    public @FormatSupport int getTrackSupport(int rendererIndex, int groupIndex, int trackIndex) {
      return RendererCapabilities.getFormatSupport(
          getCapabilities(rendererIndex, groupIndex, trackIndex));
    }

    /**
     * Returns the extent to which a renderer supports adaptation between supported tracks in a
     * specified {@link TrackGroup}.
     *
     * <p>Tracks for which {@link #getTrackSupport(int, int, int)} returns {@link C#FORMAT_HANDLED}
     * are always considered. Tracks for which {@link #getTrackSupport(int, int, int)} returns
     * {@link C#FORMAT_EXCEEDS_CAPABILITIES} are also considered if {@code
     * includeCapabilitiesExceededTracks} is set to {@code true}. Tracks for which {@link
     * #getTrackSupport(int, int, int)} returns {@link C#FORMAT_UNSUPPORTED_DRM}, {@link
     * C#FORMAT_UNSUPPORTED_TYPE} or {@link C#FORMAT_UNSUPPORTED_SUBTYPE} are never considered.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the track group.
     * @param includeCapabilitiesExceededTracks Whether tracks that exceed the capabilities of the
     *     renderer are included when determining support.
     * @return The {@link AdaptiveSupport}.
     */
    public @AdaptiveSupport int getAdaptiveSupport(
        int rendererIndex, int groupIndex, boolean includeCapabilitiesExceededTracks) {
      int trackCount = rendererTrackGroups[rendererIndex].get(groupIndex).length;
      // Iterate over the tracks in the group, recording the indices of those to consider.
      int[] trackIndices = new int[trackCount];
      int trackIndexCount = 0;
      for (int i = 0; i < trackCount; i++) {
        @FormatSupport int fixedSupport = getTrackSupport(rendererIndex, groupIndex, i);
        if (fixedSupport == C.FORMAT_HANDLED
            || (includeCapabilitiesExceededTracks
                && fixedSupport == C.FORMAT_EXCEEDS_CAPABILITIES)) {
          trackIndices[trackIndexCount++] = i;
        }
      }
      trackIndices = Arrays.copyOf(trackIndices, trackIndexCount);
      return getAdaptiveSupport(rendererIndex, groupIndex, trackIndices);
    }

    /**
     * Returns the extent to which a renderer supports adaptation between specified tracks within a
     * {@link TrackGroup}.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the track group.
     * @return The {@link AdaptiveSupport}.
     */
    public @AdaptiveSupport int getAdaptiveSupport(
        int rendererIndex, int groupIndex, int[] trackIndices) {
      int handledTrackCount = 0;
      @AdaptiveSupport int adaptiveSupport = RendererCapabilities.ADAPTIVE_SEAMLESS;
      boolean multipleMimeTypes = false;
      String firstSampleMimeType = null;
      for (int i = 0; i < trackIndices.length; i++) {
        int trackIndex = trackIndices[i];
        @Nullable
        String sampleMimeType =
            rendererTrackGroups[rendererIndex].get(groupIndex).getFormat(trackIndex).sampleMimeType;
        if (handledTrackCount++ == 0) {
          firstSampleMimeType = sampleMimeType;
        } else {
          multipleMimeTypes |= !Util.areEqual(firstSampleMimeType, sampleMimeType);
        }
        adaptiveSupport =
            min(
                adaptiveSupport,
                RendererCapabilities.getAdaptiveSupport(
                    rendererFormatSupports[rendererIndex][groupIndex][i]));
      }
      return multipleMimeTypes
          ? min(adaptiveSupport, rendererMixedMimeTypeAdaptiveSupports[rendererIndex])
          : adaptiveSupport;
    }

    /** Returns {@link TrackGroup}s not mapped to any renderer. */
    public TrackGroupArray getUnmappedTrackGroups() {
      return unmappedTrackGroups;
    }
  }

  @Nullable private MappedTrackInfo currentMappedTrackInfo;

  /**
   * Returns the mapping information for the currently active track selection, or null if no
   * selection is currently active.
   */
  @Nullable
  public final MappedTrackInfo getCurrentMappedTrackInfo() {
    return currentMappedTrackInfo;
  }

  // TrackSelector implementation.

  @Override
  public final void onSelectionActivated(@Nullable Object info) {
    currentMappedTrackInfo = (MappedTrackInfo) info;
  }

  @Override
  public final TrackSelectorResult selectTracks(
      RendererCapabilities[] rendererCapabilities,
      TrackGroupArray trackGroups,
      MediaPeriodId periodId,
      Timeline timeline)
      throws ExoPlaybackException {
    // Structures into which data will be written during the selection. The extra item at the end
    // of each array is to store data associated with track groups that cannot be associated with
    // any renderer.
    int[] rendererTrackGroupCounts = new int[rendererCapabilities.length + 1];
    TrackGroup[][] rendererTrackGroups = new TrackGroup[rendererCapabilities.length + 1][];
    @Capabilities int[][][] rendererFormatSupports = new int[rendererCapabilities.length + 1][][];
    for (int i = 0; i < rendererTrackGroups.length; i++) {
      rendererTrackGroups[i] = new TrackGroup[trackGroups.length];
      rendererFormatSupports[i] = new int[trackGroups.length][];
    }

    // Determine the extent to which each renderer supports mixed mimeType adaptation.
    @AdaptiveSupport
    int[] rendererMixedMimeTypeAdaptationSupports =
        getMixedMimeTypeAdaptationSupports(rendererCapabilities);

    // Associate each track group to a preferred renderer, and evaluate the support that the
    // renderer provides for each track in the group.
    for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
      TrackGroup group = trackGroups.get(groupIndex);
      // Associate the group to a preferred renderer.
      boolean preferUnassociatedRenderer = group.type == C.TRACK_TYPE_METADATA;
      int rendererIndex =
          findRenderer(
              rendererCapabilities, group, rendererTrackGroupCounts, preferUnassociatedRenderer);
      // Evaluate the support that the renderer provides for each track in the group.
      @Capabilities
      int[] rendererFormatSupport =
          rendererIndex == rendererCapabilities.length
              ? new int[group.length]
              : getFormatSupport(rendererCapabilities[rendererIndex], group);
      // Stash the results.
      int rendererTrackGroupCount = rendererTrackGroupCounts[rendererIndex];
      rendererTrackGroups[rendererIndex][rendererTrackGroupCount] = group;
      rendererFormatSupports[rendererIndex][rendererTrackGroupCount] = rendererFormatSupport;
      rendererTrackGroupCounts[rendererIndex]++;
    }

    // Create a track group array for each renderer, and trim each rendererFormatSupports entry.
    TrackGroupArray[] rendererTrackGroupArrays = new TrackGroupArray[rendererCapabilities.length];
    String[] rendererNames = new String[rendererCapabilities.length];
    int[] rendererTrackTypes = new int[rendererCapabilities.length];
    for (int i = 0; i < rendererCapabilities.length; i++) {
      int rendererTrackGroupCount = rendererTrackGroupCounts[i];
      rendererTrackGroupArrays[i] =
          new TrackGroupArray(
              Util.nullSafeArrayCopy(rendererTrackGroups[i], rendererTrackGroupCount));
      rendererFormatSupports[i] =
          Util.nullSafeArrayCopy(rendererFormatSupports[i], rendererTrackGroupCount);
      rendererNames[i] = rendererCapabilities[i].getName();
      rendererTrackTypes[i] = rendererCapabilities[i].getTrackType();
    }

    // Create a track group array for track groups not mapped to a renderer.
    int unmappedTrackGroupCount = rendererTrackGroupCounts[rendererCapabilities.length];
    TrackGroupArray unmappedTrackGroupArray =
        new TrackGroupArray(
            Util.nullSafeArrayCopy(
                rendererTrackGroups[rendererCapabilities.length], unmappedTrackGroupCount));

    // Package up the track information and selections.
    MappedTrackInfo mappedTrackInfo =
        new MappedTrackInfo(
            rendererNames,
            rendererTrackTypes,
            rendererTrackGroupArrays,
            rendererMixedMimeTypeAdaptationSupports,
            rendererFormatSupports,
            unmappedTrackGroupArray);

    Pair<@NullableType RendererConfiguration[], @NullableType ExoTrackSelection[]> result =
        selectTracks(
            mappedTrackInfo,
            rendererFormatSupports,
            rendererMixedMimeTypeAdaptationSupports,
            periodId,
            timeline);

    Tracks tracks = TrackSelectionUtil.buildTracks(mappedTrackInfo, result.second);

    return new TrackSelectorResult(result.first, result.second, tracks, mappedTrackInfo);
  }

  /**
   * Given mapped track information, returns a track selection and configuration for each renderer.
   *
   * @param mappedTrackInfo Mapped track information.
   * @param rendererFormatSupports The {@link Capabilities} for each mapped track, indexed by
   *     renderer, track group and track (in that order).
   * @param rendererMixedMimeTypeAdaptationSupport The {@link AdaptiveSupport} for mixed MIME type
   *     adaptation for the renderer.
   * @param mediaPeriodId The {@link MediaPeriodId} of the period for which tracks are to be
   *     selected.
   * @param timeline The {@link Timeline} holding the period for which tracks are to be selected.
   * @return A pair consisting of the track selections and configurations for each renderer. A null
   *     configuration indicates the renderer should be disabled, in which case the track selection
   *     will also be null. A track selection may also be null for a non-disabled renderer if {@link
   *     RendererCapabilities#getTrackType()} is {@link C#TRACK_TYPE_NONE}.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected abstract Pair<@NullableType RendererConfiguration[], @NullableType ExoTrackSelection[]>
      selectTracks(
          MappedTrackInfo mappedTrackInfo,
          @Capabilities int[][][] rendererFormatSupports,
          @AdaptiveSupport int[] rendererMixedMimeTypeAdaptationSupport,
          MediaPeriodId mediaPeriodId,
          Timeline timeline)
          throws ExoPlaybackException;

  /**
   * Finds the renderer to which the provided {@link TrackGroup} should be mapped.
   *
   * <p>A {@link TrackGroup} is mapped to the renderer that reports the highest of (listed in
   * decreasing order of support) {@link C#FORMAT_HANDLED}, {@link C#FORMAT_EXCEEDS_CAPABILITIES},
   * {@link C#FORMAT_UNSUPPORTED_DRM} and {@link C#FORMAT_UNSUPPORTED_SUBTYPE}.
   *
   * <p>In the case that two or more renderers report the same level of support, the assignment
   * depends on {@code preferUnassociatedRenderer}.
   *
   * <ul>
   *   <li>If {@code preferUnassociatedRenderer} is false, the renderer with the lowest index is
   *       chosen regardless of how many other track groups are already mapped to this renderer.
   *   <li>If {@code preferUnassociatedRenderer} is true, the renderer with the lowest index and no
   *       other mapped track group is chosen, or the renderer with the lowest index if all
   *       available renderers have already mapped track groups.
   * </ul>
   *
   * <p>If all renderers report {@link C#FORMAT_UNSUPPORTED_TYPE} for all of the tracks in the
   * group, then {@code renderers.length} is returned to indicate that the group was not mapped to
   * any renderer.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderers.
   * @param group The track group to map to a renderer.
   * @param rendererTrackGroupCounts The number of already mapped track groups for each renderer.
   * @param preferUnassociatedRenderer Whether renderers unassociated to any track group should be
   *     preferred.
   * @return The index of the renderer to which the track group was mapped, or {@code
   *     renderers.length} if it was not mapped to any renderer.
   * @throws ExoPlaybackException If an error occurs finding a renderer.
   */
  private static int findRenderer(
      RendererCapabilities[] rendererCapabilities,
      TrackGroup group,
      int[] rendererTrackGroupCounts,
      boolean preferUnassociatedRenderer)
      throws ExoPlaybackException {
    int bestRendererIndex = rendererCapabilities.length;
    @FormatSupport int bestFormatSupportLevel = C.FORMAT_UNSUPPORTED_TYPE;
    boolean bestRendererIsUnassociated = true;
    for (int rendererIndex = 0; rendererIndex < rendererCapabilities.length; rendererIndex++) {
      RendererCapabilities rendererCapability = rendererCapabilities[rendererIndex];
      @FormatSupport int formatSupportLevel = C.FORMAT_UNSUPPORTED_TYPE;
      for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
        @FormatSupport
        int trackFormatSupportLevel =
            RendererCapabilities.getFormatSupport(
                rendererCapability.supportsFormat(group.getFormat(trackIndex)));
        formatSupportLevel = max(formatSupportLevel, trackFormatSupportLevel);
      }
      boolean rendererIsUnassociated = rendererTrackGroupCounts[rendererIndex] == 0;
      if (formatSupportLevel > bestFormatSupportLevel
          || (formatSupportLevel == bestFormatSupportLevel
              && preferUnassociatedRenderer
              && !bestRendererIsUnassociated
              && rendererIsUnassociated)) {
        bestRendererIndex = rendererIndex;
        bestFormatSupportLevel = formatSupportLevel;
        bestRendererIsUnassociated = rendererIsUnassociated;
      }
    }
    return bestRendererIndex;
  }

  /**
   * Calls {@link RendererCapabilities#supportsFormat} for each track in the specified {@link
   * TrackGroup}, returning the results in an array.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderer.
   * @param group The track group to evaluate.
   * @return An array containing {@link Capabilities} for each track in the group.
   * @throws ExoPlaybackException If an error occurs determining the format support.
   */
  private static @Capabilities int[] getFormatSupport(
      RendererCapabilities rendererCapabilities, TrackGroup group) throws ExoPlaybackException {
    @Capabilities int[] formatSupport = new int[group.length];
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
   * @return An array containing the {@link AdaptiveSupport} for mixed MIME type adaptation for the
   *     renderer.
   * @throws ExoPlaybackException If an error occurs determining the adaptation support.
   */
  private static @AdaptiveSupport int[] getMixedMimeTypeAdaptationSupports(
      RendererCapabilities[] rendererCapabilities) throws ExoPlaybackException {
    @AdaptiveSupport int[] mixedMimeTypeAdaptationSupport = new int[rendererCapabilities.length];
    for (int i = 0; i < mixedMimeTypeAdaptationSupport.length; i++) {
      mixedMimeTypeAdaptationSupport[i] = rendererCapabilities[i].supportsMixedMimeTypeAdaptation();
    }
    return mixedMimeTypeAdaptationSupport;
  }
}

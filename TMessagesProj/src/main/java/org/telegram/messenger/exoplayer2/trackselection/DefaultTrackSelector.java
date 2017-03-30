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

import android.content.Context;
import android.graphics.Point;
import android.text.TextUtils;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.ExoPlaybackException;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.RendererCapabilities;
import org.telegram.messenger.exoplayer2.source.TrackGroup;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link MappingTrackSelector} that allows configuration of common parameters. It is safe to call
 * the methods of this class from the application thread. See {@link Parameters#Parameters()} for
 * default selection parameters.
 */
public class DefaultTrackSelector extends MappingTrackSelector {

  /**
   * Holder for available configurations for the {@link DefaultTrackSelector}.
   */
  public static final class Parameters {

    // Audio.
    public final String preferredAudioLanguage;

    // Text.
    public final String preferredTextLanguage;

    // Video.
    public final boolean allowMixedMimeAdaptiveness;
    public final boolean allowNonSeamlessAdaptiveness;
    public final int maxVideoWidth;
    public final int maxVideoHeight;
    public final boolean exceedVideoConstraintsIfNecessary;
    public final int viewportWidth;
    public final int viewportHeight;
    public final boolean orientationMayChange;

    /**
     * Constructor with default selection parameters:
     * <ul>
     *   <li>No preferred audio language is set.</li>
     *   <li>No preferred text language is set.</li>
     *   <li>Adaptation between different mime types is not allowed.</li>
     *   <li>Non seamless adaptation is allowed.</li>
     *   <li>No max limit for video width/height.</li>
     *   <li>Video constraints are ignored if no supported selection can be made otherwise.</li>
     *   <li>No viewport width/height constraints are set.</li>
     * </ul>
     */
    public Parameters() {
      this(null, null, false, true, Integer.MAX_VALUE, Integer.MAX_VALUE, true, Integer.MAX_VALUE,
          Integer.MAX_VALUE, true);
    }

    /**
     * @param preferredAudioLanguage The preferred language for audio, as well as for forced text
     *     tracks as defined by RFC 5646. {@code null} to select the default track, or first track
     *     if there's no default.
     * @param preferredTextLanguage The preferred language for text tracks as defined by RFC 5646.
     *     {@code null} to select the default track, or first track if there's no default.
     * @param allowMixedMimeAdaptiveness Whether to allow selections to contain mixed mime types.
     * @param allowNonSeamlessAdaptiveness Whether non-seamless adaptation is allowed.
     * @param maxVideoWidth Maximum allowed video width.
     * @param maxVideoHeight Maximum allowed video height.
     * @param exceedVideoConstraintsIfNecessary True to ignore video constraints when no selections
     *     can be made otherwise. False to force constraints anyway.
     * @param viewportWidth Viewport width in pixels.
     * @param viewportHeight Viewport height in pixels.
     * @param orientationMayChange Whether orientation may change during playback.
     */
    public Parameters(String preferredAudioLanguage, String preferredTextLanguage,
        boolean allowMixedMimeAdaptiveness, boolean allowNonSeamlessAdaptiveness,
        int maxVideoWidth, int maxVideoHeight, boolean exceedVideoConstraintsIfNecessary,
        int viewportWidth, int viewportHeight, boolean orientationMayChange) {
      this.preferredAudioLanguage = preferredAudioLanguage;
      this.preferredTextLanguage = preferredTextLanguage;
      this.allowMixedMimeAdaptiveness = allowMixedMimeAdaptiveness;
      this.allowNonSeamlessAdaptiveness = allowNonSeamlessAdaptiveness;
      this.maxVideoWidth = maxVideoWidth;
      this.maxVideoHeight = maxVideoHeight;
      this.exceedVideoConstraintsIfNecessary = exceedVideoConstraintsIfNecessary;
      this.viewportWidth = viewportWidth;
      this.viewportHeight = viewportHeight;
      this.orientationMayChange = orientationMayChange;
    }

    /**
     * Returns a {@link Parameters} instance with the provided preferred language for audio and
     * forced text tracks.
     *
     * @param preferredAudioLanguage The preferred language as defined by RFC 5646. {@code null} to
     *     select the default track, or first track if there's no default.
     * @return A {@link Parameters} instance with the provided preferred language for audio and
     *     forced text tracks.
     */
    public Parameters withPreferredAudioLanguage(String preferredAudioLanguage) {
      preferredAudioLanguage = Util.normalizeLanguageCode(preferredAudioLanguage);
      if (TextUtils.equals(preferredAudioLanguage, this.preferredAudioLanguage)) {
        return this;
      }
      return new Parameters(preferredAudioLanguage, preferredTextLanguage,
          allowMixedMimeAdaptiveness, allowNonSeamlessAdaptiveness, maxVideoWidth, maxVideoHeight,
          exceedVideoConstraintsIfNecessary, viewportWidth, viewportHeight, orientationMayChange);
    }

    /**
     * Returns a {@link Parameters} instance with the provided preferred language for text tracks.
     *
     * @param preferredTextLanguage The preferred language as defined by RFC 5646. {@code null} to
     *     select the default track, or no track if there's no default.
     * @return A {@link Parameters} instance with the provided preferred language for text tracks.
     */
    public Parameters withPreferredTextLanguage(String preferredTextLanguage) {
      preferredTextLanguage = Util.normalizeLanguageCode(preferredTextLanguage);
      if (TextUtils.equals(preferredTextLanguage, this.preferredTextLanguage)) {
        return this;
      }
      return new Parameters(preferredAudioLanguage, preferredTextLanguage,
          allowMixedMimeAdaptiveness, allowNonSeamlessAdaptiveness, maxVideoWidth,
          maxVideoHeight, exceedVideoConstraintsIfNecessary, viewportWidth, viewportHeight,
          orientationMayChange);
    }

    /**
     * Returns a {@link Parameters} instance with the provided mixed mime adaptiveness allowance.
     *
     * @param allowMixedMimeAdaptiveness Whether to allow selections to contain mixed mime types.
     * @return A {@link Parameters} instance with the provided mixed mime adaptiveness allowance.
     */
    public Parameters withAllowMixedMimeAdaptiveness(boolean allowMixedMimeAdaptiveness) {
      if (allowMixedMimeAdaptiveness == this.allowMixedMimeAdaptiveness) {
        return this;
      }
      return new Parameters(preferredAudioLanguage, preferredTextLanguage,
          allowMixedMimeAdaptiveness, allowNonSeamlessAdaptiveness, maxVideoWidth,
          maxVideoHeight, exceedVideoConstraintsIfNecessary, viewportWidth, viewportHeight,
          orientationMayChange);
    }

    /**
     * Returns a {@link Parameters} instance with the provided seamless adaptiveness allowance.
     *
     * @param allowNonSeamlessAdaptiveness Whether non-seamless adaptation is allowed.
     * @return A {@link Parameters} instance with the provided seamless adaptiveness allowance.
     */
    public Parameters withAllowNonSeamlessAdaptiveness(boolean allowNonSeamlessAdaptiveness) {
      if (allowNonSeamlessAdaptiveness == this.allowNonSeamlessAdaptiveness) {
        return this;
      }
      return new Parameters(preferredAudioLanguage, preferredTextLanguage,
          allowMixedMimeAdaptiveness, allowNonSeamlessAdaptiveness, maxVideoWidth,
          maxVideoHeight, exceedVideoConstraintsIfNecessary, viewportWidth, viewportHeight,
          orientationMayChange);
    }

    /**
     * Returns a {@link Parameters} instance with the provided max video size.
     *
     * @param maxVideoWidth The max video width.
     * @param maxVideoHeight The max video width.
     * @return A {@link Parameters} instance with the provided max video size.
     */
    public Parameters withMaxVideoSize(int maxVideoWidth, int maxVideoHeight) {
      if (maxVideoWidth == this.maxVideoWidth && maxVideoHeight == this.maxVideoHeight) {
        return this;
      }
      return new Parameters(preferredAudioLanguage, preferredTextLanguage,
          allowMixedMimeAdaptiveness, allowNonSeamlessAdaptiveness, maxVideoWidth,
          maxVideoHeight, exceedVideoConstraintsIfNecessary, viewportWidth, viewportHeight,
          orientationMayChange);
    }

    /**
     * Equivalent to {@code withMaxVideoSize(1279, 719)}.
     *
     * @return A {@link Parameters} instance with maximum standard definition as maximum video size.
     */
    public Parameters withMaxVideoSizeSd() {
      return withMaxVideoSize(1279, 719);
    }

    /**
     * Equivalent to {@code withMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)}.
     *
     * @return A {@link Parameters} instance without video size constraints.
     */
    public Parameters withoutVideoSizeConstraints() {
      return withMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Returns a {@link Parameters} instance with the provided
     * {@code exceedVideoConstraintsIfNecessary} value.
     *
     * @param exceedVideoConstraintsIfNecessary True to ignore video constraints when no selections
     *     can be made otherwise. False to force constraints anyway.
     * @return A {@link Parameters} instance with the provided
     *     {@code exceedVideoConstraintsIfNecessary} value.
     */
    public Parameters withExceedVideoConstraintsIfNecessary(
        boolean exceedVideoConstraintsIfNecessary) {
      if (exceedVideoConstraintsIfNecessary == this.exceedVideoConstraintsIfNecessary) {
        return this;
      }
      return new Parameters(preferredAudioLanguage, preferredTextLanguage,
          allowMixedMimeAdaptiveness, allowNonSeamlessAdaptiveness, maxVideoWidth,
          maxVideoHeight, exceedVideoConstraintsIfNecessary, viewportWidth, viewportHeight,
          orientationMayChange);
    }

    /**
     * Returns a {@link Parameters} instance with the provided viewport size.
     *
     * @param viewportWidth Viewport width in pixels.
     * @param viewportHeight Viewport height in pixels.
     * @param orientationMayChange Whether orientation may change during playback.
     * @return A {@link Parameters} instance with the provided viewport size.
     */
    public Parameters withViewportSize(int viewportWidth, int viewportHeight,
        boolean orientationMayChange) {
      if (viewportWidth == this.viewportWidth && viewportHeight == this.viewportHeight
          && orientationMayChange == this.orientationMayChange) {
        return this;
      }
      return new Parameters(preferredAudioLanguage, preferredTextLanguage,
          allowMixedMimeAdaptiveness, allowNonSeamlessAdaptiveness, maxVideoWidth,
          maxVideoHeight, exceedVideoConstraintsIfNecessary, viewportWidth, viewportHeight,
          orientationMayChange);
    }

    /**
     * Returns a {@link Parameters} instance where the viewport size is obtained from the provided
     * {@link Context}.
     *
     * @param context The context to obtain the viewport size from.
     * @param orientationMayChange Whether orientation may change during playback.
     * @return A {@link Parameters} instance where the viewport size is obtained from the provided
     *     {@link Context}.
     */
    public Parameters withViewportSizeFromContext(Context context, boolean orientationMayChange) {
      // Assume the viewport is fullscreen.
      Point viewportSize = Util.getPhysicalDisplaySize(context);
      return withViewportSize(viewportSize.x, viewportSize.y, orientationMayChange);
    }

    /**
     * Equivalent to {@code withViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE, true)}.
     *
     * @return A {@link Parameters} instance without viewport size constraints.
     */
    public Parameters withoutViewportSizeConstraints() {
      return withViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE, true);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      Parameters other = (Parameters) obj;
      return allowMixedMimeAdaptiveness == other.allowMixedMimeAdaptiveness
          && allowNonSeamlessAdaptiveness == other.allowNonSeamlessAdaptiveness
          && maxVideoWidth == other.maxVideoWidth && maxVideoHeight == other.maxVideoHeight
          && exceedVideoConstraintsIfNecessary == other.exceedVideoConstraintsIfNecessary
          && orientationMayChange == other.orientationMayChange
          && viewportWidth == other.viewportWidth && viewportHeight == other.viewportHeight
          && TextUtils.equals(preferredAudioLanguage, other.preferredAudioLanguage)
          && TextUtils.equals(preferredTextLanguage, other.preferredTextLanguage);
    }

    @Override
    public int hashCode() {
      int result = preferredAudioLanguage.hashCode();
      result = 31 * result + preferredTextLanguage.hashCode();
      result = 31 * result + (allowMixedMimeAdaptiveness ? 1 : 0);
      result = 31 * result + (allowNonSeamlessAdaptiveness ? 1 : 0);
      result = 31 * result + maxVideoWidth;
      result = 31 * result + maxVideoHeight;
      result = 31 * result + (exceedVideoConstraintsIfNecessary ? 1 : 0);
      result = 31 * result + (orientationMayChange ? 1 : 0);
      result = 31 * result + viewportWidth;
      result = 31 * result + viewportHeight;
      return result;
    }

  }

  /**
   * If a dimension (i.e. width or height) of a video is greater or equal to this fraction of the
   * corresponding viewport dimension, then the video is considered as filling the viewport (in that
   * dimension).
   */
  private static final float FRACTION_TO_CONSIDER_FULLSCREEN = 0.98f;
  private static final int[] NO_TRACKS = new int[0];

  private final TrackSelection.Factory adaptiveVideoTrackSelectionFactory;
  private final AtomicReference<Parameters> params;

  /**
   * Constructs an instance that does not support adaptive video.
   */
  public DefaultTrackSelector() {
    this(null);
  }

  /**
   * Constructs an instance that uses a factory to create adaptive video track selections.
   *
   * @param adaptiveVideoTrackSelectionFactory A factory for adaptive video {@link TrackSelection}s,
   *     or null if the selector should not support adaptive video.
   */
  public DefaultTrackSelector(TrackSelection.Factory adaptiveVideoTrackSelectionFactory) {
    this.adaptiveVideoTrackSelectionFactory = adaptiveVideoTrackSelectionFactory;
    params = new AtomicReference<>(new Parameters());
  }

  /**
   * Atomically sets the provided parameters for track selection.
   *
   * @param params The parameters for track selection.
   */
  public void setParameters(Parameters params) {
    if (!this.params.get().equals(params)) {
      this.params.set(Assertions.checkNotNull(params));
      invalidate();
    }
  }

  /**
   * Gets the current selection parameters.
   *
   * @return The current selection parameters.
   */
  public Parameters getParameters() {
    return params.get();
  }

  // MappingTrackSelector implementation.

  @Override
  protected TrackSelection[] selectTracks(RendererCapabilities[] rendererCapabilities,
      TrackGroupArray[] rendererTrackGroupArrays, int[][][] rendererFormatSupports)
      throws ExoPlaybackException {
    // Make a track selection for each renderer.
    TrackSelection[] rendererTrackSelections = new TrackSelection[rendererCapabilities.length];
    Parameters params = this.params.get();
    for (int i = 0; i < rendererCapabilities.length; i++) {
      switch (rendererCapabilities[i].getTrackType()) {
        case C.TRACK_TYPE_VIDEO:
          rendererTrackSelections[i] = selectVideoTrack(rendererCapabilities[i],
              rendererTrackGroupArrays[i], rendererFormatSupports[i], params.maxVideoWidth,
              params.maxVideoHeight, params.allowNonSeamlessAdaptiveness,
              params.allowMixedMimeAdaptiveness, params.viewportWidth, params.viewportHeight,
              params.orientationMayChange, adaptiveVideoTrackSelectionFactory,
              params.exceedVideoConstraintsIfNecessary);
          break;
        case C.TRACK_TYPE_AUDIO:
          rendererTrackSelections[i] = selectAudioTrack(rendererTrackGroupArrays[i],
              rendererFormatSupports[i], params.preferredAudioLanguage);
          break;
        case C.TRACK_TYPE_TEXT:
          rendererTrackSelections[i] = selectTextTrack(rendererTrackGroupArrays[i],
              rendererFormatSupports[i], params.preferredTextLanguage,
              params.preferredAudioLanguage);
          break;
        default:
          rendererTrackSelections[i] = selectOtherTrack(rendererCapabilities[i].getTrackType(),
              rendererTrackGroupArrays[i], rendererFormatSupports[i]);
          break;
      }
    }
    return rendererTrackSelections;
  }

  // Video track selection implementation.

  protected TrackSelection selectVideoTrack(RendererCapabilities rendererCapabilities,
      TrackGroupArray groups, int[][] formatSupport, int maxVideoWidth, int maxVideoHeight,
      boolean allowNonSeamlessAdaptiveness, boolean allowMixedMimeAdaptiveness, int viewportWidth,
      int viewportHeight, boolean orientationMayChange,
      TrackSelection.Factory adaptiveVideoTrackSelectionFactory,
      boolean exceedConstraintsIfNecessary) throws ExoPlaybackException {
    TrackSelection selection = null;
    if (adaptiveVideoTrackSelectionFactory != null) {
      selection = selectAdaptiveVideoTrack(rendererCapabilities, groups, formatSupport,
          maxVideoWidth, maxVideoHeight, allowNonSeamlessAdaptiveness,
          allowMixedMimeAdaptiveness, viewportWidth, viewportHeight,
          orientationMayChange, adaptiveVideoTrackSelectionFactory);
    }
    if (selection == null) {
      selection = selectFixedVideoTrack(groups, formatSupport, maxVideoWidth, maxVideoHeight,
          viewportWidth, viewportHeight, orientationMayChange, exceedConstraintsIfNecessary);
    }
    return selection;
  }

  private static TrackSelection selectAdaptiveVideoTrack(RendererCapabilities rendererCapabilities,
      TrackGroupArray groups, int[][] formatSupport, int maxVideoWidth, int maxVideoHeight,
      boolean allowNonSeamlessAdaptiveness, boolean allowMixedMimeAdaptiveness, int viewportWidth,
      int viewportHeight, boolean orientationMayChange,
      TrackSelection.Factory adaptiveVideoTrackSelectionFactory) throws ExoPlaybackException {
    int requiredAdaptiveSupport = allowNonSeamlessAdaptiveness
        ? (RendererCapabilities.ADAPTIVE_NOT_SEAMLESS | RendererCapabilities.ADAPTIVE_SEAMLESS)
        : RendererCapabilities.ADAPTIVE_SEAMLESS;
    boolean allowMixedMimeTypes = allowMixedMimeAdaptiveness
        && (rendererCapabilities.supportsMixedMimeTypeAdaptation() & requiredAdaptiveSupport) != 0;
    for (int i = 0; i < groups.length; i++) {
      TrackGroup group = groups.get(i);
      int[] adaptiveTracks = getAdaptiveTracksForGroup(group, formatSupport[i],
          allowMixedMimeTypes, requiredAdaptiveSupport, maxVideoWidth, maxVideoHeight,
          viewportWidth, viewportHeight, orientationMayChange);
      if (adaptiveTracks.length > 0) {
        return adaptiveVideoTrackSelectionFactory.createTrackSelection(group, adaptiveTracks);
      }
    }
    return null;
  }

  private static int[] getAdaptiveTracksForGroup(TrackGroup group, int[] formatSupport,
      boolean allowMixedMimeTypes, int requiredAdaptiveSupport, int maxVideoWidth,
      int maxVideoHeight, int viewportWidth, int viewportHeight, boolean orientationMayChange) {
    if (group.length < 2) {
      return NO_TRACKS;
    }

    List<Integer> selectedTrackIndices = getViewportFilteredTrackIndices(group, viewportWidth,
        viewportHeight, orientationMayChange);
    if (selectedTrackIndices.size() < 2) {
      return NO_TRACKS;
    }

    String selectedMimeType = null;
    if (!allowMixedMimeTypes) {
      // Select the mime type for which we have the most adaptive tracks.
      HashSet<String> seenMimeTypes = new HashSet<>();
      int selectedMimeTypeTrackCount = 0;
      for (int i = 0; i < selectedTrackIndices.size(); i++) {
        int trackIndex = selectedTrackIndices.get(i);
        String sampleMimeType = group.getFormat(trackIndex).sampleMimeType;
        if (!seenMimeTypes.contains(sampleMimeType)) {
          seenMimeTypes.add(sampleMimeType);
          int countForMimeType = getAdaptiveTrackCountForMimeType(group, formatSupport,
              requiredAdaptiveSupport, sampleMimeType, maxVideoWidth, maxVideoHeight,
              selectedTrackIndices);
          if (countForMimeType > selectedMimeTypeTrackCount) {
            selectedMimeType = sampleMimeType;
            selectedMimeTypeTrackCount = countForMimeType;
          }
        }
      }
    }

    // Filter by the selected mime type.
    filterAdaptiveTrackCountForMimeType(group, formatSupport, requiredAdaptiveSupport,
        selectedMimeType, maxVideoWidth, maxVideoHeight, selectedTrackIndices);

    return selectedTrackIndices.size() < 2 ? NO_TRACKS : Util.toArray(selectedTrackIndices);
  }

  private static int getAdaptiveTrackCountForMimeType(TrackGroup group, int[] formatSupport,
      int requiredAdaptiveSupport, String mimeType, int maxVideoWidth, int maxVideoHeight,
      List<Integer> selectedTrackIndices) {
    int adaptiveTrackCount = 0;
    for (int i = 0; i < selectedTrackIndices.size(); i++) {
      int trackIndex = selectedTrackIndices.get(i);
      if (isSupportedAdaptiveVideoTrack(group.getFormat(trackIndex), mimeType,
          formatSupport[trackIndex], requiredAdaptiveSupport, maxVideoWidth, maxVideoHeight)) {
        adaptiveTrackCount++;
      }
    }
    return adaptiveTrackCount;
  }

  private static void filterAdaptiveTrackCountForMimeType(TrackGroup group, int[] formatSupport,
      int requiredAdaptiveSupport, String mimeType, int maxVideoWidth, int maxVideoHeight,
      List<Integer> selectedTrackIndices) {
    for (int i = selectedTrackIndices.size() - 1; i >= 0; i--) {
      int trackIndex = selectedTrackIndices.get(i);
      if (!isSupportedAdaptiveVideoTrack(group.getFormat(trackIndex), mimeType,
          formatSupport[trackIndex], requiredAdaptiveSupport, maxVideoWidth, maxVideoHeight)) {
        selectedTrackIndices.remove(i);
      }
    }
  }

  private static boolean isSupportedAdaptiveVideoTrack(Format format, String mimeType,
      int formatSupport, int requiredAdaptiveSupport, int maxVideoWidth, int maxVideoHeight) {
    return isSupported(formatSupport) && ((formatSupport & requiredAdaptiveSupport) != 0)
        && (mimeType == null || Util.areEqual(format.sampleMimeType, mimeType))
        && (format.width == Format.NO_VALUE || format.width <= maxVideoWidth)
        && (format.height == Format.NO_VALUE || format.height <= maxVideoHeight);
  }

  private static TrackSelection selectFixedVideoTrack(TrackGroupArray groups,
      int[][] formatSupport, int maxVideoWidth, int maxVideoHeight, int viewportWidth,
      int viewportHeight, boolean orientationMayChange, boolean exceedConstraintsIfNecessary) {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    int selectedPixelCount = Format.NO_VALUE;
    boolean selectedIsWithinConstraints = false;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup group = groups.get(groupIndex);
      List<Integer> selectedTrackIndices = getViewportFilteredTrackIndices(group, viewportWidth,
          viewportHeight, orientationMayChange);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex])) {
          Format format = group.getFormat(trackIndex);
          boolean isWithinConstraints = selectedTrackIndices.contains(trackIndex)
              && (format.width == Format.NO_VALUE || format.width <= maxVideoWidth)
              && (format.height == Format.NO_VALUE || format.height <= maxVideoHeight);
          int pixelCount = format.getPixelCount();
          boolean selectTrack;
          if (selectedIsWithinConstraints) {
            selectTrack = isWithinConstraints
                && comparePixelCounts(pixelCount, selectedPixelCount) > 0;
          } else {
            selectTrack = isWithinConstraints || (exceedConstraintsIfNecessary
                && (selectedGroup == null
                || comparePixelCounts(pixelCount, selectedPixelCount) < 0));
          }
          if (selectTrack) {
            selectedGroup = group;
            selectedTrackIndex = trackIndex;
            selectedPixelCount = pixelCount;
            selectedIsWithinConstraints = isWithinConstraints;
          }
        }
      }
    }
    return selectedGroup == null ? null
        : new FixedTrackSelection(selectedGroup, selectedTrackIndex);
  }

  /**
   * Compares two pixel counts for order. A known pixel count is considered greater than
   * {@link Format#NO_VALUE}.
   *
   * @param first The first pixel count.
   * @param second The second pixel count.
   * @return A negative integer if the first pixel count is less than the second. Zero if they are
   *     equal. A positive integer if the first pixel count is greater than the second.
   */
  private static int comparePixelCounts(int first, int second) {
    return first == Format.NO_VALUE ? (second == Format.NO_VALUE ? 0 : -1)
        : (second == Format.NO_VALUE ? 1 : (first - second));
  }


  // Audio track selection implementation.

  protected TrackSelection selectAudioTrack(TrackGroupArray groups, int[][] formatSupport,
      String preferredAudioLanguage) {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    int selectedTrackScore = 0;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex])) {
          Format format = trackGroup.getFormat(trackIndex);
          boolean isDefault = (format.selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0;
          int trackScore;
          if (formatHasLanguage(format, preferredAudioLanguage)) {
            if (isDefault) {
              trackScore = 4;
            } else {
              trackScore = 3;
            }
          } else if (isDefault) {
            trackScore = 2;
          } else {
            trackScore = 1;
          }
          if (trackScore > selectedTrackScore) {
            selectedGroup = trackGroup;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
          }
        }
      }
    }
    return selectedGroup == null ? null
        : new FixedTrackSelection(selectedGroup, selectedTrackIndex);
  }

  // Text track selection implementation.

  protected TrackSelection selectTextTrack(TrackGroupArray groups, int[][] formatSupport,
      String preferredTextLanguage, String preferredAudioLanguage) {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    int selectedTrackScore = 0;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex])) {
          Format format = trackGroup.getFormat(trackIndex);
          boolean isDefault = (format.selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0;
          boolean isForced = (format.selectionFlags & C.SELECTION_FLAG_FORCED) != 0;
          int trackScore;
          if (formatHasLanguage(format, preferredTextLanguage)) {
            if (isDefault) {
              trackScore = 6;
            } else if (!isForced) {
              // Prefer non-forced to forced if a preferred text language has been specified. Where
              // both are provided the non-forced track will usually contain the forced subtitles as
              // a subset.
              trackScore = 5;
            } else {
              trackScore = 4;
            }
          } else if (isDefault) {
            trackScore = 3;
          } else if (isForced) {
            if (formatHasLanguage(format, preferredAudioLanguage)) {
              trackScore = 2;
            } else {
              trackScore = 1;
            }
          } else {
            trackScore = 0;
          }
          if (trackScore > selectedTrackScore) {
            selectedGroup = trackGroup;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
          }
        }
      }
    }
    return selectedGroup == null ? null
        : new FixedTrackSelection(selectedGroup, selectedTrackIndex);
  }

  // General track selection methods.

  protected TrackSelection selectOtherTrack(int trackType, TrackGroupArray groups,
      int[][] formatSupport) {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    int selectedTrackScore = 0;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex])) {
          Format format = trackGroup.getFormat(trackIndex);
          boolean isDefault = (format.selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0;
          int trackScore = isDefault ? 2 : 1;
          if (trackScore > selectedTrackScore) {
            selectedGroup = trackGroup;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
          }
        }
      }
    }
    return selectedGroup == null ? null
        : new FixedTrackSelection(selectedGroup, selectedTrackIndex);
  }

  private static boolean isSupported(int formatSupport) {
    return (formatSupport & RendererCapabilities.FORMAT_SUPPORT_MASK)
        == RendererCapabilities.FORMAT_HANDLED;
  }

  private static boolean formatHasLanguage(Format format, String language) {
    return language != null && language.equals(Util.normalizeLanguageCode(format.language));
  }

  // Viewport size util methods.

  private static List<Integer> getViewportFilteredTrackIndices(TrackGroup group, int viewportWidth,
      int viewportHeight, boolean orientationMayChange) {
    // Initially include all indices.
    ArrayList<Integer> selectedTrackIndices = new ArrayList<>(group.length);
    for (int i = 0; i < group.length; i++) {
      selectedTrackIndices.add(i);
    }

    if (viewportWidth == Integer.MAX_VALUE || viewportHeight == Integer.MAX_VALUE) {
      // Viewport dimensions not set. Return the full set of indices.
      return selectedTrackIndices;
    }

    int maxVideoPixelsToRetain = Integer.MAX_VALUE;
    for (int i = 0; i < group.length; i++) {
      Format format = group.getFormat(i);
      // Keep track of the number of pixels of the selected format whose resolution is the
      // smallest to exceed the maximum size at which it can be displayed within the viewport.
      // We'll discard formats of higher resolution.
      if (format.width > 0 && format.height > 0) {
        Point maxVideoSizeInViewport = getMaxVideoSizeInViewport(orientationMayChange,
            viewportWidth, viewportHeight, format.width, format.height);
        int videoPixels = format.width * format.height;
        if (format.width >= (int) (maxVideoSizeInViewport.x * FRACTION_TO_CONSIDER_FULLSCREEN)
            && format.height >= (int) (maxVideoSizeInViewport.y * FRACTION_TO_CONSIDER_FULLSCREEN)
            && videoPixels < maxVideoPixelsToRetain) {
          maxVideoPixelsToRetain = videoPixels;
        }
      }
    }

    // Filter out formats that exceed maxVideoPixelsToRetain. These formats have an unnecessarily
    // high resolution given the size at which the video will be displayed within the viewport. Also
    // filter out formats with unknown dimensions, since we have some whose dimensions are known.
    if (maxVideoPixelsToRetain != Integer.MAX_VALUE) {
      for (int i = selectedTrackIndices.size() - 1; i >= 0; i--) {
        Format format = group.getFormat(selectedTrackIndices.get(i));
        int pixelCount = format.getPixelCount();
        if (pixelCount == Format.NO_VALUE || pixelCount > maxVideoPixelsToRetain) {
          selectedTrackIndices.remove(i);
        }
      }
    }

    return selectedTrackIndices;
  }

  /**
   * Given viewport dimensions and video dimensions, computes the maximum size of the video as it
   * will be rendered to fit inside of the viewport.
   */
  private static Point getMaxVideoSizeInViewport(boolean orientationMayChange, int viewportWidth,
      int viewportHeight, int videoWidth, int videoHeight) {
    if (orientationMayChange && (videoWidth > videoHeight) != (viewportWidth > viewportHeight)) {
      // Rotation is allowed, and the video will be larger in the rotated viewport.
      int tempViewportWidth = viewportWidth;
      viewportWidth = viewportHeight;
      viewportHeight = tempViewportWidth;
    }

    if (videoWidth * viewportHeight >= videoHeight * viewportWidth) {
      // Horizontal letter-boxing along top and bottom.
      return new Point(viewportWidth, Util.ceilDivide(viewportWidth * videoHeight, videoWidth));
    } else {
      // Vertical letter-boxing along edges.
      return new Point(Util.ceilDivide(viewportHeight * videoWidth, videoHeight), viewportHeight);
    }
  }

}

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
    public final int maxVideoBitrate;
    public final boolean exceedVideoConstraintsIfNecessary;
    public final boolean exceedRendererCapabilitiesIfNecessary;
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
     *   <li>No max video bitrate.</li>
     *   <li>Video constraints are exceeded if no supported selection can be made otherwise.</li>
     *   <li>Renderer capabilities are exceeded if no supported selection can be made.</li>
     *   <li>No viewport width/height constraints are set.</li>
     * </ul>
     */
    public Parameters() {
      this(null, null, false, true, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, true,
          true, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
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
     * @param maxVideoBitrate Maximum allowed video bitrate.
     * @param exceedVideoConstraintsIfNecessary Whether to exceed video constraints when no
     *     selection can be made otherwise.
     * @param exceedRendererCapabilitiesIfNecessary Whether to exceed renderer capabilities when no
     *     selection can be made otherwise.
     * @param viewportWidth Viewport width in pixels.
     * @param viewportHeight Viewport height in pixels.
     * @param orientationMayChange Whether orientation may change during playback.
     */
    public Parameters(String preferredAudioLanguage, String preferredTextLanguage,
        boolean allowMixedMimeAdaptiveness, boolean allowNonSeamlessAdaptiveness,
        int maxVideoWidth, int maxVideoHeight, int maxVideoBitrate,
        boolean exceedVideoConstraintsIfNecessary, boolean exceedRendererCapabilitiesIfNecessary,
        int viewportWidth, int viewportHeight, boolean orientationMayChange) {
      this.preferredAudioLanguage = preferredAudioLanguage;
      this.preferredTextLanguage = preferredTextLanguage;
      this.allowMixedMimeAdaptiveness = allowMixedMimeAdaptiveness;
      this.allowNonSeamlessAdaptiveness = allowNonSeamlessAdaptiveness;
      this.maxVideoWidth = maxVideoWidth;
      this.maxVideoHeight = maxVideoHeight;
      this.maxVideoBitrate = maxVideoBitrate;
      this.exceedVideoConstraintsIfNecessary = exceedVideoConstraintsIfNecessary;
      this.exceedRendererCapabilitiesIfNecessary = exceedRendererCapabilitiesIfNecessary;
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
          maxVideoBitrate, exceedVideoConstraintsIfNecessary, exceedRendererCapabilitiesIfNecessary,
          viewportWidth, viewportHeight, orientationMayChange);
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
          allowMixedMimeAdaptiveness, allowNonSeamlessAdaptiveness, maxVideoWidth, maxVideoHeight,
          maxVideoBitrate, exceedVideoConstraintsIfNecessary, exceedRendererCapabilitiesIfNecessary,
          viewportWidth, viewportHeight, orientationMayChange);
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
          allowMixedMimeAdaptiveness, allowNonSeamlessAdaptiveness, maxVideoWidth, maxVideoHeight,
          maxVideoBitrate, exceedVideoConstraintsIfNecessary, exceedRendererCapabilitiesIfNecessary,
          viewportWidth, viewportHeight, orientationMayChange);
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
          allowMixedMimeAdaptiveness, allowNonSeamlessAdaptiveness, maxVideoWidth, maxVideoHeight,
          maxVideoBitrate, exceedVideoConstraintsIfNecessary, exceedRendererCapabilitiesIfNecessary,
          viewportWidth, viewportHeight, orientationMayChange);
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
          allowMixedMimeAdaptiveness, allowNonSeamlessAdaptiveness, maxVideoWidth, maxVideoHeight,
          maxVideoBitrate, exceedVideoConstraintsIfNecessary, exceedRendererCapabilitiesIfNecessary,
          viewportWidth, viewportHeight, orientationMayChange);
    }

    /**
     * Returns a {@link Parameters} instance with the provided max video bitrate.
     *
     * @param maxVideoBitrate The max video bitrate.
     * @return A {@link Parameters} instance with the provided max video bitrate.
     */
    public Parameters withMaxVideoBitrate(int maxVideoBitrate) {
      if (maxVideoBitrate == this.maxVideoBitrate) {
        return this;
      }
      return new Parameters(preferredAudioLanguage, preferredTextLanguage,
          allowMixedMimeAdaptiveness, allowNonSeamlessAdaptiveness, maxVideoWidth, maxVideoHeight,
          maxVideoBitrate, exceedVideoConstraintsIfNecessary, exceedRendererCapabilitiesIfNecessary,
          viewportWidth, viewportHeight, orientationMayChange);
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
     * @param exceedVideoConstraintsIfNecessary Whether to exceed video constraints when no
     *     selection can be made otherwise.
     * @return A {@link Parameters} instance with the provided
     *     {@code exceedVideoConstraintsIfNecessary} value.
     */
    public Parameters withExceedVideoConstraintsIfNecessary(
        boolean exceedVideoConstraintsIfNecessary) {
      if (exceedVideoConstraintsIfNecessary == this.exceedVideoConstraintsIfNecessary) {
        return this;
      }
      return new Parameters(preferredAudioLanguage, preferredTextLanguage,
          allowMixedMimeAdaptiveness, allowNonSeamlessAdaptiveness, maxVideoWidth, maxVideoHeight,
          maxVideoBitrate, exceedVideoConstraintsIfNecessary, exceedRendererCapabilitiesIfNecessary,
          viewportWidth, viewportHeight, orientationMayChange);
    }

    /**
     * Returns a {@link Parameters} instance with the provided
     * {@code exceedRendererCapabilitiesIfNecessary} value.
     *
     * @param exceedRendererCapabilitiesIfNecessary Whether to exceed renderer capabilities when no
     *     selection can be made otherwise.
     * @return A {@link Parameters} instance with the provided
     *     {@code exceedRendererCapabilitiesIfNecessary} value.
     */
    public Parameters withExceedRendererCapabilitiesIfNecessary(
        boolean exceedRendererCapabilitiesIfNecessary) {
      if (exceedRendererCapabilitiesIfNecessary == this.exceedRendererCapabilitiesIfNecessary) {
        return this;
      }
      return new Parameters(preferredAudioLanguage, preferredTextLanguage,
          allowMixedMimeAdaptiveness, allowNonSeamlessAdaptiveness, maxVideoWidth, maxVideoHeight,
          maxVideoBitrate, exceedVideoConstraintsIfNecessary, exceedRendererCapabilitiesIfNecessary,
          viewportWidth, viewportHeight, orientationMayChange);
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
          allowMixedMimeAdaptiveness, allowNonSeamlessAdaptiveness, maxVideoWidth, maxVideoHeight,
          maxVideoBitrate, exceedVideoConstraintsIfNecessary, exceedRendererCapabilitiesIfNecessary,
          viewportWidth, viewportHeight, orientationMayChange);
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
          && exceedRendererCapabilitiesIfNecessary == other.exceedRendererCapabilitiesIfNecessary
          && orientationMayChange == other.orientationMayChange
          && viewportWidth == other.viewportWidth && viewportHeight == other.viewportHeight
          && maxVideoBitrate == other.maxVideoBitrate
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
      result = 31 * result + maxVideoBitrate;
      result = 31 * result + (exceedVideoConstraintsIfNecessary ? 1 : 0);
      result = 31 * result + (exceedRendererCapabilitiesIfNecessary ? 1 : 0);
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
  private static final int WITHIN_RENDERER_CAPABILITIES_BONUS = 1000;

  private final TrackSelection.Factory adaptiveTrackSelectionFactory;
  private final AtomicReference<Parameters> paramsReference;

  /**
   * Constructs an instance that does not support adaptive tracks.
   */
  public DefaultTrackSelector() {
    this(null);
  }

  /**
   * Constructs an instance that uses a factory to create adaptive track selections.
   *
   * @param adaptiveTrackSelectionFactory A factory for adaptive {@link TrackSelection}s, or null if
   *     the selector should not support adaptive tracks.
   */
  public DefaultTrackSelector(TrackSelection.Factory adaptiveTrackSelectionFactory) {
    this.adaptiveTrackSelectionFactory = adaptiveTrackSelectionFactory;
    paramsReference = new AtomicReference<>(new Parameters());
  }

  /**
   * Atomically sets the provided parameters for track selection.
   *
   * @param params The parameters for track selection.
   */
  public void setParameters(Parameters params) {
    Assertions.checkNotNull(params);
    if (!paramsReference.getAndSet(params).equals(params)) {
      invalidate();
    }
  }

  /**
   * Gets the current selection parameters.
   *
   * @return The current selection parameters.
   */
  public Parameters getParameters() {
    return paramsReference.get();
  }

  // MappingTrackSelector implementation.

  @Override
  protected TrackSelection[] selectTracks(RendererCapabilities[] rendererCapabilities,
      TrackGroupArray[] rendererTrackGroupArrays, int[][][] rendererFormatSupports)
      throws ExoPlaybackException {
    // Make a track selection for each renderer.
    int rendererCount = rendererCapabilities.length;
    TrackSelection[] rendererTrackSelections = new TrackSelection[rendererCount];
    Parameters params = paramsReference.get();
    boolean videoTrackAndRendererPresent = false;

    for (int i = 0; i < rendererCount; i++) {
      if (C.TRACK_TYPE_VIDEO == rendererCapabilities[i].getTrackType()) {
        rendererTrackSelections[i] = selectVideoTrack(rendererCapabilities[i],
            rendererTrackGroupArrays[i], rendererFormatSupports[i], params.maxVideoWidth,
            params.maxVideoHeight, params.maxVideoBitrate, params.allowNonSeamlessAdaptiveness,
            params.allowMixedMimeAdaptiveness, params.viewportWidth, params.viewportHeight,
            params.orientationMayChange, adaptiveTrackSelectionFactory,
            params.exceedVideoConstraintsIfNecessary, params.exceedRendererCapabilitiesIfNecessary);
        videoTrackAndRendererPresent |= rendererTrackGroupArrays[i].length > 0;
      }
    }

    for (int i = 0; i < rendererCount; i++) {
      switch (rendererCapabilities[i].getTrackType()) {
        case C.TRACK_TYPE_VIDEO:
          // Already done. Do nothing.
          break;
        case C.TRACK_TYPE_AUDIO:
          rendererTrackSelections[i] = selectAudioTrack(rendererTrackGroupArrays[i],
              rendererFormatSupports[i], params.preferredAudioLanguage,
              params.exceedRendererCapabilitiesIfNecessary, params.allowMixedMimeAdaptiveness,
              videoTrackAndRendererPresent ? null : adaptiveTrackSelectionFactory);
          break;
        case C.TRACK_TYPE_TEXT:
          rendererTrackSelections[i] = selectTextTrack(rendererTrackGroupArrays[i],
              rendererFormatSupports[i], params.preferredTextLanguage,
              params.preferredAudioLanguage, params.exceedRendererCapabilitiesIfNecessary);
          break;
        default:
          rendererTrackSelections[i] = selectOtherTrack(rendererCapabilities[i].getTrackType(),
              rendererTrackGroupArrays[i], rendererFormatSupports[i],
              params.exceedRendererCapabilitiesIfNecessary);
          break;
      }
    }
    return rendererTrackSelections;
  }

  // Video track selection implementation.

  protected TrackSelection selectVideoTrack(RendererCapabilities rendererCapabilities,
      TrackGroupArray groups, int[][] formatSupport, int maxVideoWidth, int maxVideoHeight,
      int maxVideoBitrate, boolean allowNonSeamlessAdaptiveness, boolean allowMixedMimeAdaptiveness,
      int viewportWidth, int viewportHeight, boolean orientationMayChange,
      TrackSelection.Factory adaptiveTrackSelectionFactory, boolean exceedConstraintsIfNecessary,
      boolean exceedRendererCapabilitiesIfNecessary) throws ExoPlaybackException {
    TrackSelection selection = null;
    if (adaptiveTrackSelectionFactory != null) {
      selection = selectAdaptiveVideoTrack(rendererCapabilities, groups, formatSupport,
          maxVideoWidth, maxVideoHeight, maxVideoBitrate, allowNonSeamlessAdaptiveness,
          allowMixedMimeAdaptiveness, viewportWidth, viewportHeight,
          orientationMayChange, adaptiveTrackSelectionFactory);
    }
    if (selection == null) {
      selection = selectFixedVideoTrack(groups, formatSupport, maxVideoWidth, maxVideoHeight,
          maxVideoBitrate, viewportWidth, viewportHeight, orientationMayChange,
          exceedConstraintsIfNecessary, exceedRendererCapabilitiesIfNecessary);
    }
    return selection;
  }

  private static TrackSelection selectAdaptiveVideoTrack(RendererCapabilities rendererCapabilities,
      TrackGroupArray groups, int[][] formatSupport, int maxVideoWidth, int maxVideoHeight,
      int maxVideoBitrate, boolean allowNonSeamlessAdaptiveness, boolean allowMixedMimeAdaptiveness,
      int viewportWidth, int viewportHeight, boolean orientationMayChange,
      TrackSelection.Factory adaptiveTrackSelectionFactory) throws ExoPlaybackException {
    int requiredAdaptiveSupport = allowNonSeamlessAdaptiveness
        ? (RendererCapabilities.ADAPTIVE_NOT_SEAMLESS | RendererCapabilities.ADAPTIVE_SEAMLESS)
        : RendererCapabilities.ADAPTIVE_SEAMLESS;
    boolean allowMixedMimeTypes = allowMixedMimeAdaptiveness
        && (rendererCapabilities.supportsMixedMimeTypeAdaptation() & requiredAdaptiveSupport) != 0;
    for (int i = 0; i < groups.length; i++) {
      TrackGroup group = groups.get(i);
      int[] adaptiveTracks = getAdaptiveVideoTracksForGroup(group, formatSupport[i],
          allowMixedMimeTypes, requiredAdaptiveSupport, maxVideoWidth, maxVideoHeight,
          maxVideoBitrate, viewportWidth, viewportHeight, orientationMayChange);
      if (adaptiveTracks.length > 0) {
        return adaptiveTrackSelectionFactory.createTrackSelection(group, adaptiveTracks);
      }
    }
    return null;
  }

  private static int[] getAdaptiveVideoTracksForGroup(TrackGroup group, int[] formatSupport,
      boolean allowMixedMimeTypes, int requiredAdaptiveSupport, int maxVideoWidth,
      int maxVideoHeight, int maxVideoBitrate, int viewportWidth, int viewportHeight,
      boolean orientationMayChange) {
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
        if (seenMimeTypes.add(sampleMimeType)) {
          int countForMimeType = getAdaptiveVideoTrackCountForMimeType(group, formatSupport,
              requiredAdaptiveSupport, sampleMimeType, maxVideoWidth, maxVideoHeight,
              maxVideoBitrate, selectedTrackIndices);
          if (countForMimeType > selectedMimeTypeTrackCount) {
            selectedMimeType = sampleMimeType;
            selectedMimeTypeTrackCount = countForMimeType;
          }
        }
      }
    }

    // Filter by the selected mime type.
    filterAdaptiveVideoTrackCountForMimeType(group, formatSupport, requiredAdaptiveSupport,
        selectedMimeType, maxVideoWidth, maxVideoHeight, maxVideoBitrate, selectedTrackIndices);

    return selectedTrackIndices.size() < 2 ? NO_TRACKS : Util.toArray(selectedTrackIndices);
  }

  private static int getAdaptiveVideoTrackCountForMimeType(TrackGroup group, int[] formatSupport,
      int requiredAdaptiveSupport, String mimeType, int maxVideoWidth, int maxVideoHeight,
      int maxVideoBitrate, List<Integer> selectedTrackIndices) {
    int adaptiveTrackCount = 0;
    for (int i = 0; i < selectedTrackIndices.size(); i++) {
      int trackIndex = selectedTrackIndices.get(i);
      if (isSupportedAdaptiveVideoTrack(group.getFormat(trackIndex), mimeType,
          formatSupport[trackIndex], requiredAdaptiveSupport, maxVideoWidth, maxVideoHeight,
          maxVideoBitrate)) {
        adaptiveTrackCount++;
      }
    }
    return adaptiveTrackCount;
  }

  private static void filterAdaptiveVideoTrackCountForMimeType(TrackGroup group,
      int[] formatSupport, int requiredAdaptiveSupport, String mimeType, int maxVideoWidth,
      int maxVideoHeight, int maxVideoBitrate, List<Integer> selectedTrackIndices) {
    for (int i = selectedTrackIndices.size() - 1; i >= 0; i--) {
      int trackIndex = selectedTrackIndices.get(i);
      if (!isSupportedAdaptiveVideoTrack(group.getFormat(trackIndex), mimeType,
          formatSupport[trackIndex], requiredAdaptiveSupport, maxVideoWidth, maxVideoHeight,
          maxVideoBitrate)) {
        selectedTrackIndices.remove(i);
      }
    }
  }

  private static boolean isSupportedAdaptiveVideoTrack(Format format, String mimeType,
      int formatSupport, int requiredAdaptiveSupport, int maxVideoWidth, int maxVideoHeight,
      int maxVideoBitrate) {
    return isSupported(formatSupport, false) && ((formatSupport & requiredAdaptiveSupport) != 0)
        && (mimeType == null || Util.areEqual(format.sampleMimeType, mimeType))
        && (format.width == Format.NO_VALUE || format.width <= maxVideoWidth)
        && (format.height == Format.NO_VALUE || format.height <= maxVideoHeight)
        && (format.bitrate == Format.NO_VALUE || format.bitrate <= maxVideoBitrate);
  }

  private static TrackSelection selectFixedVideoTrack(TrackGroupArray groups,
      int[][] formatSupport, int maxVideoWidth, int maxVideoHeight, int maxVideoBitrate,
      int viewportWidth, int viewportHeight, boolean orientationMayChange,
      boolean exceedConstraintsIfNecessary, boolean exceedRendererCapabilitiesIfNecessary) {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    int selectedTrackScore = 0;
    int selectedBitrate = Format.NO_VALUE;
    int selectedPixelCount = Format.NO_VALUE;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      List<Integer> selectedTrackIndices = getViewportFilteredTrackIndices(trackGroup,
          viewportWidth, viewportHeight, orientationMayChange);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex], exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          boolean isWithinConstraints = selectedTrackIndices.contains(trackIndex)
              && (format.width == Format.NO_VALUE || format.width <= maxVideoWidth)
              && (format.height == Format.NO_VALUE || format.height <= maxVideoHeight)
              && (format.bitrate == Format.NO_VALUE || format.bitrate <= maxVideoBitrate);
          if (!isWithinConstraints && !exceedConstraintsIfNecessary) {
            // Track should not be selected.
            continue;
          }
          int trackScore = isWithinConstraints ? 2 : 1;
          if (isSupported(trackFormatSupport[trackIndex], false)) {
            trackScore += WITHIN_RENDERER_CAPABILITIES_BONUS;
          }
          boolean selectTrack = trackScore > selectedTrackScore;
          if (trackScore == selectedTrackScore) {
            // Use the pixel count as a tie breaker (or bitrate if pixel counts are tied). If we're
            // within constraints prefer a higher pixel count (or bitrate), else prefer a lower
            // count (or bitrate). If still tied then prefer the first track (i.e. the one that's
            // already selected).
            int comparisonResult;
            int formatPixelCount = format.getPixelCount();
            if (formatPixelCount != selectedPixelCount) {
              comparisonResult = compareFormatValues(format.getPixelCount(), selectedPixelCount);
            } else {
              comparisonResult = compareFormatValues(format.bitrate, selectedBitrate);
            }
            selectTrack = isWithinConstraints ? comparisonResult > 0 : comparisonResult < 0;
          }
          if (selectTrack) {
            selectedGroup = trackGroup;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
            selectedBitrate = format.bitrate;
            selectedPixelCount = format.getPixelCount();
          }
        }
      }
    }
    return selectedGroup == null ? null
        : new FixedTrackSelection(selectedGroup, selectedTrackIndex);
  }

  /**
   * Compares two format values for order. A known value is considered greater than
   * {@link Format#NO_VALUE}.
   *
   * @param first The first value.
   * @param second The second value.
   * @return A negative integer if the first value is less than the second. Zero if they are equal.
   *     A positive integer if the first value is greater than the second.
   */
  private static int compareFormatValues(int first, int second) {
    return first == Format.NO_VALUE ? (second == Format.NO_VALUE ? 0 : -1)
        : (second == Format.NO_VALUE ? 1 : (first - second));
  }

  // Audio track selection implementation.

  protected TrackSelection selectAudioTrack(TrackGroupArray groups, int[][] formatSupport,
      String preferredAudioLanguage, boolean exceedRendererCapabilitiesIfNecessary,
      boolean allowMixedMimeAdaptiveness, TrackSelection.Factory adaptiveTrackSelectionFactory) {
    int selectedGroupIndex = C.INDEX_UNSET;
    int selectedTrackIndex = C.INDEX_UNSET;
    int selectedTrackScore = 0;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex], exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          int trackScore = getAudioTrackScore(trackFormatSupport[trackIndex],
              preferredAudioLanguage, format);
          if (trackScore > selectedTrackScore) {
            selectedGroupIndex = groupIndex;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
          }
        }
      }
    }

    if (selectedGroupIndex == C.INDEX_UNSET) {
      return null;
    }

    TrackGroup selectedGroup = groups.get(selectedGroupIndex);
    if (adaptiveTrackSelectionFactory != null) {
      // If the group of the track with the highest score allows it, try to enable adaptation.
      int[] adaptiveTracks = getAdaptiveAudioTracks(selectedGroup,
          formatSupport[selectedGroupIndex], allowMixedMimeAdaptiveness);
      if (adaptiveTracks.length > 0) {
        return adaptiveTrackSelectionFactory.createTrackSelection(selectedGroup,
            adaptiveTracks);
      }
    }
    return new FixedTrackSelection(selectedGroup, selectedTrackIndex);
  }

  private static int getAudioTrackScore(int formatSupport, String preferredLanguage,
      Format format) {
    boolean isDefault = (format.selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0;
    int trackScore;
    if (formatHasLanguage(format, preferredLanguage)) {
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
    if (isSupported(formatSupport, false)) {
      trackScore += WITHIN_RENDERER_CAPABILITIES_BONUS;
    }
    return trackScore;
  }

  private static int[] getAdaptiveAudioTracks(TrackGroup group, int[] formatSupport,
      boolean allowMixedMimeTypes) {
    int selectedConfigurationTrackCount = 0;
    AudioConfigurationTuple selectedConfiguration = null;
    HashSet<AudioConfigurationTuple> seenConfigurationTuples = new HashSet<>();
    for (int i = 0; i < group.length; i++) {
      Format format = group.getFormat(i);
      AudioConfigurationTuple configuration = new AudioConfigurationTuple(
          format.channelCount, format.sampleRate,
          allowMixedMimeTypes ? null : format.sampleMimeType);
      if (seenConfigurationTuples.add(configuration)) {
        int configurationCount = getAdaptiveAudioTrackCount(group, formatSupport, configuration);
        if (configurationCount > selectedConfigurationTrackCount) {
          selectedConfiguration = configuration;
          selectedConfigurationTrackCount = configurationCount;
        }
      }
    }

    if (selectedConfigurationTrackCount > 1) {
      int[] adaptiveIndices = new int[selectedConfigurationTrackCount];
      int index = 0;
      for (int i = 0; i < group.length; i++) {
        if (isSupportedAdaptiveAudioTrack(group.getFormat(i), formatSupport[i],
            selectedConfiguration)) {
          adaptiveIndices[index++] = i;
        }
      }
      return adaptiveIndices;
    }
    return NO_TRACKS;
  }

  private static int getAdaptiveAudioTrackCount(TrackGroup group, int[] formatSupport,
      AudioConfigurationTuple configuration) {
    int count = 0;
    for (int i = 0; i < group.length; i++) {
      if (isSupportedAdaptiveAudioTrack(group.getFormat(i), formatSupport[i], configuration)) {
        count++;
      }
    }
    return count;
  }

  private static boolean isSupportedAdaptiveAudioTrack(Format format, int formatSupport,
      AudioConfigurationTuple configuration) {
    return isSupported(formatSupport, false) && format.channelCount == configuration.channelCount
        && format.sampleRate == configuration.sampleRate
        && (configuration.mimeType == null
            || TextUtils.equals(configuration.mimeType, format.sampleMimeType));
  }

  // Text track selection implementation.

  protected TrackSelection selectTextTrack(TrackGroupArray groups, int[][] formatSupport,
      String preferredTextLanguage, String preferredAudioLanguage,
      boolean exceedRendererCapabilitiesIfNecessary) {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    int selectedTrackScore = 0;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex], exceedRendererCapabilitiesIfNecessary)) {
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
            // Track should not be selected.
            continue;
          }
          if (isSupported(trackFormatSupport[trackIndex], false)) {
            trackScore += WITHIN_RENDERER_CAPABILITIES_BONUS;
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
      int[][] formatSupport, boolean exceedRendererCapabilitiesIfNecessary) {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    int selectedTrackScore = 0;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex], exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          boolean isDefault = (format.selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0;
          int trackScore = isDefault ? 2 : 1;
          if (isSupported(trackFormatSupport[trackIndex], false)) {
            trackScore += WITHIN_RENDERER_CAPABILITIES_BONUS;
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

  protected static boolean isSupported(int formatSupport, boolean allowExceedsCapabilities) {
    int maskedSupport = formatSupport & RendererCapabilities.FORMAT_SUPPORT_MASK;
    return maskedSupport == RendererCapabilities.FORMAT_HANDLED || (allowExceedsCapabilities
        && maskedSupport == RendererCapabilities.FORMAT_EXCEEDS_CAPABILITIES);
  }

  protected static boolean formatHasLanguage(Format format, String language) {
    return TextUtils.equals(language, Util.normalizeLanguageCode(format.language));
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

  private static final class AudioConfigurationTuple {

    public final int channelCount;
    public final int sampleRate;
    public final String mimeType;

    public AudioConfigurationTuple(int channelCount, int sampleRate, String mimeType) {
      this.channelCount = channelCount;
      this.sampleRate = sampleRate;
      this.mimeType = mimeType;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      AudioConfigurationTuple other = (AudioConfigurationTuple) obj;
      return channelCount == other.channelCount && sampleRate == other.sampleRate
          && TextUtils.equals(mimeType, other.mimeType);
    }

    @Override
    public int hashCode() {
      int result = channelCount;
      result = 31 * result + sampleRate;
      result = 31 * result + (mimeType != null ? mimeType.hashCode() : 0);
      return result;
    }

  }

}

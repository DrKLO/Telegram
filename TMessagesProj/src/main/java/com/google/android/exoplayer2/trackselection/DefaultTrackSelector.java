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

import android.content.Context;
import android.graphics.Point;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * A default {@link TrackSelector} suitable for most use cases. Track selections are made according
 * to configurable {@link Parameters}, which can be set by calling {@link
 * #setParameters(Parameters)}.
 *
 * <h3>Modifying parameters</h3>
 *
 * To modify only some aspects of the parameters currently used by a selector, it's possible to
 * obtain a {@link ParametersBuilder} initialized with the current {@link Parameters}. The desired
 * modifications can be made on the builder, and the resulting {@link Parameters} can then be built
 * and set on the selector. For example the following code modifies the parameters to restrict video
 * track selections to SD, and to select a German audio track if there is one:
 *
 * <pre>{@code
 * // Build on the current parameters.
 * Parameters currentParameters = trackSelector.getParameters();
 * // Build the resulting parameters.
 * Parameters newParameters = currentParameters
 *     .buildUpon()
 *     .setMaxVideoSizeSd()
 *     .setPreferredAudioLanguage("deu")
 *     .build();
 * // Set the new parameters.
 * trackSelector.setParameters(newParameters);
 * }</pre>
 *
 * Convenience methods and chaining allow this to be written more concisely as:
 *
 * <pre>{@code
 * trackSelector.setParameters(
 *     trackSelector
 *         .buildUponParameters()
 *         .setMaxVideoSizeSd()
 *         .setPreferredAudioLanguage("deu"));
 * }</pre>
 *
 * Selection {@link Parameters} support many different options, some of which are described below.
 *
 * <h3>Selecting specific tracks</h3>
 *
 * Track selection overrides can be used to select specific tracks. To specify an override for a
 * renderer, it's first necessary to obtain the tracks that have been mapped to it:
 *
 * <pre>{@code
 * MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
 * TrackGroupArray rendererTrackGroups = mappedTrackInfo == null ? null
 *     : mappedTrackInfo.getTrackGroups(rendererIndex);
 * }</pre>
 *
 * If {@code rendererTrackGroups} is null then there aren't any currently mapped tracks, and so
 * setting an override isn't possible. Note that a {@link Player.EventListener} registered on the
 * player can be used to determine when the current tracks (and therefore the mapping) changes. If
 * {@code rendererTrackGroups} is non-null then an override can be set. The next step is to query
 * the properties of the available tracks to determine the {@code groupIndex} and the {@code
 * trackIndices} within the group it that should be selected. The override can then be specified
 * using {@link ParametersBuilder#setSelectionOverride}:
 *
 * <pre>{@code
 * SelectionOverride selectionOverride = new SelectionOverride(groupIndex, trackIndices);
 * trackSelector.setParameters(
 *     trackSelector
 *         .buildUponParameters()
 *         .setSelectionOverride(rendererIndex, rendererTrackGroups, selectionOverride));
 * }</pre>
 *
 * <h3>Constraint based track selection</h3>
 *
 * Whilst track selection overrides make it possible to select specific tracks, the recommended way
 * of controlling which tracks are selected is by specifying constraints. For example consider the
 * case of wanting to restrict video track selections to SD, and preferring German audio tracks.
 * Track selection overrides could be used to select specific tracks meeting these criteria, however
 * a simpler and more flexible approach is to specify these constraints directly:
 *
 * <pre>{@code
 * trackSelector.setParameters(
 *     trackSelector
 *         .buildUponParameters()
 *         .setMaxVideoSizeSd()
 *         .setPreferredAudioLanguage("deu"));
 * }</pre>
 *
 * There are several benefits to using constraint based track selection instead of specific track
 * overrides:
 *
 * <ul>
 *   <li>You can specify constraints before knowing what tracks the media provides. This can
 *       simplify track selection code (e.g. you don't have to listen for changes in the available
 *       tracks before configuring the selector).
 *   <li>Constraints can be applied consistently across all periods in a complex piece of media,
 *       even if those periods contain different tracks. In contrast, a specific track override is
 *       only applied to periods whose tracks match those for which the override was set.
 * </ul>
 *
 * <h3>Disabling renderers</h3>
 *
 * Renderers can be disabled using {@link ParametersBuilder#setRendererDisabled}. Disabling a
 * renderer differs from setting a {@code null} override because the renderer is disabled
 * unconditionally, whereas a {@code null} override is applied only when the track groups available
 * to the renderer match the {@link TrackGroupArray} for which it was specified.
 *
 * <h3>Tunneling</h3>
 *
 * Tunneled playback can be enabled in cases where the combination of renderers and selected tracks
 * support it. Tunneled playback is enabled by passing an audio session ID to {@link
 * ParametersBuilder#setTunnelingAudioSessionId(int)}.
 */
public class DefaultTrackSelector extends MappingTrackSelector {

  /**
   * A builder for {@link Parameters}. See the {@link Parameters} documentation for explanations of
   * the parameters that can be configured using this builder.
   */
  public static final class ParametersBuilder {

    private final SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides;
    private final SparseBooleanArray rendererDisabledFlags;

    private @Nullable String preferredAudioLanguage;
    private @Nullable String preferredTextLanguage;
    private boolean selectUndeterminedTextLanguage;
    private int disabledTextTrackSelectionFlags;
    private boolean forceLowestBitrate;
    private boolean allowMixedMimeAdaptiveness;
    private boolean allowNonSeamlessAdaptiveness;
    private int maxVideoWidth;
    private int maxVideoHeight;
    private int maxVideoBitrate;
    private boolean exceedVideoConstraintsIfNecessary;
    private boolean exceedRendererCapabilitiesIfNecessary;
    private int viewportWidth;
    private int viewportHeight;
    private boolean viewportOrientationMayChange;
    private int tunnelingAudioSessionId;

    /** Creates a builder with default initial values. */
    public ParametersBuilder() {
      this(Parameters.DEFAULT);
    }

    /**
     * @param initialValues The {@link Parameters} from which the initial values of the builder are
     *     obtained.
     */
    private ParametersBuilder(Parameters initialValues) {
      selectionOverrides = cloneSelectionOverrides(initialValues.selectionOverrides);
      rendererDisabledFlags = initialValues.rendererDisabledFlags.clone();
      preferredAudioLanguage = initialValues.preferredAudioLanguage;
      preferredTextLanguage = initialValues.preferredTextLanguage;
      selectUndeterminedTextLanguage = initialValues.selectUndeterminedTextLanguage;
      disabledTextTrackSelectionFlags = initialValues.disabledTextTrackSelectionFlags;
      forceLowestBitrate = initialValues.forceLowestBitrate;
      allowMixedMimeAdaptiveness = initialValues.allowMixedMimeAdaptiveness;
      allowNonSeamlessAdaptiveness = initialValues.allowNonSeamlessAdaptiveness;
      maxVideoWidth = initialValues.maxVideoWidth;
      maxVideoHeight = initialValues.maxVideoHeight;
      maxVideoBitrate = initialValues.maxVideoBitrate;
      exceedVideoConstraintsIfNecessary = initialValues.exceedVideoConstraintsIfNecessary;
      exceedRendererCapabilitiesIfNecessary = initialValues.exceedRendererCapabilitiesIfNecessary;
      viewportWidth = initialValues.viewportWidth;
      viewportHeight = initialValues.viewportHeight;
      viewportOrientationMayChange = initialValues.viewportOrientationMayChange;
      tunnelingAudioSessionId = initialValues.tunnelingAudioSessionId;
    }

    /**
     * See {@link Parameters#preferredAudioLanguage}.
     *
     * @return This builder.
     */
    public ParametersBuilder setPreferredAudioLanguage(String preferredAudioLanguage) {
      this.preferredAudioLanguage = preferredAudioLanguage;
      return this;
    }

    /**
     * See {@link Parameters#preferredTextLanguage}.
     *
     * @return This builder.
     */
    public ParametersBuilder setPreferredTextLanguage(String preferredTextLanguage) {
      this.preferredTextLanguage = preferredTextLanguage;
      return this;
    }

    /**
     * See {@link Parameters#selectUndeterminedTextLanguage}.
     *
     * @return This builder.
     */
    public ParametersBuilder setSelectUndeterminedTextLanguage(
        boolean selectUndeterminedTextLanguage) {
      this.selectUndeterminedTextLanguage = selectUndeterminedTextLanguage;
      return this;
    }

    /**
     * See {@link Parameters#disabledTextTrackSelectionFlags}.
     *
     * @return This builder.
     */
    public ParametersBuilder setDisabledTextTrackSelectionFlags(
        int disabledTextTrackSelectionFlags) {
      this.disabledTextTrackSelectionFlags = disabledTextTrackSelectionFlags;
      return this;
    }

    /**
     * See {@link Parameters#forceLowestBitrate}.
     *
     * @return This builder.
     */
    public ParametersBuilder setForceLowestBitrate(boolean forceLowestBitrate) {
      this.forceLowestBitrate = forceLowestBitrate;
      return this;
    }

    /**
     * See {@link Parameters#allowMixedMimeAdaptiveness}.
     *
     * @return This builder.
     */
    public ParametersBuilder setAllowMixedMimeAdaptiveness(boolean allowMixedMimeAdaptiveness) {
      this.allowMixedMimeAdaptiveness = allowMixedMimeAdaptiveness;
      return this;
    }

    /**
     * See {@link Parameters#allowNonSeamlessAdaptiveness}.
     *
     * @return This builder.
     */
    public ParametersBuilder setAllowNonSeamlessAdaptiveness(boolean allowNonSeamlessAdaptiveness) {
      this.allowNonSeamlessAdaptiveness = allowNonSeamlessAdaptiveness;
      return this;
    }

    /**
     * Equivalent to {@link #setMaxVideoSize setMaxVideoSize(1279, 719)}.
     *
     * @return This builder.
     */
    public ParametersBuilder setMaxVideoSizeSd() {
      return setMaxVideoSize(1279, 719);
    }

    /**
     * Equivalent to {@link #setMaxVideoSize setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)}.
     *
     * @return This builder.
     */
    public ParametersBuilder clearVideoSizeConstraints() {
      return setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * See {@link Parameters#maxVideoWidth} and {@link Parameters#maxVideoHeight}.
     *
     * @return This builder.
     */
    public ParametersBuilder setMaxVideoSize(int maxVideoWidth, int maxVideoHeight) {
      this.maxVideoWidth = maxVideoWidth;
      this.maxVideoHeight = maxVideoHeight;
      return this;
    }

    /**
     * See {@link Parameters#maxVideoBitrate}.
     *
     * @return This builder.
     */
    public ParametersBuilder setMaxVideoBitrate(int maxVideoBitrate) {
      this.maxVideoBitrate = maxVideoBitrate;
      return this;
    }

    /**
     * See {@link Parameters#exceedVideoConstraintsIfNecessary}.
     *
     * @return This builder.
     */
    public ParametersBuilder setExceedVideoConstraintsIfNecessary(
        boolean exceedVideoConstraintsIfNecessary) {
      this.exceedVideoConstraintsIfNecessary = exceedVideoConstraintsIfNecessary;
      return this;
    }

    /**
     * See {@link Parameters#exceedRendererCapabilitiesIfNecessary}.
     *
     * @return This builder.
     */
    public ParametersBuilder setExceedRendererCapabilitiesIfNecessary(
        boolean exceedRendererCapabilitiesIfNecessary) {
      this.exceedRendererCapabilitiesIfNecessary = exceedRendererCapabilitiesIfNecessary;
      return this;
    }

    /**
     * Equivalent to calling {@link #setViewportSize(int, int, boolean)} with the viewport size
     * obtained from {@link Util#getPhysicalDisplaySize(Context)}.
     *
     * @param context Any context.
     * @param viewportOrientationMayChange See {@link Parameters#viewportOrientationMayChange}.
     * @return This builder.
     */
    public ParametersBuilder setViewportSizeToPhysicalDisplaySize(
        Context context, boolean viewportOrientationMayChange) {
      // Assume the viewport is fullscreen.
      Point viewportSize = Util.getPhysicalDisplaySize(context);
      return setViewportSize(viewportSize.x, viewportSize.y, viewportOrientationMayChange);
    }

    /**
     * Equivalent to
     * {@link #setViewportSize setViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE, true)}.
     *
     * @return This builder.
     */
    public ParametersBuilder clearViewportSizeConstraints() {
      return setViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE, true);
    }

    /**
     * See {@link Parameters#viewportWidth}, {@link Parameters#maxVideoHeight} and {@link
     * Parameters#viewportOrientationMayChange}.
     *
     * @param viewportWidth See {@link Parameters#viewportWidth}.
     * @param viewportHeight See {@link Parameters#viewportHeight}.
     * @param viewportOrientationMayChange See {@link Parameters#viewportOrientationMayChange}.
     * @return This builder.
     */
    public ParametersBuilder setViewportSize(
        int viewportWidth, int viewportHeight, boolean viewportOrientationMayChange) {
      this.viewportWidth = viewportWidth;
      this.viewportHeight = viewportHeight;
      this.viewportOrientationMayChange = viewportOrientationMayChange;
      return this;
    }

    /**
     * Sets whether the renderer at the specified index is disabled. Disabling a renderer prevents
     * the selector from selecting any tracks for it.
     *
     * @param rendererIndex The renderer index.
     * @param disabled Whether the renderer is disabled.
     */
    public final ParametersBuilder setRendererDisabled(int rendererIndex, boolean disabled) {
      if (rendererDisabledFlags.get(rendererIndex) == disabled) {
        // The disabled flag is unchanged.
        return this;
      }
      // Only true values are placed in the array to make it easier to check for equality.
      if (disabled) {
        rendererDisabledFlags.put(rendererIndex, true);
      } else {
        rendererDisabledFlags.delete(rendererIndex);
      }
      return this;
    }

    /**
     * Overrides the track selection for the renderer at the specified index.
     *
     * <p>When the {@link TrackGroupArray} mapped to the renderer matches the one provided, the
     * override is applied. When the {@link TrackGroupArray} does not match, the override has no
     * effect. The override replaces any previous override for the specified {@link TrackGroupArray}
     * for the specified {@link Renderer}.
     *
     * <p>Passing a {@code null} override will cause the renderer to be disabled when the {@link
     * TrackGroupArray} mapped to it matches the one provided. When the {@link TrackGroupArray} does
     * not match a {@code null} override has no effect. Hence a {@code null} override differs from
     * disabling the renderer using {@link #setRendererDisabled(int, boolean)} because the renderer
     * is disabled conditionally on the {@link TrackGroupArray} mapped to it, where-as {@link
     * #setRendererDisabled(int, boolean)} disables the renderer unconditionally.
     *
     * <p>To remove overrides use {@link #clearSelectionOverride(int, TrackGroupArray)}, {@link
     * #clearSelectionOverrides(int)} or {@link #clearSelectionOverrides()}.
     *
     * @param rendererIndex The renderer index.
     * @param groups The {@link TrackGroupArray} for which the override should be applied.
     * @param override The override.
     */
    public final ParametersBuilder setSelectionOverride(
        int rendererIndex, TrackGroupArray groups, SelectionOverride override) {
      Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
      if (overrides == null) {
        overrides = new HashMap<>();
        selectionOverrides.put(rendererIndex, overrides);
      }
      if (overrides.containsKey(groups) && Util.areEqual(overrides.get(groups), override)) {
        // The override is unchanged.
        return this;
      }
      overrides.put(groups, override);
      return this;
    }

    /**
     * Clears a track selection override for the specified renderer and {@link TrackGroupArray}.
     *
     * @param rendererIndex The renderer index.
     * @param groups The {@link TrackGroupArray} for which the override should be cleared.
     */
    public final ParametersBuilder clearSelectionOverride(
        int rendererIndex, TrackGroupArray groups) {
      Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
      if (overrides == null || !overrides.containsKey(groups)) {
        // Nothing to clear.
        return this;
      }
      overrides.remove(groups);
      if (overrides.isEmpty()) {
        selectionOverrides.remove(rendererIndex);
      }
      return this;
    }

    /**
     * Clears all track selection overrides for the specified renderer.
     *
     * @param rendererIndex The renderer index.
     */
    public final ParametersBuilder clearSelectionOverrides(int rendererIndex) {
      Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
      if (overrides == null || overrides.isEmpty()) {
        // Nothing to clear.
        return this;
      }
      selectionOverrides.remove(rendererIndex);
      return this;
    }

    /** Clears all track selection overrides for all renderers. */
    public final ParametersBuilder clearSelectionOverrides() {
      if (selectionOverrides.size() == 0) {
        // Nothing to clear.
        return this;
      }
      selectionOverrides.clear();
      return this;
    }

    /**
     * See {@link Parameters#tunnelingAudioSessionId}.
     *
     * <p>Enables or disables tunneling. To enable tunneling, pass an audio session id to use when
     * in tunneling mode. Session ids can be generated using {@link
     * C#generateAudioSessionIdV21(Context)}. To disable tunneling pass {@link
     * C#AUDIO_SESSION_ID_UNSET}. Tunneling will only be activated if it's both enabled and
     * supported by the audio and video renderers for the selected tracks.
     *
     * @param tunnelingAudioSessionId The audio session id to use when tunneling, or {@link
     *     C#AUDIO_SESSION_ID_UNSET} to disable tunneling.
     */
    public ParametersBuilder setTunnelingAudioSessionId(int tunnelingAudioSessionId) {
      if (this.tunnelingAudioSessionId != tunnelingAudioSessionId) {
        this.tunnelingAudioSessionId = tunnelingAudioSessionId;
        return this;
      }
      return this;
    }

    /**
     * Builds a {@link Parameters} instance with the selected values.
     */
    public Parameters build() {
      return new Parameters(
          selectionOverrides,
          rendererDisabledFlags,
          preferredAudioLanguage,
          preferredTextLanguage,
          selectUndeterminedTextLanguage,
          disabledTextTrackSelectionFlags,
          forceLowestBitrate,
          allowMixedMimeAdaptiveness,
          allowNonSeamlessAdaptiveness,
          maxVideoWidth,
          maxVideoHeight,
          maxVideoBitrate,
          exceedVideoConstraintsIfNecessary,
          exceedRendererCapabilitiesIfNecessary,
          viewportWidth,
          viewportHeight,
          viewportOrientationMayChange,
          tunnelingAudioSessionId);
    }

    private static SparseArray<Map<TrackGroupArray, SelectionOverride>> cloneSelectionOverrides(
        SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides) {
      SparseArray<Map<TrackGroupArray, SelectionOverride>> clone = new SparseArray<>();
      for (int i = 0; i < selectionOverrides.size(); i++) {
        clone.put(selectionOverrides.keyAt(i), new HashMap<>(selectionOverrides.valueAt(i)));
      }
      return clone;
    }
  }

  /** Constraint parameters for {@link DefaultTrackSelector}. */
  public static final class Parameters implements Parcelable {

    /** An instance with default values. */
    public static final Parameters DEFAULT = new Parameters();

    // Per renderer overrides.

    private final SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides;
    private final SparseBooleanArray rendererDisabledFlags;

    // Audio
    /**
     * The preferred language for audio and forced text tracks, as an ISO 639-2/T tag. {@code null}
     * selects the default track, or the first track if there's no default. The default value is
     * {@code null}.
     */
    public final @Nullable String preferredAudioLanguage;

    // Text
    /**
     * The preferred language for text tracks as an ISO 639-2/T tag. {@code null} selects the
     * default track if there is one, or no track otherwise. The default value is {@code null}.
     */
    public final @Nullable String preferredTextLanguage;
    /**
     * Whether a text track with undetermined language should be selected if no track with {@link
     * #preferredTextLanguage} is available, or if {@link #preferredTextLanguage} is unset. The
     * default value is {@code false}.
     */
    public final boolean selectUndeterminedTextLanguage;
    /**
     * Bitmask of selection flags that are disabled for text track selections. See {@link
     * C.SelectionFlags}. The default value is {@code 0} (i.e. no flags).
     */
    public final int disabledTextTrackSelectionFlags;

    // Video
    /**
     * Maximum allowed video width. The default value is {@link Integer#MAX_VALUE} (i.e. no
     * constraint).
     *
     * <p>To constrain adaptive video track selections to be suitable for a given viewport (the
     * region of the display within which video will be played), use ({@link #viewportWidth}, {@link
     * #viewportHeight} and {@link #viewportOrientationMayChange}) instead.
     */
    public final int maxVideoWidth;
    /**
     * Maximum allowed video height. The default value is {@link Integer#MAX_VALUE} (i.e. no
     * constraint).
     *
     * <p>To constrain adaptive video track selections to be suitable for a given viewport (the
     * region of the display within which video will be played), use ({@link #viewportWidth}, {@link
     * #viewportHeight} and {@link #viewportOrientationMayChange}) instead.
     */
    public final int maxVideoHeight;
    /**
     * Maximum video bitrate. The default value is {@link Integer#MAX_VALUE} (i.e. no constraint).
     */
    public final int maxVideoBitrate;
    /**
     * Whether to exceed the {@link #maxVideoWidth}, {@link #maxVideoHeight} and {@link
     * #maxVideoBitrate} constraints when no selection can be made otherwise. The default value is
     * {@code true}.
     */
    public final boolean exceedVideoConstraintsIfNecessary;
    /**
     * Viewport width in pixels. Constrains video track selections for adaptive content so that only
     * tracks suitable for the viewport are selected. The default value is {@link Integer#MAX_VALUE}
     * (i.e. no constraint).
     */
    public final int viewportWidth;
    /**
     * Viewport height in pixels. Constrains video track selections for adaptive content so that
     * only tracks suitable for the viewport are selected. The default value is {@link
     * Integer#MAX_VALUE} (i.e. no constraint).
     */
    public final int viewportHeight;
    /**
     * Whether the viewport orientation may change during playback. Constrains video track
     * selections for adaptive content so that only tracks suitable for the viewport are selected.
     * The default value is {@code true}.
     */
    public final boolean viewportOrientationMayChange;

    // General
    /**
     * Whether to force selection of the single lowest bitrate audio and video tracks that comply
     * with all other constraints. The default value is {@code false}.
     */
    public final boolean forceLowestBitrate;
    /**
     * Whether to allow adaptive selections containing mixed mime types. The default value is {@code
     * false}.
     */
    public final boolean allowMixedMimeAdaptiveness;
    /**
     * Whether to allow adaptive selections where adaptation may not be completely seamless. The
     * default value is {@code true}.
     */
    public final boolean allowNonSeamlessAdaptiveness;
    /**
     * Whether to exceed renderer capabilities when no selection can be made otherwise.
     *
     * <p>This parameter applies when all of the tracks available for a renderer exceed the
     * renderer's reported capabilities. If the parameter is {@code true} then the lowest quality
     * track will still be selected. Playback may succeed if the renderer has under-reported its
     * true capabilities. If {@code false} then no track will be selected. The default value is
     * {@code true}.
     */
    public final boolean exceedRendererCapabilitiesIfNecessary;
    /**
     * The audio session id to use when tunneling, or {@link C#AUDIO_SESSION_ID_UNSET} if tunneling
     * is disabled. The default value is {@link C#AUDIO_SESSION_ID_UNSET} (i.e. tunneling is
     * disabled).
     */
    public final int tunnelingAudioSessionId;

    private Parameters() {
      this(
          /* selectionOverrides= */ new SparseArray<>(),
          /* rendererDisabledFlags= */ new SparseBooleanArray(),
          /* preferredAudioLanguage= */ null,
          /* preferredTextLanguage= */ null,
          /* selectUndeterminedTextLanguage= */ false,
          /* disabledTextTrackSelectionFlags= */ 0,
          /* forceLowestBitrate= */ false,
          /* allowMixedMimeAdaptiveness= */ false,
          /* allowNonSeamlessAdaptiveness= */ true,
          /* maxVideoWidth= */ Integer.MAX_VALUE,
          /* maxVideoHeight= */ Integer.MAX_VALUE,
          /* maxVideoBitrate= */ Integer.MAX_VALUE,
          /* exceedVideoConstraintsIfNecessary= */ true,
          /* exceedRendererCapabilitiesIfNecessary= */ true,
          /* viewportWidth= */ Integer.MAX_VALUE,
          /* viewportHeight= */ Integer.MAX_VALUE,
          /* viewportOrientationMayChange= */ true,
          /* tunnelingAudioSessionId= */ C.AUDIO_SESSION_ID_UNSET);
    }

    /* package */ Parameters(
        SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides,
        SparseBooleanArray rendererDisabledFlags,
        @Nullable String preferredAudioLanguage,
        @Nullable String preferredTextLanguage,
        boolean selectUndeterminedTextLanguage,
        int disabledTextTrackSelectionFlags,
        boolean forceLowestBitrate,
        boolean allowMixedMimeAdaptiveness,
        boolean allowNonSeamlessAdaptiveness,
        int maxVideoWidth,
        int maxVideoHeight,
        int maxVideoBitrate,
        boolean exceedVideoConstraintsIfNecessary,
        boolean exceedRendererCapabilitiesIfNecessary,
        int viewportWidth,
        int viewportHeight,
        boolean viewportOrientationMayChange,
        int tunnelingAudioSessionId) {
      this.selectionOverrides = selectionOverrides;
      this.rendererDisabledFlags = rendererDisabledFlags;
      this.preferredAudioLanguage = Util.normalizeLanguageCode(preferredAudioLanguage);
      this.preferredTextLanguage = Util.normalizeLanguageCode(preferredTextLanguage);
      this.selectUndeterminedTextLanguage = selectUndeterminedTextLanguage;
      this.disabledTextTrackSelectionFlags = disabledTextTrackSelectionFlags;
      this.forceLowestBitrate = forceLowestBitrate;
      this.allowMixedMimeAdaptiveness = allowMixedMimeAdaptiveness;
      this.allowNonSeamlessAdaptiveness = allowNonSeamlessAdaptiveness;
      this.maxVideoWidth = maxVideoWidth;
      this.maxVideoHeight = maxVideoHeight;
      this.maxVideoBitrate = maxVideoBitrate;
      this.exceedVideoConstraintsIfNecessary = exceedVideoConstraintsIfNecessary;
      this.exceedRendererCapabilitiesIfNecessary = exceedRendererCapabilitiesIfNecessary;
      this.viewportWidth = viewportWidth;
      this.viewportHeight = viewportHeight;
      this.viewportOrientationMayChange = viewportOrientationMayChange;
      this.tunnelingAudioSessionId = tunnelingAudioSessionId;
    }

    /* package */ Parameters(Parcel in) {
      this.selectionOverrides = readSelectionOverrides(in);
      this.rendererDisabledFlags = in.readSparseBooleanArray();
      this.preferredAudioLanguage = in.readString();
      this.preferredTextLanguage = in.readString();
      this.selectUndeterminedTextLanguage = Util.readBoolean(in);
      this.disabledTextTrackSelectionFlags = in.readInt();
      this.forceLowestBitrate = Util.readBoolean(in);
      this.allowMixedMimeAdaptiveness = Util.readBoolean(in);
      this.allowNonSeamlessAdaptiveness = Util.readBoolean(in);
      this.maxVideoWidth = in.readInt();
      this.maxVideoHeight = in.readInt();
      this.maxVideoBitrate = in.readInt();
      this.exceedVideoConstraintsIfNecessary = Util.readBoolean(in);
      this.exceedRendererCapabilitiesIfNecessary = Util.readBoolean(in);
      this.viewportWidth = in.readInt();
      this.viewportHeight = in.readInt();
      this.viewportOrientationMayChange = Util.readBoolean(in);
      this.tunnelingAudioSessionId = in.readInt();
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
    public final @Nullable SelectionOverride getSelectionOverride(
        int rendererIndex, TrackGroupArray groups) {
      Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
      return overrides != null ? overrides.get(groups) : null;
    }

    /**
     * Creates a new {@link ParametersBuilder}, copying the initial values from this instance.
     */
    public ParametersBuilder buildUpon() {
      return new ParametersBuilder(this);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      Parameters other = (Parameters) obj;
      return selectUndeterminedTextLanguage == other.selectUndeterminedTextLanguage
          && disabledTextTrackSelectionFlags == other.disabledTextTrackSelectionFlags
          && forceLowestBitrate == other.forceLowestBitrate
          && allowMixedMimeAdaptiveness == other.allowMixedMimeAdaptiveness
          && allowNonSeamlessAdaptiveness == other.allowNonSeamlessAdaptiveness
          && maxVideoWidth == other.maxVideoWidth
          && maxVideoHeight == other.maxVideoHeight
          && exceedVideoConstraintsIfNecessary == other.exceedVideoConstraintsIfNecessary
          && exceedRendererCapabilitiesIfNecessary == other.exceedRendererCapabilitiesIfNecessary
          && viewportOrientationMayChange == other.viewportOrientationMayChange
          && viewportWidth == other.viewportWidth
          && viewportHeight == other.viewportHeight
          && maxVideoBitrate == other.maxVideoBitrate
          && tunnelingAudioSessionId == other.tunnelingAudioSessionId
          && TextUtils.equals(preferredAudioLanguage, other.preferredAudioLanguage)
          && TextUtils.equals(preferredTextLanguage, other.preferredTextLanguage)
          && areRendererDisabledFlagsEqual(rendererDisabledFlags, other.rendererDisabledFlags)
          && areSelectionOverridesEqual(selectionOverrides, other.selectionOverrides);
    }

    @Override
    public int hashCode() {
      int result = selectUndeterminedTextLanguage ? 1 : 0;
      result = 31 * result + disabledTextTrackSelectionFlags;
      result = 31 * result + (forceLowestBitrate ? 1 : 0);
      result = 31 * result + (allowMixedMimeAdaptiveness ? 1 : 0);
      result = 31 * result + (allowNonSeamlessAdaptiveness ? 1 : 0);
      result = 31 * result + maxVideoWidth;
      result = 31 * result + maxVideoHeight;
      result = 31 * result + (exceedVideoConstraintsIfNecessary ? 1 : 0);
      result = 31 * result + (exceedRendererCapabilitiesIfNecessary ? 1 : 0);
      result = 31 * result + (viewportOrientationMayChange ? 1 : 0);
      result = 31 * result + viewportWidth;
      result = 31 * result + viewportHeight;
      result = 31 * result + maxVideoBitrate;
      result = 31 * result + tunnelingAudioSessionId;
      result =
          31 * result + (preferredAudioLanguage == null ? 0 : preferredAudioLanguage.hashCode());
      result = 31 * result + (preferredTextLanguage == null ? 0 : preferredTextLanguage.hashCode());
      return result;
    }

    // Parcelable implementation.

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      writeSelectionOverridesToParcel(dest, selectionOverrides);
      dest.writeSparseBooleanArray(rendererDisabledFlags);
      dest.writeString(preferredAudioLanguage);
      dest.writeString(preferredTextLanguage);
      Util.writeBoolean(dest, selectUndeterminedTextLanguage);
      dest.writeInt(disabledTextTrackSelectionFlags);
      Util.writeBoolean(dest, forceLowestBitrate);
      Util.writeBoolean(dest, allowMixedMimeAdaptiveness);
      Util.writeBoolean(dest, allowNonSeamlessAdaptiveness);
      dest.writeInt(maxVideoWidth);
      dest.writeInt(maxVideoHeight);
      dest.writeInt(maxVideoBitrate);
      Util.writeBoolean(dest, exceedVideoConstraintsIfNecessary);
      Util.writeBoolean(dest, exceedRendererCapabilitiesIfNecessary);
      dest.writeInt(viewportWidth);
      dest.writeInt(viewportHeight);
      Util.writeBoolean(dest, viewportOrientationMayChange);
      dest.writeInt(tunnelingAudioSessionId);
    }

    public static final Parcelable.Creator<Parameters> CREATOR =
        new Parcelable.Creator<Parameters>() {

          @Override
          public Parameters createFromParcel(Parcel in) {
            return new Parameters(in);
          }

          @Override
          public Parameters[] newArray(int size) {
            return new Parameters[size];
          }
        };

    // Static utility methods.

    private static SparseArray<Map<TrackGroupArray, SelectionOverride>> readSelectionOverrides(
        Parcel in) {
      int renderersWithOverridesCount = in.readInt();
      SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides =
          new SparseArray<>(renderersWithOverridesCount);
      for (int i = 0; i < renderersWithOverridesCount; i++) {
        int rendererIndex = in.readInt();
        int overrideCount = in.readInt();
        Map<TrackGroupArray, SelectionOverride> overrides = new HashMap<>(overrideCount);
        for (int j = 0; j < overrideCount; j++) {
          TrackGroupArray trackGroups = in.readParcelable(TrackGroupArray.class.getClassLoader());
          SelectionOverride override = in.readParcelable(SelectionOverride.class.getClassLoader());
          overrides.put(trackGroups, override);
        }
        selectionOverrides.put(rendererIndex, overrides);
      }
      return selectionOverrides;
    }

    private static void writeSelectionOverridesToParcel(
        Parcel dest, SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides) {
      int renderersWithOverridesCount = selectionOverrides.size();
      dest.writeInt(renderersWithOverridesCount);
      for (int i = 0; i < renderersWithOverridesCount; i++) {
        int rendererIndex = selectionOverrides.keyAt(i);
        Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.valueAt(i);
        int overrideCount = overrides.size();
        dest.writeInt(rendererIndex);
        dest.writeInt(overrideCount);
        for (Map.Entry<TrackGroupArray, SelectionOverride> override : overrides.entrySet()) {
          dest.writeParcelable(override.getKey(), /* parcelableFlags= */ 0);
          dest.writeParcelable(override.getValue(), /* parcelableFlags= */ 0);
        }
      }
    }

    private static boolean areRendererDisabledFlagsEqual(
        SparseBooleanArray first, SparseBooleanArray second) {
      int firstSize = first.size();
      if (second.size() != firstSize) {
        return false;
      }
      // Only true values are put into rendererDisabledFlags, so we don't need to compare values.
      for (int indexInFirst = 0; indexInFirst < firstSize; indexInFirst++) {
        if (second.indexOfKey(first.keyAt(indexInFirst)) < 0) {
          return false;
        }
      }
      return true;
    }

    private static boolean areSelectionOverridesEqual(
        SparseArray<Map<TrackGroupArray, SelectionOverride>> first,
        SparseArray<Map<TrackGroupArray, SelectionOverride>> second) {
      int firstSize = first.size();
      if (second.size() != firstSize) {
        return false;
      }
      for (int indexInFirst = 0; indexInFirst < firstSize; indexInFirst++) {
        int indexInSecond = second.indexOfKey(first.keyAt(indexInFirst));
        if (indexInSecond < 0
            || !areSelectionOverridesEqual(
                first.valueAt(indexInFirst), second.valueAt(indexInSecond))) {
          return false;
        }
      }
      return true;
    }

    private static boolean areSelectionOverridesEqual(
        Map<TrackGroupArray, SelectionOverride> first,
        Map<TrackGroupArray, SelectionOverride> second) {
      int firstSize = first.size();
      if (second.size() != firstSize) {
        return false;
      }
      for (Map.Entry<TrackGroupArray, SelectionOverride> firstEntry : first.entrySet()) {
        TrackGroupArray key = firstEntry.getKey();
        if (!second.containsKey(key) || !Util.areEqual(firstEntry.getValue(), second.get(key))) {
          return false;
        }
      }
      return true;
    }
  }

  /** A track selection override. */
  public static final class SelectionOverride implements Parcelable {

    public final int groupIndex;
    public final int[] tracks;
    public final int length;

    /**
     * @param groupIndex The overriding track group index.
     * @param tracks The overriding track indices within the track group.
     */
    public SelectionOverride(int groupIndex, int... tracks) {
      this.groupIndex = groupIndex;
      this.tracks = Arrays.copyOf(tracks, tracks.length);
      this.length = tracks.length;
      Arrays.sort(this.tracks);
    }

    /* package */ SelectionOverride(Parcel in) {
      groupIndex = in.readInt();
      length = in.readByte();
      tracks = new int[length];
      in.readIntArray(tracks);
    }

    /** Returns whether this override contains the specified track index. */
    public boolean containsTrack(int track) {
      for (int overrideTrack : tracks) {
        if (overrideTrack == track) {
          return true;
        }
      }
      return false;
    }

    @Override
    public int hashCode() {
      return 31 * groupIndex + Arrays.hashCode(tracks);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      SelectionOverride other = (SelectionOverride) obj;
      return groupIndex == other.groupIndex && Arrays.equals(tracks, other.tracks);
    }

    // Parcelable implementation.

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeInt(groupIndex);
      dest.writeInt(tracks.length);
      dest.writeIntArray(tracks);
    }

    public static final Parcelable.Creator<SelectionOverride> CREATOR =
        new Parcelable.Creator<SelectionOverride>() {

          @Override
          public SelectionOverride createFromParcel(Parcel in) {
            return new SelectionOverride(in);
          }

          @Override
          public SelectionOverride[] newArray(int size) {
            return new SelectionOverride[size];
          }
        };
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
  private final AtomicReference<Parameters> parametersReference;

  /** Constructs an instance that uses a default factory to create adaptive track selections. */
  public DefaultTrackSelector() {
    this(new AdaptiveTrackSelection.Factory());
  }

  /**
   * @deprecated Use {@link #DefaultTrackSelector()} instead. Custom bandwidth meter should be
   *     directly passed to the player in ExoPlayerFactory.
   */
  @Deprecated
  public DefaultTrackSelector(BandwidthMeter bandwidthMeter) {
    this(new AdaptiveTrackSelection.Factory(bandwidthMeter));
  }

  /**
   * Constructs an instance that uses a factory to create adaptive track selections.
   *
   * @param adaptiveTrackSelectionFactory A factory for adaptive {@link TrackSelection}s.
   */
  public DefaultTrackSelector(TrackSelection.Factory adaptiveTrackSelectionFactory) {
    this.adaptiveTrackSelectionFactory = adaptiveTrackSelectionFactory;
    parametersReference = new AtomicReference<>(Parameters.DEFAULT);
  }

  /**
   * Atomically sets the provided parameters for track selection.
   *
   * @param parameters The parameters for track selection.
   */
  public void setParameters(Parameters parameters) {
    Assertions.checkNotNull(parameters);
    if (!parametersReference.getAndSet(parameters).equals(parameters)) {
      invalidate();
    }
  }

  /**
   * Atomically sets the provided parameters for track selection.
   *
   * @param parametersBuilder A builder from which to obtain the parameters for track selection.
   */
  public void setParameters(ParametersBuilder parametersBuilder) {
    setParameters(parametersBuilder.build());
  }

  /**
   * Gets the current selection parameters.
   *
   * @return The current selection parameters.
   */
  public Parameters getParameters() {
    return parametersReference.get();
  }

  /** Returns a new {@link ParametersBuilder} initialized with the current selection parameters. */
  public ParametersBuilder buildUponParameters() {
    return getParameters().buildUpon();
  }

  /** @deprecated Use {@link ParametersBuilder#setRendererDisabled(int, boolean)}. */
  @Deprecated
  public final void setRendererDisabled(int rendererIndex, boolean disabled) {
    setParameters(buildUponParameters().setRendererDisabled(rendererIndex, disabled));
  }

  /** @deprecated Use {@link Parameters#getRendererDisabled(int)}. * */
  @Deprecated
  public final boolean getRendererDisabled(int rendererIndex) {
    return getParameters().getRendererDisabled(rendererIndex);
  }

  /**
   * @deprecated Use {@link ParametersBuilder#setSelectionOverride(int, TrackGroupArray,
   *     SelectionOverride)}.
   */
  @Deprecated
  public final void setSelectionOverride(
      int rendererIndex, TrackGroupArray groups, SelectionOverride override) {
    setParameters(buildUponParameters().setSelectionOverride(rendererIndex, groups, override));
  }

  /** @deprecated Use {@link Parameters#hasSelectionOverride(int, TrackGroupArray)}. * */
  @Deprecated
  public final boolean hasSelectionOverride(int rendererIndex, TrackGroupArray groups) {
    return getParameters().hasSelectionOverride(rendererIndex, groups);
  }

  /** @deprecated Use {@link Parameters#getSelectionOverride(int, TrackGroupArray)}. */
  @Deprecated
  public final @Nullable SelectionOverride getSelectionOverride(
      int rendererIndex, TrackGroupArray groups) {
    return getParameters().getSelectionOverride(rendererIndex, groups);
  }

  /** @deprecated Use {@link ParametersBuilder#clearSelectionOverride(int, TrackGroupArray)}. */
  @Deprecated
  public final void clearSelectionOverride(int rendererIndex, TrackGroupArray groups) {
    setParameters(buildUponParameters().clearSelectionOverride(rendererIndex, groups));
  }

  /** @deprecated Use {@link ParametersBuilder#clearSelectionOverrides(int)}. */
  @Deprecated
  public final void clearSelectionOverrides(int rendererIndex) {
    setParameters(buildUponParameters().clearSelectionOverrides(rendererIndex));
  }

  /** @deprecated Use {@link ParametersBuilder#clearSelectionOverrides()}. */
  @Deprecated
  public final void clearSelectionOverrides() {
    setParameters(buildUponParameters().clearSelectionOverrides());
  }

  /** @deprecated Use {@link ParametersBuilder#setTunnelingAudioSessionId(int)}. */
  @Deprecated
  public void setTunnelingAudioSessionId(int tunnelingAudioSessionId) {
    setParameters(buildUponParameters().setTunnelingAudioSessionId(tunnelingAudioSessionId));
  }

  // MappingTrackSelector implementation.

  @Override
  protected final Pair<@NullableType RendererConfiguration[], @NullableType TrackSelection[]>
      selectTracks(
          MappedTrackInfo mappedTrackInfo,
          int[][][] rendererFormatSupports,
          int[] rendererMixedMimeTypeAdaptationSupports)
          throws ExoPlaybackException {
    Parameters params = parametersReference.get();
    int rendererCount = mappedTrackInfo.getRendererCount();
    @NullableType TrackSelection[] rendererTrackSelections =
        selectAllTracks(
            mappedTrackInfo,
            rendererFormatSupports,
            rendererMixedMimeTypeAdaptationSupports,
            params);

    // Apply track disabling and overriding.
    for (int i = 0; i < rendererCount; i++) {
      if (params.getRendererDisabled(i)) {
        rendererTrackSelections[i] = null;
      } else {
        TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(i);
        if (params.hasSelectionOverride(i, rendererTrackGroups)) {
          SelectionOverride override = params.getSelectionOverride(i, rendererTrackGroups);
          if (override == null) {
            rendererTrackSelections[i] = null;
          } else if (override.length == 1) {
            rendererTrackSelections[i] =
                new FixedTrackSelection(
                    rendererTrackGroups.get(override.groupIndex), override.tracks[0]);
          } else {
            rendererTrackSelections[i] =
                Assertions.checkNotNull(adaptiveTrackSelectionFactory)
                    .createTrackSelection(
                        rendererTrackGroups.get(override.groupIndex),
                        getBandwidthMeter(),
                        override.tracks);
          }
        }
      }
    }

    // Initialize the renderer configurations to the default configuration for all renderers with
    // selections, and null otherwise.
    @NullableType RendererConfiguration[] rendererConfigurations =
        new RendererConfiguration[rendererCount];
    for (int i = 0; i < rendererCount; i++) {
      boolean forceRendererDisabled = params.getRendererDisabled(i);
      boolean rendererEnabled =
          !forceRendererDisabled
              && (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_NONE
                  || rendererTrackSelections[i] != null);
      rendererConfigurations[i] = rendererEnabled ? RendererConfiguration.DEFAULT : null;
    }

    // Configure audio and video renderers to use tunneling if appropriate.
    maybeConfigureRenderersForTunneling(
        mappedTrackInfo,
        rendererFormatSupports,
        rendererConfigurations,
        rendererTrackSelections,
        params.tunnelingAudioSessionId);

    return Pair.create(rendererConfigurations, rendererTrackSelections);
  }

  // Track selection prior to overrides and disabled flags being applied.

  /**
   * Called from {@link #selectTracks(MappedTrackInfo, int[][][], int[])} to make a track selection
   * for each renderer, prior to overrides and disabled flags being applied.
   *
   * <p>The implementation should not account for overrides and disabled flags. Track selections
   * generated by this method will be overridden to account for these properties.
   *
   * @param mappedTrackInfo Mapped track information.
   * @param rendererFormatSupports The result of {@link RendererCapabilities#supportsFormat} for
   *     each mapped track, indexed by renderer, track group and track (in that order).
   * @param rendererMixedMimeTypeAdaptationSupports The result of {@link
   *     RendererCapabilities#supportsMixedMimeTypeAdaptation()} for each renderer.
   * @return Track selections for each renderer. A null selection indicates the renderer should be
   *     disabled, unless RendererCapabilities#getTrackType()} is {@link C#TRACK_TYPE_NONE}.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected @NullableType TrackSelection[] selectAllTracks(
      MappedTrackInfo mappedTrackInfo,
      int[][][] rendererFormatSupports,
      int[] rendererMixedMimeTypeAdaptationSupports,
      Parameters params)
      throws ExoPlaybackException {
    int rendererCount = mappedTrackInfo.getRendererCount();
    @NullableType TrackSelection[] rendererTrackSelections = new TrackSelection[rendererCount];

    boolean seenVideoRendererWithMappedTracks = false;
    boolean selectedVideoTracks = false;
    for (int i = 0; i < rendererCount; i++) {
      if (C.TRACK_TYPE_VIDEO == mappedTrackInfo.getRendererType(i)) {
        if (!selectedVideoTracks) {
          rendererTrackSelections[i] =
              selectVideoTrack(
                  mappedTrackInfo.getTrackGroups(i),
                  rendererFormatSupports[i],
                  rendererMixedMimeTypeAdaptationSupports[i],
                  params,
                  adaptiveTrackSelectionFactory);
          selectedVideoTracks = rendererTrackSelections[i] != null;
        }
        seenVideoRendererWithMappedTracks |= mappedTrackInfo.getTrackGroups(i).length > 0;
      }
    }

    boolean selectedAudioTracks = false;
    boolean selectedTextTracks = false;
    for (int i = 0; i < rendererCount; i++) {
      int trackType = mappedTrackInfo.getRendererType(i);
      switch (trackType) {
        case C.TRACK_TYPE_VIDEO:
          // Already done. Do nothing.
          break;
        case C.TRACK_TYPE_AUDIO:
          if (!selectedAudioTracks) {
            rendererTrackSelections[i] =
                selectAudioTrack(
                    mappedTrackInfo.getTrackGroups(i),
                    rendererFormatSupports[i],
                    rendererMixedMimeTypeAdaptationSupports[i],
                    params,
                    seenVideoRendererWithMappedTracks ? null : adaptiveTrackSelectionFactory);
            selectedAudioTracks = rendererTrackSelections[i] != null;
          }
          break;
        case C.TRACK_TYPE_TEXT:
          if (!selectedTextTracks) {
            rendererTrackSelections[i] =
                selectTextTrack(
                    mappedTrackInfo.getTrackGroups(i), rendererFormatSupports[i], params);
            selectedTextTracks = rendererTrackSelections[i] != null;
          }
          break;
        default:
          rendererTrackSelections[i] =
              selectOtherTrack(
                  trackType, mappedTrackInfo.getTrackGroups(i), rendererFormatSupports[i], params);
          break;
      }
    }

    return rendererTrackSelections;
  }

  // Video track selection implementation.

  /**
   * Called by {@link #selectAllTracks(MappedTrackInfo, int[][][], int[], Parameters)} to create a
   * {@link TrackSelection} for a video renderer.
   *
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupports The result of {@link RendererCapabilities#supportsFormat} for each mapped
   *     track, indexed by track group index and track index (in that order).
   * @param mixedMimeTypeAdaptationSupports The result of {@link
   *     RendererCapabilities#supportsMixedMimeTypeAdaptation()} for the renderer.
   * @param params The selector's current constraint parameters.
   * @param adaptiveTrackSelectionFactory A factory for generating adaptive track selections, or
   *     null if a fixed track selection is required.
   * @return The {@link TrackSelection} for the renderer, or null if no selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected @Nullable TrackSelection selectVideoTrack(
      TrackGroupArray groups,
      int[][] formatSupports,
      int mixedMimeTypeAdaptationSupports,
      Parameters params,
      @Nullable TrackSelection.Factory adaptiveTrackSelectionFactory)
      throws ExoPlaybackException {
    TrackSelection selection = null;
    if (!params.forceLowestBitrate && adaptiveTrackSelectionFactory != null) {
      selection =
          selectAdaptiveVideoTrack(
              groups,
              formatSupports,
              mixedMimeTypeAdaptationSupports,
              params,
              adaptiveTrackSelectionFactory,
              getBandwidthMeter());
    }
    if (selection == null) {
      selection = selectFixedVideoTrack(groups, formatSupports, params);
    }
    return selection;
  }

  private static @Nullable TrackSelection selectAdaptiveVideoTrack(
      TrackGroupArray groups,
      int[][] formatSupport,
      int mixedMimeTypeAdaptationSupports,
      Parameters params,
      TrackSelection.Factory adaptiveTrackSelectionFactory,
      BandwidthMeter bandwidthMeter)
      throws ExoPlaybackException {
    int requiredAdaptiveSupport = params.allowNonSeamlessAdaptiveness
        ? (RendererCapabilities.ADAPTIVE_NOT_SEAMLESS | RendererCapabilities.ADAPTIVE_SEAMLESS)
        : RendererCapabilities.ADAPTIVE_SEAMLESS;
    boolean allowMixedMimeTypes =
        params.allowMixedMimeAdaptiveness
            && (mixedMimeTypeAdaptationSupports & requiredAdaptiveSupport) != 0;
    for (int i = 0; i < groups.length; i++) {
      TrackGroup group = groups.get(i);
      int[] adaptiveTracks = getAdaptiveVideoTracksForGroup(group, formatSupport[i],
          allowMixedMimeTypes, requiredAdaptiveSupport, params.maxVideoWidth, params.maxVideoHeight,
          params.maxVideoBitrate, params.viewportWidth, params.viewportHeight,
          params.viewportOrientationMayChange);
      if (adaptiveTracks.length > 0) {
        return Assertions.checkNotNull(adaptiveTrackSelectionFactory)
            .createTrackSelection(group, bandwidthMeter, adaptiveTracks);
      }
    }
    return null;
  }

  private static int[] getAdaptiveVideoTracksForGroup(TrackGroup group, int[] formatSupport,
      boolean allowMixedMimeTypes, int requiredAdaptiveSupport, int maxVideoWidth,
      int maxVideoHeight, int maxVideoBitrate, int viewportWidth, int viewportHeight,
      boolean viewportOrientationMayChange) {
    if (group.length < 2) {
      return NO_TRACKS;
    }

    List<Integer> selectedTrackIndices = getViewportFilteredTrackIndices(group, viewportWidth,
        viewportHeight, viewportOrientationMayChange);
    if (selectedTrackIndices.size() < 2) {
      return NO_TRACKS;
    }

    String selectedMimeType = null;
    if (!allowMixedMimeTypes) {
      // Select the mime type for which we have the most adaptive tracks.
      HashSet<@NullableType String> seenMimeTypes = new HashSet<>();
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

  private static int getAdaptiveVideoTrackCountForMimeType(
      TrackGroup group,
      int[] formatSupport,
      int requiredAdaptiveSupport,
      @Nullable String mimeType,
      int maxVideoWidth,
      int maxVideoHeight,
      int maxVideoBitrate,
      List<Integer> selectedTrackIndices) {
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

  private static void filterAdaptiveVideoTrackCountForMimeType(
      TrackGroup group,
      int[] formatSupport,
      int requiredAdaptiveSupport,
      @Nullable String mimeType,
      int maxVideoWidth,
      int maxVideoHeight,
      int maxVideoBitrate,
      List<Integer> selectedTrackIndices) {
    for (int i = selectedTrackIndices.size() - 1; i >= 0; i--) {
      int trackIndex = selectedTrackIndices.get(i);
      if (!isSupportedAdaptiveVideoTrack(group.getFormat(trackIndex), mimeType,
          formatSupport[trackIndex], requiredAdaptiveSupport, maxVideoWidth, maxVideoHeight,
          maxVideoBitrate)) {
        selectedTrackIndices.remove(i);
      }
    }
  }

  private static boolean isSupportedAdaptiveVideoTrack(
      Format format,
      @Nullable String mimeType,
      int formatSupport,
      int requiredAdaptiveSupport,
      int maxVideoWidth,
      int maxVideoHeight,
      int maxVideoBitrate) {
    return isSupported(formatSupport, false) && ((formatSupport & requiredAdaptiveSupport) != 0)
        && (mimeType == null || Util.areEqual(format.sampleMimeType, mimeType))
        && (format.width == Format.NO_VALUE || format.width <= maxVideoWidth)
        && (format.height == Format.NO_VALUE || format.height <= maxVideoHeight)
        && (format.bitrate == Format.NO_VALUE || format.bitrate <= maxVideoBitrate);
  }

  private static @Nullable TrackSelection selectFixedVideoTrack(
      TrackGroupArray groups, int[][] formatSupports, Parameters params) {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    int selectedTrackScore = 0;
    int selectedBitrate = Format.NO_VALUE;
    int selectedPixelCount = Format.NO_VALUE;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      List<Integer> selectedTrackIndices = getViewportFilteredTrackIndices(trackGroup,
          params.viewportWidth, params.viewportHeight, params.viewportOrientationMayChange);
      int[] trackFormatSupport = formatSupports[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex],
            params.exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          boolean isWithinConstraints = selectedTrackIndices.contains(trackIndex)
              && (format.width == Format.NO_VALUE || format.width <= params.maxVideoWidth)
              && (format.height == Format.NO_VALUE || format.height <= params.maxVideoHeight)
              && (format.bitrate == Format.NO_VALUE || format.bitrate <= params.maxVideoBitrate);
          if (!isWithinConstraints && !params.exceedVideoConstraintsIfNecessary) {
            // Track should not be selected.
            continue;
          }
          int trackScore = isWithinConstraints ? 2 : 1;
          boolean isWithinCapabilities = isSupported(trackFormatSupport[trackIndex], false);
          if (isWithinCapabilities) {
            trackScore += WITHIN_RENDERER_CAPABILITIES_BONUS;
          }
          boolean selectTrack = trackScore > selectedTrackScore;
          if (trackScore == selectedTrackScore) {
            if (params.forceLowestBitrate) {
              // Use bitrate as a tie breaker, preferring the lower bitrate.
              selectTrack = compareFormatValues(format.bitrate, selectedBitrate) < 0;
            } else {
              // Use the pixel count as a tie breaker (or bitrate if pixel counts are tied). If
              // we're within constraints prefer a higher pixel count (or bitrate), else prefer a
              // lower count (or bitrate). If still tied then prefer the first track (i.e. the one
              // that's already selected).
              int formatPixelCount = format.getPixelCount();
              int comparisonResult = formatPixelCount != selectedPixelCount
                  ? compareFormatValues(formatPixelCount, selectedPixelCount)
                  : compareFormatValues(format.bitrate, selectedBitrate);
              selectTrack = isWithinCapabilities && isWithinConstraints
                  ? comparisonResult > 0 : comparisonResult < 0;
            }
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

  // Audio track selection implementation.

  /**
   * Called by {@link #selectAllTracks(MappedTrackInfo, int[][][], int[], Parameters)} to create a
   * {@link TrackSelection} for an audio renderer.
   *
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupports The result of {@link RendererCapabilities#supportsFormat} for each mapped
   *     track, indexed by track group index and track index (in that order).
   * @param mixedMimeTypeAdaptationSupports The result of {@link
   *     RendererCapabilities#supportsMixedMimeTypeAdaptation()} for the renderer.
   * @param params The selector's current constraint parameters.
   * @param adaptiveTrackSelectionFactory A factory for generating adaptive track selections, or
   *     null if a fixed track selection is required.
   * @return The {@link TrackSelection} for the renderer, or null if no selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected @Nullable TrackSelection selectAudioTrack(
      TrackGroupArray groups,
      int[][] formatSupports,
      int mixedMimeTypeAdaptationSupports,
      Parameters params,
      @Nullable TrackSelection.Factory adaptiveTrackSelectionFactory)
      throws ExoPlaybackException {
    int selectedTrackIndex = C.INDEX_UNSET;
    int selectedGroupIndex = C.INDEX_UNSET;
    AudioTrackScore selectedTrackScore = null;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      int[] trackFormatSupport = formatSupports[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex],
            params.exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          AudioTrackScore trackScore =
              new AudioTrackScore(format, params, trackFormatSupport[trackIndex]);
          if (selectedTrackScore == null || trackScore.compareTo(selectedTrackScore) > 0) {
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
    if (!params.forceLowestBitrate && adaptiveTrackSelectionFactory != null) {
      // If the group of the track with the highest score allows it, try to enable adaptation.
      int[] adaptiveTracks =
          getAdaptiveAudioTracks(
              selectedGroup, formatSupports[selectedGroupIndex], params.allowMixedMimeAdaptiveness);
      if (adaptiveTracks.length > 0) {
        return adaptiveTrackSelectionFactory
            .createTrackSelection(selectedGroup, getBandwidthMeter(), adaptiveTracks);
      }
    }
    return new FixedTrackSelection(selectedGroup, selectedTrackIndex);
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
        if (isSupportedAdaptiveAudioTrack(
            group.getFormat(i), formatSupport[i], Assertions.checkNotNull(selectedConfiguration))) {
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

  /**
   * Called by {@link #selectAllTracks(MappedTrackInfo, int[][][], int[], Parameters)} to create a
   * {@link TrackSelection} for a text renderer.
   *
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupport The result of {@link RendererCapabilities#supportsFormat} for each mapped
   *     track, indexed by track group index and track index (in that order).
   * @param params The selector's current constraint parameters.
   * @return The {@link TrackSelection} for the renderer, or null if no selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected @Nullable TrackSelection selectTextTrack(
      TrackGroupArray groups, int[][] formatSupport, Parameters params)
      throws ExoPlaybackException {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    int selectedTrackScore = 0;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex],
            params.exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          int maskedSelectionFlags =
              format.selectionFlags & ~params.disabledTextTrackSelectionFlags;
          boolean isDefault = (maskedSelectionFlags & C.SELECTION_FLAG_DEFAULT) != 0;
          boolean isForced = (maskedSelectionFlags & C.SELECTION_FLAG_FORCED) != 0;
          int trackScore;
          boolean preferredLanguageFound = formatHasLanguage(format, params.preferredTextLanguage);
          if (preferredLanguageFound
              || (params.selectUndeterminedTextLanguage && formatHasNoLanguage(format))) {
            if (isDefault) {
              trackScore = 8;
            } else if (!isForced) {
              // Prefer non-forced to forced if a preferred text language has been specified. Where
              // both are provided the non-forced track will usually contain the forced subtitles as
              // a subset.
              trackScore = 6;
            } else {
              trackScore = 4;
            }
            trackScore += preferredLanguageFound ? 1 : 0;
          } else if (isDefault) {
            trackScore = 3;
          } else if (isForced) {
            if (formatHasLanguage(format, params.preferredAudioLanguage)) {
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

  /**
   * Called by {@link #selectAllTracks(MappedTrackInfo, int[][][], int[], Parameters)} to create a
   * {@link TrackSelection} for a renderer whose type is neither video, audio or text.
   *
   * @param trackType The type of the renderer.
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupport The result of {@link RendererCapabilities#supportsFormat} for each mapped
   *     track, indexed by track group index and track index (in that order).
   * @param params The selector's current constraint parameters.
   * @return The {@link TrackSelection} for the renderer, or null if no selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected @Nullable TrackSelection selectOtherTrack(
      int trackType, TrackGroupArray groups, int[][] formatSupport, Parameters params)
      throws ExoPlaybackException {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    int selectedTrackScore = 0;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex],
            params.exceedRendererCapabilitiesIfNecessary)) {
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

  // Utility methods.

  /**
   * Determines whether tunneling should be enabled, replacing {@link RendererConfiguration}s in
   * {@code rendererConfigurations} with configurations that enable tunneling on the appropriate
   * renderers if so.
   *
   * @param mappedTrackInfo Mapped track information.
   * @param rendererConfigurations The renderer configurations. Configurations may be replaced with
   *     ones that enable tunneling as a result of this call.
   * @param trackSelections The renderer track selections.
   * @param tunnelingAudioSessionId The audio session id to use when tunneling, or {@link
   *     C#AUDIO_SESSION_ID_UNSET} if tunneling should not be enabled.
   */
  private static void maybeConfigureRenderersForTunneling(
      MappedTrackInfo mappedTrackInfo,
      int[][][] renderererFormatSupports,
      @NullableType RendererConfiguration[] rendererConfigurations,
      @NullableType TrackSelection[] trackSelections,
      int tunnelingAudioSessionId) {
    if (tunnelingAudioSessionId == C.AUDIO_SESSION_ID_UNSET) {
      return;
    }
    // Check whether we can enable tunneling. To enable tunneling we require exactly one audio and
    // one video renderer to support tunneling and have a selection.
    int tunnelingAudioRendererIndex = -1;
    int tunnelingVideoRendererIndex = -1;
    boolean enableTunneling = true;
    for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
      int rendererType = mappedTrackInfo.getRendererType(i);
      TrackSelection trackSelection = trackSelections[i];
      if ((rendererType == C.TRACK_TYPE_AUDIO || rendererType == C.TRACK_TYPE_VIDEO)
          && trackSelection != null) {
        if (rendererSupportsTunneling(
            renderererFormatSupports[i], mappedTrackInfo.getTrackGroups(i), trackSelection)) {
          if (rendererType == C.TRACK_TYPE_AUDIO) {
            if (tunnelingAudioRendererIndex != -1) {
              enableTunneling = false;
              break;
            } else {
              tunnelingAudioRendererIndex = i;
            }
          } else {
            if (tunnelingVideoRendererIndex != -1) {
              enableTunneling = false;
              break;
            } else {
              tunnelingVideoRendererIndex = i;
            }
          }
        }
      }
    }
    enableTunneling &= tunnelingAudioRendererIndex != -1 && tunnelingVideoRendererIndex != -1;
    if (enableTunneling) {
      RendererConfiguration tunnelingRendererConfiguration =
          new RendererConfiguration(tunnelingAudioSessionId);
      rendererConfigurations[tunnelingAudioRendererIndex] = tunnelingRendererConfiguration;
      rendererConfigurations[tunnelingVideoRendererIndex] = tunnelingRendererConfiguration;
    }
  }

  /**
   * Returns whether a renderer supports tunneling for a {@link TrackSelection}.
   *
   * @param formatSupports The result of {@link RendererCapabilities#supportsFormat} for each track,
   *     indexed by group index and track index (in that order).
   * @param trackGroups The {@link TrackGroupArray}s for the renderer.
   * @param selection The track selection.
   * @return Whether the renderer supports tunneling for the {@link TrackSelection}.
   */
  private static boolean rendererSupportsTunneling(
      int[][] formatSupports, TrackGroupArray trackGroups, TrackSelection selection) {
    if (selection == null) {
      return false;
    }
    int trackGroupIndex = trackGroups.indexOf(selection.getTrackGroup());
    for (int i = 0; i < selection.length(); i++) {
      int trackFormatSupport = formatSupports[trackGroupIndex][selection.getIndexInTrackGroup(i)];
      if ((trackFormatSupport & RendererCapabilities.TUNNELING_SUPPORT_MASK)
          != RendererCapabilities.TUNNELING_SUPPORTED) {
        return false;
      }
    }
    return true;
  }

  /**
   * Compares two format values for order. A known value is considered greater than {@link
   * Format#NO_VALUE}.
   *
   * @param first The first value.
   * @param second The second value.
   * @return A negative integer if the first value is less than the second. Zero if they are equal.
   *     A positive integer if the first value is greater than the second.
   */
  private static int compareFormatValues(int first, int second) {
    return first == Format.NO_VALUE
        ? (second == Format.NO_VALUE ? 0 : -1)
        : (second == Format.NO_VALUE ? 1 : (first - second));
  }

  /**
   * Applies the {@link RendererCapabilities#FORMAT_SUPPORT_MASK} to a value obtained from
   * {@link RendererCapabilities#supportsFormat(Format)}, returning true if the result is
   * {@link RendererCapabilities#FORMAT_HANDLED} or if {@code allowExceedsCapabilities} is set
   * and the result is {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES}.
   *
   * @param formatSupport A value obtained from {@link RendererCapabilities#supportsFormat(Format)}.
   * @param allowExceedsCapabilities Whether to return true if the format support component of the
   *     value is {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES}.
   * @return True if the format support component is {@link RendererCapabilities#FORMAT_HANDLED}, or
   *     if {@code allowExceedsCapabilities} is set and the format support component is
   *     {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES}.
   */
  protected static boolean isSupported(int formatSupport, boolean allowExceedsCapabilities) {
    int maskedSupport = formatSupport & RendererCapabilities.FORMAT_SUPPORT_MASK;
    return maskedSupport == RendererCapabilities.FORMAT_HANDLED || (allowExceedsCapabilities
        && maskedSupport == RendererCapabilities.FORMAT_EXCEEDS_CAPABILITIES);
  }

  /**
   * Returns whether a {@link Format} does not define a language.
   *
   * @param format The {@link Format}.
   * @return Whether the {@link Format} does not define a language.
   */
  protected static boolean formatHasNoLanguage(Format format) {
    return TextUtils.isEmpty(format.language) || formatHasLanguage(format, C.LANGUAGE_UNDETERMINED);
  }

  /**
   * Returns whether a {@link Format} specifies a particular language, or {@code false} if {@code
   * language} is null.
   *
   * @param format The {@link Format}.
   * @param language The language.
   * @return Whether the format specifies the language, or {@code false} if {@code language} is
   *     null.
   */
  protected static boolean formatHasLanguage(Format format, @Nullable String language) {
    return language != null
        && TextUtils.equals(language, Util.normalizeLanguageCode(format.language));
  }

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

  /**
   * A representation of how well a track fits with our track selection {@link Parameters}.
   *
   * <p>This is used to rank different audio tracks relatively with each other.
   */
  private static final class AudioTrackScore implements Comparable<AudioTrackScore> {
    private final Parameters parameters;
    private final int withinRendererCapabilitiesScore;
    private final int matchLanguageScore;
    private final int defaultSelectionFlagScore;
    private final int channelCount;
    private final int sampleRate;
    private final int bitrate;

    public AudioTrackScore(Format format, Parameters parameters, int formatSupport) {
      this.parameters = parameters;
      withinRendererCapabilitiesScore = isSupported(formatSupport, false) ? 1 : 0;
      matchLanguageScore = formatHasLanguage(format, parameters.preferredAudioLanguage) ? 1 : 0;
      defaultSelectionFlagScore = (format.selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0 ? 1 : 0;
      channelCount = format.channelCount;
      sampleRate = format.sampleRate;
      bitrate = format.bitrate;
    }

    /**
     * Compares the score of the current track format with another {@link AudioTrackScore}.
     *
     * @param other The other score to compare to.
     * @return A positive integer if this score is better than the other. Zero if they are equal. A
     *     negative integer if this score is worse than the other.
     */
    @Override
    public int compareTo(AudioTrackScore other) {
      if (this.withinRendererCapabilitiesScore != other.withinRendererCapabilitiesScore) {
        return compareInts(this.withinRendererCapabilitiesScore,
            other.withinRendererCapabilitiesScore);
      } else if (this.matchLanguageScore != other.matchLanguageScore) {
        return compareInts(this.matchLanguageScore, other.matchLanguageScore);
      } else if (this.defaultSelectionFlagScore != other.defaultSelectionFlagScore) {
        return compareInts(this.defaultSelectionFlagScore, other.defaultSelectionFlagScore);
      } else if (parameters.forceLowestBitrate) {
        return compareInts(other.bitrate, this.bitrate);
      } else {
        // If the format are within renderer capabilities, prefer higher values of channel count,
        // sample rate and bit rate in that order. Otherwise, prefer lower values.
        int resultSign = withinRendererCapabilitiesScore == 1 ? 1 : -1;
        if (this.channelCount != other.channelCount) {
          return resultSign * compareInts(this.channelCount, other.channelCount);
        } else if (this.sampleRate != other.sampleRate) {
          return resultSign * compareInts(this.sampleRate, other.sampleRate);
        }
        return resultSign * compareInts(this.bitrate, other.bitrate);
      }
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      AudioTrackScore that = (AudioTrackScore) o;

      return withinRendererCapabilitiesScore == that.withinRendererCapabilitiesScore
          && matchLanguageScore == that.matchLanguageScore
          && defaultSelectionFlagScore == that.defaultSelectionFlagScore
          && channelCount == that.channelCount && sampleRate == that.sampleRate
          && bitrate == that.bitrate;
    }

    @Override
    public int hashCode() {
      int result = withinRendererCapabilitiesScore;
      result = 31 * result + matchLanguageScore;
      result = 31 * result + defaultSelectionFlagScore;
      result = 31 * result + channelCount;
      result = 31 * result + sampleRate;
      result = 31 * result + bitrate;
      return result;
    }
  }

  /**
   * Compares two integers in a safe way and avoiding potential overflow.
   *
   * @param first The first value.
   * @param second The second value.
   * @return A negative integer if the first value is less than the second. Zero if they are equal.
   *     A positive integer if the first value is greater than the second.
   */
  private static int compareInts(int first, int second) {
    return first > second ? 1 : (second > first ? -1 : 0);
  }

  private static final class AudioConfigurationTuple {

    public final int channelCount;
    public final int sampleRate;
    public final @Nullable String mimeType;

    public AudioConfigurationTuple(int channelCount, int sampleRate, @Nullable String mimeType) {
      this.channelCount = channelCount;
      this.sampleRate = sampleRate;
      this.mimeType = mimeType;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
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

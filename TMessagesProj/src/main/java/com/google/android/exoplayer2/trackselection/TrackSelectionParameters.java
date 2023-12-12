/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.BundleableUtil.toBundleArrayList;
import static com.google.common.base.MoreObjects.firstNonNull;

import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Looper;
import android.view.accessibility.CaptioningManager;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.Bundleable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.util.BundleableUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;

/**
 * Parameters for controlling track selection.
 *
 * <p>Parameters can be queried and set on a {@link Player}. For example the following code modifies
 * the parameters to restrict video track selections to SD, and to select a German audio track if
 * there is one:
 *
 * <pre>{@code
 * // Build on the current parameters.
 * TrackSelectionParameters currentParameters = player.getTrackSelectionParameters()
 * // Build the resulting parameters.
 * TrackSelectionParameters newParameters = currentParameters
 *     .buildUpon()
 *     .setMaxVideoSizeSd()
 *     .setPreferredAudioLanguage("deu")
 *     .build();
 * // Set the new parameters.
 * player.setTrackSelectionParameters(newParameters);
 * }</pre>
 */
public class TrackSelectionParameters implements Bundleable {

  /**
   * A builder for {@link TrackSelectionParameters}. See the {@link TrackSelectionParameters}
   * documentation for explanations of the parameters that can be configured using this builder.
   */
  public static class Builder {
    // Video
    private int maxVideoWidth;
    private int maxVideoHeight;
    private int maxVideoFrameRate;
    private int maxVideoBitrate;
    private int minVideoWidth;
    private int minVideoHeight;
    private int minVideoFrameRate;
    private int minVideoBitrate;
    private int viewportWidth;
    private int viewportHeight;
    private boolean viewportOrientationMayChange;
    private ImmutableList<String> preferredVideoMimeTypes;
    private @C.RoleFlags int preferredVideoRoleFlags;
    // Audio
    private ImmutableList<String> preferredAudioLanguages;
    private @C.RoleFlags int preferredAudioRoleFlags;
    private int maxAudioChannelCount;
    private int maxAudioBitrate;
    private ImmutableList<String> preferredAudioMimeTypes;
    // Text
    private ImmutableList<String> preferredTextLanguages;
    private @C.RoleFlags int preferredTextRoleFlags;
    private @C.SelectionFlags int ignoredTextSelectionFlags;
    private boolean selectUndeterminedTextLanguage;
    // General
    private boolean forceLowestBitrate;
    private boolean forceHighestSupportedBitrate;
    private HashMap<TrackGroup, TrackSelectionOverride> overrides;
    private HashSet<@C.TrackType Integer> disabledTrackTypes;

    /**
     * @deprecated {@link Context} constraints will not be set using this constructor. Use {@link
     *     #Builder(Context)} instead.
     */
    @Deprecated
    public Builder() {
      // Video
      maxVideoWidth = Integer.MAX_VALUE;
      maxVideoHeight = Integer.MAX_VALUE;
      maxVideoFrameRate = Integer.MAX_VALUE;
      maxVideoBitrate = Integer.MAX_VALUE;
      viewportWidth = Integer.MAX_VALUE;
      viewportHeight = Integer.MAX_VALUE;
      viewportOrientationMayChange = true;
      preferredVideoMimeTypes = ImmutableList.of();
      preferredVideoRoleFlags = 0;
      // Audio
      preferredAudioLanguages = ImmutableList.of();
      preferredAudioRoleFlags = 0;
      maxAudioChannelCount = Integer.MAX_VALUE;
      maxAudioBitrate = Integer.MAX_VALUE;
      preferredAudioMimeTypes = ImmutableList.of();
      // Text
      preferredTextLanguages = ImmutableList.of();
      preferredTextRoleFlags = 0;
      ignoredTextSelectionFlags = 0;
      selectUndeterminedTextLanguage = false;
      // General
      forceLowestBitrate = false;
      forceHighestSupportedBitrate = false;
      overrides = new HashMap<>();
      disabledTrackTypes = new HashSet<>();
    }

    /**
     * Creates a builder with default initial values.
     *
     * @param context Any context.
     */
    @SuppressWarnings({"deprecation", "method.invocation"}) // Methods invoked are setter only.
    public Builder(Context context) {
      this();
      setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(context);
      setViewportSizeToPhysicalDisplaySize(context, /* viewportOrientationMayChange= */ true);
    }

    /** Creates a builder with the initial values specified in {@code initialValues}. */
    protected Builder(TrackSelectionParameters initialValues) {
      init(initialValues);
    }

    /** Creates a builder with the initial values specified in {@code bundle}. */
    protected Builder(Bundle bundle) {
      // Video
      maxVideoWidth = bundle.getInt(FIELD_MAX_VIDEO_WIDTH, DEFAULT_WITHOUT_CONTEXT.maxVideoWidth);
      maxVideoHeight =
          bundle.getInt(FIELD_MAX_VIDEO_HEIGHT, DEFAULT_WITHOUT_CONTEXT.maxVideoHeight);
      maxVideoFrameRate =
          bundle.getInt(FIELD_MAX_VIDEO_FRAMERATE, DEFAULT_WITHOUT_CONTEXT.maxVideoFrameRate);
      maxVideoBitrate =
          bundle.getInt(FIELD_MAX_VIDEO_BITRATE, DEFAULT_WITHOUT_CONTEXT.maxVideoBitrate);
      minVideoWidth = bundle.getInt(FIELD_MIN_VIDEO_WIDTH, DEFAULT_WITHOUT_CONTEXT.minVideoWidth);
      minVideoHeight =
          bundle.getInt(FIELD_MIN_VIDEO_HEIGHT, DEFAULT_WITHOUT_CONTEXT.minVideoHeight);
      minVideoFrameRate =
          bundle.getInt(FIELD_MIN_VIDEO_FRAMERATE, DEFAULT_WITHOUT_CONTEXT.minVideoFrameRate);
      minVideoBitrate =
          bundle.getInt(FIELD_MIN_VIDEO_BITRATE, DEFAULT_WITHOUT_CONTEXT.minVideoBitrate);
      viewportWidth = bundle.getInt(FIELD_VIEWPORT_WIDTH, DEFAULT_WITHOUT_CONTEXT.viewportWidth);
      viewportHeight = bundle.getInt(FIELD_VIEWPORT_HEIGHT, DEFAULT_WITHOUT_CONTEXT.viewportHeight);
      viewportOrientationMayChange =
          bundle.getBoolean(
              FIELD_VIEWPORT_ORIENTATION_MAY_CHANGE,
              DEFAULT_WITHOUT_CONTEXT.viewportOrientationMayChange);
      preferredVideoMimeTypes =
          ImmutableList.copyOf(
              firstNonNull(bundle.getStringArray(FIELD_PREFERRED_VIDEO_MIMETYPES), new String[0]));
      preferredVideoRoleFlags =
          bundle.getInt(
              FIELD_PREFERRED_VIDEO_ROLE_FLAGS, DEFAULT_WITHOUT_CONTEXT.preferredVideoRoleFlags);
      // Audio
      String[] preferredAudioLanguages1 =
          firstNonNull(bundle.getStringArray(FIELD_PREFERRED_AUDIO_LANGUAGES), new String[0]);
      preferredAudioLanguages = normalizeLanguageCodes(preferredAudioLanguages1);
      preferredAudioRoleFlags =
          bundle.getInt(
              FIELD_PREFERRED_AUDIO_ROLE_FLAGS, DEFAULT_WITHOUT_CONTEXT.preferredAudioRoleFlags);
      maxAudioChannelCount =
          bundle.getInt(
              FIELD_MAX_AUDIO_CHANNEL_COUNT, DEFAULT_WITHOUT_CONTEXT.maxAudioChannelCount);
      maxAudioBitrate =
          bundle.getInt(FIELD_MAX_AUDIO_BITRATE, DEFAULT_WITHOUT_CONTEXT.maxAudioBitrate);
      preferredAudioMimeTypes =
          ImmutableList.copyOf(
              firstNonNull(bundle.getStringArray(FIELD_PREFERRED_AUDIO_MIME_TYPES), new String[0]));
      // Text
      preferredTextLanguages =
          normalizeLanguageCodes(
              firstNonNull(bundle.getStringArray(FIELD_PREFERRED_TEXT_LANGUAGES), new String[0]));
      preferredTextRoleFlags =
          bundle.getInt(
              FIELD_PREFERRED_TEXT_ROLE_FLAGS, DEFAULT_WITHOUT_CONTEXT.preferredTextRoleFlags);
      ignoredTextSelectionFlags =
          bundle.getInt(
              FIELD_IGNORED_TEXT_SELECTION_FLAGS,
              DEFAULT_WITHOUT_CONTEXT.ignoredTextSelectionFlags);
      selectUndeterminedTextLanguage =
          bundle.getBoolean(
              FIELD_SELECT_UNDETERMINED_TEXT_LANGUAGE,
              DEFAULT_WITHOUT_CONTEXT.selectUndeterminedTextLanguage);
      // General
      forceLowestBitrate =
          bundle.getBoolean(FIELD_FORCE_LOWEST_BITRATE, DEFAULT_WITHOUT_CONTEXT.forceLowestBitrate);
      forceHighestSupportedBitrate =
          bundle.getBoolean(
              FIELD_FORCE_HIGHEST_SUPPORTED_BITRATE,
              DEFAULT_WITHOUT_CONTEXT.forceHighestSupportedBitrate);
      @Nullable
      List<Bundle> overrideBundleList = bundle.getParcelableArrayList(FIELD_SELECTION_OVERRIDES);
      List<TrackSelectionOverride> overrideList =
          overrideBundleList == null
              ? ImmutableList.of()
              : BundleableUtil.fromBundleList(TrackSelectionOverride.CREATOR, overrideBundleList);
      overrides = new HashMap<>();
      for (int i = 0; i < overrideList.size(); i++) {
        TrackSelectionOverride override = overrideList.get(i);
        overrides.put(override.mediaTrackGroup, override);
      }
      int[] disabledTrackTypeArray =
          firstNonNull(bundle.getIntArray(FIELD_DISABLED_TRACK_TYPE), new int[0]);
      disabledTrackTypes = new HashSet<>();
      for (@C.TrackType int disabledTrackType : disabledTrackTypeArray) {
        disabledTrackTypes.add(disabledTrackType);
      }
    }

    /** Overrides the value of the builder with the value of {@link TrackSelectionParameters}. */
    @EnsuresNonNull({
      "preferredVideoMimeTypes",
      "preferredAudioLanguages",
      "preferredAudioMimeTypes",
      "preferredTextLanguages",
      "overrides",
      "disabledTrackTypes",
    })
    private void init(@UnknownInitialization Builder this, TrackSelectionParameters parameters) {
      // Video
      maxVideoWidth = parameters.maxVideoWidth;
      maxVideoHeight = parameters.maxVideoHeight;
      maxVideoFrameRate = parameters.maxVideoFrameRate;
      maxVideoBitrate = parameters.maxVideoBitrate;
      minVideoWidth = parameters.minVideoWidth;
      minVideoHeight = parameters.minVideoHeight;
      minVideoFrameRate = parameters.minVideoFrameRate;
      minVideoBitrate = parameters.minVideoBitrate;
      viewportWidth = parameters.viewportWidth;
      viewportHeight = parameters.viewportHeight;
      viewportOrientationMayChange = parameters.viewportOrientationMayChange;
      preferredVideoMimeTypes = parameters.preferredVideoMimeTypes;
      preferredVideoRoleFlags = parameters.preferredVideoRoleFlags;
      // Audio
      preferredAudioLanguages = parameters.preferredAudioLanguages;
      preferredAudioRoleFlags = parameters.preferredAudioRoleFlags;
      maxAudioChannelCount = parameters.maxAudioChannelCount;
      maxAudioBitrate = parameters.maxAudioBitrate;
      preferredAudioMimeTypes = parameters.preferredAudioMimeTypes;
      // Text
      preferredTextLanguages = parameters.preferredTextLanguages;
      preferredTextRoleFlags = parameters.preferredTextRoleFlags;
      ignoredTextSelectionFlags = parameters.ignoredTextSelectionFlags;
      selectUndeterminedTextLanguage = parameters.selectUndeterminedTextLanguage;
      // General
      forceLowestBitrate = parameters.forceLowestBitrate;
      forceHighestSupportedBitrate = parameters.forceHighestSupportedBitrate;
      disabledTrackTypes = new HashSet<>(parameters.disabledTrackTypes);
      overrides = new HashMap<>(parameters.overrides);
    }

    /** Overrides the value of the builder with the value of {@link TrackSelectionParameters}. */
    @CanIgnoreReturnValue
    protected Builder set(TrackSelectionParameters parameters) {
      init(parameters);
      return this;
    }

    // Video

    /**
     * Equivalent to {@link #setMaxVideoSize setMaxVideoSize(1279, 719)}.
     *
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setMaxVideoSizeSd() {
      return setMaxVideoSize(1279, 719);
    }

    /**
     * Equivalent to {@link #setMaxVideoSize setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)}.
     *
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder clearVideoSizeConstraints() {
      return setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Sets the maximum allowed video width and height.
     *
     * @param maxVideoWidth Maximum allowed video width in pixels.
     * @param maxVideoHeight Maximum allowed video height in pixels.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setMaxVideoSize(int maxVideoWidth, int maxVideoHeight) {
      this.maxVideoWidth = maxVideoWidth;
      this.maxVideoHeight = maxVideoHeight;
      return this;
    }

    /**
     * Sets the maximum allowed video frame rate.
     *
     * @param maxVideoFrameRate Maximum allowed video frame rate in hertz.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setMaxVideoFrameRate(int maxVideoFrameRate) {
      this.maxVideoFrameRate = maxVideoFrameRate;
      return this;
    }

    /**
     * Sets the maximum allowed video bitrate.
     *
     * @param maxVideoBitrate Maximum allowed video bitrate in bits per second.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setMaxVideoBitrate(int maxVideoBitrate) {
      this.maxVideoBitrate = maxVideoBitrate;
      return this;
    }

    /**
     * Sets the minimum allowed video width and height.
     *
     * @param minVideoWidth Minimum allowed video width in pixels.
     * @param minVideoHeight Minimum allowed video height in pixels.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setMinVideoSize(int minVideoWidth, int minVideoHeight) {
      this.minVideoWidth = minVideoWidth;
      this.minVideoHeight = minVideoHeight;
      return this;
    }

    /**
     * Sets the minimum allowed video frame rate.
     *
     * @param minVideoFrameRate Minimum allowed video frame rate in hertz.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setMinVideoFrameRate(int minVideoFrameRate) {
      this.minVideoFrameRate = minVideoFrameRate;
      return this;
    }

    /**
     * Sets the minimum allowed video bitrate.
     *
     * @param minVideoBitrate Minimum allowed video bitrate in bits per second.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setMinVideoBitrate(int minVideoBitrate) {
      this.minVideoBitrate = minVideoBitrate;
      return this;
    }

    /**
     * Equivalent to calling {@link #setViewportSize(int, int, boolean)} with the viewport size
     * obtained from {@link Util#getCurrentDisplayModeSize(Context)}.
     *
     * @param context Any context.
     * @param viewportOrientationMayChange Whether the viewport orientation may change during
     *     playback.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setViewportSizeToPhysicalDisplaySize(
        Context context, boolean viewportOrientationMayChange) {
      // Assume the viewport is fullscreen.
      Point viewportSize = Util.getCurrentDisplayModeSize(context);
      return setViewportSize(viewportSize.x, viewportSize.y, viewportOrientationMayChange);
    }

    /**
     * Equivalent to {@link #setViewportSize setViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE,
     * true)}.
     *
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder clearViewportSizeConstraints() {
      return setViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE, true);
    }

    /**
     * Sets the viewport size to constrain adaptive video selections so that only tracks suitable
     * for the viewport are selected.
     *
     * @param viewportWidth Viewport width in pixels.
     * @param viewportHeight Viewport height in pixels.
     * @param viewportOrientationMayChange Whether the viewport orientation may change during
     *     playback.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setViewportSize(
        int viewportWidth, int viewportHeight, boolean viewportOrientationMayChange) {
      this.viewportWidth = viewportWidth;
      this.viewportHeight = viewportHeight;
      this.viewportOrientationMayChange = viewportOrientationMayChange;
      return this;
    }

    /**
     * Sets the preferred sample MIME type for video tracks.
     *
     * @param mimeType The preferred MIME type for video tracks, or {@code null} to clear a
     *     previously set preference.
     * @return This builder.
     */
    public Builder setPreferredVideoMimeType(@Nullable String mimeType) {
      return mimeType == null ? setPreferredVideoMimeTypes() : setPreferredVideoMimeTypes(mimeType);
    }

    /**
     * Sets the preferred sample MIME types for video tracks.
     *
     * @param mimeTypes The preferred MIME types for video tracks in order of preference, or an
     *     empty list for no preference.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setPreferredVideoMimeTypes(String... mimeTypes) {
      preferredVideoMimeTypes = ImmutableList.copyOf(mimeTypes);
      return this;
    }

    /**
     * Sets the preferred {@link C.RoleFlags} for video tracks.
     *
     * @param preferredVideoRoleFlags Preferred video role flags.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setPreferredVideoRoleFlags(@C.RoleFlags int preferredVideoRoleFlags) {
      this.preferredVideoRoleFlags = preferredVideoRoleFlags;
      return this;
    }

    // Audio

    /**
     * Sets the preferred language for audio and forced text tracks.
     *
     * @param preferredAudioLanguage Preferred audio language as an IETF BCP 47 conformant tag, or
     *     {@code null} to select the default track, or the first track if there's no default.
     * @return This builder.
     */
    public Builder setPreferredAudioLanguage(@Nullable String preferredAudioLanguage) {
      return preferredAudioLanguage == null
          ? setPreferredAudioLanguages()
          : setPreferredAudioLanguages(preferredAudioLanguage);
    }

    /**
     * Sets the preferred languages for audio and forced text tracks.
     *
     * @param preferredAudioLanguages Preferred audio languages as IETF BCP 47 conformant tags in
     *     order of preference, or an empty array to select the default track, or the first track if
     *     there's no default.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setPreferredAudioLanguages(String... preferredAudioLanguages) {
      this.preferredAudioLanguages = normalizeLanguageCodes(preferredAudioLanguages);
      return this;
    }

    /**
     * Sets the preferred {@link C.RoleFlags} for audio tracks.
     *
     * @param preferredAudioRoleFlags Preferred audio role flags.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setPreferredAudioRoleFlags(@C.RoleFlags int preferredAudioRoleFlags) {
      this.preferredAudioRoleFlags = preferredAudioRoleFlags;
      return this;
    }

    /**
     * Sets the maximum allowed audio channel count.
     *
     * @param maxAudioChannelCount Maximum allowed audio channel count.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setMaxAudioChannelCount(int maxAudioChannelCount) {
      this.maxAudioChannelCount = maxAudioChannelCount;
      return this;
    }

    /**
     * Sets the maximum allowed audio bitrate.
     *
     * @param maxAudioBitrate Maximum allowed audio bitrate in bits per second.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setMaxAudioBitrate(int maxAudioBitrate) {
      this.maxAudioBitrate = maxAudioBitrate;
      return this;
    }

    /**
     * Sets the preferred sample MIME type for audio tracks.
     *
     * @param mimeType The preferred MIME type for audio tracks, or {@code null} to clear a
     *     previously set preference.
     * @return This builder.
     */
    public Builder setPreferredAudioMimeType(@Nullable String mimeType) {
      return mimeType == null ? setPreferredAudioMimeTypes() : setPreferredAudioMimeTypes(mimeType);
    }

    /**
     * Sets the preferred sample MIME types for audio tracks.
     *
     * @param mimeTypes The preferred MIME types for audio tracks in order of preference, or an
     *     empty list for no preference.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setPreferredAudioMimeTypes(String... mimeTypes) {
      preferredAudioMimeTypes = ImmutableList.copyOf(mimeTypes);
      return this;
    }

    // Text

    /**
     * Sets the preferred language and role flags for text tracks based on the accessibility
     * settings of {@link CaptioningManager}.
     *
     * <p>Does nothing for API levels &lt; 19 or when the {@link CaptioningManager} is disabled.
     *
     * @param context A {@link Context}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(
        Context context) {
      if (Util.SDK_INT >= 19) {
        setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettingsV19(context);
      }
      return this;
    }

    /**
     * Sets the preferred language for text tracks.
     *
     * @param preferredTextLanguage Preferred text language as an IETF BCP 47 conformant tag, or
     *     {@code null} to select the default track if there is one, or no track otherwise.
     * @return This builder.
     */
    public Builder setPreferredTextLanguage(@Nullable String preferredTextLanguage) {
      return preferredTextLanguage == null
          ? setPreferredTextLanguages()
          : setPreferredTextLanguages(preferredTextLanguage);
    }

    /**
     * Sets the preferred languages for text tracks.
     *
     * @param preferredTextLanguages Preferred text languages as IETF BCP 47 conformant tags in
     *     order of preference, or an empty array to select the default track if there is one, or no
     *     track otherwise.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setPreferredTextLanguages(String... preferredTextLanguages) {
      this.preferredTextLanguages = normalizeLanguageCodes(preferredTextLanguages);
      return this;
    }

    /**
     * Sets the preferred {@link C.RoleFlags} for text tracks.
     *
     * @param preferredTextRoleFlags Preferred text role flags.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setPreferredTextRoleFlags(@C.RoleFlags int preferredTextRoleFlags) {
      this.preferredTextRoleFlags = preferredTextRoleFlags;
      return this;
    }

    /**
     * Sets a bitmask of selection flags that are ignored for text track selections.
     *
     * @param ignoredTextSelectionFlags A bitmask of {@link C.SelectionFlags} that are ignored for
     *     text track selections.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setIgnoredTextSelectionFlags(@C.SelectionFlags int ignoredTextSelectionFlags) {
      this.ignoredTextSelectionFlags = ignoredTextSelectionFlags;
      return this;
    }

    /**
     * Sets whether a text track with undetermined language should be selected if no track with
     * {@link #setPreferredTextLanguages(String...) a preferred language} is available, or if the
     * preferred language is unset.
     *
     * @param selectUndeterminedTextLanguage Whether a text track with undetermined language should
     *     be selected if no preferred language track is available.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setSelectUndeterminedTextLanguage(boolean selectUndeterminedTextLanguage) {
      this.selectUndeterminedTextLanguage = selectUndeterminedTextLanguage;
      return this;
    }

    // General

    /**
     * Sets whether to force selection of the single lowest bitrate audio and video tracks that
     * comply with all other constraints.
     *
     * @param forceLowestBitrate Whether to force selection of the single lowest bitrate audio and
     *     video tracks.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setForceLowestBitrate(boolean forceLowestBitrate) {
      this.forceLowestBitrate = forceLowestBitrate;
      return this;
    }

    /**
     * Sets whether to force selection of the highest bitrate audio and video tracks that comply
     * with all other constraints.
     *
     * @param forceHighestSupportedBitrate Whether to force selection of the highest bitrate audio
     *     and video tracks.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setForceHighestSupportedBitrate(boolean forceHighestSupportedBitrate) {
      this.forceHighestSupportedBitrate = forceHighestSupportedBitrate;
      return this;
    }

    /** Adds an override, replacing any override for the same {@link TrackGroup}. */
    @CanIgnoreReturnValue
    public Builder addOverride(TrackSelectionOverride override) {
      overrides.put(override.mediaTrackGroup, override);
      return this;
    }

    /** Sets an override, replacing all existing overrides with the same track type. */
    @CanIgnoreReturnValue
    public Builder setOverrideForType(TrackSelectionOverride override) {
      clearOverridesOfType(override.getType());
      overrides.put(override.mediaTrackGroup, override);
      return this;
    }

    /** Removes the override for the provided media {@link TrackGroup}, if there is one. */
    @CanIgnoreReturnValue
    public Builder clearOverride(TrackGroup mediaTrackGroup) {
      overrides.remove(mediaTrackGroup);
      return this;
    }

    /** Removes all overrides of the provided track type. */
    @CanIgnoreReturnValue
    public Builder clearOverridesOfType(@C.TrackType int trackType) {
      Iterator<TrackSelectionOverride> it = overrides.values().iterator();
      while (it.hasNext()) {
        TrackSelectionOverride override = it.next();
        if (override.getType() == trackType) {
          it.remove();
        }
      }
      return this;
    }

    /** Removes all overrides. */
    @CanIgnoreReturnValue
    public Builder clearOverrides() {
      overrides.clear();
      return this;
    }

    /**
     * Sets the disabled track types, preventing all tracks of those types from being selected for
     * playback. Any previously disabled track types are cleared.
     *
     * @param disabledTrackTypes The track types to disable.
     * @return This builder.
     * @deprecated Use {@link #setTrackTypeDisabled(int, boolean)}.
     */
    @CanIgnoreReturnValue
    @Deprecated
    public Builder setDisabledTrackTypes(Set<@C.TrackType Integer> disabledTrackTypes) {
      this.disabledTrackTypes.clear();
      this.disabledTrackTypes.addAll(disabledTrackTypes);
      return this;
    }

    /**
     * Sets whether a track type is disabled. If disabled, no tracks of the specified type will be
     * selected for playback.
     *
     * @param trackType The track type.
     * @param disabled Whether the track type should be disabled.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setTrackTypeDisabled(@C.TrackType int trackType, boolean disabled) {
      if (disabled) {
        disabledTrackTypes.add(trackType);
      } else {
        disabledTrackTypes.remove(trackType);
      }
      return this;
    }

    /** Builds a {@link TrackSelectionParameters} instance with the selected values. */
    public TrackSelectionParameters build() {
      return new TrackSelectionParameters(this);
    }

    @RequiresApi(19)
    private void setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettingsV19(
        Context context) {
      if (Util.SDK_INT < 23 && Looper.myLooper() == null) {
        // Android platform bug (pre-Marshmallow) that causes RuntimeExceptions when
        // CaptioningService is instantiated from a non-Looper thread. See [internal: b/143779904].
        return;
      }
      CaptioningManager captioningManager =
          (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
      if (captioningManager == null || !captioningManager.isEnabled()) {
        return;
      }
      preferredTextRoleFlags = C.ROLE_FLAG_CAPTION | C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND;
      Locale preferredLocale = captioningManager.getLocale();
      if (preferredLocale != null) {
        preferredTextLanguages = ImmutableList.of(Util.getLocaleLanguageTag(preferredLocale));
      }
    }

    private static ImmutableList<String> normalizeLanguageCodes(String[] preferredTextLanguages) {
      ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
      for (String language : checkNotNull(preferredTextLanguages)) {
        listBuilder.add(Util.normalizeLanguageCode(checkNotNull(language)));
      }
      return listBuilder.build();
    }
  }

  /**
   * An instance with default values, except those obtained from the {@link Context}.
   *
   * <p>If possible, use {@link #getDefaults(Context)} instead.
   *
   * <p>This instance will not have the following settings:
   *
   * <ul>
   *   <li>{@link Builder#setViewportSizeToPhysicalDisplaySize(Context, boolean) Viewport
   *       constraints} configured for the primary display.
   *   <li>{@link Builder#setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings(Context)
   *       Preferred text language and role flags} configured to the accessibility settings of
   *       {@link CaptioningManager}.
   * </ul>
   */
  @SuppressWarnings("deprecation")
  public static final TrackSelectionParameters DEFAULT_WITHOUT_CONTEXT = new Builder().build();
  /**
   * @deprecated This instance is not configured using {@link Context} constraints. Use {@link
   *     #getDefaults(Context)} instead.
   */
  @Deprecated public static final TrackSelectionParameters DEFAULT = DEFAULT_WITHOUT_CONTEXT;

  /** Returns an instance configured with default values. */
  public static TrackSelectionParameters getDefaults(Context context) {
    return new Builder(context).build();
  }

  // Video
  /**
   * Maximum allowed video width in pixels. The default value is {@link Integer#MAX_VALUE} (i.e. no
   * constraint).
   *
   * <p>To constrain adaptive video track selections to be suitable for a given viewport (the region
   * of the display within which video will be played), use ({@link #viewportWidth}, {@link
   * #viewportHeight} and {@link #viewportOrientationMayChange}) instead.
   */
  public final int maxVideoWidth;
  /**
   * Maximum allowed video height in pixels. The default value is {@link Integer#MAX_VALUE} (i.e. no
   * constraint).
   *
   * <p>To constrain adaptive video track selections to be suitable for a given viewport (the region
   * of the display within which video will be played), use ({@link #viewportWidth}, {@link
   * #viewportHeight} and {@link #viewportOrientationMayChange}) instead.
   */
  public final int maxVideoHeight;
  /**
   * Maximum allowed video frame rate in hertz. The default value is {@link Integer#MAX_VALUE} (i.e.
   * no constraint).
   */
  public final int maxVideoFrameRate;
  /**
   * Maximum allowed video bitrate in bits per second. The default value is {@link
   * Integer#MAX_VALUE} (i.e. no constraint).
   */
  public final int maxVideoBitrate;
  /** Minimum allowed video width in pixels. The default value is 0 (i.e. no constraint). */
  public final int minVideoWidth;
  /** Minimum allowed video height in pixels. The default value is 0 (i.e. no constraint). */
  public final int minVideoHeight;
  /** Minimum allowed video frame rate in hertz. The default value is 0 (i.e. no constraint). */
  public final int minVideoFrameRate;
  /**
   * Minimum allowed video bitrate in bits per second. The default value is 0 (i.e. no constraint).
   */
  public final int minVideoBitrate;
  /**
   * Viewport width in pixels. Constrains video track selections for adaptive content so that only
   * tracks suitable for the viewport are selected. The default value is the physical width of the
   * primary display, in pixels.
   */
  public final int viewportWidth;
  /**
   * Viewport height in pixels. Constrains video track selections for adaptive content so that only
   * tracks suitable for the viewport are selected. The default value is the physical height of the
   * primary display, in pixels.
   */
  public final int viewportHeight;
  /**
   * Whether the viewport orientation may change during playback. Constrains video track selections
   * for adaptive content so that only tracks suitable for the viewport are selected. The default
   * value is {@code true}.
   */
  public final boolean viewportOrientationMayChange;
  /**
   * The preferred sample MIME types for video tracks in order of preference, or an empty list for
   * no preference. The default is an empty list.
   */
  public final ImmutableList<String> preferredVideoMimeTypes;
  /**
   * The preferred {@link C.RoleFlags} for video tracks. {@code 0} selects the default track if
   * there is one, or the first track if there's no default. The default value is {@code 0}.
   */
  public final @C.RoleFlags int preferredVideoRoleFlags;
  // Audio
  /**
   * The preferred languages for audio and forced text tracks as IETF BCP 47 conformant tags in
   * order of preference. An empty list selects the default track, or the first track if there's no
   * default. The default value is an empty list.
   */
  public final ImmutableList<String> preferredAudioLanguages;
  /**
   * The preferred {@link C.RoleFlags} for audio tracks. {@code 0} selects the default track if
   * there is one, or the first track if there's no default. The default value is {@code 0}.
   */
  public final @C.RoleFlags int preferredAudioRoleFlags;
  /**
   * Maximum allowed audio channel count. The default value is {@link Integer#MAX_VALUE} (i.e. no
   * constraint).
   */
  public final int maxAudioChannelCount;
  /**
   * Maximum allowed audio bitrate in bits per second. The default value is {@link
   * Integer#MAX_VALUE} (i.e. no constraint).
   */
  public final int maxAudioBitrate;
  /**
   * The preferred sample MIME types for audio tracks in order of preference, or an empty list for
   * no preference. The default is an empty list.
   */
  public final ImmutableList<String> preferredAudioMimeTypes;
  // Text
  /**
   * The preferred languages for text tracks as IETF BCP 47 conformant tags in order of preference.
   * An empty list selects the default track if there is one, or no track otherwise. The default
   * value is an empty list, or the language of the accessibility {@link CaptioningManager} if
   * enabled.
   */
  public final ImmutableList<String> preferredTextLanguages;
  /**
   * The preferred {@link C.RoleFlags} for text tracks. {@code 0} selects the default track if there
   * is one, or no track otherwise. The default value is {@code 0}, or {@link C#ROLE_FLAG_SUBTITLE}
   * | {@link C#ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND} if the accessibility {@link CaptioningManager}
   * is enabled.
   */
  public final @C.RoleFlags int preferredTextRoleFlags;
  /**
   * Bitmask of selection flags that are ignored for text track selections. See {@link
   * C.SelectionFlags}. The default value is {@code 0} (i.e., no flags are ignored).
   */
  public final @C.SelectionFlags int ignoredTextSelectionFlags;
  /**
   * Whether a text track with undetermined language should be selected if no track with {@link
   * #preferredTextLanguages} is available, or if {@link #preferredTextLanguages} is unset. The
   * default value is {@code false}.
   */
  public final boolean selectUndeterminedTextLanguage;
  // General
  /**
   * Whether to force selection of the single lowest bitrate audio and video tracks that comply with
   * all other constraints. The default value is {@code false}.
   */
  public final boolean forceLowestBitrate;
  /**
   * Whether to force selection of the highest bitrate audio and video tracks that comply with all
   * other constraints. The default value is {@code false}.
   */
  public final boolean forceHighestSupportedBitrate;

  /** Overrides to force selection of specific tracks. */
  public final ImmutableMap<TrackGroup, TrackSelectionOverride> overrides;

  /**
   * The track types that are disabled. No track of a disabled type will be selected, thus no track
   * type contained in the set will be played. The default value is that no track type is disabled
   * (empty set).
   */
  public final ImmutableSet<@C.TrackType Integer> disabledTrackTypes;

  protected TrackSelectionParameters(Builder builder) {
    // Video
    this.maxVideoWidth = builder.maxVideoWidth;
    this.maxVideoHeight = builder.maxVideoHeight;
    this.maxVideoFrameRate = builder.maxVideoFrameRate;
    this.maxVideoBitrate = builder.maxVideoBitrate;
    this.minVideoWidth = builder.minVideoWidth;
    this.minVideoHeight = builder.minVideoHeight;
    this.minVideoFrameRate = builder.minVideoFrameRate;
    this.minVideoBitrate = builder.minVideoBitrate;
    this.viewportWidth = builder.viewportWidth;
    this.viewportHeight = builder.viewportHeight;
    this.viewportOrientationMayChange = builder.viewportOrientationMayChange;
    this.preferredVideoMimeTypes = builder.preferredVideoMimeTypes;
    this.preferredVideoRoleFlags = builder.preferredVideoRoleFlags;
    // Audio
    this.preferredAudioLanguages = builder.preferredAudioLanguages;
    this.preferredAudioRoleFlags = builder.preferredAudioRoleFlags;
    this.maxAudioChannelCount = builder.maxAudioChannelCount;
    this.maxAudioBitrate = builder.maxAudioBitrate;
    this.preferredAudioMimeTypes = builder.preferredAudioMimeTypes;
    // Text
    this.preferredTextLanguages = builder.preferredTextLanguages;
    this.preferredTextRoleFlags = builder.preferredTextRoleFlags;
    this.ignoredTextSelectionFlags = builder.ignoredTextSelectionFlags;
    this.selectUndeterminedTextLanguage = builder.selectUndeterminedTextLanguage;
    // General
    this.forceLowestBitrate = builder.forceLowestBitrate;
    this.forceHighestSupportedBitrate = builder.forceHighestSupportedBitrate;
    this.overrides = ImmutableMap.copyOf(builder.overrides);
    this.disabledTrackTypes = ImmutableSet.copyOf(builder.disabledTrackTypes);
  }

  /** Creates a new {@link Builder}, copying the initial values from this instance. */
  public Builder buildUpon() {
    return new Builder(this);
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TrackSelectionParameters other = (TrackSelectionParameters) obj;
    // Video
    return maxVideoWidth == other.maxVideoWidth
        && maxVideoHeight == other.maxVideoHeight
        && maxVideoFrameRate == other.maxVideoFrameRate
        && maxVideoBitrate == other.maxVideoBitrate
        && minVideoWidth == other.minVideoWidth
        && minVideoHeight == other.minVideoHeight
        && minVideoFrameRate == other.minVideoFrameRate
        && minVideoBitrate == other.minVideoBitrate
        && viewportOrientationMayChange == other.viewportOrientationMayChange
        && viewportWidth == other.viewportWidth
        && viewportHeight == other.viewportHeight
        && preferredVideoMimeTypes.equals(other.preferredVideoMimeTypes)
        && preferredVideoRoleFlags == other.preferredVideoRoleFlags
        // Audio
        && preferredAudioLanguages.equals(other.preferredAudioLanguages)
        && preferredAudioRoleFlags == other.preferredAudioRoleFlags
        && maxAudioChannelCount == other.maxAudioChannelCount
        && maxAudioBitrate == other.maxAudioBitrate
        && preferredAudioMimeTypes.equals(other.preferredAudioMimeTypes)
        // Text
        && preferredTextLanguages.equals(other.preferredTextLanguages)
        && preferredTextRoleFlags == other.preferredTextRoleFlags
        && ignoredTextSelectionFlags == other.ignoredTextSelectionFlags
        && selectUndeterminedTextLanguage == other.selectUndeterminedTextLanguage
        // General
        && forceLowestBitrate == other.forceLowestBitrate
        && forceHighestSupportedBitrate == other.forceHighestSupportedBitrate
        && overrides.equals(other.overrides)
        && disabledTrackTypes.equals(other.disabledTrackTypes);
  }

  @Override
  public int hashCode() {
    int result = 1;
    // Video
    result = 31 * result + maxVideoWidth;
    result = 31 * result + maxVideoHeight;
    result = 31 * result + maxVideoFrameRate;
    result = 31 * result + maxVideoBitrate;
    result = 31 * result + minVideoWidth;
    result = 31 * result + minVideoHeight;
    result = 31 * result + minVideoFrameRate;
    result = 31 * result + minVideoBitrate;
    result = 31 * result + (viewportOrientationMayChange ? 1 : 0);
    result = 31 * result + viewportWidth;
    result = 31 * result + viewportHeight;
    result = 31 * result + preferredVideoMimeTypes.hashCode();
    result = 31 * result + preferredVideoRoleFlags;
    // Audio
    result = 31 * result + preferredAudioLanguages.hashCode();
    result = 31 * result + preferredAudioRoleFlags;
    result = 31 * result + maxAudioChannelCount;
    result = 31 * result + maxAudioBitrate;
    result = 31 * result + preferredAudioMimeTypes.hashCode();
    // Text
    result = 31 * result + preferredTextLanguages.hashCode();
    result = 31 * result + preferredTextRoleFlags;
    result = 31 * result + ignoredTextSelectionFlags;
    result = 31 * result + (selectUndeterminedTextLanguage ? 1 : 0);
    // General
    result = 31 * result + (forceLowestBitrate ? 1 : 0);
    result = 31 * result + (forceHighestSupportedBitrate ? 1 : 0);
    result = 31 * result + overrides.hashCode();
    result = 31 * result + disabledTrackTypes.hashCode();
    return result;
  }

  // Bundleable implementation

  private static final String FIELD_PREFERRED_AUDIO_LANGUAGES = Util.intToStringMaxRadix(1);
  private static final String FIELD_PREFERRED_AUDIO_ROLE_FLAGS = Util.intToStringMaxRadix(2);
  private static final String FIELD_PREFERRED_TEXT_LANGUAGES = Util.intToStringMaxRadix(3);
  private static final String FIELD_PREFERRED_TEXT_ROLE_FLAGS = Util.intToStringMaxRadix(4);
  private static final String FIELD_SELECT_UNDETERMINED_TEXT_LANGUAGE = Util.intToStringMaxRadix(5);
  private static final String FIELD_MAX_VIDEO_WIDTH = Util.intToStringMaxRadix(6);
  private static final String FIELD_MAX_VIDEO_HEIGHT = Util.intToStringMaxRadix(7);
  private static final String FIELD_MAX_VIDEO_FRAMERATE = Util.intToStringMaxRadix(8);
  private static final String FIELD_MAX_VIDEO_BITRATE = Util.intToStringMaxRadix(9);
  private static final String FIELD_MIN_VIDEO_WIDTH = Util.intToStringMaxRadix(10);
  private static final String FIELD_MIN_VIDEO_HEIGHT = Util.intToStringMaxRadix(11);
  private static final String FIELD_MIN_VIDEO_FRAMERATE = Util.intToStringMaxRadix(12);
  private static final String FIELD_MIN_VIDEO_BITRATE = Util.intToStringMaxRadix(13);
  private static final String FIELD_VIEWPORT_WIDTH = Util.intToStringMaxRadix(14);
  private static final String FIELD_VIEWPORT_HEIGHT = Util.intToStringMaxRadix(15);
  private static final String FIELD_VIEWPORT_ORIENTATION_MAY_CHANGE = Util.intToStringMaxRadix(16);
  private static final String FIELD_PREFERRED_VIDEO_MIMETYPES = Util.intToStringMaxRadix(17);
  private static final String FIELD_MAX_AUDIO_CHANNEL_COUNT = Util.intToStringMaxRadix(18);
  private static final String FIELD_MAX_AUDIO_BITRATE = Util.intToStringMaxRadix(19);
  private static final String FIELD_PREFERRED_AUDIO_MIME_TYPES = Util.intToStringMaxRadix(20);
  private static final String FIELD_FORCE_LOWEST_BITRATE = Util.intToStringMaxRadix(21);
  private static final String FIELD_FORCE_HIGHEST_SUPPORTED_BITRATE = Util.intToStringMaxRadix(22);
  private static final String FIELD_SELECTION_OVERRIDES = Util.intToStringMaxRadix(23);
  private static final String FIELD_DISABLED_TRACK_TYPE = Util.intToStringMaxRadix(24);
  private static final String FIELD_PREFERRED_VIDEO_ROLE_FLAGS = Util.intToStringMaxRadix(25);
  private static final String FIELD_IGNORED_TEXT_SELECTION_FLAGS = Util.intToStringMaxRadix(26);

  /**
   * Defines a minimum field ID value for subclasses to use when implementing {@link #toBundle()}
   * and {@link Bundleable.Creator}.
   *
   * <p>Subclasses should obtain keys for their {@link Bundle} representation by applying a
   * non-negative offset on this constant and passing the result to {@link
   * Util#intToStringMaxRadix(int)}.
   */
  protected static final int FIELD_CUSTOM_ID_BASE = 1000;

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();

    // Video
    bundle.putInt(FIELD_MAX_VIDEO_WIDTH, maxVideoWidth);
    bundle.putInt(FIELD_MAX_VIDEO_HEIGHT, maxVideoHeight);
    bundle.putInt(FIELD_MAX_VIDEO_FRAMERATE, maxVideoFrameRate);
    bundle.putInt(FIELD_MAX_VIDEO_BITRATE, maxVideoBitrate);
    bundle.putInt(FIELD_MIN_VIDEO_WIDTH, minVideoWidth);
    bundle.putInt(FIELD_MIN_VIDEO_HEIGHT, minVideoHeight);
    bundle.putInt(FIELD_MIN_VIDEO_FRAMERATE, minVideoFrameRate);
    bundle.putInt(FIELD_MIN_VIDEO_BITRATE, minVideoBitrate);
    bundle.putInt(FIELD_VIEWPORT_WIDTH, viewportWidth);
    bundle.putInt(FIELD_VIEWPORT_HEIGHT, viewportHeight);
    bundle.putBoolean(FIELD_VIEWPORT_ORIENTATION_MAY_CHANGE, viewportOrientationMayChange);
    bundle.putStringArray(
        FIELD_PREFERRED_VIDEO_MIMETYPES, preferredVideoMimeTypes.toArray(new String[0]));
    bundle.putInt(FIELD_PREFERRED_VIDEO_ROLE_FLAGS, preferredVideoRoleFlags);
    // Audio
    bundle.putStringArray(
        FIELD_PREFERRED_AUDIO_LANGUAGES, preferredAudioLanguages.toArray(new String[0]));
    bundle.putInt(FIELD_PREFERRED_AUDIO_ROLE_FLAGS, preferredAudioRoleFlags);
    bundle.putInt(FIELD_MAX_AUDIO_CHANNEL_COUNT, maxAudioChannelCount);
    bundle.putInt(FIELD_MAX_AUDIO_BITRATE, maxAudioBitrate);
    bundle.putStringArray(
        FIELD_PREFERRED_AUDIO_MIME_TYPES, preferredAudioMimeTypes.toArray(new String[0]));
    // Text
    bundle.putStringArray(
        FIELD_PREFERRED_TEXT_LANGUAGES, preferredTextLanguages.toArray(new String[0]));
    bundle.putInt(FIELD_PREFERRED_TEXT_ROLE_FLAGS, preferredTextRoleFlags);
    bundle.putInt(FIELD_IGNORED_TEXT_SELECTION_FLAGS, ignoredTextSelectionFlags);
    bundle.putBoolean(FIELD_SELECT_UNDETERMINED_TEXT_LANGUAGE, selectUndeterminedTextLanguage);
    // General
    bundle.putBoolean(FIELD_FORCE_LOWEST_BITRATE, forceLowestBitrate);
    bundle.putBoolean(FIELD_FORCE_HIGHEST_SUPPORTED_BITRATE, forceHighestSupportedBitrate);
    bundle.putParcelableArrayList(FIELD_SELECTION_OVERRIDES, toBundleArrayList(overrides.values()));
    bundle.putIntArray(FIELD_DISABLED_TRACK_TYPE, Ints.toArray(disabledTrackTypes));

    return bundle;
  }

  /** Construct an instance from a {@link Bundle} produced by {@link #toBundle()}. */
  public static TrackSelectionParameters fromBundle(Bundle bundle) {
    return new Builder(bundle).build();
  }

  /**
   * @deprecated Use {@link #fromBundle(Bundle)} instead.
   */
  @Deprecated
  public static final Creator<TrackSelectionParameters> CREATOR =
      TrackSelectionParameters::fromBundle;
}

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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.view.View;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Provides information about an overlay view shown on top of an ad view group. */
public final class AdOverlayInfo {

  /**
   * The purpose of the overlay. One of {@link #PURPOSE_CONTROLS}, {@link #PURPOSE_CLOSE_AD}, {@link
   * #PURPOSE_OTHER} or {@link #PURPOSE_NOT_VISIBLE}.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({PURPOSE_CONTROLS, PURPOSE_CLOSE_AD, PURPOSE_OTHER, PURPOSE_NOT_VISIBLE})
  public @interface Purpose {}
  /** Purpose for playback controls overlaying the player. */
  public static final int PURPOSE_CONTROLS = 1;
  /** Purpose for ad close buttons overlaying the player. */
  public static final int PURPOSE_CLOSE_AD = 2;
  /** Purpose for other overlays. */
  public static final int PURPOSE_OTHER = 3;
  /** Purpose for overlays that are not visible. */
  public static final int PURPOSE_NOT_VISIBLE = 4;

  /** A builder for {@link AdOverlayInfo} instances. */
  public static final class Builder {

    private final View view;
    private final @Purpose int purpose;

    @Nullable private String detailedReason;

    /**
     * Creates a new builder.
     *
     * @param view The view that is overlaying the player.
     * @param purpose The purpose of the view.
     */
    public Builder(View view, @Purpose int purpose) {
      this.view = view;
      this.purpose = purpose;
    }

    /**
     * Sets an optional, detailed reason that the view is on top of the player.
     *
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setDetailedReason(@Nullable String detailedReason) {
      this.detailedReason = detailedReason;
      return this;
    }

    /** Returns a new {@link AdOverlayInfo} instance with the current builder values. */
    // Using deprecated constructor while it still exists.
    @SuppressWarnings("deprecation")
    public AdOverlayInfo build() {
      return new AdOverlayInfo(view, purpose, detailedReason);
    }
  }

  /** The overlay view. */
  public final View view;
  /** The purpose of the overlay view. */
  public final @Purpose int purpose;
  /** An optional, detailed reason that the overlay view is needed. */
  @Nullable public final String reasonDetail;

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public AdOverlayInfo(View view, @Purpose int purpose) {
    this(view, purpose, /* detailedReason= */ null);
  }

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public AdOverlayInfo(View view, @Purpose int purpose, @Nullable String detailedReason) {
    this.view = view;
    this.purpose = purpose;
    this.reasonDetail = detailedReason;
  }
}

/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Objects;

/** A rating expressed as "thumbs up" or "thumbs down". */
public final class ThumbRating extends Rating {

  private final boolean rated;
  private final boolean isThumbsUp;

  /** Creates a unrated instance. */
  public ThumbRating() {
    rated = false;
    isThumbsUp = false;
  }

  /**
   * Creates a rated instance.
   *
   * @param isThumbsUp {@code true} for "thumbs up", {@code false} for "thumbs down".
   */
  public ThumbRating(boolean isThumbsUp) {
    rated = true;
    this.isThumbsUp = isThumbsUp;
  }

  @Override
  public boolean isRated() {
    return rated;
  }

  /** Returns whether the rating is "thumbs up". */
  public boolean isThumbsUp() {
    return isThumbsUp;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(rated, isThumbsUp);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof ThumbRating)) {
      return false;
    }
    ThumbRating other = (ThumbRating) obj;
    return isThumbsUp == other.isThumbsUp && rated == other.rated;
  }

  // Bundleable implementation.

  private static final @RatingType int TYPE = RATING_TYPE_THUMB;

  private static final String FIELD_RATED = Util.intToStringMaxRadix(1);
  private static final String FIELD_IS_THUMBS_UP = Util.intToStringMaxRadix(2);

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_RATING_TYPE, TYPE);
    bundle.putBoolean(FIELD_RATED, rated);
    bundle.putBoolean(FIELD_IS_THUMBS_UP, isThumbsUp);
    return bundle;
  }

  /** Object that can restore a {@link ThumbRating} from a {@link Bundle}. */
  public static final Creator<ThumbRating> CREATOR = ThumbRating::fromBundle;

  private static ThumbRating fromBundle(Bundle bundle) {
    checkArgument(bundle.getInt(FIELD_RATING_TYPE, /* defaultValue= */ RATING_TYPE_UNSET) == TYPE);
    boolean rated = bundle.getBoolean(FIELD_RATED, /* defaultValue= */ false);
    return rated
        ? new ThumbRating(bundle.getBoolean(FIELD_IS_THUMBS_UP, /* defaultValue= */ false))
        : new ThumbRating();
  }
}

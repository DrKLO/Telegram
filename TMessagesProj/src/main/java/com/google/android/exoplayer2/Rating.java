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

import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import androidx.annotation.IntDef;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A rating for media content. The style of a rating can be one of {@link HeartRating}, {@link
 * PercentageRating}, {@link StarRating}, or {@link ThumbRating}.
 */
public abstract class Rating implements Bundleable {

  /** A float value that denotes the rating is unset. */
  /* package */ static final float RATING_UNSET = -1.0f;

  // Default package-private constructor to prevent extending Rating class outside this package.
  /* package */ Rating() {}

  /** Whether the rating exists or not. */
  public abstract boolean isRated();

  // Bundleable implementation.

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    RATING_TYPE_UNSET,
    RATING_TYPE_HEART,
    RATING_TYPE_PERCENTAGE,
    RATING_TYPE_STAR,
    RATING_TYPE_THUMB
  })
  /* package */ @interface RatingType {}

  /* package */ static final int RATING_TYPE_UNSET = -1;
  /* package */ static final int RATING_TYPE_HEART = 0;
  /* package */ static final int RATING_TYPE_PERCENTAGE = 1;
  /* package */ static final int RATING_TYPE_STAR = 2;
  /* package */ static final int RATING_TYPE_THUMB = 3;

  /* package */ static final String FIELD_RATING_TYPE = Util.intToStringMaxRadix(0);

  /** Object that can restore a {@link Rating} from a {@link Bundle}. */
  public static final Creator<Rating> CREATOR = Rating::fromBundle;

  private static Rating fromBundle(Bundle bundle) {
    @RatingType
    int ratingType = bundle.getInt(FIELD_RATING_TYPE, /* defaultValue= */ RATING_TYPE_UNSET);
    switch (ratingType) {
      case RATING_TYPE_HEART:
        return HeartRating.CREATOR.fromBundle(bundle);
      case RATING_TYPE_PERCENTAGE:
        return PercentageRating.CREATOR.fromBundle(bundle);
      case RATING_TYPE_STAR:
        return StarRating.CREATOR.fromBundle(bundle);
      case RATING_TYPE_THUMB:
        return ThumbRating.CREATOR.fromBundle(bundle);
      case RATING_TYPE_UNSET:
      default:
        throw new IllegalArgumentException("Unknown RatingType: " + ratingType);
    }
  }
}

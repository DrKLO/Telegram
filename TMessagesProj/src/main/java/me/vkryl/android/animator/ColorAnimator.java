/*
 * This file is a part of X-Android
 * Copyright © Vyacheslav Krylov 2014
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
 *
 * File created on 23/03/2023
 */

package me.vkryl.android.animator;

import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import me.vkryl.core.lambda.FutureFloat;
import me.vkryl.core.lambda.FutureInt;

public class ColorAnimator extends AtomicAnimator<ColorAnimator.FutureColor> implements FutureInt {
  public interface FutureColor extends FutureFloat, FutureInt {
    @Override
    default float getFloatValue () {
      return Float.intBitsToFloat(getIntValue());
    }
  }
  public ColorAnimator (Target<FutureColor> target, Interpolator interpolator, long duration, @NonNull FutureColor currentValue) {
    super(target, interpolator, duration, currentValue);
  }

  @Override
  protected float interpolate (FutureFloat fromValue, FutureColor toValue, float factor) {
    return Float.intBitsToFloat(
      ColorUtils.blendARGB(
        Float.floatToRawIntBits(fromValue.getFloatValue()),
        toValue.getIntValue(),
        factor
      )
    );
  }

  @Override
  public int getIntValue () {
    return Float.floatToRawIntBits(getFloatValue());
  }
}

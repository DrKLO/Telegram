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
 * File created on 10/11/17
 */

package me.vkryl.android.animator;

import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;

public class BoolAnimator implements FactorAnimator.Target {
  private final int id;
  private final FactorAnimator.Target target;
  private Interpolator interpolator;
  private long duration;
  private long startDelay;

  public BoolAnimator (View view, Interpolator interpolator, long duration) {
    this(0, (id, factor, fraction, callee) -> view.invalidate(), interpolator, duration, false);
  }

  public BoolAnimator (View view, Interpolator interpolator, long duration, boolean startValue) {
    this(0, (id, factor, fraction, callee) -> view.invalidate(), interpolator, duration, startValue);
  }

  public BoolAnimator (int id, FactorAnimator.Target target, Interpolator interpolator, long duration) {
    this(id, target, interpolator, duration, false);
  }

  public BoolAnimator (int id, FactorAnimator.Target target, Interpolator interpolator, long duration, boolean startValue) {
    this.id = id;
    this.target = target;
    this.interpolator = interpolator;
    this.duration = duration;
    this.value = startValue;
    this.floatValue = startValue ? 1f : 0f;
  }

  public void setStartDelay (long delay) {
    this.startDelay = delay;
    if (animator != null) {
      animator.setStartDelay(delay);
    }
  }

  public void setDuration (long duration) {
    this.duration = duration;
    if (animator != null) {
      animator.setDuration(duration);
    }
  }

  public void setInterpolator (Interpolator interpolator) {
    this.interpolator = interpolator;
    if (animator != null) {
      animator.setInterpolator(interpolator);
    }
  }

  private float floatValue;

  private boolean value;
  private FactorAnimator animator;

  public boolean isAnimating () {
    return animator != null && animator.isAnimating();
  }

  public boolean toggleValue (boolean animated) {
    setValue(!value, animated);
    return value;
  }

  public void setValue (boolean value, boolean animated) {
    setValue(value, animated, null);
  }

  public void forceValue (boolean value, float floatValue) {
    this.value = value;
    if (animator != null) {
      animator.forceFactor(floatValue);
    }
    setFloatValue(floatValue);
  }

  public void setValue (boolean value, boolean animated, @Nullable View view) {
    if (this.value != value || !animated) {
      this.value = value;
      final float toValue = value ? 1f : 0f;
      if (animated) {
        if (animator == null) {
          animator = new FactorAnimator(0, this, interpolator, duration, floatValue);
          if (startDelay != 0) {
            animator.setStartDelay(startDelay);
          }
        }
        animator.animateTo(toValue, view);
      } else {
        if (animator != null) {
          animator.forceFactor(toValue);
        }
        if (this.floatValue != toValue) {
          setFloatValue(toValue);
          target.onFactorChangeFinished(id, toValue, null);
        }
      }
    }
  }

  public void cancel () {
    if (animator != null) {
      animator.cancel();
    }
  }

  public void changeValueSilently (boolean newValue) {
    this.value = newValue;
  }

  public void changeValueSilently (float newValue) {
    this.floatValue = newValue;
  }

  public boolean getValue () {
    return value;
  }

  public float getFloatValue () {
    return floatValue;
  }

  private void setFloatValue (float value) {
    if (this.floatValue != value) {
      this.floatValue = value;
      target.onFactorChanged(id, value, -1f, null);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setFloatValue(factor);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    target.onFactorChangeFinished(this.id, finalFactor, null);
  }
}

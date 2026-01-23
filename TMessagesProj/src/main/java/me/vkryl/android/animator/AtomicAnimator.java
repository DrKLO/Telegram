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
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

import me.vkryl.core.lambda.FutureFloat;

public class AtomicAnimator<AtomicValue extends FutureFloat> implements FutureFloat {
  private static class TemporaryValue<AtomicValue extends FutureFloat> implements FutureFloat {
    private final AtomicAnimator<AtomicValue> animator;
    private final FutureFloat fromValue;
    private final AtomicValue toValue;
    private final float factor;
    public TemporaryValue (AtomicAnimator<AtomicValue> animator, FutureFloat fromValue, AtomicValue toValue, float factor) {
      this.animator = animator;
      this.fromValue = fromValue;
      this.toValue = toValue;
      this.factor = factor;
    }
    @Override
    public float getFloatValue () {
      return animator.interpolate(
        fromValue,
        toValue,
        factor
      );
    }
  }

  public interface Target<AtomicValue extends FutureFloat> {
    void onAtomicValueUpdate (AtomicAnimator<AtomicValue> animator, float newValue);
  }

  private final @NonNull Target<AtomicValue> target;

  private @NonNull FutureFloat currentValue;
  private @Nullable AtomicValue futureValue;
  private final @NonNull FactorAnimator animator;
  public AtomicAnimator (@NonNull Target<AtomicValue> target, Interpolator interpolator, long duration, @NonNull AtomicValue currentValue) {
    this.target = target;
    this.currentValue = currentValue;
    this.lastNotifiedValue = currentValue.getFloatValue();
    this.animator = new FactorAnimator(0, new FactorAnimator.Target() {
      @Override
      public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        notifyValueChanged(false);
      }

      @Override
      public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
        applyCurrentValue(true);
      }
    }, interpolator, duration);
  }
  protected void applyCurrentValue (boolean forceApplyFuture) {
    if (futureValue != null) {
      float factor = animator.getFactor();
      if (factor == 1f || forceApplyFuture) {
        currentValue = futureValue;
        futureValue = null;
      } else if (factor == 0f) {
        futureValue = null;
      } else {
        currentValue = new TemporaryValue<>(this, currentValue, futureValue, factor);
        futureValue = null;
      }
      animator.forceFactor(0f);
    }
  }

  public final void setValue (AtomicValue atomicValue, boolean animated) {
    float lastValue = getFloatValue();
    if (animated) {
      applyCurrentValue(false);
      this.futureValue = atomicValue;
      animator.animateTo(1f);
    } else {
      this.animator.cancel();
      this.futureValue = null;
      this.currentValue = atomicValue;
      this.animator.forceFactor(0f);
      notifyValueChanged(lastValue != getFloatValue());
    }
  }

  protected float interpolate (FutureFloat fromValue, AtomicValue toValue, float factor) {
    return AndroidUtilities.lerp(
      fromValue.getFloatValue(),
      toValue.getFloatValue(),
      factor
    );
  }

  @Override
  public final float getFloatValue () {
    if (futureValue != null) {
      float factor = animator.getFactor();
      if (factor == 1f) {
        return futureValue.getFloatValue();
      } else if (factor > 0f) {
        return interpolate(currentValue, futureValue, factor);
      }
    }
    return currentValue.getFloatValue();
  }

  private float lastNotifiedValue;

  private void notifyValueChanged (boolean force) {
    float newValue = getFloatValue();
    if (newValue != lastNotifiedValue || force) {
      this.lastNotifiedValue = newValue;
      target.onAtomicValueUpdate(this, newValue);
    }
  }
}

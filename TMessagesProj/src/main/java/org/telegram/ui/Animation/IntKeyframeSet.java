/*
 * Copyright (C) 2010 The Android Open Source Project
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

package org.telegram.ui.Animation;

import android.view.animation.Interpolator;

import org.telegram.ui.Animation.Keyframe.IntKeyframe;

import java.util.ArrayList;

class IntKeyframeSet extends KeyframeSet {
    private int firstValue;
    private int lastValue;
    private int deltaValue;
    private boolean firstTime = true;

    public IntKeyframeSet(IntKeyframe... keyframes) {
        super(keyframes);
    }

    @Override
    public Object getValue(float fraction) {
        return getIntValue(fraction);
    }

    @Override
    public IntKeyframeSet clone() {
        ArrayList<Keyframe> keyframes = mKeyframes;
        int numKeyframes = mKeyframes.size();
        IntKeyframe[] newKeyframes = new IntKeyframe[numKeyframes];
        for (int i = 0; i < numKeyframes; ++i) {
            newKeyframes[i] = (IntKeyframe) keyframes.get(i).clone();
        }
        return new IntKeyframeSet(newKeyframes);
    }

    @SuppressWarnings("unchecked")
    public int getIntValue(float fraction) {
        if (mNumKeyframes == 2) {
            if (firstTime) {
                firstTime = false;
                firstValue = ((IntKeyframe) mKeyframes.get(0)).getIntValue();
                lastValue = ((IntKeyframe) mKeyframes.get(1)).getIntValue();
                deltaValue = lastValue - firstValue;
            }
            if (mInterpolator != null) {
                fraction = mInterpolator.getInterpolation(fraction);
            }
            if (mEvaluator == null) {
                return firstValue + (int)(fraction * deltaValue);
            } else {
                return ((Number)mEvaluator.evaluate(fraction, firstValue, lastValue)).intValue();
            }
        }
        if (fraction <= 0f) {
            final IntKeyframe prevKeyframe = (IntKeyframe) mKeyframes.get(0);
            final IntKeyframe nextKeyframe = (IntKeyframe) mKeyframes.get(1);
            int prevValue = prevKeyframe.getIntValue();
            int nextValue = nextKeyframe.getIntValue();
            float prevFraction = prevKeyframe.getFraction();
            float nextFraction = nextKeyframe.getFraction();
            final Interpolator interpolator = nextKeyframe.getInterpolator();
            if (interpolator != null) {
                fraction = interpolator.getInterpolation(fraction);
            }
            float intervalFraction = (fraction - prevFraction) / (nextFraction - prevFraction);
            return mEvaluator == null ? prevValue + (int)(intervalFraction * (nextValue - prevValue)) : ((Number)mEvaluator.evaluate(intervalFraction, prevValue, nextValue)).intValue();
        } else if (fraction >= 1f) {
            final IntKeyframe prevKeyframe = (IntKeyframe) mKeyframes.get(mNumKeyframes - 2);
            final IntKeyframe nextKeyframe = (IntKeyframe) mKeyframes.get(mNumKeyframes - 1);
            int prevValue = prevKeyframe.getIntValue();
            int nextValue = nextKeyframe.getIntValue();
            float prevFraction = prevKeyframe.getFraction();
            float nextFraction = nextKeyframe.getFraction();
            final Interpolator interpolator = nextKeyframe.getInterpolator();
            if (interpolator != null) {
                fraction = interpolator.getInterpolation(fraction);
            }
            float intervalFraction = (fraction - prevFraction) / (nextFraction - prevFraction);
            return mEvaluator == null ? prevValue + (int)(intervalFraction * (nextValue - prevValue)) : ((Number)mEvaluator.evaluate(intervalFraction, prevValue, nextValue)).intValue();
        }
        IntKeyframe prevKeyframe = (IntKeyframe) mKeyframes.get(0);
        for (int i = 1; i < mNumKeyframes; ++i) {
            IntKeyframe nextKeyframe = (IntKeyframe) mKeyframes.get(i);
            if (fraction < nextKeyframe.getFraction()) {
                final Interpolator interpolator = nextKeyframe.getInterpolator();
                if (interpolator != null) {
                    fraction = interpolator.getInterpolation(fraction);
                }
                float intervalFraction = (fraction - prevKeyframe.getFraction()) / (nextKeyframe.getFraction() - prevKeyframe.getFraction());
                int prevValue = prevKeyframe.getIntValue();
                int nextValue = nextKeyframe.getIntValue();
                return mEvaluator == null ? prevValue + (int)(intervalFraction * (nextValue - prevValue)) : ((Number)mEvaluator.evaluate(intervalFraction, prevValue, nextValue)).intValue();
            }
            prevKeyframe = nextKeyframe;
        }
        return ((Number)mKeyframes.get(mNumKeyframes - 1).getValue()).intValue();
    }
}


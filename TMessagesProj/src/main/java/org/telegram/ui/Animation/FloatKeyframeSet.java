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

import org.telegram.ui.Animation.Keyframe.FloatKeyframe;

import java.util.ArrayList;

class FloatKeyframeSet extends KeyframeSet {
    private float firstValue;
    private float lastValue;
    private float deltaValue;
    private boolean firstTime = true;

    public FloatKeyframeSet(FloatKeyframe... keyframes) {
        super(keyframes);
    }

    @Override
    public Object getValue(float fraction) {
        return getFloatValue(fraction);
    }

    @Override
    public FloatKeyframeSet clone() {
        ArrayList<Keyframe> keyframes = mKeyframes;
        int numKeyframes = mKeyframes.size();
        FloatKeyframe[] newKeyframes = new FloatKeyframe[numKeyframes];
        for (int i = 0; i < numKeyframes; ++i) {
            newKeyframes[i] = (FloatKeyframe) keyframes.get(i).clone();
        }
        return new FloatKeyframeSet(newKeyframes);
    }

    @SuppressWarnings("unchecked")
    public float getFloatValue(float fraction) {
        if (mNumKeyframes == 2) {
            if (firstTime) {
                firstTime = false;
                firstValue = ((FloatKeyframe) mKeyframes.get(0)).getFloatValue();
                lastValue = ((FloatKeyframe) mKeyframes.get(1)).getFloatValue();
                deltaValue = lastValue - firstValue;
            }
            if (mInterpolator != null) {
                fraction = mInterpolator.getInterpolation(fraction);
            }
            if (mEvaluator == null) {
                return firstValue + fraction * deltaValue;
            } else {
                return ((Number)mEvaluator.evaluate(fraction, firstValue, lastValue)).floatValue();
            }
        }
        if (fraction <= 0f) {
            final FloatKeyframe prevKeyframe = (FloatKeyframe) mKeyframes.get(0);
            final FloatKeyframe nextKeyframe = (FloatKeyframe) mKeyframes.get(1);
            float prevValue = prevKeyframe.getFloatValue();
            float nextValue = nextKeyframe.getFloatValue();
            float prevFraction = prevKeyframe.getFraction();
            float nextFraction = nextKeyframe.getFraction();
            final Interpolator interpolator = nextKeyframe.getInterpolator();
            if (interpolator != null) {
                fraction = interpolator.getInterpolation(fraction);
            }
            float intervalFraction = (fraction - prevFraction) / (nextFraction - prevFraction);
            return mEvaluator == null ? prevValue + intervalFraction * (nextValue - prevValue) : ((Number)mEvaluator.evaluate(intervalFraction, prevValue, nextValue)).floatValue();
        } else if (fraction >= 1f) {
            final FloatKeyframe prevKeyframe = (FloatKeyframe) mKeyframes.get(mNumKeyframes - 2);
            final FloatKeyframe nextKeyframe = (FloatKeyframe) mKeyframes.get(mNumKeyframes - 1);
            float prevValue = prevKeyframe.getFloatValue();
            float nextValue = nextKeyframe.getFloatValue();
            float prevFraction = prevKeyframe.getFraction();
            float nextFraction = nextKeyframe.getFraction();
            final Interpolator interpolator = nextKeyframe.getInterpolator();
            if (interpolator != null) {
                fraction = interpolator.getInterpolation(fraction);
            }
            float intervalFraction = (fraction - prevFraction) / (nextFraction - prevFraction);
            return mEvaluator == null ? prevValue + intervalFraction * (nextValue - prevValue) : ((Number)mEvaluator.evaluate(intervalFraction, prevValue, nextValue)).floatValue();
        }
        FloatKeyframe prevKeyframe = (FloatKeyframe) mKeyframes.get(0);
        for (int i = 1; i < mNumKeyframes; ++i) {
            FloatKeyframe nextKeyframe = (FloatKeyframe) mKeyframes.get(i);
            if (fraction < nextKeyframe.getFraction()) {
                final Interpolator interpolator = nextKeyframe.getInterpolator();
                if (interpolator != null) {
                    fraction = interpolator.getInterpolation(fraction);
                }
                float intervalFraction = (fraction - prevKeyframe.getFraction()) /
                    (nextKeyframe.getFraction() - prevKeyframe.getFraction());
                float prevValue = prevKeyframe.getFloatValue();
                float nextValue = nextKeyframe.getFloatValue();
                return mEvaluator == null ? prevValue + intervalFraction * (nextValue - prevValue) : ((Number)mEvaluator.evaluate(intervalFraction, prevValue, nextValue)).floatValue();
            }
            prevKeyframe = nextKeyframe;
        }
        return ((Number)mKeyframes.get(mNumKeyframes - 1).getValue()).floatValue();
    }
}

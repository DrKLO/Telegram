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

package org.telegram.messenger.Animation;

import android.view.animation.Interpolator;

public abstract class Keyframe implements Cloneable {

    float mFraction;
    Class mValueType;
    private Interpolator mInterpolator = null;
    boolean mHasValue = false;

    public static Keyframe ofInt(float fraction, int value) {
        return new IntKeyframe(fraction, value);
    }

    public static Keyframe ofInt(float fraction) {
        return new IntKeyframe(fraction);
    }

    public static Keyframe ofFloat(float fraction, float value) {
        return new FloatKeyframe(fraction, value);
    }

    public static Keyframe ofFloat(float fraction) {
        return new FloatKeyframe(fraction);
    }

    public static Keyframe ofObject(float fraction, Object value) {
        return new ObjectKeyframe(fraction, value);
    }

    public static Keyframe ofObject(float fraction) {
        return new ObjectKeyframe(fraction, null);
    }

    public boolean hasValue() {
        return mHasValue;
    }

    public abstract Object getValue();
    public abstract void setValue(Object value);

    public float getFraction() {
        return mFraction;
    }

    public void setFraction(float fraction) {
        mFraction = fraction;
    }

    public Interpolator getInterpolator() {
        return mInterpolator;
    }

    public void setInterpolator(Interpolator interpolator) {
        mInterpolator = interpolator;
    }

    public Class getType() {
        return mValueType;
    }

    @Override
    public abstract Keyframe clone();

    static class ObjectKeyframe extends Keyframe {

        Object mValue;

        ObjectKeyframe(float fraction, Object value) {
            mFraction = fraction;
            mValue = value;
            mHasValue = (value != null);
            mValueType = mHasValue ? value.getClass() : Object.class;
        }

        public Object getValue() {
            return mValue;
        }

        public void setValue(Object value) {
            mValue = value;
            mHasValue = (value != null);
        }

        @Override
        public ObjectKeyframe clone() {
            ObjectKeyframe kfClone = new ObjectKeyframe(getFraction(), mHasValue ? mValue : null);
            kfClone.setInterpolator(getInterpolator());
            return kfClone;
        }
    }

    static class IntKeyframe extends Keyframe {

        int mValue;

        IntKeyframe(float fraction, int value) {
            mFraction = fraction;
            mValue = value;
            mValueType = int.class;
            mHasValue = true;
        }

        IntKeyframe(float fraction) {
            mFraction = fraction;
            mValueType = int.class;
        }

        public int getIntValue() {
            return mValue;
        }

        public Object getValue() {
            return mValue;
        }

        public void setValue(Object value) {
            if (value != null && value.getClass() == Integer.class) {
                mValue = (Integer) value;
                mHasValue = true;
            }
        }

        @Override
        public IntKeyframe clone() {
            IntKeyframe kfClone = mHasValue ? new IntKeyframe(getFraction(), mValue) : new IntKeyframe(getFraction());
            kfClone.setInterpolator(getInterpolator());
            return kfClone;
        }
    }

    static class FloatKeyframe extends Keyframe {

        float mValue;

        FloatKeyframe(float fraction, float value) {
            mFraction = fraction;
            mValue = value;
            mValueType = float.class;
            mHasValue = true;
        }

        FloatKeyframe(float fraction) {
            mFraction = fraction;
            mValueType = float.class;
        }

        public float getFloatValue() {
            return mValue;
        }

        public Object getValue() {
            return mValue;
        }

        public void setValue(Object value) {
            if (value != null && value.getClass() == Float.class) {
                mValue = (Float) value;
                mHasValue = true;
            }
        }

        @Override
        public FloatKeyframe clone() {
            FloatKeyframe kfClone = mHasValue ? new FloatKeyframe(getFraction(), mValue) : new FloatKeyframe(getFraction());
            kfClone.setInterpolator(getInterpolator());
            return kfClone;
        }
    }
}

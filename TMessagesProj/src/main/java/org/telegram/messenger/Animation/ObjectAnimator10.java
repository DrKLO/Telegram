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

import android.view.View;

import java.util.HashMap;

public final class ObjectAnimator10 extends ValueAnimator {

    private static final HashMap<String, Property> PROXY_PROPERTIES = new HashMap<String, Property>();

    static {
        Property<View, Float> ALPHA = new FloatProperty10<View>("alpha") {
            @Override
            public void setValue(View object, float value) {
                View10.wrap(object).setAlpha(value);
            }

            @Override
            public Float get(View object) {
                return View10.wrap(object).getAlpha();
            }
        };

        Property<View, Float> PIVOT_X = new FloatProperty10<View>("pivotX") {
            @Override
            public void setValue(View object, float value) {
                View10.wrap(object).setPivotX(value);
            }

            @Override
            public Float get(View object) {
                return View10.wrap(object).getPivotX();
            }
        };

        Property<View, Float> PIVOT_Y = new FloatProperty10<View>("pivotY") {
            @Override
            public void setValue(View object, float value) {
                View10.wrap(object).setPivotY(value);
            }

            @Override
            public Float get(View object) {
                return View10.wrap(object).getPivotY();
            }
        };

        Property<View, Float> TRANSLATION_X = new FloatProperty10<View>("translationX") {
            @Override
            public void setValue(View object, float value) {
                View10.wrap(object).setTranslationX(value);
            }

            @Override
            public Float get(View object) {
                return View10.wrap(object).getTranslationX();
            }
        };

        Property<View, Float> TRANSLATION_Y = new FloatProperty10<View>("translationY") {
            @Override
            public void setValue(View object, float value) {
                View10.wrap(object).setTranslationY(value);
            }

            @Override
            public Float get(View object) {
                return View10.wrap(object).getTranslationY();
            }
        };

        Property<View, Float> ROTATION = new FloatProperty10<View>("rotation") {
            @Override
            public void setValue(View object, float value) {
                View10.wrap(object).setRotation(value);
            }

            @Override
            public Float get(View object) {
                return View10.wrap(object).getRotation();
            }
        };

        Property<View, Float> ROTATION_X = new FloatProperty10<View>("rotationX") {
            @Override
            public void setValue(View object, float value) {
                View10.wrap(object).setRotationX(value);
            }

            @Override
            public Float get(View object) {
                return View10.wrap(object).getRotationX();
            }
        };

        Property<View, Float> ROTATION_Y = new FloatProperty10<View>("rotationY") {
            @Override
            public void setValue(View object, float value) {
                View10.wrap(object).setRotationY(value);
            }

            @Override
            public Float get(View object) {
                return View10.wrap(object).getRotationY();
            }
        };

        Property<View, Float> SCALE_X = new FloatProperty10<View>("scaleX") {
            @Override
            public void setValue(View object, float value) {
                View10.wrap(object).setScaleX(value);
            }

            @Override
            public Float get(View object) {
                return View10.wrap(object).getScaleX();
            }
        };

        Property<View, Float> SCALE_Y = new FloatProperty10<View>("scaleY") {
            @Override
            public void setValue(View object, float value) {
                View10.wrap(object).setScaleY(value);
            }

            @Override
            public Float get(View object) {
                return View10.wrap(object).getScaleY();
            }
        };

        Property<View, Integer> SCROLL_X = new IntProperty<View>("scrollX") {
            @Override
            public void setValue(View object, int value) {
                View10.wrap(object).setScrollX(value);
            }

            @Override
            public Integer get(View object) {
                return View10.wrap(object).getScrollX();
            }
        };

        Property<View, Integer> SCROLL_Y = new IntProperty<View>("scrollY") {
            @Override
            public void setValue(View object, int value) {
                View10.wrap(object).setScrollY(value);
            }

            @Override
            public Integer get(View object) {
                return View10.wrap(object).getScrollY();
            }
        };

        Property<View, Float> X = new FloatProperty10<View>("x") {
            @Override
            public void setValue(View object, float value) {
                View10.wrap(object).setX(value);
            }

            @Override
            public Float get(View object) {
                return View10.wrap(object).getX();
            }
        };

        Property<View, Float> Y = new FloatProperty10<View>("y") {
            @Override
            public void setValue(View object, float value) {
                View10.wrap(object).setY(value);
            }

            @Override
            public Float get(View object) {
                return View10.wrap(object).getY();
            }
        };

        PROXY_PROPERTIES.put("alpha", ALPHA);
        PROXY_PROPERTIES.put("pivotX", PIVOT_X);
        PROXY_PROPERTIES.put("pivotY", PIVOT_Y);
        PROXY_PROPERTIES.put("translationX", TRANSLATION_X);
        PROXY_PROPERTIES.put("translationY", TRANSLATION_Y);
        PROXY_PROPERTIES.put("rotation", ROTATION);
        PROXY_PROPERTIES.put("rotationX", ROTATION_X);
        PROXY_PROPERTIES.put("rotationY", ROTATION_Y);
        PROXY_PROPERTIES.put("scaleX", SCALE_X);
        PROXY_PROPERTIES.put("scaleY", SCALE_Y);
        PROXY_PROPERTIES.put("scrollX", SCROLL_X);
        PROXY_PROPERTIES.put("scrollY", SCROLL_Y);
        PROXY_PROPERTIES.put("x", X);
        PROXY_PROPERTIES.put("y", Y);
    }

    private Object mTarget;
    private String mPropertyName;
    private Property mProperty;
    private boolean mAutoCancel = false;

    public void setPropertyName(String propertyName) {
        if (mValues != null) {
            PropertyValuesHolder valuesHolder = mValues[0];
            String oldName = valuesHolder.getPropertyName();
            valuesHolder.setPropertyName(propertyName);
            mValuesMap.remove(oldName);
            mValuesMap.put(propertyName, valuesHolder);
        }
        mPropertyName = propertyName;
        mInitialized = false;
    }

    public void setProperty(Property property) {
        if (mValues != null) {
            PropertyValuesHolder valuesHolder = mValues[0];
            String oldName = valuesHolder.getPropertyName();
            valuesHolder.setProperty(property);
            mValuesMap.remove(oldName);
            mValuesMap.put(mPropertyName, valuesHolder);
        }
        if (mProperty != null) {
            mPropertyName = property.getName();
        }
        mProperty = property;
        mInitialized = false;
    }

    public String getPropertyName() {
        String propertyName = null;
        if (mPropertyName != null) {
            propertyName = mPropertyName;
        } else if (mProperty != null) {
            propertyName = mProperty.getName();
        } else if (mValues != null && mValues.length > 0) {
            for (int i = 0; i < mValues.length; ++i) {
                if (i == 0) {
                    propertyName = "";
                } else {
                    propertyName += ",";
                }
                propertyName += mValues[i].getPropertyName();
            }
        }
        return propertyName;
    }

    public ObjectAnimator10() {

    }

    private ObjectAnimator10(Object target, String propertyName) {
        mTarget = target;
        setPropertyName(propertyName);
    }

    private <T> ObjectAnimator10(T target, Property<T, ?> property) {
        mTarget = target;
        setProperty(property);
    }

    public static ObjectAnimator10 ofInt(Object target, String propertyName, int... values) {
        ObjectAnimator10 anim = new ObjectAnimator10(target, propertyName);
        anim.setIntValues(values);
        return anim;
    }

    public static <T> ObjectAnimator10 ofInt(T target, Property<T, Integer> property, int... values) {
        ObjectAnimator10 anim = new ObjectAnimator10(target, property);
        anim.setIntValues(values);
        return anim;
    }

    public static ObjectAnimator10 ofFloat(Object target, String propertyName, float... values) {
        ObjectAnimator10 anim = new ObjectAnimator10(target, propertyName);
        anim.setFloatValues(values);
        return anim;
    }

    public static <T> ObjectAnimator10 ofFloat(T target, Property<T, Float> property, float... values) {
        ObjectAnimator10 anim = new ObjectAnimator10(target, property);
        anim.setFloatValues(values);
        return anim;
    }

    public static ObjectAnimator10 ofObject(Object target, String propertyName, TypeEvaluator evaluator, Object... values) {
        ObjectAnimator10 anim = new ObjectAnimator10(target, propertyName);
        anim.setObjectValues(values);
        anim.setEvaluator(evaluator);
        return anim;
    }

    public static <T, V> ObjectAnimator10 ofObject(T target, Property<T, V> property, TypeEvaluator<V> evaluator, V... values) {
        ObjectAnimator10 anim = new ObjectAnimator10(target, property);
        anim.setObjectValues(values);
        anim.setEvaluator(evaluator);
        return anim;
    }

    public static ObjectAnimator10 ofPropertyValuesHolder(Object target, PropertyValuesHolder... values) {
        ObjectAnimator10 anim = new ObjectAnimator10();
        anim.mTarget = target;
        anim.setValues(values);
        return anim;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setIntValues(int... values) {
        if (mValues == null || mValues.length == 0) {
            if (mProperty != null) {
                setValues(PropertyValuesHolder.ofInt(mProperty, values));
            } else {
                setValues(PropertyValuesHolder.ofInt(mPropertyName, values));
            }
        } else {
            super.setIntValues(values);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setFloatValues(float... values) {
        if (mValues == null || mValues.length == 0) {
            if (mProperty != null) {
                setValues(PropertyValuesHolder.ofFloat(mProperty, values));
            } else {
                setValues(PropertyValuesHolder.ofFloat(mPropertyName, values));
            }
        } else {
            super.setFloatValues(values);
        }
    }

    @Override
    public void setObjectValues(Object... values) {
        if (mValues == null || mValues.length == 0) {
            if (mProperty != null) {
                setValues(PropertyValuesHolder.ofObject(mProperty, null, values));
            } else {
                setValues(PropertyValuesHolder.ofObject(mPropertyName, null, values));
            }
        } else {
            super.setObjectValues(values);
        }
    }

    public void setAutoCancel(boolean cancel) {
        mAutoCancel = cancel;
    }

    private boolean hasSameTargetAndProperties(Animator10 anim) {
        if (anim instanceof ObjectAnimator10) {
            PropertyValuesHolder[] theirValues = ((ObjectAnimator10) anim).getValues();
            if (((ObjectAnimator10) anim).getTarget() == mTarget &&
                    mValues.length == theirValues.length) {
                for (int i = 0; i < mValues.length; ++i) {
                    PropertyValuesHolder pvhMine = mValues[i];
                    PropertyValuesHolder pvhTheirs = theirValues[i];
                    if (pvhMine.getPropertyName() == null ||
                            !pvhMine.getPropertyName().equals(pvhTheirs.getPropertyName())) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void start() {
        AnimationHandler handler = sAnimationHandler.get();
        if (handler != null) {
            int numAnims = handler.mAnimations.size();
            for (int i = numAnims - 1; i >= 0; i--) {
                if (handler.mAnimations.get(i) instanceof ObjectAnimator10) {
                    ObjectAnimator10 anim = (ObjectAnimator10) handler.mAnimations.get(i);
                    if (anim.mAutoCancel && hasSameTargetAndProperties(anim)) {
                        anim.cancel();
                    }
                }
            }
            numAnims = handler.mPendingAnimations.size();
            for (int i = numAnims - 1; i >= 0; i--) {
                if (handler.mPendingAnimations.get(i) instanceof ObjectAnimator10) {
                    ObjectAnimator10 anim = (ObjectAnimator10) handler.mPendingAnimations.get(i);
                    if (anim.mAutoCancel && hasSameTargetAndProperties(anim)) {
                        anim.cancel();
                    }
                }
            }
            numAnims = handler.mDelayedAnims.size();
            for (int i = numAnims - 1; i >= 0; i--) {
                if (handler.mDelayedAnims.get(i) instanceof ObjectAnimator10) {
                    ObjectAnimator10 anim = (ObjectAnimator10) handler.mDelayedAnims.get(i);
                    if (anim.mAutoCancel && hasSameTargetAndProperties(anim)) {
                        anim.cancel();
                    }
                }
            }
        }
        super.start();
    }

    @Override
    void initAnimation() {
        if (!mInitialized) {
            if ((mProperty == null) && (mTarget instanceof View) && PROXY_PROPERTIES.containsKey(mPropertyName)) {
                setProperty(PROXY_PROPERTIES.get(mPropertyName));
            }
            int numValues = mValues.length;
            for (PropertyValuesHolder mValue : mValues) {
                mValue.setupSetterAndGetter(mTarget);
            }
            super.initAnimation();
        }
    }

    @Override
    public ObjectAnimator10 setDuration(long duration) {
        super.setDuration(duration);
        return this;
    }

    public Object getTarget() {
        return mTarget;
    }

    @Override
    public void setTarget(Object target) {
        if (mTarget != target) {
            final Object oldTarget = mTarget;
            mTarget = target;
            if (oldTarget != null && target != null && oldTarget.getClass() == target.getClass()) {
                return;
            }
            mInitialized = false;
        }
    }

    @Override
    public void setupStartValues() {
        initAnimation();
        int numValues = mValues.length;
        for (PropertyValuesHolder mValue : mValues) {
            mValue.setupStartValue(mTarget);
        }
    }

    @Override
    public void setupEndValues() {
        initAnimation();
        int numValues = mValues.length;
        for (PropertyValuesHolder mValue : mValues) {
            mValue.setupEndValue(mTarget);
        }
    }

    @Override
    void animateValue(float fraction) {
        super.animateValue(fraction);
        int numValues = mValues.length;
        for (PropertyValuesHolder mValue : mValues) {
            mValue.setAnimatedValue(mTarget);
        }
    }

    @Override
    public ObjectAnimator10 clone() {
        return (ObjectAnimator10) super.clone();
    }
}

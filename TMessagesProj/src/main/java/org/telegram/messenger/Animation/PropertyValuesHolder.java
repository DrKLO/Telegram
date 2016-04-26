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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PropertyValuesHolder implements Cloneable {

    String mPropertyName;
    protected Property mProperty;
    Method mSetter = null;
    private Method mGetter = null;
    Class mValueType;
    KeyframeSet mKeyframeSet = null;

    private static final TypeEvaluator sIntEvaluator = new IntEvaluator();
    private static final TypeEvaluator sFloatEvaluator = new FloatEvaluator();

    private static Class[] FLOAT_VARIANTS = {float.class, Float.class, double.class, int.class, Double.class, Integer.class};
    private static Class[] INTEGER_VARIANTS = {int.class, Integer.class, float.class, double.class, Float.class, Double.class};
    private static Class[] DOUBLE_VARIANTS = {double.class, Double.class, float.class, int.class, Float.class, Integer.class};

    private static final HashMap<Class, HashMap<String, Method>> sSetterPropertyMap = new HashMap<Class, HashMap<String, Method>>();
    private static final HashMap<Class, HashMap<String, Method>> sGetterPropertyMap = new HashMap<Class, HashMap<String, Method>>();

    final ReentrantReadWriteLock mPropertyMapLock = new ReentrantReadWriteLock();
    final Object[] mTmpValueArray = new Object[1];

    private TypeEvaluator mEvaluator;

    private Object mAnimatedValue;

    private PropertyValuesHolder(String propertyName) {
        mPropertyName = propertyName;
    }

    private PropertyValuesHolder(Property property) {
        mProperty = property;
        if (property != null) {
            mPropertyName = property.getName();
        }
    }

    public static PropertyValuesHolder ofInt(String propertyName, int... values) {
        return new IntPropertyValuesHolder(propertyName, values);
    }

    public static PropertyValuesHolder ofInt(Property<?, Integer> property, int... values) {
        return new IntPropertyValuesHolder(property, values);
    }

    public static PropertyValuesHolder ofFloat(String propertyName, float... values) {
        return new FloatPropertyValuesHolder(propertyName, values);
    }

    public static PropertyValuesHolder ofFloat(Property<?, Float> property, float... values) {
        return new FloatPropertyValuesHolder(property, values);
    }

    public static PropertyValuesHolder ofObject(String propertyName, TypeEvaluator evaluator,
                                                Object... values) {
        PropertyValuesHolder pvh = new PropertyValuesHolder(propertyName);
        pvh.setObjectValues(values);
        pvh.setEvaluator(evaluator);
        return pvh;
    }

    public static <V> PropertyValuesHolder ofObject(Property property,
                                                    TypeEvaluator<V> evaluator, V... values) {
        PropertyValuesHolder pvh = new PropertyValuesHolder(property);
        pvh.setObjectValues(values);
        pvh.setEvaluator(evaluator);
        return pvh;
    }

    public static PropertyValuesHolder ofKeyframe(String propertyName, Keyframe... values) {
        KeyframeSet keyframeSet = KeyframeSet.ofKeyframe(values);
        if (keyframeSet instanceof IntKeyframeSet) {
            return new IntPropertyValuesHolder(propertyName, (IntKeyframeSet) keyframeSet);
        } else if (keyframeSet instanceof FloatKeyframeSet) {
            return new FloatPropertyValuesHolder(propertyName, (FloatKeyframeSet) keyframeSet);
        } else {
            PropertyValuesHolder pvh = new PropertyValuesHolder(propertyName);
            pvh.mKeyframeSet = keyframeSet;
            pvh.mValueType = values[0].getType();
            return pvh;
        }
    }

    public static PropertyValuesHolder ofKeyframe(Property property, Keyframe... values) {
        KeyframeSet keyframeSet = KeyframeSet.ofKeyframe(values);
        if (keyframeSet instanceof IntKeyframeSet) {
            return new IntPropertyValuesHolder(property, (IntKeyframeSet) keyframeSet);
        } else if (keyframeSet instanceof FloatKeyframeSet) {
            return new FloatPropertyValuesHolder(property, (FloatKeyframeSet) keyframeSet);
        } else {
            PropertyValuesHolder pvh = new PropertyValuesHolder(property);
            pvh.mKeyframeSet = keyframeSet;
            pvh.mValueType = values[0].getType();
            return pvh;
        }
    }

    public void setIntValues(int... values) {
        mValueType = int.class;
        mKeyframeSet = KeyframeSet.ofInt(values);
    }

    public void setFloatValues(float... values) {
        mValueType = float.class;
        mKeyframeSet = KeyframeSet.ofFloat(values);
    }

    public void setKeyframes(Keyframe... values) {
        int numKeyframes = values.length;
        Keyframe keyframes[] = new Keyframe[Math.max(numKeyframes, 2)];
        mValueType = values[0].getType();
        System.arraycopy(values, 0, keyframes, 0, numKeyframes);
        mKeyframeSet = new KeyframeSet(keyframes);
    }

    public void setObjectValues(Object... values) {
        mValueType = values[0].getClass();
        mKeyframeSet = KeyframeSet.ofObject(values);
    }

    @SuppressWarnings("unchecked")
    private Method getPropertyFunction(Class targetClass, String prefix, Class valueType) {
        Method returnVal = null;
        String methodName = getMethodName(prefix, mPropertyName);
        Class args[] = null;
        if (valueType == null) {
            try {
                returnVal = targetClass.getMethod(methodName);
            } catch (Throwable e) {
                try {
                    returnVal = targetClass.getDeclaredMethod(methodName);
                    returnVal.setAccessible(true);
                } catch (Throwable e2) {
                    e2.printStackTrace();
                }
            }
        } else {
            args = new Class[1];
            Class typeVariants[];
            if (mValueType.equals(Float.class)) {
                typeVariants = FLOAT_VARIANTS;
            } else if (mValueType.equals(Integer.class)) {
                typeVariants = INTEGER_VARIANTS;
            } else if (mValueType.equals(Double.class)) {
                typeVariants = DOUBLE_VARIANTS;
            } else {
                typeVariants = new Class[1];
                typeVariants[0] = mValueType;
            }
            for (Class typeVariant : typeVariants) {
                args[0] = typeVariant;
                try {
                    returnVal = targetClass.getMethod(methodName, args);
                    mValueType = typeVariant;
                    return returnVal;
                } catch (Throwable e) {
                    try {
                        returnVal = targetClass.getDeclaredMethod(methodName, args);
                        returnVal.setAccessible(true);
                        mValueType = typeVariant;
                        return returnVal;
                    } catch (Throwable e2) {
                        // Swallow the error and keep trying other variants
                    }
                }
            }
        }

        return returnVal;
    }

    private Method setupSetterOrGetter(Class targetClass, HashMap<Class, HashMap<String, Method>> propertyMapMap, String prefix, Class valueType) {
        Method setterOrGetter = null;
        try {
            mPropertyMapLock.writeLock().lock();
            HashMap<String, Method> propertyMap = propertyMapMap.get(targetClass);
            if (propertyMap != null) {
                setterOrGetter = propertyMap.get(mPropertyName);
            }
            if (setterOrGetter == null) {
                setterOrGetter = getPropertyFunction(targetClass, prefix, valueType);
                if (propertyMap == null) {
                    propertyMap = new HashMap<String, Method>();
                    propertyMapMap.put(targetClass, propertyMap);
                }
                propertyMap.put(mPropertyName, setterOrGetter);
            }
        } finally {
            mPropertyMapLock.writeLock().unlock();
        }
        return setterOrGetter;
    }

    void setupSetter(Class targetClass) {
        mSetter = setupSetterOrGetter(targetClass, sSetterPropertyMap, "set", mValueType);
    }

    private void setupGetter(Class targetClass) {
        mGetter = setupSetterOrGetter(targetClass, sGetterPropertyMap, "get", null);
    }

    @SuppressWarnings("unchecked")
    void setupSetterAndGetter(Object target) {
        if (mProperty != null) {
            try {
                Object testValue = mProperty.get(target);
                for (Keyframe kf : mKeyframeSet.mKeyframes) {
                    if (!kf.hasValue()) {
                        kf.setValue(mProperty.get(target));
                    }
                }
                return;
            } catch (Throwable e) {
                mProperty = null;
            }
        }
        Class targetClass = target.getClass();
        if (mSetter == null) {
            setupSetter(targetClass);
        }
        for (Keyframe kf : mKeyframeSet.mKeyframes) {
            if (!kf.hasValue()) {
                if (mGetter == null) {
                    setupGetter(targetClass);
                    if (mGetter == null) {
                        return;
                    }
                }
                try {
                    kf.setValue(mGetter.invoke(target));
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void setupValue(Object target, Keyframe kf) {
        if (mProperty != null) {
            kf.setValue(mProperty.get(target));
        }
        try {
            if (mGetter == null) {
                Class targetClass = target.getClass();
                setupGetter(targetClass);
                if (mGetter == null) {
                    return;
                }
            }
            kf.setValue(mGetter.invoke(target));
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    void setupStartValue(Object target) {
        setupValue(target, mKeyframeSet.mKeyframes.get(0));
    }

    void setupEndValue(Object target) {
        setupValue(target, mKeyframeSet.mKeyframes.get(mKeyframeSet.mKeyframes.size() - 1));
    }

    @Override
    public PropertyValuesHolder clone() {
        try {
            PropertyValuesHolder newPVH = (PropertyValuesHolder) super.clone();
            newPVH.mPropertyName = mPropertyName;
            newPVH.mProperty = mProperty;
            newPVH.mKeyframeSet = mKeyframeSet.clone();
            newPVH.mEvaluator = mEvaluator;
            return newPVH;
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    void setAnimatedValue(Object target) {
        if (mProperty != null) {
            mProperty.set(target, getAnimatedValue());
        }
        if (mSetter != null) {
            try {
                mTmpValueArray[0] = getAnimatedValue();
                mSetter.invoke(target, mTmpValueArray);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    void init() {
        if (mEvaluator == null) {
            mEvaluator = (mValueType == Integer.class) ? sIntEvaluator : (mValueType == Float.class) ? sFloatEvaluator : null;
        }
        if (mEvaluator != null) {
            mKeyframeSet.setEvaluator(mEvaluator);
        }
    }

    public void setEvaluator(TypeEvaluator evaluator) {
        mEvaluator = evaluator;
        mKeyframeSet.setEvaluator(evaluator);
    }

    void calculateValue(float fraction) {
        mAnimatedValue = mKeyframeSet.getValue(fraction);
    }

    public void setPropertyName(String propertyName) {
        mPropertyName = propertyName;
    }

    public void setProperty(Property property) {
        mProperty = property;
    }

    public String getPropertyName() {
        return mPropertyName;
    }

    Object getAnimatedValue() {
        return mAnimatedValue;
    }

    @Override
    public String toString() {
        return mPropertyName + ": " + mKeyframeSet.toString();
    }

    static String getMethodName(String prefix, String propertyName) {
        if (propertyName == null || propertyName.length() == 0) {
            return prefix;
        }
        char firstLetter = Character.toUpperCase(propertyName.charAt(0));
        String theRest = propertyName.substring(1);
        return prefix + firstLetter + theRest;
    }

    static class IntPropertyValuesHolder extends PropertyValuesHolder {
        private static final HashMap<Class, HashMap<String, Integer>> sJNISetterPropertyMap = new HashMap<Class, HashMap<String, Integer>>();
        private IntProperty mIntProperty;

        IntKeyframeSet mIntKeyframeSet;
        int mIntAnimatedValue;

        public IntPropertyValuesHolder(String propertyName, IntKeyframeSet keyframeSet) {
            super(propertyName);
            mValueType = int.class;
            mKeyframeSet = keyframeSet;
            mIntKeyframeSet = (IntKeyframeSet) mKeyframeSet;
        }

        public IntPropertyValuesHolder(Property property, IntKeyframeSet keyframeSet) {
            super(property);
            mValueType = int.class;
            mKeyframeSet = keyframeSet;
            mIntKeyframeSet = (IntKeyframeSet) mKeyframeSet;
            if (property instanceof IntProperty) {
                mIntProperty = (IntProperty) mProperty;
            }
        }

        public IntPropertyValuesHolder(String propertyName, int... values) {
            super(propertyName);
            setIntValues(values);
        }

        public IntPropertyValuesHolder(Property property, int... values) {
            super(property);
            setIntValues(values);
            if (property instanceof IntProperty) {
                mIntProperty = (IntProperty) mProperty;
            }
        }

        @Override
        public void setIntValues(int... values) {
            super.setIntValues(values);
            mIntKeyframeSet = (IntKeyframeSet) mKeyframeSet;
        }

        @Override
        void calculateValue(float fraction) {
            mIntAnimatedValue = mIntKeyframeSet.getIntValue(fraction);
        }

        @Override
        Object getAnimatedValue() {
            return mIntAnimatedValue;
        }

        @Override
        public IntPropertyValuesHolder clone() {
            IntPropertyValuesHolder newPVH = (IntPropertyValuesHolder) super.clone();
            newPVH.mIntKeyframeSet = (IntKeyframeSet) newPVH.mKeyframeSet;
            return newPVH;
        }

        @SuppressWarnings("unchecked")
        @Override
        void setAnimatedValue(Object target) {
            if (mIntProperty != null) {
                mIntProperty.setValue(target, mIntAnimatedValue);
                return;
            }
            if (mProperty != null) {
                mProperty.set(target, mIntAnimatedValue);
                return;
            }
            if (mSetter != null) {
                try {
                    mTmpValueArray[0] = mIntAnimatedValue;
                    mSetter.invoke(target, mTmpValueArray);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        void setupSetter(Class targetClass) {
            if (mProperty != null) {
                return;
            }

            super.setupSetter(targetClass);
        }
    }

    static class FloatPropertyValuesHolder extends PropertyValuesHolder {

        private static final HashMap<Class, HashMap<String, Integer>> sJNISetterPropertyMap = new HashMap<Class, HashMap<String, Integer>>();
        private FloatProperty10 mFloatProperty;

        FloatKeyframeSet mFloatKeyframeSet;
        float mFloatAnimatedValue;

        public FloatPropertyValuesHolder(String propertyName, FloatKeyframeSet keyframeSet) {
            super(propertyName);
            mValueType = float.class;
            mKeyframeSet = keyframeSet;
            mFloatKeyframeSet = (FloatKeyframeSet) mKeyframeSet;
        }

        public FloatPropertyValuesHolder(Property property, FloatKeyframeSet keyframeSet) {
            super(property);
            mValueType = float.class;
            mKeyframeSet = keyframeSet;
            mFloatKeyframeSet = (FloatKeyframeSet) mKeyframeSet;
            if (property instanceof FloatProperty10) {
                mFloatProperty = (FloatProperty10) mProperty;
            }
        }

        public FloatPropertyValuesHolder(String propertyName, float... values) {
            super(propertyName);
            setFloatValues(values);
        }

        public FloatPropertyValuesHolder(Property property, float... values) {
            super(property);
            setFloatValues(values);
            if (property instanceof FloatProperty10) {
                mFloatProperty = (FloatProperty10) mProperty;
            }
        }

        @Override
        public void setFloatValues(float... values) {
            super.setFloatValues(values);
            mFloatKeyframeSet = (FloatKeyframeSet) mKeyframeSet;
        }

        @Override
        void calculateValue(float fraction) {
            mFloatAnimatedValue = mFloatKeyframeSet.getFloatValue(fraction);
        }

        @Override
        Object getAnimatedValue() {
            return mFloatAnimatedValue;
        }

        @Override
        public FloatPropertyValuesHolder clone() {
            FloatPropertyValuesHolder newPVH = (FloatPropertyValuesHolder) super.clone();
            newPVH.mFloatKeyframeSet = (FloatKeyframeSet) newPVH.mKeyframeSet;
            return newPVH;
        }

        @SuppressWarnings("unchecked")
        @Override
        void setAnimatedValue(Object target) {
            if (mFloatProperty != null) {
                mFloatProperty.setValue(target, mFloatAnimatedValue);
                return;
            }
            if (mProperty != null) {
                mProperty.set(target, mFloatAnimatedValue);
                return;
            }
            if (mSetter != null) {
                try {
                    mTmpValueArray[0] = mFloatAnimatedValue;
                    mSetter.invoke(target, mTmpValueArray);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        void setupSetter(Class targetClass) {
            if (mProperty != null) {
                return;
            }
            super.setupSetter(targetClass);
        }
    }
}
package org.telegram.ui.Components;

import androidx.dynamicanimation.animation.FloatPropertyCompat;

public class SimpleFloatPropertyCompat<T> extends FloatPropertyCompat<T> {
    private Getter<T> getter;
    private Setter<T> setter;
    private float multiplier = 1f;

    public SimpleFloatPropertyCompat(String name, Getter<T> getter, Setter<T> setter) {
        super(name);
        this.getter = getter;
        this.setter = setter;
    }

    public SimpleFloatPropertyCompat<T> setMultiplier(float multiplier) {
        this.multiplier = multiplier;
        return this;
    }

    public float getMultiplier() {
        return multiplier;
    }

    @Override
    public float getValue(T object) {
        return getter.get(object) * multiplier;
    }

    @Override
    public void setValue(T object, float value) {
        setter.set(object, value / multiplier);
    }

    public interface Getter<T> {
        float get(T obj);
    }

    public interface Setter<T> {
        void set(T obj, float value);
    }
}

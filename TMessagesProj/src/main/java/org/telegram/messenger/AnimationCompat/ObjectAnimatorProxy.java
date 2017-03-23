/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger.AnimationCompat;

import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.animation.Interpolator;

import org.telegram.messenger.Animation.AnimatorListenerAdapter10;
import org.telegram.messenger.Animation.ObjectAnimator10;
import org.telegram.messenger.Animation.View10;

public class ObjectAnimatorProxy {

    private Object objectAnimator;

    public ObjectAnimatorProxy(Object animator) {
        objectAnimator = animator;
    }

    public static Object ofFloat(Object target, String propertyName, float... values) {
        if (View10.NEED_PROXY) {
            return ObjectAnimator10.ofFloat(target, propertyName, values);
        } else {
            return ObjectAnimator.ofFloat(target, propertyName, values);
        }
    }

    public static Object ofInt(Object target, String propertyName, int... values) {
        if (View10.NEED_PROXY) {
            return ObjectAnimator10.ofInt(target, propertyName, values);
        } else {
            return ObjectAnimator.ofInt(target, propertyName, values);
        }
    }

    public static ObjectAnimatorProxy ofFloatProxy(Object target, String propertyName, float... values) {
        if (View10.NEED_PROXY) {
            return new ObjectAnimatorProxy(ObjectAnimator10.ofFloat(target, propertyName, values));
        } else {
            return new ObjectAnimatorProxy(ObjectAnimator.ofFloat(target, propertyName, values));
        }
    }

    public static ObjectAnimatorProxy ofIntProxy(Object target, String propertyName, int... values) {
        if (View10.NEED_PROXY) {
            return new ObjectAnimatorProxy(ObjectAnimator10.ofInt(target, propertyName, values));
        } else {
            return new ObjectAnimatorProxy(ObjectAnimator.ofInt(target, propertyName, values));
        }
    }

    public ObjectAnimatorProxy setDuration(long duration) {
        if (View10.NEED_PROXY) {
            ((ObjectAnimator10) objectAnimator).setDuration(duration);
        } else {
            ((ObjectAnimator) objectAnimator).setDuration(duration);
        }
        return this;
    }

    public void setInterpolator(Interpolator value) {
        if (View10.NEED_PROXY) {
            ((ObjectAnimator10) objectAnimator).setInterpolator(value);
        } else {
            ((ObjectAnimator) objectAnimator).setInterpolator(value);
        }
    }

    public ObjectAnimatorProxy start() {
        if (View10.NEED_PROXY) {
            ((ObjectAnimator10) objectAnimator).start();
        } else {
            ((ObjectAnimator) objectAnimator).start();
        }
        return this;
    }

    public void setAutoCancel(boolean cancel) {
        if (View10.NEED_PROXY) {
            ((ObjectAnimator10) objectAnimator).setAutoCancel(cancel);
        } else {
            ((ObjectAnimator) objectAnimator).setAutoCancel(cancel);
        }
    }

    public boolean isRunning() {
        if (View10.NEED_PROXY) {
            return ((ObjectAnimator10) objectAnimator).isRunning();
        } else {
            return ((ObjectAnimator) objectAnimator).isRunning();
        }
    }

    public void end() {
        if (View10.NEED_PROXY) {
            ((ObjectAnimator10) objectAnimator).end();
        } else {
            ((ObjectAnimator) objectAnimator).end();
        }
    }

    public void cancel() {
        if (View10.NEED_PROXY) {
            ((ObjectAnimator10) objectAnimator).cancel();
        } else {
            ((ObjectAnimator) objectAnimator).cancel();
        }
    }

    public ObjectAnimatorProxy addListener(AnimatorListenerAdapterProxy listener) {
        if (View10.NEED_PROXY) {
            ((ObjectAnimator10) objectAnimator).addListener((AnimatorListenerAdapter10) listener.animatorListenerAdapter);
        } else {
            ((ObjectAnimator) objectAnimator).addListener((AnimatorListenerAdapter) listener.animatorListenerAdapter);
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        return objectAnimator == o;
    }
}

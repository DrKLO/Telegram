/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.AnimationCompat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.view.animation.Interpolator;

import org.telegram.ui.Animation.Animator10;
import org.telegram.ui.Animation.AnimatorListenerAdapter10;
import org.telegram.ui.Animation.AnimatorSet10;
import org.telegram.ui.Animation.View10;

import java.util.Arrays;

public class AnimatorSetProxy {
    private Object animatorSet;

    public AnimatorSetProxy() {
        if (View10.NEED_PROXY) {
            animatorSet = new AnimatorSet10();
        } else {
            animatorSet = new AnimatorSet();
        }
    }

    public void playTogether(Object... items) {
        if (View10.NEED_PROXY) {
            Animator10[] animators = Arrays.copyOf(items, items.length, Animator10[].class);
            ((AnimatorSet10) animatorSet).playTogether(animators);
        } else {
            Animator[] animators = Arrays.copyOf(items, items.length, Animator[].class);
            ((AnimatorSet) animatorSet).playTogether(animators);
        }
    }

    public AnimatorSetProxy setDuration(long duration) {
        if (View10.NEED_PROXY) {
            ((AnimatorSet10) animatorSet).setDuration(duration);
        } else {
            ((AnimatorSet) animatorSet).setDuration(duration);
        }
        return this;
    }

    public void start() {
        if (View10.NEED_PROXY) {
            ((AnimatorSet10) animatorSet).start();
        } else {
            ((AnimatorSet) animatorSet).start();
        }
    }

    public void cancel() {
        if (View10.NEED_PROXY) {
            ((AnimatorSet10) animatorSet).cancel();
        } else {
            ((AnimatorSet) animatorSet).cancel();
        }
    }

    public void addListener(AnimatorListenerAdapterProxy listener) {
        if (View10.NEED_PROXY) {
            ((AnimatorSet10) animatorSet).addListener((AnimatorListenerAdapter10) listener.animatorListenerAdapter);
        } else {
            ((AnimatorSet) animatorSet).addListener((AnimatorListenerAdapter) listener.animatorListenerAdapter);
        }
    }

    public void setInterpolator(Interpolator interpolator) {
        if (View10.NEED_PROXY) {
            ((AnimatorSet10) animatorSet).setInterpolator(interpolator);
        } else {
            ((AnimatorSet) animatorSet).setInterpolator(interpolator);
        }
    }
}

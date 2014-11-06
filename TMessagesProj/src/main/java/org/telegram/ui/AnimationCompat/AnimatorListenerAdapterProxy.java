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

import org.telegram.ui.Animation.Animator10;
import org.telegram.ui.Animation.AnimatorListenerAdapter10;
import org.telegram.ui.Animation.View10;

public class AnimatorListenerAdapterProxy {
    protected Object animatorListenerAdapter;

    public AnimatorListenerAdapterProxy() {
        if (View10.NEED_PROXY) {
            animatorListenerAdapter = new AnimatorListenerAdapter10() {
                @Override
                public void onAnimationCancel(Animator10 animation) {
                    AnimatorListenerAdapterProxy.this.onAnimationCancel(animation);
                }

                @Override
                public void onAnimationEnd(Animator10 animation) {
                    AnimatorListenerAdapterProxy.this.onAnimationEnd(animation);
                }

                @Override
                public void onAnimationRepeat(Animator10 animation) {
                    AnimatorListenerAdapterProxy.this.onAnimationRepeat(animation);
                }

                @Override
                public void onAnimationStart(Animator10 animation) {
                    AnimatorListenerAdapterProxy.this.onAnimationStart(animation);
                }

                @Override
                public void onAnimationPause(Animator10 animation) {
                    AnimatorListenerAdapterProxy.this.onAnimationPause(animation);
                }

                @Override
                public void onAnimationResume(Animator10 animation) {
                    AnimatorListenerAdapterProxy.this.onAnimationResume(animation);
                }
            };
        } else {
            animatorListenerAdapter = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    AnimatorListenerAdapterProxy.this.onAnimationCancel(animation);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    AnimatorListenerAdapterProxy.this.onAnimationEnd(animation);
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                    AnimatorListenerAdapterProxy.this.onAnimationRepeat(animation);
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    AnimatorListenerAdapterProxy.this.onAnimationStart(animation);
                }

                @Override
                public void onAnimationPause(Animator animation) {
                    AnimatorListenerAdapterProxy.this.onAnimationPause(animation);
                }

                @Override
                public void onAnimationResume(Animator animation) {
                    AnimatorListenerAdapterProxy.this.onAnimationResume(animation);
                }
            };
        }
    }

    public void onAnimationCancel(Object animation) {

    }

    public void onAnimationEnd(Object animation) {

    }

    public void onAnimationRepeat(Object animation) {

    }

    public void onAnimationStart(Object animation) {

    }

    public void onAnimationPause(Object animation) {

    }

    public void onAnimationResume(Object animation) {

    }
}

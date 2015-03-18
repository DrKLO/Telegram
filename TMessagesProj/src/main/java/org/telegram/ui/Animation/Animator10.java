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

import java.util.ArrayList;

public abstract class Animator10 implements Cloneable {

    ArrayList<AnimatorListener> mListeners = null;
    ArrayList<AnimatorPauseListener> mPauseListeners = null;
    boolean mPaused = false;

    public abstract long getStartDelay();

    public abstract void setStartDelay(long startDelay);

    public abstract Animator10 setDuration(long duration);

    public abstract long getDuration();

    public abstract void setInterpolator(Interpolator value);

    public abstract boolean isRunning();

    public void start() {

    }

    public void cancel() {

    }

    public void end() {

    }

    @SuppressWarnings("unchecked")
    public void pause() {
        if (isStarted() && !mPaused) {
            mPaused = true;
            if (mPauseListeners != null) {
                ArrayList<AnimatorPauseListener> tmpListeners = (ArrayList<AnimatorPauseListener>) mPauseListeners.clone();
                int numListeners = tmpListeners.size();
                for (AnimatorPauseListener tmpListener : tmpListeners) {
                    tmpListener.onAnimationPause(this);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void resume() {
        if (mPaused) {
            mPaused = false;
            if (mPauseListeners != null) {
                ArrayList<AnimatorPauseListener> tmpListeners = (ArrayList<AnimatorPauseListener>) mPauseListeners.clone();
                int numListeners = tmpListeners.size();
                for (AnimatorPauseListener tmpListener : tmpListeners) {
                    tmpListener.onAnimationResume(this);
                }
            }
        }
    }

    public boolean isPaused() {
        return mPaused;
    }

    public boolean isStarted() {
        return isRunning();
    }

    public Interpolator getInterpolator() {
        return null;
    }

    public void addListener(AnimatorListener listener) {
        if (mListeners == null) {
            mListeners = new ArrayList<AnimatorListener>();
        }
        mListeners.add(listener);
    }

    public void removeListener(AnimatorListener listener) {
        if (mListeners == null) {
            return;
        }
        mListeners.remove(listener);
        if (mListeners.size() == 0) {
            mListeners = null;
        }
    }

    public ArrayList<AnimatorListener> getListeners() {
        return mListeners;
    }

    public void addPauseListener(AnimatorPauseListener listener) {
        if (mPauseListeners == null) {
            mPauseListeners = new ArrayList<AnimatorPauseListener>();
        }
        mPauseListeners.add(listener);
    }

    public void removePauseListener(AnimatorPauseListener listener) {
        if (mPauseListeners == null) {
            return;
        }
        mPauseListeners.remove(listener);
        if (mPauseListeners.size() == 0) {
            mPauseListeners = null;
        }
    }

    public void removeAllListeners() {
        if (mListeners != null) {
            mListeners.clear();
            mListeners = null;
        }
        if (mPauseListeners != null) {
            mPauseListeners.clear();
            mPauseListeners = null;
        }
    }

    @Override
    public Animator10 clone() {
        try {
            final Animator10 anim = (Animator10) super.clone();
            if (mListeners != null) {
                ArrayList<AnimatorListener> oldListeners = mListeners;
                anim.mListeners = new ArrayList<AnimatorListener>();
                int numListeners = oldListeners.size();
                for (AnimatorListener oldListener : oldListeners) {
                    anim.mListeners.add(oldListener);
                }
            }
            if (mPauseListeners != null) {
                ArrayList<AnimatorPauseListener> oldListeners = mPauseListeners;
                anim.mPauseListeners = new ArrayList<AnimatorPauseListener>();
                int numListeners = oldListeners.size();
                for (AnimatorPauseListener oldListener : oldListeners) {
                    anim.mPauseListeners.add(oldListener);
                }
            }
            return anim;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    public void setupStartValues() {

    }

    public void setupEndValues() {

    }

    public void setTarget(Object target) {

    }

    public interface AnimatorListener {
        void onAnimationStart(Animator10 animation);
        void onAnimationEnd(Animator10 animation);
        void onAnimationCancel(Animator10 animation);
        void onAnimationRepeat(Animator10 animation);
    }

    public interface AnimatorPauseListener {
        void onAnimationPause(Animator10 animation);
        void onAnimationResume(Animator10 animation);
    }
}

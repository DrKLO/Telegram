/*
 Copyright 2012 Jake Wharton

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.telegram.ui.Animation;

import android.graphics.Camera;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

public class View10 extends Animation {

    public static boolean NEED_PROXY = Build.VERSION.SDK_INT < 11;

    private static final WeakHashMap<View, View10> PROXIES = new WeakHashMap<>();

    public static View10 wrap(View view) {
        View10 proxy = PROXIES.get(view);
        Animation animation = view.getAnimation();
        if (proxy == null || proxy != animation && animation != null) {
            proxy = new View10(view);
            PROXIES.put(view, proxy);
        } else if (animation == null) {
            view.setAnimation(proxy);
        }
        return proxy;
    }

    private final WeakReference<View> mView;
    private final Camera mCamera = new Camera();
    private boolean mHasPivot;

    private float mAlpha = 1;
    private float mPivotX;
    private float mPivotY;
    private float mRotationX;
    private float mRotationY;
    private float mRotationZ;
    private float mScaleX = 1;
    private float mScaleY = 1;
    private float mTranslationX;
    private float mTranslationY;

    private final RectF mBefore = new RectF();
    private final RectF mAfter = new RectF();
    private final Matrix mTempMatrix = new Matrix();

    private View10(View view) {
        setDuration(0);
        setFillAfter(true);
        view.setAnimation(this);
        mView = new WeakReference<>(view);
    }

    public float getAlpha() {
        return mAlpha;
    }

    public void setAlpha(float alpha) {
        if (mAlpha != alpha) {
            mAlpha = alpha;
            View view = mView.get();
            if (view != null) {
                view.invalidate();
            }
        }
    }

    public float getPivotX() {
        return mPivotX;
    }

    public void setPivotX(float pivotX) {
        if (!mHasPivot || mPivotX != pivotX) {
            prepareForUpdate();
            mHasPivot = true;
            mPivotX = pivotX;
            invalidateAfterUpdate();
        }
    }

    public float getPivotY() {
        return mPivotY;
    }

    public void setPivotY(float pivotY) {
        if (!mHasPivot || mPivotY != pivotY) {
            prepareForUpdate();
            mHasPivot = true;
            mPivotY = pivotY;
            invalidateAfterUpdate();
        }
    }

    public float getRotation() {
        return mRotationZ;
    }

    public void setRotation(float rotation) {
        if (mRotationZ != rotation) {
            prepareForUpdate();
            mRotationZ = rotation;
            invalidateAfterUpdate();
        }
    }

    public float getRotationX() {
        return mRotationX;
    }

    public void setRotationX(float rotationX) {
        if (mRotationX != rotationX) {
            prepareForUpdate();
            mRotationX = rotationX;
            invalidateAfterUpdate();
        }
    }

    public float getRotationY() {
        return mRotationY;
    }

    public void setRotationY(float rotationY) {
        if (mRotationY != rotationY) {
            prepareForUpdate();
            mRotationY = rotationY;
            invalidateAfterUpdate();
        }
    }

    public float getScaleX() {
        return mScaleX;
    }

    public void setScaleX(float scaleX) {
        if (mScaleX != scaleX) {
            prepareForUpdate();
            mScaleX = scaleX;
            invalidateAfterUpdate();
        }
    }

    public float getScaleY() {
        return mScaleY;
    }

    public void setScaleY(float scaleY) {
        if (mScaleY != scaleY) {
            prepareForUpdate();
            mScaleY = scaleY;
            invalidateAfterUpdate();
        }
    }

    public int getScrollX() {
        View view = mView.get();
        if (view == null) {
            return 0;
        }
        return view.getScrollX();
    }

    public void setScrollX(int value) {
        View view = mView.get();
        if (view != null) {
            view.scrollTo(value, view.getScrollY());
        }
    }

    public int getScrollY() {
        View view = mView.get();
        if (view == null) {
            return 0;
        }
        return view.getScrollY();
    }

    public void setScrollY(int value) {
        View view = mView.get();
        if (view != null) {
            view.scrollTo(view.getScrollX(), value);
        }
    }

    public float getTranslationX() {
        return mTranslationX;
    }

    public void setTranslationX(float translationX) {
        if (mTranslationX != translationX) {
            prepareForUpdate();
            mTranslationX = translationX;
            invalidateAfterUpdate();
        }
    }

    public float getTranslationY() {
        return mTranslationY;
    }

    public void setTranslationY(float translationY) {
        if (mTranslationY != translationY) {
            prepareForUpdate();
            mTranslationY = translationY;
            invalidateAfterUpdate();
        }
    }

    public float getX() {
        View view = mView.get();
        if (view == null) {
            return 0;
        }
        return view.getLeft() + mTranslationX;
    }

    public void setX(float x) {
        View view = mView.get();
        if (view != null) {
            setTranslationX(x - view.getLeft());
        }
    }

    public float getY() {
        View view = mView.get();
        if (view == null) {
            return 0;
        }
        return view.getTop() + mTranslationY;
    }

    public void setY(float y) {
        View view = mView.get();
        if (view != null) {
            setTranslationY(y - view.getTop());
        }
    }

    private void prepareForUpdate() {
        View view = mView.get();
        if (view != null) {
            computeRect(mBefore, view);
        }
    }

    private void invalidateAfterUpdate() {
        View view = mView.get();
        if (view == null || view.getParent() == null) {
            return;
        }

        final RectF after = mAfter;
        computeRect(after, view);
        after.union(mBefore);

        ((View) view.getParent()).invalidate(
                (int) Math.floor(after.left),
                (int) Math.floor(after.top),
                (int) Math.ceil(after.right),
                (int) Math.ceil(after.bottom));
    }

    private void computeRect(final RectF r, View view) {
        final float w = view.getWidth();
        final float h = view.getHeight();

        r.set(0, 0, w, h);

        final Matrix m = mTempMatrix;
        m.reset();
        transformMatrix(m, view);
        mTempMatrix.mapRect(r);

        r.offset(view.getLeft(), view.getTop());

        if (r.right < r.left) {
            final float f = r.right;
            r.right = r.left;
            r.left = f;
        }
        if (r.bottom < r.top) {
            final float f = r.top;
            r.top = r.bottom;
            r.bottom = f;
        }
    }

    private void transformMatrix(Matrix m, View view) {
        final float w = view.getWidth();
        final float h = view.getHeight();
        final boolean hasPivot = mHasPivot;
        final float pX = hasPivot ? mPivotX : w / 2f;
        final float pY = hasPivot ? mPivotY : h / 2f;

        final float rX = mRotationX;
        final float rY = mRotationY;
        final float rZ = mRotationZ;
        if ((rX != 0) || (rY != 0) || (rZ != 0)) {
            final Camera camera = mCamera;
            camera.save();
            camera.rotateX(rX);
            camera.rotateY(rY);
            camera.rotateZ(-rZ);
            camera.getMatrix(m);
            camera.restore();
            m.preTranslate(-pX, -pY);
            m.postTranslate(pX, pY);
        }

        final float sX = mScaleX;
        final float sY = mScaleY;
        if ((sX != 1.0f) || (sY != 1.0f)) {
            m.postScale(sX, sY);
            final float sPX = -(pX / w) * ((sX * w) - w);
            final float sPY = -(pY / h) * ((sY * h) - h);
            m.postTranslate(sPX, sPY);
        }

        m.postTranslate(mTranslationX, mTranslationY);
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        View view = mView.get();
        if (view != null) {
            t.setAlpha(mAlpha);
            transformMatrix(t.getMatrix(), view);
        }
    }
}

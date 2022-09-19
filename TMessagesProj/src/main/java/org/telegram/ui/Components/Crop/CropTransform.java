/*
 * This is the source code of Telegram for Android v. 6.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.Components.Crop;

import androidx.annotation.NonNull;

public class CropTransform {

    private boolean hasTransform;
    private float cropPx;
    private float cropPy;
    private float cropAreaX;
    private float cropAreaY;
    private float cropScale;
    private float cropRotation;
    private boolean isMirrored;
    private int cropOrientation;
    private float cropPw;
    private float cropPh;
    private float trueCropScale;
    private float minScale;

    public void setViewTransform(boolean set) {
        hasTransform = set;
    }

    public void setViewTransform(boolean set, float px, float py, float rotate, int orientation, float scale, float cs, float ms, float pw, float ph, float cx, float cy, boolean mirrored) {
        hasTransform = set;
        cropPx = px;
        cropPy = py;
        cropScale = scale;
        cropRotation = rotate;
        cropOrientation = orientation;
        while (cropOrientation < 0) {
            cropOrientation += 360;
        }
        while (cropOrientation >= 360) {
            cropOrientation -= 360;
        }
        cropPw = pw;
        cropPh = ph;
        cropAreaX = cx;
        cropAreaY = cy;
        trueCropScale = cs;
        minScale = ms;
        isMirrored = mirrored;
    }

    public boolean hasViewTransform() {
        return hasTransform;
    }

    public float getCropAreaX() {
        return cropAreaX;
    }

    public float getCropAreaY() {
        return cropAreaY;
    }

    public float getCropPx() {
        return cropPx;
    }

    public float getCropPy() {
        return cropPy;
    }

    public float getScale() {
        return cropScale;
    }

    public float getRotation() {
        return cropRotation;
    }

    public int getOrientation() {
        return cropOrientation;
    }

    public float getTrueCropScale() {
        return trueCropScale;
    }

    public float getMinScale() {
        return minScale;
    }

    public float getCropPw() {
        return cropPw;
    }

    public float getCropPh() {
        return cropPh;
    }

    public boolean isMirrored() {
        return isMirrored;
    }

    @Override
    public CropTransform clone() {
        CropTransform cloned = new CropTransform();
        cloned.hasTransform = this.hasTransform;
        cloned.cropPx = this.cropPx;
        cloned.cropPy = this.cropPy;
        cloned.cropAreaX = this.cropAreaX;
        cloned.cropAreaY = this.cropAreaY;
        cloned.cropScale = this.cropScale;
        cloned.cropRotation = this.cropRotation;
        cloned.isMirrored = this.isMirrored;
        cloned.cropOrientation = this.cropOrientation;
        cloned.cropPw = this.cropPw;
        cloned.cropPh = this.cropPh;
        cloned.trueCropScale = this.trueCropScale;
        cloned.minScale = this.minScale;
        return cloned;
    }
}

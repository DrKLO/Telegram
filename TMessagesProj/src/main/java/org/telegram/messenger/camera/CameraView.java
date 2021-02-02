package org.telegram.messenger.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class CameraView extends FrameLayout {
    public CameraView(@NonNull Context context) {
        super(context);
    }

    public CameraView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CameraView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public abstract boolean isInitied();

    public abstract boolean isFrontface();

    public abstract void setClipTop(int cameraViewOffsetY);

    public abstract void setClipBottom(int cameraViewOffsetBottomY);

    public interface CameraViewDelegate {
        void onCameraCreated();
        void onCameraInit();
    }

    public abstract void initCamera();
    public abstract void switchCamera();
    public abstract boolean hasFrontFaceCamera();
    public abstract void setUseMaxPreview(boolean value);
    public abstract void setMirror(boolean value);
    public abstract void setOptimizeForBarcode(boolean value);
    public abstract void setDelegate(CameraViewDelegate cameraViewDelegate);
    public abstract void setZoom(float value);
    public abstract void focusToPoint(int x, int y);
    public abstract Size getPreviewSize();

}

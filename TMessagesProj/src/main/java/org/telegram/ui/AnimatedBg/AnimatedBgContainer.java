package org.telegram.ui.AnimatedBg;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class AnimatedBgContainer extends FrameLayout {

    public AnimatedBgGLSurfaceView animatedBgGLSurfaceView;
    public ImageView stubImageView;

    private boolean firstDisplayPreview = true;
    private boolean previewMode = false;
    private boolean started = false;

    public AnimatedBgContainer(@NonNull Context context) {
        super(context);
        animatedBgGLSurfaceView = new AnimatedBgGLSurfaceView(getContext());
        stubImageView = new ImageView(getContext());
        stubImageView.setScaleType(ImageView.ScaleType.FIT_XY);
        addView(
                stubImageView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT)
        );
    }

    public void onStart() {
        if (started) {
            return;
        }
        addView(
                animatedBgGLSurfaceView, 0,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT)
        );
        started = true;
    }

    public void onStop() {
        if (!started) {
            return;
        }
        removeView(animatedBgGLSurfaceView);
        started = false;
    }

    public void onDestroy() {
        started = false;
        removeAllViews();
    }

    public void displayPreview() {
        displayPreview(true);
    }
    
    public void displayPreview(boolean checkSnapshotCache) {
        if (!started) {
            return;
        }
        previewMode = true;
        updatePreviewState();
        Bitmap cachedBitmap = Theme.blurBgBitmap;
        String colorHash = Theme.blurBgBitmapColorHash;
        String currentColorHash = animatedBgGLSurfaceView.animatorEngine.getColorsHash();
        boolean haveCached = cachedBitmap != null && currentColorHash.equals(colorHash);
        if (firstDisplayPreview && haveCached && checkSnapshotCache) {
            stubImageView.setImageBitmap(cachedBitmap);
        } else {
            animatedBgGLSurfaceView.requestSnapshot(bitmap -> {
                if (!haveCached) {
                    Theme.blurBgBitmap = bitmap;
                    Theme.blurBgBitmapColorHash = currentColorHash;
                }
                stubImageView.setImageBitmap(bitmap);
            });
        }
        firstDisplayPreview = false;
    }


    public void displayBg() {
        displayBg(false);
    }

    public void displayBg(boolean cacheSnapshot) {
        if (!started) {
            return;
        }
        previewMode = false;
        updateBgState();
        if (cacheSnapshot) {
            String currentColorHash = animatedBgGLSurfaceView.animatorEngine.getColorsHash();
            animatedBgGLSurfaceView.requestSnapshot(bitmap -> {
                Theme.blurBgBitmap = bitmap;
                Theme.blurBgBitmapColorHash = currentColorHash;
            });
        }
    }

    private void updatePreviewState() {
        animatedBgGLSurfaceView.setTranslationX(animatedBgGLSurfaceView.getMeasuredWidth());
        stubImageView.setVisibility(View.VISIBLE);
        setBackgroundColor(animatedBgGLSurfaceView.animatorEngine.points[0].color);
    }

    private void updateBgState() {
        animatedBgGLSurfaceView.setTranslationX(0);
        post(() -> stubImageView.setVisibility(View.INVISIBLE));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (previewMode) {
            updatePreviewState();
        } else {
            updateBgState();
        }
    }
}
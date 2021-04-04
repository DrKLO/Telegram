package org.telegram.ui.AnimatedBg;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class AnimatedBgContainer extends FrameLayout {

    private AnimatedBgGLSurfaceView animatedBgGLSurfaceView;
    public ImageView stubImageView;

    private boolean firstDisplayPreview = true;
    private boolean previewMode = false;
    private boolean started = false;
    private boolean snapshotReady;

    private boolean displayPreviewAfterStart;
    private boolean pendingDisplayFromCacheIfExists;
    private boolean pendingDoNotUpdateSnapshotIfColorNotChange;

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

    public void updateColors() {
        animatedBgGLSurfaceView.updateColors();
    }

    public boolean isEnabledGravityProcessing() {
        return animatedBgGLSurfaceView.isEnabledGravityProcessing();
    }

    public void setEnabledGravityProcessing(boolean enabledGravityProcessing) {
        animatedBgGLSurfaceView.setEnabledGravityProcessing(enabledGravityProcessing);
    }

    public void animateToNext(MessagesController.AnimationConfig animationConfig) {
        animateToNext(animationConfig, null);
    }

    public void animateToNext(MessagesController.AnimationConfig animationConfig, AnimatorEngine.FinishMoveAnimationListener listener) {
        animatedBgGLSurfaceView.animateToNext(animationConfig, listener);
    }

    public void requestSnapshot(AnimatedBgGLSurfaceView.SnapshotListener snapshotListener) {
        animatedBgGLSurfaceView.requestSnapshot(snapshotListener);
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
        if(displayPreviewAfterStart) {
            displayPreviewAfterStart = false;
            displayPreview(
                    pendingDisplayFromCacheIfExists, pendingDoNotUpdateSnapshotIfColorNotChange
            );
        }
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
        post(this::removeAllViews);
    }

    public void displayPreview() {
        displayPreview(true, false);
    }

    public void displayPreview(boolean displayFromCacheIfExists, boolean doNotUpdateSnapshotIfColorNotChange) {
        if (!started) {
            displayPreviewAfterStart = true;
            pendingDisplayFromCacheIfExists = displayFromCacheIfExists;
            pendingDoNotUpdateSnapshotIfColorNotChange = doNotUpdateSnapshotIfColorNotChange;
            return;
        }

        animatedBgGLSurfaceView.updateColors();
        animatedBgGLSurfaceView.cancelAnimation();

        previewMode = true;
        snapshotReady = false;
        updatePreviewState();
        Bitmap cachedBitmap = Theme.blurBgBitmap;
        String colorHash = Theme.blurBgBitmapColorHash;
        String currentColorHash = animatedBgGLSurfaceView.animatorEngine.getColorsHash();
        String lastColorHash = (String) stubImageView.getTag();
        if(currentColorHash.equals(lastColorHash) && doNotUpdateSnapshotIfColorNotChange) {
            snapshotReady = true;
            updatePreviewState();
            return;
        }
        stubImageView.setImageDrawable(null);
        boolean wasCached = cachedBitmap != null && currentColorHash.equals(colorHash);
        if (firstDisplayPreview && wasCached && displayFromCacheIfExists) {
            stubImageView.setImageBitmap(cachedBitmap);
            stubImageView.setTag(currentColorHash);
            snapshotReady = true;
            updatePreviewState();
        } else {
            animatedBgGLSurfaceView.requestSnapshot(bitmap -> {
                if (!wasCached) {
                    Theme.blurBgBitmap = bitmap;
                    Theme.blurBgBitmapColorHash = currentColorHash;
                }
                stubImageView.setImageBitmap(bitmap);
                stubImageView.setTag(currentColorHash);
                snapshotReady = true;
                updatePreviewState();
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
                stubImageView.setImageBitmap(bitmap);
                stubImageView.setTag(currentColorHash);
            });
        }
    }

    private void updatePreviewState() {
        if (snapshotReady) {
            animatedBgGLSurfaceView.setTranslationX(10_000);
            stubImageView.setVisibility(View.VISIBLE);
        }
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
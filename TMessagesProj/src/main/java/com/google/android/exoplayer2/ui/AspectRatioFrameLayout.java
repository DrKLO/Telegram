/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.ui;

import android.content.Context;
import android.graphics.Matrix;
import androidx.annotation.IntDef;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A {@link FrameLayout} that resizes itself to match a specified aspect ratio.
 */
public class AspectRatioFrameLayout extends FrameLayout {

  /** Listener to be notified about changes of the aspect ratios of this view. */
  public interface AspectRatioListener {

    /**
     * Called when either the target aspect ratio or the view aspect ratio is updated.
     *
     * @param targetAspectRatio The aspect ratio that has been set in {@link #setAspectRatio(float)}
     * @param naturalAspectRatio The natural aspect ratio of this view (before its width and height
     *     are modified to satisfy the target aspect ratio).
     * @param aspectRatioMismatch Whether the target and natural aspect ratios differ enough for
     *     changing the resize mode to have an effect.
     */
    void onAspectRatioUpdated(
        float targetAspectRatio, float naturalAspectRatio, boolean aspectRatioMismatch);
  }

  // LINT.IfChange
  /**
   * Resize modes for {@link AspectRatioFrameLayout}. One of {@link #RESIZE_MODE_FIT}, {@link
   * #RESIZE_MODE_FIXED_WIDTH}, {@link #RESIZE_MODE_FIXED_HEIGHT}, {@link #RESIZE_MODE_FILL} or
   * {@link #RESIZE_MODE_ZOOM}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    RESIZE_MODE_FIT,
    RESIZE_MODE_FIXED_WIDTH,
    RESIZE_MODE_FIXED_HEIGHT,
    RESIZE_MODE_FILL,
    RESIZE_MODE_ZOOM
  })
  public @interface ResizeMode {}

  /**
   * Either the width or height is decreased to obtain the desired aspect ratio.
   */
  public static final int RESIZE_MODE_FIT = 0;
  /**
   * The width is fixed and the height is increased or decreased to obtain the desired aspect ratio.
   */
  public static final int RESIZE_MODE_FIXED_WIDTH = 1;
  /**
   * The height is fixed and the width is increased or decreased to obtain the desired aspect ratio.
   */
  public static final int RESIZE_MODE_FIXED_HEIGHT = 2;
  /**
   * The specified aspect ratio is ignored.
   */
  public static final int RESIZE_MODE_FILL = 3;
  /**
   * Either the width or height is increased to obtain the desired aspect ratio.
   */
  public static final int RESIZE_MODE_ZOOM = 4;
  // LINT.ThenChange(../../../../../../res/values/attrs.xml)

  /**
   * The {@link FrameLayout} will not resize itself if the fractional difference between its natural
   * aspect ratio and the requested aspect ratio falls below this threshold.
   *
   * <p>This tolerance allows the view to occupy the whole of the screen when the requested aspect
   * ratio is very close, but not exactly equal to, the aspect ratio of the screen. This may reduce
   * the number of view layers that need to be composited by the underlying system, which can help
   * to reduce power consumption.
   */
  private static final float MAX_ASPECT_RATIO_DEFORMATION_FRACTION = 0.01f;

  private final AspectRatioUpdateDispatcher aspectRatioUpdateDispatcher;

  private AspectRatioListener aspectRatioListener;

  private float videoAspectRatio;
  private @ResizeMode int resizeMode;
  private boolean drawingReady;
  private int rotation;
  private Matrix matrix = new Matrix();

  public AspectRatioFrameLayout(Context context) {
    super(context);
    resizeMode = RESIZE_MODE_FIT;
    aspectRatioUpdateDispatcher = new AspectRatioUpdateDispatcher();
  }

  /**
   * Sets the aspect ratio that this view should satisfy.
   *
   * @param widthHeightRatio The width to height ratio.
   */
  public void setAspectRatio(float widthHeightRatio, int rotation) {
    if (this.videoAspectRatio != widthHeightRatio) {
      this.videoAspectRatio = widthHeightRatio;
      this.rotation = rotation;
      requestLayout();
    }
  }

  /**
   * Sets the {@link AspectRatioListener}.
   *
   * @param listener The listener to be notified about aspect ratios changes.
   */
  public void setAspectRatioListener(AspectRatioListener listener) {
    this.aspectRatioListener = listener;
  }

  /** Returns the {@link ResizeMode}. */
  public @ResizeMode int getResizeMode() {
    return resizeMode;
  }

  /**
   * Sets the {@link ResizeMode}
   *
   * @param resizeMode The {@link ResizeMode}.
   */
  public void setResizeMode(@ResizeMode int resizeMode) {
    if (this.resizeMode != resizeMode) {
      this.resizeMode = resizeMode;
      requestLayout();
    }
  }

  public void setDrawingReady(boolean value) {
    if (drawingReady == value) {
      return;
    }
    drawingReady = value;
  }

  public float getAspectRatio() {
    return videoAspectRatio;
  }

  public int getVideoRotation() {
    return rotation;
  }

  public boolean isDrawingReady() {
    return drawingReady;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (videoAspectRatio <= 0) {
      // Aspect ratio not set.
      return;
    }

    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    float viewAspectRatio = (float) width / height;
    float aspectDeformation = videoAspectRatio / viewAspectRatio - 1;
    if (Math.abs(aspectDeformation) <= MAX_ASPECT_RATIO_DEFORMATION_FRACTION) {
      // We're within the allowed tolerance.
      aspectRatioUpdateDispatcher.scheduleUpdate(videoAspectRatio, viewAspectRatio, false);
      return;
    }

    switch (resizeMode) {
      case RESIZE_MODE_FIXED_WIDTH:
        height = (int) (width / videoAspectRatio);
        break;
      case RESIZE_MODE_FIXED_HEIGHT:
        width = (int) (height * videoAspectRatio);
        break;
      case RESIZE_MODE_ZOOM:
        if (aspectDeformation > 0) {
          width = (int) (height * videoAspectRatio);
        } else {
          height = (int) (width / videoAspectRatio);
        }
        break;
      case RESIZE_MODE_FIT:
        if (aspectDeformation > 0) {
          height = (int) (width / videoAspectRatio);
        } else {
          width = (int) (height * videoAspectRatio);
        }
        break;
      case RESIZE_MODE_FILL:
        if (aspectDeformation <= 0) {
          height = (int) (width / videoAspectRatio);
        } else {
          width = (int) (height * videoAspectRatio);
        }
      default:
        // Ignore target aspect ratio
        break;
    }
    aspectRatioUpdateDispatcher.scheduleUpdate(videoAspectRatio, viewAspectRatio, true);
    super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));

    int count = getChildCount();
    for (int a = 0; a < count; a++) {
      View child = getChildAt(a);
      if (child instanceof TextureView) {
        matrix.reset();
        int px = getWidth() / 2;
        int py = getHeight() / 2;
        matrix.postRotate(rotation, px, py);
        if (rotation == 90 || rotation == 270) {
          float ratio = (float) getHeight() / getWidth();
          matrix.postScale(1 / ratio, ratio, px, py);
        }
        ((TextureView) child).setTransform(matrix);
        break;
      }
    }
  }

  /** Dispatches updates to {@link AspectRatioListener}. */
  private final class AspectRatioUpdateDispatcher implements Runnable {

    private float targetAspectRatio;
    private float naturalAspectRatio;
    private boolean aspectRatioMismatch;
    private boolean isScheduled;

    public void scheduleUpdate(
        float targetAspectRatio, float naturalAspectRatio, boolean aspectRatioMismatch) {
      this.targetAspectRatio = targetAspectRatio;
      this.naturalAspectRatio = naturalAspectRatio;
      this.aspectRatioMismatch = aspectRatioMismatch;

      if (!isScheduled) {
        isScheduled = true;
        post(this);
      }
    }

    @Override
    public void run() {
      isScheduled = false;
      if (aspectRatioListener == null) {
        return;
      }
      aspectRatioListener.onAspectRatioUpdated(
          targetAspectRatio, naturalAspectRatio, aspectRatioMismatch);
    }
  }
}

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
package com.google.android.exoplayer2.video;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import androidx.annotation.Nullable;

/**
 * GLSurfaceView for rendering video output. To render video in this view, call {@link
 * #getVideoDecoderOutputBufferRenderer()} to get a {@link VideoDecoderOutputBufferRenderer} that
 * will render video decoder output buffers in this view.
 *
 * <p>This view is intended for use only with extension renderers. For other use cases a {@link
 * android.view.SurfaceView} or {@link android.view.TextureView} should be used instead.
 */
public class VideoDecoderGLSurfaceView extends GLSurfaceView {

  private final VideoDecoderRenderer renderer;

  /** @param context A {@link Context}. */
  public VideoDecoderGLSurfaceView(Context context) {
    this(context, /* attrs= */ null);
  }

  /**
   * @param context A {@link Context}.
   * @param attrs Custom attributes.
   */
  public VideoDecoderGLSurfaceView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    renderer = new VideoDecoderRenderer(this);
    setPreserveEGLContextOnPause(true);
    setEGLContextClientVersion(2);
    setRenderer(renderer);
    setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
  }

  /** Returns the {@link VideoDecoderOutputBufferRenderer} that will render frames in this view. */
  public VideoDecoderOutputBufferRenderer getVideoDecoderOutputBufferRenderer() {
    return renderer;
  }
}

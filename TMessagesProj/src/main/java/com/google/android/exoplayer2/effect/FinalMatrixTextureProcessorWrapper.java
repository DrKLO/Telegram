/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.effect;

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.FrameProcessingException;
import com.google.android.exoplayer2.util.FrameProcessor;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.SurfaceInfo;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Wrapper around a {@link GlTextureProcessor} that writes to the provided output surface and
 * optional debug surface view.
 *
 * <p>The wrapped {@link GlTextureProcessor} applies the {@link GlMatrixTransformation} and {@link
 * RgbMatrix} instances passed to the constructor, followed by any transformations needed to convert
 * the frames to the dimensions specified by the provided {@link SurfaceInfo}.
 *
 * <p>This wrapper is used for the final {@link GlTextureProcessor} instance in the chain of {@link
 * GlTextureProcessor} instances used by {@link FrameProcessor}.
 */
/* package */ final class FinalMatrixTextureProcessorWrapper implements ExternalTextureProcessor {

  private static final String TAG = "FinalProcessorWrapper";

  private final Context context;
  private final ImmutableList<GlMatrixTransformation> matrixTransformations;
  private final ImmutableList<RgbMatrix> rgbMatrices;
  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final DebugViewProvider debugViewProvider;
  private final FrameProcessor.Listener frameProcessorListener;
  private final boolean sampleFromExternalTexture;
  private final ColorInfo colorInfo;
  private final boolean releaseFramesAutomatically;
  private final float[] textureTransformMatrix;
  private final Queue<Long> streamOffsetUsQueue;
  private final Queue<Pair<TextureInfo, Long>> availableFrames;

  private int inputWidth;
  private int inputHeight;
  @Nullable private MatrixTextureProcessor matrixTextureProcessor;
  @Nullable private SurfaceViewWrapper debugSurfaceViewWrapper;
  private InputListener inputListener;
  private @MonotonicNonNull Pair<Integer, Integer> outputSizeBeforeSurfaceTransformation;
  @Nullable private SurfaceView debugSurfaceView;

  private volatile boolean outputSizeOrRotationChanged;

  @GuardedBy("this")
  @Nullable
  private SurfaceInfo outputSurfaceInfo;

  @GuardedBy("this")
  @Nullable
  private EGLSurface outputEglSurface;

  public FinalMatrixTextureProcessorWrapper(
      Context context,
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      ImmutableList<GlMatrixTransformation> matrixTransformations,
      ImmutableList<RgbMatrix> rgbMatrices,
      FrameProcessor.Listener frameProcessorListener,
      DebugViewProvider debugViewProvider,
      boolean sampleFromExternalTexture,
      ColorInfo colorInfo,
      boolean releaseFramesAutomatically) {
    this.context = context;
    this.matrixTransformations = matrixTransformations;
    this.rgbMatrices = rgbMatrices;
    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.debugViewProvider = debugViewProvider;
    this.frameProcessorListener = frameProcessorListener;
    this.sampleFromExternalTexture = sampleFromExternalTexture;
    this.colorInfo = colorInfo;
    this.releaseFramesAutomatically = releaseFramesAutomatically;

    textureTransformMatrix = GlUtil.create4x4IdentityMatrix();
    streamOffsetUsQueue = new ConcurrentLinkedQueue<>();
    inputListener = new InputListener() {};
    availableFrames = new ConcurrentLinkedQueue<>();
  }

  @Override
  public void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public void setOutputListener(OutputListener outputListener) {
    // The FrameProcessor.Listener passed to the constructor is used for output-related events.
    throw new UnsupportedOperationException();
  }

  @Override
  public void setErrorListener(ErrorListener errorListener) {
    // The FrameProcessor.Listener passed to the constructor is used for errors.
    throw new UnsupportedOperationException();
  }

  @Override
  public void queueInputFrame(TextureInfo inputTexture, long presentationTimeUs) {
    long streamOffsetUs =
        checkStateNotNull(streamOffsetUsQueue.peek(), "No input stream specified.");
    long offsetPresentationTimeUs = presentationTimeUs + streamOffsetUs;
    frameProcessorListener.onOutputFrameAvailable(offsetPresentationTimeUs);
    if (releaseFramesAutomatically) {
      renderFrameToSurfaces(
          inputTexture, presentationTimeUs, /* releaseTimeNs= */ offsetPresentationTimeUs * 1000);
    } else {
      availableFrames.add(Pair.create(inputTexture, presentationTimeUs));
    }
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public void releaseOutputFrame(TextureInfo outputTexture) {
    // The final texture processor writes to a surface so there is no texture to release.
    throw new UnsupportedOperationException();
  }

  @WorkerThread
  public void releaseOutputFrame(long releaseTimeNs) {
    checkState(!releaseFramesAutomatically);
    Pair<TextureInfo, Long> oldestAvailableFrame = availableFrames.remove();
    renderFrameToSurfaces(
        /* inputTexture= */ oldestAvailableFrame.first,
        /* presentationTimeUs= */ oldestAvailableFrame.second,
        releaseTimeNs);
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    checkState(!streamOffsetUsQueue.isEmpty(), "No input stream to end.");

    streamOffsetUsQueue.remove();
    if (streamOffsetUsQueue.isEmpty()) {
      frameProcessorListener.onFrameProcessingEnded();
    }
  }

  @Override
  @WorkerThread
  public void release() throws FrameProcessingException {
    if (matrixTextureProcessor != null) {
      matrixTextureProcessor.release();
    }
  }

  @Override
  public void setTextureTransformMatrix(float[] textureTransformMatrix) {
    System.arraycopy(
        /* src= */ textureTransformMatrix,
        /* srcPos= */ 0,
        /* dest= */ this.textureTransformMatrix,
        /* destPost= */ 0,
        /* length= */ textureTransformMatrix.length);

    if (matrixTextureProcessor != null) {
      matrixTextureProcessor.setTextureTransformMatrix(textureTransformMatrix);
    }
  }

  /**
   * Signals that there will be another input stream after all previously appended input streams
   * have {@linkplain #signalEndOfCurrentInputStream() ended}.
   *
   * <p>This method does not need to be called on the GL thread, but the caller must ensure that
   * stream offsets are appended in the correct order.
   *
   * @param streamOffsetUs The presentation timestamp offset, in microseconds.
   */
  public void appendStream(long streamOffsetUs) {
    streamOffsetUsQueue.add(streamOffsetUs);
  }

  /**
   * Sets the output {@link SurfaceInfo}.
   *
   * @see FrameProcessor#setOutputSurfaceInfo(SurfaceInfo)
   */
  public synchronized void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
    if (!Util.areEqual(this.outputSurfaceInfo, outputSurfaceInfo)) {
      if (outputSurfaceInfo != null
          && this.outputSurfaceInfo != null
          && !this.outputSurfaceInfo.surface.equals(outputSurfaceInfo.surface)) {
        this.outputEglSurface = null;
      }
      outputSizeOrRotationChanged =
          this.outputSurfaceInfo == null
              || outputSurfaceInfo == null
              || this.outputSurfaceInfo.width != outputSurfaceInfo.width
              || this.outputSurfaceInfo.height != outputSurfaceInfo.height
              || this.outputSurfaceInfo.orientationDegrees != outputSurfaceInfo.orientationDegrees;
      this.outputSurfaceInfo = outputSurfaceInfo;
    }
  }

  private void renderFrameToSurfaces(
      TextureInfo inputTexture, long presentationTimeUs, long releaseTimeNs) {
    try {
      maybeRenderFrameToOutputSurface(inputTexture, presentationTimeUs, releaseTimeNs);
    } catch (FrameProcessingException | GlUtil.GlException e) {
      frameProcessorListener.onFrameProcessingError(
          FrameProcessingException.from(e, presentationTimeUs));
    }
    maybeRenderFrameToDebugSurface(inputTexture, presentationTimeUs);
    inputListener.onInputFrameProcessed(inputTexture);
  }

  private synchronized void maybeRenderFrameToOutputSurface(
      TextureInfo inputTexture, long presentationTimeUs, long releaseTimeNs)
      throws FrameProcessingException, GlUtil.GlException {
    if (releaseTimeNs == FrameProcessor.DROP_OUTPUT_FRAME
        || !ensureConfigured(inputTexture.width, inputTexture.height)) {
      return; // Drop frames when requested, or there is no output surface.
    }

    EGLSurface outputEglSurface = this.outputEglSurface;
    SurfaceInfo outputSurfaceInfo = this.outputSurfaceInfo;
    MatrixTextureProcessor matrixTextureProcessor = this.matrixTextureProcessor;

    GlUtil.focusEglSurface(
        eglDisplay,
        eglContext,
        outputEglSurface,
        outputSurfaceInfo.width,
        outputSurfaceInfo.height);
    GlUtil.clearOutputFrame();
    matrixTextureProcessor.drawFrame(inputTexture.texId, presentationTimeUs);

    EGLExt.eglPresentationTimeANDROID(
        eglDisplay,
        outputEglSurface,
        releaseTimeNs == FrameProcessor.RELEASE_OUTPUT_FRAME_IMMEDIATELY
            ? System.nanoTime()
            : releaseTimeNs);
    EGL14.eglSwapBuffers(eglDisplay, outputEglSurface);
  }

  @EnsuresNonNullIf(
      expression = {"outputSurfaceInfo", "outputEglSurface", "matrixTextureProcessor"},
      result = true)
  private synchronized boolean ensureConfigured(int inputWidth, int inputHeight)
      throws FrameProcessingException, GlUtil.GlException {

    if (this.inputWidth != inputWidth
        || this.inputHeight != inputHeight
        || this.outputSizeBeforeSurfaceTransformation == null) {
      this.inputWidth = inputWidth;
      this.inputHeight = inputHeight;
      Pair<Integer, Integer> outputSizeBeforeSurfaceTransformation =
          MatrixUtils.configureAndGetOutputSize(inputWidth, inputHeight, matrixTransformations);
      if (!Util.areEqual(
          this.outputSizeBeforeSurfaceTransformation, outputSizeBeforeSurfaceTransformation)) {
        this.outputSizeBeforeSurfaceTransformation = outputSizeBeforeSurfaceTransformation;
        frameProcessorListener.onOutputSizeChanged(
            outputSizeBeforeSurfaceTransformation.first,
            outputSizeBeforeSurfaceTransformation.second);
      }
    }

    if (outputSurfaceInfo == null) {
      if (matrixTextureProcessor != null) {
        matrixTextureProcessor.release();
        matrixTextureProcessor = null;
      }
      outputEglSurface = null;
      return false;
    }

    SurfaceInfo outputSurfaceInfo = this.outputSurfaceInfo;
    @Nullable EGLSurface outputEglSurface = this.outputEglSurface;
    if (outputEglSurface == null) {
      boolean colorInfoIsHdr = ColorInfo.isTransferHdr(colorInfo);

      outputEglSurface =
          GlUtil.getEglSurface(
              eglDisplay,
              outputSurfaceInfo.surface,
              colorInfoIsHdr
                  ? GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_1010102
                  : GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888);

      @Nullable
      SurfaceView debugSurfaceView =
          debugViewProvider.getDebugPreviewSurfaceView(
              outputSurfaceInfo.width, outputSurfaceInfo.height);
      if (debugSurfaceView != null && !Util.areEqual(this.debugSurfaceView, debugSurfaceView)) {
        debugSurfaceViewWrapper =
            new SurfaceViewWrapper(eglDisplay, eglContext, colorInfoIsHdr, debugSurfaceView);
      }
      this.debugSurfaceView = debugSurfaceView;
    }

    if (matrixTextureProcessor != null && outputSizeOrRotationChanged) {
      matrixTextureProcessor.release();
      matrixTextureProcessor = null;
      outputSizeOrRotationChanged = false;
    }
    if (matrixTextureProcessor == null) {
      matrixTextureProcessor = createMatrixTextureProcessorForOutputSurface(outputSurfaceInfo);
    }

    this.outputSurfaceInfo = outputSurfaceInfo;
    this.outputEglSurface = outputEglSurface;
    return true;
  }

  private MatrixTextureProcessor createMatrixTextureProcessorForOutputSurface(
      SurfaceInfo outputSurfaceInfo) throws FrameProcessingException {
    ImmutableList.Builder<GlMatrixTransformation> matrixTransformationListBuilder =
        new ImmutableList.Builder<GlMatrixTransformation>().addAll(matrixTransformations);
    if (outputSurfaceInfo.orientationDegrees != 0) {
      matrixTransformationListBuilder.add(
          new ScaleToFitTransformation.Builder()
              .setRotationDegrees(outputSurfaceInfo.orientationDegrees)
              .build());
    }
    matrixTransformationListBuilder.add(
        Presentation.createForWidthAndHeight(
            outputSurfaceInfo.width, outputSurfaceInfo.height, Presentation.LAYOUT_SCALE_TO_FIT));

    MatrixTextureProcessor matrixTextureProcessor;
    ImmutableList<GlMatrixTransformation> expandedMatrixTransformations =
        matrixTransformationListBuilder.build();
    if (sampleFromExternalTexture) {
      matrixTextureProcessor =
          MatrixTextureProcessor.createWithExternalSamplerApplyingEotfThenOetf(
              context, expandedMatrixTransformations, rgbMatrices, colorInfo);
    } else {
      matrixTextureProcessor =
          MatrixTextureProcessor.createApplyingOetf(
              context, expandedMatrixTransformations, rgbMatrices, colorInfo);
    }

    matrixTextureProcessor.setTextureTransformMatrix(textureTransformMatrix);
    Pair<Integer, Integer> outputSize = matrixTextureProcessor.configure(inputWidth, inputHeight);
    checkState(outputSize.first == outputSurfaceInfo.width);
    checkState(outputSize.second == outputSurfaceInfo.height);
    return matrixTextureProcessor;
  }

  private void maybeRenderFrameToDebugSurface(TextureInfo inputTexture, long presentationTimeUs) {
    if (debugSurfaceViewWrapper == null || this.matrixTextureProcessor == null) {
      return;
    }

    MatrixTextureProcessor matrixTextureProcessor = this.matrixTextureProcessor;
    try {
      debugSurfaceViewWrapper.maybeRenderToSurfaceView(
          () -> {
            GlUtil.clearOutputFrame();
            matrixTextureProcessor.drawFrame(inputTexture.texId, presentationTimeUs);
          });
    } catch (FrameProcessingException | GlUtil.GlException e) {
      Log.d(TAG, "Error rendering to debug preview", e);
    }
  }

  /**
   * Wrapper around a {@link SurfaceView} that keeps track of whether the output surface is valid,
   * and makes rendering a no-op if not.
   */
  private static final class SurfaceViewWrapper implements SurfaceHolder.Callback {
    private final EGLDisplay eglDisplay;
    private final EGLContext eglContext;
    private final boolean useHdr;

    @GuardedBy("this")
    @Nullable
    private Surface surface;

    @GuardedBy("this")
    @Nullable
    private EGLSurface eglSurface;

    private int width;
    private int height;

    public SurfaceViewWrapper(
        EGLDisplay eglDisplay, EGLContext eglContext, boolean useHdr, SurfaceView surfaceView) {
      this.eglDisplay = eglDisplay;
      this.eglContext = eglContext;
      this.useHdr = useHdr;
      surfaceView.getHolder().addCallback(this);
      surface = surfaceView.getHolder().getSurface();
      width = surfaceView.getWidth();
      height = surfaceView.getHeight();
    }

    /**
     * Focuses the wrapped surface view's surface as an {@link EGLSurface}, renders using {@code
     * renderingTask} and swaps buffers, if the view's holder has a valid surface. Does nothing
     * otherwise.
     */
    @WorkerThread
    public synchronized void maybeRenderToSurfaceView(FrameProcessingTask renderingTask)
        throws GlUtil.GlException, FrameProcessingException {
      if (surface == null) {
        return;
      }

      if (eglSurface == null) {
        eglSurface =
            GlUtil.getEglSurface(
                eglDisplay,
                surface,
                useHdr
                    ? GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_1010102
                    : GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888);
      }
      EGLSurface eglSurface = this.eglSurface;
      GlUtil.focusEglSurface(eglDisplay, eglContext, eglSurface, width, height);
      renderingTask.run();
      EGL14.eglSwapBuffers(eglDisplay, eglSurface);
      // Prevents white flashing on the debug SurfaceView when frames are rendered too fast.
      GLES20.glFinish();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {}

    @Override
    public synchronized void surfaceChanged(
        SurfaceHolder holder, int format, int width, int height) {
      this.width = width;
      this.height = height;
      Surface newSurface = holder.getSurface();
      if (surface == null || !surface.equals(newSurface)) {
        surface = newSurface;
        eglSurface = null;
      }
    }

    @Override
    public synchronized void surfaceDestroyed(SurfaceHolder holder) {
      surface = null;
      eglSurface = null;
      width = C.LENGTH_UNSET;
      height = C.LENGTH_UNSET;
    }
  }
}

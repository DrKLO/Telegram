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

import android.util.Pair;
import androidx.annotation.CallSuper;
import com.google.android.exoplayer2.util.FrameProcessingException;
import com.google.android.exoplayer2.util.GlUtil;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Manages a GLSL shader program for processing a frame. Implementations generally copy input pixels
 * into an output frame, with changes to pixels specific to the implementation.
 *
 * <p>{@code SingleFrameGlTextureProcessor} implementations must produce exactly one output frame
 * per input frame with the same presentation timestamp. For more flexibility, implement {@link
 * GlTextureProcessor} directly.
 *
 * <p>All methods in this class must be called on the thread that owns the OpenGL context.
 */
public abstract class SingleFrameGlTextureProcessor implements GlTextureProcessor {

  private final boolean useHdr;

  private InputListener inputListener;
  private OutputListener outputListener;
  private ErrorListener errorListener;
  private int inputWidth;
  private int inputHeight;
  private @MonotonicNonNull TextureInfo outputTexture;
  private boolean outputTextureInUse;

  /**
   * Creates a {@code SingleFrameGlTextureProcessor} instance.
   *
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   */
  public SingleFrameGlTextureProcessor(boolean useHdr) {
    this.useHdr = useHdr;
    inputListener = new InputListener() {};
    outputListener = new OutputListener() {};
    errorListener = (frameProcessingException) -> {};
  }

  /**
   * Configures the texture processor based on the input dimensions.
   *
   * <p>This method must be called before {@linkplain #drawFrame(int,long) drawing} the first frame
   * and before drawing subsequent frames with different input dimensions.
   *
   * @param inputWidth The input width, in pixels.
   * @param inputHeight The input height, in pixels.
   * @return The output width and height of frames processed through {@link #drawFrame(int, long)}.
   */
  public abstract Pair<Integer, Integer> configure(int inputWidth, int inputHeight);

  /**
   * Draws one frame.
   *
   * <p>This method may only be called after the texture processor has been {@link #configure(int,
   * int) configured}. The caller is responsible for focussing the correct render target before
   * calling this method.
   *
   * <p>A minimal implementation should tell OpenGL to use its shader program, bind the shader
   * program's vertex attributes and uniforms, and issue a drawing command.
   *
   * @param inputTexId Identifier of a 2D OpenGL texture containing the input frame.
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   * @throws FrameProcessingException If an error occurs while processing or drawing the frame.
   */
  public abstract void drawFrame(int inputTexId, long presentationTimeUs)
      throws FrameProcessingException;

  @Override
  public final void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    if (!outputTextureInUse) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public final void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
  }

  @Override
  public final void setErrorListener(ErrorListener errorListener) {
    this.errorListener = errorListener;
  }

  @Override
  public final void queueInputFrame(TextureInfo inputTexture, long presentationTimeUs) {
    checkState(
        !outputTextureInUse,
        "The texture processor does not currently accept input frames. Release prior output frames"
            + " first.");

    try {
      if (outputTexture == null
          || inputTexture.width != inputWidth
          || inputTexture.height != inputHeight) {
        configureOutputTexture(inputTexture.width, inputTexture.height);
      }
      outputTextureInUse = true;
      GlUtil.focusFramebufferUsingCurrentContext(
          outputTexture.fboId, outputTexture.width, outputTexture.height);
      GlUtil.clearOutputFrame();
      drawFrame(inputTexture.texId, presentationTimeUs);
      inputListener.onInputFrameProcessed(inputTexture);
      outputListener.onOutputFrameAvailable(outputTexture, presentationTimeUs);
    } catch (FrameProcessingException | GlUtil.GlException | RuntimeException e) {
      errorListener.onFrameProcessingError(
          e instanceof FrameProcessingException
              ? (FrameProcessingException) e
              : new FrameProcessingException(e));
    }
  }

  @EnsuresNonNull("outputTexture")
  private void configureOutputTexture(int inputWidth, int inputHeight) throws GlUtil.GlException {
    this.inputWidth = inputWidth;
    this.inputHeight = inputHeight;
    Pair<Integer, Integer> outputSize = configure(inputWidth, inputHeight);
    if (outputTexture == null
        || outputSize.first != outputTexture.width
        || outputSize.second != outputTexture.height) {
      if (outputTexture != null) {
        GlUtil.deleteTexture(outputTexture.texId);
      }
      int outputTexId = GlUtil.createTexture(outputSize.first, outputSize.second, useHdr);
      int outputFboId = GlUtil.createFboForTexture(outputTexId);
      outputTexture =
          new TextureInfo(outputTexId, outputFboId, outputSize.first, outputSize.second);
    }
  }

  @Override
  public final void releaseOutputFrame(TextureInfo outputTexture) {
    outputTextureInUse = false;
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public final void signalEndOfCurrentInputStream() {
    outputListener.onCurrentOutputStreamEnded();
  }

  @Override
  @CallSuper
  public void release() throws FrameProcessingException {
    if (outputTexture != null) {
      try {
        GlUtil.deleteTexture(outputTexture.texId);
      } catch (GlUtil.GlException e) {
        throw new FrameProcessingException(e);
      }
    }
  }
}

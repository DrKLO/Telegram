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

import com.google.android.exoplayer2.util.FrameProcessingException;

/**
 * Processes frames from one OpenGL 2D texture to another.
 *
 * <p>The {@code GlTextureProcessor} consumes input frames it accepts via {@link
 * #queueInputFrame(TextureInfo, long)} and surrenders each texture back to the caller via its
 * {@linkplain InputListener#onInputFrameProcessed(TextureInfo) listener} once the texture's
 * contents have been processed.
 *
 * <p>The {@code GlTextureProcessor} produces output frames asynchronously and notifies its owner
 * when they are available via its {@linkplain OutputListener#onOutputFrameAvailable(TextureInfo,
 * long) listener}. The {@code GlTextureProcessor} instance's owner must surrender the texture back
 * to the {@code GlTextureProcessor} via {@link #releaseOutputFrame(TextureInfo)} when it has
 * finished processing it.
 *
 * <p>{@code GlTextureProcessor} implementations can choose to produce output frames before
 * receiving input frames or process several input frames before producing an output frame. However,
 * {@code GlTextureProcessor} implementations cannot assume that they will receive more than one
 * input frame at a time, so they must process each input frame they accept even if they cannot
 * produce output yet.
 *
 * <p>The methods in this interface must be called on the thread that owns the parent OpenGL
 * context. If the implementation uses another OpenGL context, e.g., on another thread, it must
 * configure it to share data with the context of thread the interface methods are called on.
 */
public interface GlTextureProcessor {

  /**
   * Listener for input-related frame processing events.
   *
   * <p>This listener can be called from any thread.
   */
  interface InputListener {
    /**
     * Called when the {@link GlTextureProcessor} is ready to accept another input frame.
     *
     * <p>For each time this method is called, {@link #queueInputFrame(TextureInfo, long)} can be
     * called once.
     */
    default void onReadyToAcceptInputFrame() {}

    /**
     * Called when the {@link GlTextureProcessor} has processed an input frame.
     *
     * <p>The implementation shall not assume the {@link GlTextureProcessor} is {@linkplain
     * #onReadyToAcceptInputFrame ready to accept another input frame} when this method is called.
     *
     * @param inputTexture The {@link TextureInfo} that was used to {@linkplain
     *     #queueInputFrame(TextureInfo, long) queue} the input frame.
     */
    default void onInputFrameProcessed(TextureInfo inputTexture) {}
  }

  /**
   * Listener for output-related frame processing events.
   *
   * <p>This listener can be called from any thread.
   */
  interface OutputListener {
    /**
     * Called when the {@link GlTextureProcessor} has produced an output frame.
     *
     * <p>After the listener's owner has processed the output frame, it must call {@link
     * #releaseOutputFrame(TextureInfo)}. The output frame should be released as soon as possible,
     * as there is no guarantee that the {@link GlTextureProcessor} will produce further output
     * frames before this output frame is released.
     *
     * @param outputTexture A {@link TextureInfo} describing the texture containing the output
     *     frame.
     * @param presentationTimeUs The presentation timestamp of the output frame, in microseconds.
     */
    default void onOutputFrameAvailable(TextureInfo outputTexture, long presentationTimeUs) {}

    /**
     * Called when the {@link GlTextureProcessor} will not produce further output frames belonging
     * to the current output stream.
     */
    default void onCurrentOutputStreamEnded() {}
  }

  /**
   * Listener for frame processing errors.
   *
   * <p>This listener can be called from any thread.
   */
  interface ErrorListener {
    /**
     * Called when an exception occurs during asynchronous frame processing.
     *
     * <p>If an error occurred, consuming and producing further frames will not work as expected and
     * the {@link GlTextureProcessor} should be released.
     */
    void onFrameProcessingError(FrameProcessingException e);
  }

  /** Sets the {@link InputListener}. */
  void setInputListener(InputListener inputListener);

  /** Sets the {@link OutputListener}. */
  void setOutputListener(OutputListener outputListener);

  /** Sets the {@link ErrorListener}. */
  void setErrorListener(ErrorListener errorListener);

  /**
   * Processes an input frame if possible.
   *
   * <p>The {@code GlTextureProcessor} owns the accepted frame until it calls {@link
   * InputListener#onInputFrameProcessed(TextureInfo)}. The caller should not overwrite or release
   * the texture before the {@code GlTextureProcessor} has finished processing it.
   *
   * <p>This method must only be called when the {@code GlTextureProcessor} can {@linkplain
   * InputListener#onReadyToAcceptInputFrame() accept an input frame}.
   *
   * @param inputTexture A {@link TextureInfo} describing the texture containing the input frame.
   * @param presentationTimeUs The presentation timestamp of the input frame, in microseconds.
   */
  void queueInputFrame(TextureInfo inputTexture, long presentationTimeUs);

  /**
   * Notifies the texture processor that the frame on the given output texture is no longer used and
   * can be overwritten.
   */
  void releaseOutputFrame(TextureInfo outputTexture);

  /**
   * Notifies the {@code GlTextureProcessor} that no further input frames belonging to the current
   * input stream will be queued.
   *
   * <p>Input frames that are queued after this method is called belong to a different input stream,
   * so presentation timestamps may reset to start from a smaller presentation timestamp than the
   * last frame of the previous input stream.
   */
  void signalEndOfCurrentInputStream();

  /**
   * Releases all resources.
   *
   * @throws FrameProcessingException If an error occurs while releasing resources.
   */
  void release() throws FrameProcessingException;
}

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

import android.util.Pair;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.effect.GlTextureProcessor.InputListener;
import com.google.android.exoplayer2.effect.GlTextureProcessor.OutputListener;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Connects a producing and a consuming {@link GlTextureProcessor} instance.
 *
 * <p>This listener should be set as {@link InputListener} on the consuming {@link
 * GlTextureProcessor} and as {@link OutputListener} on the producing {@link GlTextureProcessor}.
 */
/* package */ final class ChainingGlTextureProcessorListener
    implements GlTextureProcessor.InputListener, GlTextureProcessor.OutputListener {

  private final GlTextureProcessor producingGlTextureProcessor;
  private final GlTextureProcessor consumingGlTextureProcessor;
  private final FrameProcessingTaskExecutor frameProcessingTaskExecutor;

  @GuardedBy("this")
  private final Queue<Pair<TextureInfo, Long>> availableFrames;

  @GuardedBy("this")
  private int consumingGlTextureProcessorInputCapacity;

  /**
   * Creates a new instance.
   *
   * @param producingGlTextureProcessor The {@link GlTextureProcessor} for which this listener will
   *     be set as {@link OutputListener}.
   * @param consumingGlTextureProcessor The {@link GlTextureProcessor} for which this listener will
   *     be set as {@link InputListener}.
   * @param frameProcessingTaskExecutor The {@link FrameProcessingTaskExecutor} that is used for
   *     OpenGL calls. All calls to the producing/consuming {@link GlTextureProcessor} will be
   *     executed by the {@link FrameProcessingTaskExecutor}. The caller is responsible for
   *     releasing the {@link FrameProcessingTaskExecutor}.
   */
  public ChainingGlTextureProcessorListener(
      GlTextureProcessor producingGlTextureProcessor,
      GlTextureProcessor consumingGlTextureProcessor,
      FrameProcessingTaskExecutor frameProcessingTaskExecutor) {
    this.producingGlTextureProcessor = producingGlTextureProcessor;
    this.consumingGlTextureProcessor = consumingGlTextureProcessor;
    this.frameProcessingTaskExecutor = frameProcessingTaskExecutor;
    availableFrames = new ArrayDeque<>();
  }

  @Override
  public synchronized void onReadyToAcceptInputFrame() {
    @Nullable Pair<TextureInfo, Long> pendingFrame = availableFrames.poll();
    if (pendingFrame == null) {
      consumingGlTextureProcessorInputCapacity++;
      return;
    }

    long presentationTimeUs = pendingFrame.second;
    if (presentationTimeUs == C.TIME_END_OF_SOURCE) {
      frameProcessingTaskExecutor.submit(
          consumingGlTextureProcessor::signalEndOfCurrentInputStream);
    } else {
      frameProcessingTaskExecutor.submit(
          () ->
              consumingGlTextureProcessor.queueInputFrame(
                  /* inputTexture= */ pendingFrame.first, presentationTimeUs));
    }
  }

  @Override
  public void onInputFrameProcessed(TextureInfo inputTexture) {
    frameProcessingTaskExecutor.submit(
        () -> producingGlTextureProcessor.releaseOutputFrame(inputTexture));
  }

  @Override
  public synchronized void onOutputFrameAvailable(
      TextureInfo outputTexture, long presentationTimeUs) {
    if (consumingGlTextureProcessorInputCapacity > 0) {
      frameProcessingTaskExecutor.submit(
          () ->
              consumingGlTextureProcessor.queueInputFrame(
                  /* inputTexture= */ outputTexture, presentationTimeUs));
      consumingGlTextureProcessorInputCapacity--;
    } else {
      availableFrames.add(new Pair<>(outputTexture, presentationTimeUs));
    }
  }

  @Override
  public synchronized void onCurrentOutputStreamEnded() {
    if (!availableFrames.isEmpty()) {
      availableFrames.add(new Pair<>(TextureInfo.UNSET, C.TIME_END_OF_SOURCE));
    } else {
      frameProcessingTaskExecutor.submit(
          consumingGlTextureProcessor::signalEndOfCurrentInputStream);
    }
  }
}

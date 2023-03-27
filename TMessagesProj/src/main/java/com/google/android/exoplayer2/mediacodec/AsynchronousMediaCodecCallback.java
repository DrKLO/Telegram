/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.google.android.exoplayer2.mediacodec;

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayDeque;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link MediaCodec.Callback} that routes callbacks on a separate thread. */
@RequiresApi(23)
/* package */ final class AsynchronousMediaCodecCallback extends MediaCodec.Callback {
  private final Object lock;
  private final HandlerThread callbackThread;

  private @MonotonicNonNull Handler handler;

  @GuardedBy("lock")
  private final IntArrayQueue availableInputBuffers;

  @GuardedBy("lock")
  private final IntArrayQueue availableOutputBuffers;

  @GuardedBy("lock")
  private final ArrayDeque<MediaCodec.BufferInfo> bufferInfos;

  @GuardedBy("lock")
  private final ArrayDeque<MediaFormat> formats;

  @GuardedBy("lock")
  @Nullable
  private MediaFormat currentFormat;

  @GuardedBy("lock")
  @Nullable
  private MediaFormat pendingOutputFormat;

  @GuardedBy("lock")
  @Nullable
  private MediaCodec.CodecException mediaCodecException;

  @GuardedBy("lock")
  private long pendingFlushCount;

  @GuardedBy("lock")
  private boolean shutDown;

  @GuardedBy("lock")
  @Nullable
  private IllegalStateException internalException;

  /**
   * Creates a new instance.
   *
   * @param callbackThread The thread that will be used for routing the {@link MediaCodec}
   *     callbacks. The thread must not be started.
   */
  /* package */ AsynchronousMediaCodecCallback(HandlerThread callbackThread) {
    this.lock = new Object();
    this.callbackThread = callbackThread;
    this.availableInputBuffers = new IntArrayQueue();
    this.availableOutputBuffers = new IntArrayQueue();
    this.bufferInfos = new ArrayDeque<>();
    this.formats = new ArrayDeque<>();
  }

  /**
   * Sets the callback on {@code codec} and starts the background callback thread.
   *
   * <p>Make sure to call {@link #shutdown()} to stop the background thread and release its
   * resources.
   *
   * @see MediaCodec#setCallback(MediaCodec.Callback, Handler)
   */
  public void initialize(MediaCodec codec) {
    checkState(handler == null);

    callbackThread.start();
    Handler handler = new Handler(callbackThread.getLooper());
    codec.setCallback(this, handler);
    // Initialize this.handler at the very end ensuring the callback in not considered configured
    // if MediaCodec raises an exception.
    this.handler = handler;
  }

  /**
   * Shuts down this instance.
   *
   * <p>This method will stop the callback thread. After calling it, callbacks will no longer be
   * handled and dequeue methods will return {@link MediaCodec#INFO_TRY_AGAIN_LATER}.
   */
  public void shutdown() {
    synchronized (lock) {
      shutDown = true;
      callbackThread.quit();
      flushInternal();
    }
  }

  /**
   * Returns the next available input buffer index or {@link MediaCodec#INFO_TRY_AGAIN_LATER} if no
   * such buffer exists.
   */
  public int dequeueInputBufferIndex() {
    synchronized (lock) {
      if (isFlushingOrShutdown()) {
        return MediaCodec.INFO_TRY_AGAIN_LATER;
      } else {
        maybeThrowException();
        return availableInputBuffers.isEmpty()
            ? MediaCodec.INFO_TRY_AGAIN_LATER
            : availableInputBuffers.remove();
      }
    }
  }

  /**
   * Returns the next available output buffer index. If the next available output is a MediaFormat
   * change, it will return {@link MediaCodec#INFO_OUTPUT_FORMAT_CHANGED} and you should call {@link
   * #getOutputFormat()} to get the format. If there is no available output, this method will return
   * {@link MediaCodec#INFO_TRY_AGAIN_LATER}.
   */
  public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
    synchronized (lock) {
      if (isFlushingOrShutdown()) {
        return MediaCodec.INFO_TRY_AGAIN_LATER;
      } else {
        maybeThrowException();
        if (availableOutputBuffers.isEmpty()) {
          return MediaCodec.INFO_TRY_AGAIN_LATER;
        } else {
          int bufferIndex = availableOutputBuffers.remove();
          if (bufferIndex >= 0) {
            checkStateNotNull(currentFormat);
            MediaCodec.BufferInfo nextBufferInfo = bufferInfos.remove();
            bufferInfo.set(
                nextBufferInfo.offset,
                nextBufferInfo.size,
                nextBufferInfo.presentationTimeUs,
                nextBufferInfo.flags);
          } else if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            currentFormat = formats.remove();
          }
          return bufferIndex;
        }
      }
    }
  }

  /**
   * Returns the {@link MediaFormat} signalled by the underlying {@link MediaCodec}.
   *
   * <p>Call this <b>after</b> {@link #dequeueOutputBufferIndex} returned {@link
   * MediaCodec#INFO_OUTPUT_FORMAT_CHANGED}.
   *
   * @throws IllegalStateException If called before {@link #dequeueOutputBufferIndex} has returned
   *     {@link MediaCodec#INFO_OUTPUT_FORMAT_CHANGED}.
   */
  public MediaFormat getOutputFormat() {
    synchronized (lock) {
      if (currentFormat == null) {
        throw new IllegalStateException();
      }
      return currentFormat;
    }
  }

  /**
   * Initiates a flush asynchronously, which will be completed on the callback thread. When the
   * flush is complete, it will trigger {@code onFlushCompleted} from the callback thread.
   */
  public void flush() {
    synchronized (lock) {
      ++pendingFlushCount;
      Util.castNonNull(handler).post(this::onFlushCompleted);
    }
  }

  // Called from the callback thread.

  @Override
  public void onInputBufferAvailable(MediaCodec codec, int index) {
    synchronized (lock) {
      availableInputBuffers.add(index);
    }
  }

  @Override
  public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
    synchronized (lock) {
      if (pendingOutputFormat != null) {
        addOutputFormat(pendingOutputFormat);
        pendingOutputFormat = null;
      }
      availableOutputBuffers.add(index);
      bufferInfos.add(info);
    }
  }

  @Override
  public void onError(MediaCodec codec, MediaCodec.CodecException e) {
    synchronized (lock) {
      mediaCodecException = e;
    }
  }

  @Override
  public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
    synchronized (lock) {
      addOutputFormat(format);
      pendingOutputFormat = null;
    }
  }

  private void onFlushCompleted() {
    synchronized (lock) {
      if (shutDown) {
        return;
      }

      --pendingFlushCount;
      if (pendingFlushCount > 0) {
        // Another flush() has been called.
        return;
      } else if (pendingFlushCount < 0) {
        // This should never happen.
        setInternalException(new IllegalStateException());
        return;
      }
      flushInternal();
    }
  }

  /** Flushes all available input and output buffers and any error that was previously set. */
  @GuardedBy("lock")
  private void flushInternal() {
    if (!formats.isEmpty()) {
      pendingOutputFormat = formats.getLast();
    }
    // else, pendingOutputFormat may already be non-null following a previous flush, and remains
    // set in this case.

    availableInputBuffers.clear();
    availableOutputBuffers.clear();
    bufferInfos.clear();
    formats.clear();
    mediaCodecException = null;
  }

  @GuardedBy("lock")
  private boolean isFlushingOrShutdown() {
    return pendingFlushCount > 0 || shutDown;
  }

  @GuardedBy("lock")
  private void addOutputFormat(MediaFormat mediaFormat) {
    availableOutputBuffers.add(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    formats.add(mediaFormat);
  }

  @GuardedBy("lock")
  private void maybeThrowException() {
    maybeThrowInternalException();
    maybeThrowMediaCodecException();
  }

  @GuardedBy("lock")
  private void maybeThrowInternalException() {
    if (internalException != null) {
      IllegalStateException e = internalException;
      internalException = null;
      throw e;
    }
  }

  @GuardedBy("lock")
  private void maybeThrowMediaCodecException() {
    if (mediaCodecException != null) {
      MediaCodec.CodecException codecException = mediaCodecException;
      mediaCodecException = null;
      throw codecException;
    }
  }

  private void setInternalException(IllegalStateException e) {
    synchronized (lock) {
      internalException = e;
    }
  }
}

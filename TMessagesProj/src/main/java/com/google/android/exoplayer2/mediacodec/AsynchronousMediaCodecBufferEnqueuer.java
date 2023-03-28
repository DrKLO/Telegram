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

import static androidx.annotation.VisibleForTesting.NONE;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.media.MediaCodec;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Performs {@link MediaCodec} input buffer queueing on a background thread.
 *
 * <p>The implementation of this class assumes that its public methods will be called from the same
 * thread.
 */
@RequiresApi(23)
class AsynchronousMediaCodecBufferEnqueuer {

  private static final int MSG_QUEUE_INPUT_BUFFER = 0;
  private static final int MSG_QUEUE_SECURE_INPUT_BUFFER = 1;
  private static final int MSG_OPEN_CV = 2;

  @GuardedBy("MESSAGE_PARAMS_INSTANCE_POOL")
  private static final ArrayDeque<MessageParams> MESSAGE_PARAMS_INSTANCE_POOL = new ArrayDeque<>();

  private static final Object QUEUE_SECURE_LOCK = new Object();

  private final MediaCodec codec;
  private final HandlerThread handlerThread;
  private @MonotonicNonNull Handler handler;
  private final AtomicReference<@NullableType RuntimeException> pendingRuntimeException;
  private final ConditionVariable conditionVariable;
  private boolean started;

  /**
   * Creates a new instance that submits input buffers on the specified {@link MediaCodec}.
   *
   * @param codec The {@link MediaCodec} to submit input buffers to.
   * @param queueingThread The {@link HandlerThread} to use for queueing buffers.
   */
  public AsynchronousMediaCodecBufferEnqueuer(MediaCodec codec, HandlerThread queueingThread) {
    this(codec, queueingThread, /* conditionVariable= */ new ConditionVariable());
  }

  @VisibleForTesting
  /* package */ AsynchronousMediaCodecBufferEnqueuer(
      MediaCodec codec, HandlerThread handlerThread, ConditionVariable conditionVariable) {
    this.codec = codec;
    this.handlerThread = handlerThread;
    this.conditionVariable = conditionVariable;
    pendingRuntimeException = new AtomicReference<>();
  }

  /**
   * Starts this instance.
   *
   * <p>Call this method after creating an instance and before queueing input buffers.
   */
  public void start() {
    if (!started) {
      handlerThread.start();
      handler =
          new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
              doHandleMessage(msg);
            }
          };
      started = true;
    }
  }

  /**
   * Submits an input buffer for decoding.
   *
   * @see android.media.MediaCodec#queueInputBuffer
   */
  public void queueInputBuffer(
      int index, int offset, int size, long presentationTimeUs, int flags) {
    maybeThrowException();
    MessageParams messageParams = getMessageParams();
    messageParams.setQueueParams(index, offset, size, presentationTimeUs, flags);
    Message message = castNonNull(handler).obtainMessage(MSG_QUEUE_INPUT_BUFFER, messageParams);
    message.sendToTarget();
  }

  /**
   * Submits an input buffer that potentially contains encrypted data for decoding.
   *
   * <p>Note: This method behaves as {@link MediaCodec#queueSecureInputBuffer} with the difference
   * that {@code info} is of type {@link CryptoInfo} and not {@link
   * android.media.MediaCodec.CryptoInfo}.
   *
   * @see android.media.MediaCodec#queueSecureInputBuffer
   */
  public void queueSecureInputBuffer(
      int index, int offset, CryptoInfo info, long presentationTimeUs, int flags) {
    maybeThrowException();
    MessageParams messageParams = getMessageParams();
    messageParams.setQueueParams(index, offset, /* size= */ 0, presentationTimeUs, flags);
    copy(info, messageParams.cryptoInfo);
    Message message =
        castNonNull(handler).obtainMessage(MSG_QUEUE_SECURE_INPUT_BUFFER, messageParams);
    message.sendToTarget();
  }

  /** Flushes the instance. */
  public void flush() {
    if (started) {
      try {
        flushHandlerThread();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        // The playback thread should not be interrupted. Raising this as an
        // IllegalStateException.
        throw new IllegalStateException(e);
      }
    }
  }

  /** Shuts down the instance. Make sure to call this method to release its internal resources. */
  public void shutdown() {
    if (started) {
      flush();
      handlerThread.quit();
    }
    started = false;
  }

  /** Blocks the current thread until all input buffers pending queueing are submitted. */
  public void waitUntilQueueingComplete() throws InterruptedException {
    blockUntilHandlerThreadIsIdle();
  }

  private void maybeThrowException() {
    @Nullable RuntimeException exception = pendingRuntimeException.getAndSet(null);
    if (exception != null) {
      throw exception;
    }
  }

  /**
   * Empties all tasks enqueued on the {@link #handlerThread} via the {@link #handler}. This method
   * blocks until the {@link #handlerThread} is idle.
   */
  private void flushHandlerThread() throws InterruptedException {
    checkNotNull(this.handler).removeCallbacksAndMessages(null);
    blockUntilHandlerThreadIsIdle();
  }

  private void blockUntilHandlerThreadIsIdle() throws InterruptedException {
    conditionVariable.close();
    checkNotNull(handler).obtainMessage(MSG_OPEN_CV).sendToTarget();
    conditionVariable.block();
  }

  @VisibleForTesting(otherwise = NONE)
  /* package */ void setPendingRuntimeException(RuntimeException exception) {
    pendingRuntimeException.set(exception);
  }

  // Called from the handler thread

  private void doHandleMessage(Message msg) {
    @Nullable MessageParams params = null;
    switch (msg.what) {
      case MSG_QUEUE_INPUT_BUFFER:
        params = (MessageParams) msg.obj;
        doQueueInputBuffer(
            params.index, params.offset, params.size, params.presentationTimeUs, params.flags);
        break;
      case MSG_QUEUE_SECURE_INPUT_BUFFER:
        params = (MessageParams) msg.obj;
        doQueueSecureInputBuffer(
            params.index,
            params.offset,
            params.cryptoInfo,
            params.presentationTimeUs,
            params.flags);
        break;
      case MSG_OPEN_CV:
        conditionVariable.open();
        break;
      default:
        pendingRuntimeException.compareAndSet(
            null, new IllegalStateException(String.valueOf(msg.what)));
    }
    if (params != null) {
      recycleMessageParams(params);
    }
  }

  private void doQueueInputBuffer(
      int index, int offset, int size, long presentationTimeUs, int flag) {
    try {
      codec.queueInputBuffer(index, offset, size, presentationTimeUs, flag);
    } catch (RuntimeException e) {
      pendingRuntimeException.compareAndSet(null, e);
    }
  }

  private void doQueueSecureInputBuffer(
      int index, int offset, MediaCodec.CryptoInfo info, long presentationTimeUs, int flags) {
    try {
      // Synchronize calls to MediaCodec.queueSecureInputBuffer() to avoid race conditions inside
      // the crypto module when audio and video are sharing the same DRM session
      // (see [Internal: b/149908061]).
      synchronized (QUEUE_SECURE_LOCK) {
        codec.queueSecureInputBuffer(index, offset, info, presentationTimeUs, flags);
      }
    } catch (RuntimeException e) {
      pendingRuntimeException.compareAndSet(null, e);
    }
  }

  private static MessageParams getMessageParams() {
    synchronized (MESSAGE_PARAMS_INSTANCE_POOL) {
      if (MESSAGE_PARAMS_INSTANCE_POOL.isEmpty()) {
        return new MessageParams();
      } else {
        return MESSAGE_PARAMS_INSTANCE_POOL.removeFirst();
      }
    }
  }

  private static void recycleMessageParams(MessageParams params) {
    synchronized (MESSAGE_PARAMS_INSTANCE_POOL) {
      MESSAGE_PARAMS_INSTANCE_POOL.add(params);
    }
  }

  /** Parameters for queue input buffer and queue secure input buffer tasks. */
  private static class MessageParams {
    public int index;
    public int offset;
    public int size;
    public final MediaCodec.CryptoInfo cryptoInfo;
    public long presentationTimeUs;
    public int flags;

    MessageParams() {
      cryptoInfo = new MediaCodec.CryptoInfo();
    }

    /** Convenience method for setting the queueing parameters. */
    public void setQueueParams(
        int index, int offset, int size, long presentationTimeUs, int flags) {
      this.index = index;
      this.offset = offset;
      this.size = size;
      this.presentationTimeUs = presentationTimeUs;
      this.flags = flags;
    }
  }

  /** Performs a deep copy of {@code cryptoInfo} to {@code frameworkCryptoInfo}. */
  private static void copy(
      CryptoInfo cryptoInfo, android.media.MediaCodec.CryptoInfo frameworkCryptoInfo) {
    // Update frameworkCryptoInfo fields directly because CryptoInfo.set performs an unnecessary
    // object allocation on Android N.
    frameworkCryptoInfo.numSubSamples = cryptoInfo.numSubSamples;
    frameworkCryptoInfo.numBytesOfClearData =
        copy(cryptoInfo.numBytesOfClearData, frameworkCryptoInfo.numBytesOfClearData);
    frameworkCryptoInfo.numBytesOfEncryptedData =
        copy(cryptoInfo.numBytesOfEncryptedData, frameworkCryptoInfo.numBytesOfEncryptedData);
    frameworkCryptoInfo.key = checkNotNull(copy(cryptoInfo.key, frameworkCryptoInfo.key));
    frameworkCryptoInfo.iv = checkNotNull(copy(cryptoInfo.iv, frameworkCryptoInfo.iv));
    frameworkCryptoInfo.mode = cryptoInfo.mode;
    if (Util.SDK_INT >= 24) {
      android.media.MediaCodec.CryptoInfo.Pattern pattern =
          new android.media.MediaCodec.CryptoInfo.Pattern(
              cryptoInfo.encryptedBlocks, cryptoInfo.clearBlocks);
      frameworkCryptoInfo.setPattern(pattern);
    }
  }

  /**
   * Copies {@code src}, reusing {@code dst} if it's at least as long as {@code src}.
   *
   * @param src The source array.
   * @param dst The destination array, which will be reused if it's at least as long as {@code src}.
   * @return The copy, which may be {@code dst} if it was reused.
   */
  @Nullable
  private static int[] copy(@Nullable int[] src, @Nullable int[] dst) {
    if (src == null) {
      return dst;
    }

    if (dst == null || dst.length < src.length) {
      return Arrays.copyOf(src, src.length);
    } else {
      System.arraycopy(src, 0, dst, 0, src.length);
      return dst;
    }
  }

  /**
   * Copies {@code src}, reusing {@code dst} if it's at least as long as {@code src}.
   *
   * @param src The source array.
   * @param dst The destination array, which will be reused if it's at least as long as {@code src}.
   * @return The copy, which may be {@code dst} if it was reused.
   */
  @Nullable
  private static byte[] copy(@Nullable byte[] src, @Nullable byte[] dst) {
    if (src == null) {
      return dst;
    }

    if (dst == null || dst.length < src.length) {
      return Arrays.copyOf(src, src.length);
    } else {
      System.arraycopy(src, 0, dst, 0, src.length);
      return dst;
    }
  }
}

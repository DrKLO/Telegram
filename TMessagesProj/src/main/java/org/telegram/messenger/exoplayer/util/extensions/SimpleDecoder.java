/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.util.extensions;

import org.telegram.messenger.exoplayer.util.Assertions;
import java.util.LinkedList;

/**
 * Base class for {@link Decoder}s that use their own decode thread.
 */
public abstract class SimpleDecoder<I extends InputBuffer, O extends OutputBuffer,
    E extends Exception> extends Thread implements Decoder<I, O, E> {

  /**
   * Listener for {@link SimpleDecoder} events.
   */
  public interface EventListener<E> {

    /**
     * Invoked when the decoder encounters an error.
     *
     * @param e The corresponding exception.
     */
    void onDecoderError(E e);

  }

  private final Object lock;
  private final LinkedList<I> queuedInputBuffers;
  private final LinkedList<O> queuedOutputBuffers;
  private final I[] availableInputBuffers;
  private final O[] availableOutputBuffers;

  private int availableInputBufferCount;
  private int availableOutputBufferCount;
  private I dequeuedInputBuffer;

  private E exception;
  private boolean flushed;
  private boolean released;

  /**
   * @param inputBuffers An array of nulls that will be used to store references to input buffers.
   * @param outputBuffers An array of nulls that will be used to store references to output buffers.
   */
  protected SimpleDecoder(I[] inputBuffers, O[] outputBuffers) {
    lock = new Object();
    queuedInputBuffers = new LinkedList<>();
    queuedOutputBuffers = new LinkedList<>();
    availableInputBuffers = inputBuffers;
    availableInputBufferCount = inputBuffers.length;
    for (int i = 0; i < availableInputBufferCount; i++) {
      availableInputBuffers[i] = createInputBuffer();
    }
    availableOutputBuffers = outputBuffers;
    availableOutputBufferCount = outputBuffers.length;
    for (int i = 0; i < availableOutputBufferCount; i++) {
      availableOutputBuffers[i] = createOutputBuffer();
    }
  }

  /**
   * Sets the initial size of each input buffer.
   * <p>
   * This method should only be called before the decoder is used (i.e. before the first call to
   * {@link #dequeueInputBuffer()}.
   *
   * @param size The required input buffer size.
   */
  protected final void setInitialInputBufferSize(int size) {
    Assertions.checkState(availableInputBufferCount == availableInputBuffers.length);
    for (int i = 0; i < availableInputBuffers.length; i++) {
      availableInputBuffers[i].sampleHolder.ensureSpaceForWrite(size);
    }
  }

  @Override
  public final I dequeueInputBuffer() throws E {
    synchronized (lock) {
      maybeThrowException();
      Assertions.checkState(dequeuedInputBuffer == null);
      if (availableInputBufferCount == 0) {
        return null;
      }
      I inputBuffer = availableInputBuffers[--availableInputBufferCount];
      inputBuffer.reset();
      dequeuedInputBuffer = inputBuffer;
      return inputBuffer;
    }
  }

  @Override
  public final void queueInputBuffer(I inputBuffer) throws E {
    synchronized (lock) {
      maybeThrowException();
      Assertions.checkArgument(inputBuffer == dequeuedInputBuffer);
      queuedInputBuffers.addLast(inputBuffer);
      maybeNotifyDecodeLoop();
      dequeuedInputBuffer = null;
    }
  }

  @Override
  public final O dequeueOutputBuffer() throws E {
    synchronized (lock) {
      maybeThrowException();
      if (queuedOutputBuffers.isEmpty()) {
        return null;
      }
      return queuedOutputBuffers.removeFirst();
    }
  }

  /**
   * Releases an output buffer back to the decoder.
   *
   * @param outputBuffer The output buffer being released.
   */
  protected void releaseOutputBuffer(O outputBuffer) {
    synchronized (lock) {
      availableOutputBuffers[availableOutputBufferCount++] = outputBuffer;
      maybeNotifyDecodeLoop();
    }
  }

  @Override
  public final void flush() {
    synchronized (lock) {
      flushed = true;
      if (dequeuedInputBuffer != null) {
        availableInputBuffers[availableInputBufferCount++] = dequeuedInputBuffer;
        dequeuedInputBuffer = null;
      }
      while (!queuedInputBuffers.isEmpty()) {
        availableInputBuffers[availableInputBufferCount++] = queuedInputBuffers.removeFirst();
      }
      while (!queuedOutputBuffers.isEmpty()) {
        availableOutputBuffers[availableOutputBufferCount++] = queuedOutputBuffers.removeFirst();
      }
    }
  }

  @Override
  public void release() {
    synchronized (lock) {
      released = true;
      lock.notify();
    }
    try {
      join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Throws a decode exception, if there is one.
   *
   * @throws E The decode exception.
   */
  private void maybeThrowException() throws E {
    if (exception != null) {
      throw exception;
    }
  }

  /**
   * Notifies the decode loop if there exists a queued input buffer and an available output buffer
   * to decode into.
   * <p>
   * Should only be called whilst synchronized on the lock object.
   */
  private void maybeNotifyDecodeLoop() {
    if (canDecodeBuffer()) {
      lock.notify();
    }
  }

  @Override
  public final void run() {
    try {
      while (decode()) {
        // Do nothing.
      }
    } catch (InterruptedException e) {
      // Not expected.
      throw new IllegalStateException(e);
    }
  }

  private boolean decode() throws InterruptedException {
    I inputBuffer;
    O outputBuffer;
    boolean resetDecoder;

    // Wait until we have an input buffer to decode, and an output buffer to decode into.
    synchronized (lock) {
      while (!released && !canDecodeBuffer()) {
        lock.wait();
      }
      if (released) {
        return false;
      }
      inputBuffer = queuedInputBuffers.removeFirst();
      outputBuffer = availableOutputBuffers[--availableOutputBufferCount];
      resetDecoder = flushed;
      flushed = false;
    }

    outputBuffer.reset();
    if (inputBuffer.getFlag(Buffer.FLAG_END_OF_STREAM)) {
      outputBuffer.setFlag(Buffer.FLAG_END_OF_STREAM);
    } else {
      if (inputBuffer.getFlag(Buffer.FLAG_DECODE_ONLY)) {
        outputBuffer.setFlag(Buffer.FLAG_DECODE_ONLY);
      }
      exception = decode(inputBuffer, outputBuffer, resetDecoder);
      if (exception != null) {
        // Memory barrier to ensure that the decoder exception is visible from the playback thread.
        synchronized (lock) {}
        return false;
      }
    }

    synchronized (lock) {
      if (flushed || outputBuffer.getFlag(Buffer.FLAG_DECODE_ONLY)) {
        // If a flush occurred while decoding or the buffer was only for decoding (not presentation)
        // then make the output buffer available again rather than queueing it to be consumed.
        availableOutputBuffers[availableOutputBufferCount++] = outputBuffer;
      } else {
        // Queue the decoded output buffer to be consumed.
        queuedOutputBuffers.addLast(outputBuffer);
      }
      // Make the input buffer available again.
      availableInputBuffers[availableInputBufferCount++] = inputBuffer;
    }

    return true;
  }

  private boolean canDecodeBuffer() {
    return !queuedInputBuffers.isEmpty() && availableOutputBufferCount > 0;
  }

  /**
   * Creates a new input buffer.
   */
  protected abstract I createInputBuffer();

  /**
   * Creates a new output buffer.
   */
  protected abstract O createOutputBuffer();

  /**
   * Decodes the {@code inputBuffer} and stores any decoded output in {@code outputBuffer}.
   *
   * @param inputBuffer The buffer to decode.
   * @param outputBuffer The output buffer to store decoded data. The flag
   *     {@link Buffer#FLAG_DECODE_ONLY} will be set if the same flag is set on {@code inputBuffer},
   *     but the decoder may set/unset the flag if required. If the flag is set after this method
   *     returns, any output should not be presented.
   * @param reset True if the decoder must be reset before decoding.
   * @return A decoder exception if an error occurred, or null if decoding was successful.
   */
  protected abstract E decode(I inputBuffer, O outputBuffer, boolean reset);

}

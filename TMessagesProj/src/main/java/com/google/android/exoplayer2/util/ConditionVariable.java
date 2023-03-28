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
package com.google.android.exoplayer2.util;

/**
 * An interruptible condition variable. This class provides a number of benefits over {@link
 * android.os.ConditionVariable}:
 *
 * <ul>
 *   <li>Consistent use of ({@link Clock#elapsedRealtime()} for timing {@link #block(long)} timeout
 *       intervals. {@link android.os.ConditionVariable} used {@link System#currentTimeMillis()}
 *       prior to Android 10, which is not a correct clock to use for interval timing because it's
 *       not guaranteed to be monotonic.
 *   <li>Support for injecting a custom {@link Clock}.
 *   <li>The ability to query the variable's current state, by calling {@link #isOpen()}.
 *   <li>{@link #open()} and {@link #close()} return whether they changed the variable's state.
 * </ul>
 */
public class ConditionVariable {

  private final Clock clock;
  private boolean isOpen;

  /** Creates an instance using {@link Clock#DEFAULT}. */
  public ConditionVariable() {
    this(Clock.DEFAULT);
  }

  /**
   * Creates an instance, which starts closed.
   *
   * @param clock The {@link Clock} whose {@link Clock#elapsedRealtime()} method is used to
   *     determine when {@link #block(long)} should time out.
   */
  public ConditionVariable(Clock clock) {
    this.clock = clock;
  }

  /**
   * Opens the condition and releases all threads that are blocked.
   *
   * @return True if the condition variable was opened. False if it was already open.
   */
  public synchronized boolean open() {
    if (isOpen) {
      return false;
    }
    isOpen = true;
    notifyAll();
    return true;
  }

  /**
   * Closes the condition.
   *
   * @return True if the condition variable was closed. False if it was already closed.
   */
  public synchronized boolean close() {
    boolean wasOpen = isOpen;
    isOpen = false;
    return wasOpen;
  }

  /**
   * Blocks until the condition is opened.
   *
   * @throws InterruptedException If the thread is interrupted.
   */
  public synchronized void block() throws InterruptedException {
    while (!isOpen) {
      wait();
    }
  }

  /**
   * Blocks until the condition is opened or until {@code timeoutMs} have passed.
   *
   * @param timeoutMs The maximum time to wait in milliseconds. If {@code timeoutMs <= 0} then the
   *     call will return immediately without blocking.
   * @return True if the condition was opened, false if the call returns because of the timeout.
   * @throws InterruptedException If the thread is interrupted.
   */
  public synchronized boolean block(long timeoutMs) throws InterruptedException {
    if (timeoutMs <= 0) {
      return isOpen;
    }
    long nowMs = clock.elapsedRealtime();
    long endMs = nowMs + timeoutMs;
    if (endMs < nowMs) {
      // timeoutMs is large enough for (nowMs + timeoutMs) to rollover. Block indefinitely.
      block();
    } else {
      while (!isOpen && nowMs < endMs) {
        wait(endMs - nowMs);
        nowMs = clock.elapsedRealtime();
      }
    }
    return isOpen;
  }

  /**
   * Blocks until the condition is open. Unlike {@link #block}, this method will continue to block
   * if the calling thread is interrupted. If the calling thread was interrupted then its {@link
   * Thread#isInterrupted() interrupted status} will be set when the method returns.
   */
  public synchronized void blockUninterruptible() {
    boolean wasInterrupted = false;
    while (!isOpen) {
      try {
        wait();
      } catch (InterruptedException e) {
        wasInterrupted = true;
      }
    }
    if (wasInterrupted) {
      // Restore the interrupted status.
      Thread.currentThread().interrupt();
    }
  }

  /** Returns whether the condition is opened. */
  public synchronized boolean isOpen() {
    return isOpen;
  }
}

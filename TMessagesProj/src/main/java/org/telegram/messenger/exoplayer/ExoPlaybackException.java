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
package org.telegram.messenger.exoplayer;

/**
 * Thrown when a non-recoverable playback failure occurs.
 * <p>
 * Where possible, the cause returned by {@link #getCause()} will indicate the reason for failure.
 */
public final class ExoPlaybackException extends Exception {

  /**
   * True if the cause (i.e. the {@link Throwable} returned by {@link #getCause()}) was only caught
   * by a fail-safe at the top level of the player. False otherwise.
   */
  public final boolean caughtAtTopLevel;

  public ExoPlaybackException(String message) {
    super(message);
    caughtAtTopLevel = false;
  }

  public ExoPlaybackException(Throwable cause) {
    super(cause);
    caughtAtTopLevel = false;
  }

  public ExoPlaybackException(String message, Throwable cause) {
    super(message, cause);
    caughtAtTopLevel = false;
  }

  /* package */ ExoPlaybackException(Throwable cause, boolean caughtAtTopLevel) {
    super(cause);
    this.caughtAtTopLevel = caughtAtTopLevel;
  }

}

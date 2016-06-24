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

/**
 * Base class for {@link Decoder} buffers with flags.
 */
public abstract class Buffer {

  /**
   * Flag for empty input/output buffers that signal that the end of the stream was reached.
   */
  public static final int FLAG_END_OF_STREAM = 1;
  /**
   * Flag for non-empty input/output buffers that should only be decoded (not rendered).
   */
  public static final int FLAG_DECODE_ONLY = 2;

  private int flags;

  public void reset() {
    flags = 0;
  }

  public final void setFlag(int flag) {
    flags |= flag;
  }

  public final boolean getFlag(int flag) {
    return (flags & flag) == flag;
  }

}

/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2;

import androidx.annotation.Nullable;

/** The configuration of a {@link Renderer}. */
public final class RendererConfiguration {

  /** The default configuration. */
  public static final RendererConfiguration DEFAULT =
      new RendererConfiguration(/* tunneling= */ false);

  /** Whether to enable tunneling. */
  public final boolean tunneling;

  /**
   * @param tunneling Whether to enable tunneling.
   */
  public RendererConfiguration(boolean tunneling) {
    this.tunneling = tunneling;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    RendererConfiguration other = (RendererConfiguration) obj;
    return tunneling == other.tunneling;
  }

  @Override
  public int hashCode() {
    return tunneling ? 0 : 1;
  }
}

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

import com.google.android.exoplayer2.C;

/** Contains information describing an OpenGL texture. */
public final class TextureInfo {

  /** A {@link TextureInfo} instance with all fields unset. */
  public static final TextureInfo UNSET =
      new TextureInfo(C.INDEX_UNSET, C.INDEX_UNSET, C.LENGTH_UNSET, C.LENGTH_UNSET);

  /** The OpenGL texture identifier. */
  public final int texId;
  /** Identifier of a framebuffer object associated with the texture. */
  public final int fboId;
  /** The width of the texture, in pixels. */
  public final int width;
  /** The height of the texture, in pixels. */
  public final int height;

  /**
   * Creates a new instance.
   *
   * @param texId The OpenGL texture identifier.
   * @param fboId Identifier of a framebuffer object associated with the texture.
   * @param width The width of the texture, in pixels.
   * @param height The height of the texture, in pixels.
   */
  public TextureInfo(int texId, int fboId, int width, int height) {
    this.texId = texId;
    this.fboId = fboId;
    this.width = width;
    this.height = height;
  }
}

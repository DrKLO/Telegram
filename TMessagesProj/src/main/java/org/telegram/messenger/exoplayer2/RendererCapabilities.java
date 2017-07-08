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
package org.telegram.messenger.exoplayer2;

import org.telegram.messenger.exoplayer2.util.MimeTypes;

/**
 * Defines the capabilities of a {@link Renderer}.
 */
public interface RendererCapabilities {

  /**
   * A mask to apply to the result of {@link #supportsFormat(Format)} to obtain one of
   * {@link #FORMAT_HANDLED}, {@link #FORMAT_EXCEEDS_CAPABILITIES},
   * {@link #FORMAT_UNSUPPORTED_SUBTYPE} and {@link #FORMAT_UNSUPPORTED_TYPE}.
   */
  int FORMAT_SUPPORT_MASK = 0b11;
  /**
   * The {@link Renderer} is capable of rendering the format.
   */
  int FORMAT_HANDLED = 0b11;
  /**
   * The {@link Renderer} is capable of rendering formats with the same mime type, but the
   * properties of the format exceed the renderer's capability.
   * <p>
   * Example: The {@link Renderer} is capable of rendering H264 and the format's mime type is
   * {@link MimeTypes#VIDEO_H264}, but the format's resolution exceeds the maximum limit supported
   * by the underlying H264 decoder.
   */
  int FORMAT_EXCEEDS_CAPABILITIES = 0b10;
  /**
   * The {@link Renderer} is a general purpose renderer for formats of the same top-level type,
   * but is not capable of rendering the format or any other format with the same mime type because
   * the sub-type is not supported.
   * <p>
   * Example: The {@link Renderer} is a general purpose audio renderer and the format's
   * mime type matches audio/[subtype], but there does not exist a suitable decoder for [subtype].
   */
  int FORMAT_UNSUPPORTED_SUBTYPE = 0b01;
  /**
   * The {@link Renderer} is not capable of rendering the format, either because it does not
   * support the format's top-level type, or because it's a specialized renderer for a different
   * mime type.
   * <p>
   * Example: The {@link Renderer} is a general purpose video renderer, but the format has an
   * audio mime type.
   */
  int FORMAT_UNSUPPORTED_TYPE = 0b00;

  /**
   * A mask to apply to the result of {@link #supportsFormat(Format)} to obtain one of
   * {@link #ADAPTIVE_SEAMLESS}, {@link #ADAPTIVE_NOT_SEAMLESS} and {@link #ADAPTIVE_NOT_SUPPORTED}.
   */
  int ADAPTIVE_SUPPORT_MASK = 0b1100;
  /**
   * The {@link Renderer} can seamlessly adapt between formats.
   */
  int ADAPTIVE_SEAMLESS = 0b1000;
  /**
   * The {@link Renderer} can adapt between formats, but may suffer a brief discontinuity
   * (~50-100ms) when adaptation occurs.
   */
  int ADAPTIVE_NOT_SEAMLESS = 0b0100;
  /**
   * The {@link Renderer} does not support adaptation between formats.
   */
  int ADAPTIVE_NOT_SUPPORTED = 0b0000;

  /**
   * A mask to apply to the result of {@link #supportsFormat(Format)} to obtain one of
   * {@link #TUNNELING_SUPPORTED} and {@link #TUNNELING_NOT_SUPPORTED}.
   */
  int TUNNELING_SUPPORT_MASK = 0b10000;
  /**
   * The {@link Renderer} supports tunneled output.
   */
  int TUNNELING_SUPPORTED = 0b10000;
  /**
   * The {@link Renderer} does not support tunneled output.
   */
  int TUNNELING_NOT_SUPPORTED = 0b00000;

  /**
   * Returns the track type that the {@link Renderer} handles. For example, a video renderer will
   * return {@link C#TRACK_TYPE_VIDEO}, an audio renderer will return {@link C#TRACK_TYPE_AUDIO}, a
   * text renderer will return {@link C#TRACK_TYPE_TEXT}, and so on.
   *
   * @see Renderer#getTrackType()
   * @return One of the {@code TRACK_TYPE_*} constants defined in {@link C}.
   */
  int getTrackType();

  /**
   * Returns the extent to which the {@link Renderer} supports a given format. The returned value is
   * the bitwise OR of three properties:
   * <ul>
   * <li>The level of support for the format itself. One of {@link #FORMAT_HANDLED},
   * {@link #FORMAT_EXCEEDS_CAPABILITIES}, {@link #FORMAT_UNSUPPORTED_SUBTYPE} and
   * {@link #FORMAT_UNSUPPORTED_TYPE}.</li>
   * <li>The level of support for adapting from the format to another format of the same mime type.
   * One of {@link #ADAPTIVE_SEAMLESS}, {@link #ADAPTIVE_NOT_SEAMLESS} and
   * {@link #ADAPTIVE_NOT_SUPPORTED}.</li>
   * <li>The level of support for tunneling. One of {@link #TUNNELING_SUPPORTED} and
   * {@link #TUNNELING_NOT_SUPPORTED}.</li>
   * </ul>
   * The individual properties can be retrieved by performing a bitwise AND with
   * {@link #FORMAT_SUPPORT_MASK}, {@link #ADAPTIVE_SUPPORT_MASK} and
   * {@link #TUNNELING_SUPPORT_MASK} respectively.
   *
   * @param format The format.
   * @return The extent to which the renderer is capable of supporting the given format.
   * @throws ExoPlaybackException If an error occurs.
   */
  int supportsFormat(Format format) throws ExoPlaybackException;

  /**
   * Returns the extent to which the {@link Renderer} supports adapting between supported formats
   * that have different mime types.
   *
   * @return The extent to which the renderer supports adapting between supported formats that have
   *     different mime types. One of {@link #ADAPTIVE_SEAMLESS}, {@link #ADAPTIVE_NOT_SEAMLESS} and
   *     {@link #ADAPTIVE_NOT_SUPPORTED}.
   * @throws ExoPlaybackException If an error occurs.
   */
  int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException;

}

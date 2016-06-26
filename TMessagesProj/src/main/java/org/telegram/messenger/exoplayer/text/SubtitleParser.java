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
package org.telegram.messenger.exoplayer.text;

import org.telegram.messenger.exoplayer.ParserException;

/**
 * Parses {@link Subtitle}s from a byte array.
 */
public interface SubtitleParser {

  /**
   * Checks whether the parser supports a given subtitle mime type.
   *
   * @param mimeType A subtitle mime type.
   * @return Whether the mime type is supported.
   */
  public boolean canParse(String mimeType);

  /**
   * Parses a {@link Subtitle} from the provided {@code byte[]}.
   *
   * @param bytes The array holding the subtitle data.
   * @param offset The offset of the subtitle data in bytes.
   * @param length The length of the subtitle data in bytes.
   * @return A parsed representation of the subtitle.
   * @throws ParserException If a problem occurred parsing the subtitle data.
   */
  public Subtitle parse(byte[] bytes, int offset, int length) throws ParserException;

}

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
package org.telegram.messenger.exoplayer.metadata;

import org.telegram.messenger.exoplayer.ParserException;

/**
 * Parses metadata from binary data.
 *
 * @param <T> The type of the metadata.
 */
public interface MetadataParser<T> {

  /**
   * Checks whether the parser supports a given mime type.
   *
   * @param mimeType A metadata mime type.
   * @return Whether the mime type is supported.
   */
  public boolean canParse(String mimeType);

  /**
   * Parses metadata objects of type <T> from the provided binary data.
   *
   * @param data The raw binary data from which to parse the metadata.
   * @param size The size of the input data.
   * @return @return A parsed metadata object of type <T>.
   * @throws ParserException If a problem occurred parsing the data.
   */
  public T parse(byte[] data, int size) throws ParserException;

}

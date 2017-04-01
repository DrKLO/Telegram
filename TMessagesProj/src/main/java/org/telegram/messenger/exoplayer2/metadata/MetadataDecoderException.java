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
package org.telegram.messenger.exoplayer2.metadata;

/**
 * Thrown when an error occurs decoding metadata.
 */
public class MetadataDecoderException extends Exception {

  /**
   * @param message The detail message for this exception.
   */
  public MetadataDecoderException(String message) {
    super(message);
  }

  /**
   * @param message The detail message for this exception.
   * @param cause The cause of this exception.
   */
  public MetadataDecoderException(String message, Throwable cause) {
    super(message, cause);
  }

}

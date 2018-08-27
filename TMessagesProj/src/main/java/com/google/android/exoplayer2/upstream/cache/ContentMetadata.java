/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream.cache;

/**
 * Interface for an immutable snapshot of keyed metadata.
 *
 * <p>Internal metadata names are prefixed with {@value #INTERNAL_METADATA_NAME_PREFIX}. Custom
 * metadata names should avoid this prefix to prevent clashes.
 */
public interface ContentMetadata {

  /** Prefix of internal metadata names. */
  String INTERNAL_METADATA_NAME_PREFIX = "exo_";

  /**
   * Returns a metadata value.
   *
   * @param name Name of the metadata to be returned.
   * @param defaultValue Value to return if the metadata doesn't exist.
   * @return The metadata value.
   */
  byte[] get(String name, byte[] defaultValue);

  /**
   * Returns a metadata value.
   *
   * @param name Name of the metadata to be returned.
   * @param defaultValue Value to return if the metadata doesn't exist.
   * @return The metadata value.
   */
  String get(String name, String defaultValue);

  /**
   * Returns a metadata value.
   *
   * @param name Name of the metadata to be returned.
   * @param defaultValue Value to return if the metadata doesn't exist.
   * @return The metadata value.
   */
  long get(String name, long defaultValue);

  /** Returns whether the metadata is available. */
  boolean contains(String name);
}

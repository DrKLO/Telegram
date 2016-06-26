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
package org.telegram.messenger.exoplayer.chunk;

import org.telegram.messenger.exoplayer.upstream.DataSource;
import org.telegram.messenger.exoplayer.upstream.DataSpec;
import org.telegram.messenger.exoplayer.upstream.Loader.Loadable;
import org.telegram.messenger.exoplayer.util.Assertions;

/**
 * An abstract base class for {@link Loadable} implementations that load chunks of data required
 * for the playback of streams.
 */
public abstract class Chunk implements Loadable {

  /**
   * Value of {@link #type} for chunks containing unspecified data.
   */
  public static final int TYPE_UNSPECIFIED = 0;
  /**
   * Value of {@link #type} for chunks containing media data.
   */
  public static final int TYPE_MEDIA = 1;
  /**
   * Value of {@link #type} for chunks containing media initialization data.
   */
  public static final int TYPE_MEDIA_INITIALIZATION = 2;
  /**
   * Value of {@link #type} for chunks containing drm related data.
   */
  public static final int TYPE_DRM = 3;
  /**
   * Value of {@link #type} for chunks containing manifest or playlist data.
   */
  public static final int TYPE_MANIFEST = 4;
  /**
   * Implementations may define custom {@link #type} codes greater than or equal to this value.
   */
  public static final int TYPE_CUSTOM_BASE = 10000;

  /**
   * Value of {@link #trigger} for a load whose reason is unspecified.
   */
  public static final int TRIGGER_UNSPECIFIED = 0;
  /**
   * Value of {@link #trigger} for a load triggered by an initial format selection.
   */
  public static final int TRIGGER_INITIAL = 1;
  /**
   * Value of {@link #trigger} for a load triggered by a user initiated format selection.
   */
  public static final int TRIGGER_MANUAL = 2;
  /**
   * Value of {@link #trigger} for a load triggered by an adaptive format selection.
   */
  public static final int TRIGGER_ADAPTIVE = 3;
  /**
   * Value of {@link #trigger} for a load triggered whilst in a trick play mode.
   */
  public static final int TRIGGER_TRICK_PLAY = 4;
  /**
   * Implementations may define custom {@link #trigger} codes greater than or equal to this value.
   */
  public static final int TRIGGER_CUSTOM_BASE = 10000;
  /**
   * Value of {@link #parentId} if no parent id need be specified.
   */
  public static final int NO_PARENT_ID = -1;

  /**
   * The type of the chunk. For reporting only.
   */
  public final int type;
  /**
   * The reason why the chunk was generated. For reporting only.
   */
  public final int trigger;
  /**
   * The format associated with the data being loaded, or null if the data being loaded is not
   * associated with a specific format.
   */
  public final Format format;
  /**
   * The {@link DataSpec} that defines the data to be loaded.
   */
  public final DataSpec dataSpec;
  /**
   * Optional identifier for a parent from which this chunk originates.
   */
  public final int parentId;

  protected final DataSource dataSource;

  /**
   * @param dataSource The source from which the data should be loaded.
   * @param dataSpec Defines the data to be loaded. {@code dataSpec.length} must not exceed
   *     {@link Integer#MAX_VALUE}. If {@code dataSpec.length == C.LENGTH_UNBOUNDED} then
   *     the length resolved by {@code dataSource.open(dataSpec)} must not exceed
   *     {@link Integer#MAX_VALUE}.
   * @param type See {@link #type}.
   * @param trigger See {@link #trigger}.
   * @param format See {@link #format}.
   * @param parentId See {@link #parentId}.
   */
  public Chunk(DataSource dataSource, DataSpec dataSpec, int type, int trigger, Format format,
      int parentId) {
    this.dataSource = Assertions.checkNotNull(dataSource);
    this.dataSpec = Assertions.checkNotNull(dataSpec);
    this.type = type;
    this.trigger = trigger;
    this.format = format;
    this.parentId = parentId;
  }

  /**
   * Gets the number of bytes that have been loaded.
   *
   * @return The number of bytes that have been loaded.
   */
  public abstract long bytesLoaded();

}

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

import androidx.annotation.Nullable;
import com.google.common.base.Charsets;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** Default implementation of {@link ContentMetadata}. Values are stored as byte arrays. */
public final class DefaultContentMetadata implements ContentMetadata {

  /** An empty DefaultContentMetadata. */
  public static final DefaultContentMetadata EMPTY =
      new DefaultContentMetadata(Collections.emptyMap());

  private int hashCode;

  private final Map<String, byte[]> metadata;

  public DefaultContentMetadata() {
    this(Collections.emptyMap());
  }

  /**
   * @param metadata The metadata entries in their raw byte array form.
   */
  public DefaultContentMetadata(Map<String, byte[]> metadata) {
    this.metadata = Collections.unmodifiableMap(metadata);
  }

  /**
   * Returns a copy {@link DefaultContentMetadata} with {@code mutations} applied. If {@code
   * mutations} don't change anything, returns this instance.
   */
  public DefaultContentMetadata copyWithMutationsApplied(ContentMetadataMutations mutations) {
    Map<String, byte[]> mutatedMetadata = applyMutations(metadata, mutations);
    if (isMetadataEqual(metadata, mutatedMetadata)) {
      return this;
    }
    return new DefaultContentMetadata(mutatedMetadata);
  }

  /** Returns the set of metadata entries in their raw byte array form. */
  public Set<Entry<String, byte[]>> entrySet() {
    return metadata.entrySet();
  }

  @Override
  @Nullable
  public final byte[] get(String key, @Nullable byte[] defaultValue) {
    @Nullable byte[] bytes = metadata.get(key);
    if (bytes != null) {
      return Arrays.copyOf(bytes, bytes.length);
    } else {
      return defaultValue;
    }
  }

  @Override
  @Nullable
  public final String get(String key, @Nullable String defaultValue) {
    @Nullable byte[] bytes = metadata.get(key);
    if (bytes != null) {
      return new String(bytes, Charsets.UTF_8);
    } else {
      return defaultValue;
    }
  }

  @Override
  public final long get(String key, long defaultValue) {
    @Nullable byte[] bytes = metadata.get(key);
    if (bytes != null) {
      return ByteBuffer.wrap(bytes).getLong();
    } else {
      return defaultValue;
    }
  }

  @Override
  public final boolean contains(String key) {
    return metadata.containsKey(key);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return isMetadataEqual(metadata, ((DefaultContentMetadata) o).metadata);
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 0;
      for (Entry<String, byte[]> entry : metadata.entrySet()) {
        result += entry.getKey().hashCode() ^ Arrays.hashCode(entry.getValue());
      }
      hashCode = result;
    }
    return hashCode;
  }

  private static boolean isMetadataEqual(Map<String, byte[]> first, Map<String, byte[]> second) {
    if (first.size() != second.size()) {
      return false;
    }
    for (Entry<String, byte[]> entry : first.entrySet()) {
      byte[] value = entry.getValue();
      @Nullable byte[] otherValue = second.get(entry.getKey());
      if (!Arrays.equals(value, otherValue)) {
        return false;
      }
    }
    return true;
  }

  private static Map<String, byte[]> applyMutations(
      Map<String, byte[]> otherMetadata, ContentMetadataMutations mutations) {
    HashMap<String, byte[]> metadata = new HashMap<>(otherMetadata);
    removeValues(metadata, mutations.getRemovedValues());
    addValues(metadata, mutations.getEditedValues());
    return metadata;
  }

  private static void removeValues(HashMap<String, byte[]> metadata, List<String> names) {
    for (int i = 0; i < names.size(); i++) {
      metadata.remove(names.get(i));
    }
  }

  private static void addValues(HashMap<String, byte[]> metadata, Map<String, Object> values) {
    for (Entry<String, Object> entry : values.entrySet()) {
      metadata.put(entry.getKey(), getBytes(entry.getValue()));
    }
  }

  private static byte[] getBytes(Object value) {
    if (value instanceof Long) {
      return ByteBuffer.allocate(8).putLong((Long) value).array();
    } else if (value instanceof String) {
      return ((String) value).getBytes(Charsets.UTF_8);
    } else if (value instanceof byte[]) {
      return (byte[]) value;
    } else {
      throw new IllegalArgumentException();
    }
  }
}

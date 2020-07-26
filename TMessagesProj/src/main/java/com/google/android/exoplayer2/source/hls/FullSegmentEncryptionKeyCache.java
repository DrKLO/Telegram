/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.source.hls;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU cache that holds up to {@code maxSize} full-segment-encryption keys. Which each addition,
 * once the cache's size exceeds {@code maxSize}, the oldest item (according to insertion order) is
 * removed.
 */
/* package */ final class FullSegmentEncryptionKeyCache {

  private final LinkedHashMap<Uri, byte[]> backingMap;

  public FullSegmentEncryptionKeyCache(int maxSize) {
    backingMap =
        new LinkedHashMap<Uri, byte[]>(
            /* initialCapacity= */ maxSize + 1, /* loadFactor= */ 1, /* accessOrder= */ false) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<Uri, byte[]> eldest) {
            return size() > maxSize;
          }
        };
  }

  /**
   * Returns the {@code encryptionKey} cached against this {@code uri}, or null if {@code uri} is
   * null or not present in the cache.
   */
  @Nullable
  public byte[] get(@Nullable Uri uri) {
    if (uri == null) {
      return null;
    }
    return backingMap.get(uri);
  }

  /**
   * Inserts an entry into the cache.
   *
   * @throws NullPointerException if {@code uri} or {@code encryptionKey} are null.
   */
  @Nullable
  public byte[] put(Uri uri, byte[] encryptionKey) {
    return backingMap.put(Assertions.checkNotNull(uri), Assertions.checkNotNull(encryptionKey));
  }

  /**
   * Returns true if {@code uri} is present in the cache.
   *
   * @throws NullPointerException if {@code uri} is null.
   */
  public boolean containsUri(Uri uri) {
    return backingMap.containsKey(Assertions.checkNotNull(uri));
  }

  /**
   * Removes {@code uri} from the cache. If {@code uri} was present in the cahce, this returns the
   * corresponding {@code encryptionKey}, otherwise null.
   *
   * @throws NullPointerException if {@code uri} is null.
   */
  @Nullable
  public byte[] remove(Uri uri) {
    return backingMap.remove(Assertions.checkNotNull(uri));
  }
}

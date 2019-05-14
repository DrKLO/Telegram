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
package com.google.android.exoplayer2.upstream.cache;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** This class stores span metadata in filename. */
/* package */ final class SimpleCacheSpan extends CacheSpan {

  private static final String SUFFIX = ".v3.exo";
  private static final Pattern CACHE_FILE_PATTERN_V1 = Pattern.compile(
      "^(.+)\\.(\\d+)\\.(\\d+)\\.v1\\.exo$", Pattern.DOTALL);
  private static final Pattern CACHE_FILE_PATTERN_V2 = Pattern.compile(
      "^(.+)\\.(\\d+)\\.(\\d+)\\.v2\\.exo$", Pattern.DOTALL);
  private static final Pattern CACHE_FILE_PATTERN_V3 = Pattern.compile(
      "^(\\d+)\\.(\\d+)\\.(\\d+)\\.v3\\.exo$", Pattern.DOTALL);

  /**
   * Returns a new {@link File} instance from {@code cacheDir}, {@code id}, {@code position}, {@code
   * lastAccessTimestamp}.
   *
   * @param cacheDir The parent abstract pathname.
   * @param id The cache file id.
   * @param position The position of the stored data in the original stream.
   * @param lastAccessTimestamp The last access timestamp.
   * @return The cache file.
   */
  public static File getCacheFile(File cacheDir, int id, long position, long lastAccessTimestamp) {
    return new File(cacheDir, id + "." + position + "." + lastAccessTimestamp + SUFFIX);
  }

  /**
   * Creates a lookup span.
   *
   * @param key The cache key.
   * @param position The position of the {@link CacheSpan} in the original stream.
   * @return The span.
   */
  public static SimpleCacheSpan createLookup(String key, long position) {
    return new SimpleCacheSpan(key, position, C.LENGTH_UNSET, C.TIME_UNSET, null);
  }

  /**
   * Creates an open hole span.
   *
   * @param key The cache key.
   * @param position The position of the {@link CacheSpan} in the original stream.
   * @return The span.
   */
  public static SimpleCacheSpan createOpenHole(String key, long position) {
    return new SimpleCacheSpan(key, position, C.LENGTH_UNSET, C.TIME_UNSET, null);
  }

  /**
   * Creates a closed hole span.
   *
   * @param key The cache key.
   * @param position The position of the {@link CacheSpan} in the original stream.
   * @param length The length of the {@link CacheSpan}.
   * @return The span.
   */
  public static SimpleCacheSpan createClosedHole(String key, long position, long length) {
    return new SimpleCacheSpan(key, position, length, C.TIME_UNSET, null);
  }

  /*
   * Note: {@code fileLength} is equivalent to {@code file.length()}, but passing it as an explicit
   * argument can reduce the number of calls to this method if the calling code already knows the
   * file length. This is preferable because calling {@code file.length()} can be expensive. See:
   * https://github.com/google/ExoPlayer/issues/4253#issuecomment-451593889.
   */
  /**
   * Creates a cache span from an underlying cache file. Upgrades the file if necessary.
   *
   * @param file The cache file.
   * @param length The length of the cache file in bytes.
   * @return The span, or null if the file name is not correctly formatted, or if the id is not
   *     present in the content index.
   */
  @Nullable
  public static SimpleCacheSpan createCacheEntry(File file, long length, CachedContentIndex index) {
    String name = file.getName();
    if (!name.endsWith(SUFFIX)) {
      file = upgradeFile(file, index);
      if (file == null) {
        return null;
      }
      name = file.getName();
    }

    Matcher matcher = CACHE_FILE_PATTERN_V3.matcher(name);
    if (!matcher.matches()) {
      return null;
    }
    int id = Integer.parseInt(matcher.group(1));
    String key = index.getKeyForId(id);
    return key == null
        ? null
        : new SimpleCacheSpan(
            key, Long.parseLong(matcher.group(2)), length, Long.parseLong(matcher.group(3)), file);
  }

  /**
   * Upgrades the cache file if it is created by an earlier version of {@link SimpleCache}.
   *
   * @param file The cache file.
   * @param index Cached content index.
   * @return Upgraded cache file or {@code null} if the file name is not correctly formatted or the
   *     file can not be renamed.
   */
  @Nullable
  private static File upgradeFile(File file, CachedContentIndex index) {
    String key;
    String filename = file.getName();
    Matcher matcher = CACHE_FILE_PATTERN_V2.matcher(filename);
    if (matcher.matches()) {
      key = Util.unescapeFileName(matcher.group(1));
      if (key == null) {
        return null;
      }
    } else {
      matcher = CACHE_FILE_PATTERN_V1.matcher(filename);
      if (!matcher.matches()) {
        return null;
      }
      key = matcher.group(1); // Keys were not escaped in version 1.
    }

    File newCacheFile = getCacheFile(file.getParentFile(), index.assignIdForKey(key),
        Long.parseLong(matcher.group(2)), Long.parseLong(matcher.group(3)));
    if (!file.renameTo(newCacheFile)) {
      return null;
    }
    return newCacheFile;
  }

  /**
   * @param key The cache key.
   * @param position The position of the {@link CacheSpan} in the original stream.
   * @param length The length of the {@link CacheSpan}, or {@link C#LENGTH_UNSET} if this is an
   *     open-ended hole.
   * @param lastAccessTimestamp The last access timestamp, or {@link C#TIME_UNSET} if {@link
   *     #isCached} is false.
   * @param file The file corresponding to this {@link CacheSpan}, or null if it's a hole.
   */
  private SimpleCacheSpan(
      String key, long position, long length, long lastAccessTimestamp, @Nullable File file) {
    super(key, position, length, lastAccessTimestamp, file);
  }

  /**
   * Returns a copy of this CacheSpan whose last access time stamp is set to current time. This
   * doesn't copy or change the underlying cache file.
   *
   * @param id The cache file id.
   * @return A {@link SimpleCacheSpan} with updated last access time stamp.
   * @throws IllegalStateException If called on a non-cached span (i.e. {@link #isCached} is false).
   */
  public SimpleCacheSpan copyWithUpdatedLastAccessTime(int id) {
    Assertions.checkState(isCached);
    long now = System.currentTimeMillis();
    File newCacheFile = getCacheFile(file.getParentFile(), id, position, now);
    return new SimpleCacheSpan(key, position, length, now, newCacheFile);
  }

}

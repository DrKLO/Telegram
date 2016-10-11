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
package org.telegram.messenger.exoplayer.upstream.cache;

import org.telegram.messenger.exoplayer.util.Util;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Defines a span of data that may or may not be cached (as indicated by {@link #isCached}).
 */
public final class CacheSpan implements Comparable<CacheSpan> {

  private static final String SUFFIX = ".v2.exo";
  private static final Pattern CACHE_FILE_PATTERN_V1 =
      Pattern.compile("^(.+)\\.(\\d+)\\.(\\d+)\\.v1\\.exo$");
  private static final Pattern CACHE_FILE_PATTERN_V2 =
      Pattern.compile("^(.+)\\.(\\d+)\\.(\\d+)\\.v2\\.exo$");

  /**
   * The cache key that uniquely identifies the original stream.
   */
  public final String key;
  /**
   * The position of the {@link CacheSpan} in the original stream.
   */
  public final long position;
  /**
   * The length of the {@link CacheSpan}, or -1 if this is an open-ended hole.
   */
  public final long length;
  /**
   * Whether the {@link CacheSpan} is cached.
   */
  public final boolean isCached;
  /**
   * The file corresponding to this {@link CacheSpan}, or null if {@link #isCached} is false.
   */
  public final File file;
  /**
   * The last access timestamp, or -1 if {@link #isCached} is false.
   */
  public final long lastAccessTimestamp;

  public static File getCacheFileName(File cacheDir, String key, long offset,
      long lastAccessTimestamp) {
    return new File(cacheDir,
        Util.escapeFileName(key) + "." + offset + "." + lastAccessTimestamp + SUFFIX);
  }

  public static CacheSpan createLookup(String key, long position) {
    return new CacheSpan(key, position, -1, false, -1, null);
  }

  public static CacheSpan createOpenHole(String key, long position) {
    return new CacheSpan(key, position, -1, false, -1, null);
  }

  public static CacheSpan createClosedHole(String key, long position, long length) {
    return new CacheSpan(key, position, length, false, -1, null);
  }

  /**
   * Creates a cache span from an underlying cache file.
   *
   * @param file The cache file.
   * @return The span, or null if the file name is not correctly formatted.
   */
  public static CacheSpan createCacheEntry(File file) {
    Matcher matcher = CACHE_FILE_PATTERN_V2.matcher(file.getName());
    if (!matcher.matches()) {
      return null;
    }
    String key = Util.unescapeFileName(matcher.group(1));
    return key == null ? null : createCacheEntry(
        key, Long.parseLong(matcher.group(2)), Long.parseLong(matcher.group(3)), file);
  }

  static File upgradeIfNeeded(File file) {
    Matcher matcher = CACHE_FILE_PATTERN_V1.matcher(file.getName());
    if (!matcher.matches()) {
      return file;
    }
    String key = matcher.group(1); // Keys were not escaped in version 1.
    File newCacheFile = getCacheFileName(file.getParentFile(), key,
        Long.parseLong(matcher.group(2)), Long.parseLong(matcher.group(3)));
    file.renameTo(newCacheFile);
    return newCacheFile;
  }

  private static CacheSpan createCacheEntry(String key, long position, long lastAccessTimestamp,
      File file) {
    return new CacheSpan(key, position, file.length(), true, lastAccessTimestamp, file);
  }

  // Visible for testing.
  CacheSpan(String key, long position, long length, boolean isCached,
      long lastAccessTimestamp, File file) {
    this.key = key;
    this.position = position;
    this.length = length;
    this.isCached = isCached;
    this.file = file;
    this.lastAccessTimestamp = lastAccessTimestamp;
  }

  /**
   * @return True if this is an open-ended {@link CacheSpan}. False otherwise.
   */
  public boolean isOpenEnded() {
    return length == -1;
  }

  /**
   * Renames the file underlying this cache span to update its last access time.
   *
   * @return A {@link CacheSpan} representing the updated cache file.
   */
  public CacheSpan touch() {
    long now = System.currentTimeMillis();
    File newCacheFile = getCacheFileName(file.getParentFile(), key, position, now);
    file.renameTo(newCacheFile);
    return CacheSpan.createCacheEntry(key, position, now, newCacheFile);
  }

  @Override
  public int compareTo(CacheSpan another) {
    if (!key.equals(another.key)) {
      return key.compareTo(another.key);
    }
    long startOffsetDiff = position - another.position;
    return startOffsetDiff == 0 ? 0 : ((startOffsetDiff < 0) ? -1 : 1);
  }

}

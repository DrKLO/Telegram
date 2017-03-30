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
package org.telegram.messenger.exoplayer2.upstream.cache;

import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class stores span metadata in filename.
 */
/*package*/ final class SimpleCacheSpan extends CacheSpan {

  private static final String SUFFIX = ".v3.exo";
  private static final Pattern CACHE_FILE_PATTERN_V1 = Pattern.compile(
      "^(.+)\\.(\\d+)\\.(\\d+)\\.v1\\.exo$", Pattern.DOTALL);
  private static final Pattern CACHE_FILE_PATTERN_V2 = Pattern.compile(
      "^(.+)\\.(\\d+)\\.(\\d+)\\.v2\\.exo$", Pattern.DOTALL);
  private static final Pattern CACHE_FILE_PATTERN_V3 = Pattern.compile(
      "^(\\d+)\\.(\\d+)\\.(\\d+)\\.v3\\.exo$", Pattern.DOTALL);

  public static File getCacheFile(File cacheDir, int id, long position,
      long lastAccessTimestamp) {
    return new File(cacheDir, id + "." + position + "." + lastAccessTimestamp + SUFFIX);
  }

  public static SimpleCacheSpan createLookup(String key, long position) {
    return new SimpleCacheSpan(key, position, C.LENGTH_UNSET, C.TIME_UNSET, null);
  }

  public static SimpleCacheSpan createOpenHole(String key, long position) {
    return new SimpleCacheSpan(key, position, C.LENGTH_UNSET, C.TIME_UNSET, null);
  }

  public static SimpleCacheSpan createClosedHole(String key, long position, long length) {
    return new SimpleCacheSpan(key, position, length, C.TIME_UNSET, null);
  }

  /**
   * Creates a cache span from an underlying cache file. Upgrades the file if necessary.
   *
   * @param file The cache file.
   * @param index Cached content index.
   * @return The span, or null if the file name is not correctly formatted, or if the id is not
   *     present in the content index.
   */
  public static SimpleCacheSpan createCacheEntry(File file, CachedContentIndex index) {
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
    long length = file.length();
    int id = Integer.parseInt(matcher.group(1));
    String key = index.getKeyForId(id);
    return key == null ? null : new SimpleCacheSpan(key, Long.parseLong(matcher.group(2)), length,
        Long.parseLong(matcher.group(3)), file);
  }

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

  private SimpleCacheSpan(String key, long position, long length, long lastAccessTimestamp,
      File file) {
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

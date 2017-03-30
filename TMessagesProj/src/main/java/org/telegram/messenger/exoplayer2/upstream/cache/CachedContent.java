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
import org.telegram.messenger.exoplayer2.upstream.cache.Cache.CacheException;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.TreeSet;

/**
 * Defines the cached content for a single stream.
 */
/*package*/ final class CachedContent {

  /**
   * The cache file id that uniquely identifies the original stream.
   */
  public final int id;
  /**
   * The cache key that uniquely identifies the original stream.
   */
  public final String key;
  /**
   * The cached spans of this content.
   */
  private final TreeSet<SimpleCacheSpan> cachedSpans;
  /**
   * The length of the original stream, or {@link C#LENGTH_UNSET} if the length is unknown.
   */
  private long length;

  /**
   * Reads an instance from a {@link DataInputStream}.
   *
   * @param input Input stream containing values needed to initialize CachedContent instance.
   * @throws IOException If an error occurs during reading values.
   */
  public CachedContent(DataInputStream input) throws IOException {
    this(input.readInt(), input.readUTF(), input.readLong());
  }

  /**
   * Creates a CachedContent.
   *
   * @param id The cache file id.
   * @param key The cache stream key.
   * @param length The length of the original stream.
   */
  public CachedContent(int id, String key, long length) {
    this.id = id;
    this.key = key;
    this.length = length;
    this.cachedSpans = new TreeSet<>();
  }

  /**
   * Writes the instance to a {@link DataOutputStream}.
   *
   * @param output Output stream to store the values.
   * @throws IOException If an error occurs during writing values to output.
   */
  public void writeToStream(DataOutputStream output) throws IOException {
    output.writeInt(id);
    output.writeUTF(key);
    output.writeLong(length);
  }

  /** Returns the length of the content. */
  public long getLength() {
    return length;
  }

  /** Sets the length of the content. */
  public void setLength(long length) {
    this.length = length;
  }

  /** Adds the given {@link SimpleCacheSpan} which contains a part of the content. */
  public void addSpan(SimpleCacheSpan span) {
    cachedSpans.add(span);
  }

  /** Returns a set of all {@link SimpleCacheSpan}s. */
  public TreeSet<SimpleCacheSpan> getSpans() {
    return cachedSpans;
  }

  /**
   * Returns the span containing the position. If there isn't one, it returns a hole span
   * which defines the maximum extents of the hole in the cache.
   */
  public SimpleCacheSpan getSpan(long position) {
    SimpleCacheSpan span = getSpanInternal(position);
    if (!span.isCached) {
      SimpleCacheSpan ceilEntry = cachedSpans.ceiling(span);
      return ceilEntry == null ? SimpleCacheSpan.createOpenHole(key, position)
          : SimpleCacheSpan.createClosedHole(key, position, ceilEntry.position - position);
    }
    return span;
  }

  /** Queries if a range is entirely available in the cache. */
  public boolean isCached(long position, long length) {
    SimpleCacheSpan floorSpan = getSpanInternal(position);
    if (!floorSpan.isCached) {
      // We don't have a span covering the start of the queried region.
      return false;
    }
    long queryEndPosition = position + length;
    long currentEndPosition = floorSpan.position + floorSpan.length;
    if (currentEndPosition >= queryEndPosition) {
      // floorSpan covers the queried region.
      return true;
    }
    for (SimpleCacheSpan next : cachedSpans.tailSet(floorSpan, false)) {
      if (next.position > currentEndPosition) {
        // There's a hole in the cache within the queried region.
        return false;
      }
      // We expect currentEndPosition to always equal (next.position + next.length), but
      // perform a max check anyway to guard against the existence of overlapping spans.
      currentEndPosition = Math.max(currentEndPosition, next.position + next.length);
      if (currentEndPosition >= queryEndPosition) {
        // We've found spans covering the queried region.
        return true;
      }
    }
    // We ran out of spans before covering the queried region.
    return false;
  }

  /**
   * Copies the given span with an updated last access time. Passed span becomes invalid after this
   * call.
   *
   * @param cacheSpan Span to be copied and updated.
   * @return a span with the updated last access time.
   * @throws CacheException If renaming of the underlying span file failed.
   */
  public SimpleCacheSpan touch(SimpleCacheSpan cacheSpan) throws CacheException {
    // Remove the old span from the in-memory representation.
    Assertions.checkState(cachedSpans.remove(cacheSpan));
    // Obtain a new span with updated last access timestamp.
    SimpleCacheSpan newCacheSpan = cacheSpan.copyWithUpdatedLastAccessTime(id);
    // Rename the cache file
    if (!cacheSpan.file.renameTo(newCacheSpan.file)) {
      throw new CacheException("Renaming of " + cacheSpan.file + " to " + newCacheSpan.file
          + " failed.");
    }
    // Add the updated span back into the in-memory representation.
    cachedSpans.add(newCacheSpan);
    return newCacheSpan;
  }

  /** Returns whether there are any spans cached. */
  public boolean isEmpty() {
    return cachedSpans.isEmpty();
  }

  /** Removes the given span from cache. */
  public boolean removeSpan(CacheSpan span) {
    if (cachedSpans.remove(span)) {
      span.file.delete();
      return true;
    }
    return false;
  }

  /** Calculates a hash code for the header of this {@code CachedContent}. */
  public int headerHashCode() {
    int result = id;
    result = 31 * result + key.hashCode();
    result = 31 * result + (int) (length ^ (length >>> 32));
    return result;
  }

  /**
   * Returns the span containing the position. If there isn't one, it returns the lookup span it
   * used for searching.
   */
  private SimpleCacheSpan getSpanInternal(long position) {
    SimpleCacheSpan lookupSpan = SimpleCacheSpan.createLookup(key, position);
    SimpleCacheSpan floorSpan = cachedSpans.floor(lookupSpan);
    return floorSpan == null || floorSpan.position + floorSpan.length <= position ? lookupSpan
        : floorSpan;
  }

}

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
    SimpleCacheSpan lookupSpan = SimpleCacheSpan.createLookup(key, position);
    SimpleCacheSpan floorSpan = cachedSpans.floor(lookupSpan);
    if (floorSpan != null && floorSpan.position + floorSpan.length > position) {
      return floorSpan;
    }
    SimpleCacheSpan ceilSpan = cachedSpans.ceiling(lookupSpan);
    return ceilSpan == null ? SimpleCacheSpan.createOpenHole(key, position)
        : SimpleCacheSpan.createClosedHole(key, position, ceilSpan.position - position);
  }

  /**
   * Returns the length of the cached data block starting from the {@code position} to the block end
   * up to {@code length} bytes. If the {@code position} isn't cached then -(the length of the gap
   * to the next cached data up to {@code length} bytes) is returned.
   *
   * @param position The starting position of the data.
   * @param length The maximum length of the data to be returned.
   * @return the length of the cached or not cached data block length.
   */
  public long getCachedBytes(long position, long length) {
    SimpleCacheSpan span = getSpan(position);
    if (span.isHoleSpan()) {
      // We don't have a span covering the start of the queried region.
      return -Math.min(span.isOpenEnded() ? Long.MAX_VALUE : span.length, length);
    }
    long queryEndPosition = position + length;
    long currentEndPosition = span.position + span.length;
    if (currentEndPosition < queryEndPosition) {
      for (SimpleCacheSpan next : cachedSpans.tailSet(span, false)) {
        if (next.position > currentEndPosition) {
          // There's a hole in the cache within the queried region.
          break;
        }
        // We expect currentEndPosition to always equal (next.position + next.length), but
        // perform a max check anyway to guard against the existence of overlapping spans.
        currentEndPosition = Math.max(currentEndPosition, next.position + next.length);
        if (currentEndPosition >= queryEndPosition) {
          // We've found spans covering the queried region.
          break;
        }
      }
    }
    return Math.min(currentEndPosition - position, length);
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

}

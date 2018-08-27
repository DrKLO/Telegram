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

import android.support.annotation.Nullable;
import com.google.android.exoplayer2.upstream.cache.Cache.CacheException;
import com.google.android.exoplayer2.util.Assertions;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.TreeSet;

/**
 * Defines the cached content for a single stream.
 */
/*package*/ final class CachedContent {

  private static final int VERSION_METADATA_INTRODUCED = 2;
  private static final int VERSION_MAX = Integer.MAX_VALUE;

  /** The cache file id that uniquely identifies the original stream. */
  public final int id;
  /** The cache key that uniquely identifies the original stream. */
  public final String key;
  /** The cached spans of this content. */
  private final TreeSet<SimpleCacheSpan> cachedSpans;
  /** Metadata values. */
  private DefaultContentMetadata metadata;
  /** Whether the content is locked. */
  private boolean locked;

  /**
   * Reads an instance from a {@link DataInputStream}.
   *
   * @param version Version of the encoded data.
   * @param input Input stream containing values needed to initialize CachedContent instance.
   * @throws IOException If an error occurs during reading values.
   */
  public static CachedContent readFromStream(int version, DataInputStream input)
      throws IOException {
    int id = input.readInt();
    String key = input.readUTF();
    CachedContent cachedContent = new CachedContent(id, key);
    if (version < VERSION_METADATA_INTRODUCED) {
      long length = input.readLong();
      ContentMetadataMutations mutations = new ContentMetadataMutations();
      ContentMetadataInternal.setContentLength(mutations, length);
      cachedContent.applyMetadataMutations(mutations);
    } else {
      cachedContent.metadata = DefaultContentMetadata.readFromStream(input);
    }
    return cachedContent;
  }

  /**
   * Creates a CachedContent.
   *
   * @param id The cache file id.
   * @param key The cache stream key.
   */
  public CachedContent(int id, String key) {
    this.id = id;
    this.key = key;
    this.metadata = DefaultContentMetadata.EMPTY;
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
    metadata.writeToStream(output);
  }

  /** Returns the metadata. */
  public ContentMetadata getMetadata() {
    return metadata;
  }

  /**
   * Applies {@code mutations} to the metadata.
   *
   * @return Whether {@code mutations} changed any metadata.
   */
  public boolean applyMetadataMutations(ContentMetadataMutations mutations) {
    DefaultContentMetadata oldMetadata = metadata;
    metadata = metadata.copyWithMutationsApplied(mutations);
    return !metadata.equals(oldMetadata);
  }

  /** Returns whether the content is locked. */
  public boolean isLocked() {
    return locked;
  }

  /** Sets the locked state of the content. */
  public void setLocked(boolean locked) {
    this.locked = locked;
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
  public long getCachedBytesLength(long position, long length) {
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

  /**
   * Calculates a hash code for the header of this {@code CachedContent} which is compatible with
   * the index file with {@code version}.
   */
  public int headerHashCode(int version) {
    int result = id;
    result = 31 * result + key.hashCode();
    if (version < VERSION_METADATA_INTRODUCED) {
      long length = ContentMetadataInternal.getContentLength(metadata);
      result = 31 * result + (int) (length ^ (length >>> 32));
    } else {
      result = 31 * result + metadata.hashCode();
    }
    return result;
  }

  @Override
  public int hashCode() {
    int result = headerHashCode(VERSION_MAX);
    result = 31 * result + cachedSpans.hashCode();
    return result;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CachedContent that = (CachedContent) o;
    return id == that.id
        && key.equals(that.key)
        && cachedSpans.equals(that.cachedSpans)
        && metadata.equals(that.metadata);
  }
}

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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.max;
import static java.lang.Math.min;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.TreeSet;

/** Defines the cached content for a single resource. */
/* package */ final class CachedContent {

  private static final String TAG = "CachedContent";

  /** The cache id that uniquely identifies the resource. */
  public final int id;
  /** The cache key that uniquely identifies the resource. */
  public final String key;
  /** The cached spans of this content. */
  private final TreeSet<SimpleCacheSpan> cachedSpans;
  /** Currently locked ranges. */
  private final ArrayList<Range> lockedRanges;

  /** Metadata values. */
  private DefaultContentMetadata metadata;

  /**
   * Creates a CachedContent.
   *
   * @param id The cache id of the resource.
   * @param key The cache key of the resource.
   */
  public CachedContent(int id, String key) {
    this(id, key, DefaultContentMetadata.EMPTY);
  }

  public CachedContent(int id, String key, DefaultContentMetadata metadata) {
    this.id = id;
    this.key = key;
    this.metadata = metadata;
    cachedSpans = new TreeSet<>();
    lockedRanges = new ArrayList<>();
  }

  /** Returns the metadata. */
  public DefaultContentMetadata getMetadata() {
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

  /** Returns whether the entire resource is fully unlocked. */
  public boolean isFullyUnlocked() {
    return lockedRanges.isEmpty();
  }

  /**
   * Returns whether the specified range of the resource is fully locked by a single lock.
   *
   * @param position The position of the range.
   * @param length The length of the range, or {@link C#LENGTH_UNSET} if unbounded.
   * @return Whether the range is fully locked by a single lock.
   */
  public boolean isFullyLocked(long position, long length) {
    for (int i = 0; i < lockedRanges.size(); i++) {
      if (lockedRanges.get(i).contains(position, length)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Attempts to lock the specified range of the resource.
   *
   * @param position The position of the range.
   * @param length The length of the range, or {@link C#LENGTH_UNSET} if unbounded.
   * @return Whether the range was successfully locked.
   */
  public boolean lockRange(long position, long length) {
    for (int i = 0; i < lockedRanges.size(); i++) {
      if (lockedRanges.get(i).intersects(position, length)) {
        return false;
      }
    }
    lockedRanges.add(new Range(position, length));
    return true;
  }

  /**
   * Unlocks the currently locked range starting at the specified position.
   *
   * @param position The starting position of the locked range.
   * @throws IllegalStateException If there was no locked range starting at the specified position.
   */
  public void unlockRange(long position) {
    for (int i = 0; i < lockedRanges.size(); i++) {
      if (lockedRanges.get(i).position == position) {
        lockedRanges.remove(i);
        return;
      }
    }
    throw new IllegalStateException();
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
   * Returns the cache span corresponding to the provided range. See {@link
   * Cache#startReadWrite(String, long, long)} for detailed descriptions of the returned spans.
   *
   * @param position The position of the span being requested.
   * @param length The length of the span, or {@link C#LENGTH_UNSET} if unbounded.
   * @return The corresponding cache {@link SimpleCacheSpan}.
   */
  public SimpleCacheSpan getSpan(long position, long length) {
    SimpleCacheSpan lookupSpan = SimpleCacheSpan.createLookup(key, position);
    SimpleCacheSpan floorSpan = cachedSpans.floor(lookupSpan);
    if (floorSpan != null && floorSpan.position + floorSpan.length > position) {
      return floorSpan;
    }
    SimpleCacheSpan ceilSpan = cachedSpans.ceiling(lookupSpan);
    if (ceilSpan != null) {
      long holeLength = ceilSpan.position - position;
      length = length == C.LENGTH_UNSET ? holeLength : min(holeLength, length);
    }
    return SimpleCacheSpan.createHole(key, position, length);
  }

  /**
   * Returns the length of continuously cached data starting from {@code position}, up to a maximum
   * of {@code maxLength}. If {@code position} isn't cached, then {@code -holeLength} is returned,
   * where {@code holeLength} is the length of continuously un-cached data starting from {@code
   * position}, up to a maximum of {@code maxLength}.
   *
   * @param position The starting position of the data.
   * @param length The maximum length of the data or hole to be returned.
   * @return The length of continuously cached data, or {@code -holeLength} if {@code position}
   *     isn't cached.
   */
  public long getCachedBytesLength(long position, long length) {
    checkArgument(position >= 0);
    checkArgument(length >= 0);
    SimpleCacheSpan span = getSpan(position, length);
    if (span.isHoleSpan()) {
      // We don't have a span covering the start of the queried region.
      return -min(span.isOpenEnded() ? Long.MAX_VALUE : span.length, length);
    }
    long queryEndPosition = position + length;
    if (queryEndPosition < 0) {
      // The calculation rolled over (length is probably Long.MAX_VALUE).
      queryEndPosition = Long.MAX_VALUE;
    }
    long currentEndPosition = span.position + span.length;
    if (currentEndPosition < queryEndPosition) {
      for (SimpleCacheSpan next : cachedSpans.tailSet(span, false)) {
        if (next.position > currentEndPosition) {
          // There's a hole in the cache within the queried region.
          break;
        }
        // We expect currentEndPosition to always equal (next.position + next.length), but
        // perform a max check anyway to guard against the existence of overlapping spans.
        currentEndPosition = max(currentEndPosition, next.position + next.length);
        if (currentEndPosition >= queryEndPosition) {
          // We've found spans covering the queried region.
          break;
        }
      }
    }
    return min(currentEndPosition - position, length);
  }

  /**
   * Sets the given span's last touch timestamp. The passed span becomes invalid after this call.
   *
   * @param cacheSpan Span to be copied and updated.
   * @param lastTouchTimestamp The new last touch timestamp.
   * @param updateFile Whether the span file should be renamed to have its timestamp match the new
   *     last touch time.
   * @return A span with the updated last touch timestamp.
   */
  public SimpleCacheSpan setLastTouchTimestamp(
      SimpleCacheSpan cacheSpan, long lastTouchTimestamp, boolean updateFile) {
    checkState(cachedSpans.remove(cacheSpan));
    File file = checkNotNull(cacheSpan.file);
    if (updateFile) {
      File directory = checkNotNull(file.getParentFile());
      long position = cacheSpan.position;
      File newFile = SimpleCacheSpan.getCacheFile(directory, id, position, lastTouchTimestamp);
      if (file.renameTo(newFile)) {
        file = newFile;
      } else {
        Log.w(TAG, "Failed to rename " + file + " to " + newFile);
      }
    }
    SimpleCacheSpan newCacheSpan =
        cacheSpan.copyWithFileAndLastTouchTimestamp(file, lastTouchTimestamp);
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
      if (span.file != null) {
        span.file.delete();
      }
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    int result = id;
    result = 31 * result + key.hashCode();
    result = 31 * result + metadata.hashCode();
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

  private static final class Range {

    /** The starting position of the range. */
    public final long position;
    /** The length of the range, or {@link C#LENGTH_UNSET} if unbounded. */
    public final long length;

    public Range(long position, long length) {
      this.position = position;
      this.length = length;
    }

    /**
     * Returns whether this range fully contains the range specified by {@code otherPosition} and
     * {@code otherLength}.
     *
     * @param otherPosition The position of the range to check.
     * @param otherLength The length of the range to check, or {@link C#LENGTH_UNSET} if unbounded.
     * @return Whether this range fully contains the specified range.
     */
    public boolean contains(long otherPosition, long otherLength) {
      if (length == C.LENGTH_UNSET) {
        return otherPosition >= position;
      } else if (otherLength == C.LENGTH_UNSET) {
        return false;
      } else {
        return position <= otherPosition && (otherPosition + otherLength) <= (position + length);
      }
    }

    /**
     * Returns whether this range intersects with the range specified by {@code otherPosition} and
     * {@code otherLength}.
     *
     * @param otherPosition The position of the range to check.
     * @param otherLength The length of the range to check, or {@link C#LENGTH_UNSET} if unbounded.
     * @return Whether this range intersects with the specified range.
     */
    public boolean intersects(long otherPosition, long otherLength) {
      if (position <= otherPosition) {
        return length == C.LENGTH_UNSET || position + length > otherPosition;
      } else {
        return otherLength == C.LENGTH_UNSET || otherPosition + otherLength > position;
      }
    }
  }
}

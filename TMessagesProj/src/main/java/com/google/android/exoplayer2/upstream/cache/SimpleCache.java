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

import android.os.ConditionVariable;
import android.support.annotation.NonNull;
import android.util.Log;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * A {@link Cache} implementation that maintains an in-memory representation. Note, only one
 * instance of SimpleCache is allowed for a given directory at a given time.
 */
public final class SimpleCache implements Cache {

  private static final String TAG = "SimpleCache";
  private static final HashSet<File> lockedCacheDirs = new HashSet<>();

  private static boolean cacheFolderLockingDisabled;

  private final File cacheDir;
  private final CacheEvictor evictor;
  private final CachedContentIndex index;
  private final HashMap<String, ArrayList<Listener>> listeners;

  private long totalSpace;
  private boolean released;

  /**
   * Returns whether {@code cacheFolder} is locked by a {@link SimpleCache} instance. To unlock the
   * folder the {@link SimpleCache} instance should be released.
   */
  public static synchronized boolean isCacheFolderLocked(File cacheFolder) {
    return lockedCacheDirs.contains(cacheFolder.getAbsoluteFile());
  }

  /**
   * Disables locking the cache folders which {@link SimpleCache} instances are using and releases
   * any previous lock.
   *
   * <p>The locking prevents multiple {@link SimpleCache} instances from being created for the same
   * folder. Disabling it may cause the cache data to be corrupted. Use at your own risk.
   *
   * @deprecated Don't create multiple {@link SimpleCache} instances for the same cache folder. If
   *     you need to create another instance, make sure you call {@link #release()} on the previous
   *     instance.
   */
  @Deprecated
  public static synchronized void disableCacheFolderLocking() {
    cacheFolderLockingDisabled = true;
    lockedCacheDirs.clear();
  }

  /**
   * Constructs the cache. The cache will delete any unrecognized files from the directory. Hence
   * the directory cannot be used to store other files.
   *
   * @param cacheDir A dedicated cache directory.
   * @param evictor The evictor to be used.
   */
  public SimpleCache(File cacheDir, CacheEvictor evictor) {
    this(cacheDir, evictor, null, false);
  }

  /**
   * Constructs the cache. The cache will delete any unrecognized files from the directory. Hence
   * the directory cannot be used to store other files.
   *
   * @param cacheDir A dedicated cache directory.
   * @param evictor The evictor to be used.
   * @param secretKey If not null, cache keys will be stored encrypted on filesystem using AES/CBC.
   *     The key must be 16 bytes long.
   */
  public SimpleCache(File cacheDir, CacheEvictor evictor, byte[] secretKey) {
    this(cacheDir, evictor, secretKey, secretKey != null);
  }

  /**
   * Constructs the cache. The cache will delete any unrecognized files from the directory. Hence
   * the directory cannot be used to store other files.
   *
   * @param cacheDir A dedicated cache directory.
   * @param evictor The evictor to be used.
   * @param secretKey If not null, cache keys will be stored encrypted on filesystem using AES/CBC.
   *     The key must be 16 bytes long.
   * @param encrypt Whether the index will be encrypted when written. Must be false if {@code
   *     secretKey} is null.
   */
  public SimpleCache(File cacheDir, CacheEvictor evictor, byte[] secretKey, boolean encrypt) {
    this(cacheDir, evictor, new CachedContentIndex(cacheDir, secretKey, encrypt));
  }

  /**
   * Constructs the cache. The cache will delete any unrecognized files from the directory. Hence
   * the directory cannot be used to store other files.
   *
   * @param cacheDir A dedicated cache directory.
   * @param evictor The evictor to be used.
   * @param index The CachedContentIndex to be used.
   */
  /*package*/ SimpleCache(File cacheDir, CacheEvictor evictor, CachedContentIndex index) {
    if (!lockFolder(cacheDir)) {
      throw new IllegalStateException("Another SimpleCache instance uses the folder: " + cacheDir);
    }

    this.cacheDir = cacheDir;
    this.evictor = evictor;
    this.index = index;
    this.listeners = new HashMap<>();

    // Start cache initialization.
    final ConditionVariable conditionVariable = new ConditionVariable();
    new Thread("SimpleCache.initialize()") {
      @Override
      public void run() {
        synchronized (SimpleCache.this) {
          conditionVariable.open();
          initialize();
          SimpleCache.this.evictor.onCacheInitialized();
        }
      }
    }.start();
    conditionVariable.block();
  }

  @Override
  public synchronized void release() throws CacheException {
    if (released) {
      return;
    }
    listeners.clear();
    try {
      removeStaleSpansAndCachedContents();
    } finally {
      unlockFolder(cacheDir);
      released = true;
    }
  }

  @Override
  public synchronized NavigableSet<CacheSpan> addListener(String key, Listener listener) {
    Assertions.checkState(!released);
    ArrayList<Listener> listenersForKey = listeners.get(key);
    if (listenersForKey == null) {
      listenersForKey = new ArrayList<>();
      listeners.put(key, listenersForKey);
    }
    listenersForKey.add(listener);
    return getCachedSpans(key);
  }

  @Override
  public synchronized void removeListener(String key, Listener listener) {
    if (released) {
      return;
    }
    ArrayList<Listener> listenersForKey = listeners.get(key);
    if (listenersForKey != null) {
      listenersForKey.remove(listener);
      if (listenersForKey.isEmpty()) {
        listeners.remove(key);
      }
    }
  }

  @NonNull
  @Override
  public synchronized NavigableSet<CacheSpan> getCachedSpans(String key) {
    Assertions.checkState(!released);
    CachedContent cachedContent = index.get(key);
    return cachedContent == null || cachedContent.isEmpty()
        ? new TreeSet<CacheSpan>()
        : new TreeSet<CacheSpan>(cachedContent.getSpans());
  }

  @Override
  public synchronized Set<String> getKeys() {
    Assertions.checkState(!released);
    return new HashSet<>(index.getKeys());
  }

  @Override
  public synchronized long getCacheSpace() {
    Assertions.checkState(!released);
    return totalSpace;
  }

  @Override
  public synchronized SimpleCacheSpan startReadWrite(String key, long position)
      throws InterruptedException, CacheException {
    while (true) {
      SimpleCacheSpan span = startReadWriteNonBlocking(key, position);
      if (span != null) {
        return span;
      } else {
        // Write case, lock not available. We'll be woken up when a locked span is released (if the
        // released lock is for the requested key then we'll be able to make progress) or when a
        // span is added to the cache (if the span is for the requested key and covers the requested
        // position, then we'll become a read and be able to make progress).
        wait();
      }
    }
  }

  @Override
  public synchronized SimpleCacheSpan startReadWriteNonBlocking(String key, long position)
      throws CacheException {
    Assertions.checkState(!released);
    SimpleCacheSpan cacheSpan = getSpan(key, position);

    // Read case.
    if (cacheSpan.isCached) {
      // Obtain a new span with updated last access timestamp.
      SimpleCacheSpan newCacheSpan = index.get(key).touch(cacheSpan);
      notifySpanTouched(cacheSpan, newCacheSpan);
      return newCacheSpan;
    }

    CachedContent cachedContent = index.getOrAdd(key);
    if (!cachedContent.isLocked()) {
      // Write case, lock available.
      cachedContent.setLocked(true);
      return cacheSpan;
    }

    // Write case, lock not available.
    return null;
  }

  @Override
  public synchronized File startFile(String key, long position, long maxLength)
      throws CacheException {
    Assertions.checkState(!released);
    CachedContent cachedContent = index.get(key);
    Assertions.checkNotNull(cachedContent);
    Assertions.checkState(cachedContent.isLocked());
    if (!cacheDir.exists()) {
      // For some reason the cache directory doesn't exist. Make a best effort to create it.
      cacheDir.mkdirs();
      removeStaleSpansAndCachedContents();
    }
    evictor.onStartFile(this, key, position, maxLength);
    return SimpleCacheSpan.getCacheFile(
        cacheDir, cachedContent.id, position, System.currentTimeMillis());
  }

  @Override
  public synchronized void commitFile(File file) throws CacheException {
    Assertions.checkState(!released);
    SimpleCacheSpan span = SimpleCacheSpan.createCacheEntry(file, index);
    Assertions.checkState(span != null);
    CachedContent cachedContent = index.get(span.key);
    Assertions.checkNotNull(cachedContent);
    Assertions.checkState(cachedContent.isLocked());
    // If the file doesn't exist, don't add it to the in-memory representation.
    if (!file.exists()) {
      return;
    }
    // If the file has length 0, delete it and don't add it to the in-memory representation.
    if (file.length() == 0) {
      file.delete();
      return;
    }
    // Check if the span conflicts with the set content length
    long length = ContentMetadataInternal.getContentLength(cachedContent.getMetadata());
    if (length != C.LENGTH_UNSET) {
      Assertions.checkState((span.position + span.length) <= length);
    }
    addSpan(span);
    index.store();
    notifyAll();
  }

  @Override
  public synchronized void releaseHoleSpan(CacheSpan holeSpan) {
    Assertions.checkState(!released);
    CachedContent cachedContent = index.get(holeSpan.key);
    Assertions.checkNotNull(cachedContent);
    Assertions.checkState(cachedContent.isLocked());
    cachedContent.setLocked(false);
    index.maybeRemove(cachedContent.key);
    notifyAll();
  }

  @Override
  public synchronized void removeSpan(CacheSpan span) throws CacheException {
    Assertions.checkState(!released);
    removeSpan(span, true);
  }

  @Override
  public synchronized boolean isCached(String key, long position, long length) {
    Assertions.checkState(!released);
    CachedContent cachedContent = index.get(key);
    return cachedContent != null && cachedContent.getCachedBytesLength(position, length) >= length;
  }

  @Override
  public synchronized long getCachedLength(String key, long position, long length) {
    Assertions.checkState(!released);
    CachedContent cachedContent = index.get(key);
    return cachedContent != null ? cachedContent.getCachedBytesLength(position, length) : -length;
  }

  @Override
  public synchronized void setContentLength(String key, long length) throws CacheException {
    ContentMetadataMutations mutations = new ContentMetadataMutations();
    ContentMetadataInternal.setContentLength(mutations, length);
    applyContentMetadataMutations(key, mutations);
  }

  @Override
  public synchronized long getContentLength(String key) {
    return ContentMetadataInternal.getContentLength(getContentMetadata(key));
  }

  @Override
  public synchronized void applyContentMetadataMutations(
      String key, ContentMetadataMutations mutations) throws CacheException {
    Assertions.checkState(!released);
    index.applyContentMetadataMutations(key, mutations);
    index.store();
  }

  @Override
  public synchronized ContentMetadata getContentMetadata(String key) {
    Assertions.checkState(!released);
    return index.getContentMetadata(key);
  }

  /**
   * Returns the cache {@link SimpleCacheSpan} corresponding to the provided lookup {@link
   * SimpleCacheSpan}.
   *
   * <p>If the lookup position is contained by an existing entry in the cache, then the returned
   * {@link SimpleCacheSpan} defines the file in which the data is stored. If the lookup position is
   * not contained by an existing entry, then the returned {@link SimpleCacheSpan} defines the
   * maximum extents of the hole in the cache.
   *
   * @param key The key of the span being requested.
   * @param position The position of the span being requested.
   * @return The corresponding cache {@link SimpleCacheSpan}.
   */
  private SimpleCacheSpan getSpan(String key, long position) throws CacheException {
    CachedContent cachedContent = index.get(key);
    if (cachedContent == null) {
      return SimpleCacheSpan.createOpenHole(key, position);
    }
    while (true) {
      SimpleCacheSpan span = cachedContent.getSpan(position);
      if (span.isCached && !span.file.exists()) {
        // The file has been deleted from under us. It's likely that other files will have been
        // deleted too, so scan the whole in-memory representation.
        removeStaleSpansAndCachedContents();
        continue;
      }
      return span;
    }
  }

  /** Ensures that the cache's in-memory representation has been initialized. */
  private void initialize() {
    if (!cacheDir.exists()) {
      cacheDir.mkdirs();
      return;
    }

    index.load();

    File[] files = cacheDir.listFiles();
    if (files == null) {
      return;
    }
    for (File file : files) {
      if (file.getName().equals(CachedContentIndex.FILE_NAME)) {
        continue;
      }
      SimpleCacheSpan span =
          file.length() > 0 ? SimpleCacheSpan.createCacheEntry(file, index) : null;
      if (span != null) {
        addSpan(span);
      } else {
        file.delete();
      }
    }

    index.removeEmpty();
    try {
      index.store();
    } catch (CacheException e) {
      Log.e(TAG, "Storing index file failed", e);
    }
  }

  /**
   * Adds a cached span to the in-memory representation.
   *
   * @param span The span to be added.
   */
  private void addSpan(SimpleCacheSpan span) {
    index.getOrAdd(span.key).addSpan(span);
    totalSpace += span.length;
    notifySpanAdded(span);
  }

  private void removeSpan(CacheSpan span, boolean removeEmptyCachedContent) throws CacheException {
    CachedContent cachedContent = index.get(span.key);
    if (cachedContent == null || !cachedContent.removeSpan(span)) {
      return;
    }
    totalSpace -= span.length;
    try {
      if (removeEmptyCachedContent) {
        index.maybeRemove(cachedContent.key);
        index.store();
      }
    } finally {
      notifySpanRemoved(span);
    }
  }

  /**
   * Scans all of the cached spans in the in-memory representation, removing any for which files no
   * longer exist.
   */
  private void removeStaleSpansAndCachedContents() throws CacheException {
    ArrayList<CacheSpan> spansToBeRemoved = new ArrayList<>();
    for (CachedContent cachedContent : index.getAll()) {
      for (CacheSpan span : cachedContent.getSpans()) {
        if (!span.file.exists()) {
          spansToBeRemoved.add(span);
        }
      }
    }
    for (int i = 0; i < spansToBeRemoved.size(); i++) {
      // Remove span but not CachedContent to prevent multiple index.store() calls.
      removeSpan(spansToBeRemoved.get(i), false);
    }
    index.removeEmpty();
    index.store();
  }

  private void notifySpanRemoved(CacheSpan span) {
    ArrayList<Listener> keyListeners = listeners.get(span.key);
    if (keyListeners != null) {
      for (int i = keyListeners.size() - 1; i >= 0; i--) {
        keyListeners.get(i).onSpanRemoved(this, span);
      }
    }
    evictor.onSpanRemoved(this, span);
  }

  private void notifySpanAdded(SimpleCacheSpan span) {
    ArrayList<Listener> keyListeners = listeners.get(span.key);
    if (keyListeners != null) {
      for (int i = keyListeners.size() - 1; i >= 0; i--) {
        keyListeners.get(i).onSpanAdded(this, span);
      }
    }
    evictor.onSpanAdded(this, span);
  }

  private void notifySpanTouched(SimpleCacheSpan oldSpan, CacheSpan newSpan) {
    ArrayList<Listener> keyListeners = listeners.get(oldSpan.key);
    if (keyListeners != null) {
      for (int i = keyListeners.size() - 1; i >= 0; i--) {
        keyListeners.get(i).onSpanTouched(this, oldSpan, newSpan);
      }
    }
    evictor.onSpanTouched(this, oldSpan, newSpan);
  }

  private static synchronized boolean lockFolder(File cacheDir) {
    if (cacheFolderLockingDisabled) {
      return true;
    }
    return lockedCacheDirs.add(cacheDir.getAbsoluteFile());
  }

  private static synchronized void unlockFolder(File cacheDir) {
    if (!cacheFolderLockingDisabled) {
      lockedCacheDirs.remove(cacheDir.getAbsoluteFile());
    }
  }
}

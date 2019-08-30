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

import androidx.annotation.VisibleForTesting;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import com.google.android.exoplayer2.upstream.cache.Cache.CacheException;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.AtomicFile;
import com.google.android.exoplayer2.util.ReusableBufferedOutputStream;
import com.google.android.exoplayer2.util.Util;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** Maintains the index of cached content. */
/* package */ class CachedContentIndex {

  public static final String FILE_NAME = "cached_content_index.exi";

  private static final int VERSION = 2;

  private static final int FLAG_ENCRYPTED_INDEX = 1;

  private final HashMap<String, CachedContent> keyToContent;
  /**
   * Maps assigned ids to their corresponding keys. Also contains (id -> null) entries for ids that
   * have been removed from the index since it was last stored. This prevents reuse of these ids,
   * which is necessary to avoid clashes that could otherwise occur as a result of the sequence:
   *
   * <p>[1] (key1, id1) is removed from the in-memory index ... the index is not stored to disk ...
   * [2] id1 is reused for a different key2 ... the index is not stored to disk ... [3] A file for
   * key2 is partially written using a path corresponding to id1 ... the process is killed before
   * the index is stored to disk ... [4] The index is read from disk, causing the partially written
   * file to be incorrectly associated to key1
   *
   * <p>By avoiding id reuse in step [2], a new id2 will be used instead. Step [4] will then delete
   * the partially written file because the index does not contain an entry for id2.
   *
   * <p>When the index is next stored (id -> null) entries are removed, making the ids eligible for
   * reuse.
   */
  private final SparseArray<@NullableType String> idToKey;
  /**
   * Tracks ids for which (id -> null) entries are present in idToKey, so that they can be removed
   * efficiently when the index is next stored.
   */
  private final SparseBooleanArray removedIds;

  private final AtomicFile atomicFile;
  private final Cipher cipher;
  private final SecretKeySpec secretKeySpec;
  private final boolean encrypt;
  private boolean changed;
  private ReusableBufferedOutputStream bufferedOutputStream;

  /**
   * Creates a CachedContentIndex which works on the index file in the given cacheDir.
   *
   * @param cacheDir Directory where the index file is kept.
   */
  public CachedContentIndex(File cacheDir) {
    this(cacheDir, null);
  }

  /**
   * Creates a CachedContentIndex which works on the index file in the given cacheDir.
   *
   * @param cacheDir Directory where the index file is kept.
   * @param secretKey 16 byte AES key for reading and writing the cache index.
   */
  public CachedContentIndex(File cacheDir, byte[] secretKey) {
    this(cacheDir, secretKey, secretKey != null);
  }

  /**
   * Creates a CachedContentIndex which works on the index file in the given cacheDir.
   *
   * @param cacheDir Directory where the index file is kept.
   * @param secretKey 16 byte AES key for reading, and optionally writing, the cache index.
   * @param encrypt Whether the index will be encrypted when written. Must be false if {@code
   *     secretKey} is null.
   */
  public CachedContentIndex(File cacheDir, byte[] secretKey, boolean encrypt) {
    this.encrypt = encrypt;
    if (secretKey != null) {
      Assertions.checkArgument(secretKey.length == 16);
      try {
        cipher = getCipher();
        secretKeySpec = new SecretKeySpec(secretKey, "AES");
      } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
        throw new IllegalStateException(e); // Should never happen.
      }
    } else {
      Assertions.checkState(!encrypt);
      cipher = null;
      secretKeySpec = null;
    }
    keyToContent = new HashMap<>();
    idToKey = new SparseArray<>();
    removedIds = new SparseBooleanArray();
    atomicFile = new AtomicFile(new File(cacheDir, FILE_NAME));
  }

  /** Loads the index file. */
  public void load() {
    Assertions.checkState(!changed);
    if (!readFile()) {
      atomicFile.delete();
      keyToContent.clear();
      idToKey.clear();
    }
  }

  /** Stores the index data to index file if there is a change. */
  public void store() throws CacheException {
    if (!changed) {
      return;
    }
    writeFile();
    changed = false;
    // Make ids that were removed since the index was last stored eligible for re-use.
    int removedIdCount = removedIds.size();
    for (int i = 0; i < removedIdCount; i++) {
      idToKey.remove(removedIds.keyAt(i));
    }
    removedIds.clear();
  }

  /**
   * Adds the given key to the index if it isn't there already.
   *
   * @param key The cache key that uniquely identifies the original stream.
   * @return A new or existing CachedContent instance with the given key.
   */
  public CachedContent getOrAdd(String key) {
    CachedContent cachedContent = keyToContent.get(key);
    return cachedContent == null ? addNew(key) : cachedContent;
  }

  /** Returns a CachedContent instance with the given key or null if there isn't one. */
  public CachedContent get(String key) {
    return keyToContent.get(key);
  }

  /**
   * Returns a Collection of all CachedContent instances in the index. The collection is backed by
   * the {@code keyToContent} map, so changes to the map are reflected in the collection, and
   * vice-versa. If the map is modified while an iteration over the collection is in progress
   * (except through the iterator's own remove operation), the results of the iteration are
   * undefined.
   */
  public Collection<CachedContent> getAll() {
    return keyToContent.values();
  }

  /** Returns an existing or new id assigned to the given key. */
  public int assignIdForKey(String key) {
    return getOrAdd(key).id;
  }

  /** Returns the key which has the given id assigned. */
  public String getKeyForId(int id) {
    return idToKey.get(id);
  }

  /** Removes {@link CachedContent} with the given key from index if it's empty and not locked. */
  public void maybeRemove(String key) {
    CachedContent cachedContent = keyToContent.get(key);
    if (cachedContent != null && cachedContent.isEmpty() && !cachedContent.isLocked()) {
      keyToContent.remove(key);
      changed = true;
      // Keep an entry in idToKey to stop the id from being reused until the index is next stored.
      idToKey.put(cachedContent.id, /* value= */ null);
      // Track that the entry should be removed from idToKey when the index is next stored.
      removedIds.put(cachedContent.id, /* value= */ true);
    }
  }

  /** Removes empty and not locked {@link CachedContent} instances from index. */
  public void removeEmpty() {
    String[] keys = new String[keyToContent.size()];
    keyToContent.keySet().toArray(keys);
    for (String key : keys) {
      maybeRemove(key);
    }
  }

  /**
   * Returns a set of all content keys. The set is backed by the {@code keyToContent} map, so
   * changes to the map are reflected in the set, and vice-versa. If the map is modified while an
   * iteration over the set is in progress (except through the iterator's own remove operation), the
   * results of the iteration are undefined.
   */
  public Set<String> getKeys() {
    return keyToContent.keySet();
  }

  /**
   * Applies {@code mutations} to the {@link ContentMetadata} for the given key. A new {@link
   * CachedContent} is added if there isn't one already with the given key.
   */
  public void applyContentMetadataMutations(String key, ContentMetadataMutations mutations) {
    CachedContent cachedContent = getOrAdd(key);
    if (cachedContent.applyMetadataMutations(mutations)) {
      changed = true;
    }
  }

  /** Returns a {@link ContentMetadata} for the given key. */
  public ContentMetadata getContentMetadata(String key) {
    CachedContent cachedContent = get(key);
    return cachedContent != null ? cachedContent.getMetadata() : DefaultContentMetadata.EMPTY;
  }

  private boolean readFile() {
    DataInputStream input = null;
    try {
      InputStream inputStream = new BufferedInputStream(atomicFile.openRead());
      input = new DataInputStream(inputStream);
      int version = input.readInt();
      if (version < 0 || version > VERSION) {
        return false;
      }

      int flags = input.readInt();
      if ((flags & FLAG_ENCRYPTED_INDEX) != 0) {
        if (cipher == null) {
          return false;
        }
        byte[] initializationVector = new byte[16];
        input.readFully(initializationVector);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(initializationVector);
        try {
          cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
          throw new IllegalStateException(e);
        }
        input = new DataInputStream(new CipherInputStream(inputStream, cipher));
      } else if (encrypt) {
        changed = true; // Force index to be rewritten encrypted after read.
      }

      int count = input.readInt();
      int hashCode = 0;
      for (int i = 0; i < count; i++) {
        CachedContent cachedContent = CachedContent.readFromStream(version, input);
        add(cachedContent);
        hashCode += cachedContent.headerHashCode(version);
      }
      int fileHashCode = input.readInt();
      boolean isEOF = input.read() == -1;
      if (fileHashCode != hashCode || !isEOF) {
        return false;
      }
    } catch (IOException e) {
      return false;
    } finally {
      if (input != null) {
        Util.closeQuietly(input);
      }
    }
    return true;
  }

  private void writeFile() throws CacheException {
    DataOutputStream output = null;
    try {
      OutputStream outputStream = atomicFile.startWrite();
      if (bufferedOutputStream == null) {
        bufferedOutputStream = new ReusableBufferedOutputStream(outputStream);
      } else {
        bufferedOutputStream.reset(outputStream);
      }
      output = new DataOutputStream(bufferedOutputStream);
      output.writeInt(VERSION);

      int flags = encrypt ? FLAG_ENCRYPTED_INDEX : 0;
      output.writeInt(flags);

      if (encrypt) {
        byte[] initializationVector = new byte[16];
        new Random().nextBytes(initializationVector);
        output.write(initializationVector);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(initializationVector);
        try {
          cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
          throw new IllegalStateException(e); // Should never happen.
        }
        output.flush();
        output = new DataOutputStream(new CipherOutputStream(bufferedOutputStream, cipher));
      }

      output.writeInt(keyToContent.size());
      int hashCode = 0;
      for (CachedContent cachedContent : keyToContent.values()) {
        cachedContent.writeToStream(output);
        hashCode += cachedContent.headerHashCode(VERSION);
      }
      output.writeInt(hashCode);
      atomicFile.endWrite(output);
      // Avoid calling close twice. Duplicate CipherOutputStream.close calls did
      // not used to be no-ops: https://android-review.googlesource.com/#/c/272799/
      output = null;
    } catch (IOException e) {
      throw new CacheException(e);
    } finally {
      Util.closeQuietly(output);
    }
  }

  private CachedContent addNew(String key) {
    int id = getNewId(idToKey);
    CachedContent cachedContent = new CachedContent(id, key);
    add(cachedContent);
    changed = true;
    return cachedContent;
  }

  private void add(CachedContent cachedContent) {
    keyToContent.put(cachedContent.key, cachedContent);
    idToKey.put(cachedContent.id, cachedContent.key);
  }

  private static Cipher getCipher() throws NoSuchPaddingException, NoSuchAlgorithmException {
    // Workaround for https://issuetracker.google.com/issues/36976726
    if (Util.SDK_INT == 18) {
      try {
        return Cipher.getInstance("AES/CBC/PKCS5PADDING", "BC");
      } catch (Throwable ignored) {
        // ignored
      }
    }
    return Cipher.getInstance("AES/CBC/PKCS5PADDING");
  }

  /**
   * Returns an id which isn't used in the given array. If the maximum id in the array is smaller
   * than {@link java.lang.Integer#MAX_VALUE} it just returns the next bigger integer. Otherwise it
   * returns the smallest unused non-negative integer.
   */
  @VisibleForTesting
  public static int getNewId(SparseArray<String> idToKey) {
    int size = idToKey.size();
    int id = size == 0 ? 0 : (idToKey.keyAt(size - 1) + 1);
    if (id < 0) { // In case if we pass max int value.
      // TODO optimization: defragmentation or binary search?
      for (id = 0; id < size; id++) {
        if (id != idToKey.keyAt(id)) {
          break;
        }
      }
    }
    return id;
  }

}

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

import android.util.Log;
import android.util.SparseArray;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.upstream.cache.Cache.CacheException;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.AtomicFile;
import org.telegram.messenger.exoplayer2.util.ReusableBufferedOutputStream;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * This class maintains the index of cached content.
 */
/*package*/ class CachedContentIndex {

  public static final String FILE_NAME = "cached_content_index.exi";

  private static final int VERSION = 1;

  private static final int FLAG_ENCRYPTED_INDEX = 1;

  private static final String TAG = "CachedContentIndex";

  private final HashMap<String, CachedContent> keyToContent;
  private final SparseArray<String> idToKey;
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
   * @param encrypt When false, a plaintext index will be written.
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
      cipher = null;
      secretKeySpec = null;
    }
    keyToContent = new HashMap<>();
    idToKey = new SparseArray<>();
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
  }

  /**
   * Adds the given key to the index if it isn't there already.
   *
   * @param key The cache key that uniquely identifies the original stream.
   * @return A new or existing CachedContent instance with the given key.
   */
  public CachedContent add(String key) {
    CachedContent cachedContent = keyToContent.get(key);
    if (cachedContent == null) {
      cachedContent = addNew(key, C.LENGTH_UNSET);
    }
    return cachedContent;
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
    return add(key).id;
  }

  /** Returns the key which has the given id assigned. */
  public String getKeyForId(int id) {
    return idToKey.get(id);
  }

  /**
   * Removes {@link CachedContent} with the given key from index. It shouldn't contain any spans.
   *
   * @throws IllegalStateException If {@link CachedContent} isn't empty.
   */
  public void removeEmpty(String key) {
    CachedContent cachedContent = keyToContent.remove(key);
    if (cachedContent != null) {
      Assertions.checkState(cachedContent.isEmpty());
      idToKey.remove(cachedContent.id);
      changed = true;
    }
  }

  /** Removes empty {@link CachedContent} instances from index. */
  public void removeEmpty() {
    LinkedList<String> cachedContentToBeRemoved = new LinkedList<>();
    for (CachedContent cachedContent : keyToContent.values()) {
      if (cachedContent.isEmpty()) {
        cachedContentToBeRemoved.add(cachedContent.key);
      }
    }
    for (String key : cachedContentToBeRemoved) {
      removeEmpty(key);
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
   * Sets the content length for the given key. A new {@link CachedContent} is added if there isn't
   * one already with the given key.
   */
  public void setContentLength(String key, long length) {
    CachedContent cachedContent = get(key);
    if (cachedContent != null) {
      if (cachedContent.getLength() != length) {
        cachedContent.setLength(length);
        changed = true;
      }
    } else {
      addNew(key, length);
    }
  }

  /**
   * Returns the content length for the given key if one set, or {@link
   * org.telegram.messenger.exoplayer2.C#LENGTH_UNSET} otherwise.
   */
  public long getContentLength(String key) {
    CachedContent cachedContent = get(key);
    return cachedContent == null ? C.LENGTH_UNSET : cachedContent.getLength();
  }

  private boolean readFile() {
    DataInputStream input = null;
    try {
      InputStream inputStream = new BufferedInputStream(atomicFile.openRead());
      input = new DataInputStream(inputStream);
      int version = input.readInt();
      if (version != VERSION) {
        // Currently there is no other version
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
      } else {
        if (cipher != null) {
          changed = true; // Force index to be rewritten encrypted after read.
        }
      }

      int count = input.readInt();
      int hashCode = 0;
      for (int i = 0; i < count; i++) {
        CachedContent cachedContent = new CachedContent(input);
        add(cachedContent);
        hashCode += cachedContent.headerHashCode();
      }
      if (input.readInt() != hashCode) {
        return false;
      }
    } catch (FileNotFoundException e) {
      return false;
    } catch (IOException e) {
      Log.e(TAG, "Error reading cache content index file.", e);
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

      boolean writeEncrypted = encrypt && cipher != null;
      int flags = writeEncrypted ? FLAG_ENCRYPTED_INDEX : 0;
      output.writeInt(flags);

      if (writeEncrypted) {
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
        hashCode += cachedContent.headerHashCode();
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

  private void add(CachedContent cachedContent) {
    keyToContent.put(cachedContent.key, cachedContent);
    idToKey.put(cachedContent.id, cachedContent.key);
  }

  /** Adds the given CachedContent to the index. */
  /*package*/ void addNew(CachedContent cachedContent) {
    add(cachedContent);
    changed = true;
  }

  private CachedContent addNew(String key, long length) {
    int id = getNewId(idToKey);
    CachedContent cachedContent = new CachedContent(id, key, length);
    addNew(cachedContent);
    return cachedContent;
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
  //@VisibleForTesting
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

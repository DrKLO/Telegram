/*
 * This file is a part of X-Core
 * Copyright © Vyacheslav Krylov 2014
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
 *
 * File created on 26/02/2018
 */

package me.vkryl.core.reference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.vkryl.core.util.CombinedIterator;

public class ReferenceMap<K, T> {
  public interface FullnessListener <KK,TT> {
    void onFullnessStateChanged (ReferenceMap<KK,TT> list, boolean isFull);
  }

  private final boolean isThreadSafe, cacheIterator;
  private final @Nullable ReferenceList.FullnessListener fullnessListenerHelper;
  private int fullnessCounter;

  private ReferenceList<T> reuse;

  public ReferenceMap () {
    this(false, true, null);
  }

  public ReferenceMap (final boolean isThreadSafe) {
    this(isThreadSafe, true, null);
  }

  public ReferenceMap (final boolean isThreadSafe, boolean cacheIterator, final @Nullable FullnessListener<K, T> fullnessListener) {
    this.isThreadSafe = isThreadSafe;
    this.cacheIterator = cacheIterator;
    if (fullnessListener != null) {
      fullnessListenerHelper = (list, isFull) -> {
        synchronized (fullnessListener) {
          if (isFull) {
            if (fullnessCounter++ == 0) {
              fullnessListener.onFullnessStateChanged(ReferenceMap.this, true);
            }
          } else {
            if (--fullnessCounter == 0) {
              fullnessListener.onFullnessStateChanged(ReferenceMap.this, false);
            }
          }
        }
      };
    } else {
      fullnessListenerHelper = null;
    }
  }

  protected final Map<K, ReferenceList<T>> map = new HashMap<>();

  public final boolean add (K key, @NonNull T item) {
    synchronized (map) {
      ReferenceList<T> list = map.get(key);
      if (list == null) {
        if (reuse != null) {
          list = reuse;
          reuse = list.next;
          list.next = null;
        } else {
          list = new ReferenceList<>(isThreadSafe, cacheIterator, fullnessListenerHelper);
        }
        map.put(key, list);
      }
      return list.add(item);
    }
  }

  public final boolean has (K key) {
    synchronized (map) {
      ReferenceList<T> list = map.get(key);
      return list != null && !list.isEmpty();
    }
  }

  @Nullable
  public final ReferenceList<T> removeAll (K key) {
    synchronized (map) {
      return map.remove(key);
    }
  }

  public final void remove (K key, @NonNull T item) {
    synchronized (map) {
      ReferenceList<T> list = map.get(key);
      if (list != null) {
        list.remove(item);
        if (list.isEmpty()) {
          map.remove(key);
          list.next = reuse;
          reuse = list;
        }
      }
    }
  }

  public final void move (K oldKey, K newKey) {
    synchronized (map) {
      ReferenceList<T> oldList = map.remove(oldKey);
      if (oldList == null) {
        return;
      }
      ReferenceList<T> newList = map.get(newKey);
      if (newList != null) {
        newList.addAll(oldList);
        oldList.clear();
        oldList.next = reuse;
        reuse = oldList;
      } else {
        map.put(newKey, oldList);
      }
    }
  }

  public final void clear () {
    synchronized (map) {
      for (Map.Entry<K, ReferenceList<T>> entry : map.entrySet()) {
        ReferenceList<T> list = entry.getValue();
        list.clear();
        list.next = reuse;
        reuse = list;
      }
      map.clear();
    }
  }

  public final @Nullable Iterator<T> iterator (K key) {
    synchronized (map) {
      ReferenceList<T> list = map.get(key);
      return list != null ? list.iterator() : null;
    }
  }

  public final @Nullable Iterator<T> combinedIterator () {
    // Note: when the same object is registered with multiple keys,
    // it will be returned via iterator.next() the same amount of times as occurrences in the map
    synchronized (map) {
      if (map.isEmpty()) {
        return null;
      }
      List<Iterator<T>> iterators = new ArrayList<>(map.size());
      for (ReferenceList<T> value : map.values()) {
        iterators.add(value.iterator());
      }
      return new CombinedIterator<>(iterators);
    }
  }

  /**
   * Requires prior synchronization on ReferenceMap instance
   */
  public final @Nullable Set<K> keySetUnchecked () {
    return map.isEmpty() ? null : map.keySet();
  }

  public final Object mapUnchecked () {
    return map;
  }
}

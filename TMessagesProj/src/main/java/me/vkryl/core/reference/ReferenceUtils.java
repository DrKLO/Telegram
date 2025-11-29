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

import androidx.collection.LongSparseArray;
import androidx.collection.SparseArrayCompat;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ReferenceUtils {
  public static <T> boolean removeReference (List<Reference<T>> list, T data) {
    final int size = list.size();
    for (int i = size - 1; i >= 0; i--) {
      T oldData = list.get(i).get();
      if (oldData == null || oldData == data) {
        list.remove(i);
        return true;
      }
    }
    return false;
  }

  public static <T> boolean removeReference (LongSparseArray<List<Reference<T>>> array, T data, long key) {
    final List<Reference<T>> list = array.get(key);
    if (list != null) {
      boolean removed = removeReference(list, data);
      if (list.isEmpty()) {
        array.remove(key);
      }
      return removed;
    }
    return false;
  }

  public static <K,T> boolean removeReference (HashMap<K, List<Reference<T>>> array, T data, K key) {
    final List<Reference<T>> list = array.get(key);
    if (list != null) {
      boolean removed = removeReference(list, data);
      if (list.isEmpty()) {
        array.remove(key);
      }
      return removed;
    }
    return false;
  }

  public static <T> boolean removeReference (SparseArrayCompat<List<Reference<T>>> array, T data, int key) {
    final List<Reference<T>> list = array.get(key);
    if (list != null) {
      boolean removed = removeReference(list, data);
      if (list.isEmpty()) {
        array.remove(key);
      }
      return removed;
    }
    return false;
  }

  public static <T> boolean addReference (SparseArrayCompat<List<Reference<T>>> array, T data, int key) {
    final List<Reference<T>> list = array.get(key);
    if (list != null) {
      return addReference(list, data);
    } else {
      final List<Reference<T>> newList = new ArrayList<>();
      newList.add(new WeakReference<>(data));
      array.put(key, newList);
      return true;
    }
  }

  public static <T> boolean addReference (List<Reference<T>> list, T item) {
    boolean found = false;
    final int size = list.size();
    for (int i = size - 1; i >= 0; i--) {
      Reference<T> reference = list.get(i);
      T oldItem = reference != null ? reference.get() : null;
      if (oldItem == null) {
        list.remove(i);
      } else if (oldItem == item) {
        found = true;
      }
    }
    if (!found) {
      list.add(new WeakReference<>(item));
      return true;
    }
    return false;
  }

  public static <T> boolean addReference (ReferenceCreator<T> creator, List<Reference<T>> list, T item) {
    boolean found = false;
    final int size = list.size();
    for (int i = size - 1; i >= 0; i--) {
      Reference<T> reference = list.get(i);
      T oldItem = reference != null ? reference.get() : null;
      if (oldItem == null) {
        list.remove(i);
      } else if (oldItem == item) {
        found = true;
      }
    }
    if (!found) {
      Reference<T> reference = creator.newReference(item);
      if (reference == null)
        throw new IllegalArgumentException();
      list.add(reference);
      return true;
    }
    return false;
  }

  public static <T> void gcReferenceList (List<Reference<T>> list) {
    if (list != null) {
      final int size = list.size() - 1;
      for (int i = size - 1; i >= 0; i--) {
        T data = list.get(i).get();
        if (data == null) {
          list.remove(i);
        }
      }
    }
  }

  public static <T> void removeListIfEmpty (LongSparseArray<List<Reference<T>>> array, List<Reference<T>> list, long key) {
    if (array != null && list != null && list.isEmpty()) {
      array.remove(key);
    }
  }

  public static <T> void removeListIfEmpty (SparseArrayCompat<List<Reference<T>>> array, List<Reference<T>> list, int key) {
    if (array != null && list != null && list.isEmpty()) {
      array.remove(key);
    }
  }
}

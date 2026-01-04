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

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Semaphore;

/**
 * Object that keeps references to items and allows modifying itself during iteration.
 *
 * All methods are thread-safe, except {@link #iterator()}.
 * If there's chance you will call {@link #iterator()} from multiple threads, pass true to a constructor.
 *
 * By default, {@link ReferenceCreator#newReference(Object)} creates {@link java.lang.ref.WeakReference}.
 * However, this behavior can be overridden.
 *
 * {@link #iterator()} will throw {@link IllegalStateException} if previous iteration has not been completed
 * {@link #iterator()} will wait until previous iteration finishes, when using {@code isThreadSafe} in constructor
 * {@link #iterator()} will throw {@link IllegalStateException} when {@link InterruptedException} gets caught
 * {@link #iterator()} will cause deadlock or ANR if you try to iterate inside iteration
 *
 * {@link Iterator#next()} iterates from newer to older items
 * {@link Iterator#next()} will not return null
 * {@link Iterator#next()} will not return items removed during iteration
 * {@link Iterator#next()} will not return items added during iteration, they will be available on the next iteration
 */

public final class ReferenceList<T> implements Iterable<T>, ReferenceCreator<T> {
  public interface FullnessListener {
    void onFullnessStateChanged (ReferenceList<?> list, boolean isFull);
  }

  private final boolean cacheIterator;

  private final List<Reference<T>> items;

  private final List<Reference<T>> itemsToRemove = new ArrayList<>();
  private final List<Reference<T>> itemsToAdd = new ArrayList<>();

  private boolean isLocked;
  private final Semaphore semaphore;

  private boolean isFull;
  private final @Nullable FullnessListener fullnessListener;

  ReferenceList<T> next; // Used by ReferenceMap<T>

  public ReferenceList () {
    this(false, true, null);
  }

  public ReferenceList (boolean isThreadSafe) {
    this(isThreadSafe, true, null);
  }

  public ReferenceList (boolean isThreadSafe, boolean cacheIterator, @Nullable FullnessListener fullnessListener) {
    this.semaphore = isThreadSafe ? new Semaphore(1) : null;
    this.cacheIterator = cacheIterator;
    this.items = new ArrayList<>();
    this.fullnessListener = fullnessListener;
  }

  private void checkFull () {
    if (fullnessListener != null) {
      boolean isFull = !items.isEmpty();
      if (this.isFull != isFull) {
        this.isFull = isFull;
        fullnessListener.onFullnessStateChanged(this, isFull);
      }
    }
  }

  private void lock () {
    if (isLocked)
      throw new IllegalStateException();
    isLocked = true;
  }

  private void unlock () {
    if (!isLocked)
      throw new IllegalStateException();
    isLocked = false;
    if (!itemsToRemove.isEmpty()) {
      items.removeAll(itemsToRemove);
      itemsToRemove.clear();
    }
    if (!itemsToAdd.isEmpty()) {
      items.addAll(itemsToAdd);
      itemsToAdd.clear();
    }
    checkFull();
  }

  private int indexOf (T item) {
    if (item == null) {
      return -1;
    }
    final int size = items.size();
    for (int i = size - 1; i >= 0; i--) {
      T existingItem = items.get(i).get();
      if (existingItem == item) {
        return i;
      }
    }
    return -1;
  }

  /**
   * @return Number of added elements
   * */
  public final int addAll (ReferenceList<T> itemsToAdd) {
    int count = 0;
    for (T item : itemsToAdd) {
      if (add(item))
        count++;
    }
    return count;
  }

  /**
   * @return False when item is already present in the list
   */
  public final boolean add (@NonNull T itemToAdd) {
    synchronized (items) {
      int i = indexOf(itemToAdd);
      if (i != -1) {
        return false;
      }
      if (isLocked) {
        boolean isAdded = ReferenceUtils.addReference(this, itemsToAdd, itemToAdd);
        ReferenceUtils.removeReference(itemsToRemove, itemToAdd);
        return isAdded;
      } else {
        Reference<T> reference = newReference(itemToAdd);
        items.add(reference);
        checkFull();
        return true;
      }
    }
  }

  public final boolean remove (@NonNull T itemToRemove) {
    synchronized (items) {
      int i = indexOf(itemToRemove);
      if (i == -1) {
        return false;
      }
      if (isLocked) {
        Reference<T> item = items.get(i);
        if (!itemsToRemove.contains(item)) {
          itemsToRemove.add(item);
        }
        ReferenceUtils.removeReference(itemsToAdd, item.get());
      } else {
        items.remove(i);
        checkFull();
      }
      return true;
    }
  }

  public final void clear () {
    synchronized (items) {
      if (isLocked) {
        for (Reference<T> item : items) {
          if (!itemsToRemove.contains(item)) {
            itemsToRemove.add(item);
          }
          ReferenceUtils.removeReference(itemsToAdd, item.get());
        }
      } else {
        items.clear();
        checkFull();
      }
    }
  }

  public final boolean isEmpty () {
    synchronized (items) {
      if (isLocked) {
        return items.isEmpty() && itemsToAdd.isEmpty();
      } else {
        ReferenceUtils.gcReferenceList(items);
        return items.isEmpty();
      }
    }
  }

  public final boolean hasReferences () {
    synchronized (items) {
      if (isLocked) {
        return items.size() != 0 || itemsToAdd.size() != 0;
      } else {
        return !items.isEmpty();
      }
    }
  }

  private Itr itr;

  @NonNull
  @Override
  public final Iterator<T> iterator () {
    if (semaphore != null) {
      try {
        semaphore.acquire();
      } catch (InterruptedException t) {
        throw new IllegalStateException();
      }
    }
    synchronized (items) {
      if (cacheIterator) {
        lock();
        if (itr == null) {
          itr = new Itr();
        } else {
          itr.index = items.size();
          itr.nextItem = null;
        }
        return itr;
      } else if (items.isEmpty()) {
        return Collections.emptyIterator();
      } else {
        return new Itr();
      }
    }
  }

  private final class Itr implements Iterator<T> {
    private int index = items.size();
    private T nextItem;

    @Override
    public final boolean hasNext () {
      synchronized (items) {
        nextItem = null;
        while (nextItem == null && index > 0) {
          Reference<T> reference = items.get(--index);
          T item = reference.get();
          if (item != null && !itemsToRemove.contains(reference)) {
            nextItem = item;
            break;
          }
        }
        if (nextItem == null) {
          if (cacheIterator) {
            unlock();
          }
        }
      }
      if (nextItem == null) {
        if (semaphore != null) {
          semaphore.release();
        }
        return false;
      }
      return true;
    }

    @NonNull
    @Override
    public final T next () {
      if (nextItem == null) {
        throw new NoSuchElementException();
      }
      return nextItem;
    }
  }
}

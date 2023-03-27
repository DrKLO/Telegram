/*
 * Copyright (C) 2020 The Android Open Source Project
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
 */
package com.google.android.exoplayer2.util;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An unordered collection of elements that allows duplicates, but also allows access to a set of
 * unique elements.
 *
 * <p>This class is thread-safe using the same method as {@link
 * java.util.concurrent.CopyOnWriteArrayList}. Mutation methods cause the underlying data to be
 * copied. {@link #elementSet()} and {@link #iterator()} return snapshots that are unaffected by
 * subsequent mutations.
 *
 * <p>Iterating directly on this class reveals duplicate elements. Unique elements can be accessed
 * via {@link #elementSet()}. Iteration order for both of these is not defined.
 *
 * @param <E> The type of element being stored.
 */
// Intentionally extending @NonNull-by-default Object to disallow @Nullable E types.
@SuppressWarnings("TypeParameterExplicitlyExtendsObject")
public final class CopyOnWriteMultiset<E extends Object> implements Iterable<E> {

  private final Object lock;

  @GuardedBy("lock")
  private final Map<E, Integer> elementCounts;

  @GuardedBy("lock")
  private Set<E> elementSet;

  @GuardedBy("lock")
  private List<E> elements;

  public CopyOnWriteMultiset() {
    lock = new Object();
    elementCounts = new HashMap<>();
    elementSet = Collections.emptySet();
    elements = Collections.emptyList();
  }

  /**
   * Adds {@code element} to the multiset.
   *
   * @param element The element to be added.
   */
  public void add(E element) {
    synchronized (lock) {
      List<E> elements = new ArrayList<>(this.elements);
      elements.add(element);
      this.elements = Collections.unmodifiableList(elements);

      @Nullable Integer count = elementCounts.get(element);
      if (count == null) {
        Set<E> elementSet = new HashSet<>(this.elementSet);
        elementSet.add(element);
        this.elementSet = Collections.unmodifiableSet(elementSet);
      }
      elementCounts.put(element, count != null ? count + 1 : 1);
    }
  }

  /**
   * Removes {@code element} from the multiset.
   *
   * @param element The element to be removed.
   */
  public void remove(E element) {
    synchronized (lock) {
      @Nullable Integer count = elementCounts.get(element);
      if (count == null) {
        return;
      }

      List<E> elements = new ArrayList<>(this.elements);
      elements.remove(element);
      this.elements = Collections.unmodifiableList(elements);

      if (count == 1) {
        elementCounts.remove(element);
        Set<E> elementSet = new HashSet<>(this.elementSet);
        elementSet.remove(element);
        this.elementSet = Collections.unmodifiableSet(elementSet);
      } else {
        elementCounts.put(element, count - 1);
      }
    }
  }

  /**
   * Returns a snapshot of the unique elements currently in this multiset.
   *
   * <p>Changes to the underlying multiset are not reflected in the returned value.
   *
   * @return An unmodifiable set containing the unique elements in this multiset.
   */
  public Set<E> elementSet() {
    synchronized (lock) {
      return elementSet;
    }
  }

  /**
   * Returns an iterator over a snapshot of all the elements currently in this multiset (including
   * duplicates).
   *
   * <p>Changes to the underlying multiset are not reflected in the returned value.
   *
   * @return An unmodifiable iterator over all the elements in this multiset (including duplicates).
   */
  @Override
  public Iterator<E> iterator() {
    synchronized (lock) {
      return elements.iterator();
    }
  }

  /** Returns the number of occurrences of an element in this multiset. */
  public int count(E element) {
    synchronized (lock) {
      return elementCounts.containsKey(element) ? elementCounts.get(element) : 0;
    }
  }
}

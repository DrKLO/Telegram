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
 * File created on 14/09/2023
 */
package me.vkryl.core.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class CombinedIterator<T> implements Iterator<T> {
  private final Iterator<Iterator<T>> iterators;
  private Iterator<T> current;

  public CombinedIterator (Iterable<Iterator<T>> iterators) {
    this(iterators.iterator());
  }

  public CombinedIterator (Iterator<Iterator<T>> iterators) {
    this.iterators = iterators;
  }

  @Override
  public boolean hasNext () {
    while (current == null || !current.hasNext()) {
      if (!iterators.hasNext()) {
        current = null;
        return false;
      }
      current = iterators.next();
    }
    return true;
  }

  @Override
  public T next () {
    if (current == null) {
      throw new NoSuchElementException();
    }
    return current.next();
  }
}

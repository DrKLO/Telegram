/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.offline;

/** Provides random read-write access to the result set returned by a database query. */
interface DownloadStateCursor {

  /** Returns the DownloadState at the current position. */
  DownloadState getDownloadState();

  /** Returns the numbers of DownloadStates in the cursor. */
  int getCount();

  /**
   * Returns the current position of the cursor in the DownloadState set. The value is zero-based.
   * When the DownloadState set is first returned the cursor will be at positon -1, which is before
   * the first DownloadState. After the last DownloadState is returned another call to next() will
   * leave the cursor past the last entry, at a position of count().
   *
   * @return the current cursor position.
   */
  int getPosition();

  /**
   * Move the cursor to an absolute position. The valid range of values is -1 &lt;= position &lt;=
   * count.
   *
   * <p>This method will return true if the request destination was reachable, otherwise, it returns
   * false.
   *
   * @param position the zero-based position to move to.
   * @return whether the requested move fully succeeded.
   */
  boolean moveToPosition(int position);

  /**
   * Move the cursor to the first DownloadState.
   *
   * <p>This method will return false if the cursor is empty.
   *
   * @return whether the move succeeded.
   */
  default boolean moveToFirst() {
    return moveToPosition(0);
  }

  /**
   * Move the cursor to the last DownloadState.
   *
   * <p>This method will return false if the cursor is empty.
   *
   * @return whether the move succeeded.
   */
  default boolean moveToLast() {
    return moveToPosition(getCount() - 1);
  }

  /**
   * Move the cursor to the next DownloadState.
   *
   * <p>This method will return false if the cursor is already past the last entry in the result
   * set.
   *
   * @return whether the move succeeded.
   */
  default boolean moveToNext() {
    return moveToPosition(getPosition() + 1);
  }

  /**
   * Move the cursor to the previous DownloadState.
   *
   * <p>This method will return false if the cursor is already before the first entry in the result
   * set.
   *
   * @return whether the move succeeded.
   */
  default boolean moveToPrevious() {
    return moveToPosition(getPosition() - 1);
  }

  /** Returns whether the cursor is pointing to the first DownloadState. */
  default boolean isFirst() {
    return getPosition() == 0 && getCount() != 0;
  }

  /** Returns whether the cursor is pointing to the last DownloadState. */
  default boolean isLast() {
    int count = getCount();
    return getPosition() == (count - 1) && count != 0;
  }

  /** Returns whether the cursor is pointing to the position before the first DownloadState. */
  default boolean isBeforeFirst() {
    if (getCount() == 0) {
      return true;
    }
    return getPosition() == -1;
  }

  /** Returns whether the cursor is pointing to the position after the last DownloadState. */
  default boolean isAfterLast() {
    if (getCount() == 0) {
      return true;
    }
    return getPosition() == getCount();
  }

  /** Closes the Cursor, releasing all of its resources and making it completely invalid. */
  void close();

  /** Returns whether the cursor is closed */
  boolean isClosed();
}

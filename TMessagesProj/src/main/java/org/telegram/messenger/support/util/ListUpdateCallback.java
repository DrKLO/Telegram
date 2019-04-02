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
package org.telegram.messenger.support.util;

/**
 * An interface that can receive Update operations that are applied to a list.
 * <p>
 * This class can be used together with DiffUtil to detect changes between two lists.
 */
public interface ListUpdateCallback {
    /**
     * Called when {@code count} number of items are inserted at the given position.
     *
     * @param position The position of the new item.
     * @param count    The number of items that have been added.
     */
    void onInserted(int position, int count);

    /**
     * Called when {@code count} number of items are removed from the given position.
     *
     * @param position The position of the item which has been removed.
     * @param count    The number of items which have been removed.
     */
    void onRemoved(int position, int count);

    /**
     * Called when an item changes its position in the list.
     *
     * @param fromPosition The previous position of the item before the move.
     * @param toPosition   The new position of the item.
     */
    void onMoved(int fromPosition, int toPosition);

    /**
     * Called when {@code count} number of items are updated at the given position.
     *
     * @param position The position of the item which has been updated.
     * @param count    The number of items which has changed.
     */
    void onChanged(int position, int count, Object payload);
}

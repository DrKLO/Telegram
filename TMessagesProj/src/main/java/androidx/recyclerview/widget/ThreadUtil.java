/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.recyclerview.widget;

interface ThreadUtil<T> {

    interface MainThreadCallback<T> {

        void updateItemCount(int generation, int itemCount);

        void addTile(int generation, TileList.Tile<T> tile);

        void removeTile(int generation, int position);
    }

    interface BackgroundCallback<T> {

        void refresh(int generation);

        void updateRange(int rangeStart, int rangeEnd, int extRangeStart, int extRangeEnd,
                         int scrollHint);

        void loadTile(int position, int scrollHint);

        void recycleTile(TileList.Tile<T> tile);
    }

    MainThreadCallback<T> getMainThreadProxy(MainThreadCallback<T> callback);

    BackgroundCallback<T> getBackgroundProxy(BackgroundCallback<T> callback);
}

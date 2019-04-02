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

package org.telegram.messenger.support.util;

import android.support.annotation.NonNull;
import org.telegram.messenger.support.widget.RecyclerView;

/**
 * ListUpdateCallback that dispatches update events to the given adapter.
 *
 * @see DiffUtil.DiffResult#dispatchUpdatesTo(RecyclerView.Adapter)
 */
public final class AdapterListUpdateCallback implements ListUpdateCallback {
    @NonNull
    private final RecyclerView.Adapter mAdapter;

    /**
     * Creates an AdapterListUpdateCallback that will dispatch update events to the given adapter.
     *
     * @param adapter The Adapter to send updates to.
     */
    public AdapterListUpdateCallback(@NonNull RecyclerView.Adapter adapter) {
        mAdapter = adapter;
    }

    /** {@inheritDoc} */
    @Override
    public void onInserted(int position, int count) {
        mAdapter.notifyItemRangeInserted(position, count);
    }

    /** {@inheritDoc} */
    @Override
    public void onRemoved(int position, int count) {
        mAdapter.notifyItemRangeRemoved(position, count);
    }

    /** {@inheritDoc} */
    @Override
    public void onMoved(int fromPosition, int toPosition) {
        mAdapter.notifyItemMoved(fromPosition, toPosition);
    }

    /** {@inheritDoc} */
    @Override
    public void onChanged(int position, int count, Object payload) {
        mAdapter.notifyItemRangeChanged(position, count, payload);
    }
}

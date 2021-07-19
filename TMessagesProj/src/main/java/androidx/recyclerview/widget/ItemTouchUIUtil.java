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

import android.graphics.Canvas;
import android.view.View;

/**
 * Utility class for {@link ItemTouchHelper} which handles item transformations for different
 * API versions.
 * <p/>
 * This class has methods that map to {@link ItemTouchHelper.Callback}'s drawing methods. Default
 * implementations in {@link ItemTouchHelper.Callback} call these methods with
 * {@link RecyclerView.ViewHolder#itemView} and {@link ItemTouchUIUtil} makes necessary changes
 * on the View depending on the API level. You can access the instance of {@link ItemTouchUIUtil}
 * via {@link ItemTouchHelper.Callback#getDefaultUIUtil()} and call its methods with the children
 * of ViewHolder that you want to apply default effects.
 *
 * @see ItemTouchHelper.Callback#getDefaultUIUtil()
 */
public interface ItemTouchUIUtil {

    /**
     * The default implementation for {@link ItemTouchHelper.Callback#onChildDraw(Canvas,
     * RecyclerView, RecyclerView.ViewHolder, float, float, int, boolean)}
     */
    void onDraw(Canvas c, RecyclerView recyclerView, View view,
            float dX, float dY, int actionState, boolean isCurrentlyActive);

    /**
     * The default implementation for {@link ItemTouchHelper.Callback#onChildDrawOver(Canvas,
     * RecyclerView, RecyclerView.ViewHolder, float, float, int, boolean)}
     */
    void onDrawOver(Canvas c, RecyclerView recyclerView, View view,
            float dX, float dY, int actionState, boolean isCurrentlyActive);

    /**
     * The default implementation for {@link ItemTouchHelper.Callback#clearView(RecyclerView,
     * RecyclerView.ViewHolder)}
     */
    void clearView(View view);

    /**
     * The default implementation for {@link ItemTouchHelper.Callback#onSelectedChanged(
     * RecyclerView.ViewHolder, int)}
     */
    void onSelected(View view);
}


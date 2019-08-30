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

import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/**
 * The AccessibilityDelegate used by RecyclerView.
 * <p>
 * This class handles basic accessibility actions and delegates them to LayoutManager.
 */
public class RecyclerViewAccessibilityDelegate extends AccessibilityDelegateCompat {
    final RecyclerView mRecyclerView;
    final AccessibilityDelegateCompat mItemDelegate;


    public RecyclerViewAccessibilityDelegate(@NonNull RecyclerView recyclerView) {
        mRecyclerView = recyclerView;
        mItemDelegate = new ItemDelegate(this);
    }

    boolean shouldIgnore() {
        return mRecyclerView.hasPendingAdapterUpdates();
    }

    @Override
    public boolean performAccessibilityAction(View host, int action, Bundle args) {
        if (super.performAccessibilityAction(host, action, args)) {
            return true;
        }
        if (!shouldIgnore() && mRecyclerView.getLayoutManager() != null) {
            return mRecyclerView.getLayoutManager().performAccessibilityAction(action, args);
        }

        return false;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        if (!shouldIgnore() && mRecyclerView.getLayoutManager() != null) {
            mRecyclerView.getLayoutManager().onInitializeAccessibilityNodeInfo(info);
        }
    }

    @Override
    public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(host, event);
        if (host instanceof RecyclerView && !shouldIgnore()) {
            RecyclerView rv = (RecyclerView) host;
            if (rv.getLayoutManager() != null) {
                rv.getLayoutManager().onInitializeAccessibilityEvent(event);
            }
        }
    }

    /**
     * Gets the AccessibilityDelegate for an individual item in the RecyclerView.
     * A basic item delegate is provided by default, but you can override this
     * method to provide a custom per-item delegate.
     */
    @NonNull
    public AccessibilityDelegateCompat getItemDelegate() {
        return mItemDelegate;
    }

    /**
     * The default implementation of accessibility delegate for the individual items of the
     * RecyclerView.
     * <p>
     * If you are overriding {@code RecyclerViewAccessibilityDelegate#getItemDelegate()} but still
     * want to keep some default behavior, you can create an instance of this class and delegate to
     * the parent as necessary.
     */
    public static class ItemDelegate extends AccessibilityDelegateCompat {
        final RecyclerViewAccessibilityDelegate mRecyclerViewDelegate;

        /**
         * Creates an item delegate for the given {@code RecyclerViewAccessibilityDelegate}.
         *
         * @param recyclerViewDelegate The parent RecyclerView's accessibility delegate.
         */
        public ItemDelegate(@NonNull RecyclerViewAccessibilityDelegate recyclerViewDelegate) {
            mRecyclerViewDelegate = recyclerViewDelegate;
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            if (!mRecyclerViewDelegate.shouldIgnore()
                    && mRecyclerViewDelegate.mRecyclerView.getLayoutManager() != null) {
                mRecyclerViewDelegate.mRecyclerView.getLayoutManager()
                        .onInitializeAccessibilityNodeInfoForItem(host, info);
            }
        }

        @Override
        public boolean performAccessibilityAction(View host, int action, Bundle args) {
            if (super.performAccessibilityAction(host, action, args)) {
                return true;
            }
            if (!mRecyclerViewDelegate.shouldIgnore()
                    && mRecyclerViewDelegate.mRecyclerView.getLayoutManager() != null) {
                return mRecyclerViewDelegate.mRecyclerView.getLayoutManager()
                        .performAccessibilityActionForItem(host, action, args);
            }
            return false;
        }
    }
}


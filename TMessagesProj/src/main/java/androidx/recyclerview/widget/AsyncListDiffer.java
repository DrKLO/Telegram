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

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Helper for computing the difference between two lists via {@link DiffUtil} on a background
 * thread.
 * <p>
 * It can be connected to a
 * {@link RecyclerView.Adapter RecyclerView.Adapter}, and will signal the
 * adapter of changes between sumbitted lists.
 * <p>
 * For simplicity, the {@link ListAdapter} wrapper class can often be used instead of the
 * AsyncListDiffer directly. This AsyncListDiffer can be used for complex cases, where overriding an
 * adapter base class to support asynchronous List diffing isn't convenient.
 * <p>
 * The AsyncListDiffer can consume the values from a LiveData of <code>List</code> and present the
 * data simply for an adapter. It computes differences in list contents via {@link DiffUtil} on a
 * background thread as new <code>List</code>s are received.
 * <p>
 * Use {@link #getCurrentList()} to access the current List, and present its data objects. Diff
 * results will be dispatched to the ListUpdateCallback immediately before the current list is
 * updated. If you're dispatching list updates directly to an Adapter, this means the Adapter can
 * safely access list items and total size via {@link #getCurrentList()}.
 * <p>
 * A complete usage pattern with Room would look like this:
 * <pre>
 * {@literal @}Dao
 * interface UserDao {
 *     {@literal @}Query("SELECT * FROM user ORDER BY lastName ASC")
 *     public abstract LiveData&lt;List&lt;User>> usersByLastName();
 * }
 *
 * class MyViewModel extends ViewModel {
 *     public final LiveData&lt;List&lt;User>> usersList;
 *     public MyViewModel(UserDao userDao) {
 *         usersList = userDao.usersByLastName();
 *     }
 * }
 *
 * class MyActivity extends AppCompatActivity {
 *     {@literal @}Override
 *     public void onCreate(Bundle savedState) {
 *         super.onCreate(savedState);
 *         MyViewModel viewModel = ViewModelProviders.of(this).get(MyViewModel.class);
 *         RecyclerView recyclerView = findViewById(R.id.user_list);
 *         UserAdapter adapter = new UserAdapter();
 *         viewModel.usersList.observe(this, list -> adapter.submitList(list));
 *         recyclerView.setAdapter(adapter);
 *     }
 * }
 *
 * class UserAdapter extends RecyclerView.Adapter&lt;UserViewHolder> {
 *     private final AsyncListDiffer&lt;User> mDiffer = new AsyncListDiffer(this, DIFF_CALLBACK);
 *     {@literal @}Override
 *     public int getItemCount() {
 *         return mDiffer.getCurrentList().size();
 *     }
 *     public void submitList(List&lt;User> list) {
 *         mDiffer.submitList(list);
 *     }
 *     {@literal @}Override
 *     public void onBindViewHolder(UserViewHolder holder, int position) {
 *         User user = mDiffer.getCurrentList().get(position);
 *         holder.bindTo(user);
 *     }
 *     public static final DiffUtil.ItemCallback&lt;User> DIFF_CALLBACK
 *             = new DiffUtil.ItemCallback&lt;User>() {
 *         {@literal @}Override
 *         public boolean areItemsTheSame(
 *                 {@literal @}NonNull User oldUser, {@literal @}NonNull User newUser) {
 *             // User properties may have changed if reloaded from the DB, but ID is fixed
 *             return oldUser.getId() == newUser.getId();
 *         }
 *         {@literal @}Override
 *         public boolean areContentsTheSame(
 *                 {@literal @}NonNull User oldUser, {@literal @}NonNull User newUser) {
 *             // NOTE: if you use equals, your object must properly override Object#equals()
 *             // Incorrectly returning false here will result in too many animations.
 *             return oldUser.equals(newUser);
 *         }
 *     }
 * }</pre>
 *
 * @param <T> Type of the lists this AsyncListDiffer will receive.
 *
 * @see DiffUtil
 * @see AdapterListUpdateCallback
 */
public class AsyncListDiffer<T> {
    private final ListUpdateCallback mUpdateCallback;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final AsyncDifferConfig<T> mConfig;
    Executor mMainThreadExecutor;

    private static class MainThreadExecutor implements Executor {
        final Handler mHandler = new Handler(Looper.getMainLooper());
        MainThreadExecutor() {}
        @Override
        public void execute(@NonNull Runnable command) {
            mHandler.post(command);
        }
    }

    // TODO: use MainThreadExecutor from supportlib once one exists
    private static final Executor sMainThreadExecutor = new MainThreadExecutor();

    /**
     * Listener for when the current List is updated.
     *
     * @param <T> Type of items in List
     */
    public interface ListListener<T> {
        /**
         * Called after the current List has been updated.
         *
         * @param previousList The previous list.
         * @param currentList The new current list.
         */
        void onCurrentListChanged(@NonNull List<T> previousList, @NonNull List<T> currentList);
    }

    private final List<ListListener<T>> mListeners = new CopyOnWriteArrayList<>();

    /**
     * Convenience for
     * {@code AsyncListDiffer(new AdapterListUpdateCallback(adapter),
     * new AsyncDifferConfig.Builder().setDiffCallback(diffCallback).build());}
     *
     * @param adapter Adapter to dispatch position updates to.
     * @param diffCallback ItemCallback that compares items to dispatch appropriate animations when
     *
     * @see DiffUtil.DiffResult#dispatchUpdatesTo(RecyclerView.Adapter)
     */
    public AsyncListDiffer(@NonNull RecyclerView.Adapter adapter,
            @NonNull DiffUtil.ItemCallback<T> diffCallback) {
        this(new AdapterListUpdateCallback(adapter),
            new AsyncDifferConfig.Builder<>(diffCallback).build());
    }

    /**
     * Create a AsyncListDiffer with the provided config, and ListUpdateCallback to dispatch
     * updates to.
     *
     * @param listUpdateCallback Callback to dispatch updates to.
     * @param config Config to define background work Executor, and DiffUtil.ItemCallback for
     *               computing List diffs.
     *
     * @see DiffUtil.DiffResult#dispatchUpdatesTo(RecyclerView.Adapter)
     */
    @SuppressWarnings("WeakerAccess")
    public AsyncListDiffer(@NonNull ListUpdateCallback listUpdateCallback,
            @NonNull AsyncDifferConfig<T> config) {
        mUpdateCallback = listUpdateCallback;
        mConfig = config;
        if (config.getMainThreadExecutor() != null) {
            mMainThreadExecutor = config.getMainThreadExecutor();
        } else {
            mMainThreadExecutor = sMainThreadExecutor;
        }
    }

    @Nullable
    private List<T> mList;

    /**
     * Non-null, unmodifiable version of mList.
     * <p>
     * Collections.emptyList when mList is null, wrapped by Collections.unmodifiableList otherwise
     */
    @NonNull
    private List<T> mReadOnlyList = Collections.emptyList();

    // Max generation of currently scheduled runnable
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    int mMaxScheduledGeneration;

    /**
     * Get the current List - any diffing to present this list has already been computed and
     * dispatched via the ListUpdateCallback.
     * <p>
     * If a <code>null</code> List, or no List has been submitted, an empty list will be returned.
     * <p>
     * The returned list may not be mutated - mutations to content must be done through
     * {@link #submitList(List)}.
     *
     * @return current List.
     */
    @NonNull
    public List<T> getCurrentList() {
        return mReadOnlyList;
    }

    /**
     * Pass a new List to the AdapterHelper. Adapter updates will be computed on a background
     * thread.
     * <p>
     * If a List is already present, a diff will be computed asynchronously on a background thread.
     * When the diff is computed, it will be applied (dispatched to the {@link ListUpdateCallback}),
     * and the new List will be swapped in.
     *
     * @param newList The new List.
     */
    @SuppressWarnings("WeakerAccess")
    public void submitList(@Nullable final List<T> newList) {
        submitList(newList, null);
    }

    /**
     * Pass a new List to the AdapterHelper. Adapter updates will be computed on a background
     * thread.
     * <p>
     * If a List is already present, a diff will be computed asynchronously on a background thread.
     * When the diff is computed, it will be applied (dispatched to the {@link ListUpdateCallback}),
     * and the new List will be swapped in.
     * <p>
     * The commit callback can be used to know when the List is committed, but note that it
     * may not be executed. If List B is submitted immediately after List A, and is
     * committed directly, the callback associated with List A will not be run.
     *
     * @param newList The new List.
     * @param commitCallback Optional runnable that is executed when the List is committed, if
     *                       it is committed.
     */
    @SuppressWarnings("WeakerAccess")
    public void submitList(@Nullable final List<T> newList,
            @Nullable final Runnable commitCallback) {
        // incrementing generation means any currently-running diffs are discarded when they finish
        final int runGeneration = ++mMaxScheduledGeneration;

        if (newList == mList) {
            // nothing to do (Note - still had to inc generation, since may have ongoing work)
            if (commitCallback != null) {
                commitCallback.run();
            }
            return;
        }

        final List<T> previousList = mReadOnlyList;

        // fast simple remove all
        if (newList == null) {
            //noinspection ConstantConditions
            int countRemoved = mList.size();
            mList = null;
            mReadOnlyList = Collections.emptyList();
            // notify last, after list is updated
            mUpdateCallback.onRemoved(0, countRemoved);
            onCurrentListChanged(previousList, commitCallback);
            return;
        }

        // fast simple first insert
        if (mList == null) {
            mList = newList;
            mReadOnlyList = Collections.unmodifiableList(newList);
            // notify last, after list is updated
            mUpdateCallback.onInserted(0, newList.size());
            onCurrentListChanged(previousList, commitCallback);
            return;
        }

        final List<T> oldList = mList;
        mConfig.getBackgroundThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                final DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                    @Override
                    public int getOldListSize() {
                        return oldList.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return newList.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                        T oldItem = oldList.get(oldItemPosition);
                        T newItem = newList.get(newItemPosition);
                        if (oldItem != null && newItem != null) {
                            return mConfig.getDiffCallback().areItemsTheSame(oldItem, newItem);
                        }
                        // If both items are null we consider them the same.
                        return oldItem == null && newItem == null;
                    }

                    @Override
                    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                        T oldItem = oldList.get(oldItemPosition);
                        T newItem = newList.get(newItemPosition);
                        if (oldItem != null && newItem != null) {
                            return mConfig.getDiffCallback().areContentsTheSame(oldItem, newItem);
                        }
                        if (oldItem == null && newItem == null) {
                            return true;
                        }
                        // There is an implementation bug if we reach this point. Per the docs, this
                        // method should only be invoked when areItemsTheSame returns true. That
                        // only occurs when both items are non-null or both are null and both of
                        // those cases are handled above.
                        throw new AssertionError();
                    }

                    @Nullable
                    @Override
                    public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                        T oldItem = oldList.get(oldItemPosition);
                        T newItem = newList.get(newItemPosition);
                        if (oldItem != null && newItem != null) {
                            return mConfig.getDiffCallback().getChangePayload(oldItem, newItem);
                        }
                        // There is an implementation bug if we reach this point. Per the docs, this
                        // method should only be invoked when areItemsTheSame returns true AND
                        // areContentsTheSame returns false. That only occurs when both items are
                        // non-null which is the only case handled above.
                        throw new AssertionError();
                    }
                });

                mMainThreadExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (mMaxScheduledGeneration == runGeneration) {
                            latchList(newList, result, commitCallback);
                        }
                    }
                });
            }
        });
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void latchList(
            @NonNull List<T> newList,
            @NonNull DiffUtil.DiffResult diffResult,
            @Nullable Runnable commitCallback) {
        final List<T> previousList = mReadOnlyList;
        mList = newList;
        // notify last, after list is updated
        mReadOnlyList = Collections.unmodifiableList(newList);
        diffResult.dispatchUpdatesTo(mUpdateCallback);
        onCurrentListChanged(previousList, commitCallback);
    }

    private void onCurrentListChanged(@NonNull List<T> previousList,
            @Nullable Runnable commitCallback) {
        // current list is always mReadOnlyList
        for (ListListener<T> listener : mListeners) {
            listener.onCurrentListChanged(previousList, mReadOnlyList);
        }
        if (commitCallback != null) {
            commitCallback.run();
        }
    }

    /**
     * Add a ListListener to receive updates when the current List changes.
     *
     * @param listener Listener to receive updates.
     *
     * @see #getCurrentList()
     * @see #removeListListener(ListListener)
     */
    public void addListListener(@NonNull ListListener<T> listener) {
        mListeners.add(listener);
    }

    /**
     * Remove a previously registered ListListener.
     *
     * @param listener Previously registered listener.
     * @see #getCurrentList()
     * @see #addListListener(ListListener)
     */
    public void removeListListener(@NonNull ListListener<T> listener) {
        mListeners.remove(listener);
    }
}

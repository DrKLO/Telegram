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

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

class MessageThreadUtil<T> implements ThreadUtil<T> {

    @Override
    public MainThreadCallback<T> getMainThreadProxy(final MainThreadCallback<T> callback) {
        return new MainThreadCallback<T>() {
            final MessageQueue mQueue = new MessageQueue();
            final private Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

            static final int UPDATE_ITEM_COUNT = 1;
            static final int ADD_TILE = 2;
            static final int REMOVE_TILE = 3;

            @Override
            public void updateItemCount(int generation, int itemCount) {
                sendMessage(SyncQueueItem.obtainMessage(UPDATE_ITEM_COUNT, generation, itemCount));
            }

            @Override
            public void addTile(int generation, TileList.Tile<T> tile) {
                sendMessage(SyncQueueItem.obtainMessage(ADD_TILE, generation, tile));
            }

            @Override
            public void removeTile(int generation, int position) {
                sendMessage(SyncQueueItem.obtainMessage(REMOVE_TILE, generation, position));
            }

            private void sendMessage(SyncQueueItem msg) {
                mQueue.sendMessage(msg);
                mMainThreadHandler.post(mMainThreadRunnable);
            }

            private Runnable mMainThreadRunnable = new Runnable() {
                @Override
                public void run() {
                    SyncQueueItem msg = mQueue.next();
                    while (msg != null) {
                        switch (msg.what) {
                            case UPDATE_ITEM_COUNT:
                                callback.updateItemCount(msg.arg1, msg.arg2);
                                break;
                            case ADD_TILE:
                                @SuppressWarnings("unchecked")
                                TileList.Tile<T> tile = (TileList.Tile<T>) msg.data;
                                callback.addTile(msg.arg1, tile);
                                break;
                            case REMOVE_TILE:
                                callback.removeTile(msg.arg1, msg.arg2);
                                break;
                            default:
                                Log.e("ThreadUtil", "Unsupported message, what=" + msg.what);
                        }
                        msg = mQueue.next();
                    }
                }
            };
        };
    }

    @Override
    public BackgroundCallback<T> getBackgroundProxy(final BackgroundCallback<T> callback) {
        return new BackgroundCallback<T>() {
            final MessageQueue mQueue = new MessageQueue();
            private final Executor mExecutor = AsyncTask.THREAD_POOL_EXECUTOR;
            AtomicBoolean mBackgroundRunning = new AtomicBoolean(false);

            static final int REFRESH = 1;
            static final int UPDATE_RANGE = 2;
            static final int LOAD_TILE = 3;
            static final int RECYCLE_TILE = 4;

            @Override
            public void refresh(int generation) {
                sendMessageAtFrontOfQueue(SyncQueueItem.obtainMessage(REFRESH, generation, null));
            }

            @Override
            public void updateRange(int rangeStart, int rangeEnd,
                                    int extRangeStart, int extRangeEnd, int scrollHint) {
                sendMessageAtFrontOfQueue(SyncQueueItem.obtainMessage(UPDATE_RANGE,
                        rangeStart, rangeEnd, extRangeStart, extRangeEnd, scrollHint, null));
            }

            @Override
            public void loadTile(int position, int scrollHint) {
                sendMessage(SyncQueueItem.obtainMessage(LOAD_TILE, position, scrollHint));
            }

            @Override
            public void recycleTile(TileList.Tile<T> tile) {
                sendMessage(SyncQueueItem.obtainMessage(RECYCLE_TILE, 0, tile));
            }

            private void sendMessage(SyncQueueItem msg) {
                mQueue.sendMessage(msg);
                maybeExecuteBackgroundRunnable();
            }

            private void sendMessageAtFrontOfQueue(SyncQueueItem msg) {
                mQueue.sendMessageAtFrontOfQueue(msg);
                maybeExecuteBackgroundRunnable();
            }

            private void maybeExecuteBackgroundRunnable() {
                if (mBackgroundRunning.compareAndSet(false, true)) {
                    mExecutor.execute(mBackgroundRunnable);
                }
            }

            private Runnable mBackgroundRunnable = new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        SyncQueueItem msg = mQueue.next();
                        if (msg == null) {
                            break;
                        }
                        switch (msg.what) {
                            case REFRESH:
                                mQueue.removeMessages(REFRESH);
                                callback.refresh(msg.arg1);
                                break;
                            case UPDATE_RANGE:
                                mQueue.removeMessages(UPDATE_RANGE);
                                mQueue.removeMessages(LOAD_TILE);
                                callback.updateRange(
                                        msg.arg1, msg.arg2, msg.arg3, msg.arg4, msg.arg5);
                                break;
                            case LOAD_TILE:
                                callback.loadTile(msg.arg1, msg.arg2);
                                break;
                            case RECYCLE_TILE:
                                @SuppressWarnings("unchecked")
                                TileList.Tile<T> tile = (TileList.Tile<T>) msg.data;
                                callback.recycleTile(tile);
                                break;
                            default:
                                Log.e("ThreadUtil", "Unsupported message, what=" + msg.what);
                        }
                    }
                    mBackgroundRunning.set(false);
                }
            };
        };
    }

    /**
     * Replica of android.os.Message. Unfortunately, cannot use it without a Handler and don't want
     * to create a thread just for this component.
     */
    static class SyncQueueItem {

        private static SyncQueueItem sPool;
        private static final Object sPoolLock = new Object();
        SyncQueueItem next;
        public int what;
        public int arg1;
        public int arg2;
        public int arg3;
        public int arg4;
        public int arg5;
        public Object data;

        void recycle() {
            next = null;
            what = arg1 = arg2 = arg3 = arg4 = arg5 = 0;
            data = null;
            synchronized (sPoolLock) {
                if (sPool != null) {
                    next = sPool;
                }
                sPool = this;
            }
        }

        static SyncQueueItem obtainMessage(int what, int arg1, int arg2, int arg3, int arg4,
                                           int arg5, Object data) {
            synchronized (sPoolLock) {
                final SyncQueueItem item;
                if (sPool == null) {
                    item = new SyncQueueItem();
                } else {
                    item = sPool;
                    sPool = sPool.next;
                    item.next = null;
                }
                item.what = what;
                item.arg1 = arg1;
                item.arg2 = arg2;
                item.arg3 = arg3;
                item.arg4 = arg4;
                item.arg5 = arg5;
                item.data = data;
                return item;
            }
        }

        static SyncQueueItem obtainMessage(int what, int arg1, int arg2) {
            return obtainMessage(what, arg1, arg2, 0, 0, 0, null);
        }

        static SyncQueueItem obtainMessage(int what, int arg1, Object data) {
            return obtainMessage(what, arg1, 0, 0, 0, 0, data);
        }
    }

    static class MessageQueue {

        private SyncQueueItem mRoot;

        synchronized SyncQueueItem next() {
            if (mRoot == null) {
                return null;
            }
            final SyncQueueItem next = mRoot;
            mRoot = mRoot.next;
            return next;
        }

        synchronized void sendMessageAtFrontOfQueue(SyncQueueItem item) {
            item.next = mRoot;
            mRoot = item;
        }

        synchronized void sendMessage(SyncQueueItem item) {
            if (mRoot == null) {
                mRoot = item;
                return;
            }
            SyncQueueItem last = mRoot;
            while (last.next != null) {
                last = last.next;
            }
            last.next = item;
        }

        synchronized void removeMessages(int what) {
            while (mRoot != null && mRoot.what == what) {
                SyncQueueItem item = mRoot;
                mRoot = mRoot.next;
                item.recycle();
            }
            if (mRoot != null) {
                SyncQueueItem prev = mRoot;
                SyncQueueItem item = prev.next;
                while (item != null) {
                    SyncQueueItem next = item.next;
                    if (item.what == what) {
                        prev.next = next;
                        item.recycle();
                    } else {
                        prev = item;
                    }
                    item = next;
                }
            }
        }
    }
}

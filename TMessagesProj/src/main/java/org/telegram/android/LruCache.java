/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.android;

import android.graphics.drawable.BitmapDrawable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * Static library version of {@link android.util.LruCache}. Used to write apps
 * that run on API levels prior to 12. When running on API level 12 or above,
 * this implementation is still used; it does not try to switch to the
 * framework's implementation. See the framework SDK documentation for a class
 * overview.
 */
public class LruCache {
    private final LinkedHashMap<String, BitmapDrawable> map;
    private final LinkedHashMap<String, ArrayList<String>> mapFilters;

    /** Size of this cache in units. Not necessarily the number of elements. */
    private int size;
    private int maxSize;

    /**
     * @param maxSize for caches that do not override {@link #sizeOf}, this is
     *     the maximum number of entries in the cache. For all other caches,
     *     this is the maximum sum of the sizes of the entries in this cache.
     */
    public LruCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize <= 0");
        }
        this.maxSize = maxSize;
        this.map = new LinkedHashMap<>(0, 0.75f, true);
        this.mapFilters = new LinkedHashMap<>();
    }

    /**
     * Returns the value for {@code key} if it exists in the cache or can be
     * created by {@code #create}. If a value was returned, it is moved to the
     * head of the queue. This returns null if a value is not cached and cannot
     * be created.
     */
    public final BitmapDrawable get(String key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        BitmapDrawable mapValue;
        synchronized (this) {
            mapValue = map.get(key);
            if (mapValue != null) {
                return mapValue;
            }
        }
        return null;
    }

    public ArrayList<String> getFilterKeys(String key) {
        ArrayList<String> arr = mapFilters.get(key);
        if (arr != null) {
            return new ArrayList<>(arr);
        }
        return null;
    }

    /**
     * Caches {@code value} for {@code key}. The value is moved to the head of
     * the queue.
     *
     * @return the previous value mapped by {@code key}.
     */
    public BitmapDrawable put(String key, BitmapDrawable value) {
        if (key == null || value == null) {
            throw new NullPointerException("key == null || value == null");
        }

        BitmapDrawable previous;
        synchronized (this) {
            size += safeSizeOf(key, value);
            previous = map.put(key, value);
            if (previous != null) {
                size -= safeSizeOf(key, previous);
            }
        }

        String[] args = key.split("@");
        if (args.length > 1) {
            ArrayList<String> arr = mapFilters.get(args[0]);
            if (arr == null) {
                arr = new ArrayList<>();
                mapFilters.put(args[0], arr);
            }
            if (!arr.contains(args[1])) {
                arr.add(args[1]);
            }
        }

        if (previous != null) {
            entryRemoved(false, key, previous, value);
            ImageLoader.getInstance().callGC();
        }

        trimToSize(maxSize, key);
        return previous;
    }

    /**
     * @param maxSize the maximum size of the cache before returning. May be -1
     *     to evict even 0-sized elements.
     */
    private void trimToSize(int maxSize, String justAdded) {
        synchronized (this) {
            Iterator<HashMap.Entry<String, BitmapDrawable>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                if (size <= maxSize || map.isEmpty()) {
                    break;
                }
                HashMap.Entry<String, BitmapDrawable> entry = iterator.next();

                String key = entry.getKey();
                if (justAdded != null && justAdded.equals(key)) {
                    continue;
                }
                BitmapDrawable value = entry.getValue();
                size -= safeSizeOf(key, value);
                iterator.remove();

                String[] args = key.split("@");
                if (args.length > 1) {
                    ArrayList<String> arr = mapFilters.get(args[0]);
                    if (arr != null) {
                        arr.remove(args[1]);
                        if (arr.isEmpty()) {
                            mapFilters.remove(args[0]);
                        }
                    }
                }

                entryRemoved(true, key, value, null);
            }
            ImageLoader.getInstance().callGC();
        }
    }

    /**
     * Removes the entry for {@code key} if it exists.
     *
     * @return the previous value mapped by {@code key}.
     */
    public final BitmapDrawable remove(String key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        BitmapDrawable previous;
        synchronized (this) {
            previous = map.remove(key);
            if (previous != null) {
                size -= safeSizeOf(key, previous);
            }
        }

        if (previous != null) {
            String[] args = key.split("@");
            if (args.length > 1) {
                ArrayList<String> arr = mapFilters.get(args[0]);
                if (arr != null) {
                    arr.remove(args[1]);
                    if (arr.isEmpty()) {
                        mapFilters.remove(args[0]);
                    }
                }
            }

            entryRemoved(false, key, previous, null);
            ImageLoader.getInstance().callGC();
        }

        return previous;
    }
    
    public boolean contains(String key){
    	return map.containsKey(key);
    }

    /**
     * Called for entries that have been evicted or removed. This method is
     * invoked when a value is evicted to make space, removed by a call to
     * {@link #remove}, or replaced by a call to {@link #put}. The default
     * implementation does nothing.
     *
     * <p>The method is called without synchronization: other threads may
     * access the cache while this method is executing.
     *
     * @param evicted true if the entry is being removed to make space, false
     *     if the removal was caused by a {@link #put} or {@link #remove}.
     * @param newValue the new value for {@code key}, if it exists. If non-null,
     *     this removal was caused by a {@link #put}. Otherwise it was caused by
     *     an eviction or a {@link #remove}.
     */
    protected void entryRemoved(boolean evicted, String key, BitmapDrawable oldValue, BitmapDrawable newValue) {}

    private int safeSizeOf(String key, BitmapDrawable value) {
        int result = sizeOf(key, value);
        if (result < 0) {
            throw new IllegalStateException("Negative size: " + key + "=" + value);
        }
        return result;
    }

    /**
     * Returns the size of the entry for {@code key} and {@code value} in
     * user-defined units.  The default implementation returns 1 so that size
     * is the number of entries and max size is the maximum number of entries.
     *
     * <p>An entry's size must not change while it is in the cache.
     */
    protected int sizeOf(String key, BitmapDrawable value) {
        return 1;
    }

    /**
     * Clear the cache, calling {@link #entryRemoved} on each removed entry.
     */
    public final void evictAll() {
        trimToSize(-1, null); // -1 will evict 0-sized elements
    }

    /**
     * For caches that do not override {@link #sizeOf}, this returns the number
     * of entries in the cache. For all other caches, this returns the sum of
     * the sizes of the entries in this cache.
     */
    public synchronized final int size() {
        return size;
    }

    /**
     * For caches that do not override {@link #sizeOf}, this returns the maximum
     * number of entries in the cache. For all other caches, this returns the
     * maximum sum of the sizes of the entries in this cache.
     */
    public synchronized final int maxSize() {
        return maxSize;
    }
}

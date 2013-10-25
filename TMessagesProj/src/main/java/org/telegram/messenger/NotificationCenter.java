/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import java.util.ArrayList;
import java.util.HashMap;

public class NotificationCenter {
    final private HashMap<Integer, ArrayList<Object>> observers = new HashMap<Integer, ArrayList<Object>>();
    final private HashMap<Integer, Object> memCache = new HashMap<Integer, Object>();
    final private HashMap<String, Object> memCacheString = new HashMap<String, Object>();
    private boolean broadcasting = false;
    final private HashMap<Integer, Object> removeAfterBroadcast = new HashMap<Integer, Object>();

    public interface NotificationCenterDelegate {
        public abstract void didReceivedNotification(int id, Object... args);
    }

    public static NotificationCenter Instance = new NotificationCenter();

    public void addToMemCache(int id, Object object) {
        memCache.put(id, object);
    }

    public void addToMemCache(String id, Object object) {
        memCacheString.put(id, object);
    }

    public Object getFromMemCache(int id) {
        Object obj = memCache.get(id);
        if (obj != null) {
            memCache.remove(id);
        }
        return obj;
    }

    public Object getFromMemCache(String id, Object defaultValue) {
        Object obj = memCacheString.get(id);
        if (obj != null) {
            memCacheString.remove(id);
        } else {
            return defaultValue;
        }
        return obj;
    }

    public void postNotificationName(int id, Object... args) {
        synchronized (observers) {
            broadcasting = true;
            ArrayList<Object> objects = observers.get(id);
            if (objects != null) {
                for (Object obj : objects) {
                    ((NotificationCenterDelegate)obj).didReceivedNotification(id, args);
                }
            }
            broadcasting = false;
            if (!removeAfterBroadcast.isEmpty()) {
                for (HashMap.Entry<Integer, Object> entry : removeAfterBroadcast.entrySet()) {
                    removeObserver(entry.getValue(), entry.getKey());
                }
                removeAfterBroadcast.clear();
            }
        }
    }

    public void addObserver(Object observer, int id) {
        synchronized (observers) {
            ArrayList<Object> objects = observers.get(id);
            if (objects == null) {
                objects = new ArrayList<Object>();
                observers.put(id, objects);
            }
            if (objects.contains(observer)) {
                return;
            }
            objects.add(observer);
        }
    }

//    public void removeObserver(Object observer) {
//        synchronized (observers) {
//            if (broadcasting) {
//                removeAfterBroadcast.put(-1, observer);
//                return;
//            }
//            Integer[] keyArr = (Integer[])observers.keySet().toArray();
//            for (int a = 0; a < observers.size(); a++) {
//                Integer id = keyArr[a];
//                ArrayList<Object> objects = observers.get(id);
//                objects.remove(observer);
//                if (objects.size() == 0) {
//                    observers.remove(id);
//                    a--;
//                }
//            }
//        }
//    }

    public void removeObserver(Object observer, int id) {
        synchronized (observers) {
            if (broadcasting) {
                removeAfterBroadcast.put(id, observer);
                return;
            }
            ArrayList<Object> objects = observers.get(id);
            if (objects != null) {
                objects.remove(observer);
                if (objects.size() == 0) {
                    observers.remove(id);
                }
            }
        }
    }
}

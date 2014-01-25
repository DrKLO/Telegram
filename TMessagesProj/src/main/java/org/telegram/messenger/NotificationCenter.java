/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationCenter {

    public static NotificationCenter Instance = new NotificationCenter();

    private final Map<Integer, List<Object>> observers = new HashMap<Integer, List<Object>>();

    private Map<String, Object> memCache = new HashMap<String, Object>();
    private Map<Integer, Object> removeAfterBroadcast = new HashMap<Integer, Object>();

    private boolean broadcasting = false;

    public interface NotificationCenterDelegate {
        public abstract void didReceivedNotification(int id, Object... args);
    }

    public void addToMemCache(int id, Object object) {
        addToMemCache(String.valueOf(id), object);
    }

    public void addToMemCache(String id, Object object) {
        memCache.put(id, object);
    }

    public Object getFromMemCache(int id) {
        return getFromMemCache(String.valueOf(id), null);
    }

    public Object getFromMemCache(String id, Object defaultValue) {
        Object obj = memCache.get(id);
        if (obj != null) {
            memCache.remove(id);
            return obj;
        }
        return defaultValue;
    }

    public void postNotificationName(int id, Object... args) {
        synchronized (observers) {
            broadcasting = true;
            List<Object> objects = observers.get(id);
            if (objects != null) {
                for (Object obj : objects) {
                    ((NotificationCenterDelegate) obj).didReceivedNotification(id, args);
                }
            }
            broadcasting = false;
            if (!removeAfterBroadcast.isEmpty()) {
                for (Map.Entry<Integer, Object> entry : removeAfterBroadcast.entrySet()) {
                    removeObserver(entry.getValue(), entry.getKey());
                }
                removeAfterBroadcast.clear();
            }
        }
    }

    public void addObserver(Object observer, int id) {
        synchronized (observers) {
            if (!observers.containsKey(id)) {
                observers.put(id, new ArrayList<Object>());
            }

            List<Object> objects = observers.get(id);
            if (objects.contains(observer)) {
                return;
            }

            objects.add(observer);
        }
    }

    public void removeObserver(Object observer, int id) {
        synchronized (observers) {
            if (broadcasting) {
                removeAfterBroadcast.put(id, observer);
                return;
            }
            List<Object> objects = observers.get(id);
            if (objects != null) {
                objects.remove(observer);
                if (objects.size() == 0) {
                    observers.remove(id);
                }
            }
        }
    }
}

/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.SparseIntArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;

public class LocationController implements NotificationCenter.NotificationCenterDelegate {

    private HashMap<Long, SharingLocationInfo> sharingLocationsMap = new HashMap<>();
    private ArrayList<SharingLocationInfo> sharingLocations = new ArrayList<>();
    public HashMap<Long, ArrayList<TLRPC.Message>> locationsCache = new HashMap<>();
    private LocationManager locationManager;
    private GpsLocationListener gpsLocationListener = new GpsLocationListener();
    private GpsLocationListener networkLocationListener = new GpsLocationListener();
    private GpsLocationListener passiveLocationListener = new GpsLocationListener();
    private Location lastKnownLocation;
    private long lastLocationSendTime;
    private boolean locationSentSinceLastGoogleMapUpdate = true;
    private long lastLocationStartTime;
    private boolean started;
    private boolean lastLocationByGoogleMaps;
    private SparseIntArray requests = new SparseIntArray();
    private LongSparseArray<Boolean> cacheRequests = new LongSparseArray<>();

    public ArrayList<SharingLocationInfo> sharingLocationsUI = new ArrayList<>();
    private HashMap<Long, SharingLocationInfo> sharingLocationsMapUI = new HashMap<>();

    private final static int BACKGROUD_UPDATE_TIME = 90 * 1000;
    private final static int LOCATION_ACQUIRE_TIME = 10 * 1000;
    private final static int FOREGROUND_UPDATE_TIME = 20 * 1000;
    private final static double eps = 0.0001;

    private static volatile LocationController Instance = null;

    public static LocationController getInstance() {
        LocationController localInstance = Instance;
        if (localInstance == null) {
            synchronized (LocationController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new LocationController();
                }
            }
        }
        return localInstance;
    }

    public static class SharingLocationInfo {
        public long did;
        public int mid;
        public int stopTime;
        public int period;
        public MessageObject messageObject;
    }

    private class GpsLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {
            if (location == null) {
                return;
            }
            if (lastKnownLocation != null && (this == networkLocationListener || this == passiveLocationListener)) {
                if (!started && location.distanceTo(lastKnownLocation) > 20) {
                    lastKnownLocation = location;
                    lastLocationSendTime = System.currentTimeMillis() - BACKGROUD_UPDATE_TIME + 5000;
                }
            } else {
                lastKnownLocation = location;
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    }

    public LocationController() {
        locationManager = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                LocationController locationController = getInstance();
                NotificationCenter.getInstance().addObserver(locationController, NotificationCenter.didReceivedNewMessages);
                NotificationCenter.getInstance().addObserver(locationController, NotificationCenter.messagesDeleted);
                NotificationCenter.getInstance().addObserver(locationController, NotificationCenter.replaceMessagesObjects);
            }
        });
        loadSharingLocations();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.didReceivedNewMessages) {
            long did = (Long) args[0];
            if (!isSharingLocation(did)) {
                return;
            }
            ArrayList<TLRPC.Message> messages = locationsCache.get(did);
            if (messages == null) {
                return;
            }
            ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
            boolean added = false;
            for (int a = 0; a < arr.size(); a++) {
                MessageObject messageObject = arr.get(a);
                if (messageObject.isLiveLocation()) {
                    added = true;
                    boolean replaced = false;
                    for (int b = 0; b < messages.size(); b++) {
                        if (messages.get(b).from_id == messageObject.messageOwner.from_id) {
                            replaced = true;
                            messages.set(b, messageObject.messageOwner);
                            break;
                        }
                    }
                    if (!replaced) {
                        messages.add(messageObject.messageOwner);
                    }
                }
            }
            if (added) {
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.liveLocationsCacheChanged, did);
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            if (!sharingLocationsUI.isEmpty()) {
                ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>) args[0];
                int channelId = (Integer) args[1];
                ArrayList<Long> toRemove = null;
                for (int a = 0; a < sharingLocationsUI.size(); a++) {
                    SharingLocationInfo info = sharingLocationsUI.get(a);
                    int messageChannelId = info.messageObject != null ? info.messageObject.getChannelId() : 0;
                    if (channelId != messageChannelId) {
                        continue;
                    }
                    if (markAsDeletedMessages.contains(info.mid)) {
                        if (toRemove == null) {
                            toRemove = new ArrayList<>();
                        }
                        toRemove.add(info.did);
                    }
                }
                if (toRemove != null) {
                    for (int a = 0; a < toRemove.size(); a++) {
                        removeSharingLocation(toRemove.get(a));
                    }
                }
            }
        } else if (id == NotificationCenter.replaceMessagesObjects) {
            long did = (long) args[0];
            if (!isSharingLocation(did)) {
                return;
            }
            ArrayList<TLRPC.Message> messages = locationsCache.get(did);
            if (messages == null) {
                return;
            }
            boolean updated = false;
            ArrayList<MessageObject> messageObjects = (ArrayList<MessageObject>) args[1];
            for (int a = 0; a < messageObjects.size(); a++) {
                MessageObject messageObject = messageObjects.get(a);
                for (int b = 0; b < messages.size(); b++) {
                    if (messages.get(b).from_id == messageObject.messageOwner.from_id) {
                        if (!messageObject.isLiveLocation()) {
                            messages.remove(b);
                        } else {
                            messages.set(b, messageObject.messageOwner);
                        }
                        updated = true;
                        break;
                    }
                }
            }
            if (updated) {
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.liveLocationsCacheChanged, did);
            }
        }
    }

    private void broadcastLastKnownLocation() {
        if (lastKnownLocation == null) {
            return;
        }
        if (requests.size() != 0) {
            for (int a = 0; a < requests.size(); a++) {
                ConnectionsManager.getInstance().cancelRequest(requests.keyAt(a), false);
            }
            requests.clear();
        }
        int date = ConnectionsManager.getInstance().getCurrentTime();
        for (int a = 0; a < sharingLocations.size(); a++) {
            final SharingLocationInfo info = sharingLocations.get(a);
            if (info.messageObject.messageOwner.media != null && info.messageObject.messageOwner.media.geo != null) {
                int messageDate = info.messageObject.messageOwner.edit_date != 0 ? info.messageObject.messageOwner.edit_date : info.messageObject.messageOwner.date;
                TLRPC.GeoPoint point = info.messageObject.messageOwner.media.geo;
                if (Math.abs(date - messageDate) < 30 && Math.abs(point.lat - lastKnownLocation.getLatitude()) <= eps && Math.abs(point._long - lastKnownLocation.getLongitude()) <= eps) {
                    continue;
                }
            }
            TLRPC.TL_messages_editMessage req = new TLRPC.TL_messages_editMessage();
            req.peer = MessagesController.getInputPeer((int) info.did);
            req.id = info.mid;
            req.stop_geo_live = false;
            req.flags |= 8192;
            req.geo_point = new TLRPC.TL_inputGeoPoint();
            req.geo_point.lat = lastKnownLocation.getLatitude();
            req.geo_point._long = lastKnownLocation.getLongitude();
            final int[] reqId = new int[1];
            reqId[0] = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error != null) {
                        if (error.text.equals("MESSAGE_ID_INVALID")) {
                            sharingLocations.remove(info);
                            sharingLocationsMap.remove(info.did);
                            saveSharingLocation(info, 1);
                            requests.delete(reqId[0]);
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    sharingLocationsUI.remove(info);
                                    sharingLocationsMapUI.remove(info.did);
                                    if (sharingLocationsUI.isEmpty()) {
                                        stopService();
                                    }
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.liveLocationsChanged);
                                }
                            });
                        }
                        return;
                    }
                    TLRPC.Updates updates = (TLRPC.Updates) response;
                    boolean updated = false;
                    for (int a = 0; a < updates.updates.size(); a++) {
                        TLRPC.Update update = updates.updates.get(a);
                        if (update instanceof TLRPC.TL_updateEditMessage) {
                            updated = true;
                            info.messageObject.messageOwner = ((TLRPC.TL_updateEditMessage) update).message;
                        } else if (update instanceof TLRPC.TL_updateEditChannelMessage) {
                            updated = true;
                            info.messageObject.messageOwner = ((TLRPC.TL_updateEditChannelMessage) update).message;
                        }
                    }
                    if (updated) {
                        saveSharingLocation(info, 0);
                    }
                    MessagesController.getInstance().processUpdates(updates, false);
                }
            });
            requests.put(reqId[0], 0);
        }
        ConnectionsManager.getInstance().resumeNetworkMaybe();
        stop(false);
    }

    protected void update() {
        if (!sharingLocations.isEmpty()) {
            for (int a = 0; a < sharingLocations.size(); a++) {
                final SharingLocationInfo info = sharingLocations.get(a);
                int currentTime = ConnectionsManager.getInstance().getCurrentTime();
                if (info.stopTime <= currentTime) {
                    sharingLocations.remove(a);
                    sharingLocationsMap.remove(info.did);
                    saveSharingLocation(info, 1);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            sharingLocationsUI.remove(info);
                            sharingLocationsMapUI.remove(info.did);
                            if (sharingLocationsUI.isEmpty()) {
                                stopService();
                            }
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.liveLocationsChanged);
                        }
                    });
                    a--;
                }
            }
            if (!started) {
                if (Math.abs(lastLocationSendTime - System.currentTimeMillis()) > BACKGROUD_UPDATE_TIME) {
                    lastLocationStartTime = System.currentTimeMillis();
                    start();
                }
            } else {
                if (lastLocationByGoogleMaps || Math.abs(lastLocationStartTime - System.currentTimeMillis()) > LOCATION_ACQUIRE_TIME) {
                    lastLocationByGoogleMaps = false;
                    locationSentSinceLastGoogleMapUpdate = true;
                    lastLocationSendTime = System.currentTimeMillis();
                    broadcastLastKnownLocation();
                }
            }
        }
    }

    public void cleanup() {
        sharingLocationsUI.clear();
        sharingLocationsMapUI.clear();
        locationsCache.clear();
        cacheRequests.clear();
        stopService();
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                requests.clear();
                sharingLocationsMap.clear();
                sharingLocations.clear();
                lastKnownLocation = null;
                stop(true);
            }
        });
    }

    protected void addSharingLocation(long did, int mid, int period, TLRPC.Message message) {
        final SharingLocationInfo info = new SharingLocationInfo();
        info.did = did;
        info.mid = mid;
        info.period = period;
        info.messageObject = new MessageObject(message, null, null, false);
        info.stopTime = ConnectionsManager.getInstance().getCurrentTime() + period;
        final SharingLocationInfo old = sharingLocationsMap.put(did, info);
        if (old != null) {
            sharingLocations.remove(old);
        }
        sharingLocations.add(info);
        saveSharingLocation(info, 0);
        lastLocationSendTime = System.currentTimeMillis() - BACKGROUD_UPDATE_TIME + 5000;
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (old != null) {
                    sharingLocationsUI.remove(old);
                }
                sharingLocationsUI.add(info);
                sharingLocationsMapUI.put(info.did, info);
                startService();
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.liveLocationsChanged);
            }
        });
    }

    public boolean isSharingLocation(long did) {
        return sharingLocationsMapUI.containsKey(did);
    }

    public SharingLocationInfo getSharingLocationInfo(long did) {
        return sharingLocationsMapUI.get(did);
    }

    private void loadSharingLocations() {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                final ArrayList<SharingLocationInfo> result = new ArrayList<>();
                final ArrayList<TLRPC.User> users = new ArrayList<>();
                final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                try {
                    ArrayList<Integer> usersToLoad = new ArrayList<>();
                    ArrayList<Integer> chatsToLoad = new ArrayList<>();
                    SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT uid, mid, date, period, message FROM sharing_locations WHERE 1");
                    while (cursor.next()) {
                        SharingLocationInfo info = new SharingLocationInfo();
                        info.did = cursor.longValue(0);
                        info.mid = cursor.intValue(1);
                        info.stopTime = cursor.intValue(2);
                        info.period = cursor.intValue(3);
                        NativeByteBuffer data = cursor.byteBufferValue(4);
                        if (data != null) {
                            info.messageObject = new MessageObject(TLRPC.Message.TLdeserialize(data, data.readInt32(false), false), null, false);
                            MessagesStorage.addUsersAndChatsFromMessage(info.messageObject.messageOwner, usersToLoad, chatsToLoad);
                            data.reuse();
                        }
                        result.add(info);
                        int lower_id = (int) info.did;
                        int high_id = (int) (info.did >> 32);
                        if (lower_id != 0) {
                            if (lower_id < 0) {
                                if (!chatsToLoad.contains(-lower_id)) {
                                    chatsToLoad.add(-lower_id);
                                }
                            } else {
                                if (!usersToLoad.contains(lower_id)) {
                                    usersToLoad.add(lower_id);
                                }
                            }
                        } else {
                            /*if (!encryptedChatIds.contains(high_id)) {
                                encryptedChatIds.add(high_id);
                            }*/
                        }
                    }
                    cursor.dispose();
                    if (!chatsToLoad.isEmpty()) {
                        MessagesStorage.getInstance().getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                    }
                    if (!usersToLoad.isEmpty()) {
                        MessagesStorage.getInstance().getUsersInternal(TextUtils.join(",", usersToLoad), users);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (!result.isEmpty()) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            MessagesController.getInstance().putUsers(users, true);
                            MessagesController.getInstance().putChats(chats, true);
                            Utilities.stageQueue.postRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    sharingLocations.addAll(result);
                                    for (int a = 0; a < sharingLocations.size(); a++) {
                                        SharingLocationInfo info = sharingLocations.get(a);
                                        sharingLocationsMap.put(info.did, info);
                                    }
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            sharingLocationsUI.addAll(result);
                                            for (int a = 0; a < result.size(); a++) {
                                                SharingLocationInfo info = result.get(a);
                                                sharingLocationsMapUI.put(info.did, info);
                                            }
                                            startService();
                                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.liveLocationsChanged);
                                        }
                                    });
                                }
                            });
                        }
                    });
                }
            }
        });
    }

    private void saveSharingLocation(final SharingLocationInfo info, final int remove) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    if (remove == 2) {
                        MessagesStorage.getInstance().getDatabase().executeFast("DELETE FROM sharing_locations WHERE 1").stepThis().dispose();
                    } else if (remove == 1) {
                        if (info == null) {
                            return;
                        }
                        MessagesStorage.getInstance().getDatabase().executeFast("DELETE FROM sharing_locations WHERE uid = " + info.did).stepThis().dispose();
                    } else {
                        if (info == null) {
                            return;
                        }
                        SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("REPLACE INTO sharing_locations VALUES(?, ?, ?, ?, ?)");
                        state.requery();

                        NativeByteBuffer data = new NativeByteBuffer(info.messageObject.messageOwner.getObjectSize());
                        info.messageObject.messageOwner.serializeToStream(data);

                        state.bindLong(1, info.did);
                        state.bindInteger(2, info.mid);
                        state.bindInteger(3, info.stopTime);
                        state.bindInteger(4, info.period);
                        state.bindByteBuffer(5, data);

                        state.step();
                        state.dispose();
                        data.reuse();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    public void removeSharingLocation(final long did) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                final SharingLocationInfo info = sharingLocationsMap.remove(did);
                if (info != null) {
                    TLRPC.TL_messages_editMessage req = new TLRPC.TL_messages_editMessage();
                    req.peer = MessagesController.getInputPeer((int) info.did);
                    req.id = info.mid;
                    req.stop_geo_live = true;
                    ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                        @Override
                        public void run(TLObject response, TLRPC.TL_error error) {
                            if (error != null) {
                                return;
                            }
                            MessagesController.getInstance().processUpdates((TLRPC.Updates) response, false);
                        }
                    });
                    sharingLocations.remove(info);
                    saveSharingLocation(info, 1);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            sharingLocationsUI.remove(info);
                            sharingLocationsMapUI.remove(info.did);
                            if (sharingLocationsUI.isEmpty()) {
                                stopService();
                            }
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.liveLocationsChanged);
                        }
                    });
                    if (sharingLocations.isEmpty()) {
                        stop(true);
                    }
                }

            }
        });
    }

    private void startService() {
        ApplicationLoader.applicationContext.startService(new Intent(ApplicationLoader.applicationContext, LocationSharingService.class));
    }

    private void stopService() {
        ApplicationLoader.applicationContext.stopService(new Intent(ApplicationLoader.applicationContext, LocationSharingService.class));
    }

    public void removeAllLocationSharings() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                for (int a = 0; a < sharingLocations.size(); a++) {
                    SharingLocationInfo info = sharingLocations.get(a);
                    TLRPC.TL_messages_editMessage req = new TLRPC.TL_messages_editMessage();
                    req.peer = MessagesController.getInputPeer((int) info.did);
                    req.id = info.mid;
                    req.stop_geo_live = true;
                    ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                        @Override
                        public void run(TLObject response, TLRPC.TL_error error) {
                            if (error != null) {
                                return;
                            }
                            MessagesController.getInstance().processUpdates((TLRPC.Updates) response, false);
                        }
                    });
                }
                sharingLocations.clear();
                sharingLocationsMap.clear();
                saveSharingLocation(null, 2);
                stop(true);
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        sharingLocationsUI.clear();
                        sharingLocationsMapUI.clear();
                        stopService();
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.liveLocationsChanged);
                    }
                });
            }
        });
    }

    public void setGoogleMapLocation(Location location, boolean first) {
        if (location == null) {
            return;
        }
        lastLocationByGoogleMaps = true;
        if (first || lastKnownLocation != null && lastKnownLocation.distanceTo(location) >= 20) {
            lastLocationSendTime = System.currentTimeMillis() - BACKGROUD_UPDATE_TIME;
            locationSentSinceLastGoogleMapUpdate = false;
        } else if (locationSentSinceLastGoogleMapUpdate) {
            lastLocationSendTime = System.currentTimeMillis() - BACKGROUD_UPDATE_TIME + FOREGROUND_UPDATE_TIME;
            locationSentSinceLastGoogleMapUpdate = false;
        }
        lastKnownLocation = location;
    }

    private void start() {
        if (started) {
            return;
        }
        lastLocationStartTime = System.currentTimeMillis();
        started = true;
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 0, gpsLocationListener);
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1, 0, networkLocationListener);
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1, 0, passiveLocationListener);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (lastKnownLocation == null) {
            try {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastKnownLocation == null) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private void stop(boolean empty) {
        started = false;
        locationManager.removeUpdates(gpsLocationListener);
        if (empty) {
            locationManager.removeUpdates(networkLocationListener);
            locationManager.removeUpdates(passiveLocationListener);
        }
    }

    public void loadLiveLocations(final long did) {
        if (cacheRequests.indexOfKey(did) >= 0) {
            return;
        }
        cacheRequests.put(did, true);
        TLRPC.TL_messages_getRecentLocations req = new TLRPC.TL_messages_getRecentLocations();
        req.peer = MessagesController.getInputPeer((int) did);
        req.limit = 100;
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        cacheRequests.delete(did);
                        TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        for (int a = 0; a < res.messages.size(); a++) {
                            if (!(res.messages.get(a).media instanceof TLRPC.TL_messageMediaGeoLive)) {
                                res.messages.remove(a);
                                a--;
                            }
                        }
                        MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                        MessagesController.getInstance().putUsers(res.users, false);
                        MessagesController.getInstance().putChats(res.chats, false);
                        locationsCache.put(did, res.messages);
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.liveLocationsCacheChanged, did);
                    }
                });
            }
        });
    }
}

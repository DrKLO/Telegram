/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.SparseIntArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import androidx.collection.LongSparseArray;

public class LocationController extends BaseController implements NotificationCenter.NotificationCenterDelegate {

    private LongSparseArray<SharingLocationInfo> sharingLocationsMap = new LongSparseArray<>();
    private ArrayList<SharingLocationInfo> sharingLocations = new ArrayList<>();
    public LongSparseArray<ArrayList<TLRPC.Message>> locationsCache = new LongSparseArray<>();
    private LongSparseArray<Integer> lastReadLocationTime = new LongSparseArray<>();
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
    private long locationEndWatchTime;
    private boolean shareMyCurrentLocation;

    private boolean lookingForPeopleNearby;

    public ArrayList<SharingLocationInfo> sharingLocationsUI = new ArrayList<>();
    private LongSparseArray<SharingLocationInfo> sharingLocationsMapUI = new LongSparseArray<>();

    private final static long UPDATE_INTERVAL = 1000, FASTEST_INTERVAL = 1000;
    private final static int BACKGROUD_UPDATE_TIME = 30 * 1000;
    private final static int LOCATION_ACQUIRE_TIME = 10 * 1000;
    private final static int FOREGROUND_UPDATE_TIME = 20 * 1000;
    private final static int WATCH_LOCATION_TIMEOUT = 65 * 1000;
    private final static int SEND_NEW_LOCATION_TIME = 2 * 1000;

    private ArrayList<TLRPC.TL_peerLocated> cachedNearbyUsers = new ArrayList<>();
    private ArrayList<TLRPC.TL_peerLocated> cachedNearbyChats = new ArrayList<>();

    private static volatile LocationController[] Instance = new LocationController[UserConfig.MAX_ACCOUNT_COUNT];

    public static LocationController getInstance(int num) {
        LocationController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (LocationController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new LocationController(num);
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
        public int account;
        public int proximityMeters;
        public int lastSentProximityMeters;
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
                    setLastKnownLocation(location);
                    lastLocationSendTime = SystemClock.elapsedRealtime() - BACKGROUD_UPDATE_TIME + 5000;
                }
            } else {
                setLastKnownLocation(location);
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

    public LocationController(int instance) {
        super(instance);

        locationManager = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);

        AndroidUtilities.runOnUIThread(() -> {
            LocationController locationController = getAccountInstance().getLocationController();
            getNotificationCenter().addObserver(locationController, NotificationCenter.didReceiveNewMessages);
            getNotificationCenter().addObserver(locationController, NotificationCenter.messagesDeleted);
            getNotificationCenter().addObserver(locationController, NotificationCenter.replaceMessagesObjects);
        });
        loadSharingLocations();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didReceiveNewMessages) {
            boolean scheduled = (Boolean) args[2];
            if (scheduled) {
                return;
            }
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
                        if (MessageObject.getFromChatId(messages.get(b)) == messageObject.getFromChatId()) {
                            replaced = true;
                            messages.set(b, messageObject.messageOwner);
                            break;
                        }
                    }
                    if (!replaced) {
                        messages.add(messageObject.messageOwner);
                    }
                } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGeoProximityReached) {
                    long dialogId = messageObject.getDialogId();
                    if (DialogObject.isUserDialog(dialogId)) {
                        setProximityLocation(dialogId, 0, false);
                    }
                }
            }
            if (added) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.liveLocationsCacheChanged, did, currentAccount);
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            boolean scheduled = (Boolean) args[2];
            if (scheduled) {
                return;
            }
            if (!sharingLocationsUI.isEmpty()) {
                ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>) args[0];
                long channelId = (Long) args[1];
                ArrayList<Long> toRemove = null;
                for (int a = 0; a < sharingLocationsUI.size(); a++) {
                    SharingLocationInfo info = sharingLocationsUI.get(a);
                    long messageChannelId = info.messageObject != null ? info.messageObject.getChannelId() : 0;
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
                    if (MessageObject.getFromChatId(messages.get(b)) == messageObject.getFromChatId()) {
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
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.liveLocationsCacheChanged, did, currentAccount);
            }
        }
    }

    private void broadcastLastKnownLocation(boolean cancelCurrent) {
        if (lastKnownLocation == null) {
            return;
        }
        if (requests.size() != 0) {
            if (cancelCurrent) {
                for (int a = 0; a < requests.size(); a++) {
                    getConnectionsManager().cancelRequest(requests.keyAt(a), false);
                }
            }
            requests.clear();
        }
        if (!sharingLocations.isEmpty()) {
            int date = getConnectionsManager().getCurrentTime();
            float[] result = new float[1];
            for (int a = 0; a < sharingLocations.size(); a++) {
                final SharingLocationInfo info = sharingLocations.get(a);
                if (info.messageObject.messageOwner.media != null && info.messageObject.messageOwner.media.geo != null && info.lastSentProximityMeters == info.proximityMeters) {
                    int messageDate = info.messageObject.messageOwner.edit_date != 0 ? info.messageObject.messageOwner.edit_date : info.messageObject.messageOwner.date;
                    TLRPC.GeoPoint point = info.messageObject.messageOwner.media.geo;
                    if (Math.abs(date - messageDate) < 10) {
                        Location.distanceBetween(point.lat, point._long, lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude(), result);
                        if (result[0] < 1.0f) {
                            continue;
                        }
                    }
                }
                TLRPC.TL_messages_editMessage req = new TLRPC.TL_messages_editMessage();
                req.peer = getMessagesController().getInputPeer(info.did);
                req.id = info.mid;
                req.flags |= 16384;
                req.media = new TLRPC.TL_inputMediaGeoLive();
                req.media.stopped = false;
                req.media.geo_point = new TLRPC.TL_inputGeoPoint();
                req.media.geo_point.lat = AndroidUtilities.fixLocationCoord(lastKnownLocation.getLatitude());
                req.media.geo_point._long = AndroidUtilities.fixLocationCoord(lastKnownLocation.getLongitude());
                req.media.geo_point.accuracy_radius = (int) lastKnownLocation.getAccuracy();
                if (req.media.geo_point.accuracy_radius != 0) {
                    req.media.geo_point.flags |= 1;
                }
                if (info.lastSentProximityMeters != info.proximityMeters) {
                    req.media.proximity_notification_radius = info.proximityMeters;
                    req.media.flags |= 8;
                }
                req.media.heading = getHeading(lastKnownLocation);
                req.media.flags |= 4;
                final int[] reqId = new int[1];
                reqId[0] = getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (error != null) {
                        if (error.text.equals("MESSAGE_ID_INVALID")) {
                            sharingLocations.remove(info);
                            sharingLocationsMap.remove(info.did);
                            saveSharingLocation(info, 1);
                            requests.delete(reqId[0]);
                            AndroidUtilities.runOnUIThread(() -> {
                                sharingLocationsUI.remove(info);
                                sharingLocationsMapUI.remove(info.did);
                                if (sharingLocationsUI.isEmpty()) {
                                    stopService();
                                }
                                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.liveLocationsChanged);
                            });
                        }
                        return;
                    }
                    if ((req.flags & 8) != 0) {
                        info.lastSentProximityMeters = req.media.proximity_notification_radius;
                    }
                    TLRPC.Updates updates = (TLRPC.Updates) response;
                    boolean updated = false;
                    for (int a1 = 0; a1 < updates.updates.size(); a1++) {
                        TLRPC.Update update = updates.updates.get(a1);
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
                    getMessagesController().processUpdates(updates, false);
                });
                requests.put(reqId[0], 0);
            }
        }
        if (shareMyCurrentLocation) {
            UserConfig userConfig = getUserConfig();
            userConfig.lastMyLocationShareTime = (int) (System.currentTimeMillis() / 1000);
            userConfig.saveConfig(false);

            TLRPC.TL_contacts_getLocated req = new TLRPC.TL_contacts_getLocated();
            req.geo_point = new TLRPC.TL_inputGeoPoint();
            req.geo_point.lat = lastKnownLocation.getLatitude();
            req.geo_point._long = lastKnownLocation.getLongitude();
            req.background = true;
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        }
        getConnectionsManager().resumeNetworkMaybe();
        if (shouldStopGps() || shareMyCurrentLocation) {
            shareMyCurrentLocation = false;
            stop(false);
        }
    }

    private boolean shouldStopGps() {
        return SystemClock.elapsedRealtime() > locationEndWatchTime;
    }

    protected void setNewLocationEndWatchTime() {
        if (sharingLocations.isEmpty()) {
            return;
        }
        locationEndWatchTime = SystemClock.elapsedRealtime() + WATCH_LOCATION_TIMEOUT;
        start();
    }

    protected void update() {
        UserConfig userConfig = getUserConfig();
        if (ApplicationLoader.isScreenOn && !ApplicationLoader.mainInterfacePaused && !shareMyCurrentLocation &&
                userConfig.isClientActivated() && userConfig.isConfigLoaded() && userConfig.sharingMyLocationUntil != 0 && Math.abs(System.currentTimeMillis() / 1000 - userConfig.lastMyLocationShareTime) >= 60 * 60) {
            shareMyCurrentLocation = true;
        }
        if (!sharingLocations.isEmpty()) {
            for (int a = 0; a < sharingLocations.size(); a++) {
                final SharingLocationInfo info = sharingLocations.get(a);
                int currentTime = getConnectionsManager().getCurrentTime();
                if (info.stopTime <= currentTime) {
                    sharingLocations.remove(a);
                    sharingLocationsMap.remove(info.did);
                    saveSharingLocation(info, 1);
                    AndroidUtilities.runOnUIThread(() -> {
                        sharingLocationsUI.remove(info);
                        sharingLocationsMapUI.remove(info.did);
                        if (sharingLocationsUI.isEmpty()) {
                            stopService();
                        }
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.liveLocationsChanged);
                    });
                    a--;
                }
            }
        }
        if (started) {
            long newTime = SystemClock.elapsedRealtime();
            if (lastLocationByGoogleMaps || Math.abs(lastLocationStartTime - newTime) > LOCATION_ACQUIRE_TIME || shouldSendLocationNow()) {
                lastLocationByGoogleMaps = false;
                locationSentSinceLastGoogleMapUpdate = true;
                boolean cancelAll = (SystemClock.elapsedRealtime() - lastLocationSendTime) > 2 * 1000;
                lastLocationStartTime = newTime;
                lastLocationSendTime = SystemClock.elapsedRealtime();
                broadcastLastKnownLocation(cancelAll);
            }
        } else if (!sharingLocations.isEmpty() || shareMyCurrentLocation) {
            if (shareMyCurrentLocation || Math.abs(lastLocationSendTime - SystemClock.elapsedRealtime()) > BACKGROUD_UPDATE_TIME) {
                lastLocationStartTime = SystemClock.elapsedRealtime();
                start();
            }
        }
    }

    private boolean shouldSendLocationNow() {
        if (!shouldStopGps()) {
            return false;
        }
        if (Math.abs(lastLocationSendTime - SystemClock.elapsedRealtime()) >= SEND_NEW_LOCATION_TIME) {
            return true;
        }
        return false;
    }

    public void cleanup() {
        sharingLocationsUI.clear();
        sharingLocationsMapUI.clear();
        locationsCache.clear();
        cacheRequests.clear();
        cachedNearbyUsers.clear();
        cachedNearbyChats.clear();
        lastReadLocationTime.clear();
        stopService();
        Utilities.stageQueue.postRunnable(() -> {
            locationEndWatchTime = 0;
            requests.clear();
            sharingLocationsMap.clear();
            sharingLocations.clear();
            setLastKnownLocation(null);
            stop(true);
        });
    }

    private void setLastKnownLocation(Location location) {
        if (location != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && (SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()) / 1000000000 > 60 * 5) {
            return;
        }
        lastKnownLocation = location;
        if (lastKnownLocation != null) {
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.newLocationAvailable));
        }
    }

    public void setCachedNearbyUsersAndChats(ArrayList<TLRPC.TL_peerLocated> u, ArrayList<TLRPC.TL_peerLocated> c) {
        cachedNearbyUsers = new ArrayList<>(u);
        cachedNearbyChats = new ArrayList<>(c);
    }

    public ArrayList<TLRPC.TL_peerLocated> getCachedNearbyUsers() {
        return cachedNearbyUsers;
    }

    public ArrayList<TLRPC.TL_peerLocated> getCachedNearbyChats() {
        return cachedNearbyChats;
    }

    protected void addSharingLocation(TLRPC.Message message) {
        final SharingLocationInfo info = new SharingLocationInfo();
        info.did = message.dialog_id;
        info.mid = message.id;
        info.period = message.media.period;
        info.lastSentProximityMeters = info.proximityMeters = message.media.proximity_notification_radius;
        info.account = currentAccount;
        info.messageObject = new MessageObject(currentAccount, message, false, false);
        info.stopTime = getConnectionsManager().getCurrentTime() + info.period;
        final SharingLocationInfo old = sharingLocationsMap.get(info.did);
        sharingLocationsMap.put(info.did, info);
        if (old != null) {
            sharingLocations.remove(old);
        }
        sharingLocations.add(info);
        saveSharingLocation(info, 0);
        lastLocationSendTime = SystemClock.elapsedRealtime() - BACKGROUD_UPDATE_TIME + 5000;
        AndroidUtilities.runOnUIThread(() -> {
            if (old != null) {
                sharingLocationsUI.remove(old);
            }
            sharingLocationsUI.add(info);
            sharingLocationsMapUI.put(info.did, info);
            startService();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.liveLocationsChanged);
        });
    }

    public boolean isSharingLocation(long did) {
        return sharingLocationsMapUI.indexOfKey(did) >= 0;
    }

    public SharingLocationInfo getSharingLocationInfo(long did) {
        return sharingLocationsMapUI.get(did);
    }

    public boolean setProximityLocation(long did, int meters, boolean broadcast) {
        SharingLocationInfo info = sharingLocationsMapUI.get(did);
        if (info != null) {
            info.proximityMeters = meters;
        }
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("UPDATE sharing_locations SET proximity = ? WHERE uid = ?");
                state.requery();
                state.bindInteger(1, meters);
                state.bindLong(2, did);
                state.step();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
        if (broadcast) {
            Utilities.stageQueue.postRunnable(() -> broadcastLastKnownLocation(true));
        }
        return info != null;
    }

    public static int getHeading(Location location) {
        float val = location.getBearing();
        if (val > 0 && val < 1.0f) {
            if (val < 0.5f) {
                return 360;
            } else {
                return 1;
            }
        }
        return (int) val;
    }

    private void loadSharingLocations() {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            final ArrayList<SharingLocationInfo> result = new ArrayList<>();
            final ArrayList<TLRPC.User> users = new ArrayList<>();
            final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            try {
                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT uid, mid, date, period, message, proximity FROM sharing_locations WHERE 1");
                while (cursor.next()) {
                    SharingLocationInfo info = new SharingLocationInfo();
                    info.did = cursor.longValue(0);
                    info.mid = cursor.intValue(1);
                    info.stopTime = cursor.intValue(2);
                    info.period = cursor.intValue(3);
                    info.proximityMeters = cursor.intValue(5);
                    info.account = currentAccount;
                    NativeByteBuffer data = cursor.byteBufferValue(4);
                    if (data != null) {
                        info.messageObject = new MessageObject(currentAccount, TLRPC.Message.TLdeserialize(data, data.readInt32(false), false), false, false);
                        MessagesStorage.addUsersAndChatsFromMessage(info.messageObject.messageOwner, usersToLoad, chatsToLoad);
                        data.reuse();
                    }
                    result.add(info);
                    if (DialogObject.isChatDialog(info.did)) {
                        if (!chatsToLoad.contains(-info.did)) {
                            chatsToLoad.add(-info.did);
                        }
                    } else if (DialogObject.isUserDialog(info.did)) {
                        if (!usersToLoad.contains(info.did)) {
                            usersToLoad.add(info.did);
                        }
                    }
                }
                cursor.dispose();
                if (!chatsToLoad.isEmpty()) {
                    getMessagesStorage().getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                }
                if (!usersToLoad.isEmpty()) {
                    getMessagesStorage().getUsersInternal(TextUtils.join(",", usersToLoad), users);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (!result.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    getMessagesController().putUsers(users, true);
                    getMessagesController().putChats(chats, true);
                    Utilities.stageQueue.postRunnable(() -> {
                        sharingLocations.addAll(result);
                        for (int a = 0; a < sharingLocations.size(); a++) {
                            SharingLocationInfo info = sharingLocations.get(a);
                            sharingLocationsMap.put(info.did, info);
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            sharingLocationsUI.addAll(result);
                            for (int a = 0; a < result.size(); a++) {
                                SharingLocationInfo info = result.get(a);
                                sharingLocationsMapUI.put(info.did, info);
                            }
                            startService();
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.liveLocationsChanged);
                        });
                    });
                });
            }
        });
    }

    private void saveSharingLocation(final SharingLocationInfo info, final int remove) {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                if (remove == 2) {
                    getMessagesStorage().getDatabase().executeFast("DELETE FROM sharing_locations WHERE 1").stepThis().dispose();
                } else if (remove == 1) {
                    if (info == null) {
                        return;
                    }
                    getMessagesStorage().getDatabase().executeFast("DELETE FROM sharing_locations WHERE uid = " + info.did).stepThis().dispose();
                } else {
                    if (info == null) {
                        return;
                    }
                    SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("REPLACE INTO sharing_locations VALUES(?, ?, ?, ?, ?, ?)");
                    state.requery();

                    NativeByteBuffer data = new NativeByteBuffer(info.messageObject.messageOwner.getObjectSize());
                    info.messageObject.messageOwner.serializeToStream(data);

                    state.bindLong(1, info.did);
                    state.bindInteger(2, info.mid);
                    state.bindInteger(3, info.stopTime);
                    state.bindInteger(4, info.period);
                    state.bindByteBuffer(5, data);
                    state.bindInteger(6, info.proximityMeters);

                    state.step();
                    state.dispose();
                    data.reuse();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void removeSharingLocation(final long did) {
        Utilities.stageQueue.postRunnable(() -> {
            final SharingLocationInfo info = sharingLocationsMap.get(did);
            sharingLocationsMap.remove(did);
            if (info != null) {
                TLRPC.TL_messages_editMessage req = new TLRPC.TL_messages_editMessage();
                req.peer = getMessagesController().getInputPeer(info.did);
                req.id = info.mid;
                req.flags |= 16384;
                req.media = new TLRPC.TL_inputMediaGeoLive();
                req.media.stopped = true;
                req.media.geo_point = new TLRPC.TL_inputGeoPointEmpty();
                getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (error != null) {
                        return;
                    }
                    getMessagesController().processUpdates((TLRPC.Updates) response, false);
                });
                sharingLocations.remove(info);
                saveSharingLocation(info, 1);
                AndroidUtilities.runOnUIThread(() -> {
                    sharingLocationsUI.remove(info);
                    sharingLocationsMapUI.remove(info.did);
                    if (sharingLocationsUI.isEmpty()) {
                        stopService();
                    }
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.liveLocationsChanged);
                });
                if (sharingLocations.isEmpty()) {
                    stop(true);
                }
            }
        });
    }

    private void startService() {
        try {
            /*if (Build.VERSION.SDK_INT >= 26) {
                ApplicationLoader.applicationContext.startForegroundService(new Intent(ApplicationLoader.applicationContext, LocationSharingService.class));
            } else {*/
                ApplicationLoader.applicationContext.startService(new Intent(ApplicationLoader.applicationContext, LocationSharingService.class));
            //}
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void stopService() {
        ApplicationLoader.applicationContext.stopService(new Intent(ApplicationLoader.applicationContext, LocationSharingService.class));
    }

    public void removeAllLocationSharings() {
        Utilities.stageQueue.postRunnable(() -> {
            for (int a = 0; a < sharingLocations.size(); a++) {
                SharingLocationInfo info = sharingLocations.get(a);
                TLRPC.TL_messages_editMessage req = new TLRPC.TL_messages_editMessage();
                req.peer = getMessagesController().getInputPeer(info.did);
                req.id = info.mid;
                req.flags |= 16384;
                req.media = new TLRPC.TL_inputMediaGeoLive();
                req.media.stopped = true;
                req.media.geo_point = new TLRPC.TL_inputGeoPointEmpty();
                getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (error != null) {
                        return;
                    }
                    getMessagesController().processUpdates((TLRPC.Updates) response, false);
                });
            }
            sharingLocations.clear();
            sharingLocationsMap.clear();
            saveSharingLocation(null, 2);
            stop(true);
            AndroidUtilities.runOnUIThread(() -> {
                sharingLocationsUI.clear();
                sharingLocationsMapUI.clear();
                stopService();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.liveLocationsChanged);
            });
        });
    }

    public void setGoogleMapLocation(Location location, boolean first) {
        if (location == null) {
            return;
        }
        lastLocationByGoogleMaps = true;
        if (first || lastKnownLocation != null && lastKnownLocation.distanceTo(location) >= 20) {
            lastLocationSendTime = SystemClock.elapsedRealtime() - BACKGROUD_UPDATE_TIME;
            locationSentSinceLastGoogleMapUpdate = false;
        } else if (locationSentSinceLastGoogleMapUpdate) {
            lastLocationSendTime = SystemClock.elapsedRealtime() - BACKGROUD_UPDATE_TIME + FOREGROUND_UPDATE_TIME;
            locationSentSinceLastGoogleMapUpdate = false;
        }
        setLastKnownLocation(location);
    }

    // TFOSS it asks properly anyway
    @SuppressLint("MissingPermission")
    private void start() {
        if (started) {
            return;
        }
        lastLocationStartTime = SystemClock.elapsedRealtime();
        started = true;
        boolean ok = false;
        if (!ok) {
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
                    setLastKnownLocation(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
                    if (lastKnownLocation == null) {
                        setLastKnownLocation(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    private void stop(boolean empty) {
        if (lookingForPeopleNearby || shareMyCurrentLocation) {
            return;
        }
        started = false;
        locationManager.removeUpdates(gpsLocationListener);
        if (empty) {
            locationManager.removeUpdates(networkLocationListener);
            locationManager.removeUpdates(passiveLocationListener);
        }
    }

    public void startLocationLookupForPeopleNearby(boolean stop) {
        Utilities.stageQueue.postRunnable(() -> {
            lookingForPeopleNearby = !stop;
            if (lookingForPeopleNearby) {
                start();
            } else if (sharingLocations.isEmpty()) {
                stop(true);
            }
        });
    }

    public Location getLastKnownLocation() {
        return lastKnownLocation;
    }

    public void loadLiveLocations(long did) {
        if (cacheRequests.indexOfKey(did) >= 0) {
            return;
        }
        cacheRequests.put(did, true);
        TLRPC.TL_messages_getRecentLocations req = new TLRPC.TL_messages_getRecentLocations();
        req.peer = getMessagesController().getInputPeer(did);
        req.limit = 100;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error != null) {
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                cacheRequests.delete(did);
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                for (int a = 0; a < res.messages.size(); a++) {
                    if (!(res.messages.get(a).media instanceof TLRPC.TL_messageMediaGeoLive)) {
                        res.messages.remove(a);
                        a--;
                    }
                }
                getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                getMessagesController().putUsers(res.users, false);
                getMessagesController().putChats(res.chats, false);
                locationsCache.put(did, res.messages);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.liveLocationsCacheChanged, did, currentAccount);
            });
        });
    }

    public void markLiveLoactionsAsRead(long dialogId) {
        if (DialogObject.isEncryptedDialog(dialogId)) {
            return;
        }
        ArrayList<TLRPC.Message> messages = locationsCache.get(dialogId);
        if (messages == null || messages.isEmpty()) {
            return;
        }
        Integer date = lastReadLocationTime.get(dialogId);
        int currentDate = (int) (SystemClock.elapsedRealtime() / 1000);
        if (date != null && date + 60 > currentDate) {
            return;
        }
        lastReadLocationTime.put(dialogId, currentDate);
        TLObject request;
        if (DialogObject.isChatDialog(dialogId) && ChatObject.isChannel(-dialogId, currentAccount)) {
            TLRPC.TL_channels_readMessageContents req = new TLRPC.TL_channels_readMessageContents();
            for (int a = 0, N = messages.size(); a < N; a++) {
                req.id.add(messages.get(a).id);
            }
            req.channel = getMessagesController().getInputChannel(-dialogId);
            request = req;
        } else {
            TLRPC.TL_messages_readMessageContents req = new TLRPC.TL_messages_readMessageContents();
            for (int a = 0, N = messages.size(); a < N; a++) {
                req.id.add(messages.get(a).id);
            }
            request = req;
        }
        getConnectionsManager().sendRequest(request, (response, error) -> {
            if (response instanceof TLRPC.TL_messages_affectedMessages) {
                TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                getMessagesController().processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
            }
        });
    }

    public static int getLocationsCount() {
        int count = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            count += LocationController.getInstance(a).sharingLocationsUI.size();
        }
        return count;
    }

    public interface LocationFetchCallback {
        void onLocationAddressAvailable(String address, String displayAddress, Location location);
    }

    private static HashMap<LocationFetchCallback, Runnable> callbacks = new HashMap<>();
    public static void fetchLocationAddress(Location location, LocationFetchCallback callback) {
        if (callback == null) {
            return;
        }
        Runnable fetchLocationRunnable = callbacks.get(callback);
        if (fetchLocationRunnable != null) {
            Utilities.globalQueue.cancelRunnable(fetchLocationRunnable);
            callbacks.remove(callback);
        }
        if (location == null) {
            callback.onLocationAddressAvailable(null, null, null);
            return;
        }

        Utilities.globalQueue.postRunnable(fetchLocationRunnable = () -> {
            String name;
            String displayName;
            try {
                Geocoder gcd = new Geocoder(ApplicationLoader.applicationContext, LocaleController.getInstance().getSystemDefaultLocale());
                List<Address> addresses = gcd.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (addresses.size() > 0) {
                    Address address = addresses.get(0);
                    boolean hasAny = false;
                    String arg;

                    StringBuilder nameBuilder = new StringBuilder();
                    StringBuilder displayNameBuilder = new StringBuilder();

                    arg = address.getSubThoroughfare();
                    if (!TextUtils.isEmpty(arg)) {
                        nameBuilder.append(arg);
                        hasAny = true;
                    }
                    arg = address.getThoroughfare();
                    if (!TextUtils.isEmpty(arg)) {
                        if (nameBuilder.length() > 0) {
                            nameBuilder.append(" ");
                        }
                        nameBuilder.append(arg);
                        hasAny = true;
                    }
                    if (!hasAny) {
                        arg = address.getAdminArea();
                        if (!TextUtils.isEmpty(arg)) {
                            if (nameBuilder.length() > 0) {
                                nameBuilder.append(", ");
                            }
                            nameBuilder.append(arg);
                        }
                        arg = address.getSubAdminArea();
                        if (!TextUtils.isEmpty(arg)) {
                            if (nameBuilder.length() > 0) {
                                nameBuilder.append(", ");
                            }
                            nameBuilder.append(arg);
                        }
                    }
                    arg = address.getLocality();
                    if (!TextUtils.isEmpty(arg)) {
                        if (nameBuilder.length() > 0) {
                            nameBuilder.append(", ");
                        }
                        nameBuilder.append(arg);
                    }
                    arg = address.getCountryName();
                    if (!TextUtils.isEmpty(arg)) {
                        if (nameBuilder.length() > 0) {
                            nameBuilder.append(", ");
                        }
                        nameBuilder.append(arg);
                    }

                    arg = address.getCountryName();
                    if (!TextUtils.isEmpty(arg)) {
                        if (displayNameBuilder.length() > 0) {
                            displayNameBuilder.append(", ");
                        }
                        displayNameBuilder.append(arg);
                    }
                    arg = address.getLocality();
                    if (!TextUtils.isEmpty(arg)) {
                        if (displayNameBuilder.length() > 0) {
                            displayNameBuilder.append(", ");
                        }
                        displayNameBuilder.append(arg);
                    }
                    if (!hasAny) {
                        arg = address.getAdminArea();
                        if (!TextUtils.isEmpty(arg)) {
                            if (displayNameBuilder.length() > 0) {
                                displayNameBuilder.append(", ");
                            }
                            displayNameBuilder.append(arg);
                        }
                        arg = address.getSubAdminArea();
                        if (!TextUtils.isEmpty(arg)) {
                            if (displayNameBuilder.length() > 0) {
                                displayNameBuilder.append(", ");
                            }
                            displayNameBuilder.append(arg);
                        }
                    }

                    name = nameBuilder.toString();
                    displayName = displayNameBuilder.toString();
                } else {
                    name = displayName = String.format(Locale.US, "Unknown address (%f,%f)", location.getLatitude(), location.getLongitude());
                }
            } catch (Exception ignore) {
                name = displayName = String.format(Locale.US, "Unknown address (%f,%f)", location.getLatitude(), location.getLongitude());
            }
            final String nameFinal = name;
            final String displayNameFinal = displayName;
            AndroidUtilities.runOnUIThread(() -> {
                callbacks.remove(callback);
                callback.onLocationAddressAvailable(nameFinal, displayNameFinal, location);
            });
        }, 300);
        callbacks.put(callback, fetchLocationRunnable);
    }
}

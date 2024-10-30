/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.Manifest;
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
import android.util.Log;
import android.util.SparseIntArray;

import androidx.collection.LongSparseArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.Components.PermissionRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

@SuppressLint("MissingPermission")
public class LocationController extends BaseController implements NotificationCenter.NotificationCenterDelegate, ILocationServiceProvider.IAPIConnectionCallbacks, ILocationServiceProvider.IAPIOnConnectionFailedListener {

    private LongSparseArray<SharingLocationInfo> sharingLocationsMap = new LongSparseArray<>();
    private ArrayList<SharingLocationInfo> sharingLocations = new ArrayList<>();
    public LongSparseArray<ArrayList<TLRPC.Message>> locationsCache = new LongSparseArray<>();
    private LongSparseArray<Integer> lastReadLocationTime = new LongSparseArray<>();
    private LocationManager locationManager;
    private GpsLocationListener gpsLocationListener = new GpsLocationListener();
    private GpsLocationListener networkLocationListener = new GpsLocationListener();
    private GpsLocationListener passiveLocationListener = new GpsLocationListener();
    private FusedLocationListener fusedLocationListener = new FusedLocationListener();
    private Location lastKnownLocation;
    private long lastLocationSendTime;
    private boolean locationSentSinceLastMapUpdate = true;
    private long lastLocationStartTime;
    private boolean started;
    private boolean lastLocationByMaps;
    private SparseIntArray requests = new SparseIntArray();
    private LongSparseArray<Boolean> cacheRequests = new LongSparseArray<>();
    private long locationEndWatchTime;

    public ArrayList<SharingLocationInfo> sharingLocationsUI = new ArrayList<>();
    private LongSparseArray<SharingLocationInfo> sharingLocationsMapUI = new LongSparseArray<>();

    private Boolean servicesAvailable;
    private boolean wasConnectedToPlayServices;
    private ILocationServiceProvider.IMapApiClient apiClient;
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private final static long UPDATE_INTERVAL = 1000, FASTEST_INTERVAL = 1000;
    private final static int BACKGROUD_UPDATE_TIME = 30 * 1000;
    private final static int LOCATION_ACQUIRE_TIME = 10 * 1000;
    private final static int FOREGROUND_UPDATE_TIME = 20 * 1000;
    private final static int WATCH_LOCATION_TIMEOUT = 65 * 1000;
    private final static int SEND_NEW_LOCATION_TIME = 2 * 1000;

    private ILocationServiceProvider.ILocationRequest locationRequest;

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

    private class FusedLocationListener implements ILocationServiceProvider.ILocationListener {

        @Override
        public void onLocationChanged(Location location) {
            if (location == null) {
                return;
            }
            setLastKnownLocation(location);
        }
    }

    public LocationController(int instance) {
        super(instance);

        locationManager = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
        apiClient = ApplicationLoader.getLocationServiceProvider().onCreateLocationServicesAPI(ApplicationLoader.applicationContext, this, this);

        locationRequest = ApplicationLoader.getLocationServiceProvider().onCreateLocationRequest();
        locationRequest.setPriority(ILocationServiceProvider.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);

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

    @Override
    public void onConnected(Bundle bundle) {
        wasConnectedToPlayServices = true;
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                ApplicationLoader.getLocationServiceProvider().checkLocationSettings(locationRequest, status -> {
                    switch (status) {
                        case ILocationServiceProvider.STATUS_SUCCESS:
                            startFusedLocationRequest(true);
                            break;
                        case ILocationServiceProvider.STATUS_RESOLUTION_REQUIRED:
                            Utilities.stageQueue.postRunnable(() -> {
                                if (!sharingLocations.isEmpty()) {
                                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.needShowPlayServicesAlert, status));
                                }
                            });
                            break;
                        case ILocationServiceProvider.STATUS_SETTINGS_CHANGE_UNAVAILABLE:
                            Utilities.stageQueue.postRunnable(() -> {
                                servicesAvailable = false;
                                try {
                                    apiClient.disconnect();
                                    start();
                                } catch (Throwable ignore) {}
                            });
                            break;
                    }
                });
            } else {
                startFusedLocationRequest(true);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void startFusedLocationRequest(boolean permissionsGranted) {
        Utilities.stageQueue.postRunnable(() -> {
            if (!permissionsGranted) {
                servicesAvailable = false;
            }
            if (!sharingLocations.isEmpty()) {
                if (permissionsGranted) {
                    try {
                        ApplicationLoader.getLocationServiceProvider().getLastLocation(this::setLastKnownLocation);
                        ApplicationLoader.getLocationServiceProvider().requestLocationUpdates(locationRequest, fusedLocationListener);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                } else {
                    start();
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed() {
        if (wasConnectedToPlayServices) {
            return;
        }
        servicesAvailable = false;
        if (started) {
            started = false;
            start();
        }
    }

    private boolean checkServices() {
        if (servicesAvailable == null) {
            servicesAvailable = ApplicationLoader.getLocationServiceProvider().checkServices();
        }
        return servicesAvailable;
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
        getConnectionsManager().resumeNetworkMaybe();
        if (shouldStopGps()) {
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
            if (lastLocationByMaps || Math.abs(lastLocationStartTime - newTime) > LOCATION_ACQUIRE_TIME || shouldSendLocationNow()) {
                lastLocationByMaps = false;
                locationSentSinceLastMapUpdate = true;
                boolean cancelAll = (SystemClock.elapsedRealtime() - lastLocationSendTime) > 2 * 1000;
                lastLocationStartTime = newTime;
                lastLocationSendTime = SystemClock.elapsedRealtime();
                broadcastLastKnownLocation(cancelAll);
            }
        } else if (!sharingLocations.isEmpty()) {
            if (Math.abs(lastLocationSendTime - SystemClock.elapsedRealtime()) > BACKGROUD_UPDATE_TIME) {
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

    protected void addSharingLocation(TLRPC.Message message) {
        final SharingLocationInfo info = new SharingLocationInfo();
        info.did = message.dialog_id;
        info.mid = message.id;
        info.period = message.media.period;
        info.lastSentProximityMeters = info.proximityMeters = message.media.proximity_notification_radius;
        info.account = currentAccount;
        info.messageObject = new MessageObject(currentAccount, message, false, false);
        if (info.period == 0x7FFFFFFF) {
            info.stopTime = Integer.MAX_VALUE;
        } else {
            info.stopTime = getConnectionsManager().getCurrentTime() + info.period;
        }
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
                        MessagesStorage.addUsersAndChatsFromMessage(info.messageObject.messageOwner, usersToLoad, chatsToLoad, null);
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
                getMessagesStorage().getUsersInternal(usersToLoad, users);
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
            if (PermissionRequest.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) || PermissionRequest.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                ApplicationLoader.applicationContext.startService(new Intent(ApplicationLoader.applicationContext, LocationSharingService.class));
            }
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

    public void setMapLocation(Location location, boolean first) {
        if (location == null) {
            return;
        }
        lastLocationByMaps = true;
        if (first || lastKnownLocation != null && lastKnownLocation.distanceTo(location) >= 20) {
            lastLocationSendTime = SystemClock.elapsedRealtime() - BACKGROUD_UPDATE_TIME;
            locationSentSinceLastMapUpdate = false;
        } else if (locationSentSinceLastMapUpdate) {
            lastLocationSendTime = SystemClock.elapsedRealtime() - BACKGROUD_UPDATE_TIME + FOREGROUND_UPDATE_TIME;
            locationSentSinceLastMapUpdate = false;
        }
        setLastKnownLocation(location);
    }

    private void start() {
        if (started) {
            return;
        }
        lastLocationStartTime = SystemClock.elapsedRealtime();
        started = true;
        boolean ok = false;
        if (checkServices()) {
            try {
                apiClient.connect();
                ok = true;
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
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
        started = false;
        if (checkServices()) {
            try {
                ApplicationLoader.getLocationServiceProvider().removeLocationUpdates(fusedLocationListener);
                apiClient.disconnect();
            } catch (Throwable e) {
                FileLog.e(e, false);
            }
        }
        locationManager.removeUpdates(gpsLocationListener);
        if (empty) {
            locationManager.removeUpdates(networkLocationListener);
            locationManager.removeUpdates(passiveLocationListener);
        }
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
        void onLocationAddressAvailable(String address, String displayAddress, TLRPC.TL_messageMediaVenue city, TLRPC.TL_messageMediaVenue street, Location location);
    }

    public static final int TYPE_BIZ = 1;
    public static final int TYPE_STORY = 2;

    // google geocoder thinks that "unnamed road" is a street name
    public static String[] unnamedRoads = {
        "Unnamed Road",
        "Вulicya bez nazvi",
        "Нeizvestnaya doroga",
        "İsimsiz Yol",
        "Ceļš bez nosaukuma",
        "Kelias be pavadinimo",
        "Droga bez nazwy",
        "Cesta bez názvu",
        "Silnice bez názvu",
        "Drum fără nume",
        "Route sans nom",
        "Vía sin nombre",
        "Estrada sem nome",
        "Οdos xoris onomasia",
        "Rrugë pa emër",
        "Пat bez ime",
        "Нeimenovani put",
        "Strada senza nome",
        "Straße ohne Straßennamen"
    };

    private static HashMap<LocationFetchCallback, Runnable> callbacks = new HashMap<>();
    public static void fetchLocationAddress(Location location, LocationFetchCallback callback) {
        fetchLocationAddress(location, 0, callback);
    }
    public static void fetchLocationAddress(Location location, int type, LocationFetchCallback callback) {
        if (callback == null) {
            return;
        }
        Runnable fetchLocationRunnable = callbacks.get(callback);
        if (fetchLocationRunnable != null) {
            Utilities.globalQueue.cancelRunnable(fetchLocationRunnable);
            callbacks.remove(callback);
        }
        if (location == null) {
            callback.onLocationAddressAvailable(null, null, null, null, null);
            return;
        }

        Locale locale;
        Locale englishLocale;
        try {
            locale = LocaleController.getInstance().getCurrentLocale();
        } catch (Exception ignore) {
            locale = LocaleController.getInstance().getSystemDefaultLocale();
        }
        if (locale.getLanguage().contains("en")) {
            englishLocale = locale;
        } else {
            englishLocale = Locale.US;
        }
        final Locale finalLocale = locale;
        Utilities.globalQueue.postRunnable(fetchLocationRunnable = () -> {
            String name, displayName, city, street, countryCode = null, locality = null, feature = null, engFeature = null;
            String engState = null, engCity = null;
            StringBuilder engStreet = new StringBuilder();
            boolean onlyCountry = true;
            TLRPC.TL_messageMediaVenue cityLocation = null;
            TL_stories.TL_geoPointAddress cityAddress = new TL_stories.TL_geoPointAddress();
            TLRPC.TL_messageMediaVenue streetLocation = null;
            TL_stories.TL_geoPointAddress streetAddress = new TL_stories.TL_geoPointAddress();
            try {
                Geocoder gcd = new Geocoder(ApplicationLoader.applicationContext, finalLocale);
                List<Address> addresses = gcd.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                List<Address> engAddresses = null;
                if (type == TYPE_STORY) {
                    if (englishLocale == finalLocale) {
                        engAddresses = addresses;
                    } else {
                        Geocoder gcd2 = new Geocoder(ApplicationLoader.applicationContext, englishLocale);
                        engAddresses = gcd2.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    }
                }
                if (addresses.size() > 0) {
                    Address address = addresses.get(0);
                    Address engAddress = engAddresses != null && engAddresses.size() >= 1 ? engAddresses.get(0) : null;
                    if (type == TYPE_BIZ) {
                        ArrayList<String> parts = new ArrayList<>();

                        String arg = null;
                        try {
                            arg = address.getAddressLine(0);
                        } catch (Exception ignore) {}
                        if (TextUtils.isEmpty(arg)) {
                            try {
                                parts.add(address.getSubThoroughfare());
                            } catch (Exception ignore) {}
                            try {
                                parts.add(address.getThoroughfare());
                            } catch (Exception ignore) {}
                            try {
                                parts.add(address.getAdminArea());
                            } catch (Exception ignore) {}
                            try {
                                parts.add(address.getCountryName());
                            } catch (Exception ignore) {}
                        } else {
                            parts.add(arg);
                        }

                        for (int i = 0; i < parts.size(); ++i) {
                            if (parts.get(i) != null) {
                                String[] partsInside = parts.get(i).split(", ");
                                if (partsInside.length > 1) {
                                    parts.remove(i);
                                    for (int j = 0; j < partsInside.length; ++j) {
                                        parts.add(i, partsInside[j]);
                                        i++;
                                    }
                                }
                            }
                        }
                        for (int i = 0; i < parts.size(); ++i) {
                            if (TextUtils.isEmpty(parts.get(i)) || parts.indexOf(parts.get(i)) != i || parts.get(i).matches("^\\s*\\d{4,}\\s*$")) {
                                parts.remove(i);
                                i--;
                            }
                        }

                        name = displayName = parts.isEmpty() ? null : TextUtils.join(", ", parts);
                        city = null;
                        street = null;
                        countryCode = null;
                    } else {

                        boolean hasAny = false;
                        String arg;

                        StringBuilder nameBuilder = new StringBuilder();
                        StringBuilder displayNameBuilder = new StringBuilder();
                        StringBuilder cityBuilder = new StringBuilder();
                        StringBuilder streetBuilder = new StringBuilder();

//                    String addressLine = null;
//                    try {
//                        addressLine = address.getAddressLine(0);
//                    } catch (Exception ignore) {}
//                    if (addressLine != null) {
//                        String postalCode = address.getPostalCode();
//                        if (postalCode != null) {
//                            addressLine = addressLine.replace(" " + postalCode, "");
//                            addressLine = addressLine.replace(postalCode, "");
//                        }
//                        String[] parts = addressLine.split(", ");
//                        if (parts.length > 2) {
//                            String _country = parts[parts.length - 1].replace(",", "").trim();
//                            String _city = parts[parts.length - 2].replace(",", "").trim();
////                            if (_city.length() > 3) {
////                                locality = _city;
////                            }
////                            feature = parts[0].replace(",", "").trim();
//                        }
//                    }

                        if (TextUtils.isEmpty(locality)) {
                            locality = address.getLocality();
                        }
                        if (TextUtils.isEmpty(locality)) {
                            locality = address.getAdminArea();
                        }
                        if (TextUtils.isEmpty(locality)) {
                            locality = address.getSubAdminArea();
                        }
                        if (engAddress != null) {
                            if (TextUtils.isEmpty(engCity)) {
                                engCity = engAddress.getLocality();
                            }
                            if (TextUtils.isEmpty(engCity)) {
                                engCity = engAddress.getAdminArea();
                            }
                            if (TextUtils.isEmpty(engCity)) {
                                engCity = engAddress.getSubAdminArea();
                            }

                            engState = engAddress.getAdminArea();
                        }

                        if (TextUtils.isEmpty(feature) && !TextUtils.equals(address.getThoroughfare(), locality) && !TextUtils.equals(address.getThoroughfare(), address.getCountryName())) {
                            feature = address.getThoroughfare();
                        }
                        if (TextUtils.isEmpty(feature) && !TextUtils.equals(address.getSubLocality(), locality) && !TextUtils.equals(address.getSubLocality(), address.getCountryName())) {
                            feature = address.getSubLocality();
                        }
                        if (TextUtils.isEmpty(feature) && !TextUtils.equals(address.getLocality(), locality) && !TextUtils.equals(address.getLocality(), address.getCountryName())) {
                            feature = address.getLocality();
                        }
                        if (!TextUtils.isEmpty(feature) && !TextUtils.equals(feature, locality) && !TextUtils.equals(feature, address.getCountryName())) {
                            if (streetBuilder.length() > 0) {
                                streetBuilder.append(", ");
                            }
                            streetBuilder.append(feature);
                        } else {
                            streetBuilder = null;
                        }

                        if (engAddress != null) {
                            if (TextUtils.isEmpty(engFeature) && !TextUtils.equals(engAddress.getThoroughfare(), locality) && !TextUtils.equals(engAddress.getThoroughfare(), engAddress.getCountryName())) {
                                engFeature = engAddress.getThoroughfare();
                            }
                            if (TextUtils.isEmpty(engFeature) && !TextUtils.equals(engAddress.getSubLocality(), locality) && !TextUtils.equals(engAddress.getSubLocality(), engAddress.getCountryName())) {
                                engFeature = engAddress.getSubLocality();
                            }
                            if (TextUtils.isEmpty(engFeature) && !TextUtils.equals(engAddress.getLocality(), locality) && !TextUtils.equals(engAddress.getLocality(), engAddress.getCountryName())) {
                                engFeature = engAddress.getLocality();
                            }
                            if (!TextUtils.isEmpty(engFeature) && !TextUtils.equals(engFeature, engState) && !TextUtils.equals(engFeature, engAddress.getCountryName())) {
                                if (engStreet.length() > 0) {
                                    engStreet.append(", ");
                                }
                                engStreet.append(engFeature);
                            } else {
                                engStreet = null;
                            }

                            if (!TextUtils.isEmpty(engStreet)) {
                                boolean isUnnamed = false;
                                for (int i = 0; i < unnamedRoads.length; ++i) {
                                    if (unnamedRoads[i].equalsIgnoreCase(engStreet.toString())) {
                                        isUnnamed = true;
                                        break;
                                    }
                                }
                                if (isUnnamed) {
                                    engStreet = null;
                                    streetBuilder = null;
                                }
                            }
                        }

                        if (!TextUtils.isEmpty(locality)) {
                            if (cityBuilder.length() > 0) {
                                cityBuilder.append(", ");
                            }
                            cityBuilder.append(locality);
                            onlyCountry = false;
                            if (streetBuilder != null) {
                                if (streetBuilder.length() > 0) {
                                    streetBuilder.append(", ");
                                }
                                streetBuilder.append(locality);
                            }
                        }

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
                        countryCode = address.getCountryCode();
                        arg = address.getCountryName();
                        if (!TextUtils.isEmpty(arg)) {
                            if (nameBuilder.length() > 0) {
                                nameBuilder.append(", ");
                            }
                            nameBuilder.append(arg);
                            String shortCountry = arg;
                            final String lng = finalLocale.getLanguage();
                            if (("US".equals(address.getCountryCode()) || "AE".equals(address.getCountryCode())) && ("en".equals(lng) || "uk".equals(lng) || "ru".equals(lng)) || "GB".equals(address.getCountryCode()) && "en".equals(lng)) {
                                shortCountry = "";
                                String[] words = arg.split(" ");
                                for (String word : words) {
                                    if (word.length() > 0)
                                        shortCountry += word.charAt(0);
                                }
                            } else if ("US".equals(address.getCountryCode())) {
                                shortCountry = "USA";
                            }
                            if (cityBuilder.length() > 0) {
                                cityBuilder.append(", ");
                            }
                            cityBuilder.append(shortCountry);
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
                        city = cityBuilder.toString();
                        street = streetBuilder == null ? null : streetBuilder.toString();
                    }
                } else {
                    if (type == TYPE_BIZ) {
                        name = displayName = null;
                    } else {
                        name = displayName = String.format(Locale.US, "Unknown address (%f,%f)", location.getLatitude(), location.getLongitude());
                    }
                    city = null;
                    street = null;
                }
                if (!TextUtils.isEmpty(city)) {
                    cityLocation = new TLRPC.TL_messageMediaVenue();
                    cityLocation.geo = new TLRPC.TL_geoPoint();
                    cityLocation.geo.lat = location.getLatitude();
                    cityLocation.geo._long = location.getLongitude();
                    cityLocation.query_id = -1;
                    cityLocation.title = city;
                    cityLocation.icon = onlyCountry ? "https://ss3.4sqi.net/img/categories_v2/building/government_capitolbuilding_64.png" : "https://ss3.4sqi.net/img/categories_v2/travel/hotel_64.png";
                    cityLocation.emoji = countryCodeToEmoji(countryCode);
                    cityLocation.address = onlyCountry ? LocaleController.getString(R.string.Country) : LocaleController.getString(R.string.PassportCity);

                    cityLocation.geoAddress = cityAddress;
                    cityAddress.country_iso2 = countryCode;
                    if (!onlyCountry) {
                        if (!TextUtils.isEmpty(engState)) {
                            cityAddress.flags |= 1;
                            cityAddress.state = engState;
                        }
                        if (!TextUtils.isEmpty(engCity)) {
                            cityAddress.flags |= 2;
                            cityAddress.city = engCity;
                        }
                    }
                }
                if (!TextUtils.isEmpty(street)) {
                    streetLocation = new TLRPC.TL_messageMediaVenue();
                    streetLocation.geo = new TLRPC.TL_geoPoint();
                    streetLocation.geo.lat = location.getLatitude();
                    streetLocation.geo._long = location.getLongitude();
                    streetLocation.query_id = -1;
                    streetLocation.title = street;
                    streetLocation.icon = "pin";
                    streetLocation.address = LocaleController.getString(R.string.PassportStreet1);

                    streetLocation.geoAddress = streetAddress;
                    streetAddress.country_iso2 = countryCode;
                    if (!TextUtils.isEmpty(engState)) {
                        streetAddress.flags |= 1;
                        streetAddress.state = engState;
                    }
                    if (!TextUtils.isEmpty(engCity)) {
                        streetAddress.flags |= 2;
                        streetAddress.city = engCity;
                    }
                    if (!TextUtils.isEmpty(engStreet)) {
                        streetAddress.flags |= 4;
                        streetAddress.street = engStreet.toString();
                    }
                }
                if (cityLocation == null && streetLocation == null && location != null) {
                    String ocean = detectOcean(location.getLongitude(), location.getLatitude());
                    if (ocean != null) {
                        cityLocation = new TLRPC.TL_messageMediaVenue();
                        cityLocation.geo = new TLRPC.TL_geoPoint();
                        cityLocation.geo.lat = location.getLatitude();
                        cityLocation.geo._long = location.getLongitude();
                        cityLocation.query_id = -1;
                        cityLocation.title = ocean;
                        cityLocation.icon = "pin";
                        cityLocation.emoji = "🌊";
                        cityLocation.address = "Ocean";
                    }
                }
            } catch (Exception ignore) {
                name = displayName = String.format(Locale.US, "Unknown address (%f,%f)", location.getLatitude(), location.getLongitude());
                city = null;
                street = null;
            }
            final String nameFinal = name;
            final String displayNameFinal = displayName;
            final TLRPC.TL_messageMediaVenue finalCityLocation = cityLocation;
            final TLRPC.TL_messageMediaVenue finalStreetLocation = streetLocation;
            AndroidUtilities.runOnUIThread(() -> {
                callbacks.remove(callback);
                callback.onLocationAddressAvailable(nameFinal, displayNameFinal, finalCityLocation, finalStreetLocation, location);
            });
        }, 300);
        callbacks.put(callback, fetchLocationRunnable);
    }

    public static String countryCodeToEmoji(String code) {
        if (code == null) {
            return null;
        }
        code = code.toUpperCase();
        final int count = code.codePointCount(0, code.length());
        if (count > 2) {
            return null;
        }
        StringBuilder flag = new StringBuilder();
        for (int j = 0; j < count; ++j) {
            flag.append(Character.toChars(Character.codePointAt(code, j) - 0x41 + 0x1F1E6));
        }
        return flag.toString();
    }

    public static String detectOcean(double x, double y) {
        if (y > 65) {
            return "Arctic Ocean";
        }
        if (x > -88 && x < 40 && y > 0 || x > -60 && x < 20 && y <= 0) {
            return "Atlantic Ocean";
        }
        if (y <= 30 && x >= 20 && x < 150) {
            return "Indian Ocean";
        }
        if ((x > 106 || x < -60) && y > 0 || (x > 150 || x < -60) && y <= 0) {
            return "Pacific Ocean";
        }
        return null;
    }
}

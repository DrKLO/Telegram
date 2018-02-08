package org.cloudveil.messenger.service;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.Gson;

import org.cloudveil.messenger.GlobalSecuritySettings;
import org.cloudveil.messenger.api.model.request.SettingsRequest;
import org.cloudveil.messenger.api.model.response.SettingsResponse;
import org.cloudveil.messenger.api.service.holder.ServiceClientHolders;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Dmitriy on 05.02.2018.
 */

public class ChannelCheckingService extends Service {
    private static final String ACTION_CHECK_CHANNELS = "org.cloudveil.messenger.service.check.channels";
    private static final long DEBOUNCE_TIME_MS = 200;
    private Disposable subscription;
    Handler handler = new Handler();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void startDataChecking(@NonNull Context context) {
        Intent intent = new Intent(ACTION_CHECK_CHANNELS);
        intent.setClass(context, ChannelCheckingService.class);
        context.startService(intent);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null && intent.getAction().equals(ACTION_CHECK_CHANNELS)) {
            handler.removeCallbacks(checkDataRunnable);
            handler.postDelayed(checkDataRunnable, DEBOUNCE_TIME_MS);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    Runnable checkDataRunnable = new Runnable() {
        @Override
        public void run() {
            sendDataCheckRequest();
        }
    };

    private void sendDataCheckRequest() {
        SettingsRequest request = new SettingsRequest();
        addDialogsToRequest(request);

        request.userPhone = UserConfig.getCurrentUser().phone;
        request.userId = UserConfig.getCurrentUser().id;
        request.userName = UserConfig.getCurrentUser().username;

        if (request.isEmpty()) {
            return;
        }

        final SettingsResponse cached = loadFromCache();
        if(!ConnectionsManager.isNetworkOnline()) {
            if(cached != null) {
                processResponse(cached);
            }
            return;
        }

        NotificationCenter.getInstance().postNotificationName(NotificationCenter.filterDialogsReady);
        subscription = ServiceClientHolders.getSettingsService().loadSettings(request).
                subscribeOn(Schedulers.io()).
                observeOn(AndroidSchedulers.mainThread()).
                subscribe(new Consumer<SettingsResponse>() {

                    @Override
                    public void accept(SettingsResponse settingsResponse) throws Exception {
                        processResponse(settingsResponse);
                        freeSubscription();

                        saveToCache(settingsResponse);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        freeSubscription();
                        if(cached != null) {
                            processResponse(cached);
                        }
                    }
                });
    }

    private void processResponse(@NonNull SettingsResponse settingsResponse) {
        ConcurrentHashMap<Long, Boolean> allowedDialogs = MessagesController.getInstance().allowedDialogs;
        allowedDialogs.clear();
        for (Long channelId : settingsResponse.channels) {
            allowedDialogs.put(channelId, true);
        }
        for (Long groupId : settingsResponse.groups) {
            allowedDialogs.put(groupId, true);
        }


        ConcurrentHashMap<Long, Boolean> allowedBots = MessagesController.getInstance().allowedBots;
        allowedBots.clear();
        for (Long groupId : settingsResponse.bots) {
            allowedBots.put(groupId, true);
        }

        GlobalSecuritySettings.setDisableSecretChat(!settingsResponse.secretChat);

        NotificationCenter.getInstance().postNotificationName(NotificationCenter.filterDialogsReady);
    }

    private SettingsResponse loadFromCache() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(this.getClass().getCanonicalName(), Activity.MODE_PRIVATE);
        String json = preferences.getString("settings", null);
        if (json == null) {
            return null;
        }
        return new Gson().fromJson(json, SettingsResponse.class);
    }

    private void saveToCache(@NonNull SettingsResponse settings) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(this.getClass().getCanonicalName(), Activity.MODE_PRIVATE);
        String json = new Gson().toJson(settings);
        preferences.edit().putString("settings", json).apply();
    }

    private void freeSubscription() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
        subscription = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        freeSubscription();
    }

    private void addDialogsToRequest(@NonNull SettingsRequest request) {
        addDialogsToRequest(request, MessagesController.getInstance().dialogs);
        addDialogsToRequest(request, MessagesController.getInstance().dialogsForward);
        addDialogsToRequest(request, MessagesController.getInstance().dialogsGroupsOnly);
        addDialogsToRequest(request, MessagesController.getInstance().dialogsServerOnly);
    }

    private void addDialogsToRequest(@NonNull SettingsRequest request, ArrayList<TLRPC.TL_dialog> dialogs) {
        //this is very complicated code from Telegram core to separate dialogs to users, groups and channels
        for (TLRPC.TL_dialog dlg : dialogs) {
            long currentDialogId = dlg.id;
            int lower_id = (int) currentDialogId;
            int high_id = (int) (currentDialogId >> 32);
            TLRPC.Chat chat = null;
            TLRPC.User user = null;
            if (lower_id != 0) {
                if (high_id == 1) {
                    chat = MessagesController.getInstance().getChat(lower_id);
                } else {
                    if (lower_id < 0) {
                        chat = MessagesController.getInstance().getChat(-lower_id);
                        if (chat != null && chat.migrated_to != null) {
                            TLRPC.Chat chat2 = MessagesController.getInstance().getChat(chat.migrated_to.channel_id);
                            if (chat2 != null) {
                                chat = chat2;
                            }
                        }
                    } else {
                        user = MessagesController.getInstance().getUser(lower_id);
                    }
                }
            } else {
                TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().getEncryptedChat(high_id);
                if (encryptedChat != null) {
                    user = MessagesController.getInstance().getUser(encryptedChat.user_id);
                }
            }

            SettingsRequest.Row row = new SettingsRequest.Row();
            row.id = dlg.id;
            if (chat != null) {
                row.title = chat.title;
                row.userName = chat.username;

                if (DialogObject.isChannel(dlg)) {
                    request.addChannel(row);
                } else {
                    request.addGroup(row);
                }
            } else if (user != null) {
                if (user.bot) {
                    row.title = user.username;
                    row.userName = user.username;
                    request.addBot(row);
                }
            }
        }
    }
}
